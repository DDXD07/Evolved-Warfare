package com.evolvedwarfare.client.render.details;

import com.evolvedwarfare.EvolvedWarfare;
import com.evolvedwarfare.client.anim.Joints;
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
import java.util.Optional;
import net.minecraft.client.model.Dilation;
import net.minecraft.client.model.ModelData;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelPartBuilder;
import net.minecraft.client.model.ModelPartData;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.jetbrains.annotations.Nullable;

/**
 * Las piezas en 3D de un aspecto de NPC (fase 111; de tools/ew_npc_details.py): barbas, coletas, capuchas, sombreros,
 * máscaras, cinturones, bolsas, bandoleras, tabardos, capas, hombreras, brazales, suelas... Cada grupo va pegado a una parte
 * del cuerpo (también a los antebrazos y espinillas de las articulaciones) y la sigue en cada fotograma; si al NPC le
 * falta esa parte (desmembrado), no se dibuja, y si lleva armadura en ese hueco, tampoco. El daño locacional va por la caja
 * del NPC, así que no le afecta.
 */
public final class NpcDetails {
	private static final String[] BONES = {"head", "body", "right_arm", "left_arm", "right_leg", "left_leg"};
	private static final Map<Identifier, Optional<NpcDetails>> CACHE = new HashMap<>();

	private record Group(ModelPart part, String bone, @Nullable EquipmentSlot slot) {
	}

	private final Identifier texture;
	private final ModelPart root;
	private final Map<String, ModelPart> bones = new HashMap<>();
	private final List<Group> groups = new ArrayList<>();

	private NpcDetails(Identifier texture, JsonObject def) {
		this.texture = texture;
		ModelData data = new ModelData();
		ModelPartData r = data.getRoot();
		Map<String, ModelPartData> made = new HashMap<>();
		for (String b : BONES) {
			made.put(b, r.addChild(b, ModelPartBuilder.create(), ModelTransform.NONE));
		}
		made.put(Joints.RIGHT_FOREARM, made.get("right_arm").addChild(Joints.RIGHT_FOREARM, ModelPartBuilder.create(), ModelTransform.NONE));
		made.put(Joints.LEFT_FOREARM, made.get("left_arm").addChild(Joints.LEFT_FOREARM, ModelPartBuilder.create(), ModelTransform.NONE));
		made.put(Joints.RIGHT_SHIN, made.get("right_leg").addChild(Joints.RIGHT_SHIN, ModelPartBuilder.create(), ModelTransform.NONE));
		made.put(Joints.LEFT_SHIN, made.get("left_leg").addChild(Joints.LEFT_SHIN, ModelPartBuilder.create(), ModelTransform.NONE));
		JsonArray gs = def.getAsJsonArray("groups");
		List<String[]> where = new ArrayList<>();
		for (int k = 0; k < gs.size(); k++) {
			JsonObject g = gs.get(k).getAsJsonObject();
			String bone = bone(g.get("bone").getAsString());
			ModelPartData parent = made.get(bone);
			if (parent == null) {
				continue;
			}
			ModelPartBuilder builder = ModelPartBuilder.create();
			for (JsonElement ce : g.getAsJsonArray("cubes")) {
				JsonObject c = ce.getAsJsonObject();
				JsonArray uv = c.getAsJsonArray("uv");
				JsonArray o = c.getAsJsonArray("origin");
				JsonArray s = c.getAsJsonArray("size");
				float inflate = c.has("inflate") ? c.get("inflate").getAsFloat() : 0.0F;
				builder.uv(uv.get(0).getAsInt(), uv.get(1).getAsInt()).cuboid(f(o, 0), f(o, 1), f(o, 2), f(s, 0), f(s, 1), f(s, 2), new Dilation(inflate));
			}
			JsonArray pv = g.getAsJsonArray("pivot");
			JsonArray rot = g.getAsJsonArray("rotation");
			float rad = MathHelper.RADIANS_PER_DEGREE;
			parent.addChild("g" + k, builder, ModelTransform.of(f(pv, 0), f(pv, 1), f(pv, 2), f(rot, 0) * rad, f(rot, 1) * rad, f(rot, 2) * rad));
			where.add(new String[] {bone, "g" + k, g.has("slot") ? g.get("slot").getAsString() : ""});
		}
		JsonArray tex = def.getAsJsonArray("texture");
		this.root = TexturedModelData.of(data, tex.get(0).getAsInt(), tex.get(1).getAsInt()).createModel();
		for (String b : BONES) {
			bones.put(b, root.getChild(b));
		}
		bones.put(Joints.RIGHT_FOREARM, bones.get("right_arm").getChild(Joints.RIGHT_FOREARM));
		bones.put(Joints.LEFT_FOREARM, bones.get("left_arm").getChild(Joints.LEFT_FOREARM));
		bones.put(Joints.RIGHT_SHIN, bones.get("right_leg").getChild(Joints.RIGHT_SHIN));
		bones.put(Joints.LEFT_SHIN, bones.get("left_leg").getChild(Joints.LEFT_SHIN));
		for (String[] w : where) {
			groups.add(new Group(bones.get(w[0]).getChild(w[1]), w[0], slot(w[2])));
		}
	}

