package com.evolvedwarfare.client.screen;

import com.evolvedwarfare.EvolvedWarfare;
import com.evolvedwarfare.registry.ModBlocks;
import com.evolvedwarfare.registry.ModItems;
import com.evolvedwarfare.weapon.WeaponType;
import com.evolvedwarfare.work.Profession;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.PageTurnWidget;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

/**
 * La guía de Evolved Warfare: un libro abierto con cuatro cintas a la derecha, una por sección (ciudades y comercio, tu
 * gente, guerra y habilidades). Cada sección son dos páginas con tres cosas cortas, cada una con su icono. Se cambia de
 * sección con las cintas, las flechas de abajo, las del teclado o 1-4.
 */
public class GuideScreen extends Screen {
	private static final Identifier TEXTURE = EvolvedWarfare.id("textures/gui/guide.png");
	public static final int BOOK_W = 292;
	public static final int BOOK_H = 190;
	private static final int PAGE_W = 120;
	private static final int LEFT_X = 18;
	private static final int RIGHT_X = 156;
	private static final int TOP = 16;
	private static final int TAB_W = 26;
	private static final int TAB_H = 22;
	private static final int ENTRIES = 3;
	/** Alto de una línea, hueco entre cosas y lo que hay para las tres (lo mismo que comprueba tools/ew_guide.py). */
	private static final int LINE = 9;
	private static final int GAP = 6;
	private static final int ROOM = 124;

	/** Una sección: su color, su icono (en la cinta) y los de cada línea (tres en cada página). */
	private record Section(String id, int color, Supplier<ItemStack> tab, List<Supplier<ItemStack>> icons) {
	}

	private static final List<Section> SECTIONS = List.of(
			new Section("cities", 0xC9A227, () -> new ItemStack(Items.BELL), List.of(
					() -> new ItemStack(Items.BELL), () -> new ItemStack(Items.COMPASS), GuideScreen::enchantedSword,
					() -> new ItemStack(ModItems.GLORY_COIN), () -> new ItemStack(ModItems.GLORY_POUCH),
					() -> new ItemStack(ModBlocks.CROP_CRATE_ITEM))),
			new Section("people", 0x3F8F3F, () -> new ItemStack(ModItems.RECRUITMENT_CONTRACT), List.of(
					() -> new ItemStack(ModItems.RECRUITMENT_CONTRACT), () -> new ItemStack(Items.BREAD),
					() -> new ItemStack(ModItems.COMMAND_STAFF),
					() -> new ItemStack(ModBlocks.WORK_SITES.get(Profession.FARMER)), () -> new ItemStack(ModBlocks.TOWN_HALL),
					() -> new ItemStack(ModBlocks.WAREHOUSE))),
			new Section("war", 0xA83232, () -> new ItemStack(ModItems.WHITE_FLAG), List.of(
					() -> new ItemStack(Items.IRON_AXE), () -> new ItemStack(ModItems.WHITE_FLAG), () -> new ItemStack(Items.GOAT_HORN),
					() -> new ItemStack(ModItems.weapon(WeaponType.HALBERD, "iron")), () -> new ItemStack(ModItems.MUSKET),
					() -> new ItemStack(ModItems.TREBUCHET))),
			new Section("skills", 0x6A3FB0, () -> new ItemStack(ModBlocks.RESEARCH_DESK), List.of(
					() -> new ItemStack(ModItems.weapon(WeaponType.LONGSWORD, "iron")), () -> new ItemStack(Items.EXPERIENCE_BOTTLE),
					() -> new ItemStack(ModBlocks.RESEARCH_DESK),
					() -> new ItemStack(Items.NETHER_STAR), () -> new ItemStack(Items.CRAFTING_TABLE),
					() -> new ItemStack(ModItems.GUIDE_BOOK))));

	private static int lastSection;
	private int section;
	private int left;
	private int top;
	private PageTurnWidget previous;
	private PageTurnWidget next;

	public GuideScreen() {
		super(Text.translatable("item.evolvedwarfare.guide_book"));
		section = lastSection;
	}

