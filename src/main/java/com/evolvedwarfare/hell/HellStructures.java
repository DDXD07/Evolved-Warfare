package com.evolvedwarfare.hell;

import com.evolvedwarfare.EvolvedWarfare;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.MapColor;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.block.piston.PistonBehavior;
import net.minecraft.component.ComponentType;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.Rarity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
import net.minecraft.world.gen.structure.Structure;
import org.jetbrains.annotations.Nullable;

/**
 * Las estructuras del Nether bíblico (fase 35): sus bloques y objetos (las plantillas las hace tools/ew_hellstructs.py),
 * y lo que pasa en ellas: runas que cambian y bloques que aparecen y desaparecen al acercarte, criaturas dormidas que se
 * despiertan, la puerta de las visiones, el trono que cuenta tus muertes en este reino y los libros de condena.
 */
public final class HellStructures {
	public static final String DORMANT = "ew_dormant";
	public static final int THRONE_DEATHS = 3;
	public static final TagKey<Structure> LANDMARKS = TagKey.of(RegistryKeys.STRUCTURE, EvolvedWarfare.id("hell_landmarks"));
	public static final int VISIONS = 8;
	public static final List<Item> CREATIVE = new ArrayList<>();

	/** Cuántas veces ha muerto el jugador en el Nether (se conserva al morir). */
	public static final AttachmentType<Integer> NETHER_DEATHS = AttachmentRegistry.<Integer>builder().persistent(Codec.INT).copyOnDeath()
			.initializer(() -> 0).buildAndRegister(EvolvedWarfare.id("nether_deaths"));

	public static final ComponentType<Integer> BLADE = Registry.register(Registries.DATA_COMPONENT_TYPE, EvolvedWarfare.id("blade"),
			ComponentType.<Integer>builder().codec(Codec.INT).packetCodec(PacketCodecs.VAR_INT).build());

	private static AbstractBlock.Settings stone() {
		return AbstractBlock.Settings.create().mapColor(MapColor.BLACK).strength(1.8F, 6.0F).requiresTool().sounds(BlockSoundGroup.POLISHED_DEEPSLATE);
	}

	private static AbstractBlock.Settings sealed() {
		return AbstractBlock.Settings.create().mapColor(MapColor.BLACK).strength(-1.0F, 3600000.0F).dropsNothing().pistonBehavior(PistonBehavior.BLOCK)
				.allowsSpawning((st, w, p, t) -> false);
	}

	public static final Block BABEL_RUNE = block("babel_rune", new StructureBlocks.Rune(stone().luminance(s -> 4)));
	public static final Block CRUMBLING_FLOOR = block("crumbling_floor", new StructureBlocks.Crumbling(AbstractBlock.Settings.create()
			.mapColor(MapColor.BLACK).strength(0.8F).sounds(BlockSoundGroup.POLISHED_DEEPSLATE)));
	public static final Block SHIFTING_BLOCK = block("shifting_block", new StructureBlocks.Shifting(AbstractBlock.Settings.create()
			.mapColor(MapColor.PURPLE).strength(1.5F, 6.0F).nonOpaque().luminance(s -> 5).sounds(BlockSoundGroup.AMETHYST_BLOCK)
			.allowsSpawning((st, w, p, t) -> false).suffocates((st, w, p) -> false).blockVision((st, w, p) -> false).solidBlock((st, w, p) -> false)));
	public static final Block BABEL_PORTAL = block("babel_portal", new StructureBlocks.VisionGate(sealed().noCollision().nonOpaque()
			.luminance(s -> 11).sounds(BlockSoundGroup.GLASS).mapColor(MapColor.PURPLE)));
	public static final Block FALSE_ALTAR = block("false_altar", new StructureBlocks.Altar(stone().strength(3.0F, 9.0F).nonOpaque()));
	public static final Block IDOL = block("idol", new StructureBlocks.Idol(stone().strength(3.0F, 9.0F).nonOpaque()));
	public static final Block FALLEN_WING = block("fallen_wing", new StructureBlocks.Facing(AbstractBlock.Settings.create().mapColor(MapColor.BLACK)
			.strength(0.3F).noCollision().nonOpaque().sounds(BlockSoundGroup.WOOL), VoxelShapes.cuboid(0.1, 0, 0.1, 0.9, 1, 0.9), false));
	public static final Block FALLEN_SWORD = block("fallen_sword", new StructureBlocks.EmbeddedSword(AbstractBlock.Settings.create()
			.mapColor(MapColor.IRON_GRAY).strength(1.0F).noCollision().nonOpaque().luminance(s -> 7).sounds(BlockSoundGroup.CHAIN).dropsNothing()));
	public static final Block ABYSS_THRONE = block("abyss_throne", new StructureBlocks.Throne(sealed().nonOpaque().luminance(s -> 6)
			.sounds(BlockSoundGroup.POLISHED_DEEPSLATE)));
	public static final Block SEALED_WALL = block("sealed_wall", new Block(sealed().sounds(BlockSoundGroup.POLISHED_DEEPSLATE).luminance(s -> 3)));
	public static final Block BROKEN_SEAL = block("broken_seal", new Block(stone().strength(2.0F, 6.0F)));

