package com.evolvedwarfare.client.render.musket;

import com.evolvedwarfare.EvolvedWarfare;
import net.minecraft.client.model.ModelData;
import net.minecraft.client.model.ModelPartBuilder;
import net.minecraft.client.model.ModelPartData;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.render.entity.model.EntityModelLayer;
import net.minecraft.util.Identifier;

/**
 * El mosquete en 3D (generado por tools/ew_musket.py: no lo edites a mano). Coordenadas en píxeles del objeto, con
 * Y hacia arriba, la boca del cañón hacia -Z y la llave a la derecha.
 */
public final class MusketModel {
	public static final EntityModelLayer LAYER = new EntityModelLayer(EvolvedWarfare.id("musket"), "main");
	public static final Identifier TEXTURE = EvolvedWarfare.id("textures/entity/musket.png");

	private MusketModel() {
	}

	public static TexturedModelData getTexturedModelData() {
		ModelData data = new ModelData();
		ModelPartData root = data.getRoot();
		root.addChild("body", ModelPartBuilder.create()
				.uv(0, 24).cuboid(7.2F, 9.0F, -8.0F, 1.6F, 1.6F, 22.0F)
				.uv(0, 56).cuboid(7.6F, 9.4F, -8.1F, 0.8F, 0.8F, 0.2F)
				.uv(0, 48).cuboid(7.85F, 10.55F, -7.2F, 0.3F, 0.35F, 0.6F)
				.uv(0, 0).cuboid(7.05F, 7.95F, -4.5F, 1.9F, 1.5F, 17.0F)
				.uv(0, 48).cuboid(7.0F, 7.9F, -5.0F, 2.0F, 1.7F, 0.6F)
				.uv(0, 24).cuboid(6.95F, 7.85F, -1.0F, 2.1F, 2.85F, 0.35F)
				.uv(0, 24).cuboid(6.95F, 7.85F, 4.0F, 2.1F, 2.85F, 0.35F)
				.uv(0, 24).cuboid(6.95F, 7.85F, 9.0F, 2.1F, 2.85F, 0.35F)
				.uv(0, 0).cuboid(6.9F, 7.3F, 12.5F, 2.2F, 2.6F, 5.0F)
				.uv(0, 24).cuboid(7.4F, 9.8F, 14.0F, 1.2F, 0.4F, 2.0F)
				.uv(0, 24).cuboid(9.1F, 8.2F, 12.6F, 0.2F, 1.4F, 4.2F)
				.uv(0, 48).cuboid(9.0F, 9.4F, 13.2F, 0.6F, 0.3F, 1.0F)
				.uv(0, 48).cuboid(6.75F, 8.0F, 13.0F, 0.2F, 1.0F, 3.5F)
				.uv(0, 48).cuboid(7.8F, 6.7F, 13.8F, 0.4F, 0.3F, 3.2F)
				.uv(0, 48).cuboid(7.8F, 6.7F, 13.8F, 0.4F, 0.7F, 0.3F)
				.uv(0, 48).cuboid(7.8F, 6.7F, 16.7F, 0.4F, 0.7F, 0.3F)
				.uv(0, 24).cuboid(7.85F, 6.9F, 15.0F, 0.3F, 0.6F, 0.3F)
				, ModelTransform.of(0F, 0F, 0F, 0.0F, 0.0F, 0.0F));
		root.addChild("stock", ModelPartBuilder.create()
				.uv(0, 0).cuboid(-1.1F, -2.6F, 0.0F, 2.2F, 2.6F, 4.0F)
				.uv(0, 0).cuboid(-1.2F, -4.2F, 3.5F, 2.4F, 4.2F, 3.5F)
				.uv(0, 48).cuboid(-1.25F, -4.25F, 7.0F, 2.5F, 4.3F, 0.3F)
				, ModelTransform.of(8.0F, 9.7F, 17.0F, 0.174533F, 0.0F, 0.0F));
		root.addChild("ramrod", ModelPartBuilder.create()
				.uv(0, 24).cuboid(7.8F, 7.45F, -7.0F, 0.4F, 0.4F, 19.0F)
				.uv(0, 48).cuboid(7.75F, 7.4F, -7.3F, 0.5F, 0.5F, 0.4F)
				, ModelTransform.of(0F, 0F, 0F, 0.0F, 0.0F, 0.0F));
		root.addChild("hammer", ModelPartBuilder.create()
				.uv(0, 24).cuboid(-0.2F, 0.0F, -0.3F, 0.4F, 1.6F, 0.6F)
				.uv(0, 24).cuboid(-0.25F, 1.3F, -1.2F, 0.5F, 0.5F, 1.2F)
				.uv(0, 56).cuboid(-0.2F, 0.9F, -1.25F, 0.4F, 0.4F, 0.5F)
				, ModelTransform.of(9.3F, 9.3F, 15.6F, 0.0F, 0.0F, 0.0F));
		root.addChild("frizzen", ModelPartBuilder.create()
				.uv(0, 24).cuboid(-0.2F, 0.0F, 0.0F, 0.4F, 1.4F, 0.4F)
				.uv(0, 24).cuboid(-0.25F, -0.05F, 0.0F, 0.5F, 0.15F, 1.0F)
				, ModelTransform.of(9.25F, 9.7F, 13.4F, 0.0F, 0.0F, 0.0F));
		root.addChild("ball", ModelPartBuilder.create()
				.uv(32, 56).cuboid(7.55F, 9.35F, -8.9F, 0.9F, 0.9F, 0.9F)
				, ModelTransform.of(0F, 0F, 0F, 0.0F, 0.0F, 0.0F));
		return TexturedModelData.of(data, 64, 64);
	}
}
