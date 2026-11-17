package com.evolvedwarfare.gametest;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.test.GameTest;
import net.minecraft.test.GameTestException;
import net.minecraft.test.TestContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Las piezas en 3D de los NPCs (fase 111): cada aspecto tiene las suyas, pegadas a partes del cuerpo que existen. */
public class NpcDetailsGameTests implements FabricGameTest {
	private static final Logger LOG = LoggerFactory.getLogger("ew-gametest");
	private static final Set<String> BONES = Set.of("head", "body", "right_arm", "left_arm", "right_leg", "left_leg", "right_forearm", "left_forearm",
			"right_shin", "left_shin");
	private static final Set<String> SLOTS = Set.of("head", "chest", "legs", "feet");

	private static void check(boolean ok, String message) {
		if (!ok) {
			throw new GameTestException(message);
		}
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "npc_details")
	public void everySkinHasItsPieces(TestContext ctx) throws Exception {
		String[][] kinds = {{"recruit", "14"}, {"soldier", "12"}, {"city_guard", "10"}, {"worker", "12"}, {"trader", "10"}, {"bandit", "12"},
				{"pirate", "12"}, {"viking", "12"}};
		int skins = 0, cubes = 0;
		for (String[] k : kinds) {
			int n = Integer.parseInt(k[1]);
			for (int i = 0; i <= n; i++) {
				String name = i < n ? k[0] + "_" + i : "captain";
				if (i == n && !Set.of("bandit", "pirate", "viking").contains(k[0])) {
					continue;
				}
				String base = "/assets/evolvedwarfare/";
				try (InputStream json = getClass().getResourceAsStream(base + "npc_details/" + k[0] + "/" + name + ".json");
						InputStream png = getClass().getResourceAsStream(base + "textures/entity/" + k[0] + "/" + name + "_details.png")) {
					check(json != null && png != null, "Faltan las piezas de " + k[0] + "/" + name);
					JsonObject def = JsonParser.parseReader(new InputStreamReader(json, StandardCharsets.UTF_8)).getAsJsonObject();
					for (JsonElement g : def.getAsJsonArray("groups")) {
						JsonObject o = g.getAsJsonObject();
						check(BONES.contains(o.get("bone").getAsString()), "Pieza pegada a una parte que no existe: " + o.get("bone"));
						check(SLOTS.contains(o.get("slot").getAsString()), "Pieza con un hueco de armadura raro: " + o.get("slot"));
						cubes += o.getAsJsonArray("cubes").size();
					}
				}
				skins++;
			}
		}
		LOG.info("[EW-GAMETEST] piezas de NPC: {} aspectos, {} piezas", skins, cubes);
		check(skins == 97 && cubes > 900, "Faltan aspectos o piezas: " + skins + " / " + cubes);
		ctx.complete();
	}
}
