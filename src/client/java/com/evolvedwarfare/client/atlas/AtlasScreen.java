package com.evolvedwarfare.client.atlas;

import com.evolvedwarfare.atlas.Atlas;
import com.evolvedwarfare.atlas.AtlasState;
import com.evolvedwarfare.logistics.Market;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.world.World;
import org.lwjgl.glfw.GLFW;

/**
 * El Atlas: el mapa de lo explorado en tinta sobre papel, que se arrastra para moverlo y se acerca con la rueda. Salen las
 * estructuras vistas (al pasar el ratón, su nombre), los territorios con su nombre y los jugadores. Con "Reclamar" se
 * marcan chunks (clic o arrastrando; el botón derecho sigue moviendo el mapa), se les pone nombre y se reclaman por Glory
 * Coins; "Liberar" suelta los tuyos que estén marcados.
 */
public class AtlasScreen extends Screen {
	private static final float[] ZOOMS = {0.25F, 0.5F, 1.0F, 2.0F, 4.0F, 8.0F};
	private static final int LEATHER = 0xFF4A2F1B, LEATHER_DARK = 0xFF2E1D10, PAPER = 0xFFEEE5CD, INK = 0xFF2A231C, INK_SOFT = 0xFF6B5D4C, GOLD = 0xFFC9A441;
	private static int zoomIndex = 3;
	private double viewX, viewZ;
	private boolean claiming, dragging, painting, paintAdd;
	private int dragButton = -1;
	private final LongOpenHashSet selection = new LongOpenHashSet();
	private TextFieldWidget name;
	private ButtonWidget claimToggle, claimButton, releaseButton, clearButton;
	private int left, top, right, bottom;

	public AtlasScreen() {
		super(Text.translatable("item.evolvedwarfare.atlas"));
	}

	private float zoom() {
		return ZOOMS[zoomIndex];
	}

	@Override
	protected void init() {
		MinecraftClient c = MinecraftClient.getInstance();
		if (viewX == 0 && viewZ == 0 && c.player != null) {
			viewX = c.player.getX();
			viewZ = c.player.getZ();
		}
		left = 22;
		top = 26;
		right = width - 22;
		bottom = height - 50;
		int y = height - 40;
		addDrawableChild(ButtonWidget.builder(Text.translatable("gui.evolvedwarfare.atlas.center"), b -> center()).dimensions(left, y, 60, 18).build());
		addDrawableChild(ButtonWidget.builder(Text.literal("−"), b -> zoomBy(-1, (left + right) / 2.0, (top + bottom) / 2.0)).dimensions(left + 63, y, 18, 18).build());
		addDrawableChild(ButtonWidget.builder(Text.literal("+"), b -> zoomBy(1, (left + right) / 2.0, (top + bottom) / 2.0)).dimensions(left + 83, y, 18, 18).build());
		claimToggle = addDrawableChild(ButtonWidget.builder(Text.translatable("gui.evolvedwarfare.atlas.claim_mode"), b -> {
			claiming = !claiming;
			selection.clear();
			refresh();
		}).dimensions(left + 106, y, 90, 18).build());
		int nx = left + 202, nw = MathHelper.clamp(right - nx - 177, 60, 110);
		name = new TextFieldWidget(textRenderer, nx, y, nw, 18, Text.translatable("gui.evolvedwarfare.atlas.name"));
		name.setMaxLength(32);
		name.setText(defaultName());
		addDrawableChild(name);
		claimButton = addDrawableChild(ButtonWidget.builder(Text.translatable("gui.evolvedwarfare.atlas.claim"), b -> send(false))
				.dimensions(nx + nw + 4, y, 62, 18).build());
		releaseButton = addDrawableChild(ButtonWidget.builder(Text.translatable("gui.evolvedwarfare.atlas.release"), b -> send(true))
				.dimensions(nx + nw + 69, y, 56, 18).build());
		clearButton = addDrawableChild(ButtonWidget.builder(Text.translatable("gui.evolvedwarfare.atlas.clear"), b -> selection.clear())
				.dimensions(nx + nw + 128, y, 50, 18).build());
		refresh();
		ClientPlayNetworking.send(new Atlas.ViewPayload(true));
	}

	private String defaultName() {
		MinecraftClient c = MinecraftClient.getInstance();
		if (c.player == null) {
			return "";
		}
		for (Atlas.TerritoryView t : AtlasClient.territories) {
			if (t.owner().equals(c.player.getUuid())) {
				return t.name();
			}
		}
		return Text.translatable("gui.evolvedwarfare.atlas.default_name", c.player.getName().getString()).getString();
	}

