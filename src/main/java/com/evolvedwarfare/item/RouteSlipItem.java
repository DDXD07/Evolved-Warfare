package com.evolvedwarfare.item;

import com.evolvedwarfare.block.WarehouseBlockEntity;
import com.evolvedwarfare.block.WorkSiteBlockEntity;
import com.evolvedwarfare.logistics.RouteData;
import com.evolvedwarfare.logistics.RouteStop;
import com.evolvedwarfare.registry.ModComponents;
import com.evolvedwarfare.screen.RouteSlipScreenHandler;
import java.util.List;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Hoja de ruta para un mensajero.
 * <ul>
 *   <li>Clic derecho en un cofre, barril, puesto de trabajo, ayuntamiento o almacén: lo añade como parada (otra vez: la
 *       quita). Hasta {@value RouteData#MAX_STOPS} paradas.</li>
 *   <li>Clic derecho al aire: abre la hoja para elegir en cada parada si recoge o entrega y qué objetos.</li>
 *   <li>Ponla en los suministros del puesto del mensajero y hará esa ruta en vez de repartir él solo.</li>
 * </ul>
 */
public class RouteSlipItem extends Item {
	private static final int GRAY = 0xAAAAAA;

	public RouteSlipItem(Settings settings) {
		super(settings);
	}

	public static RouteData data(ItemStack stack) {
		return stack.getOrDefault(ModComponents.ROUTE, RouteData.EMPTY);
	}

	/** ¿Se puede hacer parada aquí? */
	public static boolean isStop(BlockEntity be) {
		return be instanceof Inventory || be instanceof WarehouseBlockEntity;
	}

	/**
	 * Los cofres, puestos y el almacén abren su menú al hacer clic derecho antes de que el objeto se entere; con la hoja
	 * en la mano el clic marca la parada (sin tener que agacharse).
	 */
	public static void registerEvents() {
		UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
			ItemStack stack = player.getStackInHand(hand);
			if (player.isSpectator() || !(stack.getItem() instanceof RouteSlipItem)) {
				return ActionResult.PASS;
			}
			return toggleStop(world, hit.getBlockPos(), player, stack);
		});
	}

	@Override
	public ActionResult useOnBlock(ItemUsageContext context) {
		PlayerEntity player = context.getPlayer();
		return player == null ? ActionResult.PASS : toggleStop(context.getWorld(), context.getBlockPos(), player, context.getStack());
	}

	/** Añade o quita esa parada. */
	private static ActionResult toggleStop(World world, BlockPos pos, PlayerEntity player, ItemStack stack) {
		BlockEntity be = world.getBlockEntity(pos);
		if (be == null || !isStop(be)) {
			return ActionResult.PASS;
		}
		if (!world.isClient) {
			RouteData data = data(stack);
			Text name = world.getBlockState(pos).getBlock().getName();
			int index = data.indexOf(pos);
			if (index >= 0) {
				data = data.without(index);
				player.sendMessage(Text.translatable("message.evolvedwarfare.route.removed", index + 1, name), true);
			} else if (data.stops().size() >= RouteData.MAX_STOPS) {
				player.sendMessage(Text.translatable("message.evolvedwarfare.route.full", RouteData.MAX_STOPS), true);
			} else {
				// en los puestos se recoge la producción; en almacenes, cofres y ayuntamientos se entrega
				boolean pickup = be instanceof WorkSiteBlockEntity;
				data = data.with(new RouteStop(pos.toImmutable(), pickup, List.of()));
				player.sendMessage(Text.translatable(pickup ? "message.evolvedwarfare.route.added_pickup" : "message.evolvedwarfare.route.added_dropoff",
						data.stops().size(), name), true);
			}
			stack.set(ModComponents.ROUTE, data);
		}
		return ActionResult.success(world.isClient);
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
		ItemStack stack = user.getStackInHand(hand);
		if (!world.isClient && user instanceof ServerPlayerEntity serverPlayer) {
			serverPlayer.openHandledScreen(new ExtendedScreenHandlerFactory<Integer>() {
				@Override
				public Integer getScreenOpeningData(ServerPlayerEntity player) {
					return hand.ordinal();
				}

				@Override
				public Text getDisplayName() {
					return stack.getName();
				}

				@Override
				public ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity player) {
					return new RouteSlipScreenHandler(syncId, playerInventory, hand);
				}
			});
		}
		return TypedActionResult.success(stack, world.isClient);
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
		RouteData data = data(stack);
		if (data.stops().isEmpty()) {
			tooltip.add(Text.translatable("item.evolvedwarfare.route_slip.tooltip.empty").withColor(GRAY));
		}
		for (int i = 0; i < data.stops().size(); i++) {
			RouteStop stop = data.stops().get(i);
			BlockPos p = stop.pos();
			tooltip.add(Text.translatable(stop.pickup() ? "item.evolvedwarfare.route_slip.tooltip.pickup" : "item.evolvedwarfare.route_slip.tooltip.dropoff",
					i + 1, p.getX(), p.getY(), p.getZ()).withColor(GRAY));
		}
		tooltip.add(Text.translatable("item.evolvedwarfare.route_slip.tooltip.help").withColor(0x777777));
		super.appendTooltip(stack, context, tooltip, type);
	}
}
