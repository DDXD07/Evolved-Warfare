package com.evolvedwarfare.hell;

import com.evolvedwarfare.registry.ModSounds;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.entity.ai.goal.RevengeGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.ai.goal.WanderAroundFarGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;

/**
 * Un mini jefe demoníaco: con su barra de vida arriba (y su grado, de I a IV), su voz y sus poderes, que usa cada cierto
 * tiempo ({@link #ability}). Solo le hacen daño las armas con Bendición Divina ({@link DivineBlessing}).
 */
public abstract class HellBossEntity extends HostileEntity {
	private final ServerBossBar bossBar;
	private final int tier;
	private final ModSounds.Voice voice;
	protected int abilityTimer;

	protected HellBossEntity(EntityType<? extends HellBossEntity> type, World world, int tier, ModSounds.Voice voice, BossBar.Color color) {
		super(type, world);
		this.tier = tier;
		this.voice = voice;
		this.experiencePoints = 40 * tier;
		this.bossBar = new ServerBossBar(barName(), color, BossBar.Style.NOTCHED_10);
		this.abilityTimer = 60;
	}

	public int tier() {
		return tier;
	}

	public ServerBossBar bossBar() {
		return bossBar;
	}

	private Text barName() {
		String roman = switch (tier) {
			case 1 -> "I";
			case 2 -> "II";
			case 3 -> "III";
			default -> "IV";
		};
		return Text.translatable("boss.evolvedwarfare.tier", getDisplayName(), roman).formatted(tier >= 4 ? Formatting.DARK_RED : Formatting.RED);
	}

	/** Lo de todos: vida, daño, armadura, sin retroceso y lejos de vista. */
	protected static DefaultAttributeContainer.Builder bossAttributes(double health, double damage, double armor, double speed) {
		return HostileEntity.createHostileAttributes()
				.add(EntityAttributes.GENERIC_MAX_HEALTH, health)
				.add(EntityAttributes.GENERIC_ATTACK_DAMAGE, damage)
				.add(EntityAttributes.GENERIC_ARMOR, armor)
				.add(EntityAttributes.GENERIC_MOVEMENT_SPEED, speed)
				.add(EntityAttributes.GENERIC_FOLLOW_RANGE, 48.0)
				.add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 0.9)
				.add(EntityAttributes.GENERIC_ATTACK_KNOCKBACK, 1.5)
				.add(EntityAttributes.GENERIC_STEP_HEIGHT, 1.5);
	}

	@Override
	protected void initGoals() {
		goalSelector.add(0, new SwimGoal(this));
		goalSelector.add(2, new MeleeAttackGoal(this, 1.1, true));
		goalSelector.add(6, new WanderAroundFarGoal(this, 0.7));
		goalSelector.add(7, new LookAtEntityGoal(this, PlayerEntity.class, 16.0F));
		goalSelector.add(8, new LookAroundGoal(this));
		targetSelector.add(1, new RevengeGoal(this));
		targetSelector.add(2, new ActiveTargetGoal<>(this, PlayerEntity.class, false));
	}

	/** Su poder (cada vez que se acaba la espera, con presa): devuelve la espera hasta el siguiente (0: aún no). */
	protected abstract int ability(ServerWorld world, LivingEntity target);

	@Override
	protected void mobTick() {
		super.mobTick();
		bossBar.setPercent(getHealth() / getMaxHealth());
		LivingEntity target = getTarget();
		if (getWorld() instanceof ServerWorld world && target != null && target.isAlive() && --abilityTimer <= 0) {
			int wait = ability(world, target);
			abilityTimer = wait > 0 ? wait : 20;
		}
	}

	/** Los vivos (que no sean demonios) en un radio. */
	protected java.util.List<LivingEntity> around(double radius) {
		return getWorld().getEntitiesByClass(LivingEntity.class, new Box(getBlockPos()).expand(radius),
				e -> e != this && e.isAlive() && !DivineBlessing.isDemon(e) && !(e instanceof PlayerEntity p && (p.isCreative() || p.isSpectator())));
	}

	@Override
	public void onStartedTrackingBy(ServerPlayerEntity player) {
		super.onStartedTrackingBy(player);
		bossBar.addPlayer(player);
	}

	@Override
	public void onStoppedTrackingBy(ServerPlayerEntity player) {
		super.onStoppedTrackingBy(player);
		bossBar.removePlayer(player);
	}

	@Override
	public void readCustomDataFromNbt(NbtCompound nbt) {
		super.readCustomDataFromNbt(nbt);
		bossBar.setName(barName());
	}

	@Override
	public void setCustomName(Text name) {
		super.setCustomName(name);
		bossBar.setName(barName());
	}

	@Override
	protected boolean isDisallowedInPeaceful() {
		return true;
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return voice.ambient();
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return voice.hurt();
	}

	@Override
	protected SoundEvent getDeathSound() {
		return voice.death();
	}

	@Override
	protected float getSoundVolume() {
		return 1.5F + tier * 0.25F;
	}
}
