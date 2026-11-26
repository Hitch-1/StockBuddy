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
import stockbuddy.I18n;
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
    /** The pages in the menu: id, icon, name. Their order can be changed by dragging (saved in settings). */
    private static final String[][] PAGES = {
            {"ideas", "\uD83D\uDCA1", "Today's ideas"}, {"markets", "\uD83C\uDF0D", "World markets"}, {"worldstocks", "\uD83C\uDFE6", "World stocks"},
            {"calendar", "\uD83D\uDCC5", "Economic calendar"}, {"news", "\uD83D\uDCF0", "Market news"}, {"lookup", "\uD83D\uDD0D", "Look up a stock"}, {"all", "\uD83D\uDCCB", "All stocks"},
            {"alerts", "\uD83D\uDD14", "Price alerts"}, {"paper", "\uD83D\uDCBC", "Paper trading"}, {"track", "\uD83D\uDCC8", "Track record"},
            {"dictionary", "\uD83D\uDCD6", "Dictionary"}, {"sources", "\uD83D\uDD0C", "Data sources"}};

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
    // menu (drawer), header buttons and swiping
    private LinearLayout drawer, pageRows;
    private android.widget.ScrollView drawerScroll;
    private View dim;
    private final List<LinearLayout> slots = new ArrayList<>();
    private List<String> menuOrder;
    private int dragIndex = -1;
    private TextView pageTitle;
    private LinearLayout starBtn;
    private float baseScale = -1, scaleNow = -1;
    private LinearLayout inputRow;
    private EditText input;
    private Button inputBtn;
    private TextWatcher filterWatcher;
    private final Handler ticker = new Handler(Looper.getMainLooper());
    /** While the app is open: check price alerts every 5 minutes and show a notification when one fires. */
    private final Runnable alertTick = new Runnable() {
        @Override public void run() {
            final Analyzer a = Droid.analyzer;
            if (stockbuddy.Alerts.anyWaiting() && !Droid.demo()) {
                new Thread(() -> {
                    java.util.List<String> fired = stockbuddy.Alerts.check(stockbuddy.Alerts.prices(a, true));
                    if (!fired.isEmpty()) {
                        Notifier.alerts(MainActivity.this, fired);
                        runOnUiThread(() -> { if (page.equals("alerts")) render(); });
                    }
                }).start();
            }
            ticker.postDelayed(this, 5 * 60_000);
        }
    };
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
        if (!Settings.getBool("hint.menu", false)) {
            Settings.set("hint.menu", true);
            Toast.makeText(this, I18n.tr("Tip: tap \u2630 for all pages, swipe left or right to move between them, \u2605 for your watchlist."), Toast.LENGTH_LONG).show();
        }
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
        ticker.removeCallbacks(alertTick);
        ticker.postDelayed(alertTick, 5 * 60_000);
    }

    @Override
    protected void onPause() {
        ticker.removeCallbacks(marketTick);
        ticker.removeCallbacks(alertTick);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (Droid.listener == this) Droid.listener = null;
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (drawer != null && drawer.getVisibility() == View.VISIBLE) closeDrawer();
        else if (!page.equals("ideas")) show("ideas");
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
        slots.clear();
        getWindow().setStatusBarColor(c(k.panel()));
        getWindow().setNavigationBarColor(c(k.panel()));
        getWindow().getDecorView().setSystemUiVisibility(k.dark() ? 0 : View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        getWindow().getDecorView().setLayoutDirection(I18n.rtl() ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(c(k.bg()));

        // ---- header: menu (left), My watchlist (middle), refresh + three dots (right)
        android.widget.FrameLayout header = new android.widget.FrameLayout(this);
        header.setBackgroundColor(c(k.panel()));
        header.setPadding(dp(4), dp(4), dp(4), 0);
        TextView burger = iconButton("\u2630", 24);
        burger.setContentDescription("Menu");
        burger.setOnClickListener(v -> openDrawer());
        header.addView(burger, new android.widget.FrameLayout.LayoutParams(dp(52), dp(52), Gravity.START | Gravity.CENTER_VERTICAL));
        starBtn = new LinearLayout(this);
        starBtn.setOrientation(LinearLayout.VERTICAL);
        starBtn.setGravity(Gravity.CENTER);
        starBtn.setPadding(dp(14), dp(2), dp(14), dp(2));
        starBtn.setContentDescription("My watchlist");
        TextView star = text("\u2605", 22, k.warn(), true);
        star.setGravity(Gravity.CENTER);
        TextView starLabel = text("Watchlist", 10, k.muted(), false);
        starLabel.setGravity(Gravity.CENTER);
        starBtn.addView(star);
        starBtn.addView(starLabel);
        starBtn.setOnClickListener(v -> show("watchlist"));
        header.addView(starBtn, new android.widget.FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(52), Gravity.CENTER));
        LinearLayout right = new LinearLayout(this);
        right.setGravity(Gravity.CENTER_VERTICAL);
        TextView refreshBtn = iconButton("\u21BB", 22);
        refreshBtn.setContentDescription("Refresh data");
        refreshBtn.setOnClickListener(v -> Droid.refresh(this, null));
        TextView dots = iconButton("\u22EE", 24);
        dots.setContentDescription("More options");
        dots.setOnClickListener(this::dotsMenu);
        right.addView(refreshBtn, new LinearLayout.LayoutParams(dp(46), dp(52)));
        right.addView(dots, new LinearLayout.LayoutParams(dp(42), dp(52)));
        header.addView(right, new android.widget.FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(52), Gravity.END | Gravity.CENTER_VERTICAL));
        root.addView(header);

        // ---- title row: logo + page name + practice-mode note
        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        titleRow.setPadding(dp(14), 0, dp(14), dp(4));
        titleRow.setBackgroundColor(c(k.panel()));
        android.widget.ImageView logo = new android.widget.ImageView(this);
        logo.setImageResource(com.stockbuddy.app.R.drawable.icon);
        titleRow.addView(logo, new LinearLayout.LayoutParams(dp(26), dp(26)));
        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.setPadding(dp(10), 0, 0, 0);
        pageTitle = text("StockBuddy", 17, k.text(), true);
        subtitle = text("", 11, k.muted(), false);
        titles.addView(pageTitle);
        titles.addView(subtitle);
        titleRow.addView(titles, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        root.addView(titleRow);

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
        marketChip = pillButton(I18n.tr("All markets \u25BE"));
        marketChip.setOnClickListener(v -> chooseMarket());
        chipRow.addView(marketChip);
        for (int i = 0; i < IDEA_TABS.length; i++) {
            final int idx = i;
            TextView t = pillButton(I18n.tr(IDEA_TABS[i]));
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
        final android.view.GestureDetector swipes = new android.view.GestureDetector(this, new android.view.GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onFling(android.view.MotionEvent e1, android.view.MotionEvent e2, float vx, float vy) {
                if (e1 == null || e2 == null) return false;
                float dx = e2.getX() - e1.getX(), dy = e2.getY() - e1.getY();
                boolean zoomedIn = baseScale > 0 && scaleNow > baseScale * 1.05f;   // pinch-zoomed: let the page pan instead
                if (!zoomedIn && Math.abs(dx) > dp(90) && Math.abs(dx) > 2 * Math.abs(dy) && Math.abs(vx) > 600) {
                    swipe(dx < 0 ? 1 : -1);
                    return true;
                }
                return false;
            }
        });
        web.setOnTouchListener((v, ev) -> { swipes.onTouchEvent(ev); return false; });
        web.setWebViewClient(new WebViewClient() {
            @Override
            public void onScaleChanged(WebView view, float oldScale, float newScale) {
                if (baseScale < 0) baseScale = oldScale;
                scaleNow = newScale;
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                handleLink(url);
                return true;
            }
        });
        root.addView(web, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        // ---- the menu (slides in from the left over the page)
        android.widget.FrameLayout frame = new android.widget.FrameLayout(this);
        frame.addView(root, new android.widget.FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        dim = new View(this);
        dim.setBackgroundColor(0x99000000);
        dim.setVisibility(View.GONE);
        dim.setOnClickListener(v -> closeDrawer());
        frame.addView(dim, new android.widget.FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        int w = Math.min(dp(330), (int) (getResources().getDisplayMetrics().widthPixels * 0.86));
        drawer = new LinearLayout(this);
        drawer.setOrientation(LinearLayout.VERTICAL);
        drawer.setBackgroundColor(c(k.panel()));
        drawer.setElevation(dp(16));
        drawer.setClickable(true);
        drawer.setTranslationX(I18n.rtl() ? w : -w);   // Arabic: the menu slides in from the right
        drawer.setVisibility(View.INVISIBLE);
        buildDrawer();
        frame.addView(drawer, new android.widget.FrameLayout.LayoutParams(w, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.START));
        setContentView(frame);
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
        subtitle.setText(demo ? I18n.tr("PRACTICE MODE - made-up data") : I18n.tr("Your friendly daily stock helper"));
        subtitle.setTextColor(c(demo ? k.warn() : k.muted()));
        statusText.setText(Droid.status);
        progress.setVisibility(Droid.loading ? View.VISIBLE : View.GONE);

        pageTitle.setText(pageName(page));
        starBtn.setBackground(page.equals("watchlist") ? rounded(k.mix(k.panel(), k.accent(), 0.22), k.panel(), 14) : null);
        bindSlots();
        chipScroll.setVisibility(page.equals("ideas") ? View.VISIBLE : View.GONE);
        String market = Settings.get("ideas.market", "all");
        marketChip.setText((market.equals("all") ? I18n.tr("All markets") : stockbuddy.WorldStocks.marketName(market).replaceAll(" \\(.*\\)$", "")) + I18n.tr(" \u25BE"));
        if (plan != null && a != null && !market.equals("all")) plan = stockbuddy.Advisor.build(a, market);
        for (int i = 0; i < chips.size(); i++) {
            boolean on = i == ideaTab;
            String count = plan == null || i == 4 ? "" : "  " + lists(plan).get(i).size();
            chips.get(i).setText(I18n.tr(IDEA_TABS[i]) + count);
            chips.get(i).setTextColor(c(on ? (k.dark() ? k.bg() : I18n.tr("#ffffff")) : k.text()));
            chips.get(i).setBackground(on ? rounded(k.accent(), k.accent(), 18) : rounded(k.panel(), k.line(), 18));
        }
        setupInput();

        String html;
        if (plan == null || a == null) {
            html = HtmlView.message(k, I18n.tr("Getting today's data..."), I18n.tr("StockBuddy is reading stockanalysis.com, WallStreetZen, Reddit and price charts. ")
                    + I18n.tr("This can take a minute. Tip: turn on Practice mode in the More menu to learn with made-up data."));
        } else switch (page) {
            case "markets": html = HtmlView.markets(k, a); break;
            case "lookup":
                html = lookedUp != null ? HtmlView.stockWithChart(k, lookedUp)
                        : HtmlView.message(k, I18n.tr("Look up a stock"), I18n.tr("Type a ticker above (like AAPL or MSFT) to get a chart and a plain-English explanation. ")
                        + I18n.tr("You can also tap any ticker on the \"All stocks\" page."));
                break;
            case "all": html = HtmlView.ranking(k, a); break;
            case "dictionary": html = HtmlView.glossary(k, filter); break;
            case "watchlist": html = HtmlView.watchlist(k, Analyzer.readWatchlist(), a); break;
            case "news": html = HtmlView.news(k, a); break;
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
                input.setHint(I18n.tr("Type a ticker, e.g. AAPL"));
                input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
                inputBtn.setText(I18n.tr("Explain"));
                inputBtn.setVisibility(View.VISIBLE);
                break;
            case "watchlist":
                input.setHint(I18n.tr("Add tickers, e.g. AAPL MSFT"));
                input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
                inputBtn.setText(I18n.tr("Add"));
                inputBtn.setVisibility(View.VISIBLE);
                break;
            case "alerts":
                input.setHint(I18n.tr("e.g. NVDA above 150 / AAPL move 5%"));
                input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
                inputBtn.setText(I18n.tr("Add alert"));
                inputBtn.setVisibility(View.VISIBLE);
                break;
            case "paper":
                input.setHint(I18n.tr("e.g. buy 10 AAPL / sell all AAPL"));
                input.setInputType(InputType.TYPE_CLASS_TEXT);
                inputBtn.setText(I18n.tr("Do it"));
                inputBtn.setVisibility(View.VISIBLE);
                break;
            case "dictionary":
                input.setHint(I18n.tr("Search a word"));
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
        web.loadDataWithBaseURL("https://stockbuddy.local/", HtmlView.message(k, I18n.tr("Looking up ") + ticker + "...", I18n.tr("One moment.")), "text/html", "utf-8", null);
        new Thread(() -> {
            Stock s = null;
            try { s = a.lookup(ticker); } catch (Throwable ignored) {}
            final Stock found = s;
            runOnUiThread(() -> {
                lookedUp = found;
                if (found == null) {
                    render();
                    web.loadDataWithBaseURL("https://stockbuddy.local/", HtmlView.message(k, I18n.tr("Couldn't find \"") + ticker + "\"",
                            a.demo ? I18n.tr("Practice mode only knows these: ") + String.join(", ", a.stocks.keySet())
                                    : I18n.tr("Check the spelling, or your internet connection. US tickers are 1-5 letters, like AAPL or MSFT.")), "text/html", "utf-8", null);
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
            new AlertDialog.Builder(this).setTitle(I18n.tr("Paper-buy ") + tk).setMessage(I18n.tr("How many pretend shares? (Pretend money - nothing real is bought.)"))
                    .setView(qty).setPositiveButton(I18n.tr("Buy"), (d, w) -> paperCommand("buy " + qty.getText().toString().trim() + " " + tk))
                    .setNegativeButton(I18n.tr("Cancel"), null).show();
            return;
        }
        if (url.startsWith("sb://psell/")) {
            final String tk = url.substring("sb://psell/".length());
            new AlertDialog.Builder(this).setTitle(I18n.tr("Sell all ") + tk + "?").setMessage(I18n.tr("Sell all your pretend shares of ") + tk + "?")
                    .setPositiveButton(I18n.tr("Sell"), (d, w) -> paperCommand("sell all " + tk)).setNegativeButton(I18n.tr("Cancel"), null).show();
            return;
        }
        if (url.equals("sb://preset")) {
            new AlertDialog.Builder(this).setTitle(I18n.tr("Start again?")).setMessage(I18n.tr("Start again with $10,000 and delete all pretend trades?"))
                    .setPositiveButton(I18n.tr("Start again"), (d, w) -> { stockbuddy.PaperPortfolio.reset(); render(); }).setNegativeButton(I18n.tr("Cancel"), null).show();
            return;
        }
        if (url.startsWith("sb://trackdays/")) {
            Settings.set("track.days", Integer.parseInt(url.substring("sb://trackdays/".length())));
            render();
            return;
        }
        if (url.startsWith("sb://market/") || url.startsWith("sb://allmarket/")) {
            Settings.set("allstocks.market", url.substring(url.lastIndexOf('/') + 1));
            show("all");
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
            if (w.contains(t)) toast(t + I18n.tr(" is already in your watchlist."));
            else { w.add(t); saveWatchlist(w); toast(I18n.tr("Added ") + t + I18n.tr(" to your watchlist.")); }
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
            toast(I18n.tr("Couldn't save: ") + e.getMessage());
        }
    }

    // ================================================================= menus
    /** Settings & data actions shown at the bottom of the menu: {label, action}. */
    private String[][] settingsItems() {
        return new String[][]{
                {I18n.tr("\uD83C\uDF93 Practice mode (made-up data): ") + (Droid.demo() ? "ON" : "OFF"), "practice"},
                {I18n.tr("\uD83C\uDF10 World stocks: ") + (stockbuddy.WorldStocks.top() == 0 ? "off" : I18n.tr("top ") + stockbuddy.WorldStocks.top() + I18n.tr(" of each market")), "worldtoggle"},
                {I18n.tr("\u270D Add Reddit posts (paste titles)"), "redditadd"},
                {I18n.tr("\uD83D\uDCE5 Import Reddit CSV"), "redditcsv"},
                {I18n.tr("\uD83D\uDDD1 Clear my Reddit posts"), "redditclear"},
                {I18n.tr("\uD83D\uDCC4 Add any CSV as a data source"), "anycsv"},
                {I18n.tr("\uD83D\uDDD1 Remove my CSV files"), "nocsv"},
                {I18n.tr("\uD83D\uDCE5 Import Stock Rover CSV"), "rover"},
                {I18n.tr("\uD83D\uDCE5 Import WallStreetZen CSV"), "zen"},
                {I18n.tr("\uD83D\uDD11 Finnhub key (") + (stockbuddy.FinnhubSource.enabled() ? "set" : I18n.tr("not set")) + ")", "finnhub"},
                {I18n.tr("\uD83D\uDD11 Alpha Vantage key (") + (stockbuddy.AlphaVantage.enabled() ? "set" : I18n.tr("not set")) + ")", "alpha"},
                {I18n.tr("\u2709 SEC email (optional)"), "sec"},
                {I18n.tr("\u2195 Reset the menu order"), "resetorder"},
                {I18n.tr("\u2753 How to add the home-screen widget"), "widgethelp"},
                {I18n.tr("\u2139 About & disclaimer"), "about"}};
    }

    private void runAction(String action) {
        switch (action) {
            case "report": saveReport(); break;
            case "practice":
                Settings.set("practiceMode", !Droid.demo());
                Droid.refresh(this, null);
                break;
            case "skin": chooseSkin(false); break;
            case "wskin": chooseSkin(true); break;
            case "opacity": chooseOpacity(); break;
            case "language": chooseLanguage(); break;
            case "redditadd": addRedditPosts(); break;
            case "redditcsv": pickFile(PICK_REDDIT); break;
            case "redditclear":
                new AlertDialog.Builder(this).setTitle(I18n.tr("Clear my Reddit posts"))
                        .setMessage(I18n.tr("Delete all the Reddit posts you added yourself?"))
                        .setPositiveButton(I18n.tr("Delete"), (dd, w) -> {
                            try { stockbuddy.RedditSource.clearManualPosts(); Droid.refresh(this, null); }
                            catch (Exception e) { toast(I18n.tr("Couldn't delete them: ") + e.getMessage()); }
                        })
                        .setNegativeButton(I18n.tr("Cancel"), null).show();
                break;
            case "anycsv": pickFile(PICK_CSV); break;
            case "nocsv":
                try { stockbuddy.CustomCsv.removeAll(); toast(I18n.tr("Your CSV files were removed.")); Droid.refresh(this, null); }
                catch (Exception e) { toast(I18n.tr("Couldn't remove them: ") + e.getMessage()); }
                break;
            case "rover": pickFile(PICK_ROVER); break;
            case "zen": pickFile(PICK_ZEN); break;
            case "finnhub": askFinnhubKey(); break;
            case "alpha": askAlphaVantageKey(); break;
            case "sec": askSecEmail(); break;
            case "worldtoggle": chooseWorldTop(); break;
            case "resetorder":
                Settings.set("menu.order", "");
                menuOrder = null;
                bindSlots();
                toast(I18n.tr("Menu order reset."));
                break;
            case "widgethelp": info(I18n.tr("Add the widget to your home screen"),
                    I18n.tr("1. Go to your home screen.\n2. Touch and hold an empty spot.\n3. Tap \"Widgets\".\n4. Find StockBuddy and drag it onto your home screen.\n\n")
                            + I18n.tr("Tap the widget to open the app, or tap \u21BB on it to refresh. Change its colours in \u22EE > Home-screen widget skin.")); break;
            case "about": info(I18n.tr("About StockBuddy"),
                    I18n.tr("StockBuddy combines stockanalysis.com, WallStreetZen, Reddit, the WallStreetBets sentiment API, Finnhub, Alpha Vantage, SEC EDGAR, ")
                            + I18n.tr("the economic calendar, your own CSV files and StockCharts-style chart signals, and explains them in plain English.\n\nIMPORTANT: this is a learning and research helper, ")
                            + I18n.tr("NOT a financial advisor. It can be wrong, data can be late, and you can lose money. Never invest money you can't afford to lose.")); break;
            default: break;
        }
    }

    // ---------------------------------------------------------------- menu order + swiping
    private List<String> order() {
        if (menuOrder != null) return menuOrder;
        List<String> o = new ArrayList<>();
        for (String id : Settings.get("menu.order", "").split(",")) if (pageIndex(id) >= 0 && !o.contains(id)) o.add(id);
        for (String[] p : PAGES) if (!o.contains(p[0])) o.add(p[0]);
        menuOrder = o;
        return o;
    }

    private static int pageIndex(String id) {
        for (int i = 0; i < PAGES.length; i++) if (PAGES[i][0].equals(id)) return i;
        return -1;
    }

    private static String pageName(String id) {
        if (id.equals("watchlist")) return I18n.tr("\u2605 My watchlist");
        int i = pageIndex(id);
        return i < 0 ? "StockBuddy" : PAGES[i][1] + " " + I18n.tr(PAGES[i][2]);
    }

    /** Swipe left = next page, swipe right = previous page (in your menu order). */
    private void swipe(final int dir) {
        List<String> o = order();
        int idx = o.indexOf(page);
        if (idx < 0) idx = dir > 0 ? -1 : o.size();
        final int next = idx + dir;
        if (next < 0 || next >= o.size()) return;
        final String target = o.get(next);
        final float width = web.getWidth();
        web.animate().translationX(-dir * width * 0.35f).alpha(0f).setDuration(110).withEndAction(() -> {
            show(target);
            web.setTranslationX(dir * width * 0.35f);
            web.animate().translationX(0).alpha(1f).setDuration(170).start();
        }).start();
        if (!Settings.getBool("hint.swipe", false)) Settings.set("hint.swipe", true);
    }

    // ---------------------------------------------------------------- the menu (drawer)
    private void buildDrawer() {
        drawer.removeAllViews();
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.VERTICAL);
        head.setPadding(dp(18), dp(18), dp(18), dp(10));
        head.addView(text("StockBuddy", 20, k.text(), true));
        TextView hint = text("Hold \u2261 and drag to change the order. Swipe a page left or right to move between pages.", 12, k.muted(), false);
        hint.setPadding(0, dp(4), 0, 0);
        head.addView(hint);
        drawer.addView(head);
        View line = new View(this);
        line.setBackgroundColor(c(k.line()));
        drawer.addView(line, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1));

        drawerScroll = new android.widget.ScrollView(this);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(0, dp(6), 0, dp(20));
        list.addView(sectionLabel("PAGES"));
        pageRows = new LinearLayout(this);
        pageRows.setOrientation(LinearLayout.VERTICAL);
        slots.clear();
        for (int i = 0; i < PAGES.length; i++) {
            final int slot = i;
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(18), 0, dp(4), 0);
            TextView label = text("", 16, k.text(), false);
            label.setPadding(0, dp(13), 0, dp(13));
            label.setOnClickListener(v -> { show(order().get(slot)); closeDrawer(); });
            TextView handle = text("\u2261", 24, k.muted(), true);
            handle.setGravity(Gravity.CENTER);
            handle.setContentDescription("Drag to move");
            handle.setOnTouchListener((v, ev) -> dragRow(slot, ev));
            row.addView(label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            row.addView(handle, new LinearLayout.LayoutParams(dp(52), dp(48)));
            slots.add(row);
            pageRows.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        list.addView(pageRows);
        list.addView(sectionLabel(I18n.tr("SETTINGS & DATA")));
        for (final String[] item : settingsItems()) {
            TextView row = text(item[0], 14, k.text(), false);
            row.setPadding(dp(18), dp(11), dp(18), dp(11));
            row.setOnClickListener(v -> { closeDrawer(); runAction(item[1]); });
            list.addView(row);
        }
        drawerScroll.addView(list);
        drawer.addView(drawerScroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        bindSlots();
    }

    private TextView sectionLabel(String s) {
        TextView t = text(s, 11, k.muted(), true);
        t.setPadding(dp(18), dp(12), dp(18), dp(4));
        return t;
    }

    /** Shows the pages in your order and highlights the page you're on (and the one being dragged). */
    private void bindSlots() {
        List<String> o = order();
        for (int i = 0; i < slots.size() && i < o.size(); i++) {
            LinearLayout row = slots.get(i);
            String id = o.get(i);
            TextView label = (TextView) row.getChildAt(0);
            label.setText(pageName(id));
            boolean current = id.equals(page), dragging = i == dragIndex;
            label.setTextColor(c(current ? k.accent() : k.text()));
            label.setTypeface(current ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
            row.setBackground(dragging ? rounded(k.mix(k.panel(), k.accent(), 0.30), k.accent(), 10)
                    : current ? rounded(k.mix(k.panel(), k.accent(), 0.14), k.panel(), 10) : null);
            row.setElevation(dragging ? dp(6) : 0);
        }
    }

    /** Drag the \u2261 handle up or down to move a page in the menu. */
    private boolean dragRow(int slot, android.view.MotionEvent ev) {
        switch (ev.getActionMasked()) {
            case android.view.MotionEvent.ACTION_DOWN:
                dragIndex = slot;
                drawerScroll.requestDisallowInterceptTouchEvent(true);
                bindSlots();
                return true;
            case android.view.MotionEvent.ACTION_MOVE: {
                if (dragIndex < 0) return true;
                int[] loc = new int[2];
                pageRows.getLocationOnScreen(loc);
                float y = ev.getRawY() - loc[1];
                int target = dragIndex;
                for (int i = 0; i < slots.size(); i++) {
                    View row = slots.get(i);
                    if (y >= row.getTop() && y < row.getBottom()) target = i;
                }
                if (y < 0) target = 0;
                if (y >= slots.get(slots.size() - 1).getBottom()) target = slots.size() - 1;
                if (target != dragIndex) {
                    List<String> o = order();
                    String moved = o.remove(dragIndex);
                    o.add(target, moved);
                    dragIndex = target;
                    bindSlots();
                }
                int[] sv = new int[2];
                drawerScroll.getLocationOnScreen(sv);
                if (ev.getRawY() < sv[1] + dp(40)) drawerScroll.scrollBy(0, -dp(12));          // scroll while dragging near the edges
                else if (ev.getRawY() > sv[1] + drawerScroll.getHeight() - dp(40)) drawerScroll.scrollBy(0, dp(12));
                return true;
            }
            case android.view.MotionEvent.ACTION_UP:
            case android.view.MotionEvent.ACTION_CANCEL:
                if (dragIndex >= 0) {
                    dragIndex = -1;
                    Settings.set("menu.order", String.join(",", order()));
                    bindSlots();
                }
                drawerScroll.requestDisallowInterceptTouchEvent(false);
                return true;
            default:
                return false;
        }
    }

    private void openDrawer() {
        hideKeyboard();
        drawer.setVisibility(View.VISIBLE);
        dim.setAlpha(0f);
        dim.setVisibility(View.VISIBLE);
        dim.animate().alpha(1f).setDuration(200).start();
        drawer.animate().translationX(0).setDuration(220).start();
        bindSlots();
    }

    private void closeDrawer() {
        if (drawer.getVisibility() != View.VISIBLE) return;
        dim.animate().alpha(0f).setDuration(180).withEndAction(() -> dim.setVisibility(View.GONE)).start();
        drawer.animate().translationX(I18n.rtl() ? drawer.getWidth() : -drawer.getWidth()).setDuration(200).withEndAction(() -> drawer.setVisibility(View.INVISIBLE)).start();
    }

    // ---------------------------------------------------------------- three dots (top right)
    private void dotsMenu(View anchor) {
        String[][] items = {
                {I18n.tr("\uD83D\uDCBE  Save report (web page, Word, CSV or JSON)"), "report"},
                {I18n.tr("\uD83C\uDFA8  App skin"), "skin"},
                {I18n.tr("\uD83D\uDDBC  Home-screen widget skin"), "wskin"},
                {I18n.tr("\uD83D\uDC53  Widget see-through"), "opacity"},
                {"\uD83C\uDF10  " + I18n.tr("Language") + ": " + I18n.name(I18n.lang()), "language"}};
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(0, dp(6), 0, dp(6));
        box.setBackground(rounded(k.panel(), k.line(), 12));
        final android.widget.PopupWindow pop = new android.widget.PopupWindow(box, dp(300), ViewGroup.LayoutParams.WRAP_CONTENT, true);
        for (final String[] item : items) {
            TextView row = text(item[0], 15, k.text(), false);
            row.setPadding(dp(16), dp(13), dp(16), dp(13));
            row.setOnClickListener(v -> { pop.dismiss(); runAction(item[1]); });
            box.addView(row);
        }
        pop.setElevation(dp(12));
        pop.setOutsideTouchable(true);
        pop.showAsDropDown(anchor, -dp(256), 0);
    }

    private TextView iconButton(String symbol, float sp) {
        TextView t = text(symbol, sp, k.text(), true);
        t.setGravity(Gravity.CENTER);
        return t;
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
        new AlertDialog.Builder(this).setTitle(I18n.tr("Show ideas for")).setSingleChoiceItems(labels, sel, (d, w) -> {
            d.dismiss();
            Settings.set("ideas.market", options.get(w)[0]);
            render();
        }).setNegativeButton(I18n.tr("Cancel"), null).show();
    }

    private void chooseWorldTop() {
        final int[] values = {0, 3, 5, 10, 20};
        String[] labels = {I18n.tr("Off"), I18n.tr("Top 3 of each market"), I18n.tr("Top 5"), I18n.tr("Top 10"), I18n.tr("Top 20 (slower refresh)")};
        int cur = stockbuddy.WorldStocks.top(), sel = 1;
        for (int i = 0; i < values.length; i++) if (values[i] == cur) sel = i;
        new AlertDialog.Builder(this).setTitle(I18n.tr("World stocks")).setSingleChoiceItems(labels, sel, (d, w) -> {
            d.dismiss();
            stockbuddy.WorldStocks.setTop(values[w]);
            toast(I18n.tr("Refreshing..."));
            Droid.refresh(this, null);
        }).setNegativeButton(I18n.tr("Cancel"), null).show();
    }

    private void saveReport() {
        if (Droid.plan == null) { toast(I18n.tr("Please wait until the data has loaded.")); return; }
        final stockbuddy.ReportWriter.Format[] formats = stockbuddy.ReportWriter.Format.values();
        String[] labels = new String[formats.length];
        for (int i = 0; i < formats.length; i++) labels[i] = formats[i].label;
        new AlertDialog.Builder(this).setTitle(I18n.tr("Save today's report as")).setItems(labels, (d, w) -> {
            pendingFormat = formats[w];
            Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType(pendingFormat.mime);
            i.putExtra(Intent.EXTRA_TITLE, stockbuddy.ReportWriter.fileName(pendingFormat, shownPlan()));
            try { startActivityForResult(i, SAVE_REPORT); } catch (Exception e) { toast(I18n.tr("No file saver available on this phone.")); }
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
        f.setHint(I18n.tr("Your Alpha Vantage key"));
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(8), dp(20), 0);
        TextView help = new TextView(this);
        help.setText(I18n.tr("Alpha Vantage adds news sentiment (is the news about a company positive or negative?). Get a free key at ")
                + I18n.tr("alphavantage.co/support/#api-key. The free plan allows about 25 requests a day, so StockBuddy checks your first ")
                + stockbuddy.AlphaVantage.DEFAULT_PER_DAY + I18n.tr(" stocks and remembers the answers all day. Leave empty to turn it off."));
        box.addView(help);
        box.addView(f);
        new AlertDialog.Builder(this).setTitle(I18n.tr("Alpha Vantage key")).setView(box)
                .setPositiveButton(I18n.tr("Save"), (d, w) -> {
                    final String key = f.getText().toString().trim();
                    stockbuddy.AlphaVantage.setKey(key);
                    if (key.isEmpty()) { toast(I18n.tr("Alpha Vantage is turned off.")); return; }
                    new Thread(() -> {
                        final String err = stockbuddy.AlphaVantage.test(key);
                        runOnUiThread(() -> { if (err == null) toast(I18n.tr("Key works and is saved. Tap refresh to use it.")); else info(I18n.tr("Alpha Vantage"), err); });
                    }).start();
                })
                .setNegativeButton(I18n.tr("Cancel"), null).show();
    }

    private void askSecEmail() {
        final EditText f = new EditText(this);
        f.setSingleLine(true);
        f.setText(stockbuddy.SecEdgar.email());
        f.setHint(I18n.tr("you@example.com (optional)"));
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(8), dp(20), 0);
        TextView help = new TextView(this);
        help.setText(I18n.tr("The SEC (sec.gov) asks every program to say who it is. StockBuddy works without this, but if the SEC data doesn't load, ")
                + I18n.tr("enter your email. It is only ever sent to sec.gov."));
        box.addView(help);
        box.addView(f);
        new AlertDialog.Builder(this).setTitle(I18n.tr("SEC EDGAR")).setView(box)
                .setPositiveButton(I18n.tr("Save"), (d, w) -> { stockbuddy.SecEdgar.setEmail(f.getText().toString()); toast(I18n.tr("Saved.")); })
                .setNegativeButton(I18n.tr("Cancel"), null).show();
    }

    /** Free Finnhub key: analyst recommendations + financial ratios. Saved on this phone only. */
    private void askFinnhubKey() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(8), dp(20), 0);
        TextView help = new TextView(this);
        help.setText(I18n.tr("Finnhub adds Wall Street analyst recommendations and financial ratios. Get a free key at finnhub.io/register ")
                + I18n.tr("and paste it below. It is saved only on this phone. Leave it empty to turn Finnhub off."));
        box.addView(help);
        final EditText f = new EditText(this);
        f.setSingleLine(true);
        f.setText(stockbuddy.FinnhubSource.key());
        f.setHint(I18n.tr("Your Finnhub API key"));
        box.addView(f);
        new AlertDialog.Builder(this).setTitle(I18n.tr("Finnhub key")).setView(box)
                .setPositiveButton(I18n.tr("Save"), (d, w) -> {
                    final String key = f.getText().toString().trim();
                    if (key.isEmpty()) { stockbuddy.FinnhubSource.setKey(""); toast(I18n.tr("Finnhub is turned off.")); return; }
                    toast(I18n.tr("Checking the key..."));
                    new Thread(() -> {
                        final String err = stockbuddy.FinnhubSource.test(key);
                        runOnUiThread(() -> {
                            stockbuddy.FinnhubSource.setKey(key);
                            if (err == null) { toast(I18n.tr("Key works and is saved. Refreshing (about 2 minutes the first time)...")); Droid.refresh(this, null); }
                            else info(I18n.tr("Finnhub key saved, but..."), err);
                        });
                    }).start();
                })
                .setNegativeButton(I18n.tr("Cancel"), null).show();
    }

    /** Paste Reddit post titles yourself - works even when Reddit blocks automatic reading. */
    private void addRedditPosts() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(8), dp(20), 0);
        TextView help = new TextView(this);
        help.setText(I18n.tr("Open Reddit, copy the titles of posts you find interesting, and paste them below - one post per line. ")
                + I18n.tr("Tickers like $NVDA or NVDA are recognised automatically. Your posts are used for ")
                + stockbuddy.RedditSource.MANUAL_DAYS + I18n.tr(" days."));
        box.addView(help);
        final EditText sub = new EditText(this);
        sub.setSingleLine(true);
        sub.setHint(I18n.tr("Subreddit, e.g. wallstreetbets"));
        sub.setText("wallstreetbets");
        box.addView(sub);
        final EditText posts = new EditText(this);
        posts.setHint(I18n.tr("Paste post titles here, one per line"));
        posts.setMinLines(5);
        posts.setGravity(Gravity.TOP);
        posts.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        box.addView(posts);
        new AlertDialog.Builder(this).setTitle(I18n.tr("Add Reddit posts")).setView(box)
                .setPositiveButton(I18n.tr("Add"), (d, w) -> {
                    try {
                        int n = stockbuddy.RedditSource.addManualPosts(sub.getText().toString(), posts.getText().toString());
                        if (n == 0) { toast(I18n.tr("Nothing to add - paste at least one post title.")); return; }
                        toast(I18n.tr("Added ") + n + I18n.tr(" post") + (n == 1 ? "" : "s") + I18n.tr(". Refreshing..."));
                        Droid.refresh(this, null);
                    } catch (Exception e) {
                        toast(I18n.tr("Couldn't save the posts: ") + e.getMessage());
                    }
                })
                .setNegativeButton(I18n.tr("Cancel"), null).show();
    }

    private void chooseSkin(boolean widget) {
        List<String> names = new ArrayList<>();
        if (widget) names.add("Same as app");
        for (Palette p : Palette.ALL) names.add(p.name());
        String current = widget ? Settings.get("widget.skin", "Same as app") : k.name();
        new AlertDialog.Builder(this)
                .setTitle(widget ? I18n.tr("Home-screen widget skin") : I18n.tr("App skin"))
                .setSingleChoiceItems(names.toArray(new String[0]), Math.max(0, names.indexOf(current)), (d, which) -> {
                    d.dismiss();
                    if (widget) {
                        Settings.set("widget.skin", names.get(which));
                        StockWidget.updateAll(this);
                        toast(I18n.tr("Widget skin changed"));
                    } else {
                        Settings.set("skin", names.get(which));
                        build();
                        render();
                        StockWidget.updateAll(this);
                    }
                })
                .setNegativeButton(I18n.tr("Cancel"), null)
                .show();
    }

    private void chooseLanguage() {
        String[] names = new String[I18n.LANGS.length];
        int sel = 0;
        for (int i = 0; i < names.length; i++) { names[i] = I18n.LANGS[i][1]; if (I18n.LANGS[i][0].equals(I18n.lang())) sel = i; }
        new AlertDialog.Builder(this).setTitle(I18n.tr("Language")).setSingleChoiceItems(names, sel, (d, w) -> {
            d.dismiss();
            I18n.setLang(I18n.LANGS[w][0]);
            Droid.relocalize();
            build();
            render();
            StockWidget.updateAll(this);
            if (!Droid.demo()) Droid.refresh(this, null);   // also fetch the news in the new language
        }).setNegativeButton(I18n.tr("Cancel"), null).show();
    }

    private void chooseOpacity() {
        final int[] values = {100, 92, 80, 65, 50};
        String[] labels = {I18n.tr("Solid"), I18n.tr("A little see-through"), I18n.tr("See-through"), I18n.tr("Very see-through"), I18n.tr("Mostly transparent")};
        int cur = Settings.getInt("widget.opacity", 92), idx = 1;
        for (int i = 0; i < values.length; i++) if (values[i] == cur) idx = i;
        new AlertDialog.Builder(this).setTitle(I18n.tr("Widget see-through"))
                .setSingleChoiceItems(labels, idx, (d, which) -> {
                    d.dismiss();
                    Settings.set("widget.opacity", values[which]);
                    StockWidget.updateAll(this);
                }).setNegativeButton(I18n.tr("Cancel"), null).show();
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
        try { startActivityForResult(i, code); } catch (Exception e) { toast(I18n.tr("No file picker available on this phone.")); }
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
                    msg = I18n.tr("Report saved.");
                } catch (Exception e) { msg = I18n.tr("Couldn't save the report: ") + e.getMessage(); }
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
            toast(I18n.tr("Imported ") + rows + I18n.tr(" stocks. Refreshing..."));
            Droid.refresh(this, null);
        } catch (Exception e) {
            toast(I18n.tr("Couldn't import the file: ") + e.getMessage());
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
            if (n == 0) { info(I18n.tr("Can't use this file"), I18n.tr("The first line needs a column called Ticker or Symbol.")); return; }
            toast(I18n.tr("Added ") + n + I18n.tr(" stocks from your file. Refreshing..."));
            Droid.refresh(this, null);
        } catch (Exception e) {
            toast(I18n.tr("Couldn't add the file: ") + e.getMessage());
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
                info(I18n.tr("No usable posts found"), I18n.tr("The file needs a first line with a column called 'title' (optional: date, subreddit, upvotes). ")
                        + I18n.tr("Dates older than ") + stockbuddy.RedditSource.MANUAL_DAYS + I18n.tr(" days are skipped."));
                return;
            }
            toast(I18n.tr("Imported ") + n + I18n.tr(" Reddit posts. Refreshing..."));
            Droid.refresh(this, null);
        } catch (Exception e) {
            toast(I18n.tr("Couldn't import the file: ") + e.getMessage());
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
            new AlertDialog.Builder(this).setTitle(I18n.tr("Sorry - StockBuddy closed unexpectedly last time"))
                    .setMessage(I18n.tr("Details (useful if you report the problem):\n\n") + (txt.length() > 1500 ? txt.substring(0, 1500) + "..." : txt))
                    .setPositiveButton("OK", null).show();
        } catch (Exception ignored) {}
    }
}
