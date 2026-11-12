package com.evolvedwarfare.train;

import java.util.Map;
import java.util.Optional;
import net.minecraft.block.AbstractFurnaceBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CampfireBlock;
import net.minecraft.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.recipe.AbstractCookingRecipe;
import net.minecraft.recipe.RecipeEntry;
import net.minecraft.recipe.RecipeType;
import net.minecraft.recipe.input.SingleStackRecipeInput;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.screen.BlastFurnaceScreenHandler;
import net.minecraft.screen.CraftingScreenHandler;
import net.minecraft.screen.FurnaceScreenHandler;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.screen.SmokerScreenHandler;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * Bloques de verdad en la plataforma del tren (fase 56): cofre y barril (27 huecos), horno, ahumador y alto horno (que
 * funcionan: combustible, recetas, su velocidad), fogatas (cocinan hasta 4 cosas, echan humo y calientan) y la mesa de
 * crafteo. Van en una rejilla de 3 x 6 bloques por planta (en el suelo de la plataforma o sobre un techo) y viajan con el
 * tren; lo de dentro se guarda en la plataforma.
 */
public final class TrainBlocks {
	public static final int COLS = 3, ROWS = 6;
	private static Map<Item, Integer> fuelTimes;

	private TrainBlocks() {
	}

	public enum Kind {
		CHEST(Blocks.CHEST, 27), BARREL(Blocks.BARREL, 27), FURNACE(Blocks.FURNACE, 3), SMOKER(Blocks.SMOKER, 3), BLAST_FURNACE(Blocks.BLAST_FURNACE, 3),
		CAMPFIRE(Blocks.CAMPFIRE, 4), SOUL_CAMPFIRE(Blocks.SOUL_CAMPFIRE, 4), CRAFTING_TABLE(Blocks.CRAFTING_TABLE, 0);

		public final Block block;
		public final int slots;

		Kind(Block block, int slots) {
			this.block = block;
			this.slots = slots;
		}

		public boolean furnace() {
			return this == FURNACE || this == SMOKER || this == BLAST_FURNACE;
		}

		public boolean campfire() {
			return this == CAMPFIRE || this == SOUL_CAMPFIRE;
		}

		public double height() {
			return campfire() ? 0.4375 : 1.0;
		}

		@Nullable
		public static Kind of(Item item) {
			for (Kind k : values()) {
				if (k.block.asItem() == item) {
					return k;
				}
			}
			return null;
		}
	}

	/** Un bloque puesto: qué, dónde (columna, fila, planta), hacia dónde mira y lo que lleva. */
	public static final class Placed {
		public final Kind kind;
		public final int x, z, level;
		public final Direction facing;
		public final SimpleInventory inventory;
		/** Hornos: lo que le queda de fuego, lo que duraba, lo que lleva cocido y lo que tarda. Fogatas: lo cocido de cada hueco. */
		public int burn, burnTotal, cook, cookTotal = 200;
		public final int[] campfireTime = new int[4], campfireTotal = new int[4];
		/** Encendido (hornos con fuego; las fogatas, siempre). */
		public boolean lit;
		@Nullable
		private Item recipeFor;
		@Nullable
		private RecipeEntry<? extends AbstractCookingRecipe> recipe;

		public Placed(Kind kind, int x, int z, int level, Direction facing) {
			this.kind = kind;
			this.x = x;
			this.z = z;
			this.level = level;
			this.facing = facing;
			this.inventory = new SimpleInventory(Math.max(1, kind.slots));
			this.lit = kind.campfire();
		}

		/** La caja (en coordenadas de la plataforma). */
		public Box box() {
			double x0 = -COLS / 2.0 + x, z0 = -ROWS / 2.0 + z, y0 = Building.floor(level);
			return new Box(x0, y0, z0, x0 + 1, y0 + kind.height(), z0 + 1);
		}

		/** Cómo se ve. */
		public BlockState state() {
			BlockState s = kind.block.getDefaultState();
			if (s.contains(Properties.HORIZONTAL_FACING)) {
				s = s.with(Properties.HORIZONTAL_FACING, facing);
			}
			if (s.contains(Properties.FACING)) {
				s = s.with(Properties.FACING, kind == Kind.BARREL ? Direction.UP : facing);
			}
			if (s.contains(Properties.LIT)) {
				s = s.with(Properties.LIT, lit);
			}
			return s;
		}

