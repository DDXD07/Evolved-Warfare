package com.evolvedwarfare.block;

import com.evolvedwarfare.work.Profession;
import com.evolvedwarfare.work.WorkArea;
import com.mojang.serialization.MapCodec;
import java.util.List;
import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * Puesto de trabajo de un oficio. Clic derecho: su menú (almacén, estado y botón para asignar un recluta).
 * El frente (el lado del dibujo) mira hacia quien lo pone; la mina y la excavación se hacen por detrás.
 */
public class WorkSiteBlock extends BlockWithEntity {
	public static final DirectionProperty FACING = Properties.HORIZONTAL_FACING;
	private static final int GRAY = 0xAAAAAA;

	private final Profession profession;

	public WorkSiteBlock(Profession profession, Settings settings) {
		super(settings);
		this.profession = profession;
		setDefaultState(getStateManager().getDefaultState().with(FACING, Direction.NORTH));
	}

	public Profession getProfession() {
		return profession;
	}

	@Override
	protected MapCodec<? extends BlockWithEntity> getCodec() {
		return createCodec(settings -> new WorkSiteBlock(profession, settings));
	}

	@Override
	protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
		builder.add(FACING);
	}

	@Override
	public BlockState getPlacementState(ItemPlacementContext ctx) {
		return getDefaultState().with(FACING, ctx.getHorizontalPlayerFacing().getOpposite());
	}

	@Override
	protected BlockState rotate(BlockState state, BlockRotation rotation) {
		return state.with(FACING, rotation.rotate(state.get(FACING)));
	}

	@Override
	protected BlockState mirror(BlockState state, BlockMirror mirror) {
		return state.rotate(mirror.getRotation(state.get(FACING)));
	}

	@Override
	protected BlockRenderType getRenderType(BlockState state) {
		return BlockRenderType.MODEL;
	}

	@Nullable
	@Override
	public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
		return new WorkSiteBlockEntity(pos, state);
	}

	@Override
	public void onPlaced(World world, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
		super.onPlaced(world, pos, state, placer, stack);
		if (!world.isClient && placer instanceof PlayerEntity player && world.getBlockEntity(pos) instanceof WorkSiteBlockEntity site) {
			site.setOwner(player.getUuid());
			// diseño al azar para su edificio, y se abre el menú para elegirlo
			site.initSeed(world.getRandom());
			if (player instanceof ServerPlayerEntity serverPlayer) {
				site.openBlueprint(serverPlayer);
			}
		}
	}

	@Override
	protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
		if (!world.isClient && world.getBlockEntity(pos) instanceof WorkSiteBlockEntity site) {
			if (site.canAccess(player)) {
				player.openHandledScreen(site);
			} else if (site.getProfession() == Profession.MERCHANT && player instanceof ServerPlayerEntity serverPlayer) {
				// la tienda del comerciante está abierta a todos
				site.openShop(serverPlayer);
			} else {
				player.sendMessage(Text.translatable("message.evolvedwarfare.site.not_yours"), true);
			}
		}
		return ActionResult.success(world.isClient);
	}

	@Override
	protected void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
		if (!state.isOf(newState.getBlock())) {
			if (world.getBlockEntity(pos) instanceof WorkSiteBlockEntity site) {
				ItemScatterer.spawn(world, pos, site);
				site.onBroken();
				world.updateComparators(pos, this);
			}
		}
		super.onStateReplaced(state, world, pos, newState, moved);
	}

	@Override
	protected boolean hasComparatorOutput(BlockState state) {
		return true;
	}

	@Override
	protected int getComparatorOutput(BlockState state, World world, BlockPos pos) {
		return ScreenHandler.calculateComparatorOutput(world.getBlockEntity(pos));
	}

	@Override
	public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip, TooltipType options) {
		super.appendTooltip(stack, context, tooltip, options);
		tooltip.add(Text.translatable(profession.translationKey()).withColor(0xE0C060));
		tooltip.add(Text.translatable("profession.evolvedwarfare." + profession.id() + ".info").withColor(GRAY));
		WorkArea area = profession.area();
		if (area.exists()) {
			tooltip.add(Text.translatable(area.descriptionKey(), area.size(), area.size()).withColor(GRAY));
		}
		tooltip.add(Text.translatable("profession.evolvedwarfare." + profession.id() + ".supplies").withColor(GRAY));
	}
}
