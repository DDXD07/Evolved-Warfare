package com.evolvedwarfare.registry;

import com.evolvedwarfare.EvolvedWarfare;
import com.evolvedwarfare.item.AmbassadorSealItem;
import com.evolvedwarfare.item.CommandStaffItem;
import com.evolvedwarfare.item.MedicKitItem;
import com.evolvedwarfare.item.RecruitmentContractItem;
import com.evolvedwarfare.item.RouteSlipItem;
import com.evolvedwarfare.item.WhiteFlagItem;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SpawnEggItem;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.text.Text;

public final class ModItems {
	/** Contrato de reclutamiento: úsalo sobre un recluta libre para contratarlo. */
	public static final Item RECRUITMENT_CONTRACT = register("recruitment_contract",
			new RecruitmentContractItem(new Item.Settings().maxCount(16)));

	/** Bastón de mando: órdenes en grupo. */
	public static final Item COMMAND_STAFF = register("command_staff",
			new CommandStaffItem(new Item.Settings().maxCount(1)));

	/** Botiquín: para entrenar médicos. */
	public static final Item MEDIC_KIT = register("medic_kit", new MedicKitItem(new Item.Settings().maxCount(16)));

	/** Hoja de ruta: paradas para un mensajero. */
	public static final Item ROUTE_SLIP = register("route_slip", new RouteSlipItem(new Item.Settings().maxCount(1)));

	/** Huevo de bandido (creativo). */
	public static final Item BANDIT_SPAWN_EGG = register("bandit_spawn_egg",
			new SpawnEggItem(ModEntities.BANDIT, 0x3B2A20, 0x7A1C1C, new Item.Settings()));

	/** Huevos de vikingo y de pirata (creativo). */
	public static final Item VIKING_SPAWN_EGG = register("viking_spawn_egg",
			new SpawnEggItem(ModEntities.VIKING, 0x6B4A2E, 0xB8342A, new Item.Settings()));
	public static final Item PIRATE_SPAWN_EGG = register("pirate_spawn_egg",
			new SpawnEggItem(ModEntities.PIRATE, 0x1A1A1A, 0xD8D0B8, new Item.Settings()));

	/** Bandera blanca: con ella en la mano, vikingos y piratas no te atacan (si no les atacas tú). */
	public static final Item WHITE_FLAG = register("white_flag", new WhiteFlagItem(new Item.Settings().maxCount(1)));

	/** Sello de embajador: abre la diplomacia con las facciones. */
	public static final Item AMBASSADOR_SEAL = register("ambassador_seal", new AmbassadorSealItem(new Item.Settings().maxCount(1)));

	/** Pestaña propia en el inventario creativo. */
	public static final ItemGroup GROUP = Registry.register(Registries.ITEM_GROUP, EvolvedWarfare.id("main"),
			FabricItemGroup.builder()
					.icon(() -> new ItemStack(RECRUITMENT_CONTRACT))
					.displayName(Text.translatable("itemGroup.evolvedwarfare"))
					.entries((context, entries) -> {
						entries.add(RECRUITMENT_CONTRACT);
						entries.add(COMMAND_STAFF);
						entries.add(MEDIC_KIT);
						entries.add(ROUTE_SLIP);
						entries.add(WHITE_FLAG);
						entries.add(AMBASSADOR_SEAL);
						entries.add(BANDIT_SPAWN_EGG);
						entries.add(VIKING_SPAWN_EGG);
						entries.add(PIRATE_SPAWN_EGG);
						entries.add(ModBlocks.TOWN_HALL);
						entries.add(ModBlocks.WAREHOUSE);
						ModBlocks.WORK_SITES.values().forEach(block -> entries.add(block));
					})
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
