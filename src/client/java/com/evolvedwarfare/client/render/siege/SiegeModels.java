package com.evolvedwarfare.client.render.siege;

import com.evolvedwarfare.EvolvedWarfare;
import net.minecraft.client.model.ModelData;
import net.minecraft.client.model.ModelPartBuilder;
import net.minecraft.client.model.ModelPartData;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.render.entity.model.EntityModelLayer;
import net.minecraft.util.Identifier;

/**
 * Los modelos 3D de las máquinas de asedio (generado por tools/ew_siege_models.py: no lo edites a mano). En píxeles
 * desde los pies de la máquina, Y hacia arriba y el frente hacia +Z.
 */
@SuppressWarnings("unused")
public final class SiegeModels {
	public static final Identifier TEXTURE = EvolvedWarfare.id("textures/entity/siege_engines.png");
	public static final EntityModelLayer CATAPULT = new EntityModelLayer(EvolvedWarfare.id("catapult"), "main");
	public static final EntityModelLayer BALLISTA = new EntityModelLayer(EvolvedWarfare.id("ballista"), "main");
	public static final EntityModelLayer TREBUCHET = new EntityModelLayer(EvolvedWarfare.id("trebuchet"), "main");
	public static final EntityModelLayer RAM = new EntityModelLayer(EvolvedWarfare.id("ram"), "main");

	private SiegeModels() {
	}