	private void refresh() {
		claimToggle.setMessage(Text.translatable(claiming ? "gui.evolvedwarfare.atlas.view_mode" : "gui.evolvedwarfare.atlas.claim_mode"));
		name.visible = claiming;
		claimButton.visible = releaseButton.visible = clearButton.visible = claiming;
	}

	private void send(boolean remove) {
		if (selection.isEmpty()) {
			return;
		}
		ClientPlayNetworking.send(new Atlas.ClaimPayload(name.getText(), selection.toLongArray(), remove));
		selection.clear();
	}

	private void center() {
		MinecraftClient c = MinecraftClient.getInstance();
		if (c.player != null) {
			viewX = c.player.getX();
			viewZ = c.player.getZ();
		}
	}

	@Override
	public void removed() {
		ClientPlayNetworking.send(new Atlas.ViewPayload(false));
		super.removed();
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	@Override
	public void tick() {
		int n = selection.size();
		claimButton.active = n > 0 && !name.getText().isBlank();
		releaseButton.active = n > 0;
	}

	// ------------------------------------------------------------------ de la pantalla al mundo

	private double cx() {
		return (left + right) / 2.0;
	}

	private double cy() {
		return (top + bottom) / 2.0;
	}

	private double sx(double bx) {
		return cx() + (bx - viewX) * zoom();
	}

	private double sy(double bz) {
		return cy() + (bz - viewZ) * zoom();
	}

	private double bx(double sx) {
		return viewX + (sx - cx()) / zoom();
	}

	private double bz(double sy) {
		return viewZ + (sy - cy()) / zoom();
	}

	private boolean inMap(double mx, double my) {
		return mx >= left && mx < right && my >= top && my < bottom;
	}

	private long chunkAt(double mx, double my) {
		return ChunkPos.toLong(MathHelper.floor(bx(mx)) >> 4, MathHelper.floor(bz(my)) >> 4);
	}

	private void zoomBy(int d, double mx, double my) {
		double bx = bx(mx), bz = bz(my);
		zoomIndex = MathHelper.clamp(zoomIndex + d, 0, ZOOMS.length - 1);
		// que lo de debajo del ratón se quede donde está
		viewX = bx - (mx - cx()) / zoom();
		viewZ = bz - (my - cy()) / zoom();
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double h, double v) {
		if (inMap(mx, my) && v != 0) {
			zoomBy(v > 0 ? 1 : -1, mx, my);
			return true;
		}
		return super.mouseScrolled(mx, my, h, v);
	}

	@Override
	public boolean mouseClicked(double mx, double my, int button) {
		if (super.mouseClicked(mx, my, button)) {
			return true;
		}
		if (!inMap(mx, my)) {
			return false;
		}
		setFocused(null);
		if (claiming && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
			long k = chunkAt(mx, my);
			paintAdd = !selection.contains(k);
			painting = true;
			paint(k);
			return true;
		}
		dragging = true;
		dragButton = button;
		return true;
	}

	private void paint(long k) {
		if (!AtlasClient.explored(k)) {
			return;
		}
		if (paintAdd) {
			selection.add(k);
		} else {
			selection.remove(k);
		}
	}

	@Override
	public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
		if (painting) {
			paint(chunkAt(mx, my));
			return true;
		}
		if (dragging) {
			viewX -= dx / zoom();
			viewZ -= dy / zoom();
			return true;
		}
		return super.mouseDragged(mx, my, button, dx, dy);
	}

	@Override
	public boolean mouseReleased(double mx, double my, int button) {
		painting = false;
		dragging = false;
		return super.mouseReleased(mx, my, button);
	}

	@Override
	public boolean keyPressed(int key, int scan, int mods) {
		if (name.isFocused() && key != GLFW.GLFW_KEY_ESCAPE) {
			return name.keyPressed(key, scan, mods) || true;
		}
		return super.keyPressed(key, scan, mods);
	}

	// ------------------------------------------------------------------ dibujar

