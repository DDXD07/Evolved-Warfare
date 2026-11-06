package com.evolvedwarfare.train;

import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import net.minecraft.block.AbstractFurnaceBlock;
import net.minecraft.block.AnvilBlock;
import net.minecraft.block.BarrelBlock;
import net.minecraft.block.BlastFurnaceBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.CartographyTableBlock;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.CraftingTableBlock;
import net.minecraft.block.DispenserBlock;
import net.minecraft.block.EnchantingTableBlock;
import net.minecraft.block.EnderChestBlock;
import net.minecraft.block.GrindstoneBlock;
import net.minecraft.block.HopperBlock;
import net.minecraft.block.LoomBlock;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.block.SmithingTableBlock;
import net.minecraft.block.SmokerBlock;
import net.minecraft.block.StonecutterBlock;
import net.minecraft.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventories;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.recipe.AbstractCookingRecipe;
import net.minecraft.recipe.RecipeType;
import net.minecraft.recipe.input.SingleStackRecipeInput;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.screen.AnvilScreenHandler;
import net.minecraft.screen.BlastFurnaceScreenHandler;
import net.minecraft.screen.CartographyTableScreenHandler;
import net.minecraft.screen.CraftingScreenHandler;
import net.minecraft.screen.EnchantmentScreenHandler;
import net.minecraft.screen.FurnaceScreenHandler;
import net.minecraft.screen.Generic3x3ContainerScreenHandler;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.GrindstoneScreenHandler;
import net.minecraft.screen.HopperScreenHandler;
import net.minecraft.screen.LoomScreenHandler;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerContext;
import net.minecraft.screen.ShulkerBoxScreenHandler;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.screen.SmithingScreenHandler;
import net.minecraft.screen.SmokerScreenHandler;
import net.minecraft.screen.StonecutterScreenHandler;
import net.minecraft.state.property.Properties;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * Los bloques que funcionan en un tren (fase 42): cofres, barriles, cajas de shulker, dispensadores, soltadores y tolvas
 * (con su inventario guardado en el vagón), hornos, ahumadores y altos hornos (que cuecen de verdad mientras el tren va),
 * el cofre de ender, y las mesas (de crafteo, de herrería, de encantar, yunque, piedra de afilar, cortapiedras, telar y mesa
 * de cartografía).
 */
public final class TrainBlocks {
	public enum Kind {
		NONE, STORAGE27, SHULKER, STORAGE9, HOPPER, FURNACE, SMOKER, BLAST, ENDER, CRAFTING, ANVIL, ENCHANTING, GRINDSTONE, SMITHING, LOOM, CARTOGRAPHY,
		STONECUTTER
	}

	private static Map<Item, Integer> fuel;

	private TrainBlocks() {
	}

	public static Kind kind(Block b) {
		if (b instanceof ShulkerBoxBlock) {
			return Kind.SHULKER;
		}
		if (b instanceof ChestBlock || b instanceof BarrelBlock) {
			return Kind.STORAGE27;
		}
		if (b instanceof DispenserBlock) {
			return Kind.STORAGE9;
		}
		if (b instanceof HopperBlock) {
			return Kind.HOPPER;
		}
		if (b instanceof SmokerBlock) {
			return Kind.SMOKER;
		}
		if (b instanceof BlastFurnaceBlock) {
			return Kind.BLAST;
		}
		if (b instanceof AbstractFurnaceBlock) {
			return Kind.FURNACE;
		}
		if (b instanceof EnderChestBlock) {
			return Kind.ENDER;
		}
		if (b instanceof CraftingTableBlock && !(b instanceof SmithingTableBlock) && !(b instanceof CartographyTableBlock)) {
			return Kind.CRAFTING;
		}
		if (b instanceof AnvilBlock) {
			return Kind.ANVIL;
		}
		if (b instanceof EnchantingTableBlock) {
			return Kind.ENCHANTING;
		}
		if (b instanceof GrindstoneBlock) {
			return Kind.GRINDSTONE;
		}
		if (b instanceof SmithingTableBlock) {
			return Kind.SMITHING;
		}
		if (b instanceof LoomBlock) {
			return Kind.LOOM;
		}
		if (b instanceof CartographyTableBlock) {
			return Kind.CARTOGRAPHY;
		}
		if (b instanceof StonecutterBlock) {
			return Kind.STONECUTTER;
		}
		return Kind.NONE;
	}

