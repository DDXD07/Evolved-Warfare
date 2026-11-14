package com.evolvedwarfare.gametest;

import com.evolvedwarfare.EvolvedWarfare;
import com.evolvedwarfare.world.dungeon.Bosses;
import com.evolvedwarfare.world.dungeon.DungeonGateBlock;
import com.evolvedwarfare.world.dungeon.DungeonHeartBlockEntity;
import com.evolvedwarfare.world.dungeon.DungeonPiece;
import com.evolvedwarfare.world.dungeon.Dungeons;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.CowEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.loot.LootTable;
import net.minecraft.loot.context.LootContextParameterSet;
import net.minecraft.loot.context.LootContextParameters;
import net.minecraft.loot.context.LootContextTypes;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.structure.StructureTemplate;
import net.minecraft.test.GameTest;
import net.minecraft.test.GameTestException;
import net.minecraft.test.TestContext;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.random.Random;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Las mazmorras (fase 66): las plantillas, el botín, el jefe que despierta, las rejas y el cofre del final. */
public class DungeonGameTests implements FabricGameTest {
	private static final Logger LOG = LoggerFactory.getLogger("ew-gametest");
	private static final int VARIANTS = 3;

	private static void check(boolean ok, String message) {
		if (!ok) {
			throw new GameTestException(message);
		}
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "dungeon_templates")
	public void dungeonTemplatesAreComplete(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		var server = world.getServer();
		var structures = server.getRegistryManager().get(RegistryKeys.STRUCTURE);
		var sets = server.getRegistryManager().get(RegistryKeys.STRUCTURE_SET);
		List<String> bad = new ArrayList<>();
		for (String id : Dungeons.IDS) {
			if (!structures.contains(RegistryKey.of(RegistryKeys.STRUCTURE, EvolvedWarfare.id("dungeon_" + id)))) {
				bad.add(id + ": sin estructura");
			}
			for (int k = 0; k < VARIANTS; k++) {
				var opt = server.getStructureTemplateManager().getTemplate(EvolvedWarfare.id("dungeon/" + id + "_" + k));
				if (opt.isEmpty()) {
					bad.add(id + "_" + k + ": sin plantilla");
					continue;
				}
				StructureTemplate t = opt.get();
				var data = DungeonPiece.placement(BlockRotation.NONE, BlockPos.ORIGIN);
				var hearts = t.getInfosForBlock(BlockPos.ORIGIN, data, Dungeons.HEART);
				int spawners = t.getInfosForBlock(BlockPos.ORIGIN, data, Blocks.SPAWNER).size();
				int chests = t.getInfosForBlock(BlockPos.ORIGIN, data, Blocks.CHEST).size() + t.getInfosForBlock(BlockPos.ORIGIN, data, Blocks.BARREL).size();
				int gates = t.getInfosForBlock(BlockPos.ORIGIN, data, Dungeons.GATE).size();
				boolean named = hearts.size() == 1 && hearts.get(0).nbt() != null && id.equals(hearts.get(0).nbt().getString("Dungeon"));
				LOG.info("[EW-GAMETEST] mazmorra {}_{}: {} , corazón {}, {} generadores, {} cofres, {} rejas", id, k, t.getSize(), named, spawners, chests,
						gates);
				if (!named || spawners < 4 || chests < 2 || gates < 1) {
					bad.add(id + "_" + k);
				}
			}
			LootTable table = server.getReloadableRegistries().getLootTable(RegistryKey.of(RegistryKeys.LOOT_TABLE,
					EvolvedWarfare.id("chests/dungeon/" + id + "/boss")));
			LootContextParameterSet params = new LootContextParameterSet.Builder(world).add(LootContextParameters.ORIGIN,
					ctx.getAbsolute(new BlockPos(1, 1, 1).toCenterPos())).build(LootContextTypes.CHEST);
			List<ItemStack> loot = table.generateLoot(params);
			boolean relic = loot.stream().anyMatch(s -> s.contains(DataComponentTypes.CUSTOM_NAME));
			LOG.info("[EW-GAMETEST] botín del jefe de {}: {} cosas, reliquia {}", id, loot.size(), relic);
			if (loot.size() < 6 || !relic) {
				bad.add(id + ": botín");
			}
		}
		for (String s : new String[] {"dungeons_surface", "dungeons_underground"}) {
			if (!sets.contains(RegistryKey.of(RegistryKeys.STRUCTURE_SET, EvolvedWarfare.id(s)))) {
				bad.add(s);
			}
		}
		check(bad.isEmpty(), "Mazmorras incompletas: " + bad);
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "dungeon_boss", tickLimit = 240)
	public void dungeonBossWakesAndPays(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		BlockPos o = ctx.getAbsolutePos(new BlockPos(0, 2, 0)).add(0, 0, 1100);
		List<ChunkPos> forced = new ArrayList<>();
		for (int x = -16; x <= Dungeons.IDS.size() * 40 + 16; x += 16) {
			for (int z = -16; z <= 16; z += 16) {
				ChunkPos c = new ChunkPos(o.add(x, 0, z));
				forced.add(c);
				world.setChunkForced(c.x, c.z, true);
			}
		}
		List<BlockPos> hearts = new ArrayList<>();
		List<BlockPos> gates = new ArrayList<>();
		for (int i = 0; i < Dungeons.IDS.size(); i++) {
			BlockPos h = o.add(i * 40, 0, 0);
			for (BlockPos p : BlockPos.iterate(h.add(-9, -1, -9), h.add(9, 10, 9))) {
				world.setBlockState(p, p.getY() == h.getY() - 1 ? Blocks.STONE.getDefaultState() : Blocks.AIR.getDefaultState());
			}
			world.setBlockState(h, Dungeons.HEART.getDefaultState());
			BlockPos g = h.add(6, 0, 0);
			world.setBlockState(g, Dungeons.GATE.getDefaultState());
			if (world.getBlockEntity(h) instanceof DungeonHeartBlockEntity heart) {
				heart.setDungeon(Dungeons.IDS.get(i));
				heart.start(world);
			}
			hearts.add(h);
			gates.add(g);
		}
		int[] ok = new int[3];
		List<String> problems = new ArrayList<>();
		ctx.runAtTick(10, () -> {
			for (int i = 0; i < hearts.size(); i++) {
				String id = Dungeons.IDS.get(i);
				if (!(world.getBlockEntity(hearts.get(i)) instanceof DungeonHeartBlockEntity heart)) {
					problems.add(id + ": sin corazón");
					continue;
				}
				MobEntity boss = heart.bossEntity(world);
				Bosses.Boss spec = Bosses.of(id);
				boolean closed = world.getBlockState(gates.get(i)).get(DungeonGateBlock.CLOSED);
				if (boss == null || spec == null) {
					problems.add(id + ": sin jefe");
					continue;
				}
				double scale = boss.getAttributeValue(EntityAttributes.GENERIC_SCALE);
				LOG.info("[EW-GAMETEST] jefe de {}: {} vida {}/{} escala {} rejas cerradas {}", id, boss.getType().getUntranslatedName(), boss.getHealth(),
						boss.getMaxHealth(), scale, closed);
				if (Math.abs(boss.getMaxHealth() - spec.health()) < 1 && Math.abs(scale - spec.scale()) < 0.01 && closed && boss.hasCustomName()) {
					ok[0]++;
				}
				// cada poder, contra una vaca (que no rompa nada)
				CowEntity cow = EntityType.COW.create(world);
				cow.refreshPositionAndAngles(boss.getX() + 3, boss.getY(), boss.getZ(), 0, 0);
				world.spawnEntity(cow);
				try {
					for (Bosses.Power p : Bosses.Power.values()) {
						p.use(world, boss, cow, spec, heart);
					}
					ok[1]++;
				} catch (RuntimeException e) {
					LOG.error("[EW-GAMETEST] un poder falló en {}", id, e);
					problems.add(id + ": poder " + e);
				}
				boss.kill();
			}
		});
		ctx.runAtTick(40, () -> {
			for (int i = 0; i < hearts.size(); i++) {
				String id = Dungeons.IDS.get(i);
				boolean chest = world.getBlockEntity(hearts.get(i)) instanceof ChestBlockEntity c && c.getLootTable() != null
						&& c.getLootTable().getValue().getPath().equals("chests/dungeon/" + id + "/boss");
				boolean open = !world.getBlockState(gates.get(i)).get(DungeonGateBlock.CLOSED);
				LOG.info("[EW-GAMETEST] tras el jefe de {}: cofre {}, rejas abiertas {}", id, chest, open);
				if (chest && open) {
					ok[2]++;
				} else {
					problems.add(id + ": final");
				}
			}
			for (var e : world.getEntitiesByClass(MobEntity.class, new Box(o.add(-20, -10, -20).toCenterPos(), o.add(Dungeons.IDS.size() * 40 + 20, 20, 20)
					.toCenterPos()), m -> true)) {
				e.discard();
			}
			forced.forEach(c -> world.setChunkForced(c.x, c.z, false));
			int n = Dungeons.IDS.size();
			check(ok[0] == n && ok[1] == n && ok[2] == n, "Los jefes de las mazmorras no van bien: " + ok[0] + "/" + ok[1] + "/" + ok[2] + " " + problems);
			ctx.complete();
		});
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "dungeon_place", tickLimit = 100)
	public void dungeonPlacesWithItsHeart(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		var server = world.getServer();
		BlockPos o = ctx.getAbsolutePos(new BlockPos(0, 2, 0)).add(300, 0, 1300);
		StructureTemplate t = server.getStructureTemplateManager().getTemplate(EvolvedWarfare.id("dungeon/spider_nest_0")).orElseThrow();
		List<ChunkPos> forced = new ArrayList<>();
		for (int x = -16; x <= t.getSize().getX() + 16; x += 16) {
			for (int z = -16; z <= t.getSize().getZ() + 16; z += 16) {
				ChunkPos c = new ChunkPos(o.add(x, 0, z));
				forced.add(c);
				world.setChunkForced(c.x, c.z, true);
			}
		}
		BlockPos at = o.add(0, -t.getSize().getY() + 10, 0);
		var data = DungeonPiece.placement(BlockRotation.CLOCKWISE_90, new BlockPos(t.getSize().getX() / 2, 0, t.getSize().getZ() / 2));
		t.place(world, at, at, data, Random.create(3), 2);
		var hearts = t.getInfosForBlock(at, data, Dungeons.HEART);
		var spawners = t.getInfosForBlock(at, data, Blocks.SPAWNER);
		boolean heart = !hearts.isEmpty() && world.getBlockEntity(hearts.get(0).pos()) instanceof DungeonHeartBlockEntity h && "spider_nest".equals(h.dungeon());
		long spawnersOk = spawners.stream().filter(s -> world.getBlockState(s.pos()).isOf(Blocks.SPAWNER)).count();
		LOG.info("[EW-GAMETEST] mazmorra colocada: corazón {}, generadores {}/{}", heart, spawnersOk, spawners.size());
		forced.forEach(c -> world.setChunkForced(c.x, c.z, false));
		check(heart && spawnersOk == spawners.size() && spawnersOk >= 4, "La mazmorra no se coloca bien");
		ctx.complete();
	}
}
