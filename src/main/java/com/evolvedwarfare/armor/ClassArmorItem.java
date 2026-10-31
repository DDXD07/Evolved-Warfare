package com.evolvedwarfare.armor;

import com.evolvedwarfare.EvolvedWarfare;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import net.minecraft.component.type.AttributeModifierSlot;
import net.minecraft.component.type.AttributeModifiersComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ArmorMaterial;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.recipe.Ingredient;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

/**
 * Una pieza de armadura de clase ({@link ArmorClass}) de un material ({@link ArmorTier}). Se ve como las de vanilla (con
 * su textura en dos capas) y sus números (armadura, dureza, ventajas y desventajas) no salen en la descripción hasta
 * que se mantiene pulsada la tecla de recargar (R), para no llenarla de texto.
 */
public class ClassArmorItem extends ArmorItem {
	/** El cliente lo cambia: ¿está pulsada la tecla para ver las estadísticas? */
	public static BooleanSupplier statsKey = () -> false;
	/** El nombre de esa tecla (R, si no se ha cambiado). */
	public static Supplier<Text> statsKeyName = () -> Text.literal("R");

	private static final Map<ArmorClass, Map<ArmorTier, RegistryEntry<ArmorMaterial>>> MATERIALS = new EnumMap<>(ArmorClass.class);
	private static final EquipmentSlot[] ARMOR_SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

	private final ArmorClass armorClass;
	private final ArmorTier tier;

	public ClassArmorItem(ArmorClass armorClass, ArmorTier tier, Type type, Settings settings) {
		super(material(armorClass, tier), type, settings.attributeModifiers(modifiers(armorClass, tier, type)));
		this.armorClass = armorClass;
		this.tier = tier;
	}

	public ArmorClass armorClass() {
		return armorClass;
	}

	public ArmorTier tier() {
		return tier;
	}

	/** El material de cada clase y metal (con su propia textura: textures/models/armor/clase_metal_layer_1/2.png). */
	public static RegistryEntry<ArmorMaterial> material(ArmorClass armorClass, ArmorTier tier) {
		return MATERIALS.computeIfAbsent(armorClass, c -> new EnumMap<>(ArmorTier.class)).computeIfAbsent(tier, t -> {
			Map<ArmorItem.Type, Integer> defense = new EnumMap<>(ArmorItem.Type.class);
			for (ArmorItem.Type type : ArmorItem.Type.values()) {
				defense.put(type, protection(armorClass, t, type));
			}
			String id = armorClass.id() + "_" + t.id();
			ArmorMaterial value = new ArmorMaterial(defense, t.enchantability, t.equipSound(), () -> Ingredient.ofItems(t.material),
					List.of(new ArmorMaterial.Layer(EvolvedWarfare.id(id))), t.toughness, t.knockbackResistance);
			return Registry.registerReference(Registries.ARMOR_MATERIAL, EvolvedWarfare.id(id), value);
		});
	}

	/** La armadura de la pieza: la del material, menos lo que pierde la clase (ladrón y mosquetero, 1 por pieza). */
	public static int protection(ArmorClass armorClass, ArmorTier tier, ArmorItem.Type type) {
		return Math.max(0, tier.protection(type) - armorClass.armorPenalty);
	}

	/** Lo que da una pieza (la cuarta parte del conjunto), como fracción. */
	private static double piece(ArmorClass armorClass, ArmorTier tier, ArmorClass.Effect effect) {
		return armorClass.setPercent(effect, tier) / 400.0;
	}

