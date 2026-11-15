package com.evolvedwarfare.engineering;

import java.util.Optional;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * Lo que cuelga de una cuerda (fase 74): la plataforma del elevador (bajo una polea; te subes con clic derecho) o el gancho
 * de la grúa (coge el bloque en el que se apoya y lo suelta donde se le baje). No cae ni se mueve sola: la mueve su polea o
 * su grúa, y si esta desaparece, se suelta.
 */
public class HoistEntity extends Entity {
	private static final TrackedData<Float> ROPE_TOP = DataTracker.registerData(HoistEntity.class, TrackedDataHandlerRegistry.FLOAT);
	private static final TrackedData<Optional<BlockState>> CARRIED = DataTracker.registerData(HoistEntity.class,
			TrackedDataHandlerRegistry.OPTIONAL_BLOCK_STATE);
	/** Lo más larga que llega a ser la cuerda. */
	public static final int MAX_ROPE = 64;
	/** Bloques por tic a 16 rpm. */
	public static final double SPEED = 0.1;
	public static final int MAX_RIDERS = 4;

	@Nullable
	private BlockPos anchor;
	/** Lo que tiene que moverse este tic (lo pone su polea o grúa). */
	double wantDy;

	public HoistEntity(EntityType<? extends HoistEntity> type, World world) {
		super(type, world);
		noClip = true;
		setNoGravity(true);
	}

	public boolean isHook() {
		return getType() == Engineering.CRANE_HOOK;
	}

	public float ropeTop() {
		return getDataTracker().get(ROPE_TOP);
	}

	public Optional<BlockState> carried() {
		return getDataTracker().get(CARRIED);
	}

