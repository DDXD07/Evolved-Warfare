package com.evolvedwarfare.entity;

import com.evolvedwarfare.registry.ModSounds;
import com.evolvedwarfare.screen.TraderScreenHandler;
import com.evolvedwarfare.trade.Deal;
import com.evolvedwarfare.trade.TraderType;
import com.evolvedwarfare.world.CityLevels;
import java.util.Arrays;
import java.util.List;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.DyedColorComponent;
import net.minecraft.entity.EntityData;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.ai.goal.FleeEntityGoal;
import net.minecraft.entity.ai.goal.GoToWalkTargetGoal;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtCustomerGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.StopFollowingCustomerGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.ai.goal.WanderAroundFarGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.MerchantEntity;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.stat.Stats;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.village.TradeOffer;
import net.minecraft.world.LocalDifficulty;
import net.minecraft.world.ServerWorldAccess;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * Comerciante de ciudad: vive en su puesto (mercado, fragua, taberna, bazar...), no se va ni desaparece, y comercia
 * siempre con Glory Coins. Al hablarle abre su tienda con todo su catálogo a la vez;
 * lo que tiene depende del nivel de su ciudad ({@link CityLevels}). Repone sus existencias cada mañana.
 * Huye de los monstruos y de los asaltantes.
 */
public class TraderEntity extends MerchantEntity implements com.evolvedwarfare.trade.Shop {
	private static final TrackedData<Integer> TYPE = DataTracker.registerData(TraderEntity.class, TrackedDataHandlerRegistry.INTEGER);
	private static final TrackedData<Integer> VARIANT = DataTracker.registerData(TraderEntity.class, TrackedDataHandlerRegistry.INTEGER);
	/** Hasta dónde se aleja de su puesto. */
	private static final int HOME_RANGE = 8;

	private boolean typeChosen;
	private boolean variantChosen;
	@Nullable
	private BlockPos home;
	private long lastRestockDay = -1L;
	/** Nivel de su ciudad (0 = aún no calculado). */
	private int cityLevel;
	/** Semilla de sus precios. */
	private long priceSeed;
	/** Lotes que le quedan hoy de cada línea del catálogo (null = todo lleno). */
	@Nullable
	private int[] stock;
	/** En las ciudades amuralladas: dónde se pone detrás del mostrador y su cama (de noche se va a dormir). */
	@Nullable
	private BlockPos counter;
	@Nullable
	private BlockPos bed;
	/** La ciudad a la que pertenece (0 = ninguna conocida). */
	private long cityKey;
	/** Ya se ha revisado la escalera de su tienda (las de la 0.36.0 tenían el techo demasiado bajo). */
	private boolean stairsChecked;
	/** Ya se ha puesto (o había) el catálogo en su mostrador. */
	private boolean catalogChecked;

	public TraderEntity(EntityType<? extends TraderEntity> type, World world) {
		super(type, world);
		setPersistent();
	}

	public static DefaultAttributeContainer.Builder createTraderAttributes() {
		return MobEntity.createMobAttributes()
				.add(EntityAttributes.GENERIC_MAX_HEALTH, 20.0)
				.add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.5)
				.add(EntityAttributes.GENERIC_FOLLOW_RANGE, 24.0);
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {
		super.initDataTracker(builder);
		builder.add(TYPE, 0);
		builder.add(VARIANT, 0);
	}

	@Override
	protected void initGoals() {
		goalSelector.add(0, new SwimGoal(this));
		goalSelector.add(1, new StopFollowingCustomerGoal(this));
		goalSelector.add(1, new FleeEntityGoal<>(this, HostileEntity.class, 10.0F, 0.5, 0.6));
		goalSelector.add(1, new LookAtCustomerGoal(this));
		goalSelector.add(4, new GoToWalkTargetGoal(this, 0.4));
		goalSelector.add(8, new WanderAroundFarGoal(this, 0.35));
		goalSelector.add(9, new LookAtEntityGoal(this, PlayerEntity.class, 6.0F, 0.5F));
		goalSelector.add(10, new LookAtEntityGoal(this, MobEntity.class, 8.0F));
		goalSelector.add(10, new LookAroundGoal(this));
	}

	// ------------------------------------------------------------------ oficio y aspecto
	public TraderType getTraderType() {
		TraderType[] values = TraderType.values();
		return values[Math.floorMod(getDataTracker().get(TYPE), values.length)];
	}

	public void setTraderType(TraderType type) {
		getDataTracker().set(TYPE, type.ordinal());
		typeChosen = true;
	}

	public int getVariant() {
		return getDataTracker().get(VARIANT);
	}

