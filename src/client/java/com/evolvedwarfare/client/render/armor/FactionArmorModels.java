package com.evolvedwarfare.client.render.armor;

import com.evolvedwarfare.armor.FactionArmor;
import net.minecraft.client.model.Dilation;
import net.minecraft.client.model.ModelData;
import net.minecraft.client.model.ModelPartBuilder;
import net.minecraft.client.model.ModelPartData;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.model.TexturedModelData;

/** Generado por tools/ew_armor.py: los modelos 3D de las armaduras de las facciones (no editar a mano). */
public final class FactionArmorModels {
	/** Las partes de cada modelo (las mismas en todos), en el orden del modelo de jugador. */
	public static final String[] PARTS = {"head", "body", "waist", "right_arm", "left_arm", "right_skirt", "left_skirt", "right_leg", "left_leg", "right_boot", "left_boot"};

	private FactionArmorModels() {
	}

	public static TexturedModelData of(FactionArmor armor) {
		return switch (armor) {
			case BANDIT_OUTLAW -> banditOutlaw();
			case BANDIT_HIGHWAYMAN -> banditHighwayman();
			case BANDIT_BRIGAND -> banditBrigand();
			case BANDIT_WARLORD -> banditWarlord();
			case VIKING_HUNTER -> vikingHunter();
			case VIKING_WARRIOR -> vikingWarrior();
			case VIKING_HUSCARL -> vikingHuscarl();
			case VIKING_JARL -> vikingJarl();
			case PIRATE_DECKHAND -> pirateDeckhand();
			case PIRATE_BUCCANEER -> pirateBuccaneer();
			case PIRATE_CORSAIR -> pirateCorsair();
			case PIRATE_CAPTAIN -> pirateCaptain();
		};
	}

	private static TexturedModelData banditOutlaw() {
		ModelData data = new ModelData();
		ModelPartData root = data.getRoot();
		ModelPartData head = root.addChild("head", ModelPartBuilder.create()
				.mirrored(false).uv(0, 0).cuboid(-4F, -8F, -4F, 8F, 8F, 8F, new Dilation(1F))
				.mirrored(false).uv(40, 16).cuboid(-2F, -9.5F, 2F, 4F, 3F, 4F, new Dilation(0.4F))
				.mirrored(false).uv(0, 25).cuboid(-4F, -3F, -4F, 8F, 3F, 1F, new Dilation(1.1F, 0.3F, 1.15F))
				.mirrored(false).uv(26, 25).cuboid(-1F, -5F, 4.6F, 2F, 2F, 1F, new Dilation(0.3F)),
				ModelTransform.pivot(0F, 0F, 0F));
		ModelPartData head_0 = head.addChild("c0", ModelPartBuilder.create()
				.mirrored(false).uv(18, 25).cuboid(-0.5F, 0F, -0.5F, 1F, 3F, 1F, new Dilation(0F)),
				ModelTransform.of(-0.6F, -4F, 5.9F, 0.4363F, 0F, 0.2094F));
		ModelPartData head_1 = head.addChild("c1", ModelPartBuilder.create()
				.mirrored(false).uv(22, 25).cuboid(-0.5F, 0F, -0.5F, 1F, 3F, 1F, new Dilation(0F)),
				ModelTransform.of(0.6F, -4F, 5.9F, 0.3142F, 0F, -0.1745F));
		ModelPartData body = root.addChild("body", ModelPartBuilder.create()
				.mirrored(false).uv(32, 0).cuboid(-4F, 0F, -2F, 8F, 12F, 4F, new Dilation(0.8F))
				.mirrored(false).uv(56, 0).cuboid(-4F, 0F, -2F, 8F, 12F, 4F, new Dilation(1.05F))
				.mirrored(false).uv(56, 16).cuboid(-4F, 9F, -2F, 8F, 1F, 4F, new Dilation(1.2F, 0.15F, 1.2F)),
				ModelTransform.pivot(0F, 0F, 0F));
		ModelPartData waist = root.addChild("waist", ModelPartBuilder.create()
				.mirrored(false).uv(0, 16).cuboid(-4F, 7F, -2F, 8F, 5F, 4F, new Dilation(0.5F)),
				ModelTransform.pivot(0F, 0F, 0F));
		ModelPartData right_arm = root.addChild("right_arm", ModelPartBuilder.create()
				.mirrored(false).uv(96, 0).cuboid(-3F, -2F, -2F, 4F, 7F, 4F, new Dilation(0.8F))
				.mirrored(false).uv(80, 16).cuboid(-3F, 3F, -2F, 4F, 1F, 4F, new Dilation(0.95F, 0.1F, 0.95F)),
				ModelTransform.pivot(-5F, 2F, 0F));
		ModelPartData left_arm = root.addChild("left_arm", ModelPartBuilder.create()
				.mirrored(true).uv(96, 0).cuboid(-1F, -2F, -2F, 4F, 7F, 4F, new Dilation(0.8F))
				.mirrored(true).uv(80, 16).cuboid(-1F, 3F, -2F, 4F, 1F, 4F, new Dilation(0.95F, 0.1F, 0.95F)),
				ModelTransform.pivot(5F, 2F, 0F));
		ModelPartData right_skirt = root.addChild("right_skirt", ModelPartBuilder.create(),
				ModelTransform.pivot(-1.9F, 12F, 0F));
		ModelPartData left_skirt = root.addChild("left_skirt", ModelPartBuilder.create(),
				ModelTransform.pivot(1.9F, 12F, 0F));
		ModelPartData right_leg = root.addChild("right_leg", ModelPartBuilder.create()
				.mirrored(false).uv(80, 0).cuboid(-2F, 0F, -2F, 4F, 12F, 4F, new Dilation(0.5F)),
				ModelTransform.pivot(-1.9F, 12F, 0F));
		ModelPartData left_leg = root.addChild("left_leg", ModelPartBuilder.create()
				.mirrored(true).uv(80, 0).cuboid(-2F, 0F, -2F, 4F, 12F, 4F, new Dilation(0.5F)),
				ModelTransform.pivot(1.9F, 12F, 0F));
		ModelPartData right_boot = root.addChild("right_boot", ModelPartBuilder.create()
				.mirrored(false).uv(24, 16).cuboid(-2F, 7F, -2F, 4F, 5F, 4F, new Dilation(1F))
				.mirrored(false).uv(96, 16).cuboid(-2F, 11F, -2F, 4F, 1F, 4F, new Dilation(1.05F, 1.05F, 1.05F)),
				ModelTransform.pivot(-1.9F, 12F, 0F));
		ModelPartData left_boot = root.addChild("left_boot", ModelPartBuilder.create()
				.mirrored(true).uv(24, 16).cuboid(-2F, 7F, -2F, 4F, 5F, 4F, new Dilation(1F))
				.mirrored(true).uv(96, 16).cuboid(-2F, 11F, -2F, 4F, 1F, 4F, new Dilation(1.05F, 1.05F, 1.05F)),
				ModelTransform.pivot(1.9F, 12F, 0F));
		return TexturedModelData.of(data, 128, 32);
	}

