package com.evolvedwarfare.skill;

import com.evolvedwarfare.ClientBridge;
import com.evolvedwarfare.EvolvedWarfare;
import com.evolvedwarfare.config.EwConfig;
import com.evolvedwarfare.network.EngramPayloads;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.RangedWeaponItem;
import net.minecraft.item.ShieldItem;
import net.minecraft.item.ToolItem;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.recipe.RecipeEntry;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * Los engramas (fase 113, como en ARK): casi todo lo que se fabrica pide aprender antes su engrama con los puntos que
 * dan los niveles de superviviente. Al arrancar el servidor (y al recargar los datos) se reparte cada receta en un
 * engrama: primero los objetos que nombra cada uno, luego por sus patrones y etiquetas (el primero que encaja) y, lo
 * que no encaja en ninguno, en los de "otros" por el tipo de objeto. Lo "libre" (mesa de crafteo, horno, tablones,
 * palos, antorchas, materiales) no pide nada; las recetas de otros mods, tampoco (salvo con la opción).
 * <p>
 * Sin el engrama tampoco se puede usar lo que se encuentra: la armadura se cae, las herramientas y armas pegan y pican
 * como la mano y lo que se usa con clic derecho no funciona (los bloques y la comida, sí).
 */
public final class Engrams {
	private static List<Engram> all;
	private static Map<String, Engram> byId;
	private static List<String> categories;
	private static final Set<Identifier> FREE_ITEMS = new LinkedHashSet<>();
	private static final List<Pattern> FREE_PATTERNS = new ArrayList<>();
	private static final List<TagKey<Item>> FREE_TAGS = new ArrayList<>();
	private static Map<Perk, List<Engram>> byPerk;
	/** Qué engrama pide cada objeto (lo calcula el servidor y se lo manda al cliente). */
	private static volatile Map<Item, Engram> byItem = Map.of();
	/** Lo que desbloquea cada engrama (lo mismo, al revés, en orden). */
	private static volatile Map<String, List<Item>> itemsOf = Map.of();
	/** En el cliente: si el servidor los pide. */
	public static boolean clientRequired = true;
	/** Para las pruebas: que se apliquen también en ellas (normalmente no, para no estorbar a las demás pruebas). */
	public static boolean testEnforce;
	private static final Map<UUID, Long> LAST_MESSAGE = new HashMap<>();

	private Engrams() {
	}

	// ------------------------------------------------------------------ la lista

	public static synchronized void load() {
		if (all != null) {
			return;
		}
		List<Engram> list = new ArrayList<>();
		List<String> cats = new ArrayList<>();
		try (InputStream in = Engrams.class.getResourceAsStream("/data/evolvedwarfare/engrams/engrams.json")) {
			if (in == null) {
				throw new IllegalStateException("no está engrams.json");
			}
			JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
			JsonObject free = root.getAsJsonObject("free");
			for (String s : strings(free, "items")) {
				FREE_ITEMS.add(Identifier.of(s));
			}
			for (String s : strings(free, "patterns")) {
				FREE_PATTERNS.add(Pattern.compile(s));
			}
			for (String s : strings(free, "tags")) {
				FREE_TAGS.add(TagKey.of(RegistryKeys.ITEM, Identifier.of(s)));
			}
			cats.addAll(strings(root, "categories"));
			for (JsonElement el : root.getAsJsonArray("engrams")) {
				JsonObject o = el.getAsJsonObject();
				List<Identifier> items = new ArrayList<>();
				for (String s : strings(o, "items")) {
					items.add(Identifier.of(s));
				}
				List<Pattern> patterns = new ArrayList<>();
				for (String s : strings(o, "patterns")) {
					patterns.add(Pattern.compile(s));
				}
				List<Identifier> tags = new ArrayList<>();
				for (String s : strings(o, "tags")) {
					tags.add(Identifier.of(s));
				}
				Perk perk = o.has("perk") ? Perk.valueOf(o.get("perk").getAsString()) : null;
				list.add(new Engram(o.get("id").getAsString(), o.get("category").getAsString(), o.get("level").getAsInt(),
						o.get("cost").getAsInt(), o.has("icon") ? Identifier.of(o.get("icon").getAsString()) : null, List.copyOf(strings(o, "requires")),
						List.copyOf(items), List.copyOf(patterns), List.copyOf(tags), perk, o.has("perkRank") ? o.get("perkRank").getAsInt() : 1,
						o.has("fallback") ? o.get("fallback").getAsString() : null, o.has("namespace") ? o.get("namespace").getAsString() : null));
			}
		} catch (Exception e) {
			EvolvedWarfare.LOGGER.error("No se han podido leer los engramas", e);
		}
		Map<String, Engram> ids = new LinkedHashMap<>();
		Map<Perk, List<Engram>> perks = new EnumMap<>(Perk.class);
		for (Engram e : list) {
			ids.put(e.id, e);
			if (e.perk != null) {
				perks.computeIfAbsent(e.perk, k -> new ArrayList<>()).add(e);
			}
		}
		byId = ids;
		byPerk = perks;
		categories = List.copyOf(cats);
		all = List.copyOf(list);
	}

