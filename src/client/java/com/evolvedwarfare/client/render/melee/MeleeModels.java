package com.evolvedwarfare.client.render.melee;

import com.evolvedwarfare.EvolvedWarfare;
import com.evolvedwarfare.weapon.WeaponType;
import net.minecraft.client.model.ModelData;
import net.minecraft.client.model.ModelPartBuilder;
import net.minecraft.client.model.ModelPartData;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.render.entity.model.EntityModelLayer;
import net.minecraft.util.Identifier;

/**
 * Los modelos 3D de las armas cuerpo a cuerpo (generado por tools/ew_melee.py: no lo edites a mano). Cada uno tiene
 * la pieza "weapon" (el arma en su marco: origen en la mano, +Y hacia la punta) puesta en la diagonal del objeto;
 * el mangual tiene además "chain" (cadena y bola, colgando hacia -Y), que el renderizador coloca en su sitio.
 */
public final class MeleeModels {
	/** Dónde agarra la mano (píxeles del objeto) y giro de la diagonal (grados, sobre Z). */
	public static final float GRIP_X = 3F, GRIP_Y = 3F, GRIP_Z = 8F, DIAGONAL = -45F;
	/** Altura (marco del arma, píxeles) de la que cuelga la cadena del mangual. */
	public static final float CHAIN_ANCHOR = 8F;
	public static final EntityModelLayer DAGGER = new EntityModelLayer(EvolvedWarfare.id("weapon_dagger"), "main");
	public static final EntityModelLayer SPEAR = new EntityModelLayer(EvolvedWarfare.id("weapon_spear"), "main");
	public static final EntityModelLayer LONGSWORD = new EntityModelLayer(EvolvedWarfare.id("weapon_longsword"), "main");
	public static final EntityModelLayer HALBERD = new EntityModelLayer(EvolvedWarfare.id("weapon_halberd"), "main");
	public static final EntityModelLayer WARHAMMER = new EntityModelLayer(EvolvedWarfare.id("weapon_warhammer"), "main");
	public static final EntityModelLayer FLAIL = new EntityModelLayer(EvolvedWarfare.id("weapon_flail"), "main");
	public static final EntityModelLayer BATTLE_AXE = new EntityModelLayer(EvolvedWarfare.id("weapon_battle_axe"), "main");

	private MeleeModels() {
	}

	public static Identifier texture(String material) {
		return EvolvedWarfare.id("textures/entity/weapons/" + material + ".png");
	}

	public static EntityModelLayer layer(WeaponType type) {
		return switch (type) {
			case DAGGER -> DAGGER;
			case SPEAR -> SPEAR;
			case LONGSWORD -> LONGSWORD;
			case HALBERD -> HALBERD;
			case WARHAMMER -> WARHAMMER;
			case FLAIL -> FLAIL;
			case BATTLE_AXE -> BATTLE_AXE;
		};
	}

	public static TexturedModelData of(WeaponType type) {
		return switch (type) {
			case DAGGER -> dagger();
			case SPEAR -> spear();
			case LONGSWORD -> longsword();
			case HALBERD -> halberd();
			case WARHAMMER -> warhammer();
			case FLAIL -> flail();
			case BATTLE_AXE -> battleAxe();
		};
	}

	private static TexturedModelData dagger() {
		ModelData data = new ModelData();
		ModelPartData root = data.getRoot();
		root.addChild("weapon", ModelPartBuilder.create()
				.uv(128, 0).cuboid(-1F, -3.3F, -1F, 2F, 1F, 2F)
				.uv(128, 64).cuboid(-0.6F, -2.3F, -0.6F, 1.2F, 3.3F, 1.2F)
				.uv(128, 0).cuboid(-2.2F, 1F, -0.7F, 4.4F, 0.8F, 1.4F)
				.uv(48, 0).cuboid(-0.95F, 1.8F, -0.3F, 1.9F, 6.2F, 0.6F)
				.uv(48, 64).cuboid(-1.05F, 1.8F, -0.18F, 0.12F, 6.2F, 0.36F)
				.uv(48, 64).cuboid(0.93F, 1.8F, -0.18F, 0.12F, 6.2F, 0.36F)
				.uv(192, 32).cuboid(-0.2F, 2.2F, -0.33F, 0.4F, 4.8F, 0.66F)
				.uv(48, 0).cuboid(-0.65F, 8F, -0.25F, 1.3F, 1.1F, 0.5F)
				.uv(48, 0).cuboid(-0.3F, 9.1F, -0.2F, 0.6F, 0.9F, 0.4F),
				ModelTransform.of(GRIP_X, GRIP_Y, GRIP_Z, 0F, 0F, DIAGONAL * (float) (Math.PI / 180.0)));
		return TexturedModelData.of(data, 256, 128);
	}