	private static TexturedModelData banditHighwayman() {
		ModelData data = new ModelData();
		ModelPartData root = data.getRoot();
		ModelPartData head = root.addChild("head", ModelPartBuilder.create()
				.mirrored(false).uv(56, 0).cuboid(-4F, -8F, -4F, 8F, 3F, 8F, new Dilation(1F))
				.mirrored(false).uv(88, 0).cuboid(-4F, -5F, -3F, 2F, 5F, 6F, new Dilation(1.1F))
				.mirrored(false).uv(104, 0).cuboid(2F, -5F, -3F, 2F, 5F, 6F, new Dilation(1.1F))
				.mirrored(false).uv(24, 27).cuboid(-4F, -3F, -4F, 8F, 3F, 1F, new Dilation(1.15F, 0.3F, 1.2F)),
				ModelTransform.pivot(0F, 0F, 0F));
		ModelPartData head_0 = head.addChild("c0", ModelPartBuilder.create()
				.mirrored(false).uv(16, 27).cuboid(-0.5F, 0F, -0.5F, 1F, 4F, 1F, new Dilation(0F)),
				ModelTransform.of(3F, -3F, 5.4F, 0.2618F, 0F, -0.2443F));
		ModelPartData body = root.addChild("body", ModelPartBuilder.create()
				.mirrored(false).uv(0, 0).cuboid(-4F, 0F, -2F, 8F, 12F, 4F, new Dilation(1F))
				.mirrored(false).uv(78, 16).cuboid(-4F, -1F, -2F, 8F, 2F, 4F, new Dilation(1.35F, 0.4F, 1.35F))
				.mirrored(false).uv(102, 16).cuboid(-4F, 9F, -2F, 8F, 2F, 4F, new Dilation(1.2F, 0.15F, 1.2F)),
				ModelTransform.pivot(0F, 0F, 0F));
		ModelPartData body_0 = body.addChild("c0", ModelPartBuilder.create()
				.mirrored(false).uv(20, 27).cuboid(0F, 0F, -0.5F, 1F, 4F, 1F, new Dilation(0F)),
				ModelTransform.of(4.9F, 10.5F, -1F, 0F, 0F, -0.2094F));
		ModelPartData waist = root.addChild("waist", ModelPartBuilder.create()
				.mirrored(false).uv(16, 16).cuboid(-4F, 7F, -2F, 8F, 5F, 4F, new Dilation(0.5F)),
				ModelTransform.pivot(0F, 0F, 0F));
		ModelPartData right_arm = root.addChild("right_arm", ModelPartBuilder.create()
				.mirrored(false).uv(24, 0).cuboid(-3F, -2F, -2F, 4F, 12F, 4F, new Dilation(1F))
				.mirrored(false).uv(40, 16).cuboid(-4F, -3F, -3F, 5F, 3F, 6F, new Dilation(0.35F))
				.mirrored(false).uv(62, 16).cuboid(-3F, 6F, -2F, 4F, 3F, 4F, new Dilation(1.15F, 0.1F, 1.15F)),
				ModelTransform.pivot(-5F, 2F, 0F));
		ModelPartData left_arm = root.addChild("left_arm", ModelPartBuilder.create()
				.mirrored(true).uv(24, 0).cuboid(-1F, -2F, -2F, 4F, 12F, 4F, new Dilation(1F))
				.mirrored(true).uv(40, 16).cuboid(-1F, -3F, -3F, 5F, 3F, 6F, new Dilation(0.35F))
				.mirrored(true).uv(62, 16).cuboid(-1F, 6F, -2F, 4F, 3F, 4F, new Dilation(1.15F, 0.1F, 1.15F)),
				ModelTransform.pivot(5F, 2F, 0F));
		ModelPartData right_skirt = root.addChild("right_skirt", ModelPartBuilder.create(),
				ModelTransform.pivot(-1.9F, 12F, 0F));
		ModelPartData left_skirt = root.addChild("left_skirt", ModelPartBuilder.create(),
				ModelTransform.pivot(1.9F, 12F, 0F));
		ModelPartData right_leg = root.addChild("right_leg", ModelPartBuilder.create()
				.mirrored(false).uv(40, 0).cuboid(-2F, 0F, -2F, 4F, 12F, 4F, new Dilation(0.5F))
				.mirrored(false).uv(42, 27).cuboid(-2F, 4F, -2.9F, 4F, 3F, 1F, new Dilation(0F)),
				ModelTransform.pivot(-1.9F, 12F, 0F));
		ModelPartData left_leg = root.addChild("left_leg", ModelPartBuilder.create()
				.mirrored(true).uv(40, 0).cuboid(-2F, 0F, -2F, 4F, 12F, 4F, new Dilation(0.5F))
				.mirrored(true).uv(42, 27).cuboid(-2F, 4F, -2.9F, 4F, 3F, 1F, new Dilation(0F)),
				ModelTransform.pivot(1.9F, 12F, 0F));
		ModelPartData right_boot = root.addChild("right_boot", ModelPartBuilder.create()
				.mirrored(false).uv(0, 16).cuboid(-2F, 5F, -2F, 4F, 7F, 4F, new Dilation(1F))
				.mirrored(false).uv(0, 27).cuboid(-2F, 5F, -2F, 4F, 2F, 4F, new Dilation(1.3F, 0.2F, 1.3F)),
				ModelTransform.pivot(-1.9F, 12F, 0F));
		ModelPartData left_boot = root.addChild("left_boot", ModelPartBuilder.create()
				.mirrored(true).uv(0, 16).cuboid(-2F, 5F, -2F, 4F, 7F, 4F, new Dilation(1F))
				.mirrored(true).uv(0, 27).cuboid(-2F, 5F, -2F, 4F, 2F, 4F, new Dilation(1.3F, 0.2F, 1.3F)),
				ModelTransform.pivot(1.9F, 12F, 0F));
		return TexturedModelData.of(data, 128, 64);
	}

	private static TexturedModelData banditBrigand() {
		ModelData data = new ModelData();
		ModelPartData root = data.getRoot();
		ModelPartData head = root.addChild("head", ModelPartBuilder.create()
				.mirrored(false).uv(24, 16).cuboid(-4F, -8F, -4F, 8F, 4F, 8F, new Dilation(1F))
				.mirrored(false).uv(56, 0).cuboid(-7F, -4.6F, -7F, 14F, 1F, 14F, new Dilation(0F))
				.mirrored(false).uv(56, 16).cuboid(-4F, -4F, -4F, 8F, 4F, 8F, new Dilation(1.08F)),
				ModelTransform.pivot(0F, 0F, 0F));
		ModelPartData body = root.addChild("body", ModelPartBuilder.create()
				.mirrored(false).uv(0, 0).cuboid(-4F, 0F, -2F, 8F, 12F, 4F, new Dilation(1F))
				.mirrored(false).uv(0, 16).cuboid(-4F, 0F, -2F, 8F, 10F, 4F, new Dilation(1.15F))
				.mirrored(false).uv(54, 30).cuboid(-4F, 9F, -2F, 8F, 2F, 4F, new Dilation(1.3F, 0.15F, 1.3F)),
				ModelTransform.pivot(0F, 0F, 0F));
		ModelPartData waist = root.addChild("waist", ModelPartBuilder.create()
				.mirrored(false).uv(104, 16).cuboid(-4F, 7F, -2F, 8F, 5F, 4F, new Dilation(0.5F)),
				ModelTransform.pivot(0F, 0F, 0F));
		ModelPartData right_arm = root.addChild("right_arm", ModelPartBuilder.create()
				.mirrored(false).uv(24, 0).cuboid(-3F, -2F, -2F, 4F, 12F, 4F, new Dilation(1F))
				.mirrored(false).uv(0, 30).cuboid(-4F, -3F, -3F, 5F, 3F, 6F, new Dilation(0.35F))
				.mirrored(false).uv(22, 30).cuboid(-3F, 6F, -2F, 4F, 3F, 4F, new Dilation(1.12F, 0.1F, 1.12F)),
				ModelTransform.pivot(-5F, 2F, 0F));
		ModelPartData left_arm = root.addChild("left_arm", ModelPartBuilder.create()
				.mirrored(true).uv(24, 0).cuboid(-1F, -2F, -2F, 4F, 12F, 4F, new Dilation(1F))
				.mirrored(true).uv(0, 30).cuboid(-1F, -3F, -3F, 5F, 3F, 6F, new Dilation(0.35F))
				.mirrored(true).uv(22, 30).cuboid(-1F, 6F, -2F, 4F, 3F, 4F, new Dilation(1.12F, 0.1F, 1.12F)),
				ModelTransform.pivot(5F, 2F, 0F));
		ModelPartData right_skirt = root.addChild("right_skirt", ModelPartBuilder.create()
				.mirrored(false).uv(38, 30).cuboid(-2F, 0F, -2F, 4F, 3F, 4F, new Dilation(1F)),
				ModelTransform.pivot(-1.9F, 12F, 0F));
		ModelPartData left_skirt = root.addChild("left_skirt", ModelPartBuilder.create()
				.mirrored(true).uv(38, 30).cuboid(-2F, 0F, -2F, 4F, 3F, 4F, new Dilation(1F)),
				ModelTransform.pivot(1.9F, 12F, 0F));
		ModelPartData right_leg = root.addChild("right_leg", ModelPartBuilder.create()
				.mirrored(false).uv(40, 0).cuboid(-2F, 0F, -2F, 4F, 12F, 4F, new Dilation(0.5F)),
				ModelTransform.pivot(-1.9F, 12F, 0F));
		ModelPartData left_leg = root.addChild("left_leg", ModelPartBuilder.create()
				.mirrored(true).uv(40, 0).cuboid(-2F, 0F, -2F, 4F, 12F, 4F, new Dilation(0.5F)),
				ModelTransform.pivot(1.9F, 12F, 0F));
		ModelPartData right_boot = root.addChild("right_boot", ModelPartBuilder.create()
				.mirrored(false).uv(88, 16).cuboid(-2F, 6F, -2F, 4F, 6F, 4F, new Dilation(1F))
				.mirrored(false).uv(78, 30).cuboid(-2F, 10F, -3.3F, 4F, 2F, 1F, new Dilation(0F)),
				ModelTransform.pivot(-1.9F, 12F, 0F));
		ModelPartData left_boot = root.addChild("left_boot", ModelPartBuilder.create()
				.mirrored(true).uv(88, 16).cuboid(-2F, 6F, -2F, 4F, 6F, 4F, new Dilation(1F))
				.mirrored(true).uv(78, 30).cuboid(-2F, 10F, -3.3F, 4F, 2F, 1F, new Dilation(0F)),
				ModelTransform.pivot(1.9F, 12F, 0F));
		return TexturedModelData.of(data, 128, 64);
	}

