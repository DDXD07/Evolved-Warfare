package com.evolvedwarfare.client.screen;

import com.evolvedwarfare.blueprint.ScanArea;
import com.evolvedwarfare.blueprint.Scanner;
import com.evolvedwarfare.item.StructureScannerItem;
import com.evolvedwarfare.network.BlueprintPayloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.Direction;

/** Escáner de planos: nombre, frente del edificio y guardar. */
public class ScanScreen extends Screen {
	private static final int W = 240;
	private static final int H = 128;
	private static final Direction[] FRONTS = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};

	private final Hand hand;
	private TextFieldWidget nameField;
	private ButtonWidget frontButton;
	private ButtonWidget scanButton;
	private Direction front = Direction.NORTH;
	private int left;
	private int top;

	public ScanScreen(Hand hand) {
		super(Text.translatable("gui.evolvedwarfare.scanner.title"));
		this.hand = hand;
	}

	@Override
	protected void init() {
		left = (width - W) / 2;
		top = (height - H) / 2;
		if (client != null && client.player != null) {
			// el frente mira hacia el jugador (que está mirando el edificio)
			front = client.player.getHorizontalFacing().getOpposite();
		}
		nameField = new TextFieldWidget(textRenderer, left + 10, top + 44, W - 20, 16, Text.translatable("gui.evolvedwarfare.scanner.name"));
		nameField.setMaxLength(32);
		nameField.setPlaceholder(Text.translatable("gui.evolvedwarfare.scanner.name").formatted(Formatting.DARK_GRAY));
		addDrawableChild(nameField);
		setInitialFocus(nameField);
		frontButton = addDrawableChild(ButtonWidget.builder(Text.empty(), b -> cycleFront())
				.dimensions(left + 10, top + 66, W - 20, 18).build());
		scanButton = addDrawableChild(ButtonWidget.builder(Text.translatable("gui.evolvedwarfare.scanner.scan"), b -> scan())
				.dimensions(left + 10, top + 100, (W - 26) / 2, 18).build());
		addDrawableChild(ButtonWidget.builder(Text.translatable("gui.cancel"), b -> close())
				.dimensions(left + 16 + (W - 26) / 2, top + 100, (W - 26) / 2, 18).build());
		updateFront();
	}

	private BlockBox box() {
		if (client == null || client.player == null) {
			return null;
		}
		ItemStack stack = client.player.getStackInHand(hand);
		ScanArea area = StructureScannerItem.area(stack);
		return area.box();
	}

	private void cycleFront() {
		for (int i = 0; i < FRONTS.length; i++) {
			if (FRONTS[i] == front) {
				front = FRONTS[(i + 1) % FRONTS.length];
				break;
			}
		}
		updateFront();
	}

	private void updateFront() {
		frontButton.setMessage(Text.translatable("gui.evolvedwarfare.scanner.front",
				Text.translatable("gui.evolvedwarfare.direction." + front.asString())));
		BlockBox box = box();
		scanButton.active = box != null && Scanner.fits(box);
	}

	private void scan() {
		ClientPlayNetworking.send(new BlueprintPayloads.Scan(nameField.getText(), front.getId(), hand == Hand.MAIN_HAND));
		close();
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		super.render(context, mouseX, mouseY, delta);
		context.drawText(textRenderer, title, left + 10, top + 8, 0x404040, false);
		BlockBox box = box();
		if (box != null) {
			Text size = Text.translatable("item.evolvedwarfare.structure_scanner.size", box.getBlockCountX(), box.getBlockCountY(),
					box.getBlockCountZ());
			context.drawText(textRenderer, size, left + 10, top + 22, Scanner.fits(box) ? 0x2E7D32 : 0xC62828, false);
		}
		context.drawText(textRenderer, Text.translatable("gui.evolvedwarfare.scanner.hint"), left + 10, top + 34, 0x6A6A6A, false);
		context.drawText(textRenderer, Text.translatable("gui.evolvedwarfare.scanner.cost"), left + 10, top + 88, 0x6A6A6A, false);
	}

	@Override
	public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
		super.renderBackground(context, mouseX, mouseY, delta);
		Panels.panel(context, left, top, W, H);
	}

	@Override
	public boolean shouldPause() {
		return false;
	}
}
