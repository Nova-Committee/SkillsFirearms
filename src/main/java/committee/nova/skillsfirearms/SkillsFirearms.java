package committee.nova.skillsfirearms;

import com.mojang.logging.LogUtils;
import committee.nova.skillful.common.cap.skill.Skills;
import committee.nova.skillful.common.manager.SkillTypeManager;
import committee.nova.skillful.common.skill.ISkillType;
import committee.nova.skillful.common.skill.SkillInstance;
import committee.nova.skillful.common.skill.SkillType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import org.slf4j.Logger;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static committee.nova.skillsfirearms.SkillsFirearms.SFConfig.*;

@Mod(SkillsFirearms.MODID)
public class SkillsFirearms {
    public static final String MODID = "skillsfirearms";
    public static final Logger LOGGER = LogUtils.getLogger();
    public static final ISkillType FIREARM = SkillType.Builder
            .create(new ResourceLocation(MODID, "firearm"))
            .maxLevel(100)
            .levelRequiredXp((p, l) -> l * 200)
            .build();

    private static final String DISPERSION = "dispersion_ok";
    private static final Map<String, Class<?>> SUPPORTED_BULLET = new HashMap<>();
    private static final Map<String, Class<?>> SUPPORTED_ENTITY_SRC = new HashMap<>();
    private static final Map<Class<?>, Function<DamageSource, Entity>> SUPPORTED_GENERIC_SRC = new HashMap<>();
    private static final Map<Class<?>, Function<Entity, Entity>> strategiesBullet = new HashMap<>();
    private static final String CGM = "com.mrcrayfish.guns.entity.ProjectileEntity";
    private static final String IE = "blusunrize.immersiveengineering.common.entities.IEProjectileEntity";
    private static final String TACZ = "com.tacz.guns.entity.EntityKineticBullet";
    private static final String SCGUNS = "top.ribs.scguns.entity.projectile.ProjectileEntity";
    private static final String JEG = "ttv.migami.jeg.entity.projectile.ProjectileEntity";

