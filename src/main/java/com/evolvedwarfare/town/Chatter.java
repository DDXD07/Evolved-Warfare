package com.evolvedwarfare.town;

import com.evolvedwarfare.EvolvedWarfare;
import com.evolvedwarfare.entity.AbstractNpcEntity;
import com.evolvedwarfare.entity.TraderEntity;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.text.TextCodecs;
import net.minecraft.util.math.random.Random;
import org.jetbrains.annotations.Nullable;

/**
 * Las charlas de los NPCs (fase 60): cuando dos del pueblo coinciden (en la plaza por la tarde, en casa con la familia,
 * al cruzarse trabajando, dos guardias en su ronda...) se paran, se miran y hablan: uno saca un tema según lo que pasa
 * (la hora, el tiempo, su oficio, su carácter, la familia, un susto reciente, un forastero mirando, alguien del pueblo que
 * le cae bien o mal) y el otro contesta según lo que piensa de él y su carácter; a veces el primero cierra. Lo que dicen
 * sale en un bocadillo encima de su cabeza (solo lo ven los jugadores cercanos). Hablar les acerca (o les aleja, si se
 * contestan mal). Además, saludan a los jugadores que se les acercan.
 */
public final class Chatter {
	/** Los temas y cuántas frases tiene cada uno (los textos están en tools/ew_town.py). */
	static final Map<String, Integer> LINES = new LinkedHashMap<>();

	static {
		for (String t : new String[] {"greet_morning", "greet_evening", "rain", "clear", "cold", "danger", "family", "gossip_good", "gossip_bad",
				"player"}) {
			LINES.put(t, 3);
		}
		LINES.put("family", 4);
		LINES.put("guard", 4);
		LINES.put("hot", 2);
		LINES.put("hurt", 2);
		LINES.put("night", 2);
		for (Personality p : Personality.values()) {
			LINES.put("trait." + p.id, 3);
		}
		for (NpcJob j : NpcJob.values()) {
			LINES.put("work." + j.id, 2);
		}
		LINES.put("reply.friendly", 6);
		LINES.put("reply.neutral", 5);
		LINES.put("reply.rude", 5);
		LINES.put("reply.danger", 3);
		LINES.put("reply.family", 3);
		LINES.put("reply.guard", 3);
		LINES.put("reply.hurt", 2);
		LINES.put("reply.gossip_agree", 3);
		LINES.put("reply.gossip_defend", 3);
		LINES.put("close.friendly", 4);
		LINES.put("close.rude", 3);
		LINES.put("hello.friendly", 4);
		LINES.put("hello.grumpy", 3);
		LINES.put("hello.fearful", 2);
		LINES.put("hello.night", 2);
	}

	/**
	 * Lo que se dice: quién, con quién habla (-1: saluda a un jugador), el texto y cuánto dura el bocadillo (tics). El
	 * cliente solo enseña los bocadillos de una charla si estás cerca y miras a uno de los dos.
	 */
	public record SayPayload(int entityId, int partnerId, Text text, int ticks) implements CustomPayload {
		public static final Id<SayPayload> ID = new Id<>(EvolvedWarfare.id("npc_say"));
		public static final PacketCodec<RegistryByteBuf, SayPayload> CODEC = PacketCodec.tuple(PacketCodecs.VAR_INT, SayPayload::entityId,
				PacketCodecs.VAR_INT, SayPayload::partnerId, TextCodecs.REGISTRY_PACKET_CODEC, SayPayload::text, PacketCodecs.VAR_INT, SayPayload::ticks, SayPayload::new);

		@Override
		public Id<? extends CustomPayload> getId() {
			return ID;
		}
	}

	private record Line(MobEntity speaker, MobEntity listener, String topic, Object third) {
	}

	/** Una charla en marcha. */
	private static final class Talk {
		final MobEntity a, b;
		final List<Line> lines;
		int index;
		long next;

		Talk(MobEntity a, MobEntity b, List<Line> lines, long next) {
			this.a = a;
			this.b = b;
			this.lines = lines;
			this.next = next;
		}
	}

	/** Las charlas en marcha, por cada uno de los que hablan (id de entidad). */
	private static final Map<Integer, Talk> TALKS = new HashMap<>();
	/** Cuándo puede volver a charlar o saludar cada uno (por UUID, para todos los NPCs). */
	private static final Map<UUID, Long> COOLDOWN = new HashMap<>();
	private static final Map<UUID, Long> GREETED = new HashMap<>();

	private Chatter() {
	}

	public static void register() {
		PayloadTypeRegistry.playS2C().register(SayPayload.ID, SayPayload.CODEC);
		ServerTickEvents.END_WORLD_TICK.register(Chatter::tickWorld);
	}