	/**
	 * Los atributos de la pieza (ocultos en la descripción; se ven con la tecla): armadura, dureza, empuje y, según la
	 * clase, daño cuerpo a cuerpo, resistencia al empuje y velocidad.
	 */
	private static AttributeModifiersComponent modifiers(ArmorClass armorClass, ArmorTier tier, ArmorItem.Type type) {
		AttributeModifierSlot slot = AttributeModifierSlot.forEquipmentSlot(type.getEquipmentSlot());
		String name = type.getName();
		AttributeModifiersComponent.Builder builder = AttributeModifiersComponent.builder();
		builder.add(EntityAttributes.GENERIC_ARMOR, new EntityAttributeModifier(EvolvedWarfare.id("class_armor." + name),
				protection(armorClass, tier, type), EntityAttributeModifier.Operation.ADD_VALUE), slot);
		if (tier.toughness > 0.0F) {
			builder.add(EntityAttributes.GENERIC_ARMOR_TOUGHNESS, new EntityAttributeModifier(EvolvedWarfare.id("class_toughness." + name),
					tier.toughness, EntityAttributeModifier.Operation.ADD_VALUE), slot);
		}
		double knockback = tier.knockbackResistance + piece(armorClass, tier, ArmorClass.Effect.KNOCKBACK);
		if (knockback != 0.0) {
			builder.add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, new EntityAttributeModifier(
					EvolvedWarfare.id("class_knockback." + name), knockback, EntityAttributeModifier.Operation.ADD_VALUE), slot);
		}
		double melee = piece(armorClass, tier, ArmorClass.Effect.MELEE_DAMAGE);
		if (melee != 0.0) {
			builder.add(EntityAttributes.GENERIC_ATTACK_DAMAGE, new EntityAttributeModifier(EvolvedWarfare.id("class_melee." + name),
					melee, EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL), slot);
		}
		double speed = piece(armorClass, tier, ArmorClass.Effect.MOVE_SPEED);
		if (speed != 0.0) {
			builder.add(EntityAttributes.GENERIC_MOVEMENT_SPEED, new EntityAttributeModifier(EvolvedWarfare.id("class_speed." + name),
					speed, EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL), slot);
		}
		return builder.build().withShowInTooltip(false);
	}

	// ------------------------------------------------------------------ efectos que no son atributos

	/** Lo que suman las piezas de clase que lleva puestas para ese efecto (fracción: 0,25 = +25 %). */
	public static float bonus(LivingEntity entity, ArmorClass.Effect effect) {
		double total = 0.0;
		for (EquipmentSlot slot : ARMOR_SLOTS) {
			if (entity.getEquippedStack(slot).getItem() instanceof ClassArmorItem item) {
				total += piece(item.armorClass, item.tier, effect);
			}
		}
		return (float) total;
	}

	/** Daño de flechas (arquero), de balas (mosquetero) y por la espalda (ladrón). Lo llama el mixin del daño. */
	public static float modifyDamage(LivingEntity target, DamageSource source, float amount) {
		if (!(source.getAttacker() instanceof LivingEntity attacker)) {
			return amount;
		}
		Entity direct = source.getSource();
		if (direct instanceof com.evolvedwarfare.weapon.MusketBallEntity) {
			return amount * (1.0F + bonus(attacker, ArmorClass.Effect.BULLET_DAMAGE));
		}
		if (direct instanceof PersistentProjectileEntity) {
			return amount * (1.0F + bonus(attacker, ArmorClass.Effect.ARROW_DAMAGE));
		}
		if (direct == attacker && behind(target, attacker)) {
			return amount * (1.0F + bonus(attacker, ArmorClass.Effect.BACKSTAB));
		}
		return amount;
	}

	/** ¿Le da por la espalda? (el atacante está detrás de hacia donde mira el blanco) */
	public static boolean behind(LivingEntity target, Entity attacker) {
		Vec3d facing = Vec3d.fromPolar(0.0F, target.getBodyYaw());
		Vec3d toAttacker = new Vec3d(attacker.getX() - target.getX(), 0.0, attacker.getZ() - target.getZ());
		if (toAttacker.lengthSquared() < 1.0E-4) {
			return false;
		}
		return facing.dotProduct(toAttacker.normalize()) < -0.3;
	}

	// ------------------------------------------------------------------ descripción

	@Override
	public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip, TooltipType options) {
		super.appendTooltip(stack, context, tooltip, options);
		tooltip.add(Text.translatable("armor_class.evolvedwarfare." + armorClass.id()).append(" · ")
				.append(Text.translatable("armor_tier.evolvedwarfare." + tier.id())).withColor(armorClass.color));
		if (!statsKey.getAsBoolean()) {
			tooltip.add(Text.translatable("armor_class.evolvedwarfare.hold", statsKeyName.get()).formatted(Formatting.DARK_GRAY));
			return;
		}
		tooltip.add(Text.translatable("armor_class.evolvedwarfare.armor", protection(armorClass, tier, getType())).formatted(Formatting.BLUE));
		if (tier.toughness > 0.0F) {
			tooltip.add(Text.translatable("armor_class.evolvedwarfare.toughness", number(tier.toughness)).formatted(Formatting.BLUE));
		}
		if (tier.knockbackResistance > 0.0F) {
			tooltip.add(Text.translatable("armor_class.evolvedwarfare.knockback", Math.round(tier.knockbackResistance * 100)).formatted(Formatting.BLUE));
		}
		tooltip.add(Text.translatable("armor_class.evolvedwarfare.set").formatted(Formatting.GRAY));
		for (ArmorClass.Bonus bonus : armorClass.pros) {
			tooltip.add(line(bonus.effect(), armorClass.setPercent(bonus.effect(), tier)).formatted(Formatting.GREEN));
		}
		for (ArmorClass.Bonus bonus : armorClass.cons) {
			tooltip.add(line(bonus.effect(), bonus.percent()).formatted(Formatting.RED));
		}
		if (armorClass.armorPenalty > 0) {
			tooltip.add(Text.translatable("armor_class.evolvedwarfare.less_armor", 4 * armorClass.armorPenalty).formatted(Formatting.RED));
		}
		tooltip.add(Text.translatable("armor_class.evolvedwarfare.per_piece").formatted(Formatting.DARK_GRAY));
	}

	private static MutableText line(ArmorClass.Effect effect, int percent) {
		String sign = percent >= 0 ? "+" + percent : "−" + (-percent);
		return Text.translatable("armor_class.evolvedwarfare.bonus", sign, Text.translatable("armor_class.evolvedwarfare.effect." + effect.id()));
	}

	private static String number(float value) {
		return value == Math.round(value) ? String.valueOf(Math.round(value)) : String.valueOf(value);
	}

	@SuppressWarnings("unused")
	private static Identifier id(String path) {
		return EvolvedWarfare.id(path);
	}
}
