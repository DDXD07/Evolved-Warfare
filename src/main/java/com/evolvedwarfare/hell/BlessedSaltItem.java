package com.evolvedwarfare.hell;

import java.util.List;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

/** Sal bendita: echada sobre uno mismo quita corrupción y una maldición; en un espíritu de los condenados, lo libera. */
public class BlessedSaltItem extends Item {
	public BlessedSaltItem(Settings settings) {
		super(settings);
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
		ItemStack stack = user.getStackInHand(hand);
		if (world instanceof ServerWorld sw && user instanceof ServerPlayerEntity sp) {
			NetherClimate.addCorruption(sp, -15);
			for (var c : Curses.all()) {
				if (sp.removeStatusEffect(c)) {
					break;
				}
			}
			sw.spawnParticles(ParticleTypes.END_ROD, sp.getX(), sp.getBodyY(0.7), sp.getZ(), 24, 0.4, 0.6, 0.4, 0.03);
			sw.playSound(null, sp.getBlockPos(), SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 1.0F, 1.2F);
			sp.sendMessage(Text.translatable("message.evolvedwarfare.blessed_salt.used").formatted(Formatting.AQUA), true);
			stack.decrementUnlessCreative(1, sp);
			sp.getItemCooldownManager().set(this, 100);
		}
		return TypedActionResult.success(stack, world.isClient);
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
		tooltip.add(Text.translatable("item.evolvedwarfare.blessed_salt.desc").formatted(Formatting.GRAY));
	}
}
