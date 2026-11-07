package com.evolvedwarfare.gametest;

import com.evolvedwarfare.hunting.BloodStainEntity;
import com.evolvedwarfare.hunting.CarcassEntity;
import com.evolvedwarfare.hunting.Hunting;
import com.evolvedwarfare.network.ActivityPayload;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.GameTestException;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** La caza: el animal cazado se queda en el suelo; con el cuchillo se despelleja (cuero y carne) y queda sangre. */
public class HuntingGameTests implements FabricGameTest {
	private static final Logger LOG = LoggerFactory.getLogger("ew-gametest");

	@GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 400)
	public void huntedAnimalsAreSkinned(TestContext ctx) {
		ServerPlayerEntity player = (ServerPlayerEntity) ctx.createMockCreativeServerPlayerInWorld();
		Vec3d at = ctx.getAbsolute(new Vec3d(2.5, 2, 2.5));
		player.refreshPositionAndAngles(at.x + 1.2, at.y, at.z, 90, 30);
		var cow = ctx.spawnEntity(EntityType.COW, new net.minecraft.util.math.BlockPos(2, 2, 2));
		cow.damage(ctx.getWorld().getDamageSources().playerAttack(player), 100.0F);
		Box area = new Box(at, at).expand(4.0);
		ctx.runAtTick(30, () -> {
			var carcasses = ctx.getWorld().getEntitiesByClass(CarcassEntity.class, area, e -> true);
			var loose = ctx.getWorld().getEntitiesByClass(ItemEntity.class, area, e -> e.getStack().isOf(Items.BEEF) || e.getStack().isOf(Items.LEATHER));
			LOG.info("[EW-GAMETEST] caza: abatidos {}, sueltos {}", carcasses.size(), loose.size());
			if (carcasses.size() != 1 || !loose.isEmpty()) {
				throw new GameTestException("La vaca no se queda en el suelo: " + carcasses.size() + " abatidas, " + loose.size() + " cosas sueltas");
			}
			CarcassEntity carcass = carcasses.get(0);
			player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Hunting.FLINT_KNIFE));
			carcass.interact(player, Hand.MAIN_HAND);
			LOG.info("[EW-GAMETEST] caza: jugador en {} ({} en el mundo), abatido en {}, mano {}", player.getPos(),
					ctx.getWorld().getPlayerByUuid(player.getUuid()) != null, carcass.getPos(), player.getMainHandStack());
			if (ActivityPayload.current(player) != ActivityPayload.SKINNING) {
				throw new GameTestException("No empieza a despellejar");
			}
		});
		ctx.runAtTick(35, () -> LOG.info("[EW-GAMETEST] caza: tras 5 tics, actividad {}, avance {}, jugador en {}, mano {}", ActivityPayload.current(player),
				ctx.getWorld().getEntitiesByClass(CarcassEntity.class, area, e -> true).stream().map(c -> c.progress()).toList(), player.getPos(),
				player.getMainHandStack()));
		ctx.runAtTick(30 + Hunting.SKIN_TIME + 10, () -> {
			int leather = 0, beef = 0;
			for (ItemEntity e : ctx.getWorld().getEntitiesByClass(ItemEntity.class, area, e -> true)) {
				if (e.getStack().isOf(Items.LEATHER)) {
					leather += e.getStack().getCount();
				} else if (e.getStack().isOf(Items.BEEF) || e.getStack().isOf(Items.COOKED_BEEF)) {
					beef += e.getStack().getCount();
				}
			}
			int carcasses = ctx.getWorld().getEntitiesByClass(CarcassEntity.class, area, e -> true).size();
			int stains = ctx.getWorld().getEntitiesByClass(BloodStainEntity.class, area, e -> true).size();
			LOG.info("[EW-GAMETEST] despellejada: cuero {}, carne {}, abatidos {}, sangre {}, actividad {}", leather, beef, carcasses, stains,
					ActivityPayload.current(player));
			if (leather < 4 || leather > 10 || beef < 2 || beef > 6 || carcasses != 0 || stains != 1 || ActivityPayload.current(player) != ActivityPayload.NONE) {
				throw new GameTestException("Despellejar no da lo que debe: cuero " + leather + ", carne " + beef + ", abatidos " + carcasses + ", sangre " + stains);
			}
		});
		ctx.runAtTick(30 + Hunting.SKIN_TIME + 10 + BloodStainEntity.LIFE + 5, () -> {
			int stains = ctx.getWorld().getEntitiesByClass(BloodStainEntity.class, area, e -> true).size();
			if (stains != 0) {
				throw new GameTestException("La sangre no se va");
			}
			ctx.getWorld().getEntitiesByClass(ItemEntity.class, area, e -> true).forEach(ItemEntity::discard);
			ctx.complete();
		});
	}
}
