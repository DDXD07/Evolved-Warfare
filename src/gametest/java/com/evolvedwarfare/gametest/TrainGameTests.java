package com.evolvedwarfare.gametest;

import com.evolvedwarfare.train.FreightCart;
import com.evolvedwarfare.train.LocomotiveCart;
import com.evolvedwarfare.train.PassengerCart;
import com.evolvedwarfare.train.TenderCart;
import com.evolvedwarfare.train.TrainCar;
import com.evolvedwarfare.train.Trains;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.block.RailBlock;
import net.minecraft.block.enums.RailShape;
import net.minecraft.entity.vehicle.AbstractMinecartEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.GameTestException;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** El tren de vapor: la locomotora quema el carbón del ténder y arrastra a sus vagones enganchados, a su distancia. */
public class TrainGameTests implements FabricGameTest {
	private static final Logger LOG = LoggerFactory.getLogger("ew-gametest");

	private static <T extends AbstractMinecartEntity> T put(ServerWorld world, net.minecraft.entity.EntityType<T> type, BlockPos rail) {
		T cart = type.create(world);
		cart.refreshPositionAndAngles(rail.getX() + 0.5, rail.getY() + 0.0625, rail.getZ() + 0.5, 0, 0);
		world.spawnEntity(cart);
		return cart;
	}

	@GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 300)
	public void steamTrainPullsItsCars(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		BlockPos o = ctx.getAbsolutePos(new BlockPos(0, 1, 0)).add(0, 0, 300);
		java.util.List<ChunkPos> forced = new java.util.ArrayList<>();
		for (int i = -2; i < 70; i += 8) {
			ChunkPos c = new ChunkPos(o.east(i));
			forced.add(c);
			world.setChunkForced(c.x, c.z, true);
		}
		for (int i = -4; i < 64; i++) {
			world.setBlockState(o.east(i).down(), Blocks.STONE.getDefaultState());
			world.setBlockState(o.east(i), Blocks.RAIL.getDefaultState().with(RailBlock.SHAPE, RailShape.EAST_WEST));
			world.setBlockState(o.east(i).up(), Blocks.AIR.getDefaultState());
			world.setBlockState(o.east(i).up(2), Blocks.AIR.getDefaultState());
		}
		LocomotiveCart loco = put(world, Trains.LOCOMOTIVE, o.east(10));
		loco.setHeading(new Vec3d(1, 0, 0));
		TenderCart tender = put(world, Trains.TENDER, o.east(8));
		FreightCart freight = put(world, Trains.FREIGHT, o.east(6));
		PassengerCart passenger = put(world, Trains.PASSENGER, o.east(4));
		TrainCar.couple(loco, tender);
		TrainCar.couple(tender, freight);
		TrainCar.couple(freight, passenger);
		tender.setStack(0, new ItemStack(Items.COAL, 8));
		freight.setStack(40, new ItemStack(Items.DIAMOND, 3));
		loco.setThrottle(LocomotiveCart.MAX_NOTCH);
		double start = loco.getX();
		ctx.runAtTick(160, () -> {
			double run = loco.getX() - start;
			double g1 = loco.getX() - tender.getX(), g2 = tender.getX() - freight.getX(), g3 = freight.getX() - passenger.getX();
			int coal = tender.getStack(0).getCount();
			LOG.info("[EW-GAMETEST] tren: recorre {} bloques, huecos {} / {} / {}, carbón en el ténder {}, fuego {}, velocidad {}, en mercancías {}", run, g1, g2,
					g3, coal, loco.fuel(), loco.getVelocity().x, freight.size());
			boolean ok = run > 12 && Math.abs(g1 - TrainCar.SPACING) < 0.8 && Math.abs(g2 - TrainCar.SPACING) < 0.8 && Math.abs(g3 - TrainCar.SPACING) < 0.8
					&& coal < 8 && freight.size() == 54 && freight.getStack(40).isOf(Items.DIAMOND);
			for (var c : new AbstractMinecartEntity[] {loco, tender, freight, passenger}) {
				c.discard();
			}
			forced.forEach(c -> world.setChunkForced(c.x, c.z, false));
			if (!ok) {
				throw new GameTestException("El tren no tira bien de sus vagones: recorre " + run + ", huecos " + g1 + " / " + g2 + " / " + g3 + ", carbón " + coal);
			}
			ctx.complete();
		});
	}
}
