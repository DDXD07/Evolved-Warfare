package com.evolvedwarfare.gametest;

import com.evolvedwarfare.EvolvedWarfare;
import com.evolvedwarfare.hell.FallenBladeItem;
import com.evolvedwarfare.hell.HellStructures;
import com.evolvedwarfare.hell.StructureBlocks;
import com.evolvedwarfare.registry.ModEntities;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.BlockState;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.HuskEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.loot.LootTable;
import net.minecraft.loot.context.LootContextParameterSet;
import net.minecraft.loot.context.LootContextParameters;
import net.minecraft.loot.context.LootContextTypes;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.GameTestException;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.GameMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Las estructuras del Nether bíblico (fase 35): plantillas, altares, suelos, runas, dormidos, el trono, los libros y las espadas. */
public class HellStructureGameTests implements FabricGameTest {
	private static final Logger LOG = LoggerFactory.getLogger("ew-gametest");
	private static final String[] TEMPLATES = {"babel_tower", "false_temple", "salt_pillar_field", "fallen_graveyard", "lake_throne", "abyss_vault",
			"sulfur_mine", "sodom_house_0", "sodom_house_1", "sodom_house_2", "sodom_temple"};
	private static final String[] STRUCTURES = {"babel_tower", "false_temple", "salt_pillar_field", "fallen_graveyard", "lake_throne", "abyss_vault",
			"sulfur_mine", "sodom_house", "sodom_temple"};

	private static void check(boolean ok, String message) {
		if (!ok) {
			throw new GameTestException(message);
		}
	}

