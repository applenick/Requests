package tc.oc.occ.Requests;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.bukkit.entity.Player;
import tc.oc.pgm.api.PGM;
import tc.oc.pgm.api.map.MapInfo;
import tc.oc.pgm.api.player.MatchPlayer;

public class RequestManager {

  private boolean accepting; // Whether map requests are enabled

  private Map<UUID, MapInfo> requests; // Player ids to MapInfos.

  private Set<UUID> verboseStaff;

  public RequestManager(boolean enabled) {
    this.accepting = enabled;
    this.requests = Maps.newHashMap();
    this.verboseStaff = Sets.newHashSet();
  }

  public void addVerbosePlayer(UUID staff) {
    this.verboseStaff.add(staff);
  }

  public void removeVerbosePlayer(UUID staff) {
    this.verboseStaff.remove(staff);
  }

  public boolean isVerbose(UUID playerId) {
    return verboseStaff.contains(playerId);
  }

  public boolean isAccepting() {
    return accepting;
  }

  public void toggleAccepting() {
    this.accepting = !accepting;
  }

  public void request(Player sender, MapInfo map) {
    this.requests.put(sender.getUniqueId(), map);
  }

  public boolean hasRequest(Player sender) {
    return requests.containsKey(sender.getUniqueId());
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
    return getRequestedMaps().size();
  }

  public int getRequesterCount() {
    return requests.keySet().size();
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
