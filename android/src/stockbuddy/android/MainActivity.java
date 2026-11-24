package stockbuddy.android;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import stockbuddy.Advisor;
import stockbuddy.Analyzer;
import stockbuddy.AppPaths;
import stockbuddy.Csv;
import stockbuddy.HtmlView;
import stockbuddy.Palette;
import stockbuddy.Settings;
import stockbuddy.Stock;
import stockbuddy.StockRoverSource;
import stockbuddy.WallStreetZenSource;

/** The StockBuddy phone app. Pages are drawn as web pages (shared with the desktop app) inside a WebView. */
public class MainActivity extends Activity implements Droid.Listener {
    private static final int PICK_ROVER = 11, PICK_ZEN = 12, PICK_REDDIT = 13, PICK_CSV = 14, SAVE_REPORT = 15;
    private stockbuddy.ReportWriter.Format pendingFormat;
    private TextView marketChip;
    private static final String[] IDEA_TABS = HtmlView.IDEA_TABS;
    private static final String[] NAV = {"Ideas", "Markets", "Look up", "All stocks", "More"};

    private Palette k;
    private String page = "ideas";
    private int ideaTab;
    private String lookupTicker, filter = "";
    private Stock lookedUp;

    private WebView web;
    private TextView statusText, subtitle;
    private ProgressBar progress;
    private HorizontalScrollView chipScroll;
    private final List<TextView> chips = new ArrayList<>();
    private final List<TextView> navItems = new ArrayList<>();
    private LinearLayout inputRow;
    private EditText input;
    private Button inputBtn;
    private TextWatcher filterWatcher;
    private final Handler ticker = new Handler(Looper.getMainLooper());
    private volatile boolean marketsLoading;
    /** Every minute: refresh the world index prices while the Markets page is open. */
    private final Runnable marketTick = new Runnable() {
        @Override public void run() {
            final Analyzer a = Droid.analyzer;
            if (page.equals("markets") && a != null && !a.demo && !Droid.loading && !marketsLoading) {
                marketsLoading = true;
                new Thread(() -> {
                    try { stockbuddy.WorldMarkets.refreshPrices(a.markets); } catch (Throwable ignored) {}
                    marketsLoading = false;
                    runOnUiThread(() -> { if (page.equals("markets")) render(); });
                }).start();
            }
            ticker.postDelayed(this, 60_000);
        }
    };

