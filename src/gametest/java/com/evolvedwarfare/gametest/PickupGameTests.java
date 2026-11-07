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
}