	private static TexturedModelData banditWarlord() {
		ModelData data = new ModelData();
		ModelPartData root = data.getRoot();
		ModelPartData head = root.addChild("head", ModelPartBuilder.create()
				.mirrored(false).uv(0, 0).cuboid(-4F, -8F, -4F, 8F, 8F, 8F, new Dilation(1.1F))
				.mirrored(false).uv(24, 16).cuboid(-0.5F, -12.2F, -4F, 1F, 3F, 9F, new Dilation(0F)),
				ModelTransform.pivot(0F, 0F, 0F));
		ModelPartData body = root.addChild("body", ModelPartBuilder.create()
				.mirrored(false).uv(32, 0).cuboid(-4F, 0F, -2F, 8F, 12F, 4F, new Dilation(1F))
				.mirrored(false).uv(0, 16).cuboid(-4F, 0F, -2F, 8F, 10F, 4F, new Dilation(1.15F))
				.mirrored(false).uv(16, 30).cuboid(-4F, 9F, -2F, 8F, 2F, 4F, new Dilation(1.3F, 0.15F, 1.3F)),
				ModelTransform.pivot(0F, 0F, 0F));
		ModelPartData body_0 = body.addChild("c0", ModelPartBuilder.create()
				.mirrored(false).uv(88, 0).cuboid(-4F, 0F, 0F, 8F, 14F, 1F, new Dilation(0.1F)),
				ModelTransform.of(0F, 0.5F, 3.2F, 0.1047F, 0F, 0F));
		ModelPartData waist = root.addChild("waist", ModelPartBuilder.create()
				.mirrored(false).uv(66, 16).cuboid(-4F, 7F, -2F, 8F, 5F, 4F, new Dilation(0.5F)),
				ModelTransform.pivot(0F, 0F, 0F));
		ModelPartData right_arm = root.addChild("right_arm", ModelPartBuilder.create()
				.mirrored(false).uv(56, 0).cuboid(-3F, -2F, -2F, 4F, 12F, 4F, new Dilation(1F))
				.mirrored(false).uv(44, 16).cuboid(-4F, -3F, -3F, 5F, 4F, 6F, new Dilation(0.35F))
				.mirrored(false).uv(106, 16).cuboid(-3F, 6F, -2F, 4F, 4F, 4F, new Dilation(1.12F, 0.1F, 1.12F))
				.mirrored(false).uv(62, 30).cuboid(-3F, -5.3F, -1F, 1F, 2F, 1F, new Dilation(0F))
				.mirrored(false).uv(66, 30).cuboid(-1F, -5.3F, -1F, 1F, 2F, 1F, new Dilation(0F)),
				ModelTransform.pivot(-5F, 2F, 0F));
		ModelPartData left_arm = root.addChild("left_arm", ModelPartBuilder.create()
				.mirrored(true).uv(56, 0).cuboid(-1F, -2F, -2F, 4F, 12F, 4F, new Dilation(1F))
				.mirrored(true).uv(44, 16).cuboid(-1F, -3F, -3F, 5F, 4F, 6F, new Dilation(0.35F))
				.mirrored(true).uv(106, 16).cuboid(-1F, 6F, -2F, 4F, 4F, 4F, new Dilation(1.12F, 0.1F, 1.12F))
				.mirrored(true).uv(62, 30).cuboid(2F, -5.3F, -1F, 1F, 2F, 1F, new Dilation(0F))
				.mirrored(true).uv(66, 30).cuboid(0F, -5.3F, -1F, 1F, 2F, 1F, new Dilation(0F)),
				ModelTransform.pivot(5F, 2F, 0F));
		ModelPartData right_skirt = root.addChild("right_skirt", ModelPartBuilder.create()
				.mirrored(false).uv(0, 30).cuboid(-2F, -1F, -2F, 4F, 4F, 4F, new Dilation(1.15F)),
				ModelTransform.pivot(-1.9F, 12F, 0F));
		ModelPartData left_skirt = root.addChild("left_skirt", ModelPartBuilder.create()
				.mirrored(true).uv(0, 30).cuboid(-2F, -1F, -2F, 4F, 4F, 4F, new Dilation(1.15F)),
				ModelTransform.pivot(1.9F, 12F, 0F));
		ModelPartData right_leg = root.addChild("right_leg", ModelPartBuilder.create()
				.mirrored(false).uv(72, 0).cuboid(-2F, 0F, -2F, 4F, 12F, 4F, new Dilation(0.5F))
				.mirrored(false).uv(52, 30).cuboid(-2F, 4F, -3.1F, 4F, 3F, 1F, new Dilation(0F)),
				ModelTransform.pivot(-1.9F, 12F, 0F));
		ModelPartData left_leg = root.addChild("left_leg", ModelPartBuilder.create()
				.mirrored(true).uv(72, 0).cuboid(-2F, 0F, -2F, 4F, 12F, 4F, new Dilation(0.5F))
				.mirrored(true).uv(52, 30).cuboid(-2F, 4F, -3.1F, 4F, 3F, 1F, new Dilation(0F)),
				ModelTransform.pivot(1.9F, 12F, 0F));
		ModelPartData right_boot = root.addChild("right_boot", ModelPartBuilder.create()
				.mirrored(false).uv(90, 16).cuboid(-2F, 7F, -2F, 4F, 5F, 4F, new Dilation(1F))
				.mirrored(false).uv(40, 30).cuboid(-2F, 10F, -3.8F, 4F, 2F, 2F, new Dilation(0F)),
				ModelTransform.pivot(-1.9F, 12F, 0F));
		ModelPartData left_boot = root.addChild("left_boot", ModelPartBuilder.create()
				.mirrored(true).uv(90, 16).cuboid(-2F, 7F, -2F, 4F, 5F, 4F, new Dilation(1F))
				.mirrored(true).uv(40, 30).cuboid(-2F, 10F, -3.8F, 4F, 2F, 2F, new Dilation(0F)),
				ModelTransform.pivot(1.9F, 12F, 0F));
		return TexturedModelData.of(data, 128, 64);
	}

