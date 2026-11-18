package com.evolvedwarfare.client.screen;

import com.evolvedwarfare.client.ClientSkills;
import com.evolvedwarfare.network.EngramPayloads;
import com.evolvedwarfare.skill.Engram;
import com.evolvedwarfare.skill.Engrams;
import com.evolvedwarfare.skill.Skill;
import com.evolvedwarfare.skill.SkillData;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.MathHelper;
import org.jetbrains.annotations.Nullable;

/**
 * La pantalla de engramas (fase 113, como en ARK; tecla K): a la izquierda las categorías, en medio los engramas de la
 * elegida (verde: aprendido; dorado: lo puedes aprender ya; amarillo apagado: te faltan puntos; rojo: te falta nivel;
 * gris: antes hay que aprender otro) y a la derecha tus habilidades. Clic para aprenderlo; la rueda para bajar.
 */
public class EngramScreen extends Screen {
	private static String category = "";
	private static final int TILE = 26, GAP = 4, TABS_W = 104, SKILLS_W = 132, TOP = 44;

	private TextFieldWidget search;
	private int scroll;
	@Nullable
	private Engram hovered;
	private List<Engram> shown = List.of();

	public EngramScreen() {
		super(Text.translatable("screen.evolvedwarfare.engrams.title"));
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	@Override
	protected void init() {
		search = new TextFieldWidget(textRenderer, width - 12 - 120, 8, 120, 14, Text.translatable("screen.evolvedwarfare.engrams.search"));
		search.setPlaceholder(Text.translatable("screen.evolvedwarfare.engrams.search").formatted(Formatting.DARK_GRAY));
		search.setChangedListener(s -> scroll = 0);
		addDrawableChild(search);
	}

	private boolean showSkills() {
		return width >= 520;
	}

	private int gridLeft() {
		return 10 + TABS_W + 8;
	}

	private int gridRight() {
		return width - 10 - (showSkills() ? SKILLS_W + 8 : 0);
	}

	private int columns() {
		return Math.max(1, (gridRight() - gridLeft() + GAP) / (TILE + GAP));
	}

	private List<Engram> filtered() {
		String q = search == null ? "" : search.getText().trim().toLowerCase(Locale.ROOT);
		List<Engram> out = new ArrayList<>();
		for (Engram e : Engrams.all()) {
			if (!category.isEmpty() && !category.equals(e.category) && q.isEmpty()) {
				continue;
			}
			if (!q.isEmpty() && !matches(e, q)) {
				continue;
			}
			out.add(e);
		}
		out.sort(Comparator.comparingInt((Engram e) -> e.level).thenComparingInt(e -> e.cost));
		return out;
	}

	private static boolean matches(Engram e, String q) {
		if (Text.translatable(e.translationKey()).getString().toLowerCase(Locale.ROOT).contains(q)) {
			return true;
		}
		for (Item item : Engrams.itemsOf(e)) {
			if (item.getName().getString().toLowerCase(Locale.ROOT).contains(q)) {
				return true;
			}
		}
		return false;
	}

	private static ItemStack icon(Engram e) {
		if (e.icon != null && Registries.ITEM.containsId(e.icon)) {
			return new ItemStack(Registries.ITEM.get(e.icon));
		}
		List<Item> items = Engrams.itemsOf(e);
		return items.isEmpty() ? new ItemStack(Items.BOOK) : new ItemStack(items.get(0));
	}

	private static boolean creative() {
		MinecraftClient client = MinecraftClient.getInstance();
		return client.player != null && client.player.getAbilities().creativeMode;
	}

	// ------------------------------------------------------------------ dibujar

	@Override
	public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
		SkillData data = ClientSkills.data;
		ctx.fill(0, 0, width, height, 0xC8080C10);
		super.render(ctx, mouseX, mouseY, delta);
		hovered = null;
		header(ctx, data);
		tabs(ctx, mouseX, mouseY);
		grid(ctx, data, mouseX, mouseY);
		if (showSkills()) {
			skills(ctx, data);
		}
		if (!Engrams.clientRequired) {
			ctx.drawCenteredTextWithShadow(textRenderer, Text.translatable("screen.evolvedwarfare.engrams.off"), width / 2, height - 14, 0xFFE0B040);
		}
		if (hovered != null) {
			ctx.drawTooltip(textRenderer, tooltip(hovered, data), mouseX, mouseY);
		}
	}