    // ================================================================= lifecycle
    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        Droid.init(this);
        installCrashReporter();
        if (saved != null) {
            page = saved.getString("page", page);
            ideaTab = saved.getInt("ideaTab", 0);
            lookupTicker = saved.getString("lookup");
        }
        if (getIntent() != null && getIntent().getStringExtra("page") != null) page = getIntent().getStringExtra("page");
        build();
        Droid.listener = this;
        if (Droid.plan == null && !Droid.loading) Droid.refresh(this, null);
        else render();
        showPreviousCrash();
        Notifier.scheduleAlertJob(this);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (intent != null && intent.getStringExtra("page") != null) show(intent.getStringExtra("page"));
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putString("page", page);
        out.putInt("ideaTab", ideaTab);
        out.putString("lookup", lookupTicker);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Droid.listener = this;
        render();
        ticker.removeCallbacks(marketTick);
        ticker.postDelayed(marketTick, 60_000);
    }

    @Override
    protected void onPause() {
        ticker.removeCallbacks(marketTick);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (Droid.listener == this) Droid.listener = null;
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (!page.equals("ideas")) show("ideas");
        else super.onBackPressed();
    }

    @Override public void onStatus(String s) { if (statusText != null) { statusText.setText(s); progress.setVisibility(Droid.loading ? View.VISIBLE : View.GONE); } }
    @Override public void onDone() { render(); }

    // ================================================================= layout
    private int dp(float v) { return Math.round(v * getResources().getDisplayMetrics().density); }
    private int c(String hex) { return Palette.argb(hex); }

    private void build() {
        k = Droid.appSkin();
        chips.clear();
        navItems.clear();
        getWindow().setStatusBarColor(c(k.panel()));
        getWindow().setNavigationBarColor(c(k.panel()));
        getWindow().getDecorView().setSystemUiVisibility(k.dark() ? 0 : View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(c(k.bg()));

        // ---- header
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(14), dp(10), dp(8), dp(8));
        header.setBackgroundColor(c(k.panel()));
        android.widget.ImageView logo = new android.widget.ImageView(this);
        logo.setImageResource(com.stockbuddy.app.R.drawable.icon);
        header.addView(logo, new LinearLayout.LayoutParams(dp(34), dp(34)));
        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.setPadding(dp(10), 0, 0, 0);
        TextView title = text("StockBuddy", 20, k.text(), true);
        subtitle = text("", 12, k.muted(), false);
        titles.addView(title);
        titles.addView(subtitle);
        header.addView(titles, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView skinBtn = pillButton("Skin");
        skinBtn.setOnClickListener(v -> chooseSkin(false));
        TextView refreshBtn = pillButton("↻");
        refreshBtn.setTextSize(18);
        refreshBtn.setOnClickListener(v -> Droid.refresh(this, null));
        header.addView(skinBtn);
        header.addView(refreshBtn);
        root.addView(header);

        // ---- status line
        LinearLayout statusRow = new LinearLayout(this);
        statusRow.setOrientation(LinearLayout.VERTICAL);
        statusRow.setPadding(dp(14), dp(4), dp(14), dp(4));
        statusRow.setBackgroundColor(c(k.panel()));
        statusText = text(Droid.status, 12, k.muted(), false);
        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setIndeterminate(true);
        statusRow.addView(statusText);
        statusRow.addView(progress, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(6)));
        root.addView(statusRow);
        View line = new View(this);
        line.setBackgroundColor(c(k.line()));
        root.addView(line, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1));

        // ---- idea category chips
        chipScroll = new HorizontalScrollView(this);
        chipScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout chipRow = new LinearLayout(this);
        chipRow.setPadding(dp(10), dp(8), dp(10), dp(4));
        marketChip = pillButton("All markets \u25BE");
        marketChip.setOnClickListener(v -> chooseMarket());
        chipRow.addView(marketChip);
        for (int i = 0; i < IDEA_TABS.length; i++) {
            final int idx = i;
            TextView t = pillButton(IDEA_TABS[i]);
            t.setOnClickListener(v -> { ideaTab = idx; render(); });
            chips.add(t);
            chipRow.addView(t);
        }
        chipScroll.addView(chipRow);
        root.addView(chipScroll);

        // ---- text input row (look up / search / add to watchlist)
        inputRow = new LinearLayout(this);
        inputRow.setGravity(Gravity.CENTER_VERTICAL);
        inputRow.setPadding(dp(10), dp(8), dp(10), dp(4));
        input = new EditText(this);
        input.setSingleLine(true);
        input.setTextColor(c(k.text()));
        input.setHintTextColor(c(k.muted()));
        input.setBackground(rounded(k.panel(), k.line(), 10));
        input.setPadding(dp(12), dp(8), dp(12), dp(8));
        input.setImeOptions(EditorInfo.IME_ACTION_GO);
        input.setOnEditorActionListener((v, id, ev) -> {
            if (id == EditorInfo.IME_ACTION_GO || (ev != null && ev.getKeyCode() == KeyEvent.KEYCODE_ENTER)) { inputAction(); return true; }
            return false;
        });
        inputBtn = new Button(this);
        inputBtn.setAllCaps(false);
        inputBtn.setOnClickListener(v -> inputAction());
        inputRow.addView(input, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        inputRow.addView(inputBtn);
        root.addView(inputRow);

        // ---- content
        web = new WebView(this);
        web.setBackgroundColor(c(k.bg()));
        web.getSettings().setJavaScriptEnabled(false);
        web.getSettings().setTextZoom(88);
        web.getSettings().setSupportZoom(true);              // pinch to zoom (e.g. into the chart)
        web.getSettings().setBuiltInZoomControls(true);
        web.getSettings().setDisplayZoomControls(false);
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                handleLink(url);
                return true;
            }
        });
        root.addView(web, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        // ---- bottom navigation
        View line2 = new View(this);
        line2.setBackgroundColor(c(k.line()));
        root.addView(line2, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1));
        LinearLayout nav = new LinearLayout(this);
        nav.setBackgroundColor(c(k.panel()));
        nav.setPadding(dp(4), dp(4), dp(4), dp(4));
        for (String n : NAV) {
            TextView t = text(n, 13, k.muted(), true);
            t.setGravity(Gravity.CENTER);
            t.setPadding(0, dp(10), 0, dp(10));
            t.setOnClickListener(v -> {
                if (n.equals("More")) moreMenu(v);
                else show(n.equals("Ideas") ? "ideas" : n.equals("Markets") ? "markets" : n.equals("Look up") ? "lookup" : "all");
            });
            navItems.add(t);
            nav.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        }
        root.addView(nav);
        setContentView(root);
    }

    private TextView text(String s, float sp, String color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(c(color));
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private TextView pillButton(String s) {
        TextView t = text(s, 14, k.text(), true);
        t.setPadding(dp(14), dp(7), dp(14), dp(7));
        t.setBackground(rounded(k.panel(), k.line(), 18));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(dp(3), 0, dp(3), 0);
        t.setLayoutParams(lp);
        return t;
    }

    private GradientDrawable rounded(String fill, String stroke, int radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(c(fill));
        g.setStroke(1, c(stroke));
        g.setCornerRadius(dp(radiusDp));
        return g;
    }

    // ================================================================= pages
    private void show(String p) {
        page = p;
        if (!p.equals("lookup")) lookedUp = null;
        filter = "";
        if (input != null) input.setText("");
        render();
    }

    private void render() {
        if (web == null) return;
        Analyzer a = Droid.analyzer;
        Advisor.Plan plan = Droid.plan;
        boolean demo = Droid.demo();
        subtitle.setText(demo ? "PRACTICE MODE - made-up data" : "Your friendly daily stock helper");
        subtitle.setTextColor(c(demo ? k.warn() : k.muted()));
        statusText.setText(Droid.status);
        progress.setVisibility(Droid.loading ? View.VISIBLE : View.GONE);

        String navKey = page.equals("ideas") ? "Ideas" : page.equals("markets") ? "Markets" : page.equals("lookup") ? "Look up"
                : page.equals("all") ? "All stocks" : "More";
        for (TextView t : navItems) {
            boolean on = t.getText().toString().equals(navKey);
            t.setTextColor(c(on ? k.accent() : k.muted()));
            t.setBackground(on ? rounded(k.mix(k.panel(), k.accent(), 0.18), k.panel(), 12) : null);
        }
        chipScroll.setVisibility(page.equals("ideas") ? View.VISIBLE : View.GONE);
        String market = Settings.get("ideas.market", "all");
        marketChip.setText((market.equals("all") ? "All markets" : stockbuddy.WorldStocks.marketName(market).replaceAll(" \\(.*\\)$", "")) + " \u25BE");
        if (plan != null && a != null && !market.equals("all")) plan = stockbuddy.Advisor.build(a, market);
        for (int i = 0; i < chips.size(); i++) {
            boolean on = i == ideaTab;
            String count = plan == null || i == 4 ? "" : "  " + lists(plan).get(i).size();
            chips.get(i).setText(IDEA_TABS[i] + count);
            chips.get(i).setTextColor(c(on ? (k.dark() ? k.bg() : "#ffffff") : k.text()));
            chips.get(i).setBackground(on ? rounded(k.accent(), k.accent(), 18) : rounded(k.panel(), k.line(), 18));
        }
        setupInput();

        String html;
        if (plan == null || a == null) {
            html = HtmlView.message(k, "Getting today's data...", "StockBuddy is reading stockanalysis.com, WallStreetZen, Reddit and price charts. "
                    + "This can take a minute. Tip: turn on Practice mode in the More menu to learn with made-up data.");
        } else switch (page) {
            case "markets": html = HtmlView.markets(k, a); break;
            case "lookup":
                html = lookedUp != null ? HtmlView.stockWithChart(k, lookedUp)
                        : HtmlView.message(k, "Look up a stock", "Type a ticker above (like AAPL or MSFT) to get a chart and a plain-English explanation. "
                        + "You can also tap any ticker on the \"All stocks\" page.");
                break;
            case "all": html = HtmlView.ranking(k, a); break;
            case "dictionary": html = HtmlView.glossary(k, filter); break;
            case "watchlist": html = HtmlView.watchlist(k, Analyzer.readWatchlist()); break;
            case "sources": html = HtmlView.sources(k, a); break;
            case "calendar": html = HtmlView.calendar(k, a); break;
            case "worldstocks": html = HtmlView.worldStocks(k, a); break;
            case "track": html = HtmlView.trackRecord(k, a); break;
            case "alerts": html = HtmlView.alerts(k, a); break;
            case "paper": html = HtmlView.portfolio(k, a); break;
            default:
                html = HtmlView.ideasTab(k, plan, ideaTab, a.demo);
        }
        web.setBackgroundColor(c(k.bg()));
        web.loadDataWithBaseURL("https://stockbuddy.local/", html, "text/html", "utf-8", null);
    }

    private static List<List<Advisor.Idea>> lists(Advisor.Plan p) {
        return HtmlView.ideaLists(p);
    }

    private void setupInput() {
        if (filterWatcher != null) input.removeTextChangedListener(filterWatcher);
        filterWatcher = null;
        boolean visible = true;
        switch (page) {
            case "lookup":
                input.setHint("Type a ticker, e.g. AAPL");
                input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
                inputBtn.setText("Explain");
                inputBtn.setVisibility(View.VISIBLE);
                break;
            case "watchlist":
                input.setHint("Add tickers, e.g. AAPL MSFT");
                input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
                inputBtn.setText("Add");
                inputBtn.setVisibility(View.VISIBLE);
                break;
            case "alerts":
                input.setHint("e.g. NVDA above 150 / AAPL move 5%");
                input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
                inputBtn.setText("Add alert");
                inputBtn.setVisibility(View.VISIBLE);
                break;
            case "paper":
                input.setHint("e.g. buy 10 AAPL / sell all AAPL");
                input.setInputType(InputType.TYPE_CLASS_TEXT);
                inputBtn.setText("Do it");
                inputBtn.setVisibility(View.VISIBLE);
                break;
            case "dictionary":
                input.setHint("Search a word");
                input.setInputType(InputType.TYPE_CLASS_TEXT);
                inputBtn.setVisibility(View.GONE);
                filterWatcher = new TextWatcher() {
                    public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
                    public void onTextChanged(CharSequence s, int a, int b, int c) {}
                    public void afterTextChanged(Editable e) {
                        filter = e.toString();
                        web.loadDataWithBaseURL("https://stockbuddy.local/", HtmlView.glossary(k, filter), "text/html", "utf-8", null);
                    }
                };
                input.addTextChangedListener(filterWatcher);
                break;
            default:
                visible = false;
        }
        inputRow.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    private void inputAction() {
        String t = input.getText().toString().trim();
        if (t.isEmpty()) return;
        hideKeyboard();
        if (page.equals("lookup")) lookup(t);
        else if (page.equals("alerts")) {
            String err = stockbuddy.Alerts.add(t);
            if (err != null) { toast(err); return; }
            input.setText("");
            askNotificationPermission();
            Notifier.scheduleAlertJob(this);
            render();
        } else if (page.equals("paper")) {
            paperCommand(t);
            input.setText("");
        }
        else if (page.equals("watchlist")) {
            List<String> w = new ArrayList<>(Analyzer.readWatchlist());
            for (String x : t.toUpperCase().split("[\\s,]+")) {
                x = x.replace("$", "").trim();
                if (!x.isEmpty() && !w.contains(x)) w.add(x);
            }
            saveWatchlist(w);
            input.setText("");
        }
    }

    private void lookup(String t) {
        final String ticker = t.trim().toUpperCase().replace("$", "");
        final Analyzer a = Droid.analyzer;
        if (a == null) return;
        page = "lookup";
        lookupTicker = ticker;
        web.loadDataWithBaseURL("https://stockbuddy.local/", HtmlView.message(k, "Looking up " + ticker + "...", "One moment."), "text/html", "utf-8", null);
        new Thread(() -> {
            Stock s = null;
            try { s = a.lookup(ticker); } catch (Throwable ignored) {}
            final Stock found = s;
            runOnUiThread(() -> {
                lookedUp = found;
                if (found == null) {
                    render();
                    web.loadDataWithBaseURL("https://stockbuddy.local/", HtmlView.message(k, "Couldn't find \"" + ticker + "\"",
                            a.demo ? "Practice mode only knows these: " + String.join(", ", a.stocks.keySet())
                                    : "Check the spelling, or your internet connection. US tickers are 1-5 letters, like AAPL or MSFT."), "text/html", "utf-8", null);
                } else render();
            });
        }).start();
    }

    private void handleLink(String url) {
        if (url == null) return;
        if (url.startsWith("sb://alert/")) {
            page = "alerts";
            render();
            input.setText(url.substring("sb://alert/".length()) + " above ");
            input.setSelection(input.getText().length());
            return;
        }
        if (url.startsWith("sb://alertdel/")) { stockbuddy.Alerts.remove(url.substring("sb://alertdel/".length())); Notifier.scheduleAlertJob(this); render(); return; }
        if (url.equals("sb://alertclear")) { stockbuddy.Alerts.clearTriggered(); render(); return; }
        if (url.startsWith("sb://pbuy/")) {
            final String tk = url.substring("sb://pbuy/".length());
            final EditText qty = new EditText(this);
            qty.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
            qty.setText("10");
            new AlertDialog.Builder(this).setTitle("Paper-buy " + tk).setMessage("How many pretend shares? (Pretend money - nothing real is bought.)")
                    .setView(qty).setPositiveButton("Buy", (d, w) -> paperCommand("buy " + qty.getText().toString().trim() + " " + tk))
                    .setNegativeButton("Cancel", null).show();
            return;
        }
        if (url.startsWith("sb://psell/")) {
            final String tk = url.substring("sb://psell/".length());
            new AlertDialog.Builder(this).setTitle("Sell all " + tk + "?").setMessage("Sell all your pretend shares of " + tk + "?")
                    .setPositiveButton("Sell", (d, w) -> paperCommand("sell all " + tk)).setNegativeButton("Cancel", null).show();
            return;
        }
        if (url.equals("sb://preset")) {
            new AlertDialog.Builder(this).setTitle("Start again?").setMessage("Start again with $10,000 and delete all pretend trades?")
                    .setPositiveButton("Start again", (d, w) -> { stockbuddy.PaperPortfolio.reset(); render(); }).setNegativeButton("Cancel", null).show();
            return;
        }
        if (url.startsWith("sb://wtop/")) {
            stockbuddy.WorldStocks.setTop(Integer.parseInt(url.substring("sb://wtop/".length())));
            render();
            return;
        }
        if (url.startsWith("sb://page/")) {
            show(url.substring("sb://page/".length()));
            return;
        }
        if (url.startsWith("sb://chart/")) {
            stockbuddy.ChartOptions.applyLink(url);
            render();
            return;
        }
        if (url.startsWith("sb://watch/")) {
            String t = url.substring("sb://watch/".length());
            List<String> w = new ArrayList<>(Analyzer.readWatchlist());
            if (w.contains(t)) toast(t + " is already in your watchlist.");
            else { w.add(t); saveWatchlist(w); toast("Added " + t + " to your watchlist."); }
            return;
        }
        if (url.startsWith("sb://stock/")) {
            String t = url.substring("sb://stock/".length());
            page = "lookup";
            input.setText(t);
            lookup(t);
        } else if (url.startsWith("sb://remove/")) {
            List<String> w = new ArrayList<>(Analyzer.readWatchlist());
            w.remove(url.substring("sb://remove/".length()));
            saveWatchlist(w);
        } else if (url.startsWith("sb://reset")) {
            saveWatchlist(new ArrayList<>(Analyzer.DEFAULT_WATCHLIST));
        } else if (url.startsWith("http")) {
            try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); } catch (Exception ignored) {}
        }
    }

    private void saveWatchlist(List<String> w) {
        try {
            Analyzer.saveWatchlist(w);
            render();
        } catch (Exception e) {
            toast("Couldn't save: " + e.getMessage());
        }
    }

    // ================================================================= menus
    private void moreMenu(View anchor) {
        final String[][] items = {
                {"Price alerts", "alerts"},
                {"Paper trading (pretend money)", "paper"},
                {"Save report (web page, Word, CSV or JSON)...", "report"},
                {"Economic calendar (big news this week)", "calendar"},
                {"World stocks (and where to buy them)", "worldstocks"},
                {"Track record (how past ideas did)", "track"},
                {"Dictionary (what do the words mean?)", "dictionary"},
                {"My watchlist", "watchlist"},
                {"Data sources (which websites worked)", "sources"},
                {"Practice mode (made-up data): " + (Droid.demo() ? "ON - tap to turn off" : "OFF - tap to turn on"), "practice"},
                {"App skin...", "skin"},
                {"Home-screen widget skin...", "wskin"},
                {"Widget see-through...", "opacity"},
                {"Add Reddit posts (paste titles)...", "redditadd"},
                {"Import Reddit CSV...", "redditcsv"},
                {"Clear my Reddit posts", "redditclear"},
                {"Add any CSV as a data source...", "anycsv"},
                {"Remove my CSV files", "nocsv"},
                {"Import Stock Rover CSV...", "rover"},
                {"Import WallStreetZen CSV...", "zen"},
                {"Finnhub key (" + (stockbuddy.FinnhubSource.enabled() ? "set" : "not set") + ")...", "finnhub"},
                {"Alpha Vantage key (" + (stockbuddy.AlphaVantage.enabled() ? "set" : "not set") + ")...", "alpha"},
                {"SEC email (optional)...", "sec"},
                {"World stocks: " + (stockbuddy.WorldStocks.top() == 0 ? "off" : "top " + stockbuddy.WorldStocks.top() + " of each market") + "...", "worldtoggle"},
                {"How to add the home-screen widget", "widgethelp"},
                {"About & disclaimer", "about"}};
        String[] labels = new String[items.length];
        for (int i = 0; i < items.length; i++) labels[i] = items[i][0];
        new AlertDialog.Builder(this).setTitle("More").setItems(labels, (d, which) -> {
            switch (items[which][1]) {
                case "report": saveReport(); break;
                case "calendar": case "worldstocks": case "track": case "dictionary": case "watchlist": case "sources": case "alerts": case "paper":
                    show(items[which][1]); break;
                case "practice":
                    Settings.set("practiceMode", !Droid.demo());
                    Droid.refresh(this, null);
                    break;
                case "skin": chooseSkin(false); break;
                case "wskin": chooseSkin(true); break;
                case "opacity": chooseOpacity(); break;
                case "redditadd": addRedditPosts(); break;
                case "redditcsv": pickFile(PICK_REDDIT); break;
                case "redditclear":
                    new AlertDialog.Builder(this).setTitle("Clear my Reddit posts")
                            .setMessage("Delete all the Reddit posts you added yourself?")
                            .setPositiveButton("Delete", (dd, w) -> {
                                try { stockbuddy.RedditSource.clearManualPosts(); Droid.refresh(this, null); }
                                catch (Exception e) { toast("Couldn't delete them: " + e.getMessage()); }
                            })
                            .setNegativeButton("Cancel", null).show();
                    break;
                case "anycsv": pickFile(PICK_CSV); break;
                case "nocsv":
                    try { stockbuddy.CustomCsv.removeAll(); toast("Your CSV files were removed."); Droid.refresh(this, null); }
                    catch (Exception e) { toast("Couldn't remove them: " + e.getMessage()); }
                    break;
                case "rover": pickFile(PICK_ROVER); break;
                case "zen": pickFile(PICK_ZEN); break;
                case "finnhub": askFinnhubKey(); break;
                case "alpha": askAlphaVantageKey(); break;
                case "sec": askSecEmail(); break;
                case "worldtoggle": chooseWorldTop(); break;
                case "widgethelp": info("Add the widget to your home screen",
                        "1. Go to your home screen.\n2. Touch and hold an empty spot.\n3. Tap \"Widgets\".\n4. Find StockBuddy and drag it onto your home screen.\n\n"
                                + "Tap the widget to open the app, or tap \u21BB on it to refresh. Change its colours with \"Home-screen widget skin\"."); break;
                case "about": info("About StockBuddy",
                        "StockBuddy combines stockanalysis.com, WallStreetZen, Reddit, the WallStreetBets sentiment API, Finnhub, Alpha Vantage, SEC EDGAR, "
                                + "the economic calendar, your own CSV files and StockCharts-style chart signals, and explains them in plain English.\n\nIMPORTANT: this is a learning and research helper, "
                                + "NOT a financial advisor. It can be wrong, data can be late, and you can lose money. Never invest money you can't afford to lose."); break;
                default: break;
            }
        }).show();
    }

    private void paperCommand(final String cmd) {
        final Analyzer a = Droid.analyzer;
        new Thread(() -> {
            final String msg = stockbuddy.PaperPortfolio.load().run(cmd, a);
            runOnUiThread(() -> {
                toast(msg);
                if (msg.startsWith("OK")) page = "paper";
                render();
            });
        }).start();
    }

    private void askNotificationPermission() {
        if (android.os.Build.VERSION.SDK_INT >= 33 && checkSelfPermission("android.permission.POST_NOTIFICATIONS") != android.content.pm.PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 7);
    }

    private void chooseMarket() {
        final List<String[]> options = new ArrayList<>();
        options.add(new String[]{"all", "All markets"});
        for (stockbuddy.WorldMarkets.Market m : stockbuddy.WorldMarkets.all()) options.add(new String[]{m.symbol, m.name});
        String cur = Settings.get("ideas.market", "all");
        String[] labels = new String[options.size()];
        int sel = 0;
        for (int i = 0; i < labels.length; i++) { labels[i] = options.get(i)[1]; if (options.get(i)[0].equals(cur)) sel = i; }
        new AlertDialog.Builder(this).setTitle("Show ideas for").setSingleChoiceItems(labels, sel, (d, w) -> {
            d.dismiss();
            Settings.set("ideas.market", options.get(w)[0]);
            render();
        }).setNegativeButton("Cancel", null).show();
    }

    private void chooseWorldTop() {
        final int[] values = {0, 3, 5, 10, 20};
        String[] labels = {"Off", "Top 3 of each market", "Top 5", "Top 10", "Top 20 (slower refresh)"};
        int cur = stockbuddy.WorldStocks.top(), sel = 1;
        for (int i = 0; i < values.length; i++) if (values[i] == cur) sel = i;
        new AlertDialog.Builder(this).setTitle("World stocks").setSingleChoiceItems(labels, sel, (d, w) -> {
            d.dismiss();
            stockbuddy.WorldStocks.setTop(values[w]);
            toast("Refreshing...");
            Droid.refresh(this, null);
        }).setNegativeButton("Cancel", null).show();
    }

    private void saveReport() {
        if (Droid.plan == null) { toast("Please wait until the data has loaded."); return; }
        final stockbuddy.ReportWriter.Format[] formats = stockbuddy.ReportWriter.Format.values();
        String[] labels = new String[formats.length];
        for (int i = 0; i < formats.length; i++) labels[i] = formats[i].label;
        new AlertDialog.Builder(this).setTitle("Save today's report as").setItems(labels, (d, w) -> {
            pendingFormat = formats[w];
            Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType(pendingFormat.mime);
            i.putExtra(Intent.EXTRA_TITLE, stockbuddy.ReportWriter.fileName(pendingFormat, shownPlan()));
            try { startActivityForResult(i, SAVE_REPORT); } catch (Exception e) { toast("No file saver available on this phone."); }
        }).show();
    }

    private stockbuddy.Advisor.Plan shownPlan() {
        String m = Settings.get("ideas.market", "all");
        return m.equals("all") || Droid.analyzer == null ? Droid.plan : stockbuddy.Advisor.build(Droid.analyzer, m);
    }

    private void askAlphaVantageKey() {
        final EditText f = new EditText(this);
        f.setSingleLine(true);
        f.setText(stockbuddy.AlphaVantage.key());
        f.setHint("Your Alpha Vantage key");
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(8), dp(20), 0);
        TextView help = new TextView(this);
        help.setText("Alpha Vantage adds news sentiment (is the news about a company positive or negative?). Get a free key at "
                + "alphavantage.co/support/#api-key. The free plan allows about 25 requests a day, so StockBuddy checks your first "
                + stockbuddy.AlphaVantage.DEFAULT_PER_DAY + " stocks and remembers the answers all day. Leave empty to turn it off.");
        box.addView(help);
        box.addView(f);
        new AlertDialog.Builder(this).setTitle("Alpha Vantage key").setView(box)
                .setPositiveButton("Save", (d, w) -> {
                    final String key = f.getText().toString().trim();
                    stockbuddy.AlphaVantage.setKey(key);
                    if (key.isEmpty()) { toast("Alpha Vantage is turned off."); return; }
                    new Thread(() -> {
                        final String err = stockbuddy.AlphaVantage.test(key);
                        runOnUiThread(() -> { if (err == null) toast("Key works and is saved. Tap refresh to use it."); else info("Alpha Vantage", err); });
                    }).start();
                })
                .setNegativeButton("Cancel", null).show();
    }

    private void askSecEmail() {
        final EditText f = new EditText(this);
        f.setSingleLine(true);
        f.setText(stockbuddy.SecEdgar.email());
        f.setHint("you@example.com (optional)");
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(8), dp(20), 0);
        TextView help = new TextView(this);
        help.setText("The SEC (sec.gov) asks every program to say who it is. StockBuddy works without this, but if the SEC data doesn't load, "
                + "enter your email. It is only ever sent to sec.gov.");
        box.addView(help);
        box.addView(f);
        new AlertDialog.Builder(this).setTitle("SEC EDGAR").setView(box)
                .setPositiveButton("Save", (d, w) -> { stockbuddy.SecEdgar.setEmail(f.getText().toString()); toast("Saved."); })
                .setNegativeButton("Cancel", null).show();
    }

    /** Free Finnhub key: analyst recommendations + financial ratios. Saved on this phone only. */
    private void askFinnhubKey() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(8), dp(20), 0);
        TextView help = new TextView(this);
        help.setText("Finnhub adds Wall Street analyst recommendations and financial ratios. Get a free key at finnhub.io/register "
                + "and paste it below. It is saved only on this phone. Leave it empty to turn Finnhub off.");
        box.addView(help);
        final EditText f = new EditText(this);
        f.setSingleLine(true);
        f.setText(stockbuddy.FinnhubSource.key());
        f.setHint("Your Finnhub API key");
        box.addView(f);
        new AlertDialog.Builder(this).setTitle("Finnhub key").setView(box)
                .setPositiveButton("Save", (d, w) -> {
                    final String key = f.getText().toString().trim();
                    if (key.isEmpty()) { stockbuddy.FinnhubSource.setKey(""); toast("Finnhub is turned off."); return; }
                    toast("Checking the key...");
                    new Thread(() -> {
                        final String err = stockbuddy.FinnhubSource.test(key);
                        runOnUiThread(() -> {
                            stockbuddy.FinnhubSource.setKey(key);
                            if (err == null) { toast("Key works and is saved. Refreshing (about 2 minutes the first time)..."); Droid.refresh(this, null); }
                            else info("Finnhub key saved, but...", err);
                        });
                    }).start();
                })
                .setNegativeButton("Cancel", null).show();
    }

    /** Paste Reddit post titles yourself - works even when Reddit blocks automatic reading. */
    private void addRedditPosts() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(8), dp(20), 0);
        TextView help = new TextView(this);
        help.setText("Open Reddit, copy the titles of posts you find interesting, and paste them below - one post per line. "
                + "Tickers like $NVDA or NVDA are recognised automatically. Your posts are used for "
                + stockbuddy.RedditSource.MANUAL_DAYS + " days.");
        box.addView(help);
        final EditText sub = new EditText(this);
        sub.setSingleLine(true);
        sub.setHint("Subreddit, e.g. wallstreetbets");
        sub.setText("wallstreetbets");
        box.addView(sub);
        final EditText posts = new EditText(this);
        posts.setHint("Paste post titles here, one per line");
        posts.setMinLines(5);
        posts.setGravity(Gravity.TOP);
        posts.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        box.addView(posts);
        new AlertDialog.Builder(this).setTitle("Add Reddit posts").setView(box)
                .setPositiveButton("Add", (d, w) -> {
                    try {
                        int n = stockbuddy.RedditSource.addManualPosts(sub.getText().toString(), posts.getText().toString());
                        if (n == 0) { toast("Nothing to add - paste at least one post title."); return; }
                        toast("Added " + n + " post" + (n == 1 ? "" : "s") + ". Refreshing...");
                        Droid.refresh(this, null);
                    } catch (Exception e) {
                        toast("Couldn't save the posts: " + e.getMessage());
                    }
                })
                .setNegativeButton("Cancel", null).show();
    }

    private void chooseSkin(boolean widget) {
        List<String> names = new ArrayList<>();
        if (widget) names.add("Same as app");
        for (Palette p : Palette.ALL) names.add(p.name());
        String current = widget ? Settings.get("widget.skin", "Same as app") : k.name();
        new AlertDialog.Builder(this)
                .setTitle(widget ? "Home-screen widget skin" : "App skin")
                .setSingleChoiceItems(names.toArray(new String[0]), Math.max(0, names.indexOf(current)), (d, which) -> {
                    d.dismiss();
                    if (widget) {
                        Settings.set("widget.skin", names.get(which));
                        StockWidget.updateAll(this);
                        toast("Widget skin changed");
                    } else {
                        Settings.set("skin", names.get(which));
                        build();
                        render();
                        StockWidget.updateAll(this);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void chooseOpacity() {
        final int[] values = {100, 92, 80, 65, 50};
        String[] labels = {"Solid", "A little see-through", "See-through", "Very see-through", "Mostly transparent"};
        int cur = Settings.getInt("widget.opacity", 92), idx = 1;
        for (int i = 0; i < values.length; i++) if (values[i] == cur) idx = i;
        new AlertDialog.Builder(this).setTitle("Widget see-through")
                .setSingleChoiceItems(labels, idx, (d, which) -> {
                    d.dismiss();
                    Settings.set("widget.opacity", values[which]);
                    StockWidget.updateAll(this);
                }).setNegativeButton("Cancel", null).show();
    }

    private void info(String title, String msg) {
        new AlertDialog.Builder(this).setTitle(title).setMessage(msg).setPositiveButton("OK", null).show();
    }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_LONG).show(); }

    private void hideKeyboard() {
        InputMethodManager im = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (im != null) im.hideSoftInputFromWindow(input.getWindowToken(), 0);
    }

    // ================================================================= CSV import
    private void pickFile(int code) {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        try { startActivityForResult(i, code); } catch (Exception e) { toast("No file picker available on this phone."); }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (res != RESULT_OK || data == null || data.getData() == null) return;
        if (req == PICK_REDDIT) { importRedditCsv(data.getData()); return; }
        if (req == PICK_CSV) { importAnyCsv(data.getData()); return; }
        if (req == SAVE_REPORT && pendingFormat != null) {
            final Uri uri = data.getData();
            final stockbuddy.ReportWriter.Format f = pendingFormat;
            final stockbuddy.Advisor.Plan plan = shownPlan();
            new Thread(() -> {
                String msg;
                try (java.io.OutputStream out = getContentResolver().openOutputStream(uri)) {
                    out.write(stockbuddy.ReportWriter.build(f, Droid.analyzer, plan));
                    msg = "Report saved.";
                } catch (Exception e) { msg = "Couldn't save the report: " + e.getMessage(); }
                final String m = msg;
                runOnUiThread(() -> toast(m));
            }).start();
            return;
        }
        String target = req == PICK_ROVER ? "stockrover.csv" : "wallstreetzen.csv";
        try (InputStream in = getContentResolver().openInputStream(data.getData())) {
            Path dest = AppPaths.data(target);
            Files.createDirectories(dest.getParent());
            Files.copy(in, dest, StandardCopyOption.REPLACE_EXISTING);
            int rows = Csv.readByTicker(dest).size();
            StockRoverSource.reload();
            WallStreetZenSource.reload();
            toast("Imported " + rows + " stocks. Refreshing...");
            Droid.refresh(this, null);
        } catch (Exception e) {
            toast("Couldn't import the file: " + e.getMessage());
        }
    }

    private void importAnyCsv(Uri uri) {
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            Path tmp = AppPaths.data("incoming.csv");
            Files.createDirectories(tmp.getParent());
            Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
            String name = uri.getLastPathSegment() == null ? "my-data" : uri.getLastPathSegment().replaceAll(".*[/:]", "");
            int n = stockbuddy.CustomCsv.add(tmp, name);
            Files.deleteIfExists(tmp);
            if (n == 0) { info("Can't use this file", "The first line needs a column called Ticker or Symbol."); return; }
            toast("Added " + n + " stocks from your file. Refreshing...");
            Droid.refresh(this, null);
        } catch (Exception e) {
            toast("Couldn't add the file: " + e.getMessage());
        }
    }

    private void importRedditCsv(Uri uri) {
        Path dest = stockbuddy.RedditSource.manualFile();
        Path backup = dest.resolveSibling("reddit.backup.csv");
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            Files.createDirectories(dest.getParent());
            if (Files.exists(dest)) Files.copy(dest, backup, StandardCopyOption.REPLACE_EXISTING);
            Files.copy(in, dest, StandardCopyOption.REPLACE_EXISTING);
            int n = stockbuddy.RedditSource.manualPosts().size();
            if (n == 0) {
                if (Files.exists(backup)) Files.copy(backup, dest, StandardCopyOption.REPLACE_EXISTING); else Files.deleteIfExists(dest);
                info("No usable posts found", "The file needs a first line with a column called 'title' (optional: date, subreddit, upvotes). "
                        + "Dates older than " + stockbuddy.RedditSource.MANUAL_DAYS + " days are skipped.");
                return;
            }
            toast("Imported " + n + " Reddit posts. Refreshing...");
            Droid.refresh(this, null);
        } catch (Exception e) {
            toast("Couldn't import the file: " + e.getMessage());
        }
    }

    // ================================================================= crash safety
    private void installCrashReporter() {
        final Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        if (previous instanceof Reporter) return;
        Thread.setDefaultUncaughtExceptionHandler(new Reporter(previous));
    }

    private final class Reporter implements Thread.UncaughtExceptionHandler {
        private final Thread.UncaughtExceptionHandler next;
        Reporter(Thread.UncaughtExceptionHandler next) { this.next = next; }
        @Override public void uncaughtException(Thread t, Throwable e) {
            try {
                StringWriter sw = new StringWriter();
                e.printStackTrace(new PrintWriter(sw));
                Files.write(AppPaths.data("crash.txt"), sw.toString().getBytes(StandardCharsets.UTF_8));
            } catch (Throwable ignored) {}
            if (next != null) next.uncaughtException(t, e);
        }
    }

    private void showPreviousCrash() {
        Path p = AppPaths.data("crash.txt");
        if (!Files.exists(p)) return;
        try {
            String txt = new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
            Files.delete(p);
            new AlertDialog.Builder(this).setTitle("Sorry - StockBuddy closed unexpectedly last time")
                    .setMessage("Details (useful if you report the problem):\n\n" + (txt.length() > 1500 ? txt.substring(0, 1500) + "..." : txt))
                    .setPositiveButton("OK", null).show();
        } catch (Exception ignored) {}
    }
}
