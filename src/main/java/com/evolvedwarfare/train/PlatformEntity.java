package com.evolvedwarfare.train;

import com.evolvedwarfare.voxel.VoxelBuild;
import com.evolvedwarfare.voxel.VoxelGrid;
import com.evolvedwarfare.voxel.VoxelHost;
import com.evolvedwarfare.voxel.VoxelMachine;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.block.AbstractFurnaceBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CampfireBlock;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventories;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundCategory;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * La plataforma (fases 52 y 63): un vagón plano de 5 x 10 bloques sobre el que se construye bloque a bloque, como en la
 * carreta (estilo Scrap Mechanic): hasta {@link #HEIGHT} de alto, con casi cualquier bloque (ver {@link VoxelBuild#allowed});
 * cofres, hornos, fogatas, mesa, cama, puertas y trampillas se usan con clic derecho; a golpes se quitan. Lo construido va
 * con el tren: se choca con ello, se anda por encima y lo que está encima se mueve con él.
 */
public class PlatformEntity extends TrainCarEntity implements VoxelHost {
	/** La altura de la tabla y el tamaño de lo que se puede construir encima (ancho, alto, largo). */
	public static final double DECK = 1.0;
	public static final int WIDTH = 5, HEIGHT = 4, LENGTH = 10;
	public static final double HALF_LENGTH = LENGTH / 2.0 + 0.15, BOGIE = 3.5;
	private static final List<Box> BASE = List.of(new Box(-WIDTH / 2.0, 0.3, -LENGTH / 2.0, WIDTH / 2.0, DECK, LENGTH / 2.0));
	private static final List<Box> BASE_SOLID = split(new Box(-WIDTH / 2.0, 0.55, -LENGTH / 2.0, WIDTH / 2.0, DECK, LENGTH / 2.0));
	private static final TrackedData<NbtCompound> GRID = DataTracker.registerData(PlatformEntity.class, TrackedDataHandlerRegistry.NBT_COMPOUND);

	private VoxelGrid grid;
	private final VoxelBuild.Damage blockDamage = new VoxelBuild.Damage();

	public PlatformEntity(EntityType<?> type, World world) {
		super(type, world);
		grid();
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {
		builder.add(GRID, new NbtCompound());
	}

	// ------------------------------------------------------------------ la rejilla

	@Override
	public VoxelGrid grid() {
		if (grid == null) {
			grid = new VoxelGrid(WIDTH, HEIGHT, LENGTH, -WIDTH / 2.0, DECK, -LENGTH / 2.0);
		}
		return grid;
	}

	@Override
	public Entity self() {
		return this;
	}

	@Override
	public Vec3d gridToWorld(Vec3d local) {
		return toWorld(local.x, local.y, local.z);
	}

	@Override
	public Vec3d worldToGrid(Vec3d world) {
		return toLocal(world);
	}

	@Override
	public List<Box> baseBoxes() {
		return BASE;
	}

	@Override
	public void gridChanged() {
		if (!getWorld().isClient) {
			getDataTracker().set(GRID, grid().write(getRegistryManager(), false));
		}
		setBoundingBox(calculateBoundingBox());
	}

	@Override
	public void onTrackedDataSet(TrackedData<?> data) {
		super.onTrackedDataSet(data);
		if (GRID.equals(data) && getWorld().isClient) {
			grid().read(getDataTracker().get(GRID), getRegistryManager());
			setBoundingBox(calculateBoundingBox());
		}
	}

	@Override
	protected List<Box> localBoxes() {
		List<Box> out = new ArrayList<>(BASE_SOLID);
		out.addAll(grid().collisionBoxes());
		return out;
	}

	@Override
	protected List<Box> localTargets() {
		List<Box> out = new ArrayList<>(BASE);
		out.addAll(grid().outlineBoxes());
		return out;
	}

	@Override
	protected int boxesVersion() {
		return grid().version();
	}

	@Override
	protected double halfWidth() {
		return WIDTH / 2.0;
	}

	@Override
	protected double halfLength() {
		return HALF_LENGTH;
	}

	@Override
	protected double bogie() {
		return BOGIE;
	}

	@Override
	protected double height() {
		return DECK + Math.max(0.5, grid == null ? 0 : grid.height()) + 0.1;
	}

	/** Pone un bloque tal cual en una celda (para las pruebas). */
	public void putBlock(int x, int y, int z, BlockState state) {
		VoxelBuild.put(this, grid().index(x, y, z), state);
	}

	// ------------------------------------------------------------------ clic derecho y golpes

	/**
	 * Clic derecho: un vagón en la mano lo engancha y el martillo, agachado, desengancha (como en todos los coches); en un
	 * bloque que se usa (cofre, horno, puerta...), se usa; con un bloque en la mano, se pone al lado de lo que se apunta.
	 */
	@Override
	public ActionResult interact(PlayerEntity player, Hand hand) {
		World world = getWorld();
		ItemStack stack = player.getStackInHand(hand);
		ActionResult common = Trains.carInteract(this, player, hand);
		if (common != ActionResult.PASS) {
			return common;
		}
		VoxelGrid.Hit hit = VoxelBuild.aim(this, player, 1.0F);
		if (hit == null) {
			return ActionResult.PASS;
		}
		if (hand == Hand.MAIN_HAND && hit.index() >= 0 && !player.isSneaking() && VoxelBuild.usable(grid(), hit.index(), stack)) {
			if (!world.isClient) {
				VoxelBuild.use(this, player, hit.index(), stack);
			}
			return ActionResult.success(world.isClient);
		}
		if (VoxelBuild.blockOf(stack) != null) {
			VoxelBuild.Placement plan = VoxelBuild.plan(grid(), hit, stack, VoxelBuild.lookLocal(this, player, 1.0F));
			if (plan == null) {
				if (!world.isClient && hand == Hand.MAIN_HAND) {
					player.sendMessage(Text.translatable("message.evolvedwarfare.build.cant").formatted(Formatting.RED), true);
				}
				return ActionResult.FAIL;
			}
			if (!world.isClient) {
				VoxelBuild.apply(this, plan);
				stack.decrementUnlessCreative(1, player);
				var g = plan.states()[0].getSoundGroup();
				Vec3d at = gridToWorld(grid().center(plan.cells()[0]));
				world.playSound(null, at.x, at.y, at.z, g.getPlaceSound(), SoundCategory.BLOCKS, (g.getVolume() + 1.0F) / 2.0F, g.getPitch() * 0.8F);
			}
			return ActionResult.success(world.isClient);
		}
		return ActionResult.PASS;
	}

	/** Golpes: al bloque al que se apunta (se va rompiendo, como picar). La plataforma no se rompe a golpes. */
	@Override
	public boolean handleAttack(Entity attacker) {
		if (attacker instanceof PlayerEntity player && !getWorld().isClient) {
			VoxelGrid.Hit hit = VoxelBuild.aim(this, player, 1.0F);
			if (hit != null && hit.index() >= 0) {
				VoxelBuild.hit(this, player, hit.index(), blockDamage);
			}
		}
		return true;
	}

	/** Al recogerla: suelta todo lo construido (con lo que tenga dentro). */
	public void dropContents() {
		VoxelGrid g = grid();
		for (int i = 0; i < g.size(); i++) {
			if (!g.isEmpty(i)) {
				Vec3d at = gridToWorld(g.center(i));
				for (ItemStack item : VoxelBuild.remove(this, i, true)) {
					VoxelBuild.drop(getWorld(), at, item);
				}
			}
		}
	}

	// ------------------------------------------------------------------ hornos, fogatas, humo y calor

	@Override
	public void tick() {
		super.tick();
		if (getWorld().isClient) {
			particles();
			return;
		}
		VoxelGrid g = grid();
		boolean sync = false;
		for (int i = 0; i < g.size(); i++) {
			VoxelMachine m = g.machine(i);
			if (m == null) {
				continue;
			}
			if (m.kind.furnace()) {
				if (m.tickFurnace(getWorld()) && g.get(i).contains(Properties.LIT)) {
					g.set(i, g.get(i).with(Properties.LIT, m.lit()));
					sync = true;
				}
			} else if (m.kind == VoxelMachine.Kind.CAMPFIRE && g.get(i).get(Properties.LIT)) {
				ItemStack done = m.tickCampfire(getWorld());
				if (!done.isEmpty()) {
					Vec3d at = gridToWorld(g.corner(i).add(0.5, 0.6, 0.5));
					ItemEntity item = new ItemEntity(getWorld(), at.x, at.y, at.z, done);
					item.setVelocity(Vec3d.ZERO);
					getWorld().spawnEntity(item);
					sync = true;
				}
			}
		}
		if (sync) {
			gridChanged();
		}
	}

	private void particles() {
		VoxelGrid g = grid();
		for (int i = 0; i < g.size(); i++) {
			BlockState s = g.get(i);
			if (s.isAir()) {
				continue;
			}
			Block b = s.getBlock();
			boolean lit = s.contains(Properties.LIT) && s.get(Properties.LIT);
			Vec3d c = g.corner(i);
			if (b instanceof CampfireBlock && lit && random.nextInt(3) == 0) {
				Vec3d at = toWorld(c.x + 0.5 + (random.nextDouble() - 0.5) * 0.4, c.y + 0.5, c.z + 0.5 + (random.nextDouble() - 0.5) * 0.4);
				getWorld().addParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, at.x, at.y, at.z, 0.0, 0.07, 0.0);
			} else if (b instanceof AbstractFurnaceBlock && lit && random.nextInt(12) == 0) {
				Vec3d at = toWorld(c.x + 0.5, c.y + 1.0, c.z + 0.5);
				getWorld().addParticle(ParticleTypes.SMOKE, at.x, at.y, at.z, 0.0, 0.02, 0.0);
			} else if ((b == Blocks.TORCH || b == Blocks.SOUL_TORCH) && random.nextInt(4) == 0) {
				Vec3d at = toWorld(c.x + 0.5, c.y + 0.7, c.z + 0.5);
				getWorld().addParticle(b == Blocks.TORCH ? ParticleTypes.FLAME : ParticleTypes.SOUL_FIRE_FLAME, at.x, at.y, at.z, 0.0, 0.0, 0.0);
			}
		}
	}

	/** Lo que calienta (para el clima): cada fogata u horno encendido, con su sitio en el mundo. */
	public void heat(java.util.function.BiConsumer<Vec3d, Float> out) {
		VoxelGrid g = grid();
		for (int i = 0; i < g.size(); i++) {
			VoxelMachine m = g.machine(i);
			if (m != null) {
				float w = m.warmth(g.get(i));
				if (w > 0.0F) {
					out.accept(gridToWorld(g.center(i)), w);
				}
			}
		}
	}

	// ------------------------------------------------------------------ guardar

	@Override
	protected void readCustomDataFromNbt(NbtCompound nbt) {
		if (nbt.contains("Grid")) {
			grid().read(nbt.getCompound("Grid"), getRegistryManager());
		} else if (nbt.contains("Pieces")) {
			migrate(nbt.getCompound("Pieces"));
		}
		readLink(nbt);
		gridChanged();
	}

	@Override
	protected void writeCustomDataToNbt(NbtCompound nbt) {
		nbt.put("Grid", grid().write(getRegistryManager(), true));
		writeLink(nbt);
	}

	/**
	 * Las plataformas de antes (3 x 6, con piezas de ARK y bloques sueltos): los cofres, hornos, fogatas y mesas pasan a
	 * bloques en el centro de la nueva (la planta de arriba, dos bloques más arriba), con lo que tenían dentro; las piezas
	 * (paredes, techos...) se devuelven como su material principal, en el suelo junto a la plataforma.
	 */
	private void migrate(NbtCompound old) {
		Block[] kinds = {Blocks.CHEST, Blocks.BARREL, Blocks.FURNACE, Blocks.SMOKER, Blocks.BLAST_FURNACE, Blocks.CAMPFIRE, Blocks.SOUL_CAMPFIRE,
				Blocks.CRAFTING_TABLE};
		VoxelGrid g = grid();
		NbtList blocks = old.getList("Blocks", NbtElement.COMPOUND_TYPE);
		for (int k = 0; k < blocks.size(); k++) {
			NbtCompound b = blocks.getCompound(k);
			int kind = b.getByte("K");
			if (kind < 0 || kind >= kinds.length) {
				continue;
			}
			int x = b.getByte("X") + 1, z = b.getByte("Z") + 2, y = b.getByte("L") > 0 ? 2 : 0;
			if (!g.inside(x, y, z) || !g.isEmpty(g.index(x, y, z))) {
				continue;
			}
			int i = g.index(x, y, z);
			BlockState s = kinds[kind].getDefaultState();
			if (s.contains(Properties.HORIZONTAL_FACING)) {
				s = s.with(Properties.HORIZONTAL_FACING, Direction.fromHorizontal(b.getByte("F")));
			}
			if (s.contains(Properties.LIT)) {
				s = s.with(Properties.LIT, b.getBoolean("Lit"));
			}
			g.set(i, s);
			VoxelMachine.Kind mk = VoxelMachine.Kind.of(s.getBlock());
			if (mk != null) {
				VoxelMachine m = new VoxelMachine(mk);
				if (b.contains("Inv")) {
					Inventories.readNbt(b.getCompound("Inv"), m.inventory.getHeldStacks(), getRegistryManager());
				}
				int[] f = b.getIntArray("Furnace");
				if (f.length == 4) {
					m.burn = f[0];
					m.burnTotal = f[1];
					m.cook = f[2];
					m.cookTotal = Math.max(1, f[3]);
				}
				g.setMachine(i, m);
			}
		}
		// las piezas: su material principal
		Item[] material = {Items.WHEAT, Items.OAK_PLANKS, Items.COBBLESTONE, Items.IRON_INGOT};
		int[] amount = {6, 6, 6, 4};
		NbtList pieces = old.getList("List", NbtElement.COMPOUND_TYPE);
		for (int k = 0; k < pieces.size(); k++) {
			int m = pieces.getCompound(k).getByte("M");
			if (m >= 0 && m < material.length && !getWorld().isClient) {
				refund.add(new ItemStack(material[m], amount[m]));
			}
		}
	}

	/** Lo que se devuelve de las piezas de antes (se suelta en cuanto la plataforma está en el mundo). */
	private final List<ItemStack> refund = new ArrayList<>();

	@Override
	public void baseTick() {
		super.baseTick();
		if (!refund.isEmpty() && !getWorld().isClient) {
			for (ItemStack s : refund) {
				dropStack(s, 1.5F);
			}
			refund.clear();
		}
	}

	@Nullable
	public VoxelGrid.Hit aim(PlayerEntity player) {
		return VoxelBuild.aim(this, player, 1.0F);
	}
}
