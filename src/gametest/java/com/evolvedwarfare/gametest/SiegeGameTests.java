package com.evolvedwarfare.gametest;

import com.evolvedwarfare.registry.ModEntities;
import com.evolvedwarfare.registry.ModItems;
import com.evolvedwarfare.siege.SiegeBallistics;
import com.evolvedwarfare.siege.SiegeEngineEntity;
import com.evolvedwarfare.siege.SiegeKind;
import com.evolvedwarfare.siege.SiegeShotEntity;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.GameTestException;
import net.minecraft.test.TestContext;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Las máquinas de asedio, probadas en un servidor de verdad: colocarlas con clic derecho en el suelo (lejos y a los
 * pies del jugador), subirse y disparar (o golpear, el ariete).
 */
public class SiegeGameTests implements FabricGameTest {
	private static final Logger LOG = LoggerFactory.getLogger("ew-gametest");

	// ------------------------------------------------------------------ colocar

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void placeCatapult(TestContext ctx) {
		place(ctx, ModItems.CATAPULT, new BlockPos(4, 0, 4));
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void placeBallista(TestContext ctx) {
		place(ctx, ModItems.BALLISTA, new BlockPos(4, 0, 4));
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void placeTrebuchet(TestContext ctx) {
		place(ctx, ModItems.TREBUCHET, new BlockPos(4, 0, 4));
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void placeRam(TestContext ctx) {
		place(ctx, ModItems.BATTERING_RAM, new BlockPos(4, 0, 4));
	}

	/**
	 * Clic en el bloque donde está el propio jugador: tiene que ponerse delante. El trebuchet mide 5 × 5, así que el
	 * jugador está en el centro del borde (fuera del suelo de la prueba puede haber cualquier cosa).
	 */
	@GameTest(templateName = EMPTY_STRUCTURE)
	public void placeTrebuchetAtFeet(TestContext ctx) {
		floor(ctx);
		place(ctx, player(ctx, 4.5, 1.5), ModItems.TREBUCHET, new BlockPos(4, 0, 1));
	}

	private static void place(TestContext ctx, Item item, BlockPos ground) {
		floor(ctx);
		place(ctx, player(ctx), item, ground);
	}

	private static void place(TestContext ctx, ServerPlayerEntity player, Item item, BlockPos ground) {
		ItemStack stack = new ItemStack(item);
		player.setStackInHand(Hand.MAIN_HAND, stack);
		BlockPos abs = ctx.getAbsolutePos(ground);
		BlockHitResult hit = new BlockHitResult(Vec3d.ofCenter(abs).add(0.0, 0.5, 0.0), Direction.UP, abs, false);
		ActionResult result = player.interactionManager.interactBlock(player, ctx.getWorld(), stack, Hand.MAIN_HAND, hit);
		ctx.waitAndRun(2, () -> {
			List<SiegeEngineEntity> engines = engines(ctx);
			LOG.info("[EW-GAMETEST] colocar {} en {}: resultado={}, máquinas={}", item, ground, result, engines.size());
			for (SiegeEngineEntity engine : engines) {
				LOG.info("[EW-GAMETEST]   {} en {} (vivo={})", engine.getType(), engine.getPos(), engine.isAlive());
			}
			if (engines.isEmpty()) {
				throw new GameTestException("No aparece la máquina (" + item + "): resultado " + result);
			}
			ctx.complete();
		});
	}

	// ------------------------------------------------------------------ disparar

	@GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 100)
	public void fireCatapult(TestContext ctx) {
		fire(ctx, ModEntities.CATAPULT);
	}

	@GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 100)
	public void fireBallista(TestContext ctx) {
		fire(ctx, ModEntities.BALLISTA);
	}

	@GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 100)
	public void fireTrebuchet(TestContext ctx) {
		fire(ctx, ModEntities.TREBUCHET);
	}

	private static void fire(TestContext ctx, EntityType<SiegeEngineEntity> type) {
		floor(ctx);
		ServerPlayerEntity player = player(ctx);
		SiegeEngineEntity engine = ctx.spawnEntity(type, 4.5F, 1.0F, 4.5F);
		boolean riding = player.startRiding(engine, true);
		// mirando al frente y un poco hacia arriba, como al apuntar a algo lejos
		player.setPitch(-15.0F);
		ctx.waitAndRun(3, () -> {
			player.setJumping(true);
			ctx.waitAndRun(1, () -> {
				Box around = new Box(engine.getBlockPos()).expand(48.0);
				List<SiegeShotEntity> shots = ctx.getWorld().getEntitiesByClass(SiegeShotEntity.class, around, s -> s.getOwner() == player);
				LOG.info("[EW-GAMETEST] disparar {}: montado={}, pasajero={}, recarga={}, proyectiles={}", type, riding,
						engine.getFirstPassenger(), engine.getReload(), shots.size());
				if (shots.isEmpty()) {
					throw new GameTestException("No sale ningún proyectil (" + type + "): montado=" + riding + ", recarga=" + engine.getReload());
				}
				ctx.complete();
			});
		});
	}

