package de.fleaqx.minecraftDungeons.command;

import de.fleaqx.minecraftDungeons.rebirth.ui.RebirthMenuService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class RebirthCommand implements CommandExecutor {

    private final RebirthMenuService rebirthMenuService;

    public RebirthCommand(RebirthMenuService rebirthMenuService) {
        this.rebirthMenuService = rebirthMenuService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }
        rebirthMenuService.openMain(player);
        return true;
    }
}
