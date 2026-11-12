package com.evolvedwarfare.voxel;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.block.*;
import net.minecraft.block.enums.BedPart;
import net.minecraft.block.enums.BlockFace;
import net.minecraft.block.enums.BlockHalf;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.block.enums.SlabType;
import net.minecraft.block.enums.WallShape;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.screen.BlastFurnaceScreenHandler;
import net.minecraft.screen.CraftingScreenHandler;
import net.minecraft.screen.FurnaceScreenHandler;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerContext;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.screen.SmokerScreenHandler;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.EmptyBlockView;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * Construir bloque a bloque (estilo Scrap Mechanic) en una rejilla de vóxeles: qué bloques valen, cómo quedan al ponerlos
 * (hacia dónde miran, media losa arriba o abajo, antorchas en la pared, puertas y camas de dos bloques...), cómo se unen
 * las vallas y los paneles, cómo se usan (puertas, cofres, hornos, fogatas, mesa, cama) y cómo se rompen a golpes.
 */
public final class VoxelBuild {
	/** Un golpe cuenta como estos tics picando (con la herramienta buena, unos pocos golpes; a mano, bastantes más). */
	public static final float HIT_TICKS = 8.0F;

	private VoxelBuild() {
	}

	// ------------------------------------------------------------------ qué se puede poner

	/**
	 * Casi todos: los bloques normales, losas, escaleras, vallas, cristales, puertas, trampillas, faroles y antorchas, y los
	 * funcionales (cofre, barril, hornos, fogatas, mesa de crafteo, cama). No los que necesitan el mundo: líquidos, arena o
	 * grava que caen, plantas, redstone, pistones ni otros bloques con datos propios (carteles, cofres de ender...).
	 */
	public static boolean allowed(Block b) {
		if (b instanceof AirBlock) {
			return false;
		}
		if (VoxelMachine.Kind.of(b) != null || b instanceof BedBlock) {
			return true;
		}
		if (b instanceof BlockEntityProvider) {
			return false;
		}
		if (b instanceof FluidBlock || b instanceof FallingBlock || b instanceof PlantBlock || b instanceof AbstractPlantPartBlock || b instanceof CactusBlock
				|| b instanceof SugarCaneBlock || b instanceof VineBlock || b instanceof BambooBlock || b instanceof BambooShootBlock
				|| b instanceof ScaffoldingBlock || b instanceof TntBlock || b instanceof AbstractRailBlock || b instanceof ButtonBlock
				|| b instanceof LeverBlock || b instanceof AbstractPressurePlateBlock || b instanceof TripwireHookBlock || b instanceof TripwireBlock
				|| b instanceof RedstoneWireBlock || b instanceof AbstractRedstoneGateBlock || b instanceof RedstoneTorchBlock
				|| b instanceof WallRedstoneTorchBlock || b instanceof PistonBlock || b instanceof PistonHeadBlock || b instanceof ObserverBlock
				|| b instanceof NetherPortalBlock || b instanceof AbstractFireBlock || b instanceof CoralParentBlock || b instanceof MultifaceGrowthBlock
				|| b instanceof PointedDripstoneBlock || b instanceof LightBlock || b instanceof TurtleEggBlock || b instanceof SnifferEggBlock
				|| b instanceof BubbleColumnBlock || b instanceof ChorusPlantBlock || b instanceof ChorusFlowerBlock || b instanceof DispenserBlock
				|| b instanceof HopperBlock || b instanceof FrostedIceBlock || b instanceof PowderSnowBlock || b instanceof HangingRootsBlock
				|| b instanceof SporeBlossomBlock) {
			return false;
		}
		BlockState s = b.getDefaultState();
		return s.getHardness(EmptyBlockView.INSTANCE, BlockPos.ORIGIN) >= 0.0F && s.getRenderType() != BlockRenderType.INVISIBLE;
	}

	@Nullable
	public static Block blockOf(ItemStack stack) {
		return stack.getItem() instanceof BlockItem bi && allowed(bi.getBlock()) ? bi.getBlock() : null;
	}

	// ------------------------------------------------------------------ apuntar