	public static TexturedModelData catapult() {
		ModelData data = new ModelData();
		ModelPartData root = data.getRoot();
		ModelPartData root_frame = root.addChild("frame", ModelPartBuilder.create()
				.uv(0, 0).cuboid(-16F, 6F, -24F, 5F, 5F, 48F)
				.uv(0, 0).cuboid(11F, 6F, -24F, 5F, 5F, 48F)
				.uv(0, 0).cuboid(-30F, 6F, -24F, 46F, 5F, 5F)
				.uv(0, 0).cuboid(-16F, 6F, -7F, 32F, 5F, 6F)
				.uv(0, 0).cuboid(-16F, 6F, 19F, 32F, 5F, 5F)
				.uv(256, 320).cuboid(-20F, 6F, -15F, 40F, 2F, 2F)
				.uv(256, 320).cuboid(-20F, 6F, 13F, 40F, 2F, 2F)
				.uv(0, 0).cuboid(-13F, 11F, 6F, 4F, 16F, 4F)
				.uv(0, 0).cuboid(9F, 11F, 6F, 4F, 16F, 4F)
				.uv(0, 0).cuboid(-13F, 23F, 6F, 26F, 4F, 4F)
				.uv(384, 480).cuboid(-6F, 20F, 4F, 12F, 8F, 2F)
				.uv(0, 0).cuboid(-12F, 11F, -8F, 3F, 8F, 8F)
				.uv(0, 0).cuboid(9F, 11F, -8F, 3F, 8F, 8F)
				.uv(256, 416).cuboid(-9F, 9F, -7F, 18F, 8F, 6F)
				.uv(256, 320).cuboid(-12.5F, 11.5F, -4.5F, 25F, 2F, 2F)
				.uv(0, 0).cuboid(-12F, 11F, -19F, 3F, 12F, 4F)
				.uv(0, 0).cuboid(9F, 11F, -19F, 3F, 12F, 4F)
				.uv(0, 0).cuboid(-9F, 18F, -18.5F, 18F, 4F, 3F)
				.uv(256, 320).cuboid(-13.5F, 19F, -17.5F, 1.5F, 2F, 1F)
				.uv(256, 320).cuboid(-14.5F, 17F, -17.5F, 1F, 6F, 1F)
				.uv(256, 320).cuboid(12F, 19F, -17.5F, 1.5F, 2F, 1F)
				.uv(256, 320).cuboid(13.5F, 17F, -17.5F, 1F, 6F, 1F)
				.uv(0, 160).cuboid(-30F, 11F, -28F, 10F, 2F, 8F)
				.uv(0, 0).cuboid(-29.5F, 0F, -23.5F, 3F, 6F, 3F),
				ModelTransform.of(0F, 0F, 0F, 0F, 0F, 0F));
		ModelPartData root_frame_beam1 = root_frame.addChild("beam1", ModelPartBuilder.create()
				.uv(0, 0).cuboid(-1.5F, 0F, -1.5F, 3F, 17.0734F, 3F),
				ModelTransform.of(-14.5F, 11F, 20F, -0.6624F, 0F, -0.263F));
		ModelPartData root_frame_beam2 = root_frame.addChild("beam2", ModelPartBuilder.create()
				.uv(0, 0).cuboid(-1.5F, 0F, -1.5F, 3F, 17.0734F, 3F),
				ModelTransform.of(14.5F, 11F, 20F, -0.6624F, 0F, 0.263F));
		ModelPartData root_frame_wheel_fl = root_frame.addChild("wheel_fl", ModelPartBuilder.create()
				.uv(256, 320).cuboid(-2.1F, -2F, -2F, 4.2F, 4F, 4F)
				.uv(0, 0).cuboid(-1.8F, -5.576F, -0.8F, 3.6F, 11.152F, 1.6F)
				.uv(0, 0).cuboid(-1.8F, -0.8F, -5.576F, 3.6F, 1.6F, 11.152F),
				ModelTransform.of(-18F, 7F, 14F, 0F, 0F, 0F));
		ModelPartData root_frame_wheel_fl_rim0 = root_frame_wheel_fl.addChild("rim0", ModelPartBuilder.create()
				.uv(0, 160).cuboid(-1.5F, -6.8F, -3.4619F, 3F, 13.6F, 6.9238F),
				ModelTransform.of(0F, 0F, 0F, 0F, 0F, 0F));
		ModelPartData root_frame_wheel_fl_rim1 = root_frame_wheel_fl.addChild("rim1", ModelPartBuilder.create()
				.uv(0, 160).cuboid(-1.5F, -6.8F, -3.4619F, 3F, 13.6F, 6.9238F),
				ModelTransform.of(0F, 0F, 0F, 1.0472F, 0F, 0F));
		ModelPartData root_frame_wheel_fl_rim2 = root_frame_wheel_fl.addChild("rim2", ModelPartBuilder.create()
				.uv(0, 160).cuboid(-1.5F, -6.8F, -3.4619F, 3F, 13.6F, 6.9238F),
				ModelTransform.of(0F, 0F, 0F, 2.0944F, 0F, 0F));
		ModelPartData root_frame_wheel_fr = root_frame.addChild("wheel_fr", ModelPartBuilder.create()
				.uv(256, 320).cuboid(-2.1F, -2F, -2F, 4.2F, 4F, 4F)
				.uv(0, 0).cuboid(-1.8F, -5.576F, -0.8F, 3.6F, 11.152F, 1.6F)
				.uv(0, 0).cuboid(-1.8F, -0.8F, -5.576F, 3.6F, 1.6F, 11.152F),
				ModelTransform.of(18F, 7F, 14F, 0F, 0F, 0F));
		ModelPartData root_frame_wheel_fr_rim0 = root_frame_wheel_fr.addChild("rim0", ModelPartBuilder.create()
				.uv(0, 160).cuboid(-1.5F, -6.8F, -3.4619F, 3F, 13.6F, 6.9238F),
				ModelTransform.of(0F, 0F, 0F, 0F, 0F, 0F));
		ModelPartData root_frame_wheel_fr_rim1 = root_frame_wheel_fr.addChild("rim1", ModelPartBuilder.create()
				.uv(0, 160).cuboid(-1.5F, -6.8F, -3.4619F, 3F, 13.6F, 6.9238F),
				ModelTransform.of(0F, 0F, 0F, 1.0472F, 0F, 0F));
		ModelPartData root_frame_wheel_fr_rim2 = root_frame_wheel_fr.addChild("rim2", ModelPartBuilder.create()
				.uv(0, 160).cuboid(-1.5F, -6.8F, -3.4619F, 3F, 13.6F, 6.9238F),
				ModelTransform.of(0F, 0F, 0F, 2.0944F, 0F, 0F));
		ModelPartData root_frame_wheel_bl = root_frame.addChild("wheel_bl", ModelPartBuilder.create()
				.uv(256, 320).cuboid(-2.1F, -2F, -2F, 4.2F, 4F, 4F)
				.uv(0, 0).cuboid(-1.8F, -5.576F, -0.8F, 3.6F, 11.152F, 1.6F)
				.uv(0, 0).cuboid(-1.8F, -0.8F, -5.576F, 3.6F, 1.6F, 11.152F),
				ModelTransform.of(-18F, 7F, -14F, 0F, 0F, 0F));
		ModelPartData root_frame_wheel_bl_rim0 = root_frame_wheel_bl.addChild("rim0", ModelPartBuilder.create()
				.uv(0, 160).cuboid(-1.5F, -6.8F, -3.4619F, 3F, 13.6F, 6.9238F),
				ModelTransform.of(0F, 0F, 0F, 0F, 0F, 0F));
		ModelPartData root_frame_wheel_bl_rim1 = root_frame_wheel_bl.addChild("rim1", ModelPartBuilder.create()
				.uv(0, 160).cuboid(-1.5F, -6.8F, -3.4619F, 3F, 13.6F, 6.9238F),
				ModelTransform.of(0F, 0F, 0F, 1.0472F, 0F, 0F));
		ModelPartData root_frame_wheel_bl_rim2 = root_frame_wheel_bl.addChild("rim2", ModelPartBuilder.create()
				.uv(0, 160).cuboid(-1.5F, -6.8F, -3.4619F, 3F, 13.6F, 6.9238F),
				ModelTransform.of(0F, 0F, 0F, 2.0944F, 0F, 0F));
		ModelPartData root_frame_wheel_br = root_frame.addChild("wheel_br", ModelPartBuilder.create()
				.uv(256, 320).cuboid(-2.1F, -2F, -2F, 4.2F, 4F, 4F)
				.uv(0, 0).cuboid(-1.8F, -5.576F, -0.8F, 3.6F, 11.152F, 1.6F)
				.uv(0, 0).cuboid(-1.8F, -0.8F, -5.576F, 3.6F, 1.6F, 11.152F),
				ModelTransform.of(18F, 7F, -14F, 0F, 0F, 0F));
		ModelPartData root_frame_wheel_br_rim0 = root_frame_wheel_br.addChild("rim0", ModelPartBuilder.create()
				.uv(0, 160).cuboid(-1.5F, -6.8F, -3.4619F, 3F, 13.6F, 6.9238F),
				ModelTransform.of(0F, 0F, 0F, 0F, 0F, 0F));
		ModelPartData root_frame_wheel_br_rim1 = root_frame_wheel_br.addChild("rim1", ModelPartBuilder.create()
				.uv(0, 160).cuboid(-1.5F, -6.8F, -3.4619F, 3F, 13.6F, 6.9238F),
				ModelTransform.of(0F, 0F, 0F, 1.0472F, 0F, 0F));
		ModelPartData root_frame_wheel_br_rim2 = root_frame_wheel_br.addChild("rim2", ModelPartBuilder.create()
				.uv(0, 160).cuboid(-1.5F, -6.8F, -3.4619F, 3F, 13.6F, 6.9238F),
				ModelTransform.of(0F, 0F, 0F, 2.0944F, 0F, 0F));
		ModelPartData root_arm = root.addChild("arm", ModelPartBuilder.create()
				.uv(0, 160).cuboid(-2F, 0F, -2F, 4F, 41F, 4F)
				.uv(256, 320).cuboid(-2.5F, 12F, -2.5F, 5F, 1.5F, 5F)
				.uv(256, 320).cuboid(-2.5F, 30F, -2.5F, 5F, 1.5F, 5F)
				.uv(0, 160).cuboid(-6F, 39F, -6F, 12F, 2F, 12F)
				.uv(0, 160).cuboid(-6F, 41F, -6F, 12F, 3F, 1.5F)
				.uv(0, 160).cuboid(-6F, 41F, 4.5F, 12F, 3F, 1.5F)
				.uv(0, 160).cuboid(-6F, 41F, -4.5F, 1.5F, 3F, 9F)
				.uv(0, 160).cuboid(4.5F, 41F, -4.5F, 1.5F, 3F, 9F),
				ModelTransform.of(0F, 12.5F, -3.5F, 0F, 0F, 0F));
		ModelPartData root_arm_stone = root_arm.addChild("stone", ModelPartBuilder.create()
				.uv(384, 416).cuboid(-4F, 41F, -4F, 8F, 7F, 8F),
				ModelTransform.of(0F, 0F, 0F, 0F, 0F, 0F));
		return TexturedModelData.of(data, 512, 512);
	}

