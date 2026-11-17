package com.evolvedwarfare.difficulty.ai;

import com.evolvedwarfare.difficulty.SmartMobs;
import net.minecraft.block.Blocks;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.mob.SpiderEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameRules;

/** La araña te escupe telaraña a los pies desde lejos (entre 4 y 14 bloques, viéndote): te deja pegado un rato. */
public class SpiderWebGoal extends Goal {
	private final SpiderEntity spider;
	private int cooldown = 60;

	public SpiderWebGoal(SpiderEntity spider) {
		this.spider = spider;
	}

	@Override
	public boolean canStart() {
		if (cooldown > 0) {
			cooldown--;
			return false;
		}
		LivingEntity target = spider.getTarget();
		if (target == null || !target.isAlive()) {
			return false;
		}
		double d = spider.squaredDistanceTo(target);
		return d > 16.0 && d < 196.0 && spider.canSee(target) && spider.getRandom().nextInt(20) == 0;
	}

	@Override
	public void start() {
		LivingEntity target = spider.getTarget();
		if (target != null) {
			fire(spider, target);
		}
		cooldown = 200 + spider.getRandom().nextInt(160);
	}

	@Override
	public boolean shouldContinue() {
		return false;
	}

	/** Escupe la telaraña (si ahí hay aire); se deshace sola al rato. */
	public static boolean fire(SpiderEntity spider, LivingEntity target) {
		if (!(spider.getWorld() instanceof ServerWorld world) || !world.getGameRules().getBoolean(GameRules.DO_MOB_GRIEFING)) {
			return false;
		}
		BlockPos pos = target.getBlockPos();
		if (!world.getBlockState(pos).isAir()) {
			return false;
		}
		world.setBlockState(pos, Blocks.COBWEB.getDefaultState());
		SmartMobs.temporaryWeb(world, pos);
		world.playSound(null, spider.getX(), spider.getY(), spider.getZ(), SoundEvents.ENTITY_LLAMA_SPIT, SoundCategory.HOSTILE, 0.8F, 0.7F);
		Vec3d from = spider.getEyePos(), to = target.getPos().add(0, 0.5, 0);
		for (int k = 0; k <= 10; k++) {
			Vec3d p = from.lerp(to, k / 10.0);
			world.spawnParticles(ParticleTypes.WHITE_ASH, p.x, p.y, p.z, 2, 0.05, 0.05, 0.05, 0.0);
		}
		return true;
	}
}
