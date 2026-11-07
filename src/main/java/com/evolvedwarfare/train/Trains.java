package com.evolvedwarfare.train;

import com.evolvedwarfare.EvolvedWarfare;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.world.ChunkTicketType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Rarity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.World;

/**
 * El tren de vapor (fase 41): la locomotora y las plataformas, sus objetos, enganchar y desenganchar, y lo que hace falta
 * para que se pueda andar (y construir) encima mientras va: las colisiones con lo que lleva y que no te echen por "volar".
 */
public final class Trains {
	public static final ChunkTicketType<ChunkPos> TICKET = ChunkTicketType.create("evolvedwarfare_train", Comparator.comparingLong(ChunkPos::toLong), 80);

	public static final EntityType<LocomotiveEntity> LOCOMOTIVE = Registry.register(Registries.ENTITY_TYPE, EvolvedWarfare.id("locomotive"),
			EntityType.Builder.<LocomotiveEntity>create(LocomotiveEntity::new, SpawnGroup.MISC).dimensions(3.0F, 5.0F).maxTrackingRange(16)
					.trackingTickInterval(1).makeFireImmune().build("locomotive"));
	public static final EntityType<FlatcarEntity> FLATCAR = Registry.register(Registries.ENTITY_TYPE, EvolvedWarfare.id("flatcar"),
			EntityType.Builder.<FlatcarEntity>create(FlatcarEntity::new, SpawnGroup.MISC).dimensions(5.0F, 2.0F).maxTrackingRange(16).trackingTickInterval(1)
					.makeFireImmune().build("flatcar"));

	public static final List<Item> ITEMS = new ArrayList<>();
	public static final Item LOCOMOTIVE_ITEM = item("locomotive", new CarItem(true, new Item.Settings().maxCount(1).rarity(Rarity.RARE)));
	public static final Item FLATCAR_ITEM = item("flatcar", new CarItem(false, new Item.Settings().maxCount(8)));

	private Trains() {
	}

	private static Item item(String name, Item item) {
		ITEMS.add(Registry.register(Registries.ITEM, EvolvedWarfare.id(name), item));
		return item;
	}

	public static void register() {
	}

	/** El objeto: la locomotora se pone en una vía; la plataforma se engancha detrás de un tren (clic en la locomotora o en un vagón). */
	public static class CarItem extends Item {
		private final boolean locomotive;

		public CarItem(boolean locomotive, Settings settings) {
			super(settings);
			this.locomotive = locomotive;
		}

		@Override
		public ActionResult useOnBlock(ItemUsageContext context) {
			World world = context.getWorld();
			if (Track.shape(world, context.getBlockPos()) == null) {
				return ActionResult.PASS;
			}
			if (!locomotive) {
				if (!world.isClient && context.getPlayer() != null) {
					context.getPlayer().sendMessage(Text.translatable("message.evolvedwarfare.train.couple_hint").formatted(Formatting.GRAY), true);
				}
				return ActionResult.FAIL;
			}
			if (world instanceof ServerWorld sw) {
				var facing = context.getPlayer() != null ? context.getPlayer().getHorizontalFacing() : context.getHorizontalPlayerFacing();
				if (spawnLocomotive(sw, context.getBlockPos(), facing) == null) {
					return ActionResult.FAIL;
				}
				context.getStack().decrementUnlessCreative(1, context.getPlayer());
			}
			return ActionResult.success(world.isClient);
		}

