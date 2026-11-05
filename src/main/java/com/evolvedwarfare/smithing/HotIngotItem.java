package com.evolvedwarfare.smithing;

import com.evolvedwarfare.registry.ModComponents;
import java.util.List;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;

/** Un lingote al rojo (del alto horno o el horno): humea y, al minuto, se enfría y vuelve a ser un lingote. */
public class HotIngotItem extends Item {
	public static final int SECONDS = 60;
	public final ForgeMaterial material;
	private final java.util.function.Supplier<Item> cold;

	public HotIngotItem(ForgeMaterial material, java.util.function.Supplier<Item> cold, Settings settings) {
		super(settings);
		this.material = material;
		this.cold = cold;
	}

	public Item cold() {
		return cold.get();
	}

	@Override
	public void inventoryTick(ItemStack stack, World world, Entity entity, int slot, boolean selected) {
		if (world.isClient) {
			if (selected && world.random.nextInt(6) == 0) {
				world.addParticle(ParticleTypes.SMOKE, entity.getX(), entity.getEyeY() - 0.4, entity.getZ(), 0.0, 0.03, 0.0);
			}
			return;
		}
		if (world.getTime() % 20 != 0) {
			return;
		}
		int heat = stack.getOrDefault(ModComponents.HEAT, SECONDS) - 1;
		if (heat > 0) {
			stack.set(ModComponents.HEAT, heat);
			return;
		}
		if (entity instanceof PlayerEntity player) {
			player.getInventory().setStack(slot, new ItemStack(cold(), stack.getCount()));
			player.sendMessage(Text.translatable("message.evolvedwarfare.smithing.cooled").formatted(Formatting.GRAY), true);
			world.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.PLAYERS, 0.4F, 1.4F);
		}
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
		tooltip.add(Text.translatable("tooltip.evolvedwarfare.hot_ingot", stack.getOrDefault(ModComponents.HEAT, SECONDS)).formatted(Formatting.GOLD));
	}
}
