package com.evolvedwarfare.climate;

import com.evolvedwarfare.EvolvedWarfare;
import com.evolvedwarfare.config.EwConfig;
import com.evolvedwarfare.network.DrinkPosePayload;
import com.evolvedwarfare.network.DrinkRequestPayload;
import com.evolvedwarfare.network.ThirstPayload;
import com.evolvedwarfare.registry.ModEffects;
import com.evolvedwarfare.registry.ModItems;
import com.mojang.serialization.Codec;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.AbstractFurnaceBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CampfireBlock;
import net.minecraft.block.LeveledCauldronBlock;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageType;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsage;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.potion.Potions;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Difficulty;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeKeys;
import org.jetbrains.annotations.Nullable;

/**
 * La sed: de 0 a 20 (diez gotas, encima del hambre). Se gasta con el tiempo, más con el calor (el aire de alrededor:
 * desierto, verano, Nether) y con lo que cansa (correr, saltar, pelear, picar: el mismo cansancio que el hambre). Con
 * poca sed ({@link #PARCHED} o menos) no se puede correr ni se recupera vida; sin nada, la deshidratación quita vida
 * (como el hambre: según la dificultad).
 *
 * <p>Para beber:</p>
 * <ul>
 * <li><b>Agua del mundo</b>: agachado, con la mano vacía, clic derecho al agua. Se bebe con las manos (un segundo) y
 * puede traer <b>parásitos</b> ({@link ParasitesEffect}).</li>
 * <li><b>Frascos de agua</b> (los de vanilla, sin hervir): también pueden traer parásitos. <b>Hervidos</b> en el horno
 * (o en la fogata) son <b>agua hervida</b>, sin peligro.</li>
 * <li><b>Cantimplora</b>: cuatro tragos; se llena en el agua y se hierve en el horno como los frascos.</li>
 * <li><b>Caldero con agua encima de fuego</b> (fogata encendida, fuego, lava, magma u horno encendido): el agua hierve;
 * se puede beber de ella (agachado, mano vacía) o llenar frascos y cantimploras de agua hervida.</li>
 * <li><b>Lluvia</b>: agachado, mano vacía, mirando al cielo mientras llueve encima: agua limpia, poca cada vez.</li>
 * <li><b>Comida y bebida</b> con agua de verdad: melón, manzanas, bayas, zanahorias, sopas y estofados, leche, zumos...
 * (lo salado o seco, como las algas secas o la carne podrida, da sed).</li>
 * </ul>
 */
public final class Thirst {
	public static final float MAX = 20.0F;
	/** Con esto o menos: no se corre y no se recupera vida. */
	public static final float PARCHED = 6.0F;
	/** Cansancio (el del hambre) que hace falta para perder un punto de sed. */
	private static final float EXHAUSTION_PER_POINT = 8.0F;
	/** Lo que se gasta cada segundo sin hacer nada, con el aire templado (20 puntos en ~37 minutos). */
	private static final float BASE_DRAIN = 0.009F;
	/** Lo que dura beber con las manos (tics). */
	public static final int DRINK_TICKS = 20;
	/** Lo que duran los parásitos (tics) y lo que se alarga si se vuelve a beber agua mala. */
	public static final int PARASITE_TICKS = 1800;

	public static final AttachmentType<Float> LEVEL = AttachmentRegistry.<Float>builder()
			.persistent(Codec.FLOAT)
			.initializer(() -> MAX)
			.buildAndRegister(EvolvedWarfare.id("thirst"));
	public static final AttachmentType<Float> EXHAUSTION = AttachmentRegistry.<Float>builder()
			.persistent(Codec.FLOAT)
			.initializer(() -> 0.0F)
			.buildAndRegister(EvolvedWarfare.id("thirst_exhaustion"));

	public static final RegistryKey<DamageType> DEHYDRATION = RegistryKey.of(RegistryKeys.DAMAGE_TYPE, EvolvedWarfare.id("dehydration"));
	public static final RegistryKey<DamageType> PARASITES = RegistryKey.of(RegistryKeys.DAMAGE_TYPE, EvolvedWarfare.id("parasites"));

	/** De dónde se bebe con las manos. */
	public enum Source {
		/** Un río, un lago... (agua sin hervir). */
		WORLD(3.0F, true, DrinkPosePayload.SCOOP),
		/** La lluvia (limpia). */
		RAIN(2.0F, false, DrinkPosePayload.RAIN),
		/** Un caldero sin fuego debajo (sin hervir). */
		CAULDRON(4.0F, true, DrinkPosePayload.SCOOP),
		/** Un caldero encima del fuego (hervida). */
		BOILED_CAULDRON(4.0F, false, DrinkPosePayload.SCOOP);

