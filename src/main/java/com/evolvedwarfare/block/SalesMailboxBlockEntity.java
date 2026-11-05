package com.evolvedwarfare.block;

import com.evolvedwarfare.item.CropCrateItem;
import com.evolvedwarfare.item.GloryPouchItem;
import com.evolvedwarfare.logistics.ItemValues;
import com.evolvedwarfare.logistics.Market;
import com.evolvedwarfare.registry.ModBlocks;
import com.evolvedwarfare.registry.ModComponents;
import com.evolvedwarfare.registry.ModItems;
import com.evolvedwarfare.screen.SalesMailboxScreenHandler;
import com.evolvedwarfare.world.Hordes;
import java.util.UUID;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.BundleContentsComponent;
import net.minecraft.component.type.ContainerComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventories;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SidedInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ItemStackParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * Lo de dentro de un Buzón de Ventas: 16 huecos para vender (4x4) y uno aparte para las monedas.
 *
 * <p>Cada vez que empieza un día nuevo (al amanecer, el mismo día que cuenta el reloj), vende todo lo que tenga precio
 * en el mercado a la cuarta parte de lo que cuesta nuevo ({@link ItemValues}: el de las tiendas o, si no, el de sus
 * materiales); lo gastado, según lo que le
 * quede de durabilidad, y las Cajas de víveres, por lo que llevan. Lo que no tiene precio se queda donde estaba. Las
 * monedas van a su hueco (si pasan de un montón, en una Bolsa de Gloria) y lo que sobra de una moneda se guarda para la
 * próxima venta. Si el buzón estaba sin cargar, vende al volver a cargarse (una vez).</p>
 *
 * <p>Las tolvas pueden meter cosas con precio por arriba y los lados y sacar las monedas por abajo.</p>
 */
public class SalesMailboxBlockEntity extends BlockEntity implements SidedInventory, ExtendedScreenHandlerFactory<BlockPos> {
	public static final int SALE_SLOTS = 16;
	public static final int COIN_SLOT = SALE_SLOTS;
	public static final int SIZE = SALE_SLOTS + 1;
	/** Lo que paga el buzón: la cuarta parte del precio de mercado. */
	public static final double SHARE = 0.25;

	private static final int[] SALE = new int[SALE_SLOTS];
	private static final int[] COIN = {COIN_SLOT};

	static {
		for (int i = 0; i < SALE_SLOTS; i++) {
			SALE[i] = i;
		}
	}

	private final DefaultedList<ItemStack> items = DefaultedList.ofSize(SIZE, ItemStack.EMPTY);
	@Nullable
	private UUID owner;
	/** El día de la última venta (0: aún no se ha mirado). */
	private long lastDay;
	/** Lo que sobró de una moneda en las ventas anteriores. */
	private double credit;
	/** Lo de la última venta, para la pantalla. */
	private int lastSold;
	private int lastCoins;

	private final PropertyDelegate properties = new PropertyDelegate() {
		@Override
		public int get(int index) {
			return index == 0 ? lastSold : lastCoins;
		}

		@Override
		public void set(int index, int value) {
			if (index == 0) {
				lastSold = value;
			} else {
				lastCoins = value;
			}
		}

		@Override
		public int size() {
			return 2;
		}
	};

