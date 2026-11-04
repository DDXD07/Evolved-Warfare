package com.evolvedwarfare.client.catalog;

import com.evolvedwarfare.EvolvedWarfare;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.VertexSorter;
import net.fabricmc.fabric.api.client.rendering.v1.CoreShaderRegistrationCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;

/**
 * Los grabados del catálogo: cada cosa se dibuja primero en una textura aparte (como en el inventario) y luego se pasa a
 * la página con un sombreador que la convierte en un grabado de imprenta en blanco y negro (tinta en las sombras, tramado
 * en diagonal en los medios tonos, papel en las luces y un contorno de tinta). Si el sombreador no se ha podido cargar,
 * se dibuja en color.
 */
public final class Engraving {
	private static final int RESOLUTION = 64;
	@Nullable
	private static ShaderProgram program;
	@Nullable
	private static SimpleFramebuffer buffer;

	private Engraving() {
	}

	public static void register() {
		CoreShaderRegistrationCallback.EVENT.register(context -> context.register(EvolvedWarfare.id("engraving"), VertexFormats.POSITION_TEXTURE,
				shader -> program = shader));
	}

	/** Dibuja la cosa grabada en la página, en (x, y) con ese tamaño (en las coordenadas de la página). */
	public static void draw(DrawContext context, ItemStack stack, float x, float y, float size) {
		MinecraftClient client = MinecraftClient.getInstance();
		ShaderProgram shader = program;
		if (shader == null || stack.isEmpty()) {
			context.getMatrices().push();
			context.getMatrices().translate(x, y, 0.0F);
			context.getMatrices().scale(size / 16.0F, size / 16.0F, 1.0F);
			context.drawItem(stack, 0, 0);
			context.getMatrices().pop();
			return;
		}
		context.draw();
		if (buffer == null) {
			buffer = new SimpleFramebuffer(RESOLUTION, RESOLUTION, true, MinecraftClient.IS_SYSTEM_MAC);
		}
		SimpleFramebuffer fb = buffer;
		fb.setClearColor(0.0F, 0.0F, 0.0F, 0.0F);
		fb.clear(MinecraftClient.IS_SYSTEM_MAC);
		fb.beginWrite(true);
		Matrix4f projection = new Matrix4f(RenderSystem.getProjectionMatrix());
		VertexSorter sorter = RenderSystem.getVertexSorting();
		RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(0.0F, 16.0F, 16.0F, 0.0F, 1000.0F, 21000.0F), VertexSorter.BY_Z);
		Matrix4fStack view = RenderSystem.getModelViewStack();
		view.pushMatrix();
		view.identity();
		view.translate(0.0F, 0.0F, -11000.0F);
		RenderSystem.applyModelViewMatrix();
		DrawContext inner = new DrawContext(client, client.getBufferBuilders().getEntityVertexConsumers());
		inner.drawItem(stack, 0, 0);
		inner.draw();
		view.popMatrix();
		RenderSystem.applyModelViewMatrix();
		RenderSystem.setProjectionMatrix(projection, sorter);
		client.getFramebuffer().beginWrite(true);

		RenderSystem.setShader(() -> shader);
		RenderSystem.setShaderTexture(0, fb.getColorAttachment());
		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();
		Matrix4f m = context.getMatrices().peek().getPositionMatrix();
		BufferBuilder b = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE);
		// la textura del búfer va boca abajo
		b.vertex(m, x, y, 0.0F).texture(0.0F, 1.0F);
		b.vertex(m, x, y + size, 0.0F).texture(0.0F, 0.0F);
		b.vertex(m, x + size, y + size, 0.0F).texture(1.0F, 0.0F);
		b.vertex(m, x + size, y, 0.0F).texture(1.0F, 1.0F);
		BufferRenderer.drawWithGlobalProgram(b.end());
		RenderSystem.disableBlend();
	}
}