	public static TexturedModelData ballista() {
		ModelData data = new ModelData();
		ModelPartData root = data.getRoot();
		ModelPartData root_base = root.addChild("base", ModelPartBuilder.create()
				.uv(0, 0).cuboid(-14F, 0F, -2F, 28F, 3F, 4F)
				.uv(0, 0).cuboid(-2F, 0F, -14F, 4F, 3F, 28F)
				.uv(0, 0).cuboid(-2.5F, 3F, -2.5F, 5F, 12F, 5F)
				.uv(0, 160).cuboid(-5F, 14F, -5F, 10F, 2F, 10F)
				.uv(0, 160).cuboid(-4F, 8F, -23F, 8F, 2F, 7F)
				.uv(0, 0).cuboid(-3.5F, 0F, -22.5F, 2F, 8F, 2F)
				.uv(0, 0).cuboid(1.5F, 0F, -22.5F, 2F, 8F, 2F)
				.uv(0, 0).cuboid(-3.5F, 0F, -18.5F, 2F, 8F, 2F)
				.uv(0, 0).cuboid(1.5F, 0F, -18.5F, 2F, 8F, 2F),
				ModelTransform.of(0F, 0F, 0F, 0F, 0F, 0F));
		ModelPartData root_base_beam3 = root_base.addChild("beam3", ModelPartBuilder.create()
				.uv(0, 0).cuboid(-1.25F, 0F, -1.25F, 2.5F, 12.8062F, 2.5F),
				ModelTransform.of(-12F, 3F, 0F, 0F, 0F, -0.8961F));
		ModelPartData root_base_beam4 = root_base.addChild("beam4", ModelPartBuilder.create()
				.uv(0, 0).cuboid(-1.25F, 0F, -1.25F, 2.5F, 12.8062F, 2.5F),
				ModelTransform.of(12F, 3F, 0F, 0F, 0F, 0.8961F));
		ModelPartData root_base_beam5 = root_base.addChild("beam5", ModelPartBuilder.create()
				.uv(0, 0).cuboid(-1.25F, 0F, -1.25F, 2.5F, 12.8062F, 2.5F),
				ModelTransform.of(0F, 3F, 12F, -0.8961F, 0F, -0F));
		ModelPartData root_base_beam6 = root_base.addChild("beam6", ModelPartBuilder.create()
				.uv(0, 0).cuboid(-1.25F, 0F, -1.25F, 2.5F, 12.8062F, 2.5F),
				ModelTransform.of(0F, 3F, -12F, 0.8961F, 0F, -0F));
		ModelPartData root_turret = root.addChild("turret", ModelPartBuilder.create()
				.uv(0, 0).cuboid(-2F, 0F, -18F, 4F, 4F, 40F)
				.uv(0, 160).cuboid(-9F, 9F, 13F, 18F, 2F, 5F)
				.uv(0, 160).cuboid(-9F, -6F, 13F, 18F, 2F, 5F)
				.uv(0, 160).cuboid(-9F, -4F, 13F, 2F, 13F, 5F)
				.uv(0, 160).cuboid(7F, -4F, 13F, 2F, 13F, 5F)
				.uv(256, 416).cuboid(-6.5F, -5F, 13.5F, 3F, 15F, 4F)
				.uv(256, 416).cuboid(3.5F, -5F, 13.5F, 3F, 15F, 4F)
				.uv(256, 320).cuboid(-7F, 11F, 13F, 5F, 1F, 5F)
				.uv(256, 320).cuboid(2F, 11F, 13F, 5F, 1F, 5F)
				.uv(256, 320).cuboid(-7F, -7F, 13F, 5F, 1F, 5F)
				.uv(256, 320).cuboid(2F, -7F, 13F, 5F, 1F, 5F)
				.uv(0, 0).cuboid(-5F, -1F, -17F, 10F, 3F, 3F)
				.uv(256, 320).cuboid(-7F, -1.5F, -16.5F, 2F, 4F, 2F)
				.uv(256, 320).cuboid(5F, -1.5F, -16.5F, 2F, 4F, 2F)
				.uv(256, 320).cuboid(-1.5F, 4F, 18F, 3F, 1F, 4F),
				ModelTransform.of(0F, 16F, 0F, 0F, 0F, 0F));
		ModelPartData root_turret_arm_l = root_turret.addChild("arm_l", ModelPartBuilder.create()
				.uv(0, 160).cuboid(-16F, -1F, -1F, 16F, 2F, 2F)
				.uv(256, 320).cuboid(-17F, -1.5F, -1.5F, 2F, 3F, 3F),
				ModelTransform.of(-5F, 2F, 15.5F, 0F, 0F, 0F));
		ModelPartData root_turret_arm_r = root_turret.addChild("arm_r", ModelPartBuilder.create()
				.uv(0, 160).cuboid(0F, -1F, -1F, 16F, 2F, 2F)
				.uv(256, 320).cuboid(15F, -1.5F, -1.5F, 2F, 3F, 3F),
				ModelTransform.of(5F, 2F, 15.5F, 0F, 0F, 0F));
		ModelPartData root_turret_nut = root_turret.addChild("nut", ModelPartBuilder.create()
				.uv(256, 320).cuboid(-2.5F, 4F, -1.5F, 5F, 2F, 3F),
				ModelTransform.of(0F, 0F, 0F, 0F, 0F, 0F));
		ModelPartData root_turret_bolt = root_turret.addChild("bolt", ModelPartBuilder.create()
				.uv(0, 160).cuboid(-0.6F, 5F, 0F, 1.2F, 1.2F, 24F)
				.uv(256, 320).cuboid(-1.2F, 4.6F, 24F, 2.4F, 2F, 4F)
				.uv(256, 480).cuboid(-1.8F, 5.1F, 0.5F, 3.6F, 0.4F, 4F),
				ModelTransform.of(0F, 0F, 0F, 0F, 0F, 0F));
		ModelPartData root_cord = root.addChild("cord", ModelPartBuilder.create()
				.uv(256, 480).cuboid(-0.4F, -0.4F, 0F, 0.8F, 0.8F, 1F),
				ModelTransform.of(0F, 0F, 0F, 0F, 0F, 0F));
		return TexturedModelData.of(data, 512, 512);
	}

