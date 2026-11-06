package com.evolvedwarfare.hell;

import com.evolvedwarfare.EvolvedWarfare;
import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.MapColor;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.component.ComponentType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectCategory;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.SpawnEggItem;
import net.minecraft.loot.LootPool;
import net.minecraft.loot.LootTable;
import net.minecraft.loot.condition.RandomChanceLootCondition;
import net.minecraft.loot.entry.ItemEntry;
import net.minecraft.loot.function.SetComponentsLootFunction;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.Rarity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.Heightmap;
import org.jetbrains.annotations.Nullable;

/**
 * El Apocalipsis (fase 36): los tres mini jefes (el Ángel del Abismo, el Falso Profeta y la Bestia mayor), los siete
 * fragmentos de sello y la Llave del Abismo, el círculo de ritual y sus ritos, sus objetos, la protección bendita y el
 * pacto oscuro, y dónde salen los fragmentos (cofres de las estructuras y los señores de la Ciudadela).
 */
public final class Apocalypse {
	public static final List<Item> CREATIVE = new ArrayList<>();

	public static final ComponentType<Integer> SEAL = Registry.register(Registries.DATA_COMPONENT_TYPE, EvolvedWarfare.id("seal"),
			ComponentType.<Integer>builder().codec(Codec.INT).packetCodec(PacketCodecs.VAR_INT).build());

	// ------------------------------------------------------------------ los efectos

	public static class Effect extends StatusEffect {
		public Effect(StatusEffectCategory category, int color) {
			super(category, color);
		}
	}

	/** El pacto oscuro: más fuerza y velocidad, pero un hambre que no se calma. */
	public static class Pact extends Effect {
		public Pact() {
			super(StatusEffectCategory.BENEFICIAL, 0x7A1028);
		}

		@Override
		public boolean canApplyUpdateEffect(int duration, int amplifier) {
			return duration % 20 == 0;
		}

		@Override
		public boolean applyUpdateEffect(LivingEntity entity, int amplifier) {
			if (entity instanceof PlayerEntity p) {
				p.addExhaustion(0.6F);
			}
			return true;
		}
	}

	/** Mientras dura, ninguna maldición del Nether te alcanza. */
	public static final RegistryEntry<StatusEffect> BLESSED_WARD = Registry.registerReference(Registries.STATUS_EFFECT, EvolvedWarfare.id("blessed_ward"),
			new Effect(StatusEffectCategory.BENEFICIAL, 0xF0E6B4));
	public static final RegistryEntry<StatusEffect> DARK_PACT = Registry.registerReference(Registries.STATUS_EFFECT, EvolvedWarfare.id("dark_pact"),
			new Pact().addAttributeModifier(EntityAttributes.GENERIC_ATTACK_DAMAGE, EvolvedWarfare.id("effect.dark_pact.damage"), 3.0,
					EntityAttributeModifier.Operation.ADD_VALUE)
					.addAttributeModifier(EntityAttributes.GENERIC_MOVEMENT_SPEED, EvolvedWarfare.id("effect.dark_pact.speed"), 0.15,
							EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)
					.addAttributeModifier(EntityAttributes.GENERIC_ATTACK_SPEED, EvolvedWarfare.id("effect.dark_pact.haste"), 0.2,
							EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));

	// ------------------------------------------------------------------ las criaturas

	public static final EntityType<AbyssAngelEntity> ABYSS_ANGEL = type("abyss_angel",
			EntityType.Builder.create(AbyssAngelEntity::new, SpawnGroup.MONSTER).dimensions(1.2F, 3.0F).eyeHeight(2.6F).makeFireImmune().maxTrackingRange(16),
			0x16121A, 0xDCB43C);
	public static final EntityType<FalseProphetEntity> FALSE_PROPHET = type("false_prophet",
			EntityType.Builder.create(FalseProphetEntity::new, SpawnGroup.MONSTER).dimensions(0.8F, 2.6F).eyeHeight(2.2F).makeFireImmune().maxTrackingRange(12),
			0xE2DCCE, 0x8C1E1E);
	public static final EntityType<GreaterBeastEntity> GREATER_BEAST = type("greater_beast",
			EntityType.Builder.create(GreaterBeastEntity::new, SpawnGroup.MONSTER).dimensions(3.0F, 3.4F).eyeHeight(3.0F).makeFireImmune().maxTrackingRange(16),
			0xAA7832, 0x781E1E);

