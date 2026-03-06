package de.fleaqx.minecraftDungeons;

import de.fleaqx.minecraftDungeons.command.DungeonCommand;
import de.fleaqx.minecraftDungeons.command.PayCommand;
import de.fleaqx.minecraftDungeons.command.ZoneCommand;
import de.fleaqx.minecraftDungeons.config.ZoneConfigService;
import de.fleaqx.minecraftDungeons.enchant.EnchantConfigService;
import de.fleaqx.minecraftDungeons.enchant.EnchantService;
import de.fleaqx.minecraftDungeons.listener.DungeonListener;
import de.fleaqx.minecraftDungeons.profile.ProfileService;
import de.fleaqx.minecraftDungeons.runtime.AutoAttackService;
import de.fleaqx.minecraftDungeons.runtime.CombatBossBarService;
import de.fleaqx.minecraftDungeons.runtime.DamageIndicatorService;
import de.fleaqx.minecraftDungeons.runtime.DungeonService;
import de.fleaqx.minecraftDungeons.runtime.VirtualHealthService;
import de.fleaqx.minecraftDungeons.sword.SwordService;
import de.fleaqx.minecraftDungeons.sword.ui.SwordMenuService;
import de.fleaqx.minecraftDungeons.ui.ZoneMenuService;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class MinecraftDungeons extends JavaPlugin {

    private ZoneConfigService zoneConfigService;
    private EnchantConfigService enchantConfigService;
    private ProfileService profileService;
    private VirtualHealthService virtualHealthService;
    private DungeonService dungeonService;
    private ZoneMenuService zoneMenuService;
    private AutoAttackService autoAttackService;
    private DamageIndicatorService damageIndicatorService;
    private SwordService swordService;
    private SwordMenuService swordMenuService;
    private EnchantService enchantService;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        this.zoneConfigService = new ZoneConfigService(this);
        this.zoneConfigService.init();

        this.enchantConfigService = new EnchantConfigService(this);
        this.enchantConfigService.init();

        this.profileService = new ProfileService(this);
        this.profileService.init();

        this.virtualHealthService = new VirtualHealthService();
        this.damageIndicatorService = new DamageIndicatorService(this);
        this.dungeonService = new DungeonService(this, zoneConfigService, profileService, virtualHealthService, damageIndicatorService);
        this.zoneMenuService = new ZoneMenuService(dungeonService);

        this.enchantService = new EnchantService(this, enchantConfigService, profileService);
        this.enchantService.reload();
        dungeonService.setEnchantService(enchantService);

        this.swordService = new SwordService(this, profileService, enchantService);
        this.swordMenuService = new SwordMenuService(swordService, enchantService);

        this.autoAttackService = new AutoAttackService(
                this,
                dungeonService,
                damageIndicatorService,
                new CombatBossBarService(),
                enchantService
        );

        dungeonService.start();
        autoAttackService.start();
        getServer().getPluginManager().registerEvents(
                new DungeonListener(dungeonService, virtualHealthService, zoneMenuService, autoAttackService, swordService, swordMenuService),
                this
        );

        PluginCommand zone = getCommand("zone");
        if (zone != null) {
            ZoneCommand zoneCommand = new ZoneCommand(dungeonService, zoneMenuService, zoneConfigService);
            zone.setExecutor(zoneCommand);
            zone.setTabCompleter(zoneCommand);
        }

        PluginCommand dungeon = getCommand("dungeon");
        if (dungeon != null) {
            DungeonCommand dungeonCommand = new DungeonCommand(dungeonService, enchantService);
            dungeon.setExecutor(dungeonCommand);
            dungeon.setTabCompleter(dungeonCommand);
        }

        PluginCommand pay = getCommand("pay");
        if (pay != null) {
            PayCommand payCommand = new PayCommand(dungeonService);
            pay.setExecutor(payCommand);
            pay.setTabCompleter(payCommand);
        }
    }

    @Override
    public void onDisable() {
        if (autoAttackService != null) {
            autoAttackService.shutdown();
        }
        if (dungeonService != null) {
            dungeonService.shutdown();
        }
    }
}
