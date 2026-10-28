package com.evolvedwarfare.weapon;

import com.evolvedwarfare.config.EwConfig;
import java.util.List;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.item.BowItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

/** Arco largo: la flecha sale más rápida y más recta, y hace más daño. */
public class LongbowItem extends BowItem {
	public LongbowItem(Settings settings) {
		super(settings);
	}

	public static float power() {
		return (float) EwConfig.get().longbowPower;
	}

	@Override
	protected void shoot(LivingEntity shooter, ProjectileEntity projectile, int index, float speed, float divergence, float yaw,
			@Nullable LivingEntity target) {
		float power = power();
		super.shoot(shooter, projectile, index, speed * power, divergence * 0.6F, yaw, target);
		if (projectile instanceof PersistentProjectileEntity arrow) {
			arrow.setDamage(arrow.getDamage() * (0.5 + 0.5 * power));
		}
	}

	@Override
	public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip, TooltipType options) {
		super.appendTooltip(stack, context, tooltip, options);
		tooltip.add(Text.translatable("weapon.evolvedwarfare.longbow.tip").withColor(0xAAAAAA));
	}
}
