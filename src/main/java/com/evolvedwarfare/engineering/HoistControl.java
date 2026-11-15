package com.evolvedwarfare.engineering;

import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

/** Lo común a la polea y la grúa: mueven lo que cuelga de ellas según giran, hacia arriba o hacia abajo. */
final class HoistControl {
	private HoistControl() {
	}

	@Nullable
	static HoistEntity hoist(ServerWorld world, MachineBlockEntity be) {
		if (be.hoist == null) {
			return null;
		}
		Entity e = world.getEntity(be.hoist);
		if (e instanceof HoistEntity h && h.isAlive()) {
			be.lost = 0;
			return h;
		}
		// no está (quizá en un trozo sin cargar): al rato se da por perdida
		if (++be.lost > 200) {
			be.hoist = null;
			be.lost = 0;
			be.markDirty();
		}
		return null;
	}

	/** Cada tic: mueve lo que cuelga según las rpm. */
	static void drive(ServerWorld world, BlockPos pos, MachineBlockEntity be, float rpm, Vec3d tip) {
		HoistEntity h = hoist(world, be);
		if (h == null) {
			return;
		}
		if (Math.abs(h.getX() - tip.x) > 1e-3 || Math.abs(h.getZ() - tip.z) > 1e-3) {
			h.setPosition(tip.x, h.getY(), tip.z);
		}
		if (rpm > 0.01F) {
			h.wantDy = (be.lifting ? 1.0 : -1.0) * HoistEntity.SPEED * rpm / 16.0F;
			if (world.getTime() % 12 == 0) {
				world.playSound(null, pos, SoundEvents.BLOCK_LADDER_STEP, SoundCategory.BLOCKS, 0.3F, 0.6F);
			}
		}
	}

	/** Cambia entre subir y bajar. */
	static void toggle(MachineBlockEntity be, @Nullable PlayerEntity player) {
		be.setLifting(!be.lifting);
		if (player != null) {
			player.sendMessage(Text.translatable(be.lifting ? "message.evolvedwarfare.hoist.up" : "message.evolvedwarfare.hoist.down")
					.formatted(Formatting.GOLD), true);
		}
	}

	static void release(ServerWorld world, BlockState state, MachineBlockEntity be) {
		HoistEntity h = hoist(world, be);
		if (h != null) {
			h.breakOff();
		}
	}
}
