package com.evolvedwarfare.client.render;

import com.evolvedwarfare.siege.SiegeEngineEntity;
import com.evolvedwarfare.siege.SiegeKind;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.PillarBlock;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.BlockRenderManager;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;

/**
 * Las máquinas de asedio, montadas con bloques del juego escalados (troncos, tablones, hierro, cuerda de lana, cadenas):
 * así se ven con las texturas del paquete de recursos que uses. El brazo de la catapulta y del trebuchet, la cuerda de
 * la balista y el tronco del ariete se mueven al disparar o golpear; la balista apunta hacia donde mira quien la maneja.
 */
public class SiegeEngineRenderer extends EntityRenderer<SiegeEngineEntity> {
	private static final BlockState LOG_X = Blocks.OAK_LOG.getDefaultState().with(PillarBlock.AXIS, Direction.Axis.X);
	private static final BlockState LOG_Y = Blocks.OAK_LOG.getDefaultState().with(PillarBlock.AXIS, Direction.Axis.Y);
	private static final BlockState LOG_Z = Blocks.OAK_LOG.getDefaultState().with(PillarBlock.AXIS, Direction.Axis.Z);
	private static final BlockState DARK_X = Blocks.DARK_OAK_LOG.getDefaultState().with(PillarBlock.AXIS, Direction.Axis.X);
	private static final BlockState DARK_Y = Blocks.DARK_OAK_LOG.getDefaultState().with(PillarBlock.AXIS, Direction.Axis.Y);
	private static final BlockState DARK_Z = Blocks.DARK_OAK_LOG.getDefaultState().with(PillarBlock.AXIS, Direction.Axis.Z);
	private static final BlockState BEAM_Y = Blocks.STRIPPED_SPRUCE_LOG.getDefaultState().with(PillarBlock.AXIS, Direction.Axis.Y);
	private static final BlockState BEAM_Z = Blocks.STRIPPED_SPRUCE_LOG.getDefaultState().with(PillarBlock.AXIS, Direction.Axis.Z);
	private static final BlockState PLANKS = Blocks.SPRUCE_PLANKS.getDefaultState();
	private static final BlockState ROOF = Blocks.DARK_OAK_PLANKS.getDefaultState();
	private static final BlockState IRON = Blocks.IRON_BLOCK.getDefaultState();
	private static final BlockState ROPE = Blocks.BROWN_WOOL.getDefaultState();
	private static final BlockState STRING = Blocks.WHITE_WOOL.getDefaultState();
	private static final BlockState STONE = Blocks.COBBLESTONE.getDefaultState();
	private static final BlockState WEIGHT = Blocks.STONE_BRICKS.getDefaultState();
	private static final BlockState CUSHION = Blocks.HAY_BLOCK.getDefaultState();
	private static final BlockState CHAIN = Blocks.CHAIN.getDefaultState();

	private final BlockRenderManager blocks;

	public SiegeEngineRenderer(EntityRendererFactory.Context context, float shadow) {
		super(context);
		this.blocks = context.getBlockRenderManager();
		this.shadowRadius = shadow;
	}

	@Override
	public Identifier getTexture(SiegeEngineEntity entity) {
		return PlayerScreenHandler.BLOCK_ATLAS_TEXTURE;
	}

