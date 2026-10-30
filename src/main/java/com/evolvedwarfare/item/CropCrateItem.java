package com.evolvedwarfare.item;

import com.evolvedwarfare.EvolvedWarfare;
import com.evolvedwarfare.registry.ModComponents;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.block.Block;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.dynamic.Codecs;
import org.jetbrains.annotations.Nullable;

/**
 * La Caja de cultivos como objeto. Vacía, o llena de un cultivo (hasta {@value #CAPACITY}): lo que lleva va en el
 * componente {@code evolvedwarfare:crop_crate}, así al ponerla vuelve a salir con lo suyo. Las cajas iguales (mismo
 * cultivo y misma cantidad) se apilan. Los comerciantes de las ciudades solo compran los cultivos así, en cajas llenas.
 *
 * <p>Se lleva con las dos manos: no cabe en la mano izquierda (pasa al inventario) y, mientras la llevas, lo de la otra
 * mano no se puede usar (ni se ve). Para dejarla, clic derecho en el suelo.</p>
 */
public class CropCrateItem extends BlockItem {
	/** Cuánto cabe: un montón. */
	public static final int CAPACITY = 64;
	/** Lo que se puede meter en una caja (datos: se pueden añadir más cultivos con un paquete de datos). */
	public static final TagKey<Item> CROPS = TagKey.of(RegistryKeys.ITEM, EvolvedWarfare.id("crate_crops"));

	/** Lo que lleva una caja: qué cultivo y cuántos. */
	public record Contents(Item item, int count) {
		public static final Codec<Contents> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Registries.ITEM.getCodec().fieldOf("item").forGetter(Contents::item),
				Codecs.POSITIVE_INT.fieldOf("count").forGetter(Contents::count)).apply(instance, Contents::new));
		public static final PacketCodec<RegistryByteBuf, Contents> PACKET_CODEC = PacketCodec.tuple(
				PacketCodecs.registryValue(RegistryKeys.ITEM), Contents::item,
				PacketCodecs.VAR_INT, Contents::count,
				Contents::new);

		public boolean full() {
			return count >= capacity(item);
		}

		public ItemStack stack() {
			return new ItemStack(item, count);
		}
	}

	public CropCrateItem(Block block, Settings settings) {
		super(block, settings);
	}

	public static boolean isCrate(ItemStack stack) {
		return !stack.isEmpty() && stack.getItem() instanceof CropCrateItem;
	}

	/** Las dos manos: con una caja, la otra mano no hace nada; y en la izquierda no se puede llevar. */
	public static void registerEvents() {
		UseItemCallback.EVENT.register((player, world, hand) -> hand == Hand.OFF_HAND && isCrate(player.getMainHandStack())
				? TypedActionResult.fail(player.getStackInHand(hand)) : TypedActionResult.pass(player.getStackInHand(hand)));
		UseBlockCallback.EVENT.register((player, world, hand, hit) -> hand == Hand.OFF_HAND && isCrate(player.getMainHandStack())
				? ActionResult.FAIL : ActionResult.PASS);
		UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> hand == Hand.OFF_HAND && isCrate(player.getMainHandStack())
				? ActionResult.FAIL : ActionResult.PASS);
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
				ItemStack off = player.getOffHandStack();
				if (isCrate(off)) {
					player.setStackInHand(Hand.OFF_HAND, ItemStack.EMPTY);
					player.getInventory().offerOrDrop(off);
					player.sendMessage(Text.translatable("message.evolvedwarfare.crop_crate.two_hands"), true);
				}
			}
		});
	}

	/** Si se puede guardar en una caja (los cultivos de la etiqueta, sin nombre ni nada raro). */
	public static boolean accepts(ItemStack stack) {
		return !stack.isEmpty() && stack.isIn(CROPS) && stack.getComponentChanges().isEmpty();
	}

	/** Si es un cultivo de los de caja (los comerciantes ya no los compran sueltos). */
	public static boolean isCrop(Item item) {
		return new ItemStack(item).isIn(CROPS);
	}

	/** Cuántos de este cultivo caben (64, o menos si no se apila tanto). */
	public static int capacity(Item item) {
		return Math.max(1, Math.min(CAPACITY, item.getMaxCount()));
	}

	@Nullable
	public static Contents contents(ItemStack crate) {
		Contents contents = crate.get(ModComponents.CROP_CRATE);
		return contents == null || contents.count() <= 0 ? null : contents;
	}

	/** Una caja llena de ese cultivo (lo que compran los comerciantes). */
	public static ItemStack filled(Item crate, Item crop) {
		ItemStack stack = new ItemStack(crate);
		stack.set(ModComponents.CROP_CRATE, new Contents(crop, capacity(crop)));
		return stack;
	}

	/** «Caja de trigo» si lleva algo; si no, «Caja de cultivos». */
	@Override
	public Text getName(ItemStack stack) {
		Contents contents = contents(stack);
		if (contents == null) {
			return super.getName(stack);
		}
		return Text.translatable("item.evolvedwarfare.crop_crate.filled", contents.item().getName());
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
		Contents contents = contents(stack);
		if (contents == null) {
			tooltip.add(Text.translatable("tooltip.evolvedwarfare.crop_crate.empty").formatted(Formatting.GRAY));
		} else {
			int capacity = capacity(contents.item());
			tooltip.add(Text.translatable("tooltip.evolvedwarfare.crop_crate.count", contents.count(), capacity, contents.item().getName())
					.formatted(Formatting.YELLOW));
			tooltip.add(contents.full()
					? Text.translatable("tooltip.evolvedwarfare.crop_crate.full").formatted(Formatting.GREEN)
					: Text.translatable("tooltip.evolvedwarfare.crop_crate.not_full", capacity - contents.count()).formatted(Formatting.GOLD));
		}
		tooltip.add(Text.translatable("tooltip.evolvedwarfare.crop_crate.pickup").formatted(Formatting.DARK_GRAY));
	}
}
