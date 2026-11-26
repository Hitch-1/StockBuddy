package stockbuddy;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The main StockBuddy window. */
public class MainWindow extends JFrame {
    private static final String[] PAGES = {"Today's Ideas", "World Markets", "World Stocks", "Economic Calendar", "Look Up a Stock",
            "All Stocks", "Market News", "Price Alerts", "Paper Trading", "Track Record", "Dictionary", "My Watchlist", "Data Sources"};
    private static final String[] PAGE_HINTS = {"Buys, sells, options, shorts and 0DTE", "The 16 biggest stock markets",
            "Big companies abroad + where to buy", "Big news this week", "Full explanation and chart", "Every stock checked (30 days)", "Headlines in your language",
            "Get told when a price is reached", "Practise with pretend money", "How past ideas turned out", "Investing words made simple",
            "Stocks you always want checked", "Which websites worked"};

    private String currentPage = PAGES[0];
    private String lookupTicker;
    private int ideasTab;

    private final Runnable onData = this::refreshContent;
    private final Runnable onSkin = this::rebuild;
    private final Runnable onMarkets = this::refreshMarkets;
    private final java.util.function.Consumer<String> onStatus = s -> SwingUtilities.invokeLater(() -> updateStatus(s));

    // widgets that get refreshed when new data arrives
    private CardLayout cards;
    private JPanel cardPanel;
    private final Map<String, JToggleButton> navButtons = new LinkedHashMap<>();
    private final List<JToggleButton> ideaTabButtons = new ArrayList<>();
    private CardLayout ideasCards;
    private JPanel ideasCardPanel;
    private final List<JEditorPane> ideaPanes = new ArrayList<>();
    private JLabel worldMood, worldUpdated, allNotice;
    private DefaultTableModel worldModel, allModel;
    private JEditorPane lookupPane, glossaryPane, sourcesPane, worldStocksPane, calendarPane, trackPane, alertsPane, paperPane, newsPane;
    private JTextField alertField, paperField;
    private PriceChart chart;
    private JComboBox<String> lookupBox;
    private DefaultListModel<String> watchModel;
    private JLabel statusLabel;
    private JProgressBar progress;
    private JButton refreshBtn;
    private JCheckBox practiceBox;

