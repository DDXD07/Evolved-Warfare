package com.evolvedwarfare.client.screen;

import com.evolvedwarfare.client.ClientDiplomacy;
import com.evolvedwarfare.faction.Diplomacy;
import com.evolvedwarfare.faction.Faction;
import com.evolvedwarfare.faction.Reputation;
import com.evolvedwarfare.faction.Standing;
import com.evolvedwarfare.logistics.Market;
import com.evolvedwarfare.network.DiplomacyActionPayload;
import com.evolvedwarfare.network.DiplomacyDataPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Diplomacia (con el sello de embajador): una ficha por facción con la reputación (barra de -100 a 100 con las marcas
 * de enemistad, amistad y alianza), el trato, lo que queda de tregua o de espera para pedir ayuda, y los botones
 * Tributo, Tregua/Alianza/Romper, Pedir ayuda y Mercenario.
 */
public class DiplomacyScreen extends Screen {
	private static final int W = 320;
	private static final int ROW_H = 64;
	private static final int TOP = 30;
	private static final int TEXT = 0x404040;
	private static final int BUTTON_W = 72;

	private final Faction[] factions = Faction.values();
	private final ButtonWidget[] tribute = new ButtonWidget[factions.length];
	private final ButtonWidget[] treaty = new ButtonWidget[factions.length];
	private final ButtonWidget[] aid = new ButtonWidget[factions.length];
	private final ButtonWidget[] mercenary = new ButtonWidget[factions.length];
	private int left;
	private int top;
	private ItemStack coin = ItemStack.EMPTY;

	public DiplomacyScreen() {
		super(Text.translatable("gui.evolvedwarfare.diplomacy.title"));
	}

	private int panelHeight() {
		return TOP + factions.length * ROW_H + 8;
	}

	@Override
	protected void init() {
		left = (width - W) / 2;
		top = Math.max(4, (height - panelHeight()) / 2);
		coin = new ItemStack(Market.currency());
		for (int i = 0; i < factions.length; i++) {
			Faction faction = factions[i];
			int by = top + TOP + i * ROW_H + 42;
			int bx = left + 10;
			tribute[i] = addDrawableChild(ButtonWidget.builder(Text.empty(), b -> send(faction, Diplomacy.TRIBUTE))
					.dimensions(bx, by, BUTTON_W, 16).build());
			treaty[i] = addDrawableChild(ButtonWidget.builder(Text.empty(), b -> send(faction, treatyAction(faction)))
					.dimensions(bx + (BUTTON_W + 4), by, BUTTON_W, 16).build());
			aid[i] = addDrawableChild(ButtonWidget.builder(Text.translatable("gui.evolvedwarfare.diplomacy.aid"), b -> send(faction, Diplomacy.AID))
					.dimensions(bx + 2 * (BUTTON_W + 4), by, BUTTON_W, 16).build());
			mercenary[i] = addDrawableChild(ButtonWidget.builder(Text.empty(), b -> send(faction, Diplomacy.MERCENARY))
					.dimensions(bx + 3 * (BUTTON_W + 4), by, BUTTON_W, 16).build());
		}
		updateButtons();
	}

	private void send(Faction faction, int action) {
		ClientPlayNetworking.send(new DiplomacyActionPayload(faction.ordinal(), action));
	}

	private static Standing standing(DiplomacyDataPayload.Status status) {
		return Standing.byOrdinal(status.standing());
	}

	private int treatyAction(Faction faction) {
		Standing standing = standing(ClientDiplomacy.get(faction));
		if (standing == Standing.HOSTILE || standing == Standing.TRUCE) {
			return Diplomacy.TRUCE;
		}
		return standing == Standing.ALLIED ? Diplomacy.BREAK_ALLIANCE : Diplomacy.ALLIANCE;
	}

	@Override
	public void tick() {
		super.tick();
		updateButtons();
	}

	private void updateButtons() {
		for (int i = 0; i < factions.length; i++) {
			if (tribute[i] == null) {
				return;
			}
			Faction faction = factions[i];
			DiplomacyDataPayload.Status status = ClientDiplomacy.get(faction);
			Standing standing = standing(status);
			int rep = status.reputation();

			tribute[i].setMessage(Text.translatable("gui.evolvedwarfare.diplomacy.tribute", Diplomacy.TRIBUTE_COINS));
			tribute[i].active = rep < faction.maxReputation();
			tribute[i].setTooltip(Tooltip.of(Text.translatable("gui.evolvedwarfare.diplomacy.tribute.tip", Diplomacy.TRIBUTE_COINS,
					Diplomacy.TRIBUTE_GAIN)));

			switch (standing) {
				case HOSTILE, TRUCE -> {
					int cost = Diplomacy.truceCost(rep);
					treaty[i].setMessage(Text.translatable("gui.evolvedwarfare.diplomacy.truce", cost));
					treaty[i].active = standing == Standing.HOSTILE;
					treaty[i].setTooltip(Tooltip.of(Text.translatable("gui.evolvedwarfare.diplomacy.truce.tip", cost)));
				}
				case ALLIED -> {
					treaty[i].setMessage(Text.translatable("gui.evolvedwarfare.diplomacy.break"));
					treaty[i].active = true;
					treaty[i].setTooltip(Tooltip.of(Text.translatable("gui.evolvedwarfare.diplomacy.break.tip", Diplomacy.BETRAYAL)));
				}
				default -> {
					treaty[i].setMessage(Text.translatable("gui.evolvedwarfare.diplomacy.alliance", Diplomacy.ALLIANCE_COINS));
					treaty[i].active = faction.canAlly() && rep >= Reputation.ALLIED_AT;
					treaty[i].setTooltip(Tooltip.of(faction.canAlly()
							? Text.translatable("gui.evolvedwarfare.diplomacy.alliance.tip", Reputation.ALLIED_AT, Diplomacy.ALLIANCE_COINS)
							: Text.translatable("gui.evolvedwarfare.diplomacy.alliance.never")));
				}
			}

			aid[i].active = standing == Standing.ALLIED && aidLeft(status) <= 0;
			aid[i].setTooltip(Tooltip.of(Text.translatable("gui.evolvedwarfare.diplomacy.aid.tip", Diplomacy.AID_SIZE)));

			mercenary[i].setMessage(Text.translatable("gui.evolvedwarfare.diplomacy.mercenary", Diplomacy.MERCENARY_COINS));
			mercenary[i].active = standing.atLeast(Standing.FRIENDLY);
			mercenary[i].setTooltip(Tooltip.of(Text.translatable("gui.evolvedwarfare.diplomacy.mercenary.tip", Diplomacy.MERCENARY_COINS,
					Reputation.FRIENDLY_AT)));
		}
	}

