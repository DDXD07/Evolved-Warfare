package com.evolvedwarfare.gametest;

import com.evolvedwarfare.EvolvedWarfare;
import com.evolvedwarfare.armor.FactionArmor;
import com.evolvedwarfare.armor.FactionArmorItem;
import com.evolvedwarfare.entity.FactionNpcEntity;
import com.evolvedwarfare.entity.NpcRole;
import com.evolvedwarfare.faction.Faction;
import com.evolvedwarfare.registry.ModItems;
import com.evolvedwarfare.skill.Perk;
import com.evolvedwarfare.skill.SkillEffects;
import com.evolvedwarfare.skill.Skills;
import com.evolvedwarfare.world.FactionSpawner;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.SpawnReason;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.GameTestException;
import net.minecraft.test.TestContext;
import net.minecraft.util.Identifier;
import net.minecraft.world.GameMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Las armaduras de las facciones: 12 conjuntos de 4 piezas, cada rango protege más que el anterior, las de rango 1-2 son
 * ligeras y las de 3-4 pesadas, todas tienen receta y hace falta su ventaja de forja, y los NPCs las llevan según su rango.
 */
public class FactionArmorGameTests implements FabricGameTest {
	private static final Logger LOG = LoggerFactory.getLogger("ew-gametest");

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void factionArmorSets(TestContext ctx) {
		check(FactionArmor.values().length == 12 && ModItems.FACTION_ARMOR.size() == 12, "No hay 12 armaduras");
		StringBuilder log = new StringBuilder();
		for (Faction faction : Faction.values()) {
			int previous = 0;
			for (int tier = 1; tier <= 4; tier++) {
				FactionArmor armor = FactionArmor.of(faction, tier);
				List<Item> pieces = ModItems.FACTION_ARMOR.get(armor);
				check(pieces.size() == 4, armor + " no tiene 4 piezas");
				int total = 0;
				for (int i = 0; i < 4; i++) {
					if (!(pieces.get(i) instanceof FactionArmorItem item) || item.getType() != FactionArmor.TYPES[i] || item.set() != armor) {
						throw new GameTestException("Pieza rara en " + armor + ": " + pieces.get(i));
					}
					total += item.getProtection();
					Identifier id = Registries.ITEM.getId(item);
					check(id.getNamespace().equals(EvolvedWarfare.MOD_ID) && id.getPath().startsWith(armor.id()), "Mal nombre: " + id);
					check(new ItemStack(item).getMaxDamage() > 0, "Sin durabilidad: " + id);
					check(SkillEffects.light(item) == (tier <= 2), "Ligera o pesada mal: " + id);
				}
				log.append(armor.id()).append('=').append(total).append(' ');
				check(total > previous, armor + " no protege más que el rango anterior (" + previous + " -> " + total + ")");
				previous = total;
			}
		}
		LOG.info("[EW-GAMETEST] armaduras de facción (protección total): {}", log.toString().trim());
		ctx.complete();
	}

	/** Cada pieza tiene receta y no se puede fabricar sin su ventaja de forja (rango 1-2, 3 y 4). */
	@GameTest(templateName = EMPTY_STRUCTURE)
	public void factionArmorRecipesNeedPerks(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		for (List<Item> pieces : ModItems.FACTION_ARMOR.values()) {
			for (Item item : pieces) {
				Identifier id = Registries.ITEM.getId(item);
				check(world.getRecipeManager().get(id).isPresent(), "Sin receta: " + id);
			}
		}
		ServerPlayerEntity player = ctx.createMockCreativeServerPlayerInWorld();
		player.changeGameMode(GameMode.SURVIVAL);
		ItemStack outlaw = new ItemStack(ModItems.factionArmor(FactionArmor.BANDIT_OUTLAW, ArmorItem.Type.HELMET));
		ItemStack huscarl = new ItemStack(ModItems.factionArmor(FactionArmor.VIKING_HUSCARL, ArmorItem.Type.CHESTPLATE));
		ItemStack captain = new ItemStack(ModItems.factionArmor(FactionArmor.PIRATE_CAPTAIN, ArmorItem.Type.BOOTS));
		boolean before = Skills.canCraft(player, outlaw) || Skills.canCraft(player, huscarl) || Skills.canCraft(player, captain);
		Skills.grant(player, Perk.FACTION_TAILOR, 1);
		boolean tailor = Skills.canCraft(player, outlaw) && !Skills.canCraft(player, huscarl);
		Skills.grant(player, Perk.FACTION_ARMORER, 1);
		boolean armorer = Skills.canCraft(player, huscarl) && !Skills.canCraft(player, captain);
		Skills.grant(player, Perk.WARLORD_FORGE, 1);
		boolean forge = Skills.canCraft(player, captain);
		LOG.info("[EW-GAMETEST] recetas de armaduras: sin ventajas {}, sastrería {}, armería {}, forja de capitanes {}", before, tailor, armorer,
				forge);
		check(!before, "Se fabrican armaduras de facción sin ventajas");
		check(tailor && armorer && forge, "Las ventajas de forja no desbloquean su rango");
		ctx.complete();
	}

	/** El capitán vikingo va con la armadura del jarl entera; un guerrero bandido, con la suya de rango 1-3. */
	@GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 60)
	public void factionNpcsWearTheirArmor(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		FactionNpcEntity jarl = FactionSpawner.spawnMember(world, Faction.VIKINGS, ctx.getAbsolutePos(new net.minecraft.util.math.BlockPos(1, 1, 1)),
				NpcRole.CAPTAIN, SpawnReason.EVENT, world.getRandom());
		FactionNpcEntity bandit = FactionSpawner.spawnMember(world, Faction.BANDITS, ctx.getAbsolutePos(new net.minecraft.util.math.BlockPos(3, 1, 1)),
				NpcRole.WARRIOR, SpawnReason.EVENT, world.getRandom());
		check(jarl != null && bandit != null, "No aparecen");
		jarl.setAiDisabled(true);
		bandit.setAiDisabled(true);
		StringBuilder worn = new StringBuilder();
		for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
			Item item = jarl.getEquippedStack(slot).getItem();
			worn.append(Registries.ITEM.getId(item).getPath()).append(' ');
			if (!(item instanceof FactionArmorItem armor) || armor.set() != FactionArmor.VIKING_JARL) {
				jarl.discard();
				bandit.discard();
				throw new GameTestException("El jarl no lleva su armadura en " + slot + ": " + item);
			}
		}
		Item chest = bandit.getEquippedStack(EquipmentSlot.CHEST).getItem();
		LOG.info("[EW-GAMETEST] el jarl lleva: {}; el bandido, de peto: {}", worn.toString().trim(), Registries.ITEM.getId(chest));
		jarl.discard();
		bandit.discard();
		check(chest instanceof FactionArmorItem armor && armor.set().faction == Faction.BANDITS && armor.set().tier <= 3,
				"El guerrero bandido no lleva peto de bandido: " + chest);
		ctx.complete();
	}

	private static void check(boolean ok, String message) {
		if (!ok) {
			throw new GameTestException(message);
		}
	}
}
