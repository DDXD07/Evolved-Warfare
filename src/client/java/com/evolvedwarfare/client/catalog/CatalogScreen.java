package com.evolvedwarfare.client.catalog;

import com.evolvedwarfare.EvolvedWarfare;
import com.evolvedwarfare.logistics.Market;
import com.evolvedwarfare.screen.CatalogScreenHandler;
import com.evolvedwarfare.screen.TraderScreenHandler;
import com.evolvedwarfare.trade.Deal;
import com.evolvedwarfare.trade.Quality;
import com.evolvedwarfare.trade.TraderType;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.MutableText;
import net.minecraft.text.OrderedText;
import net.minecraft.text.StringVisitable;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * El catálogo de un mercader, al estilo de los catálogos de venta por correo de principios del siglo XX: un libro
 * abierto sujeto con las dos manos, en blanco y negro, de imprenta. Primero el índice (la portada de la casa y «¿Qué
 * desea?»: Compra o Venta) y luego las páginas, cuatro artículos en cada una, con su grabado, su nombre, su precio y lo
 * que queda. Las páginas se pasan con las flechas, la rueda o los botones de abajo: la hoja gira sobre el lomo y la
 * mano la acompaña.
 *
 * <p>Se dibuja en un lienzo de {@link #W} x {@link #H} que se escala a la pantalla. La compraventa es la de siempre
 * ({@link TraderScreenHandler#onButtonClick}).</p>
 */
public class CatalogScreen extends HandledScreen<CatalogScreenHandler> {
	private static final Identifier PAPER = EvolvedWarfare.id("textures/gui/catalog/paper.png");
	private static final Identifier HAND_LEFT = EvolvedWarfare.id("textures/gui/catalog/hand_left.png");
	private static final Identifier HAND_RIGHT = EvolvedWarfare.id("textures/gui/catalog/hand_right.png");

	static final int W = 420;
	static final int H = 272;
	static final int SPINE = 210;
	static final int PAGE_W = 196;
	static final int PAGE_H = 256;
	static final int LEFT_X = 12;
	static final int RIGHT_X = 212;
	static final int PAGE_Y = 8;
	static final int PER_PAGE = 4;
	static final int ENTRY_H = 50;
	static final int ENTRIES_Y = 40;
	static final int INK = 0xFF1C1B1A;
	static final int FADED = 0xFF55514C;
	static final float TURN_MS = 480.0F;

	enum Mode {
		INDEX, BUY, SELL
	}

	/** Lo que se ve: el modo y qué pliego (dos páginas). */
	private record Spread(Mode mode, int index) {
	}

	private Spread spread = new Spread(Mode.INDEX, 0);
	/** El pase de página en marcha (o null). */
	@Nullable
	private Spread turnFrom;
	private int turnDir;
	private long turnStart;
	private float scale = 1.0F;
	private float originX;
	private float originY;

	public CatalogScreen(CatalogScreenHandler handler, PlayerInventory inventory, Text title) {
		super(handler, inventory, title);
	}

	@Override
	protected void init() {
		super.init();
		if (!CatalogSession.active() && client != null) {
			CatalogSession.begin(client, handler.book());
		}
	}

	// ------------------------------------------------------------------ abrir y cerrar

	@Override
	public void close() {
		if (CatalogSession.active() && !CatalogSession.closingStarted()) {
			CatalogSession.beginClose();
			if (client != null) {
				client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.ITEM_BOOK_PUT, 1.0F));
			}
			return;
		}
		if (!CatalogSession.active() || CatalogSession.finished()) {
			super.close();
		}
	}

	@Override
	public void handledScreenTick() {
		super.handledScreenTick();
		if (client != null && CatalogSession.takeOpenSound()) {
			client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.ITEM_BOOK_PAGE_TURN, 0.9F));
		}
		if (CatalogSession.closingStarted() && CatalogSession.finished()) {
			super.close();
		}
	}

	@Override
	public void removed() {
		super.removed();
		if (client != null) {
			CatalogSession.end(client);
		}
	}

	// ------------------------------------------------------------------ lo que hay en cada pliego

	private List<Integer> entries(Mode mode) {
		List<Integer> out = new ArrayList<>();
		List<Deal> deals = handler.deals();
		for (int i = 0; i < deals.size(); i++) {
			if ((mode == Mode.BUY) == deals.get(i).sell()) {
				out.add(i);
			}
		}
		return out;
	}

	private int spreads(Mode mode) {
		if (mode == Mode.INDEX) {
			return 1;
		}
		return Math.max(1, (entries(mode).size() + PER_PAGE * 2 - 1) / (PER_PAGE * 2));
	}

	private boolean turning() {
		return turnFrom != null && System.nanoTime() / 1_000_000L - turnStart < TURN_MS;
	}

	private void turnTo(Spread to, int dir) {
		if (turning() || to.equals(spread)) {
			return;
		}
		turnFrom = spread;
		turnDir = dir;
		turnStart = System.nanoTime() / 1_000_000L;
		spread = to;
		if (client != null) {
			client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.ITEM_BOOK_PAGE_TURN, 0.9F + client.world.random.nextFloat() * 0.2F));
		}
	}

	private void next() {
		if (spread.mode() != Mode.INDEX && spread.index() + 1 < spreads(spread.mode())) {
			turnTo(new Spread(spread.mode(), spread.index() + 1), 1);
		}
	}

	private void previous() {
		if (spread.mode() == Mode.INDEX) {
			return;
		}
		if (spread.index() > 0) {
			turnTo(new Spread(spread.mode(), spread.index() - 1), -1);
		} else {
			turnTo(new Spread(Mode.INDEX, 0), -1);
		}
	}

	// ------------------------------------------------------------------ dibujar

	@Override
	public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
		// sin oscurecer: se ve el mostrador detrás
	}

	@Override
	protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		float g = CatalogSession.gui();
		if (g <= 0.0F) {
			return;
		}
		scale = Math.min(width * 0.94F / W, height * 0.9F / H);
		originX = (width - W * scale) / 2.0F;
		float finalY = (height - H * scale) / 2.0F;
		originY = finalY + (1.0F - g) * (height - finalY + 10.0F);
		float vx = (mouseX - originX) / scale;
		float vy = (mouseY - originY) / scale;
		boolean interactive = CatalogSession.reading() && !turning();

		context.getMatrices().push();
		context.getMatrices().translate(originX, originY, 0.0F);
		context.getMatrices().scale(scale, scale, 1.0F);
		// la encuadernación
		context.fill(-2, 2, W + 2, H + 6, 0x60000000);
		context.fill(0, 0, W, H, 0xFF2E1D12);
		context.fill(2, 2, W - 2, H - 2, 0xFF4A3020);
		context.fill(SPINE - 3, 0, SPINE + 3, H, 0xFF24160D);

		Hover hover = interactive ? hoverAt(vx, vy) : null;
		if (turning()) {
			float t = (System.nanoTime() / 1_000_000L - turnStart) / TURN_MS;
			float k = Math.abs(MathHelper.cos((float) Math.PI * t));
			Spread from = turnFrom;
			Spread to = spread;
			// debajo: la página que no se mueve de cada lado
			page(context, turnDir > 0 ? from : to, true, null);
			page(context, turnDir > 0 ? to : from, false, null);
			// la hoja que gira sobre el lomo
			boolean firstHalf = t < 0.5F;
			Spread sheet = firstHalf ? from : to;
			boolean sheetLeft = turnDir > 0 ? !firstHalf : firstHalf;
			context.getMatrices().push();
			context.getMatrices().translate(SPINE, 0.0F, 50.0F);
			context.getMatrices().scale(Math.max(0.02F, k), 1.0F, 1.0F);
			context.getMatrices().translate(-SPINE, 0.0F, 0.0F);
			page(context, sheet, sheetLeft, null);
			int shadow = (int) ((1.0F - k) * 110.0F);
			int px = sheetLeft ? LEFT_X : RIGHT_X;
			context.fill(px, PAGE_Y, px + PAGE_W, PAGE_Y + PAGE_H, shadow << 24);
			context.getMatrices().pop();
		} else {
			page(context, spread, true, hover);
			page(context, spread, false, hover);
		}
		hands(context);
		context.getMatrices().pop();

		// la ayuda abajo y la descripción de lo que señala
		if (g >= 1.0F) {
			Text keys = Text.translatable("gui.evolvedwarfare.catalog.keys");
			context.drawCenteredTextWithShadow(textRenderer, keys, width / 2, height - 11, 0xC0C0C0);
		}
		if (hover != null && hover.deal >= 0 && hover.illustration) {
			context.drawItemTooltip(textRenderer, handler.deals().get(hover.deal).stack(), mouseX, mouseY);
		}
	}

	/** Las manos sujetando el libro por abajo; la que pasa la hoja la acompaña. */
	private void hands(DrawContext context) {
		float lx = -30;
		float rx = W - 34;
		float ly = H - 40;
		float ry = H - 40;
		if (turning()) {
			float t = (System.nanoTime() / 1_000_000L - turnStart) / TURN_MS;
			float arc = MathHelper.sin((float) Math.PI * t);
			if (turnDir > 0) {
				rx = MathHelper.lerp(arc, rx, SPINE - 20);
				ry = ry - arc * 60.0F;
			} else {
				lx = MathHelper.lerp(arc, lx, SPINE - 44);
				ly = ly - arc * 60.0F;
			}
		}
		context.getMatrices().push();
		context.getMatrices().translate(0.0F, 0.0F, 120.0F);
		context.drawTexture(HAND_LEFT, (int) lx, (int) ly, 64, 64, 0.0F, 0.0F, 64, 64, 64, 64);
		context.drawTexture(HAND_RIGHT, (int) rx, (int) ry, 64, 64, 0.0F, 0.0F, 64, 64, 64, 64);
		context.getMatrices().pop();
	}

	/** Una página: el papel, la sombra del lomo y lo que lleva. */
	private void page(DrawContext context, Spread s, boolean left, @Nullable Hover hover) {
		int x = left ? LEFT_X : RIGHT_X;
		context.drawTexture(PAPER, x, PAGE_Y, PAGE_W, PAGE_H, left ? 0.0F : 60.0F, 0.0F, 196, 256, 256, 256);
		// sombra hacia el lomo
		for (int i = 0; i < 12; i++) {
			int a = (int) ((12 - i) * 5.5F);
			int sx = left ? x + PAGE_W - 1 - i : x + i;
			context.fill(sx, PAGE_Y, sx + 1, PAGE_Y + PAGE_H, a << 24);
		}
		if (s.mode() == Mode.INDEX) {
			if (left) {
				masthead(context, x);
			} else {
				choices(context, x, hover);
			}
			return;
		}
		List<Integer> list = entries(s.mode());
		int first = s.index() * PER_PAGE * 2 + (left ? 0 : PER_PAGE);
		header(context, x, s, left);
		if (list.isEmpty() && left) {
			center(context, Text.translatable("gui.evolvedwarfare.catalog.empty").formatted(Formatting.ITALIC), x + PAGE_W / 2, 120, 1.0F, FADED);
		}
		for (int i = 0; i < PER_PAGE; i++) {
			int n = first + i;
			if (n >= list.size()) {
				break;
			}
			int dealIndex = list.get(n);
			int ey = PAGE_Y + ENTRIES_Y + i * ENTRY_H;
			entry(context, x + 10, ey, dealIndex, s.mode(), hover != null && hover.deal == dealIndex);
		}
		footer(context, x, s, left, hover);
	}

	// ------------------------------------------------------------------ índice

	private TraderType type() {
		return handler.traderType();
	}

	private void masthead(DrawContext context, int x) {
		int cx = x + PAGE_W / 2;
		int y = PAGE_Y + 12;
		rule(context, x + 12, y, PAGE_W - 24, 2);
		y += 6;
		Text house = Text.translatable("gui.evolvedwarfare.catalog.house." + type().id());
		y = wrapCentered(context, house.copy().formatted(Formatting.BOLD), cx, y, 1.45F, PAGE_W - 24, INK) + 4;
		center(context, Text.translatable("gui.evolvedwarfare.catalog.dept." + type().id()).formatted(Formatting.BOLD), cx, y, 0.72F, INK);
		y += 10;
		rule(context, x + 12, y, PAGE_W - 24, 1);
		rule(context, x + 12, y + 3, PAGE_W - 24, 1);
		y += 8;
		wrapCentered(context, Text.translatable("gui.evolvedwarfare.catalog.slogan"), cx, y, 0.62F, PAGE_W - 30, INK);
		// el grabado grande del oficio
		float size = 84.0F;
		Engraving.draw(context, type().tool(), cx - size / 2.0F, PAGE_Y + 92, size);
		int ly = PAGE_Y + 190;
		rule(context, x + 30, ly, PAGE_W - 60, 1);
		int percent = handler.pricePercent();
		Text prices = percent > 0 ? Text.translatable("gui.evolvedwarfare.catalog.markup", percent)
				: Text.translatable("gui.evolvedwarfare.catalog.no_markup");
		center(context, Text.translatable("gui.evolvedwarfare.catalog.level", handler.cityLevel(), prices), cx, ly + 6, 0.7F, INK);
		center(context, Text.translatable("gui.evolvedwarfare.catalog.coins", coins()).formatted(Formatting.BOLD), cx, ly + 20, 0.85F, INK);
		center(context, title.copy().formatted(Formatting.ITALIC), cx, ly + 36, 0.7F, FADED);
	}

	private String coins() {
		return client != null && client.player != null ? String.format(Locale.ROOT, "%,d", Market.countCoins(client.player.getInventory())) : "0";
	}

	private static final int BOX_X = 16;
	private static final int BOX_W = PAGE_W - 32;
	private static final int BOX_H = 74;
	private static final int BUY_Y = PAGE_Y + 56;
	private static final int SELL_Y = BUY_Y + BOX_H + 18;

	private void choices(DrawContext context, int x, @Nullable Hover hover) {
		int cx = x + PAGE_W / 2;
		center(context, Text.translatable("gui.evolvedwarfare.catalog.ask").formatted(Formatting.BOLD), cx, PAGE_Y + 20, 1.1F, INK);
		rule(context, x + 20, PAGE_Y + 36, PAGE_W - 40, 1);
		choice(context, x, BUY_Y, Text.translatable("gui.evolvedwarfare.catalog.buy"),
				Text.translatable("gui.evolvedwarfare.catalog.buy.desc", entries(Mode.BUY).size()), hover != null && hover.choice == Mode.BUY);
		choice(context, x, SELL_Y, Text.translatable("gui.evolvedwarfare.catalog.sell"),
				Text.translatable("gui.evolvedwarfare.catalog.sell.desc", entries(Mode.SELL).size()), hover != null && hover.choice == Mode.SELL);
	}

	private void choice(DrawContext context, int x, int y, Text word, Text desc, boolean hot) {
		int bx = x + BOX_X;
		int ink = hot ? 0xFFE6E2D8 : INK;
		if (hot) {
			context.fill(bx, y, bx + BOX_W, y + BOX_H, INK);
		}
		box(context, bx, y, BOX_W, BOX_H, INK);
		box(context, bx + 3, y + 3, BOX_W - 6, BOX_H - 6, hot ? 0xFFE6E2D8 : INK);
		center(context, word.copy().formatted(Formatting.BOLD), bx + BOX_W / 2, y + 14, 2.6F, ink);
		center(context, desc, bx + BOX_W / 2, y + 50, 0.68F, ink);
	}

	// ------------------------------------------------------------------ páginas de artículos

	private void header(DrawContext context, int x, Spread s, boolean left) {
		int cx = x + PAGE_W / 2;
		Text house = Text.translatable("gui.evolvedwarfare.catalog.house." + type().id()).copy();
		center(context, house.copy().formatted(Formatting.BOLD), cx, PAGE_Y + 8, 0.6F, FADED);
		rule(context, x + 10, PAGE_Y + 15, PAGE_W - 20, 1);
		Text word = Text.translatable(s.mode() == Mode.BUY ? "gui.evolvedwarfare.catalog.buy" : "gui.evolvedwarfare.catalog.sell");
		center(context, word.copy().formatted(Formatting.BOLD), cx, PAGE_Y + 19, 1.5F, INK);
		rule(context, x + 10, PAGE_Y + 33, PAGE_W - 20, 2);
		if (left) {
			text(context, Text.translatable("gui.evolvedwarfare.catalog.index"), x + 10, PAGE_Y + 22, 0.6F, FADED);
		} else {
			Text money = Text.translatable("gui.evolvedwarfare.catalog.coins", coins());
			float w = textRenderer.getWidth(money) * 0.55F;
			text(context, money, x + PAGE_W - 10 - w, PAGE_Y + 23, 0.55F, FADED);
		}
	}

	private void footer(DrawContext context, int x, Spread s, boolean left, @Nullable Hover hover) {
		int cx = x + PAGE_W / 2;
		int page = s.index() * 2 + (left ? 1 : 2);
		int fy = PAGE_Y + PAGE_H - 16;
		rule(context, x + 10, fy - 3, PAGE_W - 20, 1);
		center(context, Text.literal("— " + page + " —"), cx, fy + 2, 0.65F, FADED);
		if (left && (s.index() > 0 || s.mode() != Mode.INDEX)) {
			text(context, Text.translatable("gui.evolvedwarfare.catalog.prev").formatted(Formatting.BOLD), x + 10, fy + 2, 0.6F,
					hover != null && hover.nav < 0 ? INK : FADED);
		}
		if (!left && s.index() + 1 < spreads(s.mode())) {
			Text next = Text.translatable("gui.evolvedwarfare.catalog.next").formatted(Formatting.BOLD);
			text(context, next, x + PAGE_W - 10 - textRenderer.getWidth(next) * 0.6F, fy + 2, 0.6F, hover != null && hover.nav > 0 ? INK : FADED);
		}
		if (hover != null && hover.deal >= 0 && !left) {
			Text hint = Text.translatable(s.mode() == Mode.BUY ? "gui.evolvedwarfare.catalog.click_buy" : "gui.evolvedwarfare.catalog.click_sell");
			center(context, hint, cx, fy - 10, 0.5F, FADED);
		}
	}

	private void entry(DrawContext context, int x, int y, int dealIndex, Mode mode, boolean hot) {
		Deal deal = handler.deals().get(dealIndex);
		int w = PAGE_W - 20;
		if (hot) {
			context.fill(x - 2, y, x + w + 2, y + ENTRY_H - 2, 0x18000000);
		}
		// el grabado, en su recuadro
		box(context, x, y + 3, 42, 42, INK);
		Engraving.draw(context, deal.prototype(), x + 3, y + 6, 36.0F);
		int tx = x + 47;
		int priceW = 50;
		int textW = w - 47 - priceW - 4;
		// nombre
		Text name = Text.literal(deal.prototype().getName().getString().toUpperCase(Locale.ROOT)).formatted(Formatting.BOLD);
		text(context, trim(name, textW / 0.95F), tx, y + 4, 0.95F, INK);
		// rareza y calidad
		MutableText line = Text.translatable(deal.tier().translationKey()).copy();
		Quality quality = deal.quality();
		if (quality != null) {
			line.append(" · ").append(Text.translatable(quality.translationKey()));
		}
		text(context, Text.literal(line.getString().toUpperCase(Locale.ROOT)), tx, y + 14, 0.55F, FADED);
		// la frase de reclamo
		Text blurb = Text.translatable(mode == Mode.SELL ? "gui.evolvedwarfare.catalog.blurb.sell"
				: "gui.evolvedwarfare.catalog.blurb." + Math.floorMod(Registries.ITEM.getId(deal.item()).hashCode(), 6));
		int by = y + 22;
		for (OrderedText row : textRenderer.wrapLines(StringVisitable.plain(blurb.getString()), (int) (textW / 0.55F))) {
			if (by > y + 34) {
				break;
			}
			context.getMatrices().push();
			context.getMatrices().translate(tx, by, 0.0F);
			context.getMatrices().scale(0.55F, 0.55F, 1.0F);
			context.drawText(textRenderer, row, 0, 0, INK, false);
			context.getMatrices().pop();
			by += 6;
		}
		// lo que tienes (al vender)
		if (mode == Mode.SELL && client != null && client.player != null) {
			int have = TraderScreenHandler.count(client.player.getInventory(), deal);
			text(context, Text.translatable("gui.evolvedwarfare.catalog.have", have).formatted(Formatting.ITALIC), tx, y + 38, 0.55F, have >= deal.count() ? INK : FADED);
		}
		// el precio, grande, a la derecha
		int px = x + w - priceW;
		if (mode == Mode.SELL) {
			text(context, Text.translatable("gui.evolvedwarfare.catalog.pays"), px, y + 2, 0.5F, FADED);
		}
		String price = String.format(Locale.ROOT, "%,d", deal.price());
		float big = price.length() > 5 ? 1.15F : price.length() > 3 ? 1.5F : 2.0F;
		Text dollars = Text.literal("$").formatted(Formatting.BOLD);
		text(context, dollars, px, y + 9, 0.8F, INK);
		Text amount = Text.literal(price).formatted(Formatting.BOLD);
		text(context, amount, px + priceW - textRenderer.getWidth(amount) * big, y + 8, big, INK);
		text(context, Text.literal("GC"), px + priceW - textRenderer.getWidth("GC") * 0.5F, y + 8 + 9 * big, 0.5F, FADED);
		text(context, Text.translatable("gui.evolvedwarfare.catalog.lot", deal.count()), px, y + 30, 0.52F, INK);
		int left = handler.left(dealIndex);
		if (left > 0) {
			text(context, Text.translatable(mode == Mode.BUY ? "gui.evolvedwarfare.catalog.stock" : "gui.evolvedwarfare.catalog.wants", left), px,
					y + 37, 0.48F, FADED);
		}
		// línea de puntos
		for (int dx = 0; dx < w; dx += 3) {
			context.fill(x + dx, y + ENTRY_H - 2, x + dx + 1, y + ENTRY_H - 1, 0x90000000);
		}
		if (left <= 0) {
			stamp(context, x + w / 2 + 6, y + 22, Text.translatable("gui.evolvedwarfare.catalog.sold_out"));
		}
	}

	/** Un sello de goma torcido encima (como «EN PROPIEDAD»). */
	private void stamp(DrawContext context, float cx, float cy, Text text) {
		context.getMatrices().push();
		context.getMatrices().translate(cx, cy, 30.0F);
		context.getMatrices().multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-12.0F));
		context.getMatrices().scale(1.4F, 1.4F, 1.0F);
		Text bold = text.copy().formatted(Formatting.BOLD);
		int w = textRenderer.getWidth(bold);
		box(context, -w / 2 - 3, -6, w + 6, 13, 0xB0303030);
		context.drawText(textRenderer, bold, -w / 2, -3, 0xB0303030, false);
		context.getMatrices().pop();
	}

	// ------------------------------------------------------------------ ayudas de imprenta

	private void text(DrawContext context, Text text, float x, float y, float k, int color) {
		context.getMatrices().push();
		context.getMatrices().translate(x, y, 0.0F);
		context.getMatrices().scale(k, k, 1.0F);
		context.drawText(textRenderer, text, 0, 0, color, false);
		context.getMatrices().pop();
	}

	private void center(DrawContext context, Text text, float cx, float y, float k, int color) {
		text(context, text, cx - textRenderer.getWidth(text) * k / 2.0F, y, k, color);
	}

	/** Texto centrado en varias líneas; devuelve dónde acaba. */
	private int wrapCentered(DrawContext context, Text text, float cx, float y, float k, int width, int color) {
		for (OrderedText row : textRenderer.wrapLines(text, (int) (width / k))) {
			float w = textRenderer.getWidth(row) * k;
			context.getMatrices().push();
			context.getMatrices().translate(cx - w / 2.0F, y, 0.0F);
			context.getMatrices().scale(k, k, 1.0F);
			context.drawText(textRenderer, row, 0, 0, color, false);
			context.getMatrices().pop();
			y += 10 * k;
		}
		return (int) y;
	}

	private Text trim(Text text, float width) {
		if (textRenderer.getWidth(text) <= width) {
			return text;
		}
		String s = text.getString();
		while (s.length() > 1 && textRenderer.getWidth(Text.literal(s + "…").formatted(Formatting.BOLD)) > width) {
			s = s.substring(0, s.length() - 1);
		}
		return Text.literal(s + "…").formatted(Formatting.BOLD);
	}

	private static void rule(DrawContext context, int x, int y, int w, int thick) {
		context.fill(x, y, x + w, y + thick, INK);
	}

	private static void box(DrawContext context, int x, int y, int w, int h, int color) {
		context.fill(x, y, x + w, y + 1, color);
		context.fill(x, y + h - 1, x + w, y + h, color);
		context.fill(x, y, x + 1, y + h, color);
		context.fill(x + w - 1, y, x + w, y + h, color);
	}

	// ------------------------------------------------------------------ ratón y teclado

	/** Qué hay debajo del ratón (en coordenadas del lienzo). */
	private record Hover(int deal, boolean illustration, @Nullable Mode choice, int nav) {
	}

	@Nullable
	private Hover hoverAt(float vx, float vy) {
		if (spread.mode() == Mode.INDEX) {
			int bx = RIGHT_X + BOX_X;
			if (vx >= bx && vx < bx + BOX_W) {
				if (vy >= BUY_Y && vy < BUY_Y + BOX_H) {
					return new Hover(-1, false, Mode.BUY, 0);
				}
				if (vy >= SELL_Y && vy < SELL_Y + BOX_H) {
					return new Hover(-1, false, Mode.SELL, 0);
				}
			}
			return null;
		}
		int fy = PAGE_Y + PAGE_H - 16;
		if (vy >= fy - 1 && vy < fy + 10) {
			if (vx >= LEFT_X && vx < LEFT_X + 70) {
				return new Hover(-1, false, null, -1);
			}
			if (vx >= RIGHT_X + PAGE_W - 80 && vx < RIGHT_X + PAGE_W) {
				return new Hover(-1, false, null, 1);
			}
		}
		if (vy >= PAGE_Y + 18 && vy < PAGE_Y + 30 && vx >= LEFT_X + 8 && vx < LEFT_X + 60) {
			return new Hover(-1, false, Mode.INDEX, 0);
		}
		boolean left = vx < SPINE;
		int x = left ? LEFT_X : RIGHT_X;
		if (vx < x + 8 || vx >= x + PAGE_W - 8) {
			return null;
		}
		int row = (int) Math.floor((vy - PAGE_Y - ENTRIES_Y) / ENTRY_H);
		if (row < 0 || row >= PER_PAGE) {
			return null;
		}
		List<Integer> list = entries(spread.mode());
		int n = spread.index() * PER_PAGE * 2 + (left ? 0 : PER_PAGE) + row;
		if (n >= list.size()) {
			return null;
		}
		float ry = vy - PAGE_Y - ENTRIES_Y - row * ENTRY_H;
		boolean illustration = vx >= x + 10 && vx < x + 52 && ry >= 3 && ry < 45;
		return new Hover(list.get(n), illustration, null, 0);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (!CatalogSession.reading() || turning() || button != 0 || client == null || client.interactionManager == null) {
			return true;
		}
		Hover hover = hoverAt((float) ((mouseX - originX) / scale), (float) ((mouseY - originY) / scale));
		if (hover == null) {
			return true;
		}
		if (hover.choice == Mode.BUY || hover.choice == Mode.SELL) {
			turnTo(new Spread(hover.choice, 0), 1);
		} else if (hover.choice == Mode.INDEX) {
			turnTo(new Spread(Mode.INDEX, 0), -1);
		} else if (hover.nav > 0) {
			next();
		} else if (hover.nav < 0) {
			previous();
		} else if (hover.deal >= 0) {
			client.interactionManager.clickButton(handler.syncId, hover.deal * 2 + (Screen.hasShiftDown() ? 1 : 0));
		}
		return true;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
		if (CatalogSession.reading()) {
			if (vertical < 0) {
				next();
			} else if (vertical > 0) {
				previous();
			}
		}
		return true;
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		return true;
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
		return true;
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (keyCode == GLFW.GLFW_KEY_ESCAPE || (client != null && client.options.inventoryKey.matchesKey(keyCode, scanCode))) {
			close();
			return true;
		}
		if (!CatalogSession.reading()) {
			return true;
		}
		if (keyCode == GLFW.GLFW_KEY_RIGHT || keyCode == GLFW.GLFW_KEY_D || keyCode == GLFW.GLFW_KEY_PAGE_DOWN) {
			next();
		} else if (keyCode == GLFW.GLFW_KEY_LEFT || keyCode == GLFW.GLFW_KEY_A || keyCode == GLFW.GLFW_KEY_PAGE_UP) {
			previous();
		} else if (keyCode == GLFW.GLFW_KEY_BACKSPACE || keyCode == GLFW.GLFW_KEY_HOME) {
			turnTo(new Spread(Mode.INDEX, 0), -1);
		}
		return true;
	}

	@Override
	public boolean shouldPause() {
		return false;
	}
}
