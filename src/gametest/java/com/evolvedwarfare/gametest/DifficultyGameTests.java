package com.evolvedwarfare.gametest;

import com.evolvedwarfare.config.EwConfig;
import com.evolvedwarfare.difficulty.Difficulty;
import com.evolvedwarfare.difficulty.Elites;
import com.evolvedwarfare.difficulty.Threat;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.entity.passive.CowEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.GameTestException;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.GameMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** La dificultad que sube y los élite (fase 95). */
public class DifficultyGameTests implements FabricGameTest {
	private static final Logger LOG = LoggerFactory.getLogger("ew-gametest");

	private static void check(boolean ok, String message) {
		if (!ok) {
			throw new GameTestException(message);
		}
	}

	private static void floor(TestContext ctx) {
		for (int x = 0; x < 6; x++) {
			for (int z = 0; z < 6; z++) {
				ctx.setBlockState(x, 0, z, Blocks.STONE);
			}
		}
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "difficulty")
	public void levelGrowsWithDistance(TestContext ctx) {
		BlockPos spawn = ctx.getWorld().getServer().getOverworld().getSpawnPos();
		int per = EwConfig.get().difficultyBlocksPerLevel;
		int near = Difficulty.level(ctx.getWorld(), spawn);
		int far = Difficulty.level(ctx.getWorld(), spawn.add(per * 5 + 10, 0, 0));
		int farther = Difficulty.level(ctx.getWorld(), spawn.add(0, 0, per * 50));
		LOG.info("[EW-GAMETEST] peligro: en el punto de aparición {}, a 5 niveles {}, muy lejos {}", near, far, farther);
		check(far - near == 5, "A " + (per * 5) + " bloques no sube 5 niveles: " + near + " -> " + far);
		check(farther == EwConfig.get().difficultyMaxLevel, "El nivel no tiene tope: " + farther);
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "difficulty")
	public void levelMakesMonstersTougher(TestContext ctx) {
		floor(ctx);
		ZombieEntity zombie = ctx.spawnEntity(EntityType.ZOMBIE, 2, 1, 2);
		double health = zombie.getMaxHealth(), damage = zombie.getAttributeValue(EntityAttributes.GENERIC_ATTACK_DAMAGE);
		Difficulty.apply(zombie, 10);
		Difficulty.apply(zombie, 10);
		double h10 = zombie.getMaxHealth(), d10 = zombie.getAttributeValue(EntityAttributes.GENERIC_ATTACK_DAMAGE);
		LOG.info("[EW-GAMETEST] nivel 10: vida {} -> {} (llena {}), daño {} -> {}, armadura {}", health, h10, zombie.getHealth(), damage, d10,
				zombie.getArmor());
		check(Math.abs(h10 - health * (1 + 10 * EwConfig.get().difficultyHealthPerLevel)) < 0.01, "La vida no sube con el nivel: " + h10);
		check(d10 > damage, "El daño no sube con el nivel");
		check(zombie.getHealth() == zombie.getMaxHealth(), "No empieza con la vida llena");
		check(Difficulty.affected(zombie), "Al zombi no le afecta");
		CowEntity cow = ctx.spawnEntity(EntityType.COW, 4, 1, 4);
		check(!Difficulty.affected(cow), "A la vaca le afecta");
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "difficulty")
	public void elitesAreStrongAndHaveTraits(TestContext ctx) {
		floor(ctx);
		ZombieEntity zombie = ctx.spawnEntity(EntityType.ZOMBIE, 2, 1, 2);
		double health = zombie.getMaxHealth(), armor = zombie.getArmor();
		Difficulty.apply(zombie, 0);
		Elites.make(zombie, List.of(Elites.Affix.ARMORED, Elites.Affix.BURNING));
		Threat t = zombie.getAttached(Difficulty.THREAT);
		LOG.info("[EW-GAMETEST] élite: {} vida {} -> {}, armadura {} -> {}, tamaño {}, fuego {}", zombie.getCustomName().getString(), health,
				zombie.getMaxHealth(), armor, zombie.getArmor(), zombie.getAttributeValue(EntityAttributes.GENERIC_SCALE),
				zombie.hasStatusEffect(StatusEffects.FIRE_RESISTANCE));
		check(t != null && t.elite() && t.has(Elites.Affix.ARMORED), "No queda marcado como élite");
		check(zombie.getMaxHealth() >= health * 2.4, "El élite no tiene mucha más vida");
		check(zombie.getArmor() >= armor + 8, "El élite de Hierro no lleva armadura");
		check(zombie.hasStatusEffect(StatusEffects.FIRE_RESISTANCE), "El élite de Fuego se quema");
		check(zombie.hasCustomName() && zombie.isCustomNameVisible(), "El élite no lleva su nombre a la vista");
		check(zombie.getAttributeValue(EntityAttributes.GENERIC_SCALE) > 1.1, "El élite no es más grande");
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "difficulty", tickLimit = 200)
	public void eliteTraitsHitAndDrop(TestContext ctx) {
		floor(ctx);
		ServerPlayerEntity player = (ServerPlayerEntity) ctx.createMockCreativeServerPlayerInWorld();
		player.changeGameMode(GameMode.SURVIVAL);
		ZombieEntity zombie = ctx.spawnEntity(EntityType.ZOMBIE, 2, 1, 2);
		Elites.make(zombie, List.of(Elites.Affix.VAMPIRIC, Elites.Affix.FROST));
		zombie.setHealth(10.0F);
		ctx.waitAndRun(80, () -> {
			Elites.onHit(player, player.getDamageSources().mobAttack(zombie), 4.0F);
			float healed = zombie.getHealth();
			check(healed > 10.0F, "El élite de Sangre no se cura al pegar: " + healed);
			check(player.hasStatusEffect(StatusEffects.SLOWNESS), "El élite de Escarcha no ralentiza");
			BlockPos at = zombie.getBlockPos();
			zombie.damage(player.getDamageSources().playerAttack(player), 1000.0F);
			ctx.waitAndRun(2, () -> {
				List<ItemEntity> drops = ctx.getWorld().getEntitiesByClass(ItemEntity.class, new Box(at).expand(3), e -> true);
				boolean emerald = drops.stream().anyMatch(e -> e.getStack().isOf(net.minecraft.item.Items.EMERALD));
				LOG.info("[EW-GAMETEST] élite muerto: vida tras chupar {}, suelta {}", healed, drops.stream().map(e -> e.getStack().toString()).toList());
				check(!zombie.isAlive(), "El élite no muere");
				check(emerald, "El élite no suelta esmeraldas");
				ctx.complete();
			});
		});
	}
}
