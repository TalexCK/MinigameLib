package com.talexck.minigamelib.core.resourcepack;

import com.talexck.minigamelib.api.arena.ArenaResourcePackConfig;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

public final class ResourcePackService {

  private final JavaPlugin plugin;
  private final Map<String, ServedPack> packs = new ConcurrentHashMap<>();
  private final Map<UUID, Set<String>> sentPackKeys = new ConcurrentHashMap<>();
  private HttpServer server;

  public ResourcePackService(JavaPlugin plugin) {
    this.plugin = plugin;
  }

  public void sendResourcePack(Player player, ArenaResourcePackConfig config) {
    if (config == null || !config.enabled()) {
      return;
    }
    String key = keyFor(config);
    Set<String> playerPacks =
        sentPackKeys.computeIfAbsent(player.getUniqueId(), ignored -> ConcurrentHashMap.newKeySet());
    if (!playerPacks.add(key)) {
      return;
    }
    ServedPack pack = preparePack(config);
    player.setResourcePack(pack.url(), pack.sha1(), Component.text(config.prompt()),
        config.required());
  }

  public void shutdown() {
    if (server != null) {
      server.stop(0);
      server = null;
    }
    packs.clear();
    sentPackKeys.clear();
  }

  public void clearPlayer(UUID playerId) {
    sentPackKeys.remove(playerId);
  }

  private ServedPack preparePack(ArenaResourcePackConfig config) {
    String key = keyFor(config);
    return packs.computeIfAbsent(key, ignored -> {
      try {
        Path file = extractResourcePack(config);
        byte[] sha1 = sha1(file);
        String url = urlFor(config, file.getFileName().toString());
        return new ServedPack(file, sha1, url);
      } catch (IOException exception) {
        throw new IllegalStateException("Failed to prepare resource pack: " + key, exception);
      }
    });
  }

  private String keyFor(ArenaResourcePackConfig config) {
    return config.ownerPlugin().getName() + ":" + config.resourcePath();
  }

  private Path extractResourcePack(ArenaResourcePackConfig config) throws IOException {
    Path directory = plugin.getDataFolder().toPath().resolve("resourcepacks");
    Files.createDirectories(directory);
    String fileName = sanitize(config.ownerPlugin().getName() + "-" + config.resourcePath());
    Path target = directory.resolve(fileName);

    try (InputStream input = config.ownerPlugin().getResource(config.resourcePath())) {
      if (input == null) {
        throw new IllegalArgumentException(
            "Resource pack not found in plugin resources: " + config.resourcePath());
      }
      Files.copy(input, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }
    ensureServer();
    return target;
  }

  private void ensureServer() throws IOException {
    if (server != null) {
      return;
    }
    server = HttpServer.create(new InetSocketAddress(0), 0);
    server.createContext("/resourcepacks", this::handleResourcePack);
    server.setExecutor(Executors.newSingleThreadExecutor(command -> {
      Thread thread = new Thread(command, "minigamelib-resourcepack-http");
      thread.setDaemon(true);
      return thread;
    }));
    server.start();
  }

  private void handleResourcePack(HttpExchange exchange) throws IOException {
    String prefix = "/resourcepacks/";
    String path = exchange.getRequestURI().getPath();
    if (!path.startsWith(prefix)) {
      exchange.sendResponseHeaders(404, -1);
      return;
    }
    String fileName = path.substring(prefix.length());
    ServedPack pack = packs.values().stream()
        .filter(candidate -> candidate.file().getFileName().toString().equals(fileName))
        .findFirst()
        .orElse(null);
    if (pack == null || !Files.exists(pack.file())) {
      exchange.sendResponseHeaders(404, -1);
      return;
    }

    exchange.getResponseHeaders().set("Content-Type", "application/zip");
    exchange.sendResponseHeaders(200, Files.size(pack.file()));
    try (OutputStream output = exchange.getResponseBody()) {
      Files.copy(pack.file(), output);
    }
  }

  private String urlFor(ArenaResourcePackConfig config, String fileName) {
    if (!config.publicUrlBase().isBlank()) {
      return stripTrailingSlash(config.publicUrlBase()) + "/resourcepacks/" + fileName;
    }
    return "http://" + host() + ":" + server.getAddress().getPort() + "/resourcepacks/" + fileName;
  }

  private String host() {
    String ip = Bukkit.getIp();
    return ip == null || ip.isBlank() ? "127.0.0.1" : ip;
  }

  private String sanitize(String value) {
    return value.replaceAll("[^A-Za-z0-9._-]", "_");
  }

  private String stripTrailingSlash(String value) {
    while (value.endsWith("/")) {
      value = value.substring(0, value.length() - 1);
    }
    return value;
  }

  private byte[] sha1(Path file) throws IOException {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-1");
      return digest.digest(Files.readAllBytes(file));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-1 is not available", exception);
    }
  }

  private record ServedPack(Path file, byte[] sha1, String url) {

    private ServedPack {
      Objects.requireNonNull(file, "file");
      Objects.requireNonNull(sha1, "sha1");
      Objects.requireNonNull(url, "url");
    }
  }
}
