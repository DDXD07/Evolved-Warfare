package com.evolvedwarfare.client.hell;

import com.evolvedwarfare.EvolvedWarfare;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.model.ModelData;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelPartBuilder;
import net.minecraft.client.model.ModelPartData;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.render.entity.model.SinglePartEntityModel;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.util.math.MathHelper;

/**
 * El modelo de un mini jefe, leído de su JSON (assets/evolvedwarfare/hell/&lt;id&gt;.json, de tools/ew_demons.py) y animado
 * según el papel de cada parte: piernas que andan, brazos que se balancean y golpean, cabeza que mira, mandíbula que se abre
 * al atacar, alas que baten, cola que se mece y un cuerpo que respira.
 */
public class BossModel<T extends HostileEntity> extends SinglePartEntityModel<T> {
	record Part(ModelPart part, String role, int phase, int side, String flap) {
	}

	private final ModelPart root;
	private final List<Part> parts = new ArrayList<>();

	public BossModel(ModelPart root, String id) {
		this(root, id, false);
	}

	/** {@code translucent}: para los que son de bruma (los espíritus). */
	public BossModel(ModelPart root, String id, boolean translucent) {
		super(translucent ? net.minecraft.client.render.RenderLayer::getEntityTranslucent : net.minecraft.client.render.RenderLayer::getEntityCutoutNoCull);
		this.root = root;
		for (JsonElement e : load(id).getAsJsonArray("parts")) {
			JsonObject p = e.getAsJsonObject();
			List<String> chain = path(load(id).getAsJsonArray("parts"), p.get("name").getAsString());
			ModelPart part = root;
			for (String step : chain) {
				part = part.getChild(step);
			}
			JsonObject role = p.has("role") && p.get("role").isJsonObject() ? p.getAsJsonObject("role") : new JsonObject();
			parts.add(new Part(part, role.has("role") ? role.get("role").getAsString() : "", role.has("phase") ? role.get("phase").getAsInt() : 0,
					role.has("side") ? role.get("side").getAsInt() : 0, role.has("flap") ? role.get("flap").getAsString() : "roll"));
		}
	}

	private static final Map<String, JsonObject> CACHE = new HashMap<>();

	static synchronized JsonObject load(String id) {
		return CACHE.computeIfAbsent(id, k -> {
			String path = "/assets/" + EvolvedWarfare.MOD_ID + "/hell/" + k + ".json";
			try (InputStream in = BossModel.class.getResourceAsStream(path)) {
				if (in == null) {
					throw new IllegalStateException("Falta el modelo " + path);
				}
				return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
			} catch (java.io.IOException ex) {
				throw new IllegalStateException("No se puede leer " + path, ex);
			}
		});
	}

	private static List<String> path(JsonArray parts, String name) {
		Map<String, String> parent = new HashMap<>();
		for (JsonElement e : parts) {
			JsonObject p = e.getAsJsonObject();
			parent.put(p.get("name").getAsString(), p.has("parent") && !p.get("parent").isJsonNull() ? p.get("parent").getAsString() : null);
		}
		List<String> chain = new ArrayList<>();
		for (String n = name; n != null; n = parent.get(n)) {
			chain.add(0, n);
		}
		return chain;
	}

	private static float f(JsonArray a, int i) {
		return a.get(i).getAsFloat();
	}

	public static TexturedModelData texturedModelData(String id) {
		JsonObject def = load(id);
		JsonArray tex = def.getAsJsonArray("texture");
		ModelData data = new ModelData();
		Map<String, ModelPartData> made = new HashMap<>();
		for (JsonElement e : def.getAsJsonArray("parts")) {
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
			float rad = MathHelper.RADIANS_PER_DEGREE;
			made.put(name, parent.addChild(name, builder,
					ModelTransform.of(f(pv, 0), f(pv, 1), f(pv, 2), f(rot, 0) * rad, f(rot, 1) * rad, f(rot, 2) * rad)));
		}
		return TexturedModelData.of(data, tex.get(0).getAsInt(), tex.get(1).getAsInt());
	}

	@Override
	public ModelPart getPart() {
		return root;
	}

	@Override
	public void setAngles(T entity, float limbAngle, float limbDistance, float animationProgress, float headYaw, float headPitch) {
		root.traverse().forEach(ModelPart::resetTransform);
		float speed = Math.min(1.0F, limbDistance);
		float swing = handSwingProgress > 0 ? MathHelper.sin(handSwingProgress * MathHelper.PI) : 0.0F;
		boolean angry = entity.isAttacking();
		for (Part p : parts) {
			ModelPart m = p.part();
			switch (p.role()) {
				case "leg" -> m.pitch += MathHelper.cos(limbAngle * 0.5F + (p.phase() == 1 ? MathHelper.PI : 0.0F)) * 0.9F * speed;
				case "arm" -> {
					m.pitch += MathHelper.cos(limbAngle * 0.5F + (p.side() > 0 ? 0.0F : MathHelper.PI)) * 0.6F * speed;
					m.pitch -= swing * 1.6F + (angry ? 0.25F : 0.0F);
					m.roll += p.side() * (0.05F + MathHelper.sin(animationProgress * 0.07F) * 0.04F);
				}
				case "head" -> {
					m.yaw += headYaw * MathHelper.RADIANS_PER_DEGREE * 0.8F;
					m.pitch += headPitch * MathHelper.RADIANS_PER_DEGREE * 0.6F;
				}
				case "jaw" -> m.pitch += (angry ? 0.35F : 0.08F) + MathHelper.sin(animationProgress * 0.25F) * 0.08F + swing * 0.4F;
				case "wing" -> {
					float flap = (entity.isOnGround() ? 0.15F : 0.6F) * MathHelper.sin(animationProgress * (entity.isOnGround() ? 0.15F : 1.1F));
					if ("yaw".equals(p.flap())) {
						m.yaw += flap * p.side();
					} else {
						m.roll += flap * p.side();
					}
				}
				case "tail" -> {
					m.yaw += MathHelper.sin(animationProgress * 0.08F) * 0.25F;
					m.pitch += MathHelper.sin(animationProgress * 0.06F) * 0.08F;
				}
				case "body" -> m.pitch += MathHelper.sin(animationProgress * 0.05F) * 0.02F;
				// un cuerpo de serpiente: cada trozo ondula un poco después del anterior
				case "segment" -> m.yaw += MathHelper.sin(animationProgress * (0.25F + speed * 0.35F) - p.phase() * 0.9F) * (0.25F + speed * 0.2F);
				default -> {
				}
			}
		}
	}
}
