package dev.heliosares.sync.net;

import dev.heliosares.sync.SyncCore;
import dev.heliosares.sync.net.packet.MessagePacket;
import dev.heliosares.sync.net.packet.PlaySoundPacket;
import dev.heliosares.sync.net.packet.ShowTitlePacket;
import dev.kshl.kshlib.concurrent.ConcurrentMap;
import net.md_5.bungee.api.chat.BaseComponent;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import javax.annotation.CheckReturnValue;
import javax.annotation.Nullable;
import java.io.IOException;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

public class PlayerData {


    public abstract class Variable<T> {
        public final String name;
        public final String nameOnly;
        private final boolean isFinal;
        private T value;
        private long lastUpdated;

        protected Variable(String name, T def, boolean isFinal) {
            this.name = name;
            this.nameOnly = name.substring(name.lastIndexOf(".") + 1);
            this.isFinal = isFinal;
            value = def;
        }

        /**
         * Sets the value of the variable and sends an update packet. Can be called sync or async.
         *
         * @param value The value to set
         * @return whether the update packet was sent successfully. If not, the set operation fails.
         */
        @SuppressWarnings("UnusedReturnValue")
        public final CompletableFuture<Boolean> set(T value) {
            if (!this.equals(vanished) && !(plugin.getSync() instanceof SyncServer) && !this.name.startsWith("custom.")) {
                throw new IllegalArgumentException("Cannot update the value of " + name + " from spigot servers.");
            }
            if (isFinal) {
                throw new IllegalArgumentException(name + " is final.");
            }
            CompletableFuture<Boolean> result = new CompletableFuture<>();
            if (Objects.equals(get(), value)) {
                result.complete(true);
            } else plugin.runAsync(() -> {
                T originalValue = get();

                setValueWithoutUpdate(value);

                JSONObject o = new JSONObject();
                PlayerData.this.uuid.putJSON(o);
                o.put("update", name);
                putJSON(o);

                try {
                    plugin.getSync().getUserManager().sendUpdatePacket(o);
                    result.complete(true);
                    return;
                } catch (IOException e) {
                    plugin.print(e);
                }

                set(originalValue);
                result.complete(false);
            });
            return result;
        }

        void setValueWithoutUpdate(T value) {
            this.value = value;
            this.lastUpdated = System.currentTimeMillis();
        }

        public T get() {
            return value;
        }

        public final T get(T def) {
            T value = get();
            if (value == null) return def;
            return value;
        }

        public T computeIfNull(Supplier<T> supplier) {
            T val = get();
            if (val != null) return val;
            set(val = supplier.get());
            return val;
        }

        public void modify(UnaryOperator<T> operator) {
            set(operator.apply(get()));
        }

        public long getLastUpdated() {
            return lastUpdated;
        }

        protected void putJSON(JSONObject o) {
            o.put(nameOnly, value);
        }

        protected final void processJSON(JSONObject o) {
            if (!o.has(nameOnly)) return;
            processVariable(o.get(nameOnly));
        }

        protected abstract void processVariable(Object o) throws IllegalArgumentException;