	private static TexturedModelData vikingHunter() {
		ModelData data = new ModelData();
		ModelPartData root = data.getRoot();
		ModelPartData head = root.addChild("head", ModelPartBuilder.create()
				.mirrored(false).uv(0, 0).cuboid(-4F, -8F, -4F, 8F, 8F, 8F, new Dilation(1F))
				.mirrored(false).uv(72, 16).cuboid(-3F, -10F, -3F, 6F, 2F, 5F, new Dilation(0.2F))
				.mirrored(false).uv(94, 16).cuboid(-2.5F, -10.4F, -7.4F, 5F, 3F, 4F, new Dilation(0F))
				.mirrored(false).uv(50, 25).cuboid(-4F, -12F, -2F, 2F, 3F, 1F, new Dilation(0F))
				.mirrored(false).uv(56, 25).cuboid(2F, -12F, -2F, 2F, 3F, 1F, new Dilation(0F))
				.mirrored(false).uv(0, 25).cuboid(-4F, -4F, 4F, 8F, 6F, 1F, new Dilation(0.6F)),
				ModelTransform.pivot(0F, 0F, 0F));
		ModelPartData body = root.addChild("body", ModelPartBuilder.create()
				.mirrored(false).uv(32, 0).cuboid(-4F, 0F, -2F, 8F, 12F, 4F, new Dilation(0.8F))
				.mirrored(false).uv(56, 0).cuboid(-4F, 0F, -2F, 8F, 12F, 4F, new Dilation(1.1F))
				.mirrored(false).uv(0, 16).cuboid(-5F, -1F, -3F, 10F, 3F, 6F, new Dilation(0.4F)),
				ModelTransform.pivot(0F, 0F, 0F));
		ModelPartData waist = root.addChild("waist", ModelPartBuilder.create()
				.mirrored(false).uv(32, 16).cuboid(-4F, 7F, -2F, 8F, 5F, 4F, new Dilation(0.5F)),
				ModelTransform.pivot(0F, 0F, 0F));
		ModelPartData right_arm = root.addChild("right_arm", ModelPartBuilder.create()
				.mirrored(false).uv(80, 0).cuboid(-3F, -2F, -2F, 4F, 12F, 4F, new Dilation(0.8F))
				.mirrored(false).uv(18, 25).cuboid(-3F, 5F, -2F, 4F, 3F, 4F, new Dilation(1.05F, 0.1F, 1.05F)),
				ModelTransform.pivot(-5F, 2F, 0F));
		ModelPartData left_arm = root.addChild("left_arm", ModelPartBuilder.create()
				.mirrored(true).uv(80, 0).cuboid(-1F, -2F, -2F, 4F, 12F, 4F, new Dilation(0.8F))
				.mirrored(true).uv(18, 25).cuboid(-1F, 5F, -2F, 4F, 3F, 4F, new Dilation(1.05F, 0.1F, 1.05F)),
				ModelTransform.pivot(5F, 2F, 0F));
		ModelPartData right_skirt = root.addChild("right_skirt", ModelPartBuilder.create(),
				ModelTransform.pivot(-1.9F, 12F, 0F));
		ModelPartData left_skirt = root.addChild("left_skirt", ModelPartBuilder.create(),
				ModelTransform.pivot(1.9F, 12F, 0F));
		ModelPartData right_leg = root.addChild("right_leg", ModelPartBuilder.create()
				.mirrored(false).uv(96, 0).cuboid(-2F, 0F, -2F, 4F, 12F, 4F, new Dilation(0.5F)),
				ModelTransform.pivot(-1.9F, 12F, 0F));
		ModelPartData left_leg = root.addChild("left_leg", ModelPartBuilder.create()
				.mirrored(true).uv(96, 0).cuboid(-2F, 0F, -2F, 4F, 12F, 4F, new Dilation(0.5F)),
				ModelTransform.pivot(1.9F, 12F, 0F));
		ModelPartData right_boot = root.addChild("right_boot", ModelPartBuilder.create()
				.mirrored(false).uv(56, 16).cuboid(-2F, 7F, -2F, 4F, 5F, 4F, new Dilation(1F))
				.mirrored(false).uv(34, 25).cuboid(-2F, 11F, -2F, 4F, 1F, 4F, new Dilation(1.05F, 1.05F, 1.05F)),
				ModelTransform.pivot(-1.9F, 12F, 0F));
		ModelPartData left_boot = root.addChild("left_boot", ModelPartBuilder.create()
				.mirrored(true).uv(56, 16).cuboid(-2F, 7F, -2F, 4F, 5F, 4F, new Dilation(1F))
				.mirrored(true).uv(34, 25).cuboid(-2F, 11F, -2F, 4F, 1F, 4F, new Dilation(1.05F, 1.05F, 1.05F)),
				ModelTransform.pivot(1.9F, 12F, 0F));
		return TexturedModelData.of(data, 128, 32);
	}

	private static TexturedModelData vikingWarrior() {
		ModelData data = new ModelData();
		ModelPartData root = data.getRoot();
		ModelPartData head = root.addChild("head", ModelPartBuilder.create()
				.mirrored(false).uv(56, 0).cuboid(-4F, -8F, -4F, 8F, 5F, 8F, new Dilation(1F))
				.mirrored(false).uv(16, 16).cuboid(-3F, -10F, -3F, 6F, 2F, 6F, new Dilation(0.6F))
				.mirrored(false).uv(0, 25).cuboid(-1.5F, -11.2F, -1.5F, 3F, 1F, 3F, new Dilation(0.2F))
				.mirrored(false).uv(120, 16).cuboid(-0.5F, -5F, -5.6F, 1F, 4F, 1F, new Dilation(0F)),
				ModelTransform.pivot(0F, 0F, 0F));
		ModelPartData body = root.addChild("body", ModelPartBuilder.create()
				.mirrored(false).uv(0, 0).cuboid(-4F, 0F, -2F, 8F, 12F, 4F, new Dilation(1F))
				.mirrored(false).uv(80, 16).cuboid(-4F, 9F, -2F, 8F, 2F, 4F, new Dilation(1.25F, 0.15F, 1.25F))
				.mirrored(false).uv(40, 16).cuboid(-4F, 0F, -2F, 8F, 3F, 4F, new Dilation(1.15F)),
				ModelTransform.pivot(0F, 0F, 0F));
		ModelPartData waist = root.addChild("waist", ModelPartBuilder.create()
				.mirrored(false).uv(104, 0).cuboid(-4F, 7F, -2F, 8F, 5F, 4F, new Dilation(0.5F)),
				ModelTransform.pivot(0F, 0F, 0F));
		ModelPartData right_arm = root.addChild("right_arm", ModelPartBuilder.create()
				.mirrored(false).uv(88, 0).cuboid(-3F, -2F, -2F, 4F, 8F, 4F, new Dilation(1F))
				.mirrored(false).uv(24, 0).cuboid(-3F, -2F, -2F, 4F, 12F, 4F, new Dilation(0.8F))
				.mirrored(false).uv(104, 16).cuboid(-3F, 6F, -2F, 4F, 2F, 4F, new Dilation(1.1F, 0.1F, 1.1F)),
				ModelTransform.pivot(-5F, 2F, 0F));
		ModelPartData left_arm = root.addChild("left_arm", ModelPartBuilder.create()
				.mirrored(true).uv(88, 0).cuboid(-1F, -2F, -2F, 4F, 8F, 4F, new Dilation(1F))
				.mirrored(true).uv(24, 0).cuboid(-1F, -2F, -2F, 4F, 12F, 4F, new Dilation(0.8F))
				.mirrored(true).uv(104, 16).cuboid(-1F, 6F, -2F, 4F, 2F, 4F, new Dilation(1.1F, 0.1F, 1.1F)),
				ModelTransform.pivot(5F, 2F, 0F));
		ModelPartData right_skirt = root.addChild("right_skirt", ModelPartBuilder.create()
				.mirrored(false).uv(64, 16).cuboid(-2F, 0F, -2F, 4F, 3F, 4F, new Dilation(1F)),
				ModelTransform.pivot(-1.9F, 12F, 0F));
		ModelPartData left_skirt = root.addChild("left_skirt", ModelPartBuilder.create()
				.mirrored(true).uv(64, 16).cuboid(-2F, 0F, -2F, 4F, 3F, 4F, new Dilation(1F)),
				ModelTransform.pivot(1.9F, 12F, 0F));
		ModelPartData right_leg = root.addChild("right_leg", ModelPartBuilder.create()
				.mirrored(false).uv(40, 0).cuboid(-2F, 0F, -2F, 4F, 12F, 4F, new Dilation(0.5F)),
				ModelTransform.pivot(-1.9F, 12F, 0F));
		ModelPartData left_leg = root.addChild("left_leg", ModelPartBuilder.create()
				.mirrored(true).uv(40, 0).cuboid(-2F, 0F, -2F, 4F, 12F, 4F, new Dilation(0.5F)),
				ModelTransform.pivot(1.9F, 12F, 0F));
		ModelPartData right_boot = root.addChild("right_boot", ModelPartBuilder.create()
				.mirrored(false).uv(0, 16).cuboid(-2F, 7F, -2F, 4F, 5F, 4F, new Dilation(1F)),
				ModelTransform.pivot(-1.9F, 12F, 0F));
		ModelPartData left_boot = root.addChild("left_boot", ModelPartBuilder.create()
				.mirrored(true).uv(0, 16).cuboid(-2F, 7F, -2F, 4F, 5F, 4F, new Dilation(1F)),
				ModelTransform.pivot(1.9F, 12F, 0F));
		return TexturedModelData.of(data, 128, 32);
	}

