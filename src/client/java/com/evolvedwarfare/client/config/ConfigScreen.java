package com.evolvedwarfare.client.config;

import com.evolvedwarfare.config.EwConfig;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Configuración del mod: una pestaña por apartado y, en cada fila, la opción con su valor y los botones − y + (o
 * un interruptor). Mayúsculas + clic cambia de diez en diez. Se guarda al salir. En un servidor manda la
 * configuración del servidor (/evolvedwarfare config).
 */
public class ConfigScreen extends Screen {
	private static final int ROW = 22;
	private final Screen parent;
	private final List<String> categories = EwConfig.categories();
	private int category;
	private int page;

	public ConfigScreen(Screen parent) {
		super(Text.translatable("config.evolvedwarfare.title"));
		this.parent = parent;
	}

	private List<EwConfig.Option> options() {
		List<EwConfig.Option> out = new ArrayList<>();
		for (EwConfig.Option option : EwConfig.OPTIONS) {
			if (option.category().equals(categories.get(category))) {
				out.add(option);
			}
		}
		return out;
	}

	private int perPage() {
		return Math.max(3, (height - 96) / ROW);
	}

	@Override
	protected void init() {
		clearChildren();
		int tabWidth = Math.min(90, (width - 20) / categories.size());
		int tabsX = (width - tabWidth * categories.size()) / 2;
		for (int i = 0; i < categories.size(); i++) {
			int index = i;
			ButtonWidget tab = ButtonWidget.builder(Text.translatable("config.evolvedwarfare.category." + categories.get(i)), b -> {
				category = index;
				page = 0;
				init();
			}).dimensions(tabsX + i * tabWidth, 28, tabWidth - 2, 18).build();
			tab.active = i != category;
			addDrawableChild(tab);
		}
		List<EwConfig.Option> options = options();
		int pages = Math.max(1, (options.size() + perPage() - 1) / perPage());
		page = Math.min(page, pages - 1);
		int left = width / 2 - 150;
		int y = 56;
		EwConfig config = EwConfig.get();
		for (int i = page * perPage(); i < Math.min(options.size(), (page + 1) * perPage()); i++) {
			EwConfig.Option option = options.get(i);
			if (option.isBoolean()) {
				addDrawableChild(ButtonWidget.builder(Text.empty(), b -> {
					config.set(option, config.value(option) >= 0.5 ? 0.0 : 1.0);
					init();
				}).dimensions(left + 196, y, 104, 18).build()).setMessage(config.value(option) >= 0.5
						? ScreenTexts.ON.copy().formatted(Formatting.GREEN) : ScreenTexts.OFF.copy().formatted(Formatting.RED));
			} else {
				addDrawableChild(ButtonWidget.builder(Text.literal("−"), b -> {
					config.set(option, config.value(option) - option.step() * (hasShiftDown() ? 10 : 1));
					init();
				}).dimensions(left + 196, y, 20, 18).build());
				addDrawableChild(ButtonWidget.builder(Text.literal("+"), b -> {
					config.set(option, config.value(option) + option.step() * (hasShiftDown() ? 10 : 1));
					init();
				}).dimensions(left + 280, y, 20, 18).build());
			}
			y += ROW;
		}
		if (pages > 1) {
			addDrawableChild(ButtonWidget.builder(Text.literal("<"), b -> {
				page = (page + pages - 1) % pages;
				init();
			}).dimensions(width / 2 - 60, height - 52, 20, 18).build());
			addDrawableChild(ButtonWidget.builder(Text.literal(">"), b -> {
				page = (page + 1) % pages;
				init();
			}).dimensions(width / 2 + 40, height - 52, 20, 18).build());
		}
		ButtonWidget reset = ButtonWidget.builder(Text.translatable("config.evolvedwarfare.reset"), b -> {
			EwConfig.reset();
			init();
		}).dimensions(width / 2 - 154, height - 28, 150, 20).build();
		reset.setTooltip(Tooltip.of(Text.translatable("config.evolvedwarfare.reset.tip")));
		addDrawableChild(reset);
		addDrawableChild(ButtonWidget.builder(ScreenTexts.DONE, b -> close()).dimensions(width / 2 + 4, height - 28, 150, 20).build());
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		super.render(context, mouseX, mouseY, delta);
		context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 10, 0xFFFFFF);
		List<EwConfig.Option> options = options();
		int left = width / 2 - 150;
		int y = 56;
		EwConfig config = EwConfig.get();
		for (int i = page * perPage(); i < Math.min(options.size(), (page + 1) * perPage()); i++) {
			EwConfig.Option option = options.get(i);
			Text name = Text.translatable(option.translationKey());
			context.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(name, 190).getString(), left, y + 5, 0xE0E0E0);
			if (!option.isBoolean()) {
				String value = config.display(option);
				context.drawCenteredTextWithShadow(textRenderer, value, left + 248, y + 5, 0xFFFF80);
			}
			if (mouseX >= left && mouseX < left + 190 && mouseY >= y && mouseY < y + 18) {
				context.drawOrderedTooltip(textRenderer, textRenderer.wrapLines(Text.translatable(option.translationKey() + ".tip"), 220),
						mouseX, mouseY);
			}
			y += ROW;
		}
		int pages = Math.max(1, (options.size() + perPage() - 1) / perPage());
		if (pages > 1) {
			context.drawCenteredTextWithShadow(textRenderer, (page + 1) + " / " + pages, width / 2, height - 47, 0xA0A0A0);
		}
		if (client != null && client.getCurrentServerEntry() != null) {
			context.drawCenteredTextWithShadow(textRenderer, Text.translatable("config.evolvedwarfare.server_note"), width / 2, height - 64,
					0xFF9060);
		}
	}

	@Override
	public void close() {
		EwConfig.save();
		if (client != null) {
			client.setScreen(parent);
		}
	}
}
