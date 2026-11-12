package com.evolvedwarfare.voxel;

import java.util.Map;
import java.util.Optional;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.inventory.Inventories;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.recipe.AbstractCookingRecipe;
import net.minecraft.recipe.RecipeEntry;
import net.minecraft.recipe.RecipeType;
import net.minecraft.recipe.input.SingleStackRecipeInput;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * Lo que tiene dentro un bloque funcional puesto en una rejilla de vóxeles (la carreta): un cofre o un barril (27
 * huecos), un horno, ahumador o alto horno (que funden como los de vanilla, también en marcha) o una fogata (cocina hasta
 * 4 cosas). La mesa de crafteo y la cama no guardan nada: se usan directamente.
 */
public final class VoxelMachine {
	public enum Kind {
		CHEST(27), BARREL(27), FURNACE(3), SMOKER(3), BLAST_FURNACE(3), CAMPFIRE(4);

		public final int slots;

		Kind(int slots) {
			this.slots = slots;
		}

		public boolean furnace() {
			return this == FURNACE || this == SMOKER || this == BLAST_FURNACE;
		}

		/** Qué máquina lleva ese bloque (o null si no lleva). */
		@Nullable
		public static Kind of(Block block) {
			if (block == Blocks.CHEST) {
				return CHEST;
			}
			if (block == Blocks.BARREL) {
				return BARREL;
			}
			if (block == Blocks.FURNACE) {
				return FURNACE;
			}
			if (block == Blocks.SMOKER) {
				return SMOKER;
			}
			if (block == Blocks.BLAST_FURNACE) {
				return BLAST_FURNACE;
			}
			if (block == Blocks.CAMPFIRE || block == Blocks.SOUL_CAMPFIRE) {
				return CAMPFIRE;
			}
			return null;
		}
	}

	private static Map<Item, Integer> fuelTimes;

	public final Kind kind;
	public final SimpleInventory inventory;
	/** Hornos: lo que le queda de fuego, lo que duraba, lo que lleva cocido y lo que tarda. */
	public int burn, burnTotal, cook, cookTotal = 200;
	/** Fogatas: lo cocido de cada hueco y lo que tarda. */
	public final int[] campfireTime = new int[4], campfireTotal = new int[4];
	@Nullable
	private Item recipeFor;
	@Nullable
	private RecipeEntry<? extends AbstractCookingRecipe> recipe;

	public VoxelMachine(Kind kind) {
		this.kind = kind;
		this.inventory = new SimpleInventory(kind.slots);
	}

	public boolean lit() {
		return burn > 0;
	}

	// ------------------------------------------------------------------ guardar

	public NbtCompound write(RegistryWrapper.WrapperLookup registries, boolean full) {
		NbtCompound n = new NbtCompound();
		n.putByte("K", (byte) kind.ordinal());
		if (full) {
			NbtCompound inv = new NbtCompound();
			Inventories.writeNbt(inv, inventory.getHeldStacks(), registries);
			n.put("Inv", inv);
			n.putIntArray("Furnace", new int[] {burn, burnTotal, cook, cookTotal});
			n.putIntArray("Camp", new int[] {campfireTime[0], campfireTime[1], campfireTime[2], campfireTime[3], campfireTotal[0], campfireTotal[1],
					campfireTotal[2], campfireTotal[3]});
		}
		if (kind == Kind.CAMPFIRE) {
			// lo que se ve encima de la fogata (también para el cliente)
			NbtList items = new NbtList();
			for (int i = 0; i < 4; i++) {
				NbtCompound it = new NbtCompound();
				ItemStack s = inventory.getStack(i);
				it.putString("Id", s.isEmpty() ? "" : Registries.ITEM.getId(s.getItem()).toString());
				items.add(it);
			}
			n.put("Show", items);
		}
		return n;
	}

	@Nullable
	public static VoxelMachine read(NbtCompound n, RegistryWrapper.WrapperLookup registries) {
		int k = n.getByte("K");
		if (k < 0 || k >= Kind.values().length) {
			return null;
		}
		VoxelMachine m = new VoxelMachine(Kind.values()[k]);
		if (n.contains("Inv")) {
			Inventories.readNbt(n.getCompound("Inv"), m.inventory.getHeldStacks(), registries);
		} else if (n.contains("Show")) {
			NbtList items = n.getList("Show", NbtElement.COMPOUND_TYPE);
			for (int i = 0; i < Math.min(4, items.size()); i++) {
				String id = items.getCompound(i).getString("Id");
				if (!id.isEmpty()) {
					m.inventory.setStack(i, new ItemStack(Registries.ITEM.get(Identifier.of(id))));
				}
			}
		}
		int[] f = n.getIntArray("Furnace");
		if (f.length == 4) {
			m.burn = f[0];
			m.burnTotal = f[1];
			m.cook = f[2];
			m.cookTotal = Math.max(1, f[3]);
		}
		int[] c = n.getIntArray("Camp");
		if (c.length == 8) {
			System.arraycopy(c, 0, m.campfireTime, 0, 4);
			System.arraycopy(c, 4, m.campfireTotal, 0, 4);
		}
		return m;
	}

	// ------------------------------------------------------------------ hornos y fogatas

