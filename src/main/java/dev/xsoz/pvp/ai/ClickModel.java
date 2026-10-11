package dev.xsoz.pvp.ai;

import java.util.Random;

/**
 * A finger on a mouse button, in seconds. No Minecraft types, so it can be tested alone.
 *
 * <p>What real clicking looks like, and what this copies:
 * <ul>
 *   <li><b>Never a metronome.</b> The gap between clicks varies 15-25% around its average
 *   (a skewed spread: a few long gaps, no impossibly short ones).</li>
 *   <li><b>The speed wanders.</b> Over a few seconds the rate drifts up and down by a click or two
 *   a second - excitement, tiredness, focus.</li>
 *   <li><b>Styles differ.</b> Normal clicking (one finger, ~7-10 a second), jitter clicking (a
 *   tensed arm, ~10-13, steadier) and butterfly (two fingers taking turns, ~12-16, coming in
 *   close pairs).</li>
 *   <li><b>Beats get missed.</b> Now and then a longer gap - a lapse, a re-grip.</li>
 *   <li><b>A first click takes a moment</b> after deciding to start.</li>
 * </ul>
 */
public final class ClickModel {
    public enum Style {
        NORMAL("Normal", 8.6, 1.3, "One finger: about 7 to 10 clicks a second, a loose rhythm."),
        JITTER("Jitter", 11.3, 1.2, "A tensed arm: about 10 to 13 a second, steadier."),
        BUTTERFLY("Butterfly", 13.6, 1.6, "Two fingers taking turns: about 12 to 16 a second, in close pairs.");

        public final String title;
        /** Average clicks per second, and how far the rate drifts (clicks a second). */
        public final double cps, drift;
        public final String detail;

        Style(String title, double cps, double drift, String detail) {
            this.title = title;
            this.cps = cps;
            this.drift = drift;
            this.detail = detail;
        }
    }

    /** Nothing human clicks faster than this, and servers flag it - a hard ceiling. */
    public static final double MAX_CPS = 18.5;

    private final Random rng;
    private Style style;
    private Skill skill;
    private double clock;
    private double next = -1;
    private double drift;
    private boolean pairSecond;
    private int clicks;

    public ClickModel(Style style, Skill skill, Random rng) {
        this.style = style;
        this.skill = skill;
        this.rng = rng;
    }

    public void setStyle(Style s) { style = s; }

    public void setSkill(Skill s) { skill = s; }

    public int total() { return clicks; }

    /** Clicks per second right now (the drifting rate). */
    public double rate() {
        return Math.max(4, Math.min(MAX_CPS, style.cps + drift));
    }

    /**
     * Moves time on by dt seconds. want: wants to be clicking. Returns how many clicks fall in
     * this slice of time (a 50 ms tick at 14 a second holds 0 or 1, now and then 2).
     */
    public int step(double dt, boolean want) {
        clock += dt;
        // the rate wanders slowly (an Ornstein-Uhlenbeck drift around the style's average)
        drift += -drift * dt / 3.0 + style.drift * Math.sqrt(2 * dt / 3.0) * rng.nextGaussian();
        if (!want) {
            next = -1;
            pairSecond = false;
            return 0;
        }
        if (next < 0) {
            // deciding to start: the first press comes a beat later
            next = clock + 0.03 + rng.nextDouble() * 0.06;
        }
        int n = 0;
        while (next <= clock && n < 3) {
            n++;
            clicks++;
            next += gap();
        }
        return n;
    }

    /** Seconds to the next click. */
    private double gap() {
        double mean = 1.0 / rate();
        // looser hands are less even
        double cv = 0.16 + (4 - skill.ordinal()) * 0.025;
        double g;
        if (style == Style.BUTTERFLY) {
            // pairs: a short gap inside a pair, a longer one between pairs (same average)
            pairSecond = !pairSecond;
            g = pairSecond ? mean * (0.55 + 0.12 * rng.nextGaussian()) : mean * (1.45 + 0.2 * rng.nextGaussian());
        } else {
            // log-normal: skewed to the long side, never near zero
            double sigma = Math.sqrt(Math.log(1 + cv * cv));
            g = mean * Math.exp(sigma * rng.nextGaussian() - sigma * sigma / 2);
        }
        // a lapse now and then (a missed beat, a re-grip)
        if (rng.nextDouble() < 0.012 + (4 - skill.ordinal()) * 0.006) g += 0.08 + rng.nextDouble() * 0.15;
        return Math.max(1.0 / 25, g);
    }
}