	private static TexturedModelData vikingHuscarl() {
		ModelData data = new ModelData();
		ModelPartData root = data.getRoot();
		ModelPartData head = root.addChild("head", ModelPartBuilder.create()
				.mirrored(false).uv(56, 0).cuboid(-4F, -8F, -4F, 8F, 5F, 8F, new Dilation(1F))
				.mirrored(false).uv(46, 16).cuboid(-0.5F, -9.4F, -4.5F, 1F, 1F, 9F, new Dilation(0F))
				.mirrored(false).uv(50, 29).cuboid(-4F, -5F, -5.6F, 8F, 3F, 1F, new Dilation(0F))
				.mirrored(false).uv(68, 29).cuboid(-0.5F, -2F, -5.6F, 1F, 2F, 1F, new Dilation(0F))
				.mirrored(false).uv(88, 0).cuboid(-4F, -3F, -4F, 8F, 5F, 8F, new Dilation(1.1F)),
				ModelTransform.pivot(0F, 0F, 0F));
		ModelPartData body = root.addChild("body", ModelPartBuilder.create()
				.mirrored(false).uv(0, 0).cuboid(-4F, 0F, -2F, 8F, 12F, 4F, new Dilation(1F))
				.mirrored(false).uv(0, 16).cuboid(-4F, 0F, -2F, 8F, 9F, 4F, new Dilation(1.15F))
				.mirrored(false).uv(16, 29).cuboid(-4F, 9F, -2F, 8F, 2F, 4F, new Dilation(1.3F, 0.15F, 1.3F)),
				ModelTransform.pivot(0F, 0F, 0F));
		ModelPartData waist = root.addChild("waist", ModelPartBuilder.create()
				.mirrored(false).uv(82, 16).cuboid(-4F, 7F, -2F, 8F, 5F, 4F, new Dilation(0.5F)),
				ModelTransform.pivot(0F, 0F, 0F));
		ModelPartData right_arm = root.addChild("right_arm", ModelPartBuilder.create()
				.mirrored(false).uv(24, 0).cuboid(-3F, -2F, -2F, 4F, 12F, 4F, new Dilation(1F))
				.mirrored(false).uv(0, 29).cuboid(-3F, 6F, -2F, 4F, 3F, 4F, new Dilation(1.15F, 0.1F, 1.15F)),
				ModelTransform.pivot(-5F, 2F, 0F));
		ModelPartData right_arm_0 = right_arm.addChild("c0", ModelPartBuilder.create()
				.mirrored(false).uv(24, 16).cuboid(-4F, -3F, -3F, 5F, 4F, 6F, new Dilation(0.35F)),
				ModelTransform.of(0F, 0F, 0F, 0F, 0F, 0.1396F));
		ModelPartData left_arm = root.addChild("left_arm", ModelPartBuilder.create()
				.mirrored(true).uv(24, 0).cuboid(-1F, -2F, -2F, 4F, 12F, 4F, new Dilation(1F))
				.mirrored(true).uv(0, 29).cuboid(-1F, 6F, -2F, 4F, 3F, 4F, new Dilation(1.15F, 0.1F, 1.15F)),
				ModelTransform.pivot(5F, 2F, 0F));
		ModelPartData left_arm_0 = left_arm.addChild("c0", ModelPartBuilder.create()
				.mirrored(true).uv(24, 16).cuboid(-1F, -3F, -3F, 5F, 4F, 6F, new Dilation(0.35F)),
				ModelTransform.of(0F, 0F, 0F, 0F, 0F, -0.1396F));
		ModelPartData right_skirt = root.addChild("right_skirt", ModelPartBuilder.create()
				.mirrored(false).uv(106, 16).cuboid(-2F, -1F, -2F, 4F, 5F, 4F, new Dilation(1.15F)),
				ModelTransform.pivot(-1.9F, 12F, 0F));
		ModelPartData left_skirt = root.addChild("left_skirt", ModelPartBuilder.create()
				.mirrored(true).uv(106, 16).cuboid(-2F, -1F, -2F, 4F, 5F, 4F, new Dilation(1.15F)),
				ModelTransform.pivot(1.9F, 12F, 0F));
		ModelPartData right_leg = root.addChild("right_leg", ModelPartBuilder.create()
				.mirrored(false).uv(40, 0).cuboid(-2F, 0F, -2F, 4F, 12F, 4F, new Dilation(0.5F)),
				ModelTransform.pivot(-1.9F, 12F, 0F));
		ModelPartData left_leg = root.addChild("left_leg", ModelPartBuilder.create()
				.mirrored(true).uv(40, 0).cuboid(-2F, 0F, -2F, 4F, 12F, 4F, new Dilation(0.5F)),
				ModelTransform.pivot(1.9F, 12F, 0F));
		ModelPartData right_boot = root.addChild("right_boot", ModelPartBuilder.create()
				.mirrored(false).uv(66, 16).cuboid(-2F, 6F, -2F, 4F, 6F, 4F, new Dilation(1F))
				.mirrored(false).uv(40, 29).cuboid(-2F, 6F, -3.3F, 4F, 4F, 1F, new Dilation(0F)),
				ModelTransform.pivot(-1.9F, 12F, 0F));
		ModelPartData left_boot = root.addChild("left_boot", ModelPartBuilder.create()
				.mirrored(true).uv(66, 16).cuboid(-2F, 6F, -2F, 4F, 6F, 4F, new Dilation(1F))
				.mirrored(true).uv(40, 29).cuboid(-2F, 6F, -3.3F, 4F, 4F, 1F, new Dilation(0F)),
				ModelTransform.pivot(1.9F, 12F, 0F));
		return TexturedModelData.of(data, 128, 64);
	}

	private static TexturedModelData vikingJarl() {
		ModelData data = new ModelData();
		ModelPartData root = data.getRoot();
		ModelPartData head = root.addChild("head", ModelPartBuilder.create()
				.mirrored(false).uv(0, 16).cuboid(-4F, -8F, -4F, 8F, 5F, 8F, new Dilation(1F))
				.mirrored(false).uv(86, 16).cuboid(-0.5F, -9.4F, -4.5F, 1F, 1F, 9F, new Dilation(0F))
				.mirrored(false).uv(16, 38).cuboid(-0.5F, -5F, -5.6F, 1F, 4F, 1F, new Dilation(0F))
				.mirrored(false).uv(32, 16).cuboid(-4F, -3F, -4F, 8F, 4F, 8F, new Dilation(1.1F)),
				ModelTransform.pivot(0F, 0F, 0F));
		ModelPartData head_0 = head.addChild("c0", ModelPartBuilder.create()
				.mirrored(false).uv(20, 38).cuboid(-4F, -1F, -1F, 4F, 2F, 2F, new Dilation(0F)),
				ModelTransform.of(-4.6F, -6.2F, 0.5F, 0F, -0.2094F, 0.2094F));
		ModelPartData head_0_0 = head_0.addChild("c0", ModelPartBuilder.create()
				.mirrored(false).uv(32, 38).cuboid(-3F, -1F, -1F, 3F, 2F, 2F, new Dilation(-0.2F)),
				ModelTransform.of(-3.8F, 0F, 0F, 0F, -0.1745F, 0.5585F));
		ModelPartData head_0_0_0 = head_0_0.addChild("c0", ModelPartBuilder.create()
				.mirrored(false).uv(42, 38).cuboid(-3F, -0.5F, -0.5F, 3F, 1F, 1F, new Dilation(0F)),
				ModelTransform.of(-2.8F, 0F, 0F, 0F, -0.2094F, 0.6981F));
		ModelPartData head_1 = head.addChild("c1", ModelPartBuilder.create()
				.mirrored(true).uv(20, 38).cuboid(0F, -1F, -1F, 4F, 2F, 2F, new Dilation(0F)),
				ModelTransform.of(4.6F, -6.2F, 0.5F, 0F, 0.2094F, -0.2094F));
		ModelPartData head_1_0 = head_1.addChild("c0", ModelPartBuilder.create()
				.mirrored(true).uv(32, 38).cuboid(0F, -1F, -1F, 3F, 2F, 2F, new Dilation(-0.2F)),
				ModelTransform.of(3.8F, 0F, 0F, 0F, 0.1745F, -0.5585F));
		ModelPartData head_1_0_0 = head_1_0.addChild("c0", ModelPartBuilder.create()
				.mirrored(true).uv(42, 38).cuboid(0F, -0.5F, -0.5F, 3F, 1F, 1F, new Dilation(0F)),
				ModelTransform.of(2.8F, 0F, 0F, 0F, 0.2094F, -0.6981F));
		ModelPartData body = root.addChild("body", ModelPartBuilder.create()
				.mirrored(false).uv(0, 0).cuboid(-4F, 0F, -2F, 8F, 12F, 4F, new Dilation(1F))
				.mirrored(false).uv(56, 0).cuboid(-4F, 0F, -2F, 8F, 10F, 4F, new Dilation(1.15F))
				.mirrored(false).uv(72, 29).cuboid(-4F, 9F, -2F, 8F, 2F, 4F, new Dilation(1.3F, 0.15F, 1.3F))
				.mirrored(false).uv(0, 29).cuboid(-5F, -1F, -3F, 10F, 3F, 6F, new Dilation(0.4F)),
				ModelTransform.pivot(0F, 0F, 0F));
		ModelPartData body_0 = body.addChild("c0", ModelPartBuilder.create()
				.mirrored(false).uv(80, 0).cuboid(-4F, 0F, 0F, 8F, 13F, 1F, new Dilation(0.1F)),
				ModelTransform.of(0F, 0.5F, 3.2F, 0.1222F, 0F, 0F));
		ModelPartData waist = root.addChild("waist", ModelPartBuilder.create()
				.mirrored(false).uv(32, 29).cuboid(-4F, 7F, -2F, 8F, 5F, 4F, new Dilation(0.5F)),
				ModelTransform.pivot(0F, 0F, 0F));
		ModelPartData right_arm = root.addChild("right_arm", ModelPartBuilder.create()
				.mirrored(false).uv(24, 0).cuboid(-3F, -2F, -2F, 4F, 12F, 4F, new Dilation(1F))
				.mirrored(false).uv(112, 29).cuboid(-3F, 6F, -2F, 4F, 1F, 4F, new Dilation(1.15F, 0.1F, 1.15F))
				.mirrored(false).uv(0, 38).cuboid(-3F, 3F, -2F, 4F, 1F, 4F, new Dilation(1.15F, 0.1F, 1.15F)),
				ModelTransform.pivot(-5F, 2F, 0F));
		ModelPartData right_arm_0 = right_arm.addChild("c0", ModelPartBuilder.create()
				.mirrored(false).uv(64, 16).cuboid(-4F, -3F, -3F, 5F, 4F, 6F, new Dilation(0.35F)),
				ModelTransform.of(0F, 0F, 0F, 0F, 0F, 0.1396F));
		ModelPartData left_arm = root.addChild("left_arm", ModelPartBuilder.create()
				.mirrored(true).uv(24, 0).cuboid(-1F, -2F, -2F, 4F, 12F, 4F, new Dilation(1F))
				.mirrored(true).uv(112, 29).cuboid(-1F, 6F, -2F, 4F, 1F, 4F, new Dilation(1.15F, 0.1F, 1.15F))
				.mirrored(true).uv(0, 38).cuboid(-1F, 3F, -2F, 4F, 1F, 4F, new Dilation(1.15F, 0.1F, 1.15F)),
				ModelTransform.pivot(5F, 2F, 0F));
		ModelPartData left_arm_0 = left_arm.addChild("c0", ModelPartBuilder.create()
				.mirrored(true).uv(64, 16).cuboid(-1F, -3F, -3F, 5F, 4F, 6F, new Dilation(0.35F)),
				ModelTransform.of(0F, 0F, 0F, 0F, 0F, -0.1396F));
		ModelPartData right_skirt = root.addChild("right_skirt", ModelPartBuilder.create()
				.mirrored(false).uv(106, 16).cuboid(-2F, -1F, -2F, 4F, 6F, 4F, new Dilation(1.15F)),
				ModelTransform.pivot(-1.9F, 12F, 0F));
		ModelPartData left_skirt = root.addChild("left_skirt", ModelPartBuilder.create()
				.mirrored(true).uv(106, 16).cuboid(-2F, -1F, -2F, 4F, 6F, 4F, new Dilation(1.15F)),
				ModelTransform.pivot(1.9F, 12F, 0F));
		ModelPartData right_leg = root.addChild("right_leg", ModelPartBuilder.create()
				.mirrored(false).uv(40, 0).cuboid(-2F, 0F, -2F, 4F, 12F, 4F, new Dilation(0.5F)),
				ModelTransform.pivot(-1.9F, 12F, 0F));
		ModelPartData left_leg = root.addChild("left_leg", ModelPartBuilder.create()
				.mirrored(true).uv(40, 0).cuboid(-2F, 0F, -2F, 4F, 12F, 4F, new Dilation(0.5F)),
				ModelTransform.pivot(1.9F, 12F, 0F));
		ModelPartData right_boot = root.addChild("right_boot", ModelPartBuilder.create()
				.mirrored(false).uv(56, 29).cuboid(-2F, 7F, -2F, 4F, 5F, 4F, new Dilation(1F))
				.mirrored(false).uv(96, 29).cuboid(-2F, 6F, -2F, 4F, 2F, 4F, new Dilation(1.35F, 0.3F, 1.35F)),
				ModelTransform.pivot(-1.9F, 12F, 0F));
		ModelPartData left_boot = root.addChild("left_boot", ModelPartBuilder.create()
				.mirrored(true).uv(56, 29).cuboid(-2F, 7F, -2F, 4F, 5F, 4F, new Dilation(1F))
				.mirrored(true).uv(96, 29).cuboid(-2F, 6F, -2F, 4F, 2F, 4F, new Dilation(1.35F, 0.3F, 1.35F)),
				ModelTransform.pivot(1.9F, 12F, 0F));
		return TexturedModelData.of(data, 128, 64);
	}