	private RecipeType<? extends AbstractCookingRecipe> recipeType() {
		return switch (kind) {
			case SMOKER -> RecipeType.SMOKING;
			case BLAST_FURNACE -> RecipeType.BLASTING;
			case CAMPFIRE -> RecipeType.CAMPFIRE_COOKING;
			default -> RecipeType.SMELTING;
		};
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	@Nullable
	private RecipeEntry<? extends AbstractCookingRecipe> recipe(World world, ItemStack input) {
		if (input.isEmpty()) {
			return null;
		}
		Optional<? extends RecipeEntry<? extends AbstractCookingRecipe>> r = world.getRecipeManager().getFirstMatch((RecipeType) recipeType(),
				new SingleStackRecipeInput(input), world);
		return r.orElse(null);
	}

	static int fuelTime(ItemStack stack) {
		if (stack.isEmpty()) {
			return 0;
		}
		if (fuelTimes == null) {
			fuelTimes = AbstractFurnaceBlockEntity.createFuelTimeMap();
		}
		return fuelTimes.getOrDefault(stack.getItem(), 0);
	}

	/** Un tic de horno (como el de vanilla, sin experiencia). Devuelve si ha cambiado de encendido a apagado o al revés. */
	public boolean tickFurnace(World world) {
		boolean wasLit = lit();
		if (burn > 0) {
			burn--;
		}
		ItemStack input = inventory.getStack(0), fuel = inventory.getStack(1), out = inventory.getStack(2);
		if (input.getItem() != recipeFor) {
			recipeFor = input.isEmpty() ? null : input.getItem();
			recipe = recipe(world, input);
			cook = 0;
			cookTotal = recipe == null ? 200 : Math.max(1, recipe.value().getCookingTime());
		}
		ItemStack result = recipe == null ? ItemStack.EMPTY : recipe.value().getResult(world.getRegistryManager());
		boolean can = !result.isEmpty()
				&& (out.isEmpty() || ItemStack.areItemsAndComponentsEqual(out, result) && out.getCount() + result.getCount() <= out.getMaxCount());
		if (burn <= 0 && can) {
			int t = fuelTime(fuel);
			if (kind != Kind.FURNACE) {
				t /= 2;
			}
			if (t > 0) {
				burn = burnTotal = t;
				ItemStack rest = fuel.getRecipeRemainder();
				fuel.decrement(1);
				if (fuel.isEmpty() && !rest.isEmpty()) {
					inventory.setStack(1, rest);
				}
			}
		}
		if (burn > 0 && can) {
			if (++cook >= cookTotal) {
				cook = 0;
				if (out.isEmpty()) {
					inventory.setStack(2, result.copy());
				} else {
					out.increment(result.getCount());
				}
				input.decrement(1);
			}
		} else if (burn <= 0 && cook > 0) {
			cook = Math.max(0, cook - 2);
		}
		return lit() != wasLit;
	}

	/** Un tic de fogata: cocina lo que tiene encima. Devuelve lo que ha terminado (o vacío). */
	public ItemStack tickCampfire(World world) {
		for (int i = 0; i < 4; i++) {
			ItemStack s = inventory.getStack(i);
			if (s.isEmpty()) {
				continue;
			}
			if (campfireTotal[i] <= 0) {
				RecipeEntry<? extends AbstractCookingRecipe> r = recipe(world, s);
				campfireTotal[i] = r == null ? -1 : Math.max(1, r.value().getCookingTime());
			}
			if (campfireTotal[i] < 0) {
				continue;
			}
			if (++campfireTime[i] >= campfireTotal[i]) {
				RecipeEntry<? extends AbstractCookingRecipe> r = recipe(world, s);
				inventory.setStack(i, ItemStack.EMPTY);
				campfireTime[i] = 0;
				campfireTotal[i] = 0;
				return r == null ? s : r.value().getResult(world.getRegistryManager()).copy();
			}
		}
		return ItemStack.EMPTY;
	}

	/** Pone algo a cocinar en la fogata (si tiene receta y hueco). */
	public boolean addToCampfire(World world, ItemStack stack) {
		if (recipe(world, stack) == null) {
			return false;
		}
		for (int i = 0; i < 4; i++) {
			if (inventory.getStack(i).isEmpty()) {
				inventory.setStack(i, stack.split(1));
				campfireTime[i] = 0;
				campfireTotal[i] = 0;
				return true;
			}
		}
		return false;
	}

	/** Lo que enseña la pantalla del horno (las llamas y la flecha). */
	public PropertyDelegate properties() {
		return new PropertyDelegate() {
			@Override
			public int get(int index) {
				return switch (index) {
					case 0 -> burn;
					case 1 -> burnTotal;
					case 2 -> cook;
					default -> cookTotal;
				};
			}

			@Override
			public void set(int index, int value) {
			}

			@Override
			public int size() {
				return 4;
			}
		};
	}

	/** El calor que da (para el clima): las fogatas encendidas, mucho; los hornos encendidos, algo. */
	public float warmth(BlockState state) {
		if (kind == Kind.CAMPFIRE) {
			return state.contains(net.minecraft.state.property.Properties.LIT) && state.get(net.minecraft.state.property.Properties.LIT) ? 9.0F : 0.0F;
		}
		return kind.furnace() && lit() ? 3.0F : 0.0F;
	}
}
