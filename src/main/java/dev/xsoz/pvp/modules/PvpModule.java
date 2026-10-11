package dev.xsoz.pvp.modules;

import dev.xsoz.pvp.ai.ClickModel;
import dev.xsoz.pvp.ai.Fighter;
import dev.xsoz.pvp.ai.Skill;
import dev.xsoz.pvp.module.Module;
import dev.xsoz.pvp.module.Setting;

/** PvP: takes over and fights like a person - human aim, human clicking, 1.8 craft. */
public final class PvpModule extends Module {
    public enum OnOff { ON, OFF }

    public static PvpModule INSTANCE;

    public final Setting<Skill> skill = add(new Setting<Skill>("Skill", Skill.GREAT, v -> v.title, v -> v.detail));
    public final Setting<ClickModel.Style> clicking = add(new Setting<ClickModel.Style>("Clicking", ClickModel.Style.JITTER, v -> v.title, v -> v.detail));
    public final Setting<Fighter.Kit> kit = add(new Setting<Fighter.Kit>("Kit", Fighter.Kit.AUTO, v -> v.title, v -> v.detail));
    public final Setting<Fighter.Reset> reset = add(new Setting<Fighter.Reset>("Sprint reset", Fighter.Reset.WTAP, v -> v.title, v -> v.detail));
    public final Setting<OnOff> teams = add(new Setting<OnOff>("Leave teammates", OnOff.ON, v -> v == OnOff.ON ? "On" : "Off",
            v -> v == OnOff.ON ? "Never goes for someone on its team (same team or same name colour)." : "Fights everyone but friends."));
    public final Setting<OnOff> thoughts = add(new Setting<OnOff>("Thoughts", OnOff.ON, v -> v == OnOff.ON ? "On" : "Off",
            v -> v == OnOff.ON ? "Shows what it's doing, top left." : "Nothing on screen."));

    public PvpModule() {
        super("PvP", "Takes over and fights like a person: human aim and clicking, sprint resets, rods, bows, pots - the craft of 1.8.");
        INSTANCE = this;
    }

    /** Taking over the player: never on by itself at start. */
    @Override
    public boolean remembersEnabled() { return false; }
}
