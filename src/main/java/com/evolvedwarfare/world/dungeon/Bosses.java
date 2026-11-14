package com.evolvedwarfare.world.dungeon;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.block.Blocks;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.AreaEffectCloudEntity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.AbstractPiglinEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.SlimeEntity;
import net.minecraft.entity.mob.VexEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.SmallFireballEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

/**
 * Los mini jefes de las mazmorras: monstruos vanilla potenciados (más vida, más grandes, con equipo y un nombre) y sus
 * poderes, que el Corazón de la mazmorra lanza cada pocos segundos.
 */
public final class Bosses {
	private Bosses() {
	}

	public enum Power {
		/** Llama a sus secuaces. */
		SUMMON,
		/** Salta hacia el objetivo y, al caer, una onda de choque. */
		LEAP,
		/** Onda de choque: daño y empujón alrededor. */
		SHOCKWAVE,
		/** Aura de marchitamiento. */
		WITHER_AURA,
		/** Anillo de fuego. */
		FIRE_RING,
		/** Bolas de fuego. */
		FIREBALL,
		/** Un rayo sobre el objetivo. */
		LIGHTNING,
		/** Hace flotar al objetivo. */
		LEVITATE,
		/** Aparece detrás del objetivo. */
		BLINK,
		/** Tormenta de arena: ciega y ralentiza. */
		SANDSTORM,
		/** Maldición: hambre y debilidad. */
		CURSE,
		/** Telarañas a los pies del objetivo. */
		WEB,
		/** Nube de veneno. */
		POISON_CLOUD;

