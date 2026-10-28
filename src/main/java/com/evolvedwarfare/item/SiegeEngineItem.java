package com.evolvedwarfare.item;

import com.evolvedwarfare.siege.SiegeEngineEntity;
import com.evolvedwarfare.siege.SiegeKind;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.entity.EntityType;
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
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.event.GameEvent;

/** Una máquina de asedio desmontada: clic derecho en el suelo para montarla mirando hacia donde miras. */
public class SiegeEngineItem extends Item {
	private final SiegeKind kind;
	private final Supplier<EntityType<SiegeEngineEntity>> type;

	public SiegeEngineItem(SiegeKind kind, Supplier<EntityType<SiegeEngineEntity>> type, Settings settings) {
		super(settings);
		this.kind = kind;
		this.type = type;
	}

	public SiegeKind getKind() {
		return kind;
	}

	@Override
	public ActionResult useOnBlock(ItemUsageContext context) {
		World world = context.getWorld();
		if (context.getSide() == Direction.DOWN) {
			return ActionResult.FAIL;
		}
		PlayerEntity player = context.getPlayer();
		Vec3d at = Vec3d.ofBottomCenter(context.getSide() == Direction.UP ? context.getBlockPos().up()
				: context.getBlockPos().offset(context.getSide()));
		EntityType<SiegeEngineEntity> entityType = type.get();
		Box box = entityType.getDimensions().getBoxAt(at);
		if (!world.isSpaceEmpty(null, box) || !world.getOtherEntities(null, box).isEmpty()) {
			if (player != null && !world.isClient) {
				player.sendMessage(Text.translatable("message.evolvedwarfare.siege.no_room"), true);
			}
			return ActionResult.FAIL;
		}
		if (world instanceof ServerWorld serverWorld) {
			SiegeEngineEntity engine = entityType.create(serverWorld);
			if (engine == null) {
				return ActionResult.FAIL;
			}
			float yaw = player != null ? player.getYaw() : 0.0F;
			engine.refreshPositionAndAngles(at.x, at.y, at.z, yaw, 0.0F);
			if (player != null) {
				engine.setOwner(player.getUuid());
			}
			serverWorld.spawnEntity(engine);
			world.playSound(null, at.x, at.y, at.z, SoundEvents.BLOCK_WOOD_PLACE, SoundCategory.BLOCKS, 1.0F, 0.7F);
			world.emitGameEvent(player, GameEvent.ENTITY_PLACE, at);
			ItemStack stack = context.getStack();
			if (player == null || !player.getAbilities().creativeMode) {
				stack.decrement(1);
			}
		}
		return ActionResult.success(world.isClient);
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
		super.appendTooltip(stack, context, tooltip, type);
		tooltip.add(Text.translatable("item.evolvedwarfare." + kind.id() + ".tooltip").withColor(0xAAAAAA));
		tooltip.add(Text.translatable("item.evolvedwarfare.siege.controls").withColor(0x777777));
	}
}
