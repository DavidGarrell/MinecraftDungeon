package de.fleaqx.minecraftDungeons.runtime;

import de.fleaqx.minecraftDungeons.enchant.EnchantService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.math.BigInteger;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class AutoAttackService {

    private static final int RETARGET_GRACE_TICKS = 40;

    private final JavaPlugin plugin;
    private final DungeonService dungeonService;
    private final DamageIndicatorService damageIndicatorService;
    private final CombatBossBarService combatBossBarService;
    private final EnchantService enchantService;

    private final Map<UUID, AutoAttackState> states = new ConcurrentHashMap<>();
    private final Map<UUID, Long> clickDebounce = new ConcurrentHashMap<>();
    private BukkitTask task;

    public AutoAttackService(JavaPlugin plugin,
                             DungeonService dungeonService,
                             DamageIndicatorService damageIndicatorService,
                             CombatBossBarService combatBossBarService,
                             EnchantService enchantService) {
        this.plugin = plugin;
        this.dungeonService = dungeonService;
        this.damageIndicatorService = damageIndicatorService;
        this.combatBossBarService = combatBossBarService;
        this.enchantService = enchantService;
    }

    public void start() {
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    public void shutdown() {
        if (task != null) {
            task.cancel();
        }
        states.clear();
        clickDebounce.clear();
        combatBossBarService.shutdown();
    }

    public void lockOrToggleTarget(Player player, LivingEntity target, BigInteger baseDamage) {
        if (!dungeonService.canPlayerAttackMob(player, target.getUniqueId())) {
            return;
        }

        long now = System.nanoTime();
        long last = clickDebounce.getOrDefault(player.getUniqueId(), 0L);
        if (now - last < 120_000_000L) {
            return;
        }
        clickDebounce.put(player.getUniqueId(), now);

        AutoAttackState existing = states.get(player.getUniqueId());
        if (existing != null && existing.targetId().equals(target.getUniqueId())) {
            stop(player.getUniqueId());
            player.sendMessage("Auto attack paused.");
            return;
        }

        BigInteger damage = baseDamage.max(BigInteger.ONE);
        AutoAttackState state = new AutoAttackState(player.getUniqueId(), target.getUniqueId(), damage, now);
        states.put(player.getUniqueId(), state);
        player.sendMessage("Auto attack started.");
    }

    public void stop(UUID playerId) {
        states.remove(playerId);
        Player player = Bukkit.getPlayer(playerId);
        if (player != null) {
            combatBossBarService.hide(player);
        }
    }

    private void tick() {
        long now = System.nanoTime();
        for (AutoAttackState state : states.values()) {
            Player player = Bukkit.getPlayer(state.playerId());
            if (player == null || !player.isOnline()) {
                stop(state.playerId());
                continue;
            }

            Entity entity = Bukkit.getEntity(state.targetId());
            LivingEntity target = null;
            if (entity instanceof LivingEntity living && living.isValid() && dungeonService.canPlayerAttackMob(player, living.getUniqueId())) {
                target = living;
                state.missingTicks(0);
            } else {
                target = findNextTarget(player, state.targetId());
                if (target == null) {
                    state.missingTicks(state.missingTicks() + 1);
                    if (state.missingTicks() >= RETARGET_GRACE_TICKS) {
                        stop(state.playerId());
                    }
                    continue;
                }
                state.targetId(target.getUniqueId());
                state.missingTicks(0);
            }

            if (!sameWorld(player, target) || player.getLocation().distanceSquared(target.getLocation()) > 25.0D) {
                stop(state.playerId());
                player.sendMessage("Auto attack stopped: too far away.");
                continue;
            }

            VirtualHealthService.VirtualHealth health = dungeonService.health(target.getUniqueId());
            if (health != null) {
                combatBossBarService.update(player, health);
            }

            double hitsPerSecond = dungeonService.hitsPerSecondFor(player, target.getUniqueId());
            long interval = (long) (1_000_000_000D / Math.max(0.1D, hitsPerSecond));
            if (now >= state.nextAttackNano()) {
                strike(player, target, state);
                state.nextAttackNano(now + interval);
            }
        }
    }

    private void strike(Player player, LivingEntity target, AutoAttackState state) {
        EnchantService.EnchantHitResult hit = enchantService.computeHit(player, target, state.damage());
        DungeonService.AttackResult result = dungeonService.onPlayerDamageMob(player, target, hit.totalDamage());
        if (!result.accepted()) {
            stop(player.getUniqueId());
            return;
        }

        damageIndicatorService.spawnDamage(player, target, hit.totalDamage());
        enchantService.applyPostHit(player, target, state.damage(), hit, dungeonService, damageIndicatorService);
        enchantService.grantHitXp(player);

        if (result.dead()) {
            state.missingTicks(0);
        }
    }

    private LivingEntity findNextTarget(Player player, UUID previousTargetId) {
        for (LivingEntity mob : dungeonService.activeOwnedMobs(player)) {
            if (mob == null || !mob.isValid()) {
                continue;
            }
            if (previousTargetId != null && previousTargetId.equals(mob.getUniqueId())) {
                continue;
            }
            if (dungeonService.canPlayerAttackMob(player, mob.getUniqueId()) && !dungeonService.isAfkMobFor(player, mob.getUniqueId())) {
                return mob;
            }
        }

        for (LivingEntity mob : dungeonService.activeOwnedMobs(player)) {
            if (mob != null && mob.isValid() && dungeonService.canPlayerAttackMob(player, mob.getUniqueId())) {
                return mob;
            }
        }
        return null;
    }

    private boolean sameWorld(Player player, LivingEntity entity) {
        return player.getWorld().getUID().equals(entity.getWorld().getUID());
    }

    private static final class AutoAttackState {
        private final UUID playerId;
        private UUID targetId;
        private final BigInteger damage;
        private long nextAttackNano;
        private int missingTicks;

        private AutoAttackState(UUID playerId, UUID targetId, BigInteger damage, long nextAttackNano) {
            this.playerId = playerId;
            this.targetId = targetId;
            this.damage = damage;
            this.nextAttackNano = nextAttackNano;
        }

        public UUID playerId() {
            return playerId;
        }

        public UUID targetId() {
            return targetId;
        }

        public void targetId(UUID targetId) {
            this.targetId = targetId;
        }

        public BigInteger damage() {
            return damage;
        }

        public long nextAttackNano() {
            return nextAttackNano;
        }

        public void nextAttackNano(long nextAttackNano) {
            this.nextAttackNano = nextAttackNano;
        }

        public int missingTicks() {
            return missingTicks;
        }

        public void missingTicks(int missingTicks) {
            this.missingTicks = missingTicks;
        }
    }
}
