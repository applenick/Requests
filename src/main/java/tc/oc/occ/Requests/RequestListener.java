package tc.oc.occ.Requests;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import tc.oc.pgm.api.match.event.MatchStartEvent;

public class RequestListener implements Listener {

  private RequestManager requests;

  public RequestListener(RequestManager requests) {
    this.requests = requests;
  }

  @EventHandler
  public void onMatchCycle(MatchStartEvent event) {
    requests.clearRequests(event.getMatch().getMap());
  }

  @EventHandler
  public void onStaffVerboseLogin(PlayerJoinEvent event) {
    if (event.getPlayer().hasPermission(RequestCommand.REQUEST_STAFF_PERMISSION)) {
      if (!requests.hasVerboseEntry(event.getPlayer().getUniqueId())) {
        requests.setVerboseEntry(event.getPlayer().getUniqueId(), requests.isVerboseDefault());
      }
    }
  }
}
