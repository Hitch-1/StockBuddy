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
            "All Stocks", "Price Alerts", "Paper Trading", "Track Record", "Dictionary", "My Watchlist", "Data Sources"};
    private static final String[] PAGE_HINTS = {"Buys, sells, options, shorts and 0DTE", "The 16 biggest stock markets",
            "Big companies abroad + where to buy", "Big news this week", "Full explanation and chart", "Every stock checked today",
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
    private JLabel worldMood, worldUpdated;
    private DefaultTableModel worldModel, allModel;
    private JEditorPane lookupPane, glossaryPane, sourcesPane, worldStocksPane, calendarPane, trackPane, alertsPane, paperPane;
    private JTextField alertField, paperField;
    private PriceChart chart;
    private JComboBox<String> lookupBox;
    private DefaultListModel<String> watchModel;
    private JLabel statusLabel;
    private JProgressBar progress;
    private JButton refreshBtn;
    private JCheckBox practiceBox;

    public MainWindow() {
        super("StockBuddy - your friendly daily stock helper");
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
        root.add(side, BorderLayout.WEST);

        cards = new CardLayout();
        cardPanel = new JPanel(cards);
        cardPanel.setBackground(k.bg());
        worldStocksPane = htmlPane();
        calendarPane = htmlPane();
        trackPane = htmlPane();
        cardPanel.add(ideasPage(k), "Today's Ideas");
        cardPanel.add(worldPage(k), "World Markets");
        cardPanel.add(padded(k, scroll(worldStocksPane)), "World Stocks");
        cardPanel.add(padded(k, scroll(calendarPane)), "Economic Calendar");
        cardPanel.add(lookupPage(k), "Look Up a Stock");
        cardPanel.add(allPage(k), "All Stocks");
        cardPanel.add(commandPage(k, "alerts"), "Price Alerts");
        cardPanel.add(commandPage(k, "paper"), "Paper Trading");
        cardPanel.add(padded(k, scroll(trackPane)), "Track Record");
        cardPanel.add(glossaryPage(k), "Dictionary");
        cardPanel.add(watchlistPage(k), "My Watchlist");
        cardPanel.add(sourcesPage(k), "Data Sources");
        root.add(cardPanel, BorderLayout.CENTER);
        root.add(statusBar(k), BorderLayout.SOUTH);
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
        txt.add(label("StockBuddy", k.font(Font.BOLD, 22), k.text()));
        txt.add(label("Your friendly daily stock helper", k.font(Font.PLAIN, 13), k.muted()));
        brand.add(txt);
        h.add(brand, BorderLayout.WEST);

        JPanel tools = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 18));
        tools.setOpaque(false);
        refreshBtn = button("Refresh data", "Download the latest data from all websites");
        refreshBtn.addActionListener(e -> App.refresh());
        practiceBox = new JCheckBox("Practice mode", App.demo);
        practiceBox.setToolTipText("Use made-up data to learn safely (works without internet)");
        practiceBox.setOpaque(false);
        practiceBox.setForeground(k.text());
        practiceBox.addActionListener(e -> App.setDemo(practiceBox.isSelected()));

        JToggleButton skins = pill(k.name() + "  \u25BE", k);
        skins.setIcon(swatch(k));
        skins.setIconTextGap(8);
        skins.setFont(k.font(Font.PLAIN, 14));
        skins.setToolTipText("Change the colours of StockBuddy");
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
        JButton widgetBtn = button("Desktop widget", "Show a small always-visible StockBuddy panel on your desktop");
        widgetBtn.addActionListener(e -> App.showWidget());
        JButton save = button("Save report", "Save today's ideas as a web page you can open any time");
        save.addActionListener(e -> saveReport());

        tools.add(practiceBox);
        tools.add(label("Skin:", k.font(Font.PLAIN, 14), k.text()));
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
            JToggleButton b = new JToggleButton("<html><b>" + p + "</b><br><span style='font-size:10pt;color:"
                    + Skin.hex(k.muted()) + "'>" + PAGE_HINTS[i] + "</span></html>") {
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
        JLabel warn = new JLabel("<html><div style='width:190px'><b>Not financial advice.</b> StockBuddy can be wrong. "
                + "Never invest money you can't afford to lose.</div></html>");
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
            JToggleButton b = pill(names[i], k);
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

    /** "Market: ..." picker: filter today's ideas to one of the 16 biggest markets. */
    private JComponent marketPicker(Skin k) {
        String cur = Settings.get("ideas.market", "all");
        JToggleButton b = pill("Market: " + (cur.equals("all") ? "All markets" : shortMarket(cur)) + "  \u25BE", k);
        b.setToolTipText("Show ideas for one market only");
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
                JToggleButton item = smallPill(o[1], k, o[0].equals(cur));
                item.setHorizontalAlignment(SwingConstants.LEFT);
                item.addActionListener(a -> {
                    menu.setVisible(false);
                    Settings.set("ideas.market", o[0]);
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
        top.add(label("The world's biggest stock markets", k.font(Font.BOLD, 20), k.text()));
        worldMood = label(" ", k.font(Font.PLAIN, 15), k.accent());
        top.add(worldMood);
        worldUpdated = label(" ", k.font(Font.PLAIN, 12), k.muted());
        top.add(worldUpdated);
        p.add(top, BorderLayout.NORTH);
        worldModel = new DefaultTableModel(new Object[]{"Market", "Index price", "Today", "Last month", "Status", "Local time", "Country"}, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
            @Override public Class<?> getColumnClass(int c) { return c >= 1 && c <= 3 ? Double.class : String.class; }
        };
        JTable t = table(k, worldModel);
        t.getColumnModel().getColumn(0).setPreferredWidth(300);
        p.add(scroll(t), BorderLayout.CENTER);
        p.add(label("<html>An \"index\" is a basket of a country's biggest companies. Asian markets open first, then Europe, then the Americas - "
                + "a big move in Asia overnight often affects how Europe and the US start the day.</html>", k.font(Font.PLAIN, 13), k.muted()), BorderLayout.SOUTH);
        return padded(k, p);
    }

    private JComponent lookupPage(Skin k) {
        JPanel p = new JPanel(new BorderLayout(0, 10));
        p.setOpaque(false);
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        bar.setOpaque(false);
        bar.add(label("Type a ticker (like AAPL) or pick one:", k.font(Font.PLAIN, 15), k.text()));
        lookupBox = new JComboBox<>();
        lookupBox.setEditable(true);
        lookupBox.setPreferredSize(new Dimension(220, 32));
        JButton go = button("Explain this stock", null);
        go.addActionListener(e -> doLookup(String.valueOf(lookupBox.getEditor().getItem())));
        lookupBox.getEditor().addActionListener(e -> doLookup(String.valueOf(lookupBox.getEditor().getItem())));
        bar.add(lookupBox);
        bar.add(go);
        JButton watch = button("\u2605 Add to My Watchlist", "Always check this stock from now on");
        watch.addActionListener(e -> {
            String t = lookupTicker != null ? lookupTicker : String.valueOf(lookupBox.getEditor().getItem());
            addToWatchlist(t);
        });
        bar.add(watch);
        p.add(bar, BorderLayout.NORTH);
        chart = new PriceChart(k);
        lookupPane = htmlPane();
        lookupPane.setText(HtmlView.message(k.palette(), "Look up a stock", "Type a ticker above and press Enter. You'll get a chart and a plain-English explanation of what every source says."));
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

    /** A page with a "type a command" box on top and an HTML page below (alerts, paper trading). */
    private JComponent commandPage(Skin k, String which) {
        boolean alerts = which.equals("alerts");
        JPanel p = new JPanel(new BorderLayout(0, 10));
        p.setOpaque(false);
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        bar.setOpaque(false);
        bar.add(label(alerts ? "New alert:" : "Trade:", k.font(Font.BOLD, 15), k.text()));
        JTextField f = new JTextField(alerts ? 22 : 18);
        f.setFont(k.font(Font.PLAIN, 15));
        f.setToolTipText(alerts ? "For example: NVDA above 150   TSLA below 200   AAPL move 5%" : "For example: buy 10 AAPL   sell 5 AAPL   sell all AAPL");
        JButton go = button(alerts ? "Add alert" : "Do it", null);
        JLabel example = label(alerts ? "e.g.  NVDA above 150  \u00B7  TSLA below 200  \u00B7  AAPL move 5%" : "e.g.  buy 10 AAPL  \u00B7  sell 5 AAPL  \u00B7  sell all AAPL",
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
            JButton check = button("Check now", "Check all alerts with fresh prices");
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
        String n = (String) JOptionPane.showInputDialog(this, "How many pretend shares of " + ticker + " do you want to buy?\n(Pretend money - nothing real is bought.)",
                "Paper trading", JOptionPane.PLAIN_MESSAGE, null, null, "10");
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

        r1.add(label("Chart:", k.font(Font.BOLD, 13), k.muted()));
        ButtonGroup types = new ButtonGroup();
        for (int i = 0; i < ChartOptions.TYPES.length; i++) {
            String v = ChartOptions.TYPES[i];
            JToggleButton b = smallPill(ChartOptions.TYPE_NAMES[i], k, v.equals(o.type));
            b.addActionListener(e -> { o.type = v; o.save(); chart.repaint(); });
            types.add(b); r1.add(b);
        }
        r1.add(Box.createHorizontalStrut(14));
        r1.add(label("Period:", k.font(Font.BOLD, 13), k.muted()));
        ButtonGroup ranges = new ButtonGroup();
        for (int i = 0; i < ChartOptions.RANGES.length; i++) {
            int v = ChartOptions.RANGES[i];
            JToggleButton b = smallPill(ChartOptions.RANGE_NAMES[i], k, v == o.range);
            b.addActionListener(e -> { o.range = v; o.save(); chart.resetZoom(); });
            ranges.add(b); r1.add(b);
        }

        r2.add(label("Lines:", k.font(Font.BOLD, 13), k.muted()));
        for (int i = 0; i < ChartOptions.OVERLAYS.length; i++) {
            String v = ChartOptions.OVERLAYS[i];
            JToggleButton b = smallPill(ChartOptions.OVERLAY_NAMES[i], k, o.overlay(v));
            b.setToolTipText(v.equals("bollinger") ? "A band around the price: touching the top = expensive lately, the bottom = cheap lately"
                    : "The average price of the last " + v.replaceAll("\\D", "") + " days" + (v.startsWith("ema") ? " (recent days count more)" : ""));
            b.addActionListener(e -> { o.toggle(v); o.save(); chart.repaint(); });
            r2.add(b);
        }
        r2.add(Box.createHorizontalStrut(14));
        r2.add(label("Bottom panel:", k.font(Font.BOLD, 13), k.muted()));
        ButtonGroup lowers = new ButtonGroup();
        for (int i = 0; i < ChartOptions.LOWERS.length; i++) {
            String v = ChartOptions.LOWERS[i];
            JToggleButton b = smallPill(ChartOptions.LOWER_NAMES[i], k, v.equals(o.lower));
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
        p.add(label("Every stock I checked", k.font(Font.BOLD, 20), k.text()), BorderLayout.NORTH);
        allModel = new DefaultTableModel(new Object[]{"#", "Ticker", "Name", "Price", "Today", "Verdict", "Score", "Confidence"}, 0) {
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
        p.add(label("Tip: double-click a row to see the full explanation. Click a column title to sort.", k.font(Font.PLAIN, 13), k.muted()), BorderLayout.SOUTH);
        return padded(k, p);
    }

    private JComponent glossaryPage(Skin k) {
        JPanel p = new JPanel(new BorderLayout(0, 10));
        p.setOpaque(false);
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        bar.setOpaque(false);
        bar.add(label("Search a word:", k.font(Font.PLAIN, 15), k.text()));
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
        top.add(label("<html><b style='font-size:16pt'>My watchlist</b><br>These stocks are always checked - plus today's biggest movers "
                + "and the stocks Reddit talks about most. <b>Double-click a stock</b> to see its full explanation and chart.</html>",
                k.font(Font.PLAIN, 14), k.text()), BorderLayout.NORTH);
        JPanel addRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        addRow.setOpaque(false);
        addRow.add(label("Add ticker(s):", k.font(Font.BOLD, 14), k.text()));
        JTextField add = new JTextField(16);
        add.setFont(k.font(Font.PLAIN, 15));
        add.setToolTipText("For example: AAPL  or several at once: AAPL MSFT NVDA");
        JButton addB = button("Add", "Add the ticker(s) - saved straight away");
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
                String extra = s == null ? "" : "   " + (s.name == null ? "" : s.name + "   ") + Advisor.money(s) + "   " + Scorer.verdict(s.score);
                setText("<html><b>" + v + "</b><span style='color:" + Skin.hex(k.muted()) + "'>" + HtmlView.esc(extra) + "</span></html>");
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
        JButton look = button("Look up selected", "See the full explanation and chart");
        JButton rem = button("Remove selected", "Removed straight away");
        JButton reset = button("Reset to default list", null);
        JButton refresh = button("Analyse my list now", "Download fresh data for your watchlist");
        Runnable doAdd = () -> {
            List<String> added = addToWatchlistQuiet(add.getText());
            add.setText("");
            watchMsg.setForeground(added.isEmpty() ? k.warn() : k.good());
            watchMsg.setText(added.isEmpty() ? "Type a ticker like AAPL (letters, 1-6 characters)."
                    : "Added " + String.join(", ", added) + " - saved. Click \"Analyse my list now\" to check it.");
        };
        add.addActionListener(e -> doAdd.run());
        addB.addActionListener(e -> doAdd.run());
        look.addActionListener(e -> { if (list.getSelectedValue() != null) openLookup(list.getSelectedValue()); });
        rem.addActionListener(e -> {
            List<String> gone = list.getSelectedValuesList();
            gone.forEach(watchModel::removeElement);
            saveWatchModel();
            if (!gone.isEmpty()) { watchMsg.setForeground(k.good()); watchMsg.setText("Removed " + String.join(", ", gone) + "."); }
        });
        reset.addActionListener(e -> {
            if (JOptionPane.showConfirmDialog(this, "Replace your watchlist with the default list?", "Reset watchlist",
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
            catch (Exception ex) { JOptionPane.showMessageDialog(this, "Couldn't save the watchlist: " + ex.getMessage()); return new ArrayList<>(); }
            if (watchModel != null) for (String t : added) if (!watchModel.contains(t)) watchModel.addElement(t);
        }
        return added;
    }

    /** "Add to My Watchlist" from the Look Up page. */
    private void addToWatchlist(String ticker) {
        if (ticker == null || ticker.trim().isEmpty() || ticker.equals("null")) {
            JOptionPane.showMessageDialog(this, "Look up a stock first, then add it.");
            return;
        }
        String t = ticker.trim().toUpperCase();
        if (Analyzer.readWatchlist().contains(t)) { JOptionPane.showMessageDialog(this, t + " is already in your watchlist."); return; }
        List<String> added = addToWatchlistQuiet(t);
        JOptionPane.showMessageDialog(this, added.isEmpty() ? "\"" + t + "\" doesn't look like a ticker." : "Added " + t + " to your watchlist.");
    }

    private void saveWatchModel() {
        try {
            List<String> l = new ArrayList<>();
            for (int i = 0; i < watchModel.size(); i++) l.add(watchModel.get(i));
            Analyzer.saveWatchlist(l);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Couldn't save: " + ex.getMessage());
        }
    }

    private JComponent sourcesPage(Skin k) {
        JPanel p = new JPanel(new BorderLayout(0, 10));
        p.setOpaque(false);
        sourcesPane = htmlPane();
        p.add(scroll(sourcesPane), BorderLayout.CENTER);
        JPanel btns = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        btns.setOpaque(false);
        JButton rover = button("Import Stock Rover CSV...", "Choose the CSV file you exported from Stock Rover");
        rover.addActionListener(e -> importCsv("stockrover.csv", "Stock Rover"));
        JButton zen = button("Import WallStreetZen CSV...", "A CSV with Ticker, Zen Score and Zen Rating columns");
        zen.addActionListener(e -> importCsv("wallstreetzen.csv", "WallStreetZen"));
        JButton folder = button("Open my StockBuddy folder", "Where your watchlist, CSV files and reports are kept");
        folder.addActionListener(e -> openFolder(AppPaths.base()));
        JButton auto = button(Autostart.isEnabled() ? "Don't start widget with computer" : "Start widget when computer starts", null);
        auto.addActionListener(e -> {
            boolean on = !Autostart.isEnabled();
            String err = Autostart.set(on);
            if (err != null) JOptionPane.showMessageDialog(this, err);
            auto.setText(Autostart.isEnabled() ? "Don't start widget with computer" : "Start widget when computer starts");
        });
        JButton fin = button(FinnhubSource.enabled() ? "Finnhub key: set (change...)" : "Add Finnhub key...",
                "Free key from finnhub.io: adds analyst recommendations and financial ratios");
        fin.addActionListener(e -> {
            askFinnhubKey();
            fin.setText(FinnhubSource.enabled() ? "Finnhub key: set (change...)" : "Add Finnhub key...");
        });
        JButton anyCsv = button("Add any CSV...", "Any CSV with a Ticker or Symbol column becomes a data source");
        anyCsv.addActionListener(e -> addAnyCsv());
        JButton noCsv = button("Remove my CSV files", null);
        noCsv.addActionListener(e -> {
            if (JOptionPane.showConfirmDialog(this, "Remove all the CSV files you added with \"Add any CSV\"?", "Remove CSV files",
                    JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
            try { CustomCsv.removeAll(); App.refresh(); } catch (Exception ex) { JOptionPane.showMessageDialog(this, "Couldn't remove them: " + ex.getMessage()); }
        });
        JButton av = button(AlphaVantage.enabled() ? "Alpha Vantage key: set (change...)" : "Add Alpha Vantage key...", "Free key from alphavantage.co: news sentiment");
        av.addActionListener(e -> {
            askAlphaVantageKey();
            av.setText(AlphaVantage.enabled() ? "Alpha Vantage key: set (change...)" : "Add Alpha Vantage key...");
        });
        JButton sec = button(SecEdgar.email().isEmpty() ? "SEC email (optional)..." : "SEC email: set (change...)", "The SEC asks programs to say who is asking");
        sec.addActionListener(e -> {
            String v = (String) JOptionPane.showInputDialog(this, "<html><div style='width:380px'>The SEC (sec.gov) asks every program to say who it is. "
                    + "StockBuddy works without this, but if the SEC data doesn't load, enter your email here. It is only ever sent to sec.gov.</div></html>",
                    "SEC EDGAR", JOptionPane.PLAIN_MESSAGE, null, null, SecEdgar.email());
            if (v == null) return;
            SecEdgar.setEmail(v);
            sec.setText(SecEdgar.email().isEmpty() ? "SEC email (optional)..." : "SEC email: set (change...)");
        });
        JCheckBox world = new JCheckBox("Include world stocks (choose how many on the World Stocks page)", WorldStocks.enabled());
        world.setOpaque(false);
        world.setForeground(k.text());
        world.setToolTipText("Also analyse the biggest companies of the other 13 big stock markets (refreshing takes a bit longer)");
        world.addActionListener(e -> { WorldStocks.setTop(world.isSelected() ? 3 : 0); App.refresh(); });

        JPanel files = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        files.setOpaque(false);
        files.add(label("Your files:", k.font(Font.BOLD, 14), k.text()));
        files.add(rover); files.add(zen); files.add(anyCsv); files.add(noCsv);
        btns.add(label("Keys & settings:", k.font(Font.BOLD, 14), k.text()));
        btns.add(fin); btns.add(av); btns.add(sec); btns.add(world);
        JPanel app = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        app.setOpaque(false);
        app.add(label("This computer:", k.font(Font.BOLD, 14), k.text()));
        app.add(folder); app.add(auto);

        JPanel reddit = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        reddit.setOpaque(false);
        JLabel rl = new JLabel("Reddit:");
        rl.setFont(k.font(Font.BOLD, 14));
        rl.setForeground(k.text());
        JButton addPosts = button("Add Reddit posts...", "Paste post titles you copied from Reddit (works even when Reddit blocks StockBuddy)");
        addPosts.addActionListener(e -> addRedditPosts());
        JButton redditCsv = button("Import Reddit CSV...", "A CSV with a title column (optional: date, subreddit, upvotes)");
        redditCsv.addActionListener(e -> importRedditCsv());
        JButton clear = button("Clear my Reddit posts", "Delete the posts you added yourself");
        clear.addActionListener(e -> {
            if (JOptionPane.showConfirmDialog(this, "Delete all the Reddit posts you added yourself?", "Clear my Reddit posts",
                    JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
            try { RedditSource.clearManualPosts(); App.refresh(); }
            catch (Exception ex) { JOptionPane.showMessageDialog(this, "Couldn't delete them: " + ex.getMessage()); }
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
        setTitle("StockBuddy - your friendly daily stock helper" + (App.demo ? "   [PRACTICE MODE - made-up data]" : ""));

        if (plan == null) {
            String msg = HtmlView.message(k.palette(), "Getting today's data...", "StockBuddy is reading stockanalysis.com, WallStreetZen, Reddit, your Stock Rover file and price charts. This can take a minute.");
            ideaPanes.forEach(p -> p.setText(msg));
            sourcesPane.setText(msg);
            return;
        }
        Advisor.Plan shown = shownPlan();
        List<List<Advisor.Idea>> lists = HtmlView.ideaLists(shown);
        for (int i = 0; i < 5; i++) {
            ideaPanes.get(i).setText(HtmlView.ideasTab(k.palette(), shown, i, a.demo));
            ideaPanes.get(i).setCaretPosition(0);
            ideaTabButtons.get(i).setText(HtmlView.IDEA_TABS[i] + (i < 4 ? "  " + lists.get(i).size() : ""));
        }

        refreshMarkets();

        allModel.setRowCount(0);
        List<Stock> list = new ArrayList<>(a.stocks.values());
        list.sort((x, y) -> Double.compare(y.score, x.score));
        int n = 1;
        for (Stock s : list)
            allModel.addRow(new Object[]{n++, s.ticker, s.name == null ? "" : s.name, s.price, s.changePct, Scorer.verdict(s.score),
                    Math.round(s.score * 100) * 1.0, HtmlView.prettyStars(Scorer.stars(s.confidence))});

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
            worldModel.addRow(new Object[]{m.name, m.price, m.changePct, m.monthPct, m.isOpenNow() ? "OPEN" : "closed", m.localTime(), m.country});
        long t = WorldMarkets.lastPriceUpdate;
        worldUpdated.setText(a.demo ? "Practice data - made-up numbers."
                : t == 0 ? "Index prices update every minute while StockBuddy is open."
                : "Index prices update every minute (some exchanges are a few minutes delayed). Last update: "
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
        lookupPane.setText(HtmlView.message(App.skin.palette(), "Looking up " + ticker + "...", "One moment."));
        Analyzer a = App.analyzer;
        new SwingWorker<Stock, Void>() {
            @Override protected Stock doInBackground() { return a.lookup(ticker); }
            @Override protected void done() {
                try {
                    Stock s = get();
                    if (s == null) {
                        lookupTicker = null;
                        chart.show(null, App.skin);
                        lookupPane.setText(HtmlView.message(App.skin.palette(), "Couldn't find \"" + ticker + "\"",
                                a.demo ? "Practice mode only knows these: " + String.join(", ", a.stocks.keySet())
                                        : "Check the spelling, or your internet connection. Tip: US tickers are 1-5 letters, like AAPL or MSFT."));
                    } else {
                        lookupTicker = s.ticker;
                        showStock(s);
                    }
                } catch (Exception ex) {
                    lookupPane.setText(HtmlView.message(App.skin.palette(), "Something went wrong", String.valueOf(ex.getMessage())));
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
        if (App.plan == null) { JOptionPane.showMessageDialog(this, "Please wait until the data has finished loading."); return; }
        ReportWriter.Format[] formats = ReportWriter.Format.values();
        String[] labels = new String[formats.length];
        for (int i = 0; i < formats.length; i++) labels[i] = formats[i].label;
        String market = Settings.get("ideas.market", "all");
        Object choice = JOptionPane.showInputDialog(this, "Save today's report as:" + (market.equals("all") ? "" : "\n(only " + shortMarket(market) + ", the market you picked)"),
                "Save report", JOptionPane.PLAIN_MESSAGE, null, labels, labels[0]);
        if (choice == null) return;
        ReportWriter.Format f = formats[java.util.Arrays.asList(labels).indexOf(choice)];
        try {
            Path p = ReportWriter.write(f, App.analyzer, shownPlan());
            int r = JOptionPane.showConfirmDialog(this, "Saved your report:\n" + p.toAbsolutePath() + "\n\nOpen it now?", "Report saved", JOptionPane.YES_NO_OPTION);
            if (r == JOptionPane.YES_OPTION) {
                if (f == ReportWriter.Format.HTML) Desktop.getDesktop().browse(p.toUri());
                else Desktop.getDesktop().open(p.toFile());
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Couldn't save or open the report: " + e.getMessage());
        }
    }

    private void importCsv(String target, String what) {
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Choose your " + what + " CSV file");
        fc.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("CSV files", "csv"));
        if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        try {
            Path dest = AppPaths.data(target);
            Files.createDirectories(dest.getParent());
            Files.copy(fc.getSelectedFile().toPath(), dest, StandardCopyOption.REPLACE_EXISTING);
            int rows = Csv.readByTicker(dest).size();
            StockRoverSource.reload();
            WallStreetZenSource.reload();
            JOptionPane.showMessageDialog(this, "Imported " + rows + " stocks from your " + what + " file.\nStockBuddy will now refresh.");
            App.refresh();
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Couldn't import the file: " + e.getMessage());
        }
    }

    private void addAnyCsv() {
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Choose any CSV file with a Ticker or Symbol column");
        fc.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("CSV files", "csv"));
        if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        try {
            int n = CustomCsv.add(fc.getSelectedFile().toPath(), fc.getSelectedFile().getName());
            if (n == 0) {
                JOptionPane.showMessageDialog(this, "This file has no column called Ticker or Symbol in its first line, so StockBuddy can't use it.");
                return;
            }
            JOptionPane.showMessageDialog(this, "Added \"" + fc.getSelectedFile().getName() + "\" with " + n + " stocks.\n"
                    + "Its columns appear on each stock's page. Columns like score, rating, recommendation or target are also used in the score.\nStockBuddy will now refresh.");
            App.refresh();
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Couldn't add the file: " + e.getMessage());
        }
    }

    private void askAlphaVantageKey() {
        String k = (String) JOptionPane.showInputDialog(this, "<html><div style='width:400px'><b>Alpha Vantage</b> adds news sentiment: is the news about a company "
                + "positive or negative?<br><br>Get a free key at <b>alphavantage.co/support/#api-key</b> and paste it below. It is saved only on this computer. "
                + "The free plan allows about 25 requests a day, so StockBuddy checks your first " + AlphaVantage.DEFAULT_PER_DAY
                + " stocks and remembers the answers all day.<br><br>Leave it empty to turn it off.</div></html>",
                "Alpha Vantage key", JOptionPane.PLAIN_MESSAGE, null, null, AlphaVantage.key());
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
                JOptionPane.showMessageDialog(MainWindow.this, err == null ? "Your Alpha Vantage key works and is saved. Click Refresh data to use it." : err);
            }
        }.execute();
    }

    /** Asks for the free Finnhub key, tests it, and saves it on this computer only. */
    private void askFinnhubKey() {
        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.add(new JLabel("<html><div style='width:420px'><b>Finnhub</b> adds Wall Street analyst recommendations and financial ratios "
                + "(profit margin, debt, beta...).<br><br>1. Get a free key at <b>finnhub.io/register</b> (it's on your dashboard).<br>"
                + "2. Paste it below. It is saved only on this computer.<br><br>Leave it empty and click OK to turn Finnhub off.</div></html>"), BorderLayout.NORTH);
        JTextField f = new JTextField(FinnhubSource.key(), 30);
        panel.add(f, BorderLayout.CENTER);
        if (JOptionPane.showConfirmDialog(this, panel, "Finnhub key", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return;
        String k = f.getText().trim();
        if (k.isEmpty()) { FinnhubSource.setKey(""); JOptionPane.showMessageDialog(this, "Finnhub is turned off."); return; }
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        new SwingWorker<String, Void>() {
            @Override protected String doInBackground() { return FinnhubSource.test(k); }
            @Override protected void done() {
                setCursor(Cursor.getDefaultCursor());
                String err;
                try { err = get(); } catch (Exception ex) { err = ex.getMessage(); }
                if (err == null) {
                    FinnhubSource.setKey(k);
                    JOptionPane.showMessageDialog(MainWindow.this, "Your Finnhub key works and is saved.\nStockBuddy will now refresh (the first time takes about 2 minutes).");
                    App.refresh();
                } else {
                    int r = JOptionPane.showConfirmDialog(MainWindow.this, err + "\n\nSave the key anyway?", "Finnhub key", JOptionPane.YES_NO_OPTION);
                    if (r == JOptionPane.YES_OPTION) FinnhubSource.setKey(k);
                }
            }
        }.execute();
    }

    /** Lets you paste Reddit post titles yourself - handy when Reddit blocks automatic reading. */
    private void addRedditPosts() {
        Skin k = App.skin;
        JComboBox<String> sub = new JComboBox<>(new String[]{"wallstreetbets", "ValueInvesting", "options", "stocks", "investing", "other"});
        sub.setEditable(true);
        JTextArea area = new JTextArea(12, 60);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        JPanel panel = new JPanel(new BorderLayout(0, 8));
        JLabel help = new JLabel("<html><div style='width:460px'><b>How:</b> open the subreddit in your browser, copy the titles of the posts "
                + "you find interesting, and paste them below - <b>one post per line</b>. Tickers like <b>$NVDA</b> or <b>NVDA</b> are "
                + "recognised automatically. Your posts are used for " + RedditSource.MANUAL_DAYS + " days.<br><br>"
                + "Tip: a line can start with the subreddit, e.g. <i>r/options: SPY calls look good</i></div></html>");
        JPanel top = new JPanel(new BorderLayout(0, 8));
        top.add(help, BorderLayout.NORTH);
        JPanel subRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        subRow.add(new JLabel("Which subreddit are they from?"));
        subRow.add(sub);
        top.add(subRow, BorderLayout.SOUTH);
        panel.add(top, BorderLayout.NORTH);
        panel.add(new JScrollPane(area), BorderLayout.CENTER);
        area.setFont(k.font(Font.PLAIN, 14));
        if (JOptionPane.showConfirmDialog(this, panel, "Add Reddit posts", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE)
                != JOptionPane.OK_OPTION) return;
        try {
            int n = RedditSource.addManualPosts(String.valueOf(sub.getEditor().getItem()), area.getText());
            if (n == 0) { JOptionPane.showMessageDialog(this, "Nothing to add - paste at least one post title."); return; }
            JOptionPane.showMessageDialog(this, "Added " + n + " post" + (n == 1 ? "" : "s") + ".\nStockBuddy will now refresh to include them.");
            App.refresh();
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Couldn't save the posts: " + e.getMessage());
        }
    }

    private void importRedditCsv() {
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Choose a CSV file with Reddit posts (needs a 'title' column)");
        fc.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("CSV files", "csv"));
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
                JOptionPane.showMessageDialog(this, "No usable posts found. The file needs a first line with a column called 'title'\n"
                        + "(optional columns: date, subreddit, upvotes). Dates older than " + RedditSource.MANUAL_DAYS + " days are skipped.");
                return;
            }
            JOptionPane.showMessageDialog(this, "Imported " + n + " Reddit posts (this replaced the posts you had added before).\nStockBuddy will now refresh.");
            App.refresh();
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Couldn't import the file: " + e.getMessage());
        }
    }

    private void openFolder(Path p) {
        try { Files.createDirectories(p); Desktop.getDesktop().open(p.toFile()); }
        catch (Exception e) { JOptionPane.showMessageDialog(this, "Your StockBuddy folder is:\n" + p.toAbsolutePath()); }
    }

    // =============================================================== small helpers
    private JEditorPane htmlPane() {
        JEditorPane p = new JEditorPane("text/html", "");
        p.setEditable(false);
        p.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
        p.setBackground(App.skin.bg());
        p.addHyperlinkListener(e -> {
            String d = e.getDescription();
            if (e.getEventType() != javax.swing.event.HyperlinkEvent.EventType.ACTIVATED || d == null) return;
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
                if (JOptionPane.showConfirmDialog(this, "Sell all your pretend " + t + " shares?", "Paper trading", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION)
                    paperCommand("sell all " + t);
            } else if (d.equals("sb://preset")) {
                if (JOptionPane.showConfirmDialog(this, "Start again with $10,000 and delete all pretend trades?", "Paper trading", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
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
                    else if (name.equals("Price") || name.equals("Index price")) shown = String.format("%,.2f", d);
                    else if (name.equals("Score")) shown = String.format("%+.0f", d);
                    else shown = String.format("%+.2f%%", d);
                }
                super.getTableCellRendererComponent(tb, shown, sel, foc, row, col);
                setBorder(new EmptyBorder(0, 10, 0, 10));
                setHorizontalAlignment(v instanceof Number ? RIGHT : LEFT);
                setBackground(sel ? k.soft() : row % 2 == 0 ? k.panel() : k.mix(k.panel(), k.bg(), 0.6));
                Color fg = k.text();
                if (v instanceof Double d && !name.equals("Price") && !name.equals("Index price") && !Double.isNaN(d)) fg = d > 0.05 ? k.good() : d < -0.05 ? k.bad() : k.warn();
                if (v instanceof String s) {
                    if (s.contains("BUY") || s.equals("OPEN")) fg = k.good();
                    else if (s.contains("SELL")) fg = k.bad();
                    else if (s.startsWith("HOLD")) fg = k.warn();
                    else if (s.equals("closed")) fg = k.muted();
                }
                setForeground(fg);
                setFont(name.equals("Ticker") || name.equals("Verdict") ? k.font(Font.BOLD, 14) : k.font(Font.PLAIN, 14));
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
