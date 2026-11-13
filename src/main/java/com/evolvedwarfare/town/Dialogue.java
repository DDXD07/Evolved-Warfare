package com.evolvedwarfare.town;

import com.evolvedwarfare.EvolvedWarfare;
import com.evolvedwarfare.entity.AbstractNpcEntity;
import com.evolvedwarfare.entity.CityGuardEntity;
import com.evolvedwarfare.entity.NpcOrder;
import com.evolvedwarfare.entity.TraderEntity;
import com.evolvedwarfare.item.RecruitmentContractItem;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.text.TextCodecs;
import net.minecraft.util.math.random.Random;
import org.jetbrains.annotations.Nullable;

/**
 * Hablar con los NPCs (fase 61): una pantalla con su retrato, quién es, lo que piensa de ti y lo que está haciendo, lo
 * que te contesta y lo que le puedes decir: cómo está, qué hace, que te hable de sí mismo (su familia, sus amigos, sus
 * enemigos), qué se cuenta por el pueblo, si trabajaría para ti (y contratarlo ahí mismo, con un contrato del inventario),
 * órdenes si es tuyo (sígueme, espera, vive aquí, su equipo y paga) o ver la tienda si es mercader. Lo que piensa de ti
 * cambia: charlar con él (una vez al día), hacerle un regalo de comida y contratarlo le gustan; pegarle, no.
 */
public final class Dialogue {
	public enum Option {
		HOW, DOING, ABOUT, NEWS, HIRE, HIRE_YES, FOLLOW, STAY, LIVE, GEAR, TRADE, BYE;

		public String id() {
			return name().toLowerCase(java.util.Locale.ROOT);
		}
	}

	/** Cuántas frases tiene cada respuesta (los textos están en tools/ew_town.py). */
	static final Map<String, Integer> LINES = new HashMap<>();

	static {
		LINES.put("open.friendly", 3);
		LINES.put("open.neutral", 3);
		LINES.put("open.rude", 3);
		LINES.put("open.night", 2);
		LINES.put("how.hurt", 2);
		LINES.put("how.scared", 2);
		LINES.put("how.hungry", 1);
		LINES.put("how.rude", 3);
		for (Personality p : Personality.values()) {
			LINES.put("how." + p.id, 2);
			LINES.put("about.trait." + p.id, 1);
		}
		for (Activity a : Activity.values()) {
			LINES.put("doing." + a.id(), 1);
		}
		LINES.put("doing.leisure", 2);
		LINES.put("doing.patrol", 2);
		LINES.put("doing.follow", 1);
		LINES.put("doing.stay", 1);
		LINES.put("news.none", 2);
		LINES.put("news.danger", 1);
		LINES.put("news.people", 1);
		LINES.put("hire.refuse", 2);
		LINES.put("hire.done", 2);
		LINES.put("order.ok", 3);
		LINES.put("order.lazy", 1);
		LINES.put("order.grumpy", 1);
		LINES.put("gift.thanks", 3);
	}

	// ------------------------------------------------------------------ paquetes

	/** Lo que ve el jugador: con quién habla, cómo es, lo que piensa de él, qué hace, lo que contesta y qué puede decirle. */
	public record StatePayload(int entityId, int profileBits, int mood, Text activity, Text reply, List<Integer> options, int hireCoins)
			implements CustomPayload {
		public static final Id<StatePayload> ID = new Id<>(EvolvedWarfare.id("dialogue_state"));
		public static final PacketCodec<RegistryByteBuf, StatePayload> CODEC = PacketCodec.of(StatePayload::write, StatePayload::read);

		private void write(RegistryByteBuf buf) {
			buf.writeVarInt(entityId);
			buf.writeVarInt(profileBits);
			buf.writeVarInt(mood);
			TextCodecs.REGISTRY_PACKET_CODEC.encode(buf, activity);
			TextCodecs.REGISTRY_PACKET_CODEC.encode(buf, reply);
			buf.writeVarInt(options.size());
			for (int o : options) {
				buf.writeVarInt(o);
			}
			buf.writeVarInt(hireCoins);
		}

		private static StatePayload read(RegistryByteBuf buf) {
			int id = buf.readVarInt(), bits = buf.readVarInt(), mood = buf.readVarInt();
			Text activity = TextCodecs.REGISTRY_PACKET_CODEC.decode(buf);
			Text reply = TextCodecs.REGISTRY_PACKET_CODEC.decode(buf);
			int n = Math.min(32, buf.readVarInt());
			List<Integer> options = new ArrayList<>();
			for (int i = 0; i < n; i++) {
				options.add(buf.readVarInt());
			}
			return new StatePayload(id, bits, mood, activity, reply, options, buf.readVarInt());
		}

		@Override
		public Id<? extends CustomPayload> getId() {
			return ID;
		}
	}

