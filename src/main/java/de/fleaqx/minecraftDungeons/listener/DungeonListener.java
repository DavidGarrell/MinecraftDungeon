package de.fleaqx.minecraftDungeons.listener;

import de.fleaqx.minecraftDungeons.companion.CompanionService;
import de.fleaqx.minecraftDungeons.companion.ui.CompanionMenuService;
import de.fleaqx.minecraftDungeons.runtime.AutoAttackService;
import de.fleaqx.minecraftDungeons.runtime.DungeonService;
import de.fleaqx.minecraftDungeons.runtime.VirtualHealthService;
import de.fleaqx.minecraftDungeons.rebirth.ui.RebirthMenuService;
import de.fleaqx.minecraftDungeons.sword.SwordService;
import de.fleaqx.minecraftDungeons.sword.ui.SwordMenuService;
import de.fleaqx.minecraftDungeons.ui.ZoneMenuService;
import org.bukkit.ChatColor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.Action;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.math.BigInteger;
import java.util.UUID;

public class DungeonListener implements Listener {

    private final DungeonService dungeonService;
    private final VirtualHealthService virtualHealthService;
    private final ZoneMenuService zoneMenuService;
    private final AutoAttackService autoAttackService;
    private final SwordService swordService;
    private final SwordMenuService swordMenuService;
    private final CompanionService companionService;
    private final CompanionMenuService companionMenuService;
    private final RebirthMenuService rebirthMenuService;

    public DungeonListener(DungeonService dungeonService,
                           VirtualHealthService virtualHealthService,
                           ZoneMenuService zoneMenuService,
                           AutoAttackService autoAttackService,
                           SwordService swordService,
                           SwordMenuService swordMenuService,
                           CompanionService companionService,
                           CompanionMenuService companionMenuService,
                           RebirthMenuService rebirthMenuService) {
        this.dungeonService = dungeonService;
        this.virtualHealthService = virtualHealthService;
        this.zoneMenuService = zoneMenuService;
        this.autoAttackService = autoAttackService;
        this.swordService = swordService;
        this.swordMenuService = swordMenuService;
        this.companionService = companionService;
        this.companionMenuService = companionMenuService;
        this.rebirthMenuService = rebirthMenuService;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        swordService.ensureSwordInSlot(event.getPlayer());
        companionService.ensureControllerInSlot(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDamageManagedEntity(EntityDamageByEntityEvent event) {
        Entity target = event.getEntity();
        if (!(target instanceof LivingEntity living) || !dungeonService.isManagedMob(target.getUniqueId())) {
            return;
        }

        Player damager = extractDamager(event.getDamager());
        if (damager == null) {
            event.setCancelled(true);
            return;
        }

        UUID owner = dungeonService.mobOwner(target.getUniqueId());
        if (owner == null || !owner.equals(damager.getUniqueId())) {
            event.setCancelled(true);
            return;
        }

        event.setCancelled(true);
        BigInteger damage = swordService.currentDamage(damager).max(BigInteger.ONE);
        autoAttackService.lockOrToggleTarget(damager, living, damage);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onOtherDamage(EntityDamageEvent event) {
        if (!dungeonService.isManagedMob(event.getEntity().getUniqueId())) {
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler
    public void onDeath(EntityDeathEvent event) {
        if (!dungeonService.isManagedMob(event.getEntity().getUniqueId())) {
            return;
        }
        event.getDrops().clear();
        event.setDroppedExp(0);
        virtualHealthService.remove(event.getEntity().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        autoAttackService.stop(playerId);
        dungeonService.removePlayer(playerId);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (swordMenuService.handleClick(event)) {
            return;
        }
        if (companionMenuService.handleClick(event)) {
            return;
        }
        if (rebirthMenuService.handleClick(event)) {
            return;
        }
        zoneMenuService.handleClick(event);
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        swordMenuService.handleClose(event);
        companionMenuService.handleClose(event);
        rebirthMenuService.handleClose(event);
        zoneMenuService.handleClose(event);
    }

    @EventHandler
    public void onRightClickSword(PlayerInteractEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }

        if (event.getHand() == null || event.getHand().name().contains("OFF_HAND")) {
            return;
        }

        Action action = event.getAction();
        boolean rightClick = action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK;

        if (rightClick && event.getClickedBlock() != null) {
            companionService.eggPointAtBlock(event.getClickedBlock()).ifPresent(point -> {
                event.setCancelled(true);
                companionMenuService.openEggMenu(player, point.zoneId(), point.stage());
            });
            if (event.isCancelled()) {
                return;
            }
        }

        if (rightClick && player.getInventory().getHeldItemSlot() == 0 && swordService.isManagedSword(player.getInventory().getItemInMainHand())) {
            event.setCancelled(true);
            swordMenuService.openMain(player);
            return;
        }

        if (rightClick && companionService.isController(player.getInventory().getItemInMainHand())) {
            event.setCancelled(true);
            companionMenuService.openCompanions(player);
            return;
        }

        if (rightClick) {
            companionService.nearbyEgg(player, 3.5D).ifPresent(point -> {
                event.setCancelled(true);
                companionMenuService.openEggMenu(player, point.zoneId(), point.stage());
            });
        }
    }

    @EventHandler
    public void onInteractAfk(PlayerInteractAtEntityEvent event) {
        Player player = event.getPlayer();

        java.util.Optional<CompanionService.EggPoint> eggPoint = companionService.eggPointByEntity(event.getRightClicked());
        if (eggPoint.isPresent()) {
            event.setCancelled(true);
            companionMenuService.openEggMenu(player, eggPoint.get().zoneId(), eggPoint.get().stage());
            return;
        }

        if (dungeonService.isAfkMobFor(player, event.getRightClicked().getUniqueId())) {
            event.setCancelled(true);
            swordMenuService.openMain(player);
        }
    }

    @EventHandler
    public void onDropSword(PlayerDropItemEvent event) {
        if (swordService.isManagedSword(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
            SwordService.BuyBestResult result = swordService.buyBest(event.getPlayer());
            if (result.upgrades() > 0) {
                String swordName = swordService.definition(result.swordId()).name();
                event.getPlayer().sendMessage(ChatColor.GREEN + "Bought best sword upgrades: " + result.upgrades() + " (" + swordName + " " + roman(result.tier()) + ")");
            } else {
                event.getPlayer().sendMessage(ChatColor.RED + "No affordable sword upgrade.");
            }
        }

        if (companionService.isController(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
        }
    }

    private String roman(int value) {
        return switch (value) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            default -> "V";
        };
    }

    private Player extractDamager(Entity entity) {
        if (entity instanceof Player player) {
            return player;
        }
        if (entity instanceof Projectile projectile && projectile.getShooter() instanceof Player player) {
            return player;
        }
        return null;
    }
}
