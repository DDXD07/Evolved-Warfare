package com.evolvedwarfare.cart;

import java.util.Map;
import net.minecraft.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventories;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.recipe.AbstractCookingRecipe;
import net.minecraft.recipe.RecipeEntry;
import net.minecraft.recipe.RecipeType;
import net.minecraft.recipe.input.SingleStackRecipeInput;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * Lo que va encima de una carreta, en uno de sus 9 huecos: un cofre o un barril (27 huecos, como en el mundo), un horno,
 * ahumador o alto horno (funde también en marcha), una mesa de crafteo o una cama (que ocupa dos huecos seguidos).
 */
public final class CartModule {
	public enum Kind {
		CHEST(27), BARREL(27), FURNACE(3), SMOKER(3), BLAST_FURNACE(3), CRAFTING(0), BED(0);

		public final int slots;

		Kind(int slots) {
			this.slots = slots;
		}

		public boolean furnace() {
			return this == FURNACE || this == SMOKER || this == BLAST_FURNACE;
		}

		public boolean storage() {
			return this == CHEST || this == BARREL;
		}

		RecipeType<? extends AbstractCookingRecipe> recipes() {
			return switch (this) {
				case SMOKER -> RecipeType.SMOKING;
				case BLAST_FURNACE -> RecipeType.BLASTING;
				default -> RecipeType.SMELTING;
			};
		}
	}

	/** Qué se puede poner en la carreta (o null). */
	@Nullable
	public static Kind kindOf(ItemStack stack) {
		if (stack.isEmpty() || !stack.getComponentChanges().isEmpty() && !stack.isIn(ItemTags.BEDS)) {
			return null;
		}
		Item item = stack.getItem();
		if (item == Items.CHEST) {
			return Kind.CHEST;
		}
		if (item == Items.BARREL) {
			return Kind.BARREL;
		}
		if (item == Items.FURNACE) {
			return Kind.FURNACE;
		}
		if (item == Items.SMOKER) {
			return Kind.SMOKER;
		}
		if (item == Items.BLAST_FURNACE) {
			return Kind.BLAST_FURNACE;
		}
		if (item == Items.CRAFTING_TABLE) {
			return Kind.CRAFTING;
		}
		return stack.isIn(ItemTags.BEDS) ? Kind.BED : null;
	}

	public final Kind kind;
	/** El objeto que se puso (uno). */
	public final ItemStack item;
	/** Lo de dentro (cofre, barril, horno); vacío para la mesa y la cama. */
	public final SimpleInventory inventory;
	/** Cama: el hueco donde están los pies. */
	public int partner = -1;
	private int burnTime;
	private int fuelTime;
	private int cookTime;
	private int cookTimeTotal;

	/** Lo que enseña la pantalla del horno (las llamas y la flecha). */
	public final PropertyDelegate furnaceProperties = new PropertyDelegate() {
		@Override
		public int get(int index) {
			return switch (index) {
				case 0 -> burnTime;
				case 1 -> fuelTime;
				case 2 -> cookTime;
				case 3 -> cookTimeTotal;
				default -> 0;
			};
		}

		@Override
		public void set(int index, int value) {
			switch (index) {
				case 0 -> burnTime = value;
				case 1 -> fuelTime = value;
				case 2 -> cookTime = value;
				case 3 -> cookTimeTotal = value;
				default -> {
				}
			}
		}

		@Override
		public int size() {
			return 4;
		}
	};

	CartModule(CartEntity cart, Kind kind, ItemStack item) {
		this.kind = kind;
		this.item = item.copyWithCount(1);
		this.inventory = new SimpleInventory(kind.slots) {
			@Override
			public boolean canPlayerUse(PlayerEntity player) {
				return cart.isAlive() && player.squaredDistanceTo(cart) <= 64.0;
			}

			@Override
			public void onOpen(PlayerEntity player) {
				if (kind.storage() && !player.isSpectator()) {
					cart.getWorld().playSound(null, cart.getX(), cart.getY() + 1.0, cart.getZ(),
							kind == Kind.BARREL ? SoundEvents.BLOCK_BARREL_OPEN : SoundEvents.BLOCK_CHEST_OPEN, SoundCategory.BLOCKS, 0.5F, 1.0F);
				}
			}

			@Override
			public void onClose(PlayerEntity player) {
				if (kind.storage() && !player.isSpectator()) {
					cart.getWorld().playSound(null, cart.getX(), cart.getY() + 1.0, cart.getZ(),
							kind == Kind.BARREL ? SoundEvents.BLOCK_BARREL_CLOSE : SoundEvents.BLOCK_CHEST_CLOSE, SoundCategory.BLOCKS, 0.5F, 1.0F);
				}
			}

			@Override
			public boolean isValid(int slot, ItemStack stack) {
				return !kind.furnace() || slot != 2;
			}
		};
	}

	public boolean lit() {
		return burnTime > 0;
	}

