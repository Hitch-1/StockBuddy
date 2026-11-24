package stockbuddy;

/** Simple logger. Run with --debug to see why a website was skipped. */
public final class Log {
    public static boolean DEBUG = false;
    private Log() {}
    public static void debug(String m) { if (DEBUG) System.out.println(Ui.DIM + "  [debug] " + m + Ui.RESET); }
}
