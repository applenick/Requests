package tc.oc.occ.Requests;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
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
}