	/** Lo que mira el jugador en la rejilla (o en la base), con su alcance para entidades (+1, como en el mundo). */
	@Nullable
	public static VoxelGrid.Hit aim(VoxelHost host, PlayerEntity player, float tickDelta) {
		Vec3d eye = player.getCameraPosVec(tickDelta);
		Vec3d end = eye.add(player.getRotationVec(tickDelta).multiply(player.getEntityInteractionRange() + 1.0));
		return host.grid().raycast(host.worldToGrid(eye), host.worldToGrid(end), host.baseBoxes());
	}

	/** Hacia dónde mira el jugador, en las coordenadas de la entidad. */
	public static Vec3d lookLocal(VoxelHost host, PlayerEntity player, float tickDelta) {
		Vec3d eye = player.getCameraPosVec(tickDelta);
		return host.worldToGrid(eye.add(player.getRotationVec(tickDelta))).subtract(host.worldToGrid(eye));
	}

	// ------------------------------------------------------------------ poner

	/** Lo que se pondría: en qué celdas y cómo queda cada una. */
	public record Placement(int[] cells, BlockState[] states) {
	}

	/** Dónde y cómo quedaría ese bloque si se pone apuntando ahí (o null si no cabe). */
	@Nullable
	public static Placement plan(VoxelGrid g, VoxelGrid.Hit hit, ItemStack stack, Vec3d look) {
		Block block = blockOf(stack);
		if (block == null) {
			return null;
		}
		// media losa sobre media losa igual: se hace entera
		if (hit.index() >= 0) {
			BlockState c = g.get(hit.index());
			if (c.isOf(block) && c.contains(Properties.SLAB_TYPE)) {
				SlabType t = c.get(Properties.SLAB_TYPE);
				if (t == SlabType.BOTTOM && hit.side() == Direction.UP || t == SlabType.TOP && hit.side() == Direction.DOWN) {
					return new Placement(new int[] {hit.index()}, new BlockState[] {c.with(Properties.SLAB_TYPE, SlabType.DOUBLE)});
				}
			}
		}
		int t = g.target(hit);
		if (t < 0 || !g.isEmpty(t)) {
			return null;
		}
		Vec3d frac = hit.pos().subtract(g.corner(t));
		BlockState s = stateFor(block, hit.side(), frac, look);
		if (s == null) {
			return null;
		}
		if (s.contains(Properties.DOUBLE_BLOCK_HALF)) {
			int up = g.neighbor(t, Direction.UP);
			if (up < 0 || !g.isEmpty(up)) {
				return null;
			}
			return new Placement(new int[] {t, up}, new BlockState[] {s.with(Properties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.LOWER),
					s.with(Properties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER)});
		}
		if (block instanceof BedBlock) {
			int head = g.neighbor(t, s.get(Properties.HORIZONTAL_FACING));
			if (head < 0 || !g.isEmpty(head)) {
				return null;
			}
			return new Placement(new int[] {t, head}, new BlockState[] {s.with(Properties.BED_PART, BedPart.FOOT), s.with(Properties.BED_PART, BedPart.HEAD)});
		}
		return new Placement(new int[] {t}, new BlockState[] {s});
	}

