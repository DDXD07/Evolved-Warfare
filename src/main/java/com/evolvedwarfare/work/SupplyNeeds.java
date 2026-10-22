package com.evolvedwarfare.work;

import java.util.List;
import java.util.function.Predicate;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.ItemTags;

/**
 * Lo que gasta cada oficio de sus suministros. El mensajero lo usa para saber qué llevar a cada puesto desde el
 * almacén (hasta {@link Need#target()}), y para dejar algo en la producción cuando el oficio también lo usa de ahí
 * (semillas, brotes).
 */
public final class SupplyNeeds {
	/** Un tipo de suministro y cuántos conviene tener. */
	public record Need(Predicate<ItemStack> filter, int target) {
	}

	private static final Predicate<ItemStack> BONE_MEAL = s -> s.isOf(Items.BONE_MEAL);
	private static final Predicate<ItemStack> FUEL = s -> s.isOf(Items.COAL) || s.isOf(Items.CHARCOAL) || s.isOf(Items.COAL_BLOCK);
	private static final Predicate<ItemStack> SEEDS = s -> s.isOf(Items.WHEAT_SEEDS) || s.isOf(Items.BEETROOT_SEEDS)
			|| s.isOf(Items.CARROT) || s.isOf(Items.POTATO);
	private static final Predicate<ItemStack> ORES = s -> s.isOf(Items.RAW_IRON) || s.isOf(Items.RAW_GOLD) || s.isOf(Items.RAW_COPPER)
			|| s.isIn(ItemTags.IRON_ORES) || s.isIn(ItemTags.GOLD_ORES) || s.isIn(ItemTags.COPPER_ORES);
	private static final Predicate<ItemStack> RAW_FOOD = s -> s.isOf(Items.BEEF) || s.isOf(Items.PORKCHOP) || s.isOf(Items.CHICKEN)
			|| s.isOf(Items.MUTTON) || s.isOf(Items.RABBIT) || s.isOf(Items.COD) || s.isOf(Items.SALMON) || s.isOf(Items.POTATO)
			|| s.isOf(Items.KELP);
	private static final Predicate<ItemStack> STONE = s -> s.isOf(Items.COBBLESTONE) || s.isOf(Items.STONE)
			|| s.isOf(Items.COBBLED_DEEPSLATE) || s.isOf(Items.ANDESITE) || s.isOf(Items.DIORITE) || s.isOf(Items.GRANITE)
			|| s.isOf(Items.BLACKSTONE) || s.isOf(Items.TUFF) || s.isOf(Items.SAND) || s.isOf(Items.RED_SAND);
	private static final Predicate<ItemStack> FLOWERS = s -> s.isIn(ItemTags.SMALL_FLOWERS) && !s.isOf(Items.WITHER_ROSE);

	private SupplyNeeds() {
	}

	public static List<Need> of(Profession profession) {
		return switch (profession) {
			case FARMER -> List.of(new Need(SEEDS, 16), new Need(BONE_MEAL, 16));
			case LUMBERJACK -> List.of(new Need(LumberjackJob.SAPLING, 16), new Need(BONE_MEAL, 16));
			case MINER -> List.of(new Need(s -> s.isOf(Items.TORCH), 32));
			case SHEPHERD -> List.of(new Need(s -> s.isOf(Items.WHEAT), 16), new Need(s -> s.isOf(Items.BUCKET), 2));
			case BREEDER -> List.of(new Need(s -> s.isOf(Items.WHEAT_SEEDS), 16), new Need(s -> s.isOf(Items.CARROT), 16));
			case BEEKEEPER -> List.of(new Need(s -> s.isOf(Items.GLASS_BOTTLE), 16), new Need(FLOWERS, 8));
			case GARDENER -> List.of(new Need(BONE_MEAL, 16));
			case BLACKSMITH -> List.of(new Need(ORES, 32), new Need(FUEL, 16));
			case COOK -> List.of(new Need(RAW_FOOD, 32), new Need(s -> s.isOf(Items.WHEAT), 15), new Need(FUEL, 16));
			case CHARCOAL_BURNER -> List.of(new Need(s -> s.isIn(ItemTags.LOGS_THAT_BURN), 32));
			case STONEMASON -> List.of(new Need(STONE, 32));
			case FLETCHER -> List.of(new Need(s -> s.isOf(Items.FLINT), 16), new Need(s -> s.isOf(Items.STICK), 16),
					new Need(s -> s.isOf(Items.FEATHER), 16), new Need(s -> s.isOf(Items.GRAVEL), 16));
			default -> List.of();
		};
	}

	/** ¿Lo gasta este oficio? */
	public static boolean isInput(Profession profession, ItemStack stack) {
		for (Need need : of(profession)) {
			if (need.filter().test(stack)) {
				return true;
			}
		}
		return false;
	}
}
