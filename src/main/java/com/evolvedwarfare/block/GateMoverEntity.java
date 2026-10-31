package com.evolvedwarfare.block;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.Registries;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * Un rastrillo o un puente levadizo mientras se mueve: el torno quita sus bloques, esta entidad los lleva (y el cliente
 * los dibuja moviéndose poco a poco) y al acabar los vuelve a poner en su sitio nuevo.
 * <ul>
 * <li><b>Rastrillo</b> (deslizar): sube o baja {@code dy} bloques. Mientras baja, aplasta a quien pille debajo; mientras
 * se mueve, sigue cerrando el paso.</li>
 * <li><b>Puente</b> (girar): un cuarto de vuelta sobre la fila de la bisagra; de tumbado (bajado) a de pie (subido) o al
 * revés. Los bloques se guardan como si estuviera bajado: la bisagra en {@code (0,0,0)} y la punta hacia {@code out}.</li>
 * </ul>
 * Si se quita la entidad antes de acabar (por un comando, por ejemplo), pone los bloques en su sitio final igualmente.
 */
public class GateMoverEntity extends Entity {
	private static final TrackedData<NbtCompound> GATE = DataTracker.registerData(GateMoverEntity.class, TrackedDataHandlerRegistry.NBT_COMPOUND);
	private static final float CRUSH_DAMAGE = 6.0F;

	// lo que se mueve (relativo a la posición de la entidad, que es la esquina de un bloque)
	private final List<BlockPos> blocks = new ArrayList<>();
	private final List<BlockState> states = new ArrayList<>();
	private boolean swing;
	private int dy;
	private Direction out = Direction.NORTH;
	private boolean raise;
	private long start;
	private int duration = 1;
	@Nullable
	private BlockPos winch;
	private boolean crush;
	private boolean finished;
	private final Set<UUID> crushed = new HashSet<>();

	public GateMoverEntity(EntityType<? extends GateMoverEntity> type, World world) {
		super(type, world);
		this.noClip = true;
	}

	/** El rastrillo: sube (dy > 0) o baja (dy < 0) en {@code duration} tics. */
	public static GateMoverEntity slide(World world, BlockPos origin, List<BlockPos> rel, List<BlockState> states, int dy, int duration,
			BlockPos winch) {
		GateMoverEntity mover = create(world, origin, rel, states, duration, winch);
		mover.swing = false;
		mover.dy = dy;
		mover.crush = dy < 0;
		mover.sync();
		return mover;
	}

	/** El puente: sube ('raise') o baja girando sobre la bisagra; rel, como si estuviera bajado (la punta hacia out). */
	public static GateMoverEntity swing(World world, BlockPos origin, List<BlockPos> rel, List<BlockState> states, Direction out,
			boolean raise, int duration, BlockPos winch) {
		GateMoverEntity mover = create(world, origin, rel, states, duration, winch);
		mover.swing = true;
		mover.out = out;
		mover.raise = raise;
		mover.sync();
		return mover;
	}

	private static GateMoverEntity create(World world, BlockPos origin, List<BlockPos> rel, List<BlockState> states, int duration,
			BlockPos winch) {
		GateMoverEntity mover = new GateMoverEntity(com.evolvedwarfare.registry.ModEntities.GATE_MOVER, world);
		mover.refreshPositionAndAngles(origin.getX(), origin.getY(), origin.getZ(), 0.0F, 0.0F);
		mover.blocks.addAll(rel);
		mover.states.addAll(states);
		mover.duration = Math.max(1, duration);
		mover.start = world.getTime();
		mover.winch = winch.toImmutable();
		return mover;
	}

	// ------------------------------------------------------------------ datos (también para el cliente)

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {
		builder.add(GATE, new NbtCompound());
	}

