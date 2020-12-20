package tc.oc.occ.Requests;

import static tc.oc.pgm.lib.net.kyori.adventure.text.Component.newline;
import static tc.oc.pgm.lib.net.kyori.adventure.text.Component.text;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.InvalidCommandArgument;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.CommandCompletion;
import co.aikar.commands.annotation.CommandPermission;
import co.aikar.commands.annotation.Default;
import co.aikar.commands.annotation.Dependency;
import co.aikar.commands.annotation.Description;
import co.aikar.commands.annotation.Optional;
import co.aikar.commands.annotation.Subcommand;
import co.aikar.commands.annotation.Syntax;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import tc.oc.pgm.api.PGM;
import tc.oc.pgm.api.map.MapInfo;
import tc.oc.pgm.api.map.MapOrder;
import tc.oc.pgm.api.match.Match;
import tc.oc.pgm.api.player.MatchPlayer;
import tc.oc.pgm.lib.net.kyori.adventure.text.Component;
import tc.oc.pgm.lib.net.kyori.adventure.text.TextComponent;
import tc.oc.pgm.lib.net.kyori.adventure.text.event.ClickEvent;
import tc.oc.pgm.lib.net.kyori.adventure.text.event.HoverEvent;
import tc.oc.pgm.lib.net.kyori.adventure.text.format.NamedTextColor;
import tc.oc.pgm.lib.net.kyori.adventure.text.format.TextDecoration;
import tc.oc.pgm.listeners.ChatDispatcher;
import tc.oc.pgm.rotation.MapPoolManager;
import tc.oc.pgm.rotation.VotingPool;
import tc.oc.pgm.util.Audience;
import tc.oc.pgm.util.LegacyFormatUtils;
import tc.oc.pgm.util.UsernameFormatUtils;
import tc.oc.pgm.util.named.MapNameStyle;
import tc.oc.pgm.util.named.NameStyle;
import tc.oc.pgm.util.text.TextFormatter;
import tc.oc.pgm.util.text.types.PlayerComponent;

public class RequestCommand extends BaseCommand {

  public static final String REQUEST_PERMISSION = "occ.command.request";
  public static final String REQUEST_STAFF_PERMISSION = REQUEST_PERMISSION + ".staff";

  @Dependency private RequestManager requests;

  @CommandAlias("request|req")
  @Description("Request a map to be played")
  @Syntax("[map name]")
  @CommandPermission(REQUEST_PERMISSION)
  @CommandCompletion("@maps")
  public void requestMap(Player sender, @Optional String input) {
    if (!requests.isAccepting()) {
      sendWarning(sender, text("Sorry, map requests are not being accepted at this time"));
      return;
    }

    if (!requests.canRequest(sender)) {
      int secondsLeft = requests.getCooldownRemaining(sender);
      sendWarning(
          sender,
          text("Please wait ")
              .append(text(secondsLeft, NamedTextColor.AQUA))
              .append(text(" second" + (secondsLeft != 1 ? "s" : "")))
              .append(text(" before requesting a new map."))
              .color(NamedTextColor.GRAY));
      return;
    }

    if (input == null || input.isEmpty()) {
      MapInfo requested = requests.getRequestedMap(sender);
      if (requested != null) {
        sendWarning(
            sender,
            text("You have already requested a map: ", NamedTextColor.DARK_PURPLE)
                .append(formatMapName(requested, MapNameStyle.COLOR_WITH_AUTHORS)));
      } else {
        sendWarning(sender, text("You have not requested a map yet"));
      }
      return;
    }

    MapInfo map = parseMapText(input);
    if (requests.getRequestedMap(sender) != null && requests.getRequestedMap(sender).equals(map)) {
      sendWarning(
          sender,
          text("You have already requested: ")
              .append(formatMapName(map, MapNameStyle.COLOR))
              .color(NamedTextColor.GRAY));
      return;
    }

    boolean hadRequest = requests.hasRequest(sender);

    requests.request(sender, map);

    Component requestConfirm =
        text(" \u2714 ", NamedTextColor.GREEN)
            .append(
                text(hadRequest ? "Updated request to " : "Requested ", NamedTextColor.DARK_PURPLE))
            .append(formatMapName(map, MapNameStyle.COLOR_WITH_AUTHORS));
    sendMessage(sender, requestConfirm);

    sendVerboseRequest(sender, map);
  }

  @CommandAlias("requests|rqst|reqs")
  @Description("Manage and view map requests")
  @CommandPermission(REQUEST_STAFF_PERMISSION)
  public class RequestStaffCommand extends BaseCommand {