	public static String key(String topic, int n) {
		return "chat.evolvedwarfare." + topic + "." + n;
	}

	/** ¿Está charlando? */
	public static boolean talking(Entity e) {
		return TALKS.containsKey(e.getId());
	}

	/** Con quién charla (o null). */
	@Nullable
	public static MobEntity partner(Entity e) {
		Talk t = TALKS.get(e.getId());
		return t == null ? null : t.a == e ? t.b : t.a;
	}

	// ------------------------------------------------------------------ empezar

	/** ¿Está libre para charlar? (despierto, sin pelear, sin peligro, sin cliente, sin charla y sin haber charlado hace poco). */
	private static boolean free(MobEntity e, ServerWorld world) {
		if (!e.isAlive() || e.isSleeping() || e.getTarget() != null || talking(e) || !(e instanceof Townsfolk)) {
			return false;
		}
		if (COOLDOWN.getOrDefault(e.getUuid(), 0L) > world.getTime()) {
			return false;
		}
		if (e instanceof AbstractNpcEntity npc) {
			Activity a = npc.getActivity();
			return a != Activity.SLEEP && a != Activity.HIDE && npc.ewProfile().dangerUntil <= world.getTime();
		}
		return !(e instanceof TraderEntity trader) || !trader.hasCustomer();
	}

	/** Cada dos segundos, cada NPC mira si tiene con quién charlar (y si hay un jugador al que saludar). */
	public static void consider(AbstractNpcEntity npc, ServerWorld world) {
		if ((npc.age + npc.getId()) % 40 != 0 || !free(npc, world)) {
			return;
		}
		Random random = npc.getRandom();
		greet(npc, world);
		Activity act = npc.getActivity();
		NpcProfile p = npc.ewProfile();
		int chance;
		double radius = 4.5;
		switch (act) {
			case LEISURE -> {
				chance = 2;
				radius = 6.0;
			}
			case HOME, BREAKFAST, DINNER -> chance = 4;
			case WORK -> {
				chance = 10;
				radius = 3.0;
			}
			case PATROL -> chance = 5;
			case NONE -> chance = npc.getNavigation().isIdle() ? 8 : 0;
			default -> chance = 0;
		}
		if (chance == 0 || random.nextInt(chance) != 0) {
			return;
		}
		MobEntity best = null;
		double bestScore = Double.MAX_VALUE;
		for (MobEntity other : world.getEntitiesByClass(MobEntity.class, npc.getBoundingBox().expand(radius, 2.0, radius),
				e -> e != npc && free(e, world))) {
			NpcProfile o = ((Townsfolk) other).ewProfile();
			// en casa solo con la familia; en la ronda, guardias con guardias
			if ((act == Activity.HOME || act == Activity.BREAKFAST || act == Activity.DINNER) && !p.isFamily(other.getUuid())) {
				continue;
			}
			if (act == Activity.PATROL && o.job != NpcJob.GUARD) {
				continue;
			}
			// se prefiere a los amigos (y a los que están cerca)
			double score = npc.squaredDistanceTo(other) - p.affinity(other.getUuid()) * 0.1;
			if (score < bestScore && npc.canSee(other)) {
				bestScore = score;
				best = other;
			}
		}
		if (best != null) {
			start(npc, best, world);
		}
	}

	/** Empieza una charla de {@code a} con {@code b} (también para las pruebas). */
	public static void start(MobEntity a, MobEntity b, ServerWorld world) {
		List<Line> lines = compose(a, b, world);
		if (lines.isEmpty()) {
			return;
		}
		Talk talk = new Talk(a, b, lines, world.getTime() + 5);
		TALKS.put(a.getId(), talk);
		TALKS.put(b.getId(), talk);
		a.getNavigation().stop();
		b.getNavigation().stop();
	}

	// ------------------------------------------------------------------ de qué hablan

