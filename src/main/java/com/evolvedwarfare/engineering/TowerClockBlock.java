package com.evolvedwarfare.engineering;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.Properties;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.BlockView;

/**
 * El reloj de torre: la esfera mira hacia quien lo pone y el eje le entra por detrás o por abajo. Mientras gira marca la
 * hora del día; parado, se queda en la hora en que se paró. A cada hora en punto da tantas campanadas como la hora (de 1 a
 * 12) y, mientras suenan, da señal de redstone.
 */
public class TowerClockBlock extends MachineBlock {
	public static final MapCodec<TowerClockBlock> CODEC = createCodec(TowerClockBlock::new);
	public static final float IMPACT = 0.5F;
	/** Tics entre campanadas. */
	public static final int STROKE = 30;

	public TowerClockBlock(Settings settings) {
		super(settings);
		setDefaultState(getStateManager().getDefaultState().with(Properties.HORIZONTAL_FACING, Direction.NORTH).with(Properties.POWERED, false));
	}

	@Override
	protected MapCodec<? extends BlockWithEntity> getCodec() {
		return CODEC;
	}

	@Override
	protected boolean drawnByRenderer() {
		return false;
	}

	@Override
	protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
		builder.add(Properties.HORIZONTAL_FACING, Properties.POWERED);
	}

	@Override
	public BlockState getPlacementState(ItemPlacementContext ctx) {
		return getDefaultState().with(Properties.HORIZONTAL_FACING, ctx.getHorizontalPlayerFacing().getOpposite());
	}

	@Override
	protected BlockState rotate(BlockState state, BlockRotation rotation) {
		return state.with(Properties.HORIZONTAL_FACING, rotation.rotate(state.get(Properties.HORIZONTAL_FACING)));
	}

	@Override
	protected float machineImpact() {
		return IMPACT;
	}

	@Override
	public boolean hasAxle(BlockState state, Direction face) {
		return face == Direction.DOWN || face == state.get(Properties.HORIZONTAL_FACING).getOpposite();
	}

	@Override
	public Direction.Axis axis(BlockState state) {
		return null;
	}

	/** La hora (0 a 23) de ese momento del día (el día empieza a las 6). */
	public static int hourOf(long timeOfDay) {
		return (int) ((Math.floorMod(timeOfDay, 24000L) / 1000L + 6L) % 24L);
	}

	/** Las campanadas de esa hora: de 1 a 12. */
	public static int strokes(int hour) {
		int h = hour % 12;
		return h == 0 ? 12 : h;
	}

	/** Empieza a dar campanadas. */
	public static void chime(MachineBlockEntity be, int strokes) {
		be.hits = strokes;
		be.progress = STROKE;
		be.markDirty();
	}

	@Override
	public void work(ServerWorld world, BlockPos pos, BlockState state, MachineBlockEntity be, float rpm) {
		boolean running = rpm > 0.01F;
		long time = Math.floorMod(world.getTimeOfDay(), 24000L);
		if (running) {
			be.phase = time;
			int hour = hourOf(time);
			if (be.target >= 0 && hour != be.target) {
				chime(be, strokes(hour));
			}
			be.target = hour;
		} else {
			be.target = -1;
		}
		if (running != be.running) {
			be.running = running;
			be.changed();
		}
		if (be.hits > 0) {
			be.progress += 1.0F;
			if (be.progress >= STROKE) {
				be.progress = 0;
				be.hits--;
				world.playSound(null, pos, SoundEvents.BLOCK_BELL_USE, SoundCategory.BLOCKS, 3.0F, 0.75F);
			}
		}
		boolean powered = be.hits > 0;
		if (powered != state.get(Properties.POWERED)) {
			world.setBlockState(pos, state.with(Properties.POWERED, powered), Block.NOTIFY_ALL);
		}
	}

	@Override
	protected boolean emitsRedstonePower(BlockState state) {
		return true;
	}

	@Override
	protected int getWeakRedstonePower(BlockState state, BlockView world, BlockPos pos, Direction direction) {
		return state.get(Properties.POWERED) ? 15 : 0;
	}
}
