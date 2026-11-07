package com.evolvedwarfare.gametest;

import com.evolvedwarfare.pickup.Pickup;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.GameTestException;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.Vec3d;

/** Recoger a mano: pasar por encima no recoge; agachado o con clic derecho, sí. */
public class PickupGameTests implements FabricGameTest {
	@GameTest(templateName = EMPTY_STRUCTURE)
	public void pickupNeedsSneakOrClick(TestContext ctx) {
		ServerPlayerEntity player = (ServerPlayerEntity) ctx.createMockCreativeServerPlayerInWorld();
		Vec3d at = ctx.getAbsolute(new Vec3d(1.5, 2, 1.5));
		player.refreshPositionAndAngles(at.x, at.y, at.z, 0, 0);
		player.getInventory().clear();
		ItemEntity a = new ItemEntity(ctx.getWorld(), at.x, at.y, at.z, new ItemStack(Items.DIAMOND, 3));
		a.setPickupDelay(0);
		ctx.getWorld().spawnEntity(a);
		player.setSneaking(false);
		a.onPlayerCollision(player);
		boolean walkedOver = a.isAlive();
		player.setSneaking(true);
		a.onPlayerCollision(player);
		boolean sneaked = a.isRemoved();
		player.setSneaking(false);
		ItemEntity b = new ItemEntity(ctx.getWorld(), at.x, at.y, at.z, new ItemStack(Items.EMERALD, 2));
		b.setPickupDelay(0);
		ctx.getWorld().spawnEntity(b);
		boolean clicked = Pickup.pick(player, b.getId()) && b.isRemoved();
		if (!(walkedOver && sneaked && clicked)) {
			throw new GameTestException("Recoger a mano: al pasar " + walkedOver + ", agachado " + sneaked + ", clic " + clicked);
		}
		ctx.complete();
	}

	/** En el agua no flota: se hunde. */
	@GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 120)
	public void droppedItemsSinkInWater(TestContext ctx) {
		for (int y = 1; y <= 4; y++) {
			ctx.setBlockState(new net.minecraft.util.math.BlockPos(1, y, 1), net.minecraft.block.Blocks.WATER);
		}
		Vec3d at = ctx.getAbsolute(new Vec3d(1.5, 4.5, 1.5));
		ItemEntity item = new ItemEntity(ctx.getWorld(), at.x, at.y, at.z, new ItemStack(Items.IRON_INGOT));
		item.setVelocity(Vec3d.ZERO);
		ctx.getWorld().spawnEntity(item);
		double start = item.getY();
		ctx.runAtTick(80, () -> {
			if (!(item.getY() < start - 1.5)) {
				throw new GameTestException("Lo tirado flota en el agua: de " + start + " a " + item.getY());
			}
			item.discard();
			ctx.complete();
		});
	}
}
