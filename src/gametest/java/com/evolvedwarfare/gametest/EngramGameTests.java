package com.evolvedwarfare.gametest;

import com.evolvedwarfare.registry.ModItems;
import com.evolvedwarfare.skill.Engram;
import com.evolvedwarfare.skill.Engrams;
import com.evolvedwarfare.skill.Perk;
import com.evolvedwarfare.skill.SkillData;
import com.evolvedwarfare.skill.Skills;
import com.evolvedwarfare.weapon.WeaponType;
import java.util.HashSet;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.entity.EquipmentSlot;
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

/** Los engramas (fase 113): qué pide engrama, aprenderlos, fabricar y usar, la armadura y las ventajas de antes. */
public class EngramGameTests implements FabricGameTest {
	private static final Logger LOG = LoggerFactory.getLogger("ew-gametest");

	private static void check(boolean ok, String message) {
		if (!ok) {
			throw new GameTestException(message);
		}
	}

	private static String of(Item item) {
		Engram e = Engrams.of(item);
		return e == null ? "-" : e.id;
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "engrams")
	public void catalogIsComplete(TestContext ctx) {
		Set<String> ids = new HashSet<>();
		for (Engram e : Engrams.all()) {
			check(ids.add(e.id), "Engrama repetido: " + e.id);
			check(Engrams.categories().contains(e.category), "Categoría rara: " + e.category);
			check(e.level >= 1 && e.level <= SkillData.MAX_LEVEL && e.cost > 0, "Nivel o coste raro: " + e.id);
		}
		int empty = 0;
		for (Engram e : Engrams.all()) {
			for (String r : e.requires) {
				check(Engrams.byId(r) != null, e.id + " pide " + r + ", que no existe");
			}
			if (Engrams.itemsOf(e).isEmpty() && e.perk == null) {
				empty++;
				LOG.info("[EW-GAMETEST] engrama sin nada: {}", e.id);
			}
		}
		LOG.info("[EW-GAMETEST] engramas: {} engramas, {} objetos con engrama, {} vacíos; pico de hierro {}, escalera de roble {}, peto de diamante {},"
						+ " daga {}, mesa {}, horno {}, lingote {}, tablón {}, vendas {}, cama {}, vidrio {}", Engrams.all().size(), Engrams.lockedItemCount(),
				empty, of(Items.IRON_PICKAXE), of(Items.OAK_STAIRS), of(Items.DIAMOND_CHESTPLATE), of(ModItems.weapon(WeaponType.DAGGER, "iron")),
				of(Items.CRAFTING_TABLE), of(Items.FURNACE), of(Items.IRON_INGOT), of(Items.OAK_PLANKS), of(com.evolvedwarfare.health.HealthItems.BANDAGE), of(Items.RED_BED),
				of(Items.GLASS_PANE));
		check(Engrams.all().size() >= 150, "Pocos engramas: " + Engrams.all().size());
		check(Engrams.lockedItemCount() > 600, "Pocos objetos con engrama: " + Engrams.lockedItemCount());
		check(of(Items.IRON_PICKAXE).equals("iron_tools"), "El pico de hierro no va en sus herramientas: " + of(Items.IRON_PICKAXE));
		check(of(Items.OAK_STAIRS).equals("wood_building"), "La escalera de roble: " + of(Items.OAK_STAIRS));
		check(of(Items.DIAMOND_CHESTPLATE).equals("diamond_armor"), "El peto de diamante: " + of(Items.DIAMOND_CHESTPLATE));
		check(of(ModItems.weapon(WeaponType.DAGGER, "iron")).equals("dagger"), "La daga");
		check(of(Items.RED_BED).equals("bed"), "La cama: " + of(Items.RED_BED));
		check(of(Items.GLASS_PANE).equals("glass"), "El panel de vidrio: " + of(Items.GLASS_PANE));
		check(of(com.evolvedwarfare.health.HealthItems.BANDAGE).equals("bandage"), "Las vendas");
		// lo que sale del horno no pide engrama (fundir y cocinar es libre; fase 115)
		for (Item free : new Item[] {Items.CRAFTING_TABLE, Items.FURNACE, Items.IRON_INGOT, Items.OAK_PLANKS, Items.STICK, Items.TORCH,
				Items.COOKED_BEEF, Items.SMOOTH_STONE, Items.GLASS, Items.TERRACOTTA}) {
			check(Engrams.of(free) == null, "Pide engrama y debería ser libre: " + free);
		}
		check(empty <= 3, "Demasiados engramas sin nada: " + empty);
		ctx.complete();
	}

