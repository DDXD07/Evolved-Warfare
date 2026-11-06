package com.evolvedwarfare.client.smithing;

import com.evolvedwarfare.smithing.CrucibleContents;
import com.evolvedwarfare.smithing.CrucibleItem;
import com.evolvedwarfare.smithing.MoldBlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Util;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;

/**
 * El medidor del vertido, junto a la mira: mirando un molde con el crisol lleno en la mano, una barra que se llena con el
 * metal, la raya de lo justo y la franja verde (lo perfecto); por encima, en rojo, lo que rebosa. Al lado, lo que queda en el
 * crisol. Sin texto.
 */
public final class PourGauge {
	private PourGauge() {
	}

	public static void render(DrawContext ctx) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.player == null || client.world == null || client.options.hudHidden || client.currentScreen != null) {
			return;
		}
		ItemStack held = client.player.getMainHandStack();
		if (!(held.getItem() instanceof CrucibleItem)) {
			return;
		}
		HitResult hit = client.crosshairTarget;
		if (!(hit instanceof BlockHitResult bhr) || hit.getType() != HitResult.Type.BLOCK
				|| !(client.world.getBlockEntity(bhr.getBlockPos()) instanceof MoldBlockEntity mold)) {
			return;
		}
		int stage = mold.stage();
		if (stage != MoldBlockEntity.CARVED && stage != MoldBlockEntity.POURING && stage != MoldBlockEntity.COOLING) {
			return;
		}
		CrucibleContents c = CrucibleItem.contents(held);
		int x = ctx.getScaledWindowWidth() / 2 + 14;
		int h = 56;
		int y = ctx.getScaledWindowHeight() / 2 - h / 2;
		float max = MoldBlockEntity.OVERFLOW;
		float ratio = mold.fill() / mold.target();
		ctx.fill(x - 1, y - 1, x + 9, y + h + 1, 0xC0000000);
		ctx.fill(x, y, x + 8, y + h, 0xFF2A2420);
		// la zona de rebose (roja) y la perfecta (verde)
		int full = y + h - (int) (h / max);
		ctx.fill(x, y, x + 8, full - (int) (0.03F * h / max), 0x60D03020);
		ctx.fill(x, full - (int) (0.03F * h / max), x + 8, full + (int) (0.03F * h / max) + 1, 0xA040D040);
		// el metal
		int level = (int) (Math.min(max, ratio) / max * h);
		boolean perfect = ratio >= 0.97F && ratio <= 1.03F;
		float pulse = 0.5F + 0.5F * (float) Math.sin(Util.getMeasuringTimeMs() / 90.0);
		int metal = stage == MoldBlockEntity.COOLING ? 0xFF9A6050 : ratio > 1.03F ? 0xFFFF5020 : perfect ? (pulse > 0.5F ? 0xFFFFF080 : 0xFFFFC040) : 0xFFFF9030;
		ctx.fill(x + 1, y + h - level, x + 7, y + h, metal);
		// la raya de lo justo
		ctx.fill(x - 3, full, x + 11, full + 1, 0xFFFFFFFF);
		// lo que queda en el crisol
		int left = (int) (Math.min(4.0F, c.molten()) / 4.0F * h);
		ctx.fill(x + 12, y, x + 15, y + h, 0xFF2A2420);
		ctx.fill(x + 12, y + h - left, x + 15, y + h, 0xFFE07020);
	}
}