	private static List<Line> compose(MobEntity a, MobEntity b, ServerWorld world) {
		Random random = a.getRandom();
		NpcProfile pa = ((Townsfolk) a).ewProfile(), pb = ((Townsfolk) b).ewProfile();
		long t = Routine.timeOfDay(world);
		boolean family = pa.isFamily(b.getUuid());
		Map<String, Integer> weights = new LinkedHashMap<>();
		weights.put("work." + pa.job.id, 2);
		weights.put("trait." + pa.personality.id, 2);
		if (family) {
			weights.put("family", 5);
		}
		if (pa.dangerUntil > world.getTime() - 2400L && pa.dangerUntil > 0) {
			weights.put("danger", 6);
		}
		if (a.getHealth() < a.getMaxHealth() * 0.6F) {
			weights.put("hurt", 4);
		}
		if (pa.job == NpcJob.GUARD && pb.job == NpcJob.GUARD) {
			weights.put("guard", 6);
		}
		if (world.isRaining()) {
			weights.put("rain", 2);
		} else {
			float temp = world.getBiome(a.getBlockPos()).value().getTemperature();
			weights.put(temp < 0.2F ? "cold" : temp > 1.5F ? "hot" : "clear", temp < 0.2F || temp > 1.5F ? 2 : 1);
		}
		if (t < 2000L) {
			weights.put("greet_morning", 2);
		} else if (t > 11000L && t < 13000L) {
			weights.put("greet_evening", 2);
		} else if (t >= 13000L) {
			weights.put("night", 2);
		}
		PlayerEntity watcher = world.getClosestPlayer(a, 10.0);
		if (watcher != null && !watcher.isSpectator()) {
			weights.put("player", 2);
		}
		// alguien del pueblo que le cae muy bien o muy mal (y que no es el que escucha)
		Text third = null;
		UUID thirdId = null;
		boolean likesThird = false;
		for (Map.Entry<UUID, Integer> e : pa.relations().entrySet()) {
			if (e.getKey().equals(b.getUuid()) || Math.abs(e.getValue()) < 25 || random.nextInt(3) != 0) {
				continue;
			}
			Entity who = world.getEntity(e.getKey());
			if (who != null && who.isAlive()) {
				third = who.getName();
				thirdId = e.getKey();
				likesThird = e.getValue() > 0;
				weights.put(likesThird ? "gossip_good" : "gossip_bad", 3);
				break;
			}
		}
		String topic = pick(weights, random);
		// la respuesta: según lo que piensa del otro y su carácter
		int mood = pb.affinity(a.getUuid()) + switch (pb.personality) {
			case GRUMPY -> -20;
			case KIND, CHEERFUL -> 12;
			default -> 0;
		} + random.nextInt(21) - 10;
		String tone = mood > 25 ? "friendly" : mood < -15 ? "rude" : "neutral";
		String reply = "reply." + tone;
		if (!tone.equals("rude")) {
			switch (topic) {
				case "danger", "family", "guard", "hurt" -> reply = "reply." + topic;
				case "gossip_good", "gossip_bad" -> {
					UUID who = thirdId;
					int theirs = who == null ? 0 : pb.affinity(who);
					boolean agree = (theirs > 0) == likesThird || Math.abs(theirs) < 10;
					reply = agree ? "reply.gossip_agree" : "reply.gossip_defend";
					// se contagia un poco la opinión
					if (who != null && agree && pb.affinity(a.getUuid()) > 0) {
						pb.like(who, likesThird ? 2 : -2);
					}
				}
				default -> {
				}
			}
		}
		List<Line> lines = new ArrayList<>();
		lines.add(new Line(a, b, topic, third));
		lines.add(new Line(b, a, reply, third));
		if (random.nextBoolean()) {
			lines.add(new Line(a, b, tone.equals("rude") ? "close.rude" : "close.friendly", third));
		}
		// lo que cambia entre ellos
		int delta = switch (tone) {
			case "friendly" -> 3;
			case "rude" -> -3;
			default -> 1;
		};
		pa.like(b.getUuid(), delta);
		pb.like(a.getUuid(), tone.equals("rude") ? -1 : delta);
		return lines;
	}

	private static String pick(Map<String, Integer> weights, Random random) {
		int total = 0;
		for (int w : weights.values()) {
			total += w;
		}
		int r = random.nextInt(Math.max(1, total));
		for (Map.Entry<String, Integer> e : weights.entrySet()) {
			r -= e.getValue();
			if (r < 0) {
				return e.getKey();
			}
		}
		return weights.keySet().iterator().next();
	}

	/** El texto de una frase al azar del tema: con el nombre del que escucha, del tercero y el oficio del que habla. */
	static Text line(String topic, MobEntity speaker, @Nullable Entity listener, @Nullable Object third, Random random) {
		int n = 1 + random.nextInt(LINES.getOrDefault(topic, 1));
		Text job = speaker instanceof Townsfolk t ? NpcProfile.jobOf(t.ewProfileBits()).label(NpcProfile.femaleOf(t.ewProfileBits())) : Text.empty();
		return Text.translatable(key(topic, n), listener == null ? Text.literal("") : listener.getName(),
				third instanceof Text tx ? tx : Text.literal(""), job);
	}

	// ------------------------------------------------------------------ decirlo

	/** Cuántas frases se han dicho (para las pruebas). */
	public static long spoken;

