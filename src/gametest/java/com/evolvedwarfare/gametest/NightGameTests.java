package com.evolvedwarfare.gametest;

import com.evolvedwarfare.config.EwConfig;
import com.evolvedwarfare.night.GapJumpGoal;
import com.evolvedwarfare.night.NightThreat;
import com.evolvedwarfare.night.ZombieSiegeGoal;
import com.evolvedwarfare.registry.ModEffects;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.GameTestException;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** La noche más dura: los zombis rompen, excavan y saltan, las hordas de zombis, y la infección. */
public class NightGameTests implements FabricGameTest {
	private static final Logger LOG = LoggerFactory.getLogger("ew-gametest");

	private static void check(boolean ok, String message) {
		if (!ok) {
			throw new GameTestException(message);
		}
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void breakTimes(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		BlockPos p = ctx.getAbsolutePos(new BlockPos(1, 1, 1));
		int dirt = ZombieSiegeGoal.breakTicks(world, p, Blocks.DIRT.getDefaultState());
		int stone = ZombieSiegeGoal.breakTicks(world, p, Blocks.STONE.getDefaultState());
		int obsidian = ZombieSiegeGoal.breakTicks(world, p, Blocks.OBSIDIAN.getDefaultState());
		int bedrock = ZombieSiegeGoal.breakTicks(world, p, Blocks.BEDROCK.getDefaultState());
		int torch = ZombieSiegeGoal.breakTicks(world, p, Blocks.TORCH.getDefaultState());
		LOG.info("[EW-GAMETEST] romper: tierra {} tics, piedra {}, obsidiana {}, roca madre {}, antorcha {}", dirt, stone, obsidian, bedrock, torch);
		check(dirt < stone && stone < obsidian, "Los tiempos no van por dureza");
		check(obsidian == 1500, "La obsidiana no tarda 75 s: " + obsidian);
		check(bedrock < 0 && torch < 0, "Rompe la roca madre o se para en una antorcha");
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 400)
	public void zombieBreaksThroughAWall(TestContext ctx) {
		for (int x = 0; x <= 6; x++) {
			for (int z = 0; z <= 2; z++) {
				ctx.setBlockState(x, 0, z, Blocks.STONE);
				for (int y = 1; y <= 3; y++) {
					ctx.setBlockState(x, y, z, Blocks.AIR);
				}
			}
		}
		// una celda de piedra con techo: el zombi dentro, el muñeco fuera
		for (int y = 1; y <= 2; y++) {
			ctx.setBlockState(0, y, 1, Blocks.STONE);
			ctx.setBlockState(2, y, 1, Blocks.STONE);
			ctx.setBlockState(1, y, 0, Blocks.STONE);
			ctx.setBlockState(1, y, 2, Blocks.STONE);
		}
		ctx.setBlockState(1, 3, 1, Blocks.STONE);
		ZombieEntity zombie = ctx.spawnEntity(EntityType.ZOMBIE, 1, 1, 1);
		zombie.equipStack(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
		ArmorStandEntity dummy = ctx.spawnEntity(EntityType.ARMOR_STAND, 5, 1, 1);
		zombie.setTarget(dummy);
		ctx.waitAndRun(300, () -> {
			boolean low = ctx.getBlockState(new BlockPos(2, 1, 1)).isAir();
			boolean high = ctx.getBlockState(new BlockPos(2, 2, 1)).isAir();
			LOG.info("[EW-GAMETEST] zombi encerrado: pared de abajo rota {}, de arriba {}; el zombi en {}", low, high, ctx.getRelativePos(zombie.getBlockPos()));
			check(low || high, "El zombi no rompe la pared para llegar");
			zombie.discard();
			dummy.discard();
			ctx.complete();
		});
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void zombiesSeeGaps(TestContext ctx) {
		for (int x = 0; x <= 6; x++) {
			for (int y = 0; y <= 3; y++) {
				ctx.setBlockState(x, y, 1, Blocks.AIR);
			}
		}
		ctx.setBlockState(1, 2, 1, Blocks.STONE);
		ctx.setBlockState(4, 2, 1, Blocks.STONE); // hueco de 2: x = 2 y 3
		ctx.setBlockState(6, 2, 1, Blocks.STONE);
		ServerWorld world = ctx.getWorld();
		int two = GapJumpGoal.gap(world, ctx.getAbsolutePos(new BlockPos(1, 3, 1)), Direction.EAST);
		int one = GapJumpGoal.gap(world, ctx.getAbsolutePos(new BlockPos(4, 3, 1)), Direction.EAST);
		int none = GapJumpGoal.gap(world, ctx.getAbsolutePos(new BlockPos(4, 3, 1)), Direction.WEST);
		LOG.info("[EW-GAMETEST] huecos: de 2 -> {}, de 1 -> {}, hacia atrás (3) -> {}", two, one, none);
		check(two == 2 && one == 1 && none == 2, "No ve bien los huecos: " + two + " " + one + " " + none);
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 100)
	public void zombieHordes(TestContext ctx) {
		int first = NightThreat.hordeSize(2, 2, 1);
		int late = NightThreat.hordeSize(100, 2, 1);
		int two = NightThreat.hordeSize(2, 2, 2);
		ServerWorld world = ctx.getWorld();
		List<ZombieEntity> horde = NightThreat.spawnHorde(world, ctx.getAbsolutePos(new BlockPos(1, 1, 1)), first, world.getRandom());
		boolean tagged = horde.stream().allMatch(z -> z.getCommandTags().contains(NightThreat.HORDE_TAG));
		LOG.info("[EW-GAMETEST] horda de zombis: la primera {}, tarde {}, con 2 jugadores {}; salen {} (marcados {})", first, late, two, horde.size(),
				tagged);
		horde.forEach(ZombieEntity::discard);
		check(first == 15 && late == 30 && two == 20, "El tamaño de la horda no cuadra");
		check(horde.size() >= 10 && tagged, "No sale la horda: " + horde.size());
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void bitesInfect(TestContext ctx) {
		ServerPlayerEntity player = (ServerPlayerEntity) ctx.createMockCreativeServerPlayerInWorld();
		EwConfig config = EwConfig.get();
		int chance = config.zombieInfectionChance;
		config.zombieInfectionChance = 100;
		try {
			NightThreat.maybeInfect(player, player.getRandom());
			int a = player.getStatusEffect(ModEffects.INFECTION).getAmplifier();
			NightThreat.maybeInfect(player, player.getRandom());
			int b = player.getStatusEffect(ModEffects.INFECTION).getAmplifier();
			config.zombieInfectionChance = 0;
			player.removeStatusEffect(ModEffects.INFECTION);
			NightThreat.maybeInfect(player, player.getRandom());
			boolean clean = !player.hasStatusEffect(ModEffects.INFECTION);
			LOG.info("[EW-GAMETEST] infección: primer mordisco grado {}, segundo {}; con 0 % sin infección {}", a, b, clean);
			check(a == 0 && b == 1 && clean, "La infección no va bien");
		} finally {
			config.zombieInfectionChance = chance;
		}
		ctx.complete();
	}
}
