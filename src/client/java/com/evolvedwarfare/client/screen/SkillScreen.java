package com.evolvedwarfare.client.screen;

import com.evolvedwarfare.client.ClientSkills;
import com.evolvedwarfare.network.SkillPayloads;
import com.evolvedwarfare.skill.Perk;
import com.evolvedwarfare.skill.Skill;
import com.evolvedwarfare.skill.SkillData;
import com.evolvedwarfare.skill.Skills;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundEvents;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.text.MutableText;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;
import org.jetbrains.annotations.Nullable;

/**
 * El árbol de habilidades, como en Skyrim: cielo estrellado, la constelación de la habilidad elegida (cada estrella es
 * una ventaja: dorada si la tienes, blanca si la puedes coger, gris si aún no), arriba tu nivel y tus puntos, y abajo las
 * once habilidades con su nivel. Clic en una estrella para cogerla; flechas o clic abajo para cambiar de habilidad.
 */
public class SkillScreen extends Screen {
	private static Skill selected = Skill.BLADES;
	private static final int TAB = 34;
	private static final int GROUP_GAP = 8;

	@Nullable
	private Perk hovered;
	@Nullable
	private Skill hoveredTab;

	public SkillScreen() {
		super(Text.translatable("screen.evolvedwarfare.skills.title"));
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	// ------------------------------------------------------------------ medidas

	private int top() {
		return 58;
	}

	private int bottom() {
		return height - 64;
	}

	private int cellW() {
		return Math.min(74, Math.max(40, (width - 40) / Perk.COLUMNS));
	}

	private int cellH() {
		return Math.max(24, (bottom() - top()) / Perk.ROWS);
	}

	private int nodeX(Perk perk) {
		return width / 2 + (perk.column - 2) * cellW();
	}

	private int nodeY(Perk perk) {
		return bottom() - cellH() / 2 - perk.row * cellH();
	}

	private int tabsWidth() {
		return Skill.values().length * TAB + 3 * GROUP_GAP;
	}

	private int tabX(Skill skill) {
		int x = (width - tabsWidth()) / 2 + skill.ordinal() * TAB;
		return x + skill.group.ordinal() * GROUP_GAP;
	}

	private int tabY() {
		return height - 44;
	}

	// ------------------------------------------------------------------ dibujo

	@Override
	public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
		context.fillGradient(0, 0, width, height, 0xFF04060E, 0xFF0D1428);
		// un velo del color de la habilidad, más fuerte en el centro
		int c = selected.color & 0xFFFFFF;
		for (int i = 0; i < 5; i++) {
			int inset = i * Math.min(width, height) / 10;
			context.fill(inset, inset / 2, width - inset, height - inset / 2, (0x08 << 24) | c);
		}
		// estrellas de fondo (siempre las mismas para cada habilidad), que titilan
		long seed = 0x9E3779B97F4A7C15L * (selected.ordinal() + 7);
		double time = System.currentTimeMillis() / 1000.0;
		for (int i = 0; i < 160; i++) {
			seed = seed * 6364136223846793005L + 1442695040888963407L;
			int x = (int) ((seed >>> 33) % Math.max(1, width));
			seed = seed * 6364136223846793005L + 1442695040888963407L;
			int y = (int) ((seed >>> 33) % Math.max(1, height));
			double twinkle = 0.55 + 0.45 * Math.sin(time * (0.6 + (i % 7) * 0.3) + i);
			int a = (int) (60 + 150 * twinkle * ((i % 5) / 4.0 * 0.6 + 0.4));
			context.fill(x, y, x + 1, y + 1, (a << 24) | 0xDCE6FF);
			if (i % 23 == 0) {
				context.fill(x - 1, y, x + 2, y + 1, (a / 3 << 24) | 0xDCE6FF);
				context.fill(x, y - 1, x + 1, y + 2, (a / 3 << 24) | 0xDCE6FF);
			}
		}
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		super.render(context, mouseX, mouseY, delta);
		SkillData data = ClientSkills.data;
		boolean creative = client != null && client.player != null && client.player.isCreative();
		hovered = null;
		hoveredTab = null;

		header(context, data);
		List<Perk> perks = Perk.of(selected);
		// líneas entre estrellas
		for (Perk perk : perks) {
			for (Perk before : perk.requires()) {
				boolean lit = data.rank(perk) > 0 && data.rank(before) > 0;
				int color = lit ? (0xEE << 24) | (selected.color & 0xFFFFFF) : data.rank(before) > 0 ? 0x99C8D2E6 : 0x445A6478;
				line(context, nodeX(before), nodeY(before), nodeX(perk), nodeY(perk), color);
			}
		}
		// las estrellas
		for (Perk perk : perks) {
			int x = nodeX(perk);
			int y = nodeY(perk);
			boolean over = Math.abs(mouseX - x) <= 8 && Math.abs(mouseY - y) <= 8;
			if (over) {
				hovered = perk;
			}
			star(context, perk, data, creative, x, y, over);
		}
		tabs(context, data, mouseX, mouseY);
		context.drawCenteredTextWithShadow(textRenderer, Text.translatable("screen.evolvedwarfare.skills.hint").formatted(Formatting.DARK_GRAY),
				width / 2, height - 10, 0xFFFFFF);
		if (hovered != null) {
			context.drawTooltip(textRenderer, describe(hovered, data, creative), mouseX, mouseY);
		} else if (hoveredTab != null) {
			List<Text> lines = new ArrayList<>();
			lines.add(Text.translatable(hoveredTab.translationKey()).formatted(Formatting.GOLD));
			lines.add(Text.translatable("screen.evolvedwarfare.skills.skill_level", data.level(hoveredTab)).formatted(Formatting.WHITE));
			lines.add(Text.translatable(hoveredTab.translationKey() + ".desc").formatted(Formatting.GRAY));
			context.drawTooltip(textRenderer, lines, mouseX, mouseY);
		}
	}

