package committee.nova.skillsfirearms;

import com.google.common.collect.ImmutableList;
import committee.nova.skillful.api.skill.ISkill;
import committee.nova.skillful.impl.skill.SkillBuilder;
import committee.nova.skillful.impl.skill.instance.SkillInstance;
import committee.nova.skillful.manager.SkillfulManager;
import committee.nova.skillful.util.Utilities;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.MobEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EntityDamageSource;
import net.minecraft.util.IndirectEntityDamageSource;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.BossInfo;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static committee.nova.skillsfirearms.SkillsFirearms.CommonConfig.*;

@Mod(SkillsFirearms.MODID)
public class SkillsFirearms {
    public static final String MODID = "skillsfirearms";
    private static final Logger LOGGER = LogManager.getLogger();
    public static final ISkill FIREARM = SkillBuilder
            .create(new ResourceLocation(MODID, "firearm"))
            .setColor(BossInfo.Color.BLUE)
            .setLevelRequiredXP$J(i -> i * 200)
            .setMaxLevel(100)
            .build();

    private static final String DISPERSION = "dispersionSet";
    private static final Set<Class<?>> SUPPORTED_BULLET = new HashSet<>();
    private static final Set<Class<?>> SUPPORTED_ENTITY_SRC = new HashSet<>();
    private static final Map<Class<?>, Function<DamageSource, Entity>> SUPPORTED_GENERIC_SRC = new HashMap<>();
    private static final Map<Class<?>, Function<Entity, Entity>> strategiesBullet = new HashMap<>();

    private static final String CGM = "com.mrcrayfish.guns.entity.ProjectileEntity";
    private static final String TAC = "com.tac.guns.entity.ProjectileEntity";
    private static final String GRAE = "com.song.nuclear_craft.entities.AbstractAmmoEntity";
    private static final List<String> DOOM = ImmutableList.of(
            "mod.azure.doom.entity.projectiles.BulletEntity", "mod.azure.doom.entity.projectiles.ChaingunBulletEntity",
            "mod.azure.doom.entity.projectiles.ShotgunShellEntity", "mod.azure.doom.entity.projectiles.EnergyCellEntity"
    );