	private static int truceLeft(DiplomacyDataPayload.Status status) {
		return status.truceLeft() - ClientDiplomacy.ticksSinceUpdate();
	}

	private static int aidLeft(DiplomacyDataPayload.Status status) {
		return status.aidLeft() - ClientDiplomacy.ticksSinceUpdate();
	}

	/** Minutos reales que quedan, redondeando hacia arriba. */
	private static int minutes(int ticks) {
		return Math.max(1, (ticks + 1199) / 1200);
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		super.render(context, mouseX, mouseY, delta);
		context.drawText(textRenderer, title, left + 10, top + 8, TEXT, false);
		// tus monedas, arriba a la derecha
		int coins = client != null && client.player != null ? Market.countCoins(client.player.getInventory()) : 0;
		String money = Integer.toString(coins);
		int mx = left + W - 28 - textRenderer.getWidth(money);
		context.drawText(textRenderer, money, mx, top + 8, 0x7A5A00, false);
		context.drawItem(coin, left + W - 26, top + 3);

		for (int i = 0; i < factions.length; i++) {
			drawFaction(context, factions[i], left + 6, top + TOP + i * ROW_H);
		}
	}

	private void drawFaction(DrawContext context, Faction faction, int x, int y) {
		DiplomacyDataPayload.Status status = ClientDiplomacy.get(faction);
		Standing standing = standing(status);
		int rep = status.reputation();
		// ficha
		context.fill(x, y, x + W - 12, y + ROW_H - 4, 0xFFB5B5B5);
		context.fill(x, y, x + W - 12, y + 1, 0xFF8B8B8B);
		context.fill(x, y, x + 1, y + ROW_H - 4, 0xFF8B8B8B);
		// nombre y trato
		context.drawText(textRenderer, faction.displayName().formatted(Formatting.BOLD), x + 4, y + 4, 0xFFFFFF, true);
		Text standingText = Text.translatable(standing.translationKey()).formatted(standing.color());
		context.drawText(textRenderer, standingText, x + W - 16 - textRenderer.getWidth(standingText), y + 4, 0xFFFFFF, true);
		// barra de reputación
		int barX = x + 4;
		int barW = W - 20;
		int barY = y + 16;
		context.fill(barX - 1, barY - 1, barX + barW + 1, barY + 7, 0xFF373737);
		context.fill(barX, barY, barX + barW, barY + 6, 0xFF6B6B6B);
		int zero = barX + barW / 2;
		int pos = barX + (rep - Reputation.MIN) * barW / (Reputation.MAX - Reputation.MIN);
		int color = standing == Standing.ALLIED ? 0xFF3FC8E8 : rep >= Reputation.FRIENDLY_AT ? 0xFF4CAF50
				: rep < Reputation.HOSTILE_BELOW ? 0xFFD03A2E : 0xFFB0B0B0;
		context.fill(Math.min(zero, pos), barY, Math.max(zero, pos) + 1, barY + 6, color);
		for (int mark : new int[] {Reputation.HOSTILE_BELOW, Reputation.FRIENDLY_AT, Reputation.ALLIED_AT}) {
			int mxp = barX + (mark - Reputation.MIN) * barW / (Reputation.MAX - Reputation.MIN);
			context.fill(mxp, barY - 1, mxp + 1, barY + 7, 0xFF202020);
		}
		context.fill(zero, barY - 2, zero + 1, barY + 8, 0xFFFFFFFF);
		// valor y nota (tregua, ayuda)
		context.drawText(textRenderer, Text.translatable("gui.evolvedwarfare.diplomacy.reputation", rep), x + 4, y + 27, TEXT, false);
		Text note = null;
		if (standing == Standing.TRUCE && truceLeft(status) > 0) {
			note = Text.translatable("gui.evolvedwarfare.diplomacy.truce_left", minutes(truceLeft(status)));
		} else if (standing == Standing.ALLIED) {
			note = aidLeft(status) > 0 ? Text.translatable("gui.evolvedwarfare.diplomacy.aid_left", minutes(aidLeft(status)))
					: Text.translatable("gui.evolvedwarfare.diplomacy.aid_ready");
		} else if (!faction.respectsWhiteFlag()) {
			note = Text.translatable("gui.evolvedwarfare.diplomacy.no_flag");
		}
		if (note != null) {
			context.drawText(textRenderer, note, x + W - 16 - textRenderer.getWidth(note), y + 27, 0x505050, false);
		}
	}

	@Override
	public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
		super.renderBackground(context, mouseX, mouseY, delta);
		Panels.panel(context, left, top, W, panelHeight());
	}

	@Override
	public boolean shouldPause() {
		return false;
	}
}
