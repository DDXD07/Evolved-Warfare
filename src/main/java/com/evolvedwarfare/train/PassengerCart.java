package com.evolvedwarfare.train;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.vehicle.AbstractMinecartEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/** El vagón de pasajeros: dos asientos (uno detrás de otro), con techo y ventanas. Lleva también criaturas, como una vagoneta. */
public class PassengerCart extends AbstractMinecartEntity implements TrainCar {
	private final Link link = new Link();

	public PassengerCart(EntityType<? extends PassengerCart> type, World world) {
		super(type, world);
	}

	@Override
	public Link link() {
		return link;
	}

	@Override
	public AbstractMinecartEntity.Type getMinecartType() {
		return Type.RIDEABLE;
	}

	@Override
	public Item asItem() {
		return Trains.PASSENGER_ITEM;
	}

	@Override
	public ItemStack getPickBlockStack() {
		return new ItemStack(Trains.PASSENGER_ITEM);
	}

	@Override
	protected boolean canAddPassenger(Entity passenger) {
		return getPassengerList().size() < 2;
	}

	@Override
	protected void updatePassengerPosition(Entity passenger, PositionUpdater updater) {
		super.updatePassengerPosition(passenger, updater);
		int i = getPassengerList().indexOf(passenger);
		if (getPassengerList().size() > 1 && i >= 0) {
			// uno delante y otro detrás, a lo largo del vagón
			double r = getYaw() * MathHelper.RADIANS_PER_DEGREE;
			double off = i == 0 ? 0.32 : -0.32;
			Vec3d along = new Vec3d(Math.cos(r), 0, Math.sin(r)).multiply(off);
			updater.accept(passenger, passenger.getX() + along.x, passenger.getY(), passenger.getZ() + along.z);
		}
	}

	@Override
	public ActionResult interact(PlayerEntity player, Hand hand) {
		if (player.isSneaking()) {
			if (player.getStackInHand(hand).isEmpty() && link.front != null) {
				if (getWorld() instanceof ServerWorld sw) {
					TrainCar.uncouple(sw, this);
					LocomotiveCart.uncoupledMessage(player, this);
				}
				return ActionResult.SUCCESS;
			}
			return ActionResult.PASS;
		}
		if (hasPassenger(player) || !canAddPassenger(player)) {
			return ActionResult.PASS;
		}
		if (!getWorld().isClient) {
			return player.startRiding(this) ? ActionResult.CONSUME : ActionResult.PASS;
		}
		return ActionResult.SUCCESS;
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
