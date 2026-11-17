package com.evolvedwarfare.mixin;

import com.evolvedwarfare.difficulty.MendingRarity;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.entity.Entity;
import net.minecraft.item.Items;
import net.minecraft.util.math.random.Random;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.TradeOffers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/** Los bibliotecarios: libros de reparación, uno entre 5000 (fase 100). */
@Mixin(TradeOffers.EnchantBookFactory.class)
public abstract class LibrarianMendingMixin {
	@Unique
	private static final ThreadLocal<Boolean> EVOLVEDWARFARE$BUSY = ThreadLocal.withInitial(() -> false);

	@ModifyReturnValue(method = "create", at = @At("RETURN"))
	private TradeOffer evolvedwarfare$rareMending(TradeOffer offer, Entity entity, Random random) {
		if (offer == null || EVOLVEDWARFARE$BUSY.get() || MendingRarity.chance() <= 0) {
			return offer;
		}
		TradeOffers.Factory self = (TradeOffers.Factory) (Object) this;
		EVOLVEDWARFARE$BUSY.set(true);
		try {
			for (int k = 0; k < 6 && offer != null && MendingRarity.hasMending(offer.getSellItem()); k++) {
				offer = self.create(entity, random);
			}
		} finally {
			EVOLVEDWARFARE$BUSY.set(false);
		}
		if (offer == null) {
			return null;
		}
		if (MendingRarity.hasMending(offer.getSellItem())) {
			MendingRarity.strip(offer.getSellItem());
		}
		if (offer.getSellItem().isOf(Items.ENCHANTED_BOOK) && random.nextInt(MendingRarity.chance()) == 0) {
			var book = MendingRarity.mendingBook(entity.getWorld().getRegistryManager(), 1);
			if (!book.isEmpty()) {
				offer = new TradeOffer(offer.getFirstBuyItem(), offer.getSecondBuyItem(), book, offer.getMaxUses(), offer.getMerchantExperience(),
						offer.getPriceMultiplier());
			}
		}
		return offer;
	}
}