	private void header(DrawContext context, SkillData data) {
		// nivel de personaje y su barra, a la izquierda; puntos, a la derecha
		Text level = Text.translatable("screen.evolvedwarfare.skills.level", data.level);
		context.drawTextWithShadow(textRenderer, level, 12, 10, 0xFFE6D2A0);
		float next = SkillData.levelXpToNext(data.level);
		bar(context, 12, 22, 110, 4, data.levelXp / next, 0xFF6FA8DC);
		Text points = Text.translatable("screen.evolvedwarfare.skills.points", data.points);
		context.drawTextWithShadow(textRenderer, points, width - 12 - textRenderer.getWidth(points), 10,
				data.points > 0 ? 0xFFFFD75E : 0xFF9AA0AA);
		// la habilidad elegida, en grande, con su nivel y su barra
		context.getMatrices().push();
		context.getMatrices().translate(width / 2.0F, 12.0F, 0.0F);
		context.getMatrices().scale(1.8F, 1.8F, 1.0F);
		Text name = Text.translatable(selected.translationKey()).formatted(Formatting.BOLD);
		context.drawCenteredTextWithShadow(textRenderer, name, 0, 0, 0xFF000000 | selected.color);
		context.getMatrices().pop();
		int skillLevel = data.level(selected);
		Text sub = Text.translatable("screen.evolvedwarfare.skills.skill_level", skillLevel);
		context.drawCenteredTextWithShadow(textRenderer, sub, width / 2, 32, 0xFFE0E6F0);
		float progress = skillLevel >= Skill.MAX_LEVEL ? 1.0F : data.xp(selected) / Skill.xpToNext(skillLevel);
		bar(context, width / 2 - 60, 44, 120, 3, progress, 0xFF000000 | selected.color);
	}

