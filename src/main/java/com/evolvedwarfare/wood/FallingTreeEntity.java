package com.evolvedwarfare.wood;

import com.evolvedwarfare.registry.ModEntities;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * Un árbol que cae: lleva sus bloques (troncos y hojas) y gira sobre el borde de la base hacia un lado, cada vez más
 * deprisa, hasta quedar tumbado. Al llegar al suelo suena, salen astillas y hojas, hace daño a quien pille debajo y suelta
 * la madera, y lo que den las hojas (brotes, palos, fruta), donde ha caído cada bloque. No se guarda.
 */
public class FallingTreeEntity extends Entity {
	private static final TrackedData<NbtCompound> DATA = DataTracker.registerData(FallingTreeEntity.class, TrackedDataHandlerRegistry.NBT_COMPOUND);
	/** Los bloques, ya leídos (también en el cliente). */
	private int[] states;
	private int[] rel;
	private BlockPos origin = BlockPos.ORIGIN;
	private Direction dir = Direction.NORTH;
	private int duration = 20;
	private int logs;
	private boolean landed;

	public FallingTreeEntity(EntityType<? extends FallingTreeEntity> type, World world) {
		super(type, world);
		noClip = true;
		setNoGravity(true);
	}

	public static FallingTreeEntity create(ServerWorld world, BlockPos origin, Direction dir, int[] states, int[] rel, int logs) {
		FallingTreeEntity tree = new FallingTreeEntity(ModEntities.FALLING_TREE, world);
		int height = 1;
		for (int r : rel) {
			height = Math.max(height, unpackY(r) + 1);
		}
		NbtCompound nbt = new NbtCompound();
		nbt.putIntArray("S", states);
		nbt.putIntArray("P", rel);
		nbt.putLong("O", origin.asLong());
		nbt.putInt("D", dir.getHorizontal());
		nbt.putInt("T", duration(height));
		nbt.putInt("L", logs);
		tree.getDataTracker().set(DATA, nbt);
		tree.read(nbt);
		Vec3d pivot = pivot(origin, dir);
		tree.refreshPositionAndAngles(pivot.x, pivot.y, pivot.z, 0.0F, 0.0F);
		return tree;
	}

	/** Cuánto tarda en caer (en tics): los árboles altos, más. */
	public static int duration(int height) {
		return MathHelper.clamp(14 + height * 2, 18, 60);
	}

	/** El ángulo (grados) a los {@code t} tics: como algo que se vence, cada vez más deprisa. */
	public static float angle(float t, int duration) {
		float f = MathHelper.clamp(t / duration, 0.0F, 1.0F);
		return 90.0F * f * f;
	}

	/** El borde de la base hacia donde cae: sobre él gira. */
	public static Vec3d pivot(BlockPos origin, Direction dir) {
		return new Vec3d(origin.getX() + 0.5 + dir.getOffsetX() * 0.5, origin.getY(), origin.getZ() + 0.5 + dir.getOffsetZ() * 0.5);
	}

	public static int pack(int dx, int dy, int dz) {
		return ((dx + 512) & 1023) << 20 | ((dy + 512) & 1023) << 10 | ((dz + 512) & 1023);
	}

	public static int unpackX(int p) {
		return (p >> 20 & 1023) - 512;
	}

	public static int unpackY(int p) {
		return (p >> 10 & 1023) - 512;
	}

	public static int unpackZ(int p) {
		return (p & 1023) - 512;
	}

	private void read(NbtCompound nbt) {
		states = nbt.getIntArray("S");
		rel = nbt.getIntArray("P");
		origin = BlockPos.fromLong(nbt.getLong("O"));
		dir = Direction.fromHorizontal(nbt.getInt("D"));
		duration = Math.max(1, nbt.getInt("T"));
		logs = nbt.getInt("L");
	}

	private void ensureRead() {
		if (states == null) {
			read(getDataTracker().get(DATA));
		}
	}

	@Override
	public void onTrackedDataSet(TrackedData<?> data) {
		super.onTrackedDataSet(data);
		if (DATA.equals(data)) {
			read(getDataTracker().get(DATA));
		}
	}

