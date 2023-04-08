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
import net.minecraft.util.DamageSource;
import net.minecraft.util.EntityDamageSource;
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

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

@Mod(modid = SkillsFirearms.MODID, useMetadata = true, guiFactory = "committee.nova.skillsfirearms.client.gui.GuiFactory",
        dependencies = "required-after:skillful@[0.0.3.6,)", acceptableRemoteVersions = "*")
public class SkillsFirearms {
    public static final String MODID = "skillsfirearms";
    private static Logger LOGGER;
    private static final String DISPERSION = "dispersionSet";
    private static final Map<Class<?>, Function<Entity, Entity>> strategiesBullet = new HashMap<>();

    private static final String CGM = "com.mrcrayfish.guns.entity.EntityProjectile";
    private static final String VMW = "com.vicmatskiv.weaponlib.EntityProjectile";
    private static final String MW = "com.modularwarfare.common.entity.EntityBullet";
    private static final String PUBG = "dev.toma.pubgmc.common.entity.EntityBullet";
    private static final String GVC = "gvclib.entity.EntityBBase";
    private static final String TECHGUN_ENTITY = "techguns.entities.projectiles.GenericProjectile";
    private static final String MO = "matteroverdrive.entity.weapon.PlasmaBolt";
    private static final String AOA3 = "net.tslat.aoa3.entity.projectiles.gun.BaseBullet";
    private static final String ALGANE_ORB = "xyz.phanta.algane.entity.EntityShockOrb";
    private static final String MATCHLOCK_GUN = "com.korallkarlsson.matchlockweapons.entities.Bullet";
    private static final String PVZ_PEA = "com.hungteen.pvzmod.entities.bullets.EntityPea";
    private static final String L2M_BULLET = "net.thecallunxz.left2mine.entities.projectiles.EntityProjectile";

    private static final String TECHGUN_SRC = "techguns.damagesystem.TGDamageSource";
    private static final String ALGANE_SRC = "xyz.phanta.algane.lasergun.damage.DamageHitscan";
    private static final String MW_FIXED_SRC = "committee.nova.mwdmgsrcfix.DamageSourceModular";
    private static final String PVZ_SRC = "com.hungteen.pvzmod.damage.PVZDamageSource";
    private static final String L2M_SRC = "net.thecallunxz.left2mine.entities.projectiles.DamageSourceShot";

    private static final Set<Class<?>> SUPPORTED_BULLET = new HashSet<>();
    private static final Set<Class<?>> SUPPORTED_SRC = new HashSet<>();

    private static final ResourceLocation FA_OLD = new ResourceLocation("skillscgm", "firearm");
    public static final ISkill FIREARM = new Skill(new ResourceLocation(MODID, "firearm"), 100, BossInfo.Color.BLUE, (int i) -> i * 200);

