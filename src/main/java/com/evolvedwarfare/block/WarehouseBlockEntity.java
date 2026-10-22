package com.evolvedwarfare.block;

import com.evolvedwarfare.logistics.StorageNetwork;
import com.evolvedwarfare.registry.ModBlocks;
import com.evolvedwarfare.screen.WarehouseScreenHandler;
import java.util.UUID;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

/**
 * Almacén: no guarda nada él mismo; une los cofres y barriles de alrededor en una red ({@link StorageNetwork}) y es el
 * terminal para verla entera, buscar y sacar o meter cosas. Los mensajeros llevan aquí la producción.
 */
public class WarehouseBlockEntity extends BlockEntity implements ExtendedScreenHandlerFactory<BlockPos> {
	@Nullable
	private UUID owner;

	public WarehouseBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlocks.WAREHOUSE_ENTITY, pos, state);
	}

	@Nullable
	public UUID getOwner() {
		return owner;
	}

	public void setOwner(@Nullable UUID owner) {
		this.owner = owner;
		markDirty();
	}

	public boolean canAccess(PlayerEntity player) {
		return owner == null || owner.equals(player.getUuid()) || player.isCreative();
	}

	public StorageNetwork network() {
		return StorageNetwork.around(world, pos);
	}

	@Override
	public BlockPos getScreenOpeningData(ServerPlayerEntity player) {
		return pos;
	}

	@Override
	public Text getDisplayName() {
		return getCachedState().getBlock().getName();
	}

	@Override
	public ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity player) {
		return new WarehouseScreenHandler(syncId, playerInventory, this);
	}

	@Override
	protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		super.writeNbt(nbt, registries);
		if (owner != null) {
			nbt.putUuid("Owner", owner);
		}
	}

	@Override
	protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		super.readNbt(nbt, registries);
		owner = nbt.containsUuid("Owner") ? nbt.getUuid("Owner") : null;
	}
}
