package com.evolvedwarfare.item;

import com.evolvedwarfare.logistics.Market;
import com.evolvedwarfare.logistics.Money;
import com.evolvedwarfare.registry.ModComponents;
import java.util.List;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.StackReference;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.screen.slot.Slot;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ClickType;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

/**
 * Bolsa de Gloria: guarda tantas Glory Coins como quieras en un solo hueco (un montón de monedas solo llega a 64).
 * Las tiendas cobran de la bolsa y pagan en ella; si no llevas ninguna y te pagan mucho, te dan una nueva.
 * <ul>
 *   <li>Clic derecho: mete todas las monedas sueltas que lleves.</li>
 *   <li>Mayús + clic derecho: saca 64.</li>
 *   <li>En el inventario, clic derecho con monedas encima de la bolsa (o con la bolsa encima de las monedas): las mete;
 *   con la mano vacía, saca un montón.</li>
 * </ul>
 */
public class GloryPouchItem extends Item {
	public GloryPouchItem(Settings settings) {
		super(settings);
	}

	public static boolean isPouch(ItemStack stack) {
		return !stack.isEmpty() && stack.getItem() instanceof GloryPouchItem;
	}

	public static int balance(ItemStack pouch) {
		return Math.max(0, pouch.getOrDefault(ModComponents.GLORY_BALANCE, 0));
	}

	public static void setBalance(ItemStack pouch, long amount) {
		int value = (int) Math.max(0L, Math.min(Integer.MAX_VALUE, amount));
		if (value == 0) {
			pouch.remove(ModComponents.GLORY_BALANCE);
		} else {
			pouch.set(ModComponents.GLORY_BALANCE, value);
		}
	}

	/** Mete monedas; devuelve las que no caben (solo si la bolsa llega al máximo, más de dos mil millones). */
	public static long deposit(ItemStack pouch, long amount) {
		long room = Integer.MAX_VALUE - (long) balance(pouch);
		long in = Math.max(0L, Math.min(room, amount));
		setBalance(pouch, balance(pouch) + in);
		return amount - in;
	}

	/** Saca hasta {@code amount} monedas; devuelve las que ha sacado. */
	public static int withdraw(ItemStack pouch, int amount) {
		int out = Math.max(0, Math.min(amount, balance(pouch)));
		setBalance(pouch, balance(pouch) - out);
		return out;
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
		ItemStack pouch = user.getStackInHand(hand);
		if (!world.isClient) {
			PlayerInventory inventory = user.getInventory();
			if (user.isSneaking()) {
				int out = withdraw(pouch, Market.currency().getMaxCount());
				if (out > 0) {
					inventory.offerOrDrop(new ItemStack(Market.currency(), out));
					world.playSound(null, user.getX(), user.getY(), user.getZ(), SoundEvents.ITEM_BUNDLE_REMOVE_ONE, SoundCategory.PLAYERS,
							0.8F, 0.8F + world.getRandom().nextFloat() * 0.4F);
				}
			} else {
				long moved = 0;
				for (int i = 0; i < inventory.size(); i++) {
					ItemStack stack = inventory.getStack(i);
					if (Market.isPlainCoin(stack)) {
						int count = stack.getCount();
						long rest = deposit(pouch, count);
						moved += count - rest;
						stack.setCount((int) rest);
					}
				}
				inventory.markDirty();
				if (moved > 0) {
					world.playSound(null, user.getX(), user.getY(), user.getZ(), SoundEvents.ITEM_BUNDLE_INSERT, SoundCategory.PLAYERS,
							0.8F, 0.8F + world.getRandom().nextFloat() * 0.4F);
				}
			}
			user.sendMessage(Text.translatable("message.evolvedwarfare.pouch.balance", balance(pouch), Market.currency().getName())
					.formatted(Formatting.GOLD), true);
		}
		return TypedActionResult.success(pouch, world.isClient());
	}

	/** Con la bolsa en el cursor, clic derecho en un hueco: mete las monedas que haya o, si está vacío, saca un montón. */
	@Override
	public boolean onStackClicked(ItemStack pouch, Slot slot, ClickType clickType, PlayerEntity player) {
		if (clickType != ClickType.RIGHT) {
			return false;
		}
		ItemStack target = slot.getStack();
		if (Market.isPlainCoin(target)) {
			ItemStack taken = slot.takeStackRange(target.getCount(), Integer.MAX_VALUE, player);
			long rest = deposit(pouch, taken.getCount());
			if (rest > 0) {
				taken.setCount((int) rest);
				slot.insertStack(taken);
			}
			player.playSound(SoundEvents.ITEM_BUNDLE_INSERT, 0.8F, 0.8F + player.getWorld().getRandom().nextFloat() * 0.4F);
			return true;
		}
		if (target.isEmpty() && balance(pouch) > 0) {
			int out = Math.min(Market.currency().getMaxCount(), balance(pouch));
			ItemStack rest = slot.insertStack(new ItemStack(Market.currency(), out));
			withdraw(pouch, out - rest.getCount());
			player.playSound(SoundEvents.ITEM_BUNDLE_REMOVE_ONE, 0.8F, 0.8F + player.getWorld().getRandom().nextFloat() * 0.4F);
			return true;
		}
		return false;
	}

	/** Con monedas en el cursor, clic derecho en la bolsa: las mete. Con el cursor vacío: saca un montón. */
	@Override
	public boolean onClicked(ItemStack pouch, ItemStack otherStack, Slot slot, ClickType clickType, PlayerEntity player,
			StackReference cursorStackReference) {
		if (clickType != ClickType.RIGHT || !slot.canTakePartial(player)) {
			return false;
		}
		if (Market.isPlainCoin(otherStack)) {
			long rest = deposit(pouch, otherStack.getCount());
			otherStack.setCount((int) rest);
			player.playSound(SoundEvents.ITEM_BUNDLE_INSERT, 0.8F, 0.8F + player.getWorld().getRandom().nextFloat() * 0.4F);
			return true;
		}
		if (otherStack.isEmpty() && balance(pouch) > 0) {
			int out = withdraw(pouch, Market.currency().getMaxCount());
			cursorStackReference.set(new ItemStack(Market.currency(), out));
			player.playSound(SoundEvents.ITEM_BUNDLE_REMOVE_ONE, 0.8F, 0.8F + player.getWorld().getRandom().nextFloat() * 0.4F);
			return true;
		}
		return false;
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
		tooltip.add(Text.translatable("item.evolvedwarfare.glory_pouch.balance", Money.full(balance(stack))).formatted(Formatting.GOLD));
		tooltip.add(Text.translatable("item.evolvedwarfare.glory_pouch.hint_use").formatted(Formatting.GRAY));
		super.appendTooltip(stack, context, tooltip, type);
	}
}