	/**
	 * Cámara aérea: el cliente marca un punto 45 bloques al frente y el disparo va ahí (con la parábola alta), no a lo
	 * que mira el jugador (al cielo: eso sería el tiro más largo).
	 */
	@GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 100)
	public void aerialAimCatapult(TestContext ctx) {
		floor(ctx);
		ServerPlayerEntity player = player(ctx);
		SiegeEngineEntity engine = ctx.spawnEntity(ModEntities.CATAPULT, 4.5F, 1.0F, 4.5F);
		boolean riding = player.startRiding(engine, true);
		player.setPitch(-15.0F);
		Vec3d target = ctx.getAbsolute(new Vec3d(4.5, 1.0, 49.5));
		ctx.waitAndRun(3, () -> {
			engine.setPilotAim(player, target);
			SiegeBallistics.Aim expected = SiegeBallistics.aimAt(SiegeKind.CATAPULT, engine.muzzle(), engine.getYaw(), target);
			player.setJumping(true);
			ctx.waitAndRun(1, () -> {
				Box around = new Box(engine.getBlockPos()).expand(48.0);
				List<SiegeShotEntity> shots = ctx.getWorld().getEntitiesByClass(SiegeShotEntity.class, around, s -> s.getOwner() == player);
				if (shots.isEmpty()) {
					throw new GameTestException("No sale ningún proyectil con la cámara aérea: montado=" + riding);
				}
				Vec3d v = shots.get(0).getVelocity();
				double elevation = Math.toDegrees(Math.atan2(v.y, Math.sqrt(v.x * v.x + v.z * v.z)));
				double yaw = Math.toDegrees(Math.atan2(-v.x, v.z));
				LOG.info("[EW-GAMETEST] cámara aérea: esperado yaw={} elev={} (llega={}), sale yaw={} elev={}", expected.yaw(),
						expected.elevation(), expected.reachable(), yaw, elevation);
				if (!expected.reachable() || Math.abs(elevation - expected.elevation()) > 3.0
						|| Math.abs(MathHelper.wrapDegrees(yaw - expected.yaw())) > 2.0) {
					throw new GameTestException("El tiro no va al punto de la cámara aérea: elevación " + elevation + " (esperada "
							+ expected.elevation() + ")");
				}
				ctx.complete();
			});
		});
	}

	@GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 100)
	public void ramStrikes(TestContext ctx) {
		floor(ctx);
		ServerPlayerEntity player = player(ctx);
		SiegeEngineEntity ram = ctx.spawnEntity(ModEntities.RAM, 4.5F, 1.0F, 3.5F);
		boolean riding = player.startRiding(ram, true);
		ctx.waitAndRun(3, () -> {
			player.setJumping(true);
			ctx.waitAndRun(2, () -> {
				LOG.info("[EW-GAMETEST] ariete: montado={}, golpe={}, recarga={}", riding, ram.getStrikeTicks(), ram.getReload());
				if (ram.getStrikeTicks() <= 0 && ram.getReload() <= 0) {
					throw new GameTestException("El ariete no golpea: montado=" + riding);
				}
				ctx.complete();
			});
		});
	}

	// ------------------------------------------------------------------ ayudas

	private static void floor(TestContext ctx) {
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) {
				ctx.setBlockState(x, 0, z, Blocks.STONE);
			}
		}
	}

	/** Un jugador en creativo, de pie en (1,5; 1; 1,5) mirando hacia +Z (sur) y un poco al suelo. */
	private static ServerPlayerEntity player(TestContext ctx) {
		return player(ctx, 1.5, 1.5);
	}

	private static ServerPlayerEntity player(TestContext ctx, double x, double z) {
		ServerPlayerEntity player = ctx.createMockCreativeServerPlayerInWorld();
		Vec3d pos = ctx.getAbsolute(new Vec3d(x, 1.0, z));
		player.refreshPositionAndAngles(pos.x, pos.y, pos.z, 0.0F, 30.0F);
		return player;
	}

	private static List<SiegeEngineEntity> engines(TestContext ctx) {
		Box box = new Box(ctx.getAbsolutePos(BlockPos.ORIGIN).toCenterPos(), ctx.getAbsolutePos(new BlockPos(8, 6, 8)).toCenterPos()).expand(1.0);
		return ctx.getWorld().getEntitiesByClass(SiegeEngineEntity.class, box, e -> true);
	}
}
