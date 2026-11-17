package com.evolvedwarfare.difficulty;

import com.evolvedwarfare.EvolvedWarfare;
import com.evolvedwarfare.config.EwConfig;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.boss.WitherEntity;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.ElderGuardianEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.mob.WardenEntity;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * La dificultad que sube (fase 95), al estilo RLCraft: cada monstruo, al aparecer, recibe un <b>nivel de peligro</b> según
 * lo lejos que esté del punto de aparición del mundo, los días que lleva el mundo y la dimensión (el Nether y el End, más).
 * Cada nivel le da más vida y más daño (también a sus flechas y explosiones), y a partir del 5, armadura; y algo más de
 * velocidad. Y cuanto más nivel, más probable que salga <b>élite</b> ({@link Elites}).
 */
public final class Difficulty {
	public static final AttachmentType<Threat> THREAT = AttachmentRegistry.<Threat>builder()
			.persistent(Threat.CODEC)
			.buildAndRegister(EvolvedWarfare.id("threat"));

	private static final Identifier HEALTH_ID = EvolvedWarfare.id("threat_health");
	private static final Identifier DAMAGE_ID = EvolvedWarfare.id("threat_damage");
	private static final Identifier ARMOR_ID = EvolvedWarfare.id("threat_armor");
	private static final Identifier SPEED_ID = EvolvedWarfare.id("threat_speed");

	private Difficulty() {
	}

	/** En las pruebas automáticas no se toca nada solo (las pruebas lo llaman a mano). */
	static boolean automatic() {
		return System.getProperty("fabric-api.gametest") == null;
	}

	public static void register() {
		ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
			if (automatic() && entity instanceof MobEntity mob && !mob.hasAttached(THREAT) && affected(mob)) {
				onFirstLoad(mob, world);
			}
			if (entity instanceof MobEntity mob) {
				Elites.track(mob);
			}
		});
		Elites.register();
		SmartMobs.register();
	}

	/** ¿Le afecta? Los monstruos de Minecraft, sin nombre puesto y que no sean jefes. */
	public static boolean affected(MobEntity mob) {
		if (!(mob instanceof Monster) || mob instanceof WitherEntity || mob instanceof EnderDragonEntity || mob instanceof WardenEntity
				|| mob instanceof ElderGuardianEntity || mob.hasCustomName()) {
			return false;
		}
		return Registries.ENTITY_TYPE.getId(mob.getType()).getNamespace().equals("minecraft");
	}

	private static void onFirstLoad(MobEntity mob, ServerWorld world) {
		EwConfig c = EwConfig.get();
		int level = c.scalingEnabled ? level(world, mob.getBlockPos()) : 0;
		apply(mob, level);
		if (c.elitesEnabled) {
			float chance = Math.min(0.3F, (float) (c.eliteChance + c.eliteChancePerLevel * level));
			if (mob.getRandom().nextFloat() < chance) {
				Elites.make(mob, Elites.roll(mob, level));
			}
		}
	}

	/** El nivel de peligro de un sitio: distancia al punto de aparición, días del mundo y dimensión. */
	public static int level(World world, BlockPos pos) {
		EwConfig c = EwConfig.get();
		if (!c.scalingEnabled) {
			return 0;
		}
		BlockPos spawn = world.getServer() != null ? world.getServer().getOverworld().getSpawnPos() : world.getSpawnPos();
		double dx = pos.getX() - spawn.getX(), dz = pos.getZ() - spawn.getZ();
		double distance = Math.sqrt(dx * dx + dz * dz);
		long time = world.getServer() != null ? world.getServer().getOverworld().getTimeOfDay() : world.getTimeOfDay();
		long days = time / 24000L;
		int level = (int) (distance / Math.max(1, c.difficultyBlocksPerLevel)) + (int) (days / Math.max(1, c.difficultyDaysPerLevel));
		if (world.getRegistryKey() == World.NETHER) {
			level += 3;
		} else if (world.getRegistryKey() == World.END) {
			level += 5;
		} else if (world.getRegistryKey() != World.OVERWORLD) {
			level += 4;
		}
		return Math.max(0, Math.min(c.difficultyMaxLevel, level));
	}

	static void modifier(LivingEntity entity, RegistryEntry<EntityAttribute> attribute, Identifier id, double value,
			EntityAttributeModifier.Operation op) {
		EntityAttributeInstance inst = entity.getAttributeInstance(attribute);
		if (inst == null) {
			return;
		}
		inst.removeModifier(id);
		if (Math.abs(value) > 1e-6) {
			inst.addPersistentModifier(new EntityAttributeModifier(id, value, op));
		}
	}

	/** Le pone el nivel (más vida, daño, armadura y velocidad); se guarda con él y no se vuelve a poner. */
	public static void apply(MobEntity mob, int level) {
		EwConfig c = EwConfig.get();
		modifier(mob, EntityAttributes.GENERIC_MAX_HEALTH, HEALTH_ID, level * c.difficultyHealthPerLevel,
				EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		modifier(mob, EntityAttributes.GENERIC_ATTACK_DAMAGE, DAMAGE_ID, level * c.difficultyDamagePerLevel,
				EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		modifier(mob, EntityAttributes.GENERIC_ARMOR, ARMOR_ID, Math.max(0, level - 5) * 0.4, EntityAttributeModifier.Operation.ADD_VALUE);
		modifier(mob, EntityAttributes.GENERIC_MOVEMENT_SPEED, SPEED_ID, Math.min(0.1, level * 0.005),
				EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		mob.setHealth(mob.getMaxHealth());
		Threat before = mob.getAttached(THREAT);
		mob.setAttached(THREAT, new Threat(level, before == null ? java.util.List.of() : before.affixes()));
	}

	@Nullable
	public static Threat threat(@Nullable Entity entity) {
		return entity == null ? null : entity.getAttached(THREAT);
	}

	/** Las flechas y explosiones de un monstruo con nivel hacen más daño (el cuerpo a cuerpo ya va con el atributo). */
	public static float modifyOutgoing(DamageSource source, float amount) {
		Threat t = threat(source.getAttacker());
		if (t == null || !(source.isIn(DamageTypeTags.IS_PROJECTILE) || source.isIn(DamageTypeTags.IS_EXPLOSION))) {
			return amount;
		}
		float mult = 1.0F + (float) (t.level() * EwConfig.get().difficultyDamagePerLevel);
		if (t.elite()) {
			mult *= 1.5F;
		}
		return amount * mult;
	}
}
