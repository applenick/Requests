package tc.oc.occ.Requests;

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
import java.util.stream.Collectors;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import tc.oc.pgm.api.PGM;
import tc.oc.pgm.api.map.MapInfo;
import tc.oc.pgm.api.match.Match;
import tc.oc.pgm.api.player.MatchPlayer;
import tc.oc.pgm.lib.net.kyori.text.Component;
import tc.oc.pgm.lib.net.kyori.text.TextComponent;
import tc.oc.pgm.lib.net.kyori.text.event.ClickEvent;
import tc.oc.pgm.lib.net.kyori.text.event.HoverEvent;
import tc.oc.pgm.lib.net.kyori.text.format.TextColor;
import tc.oc.pgm.lib.net.kyori.text.format.TextDecoration;
import tc.oc.pgm.listeners.ChatDispatcher;
import tc.oc.pgm.util.UsernameFormatUtils;
import tc.oc.pgm.util.chat.Audience;
import tc.oc.pgm.util.named.MapNameStyle;
import tc.oc.pgm.util.named.NameStyle;
import tc.oc.pgm.util.text.TextFormatter;
import tc.oc.pgm.util.text.types.PlayerComponent;

public class RequestCommand extends BaseCommand {

  public static final String REQUEST_PERMISSION = "occ.command.request";

  @Dependency private RequestManager requests;

  @CommandAlias("request|req")
  @Description("Request a map to be played")
  @Syntax("[map name]")
  @CommandPermission(REQUEST_PERMISSION)
  @CommandCompletion("@maps")
  public void requestMap(Player sender, @Optional String input) {
    if (!requests.isAccepting()) {
      sendWarning(
          sender, TextComponent.of("Sorry, map requests are not being accepted at this time"));
      return;
    }

    if (input == null || input.isEmpty()) {
      MapInfo requested = requests.getRequestedMap(sender);
      if (requested != null) {
        sendWarning(
            sender,
            TextComponent.builder()
                .append("You have already requested a map: ", TextColor.DARK_PURPLE)
                .append(requested.getStyledName(MapNameStyle.COLOR_WITH_AUTHORS))
                .build());
      } else {
        sendWarning(sender, TextComponent.of("You have not requested a map yet"));
      }
      return;
    }

    MapInfo map = parseMapText(input);
    if (requests.getRequestedMap(sender) != null && requests.getRequestedMap(sender).equals(map)) {
      sendWarning(
          sender,
          TextComponent.builder()
              .append("You have already requested: ")
              .append(map.getStyledName(MapNameStyle.COLOR_WITH_AUTHORS))
              .build());
      return;
    }

    requests.request(sender, map);

    Component requestConfirm =
        TextComponent.builder()
            .append(" \u2714 ", TextColor.GREEN)
            .append("Requested ", TextColor.DARK_PURPLE)
            .append(map.getStyledName(MapNameStyle.COLOR_WITH_AUTHORS))
            .hoverEvent(
                HoverEvent.showText(TextComponent.of("Click to view map info", TextColor.YELLOW)))
            .clickEvent(ClickEvent.runCommand("/map " + map.getName()))
            .build();
    sendMessage(sender, requestConfirm);

    sendVerboseRequest(sender, map);
  }

  @CommandAlias("requests|rqst|reqs")
  @Description("Manage and view map requests")
  @CommandPermission(REQUEST_PERMISSION + ".staff")
  public class RequestStaffCommand extends BaseCommand {

    @Subcommand("clear")
    @Description("Clear map requests for everyone or a single map")
    @CommandCompletion("@requested")
    public void clearRequestsCommand(CommandSender sender, @Optional String map) {
      TextComponent.Builder broadcast =
          TextComponent.builder().append(formatStaffName(sender)).append(" removed all ");
      int removed = 0;

      if (map != null && !map.isEmpty()) {
        MapInfo mapInfo = parseMapText(map);
        removed = requests.clearRequests(mapInfo);
        broadcast.append("requests for ").append(mapInfo.getStyledName(MapNameStyle.COLOR));
      } else {
        broadcast.append("map requests");
        removed = requests.clearAll();
      }

      broadcast
          .append(" (")
          .append(TextComponent.of(removed, TextColor.RED, TextDecoration.BOLD))
          .append(")")
          .color(TextColor.GRAY)
          .build();
      broadcastAC(sender, broadcast.build());
    }

    @Subcommand("toggle")
    @Description("Toggle whether map requests are accepted")
    public void toggleRequestsCommand(CommandSender sender) {
      requests.toggleAccepting();
      TextComponent.Builder broadcast =
          TextComponent.builder()
              .append(formatStaffName(sender))
              .append(" has ")
              .append(
                  requests.isAccepting() ? "enabled" : "disabled",
                  requests.isAccepting() ? TextColor.GREEN : TextColor.RED,
                  TextDecoration.BOLD)
              .append(" map requests");

      if (requests.isAccepting()) {
        broadcast
            .hoverEvent(
                HoverEvent.showText(
                    TextComponent.of("Click to view player map requests", TextColor.GRAY)))
            .clickEvent(ClickEvent.runCommand("/requests"));
      }
      broadcastAC(sender, broadcast.color(TextColor.GRAY).build());
    }

