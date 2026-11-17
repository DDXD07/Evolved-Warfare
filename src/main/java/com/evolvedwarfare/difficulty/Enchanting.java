package com.evolvedwarfare.difficulty;

import com.evolvedwarfare.config.EwConfig;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Encantar más difícil (fase 100):
 * <ul>
 * <li>La <b>mesa de encantamientos</b> cuesta el triple de niveles (3, 6 o 9 según la opción) y el doble de lapislázuli; y a
 * veces se resiste y deja una <b>maldición</b> (de desaparición o, en la armadura, de ligamiento): un 8 % la primera
 * opción, un 12 % la segunda y un 16 % la tercera.</li>
 * <li>El <b>yunque</b> cuesta un 50 % más de niveles (y con 40 o más, «¡Demasiado caro!»: se llega antes).</li>
 * <li>La <b>reparación</b> (Mending) repara la mitad con cada punto de experiencia (en {@code data/minecraft/enchantment}).</li>
 * </ul>
 */
public final class Enchanting {
	private Enchanting() {
	}

	public static boolean enabled() {
		return EwConfig.get().hardEnchanting;
	}

	/**
	 * Lo que se cobra de más después de encantar en la mesa (la opción {@code button}, de 0 a 2): niveles, lapislázuli y la
	 * maldición, si toca. Devuelve si ha caído maldición.
	 */
	public static boolean afterTableEnchant(PlayerEntity player, ItemStack item, ItemStack lapis, int button) {
		if (!enabled() || player.getAbilities().creativeMode || player.getWorld().isClient) {
			return false;
		}
		EwConfig c = EwConfig.get();
		int base = button + 1;
		int extraLevels = (int) Math.round(base * (c.enchantLevelMultiplier - 1.0));
		if (extraLevels > 0) {
			player.addExperienceLevels(-Math.min(extraLevels, player.experienceLevel));
		}
		int extraLapis = (int) Math.round(base * (c.enchantLapisMultiplier - 1.0));
		if (extraLapis > 0 && lapis.isOf(Items.LAPIS_LAZULI)) {
			lapis.decrement(Math.min(extraLapis, lapis.getCount()));
		}
		float chance = (float) (c.enchantCurseChance * (1.0 + 0.5 * button));
		if (player.getRandom().nextFloat() < chance) {
			return curse(player, item);
		}
		return false;
	}

	/** Le pone una maldición (de ligamiento a la armadura, a veces; si no, de desaparición). */
	public static boolean curse(PlayerEntity player, ItemStack item) {
		var registry = player.getWorld().getRegistryManager().get(RegistryKeys.ENCHANTMENT);
		boolean armor = player.getPreferredEquipmentSlot(item).getType() == EquipmentSlot.Type.HUMANOID_ARMOR;
		RegistryKey<Enchantment> key = armor && player.getRandom().nextBoolean() ? Enchantments.BINDING_CURSE : Enchantments.VANISHING_CURSE;
		RegistryEntry<Enchantment> curse = registry.getEntry(key).orElse(null);
		if (curse == null || EnchantmentHelper.getLevel(curse, item) > 0) {
			return false;
		}
		item.addEnchantment(curse, 1);
		player.sendMessage(Text.translatable("message.evolvedwarfare.enchanting.cursed").formatted(Formatting.DARK_PURPLE), true);
		player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_ELDER_GUARDIAN_CURSE, SoundCategory.PLAYERS,
				0.4F, 1.4F);
		return true;
	}

	/** Lo que cuesta el yunque. */
	public static int anvilCost(int cost) {
		if (!enabled() || cost <= 0) {
			return cost;
		}
		return (int) Math.ceil(cost * EwConfig.get().anvilCostMultiplier);
	}
}
