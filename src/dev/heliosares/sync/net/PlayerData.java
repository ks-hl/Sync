package dev.heliosares.sync.net;

import org.json.JSONObject;

import java.util.UUID;

public class PlayerData {
    private final String server;
    private final String name;
    private final UUID uuid;
    private boolean vanished;

    public PlayerData(String server, String name, String uuid, boolean vanished) {
        this.server = server;
        this.name = name;
        this.uuid = UUID.fromString(uuid);
        this.vanished = vanished;
    }

    public JSONObject toJSON() {
        return new JSONObject().put("name", name).put("uuid", uuid).put("v", vanished);
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

    public int hashData() {
        return (uuid.toString() + (vanished ? "v" : "n")).hashCode();
    }
}
