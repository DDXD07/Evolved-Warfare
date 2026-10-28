package com.evolvedwarfare.weapon;

import com.evolvedwarfare.EvolvedWarfare;
import java.util.List;
import net.minecraft.component.type.AttributeModifierSlot;
import net.minecraft.component.type.AttributeModifiersComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SwordItem;
import net.minecraft.item.ToolMaterial;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/** Daga, lanza, mandoble, alabarda, martillo de guerra y mangual (se comportan como espadas: barrido, encantamientos). */
public class MedievalWeaponItem extends SwordItem {
	public static final Identifier REACH_ID = EvolvedWarfare.id("weapon_reach");
	private final WeaponType type;

	public MedievalWeaponItem(ToolMaterial material, WeaponType type, Settings settings) {
		super(material, settings.attributeModifiers(modifiers(material, type)));
		this.type = type;
	}

	public WeaponType getType() {
		return type;
	}

	/** Daño, velocidad y (si el arma es larga o corta) alcance. */
	public static AttributeModifiersComponent modifiers(ToolMaterial material, WeaponType type) {
		AttributeModifiersComponent.Builder builder = AttributeModifiersComponent.builder()
				.add(EntityAttributes.GENERIC_ATTACK_DAMAGE, new EntityAttributeModifier(BASE_ATTACK_DAMAGE_MODIFIER_ID,
						type.damage + material.getAttackDamage(), EntityAttributeModifier.Operation.ADD_VALUE), AttributeModifierSlot.MAINHAND)
				.add(EntityAttributes.GENERIC_ATTACK_SPEED, new EntityAttributeModifier(BASE_ATTACK_SPEED_MODIFIER_ID,
						type.speed, EntityAttributeModifier.Operation.ADD_VALUE), AttributeModifierSlot.MAINHAND);
		if (type.reach != 0.0) {
			builder.add(EntityAttributes.PLAYER_ENTITY_INTERACTION_RANGE, new EntityAttributeModifier(REACH_ID, type.reach,
					EntityAttributeModifier.Operation.ADD_VALUE), AttributeModifierSlot.MAINHAND);
		}
		return builder.build();
	}

	@Override
	public float getBonusAttackDamage(Entity target, float baseAttackDamage, DamageSource damageSource) {
		return super.getBonusAttackDamage(target, baseAttackDamage, damageSource)
				+ MedievalWeapons.bonusDamage(type, target, baseAttackDamage, damageSource);
	}

	@Override
	public boolean postHit(ItemStack stack, LivingEntity target, LivingEntity attacker) {
		MedievalWeapons.afterHit(type, target, attacker);
		return super.postHit(stack, target, attacker);
	}

	@Override
	public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip, TooltipType options) {
		super.appendTooltip(stack, context, tooltip, options);
		tooltip.add(Text.translatable("weapon.evolvedwarfare." + type.id() + ".tip").withColor(0xAAAAAA));
	}
}