    @Subcommand("clear")
    @Description("Clear map requests for everyone or a single map")
    @CommandCompletion("@requested")
    public void clearRequestsCommand(CommandSender sender, @Optional String map) {
      Component broadcast = formatStaffName(sender).append(text(" removed all "));
      int removed = 0;

      if (map != null && !map.isEmpty()) {
        MapInfo mapInfo = parseMapText(map);
        removed = requests.clearRequests(mapInfo);
        broadcast.append(text("requests for ")).append(formatMapName(mapInfo, MapNameStyle.COLOR));
      } else {
        broadcast.append(text("map requests"));
        removed = requests.clearAll();
      }

      broadcast
          .append(text(" ("))
          .append(text(removed, NamedTextColor.RED, TextDecoration.BOLD))
          .append(text(")"))
          .color(NamedTextColor.GRAY);
      broadcastAC(sender, broadcast);
    }

    @Subcommand("broadcast|announce")
    @Description("Broadcast an announcement to inform players you're taking requests")
    public void broadcastCommand(CommandSender sender, @Default("false") boolean showName) {
      if (!requests.isAccepting()) {
        sendWarning(sender, text("Map requests are not enabled! Unable to broadcast announcement"));
        return;
      }

      Component broadcast = newline().append(newline());

      Component named = formatStaffName(sender).append(text(" is "));
      Component alert = showName ? named : text("We are");
      alert.append(text("accepting map requests"));

      broadcast
          .append(
              TextFormatter.horizontalLineHeading(
                  sender,
                  alert.color(NamedTextColor.YELLOW),
                  NamedTextColor.GOLD,
                  TextDecoration.OBFUSCATED,
                  LegacyFormatUtils.MAX_CHAT_WIDTH))
          .append(newline())
          .append(text("  "))
          .append(DIV)
          .append(text("Use "))
          .append(text("/request [map]", NamedTextColor.AQUA, TextDecoration.UNDERLINED))
          .append(text(" submit your request"))
          .append(RDIV)
          .append(newline())
          .append(newline())
          .color(NamedTextColor.GREEN)
          .hoverEvent(HoverEvent.showText(text("Click to request a map", NamedTextColor.GRAY)))
          .clickEvent(ClickEvent.suggestCommand("/request"));

      broadcastEveryone(broadcast);
    }

    @Subcommand("toggle")
    @Description("Toggle whether map requests are accepted")
    public void toggleRequestsCommand(CommandSender sender) {
      requests.toggleAccepting();
      Component broadcast =
          formatStaffName(sender)
              .append(text(" has "))
              .append(
                  text(
                      requests.isAccepting() ? "enabled" : "disabled",
                      requests.isAccepting() ? NamedTextColor.GREEN : NamedTextColor.RED,
                      TextDecoration.BOLD))
              .append(text(" map requests"));

      if (requests.isAccepting()) {
        broadcast
            .hoverEvent(
                HoverEvent.showText(text("Click to view player map requests", NamedTextColor.GRAY)))
            .clickEvent(ClickEvent.runCommand("/requests"));
      }
      broadcastAC(sender, broadcast.color(NamedTextColor.GRAY));
    }

    @Default
    @Syntax("[max maps]")
    public void listRequests(CommandSender sender, @Default("10") int amount) {
      if (!requests.isAccepting()) {
        sendWarning(sender, text("Map requests are disabled. Use /requests toggle to enable"));
        return;
      }

      int max = Math.max(10, amount); // Amount of requests to show

      Component title = text("Map Requests", NamedTextColor.DARK_PURPLE, TextDecoration.BOLD);
      Component mapCount =
          text(requests.getRequestedMaps().size(), NamedTextColor.YELLOW)
              .hoverEvent(HoverEvent.showText(text("Maps", NamedTextColor.GRAY)));

      Component playerCount =
          text(requests.getRequesterCount(), NamedTextColor.YELLOW)
              .hoverEvent(HoverEvent.showText(text("Players", NamedTextColor.GRAY)));

      Component header =
          title
              .append(text(" - "))
              .append(mapCount)
              .append(text(":"))
              .append(playerCount)
              .color(NamedTextColor.GRAY);

      MapOrder order = PGM.get().getMapOrder();
      boolean includeVote =
          order instanceof MapPoolManager
              ? ((MapPoolManager) order).getActiveMapPool() instanceof VotingPool
              : false;

      sendMessage(sender, TextFormatter.horizontalLineHeading(sender, header, NamedTextColor.GRAY));

      List<MapInfo> requestedMaps =
          requests.getRequestedMaps().stream()
              .filter(map -> !requests.getOnlineMapRequesters(map).isEmpty())
              .collect(Collectors.toList());
      requestedMaps.sort(
          (map1, map2) ->
              -Integer.compare(
                  requests.getMapRequestCount(map1), requests.getMapRequestCount(map2)));

      int index = 1;
      for (MapInfo map : requestedMaps.subList(0, Math.min(requestedMaps.size(), max))) {
        sendMessage(sender, formatMapClick(sender, map, index, includeVote));
        index++;
      }
    }