	public SalesMailboxBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlocks.SALES_MAILBOX_ENTITY, pos, state);
	}

	// ------------------------------------------------------------------ dueño

	@Nullable
	public UUID getOwner() {
		return owner;
	}

	public void setOwner(@Nullable UUID owner) {
		this.owner = owner;
		markDirty();
	}

	public boolean canAccess(PlayerEntity player) {
		return owner == null || owner.equals(player.getUuid()) || player.isCreative();
	}

	// ------------------------------------------------------------------ precios

	/**
	 * Lo que paga el buzón por ese montón (en monedas, con decimales), o 0 si no lo compra: las monedas y las bolsas no
	 * se venden, ni nada que lleve cosas dentro (cofres de shulker, saquitos...), que se perderían.
	 */
	public static double saleValue(ItemStack stack, @Nullable World world) {
		if (stack.isEmpty() || Market.isCurrency(stack) || GloryPouchItem.isPouch(stack)) {
			return 0.0;
		}
		if (stack.contains(ModComponents.FISH)) {
			// los peces con calidad: según su peso y su calidad (solo aquí se venden)
			return com.evolvedwarfare.fishing.Fishing.price(stack) * stack.getCount();
		}
		if (stack.getItem() instanceof CropCrateItem) {
			CropCrateItem.Contents contents = stack.get(ModComponents.CROP_CRATE);
			double crate = ItemValues.value(stack.getItem(), world);
			double inside = contents == null ? 0.0 : contents.count() * ItemValues.value(contents.item(), world);
			return SHARE * (crate + inside) * stack.getCount();
		}
		if (stack.getOrDefault(DataComponentTypes.CONTAINER, ContainerComponent.DEFAULT).streamNonEmpty().findAny().isPresent()) {
			return 0.0;
		}
		BundleContentsComponent bundle = stack.get(DataComponentTypes.BUNDLE_CONTENTS);
		if (bundle != null && !bundle.isEmpty()) {
			return 0.0;
		}
		double unit = ItemValues.value(stack.getItem(), world);
		if (unit <= 0.0) {
			return 0.0;
		}
		if (stack.isDamageable() && stack.getMaxDamage() > 0) {
			unit *= Math.max(0.0, stack.getMaxDamage() - stack.getDamage()) / (double) stack.getMaxDamage();
		}
		return SHARE * unit * stack.getCount();
	}

	public static boolean sellable(ItemStack stack, @Nullable World world) {
		return saleValue(stack, world) > 0.0;
	}

	// ------------------------------------------------------------------ venta

	public static void tick(World world, BlockPos pos, BlockState state, SalesMailboxBlockEntity mailbox) {
		mailbox.onDay(Hordes.day(world.getTimeOfDay()));
	}

	/** Cada tic, con el día de hoy: si ha empezado uno nuevo desde la última vez, vende. Devuelve si ha vendido. */
	public boolean onDay(long day) {
		if (lastDay == 0L || day < lastDay) {
			// recién puesto, o han echado el tiempo atrás: se empieza a contar desde hoy
			lastDay = day;
			markDirty();
			return false;
		}
		if (day > lastDay) {
			lastDay = day;
			sell();
			return true;
		}
		return false;
	}

	/**
	 * Vende todo lo que tenga precio, mete las monedas en su hueco y, si ha vendido algo, avisa: monedas que saltan del
	 * buzón, campanilla y destellos verdes, y un mensaje al dueño. Devuelve las monedas que ha dado.
	 */
	public int sell() {
		double total = 0.0;
		int sold = 0;
		for (int i = 0; i < SALE_SLOTS; i++) {
			ItemStack stack = items.get(i);
			double value = saleValue(stack, world);
			if (value > 0.0) {
				total += value;
				sold += stack.getCount();
				items.set(i, ItemStack.EMPTY);
			}
		}
		if (sold == 0) {
			markDirty();
			return 0;
		}
		credit += total;
		long coins = (long) Math.floor(credit + 1.0E-9);
		long left = depositCoins(coins);
		credit -= coins - left;
		int paid = (int) Math.min(Integer.MAX_VALUE, coins - left);
		lastSold = sold;
		lastCoins = paid;
		markDirty();
		if (world instanceof ServerWorld server) {
			celebrate(server);
			notifyOwner(server, sold, paid);
		}
		return paid;
	}

	/** Mete monedas en el hueco de las monedas; devuelve las que no caben (solo si hay algo raro en el hueco). */
	private long depositCoins(long amount) {
		if (amount <= 0) {
			return 0;
		}
		ItemStack slot = items.get(COIN_SLOT);
		if (GloryPouchItem.isPouch(slot)) {
			return GloryPouchItem.deposit(slot, amount);
		}
		long have = slot.isEmpty() ? 0 : Market.isPlainCoin(slot) ? slot.getCount() : -1;
		if (have < 0) {
			return amount;
		}
		long total = have + amount;
		int max = Market.currency().getMaxCount();
		if (total <= max) {
			items.set(COIN_SLOT, new ItemStack(Market.currency(), (int) total));
			return 0;
		}
		// más de un montón: todo a una Bolsa de Gloria
		ItemStack pouch = new ItemStack(ModItems.GLORY_POUCH);
		long rest = GloryPouchItem.deposit(pouch, total);
		items.set(COIN_SLOT, pouch);
		return rest;
	}

	/** Las monedas saltan de la ranura, suena la campanilla del buzón y brillan destellos verdes de trato hecho. */
	private void celebrate(ServerWorld server) {
		double x = pos.getX() + 0.5;
		double y = pos.getY() + 1.0;
		double z = pos.getZ() + 0.5;
		server.spawnParticles(new ItemStackParticleEffect(ParticleTypes.ITEM, new ItemStack(Market.currency())), x, y, z, 18,
				0.12, 0.05, 0.12, 0.18);
		server.spawnParticles(ParticleTypes.HAPPY_VILLAGER, x, y - 0.2, z, 12, 0.45, 0.35, 0.45, 0.0);
		server.spawnParticles(ParticleTypes.WAX_ON, x, y - 0.1, z, 8, 0.35, 0.25, 0.35, 0.0);
		server.playSound(null, pos, SoundEvents.BLOCK_NOTE_BLOCK_BELL.value(), SoundCategory.BLOCKS, 0.9F, 1.6F);
		server.playSound(null, pos, SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.BLOCKS, 0.7F, 0.9F);
		server.playSound(null, pos, SoundEvents.BLOCK_CHAIN_PLACE, SoundCategory.BLOCKS, 0.6F, 1.8F);
	}

	private void notifyOwner(ServerWorld server, int sold, int coins) {
		if (owner == null) {
			return;
		}
		ServerPlayerEntity player = server.getServer().getPlayerManager().getPlayer(owner);
		if (player != null) {
			player.sendMessage(Text.translatable("message.evolvedwarfare.sales_mailbox.sold", sold, coins,
					pos.getX(), pos.getY(), pos.getZ()).formatted(Formatting.GOLD), false);
		}
	}

	public PropertyDelegate properties() {
		return properties;
	}

	// ------------------------------------------------------------------ inventario

	@Override
	public int size() {
		return SIZE;
	}

	@Override
	public boolean isEmpty() {
		for (ItemStack stack : items) {
			if (!stack.isEmpty()) {
				return false;
			}
		}
		return true;
	}

	@Override
	public ItemStack getStack(int slot) {
		return items.get(slot);
	}

	@Override
	public ItemStack removeStack(int slot, int amount) {
		ItemStack taken = Inventories.splitStack(items, slot, amount);
		if (!taken.isEmpty()) {
			markDirty();
		}
		return taken;
	}

	@Override
	public ItemStack removeStack(int slot) {
		ItemStack taken = Inventories.removeStack(items, slot);
		markDirty();
		return taken;
	}

	@Override
	public void setStack(int slot, ItemStack stack) {
		items.set(slot, stack);
		stack.capCount(getMaxCount(stack));
		markDirty();
	}

	/** En los huecos de venta, cualquier cosa menos monedas y bolsas; en el de las monedas, solo una bolsa propia. */
	@Override
	public boolean isValid(int slot, ItemStack stack) {
		if (slot == COIN_SLOT) {
			return GloryPouchItem.isPouch(stack);
		}
		return !Market.isCurrency(stack) && !GloryPouchItem.isPouch(stack);
	}

	@Override
	public int[] getAvailableSlots(Direction side) {
		return side == Direction.DOWN ? COIN : SALE;
	}

	/** Las tolvas solo meten lo que se puede vender (para no atascarlo). */
	@Override
	public boolean canInsert(int slot, ItemStack stack, @Nullable Direction dir) {
		return slot < SALE_SLOTS && dir != Direction.DOWN && sellable(stack, world);
	}

	@Override
	public boolean canExtract(int slot, ItemStack stack, Direction dir) {
		return slot == COIN_SLOT && dir == Direction.DOWN;
	}

	@Override
	public boolean canPlayerUse(PlayerEntity player) {
		return Inventory.canPlayerUse(this, player) && canAccess(player);
	}

	@Override
	public void clear() {
		items.clear();
		markDirty();
	}

	/** Al cambiar las monedas, sube o baja la banderita. */
	@Override
	public void markDirty() {
		super.markDirty();
		if (world != null && !world.isClient) {
			BlockState state = getCachedState();
			boolean flag = !items.get(COIN_SLOT).isEmpty();
			if (state.contains(SalesMailboxBlock.FLAG) && state.get(SalesMailboxBlock.FLAG) != flag) {
				world.setBlockState(pos, state.with(SalesMailboxBlock.FLAG, flag), Block.NOTIFY_ALL);
			}
		}
	}

	// ------------------------------------------------------------------ menú

	@Override
	public BlockPos getScreenOpeningData(ServerPlayerEntity player) {
		return pos;
	}

	@Override
	public Text getDisplayName() {
		return getCachedState().getBlock().getName();
	}

	@Override
	public ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity player) {
		return new SalesMailboxScreenHandler(syncId, playerInventory, this, properties);
	}

	// ------------------------------------------------------------------ guardado

	@Override
	protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		super.writeNbt(nbt, registries);
		Inventories.writeNbt(nbt, items, registries);
		if (owner != null) {
			nbt.putUuid("Owner", owner);
		}
		nbt.putLong("LastDay", lastDay);
		nbt.putDouble("Credit", credit);
		nbt.putInt("LastSold", lastSold);
		nbt.putInt("LastCoins", lastCoins);
	}

	@Override
	protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		super.readNbt(nbt, registries);
		items.clear();
		Inventories.readNbt(nbt, items, registries);
		owner = nbt.containsUuid("Owner") ? nbt.getUuid("Owner") : null;
		lastDay = nbt.getLong("LastDay");
		credit = nbt.getDouble("Credit");
		lastSold = nbt.getInt("LastSold");
		lastCoins = nbt.getInt("LastCoins");
	}
}
