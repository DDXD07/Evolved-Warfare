package com.evolvedwarfare.backpack;

import com.evolvedwarfare.EvolvedWarfare;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.CraftingInventory;
import net.minecraft.inventory.CraftingResultInventory;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.registry.Registries;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;

/**
 * El botón de ordenar (fase 39) de las mochilas y los cofres: junta los montones iguales y los clasifica (armas,
 * herramientas, armadura, comida, bloques y lo demás; dentro, por objeto y de más a menos). Lo ordenado va delante, así que
 * en una mochila los huecos de carga pesada se vacían si cabe todo en los ligeros.
 */
public final class Sorting {
	/** target: 0 el contenedor de la pantalla (cofre, barril, caja, mochila en el suelo o con B); 1 la mochila del inventario. */
	public record SortPayload(int syncId, int target) implements CustomPayload {
		public static final Id<SortPayload> ID = new Id<>(EvolvedWarfare.id("sort"));
		public static final PacketCodec<RegistryByteBuf, SortPayload> CODEC = PacketCodec.tuple(PacketCodecs.VAR_INT, SortPayload::syncId, PacketCodecs.VAR_INT,
				SortPayload::target, SortPayload::new);

		@Override
		public Id<? extends CustomPayload> getId() {
			return ID;
		}
	}

	private Sorting() {
	}

	public static void register() {
		PayloadTypeRegistry.playC2S().register(SortPayload.ID, SortPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(SortPayload.ID, (payload, context) -> sort(context.player(), payload.syncId(), payload.target()));
	}

	static int category(ItemStack s) {
		return switch (Loadout.role(s)) {
			case WEAPON -> 0;
			case BOTH -> 1;
			case TOOL -> 2;
			default -> s.getItem() instanceof ArmorItem ? 3 : s.contains(DataComponentTypes.FOOD) ? 4 : s.getItem() instanceof BlockItem ? 5 : 6;
		};
	}

	public static final Comparator<ItemStack> ORDER = Comparator.comparingInt(Sorting::category)
			.thenComparingInt(s -> Registries.ITEM.getRawId(s.getItem()))
			.thenComparing(s -> s.getName().getString())
			.thenComparing(Comparator.comparingInt(ItemStack::getCount).reversed());

	/** Junta y ordena unos montones (devuelve la lista nueva, sin vacíos). */
	public static List<ItemStack> sorted(List<ItemStack> stacks) {
		List<ItemStack> merged = new ArrayList<>();
		for (ItemStack s : stacks) {
			if (s.isEmpty()) {
				continue;
			}
			ItemStack left = s.copy();
			for (ItemStack m : merged) {
				if (left.isEmpty()) {
					break;
				}
				if (ItemStack.areItemsAndComponentsEqual(m, left) && m.isStackable() && m.getCount() < m.getMaxCount()) {
					int move = Math.min(left.getCount(), m.getMaxCount() - m.getCount());
					m.increment(move);
					left.decrement(move);
				}
			}
			if (!left.isEmpty()) {
				merged.add(left);
			}
		}
		merged.sort(ORDER);
		return merged;
	}

	/** Ordena unos huecos (todos del mismo inventario): lo que no quepa en su sitio se queda donde se pueda. */
	public static int sortSlots(List<Slot> slots) {
		List<ItemStack> all = new ArrayList<>();
		for (Slot s : slots) {
			all.add(s.getStack().copy());
		}
		List<ItemStack> out = sorted(all);
		for (Slot s : slots) {
			s.setStackNoCallbacks(ItemStack.EMPTY);
		}
		int i = 0;
		List<ItemStack> leftover = new ArrayList<>();
		for (ItemStack stack : out) {
			while (i < slots.size() && !slots.get(i).canInsert(stack)) {
				i++;
			}
			if (i < slots.size()) {
				slots.get(i++).setStackNoCallbacks(stack);
			} else {
				leftover.add(stack);
			}
		}
		// lo que no haya entrado (raro): en el primer hueco libre que lo admita
		for (ItemStack stack : leftover) {
			for (Slot s : slots) {
				if (!s.hasStack() && s.canInsert(stack)) {
					s.setStackNoCallbacks(stack);
					stack = ItemStack.EMPTY;
					break;
				}
			}
			if (!stack.isEmpty()) {
				slots.get(0).inventory.markDirty();
			}
		}
		if (!slots.isEmpty()) {
			slots.get(0).inventory.markDirty();
		}
		return out.size();
	}

	public static void sort(ServerPlayerEntity player, int syncId, int target) {
		ScreenHandler handler = player.currentScreenHandler;
		if (handler.syncId != syncId) {
			return;
		}
		List<Slot> slots = new ArrayList<>();
		if (target == 1 || handler instanceof PlayerScreenHandler) {
			for (Slot s : player.playerScreenHandler.slots) {
				if (s instanceof BackpackSlots.Pack && s.isEnabled()) {
					slots.add(s);
				}
			}
		} else {
			for (Slot s : handler.slots) {
				if (!(s.inventory instanceof PlayerInventory) && !(s.inventory instanceof CraftingInventory) && !(s.inventory instanceof CraftingResultInventory)
						&& s.isEnabled() && (slots.isEmpty() || slots.get(0).inventory == s.inventory)) {
					slots.add(s);
				}
			}
		}
		if (slots.isEmpty()) {
			return;
		}
		sortSlots(slots);
		handler.sendContentUpdates();
		player.playSound(SoundEvents.ITEM_BUNDLE_INSERT, 0.8F, 1.0F);
		player.getWorld().playSound(null, player.getBlockPos(), SoundEvents.ITEM_BUNDLE_INSERT, SoundCategory.PLAYERS, 0.5F, 1.2F);
	}
}