	public static TexturedModelData trebuchet() {
		ModelData data = new ModelData();
		ModelPartData root = data.getRoot();
		ModelPartData root_frame = root.addChild("frame", ModelPartBuilder.create()
				.uv(0, 0).cuboid(-30F, 6F, -40F, 7F, 8F, 80F)
				.uv(0, 0).cuboid(23F, 6F, -40F, 7F, 8F, 80F)
				.uv(0, 0).cuboid(-30F, 6F, -40F, 60F, 8F, 6F)
				.uv(0, 0).cuboid(-30F, 6F, -3F, 60F, 8F, 6F)
				.uv(0, 0).cuboid(-30F, 6F, 34F, 60F, 8F, 6F)
				.uv(256, 320).cuboid(-35F, 7F, -29F, 70F, 2F, 2F)
				.uv(256, 320).cuboid(-35F, 7F, 27F, 70F, 2F, 2F)
				.uv(0, 0).cuboid(-30F, 90F, -5F, 7F, 10F, 10F)
				.uv(0, 0).cuboid(23F, 90F, -5F, 7F, 10F, 10F)
				.uv(256, 320).cuboid(-31F, 94F, -2F, 62F, 4F, 4F)
				.uv(0, 0).cuboid(-29.5F, 50F, -17F, 6F, 6F, 34F)
				.uv(0, 0).cuboid(23.5F, 50F, -17F, 6F, 6F, 34F)
				.uv(0, 0).cuboid(-17F, 14F, -30F, 3F, 20F, 7F)
				.uv(0, 0).cuboid(14F, 14F, -30F, 3F, 20F, 7F)
				.uv(0, 0).cuboid(-14F, 26F, -30F, 28F, 7F, 7F)
				.uv(256, 320).cuboid(-15F, 25F, -27.5F, 1F, 9F, 2F)
				.uv(256, 320).cuboid(-15F, 28.5F, -31F, 1F, 2F, 9F)
				.uv(256, 320).cuboid(14F, 25F, -27.5F, 1F, 9F, 2F)
				.uv(256, 320).cuboid(14F, 28.5F, -31F, 1F, 2F, 9F)
				.uv(0, 160).cuboid(-6F, 14F, -40F, 12F, 2F, 7F)
				.uv(0, 160).cuboid(-10F, 14F, -46F, 20F, 1F, 8F),
				ModelTransform.of(0F, 0F, 0F, 0F, 0F, 0F));
		ModelPartData root_frame_beam7 = root_frame.addChild("beam7", ModelPartBuilder.create()
				.uv(0, 0).cuboid(-3F, 0F, -3F, 6F, 83.9345F, 6F),
				ModelTransform.of(-26.5F, 14F, -32F, 0.3783F, 0F, -0F));
		ModelPartData root_frame_beam8 = root_frame.addChild("beam8", ModelPartBuilder.create()
				.uv(0, 0).cuboid(-3F, 0F, -3F, 6F, 83.9345F, 6F),
				ModelTransform.of(-26.5F, 14F, 32F, -0.3783F, 0F, -0F));
		ModelPartData root_frame_beam9 = root_frame.addChild("beam9", ModelPartBuilder.create()
				.uv(0, 0).cuboid(-2F, 0F, -2F, 4F, 44.4185F, 4F),
				ModelTransform.of(-26.5F, 14F, -39F, 0.5443F, 0F, -0F));
		ModelPartData root_frame_beam10 = root_frame.addChild("beam10", ModelPartBuilder.create()
				.uv(0, 0).cuboid(-2F, 0F, -2F, 4F, 44.4185F, 4F),
				ModelTransform.of(-26.5F, 14F, 39F, -0.5443F, 0F, -0F));
		ModelPartData root_frame_beam11 = root_frame.addChild("beam11", ModelPartBuilder.create()
				.uv(0, 0).cuboid(-3F, 0F, -3F, 6F, 83.9345F, 6F),
				ModelTransform.of(26.5F, 14F, -32F, 0.3783F, 0F, -0F));
		ModelPartData root_frame_beam12 = root_frame.addChild("beam12", ModelPartBuilder.create()
				.uv(0, 0).cuboid(-3F, 0F, -3F, 6F, 83.9345F, 6F),
				ModelTransform.of(26.5F, 14F, 32F, -0.3783F, 0F, -0F));
		ModelPartData root_frame_beam13 = root_frame.addChild("beam13", ModelPartBuilder.create()
				.uv(0, 0).cuboid(-2F, 0F, -2F, 4F, 44.4185F, 4F),
				ModelTransform.of(26.5F, 14F, -39F, 0.5443F, 0F, -0F));
		ModelPartData root_frame_beam14 = root_frame.addChild("beam14", ModelPartBuilder.create()
				.uv(0, 0).cuboid(-2F, 0F, -2F, 4F, 44.4185F, 4F),
				ModelTransform.of(26.5F, 14F, 39F, -0.5443F, 0F, -0F));
		ModelPartData root_frame_wheel_fl = root_frame.addChild("wheel_fl", ModelPartBuilder.create()
				.uv(256, 320).cuboid(-2.6F, -2F, -2F, 5.2F, 4F, 4F)
				.uv(0, 0).cuboid(-2.3F, -6.56F, -0.8F, 4.6F, 13.12F, 1.6F)
				.uv(0, 0).cuboid(-2.3F, -0.8F, -6.56F, 4.6F, 1.6F, 13.12F),
				ModelTransform.of(-32.5F, 8F, 28F, 0F, 0F, 0F));
		ModelPartData root_frame_wheel_fl_rim0 = root_frame_wheel_fl.addChild("rim0", ModelPartBuilder.create()
				.uv(0, 160).cuboid(-2F, -8F, -4.0728F, 4F, 16F, 8.1457F),
				ModelTransform.of(0F, 0F, 0F, 0F, 0F, 0F));
		ModelPartData root_frame_wheel_fl_rim1 = root_frame_wheel_fl.addChild("rim1", ModelPartBuilder.create()
				.uv(0, 160).cuboid(-2F, -8F, -4.0728F, 4F, 16F, 8.1457F),
				ModelTransform.of(0F, 0F, 0F, 1.0472F, 0F, 0F));
		ModelPartData root_frame_wheel_fl_rim2 = root_frame_wheel_fl.addChild("rim2", ModelPartBuilder.create()
				.uv(0, 160).cuboid(-2F, -8F, -4.0728F, 4F, 16F, 8.1457F),
				ModelTransform.of(0F, 0F, 0F, 2.0944F, 0F, 0F));
		ModelPartData root_frame_wheel_fr = root_frame.addChild("wheel_fr", ModelPartBuilder.create()
				.uv(256, 320).cuboid(-2.6F, -2F, -2F, 5.2F, 4F, 4F)
				.uv(0, 0).cuboid(-2.3F, -6.56F, -0.8F, 4.6F, 13.12F, 1.6F)
				.uv(0, 0).cuboid(-2.3F, -0.8F, -6.56F, 4.6F, 1.6F, 13.12F),
				ModelTransform.of(32.5F, 8F, 28F, 0F, 0F, 0F));
		ModelPartData root_frame_wheel_fr_rim0 = root_frame_wheel_fr.addChild("rim0", ModelPartBuilder.create()
				.uv(0, 160).cuboid(-2F, -8F, -4.0728F, 4F, 16F, 8.1457F),
				ModelTransform.of(0F, 0F, 0F, 0F, 0F, 0F));
		ModelPartData root_frame_wheel_fr_rim1 = root_frame_wheel_fr.addChild("rim1", ModelPartBuilder.create()
				.uv(0, 160).cuboid(-2F, -8F, -4.0728F, 4F, 16F, 8.1457F),
				ModelTransform.of(0F, 0F, 0F, 1.0472F, 0F, 0F));
		ModelPartData root_frame_wheel_fr_rim2 = root_frame_wheel_fr.addChild("rim2", ModelPartBuilder.create()
				.uv(0, 160).cuboid(-2F, -8F, -4.0728F, 4F, 16F, 8.1457F),
				ModelTransform.of(0F, 0F, 0F, 2.0944F, 0F, 0F));
		ModelPartData root_frame_wheel_bl = root_frame.addChild("wheel_bl", ModelPartBuilder.create()
				.uv(256, 320).cuboid(-2.6F, -2F, -2F, 5.2F, 4F, 4F)
				.uv(0, 0).cuboid(-2.3F, -6.56F, -0.8F, 4.6F, 13.12F, 1.6F)
				.uv(0, 0).cuboid(-2.3F, -0.8F, -6.56F, 4.6F, 1.6F, 13.12F),
				ModelTransform.of(-32.5F, 8F, -28F, 0F, 0F, 0F));
		ModelPartData root_frame_wheel_bl_rim0 = root_frame_wheel_bl.addChild("rim0", ModelPartBuilder.create()
				.uv(0, 160).cuboid(-2F, -8F, -4.0728F, 4F, 16F, 8.1457F),
				ModelTransform.of(0F, 0F, 0F, 0F, 0F, 0F));
		ModelPartData root_frame_wheel_bl_rim1 = root_frame_wheel_bl.addChild("rim1", ModelPartBuilder.create()
				.uv(0, 160).cuboid(-2F, -8F, -4.0728F, 4F, 16F, 8.1457F),
				ModelTransform.of(0F, 0F, 0F, 1.0472F, 0F, 0F));
		ModelPartData root_frame_wheel_bl_rim2 = root_frame_wheel_bl.addChild("rim2", ModelPartBuilder.create()
				.uv(0, 160).cuboid(-2F, -8F, -4.0728F, 4F, 16F, 8.1457F),
				ModelTransform.of(0F, 0F, 0F, 2.0944F, 0F, 0F));
		ModelPartData root_frame_wheel_br = root_frame.addChild("wheel_br", ModelPartBuilder.create()
				.uv(256, 320).cuboid(-2.6F, -2F, -2F, 5.2F, 4F, 4F)
				.uv(0, 0).cuboid(-2.3F, -6.56F, -0.8F, 4.6F, 13.12F, 1.6F)
				.uv(0, 0).cuboid(-2.3F, -0.8F, -6.56F, 4.6F, 1.6F, 13.12F),
				ModelTransform.of(32.5F, 8F, -28F, 0F, 0F, 0F));
		ModelPartData root_frame_wheel_br_rim0 = root_frame_wheel_br.addChild("rim0", ModelPartBuilder.create()
				.uv(0, 160).cuboid(-2F, -8F, -4.0728F, 4F, 16F, 8.1457F),
				ModelTransform.of(0F, 0F, 0F, 0F, 0F, 0F));
		ModelPartData root_frame_wheel_br_rim1 = root_frame_wheel_br.addChild("rim1", ModelPartBuilder.create()
				.uv(0, 160).cuboid(-2F, -8F, -4.0728F, 4F, 16F, 8.1457F),
				ModelTransform.of(0F, 0F, 0F, 1.0472F, 0F, 0F));
		ModelPartData root_frame_wheel_br_rim2 = root_frame_wheel_br.addChild("rim2", ModelPartBuilder.create()
				.uv(0, 160).cuboid(-2F, -8F, -4.0728F, 4F, 16F, 8.1457F),
				ModelTransform.of(0F, 0F, 0F, 2.0944F, 0F, 0F));
		ModelPartData root_arm = root.addChild("arm", ModelPartBuilder.create()
				.uv(0, 0).cuboid(-3F, -26F, -3F, 6F, 130F, 6F)
				.uv(256, 320).cuboid(-3.5F, -26F, -3.5F, 7F, 3F, 7F)
				.uv(256, 320).cuboid(-3.5F, -3F, -3.5F, 7F, 6F, 7F)
				.uv(256, 320).cuboid(-3.5F, 100F, -3.5F, 7F, 4F, 7F)
				.uv(256, 320).cuboid(-3.5F, 40F, -3.5F, 7F, 2F, 7F),
				ModelTransform.of(0F, 96F, 0F, 0F, 0F, 0F));
		ModelPartData root_arm_weight = root_arm.addChild("weight", ModelPartBuilder.create()
				.uv(256, 320).cuboid(-1.5F, -12F, -1.5F, 3F, 12F, 3F)
				.uv(0, 0).cuboid(-14F, -40F, -14F, 28F, 28F, 28F)
				.uv(256, 320).cuboid(-14.5F, -31F, -14.5F, 29F, 2F, 29F)
				.uv(256, 320).cuboid(-14.5F, -20F, -14.5F, 29F, 2F, 29F)
				.uv(384, 416).cuboid(-12F, -12.5F, -12F, 24F, 1F, 24F),
				ModelTransform.of(0F, -26F, 0F, 0F, 0F, 0F));
		ModelPartData root_arm_sling = root_arm.addChild("sling", ModelPartBuilder.create()
				.uv(256, 480).cuboid(-2.5F, -18F, -0.5F, 1F, 18F, 1F)
				.uv(256, 480).cuboid(1.5F, -18F, -0.5F, 1F, 18F, 1F)
				.uv(0, 320).cuboid(-3.5F, -21F, -3.5F, 7F, 3F, 7F),
				ModelTransform.of(0F, 104F, 0F, 0F, 0F, 0F));
		ModelPartData root_arm_sling_stone = root_arm_sling.addChild("stone", ModelPartBuilder.create()
				.uv(384, 416).cuboid(-2.5F, -24F, -2.5F, 5F, 5F, 5F),
				ModelTransform.of(0F, 0F, 0F, 0F, 0F, 0F));
		return TexturedModelData.of(data, 512, 512);
	}