		@Override
		public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, net.minecraft.item.tooltip.TooltipType type) {
			tooltip.add(Text.translatable(getTranslationKey() + ".desc").formatted(Formatting.GRAY));
		}
	}

	/** Pone una locomotora en la vía de pos, mirando hacia facing. */
	public static LocomotiveEntity spawnLocomotive(ServerWorld world, net.minecraft.util.math.BlockPos pos, net.minecraft.util.math.Direction facing) {
		Track.Pos start = Track.start(world, pos, facing);
		if (start == null) {
			return null;
		}
		LocomotiveEntity loco = LOCOMOTIVE.create(world);
		if (loco == null) {
			return null;
		}
		loco.setTrack(start);
		world.spawnEntity(loco);
		world.playSound(null, pos, SoundEvents.BLOCK_ANVIL_PLACE, SoundCategory.BLOCKS, 0.6F, 0.6F);
		return loco;
	}

	/** Engancha una plataforma nueva al final del tren de ese coche. */
	public static ActionResult couple(TrainCarEntity car, PlayerEntity player, ItemStack stack) {
		if (!stack.isOf(FLATCAR_ITEM)) {
			return ActionResult.PASS;
		}
		if (!(car.getWorld() instanceof ServerWorld world)) {
			return ActionResult.SUCCESS;
		}
		LocomotiveEntity loco = car instanceof LocomotiveEntity l ? l
				: car instanceof FlatcarEntity f && f.locomotive() != null && world.getEntity(f.locomotive()) instanceof LocomotiveEntity l2 ? l2 : null;
		if (loco == null) {
			return ActionResult.FAIL;
		}
		FlatcarEntity wagon = addWagon(loco);
		if (wagon == null) {
			player.sendMessage(Text.translatable("message.evolvedwarfare.train.no_room").formatted(Formatting.RED), true);
			return ActionResult.FAIL;
		}
		stack.decrementUnlessCreative(1, player);
		return ActionResult.CONSUME;
	}

	/** Añade una plataforma (si cabe en la vía por detrás y no pasa del máximo). */
	public static FlatcarEntity addWagon(LocomotiveEntity loco) {
		if (!(loco.getWorld() instanceof ServerWorld world) || loco.wagons().size() >= LocomotiveEntity.MAX_WAGONS || Math.abs(loco.speed()) > 1e-3) {
			return null;
		}
		FlatcarEntity wagon = FLATCAR.create(world);
		if (wagon == null) {
			return null;
		}
		wagon.setPosition(loco.getPos());
		world.spawnEntity(wagon);
		loco.addWagon(wagon);
		if (!loco.place()) {
			loco.wagons().remove(wagon.getUuid());
			wagon.discard();
			loco.place();
			return null;
		}
		world.playSound(null, wagon.getBlockPos(), SoundEvents.BLOCK_CHAIN_PLACE, SoundCategory.BLOCKS, 1.0F, 0.7F);
		return wagon;
	}

	/** Agachado con la mano vacía: se desengancha la última plataforma (si está vacía) o, sin plataformas, se recoge la locomotora. */
	public static ActionResult uncouple(TrainCarEntity car, PlayerEntity player) {
		if (!(car.getWorld() instanceof ServerWorld world)) {
			return ActionResult.SUCCESS;
		}
		LocomotiveEntity loco = car instanceof LocomotiveEntity l ? l
				: car instanceof FlatcarEntity f && f.locomotive() != null && world.getEntity(f.locomotive()) instanceof LocomotiveEntity l2 ? l2 : null;
		if (loco == null) {
			if (car instanceof FlatcarEntity f && f.blockCount() == 0) {
				f.discard();
				give(player, new ItemStack(FLATCAR_ITEM));
				return ActionResult.CONSUME;
			}
			return ActionResult.FAIL;
		}
		if (Math.abs(loco.speed()) > 1e-3) {
			return ActionResult.FAIL;
		}
		List<FlatcarEntity> wagons = loco.loadedWagons();
		if (!wagons.isEmpty()) {
			FlatcarEntity last = wagons.get(wagons.size() - 1);
			if (last.blockCount() > 0) {
				player.sendMessage(Text.translatable("message.evolvedwarfare.train.not_empty").formatted(Formatting.RED), true);
				return ActionResult.FAIL;
			}
			loco.wagons().remove(last.getUuid());
			last.discard();
			give(player, new ItemStack(FLATCAR_ITEM));
			world.playSound(null, last.getBlockPos(), SoundEvents.BLOCK_CHAIN_BREAK, SoundCategory.BLOCKS, 1.0F, 0.7F);
			return ActionResult.CONSUME;
		}
		if (loco.hasPassengers()) {
			return ActionResult.FAIL;
		}
		// el carbón que le quede, de vuelta
		int coal = loco.fuel() / LocomotiveEntity.COAL_BLOCK;
		loco.discard();
		give(player, new ItemStack(LOCOMOTIVE_ITEM));
		if (coal > 0) {
			give(player, new ItemStack(net.minecraft.item.Items.COAL_BLOCK, coal));
		}
		return ActionResult.CONSUME;
	}

	private static void give(PlayerEntity player, ItemStack stack) {
		if (!player.giveItemStack(stack)) {
			player.dropItem(stack, false);
		}
	}

	// ------------------------------------------------------------------ para la física

	/** Las colisiones de los trenes dentro de esa caja (para mover a quien sea, menos a los propios coches). */
	public static List<VoxelShape> collisions(Entity entity, Box box, List<VoxelShape> base) {
		if (entity instanceof TrainCarEntity || entity == null) {
			return base;
		}
		World world = entity.getWorld();
		// casi siempre no hay ningún tren cerca: se mira primero la lista de coches del mundo (barato) antes de buscar entidades
		if (!TrainCarEntity.anyNear(world, box)) {
			return base;
		}
		List<TrainCarEntity> cars = world.getEntitiesByClass(TrainCarEntity.class, box.expand(8.0), c -> true);
		if (cars.isEmpty()) {
			return base;
		}
		List<VoxelShape> out = new ArrayList<>(base);
		for (TrainCarEntity car : cars) {
			if (car.getBoundingBox().expand(0.5).intersects(box) && entity.getVehicle() != car) {
				car.collisions(box, out);
			}
		}
		return out;
	}

	/** ¿Va de pie sobre un tren? (para no echarle por "volar"). */
	public static boolean onTrain(Entity entity) {
		Box feet = entity.getBoundingBox().expand(0.1, 0.0, 0.1).stretch(0, -0.6, 0);
		if (!TrainCarEntity.anyNear(entity.getWorld(), feet)) {
			return false;
		}
		for (TrainCarEntity car : entity.getWorld().getEntitiesByClass(TrainCarEntity.class, feet.expand(8.0), c -> true)) {
			for (Box b : car.worldBoxes()) {
				if (b.intersects(feet)) {
					return true;
				}
			}
		}
		return false;
	}

	public static boolean jumping(LivingEntity e) {
		return ((com.evolvedwarfare.mixin.LivingEntityJumpingAccessor) e).evolvedwarfare$jumping();
	}
}
