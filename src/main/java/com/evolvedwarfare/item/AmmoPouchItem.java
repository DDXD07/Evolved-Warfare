package com.evolvedwarfare.item;

import com.evolvedwarfare.EvolvedWarfare;
import com.evolvedwarfare.registry.ModComponents;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.StackReference;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.screen.slot.Slot;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ClickType;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.dynamic.Codecs;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * Bolsa de munición: guarda hasta 1000 balas de un mismo tipo (las de {@code #evolvedwarfare:firearm_ammo}, de momento
 * las de mosquete). Al cargar un arma de fuego, la bala sale de aquí si no llevas una suelta en la otra mano.
 * <ul>
 *   <li>Clic derecho: mete las balas sueltas que lleves (del tipo de la bolsa; si está vacía, del primero que encuentre).</li>
 *   <li>Mayús + clic derecho: saca un montón (64).</li>
 *   <li>En el inventario, clic derecho con balas encima de la bolsa (o con la bolsa encima de las balas): las mete; con
 *   la mano vacía, saca un montón.</li>
 * </ul>
 */
public class AmmoPouchItem extends Item {
	/** Lo que cabe en una bolsa. */
	public static final int CAPACITY = 1000;
	/** Qué munición acepta. */
	public static final TagKey<Item> AMMO = TagKey.of(RegistryKeys.ITEM, EvolvedWarfare.id("firearm_ammo"));
	private static final int BAR_COLOR = 0xD4A640;

	/** Lo que hay dentro: un tipo de munición y cuántas. */
	public record Contents(Item item, int count) {
		public static final Codec<Contents> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Registries.ITEM.getCodec().fieldOf("item").forGetter(Contents::item),
				Codecs.POSITIVE_INT.fieldOf("count").forGetter(Contents::count)).apply(instance, Contents::new));
		public static final PacketCodec<RegistryByteBuf, Contents> PACKET_CODEC = PacketCodec.tuple(
				PacketCodecs.registryValue(RegistryKeys.ITEM), Contents::item,
				PacketCodecs.VAR_INT, Contents::count,
				Contents::new);
	}

	public AmmoPouchItem(Settings settings) {
		super(settings);
	}

	public static boolean isPouch(ItemStack stack) {
		return !stack.isEmpty() && stack.getItem() instanceof AmmoPouchItem;
	}

	@Nullable
	public static Contents contents(ItemStack pouch) {
		Contents contents = pouch.get(ModComponents.AMMO_POUCH);
		return contents == null || contents.count() <= 0 ? null : contents;
	}

	public static int count(ItemStack pouch) {
		Contents contents = contents(pouch);
		return contents == null ? 0 : contents.count();
	}

	private static void set(ItemStack pouch, @Nullable Item item, int count) {
		if (item == null || count <= 0) {
			pouch.remove(ModComponents.AMMO_POUCH);
		} else {
			pouch.set(ModComponents.AMMO_POUCH, new Contents(item, Math.min(CAPACITY, count)));
		}
	}

	/** Si esto se puede meter en esta bolsa (munición normal, del mismo tipo, y que quepa algo). */
	public static boolean accepts(ItemStack pouch, ItemStack ammo) {
		if (ammo.isEmpty() || !ammo.isIn(AMMO) || !ammo.getComponentChanges().isEmpty()) {
			return false;
		}
		Contents contents = contents(pouch);
		return contents == null || (contents.item() == ammo.getItem() && contents.count() < CAPACITY);
	}

	/** Mete todo lo que quepa de {@code ammo} (lo descuenta del montón); devuelve cuántas ha metido. */
	public static int insert(ItemStack pouch, ItemStack ammo) {
		if (!accepts(pouch, ammo)) {
			return 0;
		}
		int in = Math.min(ammo.getCount(), CAPACITY - count(pouch));
		set(pouch, ammo.getItem(), count(pouch) + in);
		ammo.decrement(in);
		return in;
	}

	/** Saca hasta {@code max} balas como un montón (vacío si no hay). */
	public static ItemStack extract(ItemStack pouch, int max) {
		Contents contents = contents(pouch);
		if (contents == null) {
			return ItemStack.EMPTY;
		}
		int out = Math.min(Math.min(max, contents.item().getMaxCount()), contents.count());
		set(pouch, contents.item(), contents.count() - out);
		return new ItemStack(contents.item(), out);
	}

	/** Cuántas balas de este tipo hay en las bolsas que lleva el jugador. */
	public static int countIn(PlayerInventory inventory, Item ammo) {
		int total = 0;
		for (int i = 0; i < inventory.size(); i++) {
			ItemStack stack = inventory.getStack(i);
			Contents contents = isPouch(stack) ? contents(stack) : null;
			if (contents != null && contents.item() == ammo) {
				total += contents.count();
			}
		}
		return total;
	}

	/** Gasta una bala de este tipo de alguna bolsa del jugador (primero la de la otra mano). */
	public static boolean takeOne(PlayerInventory inventory, Item ammo) {
		ItemStack offhand = inventory.offHand.get(0);
		if (takeOne(offhand, ammo)) {
			return true;
		}
		for (int i = 0; i < inventory.size(); i++) {
			if (takeOne(inventory.getStack(i), ammo)) {
				inventory.markDirty();
				return true;
			}
		}
		return false;
	}

	private static boolean takeOne(ItemStack pouch, Item ammo) {
		Contents contents = isPouch(pouch) ? contents(pouch) : null;
		if (contents == null || contents.item() != ammo) {
			return false;
		}
		set(pouch, ammo, contents.count() - 1);
		return true;
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
		ItemStack pouch = user.getStackInHand(hand);
		if (!world.isClient) {
			PlayerInventory inventory = user.getInventory();
			if (user.isSneaking()) {
				ItemStack out = extract(pouch, 64);
				if (!out.isEmpty()) {
					inventory.offerOrDrop(out);
					world.playSound(null, user.getX(), user.getY(), user.getZ(), SoundEvents.ITEM_BUNDLE_REMOVE_ONE, SoundCategory.PLAYERS,
							0.8F, 0.8F + world.getRandom().nextFloat() * 0.4F);
				}
			} else {
				int moved = 0;
				for (int i = 0; i < inventory.size(); i++) {
					ItemStack stack = inventory.getStack(i);
					if (stack != pouch) {
						moved += insert(pouch, stack);
					}
				}
				inventory.markDirty();
				if (moved > 0) {
					world.playSound(null, user.getX(), user.getY(), user.getZ(), SoundEvents.ITEM_BUNDLE_INSERT, SoundCategory.PLAYERS,
							0.8F, 0.8F + world.getRandom().nextFloat() * 0.4F);
				}
			}
			user.sendMessage(status(pouch).formatted(Formatting.GOLD), true);
		}
		return TypedActionResult.success(pouch, world.isClient());
	}

	/** "Bolsa de munición: 350/1000 Bala de mosquete" (o vacía). */
	private static net.minecraft.text.MutableText status(ItemStack pouch) {
		Contents contents = contents(pouch);
		return contents == null
				? Text.translatable("message.evolvedwarfare.ammo_pouch.empty")
				: Text.translatable("message.evolvedwarfare.ammo_pouch.status", contents.count(), CAPACITY, contents.item().getName());
	}

	/** Con la bolsa en el cursor, clic derecho en un hueco: mete las balas que haya o, si está vacío, saca un montón. */
	@Override
	public boolean onStackClicked(ItemStack pouch, Slot slot, ClickType clickType, PlayerEntity player) {
		if (clickType != ClickType.RIGHT) {
			return false;
		}
		ItemStack target = slot.getStack();
		if (accepts(pouch, target)) {
			int room = CAPACITY - count(pouch);
			ItemStack taken = slot.takeStackRange(target.getCount(), room, player);
			insert(pouch, taken);
			if (!taken.isEmpty()) {
				slot.insertStack(taken);
			}
			player.playSound(SoundEvents.ITEM_BUNDLE_INSERT, 0.8F, 0.8F + player.getWorld().getRandom().nextFloat() * 0.4F);
			return true;
		}
		if (target.isEmpty() && count(pouch) > 0) {
			ItemStack out = extract(pouch, 64);
			ItemStack rest = slot.insertStack(out);
			if (!rest.isEmpty()) {
				insert(pouch, rest);
			}
			player.playSound(SoundEvents.ITEM_BUNDLE_REMOVE_ONE, 0.8F, 0.8F + player.getWorld().getRandom().nextFloat() * 0.4F);
			return true;
		}
		return false;
	}

	/** Con balas en el cursor, clic derecho en la bolsa: las mete. Con el cursor vacío: saca un montón. */
	@Override
	public boolean onClicked(ItemStack pouch, ItemStack otherStack, Slot slot, ClickType clickType, PlayerEntity player,
			StackReference cursorStackReference) {
		if (clickType != ClickType.RIGHT || !slot.canTakePartial(player)) {
			return false;
		}
		if (accepts(pouch, otherStack)) {
			insert(pouch, otherStack);
			player.playSound(SoundEvents.ITEM_BUNDLE_INSERT, 0.8F, 0.8F + player.getWorld().getRandom().nextFloat() * 0.4F);
			return true;
		}
		if (otherStack.isEmpty() && count(pouch) > 0) {
			cursorStackReference.set(extract(pouch, 64));
			player.playSound(SoundEvents.ITEM_BUNDLE_REMOVE_ONE, 0.8F, 0.8F + player.getWorld().getRandom().nextFloat() * 0.4F);
			return true;
		}
		return false;
	}

	@Override
	public boolean isItemBarVisible(ItemStack stack) {
		return count(stack) > 0;
	}

	@Override
	public int getItemBarStep(ItemStack stack) {
		return Math.max(1, Math.round(13.0F * count(stack) / CAPACITY));
	}

	@Override
	public int getItemBarColor(ItemStack stack) {
		return BAR_COLOR;
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
		Contents contents = contents(stack);
		if (contents == null) {
			tooltip.add(Text.translatable("item.evolvedwarfare.ammo_pouch.empty").formatted(Formatting.GRAY));
		} else {
			tooltip.add(Text.translatable("item.evolvedwarfare.ammo_pouch.contents", contents.item().getName(), contents.count(), CAPACITY)
					.formatted(Formatting.GOLD));
		}
		tooltip.add(Text.translatable("item.evolvedwarfare.ammo_pouch.hint_reload").formatted(Formatting.DARK_GREEN));
		tooltip.add(Text.translatable("item.evolvedwarfare.ammo_pouch.hint_use").formatted(Formatting.GRAY));
		super.appendTooltip(stack, context, tooltip, type);
	}
}
