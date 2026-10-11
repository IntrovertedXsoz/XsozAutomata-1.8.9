package dev.xsoz.pvp.module;

import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** One page of the client: an on/off switch (if it has one), its settings, its own extras. */
public abstract class Module {
    private final String name;
    private final String description;
    private boolean enabled;
    private final List<Setting<?>> settings = new ArrayList<Setting<?>>();

    protected Module(String name, String description) {
        this.name = name;
        this.description = description;
    }

    public final String name() { return name; }

    public final String description() { return description; }

    public final boolean enabled() { return enabled; }

    public final void setEnabled(boolean on) {
        if (on == enabled) return;
        enabled = on;
        if (on) onEnable();
        else onDisable();
    }

    public final void toggle() { setEnabled(!enabled); }

    protected final <E extends Enum<E>> Setting<E> add(Setting<E> s) {
        settings.add(s);
        return s;
    }

    public final List<Setting<?>> settings() { return Collections.unmodifiableList(settings); }

    public final void saveAll(JsonObject out) {
        for (Setting<?> s : settings) s.save(out);
        save(out);
    }

    public final void loadAll(JsonObject in) {
        for (Setting<?> s : settings) s.load(in);
        load(in);
    }

    /** Whether it has an on/off switch at all (a page of settings only, otherwise). */
    public boolean hasSwitch() { return true; }

    /** Whether on/off is remembered between game starts (things that take over the player start off). */
    public boolean remembersEnabled() { return true; }

    protected void onEnable() { }

    protected void onDisable() { }

    public void save(JsonObject out) { }

    public void load(JsonObject in) { }
}
