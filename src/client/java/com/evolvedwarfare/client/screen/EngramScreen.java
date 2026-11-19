package com.evolvedwarfare.client.screen;

import com.evolvedwarfare.client.ClientSkills;
import com.evolvedwarfare.network.EngramPayloads;
import com.evolvedwarfare.skill.Engram;
import com.evolvedwarfare.skill.Engrams;
import com.evolvedwarfare.skill.Skill;
import com.evolvedwarfare.skill.SkillData;
import com.evolvedwarfare.skill.SurvivorAttribute;
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
	/** La pestaña de atributos (fase 114) en vez de la de engramas. */
	private static boolean attributesTab;
	/** Donde empiezan las categorías (debajo de las dos pestañas). */
	private static final int CATS_TOP = 44 + 34;
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
		for (Engram e : Engrams.visible()) {
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
		// fondo azul oscuro, como el menú de engramas de ARK
		ctx.fillGradient(0, 0, width, height, 0xE00C2432, 0xE0050C12);
		super.render(ctx, mouseX, mouseY, delta);
		hovered = null;
		header(ctx, data);
		tabs(ctx, mouseX, mouseY);
		if (search != null) {
			search.visible = !attributesTab;
		}
		if (attributesTab) {
			attributes(ctx, data, mouseX, mouseY);
		} else {
			grid(ctx, data, mouseX, mouseY);
		}
		if (showSkills()) {
			skills(ctx, data);
		}
		if (!Engrams.clientRequired) {
			ctx.drawCenteredTextWithShadow(textRenderer, Text.translatable("screen.evolvedwarfare.engrams.off"), width / 2, height - 14, 0xFFE0B040);
		}
		if (hovered != null && !attributesTab) {
			ctx.drawTooltip(textRenderer, tooltip(hovered, data), mouseX, mouseY);
		}
		if (attributesTab && attributeTip != null) {
			ctx.drawTooltip(textRenderer, attributeTip, mouseX, mouseY);
		}
		attributeTip = null;
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
		Text attrPoints = Text.translatable("screen.evolvedwarfare.attributes.points", data.attributePoints);
		int ax = px + Math.max(textRenderer.getWidth(points), textRenderer.getWidth(Text.translatable("screen.evolvedwarfare.engrams.count",
				data.engrams.size(), Engrams.visible().size()))) + 16;
		ctx.drawTextWithShadow(textRenderer, attrPoints, ax, 8, data.attributePoints > 0 ? 0xFFFF9FA0 : 0xFF9AA0AA);
		Text count = Text.translatable("screen.evolvedwarfare.engrams.count", data.engrams.size(), Engrams.visible().size());
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
		for (int m = 0; m < 2; m++) {
			int my = TOP + m * 15;
			boolean sel = attributesTab == (m == 1);
			boolean over = mouseX >= 10 && mouseX < 10 + TABS_W && mouseY >= my && mouseY < my + 14;
			ctx.fill(10, my, 10 + TABS_W, my + 13, sel ? 0xFF2A5A2A : over ? 0x80306030 : 0x60182028);
			Text name = Text.translatable(m == 0 ? "screen.evolvedwarfare.engrams.title" : "screen.evolvedwarfare.attributes.title")
					.formatted(Formatting.BOLD);
			ctx.drawTextWithShadow(textRenderer, name, 16, my + 3, sel ? 0xFFFFFFFF : 0xFFB0BAC4);
			if (m == 1 && ClientSkills.data.attributePoints > 0) {
				String n = String.valueOf(ClientSkills.data.attributePoints);
				ctx.drawTextWithShadow(textRenderer, n, 10 + TABS_W - 4 - textRenderer.getWidth(n), my + 3, 0xFFFF9FA0);
			}
		}
		if (attributesTab) {
			return;
		}
		int y = CATS_TOP;
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
			for (Engram e : Engrams.visible()) {
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

	/** Como en ARK (fase 119): una fila por nivel, con el número grande a la izquierda y las fichas con su nombre e icono. */
	private static final int CARD_W = 64, CARD_H = 56, CARD_GAP = 5, LEVEL_W = 36, CARD_HEAD = 15;
	/** Al abrir, baja hasta tu nivel. */
	private boolean autoScroll = true;

	private int cardColumns() {
		return Math.max(1, (gridRight() - gridLeft() - LEVEL_W + CARD_GAP) / (CARD_W + CARD_GAP));
	}

	private void grid(DrawContext ctx, SkillData data, int mouseX, int mouseY) {
		shown = filtered();
		int cols = cardColumns();
		int left = gridLeft();
		int bottom = height - 20;
		java.util.TreeMap<Integer, List<Engram>> byLevel = new java.util.TreeMap<>();
		for (Engram e : shown) {
			byLevel.computeIfAbsent(e.level, k -> new ArrayList<>()).add(e);
		}
		// alto total y dónde empieza cada nivel
		int total = 0;
		int mine = 0;
		for (var entry : byLevel.entrySet()) {
			if (entry.getKey() <= data.level) {
				mine = total;
			}
			int lines = (entry.getValue().size() + cols - 1) / cols;
			total += lines * (CARD_H + CARD_GAP) + 4;
		}
		int visibleH = bottom - TOP;
		int maxScroll = Math.max(0, total - visibleH);
		if (autoScroll && !shown.isEmpty()) {
			autoScroll = false;
			scroll = Math.max(0, mine - (CARD_H + CARD_GAP));
		}
		scroll = MathHelper.clamp(scroll, 0, maxScroll);
		ctx.enableScissor(left - 2, TOP - 2, gridRight() + 2, bottom);
		long time = System.currentTimeMillis();
		int y = TOP - scroll;
		for (var entry : byLevel.entrySet()) {
			int level = entry.getKey();
			List<Engram> list = entry.getValue();
			int lines = (list.size() + cols - 1) / cols;
			int blockH = lines * (CARD_H + CARD_GAP);
			if (y + blockH >= TOP - 2 && y <= bottom) {
				// el número del nivel, grande (azul si ya lo tienes; apagado si aún no)
				boolean reached = data.level >= level;
				ctx.getMatrices().push();
				String lv = String.valueOf(level);
				float scale = lv.length() >= 3 ? 1.6F : 2.2F;
				ctx.getMatrices().translate(left + (LEVEL_W - textRenderer.getWidth(lv) * scale) / 2.0F - 2.0F, y + 4.0F, 0.0F);
				ctx.getMatrices().scale(scale, scale, 1.0F);
				ctx.drawTextWithShadow(textRenderer, lv, 0, 0, reached ? 0xFF3CC8E6 : 0xFF2A5A66);
				ctx.getMatrices().pop();
				if (level == data.level || (reached && byLevel.higherKey(level) != null && byLevel.higherKey(level) > data.level)) {
					// la fila de tu nivel, marcada
					ctx.fill(left, y + 2, left + 2, y + blockH - CARD_GAP - 2, 0xFF3CC8E6);
				}
			}
			for (int i = 0; i < list.size(); i++) {
				Engram e = list.get(i);
				int x = left + LEVEL_W + (i % cols) * (CARD_W + CARD_GAP);
				int cy = y + (i / cols) * (CARD_H + CARD_GAP);
				if (cy + CARD_H < TOP - 2 || cy > bottom) {
					continue;
				}
				boolean over = mouseX >= x && mouseX < x + CARD_W && mouseY >= cy && mouseY < cy + CARD_H && mouseY >= TOP && mouseY < bottom;
				card(ctx, e, state(e, data), x, cy, over, time);
				if (over) {
					hovered = e;
				}
			}
			y += blockH + 4;
		}
		ctx.disableScissor();
		if (maxScroll > 0) {
			int knob = Math.max(12, visibleH * visibleH / total);
			int ky = TOP + (visibleH - knob) * scroll / maxScroll;
			ctx.fill(gridRight() + 3, TOP, gridRight() + 5, TOP + visibleH, 0x40FFFFFF);
			ctx.fill(gridRight() + 3, ky, gridRight() + 5, ky + knob, 0xFF3CC8E6);
		}
	}

	/** Una ficha: nombre arriba (en una franja oscura), el icono en grande y, abajo a la derecha, lo que cuesta. */
	private void card(DrawContext ctx, Engram e, int st, int x, int y, boolean over, long time) {
		int border = switch (st) {
			case 0 -> 0xFF4FD07A;
			case 1 -> (time / 400) % 2 == 0 ? 0xFFFFD75E : 0xFFC8A040;
			case 2 -> 0xFF8A7A40;
			case 3 -> 0xFF5A2A30;
			default -> 0xFF3A4048;
		};
		int bg = switch (st) {
			case 0 -> 0xA0143A2A;
			case 3, 4 -> 0xA00C161C;
			default -> 0xA0103040;
		};
		ctx.fill(x - 1, y - 1, x + CARD_W + 1, y + CARD_H + 1, over ? 0xFFFFFFFF : border);
		ctx.fill(x, y, x + CARD_W, y + CARD_H, bg);
		// un brillo arriba, como el cristal de ARK
		ctx.fill(x, y + CARD_HEAD, x + CARD_W, y + CARD_HEAD + 1, 0x302E8FA6);
		ctx.fill(x, y, x + CARD_W, y + CARD_HEAD, 0xC00A1A22);
		// el nombre, en una o dos líneas pequeñas
		float ts = 0.6F;
		List<net.minecraft.text.OrderedText> name = textRenderer.wrapLines(Text.translatable(e.translationKey()), (int) ((CARD_W - 4) / ts));
		ctx.getMatrices().push();
		ctx.getMatrices().translate(x + 2.0F, y + (name.size() > 1 ? 1.5F : 4.5F), 10.0F);
		ctx.getMatrices().scale(ts, ts, 1.0F);
		for (int l = 0; l < Math.min(2, name.size()); l++) {
			ctx.drawTextWithShadow(textRenderer, name.get(l), 0, l * 10, st == 3 || st == 4 ? 0xFF8A949E : 0xFFE6F4F8);
		}
		ctx.getMatrices().pop();
		// el icono, al doble
		ctx.getMatrices().push();
		ctx.getMatrices().translate(x + CARD_W / 2.0F - 16.0F, y + CARD_HEAD + 3.0F, 0.0F);
		ctx.getMatrices().scale(2.0F, 2.0F, 1.0F);
		ctx.drawItem(icon(e), 0, 0);
		ctx.getMatrices().pop();
		ctx.getMatrices().push();
		ctx.getMatrices().translate(0.0F, 0.0F, 250.0F);
		if (st >= 3) {
			ctx.fill(x, y + CARD_HEAD, x + CARD_W, y + CARD_H, 0x90000000);
		}
		if (st == 0) {
			ctx.drawTextWithShadow(textRenderer, "✔", x + CARD_W - 9, y + CARD_H - 10, 0xFF6FD07F);
		} else {
			String cost = e.cost + "p";
			ctx.drawTextWithShadow(textRenderer, cost, x + CARD_W - 2 - textRenderer.getWidth(cost), y + CARD_H - 10,
					st == 1 ? 0xFFFFD75E : st == 2 ? 0xFFB0A060 : 0xFF6A747E);
		}
		if (st == 4) {
			// le falta otro antes: candado
			String[] lock = {"..###..", ".#...#.", ".#...#.", "#######", "###.###", "###.###", "#######"};
			for (int r = 0; r < lock.length; r++) {
				for (int c = 0; c < lock[r].length(); c++) {
					if (lock[r].charAt(c) == '#') {
						ctx.fill(x + 3 + c, y + CARD_H - 10 + r, x + 4 + c, y + CARD_H - 9 + r, r < 3 ? 0xFFB0B4BA : 0xFF8A9098);
					}
				}
			}
		}
		ctx.getMatrices().pop();
	}

	@Nullable
	private SurvivorAttribute hoveredAttribute;

	/** La pestaña de atributos: cada uno con sus puntos, lo que da y su botón de "+" (mayúsculas: de 5 en 5). */
	private void attributes(DrawContext ctx, SkillData data, int mouseX, int mouseY) {
		hoveredAttribute = null;
		int left = gridLeft();
		int right = gridRight();
		int y = TOP;
		int rowH = Math.max(22, Math.min(30, (height - TOP - 24) / SurvivorAttribute.values().length));
		for (SurvivorAttribute a : SurvivorAttribute.values()) {
			int p = data.attribute(a);
			boolean over = mouseX >= left && mouseX < right && mouseY >= y && mouseY < y + rowH - 2;
			ctx.fill(left, y, right, y + rowH - 2, over ? 0x80283440 : 0x60182028);
			ctx.fill(left, y, left + 2, y + rowH - 2, 0xFF000000 | a.color);
			ItemStack icon = Registries.ITEM.containsId(a.icon) ? new ItemStack(Registries.ITEM.get(a.icon)) : new ItemStack(Items.BOOK);
			ctx.drawItem(icon, left + 6, y + (rowH - 2 - 16) / 2);
			ctx.drawTextWithShadow(textRenderer, Text.translatable(a.translationKey()).formatted(Formatting.BOLD), left + 28, y + 3, 0xFF000000 | a.color);
			ctx.drawTextWithShadow(textRenderer, effect(a, p), left + 28, y + 13, 0xFFD0D6DC);
			// el botón
			int bx = right - 20, by = y + (rowH - 2 - 14) / 2;
			boolean can = data.attributePoints > 0;
			boolean overBtn = over && can;
			ctx.fill(bx - 1, by - 1, bx + 15, by + 15, can ? (overBtn ? 0xFFFFFFFF : 0xFF6FD06F) : 0xFF3A3E44);
			ctx.fill(bx, by, bx + 14, by + 14, can ? 0xFF1F4A26 : 0xFF202428);
			ctx.drawCenteredTextWithShadow(textRenderer, "+", bx + 7, by + 3, can ? 0xFFFFFFFF : 0xFF6A6E74);
			String pts = String.valueOf(p);
			ctx.drawTextWithShadow(textRenderer, pts, bx - 8 - textRenderer.getWidth(pts), y + (rowH - 2 - 8) / 2, p > 0 ? 0xFFFFFFFF : 0xFF6A747E);
			if (over) {
				hoveredAttribute = can ? a : null;
				List<Text> tip = new ArrayList<>();
				tip.add(Text.translatable(a.translationKey()).formatted(Formatting.BOLD));
				tip.add(Text.translatable(a.translationKey() + ".desc").formatted(Formatting.GRAY));
				tip.add(Text.translatable("screen.evolvedwarfare.attributes.next", effect(a, p + 1)).formatted(Formatting.DARK_AQUA));
				if (a.cap > 0.0 && a.value(p + 1) >= a.cap) {
					tip.add(Text.translatable("screen.evolvedwarfare.attributes.cap").formatted(Formatting.GOLD));
				}
				tip.add(Text.translatable(can ? "screen.evolvedwarfare.attributes.click" : "screen.evolvedwarfare.attributes.no_points")
						.formatted(can ? Formatting.GREEN : Formatting.RED));
				attributeTip = tip;
			}
			y += rowH;
		}
		if (data.attributePoints <= 0) {
			ctx.drawTextWithShadow(textRenderer, Text.translatable("screen.evolvedwarfare.attributes.hint").formatted(Formatting.DARK_GRAY), left, y + 4,
					0xFF8A949E);
		}
	}

	@Nullable
	private List<Text> attributeTip;

	private static String num(double v) {
		double r = Math.round(v * 10.0) / 10.0;
		return r == Math.rint(r) ? String.valueOf((long) r) : String.format(Locale.ROOT, "%.1f", r).replace('.', ',');
	}

	/** Lo que dan ahora esos puntos, en palabras. */
	private static Text effect(SurvivorAttribute a, int points) {
		double v = a.value(points);
		return switch (a) {
			case HEALTH -> Text.translatable("screen.evolvedwarfare.attributes.effect.health", num(v));
			case OXYGEN -> Text.translatable("screen.evolvedwarfare.attributes.effect.oxygen", num(v * 100.0 / (v + 1.0)));
			default -> Text.translatable("screen.evolvedwarfare.attributes.effect." + a.id(), num(v * 100.0));
		};
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
		for (int m = 0; m < 2; m++) {
			int my = TOP + m * 15;
			if (mouseX >= 10 && mouseX < 10 + TABS_W && mouseY >= my && mouseY < my + 14) {
				attributesTab = m == 1;
				scroll = 0;
				click();
				return true;
			}
		}
		if (attributesTab) {
			if (hoveredAttribute != null && button == 0) {
				int times = hasShiftDown() ? Math.min(5, ClientSkills.data.attributePoints) : 1;
				for (int i = 0; i < Math.max(1, times); i++) {
					ClientPlayNetworking.send(new EngramPayloads.SpendAttribute(hoveredAttribute.id()));
				}
				click();
				return true;
			}
			return false;
		}
		int y = CATS_TOP;
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
		scroll -= (int) Math.round(vertical * (CARD_H + CARD_GAP) / 2.0);
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
