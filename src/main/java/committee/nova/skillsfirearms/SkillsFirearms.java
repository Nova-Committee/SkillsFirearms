package committee.nova.skillsfirearms;

import committee.nova.skillful.api.skill.ISkill;
import committee.nova.skillful.impl.skill.Skill;
import committee.nova.skillful.impl.skill.instance.SkillInstance;
import committee.nova.skillful.storage.SkillfulStorage.SkillRegisterEvent;
import committee.nova.skillful.util.Utilities;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.EntityMob;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EntityDamageSourceIndirect;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.BossInfo;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.config.Config;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import org.apache.logging.log4j.Logger;
import scala.Option;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.function.Function;

@Mod(modid = SkillsFirearms.MODID, useMetadata = true, guiFactory = "committee.nova.skillsfirearms.GuiFactory",
        dependencies = "required-after:skillful@[0.0.3.5,)")
public class SkillsFirearms {
    public static final String MODID = "skillsfirearms";
    private static Logger LOGGER;
    private static final String DISPERSION = "dispersionSet";
    private static final Map<String, Function<Entity, Entity>> strategies = new HashMap<>();

    private static final String CGM = "com.mrcrayfish.guns.entity.EntityProjectile";
    private static final String MW = "com.vicmatskiv.weaponlib.EntityProjectile";
    private static final String PUBG = "dev.toma.pubgmc.common.entity.EntityBullet";

    private static final Set<Class<?>> SUPPORTED = new HashSet<>();
    private static Function<EntityDamageSourceIndirect, EntityPlayerMP> PUBG_COMPAT = e -> null;

    private static final ResourceLocation FA_OLD = new ResourceLocation("skillscgm", "firearm");
    public static final ISkill FIREARM = new Skill(new ResourceLocation(MODID, "firearm"), 100, BossInfo.Color.BLUE, (int i) -> i * 200);

