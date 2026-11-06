package com.evolvedwarfare.backpack;

import java.util.List;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ContainerComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Una mochila (cinco tiers: 10, 18, 27, 36 y 54 huecos). Se lleva en el hueco de la espalda; lo que lleva dentro va en el
 * componente de contenedor del objeto (así cae entera al morir y se conserva al mejorarla en la mesa de herrería). Se
 * puede teñir, y agachado se pone en el suelo como un cofre.
 */
public class BackpackItem extends Item {
	private final int tier;

	public BackpackItem(int tier, Settings settings) {
		super(settings);
		this.tier = tier;
	}

	public int tier() {
		return tier;
	}

	public int slots() {
		return Backpacks.SIZES[tier];
	}

	public static int slots(ItemStack stack) {
		return stack.getItem() instanceof BackpackItem b ? b.slots() : 0;
	}

	/** Cuántos huecos lleva ocupados. */
	public static int used(ItemStack stack) {
		ContainerComponent c = stack.get(DataComponentTypes.CONTAINER);
		return c == null ? 0 : (int) c.streamNonEmpty().count();
	}

	/** Clic derecho: si no llevas mochila, te la pones a la espalda. */
	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
		ItemStack stack = user.getStackInHand(hand);
		if (Backpacks.worn(user).isEmpty()) {
			if (!world.isClient) {
				Backpacks.setWorn(user, stack.copy());
				user.setStackInHand(hand, ItemStack.EMPTY);
				world.playSound(null, user.getBlockPos(), SoundEvents.ITEM_ARMOR_EQUIP_LEATHER.value(), SoundCategory.PLAYERS, 1.0F, 1.0F);
			}
			return TypedActionResult.success(ItemStack.EMPTY, world.isClient);
		}
		return TypedActionResult.pass(stack);
	}

	/** Agachado sobre un bloque: la deja en el suelo (como un cofre). */
	@Override
	public ActionResult useOnBlock(ItemUsageContext context) {
		PlayerEntity player = context.getPlayer();
		if (player == null || !player.isSneaking()) {
			return ActionResult.PASS;
		}
		BlockPos pos = context.getBlockPos().offset(context.getSide());
		if (context.getWorld() instanceof ServerWorld world) {
			if (!BackpackBlock.place(world, pos, context.getStack().copy(), player.getHorizontalFacing().getOpposite())) {
				return ActionResult.FAIL;
			}
			context.getStack().decrementUnlessCreative(1, player);
		}
		return ActionResult.success(context.getWorld().isClient);
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
		tooltip.add(Text.translatable("item.evolvedwarfare.backpack.slots", used(stack), slots()).formatted(Formatting.GOLD));
		tooltip.add(Text.translatable("item.evolvedwarfare.backpack.desc").formatted(Formatting.GRAY));
	}

	@Override
	public boolean canBeNested() {
		return false;
	}
}
