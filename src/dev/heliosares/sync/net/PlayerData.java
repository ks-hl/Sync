package dev.heliosares.sync.net;

import dev.heliosares.sync.SyncAPI;
import dev.heliosares.sync.SyncCore;
import dev.heliosares.sync.utils.ConcurrentMap;
import net.md_5.bungee.api.chat.BaseComponent;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import javax.annotation.CheckReturnValue;
import javax.annotation.Nullable;
import java.io.IOException;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Function;

public class PlayerData {

    private abstract class Variable<T> {
        public final String name;
        public final String nameOnly;
        private final boolean isFinal;
        private T value;

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
        public final CompletableFuture<Boolean> setValue(T value) {
            if (isFinal) {
                throw new IllegalArgumentException(name + " is final.");
            }
            CompletableFuture<Boolean> result = new CompletableFuture<>();
            if (Objects.equals(getValue(), value)) {
                result.complete(true);
            } else plugin.runAsync(() -> {
                T originalValue = getValue();

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

                setValue(originalValue);
                result.complete(false);
            });
            return result;
        }

        void setValueWithoutUpdate(T value) {
            this.value = value;
        }

        public final T getValue() {
            return value;
        }

        protected final void putJSON(JSONObject o) {
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
        public VariableBoolean(String name, boolean def, boolean isFinal) {
            super(name, def, isFinal);
        }

        @Override
        protected void processVariable(Object o) {
            if (o instanceof Boolean bool) {
                setValueWithoutUpdate(bool);
            } else throwInvalidVariableType();
        }
    }

    public final class VariableSetUUID extends Variable<Set<UUID>> {
        public VariableSetUUID(String name, Set<UUID> def, boolean isFinal) {
            super(name, def, isFinal);
        }

        @Override
        protected void processVariable(Object o) {
            if (o instanceof JSONArray array) {
                Set<UUID> set = new HashSet<>();
                array.forEach(e -> set.add(UUID.fromString((String) e)));
                setValueWithoutUpdate(set);
            } else throwInvalidVariableType();
        }

        @Override
        public int hashCode() {
            return getValue().stream().mapToInt(UUID::hashCode).reduce(0, (a, b) -> a ^ b);
        }

        @Override
        public String toString() {
            return "[" + alts.getValue().stream().map(UUID::toString).reduce((a, b) -> a + ", " + b).orElse("") + "]";
        }
    }

    private static class VariableMap<V extends Variable<?>> extends ConcurrentMap<String, V> {
        private final Function<String, V> creator;
        private final String keyName;

        protected VariableMap(Function<String, V> creator, String keyName) {
            this.creator = creator;
            this.keyName = keyName;
        }

        public JSONObject toJSON() {
            JSONObject out = new JSONObject();
            forEach((k, v) -> out.put(k, v.getValue()));
            if (out.isEmpty()) return null;
            return out;
        }

        public void processJSON(JSONObject custom) {
            if (!custom.has(keyName)) return;
            JSONObject customSub = custom.getJSONObject(keyName);
            consume(map -> {
                for (String key : customSub.keySet()) {
                    Object value = customSub.get(key);
                    map.computeIfAbsent(key, k -> creator.apply(key)).processVariable(value);
                }
            });
        }
    }

    private final SyncCore plugin;
    private final VariableString server;
    private final VariableString name;
    private final VariableUUID uuid;
    private final VariableBoolean vanished;
    private final VariableSetUUID alts;
    private final VariableSetUUID ignoring;
    private final VariableMap<VariableString> customStrings = new VariableMap<>(name -> new VariableString("custom.s." + name, null, false), "s");
    private final VariableMap<VariableBoolean> customBooleans = new VariableMap<>(name -> new VariableBoolean("custom.b." + name, false, false), "b");

    PlayerData(SyncCore plugin, String server, String name, UUID uuid, boolean vanished) {
        this.plugin = plugin;
        this.server = new VariableString("server", server, false);
        this.name = new VariableString("name", name, true);
        this.uuid = new VariableUUID("uuid", uuid, true);
        this.vanished = new VariableBoolean("v", vanished, false);
        this.alts = new VariableSetUUID("alts", new HashSet<>(), false);
        this.ignoring = new VariableSetUUID("ignoring", new HashSet<>(), false);
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
            this.customBooleans.processJSON(custom);
            this.customStrings.processJSON(custom);
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
        custom.put("s", customStrings.toJSON());
        custom.put("b", customBooleans.toJSON());
        o.put("custom", custom.isEmpty() ? null : custom);
        return o;
    }

