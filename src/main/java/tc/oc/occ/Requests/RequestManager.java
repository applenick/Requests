package tc.oc.occ.Requests;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import tc.oc.pgm.api.PGM;
import tc.oc.pgm.api.map.MapInfo;
import tc.oc.pgm.api.player.MatchPlayer;

public class RequestManager {

  private boolean accepting; // Whether map requests are enabled

  private Map<UUID, MapInfo> requests; // Player ids to MapInfos.

  private Map<UUID, Boolean> verboseStaff;

  private Cache<UUID, Instant> cooldownCache;

  private boolean verbose;
  private final int cooldownSeconds;

  public RequestManager(boolean enabled, boolean verbose, int cooldownSeconds) {
    this.accepting = enabled;
    this.verbose = verbose;
    this.cooldownSeconds = cooldownSeconds;
    this.requests = Maps.newHashMap();
    this.verboseStaff = Maps.newHashMap();
    this.cooldownCache =
        CacheBuilder.newBuilder().expireAfterWrite(cooldownSeconds, TimeUnit.SECONDS).build();
  }

  public boolean hasVerboseEntry(UUID staff) {
    return verboseStaff.containsKey(staff);
  }

  public void setVerboseEntry(UUID staff, boolean verbose) {
    this.verboseStaff.put(staff, verbose);
  }

  public boolean isVerbose(UUID playerId) {
    return verboseStaff.getOrDefault(playerId, false);
  }

  public boolean isVerboseDefault() {
    return verbose;
  }

  public boolean isAccepting() {
    return accepting;
  }

  public void toggleAccepting() {
    this.accepting = !accepting;
  }

  public void request(Player sender, MapInfo map) {
    this.requests.put(sender.getUniqueId(), map);
    this.cooldownCache.put(sender.getUniqueId(), Instant.now());
  }

  public boolean hasRequest(Player sender) {
    return requests.containsKey(sender.getUniqueId());
  }

  public boolean canRequest(Player player) {
    return cooldownCache.getIfPresent(player.getUniqueId()) == null;
  }

  public int getCooldownRemaining(Player player) {
    Instant lastRequest = cooldownCache.getIfPresent(player.getUniqueId());
    if (lastRequest == null) {
      return 0;
    }
    return cooldownSeconds
        - Math.toIntExact(Duration.between(lastRequest, Instant.now()).getSeconds());
  }

  public @Nullable MapInfo getRequestedMap(Player player) {
    return requests.get(player.getUniqueId());
  }

  public Set<MapInfo> getRequestedMaps() {
    return Sets.newHashSet(requests.values());
  }

  public Set<UUID> getMapRequesters(MapInfo info) {
    return requests.entrySet().stream()
        .filter(e -> e.getValue().equals(info))
        .map(e -> e.getKey())
        .collect(Collectors.toSet());
  }

  public Set<MatchPlayer> getOnlineMapRequesters(MapInfo info) {
    return getMapRequesters(info).stream()
        .filter(id -> PGM.get().getMatchManager().getPlayer(id) != null)
        .map(playerId -> PGM.get().getMatchManager().getPlayer(playerId))
        .collect(Collectors.toSet());
  }

  public int getMapRequestCount(MapInfo map) {
    return getOnlineMapRequesters(map).size();
  }

  public int getMapCount() {
    return getRequestedMaps().stream().mapToInt(m -> getOnlineMapRequesters(m).size()).sum();
  }

  public int getRequesterCount() {
    return Math.toIntExact(
        requests.keySet().stream().filter(id -> Bukkit.getPlayer(id) != null).count());
  }

  public int clearRequests(MapInfo map) {
    if (accepting) {
      Set<UUID> reqs = getMapRequesters(map);
      reqs.forEach(playerId -> requests.remove(playerId));
      return reqs.size();
    }

    return 0;
  }

  public int clearAll() {
    if (accepting) {
      int total = requests.keySet().size();
      requests.clear();
      return total;
    }
    return 0;
  }
}