	private static List<String> strings(JsonObject o, String key) {
		List<String> out = new ArrayList<>();
		if (o.has(key)) {
			JsonArray arr = o.getAsJsonArray(key);
			for (JsonElement e : arr) {
				out.add(e.getAsString());
			}
		}
		return out;
	}

	public static List<Engram> all() {
		load();
		return all;
	}

	public static List<String> categories() {
		load();
		return categories;
	}

	@Nullable
	public static Engram byId(String id) {
		load();
		return byId.get(id);
	}

	/** El engrama que hace falta para fabricar o usar esto (null: no pide ninguno). */
	@Nullable
	public static Engram of(Item item) {
		return byItem.get(item);
	}

	public static List<Item> itemsOf(Engram engram) {
		return itemsOf.getOrDefault(engram.id, List.of());
	}

	/** Cuántos objetos tienen engrama (para las pruebas y el registro). */
	public static int lockedItemCount() {
		return byItem.size();
	}

	// ------------------------------------------------------------------ repartir las recetas

	public static boolean isFree(Item item) {
		load();
		Identifier id = Registries.ITEM.getId(item);
		if (FREE_ITEMS.contains(id)) {
			return true;
		}
		String s = id.toString();
		for (Pattern p : FREE_PATTERNS) {
			if (p.matcher(s).find()) {
				return true;
			}
		}
		ItemStack stack = new ItemStack(item);
		for (TagKey<Item> tag : FREE_TAGS) {
			if (stack.isIn(tag)) {
				return true;
			}
		}
		return false;
	}

	private static boolean matches(Engram e, Item item, String id) {
		for (Pattern p : e.patterns) {
			if (p.matcher(id).find()) {
				return true;
			}
		}
		if (!e.tags.isEmpty()) {
			ItemStack stack = new ItemStack(item);
			for (Identifier tag : e.tags) {
				if (stack.isIn(TagKey.of(RegistryKeys.ITEM, tag))) {
					return true;
				}
			}
		}
		return false;
	}

	private static boolean fallbackFits(Engram e, Item item, String namespace) {
		if (e.namespace != null && !e.namespace.equals(namespace)
				&& !("minecraft".equals(e.namespace) && !"minecraft".equals(namespace) && !EvolvedWarfare.MOD_ID.equals(namespace))) {
			return false;
		}
		ItemStack stack = new ItemStack(item);
		return switch (e.fallback) {
			case "armor" -> item instanceof ArmorItem;
			case "tool" -> item instanceof ToolItem || item instanceof RangedWeaponItem || item instanceof ShieldItem;
			case "food" -> stack.contains(DataComponentTypes.FOOD);
			case "block" -> item instanceof BlockItem;
			default -> true;
		};
	}

