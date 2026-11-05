package com.evolvedwarfare.fishing;

import com.evolvedwarfare.EvolvedWarfare;
import com.evolvedwarfare.config.EwConfig;
import com.evolvedwarfare.registry.ModComponents;
import com.evolvedwarfare.registry.ModItems;
import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.FishingBobberEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.loot.LootTable;
import net.minecraft.loot.LootTables;
import net.minecraft.loot.context.LootContextParameterSet;
import net.minecraft.loot.context.LootContextParameters;
import net.minecraft.loot.context.LootContextTypes;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.BiomeTags;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.stat.Stats;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeKeys;
import org.jetbrains.annotations.Nullable;

/**
 * La pesca con minijuego. Cuando pica un pez y recoges la caña, en vez de salir el pez empieza el minijuego
 * ({@link FishMinigame}, en el cliente); al acabar, el cliente dice si lo has sacado, si ha sido perfecto y si has cogido
 * el cofre. El pez sale con su <b>peso</b> (al azar entre lo que pesa su especie) y su <b>calidad</b> (bronce, plata, oro o
 * platino: según lo que pese, tu nivel, tu suerte y, si es perfecto, una más). La basura y los tesoros de siempre salen
 * sin minijuego.
 * <p>
 * El <b>nivel de pesca</b> (0 a 10) sube pescando, y agranda la zona verde; también la agrandan el encantamiento Atracción
 * y el <b>cebo</b> en la otra mano (que se gasta). Suerte marina y el cebo mágico traen más cofres.
 */
public final class Fishing {
	public static final AttachmentType<Integer> XP = AttachmentRegistry.<Integer>builder().persistent(Codec.INT).initializer(() -> 0)
			.buildAndRegister(EvolvedWarfare.id("fishing_xp"));
	private static final int[] LEVELS = {0, 30, 80, 150, 240, 350, 480, 640, 830, 1050, 1300};
	public static final RegistryKey<LootTable> CHEST_LOOT = RegistryKey.of(RegistryKeys.LOOT_TABLE, EvolvedWarfare.id("gameplay/fishing_chest"));

	/** Una partida en curso. */
	public record Session(int id, FishingBobberEntity bobber, FishSpecies species, ItemStack rod, boolean treasure, int bait, long started) {
	}

	private static final Map<UUID, Session> SESSIONS = new HashMap<>();
	private static int nextId = 1;

	private Fishing() {
	}

	public static boolean enabled() {
		return EwConfig.get().fishingMinigame;
	}