	public static final BlockEntityType<IdolBlockEntity> IDOL_ENTITY = Registry.register(Registries.BLOCK_ENTITY_TYPE, EvolvedWarfare.id("idol"),
			BlockEntityType.Builder.create(IdolBlockEntity::new, IDOL).build(null));

	public static final Item BOOK_OF_CONDEMNATION = item("book_of_condemnation", new BookOfCondemnationItem(new Item.Settings().maxCount(16)
			.rarity(Rarity.UNCOMMON)));
	public static final Item FALLEN_BLADE = item("fallen_blade", new FallenBladeItem(new Item.Settings().rarity(Rarity.RARE)));

	private HellStructures() {
	}

	private static Block block(String name, Block block) {
		Identifier id = EvolvedWarfare.id(name);
		Registry.register(Registries.BLOCK, id, block);
		CREATIVE.add(Registry.register(Registries.ITEM, id, new BlockItem(block, new Item.Settings())));
		return block;
	}

	private static Item item(String name, Item item) {
		CREATIVE.add(Registry.register(Registries.ITEM, EvolvedWarfare.id(name), item));
		return item;
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(HellStructures::tick);
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof ServerPlayerEntity player && player.getWorld().getRegistryKey() == World.NETHER) {
				addNetherDeath(player);
			}
		});
	}

	public static int netherDeaths(PlayerEntity player) {
		return player.getAttachedOrElse(NETHER_DEATHS, 0);
	}

	public static void addNetherDeath(PlayerEntity player) {
		player.setAttached(NETHER_DEATHS, netherDeaths(player) + 1);
	}

	private static void tick(MinecraftServer server) {
		int t = server.getTicks();
		if (t % 10 != 0) {
			return;
		}
		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			if (player.isSpectator()) {
				continue;
			}
			ServerWorld world = player.getServerWorld();
			if (!player.isCreative()) {
				wakeNear(world, player.getPos(), 5.5, player);
			}
			if (t % 40 == 0 && world.getRegistryKey() == World.NETHER) {
				scan(world, player.getBlockPos());
			}
		}
	}

	// ------------------------------------------------------------------ lo que se mueve al acercarte

	/** Runas que cambian de forma y bloques que aparecen y desaparecen alrededor de pos; devuelve cuántos cambiaron. */
	public static int scan(ServerWorld world, BlockPos center) {
		int changed = 0;
		long phase = world.getTime() / 60;
		for (BlockPos p : BlockPos.iterate(center.add(-10, -6, -10), center.add(10, 6, 10))) {
			// se salta enseguida lo que está en secciones de chunk sin runas ni bloques cambiantes (casi todo)
			if (!sectionHasShifting(world, p)) {
				continue;
			}
			BlockState state = world.getBlockState(p);
			if (state.isOf(BABEL_RUNE)) {
				if (p.getSquaredDistance(center) <= 64 && world.random.nextInt(5) < 2) {
					int g = (state.get(StructureBlocks.Rune.GLYPH) + 1 + world.random.nextInt(7)) % 8;
					world.setBlockState(p, state.with(StructureBlocks.Rune.GLYPH, g), Block.NOTIFY_LISTENERS);
					changed++;
				}
			} else if (state.isOf(SHIFTING_BLOCK)) {
				boolean solid = ((phase + (p.getX() * 31L + p.getY() * 7L + p.getZ() * 13L)) & 1L) == 0;
				if (solid != state.get(StructureBlocks.Shifting.SOLID)) {
					if (solid && !world.getOtherEntities(null, new Box(p)).isEmpty()) {
						continue;
					}
					world.setBlockState(p.toImmutable(), state.with(StructureBlocks.Shifting.SOLID, solid), Block.NOTIFY_ALL);
					world.spawnParticles(ParticleTypes.REVERSE_PORTAL, p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, 6, 0.3, 0.3, 0.3, 0.02);
					changed++;
				}
			}
		}
		return changed;
	}

	private static long lastSection = Long.MIN_VALUE;
	private static boolean lastSectionHas;
	private static long lastSectionTime = -1;

	/** ¿Tiene la sección (16x16x16) de ese bloque alguna runa o bloque cambiante? (mira su paleta; se recuerda la última) */
	private static boolean sectionHasShifting(ServerWorld world, BlockPos p) {
		long key = net.minecraft.util.math.ChunkSectionPos.asLong(p.getX() >> 4, p.getY() >> 4, p.getZ() >> 4);
		if (key == lastSection && lastSectionTime == world.getTime()) {
			return lastSectionHas;
		}
		boolean has = false;
		if (!world.isOutOfHeightLimit(p.getY()) && world.isChunkLoaded(p.getX() >> 4, p.getZ() >> 4)) {
			var chunk = world.getChunk(p.getX() >> 4, p.getZ() >> 4);
			var section = chunk.getSection(chunk.getSectionIndex(p.getY()));
			has = !section.isEmpty() && section.hasAny(st -> st.isOf(BABEL_RUNE) || st.isOf(SHIFTING_BLOCK));
		}
		lastSection = key;
		lastSectionHas = has;
		lastSectionTime = world.getTime();
		return has;
	}

	/** Despierta a las criaturas dormidas (las de las tumbas y los habitantes de Sodoma) cercanas; devuelve cuántas. */
	public static int wakeNear(ServerWorld world, Vec3d pos, double radius, @Nullable PlayerEntity target) {
		List<MobEntity> sleepers = world.getEntitiesByClass(MobEntity.class, new Box(pos, pos).expand(radius),
				e -> e.getCommandTags().contains(DORMANT) && e.squaredDistanceTo(pos) <= radius * radius);
		for (MobEntity mob : sleepers) {
			mob.removeCommandTag(DORMANT);
			mob.setAiDisabled(false);
			if (target != null && !Curses.creative(target)) {
				mob.setTarget(target);
			}
			world.playSound(null, mob.getBlockPos(), SoundEvents.ENTITY_ZOMBIE_VILLAGER_CONVERTED, SoundCategory.HOSTILE, 0.8F, 0.5F);
			world.spawnParticles(ParticleTypes.ASH, mob.getX(), mob.getBodyY(0.6), mob.getZ(), 30, 0.4, 0.6, 0.4, 0.02);
		}
		if (!sleepers.isEmpty() && target != null) {
			target.sendMessage(Text.translatable("message.evolvedwarfare.dormant.wake").formatted(Formatting.RED), true);
		}
		return sleepers.size();
	}

	// ------------------------------------------------------------------ la puerta de las visiones

	@Nullable
	static Pair<BlockPos, RegistryEntry<Structure>> landmark(ServerWorld world, BlockPos from, int radius) {
		if (world.getRegistryKey() != World.NETHER) {
			return null;
		}
		var list = world.getRegistryManager().get(RegistryKeys.STRUCTURE).getEntryList(LANDMARKS);
		if (list.isEmpty()) {
			return null;
		}
		return world.getChunkManager().getChunkGenerator().locateStructure(world, list.get(), from, radius, false);
	}

	public static void vision(ServerWorld world, ServerPlayerEntity player, BlockPos at) {
		player.addStatusEffect(new StatusEffectInstance(StatusEffects.NAUSEA, 160, 0));
		player.addStatusEffect(new StatusEffectInstance(StatusEffects.DARKNESS, 40, 0));
		int k = world.random.nextInt(VISIONS);
		player.sendMessage(Text.translatable("message.evolvedwarfare.portal.vision", Text.translatable("vision.evolvedwarfare." + k))
				.formatted(Formatting.LIGHT_PURPLE, Formatting.ITALIC), false);
		world.playSound(null, at, SoundEvents.BLOCK_PORTAL_TRIGGER, SoundCategory.BLOCKS, 0.4F, 1.6F);
		if (ServerPlayNetworking.canSend(player, HellPayloads.Vision.ID)) {
			Vec3d dir = null;
			var found = landmark(world, player.getBlockPos(), 24);
			if (found != null) {
				dir = Vec3d.of(found.getFirst().subtract(player.getBlockPos())).multiply(1, 0, 1);
			}
			if (dir == null || dir.lengthSquared() < 1) {
				double a = world.random.nextDouble() * Math.PI * 2;
				dir = new Vec3d(Math.cos(a), 0, Math.sin(a));
			}
			dir = dir.normalize();
			int x = (int) (player.getX() + dir.x * 20), z = (int) (player.getZ() + dir.z * 20);
			int y = Hell.floorY(world, x, z, player.getBlockY() + 12);
			ServerPlayNetworking.send(player, new HellPayloads.Vision(x + 0.5, Math.min(y, player.getBlockY() + 12), z + 0.5));
		}
	}

	// ------------------------------------------------------------------ el trono y lo sellado

	/** Usar el trono del Abismo: si te reconoce, abre los muros sellados cercanos. Devuelve cuántos bloques abrió (-1 si no te reconoce). */
	public static int useThrone(ServerWorld world, ServerPlayerEntity player, BlockPos pos) {
		int deaths = netherDeaths(player);
		if (deaths < THRONE_DEATHS && !Curses.creative(player)) {
			player.sendMessage(Text.translatable("message.evolvedwarfare.throne.deaths", deaths, THRONE_DEATHS).formatted(Formatting.DARK_PURPLE), true);
			world.playSound(null, pos, SoundEvents.BLOCK_RESPAWN_ANCHOR_DEPLETE.value(), SoundCategory.BLOCKS, 1.0F, 0.5F);
			return -1;
		}
		for (net.minecraft.util.Hand hand : net.minecraft.util.Hand.values()) {
			net.minecraft.item.ItemStack held = player.getStackInHand(hand);
			if (held.isOf(Apocalypse.ABYSS_KEY) && Apocalypse.wakeAngel(world, player, pos) != null) {
				held.decrementUnlessCreative(1, player);
				openSealed(world, pos, 16);
				return 0;
			}
		}
		int opened = openSealed(world, pos, 16);
		if (opened > 0) {
			player.sendMessage(Text.translatable("message.evolvedwarfare.throne.open").formatted(Formatting.GOLD), true);
			world.playSound(null, pos, SoundEvents.BLOCK_END_PORTAL_SPAWN, SoundCategory.BLOCKS, 0.8F, 0.6F);
		}
		return opened;
	}

	public static int openSealed(ServerWorld world, BlockPos center, int r) {
		int n = 0;
		for (BlockPos p : BlockPos.iterate(center.add(-r, -8, -r), center.add(r, 8, r))) {
			if (world.getBlockState(p).isOf(SEALED_WALL)) {
				world.setBlockState(p, Blocks.AIR.getDefaultState());
				world.spawnParticles(ParticleTypes.SOUL, p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, 4, 0.3, 0.3, 0.3, 0.02);
				n++;
			}
		}
		return n;
	}

	// ------------------------------------------------------------------ los libros de condena

	/** Leer un libro de condena: te hace daño y, a cambio, debilita a los señores del abismo cercanos o te revela un lugar. */
	public static int read(ServerWorld world, ServerPlayerEntity player) {
		if (!Curses.creative(player)) {
			player.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 600, 0));
			player.addStatusEffect(new StatusEffectInstance(StatusEffects.HUNGER, 400, 1));
			player.addStatusEffect(new StatusEffectInstance(StatusEffects.WITHER, 100, 0));
		}
		player.addStatusEffect(new StatusEffectInstance(StatusEffects.DARKNESS, 120, 0));
		NetherClimate.addCorruption(player, 3);
		world.playSound(null, player.getBlockPos(), SoundEvents.ITEM_BOOK_PAGE_TURN, SoundCategory.PLAYERS, 1.0F, 0.5F);
		world.playSound(null, player.getBlockPos(), SoundEvents.AMBIENT_SOUL_SAND_VALLEY_MOOD.value(), SoundCategory.PLAYERS, 1.0F, 0.7F);
		// los señores del abismo cercanos
		List<net.minecraft.entity.mob.HostileEntity> bosses = world.getEntitiesByClass(net.minecraft.entity.mob.HostileEntity.class,
				player.getBoundingBox().expand(32), e -> e.isAlive() && (e instanceof HellBossEntity || e instanceof MiniBossEntity)
						&& !(e instanceof FalseProphetEntity f && f.illusion()));
		if (!bosses.isEmpty()) {
			for (net.minecraft.entity.mob.HostileEntity boss : bosses) {
				boss.setHealth(Math.max(1.0F, boss.getHealth() - boss.getMaxHealth() * 0.2F));
				boss.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 1200, 1));
				boss.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 600, 0));
				boss.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 1200, 0));
				world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, boss.getX(), boss.getBodyY(0.5), boss.getZ(), 40, 0.6, 1.0, 0.6, 0.05);
			}
			player.sendMessage(Text.translatable("message.evolvedwarfare.condemnation.weaken").formatted(Formatting.GOLD), false);
			return bosses.size();
		}
		var found = landmark(world, player.getBlockPos(), 60);
		if (found == null) {
			player.sendMessage(Text.translatable("message.evolvedwarfare.condemnation.nothing").formatted(Formatting.GRAY), true);
			return 0;
		}
		BlockPos at = found.getFirst();
		int dx = at.getX() - player.getBlockX(), dz = at.getZ() - player.getBlockZ();
		Direction dir = Math.abs(dx) > Math.abs(dz) ? (dx > 0 ? Direction.EAST : Direction.WEST) : (dz > 0 ? Direction.SOUTH : Direction.NORTH);
		String key = found.getSecond().getKey().map(k -> "structure." + k.getValue().getNamespace() + "." + k.getValue().getPath()).orElse("?");
		player.sendMessage(Text.translatable("message.evolvedwarfare.condemnation.reveal", Text.translatable(key), (int) Math.sqrt(dx * (double) dx + dz * (double) dz),
				Text.translatable("direction.evolvedwarfare." + dir.asString())).formatted(Formatting.DARK_RED), false);
		if (ServerPlayNetworking.canSend(player, HellPayloads.Vision.ID)) {
			Vec3d d = new Vec3d(dx, 0, dz).normalize();
			ServerPlayNetworking.send(player, new HellPayloads.Vision(player.getX() + d.x * 18, player.getY(), player.getZ() + d.z * 18));
		}
		return -1;
	}
}
