package tc.oc.occ.Requests;

import co.aikar.commands.BukkitCommandManager;
import com.google.common.collect.Lists;
import java.util.stream.Collectors;
import org.bukkit.plugin.java.JavaPlugin;
import tc.oc.pgm.api.PGM;
import tc.oc.pgm.api.map.MapInfo;
import tc.oc.pgm.util.bukkit.BukkitUtils;

public class Requests extends JavaPlugin {

  // Inspired by 1.12 PGM and allows for map nap tab-complete to actually work :)
  public static final String SPACE = "\u2508";

  private RequestManager requests;
  private BukkitCommandManager commands;

  @Override
  public void onEnable() {
    this.requests = new RequestManager();
    this.setupCommands();
    getServer().getPluginManager().registerEvents(new RequestListener(requests), this);
  }

  private void setupCommands() {
    this.commands = new BukkitCommandManager(this);
    commands.registerDependency(RequestManager.class, requests);

    commands
        .getCommandCompletions()
        .registerCompletion(
            "requested",
            c -> {
              return Lists.newArrayList(
                  requests.getRequestedMaps().stream()
                      .map(MapInfo::getName)
                      .collect(Collectors.toList()));
            });

    commands
        .getCommandCompletions()
        .registerCompletion(
            "maps",
            c -> {
              return Lists.newArrayList(PGM.get().getMapLibrary().getMaps()).stream()
                  .map(mapInfo -> mapInfo.getName().replaceAll(" ", SPACE))
                  .collect(Collectors.toList());
            });
    commands.registerCommand(new RequestCommand());
  }

  public static final String format(String format, Object... args) {
    return BukkitUtils.colorize(String.format(format, args));
  }
}