	/** Reparte lo que dan las recetas del servidor en los engramas. */
	public static void classify(MinecraftServer server) {
		load();
		Map<Item, Engram> map = new LinkedHashMap<>();
		// lo que nombra cada engrama (aunque no tenga receta, como lo que se forja)
		for (Engram e : all) {
			for (Identifier id : e.items) {
				if (Registries.ITEM.containsId(id)) {
					map.putIfAbsent(Registries.ITEM.get(id), e);
				}
			}
			if (e.perk != null) {
				for (Item item : e.perk.unlocks()) {
					map.putIfAbsent(item, e);
				}
			}
		}
		Set<Item> outputs = new LinkedHashSet<>();
		for (RecipeEntry<?> recipe : server.getRecipeManager().values()) {
			try {
				ItemStack result = recipe.value().getResult(server.getRegistryManager());
				if (!result.isEmpty()) {
					outputs.add(result.getItem());
				}
			} catch (RuntimeException ignored) {
				// alguna receta especial sin resultado fijo
			}
		}
		boolean others = EwConfig.get().engramsOtherMods;
		for (Item item : outputs) {
			if (map.containsKey(item) || isFree(item)) {
				continue;
			}
			Identifier id = Registries.ITEM.getId(item);
			String ns = id.getNamespace();
			if (!others && !"minecraft".equals(ns) && !EvolvedWarfare.MOD_ID.equals(ns)) {
				continue;
			}
			String full = id.toString();
			Engram found = null;
			for (Engram e : all) {
				if (e.fallback == null && matches(e, item, full)) {
					found = e;
					break;
				}
			}
			if (found == null) {
				for (Engram e : all) {
					if (e.fallback != null && fallbackFits(e, item, ns)) {
						found = e;
						break;
					}
				}
			}
			if (found != null) {
				map.put(item, found);
			}
		}
		// lo del juego y del mod que no sale de recetas (lo que se forja, como el acero) pero encaja en un patrón: así
		// tampoco se puede usar sin su engrama si se encuentra
		for (Item item : Registries.ITEM) {
			if (map.containsKey(item)) {
				continue;
			}
			Identifier id = Registries.ITEM.getId(item);
			if (!"minecraft".equals(id.getNamespace()) && !EvolvedWarfare.MOD_ID.equals(id.getNamespace())) {
				continue;
			}
			if (item instanceof BlockItem || isFree(item)) {
				continue;
			}
			String full = id.toString();
			for (Engram e : all) {
				if (e.fallback == null && !e.patterns.isEmpty() && matches(e, item, full)) {
					map.put(item, e);
					break;
				}
			}
		}
		// lo libre manda aunque algún engrama lo nombre
		map.keySet().removeIf(Engrams::isFree);
		Map<String, List<Item>> lists = new LinkedHashMap<>();
		for (Map.Entry<Item, Engram> e : map.entrySet()) {
			lists.computeIfAbsent(e.getValue().id, k -> new ArrayList<>()).add(e.getKey());
		}
		byItem = Collections.unmodifiableMap(map);
		itemsOf = Collections.unmodifiableMap(lists);
		EvolvedWarfare.LOGGER.info("Engramas: {} engramas, {} objetos con engrama de {} que salen de recetas", all.size(), map.size(), outputs.size());
	}

	// ------------------------------------------------------------------ red

