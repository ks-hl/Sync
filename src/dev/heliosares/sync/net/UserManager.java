package dev.heliosares.sync.net;

import dev.heliosares.sync.SyncCore;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.*;
import java.util.Map.Entry;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class UserManager implements NetEventHandler.PacketConsumer {

    private final SyncNetCore sync;
    private final SyncCore plugin;
    private final Map<String, Map<UUID, PlayerData>> players = new HashMap<>();
    private int lasthash;

    public UserManager(SyncCore plugin, SyncNetCore client) {
        this.sync = client;
        this.plugin = plugin;

        if (sync instanceof SyncClient)
            plugin.scheduleAsync(this::sendCurrentHash, 10000, 10000);
    }

    private static int hash(Collection<PlayerData> players) {
        if (players == null || players.isEmpty()) {
            return 0;
        }
        return players.hashCode();
    }

    private static Map<UUID, PlayerData> getPlayerData(String server, JSONArray arr) {
        Map<UUID, PlayerData> list = new HashMap<>();
        arr.forEach(o -> {
            try {
                PlayerData data = new PlayerData(server, (JSONObject) o);
                list.put(data.getUUID(), data);
            } catch (JSONException ignored) {
            }
        });
        return list;
    }

    @Override
    public void execute(String server, Packet packet) {
        if (packet.getPayload().has("request")) {
            if (packet.getPayload().getString("request").equalsIgnoreCase("all")) {
                try {
                    sendPlayers(packet.getForward());
                } catch (IOException e) {
                    plugin.print(e);
                }
            }
        } else if (packet.getPayload().has("players")) {
            synchronized (players) {
                players.put(packet.getForward(),
                        getPlayerData(packet.getForward(), packet.getPayload().getJSONArray("players")));
            }
        } else {
            if (packet.getPayload().has("join")) {
                players.computeIfAbsent(packet.getForward(), a -> new HashMap<>()).putAll(getPlayerData(packet.getForward(), packet.getPayload().getJSONArray("join")));
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
                        otherhash = hash(players.get(packet.getForward()).values());
                    }
                } catch (NullPointerException ignored) {
                }
                if (hash != otherhash) request(packet.getForward());
            }
        }
    }

    public void sendPlayers(@Nullable String server) throws IOException {
        if (sync instanceof SyncServer) {
            return;
        }
        Set<PlayerData> players = plugin.getPlayers();
        if (players == null) return;
        sync.send(server, new Packet(null, Packets.PLAYER_DATA.id, new JSONObject().put("players",
                new JSONArray(players.stream().map(PlayerData::toJSON).collect(Collectors.toList()))).put("hash", hash(players))));
    }

    public PlayerData getPlayer(String name) {
        return getPlayer(d -> d.getName().equalsIgnoreCase(name));
    }

    public PlayerData getPlayer(UUID uuid) {
        synchronized (players) {
            return players.values().stream().filter(map -> map.containsKey(uuid)).map(map -> map.get(uuid)).findFirst().orElse(null);
        }
    }

    public PlayerData getPlayer(Predicate<PlayerData> predicate) {
        synchronized (players) {
            for (Map<UUID, PlayerData> map : players.values()) {
                Optional<PlayerData> o = map.values().stream().filter(predicate).findAny();
                if (o.isPresent()) return o.get();
            }
        }
        return null;
    }

    public String toFormattedString() {
        synchronized (players) {
            if (players.isEmpty()) {
                return "No servers";
            }
            StringBuilder build = new StringBuilder();
            for (Entry<String, Map<UUID, PlayerData>> entry : players.entrySet()) {
                build.append("§6§l").append(entry.getKey()).append("§7: ");
                if (entry.getValue().isEmpty()) {
                    build.append("None\n");
                    continue;
                }
                build.append("\n");
                StringBuilder line = new StringBuilder();
                for (PlayerData p : entry.getValue().values()) {
                    if (line.length() > 100) {
                        build.append(line).append("\n");
                        line = new StringBuilder();
                    }
                    line.append(p.isVanished() ? "§c[V]§7" : "§7").append(p.getName()).append("§8[§7").append(p.getAlts() == null ? 0 : p.getAlts().size()).append("§8]").append(", ");
                }
                build.append(line).append("\n");
            }
            return build.substring(0, build.length() - 1);
        }
    }

    public void updatePlayer(PlayerData data) {
        plugin.debug("Sending update for " + data.getName());
        plugin.runAsync(() -> {
            try {
                sync.send("all", new Packet(null, Packets.PLAYER_DATA.id,
                        new JSONObject().put("join", new JSONArray().put(data.toJSON())).put("hash",
                                lasthash = plugin.getPlayers().stream()
                                        .map(p -> p.getUUID().equals(data.getUUID()) ? data.hashData() : p.hashData())
                                        .reduce(Integer::sum).orElse(0))));
            } catch (JSONException | IOException e) {
                plugin.print(e);
            }
        });

    }

    public void quitPlayer(UUID uuid) {
        plugin.debug("Sending quit for " + uuid.toString());
        plugin.runAsync(() -> {
            try {
                int hash = hash(Objects.requireNonNull(plugin.getPlayers()).stream().filter(p -> !p.getUUID().equals(uuid))
                        .collect(Collectors.toSet()));
                sync.send("all",
                        new Packet(null, Packets.PLAYER_DATA.id,
                                new JSONObject().put("quit", new JSONArray().put(uuid.toString())).put("hash",
                                        hash)));
            } catch (JSONException | IOException e) {
                plugin.print(e);
            }
        });
    }

    protected void request(String server) {
        try {
            sync.send(server, new Packet(null, Packets.PLAYER_DATA.id, new JSONObject().put("request", "all"))
                    .setForward(sync.getName()));
        } catch (JSONException | IOException e) {
            plugin.print(e);
        }
    }

    private void sendCurrentHash() {
        Set<PlayerData> pl = plugin.getPlayers();
        if (pl == null || pl.isEmpty()) {
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

    private void quit(String server, UUID uuid) {
        synchronized (players) {
            Map<UUID, PlayerData> data = players.get(server);
            if (data == null) return;
            data.remove(uuid);
        }
    }

    public Map<String, Map<UUID, PlayerData>> getPlayers() {
        Map<String, Map<UUID, PlayerData>> out = new HashMap<>();
        synchronized (players) {
            players.forEach((k, v) -> out.put(k, Collections.unmodifiableMap(v)));
        }
        return Collections.unmodifiableMap(out);
    }

    public List<PlayerData> getAllPlayers() {
        List<PlayerData> out = new ArrayList<>();
        synchronized (players) {
            players.forEach((k, v) -> out.addAll(v.values()));
        }
        return out;
    }
}
