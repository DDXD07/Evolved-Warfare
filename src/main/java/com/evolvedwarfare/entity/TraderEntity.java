package com.evolvedwarfare.entity;

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
 * siempre con Golden Coins (o esmeraldas si no está ese mod). Al hablarle abre su tienda con todo su catálogo a la vez;
 * lo que tiene depende del nivel de su ciudad ({@link CityLevels}). Repone sus existencias cada mañana.
 * Huye de los monstruos y de los asaltantes.
 */
public class TraderEntity extends MerchantEntity {
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
		if (getPositionTarget() == null || !getPositionTarget().equals(home) || getPositionTargetRange() != HOME_RANGE) {
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

	/** Todo lo que vende y compra este comerciante, con los precios de su ciudad. */
	public List<Deal> catalog() {
		return getTraderType().catalog(getCityLevel(), priceSeed);
	}

	/** Lotes que le quedan hoy de la línea {@code index}. */
	public int stockLeft(int index, Deal deal) {
		if (stock == null || index < 0 || index >= stock.length) {
			return deal.stock();
		}
		return Math.min(stock[index], deal.stock());
	}

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

	/** Después de cada trato. */
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
		if (hand == Hand.MAIN_HAND) {
			player.incrementStat(Stats.TALKED_TO_VILLAGER);
		}
		if (player instanceof ServerPlayerEntity serverPlayer) {
			openShop(serverPlayer);
		}
		return ActionResult.success(getWorld().isClient);
	}

	private void openShop(ServerPlayerEntity player) {
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
		TraderScreenHandler.Data data = new TraderScreenHandler.Data(getId(), getCityLevel(), percent, catalog());
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
		return hasCustomer() ? SoundEvents.ENTITY_VILLAGER_TRADE : SoundEvents.ENTITY_VILLAGER_AMBIENT;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.ENTITY_VILLAGER_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.ENTITY_VILLAGER_DEATH;
	}

	@Override
	protected SoundEvent getTradingSound(boolean sold) {
		return sold ? SoundEvents.ENTITY_VILLAGER_YES : SoundEvents.ENTITY_VILLAGER_NO;
	}

	@Override
	public SoundEvent getYesSound() {
		return SoundEvents.ENTITY_VILLAGER_YES;
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
	}
}
