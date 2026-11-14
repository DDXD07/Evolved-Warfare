package com.evolvedwarfare.train;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * La tuneladora de vapor (fase 64): una locomotora con una gran cabeza giratoria delante que, al llegar al final de la vía,
 * excava un túnel de {@link #WIDTH} x {@link #HEIGHT} (caben las plataformas cargadas) y pone el trozo de vía siguiente:
 * recto, o en cuesta hacia arriba o hacia abajo (el maquinista lo elige con A y D). Gasta las vías de su depósito (o de
 * los ténderes) y guarda en él lo que excava (lo que no cabe, cae); si no hay suelo bajo la vía, lo rellena con piedra del
 * depósito; y tapa con piedra el agua y la lava de las paredes para que no se inunde. Excava más deprisa cuanto más abierto
 * está el regulador. No rompe lo irrompible (el lecho de roca), ni las vías.
 */
public class BorerEntity extends LocomotiveEntity {
	/** El túnel: 6 de ancho (la vía va entre dos bloques: así caben las plataformas de 5) y 6 de alto. */
	public static final int WIDTH = 6, HEIGHT = 6;
	/** Cuánto por delante del centro mira si queda vía (más que la cabeza). */
	public static final double LOOKAHEAD = HALF_LENGTH + 1.5;
	private static final TrackedData<Integer> MODE = DataTracker.registerData(BorerEntity.class, TrackedDataHandlerRegistry.INTEGER);
	private static final TrackedData<Boolean> DIGGING = DataTracker.registerData(BorerEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
	private static final double K = 2.5 / 16.0;

	/** El depósito: vías, combustible y lo excavado. */
	public final SimpleInventory storage = new SimpleInventory(27) {
		@Override
		public boolean canPlayerUse(PlayerEntity player) {
			return isAlive() && player.squaredDistanceTo(BorerEntity.this) < 10 * 10;
		}
	};
	/** Lo que se está excavando ahora: el trozo de vía que irá, los bloques y lo que falta. */
	@Nullable
	private Job job;
	private int modeCooldown;
	private int warnCooldown;
	private List<Box> boxes;

	/** Un trozo de túnel en obras. */
	private record Job(Track.Piece piece, Item rail, List<BlockPos> dig, List<BlockPos> floor, List<BlockPos> seal, float cost, float[] done) {
	}

	public BorerEntity(EntityType<?> type, World world) {
		super(type, world);
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {
		super.initDataTracker(builder);
		builder.add(MODE, 0);
		builder.add(DIGGING, false);
	}

	/** -1 baja, 0 llano, 1 sube. */
	public int mode() {
		return dataTracker.get(MODE);
	}

	public void setMode(int mode) {
		dataTracker.set(MODE, MathHelper.clamp(mode, -1, 1));
	}

	/** ¿Está excavando? (la cabeza gira y echa polvo). */
	public boolean digging() {
		return dataTracker.get(DIGGING);
	}

	@Override
	protected List<Box> localBoxes() {
		if (boxes == null) {
			List<Box> b = new ArrayList<>(super.localBoxes());
			// la cabeza: un disco de casi todo el túnel, delante
			b.addAll(split(new Box(-2.4, 0.3, 15 * K, 2.4, 5.4, 18 * K)));
			// el depósito (a la derecha) y el hogar (a la izquierda), con sus puertas
			b.addAll(split(new Box(-13 * K, 6 * K, -3 * K, -8 * K, 17 * K, 12 * K)));
			b.addAll(split(new Box(6 * K, 6 * K, -4 * K, 13 * K, 17 * K, 6 * K)));
			boxes = b;
		}
		return boxes;
	}

	@Override
	protected double halfWidth() {
		return 2.4;
	}

	@Override
	protected double height() {
		return 5.4;
	}

	@Override
	@Nullable
	protected Inventory extraFuel() {
		return storage;
	}

	// ------------------------------------------------------------------ el maquinista

	/** A baja, D sube (y la contraria vuelve a llano). */
	@Override
	protected void driverInput(ServerWorld world, ServerPlayerEntity driver) {
		if (modeCooldown > 0) {
			modeCooldown--;
			return;
		}
		if (Math.abs(driver.sidewaysSpeed) > 0.1F) {
			setMode(mode() + (driver.sidewaysSpeed > 0 ? -1 : 1));
			modeCooldown = 8;
			world.playSound(null, getBlockPos(), SoundEvents.BLOCK_LEVER_CLICK, SoundCategory.NEUTRAL, 0.6F, mode() > 0 ? 1.2F : mode() < 0 ? 0.6F : 0.9F);
		}
	}

	@Override
	public Text dash() {
		Text base = super.dash();
		Text mode = Text.translatable("message.evolvedwarfare.borer.mode." + (mode() > 0 ? "up" : mode() < 0 ? "down" : "flat"));
		return Text.translatable("message.evolvedwarfare.borer.dash", base, mode, countRails(Trains.TRACK_STRAIGHT) + countRails(Trains.TRACK_SLOPE))
				.formatted(Formatting.GOLD);
	}

	// ------------------------------------------------------------------ excavar y poner vía

	@Override
	protected boolean clearAhead(ServerWorld world, boolean burning) {
		Track.Pos pos = track();
		if (pos == null) {
			return true;
		}
		Track.Walk ahead = Track.walk(world, pos, LOOKAHEAD);
		if (!ahead.blocked()) {
			job = null;
			dataTracker.set(DIGGING, false);
			return true;
		}
		if (!burning) {
			dataTracker.set(DIGGING, false);
			return false;
		}
		if (job == null) {
			job = plan(world, ahead.pos());
			if (job == null) {
				dataTracker.set(DIGGING, false);
				return false;
			}
		}
		dataTracker.set(DIGGING, true);
		// cuanto más abierto el regulador, más deprisa
		job.done()[0] += 1.0F + 0.5F * Math.max(0, throttle());
		effects(world);
		if (job.done()[0] < job.cost()) {
			return false;
		}
		finish(world, job);
		job = null;
		return false;
	}

	/** El trozo de vía que va después de esa punta (recto o en cuesta) y lo que hay que excavar, rellenar y tapar. */
	@Nullable
	private Job plan(ServerWorld world, Track.Pos end) {
		Track.Piece last = Track.piece(world, end.piece());
		if (last == null) {
			return null;
		}
		int route = end.route();
		double len = last.length(route);
		boolean atEnd = end.t() >= len - 1e-6;
		Vec3d tip = last.point(route, atEnd ? len : 0);
		Vec3d out = Track.flat(last.dir(route, atEnd ? len : 0)).multiply(atEnd ? 1 : -1);
		Direction facing = Direction.getFacing(out.x, 0, out.z);
		int y = (int) Math.floor(tip.y - Track.RAIL_TOP + 0.01);
		double ox = Math.round(tip.x), oz = Math.round(tip.z);
		Track.Piece piece;
		int mode = mode();
		if (mode < 0) {
			piece = new Track.Piece(BlockPos.ORIGIN, Track.Shape.SLOPE, facing.getOpposite(), false, false, ox + 4 * facing.getOffsetX(),
					oz + 4 * facing.getOffsetZ(), y - 1);
		} else {
			piece = new Track.Piece(BlockPos.ORIGIN, mode > 0 ? Track.Shape.SLOPE : Track.Shape.STRAIGHT, facing, false, false, ox, oz, y);
		}
		piece = new Track.Piece(piece.blockOf(0, 0), piece.shape(), piece.facing(), false, false, piece.ox(), piece.oz(), piece.y());
		Item rail = Trains.trackItem(piece.shape(), false);
		if (countRails(rail) <= 0) {
			warn(Text.translatable("message.evolvedwarfare.borer.no_rails", new ItemStack(rail).getName()));
			return null;
		}
		// los bloques del túnel: 6 de ancho y 6 de alto (uno más en las cuestas), sobre la vía; el suelo bajo los raíles
		Set<BlockPos> dig = new LinkedHashSet<>(), floor = new LinkedHashSet<>(), seal = new LinkedHashSet<>();
		boolean slope = piece.shape() == Track.Shape.SLOPE;
		int steps = slope ? 4 : 2;
		float cost = 0.0F;
		for (int k = 0; k < steps; k++) {
			double v = k + 0.5;
			double h = slope ? v / 4.0 : 0.0;
			for (int c = 0; c < WIDTH; c++) {
				double u = c - WIDTH / 2.0 + 0.5;
				Vec3d at = piece.world(u, v, h);
				int x = MathHelper.floor(at.x), z = MathHelper.floor(at.z);
				int y0 = MathHelper.floor(at.y - Track.RAIL_TOP + 0.01);
				int top = y0 + HEIGHT - 1 + (slope ? 1 : 0);
				for (int yy = y0; yy <= top; yy++) {
					dig.add(new BlockPos(x, yy, z));
				}
				if (Math.abs(u) < 1.0) {
					floor.add(new BlockPos(x, y0 - 1, z));
				}
				// las paredes, el techo y el suelo: si hay agua o lava, se tapan
				seal.add(new BlockPos(x, top + 1, z));
				seal.add(new BlockPos(x, y0 - 1, z));
				if (c == 0 || c == WIDTH - 1) {
					Vec3d side = piece.world(u + (c == 0 ? -1 : 1), v, h);
					for (int yy = y0; yy <= top; yy++) {
						seal.add(new BlockPos(MathHelper.floor(side.x), yy, MathHelper.floor(side.z)));
					}
				}
			}
		}
		List<BlockPos> toDig = new ArrayList<>();
		for (BlockPos p : dig) {
			if (world.isOutOfHeightLimit(p)) {
				warn(Text.translatable("message.evolvedwarfare.borer.blocked"));
				return null;
			}
			BlockState s = world.getBlockState(p);
			if (s.isAir() || s.getBlock() instanceof TrackBlock) {
				continue;
			}
			float hardness = s.getHardness(world, p);
			if (hardness < 0.0F) {
				warn(Text.translatable("message.evolvedwarfare.borer.blocked"));
				return null;
			}
			toDig.add(p);
			cost += s.getFluidState().isEmpty() ? MathHelper.clamp(hardness, 0.2F, 8.0F) : 0.2F;
		}
		seal.removeAll(dig);
		return new Job(piece, rail, toDig, new ArrayList<>(floor), new ArrayList<>(seal), Math.max(10.0F, cost), new float[1]);
	}

	/** Termina el trozo: tapa el agua y la lava, excava (guarda lo que sale), rellena el suelo y pone la vía. */
	private void finish(ServerWorld world, Job job) {
		ItemStack tool = new ItemStack(Items.DIAMOND_PICKAXE);
		for (BlockPos p : job.seal()) {
			if (!world.getFluidState(p).isEmpty()) {
				world.setBlockState(p, Blocks.COBBLESTONE.getDefaultState(), Block.NOTIFY_ALL);
			}
		}
		for (BlockPos p : job.dig()) {
			BlockState s = world.getBlockState(p);
			if (s.isAir() || s.getBlock() instanceof TrackBlock || s.getHardness(world, p) < 0.0F) {
				continue;
			}
			if (s.getFluidState().isEmpty()) {
				BlockEntity be = world.getBlockEntity(p);
				for (ItemStack drop : Block.getDroppedStacks(s, world, p, be, this, tool)) {
					store(world, drop);
				}
			}
			world.setBlockState(p, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
		}
		for (BlockPos p : job.floor()) {
			BlockState s = world.getBlockState(p);
			if (s.isReplaceable() || !s.getFluidState().isEmpty()) {
				BlockState fill = takeFill();
				if (fill != null) {
					world.setBlockState(p, fill, Block.NOTIFY_ALL);
				}
			}
		}
		if (takeRail(job.rail())) {
			TrackItem.place(world, job.piece());
			world.playSound(null, job.piece().main(), SoundEvents.BLOCK_CHAIN_PLACE, SoundCategory.BLOCKS, 1.0F, 0.7F);
		}
		world.playSound(null, getBlockPos(), SoundEvents.BLOCK_STONE_BREAK, SoundCategory.BLOCKS, 1.5F, 0.6F);
	}

	/** Polvo y ruido mientras la cabeza muerde la roca. */
	private void effects(ServerWorld world) {
		Vec3d head = toWorld(0, 2.6, 19 * K);
		if (age % 3 == 0 && job != null && !job.dig().isEmpty()) {
			BlockPos p = job.dig().get(random.nextInt(job.dig().size()));
			BlockState s = world.getBlockState(p);
			if (!s.isAir()) {
				world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, s), head.x, head.y, head.z, 8, 1.6, 1.6, 1.6, 0.1);
			}
		}
		if (age % 10 == 0) {
			world.playSound(null, head.x, head.y, head.z, SoundEvents.BLOCK_GRINDSTONE_USE, SoundCategory.BLOCKS, 0.8F, 0.6F);
		}
		if (age % 7 == 0) {
			world.playSound(null, head.x, head.y, head.z, SoundEvents.BLOCK_STONE_HIT, SoundCategory.BLOCKS, 1.0F, 0.5F);
		}
	}

	private void warn(Text text) {
		if (warnCooldown > 0) {
			return;
		}
		warnCooldown = 60;
		if (getFirstPassenger() instanceof PlayerEntity driver) {
			driver.sendMessage(text.copy().formatted(Formatting.RED), true);
		}
	}

	@Override
	public void tick() {
		if (warnCooldown > 0) {
			warnCooldown--;
		}
		super.tick();
		if (getWorld().isClient && digging()) {
			Vec3d head = toWorld((random.nextDouble() - 0.5) * 4.0, 0.5 + random.nextDouble() * 4.5, 19 * K);
			getWorld().addParticle(ParticleTypes.CLOUD, head.x, head.y, head.z, 0.0, 0.02, 0.0);
		}
	}

	// ------------------------------------------------------------------ el depósito

	/** Las vías de un tipo que hay (en su depósito y en los ténderes). */
	public int countRails(Item rail) {
		int n = count(storage, rail);
		if (getWorld() instanceof ServerWorld) {
			for (TrainCarEntity car : loadedWagons()) {
				if (car instanceof TenderEntity t) {
					n += count(t.inventory, rail);
				}
			}
		}
		return n;
	}

	private static int count(Inventory inv, Item item) {
		int n = 0;
		for (int i = 0; i < inv.size(); i++) {
			if (inv.getStack(i).isOf(item)) {
				n += inv.getStack(i).getCount();
			}
		}
		return n;
	}

	private boolean takeRail(Item rail) {
		if (take(storage, rail)) {
			return true;
		}
		for (TrainCarEntity car : loadedWagons()) {
			if (car instanceof TenderEntity t && take(t.inventory, rail)) {
				return true;
			}
		}
		return false;
	}

	private static boolean take(Inventory inv, Item item) {
		for (int i = 0; i < inv.size(); i++) {
			if (inv.getStack(i).isOf(item)) {
				inv.getStack(i).decrement(1);
				inv.markDirty();
				return true;
			}
		}
		return false;
	}

	/** Un bloque del depósito para rellenar el suelo (piedra, tierra... cualquier bloque entero y macizo). */
	@Nullable
	private BlockState takeFill() {
		for (int i = 0; i < storage.size(); i++) {
			ItemStack s = storage.getStack(i);
			if (s.getItem() instanceof BlockItem bi) {
				BlockState st = bi.getBlock().getDefaultState();
				if (st.isFullCube(getWorld(), BlockPos.ORIGIN) && !st.hasBlockEntity() && bi.getBlock() != Blocks.GRAVEL && bi.getBlock() != Blocks.SAND
						&& !(bi.getBlock() instanceof net.minecraft.block.FallingBlock)) {
					s.decrement(1);
					storage.markDirty();
					return st;
				}
			}
		}
		return null;
	}

	/** Guarda lo excavado; lo que no cabe, cae junto a la tuneladora. */
	private void store(ServerWorld world, ItemStack stack) {
		ItemStack rest = storage.addStack(stack);
		if (!rest.isEmpty()) {
			dropStack(rest, 1.0F);
		}
	}

	// ------------------------------------------------------------------ clic derecho

	/** Las dos puertas de fuera: la compuerta del depósito (a la derecha) y la de la caldera (a la izquierda). */
	public enum Hatch { DEPOSIT, FIREBOX }

	/** La puerta a la que apunta un rayo (desde el ojo, en esa dirección), o null. */
	@Nullable
	public Hatch hatchAt(Vec3d eye, Vec3d look) {
		Vec3d hit = raycast(eye, eye.add(look.normalize().multiply(6.0)));
		if (hit == null) {
			return null;
		}
		Vec3d l = toLocal(hit);
		if (l.y < 6 * K - 0.05 || l.y > 17 * K + 0.05) {
			return null;
		}
		if (l.x < -8 * K + 0.01 && l.z > -3 * K - 0.05 && l.z < 12 * K + 0.05) {
			return Hatch.DEPOSIT;
		}
		if (l.x > 7.5 * K && l.z > -4 * K - 0.05 && l.z < 6 * K + 0.05) {
			return Hatch.FIREBOX;
		}
		return null;
	}

	/** Clic derecho en la compuerta del depósito o en la puerta de la caldera; en lo demás, como una locomotora (sin agacharse). */
	@Override
	public ActionResult interact(PlayerEntity player, Hand hand) {
		ActionResult common = Trains.carInteract(this, player, hand);
		if (common != ActionResult.PASS) {
			return common;
		}
		if (hand != Hand.MAIN_HAND) {
			return ActionResult.PASS;
		}
		Hatch hatch = hatchAt(player.getCameraPosVec(1.0F), player.getRotationVec(1.0F));
		if (hatch != null) {
			if (getWorld().isClient) {
				return ActionResult.SUCCESS;
			}
			if (hatch == Hatch.FIREBOX) {
				return openFirebox(player, hand);
			}
			getWorld().playSound(null, getBlockPos(), SoundEvents.BLOCK_IRON_TRAPDOOR_OPEN, SoundCategory.NEUTRAL, 0.8F, 0.9F);
			player.openHandledScreen(new SimpleNamedScreenHandlerFactory(
					(syncId, inv, p) -> GenericContainerScreenHandler.createGeneric9x3(syncId, inv, storage),
					Text.translatable("container.evolvedwarfare.steam_borer")));
			return ActionResult.CONSUME;
		}
		if (player.isSneaking()) {
			return ActionResult.PASS;
		}
		return super.interact(player, hand);
	}

	@Override
	public void dropContents() {
		super.dropContents();
		for (int i = 0; i < storage.size(); i++) {
			ItemStack s = storage.removeStack(i);
			if (!s.isEmpty()) {
				dropStack(s);
			}
		}
	}

	// ------------------------------------------------------------------ guardar

	@Override
	protected void readCustomDataFromNbt(NbtCompound nbt) {
		super.readCustomDataFromNbt(nbt);
		storage.readNbtList(nbt.getList("Storage", NbtElement.COMPOUND_TYPE), getRegistryManager());
		setMode(nbt.getInt("Mode"));
	}

	@Override
	protected void writeCustomDataToNbt(NbtCompound nbt) {
		super.writeCustomDataToNbt(nbt);
		nbt.put("Storage", storage.toNbtList(getRegistryManager()));
		nbt.putInt("Mode", mode());
	}
}
