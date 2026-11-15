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
}