    public SkillsFirearms() {
        MinecraftForge.EVENT_BUS.register(this);
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, SFConfig.CFG);
        SkillTypeManager.registerSkillType(FIREARM);
        if (cgm.get()) {
            LOGGER.info("Try registering cgm compatibility");
            try {
                final Class<?> cgmClz = Class.forName(CGM, false, this.getClass().getClassLoader());
                SUPPORTED_BULLET.put(CGM, cgmClz);
                final Method p = cgmClz.getDeclaredMethod("getShooter");
                p.setAccessible(true);
                strategiesBullet.put(cgmClz, e -> {
                    try {
                        return (Entity) p.invoke(e);
                    } catch (IllegalAccessException | InvocationTargetException ex) {
                        LOGGER.debug("Skills:Firearms failed to get the entity", ex);
                    }
                    return null;
                });
                LOGGER.info("Successfully registered cgm compatibility!");
            } catch (Exception e) {
                LOGGER.error("Failed to register cgm compatibility...");
                LOGGER.debug("Failure:", e);
            }
        }
        if (ie.get()) {
            LOGGER.info("Try registering ie compatibility");
            try {
                final Class<?> ieClz = Class.forName(IE, false, this.getClass().getClassLoader());
                SUPPORTED_BULLET.put(IE, ieClz);
                final Method p = ieClz.getDeclaredMethod("getOwner");
                p.setAccessible(true);
                strategiesBullet.put(ieClz, e -> {
                    try {
                        return (Entity) p.invoke(e);
                    } catch (IllegalAccessException | InvocationTargetException ex) {
                        LOGGER.debug("Skills:Firearms failed to get the entity", ex);
                    }
                    return null;
                });
                LOGGER.info("Successfully registered ie compatibility!");
            } catch (Exception e) {
                LOGGER.error("Failed to register ie compatibility...");
                LOGGER.debug("Failure:", e);
            }
        }
        if (tacz.get()) {
            LOGGER.info("Try registering tacz compatibility");
            try {
                final Class<?> taczClz = Class.forName(TACZ, false, this.getClass().getClassLoader());
                SUPPORTED_BULLET.put(TACZ, taczClz);
                final Method p = ObfuscationReflectionHelper.findMethod(taczClz, "getOwner");
                p.setAccessible(true);
                strategiesBullet.put(taczClz, e -> {
                    try {
                        return (Entity) p.invoke(e);
                    } catch (IllegalAccessException | InvocationTargetException ex) {
                        LOGGER.debug("Skills:Firearms failed to get the entity", ex);
                    }
                    return null;
                });
                LOGGER.info("Successfully registered tacz compatibility!");
            } catch (Exception e) {
                LOGGER.error("Failed to register tacz compatibility...");
                LOGGER.debug("Failure:", e);
            }
        }
        if (scguns.get()) {
            LOGGER.info("Try registering scguns compatibility");
            try {
                final Class<?> scgunsClz = Class.forName(SCGUNS, false, this.getClass().getClassLoader());
                SUPPORTED_BULLET.put(SCGUNS, scgunsClz);
                final Method p = scgunsClz.getDeclaredMethod("getShooter");
                p.setAccessible(true);
                strategiesBullet.put(scgunsClz, e -> {
                    try {
                        return (Entity) p.invoke(e);
                    } catch (IllegalAccessException | InvocationTargetException ex) {
                        LOGGER.debug("Skills:Firearms failed to get the entity", ex);
                    }
                    return null;
                });
                LOGGER.info("Successfully registered scguns compatibility!");
            } catch (Exception e) {
                LOGGER.error("Failed to register scguns compatibility...");
                LOGGER.debug("Failure:", e);
            }
        }
        if (jeg.get()) {
            LOGGER.info("Try registering jeg compatibility");
            try {
                final Class<?> jegClz = Class.forName(JEG, false, this.getClass().getClassLoader());
                SUPPORTED_BULLET.put(JEG, jegClz);
                final Method p = jegClz.getDeclaredMethod("getShooter");
                p.setAccessible(true);
                strategiesBullet.put(jegClz, e -> {
                    try {
                        return (Entity) p.invoke(e);
                    } catch (IllegalAccessException | InvocationTargetException ex) {
                        LOGGER.debug("Skills:Firearms failed to get the entity", ex);
                    }
                    return null;
                });
                LOGGER.info("Successfully registered jeg compatibility!");
            } catch (Exception e) {
                LOGGER.error("Failed to register jeg compatibility...");
                LOGGER.debug("Failure:", e);
            }
        }
    }

    @SubscribeEvent
    public void onDamageModifier(LivingHurtEvent event) {
        ServerPlayer player0 = null;
        final DamageSource src = event.getSource();
        if (src.getEntity() instanceof ServerPlayer p && !checkBulletNotSupported(src.getDirectEntity()))
            player0 = p;
        if (player0 == null) player0 = getGenericSrcShooter(src);
        if (player0 == null) return;
        final ServerPlayer player = player0;
        player.getCapability(Skills.SKILLS_CAPABILITY).ifPresent(s -> {
            final SkillInstance firearm = s.getOrCreateSkill(FIREARM);
            event.setAmount(event.getAmount() * (1.0F + Math.max(.0F, (firearm.getLevel() - 10.0F) / 50.0F) * damageMultiplier.get().floatValue()));
            final LivingEntity target = event.getEntity();
            firearm.changeXp(player, Math.max(1, (int) (event.getAmount() * 1.08 / target.getBbWidth() / target.getBbHeight()
                    * target.getSpeed() * (target instanceof Mob ? 1.0 : 0.25) * damageXpMultiplier.get())));
        });
    }

    @SubscribeEvent
    public void onKill(LivingDeathEvent event) {
        ServerPlayer player0 = null;
        final DamageSource src = event.getSource();
        if (src.getEntity() instanceof ServerPlayer p && !checkBulletNotSupported(src.getDirectEntity()))
            player0 = p;
        if (player0 == null) player0 = getGenericSrcShooter(src);
        if (player0 == null) return;
        final ServerPlayer player = player0;
        player.getCapability(Skills.SKILLS_CAPABILITY).ifPresent(s -> {
            final SkillInstance firearm = s.getOrCreateSkill(FIREARM);
            firearm.changeXp(player, (long) (killXpMultiplier.get() * (5 + event.getEntity().getMaxHealth() / 20.0)));
        });
    }

    @SubscribeEvent
    public void onProjectileSpawn(EntityJoinLevelEvent event) {
        final Entity bullet = event.getEntity();
        if (checkBulletNotSupported(bullet)) return;
        final AtomicReference<Function<Entity, Entity>> fun = new AtomicReference<>(e -> null);
        strategiesBullet.forEach((c, f) -> {
            if (c.isAssignableFrom(bullet.getClass())) fun.set(f);
        });
        final Entity s = fun.get().apply(bullet);
        if (s == null) return;
        if (!(s instanceof ServerPlayer shooter)) return;
        shooter.getCapability(Skills.SKILLS_CAPABILITY).ifPresent(skills -> {
            final SkillInstance firearm = skills.getOrCreateSkill(FIREARM);
            if (firearm.getLevel() > 20) return;
            final long dispersion = 21 - firearm.getLevel();
            final CompoundTag data = bullet.getPersistentData();
            if (data.getBoolean(DISPERSION)) return;
            final Random rand = ThreadLocalRandom.current();
            if (rand.nextLong(Math.max(dispersion, 11)) < 5) return;
            final double dispersionMp = dispersionMultiplier.get();
            bullet.setDeltaMovement(bullet.getDeltaMovement().add(dispersion * (rand.nextDouble() - 0.5) * dispersionMp,
                    dispersion * (rand.nextDouble() - 0.5) * dispersionMp, dispersion * (rand.nextDouble() - 0.5) * dispersionMp));
            data.putBoolean(DISPERSION, true);
        });
    }

    private static boolean checkBulletNotSupported(Entity direct) {
        if (direct == null) return true;
        final Class<?> directCls = direct.getClass();
        if (SUPPORTED_BULLET.containsKey(directCls.getName())) return false;
        for (final Class<?> clazz : SUPPORTED_BULLET.values()) if (clazz.isAssignableFrom(directCls)) return false;
        return true;
    }

    private static boolean checkEntityDmgSrcIsSupported(DamageSource src) {
        if (src == null) return false;
        final Class<?> srcCls = src.getClass();
        if (SUPPORTED_ENTITY_SRC.containsKey(srcCls.getName())) return true;
        for (final Class<?> clazz : SUPPORTED_ENTITY_SRC.values())
            if (clazz.isAssignableFrom(src.getClass())) return true;
        return false;
    }

    private static ServerPlayer getGenericSrcShooter(DamageSource src) {
        if (src == null) return null;
        for (final Map.Entry<Class<?>, Function<DamageSource, Entity>> entry : SUPPORTED_GENERIC_SRC.entrySet()) {
            if (entry.getKey().isAssignableFrom(src.getClass())) {
                final Entity e = entry.getValue().apply(src);
                if (e instanceof ServerPlayer) return (ServerPlayer) e;
            }
        }
        return null;
    }

    public static class SFConfig {
        public static final ForgeConfigSpec CFG;
        public static final ForgeConfigSpec.BooleanValue cgm;
        public static final ForgeConfigSpec.BooleanValue ie;
        public static final ForgeConfigSpec.BooleanValue tacz;
        public static final ForgeConfigSpec.BooleanValue scguns;
        public static final ForgeConfigSpec.BooleanValue jeg;
        public static final ForgeConfigSpec.DoubleValue dispersionMultiplier;
        public static final ForgeConfigSpec.DoubleValue damageMultiplier;
        public static final ForgeConfigSpec.DoubleValue damageXpMultiplier;
        public static final ForgeConfigSpec.DoubleValue killXpMultiplier;

        static {
            final ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
            builder.comment("Skills: Firearms Settings").push("Skill");
            dispersionMultiplier = builder.comment("Multiplier for dispersion")
                    .defineInRange("dispersionMultiplier", .025, .0, 5.0);
            damageMultiplier = builder.comment("Multiplier for damage bonus decided by skill level")
                    .defineInRange("damageMultiplier", 1.0, .0, 100.0);
            damageXpMultiplier = builder.comment("Multiplier for xp gain after damaging an entity with a firearm")
                    .defineInRange("damageXpMultiplier", 1.0, .0, 1000.0);
            killXpMultiplier = builder.comment("Multiplier for xp gain after killing an entity with a firearm")
                    .defineInRange("killXpMultiplier", 1.0, .0, 1000.0);
            builder.pop();
            builder.push("Compat");
            cgm = builder.comment("Enable compat for MrCrayfish's Gun Mod and its dependents")
                    .define("cgm", true);
            ie = builder.comment("Enable compat for Immersive Engineering")
                    .define("ie", true);
            tacz = builder.comment("Enable compat for Timeless and Classics: Zero")
                    .define("tacz", true);
            scguns = builder.comment("Enable compat for Scorched Guns 2")
                    .define("scguns", true);
            jeg = builder.comment("Enable compat for Just Enough Guns")
                    .define("jeg", true);
            builder.pop();
            CFG = builder.build();
        }
    }
}