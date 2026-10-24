package com.evolvedwarfare.work;

import java.util.function.Supplier;
import net.minecraft.item.AxeItem;
import net.minecraft.item.FishingRodItem;
import net.minecraft.item.HoeItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.PickaxeItem;
import net.minecraft.item.ShearsItem;
import net.minecraft.item.ShovelItem;
import net.minecraft.item.ToolItem;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.recipe.RecipeType;
import org.jetbrains.annotations.Nullable;

/**
 * Los oficios de los trabajadores. Cada uno tiene su puesto de trabajo (un bloque), su zona y, si la usa, su
 * herramienta: con ella trabaja más rápido (más cuanto mejor sea) y sin ella va a la mitad de velocidad.
 */
public enum Profession {
	/** Ara la tierra junto al agua, siembra, cosecha y resiembra. */
	FARMER("farmer", HoeItem.class, WorkArea.around(5, 2, 1), FarmerJob::new),
	/** Tala los árboles enteros, recoge brotes y manzanas y replanta en cuadrícula. */
	LUMBERJACK("lumberjack", AxeItem.class, WorkArea.around(7, 3, 3), LumberjackJob::new),
	/** Cava una entrada de 5 escalones detrás del puesto y, bajo tierra, un pozo de 5x5 hasta el fondo del mundo. */
	MINER("miner", PickaxeItem.class, WorkArea.mine(2), () -> new ExcavationJob(false, 0)),
	/** Saca tierra, arena, grava y arcilla de un pozo de 7x7 abierto detrás del puesto (hasta 16 de profundidad). */
	DIGGER("digger", ShovelItem.class, WorkArea.pit(3), () -> new ExcavationJob(true, 16)),
	/** Pesca en el agua cercana. */
	FISHERMAN("fisherman", FishingRodItem.class, WorkArea.around(6, 3, 1), FishermanJob::new),
	/** Ovejas, vacas y cabras: esquila, ordeña, cría y mantiene el rebaño. */
	SHEPHERD("shepherd", ShearsItem.class, WorkArea.around(8, 3, 4), LivestockJob::shepherd),
	/** Gallinas, cerdos y conejos: cría, recoge huevos y plumas y mantiene el corral. */
	BREEDER("breeder", null, WorkArea.around(8, 3, 4), LivestockJob::breeder),
	/** Saca la miel de las colmenas y planta flores para las abejas. */
	BEEKEEPER("beekeeper", ShearsItem.class, WorkArea.around(8, 3, 8), BeekeeperJob::new),
	/** Melones, calabazas, caña, cactus, bambú, bayas, cacao y verrugas del Nether. */
	GARDENER("gardener", AxeItem.class, WorkArea.around(6, 3, 6), GardenerJob::new),
	/** Funde menas y metal en bruto en lingotes (recetas de alto horno). */
	BLACKSMITH("blacksmith", null, WorkArea.NONE, () -> new CookingJob<>(RecipeType.BLASTING, false, ParticleTypes.FLAME)),
	/** Cocina carne, pescado y patatas (recetas de ahumador) y hornea pan con trigo. */
	COOK("cook", null, WorkArea.NONE, () -> new CookingJob<>(RecipeType.SMOKING, true, ParticleTypes.SMOKE)),
	/** Convierte troncos en carbón vegetal sin gastar combustible. */
	CHARCOAL_BURNER("charcoal_burner", null, WorkArea.NONE, ConversionJob::charcoalBurner),
	/** Labra piedra: ladrillos de piedra, piedra pulida, arenisca... */
	STONEMASON("stonemason", PickaxeItem.class, WorkArea.NONE, ConversionJob::stonemason),
	/** Hace flechas (pedernal, palo y pluma) y saca pedernal de la grava. */
	FLETCHER("fletcher", null, WorkArea.NONE, ConversionJob::fletcher),
	/** Lleva la producción al almacén y del almacén lo que falta en cada puesto; o sigue una hoja de ruta. */
	COURIER("courier", null, WorkArea.NONE, CourierJob::new),
	/** Atiende la tienda del puesto y vende en el mercado lo que sobra en el almacén. */
	MERCHANT("merchant", null, WorkArea.NONE, MerchantJob::new),
	/** Construye el plano que le pongas en los suministros, detrás de su puesto, con los materiales exactos. */
	BUILDER("builder", null, WorkArea.NONE, ConstructionJob::new);

	private final String id;
	@Nullable
	private final Class<? extends Item> tool;
	private final WorkArea area;
	private final Supplier<Job> jobFactory;

	Profession(String id, @Nullable Class<? extends Item> tool, WorkArea area, Supplier<Job> jobFactory) {
		this.id = id;
		this.tool = tool;
		this.area = area;
		this.jobFactory = jobFactory;
	}

	public String id() {
		return id;
	}

	public WorkArea area() {
		return area;
	}

	public Job createJob() {
		return jobFactory.get();
	}

	public boolean usesTool() {
		return tool != null;
	}

	public boolean isTool(ItemStack stack) {
		return tool != null && !stack.isEmpty() && tool.isInstance(stack.getItem());
	}

	/**
	 * Rapidez con lo que lleva en la mano: 0,5 sin su herramienta; con ella, de 1 (madera) a 2 (oro), y 1,25
	 * para las que no son de un material (caña, tijeras).
	 */
	public float speed(ItemStack held) {
		if (tool == null) {
			return 1.0F;
		}
		if (!isTool(held)) {
			return 0.5F;
		}
		if (held.getItem() instanceof ToolItem toolItem) {
			return 1.0F + Math.max(0.0F, toolItem.getMaterial().getMiningSpeedMultiplier() - 2.0F) / 10.0F;
		}
		return 1.25F;
	}

	public String translationKey() {
		return "profession.evolvedwarfare." + id;
	}

	public static Profession byOrdinal(int ordinal) {
		Profession[] values = values();
		return ordinal >= 0 && ordinal < values.length ? values[ordinal] : FARMER;
	}

	public static Profession byId(String id, Profession fallback) {
		for (Profession profession : values()) {
			if (profession.id.equals(id)) {
				return profession;
			}
		}
		return fallback;
	}
}
