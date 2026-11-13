package com.evolvedwarfare.client.town;

import com.evolvedwarfare.town.Dialogue;
import com.evolvedwarfare.town.NpcProfile;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * La pantalla para hablar con un NPC (fase 61): a la izquierda su retrato (te sigue con la mirada), su nombre, su oficio y
 * carácter, lo que piensa de ti y lo que está haciendo; a la derecha lo que te contesta y debajo lo que le puedes decir.
 */
public class DialogueScreen extends Screen {
	private static final int W = 330, H = 240;
	private static final int PANEL = 0xE0181410, FRAME = 0xFF8A6A3A, SPEECH = 0xF0EFE6D2, INK = 0xFF2A2018;

	private Dialogue.StatePayload state;
	private int left, top;

	public DialogueScreen(Dialogue.StatePayload state) {
		super(Text.translatable("dialogue.evolvedwarfare.title"));
		this.state = state;
	}

	public int entityId() {
		return state.entityId();
	}

	/** Nueva respuesta del mismo NPC: se cambia el texto y las opciones sin cerrar la pantalla. */
	public void update(Dialogue.StatePayload next) {
		this.state = next;
		clearAndInit();
	}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(Dialogue.StatePayload.ID, (payload, context) -> {
			MinecraftClient client = context.client();
			if (client.currentScreen instanceof DialogueScreen open && open.entityId() == payload.entityId()) {
				open.update(payload);
			} else {
				client.setScreen(new DialogueScreen(payload));
			}
		});
	}

	@Override
	protected void init() {
		left = (width - W) / 2;
		top = (height - H) / 2;
		List<Integer> options = state.options();
		for (int i = 0; i < options.size(); i++) {
			Dialogue.Option option = Dialogue.Option.values()[options.get(i)];
			Text label = option == Dialogue.Option.HIRE_YES
					? Text.translatable("dialogue.evolvedwarfare.option.hire_yes",
							state.hireCoins() > 0 ? Text.translatable("dialogue.evolvedwarfare.cost.coins", state.hireCoins())
									: Text.translatable("dialogue.evolvedwarfare.cost.contract"))
					: Text.translatable("dialogue.evolvedwarfare.option." + option.id());
			// en dos columnas, debajo de lo que dice
			int bx = left + 8 + (i % 2) * 159, by = top + 130 + (i / 2) * 20;
			addDrawableChild(ButtonWidget.builder(label, b -> choose(option)).dimensions(bx, by, 155, 18).build());
		}
	}

	private void choose(Dialogue.Option option) {
		ClientPlayNetworking.send(new Dialogue.ActionPayload(state.entityId(), option.ordinal()));
		if (option == Dialogue.Option.BYE || option == Dialogue.Option.GEAR || option == Dialogue.Option.TRADE || option == Dialogue.Option.HIRE_YES) {
			// la pantalla siguiente (o ninguna) la decide el servidor; si se contrata, no vuelve
			if (option == Dialogue.Option.BYE) {
				close();
			}
		}
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		super.render(context, mouseX, mouseY, delta);
		MinecraftClient client = MinecraftClient.getInstance();
		Entity entity = client.world == null ? null : client.world.getEntityById(state.entityId());
		if (entity == null || !entity.isAlive() || client.player != null && client.player.squaredDistanceTo(entity) > 10 * 10) {
			close();
			return;
		}
		int portraitBottom = top + 122;
		// el retrato
		if (entity instanceof LivingEntity living) {
			InventoryScreen.drawEntity(context, left + 8, top + 8, left + 84, portraitBottom, 36, 0.0625F, mouseX, mouseY, living);
		}
		// quién es
		int tx = left + 90, ty = top + 10;
		context.drawText(textRenderer, entity.getName(), tx, ty, 0xFFFFE6A0, true);
		context.drawText(textRenderer, NpcProfile.describe(state.profileBits()), tx, ty + 12, 0xFFD8D0C0, false);
		int mood = state.mood();
		int moodColor = mood >= 15 ? 0xFF80E070 : mood > -15 ? 0xFFD8D0C0 : 0xFFE07060;
		context.drawText(textRenderer, Dialogue.moodLabel(mood), tx, ty + 24, moodColor, false);
		context.drawText(textRenderer, state.activity().copy().formatted(Formatting.ITALIC), tx, ty + 36, 0xFFB0A898, false);
		// lo que dice
		int sx = tx - 2, sy = ty + 50, sw = left + W - 8 - sx;
		List<OrderedText> lines = textRenderer.wrapLines(state.reply(), sw - 10);
		int maxLines = 6;
		int sh = Math.min(lines.size(), maxLines) * 10 + 8;
		context.fill(sx, sy, sx + sw, sy + sh, SPEECH);
		for (int i = 0; i < Math.min(lines.size(), maxLines); i++) {
			context.drawText(textRenderer, lines.get(i), sx + 5, sy + 5 + i * 10, INK, false);
		}
	}

	@Override
	public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
		super.renderBackground(context, mouseX, mouseY, delta);
		context.fill(left - 1, top - 1, left + W + 1, top + H + 1, FRAME);
		context.fill(left, top, left + W, top + H, PANEL);
	}

	@Override
	public boolean shouldPause() {
		return false;
	}
}
