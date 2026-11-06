package com.evolvedwarfare.train;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.BedBlock;
import net.minecraft.block.enums.BedPart;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.block.enums.BlockHalf;
import net.minecraft.block.enums.SlabType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.BucketItem;
import net.minecraft.item.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.Registries;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.collection.DefaultedList;
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
 * quita con clic izquierdo, aunque el tren esté en marcha. Lo construido choca, se pisa, y viaja con el tren. Funcionan
 * los cofres, barriles, cajas de shulker, tolvas y dispensadores (con su inventario), los hornos (que cuecen), las mesas,
 * puertas, trampillas y palancas; las camas y plantas altas se ponen con sus dos partes, y el agua y la lava con cubo.
 */
public class FlatcarEntity extends TrainCarEntity {
	public static final int W = 5, L = 9, H = 8;
	public static final double DECK = 1.25;
	public static final double HALF_LENGTH = 4.75;
	private static final TrackedData<NbtCompound> GRID = DataTracker.registerData(FlatcarEntity.class, TrackedDataHandlerRegistry.NBT_COMPOUND);
	private final BlockState[] cells = new BlockState[W * H * L];
	private int version;
	private List<Box> boxes = List.of();
	private List<Box> targets = List.of();
	private int top;
	@Nullable
	private UUID locomotive;
	/** Lo guardado en las casillas con inventario u horno. */
	private final java.util.Map<Integer, TrainBlocks.Cell> extras = new java.util.HashMap<>();

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

