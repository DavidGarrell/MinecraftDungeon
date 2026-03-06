package de.fleaqx.minecraftDungeons.command;

import de.fleaqx.minecraftDungeons.currency.CurrencyType;
import de.fleaqx.minecraftDungeons.currency.NumberFormat;
import de.fleaqx.minecraftDungeons.runtime.DungeonService;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.math.BigInteger;
import java.util.List;

public class PayCommand implements CommandExecutor, TabCompleter {

    private final DungeonService dungeonService;

    public PayCommand(DungeonService dungeonService) {
        this.dungeonService = dungeonService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Players only.");
            return true;
        }

        if (args.length < 3) {
            player.sendMessage(ChatColor.YELLOW + "/pay <player> <souls|essence|shards> <amount>");
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            player.sendMessage(ChatColor.RED + "Player not found.");
            return true;
        }

        CurrencyType type;
        try {
            type = CurrencyType.valueOf(args[1].toUpperCase());
        } catch (Exception exception) {
            player.sendMessage(ChatColor.RED + "Invalid currency.");
            return true;
        }

        if (type == CurrencyType.MONEY) {
            player.sendMessage(ChatColor.RED + "Money cannot be paid with this command.");
            return true;
        }

        BigInteger amount = NumberFormat.parse(args[2], BigInteger.ZERO);
        if (amount.compareTo(BigInteger.ZERO) <= 0) {
            player.sendMessage(ChatColor.RED + "Amount must be greater than 0.");
            return true;
        }

        if (!dungeonService.removeCurrency(player, type, amount)) {
            player.sendMessage(ChatColor.RED + "Not enough " + type.name().toLowerCase() + ".");
            return true;
        }

        dungeonService.addCurrency(target, type, amount);
        player.sendMessage(ChatColor.GREEN + "Sent " + NumberFormat.compact(amount) + " " + type.name().toLowerCase() + " to " + target.getName() + ".");
        target.sendMessage(ChatColor.GREEN + "Received " + NumberFormat.compact(amount) + " " + type.name().toLowerCase() + " from " + player.getName() + ".");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
        }
        if (args.length == 2) {
            return List.of("souls", "essence", "shards");
        }
        return List.of();
    }
}
