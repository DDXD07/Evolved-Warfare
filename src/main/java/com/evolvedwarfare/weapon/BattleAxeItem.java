package com.evolvedwarfare.weapon;

import java.util.List;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.AxeItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ToolMaterial;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

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

	/** Mantener clic derecho: cargar el golpe fuerte ({@link HeavyAttack}); sobre un tronco sigue descortezándolo. */
	@Override
	public int getMaxUseTime(ItemStack stack, LivingEntity user) {
		return HeavyAttack.maxUseTime();
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
		return HeavyAttack.use(world, user, hand);
	}

	@Override
	public void onStoppedUsing(ItemStack stack, World world, LivingEntity user, int remainingUseTicks) {
		HeavyAttack.release(stack, world, user, remainingUseTicks, WeaponType.BATTLE_AXE);
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
		tooltip.add(Text.translatable("weapon.evolvedwarfare.heavy.tip").withColor(0x8A8A8A));
	}
}