	/** ¿Necesita guardar algo (inventario u horno)? */
	public static boolean hasData(Kind k) {
		return switch (k) {
			case STORAGE27, SHULKER, STORAGE9, HOPPER, FURNACE, SMOKER, BLAST -> true;
			default -> false;
		};
	}

	public static int size(Kind k) {
		return switch (k) {
			case STORAGE27, SHULKER -> 27;
			case STORAGE9 -> 9;
			case HOPPER -> 5;
			case FURNACE, SMOKER, BLAST -> 3;
			default -> 0;
		};
	}

	/** Lo guardado de una casilla: su inventario y, si es un horno, cómo va. */
	public static final class Cell extends SimpleInventory {
		public final Kind kind;
		private final FlatcarEntity car;
		public int burn, fuelTime, cook, cookTotal = 200;
		public final PropertyDelegate properties = new PropertyDelegate() {
			@Override
			public int get(int i) {
				return switch (i) {
					case 0 -> burn;
					case 1 -> fuelTime;
					case 2 -> cook;
					default -> cookTotal;
				};
			}

			@Override
			public void set(int i, int v) {
				switch (i) {
					case 0 -> burn = v;
					case 1 -> fuelTime = v;
					case 2 -> cook = v;
					default -> cookTotal = v;
				}
			}

			@Override
			public int size() {
				return 4;
			}
		};

		public Cell(Kind kind, FlatcarEntity car) {
			super(size(kind));
			this.kind = kind;
			this.car = car;
		}

		@Override
		public boolean canPlayerUse(PlayerEntity player) {
			return car.isAlive() && player.squaredDistanceTo(car) < 12 * 12;
		}

		public NbtCompound write(RegistryWrapper.WrapperLookup registries) {
			NbtCompound n = new NbtCompound();
			DefaultedList<ItemStack> list = DefaultedList.ofSize(size(), ItemStack.EMPTY);
			for (int i = 0; i < size(); i++) {
				list.set(i, getStack(i));
			}
			Inventories.writeNbt(n, list, registries);
			n.putInt("Burn", burn);
			n.putInt("FuelTime", fuelTime);
			n.putInt("Cook", cook);
			n.putInt("CookTotal", cookTotal);
			return n;
		}

		public void read(NbtCompound n, RegistryWrapper.WrapperLookup registries) {
			DefaultedList<ItemStack> list = DefaultedList.ofSize(size(), ItemStack.EMPTY);
			Inventories.readNbt(n, list, registries);
			for (int i = 0; i < size(); i++) {
				setStack(i, list.get(i));
			}
			burn = n.getInt("Burn");
			fuelTime = n.getInt("FuelTime");
			cook = n.getInt("Cook");
			cookTotal = n.contains("CookTotal") ? n.getInt("CookTotal") : 200;
		}

		private RecipeType<? extends AbstractCookingRecipe> type() {
			return kind == Kind.SMOKER ? RecipeType.SMOKING : kind == Kind.BLAST ? RecipeType.BLASTING : RecipeType.SMELTING;
		}

		/** Un tic de horno (como el de vanilla): devuelve si está encendido. */
		@SuppressWarnings("unchecked")
		public boolean tickFurnace(World world) {
			if (kind != Kind.FURNACE && kind != Kind.SMOKER && kind != Kind.BLAST) {
				return false;
			}
			if (burn > 0) {
				burn--;
			}
			ItemStack input = getStack(0), fuelStack = getStack(1), output = getStack(2);
			var recipe = input.isEmpty() ? Optional.<net.minecraft.recipe.RecipeEntry<AbstractCookingRecipe>>empty()
					: world.getRecipeManager().getFirstMatch((RecipeType<AbstractCookingRecipe>) type(), new SingleStackRecipeInput(input), world);
			ItemStack result = recipe.map(r -> r.value().getResult(world.getRegistryManager())).orElse(ItemStack.EMPTY);
			boolean fits = !result.isEmpty() && (output.isEmpty() || ItemStack.areItemsAndComponentsEqual(output, result)
					&& output.getCount() + result.getCount() <= output.getMaxCount());
			if (burn == 0 && fits) {
				int t = fuelTime(fuelStack);
				if (t > 0) {
					burn = fuelTime = t;
					Item remainder = fuelStack.getItem().getRecipeRemainder();
					fuelStack.decrement(1);
					if (fuelStack.isEmpty() && remainder != null) {
						setStack(1, new ItemStack(remainder));
					}
				}
			}
			if (burn > 0 && fits) {
				cookTotal = recipe.get().value().getCookingTime();
				if (++cook >= cookTotal) {
					cook = 0;
					if (output.isEmpty()) {
						setStack(2, result.copy());
					} else {
						output.increment(result.getCount());
					}
					input.decrement(1);
				}
			} else if (!fits) {
				cook = 0;
			} else if (cook > 0) {
				cook = Math.max(0, cook - 2);
			}
			markDirty();
			return burn > 0;
		}
	}

