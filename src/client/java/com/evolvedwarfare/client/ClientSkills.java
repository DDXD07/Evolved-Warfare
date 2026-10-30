package com.evolvedwarfare.client;

import com.evolvedwarfare.ClientBridge;
import com.evolvedwarfare.client.screen.SkillScreen;
import com.evolvedwarfare.network.SkillPayloads;
import com.evolvedwarfare.skill.Perk;
import com.evolvedwarfare.skill.Skill;
import com.evolvedwarfare.skill.SkillData;
import com.evolvedwarfare.skill.Skills;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

/**
 * El árbol de habilidades en el cliente: lo que manda el servidor, la tecla K para abrirlo, los avisos de arriba al subir
 * una habilidad o de nivel (como en Skyrim) y la línea roja en la descripción de lo que aún no puedes fabricar.
 */
public final class ClientSkills {
	public static SkillData data = new SkillData();
	public static boolean required = true;
	private static boolean received;
	public static KeyBinding open;

	/** Un aviso de arriba: el texto, su color y cuándo salió. */
	private record Notice(Text text, int color, long since, boolean big) {
	}

	private static final List<Notice> NOTICES = new ArrayList<>();
	private static final long NOTICE_MS = 3500L;

	private ClientSkills() {
	}

	public static void register() {
		open = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.evolvedwarfare.skills", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_K,
				"key.categories.evolvedwarfare"));
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (open.wasPressed()) {
				if (client.player != null && client.currentScreen == null) {
					client.setScreen(new SkillScreen());
				}
			}
		});
		ClientPlayNetworking.registerGlobalReceiver(SkillPayloads.Data.ID, (payload, context) -> {
			SkillData next = SkillData.fromNbt(payload.data());
			if (received) {
				notices(data, next);
			}
			data = next;
			required = payload.required();
			received = true;
			if (payload.open()) {
				context.client().setScreen(new SkillScreen());
			}
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			data = new SkillData();
			received = false;
			NOTICES.clear();
		});
		ClientBridge.clientPerkRank = (player, perk) -> player == MinecraftClient.getInstance().player ? data.rank(perk) : 0;
		ItemTooltipCallback.EVENT.register((stack, tooltipContext, type, lines) -> {
			Perk perk = Skills.lockOf(stack.getItem());
			MinecraftClient client = MinecraftClient.getInstance();
			if (perk != null && required && data.rank(perk) == 0 && client.player != null && !client.player.isCreative()) {
				lines.add(Text.translatable("tooltip.evolvedwarfare.skills.needed", Text.translatable(perk.translationKey()),
						Text.translatable(perk.skill.translationKey()), perk.levelFor(1)).formatted(Formatting.RED));
			}
		});
		HudRenderCallback.EVENT.register((context, tickCounter) -> hud(context));
	}

	/** Lo que ha subido desde la última vez: un aviso por habilidad y otro, grande, por nivel. */
	private static void notices(SkillData before, SkillData after) {
		long now = System.currentTimeMillis();
		for (Skill skill : Skill.values()) {
			if (after.level(skill) > before.level(skill)) {
				NOTICES.add(new Notice(Text.translatable("hud.evolvedwarfare.skills.skill_up", Text.translatable(skill.translationKey()),
						after.level(skill)), skill.color, now, false));
			}
		}
		if (after.level > before.level) {
			NOTICES.add(new Notice(Text.translatable("hud.evolvedwarfare.skills.level_up", after.level, open.getBoundKeyLocalizedText()),
					0xFFD75E, now, true));
		}
		while (NOTICES.size() > 4) {
			NOTICES.remove(0);
		}
	}

	private static void hud(DrawContext context) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (NOTICES.isEmpty() || client.options.hudHidden || client.currentScreen instanceof SkillScreen) {
			return;
		}
		long now = System.currentTimeMillis();
		NOTICES.removeIf(n -> now - n.since() > NOTICE_MS);
		int y = 30;
		int x = context.getScaledWindowWidth() / 2;
		for (Notice notice : NOTICES) {
			float age = (now - notice.since()) / (float) NOTICE_MS;
			float alpha = age < 0.1F ? age / 0.1F : age > 0.75F ? (1.0F - age) / 0.25F : 1.0F;
			int a = MathHelper.clamp((int) (alpha * 255), 8, 255);
			int color = (a << 24) | (notice.color() & 0xFFFFFF);
			if (notice.big()) {
				context.getMatrices().push();
				context.getMatrices().translate(x, y, 0.0F);
				context.getMatrices().scale(1.6F, 1.6F, 1.0F);
				context.drawCenteredTextWithShadow(client.textRenderer, notice.text(), 0, 0, color);
				context.getMatrices().pop();
				y += 20;
			} else {
				context.drawCenteredTextWithShadow(client.textRenderer, notice.text(), x, y, color);
				y += 12;
			}
		}
	}
}
