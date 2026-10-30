package com.evolvedwarfare.registry;

import com.evolvedwarfare.EvolvedWarfare;
import com.evolvedwarfare.item.AmbassadorSealItem;
import com.evolvedwarfare.item.BlueprintItem;
import com.evolvedwarfare.item.StructureScannerItem;
import com.evolvedwarfare.item.CommandStaffItem;
import com.evolvedwarfare.item.MedicKitItem;
import com.evolvedwarfare.item.RecruitmentContractItem;
import com.evolvedwarfare.item.RouteSlipItem;
import com.evolvedwarfare.item.SiegeEngineItem;
import com.evolvedwarfare.siege.SiegeKind;
import com.evolvedwarfare.weapon.BattleAxeItem;
import com.evolvedwarfare.weapon.JavelinItem;
import com.evolvedwarfare.weapon.LongbowItem;
import com.evolvedwarfare.weapon.MedievalWeaponItem;
import com.evolvedwarfare.weapon.WeaponType;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.item.ToolMaterial;
import net.minecraft.item.ToolMaterials;
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
	/** La Glory Coin: la moneda del mod. No se fabrica; se gana vendiendo, en cofres y en el botín de los enemigos. */
	/** La guía del mod (se da al entrar por primera vez). */
	public static final Item GUIDE_BOOK = register("guide_book", new com.evolvedwarfare.item.GuideBookItem(new Item.Settings().maxCount(1)));
	public static final Item GLORY_COIN = register("glory_coin", new Item(new Item.Settings().rarity(net.minecraft.util.Rarity.UNCOMMON)));
	/** Bolsa de Gloria: guarda todas las Glory Coins que quieras en un hueco; las tiendas cobran y pagan en ella. */
	public static final Item GLORY_POUCH = register("glory_pouch", new com.evolvedwarfare.item.GloryPouchItem(
			new Item.Settings().maxCount(1).rarity(net.minecraft.util.Rarity.UNCOMMON)));
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
	public static final Item TRADER_SPAWN_EGG = register("trader_spawn_egg",
			new SpawnEggItem(ModEntities.TRADER, 0x7A5230, 0xE3B53B, new Item.Settings()));
	public static final Item PIRATE_SPAWN_EGG = register("pirate_spawn_egg",
			new SpawnEggItem(ModEntities.PIRATE, 0x1A1A1A, 0xD8D0B8, new Item.Settings()));

	/** Bandera blanca: con ella en la mano, vikingos y piratas no te atacan (si no les atacas tú). */
	public static final Item WHITE_FLAG = register("white_flag", new WhiteFlagItem(new Item.Settings().maxCount(1)));

	/** Sello de embajador: abre la diplomacia con las facciones. */
	public static final Item AMBASSADOR_SEAL = register("ambassador_seal", new AmbassadorSealItem(new Item.Settings().maxCount(1)));

	/** Escáner de planos y plano (fase 6). */
	public static final Item STRUCTURE_SCANNER = register("structure_scanner", new StructureScannerItem(new Item.Settings().maxCount(1)));
	public static final Item BLUEPRINT = register("blueprint", new BlueprintItem(new Item.Settings().maxCount(1)));

	/** Máquinas de asedio y su munición (fase 7). */
	public static final Item CATAPULT = register("catapult",
			new SiegeEngineItem(SiegeKind.CATAPULT, () -> ModEntities.CATAPULT, new Item.Settings().maxCount(1)));
	public static final Item BALLISTA = register("ballista",
			new SiegeEngineItem(SiegeKind.BALLISTA, () -> ModEntities.BALLISTA, new Item.Settings().maxCount(1)));
	public static final Item TREBUCHET = register("trebuchet",
			new SiegeEngineItem(SiegeKind.TREBUCHET, () -> ModEntities.TREBUCHET, new Item.Settings().maxCount(1)));
	public static final Item BATTERING_RAM = register("battering_ram",
			new SiegeEngineItem(SiegeKind.RAM, () -> ModEntities.RAM, new Item.Settings().maxCount(1)));
	public static final Item BALLISTA_BOLT = register("ballista_bolt", new Item(new Item.Settings()));
	public static final Item FIRE_POT = register("fire_pot", new Item(new Item.Settings().maxCount(16)));

	/** Armas medievales (fase 8): cada tipo en hierro, diamante y netherita. */
	public static final String[] WEAPON_MATERIALS = {"iron", "diamond", "netherite"};
	public static final Map<WeaponType, List<Item>> WEAPONS = new EnumMap<>(WeaponType.class);

	static {
		for (WeaponType type : WeaponType.values()) {
			List<Item> items = new ArrayList<>();
			for (String material : WEAPON_MATERIALS) {
				ToolMaterial tool = switch (material) {
					case "diamond" -> ToolMaterials.DIAMOND;
					case "netherite" -> ToolMaterials.NETHERITE;
					default -> ToolMaterials.IRON;
				};
				Item.Settings settings = new Item.Settings();
				if (material.equals("netherite")) {
					settings.fireproof();
				}
				Item item = type == WeaponType.BATTLE_AXE ? new BattleAxeItem(tool, settings) : new MedievalWeaponItem(tool, type, settings);
				items.add(register(material + "_" + type.id(), item));
			}
			WEAPONS.put(type, items);
		}
	}

	public static final Item LONGBOW = register("longbow", new LongbowItem(new Item.Settings().maxDamage(500)));
	public static final Item JAVELIN = register("javelin", new JavelinItem(new Item.Settings().maxCount(16)));

	/** Mosquete (fase 10): no se fabrica, lo vende el armero. Y su munición: pólvora + lingote de hierro + papel = 9. */
	public static final Item MUSKET = register("musket", new com.evolvedwarfare.weapon.MusketItem(
			new Item.Settings().maxDamage(300).rarity(net.minecraft.util.Rarity.RARE)));
	public static final Item MUSKET_BALL = register("musket_ball", new Item(new Item.Settings()));
	/** Bolsa de munición: hasta 1000 balas de un tipo; las armas de fuego recargan de ella. */
	public static final Item AMMO_POUCH = register("ammo_pouch", new com.evolvedwarfare.item.AmmoPouchItem(new Item.Settings().maxCount(1)));

	/** Un arma concreta: tipo y material ("iron", "diamond" o "netherite"). */
	public static Item weapon(WeaponType type, String material) {
		List<Item> items = WEAPONS.get(type);
		for (int i = 0; i < WEAPON_MATERIALS.length; i++) {
			if (WEAPON_MATERIALS[i].equals(material)) {
				return items.get(i);
			}
		}
		return items.get(0);
	}

	public static Item siegeItem(SiegeKind kind) {
		return switch (kind) {
			case CATAPULT -> CATAPULT;
			case BALLISTA -> BALLISTA;
			case TREBUCHET -> TREBUCHET;
			case RAM -> BATTERING_RAM;
		};
	}

	/** Pestaña propia en el inventario creativo. */
	public static final ItemGroup GROUP = Registry.register(Registries.ITEM_GROUP, EvolvedWarfare.id("main"),
			FabricItemGroup.builder()
					.icon(() -> new ItemStack(RECRUITMENT_CONTRACT))
					.displayName(Text.translatable("itemGroup.evolvedwarfare"))
					.entries((context, entries) -> {
						entries.add(GUIDE_BOOK);
						entries.add(GLORY_COIN);
						entries.add(GLORY_POUCH);
						entries.add(RECRUITMENT_CONTRACT);
						entries.add(COMMAND_STAFF);
						entries.add(MEDIC_KIT);
						entries.add(ROUTE_SLIP);
						entries.add(WHITE_FLAG);
						entries.add(AMBASSADOR_SEAL);
						entries.add(STRUCTURE_SCANNER);
						entries.add(ModBlocks.ARCHITECT_TABLE);
						entries.add(CATAPULT);
						entries.add(BALLISTA);
						entries.add(TREBUCHET);
						entries.add(BATTERING_RAM);
						entries.add(BALLISTA_BOLT);
						entries.add(FIRE_POT);
						entries.add(ModBlocks.PORTCULLIS);
						entries.add(ModBlocks.DRAWBRIDGE);
						entries.add(ModBlocks.GATE_WINCH);
						entries.add(ModBlocks.RESEARCH_DESK);
						entries.add(ModBlocks.CROP_CRATE_ITEM);
						for (List<Item> items : WEAPONS.values()) {
							items.forEach(entries::add);
						}
						entries.add(LONGBOW);
						entries.add(JAVELIN);
						entries.add(MUSKET);
						entries.add(MUSKET_BALL);
						entries.add(AMMO_POUCH);
						entries.add(BANDIT_SPAWN_EGG);
						entries.add(VIKING_SPAWN_EGG);
						entries.add(PIRATE_SPAWN_EGG);
						entries.add(TRADER_SPAWN_EGG);
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
