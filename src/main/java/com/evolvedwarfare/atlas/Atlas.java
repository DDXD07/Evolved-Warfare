package com.evolvedwarfare.atlas;

import com.evolvedwarfare.EvolvedWarfare;
import com.evolvedwarfare.config.EwConfig;
import com.evolvedwarfare.logistics.Market;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.BlockState;
import net.minecraft.block.MapColor;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.structure.StructureStart;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.world.gen.structure.Structure;
import org.jetbrains.annotations.Nullable;

/**
 * El Atlas (fase 53): un libro con el mapa del mundo normal, en tinta sobre papel (como los catálogos de los comerciantes),
 * que se va dibujando según explora cada jugador (lo de cada uno, solo suyo) y se actualiza en tiempo real cerca de él:
 * el relieve con sus curvas de nivel, el agua rayada, lo construido... Salen las estructuras vistas (ciudades con su
 * nombre, asentamientos, castillos, aldeas, templos...) y los jugadores. Desde el Atlas se reclaman territorios por chunks
 * (pegados entre sí, a {@code atlasClaimCost} Glory Coins el chunk, hasta {@code atlasMaxChunks} por jugador): se ven
 * resaltados en el mapa de todos, con su nombre. Solo es una marca: no protege nada.
 */
public final class Atlas {
	/** Cuántos chunks alrededor se exploran (radio). */
	public static final int EXPLORE_RADIUS = 5;
	/** Cuántos alrededor se vuelven a dibujar cada poco (lo que cambia se ve en tiempo real). */
	public static final int LIVE_RADIUS = 2;
	private static final int SCANS_PER_TICK = 16, SENDS_PER_TICK = 96;
	private static final Set<String> VANILLA = Set.of("village_plains", "village_desert", "village_savanna", "village_snowy", "village_taiga",
			"desert_pyramid", "jungle_pyramid", "swamp_hut", "igloo", "mansion", "monument", "pillager_outpost", "ancient_city", "trail_ruins",
			"ocean_ruin_cold", "ocean_ruin_warm", "shipwreck", "shipwreck_beached", "trial_chambers");
	private static final int[] COLORS = {0xD03A2E, 0x2E6FD0, 0x2E9E4A, 0xC77A1A, 0x8A3AC4, 0x1A9EA0, 0xC43A8A, 0x6E6E20};

	public static final Item ATLAS = net.minecraft.registry.Registry.register(Registries.ITEM, EvolvedWarfare.id("atlas"),
			new AtlasItem(new Item.Settings().maxCount(1)));

	/** Para abrir el Atlas en el cliente (lo pone el cliente). */
	public static Runnable openScreen = () -> {
	};

	private static final LongLinkedOpenHashSet SCAN = new LongLinkedOpenHashSet();
	private static final Map<UUID, LongArrayFIFOQueue> SEND = new HashMap<>();
	private static final Set<UUID> VIEWERS = new HashSet<>();

	private Atlas() {
	}

	public static class AtlasItem extends Item {
		public AtlasItem(Settings settings) {
			super(settings);
		}

		@Override
		public TypedActionResult<ItemStack> use(World world, PlayerEntity player, Hand hand) {
			if (world.isClient) {
				openScreen.run();
			} else {
				world.playSound(null, player.getBlockPos(), SoundEvents.ITEM_BOOK_PAGE_TURN, SoundCategory.PLAYERS, 1.0F, 1.0F);
			}
			return TypedActionResult.success(player.getStackInHand(hand), world.isClient);
		}

