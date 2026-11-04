package com.evolvedwarfare.climate;

import com.evolvedwarfare.EvolvedWarfare;
import com.evolvedwarfare.config.EwConfig;
import com.evolvedwarfare.network.ClimatePayload;
import com.evolvedwarfare.world.Hordes;
import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CampfireBlock;
import net.minecraft.block.SnowBlock;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageType;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ArmorMaterials;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.potion.Potions;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.GameMode;
import net.minecraft.world.GameRules;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import org.jetbrains.annotations.Nullable;

/**
 * El clima (fase 14): la temperatura del aire en cada sitio y la del cuerpo de cada jugador.
 *
 * <p><b>El aire</b> sale del bioma, la estación ({@link Season}), la hora (de noche hace frío; en el desierto, mucho), la
 * altura, la lluvia y la nieve, si estás a cubierto o bajo tierra (más templado) y lo que tengas cerca: hogueras, fuego,
 * lava, magma, hornos encendidos y antorchas dan calor; el hielo y la nieve, frío. Llevar una antorcha o un farol en la
 * mano también calienta. En el Nether siempre hace un calor de muerte; en el End, frío.</p>
 *
 * <p><b>Lo que notas</b>: con frío, la ropa de piel abriga y el cuero un poco; con calor, la de lino refresca y las
 * armaduras de metal al sol queman. Mojado (en el agua o bajo la lluvia, y un rato después) se pasa más frío.</p>
 *
 * <p><b>El cuerpo</b> está a 37 °C. Con lo que notas por debajo de 10 °C se va enfriando y por encima de 30 °C se va
 * calentando; en medio vuelve poco a poco a 37. Por debajo de 35 °C, <b>hipotermia</b>: lentitud y pierdes vida (más
 * deprisa por debajo de 33). Por encima de 39 °C, <b>hipertermia</b>: debilidad, hambre y pierdes vida (más deprisa por
 * encima de 40,5). Correr calienta, arder calienta mucho; las sopas y estofados calientan, el agua y el melón refrescan;
 * la poción de resistencia al fuego evita el golpe de calor.</p>
 */
public final class Climate {
	public static final float NORMAL = 37.0F;
	public static final float HYPOTHERMIA = 35.0F;
	public static final float SEVERE_COLD = 33.0F;
	public static final float HYPERTHERMIA = 39.0F;
	public static final float SEVERE_HEAT = 40.5F;

	/** La temperatura del cuerpo de cada jugador (se guarda con él; al morir vuelve a 37). */
	public static final AttachmentType<Float> BODY = AttachmentRegistry.<Float>builder()
			.persistent(Codec.FLOAT)
			.initializer(() -> NORMAL)
			.buildAndRegister(EvolvedWarfare.id("body_temperature"));
	/** Segundos que le quedan de estar mojado. */
	public static final AttachmentType<Integer> WET = AttachmentRegistry.<Integer>builder()
			.initializer(() -> 0)
			.buildAndRegister(EvolvedWarfare.id("wet"));

	public static final RegistryKey<DamageType> HYPOTHERMIA_DAMAGE = RegistryKey.of(RegistryKeys.DAMAGE_TYPE, EvolvedWarfare.id("hypothermia"));
	public static final RegistryKey<DamageType> HEATSTROKE_DAMAGE = RegistryKey.of(RegistryKeys.DAMAGE_TYPE, EvolvedWarfare.id("heatstroke"));

	/**
	 * Lo que la estación cambia la temperatura de los biomas (la que decide si nieva y si se hiela el agua). El servidor
	 * lo pone cada segundo y el cliente al recibir el clima; en un mundo de un jugador es el mismo número.
	 */
	public static volatile float biomeOffset;
	/** La estación que pinta la hierba y las hojas (null: ninguna, sin cambiar los colores). */
	@Nullable
	public static volatile Season tintSeason;

	private static long lastWeatherDay = -1L;

	private Climate() {
	}

	public static boolean enabled() {
		return EwConfig.get().temperatureEnabled;
	}

	public static boolean seasonsEnabled() {
		return EwConfig.get().seasonsEnabled;
	}