        protected void throwInvalidVariableType() throws IllegalArgumentException {
            throw new IllegalArgumentException("Invalid type for variable " + name);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, value);
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Variable<?> variable)) return false;
            return variable.name.equals(name) && Objects.equals(variable.value, value);
        }

        @Override
        public String toString() {
            return name + "=" + get();
        }
    }

    public final class VariableString extends Variable<String> {
        public VariableString(String name, String def, boolean isFinal) {
            super(name, def, isFinal);
        }

        @Override
        protected void processVariable(Object o) {
            if (o instanceof String string) {
                setValueWithoutUpdate(string);
            } else throwInvalidVariableType();
        }
    }

    public final class VariableBlob extends Variable<byte[]> {
        public VariableBlob(String name, byte[] def, boolean isFinal) {
            super(name, def, isFinal);
        }

        @Override
        protected void processVariable(Object o) {
            setValueWithoutUpdate(Base64.getDecoder().decode(o.toString()));
        }

        @Override
        public void putJSON(JSONObject o) {
            o.put(nameOnly, Base64.getEncoder().encodeToString(get()));
        }
    }

    public final class VariableUUID extends Variable<UUID> {
        public VariableUUID(String name, UUID def, boolean isFinal) {
            super(name, def, isFinal);
        }

        @Override
        protected void processVariable(Object o) {
            if (o instanceof String string) {
                try {
                    setValueWithoutUpdate(UUID.fromString(string));
                } catch (IllegalArgumentException ignored) {
                    throwInvalidVariableType();
                }
            }
        }
    }

    public final class VariableBoolean extends Variable<Boolean> {
        public VariableBoolean(String name, Boolean def, boolean isFinal) {
            super(name, def, isFinal);
        }

        @Override
        protected void processVariable(Object o) {
            if (o instanceof Boolean bool) {
                setValueWithoutUpdate(bool);
            } else throwInvalidVariableType();
        }
    }

    public final class VariableSetUUID extends VariableSet<UUID> {
        public VariableSetUUID(String name, Set<UUID> def, boolean isFinal) {
            super(name, def, isFinal);
        }

        @Override
        protected UUID map(Object o) {
            return UUID.fromString(o.toString());
        }

        @Override
        public Set<UUID> get() {
            Set<UUID> out = super.get();
            if (out == null) return null;
            return Collections.unmodifiableSet(out);
        }
    }

    public final class VariableSetString extends VariableSet<String> {
        public VariableSetString(String name, Set<String> def, boolean isFinal) {
            super(name, def, isFinal);
        }

        @Override
        protected String map(Object o) {
            return o.toString();
        }

        @Override
        public Set<String> get() {
            Set<String> out = super.get();
            if (out == null) return null;
            return Collections.unmodifiableSet(out);
        }
    }

    public abstract class VariableSet<V> extends VariableCollection<V, Set<V>> {

        public VariableSet(String name, Set<V> def, boolean isFinal) {
            super(name, def, isFinal);
        }

        @Override
        protected Set<V> make(JSONArray array) {
            Set<V> set = new HashSet<>();
            array.forEach(e -> set.add(map(e)));
            return set;
        }
    }

    public abstract class VariableCollection<V, T extends Collection<V>> extends Variable<T> {
        public VariableCollection(String name, T def, boolean isFinal) {
            super(name, def, isFinal);
        }

        @Override
        protected void processVariable(Object o) {
            if (o instanceof JSONArray array) {
                T set = make(array);
                setValueWithoutUpdate(set);
            } else throwInvalidVariableType();
        }

        protected abstract T make(JSONArray array);

        protected abstract V map(Object o);

        @Override
        public int hashCode() {
            return get().stream().mapToInt(Object::hashCode).reduce(0, (a, b) -> a ^ b);
        }

        @Override
        public String toString() {
            return "[" + alts.get().stream().map(Object::toString).reduce((a, b) -> a + ", " + b).orElse("") + "]";
        }
    }

    private class MapOfVariables<V extends Variable<?>> extends ConcurrentMap<HashMap<String, V>, String, V> {
        private final String keyName;
        private final Function<String, V> creator;

        protected MapOfVariables(String keyName, Function<String, V> creator) {
            super(new HashMap<>());
            this.keyName = keyName;
            this.creator = creator;

            customMaps.put(keyName, this);
        }

        public JSONObject toJSON() {
            JSONObject out = new JSONObject();
            forEach((k, v) -> v.putJSON(out));
            if (out.isEmpty()) return null;
            return out;
        }

        public void processJSON(JSONObject custom) {
            if (!custom.has(keyName)) return;
            JSONObject customSub = custom.getJSONObject(keyName);
            consume(map -> {
                for (String key : customSub.keySet()) {
                    Object value = customSub.get(key);
                    computeIfAbsent(key).processVariable(value);
                }
            });
        }

        public V computeIfAbsent(String key) {
            return function(map -> map.computeIfAbsent(key, k -> creator.apply(key)));
        }
    }

    private final SyncCore plugin;
    private final VariableString server;
    private final VariableString name;
    private final VariableUUID uuid;
    private final VariableBoolean vanished;
    private final VariableSetUUID alts;
    private final VariableSetUUID ignoring;
    private final MapOfVariables<VariableString> customStrings;
    private final MapOfVariables<VariableSetString> customStringSets;
    private final MapOfVariables<VariableBoolean> customBooleans;
    private final MapOfVariables<VariableBlob> customBlobs;
    private final ConcurrentMap<HashMap<String, MapOfVariables<?>>, String, MapOfVariables<?>> customMaps = new ConcurrentMap<>(new HashMap<>());

    PlayerData(SyncCore plugin, String server, String name, UUID uuid, boolean vanished) {
        this.plugin = plugin;
        this.server = new VariableString("server", server, false);
        this.name = new VariableString("name", name, true);
        this.uuid = new VariableUUID("uuid", uuid, true);
        this.vanished = new VariableBoolean("v", vanished, false);
        this.alts = new VariableSetUUID("alts", new HashSet<>(), false);
        this.ignoring = new VariableSetUUID("ignoring", new HashSet<>(), false);

        this.customStrings = new MapOfVariables<>("s", varName -> new VariableString("custom.s." + varName, null, false));
        this.customStringSets = new MapOfVariables<>("ss", varName -> new VariableSetString("custom.ss." + varName, null, false));
        this.customBooleans = new MapOfVariables<>("b", varName -> new VariableBoolean("custom.b." + varName, null, false));
        this.customBlobs = new MapOfVariables<>("bl", varName -> new VariableBlob("custom.bl." + varName, null, false));
    }

    PlayerData(SyncCore plugin, JSONObject o) throws JSONException {
        this(plugin, null, null, null, false);
        this.server.processJSON(o);
        this.name.processJSON(o);
        this.uuid.processJSON(o);
        this.vanished.processJSON(o);
        this.alts.processJSON(o);
        this.ignoring.processJSON(o);

        if (o.has("custom")) {
            JSONObject custom = o.getJSONObject("custom");
            customMaps.forEach((key, map) -> map.processJSON(custom));
        }
    }

    @CheckReturnValue
    public JSONObject toJSON() {
        JSONObject o = new JSONObject();
        this.server.putJSON(o);
        this.name.putJSON(o);
        this.uuid.putJSON(o);
        this.vanished.putJSON(o);
        this.alts.putJSON(o);
        this.ignoring.putJSON(o);
        JSONObject custom = new JSONObject();
        customMaps.forEach((key, map) -> custom.put(key, map.toJSON()));
        o.put("custom", custom.isEmpty() ? null : custom);
        return o;
    }

    protected void handleUpdate(String field, JSONObject payload) {
        String nameOnly = field;
        for (int i = 0; i < 2; i++) nameOnly = nameOnly.substring(nameOnly.indexOf(".") + 1);

        (switch (field) {
            case "server" -> server;
            case "name" -> name;
            case "uuid" -> uuid;
            case "v" -> vanished;
            case "alts" -> alts;
            case "ignoring" -> ignoring;
            default -> {
                if (field.startsWith("custom.")) {
                    String key = field.split("\\.")[1];
                    MapOfVariables<?> map = customMaps.get(key);
                    if (map != null) {
                        map.processJSON(payload);
                        Variable<?> var = map.computeIfAbsent(nameOnly);
                        if (var != null) yield var;
                    }
                    throw new IllegalArgumentException("Invalid variable type '" + key + "' for field " + field);
                }
                throw new IllegalArgumentException("Invalid field to update: " + field);
            }
        }).processVariable(payload.get(nameOnly));
    }

    @Override
    public String toString() {
        return toString(false);
    }

    public String toFormattedString() {
        return toString(true);
    }

    private String toString(boolean format) {
        BiFunction<String, Object, String> formatter = (k, v) -> {
            if (!format) return k + ": " + v + "\n";
            if (v == null) v = "null";

            String valueColor = switch (v.toString().toLowerCase()) {
                case "null" -> "8";
                case "true" -> "a";
                case "false" -> "c";
                default -> "7";
            };

            return "§f" + k + "§8: §" + valueColor + v + "\n";
        };
        StringBuilder out = new StringBuilder();

        out.append(formatter.apply("name", name.get()));
        out.append(formatter.apply("uuid", uuid.get()));
        out.append(formatter.apply("server", server.get()));
        out.append(formatter.apply("vanished", vanished.get()));
        out.append(formatter.apply("alts", alts));
        out.append(formatter.apply("ignoring", ignoring));
        out.append(formatter.apply("custom", ""));
        customMaps.forEach((key, map) -> {
            out.append(formatter.apply("  " + key, ""));
            map.forEach((k, v) -> out.append("    ").append(formatter.apply(v.nameOnly, v.get())));
        });

        return out.substring(0, out.length() - 1);
    }

    @Override
    public int hashCode() {
        return Objects.hash(server, name, uuid, vanished, alts, ignoring, customMaps);
    }


    @CheckReturnValue
    public String getServer() {
        return server.get();
    }

    @CheckReturnValue
    public String getName() {
        return name.get();
    }

    @CheckReturnValue
    public UUID getUUID() {
        return uuid.get();
    }

    @CheckReturnValue
    @SuppressWarnings("unused")
    public boolean isVanished() {
        return vanished.get();
    }

    public void setVanished(boolean vanished) {
        this.vanished.set(vanished);
    }

    @SuppressWarnings("unused")
    public void setAlts(Set<UUID> alts) {
        this.alts.set(alts);
    }

    @CheckReturnValue
    @SuppressWarnings("unused")
    public Set<UUID> getAlts() {
        return alts.get();
    }

    @SuppressWarnings("unused")
    public void setIgnoring(Set<UUID> ignoring) {
        this.ignoring.set(ignoring);
    }

    @CheckReturnValue
    @SuppressWarnings("unused")
    public Set<UUID> getIgnoring() {
        return ignoring.get();
    }

    public void setServer(String server) {
        this.server.set(server);
    }

    @SuppressWarnings("unused")
    public void sendMessage(String msg) throws Exception {
        plugin.getSync().send(getServer(), new MessagePacket(null, getUUID(), null, msg, null, false));
    }

    @SuppressWarnings("unused")
    public void sendMessage(BaseComponent[] msg) throws Exception {
        plugin.getSync().send(getServer(), new MessagePacket(null, getUUID(), null, msg, false));
    }

    @SuppressWarnings("unused")
    public void sendTitle(@Nullable String title, @Nullable String subtitle, int fadein, int duration, int fadeout) throws Exception {
        plugin.getSync().send(getServer(), new ShowTitlePacket(title, subtitle, fadein, duration, fadeout, getUUID(), null));
    }

    @SuppressWarnings("unused")
    public void playSound(String sound, float volume, float pitch) throws Exception {
        plugin.getSync().send(getServer(), new PlaySoundPacket(sound, pitch, volume, getUUID(), null));
    }

    /**
     * @param plugin The name of the plugin owning the variable. Can not be null or empty. Must be alphanumeric, underscores, dashes, or periods.
     * @param name   The name of the variable. Can not be null or empty. Must be alphanumeric, underscores, dashes, or periods.
     * @param insert Whether to create this variable if it does not already exist
     * @return The handler for the custom variable specified, or null if !insert and the variable does not exist
     * @throws IllegalArgumentException If the name does not conform to the previously mentioned format
     */
    @CheckReturnValue
    @SuppressWarnings("unused")
    public VariableString getCustomString(String plugin, String name, boolean insert) {
        String combined = checkName(plugin, name, insert);
        return customStrings.function(vars -> insert ? vars.computeIfAbsent(combined, customStrings.creator) : vars.get(combined));
    }


    /**
     * @param plugin The name of the plugin owning the variable. Can not be null or empty. Must be alphanumeric, underscores, dashes, or periods.
     * @param name   The name of the variable. Can not be null or empty. Must be alphanumeric, underscores, dashes, or periods.
     * @param insert Whether to create this variable if it does not already exist
     * @return The handler for the custom variable specified, or null if !insert and the variable does not exist
     * @throws IllegalArgumentException If the name does not conform to the previously mentioned format
     */
    @CheckReturnValue
    @SuppressWarnings("unused")
    public VariableBoolean getCustomBoolean(String plugin, String name, boolean insert) {
        String combined = checkName(plugin, name, insert);
        return customBooleans.function(vars -> insert ? vars.computeIfAbsent(combined, customBooleans.creator) : vars.get(combined));
    }


    /**
     * @param plugin The name of the plugin owning the variable. Can not be null or empty. Must be alphanumeric, underscores, dashes, or periods.
     * @param name   The name of the variable. Can not be null or empty. Must be alphanumeric, underscores, dashes, or periods.
     * @param insert Whether to create this variable if it does not already exist
     * @return The handler for the custom variable specified, or null if !insert and the variable does not exist
     * @throws IllegalArgumentException If the name does not conform to the previously mentioned format
     */
    @CheckReturnValue
    @SuppressWarnings("unused")
    public VariableSetString getCustomStringSet(String plugin, String name, boolean insert) {
        String combined = checkName(plugin, name, insert);
        return customStringSets.function(vars -> insert ? vars.computeIfAbsent(combined, customStringSets.creator) : vars.get(combined));
    }


    /**
     * @param plugin The name of the plugin owning the variable. Can not be null or empty. Must be alphanumeric, underscores, dashes, or periods.
     * @param name   The name of the variable. Can not be null or empty. Must be alphanumeric, underscores, dashes, or periods.
     * @param insert Whether to create this variable if it does not already exist
     * @return The handler for the custom variable specified, or null if !insert and the variable does not exist
     * @throws IllegalArgumentException If the name does not conform to the previously mentioned format
     */
    @CheckReturnValue
    @SuppressWarnings("unused")
    public VariableBlob getCustomBlob(String plugin, String name, boolean insert) {
        String combined = checkName(plugin, name, insert);
        return customBlobs.function(vars -> insert ? vars.computeIfAbsent(combined, customBlobs.creator) : vars.get(combined));
    }

    private String checkName(String plugin, String name, boolean insert) {
        String combined = plugin + ":" + name;
        if (!insert) return combined;
        if (plugin != null && plugin.matches("[\\w_\\-.]+") && name != null && name.matches("[\\w_\\-.]+"))
            return combined;
        throw new IllegalArgumentException("Custom variable name must conform to 'PluginName:VariableName'");
    }
}