	private static TexturedModelData pirateDeckhand() {
		ModelData data = new ModelData();
		ModelPartData root = data.getRoot();
		ModelPartData head = root.addChild("head", ModelPartBuilder.create()
				.mirrored(false).uv(40, 0).cuboid(-4F, -8F, -4F, 8F, 4F, 8F, new Dilation(1.05F))
				.mirrored(false).uv(52, 16).cuboid(-1F, -6F, 4.4F, 2F, 2F, 1F, new Dilation(0.3F)),
				ModelTransform.pivot(0F, 0F, 0F));
		ModelPartData head_0 = head.addChild("c0", ModelPartBuilder.create()
				.mirrored(false).uv(40, 16).cuboid(-0.5F, 0F, -0.5F, 1F, 4F, 1F, new Dilation(0F)),
				ModelTransform.of(-0.8F, -5F, 5.8F, 0.4363F, 0F, 0.2618F));
		ModelPartData head_1 = head.addChild("c1", ModelPartBuilder.create()
				.mirrored(false).uv(48, 16).cuboid(-0.5F, 0F, -0.5F, 1F, 3F, 1F, new Dilation(0F)),
				ModelTransform.of(0.8F, -5F, 5.8F, 0.3491F, 0F, -0.2094F));
		ModelPartData body = root.addChild("body", ModelPartBuilder.create()
				.mirrored(false).uv(0, 0).cuboid(-4F, 0F, -2F, 8F, 12F, 4F, new Dilation(1F))
				.mirrored(false).uv(0, 16).cuboid(-4F, 9F, -2F, 8F, 2F, 4F, new Dilation(1.2F, 0.15F, 1.2F)),
				ModelTransform.pivot(0F, 0F, 0F));
		ModelPartData body_0 = body.addChild("c0", ModelPartBuilder.create()
				.mirrored(false).uv(44, 16).cuboid(0F, 0F, -0.5F, 1F, 4F, 1F, new Dilation(0F)),
				ModelTransform.of(4.9F, 10.5F, -1F, 0F, 0F, -0.1745F));
		ModelPartData waist = root.addChild("waist", ModelPartBuilder.create()
				.mirrored(false).uv(88, 0).cuboid(-4F, 7F, -2F, 8F, 5F, 4F, new Dilation(0.5F)),
				ModelTransform.pivot(0F, 0F, 0F));
		ModelPartData right_arm = root.addChild("right_arm", ModelPartBuilder.create()
				.mirrored(false).uv(72, 0).cuboid(-3F, -2F, -2F, 4F, 7F, 4F, new Dilation(1F)),
				ModelTransform.pivot(-5F, 2F, 0F));
		ModelPartData left_arm = root.addChild("left_arm", ModelPartBuilder.create()
				.mirrored(true).uv(72, 0).cuboid(-1F, -2F, -2F, 4F, 7F, 4F, new Dilation(1F)),
				ModelTransform.pivot(5F, 2F, 0F));
		ModelPartData right_skirt = root.addChild("right_skirt", ModelPartBuilder.create(),
				ModelTransform.pivot(-1.9F, 12F, 0F));
		ModelPartData left_skirt = root.addChild("left_skirt", ModelPartBuilder.create(),
				ModelTransform.pivot(1.9F, 12F, 0F));
		ModelPartData right_leg = root.addChild("right_leg", ModelPartBuilder.create()
				.mirrored(false).uv(24, 0).cuboid(-2F, 0F, -2F, 4F, 9F, 4F, new Dilation(0.5F))
				.mirrored(false).uv(24, 16).cuboid(-2F, 8F, -2F, 4F, 1F, 4F, new Dilation(0.8F, 0.2F, 0.8F)),
				ModelTransform.pivot(-1.9F, 12F, 0F));
		ModelPartData left_leg = root.addChild("left_leg", ModelPartBuilder.create()
				.mirrored(true).uv(24, 0).cuboid(-2F, 0F, -2F, 4F, 9F, 4F, new Dilation(0.5F))
				.mirrored(true).uv(24, 16).cuboid(-2F, 8F, -2F, 4F, 1F, 4F, new Dilation(0.8F, 0.2F, 0.8F)),
				ModelTransform.pivot(1.9F, 12F, 0F));
		ModelPartData right_boot = root.addChild("right_boot", ModelPartBuilder.create()
				.mirrored(false).uv(112, 0).cuboid(-2F, 9F, -2F, 4F, 3F, 4F, new Dilation(1F)),
				ModelTransform.pivot(-1.9F, 12F, 0F));
		ModelPartData left_boot = root.addChild("left_boot", ModelPartBuilder.create()
				.mirrored(true).uv(112, 0).cuboid(-2F, 9F, -2F, 4F, 3F, 4F, new Dilation(1F)),
				ModelTransform.pivot(1.9F, 12F, 0F));
		return TexturedModelData.of(data, 128, 32);
	}