	/** Lo dice: bocadillo para los jugadores que lo ven y un murmullo. */
	public static void say(MobEntity speaker, @Nullable Entity partner, Text text, ServerWorld world) {
		spoken++;
		int ticks = Math.min(140, 50 + text.getString().length() * 2);
		SayPayload payload = new SayPayload(speaker.getId(), partner == null ? -1 : partner.getId(), text, ticks);
		for (ServerPlayerEntity player : PlayerLookup.tracking(speaker)) {
			if (player.squaredDistanceTo(speaker) < 24 * 24) {
				ServerPlayNetworking.send(player, payload);
			}
		}
		speaker.playAmbientSound();
		speaker.swingHand(net.minecraft.util.Hand.OFF_HAND);
	}

	private static void tickWorld(ServerWorld world) {
		if (TALKS.isEmpty()) {
			return;
		}
		long now = world.getTime();
		List<Talk> done = new ArrayList<>();
		for (Talk talk : new ArrayList<>(new java.util.LinkedHashSet<>(TALKS.values()))) {
			if (talk.a.getWorld() != world) {
				continue;
			}
			boolean broken = !talk.a.isAlive() || !talk.b.isAlive() || talk.a.isRemoved() || talk.b.isRemoved() || talk.a.isSleeping()
					|| talk.b.isSleeping() || talk.a.getTarget() != null || talk.b.getTarget() != null || talk.a.squaredDistanceTo(talk.b) > 8 * 8
					|| hiding(talk.a) || hiding(talk.b);
			if (broken || talk.index >= talk.lines.size()) {
				done.add(talk);
				continue;
			}
			// se miran mientras hablan
			talk.a.getLookControl().lookAt(talk.b, 30.0F, 30.0F);
			talk.b.getLookControl().lookAt(talk.a, 30.0F, 30.0F);
			if (now >= talk.next) {
				Line l = talk.lines.get(talk.index++);
				Text text = line(l.topic(), l.speaker(), l.listener(), l.third(), l.speaker().getRandom());
				say(l.speaker(), l.listener(), text, world);
				talk.next = now + Math.min(140, 50 + text.getString().length() * 2) - 10;
			}
		}
		for (Talk talk : done) {
			end(talk, now);
		}
	}

	private static boolean hiding(MobEntity e) {
		return e instanceof AbstractNpcEntity npc && (npc.getActivity() == Activity.HIDE || npc.getActivity() == Activity.SLEEP);
	}

	private static void end(Talk talk, long now) {
		TALKS.remove(talk.a.getId());
		TALKS.remove(talk.b.getId());
		for (MobEntity e : new MobEntity[] {talk.a, talk.b}) {
			COOLDOWN.put(e.getUuid(), now + 600 + e.getRandom().nextInt(900));
		}
		if (COOLDOWN.size() > 4096) {
			COOLDOWN.entrySet().removeIf(e -> e.getValue() < now);
		}
	}

	/** Saluda al jugador que se le acerca (de vez en cuando, cada uno a su manera). */
	private static void greet(AbstractNpcEntity npc, ServerWorld world) {
		PlayerEntity player = world.getClosestPlayer(npc, 3.5);
		if (player == null || player.isSpectator() || player.isSneaking() || npc.getRandom().nextInt(3) != 0) {
			return;
		}
		long now = world.getTime();
		if (GREETED.getOrDefault(npc.getUuid(), 0L) > now) {
			return;
		}
		GREETED.put(npc.getUuid(), now + 2400 + npc.getRandom().nextInt(2400));
		if (GREETED.size() > 4096) {
			GREETED.entrySet().removeIf(e -> e.getValue() < now);
		}
		NpcProfile p = npc.ewProfile();
		String topic = Routine.timeOfDay(world) >= 12500L ? "hello.night"
				: p.personality == Personality.GRUMPY ? "hello.grumpy" : p.personality == Personality.FEARFUL ? "hello.fearful" : "hello.friendly";
		npc.getLookControl().lookAt(player, 30.0F, 30.0F);
		say(npc, null, line(topic, npc, player, null, npc.getRandom()), world);
	}

	/** Para las pruebas: termina todas las charlas. */
	public static void clear() {
		TALKS.clear();
		COOLDOWN.clear();
		GREETED.clear();
	}

	/** Para las pruebas: las frases que se dirían en una charla de {@code a} con {@code b} (sin decirlas). */
	public static List<Text> preview(MobEntity a, MobEntity b, ServerWorld world) {
		List<Text> out = new ArrayList<>();
		for (Line l : compose(a, b, world)) {
			out.add(line(l.topic(), l.speaker(), l.listener(), l.third(), a.getRandom()));
		}
		return out;
	}

	/** Todas las claves de texto de las charlas (para comprobar que existen). */
	public static List<String> allKeys() {
		List<String> keys = new ArrayList<>();
		for (Map.Entry<String, Integer> e : LINES.entrySet()) {
			for (int n = 1; n <= e.getValue(); n++) {
				keys.add(key(e.getKey(), n));
			}
		}
		return keys;
	}
}
