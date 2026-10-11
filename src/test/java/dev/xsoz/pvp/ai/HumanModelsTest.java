package dev.xsoz.pvp.ai;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.Assert.assertTrue;

/** The hand and the finger, checked against what people actually do. */
public class HumanModelsTest {

    /** Click times (seconds) over a long spell of wanting to click, ticked at 20 a second. */
    private static List<Double> clicks(ClickModel.Style style, Skill skill, long seed, double seconds) {
        ClickModel m = new ClickModel(style, skill, new Random(seed));
        List<Double> at = new ArrayList<Double>();
        for (int t = 0; t < seconds * 20; t++) {
            int n = m.step(0.05, true);
            for (int k = 0; k < n; k++) at.add(t * 0.05);
        }
        return at;
    }

    @Test
    public void clickRatesSitInEachStylesHumanRange() {
        double[][] range = {{6.5, 10.5}, {9, 13.5}, {11, 16.5}};
        for (ClickModel.Style st : ClickModel.Style.values()) {
            double cps = clicks(st, Skill.GREAT, 7, 60).size() / 60.0;
            double[] r = range[st.ordinal()];
            assertTrue(st + " clicks " + cps + " a second", cps >= r[0] && cps <= r[1]);
        }
    }

    @Test
    public void neverAMetronomeAndNeverOverTheCeiling() {
        for (ClickModel.Style st : ClickModel.Style.values()) {
            List<Double> at = clicks(st, Skill.PRO, 11, 60);
            // any one second: never over the hard ceiling (plus one for where the second falls)
            for (double s = 0; s < 59; s += 0.5) {
                int n = 0;
                for (double a : at) if (a >= s && a < s + 1) n++;
                assertTrue(st + ": " + n + " clicks in one second", n <= ClickModel.MAX_CPS + 2);
            }
            // per-tick counts vary (no fixed pattern): at least two different counts show up often
            int[] perTick = new int[1200];
            for (double a : at) perTick[(int) Math.round(a / 0.05)]++;
            int ones = 0, zeros = 0;
            for (int v : perTick) {
                if (v == 0) zeros++;
                if (v == 1) ones++;
            }
            assertTrue(st + ": ticks without a click " + zeros, zeros > 50);
            assertTrue(st + ": ticks with one click " + ones, ones > 200);
        }
    }

    @Test
    public void notClickingMeansNoClicks() {
        ClickModel m = new ClickModel(ClickModel.Style.BUTTERFLY, Skill.PRO, new Random(3));
        int n = 0;
        for (int t = 0; t < 200; t++) n += m.step(0.05, false);
        assertTrue(n == 0);
    }

    @Test
    public void aimSettlesOnAStillTargetWithHumanOvershoot() {
        int overshoots = 0, runs = 40;
        for (int r = 0; r < runs; r++) {
            AimModel a = new AimModel(Skill.GREAT, new Random(100 + r));
            double yaw = 0, pitch = 0, maxPast = 0;
            for (int t = 0; t < 60; t++) {
                a.target(40, 5, 3, t == 0, false);
                for (int f = 0; f < 3; f++) {
                    double[] d = a.step(yaw, pitch, 1 / 60.0);
                    yaw += d[0];
                    pitch += d[1];
                    maxPast = Math.max(maxPast, yaw - 40);
                }
            }
            assertTrue("run " + r + " ended " + yaw + " off 40", Math.abs(yaw - 40) < 3.5);
            if (maxPast > 0.3) overshoots++;
        }
        // people overshoot some of the time, not every time and not never
        assertTrue("overshot " + overshoots + " of " + runs, overshoots > 2 && overshoots < runs - 2);
    }
}
