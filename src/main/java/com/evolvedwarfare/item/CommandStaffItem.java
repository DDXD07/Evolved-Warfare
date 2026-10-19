package com.evolvedwarfare.item;

import com.evolvedwarfare.entity.AbstractNpcEntity;
import com.evolvedwarfare.entity.NpcOrder;
import com.evolvedwarfare.entity.NpcSides;
import com.evolvedwarfare.entity.SoldierEntity;
import java.util.List;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Bastón de mando: órdenes para todos tus NPCs a menos de 32 bloques.
 * <ul>
 *   <li>Clic derecho al aire: te siguen. Con Mayús: se quedan quietos.</li>
 *   <li>Clic derecho en un bloque: van allí y lo vigilan.</li>
 *   <li>Clic derecho en un enemigo: tus soldados lo atacan.</li>
 * </ul>
 */
public class CommandStaffItem extends Item {
	private static final double RANGE = 32.0;
	private static final int GRAY = 0xAAAAAA;

	public CommandStaffItem(Settings settings) {
		super(settings);
	}

	private static List<AbstractNpcEntity> followers(World world, PlayerEntity player) {
		return world.getEntitiesByClass(AbstractNpcEntity.class, player.getBoundingBox().expand(RANGE),
				npc -> npc.isAlive() && npc.isOwner(player));
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
		ItemStack stack = user.getStackInHand(hand);
		if (!world.isClient) {
			NpcOrder order = user.isSneaking() ? NpcOrder.STAY : NpcOrder.FOLLOW;
			List<AbstractNpcEntity> npcs = followers(world, user);
			for (AbstractNpcEntity npc : npcs) {
				npc.setOrder(order);
			}
			user.sendMessage(Text.translatable(order == NpcOrder.FOLLOW
					? "message.evolvedwarfare.staff.follow" : "message.evolvedwarfare.staff.stay", npcs.size()), true);
		}
		return TypedActionResult.success(stack, world.isClient);
	}

	@Override
	public ActionResult useOnBlock(ItemUsageContext context) {
		World world = context.getWorld();
		PlayerEntity player = context.getPlayer();
		if (player == null) {
			return ActionResult.PASS;
		}
		if (!world.isClient) {
			BlockPos post = context.getBlockPos().offset(context.getSide());
			List<AbstractNpcEntity> npcs = followers(world, player);
			for (AbstractNpcEntity npc : npcs) {
				npc.setHomeAt(post);
			}
			player.sendMessage(Text.translatable("message.evolvedwarfare.staff.guard", npcs.size()), true);
		}
		return ActionResult.success(world.isClient);
	}

	@Override
	public ActionResult useOnEntity(ItemStack stack, PlayerEntity user, LivingEntity entity, Hand hand) {
		World world = user.getWorld();
		if (NpcSides.allies(user, entity)) {
			return ActionResult.PASS;
		}
		if (!world.isClient) {
			int count = 0;
			for (AbstractNpcEntity npc : followers(world, user)) {
				if (npc instanceof SoldierEntity soldier && soldier.fights()) {
					soldier.setTarget(entity);
					count++;
				}
			}
			user.sendMessage(Text.translatable("message.evolvedwarfare.staff.attack", count, entity.getName()), true);
		}
		return ActionResult.success(world.isClient);
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
		for (int i = 0; i < 4; i++) {
			tooltip.add(Text.translatable("item.evolvedwarfare.command_staff.tooltip." + i).withColor(GRAY));
		}
		super.appendTooltip(stack, context, tooltip, type);
	}
}
