package com.evolvedwarfare.climate;

import java.util.List;
import net.minecraft.advancement.criterion.Criteria;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsage;
import net.minecraft.item.Items;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.stat.Stats;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.UseAction;
import net.minecraft.world.World;

/**
 * Una bebida en frasco: el agua hervida (frasco de agua de vanilla pasado por el horno o por un caldero al fuego) y los
 * zumos. Quita sed, refresca, puede alimentar un poco y deja el frasco vacío.
 */
public class DrinkItem extends Item {
	private final float thirst;
	private final int food;
	private final float saturation;
	private final float cooling;

	public DrinkItem(float thirst, int food, float saturation, float cooling, Settings settings) {
		super(settings);
		this.thirst = thirst;
		this.food = food;
		this.saturation = saturation;
		this.cooling = cooling;
	}

	public float thirst() {
		return thirst;
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
		return ItemUsage.consumeHeldItem(world, user, hand);
	}

	@Override
	public UseAction getUseAction(ItemStack stack) {
		return UseAction.DRINK;
	}

	@Override
	public int getMaxUseTime(ItemStack stack, LivingEntity user) {
		return 32;
	}

	@Override
	public SoundEvent getDrinkSound() {
		return SoundEvents.ENTITY_GENERIC_DRINK;
	}

	@Override
	public ItemStack finishUsing(ItemStack stack, World world, LivingEntity user) {
		if (user instanceof ServerPlayerEntity player) {
			Criteria.CONSUME_ITEM.trigger(player, stack);
			player.incrementStat(Stats.USED.getOrCreateStat(this));
			if (Thirst.enabled()) {
				Thirst.add(player, thirst);
			}
			if (cooling > 0.0F) {
				Climate.cool(player, cooling);
			}
			if (food > 0) {
				player.getHungerManager().add(food, saturation);
			}
		}
		stack.decrementUnlessCreative(1, user);
		if (stack.isEmpty()) {
			return new ItemStack(Items.GLASS_BOTTLE);
		}
		if (user instanceof PlayerEntity player && !player.isInCreativeMode()) {
			ItemStack bottle = new ItemStack(Items.GLASS_BOTTLE);
			if (!player.getInventory().insertStack(bottle)) {
				player.dropItem(bottle, false);
			}
		}
		return stack;
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
		super.appendTooltip(stack, context, tooltip, type);
		tooltip.add(Text.translatable(getTranslationKey() + ".desc").formatted(Formatting.GRAY));
	}
}