	private static ItemStack enchantedSword() {
		ItemStack stack = new ItemStack(Items.DIAMOND_SWORD);
		stack.set(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
		return stack;
	}

	@Override
	protected void init() {
		left = (width - BOOK_W - TAB_W) / 2;
		top = (height - BOOK_H) / 2;
		previous = addDrawableChild(new PageTurnWidget(left + LEFT_X - 4, top + BOOK_H - 26, false, button -> turn(-1), true));
		next = addDrawableChild(new PageTurnWidget(left + BOOK_W - 44, top + BOOK_H - 26, true, button -> turn(1), true));
		updateButtons();
		MinecraftClient.getInstance().getSoundManager().play(PositionedSoundInstance.master(SoundEvents.ITEM_BOOK_PAGE_TURN, 1.0F));
	}

	private void turn(int delta) {
		open(Math.floorMod(section + delta, SECTIONS.size()), false);
	}

	private void open(int index, boolean sound) {
		if (index == section) {
			return;
		}
		section = index;
		lastSection = index;
		updateButtons();
		if (sound) {
			MinecraftClient.getInstance().getSoundManager().play(PositionedSoundInstance.master(SoundEvents.ITEM_BOOK_PAGE_TURN, 1.0F));
		}
	}

	private void updateButtons() {
		if (previous != null) {
			previous.visible = section > 0;
			next.visible = section < SECTIONS.size() - 1;
		}
	}

	@Override
	public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
		super.renderBackground(context, mouseX, mouseY, delta);
		// las cintas: debajo del libro, asomando por la derecha (la elegida, más y más clara)
		for (int i = 0; i < SECTIONS.size(); i++) {
			Section s = SECTIONS.get(i);
			int tx = left + BOOK_W - 6;
			int ty = top + 18 + i * (TAB_H + 6);
			int out = i == section ? TAB_W + 4 : TAB_W;
			int color = i == section ? s.color() : darker(s.color(), 0.72F);
			context.fill(tx, ty, tx + out, ty + TAB_H, 0xFF000000 | darker(s.color(), 0.45F));
			context.fill(tx, ty + 1, tx + out - 1, ty + TAB_H - 1, 0xFF000000 | color);
			context.fill(tx, ty + 1, tx + out - 1, ty + 3, 0x40FFFFFF);
		}
		context.drawTexture(TEXTURE, left, top, 0, 0, BOOK_W, BOOK_H, BOOK_W, BOOK_H);
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		super.render(context, mouseX, mouseY, delta);
		Section s = SECTIONS.get(section);
		for (int i = 0; i < SECTIONS.size(); i++) {
			int tx = left + BOOK_W - 6;
			int ty = top + 18 + i * (TAB_H + 6);
			context.drawItem(SECTIONS.get(i).tab().get(), tx + (i == section ? 10 : 7), ty + 3);
		}
		String base = "guide.evolvedwarfare." + s.id() + ".";
		// las dos páginas, y abajo a la derecha el número de sección
		page(context, left + LEFT_X, base + "left", s, 0);
		page(context, left + RIGHT_X, base + "right", s, ENTRIES);
		Text number = Text.literal((section + 1) + " / " + SECTIONS.size());
		context.drawText(textRenderer, number, left + RIGHT_X + PAGE_W / 2 - textRenderer.getWidth(number) / 2, top + BOOK_H - 22, 0xFF8A7556,
				false);
		// el nombre de la cinta bajo el ratón
		for (int i = 0; i < SECTIONS.size(); i++) {
			if (overTab(i, mouseX, mouseY)) {
				context.drawTooltip(textRenderer, Text.translatable("guide.evolvedwarfare." + SECTIONS.get(i).id() + ".name"), mouseX, mouseY);
			}
		}
	}

	/** Una página: su título (con el color de la sección) y sus tres líneas con icono. Si no cabe, se encoge un poco. */
	private void page(DrawContext context, int x, String key, Section s, int firstIcon) {
		Text heading = Text.translatable(key + ".title");
		context.drawText(textRenderer, heading, x + PAGE_W / 2 - textRenderer.getWidth(heading) / 2, top + TOP, 0xFF000000 | darker(s.color(), 0.8F), false);
		context.fill(x + 10, top + TOP + 11, x + PAGE_W - 10, top + TOP + 12, 0x40000000 | darker(s.color(), 0.8F));
		float scale = 1.0F;
		List<List<OrderedText>> lines = new java.util.ArrayList<>();
		for (int attempt = 0; attempt < 5; attempt++) {
			int wrap = (int) ((PAGE_W - 22) / scale);
			lines.clear();
			int height = -GAP;
			for (int i = 1; i <= ENTRIES; i++) {
				List<OrderedText> wrapped = textRenderer.wrapLines(Text.translatable(key + "." + i), wrap);
				lines.add(wrapped);
				height += entryHeight(wrapped.size(), scale) + GAP;
			}
			if (height <= ROOM) {
				break;
			}
			scale -= 0.07F;
		}
		int y = top + TOP + 20;
		for (int i = 0; i < ENTRIES; i++) {
			List<OrderedText> wrapped = lines.get(i);
			int h = entryHeight(wrapped.size(), scale);
			context.drawItem(s.icons().get(firstIcon + i).get(), x, y + Math.max(0, (h - 16) / 2 - 2));
			context.getMatrices().push();
			context.getMatrices().translate(x + 20, y + (wrapped.size() == 1 ? 4 : 0), 0.0F);
			context.getMatrices().scale(scale, scale, 1.0F);
			for (int l = 0; l < wrapped.size(); l++) {
				context.drawText(textRenderer, wrapped.get(l), 0, l * LINE, 0xFF3B2A1A, false);
			}
			context.getMatrices().pop();
			y += h + GAP;
		}
	}

	private static int entryHeight(int lines, float scale) {
		return Math.max(16, Math.round(lines * LINE * scale));
	}

	private boolean overTab(int i, double mouseX, double mouseY) {
		int tx = left + BOOK_W - 6;
		int ty = top + 18 + i * (TAB_H + 6);
		return mouseX >= tx + 4 && mouseX < tx + TAB_W + 4 && mouseY >= ty && mouseY < ty + TAB_H;
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		for (int i = 0; i < SECTIONS.size(); i++) {
			if (overTab(i, mouseX, mouseY)) {
				open(i, true);
				return true;
			}
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (keyCode == GLFW.GLFW_KEY_LEFT || keyCode == GLFW.GLFW_KEY_PAGE_UP) {
			if (section > 0) {
				open(section - 1, true);
			}
			return true;
		}
		if (keyCode == GLFW.GLFW_KEY_RIGHT || keyCode == GLFW.GLFW_KEY_PAGE_DOWN) {
			if (section < SECTIONS.size() - 1) {
				open(section + 1, true);
			}
			return true;
		}
		if (keyCode >= GLFW.GLFW_KEY_1 && keyCode < GLFW.GLFW_KEY_1 + SECTIONS.size()) {
			open(keyCode - GLFW.GLFW_KEY_1, true);
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	private static int darker(int rgb, float f) {
		int r = (int) (((rgb >> 16) & 0xFF) * f);
		int g = (int) (((rgb >> 8) & 0xFF) * f);
		int b = (int) ((rgb & 0xFF) * f);
		return (Math.min(255, r) << 16) | (Math.min(255, g) << 8) | Math.min(255, b);
	}
}