	// ------------------------------------------------------------------ los bloques y objetos

	public static final Block RITUAL_CIRCLE = Registry.register(Registries.BLOCK, EvolvedWarfare.id("ritual_circle"), new RitualCircleBlock(
			AbstractBlock.Settings.create().mapColor(MapColor.BLACK).strength(2.0F, 6.0F).requiresTool().nonOpaque().luminance(s -> 4)
					.sounds(BlockSoundGroup.POLISHED_DEEPSLATE)));
	public static final BlockEntityType<RitualCircleBlockEntity> RITUAL_CIRCLE_ENTITY = Registry.register(Registries.BLOCK_ENTITY_TYPE,
			EvolvedWarfare.id("ritual_circle"), BlockEntityType.Builder.create(RitualCircleBlockEntity::new, RITUAL_CIRCLE).build(null));
	public static final Item RITUAL_CIRCLE_ITEM = item("ritual_circle", new BlockItem(RITUAL_CIRCLE, new Item.Settings()));
	public static final Item SEAL_FRAGMENT = item("seal_fragment", new SealFragmentItem(new Item.Settings().rarity(Rarity.RARE)));
	public static final Item ABYSS_KEY = item("abyss_key", new ApocalypseItems.Key(new Item.Settings().maxCount(1).rarity(Rarity.EPIC).fireproof()));
	public static final Item PROPHET_STAFF = item("prophet_staff", new ApocalypseItems.ProphetStaff(new Item.Settings().maxDamage(120).rarity(Rarity.EPIC)
			.fireproof()));
	public static final Item BEAST_HORN = item("beast_horn", new ApocalypseItems.BeastHorn(new Item.Settings().maxCount(1).rarity(Rarity.EPIC).fireproof()));
	public static final Item ABYSS_WINGS = item("abyss_wings", new ApocalypseItems.AbyssWings(new Item.Settings().maxDamage(160).rarity(Rarity.EPIC)
			.fireproof()));

	/** Dónde salen fragmentos de sello (cofres y señores de la Ciudadela) y con qué probabilidad. */
	private static final Map<String, Float> SEAL_SOURCES = Map.ofEntries(
			Map.entry("chests/babel_top", 1.0F), Map.entry("chests/citadel_secret", 1.0F), Map.entry("chests/abyss_vault", 0.45F),
			Map.entry("chests/lake_throne", 0.35F), Map.entry("chests/sodom_temple", 0.3F), Map.entry("chests/temple_crypt", 0.2F),
			Map.entry("chests/citadel_treasury", 0.5F), Map.entry("chests/citadel_library", 0.25F),
			Map.entry("entities/legion", 0.5F), Map.entry("entities/beelzebub", 0.5F), Map.entry("entities/moloch", 0.6F),
			Map.entry("entities/abaddon", 1.0F));

	private Apocalypse() {
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
		FabricDefaultAttributeRegistry.register(ABYSS_ANGEL, AbyssAngelEntity.createAttributes());
		FabricDefaultAttributeRegistry.register(FALSE_PROPHET, FalseProphetEntity.createAttributes());
		FabricDefaultAttributeRegistry.register(GREATER_BEAST, GreaterBeastEntity.createAttributes());
		LootTableEvents.MODIFY.register((key, builder, source, registries) -> {
			Identifier id = key.getValue();
			if (!id.getNamespace().equals(EvolvedWarfare.MOD_ID)) {
				return;
			}
			Float chance = SEAL_SOURCES.get(id.getPath());
			if (chance != null) {
				builder.pool(sealPool(chance));
			}
		});
	}

	/** Una tirada de un fragmento de sello al azar (de los siete), con esa probabilidad. */
	public static LootPool.Builder sealPool(float chance) {
		LootPool.Builder pool = LootPool.builder();
		for (int k = 0; k < SealFragmentItem.SEALS; k++) {
			pool.with(ItemEntry.builder(SEAL_FRAGMENT).apply(SetComponentsLootFunction.builder(SEAL, k)));
		}
		if (chance < 1.0F) {
			pool.conditionally(RandomChanceLootCondition.builder(chance));
		}
		return pool;
	}

	// ------------------------------------------------------------------ lo común

