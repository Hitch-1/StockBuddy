package stockbuddy;

/**
 * One opinion about a stock from one source.
 * score: -1 = strong "sell" signal, 0 = neutral, +1 = strong "buy" signal.
 * reason: a plain-English sentence explaining why.
 */
public final class Signal {
    private final String source, reason;
    private final double score;

    public Signal(String source, double score, String reason) {
        this.source = source;
        this.score = Math.max(-1, Math.min(1, score));
        this.reason = reason;
    }

    public String source() { return source; }
    public double score() { return score; }
    public String reason() { return reason; }
}