	/** Lo que elige el jugador. */
	public record ActionPayload(int entityId, int option) implements CustomPayload {
		public static final Id<ActionPayload> ID = new Id<>(EvolvedWarfare.id("dialogue_action"));
		public static final PacketCodec<RegistryByteBuf, ActionPayload> CODEC = PacketCodec.of(
				(p, buf) -> {
					buf.writeVarInt(p.entityId);
					buf.writeVarInt(p.option);
				}, buf -> new ActionPayload(buf.readVarInt(), buf.readVarInt()));

		@Override
		public Id<? extends CustomPayload> getId() {
			return ID;
		}
	}

	/** Cuándo charló cada NPC con cada jugador por última vez (día) y cuándo le regalaron algo (para no subir sin fin). */
	private static final Map<UUID, Long> LAST_CHAT = new HashMap<>();
	private static final Map<UUID, Long> LAST_GIFT = new HashMap<>();

	private Dialogue() {
	}

	public static void register() {
		PayloadTypeRegistry.playS2C().register(StatePayload.ID, StatePayload.CODEC);
		PayloadTypeRegistry.playC2S().register(ActionPayload.ID, ActionPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(ActionPayload.ID, (payload, context) -> {
			ServerPlayerEntity player = context.player();
			Entity e = player.getWorld().getEntityById(payload.entityId());
			Option[] all = Option.values();
			if (e instanceof MobEntity npc && e instanceof Townsfolk && e.isAlive() && player.squaredDistanceTo(e) < 10 * 10 && payload.option() >= 0
					&& payload.option() < all.length) {
				act(player, npc, all[payload.option()]);
			}
		});
	}

	public static String key(String topic, int n) {
		return "dialogue.evolvedwarfare." + topic + "." + n;
	}

	private static Text say(String topic, Random random, Object... args) {
		int n = 1 + random.nextInt(LINES.getOrDefault(topic, 1));
		return Text.translatable(key(topic, n), args);
	}

	// ------------------------------------------------------------------ lo que piensa de ti

	public static int mood(MobEntity npc, PlayerEntity player) {
		return ((Townsfolk) npc).ewProfile().affinity(player.getUuid());
	}

	/** Le gusta (o no) algo que has hecho. */
	public static void like(MobEntity npc, PlayerEntity player, int delta) {
		((Townsfolk) npc).ewProfile().like(player.getUuid(), delta);
	}

	/** Un regalo de comida: le gusta (una vez cada minuto, como mucho). Devuelve si lo acepta. */
	public static boolean gift(MobEntity npc, ServerPlayerEntity player, ItemStack stack) {
		if (stack.get(DataComponentTypes.FOOD) == null || !(npc.getWorld() instanceof ServerWorld world)) {
			return false;
		}
		UUID key = new UUID(npc.getUuid().getMostSignificantBits(), player.getUuid().getLeastSignificantBits());
		long now = world.getTime();
		if (LAST_GIFT.getOrDefault(key, -100000L) > now - 1200L) {
			Chatter.say(npc, null, Text.translatable("dialogue.evolvedwarfare.gift.again.1"), world);
			return true;
		}
		LAST_GIFT.put(key, now);
		stack.decrementUnlessCreative(1, player);
		Personality who = ((Townsfolk) npc).ewProfile().personality;
		like(npc, player, who == Personality.GRUMPY ? 4 : who == Personality.KIND || who == Personality.CHEERFUL ? 12 : 8);
		Chatter.say(npc, null, say("gift.thanks", npc.getRandom()), world);
		world.spawnParticles(ParticleTypes.HEART, npc.getX(), npc.getEyeY() + 0.4, npc.getZ(), 3, 0.3, 0.2, 0.3, 0.0);
		npc.playSound(SoundEvents.ENTITY_GENERIC_EAT, 0.5F, 1.0F);
		return true;
	}

	public static Text moodLabel(int mood) {
		String k = mood >= 50 ? "love" : mood >= 15 ? "like" : mood > -15 ? "neutral" : mood > -50 ? "dislike" : "hate";
		return Text.translatable("dialogue.evolvedwarfare.mood." + k);
	}

	// ------------------------------------------------------------------ abrir y contestar

	/** Empieza a hablar con él: saluda (según lo que piensa de ti) y, una vez al día, charlar le gusta un poco. */
	public static void open(ServerPlayerEntity player, MobEntity npc) {
		ServerWorld world = (ServerWorld) npc.getWorld();
		UUID key = new UUID(npc.getUuid().getMostSignificantBits(), player.getUuid().getLeastSignificantBits());
		long day = world.getTimeOfDay() / 24000L;
		if (LAST_CHAT.getOrDefault(key, -1L) != day) {
			LAST_CHAT.put(key, day);
			if (mood(npc, player) < 40) {
				like(npc, player, 2);
			}
		}
		int mood = mood(npc, player);
		String open = Routine.timeOfDay(world) >= 13000L ? "open.night" : mood < -20 ? "open.rude" : mood > 20 ? "open.friendly" : "open.neutral";
		npc.getNavigation().stop();
		npc.getLookControl().lookAt(player, 30.0F, 30.0F);
		send(player, npc, say(open, npc.getRandom(), player.getName()));
	}

	/** Lo que el jugador le dice: le contesta (y la pantalla se actualiza), o se cierra. */
	public static void act(ServerPlayerEntity player, MobEntity npc, Option option) {
		Text reply = reply(player, npc, option);
		if (reply != null && npc.isAlive()) {
			send(player, npc, reply);
		}
	}

	/** Lo que contesta (y hace) a eso; null si no hay que contestar (se cierra o se abre otra pantalla). */
	@Nullable
	public static Text reply(ServerPlayerEntity player, MobEntity npc, Option option) {
		ServerWorld world = (ServerWorld) npc.getWorld();
		Random random = npc.getRandom();
		NpcProfile p = ((Townsfolk) npc).ewProfile();
		int mood = mood(npc, player);
		AbstractNpcEntity mine = npc instanceof AbstractNpcEntity a && a.isOwner(player) ? a : null;
		npc.getLookControl().lookAt(player, 30.0F, 30.0F);
		Text reply = switch (option) {
			case HOW -> how(npc, p, mood, world, random);
			case DOING -> doing(npc, p, random);
			case ABOUT -> about(npc, p, world, random);
			case NEWS -> news(npc, p, world, random);
			case HIRE -> hireAsk(npc, mood, random);
			case HIRE_YES -> {
				Text done = hire(player, npc, mood, random);
				if (done == null) {
					// contratado: el NPC puede haber cambiado (guardia → soldado, mercader → recluta); se cierra
					return null;
				}
				yield done;
			}
			case FOLLOW, STAY, LIVE -> {
				if (mine == null) {
					yield Text.translatable("message.evolvedwarfare.not_yours", npc.getName());
				}
				if (option == Option.FOLLOW) {
					mine.setOrder(NpcOrder.FOLLOW);
				} else if (option == Option.STAY) {
					mine.setOrder(NpcOrder.STAY);
				} else {
					mine.setHomeHere();
				}
				yield say(p.personality == Personality.LAZY ? "order.lazy" : p.personality == Personality.GRUMPY ? "order.grumpy" : "order.ok", random,
						player.getName());
			}
			case GEAR -> {
				if (mine != null) {
					mine.openGearMenu(player);
				}
				yield null;
			}
			case TRADE -> {
				if (npc instanceof TraderEntity trader) {
					trader.openCatalog(player, trader.catalogNearby());
				}
				yield null;
			}
			case BYE -> null;
		};
		return reply;
	}

	/** Le manda al jugador la pantalla (o la actualiza) con la respuesta y lo que puede decirle ahora. */
	static void send(ServerPlayerEntity player, MobEntity npc, Text reply) {
		List<Integer> options = new ArrayList<>();
		for (Option o : new Option[] {Option.HOW, Option.DOING, Option.ABOUT, Option.NEWS}) {
			options.add(o.ordinal());
		}
		int coins = -1;
		if (npc instanceof AbstractNpcEntity a && a.isOwner(player)) {
			options.add(Option.FOLLOW.ordinal());
			options.add(Option.STAY.ordinal());
			options.add(Option.LIVE.ordinal());
			options.add(Option.GEAR.ordinal());
		} else if (hireable(npc)) {
			coins = npc instanceof CityGuardEntity ? Hiring.GUARD_COINS : npc instanceof TraderEntity ? Hiring.MERCHANT_COINS : 0;
			options.add(Option.HIRE.ordinal());
			options.add(Option.HIRE_YES.ordinal());
		}
		if (npc instanceof TraderEntity) {
			options.add(Option.TRADE.ordinal());
		}
		options.add(Option.BYE.ordinal());
		ServerPlayNetworking.send(player, new StatePayload(npc.getId(), ((Townsfolk) npc).ewProfileBits(), mood(npc, player), activityLabel(npc),
				reply, options, coins));
	}

	private static boolean hireable(MobEntity npc) {
		if (npc instanceof CityGuardEntity || npc instanceof TraderEntity) {
			return true;
		}
		return npc instanceof AbstractNpcEntity a && a.isForHire();
	}

	public static Text activityLabel(MobEntity npc) {
		Activity a = npc instanceof AbstractNpcEntity n ? n.getActivity() : npc.isSleeping() ? Activity.SLEEP : Activity.WORK;
		if (a == Activity.NONE && npc instanceof AbstractNpcEntity n && !n.isForHire()) {
			return Text.translatable("dialogue.evolvedwarfare.activity.order." + n.getEffectiveOrder().name().toLowerCase(java.util.Locale.ROOT));
		}
		return Text.translatable("dialogue.evolvedwarfare.activity." + a.id());
	}

	// ------------------------------------------------------------------ respuestas

	private static Text how(MobEntity npc, NpcProfile p, int mood, ServerWorld world, Random random) {
		if (mood < -20) {
			return say("how.rude", random);
		}
		if (npc.getHealth() < npc.getMaxHealth() * 0.5F) {
			return say("how.hurt", random);
		}
		if (p.dangerUntil > world.getTime() - 2400L && p.dangerUntil > 0) {
			return say("how.scared", random);
		}
		if (npc instanceof AbstractNpcEntity a && a.lastMeal > 0 && world.getTime() - a.lastMeal > 30000L) {
			return say("how.hungry", random);
		}
		return say("how." + p.personality.id, random);
	}

	private static Text doing(MobEntity npc, NpcProfile p, Random random) {
		boolean female = NpcProfile.femaleOf(((Townsfolk) npc).ewProfileBits());
		Text job = p.job.label(female);
		Activity a = npc instanceof AbstractNpcEntity n ? n.getActivity() : npc.isSleeping() ? Activity.SLEEP : Activity.WORK;
		if (a == Activity.NONE && npc instanceof AbstractNpcEntity n && !n.isForHire()) {
			return say(n.getEffectiveOrder() == NpcOrder.FOLLOW ? "doing.follow" : "doing.stay", random);
		}
		if (a == Activity.WORK) {
			// lo de su oficio (las mismas frases que en las charlas)
			int n = 1 + random.nextInt(Chatter.LINES.getOrDefault("work." + p.job.id, 1));
			return Text.translatable(key("doing.work", 1), job).append(" ").append(Text.translatable(Chatter.key("work." + p.job.id, n)));
		}
		return say("doing." + a.id(), random, job);
	}

	private static Text about(MobEntity npc, NpcProfile p, ServerWorld world, Random random) {
		boolean female = NpcProfile.femaleOf(((Townsfolk) npc).ewProfileBits());
		MutableText out = Text.translatable(key("about.intro", 1), npc.getName(), p.job.label(female));
		out.append(" ").append(Text.translatable(key("about.trait." + p.personality.id, 1)));
		if (npc instanceof AbstractNpcEntity a && a.getOwner() != null) {
			out.append(" ").append(Text.translatable(key("about.hired", 1), a.getOwner().getName()));
		}
		List<Text> family = new ArrayList<>();
		UUID best = null, worst = null;
		int bestV = 20, worstV = -20;
		for (Map.Entry<UUID, Integer> e : p.relations().entrySet()) {
			Entity who = world.getEntity(e.getKey());
			if (who == null || !who.isAlive() || who instanceof PlayerEntity) {
				continue;
			}
			if (p.isFamily(e.getKey())) {
				family.add(who.getName());
			} else if (e.getValue() > bestV) {
				bestV = e.getValue();
				best = e.getKey();
			} else if (e.getValue() < worstV) {
				worstV = e.getValue();
				worst = e.getKey();
			}
		}
		if (!family.isEmpty()) {
			out.append(" ").append(Text.translatable(key("about.family", 1), Text.literal(String.join(", ", family.stream().map(Text::getString).toList()))));
		} else if (p.community != 0) {
			out.append(" ").append(Text.translatable(key("about.alone", 1)));
		}
		if (best != null) {
			out.append(" ").append(Text.translatable(key("about.friend", 1), world.getEntity(best).getName()));
		}
		if (worst != null) {
			out.append(" ").append(Text.translatable(key("about.enemy", 1), world.getEntity(worst).getName()));
		}
		return out;
	}

	private static Text news(MobEntity npc, NpcProfile p, ServerWorld world, Random random) {
		if (p.dangerUntil > world.getTime() - 6000L && p.dangerUntil > 0) {
			return say("news.danger", random);
		}
		// cotilleo sobre alguien del pueblo
		for (Map.Entry<UUID, Integer> e : p.relations().entrySet()) {
			if (Math.abs(e.getValue()) < 25 || random.nextInt(2) != 0) {
				continue;
			}
			Entity who = world.getEntity(e.getKey());
			if (who != null && who.isAlive() && !(who instanceof PlayerEntity)) {
				String topic = e.getValue() > 0 ? "gossip_good" : "gossip_bad";
				int n = 1 + random.nextInt(Chatter.LINES.getOrDefault(topic, 1));
				return Text.translatable(Chatter.key(topic, n), Text.literal(""), who.getName(), Text.literal(""));
			}
		}
		if (p.community != 0 && random.nextBoolean()) {
			int people = world.getEntitiesByClass(MobEntity.class, npc.getBoundingBox().expand(64.0, 24.0, 64.0),
					e -> e instanceof Townsfolk t && t.ewProfile().community == p.community && e.isAlive()).size() + 1;
			return say("news.people", random, people);
		}
		return say("news.none", random);
	}

	private static Text hireAsk(MobEntity npc, int mood, Random random) {
		if (mood < -20) {
			return say("hire.refuse", random);
		}
		int coins = npc instanceof CityGuardEntity ? Hiring.GUARD_COINS : npc instanceof TraderEntity ? Hiring.MERCHANT_COINS : 0;
		Text cost = coins > 0 ? Text.translatable("dialogue.evolvedwarfare.cost.coins", coins) : Text.translatable("dialogue.evolvedwarfare.cost.contract");
		return Text.translatable(key("hire.ask", 1), cost);
	}

	/** Contratarlo con un contrato del inventario (y monedas). Null si se ha contratado (se cierra la pantalla). */
	@Nullable
	private static Text hire(ServerPlayerEntity player, MobEntity npc, int mood, Random random) {
		if (mood < -20) {
			return say("hire.refuse", random);
		}
		if (!hireable(npc)) {
			return hireAsk(npc, mood, random);
		}
		ItemStack contract = ItemStack.EMPTY;
		for (int i = 0; i < player.getInventory().size(); i++) {
			ItemStack s = player.getInventory().getStack(i);
			if (s.getItem() instanceof RecruitmentContractItem) {
				contract = s;
				break;
			}
		}
		if (contract.isEmpty()) {
			return Text.translatable(key("hire.no_contract", 1));
		}
		MobEntity hired = null;
		if (npc instanceof CityGuardEntity guard) {
			hired = Hiring.hireGuard(guard, player, contract);
		} else if (npc instanceof TraderEntity trader) {
			hired = Hiring.hireTrader(trader, player, contract);
		} else if (npc instanceof AbstractNpcEntity a) {
			contract.decrementUnlessCreative(1, player);
			a.signContract(player);
			hired = a;
		}
		if (hired == null) {
			// no le llegan las monedas (ya se le ha dicho)
			return hireAsk(npc, mood, random);
		}
		like(hired, player, 10);
		Chatter.say(hired, null, say("hire.done", random), (ServerWorld) hired.getWorld());
		return null;
	}

	/** Todas las claves de texto del diálogo (para comprobar que existen). */
	public static List<String> allKeys() {
		List<String> keys = new ArrayList<>();
		for (Map.Entry<String, Integer> e : LINES.entrySet()) {
			for (int n = 1; n <= e.getValue(); n++) {
				keys.add(key(e.getKey(), n));
			}
		}
		for (String k : new String[] {"doing.work", "about.intro", "about.hired", "about.family", "about.alone", "about.friend", "about.enemy",
				"hire.ask", "hire.no_contract", "gift.again"}) {
			keys.add(key(k, 1));
		}
		for (Option o : Option.values()) {
			keys.add("dialogue.evolvedwarfare.option." + o.id());
		}
		for (String m : new String[] {"love", "like", "neutral", "dislike", "hate"}) {
			keys.add("dialogue.evolvedwarfare.mood." + m);
		}
		for (Activity a : Activity.values()) {
			keys.add("dialogue.evolvedwarfare.activity." + a.id());
		}
		for (NpcOrder o : NpcOrder.values()) {
			keys.add("dialogue.evolvedwarfare.activity.order." + o.name().toLowerCase(java.util.Locale.ROOT));
		}
		keys.add("dialogue.evolvedwarfare.cost.coins");
		keys.add("dialogue.evolvedwarfare.cost.contract");
		return keys;
	}
}
