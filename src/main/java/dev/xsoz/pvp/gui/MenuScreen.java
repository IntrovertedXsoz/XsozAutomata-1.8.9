package dev.xsoz.pvp.gui;

import dev.xsoz.pvp.XsozPvp;
import dev.xsoz.pvp.module.Module;
import dev.xsoz.pvp.module.Modules;
import dev.xsoz.pvp.module.Setting;
import dev.xsoz.pvp.modules.FriendsModule;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * The menu (Right Shift): the modules down the left (a dot for on/off), the open one on the right -
 * its switch, its settings (left click: next value, right click: the one before), and anything else
 * it has (the friends list). Hover anything and the bar along the bottom says what it does.
 */
public final class MenuScreen extends GuiScreen {
    private static final int PANEL = 0xF0121419, HEADER = 0xFF0C0D11, SIDE = 0xFF0E1014, LINE = 0xFF23262E;
    private static final int ACCENT = 0xFF8B6CFF, TEXT = 0xFFE8E8EE, DIM = 0xFF8D8F9A, FAINT = 0xFF5A5D68;
    private static final int ON = 0xFF4CD08A, OFF = 0xFF4A4D57, HOVER = 0x14FFFFFF, BOX = 0xFF1B1E25, BOX_HOVER = 0xFF262A33, FIELD = 0xFF0A0B0E;
    private static final int HEAD_H = 24, FOOT_H = 26, SIDE_W = 104, ITEM_H = 20, ROW_H = 18;

    private static int page;
    private int px, py, pw, ph, cx, cy, cw, ch;
    private int scroll, contentH;
    private String tip, flash;
    private long flashUntil;
    private GuiTextField friendBox;

    /** A clickable row or button drawn this frame. */
    private static final class Hit {
        final int x, y, w, h;
        final Runnable left, right;
        final String help;

