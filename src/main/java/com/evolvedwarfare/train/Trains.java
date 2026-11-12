package com.evolvedwarfare.train;

import com.evolvedwarfare.EvolvedWarfare;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.block.piston.PistonBehavior;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.Items;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.recipe.Ingredient;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ChunkTicketType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.Rarity;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.World;

/**
 * El tren de vapor grande (fase 52): las vías de dos de ancho (rectas, cuestas, curvas y desvíos), la locomotora, el
 * ténder y la plataforma (donde se construye como en ARK); las partes con las que se montan en la mesa de ensamblaje; el
 * martillo de construcción y las piezas. Y lo que hace falta para andar sobre el tren mientras va (sus colisiones y que
 * no te echen por "volar").
 */
public final class Trains {
	public static final ChunkTicketType<ChunkPos> TICKET = ChunkTicketType.create("evolvedwarfare_train", Comparator.comparingLong(ChunkPos::toLong), 80);
	public static final List<Item> ITEMS = new ArrayList<>();

	// ------------------------------------------------------------------ las vías
	public static final TrackBlock TRACK = Registry.register(Registries.BLOCK, EvolvedWarfare.id("big_track"),
			new TrackBlock(AbstractBlock.Settings.create().strength(0.8F).sounds(BlockSoundGroup.METAL).nonOpaque()
					.pistonBehavior(PistonBehavior.BLOCK)));
	public static final BlockEntityType<TrackBlock.TrackBlockEntity> TRACK_ENTITY = Registry.register(Registries.BLOCK_ENTITY_TYPE,
			EvolvedWarfare.id("big_track"), BlockEntityType.Builder.create(TrackBlock.TrackBlockEntity::new, TRACK).build(null));
	public static final Item TRACK_STRAIGHT = item("track_straight", new TrackItem(Track.Shape.STRAIGHT, false, new Item.Settings()));
	public static final Item TRACK_SLOPE = item("track_slope", new TrackItem(Track.Shape.SLOPE, false, new Item.Settings()));
	public static final Item TRACK_CURVE_RIGHT = item("track_curve_right", new TrackItem(Track.Shape.CURVE, false, new Item.Settings()));
	public static final Item TRACK_CURVE_LEFT = item("track_curve_left", new TrackItem(Track.Shape.CURVE, true, new Item.Settings()));
	public static final Item TRACK_SWITCH_RIGHT = item("track_switch_right", new TrackItem(Track.Shape.SWITCH, false, new Item.Settings()));
	public static final Item TRACK_SWITCH_LEFT = item("track_switch_left", new TrackItem(Track.Shape.SWITCH, true, new Item.Settings()));

	// ------------------------------------------------------------------ las partes y la mesa
	public static final Item CHASSIS = item("train_chassis", new PartItem(new Item.Settings()));
	public static final Item WHEELS = item("train_wheels", new PartItem(new Item.Settings()));
	public static final Item BOILER = item("steam_boiler", new PartItem(new Item.Settings()));
	public static final Item CAB = item("train_cab", new PartItem(new Item.Settings()));
	public static final Item BUNKER = item("coal_bunker", new PartItem(new Item.Settings()));
	public static final AssemblyTableBlock ASSEMBLY_TABLE = Registry.register(Registries.BLOCK, EvolvedWarfare.id("train_assembly_table"),
			new AssemblyTableBlock(AbstractBlock.Settings.create().strength(2.5F).sounds(BlockSoundGroup.METAL).requiresTool()));
	public static final Item ASSEMBLY_TABLE_ITEM = item("train_assembly_table", new BlockItem(ASSEMBLY_TABLE, new Item.Settings()));
	public static final ScreenHandlerType<AssemblyScreenHandler> ASSEMBLY_SCREEN = Registry.register(Registries.SCREEN_HANDLER,
			EvolvedWarfare.id("train_assembly_table"),
			new ScreenHandlerType<>(AssemblyScreenHandler::new, net.minecraft.resource.featuretoggle.FeatureFlags.VANILLA_FEATURES));

