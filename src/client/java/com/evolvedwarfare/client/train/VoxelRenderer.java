package com.evolvedwarfare.client.train;

import com.evolvedwarfare.voxel.VoxelBuild;
import com.evolvedwarfare.voxel.VoxelGrid;
import com.evolvedwarfare.voxel.VoxelHost;
import com.evolvedwarfare.voxel.VoxelMachine;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.block.BedBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.enums.BedPart;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.state.property.Properties;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.EmptyBlockView;
import net.minecraft.world.World;

/**
 * Dibuja lo construido bloque a bloque encima de una entidad (la carreta) con los modelos de verdad de los bloques, y la
 * vista previa al construir: el bloque en verde donde irá (en rojo si no cabe) y el contorno del bloque al que se apunta.
 */
public final class VoxelRenderer {
	private VoxelRenderer() {
	}

	public static void register() {
		WorldRenderEvents.AFTER_ENTITIES.register(VoxelRenderer::preview);
	}

	/** Los bloques de la rejilla, en las coordenadas de la entidad (ya giradas). */
	public static void draw(VoxelGrid g, MatrixStack m, VertexConsumerProvider consumers, int light, World world, int seed) {
		MinecraftClient client = MinecraftClient.getInstance();
		for (int i = 0; i < g.size(); i++) {
			BlockState s = g.get(i);
			if (s.isAir()) {
				continue;
			}
			Block b = s.getBlock();
			int l = light;
			int lum = s.getLuminance();
			if (lum > 0) {
				l = LightmapTextureManager.pack(Math.max(LightmapTextureManager.getBlockLightCoordinates(light), lum),
						LightmapTextureManager.getSkyLightCoordinates(light));
			}
			Vec3d c = g.corner(i);
			m.push();
			m.translate(c.x, c.y, c.z);
			if (s.getRenderType() == BlockRenderType.ENTITYBLOCK_ANIMATED) {
				// cofres y camas se dibujan como objeto: se giran a mano (la cama, entera, desde la cabecera)
				float angle;
				if (b instanceof BedBlock) {
					if (s.get(Properties.BED_PART) != BedPart.HEAD) {
						m.pop();
						continue;
					}
					Direction feet = s.get(Properties.HORIZONTAL_FACING).getOpposite();
					angle = (float) MathHelper.atan2(-feet.getOffsetX(), -feet.getOffsetZ());
				} else {
					Direction f = s.contains(Properties.HORIZONTAL_FACING) ? s.get(Properties.HORIZONTAL_FACING) : Direction.SOUTH;
					angle = (180.0F - f.asRotation()) * MathHelper.RADIANS_PER_DEGREE;
				}
				m.translate(0.5, 0, 0.5);
				m.multiply(RotationAxis.POSITIVE_Y.rotation(angle));
				m.translate(-0.5, 0, -0.5);
			}
			client.getBlockRenderManager().renderBlockAsEntity(s, m, consumers, l, OverlayTexture.DEFAULT_UV);
			VoxelMachine machine = g.machine(i);
			if (machine != null && machine.kind == VoxelMachine.Kind.CAMPFIRE) {
				for (int k = 0; k < 4; k++) {
					ItemStack item = machine.inventory.getStack(k);
					if (item.isEmpty()) {
						continue;
					}
					m.push();
					m.translate(0.3 + (k % 2) * 0.4, 0.44, 0.3 + (k / 2) * 0.4);
					m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(90.0F));
					m.scale(0.375F, 0.375F, 0.375F);
					client.getItemRenderer().renderItem(item, ModelTransformationMode.FIXED, l, OverlayTexture.DEFAULT_UV, m, consumers, world, seed + i * 4 + k);
					m.pop();
				}
			}
			m.pop();
		}
	}

	// ------------------------------------------------------------------ vista previa

	private static void preview(WorldRenderContext ctx) {
		MinecraftClient client = MinecraftClient.getInstance();
		PlayerEntity player = client.player;
		if (player == null || ctx.consumers() == null || ctx.matrixStack() == null
				|| !(client.crosshairTarget instanceof EntityHitResult eh) || !(eh.getEntity() instanceof VoxelHost host)) {
			return;
		}
		float tickDelta = ctx.tickCounter().getTickDelta(true);
		VoxelGrid g = host.grid();
		VoxelGrid.Hit hit = VoxelBuild.aim(host, player, tickDelta);
		if (hit == null) {
			return;
		}
		ItemStack stack = player.getStackInHand(Hand.MAIN_HAND);
		if (VoxelBuild.blockOf(stack) == null) {
			stack = player.getStackInHand(Hand.OFF_HAND);
		}
		Entity e = host.self();
		Vec3d cam = ctx.camera().getPos();
		MatrixStack m = ctx.matrixStack();
		m.push();
		m.translate(MathHelper.lerp(tickDelta, e.prevX, e.getX()) - cam.x, MathHelper.lerp(tickDelta, e.prevY, e.getY()) - cam.y,
				MathHelper.lerp(tickDelta, e.prevZ, e.getZ()) - cam.z);
		m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-MathHelper.lerpAngleDegrees(tickDelta, e.prevYaw, e.getYaw())));
		boolean usable = hit.index() >= 0 && !player.isSneaking() && VoxelBuild.usable(g, hit.index(), stack);
		if (VoxelBuild.blockOf(stack) != null && !usable) {
			VoxelBuild.Placement plan = VoxelBuild.plan(g, hit, stack, VoxelBuild.lookLocal(host, player, tickDelta));
			VertexConsumer vc = ctx.consumers().getBuffer(RenderLayer.getEntityTranslucent(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE));
			int light = LightmapTextureManager.MAX_LIGHT_COORDINATE;
			if (plan != null) {
				for (int k = 0; k < plan.cells().length; k++) {
					BlockState s = plan.states()[k];
					Vec3d c = g.corner(plan.cells()[k]);
					var sprite = client.getBlockRenderManager().getModels().getModelParticleSprite(s);
					var shape = s.getOutlineShape(EmptyBlockView.INSTANCE, BlockPos.ORIGIN);
					for (Box b : shape.getBoundingBoxes()) {
						Cubes.box(m.peek(), vc, b.offset(c).expand(0.005), sprite, TrainRenderers.GOOD, light);
					}
				}
			} else {
				int t = g.target(hit);
				if (t >= 0) {
					var sprite = client.getBlockRenderManager().getModels().getModelParticleSprite(VoxelBuild.blockOf(stack).getDefaultState());
					Cubes.box(m.peek(), vc, g.cell(t).expand(0.005), sprite, TrainRenderers.BAD, light);
				}
			}
		} else if (hit.index() >= 0) {
			// el contorno del bloque apuntado, como en el mundo
			VertexConsumer lines = ctx.consumers().getBuffer(RenderLayer.getLines());
			Vec3d c = g.corner(hit.index());
			for (Box b : g.get(hit.index()).getOutlineShape(EmptyBlockView.INSTANCE, BlockPos.ORIGIN).getBoundingBoxes()) {
				WorldRenderer.drawBox(m, lines, b.offset(c).expand(0.002), 0.0F, 0.0F, 0.0F, 0.4F);
			}
		}
		m.pop();
	}
}
