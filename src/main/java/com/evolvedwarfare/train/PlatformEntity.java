package com.evolvedwarfare.train;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.AxeItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.PickaxeItem;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import org.jetbrains.annotations.Nullable;

/**
 * La plataforma (fase 52): un vagón plano de 3 x 6 bloques donde se construye como en ARK (paredes, puertas, ventanas,
 * techos, tejados, escaleras y barandillas de paja, madera, piedra o metal; ver {@link Building}). Lo construido va con el
 * tren: se choca con ello, se anda por encima y lo que está encima se mueve con él.
 */
public class PlatformEntity extends TrainCarEntity {
	public static final double HALF_LENGTH = 3.15, BOGIE = 2.0;
	private static final TrackedData<NbtCompound> PIECES = DataTracker.registerData(PlatformEntity.class, TrackedDataHandlerRegistry.NBT_COMPOUND);

	private final List<Building.Placed> pieces = new ArrayList<>();
	private final List<TrainBlocks.Placed> blocks = new ArrayList<>();
	private List<Box> boxes = List.of(), targets = List.of();
	private int version;
	private double top;

	public PlatformEntity(EntityType<?> type, World world) {
		super(type, world);
		rebuild();
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {
		builder.add(PIECES, new NbtCompound());
	}

	public List<Building.Placed> pieces() {
		return pieces;
	}

	public List<TrainBlocks.Placed> blocks() {
		return blocks;
	}

	private void rebuild() {
		List<Box> solid = new ArrayList<>(split(new Box(-1.5, 0.55, -3.0, 1.5, Building.DECK, 3.0)));
		List<Box> tg = new ArrayList<>();
		tg.add(new Box(-1.5, 0.3, -3.0, 1.5, Building.DECK, 3.0));
		top = 0;
		for (Building.Placed p : pieces) {
			for (Building.Part part : Building.parts(p)) {
				if (part.solid()) {
					solid.addAll(split(part.box()));
				}
				top = Math.max(top, part.box().maxY - Building.DECK);
			}
			tg.add(Building.bounds(p));
		}
		for (TrainBlocks.Placed b : blocks) {
			solid.add(b.box());
			tg.add(b.box());
			top = Math.max(top, b.box().maxY - Building.DECK);
		}
		boxes = solid;
		targets = tg;
		version++;
		setBoundingBox(calculateBoundingBox());
	}

	/** Cambia las piezas (en el servidor): se guardan, se mandan al cliente y se recalcula lo macizo. */
	private void changed() {
		rebuild();
		dataTracker.set(PIECES, write());
	}

	@Override
	public void onTrackedDataSet(TrackedData<?> data) {
		super.onTrackedDataSet(data);
		if (PIECES.equals(data) && getWorld().isClient) {
			read(dataTracker.get(PIECES));
		}
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
		return 1.5;
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
		return Building.DECK + top + 0.1;
	}

	// ------------------------------------------------------------------ construir

	/** ¿Se puede poner esa pieza ahí? */
	public boolean canPlace(Building.Slot slot) {
		return Building.canPlace(pieces, slot);
	}

	/** Dónde iría una pieza si se apunta ahí (punto del mundo) mirando hacia look (del mundo). */
	@Nullable
	public Building.Slot slotFor(Building.Kind kind, Vec3d hitWorld, Vec3d lookWorld) {
		Vec3d hit = toLocal(hitWorld);
		Vec3d look = toLocal(getPos().add(lookWorld)).subtract(toLocal(getPos()));
		return Building.slotFor(kind, hit, look);
	}

	public boolean place(Building.Slot slot, Building.Material material) {
		if (!canPlace(slot)) {
			return false;
		}
		pieces.add(new Building.Placed(slot.kind(), material, slot.level(), slot.a(), slot.b(), slot.rot()));
		changed();
		return true;
	}

	/** La pieza a la que da un rayo (del mundo), o null. */
	@Nullable
	public Building.Placed aimed(Vec3d from, Vec3d to) {
		Vec3d lf = toLocal(from), lt = toLocal(to);
		Building.Placed best = null;
		double bestD = Double.MAX_VALUE;
		boolean deck = false;
		var deckHit = new Box(-1.5, 0.3, -3.0, 1.5, Building.DECK, 3.0).raycast(lf, lt);
		double deckD = deckHit.map(h -> h.squaredDistanceTo(lf)).orElse(Double.MAX_VALUE);
		for (Building.Placed p : pieces) {
			for (Building.Part part : Building.parts(p)) {
				Box b = part.box().expand(0.02);
				if (b.contains(lf)) {
					return p;
				}
				var hit = b.raycast(lf, lt);
				if (hit.isPresent()) {
					double d = hit.get().squaredDistanceTo(lf);
					if (d < bestD) {
						bestD = d;
						best = p;
					}
				}
			}
		}
		return bestD <= deckD ? best : null;
	}

	@Nullable
	public Building.Placed aimed(PlayerEntity player) {
		double reach = player.getEntityInteractionRange() + 1.0;
		Vec3d eye = player.getEyePos();
		return aimed(eye, eye.add(player.getRotationVec(1.0F).multiply(reach)));
	}

	/** Quita una pieza (y lo que se quede sin apoyo); devuelve las piezas que salen. */
	public List<Building.Placed> remove(Building.Placed p) {
		List<Building.Placed> out = new ArrayList<>();
		if (!pieces.remove(p)) {
			return out;
		}
		out.add(p);
		List<Building.Placed> fall = Building.unsupported(pieces);
		pieces.removeAll(fall);
		out.addAll(fall);
		changed();
		return out;
	}

	private void give(PlayerEntity player, Building.Placed p) {
		ItemStack s = new ItemStack(Trains.pieceItem(p.kind, p.material));
		if (!player.giveItemStack(s)) {
			player.dropItem(s, false);
		}
	}

	public static BlockSoundGroup sound(Building.Material m) {
		return switch (m) {
			case STRAW -> BlockSoundGroup.GRASS;
			case WOOD -> BlockSoundGroup.WOOD;
			case STONE -> BlockSoundGroup.STONE;
			case METAL -> BlockSoundGroup.METAL;
		};
	}

	/**
	 * Clic derecho (con el punto exacto donde se apunta): con una pieza en la mano, se pone donde se ve la vista previa;
	 * agachado con el martillo, se recoge la pieza (o, sin piezas, se desengancha la plataforma); en una puerta, se abre.
	 */
	@Override
	public ActionResult interactAt(PlayerEntity player, Vec3d hitPos, Hand hand) {
		ItemStack stack = player.getStackInHand(hand);
		if (stack.getItem() instanceof PieceItem piece) {
			Building.Slot slot = slotFor(piece.kind, getPos().add(hitPos), player.getRotationVec(1.0F));
			if (slot == null || !canPlace(slot)) {
				if (!getWorld().isClient && hand == Hand.MAIN_HAND) {
					player.sendMessage(Text.translatable("message.evolvedwarfare.build.cant").formatted(Formatting.RED), true);
				}
				return ActionResult.FAIL;
			}
			if (!getWorld().isClient) {
				place(slot, piece.material);
				stack.decrementUnlessCreative(1, player);
				BlockSoundGroup g = sound(piece.material);
				getWorld().playSound(null, getBlockPos(), g.getPlaceSound(), SoundCategory.BLOCKS, 1.0F, g.getPitch() * 0.8F);
			}
			return ActionResult.SUCCESS;
		}
		TrainBlocks.Kind blockKind = TrainBlocks.Kind.of(stack.getItem());
		if (blockKind != null && !player.isSneaking()) {
			int[] at = blockSlotFor(getPos().add(hitPos), player.getRotationVec(1.0F));
			if (at == null || !canPlaceBlock(at)) {
				if (!getWorld().isClient && hand == Hand.MAIN_HAND) {
					player.sendMessage(Text.translatable("message.evolvedwarfare.build.cant").formatted(Formatting.RED), true);
				}
				return ActionResult.FAIL;
			}
			if (!getWorld().isClient) {
				placeBlock(blockKind, at, player);
				stack.decrementUnlessCreative(1, player);
				var g = blockKind.block.getDefaultState().getSoundGroup();
				getWorld().playSound(null, getBlockPos(), g.getPlaceSound(), SoundCategory.BLOCKS, 1.0F, g.getPitch() * 0.8F);
			}
			return ActionResult.SUCCESS;
		}
		if (hand != Hand.MAIN_HAND) {
			return ActionResult.PASS;
		}
		TrainBlocks.Placed block = aimedBlock(player);
		if (block != null) {
			if (stack.isOf(Trains.BUILD_HAMMER) && player.isSneaking()) {
				if (!getWorld().isClient) {
					blocks.remove(block);
					TrainBlocks.drop(this, block, player);
					changed();
					var g = block.kind.block.getDefaultState().getSoundGroup();
					getWorld().playSound(null, getBlockPos(), g.getBreakSound(), SoundCategory.BLOCKS, 1.0F, g.getPitch() * 0.8F);
				}
				return ActionResult.SUCCESS;
			}
			if (block.kind.campfire()) {
				if (!getWorld().isClient && block.addToCampfire(getWorld(), player.getAbilities().creativeMode ? stack.copy() : stack)) {
					changed();
				}
				return ActionResult.SUCCESS;
			}
			if (!getWorld().isClient) {
				TrainBlocks.open(player, this, block);
				if (block.kind == TrainBlocks.Kind.CHEST) {
					getWorld().playSound(null, getBlockPos(), SoundEvents.BLOCK_CHEST_OPEN, SoundCategory.BLOCKS, 0.6F, 1.0F);
				} else if (block.kind == TrainBlocks.Kind.BARREL) {
					getWorld().playSound(null, getBlockPos(), SoundEvents.BLOCK_BARREL_OPEN, SoundCategory.BLOCKS, 0.6F, 1.0F);
				}
			}
			return ActionResult.SUCCESS;
		}
		Building.Placed aimed = aimed(player);
		if (stack.isOf(Trains.BUILD_HAMMER) && player.isSneaking() && aimed != null) {
			if (!getWorld().isClient) {
				for (Building.Placed p : remove(aimed)) {
					if (!player.getAbilities().creativeMode || p != aimed) {
						give(player, p);
					}
				}
				BlockSoundGroup g = sound(aimed.material);
				getWorld().playSound(null, getBlockPos(), g.getBreakSound(), SoundCategory.BLOCKS, 1.0F, g.getPitch() * 0.8F);
			}
			return ActionResult.SUCCESS;
		}
		ActionResult common = Trains.carInteract(this, player, hand);
		if (common != ActionResult.PASS) {
			return common;
		}
		if (aimed != null && aimed.kind == Building.Kind.DOORWAY) {
			if (!getWorld().isClient) {
				aimed.open = !aimed.open;
				changed();
				boolean metal = aimed.material == Building.Material.METAL || aimed.material == Building.Material.STONE;
				getWorld().playSound(null, getBlockPos(), aimed.open ? metal ? SoundEvents.BLOCK_IRON_DOOR_OPEN : SoundEvents.BLOCK_WOODEN_DOOR_OPEN
						: metal ? SoundEvents.BLOCK_IRON_DOOR_CLOSE : SoundEvents.BLOCK_WOODEN_DOOR_CLOSE, SoundCategory.BLOCKS, 1.0F, 1.0F);
			}
			return ActionResult.SUCCESS;
		}
		return ActionResult.PASS;
	}

	/** Golpes: le quitan vida a la pieza a la que se apunta (más con hachas y picos); al acabarse, se rompe (sin soltar nada). */
	@Override
	public boolean handleAttack(Entity attacker) {
		if (!(attacker instanceof PlayerEntity player) || getWorld().isClient) {
			return true;
		}
		Building.Placed p = aimed(player);
		if (p == null) {
			return true;
		}
		ItemStack tool = player.getMainHandStack();
		float dmg = (float) player.getAttributeValue(EntityAttributes.GENERIC_ATTACK_DAMAGE);
		if (tool.getItem() instanceof AxeItem || tool.getItem() instanceof PickaxeItem) {
			dmg *= 2.0F;
		}
		dmg *= Math.max(0.2F, player.getAttackCooldownProgress(0.5F));
		if (player.getAbilities().creativeMode) {
			dmg = p.hp + 1;
		}
		p.hp -= dmg;
		BlockSoundGroup g = sound(p.material);
		if (p.hp <= 0) {
			remove(p);
			getWorld().playSound(null, getBlockPos(), g.getBreakSound(), SoundCategory.BLOCKS, 1.0F, g.getPitch() * 0.8F);
		} else {
			getWorld().playSound(null, getBlockPos(), g.getHitSound(), SoundCategory.BLOCKS, 1.0F, g.getPitch() * 0.6F);
			player.sendMessage(Text.translatable("message.evolvedwarfare.build.hp", Math.round(p.hp), Math.round(p.maxHp())).formatted(Formatting.GRAY), true);
			changed();
		}
		return true;
	}

	/** Al recogerla: suelta las piezas y los bloques que lleve (con lo que tengan dentro). */
	public void dropContents() {
		for (Building.Placed p : pieces) {
			dropStack(new ItemStack(Trains.pieceItem(p.kind, p.material)));
		}
		for (TrainBlocks.Placed b : blocks) {
			TrainBlocks.drop(this, b, null);
		}
		pieces.clear();
		blocks.clear();
		changed();
	}

	// ------------------------------------------------------------------ bloques (cofres, hornos, fogatas, mesa)

	/** Dónde iría un bloque si se apunta ahí (punto y mirada del mundo): {columna, fila, planta}, o null. */
	@Nullable
	public int[] blockSlotFor(Vec3d hitWorld, Vec3d lookWorld) {
		Vec3d hit = toLocal(hitWorld);
		Vec3d look = toLocal(getPos().add(lookWorld)).subtract(toLocal(getPos()));
		Vec3d q = hit.subtract(look.multiply(0.05));
		int level = MathHelper.floor((q.y - Building.DECK + 0.2) / Building.LEVEL);
		int x = MathHelper.floor(q.x + TrainBlocks.COLS / 2.0), z = MathHelper.floor(q.z + TrainBlocks.ROWS / 2.0);
		if (level < 0 || level >= Building.LEVELS || x < 0 || x >= TrainBlocks.COLS || z < 0 || z >= TrainBlocks.ROWS) {
			return null;
		}
		return new int[] {x, z, level, Building.rotFor(new Vec3d(-look.x, 0, -look.z))};
	}

	/** ¿Cabe un bloque ahí? (libre, sin chocar con paredes ni escaleras, y en la planta de arriba, sobre un techo). */
	public boolean canPlaceBlock(int[] at) {
		for (TrainBlocks.Placed b : blocks) {
			if (b.x == at[0] && b.z == at[1] && b.level == at[2]) {
				return false;
			}
		}
		TrainBlocks.Placed probe = new TrainBlocks.Placed(TrainBlocks.Kind.CHEST, at[0], at[1], at[2], Direction.NORTH);
		Box box = probe.box().contract(0.02);
		for (Building.Placed p : pieces) {
			for (Building.Part part : Building.parts(p)) {
				if (part.solid() && part.box().intersects(box)) {
					return false;
				}
			}
		}
		if (at[2] > 0) {
			double cx = box.getCenter().x, cz = box.getCenter().z;
			boolean floor = false;
			for (Building.Placed p : pieces) {
				if (p.kind == Building.Kind.CEILING && p.level == at[2] - 1) {
					Box b = Building.bounds(p);
					floor |= cx > b.minX && cx < b.maxX && cz > b.minZ && cz < b.maxZ;
				}
			}
			return floor;
		}
		return true;
	}

	/** Pone un bloque (si cabe); para las pruebas y para quien lo necesite. */
	public boolean addBlock(TrainBlocks.Kind kind, int[] at) {
		if (!canPlaceBlock(at)) {
			return false;
		}
		placeBlock(kind, at, null);
		return true;
	}

	private void placeBlock(TrainBlocks.Kind kind, int[] at, @Nullable PlayerEntity player) {
		Direction facing = switch (at[3]) {
			case 0 -> Direction.SOUTH;
			case 1 -> Direction.EAST;
			case 2 -> Direction.NORTH;
			default -> Direction.WEST;
		};
		blocks.add(new TrainBlocks.Placed(kind, at[0], at[1], at[2], facing));
		changed();
	}

	/** El bloque al que se apunta (si está más cerca que cualquier pieza), o null. */
	@Nullable
	public TrainBlocks.Placed aimedBlock(PlayerEntity player) {
		double reach = player.getEntityInteractionRange() + 1.0;
		Vec3d eye = player.getEyePos();
		Vec3d lf = toLocal(eye), lt = toLocal(eye.add(player.getRotationVec(1.0F).multiply(reach)));
		TrainBlocks.Placed best = null;
		double bestD = Double.MAX_VALUE;
		for (TrainBlocks.Placed b : blocks) {
			var hit = b.box().expand(0.01).raycast(lf, lt);
			if (hit.isPresent() && hit.get().squaredDistanceTo(lf) < bestD) {
				bestD = hit.get().squaredDistanceTo(lf);
				best = b;
			}
		}
		if (best == null) {
			return null;
		}
		for (Building.Placed p : pieces) {
			for (Building.Part part : Building.parts(p)) {
				var hit = part.box().raycast(lf, lt);
				if (hit.isPresent() && hit.get().squaredDistanceTo(lf) < bestD - 1e-4) {
					return null;
				}
			}
		}
		return best;
	}

	/** Lo que se calienta cerca (para el clima): cada fogata u horno encendido, con su centro en el mundo. */
	public void heat(java.util.function.BiConsumer<Vec3d, Float> out) {
		for (TrainBlocks.Placed b : blocks) {
			float w = TrainBlocks.warmth(b);
			if (w > 0) {
				Vec3d c = b.box().getCenter();
				out.accept(toWorld(c.x, c.y, c.z), w);
			}
		}
	}

	@Override
	public void tick() {
		super.tick();
		if (blocks.isEmpty()) {
			return;
		}
		if (getWorld().isClient) {
			for (TrainBlocks.Placed b : blocks) {
				if (b.lit && (b.kind.campfire() ? random.nextInt(3) == 0 : random.nextInt(12) == 0)) {
					Box box = b.box();
					Vec3d c = toWorld(box.getCenter().x + (random.nextDouble() - 0.5) * 0.4, box.maxY, box.getCenter().z + (random.nextDouble() - 0.5) * 0.4);
					getWorld().addParticle(b.kind.campfire() ? net.minecraft.particle.ParticleTypes.CAMPFIRE_COSY_SMOKE : net.minecraft.particle.ParticleTypes.SMOKE,
							c.x, c.y, c.z, 0.0, b.kind.campfire() ? 0.07 : 0.02, 0.0);
				}
			}
			return;
		}
		boolean sync = false;
		int[] slot = new int[1];
		for (TrainBlocks.Placed b : blocks) {
			if (b.kind.furnace()) {
				sync |= b.tickFurnace(getWorld());
			} else if (b.kind.campfire()) {
				ItemStack done = b.tickCampfire(getWorld(), slot);
				if (!done.isEmpty()) {
					Box box = b.box();
					Vec3d c = toWorld(box.getCenter().x, box.maxY + 0.2, box.getCenter().z);
					net.minecraft.entity.ItemEntity item = new net.minecraft.entity.ItemEntity(getWorld(), c.x, c.y, c.z, done);
					item.setVelocity(Vec3d.ZERO);
					getWorld().spawnEntity(item);
					sync = true;
				}
			}
		}
		if (sync) {
			changed();
		}
	}

	// ------------------------------------------------------------------ guardar

	/** Lo que se guarda (full) o se manda al cliente (sin lo de dentro de los bloques). */
	private NbtCompound write(boolean full) {
		NbtCompound nbt = new NbtCompound();
		NbtList list = new NbtList();
		for (Building.Placed p : pieces) {
			list.add(p.write());
		}
		nbt.put("List", list);
		NbtList bl = new NbtList();
		for (TrainBlocks.Placed b : blocks) {
			bl.add(b.write(getRegistryManager(), full));
		}
		nbt.put("Blocks", bl);
		return nbt;
	}

	private NbtCompound write() {
		return write(false);
	}

	private void read(NbtCompound nbt) {
		pieces.clear();
		for (NbtElement e : nbt.getList("List", NbtElement.COMPOUND_TYPE)) {
			Building.Placed p = Building.Placed.read((NbtCompound) e);
			if (p != null) {
				pieces.add(p);
			}
		}
		blocks.clear();
		for (NbtElement e : nbt.getList("Blocks", NbtElement.COMPOUND_TYPE)) {
			TrainBlocks.Placed b = TrainBlocks.Placed.read((NbtCompound) e, getRegistryManager());
			if (b != null) {
				blocks.add(b);
			}
		}
		rebuild();
	}

	@Override
	protected void readCustomDataFromNbt(NbtCompound nbt) {
		read(nbt.getCompound("Pieces"));
		dataTracker.set(PIECES, write(false));
		readLink(nbt);
	}

	@Override
	protected void writeCustomDataToNbt(NbtCompound nbt) {
		nbt.put("Pieces", write(true));
		writeLink(nbt);
	}
}
