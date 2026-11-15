package com.evolvedwarfare.engineering;

import net.minecraft.block.Block;
import net.minecraft.item.ToolMaterial;
import net.minecraft.recipe.Ingredient;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.TagKey;

/**
 * El acero como material de herramientas y armas (fase 80): entre el hierro y el diamante. +50 % de durabilidad que el
 * hierro (375), un 10 % más rápido al picar y +1 de daño; pica lo mismo que el hierro. No se mejora a netherita.
 */
public final class SteelTier implements ToolMaterial {
	public static final SteelTier INSTANCE = new SteelTier();

	private SteelTier() {
	}

	@Override
	public int getDurability() {
		return 375;
	}

	@Override
	public float getMiningSpeedMultiplier() {
		return 6.6F;
	}

	@Override
	public float getAttackDamage() {
		return 3.0F;
	}

	@Override
	public TagKey<Block> getInverseTag() {
		return BlockTags.INCORRECT_FOR_IRON_TOOL;
	}

	@Override
	public int getEnchantability() {
		return 14;
	}

	@Override
	public Ingredient getRepairIngredient() {
		return Ingredient.ofItems(Metalwork.STEEL_INGOT);
	}
}
