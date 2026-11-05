package com.evolvedwarfare.client.hell;

import com.evolvedwarfare.EvolvedWarfare;
import com.evolvedwarfare.network.FireRainPayload;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.DimensionRenderingRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.DimensionEffects;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import org.joml.Matrix4f;

/**
 * El cielo del Nether bíblico: rojo sangre y brumoso, sin sol ni estrellas, con un resplandor de fuego en el horizonte;
 * sin la niebla espesa de vanilla (se ven las montañas a lo lejos). Cae ceniza y alguna brasa; con la lluvia de fuego,
 * el cielo se enciende y caen muchas más.
 */
public final class HellSky {
	private static boolean fireRain;
	private static float glow;

	private HellSky() {
	}

	/** Como las del Nether de vanilla, pero sin niebla espesa. */
	static class Effects extends DimensionEffects {
		Effects() {
			super(Float.NaN, true, SkyType.NONE, false, true);
		}

		@Override
		public Vec3d adjustFogColor(Vec3d color, float sunHeight) {
			return color;
		}

		@Override
		public boolean useThickFog(int camX, int camY) {
			return false;
		}
	}

	public static void register() {
		DimensionRenderingRegistry.registerDimensionEffects(EvolvedWarfare.id("hell"), new Effects());
		DimensionRenderingRegistry.registerSkyRenderer(World.NETHER, HellSky::render);
		ClientPlayNetworking.registerGlobalReceiver(FireRainPayload.ID, (payload, context) -> fireRain = payload.active());
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> fireRain = false);
		ClientTickEvents.END_CLIENT_TICK.register(HellSky::tick);
	}

	private static void tick(MinecraftClient client) {
		ClientWorld world = client.world;
		glow = MathHelper.lerp(0.02F, glow, fireRain ? 1.0F : 0.0F);
		if (world == null || client.player == null || client.isPaused() || world.getRegistryKey() != World.NETHER) {
			return;
		}
		Random random = world.random;
		Vec3d at = client.player.getPos();
		int ash = 3 + (int) (glow * 6);
		for (int i = 0; i < ash; i++) {
			double x = at.x + (random.nextDouble() - 0.5) * 32;
			double y = at.y + 4 + random.nextDouble() * 14;
			double z = at.z + (random.nextDouble() - 0.5) * 32;
			world.addParticle(ParticleTypes.WHITE_ASH, x, y, z, 0.0, -0.02, 0.0);
		}
		if (random.nextFloat() < 0.25F + glow) {
			double x = at.x + (random.nextDouble() - 0.5) * 24;
			double y = at.y + 6 + random.nextDouble() * 10;
			double z = at.z + (random.nextDouble() - 0.5) * 24;
			world.addParticle(ParticleTypes.SMALL_FLAME, x, y, z, (random.nextDouble() - 0.5) * 0.02, -0.06, (random.nextDouble() - 0.5) * 0.02);
		}
	}

	private static int mix(int a, int b, float t) {
		int r = (int) MathHelper.lerp(t, a >> 16 & 255, b >> 16 & 255);
		int g = (int) MathHelper.lerp(t, a >> 8 & 255, b >> 8 & 255);
		int bl = (int) MathHelper.lerp(t, a & 255, b & 255);
		return r << 16 | g << 8 | bl;
	}

	private static void vertex(BufferBuilder buf, Matrix4f m, float x, float y, float z, int rgb) {
		buf.vertex(m, x, y, z).color(rgb >> 16 & 255, rgb >> 8 & 255, rgb & 255, 255);
	}

	private static void render(WorldRenderContext context) {
		float flicker = glow * (0.85F + 0.15F * MathHelper.sin((System.currentTimeMillis() % 100000L) / 180.0F));
		int top = mix(0x1C0303, 0x4A0A02, flicker);
		int mid = mix(0x4A0B05, 0x9A2A06, flicker);
		int horizon = mix(0x8C2408, 0xE0641A, flicker);
		int below = mix(0x200404, 0x3A0804, flicker);
		RenderSystem.depthMask(false);
		RenderSystem.disableBlend();
		RenderSystem.disableCull();
		RenderSystem.setShader(GameRenderer::getPositionColorProgram);
		Matrix4f m = context.positionMatrix();
		BufferBuilder buf = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
		float r = 100.0F;
		float[][] corners = {{-r, -r}, {r, -r}, {r, r}, {-r, r}};
		for (int i = 0; i < 4; i++) {
			float[] a = corners[i];
			float[] b = corners[(i + 1) % 4];
			// de arriba al resplandor del horizonte, y de ahí hacia abajo
			vertex(buf, m, a[0], r, a[1], top);
			vertex(buf, m, b[0], r, b[1], top);
			vertex(buf, m, b[0], 18.0F, b[1], mid);
			vertex(buf, m, a[0], 18.0F, a[1], mid);
			vertex(buf, m, a[0], 18.0F, a[1], mid);
			vertex(buf, m, b[0], 18.0F, b[1], mid);
			vertex(buf, m, b[0], 0.0F, b[1], horizon);
			vertex(buf, m, a[0], 0.0F, a[1], horizon);
			vertex(buf, m, a[0], 0.0F, a[1], horizon);
			vertex(buf, m, b[0], 0.0F, b[1], horizon);
			vertex(buf, m, b[0], -r, b[1], below);
			vertex(buf, m, a[0], -r, a[1], below);
		}
		// la tapa de arriba y la de abajo
		vertex(buf, m, -r, r, -r, top);
		vertex(buf, m, -r, r, r, top);
		vertex(buf, m, r, r, r, top);
		vertex(buf, m, r, r, -r, top);
		vertex(buf, m, -r, -r, -r, below);
		vertex(buf, m, r, -r, -r, below);
		vertex(buf, m, r, -r, r, below);
		vertex(buf, m, -r, -r, r, below);
		BufferRenderer.drawWithGlobalProgram(buf.end());
		RenderSystem.enableCull();
		RenderSystem.depthMask(true);
	}
}
