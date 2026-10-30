package com.evolvedwarfare.block;

import com.evolvedwarfare.item.CropCrateItem;
import com.evolvedwarfare.registry.ModBlocks;
import com.evolvedwarfare.registry.ModComponents;
import com.evolvedwarfare.screen.CropCrateScreenHandler;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.component.ComponentMap;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventories;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

/**
 * Lo que hay dentro de una Caja de cultivos: un solo hueco con hasta 64 de un cultivo. Se ve desde fuera (el cliente
 * recibe lo que lleva), la llenan también las tolvas y, al romperla o cogerla, lo que lleva se queda en la caja.
 */
public class CropCrateBlockEntity extends BlockEntity implements Inventory, ExtendedScreenHandlerFactory<BlockPos> {
	private final DefaultedList<ItemStack> items = DefaultedList.ofSize(1, ItemStack.EMPTY);

	public CropCrateBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlocks.CROP_CRATE_ENTITY, pos, state);
	}

	public ItemStack contents() {
		return items.get(0);
	}

	// ------------------------------------------------------------------ inventario (1 hueco)

	@Override
	public int size() {
		return 1;
	}

	@Override
	public boolean isEmpty() {
		return items.get(0).isEmpty();
	}

	@Override
	public ItemStack getStack(int slot) {
		return items.get(0);
	}

	@Override
	public ItemStack removeStack(int slot, int amount) {
		ItemStack taken = Inventories.splitStack(items, 0, amount);
		if (!taken.isEmpty()) {
			markDirty();
		}
		return taken;
	}

	@Override
	public ItemStack removeStack(int slot) {
		ItemStack taken = Inventories.removeStack(items, 0);
		markDirty();
		return taken;
	}

	@Override
	public void setStack(int slot, ItemStack stack) {
		if (!stack.isEmpty() && stack.getCount() > CropCrateItem.capacity(stack.getItem())) {
			stack.setCount(CropCrateItem.capacity(stack.getItem()));
		}
		items.set(0, stack);
		markDirty();
	}

	@Override
	public int getMaxCountPerStack() {
		return CropCrateItem.CAPACITY;
	}

	/** Solo cultivos, y del mismo que ya tenga. */
	@Override
	public boolean isValid(int slot, ItemStack stack) {
		ItemStack inside = items.get(0);
		return CropCrateItem.accepts(stack) && (inside.isEmpty() || ItemStack.areItemsAndComponentsEqual(inside, stack));
	}

	@Override
	public boolean canPlayerUse(PlayerEntity player) {
		return Inventory.canPlayerUse(this, player);
	}

	@Override
	public void clear() {
		items.set(0, ItemStack.EMPTY);
		markDirty();
	}

	/** Al cambiar, se guarda y se le manda al cliente (para que se vea lo que lleva). */
	@Override
	public void markDirty() {
		super.markDirty();
		if (world != null && !world.isClient) {
			world.updateListeners(pos, getCachedState(), getCachedState(), Block.NOTIFY_LISTENERS);
		}
	}

	// ------------------------------------------------------------------ menú

	@Override
	public BlockPos getScreenOpeningData(ServerPlayerEntity player) {
		return pos;
	}

	@Override
	public Text getDisplayName() {
		ItemStack inside = items.get(0);
		return inside.isEmpty() ? getCachedState().getBlock().getName()
				: Text.translatable("item.evolvedwarfare.crop_crate.filled", inside.getItem().getName());
	}

	@Override
	public ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity player) {
		return new CropCrateScreenHandler(syncId, playerInventory, this);
	}

	// ------------------------------------------------------------------ objeto <-> bloque

	@Override
	protected void readComponents(ComponentsAccess components) {
		super.readComponents(components);
		CropCrateItem.Contents contents = components.get(ModComponents.CROP_CRATE);
		items.set(0, contents == null ? ItemStack.EMPTY : contents.stack());
	}

	@Override
	protected void addComponents(ComponentMap.Builder builder) {
		super.addComponents(builder);
		ItemStack inside = items.get(0);
		if (!inside.isEmpty()) {
			builder.add(ModComponents.CROP_CRATE, new CropCrateItem.Contents(inside.getItem(), inside.getCount()));
		}
	}

	@Override
	public void removeFromCopiedStackNbt(NbtCompound nbt) {
		nbt.remove("Items");
	}

	// ------------------------------------------------------------------ guardado y red

	@Override
	protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		super.writeNbt(nbt, registries);
		Inventories.writeNbt(nbt, items, registries);
	}

	@Override
	protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		super.readNbt(nbt, registries);
		items.set(0, ItemStack.EMPTY);
		Inventories.readNbt(nbt, items, registries);
	}

	@Nullable
	@Override
	public Packet<ClientPlayPacketListener> toUpdatePacket() {
		return BlockEntityUpdateS2CPacket.create(this);
	}

	@Override
	public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup registries) {
		NbtCompound nbt = new NbtCompound();
		// sin «Items» en el paquete no se vaciaría en el cliente al sacarlo todo
		Inventories.writeNbt(nbt, items, true, registries);
		return nbt;
	}
}
