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
 * Los bocadillos de lo que dicen los NPCs (fases 60 y 60.1): el texto encima de su cabeza, de cara a la cámara, con fondo
 * oscuro, partido en líneas cortas. Para que la pantalla no se llene de charlas, los de una charla solo se ven si estás a
 * {@link #RANGE} bloques o menos de uno de los dos y le miras (a cualquiera de los dos): entonces se ven los bocadillos
 * de ambos. Los saludos (a ti) se ven si estás cerca. Aparecen y se van con un fundido.
 */
public final class SpeechBubbles {
	/** Hasta dónde se ven (bloques). */
	public static final double RANGE = 5.0;

	private record Bubble(int partner, Text text, long until) {
	}

	private static final Map<Integer, Bubble> BUBBLES = new HashMap<>();
	/** Lo visible que está cada bocadillo ahora (0 a 1, para el fundido). */
	private static final Map<Integer, Float> SHOWN = new HashMap<>();
	private static long lastFrame;

	private SpeechBubbles() {
	}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(Chatter.SayPayload.ID, (payload, context) -> {
			MinecraftClient client = context.client();
			if (client.world != null) {
				BUBBLES.put(payload.entityId(), new Bubble(payload.partnerId(), payload.text(), client.world.getTime() + payload.ticks()));
			}
		});
		WorldRenderEvents.AFTER_ENTITIES.register(SpeechBubbles::render);
	}

	/** Lo que está diciendo ahora (o null), para otras pantallas. */
	public static Text current(int entityId) {
		Bubble b = BUBBLES.get(entityId);
		return b == null ? null : b.text();
	}

	/** ¿Le miras? (el rayo de la vista pasa por él) y está cerca. */
	private static boolean lookedAt(Entity e, Vec3d eye, Vec3d look) {
		if (e == null || e.squaredDistanceTo(eye) > (RANGE + 1.0) * (RANGE + 1.0)) {
			return false;
		}
		return e.getBoundingBox().expand(0.35).raycast(eye, eye.add(look.multiply(RANGE + 2.0))).isPresent();
	}

	private static boolean near(Entity e, Vec3d eye) {
		return e != null && e.squaredDistanceTo(eye) <= RANGE * RANGE;
	}

	/** ¿Se ve el bocadillo de este NPC ahora? */
	private static boolean visible(MinecraftClient client, Entity speaker, Bubble b, Vec3d eye, Vec3d look) {
		if (b.partner() < 0) {
			return near(speaker, eye);
		}
		Entity partner = client.world.getEntityById(b.partner());
		return (near(speaker, eye) || near(partner, eye)) && (lookedAt(speaker, eye, look) || lookedAt(partner, eye, look) || focused(client, b.partner()));
	}

	/** Si miras a su compañero y este también está hablando, cuenta como mirarle (los dos bocadillos de la charla). */
	private static boolean focused(MinecraftClient client, int partnerId) {
		return client.targetedEntity != null && client.targetedEntity.getId() == partnerId;
	}

	private static void render(WorldRenderContext ctx) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (BUBBLES.isEmpty() || client.world == null || client.player == null || ctx.consumers() == null || ctx.matrixStack() == null) {
			return;
		}
		long now = client.world.getTime();
		float delta = ctx.tickCounter().getTickDelta(true);
		Vec3d cam = ctx.camera().getPos();
		Vec3d eye = client.player.getCameraPosVec(delta);
		Vec3d look = client.player.getRotationVec(delta);
		// el fundido avanza con el tiempo real (no con los fotogramas)
		long ms = System.currentTimeMillis();
		float step = lastFrame == 0 ? 1.0F : MathHelper.clamp((ms - lastFrame) / 200.0F, 0.0F, 1.0F);
		lastFrame = ms;
		TextRenderer font = client.textRenderer;
		MatrixStack m = ctx.matrixStack();
		for (Iterator<Map.Entry<Integer, Bubble>> it = BUBBLES.entrySet().iterator(); it.hasNext();) {
			Map.Entry<Integer, Bubble> e = it.next();
			Bubble b = e.getValue();
			Entity entity = client.world.getEntityById(e.getKey());
			if (now >= b.until() || entity == null || entity.isRemoved()) {
				it.remove();
				SHOWN.remove(e.getKey());
				continue;
			}
			float shown = SHOWN.getOrDefault(e.getKey(), 0.0F);
			shown = visible(client, entity, b, eye, look) ? Math.min(1.0F, shown + step) : Math.max(0.0F, shown - step);
			SHOWN.put(e.getKey(), shown);
			float left = b.until() - now - delta;
			int alpha = (int) (255 * shown * MathHelper.clamp(left / 10.0F, 0.0F, 1.0F));
			if (alpha < 8) {
				continue;
			}
			Vec3d pos = entity.getLerpedPos(delta);
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
