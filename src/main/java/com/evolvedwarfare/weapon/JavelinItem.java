package com.evolvedwarfare.weapon;

import java.util.List;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.stat.Stats;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.UseAction;
import net.minecraft.world.World;

/** Jabalina: mantén pulsado clic derecho y suelta para lanzarla (como el tridente). Luego se recoge. */
public class JavelinItem extends Item {
	private static final int MIN_DRAW = 8;

	public JavelinItem(Settings settings) {
		super(settings);
	}

	@Override
	public UseAction getUseAction(ItemStack stack) {
		return UseAction.SPEAR;
	}

	@Override
	public int getMaxUseTime(ItemStack stack, LivingEntity user) {
		return 72000;
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
		user.setCurrentHand(hand);
		return TypedActionResult.consume(user.getStackInHand(hand));
	}

	@Override
	public void onStoppedUsing(ItemStack stack, World world, LivingEntity user, int remainingUseTicks) {
		if (!(user instanceof PlayerEntity player)) {
			return;
		}
		int drawn = getMaxUseTime(stack, user) - remainingUseTicks;
		if (drawn < MIN_DRAW) {
			return;
		}
		if (!world.isClient) {
			JavelinEntity javelin = new JavelinEntity(world, player, stack.copyWithCount(1));
			float power = Math.min(1.0F, drawn / 20.0F);
			javelin.setVelocity(player, player.getPitch(), player.getYaw(), 0.0F, 1.2F + 1.4F * power, 1.0F);
			if (player.getAbilities().creativeMode) {
				javelin.pickupType = PersistentProjectileEntity.PickupPermission.CREATIVE_ONLY;
			} else {
				stack.decrement(1);
			}
			world.spawnEntity(javelin);
			world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_ARROW_SHOOT, SoundCategory.PLAYERS, 1.0F, 0.6F);
		}
		player.incrementStat(Stats.USED.getOrCreateStat(this));
	}

	@Override
	public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip, TooltipType options) {
		super.appendTooltip(stack, context, tooltip, options);
		tooltip.add(Text.translatable("weapon.evolvedwarfare.javelin.tip").withColor(0xAAAAAA));
	}
}