		public final float thirst;
		public final boolean risky;
		public final byte pose;

		Source(float thirst, boolean risky, byte pose) {
			this.thirst = thirst;
			this.risky = risky;
			this.pose = pose;
		}
	}

	/** Uno que está bebiendo con las manos. */
	private static final class Drinking {
		final Source source;
		final Vec3d from;
		@Nullable
		final BlockPos at;
		int ticks;

		Drinking(Source source, Vec3d from, @Nullable BlockPos at) {
			this.source = source;
			this.from = from;
			this.at = at;
		}
	}

	private static final Map<UUID, Drinking> DRINKING = new HashMap<>();
	/** La sed que quita (o da, si es negativa) cada cosa de comer o beber de vanilla. */
	private static final Map<Item, Float> FOODS = new IdentityHashMap<>();

	static {
		FOODS.put(Items.MELON_SLICE, 4.0F);
		FOODS.put(Items.APPLE, 2.0F);
		FOODS.put(Items.GOLDEN_APPLE, 3.0F);
		FOODS.put(Items.ENCHANTED_GOLDEN_APPLE, 6.0F);
		FOODS.put(Items.CARROT, 1.0F);
		FOODS.put(Items.GOLDEN_CARROT, 1.0F);
		FOODS.put(Items.BEETROOT, 1.0F);
		FOODS.put(Items.POTATO, 1.0F);
		FOODS.put(Items.SWEET_BERRIES, 1.0F);
		FOODS.put(Items.GLOW_BERRIES, 1.0F);
		FOODS.put(Items.CHORUS_FRUIT, 2.0F);
		FOODS.put(Items.PUMPKIN_PIE, 1.0F);
		FOODS.put(Items.MUSHROOM_STEW, 5.0F);
		FOODS.put(Items.BEETROOT_SOUP, 6.0F);
		FOODS.put(Items.RABBIT_STEW, 5.0F);
		FOODS.put(Items.SUSPICIOUS_STEW, 5.0F);
		FOODS.put(Items.MILK_BUCKET, 6.0F);
		FOODS.put(Items.HONEY_BOTTLE, 1.0F);
		FOODS.put(Items.COD, 1.0F);
		FOODS.put(Items.SALMON, 1.0F);
		FOODS.put(Items.TROPICAL_FISH, 1.0F);
		// lo seco o salado da sed
		FOODS.put(Items.DRIED_KELP, -1.0F);
		FOODS.put(Items.COOKIE, -1.0F);
		FOODS.put(Items.SPIDER_EYE, -1.0F);
		FOODS.put(Items.ROTTEN_FLESH, -2.0F);
		FOODS.put(Items.PUFFERFISH, -3.0F);
	}

	/** Lo que quita de sed una comida del mod (lo llaman los cultivos). */
	public static void food(Item item, float thirst) {
		FOODS.put(item, thirst);
	}

	/** La sed que quita un frasco de agua sin hervir (el de vanilla) y cualquier otra poción. */
	public static final float RAW_BOTTLE = 5.0F;
	public static final float OTHER_POTION = 3.0F;

	private Thirst() {
	}

