package com.evolvedwarfare.hell;

import com.evolvedwarfare.EvolvedWarfare;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;

/**
 * La Bendición Divina: el encantamiento (de espadas, hachas, arcos, ballestas, tridentes y mazas) que es <b>la única
 * manera de herir a un demonio</b>. Sin ella, ni las armas, ni las flechas, ni el fuego, ni las explosiones les hacen
 * nada (solo el vacío y /kill). Con ella, además, hacen 4 de daño más a los demonios.
 */
public final class DivineBlessing {
	public static final RegistryKey<Enchantment> KEY = RegistryKey.of(RegistryKeys.ENCHANTMENT, EvolvedWarfare.id("divine_blessing"));
	public static final TagKey<EntityType<?>> DEMONS = TagKey.of(RegistryKeys.ENTITY_TYPE, EvolvedWarfare.id("demons"));
	/** A quién se le dijo ya (para no repetirlo en cada golpe). */
	private static final Map<UUID, Long> TOLD = new WeakHashMap<>();

	private DivineBlessing() {
	}

	public static void register() {
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			if (!isDemon(entity) || source.isIn(DamageTypeTags.BYPASSES_INVULNERABILITY) || blessed(entity.getWorld(), source)) {
				return true;
			}
			if (source.getAttacker() instanceof PlayerEntity player) {
				long now = entity.getWorld().getTime();
				Long last = TOLD.get(player.getUuid());
				if (last == null || now - last > 60) {
					TOLD.put(player.getUuid(), now);
					player.sendMessage(Text.translatable("message.evolvedwarfare.demon.unharmed").formatted(Formatting.GOLD), true);
				}
				entity.getWorld().playSound(null, entity.getBlockPos(), SoundEvents.ITEM_SHIELD_BLOCK, SoundCategory.HOSTILE, 0.8F, 0.6F);
			}
			return false;
		});
	}

	public static boolean isDemon(Entity entity) {
		return entity.getType().isIn(DEMONS);
	}

	/** ¿Viene de un arma bendita (la de la mano, o la que disparó la flecha o lanzó el tridente)? */
	public static boolean blessed(World world, DamageSource source) {
		ItemStack weapon = source.getWeaponStack();
		if (weapon == null || weapon.isEmpty()) {
			if (source.getAttacker() instanceof LivingEntity attacker && source.getSource() == attacker) {
				weapon = attacker.getMainHandStack();
			}
		}
		return weapon != null && !weapon.isEmpty() && level(world, weapon) > 0;
	}

	public static int level(World world, ItemStack stack) {
		RegistryEntry<Enchantment> entry = world.getRegistryManager().get(RegistryKeys.ENCHANTMENT).getEntry(KEY).orElse(null);
		return entry == null ? 0 : EnchantmentHelper.getLevel(entry, stack);
	}
}