	private NbtCompound gateNbt() {
		NbtCompound nbt = new NbtCompound();
		nbt.putBoolean("Swing", swing);
		nbt.putInt("Dy", dy);
		nbt.putInt("Out", out.getId());
		nbt.putBoolean("Raise", raise);
		nbt.putLong("Start", start);
		nbt.putInt("Duration", duration);
		nbt.putBoolean("Crush", crush);
		if (winch != null) {
			nbt.putLong("Winch", winch.asLong());
		}
		NbtList list = new NbtList();
		for (int i = 0; i < blocks.size(); i++) {
			NbtCompound entry = new NbtCompound();
			entry.putLong("Pos", blocks.get(i).asLong());
			entry.put("State", NbtHelper.fromBlockState(states.get(i)));
			list.add(entry);
		}
		nbt.put("Blocks", list);
		return nbt;
	}

	private void readGate(NbtCompound nbt) {
		swing = nbt.getBoolean("Swing");
		dy = nbt.getInt("Dy");
		out = Direction.byId(nbt.getInt("Out"));
		raise = nbt.getBoolean("Raise");
		start = nbt.getLong("Start");
		duration = Math.max(1, nbt.getInt("Duration"));
		crush = nbt.getBoolean("Crush");
		winch = nbt.contains("Winch") ? BlockPos.fromLong(nbt.getLong("Winch")) : null;
		blocks.clear();
		states.clear();
		NbtList list = nbt.getList("Blocks", NbtElement.COMPOUND_TYPE);
		for (int i = 0; i < list.size(); i++) {
			NbtCompound entry = list.getCompound(i);
			blocks.add(BlockPos.fromLong(entry.getLong("Pos")));
			states.add(NbtHelper.toBlockState(Registries.BLOCK.getReadOnlyWrapper(), entry.getCompound("State")));
		}
	}

	private void sync() {
		getDataTracker().set(GATE, gateNbt());
	}

	@Override
	public void onTrackedDataSet(TrackedData<?> data) {
		super.onTrackedDataSet(data);
		if (GATE.equals(data) && getWorld().isClient) {
			readGate(getDataTracker().get(GATE));
		}
	}

	@Override
	protected void readCustomDataFromNbt(NbtCompound nbt) {
		readGate(nbt.getCompound("Gate"));
		sync();
	}

	@Override
	protected void writeCustomDataToNbt(NbtCompound nbt) {
		nbt.put("Gate", gateNbt());
	}

	// ------------------------------------------------------------------ el movimiento

	public List<BlockPos> blocks() {
		return blocks;
	}

	public List<BlockState> states() {
		return states;
	}

	public boolean isSwing() {
		return swing;
	}

	public Direction out() {
		return out;
	}

	public int dy() {
		return dy;
	}

	/** Cuánto lleva (0..1), suavizado: el rastrillo arranca y frena; el puente al bajar cae cada vez más rápido. */
	public float progress(float tickDelta) {
		float t = MathHelper.clamp((getWorld().getTime() - start + tickDelta) / duration, 0.0F, 1.0F);
		if (swing && !raise) {
			return t * t;
		}
		return t * t * (3.0F - 2.0F * t);
	}

	/** Puente: el ángulo (grados) sobre la bisagra; 0 = bajado, 90 = subido. */
	public float angle(float tickDelta) {
		float p = progress(tickDelta);
		return 90.0F * (raise ? p : 1.0F - p);
	}

	/** Rastrillo: cuánto se ha desplazado (bloques). */
	public float offset(float tickDelta) {
		return dy * progress(tickDelta);
	}

	/** Lo que ocupa ahora el rastrillo (para que siga cerrando el paso y para aplastar). */
	private Box slideBox() {
		double off = offset(0.0F);
		Box box = null;
		for (BlockPos rel : blocks) {
			Box b = new Box(getX() + rel.getX(), getY() + rel.getY() + off, getZ() + rel.getZ(),
					getX() + rel.getX() + 1, getY() + rel.getY() + 1 + off, getZ() + rel.getZ() + 1);
			box = box == null ? b : box.union(b);
		}
		return box != null ? box : getBoundingBox();
	}