	public static int fuelTime(ItemStack stack) {
		if (fuel == null) {
			fuel = AbstractFurnaceBlockEntity.createFuelTimeMap();
		}
		return stack.isEmpty() ? 0 : fuel.getOrDefault(stack.getItem(), 0);
	}

	/**
	 * Para las mesas de un tren: un "sitio" en lo alto del cielo (allí no hay nada que romper ni que contar) donde las mesas
	 * creen que están, y que siempre deja usarlas (si el jugador sigue cerca del tren).
	 */
	record TrainContext(World world, BlockPos pos, FlatcarEntity car, PlayerEntity player) implements ScreenHandlerContext {
		@Override
		@SuppressWarnings("unchecked")
		public <T> Optional<T> get(BiFunction<World, BlockPos, T> getter) {
			T v = getter.apply(world, pos);
			if (v instanceof Boolean) {
				return Optional.of((T) Boolean.valueOf(car.isAlive() && player.squaredDistanceTo(car) < 12 * 12));
			}
			return Optional.ofNullable(v);
		}
	}

	/** Abre la pantalla de esa casilla (devuelve si había algo que abrir). */
	public static boolean open(FlatcarEntity car, PlayerEntity player, BlockState state, @Nullable Cell cell) {
		Kind k = kind(state.getBlock());
		if (k == Kind.NONE) {
			return false;
		}
		World world = car.getWorld();
		BlockPos sky = new BlockPos(car.getBlockX(), world.getTopY() - 1, car.getBlockZ());
		ScreenHandlerContext ctx = new TrainContext(world, sky, car, player);
		var title = state.getBlock().getName();
		SimpleNamedScreenHandlerFactory factory = new SimpleNamedScreenHandlerFactory((syncId, inv, p) -> handler(k, syncId, inv, p, cell, ctx), title);
		player.openHandledScreen(factory);
		return true;
	}

	@Nullable
	private static ScreenHandler handler(Kind k, int syncId, PlayerInventory inv, PlayerEntity p, @Nullable Cell cell, ScreenHandlerContext ctx) {
		return switch (k) {
			case STORAGE27 -> cell == null ? null : GenericContainerScreenHandler.createGeneric9x3(syncId, inv, cell);
			case SHULKER -> cell == null ? null : new ShulkerBoxScreenHandler(syncId, inv, cell);
			case STORAGE9 -> cell == null ? null : new Generic3x3ContainerScreenHandler(syncId, inv, cell);
			case HOPPER -> cell == null ? null : new HopperScreenHandler(syncId, inv, cell);
			case FURNACE -> cell == null ? null : new FurnaceScreenHandler(syncId, inv, cell, cell.properties);
			case SMOKER -> cell == null ? null : new SmokerScreenHandler(syncId, inv, cell, cell.properties);
			case BLAST -> cell == null ? null : new BlastFurnaceScreenHandler(syncId, inv, cell, cell.properties);
			case ENDER -> GenericContainerScreenHandler.createGeneric9x3(syncId, inv, p.getEnderChestInventory());
			case CRAFTING -> new CraftingScreenHandler(syncId, inv, ctx);
			case ANVIL -> new AnvilScreenHandler(syncId, inv, ctx);
			case ENCHANTING -> new EnchantmentScreenHandler(syncId, inv, ctx);
			case GRINDSTONE -> new GrindstoneScreenHandler(syncId, inv, ctx);
			case SMITHING -> new SmithingScreenHandler(syncId, inv, ctx);
			case LOOM -> new LoomScreenHandler(syncId, inv, ctx);
			case CARTOGRAPHY -> new CartographyTableScreenHandler(syncId, inv, ctx);
			case STONECUTTER -> new StonecutterScreenHandler(syncId, inv, ctx);
			default -> null;
		};
	}

	/** ¿Se abre/cierra con un clic? (puertas, trampillas, puertas de valla, palancas). */
	public static boolean toggles(BlockState s) {
		return s.contains(Properties.OPEN) || s.contains(Properties.POWERED) && s.getBlock() instanceof net.minecraft.block.LeverBlock;
	}
}
