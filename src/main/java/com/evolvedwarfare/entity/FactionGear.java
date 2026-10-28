package com.evolvedwarfare.entity;

import com.evolvedwarfare.EvolvedWarfare;
import com.evolvedwarfare.faction.Faction;
import com.evolvedwarfare.registry.ModItems;
import net.minecraft.block.entity.BannerPattern;
import net.minecraft.block.entity.BannerPatterns;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.BannerPatternsComponent;
import net.minecraft.component.type.DyedColorComponent;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;

/** Equipo de las facciones: ropa de cuero de sus colores, armas y escudos pintados con su emblema. */
public final class FactionGear {
	/** Ropa vikinga: pardo, azul pizarra, rojo óxido y piel. */
	private static final int[] VIKING_COLORS = {0x6B4A2E, 0x3E4F5E, 0x7A2E22, 0x8A7A5A};
	/** Ropa pirata: rojo, azul marino, negro y camisa blanca. */
	private static final int[] PIRATE_COLORS = {0x7A1E1E, 0x1E2A4A, 0x2B2B2B, 0x5A3A22};
	private static final int SHIRT = 0xD8D0B8;

	private FactionGear() {
	}

	static ItemStack dyed(Item item, int color) {
		ItemStack stack = new ItemStack(item);
		stack.set(DataComponentTypes.DYED_COLOR, new DyedColorComponent(color, false));
		return stack;
	}

	private static RegistryKey<BannerPattern> emblem(Faction faction) {
		return switch (faction) {
			case BANDITS -> BannerPatterns.STRIPE_DOWNRIGHT;
			case VIKINGS -> BannerPatterns.HALF_VERTICAL;
			case PIRATES -> BannerPatterns.SKULL;
		};
	}

	/** Escudo con el color y el emblema de la facción. */
	public static ItemStack shield(World world, Faction faction) {
		ItemStack shield = new ItemStack(Items.SHIELD);
		shield.set(DataComponentTypes.BASE_COLOR, faction.shieldColor());
		try {
			BannerPatternsComponent patterns = new BannerPatternsComponent.Builder()
					.add(world.getRegistryManager().getWrapperOrThrow(RegistryKeys.BANNER_PATTERN), emblem(faction),
							faction.shieldPatternColor())
					.build();
			shield.set(DataComponentTypes.BANNER_PATTERNS, patterns);
		} catch (RuntimeException e) {
			EvolvedWarfare.LOGGER.debug("Sin emblema para el escudo de {}", faction.id(), e);
		}
		return shield;
	}

	/** Si lleva escudo, se lo pinta con los colores de la facción. */
	public static void paintShield(MobEntity mob, Faction faction) {
		if (mob.getOffHandStack().isOf(Items.SHIELD)) {
			mob.equipStack(EquipmentSlot.OFFHAND, shield(mob.getWorld(), faction));
		}
	}

	/** Equipo de cualquier facción. */
	public static void equip(MobEntity mob, Faction faction, NpcRole role, Random r, boolean elite) {
		switch (faction) {
			case BANDITS -> {
				NpcCombat.equip(mob, role, r, NpcCombat.randomBanditColor(r), elite);
				paintShield(mob, faction);
			}
			case VIKINGS -> equipViking(mob, role, r, elite);
			case PIRATES -> equipPirate(mob, role, r, elite);
		}
	}

	// ------------------------------------------------------------------ vikingos