    public SkillsFirearms() {
        MinecraftForge.EVENT_BUS.register(this);
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, COMMON_CONFIG);
        SkillfulManager.addSkill(FIREARM);
        if (cgm.get()) {
            LOGGER.info("Try registering cgm compatibility");
            try {
                final Class<?> cgm = Class.forName(CGM);
                SUPPORTED_BULLET.add(cgm);
                final Method p = cgm.getDeclaredMethod("getShooter");
                p.setAccessible(true);
                strategiesBullet.put(cgm, e -> {
                    try {
                        return (Entity) p.invoke(e);
                    } catch (IllegalAccessException | InvocationTargetException ex) {
                        ex.printStackTrace();
                    }
                    return null;
                });
                LOGGER.info("Successfully registered cgm compatibility!");
            } catch (Exception e) {
                LOGGER.error("Failed to register cgm compatibility...");
                LOGGER.debug("Failure:", e);
            }
        }
        if (tac.get()) {
            LOGGER.info("Try registering tac compatibility");
            try {
                final Class<?> tac = Class.forName(TAC);
                SUPPORTED_BULLET.add(tac);
                final Method p = tac.getDeclaredMethod("getShooter");
                p.setAccessible(true);
                strategiesBullet.put(tac, e -> {
                    try {
                        return (Entity) p.invoke(e);
                    } catch (IllegalAccessException | InvocationTargetException ex) {
                        ex.printStackTrace();
                    }
                    return null;
                });
                LOGGER.info("Successfully registered tac compatibility!");
            } catch (Exception e) {
                LOGGER.error("Failed to register tac compatibility...");
                LOGGER.debug("Failure:", e);
            }
        }
        if (grae.get()) {
            LOGGER.info("Try registering grae compatibility");
            try {
                final Class<?> grae = Class.forName(GRAE);
                SUPPORTED_BULLET.add(grae);
                final Method p = grae.getMethod("func_234616_v_");
                strategiesBullet.put(grae, e -> {
                    try {
                        return (Entity) p.invoke(e);
                    } catch (IllegalAccessException | InvocationTargetException ex) {
                        ex.printStackTrace();
                    }
                    return null;
                });
                LOGGER.info("Successfully registered grae compatibility!");
            } catch (Exception e) {
                LOGGER.error("Failed to register grae compatibility...");
                LOGGER.debug("Failure:", e);
            }
        }
        if (doom.get()) {
            LOGGER.info("Try registering doom compatibility");
            for (final String clzName : DOOM) {
                try {
                    final Class<?> doom = Class.forName(clzName);
                    SUPPORTED_BULLET.add(doom);
                    final Method p = doom.getMethod("func_234616_v_");
                    strategiesBullet.put(doom, e -> {
                        try {
                            return (Entity) p.invoke(e);
                        } catch (IllegalAccessException | InvocationTargetException ex) {
                            ex.printStackTrace();
                        }
                        return null;
                    });
                    LOGGER.info("Successfully registered {} for doom compatibility!", clzName);
                } catch (Exception e) {
                    LOGGER.error("Failed to register {} for doom compatibility...", clzName);
                    LOGGER.debug("Failure:", e);
                }
            }
        }
    }

    @SubscribeEvent
    public void onDamageModifier(LivingHurtEvent event) {
        ServerPlayerEntity player = null;
        final DamageSource src = event.getSource();
        if ((src instanceof IndirectEntityDamageSource)) {
            final IndirectEntityDamageSource s = (IndirectEntityDamageSource) event.getSource();
            final Entity e = s.getEntity();
            if (!(e instanceof ServerPlayerEntity)) return;
            if (!checkBulletNotSupported(s.getDirectEntity())) player = (ServerPlayerEntity) s.getEntity();
        } else if (src instanceof EntityDamageSource) {
            final EntityDamageSource s = (EntityDamageSource) src;
            if (checkEntityDmgSrcIsSupported(s)) player = (ServerPlayerEntity) s.getEntity();
        } else player = getGenericSrcShooter(src);
        if (player == null) return;
        final SkillInstance firearm = Utilities.getPlayerSkillStat(player, FIREARM);
        event.setAmount(event.getAmount() * (1.0F + Math.max(.0F, (firearm.getCurrentLevel() - 10.0F) / 50.0F) * damageMultiplier.get().floatValue()));
        final LivingEntity target = event.getEntityLiving();
        firearm.addXp(player, Math.max(1, (int) (event.getAmount() * 1.08 / target.getBbWidth() / target.getBbHeight()
                * target.getSpeed() * (target instanceof MobEntity ? 1.0 : 0.25) * damageXpMultiplier.get())));
    }

    @SubscribeEvent
    public void onKill(LivingDeathEvent event) {
        ServerPlayerEntity player = null;
        final DamageSource src = event.getSource();
        if ((src instanceof IndirectEntityDamageSource)) {
            final IndirectEntityDamageSource s = (IndirectEntityDamageSource) event.getSource();
            final Entity e = s.getEntity();
            if (!(e instanceof ServerPlayerEntity)) return;
            if (!checkBulletNotSupported(s.getDirectEntity())) player = (ServerPlayerEntity) s.getEntity();
        } else if (src instanceof EntityDamageSource) {
            final EntityDamageSource s = (EntityDamageSource) src;
            if (checkEntityDmgSrcIsSupported(s)) player = (ServerPlayerEntity) s.getEntity();
        } else player = getGenericSrcShooter(src);
        if (player == null) return;
        Utilities.getPlayerSkillStat(player, FIREARM).addXp(player, (int) (CommonConfig.killXpMultiplier.get() * (5 + event.getEntityLiving().getMaxHealth() / 20.0)));
    }

    @SubscribeEvent
    public void onProjectileSpawn(EntityJoinWorldEvent event) {
        final Entity bullet = event.getEntity();
        if (checkBulletNotSupported(bullet)) return;
        final AtomicReference<Function<Entity, Entity>> fun = new AtomicReference<>(e -> null);
        strategiesBullet.forEach((c, f) -> {
            if (c.isAssignableFrom(bullet.getClass())) fun.set(f);
        });
        final Entity s = fun.get().apply(bullet);
        if (s == null) return;
        if (!(s instanceof ServerPlayerEntity)) return;
        final ServerPlayerEntity shooter = (ServerPlayerEntity) s;
        final SkillInstance firearm = Utilities.getPlayerSkillStat(shooter, FIREARM);
        if (firearm.getCurrentLevel() > 20) return;
        final int dispersion = 21 - firearm.getCurrentLevel();
        final CompoundNBT data = bullet.getPersistentData();
        if (data.getBoolean(DISPERSION)) return;
        final Random rand = shooter.getRandom();
        if (rand.nextInt(Math.max(dispersion, 11)) < 5) return;
        final double dispersionMultiplier = CommonConfig.dispersionMultiplier.get();
        bullet.setDeltaMovement(bullet.getDeltaMovement().add(dispersion * (rand.nextDouble() - 0.5) * dispersionMultiplier,
                dispersion * (rand.nextDouble() - 0.5) * dispersionMultiplier, dispersion * (rand.nextDouble() - 0.5) * dispersionMultiplier));
        data.putBoolean(DISPERSION, true);
    }

    private static boolean checkBulletNotSupported(Entity direct) {
        if (direct == null) return true;
        for (final Class<?> clazz : SUPPORTED_BULLET) if (clazz.isAssignableFrom(direct.getClass())) return false;
        return true;
    }

    private static boolean checkEntityDmgSrcIsSupported(EntityDamageSource src) {
        if (src == null) return false;
        for (final Class<?> clazz : SUPPORTED_ENTITY_SRC) if (clazz.isAssignableFrom(src.getClass())) return true;
        return false;
    }

    private static ServerPlayerEntity getGenericSrcShooter(DamageSource src) {
        if (src == null) return null;
        for (final Map.Entry<Class<?>, Function<DamageSource, Entity>> entry : SUPPORTED_GENERIC_SRC.entrySet()) {
            if (entry.getKey().isAssignableFrom(src.getClass())) {
                final Entity e = entry.getValue().apply(src);
                if (e instanceof ServerPlayerEntity) return (ServerPlayerEntity) e;
            }
        }
        return null;
    }

    public static class CommonConfig {
        public static final ForgeConfigSpec COMMON_CONFIG;
        public static final ForgeConfigSpec.BooleanValue cgm;
        public static final ForgeConfigSpec.BooleanValue tac;
        public static final ForgeConfigSpec.BooleanValue grae;
        public static final ForgeConfigSpec.BooleanValue doom;
        public static final ForgeConfigSpec.DoubleValue dispersionMultiplier;
        public static final ForgeConfigSpec.DoubleValue damageMultiplier;
        public static final ForgeConfigSpec.DoubleValue damageXpMultiplier;
        public static final ForgeConfigSpec.DoubleValue killXpMultiplier;

        static {
            final ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
            builder.comment("Skills: Firearms General Settings").push("general");
            dispersionMultiplier = builder.comment("Multiplier for dispersion")
                    .defineInRange("dispersionMultiplier", .025, .0, 5.0);
            damageMultiplier = builder.comment("Multiplier for damage bonus decided by skill level")
                    .defineInRange("damageMultiplier", 1.0, .0, 100.0);
            damageXpMultiplier = builder.comment("Multiplier for xp gain after damaging an entity with a firearm")
                    .defineInRange("damageXpMultiplier", 1.0, .0, 1000.0);
            killXpMultiplier = builder.comment("Multiplier for xp gain after killing an entity with a firearm")
                    .defineInRange("killXpMultiplier", 1.0, .0, 1000.0);
            builder.pop();
            builder.comment("Skills: Firearms Compat Settings").push("compat");
            cgm = builder.comment("Enable compat for MrCrayfish's Gun Mod and its dependents").define("cgm", true);
            tac = builder.comment("Enable compat for Timeless & Classics").define("tac", true);
            grae = builder.comment("Enable compat for Guns, Rockets and Atomic Explosions").define("grae", true);
            doom = builder.comment("Enable compat for MCDoom").define("doom", true);
            builder.pop();
            COMMON_CONFIG = builder.build();
        }
    }
}
