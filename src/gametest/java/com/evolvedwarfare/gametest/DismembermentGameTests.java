package com.evolvedwarfare.gametest;

import com.evolvedwarfare.combat.Dismemberment;
import com.evolvedwarfare.combat.SeveredLimbEntity;
import com.evolvedwarfare.entity.SoldierEntity;
import com.evolvedwarfare.registry.ModEntities;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.GameTestException;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Desmembramiento: las probabilidades, y lo que pasa al cortar un brazo, una pierna o la cabeza (y que se guarda). */
public class DismembermentGameTests implements FabricGameTest {
	private static final Logger LOG = LoggerFactory.getLogger("ew-gametest");

	private static void check(boolean ok, String message) {
		if (!ok) {
			throw new GameTestException(message);
		}
	}

	private static SoldierEntity soldier(TestContext ctx) {
		for (int x = 0; x < 5; x++) {
			for (int z = 0; z < 5; z++) {
				ctx.setBlockState(x, 0, z, Blocks.STONE);
			}
		}
		SoldierEntity s = ctx.spawnEntity(ModEntities.SOLDIER, new BlockPos(2, 1, 2));
		s.equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
		s.equipStack(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
		return s;
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void chancesMakeSense(TestContext ctx) {
		float sword = Dismemberment.limbChance(1.0F, 7.0F, 1.0F);
		float axe = Dismemberment.limbChance(1.6F, 7.0F, 1.0F);
		float hurt = Dismemberment.limbChance(1.0F, 7.0F, 0.2F);
		float weak = Dismemberment.limbChance(1.0F, 1.0F, 1.0F);
		float head = Dismemberment.headChance(1.0F, false, 1.0F);
		float crit = Dismemberment.headChance(1.6F, true, 0.3F);
		LOG.info("[EW-GAMETEST] desmembrar: espada {}, hacha {}, herido {}, flojo {}; cabeza {}, crítico con hacha y herido {}", sword, axe, hurt,
				weak, head, crit);
		check(axe > sword && hurt > sword && weak < sword, "Las probabilidades no suben con el filo, la herida y la fuerza");
		check(sword > 0.03F && sword < 0.2F, "Cortar con una espada no es raro ni casi seguro: " + sword);
		check(head < sword && head > 0.0F, "Decapitar no es más difícil que cortar un brazo: " + head);
		check(crit > head * 3.0F, "Un crítico con hacha a uno herido no ayuda a decapitar");
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void cutArmsAndLegs(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		SoldierEntity s = soldier(ctx);
		DamageSource source = world.getDamageSources().generic();
		double speed = s.getAttributeValue(EntityAttributes.GENERIC_MOVEMENT_SPEED);
		Dismemberment.cut(s, source, Dismemberment.RIGHT_ARM);
		boolean droppedSword = s.getMainHandStack().isEmpty();
		s.equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_AXE));
		boolean cannotHold = s.getMainHandStack().isEmpty();
		boolean bowNeedsTwo = !Dismemberment.hasBothArms(s) && Dismemberment.hasAnArm(s);
		Dismemberment.cut(s, source, Dismemberment.LEFT_ARM);
		boolean droppedShield = s.getOffHandStack().isEmpty();
		boolean noArms = !Dismemberment.hasAnArm(s) && !s.tryAttack(s);
		Dismemberment.cut(s, source, Dismemberment.RIGHT_LEG);
		double oneLeg = s.getAttributeValue(EntityAttributes.GENERIC_MOVEMENT_SPEED);
		Dismemberment.cut(s, source, Dismemberment.LEFT_LEG);
		double noLegs = s.getAttributeValue(EntityAttributes.GENERIC_MOVEMENT_SPEED);
		Box box = new Box(ctx.getAbsolutePos(new BlockPos(2, 1, 2))).expand(4.0);
		int items = world.getEntitiesByClass(ItemEntity.class, box, e -> true).size();
		int limbs = world.getEntitiesByClass(SeveredLimbEntity.class, box, e -> true).size();
		NbtCompound nbt = new NbtCompound();
		s.writeNbt(nbt);
		SoldierEntity copy = ModEntities.SOLDIER.create(world);
		copy.readNbt(nbt);
		boolean saved = Dismemberment.severed(copy) == (Dismemberment.RIGHT_ARM | Dismemberment.LEFT_ARM | Dismemberment.RIGHT_LEG
				| Dismemberment.LEFT_LEG);
		LOG.info("[EW-GAMETEST] desmembrar: suelta espada {}, no coge el hacha {}, sin arco {}, suelta escudo {}, sin brazos no pega {}, velocidad {} -> {} -> {}, "
				+ "en el suelo {} cosas y {} trozos, guardado {}", droppedSword, cannotHold, bowNeedsTwo, droppedShield, noArms, speed, oneLeg, noLegs,
				items, limbs, saved);
		check(droppedSword && cannotHold, "Sin el brazo derecho sigue con el arma");
		check(bowNeedsTwo, "Con un brazo cuenta como que puede tirar con arco");
		check(droppedShield, "Sin el brazo izquierdo sigue con el escudo");
		check(noArms, "Sin brazos puede golpear");
		check(oneLeg < speed * 0.5 && noLegs < speed * 0.2, "Sin piernas va igual de rápido: " + oneLeg + " / " + noLegs);
		check(items >= 2, "No ha soltado el arma y el escudo: " + items);
		check(limbs == 4, "No han salido volando los cuatro trozos: " + limbs);
		check(saved, "Lo cortado no se guarda");
		s.discard();
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void decapitationKills(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		SoldierEntity s = soldier(ctx);
		Dismemberment.decapitate(s, world.getDamageSources().generic());
		Box box = new Box(ctx.getAbsolutePos(new BlockPos(2, 1, 2))).expand(4.0);
		boolean head = world.getEntitiesByClass(SeveredLimbEntity.class, box, e -> e.part() == Dismemberment.HEAD).size() == 1;
		LOG.info("[EW-GAMETEST] decapitar: vivo {}, sin cabeza {}, la cabeza en el suelo {}", s.isAlive(), Dismemberment.isSevered(s, Dismemberment.HEAD),
				head);
		check(!s.isAlive() || s.getHealth() <= 0.0F, "Decapitado y sigue vivo");
		check(Dismemberment.isSevered(s, Dismemberment.HEAD) && head, "No ha salido la cabeza");
		ctx.complete();
	}
}
