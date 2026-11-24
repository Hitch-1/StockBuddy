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
    private static final String[] PAGES = {"Today's Ideas", "World Markets", "Look Up a Stock", "All Stocks",
            "Dictionary", "My Watchlist", "Data Sources"};
    private static final String[] PAGE_HINTS = {"Buys, sells, options, shorts and 0DTE", "The 16 biggest stock markets",
            "Full explanation and chart", "Every stock, best to worst", "Investing words made simple",
            "Stocks you always want checked", "Which websites worked"};

    private String currentPage = PAGES[0];
    private String lookupTicker;
    private int ideasTab;

    private final Runnable onData = this::refreshContent;
    private final Runnable onSkin = this::rebuild;
    private final java.util.function.Consumer<String> onStatus = s -> SwingUtilities.invokeLater(() -> updateStatus(s));

    // widgets that get refreshed when new data arrives
    private CardLayout cards;
    private JPanel cardPanel;
    private final Map<String, JToggleButton> navButtons = new LinkedHashMap<>();
    private final List<JToggleButton> ideaTabButtons = new ArrayList<>();
    private CardLayout ideasCards;
    private JPanel ideasCardPanel;
    private final List<JEditorPane> ideaPanes = new ArrayList<>();
    private JLabel worldMood;
    private DefaultTableModel worldModel, allModel;
    private JEditorPane lookupPane, glossaryPane, sourcesPane;
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
                App.removeListeners(onData, onSkin, onStatus);
                Settings.set("main.w", getWidth()); Settings.set("main.h", getHeight());
                App.exitIfNoWindows();
            }
        });
        App.onData(onData);
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
        root.add(sidebar(k), BorderLayout.WEST);

        cards = new CardLayout();
        cardPanel = new JPanel(cards);
        cardPanel.setBackground(k.bg());
        cardPanel.add(ideasPage(k), PAGES[0]);
        cardPanel.add(worldPage(k), PAGES[1]);
        cardPanel.add(lookupPage(k), PAGES[2]);
        cardPanel.add(allPage(k), PAGES[3]);
        cardPanel.add(glossaryPage(k), PAGES[4]);
        cardPanel.add(watchlistPage(k), PAGES[5]);
        cardPanel.add(sourcesPage(k), PAGES[6]);
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
            b.setMaximumSize(new Dimension(250, 58));
            b.setPreferredSize(new Dimension(240, 58));
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
        String[] names = {"Best Buys", "Best Sells", "Options", "Shorts", "0DTE (today)"};
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
        JPanel top = new JPanel(new GridLayout(2, 1));
        top.setOpaque(false);
        top.add(label("The world's biggest stock markets", k.font(Font.BOLD, 20), k.text()));
        worldMood = label(" ", k.font(Font.PLAIN, 15), k.accent());
        top.add(worldMood);
        p.add(top, BorderLayout.NORTH);
        worldModel = new DefaultTableModel(new Object[]{"Market", "Country", "Status", "Today", "Last month", "Local time"}, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
            @Override public Class<?> getColumnClass(int c) { return c == 3 || c == 4 ? Double.class : String.class; }
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
        p.add(bar, BorderLayout.NORTH);
        chart = new PriceChart(k);
        lookupPane = htmlPane();
        lookupPane.setText(HtmlView.message(k.palette(), "Look up a stock", "Type a ticker above and press Enter. You'll get a chart and a plain-English explanation of what every source says."));
        JPanel center = new JPanel(new BorderLayout(0, 10));
        center.setOpaque(false);
        center.add(chart, BorderLayout.NORTH);
        center.add(scroll(lookupPane), BorderLayout.CENTER);
        p.add(center, BorderLayout.CENTER);
        return padded(k, p);
    }

    private JComponent allPage(Skin k) {
        JPanel p = new JPanel(new BorderLayout(0, 10));
        p.setOpaque(false);
        p.add(label("Every stock I checked, best to worst", k.font(Font.BOLD, 20), k.text()), BorderLayout.NORTH);
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

    private JComponent watchlistPage(Skin k) {
        JPanel p = new JPanel(new BorderLayout(12, 10));
        p.setOpaque(false);
        p.add(label("<html><b style='font-size:16pt'>My watchlist</b><br>These stocks are always checked - plus today's biggest movers "
                + "and the stocks Reddit talks about most.</html>", k.font(Font.PLAIN, 14), k.text()), BorderLayout.NORTH);
        watchModel = new DefaultListModel<>();
        Analyzer.readWatchlist().forEach(watchModel::addElement);
        JList<String> list = new JList<>(watchModel);
        list.setFont(k.font(Font.BOLD, 15));
        list.setFixedCellHeight(28);
        p.add(scroll(list), BorderLayout.CENTER);

        JPanel btns = new JPanel();
        btns.setOpaque(false);
        btns.setLayout(new BoxLayout(btns, BoxLayout.Y_AXIS));
        JTextField add = new JTextField(10);
        add.setMaximumSize(new Dimension(220, 32));
        add.setToolTipText("Type one or more tickers separated by spaces");
        JButton addB = button("Add", "Add the ticker(s) typed above");
        JButton rem = button("Remove selected", null);
        JButton reset = button("Reset to default list", null);
        JButton save = button("Save and refresh", "Save the list and analyse it now");
        Runnable doAdd = () -> {
            for (String t : add.getText().toUpperCase().split("[\\s,]+")) {
                t = t.replace("$", "").trim();
                if (!t.isEmpty() && !watchModel.contains(t)) watchModel.addElement(t);
            }
            add.setText("");
        };
        add.addActionListener(e -> doAdd.run());
        addB.addActionListener(e -> doAdd.run());
        rem.addActionListener(e -> list.getSelectedValuesList().forEach(watchModel::removeElement));
        reset.addActionListener(e -> { watchModel.clear(); Analyzer.DEFAULT_WATCHLIST.forEach(watchModel::addElement); });
        save.addActionListener(e -> {
            try {
                List<String> l = new ArrayList<>();
                for (int i = 0; i < watchModel.size(); i++) l.add(watchModel.get(i));
                Analyzer.saveWatchlist(l);
                App.refresh();
            } catch (Exception ex) { JOptionPane.showMessageDialog(this, "Couldn't save: " + ex.getMessage()); }
        });
        btns.add(label("New ticker(s):", k.font(Font.PLAIN, 14), k.text()));
        for (JComponent c : new JComponent[]{add, addB, rem, reset, save}) {
            c.setAlignmentX(Component.LEFT_ALIGNMENT);
            btns.add(Box.createVerticalStrut(8));
            btns.add(c);
        }
        p.add(btns, BorderLayout.EAST);
        return padded(k, p);
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
        btns.add(rover); btns.add(zen); btns.add(folder); btns.add(auto);
        p.add(btns, BorderLayout.SOUTH);
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
        String[][] intro = {
                {"Best buys today", "Stocks where most sources see good signs. [+] = a source likes it, [-] = a source sees a problem. More stars = sources agree more.", "No stock had enough good signals today. That's OK - waiting is a strategy too."},
                {"Best sells (if you own them)", "Stocks where most sources see warning signs. If you don't own them, this simply means \"don't buy right now\".", "No stock had strong sell signals today."},
                {"Best options ideas (1-2 months)", "Calls (bet on up) or puts (bet on down) on stocks with strong, clear signals. Costs are estimates - check your broker.", "No stock had signals strong and clear enough for an options idea today."},
                {"Best short ideas (advanced)", "Stocks in a clear downtrend with weak ratings. Shorting is risky - buying a put is the safer way for beginners.", "No good short candidates today (we need a clear downtrend AND bad ratings)."},
                {"0DTE ideas (expire today)", "Options on SPY and QQQ that expire at today's close. Extremely risky - often the best idea is to sit out.", ""}};
        List<List<Advisor.Idea>> lists = List.of(plan.buys, plan.sells, plan.options, plan.shorts, plan.zeroDte);
        String[] tabNames = {"Best Buys", "Best Sells", "Options", "Shorts", "0DTE (today)"};
        for (int i = 0; i < 5; i++) {
            ideaPanes.get(i).setText(HtmlView.ideas(k.palette(), intro[i][0], intro[i][1], lists.get(i), intro[i][2], a.demo));
            ideaPanes.get(i).setCaretPosition(0);
            ideaTabButtons.get(i).setText(tabNames[i] + (i < 4 ? "  " + lists.get(i).size() : ""));
        }

        worldMood.setText(WorldMarkets.mood(a.markets));
        worldModel.setRowCount(0);
        for (WorldMarkets.Market m : a.markets)
            worldModel.addRow(new Object[]{m.name, m.country, m.isOpenNow() ? "OPEN" : "closed", m.changePct, m.monthPct, m.localTime()});

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
        sourcesPane.setCaretPosition(0);
        if (lookupTicker != null) showStock(a.stocks.get(lookupTicker));
    }

    private void updateStatus(String s) {
        if (statusLabel == null) return;
        statusLabel.setText(s);
        progress.setVisible(App.loading);
        refreshBtn.setEnabled(!App.loading);
    }

    public void openLookup(String ticker) {
        showPage(PAGES[2]);
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
        try {
            Path p = ReportWriter.write(App.analyzer, App.plan);
            int r = JOptionPane.showConfirmDialog(this, "Saved your report:\n" + p.toAbsolutePath() + "\n\nOpen it in your web browser now?",
                    "Report saved", JOptionPane.YES_NO_OPTION);
            if (r == JOptionPane.YES_OPTION) Desktop.getDesktop().browse(p.toUri());
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Couldn't save the report: " + e.getMessage());
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
            if (e.getEventType() == javax.swing.event.HyperlinkEvent.EventType.ACTIVATED && d != null && d.startsWith("sb://stock/"))
                openLookup(d.substring("sb://stock/".length()));
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
                    else if (name.equals("Price")) shown = String.format("%.2f", d);
                    else if (name.equals("Score")) shown = String.format("%+.0f", d);
                    else shown = String.format("%+.2f%%", d);
                }
                super.getTableCellRendererComponent(tb, shown, sel, foc, row, col);
                setBorder(new EmptyBorder(0, 10, 0, 10));
                setHorizontalAlignment(v instanceof Number ? RIGHT : LEFT);
                setBackground(sel ? k.soft() : row % 2 == 0 ? k.panel() : k.mix(k.panel(), k.bg(), 0.6));
                Color fg = k.text();
                if (v instanceof Double d && !name.equals("Price") && !Double.isNaN(d)) fg = d > 0.05 ? k.good() : d < -0.05 ? k.bad() : k.warn();
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