    @Subcommand("verbose|vb")
    @Description("Toggle verbose request messages")
    public void toggleVerbose(Player sender) {
      Component status = text("You have");

      if (requests.isVerbose(sender.getUniqueId())) {
        status.append(text(" disabled ", NamedTextColor.RED));
      } else {
        status.append(text(" enabled ", NamedTextColor.GREEN));
      }
      requests.setVerboseEntry(sender.getUniqueId(), !requests.isVerbose(sender.getUniqueId()));

      status.append(text("verbose map requests")).color(NamedTextColor.GRAY);
      sendWarning(sender, status);
    }
  }

  public static final Component DIV = text(" \u00BB ", NamedTextColor.GRAY);
  public static final Component RDIV = text(" \u00AB ", NamedTextColor.GRAY);

  private Component formatMapClick(
      CommandSender sender, MapInfo map, int index, boolean includeVote) {
    Set<MatchPlayer> names = requests.getOnlineMapRequesters(map);

    int mapRequestCount = names.size();
    Component count = text(mapRequestCount, NamedTextColor.GREEN);
    count =
        count.hoverEvent(
            HoverEvent.showText(
                TextFormatter.nameList(names, NameStyle.FANCY, NamedTextColor.GRAY)));

    Component setNext =
        text("[")
            .append(text("Set", NamedTextColor.DARK_GREEN, TextDecoration.BOLD))
            .append(text("]"))
            .color(NamedTextColor.GRAY)
            .hoverEvent(HoverEvent.showText(text("Click to setnext this map", NamedTextColor.GRAY)))
            .clickEvent(ClickEvent.runCommand("/setnext " + map.getName()));

    Component vote =
        text("[")
            .append(text("Vote", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD))
            .append(text("]"))
            .color(NamedTextColor.GRAY)
            .hoverEvent(
                HoverEvent.showText(text("Click to add this map to the vote", NamedTextColor.GRAY)))
            .clickEvent(ClickEvent.runCommand("/vote add " + map.getName()));

    TextComponent.Builder mapRequest =
        text()
            .append(text(index, NamedTextColor.GRAY))
            .append(text(". "))
            .append(formatMapName(map, MapNameStyle.COLOR))
            .append(DIV)
            .append(count)
            .append(text(" request" + (mapRequestCount != 1 ? "s" : ""), NamedTextColor.GRAY))
            .append(DIV)
            .append(setNext);

    if (includeVote) {
      mapRequest.append(text(" ")).append(vote);
    }

    return mapRequest.build();
  }

  private void sendVerboseRequest(Player requester, MapInfo request) {
    Component verbose =
        PlayerComponent.player(requester, NameStyle.FANCY)
            .append(text(" requested "))
            .append(formatMapName(request, MapNameStyle.COLOR))
            .color(NamedTextColor.GRAY);

    Bukkit.getOnlinePlayers().stream()
        .filter(pl -> requests.isVerbose(pl.getUniqueId()))
        .forEach(player -> sendWarning(player, verbose));
  }

  private void sendMessage(CommandSender sender, Component message) {
    Audience.get(sender).sendMessage(message);
  }

  private void sendWarning(CommandSender sender, Component message) {
    Audience.get(sender).sendMessage(text("\u26a0 ", NamedTextColor.YELLOW).append(message));
  }

  private Component formatMapName(MapInfo info, MapNameStyle style) {
    return info.getStyledName(style)
        .hoverEvent(HoverEvent.showText(text("Click to view map info", NamedTextColor.GRAY)))
        .clickEvent(ClickEvent.runCommand("/map " + info.getName()));
  }

  private Component formatStaffName(CommandSender sender) {
    Match match = PGM.get().getMatchManager().getMatch(sender);
    if (match != null) {
      return UsernameFormatUtils.formatStaffName(sender, match);
    } else {
      return PlayerComponent.player(sender, NameStyle.CONCISE);
    }
  }

  private void broadcastEveryone(Component message) {
    Bukkit.getOnlinePlayers().stream().forEach(pl -> sendMessage(pl, message));
  }

  private void broadcastAC(CommandSender sender, Component message) {
    Match match = PGM.get().getMatchManager().getMatch(sender);
    if (match != null) {
      ChatDispatcher.broadcastAdminChatMessage(message, match);
    } else {
      sendMessage(sender, message);
    }
  }

  private MapInfo parseMapText(String input) {
    if (input.contains(Requests.SPACE)) {
      input = input.replaceAll(Requests.SPACE, " ");
    }
    MapInfo map = PGM.get().getMapLibrary().getMap(input);

    if (map == null) {
      throw new InvalidCommandArgument(
          ChatColor.AQUA + input + ChatColor.RED + " is not a valid map name", false);
    }

    return map;
  }
}
