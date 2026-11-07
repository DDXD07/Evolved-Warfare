package com.evolvedwarfare.gametest;

import com.evolvedwarfare.registry.ModItems;
import com.evolvedwarfare.wildlife.WildAnimalEntity;
import com.evolvedwarfare.wildlife.WildSpecies;
import com.evolvedwarfare.wildlife.Wildlife;
import java.util.List;
import java.util.stream.Collectors;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.GameTestException;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Los animales salvajes: las 20 especies aparecen con su vida, se crían con su comida, cazan a quien deben y sueltan lo suyo. */
public class WildlifeGameTests implements FabricGameTest {
	private static final Logger LOG = LoggerFactory.getLogger("ew-gametest");

	private static void check(boolean ok, String message) {
		if (!ok) {
			throw new GameTestException(message);
		}
	}

	/** Lo que seguro suelta cada una al morir (al menos una cosa de esto). */
	private static Item[] expected(WildSpecies s) {
		return switch (s) {
			case RED_DEER, ROE_DEER, MOOSE, REINDEER, GAZELLE -> new Item[] {ModItems.VENISON, ModItems.COOKED_VENISON};
			case BISON, WILD_BOAR, BROWN_BEAR -> new Item[] {ModItems.GAME_MEAT, ModItems.COOKED_GAME_MEAT};
			case DUCK, PHEASANT, TURKEY -> new Item[] {ModItems.FOWL, ModItems.COOKED_FOWL};
			case MOUFLON -> new Item[] {Items.MUTTON, Items.COOKED_MUTTON};
			case HARE -> new Item[] {Items.RABBIT, Items.COOKED_RABBIT};
			case LYNX, COUGAR, BEAVER -> new Item[] {ModItems.FUR_PELT};
			default -> new Item[0];
		};
	}

	@GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 100)
	public void everySpeciesLivesAndDrops(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) {
				ctx.setBlockState(x, 0, z, Blocks.GRASS_BLOCK);
			}
		}
		StringBuilder log = new StringBuilder();
		for (WildSpecies s : WildSpecies.values()) {
			EntityType<WildAnimalEntity> type = Wildlife.type(s);
			WildAnimalEntity animal = ctx.spawnEntity(type, new BlockPos(4, 1, 4));
			check(animal.species() == s, "La especie no es la suya: " + s);
			check(Math.abs(animal.getMaxHealth() - s.health) < 0.01, "Vida de " + s + ": " + animal.getMaxHealth());
			check(animal.getAttributeValue(EntityAttributes.GENERIC_MOVEMENT_SPEED) > 0.1, "Sin velocidad: " + s);
			ItemStack food = s.food().getMatchingStacks()[0];
			check(animal.isBreedingItem(food), s + " no se cría con " + food);
			PassiveEntity child = animal.createChild(world, animal);
			check(child != null && child.getType() == type, "Las crías de " + s + " no son de su especie");
			check(!s.biomes.isEmpty(), s + " no aparece en ningún bioma");
			animal.damage(world.getDamageSources().generic(), 1000.0F);
			Box box = new Box(ctx.getAbsolutePos(new BlockPos(4, 1, 4))).expand(3.0);
			List<ItemEntity> drops = world.getEntitiesByClass(ItemEntity.class, box, e -> true);
			// los de caza se quedan en el suelo (para despellejarlos) con lo que soltarían guardado
			for (var c : world.getEntitiesByClass(com.evolvedwarfare.hunting.CarcassEntity.class, box, e -> true)) {
				for (ItemStack l : c.loot()) {
					drops.add(new ItemEntity(world, c.getX(), c.getY(), c.getZ(), l.copy()));
				}
				c.discard();
			}
			String names = drops.stream().map(e -> e.getStack().toString()).collect(Collectors.joining(", "));
			Item[] must = expected(s);
			if (must.length > 0) {
				boolean ok = drops.stream().anyMatch(e -> {
					for (Item i : must) {
						if (e.getStack().isOf(i)) {
							return true;
						}
					}
					return false;
				});
				check(ok, s + " no suelta lo suyo: " + names);
			}
			log.append(s.id()).append(" [").append(names).append("] ");
			drops.forEach(ItemEntity::discard);
		}
		LOG.info("[EW-GAMETEST] animales salvajes: {}", log);
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void huntersAndPrey(TestContext ctx) {
		check(WildSpecies.COUGAR.hunts(Wildlife.type(WildSpecies.RED_DEER)), "El puma no caza ciervos");
		check(WildSpecies.COUGAR.hunts(WildSpecies.GAZELLE), "La gacela no huye del puma");
		check(WildSpecies.COYOTE.hunts(EntityType.RABBIT) && WildSpecies.COYOTE.hunts(EntityType.CHICKEN), "El coyote no caza conejos ni gallinas");
		check(!WildSpecies.LYNX.hunts(Wildlife.type(WildSpecies.BISON)), "El lince caza bisontes");
		check(WildSpecies.BROWN_BEAR.hunts(EntityType.SALMON), "El oso no pesca salmones");
		for (WildSpecies s : WildSpecies.values()) {
			check(s.role != WildSpecies.Role.PREDATOR || s.damage > 0, "Un cazador sin mordisco: " + s);
			check(s.role != WildSpecies.Role.PREY || s.fleeDistance > 0, "Una presa que no huye: " + s);
		}
		LOG.info("[EW-GAMETEST] caza: puma, coyote, lince y oso cazan lo suyo; las presas huyen");
		ctx.complete();
	}
}
