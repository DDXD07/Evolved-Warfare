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
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.event.GameEvent;

/**
 * Una máquina de asedio desmontada: clic derecho en el suelo para montarla mirando hacia donde miras.
 *
 * <p>Solo no cabe si hay bloques sólidos en su sitio u otra máquina (o una barca, vagoneta...). Quien la coloca no
 * cuenta: si la caja de la máquina le pisa, se pone delante de él; las criaturas que estén en medio se apartan solas.</p>
 */
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
		BlockPos clicked = context.getBlockPos();
		// en hierba alta, nieve fina, flores...: en ese mismo sitio (no un bloque más arriba)
		boolean replaceable = world.getBlockState(clicked).isReplaceable();
		if (context.getSide() == Direction.DOWN && !replaceable) {
			return ActionResult.FAIL;
		}
		PlayerEntity player = context.getPlayer();
		Vec3d at = Vec3d.ofBottomCenter(replaceable ? clicked : clicked.offset(context.getSide()));
		EntityType<SiegeEngineEntity> entityType = type.get();
		Box box = entityType.getDimensions().getBoxAt(at);
		if (player != null && box.intersects(player.getBoundingBox())) {
			// la máquina es grande: si caería encima de quien la pone, se aparta hacia delante
			Vec3d forward = Vec3d.fromPolar(0.0F, player.getYaw());
			for (int i = 0; i < 16 && box.intersects(player.getBoundingBox()); i++) {
				at = at.add(forward.multiply(0.25));
				box = entityType.getDimensions().getBoxAt(at);
			}
		}
		Box inside = box.contract(1.0E-3);
		boolean blocked = !world.isSpaceEmpty(null, inside)
				|| !world.getOtherEntities(player, inside, e -> e instanceof SiegeEngineEntity || e.isCollidable()).isEmpty();
		if (blocked) {
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