	/**
	 * Cómo queda un bloque puesto por esa cara ({@code side}: de lo apuntado hacia la celda nueva), con el punto apuntado
	 * dentro de la celda ({@code frac}, de 0 a 1) y la mirada del jugador, como lo haría vanilla.
	 */
	@Nullable
	public static BlockState stateFor(Block block, Direction side, Vec3d frac, Vec3d look) {
		Direction h = Math.abs(look.x) < 1e-6 && Math.abs(look.z) < 1e-6 ? Direction.NORTH : Direction.getFacing(look.x, 0, look.z);
		// antorchas: en la pared si se apunta a un lado; no cuelgan del techo
		if (block == Blocks.TORCH || block == Blocks.SOUL_TORCH) {
			if (side == Direction.DOWN) {
				return null;
			}
			if (side.getAxis().isHorizontal()) {
				return (block == Blocks.TORCH ? Blocks.WALL_TORCH : Blocks.SOUL_WALL_TORCH).getDefaultState().with(Properties.HORIZONTAL_FACING, side);
			}
		}
		BlockState s = block.getDefaultState();
		boolean top = side == Direction.DOWN || side != Direction.UP && frac.y > 0.5;
		if (s.contains(Properties.HORIZONTAL_FACING)) {
			Direction f;
			if (block instanceof StairsBlock || block instanceof DoorBlock || block instanceof BedBlock || block instanceof FenceGateBlock) {
				f = h;
			} else if (block instanceof TrapdoorBlock) {
				f = side.getAxis().isHorizontal() ? side : h.getOpposite();
			} else if (block instanceof LadderBlock) {
				if (!side.getAxis().isHorizontal()) {
					return null;
				}
				f = side;
			} else if (s.contains(Properties.BLOCK_FACE)) {
				f = side.getAxis().isHorizontal() ? side : h;
			} else {
				f = h.getOpposite();
			}
			s = s.with(Properties.HORIZONTAL_FACING, f);
		}
		if (s.contains(Properties.BLOCK_FACE)) {
			s = s.with(Properties.BLOCK_FACE, side == Direction.UP ? BlockFace.FLOOR : side == Direction.DOWN ? BlockFace.CEILING : BlockFace.WALL);
		}
		if (s.contains(Properties.FACING)) {
			Direction f = block instanceof BarrelBlock ? Direction.getFacing(look.x, look.y, look.z).getOpposite() : side;
			s = s.with(Properties.FACING, f);
		}
		if (s.contains(Properties.AXIS)) {
			s = s.with(Properties.AXIS, side.getAxis());
		}
		if (s.contains(Properties.HORIZONTAL_AXIS)) {
			s = s.with(Properties.HORIZONTAL_AXIS, h.getAxis());
		}
		if (s.contains(Properties.SLAB_TYPE)) {
			s = s.with(Properties.SLAB_TYPE, top ? SlabType.TOP : SlabType.BOTTOM);
		}
		if (s.contains(Properties.BLOCK_HALF)) {
			s = s.with(Properties.BLOCK_HALF, top ? BlockHalf.TOP : BlockHalf.BOTTOM);
		}
		if (s.contains(Properties.HANGING)) {
			s = s.with(Properties.HANGING, side == Direction.DOWN);
		}
		if (s.contains(Properties.PERSISTENT)) {
			s = s.with(Properties.PERSISTENT, true);
		}
		return s;
	}

	/** Pone lo planeado (con su máquina si es un cofre, horno...), une vallas y paneles y avisa. */
	public static void apply(VoxelHost host, Placement p) {
		VoxelGrid g = host.grid();
		for (int k = 0; k < p.cells().length; k++) {
			int i = p.cells()[k];
			g.set(i, p.states()[k]);
			VoxelMachine.Kind kind = VoxelMachine.Kind.of(p.states()[k].getBlock());
			if (kind != null && (g.machine(i) == null || g.machine(i).kind != kind)) {
				g.setMachine(i, new VoxelMachine(kind));
			}
		}
		connectAround(g, p.cells());
		host.gridChanged();
	}

	/** Pone un bloque en una celda tal cual (para las pruebas y para pasar las carretas viejas). */
	public static void put(VoxelHost host, int i, BlockState state) {
		apply(host, new Placement(new int[] {i}, new BlockState[] {state}));
	}

	// ------------------------------------------------------------------ uniones (vallas, paneles, muros)

	public static void connectAround(VoxelGrid g, int... cells) {
		Set<Integer> todo = new LinkedHashSet<>();
		for (int i : cells) {
			todo.add(i);
			for (Direction d : Direction.Type.HORIZONTAL) {
				int n = g.neighbor(i, d);
				if (n >= 0) {
					todo.add(n);
				}
			}
		}
		for (int i : todo) {
			connect(g, i);
		}
	}

	private static BooleanProperty side(Direction d) {
		return switch (d) {
			case NORTH -> Properties.NORTH;
			case SOUTH -> Properties.SOUTH;
			case EAST -> Properties.EAST;
			default -> Properties.WEST;
		};
	}

	private static EnumProperty<WallShape> wallSide(Direction d) {
		return switch (d) {
			case NORTH -> Properties.NORTH_WALL_SHAPE;
			case SOUTH -> Properties.SOUTH_WALL_SHAPE;
			case EAST -> Properties.EAST_WALL_SHAPE;
			default -> Properties.WEST_WALL_SHAPE;
		};
	}

