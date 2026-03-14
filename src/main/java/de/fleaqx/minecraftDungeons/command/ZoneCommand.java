package de.fleaqx.minecraftDungeons.command;

import de.fleaqx.minecraftDungeons.config.ZoneConfigService;
import de.fleaqx.minecraftDungeons.currency.CurrencyBundle;
import de.fleaqx.minecraftDungeons.currency.CurrencyType;
import de.fleaqx.minecraftDungeons.currency.NumberFormat;
import de.fleaqx.minecraftDungeons.model.MobEntry;
import de.fleaqx.minecraftDungeons.model.MobRarity;
import de.fleaqx.minecraftDungeons.model.ZoneDefinition;
import de.fleaqx.minecraftDungeons.runtime.DungeonService;
import de.fleaqx.minecraftDungeons.ui.ZoneMenuService;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ZoneCommand implements CommandExecutor, TabCompleter {

    private final DungeonService dungeonService;
    private final ZoneMenuService menuService;
    private final ZoneConfigService configService;

    public ZoneCommand(DungeonService dungeonService, ZoneMenuService menuService, ZoneConfigService configService) {
        this.dungeonService = dungeonService;
        this.menuService = menuService;
        this.configService = configService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(ChatColor.RED + "Players only.");
                return true;
            }
            menuService.openZoneMenu(player);
            return true;
        }

        if (args[0].equalsIgnoreCase("max")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(ChatColor.RED + "Players only.");
                return true;
            }
            int bought = dungeonService.maxUpgradeStages(player);
            sender.sendMessage(bought <= 0
                    ? ChatColor.RED + "No purchasable stage progression found."
                    : ChatColor.GREEN + "Max upgrade unlocked " + bought + " progression step(s).");
            return true;
        }

        if (args[0].equalsIgnoreCase("balance")) {
            if (!(sender instanceof Player player)) {
                return true;
            }
            sender.sendMessage(ChatColor.GOLD + "Money: " + NumberFormat.compact(dungeonService.balance(player, CurrencyType.MONEY)));
            sender.sendMessage(ChatColor.GOLD + "Souls: " + NumberFormat.compact(dungeonService.balance(player, CurrencyType.SOULS)));
            sender.sendMessage(ChatColor.GOLD + "Essence: " + NumberFormat.compact(dungeonService.balance(player, CurrencyType.ESSENCE)));
            sender.sendMessage(ChatColor.GOLD + "Shards: " + NumberFormat.compact(dungeonService.balance(player, CurrencyType.SHARDS)));
            return true;
        }

        if (!args[0].equalsIgnoreCase("admin")) {
            sender.sendMessage(ChatColor.YELLOW + "/zone");
            sender.sendMessage(ChatColor.YELLOW + "/zone max");
            sender.sendMessage(ChatColor.YELLOW + "/zone balance");
            return true;
        }

        if (!sender.hasPermission("minecraftdungeons.admin")) {
            sender.sendMessage(ChatColor.RED + "No permission.");
            return true;
        }

        return handleAdmin(sender, args);
    }

    private boolean handleAdmin(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.YELLOW + "/zone admin create <id> <display> <order> <unlockPrice>");
            sender.sendMessage(ChatColor.YELLOW + "/zone admin setarea <zone> <pos1|pos2>");
            sender.sendMessage(ChatColor.YELLOW + "/zone admin setspawn <zone>");
            sender.sendMessage(ChatColor.YELLOW + "/zone admin setafkloc");
            sender.sendMessage(ChatColor.YELLOW + "/zone admin addmob <zone> <stage> <mobId> <entity> <rarity> <weight> <health> <money> <souls> <essence> <shards>");
            sender.sendMessage(ChatColor.YELLOW + "/zone admin removemob <zone> <stage> <mobId>");
            sender.sendMessage(ChatColor.YELLOW + "/zone admin setstageprice <zone> <stage> <price>");
            sender.sendMessage(ChatColor.YELLOW + "/zone admin setmeta <zone> <order|unlock-price|display-name|mobs-per-stage> <value>");
            sender.sendMessage(ChatColor.YELLOW + "/zone admin currency <give|set|take> <player> <money|souls|essence|shards> <amount>");
            sender.sendMessage(ChatColor.YELLOW + "/zone admin reload");
            return true;
        }

        switch (args[1].toLowerCase()) {
            case "reload" -> {
                dungeonService.reload();
                sender.sendMessage(ChatColor.GREEN + "Zones reloaded.");
                return true;
            }
            case "create" -> {
                if (args.length < 6) {
                    return true;
                }
                String id = args[2];
                String display = args[3];
                int order = Integer.parseInt(args[4]);
                BigInteger price = NumberFormat.parse(args[5], BigInteger.ZERO);
                boolean ok = configService.createZone(id, display, order, price);
                sender.sendMessage(ok ? ChatColor.GREEN + "Zone created." : ChatColor.RED + "Zone exists or failed.");
                dungeonService.reload();
                return true;
            }
            case "delete" -> {
                if (args.length < 3) {
                    return true;
                }
                boolean ok = configService.deleteZone(args[2]);
                sender.sendMessage(ok ? ChatColor.GREEN + "Zone deleted." : ChatColor.RED + "Zone not found.");
                dungeonService.reload();
                return true;
            }
            case "setarea" -> {
                if (!(sender instanceof Player player) || args.length < 4) {
                    return true;
                }
                int pos = args[3].equalsIgnoreCase("pos1") ? 1 : 2;
                boolean ok = configService.setZoneAreaPos(args[2], pos, player.getLocation());
                sender.sendMessage(ok ? ChatColor.GREEN + "Area position saved." : ChatColor.RED + "Zone not found.");
                dungeonService.reload();
                return true;
            }
            case "setspawn" -> {
                if (!(sender instanceof Player player) || args.length < 3) {
                    return true;
                }
                boolean ok = configService.setSpawn(args[2], player.getLocation());
                sender.sendMessage(ok ? ChatColor.GREEN + "Zone spawn saved." : ChatColor.RED + "Zone not found.");
                dungeonService.reload();
                return true;
            }
            case "setafkloc" -> {
                if (!(sender instanceof Player player)) {
                    return true;
                }
                boolean ok = configService.setAfkLocation(player.getLocation());
                sender.sendMessage(ok ? ChatColor.GREEN + "AFK mob location saved." : ChatColor.RED + "Failed to save AFK location.");
                dungeonService.reload();
                return true;
            }
            case "setstageprice" -> {
                if (args.length < 5) {
                    return true;
                }
                int stage = Integer.parseInt(args[3]);
                BigInteger price = NumberFormat.parse(args[4], BigInteger.ZERO);
                boolean ok = configService.setStagePrice(args[2], stage, price);
                sender.sendMessage(ok ? ChatColor.GREEN + "Stage price saved." : ChatColor.RED + "Failed to save stage price.");
                dungeonService.reload();
                return true;
            }
            case "setmeta" -> {
                if (args.length < 5) {
                    return true;
                }
                String zoneId = args[2];
                String key = args[3].toLowerCase();
                String value = args[4];
                Object parsed;
                switch (key) {
                    case "order", "mobs-per-stage" -> parsed = Integer.parseInt(value);
                    case "unlock-price" -> parsed = NumberFormat.parse(value, BigInteger.ZERO).toString();
                    case "display-name" -> parsed = value;
                    default -> {
                        sender.sendMessage(ChatColor.RED + "Unknown key.");
                        return true;
                    }
                }
                boolean ok = configService.setZoneMeta(zoneId, key, parsed);
                sender.sendMessage(ok ? ChatColor.GREEN + "Zone meta updated." : ChatColor.RED + "Zone not found.");
                dungeonService.reload();
                return true;
            }
            case "addmob" -> {
                if (args.length < 13) {
                    sender.sendMessage(ChatColor.RED + "Missing arguments.");
                    return true;
                }

                String zone = args[2];
                int stage = Integer.parseInt(args[3]);
                String mobId = args[4];
                EntityType type = EntityType.valueOf(args[5].toUpperCase());
                MobRarity rarity = MobRarity.valueOf(args[6].toUpperCase());
                int weight = Integer.parseInt(args[7]);
                BigInteger health = NumberFormat.parse(args[8], BigInteger.valueOf(100));
                BigInteger money = NumberFormat.parse(args[9], BigInteger.ZERO);
                BigInteger souls = NumberFormat.parse(args[10], BigInteger.ZERO);
                BigInteger essence = NumberFormat.parse(args[11], BigInteger.ZERO);
                BigInteger shards = NumberFormat.parse(args[12], BigInteger.ZERO);

                MobEntry mob = new MobEntry(mobId, type, rarity, weight, 1.0D, health, CurrencyBundle.of(money, souls, essence, shards));
                boolean ok = configService.addMob(zone, stage, mob);
                sender.sendMessage(ok ? ChatColor.GREEN + "Mob saved." : ChatColor.RED + "Zone or stage not found.");
                dungeonService.reload();
                return true;
            }
            case "removemob" -> {
                if (args.length < 5) {
                    return true;
                }
                boolean ok = configService.removeMob(args[2], Integer.parseInt(args[3]), args[4]);
                sender.sendMessage(ok ? ChatColor.GREEN + "Mob removed." : ChatColor.RED + "Failed to remove mob.");
                dungeonService.reload();
                return true;
            }
            case "currency" -> {
                if (args.length < 6) {
                    return true;
                }
                String action = args[2].toLowerCase();
                Player target = Bukkit.getPlayerExact(args[3]);
                if (target == null) {
                    sender.sendMessage(ChatColor.RED + "Player not found.");
                    return true;
                }
                CurrencyType type;
                try {
                    type = CurrencyType.valueOf(args[4].toUpperCase());
                } catch (Exception exception) {
                    sender.sendMessage(ChatColor.RED + "Invalid currency.");
                    return true;
                }
                BigInteger amount = NumberFormat.parse(args[5], BigInteger.ZERO).max(BigInteger.ZERO);

                switch (action) {
                    case "give" -> dungeonService.addCurrency(target, type, amount);
                    case "take" -> dungeonService.removeCurrency(target, type, amount);
                    case "set" -> {
                        BigInteger current = dungeonService.balance(target, type);
                        if (current.compareTo(amount) > 0) {
                            dungeonService.removeCurrency(target, type, current.subtract(amount));
                        } else {
                            dungeonService.addCurrency(target, type, amount.subtract(current));
                        }
                    }
                    default -> {
                        sender.sendMessage(ChatColor.RED + "Action must be give, set or take.");
                        return true;
                    }
                }

                sender.sendMessage(ChatColor.GREEN + "Updated " + type.name().toLowerCase() + " for " + target.getName() + ".");
                return true;
            }
            default -> {
                sender.sendMessage(ChatColor.RED + "Unknown admin command.");
                return true;
            }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("max", "balance", "admin");
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("admin")) {
            return List.of("reload", "create", "delete", "setarea", "setspawn", "setafkloc", "setstageprice", "setmeta", "addmob", "removemob", "currency");
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("admin") && args[1].equalsIgnoreCase("currency")) {
            return List.of("give", "set", "take");
        }

        if (args.length == 4 && args[0].equalsIgnoreCase("admin") && args[1].equalsIgnoreCase("currency")) {
            return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
        }

        if (args.length == 5 && args[0].equalsIgnoreCase("admin") && args[1].equalsIgnoreCase("currency")) {
            return List.of("money", "souls", "essence", "shards");
        }

        if (args.length >= 3 && args[0].equalsIgnoreCase("admin")
                && !args[1].equalsIgnoreCase("create")
                && !args[1].equalsIgnoreCase("currency")
                && !args[1].equalsIgnoreCase("setafkloc")) {
            List<String> ids = new ArrayList<>();
            for (ZoneDefinition zone : dungeonService.sortedZones()) {
                ids.add(zone.id());
            }
            return ids;
        }

        if (args.length == 6 && args[1].equalsIgnoreCase("addmob")) {
            return Arrays.stream(EntityType.values()).map(Enum::name).limit(40).toList();
        }

        if (args.length == 7 && args[1].equalsIgnoreCase("addmob")) {
            return Arrays.stream(MobRarity.values()).map(Enum::name).toList();
        }

        return List.of();
    }
}