		@Override
		public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, net.minecraft.item.tooltip.TooltipType type) {
			tooltip.add(Text.translatable("item.evolvedwarfare.atlas.desc").formatted(Formatting.GRAY));
		}
	}

	// ------------------------------------------------------------------ lo que va por la red

	/** Cómo se ven unos chunks (16 x 16 tonos cada uno). */
	public record ChunksPayload(long[] keys, byte[][] images) implements CustomPayload {
		public static final Id<ChunksPayload> ID = new Id<>(EvolvedWarfare.id("atlas_chunks"));
		public static final PacketCodec<RegistryByteBuf, ChunksPayload> CODEC = PacketCodec.of(ChunksPayload::write, ChunksPayload::read);

		void write(RegistryByteBuf buf) {
			buf.writeVarInt(keys.length);
			for (int i = 0; i < keys.length; i++) {
				buf.writeLong(keys[i]);
				buf.writeBytes(images[i]);
			}
		}

		static ChunksPayload read(RegistryByteBuf buf) {
			int n = buf.readVarInt();
			long[] k = new long[n];
			byte[][] im = new byte[n][];
			for (int i = 0; i < n; i++) {
				k[i] = buf.readLong();
				im[i] = new byte[256];
				buf.readBytes(im[i]);
			}
			return new ChunksPayload(k, im);
		}

		@Override
		public Id<? extends CustomPayload> getId() {
			return ID;
		}
	}

	/** Las estructuras vistas (replace: es la lista entera). */
	public record MarkersPayload(boolean replace, List<AtlasState.Marker> markers) implements CustomPayload {
		public static final Id<MarkersPayload> ID = new Id<>(EvolvedWarfare.id("atlas_markers"));
		public static final PacketCodec<RegistryByteBuf, MarkersPayload> CODEC = PacketCodec.of(MarkersPayload::write, MarkersPayload::read);

		void write(RegistryByteBuf buf) {
			buf.writeBoolean(replace);
			buf.writeVarInt(markers.size());
			for (AtlasState.Marker m : markers) {
				buf.writeLong(m.key());
				buf.writeString(m.id());
				buf.writeInt(m.x());
				buf.writeInt(m.z());
				buf.writeString(m.name());
			}
		}

		static MarkersPayload read(RegistryByteBuf buf) {
			boolean replace = buf.readBoolean();
			int n = buf.readVarInt();
			List<AtlasState.Marker> list = new ArrayList<>();
			for (int i = 0; i < n; i++) {
				list.add(new AtlasState.Marker(buf.readLong(), buf.readString(), buf.readInt(), buf.readInt(), buf.readString()));
			}
			return new MarkersPayload(replace, list);
		}

		@Override
		public Id<? extends CustomPayload> getId() {
			return ID;
		}
	}

	/** Un territorio, como lo ve el cliente. */
	public record TerritoryView(int id, String name, UUID owner, String ownerName, int color, long[] chunks) {
	}

	public record TerritoriesPayload(List<TerritoryView> territories) implements CustomPayload {
		public static final Id<TerritoriesPayload> ID = new Id<>(EvolvedWarfare.id("atlas_territories"));
		public static final PacketCodec<RegistryByteBuf, TerritoriesPayload> CODEC = PacketCodec.of(TerritoriesPayload::write, TerritoriesPayload::read);

		void write(RegistryByteBuf buf) {
			buf.writeVarInt(territories.size());
			for (TerritoryView t : territories) {
				buf.writeVarInt(t.id());
				buf.writeString(t.name());
				buf.writeUuid(t.owner());
				buf.writeString(t.ownerName());
				buf.writeInt(t.color());
				buf.writeLongArray(t.chunks());
			}
		}

		static TerritoriesPayload read(RegistryByteBuf buf) {
			int n = buf.readVarInt();
			List<TerritoryView> list = new ArrayList<>();
			for (int i = 0; i < n; i++) {
				list.add(new TerritoryView(buf.readVarInt(), buf.readString(), buf.readUuid(), buf.readString(), buf.readInt(), buf.readLongArray()));
			}
			return new TerritoriesPayload(list);
		}

		@Override
		public Id<? extends CustomPayload> getId() {
			return ID;
		}
	}

	/** Dónde están los jugadores (para quien tiene el Atlas abierto). */
	public record PlayerDot(UUID id, String name, double x, double z, float yaw) {
	}

	public record PlayersPayload(List<PlayerDot> players) implements CustomPayload {
		public static final Id<PlayersPayload> ID = new Id<>(EvolvedWarfare.id("atlas_players"));
		public static final PacketCodec<RegistryByteBuf, PlayersPayload> CODEC = PacketCodec.of(PlayersPayload::write, PlayersPayload::read);

		void write(RegistryByteBuf buf) {
			buf.writeVarInt(players.size());
			for (PlayerDot p : players) {
				buf.writeUuid(p.id());
				buf.writeString(p.name());
				buf.writeDouble(p.x());
				buf.writeDouble(p.z());
				buf.writeFloat(p.yaw());
			}
		}

		static PlayersPayload read(RegistryByteBuf buf) {
			int n = buf.readVarInt();
			List<PlayerDot> list = new ArrayList<>();
			for (int i = 0; i < n; i++) {
				list.add(new PlayerDot(buf.readUuid(), buf.readString(), buf.readDouble(), buf.readDouble(), buf.readFloat()));
			}
			return new PlayersPayload(list);
		}

		@Override
		public Id<? extends CustomPayload> getId() {
			return ID;
		}
	}

	/** El cliente abre o cierra el Atlas. */
	public record ViewPayload(boolean open) implements CustomPayload {
		public static final Id<ViewPayload> ID = new Id<>(EvolvedWarfare.id("atlas_view"));
		public static final PacketCodec<RegistryByteBuf, ViewPayload> CODEC = PacketCodec.of((p, buf) -> buf.writeBoolean(p.open()),
				buf -> new ViewPayload(buf.readBoolean()));

		@Override
		public Id<? extends CustomPayload> getId() {
			return ID;
		}
	}

	/** Reclamar esos chunks para el territorio de ese nombre (o, remove, dejar de reclamarlos). */
	public record ClaimPayload(String name, long[] chunks, boolean remove) implements CustomPayload {
		public static final Id<ClaimPayload> ID = new Id<>(EvolvedWarfare.id("atlas_claim"));
		public static final PacketCodec<RegistryByteBuf, ClaimPayload> CODEC = PacketCodec.of((p, buf) -> {
			buf.writeString(p.name(), 64);
			buf.writeLongArray(p.chunks());
			buf.writeBoolean(p.remove());
		}, buf -> new ClaimPayload(buf.readString(64), buf.readLongArray(), buf.readBoolean()));

		@Override
		public Id<? extends CustomPayload> getId() {
			return ID;
		}
	}

	public static void register() {
		PayloadTypeRegistry.playS2C().register(ChunksPayload.ID, ChunksPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(MarkersPayload.ID, MarkersPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(TerritoriesPayload.ID, TerritoriesPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(PlayersPayload.ID, PlayersPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(ViewPayload.ID, ViewPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(ClaimPayload.ID, ClaimPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(ViewPayload.ID, (p, ctx) -> {
			if (p.open()) {
				VIEWERS.add(ctx.player().getUuid());
				sendPlayers(ctx.player());
			} else {
				VIEWERS.remove(ctx.player().getUuid());
			}
		});
		ServerPlayNetworking.registerGlobalReceiver(ClaimPayload.ID, (p, ctx) -> {
			if (p.remove()) {
				unclaim(ctx.player(), p.chunks());
			} else {
				claim(ctx.player(), p.name(), p.chunks());
			}
		});
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> join(handler.player));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			SEND.remove(handler.player.getUuid());
			VIEWERS.remove(handler.player.getUuid());
		});
		ServerTickEvents.END_WORLD_TICK.register(Atlas::tick);
	}

	// ------------------------------------------------------------------ explorar y dibujar

	private static void join(ServerPlayerEntity player) {
		AtlasState state = AtlasState.get(player.getServer());
		LongArrayFIFOQueue q = new LongArrayFIFOQueue();
		for (long k : state.explored(player.getUuid())) {
			if (state.images.containsKey(k)) {
				q.enqueue(k);
			}
		}
		SEND.put(player.getUuid(), q);
		ServerPlayNetworking.send(player, new MarkersPayload(true, new ArrayList<>(state.markers.values())));
		ServerPlayNetworking.send(player, territoriesPayload(state));
	}

	private static void tick(ServerWorld world) {
		if (world.getRegistryKey() != World.OVERWORLD) {
			return;
		}
		AtlasState state = AtlasState.get(world.getServer());
		long time = world.getTime();
		for (ServerPlayerEntity player : world.getPlayers()) {
			if (player.isSpectator()) {
				continue;
			}
			// cada jugador va explorando (cada uno lo suyo) y lo de su alrededor se vuelve a dibujar cada poco
			if ((time + player.getId()) % 20 == 0) {
				explore(world, state, player);
			}
			if ((time + player.getId()) % 100 == 0) {
				ChunkPos c = player.getChunkPos();
				for (int dx = -LIVE_RADIUS; dx <= LIVE_RADIUS; dx++) {
					for (int dz = -LIVE_RADIUS; dz <= LIVE_RADIUS; dz++) {
						SCAN.add(ChunkPos.toLong(c.x + dx, c.z + dz));
					}
				}
			}
		}
		for (int i = 0; i < SCANS_PER_TICK && !SCAN.isEmpty(); i++) {
			long k = SCAN.removeFirstLong();
			WorldChunk chunk = world.getChunkManager().getWorldChunk(ChunkPos.getPackedX(k), ChunkPos.getPackedZ(k));
			if (chunk == null) {
				continue;
			}
			byte[] img = scan(world, chunk);
			byte[] old = state.images.get(k);
			discover(world, state, chunk);
			if (old == null || !Arrays.equals(old, img)) {
				state.images.put(k, img);
				state.markDirty();
				for (ServerPlayerEntity p : world.getPlayers()) {
					if (state.explored(p.getUuid()).contains(k)) {
						SEND.computeIfAbsent(p.getUuid(), u -> new LongArrayFIFOQueue()).enqueue(k);
					}
				}
			}
		}
		// lo que hay que mandar a cada uno, en paquetes
		for (ServerPlayerEntity p : world.getPlayers()) {
			LongArrayFIFOQueue q = SEND.get(p.getUuid());
			if (q == null || q.isEmpty()) {
				continue;
			}
			LongArrayList keys = new LongArrayList();
			List<byte[]> imgs = new ArrayList<>();
			while (!q.isEmpty() && keys.size() < SENDS_PER_TICK) {
				long k = q.dequeueLong();
				byte[] img = state.images.get(k);
				if (img != null) {
					keys.add(k);
					imgs.add(img);
				}
			}
			if (!keys.isEmpty()) {
				ServerPlayNetworking.send(p, new ChunksPayload(keys.toLongArray(), imgs.toArray(new byte[0][])));
			}
		}
		if (time % 10 == 0 && !VIEWERS.isEmpty()) {
			for (ServerPlayerEntity p : world.getServer().getPlayerManager().getPlayerList()) {
				if (VIEWERS.contains(p.getUuid())) {
					sendPlayers(p);
				}
			}
		}
	}

	private static void explore(ServerWorld world, AtlasState state, ServerPlayerEntity player) {
		LongOpenHashSet mine = state.explored(player.getUuid());
		ChunkPos c = player.getChunkPos();
		int r = EXPLORE_RADIUS;
		for (int dx = -r; dx <= r; dx++) {
			for (int dz = -r; dz <= r; dz++) {
				if (dx * dx + dz * dz > r * r + 1) {
					continue;
				}
				int x = c.x + dx, z = c.z + dz;
				long k = ChunkPos.toLong(x, z);
				if (mine.contains(k) || world.getChunkManager().getWorldChunk(x, z) == null) {
					continue;
				}
				mine.add(k);
				state.markDirty();
				if (state.images.containsKey(k)) {
					SEND.computeIfAbsent(player.getUuid(), u -> new LongArrayFIFOQueue()).enqueue(k);
				} else {
					SCAN.add(k);
				}
			}
		}
	}

	/** La altura de lo alto de una columna (de ese chunk), o null si el chunk no está cargado. */
	private static int top(WorldChunk chunk, int lx, int lz) {
		return chunk.sampleHeightmap(Heightmap.Type.WORLD_SURFACE, lx, lz);
	}

	/**
	 * Cómo se ve un chunk en el Atlas: 16 x 16 tonos (0 tinta, 255 papel). Lo claro u oscuro de cada sitio (por su color de
	 * mapa), el relieve (las laderas al norte, más claras; al sur, más oscuras), curvas de nivel cada 8 bloques, y el agua
	 * más oscura cuanto más honda, con rayas.
	 */
	public static byte[] scan(ServerWorld world, WorldChunk chunk) {
		byte[] out = new byte[256];
		ChunkPos cp = chunk.getPos();
		WorldChunk north = world.getChunkManager().getWorldChunk(cp.x, cp.z - 1);
		BlockPos.Mutable p = new BlockPos.Mutable();
		int bottom = world.getBottomY();
		int[] heights = new int[256];
		for (int lz = 0; lz < 16; lz++) {
			for (int lx = 0; lx < 16; lx++) {
				heights[lz * 16 + lx] = top(chunk, lx, lz);
			}
		}
		for (int lz = 0; lz < 16; lz++) {
			for (int lx = 0; lx < 16; lx++) {
				int h = heights[lz * 16 + lx];
				int hn = lz > 0 ? heights[(lz - 1) * 16 + lx] : north != null ? top(north, lx, 15) : h;
				int hw = lx > 0 ? heights[lz * 16 + lx - 1] : h;
				int wx = cp.getStartX() + lx, wz = cp.getStartZ() + lz;
				int y = h;
				p.set(wx, y, wz);
				BlockState s = chunk.getBlockState(p);
				int depth = 0;
				while (depth < 14 && y > bottom && s.getFluidState().isIn(FluidTags.WATER)) {
					y--;
					depth++;
					s = chunk.getBlockState(p.setY(y));
				}
				int shade;
				if (depth > 0) {
					shade = 200 - depth * 7;
					if (Math.floorMod(wz + (wx >> 2), 4) == 0) {
						shade -= 28;
					}
				} else {
					MapColor mc = s.getMapColor(chunk, p);
					for (int k = 0; k < 3 && mc == MapColor.CLEAR && y > bottom; k++) {
						y--;
						s = chunk.getBlockState(p.setY(y));
						mc = s.getMapColor(chunk, p);
					}
					int rgb = mc == MapColor.CLEAR ? 0xFFFFFF : mc.color;
					double lum = 0.3 * (rgb >> 16 & 255) + 0.59 * (rgb >> 8 & 255) + 0.11 * (rgb & 255);
					shade = (int) (118 + lum * 0.47);
					shade += MathHelper.clamp((hn - h) * -13, -55, 30);
					if (Math.floorDiv(hn, 8) != Math.floorDiv(h, 8) || Math.floorDiv(hw, 8) != Math.floorDiv(h, 8)) {
						shade -= 45;
					}
				}
				out[lz * 16 + lx] = (byte) MathHelper.clamp(shade, 0, 255);
			}
		}
		return out;
	}

	private static boolean shown(Identifier id) {
		return id.getNamespace().equals(EvolvedWarfare.MOD_ID) || id.getNamespace().equals("minecraft") && VANILLA.contains(id.getPath());
	}

	/** Las estructuras que tocan ese chunk: las que aún no estaban, se apuntan (y se mandan a todos). */
	private static void discover(ServerWorld world, AtlasState state, WorldChunk chunk) {
		Registry<Structure> registry = world.getRegistryManager().get(RegistryKeys.STRUCTURE);
		List<AtlasState.Marker> found = new ArrayList<>();
		for (StructureStart start : world.getStructureAccessor().getStructureStarts(chunk.getPos(), s -> true)) {
			if (!start.hasChildren()) {
				continue;
			}
			Identifier id = registry.getId(start.getStructure());
			if (id == null || !shown(id)) {
				continue;
			}
			long key = start.getPos().toLong() * 31L + id.hashCode();
			if (state.markers.containsKey(key)) {
				continue;
			}
			BlockBox bb = start.getBoundingBox();
			String name = com.evolvedwarfare.world.city.CityPiece.of(start).map(com.evolvedwarfare.world.city.CityPiece::cityName).orElse("");
			AtlasState.Marker m = new AtlasState.Marker(key, id.toString(), (bb.getMinX() + bb.getMaxX()) / 2, (bb.getMinZ() + bb.getMaxZ()) / 2, name);
			state.markers.put(key, m);
			found.add(m);
		}
		if (!found.isEmpty()) {
			state.markDirty();
			for (ServerPlayerEntity p : world.getServer().getPlayerManager().getPlayerList()) {
				ServerPlayNetworking.send(p, new MarkersPayload(false, found));
			}
		}
	}

	private static void sendPlayers(ServerPlayerEntity to) {
		List<PlayerDot> dots = new ArrayList<>();
		for (ServerPlayerEntity p : to.getServer().getOverworld().getPlayers()) {
			if (!p.isSpectator()) {
				dots.add(new PlayerDot(p.getUuid(), p.getName().getString(), p.getX(), p.getZ(), p.getYaw()));
			}
		}
		ServerPlayNetworking.send(to, new PlayersPayload(dots));
	}

	// ------------------------------------------------------------------ territorios

	static TerritoriesPayload territoriesPayload(AtlasState state) {
		List<TerritoryView> list = new ArrayList<>();
		for (AtlasState.Territory t : state.territories) {
			list.add(new TerritoryView(t.id, t.name, t.owner, t.ownerName, t.color, t.chunks.toLongArray()));
		}
		return new TerritoriesPayload(list);
	}

	private static void broadcastTerritories(ServerPlayerEntity any, AtlasState state) {
		TerritoriesPayload payload = territoriesPayload(state);
		for (ServerPlayerEntity p : any.getServer().getPlayerManager().getPlayerList()) {
			ServerPlayNetworking.send(p, payload);
		}
	}

	public static int claimCost() {
		return Math.max(0, EwConfig.get().atlasClaimCost);
	}

	public static int maxChunks() {
		return Math.max(1, EwConfig.get().atlasMaxChunks);
	}

	/** ¿Están todos pegados entre sí (por los lados)? */
	public static boolean connected(LongOpenHashSet chunks) {
		if (chunks.isEmpty()) {
			return true;
		}
		LongOpenHashSet seen = new LongOpenHashSet();
		LongArrayFIFOQueue q = new LongArrayFIFOQueue();
		long first = chunks.iterator().nextLong();
		q.enqueue(first);
		seen.add(first);
		while (!q.isEmpty()) {
			long k = q.dequeueLong();
			int x = ChunkPos.getPackedX(k), z = ChunkPos.getPackedZ(k);
			for (long n : new long[] {ChunkPos.toLong(x + 1, z), ChunkPos.toLong(x - 1, z), ChunkPos.toLong(x, z + 1), ChunkPos.toLong(x, z - 1)}) {
				if (chunks.contains(n) && seen.add(n)) {
					q.enqueue(n);
				}
			}
		}
		return seen.size() == chunks.size();
	}

	/** El resultado de reclamar (para las pruebas): null si ha ido bien, o la clave del mensaje de error. */
	@Nullable
	public static String claim(AtlasState state, UUID owner, String ownerName, String rawName, long[] chunks, boolean creative,
			java.util.function.IntPredicate pay) {
		String name = rawName == null ? "" : rawName.trim();
		if (name.isEmpty() || name.length() > 32) {
			return "message.evolvedwarfare.atlas.bad_name";
		}
		LongOpenHashSet mine = state.explored(owner);
		AtlasState.Territory target = null;
		int owned = 0;
		for (AtlasState.Territory t : state.territories) {
			if (t.owner.equals(owner)) {
				owned += t.chunks.size();
				if (t.name.equalsIgnoreCase(name)) {
					target = t;
				}
			}
		}
		LongOpenHashSet add = new LongOpenHashSet();
		for (long k : chunks) {
			if (add.size() > 1024) {
				break;
			}
			AtlasState.Territory o = state.owner(k);
			if (o != null) {
				if (o == target) {
					continue;
				}
				return "message.evolvedwarfare.atlas.taken";
			}
			if (!mine.contains(k)) {
				return "message.evolvedwarfare.atlas.unexplored";
			}
			add.add(k);
		}
		if (add.isEmpty()) {
			return "message.evolvedwarfare.atlas.nothing";
		}
		LongOpenHashSet all = new LongOpenHashSet(add);
		if (target != null) {
			all.addAll(target.chunks);
		}
		if (!connected(all)) {
			return "message.evolvedwarfare.atlas.not_connected";
		}
		if (owned + add.size() > maxChunks()) {
			return "message.evolvedwarfare.atlas.too_many";
		}
		int cost = add.size() * claimCost();
		if (!creative && !pay.test(cost)) {
			return "message.evolvedwarfare.atlas.no_coins";
		}
		if (target == null) {
			target = new AtlasState.Territory(state.nextId++, name, owner, ownerName, COLORS[(state.nextId * 5) % COLORS.length]);
			state.territories.add(target);
		}
		target.name = name;
		target.ownerName = ownerName;
		target.chunks.addAll(add);
		state.markDirty();
		return null;
	}

	static void claim(ServerPlayerEntity player, String name, long[] chunks) {
		if (player.getWorld().getRegistryKey() != World.OVERWORLD) {
			return;
		}
		AtlasState state = AtlasState.get(player.getServer());
		int[] paid = {0};
		String err = claim(state, player.getUuid(), player.getName().getString(), name, chunks, player.getAbilities().creativeMode, cost -> {
			paid[0] = cost;
			return Market.takeCoins(player.getInventory(), cost);
		});
		if (err != null) {
			player.sendMessage(Text.translatable(err, claimCost(), maxChunks()).formatted(Formatting.RED), false);
			return;
		}
		player.sendMessage(Text.translatable("message.evolvedwarfare.atlas.claimed", name.trim(), paid[0]).formatted(Formatting.GOLD), false);
		player.getWorld().playSound(null, player.getBlockPos(), SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT, SoundCategory.PLAYERS, 1.0F, 1.0F);
		broadcastTerritories(player, state);
	}

	static void unclaim(ServerPlayerEntity player, long[] chunks) {
		AtlasState state = AtlasState.get(player.getServer());
		boolean changed = false;
		for (long k : chunks) {
			AtlasState.Territory t = state.owner(k);
			if (t != null && t.owner.equals(player.getUuid())) {
				t.chunks.remove(k);
				changed = true;
			}
		}
		if (changed) {
			state.territories.removeIf(t -> t.chunks.isEmpty());
			state.markDirty();
			player.sendMessage(Text.translatable("message.evolvedwarfare.atlas.released").formatted(Formatting.GRAY), false);
			broadcastTerritories(player, state);
		}
	}
}