	private static void connect(VoxelGrid g, int i) {
		BlockState s = g.get(i);
		Block b = s.getBlock();
		boolean fence = b instanceof FenceBlock, pane = b instanceof PaneBlock, wall = b instanceof WallBlock;
		if (!fence && !pane && !wall) {
			return;
		}
		BlockState out = s;
		for (Direction d : Direction.Type.HORIZONTAL) {
			BlockState n = g.get(g.neighbor(i, d));
			Block nb = n.getBlock();
			boolean full = !n.isAir() && n.isSideSolidFullSquare(EmptyBlockView.INSTANCE, BlockPos.ORIGIN, d.getOpposite());
			boolean gate = nb instanceof FenceGateBlock && n.get(Properties.HORIZONTAL_FACING).rotateYClockwise().getAxis() == d.getAxis();
			boolean c = fence ? full || nb instanceof FenceBlock || gate
					: pane ? full || nb instanceof PaneBlock || nb instanceof WallBlock
					: full || nb instanceof WallBlock || nb instanceof PaneBlock || gate;
			out = wall ? out.with(wallSide(d), c ? WallShape.LOW : WallShape.NONE) : out.with(side(d), c);
		}
		if (wall) {
			out = out.with(Properties.UP, true);
		}
		if (out != s) {
			g.set(i, out);
		}
	}

	// ------------------------------------------------------------------ quitar y romper

