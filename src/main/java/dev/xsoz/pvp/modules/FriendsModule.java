package dev.xsoz.pvp.modules;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.xsoz.pvp.module.Module;
import dev.xsoz.pvp.module.Modules;

import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

/** Friends: players it never goes for. */
public final class FriendsModule extends Module {
    public static FriendsModule INSTANCE;

    /** Lower-case names. */
    public final Set<String> names = new TreeSet<String>();

    public FriendsModule() {
        super("Friends", "Players it never goes for, whatever else is set.");
        INSTANCE = this;
    }

    @Override
    public boolean hasSwitch() { return false; }

    public boolean add(String name) {
        boolean r = names.add(name.toLowerCase(Locale.ROOT));
        Modules.save();
        return r;
    }

    public boolean remove(String name) {
        boolean r = names.remove(name.toLowerCase(Locale.ROOT));
        Modules.save();
        return r;
    }

    public static boolean isFriend(String name) {
        return INSTANCE != null && name != null && INSTANCE.names.contains(name.toLowerCase(Locale.ROOT));
    }

    @Override
    public void save(JsonObject out) {
        JsonArray a = new JsonArray();
        for (String n : names) a.add(new com.google.gson.JsonPrimitive(n));
        out.add("names", a);
    }

    @Override
    public void load(JsonObject in) {
        if (!in.has("names")) return;
        for (JsonElement e : in.getAsJsonArray("names")) names.add(e.getAsString().toLowerCase(Locale.ROOT));
    }
}
