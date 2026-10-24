package com.evolvedwarfare.item;

import com.evolvedwarfare.blueprint.BlueprintLibrary;
import com.evolvedwarfare.blueprint.BlueprintPlacer;
import com.evolvedwarfare.blueprint.BlueprintRef;
import com.evolvedwarfare.blueprint.StoredBlueprint;
import com.evolvedwarfare.registry.ModComponents;
import java.util.List;
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
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.jetbrains.annotations.Nullable;

/**
 * Plano: lleva un plano de la biblioteca. Ponlo en los suministros de un constructor para que lo construya; en
 * creativo, clic derecho en el suelo lo coloca al instante (detrás de donde haces clic, mirándote).
 */
public class BlueprintItem extends Item {
	public BlueprintItem(Settings settings) {
		super(settings);
	}

	@Nullable
	public static BlueprintRef ref(ItemStack stack) {
		return stack.get(ModComponents.BLUEPRINT);
	}

	public static ItemStack create(Item item, StoredBlueprint blueprint) {
		ItemStack stack = new ItemStack(item);
		stack.set(ModComponents.BLUEPRINT, BlueprintRef.of(blueprint));
		return stack;
	}

	/** Nombre del plano (los del mod vienen traducidos). */
	public static Text nameOf(BlueprintRef ref) {
		return ref.builtin() ? Text.translatable(ref.name()) : Text.literal(ref.name());
	}

	@Override
	public Text getName(ItemStack stack) {
		BlueprintRef ref = ref(stack);
		return ref == null ? super.getName(stack) : Text.translatable("item.evolvedwarfare.blueprint.named", nameOf(ref));
	}

	@Override
	public ActionResult useOnBlock(ItemUsageContext context) {
		PlayerEntity player = context.getPlayer();
		BlueprintRef ref = ref(context.getStack());
		if (player == null || ref == null) {
			return ActionResult.PASS;
		}
		if (!player.isCreative()) {
			if (!context.getWorld().isClient) {
				player.sendMessage(Text.translatable("message.evolvedwarfare.blueprint.give_builder"), true);
			}
			return ActionResult.PASS;
		}
		if (context.getWorld() instanceof ServerWorld world) {
			StoredBlueprint stored = BlueprintLibrary.get(world.getServer(), ref.id());
			if (stored == null) {
				player.sendMessage(Text.translatable("message.evolvedwarfare.blueprint.missing"), true);
				return ActionResult.FAIL;
			}
			BlockPos clicked = context.getBlockPos();
			BlockPos post = context.getSide() == Direction.UP ? clicked.up() : clicked.offset(context.getSide());
			Direction facing = player.getHorizontalFacing().getOpposite();
			int placed = BlueprintPlacer.placeInstantly(world, stored, post, facing);
			world.playSound(null, post, SoundEvents.BLOCK_ANVIL_PLACE, SoundCategory.BLOCKS, 0.6F, 1.2F);
			player.sendMessage(Text.translatable("message.evolvedwarfare.blueprint.placed", nameOf(ref), placed), true);
		}
		return ActionResult.success(context.getWorld().isClient);
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
		BlueprintRef ref = ref(stack);
		if (ref == null) {
			tooltip.add(Text.translatable("item.evolvedwarfare.blueprint.empty").withColor(0xAAAAAA));
		} else {
			tooltip.add(Text.translatable("item.evolvedwarfare.blueprint.size", ref.sizeX(), ref.sizeY(), ref.sizeZ(), ref.blocks())
					.withColor(0xAAAAAA));
			tooltip.add(Text.translatable("item.evolvedwarfare.blueprint.help").withColor(0x777777));
		}
		super.appendTooltip(stack, context, tooltip, type);
	}
}
