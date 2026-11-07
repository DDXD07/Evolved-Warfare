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

/** El ténder: el vagón del carbón (27 huecos); la locomotora tira de él cuando se le acaba el suyo. Solo admite combustible. */
public class TenderCart extends StorageMinecartEntity implements TrainCar {
	private final Link link = new Link();

	public TenderCart(EntityType<? extends TenderCart> type, World world) {
		super(type, world);
	}

	@Override
	public Link link() {
		return link;
	}

	@Override
	public int size() {
		return 27;
	}

	@Override
	protected ScreenHandler getScreenHandler(int syncId, PlayerInventory inv) {
		return GenericContainerScreenHandler.createGeneric9x3(syncId, inv, this);
	}

	@Override
	public boolean isValid(int slot, ItemStack stack) {
		return LocomotiveCart.fuelTime(stack) > 0;
	}

	@Override
	public AbstractMinecartEntity.Type getMinecartType() {
		return Type.CHEST;
	}

	@Override
	public Item asItem() {
		return Trains.TENDER_ITEM;
	}

	@Override
	public ItemStack getPickBlockStack() {
		return new ItemStack(Trains.TENDER_ITEM);
	}

	@Override
	public void tick() {
		TrainCar.follow(this);
		super.tick();
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
