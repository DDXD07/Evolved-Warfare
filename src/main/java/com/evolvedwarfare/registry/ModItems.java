package com.evolvedwarfare.registry;

import com.evolvedwarfare.EvolvedWarfare;
import com.evolvedwarfare.item.RecruitmentContractItem;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.text.Text;

public final class ModItems {
	/** Contrato de reclutamiento: úsalo sobre el suelo para contratar a un recluta. */
	public static final Item RECRUITMENT_CONTRACT = register("recruitment_contract",
			new RecruitmentContractItem(new Item.Settings().maxCount(16)));

	/** Pestaña propia en el inventario creativo. */
	public static final ItemGroup GROUP = Registry.register(Registries.ITEM_GROUP, EvolvedWarfare.id("main"),
			FabricItemGroup.builder()
					.icon(() -> new ItemStack(RECRUITMENT_CONTRACT))
					.displayName(Text.translatable("itemGroup.evolvedwarfare"))
					.entries((context, entries) -> entries.add(RECRUITMENT_CONTRACT))
					.build());

	private ModItems() {
	}

	private static Item register(String name, Item item) {
		return Registry.register(Registries.ITEM, EvolvedWarfare.id(name), item);
	}

	public static void init() {
		// Fuerza la carga de la clase (y por tanto el registro).
	}
}