	@Override
	protected Text getDefaultName() {
		return Text.translatable("entity.evolvedwarfare.trader." + getTraderType().id());
	}

	@Override
	public EntityData initialize(ServerWorldAccess world, LocalDifficulty difficulty, SpawnReason spawnReason,
			@Nullable EntityData entityData) {
		prepare();
		return super.initialize(world, difficulty, spawnReason, entityData);
	}

	/** Elige oficio y cara si no los trae (de la estructura o del huevo) y le pone el delantal y su herramienta. */
	private void prepare() {
		if (!typeChosen) {
			setTraderType(TraderType.random(random));
		}
		if (!variantChosen) {
			getDataTracker().set(VARIANT, random.nextInt(NpcNames.VARIANTS));
			variantChosen = true;
		}
		TraderType type = getTraderType();
		if (getEquippedStack(EquipmentSlot.CHEST).isEmpty()) {
			ItemStack apron = new ItemStack(Items.LEATHER_CHESTPLATE);
			apron.set(DataComponentTypes.DYED_COLOR, new DyedColorComponent(type.apron, false));
			equipStack(EquipmentSlot.CHEST, apron);
		}
		if (getMainHandStack().isEmpty()) {
			equipStack(EquipmentSlot.MAINHAND, type.tool());
		}
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			setEquipmentDropChance(slot, 0.0F);
		}
	}

	// ------------------------------------------------------------------ vida diaria

	/** Su sitio en una ciudad amurallada: el mostrador (de día), la cama (de noche) y la ciudad. */
	public void setCityHome(BlockPos counter, BlockPos bed, long cityKey) {
		this.counter = counter.toImmutable();
		this.bed = bed.toImmutable();
		this.cityKey = cityKey;
		this.home = this.counter;
	}

	public long getCityKey() {
		return cityKey;
	}

	@Nullable
	public BlockPos getCounter() {
		return counter;
	}

	@Nullable
	public BlockPos getBed() {
		return bed;
	}

	/**
	 * Para las pruebas: a este mercader le fuerza la noche (true) o el día (false); null = la hora del mundo. Es de cada
	 * uno (no de todos) para que las pruebas que corren a la vez no se pisen.
	 */
	@Nullable
	public Boolean forceBedtime;

	/** ¿Es de noche (hora de dormir) para él? */
	private boolean bedtime(World world) {
		if (forceBedtime != null) {
			return forceBedtime;
		}
		return bedtime(world.getTimeOfDay());
	}

	/** ¿Es hora de dormir a esa hora del día? */
	public static boolean bedtime(long timeOfDay) {
		long t = Math.floorMod(timeOfDay, 24000L);
		return t >= 12500L && t < 23400L;
	}

	/** Dormido no se mueve. */
	@Override
	protected boolean isImmobile() {
		return super.isImmobile() || isSleeping();
	}

	/**
	 * Horario de los mercaderes de las ciudades: de día, detrás de su mostrador; al caer la noche sube a su cuarto (o va
	 * al de atrás) y se acuesta; al amanecer se levanta y vuelve al mostrador.
	 */
	private void schedule(ServerWorld world) {
		if (counter == null || bed == null) {
			return;
		}
		boolean night = bedtime(world);
		BlockPos target = night ? bed : counter;
		if (!night && isSleeping()) {
			wakeUp();
		}
		if (getPositionTarget() == null || !getPositionTarget().equals(target) || getPositionTargetRange() != 1) {
			setPositionTarget(target, 1);
		}
		if (isSleeping() || getCustomer() != null) {
			return;
		}
		double dx = getX() - (target.getX() + 0.5);
		double dz = getZ() - (target.getZ() + 0.5);
		double flat = dx * dx + dz * dz;
		if (night) {
			if (flat <= 3.2 && Math.abs(getY() - target.getY()) <= 1.5 && world.getBlockState(bed).getBlock() instanceof net.minecraft.block.BedBlock
					&& !world.getBlockState(bed).get(net.minecraft.block.BedBlock.OCCUPIED)) {
				getNavigation().stop();
				sleep(bed);
				return;
			}
		} else if (flat <= 0.36 && Math.abs(getY() - target.getY()) < 1.0) {
			return;
		}
		if (age % 20 == 0 && getNavigation().isIdle()) {
			getNavigation().startMovingTo(target.getX() + 0.5, target.getY(), target.getZ() + 0.5, 0.5);
		}
	}

	@Override
	public void tick() {
		super.tick();
		if (!(getWorld() instanceof ServerWorld world)) {
			return;
		}
		if (home == null) {
			prepare();
			home = getBlockPos();
		}
		if (cityLevel <= 0) {
			cityLevel = CityLevels.levelAt(world, home);
		}
		if (priceSeed == 0L) {
			priceSeed = random.nextLong() | 1L;
		}
		if (counter != null && !stairsChecked && age % 100 == 50) {
			net.minecraft.structure.StructureStart start = world.getStructureAccessor().getStructureContaining(counter, CityLevels.CITIES);
			stairsChecked = com.evolvedwarfare.world.city.CityPiece.of(start).map(city -> city.repairStairs(world, counter)).orElse(true);
		}
		if (counter != null && !catalogChecked && age % 100 == 70) {
			catalogChecked = placeCatalog(world);
		}
		if (counter != null) {
			schedule(world);
		} else if (getPositionTarget() == null || !getPositionTarget().equals(home) || getPositionTargetRange() != HOME_RANGE) {
			setPositionTarget(home, HOME_RANGE);
		}
		if (age % 100 == 0) {
			restockIfNewDay(world);
		}
	}

	/** Cada mañana repone lo vendido y lo comprado. */
	private void restockIfNewDay(ServerWorld world) {
		long day = world.getTimeOfDay() / 24000L;
		if (day == lastRestockDay) {
			return;
		}
		boolean first = lastRestockDay < 0L;
		lastRestockDay = day;
		if (!first && stock != null) {
			stock = null;
			playSound(SoundEvents.ENTITY_VILLAGER_WORK_CARTOGRAPHER, 0.6F, 1.0F);
		}
	}

	// ------------------------------------------------------------------ comercio
	public int getCityLevel() {
		return Math.max(1, Math.min(cityLevel, CityLevels.maxLevel()));
	}

	/** Todo lo que vende y compra este comerciante, con los precios y la calidad de su ciudad (y lo de hoy). */
	public List<Deal> catalog() {
		return getTraderType().catalog(getCityLevel(), priceSeed, Math.max(0L, lastRestockDay), getWorld().getRegistryManager());
	}

	/** Lotes que le quedan hoy de la línea {@code index}. */
	@Override
	public int stockLeft(int index, Deal deal) {
		if (stock == null || index < 0 || index >= stock.length) {
			return deal.stock();
		}
		return Math.min(stock[index], deal.stock());
	}

	@Override
	public void consumeStock(int index, int lots) {
		List<Deal> deals = catalog();
		if (index < 0 || index >= deals.size()) {
			return;
		}
		if (stock == null || stock.length != deals.size()) {
			stock = new int[deals.size()];
			for (int i = 0; i < stock.length; i++) {
				stock[i] = deals.get(i).stock();
			}
		}
		stock[index] = Math.max(0, stock[index] - lots);
	}

	@Override
	public MerchantEntity merchant() {
		return this;
	}

	/** Después de cada trato. */
	@Override
	public void onTrade(PlayerEntity player) {
		playSound(getYesSound(), 0.8F, getSoundPitch());
	}

	@Override
	protected void fillRecipes() {
		// no usa las ofertas de los aldeanos: tiene su propia tienda (TraderScreenHandler)
	}

	@Override
	protected void afterUsing(TradeOffer offer) {
	}

	@Override
	public ActionResult interactMob(PlayerEntity player, Hand hand) {
		ItemStack stack = player.getStackInHand(hand);
		if (stack.getItem() instanceof net.minecraft.item.SpawnEggItem || stack.isOf(Items.NAME_TAG) || !isAlive()) {
			return super.interactMob(player, hand);
		}
		if (isSleeping()) {
			if (!getWorld().isClient) {
				player.sendMessage(Text.translatable("message.evolvedwarfare.trader.sleeping", getDisplayName()), true);
			}
			return ActionResult.success(getWorld().isClient);
		}
		if (hand == Hand.MAIN_HAND) {
			player.incrementStat(Stats.TALKED_TO_VILLAGER);
		}
		if (player instanceof ServerPlayerEntity serverPlayer) {
			openCatalog(serverPlayer, catalogNearby());
		}
		return ActionResult.success(getWorld().isClient);
	}

	/** El libro de su mostrador (a menos de 4 bloques de su sitio), o null. */
	@Nullable
	public BlockPos catalogNearby() {
		BlockPos center = counter != null ? counter : getBlockPos();
		BlockPos best = null;
		double bestDistance = Double.MAX_VALUE;
		for (BlockPos p : BlockPos.iterate(center.add(-4, -1, -4), center.add(4, 2, 4))) {
			if (getWorld().getBlockState(p).isOf(com.evolvedwarfare.registry.ModBlocks.TRADER_CATALOG)) {
				double d = p.getSquaredDistance(center);
				if (d < bestDistance) {
					bestDistance = d;
					best = p.toImmutable();
				}
			}
		}
		return best;
	}

	/** Abre su catálogo (el libro del mostrador, si se da: así el jugador se acerca a él y lo abre). */
	public void openCatalog(ServerPlayerEntity player, @Nullable BlockPos book) {
		TraderScreenHandler.Data data = prepareShop(player);
		TraderEntity trader = this;
		com.evolvedwarfare.screen.CatalogScreenHandler.Opening opening = new com.evolvedwarfare.screen.CatalogScreenHandler.Opening(data,
				book != null ? book : BlockPos.ORIGIN, book != null);
		player.openHandledScreen(new ExtendedScreenHandlerFactory<com.evolvedwarfare.screen.CatalogScreenHandler.Opening>() {
			@Override
			public com.evolvedwarfare.screen.CatalogScreenHandler.Opening getScreenOpeningData(ServerPlayerEntity serverPlayer) {
				return opening;
			}

			@Override
			public Text getDisplayName() {
				return trader.getDisplayName();
			}

			@Override
			public ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity playerEntity) {
				return new com.evolvedwarfare.screen.CatalogScreenHandler(syncId, playerInventory, trader, opening);
			}
		});
		if (player.currentScreenHandler instanceof com.evolvedwarfare.screen.CatalogScreenHandler) {
			// para que los demás lo vean leyendo, con la portada de su libro
			int trade = getTraderType().ordinal();
			if (book != null && getWorld().getBlockState(book).getBlock() instanceof com.evolvedwarfare.block.CatalogBlock) {
				trade = getWorld().getBlockState(book).get(com.evolvedwarfare.block.CatalogBlock.TRADE);
			}
			com.evolvedwarfare.trade.CatalogReaders.open(player, trade, book);
		}
	}

	/** Lo que hace falta antes de abrir la tienda (reponer, nivel, precios, cliente) y lo que se le manda. */
	private TraderScreenHandler.Data prepareShop(ServerPlayerEntity player) {
		if (getWorld() instanceof ServerWorld world) {
			restockIfNewDay(world);
		}
		if (cityLevel <= 0 && getWorld() instanceof ServerWorld world) {
			cityLevel = CityLevels.levelAt(world, home != null ? home : getBlockPos());
		}
		if (priceSeed == 0L) {
			priceSeed = random.nextLong() | 1L;
		}
		if (getCustomer() == null) {
			setCustomer(player);
		}
		int percent = (int) Math.round((CityLevels.priceMultiplier(getCityLevel()) - 1.0) * 100.0);
		return new TraderScreenHandler.Data(getId(), getTraderType().ordinal(), getCityLevel(), percent, catalog());
	}

	/**
	 * Las tiendas generadas antes de que existiera el catálogo: pone el libro encima del mostrador, delante de donde se
	 * pone el mercader (si hay sitio). Devuelve true si ya no hace falta mirar más.
	 */
	private boolean placeCatalog(ServerWorld world) {
		if (counter == null || catalogNearby() != null) {
			return true;
		}
		for (net.minecraft.util.math.Direction dir : net.minecraft.util.math.Direction.Type.HORIZONTAL) {
			BlockPos desk = counter.offset(dir);
			BlockPos top = desk.up();
			BlockPos customer = desk.offset(dir);
			if (world.getBlockState(desk).isSolidBlock(world, desk) && world.getBlockState(top).isAir()
					&& !world.getBlockState(customer).isSolidBlock(world, customer)) {
				world.setBlockState(top, com.evolvedwarfare.registry.ModBlocks.TRADER_CATALOG.getDefaultState()
						.with(com.evolvedwarfare.block.CatalogBlock.FACING, dir)
						.with(com.evolvedwarfare.block.CatalogBlock.TRADE, getTraderType().ordinal()));
				return true;
			}
		}
		return true;
	}

	private void openShop(ServerPlayerEntity player) {
		if (getWorld() instanceof ServerWorld world) {
			restockIfNewDay(world); // por si es el primer cliente del día
		}
		if (cityLevel <= 0 && getWorld() instanceof ServerWorld world) {
			cityLevel = CityLevels.levelAt(world, home != null ? home : getBlockPos());
		}
		if (priceSeed == 0L) {
			priceSeed = random.nextLong() | 1L;
		}
		if (getCustomer() == null) {
			setCustomer(player);
		}
		TraderEntity trader = this;
		int percent = (int) Math.round((CityLevels.priceMultiplier(getCityLevel()) - 1.0) * 100.0);
		TraderScreenHandler.Data data = new TraderScreenHandler.Data(getId(), getTraderType().ordinal(), getCityLevel(), percent, catalog());
		player.openHandledScreen(new ExtendedScreenHandlerFactory<TraderScreenHandler.Data>() {
			@Override
			public TraderScreenHandler.Data getScreenOpeningData(ServerPlayerEntity serverPlayer) {
				return data;
			}

			@Override
			public Text getDisplayName() {
				return trader.getDisplayName();
			}

			@Override
			public ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity playerEntity) {
				return new TraderScreenHandler(syncId, playerInventory, trader, data);
			}
		});
	}

	@Override
	public boolean isLeveledMerchant() {
		return false;
	}

	@Nullable
	@Override
	public PassiveEntity createChild(ServerWorld world, PassiveEntity entity) {
		return null;
	}

	@Override
	public boolean canImmediatelyDespawn(double distanceSquared) {
		return false;
	}

	@Override
	public boolean cannotDespawn() {
		return true;
	}

	// ------------------------------------------------------------------ sonidos
	@Override
	protected SoundEvent getAmbientSound() {
		return ModSounds.TRADER.ambient();
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return ModSounds.TRADER.hurt();
	}

	@Override
	protected SoundEvent getDeathSound() {
		return ModSounds.TRADER.death();
	}

	@Override
	protected SoundEvent getTradingSound(boolean sold) {
		return sold ? ModSounds.TRADER_YES : ModSounds.TRADER_NO;
	}

	@Override
	public SoundEvent getYesSound() {
		return ModSounds.TRADER_YES;
	}

	@Override
	public float getSoundPitch() {
		return ModSounds.pitch(this);
	}

	/** Cada 10 s o más (los aldeanos, cada 4). */
	@Override
	public int getMinAmbientSoundDelay() {
		return 200;
	}

	// ------------------------------------------------------------------ guardado
	@Override
	public void writeCustomDataToNbt(NbtCompound nbt) {
		super.writeCustomDataToNbt(nbt);
		nbt.putString("TraderType", getTraderType().id());
		nbt.putInt("Variant", getVariant());
		nbt.putLong("LastRestock", lastRestockDay);
		nbt.putInt("CityLevel", cityLevel);
		nbt.putLong("PriceSeed", priceSeed);
		if (stock != null) {
			nbt.putIntArray("Stock", stock);
		}
		if (home != null) {
			nbt.putInt("HomeX", home.getX());
			nbt.putInt("HomeY", home.getY());
			nbt.putInt("HomeZ", home.getZ());
		}
		if (counter != null && bed != null) {
			nbt.putLong("Counter", counter.asLong());
			nbt.putLong("Bed", bed.asLong());
		}
		nbt.putLong("CityKey", cityKey);
		nbt.putBoolean("StairsChecked", stairsChecked);
		nbt.putBoolean("CatalogChecked", catalogChecked);
	}

	@Override
	public void readCustomDataFromNbt(NbtCompound nbt) {
		super.readCustomDataFromNbt(nbt);
		if (nbt.contains("TraderType")) {
			TraderType type = TraderType.byId(nbt.getString("TraderType"));
			if (type != null) {
				setTraderType(type);
			}
		}
		if (nbt.contains("Variant")) {
			getDataTracker().set(VARIANT, nbt.getInt("Variant"));
			variantChosen = true;
		}
		if (nbt.contains("LastRestock")) {
			lastRestockDay = nbt.getLong("LastRestock");
		}
		if (nbt.contains("CityLevel")) {
			cityLevel = nbt.getInt("CityLevel");
		}
		if (nbt.contains("PriceSeed")) {
			priceSeed = nbt.getLong("PriceSeed");
		}
		stock = nbt.contains("Stock") ? Arrays.copyOf(nbt.getIntArray("Stock"), nbt.getIntArray("Stock").length) : null;
		if (nbt.contains("HomeX")) {
			home = new BlockPos(nbt.getInt("HomeX"), nbt.getInt("HomeY"), nbt.getInt("HomeZ"));
		}
		if (nbt.contains("Counter") && nbt.contains("Bed")) {
			counter = BlockPos.fromLong(nbt.getLong("Counter"));
			bed = BlockPos.fromLong(nbt.getLong("Bed"));
		}
		cityKey = nbt.getLong("CityKey");
		stairsChecked = nbt.getBoolean("StairsChecked");
		catalogChecked = nbt.getBoolean("CatalogChecked");
	}
}
