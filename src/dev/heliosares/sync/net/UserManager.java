package dev.heliosares.sync.net;

import dev.heliosares.sync.SyncCore;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import javax.annotation.Nullable;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.*;
import java.util.Map.Entry;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class UserManager extends NetListener {

    private final SyncNetCore sync;
    private final SyncCore plugin;
    private final Map<String, List<PlayerData>> players = new HashMap<>();
    private int lasthash;

    public UserManager(SyncCore plugin, SyncNetCore client) {
        super(Packets.PLAYER_DATA.id, null);
        this.sync = client;
        this.plugin = plugin;

        if (sync instanceof SyncClient)
            plugin.scheduleAsync(() -> sendCurrentHash(), 10000, 10000);
    }

    private static int hash(List<PlayerData> players) {
        if (players == null || players.isEmpty()) {
            return 0;
        }
        return players.stream().map(PlayerData::hashData).reduce(Integer::sum).get();
    }

    private static List<PlayerData> getPlayerData(String server, JSONArray arr) {
        List<PlayerData> list = new ArrayList<>();
        arr.forEach(o -> {
            PlayerData data = getPlayerData(server, (JSONObject) o);
            if (data != null)
                list.add(data);
        });
        return list;
    }

    private static PlayerData getPlayerData(String server, JSONObject o) {
        try {
            return new PlayerData(server, o.getString("name"), o.getString("uuid"), o.getBoolean("v"));
        } catch (JSONException e) {
            return null;
        }
    }

    private void sendCurrentHash() {
        List<PlayerData> pl = plugin.getPlayers();
        if (pl.size() == 0) {
            return;
        }
        int hash = hash(pl);
        if (hash == lasthash) {
            return;
        }
        try {
            sync.send(new Packet(null, Packets.PLAYER_DATA.id, new JSONObject().put("hash", lasthash = hash)));
        } catch (Exception e) {
            plugin.print(e);
        }
    }

    @Override
    public void execute(String server, Packet packet) {
        if (packet.getPayload().has("request")) {
            if (packet.getPayload().getString("request").equalsIgnoreCase("all")) {
                try {
                    sendPlayers(packet.getForward());
                } catch (IOException | GeneralSecurityException e) {
                    plugin.print(e);
                    return;
                }
            }
        } else if (packet.getPayload().has("players")) {
            synchronized (players) {
                players.put(packet.getForward(),
                        getPlayerData(packet.getForward(), packet.getPayload().getJSONArray("players")));
            }
        } else {
            if (packet.getPayload().has("join")) {
                List<PlayerData> add = getPlayerData(packet.getForward(), packet.getPayload().getJSONArray("join"));
                List<PlayerData> current = players.get(packet.getForward());
                if (current == null) {
                    current = add;
                } else {
                    add.forEach(p -> quit(p.getServer(), p.getUUID()));
                    current.addAll(add);
                }
                synchronized (players) {
                    players.put(packet.getForward(), current);
                }
            } else if (packet.getPayload().has("quit")) {
                packet.getPayload().getJSONArray("quit").toList().stream().map(o -> (String) o)
                        .forEach(uuid -> quit(packet.getForward(), UUID.fromString(uuid)));
            } else {
                return;
            }

            if (packet.getPayload().has("hash")) {
                int hash = packet.getPayload().getInt("hash");
                int otherhash = 0;
                try {
                    synchronized (players) {
                        otherhash = hash(players.get(packet.getForward()));
                    }
                } catch (NullPointerException e) {
                }
                if (hash != otherhash) {
                    plugin.warning("Hash mismatch! " + hash + "!=" + otherhash);
                    request(packet.getForward());
                }
            }
        }
    }

    protected void request(String server) {
        try {
            sync.send(server, new Packet(null, Packets.PLAYER_DATA.id, new JSONObject().put("request", "all"))
                    .setForward(sync.getName()));
        } catch (JSONException | IOException | GeneralSecurityException e) {
            plugin.print(e);
        }
    }

    public void sendPlayers(@Nullable String server) throws IOException, GeneralSecurityException {
        if (sync instanceof SyncServer) {
            return;
        }
        List<PlayerData> players = plugin.getPlayers();
        sync.send(server,
                new Packet(null, Packets.PLAYER_DATA.id,
                        new JSONObject()
                                .put("players",
                                        new JSONArray(
                                                players.stream().map(PlayerData::toJSON).collect(Collectors.toList())))
                                .put("hash", hash(players))));
    }

    private boolean quit(String server, UUID uuid) {
        List<PlayerData> current = players.get(server);
        if (current != null) {
            Iterator<PlayerData> it = current.iterator();
            for (PlayerData data; it.hasNext(); ) {
                data = it.next();
                if (data.getUUID().equals(uuid)) {
                    it.remove();
                    return true;
                }
            }
        }
        return false;
    }

    public Map<String, List<PlayerData>> getPlayers() {
        Map<String, List<PlayerData>> out = new HashMap<>();
        synchronized (players) {
            players.forEach((k, v) -> out.put(k, Collections.unmodifiableList(v)));
        }
        return Collections.unmodifiableMap(out);
    }

    public List<PlayerData> getAllPlayers() {
        List<PlayerData> out = new ArrayList<>();
        synchronized (players) {
            players.forEach((k, v) -> out.addAll(v));
        }
        return out;
    }

    public PlayerData getPlayer(String name) {
        return getPlayer(d -> d.getName().equalsIgnoreCase(name));
    }

    public PlayerData getPlayer(UUID uuid) {
        return getPlayer(d -> d.getUUID().equals(uuid));
    }

    public PlayerData getPlayer(Predicate<PlayerData> predicate) {
        synchronized (players) {
            for (List<PlayerData> list : players.values()) {
                Optional<PlayerData> o = list.stream().filter(predicate).findAny();
                if (o.isPresent()) return o.get();
            }
        }
        return null;
    }

    public List<PlayerData> getPlayers(Predicate<PlayerData> predicate) {
        List<PlayerData> out = new ArrayList<>();
        synchronized (players) {
            for (List<PlayerData> list : players.values()) {
                out.addAll(list.stream().filter(predicate).toList());
            }
        }
        return out;
    }

    public String toFormattedString() {
        synchronized (players) {
            if (players.isEmpty()) {
                return "No servers";
            }
            String build = "";
            for (Entry<String, List<PlayerData>> entry : players.entrySet()) {
                build += "§6§l" + entry.getKey() + "§7: ";
                if (entry.getValue().isEmpty()) {
                    build += "None\n";
                    continue;
                }
                build += "\n";
                String line = "";
                for (PlayerData p : entry.getValue()) {
                    if (line.length() > 100) {
                        build += line + "\n";
                        line = "";
                    }
                    line += (p.isVanished() ? "§c[V]§7" : "§7") + p.getName() + ", ";
                }
                if (line.length() > 0) {
                    build += line + "\n";
                }
            }
            return build.isEmpty() ? "" : build.substring(0, build.length() - 1);
        }
    }

    public void updatePlayer(PlayerData data) {
        plugin.debug("Sending update for " + data.getName());
        try {
            sync.send("all", new Packet(null, Packets.PLAYER_DATA.id,
                    new JSONObject().put("join", new JSONArray().put(data.toJSON())).put("hash",
                            lasthash = plugin.getPlayers().stream()
                                    .map(p -> p.getUUID().equals(data.getUUID()) ? data.hashData() : p.hashData())
                                    .reduce((a, b) -> a + b).get())));
        } catch (JSONException | IOException | GeneralSecurityException e) {
            plugin.print(e);
        }
    }

    public void quitPlayer(UUID uuid) {
        plugin.debug("Sending quit for " + uuid.toString());
        try {
            Optional<Integer> hash = plugin.getPlayers().stream().filter(p -> !p.getUUID().equals(uuid))
                    .map(p -> p.hashData()).reduce((a, b) -> a + b);
            sync.send("all",
                    new Packet(null, Packets.PLAYER_DATA.id,
                            new JSONObject().put("quit", new JSONArray().put(uuid.toString())).put("hash",
                                    hash.isPresent() ? (lasthash = hash.get()) : 0)));
        } catch (JSONException | IOException | GeneralSecurityException e) {
            plugin.print(e);
        }
    }
}