	public static void register() {
		load();
		PayloadTypeRegistry.playS2C().register(EngramPayloads.Catalog.ID, EngramPayloads.Catalog.CODEC);
		PayloadTypeRegistry.playC2S().register(EngramPayloads.Learn.ID, EngramPayloads.Learn.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(EngramPayloads.Learn.ID, (payload, context) -> learn(context.player(), payload.engram()));
		ServerLifecycleEvents.SERVER_STARTED.register(Engrams::classify);
		ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resources, success) -> {
			classify(server);
			for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
				sendCatalog(player);
			}
		});
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> sendCatalog(handler.getPlayer()));
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTicks() % 10 != 0) {
				return;
			}
			for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
				checkArmor(player);
			}
		});
	}

	public static void sendCatalog(ServerPlayerEntity player) {
		if (!ServerPlayNetworking.canSend(player, EngramPayloads.Catalog.ID)) {
			return;
		}
		NbtCompound nbt = new NbtCompound();
		nbt.putBoolean("Required", EwConfig.get().researchRequired);
		NbtCompound items = new NbtCompound();
		for (Map.Entry<String, List<Item>> e : itemsOf.entrySet()) {
			NbtList list = new NbtList();
			for (Item item : e.getValue()) {
				list.add(NbtString.of(Registries.ITEM.getId(item).toString()));
			}
			items.put(e.getKey(), list);
		}
		nbt.put("Items", items);
		ServerPlayNetworking.send(player, new EngramPayloads.Catalog(nbt));
	}

	/** En el cliente: lo que manda el servidor. */
	public static void readCatalog(NbtCompound nbt) {
		load();
		clientRequired = nbt.getBoolean("Required");
		Map<Item, Engram> map = new LinkedHashMap<>();
		Map<String, List<Item>> lists = new LinkedHashMap<>();
		NbtCompound items = nbt.getCompound("Items");
		for (String key : items.getKeys()) {
			Engram e = byId.get(key);
			if (e == null) {
				continue;
			}
			List<Item> list = new ArrayList<>();
			for (NbtElement el : items.getList(key, NbtElement.STRING_TYPE)) {
				Identifier id = Identifier.tryParse(el.asString());
				if (id != null && Registries.ITEM.containsId(id)) {
					Item item = Registries.ITEM.get(id);
					map.put(item, e);
					list.add(item);
				}
			}
			lists.put(key, list);
		}
		byItem = Collections.unmodifiableMap(map);
		itemsOf = Collections.unmodifiableMap(lists);
	}

	// ------------------------------------------------------------------ aprender

	public enum Learn {
		OK, UNKNOWN, LEARNED, LEVEL, REQUIRES, POINTS
	}

	public static boolean learned(SkillData data, Engram engram) {
		return data.engrams.contains(engram.id);
	}

	/** Si se puede aprender (sin mirar los puntos si 'free'). */
	public static Learn check(SkillData data, @Nullable Engram engram, boolean free) {
		if (engram == null) {
			return Learn.UNKNOWN;
		}
		if (learned(data, engram)) {
			return Learn.LEARNED;
		}
		if (!free && data.level < engram.level) {
			return Learn.LEVEL;
		}
		for (String req : engram.requires) {
			if (!data.engrams.contains(req)) {
				return Learn.REQUIRES;
			}
		}
		if (!free && data.engramPoints < engram.cost) {
			return Learn.POINTS;
		}
		return Learn.OK;
	}

	public static Learn learn(ServerPlayerEntity player, String id) {
		if (player.getServer() == null) {
			return Learn.UNKNOWN;
		}
		SkillState state = SkillState.get(player.getServer());
		SkillData data = state.of(player.getUuid());
		Engram engram = byId(id);
		boolean free = player.getAbilities().creativeMode;
		Learn result = check(data, engram, free);
		if (result == Learn.OK) {
			data.engrams.add(engram.id);
			if (!free) {
				data.engramPoints -= engram.cost;
			}
			state.markDirty();
			player.playSoundToPlayer(SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 0.7F, 1.4F);
			player.sendMessage(Text.translatable("message.evolvedwarfare.engrams.learned", Text.translatable(engram.translationKey()))
					.formatted(Formatting.AQUA), true);
			if (engram.perk != null) {
				SkillEffects.refresh(player);
			}
		} else {
			player.playSoundToPlayer(SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(), SoundCategory.PLAYERS, 0.6F, 0.6F);
		}
		Skills.sync(player, false);
		return result;
	}

	/** Aprende sin pedir nada (comandos, pruebas, lo que ya sabía forjar). */
	public static void grant(ServerPlayerEntity player, String id) {
		Engram engram = byId(id);
		if (engram == null || player.getServer() == null) {
			return;
		}
		SkillState state = SkillState.get(player.getServer());
		state.of(player.getUuid()).engrams.add(engram.id);
		state.markDirty();
		SkillEffects.refresh(player);
		Skills.sync(player, false);
	}

	/** Olvida todos y devuelve sus puntos (comando). */
	public static void reset(ServerPlayerEntity player) {
		SkillState state = SkillState.get(player.getServer());
		SkillData data = state.of(player.getUuid());
		for (String id : data.engrams) {
			Engram e = byId(id);
			if (e != null) {
				data.engramPoints += e.cost;
			}
		}
		data.engrams.clear();
		state.markDirty();
		SkillEffects.refresh(player);
		Skills.sync(player, false);
	}

	/** El rango que da de una ventaja de antes (forja, mando, asedio); las de combate ya no existen (0). */
	public static int perkRank(SkillData data, Perk perk) {
		load();
		List<Engram> list = byPerk.get(perk);
		if (list == null) {
			return 0;
		}
		int best = 0;
		for (Engram e : list) {
			if (data.engrams.contains(e.id)) {
				best = Math.max(best, Math.min(perk.ranks(), e.perkRank));
			}
		}
		return best;
	}

	/**
	 * Los que ya jugaban (fase 113): los 10 puntos del principio y, lo que ya sabían forjar o mandar con las ventajas de
	 * antes, como engramas (sin gastar puntos).
	 */
	public static List<Engram> migrate(SkillData data) {
		load();
		List<Engram> given = new ArrayList<>();
		if (data.engramStart) {
			return given;
		}
		data.engramStart = true;
		data.engramPoints += 10;
		for (Map.Entry<Perk, Integer> e : data.perks.entrySet()) {
			if (e.getValue() <= 0) {
				continue;
			}
			for (Engram engram : byPerk.getOrDefault(e.getKey(), List.of())) {
				if (data.engrams.add(engram.id)) {
					given.add(engram);
				}
			}
		}
		// lo que pide lo que ya tenía, también (si no, no tendría sentido)
		boolean added = true;
		while (added) {
			added = false;
			for (Engram engram : List.copyOf(given)) {
				for (String req : engram.requires) {
					Engram r = byId.get(req);
					if (r != null && data.engrams.add(r.id)) {
						given.add(r);
						added = true;
					}
				}
			}
		}
		return given;
	}

	public static Text names(List<Engram> list) {
		MutableText text = Text.empty();
		for (int i = 0; i < list.size(); i++) {
			if (i > 0) {
				text.append(", ");
			}
			text.append(Text.translatable(list.get(i).translationKey()));
		}
		return text;
	}

	// ------------------------------------------------------------------ fabricar y usar

	/** Si se aplican a este jugador (no en creativo ni espectador, ni con la opción quitada). */
	public static boolean enforced(PlayerEntity player) {
		if (player.getAbilities().creativeMode || player.isSpectator()) {
			return false;
		}
		if (System.getProperty("fabric-api.gametest") != null && !testEnforce) {
			return false;
		}
		return player.getWorld().isClient ? clientRequired : EwConfig.get().researchRequired;
	}

	public static boolean knows(PlayerEntity player, Engram engram) {
		if (player.getWorld().isClient) {
			return ClientBridge.clientEngramLearned.test(player, engram.id);
		}
		if (player.getServer() == null) {
			return true;
		}
		SkillData data = SkillState.get(player.getServer()).peek(player.getUuid());
		return data != null && data.engrams.contains(engram.id);
	}

	/** El engrama que le falta para esto (o null si no le falta ninguno). */
	@Nullable
	public static Engram missing(PlayerEntity player, ItemStack stack) {
		if (stack.isEmpty() || !enforced(player)) {
			return null;
		}
		Engram engram = of(stack.getItem());
		return engram == null || knows(player, engram) ? null : engram;
	}

	public static boolean canCraft(PlayerEntity player, ItemStack result) {
		return missing(player, result) == null;
	}

	/** Si puede usar esto con clic derecho, ponérselo o pegar y picar con ello (los bloques y la comida, siempre). */
	public static boolean canUse(PlayerEntity player, ItemStack stack) {
		if (stack.isEmpty() || stack.getItem() instanceof BlockItem || stack.contains(DataComponentTypes.FOOD)) {
			return true;
		}
		return missing(player, stack) == null;
	}

	/** Lleva en la mano algo que no sabe usar (pega y pica como con la mano). */
	public static boolean heldLocked(PlayerEntity player) {
		return !canUse(player, player.getMainHandStack());
	}

	/** Golpe cuerpo a cuerpo con algo que no sabe usar: como un puñetazo. */
	public static float modifyMelee(DamageSource source, float amount) {
		if (source.getAttacker() instanceof PlayerEntity player && source.getSource() == player && heldLocked(player)) {
			return Math.min(amount, 1.0F);
		}
		return amount;
	}

	/** Le avisa de lo que le falta (en la barra de acción, como mucho cada segundo). */
	public static void warn(PlayerEntity player, Engram engram, String what) {
		if (!(player instanceof ServerPlayerEntity sp)) {
			return;
		}
		long now = sp.getServerWorld().getTime();
		Long last = LAST_MESSAGE.get(sp.getUuid());
		if (last != null && now - last < 20L) {
			return;
		}
		LAST_MESSAGE.put(sp.getUuid(), now);
		sp.sendMessage(Text.translatable("message.evolvedwarfare.engrams.locked_" + what, Text.translatable(engram.translationKey()), engram.level)
				.formatted(Formatting.RED), true);
	}

	/** La armadura que no sabe usar se cae (al inventario, o al suelo si no cabe). */
	public static void checkArmor(ServerPlayerEntity player) {
		if (!player.isAlive() || !enforced(player)) {
			return;
		}
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			if (slot.getType() != EquipmentSlot.Type.HUMANOID_ARMOR) {
				continue;
			}
			ItemStack stack = player.getEquippedStack(slot);
			Engram engram = stack.isEmpty() ? null : missing(player, stack);
			if (engram == null) {
				continue;
			}
			ItemStack copy = stack.copy();
			player.equipStack(slot, ItemStack.EMPTY);
			if (!player.getInventory().insertStack(copy)) {
				player.dropItem(copy, false);
			}
			player.sendMessage(Text.translatable("message.evolvedwarfare.engrams.locked_armor", copy.getName(),
					Text.translatable(engram.translationKey()), engram.level).formatted(Formatting.RED), true);
		}
	}
}
