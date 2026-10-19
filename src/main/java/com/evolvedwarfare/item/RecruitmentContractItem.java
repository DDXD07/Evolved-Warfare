package com.evolvedwarfare.item;

import com.evolvedwarfare.entity.RecruitEntity;
import com.evolvedwarfare.registry.ModEntities;
import java.util.List;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Contrato de reclutamiento: clic derecho en el suelo y aparece un recluta a tu servicio. */
public class RecruitmentContractItem extends Item {
	private static final int GRAY = 0xAAAAAA;

	public RecruitmentContractItem(Settings settings) {
		super(settings);
	}

	@Override
	public ActionResult useOnBlock(ItemUsageContext context) {
		World world = context.getWorld();
		if (world.isClient) {
			return ActionResult.SUCCESS;
		}
		PlayerEntity player = context.getPlayer();
		BlockPos pos = context.getBlockPos().offset(context.getSide());
		RecruitEntity recruit = ModEntities.RECRUIT.create(world);
		if (recruit == null) {
			return ActionResult.FAIL;
		}
		float yaw = player != null ? player.getYaw() + 180.0F : 0.0F;
		recruit.refreshPositionAndAngles(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, yaw, 0.0F);
		recruit.randomizeAppearance(world.getRandom());
		if (player != null) {
			recruit.setOwner(player);
		}
		world.spawnEntity(recruit);
		context.getStack().decrementUnlessCreative(1, player);
		if (player != null) {
			player.sendMessage(Text.translatable("message.evolvedwarfare.recruited", recruit.getName()), true);
		}
		return ActionResult.CONSUME;
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
		tooltip.add(Text.translatable("item.evolvedwarfare.recruitment_contract.tooltip.0").withColor(GRAY));
		tooltip.add(Text.translatable("item.evolvedwarfare.recruitment_contract.tooltip.1").withColor(GRAY));
		super.appendTooltip(stack, context, tooltip, type);
	}
}
