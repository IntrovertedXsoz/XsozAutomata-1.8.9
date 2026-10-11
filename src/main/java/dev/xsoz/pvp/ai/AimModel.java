package dev.xsoz.pvp.ai;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.Random;

/**
 * A human hand on a mouse, in degrees and seconds. No Minecraft types, so it can be tested alone.
 *
 * <p>What people actually do when they aim, and what this copies:
 * <ul>
 *   <li><b>The eyes are behind.</b> The hand acts on what the eyes saw ~110-200 ms ago (the
 *   visuomotor delay). Players partly make up for it by guessing ahead - better players more.
 *   So when someone strafing changes direction, the aim keeps going the old way for a moment,
 *   goes past, and comes back. That overshoot on every direction change is the clearest sign of
 *   a human tracking.</li>
 *   <li><b>Corrections come in small bursts</b> (sub-movements, about every 0.1-0.15 s), not as
 *   one perfectly smooth glide.</li>
 *   <li><b>Following speed wobbles</b>: the hand runs a little fast, then a little slow (motor
 *   noise grows with speed).</li>
 *   <li><b>Flicks</b> for big moves: a bell-shaped speed curve, timed by Fitts' law (farther and
 *   smaller takes longer), landing short or long by an amount that grows with the size of the
 *   move, on a slightly curved path - then a correction. A flick rides on top of following the
 *   target, it doesn't stop it.</li>
 *   <li><b>A pause after each flick</b> (40-90 ms) while the eyes judge where it landed.</li>
 *   <li><b>Tremor</b> (8-12 Hz) and slow <b>drift</b>: never perfectly still, never perfectly centred.</li>
 *   <li><b>Reaction time</b> before the first move toward something new.</li>
 * </ul>
 */
public final class AimModel {
    private static final class Sample {
        final double t, yaw, pitch;

        Sample(double t, double yaw, double pitch) {
            this.t = t;
            this.yaw = yaw;
            this.pitch = pitch;
        }

        double t() { return t; }

        double yaw() { return yaw; }

        double pitch() { return pitch; }
    }

    private final Random rng;
    private Skill skill;

    private double clock;
    private boolean hasTarget;
    private double width = 3;
    private boolean precise;
    private boolean calm;
    /** What the eyes have seen, oldest first (the hand works off a delayed copy). */
    private final Deque<Sample> seen = new ArrayDeque<>();
    // the flick in progress
    private boolean flicking;
    private double fStart, fDur, fYaw1, fPitch1, fDoneYaw, fDonePitch;
    private double fArc, fPerpY, fPerpP;
    private double waitUntil;
    private double dwellUntil;
    // sub-movement corrections
    private double nextCorrection;
    private double corrYaw, corrPitch;
    // speed wobble, drift, tremor
    private double gainWobble;
    private double driftYaw, driftPitch;
    private final double ph1, ph2, ph3, ph4;
    private double lastTremorYaw, lastTremorPitch;
    private int flicks;

    public AimModel(Skill skill, Random rng) {
        this.skill = skill;
        this.rng = rng;
        ph1 = rng.nextDouble() * Math.PI * 2;
        ph2 = rng.nextDouble() * Math.PI * 2;
        ph3 = rng.nextDouble() * Math.PI * 2;
        ph4 = rng.nextDouble() * Math.PI * 2;
    }

    public void setSkill(Skill s) { skill = s; }

    public int flicks() { return flicks; }

    public boolean flicking() { return flicking; }

    // ------------------------------------------------------------------ per skill

    /** Visuomotor delay, seconds. */
    private double lag() { return new double[] {0.17, 0.15, 0.135, 0.125, 0.115}[skill.ordinal()]; }

    /** How much of that delay they guess ahead to make up (0 = none, 1 = all). */
    private double anticipation() { return new double[] {0.45, 0.55, 0.63, 0.70, 0.76}[skill.ordinal()]; }

    /** Size of the wobble in following speed (share of the speed). */
    private double speedNoise() { return new double[] {0.22, 0.18, 0.15, 0.12, 0.10}[skill.ordinal()]; }

    /** Seconds between correction bursts. */
    private double correctionGap() { return new double[] {0.15, 0.13, 0.115, 0.10, 0.09}[skill.ordinal()]; }

    // ------------------------------------------------------------------ input

    /**
     * What to look at, updated every game tick. width is how big the target looks, in degrees.
     * fresh: a new thing to look at - the hand reacts to it first.
     */
    public void target(double yaw, double pitch, double widthDeg, boolean fresh, boolean precise) {
        target(yaw, pitch, widthDeg, fresh, precise, false);
    }