	void link(BlockPos anchor, float ropeTop) {
		this.anchor = anchor.toImmutable();
		getDataTracker().set(ROPE_TOP, ropeTop);
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {
		builder.add(ROPE_TOP, 0.0F);
		builder.add(CARRIED, Optional.empty());
	}

	/** La caja que tiene que estar libre: con el bloque que lleva colgado, o con sitio para quien va subido. */
	private Box space(boolean up) {
		Box b = getBoundingBox().contract(0.02, 0.0, 0.02);
		if (carried().isPresent()) {
			b = b.union(new Box(getX() - 0.48, getY() - 0.98, getZ() - 0.48, getX() + 0.48, getY(), getZ() + 0.48));
		}
		if (up && hasPassengers()) {
			b = b.withMaxY(b.maxY + 1.9);
		}
		return b;
	}

	/** Lo que se puede mover de verdad (se para al tocar algo, al tope de la cuerda o arriba del todo). */
	double allowed(double dy) {
		double top = ropeTop() - getHeight();
		double bottom = ropeTop() - MAX_ROPE;
		if (dy > 0) {
			dy = Math.min(dy, top - getY());
		} else {
			dy = Math.max(dy, bottom - getY());
		}
		if (Math.abs(dy) < 1e-4) {
			return 0.0;
		}
		Box b = space(dy > 0);
		for (double f : new double[] {1.0, 0.5, 0.25, 0.1, 0.05}) {
			if (getWorld().isSpaceEmpty(this, b.offset(0.0, dy * f, 0.0))) {
				return dy * f;
			}
		}
		return 0.0;
	}

	/** ¿Cabe aquí (para colgarla o mover la pluma)? */
	public boolean fitsAt(double x, double y, double z) {
		double ox = getX(), oy = getY(), oz = getZ();
		setPosition(x, y, z);
		boolean ok = getWorld().isSpaceEmpty(this, space(false));
		setPosition(ox, oy, oz);
		return ok;
	}

	@Override
	public void tick() {
		super.tick();
		if (getWorld().isClient) {
			return;
		}
		if (age % 20 == 0 && !anchored()) {
			breakOff();
			return;
		}
		double dy = allowed(wantDy);
		wantDy = 0.0;
		if (dy != 0.0) {
			setPosition(getX(), getY() + dy, getZ());
		}
		setVelocity(0.0, dy, 0.0);
		velocityModified = true;
	}

	/** ¿Sigue colgada de su polea o grúa (y es la suya)? */
	private boolean anchored() {
		if (anchor == null || !getWorld().isChunkLoaded(anchor)) {
			return anchor != null;
		}
		return getWorld().getBlockEntity(anchor) instanceof MachineBlockEntity be && getUuid().equals(be.hoist)
				&& (isHook() ? be.getCachedState().getBlock() instanceof CraneBlock : be.getCachedState().getBlock() instanceof PulleyBlock);
	}

	/** Se suelta: la plataforma cae como objeto; el gancho suelta lo que llevaba. */
	void breakOff() {
		if (getWorld() instanceof ServerWorld sw) {
			if (isHook()) {
				carried().ifPresent(state -> dropStack(new ItemStack(state.getBlock())));
			} else {
				dropStack(new ItemStack(Engineering.LIFT_PLATFORM));
			}
		}
		discard();
	}

	@Override
	public ActionResult interact(PlayerEntity player, Hand hand) {
		if (isHook()) {
			if (!getWorld().isClient && hand == Hand.MAIN_HAND) {
				grabOrRelease(player);
			}
			return ActionResult.SUCCESS;
		}
		if (player.shouldCancelInteraction() || getPassengerList().size() >= MAX_RIDERS) {
			return ActionResult.PASS;
		}
		if (!getWorld().isClient) {
			return player.startRiding(this) ? ActionResult.CONSUME : ActionResult.PASS;
		}
		return ActionResult.SUCCESS;
	}

	/** El gancho: coge el bloque en el que se apoya, o suelta debajo el que lleva. Devuelve si lo ha hecho. */
	public boolean grabOrRelease(@Nullable PlayerEntity player) {
		World world = getWorld();
		BlockPos below = BlockPos.ofFloored(getX(), getY() - 0.5, getZ());
		if (carried().isEmpty()) {
			BlockState state = world.getBlockState(below);
			boolean resting = getY() - (below.getY() + 1) < 0.2;
			if (!resting || state.isAir() || state.hasBlockEntity() || state.getHardness(world, below) < 0 || !state.getFluidState().isEmpty()
					|| state.getCollisionShape(world, below).isEmpty()) {
				if (player != null) {
					player.sendMessage(Text.translatable("message.evolvedwarfare.hoist.cant_grab").formatted(Formatting.YELLOW), true);
				}
				return false;
			}
			world.setBlockState(below, Blocks.AIR.getDefaultState());
			getDataTracker().set(CARRIED, Optional.of(state));
			return true;
		}
		BlockState have = world.getBlockState(below);
		if (!have.isReplaceable()) {
			if (player != null) {
				player.sendMessage(Text.translatable("message.evolvedwarfare.hoist.cant_drop").formatted(Formatting.YELLOW), true);
			}
			return false;
		}
		world.setBlockState(below, carried().get());
		getDataTracker().set(CARRIED, Optional.empty());
		return true;
	}

	@Override
	public boolean canHit() {
		return !isRemoved();
	}

	@Override
	public boolean damage(DamageSource source, float amount) {
		if (isHook() || getWorld().isClient || isRemoved() || !(source.getAttacker() instanceof PlayerEntity player)) {
			return false;
		}
		// la plataforma se recoge a golpes
		if (!player.isCreative()) {
			dropStack(new ItemStack(Engineering.LIFT_PLATFORM));
		}
		discard();
		return true;
	}

	@Override
	protected boolean canAddPassenger(Entity passenger) {
		return !isHook() && getPassengerList().size() < MAX_RIDERS;
	}

	@Override
	public boolean shouldRender(double distance) {
		return distance < 128 * 128;
	}

	@Override
	protected void readCustomDataFromNbt(NbtCompound nbt) {
		if (nbt.contains("Anchor")) {
			anchor = NbtHelper.toBlockPos(nbt, "Anchor").orElse(null);
		}
		getDataTracker().set(ROPE_TOP, nbt.getFloat("RopeTop"));
		if (nbt.contains("Carried")) {
			getDataTracker().set(CARRIED, Optional.of(NbtHelper.toBlockState(getWorld().createCommandRegistryWrapper(RegistryKeys.BLOCK),
					nbt.getCompound("Carried"))));
		}
	}

	@Override
	protected void writeCustomDataToNbt(NbtCompound nbt) {
		if (anchor != null) {
			nbt.put("Anchor", NbtHelper.fromBlockPos(anchor));
		}
		nbt.putFloat("RopeTop", ropeTop());
		carried().ifPresent(state -> nbt.put("Carried", NbtHelper.fromBlockState(state)));
	}
}
