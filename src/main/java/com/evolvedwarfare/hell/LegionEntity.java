package com.evolvedwarfare.hell;

import com.evolvedwarfare.registry.ModEntities;
import com.evolvedwarfare.registry.ModSounds;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;

/**
 * Legión (mini jefe I): una masa de carne llena de caras. «Mi nombre es Legión, porque somos muchos»: al herirlo, se le
 * desprenden diablillos (como mucho cada 8 segundos), y araña fuerte.
 */
public class LegionEntity extends HellBossEntity {
	private int splitCooldown;

	public LegionEntity(EntityType<? extends LegionEntity> type, World world) {
		super(type, world, 1, ModSounds.LEGION, BossBar.Color.RED);
	}

	public static DefaultAttributeContainer.Builder createAttributes() {
		return bossAttributes(120.0, 8.0, 6.0, 0.25);
	}

	/** Se le desprenden dos diablillos. Devuelve cuántos. */
	public int split(ServerWorld world) {
		int n = 0;
		for (int i = 0; i < 2; i++) {
			ImpEntity imp = ModEntities.IMP.create(world);
			if (imp == null) {
				continue;
			}
			imp.refreshPositionAndAngles(getX() + (random.nextDouble() - 0.5) * 2, getY() + 0.5, getZ() + (random.nextDouble() - 0.5) * 2,
					random.nextFloat() * 360, 0);
			imp.initialize(world, world.getLocalDifficulty(getBlockPos()), SpawnReason.MOB_SUMMONED, null);
			imp.setTarget(getTarget());
			world.spawnEntity(imp);
			n++;
		}
		world.spawnParticles(ParticleTypes.CRIMSON_SPORE, getX(), getBodyY(0.6), getZ(), 30, 0.6, 0.8, 0.6, 0.05);
		for (ServerPlayerEntity p : world.getPlayers(p -> p.squaredDistanceTo(this) < 32 * 32)) {
			p.sendMessage(Text.translatable("message.evolvedwarfare.legion.split").formatted(Formatting.DARK_RED, Formatting.ITALIC), true);
		}
		return n;
	}

	@Override
	public boolean damage(DamageSource source, float amount) {
		boolean hurt = super.damage(source, amount);
		if (hurt && getWorld() instanceof ServerWorld world && splitCooldown <= 0 && isAlive()) {
			splitCooldown = 160;
			split(world);
		}
		return hurt;
	}

	@Override
	protected void mobTick() {
		super.mobTick();
		if (splitCooldown > 0) {
			splitCooldown--;
		}
	}

	@Override
	protected int ability(ServerWorld world, LivingEntity target) {
		return 200; // su poder es partirse al recibir golpes
	}
}
