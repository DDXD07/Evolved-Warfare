package com.evolvedwarfare.item;

import com.evolvedwarfare.ClientBridge;
import com.evolvedwarfare.registry.ModItems;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.stat.Stats;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

/**
 * La guía de Evolved Warfare: un librito con cuatro secciones (ciudades y comercio, tu gente, guerra y habilidades) que se
 * abre con clic derecho. Se da una vez a cada jugador la primera vez que entra en el mundo; si se pierde, libro + papel.
 */
public class GuideBookItem extends Item {
	/** Marca (en el jugador) de que ya recibió su guía. */
	public static final String GIVEN_TAG = "evolvedwarfare.guide";

	public GuideBookItem(Settings settings) {
		super(settings);
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
		ItemStack stack = user.getStackInHand(hand);
		if (world.isClient) {
			ClientBridge.openGuide.run();
		}
		user.incrementStat(Stats.USED.getOrCreateStat(this));
		return TypedActionResult.success(stack, world.isClient);
	}

	public static void registerEvents() {
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> giveOnFirstJoin(handler.player));
	}

	/** La primera vez que entra: la guía al inventario (o a sus pies, si no cabe). Devuelve si se la ha dado. */
	public static boolean giveOnFirstJoin(ServerPlayerEntity player) {
		if (player.getCommandTags().contains(GIVEN_TAG) || !player.addCommandTag(GIVEN_TAG)) {
			return false;
		}
		player.getInventory().offerOrDrop(new ItemStack(ModItems.GUIDE_BOOK));
		player.sendMessage(Text.translatable("message.evolvedwarfare.guide.given").formatted(Formatting.GOLD), false);
		player.getWorld().playSound(null, player.getBlockPos(), SoundEvents.ITEM_BOOK_PAGE_TURN, SoundCategory.PLAYERS, 1.0F, 1.0F);
		return true;
	}
}
