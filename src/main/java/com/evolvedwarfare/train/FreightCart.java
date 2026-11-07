package com.evolvedwarfare.train;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.entity.vehicle.AbstractMinecartEntity;
import net.minecraft.entity.vehicle.StorageMinecartEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.world.World;

/** El vagón de mercancías: un furgón cerrado con 54 huecos (lo que un cofre doble). */
public class FreightCart extends StorageMinecartEntity implements TrainCar {
	private final Link link = new Link();
	/** Su propio inventario: el de las vagonetas con cofre es de 36 como mucho. */
	private net.minecraft.util.collection.DefaultedList<ItemStack> items = net.minecraft.util.collection.DefaultedList.ofSize(54, ItemStack.EMPTY);

	public FreightCart(EntityType<? extends FreightCart> type, World world) {
		super(type, world);
	}

	@Override
	public Link link() {
		return link;
	}

	@Override
	public int size() {
		return 54;
	}

	@Override
	public net.minecraft.util.collection.DefaultedList<ItemStack> getInventory() {
		return items;
	}

	@Override
	public void resetInventory() {
		items = net.minecraft.util.collection.DefaultedList.ofSize(54, ItemStack.EMPTY);
	}

	@Override
	protected ScreenHandler getScreenHandler(int syncId, PlayerInventory inv) {
		return GenericContainerScreenHandler.createGeneric9x6(syncId, inv, this);
	}

	@Override
	public AbstractMinecartEntity.Type getMinecartType() {
		return Type.CHEST;
	}

	@Override
	public Item asItem() {
		return Trains.FREIGHT_ITEM;
	}

	@Override
	public ItemStack getPickBlockStack() {
		return new ItemStack(Trains.FREIGHT_ITEM);
	}

	@Override
	public void tick() {
		TrainCar.follow(this);
		super.tick();
		TrainCar.moved(this);
	}

	@Override
	protected double getMaxSpeed() {
		return LocomotiveCart.TOP_SPEED;
	}

	@Override
	public void pushAwayFrom(Entity entity) {
		if (!TrainCar.sameTrain(this, entity)) {
			super.pushAwayFrom(entity);
		}
	}

	@Override
	public ActionResult interact(PlayerEntity player, Hand hand) {
		if (player.isSneaking() && player.getStackInHand(hand).isEmpty() && link.front != null) {
			if (getWorld() instanceof ServerWorld sw) {
				TrainCar.uncouple(sw, this);
				LocomotiveCart.uncoupledMessage(player, this);
			}
			return ActionResult.SUCCESS;
		}
		return super.interact(player, hand);
	}

	@Override
	public void remove(RemovalReason reason) {
		TrainCar.removed(this);
		super.remove(reason);
	}

	@Override
	protected void writeCustomDataToNbt(NbtCompound nbt) {
		super.writeCustomDataToNbt(nbt);
		link.write(nbt);
	}

	@Override
	protected void readCustomDataFromNbt(NbtCompound nbt) {
		super.readCustomDataFromNbt(nbt);
		link.read(nbt);
	}
}