	@Override
	public void tick() {
		super.tick();
		if (!swing && !blocks.isEmpty()) {
			setBoundingBox(slideBox());
		}
		World world = getWorld();
		if (world.isClient || finished) {
			return;
		}
		long elapsed = world.getTime() - start;
		if (elapsed % 6 == 0) {
			world.playSound(null, getBlockPos(), SoundEvents.BLOCK_CHAIN_PLACE, SoundCategory.BLOCKS, 0.7F, swing ? 0.55F : 0.65F);
		}
		if (swing && elapsed % 12 == 3) {
			world.playSound(null, getBlockPos(), SoundEvents.BLOCK_WOODEN_DOOR_OPEN, SoundCategory.BLOCKS, 0.5F, 0.5F);
		}
		if (crush) {
			crushTick();
		}
		if (elapsed >= duration) {
			finish();
		}
	}

	/** Al bajar, a quien pille debajo del rastrillo le da un buen golpe (una vez). */
	private void crushTick() {
		double off = offset(0.0F);
		for (BlockPos rel : blocks) {
			Box below = new Box(getX() + rel.getX() + 0.1, getY() + rel.getY() + off, getZ() + rel.getZ() + 0.1,
					getX() + rel.getX() + 0.9, getY() + rel.getY() + off + 0.4, getZ() + rel.getZ() + 0.9);
			for (LivingEntity entity : getWorld().getEntitiesByClass(LivingEntity.class, below, LivingEntity::isAlive)) {
				if (crushed.add(entity.getUuid())) {
					entity.damage(getWorld().getDamageSources().fallingBlock(this), CRUSH_DAMAGE);
				}
			}
		}
	}

	/** Dónde queda cada bloque al acabar. */
	public BlockPos finalPos(int i) {
		BlockPos origin = getBlockPos();
		BlockPos rel = blocks.get(i);
		if (!swing) {
			return origin.add(rel).up(dy);
		}
		if (!raise) {
			return origin.add(rel);
		}
		int k = rel.getX() * out.getOffsetX() + rel.getZ() * out.getOffsetZ();
		return origin.add(rel).offset(out, -k).up(k);
	}

	private void placeBlocks() {
		World world = getWorld();
		for (int i = 0; i < blocks.size(); i++) {
			BlockPos at = finalPos(i);
			BlockState there = world.getBlockState(at);
			if (there.isAir() || there.isReplaceable()) {
				world.setBlockState(at, states.get(i), Block.NOTIFY_ALL);
			} else {
				// alguien ha puesto algo en medio: la pieza cae al suelo
				Block.dropStack(world, at, new ItemStack(states.get(i).getBlock()));
			}
		}
	}

	private void finish() {
		if (finished) {
			return;
		}
		finished = true;
		World world = getWorld();
		placeBlocks();
		if (swing) {
			world.playSound(null, getBlockPos(), raise ? SoundEvents.BLOCK_WOODEN_TRAPDOOR_CLOSE : SoundEvents.ENTITY_ZOMBIE_ATTACK_WOODEN_DOOR,
					SoundCategory.BLOCKS, raise ? 1.0F : 0.8F, 0.5F);
		} else {
			world.playSound(null, getBlockPos().up(Math.max(0, dy)), dy < 0 ? SoundEvents.BLOCK_ANVIL_LAND : SoundEvents.BLOCK_IRON_DOOR_CLOSE,
					SoundCategory.BLOCKS, dy < 0 ? 0.5F : 1.0F, 0.5F);
		}
		if (winch != null && world.getBlockEntity(winch) instanceof GateWinchBlockEntity entity) {
			entity.finishMove(getUuid());
		}
		discard();
	}

	/** Quitada antes de acabar (un comando...): deja los bloques en su sitio final, para no perderlos. */
	@Override
	public void remove(Entity.RemovalReason reason) {
		if (!getWorld().isClient && reason.shouldDestroy() && !finished) {
			finished = true;
			placeBlocks();
		}
		super.remove(reason);
	}

	// ------------------------------------------------------------------ como bloque

	/** El rastrillo sigue cerrando el paso mientras se mueve. */
	@Override
	public boolean isCollidable() {
		return !swing;
	}

	@Override
	public boolean canHit() {
		return false;
	}

	@Override
	public boolean isAttackable() {
		return false;
	}

	@Override
	public boolean damage(DamageSource source, float amount) {
		return false;
	}

	@Override
	public boolean shouldRender(double distance) {
		return distance < 160.0 * 160.0;
	}

	@Override
	public boolean isFireImmune() {
		return true;
	}
}