	// ------------------------------------------------------------------ los coches
	public static final EntityType<LocomotiveEntity> LOCOMOTIVE = Registry.register(Registries.ENTITY_TYPE, EvolvedWarfare.id("steam_locomotive"),
			EntityType.Builder.<LocomotiveEntity>create(LocomotiveEntity::new, SpawnGroup.MISC).dimensions(2.8F, 3.8F).maxTrackingRange(12)
					.trackingTickInterval(1).makeFireImmune().build("steam_locomotive"));
	public static final EntityType<TenderEntity> TENDER = Registry.register(Registries.ENTITY_TYPE, EvolvedWarfare.id("coal_tender"),
			EntityType.Builder.<TenderEntity>create(TenderEntity::new, SpawnGroup.MISC).dimensions(2.6F, 3.0F).maxTrackingRange(12).trackingTickInterval(1)
					.makeFireImmune().build("coal_tender"));
	public static final EntityType<PlatformEntity> PLATFORM = Registry.register(Registries.ENTITY_TYPE, EvolvedWarfare.id("flatcar"),
			EntityType.Builder.<PlatformEntity>create(PlatformEntity::new, SpawnGroup.MISC).dimensions(3.0F, 1.0F).maxTrackingRange(12)
					.trackingTickInterval(1).makeFireImmune().build("flatcar"));
	public static final ScreenHandlerType<LocomotiveScreenHandler> LOCOMOTIVE_SCREEN = Registry.register(Registries.SCREEN_HANDLER,
			EvolvedWarfare.id("steam_locomotive"),
			new ScreenHandlerType<>(LocomotiveScreenHandler::new, net.minecraft.resource.featuretoggle.FeatureFlags.VANILLA_FEATURES));
	public static final Item LOCOMOTIVE_ITEM = item("steam_locomotive", new CarItem(0, new Item.Settings().maxCount(1).rarity(Rarity.UNCOMMON)));
	public static final Item TENDER_ITEM = item("coal_tender", new CarItem(1, new Item.Settings().maxCount(1)));
	public static final Item PLATFORM_ITEM = item("flatcar", new CarItem(2, new Item.Settings().maxCount(1)));

	// ------------------------------------------------------------------ construir
	public static final Item BUILD_HAMMER = item("building_hammer", new HammerItem(new Item.Settings().maxCount(1)));
	private static final Map<Building.Kind, Map<Building.Material, Item>> PIECES = new EnumMap<>(Building.Kind.class);

	static {
		for (Building.Material m : Building.Material.values()) {
			for (Building.Kind k : Building.Kind.values()) {
				PIECES.computeIfAbsent(k, x -> new EnumMap<>(Building.Material.class))
						.put(m, item(m.id + "_" + k.id, new PieceItem(k, m, new Item.Settings().maxCount(16))));
			}
		}
	}

	/** Para que el cliente abra el menú de construcción (lo pone el cliente). */
	public static Runnable openBuildMenu = () -> {
	};

	private Trains() {
	}

	private static Item item(String name, Item item) {
		ITEMS.add(Registry.register(Registries.ITEM, EvolvedWarfare.id(name), item));
		return item;
	}

	public static Item pieceItem(Building.Kind kind, Building.Material material) {
		return PIECES.get(kind).get(material);
	}

	public static Item trackItem(Track.Shape shape, boolean left) {
		return switch (shape) {
			case STRAIGHT -> TRACK_STRAIGHT;
			case SLOPE -> TRACK_SLOPE;
			case CURVE -> left ? TRACK_CURVE_LEFT : TRACK_CURVE_RIGHT;
			case SWITCH -> left ? TRACK_SWITCH_LEFT : TRACK_SWITCH_RIGHT;
		};
	}

	// ------------------------------------------------------------------ fabricar piezas (desde el menú del martillo)

	public record CraftPiecePayload(int kind, int material, int count) implements CustomPayload {
		public static final Id<CraftPiecePayload> ID = new Id<>(EvolvedWarfare.id("craft_piece"));
		public static final PacketCodec<RegistryByteBuf, CraftPiecePayload> CODEC = PacketCodec.tuple(PacketCodecs.VAR_INT, CraftPiecePayload::kind,
				PacketCodecs.VAR_INT, CraftPiecePayload::material, PacketCodecs.VAR_INT, CraftPiecePayload::count, CraftPiecePayload::new);

