package com.evolvedwarfare.gametest;

import com.evolvedwarfare.registry.ModItems;
import com.evolvedwarfare.research.ResearchState;
import com.evolvedwarfare.research.Tech;
import com.evolvedwarfare.skill.Perk;
import com.evolvedwarfare.skill.Skill;
import com.evolvedwarfare.skill.SkillData;
import com.evolvedwarfare.skill.SkillEffects;
import com.evolvedwarfare.skill.Skills;
import com.evolvedwarfare.weapon.WeaponType;
import java.util.HashSet;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.GameTestException;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.world.GameMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * El árbol de habilidades: que esté bien hecho, que las habilidades suban usándolas (y den puntos), que las ventajas
 * desbloqueen la fabricación y den su bonus, y que lo investigado antes pase al árbol.
 */
public class SkillGameTests implements FabricGameTest {
	private static final Logger LOG = LoggerFactory.getLogger("ew-gametest");

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void perkTreeIsConsistent(TestContext ctx) {
		Set<String> spots = new HashSet<>();
		Set<Item> locked = new HashSet<>();
		for (Perk perk : Perk.values()) {
			if (!spots.add(perk.skill + ":" + perk.column + ":" + perk.row)) {
				throw new GameTestException("Dos ventajas en el mismo sitio: " + perk);
			}
			if (perk.column < 0 || perk.column >= Perk.COLUMNS || perk.row < 0 || perk.row >= Perk.ROWS) {
				throw new GameTestException("Fuera del dibujo: " + perk);
			}
			for (int rank = 2; rank <= perk.ranks(); rank++) {
				if (perk.levelFor(rank) < perk.levelFor(rank - 1)) {
					throw new GameTestException("Los rangos piden menos nivel: " + perk);
				}
			}
			for (Perk before : perk.requires()) {
				if (before.skill != perk.skill || before.levelFor(1) > perk.levelFor(1)) {
					throw new GameTestException("Requisito raro: " + perk + " pide " + before);
				}
			}
			for (Item item : perk.unlocks()) {
				if (!locked.add(item)) {
					throw new GameTestException("Dos ventajas desbloquean " + item);
				}
			}
		}
		for (Tech tech : Tech.values()) {
			for (Item item : tech.unlocks()) {
				if (!locked.contains(item)) {
					throw new GameTestException("Lo que desbloqueaba la investigación ya no se desbloquea: " + item);
				}
			}
		}
		LOG.info("[EW-GAMETEST] árbol: {} ventajas, {} objetos con ventaja", Perk.values().length, locked.size());
		ctx.complete();
	}

