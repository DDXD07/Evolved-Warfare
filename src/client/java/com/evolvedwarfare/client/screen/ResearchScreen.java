package com.evolvedwarfare.client.screen;

import com.evolvedwarfare.client.ClientResearch;
import com.evolvedwarfare.network.ResearchPayloads;
import com.evolvedwarfare.research.Tech;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

/**
 * El árbol de investigación: tres columnas (niveles) con las técnicas y líneas a lo que piden antes. Verde: hecha;
 * dorado: se puede empezar; azul: en curso; gris: aún no. Abajo, la técnica elegida: qué desbloquea, qué cuesta (en rojo
 * lo que te falta) y cuánto tarda en esta mesa, con el botón de empezar (o de cancelar, devolviendo la mitad).
 */
public class ResearchScreen extends Screen {
	private static final int W = 340;
	private static final int H = 236;
	private static final int NODE_W = 96;
	private static final int NODE_H = 16;
	private static final int GAP_X = 14;
	private static final int ROW_H = 18;
	private static final int TREE_Y = 30;
	private static final int INFO_Y = TREE_Y + Tech.ROWS * ROW_H + 6;
	private static final int TEXT = 0x404040;

	private final BlockPos desk;
	private int left;
	private int top;
	private Tech selected = Tech.MILITARY_SMITHING;
	private ButtonWidget startButton;
	private ButtonWidget cancelButton;

	public ResearchScreen(BlockPos desk) {
		super(Text.translatable("gui.evolvedwarfare.research.title"));
		this.desk = desk;
		Tech current = ClientResearch.current();
		if (current != null) {
			selected = current;
		}
	}

	@Override
	protected void init() {
		left = (width - W) / 2;
		top = Math.max(4, (height - H) / 2);
		startButton = addDrawableChild(ButtonWidget.builder(Text.translatable("gui.evolvedwarfare.research.start"),
				b -> send(ResearchPayloads.Action.START)).dimensions(left + W - 82, top + H - 26, 74, 18).build());
		cancelButton = addDrawableChild(ButtonWidget.builder(Text.translatable("gui.evolvedwarfare.research.cancel"),
				b -> send(ResearchPayloads.Action.CANCEL)).dimensions(left + W - 82, top + H - 26, 74, 18).build());
		updateButtons();
	}

	private void send(int action) {
		ClientPlayNetworking.send(new ResearchPayloads.Action(action, selected.id(), desk));
	}

	private boolean available(Tech tech) {
		return !ClientResearch.done(tech) && (tech.requires == null || ClientResearch.done(tech.requires));
	}

	private int nodeX(Tech tech) {
		return left + 8 + tech.column * (NODE_W + GAP_X);
	}

	private int nodeY(Tech tech) {
		return top + TREE_Y + tech.row * ROW_H;
	}

	private int count(Tech.Cost cost) {
		PlayerEntity player = client != null ? client.player : null;
		if (player == null) {
			return 0;
		}
		int total = 0;
		for (int i = 0; i < player.getInventory().size(); i++) {
			ItemStack stack = player.getInventory().getStack(i);
			if (cost.matches(stack)) {
				total += stack.getCount();
			}
		}
		return total;
	}

	private void updateButtons() {
		Tech current = ClientResearch.current();
		boolean isCurrent = current == selected;
		cancelButton.visible = isCurrent;
		startButton.visible = !isCurrent && !ClientResearch.done(selected);
		boolean affordable = client != null && client.player != null && client.player.isCreative();
		if (!affordable) {
			affordable = true;
			for (Tech.Cost cost : selected.cost()) {
				if (count(cost) < cost.count()) {
					affordable = false;
				}
			}
		}
		startButton.active = current == null && available(selected) && affordable;
	}

