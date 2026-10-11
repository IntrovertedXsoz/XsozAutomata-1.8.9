package dev.xsoz.pvp.ai;

/**
 * How good the hands and the head are. Every level plays by a player's rules (it only knows what
 * a player can see, clicks what's under the crosshair, moves the mouse in whole mouse counts); a
 * better level reacts faster, aims cleaner, clicks steadier and makes fewer mistakes, the way
 * players get better.
 */
public enum Skill {
    CASUAL("Casual", 330, 0.10, 0.10, 1.6, 0.030,
            "Slow to react, flicks land well off and need a second try, loses its rhythm."),
    DECENT("Decent", 260, 0.075, 0.085, 1.2, 0.015,
            "Reacts in about a quarter second, mostly clean flicks, w-taps now and then."),
    GOOD("Good", 210, 0.055, 0.072, 0.9, 0.007,
            "Quick, tidy aim with small corrections, keeps combos going, block-hits."),
    GREAT("Great", 175, 0.04, 0.062, 0.6, 0.003,
            "Fast and steady, rarely misses a flick by much, plays knockback well."),
    PRO("Pro", 145, 0.03, 0.055, 0.4, 0.001,
            "Tournament-level hands. Still human: still overshoots a little, still has a reaction time.");

    public final String title;
    /** Mean reaction time to something new, in ms (humans: ~150-350). */
    public final int reactionMs;
    /** Endpoint spread of a flick, as a share of how far it moves (signal-dependent noise). */
    public final double flickSpread;
    /** Fitts' law slope: seconds per bit of difficulty. Smaller = faster flicks. */
    public final double fittsB;
    /** Hand tremor and drift, in degrees. */
    public final double tremor;
    /** Chance per decision to fumble (a mistimed tap, a wrong key, a look a bit off). */
    public final double fumble;
    public final String detail;

    Skill(String title, int reactionMs, double flickSpread, double fittsB, double tremor, double fumble, String detail) {
        this.title = title;
        this.reactionMs = reactionMs;
        this.flickSpread = flickSpread;
        this.fittsB = fittsB;
        this.tremor = tremor;
        this.fumble = fumble;
        this.detail = detail;
    }

    public int reactionTicks() { return Math.max(2, Math.round(reactionMs / 50f)); }

    public boolean atLeast(Skill s) { return ordinal() >= s.ordinal(); }
}
