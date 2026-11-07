package com.evolvedwarfare.wildlife;

import com.evolvedwarfare.registry.ModItems;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;
import net.minecraft.entity.EntityType;
import net.minecraft.item.Items;
import net.minecraft.recipe.Ingredient;
import net.minecraft.registry.RegistryKey;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeKeys;

/**
 * Las 20 especies de animales salvajes: tamaño, vida, velocidad, mordisco, qué son (presa, neutral o cazador), cómo
 * viven (en manada, de noche, en el agua, pastando, carroñeros, aves), qué comen (para atraerlas y criarlas), qué cazan,
 * sus sonidos (los de vanilla, con otro tono) y dónde aparecen. El modelo, la textura y lo que sueltan están en
 * tools/ew_wildlife.py.
 */
public enum WildSpecies {
	RED_DEER(Role.PREY, 0.9F, 1.7F, 16, 0.25, 0, 0x8A5836, 0xE2D2B0),
	ROE_DEER(Role.PREY, 0.7F, 1.3F, 10, 0.27, 0, 0xA06A40, 0xEEE8DE),
	MOOSE(Role.NEUTRAL, 1.4F, 2.3F, 40, 0.22, 7, 0x523C2C, 0xCEBE98),
	REINDEER(Role.PREY, 0.9F, 1.6F, 16, 0.24, 0, 0x988874, 0xE4E0D6),
	GAZELLE(Role.PREY, 0.7F, 1.4F, 10, 0.31, 0, 0xCCA26E, 0x603E28),
	MOUFLON(Role.NEUTRAL, 0.9F, 1.3F, 14, 0.25, 3, 0x8E5A38, 0xECE6DC),
	BISON(Role.NEUTRAL, 1.5F, 1.8F, 40, 0.2, 6, 0x624830, 0x2A2016),
	WILD_BOAR(Role.NEUTRAL, 0.9F, 0.9F, 16, 0.26, 4, 0x50443C, 0xEAE4D4),
	BROWN_BEAR(Role.PREDATOR, 1.4F, 1.4F, 40, 0.25, 7, 0x68482E, 0x9C7A58),
	COYOTE(Role.PREDATOR, 0.6F, 0.85F, 10, 0.3, 3, 0xAC906C, 0x7E6C58),
	LYNX(Role.PREDATOR, 0.7F, 0.9F, 12, 0.3, 4, 0xBCA07E, 0x1E1A16),
	COUGAR(Role.PREDATOR, 0.8F, 1.0F, 20, 0.32, 5, 0xC29462, 0x463224),
	HARE(Role.PREY, 0.45F, 0.6F, 4, 0.32, 0, 0x967C5A, 0xEEE8DE),
	SQUIRREL(Role.PREY, 0.35F, 0.35F, 3, 0.3, 0, 0xB65A28, 0xF0E2CE),
	RACCOON(Role.NEUTRAL, 0.6F, 0.6F, 8, 0.26, 2, 0x7A7872, 0x1E1C1C),
	BADGER(Role.NEUTRAL, 0.7F, 0.6F, 12, 0.24, 3, 0x84827C, 0xECEAE4),
	BEAVER(Role.PREY, 0.6F, 0.5F, 8, 0.22, 0, 0x6E482A, 0x3E3A38),
	DUCK(Role.PREY, 0.45F, 0.65F, 4, 0.22, 0, 0xA08E74, 0x2C703E),
	PHEASANT(Role.PREY, 0.45F, 0.7F, 4, 0.25, 0, 0xB2622C, 0x28545C),
	TURKEY(Role.PREY, 0.6F, 1.0F, 8, 0.24, 0, 0x463A30, 0xC82828);

	public enum Role {
		/** Huye de los jugadores y de los cazadores. */
		PREY,
		/** Va a lo suyo, pero si le pegas se defiende (y la manada con él). */
		NEUTRAL,
		/** Caza a otros animales cuando tiene hambre. */
		PREDATOR
	}

	public final Role role;
	public final float width;
	public final float height;
	public final double health;
	public final double speed;
	public final double damage;
	public final int eggPrimary;
	public final int eggSecondary;

