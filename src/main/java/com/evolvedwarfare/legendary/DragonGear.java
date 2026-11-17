package com.evolvedwarfare.legendary;

import com.evolvedwarfare.EvolvedWarfare;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ArmorMaterial;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SwordItem;
import net.minecraft.item.ToolMaterial;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.recipe.Ingredient;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Rarity;

/**
 * Lo que se hace con los dragones (fase 102): las armaduras de escamas (de fuego y de hielo: como la de diamante, más
 * dura; cada pieza quita un 15 % del daño de su elemento y el juego completo hace inmune al fuego o al frío) y las
 * espadas de hueso de dragón (más que la de netherita; la flamígera quema y la helada congela, y cada una hiere de más al
 * dragón contrario).
 */
public final class DragonGear {
	public enum Element {
		FIRE, ICE
	}

	// ------------------------------------------------------------------ las armaduras

	private static final Map<Element, RegistryEntry<ArmorMaterial>> MATERIALS = new EnumMap<>(Element.class);

	static RegistryEntry<ArmorMaterial> material(Element element) {
		return MATERIALS.computeIfAbsent(element, e -> {
			String id = e == Element.FIRE ? "fire_dragon_scale" : "ice_dragon_scale";
			Map<ArmorItem.Type, Integer> defense = new EnumMap<>(ArmorItem.Type.class);
			defense.put(ArmorItem.Type.HELMET, 3);
			defense.put(ArmorItem.Type.CHESTPLATE, 8);
			defense.put(ArmorItem.Type.LEGGINGS, 6);
			defense.put(ArmorItem.Type.BOOTS, 3);
			defense.put(ArmorItem.Type.BODY, 11);
			Item scale = e == Element.FIRE ? Legendaries.FIRE_DRAGON_SCALE : Legendaries.ICE_DRAGON_SCALE;
			return Registry.registerReference(Registries.ARMOR_MATERIAL, EvolvedWarfare.id(id), new ArmorMaterial(defense, 18,
					SoundEvents.ITEM_ARMOR_EQUIP_NETHERITE, () -> Ingredient.ofItems(scale), List.of(new ArmorMaterial.Layer(EvolvedWarfare.id(id))),
					3.5F, 0.1F));
		});
	}

	public static class DragonArmorItem extends ArmorItem {
		private final Element element;

		public DragonArmorItem(Element element, Type type) {
			super(material(element), type, settings(element).maxDamage(type.getMaxDamage(40)));
			this.element = element;
		}

		public Element element() {
			return element;
		}

		@Override
		public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
			super.appendTooltip(stack, context, tooltip, type);
			tooltip.add(Text.translatable("armor.evolvedwarfare.dragon." + (element == Element.FIRE ? "fire" : "ice"))
					.formatted(element == Element.FIRE ? Formatting.GOLD : Formatting.AQUA));
		}
	}

	private static Item.Settings settings(Element element) {
		Item.Settings s = new Item.Settings().rarity(Rarity.EPIC);
		return element == Element.FIRE ? s.fireproof() : s;
	}

	/** Cuántas piezas lleva de ese elemento. */
	public static int pieces(LivingEntity entity, Element element) {
		int n = 0;
		for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
			if (entity.getEquippedStack(slot).getItem() instanceof DragonArmorItem a && a.element() == element) {
				n++;
			}
		}
		return n;
	}

	/** Lo que quitan las escamas del daño de su elemento (desde LivingEntityDamageMixin). */
	public static float modifyIncoming(LivingEntity entity, DamageSource source, float amount) {
		if (source.isIn(DamageTypeTags.IS_FIRE)) {
			int n = pieces(entity, Element.FIRE);
			return n >= 4 ? 0.0F : amount * (1.0F - 0.15F * n);
		}
		if (source.isIn(DamageTypeTags.IS_FREEZING) || source.isOf(Legendaries.DRAGON_ICE)) {
			int n = pieces(entity, Element.ICE);
			return n >= 4 ? 0.0F : amount * (1.0F - 0.15F * n);
		}
		return amount;
	}

	/** El juego completo de hielo: el que te pega se queda lento. */
	public static void onHurt(LivingEntity victim, DamageSource source) {
		if (source.getAttacker() instanceof LivingEntity attacker && attacker != victim && pieces(victim, Element.ICE) >= 4
				&& source.getSource() == attacker) {
			attacker.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 60, 1), victim);
			attacker.setFrozenTicks(Math.min(attacker.getMinFreezeDamageTicks(), attacker.getFrozenTicks() + 40));
		}
	}

	private static void tick(MinecraftServer server) {
		if (server.getTicks() % 20 != 0) {
			return;
		}
		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			if (pieces(player, Element.FIRE) >= 4) {
				player.addStatusEffect(new StatusEffectInstance(StatusEffects.FIRE_RESISTANCE, 50, 0, true, false, true));
			}
			if (pieces(player, Element.ICE) >= 4) {
				player.setFrozenTicks(0);
			}
		}
	}

	// ------------------------------------------------------------------ las espadas

	/** El hueso de dragón: más que la netherita. */
	public static final ToolMaterial DRAGONBONE = new ToolMaterial() {
		@Override
		public int getDurability() {
			return 2400;
		}

		@Override
		public float getMiningSpeedMultiplier() {
			return 10.0F;
		}

		@Override
		public float getAttackDamage() {
			return 5.0F;
		}

		@Override
		public TagKey<Block> getInverseTag() {
			return BlockTags.INCORRECT_FOR_NETHERITE_TOOL;
		}

		@Override
		public int getEnchantability() {
			return 20;
		}

		@Override
		public Ingredient getRepairIngredient() {
			return Ingredient.ofItems(Legendaries.DRAGON_BONE);
		}
	};

	public static class DragonboneSwordItem extends SwordItem {
		/** null: la normal. */
		private final Element element;

		public DragonboneSwordItem(Element element) {
			super(DRAGONBONE, (element == Element.FIRE ? new Item.Settings().fireproof() : new Item.Settings()).rarity(Rarity.EPIC)
					.attributeModifiers(SwordItem.createAttributeModifiers(DRAGONBONE, 3, -2.4F)));
			this.element = element;
		}

		@Override
		public boolean postHit(ItemStack stack, LivingEntity target, LivingEntity attacker) {
			if (element == Element.FIRE) {
				target.setOnFireFor(5.0F);
			} else if (element == Element.ICE) {
				target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 80, 1), attacker);
				target.setFrozenTicks(Math.min(target.getMinFreezeDamageTicks() + 60, target.getFrozenTicks() + 80));
			}
			return super.postHit(stack, target, attacker);
		}

		@Override
		public float getBonusAttackDamage(Entity target, float baseAttackDamage, DamageSource damageSource) {
			if (target instanceof DragonEntity dragon && element != null && dragon.ice() == (element == Element.FIRE)) {
				return 8.0F;
			}
			return 0.0F;
		}

		@Override
		public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
			tooltip.add(Text.translatable(getTranslationKey() + ".desc").formatted(Formatting.GRAY));
		}
	}

	private DragonGear() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(DragonGear::tick);
	}

	static boolean wears(PlayerEntity player, Element element) {
		return pieces(player, element) >= 4;
	}
}