	/** Golpear con una daga sube Hojas; llevar hierro y recibir golpes sube la armadura pesada. */
	@GameTest(templateName = EMPTY_STRUCTURE)
	public void skillsRiseWithUse(TestContext ctx) {
		floor(ctx);
		ServerPlayerEntity player = ctx.createMockCreativeServerPlayerInWorld();
		player.changeGameMode(GameMode.SURVIVAL);
		player.setStackInHand(Hand.MAIN_HAND, new ItemStack(ModItems.weapon(WeaponType.DAGGER, "iron")));
		player.equipStack(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
		ZombieEntity zombie = ctx.spawnMob(EntityType.ZOMBIE, 2, 1, 2);
		zombie.setAiDisabled(true);
		for (int i = 0; i < 5; i++) {
			zombie.timeUntilRegen = 0;
			zombie.damage(player.getDamageSources().playerAttack(player), 4.0F);
		}
		player.timeUntilRegen = 0;
		player.damage(zombie.getDamageSources().mobAttack(zombie), 3.0F);
		SkillData data = Skills.get(ctx.getWorld().getServer(), player.getUuid());
		LOG.info("[EW-GAMETEST] habilidades al usar: hojas nivel {} (+{}), armadura pesada {} (+{})", data.level(Skill.BLADES),
				data.xp(Skill.BLADES), data.level(Skill.HEAVY_ARMOR), data.xp(Skill.HEAVY_ARMOR));
		if (data.level(Skill.BLADES) == 0 && data.xp(Skill.BLADES) <= 0.0F) {
			throw new GameTestException("Golpear con la daga no sube Hojas");
		}
		if (data.level(Skill.HEAVY_ARMOR) == 0 && data.xp(Skill.HEAVY_ARMOR) <= 0.0F) {
			throw new GameTestException("Recibir golpes con armadura de hierro no sube Armadura pesada");
		}
		zombie.discard();
		ctx.complete();
	}

	/** Subir mucho una habilidad sube el personaje y da puntos; la ventaja de forja permite fabricar. */
	@GameTest(templateName = EMPTY_STRUCTURE)
	public void levelsGivePointsAndUnlockCrafting(TestContext ctx) {
		ServerPlayerEntity player = ctx.createMockCreativeServerPlayerInWorld();
		player.changeGameMode(GameMode.SURVIVAL);
		ItemStack dagger = new ItemStack(ModItems.weapon(WeaponType.DAGGER, "iron"));
		SkillData data = Skills.get(ctx.getWorld().getServer(), player.getUuid());
		int points = data.points;
		boolean before = Skills.canCraft(player, dagger);
		Skills.gain(player, Skill.AXES, 3000.0F);
		Skills.Unlock result = Skills.unlock(player, Perk.DAGGER_SMITH);
		boolean after = Skills.canCraft(player, dagger);
		LOG.info("[EW-GAMETEST] subir: hachas {}, nivel {}, puntos {} -> {}; daga antes={}, después={} ({})", data.level(Skill.AXES), data.level,
				points, data.points, before, after, result);
		if (data.level(Skill.AXES) < 20 || data.level < 3) {
			throw new GameTestException("No sube: hachas " + data.level(Skill.AXES) + ", nivel " + data.level);
		}
		if (before || !after || result != Skills.Unlock.OK) {
			throw new GameTestException("La forja de dagas no desbloquea las dagas: antes=" + before + ", después=" + after + ", " + result);
		}
		if (Skills.unlock(player, Perk.WHIRLWIND) != Skills.Unlock.LEVEL) {
			throw new GameTestException("Se puede coger una ventaja sin el nivel");
		}
		ctx.complete();
	}

	/** Con "Maestría con hojas" al máximo, el mismo golpe hace bastante más (+50 %). */
	@GameTest(templateName = EMPTY_STRUCTURE)
	public void masteryAddsDamage(TestContext ctx) {
		floor(ctx);
		ServerPlayerEntity player = ctx.createMockCreativeServerPlayerInWorld();
		player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
		ZombieEntity zombie = ctx.spawnMob(EntityType.ZOMBIE, 3, 1, 3);
		zombie.setAiDisabled(true);
		float start = zombie.getHealth();
		zombie.damage(player.getDamageSources().playerAttack(player), 6.0F);
		float a = start - zombie.getHealth();
		zombie.setHealth(start);
		zombie.timeUntilRegen = 0;
		Skills.grant(player, Perk.BLADE_MASTERY, 5);
		zombie.damage(player.getDamageSources().playerAttack(player), 6.0F);
		float b = start - zombie.getHealth();
		LOG.info("[EW-GAMETEST] maestría: sin ventaja {} de daño, con ella {}", a, b);
		if (b < a * 1.35F) {
			throw new GameTestException("La maestría no suma daño: " + a + " -> " + b);
		}
		if (SkillEffects.weaponSkill(new ItemStack(ModItems.weapon(WeaponType.HALBERD, "iron"))) != Skill.POLEARMS) {
			throw new GameTestException("La alabarda no cuenta como asta");
		}
		zombie.discard();
		ctx.complete();
	}

	/** Lo investigado con la mesa de antes pasa al árbol sin gastar puntos. */
	@GameTest(templateName = EMPTY_STRUCTURE)
	public void researchMovesToTree(TestContext ctx) {
		ServerPlayerEntity player = ctx.createMockCreativeServerPlayerInWorld();
		ResearchState research = ResearchState.get(ctx.getWorld().getServer());
		ResearchState.Entry entry = research.of(player.getUuid());
		entry.done.add(Tech.MILITARY_SMITHING);
		entry.done.add(Tech.DRILL);
		SkillData data = Skills.get(ctx.getWorld().getServer(), player.getUuid());
		data.migrated = false;
		int points = data.points;
		Skills.migrate(player);
		LOG.info("[EW-GAMETEST] migración: dagas {}, lanzas {}, jabalinas {}, instrucción {}, puntos {} -> {}", data.rank(Perk.DAGGER_SMITH),
				data.rank(Perk.SPEAR_SMITH), data.rank(Perk.JAVELIN_SMITH), data.rank(Perk.DRILL), points, data.points);
		if (data.rank(Perk.DAGGER_SMITH) == 0 || data.rank(Perk.SPEAR_SMITH) == 0 || data.rank(Perk.JAVELIN_SMITH) == 0
				|| data.rank(Perk.DRILL) == 0 || data.points != points) {
			throw new GameTestException("La investigación no pasa bien al árbol");
		}
		ctx.complete();
	}

	private static void floor(TestContext ctx) {
		for (int x = 0; x < 7; x++) {
			for (int z = 0; z < 7; z++) {
				ctx.setBlockState(x, 0, z, Blocks.STONE);
			}
		}
	}
}
