package com.evolvedwarfare.train;

import com.evolvedwarfare.EvolvedWarfare;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.block.AbstractRailBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.enums.RailShape;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.vehicle.AbstractMinecartEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Rarity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * El tren de vapor (fase 51): del tamaño de una vagoneta y sobre las vías de siempre (curvas, cuestas, desvíos y raíles
 * propulsores incluidos). La locomotora quema carbón (o carbón vegetal, o bloques de carbón) que se le mete en su
 * caldera; detrás, los vagones: de pasajeros (dos asientos), de mercancías (54 huecos) y el ténder (27 huecos de
 * combustible, del que tira la locomotora cuando se le acaba el suyo).
 */
public final class Trains {
	public static final EntityType<LocomotiveCart> LOCOMOTIVE = type("steam_locomotive", LocomotiveCart::new);
	public static final EntityType<PassengerCart> PASSENGER = type("passenger_car", PassengerCart::new);
	public static final EntityType<FreightCart> FREIGHT = type("freight_car", FreightCart::new);
	public static final EntityType<TenderCart> TENDER = type("coal_tender", TenderCart::new);

	public static final ScreenHandlerType<LocomotiveScreenHandler> LOCOMOTIVE_SCREEN = Registry.register(Registries.SCREEN_HANDLER,
			EvolvedWarfare.id("steam_locomotive"), new ScreenHandlerType<>(LocomotiveScreenHandler::new, net.minecraft.resource.featuretoggle.FeatureFlags.VANILLA_FEATURES));

	public static final List<Item> ITEMS = new ArrayList<>();
	public static final Item LOCOMOTIVE_ITEM = item("steam_locomotive", LOCOMOTIVE, new Item.Settings().maxCount(1).rarity(Rarity.UNCOMMON));
	public static final Item PASSENGER_ITEM = item("passenger_car", PASSENGER, new Item.Settings().maxCount(1));
	public static final Item FREIGHT_ITEM = item("freight_car", FREIGHT, new Item.Settings().maxCount(1));
	public static final Item TENDER_ITEM = item("coal_tender", TENDER, new Item.Settings().maxCount(1));

	private Trains() {
	}

	private static <T extends AbstractMinecartEntity> EntityType<T> type(String name, EntityType.EntityFactory<T> factory) {
		return Registry.register(Registries.ENTITY_TYPE, EvolvedWarfare.id(name),
				EntityType.Builder.create(factory, SpawnGroup.MISC).dimensions(0.98F, 1.1F).passengerAttachments(0.1875F).maxTrackingRange(8)
						.build(name));
	}

	private static Item item(String name, EntityType<? extends AbstractMinecartEntity> type, Item.Settings settings) {
		Item item = Registry.register(Registries.ITEM, EvolvedWarfare.id(name), new CarItem(type, settings));
		ITEMS.add(item);
		return item;
	}

	public static void register() {
	}

	/** El objeto: se pone en una vía; si hay un tren justo al lado, se engancha detrás de su último coche. */
	public static class CarItem extends Item {
		private final EntityType<? extends AbstractMinecartEntity> type;

		public CarItem(EntityType<? extends AbstractMinecartEntity> type, Settings settings) {
			super(settings);
			this.type = type;
		}

		@Override
		public ActionResult useOnBlock(ItemUsageContext context) {
			World world = context.getWorld();
			BlockPos pos = context.getBlockPos();
			BlockState state = world.getBlockState(pos);
			if (!state.isIn(BlockTags.RAILS)) {
				return ActionResult.FAIL;
			}
			if (world instanceof ServerWorld sw) {
				RailShape shape = state.getBlock() instanceof AbstractRailBlock rail ? state.get(rail.getShapeProperty()) : RailShape.NORTH_SOUTH;
				double y = pos.getY() + 0.0625 + (shape.isAscending() ? 0.5 : 0.0);
				AbstractMinecartEntity cart = type.create(sw);
				if (cart == null) {
					return ActionResult.FAIL;
				}
				PlayerEntity player = context.getPlayer();
				Direction facing = player != null ? player.getHorizontalFacing() : Direction.NORTH;
				cart.refreshPositionAndAngles(pos.getX() + 0.5, y, pos.getZ() + 0.5, facing.asRotation(), 0.0F);
				if (cart instanceof LocomotiveCart loco) {
					loco.setHeading(new Vec3d(facing.getOffsetX(), 0, facing.getOffsetZ()));
				}
				sw.spawnEntity(cart);
				sw.emitGameEvent(net.minecraft.world.event.GameEvent.ENTITY_PLACE, pos, net.minecraft.world.event.GameEvent.Emitter.of(player, sw.getBlockState(pos.down())));
				if (cart instanceof TrainCar car) {
					TrainCar tail = tail(sw, car);
					if (tail != null) {
						TrainCar.couple(tail, car);
						sw.playSound(null, pos, SoundEvents.BLOCK_CHAIN_PLACE, SoundCategory.BLOCKS, 1.0F, 0.8F);
						if (player != null) {
							player.sendMessage(Text.translatable("message.evolvedwarfare.train.coupled").formatted(Formatting.GRAY), true);
						}
					}
				}
				context.getStack().decrementUnlessCreative(1, player);
			}
			return ActionResult.success(world.isClient);
		}

		@Override
		public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, net.minecraft.item.tooltip.TooltipType type) {
			tooltip.add(Text.translatable(getTranslationKey() + ".desc").formatted(Formatting.GRAY));
		}
	}

	/** El último coche de un tren que esté justo al lado (sin nadie enganchado detrás), o null. */
	static TrainCar tail(ServerWorld world, TrainCar placed) {
		AbstractMinecartEntity me = placed.cart();
		TrainCar best = null;
		double bestD = 3.2 * 3.2;
		for (AbstractMinecartEntity other : world.getEntitiesByClass(AbstractMinecartEntity.class, me.getBoundingBox().expand(3.5), e -> e != me)) {
			if (other instanceof TrainCar car && car.link().back == null) {
				double d = other.squaredDistanceTo(me);
				if (d < bestD) {
					bestD = d;
					best = car;
				}
			}
		}
		return best;
	}
}
