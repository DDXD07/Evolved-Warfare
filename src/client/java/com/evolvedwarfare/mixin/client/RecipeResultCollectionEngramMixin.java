package com.evolvedwarfare.mixin.client;

import com.evolvedwarfare.skill.Engrams;
import java.util.Set;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.recipebook.RecipeResultCollection;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.recipe.RecipeEntry;
import net.minecraft.recipe.RecipeMatcher;
import net.minecraft.recipe.book.RecipeBook;
import net.minecraft.registry.DynamicRegistryManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * El libro de recetas (fase 115): lo que pide un engrama que no tienes cuenta como que no se puede fabricar (sale en
 * rojo, con "solo lo que se puede fabricar" no sale, y al pulsarlo solo enseña la receta en fantasma).
 */
@Mixin(RecipeResultCollection.class)
public abstract class RecipeResultCollectionEngramMixin {
	@Shadow
	@Final
	private Set<RecipeEntry<?>> craftableRecipes;

	@Shadow
	@Final
	private DynamicRegistryManager registryManager;

	@Inject(method = "computeCraftables", at = @At("TAIL"))
	private void evolvedwarfare$lockedByEngram(RecipeMatcher recipeFinder, int gridWidth, int gridHeight, RecipeBook recipeBook, CallbackInfo ci) {
		PlayerEntity player = MinecraftClient.getInstance().player;
		if (player == null || craftableRecipes.isEmpty()) {
			return;
		}
		craftableRecipes.removeIf(r -> Engrams.missing(player, r.value(), registryManager) != null);
	}
}