	/** Lo de dentro, al suelo (al quitarlo de la carreta o romperla). */
	void dropContents(World world, Vec3d at) {
		for (int i = 0; i < inventory.size(); i++) {
			ItemStack stack = inventory.removeStack(i);
			if (!stack.isEmpty()) {
				ItemScatterer.spawn(world, at.x, at.y, at.z, stack);
			}
		}
	}

	// ------------------------------------------------------------------ horno

	private static Map<Item, Integer> fuels;

	private int fuelTime(ItemStack fuel) {
		if (fuels == null) {
			fuels = AbstractFurnaceBlockEntity.createFuelTimeMap();
		}
		int time = fuels.getOrDefault(fuel.getItem(), 0);
		return kind == Kind.FURNACE ? time : time / 2;
	}

	/** Un tic del horno (como el de vanilla, sin experiencia). Devuelve si cambió de encendido a apagado o al revés. */
	boolean tickFurnace(World world) {
		boolean wasLit = lit();
		if (burnTime > 0) {
			burnTime--;
		}
		ItemStack input = inventory.getStack(0);
		ItemStack fuel = inventory.getStack(1);
		RecipeEntry<? extends AbstractCookingRecipe> recipe = input.isEmpty() ? null
				: world.getRecipeManager().getFirstMatch(kind.recipes(), new SingleStackRecipeInput(input), world).orElse(null);
		boolean can = recipe != null && fits(recipe.value().getResult(world.getRegistryManager()));
		if (burnTime <= 0 && can && !fuel.isEmpty() && fuelTime(fuel) > 0) {
			burnTime = fuelTime(fuel);
			fuelTime = burnTime;
			Item rest = fuel.getItem().getRecipeRemainder();
			fuel.decrement(1);
			if (fuel.isEmpty() && rest != null) {
				inventory.setStack(1, new ItemStack(rest));
			}
		}
		if (burnTime > 0 && can) {
			cookTimeTotal = recipe.value().getCookingTime();
			cookTime++;
			if (cookTime >= cookTimeTotal) {
				cookTime = 0;
				ItemStack out = recipe.value().craft(new SingleStackRecipeInput(input), world.getRegistryManager());
				ItemStack have = inventory.getStack(2);
				if (have.isEmpty()) {
					inventory.setStack(2, out.copy());
				} else {
					have.increment(out.getCount());
				}
				input.decrement(1);
			}
		} else if (!can) {
			cookTime = 0;
		} else {
			cookTime = Math.max(0, cookTime - 2);
		}
		return wasLit != lit();
	}

	private boolean fits(ItemStack result) {
		ItemStack have = inventory.getStack(2);
		return have.isEmpty() || ItemStack.areItemsAndComponentsEqual(have, result) && have.getCount() + result.getCount() <= have.getMaxCount();
	}

	// ------------------------------------------------------------------ guardar

	NbtCompound toNbt(int cell, RegistryWrapper.WrapperLookup registries) {
		NbtCompound nbt = new NbtCompound();
		nbt.putInt("Cell", cell);
		nbt.put("Item", item.encode(registries));
		if (partner >= 0) {
			nbt.putInt("Partner", partner);
		}
		if (inventory.size() > 0) {
			DefaultedList<ItemStack> stacks = DefaultedList.ofSize(inventory.size(), ItemStack.EMPTY);
			for (int i = 0; i < inventory.size(); i++) {
				stacks.set(i, inventory.getStack(i));
			}
			Inventories.writeNbt(nbt, stacks, registries);
		}
		if (kind.furnace()) {
			nbt.putInt("BurnTime", burnTime);
			nbt.putInt("FuelTime", fuelTime);
			nbt.putInt("CookTime", cookTime);
			nbt.putInt("CookTimeTotal", cookTimeTotal);
		}
		return nbt;
	}

	@Nullable
	static CartModule fromNbt(CartEntity cart, NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		ItemStack item = ItemStack.fromNbt(registries, nbt.get("Item")).orElse(ItemStack.EMPTY);
		Kind kind = kindOf(item);
		if (kind == null) {
			return null;
		}
		CartModule module = new CartModule(cart, kind, item);
		module.partner = nbt.contains("Partner") ? nbt.getInt("Partner") : -1;
		if (module.inventory.size() > 0) {
			DefaultedList<ItemStack> stacks = DefaultedList.ofSize(module.inventory.size(), ItemStack.EMPTY);
			Inventories.readNbt(nbt, stacks, registries);
			for (int i = 0; i < stacks.size(); i++) {
				module.inventory.setStack(i, stacks.get(i));
			}
		}
		module.burnTime = nbt.getInt("BurnTime");
		module.fuelTime = nbt.getInt("FuelTime");
		module.cookTime = nbt.getInt("CookTime");
		module.cookTimeTotal = nbt.getInt("CookTimeTotal");
		return module;
	}
}
