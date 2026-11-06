package com.evolvedwarfare.hell;

import com.evolvedwarfare.registry.ModEffects;
import java.util.List;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SwordItem;
import net.minecraft.item.ToolMaterials;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Las hojas caídas: espadas de ángeles caídos que aún brillan. Cada una tiene un don (fuego, plaga, silencio, caída) y su
 * maldición para quien la empuña.
 */
public class FallenBladeItem extends SwordItem {
	public static final String[] KINDS = {"wrath", "plague", "silence", "fall"};
	public static final int WRATH = 0, PLAGUE = 1, SILENCE = 2, FALL = 3;

	public FallenBladeItem(Settings settings) {
		super(ToolMaterials.DIAMOND, settings.attributeModifiers(SwordItem.createAttributeModifiers(ToolMaterials.DIAMOND, 4, -2.4F)));
	}

	public static int kind(ItemStack stack) {
		Integer k = stack.get(HellStructures.BLADE);
		return k == null ? 0 : Math.floorMod(k, KINDS.length);
	}

	public static ItemStack of(int kind) {
		ItemStack stack = new ItemStack(HellStructures.FALLEN_BLADE);
		stack.set(HellStructures.BLADE, kind);
		return stack;
	}

	@Override
	public Text getName(ItemStack stack) {
		return Text.translatable("blade.evolvedwarfare." + KINDS[kind(stack)]);
	}

	@Override
	public boolean postHit(ItemStack stack, LivingEntity target, LivingEntity attacker) {
		boolean r = super.postHit(stack, target, attacker);
		if (attacker.getWorld() instanceof ServerWorld world) {
			var random = world.random;
			switch (kind(stack)) {
				case WRATH -> {
					target.setOnFireFor(5.0F);
					if (random.nextInt(8) == 0) {
						attacker.addStatusEffect(new StatusEffectInstance(ModEffects.JUDGEMENT_HEAT, 200, 0));
					}
				}
				case PLAGUE -> {
					target.addStatusEffect(new StatusEffectInstance(StatusEffects.POISON, 100, 1));
					target.addStatusEffect(new StatusEffectInstance(StatusEffects.WITHER, 60, 0));
					if (attacker instanceof PlayerEntity p) {
						p.addExhaustion(2.0F);
					}
				}
				case SILENCE -> {
					target.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, 80, 0));
					target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 60, 1));
					if (random.nextInt(6) == 0) {
						attacker.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, 60, 0));
					}
				}
				default -> {
					double dx = target.getX() - attacker.getX(), dz = target.getZ() - attacker.getZ();
					target.takeKnockback(1.6, -dx, -dz);
					target.addVelocity(0, 0.35, 0);
					attacker.damage(world.getDamageSources().magic(), 1.0F);
				}
			}
		}
		return r;
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
		tooltip.add(Text.translatable("blade.evolvedwarfare." + KINDS[kind(stack)] + ".desc").formatted(Formatting.GRAY));
	}

	@Override
	public boolean hasGlint(ItemStack stack) {
		return true;
	}
}
