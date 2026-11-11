package com.evolvedwarfare.train;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;

/** El ténder: el vagón del carbón (27 huecos de combustible), del que tira la locomotora cuando se le acaba el suyo. */
public class TenderEntity extends TrainCarEntity {
	public static final double HALF_LENGTH = 2.1, BOGIE = 1.2;
	private static final double K = 2.5 / 16.0;
	private static final List<Box> BOXES = boxes();

	public final SimpleInventory inventory = new SimpleInventory(27) {
		@Override
		public boolean isValid(int slot, ItemStack stack) {
			return LocomotiveEntity.fuelTime(stack) > 0;
		}

		@Override
		public boolean canPlayerUse(PlayerEntity player) {
			return isAlive() && player.squaredDistanceTo(TenderEntity.this) < 10 * 10;
		}
	};

	public TenderEntity(EntityType<?> type, World world) {
		super(type, world);
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {
	}

	private static List<Box> boxes() {
		List<Box> b = new ArrayList<>();
		b.addAll(split(new Box(-1.25, 0.45, -1.9, 1.25, 16 * K, 1.9)));     // el depósito (se puede subir encima)
		b.addAll(split(new Box(-1.25, 16 * K, -1.9, 1.25, 19 * K, -0.3)));  // el depósito de agua, detrás
		return b;
	}

	@Override
	protected List<Box> localBoxes() {
		return BOXES;
	}

	@Override
	protected int boxesVersion() {
		return 0;
	}

	@Override
	protected double halfWidth() {
		return 1.3;
	}

	@Override
	protected double halfLength() {
		return HALF_LENGTH;
	}

	@Override
	protected double bogie() {
		return BOGIE;
	}

	@Override
	protected double height() {
		return 19 * K;
	}

	@Override
	public ActionResult interact(PlayerEntity player, Hand hand) {
		ActionResult common = Trains.carInteract(this, player, hand);
		if (common != ActionResult.PASS) {
			return common;
		}
		if (hand != Hand.MAIN_HAND) {
			return ActionResult.PASS;
		}
		if (!getWorld().isClient) {
			player.openHandledScreen(new SimpleNamedScreenHandlerFactory((syncId, inv, p) -> GenericContainerScreenHandler.createGeneric9x3(syncId, inv, inventory),
					Text.translatable("entity.evolvedwarfare.coal_tender")));
		}
		return ActionResult.SUCCESS;
	}

	public void dropContents() {
		for (int i = 0; i < inventory.size(); i++) {
			ItemStack s = inventory.removeStack(i);
			if (!s.isEmpty()) {
				dropStack(s);
			}
		}
	}

	@Override
	protected void readCustomDataFromNbt(NbtCompound nbt) {
		inventory.readNbtList(nbt.getList("Coal", NbtElement.COMPOUND_TYPE), getRegistryManager());
		readLink(nbt);
	}

	@Override
	protected void writeCustomDataToNbt(NbtCompound nbt) {
		nbt.put("Coal", inventory.toNbtList(getRegistryManager()));
		writeLink(nbt);
	}
}