    /** calm: just looking somewhere (around, along the way) - one smooth, deliberate turn, no flick. */
    public void target(double yaw, double pitch, double widthDeg, boolean fresh, boolean precise, boolean calm) {
        this.calm = calm;
        if (!hasTarget || fresh) {
            // reaction: about half a reaction time (the brain already noticed it), with spread
            double react = skill.reactionMs / 1000.0 * (0.4 + 0.25 * Math.abs(rng.nextGaussian()));
            // lining up something we chose (a block to place on or break): no surprise to react to,
            // just a beat to start the move
            if (precise) react = 0.02 + 0.03 * rng.nextDouble();
            if (calm) react = 0.03 + 0.06 * rng.nextDouble();
            waitUntil = Math.max(waitUntil, clock + react);
            seen.clear();
        }
        // the yaw the caller gives is unwrapped near the current view; keep the history continuous
        Sample last = seen.peekLast();
        if (last != null) yaw = last.yaw() + wrap(yaw - last.yaw());
        seen.addLast(new Sample(clock, yaw, pitch));
        while (seen.size() > 2 && clock - seen.peekFirst().t() > 0.6) seen.pollFirst();
        hasTarget = true;
        width = Math.max(0.6, widthDeg);
        this.precise = precise;
    }

    /** Hand off the mouse. */
    public void clear() {
        hasTarget = false;
        flicking = false;
        seen.clear();
    }

    /**
     * What the hand works from: where the target was lag seconds ago, how fast it was moving then,
     * and a partial guess at where it is now. {yaw, pitch, velYaw, velPitch}
     */
    private double[] perceived() {
        double lag = lag();
        double t = clock - lag;
        Sample a = null, b = null;
        for (Iterator<Sample> it = seen.iterator(); it.hasNext(); ) {
            Sample s = it.next();
            if (s.t() <= t) a = s;
            else {
                b = s;
                break;
            }
        }
        if (a == null) a = seen.peekFirst();
        if (b == null) b = a;
        double f = b.t() > a.t() ? (t - a.t()) / (b.t() - a.t()) : 0;
        f = Math.max(0, Math.min(1, f));
        double y = a.yaw() + (b.yaw() - a.yaw()) * f;
        double p = a.pitch() + (b.pitch() - a.pitch()) * f;
        // motion as seen around then
        Sample before = a;
        for (Sample s : seen) {
            if (s.t() <= a.t() - 0.1) before = s;
        }
        double dt = a.t() - before.t();
        double vy = dt > 0.01 ? (a.yaw() - before.yaw()) / dt : 0;
        double vp = dt > 0.01 ? (a.pitch() - before.pitch()) / dt : 0;
        if (b != a && b.t() - a.t() > 0.01) {
            vy = 0.5 * vy + 0.5 * (b.yaw() - a.yaw()) / (b.t() - a.t());
            vp = 0.5 * vp + 0.5 * (b.pitch() - a.pitch()) / (b.t() - a.t());
        }
        double guess = lag * anticipation();
        return new double[] {y + vy * guess, p + vp * guess, vy, vp};
    }

    // ------------------------------------------------------------------ the hand

