package com.evolvedwarfare.night;

import com.evolvedwarfare.EvolvedWarfare;
import com.evolvedwarfare.config.EwConfig;
import com.evolvedwarfare.mixin.MobEntityGoalsAccessor;
import com.evolvedwarfare.registry.ModEffects;
import com.evolvedwarfare.world.Hordes;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageType;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.mob.EndermanEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.entity.mob.ZombifiedPiglinEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.BiomeTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Difficulty;
import net.minecraft.world.GameRules;
import net.minecraft.world.Heightmap;
import net.minecraft.world.PersistentState;
import net.minecraft.world.World;

/**
 * La noche más dura:
 * <ul>
 * <li>De noche, los monstruos ven a los jugadores <b>el doble de lejos</b>.</li>
 * <li>Los <b>endermans</b> atacan de noche en cuanto te ven (sin que les mires).</li>
 * <li>Los <b>zombis</b> se abren camino ({@link ZombieSiegeGoal}): rompen bloques (hasta la obsidiana, con su tiempo),
 * excavan hacia abajo o hacia arriba, y saltan huecos de 1 o 2 bloques ({@link GapJumpGoal}).</li>
 * <li>Su mordisco puede <b>infectar</b> ({@link InfectionEffect}).</li>
 * <li><b>Noches de zombis</b>: cada {@code zombieHordeEveryDays} días (2) el contador de días se pone verde y, al
 * anochecer, una horda de al menos 15 zombis sale a por cada jugador (o grupo de jugadores), sabiendo dónde está.</li>
 * </ul>
 */
public final class NightThreat {
	public static final String HORDE_TAG = "evolvedwarfare.zombie_horde";
	public static final RegistryKey<DamageType> INFECTION = RegistryKey.of(RegistryKeys.DAMAGE_TYPE, EvolvedWarfare.id("infection"));
	private static final Identifier NIGHT_SIGHT = EvolvedWarfare.id("night_sight");
	private static final Identifier HORDE_SIGHT = EvolvedWarfare.id("horde_sight");
	/** Al anochecer (las 19:00). */
	public static final int NIGHTFALL = 13000;
	private static final int LAST_START = 21000;
	private static final int GROUP = 48;
	/** Frases que avisan de que llega la horda (una al azar cada noche de zombis). */
	public static final int PHRASES = 36;

	private NightThreat() {
	}

	public static boolean hostileNights() {
		return EwConfig.get().nightHostility;
	}

	public static boolean breakBlocks() {
		return EwConfig.get().zombieBreakBlocks;
	}

	public static DamageSource infectionSource(ServerWorld world) {
		return new DamageSource(world.getRegistryManager().get(RegistryKeys.DAMAGE_TYPE).entryOf(INFECTION));
	}

