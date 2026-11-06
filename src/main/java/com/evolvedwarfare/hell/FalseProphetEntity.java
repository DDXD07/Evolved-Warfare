package com.evolvedwarfare.hell;

import java.util.List;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.ai.goal.FleeEntityGoal;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.entity.ai.goal.RevengeGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.ai.goal.WanderAroundFarGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * El Falso Profeta: responde a las ofrendas de los corruptos (o a su rito). No pelea de cerca: se aparta (y se desvanece
 * para aparecer más lejos), hace bajar fuego del cielo, convierte a las criaturas de alrededor en sus seguidores, te cambia
 * de sitio lo que llevas en la mano y, desde la mitad de la vida, se rodea de ilusiones de sí mismo. Junto a un altar de
 * falsos dioses se cura.
 */
public class FalseProphetEntity extends MiniBossEntity {
	public static final String FOLLOWER = "ew_prophet_follower";
	private static final TrackedData<Boolean> ILLUSION = DataTracker.registerData(FalseProphetEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
	private int life = -1;
	private boolean illusionsCast;

	public FalseProphetEntity(EntityType<? extends HostileEntity> type, World world) {
		super(type, world, BossBar.Color.WHITE);
	}

	public static DefaultAttributeContainer.Builder createAttributes() {
		return attributes(260.0, 6.0, 6.0, 0.3);
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {
		super.initDataTracker(builder);
		builder.add(ILLUSION, false);
	}

	public boolean illusion() {
		return dataTracker.get(ILLUSION);
	}

	/** Una ilusión: igual por fuera, pero de un golpe se deshace y se acaba sola. */
	public void makeIllusion(int ticks) {
		dataTracker.set(ILLUSION, true);
		life = ticks;
		setHealth(1.0F);
		experiencePoints = 0;
	}

	@Override
	protected void initGoals() {
		goalSelector.add(0, new SwimGoal(this));
		goalSelector.add(2, new FleeEntityGoal<>(this, PlayerEntity.class, 5.0F, 1.0, 1.25, e -> !Curses.creative((PlayerEntity) e)));
		goalSelector.add(4, new MeleeAttackGoal(this, 1.0, false));
		goalSelector.add(6, new WanderAroundFarGoal(this, 0.6));
		goalSelector.add(7, new LookAtEntityGoal(this, PlayerEntity.class, 16.0F));
		goalSelector.add(8, new LookAroundGoal(this));
		targetSelector.add(1, new RevengeGoal(this));
		targetSelector.add(2, new ActiveTargetGoal<>(this, PlayerEntity.class, false));
	}

	@Override
	protected void mobTick() {
		if (illusion()) {
			if (--life <= 0) {
				vanish();
			}
			// las ilusiones no tienen poderes ni barra propia
			return;
		}
		super.mobTick();
		LivingEntity target = getTarget();
		if (target != null && squaredDistanceTo(target) < 9 && age % 30 == 0 && getWorld() instanceof ServerWorld world) {
			blink(world);
		}
	}

	private void vanish() {
		if (getWorld() instanceof ServerWorld world) {
			world.spawnParticles(ParticleTypes.CLOUD, getX(), getBodyY(0.5), getZ(), 20, 0.3, 0.6, 0.3, 0.02);
		}
		discard();
	}

	/** Se desvanece y aparece unos bloques más lejos. */
	public boolean blink(ServerWorld world) {
		for (int i = 0; i < 12; i++) {
			double x = getX() + (random.nextDouble() - 0.5) * 16;
			double z = getZ() + (random.nextDouble() - 0.5) * 16;
			double y = getY() + random.nextInt(5) - 2;
			world.spawnParticles(ParticleTypes.WITCH, getX(), getBodyY(0.5), getZ(), 20, 0.3, 0.8, 0.3, 0.05);
			if (teleport(x, y, z, true)) {
				world.playSound(null, getBlockPos(), SoundEvents.ENTITY_ILLUSIONER_MIRROR_MOVE, SoundCategory.HOSTILE, 1.0F, 0.8F);
				return true;
			}
		}
		return false;
	}

	@Override
	protected int ability(ServerWorld world, LivingEntity target) {
		healAtAltar(world);
		if (phase() >= 2 && !illusionsCast) {
			illusionsCast = true;
			illusions(world, target, 3);
			return 100;
		}
		int pick = random.nextInt(phase() == 1 ? 3 : 4);
		switch (pick) {
			case 0 -> {
				int n = convert(world, 10, target);
				if (n == 0) {
					fire(world, target);
				}
			}
			case 1 -> deceive(target);
			case 3 -> illusions(world, target, 2);
			default -> fire(world, target);
		}
		return phase() == 3 ? 70 : 100;
	}

	/** Fuego del cielo alrededor de la presa. */
	public void fire(ServerWorld world, LivingEntity target) {
		swingHand(net.minecraft.util.Hand.MAIN_HAND);
		int n = 2 + phase();
		for (int i = 0; i < n; i++) {
			BlockPos at = i == 0 ? target.getBlockPos() : target.getBlockPos().add(random.nextInt(7) - 3, 0, random.nextInt(7) - 3);
			Apocalypse.fireFromHeaven(world, at, this, 6.0F);
		}
	}

	/** Los "milagros": las criaturas hostiles de alrededor se vuelven sus seguidoras (más fuertes, contra ti). */
	public int convert(ServerWorld world, double radius, LivingEntity target) {
		List<MobEntity> mobs = world.getEntitiesByClass(MobEntity.class, getBoundingBox().expand(radius), e -> e instanceof HostileEntity && e != this
				&& !(e instanceof MiniBossEntity) && !(e instanceof HellBossEntity) && !e.getCommandTags().contains(FOLLOWER));
		int n = 0;
		for (MobEntity m : mobs) {
			if (n >= 3) {
				break;
			}
			m.addCommandTag(FOLLOWER);
			m.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, 1200, 1));
			m.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 1200, 0));
			m.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 1200, 0));
			m.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 1200, 0));
			if (!m.hasCustomName()) {
				m.setCustomName(Text.translatable("entity.evolvedwarfare.prophet_follower").formatted(Formatting.GOLD));
			}
			if (target != null) {
				m.setTarget(target);
			}
			world.spawnParticles(ParticleTypes.END_ROD, m.getX(), m.getBodyY(0.6), m.getZ(), 20, 0.3, 0.6, 0.3, 0.05);
			n++;
		}
		if (n > 0) {
			swingHand(net.minecraft.util.Hand.OFF_HAND);
			world.playSound(null, getBlockPos(), SoundEvents.ENTITY_EVOKER_PREPARE_WOLOLO, SoundCategory.HOSTILE, 1.5F, 0.7F);
			for (PlayerEntity p : players(24)) {
				p.sendMessage(Text.translatable("message.evolvedwarfare.prophet.miracle").formatted(Formatting.GOLD, Formatting.ITALIC), true);
			}
		}
		return n;
	}

	/** El engaño: te cambia lo de la mano por otra cosa de la barra, y te marea. */
	public void deceive(LivingEntity target) {
		if (target instanceof PlayerEntity p && !Curses.creative(p)) {
			PlayerInventory inv = p.getInventory();
			int other = random.nextInt(PlayerInventory.getHotbarSize());
			if (other != inv.selectedSlot) {
				ItemStack a = inv.getStack(inv.selectedSlot);
				inv.setStack(inv.selectedSlot, inv.getStack(other));
				inv.setStack(other, a);
			}
			p.addStatusEffect(new StatusEffectInstance(StatusEffects.NAUSEA, 140, 0), this);
			p.sendMessage(Text.translatable("message.evolvedwarfare.prophet.deceive").formatted(Formatting.GOLD, Formatting.ITALIC), true);
			playSound(SoundEvents.ENTITY_ILLUSIONER_CAST_SPELL, 1.5F, 0.8F);
		}
	}

	/** Ilusiones de sí mismo alrededor (duran 30 s). */
	public int illusions(ServerWorld world, LivingEntity target, int n) {
		int made = 0;
		for (int i = 0; i < n; i++) {
			FalseProphetEntity copy = Apocalypse.FALSE_PROPHET.create(world);
			if (copy == null) {
				continue;
			}
			double a = i * Math.PI * 2 / n + random.nextDouble();
			copy.refreshPositionAndAngles(getX() + Math.cos(a) * 4, getY(), getZ() + Math.sin(a) * 4, getYaw(), 0);
			copy.makeIllusion(600);
			copy.setCustomName(Text.translatable("entity.evolvedwarfare.prophet_illusion"));
			copy.setCustomNameVisible(false);
			copy.setTarget(target);
			world.spawnEntity(copy);
			world.spawnParticles(ParticleTypes.WITCH, copy.getX(), copy.getBodyY(0.5), copy.getZ(), 20, 0.3, 0.8, 0.3, 0.05);
			made++;
		}
		world.playSound(null, getBlockPos(), SoundEvents.ENTITY_ILLUSIONER_PREPARE_MIRROR, SoundCategory.HOSTILE, 1.5F, 0.8F);
		for (PlayerEntity p : players(24)) {
			p.sendMessage(Text.translatable("message.evolvedwarfare.prophet.illusions").formatted(Formatting.GOLD, Formatting.ITALIC), true);
		}
		return made;
	}

	private void healAtAltar(ServerWorld world) {
		for (BlockPos p : BlockPos.iterate(getBlockPos().add(-6, -2, -6), getBlockPos().add(6, 2, 6))) {
			if (world.getBlockState(p).isOf(HellStructures.FALSE_ALTAR)) {
				heal(12.0F);
				world.spawnParticles(ParticleTypes.HAPPY_VILLAGER, getX(), getBodyY(0.7), getZ(), 10, 0.4, 0.6, 0.4, 0.0);
				return;
			}
		}
	}

	@Override
	public boolean damage(DamageSource source, float amount) {
		if (illusion() && !getWorld().isClient && !isInvulnerableTo(source)) {
			vanish();
			return true;
		}
		return super.damage(source, amount);
	}

	@Override
	public void onStartedTrackingBy(net.minecraft.server.network.ServerPlayerEntity player) {
		if (!illusion()) {
			super.onStartedTrackingBy(player);
		}
	}

	@Override
	protected void dropLoot(DamageSource damageSource, boolean causedByPlayer) {
		if (!illusion()) {
			super.dropLoot(damageSource, causedByPlayer);
		}
	}

	@Override
	public void writeCustomDataToNbt(NbtCompound nbt) {
		super.writeCustomDataToNbt(nbt);
		nbt.putBoolean("Illusion", illusion());
		nbt.putInt("Life", life);
		nbt.putBoolean("IllusionsCast", illusionsCast);
	}

	@Override
	public void readCustomDataFromNbt(NbtCompound nbt) {
		super.readCustomDataFromNbt(nbt);
		dataTracker.set(ILLUSION, nbt.getBoolean("Illusion"));
		life = nbt.getInt("Life");
		illusionsCast = nbt.getBoolean("IllusionsCast");
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return SoundEvents.ENTITY_EVOKER_AMBIENT;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.ENTITY_EVOKER_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.ENTITY_EVOKER_DEATH;
	}

	@Override
	protected float getSoundPitch() {
		return 0.7F;
	}
}