		@Override
		public Id<? extends CustomPayload> getId() {
			return ID;
		}
	}

	public static void register() {
		PayloadTypeRegistry.playC2S().register(CraftPiecePayload.ID, CraftPiecePayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(CraftPiecePayload.ID, (payload, context) -> craft(context.player(), payload));
	}

	static void craft(ServerPlayerEntity player, CraftPiecePayload p) {
		if (p.kind() < 0 || p.kind() >= Building.Kind.values().length || p.material() < 0 || p.material() >= Building.Material.values().length) {
			return;
		}
		Building.Kind kind = Building.Kind.values()[p.kind()];
		Building.Material material = Building.Material.values()[p.material()];
		int made = 0;
		for (int i = 0; i < Math.min(64, Math.max(1, p.count())); i++) {
			if (!player.getAbilities().creativeMode && !Building.pay(player.getInventory(), kind, material)) {
				break;
			}
			ItemStack s = new ItemStack(pieceItem(kind, material));
			if (!player.giveItemStack(s)) {
				player.dropItem(s, false);
			}
			made++;
		}
		if (made > 0) {
			player.getWorld().playSound(null, player.getBlockPos(), SoundEvents.BLOCK_SMITHING_TABLE_USE, SoundCategory.PLAYERS, 0.6F, 1.1F);
			player.currentScreenHandler.sendContentUpdates();
			player.playerScreenHandler.sendContentUpdates();
		}
	}

	// ------------------------------------------------------------------ montar el tren en la mesa

	/** Lo que se monta en la mesa de ensamblaje: qué sale y qué hace falta. */
	public record Assembly(Item result, List<Building.Cost> needs) {
	}

	private static List<Assembly> assemblies;

	public static List<Assembly> assemblies() {
		if (assemblies == null) {
			List<Assembly> a = new ArrayList<>();
			a.add(new Assembly(LOCOMOTIVE_ITEM, List.of(need(CHASSIS, 1), need(WHEELS, 3), need(BOILER, 1), need(CAB, 1))));
			a.add(new Assembly(TENDER_ITEM, List.of(need(CHASSIS, 1), need(WHEELS, 2), need(BUNKER, 1))));
			a.add(new Assembly(PLATFORM_ITEM, List.of(need(CHASSIS, 1), need(WHEELS, 2),
					new Building.Cost(Ingredient.fromTag(ItemTags.PLANKS), 12, Items.OAK_PLANKS))));
			assemblies = a;
		}
		return assemblies;
	}

	private static Building.Cost need(Item item, int n) {
		return new Building.Cost(Ingredient.ofItems(item), n, item);
	}

	// ------------------------------------------------------------------ los objetos

	/** Una parte del tren (se monta en la mesa de ensamblaje). */
	public static class PartItem extends Item {
		public PartItem(Settings settings) {
			super(settings);
		}

		@Override
		public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, net.minecraft.item.tooltip.TooltipType type) {
			tooltip.add(Text.translatable("item.evolvedwarfare.train_part.desc").formatted(Formatting.GRAY));
		}
	}

	/** El martillo de construcción: abre el menú de piezas; agachado + clic derecho en una pieza o un coche, lo recoge. */
	public static class HammerItem extends Item {
		public HammerItem(Settings settings) {
			super(settings);
		}

		@Override
		public TypedActionResult<ItemStack> use(World world, PlayerEntity player, Hand hand) {
			if (world.isClient) {
				openBuildMenu.run();
			}
			return TypedActionResult.success(player.getStackInHand(hand), world.isClient);
		}