	/** Ningún engrama pide de ingrediente algo de un engrama de más nivel que no pida antes (fase 116). */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "engrams")
	public void noLevelInversions(TestContext ctx) {
		java.util.List<String> bad = Engrams.inversions(ctx.getWorld().getServer());
		for (String b : bad) {
			LOG.info("[EW-GAMETEST] engrama al revés: {}", b);
		}
		LOG.info("[EW-GAMETEST] engramas al revés: {}", bad.size());
		check(bad.isEmpty(), bad.size() + " engramas piden algo de más nivel (mira el registro)");
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "engrams")
	public void learnCraftAndUse(TestContext ctx) {
		ServerPlayerEntity player = ctx.createMockCreativeServerPlayerInWorld();
		player.changeGameMode(GameMode.SURVIVAL);
		SkillData data = Skills.get(ctx.getWorld().getServer(), player.getUuid());
		Skills.survivorXp(player, 3000.0F);
		data.engramPoints = 100;
		ItemStack pick = new ItemStack(Items.IRON_PICKAXE);
		ItemStack sword = new ItemStack(Items.IRON_SWORD);
		ItemStack chest = new ItemStack(Items.IRON_CHESTPLATE);
		Engrams.testEnforce = true;
		try {
			boolean craftBefore = Skills.canCraft(player, pick);
			player.setStackInHand(Hand.MAIN_HAND, sword);
			boolean useBefore = Engrams.canUse(player, sword);
			float punch = Engrams.modifyMelee(player.getDamageSources().playerAttack(player), 6.0F);
			player.equipStack(EquipmentSlot.CHEST, chest.copy());
			Engrams.checkArmor(player);
			boolean armorOff = player.getEquippedStack(EquipmentSlot.CHEST).isEmpty() && player.getInventory().contains(chest);
			boolean blocks = Engrams.canUse(player, new ItemStack(Items.OAK_STAIRS)) && Engrams.canUse(player, new ItemStack(Items.BREAD));
			Engrams.Learn skip = Engrams.learn(player, "iron_tools");
			Engrams.Learn wood = Engrams.learn(player, "wood_tools");
			Engrams.Learn stone = Engrams.learn(player, "stone_tools");
			Engrams.Learn iron = Engrams.learn(player, "iron_tools");
			Engrams.Learn diamond = Engrams.learn(player, "diamond_tools");
			int left = data.engramPoints;
			boolean craftAfter = Skills.canCraft(player, pick);
			boolean useAfter = Engrams.canUse(player, sword);
			float hit = Engrams.modifyMelee(player.getDamageSources().playerAttack(player), 6.0F);
			LOG.info("[EW-GAMETEST] engramas: nivel {}; pico antes {}, espada {} (golpe {}), peto fuera {}, bloques y pan {}; hierro sin piedra {},"
							+ " madera {}, piedra {}, hierro {}, diamante {}; quedan {} puntos; después pico {}, espada {} (golpe {})", data.level,
					craftBefore, useBefore, punch, armorOff, blocks, skip, wood, stone, iron, diamond, left, craftAfter, useAfter, hit);
			check(!craftBefore && !useBefore && punch == 1.0F, "Sin engrama se fabrica o se usa el hierro");
			check(armorOff, "Sin engrama la armadura no se cae");
			check(blocks, "Los bloques y la comida piden engrama");
			check(skip == Engrams.Learn.REQUIRES, "Se aprende el hierro sin la piedra: " + skip);
			check(wood == Engrams.Learn.OK && stone == Engrams.Learn.OK && iron == Engrams.Learn.OK, "No se aprenden en orden");
			check(diamond == Engrams.Learn.LEVEL, "Se aprende el diamante sin nivel: " + diamond + " (nivel " + data.level + ")");
			check(left == 100 - 2 - 3 - 8, "No cuestan lo que deben: quedan " + left);
			check(craftAfter && useAfter && hit == 6.0F, "Aprendido, sigue sin poderse fabricar o usar");
		} finally {
			Engrams.testEnforce = false;
		}
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "engrams")
	public void oldPerksBecomeEngrams(TestContext ctx) {
		ServerPlayerEntity player = ctx.createMockCreativeServerPlayerInWorld();
		SkillData data = Skills.get(ctx.getWorld().getServer(), player.getUuid());
		data.engramStart = false;
		data.perks.put(Perk.DAGGER_SMITH, 1);
		data.perks.put(Perk.HALBERD_SMITH, 1);
		data.perks.put(Perk.DRILL, 1);
		data.perks.put(Perk.BLADE_MASTERY, 3);
		int points = data.engramPoints;
		var given = Engrams.migrate(data);
		LOG.info("[EW-GAMETEST] ventajas a engramas: {} ({} puntos más); instrucción {}, alabardas {}, maestría {}", given.size(),
				data.engramPoints - points, Skills.rank(player, Perk.DRILL), Skills.rank(player, Perk.HALBERD_SMITH), Skills.rank(player, Perk.BLADE_MASTERY));
		check(data.engrams.contains("dagger") && data.engrams.contains("halberd") && data.engrams.contains("spear") && data.engrams.contains("drill"),
				"No pasan a engramas (ni lo que piden): " + data.engrams);
		check(data.engramPoints - points == 10, "No da los 10 del principio");
		check(Skills.rank(player, Perk.DRILL) == 1 && Skills.rank(player, Perk.HALBERD_SMITH) == 1, "El engrama no da la ventaja");
		check(Skills.rank(player, Perk.BLADE_MASTERY) == 0, "Las de combate siguen");
		check(Engrams.migrate(data).isEmpty() && data.engramPoints - points == 10, "Se migra dos veces");
		ctx.complete();
	}
}