		public void use(ServerWorld world, MobEntity boss, @Nullable LivingEntity target, Boss spec, @Nullable DungeonHeartBlockEntity heart) {
			Vec3d at = boss.getPos();
			switch (this) {
				case SUMMON -> {
					int n = spec.minions() + (heart != null && heart.enraged() ? 1 : 0);
					for (int i = 0; i < n; i++) {
						MobEntity m = spec.minion().create(world);
						if (m == null) {
							continue;
						}
						double a = world.random.nextDouble() * Math.PI * 2;
						m.refreshPositionAndAngles(at.x + Math.cos(a) * 2.5, at.y + 0.5, at.z + Math.sin(a) * 2.5, world.random.nextFloat() * 360, 0);
						m.initialize(world, world.getLocalDifficulty(boss.getBlockPos()), SpawnReason.MOB_SUMMONED, null);
						if (m instanceof VexEntity vex) {
							vex.setLifeTicks(20 * 20);
						}
						if (m instanceof SlimeEntity slime) {
							slime.setSize(2, true);
						}
						m.addCommandTag(Dungeons.MINION_TAG);
						if (target != null) {
							m.setTarget(target);
						}
						world.spawnEntity(m);
						world.spawnParticles(ParticleTypes.POOF, m.getX(), m.getY() + 0.5, m.getZ(), 10, 0.3, 0.4, 0.3, 0.02);
					}
					world.playSound(null, boss.getBlockPos(), SoundEvents.ENTITY_EVOKER_PREPARE_SUMMON, SoundCategory.HOSTILE, 1.0F, 0.8F);
				}
				case LEAP -> {
					if (target == null) {
						return;
					}
					Vec3d d = target.getPos().subtract(at);
					double h = Math.sqrt(d.x * d.x + d.z * d.z);
					if (h < 0.1) {
						return;
					}
					double k = Math.min(1.4, 0.25 + h * 0.11);
					boss.setVelocity(d.x / h * k, 0.75, d.z / h * k);
					boss.velocityModified = true;
					if (heart != null) {
						heart.leaped();
					}
					world.playSound(null, boss.getBlockPos(), SoundEvents.ENTITY_RAVAGER_ATTACK, SoundCategory.HOSTILE, 1.0F, 0.7F);
				}
				case SHOCKWAVE -> {
					for (PlayerEntity p : players(world, at, 5.0)) {
						p.damage(boss.getDamageSources().mobAttack(boss), 6.0F);
						Vec3d push = p.getPos().subtract(at);
						p.takeKnockback(1.4, -push.x, -push.z);
						p.velocityModified = true;
					}
					for (int i = 0; i < 24; i++) {
						double a = i * Math.PI / 12;
						world.spawnParticles(ParticleTypes.EXPLOSION, at.x + Math.cos(a) * 3, at.y + 0.2, at.z + Math.sin(a) * 3, 1, 0, 0, 0, 0);
					}
					world.playSound(null, boss.getBlockPos(), SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.HOSTILE, 0.8F, 0.6F);
				}
				case WITHER_AURA -> {
					for (PlayerEntity p : players(world, at, 7.0)) {
						p.addStatusEffect(new StatusEffectInstance(StatusEffects.WITHER, 80, 1), boss);
					}
					world.spawnParticles(ParticleTypes.SOUL, at.x, at.y + 1, at.z, 50, 3, 0.8, 3, 0.02);
					world.playSound(null, boss.getBlockPos(), SoundEvents.ENTITY_WITHER_AMBIENT, SoundCategory.HOSTILE, 1.0F, 0.6F);
				}
				case FIRE_RING -> {
					for (PlayerEntity p : players(world, at, 6.0)) {
						p.setOnFireFor(5.0F);
						p.damage(boss.getDamageSources().mobAttack(boss), 3.0F);
					}
					for (int r = 1; r <= 6; r++) {
						for (int i = 0; i < r * 6; i++) {
							double a = i * Math.PI * 2 / (r * 6);
							world.spawnParticles(ParticleTypes.FLAME, at.x + Math.cos(a) * r, at.y + 0.2, at.z + Math.sin(a) * r, 1, 0, 0.1, 0, 0.01);
						}
					}
					world.playSound(null, boss.getBlockPos(), SoundEvents.ITEM_FIRECHARGE_USE, SoundCategory.HOSTILE, 1.0F, 0.7F);
				}
				case FIREBALL -> {
					if (target == null) {
						return;
					}
					for (int i = 0; i < 3; i++) {
						Vec3d v = target.getEyePos().subtract(boss.getEyePos()).normalize().add(world.random.nextGaussian() * 0.08, 0,
								world.random.nextGaussian() * 0.08);
						SmallFireballEntity ball = new SmallFireballEntity(world, boss, v);
						ball.setPosition(boss.getX(), boss.getEyeY() - 0.2, boss.getZ());
						world.spawnEntity(ball);
					}
					world.playSound(null, boss.getBlockPos(), SoundEvents.ENTITY_BLAZE_SHOOT, SoundCategory.HOSTILE, 1.0F, 0.8F);
				}
				case LIGHTNING -> {
					if (target == null) {
						return;
					}
					LightningEntity bolt = EntityType.LIGHTNING_BOLT.create(world);
					if (bolt != null) {
						bolt.refreshPositionAfterTeleport(target.getX(), target.getY(), target.getZ());
						world.spawnEntity(bolt);
					}
				}
				case LEVITATE -> {
					if (target == null) {
						return;
					}
					target.addStatusEffect(new StatusEffectInstance(StatusEffects.LEVITATION, 50, 1), boss);
					world.spawnParticles(ParticleTypes.WITCH, target.getX(), target.getY() + 1, target.getZ(), 30, 0.4, 0.8, 0.4, 0.05);
					world.playSound(null, target.getBlockPos(), SoundEvents.ENTITY_SHULKER_SHOOT, SoundCategory.HOSTILE, 1.0F, 1.0F);
				}
				case BLINK -> {
					if (target == null) {
						return;
					}
					Vec3d back = target.getRotationVector().multiply(-2.5, 0, -2.5);
					Vec3d to = target.getPos().add(back.x, 0, back.z);
					BlockPos b = BlockPos.ofFloored(to);
					if (world.isAir(b) && world.isAir(b.up()) && !world.isAir(b.down())) {
						world.spawnParticles(ParticleTypes.PORTAL, at.x, at.y + 1, at.z, 40, 0.4, 0.8, 0.4, 0.3);
						boss.requestTeleport(to.x, b.getY(), to.z);
						world.playSound(null, b, SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.HOSTILE, 1.0F, 0.8F);
					}
				}
				case SANDSTORM -> {
					for (PlayerEntity p : players(world, at, 10.0)) {
						p.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, 60, 0), boss);
						p.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 80, 1), boss);
					}
					world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.SAND.getDefaultState()), at.x, at.y + 1.5, at.z, 250,
							5, 1.5, 5, 0.2);
					world.playSound(null, boss.getBlockPos(), SoundEvents.WEATHER_RAIN_ABOVE, SoundCategory.HOSTILE, 1.5F, 0.5F);
				}
				case CURSE -> {
					if (target == null) {
						return;
					}
					target.addStatusEffect(new StatusEffectInstance(StatusEffects.HUNGER, 160, 1), boss);
					target.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 100, 0), boss);
					world.spawnParticles(ParticleTypes.SQUID_INK, target.getX(), target.getY() + 1, target.getZ(), 20, 0.4, 0.6, 0.4, 0.02);
					world.playSound(null, target.getBlockPos(), SoundEvents.ENTITY_ZOMBIE_VILLAGER_CONVERTED, SoundCategory.HOSTILE, 0.8F, 0.6F);
				}
				case WEB -> {
					if (target == null) {
						return;
					}
					BlockPos c = target.getBlockPos();
					for (BlockPos p : new BlockPos[] {c, c.north(), c.south(), c.east(), c.west()}) {
						if (world.isAir(p) && world.random.nextFloat() < (p.equals(c) ? 1.0F : 0.5F)) {
							world.setBlockState(p, Blocks.COBWEB.getDefaultState());
						}
					}
					world.playSound(null, c, SoundEvents.ENTITY_SPIDER_AMBIENT, SoundCategory.HOSTILE, 1.0F, 0.5F);
				}
				case POISON_CLOUD -> {
					if (target == null) {
						return;
					}
					AreaEffectCloudEntity cloud = new AreaEffectCloudEntity(world, target.getX(), target.getY(), target.getZ());
					cloud.setOwner(boss);
					cloud.setRadius(3.0F);
					cloud.setDuration(100);
					cloud.setRadiusGrowth(-0.01F);
					cloud.addEffect(new StatusEffectInstance(StatusEffects.POISON, 100, 1));
					world.spawnEntity(cloud);
					world.playSound(null, target.getBlockPos(), SoundEvents.ENTITY_SPIDER_HURT, SoundCategory.HOSTILE, 1.0F, 0.5F);
				}
				default -> {
				}
			}
		}

		private static List<PlayerEntity> players(ServerWorld world, Vec3d at, double r) {
			return world.getPlayers(p -> p.isAlive() && !p.isSpectator() && !p.isCreative() && p.squaredDistanceTo(at) <= r * r).stream()
					.map(p -> (PlayerEntity) p).toList();
		}
	}

	public record Boss(EntityType<? extends MobEntity> type, double health, double damage, double armor, double speed, double scale,
			Consumer<MobEntity> outfit, List<Power> powers, EntityType<? extends MobEntity> minion, int minions) {

		/** Lo crea encima del Corazón, potenciado y con su nombre. */
		@Nullable
		public MobEntity spawn(ServerWorld world, BlockPos at, String dungeon) {
			MobEntity m = type.create(world);
			if (m == null) {
				return null;
			}
			m.refreshPositionAndAngles(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, world.random.nextFloat() * 360, 0);
			m.initialize(world, world.getLocalDifficulty(at), SpawnReason.EVENT, null);
			set(m, EntityAttributes.GENERIC_MAX_HEALTH, health);
			set(m, EntityAttributes.GENERIC_ATTACK_DAMAGE, damage);
			set(m, EntityAttributes.GENERIC_ARMOR, armor);
			set(m, EntityAttributes.GENERIC_ARMOR_TOUGHNESS, armor / 2);
			set(m, EntityAttributes.GENERIC_MOVEMENT_SPEED, speed);
			set(m, EntityAttributes.GENERIC_SCALE, scale);
			set(m, EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 0.8);
			set(m, EntityAttributes.GENERIC_FOLLOW_RANGE, 40);
			set(m, EntityAttributes.GENERIC_STEP_HEIGHT, 1.5);
			m.setHealth(m.getMaxHealth());
			outfit.accept(m);
			for (EquipmentSlot slot : EquipmentSlot.values()) {
				m.setEquipmentDropChance(slot, 0.0F);
			}
			if (m instanceof AbstractPiglinEntity piglin) {
				piglin.setImmuneToZombification(true);
			}
			m.setCustomName(Text.translatable("boss.evolvedwarfare." + dungeon).formatted(Formatting.GOLD));
			m.setCustomNameVisible(true);
			m.setPersistent();
			world.spawnEntity(m);
			return m;
		}

		private static void set(MobEntity m, RegistryEntry<EntityAttribute> attr, double value) {
			EntityAttributeInstance inst = m.getAttributeInstance(attr);
			if (inst != null) {
				inst.setBaseValue(value);
			}
		}
	}

	private static ItemStack enchanted(ServerWorld world, Item item, Object... pairs) {
		ItemStack s = new ItemStack(item);
		var reg = world.getRegistryManager().get(RegistryKeys.ENCHANTMENT);
		for (int i = 0; i + 1 < pairs.length; i += 2) {
			@SuppressWarnings("unchecked")
			RegistryKey<Enchantment> key = (RegistryKey<Enchantment>) pairs[i];
			reg.getEntry(key).ifPresent(e -> s.addEnchantment(e, (Integer) pairs[i + 1]));
		}
		return s;
	}

	private static Consumer<MobEntity> gear(Item head, Item chest, Item legs, Item feet, Item hand, RegistryKey<Enchantment> handEnchant, int level) {
		return m -> {
			ServerWorld w = (ServerWorld) m.getWorld();
			if (head != null) {
				m.equipStack(EquipmentSlot.HEAD, enchanted(w, head, Enchantments.PROTECTION, 3));
			}
			if (chest != null) {
				m.equipStack(EquipmentSlot.CHEST, enchanted(w, chest, Enchantments.PROTECTION, 2));
			}
			if (legs != null) {
				m.equipStack(EquipmentSlot.LEGS, new ItemStack(legs));
			}
			if (feet != null) {
				m.equipStack(EquipmentSlot.FEET, new ItemStack(feet));
			}
			if (hand != null) {
				m.equipStack(EquipmentSlot.MAINHAND, handEnchant == null ? new ItemStack(hand) : enchanted(w, hand, handEnchant, level));
			}
		};
	}

	private static final Map<String, Boss> BY_DUNGEON = Map.of(
			"forgotten_mausoleum", new Boss(EntityType.WITHER_SKELETON, 220, 11, 10, 0.30, 1.45,
					gear(Items.GOLDEN_HELMET, Items.CHAINMAIL_CHESTPLATE, null, null, Items.NETHERITE_SWORD, Enchantments.SHARPNESS, 2),
					List.of(Power.SUMMON, Power.WITHER_AURA, Power.LEAP, Power.SHOCKWAVE), EntityType.SKELETON, 3),
			"mad_mage_spire", new Boss(EntityType.EVOKER, 170, 7, 6, 0.42, 1.3,
					gear(null, null, null, null, null, null, 0),
					List.of(Power.BLINK, Power.LIGHTNING, Power.LEVITATE, Power.SUMMON, Power.FIREBALL), EntityType.VEX, 2),
			"pharaoh_tomb", new Boss(EntityType.HUSK, 240, 10, 8, 0.28, 1.6,
					gear(Items.GOLDEN_HELMET, Items.GOLDEN_CHESTPLATE, null, null, Items.GOLDEN_SWORD, Enchantments.SHARPNESS, 3),
					List.of(Power.SANDSTORM, Power.SUMMON, Power.CURSE, Power.LEAP), EntityType.HUSK, 3),
			"dwarven_forge", new Boss(EntityType.PIGLIN_BRUTE, 260, 13, 12, 0.36, 1.5,
					m -> {
						gear(Items.NETHERITE_HELMET, Items.IRON_CHESTPLATE, null, null, Items.GOLDEN_AXE, Enchantments.SHARPNESS, 3).accept(m);
						m.addStatusEffect(new StatusEffectInstance(StatusEffects.FIRE_RESISTANCE, -1, 0));
					},
					List.of(Power.FIRE_RING, Power.SHOCKWAVE, Power.LEAP, Power.FIREBALL, Power.SUMMON), EntityType.MAGMA_CUBE, 2),
			"spider_nest", new Boss(EntityType.SPIDER, 200, 9, 6, 0.34, 3.0,
					m -> {
					},
					List.of(Power.WEB, Power.POISON_CLOUD, Power.SUMMON, Power.LEAP), EntityType.CAVE_SPIDER, 4));

	@Nullable
	public static Boss of(String dungeon) {
		return BY_DUNGEON.get(dungeon);
	}

	/** Para las pruebas: el ángulo hacia el objetivo (no se usa en el juego). */
	static float yawTo(Vec3d from, Vec3d to) {
		return (float) (MathHelper.atan2(to.z - from.z, to.x - from.x) * MathHelper.DEGREES_PER_RADIAN) - 90.0F;
	}
}