	public static void register() {
		PayloadTypeRegistry.playS2C().register(FishingPayloads.Start.ID, FishingPayloads.Start.CODEC);
		PayloadTypeRegistry.playS2C().register(FishingPayloads.End.ID, FishingPayloads.End.CODEC);
		PayloadTypeRegistry.playC2S().register(FishingPayloads.Result.ID, FishingPayloads.Result.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(FishingPayloads.Result.ID, (payload, context) -> finish(context.player(), payload));
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (SESSIONS.isEmpty()) {
				return;
			}
			for (UUID id : List.copyOf(SESSIONS.keySet())) {
				Session s = SESSIONS.get(id);
				ServerPlayerEntity player = server.getPlayerManager().getPlayer(id);
				if (player == null || s.bobber().isRemoved() || player.fishHook != s.bobber()) {
					SESSIONS.remove(id);
					if (player != null) {
						com.evolvedwarfare.network.ActivityPayload.set(player, com.evolvedwarfare.network.ActivityPayload.NONE);
						ServerPlayNetworking.send(player, new FishingPayloads.End(s.id()));
					}
				}
			}
		});
	}

	// ------------------------------------------------------------------ nivel

	public static int level(PlayerEntity player) {
		return levelOf(player.getAttachedOrElse(XP, 0));
	}

	public static int levelOf(int xp) {
		int level = 0;
		while (level < LEVELS.length - 1 && xp >= LEVELS[level + 1]) {
			level++;
		}
		return level;
	}

	public static void addXp(ServerPlayerEntity player, int amount) {
		int before = level(player);
		player.setAttached(XP, player.getAttachedOrElse(XP, 0) + amount);
		int after = level(player);
		if (after > before) {
			player.sendMessage(Text.translatable("message.evolvedwarfare.fishing.level", after).formatted(Formatting.AQUA), false);
			player.playSoundToPlayer(SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 0.8F, 1.4F);
		}
	}

	public static boolean inGame(PlayerEntity player) {
		return SESSIONS.containsKey(player.getUuid());
	}

	public static boolean frozen(FishingBobberEntity bobber) {
		PlayerEntity owner = bobber.getPlayerOwner();
		if (owner == null) {
			return false;
		}
		Session s = SESSIONS.get(owner.getUuid());
		return s != null && s.bobber() == bobber;
	}

	// ------------------------------------------------------------------ qué pez

	/** El pez que pica aquí (según el agua: mar, mar profundo, frío, cálido, río, lago o pantano), o null. */
	public static FishSpecies pick(ServerWorld world, net.minecraft.util.math.BlockPos pos, Random random, int bait) {
		RegistryEntry<Biome> biome = world.getBiome(pos);
		boolean ocean = biome.isIn(BiomeTags.IS_OCEAN);
		boolean deep = biome.isIn(BiomeTags.IS_DEEP_OCEAN);
		boolean river = biome.isIn(BiomeTags.IS_RIVER);
		boolean swamp = biome.matchesKey(BiomeKeys.SWAMP) || biome.matchesKey(BiomeKeys.MANGROVE_SWAMP);
		float temp = biome.value().getTemperature();
		boolean cold = temp < 0.3F;
		boolean warm = temp >= 0.9F || biome.matchesKey(BiomeKeys.WARM_OCEAN) || biome.matchesKey(BiomeKeys.LUKEWARM_OCEAN);
		boolean night = world.isNight();
		List<FishSpecies> options = new ArrayList<>();
		List<Integer> weights = new ArrayList<>();
		for (FishSpecies s : FishSpecies.values()) {
			boolean fits = switch (s.water) {
				case OCEAN -> ocean;
				case DEEP_OCEAN -> deep;
				case COLD -> (ocean || river) && (cold || !warm);
				case WARM -> (ocean && warm) || (warm && !river && !swamp);
				case FRESH -> !ocean;
				case FRESH_COLD -> !ocean && (cold || river);
				case SWAMP -> swamp || (night && !ocean);
			};
			if (fits) {
				int w = s.weight;
				if (bait >= 2 && s.difficulty >= 60) {
					w *= 3; // el cebo mágico atrae a los grandes
				}
				options.add(s);
				weights.add(w);
			}
		}
		if (options.isEmpty()) {
			return FishSpecies.COD;
		}
		int total = weights.stream().mapToInt(Integer::intValue).sum();
		int r = random.nextInt(total);
		for (int i = 0; i < options.size(); i++) {
			r -= weights.get(i);
			if (r < 0) {
				return options.get(i);
			}
		}
		return options.get(0);
	}

	// ------------------------------------------------------------------ el pez que sale

	/** La calidad: 1 bronce, 2 plata, 3 oro, 4 platino (según el peso de 0 a 1, tu nivel, tu suerte; una más si es perfecto). */
	public static int quality(double weightPct, int level, int luck, boolean perfect, float roll) {
		double score = weightPct * 0.75 + level * 0.02 + luck * 0.03 + roll * 0.1;
		int q = score < 0.3 ? FishData.BRONZE : score < 0.55 ? FishData.SILVER : score < 0.8 ? FishData.GOLD : FishData.PLATINUM;
		return Math.min(FishData.PLATINUM, q + (perfect ? 1 : 0));
	}

	/** Un pez de esa especie con su peso (los grandes, más raros) y su calidad. */
	public static ItemStack makeFish(FishSpecies species, Random random, int level, int luck, boolean perfect) {
		double pct = Math.pow(random.nextDouble(), 1.6 - level * 0.04);
		double kg = species.minKg + (species.maxKg - species.minKg) * pct;
		int quality = quality(pct, level, luck, perfect, random.nextFloat());
		ItemStack stack = new ItemStack(species.item());
		stack.set(ModComponents.FISH, new FishData((int) Math.round(kg * 1000.0), quality));
		return stack;
	}

	/** Lo que paga el buzón por un pez con calidad (por unidad). */
	public static double price(ItemStack stack) {
		FishData data = stack.get(ModComponents.FISH);
		FishSpecies species = FishSpecies.of(stack.getItem());
		if (data == null || species == null) {
			return 0.0;
		}
		return species.price * (data.kg() / species.averageKg()) * data.priceFactor();
	}

	// ------------------------------------------------------------------ empezar y acabar

	private static int enchant(ServerWorld world, ItemStack stack, RegistryKey<Enchantment> key) {
		return world.getRegistryManager().get(RegistryKeys.ENCHANTMENT).getEntry(key).map(e -> EnchantmentHelper.getLevel(e, stack)).orElse(0);
	}

	/** El cebo de la otra mano: 0 nada, 1 cebo, 2 cebo mágico. */
	private static int bait(PlayerEntity player) {
		ItemStack off = player.getOffHandStack();
		return off.isOf(ModItems.MAGIC_BAIT) ? 2 : off.isOf(ModItems.BAIT) ? 1 : 0;
	}

	/**
	 * Lo que pasa al recoger la caña con algo picando: si sale un pez, empieza el minijuego (devuelve 0: la caña no se gasta
	 * aún); si sale basura o un tesoro, se lo lleva como siempre (1); -1, que lo haga vanilla.
	 */
	public static int onReel(ServerPlayerEntity player, FishingBobberEntity bobber, ItemStack rod, int luckBonus) {
		if (!enabled() || inGame(player) || !(bobber.getWorld() instanceof ServerWorld world)) {
			return -1;
		}
		LootContextParameterSet params = new LootContextParameterSet.Builder(world).add(LootContextParameters.ORIGIN, bobber.getPos())
				.add(LootContextParameters.TOOL, rod).add(LootContextParameters.THIS_ENTITY, bobber).luck(luckBonus + player.getLuck())
				.build(LootContextTypes.FISHING);
		LootTable table = world.getServer().getReloadableRegistries().getLootTable(LootTables.FISHING_GAMEPLAY);
		List<ItemStack> loot = table.generateLoot(params);
		boolean fish = loot.stream().anyMatch(s -> s.isIn(ItemTags.FISHES));
		if (!fish) {
			give(player, bobber, loot);
			bobber.discard();
			return 1;
		}
		Random random = world.getRandom();
		int bait = bait(player);
		if (bait > 0 && !player.isCreative()) {
			player.getOffHandStack().decrement(1);
		}
		FishSpecies species = pick(world, bobber.getBlockPos(), random, bait);
		int level = level(player);
		int lure = enchant(world, rod, Enchantments.LURE);
		int luck = enchant(world, rod, Enchantments.LUCK_OF_THE_SEA);
		boolean treasure = random.nextFloat() < 0.12F + luck * 0.05F + level * 0.01F + (bait >= 2 ? 0.08F : 0.0F);
		Session s = new Session(nextId++, bobber, species, rod.copy(), treasure, bait, world.getTime());
		SESSIONS.put(player.getUuid(), s);
		float zone = FishMinigame.zoneSize(level, lure, bait);
		com.evolvedwarfare.network.ActivityPayload.set(player, com.evolvedwarfare.network.ActivityPayload.FISHING);
		ServerPlayNetworking.send(player, new FishingPayloads.Start(s.id(), new ItemStack(species.item()), species.difficulty,
				species.behavior.ordinal(), zone, treasure, level, random.nextLong()));
		world.playSound(null, bobber.getBlockPos(), SoundEvents.ENTITY_FISHING_BOBBER_SPLASH, SoundCategory.NEUTRAL, 1.0F, 1.0F);
		return 0;
	}

	private static void finish(ServerPlayerEntity player, FishingPayloads.Result result) {
		Session s = SESSIONS.get(player.getUuid());
		if (s == null || s.id() != result.session() || !(player.getWorld() instanceof ServerWorld world)) {
			return;
		}
		SESSIONS.remove(player.getUuid());
		com.evolvedwarfare.network.ActivityPayload.set(player, com.evolvedwarfare.network.ActivityPayload.NONE);
		boolean plausible = world.getTime() - s.started() >= 20;
		if (result.caught() && plausible && !s.bobber().isRemoved()) {
			int luck = enchant(world, s.rod(), Enchantments.LUCK_OF_THE_SEA);
			ItemStack fish = makeFish(s.species(), world.getRandom(), level(player), luck, result.perfect());
			List<ItemStack> loot = new ArrayList<>();
			loot.add(fish);
			if (result.treasure() && s.treasure()) {
				LootContextParameterSet params = new LootContextParameterSet.Builder(world).add(LootContextParameters.ORIGIN, s.bobber().getPos())
						.add(LootContextParameters.TOOL, s.rod()).add(LootContextParameters.THIS_ENTITY, s.bobber()).luck(luck + player.getLuck())
						.build(LootContextTypes.FISHING);
				loot.addAll(world.getServer().getReloadableRegistries().getLootTable(CHEST_LOOT).generateLoot(params));
				player.sendMessage(Text.translatable("message.evolvedwarfare.fishing.treasure").formatted(Formatting.GOLD), false);
				world.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_CHEST_OPEN, SoundCategory.PLAYERS, 0.8F, 1.2F);
			}
			give(player, s.bobber(), loot);
			FishData data = fish.get(ModComponents.FISH);
			player.sendMessage(Text.translatable("message.evolvedwarfare.fishing.caught", fish.getName(),
					Text.translatable("quality.evolvedwarfare." + FishData.key(data.quality())).formatted(FishData.formatting(data.quality())),
					String.format("%.2f", data.kg()), result.perfect() ? Text.translatable("message.evolvedwarfare.fishing.perfect") : Text.empty()), true);
			addXp(player, 3 + s.species().difficulty / 8 + (result.perfect() ? 4 : 0));
			player.getStackInHand(Hand.MAIN_HAND).damage(1, player, net.minecraft.entity.EquipmentSlot.MAINHAND);
		} else {
			player.sendMessage(Text.translatable("message.evolvedwarfare.fishing.escaped").formatted(Formatting.GRAY), true);
			world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_FISHING_BOBBER_RETRIEVE, SoundCategory.NEUTRAL, 1.0F, 0.6F);
			addXp(player, 1);
		}
		s.bobber().discard();
	}

	/** Como vanilla: lo pescado sale volando hacia el jugador, con su experiencia. */
	public static void give(PlayerEntity player, FishingBobberEntity bobber, List<ItemStack> loot) {
		ServerWorld world = (ServerWorld) bobber.getWorld();
		for (ItemStack stack : loot) {
			ItemEntity item = new ItemEntity(world, bobber.getX(), bobber.getY(), bobber.getZ(), stack);
			double dx = player.getX() - bobber.getX();
			double dy = player.getY() - bobber.getY();
			double dz = player.getZ() - bobber.getZ();
			item.setVelocity(dx * 0.1, dy * 0.1 + Math.sqrt(Math.sqrt(dx * dx + dy * dy + dz * dz)) * 0.08, dz * 0.1);
			world.spawnEntity(item);
			if (stack.isIn(ItemTags.FISHES)) {
				player.increaseStat(Stats.FISH_CAUGHT, 1);
			}
		}
		world.spawnEntity(new ExperienceOrbEntity(world, player.getX(), player.getY() + 0.5, player.getZ() + 0.5, world.getRandom().nextInt(6) + 1));
	}

	@Nullable
	public static Session session(PlayerEntity player) {
		return SESSIONS.get(player.getUuid());
	}
}
