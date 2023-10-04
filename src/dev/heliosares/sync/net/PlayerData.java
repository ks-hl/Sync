package dev.heliosares.sync.net;

import dev.heliosares.sync.SyncAPI;
import net.md_5.bungee.api.chat.BaseComponent;
import org.json.JSONException;
import org.json.JSONObject;

import javax.annotation.Nullable;
import java.util.*;
import java.util.stream.Collectors;

public class PlayerData {
    private final String server;
    private final String name;
    private final UUID uuid;
    private boolean vanished;
    private Set<UUID> alts;

    public PlayerData(String server, String name, String uuid, boolean vanished) {
        this.server = server;
        this.name = name;
        this.uuid = UUID.fromString(uuid);
        this.vanished = vanished;
    }

    public PlayerData(String server, JSONObject o) throws JSONException {
        this(server, o.getString("name"), o.getString("uuid"), o.getBoolean("v"));
        alts = o.getJSONArray("alts").toList().stream().map(str -> UUID.fromString((String) str)).collect(Collectors.toUnmodifiableSet());
    }

    public JSONObject toJSON() {
        return new JSONObject().put("name", name).put("uuid", uuid).put("v", vanished).put("alts", alts);
    }

    public int hashData() {
        return Objects.hash(server, name, uuid, vanished, alts);
    }

    public void sendMessage(String msg) throws Exception {
        SyncAPI.sendMessage(name, msg, null);
    }

    public void sendMessage(BaseComponent[] msg) throws Exception {
        SyncAPI.sendMessage(name, msg, null);
    }

    public void sendTitle(@Nullable String title, @Nullable String subtitle, int fadein, int duration, int fadeout) throws Exception {
        SyncAPI.sendTitle(getUUID(), title, subtitle, fadein, duration, fadeout);
    }

    public void playSound(String sound, float volume, float pitch) throws Exception {
        SyncAPI.send(server, new Packet(null, Packets.PLAY_SOUND.id, new JSONObject()
                .put("to", uuid.toString())
                .put("sound", sound)
                .put("pitch", pitch)
                .put("volume", volume))
        );
    }

    public String getServer() {
        return server;
    }

    public String getName() {
        return name;
    }

    public UUID getUUID() {
        return uuid;
    }

    public boolean isVanished() {
        return vanished;
    }

    protected void setVanished(boolean vanished) {
        this.vanished = vanished;
    }

    public void setAlts(Set<UUID> alts) {
        this.alts = Collections.unmodifiableSet(alts);
    }

    public Set<UUID> getAlts() {
        return alts;
    }
}