	public static void equipViking(MobEntity mob, NpcRole role, Random r, boolean elite) {
		int color = VIKING_COLORS[r.nextInt(VIKING_COLORS.length)];
		World world = mob.getWorld();
		switch (role) {
			case BARBARIAN -> {
				// berserker: hacha a dos manos, sin casco ni camisa, con calzas y botas de piel
				mob.equipStack(EquipmentSlot.MAINHAND, new ItemStack(r.nextFloat() < 0.35F ? com.evolvedwarfare.registry.ModItems.weapon(com.evolvedwarfare.weapon.WeaponType.BATTLE_AXE, "iron")
						: elite || r.nextFloat() < 0.6F ? Items.IRON_AXE : Items.STONE_AXE));
				mob.equipStack(EquipmentSlot.LEGS, dyed(Items.LEATHER_LEGGINGS, 0x8A7A5A));
				mob.equipStack(EquipmentSlot.FEET, dyed(Items.LEATHER_BOOTS, 0x5A4632));
			}
			case ARCHER -> {
				mob.equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
				mob.equipStack(EquipmentSlot.HEAD, dyed(Items.LEATHER_HELMET, color));
				mob.equipStack(EquipmentSlot.CHEST, dyed(Items.LEATHER_CHESTPLATE, color));
				mob.equipStack(EquipmentSlot.FEET, dyed(Items.LEATHER_BOOTS, 0x5A4632));
			}
			case CROSSBOWMAN -> {
				mob.equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.CROSSBOW));
				mob.equipStack(EquipmentSlot.HEAD, new ItemStack(Items.CHAINMAIL_HELMET));
				mob.equipStack(EquipmentSlot.CHEST, dyed(Items.LEATHER_CHESTPLATE, color));
			}
			case MEDIC -> {
				// völva: la curandera
				mob.equipStack(EquipmentSlot.MAINHAND, new ItemStack(ModItems.MEDIC_KIT));
				mob.equipStack(EquipmentSlot.HEAD, dyed(Items.LEATHER_HELMET, 0x2E2A3A));
				mob.equipStack(EquipmentSlot.CHEST, dyed(Items.LEATHER_CHESTPLATE, 0x3E4F5E));
			}
			case CAPTAIN -> {
				// jarl
				mob.equipStack(EquipmentSlot.MAINHAND, new ItemStack(r.nextFloat() < 0.5F
						? com.evolvedwarfare.registry.ModItems.weapon(com.evolvedwarfare.weapon.WeaponType.BATTLE_AXE, elite && r.nextFloat() < 0.4F ? "diamond" : "iron")
						: elite && r.nextFloat() < 0.25F ? Items.DIAMOND_AXE : Items.IRON_AXE));
				mob.equipStack(EquipmentSlot.OFFHAND, shield(world, Faction.VIKINGS));
				mob.equipStack(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
				mob.equipStack(EquipmentSlot.CHEST, new ItemStack(Items.CHAINMAIL_CHESTPLATE));
				mob.equipStack(EquipmentSlot.LEGS, dyed(Items.LEATHER_LEGGINGS, 0x7A2E22));
				mob.equipStack(EquipmentSlot.FEET, new ItemStack(Items.IRON_BOOTS));
			}
			default -> {
				// huscarl: hacha o espada con escudo redondo y casco de hierro
				mob.equipStack(EquipmentSlot.MAINHAND, new ItemStack(r.nextBoolean()
						? (elite || r.nextFloat() < 0.6F ? Items.IRON_AXE : Items.STONE_AXE)
						: (elite || r.nextFloat() < 0.6F ? Items.IRON_SWORD : Items.STONE_SWORD)));
				mob.equipStack(EquipmentSlot.OFFHAND, shield(world, Faction.VIKINGS));
				mob.equipStack(EquipmentSlot.HEAD, elite || r.nextFloat() < 0.7F ? new ItemStack(Items.IRON_HELMET) : dyed(Items.LEATHER_HELMET, color));
				mob.equipStack(EquipmentSlot.CHEST, elite || r.nextFloat() < 0.5F ? new ItemStack(Items.CHAINMAIL_CHESTPLATE) : dyed(Items.LEATHER_CHESTPLATE, color));
				mob.equipStack(EquipmentSlot.LEGS, dyed(Items.LEATHER_LEGGINGS, color));
			}
		}
	}

	// ------------------------------------------------------------------ piratas

	public static void equipPirate(MobEntity mob, NpcRole role, Random r, boolean elite) {
		int color = PIRATE_COLORS[r.nextInt(PIRATE_COLORS.length)];
		World world = mob.getWorld();
		switch (role) {
			case BARBARIAN -> {
				// matón: hacha y el pecho al aire
				mob.equipStack(EquipmentSlot.MAINHAND, new ItemStack(r.nextFloat() < 0.3F ? com.evolvedwarfare.registry.ModItems.weapon(com.evolvedwarfare.weapon.WeaponType.FLAIL, "iron")
						: elite || r.nextFloat() < 0.5F ? Items.IRON_AXE : Items.STONE_AXE));
				mob.equipStack(EquipmentSlot.LEGS, dyed(Items.LEATHER_LEGGINGS, color));
			}
			case ARCHER, CROSSBOWMAN -> {
				// tirador: ballesta (con flechas incendiarias de vez en cuando)
				mob.equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.CROSSBOW));
				mob.equipStack(EquipmentSlot.HEAD, dyed(Items.LEATHER_HELMET, color));
				mob.equipStack(EquipmentSlot.CHEST, dyed(Items.LEATHER_CHESTPLATE, SHIRT));
				mob.equipStack(EquipmentSlot.LEGS, dyed(Items.LEATHER_LEGGINGS, 0x2B2B2B));
			}
			case MEDIC -> {
				// cirujano de a bordo
				mob.equipStack(EquipmentSlot.MAINHAND, new ItemStack(ModItems.MEDIC_KIT));
				mob.equipStack(EquipmentSlot.CHEST, dyed(Items.LEATHER_CHESTPLATE, SHIRT));
				mob.equipStack(EquipmentSlot.LEGS, dyed(Items.LEATHER_LEGGINGS, 0x1E2A4A));
			}
			case CAPTAIN -> {
				mob.equipStack(EquipmentSlot.MAINHAND, new ItemStack(elite && r.nextFloat() < 0.3F ? Items.DIAMOND_SWORD : Items.IRON_SWORD));
				mob.equipStack(EquipmentSlot.HEAD, dyed(Items.LEATHER_HELMET, 0x1A1A1A));
				mob.equipStack(EquipmentSlot.CHEST, new ItemStack(Items.CHAINMAIL_CHESTPLATE));
				mob.equipStack(EquipmentSlot.LEGS, dyed(Items.LEATHER_LEGGINGS, 0x1A1A1A));
				mob.equipStack(EquipmentSlot.FEET, new ItemStack(Items.IRON_BOOTS));
			}
			default -> {
				// pirata de abordaje: alfanje (espada) y a veces un escudo con la calavera
				float pick = r.nextFloat();
				mob.equipStack(EquipmentSlot.MAINHAND, new ItemStack(pick < 0.15F ? Items.GOLDEN_SWORD
						: pick < 0.35F ? com.evolvedwarfare.registry.ModItems.weapon(com.evolvedwarfare.weapon.WeaponType.DAGGER, "iron")
						: elite || r.nextFloat() < 0.6F ? Items.IRON_SWORD : Items.STONE_SWORD));
				if (elite || r.nextFloat() < 0.25F) {
					mob.equipStack(EquipmentSlot.OFFHAND, shield(world, Faction.PIRATES));
				}
				if (r.nextFloat() < 0.7F) {
					mob.equipStack(EquipmentSlot.HEAD, dyed(Items.LEATHER_HELMET, color));
				}
				mob.equipStack(EquipmentSlot.CHEST, dyed(Items.LEATHER_CHESTPLATE, r.nextBoolean() ? SHIRT : color));
				mob.equipStack(EquipmentSlot.LEGS, dyed(Items.LEATHER_LEGGINGS, 0x2B2B2B));
				if (elite) {
					mob.equipStack(EquipmentSlot.FEET, dyed(Items.LEATHER_BOOTS, 0x1A1A1A));
				}
			}
		}
	}
}