    /**
     * Moves time on by dt seconds from the current view (yaw, pitch); returns how far to move the
     * mouse, {dYaw, dPitch} in degrees.
     */
    public double[] step(double yaw, double pitch, double dt) {
        dt = Math.max(0, Math.min(dt, 0.1));
        clock += dt;
        if (!hasTarget || seen.isEmpty()) {
            lastTremorYaw = 0;
            lastTremorPitch = 0;
            return new double[] {0, 0};
        }
        double dy = 0, dp = 0;
        double sigma = skill.tremor * (precise ? 0.35 : 0.9);
        driftYaw += -driftYaw * dt / 0.7 + sigma * Math.sqrt(dt) * rng.nextGaussian();
        driftPitch += -driftPitch * dt / 0.7 + sigma * 0.6 * Math.sqrt(dt) * rng.nextGaussian();
        double sn = speedNoise();
        gainWobble += -gainWobble * dt / 0.25 + sn * Math.sqrt(2 * dt / 0.25) * rng.nextGaussian();

        double[] seenNow = perceived();
        double ey = wrap(seenNow[0] + driftYaw - yaw);
        double ep = seenNow[1] + driftPitch * 0.6 - pitch;
        double follow = (0.86 + 0.025 * skill.ordinal()) * (1 + gainWobble);
        double vy = seenNow[2] * follow;
        double vp = seenNow[3] * follow;

        if (flicking) {
            double tau = Math.min(1, (clock - fStart) / fDur);
            double s = minJerk(tau);
            double bow = fArc * Math.sin(Math.PI * s);
            double ny = fYaw1 * s + fPerpY * bow;
            double np = fPitch1 * s + fPerpP * bow;
            dy = ny - fDoneYaw + vy * dt;
            dp = np - fDonePitch + vp * dt;
            fDoneYaw = ny;
            fDonePitch = np;
            if (tau >= 1) {
                flicking = false;
                dwellUntil = clock + 0.04 + rng.nextDouble() * 0.05;
            }
        } else if (clock < waitUntil) {
            // still reacting
        } else if (clock < dwellUntil) {
            // judging where the flick landed; the eyes keep following meanwhile
            dy = vy * dt;
            dp = vp * dt;
        } else {
            double err = Math.hypot(ey, ep);
            double threshold = calm ? Math.max(4, width * 0.6) : precise ? Math.max(0.9, width * 0.4) : Math.max(1.8, width * 0.5) + 0.8;
            if (err > threshold) {
                startFlick(ey, ep);
            } else {
                // following, with a correction burst every so often that closes most of the gap
                if (clock >= nextCorrection) {
                    double gap = correctionGap() * (0.8 + 0.4 * rng.nextDouble());
                    double close = 0.72 + 0.1 * rng.nextGaussian();
                    corrYaw = ey * close / gap;
                    corrPitch = ep * close / gap;
                    nextCorrection = clock + gap;
                }
                dy = (vy + corrYaw) * dt;
                dp = (vp + corrPitch) * dt;
            }
        }
        // physiological tremor, 8-12 Hz
        double a = skill.tremor * (precise ? 0.08 : 0.16);
        double ty = a * (0.6 * Math.sin(2 * Math.PI * 9.1 * clock + ph1) + 0.4 * Math.sin(2 * Math.PI * 11.3 * clock + ph2));
        double tp = a * 0.7 * (0.6 * Math.sin(2 * Math.PI * 8.4 * clock + ph3) + 0.4 * Math.sin(2 * Math.PI * 10.6 * clock + ph4));
        dy += ty - lastTremorYaw;
        dp += tp - lastTremorPitch;
        lastTremorYaw = ty;
        lastTremorPitch = tp;
        return new double[] {dy, dp};
    }

    private void startFlick(double ey, double ep) {
        double dist = Math.hypot(ey, ep);
        // Fitts: T = a + b * log2(1 + D / W)
        double a = 0.045 + (4 - skill.ordinal()) * 0.008;
        double dur = a + skill.fittsB * log2(1 + dist / width);
        if (precise) dur *= 1.05;
        dur *= 0.9 + rng.nextDouble() * 0.25;
        // where it lands: most flicks fall a little short, some go past; the spread grows with distance
        // (toned down: big misses read as a bot as much as none at all do)
        double spread = skill.flickSpread * (precise ? 0.6 : 0.7);
        double along = Math.max(0.78, Math.min(1.14, 0.97 + rng.nextGaussian() * spread));
        double across = rng.nextGaussian() * spread * 0.45;
        if (calm) {
            // turning to look somewhere: a smooth turn at an easy speed that ends where it meant to
            dur = 0.16 + dist / (100 + 50 * rng.nextDouble());
            // a big turn swings a little past (or stops a little short) and settles - more so the
            // bigger and faster it is; a small one lands about right
            double swing = Math.min(0.035, 0.006 + dist / 5000);
            along = Math.max(0.96, Math.min(1.04, 1.006 + rng.nextGaussian() * swing));
            across = rng.nextGaussian() * swing * 0.5;
        } else if (precise) {
            // lining up a block: the hand goes most of the way and creeps in the rest - it lands
            // short far more often than past, and only a hair past when it does
            along = Math.max(0.9, Math.min(1.004, 0.96 + rng.nextGaussian() * spread * 0.2));
            across = rng.nextGaussian() * spread * 0.1;
        }
        double ux = ey / dist, uy = ep / dist;
        fYaw1 = ey * along - uy * across * dist;
        fPitch1 = ep * along + ux * across * dist;
        // the path bows a little to one side
        fArc = rng.nextGaussian() * (calm ? 0.03 : 0.045) * dist;
        fPerpY = -uy;
        fPerpP = ux;
        fStart = clock;
        fDur = Math.max(0.05, dur);
        fDoneYaw = 0;
        fDonePitch = 0;
        flicking = true;
        flicks++;
        nextCorrection = 0;
    }

    /** Minimum-jerk position profile: smooth start, fast middle, smooth stop. */
    static double minJerk(double t) {
        return t * t * t * (10 - 15 * t + 6 * t * t);
    }

    static double log2(double x) { return Math.log(x) / Math.log(2); }

    public static double wrap(double deg) {
        deg %= 360;
        if (deg >= 180) deg -= 360;
        if (deg < -180) deg += 360;
        return deg;
    }
}
