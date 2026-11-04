package com.evolvedwarfare.client.wildlife;

import com.evolvedwarfare.EvolvedWarfare;
import com.evolvedwarfare.wildlife.WildSpecies;
import com.evolvedwarfare.wildlife.Wildlife;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.model.ModelData;
import net.minecraft.client.model.ModelPartBuilder;
import net.minecraft.client.model.ModelPartData;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.render.entity.model.EntityModelLayer;

/**
 * Los modelos de los animales salvajes: cada especie tiene su JSON (assets/evolvedwarfare/wildlife/&lt;especie&gt;.json, hecho
 * por tools/ew_wildlife.py) con sus partes, pivotes, giros, cubos y el papel de cada parte para animarla.
 */
public final class WildModels {
	/** Una parte: su camino desde la raíz, su papel ("leg", "head", "neck", "tail", "ear", "wing", "body"...) y sus datos. */
	public record PartDef(String name, List<String> path, String role, int phase, float gait, int side) {
	}

	public record Def(int textureWidth, int textureHeight, List<PartDef> parts, JsonArray json) {
	}

	private static final Map<WildSpecies, Def> DEFS = new EnumMap<>(WildSpecies.class);
	private static final Map<WildSpecies, EntityModelLayer> LAYERS = new EnumMap<>(WildSpecies.class);

	private WildModels() {
	}

	public static void register() {
		for (WildSpecies s : WildSpecies.values()) {
			EntityModelLayer layer = new EntityModelLayer(EvolvedWarfare.id(s.id()), "main");
			LAYERS.put(s, layer);
			EntityModelLayerRegistry.registerModelLayer(layer, () -> texturedModelData(s));
			EntityRendererRegistry.register(Wildlife.type(s), ctx -> new WildAnimalRenderer(ctx, s, layer));
		}
	}

	public static synchronized Def def(WildSpecies s) {
		return DEFS.computeIfAbsent(s, WildModels::load);
	}

	private static Def load(WildSpecies s) {
		String path = "/assets/" + EvolvedWarfare.MOD_ID + "/wildlife/" + s.id() + ".json";
		try (InputStream in = WildModels.class.getResourceAsStream(path)) {
			if (in == null) {
				throw new IllegalStateException("Falta el modelo " + path);
			}
			JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
			JsonArray tex = root.getAsJsonArray("texture");
			JsonArray parts = root.getAsJsonArray("parts");
			Map<String, List<String>> paths = new HashMap<>();
			List<PartDef> defs = new ArrayList<>();
			for (JsonElement e : parts) {
				JsonObject p = e.getAsJsonObject();
				String name = p.get("name").getAsString();
				List<String> chain = new ArrayList<>();
				if (p.has("parent") && !p.get("parent").isJsonNull()) {
					chain.addAll(paths.get(p.get("parent").getAsString()));
				}
				chain.add(name);
				paths.put(name, chain);
				JsonObject role = p.has("role") && p.get("role").isJsonObject() ? p.getAsJsonObject("role") : new JsonObject();
				defs.add(new PartDef(name, List.copyOf(chain), role.has("role") ? role.get("role").getAsString() : "",
						role.has("phase") ? role.get("phase").getAsInt() : 0, role.has("gait") ? role.get("gait").getAsFloat() : 1.0F,
						role.has("side") ? role.get("side").getAsInt() : 0));
			}
			return new Def(tex.get(0).getAsInt(), tex.get(1).getAsInt(), List.copyOf(defs), parts);
		} catch (java.io.IOException ex) {
			throw new IllegalStateException("No se puede leer " + path, ex);
		}
	}

	private static float f(JsonArray a, int i) {
		return a.get(i).getAsFloat();
	}

	public static TexturedModelData texturedModelData(WildSpecies s) {
		Def def = def(s);
		ModelData data = new ModelData();
		Map<String, ModelPartData> made = new HashMap<>();
		for (JsonElement e : def.json()) {
			JsonObject p = e.getAsJsonObject();
			String name = p.get("name").getAsString();
			ModelPartData parent = p.has("parent") && !p.get("parent").isJsonNull() ? made.get(p.get("parent").getAsString()) : data.getRoot();
			ModelPartBuilder builder = ModelPartBuilder.create();
			for (JsonElement ce : p.getAsJsonArray("cubes")) {
				JsonObject c = ce.getAsJsonObject();
				JsonArray uv = c.getAsJsonArray("uv");
				JsonArray o = c.getAsJsonArray("origin");
				JsonArray sz = c.getAsJsonArray("size");
				builder.uv(uv.get(0).getAsInt(), uv.get(1).getAsInt()).cuboid(f(o, 0), f(o, 1), f(o, 2), f(sz, 0), f(sz, 1), f(sz, 2));
			}
			JsonArray pv = p.getAsJsonArray("pivot");
			JsonArray rot = p.getAsJsonArray("rotation");
			float rad = (float) (Math.PI / 180.0);
			made.put(name, parent.addChild(name, builder, ModelTransform.of(f(pv, 0), f(pv, 1), f(pv, 2), f(rot, 0) * rad, f(rot, 1) * rad,
					f(rot, 2) * rad)));
		}
		return TexturedModelData.of(data, def.textureWidth(), def.textureHeight());
	}
}