		NbtCompound write(RegistryWrapper.WrapperLookup registries, boolean full) {
			NbtCompound n = new NbtCompound();
			n.putByte("K", (byte) kind.ordinal());
			n.putByte("X", (byte) x);
			n.putByte("Z", (byte) z);
			n.putByte("L", (byte) level);
			n.putByte("F", (byte) facing.getHorizontal());
			n.putBoolean("Lit", lit);
			if (full) {
				NbtCompound inv = new NbtCompound();
				net.minecraft.inventory.Inventories.writeNbt(inv, inventory.getHeldStacks(), registries);
				n.put("Inv", inv);
				n.putIntArray("Furnace", new int[] {burn, burnTotal, cook, cookTotal});
				n.putIntArray("Camp", new int[] {campfireTime[0], campfireTime[1], campfireTime[2], campfireTime[3], campfireTotal[0], campfireTotal[1],
						campfireTotal[2], campfireTotal[3]});
			}
			if (kind.campfire()) {
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
		static Placed read(NbtCompound n, RegistryWrapper.WrapperLookup registries) {
			int k = n.getByte("K");
			if (k < 0 || k >= Kind.values().length) {
				return null;
			}
			Placed p = new Placed(Kind.values()[k], n.getByte("X"), n.getByte("Z"), n.getByte("L"), Direction.fromHorizontal(n.getByte("F")));
			p.lit = n.getBoolean("Lit");
			if (n.contains("Inv")) {
				// los huecos, cada uno en su sitio (en el horno importa: entrada, combustible, salida)
				net.minecraft.inventory.Inventories.readNbt(n.getCompound("Inv"), p.inventory.getHeldStacks(), registries);
			}
			int[] f = n.getIntArray("Furnace");
			if (f.length == 4) {
				p.burn = f[0];
				p.burnTotal = f[1];
				p.cook = f[2];
				p.cookTotal = Math.max(1, f[3]);
			}
			int[] c = n.getIntArray("Camp");
			if (c.length == 8) {
				System.arraycopy(c, 0, p.campfireTime, 0, 4);
				System.arraycopy(c, 4, p.campfireTotal, 0, 4);
			}
			if (n.contains("Show") && !n.contains("Inv")) {
				NbtList items = n.getList("Show", NbtElement.COMPOUND_TYPE);
				for (int i = 0; i < Math.min(4, items.size()); i++) {
					String id = items.getCompound(i).getString("Id");
					if (!id.isEmpty()) {
						Item it = Registries.ITEM.get(Identifier.of(id));
						p.inventory.setStack(i, new ItemStack(it));
					}
				}
			}
			return p;
		}

		// ------------------------------------------------------------------ hornos

		private RecipeType<? extends AbstractCookingRecipe> recipeType() {
			return switch (kind) {
				case SMOKER -> RecipeType.SMOKING;
				case BLAST_FURNACE -> RecipeType.BLASTING;
				case CAMPFIRE, SOUL_CAMPFIRE -> RecipeType.CAMPFIRE_COOKING;
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

		/** Un tic de horno (como el de vanilla). Devuelve si ha cambiado lo que se ve (encendido o apagado). */
		boolean tickFurnace(World world) {
			boolean wasLit = lit;
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
			boolean can = !result.isEmpty() && (out.isEmpty() || ItemStack.areItemsAndComponentsEqual(out, result) && out.getCount() + result.getCount() <= out.getMaxCount());
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
			lit = burn > 0;
			return lit != wasLit;
		}

		/** Un tic de fogata: cocina lo que tiene encima. Devuelve lo que ha terminado (o vacío), con su hueco. */
		ItemStack tickCampfire(World world, int[] slotOut) {
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
					slotOut[0] = i;
					return r == null ? s : r.value().getResult(world.getRegistryManager()).copy();
				}
			}
			return ItemStack.EMPTY;
		}

		/** Pone algo a cocinar en la fogata (si tiene receta y hueco). */
		boolean addToCampfire(World world, ItemStack stack) {
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

		/** Lo que hay en el horno para la pantalla. */
		PropertyDelegate properties() {
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

	/** Abre la pantalla de un bloque (cofre, barril, horno o mesa). */
	static void open(PlayerEntity player, PlatformEntity car, Placed p) {
		Text title = Text.translatable(p.kind.block.getTranslationKey());
		player.openHandledScreen(new SimpleNamedScreenHandlerFactory((syncId, playerInv, pl) -> menu(syncId, playerInv, car, p), title));
	}

	private static ScreenHandler menu(int syncId, PlayerInventory playerInv, PlatformEntity car, Placed p) {
		Guarded inv = new Guarded(p.inventory, car);
		return switch (p.kind) {
			case CHEST, BARREL -> GenericContainerScreenHandler.createGeneric9x3(syncId, playerInv, inv);
			case FURNACE -> new FurnaceScreenHandler(syncId, playerInv, inv, p.properties());
			case SMOKER -> new SmokerScreenHandler(syncId, playerInv, inv, p.properties());
			case BLAST_FURNACE -> new BlastFurnaceScreenHandler(syncId, playerInv, inv, p.properties());
			default -> new Crafting(syncId, playerInv, car);
		};
	}

	/** El inventario del bloque, que solo se puede usar cerca de la plataforma. */
	static final class Guarded extends SimpleInventory {
		private final SimpleInventory inner;
		private final PlatformEntity car;

		Guarded(SimpleInventory inner, PlatformEntity car) {
			super(0);
			this.inner = inner;
			this.car = car;
		}

		@Override
		public int size() {
			return inner.size();
		}

		@Override
		public boolean isEmpty() {
			return inner.isEmpty();
		}

		@Override
		public ItemStack getStack(int slot) {
			return inner.getStack(slot);
		}

		@Override
		public ItemStack removeStack(int slot, int amount) {
			return inner.removeStack(slot, amount);
		}

		@Override
		public ItemStack removeStack(int slot) {
			return inner.removeStack(slot);
		}

		@Override
		public void setStack(int slot, ItemStack stack) {
			inner.setStack(slot, stack);
		}

		@Override
		public void markDirty() {
			inner.markDirty();
		}

		@Override
		public boolean canPlayerUse(PlayerEntity player) {
			return car.isAlive() && player.squaredDistanceTo(car) < 12 * 12;
		}

		@Override
		public void clear() {
			inner.clear();
		}
	}

	/** La mesa de crafteo del tren: se puede usar cerca de la plataforma y, al cerrar, devuelve lo de la rejilla. */
	static final class Crafting extends CraftingScreenHandler {
		private final PlatformEntity car;

		Crafting(int syncId, PlayerInventory inv, PlatformEntity car) {
			super(syncId, inv);
			this.car = car;
		}

		@Override
		public boolean canUse(PlayerEntity player) {
			return car.isAlive() && player.squaredDistanceTo(car) < 12 * 12;
		}

		@Override
		public void onClosed(PlayerEntity player) {
			super.onClosed(player);
			// sin sitio en el mundo, vanilla no devolvería lo que queda en la rejilla (los huecos 1 a 9)
			for (int i = 1; i <= 9 && i < slots.size(); i++) {
				ItemStack s = slots.get(i).getStack();
				if (!s.isEmpty()) {
					slots.get(i).setStack(ItemStack.EMPTY);
					player.getInventory().offerOrDrop(s);
				}
			}
		}
	}

	/** Lo que suelta al recogerlo (el bloque y lo de dentro). */
	static void drop(PlatformEntity car, Placed p, @Nullable PlayerEntity player) {
		for (int i = 0; i < p.inventory.size(); i++) {
			ItemStack s = p.inventory.removeStack(i);
			if (!s.isEmpty()) {
				if (player != null) {
					player.getInventory().offerOrDrop(s);
				} else {
					car.dropStack(s);
				}
			}
		}
		ItemStack self = new ItemStack(p.kind.block);
		if (player != null) {
			if (!player.getAbilities().creativeMode) {
				player.getInventory().offerOrDrop(self);
			}
		} else {
			car.dropStack(self);
		}
	}

	/** El calor de una fogata del tren (para el clima). */
	public static float warmth(Placed p) {
		return p.kind.campfire() && p.lit ? 9.0F : p.kind.furnace() && p.lit ? 3.0F : 0.0F;
	}

	/** Para los que no son bloques normales: el item del bloque. */
	public static ItemStack icon(Kind k) {
		return k == Kind.CRAFTING_TABLE ? new ItemStack(Items.CRAFTING_TABLE) : new ItemStack(k.block);
	}
}
