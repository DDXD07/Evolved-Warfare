package com.evolvedwarfare.work;

import com.evolvedwarfare.block.WarehouseBlockEntity;
import com.evolvedwarfare.logistics.Logistics;
import com.evolvedwarfare.logistics.Market;
import com.evolvedwarfare.logistics.StorageNetwork;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

/**
 * Comerciante: atiende la tienda de su puesto (los jugadores compran lo que pongas en los suministros, ver
 * {@code ShopScreenHandler}) y, cada 2 minutos, va al almacén y vende lo que sobre: de cada cosa que el mercado compre
 * deja {@value #RESERVE} y vende el resto, hasta {@value #MAX_LOTS} lotes por viaje. Las monedas van a la producción.
 */
public class MerchantJob implements Job {
	public static final int RANGE = 48;
	public static final int SALE_INTERVAL = 2400;
	public static final int RESERVE = 64;
	private static final int MAX_LOTS = 8;
	/** Más lejos de esto, vuelve al puesto. */
	private static final double STALL_DISTANCE = 4.5;

	private long nextSale = -1;

	@Override
	@Nullable
	public WorkAction plan(WorkContext ctx) {
		long now = ctx.world.getTime();
		if (nextSale < 0) {
			nextSale = now + 200;
		}
		WarehouseBlockEntity warehouse = Logistics.nearestWarehouse(ctx.world, ctx.sitePos, RANGE, ctx.site.getOwner());
		if (now >= nextSale && warehouse != null && !ctx.outputFull()) {
			BlockPos wpos = warehouse.getPos().toImmutable();
			if (!ctx.ignored(wpos) && !lots(warehouse.network()).isEmpty()) {
				return WorkAction.atBlock(wpos, 40, c -> sell(c, wpos)).effect((c, t) -> {
					if (t % 10 == 0) {
						c.world.spawnParticles(ParticleTypes.HAPPY_VILLAGER, wpos.getX() + 0.5, wpos.getY() + 1.2, wpos.getZ() + 0.5,
								1, 0.3, 0.2, 0.3, 0.0);
					}
				});
			}
			// nada que vender: vuelve a mirar dentro de medio minuto
			nextSale = now + SALE_INTERVAL / 4;
		}
		if (ctx.distanceSq(ctx.sitePos) > STALL_DISTANCE * STALL_DISTANCE) {
			// vuelve a su puesto
			return WorkAction.fromSite(ctx.sitePos, Vec3d.ofCenter(ctx.sitePos), 20, c -> false);
		}
		if (warehouse == null) {
			return ctx.idle(WorkStatus.NO_WAREHOUSE);
		}
		return ctx.idle(ctx.outputFull() ? WorkStatus.STORAGE_FULL : WorkStatus.SHOP_OPEN);
	}

	/** Un lote para vender: {@code lots} veces el precio de ese objeto. */
	private record Lot(ItemStack prototype, Market.Price price, int lots) {
	}

	/** Qué sobra en la red (más de {@value #RESERVE}) y el mercado compra; lo más abundante primero. */
	private static List<Lot> lots(StorageNetwork network) {
		List<StorageNetwork.Entry> contents = new ArrayList<>(network.contents());
		contents.sort(Comparator.comparingInt(StorageNetwork.Entry::count).reversed());
		List<Lot> lots = new ArrayList<>();
		int total = 0;
		for (StorageNetwork.Entry entry : contents) {
			Market.Price price = Market.priceOf(entry.prototype());
			if (price == null) {
				continue;
			}
			int n = Math.min((entry.count() - RESERVE) / price.items(), MAX_LOTS - total);
			if (n > 0) {
				lots.add(new Lot(entry.prototype(), price, n));
				total += n;
			}
			if (total >= MAX_LOTS) {
				break;
			}
		}
		return lots;
	}

	private boolean sell(WorkContext ctx, BlockPos wpos) {
		nextSale = ctx.world.getTime() + SALE_INTERVAL;
		if (!(ctx.world.getBlockEntity(wpos) instanceof WarehouseBlockEntity warehouse)) {
			return false;
		}
		StorageNetwork network = warehouse.network();
		int coins = 0;
		for (Lot lot : lots(network)) {
			int want = lot.lots() * lot.price().items();
			int taken = 0;
			while (taken < want) {
				ItemStack part = network.extractLike(lot.prototype(), want - taken);
				if (part.isEmpty()) {
					break;
				}
				taken += part.getCount();
			}
			int sold = taken / lot.price().items();
			int leftover = taken - sold * lot.price().items();
			if (leftover > 0) {
				network.insert(lot.prototype().copyWithCount(leftover));
			}
			coins += sold * lot.price().coins();
		}
		if (coins <= 0) {
			return false;
		}
		ctx.storeAll(Market.coins(coins));
		ctx.sound(wpos, SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6F, 1.2F);
		return true;
	}
}
