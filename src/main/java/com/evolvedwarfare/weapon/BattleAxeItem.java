package com.evolvedwarfare.weapon;

import java.util.List;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.item.AxeItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ToolMaterial;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;

/** Hacha de batalla: como un hacha (desarma escudos, también corta madera), pero más pesada y con más daño. */
public class BattleAxeItem extends AxeItem {
	public BattleAxeItem(ToolMaterial material, Settings settings) {
		super(material, settings.attributeModifiers(MedievalWeaponItem.modifiers(material, WeaponType.BATTLE_AXE)));
	}

	@Override
	public float getBonusAttackDamage(Entity target, float baseAttackDamage, DamageSource damageSource) {
		return super.getBonusAttackDamage(target, baseAttackDamage, damageSource)
				+ MedievalWeapons.bonusDamage(WeaponType.BATTLE_AXE, target, baseAttackDamage, damageSource);
	}

	@Override
	public boolean postHit(ItemStack stack, LivingEntity target, LivingEntity attacker) {
		MedievalWeapons.afterHit(WeaponType.BATTLE_AXE, target, attacker);
		return super.postHit(stack, target, attacker);
	}

	@Override
	public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip, TooltipType options) {
		super.appendTooltip(stack, context, tooltip, options);
		tooltip.add(Text.translatable("weapon.evolvedwarfare.battle_axe.tip").withColor(0xAAAAAA));
	}
}
