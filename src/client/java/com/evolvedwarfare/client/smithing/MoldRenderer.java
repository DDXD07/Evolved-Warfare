package com.evolvedwarfare.client.smithing;

import com.evolvedwarfare.smithing.ForgeMaterial;
import com.evolvedwarfare.smithing.MoldBlockEntity;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;

/**
 * El molde de arcilla: una losa de 16x16 casillas. Las talladas se hunden (con sus paredes) y, al verter, se llenan de metal
 * que sube, brilla al rojo y se va apagando hasta quedarse del color del metal; lo que rebosa se derrama por encima. Al
 * golpearlo ya frío, la arcilla se va desmoronando y asoma la pieza.
 */
public class MoldRenderer implements BlockEntityRenderer<MoldBlockEntity> {
	private static final Identifier CLAY = Identifier.ofVanilla("block/clay");
	private static final Identifier LAVA = Identifier.ofVanilla("block/lava_still");
	private static final Identifier IRON = Identifier.ofVanilla("block/iron_block");
	private static final Identifier GOLD = Identifier.ofVanilla("block/gold_block");
	private static final float CELL = 14.0F / 16.0F / 16.0F;
	private static final float START = 1.0F / 16.0F;
	private static final float FLOOR = 2.0F / 16.0F;
	private static final float SURFACE = 4.0F / 16.0F;

	public MoldRenderer(BlockEntityRendererFactory.Context context) {
	}

	private static float hash(int x, int y, long seed) {
		long h = seed * 0x9E3779B97F4A7C15L + x * 73856093L + y * 19349663L;
		h ^= h >>> 29;
		h *= 0xBF58476D1CE4E5B9L;
		h ^= h >>> 32;
		return (h & 0xFFFF) / 65535.0F;
	}

	@Override
	public void render(MoldBlockEntity mold, float tickDelta, MatrixStack matrices, VertexConsumerProvider consumers, int light, int overlay) {
		VertexConsumer b = consumers.getBuffer(RenderLayer.getEntityCutoutNoCull(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE));
		MatrixStack.Entry e = matrices.peek();
		Sprite clay = Quads.sprite(CLAY);
		long seed = mold.getPos().asLong();
		int stage = mold.stage();
		float broken = stage == MoldBlockEntity.SOLID ? mold.cracks() / (float) MoldBlockEntity.CRACKS * 0.7F : 0.0F;
		boolean[][] solid = new boolean[16][16];
		for (int y = 0; y < 16; y++) {
			for (int x = 0; x < 16; x++) {
				solid[x][y] = !mold.isCarved(x, y) && hash(x, y, seed) >= broken;
			}
		}
		for (int y = 0; y < 16; y++) {
			for (int x = 0; x < 16; x++) {
				if (!solid[x][y]) {
					continue;
				}
				float x0 = START + x * CELL;
				float z0 = START + y * CELL;
				float x1 = x0 + CELL;
				float z1 = z0 + CELL;
				int col = Quads.shade(0xFFFFFFFF, 0.93F + hash(x, y, seed + 7) * 0.07F);
				Quads.top(b, e, clay, x0, z0, x1, z1, SURFACE, col, light, overlay);
				int wall = Quads.shade(0xFFFFFFFF, 0.62F);
				if (x > 0 && !solid[x - 1][y]) {
					Quads.side(b, e, clay, x0, z1, x0, z0, FLOOR, SURFACE, -1, 0, wall, light, overlay);
				}
				if (x < 15 && !solid[x + 1][y]) {
					Quads.side(b, e, clay, x1, z0, x1, z1, FLOOR, SURFACE, 1, 0, wall, light, overlay);
				}
				if (y > 0 && !solid[x][y - 1]) {
					Quads.side(b, e, clay, x0, z0, x1, z0, FLOOR, SURFACE, 0, -1, wall, light, overlay);
				}
				if (y < 15 && !solid[x][y + 1]) {
					Quads.side(b, e, clay, x1, z1, x0, z1, FLOOR, SURFACE, 0, 1, wall, light, overlay);
				}
			}
		}
		if (stage < MoldBlockEntity.POURING || mold.fill() <= 0.0F) {
			return;
		}
		// el metal
		ForgeMaterial m = ForgeMaterial.byId(mold.metal());
		float ratio = mold.fill() / mold.target();
		float heat = mold.heat();
		float y = FLOOR + (SURFACE - FLOOR - 0.15F / 16.0F) * Math.min(1.0F, ratio);
		Sprite sprite;
		int color;
		int glow;
		if (heat > 0.35F) {
			sprite = Quads.sprite(LAVA);
			color = Quads.lerpColor(0xFF8A2010, CrucibleRenderer.moltenColor(m), (heat - 0.35F) / 0.65F);
			glow = LightmapTextureManager.MAX_LIGHT_COORDINATE;
		} else {
			sprite = Quads.sprite(m == ForgeMaterial.GOLD ? GOLD : IRON);
			color = Quads.lerpColor(0xFFFFFFFF, 0xFFE06040, heat / 0.35F);
			glow = heat > 0.1F ? LightmapTextureManager.MAX_LIGHT_COORDINATE : light;
		}
		for (int cy = 0; cy < 16; cy++) {
			for (int cx = 0; cx < 16; cx++) {
				float x0 = START + cx * CELL;
				float z0 = START + cy * CELL;
				if (mold.isCarved(cx, cy)) {
					Quads.top(b, e, sprite, x0, z0, x0 + CELL, z0 + CELL, y, color, glow, overlay);
				} else if (!solid[cx][cy]) {
					// arcilla desmoronada: se ve el fondo (y nada de metal)
				} else if (ratio > 1.03F && nearCarved(mold, cx, cy) && hash(cx, cy, seed + 3) < (ratio - 1.0F) * 6.0F) {
					// lo que rebosa, por encima de la arcilla
					Quads.top(b, e, sprite, x0, z0, x0 + CELL, z0 + CELL, SURFACE + 0.1F / 16.0F, color, glow, overlay);
				}
			}
		}
	}

	private static boolean nearCarved(MoldBlockEntity mold, int x, int y) {
		for (int dy = -1; dy <= 1; dy++) {
			for (int dx = -1; dx <= 1; dx++) {
				int nx = x + dx;
				int ny = y + dy;
				if (nx >= 0 && ny >= 0 && nx < 16 && ny < 16 && mold.isCarved(nx, ny)) {
					return true;
				}
			}
		}
		return false;
	}
}
