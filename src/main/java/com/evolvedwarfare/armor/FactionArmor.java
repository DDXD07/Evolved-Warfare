package com.evolvedwarfare.armor;

import com.evolvedwarfare.EvolvedWarfare;
import com.evolvedwarfare.entity.NpcRole;
import com.evolvedwarfare.faction.Faction;
import com.evolvedwarfare.registry.ModItems;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ArmorMaterial;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.recipe.Ingredient;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.random.Random;
import org.jetbrains.annotations.Nullable;

/**
 * Las armaduras de las facciones: 4 por facción, de menos a más rango (1: tropa ligera, 2: guerrero, 3: veterano,
 * 4: capitán). Cada una tiene su modelo 3D ({@code tools/ew_armor.py}). Las de rango 1 y 2 cuentan como armadura ligera
 * para el árbol de habilidades; las de 3 y 4, como pesada.
 *
 * <p>Protección (casco, peto, grebas, botas): rango 1 como el cuero (1/3/2/1), 2 como la malla (2/5/4/1), 3 como el hierro
 * (2/6/5/2) y 4 entre el hierro y el diamante (3/7/6/3, dureza 1,5). Cada facción tiene su toque: los vikingos son más
 * duros (+0,5 de dureza desde el rango 2 y algo de resistencia al empuje el jarl), los piratas se encantan mejor y los
 * bandidos aguantan más (más durabilidad).</p>
 */
public enum FactionArmor {
	BANDIT_OUTLAW(Faction.BANDITS, 1),
	BANDIT_HIGHWAYMAN(Faction.BANDITS, 2),
	BANDIT_BRIGAND(Faction.BANDITS, 3),
	BANDIT_WARLORD(Faction.BANDITS, 4),
	VIKING_HUNTER(Faction.VIKINGS, 1),
	VIKING_WARRIOR(Faction.VIKINGS, 2),
	VIKING_HUSCARL(Faction.VIKINGS, 3),
	VIKING_JARL(Faction.VIKINGS, 4),
	PIRATE_DECKHAND(Faction.PIRATES, 1),
	PIRATE_BUCCANEER(Faction.PIRATES, 2),
	PIRATE_CORSAIR(Faction.PIRATES, 3),
	PIRATE_CAPTAIN(Faction.PIRATES, 4);

	/** Las piezas, en orden: casco, peto, grebas y botas. */
	public static final ArmorItem.Type[] TYPES = {ArmorItem.Type.HELMET, ArmorItem.Type.CHESTPLATE, ArmorItem.Type.LEGGINGS,
			ArmorItem.Type.BOOTS};

	public final Faction faction;
	public final int tier;
	@Nullable
	private RegistryEntry<ArmorMaterial> material;