	private static TexturedModelData pirateBuccaneer() {
		ModelData data = new ModelData();
		ModelPartData root = data.getRoot();
		ModelPartData head = root.addChild("head", ModelPartBuilder.create()
				.mirrored(false).uv(56, 16).cuboid(-4F, -11F, -4F, 8F, 3F, 8F, new Dilation(0.4F))
				.mirrored(false).uv(0, 16).cuboid(-7F, -8.3F, -7F, 14F, 1F, 14F, new Dilation(0F)),
				ModelTransform.pivot(0F, 0F, 0F));
		ModelPartData body = root.addChild("body", ModelPartBuilder.create()
				.mirrored(false).uv(0, 0).cuboid(-4F, 0F, -2F, 8F, 12F, 4F, new Dilation(0.8F))
				.mirrored(false).uv(24, 0).cuboid(-4F, 0F, -2F, 8F, 12F, 4F, new Dilation(1.05F))
				.mirrored(false).uv(32, 31).cuboid(-4F, 9F, -2F, 8F, 2F, 4F, new Dilation(1.25F, 0.15F, 1.25F)),
				ModelTransform.pivot(0F, 0F, 0F));
		ModelPartData waist = root.addChild("waist", ModelPartBuilder.create()
				.mirrored(false).uv(104, 16).cuboid(-4F, 7F, -2F, 8F, 5F, 4F, new Dilation(0.5F)),
				ModelTransform.pivot(0F, 0F, 0F));
		ModelPartData right_arm = root.addChild("right_arm", ModelPartBuilder.create()
				.mirrored(false).uv(48, 0).cuboid(-3F, -2F, -2F, 4F, 12F, 4F, new Dilation(1F))
				.mirrored(false).uv(16, 31).cuboid(-3F, 6F, -2F, 4F, 3F, 4F, new Dilation(1.35F, 0.1F, 1.35F)),
				ModelTransform.pivot(-5F, 2F, 0F));
		ModelPartData left_arm = root.addChild("left_arm", ModelPartBuilder.create()
				.mirrored(true).uv(48, 0).cuboid(-1F, -2F, -2F, 4F, 12F, 4F, new Dilation(1F))
				.mirrored(true).uv(16, 31).cuboid(-1F, 6F, -2F, 4F, 3F, 4F, new Dilation(1.35F, 0.1F, 1.35F)),
				ModelTransform.pivot(5F, 2F, 0F));
		ModelPartData right_skirt = root.addChild("right_skirt", ModelPartBuilder.create()
				.mirrored(false).uv(0, 31).cuboid(-2F, 0F, -2F, 4F, 4F, 4F, new Dilation(1.05F)),
				ModelTransform.pivot(-1.9F, 12F, 0F));
		ModelPartData left_skirt = root.addChild("left_skirt", ModelPartBuilder.create()
				.mirrored(true).uv(0, 31).cuboid(-2F, 0F, -2F, 4F, 4F, 4F, new Dilation(1.05F)),
				ModelTransform.pivot(1.9F, 12F, 0F));
		ModelPartData right_leg = root.addChild("right_leg", ModelPartBuilder.create()
				.mirrored(false).uv(64, 0).cuboid(-2F, 0F, -2F, 4F, 12F, 4F, new Dilation(0.5F)),
				ModelTransform.pivot(-1.9F, 12F, 0F));
		ModelPartData left_leg = root.addChild("left_leg", ModelPartBuilder.create()
				.mirrored(true).uv(64, 0).cuboid(-2F, 0F, -2F, 4F, 12F, 4F, new Dilation(0.5F)),
				ModelTransform.pivot(1.9F, 12F, 0F));
		ModelPartData right_boot = root.addChild("right_boot", ModelPartBuilder.create()
				.mirrored(false).uv(88, 16).cuboid(-2F, 5F, -2F, 4F, 7F, 4F, new Dilation(1F))
				.mirrored(false).uv(56, 31).cuboid(-2F, 5F, -2F, 4F, 2F, 4F, new Dilation(1.4F, 0.3F, 1.4F)),
				ModelTransform.pivot(-1.9F, 12F, 0F));
		ModelPartData left_boot = root.addChild("left_boot", ModelPartBuilder.create()
				.mirrored(true).uv(88, 16).cuboid(-2F, 5F, -2F, 4F, 7F, 4F, new Dilation(1F))
				.mirrored(true).uv(56, 31).cuboid(-2F, 5F, -2F, 4F, 2F, 4F, new Dilation(1.4F, 0.3F, 1.4F)),
				ModelTransform.pivot(1.9F, 12F, 0F));
		return TexturedModelData.of(data, 128, 64);
	}

	private static TexturedModelData pirateCorsair() {
		ModelData data = new ModelData();
		ModelPartData root = data.getRoot();
		ModelPartData head = root.addChild("head", ModelPartBuilder.create(),
				ModelTransform.pivot(0F, 0F, 0F));
		ModelPartData head_0 = head.addChild("c0", ModelPartBuilder.create()
				.mirrored(false).uv(56, 0).cuboid(-4F, -12.2F, -4F, 8F, 4F, 8F, new Dilation(0.35F)),
				ModelTransform.pivot(0F, 0F, 0F));
		ModelPartData head_1 = head.addChild("c1", ModelPartBuilder.create()
				.mirrored(false).uv(30, 25).cuboid(-6F, -3F, 0F, 12F, 3F, 1F, new Dilation(0F)),
				ModelTransform.of(0F, -8.6F, 5.4F, -0.2793F, 0F, 0F));
		ModelPartData head_2 = head.addChild("c2", ModelPartBuilder.create()
				.mirrored(false).uv(96, 16).cuboid(0F, -3F, -0.5F, 14F, 3F, 1F, new Dilation(0F)),
				ModelTransform.of(0F, -8.6F, -7.2F, -0.2793F, -2.0152F, 0F));
		ModelPartData head_3 = head.addChild("c3", ModelPartBuilder.create()
				.mirrored(false).uv(0, 25).cuboid(0F, -3F, -0.5F, 14F, 3F, 1F, new Dilation(0F)),
				ModelTransform.of(0F, -8.6F, -7.2F, 0.2793F, -1.1264F, 0F));
		ModelPartData body = root.addChild("body", ModelPartBuilder.create()
				.mirrored(false).uv(0, 0).cuboid(-4F, 0F, -2F, 8F, 12F, 4F, new Dilation(0.9F))
				.mirrored(false).uv(88, 0).cuboid(-4F, 0F, -2F, 8F, 8F, 4F, new Dilation(1.2F))
				.mirrored(false).uv(56, 16).cuboid(-4F, 9F, -2F, 8F, 2F, 4F, new Dilation(1.25F, 0.15F, 1.25F)),
				ModelTransform.pivot(0F, 0F, 0F));
		ModelPartData waist = root.addChild("waist", ModelPartBuilder.create()
				.mirrored(false).uv(0, 16).cuboid(-4F, 7F, -2F, 8F, 5F, 4F, new Dilation(0.5F)),
				ModelTransform.pivot(0F, 0F, 0F));
		ModelPartData right_arm = root.addChild("right_arm", ModelPartBuilder.create()
				.mirrored(false).uv(24, 0).cuboid(-3F, -2F, -2F, 4F, 12F, 4F, new Dilation(0.9F))
				.mirrored(false).uv(40, 16).cuboid(-3F, 6F, -2F, 4F, 3F, 4F, new Dilation(1.3F, 0.1F, 1.3F)),
				ModelTransform.pivot(-5F, 2F, 0F));
		ModelPartData left_arm = root.addChild("left_arm", ModelPartBuilder.create()
				.mirrored(true).uv(24, 0).cuboid(-1F, -2F, -2F, 4F, 12F, 4F, new Dilation(0.9F))
				.mirrored(true).uv(40, 16).cuboid(-1F, 6F, -2F, 4F, 3F, 4F, new Dilation(1.3F, 0.1F, 1.3F)),
				ModelTransform.pivot(5F, 2F, 0F));
		ModelPartData right_skirt = root.addChild("right_skirt", ModelPartBuilder.create()
				.mirrored(false).uv(24, 16).cuboid(-2F, 0F, -2F, 4F, 5F, 4F, new Dilation(1.05F)),
				ModelTransform.pivot(-1.9F, 12F, 0F));
		ModelPartData left_skirt = root.addChild("left_skirt", ModelPartBuilder.create()
				.mirrored(true).uv(24, 16).cuboid(-2F, 0F, -2F, 4F, 5F, 4F, new Dilation(1.05F)),
				ModelTransform.pivot(1.9F, 12F, 0F));
		ModelPartData right_leg = root.addChild("right_leg", ModelPartBuilder.create()
				.mirrored(false).uv(40, 0).cuboid(-2F, 0F, -2F, 4F, 12F, 4F, new Dilation(0.5F)),
				ModelTransform.pivot(-1.9F, 12F, 0F));
		ModelPartData left_leg = root.addChild("left_leg", ModelPartBuilder.create()
				.mirrored(true).uv(40, 0).cuboid(-2F, 0F, -2F, 4F, 12F, 4F, new Dilation(0.5F)),
				ModelTransform.pivot(1.9F, 12F, 0F));
		ModelPartData right_boot = root.addChild("right_boot", ModelPartBuilder.create()
				.mirrored(false).uv(112, 0).cuboid(-2F, 5F, -2F, 4F, 7F, 4F, new Dilation(1F))
				.mirrored(false).uv(80, 16).cuboid(-2F, 5F, -2F, 4F, 2F, 4F, new Dilation(1.4F, 0.3F, 1.4F)),
				ModelTransform.pivot(-1.9F, 12F, 0F));
		ModelPartData left_boot = root.addChild("left_boot", ModelPartBuilder.create()
				.mirrored(true).uv(112, 0).cuboid(-2F, 5F, -2F, 4F, 7F, 4F, new Dilation(1F))
				.mirrored(true).uv(80, 16).cuboid(-2F, 5F, -2F, 4F, 2F, 4F, new Dilation(1.4F, 0.3F, 1.4F)),
				ModelTransform.pivot(1.9F, 12F, 0F));
		return TexturedModelData.of(data, 128, 32);
	}

