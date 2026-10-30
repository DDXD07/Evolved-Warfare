package com.evolvedwarfare.gametest;

import com.evolvedwarfare.EvolvedWarfare;
import com.evolvedwarfare.registry.ModEntities;
import com.evolvedwarfare.registry.ModSounds;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.registry.Registries;
import net.minecraft.sound.SoundEvent;
import net.minecraft.test.GameTest;
import net.minecraft.test.GameTestException;
import net.minecraft.test.TestContext;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Las voces de los NPCs: cada facción con la suya, todas registradas y con sus .ogg, y cada NPC con su tono. */
public class NpcVoiceGameTests implements FabricGameTest {
	private static final Logger LOG = LoggerFactory.getLogger("ew-gametest");

	/** Cada sonido «npc.*» de sounds.json está registrado y sus archivos existen (y al revés). */
	@GameTest(templateName = EMPTY_STRUCTURE)
	public void voiceFilesExist(TestContext ctx) {
		JsonObject sounds = read("/assets/evolvedwarfare/sounds.json").getAsJsonObject();
		int events = 0;
		int files = 0;
		for (Map.Entry<String, JsonElement> entry : sounds.entrySet()) {
			if (!entry.getKey().startsWith("npc.")) {
				continue;
			}
			events++;
			if (!Registries.SOUND_EVENT.containsId(EvolvedWarfare.id(entry.getKey()))) {
				throw new GameTestException("Sonido sin registrar: " + entry.getKey());
			}
			for (JsonElement sound : entry.getValue().getAsJsonObject().getAsJsonArray("sounds")) {
				String name = sound.isJsonObject() ? sound.getAsJsonObject().get("name").getAsString() : sound.getAsString();
				Identifier id = Identifier.of(name);
				String path = "/assets/" + id.getNamespace() + "/sounds/" + id.getPath() + ".ogg";
				if (NpcVoiceGameTests.class.getResource(path) == null) {
					throw new GameTestException("Falta el archivo " + path + " de " + entry.getKey());
				}
				files++;
			}
		}
		for (Identifier id : Registries.SOUND_EVENT.getIds()) {
			if (id.getNamespace().equals(EvolvedWarfare.MOD_ID) && id.getPath().startsWith("npc.") && !sounds.has(id.getPath())) {
				throw new GameTestException("Registrado pero sin entrada en sounds.json: " + id);
			}
		}
		LOG.info("[EW-GAMETEST] voces: {} sonidos, {} archivos", events, files);
		if (events < 18) {
			throw new GameTestException("Faltan voces: solo " + events);
		}
		ctx.complete();
	}

	/** Bandidos, vikingos, piratas, tu gente y los comerciantes hablan, se quejan y mueren con su propia voz. */
	@GameTest(templateName = EMPTY_STRUCTURE)
	public void eachFactionHasItsVoice(TestContext ctx) {
		check(ctx, ModEntities.BANDIT, ModSounds.BANDIT);
		check(ctx, ModEntities.VIKING, ModSounds.VIKING);
		check(ctx, ModEntities.PIRATE, ModSounds.PIRATE);
		check(ctx, ModEntities.RECRUIT, ModSounds.RECRUIT);
		check(ctx, ModEntities.SOLDIER, ModSounds.RECRUIT);
		check(ctx, ModEntities.WORKER, ModSounds.RECRUIT);
		check(ctx, ModEntities.TRADER, ModSounds.TRADER);
		ctx.complete();
	}

	private static void check(TestContext ctx, EntityType<? extends LivingEntity> type, ModSounds.Voice voice) {
		List<Float> pitches = new ArrayList<>();
		LivingEntity first = null;
		for (int i = 0; i < 6; i++) {
			LivingEntity npc = ctx.spawnEntity(type, 1.5F + i, 2.0F, 1.5F);
			if (first == null) {
				first = npc;
			}
			pitches.add((Float) call(npc, "getSoundPitch"));
			if (npc != first) {
				npc.discard();
			}
		}
		SoundEvent ambient = (SoundEvent) call(first, "getAmbientSound");
		SoundEvent hurt = (SoundEvent) call(first, "getHurtSound", first.getDamageSources().generic());
		SoundEvent death = (SoundEvent) call(first, "getDeathSound");
		float min = pitches.stream().min(Float::compare).orElse(1.0F);
		float max = pitches.stream().max(Float::compare).orElse(1.0F);
		LOG.info("[EW-GAMETEST] voz de {}: {} / {} / {}, tonos {}..{}", EntityType.getId(type), id(ambient), id(hurt), id(death), min, max);
		first.discard();
		if (ambient != voice.ambient() || hurt != voice.hurt() || death != voice.death()) {
			throw new GameTestException(EntityType.getId(type) + " no usa su voz: " + id(ambient) + ", " + id(hurt) + ", " + id(death));
		}
		if (min < 0.8F || max > 1.2F || max - min < 0.01F) {
			throw new GameTestException(EntityType.getId(type) + ": tonos raros " + pitches);
		}
	}

	private static String id(SoundEvent sound) {
		return sound == null ? "nada" : sound.getId().toString();
	}

	/** Los métodos de sonido son protegidos: en el entorno de pruebas los nombres son los de Yarn. */
	private static Object call(LivingEntity entity, String name, Object... args) {
		Class<?>[] types = args.length == 0 ? new Class<?>[0] : new Class<?>[] {DamageSource.class};
		for (Class<?> c = entity.getClass(); c != null; c = c.getSuperclass()) {
			try {
				Method method = c.getDeclaredMethod(name, types);
				method.setAccessible(true);
				return method.invoke(entity, args);
			} catch (NoSuchMethodException e) {
				// en la clase de arriba
			} catch (ReflectiveOperationException e) {
				throw new GameTestException(name + ": " + e);
			}
		}
		throw new GameTestException("No está " + name + " en " + entity.getClass());
	}

	private static JsonElement read(String path) {
		try (InputStream in = NpcVoiceGameTests.class.getResourceAsStream(path)) {
			if (in == null) {
				throw new GameTestException("No está " + path);
			}
			return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8));
		} catch (java.io.IOException e) {
			throw new GameTestException(path + ": " + e);
		}
	}
}
