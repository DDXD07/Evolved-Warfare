package com.evolvedwarfare.client.hell;

import com.evolvedwarfare.EvolvedWarfare;
import com.evolvedwarfare.hell.HellPayloads;
import com.evolvedwarfare.hell.NetherClimate;
import com.evolvedwarfare.registry.ModBlocks;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;

/**
 * Lo que se ve y se siente del Nether bíblico en el cliente: la niebla de cada bioma (negra en el Abismo, humo en las
 * montañas, bruma roja en el Valle), el aire que tiembla con el calor (el campo de visión ondula cerca de la lava y en el
 * Lago), las siluetas de las visiones (se desvanecen si las miras) y el ojo de la corrupción junto a la barra.
 */
public final class HellClient {
	private static final Identifier VISION = EvolvedWarfare.id("textures/entity/hell/vision.png");
	private static final Identifier EYE = EvolvedWarfare.id("textures/gui/corruption.png");
	private static int corruption;
	private static final List<Vision> VISIONS = new ArrayList<>();
	private static float fogStart = -1.0F;
	private static float fogEnd = -1.0F;
	private static float heat;

	private record Vision(Vec3d pos, long start, long[] gone) {
	}

	private HellClient() {
	}

	public static void register() {
		BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.SULFUR_CRYSTAL, RenderLayer.getCutout());
		BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.STONE_FRUIT, RenderLayer.getCutout());
		// la flora del Nether vivo (fase 37)
		for (net.minecraft.block.Block b : new net.minecraft.block.Block[] {com.evolvedwarfare.hell.HellFlora.EMBER_GRASS,
				com.evolvedwarfare.hell.HellFlora.EMBER_FERN, com.evolvedwarfare.hell.HellFlora.HELLBLOOM, com.evolvedwarfare.hell.HellFlora.CORPSE_LILY,
				com.evolvedwarfare.hell.HellFlora.BLOODCAP, com.evolvedwarfare.hell.HellFlora.THORN_BUSH, com.evolvedwarfare.hell.HellFlora.EMBER_VINE}) {
			BlockRenderLayerMap.INSTANCE.putBlock(b, RenderLayer.getCutout());
		}
		BlockRenderLayerMap.INSTANCE.putBlock(com.evolvedwarfare.hell.HellFlora.EMBER_LEAVES, RenderLayer.getCutoutMipped());
		BlockRenderLayerMap.INSTANCE.putBlock(com.evolvedwarfare.hell.HellFlora.THORN_LEAVES, RenderLayer.getCutoutMipped());
		// las estructuras (fase 35)
		BlockRenderLayerMap.INSTANCE.putBlock(com.evolvedwarfare.hell.HellStructures.SHIFTING_BLOCK, RenderLayer.getTranslucent());
		BlockRenderLayerMap.INSTANCE.putBlock(com.evolvedwarfare.hell.HellStructures.BABEL_PORTAL, RenderLayer.getTranslucent());
		BlockRenderLayerMap.INSTANCE.putBlock(com.evolvedwarfare.hell.HellStructures.FALLEN_WING, RenderLayer.getCutout());
		BlockRenderLayerMap.INSTANCE.putBlock(com.evolvedwarfare.hell.HellStructures.FALLEN_SWORD, RenderLayer.getCutout());
		net.minecraft.client.render.block.entity.BlockEntityRendererFactories.register(com.evolvedwarfare.hell.HellStructures.IDOL_ENTITY, IdolRenderer::new);
		net.minecraft.client.render.block.entity.BlockEntityRendererFactories.register(com.evolvedwarfare.hell.Apocalypse.RITUAL_CIRCLE_ENTITY,
				RitualCircleRenderer::new);
		net.fabricmc.fabric.api.object.builder.v1.client.model.FabricModelPredicateProviderRegistry.register(
				com.evolvedwarfare.hell.Apocalypse.SEAL_FRAGMENT, com.evolvedwarfare.EvolvedWarfare.id("seal"),
				(stack, world, entity, seed) -> com.evolvedwarfare.hell.SealFragmentItem.kind(stack) / 10.0F);
		net.fabricmc.fabric.api.object.builder.v1.client.model.FabricModelPredicateProviderRegistry.register(
				com.evolvedwarfare.hell.HellStructures.FALLEN_BLADE, com.evolvedwarfare.EvolvedWarfare.id("blade"),
				(stack, world, entity, seed) -> com.evolvedwarfare.hell.FallenBladeItem.kind(stack) / 10.0F);
		ClientPlayNetworking.registerGlobalReceiver(HellPayloads.Corruption.ID, (p, c) -> corruption = p.value());
		ClientPlayNetworking.registerGlobalReceiver(HellPayloads.Vision.ID, (p, c) -> VISIONS.add(new Vision(new Vec3d(p.x(), p.y(), p.z()),
				Util.getMeasuringTimeMs(), new long[] {0L})));
		ClientPlayConnectionEvents.DISCONNECT.register((h, c) -> {
			corruption = 0;
			VISIONS.clear();
		});
		HudRenderCallback.EVENT.register((ctx, tick) -> hud(ctx));
		WorldRenderEvents.AFTER_ENTITIES.register(HellClient::visions);
	}

	private static boolean inNether() {
		MinecraftClient c = MinecraftClient.getInstance();
		return c.world != null && c.world.getRegistryKey() == World.NETHER;
	}

	private static boolean biome(BlockPos pos, RegistryKey<Biome> key) {
		MinecraftClient c = MinecraftClient.getInstance();
		return c.world != null && c.world.getBiome(pos).matchesKey(key);
	}

	// ------------------------------------------------------------------ niebla y calor

	/** La niebla del bioma donde está la cámara (se va acercando poco a poco). Devuelve {inicio, fin} o null si no toca. */
	public static float[] fog(Camera camera, float viewDistance) {
		if (!inNether()) {
			fogStart = -1.0F;
			return null;
		}
		BlockPos pos = camera.getBlockPos();
		float start = viewDistance * 0.55F;
		float end = viewDistance;
		if (biome(pos, NetherClimate.ABYSS)) {
			start = 1.0F;
			end = 24.0F;
		} else if (biome(pos, NetherClimate.MOUNTAINS)) {
			start = 8.0F;
			end = Math.min(viewDistance, 56.0F);
		} else if (biome(pos, NetherClimate.VALLEY)) {
			start = 20.0F;
			end = Math.min(viewDistance, 110.0F);
		} else if (biome(pos, NetherClimate.RUINS)) {
			start = 12.0F;
			end = Math.min(viewDistance, 80.0F);
		}
		if (fogStart < 0) {
			fogStart = start;
			fogEnd = end;
		}
		fogStart = MathHelper.lerp(0.02F, fogStart, start);
		fogEnd = MathHelper.lerp(0.02F, fogEnd, end);
		return new float[] {fogStart, fogEnd};
	}

	/** Lo que tiembla el aire (en grados de campo de visión): cerca de la lava y en el Lago de Fuego. */
	public static double heatWobble() {
		MinecraftClient c = MinecraftClient.getInstance();
		if (!inNether() || c.player == null) {
			heat = 0.0F;
			return 0.0;
		}
		BlockPos pos = c.player.getBlockPos();
		float target = biome(pos, NetherClimate.LAKE) ? 0.6F : 0.0F;
		if (c.world.getTime() % 10 == 0 || heat > 0.0F) {
			int lava = 0;
			for (BlockPos p : BlockPos.iterate(pos.add(-4, -3, -4), pos.add(4, 1, 4))) {
				if (c.world.getFluidState(p).isIn(FluidTags.LAVA)) {
					lava++;
				}
			}
			target = Math.max(target, Math.min(1.0F, lava / 20.0F));
		}
		heat = MathHelper.lerp(0.01F, heat, target);
		double t = Util.getMeasuringTimeMs() / 1000.0;
		return heat * (Math.sin(t * 2.1) * 0.6 + Math.sin(t * 5.3 + 1.0) * 0.3 + Math.sin(t * 11.7) * 0.15);
	}

	// ------------------------------------------------------------------ el ojo de la corrupción

	private static void hud(DrawContext ctx) {
		MinecraftClient c = MinecraftClient.getInstance();
		if (c.player == null || c.options.hudHidden || corruption <= 0 && !inNether()) {
			return;
		}
		int x = ctx.getScaledWindowWidth() / 2 + 96;
		int y = ctx.getScaledWindowHeight() - 19;
		float pulse = corruption > 50 ? 0.5F + 0.5F * MathHelper.sin(Util.getMeasuringTimeMs() / (400.0F - corruption * 2)) : 1.0F;
		ctx.drawTexture(EYE, x, y, corruption > 0 ? 9 : 0, 0, 9, 9, 18, 9);
		int h = (int) (corruption / (float) NetherClimate.MAX_CORRUPTION * 16);
		ctx.fill(x + 11, y - 7, x + 14, y + 9, 0xA0201020);
		ctx.fill(x + 11, y + 9 - h, x + 14, y + 9, (int) (180 + 75 * pulse) << 24 | 0x9628C0);
	}

	// ------------------------------------------------------------------ las siluetas

	private static void visions(WorldRenderContext ctx) {
		if (VISIONS.isEmpty() || ctx.consumers() == null || ctx.matrixStack() == null) {
			return;
		}
		Camera camera = ctx.camera();
		Vec3d cam = camera.getPos();
		Vec3d look = Vec3d.fromPolar(camera.getPitch(), camera.getYaw());
		long now = Util.getMeasuringTimeMs();
		MatrixStack m = ctx.matrixStack();
		VertexConsumer b = ctx.consumers().getBuffer(RenderLayer.getEntityTranslucent(VISION));
		VISIONS.removeIf(v -> now - v.start() > 6000 || v.gone()[0] != 0 && now - v.gone()[0] > 400);
		for (Vision v : VISIONS) {
			Vec3d to = v.pos().add(0, 1.2, 0).subtract(cam);
			double angle = Math.toDegrees(Math.acos(MathHelper.clamp(to.normalize().dotProduct(look), -1.0, 1.0)));
			if (angle < 7.0 && v.gone()[0] == 0 && now - v.start() > 600) {
				// la miras: se va
				v.gone()[0] = now;
				MinecraftClient.getInstance().player.playSound(SoundEvents.ENTITY_VEX_DEATH, 1.0F, 0.6F);
			}
			float age = (now - v.start()) / 1000.0F;
			float alpha = Math.min(1.0F, age / 0.8F) * Math.min(1.0F, (6.0F - age) / 1.5F);
			if (v.gone()[0] != 0) {
				alpha *= 1.0F - (now - v.gone()[0]) / 400.0F;
			}
			if (alpha <= 0.01F) {
				continue;
			}
			int a = (int) (alpha * 220);
			m.push();
			m.translate(v.pos().x - cam.x, v.pos().y - cam.y, v.pos().z - cam.z);
			m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180.0F - camera.getYaw()));
			MatrixStack.Entry e = m.peek();
			int light = LightmapTextureManager.MAX_LIGHT_COORDINATE;
			float w = 0.7F;
			float h = 2.6F;
			b.vertex(e, -w, h, 0).color(255, 255, 255, a).texture(0, 0).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(e, 0, 0, 1);
			b.vertex(e, -w, 0, 0).color(255, 255, 255, a).texture(0, 1).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(e, 0, 0, 1);
			b.vertex(e, w, 0, 0).color(255, 255, 255, a).texture(1, 1).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(e, 0, 0, 1);
			b.vertex(e, w, h, 0).color(255, 255, 255, a).texture(1, 0).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(e, 0, 0, 1);
			m.pop();
		}
	}
}