	/** Distancia a la que huye de un jugador que no va agachado (0: no huye). */
	public int fleeDistance;
	public boolean herd;
	public boolean bird;
	public boolean swimmer;
	public boolean nocturnal;
	public boolean grazer;
	public boolean scavenger;
	/** Ataca a los jugadores: el puma de noche; el oso si te acercas mucho. */
	public boolean nightHunter;
	public boolean territorial;
	/** Salta al correr (liebre, ardilla). */
	public boolean hopper;
	Supplier<Ingredient> food = () -> Ingredient.ofItems(Items.WHEAT);
	/** Lo que caza (especies del mod y de vanilla). */
	List<WildSpecies> preySpecies = List.of();
	Supplier<List<EntityType<?>>> preyVanilla = List::of;
	public SoundEvent ambient = SoundEvents.ENTITY_GOAT_AMBIENT;
	public SoundEvent hurt = SoundEvents.ENTITY_GOAT_HURT;
	public SoundEvent death = SoundEvents.ENTITY_GOAT_DEATH;
	public float pitch = 1.0F;
	public List<RegistryKey<Biome>> biomes = List.of();
	public int weight = 6;
	public int minGroup = 1;
	public int maxGroup = 2;

	WildSpecies(Role role, float width, float height, double health, double speed, double damage, int eggPrimary, int eggSecondary) {
		this.role = role;
		this.width = width;
		this.height = height;
		this.health = health;
		this.speed = speed;
		this.damage = damage;
		this.eggPrimary = eggPrimary;
		this.eggSecondary = eggSecondary;
	}

	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}

	private Ingredient foodCache;
	private java.util.Set<net.minecraft.item.Item> foodItems;

	/** Lo que come (se calcula una vez: antes se creaba un ingrediente nuevo cada vez, y se mira cada tic por cada jugador). */
	public Ingredient food() {
		if (foodCache == null) {
			foodCache = food.get();
		}
		return foodCache;
	}

	/** ¿Es comida suya? (rápido: un conjunto de objetos) */
	public boolean isFood(net.minecraft.item.ItemStack stack) {
		if (stack.isEmpty()) {
			return false;
		}
		java.util.Set<net.minecraft.item.Item> items = foodItems;
		if (items == null) {
			items = new java.util.HashSet<>();
			for (net.minecraft.item.ItemStack s : food().getMatchingStacks()) {
				items.add(s.getItem());
			}
			foodItems = items;
		}
		return items.contains(stack.getItem());
	}

	/** ¿Caza a ese? */
	public boolean hunts(EntityType<?> type) {
		for (WildSpecies s : preySpecies) {
			if (Wildlife.type(s) == type) {
				return true;
			}
		}
		if (preyCache == null) {
			preyCache = preyVanilla.get();
		}
		return preyCache.contains(type);
	}

	private List<EntityType<?>> preyCache;

	/** ¿Caza a esa especie? (para que la presa huya de él) */
	public boolean hunts(WildSpecies s) {
		return preySpecies.contains(s);
	}

	private void sounds(SoundEvent ambient, SoundEvent hurt, SoundEvent death, float pitch) {
		this.ambient = ambient;
		this.hurt = hurt;
		this.death = death;
		this.pitch = pitch;
	}

	@SafeVarargs
	private void spawn(int weight, int min, int max, RegistryKey<Biome>... biomes) {
		this.weight = weight;
		this.minGroup = min;
		this.maxGroup = max;
		this.biomes = List.of(biomes);
	}

	static {
		Supplier<Ingredient> cervid = () -> Ingredient.ofItems(Items.WHEAT, Items.APPLE);
		Supplier<Ingredient> meat = () -> Ingredient.ofItems(Items.RABBIT, Items.CHICKEN, Items.MUTTON, ModItems.VENISON, ModItems.FOWL);
		Supplier<Ingredient> seeds = () -> Ingredient.ofItems(Items.WHEAT_SEEDS, Items.BEETROOT_SEEDS, ModItems.CORN_SEEDS);

		RED_DEER.fleeDistance = 14;
		RED_DEER.herd = RED_DEER.grazer = true;
		RED_DEER.food = cervid;
		RED_DEER.sounds(SoundEvents.ENTITY_GOAT_AMBIENT, SoundEvents.ENTITY_GOAT_HURT, SoundEvents.ENTITY_GOAT_DEATH, 0.8F);
		RED_DEER.spawn(8, 2, 4, BiomeKeys.FOREST, BiomeKeys.BIRCH_FOREST, BiomeKeys.FLOWER_FOREST, BiomeKeys.OLD_GROWTH_BIRCH_FOREST,
				BiomeKeys.MEADOW, BiomeKeys.PLAINS);

		ROE_DEER.fleeDistance = 12;
		ROE_DEER.herd = ROE_DEER.grazer = true;
		ROE_DEER.food = cervid;
		ROE_DEER.sounds(SoundEvents.ENTITY_GOAT_AMBIENT, SoundEvents.ENTITY_GOAT_HURT, SoundEvents.ENTITY_GOAT_DEATH, 1.2F);
		ROE_DEER.spawn(8, 1, 3, BiomeKeys.FOREST, BiomeKeys.BIRCH_FOREST, BiomeKeys.DARK_FOREST, BiomeKeys.MEADOW, BiomeKeys.FLOWER_FOREST);

		MOOSE.grazer = true;
		MOOSE.food = cervid;
		MOOSE.sounds(SoundEvents.ENTITY_COW_AMBIENT, SoundEvents.ENTITY_COW_HURT, SoundEvents.ENTITY_COW_DEATH, 0.55F);
		MOOSE.spawn(4, 1, 2, BiomeKeys.TAIGA, BiomeKeys.OLD_GROWTH_PINE_TAIGA, BiomeKeys.OLD_GROWTH_SPRUCE_TAIGA, BiomeKeys.SNOWY_TAIGA,
				BiomeKeys.SWAMP);

		REINDEER.fleeDistance = 9;
		REINDEER.herd = REINDEER.grazer = true;
		REINDEER.food = cervid;
		REINDEER.sounds(SoundEvents.ENTITY_GOAT_AMBIENT, SoundEvents.ENTITY_GOAT_HURT, SoundEvents.ENTITY_GOAT_DEATH, 0.7F);
		REINDEER.spawn(8, 3, 5, BiomeKeys.SNOWY_PLAINS, BiomeKeys.SNOWY_TAIGA, BiomeKeys.GROVE, BiomeKeys.SNOWY_SLOPES, BiomeKeys.ICE_SPIKES);

		GAZELLE.fleeDistance = 16;
		GAZELLE.herd = GAZELLE.grazer = true;
		GAZELLE.food = cervid;
		GAZELLE.sounds(SoundEvents.ENTITY_GOAT_AMBIENT, SoundEvents.ENTITY_GOAT_HURT, SoundEvents.ENTITY_GOAT_DEATH, 1.3F);
		GAZELLE.spawn(9, 3, 6, BiomeKeys.SAVANNA, BiomeKeys.SAVANNA_PLATEAU, BiomeKeys.DESERT, BiomeKeys.BADLANDS);

		MOUFLON.fleeDistance = 10;
		MOUFLON.herd = MOUFLON.grazer = true;
		MOUFLON.sounds(SoundEvents.ENTITY_SHEEP_AMBIENT, SoundEvents.ENTITY_SHEEP_HURT, SoundEvents.ENTITY_SHEEP_DEATH, 0.8F);
		MOUFLON.spawn(7, 2, 4, BiomeKeys.WINDSWEPT_HILLS, BiomeKeys.WINDSWEPT_GRAVELLY_HILLS, BiomeKeys.WINDSWEPT_FOREST, BiomeKeys.MEADOW,
				BiomeKeys.STONY_PEAKS);

		BISON.herd = BISON.grazer = true;
		BISON.sounds(SoundEvents.ENTITY_COW_AMBIENT, SoundEvents.ENTITY_COW_HURT, SoundEvents.ENTITY_COW_DEATH, 0.6F);
		BISON.spawn(6, 3, 6, BiomeKeys.PLAINS, BiomeKeys.SUNFLOWER_PLAINS, BiomeKeys.SAVANNA, BiomeKeys.MEADOW);

		WILD_BOAR.herd = true;
		WILD_BOAR.food = () -> Ingredient.ofItems(Items.CARROT, Items.POTATO, Items.BEETROOT, ModItems.CORN);
		WILD_BOAR.sounds(SoundEvents.ENTITY_HOGLIN_AMBIENT, SoundEvents.ENTITY_HOGLIN_HURT, SoundEvents.ENTITY_HOGLIN_DEATH, 1.25F);
		WILD_BOAR.spawn(7, 2, 4, BiomeKeys.FOREST, BiomeKeys.DARK_FOREST, BiomeKeys.TAIGA, BiomeKeys.OLD_GROWTH_PINE_TAIGA, BiomeKeys.BIRCH_FOREST);

		BROWN_BEAR.scavenger = BROWN_BEAR.territorial = true;
		BROWN_BEAR.food = () -> Ingredient.ofItems(Items.SWEET_BERRIES, Items.SALMON, Items.HONEY_BOTTLE);
		BROWN_BEAR.preySpecies = List.of(ROE_DEER, HARE, BEAVER);
		BROWN_BEAR.preyVanilla = () -> List.of(EntityType.SALMON, EntityType.COD, EntityType.RABBIT);
		BROWN_BEAR.sounds(SoundEvents.ENTITY_POLAR_BEAR_AMBIENT, SoundEvents.ENTITY_POLAR_BEAR_HURT, SoundEvents.ENTITY_POLAR_BEAR_DEATH, 0.85F);
		BROWN_BEAR.spawn(3, 1, 1, BiomeKeys.TAIGA, BiomeKeys.OLD_GROWTH_PINE_TAIGA, BiomeKeys.OLD_GROWTH_SPRUCE_TAIGA, BiomeKeys.GROVE,
				BiomeKeys.FOREST);

		COYOTE.nocturnal = COYOTE.scavenger = true;
		COYOTE.food = meat;
		COYOTE.preySpecies = List.of(HARE, SQUIRREL, DUCK, PHEASANT, TURKEY);
		COYOTE.preyVanilla = () -> List.of(EntityType.RABBIT, EntityType.CHICKEN);
		COYOTE.sounds(SoundEvents.ENTITY_WOLF_AMBIENT, SoundEvents.ENTITY_WOLF_HURT, SoundEvents.ENTITY_WOLF_DEATH, 1.35F);
		COYOTE.spawn(5, 1, 3, BiomeKeys.PLAINS, BiomeKeys.DESERT, BiomeKeys.BADLANDS, BiomeKeys.SAVANNA, BiomeKeys.WOODED_BADLANDS);

		LYNX.nocturnal = true;
		LYNX.food = meat;
		LYNX.preySpecies = List.of(HARE, SQUIRREL, PHEASANT, DUCK);
		LYNX.preyVanilla = () -> List.of(EntityType.RABBIT, EntityType.CHICKEN);
		LYNX.sounds(SoundEvents.ENTITY_OCELOT_AMBIENT, SoundEvents.ENTITY_OCELOT_HURT, SoundEvents.ENTITY_OCELOT_DEATH, 0.75F);
		LYNX.spawn(3, 1, 1, BiomeKeys.TAIGA, BiomeKeys.SNOWY_TAIGA, BiomeKeys.GROVE, BiomeKeys.OLD_GROWTH_PINE_TAIGA);

		COUGAR.nightHunter = true;
		COUGAR.food = meat;
		COUGAR.preySpecies = List.of(RED_DEER, ROE_DEER, GAZELLE, MOUFLON, HARE, TURKEY);
		COUGAR.preyVanilla = () -> List.of(EntityType.SHEEP, EntityType.RABBIT, EntityType.GOAT);
		COUGAR.sounds(SoundEvents.ENTITY_OCELOT_AMBIENT, SoundEvents.ENTITY_OCELOT_HURT, SoundEvents.ENTITY_OCELOT_DEATH, 0.55F);
		COUGAR.spawn(3, 1, 1, BiomeKeys.WINDSWEPT_HILLS, BiomeKeys.WINDSWEPT_FOREST, BiomeKeys.SAVANNA_PLATEAU, BiomeKeys.WOODED_BADLANDS,
				BiomeKeys.MEADOW);

		HARE.fleeDistance = 10;
		HARE.grazer = HARE.hopper = true;
		HARE.food = () -> Ingredient.ofItems(Items.CARROT, Items.DANDELION, ModItems.CABBAGE);
		HARE.sounds(SoundEvents.ENTITY_RABBIT_AMBIENT, SoundEvents.ENTITY_RABBIT_HURT, SoundEvents.ENTITY_RABBIT_DEATH, 0.9F);
		HARE.spawn(8, 1, 3, BiomeKeys.PLAINS, BiomeKeys.SNOWY_PLAINS, BiomeKeys.MEADOW, BiomeKeys.SUNFLOWER_PLAINS, BiomeKeys.SAVANNA);

		SQUIRREL.fleeDistance = 7;
		SQUIRREL.hopper = true;
		SQUIRREL.food = () -> Ingredient.ofItems(Items.SWEET_BERRIES, Items.WHEAT_SEEDS, Items.PUMPKIN_SEEDS);
		SQUIRREL.sounds(SoundEvents.ENTITY_FOX_AMBIENT, SoundEvents.ENTITY_FOX_HURT, SoundEvents.ENTITY_FOX_DEATH, 1.7F);
		SQUIRREL.spawn(9, 1, 2, BiomeKeys.FOREST, BiomeKeys.BIRCH_FOREST, BiomeKeys.DARK_FOREST, BiomeKeys.TAIGA,
				BiomeKeys.OLD_GROWTH_SPRUCE_TAIGA, BiomeKeys.FLOWER_FOREST);

		RACCOON.nocturnal = RACCOON.scavenger = true;
		RACCOON.food = () -> Ingredient.ofItems(Items.SWEET_BERRIES, Items.APPLE, Items.COD, ModItems.CORN);
		RACCOON.sounds(SoundEvents.ENTITY_FOX_AMBIENT, SoundEvents.ENTITY_FOX_HURT, SoundEvents.ENTITY_FOX_DEATH, 1.15F);
		RACCOON.spawn(5, 1, 2, BiomeKeys.FOREST, BiomeKeys.SWAMP, BiomeKeys.DARK_FOREST, BiomeKeys.BIRCH_FOREST);

		BADGER.nocturnal = BADGER.scavenger = true;
		BADGER.food = () -> Ingredient.ofItems(Items.SWEET_BERRIES, Items.BEETROOT, ModItems.CORN);
		BADGER.sounds(SoundEvents.ENTITY_FOX_SNIFF, SoundEvents.ENTITY_FOX_HURT, SoundEvents.ENTITY_FOX_DEATH, 0.7F);
		BADGER.spawn(4, 1, 2, BiomeKeys.PLAINS, BiomeKeys.FOREST, BiomeKeys.MEADOW, BiomeKeys.BIRCH_FOREST);

		BEAVER.fleeDistance = 7;
		BEAVER.swimmer = true;
		BEAVER.food = () -> Ingredient.ofItems(Items.STICK, Items.SUGAR_CANE);
		BEAVER.sounds(SoundEvents.ENTITY_FOX_SNIFF, SoundEvents.ENTITY_FOX_HURT, SoundEvents.ENTITY_FOX_DEATH, 0.9F);
		BEAVER.spawn(6, 1, 2, BiomeKeys.RIVER, BiomeKeys.SWAMP, BiomeKeys.TAIGA, BiomeKeys.OLD_GROWTH_SPRUCE_TAIGA);

		DUCK.fleeDistance = 7;
		DUCK.herd = DUCK.bird = DUCK.swimmer = DUCK.grazer = true;
		DUCK.food = seeds;
		DUCK.sounds(SoundEvents.ENTITY_CHICKEN_AMBIENT, SoundEvents.ENTITY_CHICKEN_HURT, SoundEvents.ENTITY_CHICKEN_DEATH, 0.75F);
		DUCK.spawn(8, 2, 4, BiomeKeys.RIVER, BiomeKeys.SWAMP, BiomeKeys.MANGROVE_SWAMP, BiomeKeys.PLAINS);

		PHEASANT.fleeDistance = 9;
		PHEASANT.bird = PHEASANT.grazer = true;
		PHEASANT.food = seeds;
		PHEASANT.sounds(SoundEvents.ENTITY_CHICKEN_AMBIENT, SoundEvents.ENTITY_CHICKEN_HURT, SoundEvents.ENTITY_CHICKEN_DEATH, 1.3F);
		PHEASANT.spawn(7, 1, 3, BiomeKeys.PLAINS, BiomeKeys.FOREST, BiomeKeys.FLOWER_FOREST, BiomeKeys.MEADOW, BiomeKeys.SUNFLOWER_PLAINS);

		TURKEY.fleeDistance = 7;
		TURKEY.herd = TURKEY.bird = TURKEY.grazer = true;
		TURKEY.food = seeds;
		TURKEY.sounds(SoundEvents.ENTITY_CHICKEN_AMBIENT, SoundEvents.ENTITY_CHICKEN_HURT, SoundEvents.ENTITY_CHICKEN_DEATH, 0.6F);
		TURKEY.spawn(6, 2, 4, BiomeKeys.FOREST, BiomeKeys.BIRCH_FOREST, BiomeKeys.DARK_FOREST, BiomeKeys.TAIGA);
	}
}