	private static ServerPlayerEntity survivor(TestContext ctx) {
		ServerPlayerEntity player = (ServerPlayerEntity) ctx.createMockCreativeServerPlayerInWorld();
		player.changeGameMode(GameMode.SURVIVAL);
		player.getInventory().clear();
		var at = ctx.getAbsolute(new net.minecraft.util.math.Vec3d(1.5, 2, 1.5));
		player.refreshPositionAndAngles(at.x, at.y, at.z, 0, 0);
		return player;
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void hellStructuresExist(TestContext ctx) {
		var server = ctx.getWorld().getServer();
		int templates = 0, structures = 0;
		for (String t : TEMPLATES) {
			templates += server.getStructureTemplateManager().getTemplate(EvolvedWarfare.id("hell/" + t)).isPresent() ? 1 : 0;
		}
		var registry = server.getRegistryManager().get(RegistryKeys.STRUCTURE);
		for (String s : STRUCTURES) {
			structures += registry.contains(RegistryKey.of(RegistryKeys.STRUCTURE, EvolvedWarfare.id(s))) ? 1 : 0;
		}
		var tower = server.getStructureTemplateManager().getTemplate(EvolvedWarfare.id("hell/babel_tower")).orElseThrow();
		LOG.info("[EW-GAMETEST] estructuras del Nether: {}/{} plantillas, {}/{} estructuras, torre {}", templates, TEMPLATES.length, structures,
				STRUCTURES.length, tower.getSize());
		check(templates == TEMPLATES.length && structures == STRUCTURES.length && tower.getSize().getY() > 80, "Faltan estructuras del Nether");
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void altarTakesOfferings(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		ServerPlayerEntity player = survivor(ctx);
		BlockPos pos = ctx.getAbsolutePos(new BlockPos(1, 1, 1));
		world.setBlockState(pos, HellStructures.FALSE_ALTAR.getDefaultState());
		boolean values = StructureBlocks.Altar.value(new ItemStack(Items.GOLD_INGOT)) == 3 && StructureBlocks.Altar.value(new ItemStack(Items.BREAD)) == 1
				&& StructureBlocks.Altar.value(new ItemStack(Items.DIAMOND_SWORD)) == 3 && StructureBlocks.Altar.value(new ItemStack(Items.BOOK)) == 2
				&& StructureBlocks.Altar.value(new ItemStack(Items.DIRT)) == 0;
		Random random = Random.create(7);
		int boons = 0, curses = 0;
		for (int i = 0; i < 40; i++) {
			if (StructureBlocks.Altar.offer(world, player, pos, 3, random)) {
				boons++;
			} else {
				curses++;
			}
		}
		boolean cursed = com.evolvedwarfare.hell.Curses.all().stream().anyMatch(player::hasStatusEffect);
		StructureBlocks.Altar.purify(world, player, pos);
		boolean clean = com.evolvedwarfare.hell.Curses.all().stream().noneMatch(player::hasStatusEffect);
		LOG.info("[EW-GAMETEST] altar: valores {}, {} dones y {} maldiciones, maldito {}, purificado {}", values, boons, curses, cursed, clean);
		check(values && boons > 0 && curses > 0 && cursed && clean, "El altar no acepta ofrendas como debe");
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 80)
	public void floorCrumbles(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		BlockPos a = ctx.getAbsolutePos(new BlockPos(1, 2, 1));
		BlockPos b = a.east();
		world.setBlockState(a, HellStructures.CRUMBLING_FLOOR.getDefaultState());
		world.setBlockState(b, HellStructures.CRUMBLING_FLOOR.getDefaultState());
		StructureBlocks.Crumbling.crack(world, a);
		ctx.runAtTick(5, () -> check(world.getBlockState(a).isOf(HellStructures.CRUMBLING_FLOOR), "El suelo se hundió al momento"));
		ctx.runAtTick(50, () -> {
			LOG.info("[EW-GAMETEST] suelo agrietado: pisado {}, vecino {}", world.getBlockState(a).isAir(), world.getBlockState(b).isAir());
			check(world.getBlockState(a).isAir() && world.getBlockState(b).isAir(), "El suelo agrietado no se hunde");
			ctx.complete();
		});
	}

	@GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 100)
	public void runesAndBlocksShift(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		BlockPos c = ctx.getAbsolutePos(new BlockPos(4, 2, 4));
		for (int i = 0; i < 6; i++) {
			world.setBlockState(c.add(i - 3, 1, 2), HellStructures.BABEL_RUNE.getDefaultState());
		}
		BlockPos s = c.add(0, 2, -3);
		world.setBlockState(s, HellStructures.SHIFTING_BLOCK.getDefaultState());
		boolean[] first = new boolean[1];
		int[] changedRunes = new int[1];
		ctx.runAtTick(2, () -> {
			HellStructures.scan(world, c);
			HellStructures.scan(world, c);
			HellStructures.scan(world, c);
			for (int i = 0; i < 6; i++) {
				changedRunes[0] += world.getBlockState(c.add(i - 3, 1, 2)).get(StructureBlocks.Rune.GLYPH) != 0 ? 1 : 0;
			}
			first[0] = world.getBlockState(s).get(StructureBlocks.Shifting.SOLID);
		});
		ctx.runAtTick(62, () -> {
			HellStructures.scan(world, c);
			BlockState after = world.getBlockState(s);
			boolean second = after.get(StructureBlocks.Shifting.SOLID);
			boolean noCollision = second || after.getCollisionShape(world, s).isEmpty();
			LOG.info("[EW-GAMETEST] Babel: {} runas cambiadas, bloque {} -> {} (atravesable {})", changedRunes[0], first[0], second, noCollision);
			check(changedRunes[0] > 0 && first[0] != second && noCollision, "Las runas o los bloques cambiantes no cambian");
			ctx.complete();
		});
	}

	@GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 40)
	public void sleepersWake(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		HuskEntity husk = ctx.spawnEntity(EntityType.HUSK, 2, 2, 2);
		husk.setAiDisabled(true);
		husk.addCommandTag(HellStructures.DORMANT);
		int far = HellStructures.wakeNear(world, husk.getPos().add(20, 0, 0), 5, null);
		int near = HellStructures.wakeNear(world, husk.getPos().add(2, 0, 0), 5, null);
		LOG.info("[EW-GAMETEST] dormidos: lejos {}, cerca {}, IA {}", far, near, !husk.isAiDisabled());
		check(far == 0 && near == 1 && !husk.isAiDisabled() && !husk.getCommandTags().contains(HellStructures.DORMANT), "Los dormidos no se despiertan");
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void throneCountsDeaths(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		ServerPlayerEntity player = survivor(ctx);
		BlockPos throne = ctx.getAbsolutePos(new BlockPos(1, 2, 1));
		world.setBlockState(throne, HellStructures.ABYSS_THRONE.getDefaultState());
		BlockPos wall = ctx.getAbsolutePos(new BlockPos(4, 2, 1));
		world.setBlockState(wall, HellStructures.SEALED_WALL.getDefaultState());
		world.setBlockState(wall.up(), HellStructures.SEALED_WALL.getDefaultState());
		int refused = HellStructures.useThrone(world, player, throne);
		boolean stillSealed = world.getBlockState(wall).isOf(HellStructures.SEALED_WALL);
		for (int i = 0; i < HellStructures.THRONE_DEATHS; i++) {
			HellStructures.addNetherDeath(player);
		}
		int opened = HellStructures.useThrone(world, player, throne);
		LOG.info("[EW-GAMETEST] trono: sin muertes {}, sellado {}, con {} muertes abre {}", refused, stillSealed, HellStructures.netherDeaths(player), opened);
		check(refused == -1 && stillSealed && opened == 2 && world.getBlockState(wall).isAir(), "El trono no cuenta las muertes");
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "condemnation", tickLimit = 40)
	public void condemnationWeakensLords(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		ServerPlayerEntity player = survivor(ctx);
		var boss = ctx.spawnEntity(ModEntities.MOLOCH, 4, 2, 4);
		boss.setAiDisabled(true);
		float before = boss.getHealth();
		int weakened = HellStructures.read(world, player);
		LOG.info("[EW-GAMETEST] libro de condena: {} señores, vida {} -> {}, lector débil {}", weakened, before, boss.getHealth(),
				player.hasStatusEffect(StatusEffects.WEAKNESS));
		check(weakened == 1 && boss.getHealth() < before && boss.hasStatusEffect(StatusEffects.WEAKNESS) && player.hasStatusEffect(StatusEffects.WEAKNESS),
				"El libro de condena no debilita");
		boss.discard();
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 40)
	public void fallenSwordPulls(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		ServerPlayerEntity player = survivor(ctx);
		BlockPos pos = ctx.getAbsolutePos(new BlockPos(2, 2, 2));
		world.setBlockState(pos, HellStructures.FALLEN_SWORD.getDefaultState());
		var fallen = ctx.spawnEntity(com.evolvedwarfare.hell.DamnedEntities.FALLEN, 5, 2, 2);
		fallen.setAiDisabled(true);
		fallen.addCommandTag(HellStructures.DORMANT);
		ItemStack blade = StructureBlocks.EmbeddedSword.pull(world, player, pos);
		boolean named = true;
		for (int k = 0; k < FallenBladeItem.KINDS.length; k++) {
			named &= FallenBladeItem.kind(FallenBladeItem.of(k)) == k;
		}
		LOG.info("[EW-GAMETEST] espada clavada: {} ({}), bloque {}, caído despierto {}", blade.getItem(), FallenBladeItem.kind(blade),
				world.getBlockState(pos).isAir(), !fallen.isAiDisabled());
		check(blade.isOf(HellStructures.FALLEN_BLADE) && named && world.getBlockState(pos).isAir() && !fallen.isAiDisabled()
				&& player.getInventory().contains(blade), "La espada no se arranca");
		fallen.discard();
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void loreBooksInChests(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		LootTable table = world.getServer().getReloadableRegistries().getLootTable(RegistryKey.of(RegistryKeys.LOOT_TABLE, EvolvedWarfare.id("chests/lore")));
		LootContextParameterSet params = new LootContextParameterSet.Builder(world).add(LootContextParameters.ORIGIN, ctx.getAbsolute(new BlockPos(1, 1, 1)
				.toCenterPos())).build(LootContextTypes.CHEST);
		int books = 0, pages = 0;
		for (int i = 0; i < 10; i++) {
			for (ItemStack s : table.generateLoot(params)) {
				var content = s.get(DataComponentTypes.WRITTEN_BOOK_CONTENT);
				if (s.isOf(Items.WRITTEN_BOOK) && content != null) {
					books++;
					pages += content.pages().size();
				}
			}
		}
		LOG.info("[EW-GAMETEST] libros de saber: {} libros, {} páginas", books, pages);
		check(table != LootTable.EMPTY && books > 0 && pages == books * 2, "No salen libros de saber");
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 40)
	public void bladeCurses(TestContext ctx) {
		ServerPlayerEntity player = survivor(ctx);
		var target = ctx.spawnEntity(EntityType.PIG, 3, 2, 3);
		target.setAiDisabled(true);
		ItemStack plague = FallenBladeItem.of(FallenBladeItem.PLAGUE);
		plague.getItem().postHit(plague, target, player);
		ItemStack wrath = FallenBladeItem.of(FallenBladeItem.WRATH);
		wrath.getItem().postHit(wrath, target, player);
		LOG.info("[EW-GAMETEST] hojas caídas: veneno {}, fuego {}", target.hasStatusEffect(StatusEffects.POISON), target.isOnFire());
		check(target.hasStatusEffect(StatusEffects.POISON) && target.isOnFire(), "Las hojas caídas no hacen nada");
		target.discard();
		ctx.complete();
	}
}
