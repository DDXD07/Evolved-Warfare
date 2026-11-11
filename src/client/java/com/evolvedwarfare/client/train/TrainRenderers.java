package com.evolvedwarfare.client.train;

import com.evolvedwarfare.EvolvedWarfare;
import com.evolvedwarfare.client.hell.BossModel;
import com.evolvedwarfare.train.Building;
import com.evolvedwarfare.train.PieceItem;
import com.evolvedwarfare.train.PlatformEntity;
import com.evolvedwarfare.train.TrackItem;
import com.evolvedwarfare.train.TrainCarEntity;
import com.evolvedwarfare.train.Trains;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreens;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactories;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.model.EntityModelLayer;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;

/**
 * El tren en el cliente: los modelos de la locomotora, el ténder y la plataforma (con sus ruedas girando y lo construido
 * encima), las vías grandes, las pantallas (caldera, mesa de ensamblaje y menú de construcción) y las vistas previas:
 * la pieza con la que se va a construir en la plataforma (verde si cabe, roja si no) y el trozo de vía que se va a poner.
 */
public final class TrainRenderers {
	static final int GOOD = 0x7040FF50, BAD = 0x70FF4040;

	private TrainRenderers() {
	}

	public static void register() {
		TrainCarEntity.ClientAccess.set(() -> MinecraftClient.getInstance().player);
		Trains.openBuildMenu = () -> MinecraftClient.getInstance().setScreen(new BuildScreen());
		car(Trains.LOCOMOTIVE, "steam_locomotive", 2.5F);
		car(Trains.TENDER, "coal_tender", 2.5F);
		car(Trains.PLATFORM, "flatcar", 2.0F);
		BlockEntityRendererFactories.register(Trains.TRACK_ENTITY, ctx -> new TrackRenderer());
		HandledScreens.register(Trains.LOCOMOTIVE_SCREEN, LocomotiveScreen::new);
		HandledScreens.register(Trains.ASSEMBLY_SCREEN, AssemblyScreen::new);
		WorldRenderEvents.AFTER_ENTITIES.register(TrainRenderers::previews);
	}

	private static <T extends TrainCarEntity> void car(net.minecraft.entity.EntityType<T> type, String id, float scale) {
		EntityModelLayer layer = new EntityModelLayer(EvolvedWarfare.id(id), "main");
		EntityModelLayerRegistry.registerModelLayer(layer, () -> BossModel.texturedModelData(id));
		EntityRendererRegistry.register(type, ctx -> new CarRenderer<>(ctx, layer, id, scale));
	}

	/** Las texturas de cada material: el material, el remate, el cristal, la puerta y el tejado. */
	static String tex(Building.Material m, Building.Tex t) {
		return switch (t) {
			case GLASS, NONE -> "block/glass";
			case MAIN -> switch (m) {
				case STRAW -> "block/hay_block_side";
				case WOOD -> "block/oak_planks";
				case STONE -> "block/stone_bricks";
				case METAL -> "block/iron_block";
			};
			case TRIM -> switch (m) {
				case STRAW -> "block/bamboo_block";
				case WOOD -> "block/stripped_spruce_log";
				case STONE -> "block/smooth_stone";
				case METAL -> "block/smooth_basalt";
			};
			case DOOR -> switch (m) {
				case STRAW -> "block/bamboo_planks";
				case WOOD -> "block/spruce_planks";
				case STONE -> "block/dark_oak_planks";
				case METAL -> "block/iron_trapdoor";
			};
			case ROOF -> switch (m) {
				case STRAW -> "block/hay_block_top";
				case WOOD -> "block/spruce_planks";
				case STONE -> "block/deepslate_tiles";
				case METAL -> "block/iron_block";
			};
		};
	}

	/** Dibuja una pieza (en coordenadas de la plataforma); tint -1 = sus colores. */
	static void drawPiece(MatrixStack matrices, VertexConsumer vc, List<Building.Part> parts, Building.Material m, int light, int tint) {
		MatrixStack.Entry e = matrices.peek();
		for (Building.Part part : parts) {
			if (part.tex() == Building.Tex.NONE) {
				// la caja de choque de las barandillas no se ve
				continue;
			}
			int color = tint;
			if (color == -1) {
				color = m == Building.Material.METAL && part.tex() == Building.Tex.MAIN ? 0xFFB8BCC4 : 0xFFFFFFFF;
			}
			Cubes.box(e, vc, part.box(), Cubes.sprite(tex(m, part.tex())), color, light);
		}
	}

	static class CarRenderer<T extends TrainCarEntity> extends EntityRenderer<T> {
		private final ModelPart root;
		private final Identifier texture, glow;
		private final float scale;
		private final List<ModelPart> wheels = new ArrayList<>();

		CarRenderer(EntityRendererFactory.Context ctx, EntityModelLayer layer, String id, float scale) {
			super(ctx);
			this.root = ctx.getPart(layer);
			this.scale = scale;
			this.texture = EvolvedWarfare.id("textures/entity/train/" + id + ".png");
			this.glow = EvolvedWarfare.id("textures/entity/train/" + id + "_glow.png");
			this.shadowRadius = 1.6F;
			// las ruedas (las partes que cuelgan del bastidor y se llaman lead*, drive* o w*)
			try (InputStream in = TrainRenderers.class.getResourceAsStream("/assets/" + EvolvedWarfare.MOD_ID + "/hell/" + id + ".json")) {
				if (in != null) {
					JsonObject def = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
					for (JsonElement el : def.getAsJsonArray("parts")) {
						JsonObject p = el.getAsJsonObject();
						String name = p.get("name").getAsString();
						if (p.has("role") && p.get("role").isJsonObject() && "wheel".equals(p.getAsJsonObject("role").get("role").getAsString())
								&& root.hasChild("frame") && root.getChild("frame").hasChild(name)) {
							wheels.add(root.getChild("frame").getChild(name));
						}
					}
				}
			} catch (Exception ignored) {
				// sin ruedas que giren
			}
		}

