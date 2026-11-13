package com.evolvedwarfare.client.town;

import com.evolvedwarfare.town.Chatter;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

/**
 * Los bocadillos de lo que dicen los NPCs (fase 60): el texto encima de su cabeza, de cara a la cámara, con fondo
 * oscuro, partido en líneas cortas; dura unos segundos y se desvanece al final.
 */
public final class SpeechBubbles {
	private record Bubble(Text text, long until, int ticks) {
	}

	private static final Map<Integer, Bubble> BUBBLES = new HashMap<>();

	private SpeechBubbles() {
	}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(Chatter.SayPayload.ID, (payload, context) -> {
			MinecraftClient client = context.client();
			if (client.world != null) {
				BUBBLES.put(payload.entityId(), new Bubble(payload.text(), client.world.getTime() + payload.ticks(), payload.ticks()));
			}
		});
		WorldRenderEvents.AFTER_ENTITIES.register(SpeechBubbles::render);
	}

	/** Lo que está diciendo ahora (o null), para otras pantallas. */
	public static Text current(int entityId) {
		Bubble b = BUBBLES.get(entityId);
		return b == null ? null : b.text();
	}

	private static void render(WorldRenderContext ctx) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (BUBBLES.isEmpty() || client.world == null || ctx.consumers() == null || ctx.matrixStack() == null) {
			return;
		}
		long now = client.world.getTime();
		float delta = ctx.tickCounter().getTickDelta(true);
		Vec3d cam = ctx.camera().getPos();
		TextRenderer font = client.textRenderer;
		MatrixStack m = ctx.matrixStack();
		for (Iterator<Map.Entry<Integer, Bubble>> it = BUBBLES.entrySet().iterator(); it.hasNext();) {
			Map.Entry<Integer, Bubble> e = it.next();
			Bubble b = e.getValue();
			Entity entity = client.world.getEntityById(e.getKey());
			if (now >= b.until() || entity == null || entity.isRemoved()) {
				it.remove();
				continue;
			}
			Vec3d pos = entity.getLerpedPos(delta);
			if (pos.squaredDistanceTo(cam) > 24 * 24) {
				continue;
			}
			float left = b.until() - now - delta;
			int alpha = (int) (255 * MathHelper.clamp(left / 10.0F, 0.0F, 1.0F));
			if (alpha < 8) {
				continue;
			}
			List<OrderedText> lines = font.wrapLines(b.text(), 150);
			m.push();
			m.translate(pos.x - cam.x, pos.y - cam.y + entity.getHeight() + 0.85, pos.z - cam.z);
			m.multiply(ctx.camera().getRotation());
			m.scale(0.025F, -0.025F, 0.025F);
			Matrix4f matrix = m.peek().getPositionMatrix();
			int background = (int) (alpha * 0.55F) << 24;
			int color = alpha << 24 | 0xFFFFFF;
			float y = -10.0F * lines.size();
			for (OrderedText line : lines) {
				float x = -font.getWidth(line) / 2.0F;
				font.draw(line, x, y, color, false, matrix, ctx.consumers(), TextRenderer.TextLayerType.NORMAL, background,
						LightmapTextureManager.MAX_LIGHT_COORDINATE);
				y += 10.0F;
			}
			m.pop();
		}
	}
}
