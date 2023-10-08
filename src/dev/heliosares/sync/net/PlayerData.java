package dev.heliosares.sync.net;

import dev.heliosares.sync.SyncAPI;
import dev.heliosares.sync.SyncCore;
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

public class PlayerData {


    private abstract class Variable<T> {
        public final String name;
        private final boolean isFinal;
        private T value;

        protected Variable(String name, T def, boolean isFinal) {
            this.name = name;
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
            if (!this.equals(vanished) && !(plugin.getSync() instanceof SyncServer)) {
                throw new IllegalArgumentException("Cannot update the value of " + name + " from spigot servers.");
            }
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
            o.put(name, value);
        }

        protected final void processJSON(JSONObject o) {
            if (!o.has(name)) return;
            processVariable(o.get(name));
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
    }

    private final SyncCore plugin;
    private final VariableString server;
    private final VariableString name;
    private final VariableUUID uuid;
    private final VariableBoolean vanished;
    private final VariableSetUUID alts;

    PlayerData(SyncCore plugin, String server, String name, UUID uuid, boolean vanished) {
        this.plugin = plugin;
        this.server = new VariableString("server", server, false);
        this.name = new VariableString("name", name, true);
        this.uuid = new VariableUUID("uuid", uuid, true);
        this.vanished = new VariableBoolean("v", vanished, false);
        this.alts = new VariableSetUUID("alts", new HashSet<>(), false);
    }

    PlayerData(SyncCore plugin, JSONObject o) throws JSONException {
        this(plugin, null, null, null, false);
        this.server.processJSON(o);
        this.name.processJSON(o);
        this.uuid.processJSON(o);
        this.vanished.processJSON(o);
        this.alts.processJSON(o);
    }

    @CheckReturnValue
    public JSONObject toJSON() {
        JSONObject o = new JSONObject();
        this.server.putJSON(o);
        this.name.putJSON(o);
        this.uuid.putJSON(o);
        this.vanished.putJSON(o);
        this.alts.putJSON(o);
        return o;
    }

    protected void handleUpdate(String field, Object value) {
        (
                switch (field) {
                    case "server" -> server;
                    case "name" -> name;
                    case "uuid" -> uuid;
                    case "vanished" -> vanished;
                    case "alts" -> alts;
                    default -> throw new IllegalArgumentException();
                }
        ).processVariable(value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(server, name, uuid, vanished, alts);
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
    public Set<UUID> getAlts() {
        return alts.getValue();
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
        SyncAPI.send(getServer(), new Packet(null, Packets.PLAY_SOUND.id, new JSONObject()
                .put("to", getUUID())
                .put("sound", sound)
                .put("pitch", pitch)
                .put("volume", volume))
        );
    }
}
