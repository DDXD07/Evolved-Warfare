package com.evolvedwarfare.block;

import com.evolvedwarfare.registry.ModBlocks;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * Lo que hace el torno. La primera vez que se usa busca, pegado a él, un rastrillo o un puente levadizo y se lo
 * aprende entero (bloques del mismo tipo unidos entre sí, hasta {@value #MAX_PARTS}):
 * <ul>
 * <li><b>Rastrillo</b>: abrir lo sube tantos bloques como alto es (si tiene sitio encima); cerrar lo baja hasta que
 * toca el suelo, aplastando a quien esté debajo.</li>
 * <li><b>Puente levadizo</b>: un rectángulo de tablones tumbado (bajado) o de pie (subido). Gira sobre su fila de
 * bisagra: al subirlo, las filas de la punta pasan a apilarse encima de la bisagra, y al bajarlo vuelven a salir hacia
 * donde mira el torno.</li>
 * </ul>
 * Se mueve un bloque (o una fila) cada pocos tics. Si alguien rompe una pieza, se desengancha: otro clic lo vuelve a
 * enganchar.
 */
public class GateWinchBlockEntity extends BlockEntity {
	private static final int MAX_PARTS = 256;
	private static final int PORTCULLIS_STEP = 4;
	private static final int DRAWBRIDGE_STEP = 5;

	private enum Mode {
		NONE,
		PORTCULLIS,
		DRAWBRIDGE
	}

	private Mode mode = Mode.NONE;
	/** Rastrillo: dónde está cada pieza ahora. Puente: la fila de bisagra. (Relativo al torno.) */
	private final List<BlockPos> parts = new ArrayList<>();
	private BlockState partState = Blocks.AIR.getDefaultState();
	/** Rastrillo: altura (bloques) y cuánto está subido. */
	private int height;
	private int raised;
	/** Puente: largo y cuántas filas hay ya de pie (0 = bajado; largo - 1 = subido). */
	private int length;
	private int upright;
	private boolean targetOpen;
	private boolean moving;
	private int cooldown;
	@Nullable
	private UUID owner;

	public GateWinchBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlocks.GATE_WINCH_ENTITY, pos, state);
	}

	public void setOwner(UUID owner) {
		this.owner = owner;
		markDirty();
	}

	private Direction out() {
		BlockState state = getCachedState();
		return state.contains(GateWinchBlock.FACING) ? state.get(GateWinchBlock.FACING) : Direction.NORTH;
	}

	// ------------------------------------------------------------------ uso

	public void use(PlayerEntity player) {
		if (owner != null && !owner.equals(player.getUuid()) && !player.getAbilities().creativeMode) {
			player.sendMessage(Text.translatable("message.evolvedwarfare.winch.not_yours"), true);
			return;
		}
		if (owner == null) {
			owner = player.getUuid();
		}
		if (mode == Mode.NONE || !intact()) {
			if (!link()) {
				player.sendMessage(Text.translatable("message.evolvedwarfare.winch.nothing").formatted(Formatting.RED), true);
				return;
			}
			player.sendMessage(Text.translatable(mode == Mode.PORTCULLIS ? "message.evolvedwarfare.winch.linked_portcullis"
					: "message.evolvedwarfare.winch.linked_drawbridge", count()), true);
		}
		setTarget(!targetOpen);
		player.sendMessage(Text.translatable(targetOpen ? "message.evolvedwarfare.winch.opening" : "message.evolvedwarfare.winch.closing"), true);
	}

	public void onRedstone(boolean powered) {
		if (mode == Mode.NONE || !intact()) {
			if (!link()) {
				return;
			}
		}
		setTarget(powered);
	}

	private void setTarget(boolean open) {
		targetOpen = open;
		moving = true;
		cooldown = 0;
		World world = getWorld();
		if (world != null) {
			world.playSound(null, pos, mode == Mode.PORTCULLIS ? SoundEvents.BLOCK_IRON_DOOR_OPEN : SoundEvents.BLOCK_WOODEN_TRAPDOOR_OPEN,
					SoundCategory.BLOCKS, 1.0F, 0.6F);
		}
		markDirty();
	}

	private int count() {
		return mode == Mode.PORTCULLIS ? parts.size() : parts.size() * length;
	}

	// ------------------------------------------------------------------ enganchar

	private boolean link() {
		World world = getWorld();
		if (world == null) {
			return false;
		}
		mode = Mode.NONE;
		parts.clear();
		for (int dx = -1; dx <= 1; dx++) {
			for (int dy = -1; dy <= 1; dy++) {
				for (int dz = -1; dz <= 1; dz++) {
					BlockPos start = pos.add(dx, dy, dz);
					BlockState state = world.getBlockState(start);
					if (state.getBlock() instanceof PortcullisBlock) {
						if (linkPortcullis(world, start, state)) {
							markDirty();
							return true;
						}
					} else if (state.getBlock() instanceof DrawbridgeBlock) {
						if (linkDrawbridge(world, start, state)) {
							markDirty();
							return true;
						}
					}
				}
			}
		}
		return false;
	}

	private static Set<BlockPos> flood(World world, BlockPos start, Block block) {
		Set<BlockPos> found = new HashSet<>();
		ArrayDeque<BlockPos> queue = new ArrayDeque<>();
		queue.add(start);
		found.add(start);
		while (!queue.isEmpty() && found.size() < MAX_PARTS) {
			BlockPos at = queue.poll();
			for (Direction direction : Direction.values()) {
				BlockPos next = at.offset(direction);
				if (!found.contains(next) && world.getBlockState(next).isOf(block)) {
					found.add(next);
					queue.add(next);
				}
			}
		}
		return found;
	}

	private boolean linkPortcullis(World world, BlockPos start, BlockState state) {
		Set<BlockPos> found = flood(world, start, state.getBlock());
		Map<Long, Integer> columns = new HashMap<>();
		for (BlockPos part : found) {
			columns.merge(BlockPos.asLong(part.getX(), 0, part.getZ()), 1, Integer::sum);
			parts.add(part.subtract(pos));
		}
		partState = state;
		height = columns.values().stream().mapToInt(Integer::intValue).max().orElse(1);
		mode = Mode.PORTCULLIS;
		// si cuelga (debajo hay hueco), está subido
		boolean hanging = canShift(world, -1);
		raised = hanging ? height : 0;
		targetOpen = hanging;
		moving = false;
		return true;
	}

	private boolean linkDrawbridge(World world, BlockPos start, BlockState state) {
		Set<BlockPos> found = flood(world, start, state.getBlock());
		Direction out = out();
		int minY = Integer.MAX_VALUE;
		int maxY = Integer.MIN_VALUE;
		int minK = Integer.MAX_VALUE;
		int maxK = Integer.MIN_VALUE;
		for (BlockPos part : found) {
			int k = part.getX() * out.getOffsetX() + part.getZ() * out.getOffsetZ();
			minY = Math.min(minY, part.getY());
			maxY = Math.max(maxY, part.getY());
			minK = Math.min(minK, k);
			maxK = Math.max(maxK, k);
		}
		List<BlockPos> hinge = new ArrayList<>();
		if (minY == maxY) {
			// tumbado (bajado): la bisagra es la fila más hacia dentro
			for (BlockPos part : found) {
				if (part.getX() * out.getOffsetX() + part.getZ() * out.getOffsetZ() == minK) {
					hinge.add(part);
				}
			}
			length = maxK - minK + 1;
			upright = 0;
			targetOpen = true;
		} else if (minK == maxK) {
			// de pie (subido): la bisagra es la fila de abajo
			for (BlockPos part : found) {
				if (part.getY() == minY) {
					hinge.add(part);
				}
			}
			length = maxY - minY + 1;
			upright = length - 1;
			targetOpen = false;
		} else {
			return false;
		}
		if (length < 2 || hinge.size() * length != found.size()) {
			return false;
		}
		for (BlockPos part : hinge) {
			for (int k = 0; k < length; k++) {
				BlockPos expected = targetOpen ? part.offset(out, k) : part.up(k);
				if (!found.contains(expected)) {
					return false;
				}
			}
		}
		for (BlockPos part : hinge) {
			parts.add(part.subtract(pos));
		}
		partState = state;
		mode = Mode.DRAWBRIDGE;
		moving = false;
		return true;
	}

	// ------------------------------------------------------------------ mover

	public static void tick(World world, BlockPos pos, BlockState state, GateWinchBlockEntity winch) {
		if (!winch.moving || winch.mode == Mode.NONE) {
			return;
		}
		if (winch.cooldown > 0) {
			winch.cooldown--;
			return;
		}
		if (!winch.intact()) {
			winch.mode = Mode.NONE;
			winch.moving = false;
			winch.markDirty();
			return;
		}
		boolean moved = winch.mode == Mode.PORTCULLIS ? winch.stepPortcullis(world) : winch.stepDrawbridge(world);
		if (!moved) {
			winch.moving = false;
		}
		winch.cooldown = winch.mode == Mode.PORTCULLIS ? PORTCULLIS_STEP : DRAWBRIDGE_STEP;
		winch.markDirty();
	}

	/** ¿Siguen las piezas donde deben? */
	private boolean intact() {
		World world = getWorld();
		if (world == null || mode == Mode.NONE) {
			return false;
		}
		for (BlockPos part : currentBlocks()) {
			if (!world.getBlockState(part).isOf(partState.getBlock())) {
				return false;
			}
		}
		return true;
	}

	/** Las posiciones (absolutas) que ocupa ahora. */
	private List<BlockPos> currentBlocks() {
		List<BlockPos> out = new ArrayList<>();
		if (mode == Mode.PORTCULLIS) {
			for (BlockPos part : parts) {
				out.add(pos.add(part));
			}
		} else if (mode == Mode.DRAWBRIDGE) {
			Direction dir = out();
			for (BlockPos part : parts) {
				BlockPos hinge = pos.add(part);
				for (int k = 0; k < length - upright; k++) {
					out.add(hinge.offset(dir, k));
				}
				for (int h = 1; h <= upright; h++) {
					out.add(hinge.up(h));
				}
			}
		}
		return out;
	}

	private static boolean free(World world, BlockPos at) {
		BlockState state = world.getBlockState(at);
		return state.isAir() || state.isReplaceable();
	}

	/** ¿Puede el rastrillo moverse un bloque arriba (1) o abajo (-1)? */
	private boolean canShift(World world, int dy) {
		Set<BlockPos> current = new HashSet<>(currentBlocks());
		for (BlockPos at : current) {
			BlockPos next = at.up(dy);
			if (!current.contains(next) && !free(world, next)) {
				return false;
			}
		}
		return true;
	}

	private boolean stepPortcullis(World world) {
		int dy;
		if (targetOpen) {
			if (raised >= height) {
				return false;
			}
			dy = 1;
		} else {
			dy = -1;
		}
		if (!canShift(world, dy)) {
			if (!targetOpen) {
				raised = 0;
			}
			return false;
		}
		Map<BlockPos, BlockState> current = new LinkedHashMap<>();
		for (BlockPos at : currentBlocks()) {
			current.put(at, world.getBlockState(at));
		}
		Map<BlockPos, BlockState> next = new LinkedHashMap<>();
		for (Map.Entry<BlockPos, BlockState> entry : current.entrySet()) {
			next.put(entry.getKey().up(dy), entry.getValue());
		}
		for (Map.Entry<BlockPos, BlockState> entry : next.entrySet()) {
			if (!current.containsKey(entry.getKey())) {
				if (dy < 0) {
					crush(world, entry.getKey());
				}
				world.setBlockState(entry.getKey(), entry.getValue(), Block.NOTIFY_ALL);
			}
		}
		for (BlockPos at : current.keySet()) {
			if (!next.containsKey(at)) {
				world.setBlockState(at, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
			}
		}
		parts.replaceAll(part -> part.up(dy));
		raised = Math.max(0, raised + dy);
		world.playSound(null, pos, SoundEvents.BLOCK_CHAIN_PLACE, SoundCategory.BLOCKS, 0.8F, dy > 0 ? 0.7F : 0.5F);
		return true;
	}

	private boolean stepDrawbridge(World world) {
		Direction dir = out();
		List<BlockPos> from = new ArrayList<>();
		List<BlockPos> to = new ArrayList<>();
		if (targetOpen) {
			if (upright <= 0) {
				return false;
			}
			for (BlockPos part : parts) {
				BlockPos hinge = pos.add(part);
				from.add(hinge.up(upright));
				to.add(hinge.offset(dir, length - upright));
			}
		} else {
			if (upright >= length - 1) {
				return false;
			}
			for (BlockPos part : parts) {
				BlockPos hinge = pos.add(part);
				from.add(hinge.offset(dir, length - 1 - upright));
				to.add(hinge.up(upright + 1));
			}
		}
		for (BlockPos at : to) {
			if (!free(world, at)) {
				return false;
			}
		}
		for (BlockPos at : to) {
			world.setBlockState(at, partState, Block.NOTIFY_ALL);
		}
		for (BlockPos at : from) {
			world.setBlockState(at, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
		}
		upright += targetOpen ? -1 : 1;
		world.playSound(null, pos, SoundEvents.BLOCK_CHAIN_PLACE, SoundCategory.BLOCKS, 0.9F, 0.6F);
		world.playSound(null, pos, SoundEvents.BLOCK_WOOD_PLACE, SoundCategory.BLOCKS, 0.7F, 0.6F);
		return true;
	}

	/** Lo que esté debajo del rastrillo cuando baja se lleva un buen golpe. */
	private static void crush(World world, BlockPos at) {
		for (LivingEntity entity : world.getEntitiesByClass(LivingEntity.class, new Box(at), LivingEntity::isAlive)) {
			entity.damage(world.getDamageSources().fallingBlock(null), 6.0F);
		}
	}

	// ------------------------------------------------------------------ guardar

	@Override
	protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		super.writeNbt(nbt, registries);
		nbt.putString("Mode", mode.name());
		long[] packed = new long[parts.size()];
		for (int i = 0; i < packed.length; i++) {
			packed[i] = parts.get(i).asLong();
		}
		nbt.putLongArray("Parts", packed);
		nbt.put("PartState", NbtHelper.fromBlockState(partState));
		nbt.putInt("Height", height);
		nbt.putInt("Raised", raised);
		nbt.putInt("Length", length);
		nbt.putInt("Upright", upright);
		nbt.putBoolean("TargetOpen", targetOpen);
		nbt.putBoolean("Moving", moving);
		if (owner != null) {
			nbt.putUuid("Owner", owner);
		}
	}

	@Override
	protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		super.readNbt(nbt, registries);
		try {
			mode = Mode.valueOf(nbt.getString("Mode"));
		} catch (IllegalArgumentException e) {
			mode = Mode.NONE;
		}
		parts.clear();
		for (long packed : nbt.getLongArray("Parts")) {
			parts.add(BlockPos.fromLong(packed));
		}
		partState = NbtHelper.toBlockState(registries.getWrapperOrThrow(RegistryKeys.BLOCK), nbt.getCompound("PartState"));
		height = nbt.getInt("Height");
		raised = nbt.getInt("Raised");
		length = nbt.getInt("Length");
		upright = nbt.getInt("Upright");
		targetOpen = nbt.getBoolean("TargetOpen");
		moving = nbt.getBoolean("Moving");
		owner = nbt.containsUuid("Owner") ? nbt.getUuid("Owner") : null;
	}
}