	@Override
	public void render(SiegeEngineEntity engine, float yaw, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertices, int light) {
		matrices.push();
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-yaw));
		float hurt = engine.getHurtTicks() - tickDelta;
		if (hurt > 0.0F) {
			matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(MathHelper.sin(hurt) * hurt * 0.4F));
		}
		Part part = new Part(matrices, vertices, light, OverlayTexture.getUv(0.0F, hurt > 0.0F));
		switch (engine.getKind()) {
			case CATAPULT -> catapult(part, engine, tickDelta);
			case BALLISTA -> ballista(part, engine, tickDelta);
			case TREBUCHET -> trebuchet(part, engine, tickDelta);
			case RAM -> ram(part, engine, tickDelta);
		}
		matrices.pop();
		super.render(engine, yaw, tickDelta, matrices, vertices, light);
	}

	/** Dibuja cajas con bloques escalados. */
	private final class Part {
		final MatrixStack m;
		final VertexConsumerProvider v;
		final int light;
		final int overlay;

		Part(MatrixStack m, VertexConsumerProvider v, int light, int overlay) {
			this.m = m;
			this.v = v;
			this.light = light;
			this.overlay = overlay;
		}

		void box(BlockState state, double x0, double y0, double z0, double x1, double y1, double z1) {
			m.push();
			m.translate(x0, y0, z0);
			m.scale((float) (x1 - x0), (float) (y1 - y0), (float) (z1 - z0));
			blocks.renderBlockAsEntity(state, m, v, light, overlay);
			m.pop();
		}

		/** Caja centrada en x, z. */
		void centered(BlockState state, double cx, double y0, double cz, double halfX, double height, double halfZ) {
			box(state, cx - halfX, y0, cz - halfZ, cx + halfX, y0 + height, cz + halfZ);
		}
	}

	/** Cuánto ha pasado desde el último disparo (0..1), y si está lista. */
	private static float elapsed(SiegeEngineEntity engine, float tickDelta) {
		int total = engine.getReloadTotal();
		int reload = engine.getReload();
		if (reload <= 0) {
			return 1.0F;
		}
		return MathHelper.clamp((total - reload + tickDelta) / total, 0.0F, 1.0F);
	}

	private static float armAngle(SiegeEngineEntity engine, float tickDelta, float cocked, float fired, int swingTicks) {
		int total = engine.getReloadTotal();
		int reload = engine.getReload();
		if (reload <= 0) {
			return cocked;
		}
		float ticks = total - reload + tickDelta;
		if (ticks < swingTicks) {
			return MathHelper.lerp(ticks / swingTicks, cocked, fired);
		}
		return MathHelper.lerp((ticks - swingTicks) / (total - swingTicks), fired, cocked);
	}

	private static void wheels(Part p, double x, double z, double size) {
		for (double side : new double[] {-1.0, 1.0}) {
			for (double end : new double[] {-1.0, 1.0}) {
				double cx = side * x;
				double cz = end * z;
				p.box(LOG_X, cx - 0.1, 0.0, cz - size * 0.5, cx + 0.1, size, cz + size * 0.5);
			}
			p.box(DARK_X, -x, size * 0.5 - 0.05, side * z - 0.05, x, size * 0.5 + 0.05, side * z + 0.05);
		}
	}

	// ------------------------------------------------------------------ catapulta

	private void catapult(Part p, SiegeEngineEntity engine, float tickDelta) {
		wheels(p, 0.8, 0.8, 0.62);
		// bastidor
		p.box(LOG_Z, -0.7, 0.25, -1.1, -0.45, 0.5, 1.1);
		p.box(LOG_Z, 0.45, 0.25, -1.1, 0.7, 0.5, 1.1);
		p.box(LOG_X, -0.7, 0.25, -1.1, 0.7, 0.5, -0.85);
		p.box(LOG_X, -0.7, 0.25, 0.85, 0.7, 0.5, 1.1);
		// tope del brazo
		p.box(LOG_Y, -0.62, 0.5, 0.3, -0.45, 1.75, 0.5);
		p.box(LOG_Y, 0.45, 0.5, 0.3, 0.62, 1.75, 0.5);
		p.box(LOG_X, -0.62, 1.55, 0.3, 0.62, 1.75, 0.5);
		p.box(CUSHION, -0.3, 1.45, 0.2, 0.3, 1.8, 0.3);
		p.box(LOG_Z, -0.55, 0.5, -0.1, -0.45, 1.6, 0.35);
		// madeja de cuerda (la torsión) y su eje
		p.box(ROPE, -0.45, 0.42, -0.47, 0.45, 0.68, -0.23);
		p.box(IRON, -0.72, 0.5, -0.4, 0.72, 0.6, -0.3);
		// torno de atrás
		p.box(DARK_X, -0.45, 0.5, -1.05, 0.45, 0.68, -0.88);
		// brazo
		float angle = armAngle(engine, tickDelta, -62.0F, 32.0F, 3);
		p.m.push();
		p.m.translate(0.0, 0.55, -0.35);
		p.m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(angle));
		p.box(BEAM_Y, -0.1, -0.1, -0.1, 0.1, 1.8, 0.1);
		p.box(PLANKS, -0.3, 1.75, -0.3, 0.3, 1.87, 0.3);
		p.box(PLANKS, -0.3, 1.87, -0.3, 0.3, 2.0, -0.24);
		p.box(PLANKS, -0.3, 1.87, 0.24, 0.3, 2.0, 0.3);
		p.box(PLANKS, -0.3, 1.87, -0.24, -0.24, 2.0, 0.24);
		p.box(PLANKS, 0.24, 1.87, -0.24, 0.3, 2.0, 0.24);
		if (engine.getReload() <= 0) {
			p.box(STONE, -0.2, 1.87, -0.2, 0.2, 2.2, 0.2);
		}
		p.m.pop();
	}

	// ------------------------------------------------------------------ balista

	private void ballista(Part p, SiegeEngineEntity engine, float tickDelta) {
		p.box(LOG_X, -0.8, 0.0, -0.1, 0.8, 0.2, 0.1);
		p.box(LOG_Z, -0.1, 0.0, -0.8, 0.1, 0.2, 0.8);
		p.box(LOG_Y, -0.12, 0.2, -0.12, 0.12, 0.95, 0.12);
		p.box(PLANKS, -0.28, 0.95, -0.28, 0.28, 1.05, 0.28);
		// la parte de arriba gira y se inclina hacia donde apunta quien la maneja
		float relYaw = 0.0F;
		float elevation = 0.0F;
		Entity rider = engine.getFirstPassenger();
		if (rider != null) {
			float riderYaw = rider.getYaw(tickDelta);
			relYaw = MathHelper.clamp(MathHelper.wrapDegrees(riderYaw - MathHelper.lerp(tickDelta, engine.prevYaw, engine.getYaw())),
					-engine.getKind().yawArc, engine.getKind().yawArc);
			elevation = MathHelper.clamp(-rider.getPitch(tickDelta), engine.getKind().minElevation, engine.getKind().maxElevation);
		}
		p.m.push();
		p.m.translate(0.0, 1.05, 0.0);
		p.m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-relYaw));
		p.m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-elevation));
		// caja y arco
		p.box(DARK_Z, -0.12, 0.0, -0.95, 0.12, 0.22, 1.0);
		p.box(ROPE, -0.32, -0.12, 0.7, -0.16, 0.34, 0.96);
		p.box(ROPE, 0.16, -0.12, 0.7, 0.32, 0.34, 0.96);
		p.box(PLANKS, -1.0, 0.05, 0.78, -0.12, 0.17, 0.9);
		p.box(PLANKS, 0.12, 0.05, 0.78, 1.0, 0.17, 0.9);
		p.box(IRON, -1.06, 0.03, 0.76, -0.96, 0.19, 0.92);
		p.box(IRON, 0.96, 0.03, 0.76, 1.06, 0.19, 0.92);
		p.box(DARK_X, -0.32, -0.02, -0.95, 0.32, 0.14, -0.8);
		// la cuerda: tensa (atrás) cuando está cargada, suelta (delante) justo después de disparar
		float ready = elapsed(engine, tickDelta);
		float nutZ = (float) MathHelper.lerp(ready, 0.6, -0.55);
		for (double side : new double[] {-1.0, 1.0}) {
			double dx = 1.0 * side;
			double dz = 0.84 - nutZ;
			double length = Math.sqrt(dx * dx + dz * dz);
			float angle = (float) Math.atan2(dx, dz);
			p.m.push();
			p.m.translate(0.0, 0.11, nutZ);
			p.m.multiply(RotationAxis.POSITIVE_Y.rotation(angle));
			p.box(STRING, -0.015, -0.015, 0.0, 0.015, 0.015, length);
			p.m.pop();
		}
		if (engine.getReload() <= 0) {
			p.box(BEAM_Z, -0.045, 0.22, nutZ, 0.045, 0.31, nutZ + 1.35);
			p.box(IRON, -0.06, 0.2, nutZ + 1.35, 0.06, 0.33, nutZ + 1.55);
			p.box(STRING, -0.08, 0.24, nutZ, 0.08, 0.29, nutZ + 0.18);
		}
		p.m.pop();
	}

	// ------------------------------------------------------------------ trebuchet

	private void trebuchet(Part p, SiegeEngineEntity engine, float tickDelta) {
		wheels(p, 1.1, 1.4, 0.8);
		p.box(LOG_Z, -1.0, 0.3, -2.0, -0.75, 0.55, 2.0);
		p.box(LOG_Z, 0.75, 0.3, -2.0, 1.0, 0.55, 2.0);
		for (double z : new double[] {-1.9, 0.0, 1.9}) {
			p.box(LOG_X, -1.0, 0.3, z - 0.12, 1.0, 0.55, z + 0.12);
		}
		// caballetes en A
		double lean = Math.toDegrees(Math.atan2(1.2, 2.75));
		double post = Math.sqrt(1.2 * 1.2 + 2.75 * 2.75);
		for (double x : new double[] {-0.85, 0.85}) {
			for (double z : new double[] {-1.2, 1.2}) {
				p.m.push();
				p.m.translate(x, 0.55, z);
				p.m.multiply(RotationAxis.POSITIVE_X.rotationDegrees((float) (z < 0.0 ? lean : -lean)));
				p.box(DARK_Y, -0.1, 0.0, -0.1, 0.1, post + 0.15, 0.1);
				p.m.pop();
			}
			p.box(DARK_Z, x - 0.09, 1.7, -0.75, x + 0.09, 1.86, 0.75);
		}
		p.box(IRON, -0.95, 3.2, -0.1, 0.95, 3.4, 0.1);
		// el brazo: el lado largo lanza y el corto lleva el contrapeso
		float angle = armAngle(engine, tickDelta, -140.0F, 40.0F, 8);
		p.m.push();
		p.m.translate(0.0, 3.3, 0.0);
		p.m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(angle));
		p.box(BEAM_Y, -0.13, -1.0, -0.13, 0.13, 3.6, 0.13);
		p.box(IRON, -0.16, 3.45, -0.16, 0.16, 3.62, 0.16);
		if (engine.getReload() <= 0) {
			p.box(STONE, -0.22, 3.62, -0.22, 0.22, 4.05, 0.22);
		}
		p.m.pop();
		// el contrapeso cuelga siempre derecho
		double rad = Math.toRadians(angle);
		double endY = 3.3 - Math.cos(rad);
		double endZ = -Math.sin(rad);
		p.box(CHAIN, -0.08, endY - 0.35, endZ - 0.08, 0.08, endY, endZ + 0.08);
		p.box(WEIGHT, -0.5, endY - 1.2, endZ - 0.5, 0.5, endY - 0.35, endZ + 0.5);
		p.box(IRON, -0.52, endY - 0.45, endZ - 0.52, 0.52, endY - 0.35, endZ + 0.52);
	}

	// ------------------------------------------------------------------ ariete

	private void ram(Part p, SiegeEngineEntity engine, float tickDelta) {
		wheels(p, 1.05, 1.1, 0.6);
		p.box(LOG_Z, -0.95, 0.3, -1.6, -0.75, 0.5, 1.6);
		p.box(LOG_Z, 0.75, 0.3, -1.6, 0.95, 0.5, 1.6);
		p.box(LOG_X, -0.95, 0.3, -1.6, 0.95, 0.5, -1.4);
		p.box(LOG_X, -0.95, 0.3, 1.4, 0.95, 0.5, 1.6);
		for (double x : new double[] {-0.85, 0.85}) {
			for (double z : new double[] {-1.5, 1.5}) {
				p.centered(LOG_Y, x, 0.5, z, 0.085, 1.15, 0.085);
			}
			p.box(LOG_Z, x - 0.085, 1.55, -1.6, x + 0.085, 1.7, 1.6);
		}
		// tejado a dos aguas (protege a los de dentro)
		double run = 1.05;
		double rise = 0.6;
		float slope = (float) Math.toDegrees(Math.atan2(rise, run));
		double slant = Math.sqrt(run * run + rise * rise);
		p.m.push();
		p.m.translate(-run, 1.62, -1.75);
		p.m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(slope));
		p.box(ROOF, 0.0, 0.0, 0.0, slant, 0.08, 3.5);
		p.m.pop();
		p.m.push();
		p.m.translate(run, 1.62, -1.75);
		p.m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(180.0F - slope));
		p.box(ROOF, 0.0, -0.08, 0.0, slant, 0.0, 3.5);
		p.m.pop();
		p.box(DARK_Z, -0.1, 2.12, -1.8, 0.1, 2.3, 1.8);
		// el tronco con cabeza de hierro, colgado de cadenas: se lanza adelante al golpear
		float strike = engine.getStrikeTicks() - tickDelta;
		double swing = strike > 0.0F ? 0.55 * Math.sqrt(strike / SiegeEngineEntity.STRIKE_TICKS) : 0.0;
		for (double z : new double[] {-0.9, 0.9}) {
			p.box(CHAIN, -0.07, 1.2, z + swing - 0.07, 0.07, 2.15, z + swing + 0.07);
		}
		p.box(DARK_Z, -0.25, 0.75, -1.9 + swing, 0.25, 1.25, 1.9 + swing);
		p.box(IRON, -0.32, 0.68, 1.9 + swing, 0.32, 1.32, 2.4 + swing);
		p.box(IRON, -0.27, 0.73, 1.0 + swing, 0.27, 1.27, 1.12 + swing);
	}

	public static SiegeEngineRenderer forKind(EntityRendererFactory.Context context, SiegeKind kind) {
		float shadow = switch (kind) {
			case CATAPULT -> 1.1F;
			case BALLISTA -> 0.8F;
			case TREBUCHET -> 1.6F;
			case RAM -> 1.2F;
		};
		return new SiegeEngineRenderer(context, shadow);
	}
}