	@Override
	public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
		// el fondo lo pone render (oscuro, sin desenfoque)
	}

	private void header(DrawContext ctx, SkillData data) {
		ctx.getMatrices().push();
		ctx.getMatrices().translate(12.0F, 8.0F, 0.0F);
		ctx.getMatrices().scale(1.5F, 1.5F, 1.0F);
		ctx.drawTextWithShadow(textRenderer, title.copy().formatted(Formatting.BOLD), 0, 0, 0xFF9FD8E6);
		ctx.getMatrices().pop();
		int x = 12 + (int) (textRenderer.getWidth(title.copy().formatted(Formatting.BOLD)) * 1.5F) + 16;
		Text level = Text.translatable("screen.evolvedwarfare.skills.level", data.level);
		ctx.drawTextWithShadow(textRenderer, level, x, 8, 0xFFE6D2A0);
		float f = data.maxLevel() ? 1.0F : data.levelXp / SkillData.levelXpToNext(data.level);
		int bw = 110;
		ctx.fill(x - 1, 19, x + bw + 1, 23, 0xAA000000);
		ctx.fill(x, 20, x + bw, 22, 0xFF1E3A44);
		ctx.fill(x, 20, x + Math.round(bw * MathHelper.clamp(f, 0.0F, 1.0F)), 22, data.maxLevel() ? 0xFFFFD75E : 0xFF3CC8E6);
		int px = x + bw + 16;
		Text points = Text.translatable("screen.evolvedwarfare.engrams.points", data.engramPoints);
		ctx.drawTextWithShadow(textRenderer, points, px, 8, data.engramPoints > 0 ? 0xFFFFD75E : 0xFF9AA0AA);
		Text count = Text.translatable("screen.evolvedwarfare.engrams.count", data.engrams.size(), Engrams.all().size());
		ctx.drawTextWithShadow(textRenderer, count, px, 19, 0xFF8A949E);
		ctx.fill(10, TOP - 6, width - 10, TOP - 5, 0xFF2A6470);
	}

	private List<String> tabList() {
		List<String> list = new ArrayList<>();
		list.add("");
		list.addAll(Engrams.categories());
		return list;
	}

	private void tabs(DrawContext ctx, int mouseX, int mouseY) {
		int y = TOP;
		for (String cat : tabList()) {
			boolean sel = cat.equals(category);
			boolean over = mouseX >= 10 && mouseX < 10 + TABS_W && mouseY >= y && mouseY < y + 14;
			ctx.fill(10, y, 10 + TABS_W, y + 13, sel ? 0xFF1F4650 : over ? 0x80305060 : 0x60182028);
			if (sel) {
				ctx.fill(10, y, 12, y + 13, 0xFF3CC8E6);
			}
			Text name = cat.isEmpty() ? Text.translatable("screen.evolvedwarfare.engrams.all") : Text.translatable(Engram.categoryKey(cat));
			ctx.drawTextWithShadow(textRenderer, name, 16, y + 3, sel ? 0xFFFFFFFF : 0xFFB0BAC4);
			int learned = 0, total = 0;
			for (Engram e : Engrams.all()) {
				if (cat.isEmpty() || cat.equals(e.category)) {
					total++;
					learned += ClientSkills.data.engrams.contains(e.id) ? 1 : 0;
				}
			}
			String c = learned + "/" + total;
			ctx.drawTextWithShadow(textRenderer, c, 10 + TABS_W - 4 - textRenderer.getWidth(c), y + 3, learned == total ? 0xFF6FD06F : 0xFF6A747E);
			y += 15;
		}
	}

	private int state(Engram e, SkillData data) {
		return switch (Engrams.check(data, e, creative())) {
			case LEARNED -> 0;
			case OK -> 1;
			case POINTS -> 2;
			case LEVEL -> 3;
			default -> 4;
		};
	}

	private void grid(DrawContext ctx, SkillData data, int mouseX, int mouseY) {
		shown = filtered();
		int cols = columns();
		int left = gridLeft();
		int rows = (shown.size() + cols - 1) / cols;
		int visibleH = height - TOP - 22;
		int maxScroll = Math.max(0, rows * (TILE + GAP) - visibleH);
		scroll = MathHelper.clamp(scroll, 0, maxScroll);
		ctx.enableScissor(left - 2, TOP - 2, gridRight() + 2, height - 20);
		long time = System.currentTimeMillis();
		for (int i = 0; i < shown.size(); i++) {
			Engram e = shown.get(i);
			int x = left + (i % cols) * (TILE + GAP);
			int y = TOP + (i / cols) * (TILE + GAP) - scroll;
			if (y + TILE < TOP - 2 || y > height - 20) {
				continue;
			}
			int st = state(e, data);
			int border = switch (st) {
				case 0 -> 0xFF4FB04F;
				case 1 -> (time / 400) % 2 == 0 ? 0xFFFFD75E : 0xFFC8A040;
				case 2 -> 0xFF8A7A40;
				case 3 -> 0xFF8A2A2A;
				default -> 0xFF4A4E54;
			};
			int bg = switch (st) {
				case 0 -> 0xFF1A3320;
				case 1 -> 0xFF2E2A18;
				case 3 -> 0xFF2A1416;
				default -> 0xFF161A1E;
			};
			boolean over = mouseX >= x && mouseX < x + TILE && mouseY >= y && mouseY < y + TILE && mouseY >= TOP && mouseY < height - 20;
			ctx.fill(x - 1, y - 1, x + TILE + 1, y + TILE + 1, over ? 0xFFFFFFFF : border);
			ctx.fill(x, y, x + TILE, y + TILE, bg);
			ctx.drawItem(icon(e), x + 5, y + 4);
			if (st >= 3) {
				// apagado: aún no
				ctx.fill(x, y, x + TILE, y + TILE, 0x90000000);
			}
			ctx.getMatrices().push();
			ctx.getMatrices().translate(0.0F, 0.0F, 200.0F);
			String lv = String.valueOf(e.level);
			ctx.drawTextWithShadow(textRenderer, lv, x + TILE - 1 - textRenderer.getWidth(lv), y + TILE - 8,
					st == 3 ? 0xFFFF6060 : st == 0 ? 0xFF8FE08F : 0xFFE0E0E0);
			if (st == 0) {
				ctx.drawTextWithShadow(textRenderer, "✔", x + 1, y + 1, 0xFF6FD06F);
			}
			ctx.getMatrices().pop();
			if (over) {
				hovered = e;
			}
		}
		ctx.disableScissor();
		if (maxScroll > 0) {
			int track = visibleH;
			int knob = Math.max(12, track * visibleH / (rows * (TILE + GAP)));
			int ky = TOP + (track - knob) * scroll / maxScroll;
			ctx.fill(gridRight() + 3, TOP, gridRight() + 5, TOP + track, 0x40FFFFFF);
			ctx.fill(gridRight() + 3, ky, gridRight() + 5, ky + knob, 0xFF3CC8E6);
		}
	}

	private void skills(DrawContext ctx, SkillData data) {
		int x = width - 10 - SKILLS_W;
		int y = TOP;
		ctx.fill(x - 4, y - 2, x + SKILLS_W, y + 12 + Skill.values().length * 18, 0x60182028);
		ctx.drawTextWithShadow(textRenderer, Text.translatable("screen.evolvedwarfare.engrams.skills").formatted(Formatting.BOLD), x, y, 0xFFE6D2A0);
		y += 13;
		for (Skill skill : Skill.values()) {
			ctx.drawItem(new ItemStack(skill.icon()), x, y);
			ctx.drawTextWithShadow(textRenderer, Text.translatable(skill.translationKey()), x + 19, y + 1, 0xFF000000 | skill.color);
			String lv = String.valueOf(data.level(skill));
			ctx.drawTextWithShadow(textRenderer, lv, x + SKILLS_W - 6 - textRenderer.getWidth(lv), y + 1, 0xFFFFFFFF);
			int bw = SKILLS_W - 25;
			float f = data.level(skill) >= Skill.MAX_LEVEL ? 1.0F : data.xp(skill) / Skill.xpToNext(data.level(skill));
			ctx.fill(x + 19, y + 11, x + 19 + bw, y + 13, 0xFF1E2A33);
			ctx.fill(x + 19, y + 11, x + 19 + Math.round(bw * MathHelper.clamp(f, 0.0F, 1.0F)), y + 13, 0xFF000000 | skill.color);
			y += 18;
		}
	}

	private List<Text> tooltip(Engram e, SkillData data) {
		List<Text> lines = new ArrayList<>();
		lines.add(Text.translatable(e.translationKey()).formatted(Formatting.BOLD, Formatting.AQUA));
		lines.add(Text.translatable(Engram.categoryKey(e.category)).formatted(Formatting.DARK_GRAY));
		Engrams.Learn st = Engrams.check(data, e, creative());
		lines.add(Text.translatable("screen.evolvedwarfare.engrams.needs_level", e.level)
				.formatted(data.level >= e.level ? Formatting.GRAY : Formatting.RED));
		lines.add(Text.translatable("screen.evolvedwarfare.engrams.cost", e.cost)
				.formatted(st == Engrams.Learn.POINTS ? Formatting.RED : Formatting.GRAY));
		if (!e.requires.isEmpty()) {
			List<Text> reqs = new ArrayList<>();
			boolean all = true;
			for (String r : e.requires) {
				Engram req = Engrams.byId(r);
				if (req != null) {
					boolean has = data.engrams.contains(r);
					all &= has;
					reqs.add(Text.translatable(req.translationKey()).formatted(has ? Formatting.GREEN : Formatting.RED));
				}
			}
			var line = Text.translatable("screen.evolvedwarfare.engrams.requires", Text.empty()).formatted(all ? Formatting.GRAY : Formatting.RED).copy();
			for (int i = 0; i < reqs.size(); i++) {
				if (i > 0) {
					line.append(Text.literal(", ").formatted(Formatting.GRAY));
				}
				line.append(reqs.get(i));
			}
			lines.add(line);
		}
		if (e.perk != null) {
			lines.add(Text.translatable("screen.evolvedwarfare.engrams.perk").formatted(Formatting.GOLD));
		}
		List<Item> items = Engrams.itemsOf(e);
		if (!items.isEmpty()) {
			lines.add(Text.translatable("screen.evolvedwarfare.engrams.unlocks").formatted(Formatting.YELLOW));
			int max = 10;
			for (int i = 0; i < Math.min(max, items.size()); i++) {
				lines.add(Text.literal(" • ").formatted(Formatting.DARK_GRAY).append(items.get(i).getName().copy().formatted(Formatting.WHITE)));
			}
			if (items.size() > max) {
				lines.add(Text.translatable("screen.evolvedwarfare.engrams.more", items.size() - max).formatted(Formatting.DARK_GRAY));
			}
		}
		switch (st) {
			case LEARNED -> lines.add(Text.translatable("screen.evolvedwarfare.engrams.learned").formatted(Formatting.GREEN));
			case OK -> lines.add(Text.translatable("screen.evolvedwarfare.engrams.click").formatted(Formatting.GOLD));
			case POINTS -> lines.add(Text.translatable("screen.evolvedwarfare.engrams.no_points").formatted(Formatting.RED));
			default -> {
			}
		}
		return lines;
	}

	// ------------------------------------------------------------------ ratón y teclado

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (super.mouseClicked(mouseX, mouseY, button)) {
			return true;
		}
		int y = TOP;
		for (String cat : tabList()) {
			if (mouseX >= 10 && mouseX < 10 + TABS_W && mouseY >= y && mouseY < y + 14) {
				category = cat;
				scroll = 0;
				if (search != null) {
					search.setText("");
				}
				click();
				return true;
			}
			y += 15;
		}
		if (hovered != null && button == 0) {
			if (Engrams.check(ClientSkills.data, hovered, creative()) == Engrams.Learn.OK) {
				ClientPlayNetworking.send(new EngramPayloads.Learn(hovered.id));
			}
			click();
			return true;
		}
		return false;
	}

	private void click() {
		MinecraftClient.getInstance().getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK, 1.0F));
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
		scroll -= (int) Math.round(vertical * (TILE + GAP));
		return true;
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (search != null && search.isFocused()) {
			if (keyCode == 256) {
				close();
				return true;
			}
			return search.keyPressed(keyCode, scanCode, modifiers) || search.isActive() || super.keyPressed(keyCode, scanCode, modifiers);
		}
		if (ClientSkills.open != null && ClientSkills.open.matchesKey(keyCode, scanCode)) {
			close();
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}
}
