package com.evolvedwarfare.work;

import com.evolvedwarfare.block.TownHallBlockEntity;
import com.evolvedwarfare.block.WarehouseBlockEntity;
import com.evolvedwarfare.block.WorkSiteBlockEntity;
import com.evolvedwarfare.building.BuildMaterials;
import com.evolvedwarfare.building.BuildState;
import com.evolvedwarfare.building.Material;
import com.evolvedwarfare.entity.AbstractNpcEntity;
import com.evolvedwarfare.item.RouteSlipItem;
import com.evolvedwarfare.logistics.Logistics;
import com.evolvedwarfare.logistics.RouteData;
import com.evolvedwarfare.logistics.RouteStop;
import com.evolvedwarfare.logistics.StorageNetwork;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * Mensajero: mueve cosas entre el almacén, los puestos y el ayuntamiento (a menos de {@value #RANGE} bloques de su
 * puesto). Lleva hasta 9 montones encima.
 *
 * <p><b>Automático</b> (sin hoja de ruta), por orden:</p>
 * <ol>
 *   <li>Si lleva algo, lo entrega (al puesto que lo pidió o, si no, al almacén).</li>
 *   <li>Lleva del almacén lo que falta: el material que le falta a una obra, lo que gasta cada oficio
 *       ({@link SupplyNeeds}), lo que se está acabando en los suministros y la paga del ayuntamiento.</li>
 *   <li>Recoge la producción de los puestos con {@value #COLLECT_AT} o más objetos (o llenos) y la lleva al almacén.
 *       Deja {@value #KEEP_INPUTS} de lo que el oficio también usa (semillas, brotes).</li>
 * </ol>
 *
 * <p><b>Con hoja de ruta</b> (en sus suministros): recorre las paradas en orden, recogiendo o entregando lo que
 * diga cada una (con sus filtros), y se salta las paradas donde no hay nada que hacer.</p>
 */
public class CourierJob implements Job {
	public static final int RANGE = 48;
	private static final int TRIP = 64;
	private static final int COLLECT_AT = 16;
	private static final int KEEP_INPUTS = 16;
	private static final int HALL_MIN = 32;
	private static final int HALL_TARGET = 64;
	private static final long SERVED_COOLDOWN = 200;
	private static final long FAILED_COOLDOWN = 1200;
	private static final long FULL_COOLDOWN = 600;
	private static final int HANDLE_TICKS = 15;

	/** Qué traer y para quién. */
	private record Request(ItemStack prototype, int amount) {
	}

	/** Un sitio de una parada (o del reparto) del que sacar y en el que meter. */
	private interface Endpoint {
		int count(Predicate<ItemStack> filter);

		ItemStack take(Predicate<ItemStack> filter, int max);

		/** Devuelve lo que no cabe. */
		ItemStack put(ItemStack stack);

		int room(ItemStack stack);
	}

	@Nullable
	private BlockPos deliverTo;
	private int routeIndex;
	private long warehouseFullUntil;
	private final Map<Long, Long> cooldown = new HashMap<>();

	@Override
	@Nullable
	public WorkAction plan(WorkContext ctx) {
		long now = ctx.world.getTime();
		cooldown.values().removeIf(until -> until < now);
		ItemStack slip = ctx.site.find(s -> s.getItem() instanceof RouteSlipItem, false);
		if (slip != null) {
			RouteData route = RouteSlipItem.data(slip);
			if (!route.stops().isEmpty()) {
				return planRoute(ctx, route);
			}
		}
		return planAuto(ctx, now);
	}

	// ------------------------------------------------------------------ automático

	@Nullable
	private WorkAction planAuto(WorkContext ctx, long now) {
		UUID owner = ctx.site.getOwner();
		WarehouseBlockEntity warehouse = Logistics.nearestWarehouse(ctx.world, ctx.sitePos, RANGE, owner);
		if (ctx.worker.hasCargo()) {
			if (deliverTo != null) {
				BlockPos target = deliverTo;
				if (receiver(ctx.world, target) != null && !ctx.ignored(target)) {
					return WorkAction.atBlock(target, HANDLE_TICKS, c -> deliver(c, target));
				}
				deliverTo = null;
			}
			if (warehouse == null) {
				return ctx.idle(WorkStatus.NO_WAREHOUSE);
			}
			if (now < warehouseFullUntil) {
				return ctx.idle(WorkStatus.STORAGE_FULL);
			}
			BlockPos wpos = warehouse.getPos().toImmutable();
			return WorkAction.atBlock(wpos, HANDLE_TICKS, c -> unload(c, wpos));
		}
		deliverTo = null;
		if (warehouse == null) {
			return ctx.idle(WorkStatus.NO_WAREHOUSE);
		}
		BlockPos wpos = warehouse.getPos().toImmutable();
		if (ctx.ignored(wpos)) {
			return ctx.idle(WorkStatus.WAITING);
		}
		StorageNetwork network = warehouse.network();
		List<StorageNetwork.Entry> contents = network.contents();
		List<WorkSiteBlockEntity> sites = Logistics.sites(ctx.world, ctx.sitePos, RANGE, owner);

		// 1. lo que falta en los puestos
		if (!contents.isEmpty()) {
			for (WorkSiteBlockEntity site : sites) {
				if (site.getProfession() == Profession.COURIER || !servable(ctx, site.getPos())) {
					continue;
				}
				Request request = need(site, contents);
				if (request != null) {
					return fetch(wpos, request, site.getPos().toImmutable());
				}
			}
			// la paga del ayuntamiento
			for (TownHallBlockEntity hall : Logistics.townHalls(ctx.world, ctx.sitePos, RANGE, owner)) {
				if (!servable(ctx, hall.getPos())) {
					continue;
				}
				int have = Logistics.countItems(hall, 0, hall.size(), AbstractNpcEntity::isUpkeepItem);
				if (have < HALL_MIN) {
					Request request = request(hall, 0, hall.size(), contents, AbstractNpcEntity::isUpkeepItem, HALL_TARGET - have);
					if (request != null) {
						return fetch(wpos, request, hall.getPos().toImmutable());
					}
				}
			}
		}

		// 2. recoger la producción
		for (WorkSiteBlockEntity site : sites) {
			if (site.getProfession() == Profession.COURIER || !servable(ctx, site.getPos())) {
				continue;
			}
			int ready = collectable(site);
			if (ready >= COLLECT_AT || (ready > 0 && site.isOutputFull())) {
				BlockPos pos = site.getPos().toImmutable();
				return WorkAction.atBlock(pos, HANDLE_TICKS, c -> collect(c, pos));
			}
		}
		return ctx.idle(WorkStatus.WAITING);
	}

	private boolean servable(WorkContext ctx, BlockPos pos) {
		return !ctx.ignored(pos) && !cooldown.containsKey(pos.asLong());
	}

	private void cool(WorkContext ctx, BlockPos pos, long ticks) {
		cooldown.put(pos.asLong(), ctx.world.getTime() + ticks);
	}

	/** Lo primero que le falta a un puesto y hay en el almacén. */
	@Nullable
	private static Request need(WorkSiteBlockEntity site, List<StorageNetwork.Entry> contents) {
		int slots = WorkSiteBlockEntity.SUPPLY_SLOTS;
		// el constructor (o una obra de un plano) avisa del objeto exacto que le falta
		Item missingItem = WorkSiteBlockEntity.missingItemFromNote(site.getNote());
		if (missingItem != null) {
			return request(site, 0, slots, contents, s -> s.isOf(missingItem), TRIP);
		}
		if (site.getBuildState() == BuildState.BUILDING) {
			Material missing = WorkSiteBlockEntity.missingFromNote(site.getNote());
			return missing == null ? null : request(site, 0, slots, contents, s -> BuildMaterials.accepts(missing, s), TRIP);
		}
		for (SupplyNeeds.Need need : SupplyNeeds.of(site.getProfession())) {
			int have = site.count(need.filter(), false);
			if (have * 2 < need.target()) {
				Request request = request(site, 0, slots, contents, need.filter(), need.target() - have);
				if (request != null) {
					return request;
				}
			}
		}
		// lo que el jugador puso en los suministros y se está acabando
		for (int i = 0; i < slots; i++) {
			ItemStack stack = site.getStack(i);
			if (stack.isEmpty() || stack.getMaxCount() < 4 || stack.getItem() instanceof RouteSlipItem) {
				continue;
			}
			ItemStack prototype = stack.copyWithCount(1);
			Predicate<ItemStack> same = s -> ItemStack.areItemsAndComponentsEqual(s, prototype);
			int have = site.count(same, false);
			if (have * 4 < stack.getMaxCount()) {
				Request request = request(site, 0, slots, contents, same, stack.getMaxCount() - have);
				if (request != null) {
					return request;
				}
			}
		}
		return null;
	}

	/** Qué traer del almacén (el primer tipo que cumpla el filtro) y cuánto, según lo que hay y lo que cabe. */
	@Nullable
	private static Request request(Inventory target, int from, int to, List<StorageNetwork.Entry> contents,
			Predicate<ItemStack> filter, int want) {
		for (StorageNetwork.Entry entry : contents) {
			if (!filter.test(entry.prototype())) {
				continue;
			}
			int room = Logistics.roomFor(target, entry.prototype(), from, to);
			int amount = Math.min(Math.min(want, TRIP), Math.min(room, Math.min(entry.count(), entry.prototype().getMaxCount())));
			if (amount > 0) {
				return new Request(entry.prototype().copyWithCount(1), amount);
			}
		}
		return null;
	}

	/** Ir al almacén a por algo para un puesto o el ayuntamiento. */
	private WorkAction fetch(BlockPos wpos, Request request, BlockPos target) {
		return WorkAction.atBlock(wpos, HANDLE_TICKS, c -> {
			if (!(c.world.getBlockEntity(wpos) instanceof WarehouseBlockEntity warehouse)) {
				return false;
			}
			ItemStack taken = warehouse.network().extractLike(request.prototype(), request.amount());
			if (taken.isEmpty()) {
				cool(c, target, FAILED_COOLDOWN);
				return false;
			}
			ItemStack rest = c.worker.getCargo().addStack(taken);
			if (!rest.isEmpty()) {
				warehouse.network().insert(rest);
			}
			deliverTo = target;
			c.sound(wpos, SoundEvents.ENTITY_ITEM_PICKUP, 0.4F, 1.0F);
			return true;
		});
	}

	/** Dónde se entrega en un puesto (los suministros) o en un ayuntamiento. */
	@Nullable
	private static Endpoint receiver(World world, BlockPos pos) {
		BlockEntity be = world.getBlockEntity(pos);
		if (be instanceof WorkSiteBlockEntity site) {
			return inventory(site, 0, WorkSiteBlockEntity.SUPPLY_SLOTS);
		}
		if (be instanceof TownHallBlockEntity hall) {
			return inventory(hall, 0, hall.size());
		}
		return null;
	}

	private boolean deliver(WorkContext ctx, BlockPos target) {
		deliverTo = null;
		Endpoint endpoint = receiver(ctx.world, target);
		if (endpoint == null) {
			return false;
		}
		int moved = dropOff(ctx.worker.getCargo(), endpoint, stack -> true);
		cool(ctx, target, ctx.worker.hasCargo() ? FAILED_COOLDOWN : SERVED_COOLDOWN);
		if (moved > 0) {
			ctx.sound(target, SoundEvents.ENTITY_ITEM_PICKUP, 0.4F, 0.8F);
		}
		return moved > 0;
	}

	private boolean unload(WorkContext ctx, BlockPos wpos) {
		if (!(ctx.world.getBlockEntity(wpos) instanceof WarehouseBlockEntity warehouse)) {
			return false;
		}
		int moved = dropOff(ctx.worker.getCargo(), network(warehouse.network()), stack -> true);
		if (ctx.worker.hasCargo()) {
			// el almacén está lleno: espera un rato antes de volver a probar
			warehouseFullUntil = ctx.world.getTime() + FULL_COOLDOWN;
		}
		if (moved > 0) {
			ctx.sound(wpos, SoundEvents.BLOCK_BARREL_CLOSE, 0.5F, 1.0F);
		}
		return moved > 0;
	}

	/** Cuántos objetos se llevaría de la producción de un puesto. */
	private static int collectable(WorkSiteBlockEntity site) {
		Map<Item, Integer> kept = new HashMap<>();
		int total = 0;
		for (int i = WorkSiteBlockEntity.SUPPLY_SLOTS; i < WorkSiteBlockEntity.SIZE; i++) {
			ItemStack stack = site.getStack(i);
			if (!stack.isEmpty()) {
				total += stack.getCount() - keep(site.getProfession(), stack, kept);
			}
		}
		return total;
	}

	/** Cuánto de ese montón se queda en el puesto (lo que el oficio también usa, hasta {@value #KEEP_INPUTS}). */
	private static int keep(Profession profession, ItemStack stack, Map<Item, Integer> kept) {
		if (!SupplyNeeds.isInput(profession, stack)) {
			return 0;
		}
		int already = kept.getOrDefault(stack.getItem(), 0);
		int keep = Math.min(stack.getCount(), Math.max(0, KEEP_INPUTS - already));
		kept.put(stack.getItem(), already + keep);
		return keep;
	}

	private boolean collect(WorkContext ctx, BlockPos pos) {
		if (!(ctx.world.getBlockEntity(pos) instanceof WorkSiteBlockEntity site)) {
			return false;
		}
		SimpleInventory cargo = ctx.worker.getCargo();
		Map<Item, Integer> kept = new HashMap<>();
		int moved = 0;
		for (int i = WorkSiteBlockEntity.SUPPLY_SLOTS; i < WorkSiteBlockEntity.SIZE; i++) {
			ItemStack stack = site.getStack(i);
			if (stack.isEmpty()) {
				continue;
			}
			int take = stack.getCount() - keep(site.getProfession(), stack, kept);
			if (take <= 0) {
				continue;
			}
			ItemStack rest = cargo.addStack(stack.split(take));
			stack.increment(rest.getCount());
			moved += take - rest.getCount();
			if (!rest.isEmpty()) {
				break;
			}
		}
		if (moved > 0) {
			site.markDirty();
			ctx.sound(pos, SoundEvents.ENTITY_ITEM_PICKUP, 0.4F, 1.2F);
		}
		cool(ctx, pos, SERVED_COOLDOWN);
		return moved > 0;
	}

	// ------------------------------------------------------------------ hoja de ruta

	@Nullable
	private WorkAction planRoute(WorkContext ctx, RouteData route) {
		List<RouteStop> stops = route.stops();
		for (int tries = 0; tries < stops.size(); tries++) {
			int index = Math.floorMod(routeIndex, stops.size());
			RouteStop stop = stops.get(index);
			if (useful(ctx, stop)) {
				return WorkAction.atBlock(stop.pos(), HANDLE_TICKS, c -> {
					routeIndex = index + 1;
					return visit(c, stop);
				});
			}
			routeIndex = index + 1;
		}
		return ctx.idle(WorkStatus.ROUTE_IDLE);
	}

	/** ¿Merece la pena ir a esta parada ahora? */
	private boolean useful(WorkContext ctx, RouteStop stop) {
		if (ctx.ignored(stop.pos()) || !ctx.world.isChunkLoaded(stop.pos())) {
			return false;
		}
		Endpoint endpoint = stopEndpoint(ctx, stop);
		if (endpoint == null) {
			return false;
		}
		SimpleInventory cargo = ctx.worker.getCargo();
		if (stop.pickup()) {
			return hasEmptySlot(cargo) && endpoint.count(stop::accepts) > 0;
		}
		for (int i = 0; i < cargo.size(); i++) {
			ItemStack stack = cargo.getStack(i);
			if (!stack.isEmpty() && stop.accepts(stack) && endpoint.room(stack) > 0) {
				return true;
			}
		}
		return false;
	}

	private boolean visit(WorkContext ctx, RouteStop stop) {
		Endpoint endpoint = stopEndpoint(ctx, stop);
		if (endpoint == null) {
			return false;
		}
		SimpleInventory cargo = ctx.worker.getCargo();
		int moved;
		if (stop.pickup()) {
			moved = 0;
			for (int tries = 0; tries < cargo.size() && hasEmptySlot(cargo); tries++) {
				ItemStack taken = endpoint.take(stop::accepts, TRIP);
				if (taken.isEmpty()) {
					break;
				}
				int count = taken.getCount();
				ItemStack rest = cargo.addStack(taken);
				moved += count - rest.getCount();
				if (!rest.isEmpty()) {
					endpoint.put(rest);
					break;
				}
			}
		} else {
			moved = dropOff(cargo, endpoint, stop::accepts);
		}
		if (moved > 0) {
			ctx.sound(stop.pos(), SoundEvents.ENTITY_ITEM_PICKUP, 0.4F, stop.pickup() ? 1.2F : 0.8F);
		}
		return moved > 0;
	}

	/** El sitio de una parada, o null si ya no está o es de otro jugador. */
	@Nullable
	private static Endpoint stopEndpoint(WorkContext ctx, RouteStop stop) {
		BlockEntity be = ctx.world.getBlockEntity(stop.pos());
		UUID owner = ctx.site.getOwner();
		if (be instanceof WarehouseBlockEntity warehouse) {
			return Logistics.sameOwner(warehouse.getOwner(), owner) ? network(warehouse.network()) : null;
		}
		if (be instanceof WorkSiteBlockEntity site) {
			if (!Logistics.sameOwner(site.getOwner(), owner)) {
				return null;
			}
			// en los puestos se recoge de la producción y se entrega en los suministros
			return stop.pickup() ? inventory(site, WorkSiteBlockEntity.SUPPLY_SLOTS, WorkSiteBlockEntity.SIZE)
					: inventory(site, 0, WorkSiteBlockEntity.SUPPLY_SLOTS);
		}
		if (be instanceof TownHallBlockEntity hall) {
			return Logistics.sameOwner(hall.getOwner(), owner) ? inventory(hall, 0, hall.size()) : null;
		}
		if (be instanceof Inventory inventory) {
			return inventory(inventory, 0, inventory.size());
		}
		return null;
	}

	// ------------------------------------------------------------------ utilidades

	private static boolean hasEmptySlot(SimpleInventory cargo) {
		for (int i = 0; i < cargo.size(); i++) {
			if (cargo.getStack(i).isEmpty()) {
				return true;
			}
		}
		return false;
	}

	/** Deja lo que lleva (lo que cumpla el filtro). Devuelve cuántos objetos dejó. */
	private static int dropOff(SimpleInventory cargo, Endpoint endpoint, Predicate<ItemStack> filter) {
		int moved = 0;
		for (int i = 0; i < cargo.size(); i++) {
			ItemStack stack = cargo.getStack(i);
			if (stack.isEmpty() || !filter.test(stack)) {
				continue;
			}
			ItemStack rest = endpoint.put(stack);
			moved += stack.getCount() - rest.getCount();
			cargo.setStack(i, rest);
		}
		return moved;
	}

	private static Endpoint inventory(Inventory inventory, int from, int to) {
		return new Endpoint() {
			@Override
			public int count(Predicate<ItemStack> filter) {
				return Logistics.countItems(inventory, from, to, filter);
			}

			@Override
			public ItemStack take(Predicate<ItemStack> filter, int max) {
				return Logistics.extractFrom(inventory, from, to, filter, max);
			}

			@Override
			public ItemStack put(ItemStack stack) {
				return Logistics.insertInto(inventory, stack, from, to);
			}

			@Override
			public int room(ItemStack stack) {
				return Logistics.roomFor(inventory, stack, from, to);
			}
		};
	}

	private static Endpoint network(StorageNetwork network) {
		return new Endpoint() {
			@Override
			public int count(Predicate<ItemStack> filter) {
				return network.count(filter);
			}

			@Override
			public ItemStack take(Predicate<ItemStack> filter, int max) {
				return network.extract(filter, max);
			}

			@Override
			public ItemStack put(ItemStack stack) {
				return network.insert(stack);
			}

			@Override
			public int room(ItemStack stack) {
				// la red no lo dice sin probar: se supone que cabe
				return network.containerCount() > 0 ? stack.getCount() : 0;
			}
		};
	}
}