        Hit(int x, int y, int w, int h, Runnable left, Runnable right, String help) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
            this.left = left;
            this.right = right;
            this.help = help;
        }
    }

    private final List<Hit> hits = new ArrayList<Hit>();

    @Override
    public void initGui() {
        pw = Math.min(width - 16, 460);
        ph = Math.min(height - 16, 320);
        px = (width - pw) / 2;
        py = (height - ph) / 2;
        cx = px + SIDE_W + 10;
        cw = pw - SIDE_W - 20;
        cy = py + HEAD_H + 6;
        ch = ph - HEAD_H - FOOT_H - 8;
        page = Math.max(0, Math.min(page, Modules.all().size() - 1));
        Keyboard.enableRepeatEvents(true);
        String typed = friendBox == null ? "" : friendBox.getText();
        friendBox = new GuiTextField(0, fontRendererObj, 0, 0, Math.max(40, cw - 120), 10);
        friendBox.setText(typed);
        friendBox.setEnableBackgroundDrawing(false);
        friendBox.setMaxStringLength(16);
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
        Modules.save();
    }

    @Override
    public boolean doesGuiPauseGame() { return false; }

    private static boolean in(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    @Override
    public void drawScreen(int mx, int my, float partial) {
        drawDefaultBackground();
        hits.clear();
        tip = null;
        drawRect(px - 1, py - 1, px + pw + 1, py + ph + 1, LINE);
        drawRect(px, py, px + pw, py + ph, PANEL);
        drawRect(px, py, px + pw, py + HEAD_H, HEADER);
        drawRect(px, py + HEAD_H - 1, px + pw, py + HEAD_H, ACCENT);
        fontRendererObj.drawStringWithShadow("Xsoz Automata", px + 10, py + 8, TEXT);
        fontRendererObj.drawStringWithShadow("1.8.9 PvP Edition  v" + XsozPvp.VERSION, px + 14 + fontRendererObj.getStringWidth("Xsoz Automata"), py + 8, FAINT);
        String keys = "RShift / Esc to close";
        fontRendererObj.drawStringWithShadow(keys, px + pw - 10 - fontRendererObj.getStringWidth(keys), py + 8, FAINT);
        // modules down the side
        drawRect(px, py + HEAD_H, px + SIDE_W, py + ph, SIDE);
        drawRect(px + SIDE_W, py + HEAD_H, px + SIDE_W + 1, py + ph, LINE);
        List<Module> mods = Modules.all();
        for (int i = 0; i < mods.size(); i++) {
            final int idx = i;
            Module m = mods.get(i);
            int y = py + HEAD_H + 6 + i * ITEM_H;
            boolean hover = in(mx, my, px, y, SIDE_W, ITEM_H);
            if (i == page) {
                drawRect(px, y, px + SIDE_W, y + ITEM_H, 0x22FFFFFF);
                drawRect(px, y, px + 2, y + ITEM_H, ACCENT);
            } else if (hover) drawRect(px, y, px + SIDE_W, y + ITEM_H, HOVER);
            fontRendererObj.drawStringWithShadow(m.name(), px + 12, y + 6, i == page ? TEXT : DIM);
            if (m.hasSwitch()) drawRect(px + SIDE_W - 14, y + 8, px + SIDE_W - 9, y + 13, m.enabled() ? ON : OFF);
            hits.add(new Hit(px, y, SIDE_W, ITEM_H, () -> {
                page = idx;
                scroll = 0;
            }, null, m.description()));
        }
        // the page
        if (!mods.isEmpty()) {
            int y0 = cy - scroll;
            int y = page(mods.get(page), mx, my, y0);
            contentH = y - y0;
            scroll = Math.max(0, Math.min(scroll, Math.max(0, contentH - ch)));
        }
        // the bar along the bottom
        int fy = py + ph - FOOT_H;
        drawRect(px + SIDE_W + 1, fy, px + pw, fy + 1, LINE);
        for (Hit h : hits) if (in(mx, my, h.x, h.y, h.w, h.h) && h.help != null) tip = h.help;
        String foot;
        int colour;
        if (flash != null && System.currentTimeMillis() < flashUntil) {
            foot = flash;
            colour = ACCENT;
        } else if (tip != null) {
            foot = tip;
            colour = DIM;
        } else {
            foot = "Left click: next   Right click: back   Scroll: more";
            colour = FAINT;
        }
        List<String> lines = fontRendererObj.listFormattedStringToWidth(foot, pw - SIDE_W - 20);
        int ly = fy + (FOOT_H - Math.min(2, lines.size()) * 10) / 2 + 1;
        for (int i = 0; i < Math.min(2, lines.size()); i++) fontRendererObj.drawStringWithShadow(lines.get(i), px + SIDE_W + 10, ly + i * 10, colour);
        super.drawScreen(mx, my, partial);
    }

    /** Draws a page's rows from y; returns where it ended. Rows outside the content box aren't drawn. */
    private int page(Module m, int mx, int my, int y) {
        // title and description
        if (visible(y, 14)) fontRendererObj.drawStringWithShadow("§l" + m.name(), cx, y + 2, TEXT);
        y += 14;
        for (String line : fontRendererObj.listFormattedStringToWidth(m.description(), cw)) {
            if (visible(y, 10)) fontRendererObj.drawStringWithShadow(line, cx, y, DIM);
            y += 10;
        }
        y += 6;
        if (m.hasSwitch()) {
            if (visible(y, 20)) {
                boolean hover = in(mx, my, cx, y, cw, 20);
                if (hover) drawRect(cx - 4, y, cx + cw + 4, y + 20, HOVER);
                fontRendererObj.drawStringWithShadow("Enabled", cx, y + 6, TEXT);
                int sx = cx + cw - 22, sy = y + 5;
                drawRect(sx, sy, sx + 22, sy + 10, m.enabled() ? ON : OFF);
                int kx = m.enabled() ? sx + 12 : sx;
                drawRect(kx + 1, sy + 1, kx + 9, sy + 9, 0xFFF4F4F6);
                hits.add(new Hit(cx, y, cw, 20, () -> {
                    m.toggle();
                    Modules.save();
                }, null, "Switches " + m.name() + " " + (m.enabled() ? "off" : "on") + "."));
            }
            y += 22;
        }
        if (!m.settings().isEmpty()) {
            y = section("Settings", y);
            for (Setting<?> st : m.settings()) {
                if (visible(y, ROW_H)) {
                    boolean hover = in(mx, my, cx, y, cw, ROW_H);
                    if (hover) drawRect(cx - 4, y, cx + cw + 4, y + ROW_H, HOVER);
                    fontRendererObj.drawStringWithShadow(st.name(), cx, y + 5, TEXT);
                    String v = st.valueLabel();
                    fontRendererObj.drawStringWithShadow(v, cx + cw - fontRendererObj.getStringWidth(v) - 6, y + 5, hover ? ACCENT : 0xFFC9BEFF);
                    hits.add(new Hit(cx, y, cw, ROW_H, () -> {
                        st.cycle();
                        Modules.save();
                    }, () -> {
                        st.cycleBack();
                        Modules.save();
                    }, st.name() + ": " + st.detail()));
                }
                y += ROW_H;
            }
        }
        if (m instanceof FriendsModule) y = friends((FriendsModule) m, mx, my, y);
        return y + 4;
    }

    private int friends(FriendsModule f, int mx, int my, int y) {
        y = section("Add a friend", y);
        boolean vis = visible(y, 20);
        friendBox.xPosition = cx + 5;
        friendBox.yPosition = y + 6;
        friendBox.setVisible(vis);
        if (vis) {
            drawRect(cx, y + 1, cx + cw - 110, y + 19, FIELD);
            drawRect(cx, y + 18, cx + cw - 110, y + 19, friendBox.isFocused() ? ACCENT : LINE);
            if (friendBox.getText().isEmpty() && !friendBox.isFocused()) fontRendererObj.drawString("player name", cx + 5, y + 6, FAINT);
            friendBox.drawTextBox();
            hits.add(new Hit(cx, y + 1, cw - 110, 18, () -> friendBox.setFocused(true), null, "A player's name, exactly as it shows in game."));
            button("Add", cx + cw - 104, y + 1, 40, mx, my, () -> {
                String n = friendBox.getText().trim();
                if (n.isEmpty()) {
                    flash("Type a name");
                    return;
                }
                f.add(n);
                friendBox.setText("");
                flash(n + " is a friend");
            }, "Adds them: never gone for from now on.");
            button("Looking at", cx + cw - 60, y + 1, 60, mx, my, () -> {
                MovingObjectPosition h = mc.objectMouseOver;
                if (h != null && h.entityHit instanceof EntityPlayer) {
                    f.add(h.entityHit.getName());
                    flash(h.entityHit.getName() + " is a friend");
                } else flash("Not looking at a player");
            }, "Adds the player under your crosshair.");
        }
        y += 22;
        y = section("Friends (" + f.names.size() + ")", y);
        if (f.names.isEmpty()) {
            if (visible(y, 12)) fontRendererObj.drawStringWithShadow("Nobody yet.", cx, y + 2, DIM);
            y += 12;
        }
        for (final String n : new ArrayList<String>(f.names)) {
            if (visible(y, 20)) {
                fontRendererObj.drawStringWithShadow(n, cx, y + 6, TEXT);
                button("Remove", cx + cw - 50, y + 1, 50, mx, my, () -> f.remove(n), "Takes them off the list.");
            }
            y += 20;
        }
        return y;
    }

    private void button(String label, int x, int y, int w, int mx, int my, Runnable run, String help) {
        boolean hover = in(mx, my, x, y, w, 18) && my >= cy && my < cy + ch;
        drawRect(x, y, x + w, y + 18, hover ? BOX_HOVER : BOX);
        drawRect(x, y + 17, x + w, y + 18, hover ? ACCENT : LINE);
        fontRendererObj.drawStringWithShadow(label, x + (w - fontRendererObj.getStringWidth(label)) / 2, y + 5, TEXT);
        hits.add(new Hit(x, y, w, 18, run, null, help));
    }

    private int section(String title, int y) {
        if (visible(y, 20)) {
            String t = title.toUpperCase();
            fontRendererObj.drawStringWithShadow(t, cx, y + 8, ACCENT);
            int tw = fontRendererObj.getStringWidth(t);
            drawRect(cx + tw + 6, y + 12, cx + cw, y + 13, LINE);
        }
        return y + 20;
    }

    private boolean visible(int y, int h) { return y >= cy && y + h <= cy + ch; }

    private void flash(String msg) {
        flash = msg;
        flashUntil = System.currentTimeMillis() + 3000;
    }

    @Override
    protected void mouseClicked(int mx, int my, int button) throws IOException {
        friendBox.mouseClicked(mx, my, button);
        for (Hit h : new ArrayList<Hit>(hits)) {
            if (!in(mx, my, h.x, h.y, h.w, h.h)) continue;
            Runnable r = button == 1 && h.right != null ? h.right : h.left;
            if (r == null) continue;
            mc.getSoundHandler().playSound(PositionedSoundRecord.create(new ResourceLocation("gui.button.press"), 1.0F));
            r.run();
            return;
        }
        friendBox.setFocused(in(mx, my, friendBox.xPosition - 5, friendBox.yPosition - 5, friendBox.getWidth() + 10, 18));
    }

    @Override
    protected void keyTyped(char c, int key) throws IOException {
        if (friendBox.isFocused()) {
            if (key == Keyboard.KEY_RETURN) {
                String n = friendBox.getText().trim();
                if (!n.isEmpty()) {
                    FriendsModule.INSTANCE.add(n);
                    friendBox.setText("");
                    flash(n + " is a friend");
                }
                return;
            }
            if (key != Keyboard.KEY_ESCAPE) {
                friendBox.textboxKeyTyped(c, key);
                return;
            }
        }
        if (key == XsozPvp.menuKey.getKeyCode()) {
            mc.displayGuiScreen(null);
            return;
        }
        super.keyTyped(c, key);
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0) scroll = Math.max(0, scroll - Integer.signum(wheel) * 16);
    }

    @Override
    public void updateScreen() {
        friendBox.updateCursorCounter();
    }
}