	public int[] states() {
		ensureRead();
		return states;
	}

	public int[] rel() {
		ensureRead();
		return rel;
	}

	public BlockPos origin() {
		ensureRead();
		return origin;
	}

	public Direction direction() {
		ensureRead();
		return dir;
	}

	public int duration() {
		ensureRead();
		return duration;
	}

	public int logCount() {
		ensureRead();
		return logs;
	}

	/** Dónde queda el centro de un bloque (relativo a la base) con el árbol girado {@code degrees}. */
	public static Vec3d rotated(BlockPos origin, Direction dir, int packed, float degrees) {
		Vec3d pivot = pivot(origin, dir);
		Vec3d v = new Vec3d(origin.getX() + unpackX(packed) + 0.5, origin.getY() + unpackY(packed) + 0.5, origin.getZ() + unpackZ(packed) + 0.5)
				.subtract(pivot);
		double a = v.x * dir.getOffsetX() + v.z * dir.getOffsetZ();
		double h = v.y;
		Vec3d perp = new Vec3d(v.x - a * dir.getOffsetX(), 0.0, v.z - a * dir.getOffsetZ());
		double r = Math.toRadians(degrees);
		double a2 = a * Math.cos(r) + h * Math.sin(r);
		double h2 = h * Math.cos(r) - a * Math.sin(r);
		return pivot.add(perp).add(dir.getOffsetX() * a2, h2, dir.getOffsetZ() * a2);
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {
		builder.add(DATA, new NbtCompound());
	}

	@Override
	public void tick() {
		super.tick();
		ensureRead();
		if (getWorld() instanceof ServerWorld world && !landed && age >= duration()) {
			landed = true;
			land(world);
			discard();
		}
	}

	private void land(ServerWorld world) {
		Set<LivingEntity> hit = new HashSet<>();
		for (int i = 0; i < states.length; i++) {
			BlockState state = Block.getStateFromRawId(states[i]);
			Vec3d at = rotated(origin, dir, rel[i], 90.0F);
			BlockPos pos = BlockPos.ofFloored(at);
			boolean log = state.isIn(BlockTags.LOGS);
			List<ItemStack> drops = log ? List.of(new ItemStack(state.getBlock())) : Block.getDroppedStacks(state, world, pos, null);
			for (ItemStack stack : drops) {
				ItemEntity item = new ItemEntity(world, at.x, at.y, at.z, stack, (random.nextDouble() - 0.5) * 0.1, 0.15, (random.nextDouble() - 0.5) * 0.1);
				item.setToDefaultPickupDelay();
				world.spawnEntity(item);
			}
			if (i % 3 == 0) {
				world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, state), at.x, at.y, at.z, 4, 0.35, 0.35, 0.35, 0.05);
			}
			if (log) {
				hit.addAll(world.getEntitiesByClass(LivingEntity.class, new Box(pos).expand(0.2), LivingEntity::isAlive));
			}
		}
		DamageSource source = world.getDamageSources().fallingBlock(this);
		for (LivingEntity e : hit) {
			e.damage(source, Math.min(10.0F, 3.0F + logs * 0.5F));
		}
		Vec3d mid = rotated(origin, dir, pack(0, Math.max(1, logs / 2), 0), 90.0F);
		world.playSound(null, mid.x, mid.y, mid.z, SoundEvents.ENTITY_GENERIC_BIG_FALL, SoundCategory.BLOCKS, 2.0F, 0.6F);
		world.playSound(null, mid.x, mid.y, mid.z, SoundEvents.BLOCK_WOOD_BREAK, SoundCategory.BLOCKS, 1.5F, 0.7F);
		world.playSound(null, mid.x, mid.y, mid.z, SoundEvents.BLOCK_AZALEA_LEAVES_BREAK, SoundCategory.BLOCKS, 1.5F, 0.8F);
	}

	@Override
	public boolean damage(DamageSource source, float amount) {
		return false;
	}

	@Override
	public boolean shouldSave() {
		return false;
	}

	@Override
	protected void readCustomDataFromNbt(NbtCompound nbt) {
	}

	@Override
	protected void writeCustomDataToNbt(NbtCompound nbt) {
	}
}