	private static TexturedModelData spear() {
		ModelData data = new ModelData();
		ModelPartData root = data.getRoot();
		root.addChild("weapon", ModelPartBuilder.create()
				.uv(0, 0).cuboid(-0.65F, -13F, -0.65F, 1.3F, 31F, 1.3F)
				.uv(128, 0).cuboid(-0.85F, -14.1F, -0.85F, 1.7F, 1.2F, 1.7F)
				.uv(128, 64).cuboid(-0.75F, -2.5F, -0.75F, 1.5F, 5F, 1.5F)
				.uv(128, 64).cuboid(-0.75F, -10F, -0.75F, 1.5F, 3F, 1.5F)
				.uv(128, 0).cuboid(-0.85F, 17.6F, -0.85F, 1.7F, 2.6F, 1.7F)
				.uv(48, 0).cuboid(-1.2F, 20F, -0.28F, 2.4F, 0.6F, 0.56F)
				.uv(48, 0).cuboid(-1.55F, 20.6F, -0.3F, 3.1F, 3.6F, 0.6F)
				.uv(48, 0).cuboid(-1.15F, 24.2F, -0.27F, 2.3F, 2F, 0.54F)
				.uv(48, 0).cuboid(-0.7F, 26.2F, -0.24F, 1.4F, 1.6F, 0.48F)
				.uv(48, 0).cuboid(-0.3F, 27.8F, -0.2F, 0.6F, 1.1F, 0.4F)
				.uv(48, 64).cuboid(-0.22F, 20F, -0.36F, 0.44F, 7.4F, 0.72F),
				ModelTransform.of(GRIP_X, GRIP_Y, GRIP_Z, 0F, 0F, DIAGONAL * (float) (Math.PI / 180.0)));
		return TexturedModelData.of(data, 256, 128);
	}

	private static TexturedModelData longsword() {
		ModelData data = new ModelData();
		ModelPartData root = data.getRoot();
		root.addChild("weapon", ModelPartBuilder.create()
				.uv(128, 0).cuboid(-1.2F, -7F, -1.2F, 2.4F, 1.4F, 2.4F)
				.uv(128, 0).cuboid(-0.75F, -7.4F, -0.75F, 1.5F, 0.4F, 1.5F)
				.uv(128, 64).cuboid(-0.7F, -5.6F, -0.7F, 1.4F, 5.8F, 1.4F)
				.uv(128, 0).cuboid(-4.2F, 0.2F, -0.8F, 8.4F, 1F, 1.6F)
				.uv(128, 0).cuboid(-4.8F, -0.1F, -0.9F, 0.8F, 1.6F, 1.8F)
				.uv(128, 0).cuboid(4F, -0.1F, -0.9F, 0.8F, 1.6F, 1.8F)
				.uv(128, 0).cuboid(-1.4F, 1.2F, -0.5F, 2.8F, 0.9F, 1F)
				.uv(48, 0).cuboid(-1.2F, 2.1F, -0.35F, 2.4F, 15.4F, 0.7F)
				.uv(48, 64).cuboid(-1.32F, 2.1F, -0.2F, 0.14F, 15.4F, 0.4F)
				.uv(48, 64).cuboid(1.18F, 2.1F, -0.2F, 0.14F, 15.4F, 0.4F)
				.uv(192, 32).cuboid(-0.3F, 2.4F, -0.38F, 0.6F, 12F, 0.76F)
				.uv(48, 0).cuboid(-0.9F, 17.5F, -0.3F, 1.8F, 1.4F, 0.6F)
				.uv(48, 0).cuboid(-0.52F, 18.9F, -0.25F, 1.04F, 1.2F, 0.5F)
				.uv(48, 0).cuboid(-0.2F, 20.1F, -0.2F, 0.4F, 0.8F, 0.4F),
				ModelTransform.of(GRIP_X, GRIP_Y, GRIP_Z, 0F, 0F, DIAGONAL * (float) (Math.PI / 180.0)));
		return TexturedModelData.of(data, 256, 128);
	}