	FactionArmor(Faction faction, int tier) {
		this.faction = faction;
		this.tier = tier;
	}

	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}

	/** Rangos 1 y 2: ligera (cuero, tela, malla); 3 y 4: pesada (placas, láminas, hierro). */
	public boolean light() {
		return tier <= 2;
	}

	public static FactionArmor of(Faction faction, int tier) {
		int t = Math.max(1, Math.min(4, tier));
		for (FactionArmor armor : values()) {
			if (armor.faction == faction && armor.tier == t) {
				return armor;
			}
		}
		throw new IllegalArgumentException(faction + " " + tier);
	}

	public static String pieceName(ArmorItem.Type type) {
		return switch (type) {
			case HELMET -> "helmet";
			case CHESTPLATE -> "chestplate";
			case LEGGINGS -> "leggings";
			default -> "boots";
		};
	}

	// ------------------------------------------------------------------ números

	/** Protección de cada pieza (casco, peto, grebas, botas) según el rango. */
	public int protection(ArmorItem.Type type) {
		int[] values = switch (tier) {
			case 1 -> new int[] {1, 3, 2, 1};
			case 2 -> new int[] {2, 5, 4, 1};
			case 3 -> new int[] {2, 6, 5, 2};
			default -> new int[] {3, 7, 6, 3};
		};
		return switch (type) {
			case HELMET -> values[0];
			case CHESTPLATE, BODY -> values[1];
			case LEGGINGS -> values[2];
			case BOOTS -> values[3];
		};
	}

	/** Multiplicador de durabilidad (cuero 5, malla y hierro 15, diamante 33). */
	public int durability() {
		int base = switch (tier) {
			case 1 -> 7;
			case 2 -> 15;
			case 3 -> 18;
			default -> 28;
		};
		return faction == Faction.BANDITS ? base + 3 : base;
	}

	public int enchantability() {
		int base = switch (tier) {
			case 1 -> 15;
			case 2 -> 12;
			case 3 -> 9;
			default -> 12;
		};
		return faction == Faction.PIRATES ? base + 4 : base;
	}

	public float toughness() {
		float base = switch (tier) {
			case 3 -> 0.5F;
			case 4 -> 1.5F;
			default -> 0.0F;
		};
		return faction == Faction.VIKINGS && tier >= 2 ? base + 0.5F : base;
	}

	public float knockbackResistance() {
		return faction == Faction.VIKINGS && tier == 4 ? 0.05F : 0.0F;
	}

	private RegistryEntry<SoundEvent> equipSound() {
		return switch (tier) {
			case 1 -> SoundEvents.ITEM_ARMOR_EQUIP_LEATHER;
			case 2 -> faction == Faction.PIRATES ? SoundEvents.ITEM_ARMOR_EQUIP_LEATHER : SoundEvents.ITEM_ARMOR_EQUIP_CHAIN;
			case 3 -> SoundEvents.ITEM_ARMOR_EQUIP_IRON;
			default -> faction == Faction.PIRATES ? SoundEvents.ITEM_ARMOR_EQUIP_GOLD : SoundEvents.ITEM_ARMOR_EQUIP_IRON;
		};
	}

	/** El material (se registra la primera vez que se pide, al registrar sus piezas). */
	public RegistryEntry<ArmorMaterial> material() {
		if (material == null) {
			Map<ArmorItem.Type, Integer> defense = new EnumMap<>(ArmorItem.Type.class);
			for (ArmorItem.Type type : ArmorItem.Type.values()) {
				defense.put(type, protection(type));
			}
			ArmorMaterial value = new ArmorMaterial(defense, enchantability(), equipSound(),
					() -> Ingredient.ofItems(tier == 1 ? Items.LEATHER : Items.IRON_INGOT),
					List.of(new ArmorMaterial.Layer(EvolvedWarfare.id("faction/" + id()))), toughness(), knockbackResistance());
			material = Registry.registerReference(Registries.ARMOR_MATERIAL, EvolvedWarfare.id(id()), value);
		}
		return material;
	}

	// ------------------------------------------------------------------ las llevan sus NPCs

	/**
	 * Viste a un guerrero de una facción con sus armaduras según su clase: el capitán, la de rango 4 entera; los guerreros,
	 * la de rango 2 (3 si es de élite); los tiradores, rango 1-2; los berserkers, sin peto. Alguna pieza puede ser de un
	 * rango menos o faltar, para que no vayan todos iguales.
	 */
	public static void dress(MobEntity mob, Faction faction, NpcRole role, Random r, boolean elite) {
		int tier;
		float[] chance; // casco, peto, grebas, botas
		switch (role) {
			case CAPTAIN -> {
				tier = 4;
				chance = new float[] {1.0F, 1.0F, 1.0F, 1.0F};
			}
			case BARBARIAN -> {
				tier = elite ? 2 : 1;
				chance = new float[] {0.5F, 0.0F, 1.0F, 1.0F};
			}
			case ARCHER -> {
				tier = elite ? 2 : 1;
				chance = new float[] {0.9F, 1.0F, 0.8F, elite ? 1.0F : 0.5F};
			}
			case CROSSBOWMAN -> {
				tier = 2;
				chance = new float[] {0.9F, 1.0F, 0.8F, 0.5F};
			}
			case MEDIC -> {
				tier = 1;
				chance = new float[] {0.5F, 1.0F, 0.8F, 0.5F};
			}
			default -> {
				tier = elite ? 3 : 2;
				chance = new float[] {0.8F, 1.0F, 1.0F, 0.7F};
			}
		}
		for (int i = 0; i < TYPES.length; i++) {
			ArmorItem.Type type = TYPES[i];
			EquipmentSlot slot = type.getEquipmentSlot();
			if (r.nextFloat() >= chance[i]) {
				mob.equipStack(slot, ItemStack.EMPTY);
				continue;
			}
			int pieceTier = tier;
			if (role != NpcRole.CAPTAIN && tier > 1 && r.nextFloat() < 0.25F) {
				pieceTier--;
			} else if (role == NpcRole.CAPTAIN && !elite && r.nextFloat() < 0.15F) {
				pieceTier = 3;
			}
			mob.equipStack(slot, new ItemStack(ModItems.factionArmor(of(faction, pieceTier), type)));
		}
	}
}
