package de.fleaqx.minecraftDungeons.command;

import de.fleaqx.minecraftDungeons.currency.NumberFormat;
import de.fleaqx.minecraftDungeons.enchant.EnchantService;
import de.fleaqx.minecraftDungeons.model.ZoneDefinition;
import de.fleaqx.minecraftDungeons.runtime.DungeonService;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

public class DungeonCommand implements CommandExecutor, TabCompleter {

    private final DungeonService dungeonService;
    private final EnchantService enchantService;

    public DungeonCommand(DungeonService dungeonService, EnchantService enchantService) {
        this.dungeonService = dungeonService;
        this.enchantService = enchantService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(ChatColor.YELLOW + "/dungeon reload");
            sender.sendMessage(ChatColor.YELLOW + "/dungeon start <zone> [stage]");
            sender.sendMessage(ChatColor.YELLOW + "/dungeon enchant <reload|list|setlevel|addlevel|settoollevel|addtoolxp>");
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            dungeonService.reload();
            enchantService.reload();
            sender.sendMessage(ChatColor.GREEN + "Dungeon and enchant configuration reloaded.");
            return true;
        }

        if (args[0].equalsIgnoreCase("start")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(ChatColor.RED + "Players only.");
                return true;
            }
            if (args.length < 2) {
                sender.sendMessage(ChatColor.RED + "Usage: /dungeon start <zone> [stage]");
                return true;
            }

            int stage = 1;
            if (args.length >= 3) {
                try {
                    stage = Integer.parseInt(args[2]);
                } catch (Exception ignored) {
                    stage = 1;
                }
            }

            int finalStage = stage;
            dungeonService.zoneById(args[1]).ifPresentOrElse(
                    zone -> {
                        dungeonService.forceStart(player, zone, finalStage);
                        player.sendMessage(ChatColor.GREEN + "Dungeon started: " + zone.displayName() + " Stage " + finalStage);
                    },
                    () -> sender.sendMessage(ChatColor.RED + "Zone not found."));
            return true;
        }

        if (args[0].equalsIgnoreCase("enchant")) {
            if (!sender.hasPermission("minecraftdungeons.admin")) {
                sender.sendMessage(ChatColor.RED + "No permission.");
                return true;
            }

            if (args.length < 2) {
                sender.sendMessage(ChatColor.YELLOW + "/dungeon enchant reload");
                sender.sendMessage(ChatColor.YELLOW + "/dungeon enchant list");
                sender.sendMessage(ChatColor.YELLOW + "/dungeon enchant setlevel <player> <enchantId> <level>");
                sender.sendMessage(ChatColor.YELLOW + "/dungeon enchant addlevel <player> <enchantId> <amount>");
                sender.sendMessage(ChatColor.YELLOW + "/dungeon enchant settoollevel <player> <level>");
                sender.sendMessage(ChatColor.YELLOW + "/dungeon enchant addtoolxp <player> <amount>");
                return true;
            }

            return handleEnchantAdmin(sender, args);
        }

        return true;
    }

    private boolean handleEnchantAdmin(CommandSender sender, String[] args) {
        switch (args[1].toLowerCase()) {
            case "reload" -> {
                enchantService.reload();
                sender.sendMessage(ChatColor.GREEN + "Enchant config reloaded.");
                return true;
            }
            case "list" -> {
                sender.sendMessage(ChatColor.AQUA + "Enchants: " + String.join(", ", enchantService.enchantIds()));
                return true;
            }
            case "setlevel" -> {
                if (args.length < 5) {
                    sender.sendMessage(ChatColor.RED + "Usage: /dungeon enchant setlevel <player> <enchantId> <level>");
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[2]);
                if (target == null) {
                    sender.sendMessage(ChatColor.RED + "Player not found.");
                    return true;
                }
                int level;
                try {
                    level = Integer.parseInt(args[4]);
                } catch (Exception exception) {
                    sender.sendMessage(ChatColor.RED + "Invalid level.");
                    return true;
                }
                enchantService.setEnchantLevel(target, args[3], level);
                sender.sendMessage(ChatColor.GREEN + "Enchant level updated.");
                return true;
            }
            case "addlevel" -> {
                if (args.length < 5) {
                    sender.sendMessage(ChatColor.RED + "Usage: /dungeon enchant addlevel <player> <enchantId> <amount>");
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[2]);
                if (target == null) {
                    sender.sendMessage(ChatColor.RED + "Player not found.");
                    return true;
                }
                int add;
                try {
                    add = Integer.parseInt(args[4]);
                } catch (Exception exception) {
                    sender.sendMessage(ChatColor.RED + "Invalid amount.");
                    return true;
                }
                int current = enchantService.enchantLevel(target, args[3]);
                enchantService.setEnchantLevel(target, args[3], current + Math.max(0, add));
                sender.sendMessage(ChatColor.GREEN + "Enchant level updated.");
                return true;
            }
            case "settoollevel" -> {
                if (args.length < 4) {
                    sender.sendMessage(ChatColor.RED + "Usage: /dungeon enchant settoollevel <player> <level>");
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[2]);
                if (target == null) {
                    sender.sendMessage(ChatColor.RED + "Player not found.");
                    return true;
                }
                int level;
                try {
                    level = Integer.parseInt(args[3]);
                } catch (Exception exception) {
                    sender.sendMessage(ChatColor.RED + "Invalid level.");
                    return true;
                }
                enchantService.setToolLevel(target, level);
                sender.sendMessage(ChatColor.GREEN + "Tool level updated.");
                return true;
            }
            case "addtoolxp" -> {
                if (args.length < 4) {
                    sender.sendMessage(ChatColor.RED + "Usage: /dungeon enchant addtoolxp <player> <amount>");
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[2]);
                if (target == null) {
                    sender.sendMessage(ChatColor.RED + "Player not found.");
                    return true;
                }
                BigInteger amount = NumberFormat.parse(args[3], BigInteger.ZERO);
                enchantService.addToolXp(target, amount);
                sender.sendMessage(ChatColor.GREEN + "Tool XP added.");
                return true;
            }
            default -> {
                sender.sendMessage(ChatColor.RED + "Unknown enchant subcommand.");
                return true;
            }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("reload", "start", "enchant");
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("start")) {
            List<String> ids = new ArrayList<>();
            for (ZoneDefinition zone : dungeonService.zones()) {
                ids.add(zone.id());
            }
            return ids;
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("enchant")) {
            return List.of("reload", "list", "setlevel", "addlevel", "settoollevel", "addtoolxp");
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("enchant")
                && (args[1].equalsIgnoreCase("setlevel") || args[1].equalsIgnoreCase("addlevel") || args[1].equalsIgnoreCase("settoollevel") || args[1].equalsIgnoreCase("addtoolxp"))) {
            return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
        }

        if (args.length == 4 && args[0].equalsIgnoreCase("enchant")
                && (args[1].equalsIgnoreCase("setlevel") || args[1].equalsIgnoreCase("addlevel"))) {
            return enchantService.enchantIds();
        }

        return List.of();
    }
}
