package com.evolvedwarfare.engineering;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.ItemActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * La polea del elevador: de ella cuelga una plataforma (se le pone con clic derecho con la plataforma en la mano) que sube
 * o baja según gira (clic derecho cambia), a 2 bloques por segundo a 16 rpm. Gasta 2 por rpm.
 */
public class PulleyBlock extends SideShaftMachineBlock {
	public static final MapCodec<PulleyBlock> CODEC = createCodec(PulleyBlock::new);
	public static final float IMPACT = 2.0F;

	public PulleyBlock(Settings settings) {
		super(settings);
	}

	@Override
	protected MapCodec<? extends BlockWithEntity> getCodec() {
		return CODEC;
	}

	@Override
	protected float machineImpact() {
		return IMPACT;
	}

	static Vec3d tip(BlockPos pos) {
		return new Vec3d(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
	}

	@Override
	public void work(ServerWorld world, BlockPos pos, BlockState state, MachineBlockEntity be, float rpm) {
		HoistControl.drive(world, pos, be, rpm, tip(pos));
	}

	/** Cuelga una plataforma nueva debajo; devuelve si se ha podido. */
	public static boolean attachPlatform(ServerWorld world, BlockPos pos, PlayerEntity player) {
		if (!(world.getBlockEntity(pos) instanceof MachineBlockEntity be)) {
			return false;
		}
		if (HoistControl.hoist(world, be) != null) {
			if (player != null) {
				player.sendMessage(Text.translatable("message.evolvedwarfare.hoist.has_platform").formatted(Formatting.YELLOW), true);
			}
			return false;
		}
		HoistEntity h = new HoistEntity(Engineering.LIFT_PLATFORM_ENTITY, world);
		Vec3d tip = tip(pos);
		double y = tip.y - 1.0;
		h.setPosition(tip.x, y, tip.z);
		h.link(pos, (float) tip.y);
		if (!h.fitsAt(tip.x, y, tip.z)) {
			if (player != null) {
				player.sendMessage(Text.translatable("message.evolvedwarfare.hoist.no_room").formatted(Formatting.YELLOW), true);
			}
			return false;
		}
		world.spawnEntity(h);
		be.hoist = h.getUuid();
		be.markDirty();
		return true;
	}

	@Override
	protected ItemActionResult onUseWithItem(ItemStack stack, BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand,
			BlockHitResult hit) {
		if (!stack.isOf(Engineering.LIFT_PLATFORM)) {
			return ItemActionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
		}
		if (world instanceof ServerWorld sw && attachPlatform(sw, pos, player) && !player.isCreative()) {
			stack.decrement(1);
		}
		return ItemActionResult.success(world.isClient);
	}

	@Override
	protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
		if (player.isSneaking()) {
			return super.onUse(state, world, pos, player, hit);
		}
		if (!world.isClient && world.getBlockEntity(pos) instanceof MachineBlockEntity be) {
			HoistControl.toggle(be, player);
		}
		return ActionResult.SUCCESS;
	}

	@Override
	protected void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
		if (!state.isOf(newState.getBlock()) && world instanceof ServerWorld sw && world.getBlockEntity(pos) instanceof MachineBlockEntity be) {
			HoistControl.release(sw, state, be);
		}
		super.onStateReplaced(state, world, pos, newState, moved);
	}
}