    @EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        LOGGER = event.getModLog();
        MinecraftForge.EVENT_BUS.register(this);
    }

    @EventHandler
    @SuppressWarnings("unchecked")
    public void postInit(FMLPostInitializationEvent event) {
        if (CompatConfig.cgm) {
            LOGGER.info("Try registering cgm compatibility");
            try {
                final Class<?> cgm = Class.forName(CGM);
                final Method p = cgm.getDeclaredMethod("getShooter");
                p.setAccessible(true);
                strategies.put(CGM, e -> {
                    try {
                        return (Entity) p.invoke(e);
                    } catch (IllegalAccessException | InvocationTargetException ex) {
                        ex.printStackTrace();
                    }
                    return null;
                });
                SUPPORTED.add(cgm);
                LOGGER.info("Successfully registered cgm compatibility!");
            } catch (ClassNotFoundException | NoSuchMethodException ignored) {
                LOGGER.warn("Failed to register cgm compatibility...");
            }
        }
        if (CompatConfig.vmw) {
            LOGGER.info("Try registering VMW compatibility");
            try {
                final Class<?> mw = Class.forName(MW);
                final Method p = mw.getDeclaredMethod("getThrower");
                p.setAccessible(true);
                strategies.put(MW, e -> {
                    try {
                        return (Entity) p.invoke(e);
                    } catch (IllegalAccessException | InvocationTargetException ex) {
                        ex.printStackTrace();
                    }
                    return null;
                });
                SUPPORTED.add(mw);
                LOGGER.info("Successfully registered VMW compatibility!");
            } catch (ClassNotFoundException | NoSuchMethodException ignored) {
                LOGGER.error("Failed to register VMW compatibility...");
            }
        }
        if (CompatConfig.pubg) {
            LOGGER.info("Try registering PUBGMC compatibility");
            try {
                final Class<?> pubg = Class.forName(PUBG);
                //final Method p = pubg.getDeclaredMethod("getShooter");
                //p.setAccessible(true);
                //strategies.put(PUBG, e -> {
                //    try {
                //        return (Entity) p.invoke(e);
                //    } catch (IllegalAccessException | InvocationTargetException ex) {
                //        ex.printStackTrace();
                //    }
                //    return null;
                //});
                SUPPORTED.add(pubg);
                // Toma's code seemed to have something wrong
                final Class<? extends EntityDamageSourceIndirect> GUN = (Class<? extends EntityDamageSourceIndirect>)
                        Class.forName("dev.toma.pubgmc.init.DamageSourceGun");
                PUBG_COMPAT = d -> {
                    if (GUN.isAssignableFrom(d.getClass())) try {
                        final Entity e = GUN.cast(d).getTrueSource();
                        if (!(e instanceof EntityPlayerMP)) return null;
                        return (EntityPlayerMP) e;
                    } catch (ClassCastException e) {
                        return null;
                    }
                    return null;
                };
                LOGGER.info("Successfully registered PUBGMC compatibility!");
                //} catch (ClassNotFoundException | NoSuchMethodException ignored) {
                //    LOGGER.error("Failed to register PUBGMC compatibility...");
                //}
            } catch (ClassNotFoundException ignored) {
                LOGGER.error("Failed to register PUBGMC compatibility...");
            }
        }
    }

    @SubscribeEvent
    public void onLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.player instanceof EntityPlayerMP)) return;
        final EntityPlayerMP player = (EntityPlayerMP) event.player;
        final Option<SkillInstance> oldOpt = Utilities.getPlayerSkillStatCleanly(player, FA_OLD);
        if (oldOpt.isEmpty()) return;
        final SkillInstance firearm = Utilities.getPlayerSkillStat(player, FIREARM);
        final SkillInstance old = oldOpt.get();
        final int oldLevel = old.getCurrentLevel();
        if (!(firearm.getCurrentLevel() < oldLevel || (firearm.getCurrentLevel() == oldLevel && firearm.getCurrentXp() < old.getCurrentXp())))
            return;
        while (firearm.getCurrentLevel() < oldLevel)
            firearm.addXp(player, firearm.skill().getLevelRequiredXp(firearm.getCurrentLevel()));
        firearm.addXp(player, Math.max(0, old.getCurrentXp() - firearm.getCurrentXp()));
        Utilities.removePlayerSkill(player, FA_OLD);
    }

    @SubscribeEvent
    public void onSkillRegister(SkillRegisterEvent event) {
        event.addSkill(FIREARM);
    }

    @SubscribeEvent
    public void onDamageModifier(LivingHurtEvent event) {
        if (!(event.getSource() instanceof EntityDamageSourceIndirect)) return;
        final EntityDamageSourceIndirect s = (EntityDamageSourceIndirect) event.getSource();
        if (!(s.getTrueSource() instanceof EntityPlayerMP)) return;
        final EntityPlayerMP player = checkNotSupported(s.getImmediateSource()) ? PUBG_COMPAT.apply(s) : (EntityPlayerMP) s.getTrueSource();
        if (player == null) return;
        final SkillInstance firearm = Utilities.getPlayerSkillStat(player, FIREARM);
        event.setAmount(event.getAmount() * (1.0F + Math.max(.0F, (firearm.getCurrentLevel() - 10.0F) / 50.0F)));
        final EntityLivingBase target = event.getEntityLiving();
        firearm.addXp(player, Math.max(1, (int) (event.getAmount() * 1.08 / target.width / target.height * target.getAIMoveSpeed() * (target instanceof EntityMob ? 1.0 : 0.25))));
    }

    @SubscribeEvent
    public void onKill(LivingDeathEvent event) {
        if (!(event.getSource() instanceof EntityDamageSourceIndirect)) return;
        final EntityDamageSourceIndirect s = (EntityDamageSourceIndirect) event.getSource();
        if (!(s.getTrueSource() instanceof EntityPlayerMP)) return;
        final EntityPlayerMP player = checkNotSupported(s.getImmediateSource()) ? PUBG_COMPAT.apply(s) : (EntityPlayerMP) s.getTrueSource();
        if (player == null) return;
        Utilities.getPlayerSkillStat(player, FIREARM).addXp(player, 5 + (int) (event.getEntityLiving().getMaxHealth() / 20.0));
    }

    @SubscribeEvent
    public void onProjectileSpawn(EntityJoinWorldEvent event) {
        final Entity bullet = event.getEntity();
        if (checkNotSupported(bullet)) return;
        final Function<Entity, Entity> fun = strategies.get(bullet.getClass().getName());
        if (fun == null) return;
        final Entity s = fun.apply(bullet);
        if (!(s instanceof EntityPlayerMP)) return;
        final EntityPlayerMP shooter = (EntityPlayerMP) s;
        final SkillInstance firearm = Utilities.getPlayerSkillStat(shooter, FIREARM);
        if (firearm.getCurrentLevel() > 20) return;
        final int dispersion = 21 - firearm.getCurrentLevel();
        final NBTTagCompound data = bullet.getEntityData();
        if (data.getBoolean(DISPERSION)) return;
        final Random rand = shooter.getRNG();
        if (rand.nextInt(Math.max(dispersion, 11)) < 5) return;
        bullet.motionX += dispersion * (rand.nextDouble() - 0.5) * 0.025;
        bullet.motionY += dispersion * (rand.nextDouble() - 0.5) * 0.025;
        bullet.motionZ += dispersion * (rand.nextDouble() - 0.5) * 0.025;
        data.setBoolean(DISPERSION, true);
    }

    private static boolean checkNotSupported(Entity direct) {
        if (direct == null) return true;
        for (final Class<?> clazz : SUPPORTED) if (clazz.isAssignableFrom(direct.getClass())) return false;
        return true;
    }

    @Config(modid = MODID)
    @Config.LangKey("cfg.skillsfirearms.compat")
    public static final class CompatConfig {
        @Config.Comment("Enable compat for MrCrayfish's Guns Mod")
        @Config.RequiresMcRestart
        public static boolean cgm = true;

        @Config.Comment("Enable compat for Vic's Modern Warfare Mod")
        @Config.RequiresMcRestart
        public static boolean vmw = true;

        @Config.Comment("Enable compat for PUBGMC")
        @Config.RequiresMcRestart
        public static boolean pubg = true;
    }
}