	private static TexturedModelData pirateCaptain() {
		ModelData data = new ModelData();
		ModelPartData root = data.getRoot();
		ModelPartData head = root.addChild("head", ModelPartBuilder.create()
				.mirrored(false).uv(28, 33).cuboid(-1.5F, -10.8F, -4.7F, 3F, 3F, 1F, new Dilation(0F)),
				ModelTransform.pivot(0F, 0F, 0F));
		ModelPartData head_0 = head.addChild("c0", ModelPartBuilder.create()
				.mirrored(false).uv(80, 0).cuboid(-4F, -12.2F, -4F, 8F, 4F, 8F, new Dilation(0.35F)),
				ModelTransform.pivot(0F, 0F, 0F));
		ModelPartData head_1 = head.addChild("c1", ModelPartBuilder.create()
				.mirrored(false).uv(0, 33).cuboid(-6.5F, -4F, 0F, 13F, 4F, 1F, new Dilation(0F)),
				ModelTransform.of(0F, -8.6F, 5.8F, -0.2793F, 0F, 0F));
		ModelPartData head_2 = head.addChild("c2", ModelPartBuilder.create()
				.mirrored(false).uv(40, 27).cuboid(0F, -4F, -0.5F, 15F, 4F, 1F, new Dilation(0F)),
				ModelTransform.of(0F, -8.6F, -7.8F, -0.2793F, -2.0166F, 0F));
		ModelPartData head_3 = head.addChild("c3", ModelPartBuilder.create()
				.mirrored(false).uv(72, 27).cuboid(0F, -4F, -0.5F, 15F, 4F, 1F, new Dilation(0F)),
				ModelTransform.of(0F, -8.6F, -7.8F, 0.2793F, -1.125F, 0F));
		ModelPartData head_4 = head.addChild("c4", ModelPartBuilder.create()
				.mirrored(false).uv(16, 16).cuboid(-0.5F, -9F, -1F, 1F, 9F, 2F, new Dilation(0F)),
				ModelTransform.of(3.9F, -10.6F, 1.5F, -0.4887F, 0F, 0.384F));
		ModelPartData head_5 = head.addChild("c5", ModelPartBuilder.create()
				.mirrored(false).uv(46, 16).cuboid(-0.5F, -7F, -1F, 1F, 7F, 2F, new Dilation(0F)),
				ModelTransform.of(3.6F, -10.6F, 2.5F, -0.6981F, 0F, 0.2443F));
		ModelPartData body = root.addChild("body", ModelPartBuilder.create()
				.mirrored(false).uv(0, 0).cuboid(-4F, 0F, -2F, 8F, 12F, 4F, new Dilation(0.8F))
				.mirrored(false).uv(24, 0).cuboid(-4F, 0F, -2F, 8F, 12F, 4F, new Dilation(1.1F))
				.mirrored(false).uv(0, 27).cuboid(-4F, 9F, -2F, 8F, 2F, 4F, new Dilation(1.3F, 0.15F, 1.3F)),
				ModelTransform.pivot(0F, 0F, 0F));
		ModelPartData waist = root.addChild("waist", ModelPartBuilder.create()
				.mirrored(false).uv(22, 16).cuboid(-4F, 7F, -2F, 8F, 5F, 4F, new Dilation(0.5F)),
				ModelTransform.pivot(0F, 0F, 0F));
		ModelPartData right_arm = root.addChild("right_arm", ModelPartBuilder.create()
				.mirrored(false).uv(48, 0).cuboid(-3F, -2F, -2F, 4F, 12F, 4F, new Dilation(1.1F))
				.mirrored(false).uv(52, 16).cuboid(-4F, -3F, -3F, 5F, 1F, 6F, new Dilation(0.3F))
				.mirrored(false).uv(74, 16).cuboid(-4F, -2.2F, -3F, 5F, 1F, 6F, new Dilation(0.35F, 0F, 0.35F))
				.mirrored(false).uv(96, 16).cuboid(-3F, 6F, -2F, 4F, 3F, 4F, new Dilation(1.45F, 0.1F, 1.45F)),
				ModelTransform.pivot(-5F, 2F, 0F));
		ModelPartData left_arm = root.addChild("left_arm", ModelPartBuilder.create()
				.mirrored(true).uv(48, 0).cuboid(-1F, -2F, -2F, 4F, 12F, 4F, new Dilation(1.1F))
				.mirrored(true).uv(52, 16).cuboid(-1F, -3F, -3F, 5F, 1F, 6F, new Dilation(0.3F))
				.mirrored(true).uv(74, 16).cuboid(-1F, -2.2F, -3F, 5F, 1F, 6F, new Dilation(0.35F, 0F, 0.35F))
				.mirrored(true).uv(96, 16).cuboid(-1F, 6F, -2F, 4F, 3F, 4F, new Dilation(1.45F, 0.1F, 1.45F)),
				ModelTransform.pivot(5F, 2F, 0F));
		ModelPartData right_skirt = root.addChild("right_skirt", ModelPartBuilder.create()
				.mirrored(false).uv(112, 0).cuboid(-2F, 0F, -2F, 4F, 8F, 4F, new Dilation(1.1F)),
				ModelTransform.pivot(-1.9F, 12F, 0F));
		ModelPartData left_skirt = root.addChild("left_skirt", ModelPartBuilder.create()
				.mirrored(true).uv(112, 0).cuboid(-2F, 0F, -2F, 4F, 8F, 4F, new Dilation(1.1F)),
				ModelTransform.pivot(1.9F, 12F, 0F));
		ModelPartData right_leg = root.addChild("right_leg", ModelPartBuilder.create()
				.mirrored(false).uv(64, 0).cuboid(-2F, 0F, -2F, 4F, 12F, 4F, new Dilation(0.5F)),
				ModelTransform.pivot(-1.9F, 12F, 0F));
		ModelPartData left_leg = root.addChild("left_leg", ModelPartBuilder.create()
				.mirrored(true).uv(64, 0).cuboid(-2F, 0F, -2F, 4F, 12F, 4F, new Dilation(0.5F)),
				ModelTransform.pivot(1.9F, 12F, 0F));
		ModelPartData right_boot = root.addChild("right_boot", ModelPartBuilder.create()
				.mirrored(false).uv(0, 16).cuboid(-2F, 5F, -2F, 4F, 7F, 4F, new Dilation(1F))
				.mirrored(false).uv(24, 27).cuboid(-2F, 5F, -2F, 4F, 2F, 4F, new Dilation(1.45F, 0.3F, 1.45F)),
				ModelTransform.pivot(-1.9F, 12F, 0F));
		ModelPartData left_boot = root.addChild("left_boot", ModelPartBuilder.create()
				.mirrored(true).uv(0, 16).cuboid(-2F, 5F, -2F, 4F, 7F, 4F, new Dilation(1F))
				.mirrored(true).uv(24, 27).cuboid(-2F, 5F, -2F, 4F, 2F, 4F, new Dilation(1.45F, 0.3F, 1.45F)),
				ModelTransform.pivot(1.9F, 12F, 0F));
		return TexturedModelData.of(data, 128, 64);
	}
}
