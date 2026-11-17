package com.evolvedwarfare.legendary;

import com.evolvedwarfare.EvolvedWarfare;
import com.evolvedwarfare.config.EwConfig;
import com.evolvedwarfare.difficulty.Difficulty;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.damage.DamageType;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SpawnEggItem;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Rarity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.GameRules;
import net.minecraft.world.Heightmap;
import org.jetbrains.annotations.Nullable;

/**
 * Las criaturas legendarias (fase 101 en adelante). Tanda 1: los <b>dragones</b> de fuego y de hielo.
 *
 * <p>Aparecen solos, lejos y en las zonas peligrosas: cada minuto, por cada jugador en el mundo normal en una zona con al
 * menos el nivel de peligro {@code legendaryMinLevel}, hay una probabilidad ({@code legendaryChance}) de que llegue uno
 * (si no hay ya otro en 300 bloques): de hielo en los sitios fríos, de fuego en los cálidos y secos (desiertos, sabanas,
 * mesetas). Llegan con un rugido que se oye de lejos.</p>
 */
public final class Legendaries {
	public static final List<Item> CREATIVE = new ArrayList<>();
	public static final RegistryKey<DamageType> DRAGON_FIRE = RegistryKey.of(RegistryKeys.DAMAGE_TYPE, EvolvedWarfare.id("dragon_fire"));
	public static final RegistryKey<DamageType> DRAGON_ICE = RegistryKey.of(RegistryKeys.DAMAGE_TYPE, EvolvedWarfare.id("dragon_ice"));

	public static final EntityType<DragonEntity> FIRE_DRAGON = type("fire_dragon",
			EntityType.Builder.create(DragonEntity::new, SpawnGroup.MONSTER).dimensions(4.5F, 3.5F).eyeHeight(3.0F).makeFireImmune()
					.maxTrackingRange(20), 0x96221A, 0xD69642);
	public static final EntityType<DragonEntity> ICE_DRAGON = type("ice_dragon",
			EntityType.Builder.create(DragonEntity::new, SpawnGroup.MONSTER).dimensions(4.5F, 3.5F).eyeHeight(3.0F).maxTrackingRange(20),
			0x84B2D6, 0xE8F0F8);

	public static final Item FIRE_DRAGON_SCALE = item("fire_dragon_scale", new Described(new Item.Settings().rarity(Rarity.RARE).fireproof()));
	public static final Item ICE_DRAGON_SCALE = item("ice_dragon_scale", new Described(new Item.Settings().rarity(Rarity.RARE)));
	public static final Item DRAGON_BONE = item("dragon_bone", new Described(new Item.Settings().rarity(Rarity.RARE)));
	public static final Item FIRE_DRAGON_HEART = item("fire_dragon_heart", new Described(new Item.Settings().maxCount(16).rarity(Rarity.EPIC).fireproof()));
	public static final Item ICE_DRAGON_HEART = item("ice_dragon_heart", new Described(new Item.Settings().maxCount(16).rarity(Rarity.EPIC)));

	public static final ItemGroup GROUP = Registry.register(Registries.ITEM_GROUP, EvolvedWarfare.id("legendary"), FabricItemGroup.builder()
			.icon(() -> new ItemStack(FIRE_DRAGON_SCALE))
			.displayName(Text.translatable("itemGroup.evolvedwarfare.legendary"))
			.entries((ctx, entries) -> CREATIVE.forEach(entries::add))
			.build());

	/** Un objeto con su descripción. */
	static class Described extends Item {
		Described(Settings settings) {
			super(settings);
		}

