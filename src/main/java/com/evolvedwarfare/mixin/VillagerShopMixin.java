package com.evolvedwarfare.mixin;

import com.evolvedwarfare.trade.Deal;
import com.evolvedwarfare.trade.TraderType;
import com.evolvedwarfare.trade.VillageShops;
import com.evolvedwarfare.world.CityLevels;
import java.util.List;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.MerchantEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Los aldeanos sin esmeraldas: al hablar con ellos, los tenderos abren su tienda del mod (en Glory Coins) y los demás
 * dicen que están trabajando (ver {@link VillageShops}). Cada tendero guarda sus precios y lo que le queda hoy.
 */
@Mixin(VillagerEntity.class)
public abstract class VillagerShopMixin extends MerchantEntity implements VillageShops.Keeper {
	@Unique
	private long evolvedwarfare$seed;
	@Unique
	private long evolvedwarfare$day = -1L;
	@Unique
	private int[] evolvedwarfare$stock;
	@Unique
	private int evolvedwarfare$level;
	@Unique
	private TraderType evolvedwarfare$type;

	protected VillagerShopMixin(EntityType<? extends MerchantEntity> type, World world) {
		super(type, world);
	}

	@Inject(method = "interactMob", at = @At("HEAD"), cancellable = true)
	private void evolvedwarfare$shop(PlayerEntity player, Hand hand, CallbackInfoReturnable<ActionResult> cir) {
		VillagerEntity self = (VillagerEntity) (Object) this;
		var stack = player.getStackInHand(hand);
		if (stack.isOf(Items.VILLAGER_SPAWN_EGG) || stack.isOf(Items.NAME_TAG) || !self.isAlive() || self.isBaby() || self.hasCustomer()) {
			return;
		}
		if (self.isSleeping()) {
			if (!self.getWorld().isClient) {
				player.sendMessage(Text.translatable("message.evolvedwarfare.trader.sleeping", self.getDisplayName()), true);
			}
			cir.setReturnValue(ActionResult.success(self.getWorld().isClient));
			return;
		}
		if (player instanceof ServerPlayerEntity sp) {
			VillageShops.interact(self, sp);
		}
		cir.setReturnValue(ActionResult.success(self.getWorld().isClient));
	}

	@Override
	public List<Deal> evolvedwarfare$catalog(ServerWorld world, TraderType type) {
		long day = world.getTimeOfDay() / 24000L;
		if (evolvedwarfare$seed == 0L) {
			evolvedwarfare$seed = getRandom().nextLong() | 1L;
		}
		if (day != evolvedwarfare$day || type != evolvedwarfare$type) {
			// cada mañana (o si cambia de oficio) repone
			evolvedwarfare$day = day;
			evolvedwarfare$type = type;
			evolvedwarfare$stock = null;
		}
		return type.catalog(evolvedwarfare$level(world), evolvedwarfare$seed, day, world.getRegistryManager(), true);
	}

	@Override
	public int evolvedwarfare$level(ServerWorld world) {
		if (evolvedwarfare$level <= 0) {
			evolvedwarfare$level = CityLevels.levelAt(world, getBlockPos());
		}
		return Math.max(1, Math.min(evolvedwarfare$level, CityLevels.maxLevel()));
	}

	@Override
	public int stockLeft(int index, Deal deal) {
		int[] stock = evolvedwarfare$stock;
		if (stock == null || index < 0 || index >= stock.length) {
			return deal.stock();
		}
		return Math.min(stock[index], deal.stock());
	}

	@Override
	public void consumeStock(int index, int lots) {
		if (!(getWorld() instanceof ServerWorld world) || evolvedwarfare$type == null) {
			return;
		}
		List<Deal> deals = evolvedwarfare$catalog(world, evolvedwarfare$type);
		if (index < 0 || index >= deals.size()) {
			return;
		}
		if (evolvedwarfare$stock == null || evolvedwarfare$stock.length != deals.size()) {
			evolvedwarfare$stock = new int[deals.size()];
			for (int i = 0; i < deals.size(); i++) {
				evolvedwarfare$stock[i] = deals.get(i).stock();
			}
		}
		evolvedwarfare$stock[index] = Math.max(0, evolvedwarfare$stock[index] - lots);
	}

	@Override
	public void onTrade(PlayerEntity player) {
		playSound(SoundEvents.ENTITY_VILLAGER_YES, 0.8F, getSoundPitch());
	}

	@Override
	public MerchantEntity merchant() {
		return this;
	}

	@Inject(method = "writeCustomDataToNbt", at = @At("TAIL"))
	private void evolvedwarfare$writeShop(NbtCompound nbt, CallbackInfo ci) {
		NbtCompound shop = new NbtCompound();
		shop.putLong("Seed", evolvedwarfare$seed);
		shop.putLong("Day", evolvedwarfare$day);
		shop.putInt("Level", evolvedwarfare$level);
		if (evolvedwarfare$type != null) {
			shop.putString("Type", evolvedwarfare$type.id());
		}
		if (evolvedwarfare$stock != null) {
			shop.putIntArray("Stock", evolvedwarfare$stock);
		}
		nbt.put("EvolvedWarfareShop", shop);
	}

	@Inject(method = "readCustomDataFromNbt", at = @At("TAIL"))
	private void evolvedwarfare$readShop(NbtCompound nbt, CallbackInfo ci) {
		if (!nbt.contains("EvolvedWarfareShop")) {
			return;
		}
		NbtCompound shop = nbt.getCompound("EvolvedWarfareShop");
		evolvedwarfare$seed = shop.getLong("Seed");
		evolvedwarfare$day = shop.getLong("Day");
		evolvedwarfare$level = shop.getInt("Level");
		evolvedwarfare$type = shop.contains("Type") ? TraderType.byId(shop.getString("Type")) : null;
		evolvedwarfare$stock = shop.contains("Stock") ? shop.getIntArray("Stock") : null;
	}
}