	/** Los nombres de las articulaciones de tools/ew_npc_details.py → los de {@link Joints}. */
	private static String bone(String name) {
		return switch (name) {
			case "right_forearm" -> Joints.RIGHT_FOREARM;
			case "left_forearm" -> Joints.LEFT_FOREARM;
			case "right_shin" -> Joints.RIGHT_SHIN;
			case "left_shin" -> Joints.LEFT_SHIN;
			default -> name;
		};
	}

	@Nullable
	private static EquipmentSlot slot(String name) {
		return switch (name) {
			case "head" -> EquipmentSlot.HEAD;
			case "chest" -> EquipmentSlot.CHEST;
			case "legs" -> EquipmentSlot.LEGS;
			case "feet" -> EquipmentSlot.FEET;
			default -> null;
		};
	}

	private static float f(JsonArray a, int i) {
		return a.get(i).getAsFloat();
	}

	/** Las piezas de un aspecto (por su textura: textures/entity/x/y.png → npc_details/x/y.json), o null si no tiene. */
	@Nullable
	public static synchronized NpcDetails forSkin(Identifier skin) {
		return CACHE.computeIfAbsent(skin, NpcDetails::load).orElse(null);
	}

	private static Optional<NpcDetails> load(Identifier skin) {
		if (!skin.getNamespace().equals(EvolvedWarfare.MOD_ID) || !skin.getPath().startsWith("textures/entity/") || !skin.getPath().endsWith(".png")) {
			return Optional.empty();
		}
		String rel = skin.getPath().substring("textures/entity/".length(), skin.getPath().length() - 4);
		String path = "/assets/" + EvolvedWarfare.MOD_ID + "/npc_details/" + rel + ".json";
		try (InputStream in = NpcDetails.class.getResourceAsStream(path)) {
			if (in == null) {
				return Optional.empty();
			}
			JsonObject def = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
			return Optional.of(new NpcDetails(EvolvedWarfare.id("textures/entity/" + rel + "_details.png"), def));
		} catch (Exception ex) {
			EvolvedWarfare.LOGGER.warn("No se pueden leer las piezas de {}", path, ex);
			return Optional.empty();
		}
	}

	private static void follow(ModelPart detail, ModelPart body) {
		detail.copyTransform(body);
		detail.visible = body.visible;
	}

	/** Sigue al cuerpo del NPC (postura, articulaciones y lo que le falta) y dibuja las piezas. */
	public void render(BipedEntityModel<?> model, LivingEntity entity, MatrixStack matrices, VertexConsumerProvider consumers, int light, int overlay) {
		follow(bones.get("head"), model.head);
		follow(bones.get("body"), model.body);
		follow(bones.get("right_arm"), model.rightArm);
		follow(bones.get("left_arm"), model.leftArm);
		follow(bones.get("right_leg"), model.rightLeg);
		follow(bones.get("left_leg"), model.leftLeg);
		lower(Joints.RIGHT_FOREARM, model.rightArm);
		lower(Joints.LEFT_FOREARM, model.leftArm);
		lower(Joints.RIGHT_SHIN, model.rightLeg);
		lower(Joints.LEFT_SHIN, model.leftLeg);
		for (Group g : groups) {
			g.part.visible = g.slot == null || entity.getEquippedStack(g.slot).isEmpty();
		}
		VertexConsumer vc = consumers.getBuffer(RenderLayer.getEntityCutoutNoCull(texture));
		root.render(matrices, vc, light, overlay);
	}

	private void lower(String name, ModelPart limb) {
		ModelPart low = Joints.lower(limb);
		ModelPart detail = bones.get(name);
		if (low != null) {
			follow(detail, low);
		} else {
			detail.resetTransform();
			detail.setPivot(0.0F, name.contains("shin") ? 6.0F : 4.0F, 0.0F);
			detail.visible = true;
		}
	}

	/**
	 * Las piezas de un trozo cortado (cabeza, brazo o pierna), quieto y en su sitio: lo dibuja el renderer de los trozos
	 * (con la misma postura que les pone a ellos).
	 */
	public void renderPiece(String bone, MatrixStack matrices, VertexConsumerProvider consumers, int light, int overlay) {
		ModelPart part = bones.get(bone);
		if (part == null) {
			return;
		}
		part.resetTransform();
		part.setPivot(0.0F, 0.0F, 0.0F);
		part.setAngles(0.0F, 0.0F, 0.0F);
		part.visible = true;
		for (ModelPart child : List.of(bones.getOrDefault(lowerOf(bone), part))) {
			if (child != part) {
				child.resetTransform();
				child.setPivot(0.0F, bone.contains("leg") ? 6.0F : 4.0F, 0.0F);
				child.visible = true;
			}
		}
		for (Group g : groups) {
			g.part.visible = true;
		}
		part.render(matrices, consumers.getBuffer(RenderLayer.getEntityCutoutNoCull(texture)), light, overlay);
	}

	private static String lowerOf(String bone) {
		return switch (bone) {
			case "right_arm" -> Joints.RIGHT_FOREARM;
			case "left_arm" -> Joints.LEFT_FOREARM;
			case "right_leg" -> Joints.RIGHT_SHIN;
			case "left_leg" -> Joints.LEFT_SHIN;
			default -> "";
		};
	}
}