	private static TexturedModelData halberd() {
		ModelData data = new ModelData();
		ModelPartData root = data.getRoot();
		root.addChild("weapon", ModelPartBuilder.create()
				.uv(0, 0).cuboid(-0.7F, -12F, -0.7F, 1.4F, 34.5F, 1.4F)
				.uv(128, 0).cuboid(-0.9F, -13.2F, -0.9F, 1.8F, 1.3F, 1.8F)
				.uv(128, 64).cuboid(-0.8F, -3F, -0.8F, 1.6F, 5F, 1.6F)
				.uv(128, 0).cuboid(-0.9F, 13.5F, -0.9F, 1.8F, 0.9F, 1.8F)
				.uv(128, 0).cuboid(-0.9F, 21.6F, -0.9F, 1.8F, 0.9F, 1.8F)
				.uv(48, 0).cuboid(-3.2F, 15.4F, -0.3F, 2.5F, 5.6F, 0.6F)
				.uv(48, 0).cuboid(-5.6F, 13.8F, -0.28F, 2.4F, 8.8F, 0.56F)
				.uv(48, 0).cuboid(-6.9F, 12.8F, -0.25F, 1.3F, 10.8F, 0.5F)
				.uv(48, 64).cuboid(-7.1F, 13F, -0.15F, 0.2F, 10.4F, 0.3F)
				.uv(48, 0).cuboid(0.7F, 18.4F, -0.3F, 3.4F, 1.1F, 0.6F)
				.uv(48, 0).cuboid(3.7F, 17.2F, -0.26F, 1F, 1.4F, 0.52F)
				.uv(48, 0).cuboid(4.1F, 16.4F, -0.22F, 0.6F, 1F, 0.44F)
				.uv(48, 0).cuboid(-0.6F, 22.5F, -0.35F, 1.2F, 5.6F, 0.7F)
				.uv(48, 0).cuboid(-0.35F, 28.1F, -0.25F, 0.7F, 1.8F, 0.5F),
				ModelTransform.of(GRIP_X, GRIP_Y, GRIP_Z, 0F, 0F, DIAGONAL * (float) (Math.PI / 180.0)));
		return TexturedModelData.of(data, 256, 128);
	}

	private static TexturedModelData warhammer() {
		ModelData data = new ModelData();
		ModelPartData root = data.getRoot();
		root.addChild("weapon", ModelPartBuilder.create()
				.uv(0, 0).cuboid(-0.7F, -5.5F, -0.7F, 1.4F, 25.5F, 1.4F)
				.uv(128, 0).cuboid(-0.9F, -6.6F, -0.9F, 1.8F, 1.2F, 1.8F)
				.uv(128, 64).cuboid(-0.8F, -4.5F, -0.8F, 1.6F, 7F, 1.6F)
				.uv(128, 0).cuboid(-0.85F, 9F, -0.85F, 1.7F, 0.8F, 1.7F)
				.uv(128, 0).cuboid(-1F, 16.6F, -1F, 2F, 5F, 2F)
				.uv(48, 0).cuboid(-4.6F, 17.6F, -1.3F, 3.6F, 3.4F, 2.6F)
				.uv(48, 64).cuboid(-5.1F, 17.4F, -1.5F, 0.5F, 3.8F, 3F)
				.uv(48, 0).cuboid(1F, 18.3F, -0.7F, 3F, 2F, 1.4F)
				.uv(48, 0).cuboid(4F, 18.7F, -0.45F, 1.6F, 1.2F, 0.9F)
				.uv(48, 0).cuboid(5.6F, 19F, -0.25F, 0.8F, 0.6F, 0.5F)
				.uv(48, 0).cuboid(-0.45F, 21.6F, -0.45F, 0.9F, 2.6F, 0.9F),
				ModelTransform.of(GRIP_X, GRIP_Y, GRIP_Z, 0F, 0F, DIAGONAL * (float) (Math.PI / 180.0)));
		return TexturedModelData.of(data, 256, 128);
	}

