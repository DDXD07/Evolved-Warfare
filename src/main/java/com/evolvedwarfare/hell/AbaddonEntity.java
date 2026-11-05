package com.evolvedwarfare.hell;

import com.evolvedwarfare.registry.ModEntities;
import com.evolvedwarfare.registry.ModSounds;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.projectile.SmallFireballEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * Abadón, el Destructor (mini jefe IV, el peor): un esqueleto gigante coronado, con alas y cola de escorpión. De cerca
 * <b>golpea el suelo</b> (una onda que hiere y lanza por los aires a todo lo que haya en 7 bloques); de lejos lanza
 * <b>lluvias de fuego</b>; y cuando le queda menos de la mitad de vida, <b>llama a sus siervos</b> (demonios y diablillos).
 */
public class AbaddonEntity extends HellBossEntity {
	private int summonCooldown = 200;

	public AbaddonEntity(EntityType<? extends AbaddonEntity> type, World world) {
		super(type, world, 4, ModSounds.ABADDON, BossBar.Color.PURPLE);
	}

	public static DefaultAttributeContainer.Builder createAttributes() {
		return bossAttributes(500.0, 18.0, 14.0, 0.23);
	}

	/** El golpe al suelo: devuelve a cuántos alcanza. */
	public int slam(ServerWorld world) {
		int n = 0;
		for (LivingEntity e : around(7.0)) {
			e.damage(getDamageSources().mobAttack(this), 12.0F);
			Vec3d away = e.getPos().subtract(getPos()).multiply(1, 0, 1).normalize();
			e.setVelocity(away.x * 1.2, 0.9, away.z * 1.2);
			e.velocityModified = true;
			n++;
		}
		for (int i = 0; i < 24; i++) {
			double a = i * Math.PI * 2 / 24;
			world.spawnParticles(ParticleTypes.EXPLOSION, getX() + Math.cos(a) * 4, getY() + 0.2, getZ() + Math.sin(a) * 4, 1, 0, 0, 0, 0);
		}
		world.playSound(null, getBlockPos(), SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.HOSTILE, 3.0F, 0.5F);
		return n;
	}

	/** Una lluvia de fuego sobre la presa: 6 bolas que caen alrededor. */
	public int rain(ServerWorld world, LivingEntity target) {
		for (int i = 0; i < 6; i++) {
			double x = target.getX() + (random.nextDouble() - 0.5) * 6;
			double z = target.getZ() + (random.nextDouble() - 0.5) * 6;
			SmallFireballEntity fb = new SmallFireballEntity(world, this, new Vec3d(0, -1, 0));
			fb.setPosition(x, target.getY() + 14 + random.nextDouble() * 4, z);
			world.spawnEntity(fb);
		}
		world.playSound(null, target.getBlockPos(), SoundEvents.ENTITY_WITHER_SHOOT, SoundCategory.HOSTILE, 2.0F, 0.5F);
		return 6;
	}

	/** Llama a sus siervos: un demonio y dos diablillos. Devuelve cuántos. */
	public int summon(ServerWorld world) {
		int n = 0;
		EntityType<?>[] servants = {ModEntities.DEMON, ModEntities.IMP, ModEntities.IMP};
		for (EntityType<?> type : servants) {
			if (type.create(world) instanceof net.minecraft.entity.mob.HostileEntity mob) {
				mob.refreshPositionAndAngles(getX() + (random.nextDouble() - 0.5) * 6, getY(), getZ() + (random.nextDouble() - 0.5) * 6,
						random.nextFloat() * 360, 0);
				mob.initialize(world, world.getLocalDifficulty(getBlockPos()), SpawnReason.MOB_SUMMONED, null);
				mob.setTarget(getTarget());
				world.spawnEntity(mob);
				world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, mob.getX(), mob.getY() + 1, mob.getZ(), 20, 0.3, 0.6, 0.3, 0.02);
				n++;
			}
		}
		for (ServerPlayerEntity p : world.getPlayers(p -> p.squaredDistanceTo(this) < 48 * 48)) {
			p.sendMessage(Text.translatable("message.evolvedwarfare.abaddon.summon").formatted(Formatting.DARK_PURPLE), true);
		}
		return n;
	}

	@Override
	protected int ability(ServerWorld world, LivingEntity target) {
		if (getHealth() < getMaxHealth() / 2 && summonCooldown <= 0) {
			summonCooldown = 600;
			summon(world);
			return 60;
		}
		double d = squaredDistanceTo(target);
		if (d < 6 * 6) {
			slam(world);
			return 140;
		}
		if (d < 32 * 32) {
			rain(world, target);
			return 120;
		}
		return 20;
	}

	@Override
	protected void mobTick() {
		super.mobTick();
		if (summonCooldown > 0) {
			summonCooldown--;
		}
	}
}
