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

	@GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 400)
	public void hordeFindsYouIndoors(TestContext ctx) {
		for (int x = 0; x <= 12; x++) {
			for (int z = 0; z <= 6; z++) {
				ctx.setBlockState(x, 0, z, Blocks.STONE);
				for (int y = 1; y <= 4; y++) {
					ctx.setBlockState(x, y, z, Blocks.AIR);
				}
			}
		}
		// una caseta cerrada de tablones (3x3 por dentro, con techo): el jugador dentro
		for (int x = 8; x <= 12; x++) {
			for (int z = 1; z <= 5; z++) {
				for (int y = 1; y <= 4; y++) {
					boolean inside = x > 8 && x < 12 && z > 1 && z < 5 && y < 4;
					ctx.setBlockState(x, y, z, inside ? Blocks.AIR : Blocks.OAK_PLANKS);
				}
			}
		}
		ServerWorld world = ctx.getWorld();
		ServerPlayerEntity player = (ServerPlayerEntity) ctx.createMockCreativeServerPlayerInWorld();
		player.changeGameMode(net.minecraft.world.GameMode.SURVIVAL);
		BlockPos in = ctx.getAbsolutePos(new BlockPos(10, 1, 3));
		player.refreshPositionAndAngles(in.getX() + 0.5, in.getY(), in.getZ() + 0.5, 0.0F, 0.0F);
		ZombieEntity zombie = EntityType.ZOMBIE.create(world);
		BlockPos out = ctx.getAbsolutePos(new BlockPos(2, 1, 3));
		zombie.refreshPositionAndAngles(out.getX() + 0.5, out.getY(), out.getZ() + 0.5, 0.0F, 0.0F);
		zombie.equipStack(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
		world.spawnEntity(zombie);
		// el jugador de pruebas cuenta como creativo: aquí la horda le vale igual
		((com.evolvedwarfare.mixin.MobEntityGoalsAccessor) zombie).evolvedwarfare$targets().add(0,
				new com.evolvedwarfare.night.ScentTargetGoal(zombie, 96, m -> true, p -> p == player));
		ctx.waitAndRun(300, () -> {
			boolean sees = zombie.canSee(player);
			boolean hunting = zombie.getTarget() == player;
			int broken = 0;
			for (int x = 8; x <= 12; x++) {
				for (int z = 1; z <= 5; z++) {
					for (int y = 1; y <= 3; y++) {
						boolean wall = x == 8 || x == 12 || z == 1 || z == 5;
						if (wall && ctx.getBlockState(new BlockPos(x, y, z)).isAir()) {
							broken++;
						}
					}
				}
			}
			LOG.info("[EW-GAMETEST] jugador dentro de una caseta (creativo {}, en el mundo {}): el zombi le ve {}, le persigue {}, bloques de pared rotos {}, el zombi en {}", player.isCreative(), world.getPlayers().contains(player), sees,
					hunting, broken, ctx.getRelativePos(zombie.getBlockPos()));
			zombie.discard();
			player.discard();
			check(hunting, "El zombi pierde al jugador dentro de la caseta");
			check(broken > 0, "El zombi no rompe la caseta para entrar");
			ctx.complete();
		});
	}

	@GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 400)
	public void zombiesBuildUp(TestContext ctx) {
		for (int x = 0; x <= 6; x++) {
			for (int z = 0; z <= 6; z++) {
				ctx.setBlockState(x, 0, z, Blocks.STONE);
				for (int y = 1; y <= 7; y++) {
					ctx.setBlockState(x, y, z, Blocks.AIR);
				}
			}
		}
		// el muñeco en lo alto de una columna de piedra de 3; el zombi al pie
		for (int y = 1; y <= 3; y++) {
			ctx.setBlockState(3, y, 3, Blocks.BEDROCK);
		}
		ArmorStandEntity dummy = ctx.spawnEntity(EntityType.ARMOR_STAND, 3, 4, 3);
		ZombieEntity zombie = ctx.spawnEntity(EntityType.ZOMBIE, 3, 1, 2);
		zombie.equipStack(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
		zombie.setTarget(dummy);
		ctx.waitAndRun(300, () -> {
			int dirt = 0;
			for (BlockPos p : BlockPos.iterate(ctx.getAbsolutePos(new BlockPos(0, 1, 0)), ctx.getAbsolutePos(new BlockPos(6, 7, 6)))) {
				if (ctx.getWorld().getBlockState(p).isOf(Blocks.DIRT)) {
					dirt++;
				}
			}
			double up = zombie.getY() - ctx.getAbsolutePos(BlockPos.ORIGIN).getY();
			LOG.info("[EW-GAMETEST] zombi bajo una columna: tierra puesta {}, el zombi a altura {}", dirt, up);
			zombie.discard();
			dummy.discard();
			check(dirt >= 2, "El zombi no pone tierra para subir");
			ctx.complete();
		});
	}

	/** Trabajo en equipo: no rompen la tierra que ha puesto la horda ni el bloque en el que está otro, y rompen juntos. */
	@GameTest(templateName = EMPTY_STRUCTURE)
	public void hordeTeamwork(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		BlockPos built = ctx.getAbsolutePos(new BlockPos(1, 1, 1));
		BlockPos wild = ctx.getAbsolutePos(new BlockPos(3, 1, 1));
		BlockPos under = ctx.getAbsolutePos(new BlockPos(1, 1, 3));
		world.setBlockState(built, Blocks.DIRT.getDefaultState());
		world.setBlockState(wild, Blocks.DIRT.getDefaultState());
		world.setBlockState(under, Blocks.DIRT.getDefaultState());
		com.evolvedwarfare.night.HordeWork.built(world, built);
		ZombieEntity a = EntityType.ZOMBIE.create(world);
		a.refreshPositionAndAngles(ctx.getAbsolutePos(new BlockPos(2, 1, 2)).toCenterPos().add(0, -0.5, 0), 0, 0);
		world.spawnEntity(a);
		ZombieEntity b = EntityType.ZOMBIE.create(world);
		b.refreshPositionAndAngles(under.up().toCenterPos().add(0, -0.5, 0), 0, 0);
		world.spawnEntity(b);
		boolean builtSafe = !com.evolvedwarfare.night.HordeWork.mayBreak(world, built, a);
		boolean wildOk = com.evolvedwarfare.night.HordeWork.mayBreak(world, wild, a);
		boolean underSafe = !com.evolvedwarfare.night.HordeWork.mayBreak(world, under, a);
		var j1 = com.evolvedwarfare.night.HordeWork.join(world, wild, 30, a);
		j1.progress += 10;
		var j2 = com.evolvedwarfare.night.HordeWork.join(world, wild, 30, b);
		j2.progress += 10;
		boolean shared = j1 == j2 && j1.progress == 20;
		com.evolvedwarfare.night.HordeWork.finish(world, wild);
		LOG.info("[EW-GAMETEST] horda en equipo: respeta su tierra {}, rompe la otra {}, respeta al compañero de encima {}, rompen juntos {}",
				builtSafe, wildOk, underSafe, shared);
		check(builtSafe && wildOk && underSafe && shared, "Los zombis se estorban entre ellos");
		a.discard();
		b.discard();
		ctx.complete();
	}
}
