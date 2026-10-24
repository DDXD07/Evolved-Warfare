package com.evolvedwarfare.item;

import com.evolvedwarfare.network.DiplomacyNetworking;
import java.util.List;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

/** Sello de embajador: clic derecho para abrir la diplomacia (reputación, tributos, treguas, alianzas, mercenarios). */
public class AmbassadorSealItem extends Item {
	public AmbassadorSealItem(Settings settings) {
		super(settings);
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
		ItemStack stack = user.getStackInHand(hand);
		if (!world.isClient && user instanceof ServerPlayerEntity player) {
			DiplomacyNetworking.open(player);
			world.playSound(null, user.getBlockPos(), SoundEvents.ITEM_BOOK_PAGE_TURN, SoundCategory.PLAYERS, 1.0F, 1.0F);
		}
		return TypedActionResult.success(stack, world.isClient);
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
		tooltip.add(Text.translatable("item.evolvedwarfare.ambassador_seal.tooltip").withColor(0xAAAAAA));
		super.appendTooltip(stack, context, tooltip, type);
	}
}
