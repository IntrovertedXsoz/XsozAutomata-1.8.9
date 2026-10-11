package dev.xsoz.pvp.modules;

import dev.xsoz.pvp.module.Module;
import dev.xsoz.pvp.module.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ChatComponentText;

/** Messages: how it tells you things (in chat, above the hotbar, or not at all). Never in public chat. */
public final class MessagesModule extends Module {
    public enum Tell {
        CHAT("In chat", "In your chat (only you see it)."),
        BAR("Above the hotbar", "A line above the hotbar that fades - nothing left in chat."),
        OFF("Off", "Never tells you anything.");

        public final String title, detail;

        Tell(String title, String detail) {
            this.title = title;
            this.detail = detail;
        }
    }

    public static MessagesModule INSTANCE;

    public final Setting<Tell> tell = add(new Setting<Tell>("Tell me", Tell.CHAT, v -> v.title, v -> v.detail));

    public MessagesModule() {
        super("Messages", "How it tells you things: in chat, above the hotbar, or not at all. It never talks in public chat.");
        INSTANCE = this;
    }

    @Override
    public boolean hasSwitch() { return false; }

    /** Tell the player something, the way they've chosen. */
    public static void say(String msg) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || INSTANCE == null) return;
        switch (INSTANCE.tell.get()) {
            case CHAT:
                mc.thePlayer.addChatMessage(new ChatComponentText("§7[§dAutomata§7] §f" + msg));
                break;
            case BAR:
                mc.ingameGUI.setRecordPlaying(msg, false);
                break;
            default:
                break;
        }
    }
}