	/** Lo guardado en esa casilla (inventario u horno), o null. */
	@Nullable
	public TrainBlocks.Cell cell(int x, int y, int z) {
		return inside(x, y, z) ? extras.get(index(x, y, z)) : null;
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
		out.addAll(split(new Box(-W / 2.0, 0.55, -HALF_LENGTH, W / 2.0, DECK, HALF_LENGTH)));
		List<Box> targetsOut = new ArrayList<>();
		targetsOut.add(new Box(-W / 2.0, 0.0, -HALF_LENGTH, W / 2.0, DECK, HALF_LENGTH));
		top = 0;
		for (int y = 0; y < H; y++) {
			for (int z = 0; z < L; z++) {
				for (int x = 0; x < W; x++) {
					BlockState s = cells[index(x, y, z)];
					if (s == null) {
						continue;
					}
					top = Math.max(top, y + 1);
					double tx = x - W / 2.0, ty = DECK + y, tz = z - L / 2.0;
					var outline = s.getOutlineShape(getWorld(), net.minecraft.util.math.BlockPos.ORIGIN);
					if (outline.isEmpty() || !s.getFluidState().isEmpty()) {
						targetsOut.add(new Box(tx, ty, tz, tx + 1, ty + 1, tz + 1));
					} else {
						for (Box b : outline.getBoundingBoxes()) {
							targetsOut.add(b.offset(tx, ty, tz));
						}
					}
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
		targets = targetsOut;
		version++;
		setBoundingBox(calculateBoundingBox());
	}

	@Override
	protected List<Box> localBoxes() {
		return boxes;
	}

	@Override
	protected List<Box> localTargets() {
		return targets;
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

	/** ¿Se puede poner en el tren? Casi todo: bloques normales, con inventario, de dos partes (puertas, camas, plantas altas)... */
	public static boolean buildable(ItemStack stack) {
		if (!(stack.getItem() instanceof BlockItem bi)) {
			return false;
		}
		Block b = bi.getBlock();
		return b != Blocks.AIR && !(b instanceof net.minecraft.block.FluidBlock);
	}

	/** Las otras casillas de un bloque de dos partes (la de arriba de una puerta, la cabecera de una cama); null si no tiene. */
	@Nullable
	private int[] partner(int x, int y, int z, BlockState s) {
		if (s.contains(Properties.DOUBLE_BLOCK_HALF)) {
			return s.get(Properties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.LOWER ? new int[] {x, y + 1, z} : new int[] {x, y - 1, z};
		}
		if (s.getBlock() instanceof BedBlock) {
			Direction f = s.get(BedBlock.FACING);
			Direction d = s.get(BedBlock.PART) == BedPart.FOOT ? f : f.getOpposite();
			return new int[] {x + d.getOffsetX(), y, z + d.getOffsetZ()};
		}
		return null;
	}

	/** El bloque a poner, mirando hacia el jugador (en el giro del vagón) y con su mitad (losas, escaleras) según dónde apunta. */
	public BlockState placement(Block block, PlayerEntity player, boolean upper) {
		BlockState s = block.getDefaultState();
		float rel = MathHelper.wrapDegrees(player.getYaw() - getYaw());
		Direction facing = Direction.fromRotation(rel);
		if (s.contains(Properties.HORIZONTAL_FACING)) {
			s = s.with(Properties.HORIZONTAL_FACING, block instanceof BedBlock || block instanceof DoorBlock ? facing : facing.getOpposite());
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

	private boolean free(int[] c) {
		return c != null && inside(c[0], c[1], c[2]) && get(c[0], c[1], c[2]) == null;
	}

	/** ¿Hay alguien dentro de esa casilla? */
	private boolean occupied(int[] c) {
		Box cell = new Box(c[0] - W / 2.0, DECK + c[1], c[2] - L / 2.0, c[0] + 1 - W / 2.0, DECK + c[1] + 1, c[2] + 1 - L / 2.0);
		Box world = rotated(getPos(), getYaw(), cell).contract(0.08);
		return !getWorld().getOtherEntities(this, world, e -> !(e instanceof TrainCarEntity) && e.isAlive() && !e.isSpectator()).isEmpty();
	}

	/** Pone un bloque (con su otra parte si la tiene, y su inventario si lo lleva). Devuelve si ha podido. */
	public boolean build(int[] c, BlockState state) {
		if (!free(c) || occupied(c)) {
			return false;
		}
		int[] second = null;
		BlockState other = null;
		if (state.contains(Properties.DOUBLE_BLOCK_HALF)) {
			second = new int[] {c[0], c[1] + 1, c[2]};
			state = state.with(Properties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.LOWER);
			other = state.with(Properties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER);
		} else if (state.getBlock() instanceof BedBlock) {
			Direction f = state.get(BedBlock.FACING);
			second = new int[] {c[0] + f.getOffsetX(), c[1], c[2] + f.getOffsetZ()};
			state = state.with(BedBlock.PART, BedPart.FOOT);
			other = state.with(BedBlock.PART, BedPart.HEAD);
		}
		if (second != null && (!free(second) || occupied(second))) {
			return false;
		}
		set(c[0], c[1], c[2], state);
		if (second != null) {
			set(second[0], second[1], second[2], other);
		}
		TrainBlocks.Kind kind = TrainBlocks.kind(state.getBlock());
		if (TrainBlocks.hasData(kind)) {
			extras.put(index(c[0], c[1], c[2]), new TrainBlocks.Cell(kind, this));
		}
		return true;
	}

	/** Quita el bloque de esa casilla (y su otra parte): devuelve lo que suelta (el bloque y lo que llevara dentro). */
	public List<ItemStack> remove(int[] c) {
		List<ItemStack> drops = new ArrayList<>();
		BlockState state = get(c[0], c[1], c[2]);
		if (state == null) {
			return drops;
		}
		int[] p = partner(c[0], c[1], c[2], state);
		if (p != null && get(p[0], p[1], p[2]) != null && get(p[0], p[1], p[2]).isOf(state.getBlock())) {
			set(p[0], p[1], p[2], null);
		}
		set(c[0], c[1], c[2], null);
		TrainBlocks.Cell cell = extras.remove(index(c[0], c[1], c[2]));
		ItemStack item = new ItemStack(state.getBlock().asItem());
		if (cell != null && cell.kind == TrainBlocks.Kind.SHULKER) {
			// la caja de shulker se lleva lo de dentro, como siempre
			item.set(net.minecraft.component.DataComponentTypes.CONTAINER, net.minecraft.component.type.ContainerComponent.fromStacks(cell.getHeldStacks()));
		} else if (cell != null) {
			for (ItemStack s : cell.getHeldStacks()) {
				if (!s.isEmpty()) {
					drops.add(s.copy());
				}
			}
		}
		if (!item.isEmpty()) {
			drops.add(0, item);
		}
		return drops;
	}

	/** Usa el bloque de esa casilla: abre puertas, trampillas y palancas, abre cofres, hornos y mesas. */
	public boolean use(int[] c, PlayerEntity player) {
		BlockState state = get(c[0], c[1], c[2]);
		if (state == null) {
			return false;
		}
		if (TrainBlocks.toggles(state)) {
			if (!getWorld().isClient) {
				var prop = state.contains(Properties.OPEN) ? Properties.OPEN : Properties.POWERED;
				BlockState toggled = state.cycle(prop);
				set(c[0], c[1], c[2], toggled);
				int[] p = partner(c[0], c[1], c[2], state);
				if (p != null && get(p[0], p[1], p[2]) != null && get(p[0], p[1], p[2]).contains(prop)) {
					set(p[0], p[1], p[2], get(p[0], p[1], p[2]).with(prop, toggled.get(prop)));
				}
				var sound = toggled.contains(Properties.OPEN) && toggled.get(Properties.OPEN) ? SoundEvents.BLOCK_WOODEN_DOOR_OPEN : SoundEvents.BLOCK_WOODEN_DOOR_CLOSE;
				if (state.getBlock() instanceof net.minecraft.block.LeverBlock) {
					sound = SoundEvents.BLOCK_LEVER_CLICK;
				}
				getWorld().playSound(null, getBlockPos(), sound, SoundCategory.BLOCKS, 1.0F, 1.0F);
			}
			return true;
		}
		if (state.getBlock() instanceof BedBlock) {
			if (!getWorld().isClient) {
				player.sendMessage(Text.translatable("message.evolvedwarfare.train.no_sleep").formatted(Formatting.GRAY), true);
			}
			return true;
		}
		TrainBlocks.Kind kind = TrainBlocks.kind(state.getBlock());
		if (kind == TrainBlocks.Kind.NONE) {
			return false;
		}
		if (!getWorld().isClient) {
			int[] base = c;
			TrainBlocks.Cell cell = extras.get(index(base[0], base[1], base[2]));
			if (cell == null && TrainBlocks.hasData(kind)) {
				cell = new TrainBlocks.Cell(kind, this);
				extras.put(index(base[0], base[1], base[2]), cell);
			}
			TrainBlocks.open(this, player, state, cell);
		}
		return true;
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
		int[][] aim = aim(player);
		// usar lo que hay (salvo agachado con algo en la mano, que es para poner al lado)
		if (aim != null && aim[0] != null && !(player.isSneaking() && !stack.isEmpty()) && use(aim[0], player)) {
			return ActionResult.SUCCESS;
		}
		// cubos: agua y lava (se ven, no se nada en ellas) y recogerlas con el cubo vacío
		if (stack.getItem() instanceof BucketItem) {
			return bucket(player, stack, aim);
		}
		if (!buildable(stack) || aim == null || aim[1] == null) {
			return ActionResult.PASS;
		}
		if (getWorld().isClient) {
			return ActionResult.SUCCESS;
		}
		int[] c = aim[1];
		Vec3d eye = toLocal(player.getEyePos());
		boolean upper = eye.y - DECK - c[1] > 0.5 && player.getPitch() < 0 || aim[0] != null && aim[0][1] == c[1];
		BlockState state = placement(((BlockItem) stack.getItem()).getBlock(), player, upper);
		if (!build(c, state)) {
			return ActionResult.FAIL;
		}
		// lo que lleve el objeto dentro (una caja de shulker llena)
		var contents = stack.get(net.minecraft.component.DataComponentTypes.CONTAINER);
		TrainBlocks.Cell cell = extras.get(index(c[0], c[1], c[2]));
		if (contents != null && cell != null) {
			DefaultedList<ItemStack> list = DefaultedList.ofSize(cell.size(), ItemStack.EMPTY);
			contents.copyTo(list);
			for (int i = 0; i < list.size(); i++) {
				cell.setStack(i, list.get(i));
			}
		}
		var sound = state.getSoundGroup();
		getWorld().playSound(null, getBlockPos(), sound.getPlaceSound(), SoundCategory.BLOCKS, (sound.getVolume() + 1.0F) / 2.0F, sound.getPitch() * 0.8F);
		stack.decrementUnlessCreative(1, player);
		return ActionResult.CONSUME;
	}

	private ActionResult bucket(PlayerEntity player, ItemStack stack, @Nullable int[][] aim) {
		if (aim == null) {
			return ActionResult.PASS;
		}
		BlockState fluid = stack.isOf(Items.WATER_BUCKET) ? Blocks.WATER.getDefaultState() : stack.isOf(Items.LAVA_BUCKET) ? Blocks.LAVA.getDefaultState() : null;
		if (stack.isOf(Items.BUCKET)) {
			if (aim[0] == null || get(aim[0][0], aim[0][1], aim[0][2]) == null || get(aim[0][0], aim[0][1], aim[0][2]).getFluidState().isEmpty()) {
				return ActionResult.PASS;
			}
			if (!getWorld().isClient) {
				BlockState s = get(aim[0][0], aim[0][1], aim[0][2]);
				set(aim[0][0], aim[0][1], aim[0][2], null);
				ItemStack full = new ItemStack(s.isOf(Blocks.LAVA) ? Items.LAVA_BUCKET : Items.WATER_BUCKET);
				player.setStackInHand(Hand.MAIN_HAND, net.minecraft.item.ItemUsage.exchangeStack(stack, player, full));
				getWorld().playSound(null, getBlockPos(), s.isOf(Blocks.LAVA) ? SoundEvents.ITEM_BUCKET_FILL_LAVA : SoundEvents.ITEM_BUCKET_FILL,
						SoundCategory.BLOCKS, 1.0F, 1.0F);
			}
			return ActionResult.SUCCESS;
		}
		if (fluid == null || aim[1] == null) {
			return ActionResult.PASS;
		}
		if (!getWorld().isClient) {
			if (!build(aim[1], fluid)) {
				return ActionResult.FAIL;
			}
			if (!player.getAbilities().creativeMode) {
				player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.BUCKET));
			}
			getWorld().playSound(null, getBlockPos(), fluid.isOf(Blocks.LAVA) ? SoundEvents.ITEM_BUCKET_EMPTY_LAVA : SoundEvents.ITEM_BUCKET_EMPTY,
					SoundCategory.BLOCKS, 1.0F, 1.0F);
		}
		return ActionResult.SUCCESS;
	}

	/** Clic izquierdo: quita el bloque al que apunta (y te lo da, con lo que llevara dentro). */
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
		if (state == null) {
			return true;
		}
		List<ItemStack> drops = remove(c);
		var sound = state.getSoundGroup();
		getWorld().playSound(null, getBlockPos(), sound.getBreakSound(), SoundCategory.BLOCKS, (sound.getVolume() + 1.0F) / 2.0F, sound.getPitch() * 0.8F);
		if (!state.getFluidState().isEmpty()) {
			return true;
		}
		for (int i = 0; i < drops.size(); i++) {
			ItemStack drop = drops.get(i);
			if (i == 0 && player.getAbilities().creativeMode) {
				continue;
			}
			if (!player.giveItemStack(drop)) {
				player.dropItem(drop, false);
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
		extras.clear();
		for (NbtElement e : nbt.getList("Extras", NbtElement.COMPOUND_TYPE)) {
			NbtCompound c = (NbtCompound) e;
			int i = c.getInt("Index");
			BlockState s = i >= 0 && i < cells.length ? cells[i] : null;
			if (s != null && TrainBlocks.hasData(TrainBlocks.kind(s.getBlock()))) {
				TrainBlocks.Cell cell = new TrainBlocks.Cell(TrainBlocks.kind(s.getBlock()), this);
				cell.read(c, getRegistryManager());
				extras.put(i, cell);
			}
		}
		dataTracker.set(GRID, nbt.getCompound("Grid"));
		locomotive = nbt.containsUuid("Locomotive") ? nbt.getUuid("Locomotive") : null;
	}

	@Override
	protected void writeCustomDataToNbt(NbtCompound nbt) {
		nbt.put("Grid", write());
		NbtList list = new NbtList();
		for (var e : extras.entrySet()) {
			NbtCompound c = e.getValue().write(getRegistryManager());
			c.putInt("Index", e.getKey());
			list.add(c);
		}
		nbt.put("Extras", list);
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
		// los hornos cuecen (y se encienden y apagan) aunque el tren vaya
		if (!getWorld().isClient) {
			for (var e : extras.entrySet()) {
				TrainBlocks.Cell cell = e.getValue();
				if (cell.kind == TrainBlocks.Kind.FURNACE || cell.kind == TrainBlocks.Kind.SMOKER || cell.kind == TrainBlocks.Kind.BLAST) {
					boolean lit = cell.tickFurnace(getWorld());
					BlockState s = cells[e.getKey()];
					if (s != null && s.contains(Properties.LIT) && s.get(Properties.LIT) != lit) {
						int i = e.getKey();
						set(i % W, i / (W * L), (i / W) % L, s.with(Properties.LIT, lit));
					}
				}
			}
		}
		// una plataforma suelta (sin locomotora cargada) se queda quieta
	}
}