	private static void bar(DrawContext context, int x, int y, int w, int h, float progress, int color) {
		context.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xAA000000);
		context.fill(x, y, x + w, y + h, 0x55303848);
		context.fill(x, y, x + Math.round(w * MathHelper.clamp(progress, 0.0F, 1.0F)), y + h, color);
	}

	/** Una estrella: dorada (la tienes), blanca (la puedes coger), gris (aún no); con su rango y su nombre debajo. */
	private void star(DrawContext context, Perk perk, SkillData data, boolean creative, int x, int y, boolean over) {
		int rank = data.rank(perk);
		Skills.Unlock state = Skills.check(data, perk, creative);
		boolean owned = rank > 0;
		boolean open = state == Skills.Unlock.OK || (state == Skills.Unlock.POINTS);
		int core = owned ? 0xFFFFE08A : open ? 0xFFF2F6FF : 0xFF5C6474;
		int glow = owned ? (selected.color & 0xFFFFFF) : open ? 0xC8D8FF : 0x404858;
		double pulse = 0.5 + 0.5 * Math.sin(System.currentTimeMillis() / 400.0 + perk.ordinal());
		int glowA = owned ? 0x50 : open ? (int) (0x20 + 0x30 * pulse) : 0x10;
		if (over) {
			glowA = 0x90;
		}
		int r = over ? 9 : 7;
		context.fill(x - r, y - r, x + r + 1, y + r + 1, (glowA / 3 << 24) | glow);
		context.fill(x - r + 3, y - r + 3, x + r - 2, y + r - 2, (glowA << 24) | glow);
		// rayos
		int ray = owned || over ? 6 : 4;
		context.fill(x - ray, y, x + ray + 1, y + 1, core);
		context.fill(x, y - ray, x + 1, y + ray + 1, core);
		context.fill(x - 1, y - 1, x + 2, y + 2, core);
		if (owned) {
			context.fill(x - 2, y - 2, x + 3, y + 3, (0x60 << 24) | 0xFFFFFF);
		}
		// nombre (pequeño) y rango
		context.getMatrices().push();
		context.getMatrices().translate(x, y + 10, 0.0F);
		context.getMatrices().scale(0.7F, 0.7F, 1.0F);
		int textColor = owned ? 0xFFFFE6A8 : open ? 0xFFD8DEEA : 0xFF7A8292;
		context.drawCenteredTextWithShadow(textRenderer, Text.translatable(perk.translationKey()), 0, 0, textColor);
		if (perk.ranks() > 1) {
			context.drawCenteredTextWithShadow(textRenderer, Text.literal(rank + "/" + perk.ranks()), 0, 10, textColor);
		}
		context.getMatrices().pop();
	}

	/** Una línea de puntos entre dos estrellas. */
	private static void line(DrawContext context, int x0, int y0, int x1, int y1, int color) {
		int dx = x1 - x0;
		int dy = y1 - y0;
		int steps = Math.max(Math.abs(dx), Math.abs(dy));
		if (steps == 0) {
			return;
		}
		for (int i = 6; i <= steps - 6; i += 2) {
			int x = x0 + dx * i / steps;
			int y = y0 + dy * i / steps;
			context.fill(x, y, x + 1, y + 1, color);
		}
	}

	private void tabs(DrawContext context, SkillData data, int mouseX, int mouseY) {
		int y = tabY();
		for (Skill skill : Skill.values()) {
			int x = tabX(skill);
			boolean sel = skill == selected;
			boolean over = mouseX >= x && mouseX < x + TAB - 2 && mouseY >= y && mouseY < y + 30;
			if (over) {
				hoveredTab = skill;
			}
			int bg = sel ? (0x70 << 24) | (skill.color & 0xFFFFFF) : over ? 0x40FFFFFF : 0x30000000;
			context.fill(x, y, x + TAB - 2, y + 30, bg);
			if (sel) {
				context.fill(x, y + 29, x + TAB - 2, y + 31, 0xFF000000 | skill.color);
			}
			context.drawItem(new ItemStack(skill.icon()), x + (TAB - 2) / 2 - 8, y + 2);
			Text level = Text.literal(String.valueOf(data.level(skill)));
			context.drawCenteredTextWithShadow(textRenderer, level, x + (TAB - 2) / 2, y + 20, sel ? 0xFFFFFFFF : 0xFFB8C0CC);
		}
	}

	// ------------------------------------------------------------------ descripción

	private List<Text> describe(Perk perk, SkillData data, boolean creative) {
		List<Text> lines = new ArrayList<>();
		int rank = data.rank(perk);
		lines.add(Text.translatable(perk.translationKey()).formatted(Formatting.GOLD));
		if (perk.ranks() > 1) {
			lines.add(Text.translatable("screen.evolvedwarfare.skills.rank", rank, perk.ranks()).formatted(Formatting.GRAY));
		}
		if (perk.hasValues()) {
			if (rank > 0) {
				lines.add(Text.translatable("screen.evolvedwarfare.skills.now", desc(perk, rank)).formatted(Formatting.GREEN));
			}
			if (rank < perk.ranks()) {
				Text text = desc(perk, rank + 1);
				lines.add(rank > 0 ? Text.translatable("screen.evolvedwarfare.skills.next", text).formatted(Formatting.WHITE)
						: text.copy().formatted(Formatting.WHITE));
			}
		} else {
			lines.add(desc(perk, 1).copy().formatted(Formatting.WHITE));
		}
		List<Item> unlocks = perk.unlocks();
		if (!unlocks.isEmpty()) {
			MutableText items = Text.empty();
			List<Item> distinct = new ArrayList<>();
			for (Item item : unlocks) {
				if (distinct.stream().noneMatch(other -> other.getName().getString().equals(item.getName().getString()))) {
					distinct.add(item);
				}
			}
			for (int i = 0; i < Math.min(4, distinct.size()); i++) {
				if (i > 0) {
					items.append(", ");
				}
				items.append(distinct.get(i).getName());
			}
			lines.add(Text.translatable("screen.evolvedwarfare.skills.unlocks", items).formatted(Formatting.AQUA));
		}
		Skills.Unlock state = Skills.check(data, perk, creative);
		switch (state) {
			case MAX_RANK -> lines.add(Text.translatable(perk.ranks() > 1 ? "screen.evolvedwarfare.skills.max" : "screen.evolvedwarfare.skills.owned")
					.formatted(Formatting.YELLOW));
			case LEVEL -> lines.add(Text.translatable("screen.evolvedwarfare.skills.needs_level", Text.translatable(perk.skill.translationKey()),
					perk.levelFor(rank + 1)).formatted(Formatting.RED));
			case REQUIRES -> {
				MutableText any = Text.empty();
				List<Perk> before = perk.requires();
				for (int i = 0; i < before.size(); i++) {
					if (i > 0) {
						any.append(Text.translatable("screen.evolvedwarfare.skills.or"));
					}
					any.append(Text.translatable(before.get(i).translationKey()));
				}
				lines.add(Text.translatable("screen.evolvedwarfare.skills.needs_perk", any).formatted(Formatting.RED));
			}
			case POINTS -> lines.add(Text.translatable("screen.evolvedwarfare.skills.no_points").formatted(Formatting.RED));
			case OK -> lines.add(Text.translatable(creative ? "screen.evolvedwarfare.skills.click_free" : "screen.evolvedwarfare.skills.click")
					.formatted(Formatting.GREEN));
		}
		return lines;
	}

	private static Text desc(Perk perk, int rank) {
		String key = perk.translationKey() + ".desc";
		return perk.hasValues() ? Text.translatable(key, number(perk.value(rank))) : Text.translatable(key);
	}

	private static String number(float value) {
		if (Math.abs(value - Math.round(value)) < 0.01F) {
			return String.valueOf(Math.round(value));
		}
		return String.format(Locale.ROOT, "%.1f", value).replace('.', ',');
	}

	// ------------------------------------------------------------------ ratón y teclado

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (button == 0) {
			for (Skill skill : Skill.values()) {
				int x = tabX(skill);
				if (mouseX >= x && mouseX < x + TAB - 2 && mouseY >= tabY() && mouseY < tabY() + 30) {
					select(skill);
					return true;
				}
			}
			for (Perk perk : Perk.of(selected)) {
				if (Math.abs(mouseX - nodeX(perk)) <= 8 && Math.abs(mouseY - nodeY(perk)) <= 8) {
					boolean creative = client != null && client.player != null && client.player.isCreative();
					if (Skills.check(ClientSkills.data, perk, creative) == Skills.Unlock.OK) {
						ClientPlayNetworking.send(new SkillPayloads.Unlock(perk.id()));
					} else if (client != null) {
						client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.BLOCK_NOTE_BLOCK_BASS, 0.6F));
					}
					return true;
				}
			}
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
		if (vertical != 0) {
			step(vertical > 0 ? -1 : 1);
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (keyCode == GLFW.GLFW_KEY_LEFT || keyCode == GLFW.GLFW_KEY_A) {
			step(-1);
			return true;
		}
		if (keyCode == GLFW.GLFW_KEY_RIGHT || keyCode == GLFW.GLFW_KEY_D) {
			step(1);
			return true;
		}
		if (ClientSkills.open != null && ClientSkills.open.matchesKey(keyCode, scanCode)) {
			close();
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	private void step(int direction) {
		Skill[] all = Skill.values();
		select(all[Math.floorMod(selected.ordinal() + direction, all.length)]);
	}

	private void select(Skill skill) {
		if (skill != selected) {
			selected = skill;
			MinecraftClient client = MinecraftClient.getInstance();
			client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.ITEM_BOOK_PAGE_TURN, 1.0F));
		}
	}
}
