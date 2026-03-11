package de.fleaqx.minecraftDungeons.command;

import de.fleaqx.minecraftDungeons.companion.CompanionService;
import de.fleaqx.minecraftDungeons.companion.ui.CompanionMenuService;
import de.fleaqx.minecraftDungeons.currency.CurrencyType;
import de.fleaqx.minecraftDungeons.currency.NumberFormat;
import de.fleaqx.minecraftDungeons.enchant.EnchantService;
import de.fleaqx.minecraftDungeons.model.ZoneDefinition;
import de.fleaqx.minecraftDungeons.profile.PlayerProfile;
import de.fleaqx.minecraftDungeons.runtime.DungeonService;
import de.fleaqx.minecraftDungeons.sword.SwordPerkService;
import de.fleaqx.minecraftDungeons.sword.SwordService;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

public class DungeonCommand implements CommandExecutor, TabCompleter {

    private final JavaPlugin plugin;
    private final DungeonService dungeonService;
    private final EnchantService enchantService;
    private final SwordPerkService swordPerkService;
    private final SwordService swordService;
    private final CompanionService companionService;
    private final CompanionMenuService companionMenuService;

    public DungeonCommand(JavaPlugin plugin, DungeonService dungeonService, EnchantService enchantService, SwordPerkService swordPerkService, SwordService swordService, CompanionService companionService, CompanionMenuService companionMenuService) {
        this.plugin = plugin;
        this.dungeonService = dungeonService;
        this.enchantService = enchantService;
        this.swordPerkService = swordPerkService;
        this.swordService = swordService;
        this.companionService = companionService;
        this.companionMenuService = companionMenuService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(ChatColor.YELLOW + "/dungeon reload");
            sender.sendMessage(ChatColor.YELLOW + "/dungeon start <zone> [stage]");
            sender.sendMessage(ChatColor.YELLOW + "/dungeon enchant <reload|list|setlevel|addlevel|settoollevel|addtoolxp>");
            sender.sendMessage(ChatColor.YELLOW + "/dungeon perk <reload|list|set|addpoints>");
            sender.sendMessage(ChatColor.YELLOW + "/dungeon economy <get|set|add|take> <player> <currency|all> [amount]");
            sender.sendMessage(ChatColor.YELLOW + "/dungeon companion <setlocation|egg|ui>");
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            plugin.reloadConfig();
            dungeonService.reload();
            enchantService.reload();
            swordPerkService.reload();
            sender.sendMessage(ChatColor.GREEN + "Dungeon, enchant and perk configuration reloaded.");
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

        if (args[0].equalsIgnoreCase("perk")) {
            if (!sender.hasPermission("minecraftdungeons.admin")) {
                sender.sendMessage(ChatColor.RED + "No permission.");
                return true;
            }
            return handlePerkAdmin(sender, args);
        }

        if (args[0].equalsIgnoreCase("economy")) {
            if (!sender.hasPermission("minecraftdungeons.admin")) {
                sender.sendMessage(ChatColor.RED + "No permission.");
                return true;
            }
            return handleEconomyAdmin(sender, args);
        }

        if (args[0].equalsIgnoreCase("companion")) {
            return handleCompanion(sender, args);
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

    private boolean handlePerkAdmin(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.YELLOW + "/dungeon perk reload");
            sender.sendMessage(ChatColor.YELLOW + "/dungeon perk list");
            sender.sendMessage(ChatColor.YELLOW + "/dungeon perk set <player> <perkId> <level>");
            sender.sendMessage(ChatColor.YELLOW + "/dungeon perk addpoints <player> <amount>");
            return true;
        }

        switch (args[1].toLowerCase()) {
            case "reload" -> {
                plugin.reloadConfig();
                swordPerkService.reload();
                sender.sendMessage(ChatColor.GREEN + "Perk config reloaded.");
                return true;
            }
            case "list" -> {
                List<String> ids = swordPerkService.availablePerks().stream().map(SwordPerkService.PerkDefinition::id).toList();
                sender.sendMessage(ChatColor.AQUA + "Perks: " + String.join(", ", ids));
                return true;
            }
            case "set" -> {
                if (args.length < 5) {
                    sender.sendMessage(ChatColor.RED + "Usage: /dungeon perk set <player> <perkId> <level>");
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
                if (!swordPerkService.setPerk(target, args[3], level)) {
                    sender.sendMessage(ChatColor.RED + "Unknown perk id.");
                    return true;
                }
                swordService.ensureSwordInSlot(target);
                sender.sendMessage(ChatColor.GREEN + "Perk updated for " + target.getName() + ".");
                return true;
            }
            case "addpoints" -> {
                if (args.length < 4) {
                    sender.sendMessage(ChatColor.RED + "Usage: /dungeon perk addpoints <player> <amount>");
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[2]);
                if (target == null) {
                    sender.sendMessage(ChatColor.RED + "Player not found.");
                    return true;
                }
                int amount;
                try {
                    amount = Integer.parseInt(args[3]);
                } catch (Exception exception) {
                    sender.sendMessage(ChatColor.RED + "Invalid amount.");
                    return true;
                }
                swordPerkService.addPerkPoints(target, amount);
                sender.sendMessage(ChatColor.GREEN + "Added perk points to " + target.getName() + ".");
                return true;
            }
            default -> {
                sender.sendMessage(ChatColor.RED + "Unknown perk subcommand.");
                return true;
            }
        }
    }

    private boolean handleEconomyAdmin(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage(ChatColor.YELLOW + "/dungeon economy get <player> <currency|all>");
            sender.sendMessage(ChatColor.YELLOW + "/dungeon economy set <player> <currency|all> <amount>");
            sender.sendMessage(ChatColor.YELLOW + "/dungeon economy add <player> <currency|all> <amount>");
            sender.sendMessage(ChatColor.YELLOW + "/dungeon economy take <player> <currency|all> <amount>");
            return true;
        }

        String action = args[1].toLowerCase();
        Player target = Bukkit.getPlayerExact(args[2]);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "Player not found.");
            return true;
        }

        PlayerProfile profile = dungeonService.profile(target);
        boolean allCurrencies = args[3].equalsIgnoreCase("all");
        CurrencyType type = allCurrencies ? null : CurrencyType.fromInput(args[3]);
        if (!allCurrencies && type == null) {
            sender.sendMessage(ChatColor.RED + "Unknown currency. Available: money, souls, essence, shards, all");
            return true;
        }

        if (action.equals("get")) {
            if (allCurrencies) {
                for (CurrencyType currencyType : CurrencyType.values()) {
                    sender.sendMessage(ChatColor.YELLOW + target.getName() + " " + currencyType.displayName() + ": "
                            + ChatColor.GREEN + NumberFormat.compact(profile.balance(currencyType)));
                }
            } else {
                sender.sendMessage(ChatColor.YELLOW + target.getName() + " " + type.displayName() + ": "
                        + ChatColor.GREEN + NumberFormat.compact(profile.balance(type)));
            }
            return true;
        }

        if (!action.equals("set") && !action.equals("add") && !action.equals("take")) {
            sender.sendMessage(ChatColor.RED + "Unknown economy subcommand.");
            return true;
        }

        if (args.length < 5) {
            sender.sendMessage(ChatColor.RED + "Usage: /dungeon economy " + action + " <player> <currency|all> <amount>");
            return true;
        }

        BigInteger amount = NumberFormat.parse(args[4], BigInteger.valueOf(-1));
        if (amount.compareTo(BigInteger.ZERO) < 0) {
            sender.sendMessage(ChatColor.RED + "Invalid amount.");
            return true;
        }

        if (!allCurrencies) {
            if (!applyEconomyAction(profile, type, action, amount)) {
                sender.sendMessage(ChatColor.RED + "Player does not have enough " + type.displayName() + ".");
                return true;
            }
            sender.sendMessage(ChatColor.GREEN + "Updated " + target.getName() + "'s " + type.displayName() + " to "
                    + NumberFormat.compact(profile.balance(type)) + ".");
            return true;
        }

        if (action.equals("take")) {
            for (CurrencyType currencyType : CurrencyType.values()) {
                if (profile.balance(currencyType).compareTo(amount) < 0) {
                    sender.sendMessage(ChatColor.RED + "Player does not have enough " + currencyType.displayName() + " for action 'all'.");
                    return true;
                }
            }
        }

        for (CurrencyType currencyType : CurrencyType.values()) {
            applyEconomyAction(profile, currencyType, action, amount);
        }

        sender.sendMessage(ChatColor.GREEN + "Updated all currencies for " + target.getName() + " by "
                + NumberFormat.compact(amount) + ".");
        return true;
    }

    private boolean applyEconomyAction(PlayerProfile profile, CurrencyType type, String action, BigInteger amount) {
        return switch (action) {
            case "set" -> {
                BigInteger current = profile.balance(type);
                if (current.compareTo(amount) < 0) {
                    profile.add(type, amount.subtract(current));
                } else if (current.compareTo(amount) > 0) {
                    profile.remove(type, current.subtract(amount));
                }
                yield true;
            }
            case "add" -> {
                profile.add(type, amount);
                yield true;
            }
            case "take" -> profile.remove(type, amount);
            default -> false;
        };
    }

    private boolean handleCompanion(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.YELLOW + "/dungeon companion setlocation <zoneId>");
            sender.sendMessage(ChatColor.YELLOW + "/dungeon companion egg <zoneId> [stage] [player]");
            sender.sendMessage(ChatColor.YELLOW + "/dungeon companion ui [player]");
            return true;
        }

        switch (args[1].toLowerCase()) {
            case "setlocation" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(ChatColor.RED + "Players only.");
                    return true;
                }
                if (!sender.hasPermission("minecraftdungeons.admin")) {
                    sender.sendMessage(ChatColor.RED + "No permission.");
                    return true;
                }
                if (args.length < 3) {
                    sender.sendMessage(ChatColor.RED + "Usage: /dungeon companion setlocation <zoneId>");
                    return true;
                }
                companionService.setEggLocation(args[2], 0, player.getLocation());
                sender.sendMessage(ChatColor.GREEN + "Companion egg location set for zone " + args[2] + " (stage auto).");
                return true;
            }
            case "egg" -> {
                if (!(sender instanceof Player self)) {
                    sender.sendMessage(ChatColor.RED + "Players only.");
                    return true;
                }
                if (args.length < 3) {
                    sender.sendMessage(ChatColor.RED + "Usage: /dungeon companion egg <zoneId> [stage] [player]");
                    return true;
                }

                String zoneId = args[2];
                Integer stage = null;
                Player target = self;
                int playerArgIndex = -1;

                if (args.length >= 4) {
                    try {
                        stage = Integer.parseInt(args[3]);
                        playerArgIndex = 4;
                    } catch (Exception ignored) {
                        playerArgIndex = 3;
                    }
                }

                if (playerArgIndex > 0 && args.length > playerArgIndex) {
                    if (!sender.hasPermission("minecraftdungeons.admin")) {
                        sender.sendMessage(ChatColor.RED + "No permission.");
                        return true;
                    }
                    target = Bukkit.getPlayerExact(args[playerArgIndex]);
                    if (target == null) {
                        sender.sendMessage(ChatColor.RED + "Player not found.");
                        return true;
                    }
                }

                if (stage != null) {
                    companionMenuService.openEggMenu(target, zoneId, stage);
                } else {
                    companionMenuService.openEggMenuAutoStage(target, zoneId);
                }
                return true;
            }
            case "ui" -> {
                Player target;
                if (args.length >= 3) {
                    if (!sender.hasPermission("minecraftdungeons.admin")) {
                        sender.sendMessage(ChatColor.RED + "No permission.");
                        return true;
                    }
                    target = Bukkit.getPlayerExact(args[2]);
                    if (target == null) {
                        sender.sendMessage(ChatColor.RED + "Player not found.");
                        return true;
                    }
                } else if (sender instanceof Player player) {
                    target = player;
                } else {
                    sender.sendMessage(ChatColor.RED + "Usage: /dungeon companion ui <player>");
                    return true;
                }
                companionMenuService.openCompanions(target);
                return true;
            }
            default -> {
                sender.sendMessage(ChatColor.RED + "Unknown companion subcommand.");
                return true;
            }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("reload", "start", "enchant", "perk", "economy", "companion");
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

        if (args.length == 2 && args[0].equalsIgnoreCase("perk")) {
            return List.of("reload", "list", "set", "addpoints");
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("economy")) {
            return List.of("get", "set", "add", "take");
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("companion")) {
            return List.of("setlocation", "egg", "ui");
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("enchant")
                && (args[1].equalsIgnoreCase("setlevel") || args[1].equalsIgnoreCase("addlevel") || args[1].equalsIgnoreCase("settoollevel") || args[1].equalsIgnoreCase("addtoolxp"))) {
            return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("perk")
                && (args[1].equalsIgnoreCase("set") || args[1].equalsIgnoreCase("addpoints"))) {
            return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("economy")) {
            return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
        }

        if (args.length == 4 && args[0].equalsIgnoreCase("enchant")
                && (args[1].equalsIgnoreCase("setlevel") || args[1].equalsIgnoreCase("addlevel"))) {
            return enchantService.enchantIds();
        }

        if (args.length == 4 && args[0].equalsIgnoreCase("perk") && args[1].equalsIgnoreCase("set")) {
            return swordPerkService.availablePerks().stream().map(SwordPerkService.PerkDefinition::id).toList();
        }

        if (args.length == 4 && args[0].equalsIgnoreCase("economy")) {
            List<String> suggestions = new ArrayList<>();
            for (CurrencyType type : CurrencyType.values()) {
                suggestions.add(type.key());
            }
            suggestions.add("all");
            return suggestions;
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("companion") && args[1].equalsIgnoreCase("setlocation")) {
            return dungeonService.zones().stream().map(ZoneDefinition::id).toList();
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("companion") && args[1].equalsIgnoreCase("ui")) {
            return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("companion") && args[1].equalsIgnoreCase("egg")) {
            return dungeonService.zones().stream().map(ZoneDefinition::id).toList();
        }

        if (args.length == 5 && args[0].equalsIgnoreCase("companion") && args[1].equalsIgnoreCase("egg")) {
            return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
        }

        return List.of();
    }
}