		@Override
		public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, net.minecraft.item.tooltip.TooltipType type) {
			tooltip.add(Text.translatable(getTranslationKey() + ".desc").formatted(Formatting.GRAY));
		}
	}

	/** Qué legendario le va a un sitio, o ninguno. */
	public enum Kind {
		FIRE_DRAGON, ICE_DRAGON
	}

	private Legendaries() {
	}

	private static <T extends HostileEntity> EntityType<T> type(String id, EntityType.Builder<T> builder, int a, int b) {
		EntityType<T> type = Registry.register(Registries.ENTITY_TYPE, EvolvedWarfare.id(id), builder.build(id));
		item(id + "_spawn_egg", new SpawnEggItem(type, a, b, new Item.Settings()));
		return type;
	}

	private static Item item(String name, Item item) {
		CREATIVE.add(Registry.register(Registries.ITEM, EvolvedWarfare.id(name), item));
		return item;
	}

	public static void register() {
		FabricDefaultAttributeRegistry.register(FIRE_DRAGON, DragonEntity.createAttributes());
		FabricDefaultAttributeRegistry.register(ICE_DRAGON, DragonEntity.createAttributes());
		ServerTickEvents.END_SERVER_TICK.register(Legendaries::tick);
	}

	public static boolean griefing(ServerWorld world) {
		return EwConfig.get().dragonGriefing && world.getGameRules().getBoolean(GameRules.DO_MOB_GRIEFING);
	}

	/** El legendario que toca según la temperatura del bioma: de hielo con frío, de fuego con calor; si no, ninguno. */
	@Nullable
	public static Kind kindFor(float temperature) {
		if (temperature < 0.3F) {
			return Kind.ICE_DRAGON;
		}
		if (temperature >= 1.0F) {
			return Kind.FIRE_DRAGON;
		}
		return null;
	}

	private static void tick(MinecraftServer server) {
		if (server.getTicks() % 1200 != 0 || !EwConfig.get().legendaryEnabled || System.getProperty("fabric-api.gametest") != null) {
			return;
		}
		ServerWorld world = server.getOverworld();
		if (world.getDifficulty() == net.minecraft.world.Difficulty.PEACEFUL) {
			return;
		}
		for (ServerPlayerEntity player : world.getPlayers()) {
			if (player.isSpectator() || player.isCreative() || Difficulty.level(world, player.getBlockPos()) < EwConfig.get().legendaryMinLevel
					|| world.random.nextDouble() >= EwConfig.get().legendaryChance) {
				continue;
			}
			Kind kind = kindFor(world.getBiome(player.getBlockPos()).value().getTemperature());
			if (kind == null || !world.getEntitiesByClass(DragonEntity.class, new Box(player.getBlockPos()).expand(300, 200, 300), e -> true).isEmpty()) {
				continue;
			}
			spawn(world, player, kind);
		}
	}

	/** Llega uno: lejos del jugador, alto, con un rugido que se oye desde muy lejos. */
	@Nullable
	public static DragonEntity spawn(ServerWorld world, ServerPlayerEntity near, Kind kind) {
		double a = world.random.nextDouble() * Math.PI * 2;
		double d = 56 + world.random.nextInt(32);
		int x = (int) (near.getX() + Math.cos(a) * d), z = (int) (near.getZ() + Math.sin(a) * d);
		if (!world.isChunkLoaded(x >> 4, z >> 4)) {
			return null;
		}
		int y = world.getTopY(Heightmap.Type.MOTION_BLOCKING, x, z) + 24;
		DragonEntity dragon = (kind == Kind.ICE_DRAGON ? ICE_DRAGON : FIRE_DRAGON).create(world);
		if (dragon == null) {
			return null;
		}
		dragon.refreshPositionAndAngles(x + 0.5, y, z + 0.5, world.random.nextFloat() * 360, 0);
		dragon.setHome(new BlockPos(x, y - 24, z));
		world.spawnEntity(dragon);
		String key = "message.evolvedwarfare.legendary.roar." + (kind == Kind.ICE_DRAGON ? "ice_dragon" : "fire_dragon");
		for (ServerPlayerEntity p : world.getPlayers(p -> p.squaredDistanceTo(dragon) < 200 * 200)) {
			p.playSoundToPlayer(SoundEvents.ENTITY_ENDER_DRAGON_GROWL, SoundCategory.HOSTILE, 1.0F, kind == Kind.ICE_DRAGON ? 1.3F : 0.8F);
			p.sendMessage(Text.translatable(key).formatted(kind == Kind.ICE_DRAGON ? Formatting.AQUA : Formatting.RED, Formatting.ITALIC), false);
		}
		return dragon;
	}
}