	/** Las celdas de un bloque (las dos de una puerta o de una cama). */
	public static int[] parts(VoxelGrid g, int i) {
		BlockState s = g.get(i);
		int other = -1;
		if (s.contains(Properties.DOUBLE_BLOCK_HALF)) {
			other = g.neighbor(i, s.get(Properties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.LOWER ? Direction.UP : Direction.DOWN);
		} else if (s.getBlock() instanceof BedBlock) {
			Direction f = s.get(Properties.HORIZONTAL_FACING);
			other = g.neighbor(i, s.get(Properties.BED_PART) == BedPart.FOOT ? f : f.getOpposite());
		}
		return other >= 0 && g.get(other).isOf(s.getBlock()) ? new int[] {i, other} : new int[] {i};
	}

	/** La cabecera de una cama (la celda de la cama que tiene la almohada). */
	public static int bedHead(VoxelGrid g, int i) {
		for (int p : parts(g, i)) {
			if (g.get(p).getBlock() instanceof BedBlock && g.get(p).get(Properties.BED_PART) == BedPart.HEAD) {
				return p;
			}
		}
		return i;
	}

	/**
	 * Quita el bloque de esa celda (y su otra mitad): devuelve lo que suelta, el propio bloque (si {@code withBlock}) y lo
	 * que tenía dentro.
	 */
	public static List<ItemStack> remove(VoxelHost host, int i, boolean withBlock) {
		VoxelGrid g = host.grid();
		List<ItemStack> out = new ArrayList<>();
		BlockState s = g.get(i);
		if (s.isAir()) {
			return out;
		}
		if (withBlock) {
			int count = s.contains(Properties.SLAB_TYPE) && s.get(Properties.SLAB_TYPE) == SlabType.DOUBLE ? 2 : 1;
			ItemStack self = new ItemStack(s.getBlock().asItem(), count);
			if (!self.isEmpty()) {
				out.add(self);
			}
		}
		int[] cells = parts(g, i);
		for (int c : cells) {
			VoxelMachine m = g.machine(c);
			if (m != null) {
				for (int k = 0; k < m.inventory.size(); k++) {
					ItemStack inside = m.inventory.removeStack(k);
					if (!inside.isEmpty()) {
						out.add(inside);
					}
				}
				g.setMachine(c, null);
			}
			g.set(c, Blocks.AIR.getDefaultState());
		}
		connectAround(g, cells);
		host.gridChanged();
		return out;
	}

	/** Lo que lleva roto cada celda (por golpes), para la rejilla de cada entidad. */
	public static final class Damage {
		int cell = -1;
		float done;
		long last;
	}

	/**
	 * Un golpe a un bloque: avanza lo roto según su dureza y la herramienta (como picar unos tics); al llegar al final se
	 * rompe y suelta el bloque (si la herramienta sirve, como en el mundo) y lo que tenía dentro. En creativo, de un golpe.
	 */
	public static void hit(VoxelHost host, PlayerEntity player, int i, Damage damage) {
		VoxelGrid g = host.grid();
		World world = host.self().getWorld();
		BlockState s = g.get(i);
		if (s.isAir() || !(world instanceof ServerWorld sw)) {
			return;
		}
		boolean creative = player.getAbilities().creativeMode;
		float hardness = s.getHardness(EmptyBlockView.INSTANCE, BlockPos.ORIGIN);
		boolean harvest = player.canHarvest(s);
		float delta = hardness <= 0.0F || creative ? 1.0F : player.getBlockBreakingSpeed(s) / hardness / (harvest ? 30.0F : 100.0F) * HIT_TICKS;
		long now = world.getTime();
		if (damage.cell != i || now - damage.last > 200) {
			damage.cell = i;
			damage.done = 0.0F;
		}
		damage.last = now;
		damage.done += delta;
		Vec3d at = host.gridToWorld(g.center(i));
		BlockSoundGroup sound = s.getSoundGroup();
		if (damage.done < 1.0F) {
			world.playSound(null, at.x, at.y, at.z, sound.getHitSound(), SoundCategory.BLOCKS, (sound.getVolume() + 1.0F) / 4.0F, sound.getPitch() * 0.5F);
			sw.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, s), at.x, at.y, at.z, 4, 0.3, 0.3, 0.3, 0.05);
			return;
		}
		damage.cell = -1;
		ItemStack tool = player.getMainHandStack();
		if (!creative) {
			tool.postMine(world, s, BlockPos.ofFloored(at), player);
		}
		for (ItemStack drop : remove(host, i, harvest && !creative)) {
			drop(world, at, drop);
		}
		world.playSound(null, at.x, at.y, at.z, sound.getBreakSound(), SoundCategory.BLOCKS, (sound.getVolume() + 1.0F) / 2.0F, sound.getPitch() * 0.8F);
		sw.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, s), at.x, at.y, at.z, 24, 0.3, 0.3, 0.3, 0.1);
	}

	/** Suelta algo en ese sitio (sin salir disparado). */
	public static void drop(World world, Vec3d at, ItemStack stack) {
		net.minecraft.entity.ItemEntity item = new net.minecraft.entity.ItemEntity(world, at.x, at.y, at.z, stack);
		item.setVelocity(world.random.nextGaussian() * 0.05, 0.15, world.random.nextGaussian() * 0.05);
		item.setToDefaultPickupDelay();
		world.spawnEntity(item);
	}

	// ------------------------------------------------------------------ usar

	/** ¿Hace algo al usarlo (clic derecho) con eso en la mano? */
	public static boolean usable(VoxelGrid g, int i, ItemStack stack) {
		BlockState s = g.get(i);
		Block b = s.getBlock();
		if (b instanceof DoorBlock) {
			return !s.isOf(Blocks.IRON_DOOR);
		}
		if (b instanceof TrapdoorBlock) {
			return !s.isOf(Blocks.IRON_TRAPDOOR);
		}
		if (b instanceof FenceGateBlock || b == Blocks.CRAFTING_TABLE || b instanceof BedBlock) {
			return true;
		}
		VoxelMachine.Kind k = VoxelMachine.Kind.of(b);
		return k != null && (k != VoxelMachine.Kind.CAMPFIRE || !stack.isEmpty() && blockOf(stack) == null);
	}

	/** Lo usa (en el servidor): abre o cierra puertas, abre cofres y hornos, echa comida a la fogata, la mesa, la cama. */
	public static void use(VoxelHost host, PlayerEntity player, int i, ItemStack stack) {
		VoxelGrid g = host.grid();
		World world = host.self().getWorld();
		BlockState s = g.get(i);
		Block b = s.getBlock();
		Vec3d at = host.gridToWorld(g.center(i));
		if (b instanceof DoorBlock || b instanceof TrapdoorBlock || b instanceof FenceGateBlock) {
			boolean open = !s.get(Properties.OPEN);
			for (int p : parts(g, i)) {
				g.set(p, g.get(p).with(Properties.OPEN, open));
			}
			SoundEvent sound = b instanceof DoorBlock ? open ? SoundEvents.BLOCK_WOODEN_DOOR_OPEN : SoundEvents.BLOCK_WOODEN_DOOR_CLOSE
					: b instanceof TrapdoorBlock ? open ? SoundEvents.BLOCK_WOODEN_TRAPDOOR_OPEN : SoundEvents.BLOCK_WOODEN_TRAPDOOR_CLOSE
					: open ? SoundEvents.BLOCK_FENCE_GATE_OPEN : SoundEvents.BLOCK_FENCE_GATE_CLOSE;
			world.playSound(null, at.x, at.y, at.z, sound, SoundCategory.BLOCKS, 1.0F, 1.0F);
			host.gridChanged();
			return;
		}
		if (b instanceof BedBlock) {
			host.sleepOn(bedHead(g, i), player);
			return;
		}
		Entity self = host.self();
		if (b == Blocks.CRAFTING_TABLE) {
			player.openHandledScreen(new SimpleNamedScreenHandlerFactory((id, inv, p) -> new Crafting(id, inv, self), Text.translatable("container.crafting")));
			return;
		}
		VoxelMachine m = g.machine(i);
		if (m == null) {
			return;
		}
		if (m.kind == VoxelMachine.Kind.CAMPFIRE) {
			if (m.addToCampfire(world, player.getAbilities().creativeMode ? stack.copy() : stack)) {
				host.gridChanged();
			}
			return;
		}
		Text title = Text.translatable(b.getTranslationKey());
		player.openHandledScreen(new SimpleNamedScreenHandlerFactory((id, inv, p) -> menu(id, inv, self, m), title));
		if (m.kind == VoxelMachine.Kind.CHEST || m.kind == VoxelMachine.Kind.BARREL) {
			world.playSound(null, at.x, at.y, at.z, m.kind == VoxelMachine.Kind.CHEST ? SoundEvents.BLOCK_CHEST_OPEN : SoundEvents.BLOCK_BARREL_OPEN,
					SoundCategory.BLOCKS, 0.5F, 1.0F);
		}
	}

	private static ScreenHandler menu(int syncId, PlayerInventory playerInv, Entity self, VoxelMachine m) {
		Guarded inv = new Guarded(m.inventory, self);
		return switch (m.kind) {
			case FURNACE -> new FurnaceScreenHandler(syncId, playerInv, inv, m.properties());
			case SMOKER -> new SmokerScreenHandler(syncId, playerInv, inv, m.properties());
			case BLAST_FURNACE -> new BlastFurnaceScreenHandler(syncId, playerInv, inv, m.properties());
			default -> GenericContainerScreenHandler.createGeneric9x3(syncId, playerInv, inv);
		};
	}

	/** El inventario del bloque, que solo se puede usar cerca de la entidad (y mientras el bloque siga ahí). */
	static final class Guarded extends SimpleInventory {
		private final SimpleInventory inner;
		private final Entity self;

		Guarded(SimpleInventory inner, Entity self) {
			super(0);
			this.inner = inner;
			this.self = self;
		}

		@Override
		public int size() {
			return inner.size();
		}

		@Override
		public boolean isEmpty() {
			return inner.isEmpty();
		}

		@Override
		public ItemStack getStack(int slot) {
			return inner.getStack(slot);
		}

		@Override
		public ItemStack removeStack(int slot, int amount) {
			return inner.removeStack(slot, amount);
		}

		@Override
		public ItemStack removeStack(int slot) {
			return inner.removeStack(slot);
		}

		@Override
		public void setStack(int slot, ItemStack stack) {
			inner.setStack(slot, stack);
		}

		@Override
		public void markDirty() {
			inner.markDirty();
		}

		@Override
		public boolean canPlayerUse(PlayerEntity player) {
			return self.isAlive() && player.squaredDistanceTo(self) < 10 * 10;
		}

		@Override
		public void clear() {
			inner.clear();
		}
	}

	/** La mesa de crafteo de la rejilla: vale cerca de la entidad y, al cerrar, devuelve lo de la cuadrícula. */
	static final class Crafting extends CraftingScreenHandler {
		private final Entity self;

		Crafting(int syncId, PlayerInventory inv, Entity self) {
			super(syncId, inv, ScreenHandlerContext.create(self.getWorld(), self.getBlockPos()));
			this.self = self;
		}

		@Override
		public boolean canUse(PlayerEntity player) {
			return self.isAlive() && player.squaredDistanceTo(self) < 10 * 10;
		}
	}
}
