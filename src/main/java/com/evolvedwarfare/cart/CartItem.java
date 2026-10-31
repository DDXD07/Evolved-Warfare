package com.evolvedwarfare.cart;

import com.evolvedwarfare.registry.ModEntities;
import java.util.List;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.event.GameEvent;

/** La carreta en la mano: clic derecho en el suelo la pone, con las varas hacia ti. Necesita sitio (3x3). */
public class CartItem extends Item {
	public CartItem(Settings settings) {
		super(settings);
	}

	@Override
	public ActionResult useOnBlock(ItemUsageContext context) {
		World world = context.getWorld();
		PlayerEntity player = context.getPlayer();
		Vec3d at = context.getSide() == Direction.UP ? context.getHitPos()
				: Vec3d.ofBottomCenter(context.getBlockPos().offset(context.getSide()));
		CartEntity cart = ModEntities.CART.create(world);
		if (cart == null) {
			return ActionResult.FAIL;
		}
		float yaw = player == null ? 0.0F : player.getYaw() + 180.0F;
		cart.refreshPositionAndAngles(at.x, at.y, at.z, yaw, 0.0F);
		if (!world.isSpaceEmpty(cart, cart.getBoundingBox().contract(0.05))) {
			if (player != null && !world.isClient) {
				player.sendMessage(Text.translatable("message.evolvedwarfare.cart.no_room").formatted(Formatting.RED), true);
			}
			return ActionResult.FAIL;
		}
		if (!world.isClient) {
			world.spawnEntity(cart);
			world.emitGameEvent(player, GameEvent.ENTITY_PLACE, cart.getBlockPos());
			world.playSound(null, cart.getX(), cart.getY(), cart.getZ(), SoundEvents.BLOCK_WOOD_PLACE, SoundCategory.BLOCKS, 1.0F, 0.8F);
			context.getStack().decrementUnlessCreative(1, player);
		}
		return ActionResult.success(world.isClient);
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
		tooltip.add(Text.translatable("item.evolvedwarfare.cart.tooltip").formatted(Formatting.GRAY));
	}
}