    @Default
    @Syntax("[max maps]")
    public void listRequests(CommandSender sender, @Default("10") int amount) {
      if (!requests.isAccepting()) {
        sendWarning(
            sender, TextComponent.of("Map requests are disabled. Use /requests toggle to enable"));
        return;
      }

      int max = Math.max(10, amount); // Amount of requests to show

      Component title =
          TextComponent.of("Map Requests", TextColor.DARK_PURPLE, TextDecoration.BOLD);
      Component mapCount =
          TextComponent.builder()
              .append(TextComponent.of(requests.getRequestedMaps().size(), TextColor.YELLOW))
              .hoverEvent(HoverEvent.showText(TextComponent.of("Maps", TextColor.GRAY)))
              .build();

      Component playerCount =
          TextComponent.builder()
              .append(TextComponent.of(requests.getRequesterCount(), TextColor.YELLOW))
              .hoverEvent(HoverEvent.showText(TextComponent.of("Players", TextColor.GRAY)))
              .build();

      Component header =
          TextComponent.builder()
              .append(title)
              .append(" - ")
              .append(mapCount)
              .append(":")
              .append(playerCount)
              .color(TextColor.GRAY)
              .build();

      sendMessage(sender, TextFormatter.horizontalLineHeading(sender, header, TextColor.GRAY));
      int index = 1;
      for (MapInfo map :
          requests.getRequestedMaps().stream()
              .sorted(
                  (map1, map2) ->
                      Integer.compare(
                          requests.getMapRequestCount(map2), requests.getMapRequestCount(map1)))
              .limit(Math.min(requests.getRequestedMaps().size(), max))
              .collect(Collectors.toList())) {
        sendMessage(sender, formatMapClick(map, index));
        index++;
      }
    }

    @Subcommand("verbose|vb")
    @Description("Toggle verbose request messages")
    public void toggleVerbose(Player sender) {
      TextComponent.Builder status = TextComponent.builder().append("You have");

      if (requests.isVerbose(sender.getUniqueId())) {
        requests.removeVerbosePlayer(sender.getUniqueId());
        status.append(" disabled ", TextColor.RED);
      } else {
        requests.addVerbosePlayer(sender.getUniqueId());
        status.append(" enabled ", TextColor.GREEN);
      }
      status.append("verbose map requests").color(TextColor.GRAY);
      sendWarning(sender, status.build());
    }
  }

  public static final Component DIV = TextComponent.of(" \u00BB ", TextColor.GRAY);

  private Component formatMapClick(MapInfo map, int index) {
    List<MatchPlayer> names =
        requests.getMapRequesters(map).stream()
            .filter(id -> PGM.get().getMatchManager().getPlayer(id) != null)
            .map(playerId -> PGM.get().getMatchManager().getPlayer(playerId))
            .collect(Collectors.toList());

    int mapRequestCount = requests.getMapRequestCount(map);
    Component count = TextComponent.of(mapRequestCount, TextColor.GREEN);
    count =
        count.hoverEvent(
            HoverEvent.showText(TextFormatter.nameList(names, NameStyle.FANCY, TextColor.GRAY)));

    return TextComponent.builder()
        .append(TextComponent.of(index, TextColor.GRAY))
        .append(". ")
        .append(map.getStyledName(MapNameStyle.COLOR))
        .append(DIV)
        .append(count)
        .append(" request" + (mapRequestCount != 1 ? "s" : ""), TextColor.GRAY)
        .hoverEvent(
            HoverEvent.showText(
                TextComponent.builder()
                    .append("Click to /setnext ", TextColor.DARK_PURPLE)
                    .append(map.getStyledName(MapNameStyle.COLOR_WITH_AUTHORS))
                    .build()))
        .clickEvent(ClickEvent.runCommand("/setnext " + map.getName()))
        .build();
  }

  private void sendVerboseRequest(Player requester, MapInfo request) {
    Component verbose =
        TextComponent.builder()
            .append(PlayerComponent.of(requester, NameStyle.FANCY))
            .append(" requested ")
            .append(request.getStyledName(MapNameStyle.COLOR))
            .color(TextColor.GRAY)
            .build();

    Bukkit.getOnlinePlayers().stream()
        .filter(pl -> requests.isVerbose(pl.getUniqueId()))
        .forEach(player -> sendWarning(player, verbose));
  }

  private void sendMessage(CommandSender sender, Component message) {
    Audience.get(sender).sendMessage(message);
  }

  private void sendWarning(CommandSender sender, Component message) {
    Audience.get(sender).sendWarning(message);
  }

  private Component formatStaffName(CommandSender sender) {
    Match match = PGM.get().getMatchManager().getMatch(sender);
    if (match != null) {
      return UsernameFormatUtils.formatStaffName(sender, match);
    } else {
      return PlayerComponent.of(sender, NameStyle.CONCISE);
    }
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
