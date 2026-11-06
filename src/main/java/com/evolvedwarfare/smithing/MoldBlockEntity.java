package com.evolvedwarfare.smithing;

import com.evolvedwarfare.registry.ModBlocks;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * Un molde de arcilla puesto en el suelo. Pasa por: en blanco → tallado (con la forma de una pieza, del minijuego) →
 * llenándose (se vierte el metal del crisol) → enfriándose → sólido (se rompe el molde a golpes y sale la pieza en bruto).
 *
 * <p>Lo bien tallado ({@link #moldScore}) y lo bien vertido (ni de menos ni de más) dan la nota de la pieza en bruto, que
 * luego se termina de afilar en el yunque.</p>
 */
public class MoldBlockEntity extends BlockEntity {
	public static final int BLANK = 0;
	public static final int CARVED = 1;
	public static final int POURING = 2;
	public static final int COOLING = 3;
	public static final int SOLID = 4;
	/** Tics sin verter tras los que el metal empieza a cuajar. */
	public static final int SET_TICKS = 30;
	public static final int COOL_TICKS = 100;
	/** Lo más que cabe, sobre lo que pide la pieza (lo demás se derrama). */
	public static final float OVERFLOW = 1.15F;
	public static final int CRACKS = 3;

	private int stage = BLANK;
	private int part = -1;
	private final long[] carved = new long[4];
	private float moldScore;
	private String metal = "";
	private float fill;
	private float pourScore;
	private long lastPour;
	private int cool;
	private int cracks;
	private boolean spilled;

	public MoldBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlocks.CLAY_MOLD_ENTITY, pos, state);
	}

	public int stage() {
		return stage;
	}

	@Nullable
	public ForgedPart part() {
		return part < 0 || part >= ForgedPart.values().length ? null : ForgedPart.values()[part];
	}

	public long[] carved() {
		return carved;
	}

	public boolean isCarved(int x, int y) {
		int i = y * 16 + x;
		return (carved[i >> 6] >>> (i & 63) & 1L) != 0L;
	}

	public float fill() {
		return fill;
	}

	public float target() {
		ForgedPart p = part();
		return p == null ? 1.0F : p.cost;
	}

	public String metal() {
		return metal;
	}

	/** Lo caliente que está el metal (1 recién vertido, 0 frío). */
	public float heat() {
		return switch (stage) {
			case POURING -> 1.0F;
			case COOLING -> cool / (float) COOL_TICKS;
			default -> 0.0F;
		};
	}

	public int cracks() {
		return cracks;
	}

	public float moldScore() {
		return moldScore;
	}

	public float pourScore() {
		return pourScore;
	}

	/** La nota de la pieza en bruto: el molde y el vertido. */
	public float castScore() {
		return moldScore * 0.55F + pourScore * 0.45F;
	}

	/** El minijuego de tallar ha terminado: la forma y lo bien que ha salido. */
	public boolean carve(ForgedPart chosen, long[] bits, float score) {
		if (stage != BLANK) {
			return false;
		}
		part = chosen.ordinal();
		System.arraycopy(bits, 0, carved, 0, 4);
		moldScore = Math.max(0.0F, Math.min(1.0F, score));
		stage = CARVED;
		markDirty();
		return true;
	}

	/** ¿Se puede verter ese metal aquí? (las armas medievales no son de oro). */
	public boolean accepts(ForgeMaterial m) {
		ForgedPart p = part();
		if (p == null || m == null || m == ForgeMaterial.DIAMOND || (stage != CARVED && stage != POURING)) {
			return false;
		}
		return p.result(m) != null && (metal.isEmpty() || metal.equals(m.id));
	}

	/** Vierte {@code amount} lingotes de metal; devuelve lo que se ha gastado (lo que rebosa también se gasta). */
	public float pour(ForgeMaterial m, float amount) {
		if (!accepts(m) || amount <= 0.0F || world == null) {
			return 0.0F;
		}
		metal = m.id;
		float room = target() * OVERFLOW - fill;
		fill += Math.min(amount, Math.max(0.0F, room));
		if (amount > room) {
			spilled = true;
			if (world instanceof ServerWorld sw) {
				sw.spawnParticles(ParticleTypes.LAVA, pos.getX() + 0.5, pos.getY() + 0.3, pos.getZ() + 0.5, 2, 0.4, 0.05, 0.4, 0.0);
				sw.spawnParticles(ParticleTypes.FLAME, pos.getX() + 0.5, pos.getY() + 0.3, pos.getZ() + 0.5, 3, 0.45, 0.02, 0.45, 0.01);
			}
		}
		boolean first = stage == CARVED;
		stage = POURING;
		lastPour = world.getTime();
		if (first || world.getTime() % 3 == 0) {
			markDirty();
		}
		return amount;
	}

	/** Lo bien vertido: lo justo (de 97 a 103 %) es perfecto; de menos, la pieza sale incompleta; de más, con rebabas. */
	public static float pourScore(float fill, float target, boolean spilled) {
		float r = fill / target;
		float s = r < 0.97F ? 1.0F - (0.97F - r) * 3.0F : r <= 1.03F ? 1.0F : 1.0F - (r - 1.03F) * 5.0F;
		if (spilled) {
			s -= 0.15F;
		}
		return Math.max(0.0F, Math.min(1.0F, s));
	}

	public static void tick(World world, BlockPos pos, BlockState state, MoldBlockEntity be) {
		if (be.stage == POURING && world.getTime() - be.lastPour > SET_TICKS) {
			be.stage = COOLING;
			be.cool = COOL_TICKS;
			be.pourScore = pourScore(be.fill, be.target(), be.spilled);
			world.playSound(null, pos, SoundEvents.BLOCK_LAVA_EXTINGUISH, SoundCategory.BLOCKS, 0.6F, 1.1F);
			be.markDirty();
		} else if (be.stage == COOLING) {
			be.cool--;
			if (world instanceof ServerWorld sw && be.cool % 8 == 0) {
				sw.spawnParticles(ParticleTypes.WHITE_SMOKE, pos.getX() + 0.5, pos.getY() + 0.3, pos.getZ() + 0.5, 1, 0.25, 0.02, 0.25, 0.01);
			}
			if (be.cool % 10 == 0) {
				be.markDirty();
			}
			if (be.cool <= 0) {
				be.stage = SOLID;
				world.playSound(null, pos, SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.BLOCKS, 0.4F, 1.6F);
				be.markDirty();
			}
		}
	}

	/** Un golpe al molde ya frío: a los {@link #CRACKS} se rompe y suelta la pieza. Devuelve la pieza si ha salido. */
	@Nullable
	public ItemStack crack() {
		if (stage != SOLID || world == null) {
			return null;
		}
		cracks++;
		world.playSound(null, pos, SoundEvents.BLOCK_DECORATED_POT_HIT, SoundCategory.BLOCKS, 1.0F, 0.6F + cracks * 0.15F);
		if (world instanceof ServerWorld sw) {
			sw.spawnParticles(new net.minecraft.particle.BlockStateParticleEffect(ParticleTypes.BLOCK, net.minecraft.block.Blocks.CLAY.getDefaultState()),
					pos.getX() + 0.5, pos.getY() + 0.25, pos.getZ() + 0.5, 12, 0.3, 0.1, 0.3, 0.1);
		}
		markDirty();
		return cracks >= CRACKS ? result() : null;
	}

	/** La pieza en bruto que lleva dentro (si está sólida). */
	@Nullable
	public ItemStack result() {
		ForgedPart p = part();
		ForgeMaterial m = ForgeMaterial.byId(metal);
		if (p == null || m == null || (stage != SOLID && stage != COOLING)) {
			return null;
		}
		return Smithing.rough(p, m, castScore());
	}

	@Override
	public void markDirty() {
		super.markDirty();
		if (world != null && !world.isClient) {
			world.updateListeners(pos, getCachedState(), getCachedState(), Block.NOTIFY_LISTENERS);
		}
	}

	@Override
	protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		super.writeNbt(nbt, registries);
		nbt.putInt("stage", stage);
		nbt.putInt("part", part);
		nbt.putLongArray("carved", carved);
		nbt.putFloat("mold_score", moldScore);
		nbt.putString("metal", metal);
		nbt.putFloat("fill", fill);
		nbt.putFloat("pour_score", pourScore);
		nbt.putLong("last_pour", lastPour);
		nbt.putInt("cool", cool);
		nbt.putInt("cracks", cracks);
		nbt.putBoolean("spilled", spilled);
	}

	@Override
	protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		super.readNbt(nbt, registries);
		stage = nbt.getInt("stage");
		part = nbt.contains("part") ? nbt.getInt("part") : -1;
		long[] bits = nbt.getLongArray("carved");
		for (int i = 0; i < 4; i++) {
			carved[i] = i < bits.length ? bits[i] : 0L;
		}
		moldScore = nbt.getFloat("mold_score");
		metal = nbt.getString("metal");
		fill = nbt.getFloat("fill");
		pourScore = nbt.getFloat("pour_score");
		lastPour = nbt.getLong("last_pour");
		cool = nbt.getInt("cool");
		cracks = nbt.getInt("cracks");
		spilled = nbt.getBoolean("spilled");
	}

	@Nullable
	@Override
	public Packet<ClientPlayPacketListener> toUpdatePacket() {
		return BlockEntityUpdateS2CPacket.create(this);
	}

	@Override
	public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup registries) {
		return createNbt(registries);
	}
}