	public static TexturedModelData ram() {
		ModelData data = new ModelData();
		ModelPartData root = data.getRoot();
		ModelPartData root_frame = root.addChild("frame", ModelPartBuilder.create()
				.uv(0, 0).cuboid(-16F, 6F, -24F, 4F, 6F, 48F)
				.uv(0, 0).cuboid(12F, 6F, -24F, 4F, 6F, 48F)
				.uv(0, 0).cuboid(-16F, 6F, -24F, 32F, 6F, 4F)
				.uv(0, 0).cuboid(-16F, 6F, 20F, 32F, 6F, 4F)
				.uv(256, 320).cuboid(-20F, 6F, -17F, 40F, 2F, 2F)
				.uv(256, 320).cuboid(-20F, 6F, 15F, 40F, 2F, 2F)
				.uv(0, 0).cuboid(-16F, 32F, -24F, 4F, 4F, 48F)
				.uv(0, 0).cuboid(12F, 32F, -24F, 4F, 4F, 48F)
				.uv(0, 0).cuboid(-2F, 43F, -26F, 4F, 4F, 52F)
				.uv(0, 0).cuboid(-2F, 36F, -24F, 4F, 7F, 3F)
				.uv(0, 0).cuboid(-2F, 36F, 21F, 4F, 7F, 3F)
				.uv(0, 160).cuboid(-13F, 12F, -18F, 8F, 2F, 8F)
				.uv(0, 0).cuboid(-16F, 12F, -24F, 4F, 20F, 4F)
				.uv(0, 0).cuboid(-16F, 12F, -2F, 4F, 20F, 4F)
				.uv(0, 0).cuboid(-16F, 12F, 20F, 4F, 20F, 4F)
				.uv(0, 0).cuboid(12F, 12F, -24F, 4F, 20F, 4F)
				.uv(0, 0).cuboid(12F, 12F, -2F, 4F, 20F, 4F)
				.uv(0, 0).cuboid(12F, 12F, 20F, 4F, 20F, 4F),
				ModelTransform.of(0F, 0F, 0F, 0F, 0F, 0F));
		ModelPartData root_frame_roof_l = root_frame.addChild("roof_l", ModelPartBuilder.create()
				.uv(0, 320).cuboid(0F, 0F, 0F, 20F, 1.5F, 52F),
				ModelTransform.of(-17.5F, 35F, -26F, 0F, 0F, 0.5317F));
		ModelPartData root_frame_roof_r = root_frame.addChild("roof_r", ModelPartBuilder.create()
				.uv(0, 320).cuboid(0F, -1.5F, 0F, 20F, 1.5F, 52F),
				ModelTransform.of(17.5F, 35F, -26F, 0F, 0F, 2.6099F));
		ModelPartData root_frame_wheel_fl = root_frame.addChild("wheel_fl", ModelPartBuilder.create()
				.uv(256, 320).cuboid(-2.1F, -2F, -2F, 4.2F, 4F, 4F)
				.uv(0, 0).cuboid(-1.8F, -5.576F, -0.8F, 3.6F, 11.152F, 1.6F)
				.uv(0, 0).cuboid(-1.8F, -0.8F, -5.576F, 3.6F, 1.6F, 11.152F),
				ModelTransform.of(-18F, 7F, 16F, 0F, 0F, 0F));
		ModelPartData root_frame_wheel_fl_rim0 = root_frame_wheel_fl.addChild("rim0", ModelPartBuilder.create()
				.uv(0, 160).cuboid(-1.5F, -6.8F, -3.4619F, 3F, 13.6F, 6.9238F),
				ModelTransform.of(0F, 0F, 0F, 0F, 0F, 0F));
		ModelPartData root_frame_wheel_fl_rim1 = root_frame_wheel_fl.addChild("rim1", ModelPartBuilder.create()
				.uv(0, 160).cuboid(-1.5F, -6.8F, -3.4619F, 3F, 13.6F, 6.9238F),
				ModelTransform.of(0F, 0F, 0F, 1.0472F, 0F, 0F));
		ModelPartData root_frame_wheel_fl_rim2 = root_frame_wheel_fl.addChild("rim2", ModelPartBuilder.create()
				.uv(0, 160).cuboid(-1.5F, -6.8F, -3.4619F, 3F, 13.6F, 6.9238F),
				ModelTransform.of(0F, 0F, 0F, 2.0944F, 0F, 0F));
		ModelPartData root_frame_wheel_fr = root_frame.addChild("wheel_fr", ModelPartBuilder.create()
				.uv(256, 320).cuboid(-2.1F, -2F, -2F, 4.2F, 4F, 4F)
				.uv(0, 0).cuboid(-1.8F, -5.576F, -0.8F, 3.6F, 11.152F, 1.6F)
				.uv(0, 0).cuboid(-1.8F, -0.8F, -5.576F, 3.6F, 1.6F, 11.152F),
				ModelTransform.of(18F, 7F, 16F, 0F, 0F, 0F));
		ModelPartData root_frame_wheel_fr_rim0 = root_frame_wheel_fr.addChild("rim0", ModelPartBuilder.create()
				.uv(0, 160).cuboid(-1.5F, -6.8F, -3.4619F, 3F, 13.6F, 6.9238F),
				ModelTransform.of(0F, 0F, 0F, 0F, 0F, 0F));
		ModelPartData root_frame_wheel_fr_rim1 = root_frame_wheel_fr.addChild("rim1", ModelPartBuilder.create()
				.uv(0, 160).cuboid(-1.5F, -6.8F, -3.4619F, 3F, 13.6F, 6.9238F),
				ModelTransform.of(0F, 0F, 0F, 1.0472F, 0F, 0F));
		ModelPartData root_frame_wheel_fr_rim2 = root_frame_wheel_fr.addChild("rim2", ModelPartBuilder.create()
				.uv(0, 160).cuboid(-1.5F, -6.8F, -3.4619F, 3F, 13.6F, 6.9238F),
				ModelTransform.of(0F, 0F, 0F, 2.0944F, 0F, 0F));
		ModelPartData root_frame_wheel_bl = root_frame.addChild("wheel_bl", ModelPartBuilder.create()
				.uv(256, 320).cuboid(-2.1F, -2F, -2F, 4.2F, 4F, 4F)
				.uv(0, 0).cuboid(-1.8F, -5.576F, -0.8F, 3.6F, 11.152F, 1.6F)
				.uv(0, 0).cuboid(-1.8F, -0.8F, -5.576F, 3.6F, 1.6F, 11.152F),
				ModelTransform.of(-18F, 7F, -16F, 0F, 0F, 0F));
		ModelPartData root_frame_wheel_bl_rim0 = root_frame_wheel_bl.addChild("rim0", ModelPartBuilder.create()
				.uv(0, 160).cuboid(-1.5F, -6.8F, -3.4619F, 3F, 13.6F, 6.9238F),
				ModelTransform.of(0F, 0F, 0F, 0F, 0F, 0F));
		ModelPartData root_frame_wheel_bl_rim1 = root_frame_wheel_bl.addChild("rim1", ModelPartBuilder.create()
				.uv(0, 160).cuboid(-1.5F, -6.8F, -3.4619F, 3F, 13.6F, 6.9238F),
				ModelTransform.of(0F, 0F, 0F, 1.0472F, 0F, 0F));
		ModelPartData root_frame_wheel_bl_rim2 = root_frame_wheel_bl.addChild("rim2", ModelPartBuilder.create()
				.uv(0, 160).cuboid(-1.5F, -6.8F, -3.4619F, 3F, 13.6F, 6.9238F),
				ModelTransform.of(0F, 0F, 0F, 2.0944F, 0F, 0F));
		ModelPartData root_frame_wheel_br = root_frame.addChild("wheel_br", ModelPartBuilder.create()
				.uv(256, 320).cuboid(-2.1F, -2F, -2F, 4.2F, 4F, 4F)
				.uv(0, 0).cuboid(-1.8F, -5.576F, -0.8F, 3.6F, 11.152F, 1.6F)
				.uv(0, 0).cuboid(-1.8F, -0.8F, -5.576F, 3.6F, 1.6F, 11.152F),
				ModelTransform.of(18F, 7F, -16F, 0F, 0F, 0F));
		ModelPartData root_frame_wheel_br_rim0 = root_frame_wheel_br.addChild("rim0", ModelPartBuilder.create()
				.uv(0, 160).cuboid(-1.5F, -6.8F, -3.4619F, 3F, 13.6F, 6.9238F),
				ModelTransform.of(0F, 0F, 0F, 0F, 0F, 0F));
		ModelPartData root_frame_wheel_br_rim1 = root_frame_wheel_br.addChild("rim1", ModelPartBuilder.create()
				.uv(0, 160).cuboid(-1.5F, -6.8F, -3.4619F, 3F, 13.6F, 6.9238F),
				ModelTransform.of(0F, 0F, 0F, 1.0472F, 0F, 0F));
		ModelPartData root_frame_wheel_br_rim2 = root_frame_wheel_br.addChild("rim2", ModelPartBuilder.create()
				.uv(0, 160).cuboid(-1.5F, -6.8F, -3.4619F, 3F, 13.6F, 6.9238F),
				ModelTransform.of(0F, 0F, 0F, 2.0944F, 0F, 0F));
		ModelPartData root_log = root.addChild("log", ModelPartBuilder.create()
				.uv(0, 0).cuboid(-4F, 16F, -20F, 8F, 8F, 40F)
				.uv(256, 320).cuboid(-5F, 15F, 20F, 10F, 10F, 6F)
				.uv(256, 320).cuboid(-3F, 17F, 26F, 6F, 6F, 3F)
				.uv(256, 320).cuboid(-4.5F, 15.5F, -12F, 9F, 9F, 2F)
				.uv(256, 320).cuboid(-4.5F, 15.5F, 8F, 9F, 9F, 2F),
				ModelTransform.of(0F, 0F, 0F, 0F, 0F, 0F));
		ModelPartData root_rope_a = root.addChild("rope_a", ModelPartBuilder.create()
				.uv(256, 416).cuboid(-0.75F, -19F, -0.75F, 1.5F, 19F, 1.5F),
				ModelTransform.of(0F, 43F, -10F, 0F, 0F, 0F));
		ModelPartData root_rope_b = root.addChild("rope_b", ModelPartBuilder.create()
				.uv(256, 416).cuboid(-0.75F, -19F, -0.75F, 1.5F, 19F, 1.5F),
				ModelTransform.of(0F, 43F, 10F, 0F, 0F, 0F));
		return TexturedModelData.of(data, 512, 512);
	}
}
