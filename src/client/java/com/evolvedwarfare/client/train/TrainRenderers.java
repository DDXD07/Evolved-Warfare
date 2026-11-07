package com.evolvedwarfare.client.train;

import com.evolvedwarfare.EvolvedWarfare;
import com.evolvedwarfare.client.hell.BossModel;
import com.evolvedwarfare.train.Trains;
import net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.gui.screen.ingame.HandledScreens;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MinecartEntityRenderer;
import net.minecraft.client.render.entity.model.EntityModelLayer;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.vehicle.AbstractMinecartEntity;
import net.minecraft.util.Identifier;

/** Los coches del tren: el modelo de cada uno (a lo largo de la vía, como una vagoneta) y la pantalla de la caldera. */
public final class TrainRenderers {
	private TrainRenderers() {
	}

	public static void register() {
		car(Trains.LOCOMOTIVE, "steam_locomotive");
		car(Trains.PASSENGER, "passenger_car");
		car(Trains.FREIGHT, "freight_car");
		car(Trains.TENDER, "coal_tender");
		HandledScreens.register(Trains.LOCOMOTIVE_SCREEN, LocomotiveScreen::new);
	}

	private static <T extends AbstractMinecartEntity> void car(EntityType<T> type, String id) {
		EntityModelLayer layer = new EntityModelLayer(EvolvedWarfare.id(id), "main");
		EntityModelLayerRegistry.registerModelLayer(layer, () -> BossModel.texturedModelData(id));
		Identifier texture = EvolvedWarfare.id("textures/entity/train/" + id + ".png");
		EntityRendererRegistry.register(type, ctx -> new CarRenderer<>(ctx, layer, texture));
	}

	static class CarRenderer<T extends AbstractMinecartEntity> extends MinecartEntityRenderer<T> {
		private final Identifier texture;

		CarRenderer(EntityRendererFactory.Context ctx, EntityModelLayer layer, Identifier texture) {
			super(ctx, layer);
			this.texture = texture;
			this.shadowRadius = 0.6F;
		}

		@Override
		public Identifier getTexture(T entity) {
			return texture;
		}
	}
}