    @EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        LOGGER = event.getModLog();
        MinecraftForge.EVENT_BUS.register(this);
    }

    @EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        if (CompatConfig.cgm) {
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
            } catch (ClassNotFoundException | NoSuchMethodException ignored) {
                LOGGER.error("Failed to register cgm compatibility...");
            }
        }
        if (CompatConfig.vmw) {
            LOGGER.info("Try registering VMW compatibility");
            try {
                final Class<?> vmw = Class.forName(VMW);
                SUPPORTED_BULLET.add(vmw);
                final Method p = vmw.getDeclaredMethod("getThrower");
                p.setAccessible(true);
                strategiesBullet.put(vmw, e -> {
                    try {
                        return (Entity) p.invoke(e);
                    } catch (IllegalAccessException | InvocationTargetException ex) {
                        ex.printStackTrace();
                    }
                    return null;
                });
                LOGGER.info("Successfully registered VMW compatibility!");
            } catch (ClassNotFoundException | NoSuchMethodException ignored) {
                LOGGER.error("Failed to register VMW compatibility...");
            }
        }
        if (CompatConfig.modularWarfare) {
            LOGGER.info("Try registering ModularWarfare compatibility");
            try {
                final Class<?> mw = Class.forName(MW);
                SUPPORTED_BULLET.add(mw);
                try {
                    final Class<?> mwFixedSrc = Class.forName(MW_FIXED_SRC);
                    SUPPORTED_SRC.add(mwFixedSrc);
                } catch (ClassNotFoundException ignored) {
                }
                final Field f = mw.getSuperclass().getDeclaredField("field_70250_c");
                f.setAccessible(true);
                strategiesBullet.put(mw, e -> {
                    try {
                        return (Entity) f.get(e);
                    } catch (IllegalAccessException ex) {
                        ex.printStackTrace();
                    }
                    return null;
                });
                LOGGER.info("Successfully registered ModularWarfare compatibility!");
            } catch (ClassNotFoundException | NoSuchFieldException ignored) {
                LOGGER.error("Failed to register ModularWarfare compatibility...");
            }
        }
        if (CompatConfig.pubg) {
            LOGGER.info("Try registering PUBGMC compatibility");
            try {
                final Class<?> pubg = Class.forName(PUBG);
                SUPPORTED_BULLET.add(pubg);
                final Method p = pubg.getDeclaredMethod("getShooter");
                p.setAccessible(true);
                strategiesBullet.put(pubg, e -> {
                    try {
                        return (Entity) p.invoke(e);
                    } catch (IllegalAccessException | InvocationTargetException ex) {
                        ex.printStackTrace();
                    }
                    return null;
                });
                LOGGER.info("Successfully registered PUBGMC compatibility!");
            } catch (ClassNotFoundException | NoSuchMethodException ignored) {
                LOGGER.error("Failed to register PUBGMC compatibility...");
            }
        }
        if (CompatConfig.gvc) {
            LOGGER.info("Try registering compatibility for GVCLib dependents");
            try {
                final Class<?> gvc = Class.forName(GVC);
                SUPPORTED_BULLET.add(gvc);
                final Method p = gvc.getDeclaredMethod("getThrower");
                p.setAccessible(true);
                strategiesBullet.put(gvc, e -> {
                    try {
                        return (Entity) p.invoke(e);
                    } catch (IllegalAccessException | InvocationTargetException ex) {
                        ex.printStackTrace();
                    }
                    return null;
                });
                LOGGER.info("Successfully registered compatibility for GVCLib dependents!");
            } catch (ClassNotFoundException | NoSuchMethodException ignored) {
                LOGGER.error("Failed to register compatibility for GVCLib dependents...");
            }
        }
        if (CompatConfig.techgun) {
            LOGGER.info("Try registering compatibility for TechGun");
            try {
                final Class<?> techgunSrc = Class.forName(TECHGUN_SRC);
                SUPPORTED_SRC.add(techgunSrc);
                final Class<?> techgunEntity = Class.forName(TECHGUN_ENTITY);
                SUPPORTED_BULLET.add(techgunEntity);
                final Field f = techgunEntity.getDeclaredField("shooter");
                f.setAccessible(true);
                strategiesBullet.put(techgunEntity, e -> {
                    try {
                        return (Entity) f.get(e);
                    } catch (IllegalAccessException ex) {
                        ex.printStackTrace();
                    }
                    return null;
                });
                LOGGER.info("Successfully registered compatibility for TechGun!");
            } catch (NoSuchFieldException | ClassNotFoundException ignored) {
                LOGGER.error("Failed to register compatibility for TechGun...");
            }
        }
        if (CompatConfig.matterOverdrive) {
            LOGGER.info("Try registering compatibility for MatterOverdrive");
            try {
                final Class<?> mo = Class.forName(MO);
                SUPPORTED_BULLET.add(mo);
                final Field f = mo.getDeclaredField("shootingEntity");
                f.setAccessible(true);
                strategiesBullet.put(mo, e -> {
                    try {
                        return (Entity) f.get(e);
                    } catch (IllegalAccessException ex) {
                        ex.printStackTrace();
                    }
                    return null;
                });
                LOGGER.info("Successfully registered compatibility for MatterOverdrive!");
            } catch (NoSuchFieldException | ClassNotFoundException ignored) {
                LOGGER.error("Failed to register compatibility for MatterOverdrive...");
            }
        }
        if (CompatConfig.aoa3) {
            LOGGER.info("Try registering compatibility for AOA3");
            try {
                final Class<?> aoa3 = Class.forName(AOA3);
                SUPPORTED_BULLET.add(aoa3);
                final Field f = aoa3.getSuperclass().getDeclaredField("func_85052_h");
                f.setAccessible(true);
                strategiesBullet.put(aoa3, e -> {
                    try {
                        return (Entity) f.get(e);
                    } catch (IllegalAccessException ex) {
                        ex.printStackTrace();
                    }
                    return null;
                });
                LOGGER.info("Successfully registered compatibility for AOA3!");
            } catch (ClassNotFoundException | NoSuchFieldException ignored) {
                LOGGER.error("Failed to register compatibility for AOA3...");
            }
        }
        if (CompatConfig.algane) {
            LOGGER.info("Try registering compatibility for ALGANE");
            try {
                final Class<?> alganeSrc = Class.forName(ALGANE_SRC);
                SUPPORTED_SRC.add(alganeSrc);
                final Class<?> alganeOrb = Class.forName(ALGANE_ORB);
                SUPPORTED_BULLET.add(alganeOrb);
                final Field f = alganeOrb.getSuperclass().getDeclaredField("field_70235_a");
                f.setAccessible(true);
                strategiesBullet.put(alganeOrb, e -> {
                    try {
                        return (Entity) f.get(e);
                    } catch (IllegalAccessException ex) {
                        ex.printStackTrace();
                    }
                    return null;
                });
                LOGGER.info("Successfully registered compatibility for ALGANE!");
            } catch (ClassNotFoundException | NoSuchFieldException ignored) {
                LOGGER.error("Failed to register compatibility for ALGANE...");
            }
        }
        if (CompatConfig.matchlockGuns) {
            LOGGER.info("Try registering compatibility for Matchlock Guns");
            try {
                final Class<?> matchlock = Class.forName(MATCHLOCK_GUN);
                SUPPORTED_BULLET.add(matchlock);
                final Field f = matchlock.getDeclaredField("user");
                f.setAccessible(true);
                strategiesBullet.put(matchlock, e -> {
                    try {
                        return (Entity) f.get(e);
                    } catch (IllegalAccessException ex) {
                        ex.printStackTrace();
                    }
                    return null;
                });
                LOGGER.info("Successfully registered compatibility for Matchlock Guns!");
            } catch (NoSuchFieldException | ClassNotFoundException ignored) {
                LOGGER.error("Failed to register compatibility for Matchlock Guns...");
            }
        }
        if (CompatConfig.pvz) {
            LOGGER.info("Try registering compatibility for HungTeen's Plants vs Zombies Mod");
            try {
                final Class<?> pvzSrc = Class.forName(PVZ_SRC);
                SUPPORTED_SRC.add(pvzSrc);
                final Class<?> pvzPea = Class.forName(PVZ_PEA);
                SUPPORTED_BULLET.add(pvzPea);
                final Method m = pvzPea.getDeclaredMethod("getThrower");
                strategiesBullet.put(pvzPea, e -> {
                    try {
                        return (Entity) m.invoke(e);
                    } catch (IllegalAccessException | InvocationTargetException ex) {
                        ex.printStackTrace();
                    }
                    return null;
                });
                LOGGER.info("Successfully registered compatibility for HungTeen's Plants vs Zombies Mod!");
            } catch (ClassNotFoundException | NoSuchMethodException ignored) {
                LOGGER.error("Failed to register compatibility for HungTeen's Plants vs Zombies Mod...");
            }
        }
        if (CompatConfig.l2m) {
            LOGGER.info("Try registering compatibility for Left 2 Mine");
            try {
                final Class<?> l2mSrc = Class.forName(L2M_SRC);
                SUPPORTED_SRC.add(l2mSrc);
                final Class<?> l2mBullet = Class.forName(L2M_BULLET);
                SUPPORTED_BULLET.add(l2mBullet);
                final Field f = l2mBullet.getDeclaredField("shooter");
                strategiesBullet.put(l2mBullet, e -> {
                    try {
                        return (Entity) f.get(e);
                    } catch (IllegalAccessException ex) {
                        ex.printStackTrace();
                    }
                    return null;
                });
                LOGGER.info("Successfully registered compatibility for Left 2 Mine!");
            } catch (NoSuchFieldException | ClassNotFoundException ignored) {
                LOGGER.error("Failed to register compatibility for Left 2 Mine...");
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
        firearm.changeLevel(player, oldLevel);
        firearm.addXp(player, Math.max(0, old.getCurrentXp()));
        Utilities.removePlayerSkill(player, FA_OLD);
    }

    @SubscribeEvent
    public void onSkillRegister(SkillRegisterEvent event) {
        event.addSkill(FIREARM);
    }

    @SubscribeEvent
    public void onDamageModifier(LivingHurtEvent event) {
        EntityPlayerMP player = null;
        final DamageSource src = event.getSource();
        if ((src instanceof EntityDamageSourceIndirect)) {
            final EntityDamageSourceIndirect s = (EntityDamageSourceIndirect) event.getSource();
            final Entity e = s.getTrueSource();
            if (!(e instanceof EntityPlayerMP)) return;
            if (!checkBulletNotSupported(s.getImmediateSource())) player = (EntityPlayerMP) s.getTrueSource();
        } else if (src instanceof EntityDamageSource) {
            final EntityDamageSource s = (EntityDamageSource) src;
            if (checkSrcIsSupported(s)) player = (EntityPlayerMP) s.getTrueSource();
        }
        if (player == null) return;
        final SkillInstance firearm = Utilities.getPlayerSkillStat(player, FIREARM);
        event.setAmount(event.getAmount() * (1.0F + Math.max(.0F, (firearm.getCurrentLevel() - 10.0F) / 50.0F)));
        final EntityLivingBase target = event.getEntityLiving();
        firearm.addXp(player, Math.max(1, (int) (event.getAmount() * 1.08 / target.width / target.height * target.getAIMoveSpeed() * (target instanceof EntityMob ? 1.0 : 0.25))));
    }

    @SubscribeEvent
    public void onKill(LivingDeathEvent event) {
        EntityPlayerMP player = null;
        final DamageSource src = event.getSource();
        if ((src instanceof EntityDamageSourceIndirect)) {
            final EntityDamageSourceIndirect s = (EntityDamageSourceIndirect) event.getSource();
            final Entity e = s.getTrueSource();
            if (!(e instanceof EntityPlayerMP)) return;
            if (!checkBulletNotSupported(s.getImmediateSource())) player = (EntityPlayerMP) s.getTrueSource();
        } else if (src instanceof EntityDamageSource) {
            final EntityDamageSource s = (EntityDamageSource) src;
            if (checkSrcIsSupported(s)) player = (EntityPlayerMP) s.getTrueSource();
        }
        if (player == null) return;
        Utilities.getPlayerSkillStat(player, FIREARM).addXp(player, 5 + (int) (event.getEntityLiving().getMaxHealth() / 20.0));
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

    private static boolean checkBulletNotSupported(Entity direct) {
        if (direct == null) return true;
        for (final Class<?> clazz : SUPPORTED_BULLET) if (clazz.isAssignableFrom(direct.getClass())) return false;
        return true;
    }

    private static boolean checkSrcIsSupported(DamageSource src) {
        if (src == null) return false;
        for (final Class<?> clazz : SUPPORTED_SRC) if (clazz.isAssignableFrom(src.getClass())) return true;
        return false;
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

        @Config.Comment("Enable compat for Modular Warfare Mod")
        @Config.RequiresMcRestart
        public static boolean modularWarfare = true;

        @Config.Comment("Enable compat for PUBGMC")
        @Config.RequiresMcRestart
        public static boolean pubg = true;

        @Config.Comment("Enable compat for GVCLib dependents")
        @Config.RequiresMcRestart
        public static boolean gvc = true;

        @Config.Comment("Enable compat for TechGun")
        @Config.RequiresMcRestart
        public static boolean techgun = true;

        @Config.Comment("Enable compat for MatterOverdrive")
        @Config.RequiresMcRestart
        public static boolean matterOverdrive = true;

        @Config.Comment("Enable compat for AOA3")
        @Config.RequiresMcRestart
        public static boolean aoa3 = true;

        @Config.Comment("Enable compat for ALGANE")
        @Config.RequiresMcRestart
        public static boolean algane = true;

        @Config.Comment("Enable compat for Matchlock Guns")
        @Config.RequiresMcRestart
        public static boolean matchlockGuns = true;

        @Config.Comment("Enable compat for HungTeen's Plants vs Zombies Mod")
        @Config.RequiresMcRestart
        public static boolean pvz = true;

        @Config.Comment("Enable compat for Left 2 Mine")
        @Config.RequiresMcRestart
        public static boolean l2m = true;
    }
}
