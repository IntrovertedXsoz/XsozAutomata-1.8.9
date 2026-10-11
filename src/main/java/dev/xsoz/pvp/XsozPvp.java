package dev.xsoz.pvp;

import dev.xsoz.pvp.ai.Fighter;
import dev.xsoz.pvp.gui.MenuScreen;
import dev.xsoz.pvp.module.Modules;
import dev.xsoz.pvp.modules.FriendsModule;
import dev.xsoz.pvp.modules.MessagesModule;
import dev.xsoz.pvp.modules.PvpModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;

import java.io.File;
import java.util.Locale;

/** Xsoz Automata 1.8.9 PvP Edition - everything is wired up here, once. */
@Mod(modid = XsozPvp.MODID, name = XsozPvp.NAME, version = XsozPvp.VERSION, clientSideOnly = true, acceptedMinecraftVersions = "[1.8.9]")
public final class XsozPvp {
    public static final String MODID = "xsozautomata";
    public static final String NAME = "Xsoz Automata 1.8.9 PvP Edition";
    public static final String VERSION = "0.1.0";

    public static KeyBinding menuKey, fightKey;
    private static Fighter fighter;
    private long ticks;
    private File configFile;

    public static Fighter fighter() { return fighter; }

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent e) {
        configFile = new File(e.getModConfigurationDirectory(), "xsozautomata-pvp.json");
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent e) {
        Modules.register(new PvpModule());
        Modules.register(new FriendsModule());
        Modules.register(new MessagesModule());
        Modules.load(configFile);

        // Right Shift opens the menu; fighting can have a key of its own (none to start with)
        menuKey = new KeyBinding("Open the menu", Keyboard.KEY_RSHIFT, "Xsoz Automata");
        fightKey = new KeyBinding("Fight on/off", Keyboard.KEY_NONE, "Xsoz Automata");
        ClientRegistry.registerKeyBinding(menuKey);
        ClientRegistry.registerKeyBinding(fightKey);

        fighter = new Fighter(Minecraft.getMinecraft());
        MinecraftForge.EVENT_BUS.register(this);
        ClientCommandHandler.instance.registerCommand(new AutomataCommand());
        if (SelfTest.wanted()) MinecraftForge.EVENT_BUS.register(new SelfTest());
        System.out.println("[Xsoz Automata] " + NAME + " " + VERSION + " ready (" + Modules.all().size() + " modules)");
    }

    @SubscribeEvent
    public void onKey(InputEvent.KeyInputEvent e) {
        Minecraft mc = Minecraft.getMinecraft();
        while (menuKey.isPressed()) {
            if (mc.currentScreen == null) mc.displayGuiScreen(new MenuScreen());
        }
        while (fightKey.isPressed()) {
            PvpModule.INSTANCE.toggle();
            MessagesModule.say("PvP " + (PvpModule.INSTANCE.enabled() ? "on" : "off"));
        }
    }

    /** The brain thinks at the start of the tick, before the game reads the keys it presses. */
    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.START) return;
        ticks++;
        Minecraft mc = Minecraft.getMinecraft();
        boolean on = PvpModule.INSTANCE.enabled() && mc.thePlayer != null && mc.theWorld != null && mc.currentScreen == null;
        if (on) {
            fighter.tick(PvpModule.INSTANCE);
            fighter.head.tickFallback();
        } else {
            fighter.body.release();
        }
    }

    /** The hand moves the mouse every frame. */
    @SubscribeEvent
    public void onFrame(TickEvent.RenderTickEvent e) {
        if (e.phase != TickEvent.Phase.START) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (!PvpModule.INSTANCE.enabled() || mc.thePlayer == null || mc.currentScreen != null) return;
        fighter.head.frame((ticks + e.renderTickTime) * 0.05);
    }

    /** What it's doing and thinking, top left. */
    @SubscribeEvent
    public void onOverlay(RenderGameOverlayEvent.Text e) {
        Minecraft mc = Minecraft.getMinecraft();
        if (!PvpModule.INSTANCE.enabled() || PvpModule.INSTANCE.thoughts.get() != PvpModule.OnOff.ON || mc.gameSettings.showDebugInfo) return;
        FontRenderer f = mc.fontRendererObj;
        int y = 4;
        f.drawStringWithShadow("§dXsoz Automata §7PvP", 4, y, 0xFFFFFF);
        y += 10;
        f.drawStringWithShadow(fighter.doing(), 4, y, 0xE0E0E0);
        y += 10;
        if (fighter.target() != null && mc.thePlayer != null) {
            f.drawStringWithShadow(String.format(Locale.ROOT, "%s - %.1f blocks", fighter.target().getName(), mc.thePlayer.getDistanceToEntity(fighter.target())), 4, y, 0xA0A0A0);
        }
    }

    /** /automata friend add|remove <name>, /automata friend list. */
    private static final class AutomataCommand extends CommandBase {
        @Override
        public String getCommandName() { return "automata"; }

        @Override
        public String getCommandUsage(ICommandSender sender) { return "/automata friend add|remove <name>, /automata friend list"; }

        @Override
        public int getRequiredPermissionLevel() { return 0; }

        @Override
        public boolean canCommandSenderUseCommand(ICommandSender sender) { return true; }

        @Override
        public void processCommand(ICommandSender sender, String[] args) {
            if (args.length >= 2 && args[0].equalsIgnoreCase("friend")) {
                FriendsModule f = FriendsModule.INSTANCE;
                if (args[1].equalsIgnoreCase("list")) {
                    MessagesModule.say("Friends: " + (f.names.isEmpty() ? "none yet" : String.join(", ", f.names)));
                    return;
                }
                if (args.length >= 3 && args[1].equalsIgnoreCase("add")) {
                    MessagesModule.say(f.add(args[2]) ? args[2] + " is a friend: never gone for" : args[2] + " was already a friend");
                    return;
                }
                if (args.length >= 3 && args[1].equalsIgnoreCase("remove")) {
                    MessagesModule.say(f.remove(args[2]) ? args[2] + " is no longer a friend" : args[2] + " wasn't a friend");
                    return;
                }
            }
            MessagesModule.say(getCommandUsage(sender));
        }
    }
}