    protected void handleUpdate(String field, Object value) {
        (switch (field) {
            case "server" -> server;
            case "name" -> name;
            case "uuid" -> uuid;
            case "v" -> vanished;
            case "alts" -> alts;
            case "ignoring" -> ignoring;
            default -> {
                if (field.startsWith("custom.s.")) {
                    field = field.substring(9);
                    VariableString variableString = customStrings.get(field);
                    if (variableString != null) yield variableString;
                } else if (field.startsWith("custom.b.")) {
                    field = field.substring(9);
                    VariableBoolean variableBoolean = customBooleans.get(field);
                    if (variableBoolean != null) yield variableBoolean;
                }
                throw new IllegalArgumentException("Invalid field to update: " + field);
            }
        }).processVariable(value);
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

            return "�f" + k + "�8: �" + valueColor + v + "\n";
        };
        StringBuilder out = new StringBuilder();

        out.append(formatter.apply("name", name.getValue()));
        out.append(formatter.apply("uuid", uuid.getValue()));
        out.append(formatter.apply("server", server.getValue()));
        out.append(formatter.apply("vanished", vanished.getValue()));
        out.append(formatter.apply("alts", alts));
        out.append(formatter.apply("ignoring", ignoring));
        out.append(formatter.apply("custom", ""));
        out.append(formatter.apply("  strings", ""));
        customStrings.forEach((k, v) -> out.append("    ").append(formatter.apply(v.nameOnly, v.getValue())));
        out.append(formatter.apply("  booleans", ""));
        customBooleans.forEach((k, v) -> out.append("    ").append(formatter.apply(v.nameOnly, v.getValue())));

        return out.substring(0, out.length() - 1);
    }

    @Override
    public int hashCode() {
        return Objects.hash(server, name, uuid, vanished, alts, ignoring, customStrings, customBooleans);
    }


    @CheckReturnValue
    public String getServer() {
        return server.getValue();
    }

    @CheckReturnValue
    public String getName() {
        return name.getValue();
    }

    @CheckReturnValue
    public UUID getUUID() {
        return uuid.getValue();
    }

    @CheckReturnValue
    @SuppressWarnings("unused")
    public boolean isVanished() {
        return vanished.getValue();
    }

    public void setVanished(boolean vanished) {
        this.vanished.setValue(vanished);
    }

    @SuppressWarnings("unused")
    public void setAlts(Set<UUID> alts) {
        this.alts.setValue(alts);
    }

    @CheckReturnValue
    @SuppressWarnings("unused")
    public Set<UUID> getAlts() {
        return alts.getValue();
    }

    @SuppressWarnings("unused")
    public void setIgnoring(Set<UUID> ignoring) {
        this.ignoring.setValue(ignoring);
    }

    @CheckReturnValue
    @SuppressWarnings("unused")
    public Set<UUID> getIgnoring() {
        return ignoring.getValue();
    }

    public void setServer(String server) {
        this.server.setValue(server);
    }

    @SuppressWarnings("unused")
    public void sendMessage(String msg) throws Exception {
        SyncAPI.sendMessage(getName(), msg, null);
    }

    @SuppressWarnings("unused")
    public void sendMessage(BaseComponent[] msg) throws Exception {
        SyncAPI.sendMessage(getName(), msg, null);
    }

    @SuppressWarnings("unused")
    public void sendTitle(@Nullable String title, @Nullable String subtitle, int fadein, int duration, int fadeout) throws Exception {
        SyncAPI.sendTitle(getUUID(), title, subtitle, fadein, duration, fadeout);
    }

    @SuppressWarnings("unused")
    public void playSound(String sound, float volume, float pitch) throws Exception {
        SyncAPI.send(getServer(), new Packet(null, Packets.PLAY_SOUND.id, new JSONObject().put("to", getUUID()).put("sound", sound).put("pitch", pitch).put("volume", volume)));
    }

    @SuppressWarnings("unused")
    public void setCustom(String name, String value) {
        customStrings.function(vars -> vars.computeIfAbsent(name, name2 -> new VariableString(name2, null, false))).setValue(value);
    }

    @CheckReturnValue
    @Nullable
    @SuppressWarnings("unused")
    public String getCustomString(String name) {
        VariableString variable = customStrings.get(name);
        if (variable == null) return null;
        return variable.getValue();
    }

    @SuppressWarnings("unused")
    public void setCustom(String name, boolean value) {
        customBooleans.function(vars -> vars.computeIfAbsent(name, name2 -> new VariableBoolean(name2, !value, false))).setValue(value);
    }

    @CheckReturnValue
    @Nullable
    @SuppressWarnings("unused")
    public Boolean getCustomBoolean(String name) {
        VariableBoolean variable = customBooleans.get(name);
        if (variable == null) return null;
        return variable.getValue();
    }
}
