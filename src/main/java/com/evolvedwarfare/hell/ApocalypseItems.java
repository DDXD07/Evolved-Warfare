package com.evolvedwarfare.hell;

import java.util.List;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.ai.NoPenaltyTargeting;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/** Los objetos de los mini jefes: el báculo del Falso Profeta, el cuerno de la Bestia y las alas del Abismo. */
public final class ApocalypseItems {
	private ApocalypseItems() {
	}

	private abstract static class Described extends Item {
		Described(Settings settings) {
			super(settings);
		}

		@Override
		public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
			tooltip.add(Text.translatable(getTranslationKey() + ".desc").formatted(Formatting.GRAY));
		}
	}

	/** El báculo: fuego del cielo donde miras (hasta 48 bloques). En el Nether cuesta corrupción. */
	public static class ProphetStaff extends Described {
		public ProphetStaff(Settings settings) {
			super(settings);
		}

		@Override
		public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
			ItemStack stack = user.getStackInHand(hand);
			if (world instanceof ServerWorld sw) {
				HitResult hit = user.raycast(48.0, 0.0F, false);
				BlockPos at = hit instanceof BlockHitResult b && hit.getType() == HitResult.Type.BLOCK ? b.getBlockPos().up()
						: BlockPos.ofFloored(hit.getPos());
				Apocalypse.fireFromHeaven(sw, at, user, 8.0F);
				if (user instanceof ServerPlayerEntity sp && Curses.inNether(sp)) {
					NetherClimate.addCorruption(sp, 2);
				}
				stack.damage(1, user, hand == Hand.MAIN_HAND ? net.minecraft.entity.EquipmentSlot.MAINHAND : net.minecraft.entity.EquipmentSlot.OFFHAND);
				user.getItemCooldownManager().set(this, 50);
			}
			user.swingHand(hand);
			return TypedActionResult.success(stack, world.isClient);
		}
	}

	/** El cuerno: las criaturas hostiles cercanas (no los jefes) se asustan, se debilitan y huyen. */
	public static class BeastHorn extends Described {
		public BeastHorn(Settings settings) {
			super(settings);
		}

		@Override
		public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
			ItemStack stack = user.getStackInHand(hand);
			if (world instanceof ServerWorld sw) {
				int n = fear(sw, user, 20);
				user.sendMessage(Text.translatable("message.evolvedwarfare.horn.blown", n).formatted(Formatting.GOLD), true);
				user.getItemCooldownManager().set(this, 600);
			}
			return TypedActionResult.success(stack, world.isClient);
		}

		public static int fear(ServerWorld world, PlayerEntity user, double radius) {
			world.playSound(null, user.getBlockPos(), SoundEvents.EVENT_RAID_HORN.value(), SoundCategory.PLAYERS, 2.0F, 0.6F);
			world.spawnParticles(ParticleTypes.SONIC_BOOM, user.getX(), user.getEyeY(), user.getZ(), 1, 0, 0, 0, 0);
			List<MobEntity> mobs = world.getEntitiesByClass(MobEntity.class, user.getBoundingBox().expand(radius),
					e -> e instanceof HostileEntity && !(e instanceof MiniBossEntity) && !(e instanceof HellBossEntity) && e.isAlive());
			for (MobEntity m : mobs) {
				m.setTarget(null);
				m.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 300, 1));
				m.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 200, 0));
				if (m instanceof PathAwareEntity pa) {
					Vec3d away = NoPenaltyTargeting.findFrom(pa, 16, 7, user.getPos());
					if (away != null) {
						pa.getNavigation().startMovingTo(away.x, away.y, away.z, 1.4);
					}
				}
			}
			return mobs.size();
		}
	}

	/** Las alas: un gran salto alado hacia donde miras y caída lenta. */
	public static class AbyssWings extends Described {
		public AbyssWings(Settings settings) {
			super(settings);
		}

		@Override
		public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
			ItemStack stack = user.getStackInHand(hand);
			Vec3d look = user.getRotationVec(1.0F);
			user.addVelocity(look.x * 1.4, 0.9 + Math.max(0, look.y) * 0.6, look.z * 1.4);
			user.velocityModified = true;
			user.fallDistance = 0;
			if (world instanceof ServerWorld sw) {
				user.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOW_FALLING, 120, 0, false, false, true));
				sw.spawnParticles(ParticleTypes.SMOKE, user.getX(), user.getY() + 1, user.getZ(), 30, 0.5, 0.3, 0.5, 0.05);
				sw.playSound(null, user.getBlockPos(), SoundEvents.ENTITY_ENDER_DRAGON_FLAP, SoundCategory.PLAYERS, 1.0F, 1.2F);
				stack.damage(1, user, hand == Hand.MAIN_HAND ? net.minecraft.entity.EquipmentSlot.MAINHAND : net.minecraft.entity.EquipmentSlot.OFFHAND);
				user.getItemCooldownManager().set(this, 80);
			}
			return TypedActionResult.success(stack, world.isClient);
		}
	}

	/** La Llave del Abismo (con su descripción). */
	public static class Key extends Described {
		public Key(Settings settings) {
			super(settings);
		}

		@Override
		public boolean hasGlint(ItemStack stack) {
			return true;
		}
	}
}
