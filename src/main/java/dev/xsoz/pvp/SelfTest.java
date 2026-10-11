package dev.xsoz.pvp;

import dev.xsoz.pvp.ai.Fighter;
import dev.xsoz.pvp.gui.MenuScreen;
import dev.xsoz.pvp.modules.PvpModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.entity.Entity;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ScreenShotHelper;
import net.minecraft.world.EnumDifficulty;
import net.minecraft.world.WorldServer;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A run that checks the client in a real game, start to finish, for development (set
 * XSOZ_SELFTEST=1): the menu opens and draws, a flat world is made, and it fights a practice dummy
 * (a zombie named Dummy) with PvP on - then it writes run/xsoz-selftest.txt and quits. The dummy is
 * only ever a target in this mode; in normal play it only fights players.
 */
public final class SelfTest {
    private enum Stage { MENU, MENU_SHOT, WORLD, SETUP, FIGHT, DONE }

    private final Minecraft mc = Minecraft.getMinecraft();
    private final List<String> report = new ArrayList<String>();
    private Stage stage = Stage.MENU;
    private int t, failures;
    private int dummyId = -1;
    private int clicksAtStart, flicksAtStart, clickTicksAtStart;
    private float minHp = 20;
    private int killedAt = -1;

    public static boolean wanted() { return System.getenv("XSOZ_SELFTEST") != null; }

    private void check(String what, boolean ok) {
        if (!ok) failures++;
        report.add((ok ? "PASS " : "FAIL ") + what);
        System.out.println("[Xsoz Automata selftest] " + (ok ? "PASS " : "FAIL ") + what);
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        t++;
        switch (stage) {
            case MENU:
                if (mc.currentScreen instanceof GuiMainMenu && t > 40) {
                    mc.displayGuiScreen(new MenuScreen());
                    stage = Stage.MENU_SHOT;
                    t = 0;
                }
                break;
            case MENU_SHOT:
                if (t == 20) {
                    check("the menu opens", mc.currentScreen instanceof MenuScreen);
                    shot("xsoz-menu.png");
                }
                if (t == 30) {
                    mc.displayGuiScreen(null);
                    WorldSettings ws = new WorldSettings(4242L, WorldSettings.GameType.SURVIVAL, false, false, WorldType.FLAT);
                    mc.launchIntegratedServer("xsoz-selftest-" + System.currentTimeMillis(), "Xsoz self-test", ws);
                    stage = Stage.WORLD;
                    t = 0;
                }
                break;
            case WORLD:
                if (mc.thePlayer != null && mc.theWorld != null && mc.thePlayer.ticksExisted > 60) {
                    check("a world loads", true);
                    setUp();
                    stage = Stage.SETUP;
                    t = 0;
                } else if (t > 1200) {
                    check("a world loads", false);
                    finish();
                }
                break;
            case SETUP:
                if (t == 20) {
                    PvpModule.INSTANCE.setEnabled(true);
                    clicksAtStart = XsozPvp.fighter().clickTotal();
                    clickTicksAtStart = XsozPvp.fighter().clickingTicks();
                    flicksAtStart = XsozPvp.fighter().head.model().flicks();
                    stage = Stage.FIGHT;
                    t = 0;
                }
                break;
            case FIGHT:
                minHp = Math.min(minHp, mc.thePlayer.getHealth());
                if (t == 60) shot("xsoz-fight.png");
                Entity d = mc.theWorld.getEntityByID(dummyId);
                if (killedAt < 0 && (d == null || !d.isEntityAlive())) killedAt = t;
                if (killedAt >= 0 && t > killedAt + 20 || t > 900) {
                    PvpModule.INSTANCE.setEnabled(false);
                    int clicks = XsozPvp.fighter().clickTotal() - clicksAtStart;
                    double secs = (XsozPvp.fighter().clickingTicks() - clickTicksAtStart) / 20.0;
                    double cps = clicks / Math.max(0.25, secs);
                    check("killed the practice dummy" + (killedAt >= 0 ? " (" + killedAt + " ticks)" : ""), killedAt >= 0);
                    check(String.format(Locale.ROOT, "clicked like a person: %.1f a second while clicking (%d clicks in %.1f s)", cps, clicks, secs),
                            cps >= 6 && cps <= 18.5);
                    int flicks = XsozPvp.fighter().head.model().flicks() - flicksAtStart;
                    check("aimed with the hand model (" + flicks + " flicks)", flicks > 0);
                    check(String.format(Locale.ROOT, "lived (lowest health %.1f)", minHp), minHp > 0);
                    finish();
                }
                break;
            default:
                break;
        }
    }

    /** Server side: a sword in hand, night, and a dummy to fight a few blocks off. */
    private void setUp() {
        final MinecraftServer srv = mc.getIntegratedServer();
        final String name = mc.thePlayer.getName();
        srv.addScheduledTask(new Runnable() {
            @Override
            public void run() {
                EntityPlayerMP p = srv.getConfigurationManager().getPlayerByUsername(name);
                WorldServer w = (WorldServer) p.worldObj;
                srv.setDifficultyForAllWorlds(EnumDifficulty.EASY);
                w.setWorldTime(18000);
                p.inventory.setInventorySlotContents(0, new ItemStack(Items.diamond_sword));
                p.inventory.currentItem = 0;
                EntityZombie z = new EntityZombie(w);
                z.setLocationAndAngles(p.posX, p.posY, p.posZ + 7, 180, 0);
                z.setCustomNameTag("Dummy");
                z.setAlwaysRenderNameTag(true);
                w.spawnEntityInWorld(z);
                dummyId = z.getEntityId();
            }
        });
    }

    private void shot(String file) {
        try {
            ScreenShotHelper.saveScreenshot(mc.mcDataDir, file, mc.displayWidth, mc.displayHeight, mc.getFramebuffer());
        } catch (RuntimeException ex) {
            report.add("(couldn't take " + file + ": " + ex + ")");
        }
    }

    private void finish() {
        stage = Stage.DONE;
        report.add(failures == 0 ? "ALL PASSED" : failures + " FAILED");
        try {
            Files.write(new File(mc.mcDataDir, "xsoz-selftest.txt").toPath(), String.join("\n", report).getBytes(StandardCharsets.UTF_8));
        } catch (IOException ignored) {
        }
        mc.shutdown();
    }
}
