package com.evolvedwarfare.hell;

import java.util.List;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

/** Libro de condena: leerlo hace daño (efectos malos un rato), pero debilita a los señores del abismo cercanos o revela un lugar. */
public class BookOfCondemnationItem extends Item {
	public BookOfCondemnationItem(Settings settings) {
		super(settings);
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
		ItemStack stack = user.getStackInHand(hand);
		if (world instanceof ServerWorld sw && user instanceof ServerPlayerEntity sp) {
			HellStructures.read(sw, sp);
			stack.decrementUnlessCreative(1, sp);
			sp.getItemCooldownManager().set(this, 100);
		}
		return TypedActionResult.success(stack, world.isClient);
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
		tooltip.add(Text.translatable("item.evolvedwarfare.book_of_condemnation.desc").formatted(Formatting.DARK_RED, Formatting.ITALIC));
	}
}
