package com.evolvedwarfare.backpack;

import com.evolvedwarfare.EvolvedWarfare;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.AxeItem;
import net.minecraft.item.BrushItem;
import net.minecraft.item.FishingRodItem;
import net.minecraft.item.FlintAndSteelItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.MaceItem;
import net.minecraft.item.MiningToolItem;
import net.minecraft.item.RangedWeaponItem;
import net.minecraft.item.ShearsItem;
import net.minecraft.item.SwordItem;
import net.minecraft.item.TridentItem;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;

/**
 * La carga (fase 39), al estilo de Helldivers 2: en supervivencia solo se llevan encima tres cosas de combate o trabajo, cada
 * una en su sitio de la barra, y solo desde ahí se usan:
 * <ul>
 * <li>Hueco 1: el arma principal (espadas, hachas, arcos, ballestas, tridentes, mazas, mosquetes, jabalinas...), a la espalda.</li>
 * <li>Huecos 2 y 3: herramientas (picos, palas, azadas, hachas, tijeras, cañas, el martillo...), a la cadera derecha e izquierda.</li>
 * <li>Huecos 4 y 5: lo que quieras, menos armas y herramientas.</li>
 * </ul>
 * Las demás armas y herramientas se pueden guardar en la mochila, pero no usar.
 */
public final class Loadout {
	public enum Role {
		WEAPON, TOOL, BOTH, OTHER
	}

	public static final int PRIMARY = 0, RIGHT_HIP = 1, LEFT_HIP = 2;
	public static final TagKey<Item> WEAPONS = TagKey.of(RegistryKeys.ITEM, EvolvedWarfare.id("loadout/weapons"));
	public static final TagKey<Item> TOOLS = TagKey.of(RegistryKeys.ITEM, EvolvedWarfare.id("loadout/tools"));

	/** Lo que lleva encima a la vista (el arma de la espalda y las dos herramientas de la cadera), para dibujarlo en todos. */
	public static final AttachmentType<List<ItemStack>> SHOWN = AttachmentRegistry.<List<ItemStack>>builder()
			.syncWith(ItemStack.OPTIONAL_LIST_PACKET_CODEC, AttachmentSyncPredicate.all()).initializer(List::of)
			.buildAndRegister(EvolvedWarfare.id("loadout_shown"));

	private Loadout() {
	}

	public static Role role(ItemStack stack) {
		if (stack.isEmpty()) {
			return Role.OTHER;
		}
		Item item = stack.getItem();
		boolean weapon = stack.isIn(WEAPONS) || item instanceof SwordItem || item instanceof RangedWeaponItem || item instanceof TridentItem
				|| item instanceof MaceItem;
		boolean tool = stack.isIn(TOOLS) || item instanceof MiningToolItem || item instanceof ShearsItem || item instanceof FishingRodItem
				|| item instanceof FlintAndSteelItem || item instanceof BrushItem;
		if (item instanceof AxeItem || weapon && tool) {
			return Role.BOTH;
		}
		return weapon ? Role.WEAPON : tool ? Role.TOOL : Role.OTHER;
	}

	/** ¿Puede ir esto en ese hueco de la barra (0 a 4)? */
	public static boolean accepts(int slot, ItemStack stack) {
		if (stack.isEmpty() || slot < 0 || slot >= Backpacks.HOTBAR) {
			return true;
		}
		Role r = role(stack);
		return switch (slot) {
			case PRIMARY -> r == Role.WEAPON || r == Role.BOTH;
			case RIGHT_HIP, LEFT_HIP -> r == Role.TOOL || r == Role.BOTH;
			default -> r == Role.OTHER;
		};
	}

	/** El orden en que se prueba cada hueco al recoger algo. */
	public static int[] order(ItemStack stack) {
		return switch (role(stack)) {
			case WEAPON -> new int[] {PRIMARY};
			case TOOL -> new int[] {RIGHT_HIP, LEFT_HIP};
			case BOTH -> new int[] {RIGHT_HIP, LEFT_HIP, PRIMARY};
			default -> new int[] {3, 4};
		};
	}

	/**
	 * Recoger algo (en supervivencia): primero se junta con lo igual de su sitio (o de la mano izquierda), luego a un hueco
	 * libre de su sitio y, si no, a la mochila. Devuelve si ha entrado algo.
	 */
	public static boolean insert(PlayerEntity player, ItemStack stack) {
		PlayerInventory inv = player.getInventory();
		int before = stack.getCount();
		int[] order = order(stack);
		for (int i : order) {
			merge(inv, i, stack);
		}
		merge(inv, PlayerInventory.OFF_HAND_SLOT, stack);
		for (int i : order) {
			if (!stack.isEmpty() && inv.getStack(i).isEmpty()) {
				ItemStack put = stack.split(Math.min(stack.getCount(), stack.getMaxCount()));
				put.setBobbingAnimationTime(5);
				inv.setStack(i, put);
			}
		}
		if (!stack.isEmpty()) {
			Backpacks.insert(player, stack);
		}
		return stack.getCount() < before;
	}

	private static void merge(PlayerInventory inv, int slot, ItemStack stack) {
		ItemStack s = inv.getStack(slot);
		if (stack.isEmpty() || s.isEmpty() || !ItemStack.areItemsAndComponentsEqual(s, stack) || !s.isStackable() || s.getCount() >= s.getMaxCount()) {
			return;
		}
		int move = Math.min(stack.getCount(), s.getMaxCount() - s.getCount());
		s.increment(move);
		s.setBobbingAnimationTime(5);
		stack.decrement(move);
	}

	/** Lo que esté fuera de su sitio en la barra (por un atajo, un comando, al salir de creativo...) se recoloca. Devuelve cuántos. */
	public static int fix(PlayerEntity player) {
		PlayerInventory inv = player.getInventory();
		int fixed = 0;
		for (int i = 0; i < Backpacks.HOTBAR; i++) {
			ItemStack s = inv.getStack(i);
			if (!s.isEmpty() && !accepts(i, s)) {
				inv.setStack(i, ItemStack.EMPTY);
				insert(player, s);
				if (!s.isEmpty()) {
					player.dropItem(s, false, true);
				}
				fixed++;
			}
		}
		return fixed;
	}

	/** Actualiza lo que se ve encima (si ha cambiado). */
	public static void show(PlayerEntity player) {
		List<ItemStack> now = new ArrayList<>(3);
		boolean limited = Backpacks.limited(player);
		for (int i = 0; i < 3; i++) {
			// en creativo no hay carga: no se lleva nada a la vista
			now.add(limited ? player.getInventory().getStack(i).copyWithCount(1) : ItemStack.EMPTY);
		}
		List<ItemStack> old = player.getAttachedOrElse(SHOWN, List.of());
		boolean same = old.size() == 3;
		for (int i = 0; same && i < 3; i++) {
			same = ItemStack.areEqual(old.get(i), now.get(i));
		}
		if (!same) {
			player.setAttached(SHOWN, now);
		}
	}

	public static List<ItemStack> shown(PlayerEntity player) {
		return player.getAttachedOrElse(SHOWN, List.of());
	}

	/** El icono de hueco vacío de cada sitio (del atlas de bloques, de los de la mesa de herrería). */
	public static Identifier emptyIcon(int slot) {
		return switch (slot) {
			case PRIMARY -> Identifier.ofVanilla("item/empty_slot_sword");
			case RIGHT_HIP -> Identifier.ofVanilla("item/empty_slot_pickaxe");
			case LEFT_HIP -> Identifier.ofVanilla("item/empty_slot_axe");
			default -> null;
		};
	}
}
