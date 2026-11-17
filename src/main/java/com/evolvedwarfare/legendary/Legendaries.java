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

	public static final EntityType<FrostGiantEntity> FROST_GIANT = type("frost_giant",
			EntityType.Builder.create(FrostGiantEntity::new, SpawnGroup.MONSTER).dimensions(2.6F, 8.0F).eyeHeight(7.2F).maxTrackingRange(16),
			0x92ACC4, 0xE8E6DE);

	public static final EntityType<SeaSerpentEntity> SEA_SERPENT = type("sea_serpent",
			EntityType.Builder.create(SeaSerpentEntity::new, SpawnGroup.MONSTER).dimensions(3.5F, 2.2F).eyeHeight(1.6F).maxTrackingRange(16),
			0x2C7880, 0x96D2BE);

	public static final EntityType<TreantEntity> TREANT = type("treant",
			EntityType.Builder.create(TreantEntity::new, SpawnGroup.MONSTER).dimensions(1.2F, 3.3F).eyeHeight(2.6F).maxTrackingRange(10),
			0x68502E, 0x40742A);

	public static final EntityType<CocoEntity> COCO = type("coco",
			EntityType.Builder.create(CocoEntity::new, SpawnGroup.MONSTER).dimensions(0.9F, 3.1F).eyeHeight(2.8F).maxTrackingRange(10),
			0x0E0C12, 0x962018);

	public static final EntityType<RockGuardianEntity> ROCK_GUARDIAN = type("rock_guardian",
			EntityType.Builder.create(RockGuardianEntity::new, SpawnGroup.MONSTER).dimensions(1.6F, 2.3F).eyeHeight(1.6F).maxTrackingRange(10),
			0x7C7C80, 0xFFBE50);

	public static final Item FIRE_DRAGON_SCALE = item("fire_dragon_scale", new Described(new Item.Settings().rarity(Rarity.RARE).fireproof()));
	public static final Item ICE_DRAGON_SCALE = item("ice_dragon_scale", new Described(new Item.Settings().rarity(Rarity.RARE)));
	public static final Item DRAGON_BONE = item("dragon_bone", new Described(new Item.Settings().rarity(Rarity.RARE)));
	public static final Item FIRE_DRAGON_HEART = item("fire_dragon_heart", new Described(new Item.Settings().maxCount(16).rarity(Rarity.EPIC).fireproof()));
	public static final Item ICE_DRAGON_HEART = item("ice_dragon_heart", new Described(new Item.Settings().maxCount(16).rarity(Rarity.EPIC)));

	// las armaduras de escamas y las espadas de hueso (fase 102)
	public static final List<Item> DRAGON_ARMOR = new ArrayList<>();

	static {
		for (DragonGear.Element element : new DragonGear.Element[] {DragonGear.Element.FIRE, DragonGear.Element.ICE}) {
			String set = element == DragonGear.Element.FIRE ? "fire_dragon_scale" : "ice_dragon_scale";
			for (net.minecraft.item.ArmorItem.Type type : new net.minecraft.item.ArmorItem.Type[] {net.minecraft.item.ArmorItem.Type.HELMET,
					net.minecraft.item.ArmorItem.Type.CHESTPLATE, net.minecraft.item.ArmorItem.Type.LEGGINGS, net.minecraft.item.ArmorItem.Type.BOOTS}) {
				DRAGON_ARMOR.add(item(set + "_" + type.getName(), new DragonGear.DragonArmorItem(element, type)));
			}
		}
	}

	public static final Item DRAGONBONE_SWORD = item("dragonbone_sword", new DragonGear.DragonboneSwordItem(null));
	public static final Item FLAMED_DRAGONBONE_SWORD = item("flamed_dragonbone_sword", new DragonGear.DragonboneSwordItem(DragonGear.Element.FIRE));
	public static final Item ICED_DRAGONBONE_SWORD = item("iced_dragonbone_sword", new DragonGear.DragonboneSwordItem(DragonGear.Element.ICE));

	// el gigante de hielo (fase 103)
	public static final Item GIANT_CLUB = item("giant_club", new DragonGear.GiantClubItem());
	public static final Item GIANT_PELT = item("giant_pelt", new Described(new Item.Settings().rarity(Rarity.RARE)));
	// la serpiente marina (fase 104)
	public static final Item SEA_SERPENT_SCALE = item("sea_serpent_scale", new Described(new Item.Settings().rarity(Rarity.RARE)));
	public static final Item SERPENT_FANG = item("serpent_fang", new Described(new Item.Settings().rarity(Rarity.RARE)));
	public static final List<Item> TIDE_ARMOR = new ArrayList<>();
	public static final Item LIVING_BARK = item("living_bark", new Described(new Item.Settings().rarity(Rarity.UNCOMMON)));
	public static final Item COCO_CLAW = item("coco_claw", new Described(new Item.Settings().rarity(Rarity.EPIC)));
	public static final Item ROCK_CORE = item("rock_core", new Described(new Item.Settings().rarity(Rarity.RARE)));

	static {
		for (net.minecraft.item.ArmorItem.Type type : new net.minecraft.item.ArmorItem.Type[] {net.minecraft.item.ArmorItem.Type.HELMET,
				net.minecraft.item.ArmorItem.Type.CHESTPLATE, net.minecraft.item.ArmorItem.Type.LEGGINGS, net.minecraft.item.ArmorItem.Type.BOOTS}) {
			TIDE_ARMOR.add(item("sea_serpent_scale_" + type.getName(), new DragonGear.DragonArmorItem(DragonGear.Element.SEA, type)));
		}
	}

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
		FIRE_DRAGON, ICE_DRAGON, FROST_GIANT, SEA_SERPENT
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
		FabricDefaultAttributeRegistry.register(FROST_GIANT, FrostGiantEntity.createAttributes());
		FabricDefaultAttributeRegistry.register(SEA_SERPENT, SeaSerpentEntity.createAttributes());
		FabricDefaultAttributeRegistry.register(TREANT, TreantEntity.createAttributes());
		FabricDefaultAttributeRegistry.register(COCO, CocoEntity.createAttributes());
		FabricDefaultAttributeRegistry.register(ROCK_GUARDIAN, RockGuardianEntity.createAttributes());
		BlockMobs.register();
		ServerTickEvents.END_SERVER_TICK.register(Legendaries::tick);
		DragonGear.register();
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
			var biome = world.getBiome(player.getBlockPos());
			Kind kind = biome.isIn(net.minecraft.registry.tag.BiomeTags.IS_OCEAN)
					? (player.isTouchingWater() || player.getVehicle() instanceof net.minecraft.entity.vehicle.BoatEntity ? Kind.SEA_SERPENT : null)
					: kindFor(biome.value().getTemperature());
			if (kind == Kind.ICE_DRAGON && world.random.nextBoolean()) {
				kind = Kind.FROST_GIANT;
			}
			if (kind == null || !world.getEntitiesByClass(net.minecraft.entity.mob.MobEntity.class, new Box(player.getBlockPos()).expand(300, 200, 300),
					e -> e instanceof Legendary).isEmpty()) {
				continue;
			}
			spawn(world, player, kind);
		}
	}

	/** Llega uno: lejos del jugador (los dragones, en lo alto; el gigante, en el suelo), con un aviso que se oye desde muy lejos. */
	@Nullable
	public static net.minecraft.entity.mob.MobEntity spawn(ServerWorld world, ServerPlayerEntity near, Kind kind) {
		double a = world.random.nextDouble() * Math.PI * 2;
		double d = (kind == Kind.FROST_GIANT ? 36 : 56) + world.random.nextInt(32);
		int x = (int) (near.getX() + Math.cos(a) * d), z = (int) (near.getZ() + Math.sin(a) * d);
		if (!world.isChunkLoaded(x >> 4, z >> 4)) {
			return null;
		}
		int ground = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
		net.minecraft.entity.mob.MobEntity mob = switch (kind) {
			case FIRE_DRAGON -> FIRE_DRAGON.create(world);
			case ICE_DRAGON -> ICE_DRAGON.create(world);
			case FROST_GIANT -> FROST_GIANT.create(world);
			case SEA_SERPENT -> SEA_SERPENT.create(world);
		};
		if (mob == null) {
			return null;
		}
		int y = kind == Kind.FROST_GIANT ? ground : ground + 24;
		if (kind == Kind.FROST_GIANT && world.getBlockState(new BlockPos(x, ground - 1, z)).getFluidState().isStill()) {
			return null; // no en el agua
		}
		if (kind == Kind.SEA_SERPENT) {
			// bajo el agua, a unos bloques de profundidad
			y = world.getSeaLevel() - 4;
			if (!world.getFluidState(new BlockPos(x, y, z)).isIn(net.minecraft.registry.tag.FluidTags.WATER)
					|| !world.getFluidState(new BlockPos(x, y + 2, z)).isIn(net.minecraft.registry.tag.FluidTags.WATER)) {
				return null;
			}
		}
		mob.refreshPositionAndAngles(x + 0.5, y, z + 0.5, world.random.nextFloat() * 360, 0);
		if (mob instanceof DragonEntity dragon) {
			dragon.setHome(new BlockPos(x, ground, z));
		}
		world.spawnEntity(mob);
		String id = kind.name().toLowerCase(java.util.Locale.ROOT);
		boolean cold = kind != Kind.FIRE_DRAGON;
		final net.minecraft.sound.SoundEvent sound = kind == Kind.SEA_SERPENT ? SoundEvents.ENTITY_ELDER_GUARDIAN_CURSE
				: kind == Kind.FROST_GIANT ? SoundEvents.ENTITY_RAVAGER_ROAR : SoundEvents.ENTITY_ENDER_DRAGON_GROWL;
		for (ServerPlayerEntity p : world.getPlayers(p -> p.squaredDistanceTo(mob) < 200 * 200)) {
			p.playSoundToPlayer(sound, SoundCategory.HOSTILE, 1.0F, kind == Kind.FROST_GIANT ? 0.4F : kind == Kind.SEA_SERPENT ? 0.6F : cold ? 1.3F : 0.8F);
			p.sendMessage(Text.translatable("message.evolvedwarfare.legendary.roar." + id).formatted(cold ? (kind == Kind.SEA_SERPENT ? Formatting.DARK_AQUA : Formatting.AQUA) : Formatting.RED,
					Formatting.ITALIC), false);
		}
		return mob;
	}
}