		@Override
		public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, net.minecraft.item.tooltip.TooltipType type) {
			tooltip.add(Text.translatable("item.evolvedwarfare.building_hammer.desc").formatted(Formatting.GRAY));
		}
	}

	/** La locomotora se pone en una vía; el ténder y la plataforma, con clic derecho en un coche del tren (se enganchan detrás). */
	public static class CarItem extends Item {
		/** 0 locomotora, 1 ténder, 2 plataforma. */
		public final int kind;

		public CarItem(int kind, Settings settings) {
			super(settings);
			this.kind = kind;
		}

		public EntityType<? extends TrainCarEntity> type() {
			return kind == 0 ? LOCOMOTIVE : kind == 1 ? TENDER : PLATFORM;
		}

		@Override
		public ActionResult useOnBlock(ItemUsageContext context) {
			World world = context.getWorld();
			if (Track.piece(world, context.getBlockPos()) == null) {
				return ActionResult.PASS;
			}
			PlayerEntity player = context.getPlayer();
			if (kind != 0) {
				if (!world.isClient && player != null) {
					player.sendMessage(Text.translatable("message.evolvedwarfare.train.couple_hint").formatted(Formatting.GRAY), true);
				}
				return ActionResult.FAIL;
			}
			if (world instanceof ServerWorld sw) {
				Direction facing = player != null ? player.getHorizontalFacing() : context.getHorizontalPlayerFacing();
				Track.Pos start = Track.start(sw, context.getBlockPos(), facing);
				LocomotiveEntity loco = LOCOMOTIVE.create(sw);
				if (start == null || loco == null) {
					return ActionResult.FAIL;
				}
				loco.setTrack(start);
				sw.spawnEntity(loco);
				sw.playSound(null, context.getBlockPos(), SoundEvents.BLOCK_ANVIL_PLACE, SoundCategory.BLOCKS, 0.6F, 0.6F);
				context.getStack().decrementUnlessCreative(1, player);
			}
			return ActionResult.success(world.isClient);
		}

		@Override
		public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, net.minecraft.item.tooltip.TooltipType type) {
			tooltip.add(Text.translatable(getTranslationKey() + ".desc").formatted(Formatting.GRAY));
		}
	}

	// ------------------------------------------------------------------ enganchar y desenganchar

	/** Lo común a todos los coches: un vagón en la mano lo engancha; el martillo, agachado, desengancha o recoge. */
	public static ActionResult carInteract(TrainCarEntity car, PlayerEntity player, Hand hand) {
		ItemStack stack = player.getStackInHand(hand);
		if (stack.getItem() instanceof CarItem ci && ci.kind != 0) {
			return couple(car, player, stack, ci);
		}
		if (stack.isOf(BUILD_HAMMER) && player.isSneaking()) {
			return uncouple(car, player);
		}
		return ActionResult.PASS;
	}

	/** Engancha un vagón nuevo al final del tren de ese coche. */
	static ActionResult couple(TrainCarEntity car, PlayerEntity player, ItemStack stack, CarItem item) {
		if (!(car.getWorld() instanceof ServerWorld world)) {
			return ActionResult.SUCCESS;
		}
		LocomotiveEntity loco = car.loco();
		if (loco == null) {
			player.sendMessage(Text.translatable("message.evolvedwarfare.train.couple_hint").formatted(Formatting.GRAY), true);
			return ActionResult.FAIL;
		}
		if (loco.wagons().size() >= LocomotiveEntity.MAX_WAGONS || Math.abs(loco.speed()) > 1e-3) {
			player.sendMessage(Text.translatable("message.evolvedwarfare.train.no_room").formatted(Formatting.RED), true);
			return ActionResult.FAIL;
		}
		TrainCarEntity wagon = item.type().create(world);
		if (wagon == null) {
			return ActionResult.FAIL;
		}
		wagon.setPosition(loco.getPos());
		world.spawnEntity(wagon);
		loco.addWagon(wagon);
		if (!loco.place()) {
			loco.wagons().remove(wagon.getUuid());
			wagon.discard();
			loco.place();
			player.sendMessage(Text.translatable("message.evolvedwarfare.train.no_room").formatted(Formatting.RED), true);
			return ActionResult.FAIL;
		}
		world.playSound(null, wagon.getBlockPos(), SoundEvents.BLOCK_CHAIN_PLACE, SoundCategory.BLOCKS, 1.0F, 0.7F);
		player.sendMessage(Text.translatable("message.evolvedwarfare.train.coupled").formatted(Formatting.GRAY), true);
		stack.decrementUnlessCreative(1, player);
		return ActionResult.CONSUME;
	}

	/** Con el martillo, agachado: se desengancha el último vagón (o, sin vagones, se recoge la locomotora). */
	static ActionResult uncouple(TrainCarEntity car, PlayerEntity player) {
		if (!(car.getWorld() instanceof ServerWorld world)) {
			return ActionResult.SUCCESS;
		}
		LocomotiveEntity loco = car.loco();
		if (loco == null) {
			// un vagón suelto (sin su locomotora): se recoge
			pickUp(car, player);
			return ActionResult.CONSUME;
		}
		if (Math.abs(loco.speed()) > 1e-3) {
			return ActionResult.FAIL;
		}
		List<TrainCarEntity> wagons = loco.loadedWagons();
		if (!wagons.isEmpty()) {
			TrainCarEntity last = wagons.get(wagons.size() - 1);
			if (last instanceof PlatformEntity p && (!p.pieces().isEmpty() || !p.blocks().isEmpty())) {
				player.sendMessage(Text.translatable("message.evolvedwarfare.train.not_empty").formatted(Formatting.RED), true);
				return ActionResult.FAIL;
			}
			loco.wagons().remove(last.getUuid());
			pickUp(last, player);
			world.playSound(null, last.getBlockPos(), SoundEvents.BLOCK_CHAIN_BREAK, SoundCategory.BLOCKS, 1.0F, 0.7F);
			player.sendMessage(Text.translatable("message.evolvedwarfare.train.uncoupled").formatted(Formatting.GRAY), true);
			return ActionResult.CONSUME;
		}
		if (loco.hasPassengers()) {
			return ActionResult.FAIL;
		}
		pickUp(loco, player);
		return ActionResult.CONSUME;
	}

	private static void pickUp(TrainCarEntity car, PlayerEntity player) {
		Item item;
		if (car instanceof LocomotiveEntity l) {
			l.dropContents();
			item = LOCOMOTIVE_ITEM;
		} else if (car instanceof TenderEntity t) {
			t.dropContents();
			item = TENDER_ITEM;
		} else {
			if (car instanceof PlatformEntity p) {
				p.dropContents();
			}
			item = PLATFORM_ITEM;
		}
		car.discard();
		ItemStack s = new ItemStack(item);
		if (!player.getAbilities().creativeMode && !player.giveItemStack(s)) {
			player.dropItem(s, false);
		}
	}

	// ------------------------------------------------------------------ para la física

	/** Las colisiones de los trenes dentro de esa caja (para mover a quien sea, menos a los propios coches). */
	public static List<VoxelShape> collisions(Entity entity, Box box, List<VoxelShape> base) {
		if (entity instanceof TrainCarEntity || entity == null) {
			return base;
		}
		World world = entity.getWorld();
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

	/** El calor de las fogatas y hornos encendidos de los trenes cerca de ese sitio (para el clima). */
	public static float trainWarmth(World world, net.minecraft.util.math.BlockPos center) {
		Box near = new Box(center).expand(6.0);
		if (!TrainCarEntity.anyNear(world, near)) {
			return 0.0F;
		}
		float[] heat = {0.0F};
		net.minecraft.util.math.Vec3d c = net.minecraft.util.math.Vec3d.ofCenter(center);
		for (PlatformEntity p : world.getEntitiesByClass(PlatformEntity.class, near.expand(4.0), x -> !x.blocks().isEmpty())) {
			p.heat((pos, w) -> heat[0] += (float) (w * Math.max(0.0, 1.0 - pos.distanceTo(c) / 6.0)));
		}
		for (com.evolvedwarfare.cart.CartEntity cart : world.getEntitiesByClass(com.evolvedwarfare.cart.CartEntity.class, near.expand(4.0), x -> true)) {
			cart.heat((pos, w) -> heat[0] += (float) (w * Math.max(0.0, 1.0 - pos.distanceTo(c) / 6.0)));
		}
		return heat[0];
	}

	/** El bloque de la mesa (para las pruebas). */
	public static Block assemblyTable() {
		return ASSEMBLY_TABLE;
	}
}