	private static TexturedModelData flail() {
		ModelData data = new ModelData();
		ModelPartData root = data.getRoot();
		root.addChild("weapon", ModelPartBuilder.create()
				.uv(0, 0).cuboid(-0.8F, -4.2F, -0.8F, 1.6F, 10.6F, 1.6F)
				.uv(128, 64).cuboid(-0.9F, -3.2F, -0.9F, 1.8F, 5F, 1.8F)
				.uv(128, 0).cuboid(-1.1F, -5.2F, -1.1F, 2.2F, 1.1F, 2.2F)
				.uv(128, 0).cuboid(-1.1F, 6.4F, -1.1F, 2.2F, 1.2F, 2.2F)
				.uv(128, 0).cuboid(-0.25F, 7.6F, -0.6F, 0.5F, 0.8F, 1.2F),
				ModelTransform.of(GRIP_X, GRIP_Y, GRIP_Z, 0F, 0F, DIAGONAL * (float) (Math.PI / 180.0)));
		root.addChild("chain", ModelPartBuilder.create()
				.uv(192, 0).cuboid(-0.4F, -1.3F, -0.12F, 0.8F, 1.4F, 0.24F)
				.uv(192, 0).cuboid(-0.12F, -2.6F, -0.4F, 0.24F, 1.4F, 0.8F)
				.uv(192, 0).cuboid(-0.4F, -3.9F, -0.12F, 0.8F, 1.4F, 0.24F)
				.uv(192, 0).cuboid(-0.12F, -5.2F, -0.4F, 0.24F, 1.4F, 0.8F)
				.uv(192, 0).cuboid(-0.4F, -6.5F, -0.12F, 0.8F, 1.4F, 0.24F)
				.uv(192, 0).cuboid(-0.12F, -7.8F, -0.4F, 0.24F, 1.4F, 0.8F)
				.uv(48, 0).cuboid(-1.7F, -11.5F, -1.7F, 3.4F, 3.4F, 3.4F)
				.uv(48, 0).cuboid(-1.3F, -11.9F, -1.3F, 2.6F, 4.2F, 2.6F)
				.uv(48, 0).cuboid(-2.1F, -11.1F, -1.3F, 4.2F, 2.6F, 2.6F)
				.uv(48, 0).cuboid(-1.3F, -11.1F, -2.1F, 2.6F, 2.6F, 4.2F)
				.uv(48, 64).cuboid(2.1F, -10.15F, -0.35F, 1.2F, 0.7F, 0.7F)
				.uv(48, 64).cuboid(-3.3F, -10.15F, -0.35F, 1.2F, 0.7F, 0.7F)
				.uv(48, 64).cuboid(-0.35F, -10.15F, 2.1F, 0.7F, 0.7F, 1.2F)
				.uv(48, 64).cuboid(-0.35F, -10.15F, -3.3F, 0.7F, 0.7F, 1.2F)
				.uv(48, 64).cuboid(-0.35F, -13.1F, -0.35F, 0.7F, 1.2F, 0.7F)
				.uv(48, 64).cuboid(1.2F, -8.6F, -0.3F, 0.6F, 0.6F, 0.6F)
				.uv(48, 64).cuboid(-1.8F, -8.6F, -0.3F, 0.6F, 0.6F, 0.6F)
				.uv(48, 64).cuboid(1.2F, -11.6F, -0.3F, 0.6F, 0.6F, 0.6F)
				.uv(48, 64).cuboid(-1.8F, -11.6F, -0.3F, 0.6F, 0.6F, 0.6F),
				ModelTransform.NONE);
		return TexturedModelData.of(data, 256, 128);
	}

	private static TexturedModelData battleAxe() {
		ModelData data = new ModelData();
		ModelPartData root = data.getRoot();
		root.addChild("weapon", ModelPartBuilder.create()
				.uv(0, 0).cuboid(-0.75F, -5F, -0.75F, 1.5F, 25F, 1.5F)
				.uv(128, 0).cuboid(-0.9F, -6.1F, -0.9F, 1.8F, 1.2F, 1.8F)
				.uv(128, 64).cuboid(-0.85F, -4F, -0.85F, 1.7F, 5.5F, 1.7F)
				.uv(128, 0).cuboid(-1.1F, 15F, -1.1F, 2.2F, 5F, 2.2F)
				.uv(128, 0).cuboid(-0.8F, 20F, -0.8F, 1.6F, 0.8F, 1.6F)
				.uv(48, 0).cuboid(-3.1F, 15.6F, -0.42F, 2F, 3.6F, 0.84F)
				.uv(48, 0).cuboid(-5.4F, 13.4F, -0.36F, 2.3F, 8.2F, 0.72F)
				.uv(48, 0).cuboid(-7F, 11.6F, -0.3F, 1.6F, 11.6F, 0.6F)
				.uv(48, 0).cuboid(-4.3F, 12.2F, -0.33F, 1F, 1.4F, 0.66F)
				.uv(48, 64).cuboid(-7.35F, 11.9F, -0.16F, 0.35F, 11F, 0.32F)
				.uv(128, 0).cuboid(1.1F, 16F, -0.95F, 1.6F, 2.8F, 1.9F),
				ModelTransform.of(GRIP_X, GRIP_Y, GRIP_Z, 0F, 0F, DIAGONAL * (float) (Math.PI / 180.0)));
		return TexturedModelData.of(data, 256, 128);
	}
}