		@Override
		public Identifier getTexture(T entity) {
			return texture;
		}

		@Override
		public boolean shouldRender(T entity, net.minecraft.client.render.Frustum frustum, double x, double y, double z) {
			return entity.shouldRender(x, y, z) && frustum.isVisible(entity.getBoundingBox().expand(1.0));
		}

		@Override
		public void render(T car, float yaw, float tickDelta, MatrixStack matrices, VertexConsumerProvider consumers, int light) {
			matrices.push();
			float ry = MathHelper.lerpAngleDegrees(tickDelta, car.prevYaw, car.getYaw());
			float rp = MathHelper.lerp(tickDelta, car.prevPitch, car.getPitch());
			matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-ry));
			matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(rp));
			float angle = MathHelper.lerp(tickDelta, car.lastWheelAngle, car.wheelAngle);
			for (ModelPart w : wheels) {
				w.roll = -angle;
			}
			matrices.push();
			matrices.translate(0.0F, 6.0F / 16.0F * scale, 0.0F);
			matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-90.0F));
			matrices.scale(-scale, -scale, scale);
			root.render(matrices, consumers.getBuffer(RenderLayer.getEntityCutoutNoCull(texture)), light, OverlayTexture.DEFAULT_UV);
			root.render(matrices, consumers.getBuffer(RenderLayer.getEyes(glow)), LightmapTextureManager.MAX_LIGHT_COORDINATE, OverlayTexture.DEFAULT_UV);
			matrices.pop();
			if (car instanceof PlatformEntity flat && !flat.pieces().isEmpty()) {
				VertexConsumer vc = consumers.getBuffer(RenderLayer.getEntityCutoutNoCull(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE));
				for (Building.Placed p : flat.pieces()) {
					drawPiece(matrices, vc, Building.parts(p), p.material, light, -1);
				}
			}
			matrices.pop();
			super.render(car, yaw, tickDelta, matrices, consumers, light);
		}
	}

	// ------------------------------------------------------------------ las vistas previas

	private static void previews(WorldRenderContext ctx) {
		MinecraftClient client = MinecraftClient.getInstance();
		PlayerEntity player = client.player;
		if (player == null || ctx.consumers() == null || ctx.matrixStack() == null || client.crosshairTarget == null) {
			return;
		}
		ItemStack stack = player.getStackInHand(Hand.MAIN_HAND);
		Vec3d cam = ctx.camera().getPos();
		MatrixStack m = ctx.matrixStack();
		float tickDelta = ctx.tickCounter().getTickDelta(true);
		VertexConsumer vc = ctx.consumers().getBuffer(RenderLayer.getEntityTranslucent(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE));
		int light = LightmapTextureManager.MAX_LIGHT_COORDINATE;
		if (stack.getItem() instanceof PieceItem piece && client.crosshairTarget instanceof EntityHitResult eh && eh.getEntity() instanceof PlatformEntity flat) {
			Building.Slot slot = flat.slotFor(piece.kind, eh.getPos(), player.getRotationVec(tickDelta));
			if (slot == null) {
				return;
			}
			boolean ok = flat.canPlace(slot);
			double x = MathHelper.lerp(tickDelta, flat.prevX, flat.getX()), y = MathHelper.lerp(tickDelta, flat.prevY, flat.getY()),
					z = MathHelper.lerp(tickDelta, flat.prevZ, flat.getZ());
			m.push();
			m.translate(x - cam.x, y - cam.y, z - cam.z);
			m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-MathHelper.lerpAngleDegrees(tickDelta, flat.prevYaw, flat.getYaw())));
			m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(MathHelper.lerp(tickDelta, flat.prevPitch, flat.getPitch())));
			List<Building.Part> parts = Building.parts(slot.kind(), slot.level(), slot.a(), slot.b(), slot.rot(), false);
			List<Building.Part> grown = new ArrayList<>();
			for (Building.Part p : parts) {
				grown.add(new Building.Part(p.box().expand(0.01), p.tex(), p.solid()));
			}
			drawPiece(m, vc, grown, piece.material, light, ok ? GOOD : BAD);
			m.pop();
		} else if (stack.getItem() instanceof TrackItem track && client.crosshairTarget instanceof BlockHitResult bh
				&& bh.getType() == HitResult.Type.BLOCK) {
			BlockPos target = new ItemPlacementContext(player, Hand.MAIN_HAND, stack, bh).getBlockPos();
			TrackItem.Plan plan = track.plan(client.world, player, target);
			if (plan == null) {
				return;
			}
			var p = plan.piece();
			m.push();
			m.translate(p.ox() - cam.x, p.y() + 0.01 - cam.y, p.oz() - cam.z);
			TrackRenderer.draw(m, vc, p, light, plan.valid() ? GOOD : BAD);
			m.pop();
		}
	}
}
