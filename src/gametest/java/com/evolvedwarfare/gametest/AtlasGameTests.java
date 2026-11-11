package com.evolvedwarfare.gametest;

import com.evolvedwarfare.atlas.Atlas;
import com.evolvedwarfare.atlas.AtlasState;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.GameTestException;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.WorldChunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** El Atlas: cómo dibuja un chunk (el agua, más oscura) y las reglas para reclamar territorio. */
public class AtlasGameTests implements FabricGameTest {
	private static final Logger LOG = LoggerFactory.getLogger("ew-gametest");

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void atlasDrawsWaterDarker(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		BlockPos o = ctx.getAbsolutePos(new BlockPos(0, 0, 0));
		ChunkPos cp = new ChunkPos(o.add(16, 0, 16));
		int y = o.getY();
		BlockPos base = new BlockPos(cp.getStartX(), y, cp.getStartZ());
		for (BlockPos p : BlockPos.iterate(base, base.add(15, 6, 15))) {
			world.setBlockState(p, p.getY() == y ? Blocks.STONE.getDefaultState() : Blocks.AIR.getDefaultState());
		}
		// un estanque de 3 de hondo en la mitad de abajo del chunk
		for (BlockPos p : BlockPos.iterate(base.add(0, 0, 8), base.add(15, 0, 15))) {
			world.setBlockState(p.down(3), Blocks.STONE.getDefaultState());
			for (int d = 0; d < 3; d++) {
				world.setBlockState(p.down(d), Blocks.WATER.getDefaultState());
			}
		}
		WorldChunk chunk = world.getChunk(cp.x, cp.z);
		byte[] img = Atlas.scan(world, chunk);
		int land = img[3 * 16 + 5] & 255, water = img[12 * 16 + 5] & 255;
		LOG.info("[EW-GAMETEST] atlas: piedra {}, agua {}", land, water);
		if (img.length != 256 || water >= land) {
			throw new GameTestException("El Atlas no dibuja bien el agua: piedra " + land + ", agua " + water);
		}
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void claimingTerritories(TestContext ctx) {
		AtlasState state = new AtlasState();
		UUID me = UUID.randomUUID(), other = UUID.randomUUID();
		for (int x = 0; x < 6; x++) {
			for (int z = 0; z < 6; z++) {
				state.explored(me).add(ChunkPos.toLong(x, z));
				state.explored(other).add(ChunkPos.toLong(x, z));
			}
		}
		int[] wallet = {1000};
		java.util.function.IntPredicate pay = cost -> {
			if (wallet[0] < cost) {
				return false;
			}
			wallet[0] -= cost;
			return true;
		};
		String first = Atlas.claim(state, me, "Yo", "Valdemora", new long[] {ChunkPos.toLong(0, 0), ChunkPos.toLong(1, 0)}, false, pay);
		String apart = Atlas.claim(state, me, "Yo", "Lejos", new long[] {ChunkPos.toLong(4, 4), ChunkPos.toLong(4, 2)}, false, pay);
		String grow = Atlas.claim(state, me, "Yo", "valdemora", new long[] {ChunkPos.toLong(1, 1)}, false, pay);
		String taken = Atlas.claim(state, other, "Otro", "Suyo", new long[] {ChunkPos.toLong(1, 1)}, false, pay);
		String unexplored = Atlas.claim(state, other, "Otro", "Suyo", new long[] {ChunkPos.toLong(20, 20)}, false, pay);
		wallet[0] = 5;
		String poor = Atlas.claim(state, other, "Otro", "Suyo", new long[] {ChunkPos.toLong(5, 5)}, false, pay);
		String noName = Atlas.claim(state, other, "Otro", "  ", new long[] {ChunkPos.toLong(5, 5)}, false, pay);
		int territories = state.territories().size(), mine = state.territories().isEmpty() ? 0 : state.territories().get(0).chunks.size();
		LOG.info("[EW-GAMETEST] territorios: primero {}, separado {}, ampliar {}, de otro {}, sin explorar {}, sin monedas {}, sin nombre {}, {} territorio(s), "
				+ "{} chunks, quedan {}", first, apart, grow, taken, unexplored, poor, noName, territories, mine, wallet[0]);
		boolean ok = first == null && "message.evolvedwarfare.atlas.not_connected".equals(apart) && grow == null
				&& "message.evolvedwarfare.atlas.taken".equals(taken) && "message.evolvedwarfare.atlas.unexplored".equals(unexplored)
				&& "message.evolvedwarfare.atlas.no_coins".equals(poor) && "message.evolvedwarfare.atlas.bad_name".equals(noName) && territories == 1
				&& mine == 3 && wallet[0] == 5;
		if (!ok) {
			throw new GameTestException("Las reglas de los territorios fallan");
		}
		ctx.complete();
	}
}
