package com.evolvedwarfare.train;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.BedBlock;
import net.minecraft.block.TallPlantBlock;
import net.minecraft.block.enums.BlockHalf;
import net.minecraft.block.enums.SlabType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.Registries;
import net.minecraft.sound.SoundCategory;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * Una plataforma: un vagón llano de 5 x 9 en el que se construye lo que se quiera (hasta 8 de alto) con clic derecho, y se
 * quita con clic izquierdo, aunque el tren esté en marcha. Lo construido choca, se pisa, y viaja con el tren.
 */
public class FlatcarEntity extends TrainCarEntity {
	public static final int W = 5, L = 9, H = 8;
	public static final double DECK = 1.25;
	public static final double HALF_LENGTH = 4.75;
	private static final TrackedData<NbtCompound> GRID = DataTracker.registerData(FlatcarEntity.class, TrackedDataHandlerRegistry.NBT_COMPOUND);
	private final BlockState[] cells = new BlockState[W * H * L];
	private int version;
	private List<Box> boxes = List.of();
	private int top;
	@Nullable
	private UUID locomotive;

	public FlatcarEntity(EntityType<?> type, World world) {
		super(type, world);
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {
		builder.add(GRID, new NbtCompound());
	}

	@Nullable
	public UUID locomotive() {
		return locomotive;
	}

	public void setLocomotive(@Nullable UUID loco) {
		this.locomotive = loco;
	}

	static int index(int x, int y, int z) {
		return (y * L + z) * W + x;
	}

	public static boolean inside(int x, int y, int z) {
		return x >= 0 && x < W && y >= 0 && y < H && z >= 0 && z < L;
	}

	@Nullable
	public BlockState get(int x, int y, int z) {
		return inside(x, y, z) ? cells[index(x, y, z)] : null;
	}

	/** Pone (o quita, con null) un bloque en la casilla (x, y, z): x 0-4 a lo ancho, y 0-7 desde la plataforma, z 0-8 hacia delante. */
	public void set(int x, int y, int z, @Nullable BlockState state) {
		if (!inside(x, y, z)) {
			return;
		}
		cells[index(x, y, z)] = state == null || state.isAir() ? null : state;
		rebuild();
		if (!getWorld().isClient) {
			dataTracker.set(GRID, write());
		}
	}

	public int blockCount() {
		int n = 0;
		for (BlockState s : cells) {
			n += s == null ? 0 : 1;
		}
		return n;
	}

	private void rebuild() {
		List<Box> out = new ArrayList<>();
		out.add(new Box(-W / 2.0, 0.55, -HALF_LENGTH, W / 2.0, DECK, HALF_LENGTH));
		top = 0;
		for (int y = 0; y < H; y++) {
			for (int z = 0; z < L; z++) {
				for (int x = 0; x < W; x++) {
					BlockState s = cells[index(x, y, z)];
					if (s == null) {
						continue;
					}
					top = Math.max(top, y + 1);
					var shape = s.getCollisionShape(getWorld(), net.minecraft.util.math.BlockPos.ORIGIN);
					if (shape.isEmpty()) {
						continue;
					}
					double ox = x - W / 2.0, oy = DECK + y, oz = z - L / 2.0;
					for (Box b : shape.getBoundingBoxes()) {
						out.add(b.offset(ox, oy, oz));
					}
				}
			}
		}
		boxes = out;
		version++;
		setBoundingBox(calculateBoundingBox());
	}

	@Override
	protected List<Box> localBoxes() {
		return boxes;
	}

	@Override
	protected int boxesVersion() {
		return version;
	}

	@Override
	protected double halfWidth() {
		return W / 2.0;
	}

	@Override
	protected double halfLength() {
		return HALF_LENGTH;
	}

	@Override
	protected double height() {
		return DECK + Math.max(top, 0) + 0.2;
	}

	@Override
	public void onTrackedDataSet(TrackedData<?> data) {
		super.onTrackedDataSet(data);
		if (GRID.equals(data) && getWorld().isClient) {
			read(dataTracker.get(GRID));
		}
	}

	// ------------------------------------------------------------------ construir y quitar

	/** La casilla a la que apunta el jugador (y la de al lado, donde iría un bloque nuevo); null si no apunta a nada. */
	@Nullable
	public int[][] aim(PlayerEntity player) {
		Vec3d eye = toLocal(player.getEyePos());
		Vec3d look = toLocal(player.getEyePos().add(player.getRotationVec(1.0F))).subtract(eye);
		int[] prev = null;
		for (double d = 0; d <= 6.0; d += 0.04) {
			Vec3d p = eye.add(look.multiply(d));
			int x = MathHelper.floor(p.x + W / 2.0), z = MathHelper.floor(p.z + L / 2.0);
			int y = MathHelper.floor(p.y - DECK);
			if (x >= 0 && x < W && z >= 0 && z < L && p.y >= 0.55 && p.y < DECK) {
				// la plataforma: se construye encima
				return prev == null ? null : new int[][] {null, prev};
			}
			if (inside(x, y, z)) {
				if (cells[index(x, y, z)] != null) {
					return new int[][] {{x, y, z}, prev};
				}
				prev = new int[] {x, y, z};
			}
		}
		return null;
	}

	public static boolean buildable(ItemStack stack) {
		if (!(stack.getItem() instanceof BlockItem bi)) {
			return false;
		}
		Block b = bi.getBlock();
		return !(b instanceof BlockEntityProvider) && !(b instanceof DoorBlock) && !(b instanceof BedBlock) && !(b instanceof TallPlantBlock)
				&& b.getDefaultState().getFluidState().isEmpty() && b != Blocks.AIR;
	}

	/** El bloque a poner, mirando hacia el jugador (en el giro del vagón) y con su mitad (losas, escaleras) según dónde apunta. */
	public BlockState placement(Block block, PlayerEntity player, boolean upper) {
		BlockState s = block.getDefaultState();
		float rel = MathHelper.wrapDegrees(player.getYaw() - snap(getYaw()));
		Direction facing = Direction.fromRotation(rel);
		if (s.contains(Properties.HORIZONTAL_FACING)) {
			s = s.with(Properties.HORIZONTAL_FACING, facing.getOpposite());
		}
		if (s.contains(Properties.FACING)) {
			s = s.with(Properties.FACING, facing.getOpposite());
		}
		if (s.contains(Properties.SLAB_TYPE)) {
			s = s.with(Properties.SLAB_TYPE, upper ? SlabType.TOP : SlabType.BOTTOM);
		}
		if (s.contains(Properties.BLOCK_HALF)) {
			s = s.with(Properties.BLOCK_HALF, upper ? BlockHalf.TOP : BlockHalf.BOTTOM);
		}
		if (s.contains(Properties.WATERLOGGED)) {
			s = s.with(Properties.WATERLOGGED, false);
		}
		return s;
	}

	@Override
	public ActionResult interact(PlayerEntity player, Hand hand) {
		ItemStack stack = player.getStackInHand(hand);
		if (stack.isOf(Trains.FLATCAR_ITEM) || stack.isOf(Trains.LOCOMOTIVE_ITEM)) {
			return Trains.couple(this, player, stack);
		}
		if (hand != Hand.MAIN_HAND) {
			return ActionResult.PASS;
		}
		if (stack.isEmpty() && player.isSneaking()) {
			return Trains.uncouple(this, player);
		}
		if (!buildable(stack)) {
			if (stack.getItem() instanceof BlockItem && !getWorld().isClient) {
				player.sendMessage(Text.translatable("message.evolvedwarfare.train.not_buildable").formatted(Formatting.GRAY), true);
			}
			return ActionResult.PASS;
		}
		if (getWorld().isClient) {
			return ActionResult.SUCCESS;
		}
		int[][] aim = aim(player);
		if (aim == null || aim[1] == null) {
			return ActionResult.PASS;
		}
		int[] c = aim[1];
		// que no se meta dentro de nadie
		Box cell = new Box(c[0] - W / 2.0, DECK + c[1], c[2] - L / 2.0, c[0] + 1 - W / 2.0, DECK + c[1] + 1, c[2] + 1 - L / 2.0);
		Vec3d a = toWorld(cell.minX, cell.minY, cell.minZ), b = toWorld(cell.maxX, cell.maxY, cell.maxZ);
		Box worldCell = new Box(a, b).contract(0.05);
		if (!getWorld().getOtherEntities(this, worldCell, e -> !(e instanceof TrainCarEntity) && e.isAlive()).isEmpty()) {
			return ActionResult.FAIL;
		}
		Vec3d eye = toLocal(player.getEyePos());
		boolean upper = eye.y - DECK - c[1] > 0.5 && player.getPitch() < 0 || aim[0] != null && aim[0][1] == c[1];
		BlockState state = placement(((BlockItem) stack.getItem()).getBlock(), player, upper);
		set(c[0], c[1], c[2], state);
		var sound = state.getSoundGroup();
		getWorld().playSound(null, getBlockPos(), sound.getPlaceSound(), SoundCategory.BLOCKS, (sound.getVolume() + 1.0F) / 2.0F, sound.getPitch() * 0.8F);
		stack.decrementUnlessCreative(1, player);
		return ActionResult.CONSUME;
	}

	/** Clic izquierdo: quita el bloque al que apunta (y te lo da). */
	@Override
	public boolean handleAttack(Entity attacker) {
		if (!(attacker instanceof PlayerEntity player) || getWorld().isClient) {
			return true;
		}
		int[][] aim = aim(player);
		if (aim == null || aim[0] == null) {
			return true;
		}
		int[] c = aim[0];
		BlockState state = get(c[0], c[1], c[2]);
		set(c[0], c[1], c[2], null);
		if (state != null) {
			var sound = state.getSoundGroup();
			getWorld().playSound(null, getBlockPos(), sound.getBreakSound(), SoundCategory.BLOCKS, (sound.getVolume() + 1.0F) / 2.0F, sound.getPitch() * 0.8F);
			if (!player.getAbilities().creativeMode) {
				ItemStack drop = new ItemStack(state.getBlock().asItem());
				if (!drop.isEmpty() && !player.giveItemStack(drop)) {
					player.dropItem(drop, false);
				}
			}
		}
		return true;
	}

	// ------------------------------------------------------------------ guardar

	private NbtCompound write() {
		NbtCompound nbt = new NbtCompound();
		NbtList palette = new NbtList();
		List<BlockState> seen = new ArrayList<>();
		int[] idx = new int[cells.length];
		for (int i = 0; i < cells.length; i++) {
			BlockState s = cells[i];
			if (s == null) {
				continue;
			}
			int k = seen.indexOf(s);
			if (k < 0) {
				k = seen.size();
				seen.add(s);
				palette.add(NbtHelper.fromBlockState(s));
			}
			idx[i] = k + 1;
		}
		nbt.put("Palette", palette);
		nbt.putIntArray("Cells", idx);
		return nbt;
	}

	private void read(NbtCompound nbt) {
		NbtList palette = nbt.getList("Palette", 10);
		List<BlockState> states = new ArrayList<>();
		var lookup = Registries.BLOCK.getReadOnlyWrapper();
		for (int i = 0; i < palette.size(); i++) {
			states.add(NbtHelper.toBlockState(lookup, palette.getCompound(i)));
		}
		int[] idx = nbt.getIntArray("Cells");
		for (int i = 0; i < cells.length; i++) {
			int k = i < idx.length ? idx[i] : 0;
			cells[i] = k > 0 && k <= states.size() ? states.get(k - 1) : null;
		}
		rebuild();
	}

	@Override
	protected void readCustomDataFromNbt(NbtCompound nbt) {
		read(nbt.getCompound("Grid"));
		dataTracker.set(GRID, nbt.getCompound("Grid"));
		locomotive = nbt.containsUuid("Locomotive") ? nbt.getUuid("Locomotive") : null;
	}

	@Override
	protected void writeCustomDataToNbt(NbtCompound nbt) {
		nbt.put("Grid", write());
		if (locomotive != null) {
			nbt.putUuid("Locomotive", locomotive);
		}
	}

	@Override
	public void tick() {
		if (boxes.isEmpty()) {
			rebuild();
		}
		super.tick();
		// una plataforma suelta (sin locomotora cargada) se queda quieta
	}
}