	/** Fuego del cielo: una columna de llamas en pos que daña y quema lo de alrededor (3 bloques), menos a quien lo llama. */
	public static int fireFromHeaven(ServerWorld world, BlockPos pos, @Nullable Entity caster, float damage) {
		int y = Math.min(pos.getY(), world.getTopY(Heightmap.Type.MOTION_BLOCKING, pos.getX(), pos.getZ()));
		BlockPos at = new BlockPos(pos.getX(), Math.max(y, pos.getY() - 3), pos.getZ());
		double x = at.getX() + 0.5, z = at.getZ() + 0.5;
		for (int k = 0; k < 14; k++) {
			world.spawnParticles(ParticleTypes.FLAME, x, at.getY() + k * 0.8, z, 6, 0.25, 0.3, 0.25, 0.01);
		}
		world.spawnParticles(ParticleTypes.LAVA, x, at.getY() + 0.2, z, 12, 0.8, 0.1, 0.8, 0.0);
		world.playSound(null, at, SoundEvents.ENTITY_BLAZE_SHOOT, SoundCategory.HOSTILE, 1.2F, 0.5F);
		world.playSound(null, at, SoundEvents.ITEM_FIRECHARGE_USE, SoundCategory.HOSTILE, 1.0F, 0.6F);
		int hit = 0;
		for (LivingEntity e : world.getEntitiesByClass(LivingEntity.class, new Box(at).expand(2.5, 3, 2.5), e -> e != caster && e.isAlive()
				&& !(caster instanceof MiniBossEntity && e instanceof MiniBossEntity))) {
			var src = caster instanceof LivingEntity l ? (l instanceof PlayerEntity p ? world.getDamageSources().playerAttack(p)
					: world.getDamageSources().mobAttack(l)) : world.getDamageSources().inFire();
			if (e.damage(src, damage)) {
				hit++;
			}
			e.setOnFireFor(4.0F);
		}
		if (world.getBlockState(at).isAir() && world.getBlockState(at.down()).isSolidBlock(world, at.down()) && world.random.nextInt(3) == 0) {
			world.setBlockState(at, net.minecraft.block.Blocks.FIRE.getDefaultState());
		}
		return hit;
	}

	/** Hace aparecer a un mini jefe que va a por el jugador. */
	@Nullable
	public static <T extends MobEntity> T summon(ServerWorld world, EntityType<T> type, BlockPos at, @Nullable PlayerEntity target) {
		T mob = type.create(world);
		if (mob == null) {
			return null;
		}
		mob.refreshPositionAndAngles(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, world.random.nextFloat() * 360, 0);
		mob.initialize(world, world.getLocalDifficulty(at), SpawnReason.EVENT, null);
		if (target != null && !Curses.creative(target)) {
			mob.setTarget(target);
		}
		world.spawnEntity(mob);
		world.spawnParticles(ParticleTypes.LARGE_SMOKE, mob.getX(), mob.getBodyY(0.5), mob.getZ(), 80, 1.0, 1.5, 1.0, 0.05);
		world.playSound(null, at, SoundEvents.ENTITY_WITHER_SPAWN, SoundCategory.HOSTILE, 1.5F, 0.6F);
		return mob;
	}

	/** El trono con la Llave del Abismo: el Ángel sube del pozo. Devuelve el ángel (o null si no se pudo). */
	@Nullable
	public static AbyssAngelEntity wakeAngel(ServerWorld world, ServerPlayerEntity player, BlockPos throne) {
		if (!world.getEntitiesByClass(AbyssAngelEntity.class, new Box(throne).expand(64), e -> true).isEmpty()) {
			player.sendMessage(Text.translatable("message.evolvedwarfare.ritual.busy").formatted(Formatting.GRAY), true);
			return null;
		}
		AbyssAngelEntity angel = summon(world, ABYSS_ANGEL, throne.up(4).west(6), player);
		if (angel != null) {
			player.sendMessage(Text.translatable("message.evolvedwarfare.angel.summoned").formatted(Formatting.DARK_PURPLE, Formatting.BOLD), false);
			world.playSound(null, throne, SoundEvents.BLOCK_END_PORTAL_SPAWN, SoundCategory.HOSTILE, 1.0F, 0.5F);
		}
		return angel;
	}

	/** ¿Hay un Falso Profeta verdadero cerca? */
	public static boolean prophetNear(ServerWorld world, BlockPos pos) {
		return !world.getEntitiesByClass(FalseProphetEntity.class, new Box(pos).expand(64), e -> !e.illusion()).isEmpty();
	}

	public static RegistryKey<LootTable> loot(String path) {
		return RegistryKey.of(RegistryKeys.LOOT_TABLE, EvolvedWarfare.id(path));
	}
}
