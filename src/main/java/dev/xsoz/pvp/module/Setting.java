package dev.xsoz.pvp.module;

import com.google.gson.JsonObject;

/** A module option the menu shows as a row that cycles through its values (an enum). */
public final class Setting<E extends Enum<E>> {
    public interface Text<E> {
        String of(E v);
    }

    public interface Listener<E> {
        void changed(E v);
    }

    private final String name;
    private final E[] values;
    private final Text<E> label;
    private final Text<E> detail;
    private E value;
    private Listener<E> onChange = v -> { };

    public Setting(String name, E initial, Text<E> label, Text<E> detail) {
        this.name = name;
        this.value = initial;
        this.values = initial.getDeclaringClass().getEnumConstants();
        this.label = label;
        this.detail = detail;
    }

    public Setting<E> onChange(Listener<E> c) {
        onChange = c;
        return this;
    }

    public String name() { return name; }

    public E get() { return value; }

    public String valueLabel() { return label.of(value); }

    public String detail() { return detail.of(value); }

    public void set(E v) {
        if (v == value) return;
        value = v;
        onChange.changed(v);
    }

    public void cycle() { set(values[(value.ordinal() + 1) % values.length]); }

    public void cycleBack() { set(values[(value.ordinal() + values.length - 1) % values.length]); }

    void save(JsonObject o) { o.addProperty(name, value.name()); }

    void load(JsonObject o) {
        if (!o.has(name)) return;
        String n = o.get(name).getAsString();
        for (E v : values) if (v.name().equals(n)) set(v);
    }
}