	public static boolean enabled() {
		return EwConfig.get().thirstEnabled;
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Thirst::tickDrinking);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> DRINKING.clear());
		ServerPlayNetworking.registerGlobalReceiver(DrinkRequestPayload.ID, (payload, context) -> request(context.player(), payload.kind()));
		UseBlockCallback.EVENT.register(Thirst::useCauldron);
	}

	// ------------------------------------------------------------------ el nivel

	public static float get(PlayerEntity player) {
		Float value = player.getAttached(LEVEL);
		return value == null ? MAX : value;
	}

	public static void set(PlayerEntity player, float value) {
		player.setAttached(LEVEL, MathHelper.clamp(value, 0.0F, MAX));
		if (player instanceof ServerPlayerEntity serverPlayer) {
			sync(serverPlayer);
		}
	}

	public static void add(PlayerEntity player, float amount) {
		set(player, get(player) + amount);
	}

	public static boolean parched(PlayerEntity player) {
		return enabled() && get(player) <= PARCHED;
	}

	public static void sync(ServerPlayerEntity player) {
		if (ServerPlayNetworking.canSend(player, ThirstPayload.ID)) {
			ServerPlayNetworking.send(player, new ThirstPayload(get(player), enabled()));
		}
	}

	/** El cansancio del hambre (correr, saltar, pelear...) también da sed. */
	public static void exhaust(PlayerEntity player, float amount) {
		if (!(player instanceof ServerPlayerEntity serverPlayer) || !enabled() || amount <= 0.0F || Climate.exempt(serverPlayer)) {
			return;
		}
		float e = player.getAttachedOrCreate(EXHAUSTION) + amount;
		if (e >= EXHAUSTION_PER_POINT) {
			e -= EXHAUSTION_PER_POINT;
			add(player, -1.0F);
		}
		player.setAttached(EXHAUSTION, e);
	}

	// ------------------------------------------------------------------ cada segundo (desde Climate)

	/**
	 * Un segundo de un jugador: se gasta la sed (más con calor) y, si no le queda, lo nota. {@code air}: la temperatura
	 * del aire donde está (°C).
	 */
	public static void second(ServerWorld world, ServerPlayerEntity player, float air, long second) {
		if (!enabled() || Climate.exempt(player) || !player.isAlive()) {
			if (second % 5 == 0) {
				sync(player);
			}
			return;
		}
		float level = get(player);
		if (world.getDifficulty() == Difficulty.PEACEFUL) {
			level = Math.min(MAX, level + 0.5F);
		} else {
			level -= drain(air, player.isSprinting(), Climate.body(player));
		}
		level = MathHelper.clamp(level, 0.0F, MAX);
		player.setAttached(LEVEL, level);
		if (level <= 0.0F) {
			// deshidratación: como el hambre, según la dificultad
			if (second % 4 == 0) {
				Difficulty d = world.getDifficulty();
				float health = player.getHealth();
				if (d == Difficulty.HARD || (d == Difficulty.NORMAL && health > 1.0F) || (d == Difficulty.EASY && health > 10.0F)) {
					player.damage(source(world, DEHYDRATION), 1.0F);
				}
			}
			player.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 40, 0, true, false, true));
		}
		if (level <= PARCHED && player.isSprinting()) {
			player.setSprinting(false);
		}
		sync(player);
	}

	/** Lo que se gasta en un segundo. */
	public static float drain(float air, boolean sprinting, float body) {
		float rate = BASE_DRAIN;
		if (air > 25.0F) {
			rate *= 1.0F + (Math.min(air, 50.0F) - 25.0F) / 12.0F;
		}
		if (body > 38.0F) {
			rate += 0.01F;
		}
		if (sprinting) {
			rate += 0.012F;
		}
		return rate;
	}

	// ------------------------------------------------------------------ comer y beber cosas

	/** La sed que quita una cosa al comerla o beberla (0 si nada). */
	public static float valueOf(ItemStack stack) {
		if (stack.getItem() instanceof DrinkItem drink) {
			return drink.thirst();
		}
		if (stack.getItem() instanceof CanteenItem canteen) {
			return canteen.sip();
		}
		if (stack.isOf(Items.POTION)) {
			PotionContentsComponent potion = stack.get(DataComponentTypes.POTION_CONTENTS);
			return potion != null && potion.matches(Potions.WATER) ? RAW_BOTTLE : OTHER_POTION;
		}
		return FOODS.getOrDefault(stack.getItem(), 0.0F);
	}

	/** ¿Puede traer parásitos? */
	public static boolean risky(ItemStack stack) {
		if (stack.getItem() instanceof CanteenItem canteen) {
			return canteen.risky();
		}
		if (stack.isOf(Items.POTION)) {
			PotionContentsComponent potion = stack.get(DataComponentTypes.POTION_CONTENTS);
			return potion != null && potion.matches(Potions.WATER);
		}
		return false;
	}

	/** Al terminar de comer o beber algo de vanilla (lo del mod se hace en el propio objeto). */
	public static void onConsume(LivingEntity entity, ItemStack stack) {
		if (!(entity instanceof ServerPlayerEntity player) || stack.isEmpty() || !enabled()
				|| stack.getItem() instanceof DrinkItem || stack.getItem() instanceof CanteenItem) {
			return;
		}
		float value = valueOf(stack);
		if (value != 0.0F) {
			add(player, value);
		}
		if (risky(stack)) {
			maybeInfect(player, player.getRandom().nextFloat(), 1.0F);
		}
	}

	/** Bebe agua (de un objeto del mod): la sed, el frescor y, si no está hervida, la suerte con los parásitos. */
	public static void drinkWater(ServerPlayerEntity player, float thirst, boolean risky) {
		if (!enabled()) {
			return;
		}
		add(player, thirst);
		Climate.cool(player, 1.0F);
		if (risky) {
			maybeInfect(player, player.getRandom().nextFloat(), 1.0F);
		}
	}

	// ------------------------------------------------------------------ los parásitos

	/** La probabilidad de coger parásitos con agua sin hervir (0 a 1). */
	public static float parasiteChance() {
		return MathHelper.clamp(EwConfig.get().parasiteChance / 100.0F, 0.0F, 1.0F);
	}

	/** Con {@code roll} (0 a 1) menor que la probabilidad (por {@code factor}), le dan parásitos. Devuelve si le han dado. */
	public static boolean maybeInfect(ServerPlayerEntity player, float roll, float factor) {
		if (roll >= parasiteChance() * factor) {
			return false;
		}
		infect(player);
		return true;
	}

	public static void infect(LivingEntity entity) {
		StatusEffectInstance current = entity.getStatusEffect(ModEffects.PARASITES);
		int duration = current != null ? Math.min(current.getDuration() + PARASITE_TICKS / 3, PARASITE_TICKS * 2) : PARASITE_TICKS;
		entity.addStatusEffect(new StatusEffectInstance(ModEffects.PARASITES, duration, 0));
		if (entity instanceof ServerPlayerEntity player && current == null) {
			player.sendMessage(Text.translatable("message.evolvedwarfare.thirst.parasites").formatted(Formatting.DARK_GREEN), true);
		}
	}

	// ------------------------------------------------------------------ beber con las manos

	/** El cliente pide beber: del agua que mira ({@link DrinkRequestPayload#WATER}) o de la lluvia. */
	public static void request(ServerPlayerEntity player, byte kind) {
		if (!player.isSneaking() || !player.getMainHandStack().isEmpty() || !player.isAlive() || player.isSpectator()) {
			return;
		}
		if (kind == DrinkRequestPayload.RAIN) {
			BlockPos head = BlockPos.ofFloored(player.getEyePos());
			if (player.getPitch() < -40.0F && player.getWorld().hasRain(head)) {
				startDrink(player, Source.RAIN, null);
			}
			return;
		}
		BlockPos water = waterInSight(player);
		if (water != null) {
			startDrink(player, Source.WORLD, water);
		}
	}

	/** El agua que mira, a su alcance (o null). */
	@Nullable
	public static BlockPos waterInSight(PlayerEntity player) {
		HitResult hit = player.raycast(player.getBlockInteractionRange(), 1.0F, true);
		if (hit instanceof BlockHitResult block && hit.getType() == HitResult.Type.BLOCK
				&& player.getWorld().getFluidState(block.getBlockPos()).isIn(FluidTags.WATER)) {
			return block.getBlockPos();
		}
		return null;
	}

	/** Empieza a beber con las manos (un segundo). Devuelve false si no puede (ya bebe, o no tiene sed). */
	public static boolean startDrink(ServerPlayerEntity player, Source source, @Nullable BlockPos at) {
		if (DRINKING.containsKey(player.getUuid())) {
			return false;
		}
		if (enabled() && get(player) >= MAX - 0.5F) {
			player.sendMessage(Text.translatable("message.evolvedwarfare.thirst.full"), true);
			return false;
		}
		if (source == Source.WORLD && at != null) {
			// agua realista: un trago con las manos se lleva una capa
			com.evolvedwarfare.fluid.FiniteFluids.drain(player.getWorld(), at, net.minecraft.fluid.Fluids.WATER, 1);
		}
		DRINKING.put(player.getUuid(), new Drinking(source, player.getPos(), at));
		DrinkPosePayload pose = new DrinkPosePayload(player.getId(), source.pose);
		send(player, pose);
		for (ServerPlayerEntity other : PlayerLookup.tracking(player)) {
			if (other != player) {
				send(other, pose);
			}
		}
		ServerWorld world = player.getServerWorld();
		if (at != null) {
			world.playSound(null, at, SoundEvents.ENTITY_GENERIC_SPLASH, SoundCategory.PLAYERS, 0.25F, 1.4F);
			world.spawnParticles(ParticleTypes.SPLASH, at.getX() + 0.5, at.getY() + 0.95, at.getZ() + 0.5, 10, 0.2, 0.05, 0.2, 0.1);
		}
		return true;
	}

	public static boolean drinking(PlayerEntity player) {
		return DRINKING.containsKey(player.getUuid());
	}

	private static void send(ServerPlayerEntity to, DrinkPosePayload payload) {
		if (ServerPlayNetworking.canSend(to, DrinkPosePayload.ID)) {
			ServerPlayNetworking.send(to, payload);
		}
	}

	private static void tickDrinking(net.minecraft.server.MinecraftServer server) {
		if (DRINKING.isEmpty()) {
			return;
		}
		Iterator<Map.Entry<UUID, Drinking>> it = DRINKING.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<UUID, Drinking> entry = it.next();
			ServerPlayerEntity player = server.getPlayerManager().getPlayer(entry.getKey());
			Drinking d = entry.getValue();
			if (player == null || !player.isAlive() || player.getPos().squaredDistanceTo(d.from) > 4.0) {
				it.remove();
				continue;
			}
			d.ticks++;
			ServerWorld world = player.getServerWorld();
			if (d.ticks == 11 || d.ticks == 16) {
				world.playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.ENTITY_GENERIC_DRINK, SoundCategory.PLAYERS,
						0.5F, 0.9F + player.getRandom().nextFloat() * 0.2F);
			}
			if (d.ticks == 13) {
				// lo que se escurre entre los dedos
				Vec3d mouth = player.getEyePos().add(player.getRotationVector().multiply(0.3)).subtract(0.0, 0.25, 0.0);
				world.spawnParticles(ParticleTypes.FALLING_WATER, mouth.x, mouth.y, mouth.z, 6, 0.12, 0.05, 0.12, 0.0);
			}
			if (d.ticks >= DRINK_TICKS) {
				it.remove();
				finish(player, d.source, d.at, player.getRandom().nextFloat());
			}
		}
	}

	/** Termina de beber: la sed, el frescor y los parásitos (si {@code roll}, de 0 a 1, cae por debajo de la probabilidad). */
	public static void finish(ServerPlayerEntity player, Source source, @Nullable BlockPos at, float roll) {
		if (!enabled()) {
			return;
		}
		add(player, source.thirst);
		Climate.cool(player, 0.6F);
		if (source.risky) {
			// el agua quieta de los pantanos es peor
			float factor = 1.0F;
			if (at != null && (player.getWorld().getBiome(at).matchesKey(BiomeKeys.SWAMP)
					|| player.getWorld().getBiome(at).matchesKey(BiomeKeys.MANGROVE_SWAMP))) {
				factor = 1.6F;
			}
			maybeInfect(player, roll, factor);
		}
	}

	// ------------------------------------------------------------------ el caldero

	/** ¿Tiene fuego debajo (el agua hierve)? */
	public static boolean heated(World world, BlockPos cauldron) {
		BlockState below = world.getBlockState(cauldron.down());
		if (CampfireBlock.isLitCampfire(below) || below.isIn(BlockTags.FIRE) || below.isOf(Blocks.MAGMA_BLOCK)
				|| below.getFluidState().isIn(FluidTags.LAVA)) {
			return true;
		}
		return below.getBlock() instanceof AbstractFurnaceBlock && below.contains(Properties.LIT) && below.get(Properties.LIT);
	}

	private static ActionResult useCauldron(PlayerEntity player, World world, Hand hand, BlockHitResult hit) {
		BlockPos pos = hit.getBlockPos();
		BlockState state = world.getBlockState(pos);
		if (!state.isOf(Blocks.WATER_CAULDRON) || player.isSpectator()) {
			return ActionResult.PASS;
		}
		boolean boiling = heated(world, pos);
		ItemStack stack = player.getStackInHand(hand);
		if (hand == Hand.MAIN_HAND && stack.isEmpty() && player.isSneaking()) {
			if (player instanceof ServerPlayerEntity serverPlayer
					&& startDrink(serverPlayer, boiling ? Source.BOILED_CAULDRON : Source.CAULDRON, pos)) {
				LeveledCauldronBlock.decrementFluidLevel(state, world, pos);
			}
			return ActionResult.SUCCESS;
		}
		ItemStack filled;
		if (boiling && stack.isOf(Items.GLASS_BOTTLE)) {
			filled = new ItemStack(ModItems.BOILED_WATER);
		} else if (stack.getItem() instanceof CanteenItem) {
			filled = new ItemStack(boiling ? ModItems.BOILED_CANTEEN : ModItems.WATER_CANTEEN);
		} else {
			return ActionResult.PASS;
		}
		if (!world.isClient) {
			player.setStackInHand(hand, ItemUsage.exchangeStack(stack, player, filled));
			LeveledCauldronBlock.decrementFluidLevel(state, world, pos);
			world.playSound(null, pos, SoundEvents.ITEM_BOTTLE_FILL, SoundCategory.BLOCKS, 1.0F, 1.0F);
		}
		return ActionResult.success(world.isClient);
	}

	public static DamageSource source(ServerWorld world, RegistryKey<DamageType> key) {
		return new DamageSource(world.getRegistryManager().get(RegistryKeys.DAMAGE_TYPE).entryOf(key));
	}
}