	public static void register() {
		ServerEntityEvents.ENTITY_LOAD.register(NightThreat::onLoad);
		ServerTickEvents.END_WORLD_TICK.register(NightThreat::tick);
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			if (entity instanceof ServerPlayerEntity player && amount > 0.0F && source.getAttacker() instanceof ZombieEntity
					&& !player.isCreative() && !player.isSpectator()) {
				maybeInfect(player, player.getRandom());
			}
			return true;
		});
	}

	// ------------------------------------------------------------------ las criaturas

	private static void onLoad(Entity entity, ServerWorld world) {
		if (!(entity instanceof HostileEntity mob)) {
			return;
		}
		MobEntityGoalsAccessor ai = (MobEntityGoalsAccessor) mob;
		if (mob instanceof EndermanEntity) {
			ai.evolvedwarfare$targets().add(1, new ActiveTargetGoal<>(mob, PlayerEntity.class, 2, true, false,
					target -> hostileNights() && target.getWorld().isNight() && target.getWorld().getDimension().hasSkyLight()));
		}
		if (mob instanceof ZombieEntity && !(mob instanceof ZombifiedPiglinEntity)) {
			ai.evolvedwarfare$goals().add(1, new ZombieSiegeGoal(mob));
			ai.evolvedwarfare$goals().add(1, new GapJumpGoal(mob));
			if (mob.getCommandTags().contains(HORDE_TAG)) {
				// la horda sabe dónde estás: no le hace falta verte
				ai.evolvedwarfare$targets().add(1, new ActiveTargetGoal<>(mob, PlayerEntity.class, 1, false, false, null));
				modifier(mob, HORDE_SIGHT, 64.0, EntityAttributeModifier.Operation.ADD_VALUE, true);
			}
		}
	}

	private static void modifier(HostileEntity mob, Identifier id, double value, EntityAttributeModifier.Operation op, boolean on) {
		EntityAttributeInstance range = mob.getAttributeInstance(EntityAttributes.GENERIC_FOLLOW_RANGE);
		if (range == null) {
			return;
		}
		boolean has = range.hasModifier(id);
		if (on && !has) {
			range.addTemporaryModifier(new EntityAttributeModifier(id, value, op));
		} else if (!on && has) {
			range.removeModifier(id);
		}
	}

	/** ¿Es de noche (con cielo: no en el Nether ni el End)? */
	public static boolean isNight(World world) {
		return world.getDimension().hasSkyLight() && !world.getDimension().hasFixedTime() && world.isNight();
	}

	private static void tick(ServerWorld world) {
		long time = world.getTime();
		if (time % 100 == 0) {
			boolean night = hostileNights() && isNight(world);
			for (Entity e : world.iterateEntities()) {
				if (e instanceof HostileEntity mob) {
					modifier(mob, NIGHT_SIGHT, 1.0, EntityAttributeModifier.Operation.ADD_MULTIPLIED_BASE, night);
				}
			}
		}
		if (time % 20 == 0 && world.getRegistryKey() == World.OVERWORLD) {
			schedule(world);
		}
	}

	// ------------------------------------------------------------------ la infección

	public static void maybeInfect(ServerPlayerEntity player, Random random) {
		if (random.nextInt(100) >= EwConfig.get().zombieInfectionChance) {
			return;
		}
		StatusEffectInstance current = player.getStatusEffect(ModEffects.INFECTION);
		int amp = current == null ? 0 : Math.min(2, current.getAmplifier() + 1);
		player.addStatusEffect(new StatusEffectInstance(ModEffects.INFECTION, 20 * 60 * 15, amp));
		player.sendMessage(Text.translatable(amp == 0 ? "message.evolvedwarfare.infection" : "message.evolvedwarfare.infection.worse")
				.formatted(Formatting.DARK_GREEN), true);
	}

	// ------------------------------------------------------------------ las noches de zombis

	public static int every(ServerWorld world) {
		if (world.getDifficulty() == Difficulty.PEACEFUL || !world.getGameRules().getBoolean(GameRules.DO_MOB_SPAWNING)) {
			return 0;
		}
		return Math.max(0, EwConfig.get().zombieHordeEveryDays);
	}

	/** Cuántos zombis trae la horda de esa noche (al menos {@code zombieHordeSize}; uno más cada noche de zombis, hasta el doble; y 5 por jugador de más). */
	public static int hordeSize(long day, int every, int players) {
		int base = Math.max(1, EwConfig.get().zombieHordeSize);
		long night = every > 0 ? Math.max(1, day / every) : 1;
		return base + (int) Math.min(base, night - 1) + 5 * Math.max(0, players - 1);
	}

	private static void schedule(ServerWorld world) {
		int every = every(world);
		if (every <= 0) {
			return;
		}
		long timeOfDay = world.getTimeOfDay();
		long day = Hordes.day(timeOfDay);
		long hour = Math.floorMod(timeOfDay, Hordes.DAY);
		if (!Hordes.isHordeDay(day, every)) {
			return;
		}
		State state = State.get(world);
		if (state.warned != day && hour < NIGHTFALL - 1000) {
			state.warned = day;
			state.markDirty();
			broadcast(world, Text.translatable("message.evolvedwarfare.zombies.today", day).formatted(Formatting.GREEN));
		}
		if (state.lastNight != day && hour >= NIGHTFALL && hour < LAST_START) {
			List<ServerPlayerEntity> players = new ArrayList<>();
			for (ServerPlayerEntity p : world.getPlayers()) {
				if (p.isAlive() && !p.isSpectator() && !p.isCreative()) {
					players.add(p);
				}
			}
			if (players.isEmpty()) {
				return;
			}
			state.lastNight = day;
			state.markDirty();
			List<ServerPlayerEntity> served = new ArrayList<>();
			int phrase = world.getRandom().nextInt(PHRASES);
			for (ServerPlayerEntity p : players) {
				if (served.stream().anyMatch(s -> s.squaredDistanceTo(p) < GROUP * GROUP)) {
					continue;
				}
				int near = (int) players.stream().filter(o -> o.squaredDistanceTo(p) < GROUP * GROUP).count();
				served.add(p);
				spawnHorde(world, p.getBlockPos(), hordeSize(day, every, near), world.getRandom());
				p.sendMessage(Text.translatable("message.evolvedwarfare.zombies.phrase." + phrase).formatted(Formatting.DARK_GREEN, Formatting.ITALIC), false);
				world.playSound(null, p.getBlockPos(), SoundEvents.ENTITY_ZOMBIE_AMBIENT, SoundCategory.HOSTILE, 2.0F, 0.6F);
			}
		}
	}

	/** Saca una horda de zombis en un anillo de 20 a 30 bloques alrededor de ese sitio (en la superficie). */
	public static List<ZombieEntity> spawnHorde(ServerWorld world, BlockPos center, int count, Random random) {
		List<ZombieEntity> out = new ArrayList<>();
		int tries = 0;
		while (out.size() < count && tries++ < count * 8) {
			double a = random.nextDouble() * Math.PI * 2;
			double r = 20 + random.nextDouble() * 10;
			int x = center.getX() + MathHelper.floor(Math.cos(a) * r);
			int z = center.getZ() + MathHelper.floor(Math.sin(a) * r);
			if (!world.isChunkLoaded(x >> 4, z >> 4)) {
				continue;
			}
			int y = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
			BlockPos pos = new BlockPos(x, y, z);
			if (!world.getFluidState(pos.down()).isEmpty() || !world.getBlockState(pos).getCollisionShape(world, pos).isEmpty()) {
				continue;
			}
			EntityType<? extends ZombieEntity> type = world.getBiome(pos).isIn(BiomeTags.IS_BADLANDS) || world.getBiome(pos).matchesKey(
					net.minecraft.world.biome.BiomeKeys.DESERT) ? EntityType.HUSK : random.nextInt(12) == 0 ? EntityType.ZOMBIE_VILLAGER : EntityType.ZOMBIE;
			ZombieEntity zombie = type.create(world);
			if (zombie == null) {
				continue;
			}
			zombie.refreshPositionAndAngles(x + 0.5, y, z + 0.5, random.nextFloat() * 360.0F, 0.0F);
			zombie.initialize(world, world.getLocalDifficulty(pos), SpawnReason.EVENT, null);
			zombie.addCommandTag(HORDE_TAG);
			world.spawnEntityAndPassengers(zombie);
			out.add(zombie);
		}
		return out;
	}

	private static void broadcast(ServerWorld world, Text message) {
		for (ServerPlayerEntity player : world.getServer().getPlayerManager().getPlayerList()) {
			player.sendMessage(message, false);
		}
	}

	/** Qué noche de zombis se avisó y cuál salió ya. */
	public static class State extends PersistentState {
		private static final String ID = "evolvedwarfare_zombie_nights";
		public static final Type<State> TYPE = new Type<>(State::new, State::fromNbt, null);
		long warned = -1L;
		long lastNight = -1L;

		public static State get(ServerWorld world) {
			return world.getPersistentStateManager().getOrCreate(TYPE, ID);
		}

		private static State fromNbt(NbtCompound nbt, net.minecraft.registry.RegistryWrapper.WrapperLookup registries) {
			State s = new State();
			s.warned = nbt.getLong("Warned");
			s.lastNight = nbt.getLong("LastNight");
			return s;
		}

		@Override
		public NbtCompound writeNbt(NbtCompound nbt, net.minecraft.registry.RegistryWrapper.WrapperLookup registries) {
			nbt.putLong("Warned", warned);
			nbt.putLong("LastNight", lastNight);
			return nbt;
		}
	}
}
