package com.evolvedwarfare.smithing;

import com.evolvedwarfare.registry.ModBlocks;
import com.evolvedwarfare.registry.ModComponents;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.CampfireBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.component.ComponentMap;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * El crisol puesto en el mundo: sobre una hoguera encendida, fuego, lava o un bloque de magma, funde los lingotes al rojo
 * que se le echan (uno cada {@link #MELT_TICKS} tics) y mantiene el metal fundido. Fuera del fuego, el metal se enfría en
 * {@link #COOL_TICKS} tics y se queda sólido (hay que volver a ponerlo al fuego).
 */
public class CrucibleBlockEntity extends BlockEntity {
	public static final int CAPACITY = 4;
	public static final int MELT_TICKS = 80;
	public static final int COOL_TICKS = 600;

	private CrucibleContents contents = CrucibleContents.EMPTY;
	/** Lo que lleva fundido el lingote de arriba (0-MELT_TICKS). */
	private int melt;
	private boolean heated;

	public CrucibleBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlocks.CRUCIBLE_ENTITY, pos, state);
	}

	public CrucibleContents contents() {
		return contents;
	}

	public float meltFraction() {
		return melt / (float) MELT_TICKS;
	}

	public boolean heated() {
		return heated;
	}

	public void setContents(CrucibleContents contents) {
		this.contents = contents;
		markDirty();
	}

	/** ¿Hay fuego debajo? */
	public static boolean heatBelow(World world, BlockPos pos) {
		BlockState below = world.getBlockState(pos.down());
		if (below.isIn(BlockTags.CAMPFIRES)) {
			return below.getOrEmpty(CampfireBlock.LIT).orElse(false);
		}
		return below.isIn(BlockTags.FIRE) || below.isOf(net.minecraft.block.Blocks.MAGMA_BLOCK)
				|| world.getFluidState(pos.down()).isIn(FluidTags.LAVA);
	}

	/** Echa un lingote al rojo; devuelve si ha cabido. */
	public boolean insert(ForgeMaterial material) {
		if (!contents.metal().isEmpty() && !contents.metal().equals(material.id) && !contents.isEmpty()) {
			return false;
		}
		if (contents.solid() + Math.ceil(contents.molten() - 0.01F) >= CAPACITY) {
			return false;
		}
		contents = new CrucibleContents(material.id, contents.solid() + 1, contents.molten(), contents.coolAt());
		markDirty();
		return true;
	}

	public static void tick(World world, BlockPos pos, BlockState state, CrucibleBlockEntity be) {
		boolean hot = heatBelow(world, pos);
		if (hot != be.heated) {
			be.heated = hot;
			be.markDirty();
		}
		CrucibleContents c = be.contents;
		if (c.isEmpty()) {
			if (!c.metal().isEmpty()) {
				be.contents = CrucibleContents.EMPTY;
				be.markDirty();
			}
			return;
		}
		if (hot) {
			if (c.coolAt() != 0L) {
				be.contents = new CrucibleContents(c.metal(), c.solid(), c.molten(), 0L);
				be.markDirty();
			}
			if (c.solid() > 0) {
				be.melt++;
				if (be.melt % 10 == 0) {
					be.markDirty();
				}
				if (be.melt >= MELT_TICKS) {
					be.melt = 0;
					be.contents = new CrucibleContents(c.metal(), c.solid() - 1, Math.min(CAPACITY, c.molten() + 1.0F), 0L);
					world.playSound(null, pos, SoundEvents.BLOCK_LAVA_POP, SoundCategory.BLOCKS, 0.7F, 0.8F + world.random.nextFloat() * 0.3F);
					be.markDirty();
				}
			}
			return;
		}
		// fuera del fuego: se enfría
		if (c.molten() > 0.01F) {
			if (c.coolAt() == 0L) {
				be.contents = new CrucibleContents(c.metal(), c.solid(), c.molten(), world.getTime() + COOL_TICKS);
				be.markDirty();
			} else if (world.getTime() >= c.coolAt()) {
				be.contents = solidified(c);
				world.playSound(null, pos, SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.BLOCKS, 0.5F, 1.3F);
				be.markDirty();
			}
		}
	}

	/** El metal fundido se queda sólido (lo que no llega a un lingote se pierde). */
	public static CrucibleContents solidified(CrucibleContents c) {
		int solid = c.solid() + (int) Math.floor(c.molten() + 0.05F);
		return solid <= 0 ? CrucibleContents.EMPTY : new CrucibleContents(c.metal(), solid, 0.0F, 0L);
	}

	@Override
	public void markDirty() {
		super.markDirty();
		if (world != null && !world.isClient) {
			world.updateListeners(pos, getCachedState(), getCachedState(), Block.NOTIFY_LISTENERS);
		}
	}

	@Override
	protected void readComponents(ComponentsAccess components) {
		super.readComponents(components);
		CrucibleContents c = components.get(ModComponents.CRUCIBLE);
		contents = c == null ? CrucibleContents.EMPTY : c;
	}

	@Override
	protected void addComponents(ComponentMap.Builder builder) {
		super.addComponents(builder);
		if (!contents.isEmpty()) {
			builder.add(ModComponents.CRUCIBLE, contents);
		}
	}

	@Override
	public void removeFromCopiedStackNbt(NbtCompound nbt) {
		nbt.remove("contents");
	}

	@Override
	protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		super.writeNbt(nbt, registries);
		CrucibleContents.CODEC.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, contents).result().ifPresent(e -> nbt.put("contents", e));
		nbt.putInt("melt", melt);
		nbt.putBoolean("heated", heated);
	}

	@Override
	protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		super.readNbt(nbt, registries);
		contents = nbt.contains("contents") ? CrucibleContents.CODEC.parse(net.minecraft.nbt.NbtOps.INSTANCE, nbt.get("contents")).result()
				.orElse(CrucibleContents.EMPTY) : CrucibleContents.EMPTY;
		melt = nbt.getInt("melt");
		heated = nbt.getBoolean("heated");
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
