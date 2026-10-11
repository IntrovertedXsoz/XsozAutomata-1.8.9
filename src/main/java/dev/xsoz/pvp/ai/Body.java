package dev.xsoz.pvp.ai;

import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.item.ItemStack;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

/**
 * The hands on the keyboard and mouse buttons. Each tick the brain says which keys are down and
 * how many clicks to make; they go in through the game's own key bindings - the same thing a real
 * key press does, so the game (and the server) sees an ordinary player.
 */
public final class Body {
    private final Minecraft mc;
    public final Head head;
    private boolean fwd, back, left, right, jump, sneak, sprint, use;
    private int clicks;
    private boolean driving;
    /** Hotbar slot to change to, and when (a hand takes a moment to reach the key). */
    private int wantSlot = -1;
    private long slotAt;
    private long tick;

    public Body(Minecraft mc, Head head) {
        this.mc = mc;
        this.head = head;
    }

    public void beginTick(long t) {
        tick = t;
        fwd = back = left = right = jump = sneak = sprint = use = false;
        clicks = 0;
        head.beginTick(t);
    }

    public void forward() { fwd = true; }

    public void back() { back = true; }

    /** Not forward this tick, whatever else asked (an edge ahead). */
    public void noForward() { fwd = false; }

    public void left() { left = true; }

    public void right() { right = true; }

    public void jump() { jump = true; }

    public void sneak() { sneak = true; }

    public void sprint() { sprint = true; }

    /** Hold right click (block with the sword, eat, draw the bow). */
    public void use() { use = true; }

    public void click(int n) { clicks += n; }

    public boolean usingKeys() { return driving; }

    /** Put this hotbar slot in hand, after a human moment (2-4 ticks to reach the number key). */
    public boolean select(int slot) {
        if (mc.thePlayer == null || slot < 0 || slot > 8) return false;
        if (mc.thePlayer.inventory.currentItem == slot) {
            wantSlot = -1;
            return true;
        }
        if (wantSlot != slot) {
            wantSlot = slot;
            slotAt = tick + 2 + (long) (Math.random() * 3);
        }
        return false;
    }

    /** First hotbar slot holding something that passes the test, or -1. */
    public int hotbar(ItemTest t) {
        if (mc.thePlayer == null) return -1;
        for (int i = 0; i < 9; i++) {
            ItemStack s = mc.thePlayer.inventory.mainInventory[i];
            if (s != null && t.test(s)) return i;
        }
        return -1;
    }

    public interface ItemTest {
        boolean test(ItemStack s);
    }

    public void apply() {
        GameSettings o = mc.gameSettings;
        driving = true;
        set(o.keyBindForward, fwd && !back);
        set(o.keyBindBack, back && !fwd);
        set(o.keyBindLeft, left && !right);
        set(o.keyBindRight, right && !left);
        set(o.keyBindJump, jump);
        set(o.keyBindSneak, sneak);
        set(o.keyBindSprint, sprint);
        set(o.keyBindUseItem, use);
        for (int i = 0; i < clicks; i++) KeyBinding.onTick(o.keyBindAttack.getKeyCode());
        if (wantSlot >= 0 && tick >= slotAt && mc.thePlayer != null) {
            mc.thePlayer.inventory.currentItem = wantSlot;
            wantSlot = -1;
        }
        head.endTick();
    }

    /** Let go: every key back to whatever the real keyboard and mouse say. */
    public void release() {
        if (!driving) return;
        driving = false;
        GameSettings o = mc.gameSettings;
        for (KeyBinding k : new KeyBinding[] {o.keyBindForward, o.keyBindBack, o.keyBindLeft, o.keyBindRight, o.keyBindJump,
                o.keyBindSneak, o.keyBindSprint, o.keyBindUseItem, o.keyBindAttack}) {
            KeyBinding.setKeyBindState(k.getKeyCode(), physicallyDown(k.getKeyCode()));
        }
        head.model().clear();
        wantSlot = -1;
    }

    private static boolean physicallyDown(int code) {
        if (code == 0) return false;
        return code < 0 ? Mouse.isButtonDown(code + 100) : Keyboard.isKeyDown(code);
    }

    private static void set(KeyBinding k, boolean down) {
        KeyBinding.setKeyBindState(k.getKeyCode(), down);
    }
}