	/** La estación de ahora en el mundo (primavera si están desactivadas). */
	public static Season season(World world) {
		return seasonsEnabled() ? Season.at(world.getTimeOfDay()) : Season.SPRING;
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Climate::tick);
	}

	// ------------------------------------------------------------------ el aire

	/** La temperatura del aire (°C) según la del bioma (sin estación): llanuras ~14, desierto ~34, nieve ~-8. */
	public static float airFromBiome(float biomeTemperature) {
		return -8.0F + Math.min(biomeTemperature, 1.5F) * 28.0F;
	}

	/** Lo que sube o baja con la hora: +4 a mediodía, −6 a medianoche (en sitios secos y cálidos, −14). */
	public static float dayCurve(long timeOfDay, boolean desert) {
		double t = Math.floorMod(timeOfDay, Hordes.DAY);
		double c = Math.cos((t - 6000.0) / Hordes.DAY * Math.PI * 2.0);
		return (float) (c > 0 ? 4.0 * c : (desert ? 14.0 : 6.0) * c);
	}

	/** La temperatura del aire en ese sitio (°C), con lo que haya cerca y, si se da, lo que lleva en la mano. */
	public static float air(ServerWorld world, BlockPos pos, @Nullable PlayerEntity holder) {
		float heat = nearby(world, pos);
		if (holder != null && (holdsWarm(holder.getMainHandStack()) || holdsWarm(holder.getOffHandStack()))) {
			heat += 2.0F;
		}
		if (world.getRegistryKey() == World.NETHER) {
			return 44.0F + heat;
		}
		if (world.getRegistryKey() == World.END) {
			return -12.0F + heat;
		}
		Biome biome = world.getBiome(pos).value();
		float raw = biome.getTemperature();
		Season season = season(world);
		float a = airFromBiome(raw) + season.airOffset;
		if (!world.getDimension().hasFixedTime()) {
			a += dayCurve(world.getTimeOfDay(), raw >= 1.5F && !biome.hasPrecipitation());
		}
		if (pos.getY() > 90) {
			a -= (pos.getY() - 90) * 0.08F;
		}
		boolean sky = world.isSkyVisible(pos.up());
		if (!sky) {
			// bajo tierra se está fresco todo el año; dentro de una casa, más templado
			a = pos.getY() < 50 ? MathHelper.lerp(0.8F, a, 12.0F) : MathHelper.lerp(0.4F, a, 16.0F);
		} else if (world.isRaining() && biome.hasPrecipitation()) {
			a -= biome.getPrecipitation(pos) == Biome.Precipitation.SNOW ? 5.0F : 3.0F;
		}
		return a + heat;
	}

	private static boolean holdsWarm(ItemStack stack) {
		return stack.isOf(Items.TORCH) || stack.isOf(Items.LANTERN) || stack.isOf(Items.SOUL_TORCH) || stack.isOf(Items.SOUL_LANTERN)
				|| stack.isOf(Items.LAVA_BUCKET) || stack.isOf(Items.MAGMA_BLOCK);
	}

	/** Calor (o frío) de lo que hay cerca: hasta 5 bloques, más cuanto más cerca. */
	public static float nearby(World world, BlockPos center) {
		float heat = 0.0F;
		float cold = 0.0F;
		BlockPos.Mutable p = new BlockPos.Mutable();
		for (int dx = -5; dx <= 5; dx++) {
			for (int dy = -2; dy <= 3; dy++) {
				for (int dz = -5; dz <= 5; dz++) {
					p.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
					BlockState state = world.getBlockState(p);
					if (state.isAir()) {
						continue;
					}
					float value = warmth(state);
					if (value == 0.0F) {
						continue;
					}
					double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
					float fall = (float) Math.max(0.0, 1.0 - d / 6.0);
					if (value > 0) {
						heat += value * fall;
					} else {
						cold += value * fall;
					}
				}
			}
		}
		return Math.min(heat, 25.0F) + Math.max(cold, -8.0F);
	}

	/** Lo que calienta (o enfría) un bloque. */
	public static float warmth(BlockState state) {
		Block b = state.getBlock();
		if (b instanceof CampfireBlock) {
			return state.get(CampfireBlock.LIT) ? 9.0F : 0.0F;
		}
		if (b == Blocks.FIRE || b == Blocks.SOUL_FIRE) {
			return 6.0F;
		}
		if (b == Blocks.LAVA) {
			return 10.0F;
		}
		if (b == Blocks.MAGMA_BLOCK) {
			return 3.0F;
		}
		if ((b == Blocks.FURNACE || b == Blocks.SMOKER || b == Blocks.BLAST_FURNACE) && state.get(Properties.LIT)) {
			return 4.0F;
		}
		if (b == Blocks.TORCH || b == Blocks.WALL_TORCH || b == Blocks.LANTERN || b == Blocks.SOUL_LANTERN || b == Blocks.SOUL_TORCH
				|| b == Blocks.SOUL_WALL_TORCH) {
			return 1.0F;
		}
		if (b == Blocks.ICE || b == Blocks.PACKED_ICE || b == Blocks.BLUE_ICE) {
			return -1.5F;
		}
		if (b == Blocks.SNOW_BLOCK || b == Blocks.POWDER_SNOW) {
			return -1.0F;
		}
		return 0.0F;
	}

	// ------------------------------------------------------------------ la ropa

	/** Grados de abrigo (piel y cuero) y de frescor (lino) que lleva puestos, y piezas de metal. */
	public record Wear(float warm, float cool, int metal) {
	}

	public static Wear wear(LivingEntity entity) {
		float warm = 0.0F;
		float cool = 0.0F;
		int metal = 0;
		for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
			ItemStack stack = entity.getEquippedStack(slot);
			if (stack.getItem() instanceof ClimateWearItem item) {
				if (item.kind() == ClimateWearItem.Kind.FUR) {
					warm += item.degrees();
				} else {
					cool += item.degrees();
				}
			} else if (stack.getItem() instanceof ArmorItem armor) {
				if (armor.getMaterial().matchesKey(ArmorMaterials.LEATHER.getKey().orElseThrow())) {
					warm += 1.0F;
				} else if (!armor.getMaterial().matchesKey(ArmorMaterials.TURTLE.getKey().orElseThrow())) {
					metal++;
				}
			}
		}
		return new Wear(warm, cool, metal);
	}

	/**
	 * La temperatura que notas: con frío, la piel abriga (y el lino un poco al revés); con calor, el lino refresca, la piel
	 * da calor de más y el metal al sol quema. Mojado, en frío, cinco grados menos.
	 */
	public static float felt(float air, Wear wear, boolean sun, boolean wet) {
		float e = air;
		if (air < 18.0F) {
			e = Math.min(20.0F, air + wear.warm() - wear.cool() * 0.25F);
			if (wet) {
				e -= 5.0F;
			}
		} else if (air > 26.0F) {
			e = air - wear.cool() + wear.warm() * 0.5F + (sun ? wear.metal() : 0);
			if (wet) {
				e -= 3.0F;
			}
		}
		return e;
	}

	/** Un paso del cuerpo: lo que se enfría o se calienta en esos segundos con lo que nota. */
	public static float step(float body, float felt, float seconds) {
		if (felt < 10.0F) {
			body -= (10.0F - felt) * 0.002F * seconds;
		} else if (felt > 30.0F) {
			body += (felt - 30.0F) * 0.0025F * seconds;
		} else {
			float back = 0.05F * seconds;
			body = body < NORMAL ? Math.min(NORMAL, body + back) : Math.max(NORMAL, body - back);
		}
		return MathHelper.clamp(body, 30.0F, 44.0F);
	}

	public static float body(PlayerEntity player) {
		Float value = player.getAttached(BODY);
		return value == null ? NORMAL : value;
	}

	public static void setBody(PlayerEntity player, float value) {
		player.setAttached(BODY, MathHelper.clamp(value, 30.0F, 44.0F));
	}

	private static boolean exempt(ServerPlayerEntity player) {
		GameMode mode = player.interactionManager.getGameMode();
		return mode == GameMode.CREATIVE || mode == GameMode.SPECTATOR;
	}

	// ------------------------------------------------------------------ cada segundo

	private static void tick(MinecraftServer server) {
		ServerWorld overworld = server.getOverworld();
		if (server.getTicks() % 20 != 0) {
			if (server.getTicks() % 10 == 5) {
				melt(overworld);
			}
			return;
		}
		Season season = season(overworld);
		biomeOffset = seasonsEnabled() ? season.biomeOffset : 0.0F;
		weather(overworld, season);
		long second = server.getTicks() / 20;
		int length = Season.length();
		long day = Hordes.day(overworld.getTimeOfDay());
		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			if (!(player.getWorld() instanceof ServerWorld world)) {
				continue;
			}
			float air = air(world, player.getBlockPos(), player);
			float body = body(player);
			if (!enabled() || exempt(player) || !player.isAlive()) {
				body = NORMAL;
			} else {
				body = living(world, player, air, body, second);
			}
			setBody(player, body);
			ClimatePayload.send(player, new ClimatePayload(body, air, season.ordinal(), Season.dayOf(day, length), length, enabled(),
					seasonsEnabled()));
		}
	}

	/** El cuerpo de un jugador vivo en supervivencia: se enfría o se calienta y, si se pasa, lo nota. */
	private static float living(ServerWorld world, ServerPlayerEntity player, float air, float body, long second) {
		int wet = player.getAttachedOrCreate(WET);
		if (player.isTouchingWaterOrRain() || player.isSubmergedInWater()) {
			wet = 40;
		} else if (wet > 0) {
			wet = Math.max(0, wet - (air > 25.0F ? 3 : 1));
		}
		player.setAttached(WET, wet);
		Wear wear = wear(player);
		boolean sun = world.isDay() && world.isSkyVisible(player.getBlockPos().up()) && !world.isRaining();
		float felt = felt(air, wear, sun, wet > 0);
		body = step(body, felt, 1.0F);
		if (player.isSprinting()) {
			body += 0.012F;
		}
		if (player.isOnFire()) {
			body += 0.3F;
		}
		if (player.inPowderSnow) {
			body -= 0.15F;
		}
		boolean fireResistant = player.hasStatusEffect(StatusEffects.FIRE_RESISTANCE);
		if (fireResistant) {
			body = Math.min(body, 38.5F);
		}
		body = MathHelper.clamp(body, 30.0F, 44.0F);
		// lo que pasa
		if (body < HYPOTHERMIA) {
			boolean severe = body < SEVERE_COLD;
			player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 40, severe ? 1 : 0, true, false, true));
			if (severe) {
				player.addStatusEffect(new StatusEffectInstance(StatusEffects.MINING_FATIGUE, 40, 0, true, false, true));
			}
			// la escarcha en la pantalla (sin el daño de la nieve polvo)
			player.setFrozenTicks(Math.min(player.getMinFreezeDamageTicks() - 1, 60 + (int) ((HYPOTHERMIA - body) * 30)));
			if (severe || second % 3 == 0) {
				player.damage(source(world, HYPOTHERMIA_DAMAGE), 1.0F);
			}
		} else if (body > HYPERTHERMIA && !fireResistant) {
			boolean severe = body > SEVERE_HEAT;
			player.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 40, 0, true, false, true));
			if (severe) {
				player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 40, 0, true, false, true));
			}
			player.addExhaustion(severe ? 0.4F : 0.15F);
			if (severe || second % 3 == 0) {
				player.damage(source(world, HEATSTROKE_DAMAGE), 1.0F);
			}
		}
		return body;
	}

	private static DamageSource source(ServerWorld world, RegistryKey<DamageType> key) {
		return new DamageSource(world.getRegistryManager().get(RegistryKeys.DAMAGE_TYPE).entryOf(key));
	}

	/** Al comer o beber: las sopas y estofados calientan; el agua, el melón y las bayas refrescan. */
	public static void onConsume(LivingEntity entity, ItemStack stack) {
		if (!(entity instanceof ServerPlayerEntity player) || stack.isEmpty() || !enabled()) {
			return;
		}
		float body = body(player);
		if (stack.isOf(Items.MUSHROOM_STEW) || stack.isOf(Items.RABBIT_STEW) || stack.isOf(Items.BEETROOT_SOUP) || stack.isOf(Items.SUSPICIOUS_STEW)) {
			setBody(player, Math.max(body, Math.min(38.0F, body + 2.0F)));
		} else if (stack.isOf(Items.HONEY_BOTTLE) || stack.isOf(Items.BAKED_POTATO)) {
			setBody(player, Math.max(body, Math.min(37.8F, body + 0.6F)));
		} else if (stack.isOf(Items.MELON_SLICE) || stack.isOf(Items.SWEET_BERRIES) || stack.isOf(Items.GLOW_BERRIES)) {
			setBody(player, Math.min(body, Math.max(36.2F, body - 0.8F)));
		} else if (stack.isOf(Items.POTION)) {
			PotionContentsComponent potion = stack.get(DataComponentTypes.POTION_CONTENTS);
			if (potion != null && potion.matches(Potions.WATER)) {
				setBody(player, Math.min(body, Math.max(36.0F, body - 1.5F)));
			}
		}
	}

	// ------------------------------------------------------------------ el mundo

	/** Al empezar cada día, el tiempo que hace según la estación (si el ciclo del tiempo está activado). */
	private static void weather(ServerWorld world, Season season) {
		long day = Hordes.day(world.getTimeOfDay());
		if (day == lastWeatherDay) {
			return;
		}
		boolean first = lastWeatherDay < 0;
		Season before = first ? season : Season.of(lastWeatherDay, Season.length());
		lastWeatherDay = day;
		if (!first && seasonsEnabled() && before != season) {
			world.getServer().getPlayerManager().broadcast(net.minecraft.text.Text.translatable("message.evolvedwarfare.season.start." + season.id())
					.formatted(net.minecraft.util.Formatting.YELLOW), false);
		}
		if (first || !seasonsEnabled() || !world.getGameRules().getBoolean(GameRules.DO_WEATHER_CYCLE)) {
			return;
		}
		Random r = world.getRandom();
		int length = 6000 + r.nextInt(12000);
		if (r.nextFloat() < season.rainChance) {
			world.setWeather(0, length, true, r.nextFloat() < season.thunderChance / Math.max(0.01F, season.rainChance));
		} else {
			world.setWeather(length, 0, false, false);
		}
	}

	/** Fuera del invierno, la nieve y el hielo de los biomas que no son fríos se van derritiendo cerca de los jugadores. */
	private static void melt(ServerWorld world) {
		if (!seasonsEnabled()) {
			return;
		}
		Season season = season(world);
		if (season == Season.WINTER) {
			return;
		}
		int tries = season == Season.SUMMER ? 6 : 3;
		Random r = world.getRandom();
		for (ServerPlayerEntity player : world.getPlayers()) {
			for (int i = 0; i < tries; i++) {
				int x = player.getBlockX() + r.nextInt(81) - 40;
				int z = player.getBlockZ() + r.nextInt(81) - 40;
				if (!world.isChunkLoaded(x >> 4, z >> 4)) {
					continue;
				}
				BlockPos top = world.getTopPosition(Heightmap.Type.MOTION_BLOCKING, new BlockPos(x, 0, z));
				if (world.getBiome(top).value().getTemperature() < 0.15F) {
					continue;
				}
				BlockState state = world.getBlockState(top);
				if (state.isOf(Blocks.SNOW)) {
					int layers = state.get(SnowBlock.LAYERS);
					world.setBlockState(top, layers > 1 ? state.with(SnowBlock.LAYERS, layers - 1) : Blocks.AIR.getDefaultState());
				} else {
					BlockState below = world.getBlockState(top.down());
					if (below.isOf(Blocks.ICE)) {
						world.setBlockState(top.down(), Blocks.WATER.getDefaultState());
					} else if (below.isOf(Blocks.SNOW)) {
						world.setBlockState(top.down(), Blocks.AIR.getDefaultState());
					}
				}
			}
		}
	}

	/** En invierno, los cultivos al raso casi no crecen (los de dentro de un invernadero o bajo techo, sí). */
	public static boolean frozenCrop(ServerWorld world, BlockPos pos, Random random) {
		return seasonsEnabled() && world.getRegistryKey() == World.OVERWORLD && season(world) == Season.WINTER && world.isSkyVisible(pos)
				&& random.nextFloat() < 0.8F;
	}

	// ------------------------------------------------------------------ colores (cliente)

	/** Mezcla dos colores RGB. */
	public static int blend(int a, int b, float t) {
		int r = (int) MathHelper.lerp(t, (a >> 16) & 255, (b >> 16) & 255);
		int g = (int) MathHelper.lerp(t, (a >> 8) & 255, (b >> 8) & 255);
		int bl = (int) MathHelper.lerp(t, a & 255, b & 255);
		return (a & 0xFF000000) | (r << 16) | (g << 8) | bl;
	}

	public static int tintFoliage(int color) {
		Season s = tintSeason;
		if (s == null) {
			return color;
		}
		return switch (s) {
			case AUTUMN -> blend(color, s.tint, 0.65F);
			case WINTER -> blend(color, s.tint, 0.45F);
			case SUMMER -> blend(color, s.tint, 0.12F);
			default -> color;
		};
	}

	public static int tintGrass(int color) {
		Season s = tintSeason;
		if (s == null) {
			return color;
		}
		return switch (s) {
			case AUTUMN -> blend(color, 0xB8A04A, 0.35F);
			case WINTER -> blend(color, s.tint, 0.5F);
			case SUMMER -> blend(color, s.tint, 0.2F);
			default -> blend(color, s.tint, 0.1F);
		};
	}

	/** De Celsius a Fahrenheit. */
	public static float fahrenheit(float celsius) {
		return celsius * 9.0F / 5.0F + 32.0F;
	}
}