	@Override
	public void tick() {
		super.tick();
		if (client != null && client.world != null && client.world.getTime() % 20 == 0) {
			ClientPlayNetworking.send(new ResearchPayloads.Action(ResearchPayloads.Action.REFRESH, "", desk));
		}
		updateButtons();
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		for (Tech tech : Tech.values()) {
			int x = nodeX(tech);
			int y = nodeY(tech);
			if (mouseX >= x && mouseX < x + NODE_W && mouseY >= y && mouseY < y + NODE_H) {
				selected = tech;
				updateButtons();
				return true;
			}
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
		super.renderBackground(context, mouseX, mouseY, delta);
		Panels.panel(context, left, top, W, H);
	}

	private static String time(int ticks) {
		int seconds = Math.max(0, ticks / 20);
		return seconds >= 60 ? (seconds / 60) + ":" + String.format("%02d", seconds % 60) : seconds + " s";
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		super.render(context, mouseX, mouseY, delta);
		context.drawText(textRenderer, title, left + 8, top + 7, TEXT, false);
		// en curso, arriba a la derecha
		Tech current = ClientResearch.current();
		Text status = current == null ? Text.translatable("gui.evolvedwarfare.research.idle")
				: Text.translatable("gui.evolvedwarfare.research.current", Text.translatable(current.translationKey()),
						time(ClientResearch.remainingNow()));
		context.drawText(textRenderer, status, left + W - 8 - textRenderer.getWidth(status), top + 7, TEXT, false);
		if (current != null) {
			int barX = left + 8;
			int barW = W - 16;
			context.fill(barX, top + 18, barX + barW, top + 22, 0xFF555555);
			context.fill(barX, top + 18, barX + (int) (barW * ClientResearch.progress), top + 22, 0xFF3FA0E0);
		}
		// líneas a lo que piden antes
		for (Tech tech : Tech.values()) {
			if (tech.requires != null) {
				int fromX = nodeX(tech.requires) + NODE_W;
				int fromY = nodeY(tech.requires) + NODE_H / 2;
				int toX = nodeX(tech);
				int toY = nodeY(tech) + NODE_H / 2;
				int midX = fromX + GAP_X / 2;
				int color = ClientResearch.done(tech.requires) ? 0xFF2E6B2A : 0xFF6B6B6B;
				context.fill(fromX, fromY, midX + 1, fromY + 1, color);
				context.fill(midX, Math.min(fromY, toY), midX + 1, Math.max(fromY, toY) + 1, color);
				context.fill(midX, toY, toX, toY + 1, color);
			}
		}
		// nodos
		for (Tech tech : Tech.values()) {
			int x = nodeX(tech);
			int y = nodeY(tech);
			int fill = ClientResearch.done(tech) ? 0xFF3E7B3A : tech == current ? 0xFF2E5C8A : available(tech) ? 0xFF8A6D1E : 0xFF5A5A5A;
			int border = tech == selected ? 0xFFFFFFFF : 0xFF2A2A2A;
			context.fill(x, y, x + NODE_W, y + NODE_H, border);
			context.fill(x + 1, y + 1, x + NODE_W - 1, y + NODE_H - 1, fill);
			context.getMatrices().push();
			context.getMatrices().translate(x + 1, y + 1, 0);
			context.getMatrices().scale(0.875F, 0.875F, 1.0F);
			context.drawItem(new ItemStack(tech.icon()), 0, 0);
			context.getMatrices().pop();
			String name = textRenderer.trimToWidth(Text.translatable(tech.translationKey()).getString(), NODE_W - 20);
			context.drawText(textRenderer, name, x + 17, y + 4, 0xFFFFFF, true);
			if (mouseX >= x && mouseX < x + NODE_W && mouseY >= y && mouseY < y + NODE_H) {
				context.drawTooltip(textRenderer, Text.translatable(tech.translationKey()), mouseX, mouseY);
			}
		}
		drawInfo(context, mouseX, mouseY);
	}

	private void drawInfo(DrawContext context, int mouseX, int mouseY) {
		int x = left + 8;
		int y = top + INFO_Y;
		context.fill(left + 6, y - 3, left + W - 6, y - 2, 0xFF8B8B8B);
		Text state = ClientResearch.done(selected) ? Text.translatable("gui.evolvedwarfare.research.state.done").formatted(Formatting.DARK_GREEN)
				: ClientResearch.current() == selected ? Text.translatable("gui.evolvedwarfare.research.state.current").formatted(Formatting.BLUE)
				: available(selected) ? Text.translatable("gui.evolvedwarfare.research.state.available").formatted(Formatting.GOLD)
				: Text.translatable("gui.evolvedwarfare.research.state.locked", Text.translatable(selected.requires != null
						? selected.requires.translationKey() : selected.translationKey())).formatted(Formatting.DARK_GRAY);
		context.drawText(textRenderer, Text.translatable(selected.translationKey()).formatted(Formatting.BOLD), x, y, TEXT, false);
		context.drawText(textRenderer, state, left + W - 8 - textRenderer.getWidth(state), y, 0xFFFFFF, false);
		List<OrderedText> lines = textRenderer.wrapLines(Text.translatable(selected.translationKey() + ".desc"), W - 16);
		for (int i = 0; i < Math.min(2, lines.size()); i++) {
			context.drawText(textRenderer, lines.get(i), x, y + 11 + i * 9, 0x5A5A5A, false);
		}
		// lo que desbloquea
		int rowY = y + 31;
		context.drawText(textRenderer, Text.translatable(selected.isBonus() ? "gui.evolvedwarfare.research.bonus"
				: "gui.evolvedwarfare.research.unlocks"), x, rowY + 5, TEXT, false);
		int ix = x + 62;
		if (!selected.isBonus()) {
			List<Item> unlocks = selected.unlocks();
			for (Item item : unlocks) {
				if (ix > left + W - 24) {
					break;
				}
				ItemStack stack = new ItemStack(item);
				context.drawItem(stack, ix, rowY);
				if (mouseX >= ix && mouseX < ix + 16 && mouseY >= rowY && mouseY < rowY + 16) {
					context.drawItemTooltip(textRenderer, stack, mouseX, mouseY);
				}
				ix += 17;
			}
		}
		// coste y tiempo
		int costY = y + 50;
		context.drawText(textRenderer, Text.translatable("gui.evolvedwarfare.research.cost"), x, costY + 5, TEXT, false);
		ix = x + 62;
		boolean creative = client != null && client.player != null && client.player.isCreative();
		for (Tech.Cost cost : selected.cost()) {
			ItemStack stack = new ItemStack(cost.icon(), cost.count());
			context.drawItem(stack, ix, costY);
			String n = Integer.toString(cost.count());
			boolean enough = creative || count(cost) >= cost.count();
			context.getMatrices().push();
			context.getMatrices().translate(0, 0, 200);
			context.drawText(textRenderer, n, ix + 17 - textRenderer.getWidth(n), costY + 9, enough ? 0xFFFFFF : 0xFF5555, true);
			context.getMatrices().pop();
			if (mouseX >= ix && mouseX < ix + 16 && mouseY >= costY && mouseY < costY + 16) {
				context.drawItemTooltip(textRenderer, stack, mouseX, mouseY);
			}
			ix += 22;
		}
		int ticks = (int) Math.ceil(selected.ticks() / Math.max(0.01, ClientResearch.speed));
		Text time = Text.translatable("gui.evolvedwarfare.research.time", time(ticks),
				String.format("%.0f", ClientResearch.speed * 100.0F));
		context.drawText(textRenderer, time, ix + 6, costY + 5, 0x5A5A5A, false);
	}

	@Override
	public boolean shouldPause() {
		return false;
	}
}