    public MainWindow() {
        super(I18n.tr("StockBuddy - your friendly daily stock helper"));
        setIconImages(App.icons());
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosed(WindowEvent e) {
                App.removeListeners(onData, onSkin, onStatus, onMarkets);
                Settings.set("main.w", getWidth()); Settings.set("main.h", getHeight());
                App.exitIfNoWindows();
            }
        });
        App.onData(onData);
        App.onMarkets(onMarkets);
        App.onSkin(onSkin);
        App.onStatus(onStatus);
        rebuild();
        setSize(Settings.getInt("main.w", 1180), Settings.getInt("main.h", 800));
        setMinimumSize(new Dimension(820, 560));
        setLocationRelativeTo(null);
    }

    // =============================================================== layout
    private void rebuild() {
        Skin k = App.skin;
        ideaPanes.clear();
        ideaTabButtons.clear();
        navButtons.clear();
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(k.bg());
        root.add(header(k), BorderLayout.NORTH);
        JScrollPane side = new JScrollPane(sidebar(k), ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        side.setBorder(BorderFactory.createEmptyBorder());
        side.getVerticalScrollBar().setUnitIncrement(16);
        root.add(side, BorderLayout.LINE_START);

        cards = new CardLayout();
        cardPanel = new JPanel(cards);
        cardPanel.setBackground(k.bg());
        worldStocksPane = htmlPane();
        calendarPane = htmlPane();
        trackPane = htmlPane();
        newsPane = htmlPane();
        cardPanel.add(ideasPage(k), "Today's Ideas");
        cardPanel.add(worldPage(k), "World Markets");
        cardPanel.add(padded(k, scroll(worldStocksPane)), "World Stocks");
        cardPanel.add(padded(k, scroll(calendarPane)), "Economic Calendar");
        cardPanel.add(lookupPage(k), "Look Up a Stock");
        cardPanel.add(allPage(k), "All Stocks");
        cardPanel.add(padded(k, scroll(newsPane)), "Market News");
        cardPanel.add(commandPage(k, "alerts"), "Price Alerts");
        cardPanel.add(commandPage(k, "paper"), "Paper Trading");
        cardPanel.add(padded(k, scroll(trackPane)), "Track Record");
        cardPanel.add(glossaryPage(k), "Dictionary");
        cardPanel.add(watchlistPage(k), "My Watchlist");
        cardPanel.add(sourcesPage(k), "Data Sources");
        root.add(cardPanel, BorderLayout.CENTER);
        root.add(statusBar(k), BorderLayout.SOUTH);
        root.applyComponentOrientation(I18n.rtl() ? ComponentOrientation.RIGHT_TO_LEFT : ComponentOrientation.LEFT_TO_RIGHT);
        setContentPane(root);
        showPage(currentPage);
        refreshContent();
        revalidate();
        repaint();
    }

    private JComponent header(Skin k) {
        JPanel h = new JPanel(new BorderLayout());
        h.setBackground(k.panel());
        h.setBorder(new MatteBorder(0, 0, 1, 0, k.line()));

        JPanel brand = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 10));
        brand.setOpaque(false);
        brand.add(new JLabel(new ImageIcon(App.icons().get(3))));
        JPanel txt = new JPanel(new GridLayout(2, 1));
        txt.setOpaque(false);
        txt.add(label(I18n.tr("StockBuddy"), k.font(Font.BOLD, 22), k.text()));
        txt.add(label(I18n.tr("Your friendly daily stock helper"), k.font(Font.PLAIN, 13), k.muted()));
        brand.add(txt);
        h.add(brand, BorderLayout.WEST);

        JPanel tools = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 18));
        tools.setOpaque(false);
        refreshBtn = button(I18n.tr("Refresh data"), I18n.tr("Download the latest data from all websites"));
        refreshBtn.addActionListener(e -> App.refresh());
        practiceBox = new JCheckBox(I18n.tr("Practice mode"), App.demo);
        practiceBox.setToolTipText(I18n.tr("Use made-up data to learn safely (works without internet)"));
        practiceBox.setOpaque(false);
        practiceBox.setForeground(k.text());
        practiceBox.addActionListener(e -> App.setDemo(practiceBox.isSelected()));

        JToggleButton skins = pill(k.name() + I18n.tr("  \u25BE"), k);
        skins.setIcon(swatch(k));
        skins.setIconTextGap(8);
        skins.setFont(k.font(Font.PLAIN, 14));
        skins.setToolTipText(I18n.tr("Change the colours of StockBuddy"));
        skins.addActionListener(e -> {
            skins.setSelected(false);
            JPopupMenu menu = new JPopupMenu();
            JPanel list = new JPanel(new GridLayout(0, 1, 0, 4));
            list.setBackground(k.panel());
            list.setBorder(new EmptyBorder(6, 6, 6, 6));
            for (Skin s : Skin.ALL) {
                JToggleButton item = pill(s.name(), k);
                item.setIcon(swatch(s));
                item.setIconTextGap(10);
                item.setHorizontalAlignment(SwingConstants.LEFT);
                item.setFont(k.font(Font.PLAIN, 14));
                item.setSelected(s == k);
                item.addActionListener(a -> { menu.setVisible(false); if (s != App.skin) SwingUtilities.invokeLater(() -> App.setSkin(s)); });
                list.add(item);
            }
            menu.setBorder(BorderFactory.createLineBorder(k.line()));
            menu.add(list);
            menu.show(skins, 0, skins.getHeight() + 2);
        });
        JButton widgetBtn = button(I18n.tr("Desktop widget"), I18n.tr("Show a small always-visible StockBuddy panel on your desktop"));
        widgetBtn.addActionListener(e -> App.showWidget());
        JButton save = button(I18n.tr("Save report"), I18n.tr("Save today's ideas as a web page you can open any time"));
        save.addActionListener(e -> saveReport());

        tools.add(practiceBox);
        JToggleButton langs = pill(I18n.name(I18n.lang()) + "  \u25BE", k);
        langs.setToolTipText(I18n.tr("Language"));
        langs.addActionListener(e -> {
            langs.setSelected(false);
            JPopupMenu menu = new JPopupMenu();
            JPanel list = new JPanel(new GridLayout(0, 1, 0, 4));
            list.setBackground(k.panel());
            list.setBorder(new EmptyBorder(6, 6, 6, 6));
            for (String[] l : I18n.LANGS) {
                JToggleButton item = pill(l[1], k);
                item.setFont(k.font(Font.PLAIN, 15));
                item.setSelected(l[0].equals(I18n.lang()));
                item.addActionListener(a -> { menu.setVisible(false); if (!l[0].equals(I18n.lang())) SwingUtilities.invokeLater(() -> App.setLanguage(l[0])); });
                list.add(item);
            }
            menu.setBorder(BorderFactory.createLineBorder(k.line()));
            menu.add(list);
            menu.show(langs, 0, langs.getHeight() + 2);
        });
        tools.add(langs);
        tools.add(label(I18n.tr("Skin:"), k.font(Font.PLAIN, 14), k.text()));
        tools.add(skins);
        tools.add(widgetBtn);
        tools.add(save);
        tools.add(refreshBtn);
        h.add(tools, BorderLayout.EAST);
        return h;
    }

    private JComponent sidebar(Skin k) {
        JPanel side = new JPanel();
        side.setLayout(new BoxLayout(side, BoxLayout.Y_AXIS));
        side.setBackground(k.panel());
        side.setBorder(new CompoundBorderFix(new MatteBorder(0, 0, 0, 1, k.line()), new EmptyBorder(12, 10, 12, 10)));
        ButtonGroup group = new ButtonGroup();
        for (int i = 0; i < PAGES.length; i++) {
            String p = PAGES[i];
            JToggleButton b = new JToggleButton("<html><b>" + I18n.tr(p) + "</b><br><span style='font-size:10pt;color:"
                    + Skin.hex(k.muted()) + "'>" + I18n.tr(PAGE_HINTS[i]) + "</span></html>") {
                @Override protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    if (isSelected()) { g2.setColor(k.soft()); g2.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
                        g2.setColor(k.accent()); g2.fillRoundRect(0, 6, 4, getHeight() - 12, 4, 4); }
                    else if (getModel().isRollover()) { g2.setColor(k.mix(k.panel(), k.accent(), 0.07)); g2.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12); }
                    g2.dispose();
                    super.paintComponent(g);
                }
            };
            b.setContentAreaFilled(false);
            b.setBorderPainted(false);
            b.setFocusPainted(false);
            b.setOpaque(false);
            b.setRolloverEnabled(true);
            b.setHorizontalAlignment(SwingConstants.LEFT);
            b.setForeground(k.text());
            b.setFont(k.font(Font.PLAIN, 14));
            b.setBorder(new EmptyBorder(8, 14, 8, 10));
            b.setMaximumSize(new Dimension(250, 52));
            b.setPreferredSize(new Dimension(240, 52));
            b.setAlignmentX(Component.LEFT_ALIGNMENT);
            b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            b.addActionListener(e -> showPage(p));
            group.add(b);
            navButtons.put(p, b);
            side.add(b);
            side.add(Box.createVerticalStrut(4));
        }
        side.add(Box.createVerticalGlue());
        JLabel warn = new JLabel(I18n.tr("<html><div style='width:190px'><b>Not financial advice.</b> StockBuddy can be wrong. ")
                + I18n.tr("Never invest money you can't afford to lose.</div></html>"));
        warn.setForeground(k.warn());
        warn.setFont(k.font(Font.PLAIN, 12));
        warn.setBorder(new EmptyBorder(8, 12, 4, 6));
        side.add(warn);
        return side;
    }

    private JComponent statusBar(Skin k) {
        JPanel s = new JPanel(new BorderLayout(10, 0));
        s.setBackground(k.panel());
        s.setBorder(new CompoundBorderFix(new MatteBorder(1, 0, 0, 0, k.line()), new EmptyBorder(6, 14, 6, 14)));
        statusLabel = label(App.status, k.font(Font.PLAIN, 13), k.muted());
        progress = new JProgressBar();
        progress.setIndeterminate(true);
        progress.setPreferredSize(new Dimension(160, 14));
        progress.setVisible(App.loading);
        s.add(statusLabel, BorderLayout.CENTER);
        s.add(progress, BorderLayout.EAST);
        return s;
    }

    // =============================================================== pages
    private JComponent ideasPage(Skin k) {
        JPanel p = new JPanel(new BorderLayout(0, 12));
        p.setOpaque(false);
        JPanel tabs = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        tabs.setOpaque(false);
        ideasCards = new CardLayout();
        ideasCardPanel = new JPanel(ideasCards);
        ideasCardPanel.setOpaque(false);
        ButtonGroup g = new ButtonGroup();
        String[] names = HtmlView.IDEA_TABS;
        tabs.add(marketPicker(k));
        tabs.add(Box.createHorizontalStrut(8));
        for (int i = 0; i < names.length; i++) {
            final int idx = i;
            JEditorPane pane = htmlPane();
            ideaPanes.add(pane);
            ideasCardPanel.add(scroll(pane), String.valueOf(i));
            JToggleButton b = pill(I18n.tr(names[i]), k);
            b.addActionListener(e -> { ideasTab = idx; ideasCards.show(ideasCardPanel, String.valueOf(idx)); });
            g.add(b);
            ideaTabButtons.add(b);
            tabs.add(b);
        }
        ideaTabButtons.get(ideasTab).setSelected(true);
        ideasCards.show(ideasCardPanel, String.valueOf(ideasTab));
        p.add(tabs, BorderLayout.NORTH);
        p.add(ideasCardPanel, BorderLayout.CENTER);
        return padded(k, p);
    }

    /** I18n.tr("Market: ...") picker: filter today's ideas to one of the 16 biggest markets. */
    private JComponent marketPicker(Skin k) { return marketPicker(k, "ideas.market", I18n.tr("Show ideas for one market only")); }

    private JComponent marketPicker(Skin k, String key, String tip) {
        String cur = Settings.get(key, "all");
        JToggleButton b = pill(I18n.tr("Market:") + " " + (cur.equals("all") ? I18n.tr("All markets") : I18n.tr(shortMarket(cur))) + I18n.tr("  \u25BE"), k);
        b.setToolTipText(I18n.tr(tip));
        b.addActionListener(e -> {
            b.setSelected(false);
            JPopupMenu menu = new JPopupMenu();
            JPanel list = new JPanel(new GridLayout(0, 1, 0, 2));
            list.setBackground(k.panel());
            list.setBorder(new EmptyBorder(6, 6, 6, 6));
            List<String[]> options = new ArrayList<>();
            options.add(new String[]{"all", "All markets"});
            for (WorldMarkets.Market m : WorldMarkets.all()) options.add(new String[]{m.symbol, m.name});
            for (String[] o : options) {
                JToggleButton item = smallPill(I18n.tr(o[1]), k, o[0].equals(cur));
                item.setHorizontalAlignment(SwingConstants.LEFT);
                item.addActionListener(a -> {
                    menu.setVisible(false);
                    Settings.set(key, o[0]);
                    SwingUtilities.invokeLater(this::rebuild);
                });
                list.add(item);
            }
            menu.setBorder(BorderFactory.createLineBorder(k.line()));
            menu.add(list);
            menu.show(b, 0, b.getHeight() + 2);
        });
        return b;
    }

    private static String shortMarket(String symbol) {
        for (WorldMarkets.Market m : WorldMarkets.all()) if (m.symbol.equals(symbol)) return m.name.replaceAll(" \\(.*\\)$", "");
        return symbol;
    }

    /** The ideas to show: for all markets, or rebuilt for the chosen market. */
    private Advisor.Plan shownPlan() {
        String m = Settings.get("ideas.market", "all");
        if (App.plan == null || m.equals("all")) return App.plan;
        return Advisor.build(App.analyzer, m);
    }

    /** A rounded "pill" tab button drawn in the skin's colours (readable on every skin). */
    private static JToggleButton pill(String text, Skin k) {
        JToggleButton b = new JToggleButton(text) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int h = getHeight() - 1, w = getWidth() - 1;
                if (isSelected()) { g2.setColor(k.accent()); g2.fillRoundRect(0, 0, w, h, h, h); }
                else {
                    g2.setColor(getModel().isRollover() ? k.soft() : k.panel());
                    g2.fillRoundRect(0, 0, w, h, h, h);
                    g2.setColor(k.line());
                    g2.drawRoundRect(0, 0, w, h, h, h);
                }
                g2.dispose();
                setForeground(isSelected() ? (k.dark() ? k.bg() : Color.WHITE) : k.text());
                super.paintComponent(g);
            }
        };
        b.setContentAreaFilled(false);
        b.setBorderPainted(false);
        b.setFocusPainted(false);
        b.setOpaque(false);
        b.setRolloverEnabled(true);
        b.setFont(k.font(Font.BOLD, 14));
        b.setBorder(new EmptyBorder(7, 18, 7, 18));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }

    private JComponent worldPage(Skin k) {
        JPanel p = new JPanel(new BorderLayout(0, 10));
        p.setOpaque(false);
        JPanel top = new JPanel(new GridLayout(3, 1));
        top.setOpaque(false);
        top.add(label(I18n.tr("The world's biggest stock markets"), k.font(Font.BOLD, 20), k.text()));
        worldMood = label(" ", k.font(Font.PLAIN, 15), k.accent());
        top.add(worldMood);
        worldUpdated = label(" ", k.font(Font.PLAIN, 12), k.muted());
        top.add(worldUpdated);
        p.add(top, BorderLayout.NORTH);
        worldModel = new DefaultTableModel(new Object[]{I18n.tr("Market"), I18n.tr("Index price"), I18n.tr("Today"), I18n.tr("Last month"), I18n.tr("Status"), I18n.tr("Local time"), I18n.tr("Country")}, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
            @Override public Class<?> getColumnClass(int c) { return c >= 1 && c <= 3 ? Double.class : String.class; }
        };
        JTable t = table(k, worldModel);
        t.getColumnModel().getColumn(0).setPreferredWidth(300);
        t.setToolTipText(I18n.tr("Double-click a market to see all its stocks"));
        t.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                int row = t.rowAtPoint(e.getPoint());
                if (e.getClickCount() == 2 && row >= 0) {
                    int m = t.convertRowIndexToModel(row);
                    if (m < App.analyzer.markets.size()) {
                        Settings.set("allstocks.market", App.analyzer.markets.get(m).symbol);
                        rebuild();
                        showPage("All Stocks");
                    }
                }
            }
        });
        p.add(scroll(t), BorderLayout.CENTER);
        p.add(label(I18n.tr("<html>An \"index\" is a basket of a country's biggest companies. Asian markets open first, then Europe, then the Americas - ")
                + I18n.tr("a big move in Asia overnight often affects how Europe and the US start the day.</html>"), k.font(Font.PLAIN, 13), k.muted()), BorderLayout.SOUTH);
        return padded(k, p);
    }

    private JComponent lookupPage(Skin k) {
        JPanel p = new JPanel(new BorderLayout(0, 10));
        p.setOpaque(false);
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        bar.setOpaque(false);
        bar.add(label(I18n.tr("Type a ticker (like AAPL) or pick one:"), k.font(Font.PLAIN, 15), k.text()));
        lookupBox = new JComboBox<>();
        lookupBox.setEditable(true);
        lookupBox.setPreferredSize(new Dimension(220, 32));
        JButton go = button(I18n.tr("Explain this stock"), null);
        go.addActionListener(e -> doLookup(String.valueOf(lookupBox.getEditor().getItem())));
        lookupBox.getEditor().addActionListener(e -> doLookup(String.valueOf(lookupBox.getEditor().getItem())));
        bar.add(lookupBox);
        bar.add(go);
        JButton watch = button(I18n.tr("\u2605 Add to My Watchlist"), I18n.tr("Always check this stock from now on"));
        watch.addActionListener(e -> {
            String t = lookupTicker != null ? lookupTicker : String.valueOf(lookupBox.getEditor().getItem());
            addToWatchlist(t);
        });
        bar.add(watch);
        p.add(bar, BorderLayout.NORTH);
        chart = new PriceChart(k);
        lookupPane = htmlPane();
        lookupPane.setText(HtmlView.message(k.palette(), I18n.tr("Look up a stock"), I18n.tr("Type a ticker above and press Enter. You'll get a chart and a plain-English explanation of what every source says.")));
        JPanel center = new JPanel(new BorderLayout(0, 10));
        center.setOpaque(false);
        JPanel chartBox = new JPanel(new BorderLayout(0, 6));
        chartBox.setOpaque(false);
        chartBox.add(chartToolbar(k), BorderLayout.NORTH);
        chartBox.add(chart, BorderLayout.CENTER);
        center.add(chartBox, BorderLayout.NORTH);
        center.add(scroll(lookupPane), BorderLayout.CENTER);
        p.add(center, BorderLayout.CENTER);
        return padded(k, p);
    }

    /** A page with a I18n.tr("type a command") box on top and an HTML page below (alerts, paper trading). */
    private JComponent commandPage(Skin k, String which) {
        boolean alerts = which.equals("alerts");
        JPanel p = new JPanel(new BorderLayout(0, 10));
        p.setOpaque(false);
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        bar.setOpaque(false);
        bar.add(label(alerts ? I18n.tr("New alert:") : I18n.tr("Trade:"), k.font(Font.BOLD, 15), k.text()));
        JTextField f = new JTextField(alerts ? 22 : 18);
        f.setFont(k.font(Font.PLAIN, 15));
        f.setToolTipText(alerts ? I18n.tr("For example: NVDA above 150   TSLA below 200   AAPL move 5%") : I18n.tr("For example: buy 10 AAPL   sell 5 AAPL   sell all AAPL"));
        JButton go = button(alerts ? I18n.tr("Add alert") : I18n.tr("Do it"), null);
        JLabel example = label(alerts ? I18n.tr("e.g.  NVDA above 150  \u00B7  TSLA below 200  \u00B7  AAPL move 5%") : I18n.tr("e.g.  buy 10 AAPL  \u00B7  sell 5 AAPL  \u00B7  sell all AAPL"),
                k.font(Font.PLAIN, 13), k.muted());
        Runnable act = () -> {
            if (alerts) {
                String err = Alerts.add(f.getText());
                if (err != null) { JOptionPane.showMessageDialog(this, err); return; }
                f.setText("");
                refreshAlertsAndPaper();
                App.checkAlertsSoon();
            } else {
                paperCommand(f.getText());
                f.setText("");
            }
        };
        f.addActionListener(e -> act.run());
        go.addActionListener(e -> act.run());
        bar.add(f); bar.add(go); bar.add(example);
        if (alerts) {
            JButton check = button(I18n.tr("Check now"), I18n.tr("Check all alerts with fresh prices"));
            check.addActionListener(e -> App.checkAlertsSoon());
            bar.add(check);
            alertField = f;
        } else paperField = f;
        JEditorPane pane = htmlPane();
        if (alerts) alertsPane = pane; else paperPane = pane;
        p.add(bar, BorderLayout.NORTH);
        p.add(scroll(pane), BorderLayout.CENTER);
        return padded(k, p);
    }

    void refreshAlertsAndPaper() {
        if (alertsPane == null) return;
        alertsPane.setText(HtmlView.alerts(App.skin.palette(), App.analyzer));
        alertsPane.setCaretPosition(0);
        paperPane.setText(HtmlView.portfolio(App.skin.palette(), App.analyzer));
        paperPane.setCaretPosition(0);
    }

    private void paperBuy(String ticker) {
        String n = (String) JOptionPane.showInputDialog(this, I18n.tr("How many pretend shares of ") + ticker + I18n.tr(" do you want to buy?\n(Pretend money - nothing real is bought.)"),
                I18n.tr("Paper trading"), JOptionPane.PLAIN_MESSAGE, null, null, "10");
        if (n == null || n.trim().isEmpty()) return;
        paperCommand("buy " + n.trim() + " " + ticker);
    }

    private void paperCommand(String cmd) {
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        Analyzer a = App.analyzer;
        new SwingWorker<String, Void>() {
            @Override protected String doInBackground() { return PaperPortfolio.load().run(cmd, a); }
            @Override protected void done() {
                setCursor(Cursor.getDefaultCursor());
                String msg;
                try { msg = get(); } catch (Exception e) { msg = e.getMessage(); }
                if (!msg.startsWith("OK")) JOptionPane.showMessageDialog(MainWindow.this, msg);
                else { showPage("Paper Trading"); updateStatus(msg); }
                refreshAlertsAndPaper();
            }
        }.execute();
    }

    /** The buttons above the chart: style, period, indicator lines and the bottom panel. */
    private JComponent chartToolbar(Skin k) {
        ChartOptions o = chart.options();
        JPanel rows = new JPanel(new GridLayout(2, 1, 0, 4));
        rows.setOpaque(false);
        JPanel r1 = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0)), r2 = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        r1.setOpaque(false); r2.setOpaque(false);

        r1.add(label(I18n.tr("Chart:"), k.font(Font.BOLD, 13), k.muted()));
        ButtonGroup types = new ButtonGroup();
        for (int i = 0; i < ChartOptions.TYPES.length; i++) {
            String v = ChartOptions.TYPES[i];
            JToggleButton b = smallPill(I18n.tr(ChartOptions.TYPE_NAMES[i]), k, v.equals(o.type));
            b.addActionListener(e -> { o.type = v; o.save(); chart.repaint(); });
            types.add(b); r1.add(b);
        }
        r1.add(Box.createHorizontalStrut(14));
        r1.add(label(I18n.tr("Period:"), k.font(Font.BOLD, 13), k.muted()));
        ButtonGroup ranges = new ButtonGroup();
        for (int i = 0; i < ChartOptions.RANGES.length; i++) {
            int v = ChartOptions.RANGES[i];
            JToggleButton b = smallPill(I18n.tr(ChartOptions.RANGE_NAMES[i]), k, v == o.range);
            b.addActionListener(e -> { o.range = v; o.save(); chart.resetZoom(); });
            ranges.add(b); r1.add(b);
        }

        r2.add(label(I18n.tr("Lines:"), k.font(Font.BOLD, 13), k.muted()));
        for (int i = 0; i < ChartOptions.OVERLAYS.length; i++) {
            String v = ChartOptions.OVERLAYS[i];
            JToggleButton b = smallPill(I18n.tr(ChartOptions.OVERLAY_NAMES[i]), k, o.overlay(v));
            b.setToolTipText(v.equals("bollinger") ? I18n.tr("A band around the price: touching the top = expensive lately, the bottom = cheap lately")
                    : I18n.tr("The average price of the last ") + v.replaceAll("\\D", "") + I18n.tr(" days") + (v.startsWith("ema") ? I18n.tr(" (recent days count more)") : ""));
            b.addActionListener(e -> { o.toggle(v); o.save(); chart.repaint(); });
            r2.add(b);
        }
        r2.add(Box.createHorizontalStrut(14));
        r2.add(label(I18n.tr("Bottom panel:"), k.font(Font.BOLD, 13), k.muted()));
        ButtonGroup lowers = new ButtonGroup();
        for (int i = 0; i < ChartOptions.LOWERS.length; i++) {
            String v = ChartOptions.LOWERS[i];
            JToggleButton b = smallPill(I18n.tr(ChartOptions.LOWER_NAMES[i]), k, v.equals(o.lower));
            b.addActionListener(e -> { o.lower = v; o.save(); chart.repaint(); });
            lowers.add(b); r2.add(b);
        }
        rows.add(r1);
        rows.add(r2);
        return rows;
    }

    private static JToggleButton smallPill(String text, Skin k, boolean selected) {
        JToggleButton b = pill(text, k);
        b.setFont(k.font(Font.PLAIN, 12));
        b.setBorder(new EmptyBorder(3, 11, 3, 11));
        b.setSelected(selected);
        return b;
    }

    private JComponent allPage(Skin k) {
        JPanel p = new JPanel(new BorderLayout(0, 10));
        p.setOpaque(false);
        JPanel top = new JPanel(new BorderLayout(0, 6));
        top.setOpaque(false);
        JPanel titleRow = new JPanel(new FlowLayout(FlowLayout.LEADING, 10, 0));
        titleRow.setOpaque(false);
        titleRow.add(label(I18n.tr("Every stock I checked"), k.font(Font.BOLD, 20), k.text()));
        titleRow.add(marketPicker(k, "allstocks.market", I18n.tr("Show the stocks of one market only")));
        top.add(titleRow, BorderLayout.NORTH);
        allNotice = label(" ", k.font(Font.PLAIN, 13), k.warn());
        top.add(allNotice, BorderLayout.SOUTH);
        p.add(top, BorderLayout.NORTH);
        allModel = new DefaultTableModel(new Object[]{"#", I18n.tr("Ticker"), I18n.tr("Name"), I18n.tr("Price"), I18n.tr("Today"), I18n.tr("Verdict"), I18n.tr("Score"),
                I18n.tr("Confidence"), I18n.tr("Checked")}, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
            @Override public Class<?> getColumnClass(int c) { return c == 0 ? Integer.class : c == 3 || c == 4 || c == 6 ? Double.class : String.class; }
        };
        JTable t = table(k, allModel);
        t.getColumnModel().getColumn(2).setPreferredWidth(240);
        t.getColumnModel().getColumn(5).setPreferredWidth(170);
        t.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && t.getSelectedRow() >= 0)
                    openLookup((String) t.getValueAt(t.getSelectedRow(), 1));
            }
        });
        p.add(scroll(t), BorderLayout.CENTER);
        p.add(label(I18n.tr("Tip: double-click a row to see the full explanation. Click a column title to sort."), k.font(Font.PLAIN, 13), k.muted()), BorderLayout.SOUTH);
        return padded(k, p);
    }

    private JComponent glossaryPage(Skin k) {
        JPanel p = new JPanel(new BorderLayout(0, 10));
        p.setOpaque(false);
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        bar.setOpaque(false);
        bar.add(label(I18n.tr("Search a word:"), k.font(Font.PLAIN, 15), k.text()));
        JTextField search = new JTextField(22);
        bar.add(search);
        p.add(bar, BorderLayout.NORTH);
        glossaryPane = htmlPane();
        glossaryPane.setText(HtmlView.glossary(k.palette(), ""));
        search.getDocument().addDocumentListener(new DocumentListener() {
            void upd() { glossaryPane.setText(HtmlView.glossary(App.skin.palette(), search.getText())); glossaryPane.setCaretPosition(0); }
            public void insertUpdate(DocumentEvent e) { upd(); }
            public void removeUpdate(DocumentEvent e) { upd(); }
            public void changedUpdate(DocumentEvent e) { upd(); }
        });
        p.add(scroll(glossaryPane), BorderLayout.CENTER);
        return padded(k, p);
    }

    private JLabel watchMsg;

    private JComponent watchlistPage(Skin k) {
        JPanel p = new JPanel(new BorderLayout(12, 10));
        p.setOpaque(false);

        JPanel top = new JPanel(new BorderLayout(0, 8));
        top.setOpaque(false);
        top.add(label(I18n.tr("<html><b style='font-size:16pt'>My watchlist</b><br>These stocks are always checked - plus today's biggest movers ")
                + I18n.tr("and the stocks Reddit talks about most. <b>Double-click a stock</b> to see its full explanation and chart.</html>"),
                k.font(Font.PLAIN, 14), k.text()), BorderLayout.NORTH);
        JPanel addRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        addRow.setOpaque(false);
        addRow.add(label(I18n.tr("Add ticker(s):"), k.font(Font.BOLD, 14), k.text()));
        JTextField add = new JTextField(16);
        add.setFont(k.font(Font.PLAIN, 15));
        add.setToolTipText(I18n.tr("For example: AAPL  or several at once: AAPL MSFT NVDA"));
        JButton addB = button(I18n.tr("Add"), I18n.tr("Add the ticker(s) - saved straight away"));
        addRow.add(add);
        addRow.add(addB);
        watchMsg = label(" ", k.font(Font.PLAIN, 13), k.good());
        addRow.add(watchMsg);
        top.add(addRow, BorderLayout.SOUTH);
        p.add(top, BorderLayout.NORTH);

        watchModel = new DefaultListModel<>();
        Analyzer.readWatchlist().forEach(watchModel::addElement);
        JList<String> list = new JList<>(watchModel);
        list.setFont(k.font(Font.BOLD, 15));
        list.setFixedCellHeight(30);
        list.setCellRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> l, Object v, int i, boolean sel, boolean foc) {
                super.getListCellRendererComponent(l, v, i, sel, foc);
                Stock s = App.analyzer.stocks.get(String.valueOf(v));
                Color col = s == null || s.tech == null ? k.muted() : s.score >= 0.2 ? k.good() : s.score <= -0.2 ? k.bad() : k.warn();
                String extra = s == null ? "" : "   " + (s.name == null ? "" : s.name + "   ") + Advisor.money(s);
                String verdict = s == null || s.tech == null ? "" : "   " + Scorer.verdict(s.score);
                setText(I18n.tr("<html><span style='color:") + Skin.hex(col) + I18n.tr("'>&#9679;</span> <b style='color:") + Skin.hex(col) + "'>" + v + I18n.tr("</b><span style='color:") + Skin.hex(k.muted()) + "'>"
                        + HtmlView.esc(extra) + I18n.tr("</span><b style='color:") + Skin.hex(col) + "'>" + HtmlView.esc(verdict) + I18n.tr("</b></html>"));
                setBorder(new EmptyBorder(0, 10, 0, 10));
                setBackground(sel ? k.soft() : k.panel());
                setForeground(k.text());
                return this;
            }
        });
        list.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int i = list.locationToIndex(e.getPoint());
                    if (i >= 0) openLookup(watchModel.get(i));
                }
            }
        });
        p.add(scroll(list), BorderLayout.CENTER);

        JPanel btns = new JPanel();
        btns.setOpaque(false);
        btns.setLayout(new BoxLayout(btns, BoxLayout.Y_AXIS));
        JButton look = button(I18n.tr("Look up selected"), I18n.tr("See the full explanation and chart"));
        JButton rem = button(I18n.tr("Remove selected"), I18n.tr("Removed straight away"));
        JButton reset = button(I18n.tr("Reset to default list"), null);
        JButton refresh = button(I18n.tr("Analyse my list now"), I18n.tr("Download fresh data for your watchlist"));
        Runnable doAdd = () -> {
            List<String> added = addToWatchlistQuiet(add.getText());
            add.setText("");
            watchMsg.setForeground(added.isEmpty() ? k.warn() : k.good());
            watchMsg.setText(added.isEmpty() ? I18n.tr("Type a ticker like AAPL (letters, 1-6 characters).")
                    : I18n.tr("Added ") + String.join(", ", added) + I18n.tr(" - saved. Click \"Analyse my list now\" to check it."));
        };
        add.addActionListener(e -> doAdd.run());
        addB.addActionListener(e -> doAdd.run());
        look.addActionListener(e -> { if (list.getSelectedValue() != null) openLookup(list.getSelectedValue()); });
        rem.addActionListener(e -> {
            List<String> gone = list.getSelectedValuesList();
            gone.forEach(watchModel::removeElement);
            saveWatchModel();
            if (!gone.isEmpty()) { watchMsg.setForeground(k.good()); watchMsg.setText(I18n.tr("Removed ") + String.join(", ", gone) + "."); }
        });
        reset.addActionListener(e -> {
            if (JOptionPane.showConfirmDialog(this, I18n.tr("Replace your watchlist with the default list?"), I18n.tr("Reset watchlist"),
                    JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
            watchModel.clear();
            Analyzer.DEFAULT_WATCHLIST.forEach(watchModel::addElement);
            saveWatchModel();
        });
        refresh.addActionListener(e -> App.refresh());
        for (JComponent c : new JComponent[]{look, rem, reset, refresh}) {
            c.setAlignmentX(Component.LEFT_ALIGNMENT);
            btns.add(c);
            btns.add(Box.createVerticalStrut(8));
        }
        p.add(btns, BorderLayout.EAST);
        return padded(k, p);
    }

    /** Checks the text, adds new tickers to the saved watchlist, and returns the ones added. */
    private List<String> addToWatchlistQuiet(String text) {
        List<String> added = new ArrayList<>();
        List<String> w = new ArrayList<>(Analyzer.readWatchlist());
        for (String t : text == null ? new String[0] : text.toUpperCase().split("[\\s,;]+")) {
            t = t.replace("$", "").trim();
            if (!t.matches("[A-Z][A-Z0-9.\\-]{0,9}")) continue;
            if (!w.contains(t)) { w.add(t); added.add(t); }
        }
        if (!added.isEmpty()) {
            try { Analyzer.saveWatchlist(w); }
            catch (Exception ex) { JOptionPane.showMessageDialog(this, I18n.tr("Couldn't save the watchlist: ") + ex.getMessage()); return new ArrayList<>(); }
            if (watchModel != null) for (String t : added) if (!watchModel.contains(t)) watchModel.addElement(t);
        }
        return added;
    }

    /** I18n.tr("Add to My Watchlist") from the Look Up page. */
    private void addToWatchlist(String ticker) {
        if (ticker == null || ticker.trim().isEmpty() || ticker.equals("null")) {
            JOptionPane.showMessageDialog(this, I18n.tr("Look up a stock first, then add it."));
            return;
        }
        String t = ticker.trim().toUpperCase();
        if (Analyzer.readWatchlist().contains(t)) { JOptionPane.showMessageDialog(this, t + I18n.tr(" is already in your watchlist.")); return; }
        List<String> added = addToWatchlistQuiet(t);
        JOptionPane.showMessageDialog(this, added.isEmpty() ? "\"" + t + I18n.tr("\" doesn't look like a ticker.") : I18n.tr("Added ") + t + I18n.tr(" to your watchlist."));
    }

    private void saveWatchModel() {
        try {
            List<String> l = new ArrayList<>();
            for (int i = 0; i < watchModel.size(); i++) l.add(watchModel.get(i));
            Analyzer.saveWatchlist(l);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, I18n.tr("Couldn't save: ") + ex.getMessage());
        }
    }

    private JComponent sourcesPage(Skin k) {
        JPanel p = new JPanel(new BorderLayout(0, 10));
        p.setOpaque(false);
        sourcesPane = htmlPane();
        p.add(scroll(sourcesPane), BorderLayout.CENTER);
        JPanel btns = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        btns.setOpaque(false);
        JButton rover = button(I18n.tr("Import Stock Rover CSV..."), I18n.tr("Choose the CSV file you exported from Stock Rover"));
        rover.addActionListener(e -> importCsv("stockrover.csv", I18n.tr("Stock Rover")));
        JButton zen = button(I18n.tr("Import WallStreetZen CSV..."), I18n.tr("A CSV with Ticker, Zen Score and Zen Rating columns"));
        zen.addActionListener(e -> importCsv("wallstreetzen.csv", I18n.tr("WallStreetZen")));
        JButton folder = button(I18n.tr("Open my StockBuddy folder"), I18n.tr("Where your watchlist, CSV files and reports are kept"));
        folder.addActionListener(e -> openFolder(AppPaths.base()));
        JButton auto = button(Autostart.isEnabled() ? I18n.tr("Don't start widget with computer") : I18n.tr("Start widget when computer starts"), null);
        auto.addActionListener(e -> {
            boolean on = !Autostart.isEnabled();
            String err = Autostart.set(on);
            if (err != null) JOptionPane.showMessageDialog(this, err);
            auto.setText(Autostart.isEnabled() ? I18n.tr("Don't start widget with computer") : I18n.tr("Start widget when computer starts"));
        });
        JButton fin = button(FinnhubSource.enabled() ? I18n.tr("Finnhub key: set (change...)") : I18n.tr("Add Finnhub key..."),
                I18n.tr("Free key from finnhub.io: adds analyst recommendations and financial ratios"));
        fin.addActionListener(e -> {
            askFinnhubKey();
            fin.setText(FinnhubSource.enabled() ? I18n.tr("Finnhub key: set (change...)") : I18n.tr("Add Finnhub key..."));
        });
        JButton anyCsv = button(I18n.tr("Add any CSV..."), I18n.tr("Any CSV with a Ticker or Symbol column becomes a data source"));
        anyCsv.addActionListener(e -> addAnyCsv());
        JButton noCsv = button(I18n.tr("Remove my CSV files"), null);
        noCsv.addActionListener(e -> {
            if (JOptionPane.showConfirmDialog(this, I18n.tr("Remove all the CSV files you added with \"Add any CSV\"?"), I18n.tr("Remove CSV files"),
                    JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
            try { CustomCsv.removeAll(); App.refresh(); } catch (Exception ex) { JOptionPane.showMessageDialog(this, I18n.tr("Couldn't remove them: ") + ex.getMessage()); }
        });
        JButton av = button(AlphaVantage.enabled() ? I18n.tr("Alpha Vantage key: set (change...)") : I18n.tr("Add Alpha Vantage key..."), I18n.tr("Free key from alphavantage.co: news sentiment"));
        av.addActionListener(e -> {
            askAlphaVantageKey();
            av.setText(AlphaVantage.enabled() ? I18n.tr("Alpha Vantage key: set (change...)") : I18n.tr("Add Alpha Vantage key..."));
        });
        JButton sec = button(SecEdgar.email().isEmpty() ? I18n.tr("SEC email (optional)...") : I18n.tr("SEC email: set (change...)"), I18n.tr("The SEC asks programs to say who is asking"));
        sec.addActionListener(e -> {
            String v = (String) JOptionPane.showInputDialog(this, I18n.tr("<html><div style='width:380px'>The SEC (sec.gov) asks every program to say who it is. ")
                    + I18n.tr("StockBuddy works without this, but if the SEC data doesn't load, enter your email here. It is only ever sent to sec.gov.</div></html>"),
                    I18n.tr("SEC EDGAR"), JOptionPane.PLAIN_MESSAGE, null, null, SecEdgar.email());
            if (v == null) return;
            SecEdgar.setEmail(v);
            sec.setText(SecEdgar.email().isEmpty() ? I18n.tr("SEC email (optional)...") : I18n.tr("SEC email: set (change...)"));
        });
        JCheckBox world = new JCheckBox(I18n.tr("Include world stocks (choose how many on the World Stocks page)"), WorldStocks.enabled());
        world.setOpaque(false);
        world.setForeground(k.text());
        world.setToolTipText(I18n.tr("Also analyse the biggest companies of the other 13 big stock markets (refreshing takes a bit longer)"));
        world.addActionListener(e -> { WorldStocks.setTop(world.isSelected() ? 3 : 0); App.refresh(); });

        JPanel files = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        files.setOpaque(false);
        files.add(label(I18n.tr("Your files:"), k.font(Font.BOLD, 14), k.text()));
        files.add(rover); files.add(zen); files.add(anyCsv); files.add(noCsv);
        btns.add(label(I18n.tr("Keys & settings:"), k.font(Font.BOLD, 14), k.text()));
        btns.add(fin); btns.add(av); btns.add(sec); btns.add(world);
        JPanel app = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        app.setOpaque(false);
        app.add(label(I18n.tr("This computer:"), k.font(Font.BOLD, 14), k.text()));
        app.add(folder); app.add(auto);

        JPanel reddit = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        reddit.setOpaque(false);
        JLabel rl = new JLabel(I18n.tr("Reddit:"));
        rl.setFont(k.font(Font.BOLD, 14));
        rl.setForeground(k.text());
        JButton addPosts = button(I18n.tr("Add Reddit posts..."), I18n.tr("Paste post titles you copied from Reddit (works even when Reddit blocks StockBuddy)"));
        addPosts.addActionListener(e -> addRedditPosts());
        JButton redditCsv = button(I18n.tr("Import Reddit CSV..."), I18n.tr("A CSV with a title column (optional: date, subreddit, upvotes)"));
        redditCsv.addActionListener(e -> importRedditCsv());
        JButton clear = button(I18n.tr("Clear my Reddit posts"), I18n.tr("Delete the posts you added yourself"));
        clear.addActionListener(e -> {
            if (JOptionPane.showConfirmDialog(this, I18n.tr("Delete all the Reddit posts you added yourself?"), I18n.tr("Clear my Reddit posts"),
                    JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
            try { RedditSource.clearManualPosts(); App.refresh(); }
            catch (Exception ex) { JOptionPane.showMessageDialog(this, I18n.tr("Couldn't delete them: ") + ex.getMessage()); }
        });
        reddit.add(rl); reddit.add(addPosts); reddit.add(redditCsv); reddit.add(clear);

        JPanel south = new JPanel(new GridLayout(4, 1, 0, 8));
        south.setOpaque(false);
        south.add(reddit);
        south.add(files);
        south.add(btns);
        south.add(app);
        p.add(south, BorderLayout.SOUTH);
        return padded(k, p);
    }

    // =============================================================== data -> screen
    private void refreshContent() {
        if (statusLabel == null) return;
        Skin k = App.skin;
        Analyzer a = App.analyzer;
        Advisor.Plan plan = App.plan;
        updateStatus(App.status);
        refreshBtn.setEnabled(!App.loading);
        practiceBox.setSelected(App.demo);
        setTitle(I18n.tr("StockBuddy - your friendly daily stock helper") + (App.demo ? I18n.tr("   [PRACTICE MODE - made-up data]") : ""));

        if (plan == null) {
            String msg = HtmlView.message(k.palette(), I18n.tr("Getting today's data..."), I18n.tr("StockBuddy is reading stockanalysis.com, WallStreetZen, Reddit, your Stock Rover file and price charts. This can take a minute."));
            ideaPanes.forEach(p -> p.setText(msg));
            sourcesPane.setText(msg);
            return;
        }
        Advisor.Plan shown = shownPlan();
        List<List<Advisor.Idea>> lists = HtmlView.ideaLists(shown);
        for (int i = 0; i < 5; i++) {
            ideaPanes.get(i).setText(HtmlView.ideasTab(k.palette(), shown, i, a.demo));
            ideaPanes.get(i).setCaretPosition(0);
            ideaTabButtons.get(i).setText(I18n.tr(HtmlView.IDEA_TABS[i]) + (i < 4 ? "  " + lists.get(i).size() : ""));
        }

        refreshMarkets();

        allModel.setRowCount(0);
        String allMarket = Settings.get("allstocks.market", "all");
        List<Stock> list = new ArrayList<>();
        for (Stock s : a.stocks.values()) if (WorldMarkets.belongs(s, allMarket)) list.add(s);
        list.sort((x, y) -> Double.compare(y.score, x.score));
        int n = 1;
        for (Stock s : list)
            allModel.addRow(new Object[]{n++, s.ticker, s.name == null ? "" : s.name, s.price, s.changePct, Scorer.verdict(s.score),
                    Math.round(s.score * 100) * 1.0, HtmlView.prettyStars(Scorer.stars(s.confidence)), I18n.tr("today")});
        List<CheckedHistory.Entry> history = a.demo ? new ArrayList<>() : CheckedHistory.load();
        for (CheckedHistory.Entry e : history) {
            if (a.stocks.containsKey(e.ticker)) continue;
            WorldStocks.Entry we = WorldStocks.forTicker(e.ticker);
            if (!allMarket.equals("all") && (we == null ? !WorldMarkets.isUs(allMarket) : !we.market.equals(allMarket))) continue;
            allModel.addRow(new Object[]{n++, e.ticker, e.name, e.price, e.changePct, Scorer.verdict(e.score), Math.round(e.score * 100) * 1.0, "", e.date});
        }
        String notice = I18n.trf("Every stock you check stays here for %d days, then it is cleared automatically.", CheckedHistory.KEEP_DAYS);
        if (CheckedHistory.lastCleared > 0) notice += " " + I18n.trf("%d old stocks were just cleared.", CheckedHistory.lastCleared);
        java.time.LocalDate next = CheckedHistory.nextClearing(history);
        if (next != null) notice += " " + I18n.trf("Next clean-up: %s.", next.toString());
        allNotice.setText(notice);

        Object typed = lookupBox.getEditor().getItem();
        lookupBox.removeAllItems();
        for (Stock s : list) lookupBox.addItem(s.ticker);
        String keep = typed == null ? "" : typed.toString();
        if (keep.isBlank() && lookupTicker != null) keep = lookupTicker;
        lookupBox.getEditor().setItem(keep);

        sourcesPane.setText(HtmlView.sources(k.palette(), a));
        worldStocksPane.setText(HtmlView.worldStocks(k.palette(), a));
        worldStocksPane.setCaretPosition(0);
        calendarPane.setText(HtmlView.calendar(k.palette(), a));
        calendarPane.setCaretPosition(0);
        trackPane.setText(HtmlView.trackRecord(k.palette(), a));
        trackPane.setCaretPosition(0);
        newsPane.setText(HtmlView.news(k.palette(), a));
        newsPane.setCaretPosition(0);
        refreshAlertsAndPaper();
        sourcesPane.setCaretPosition(0);
        if (lookupTicker != null) showStock(a.stocks.get(lookupTicker));
    }

    /** Fills the World Markets table (also called every minute when index prices update). */
    void refreshMarkets() {
        if (worldModel == null) return;
        Analyzer a = App.analyzer;
        worldMood.setText(WorldMarkets.mood(a.markets));
        worldModel.setRowCount(0);
        for (WorldMarkets.Market m : a.markets)
            worldModel.addRow(new Object[]{I18n.tr(m.name), m.price, m.changePct, m.monthPct, m.isOpenNow() ? I18n.tr("OPEN") : I18n.tr("closed"), m.localTime(), I18n.tr(m.country)});
        long t = WorldMarkets.lastPriceUpdate;
        worldUpdated.setText(a.demo ? I18n.tr("Practice data - made-up numbers.")
                : t == 0 ? I18n.tr("Index prices update every minute while StockBuddy is open.")
                : I18n.tr("Index prices update every minute (some exchanges are a few minutes delayed). Last update: ")
                + java.time.LocalTime.now().withNano(0).minusSeconds((System.currentTimeMillis() - t) / 1000) + ".");
    }

    private void updateStatus(String s) {
        if (statusLabel == null) return;
        statusLabel.setText(s);
        progress.setVisible(App.loading);
        refreshBtn.setEnabled(!App.loading);
    }

    public void openLookup(String ticker) {
        showPage("Look Up a Stock");
        lookupBox.getEditor().setItem(ticker);
        doLookup(ticker);
    }

    private void doLookup(String t) {
        if (t == null || t.isBlank()) return;
        String ticker = t.trim().toUpperCase().replace("$", "");
        lookupPane.setText(HtmlView.message(App.skin.palette(), I18n.tr("Looking up ") + ticker + "...", I18n.tr("One moment.")));
        Analyzer a = App.analyzer;
        new SwingWorker<Stock, Void>() {
            @Override protected Stock doInBackground() { return a.lookup(ticker); }
            @Override protected void done() {
                try {
                    Stock s = get();
                    if (s == null) {
                        lookupTicker = null;
                        chart.show(null, App.skin);
                        lookupPane.setText(HtmlView.message(App.skin.palette(), I18n.tr("Couldn't find \"") + ticker + "\"",
                                a.demo ? I18n.tr("Practice mode only knows these: ") + String.join(", ", a.stocks.keySet())
                                        : I18n.tr("Check the spelling, or your internet connection. Tip: US tickers are 1-5 letters, like AAPL or MSFT.")));
                    } else {
                        lookupTicker = s.ticker;
                        showStock(s);
                    }
                } catch (Exception ex) {
                    lookupPane.setText(HtmlView.message(App.skin.palette(), I18n.tr("Something went wrong"), String.valueOf(ex.getMessage())));
                }
            }
        }.execute();
    }

    private void showStock(Stock s) {
        if (s == null) return;
        chart.show(s, App.skin);
        lookupPane.setText(HtmlView.stock(App.skin.palette(), s));
        lookupPane.setCaretPosition(0);
    }

    void showPage(String p) {
        currentPage = p;
        cards.show(cardPanel, p);
        JToggleButton b = navButtons.get(p);
        if (b != null) b.setSelected(true);
    }

    // =============================================================== actions
    private void saveReport() {
        if (App.plan == null) { JOptionPane.showMessageDialog(this, I18n.tr("Please wait until the data has finished loading.")); return; }
        ReportWriter.Format[] formats = ReportWriter.Format.values();
        String[] labels = new String[formats.length];
        for (int i = 0; i < formats.length; i++) labels[i] = formats[i].label;
        String market = Settings.get("ideas.market", "all");
        Object choice = JOptionPane.showInputDialog(this, I18n.tr("Save today's report as:") + (market.equals("all") ? "" : I18n.tr("\n(only ") + shortMarket(market) + I18n.tr(", the market you picked)")),
                I18n.tr("Save report"), JOptionPane.PLAIN_MESSAGE, null, labels, labels[0]);
        if (choice == null) return;
        ReportWriter.Format f = formats[java.util.Arrays.asList(labels).indexOf(choice)];
        try {
            Path p = ReportWriter.write(f, App.analyzer, shownPlan());
            int r = JOptionPane.showConfirmDialog(this, I18n.tr("Saved your report:\n") + p.toAbsolutePath() + I18n.tr("\n\nOpen it now?"), I18n.tr("Report saved"), JOptionPane.YES_NO_OPTION);
            if (r == JOptionPane.YES_OPTION) {
                if (f == ReportWriter.Format.HTML) Desktop.getDesktop().browse(p.toUri());
                else Desktop.getDesktop().open(p.toFile());
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, I18n.tr("Couldn't save or open the report: ") + e.getMessage());
        }
    }

    private void importCsv(String target, String what) {
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle(I18n.tr("Choose your ") + what + I18n.tr(" CSV file"));
        fc.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(I18n.tr("CSV files"), "csv"));
        if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        try {
            Path dest = AppPaths.data(target);
            Files.createDirectories(dest.getParent());
            Files.copy(fc.getSelectedFile().toPath(), dest, StandardCopyOption.REPLACE_EXISTING);
            int rows = Csv.readByTicker(dest).size();
            StockRoverSource.reload();
            WallStreetZenSource.reload();
            JOptionPane.showMessageDialog(this, I18n.tr("Imported ") + rows + I18n.tr(" stocks from your ") + what + I18n.tr(" file.\nStockBuddy will now refresh."));
            App.refresh();
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, I18n.tr("Couldn't import the file: ") + e.getMessage());
        }
    }

    private void addAnyCsv() {
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle(I18n.tr("Choose any CSV file with a Ticker or Symbol column"));
        fc.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(I18n.tr("CSV files"), "csv"));
        if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        try {
            int n = CustomCsv.add(fc.getSelectedFile().toPath(), fc.getSelectedFile().getName());
            if (n == 0) {
                JOptionPane.showMessageDialog(this, I18n.tr("This file has no column called Ticker or Symbol in its first line, so StockBuddy can't use it."));
                return;
            }
            JOptionPane.showMessageDialog(this, I18n.tr("Added \"") + fc.getSelectedFile().getName() + I18n.tr("\" with ") + n + I18n.tr(" stocks.\n")
                    + I18n.tr("Its columns appear on each stock's page. Columns like score, rating, recommendation or target are also used in the score.\nStockBuddy will now refresh."));
            App.refresh();
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, I18n.tr("Couldn't add the file: ") + e.getMessage());
        }
    }

    private void askAlphaVantageKey() {
        String k = (String) JOptionPane.showInputDialog(this, I18n.tr("<html><div style='width:400px'><b>Alpha Vantage</b> adds news sentiment: is the news about a company ")
                + I18n.tr("positive or negative?<br><br>Get a free key at <b>alphavantage.co/support/#api-key</b> and paste it below. It is saved only on this computer. ")
                + I18n.tr("The free plan allows about 25 requests a day, so StockBuddy checks your first ") + AlphaVantage.DEFAULT_PER_DAY
                + I18n.tr(" stocks and remembers the answers all day.<br><br>Leave it empty to turn it off.</div></html>"),
                I18n.tr("Alpha Vantage key"), JOptionPane.PLAIN_MESSAGE, null, null, AlphaVantage.key());
        if (k == null) return;
        if (k.trim().isEmpty()) { AlphaVantage.setKey(""); return; }
        AlphaVantage.setKey(k);
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        new SwingWorker<String, Void>() {
            @Override protected String doInBackground() { return AlphaVantage.test(k.trim()); }
            @Override protected void done() {
                setCursor(Cursor.getDefaultCursor());
                String err;
                try { err = get(); } catch (Exception ex) { err = ex.getMessage(); }
                JOptionPane.showMessageDialog(MainWindow.this, err == null ? I18n.tr("Your Alpha Vantage key works and is saved. Click Refresh data to use it.") : err);
            }
        }.execute();
    }

    /** Asks for the free Finnhub key, tests it, and saves it on this computer only. */
    private void askFinnhubKey() {
        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.add(new JLabel(I18n.tr("<html><div style='width:420px'><b>Finnhub</b> adds Wall Street analyst recommendations and financial ratios ")
                + I18n.tr("(profit margin, debt, beta...).<br><br>1. Get a free key at <b>finnhub.io/register</b> (it's on your dashboard).<br>")
                + I18n.tr("2. Paste it below. It is saved only on this computer.<br><br>Leave it empty and click OK to turn Finnhub off.</div></html>")), BorderLayout.NORTH);
        JTextField f = new JTextField(FinnhubSource.key(), 30);
        panel.add(f, BorderLayout.CENTER);
        if (JOptionPane.showConfirmDialog(this, panel, I18n.tr("Finnhub key"), JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return;
        String k = f.getText().trim();
        if (k.isEmpty()) { FinnhubSource.setKey(""); JOptionPane.showMessageDialog(this, I18n.tr("Finnhub is turned off.")); return; }
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        new SwingWorker<String, Void>() {
            @Override protected String doInBackground() { return FinnhubSource.test(k); }
            @Override protected void done() {
                setCursor(Cursor.getDefaultCursor());
                String err;
                try { err = get(); } catch (Exception ex) { err = ex.getMessage(); }
                if (err == null) {
                    FinnhubSource.setKey(k);
                    JOptionPane.showMessageDialog(MainWindow.this, I18n.tr("Your Finnhub key works and is saved.\nStockBuddy will now refresh (the first time takes about 2 minutes)."));
                    App.refresh();
                } else {
                    int r = JOptionPane.showConfirmDialog(MainWindow.this, err + I18n.tr("\n\nSave the key anyway?"), I18n.tr("Finnhub key"), JOptionPane.YES_NO_OPTION);
                    if (r == JOptionPane.YES_OPTION) FinnhubSource.setKey(k);
                }
            }
        }.execute();
    }

    /** Lets you paste Reddit post titles yourself - handy when Reddit blocks automatic reading. */
    private void addRedditPosts() {
        Skin k = App.skin;
        JComboBox<String> sub = new JComboBox<>(new String[]{"wallstreetbets", I18n.tr("ValueInvesting"), "options", "stocks", "investing", "other"});
        sub.setEditable(true);
        JTextArea area = new JTextArea(12, 60);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        JPanel panel = new JPanel(new BorderLayout(0, 8));
        JLabel help = new JLabel(I18n.tr("<html><div style='width:460px'><b>How:</b> open the subreddit in your browser, copy the titles of the posts ")
                + I18n.tr("you find interesting, and paste them below - <b>one post per line</b>. Tickers like <b>$NVDA</b> or <b>NVDA</b> are ")
                + I18n.tr("recognised automatically. Your posts are used for ") + RedditSource.MANUAL_DAYS + I18n.tr(" days.<br><br>")
                + I18n.tr("Tip: a line can start with the subreddit, e.g. <i>r/options: SPY calls look good</i></div></html>"));
        JPanel top = new JPanel(new BorderLayout(0, 8));
        top.add(help, BorderLayout.NORTH);
        JPanel subRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        subRow.add(new JLabel(I18n.tr("Which subreddit are they from?")));
        subRow.add(sub);
        top.add(subRow, BorderLayout.SOUTH);
        panel.add(top, BorderLayout.NORTH);
        panel.add(new JScrollPane(area), BorderLayout.CENTER);
        area.setFont(k.font(Font.PLAIN, 14));
        if (JOptionPane.showConfirmDialog(this, panel, I18n.tr("Add Reddit posts"), JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE)
                != JOptionPane.OK_OPTION) return;
        try {
            int n = RedditSource.addManualPosts(String.valueOf(sub.getEditor().getItem()), area.getText());
            if (n == 0) { JOptionPane.showMessageDialog(this, I18n.tr("Nothing to add - paste at least one post title.")); return; }
            JOptionPane.showMessageDialog(this, I18n.tr("Added ") + n + I18n.tr(" post") + (n == 1 ? "" : "s") + I18n.tr(".\nStockBuddy will now refresh to include them."));
            App.refresh();
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, I18n.tr("Couldn't save the posts: ") + e.getMessage());
        }
    }

    private void importRedditCsv() {
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle(I18n.tr("Choose a CSV file with Reddit posts (needs a 'title' column)"));
        fc.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(I18n.tr("CSV files"), "csv"));
        if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        try {
            Path dest = RedditSource.manualFile();
            Path backup = dest.resolveSibling("reddit.backup.csv");
            if (Files.exists(dest)) Files.copy(dest, backup, StandardCopyOption.REPLACE_EXISTING);
            Files.createDirectories(dest.getParent());
            Files.copy(fc.getSelectedFile().toPath(), dest, StandardCopyOption.REPLACE_EXISTING);
            int n = RedditSource.manualPosts().size();
            if (n == 0) {
                if (Files.exists(backup)) Files.copy(backup, dest, StandardCopyOption.REPLACE_EXISTING); else Files.deleteIfExists(dest);
                JOptionPane.showMessageDialog(this, I18n.tr("No usable posts found. The file needs a first line with a column called 'title'\n")
                        + I18n.tr("(optional columns: date, subreddit, upvotes). Dates older than ") + RedditSource.MANUAL_DAYS + I18n.tr(" days are skipped."));
                return;
            }
            JOptionPane.showMessageDialog(this, I18n.tr("Imported ") + n + I18n.tr(" Reddit posts (this replaced the posts you had added before).\nStockBuddy will now refresh."));
            App.refresh();
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, I18n.tr("Couldn't import the file: ") + e.getMessage());
        }
    }

    private void openFolder(Path p) {
        try { Files.createDirectories(p); Desktop.getDesktop().open(p.toFile()); }
        catch (Exception e) { JOptionPane.showMessageDialog(this, I18n.tr("Your StockBuddy folder is:\n") + p.toAbsolutePath()); }
    }

    // =============================================================== small helpers
    private JEditorPane htmlPane() {
        HtmlView.swing = true;
        JEditorPane p = new JEditorPane("text/html", "") {
            @Override public void applyComponentOrientation(ComponentOrientation o) { /* pages align themselves; RTL here breaks line wrapping */ }
        };
        p.setEditable(false);
        p.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
        p.setBackground(App.skin.bg());
        p.addHyperlinkListener(e -> {
            String d = e.getDescription();
            if (e.getEventType() != javax.swing.event.HyperlinkEvent.EventType.ACTIVATED || d == null) return;
            if (d.startsWith("http")) { try { Desktop.getDesktop().browse(new java.net.URI(d)); } catch (Exception ex) { JOptionPane.showMessageDialog(this, d); } return; }
            if (d.startsWith("sb://trackdays/")) { Settings.set("track.days", Integer.parseInt(d.substring("sb://trackdays/".length()))); refreshContent(); return; }
            if (d.startsWith("sb://allmarket/") || d.startsWith("sb://market/")) {
                Settings.set("allstocks.market", d.substring(d.lastIndexOf('/') + 1));
                rebuild();
                showPage("All Stocks");
                return;
            }
            if (d.startsWith("sb://stock/")) openLookup(d.substring("sb://stock/".length()));
            else if (d.startsWith("sb://watch/")) addToWatchlist(d.substring("sb://watch/".length()));
            else if (d.equals("sb://page/calendar")) showPage("Economic Calendar");
            else if (d.startsWith("sb://wtop/")) {
                WorldStocks.setTop(Integer.parseInt(d.substring("sb://wtop/".length())));
                worldStocksPane.setText(HtmlView.worldStocks(App.skin.palette(), App.analyzer));
                worldStocksPane.setCaretPosition(0);
            }
            else if (d.startsWith("sb://alert/")) { showPage("Price Alerts"); alertField.setText(d.substring("sb://alert/".length()) + " above "); alertField.requestFocusInWindow(); }
            else if (d.startsWith("sb://alertdel/")) { Alerts.remove(d.substring("sb://alertdel/".length())); refreshAlertsAndPaper(); }
            else if (d.equals("sb://alertclear")) { Alerts.clearTriggered(); refreshAlertsAndPaper(); }
            else if (d.startsWith("sb://pbuy/")) paperBuy(d.substring("sb://pbuy/".length()));
            else if (d.startsWith("sb://psell/")) {
                String t = d.substring("sb://psell/".length());
                if (JOptionPane.showConfirmDialog(this, I18n.tr("Sell all your pretend ") + t + I18n.tr(" shares?"), I18n.tr("Paper trading"), JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION)
                    paperCommand("sell all " + t);
            } else if (d.equals("sb://preset")) {
                if (JOptionPane.showConfirmDialog(this, I18n.tr("Start again with $10,000 and delete all pretend trades?"), I18n.tr("Paper trading"), JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
                    PaperPortfolio.reset(); refreshAlertsAndPaper();
                }
            }
            else if (d.equals("sb://page/worldstocks")) showPage("World Stocks");
        });
        return p;
    }

    private JScrollPane scroll(JComponent c) {
        JScrollPane s = new JScrollPane(c);
        s.setBorder(BorderFactory.createLineBorder(App.skin.line()));
        s.getVerticalScrollBar().setUnitIncrement(18);
        s.getViewport().setBackground(App.skin.panel());
        return s;
    }

    private JPanel padded(Skin k, JComponent c) {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(k.bg());
        p.setBorder(new EmptyBorder(16, 18, 14, 18));
        p.add(c);
        return p;
    }

    private static JLabel label(String text, Font f, Color c) {
        JLabel l = new JLabel(text);
        l.setFont(f);
        l.setForeground(c);
        return l;
    }

    private static JButton button(String text, String tip) {
        JButton b = new JButton(text);
        b.setToolTipText(tip);
        b.setFocusPainted(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }

    private JTable table(Skin k, DefaultTableModel model) {
        JTable t = new JTable(model);
        t.setRowHeight(30);
        t.setFont(k.font(Font.PLAIN, 14));
        t.getTableHeader().setFont(k.font(Font.BOLD, 14));
        t.setAutoCreateRowSorter(true);
        t.setRowSorter(new TableRowSorter<>(model));
        t.setShowVerticalLines(false);
        t.setFillsViewportHeight(true);
        t.setBackground(k.panel());
        t.setForeground(k.text());
        DefaultTableCellRenderer r = new DefaultTableCellRenderer() {
            @Override public Component getTableCellRendererComponent(JTable tb, Object v, boolean sel, boolean foc, int row, int col) {
                String name = tb.getColumnName(col);
                Object shown = v;
                if (v instanceof Double d) {
                    if (Double.isNaN(d)) shown = "n/a";
                    else if (name.equals(I18n.tr("Price")) || name.equals(I18n.tr("Index price"))) shown = String.format("%,.2f", d);
                    else if (name.equals(I18n.tr("Score"))) shown = String.format("%+.0f", d);
                    else shown = String.format("%+.2f%%", d);
                }
                super.getTableCellRendererComponent(tb, shown, sel, foc, row, col);
                setBorder(new EmptyBorder(0, 10, 0, 10));
                setHorizontalAlignment(v instanceof Number ? RIGHT : LEFT);
                setBackground(sel ? k.soft() : row % 2 == 0 ? k.panel() : k.mix(k.panel(), k.bg(), 0.6));
                Color fg = k.text();
                if (v instanceof Double d && !name.equals(I18n.tr("Price")) && !name.equals(I18n.tr("Index price")) && !Double.isNaN(d)) fg = d > 0.05 ? k.good() : d < -0.05 ? k.bad() : k.warn();
                if (v instanceof String s) {
                    int scoreCol = -1;
                    for (int c = 0; c < tb.getColumnCount(); c++) if (tb.getColumnName(c).equals(I18n.tr("Score"))) scoreCol = c;
                    if (name.equals(I18n.tr("Verdict")) && scoreCol >= 0 && tb.getValueAt(row, scoreCol) instanceof Double) {
                        double sc = (Double) tb.getValueAt(row, scoreCol);
                        fg = sc >= 20 ? k.good() : sc <= -20 ? k.bad() : k.warn();
                    }
                    if (s.equals(I18n.tr("OPEN"))) fg = k.good();
                    else if (s.equals(I18n.tr("closed"))) fg = k.muted();
                }
                setForeground(fg);
                setFont(name.equals(I18n.tr("Ticker")) || name.equals(I18n.tr("Verdict")) ? k.font(Font.BOLD, 14) : k.font(Font.PLAIN, 14));
                return this;
            }
        };
        t.setDefaultRenderer(Object.class, r);
        t.setDefaultRenderer(String.class, r);
        t.setDefaultRenderer(Double.class, r);
        t.setDefaultRenderer(Integer.class, r);
        return t;
    }

    /** A small preview of a skin's colours, shown in the skin list. */
    static Icon swatch(Skin s) {
        return new Icon() {
            public int getIconWidth() { return 46; }
            public int getIconHeight() { return 16; }
            public void paintIcon(Component c, Graphics g, int x, int y) {
                Color[] cs = {s.bg(), s.accent(), s.good(), s.bad()};
                for (int i = 0; i < cs.length; i++) { g.setColor(cs[i]); g.fillRect(x + i * 11, y + 2, 11, 12); }
                g.setColor(Color.GRAY);
                g.drawRect(x, y + 2, 43, 12);
            }
        };
    }

    /** Compound border helper (keeps imports short). */
    private static final class CompoundBorderFix extends javax.swing.border.CompoundBorder {
        CompoundBorderFix(javax.swing.border.Border out, javax.swing.border.Border in) { super(out, in); }
    }
}
