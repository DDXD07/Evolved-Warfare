package com.evolvedwarfare.registry;

import com.evolvedwarfare.EvolvedWarfare;
import com.evolvedwarfare.armor.ArmorClass;
import com.evolvedwarfare.armor.ArmorTier;
import com.evolvedwarfare.armor.ClassArmorItem;
import com.evolvedwarfare.armor.FactionArmor;
import com.evolvedwarfare.armor.FactionArmorItem;
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
import net.minecraft.item.ArmorItem;
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

	/**
	 * Armaduras de las facciones: 4 por facción (por rango), cada una con casco, peto, grebas y botas
	 * ({@link FactionArmor}).
	 */
	public static final Map<FactionArmor, List<Item>> FACTION_ARMOR = new EnumMap<>(FactionArmor.class);

	static {
		for (FactionArmor armor : FactionArmor.values()) {
			List<Item> pieces = new ArrayList<>();
			for (ArmorItem.Type type : FactionArmor.TYPES) {
				Item.Settings settings = new Item.Settings().maxDamage(type.getMaxDamage(armor.durability()));
				if (armor.tier == 4) {
					settings.rarity(net.minecraft.util.Rarity.UNCOMMON);
				}
				pieces.add(register(armor.id() + "_" + FactionArmor.pieceName(type), new FactionArmorItem(armor, type, settings)));
			}
			FACTION_ARMOR.put(armor, List.copyOf(pieces));
		}
	}

	/**
	 * Armaduras de clase (arquero, guerrero, ladrón y mosquetero), con aspecto de vanilla, en cuero, cobre, hierro,
	 * diamante y netherita ({@link ClassArmorItem}).
	 */
	public static final Map<ArmorClass, Map<ArmorTier, List<Item>>> CLASS_ARMOR = new EnumMap<>(ArmorClass.class);

	static {
		for (ArmorClass armorClass : ArmorClass.values()) {
			Map<ArmorTier, List<Item>> tiers = new EnumMap<>(ArmorTier.class);
			for (ArmorTier tier : ArmorTier.values()) {
				List<Item> pieces = new ArrayList<>();
				for (ArmorItem.Type type : FactionArmor.TYPES) {
					Item.Settings settings = new Item.Settings().maxDamage(type.getMaxDamage(tier.durability));
					if (tier == ArmorTier.NETHERITE) {
						settings.fireproof();
					}
					pieces.add(register(armorClass.id() + "_" + tier.id() + "_" + FactionArmor.pieceName(type),
							new ClassArmorItem(armorClass, tier, type, settings)));
				}
				tiers.put(tier, List.copyOf(pieces));
			}
			CLASS_ARMOR.put(armorClass, tiers);
		}
	}

	// la sed: agua hervida, zumos y la cantimplora (vacía, con agua sin hervir y con agua hervida)
	public static final Item BOILED_WATER = register("boiled_water",
			new com.evolvedwarfare.climate.DrinkItem(7.0F, 0, 0.0F, 1.5F, new Item.Settings().maxCount(16)));
	public static final Item MELON_JUICE = register("melon_juice",
			new com.evolvedwarfare.climate.DrinkItem(8.0F, 2, 0.2F, 1.0F, new Item.Settings().maxCount(16)));
	public static final Item APPLE_JUICE = register("apple_juice",
			new com.evolvedwarfare.climate.DrinkItem(6.0F, 2, 0.3F, 0.5F, new Item.Settings().maxCount(16)));
	public static final Item BERRY_JUICE = register("berry_juice",
			new com.evolvedwarfare.climate.DrinkItem(6.0F, 1, 0.2F, 0.5F, new Item.Settings().maxCount(16)));
	public static final Item CANTEEN = register("canteen", new com.evolvedwarfare.climate.CanteenItem(
			com.evolvedwarfare.climate.CanteenItem.Kind.EMPTY, new Item.Settings().maxCount(1)));
	public static final Item WATER_CANTEEN = register("water_canteen", new com.evolvedwarfare.climate.CanteenItem(
			com.evolvedwarfare.climate.CanteenItem.Kind.RAW, new Item.Settings().maxDamage(com.evolvedwarfare.climate.CanteenItem.SIPS)));
	public static final Item BOILED_CANTEEN = register("boiled_canteen", new com.evolvedwarfare.climate.CanteenItem(
			com.evolvedwarfare.climate.CanteenItem.Kind.BOILED, new Item.Settings().maxDamage(com.evolvedwarfare.climate.CanteenItem.SIPS)));

	// los cultivos nuevos: semillas, lo cosechado (en 3D), la mazorca asada y el guiso de verduras
	public static final Item TOMATO_SEEDS = register("tomato_seeds", new net.minecraft.item.AliasedBlockItem(ModBlocks.TOMATOES, new Item.Settings()));
	public static final Item CORN_SEEDS = register("corn_seeds", new net.minecraft.item.AliasedBlockItem(ModBlocks.CORN, new Item.Settings()));
	public static final Item CABBAGE_SEEDS = register("cabbage_seeds", new net.minecraft.item.AliasedBlockItem(ModBlocks.CABBAGES, new Item.Settings()));
	public static final Item TOMATO = register("tomato", new com.evolvedwarfare.item.DescribedItem(new Item.Settings().food(food(3, 0.4F))));
	public static final Item CORN = register("corn", new com.evolvedwarfare.item.DescribedItem(new Item.Settings().food(food(2, 0.3F))));
	public static final Item ROASTED_CORN = register("roasted_corn", new Item(new Item.Settings().food(food(6, 0.6F))));
	public static final Item CABBAGE = register("cabbage", new com.evolvedwarfare.item.DescribedItem(new Item.Settings().food(food(2, 0.4F))));
	public static final Item ONION = register("onion", new com.evolvedwarfare.item.DescribedAliasedItem(ModBlocks.ONIONS,
			new Item.Settings().food(food(1, 0.2F)), false));
	public static final Item GARLIC = register("garlic", new com.evolvedwarfare.item.DescribedAliasedItem(ModBlocks.GARLIC,
			new Item.Settings().food(food(1, 0.1F)), true));
	public static final Item VEGETABLE_STEW = register("vegetable_stew", new com.evolvedwarfare.item.DescribedItem(new Item.Settings().maxCount(1)
			.food(new net.minecraft.component.type.FoodComponent.Builder().nutrition(9).saturationModifier(0.8F)
					.usingConvertsTo(net.minecraft.item.Items.BOWL).build())));

	// la herrería: piezas forjadas, lingotes al rojo y el martillo
	public static final Item FORGED_PART = register("forged_part", new com.evolvedwarfare.smithing.ForgedPartItem(new Item.Settings().maxCount(1)));
	public static final Item HOT_IRON_INGOT = register("hot_iron_ingot", new com.evolvedwarfare.smithing.HotIngotItem(
			com.evolvedwarfare.smithing.ForgeMaterial.IRON, () -> net.minecraft.item.Items.IRON_INGOT, new Item.Settings()));
	public static final Item HOT_GOLD_INGOT = register("hot_gold_ingot", new com.evolvedwarfare.smithing.HotIngotItem(
			com.evolvedwarfare.smithing.ForgeMaterial.GOLD, () -> net.minecraft.item.Items.GOLD_INGOT, new Item.Settings()));
	public static final Item SMITHING_HAMMER = register("smithing_hammer", new com.evolvedwarfare.item.DescribedItem(new Item.Settings().maxDamage(250)));
	/** El Manual del Herrero: cómo se forja, con ilustraciones (lo venden los armeros por 20 Glory Coins). */
	public static final Item SMITHING_MANUAL = register("smithing_manual", new com.evolvedwarfare.smithing.SmithingManualItem(new Item.Settings().maxCount(1)));

	// la pesca: peces nuevos y cebos
	public static final Item PERCH = register("perch", new com.evolvedwarfare.item.DescribedItem(new Item.Settings().food(food(2, 0.1F))));
	public static final Item CARP = register("carp", new com.evolvedwarfare.item.DescribedItem(new Item.Settings().food(food(2, 0.1F))));
	public static final Item TROUT = register("trout", new com.evolvedwarfare.item.DescribedItem(new Item.Settings().food(food(2, 0.2F))));
	public static final Item PIKE = register("pike", new com.evolvedwarfare.item.DescribedItem(new Item.Settings().food(food(3, 0.2F))));
	public static final Item EEL = register("eel", new com.evolvedwarfare.item.DescribedItem(new Item.Settings().food(food(2, 0.2F))));
	public static final Item TUNA = register("tuna", new com.evolvedwarfare.item.DescribedItem(new Item.Settings().food(food(4, 0.3F))));
	public static final Item SWORDFISH = register("swordfish", new com.evolvedwarfare.item.DescribedItem(new Item.Settings().food(food(4, 0.3F))));
	public static final Item STURGEON = register("sturgeon", new com.evolvedwarfare.item.DescribedItem(new Item.Settings().food(food(4, 0.3F))));
	public static final Item BAIT = register("bait", new com.evolvedwarfare.item.DescribedItem(new Item.Settings()));
	public static final Item MAGIC_BAIT = register("magic_bait", new com.evolvedwarfare.item.DescribedItem(new Item.Settings()));

	// el Nether bíblico: el azufre y los huevos de sus criaturas
	public static final Item BRIMSTONE = register("brimstone", new com.evolvedwarfare.item.DescribedItem(new Item.Settings()));
	public static final Item DEMON_SPAWN_EGG = register("demon_spawn_egg",
			new net.minecraft.item.SpawnEggItem(ModEntities.DEMON, 0x5A0C08, 0x1E1A18, new Item.Settings()));
	public static final Item IMP_SPAWN_EGG = register("imp_spawn_egg",
			new net.minecraft.item.SpawnEggItem(ModEntities.IMP, 0xC4461E, 0x3C2A1E, new Item.Settings()));
	public static final Item LOST_SOUL_SPAWN_EGG = register("lost_soul_spawn_egg",
			new net.minecraft.item.SpawnEggItem(ModEntities.LOST_SOUL, 0xC4E4EC, 0x4A8CA0, new Item.Settings()));

	public static final Item LEGION_SPAWN_EGG = register("legion_spawn_egg",
			new net.minecraft.item.SpawnEggItem(ModEntities.LEGION, 0x965C56, 0xCEB0A0, new Item.Settings()));
	public static final Item BEELZEBUB_SPAWN_EGG = register("beelzebub_spawn_egg",
			new net.minecraft.item.SpawnEggItem(ModEntities.BEELZEBUB, 0x2A3620, 0x961010, new Item.Settings()));
	public static final Item MOLOCH_SPAWN_EGG = register("moloch_spawn_egg",
			new net.minecraft.item.SpawnEggItem(ModEntities.MOLOCH, 0x683A28, 0xFF8C1E, new Item.Settings()));
	public static final Item ABADDON_SPAWN_EGG = register("abaddon_spawn_egg",
			new net.minecraft.item.SpawnEggItem(ModEntities.ABADDON, 0xDED4B6, 0xD2AA32, new Item.Settings()));

	// la fruta de los frutales, sus zumos y el hacha de sílex
	public static final Item PEAR = register("pear", new com.evolvedwarfare.item.DescribedItem(new Item.Settings().food(food(4, 0.3F))));
	public static final Item PLUM = register("plum", new com.evolvedwarfare.item.DescribedItem(new Item.Settings().food(food(3, 0.3F))));
	public static final Item PEACH = register("peach", new com.evolvedwarfare.item.DescribedItem(new Item.Settings().food(food(4, 0.4F))));
	public static final Item LEMON = register("lemon", new com.evolvedwarfare.item.DescribedItem(new Item.Settings().food(food(1, 0.1F))));
	public static final Item ORANGE = register("orange", new com.evolvedwarfare.item.DescribedItem(new Item.Settings().food(food(4, 0.3F))));
	public static final Item LEMONADE = register("lemonade",
			new com.evolvedwarfare.climate.DrinkItem(9.0F, 1, 0.2F, 1.5F, new Item.Settings().maxCount(16)));
	public static final Item ORANGE_JUICE = register("orange_juice",
			new com.evolvedwarfare.climate.DrinkItem(7.0F, 3, 0.4F, 0.5F, new Item.Settings().maxCount(16)));
	public static final Item FLINT_HATCHET = register("flint_hatchet", new net.minecraft.item.AxeItem(com.evolvedwarfare.wood.Woodcutting.FLINT,
			new Item.Settings().attributeModifiers(net.minecraft.item.AxeItem.createAttributeModifiers(com.evolvedwarfare.wood.Woodcutting.FLINT, 5.0F, -3.2F))));

	private static net.minecraft.component.type.FoodComponent food(int nutrition, float saturation) {
		return new net.minecraft.component.type.FoodComponent.Builder().nutrition(nutrition).saturationModifier(saturation).build();
	}

	// los animales salvajes: carnes, pieles y trofeos (y sus huevos de aparición, abajo)
	public static final Item VENISON = register("venison", new Item(new Item.Settings().food(food(3, 0.3F))));
	public static final Item COOKED_VENISON = register("cooked_venison", new Item(new Item.Settings().food(food(8, 0.8F))));
	public static final Item GAME_MEAT = register("game_meat", new Item(new Item.Settings().food(food(3, 0.3F))));
	public static final Item COOKED_GAME_MEAT = register("cooked_game_meat", new Item(new Item.Settings().food(food(8, 0.8F))));
	public static final Item FOWL = register("fowl", new Item(new Item.Settings().food(new net.minecraft.component.type.FoodComponent.Builder()
			.nutrition(2).saturationModifier(0.3F)
			.statusEffect(new net.minecraft.entity.effect.StatusEffectInstance(net.minecraft.entity.effect.StatusEffects.HUNGER, 600, 0), 0.3F)
			.build())));
	public static final Item COOKED_FOWL = register("cooked_fowl", new Item(new Item.Settings().food(food(6, 0.6F))));
	public static final Item FUR_PELT = register("fur_pelt", new com.evolvedwarfare.item.DescribedItem(new Item.Settings()));
	public static final Item ANTLER = register("antler", new com.evolvedwarfare.item.DescribedItem(new Item.Settings()));
	public static final Item HORN = register("horn", new com.evolvedwarfare.item.DescribedItem(new Item.Settings()));
	public static final Item TUSK = register("tusk", new com.evolvedwarfare.item.DescribedItem(new Item.Settings()));
	public static final Item BEAR_CLAW = register("bear_claw", new com.evolvedwarfare.item.DescribedItem(new Item.Settings()
			.rarity(net.minecraft.util.Rarity.UNCOMMON)));
	public static final Map<com.evolvedwarfare.wildlife.WildSpecies, Item> WILD_EGGS =
			new EnumMap<>(com.evolvedwarfare.wildlife.WildSpecies.class);

	static {
		for (com.evolvedwarfare.wildlife.WildSpecies s : com.evolvedwarfare.wildlife.WildSpecies.values()) {
			WILD_EGGS.put(s, register(s.id() + "_spawn_egg", new SpawnEggItem(com.evolvedwarfare.wildlife.Wildlife.type(s), s.eggPrimary,
					s.eggSecondary, new Item.Settings())));
		}
	}

	/** Ropa para el clima: piel (abriga) y lino (refresca), cuatro piezas cada una ({@link com.evolvedwarfare.climate.ClimateWearItem}). */
	public static final Map<com.evolvedwarfare.climate.ClimateWearItem.Kind, List<Item>> CLIMATE_WEAR =
			new EnumMap<>(com.evolvedwarfare.climate.ClimateWearItem.Kind.class);

	static {
		String[][] names = {{"fur_hood", "fur_coat", "fur_trousers", "fur_boots"}, {"straw_hat", "linen_tunic", "linen_trousers", "sandals"}};
		for (com.evolvedwarfare.climate.ClimateWearItem.Kind kind : com.evolvedwarfare.climate.ClimateWearItem.Kind.values()) {
			List<Item> pieces = new ArrayList<>();
			for (int i = 0; i < FactionArmor.TYPES.length; i++) {
				ArmorItem.Type type = FactionArmor.TYPES[i];
				pieces.add(register(names[kind.ordinal()][i], new com.evolvedwarfare.climate.ClimateWearItem(kind, type,
						new Item.Settings().maxDamage(type.getMaxDamage(kind == com.evolvedwarfare.climate.ClimateWearItem.Kind.FUR ? 8 : 5)))));
			}
			CLIMATE_WEAR.put(kind, List.copyOf(pieces));
		}
	}

	/** Una pieza de armadura de clase. */
	public static Item classArmor(ArmorClass armorClass, ArmorTier tier, ArmorItem.Type type) {
		List<Item> pieces = CLASS_ARMOR.get(armorClass).get(tier);
		for (int i = 0; i < FactionArmor.TYPES.length; i++) {
			if (FactionArmor.TYPES[i] == type) {
				return pieces.get(i);
			}
		}
		throw new IllegalArgumentException(type.toString());
	}

	/** Una pieza de una armadura de facción. */
	public static Item factionArmor(FactionArmor armor, ArmorItem.Type type) {
		List<Item> pieces = FACTION_ARMOR.get(armor);
		for (int i = 0; i < FactionArmor.TYPES.length; i++) {
			if (FactionArmor.TYPES[i] == type) {
				return pieces.get(i);
			}
		}
		throw new IllegalArgumentException(type.toString());
	}

	/** Todas las piezas de las armaduras de esos rangos (de las tres facciones). */
	public static List<Item> factionArmorOfTiers(int... tiers) {
		List<Item> out = new ArrayList<>();
		for (FactionArmor armor : FactionArmor.values()) {
			for (int tier : tiers) {
				if (armor.tier == tier) {
					out.addAll(FACTION_ARMOR.get(armor));
				}
			}
		}
		return out;
	}

	public static final Item LONGBOW = register("longbow", new LongbowItem(new Item.Settings().maxDamage(500)));
	public static final Item JAVELIN = register("javelin", new JavelinItem(new Item.Settings().maxCount(16)));

	/** Mosquete (fase 10): no se fabrica, lo vende el armero. Y su munición: pólvora + lingote de hierro + papel = 9. */
	public static final Item MUSKET = register("musket", new com.evolvedwarfare.weapon.MusketItem(
			new Item.Settings().maxDamage(300).rarity(net.minecraft.util.Rarity.RARE)));
	public static final Item MUSKET_BALL = register("musket_ball", new Item(new Item.Settings()));
	/** Bolsa de munición: hasta 1000 balas de un tipo; las armas de fuego recargan de ella. */
	/** La carreta y sus piezas: dos ruedas y la plancha. */
	public static final Item CART_WHEEL = register("cart_wheel", new Item(new Item.Settings().maxCount(16)));
	public static final Item CART_BED = register("cart_bed", new Item(new Item.Settings().maxCount(16)));
	public static final Item CART = register("cart", new com.evolvedwarfare.cart.CartItem(new Item.Settings().maxCount(1)));
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
						entries.add(ModBlocks.SALES_MAILBOX);
						entries.add(ModBlocks.TRADER_CATALOG);
						entries.add(SMITHING_HAMMER);
						entries.add(SMITHING_MANUAL);
						entries.add(HOT_IRON_INGOT);
						entries.add(HOT_GOLD_INGOT);
						for (Item fish : new Item[] {PERCH, CARP, TROUT, PIKE, EEL, TUNA, SWORDFISH, STURGEON, BAIT, MAGIC_BAIT}) {
							entries.add(fish);
						}
						entries.add(ModBlocks.ASH_BLOCK);
						entries.add(ModBlocks.BRIMSTONE_BLOCK);
						entries.add(BRIMSTONE);
						entries.add(DEMON_SPAWN_EGG);
						entries.add(IMP_SPAWN_EGG);
						entries.add(LOST_SOUL_SPAWN_EGG);
						entries.add(LEGION_SPAWN_EGG);
						entries.add(BEELZEBUB_SPAWN_EGG);
						entries.add(MOLOCH_SPAWN_EGG);
						entries.add(ABADDON_SPAWN_EGG);
						entries.add(FLINT_HATCHET);
						for (com.evolvedwarfare.orchard.FruitKind kind : com.evolvedwarfare.orchard.FruitKind.values()) {
							entries.add(ModBlocks.FRUIT_SAPLINGS.get(kind));
							entries.add(ModBlocks.FRUIT_LEAVES.get(kind));
						}
						entries.add(PEAR);
						entries.add(PLUM);
						entries.add(PEACH);
						entries.add(LEMON);
						entries.add(ORANGE);
						entries.add(LEMONADE);
						entries.add(ORANGE_JUICE);
						entries.add(ModBlocks.PIPE);
						entries.add(ModBlocks.FAUCET);
						entries.add(ModBlocks.HAND_PUMP);
						entries.add(CART_WHEEL);
						entries.add(CART_BED);
						entries.add(CART);
						for (List<Item> items : WEAPONS.values()) {
							items.forEach(entries::add);
						}
						entries.add(LONGBOW);
						entries.add(JAVELIN);
						for (List<Item> pieces : FACTION_ARMOR.values()) {
							pieces.forEach(entries::add);
						}
						for (Map<ArmorTier, List<Item>> tiers : CLASS_ARMOR.values()) {
							tiers.values().forEach(pieces -> pieces.forEach(entries::add));
						}
						CLIMATE_WEAR.values().forEach(pieces -> pieces.forEach(entries::add));
						entries.add(VENISON);
						entries.add(COOKED_VENISON);
						entries.add(GAME_MEAT);
						entries.add(COOKED_GAME_MEAT);
						entries.add(FOWL);
						entries.add(COOKED_FOWL);
						entries.add(FUR_PELT);
						entries.add(ANTLER);
						entries.add(HORN);
						entries.add(TUSK);
						entries.add(BEAR_CLAW);
						WILD_EGGS.values().forEach(entries::add);
						entries.add(TOMATO_SEEDS);
						entries.add(TOMATO);
						entries.add(CORN_SEEDS);
						entries.add(CORN);
						entries.add(ROASTED_CORN);
						entries.add(CABBAGE_SEEDS);
						entries.add(CABBAGE);
						entries.add(ONION);
						entries.add(GARLIC);
						entries.add(VEGETABLE_STEW);
						entries.add(BOILED_WATER);
						entries.add(MELON_JUICE);
						entries.add(APPLE_JUICE);
						entries.add(BERRY_JUICE);
						entries.add(CANTEEN);
						entries.add(WATER_CANTEEN);
						entries.add(BOILED_CANTEEN);
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
