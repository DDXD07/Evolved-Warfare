package com.evolvedwarfare.mixin.client;

import com.evolvedwarfare.skill.Engram;
import com.evolvedwarfare.skill.Engrams;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.recipebook.AnimatedResultButton;
import net.minecraft.client.gui.screen.recipebook.RecipeResultCollection;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.recipe.RecipeEntry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** En el libro de recetas (fase 115), lo que pide un engrama que no tienes sale oscurecido, con un candado y su nivel. */
@Mixin(AnimatedResultButton.class)
public abstract class AnimatedResultButtonEngramMixin {
	@Shadow
	private RecipeResultCollection resultCollection;

	@Shadow
	public abstract RecipeEntry<?> currentRecipe();

	@Unique
	private static final String[] EVOLVEDWARFARE$LOCK = {
			"..###..",
			".#...#.",
			".#...#.",
			"#######",
			"###.###",
			"###.###",
			"#######"};

	@Inject(method = "renderWidget", at = @At("TAIL"))
	private void evolvedwarfare$engramLock(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.player == null || resultCollection == null) {
			return;
		}
		RecipeEntry<?> recipe = currentRecipe();
		Engram engram = recipe == null ? null : Engrams.missing(client.player, recipe.value(), resultCollection.getRegistryManager());
		if (engram == null) {
			return;
		}
		ClickableWidget self = (ClickableWidget) (Object) this;
		int x = self.getX(), y = self.getY();
		context.getMatrices().push();
		context.getMatrices().translate(0.0F, 0.0F, 300.0F);
		context.fill(x + 1, y + 1, x + self.getWidth() - 1, y + self.getHeight() - 1, 0x99101418);
		// el candado, abajo a la izquierda
		int lx = x + 2, ly = y + self.getHeight() - 10;
		for (int row = 0; row < EVOLVEDWARFARE$LOCK.length; row++) {
			for (int col = 0; col < EVOLVEDWARFARE$LOCK[row].length(); col++) {
				if (EVOLVEDWARFARE$LOCK[row].charAt(col) == '#') {
					context.fill(lx + col, ly + row, lx + col + 1, ly + row + 1, row < 3 ? 0xFFB0B4BA : 0xFFE0B040);
				}
			}
		}
		String level = String.valueOf(engram.level);
		boolean reachable = com.evolvedwarfare.client.ClientSkills.data.level >= engram.level;
		context.drawTextWithShadow(client.textRenderer, level, x + self.getWidth() - 2 - client.textRenderer.getWidth(level), y + 2,
				reachable ? 0xFFFFD75E : 0xFFFF6060);
		context.getMatrices().pop();
	}
}
