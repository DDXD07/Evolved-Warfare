package com.evolvedwarfare.gametest;

import com.evolvedwarfare.EvolvedWarfare;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.structure.StructureSet;
import net.minecraft.structure.StructureTemplate;
import net.minecraft.test.GameTest;
import net.minecraft.test.GameTestException;
import net.minecraft.test.TestContext;
import net.minecraft.util.Identifier;
import net.minecraft.world.gen.structure.Structure;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** La ciudadela infernal: sustituye a la fortaleza del Nether. */
public class CitadelGameTests implements FabricGameTest {
	private static final Logger LOG = LoggerFactory.getLogger("ew-gametest");

	private static void check(boolean ok, String message) {
		if (!ok) {
			throw new GameTestException(message);
		}
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void citadelReplacesFortress(TestContext ctx) {
		var server = ctx.getWorld().getServer();
		var structures = server.getRegistryManager().get(RegistryKeys.STRUCTURE);
		RegistryKey<Structure> citadel = RegistryKey.of(RegistryKeys.STRUCTURE, EvolvedWarfare.id("nether_citadel"));
		StructureSet set = server.getRegistryManager().get(RegistryKeys.STRUCTURE_SET).get(Identifier.ofVanilla("nether_complexes"));
		boolean hasCitadel = false;
		boolean hasFortress = false;
		for (StructureSet.WeightedEntry e : set.structures()) {
			hasCitadel |= e.structure().matchesKey(citadel);
			hasFortress |= e.structure().matchesKey(RegistryKey.of(RegistryKeys.STRUCTURE, Identifier.ofVanilla("fortress")));
		}
		var template = server.getStructureTemplateManager().getTemplate(EvolvedWarfare.id("nether_citadel/citadel"));
		var size = template.map(StructureTemplate::getSize).orElse(null);
		boolean bosses = true;
		for (String b : new String[] {"legion", "beelzebub", "moloch", "abaddon"}) {
			bosses &= server.getStructureTemplateManager().getTemplate(EvolvedWarfare.id("nether_citadel/boss_" + b)).isPresent();
		}
		LOG.info("[EW-GAMETEST] ciudadela: registrada {}, en el Nether {}, fortaleza de vanilla {}, plantilla {}, piezas de jefe {}",
				structures.contains(citadel), hasCitadel, hasFortress, size, bosses);
		check(structures.contains(citadel) && hasCitadel && !hasFortress, "La ciudadela no sustituye a la fortaleza");
		check(size != null && size.getX() == 88 && size.getZ() == 88 && bosses, "Falta la plantilla de la ciudadela");
		ctx.complete();
	}
}
