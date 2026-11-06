package com.evolvedwarfare.hell;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
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
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;

/**
 * Un mini jefe del Apocalipsis (fase 36): su barra de vida (morada), sus fases según la vida que le queda y sus poderes
 * cada cierto tiempo. Le hace daño cualquier arma, pero las de Bendición Divina le hacen la mitad más.
 */
public abstract class MiniBossEntity extends HostileEntity {
	private final ServerBossBar bossBar;
	protected int abilityTimer = 60;

	protected MiniBossEntity(EntityType<? extends HostileEntity> type, World world, BossBar.Color color) {
		super(type, world);
		this.bossBar = new ServerBossBar(Text.empty(), color, BossBar.Style.NOTCHED_12);
		this.experiencePoints = 150;
		setPersistent();
	}

	protected static DefaultAttributeContainer.Builder attributes(double health, double damage, double armor, double speed) {
		return HostileEntity.createHostileAttributes()
				.add(EntityAttributes.GENERIC_MAX_HEALTH, health)
				.add(EntityAttributes.GENERIC_ATTACK_DAMAGE, damage)
				.add(EntityAttributes.GENERIC_ARMOR, armor)
				.add(EntityAttributes.GENERIC_ARMOR_TOUGHNESS, armor / 2)
				.add(EntityAttributes.GENERIC_MOVEMENT_SPEED, speed)
				.add(EntityAttributes.GENERIC_FOLLOW_RANGE, 48.0)
				.add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 0.85)
				.add(EntityAttributes.GENERIC_STEP_HEIGHT, 1.5);
	}

	public ServerBossBar bossBar() {
		return bossBar;
	}

	/** 1, 2 o 3 según la vida (más de 60 %, más de 30 %, menos). */
	public int phase() {
		float f = getHealth() / getMaxHealth();
		return f > 0.6F ? 1 : f > 0.3F ? 2 : 3;
	}

	/** Su poder: devuelve la espera hasta el siguiente. */
	protected abstract int ability(ServerWorld world, LivingEntity target);

	@Override
	protected void mobTick() {
		super.mobTick();
		bossBar.setName(getDisplayName().copy().formatted(Formatting.DARK_PURPLE));
		bossBar.setPercent(getHealth() / getMaxHealth());
		LivingEntity target = getTarget();
		if (getWorld() instanceof ServerWorld world && target != null && target.isAlive() && --abilityTimer <= 0) {
			int wait = ability(world, target);
			abilityTimer = wait > 0 ? wait : 20;
		}
	}

	@Override
	public boolean damage(DamageSource source, float amount) {
		if (!getWorld().isClient && DivineBlessing.blessed(getWorld(), source)) {
			amount *= 1.5F;
		}
		return super.damage(source, amount);
	}

	/** Los jugadores (no creativos) cerca. */
	protected java.util.List<PlayerEntity> players(double radius) {
		return getWorld().getEntitiesByClass(PlayerEntity.class, new Box(getBlockPos()).expand(radius),
				p -> p.isAlive() && !p.isSpectator() && !Curses.creative(p));
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
	public void writeCustomDataToNbt(NbtCompound nbt) {
		super.writeCustomDataToNbt(nbt);
		nbt.putInt("Ability", abilityTimer);
	}

	@Override
	public void readCustomDataFromNbt(NbtCompound nbt) {
		super.readCustomDataFromNbt(nbt);
		abilityTimer = nbt.getInt("Ability");
	}

	@Override
	public boolean cannotDespawn() {
		return true;
	}

	@Override
	protected boolean isDisallowedInPeaceful() {
		return true;
	}

	@Override
	public boolean handleFallDamage(float fallDistance, float damageMultiplier, DamageSource damageSource) {
		return false;
	}
}
