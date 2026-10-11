package dev.xsoz.pvp.module;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Every module, in menu order, and their settings file (config/xsozautomata-pvp.json). */
public final class Modules {
    private static final List<Module> ALL = new ArrayList<Module>();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static File file;

    private Modules() { }

    public static <T extends Module> T register(T m) {
        ALL.add(m);
        return m;
    }

    public static List<Module> all() { return Collections.unmodifiableList(ALL); }

    public static void load(File configFile) {
        file = configFile;
        if (!file.exists()) return;
        try {
            JsonObject root = new JsonParser().parse(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8)).getAsJsonObject();
            for (Module m : ALL) {
                if (!root.has(m.name())) continue;
                JsonObject o = root.getAsJsonObject(m.name());
                m.loadAll(o);
                if (o.has("enabled") && m.remembersEnabled()) m.setEnabled(o.get("enabled").getAsBoolean());
            }
        } catch (IOException | RuntimeException e) {
            System.out.println("[Xsoz Automata] couldn't read the settings, using defaults: " + e);
        }
    }

    public static void save() {
        if (file == null) return;
        JsonObject root = new JsonObject();
        for (Module m : ALL) {
            JsonObject o = new JsonObject();
            o.addProperty("enabled", m.enabled());
            m.saveAll(o);
            root.add(m.name(), o);
        }
        try {
            File tmp = new File(file.getPath() + ".tmp");
            Files.write(tmp.toPath(), GSON.toJson(root).getBytes(StandardCharsets.UTF_8));
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            System.out.println("[Xsoz Automata] couldn't save the settings: " + e);
        }
    }
}