	@Override
	public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
		ctx.fill(0, 0, width, height, 0xA0000000);
		// las tapas del libro y el papel
		ctx.fill(8, 8, width - 8, height - 8, LEATHER_DARK);
		ctx.fill(10, 10, width - 10, height - 10, LEATHER);
		ctx.fill(left - 4, top - 4, right + 4, bottom + 4, 0xFF8C7354);
		ctx.fill(left - 3, top - 3, right + 3, bottom + 3, PAPER);
	}

	@Override
	public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
		super.render(ctx, mouseX, mouseY, delta);
		ctx.drawCenteredTextWithShadow(textRenderer, title.copy().formatted(net.minecraft.util.Formatting.BOLD), width / 2, 13, GOLD);
		ctx.enableScissor(left, top, right, bottom);
		drawTiles(ctx);
		if (claiming) {
			drawGrid(ctx);
		}
		drawTerritories(ctx);
		if (claiming) {
			drawSelection(ctx);
		}
		String hover = drawMarkers(ctx, mouseX, mouseY);
		drawPlayers(ctx, delta);
		ctx.disableScissor();
		drawInfo(ctx, mouseX, mouseY);
		MinecraftClient c = MinecraftClient.getInstance();
		if (c.world != null && c.world.getRegistryKey() != World.OVERWORLD) {
			ctx.drawCenteredTextWithShadow(textRenderer, Text.translatable("gui.evolvedwarfare.atlas.other_world"), width / 2, top + 8, 0xFFFF8060);
		}
		if (hover != null) {
			ctx.drawTooltip(textRenderer, Text.literal(hover), mouseX, mouseY);
		}
	}

	private void drawTiles(DrawContext ctx) {
		float z = zoom();
		double b0x = bx(left), b1x = bx(right), b0z = bz(top), b1z = bz(bottom);
		int t0x = Math.floorDiv(MathHelper.floor(b0x), AtlasClient.TILE), t1x = Math.floorDiv(MathHelper.floor(b1x), AtlasClient.TILE);
		int t0z = Math.floorDiv(MathHelper.floor(b0z), AtlasClient.TILE), t1z = Math.floorDiv(MathHelper.floor(b1z), AtlasClient.TILE);
		int ox = MathHelper.floor(viewX), oz = MathHelper.floor(viewZ);
		MatrixStack m = ctx.getMatrices();
		m.push();
		m.translate(cx(), cy(), 0);
		m.scale(z, z, 1);
		m.translate(-(viewX - ox), -(viewZ - oz), 0);
		for (int tx = t0x; tx <= t1x; tx++) {
			for (int tz = t0z; tz <= t1z; tz++) {
				Identifier id = AtlasClient.tile(tx, tz);
				if (id != null) {
					ctx.drawTexture(id, tx * AtlasClient.TILE - ox, tz * AtlasClient.TILE - oz, 0, 0, AtlasClient.TILE, AtlasClient.TILE, AtlasClient.TILE,
							AtlasClient.TILE);
				}
			}
		}
		m.pop();
	}

	private void rect(DrawContext ctx, double x0, double y0, double x1, double y1, int color) {
		ctx.fill((int) Math.floor(x0), (int) Math.floor(y0), (int) Math.ceil(x1), (int) Math.ceil(y1), color);
	}

	private void drawGrid(DrawContext ctx) {
		if (zoom() < 0.5F) {
			return;
		}
		int c0 = MathHelper.floor(bx(left)) >> 4, c1 = MathHelper.floor(bx(right)) >> 4;
		int r0 = MathHelper.floor(bz(top)) >> 4, r1 = MathHelper.floor(bz(bottom)) >> 4;
		for (int c = c0; c <= c1 + 1; c++) {
			int x = (int) Math.round(sx(c * 16));
			ctx.fill(x, top, x + 1, bottom, 0x30000000);
		}
		for (int r = r0; r <= r1 + 1; r++) {
			int y = (int) Math.round(sy(r * 16));
			ctx.fill(left, y, right, y + 1, 0x30000000);
		}
	}

	/** Un chunk con borde donde el de al lado no es del mismo grupo. */
	private void chunkCell(DrawContext ctx, long k, int fill, int border, java.util.function.LongPredicate same) {
		int x = ChunkPos.getPackedX(k), z = ChunkPos.getPackedZ(k);
		double x0 = sx(x * 16), x1 = sx(x * 16 + 16), y0 = sy(z * 16), y1 = sy(z * 16 + 16);
		if (x1 < left || x0 > right || y1 < top || y0 > bottom) {
			return;
		}
		rect(ctx, x0, y0, x1, y1, fill);
		double t = Math.max(1.0, Math.min(2.0, zoom()));
		if (!same.test(ChunkPos.toLong(x, z - 1))) {
			rect(ctx, x0, y0, x1, y0 + t, border);
		}
		if (!same.test(ChunkPos.toLong(x, z + 1))) {
			rect(ctx, x0, y1 - t, x1, y1, border);
		}
		if (!same.test(ChunkPos.toLong(x - 1, z))) {
			rect(ctx, x0, y0, x0 + t, y1, border);
		}
		if (!same.test(ChunkPos.toLong(x + 1, z))) {
			rect(ctx, x1 - t, y0, x1, y1, border);
		}
	}

	private void drawTerritories(DrawContext ctx) {
		for (Atlas.TerritoryView t : AtlasClient.territories) {
			int rgb = t.color() & 0xFFFFFF;
			double sumX = 0, sumZ = 0;
			int seen = 0;
			for (long k : t.chunks()) {
				if (!AtlasClient.explored(k)) {
					continue;
				}
				chunkCell(ctx, k, 0x48000000 | rgb, 0xE0000000 | rgb, n -> AtlasClient.OWNER.get(n) == t);
				sumX += ChunkPos.getPackedX(k) * 16 + 8;
				sumZ += ChunkPos.getPackedZ(k) * 16 + 8;
				seen++;
			}
			if (seen > 0) {
				// el nombre, en una cinta en el centro
				int px = (int) sx(sumX / seen), py = (int) sy(sumZ / seen);
				String label = t.name();
				int w = textRenderer.getWidth(label);
				ctx.fill(px - w / 2 - 4, py - 6, px + w / 2 + 4, py + 6, 0xD0000000 | rgb);
				ctx.fill(px - w / 2 - 4, py + 5, px + w / 2 + 4, py + 6, 0xFF000000);
				ctx.drawText(textRenderer, label, px - w / 2, py - 4, 0xFFFFFFFF, true);
			}
		}
	}

	private void drawSelection(DrawContext ctx) {
		for (long k : selection) {
			chunkCell(ctx, k, 0x50E0B030, 0xFFE0B030, selection::contains);
		}
	}

	// ------------------------------------------------------------------ las estructuras

	private static final String[] CITY = {".X...X.", ".X...X.", ".XXXXX.", "XXXXXXX", "XX.X.XX", "XX.X.XX", "XXXXXXX"};
	private static final String[] VILLAGE = {"...X...", "..XXX..", ".XXXXX.", "XXXXXXX", ".X...X.", ".X.X.X.", ".XXXXX."};
	private static final String[] CAMP = {"X......", "XXX....", "XX.....", "X..X...", "X.XXX..", "XXXXXX.", "XXXXXXX"};
	private static final String[] TOWER = {"X.X.X..", "XXXXX..", ".XXX...", ".X.X...", ".XXX...", ".XXX...", "XXXXX.."};
	private static final String[] TEMPLE = {"...X...", "..XXX..", "..X.X..", ".XXXXX.", ".X.X.X.", "XXXXXXX", "XXXXXXX"};
	private static final String[] WRECK = {"...X...", "..XXX..", "...X...", "...X...", "X..X..X", ".X.X.X.", "..XXX.."};

	private static String[] glyph(String id) {
		String path = id.substring(id.indexOf(':') + 1);
		if (path.contains("city")) {
			return CITY;
		}
		if (path.startsWith("village")) {
			return VILLAGE;
		}
		if (path.startsWith("bandit") || path.startsWith("viking") || path.startsWith("pirate") || path.startsWith("mercenary") || path.equals("pillager_outpost")) {
			return CAMP;
		}
		if (path.equals("castle") || path.equals("watchtower")) {
			return TOWER;
		}
		if (path.startsWith("shipwreck") || path.startsWith("ocean_ruin")) {
			return WRECK;
		}
		return TEMPLE;
	}

	static String markerName(AtlasState.Marker m) {
		String id = m.id();
		int c = id.indexOf(':');
		String ns = id.substring(0, c), path = id.substring(c + 1);
		String kind = Text.translatableWithFallback("structure." + ns + "." + path, pretty(path)).getString();
		return m.name().isEmpty() ? kind : m.name() + " (" + kind + ")";
	}

	private static String pretty(String path) {
		String s = path.replace('_', ' ');
		return s.substring(0, 1).toUpperCase(Locale.ROOT) + s.substring(1);
	}

	/** Dibuja las estructuras vistas; devuelve el nombre de la que está bajo el ratón (o null). */
	private String drawMarkers(DrawContext ctx, int mouseX, int mouseY) {
		String hover = null;
		List<AtlasState.Marker> list = new ArrayList<>(AtlasClient.MARKERS.values());
		for (AtlasState.Marker m : list) {
			if (!AtlasClient.explored(ChunkPos.toLong(m.x() >> 4, m.z() >> 4))) {
				continue;
			}
			int px = (int) sx(m.x() + 0.5), py = (int) sy(m.z() + 0.5);
			if (px < left - 8 || px > right + 8 || py < top - 8 || py > bottom + 8) {
				continue;
			}
			String[] g = glyph(m.id());
			int s = zoom() >= 2.0F ? 2 : 1;
			int gx = px - 3 * s, gy = py - 3 * s;
			ctx.fill(gx - 1, gy - 1, gx + 7 * s + 1, gy + 7 * s + 1, 0xC0EEE5CD);
			for (int y = 0; y < 7; y++) {
				for (int x = 0; x < 7; x++) {
					if (g[y].charAt(x) == 'X') {
						ctx.fill(gx + x * s, gy + y * s, gx + x * s + s, gy + y * s + s, INK);
					}
				}
			}
			boolean city = g == CITY;
			if (!m.name().isEmpty() && (city || zoom() >= 1.0F)) {
				int w = textRenderer.getWidth(m.name());
				ctx.drawText(textRenderer, m.name(), px - w / 2, gy + 7 * s + 2, city ? INK : INK_SOFT, false);
			}
			if (Math.abs(mouseX - px) <= 4 * s && Math.abs(mouseY - py) <= 4 * s) {
				hover = markerName(m);
			}
		}
		return hover;
	}

	// ------------------------------------------------------------------ los jugadores

	private void arrow(DrawContext ctx, double x, double y, float yaw, int color) {
		MatrixStack m = ctx.getMatrices();
		m.push();
		m.translate(x, y, 0);
		m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(yaw + 180.0F));
		ctx.fill(-3, -1, 4, 5, 0xFF000000);
		ctx.fill(-2, -4, 3, 4, 0xFF000000);
		ctx.fill(-2, 0, 3, 4, color);
		ctx.fill(-1, -3, 2, 3, color);
		m.pop();
	}

	private void drawPlayers(DrawContext ctx, float delta) {
		MinecraftClient c = MinecraftClient.getInstance();
		for (Atlas.PlayerDot p : AtlasClient.players) {
			if (c.player != null && p.id().equals(c.player.getUuid())) {
				continue;
			}
			double x = sx(p.x()), y = sy(p.z());
			arrow(ctx, x, y, p.yaw(), 0xFF3060D0);
			int w = textRenderer.getWidth(p.name());
			ctx.drawText(textRenderer, p.name(), (int) x - w / 2, (int) y + 7, 0xFF203878, false);
		}
		if (c.player != null && c.world != null && c.world.getRegistryKey() == World.OVERWORLD) {
			double x = MathHelper.lerp(delta, c.player.prevX, c.player.getX()), z = MathHelper.lerp(delta, c.player.prevZ, c.player.getZ());
			arrow(ctx, sx(x), sy(z), c.player.getYaw(delta), 0xFFD03020);
		}
	}

	private void drawInfo(DrawContext ctx, int mouseX, int mouseY) {
		MinecraftClient c = MinecraftClient.getInstance();
		int y = height - 20;
		if (inMap(mouseX, mouseY)) {
			int bx = MathHelper.floor(bx(mouseX)), bz = MathHelper.floor(bz(mouseY));
			Atlas.TerritoryView t = AtlasClient.OWNER.get(ChunkPos.toLong(bx >> 4, bz >> 4));
			String where = bx + ", " + bz;
			if (t != null && AtlasClient.explored(ChunkPos.toLong(bx >> 4, bz >> 4))) {
				where += "  ·  " + Text.translatable("gui.evolvedwarfare.atlas.territory", t.name(), t.ownerName()).getString();
			}
			ctx.drawText(textRenderer, where, left, y, 0xFFE8D8B0, false);
		}
		Text info;
		if (claiming) {
			int coins = c.player == null ? 0 : Market.countCoins(c.player.getInventory());
			int cost = selection.size() * Atlas.claimCost();
			info = Text.translatable("gui.evolvedwarfare.atlas.cost", selection.size(), cost, coins).formatted(cost > coins ? net.minecraft.util.Formatting.RED
					: net.minecraft.util.Formatting.GOLD);
		} else {
			info = Text.translatable("gui.evolvedwarfare.atlas.hint");
		}
		ctx.drawText(textRenderer, info, right - textRenderer.getWidth(info), y, 0xFFE8D8B0, false);
		String scale = Text.translatable("gui.evolvedwarfare.atlas.scale", zoom() >= 1 ? (int) zoom() + ":1" : "1:" + (int) (1 / zoom())).getString();
		ctx.drawText(textRenderer, scale, right - textRenderer.getWidth(scale), 13, 0xFFE8D8B0, false);
	}
}
