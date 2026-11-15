package com.evolvedwarfare.gametest;

import com.evolvedwarfare.engineering.Engineering;
import com.evolvedwarfare.engineering.KineticBlockEntity;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.state.property.Properties;
import net.minecraft.test.GameTest;
import net.minecraft.test.GameTestException;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** La ingeniería medieval (fase 72): la red de fuerza, las relaciones de los engranajes, la sobrecarga, el agua y el viento. */
public class EngineeringGameTests implements FabricGameTest {
	private static final Logger LOG = LoggerFactory.getLogger("ew-gametest");

	private static KineticBlockEntity be(TestContext ctx, int x, int y, int z) {
		if (ctx.getWorld().getBlockEntity(ctx.getAbsolutePos(new BlockPos(x, y, z))) instanceof KineticBlockEntity k) {
			return k;
		}
		throw new GameTestException("No hay bloque de fuerza en " + x + "," + y + "," + z);
	}

	/** Manivela → ejes → velocímetro → engranaje pequeño → otro pequeño (al revés) y uno grande en diagonal (a la mitad). */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "kinetic_gears", tickLimit = 80)
	public void gearsShareSpeedAndForce(TestContext ctx) {
		ctx.setBlockState(1, 2, 1, Engineering.HAND_CRANK.getDefaultState().with(Properties.FACING, Direction.WEST));
		for (int x = 2; x <= 4; x++) {
			ctx.setBlockState(x, 2, 1, (x == 3 ? Engineering.SPEED_GAUGE.getDefaultState().with(Properties.HORIZONTAL_AXIS, Direction.Axis.X)
					: Engineering.SHAFT.getDefaultState().with(Properties.AXIS, Direction.Axis.X)));
		}
		ctx.setBlockState(5, 2, 1, Engineering.SMALL_GEAR.getDefaultState().with(Properties.AXIS, Direction.Axis.X));
		ctx.setBlockState(5, 3, 1, Engineering.SMALL_GEAR.getDefaultState().with(Properties.AXIS, Direction.Axis.X));
		ctx.setBlockState(5, 3, 2, Engineering.LARGE_GEAR.getDefaultState().with(Properties.AXIS, Direction.Axis.X));
		ctx.runAtTick(2, () -> {
			KineticBlockEntity crank = be(ctx, 1, 2, 1);
			crank.turnCrank(200);
			crank.refreshSource();
		});
		ctx.runAtTick(8, () -> {
			float s = be(ctx, 2, 2, 1).speed(), g = be(ctx, 3, 2, 1).speed(), a = be(ctx, 5, 2, 1).speed(), b = be(ctx, 5, 3, 1).speed(),
					l = be(ctx, 5, 3, 2).speed();
			LOG.info("[EW-GAMETEST] engranajes: eje {}, velocímetro {}, pequeño {}, pequeño al lado {}, grande {}, fuerza {}/{}", s, g, a, b, l,
					be(ctx, 4, 2, 1).stress(), be(ctx, 4, 2, 1).capacity());
			if (!(Math.abs(Math.abs(s) - 16) < 0.01 && g == s && a == s && Math.abs(b + a) < 0.01 && Math.abs(l - (-0.5F * a)) < 0.01
					&& be(ctx, 4, 2, 1).capacity() > 40)) {
				throw new GameTestException("Las velocidades de la red no cuadran");
			}
			// sobrecarga: 4 de fuerza por rpm a 16 rpm = 64 > 48
			be(ctx, 3, 2, 1).setExtraImpact(4.0F);
		});
		ctx.runAtTick(14, () -> {
			KineticBlockEntity g = be(ctx, 3, 2, 1);
			LOG.info("[EW-GAMETEST] sobrecarga: estado {}, velocidad {}, fuerza {}/{}", g.networkState(), g.speed(), g.stress(), g.capacity());
			if (g.networkState() != KineticBlockEntity.OVERSTRESSED || g.speed() != 0) {
				throw new GameTestException("La red no se sobrecarga");
			}
			g.setExtraImpact(1.0F);
		});
		ctx.runAtTick(20, () -> {
			KineticBlockEntity g = be(ctx, 3, 2, 1);
			LOG.info("[EW-GAMETEST] sin sobrecarga: estado {}, velocidad {}, fuerza {}/{}", g.networkState(), g.speed(), g.stress(), g.capacity());
			if (g.networkState() != KineticBlockEntity.OK || Math.abs(g.speed()) < 1 || Math.abs(g.stress() - 16) > 0.5) {
				throw new GameTestException("La red no vuelve a girar");
			}
			ctx.complete();
		});
	}

	/** El agua que cae por un lado mueve la rueda hidráulica (y sin agua que se mueva, se para). */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "kinetic_water", tickLimit = 120)
	public void waterTurnsTheWheel(TestContext ctx) {
		ctx.setBlockState(3, 3, 3, Engineering.WATER_WHEEL_SMALL.getDefaultState().with(Properties.HORIZONTAL_AXIS, Direction.Axis.X));
		ctx.setBlockState(2, 3, 3, Engineering.SHAFT.getDefaultState().with(Properties.AXIS, Direction.Axis.X));
		ctx.runAtTick(10, () -> {
			KineticBlockEntity w = be(ctx, 3, 3, 3);
			w.refreshSource();
			if (w.generated() != 0 || w.problem() != 2) {
				throw new GameTestException("La rueda gira sin agua");
			}
			// una cascada por un lado (el agua es finita: se pone y se mide en el mismo tic)
			for (int y = 2; y <= 4; y++) {
				ctx.getWorld().setBlockState(ctx.getAbsolutePos(new net.minecraft.util.math.BlockPos(3, y, 4)),
						net.minecraft.fluid.Fluids.FLOWING_WATER.getFlowing(8, true).getBlockState(), net.minecraft.block.Block.NOTIFY_LISTENERS);
			}
			w.refreshSource();
		});
		ctx.runAtTick(12, () -> {
			KineticBlockEntity w = be(ctx, 3, 3, 3);
			KineticBlockEntity shaft = be(ctx, 2, 3, 3);
			LOG.info("[EW-GAMETEST] rueda hidráulica: da {}, gira {}, fuerza {}, problema {}; eje {}", w.generated(), w.speed(), w.capacity(), w.problem(), shaft.speed());
			if (Math.abs(w.speed()) < 1 || w.capacity() <= 0 || shaft.speed() != w.speed()) {
				throw new GameTestException("El agua no mueve la rueda");
			}
			ctx.complete();
		});
	}

	/** El molino de viento gira con el aire libre y se para si algo estorba a las aspas. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "kinetic_wind", tickLimit = 160)
	public void windTurnsTheSails(TestContext ctx) {
		ctx.setBlockState(3, 4, 3, Engineering.WINDMILL_SMALL.getDefaultState().with(Properties.HORIZONTAL_FACING, Direction.NORTH));
		ctx.runAtTick(50, () -> {
			KineticBlockEntity w = be(ctx, 3, 4, 3);
			LOG.info("[EW-GAMETEST] molino libre: gira {}, fuerza {}", w.speed(), w.capacity());
			if (Math.abs(w.speed()) < 1 || w.capacity() <= 0) {
				throw new GameTestException("El molino no gira con aire libre");
			}
			ctx.setBlockState(3, 5, 3, Blocks.STONE);
		});
		ctx.runAtTick(100, () -> {
			KineticBlockEntity w = be(ctx, 3, 4, 3);
			LOG.info("[EW-GAMETEST] molino tapado: gira {}, problema {}", w.speed(), w.problem());
			if (w.speed() != 0 || w.problem() != 1) {
				throw new GameTestException("El molino gira aunque algo estorba a las aspas");
			}
			ctx.complete();
		});
	}

	// ------------------------------------------------------------------ fase 73: las máquinas

	/** Da vueltas a la manivela de (x,y,z) todos los tics hasta 'until'. */
	private static void keepCranking(TestContext ctx, int x, int y, int z, int until) {
		for (int t = 1; t <= until; t++) {
			ctx.runAtTick(t, () -> {
				KineticBlockEntity c = be(ctx, x, y, z);
				c.turnCrank(10);
				c.refreshSource();
			});
		}
	}

	private static com.evolvedwarfare.engineering.MachineBlockEntity machine(TestContext ctx, int x, int y, int z) {
		if (be(ctx, x, y, z) instanceof com.evolvedwarfare.engineering.MachineBlockEntity m) {
			return m;
		}
		throw new GameTestException("No hay máquina en " + x + "," + y + "," + z);
	}

	private static int count(net.minecraft.inventory.Inventory inv, net.minecraft.item.Item item) {
		int n = 0;
		for (int i = 0; i < inv.size(); i++) {
			if (inv.getStack(i).isOf(item)) {
				n += inv.getStack(i).getCount();
			}
		}
		return n;
	}

	/** La muela, movida a mano con la manivela de encima, muele el trigo en harina (y la manivela tiene fuerza de sobra). */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "machine_mill", tickLimit = 200)
	public void millstoneGrindsFlour(TestContext ctx) {
		ctx.setBlockState(1, 1, 1, Engineering.MILLSTONE);
		ctx.setBlockState(1, 2, 1, Engineering.HAND_CRANK.getDefaultState().with(Properties.FACING, Direction.UP));
		ctx.runAtTick(1, () -> machine(ctx, 1, 1, 1).insert(new net.minecraft.item.ItemStack(net.minecraft.item.Items.WHEAT, 3)));
		keepCranking(ctx, 1, 2, 1, 170);
		ctx.runAtTick(160, () -> {
			var m = machine(ctx, 1, 1, 1);
			int flour = count(m, Engineering.FLOUR);
			LOG.info("[EW-GAMETEST] muela: gira {}, fuerza {}/{}, harina {}, trigo {}", m.speed(), m.stress(), m.capacity(), flour, m.input().getCount());
			if (flour != 3 || m.networkState() != KineticBlockEntity.OK) {
				throw new GameTestException("La muela no muele el trigo en harina");
			}
			ctx.complete();
		});
	}

	/** El aserradero sierra los troncos en 6 tablones cada uno y los echa al cofre de delante. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "machine_saw", tickLimit = 200)
	public void sawmillCutsLogsIntoTheChest(TestContext ctx) {
		ctx.setBlockState(2, 1, 2, Engineering.SAWMILL.getDefaultState().with(Properties.HORIZONTAL_FACING, Direction.EAST));
		ctx.setBlockState(2, 1, 1, Engineering.HAND_CRANK.getDefaultState().with(Properties.FACING, Direction.NORTH));
		ctx.setBlockState(3, 1, 2, Blocks.CHEST);
		ctx.runAtTick(1, () -> machine(ctx, 2, 1, 2).insert(new net.minecraft.item.ItemStack(net.minecraft.item.Items.OAK_LOG, 2)));
		keepCranking(ctx, 2, 1, 1, 150);
		ctx.runAtTick(140, () -> {
			var m = machine(ctx, 2, 1, 2);
			var chest = (net.minecraft.inventory.Inventory) ctx.getWorld().getBlockEntity(ctx.getAbsolutePos(new BlockPos(3, 1, 2)));
			int planks = count(chest, net.minecraft.item.Items.OAK_PLANKS);
			LOG.info("[EW-GAMETEST] aserradero: gira {}, fuerza {}/{}, tablones en el cofre {}", m.speed(), m.stress(), m.capacity(), planks);
			if (planks != 12) {
				throw new GameTestException("El aserradero no sierra los troncos en tablones");
			}
			ctx.complete();
		});
	}

	/** El martinete afila solo una hoja de espada en bruto que hay en el yunque de delante. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "machine_hammer", tickLimit = 300)
	public void tripHammerSharpensOnTheAnvil(TestContext ctx) {
		ctx.setBlockState(1, 2, 2, Engineering.TRIP_HAMMER.getDefaultState().with(Properties.HORIZONTAL_FACING, Direction.EAST));
		ctx.setBlockState(1, 2, 1, Engineering.HAND_CRANK.getDefaultState().with(Properties.FACING, Direction.NORTH));
		ctx.setBlockState(2, 0, 2, Blocks.STONE);
		ctx.setBlockState(2, 1, 2, Blocks.ANVIL);
		net.minecraft.entity.ItemEntity[] item = new net.minecraft.entity.ItemEntity[1];
		ctx.runAtTick(2, () -> {
			BlockPos a = ctx.getAbsolutePos(new BlockPos(2, 1, 2));
			item[0] = new net.minecraft.entity.ItemEntity(ctx.getWorld(), a.getX() + 0.5, a.getY() + 1.05, a.getZ() + 0.5,
					com.evolvedwarfare.smithing.Smithing.rough(com.evolvedwarfare.smithing.ForgedPart.SWORD_BLADE, com.evolvedwarfare.smithing.ForgeMaterial.IRON,
							0.8F), 0, 0, 0);
			item[0].setPickupDelayInfinite();
			ctx.getWorld().spawnEntity(item[0]);
		});
		keepCranking(ctx, 1, 2, 1, 260);
		ctx.runAtTick(250, () -> {
			var data = item[0].getStack().get(com.evolvedwarfare.registry.ModComponents.FORGED_PART);
			LOG.info("[EW-GAMETEST] martinete: gira {}, golpes {}, pieza {}, vivo {}", be(ctx, 1, 2, 2).speed(), machine(ctx, 1, 2, 2).hits(), data,
					item[0].isAlive());
			if (data == null || data.quality() <= 0) {
				throw new GameTestException("El martinete no afila la pieza");
			}
			ctx.complete();
		});
	}

	/** El fuelle hace que el horno de delante funda más deprisa que uno igual sin fuelle. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "machine_bellows", tickLimit = 220)
	public void bellowsSpeedUpTheFurnace(TestContext ctx) {
		ctx.setBlockState(2, 1, 1, Engineering.BELLOWS.getDefaultState().with(Properties.HORIZONTAL_FACING, Direction.EAST));
		ctx.setBlockState(2, 1, 0, Engineering.HAND_CRANK.getDefaultState().with(Properties.FACING, Direction.NORTH));
		ctx.setBlockState(3, 1, 1, Blocks.FURNACE);
		ctx.setBlockState(3, 1, 4, Blocks.FURNACE);
		ctx.runAtTick(1, () -> {
			for (int z : new int[] {1, 4}) {
				var f = (net.minecraft.inventory.Inventory) ctx.getWorld().getBlockEntity(ctx.getAbsolutePos(new BlockPos(3, 1, z)));
				f.setStack(0, new net.minecraft.item.ItemStack(net.minecraft.item.Items.RAW_IRON, 4));
				f.setStack(1, new net.minecraft.item.ItemStack(net.minecraft.item.Items.COAL, 2));
			}
		});
		keepCranking(ctx, 2, 1, 0, 200);
		ctx.runAtTick(180, () -> {
			var blown = (net.minecraft.inventory.Inventory) ctx.getWorld().getBlockEntity(ctx.getAbsolutePos(new BlockPos(3, 1, 1)));
			var plain = (net.minecraft.inventory.Inventory) ctx.getWorld().getBlockEntity(ctx.getAbsolutePos(new BlockPos(3, 1, 4)));
			int a = count(blown, net.minecraft.item.Items.IRON_INGOT), b = count(plain, net.minecraft.item.Items.IRON_INGOT);
			LOG.info("[EW-GAMETEST] fuelle: gira {}, con fuelle {} lingotes, sin fuelle {}", be(ctx, 2, 1, 1).speed(), a, b);
			if (a < 1 || a <= b) {
				throw new GameTestException("El fuelle no acelera el horno");
			}
			ctx.complete();
		});
	}

	// ------------------------------------------------------------------ fase 74: el transporte

	/** Dos tornillos movidos por la manivela de debajo suben el agua del lado del pie a lo alto, y un objeto suelto también. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "machine_screw", tickLimit = 200)
	public void screwLiftsWaterAndItems(TestContext ctx) {
		ctx.setBlockState(2, 1, 2, Engineering.HAND_CRANK.getDefaultState().with(Properties.FACING, Direction.DOWN));
		ctx.setBlockState(2, 2, 2, Engineering.SCREW);
		ctx.setBlockState(2, 3, 2, Engineering.SCREW);
		// un estanque de un bloque al lado del pie
		for (int[] c : new int[][] {{0, 2, 2}, {1, 2, 1}, {1, 2, 3}, {1, 1, 2}}) {
			ctx.setBlockState(c[0], c[1], c[2], Blocks.STONE);
		}
		ctx.setBlockState(1, 2, 2, Blocks.WATER);
		// arriba, un cuenco cerrado para el agua
		for (int[] c : new int[][] {{1, 4, 2}, {3, 4, 2}, {2, 4, 1}, {2, 4, 3}}) {
			ctx.setBlockState(c[0], c[1], c[2], Blocks.STONE);
		}
		ctx.runAtTick(2, () -> {
			BlockPos at = ctx.getAbsolutePos(new BlockPos(3, 3, 2));
			ctx.getWorld().spawnEntity(new net.minecraft.entity.ItemEntity(ctx.getWorld(), at.getX() + 0.5, at.getY() - 0.9, at.getZ() + 0.5,
					new net.minecraft.item.ItemStack(net.minecraft.item.Items.APPLE), 0, 0, 0));
			ctx.setBlockState(3, 1, 2, Blocks.STONE);
		});
		keepCranking(ctx, 2, 1, 2, 120);
		ctx.runAtTick(110, () -> {
			BlockPos out = ctx.getAbsolutePos(new BlockPos(2, 4, 2));
			var world = ctx.getWorld();
			boolean water = world.getFluidState(out).isIn(net.minecraft.registry.tag.FluidTags.WATER);
			int apples = 0;
			for (var e : world.getEntitiesByClass(net.minecraft.entity.ItemEntity.class, new net.minecraft.util.math.Box(out).expand(2), x -> true)) {
				if (e.getStack().isOf(net.minecraft.item.Items.APPLE) && e.getY() >= out.getY()) {
					apples++;
				}
			}
			LOG.info("[EW-GAMETEST] tornillo: gira {}, agua arriba {} ({}), manzanas arriba {}", be(ctx, 2, 2, 2).speed(), water,
					world.getFluidState(out), apples);
			if (!water || apples != 1) {
				throw new GameTestException("El tornillo no sube el agua o los objetos");
			}
			ctx.complete();
		});
	}

	/** La polea baja su plataforma hasta el suelo y, al cambiarla, la sube. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "machine_pulley", tickLimit = 200)
	public void pulleyMovesThePlatform(TestContext ctx) {
		for (int x = 1; x <= 3; x++) {
			for (int z = 1; z <= 3; z++) {
				ctx.setBlockState(x, 1, z, Blocks.STONE);
			}
		}
		ctx.setBlockState(2, 8, 2, Engineering.PULLEY.getDefaultState().with(Properties.HORIZONTAL_FACING, Direction.EAST));
		ctx.setBlockState(2, 8, 1, Engineering.HAND_CRANK.getDefaultState().with(Properties.FACING, Direction.NORTH));
		com.evolvedwarfare.engineering.HoistEntity[] h = new com.evolvedwarfare.engineering.HoistEntity[1];
		ctx.runAtTick(2, () -> {
			BlockPos p = ctx.getAbsolutePos(new BlockPos(2, 8, 2));
			if (!com.evolvedwarfare.engineering.PulleyBlock.attachPlatform(ctx.getWorld(), p, null)) {
				throw new GameTestException("No se cuelga la plataforma");
			}
			h[0] = (com.evolvedwarfare.engineering.HoistEntity) ctx.getWorld().getEntity(machine(ctx, 2, 8, 2).hoistId());
		});
		keepCranking(ctx, 2, 8, 1, 180);
		double base = ctx.getAbsolutePos(BlockPos.ORIGIN).getY();
		ctx.runAtTick(90, () -> {
			double y = h[0].getY() - base;
			LOG.info("[EW-GAMETEST] polea: plataforma abajo a {}", y);
			if (Math.abs(y - 2.0) > 0.06) {
				throw new GameTestException("La plataforma no baja hasta el suelo");
			}
			machine(ctx, 2, 8, 2).setLifting(true);
		});
		ctx.runAtTick(150, () -> {
			double y = h[0].getY() - base;
			LOG.info("[EW-GAMETEST] polea: plataforma arriba a {}", y);
			if (y < 5.5 || y > 7.76) {
				throw new GameTestException("La plataforma no sube");
			}
			ctx.complete();
		});
	}

	/** La grúa baja el gancho, coge un tronco, lo sube, gira la pluma y lo suelta en otro sitio. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "machine_crane", tickLimit = 260)
	public void craneMovesABlock(TestContext ctx) {
		ctx.setBlockState(1, 1, 1, Engineering.CRANE.getDefaultState().with(Properties.HORIZONTAL_FACING, Direction.EAST));
		ctx.setBlockState(1, 0, 1, Engineering.HAND_CRANK.getDefaultState().with(Properties.FACING, Direction.DOWN));
		ctx.setBlockState(4, 0, 1, Blocks.STONE);
		ctx.setBlockState(4, 1, 1, Blocks.OAK_LOG);
		ctx.setBlockState(1, 0, 4, Blocks.STONE);
		keepCranking(ctx, 1, 0, 1, 240);
		java.util.function.Supplier<com.evolvedwarfare.engineering.HoistEntity> hook = () -> {
			var id = machine(ctx, 1, 1, 1).hoistId();
			if (id == null || !(ctx.getWorld().getEntity(id) instanceof com.evolvedwarfare.engineering.HoistEntity e)) {
				throw new GameTestException("La grúa no tiene gancho");
			}
			return e;
		};
		ctx.runAtTick(60, () -> {
			var e = hook.get();
			LOG.info("[EW-GAMETEST] grúa: gancho en {}", e.getPos());
			if (!e.grabOrRelease(null)) {
				throw new GameTestException("El gancho no coge el tronco");
			}
			machine(ctx, 1, 1, 1).setLifting(true);
		});
		ctx.runAtTick(100, () -> {
			var e = hook.get();
			if (!ctx.getBlockState(new BlockPos(4, 1, 1)).isAir() || e.carried().isEmpty()) {
				throw new GameTestException("El tronco no va colgado");
			}
			if (!com.evolvedwarfare.engineering.CraneBlock.turnJib(ctx.getWorld(), ctx.getAbsolutePos(new BlockPos(1, 1, 1)), null)) {
				throw new GameTestException("La pluma no gira");
			}
			machine(ctx, 1, 1, 1).setLifting(false);
		});
		ctx.runAtTick(150, () -> {
			var e = hook.get();
			LOG.info("[EW-GAMETEST] grúa: gancho girado en {}, lleva {}", e.getPos(), e.carried());
			if (!e.grabOrRelease(null)) {
				throw new GameTestException("El gancho no suelta el tronco");
			}
		});
		ctx.runAtTick(155, () -> {
			boolean placed = ctx.getBlockState(new BlockPos(1, 1, 4)).isOf(Blocks.OAK_LOG);
			LOG.info("[EW-GAMETEST] grúa: tronco puesto en su sitio nuevo {}", placed);
			if (!placed) {
				throw new GameTestException("El tronco no queda en el sitio nuevo");
			}
			ctx.complete();
		});
	}

	// ------------------------------------------------------------------ fase 75: la automatización

	/** El embrague corta el giro con señal de redstone y lo devuelve sin ella; el inversor da la vuelta al lado positivo. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "machine_clutch", tickLimit = 120)
	public void clutchAndGearshift(TestContext ctx) {
		ctx.setBlockState(1, 2, 1, Engineering.HAND_CRANK.getDefaultState().with(Properties.FACING, Direction.WEST));
		ctx.setBlockState(2, 2, 1, Engineering.CLUTCH.getDefaultState().with(Properties.AXIS, Direction.Axis.X));
		ctx.setBlockState(3, 2, 1, Engineering.GEARSHIFT.getDefaultState().with(Properties.AXIS, Direction.Axis.X));
		ctx.setBlockState(4, 2, 1, Engineering.SHAFT.getDefaultState().with(Properties.AXIS, Direction.Axis.X));
		keepCranking(ctx, 1, 2, 1, 110);
		float[] before = new float[2];
		ctx.runAtTick(20, () -> {
			before[0] = be(ctx, 3, 2, 1).speed();
			before[1] = be(ctx, 4, 2, 1).speed();
			LOG.info("[EW-GAMETEST] embrague suelto: inversor {}, eje {}", before[0], before[1]);
			if (Math.abs(before[1]) < 1 || before[0] != before[1]) {
				throw new GameTestException("El giro no pasa por el embrague y el inversor");
			}
			ctx.setBlockState(3, 3, 1, Blocks.REDSTONE_BLOCK);
		});
		ctx.runAtTick(40, () -> {
			float g = be(ctx, 3, 2, 1).speed(), sh = be(ctx, 4, 2, 1).speed();
			LOG.info("[EW-GAMETEST] inversor con señal: inversor {}, eje {}", g, sh);
			if (Math.abs(g) < 1 || sh != -g) {
				throw new GameTestException("El inversor no invierte el giro");
			}
			ctx.setBlockState(3, 3, 1, Blocks.AIR);
			ctx.setBlockState(2, 3, 1, Blocks.REDSTONE_BLOCK);
		});
		ctx.runAtTick(60, () -> {
			float sh = be(ctx, 4, 2, 1).speed(), c = be(ctx, 1, 2, 1).speed();
			LOG.info("[EW-GAMETEST] embrague con señal: manivela {}, eje {}", c, sh);
			if (sh != 0 || Math.abs(c) < 1) {
				throw new GameTestException("El embrague no corta el giro");
			}
			ctx.setBlockState(2, 3, 1, Blocks.AIR);
		});
		ctx.runAtTick(80, () -> {
			float sh = be(ctx, 4, 2, 1).speed();
			if (sh != before[1]) {
				throw new GameTestException("El embrague no vuelve a pasar el giro");
			}
			ctx.complete();
		});
	}

	/** La leva da pulsos de redstone mientras gira: cuatro por vuelta. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "machine_cam", tickLimit = 120)
	public void camPulsesRedstone(TestContext ctx) {
		ctx.setBlockState(1, 2, 1, Engineering.HAND_CRANK.getDefaultState().with(Properties.FACING, Direction.WEST));
		ctx.setBlockState(2, 2, 1, Engineering.REDSTONE_CAM.getDefaultState().with(Properties.AXIS, Direction.Axis.X));
		ctx.setBlockState(2, 3, 1, Blocks.REDSTONE_LAMP);
		keepCranking(ctx, 1, 2, 1, 100);
		int[] changes = {0, 0};
		boolean[] last = {false};
		for (int t = 5; t <= 95; t++) {
			ctx.runAtTick(t, () -> {
				boolean on = ctx.getBlockState(new BlockPos(2, 2, 1)).get(Properties.POWERED);
				if (on != last[0]) {
					changes[0]++;
					last[0] = on;
				}
				if (ctx.getBlockState(new BlockPos(2, 3, 1)).get(Properties.LIT)) {
					changes[1]++;
				}
			});
		}
		ctx.runAtTick(100, () -> {
			LOG.info("[EW-GAMETEST] leva: {} cambios de señal, lámpara encendida {} tics", changes[0], changes[1]);
			if (changes[0] < 6 || changes[1] < 10) {
				throw new GameTestException("La leva no da pulsos de redstone");
			}
			ctx.complete();
		});
	}

	/** El torno abre el rastrillo con señal de redstone (lo recoge) y lo cierra sin ella (lo baja hasta el suelo). */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "machine_portcullis", tickLimit = 200)
	public void winchOpensAndClosesThePortcullis(TestContext ctx) {
		for (int z = 1; z <= 3; z++) {
			ctx.setBlockState(2, 1, z, Blocks.STONE);
			for (int y = 2; y <= 5; y++) {
				ctx.setBlockState(2, y, z, Engineering.PORTCULLIS);
			}
		}
		ctx.setBlockState(2, 6, 2, Engineering.PORTCULLIS_WINCH.getDefaultState().with(Properties.HORIZONTAL_FACING, Direction.EAST));
		ctx.setBlockState(2, 6, 1, Engineering.HAND_CRANK.getDefaultState().with(Properties.FACING, Direction.NORTH));
		keepCranking(ctx, 2, 6, 1, 190);
		ctx.runAtTick(5, () -> ctx.setBlockState(3, 6, 2, Blocks.REDSTONE_BLOCK));
		ctx.runAtTick(80, () -> {
			boolean open = true;
			for (int z = 1; z <= 3; z++) {
				open &= ctx.getBlockState(new BlockPos(2, 2, z)).isAir() && ctx.getBlockState(new BlockPos(2, 4, z)).isAir()
						&& ctx.getBlockState(new BlockPos(2, 5, z)).isOf(Engineering.PORTCULLIS);
			}
			LOG.info("[EW-GAMETEST] rastrillo abierto {}, filas recogidas {}", open, machine(ctx, 2, 6, 2).stored());
			if (!open || machine(ctx, 2, 6, 2).stored() != 9) {
				throw new GameTestException("El torno no abre el rastrillo");
			}
			ctx.setBlockState(3, 6, 2, Blocks.AIR);
		});
		ctx.runAtTick(160, () -> {
			boolean closed = true;
			for (int z = 1; z <= 3; z++) {
				for (int y = 2; y <= 5; y++) {
					closed &= ctx.getBlockState(new BlockPos(2, y, z)).isOf(Engineering.PORTCULLIS);
				}
			}
			LOG.info("[EW-GAMETEST] rastrillo cerrado {}, filas recogidas {}", closed, machine(ctx, 2, 6, 2).stored());
			if (!closed || machine(ctx, 2, 6, 2).stored() != 0) {
				throw new GameTestException("El torno no cierra el rastrillo");
			}
			ctx.complete();
		});
	}

	/** El reloj de torre: la hora de cada momento, las campanadas de cada hora, y la señal mientras suenan. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "machine_clock", tickLimit = 200)
	public void towerClockStrikesTheHours(TestContext ctx) {
		if (com.evolvedwarfare.engineering.TowerClockBlock.hourOf(0) != 6 || com.evolvedwarfare.engineering.TowerClockBlock.hourOf(18000) != 0
				|| com.evolvedwarfare.engineering.TowerClockBlock.strokes(0) != 12 || com.evolvedwarfare.engineering.TowerClockBlock.strokes(15) != 3) {
			throw new GameTestException("Las horas del reloj no cuadran");
		}
		ctx.setBlockState(2, 2, 2, Engineering.TOWER_CLOCK.getDefaultState().with(Properties.HORIZONTAL_FACING, Direction.SOUTH));
		ctx.setBlockState(2, 1, 2, Engineering.HAND_CRANK.getDefaultState().with(Properties.FACING, Direction.DOWN));
		keepCranking(ctx, 2, 1, 2, 190);
		ctx.runAtTick(20, () -> com.evolvedwarfare.engineering.TowerClockBlock.chime(machine(ctx, 2, 2, 2), 3));
		int[] left = new int[1];
		ctx.runAtTick(30, () -> {
			if (!ctx.getBlockState(new BlockPos(2, 2, 2)).get(Properties.POWERED)) {
				throw new GameTestException("El reloj no da señal mientras suena");
			}
			left[0] = machine(ctx, 2, 2, 2).hits();
		});
		ctx.runAtTick(140, () -> {
			var clock = machine(ctx, 2, 2, 2);
			long time = Math.floorMod(ctx.getWorld().getTimeOfDay(), 24000L);
			LOG.info("[EW-GAMETEST] reloj: gira {}, marca {}, hora del mundo {}, campanadas que quedaban {} y quedan {}", clock.speed(), clock.phase(),
					time, left[0], clock.hits());
			// (si en medio da la hora el reloj, se suman sus campanadas: solo se mira que van sonando)
			if (Math.abs(clock.speed()) < 1 || Math.abs(clock.phase() - time) > 2 || clock.hits() == left[0]) {
				throw new GameTestException("El reloj no marca la hora o no da las campanadas");
			}
			ctx.complete();
		});
	}

	// ------------------------------------------------------------------ fase 77: placas, anillas y acero

	/** El martinete aplana en placas los lingotes que hay en el yunque (cuatro golpes cada una). */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "metal_plates", tickLimit = 300)
	public void tripHammerFlattensPlates(TestContext ctx) {
		ctx.setBlockState(1, 2, 2, Engineering.TRIP_HAMMER.getDefaultState().with(Properties.HORIZONTAL_FACING, Direction.EAST));
		ctx.setBlockState(1, 2, 1, Engineering.HAND_CRANK.getDefaultState().with(Properties.FACING, Direction.NORTH));
		ctx.setBlockState(2, 0, 2, Blocks.STONE);
		ctx.setBlockState(2, 1, 2, Blocks.ANVIL);
		ctx.runAtTick(2, () -> {
			BlockPos a = ctx.getAbsolutePos(new BlockPos(2, 1, 2));
			var e = new net.minecraft.entity.ItemEntity(ctx.getWorld(), a.getX() + 0.5, a.getY() + 1.05, a.getZ() + 0.5,
					new net.minecraft.item.ItemStack(net.minecraft.item.Items.IRON_INGOT, 2), 0, 0, 0);
			e.setPickupDelayInfinite();
			ctx.getWorld().spawnEntity(e);
		});
		keepCranking(ctx, 1, 2, 1, 260);
		ctx.runAtTick(250, () -> {
			BlockPos a = ctx.getAbsolutePos(new BlockPos(2, 1, 2));
			int plates = 0, ingots = 0;
			for (var e : ctx.getWorld().getEntitiesByClass(net.minecraft.entity.ItemEntity.class, new net.minecraft.util.math.Box(a).expand(2), x -> true)) {
				if (e.getStack().isOf(com.evolvedwarfare.engineering.Metalwork.IRON_PLATE)) {
					plates += e.getStack().getCount();
				} else if (e.getStack().isOf(net.minecraft.item.Items.IRON_INGOT)) {
					ingots += e.getStack().getCount();
				}
			}
			LOG.info("[EW-GAMETEST] martinete: placas {}, lingotes que quedan {}", plates, ingots);
			if (plates != 2 || ingots != 0) {
				throw new GameTestException("El martinete no aplana los lingotes en placas");
			}
			ctx.complete();
		});
	}

	/** Hierro fundido + carbón en el crisol, con el fuelle soplando: acero. Sin fuelle, no. Y frío, salen lingotes de acero. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "metal_steel", tickLimit = 120)
	public void bellowsAndCoalMakeSteel(TestContext ctx) {
		ctx.setBlockState(2, 1, 2, Blocks.MAGMA_BLOCK);
		ctx.setBlockState(2, 2, 2, com.evolvedwarfare.registry.ModBlocks.CRUCIBLE);
		ctx.setBlockState(1, 2, 2, Engineering.BELLOWS.getDefaultState().with(Properties.HORIZONTAL_FACING, Direction.EAST));
		ctx.setBlockState(1, 2, 1, Engineering.HAND_CRANK.getDefaultState().with(Properties.FACING, Direction.NORTH));
		ctx.setBlockState(5, 1, 2, Blocks.MAGMA_BLOCK);
		ctx.setBlockState(5, 2, 2, com.evolvedwarfare.registry.ModBlocks.CRUCIBLE);
		keepCranking(ctx, 1, 2, 1, 100);
		ctx.runAtTick(5, () -> {
			for (int x : new int[] {2, 5}) {
				var c = (com.evolvedwarfare.smithing.CrucibleBlockEntity) ctx.getWorld().getBlockEntity(ctx.getAbsolutePos(new BlockPos(x, 2, 2)));
				c.setContents(new com.evolvedwarfare.smithing.CrucibleContents("iron", 0, 2.0F, 0L));
			}
		});
		ctx.runAtTick(30, () -> {
			var world = ctx.getWorld();
			var coal = new net.minecraft.item.ItemStack(net.minecraft.item.Items.COAL, 3);
			boolean noBellows = com.evolvedwarfare.engineering.Metalwork.carburize(world, ctx.getAbsolutePos(new BlockPos(5, 2, 2)), coal, null);
			boolean steel = com.evolvedwarfare.engineering.Metalwork.carburize(world, ctx.getAbsolutePos(new BlockPos(2, 2, 2)), coal, null);
			var c = (com.evolvedwarfare.smithing.CrucibleBlockEntity) world.getBlockEntity(ctx.getAbsolutePos(new BlockPos(2, 2, 2)));
			LOG.info("[EW-GAMETEST] acero: sin fuelle {}, con fuelle {}, crisol {}, carbón que queda {}", noBellows, steel, c.contents(), coal.getCount());
			if (noBellows || !steel || !"steel".equals(c.contents().metal()) || coal.getCount() != 1) {
				throw new GameTestException("El crisol no hace acero con carbón y fuelle");
			}
			// ya frío: los lingotes
			c.setContents(new com.evolvedwarfare.smithing.CrucibleContents("steel", 2, 0.0F, 0L));
			var player = ctx.createMockPlayer(net.minecraft.world.GameMode.SURVIVAL);
			boolean took = com.evolvedwarfare.engineering.Metalwork.takeIngots(world, ctx.getAbsolutePos(new BlockPos(2, 2, 2)), player);
			int ingots = player.getInventory().count(com.evolvedwarfare.engineering.Metalwork.STEEL_INGOT);
			LOG.info("[EW-GAMETEST] acero: lingotes sacados {} ({})", ingots, took);
			if (!took || ingots != 2) {
				throw new GameTestException("No salen los lingotes de acero del crisol");
			}
			ctx.complete();
		});
	}

	/** Una hoja de espada de acero da una espada de hierro mejorada (más durabilidad, más daño) que se llama de acero. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "metal_steel_tools")
	public void steelPartsMakeBetterTools(TestContext ctx) {
		var world = ctx.getWorld();
		var recipe = new com.evolvedwarfare.smithing.ForgedToolRecipe(net.minecraft.recipe.book.CraftingRecipeCategory.EQUIPMENT);
		java.util.List<net.minecraft.item.ItemStack> grid = new java.util.ArrayList<>(java.util.List.of(
				com.evolvedwarfare.smithing.Smithing.part(com.evolvedwarfare.smithing.ForgedPart.SWORD_BLADE, com.evolvedwarfare.smithing.ForgeMaterial.STEEL, 2),
				new net.minecraft.item.ItemStack(net.minecraft.item.Items.STICK), new net.minecraft.item.ItemStack(net.minecraft.item.Items.LEATHER)));
		while (grid.size() < 9) {
			grid.add(net.minecraft.item.ItemStack.EMPTY);
		}
		var input = net.minecraft.recipe.input.CraftingRecipeInput.create(3, 3, grid);
		var sword = recipe.matches(input, world) ? recipe.craft(input, world.getRegistryManager()) : net.minecraft.item.ItemStack.EMPTY;
		int max = sword.getOrDefault(net.minecraft.component.DataComponentTypes.MAX_DAMAGE, 0);
		String name = sword.getName().getString();
		LOG.info("[EW-GAMETEST] acero: {} «{}», durabilidad {}, acero {}", sword.getItem(), name, max,
				sword.get(com.evolvedwarfare.engineering.Metalwork.STEEL));
		if (!sword.isOf(net.minecraft.item.Items.IRON_SWORD) || max != 375 || !Boolean.TRUE.equals(sword.get(com.evolvedwarfare.engineering.Metalwork.STEEL))) {
			throw new GameTestException("La hoja de acero no da una espada de acero");
		}
		ctx.complete();
	}

	// ------------------------------------------------------------------ fase 78: armaduras de placas y escudos

	private static double sum(net.minecraft.item.ItemStack stack, net.minecraft.registry.entry.RegistryEntry<net.minecraft.entity.attribute.EntityAttribute> attr) {
		double total = 0;
		var mods = stack.getOrDefault(net.minecraft.component.DataComponentTypes.ATTRIBUTE_MODIFIERS,
				net.minecraft.component.type.AttributeModifiersComponent.DEFAULT);
		for (var e : mods.modifiers()) {
			if (e.attribute().equals(attr)) {
				total += e.modifier().value();
			}
		}
		return total;
	}

	/** Las armaduras de placas protegen más que las de su metal pero pesan; los escudos aguantan más y se reparan con placas. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "metal_armor")
	public void plateArmorAndShields(TestContext ctx) {
		double[] armor = new double[2], speed = new double[2], tough = new double[2];
		int k = 0;
		for (var item : com.evolvedwarfare.engineering.Metalwork.PLATE_ARMOR) {
			var stack = new net.minecraft.item.ItemStack(item);
			int set = k++ / 4;
			armor[set] += sum(stack, net.minecraft.entity.attribute.EntityAttributes.GENERIC_ARMOR);
			tough[set] += sum(stack, net.minecraft.entity.attribute.EntityAttributes.GENERIC_ARMOR_TOUGHNESS);
			speed[set] += sum(stack, net.minecraft.entity.attribute.EntityAttributes.GENERIC_MOVEMENT_SPEED);
		}
		var shield = new net.minecraft.item.ItemStack(com.evolvedwarfare.engineering.Metalwork.REINFORCED_SHIELD);
		var pavise = new net.minecraft.item.ItemStack(com.evolvedwarfare.engineering.Metalwork.PAVISE);
		int shieldMax = shield.getMaxDamage(), paviseMax = pavise.getMaxDamage();
		boolean repair = shield.getItem().canRepair(shield, new net.minecraft.item.ItemStack(com.evolvedwarfare.engineering.Metalwork.IRON_PLATE));
		LOG.info("[EW-GAMETEST] placas: hierro armadura {} dureza {} velocidad {}; acero armadura {} dureza {} velocidad {}; escudo {} pavés {} (repara {})",
				armor[0], tough[0], speed[0], armor[1], tough[1], speed[1], shieldMax, paviseMax, repair);
		if (armor[0] != 19 || armor[1] != 21 || Math.abs(speed[1] + 0.12) > 1e-6 || tough[1] != 10 || shieldMax != 672 || paviseMax != 1008 || !repair) {
			throw new GameTestException("Las armaduras de placas o los escudos no tienen sus números");
		}
		ctx.complete();
	}
}
