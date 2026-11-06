package com.evolvedwarfare.hell;

import com.evolvedwarfare.EvolvedWarfare;
import com.evolvedwarfare.registry.ModBlocks;
import com.evolvedwarfare.registry.ModEffects;
import com.mojang.serialization.Codec;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityCombatEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CampfireBlock;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.SkeletonEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.gen.structure.Structure;

/**
 * Lo que el Nether bíblico le hace al que está en él, bioma a bioma:
 * <ul>
 * <li><b>Lago de Fuego y Azufre</b>: a los 2 minutos a la intemperie (sin agua ni techo), fiebre y hambre.</li>
 * <li><b>Valle de los Huesos</b>: si te quedas en el mismo sitio, los esqueletos enterrados se levantan en oleadas cada vez
 * mayores; y rachas de viento seco que empujan y dan hambre.</li>
 * <li><b>El Abismo</b>: la luz apenas funciona: las antorchas se apagan solas y los farolillos duran poco.</li>
 * <li><b>Montañas de Azufre y Oscuridad</b>: rayos de fuego que dejan cráteres de azufre (y el calor del juicio a quien
 * pilla cerca), zonas donde la luz se apaga y el aire espeso que quita la regeneración.</li>
 * <li><b>Calor real</b>: cerca de la lava o en las montañas, hambre y la armadura se va estropeando.</li>
 * <li><b>Corrupción</b> (0-100): sube con el tiempo en el Nether y al matar sus criaturas; da hambre, trae visiones y
 * corrompe los bloques de alrededor (piedra maldita). Baja fuera del Nether.</li>
 * <li><b>Ecos y visiones</b>: frases bíblicas deformadas, susurros y siluetas que desaparecen (a veces señalan la
 * Ciudadela).</li>
 * </ul>
 * Las maldiciones ({@link Curses}) se quitan al salir del Nether.
 */
public final class NetherClimate {
	public static final AttachmentType<Integer> CORRUPTION = AttachmentRegistry.<Integer>builder().persistent(Codec.INT).initializer(() -> 0)
			.buildAndRegister(EvolvedWarfare.id("corruption"));
	public static final int MAX_CORRUPTION = 100;
	public static final int FEVER_TICKS = 2400;
	public static final int DWELL_TICKS = 900;
	public static final int WAVE_EVERY = 400;
	private static final TagKey<Structure> LANDMARKS = TagKey.of(RegistryKeys.STRUCTURE, EvolvedWarfare.id("hell_landmarks"));
	private static final int PHRASES = 20;

	public static RegistryKey<Biome> biome(String name) {
		return RegistryKey.of(RegistryKeys.BIOME, EvolvedWarfare.id(name));
	}

	public static final RegistryKey<Biome> LAKE = biome("lake_of_fire");
	public static final RegistryKey<Biome> VALLEY = biome("valley_of_bones");
	public static final RegistryKey<Biome> RUINS = biome("sodom_ruins");
	public static final RegistryKey<Biome> ABYSS = biome("abyss");
	public static final RegistryKey<Biome> MOUNTAINS = biome("sulfur_mountains");

	/** Lo de cada jugador mientras está en el Nether. */
	static final class State {
		int exposed;
		BlockPos anchor = BlockPos.ORIGIN;
		int dwell;
		int waves;
		long nextVision;
		long nextBolt;
		long nextGust;
		long lastHint;
		long lastLightsOut;
		boolean feverTold;
		int sentCorruption = -1;
	}

	private static final Map<UUID, State> STATES = new HashMap<>();
	private static Vec3d wind = new Vec3d(1, 0, 0);

	private NetherClimate() {
	}

	public static void register() {
		PayloadTypeRegistry.playS2C().register(HellPayloads.Corruption.ID, HellPayloads.Corruption.CODEC);
		PayloadTypeRegistry.playS2C().register(HellPayloads.Vision.ID, HellPayloads.Vision.CODEC);
		ServerTickEvents.END_SERVER_TICK.register(NetherClimate::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> STATES.remove(handler.player.getUuid()));
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> send(handler.player, state(handler.player)));
		ServerEntityCombatEvents.AFTER_KILLED_OTHER_ENTITY.register((world, killer, killed) -> {
			if (killer instanceof ServerPlayerEntity player && killed instanceof HostileEntity && world.getRegistryKey() == World.NETHER) {
				addCorruption(player, 1);
			}
		});
	}

	static State state(ServerPlayerEntity player) {
		return STATES.computeIfAbsent(player.getUuid(), k -> new State());
	}

	public static int corruption(ServerPlayerEntity player) {
		return player.getAttachedOrElse(CORRUPTION, 0);
	}

	public static void addCorruption(ServerPlayerEntity player, int amount) {
		int before = corruption(player);
		int after = MathHelper.clamp(before + amount, 0, MAX_CORRUPTION);
		if (after != before) {
			player.setAttached(CORRUPTION, after);
			if (after / 25 > before / 25) {
				player.sendMessage(Text.translatable("message.evolvedwarfare.hell.corruption", after).formatted(Formatting.DARK_PURPLE), true);
				player.playSoundToPlayer(SoundEvents.AMBIENT_SOUL_SAND_VALLEY_MOOD.value(), SoundCategory.AMBIENT, 1.0F, 0.6F);
			}
			send(player, state(player));
		}
	}

	private static void send(ServerPlayerEntity player, State s) {
		int c = corruption(player);
		if (c != s.sentCorruption && ServerPlayNetworking.canSend(player, HellPayloads.Corruption.ID)) {
			s.sentCorruption = c;
			ServerPlayNetworking.send(player, new HellPayloads.Corruption(c));
		}
	}

	public static boolean in(ServerWorld world, BlockPos pos, RegistryKey<Biome> biome) {
		return world.getBiome(pos).matchesKey(biome);
	}

	private static void tick(MinecraftServer server) {
		if (server.getTicks() % 20 != 0) {
			return;
		}
		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			if (player.isSpectator()) {
				continue;
			}
			State s = state(player);
			ServerWorld world = player.getServerWorld();
			if (world.getRegistryKey() != World.NETHER) {
				// fuera: se quitan las maldiciones y baja la corrupción
				if (Curses.lift(player)) {
					player.sendMessage(Text.translatable("message.evolvedwarfare.hell.curses_lifted").formatted(Formatting.GRAY), true);
				}
				if (server.getTicks() % 200 == 0 && corruption(player) > 0) {
					addCorruption(player, -1);
				}
				s.exposed = 0;
				s.dwell = 0;
				continue;
			}
			secondInNether(world, player, s, server.getTicks());
		}
	}

	/** Un segundo en el Nether (también lo usan las pruebas). */
	static void secondInNether(ServerWorld world, ServerPlayerEntity player, State s, long ticks) {
		long now = world.getTime();
		BlockPos pos = player.getBlockPos();
		Random random = world.random;
		boolean creative = player.isCreative();
		int corruption = corruption(player);
		if (ticks % 600 == 0) {
			addCorruption(player, 1);
		}
		if (!creative) {
			player.addExhaustion(corruption * 0.0015F);
		}
		if (corruption >= 30 && random.nextInt(400) < corruption) {
			corruptNear(world, pos, random);
		}
		boolean nearLava = nearLava(world, pos);
		if (in(world, pos, LAKE)) {
			lake(world, player, s, nearLava);
		} else {
			s.exposed = Math.max(0, s.exposed - 40);
		}
		if (in(world, pos, VALLEY)) {
			valley(world, player, s, now, random);
		} else {
			s.dwell = 0;
			s.waves = 0;
		}
		if (in(world, pos, ABYSS)) {
			lightsOut(world, player, s, now, 0.3F, 0.08F);
		}
		if (in(world, pos, MOUNTAINS)) {
			mountains(world, player, s, now, random);
		}
		// el calor real: cerca de la lava (y en las montañas), hambre y la armadura se estropea
		if ((nearLava || in(world, pos, MOUNTAINS)) && !creative && !player.isTouchingWater()) {
			player.addExhaustion(0.03F);
			if (now % 600 < 20) {
				for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
					ItemStack armor = player.getEquippedStack(slot);
					if (!armor.isEmpty() && armor.isDamageable()) {
						armor.damage(1, player, slot);
					}
				}
			}
		}
		visions(world, player, s, now, random, corruption);
		send(player, s);
	}

	// ------------------------------------------------------------------ los biomas

	/** ¿Al raso? (sin techo en 6 bloques por encima y sin agua). */
	static boolean exposed(ServerWorld world, ServerPlayerEntity player) {
		if (player.isTouchingWater()) {
			return false;
		}
		BlockPos head = player.getBlockPos().up(2);
		for (int y = 0; y < 6; y++) {
			if (!world.getBlockState(head.up(y)).getCollisionShape(world, head.up(y)).isEmpty()) {
				return false;
			}
		}
		return true;
	}

	static boolean nearLava(ServerWorld world, BlockPos pos) {
		int lava = 0;
		for (BlockPos p : BlockPos.iterate(pos.add(-3, -2, -3), pos.add(3, 1, 3))) {
			if (world.getFluidState(p).isIn(FluidTags.LAVA) && ++lava >= 3) {
				return true;
			}
		}
		return false;
	}

	private static void lake(ServerWorld world, ServerPlayerEntity player, State s, boolean nearLava) {
		if (exposed(world, player)) {
			s.exposed += 20 * (nearLava ? 2 : 1);
		} else {
			s.exposed = Math.max(0, s.exposed - 60);
		}
		if (s.exposed >= FEVER_TICKS && !player.isCreative()) {
			player.addExhaustion(0.05F);
			if (Curses.curse(player, ModEffects.FEVER) && !s.feverTold) {
				s.feverTold = true;
				player.sendMessage(Text.translatable("message.evolvedwarfare.hell.fever").formatted(Formatting.RED), true);
			}
		}
	}

	private static void valley(ServerWorld world, ServerPlayerEntity player, State s, long now, Random random) {
		BlockPos pos = player.getBlockPos();
		if (s.anchor.getSquaredDistance(pos) > 12 * 12) {
			s.anchor = pos;
			s.dwell = 0;
			s.waves = 0;
		}
		s.dwell += 20;
		if (s.dwell >= DWELL_TICKS && (s.dwell - DWELL_TICKS) % WAVE_EVERY == 0 && !player.isCreative()) {
			int[] sizes = {1, 2, 3, 5, 8};
			int n = sizes[Math.min(sizes.length - 1, s.waves)];
			s.waves++;
			if (s.waves == 1) {
				player.sendMessage(Text.translatable("message.evolvedwarfare.hell.bones").formatted(Formatting.GRAY, Formatting.ITALIC), true);
			}
			raiseSkeletons(world, pos, n, random);
		}
		// el viento seco
		if (now >= s.nextGust) {
			s.nextGust = now + 160 + random.nextInt(200);
			if (random.nextInt(3) == 0) {
				double a = Math.atan2(wind.z, wind.x) + (random.nextDouble() - 0.5) * 1.2;
				wind = new Vec3d(Math.cos(a), 0, Math.sin(a));
			}
			if (!player.isCreative() && exposed(world, player)) {
				player.addVelocity(wind.x * 0.45, 0.06, wind.z * 0.45);
				player.velocityModified = true;
				player.addExhaustion(0.4F);
			}
			world.spawnParticles(ParticleTypes.WHITE_ASH, player.getX() - wind.x * 4, player.getY() + 1, player.getZ() - wind.z * 4, 60, 3.0, 1.2, 3.0, 0.2);
			world.playSound(null, pos, SoundEvents.ITEM_ELYTRA_FLYING, SoundCategory.WEATHER, 0.35F, 0.6F);
		}
	}

	/** Los esqueletos enterrados se levantan alrededor. */
	public static int raiseSkeletons(ServerWorld world, BlockPos around, int n, Random random) {
		int raised = 0;
		for (int i = 0; i < n * 3 && raised < n; i++) {
			double a = random.nextDouble() * Math.PI * 2;
			int r = 4 + random.nextInt(6);
			int x = around.getX() + (int) (Math.cos(a) * r);
			int z = around.getZ() + (int) (Math.sin(a) * r);
			BlockPos p = null;
			for (int dy = 4; dy >= -6; dy--) {
				BlockPos c = new BlockPos(x, around.getY() + dy, z);
				if (world.getBlockState(c).isAir() && world.getBlockState(c.up()).isAir() && world.getBlockState(c.down()).isSolidBlock(world, c.down())) {
					p = c;
					break;
				}
			}
			if (p == null) {
				continue;
			}
			SkeletonEntity skeleton = EntityType.SKELETON.create(world);
			if (skeleton == null) {
				continue;
			}
			skeleton.refreshPositionAndAngles(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, random.nextFloat() * 360.0F, 0.0F);
			skeleton.initialize(world, world.getLocalDifficulty(p), SpawnReason.EVENT, null);
			world.spawnEntity(skeleton);
			BlockState dust = world.getBlockState(p.down());
			world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, dust), p.getX() + 0.5, p.getY() + 0.2, p.getZ() + 0.5, 30, 0.4, 0.3, 0.4, 0.1);
			world.playSound(null, p, SoundEvents.ENTITY_SKELETON_AMBIENT, SoundCategory.HOSTILE, 1.0F, 0.5F);
			world.playSound(null, p, SoundEvents.BLOCK_BONE_BLOCK_BREAK, SoundCategory.BLOCKS, 1.0F, 0.6F);
			raised++;
		}
		return raised;
	}

	/** Las luces cercanas se apagan (antorchas con {@code torch} de probabilidad, farolillos con {@code lantern}). */
	static int lightsOut(ServerWorld world, ServerPlayerEntity player, State s, long now, float torch, float lantern) {
		int out = extinguish(world, player.getBlockPos(), 30, torch, lantern);
		if (out > 0 && now - s.lastLightsOut > 1200) {
			s.lastLightsOut = now;
			player.sendMessage(Text.translatable("message.evolvedwarfare.hell.lights").formatted(Formatting.DARK_GRAY, Formatting.ITALIC), true);
		}
		return out;
	}

	/** Mira {@code tries} sitios al azar alrededor y apaga las luces que toquen; devuelve cuántas. */
	public static int extinguish(ServerWorld world, BlockPos pos, int tries, float torch, float lantern) {
		Random random = world.random;
		int out = 0;
		for (int i = 0; i < tries; i++) {
			BlockPos p = pos.add(random.nextInt(21) - 10, random.nextInt(11) - 5, random.nextInt(21) - 10);
			BlockState b = world.getBlockState(p);
			boolean isTorch = b.isOf(Blocks.TORCH) || b.isOf(Blocks.WALL_TORCH) || b.isOf(Blocks.SOUL_TORCH) || b.isOf(Blocks.SOUL_WALL_TORCH);
			boolean isLantern = b.isOf(Blocks.LANTERN) || b.isOf(Blocks.SOUL_LANTERN) || b.isOf(Blocks.GLOWSTONE) || b.isOf(Blocks.SHROOMLIGHT);
			if (isTorch && random.nextFloat() < torch || isLantern && random.nextFloat() < lantern) {
				world.breakBlock(p, false);
				out++;
			} else if (b.isIn(BlockTags.CAMPFIRES) && b.get(CampfireBlock.LIT) && random.nextFloat() < torch) {
				world.setBlockState(p, b.with(CampfireBlock.LIT, false));
				out++;
			} else {
				continue;
			}
			world.spawnParticles(ParticleTypes.LARGE_SMOKE, p.getX() + 0.5, p.getY() + 0.6, p.getZ() + 0.5, 6, 0.1, 0.1, 0.1, 0.01);
			world.playSound(null, p, SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.BLOCKS, 0.5F, 0.6F);
		}
		return out;
	}

	/** ¿Es una zona de la montaña donde la luz no funciona? (uno de cada cuatro trozos de 3x3 chunks). */
	public static boolean deadLightZone(BlockPos pos) {
		long h = (pos.getX() >> 6) * 341873128712L + (pos.getZ() >> 6) * 132897987541L;
		h ^= h >>> 33;
		return (h & 3) == 0;
	}

	private static void mountains(ServerWorld world, ServerPlayerEntity player, State s, long now, Random random) {
		if (deadLightZone(player.getBlockPos())) {
			lightsOut(world, player, s, now, 0.5F, 0.25F);
		}
		// el aire espeso: cuesta regenerar
		if (!player.isCreative()) {
			player.getHungerManager().setSaturationLevel(Math.max(0.0F, player.getHungerManager().getSaturationLevel() - 0.35F));
		}
		if (now >= s.nextBolt) {
			s.nextBolt = now + 300 + random.nextInt(500);
			fireBolt(world, player, random);
		}
	}

	/** Un rayo de fuego que deja un cráter de azufre (y el calor del juicio a quien pilla cerca). */
	public static BlockPos fireBolt(ServerWorld world, ServerPlayerEntity near, Random random) {
		int x = near.getBlockX() + random.nextInt(41) - 20;
		int z = near.getBlockZ() + random.nextInt(41) - 20;
		if (!world.isChunkLoaded(x >> 4, z >> 4)) {
			return null;
		}
		int y = world.getTopY(Heightmap.Type.MOTION_BLOCKING, x, z);
		BlockPos hit = new BlockPos(x, y, z);
		LightningEntity bolt = EntityType.LIGHTNING_BOLT.create(world);
		if (bolt != null) {
			bolt.refreshPositionAfterTeleport(Vec3d.ofBottomCenter(hit));
			world.spawnEntity(bolt);
		}
		for (BlockPos p : BlockPos.iterate(hit.add(-2, -2, -2), hit.add(2, 0, 2))) {
			double d = Math.sqrt(p.getSquaredDistance(hit));
			BlockState b = world.getBlockState(p);
			if (d > 2.3 || b.isAir() || !b.getFluidState().isEmpty() || b.getHardness(world, p) < 0) {
				continue;
			}
			if (d < 1.2 && p.getY() == hit.getY() - 1) {
				world.setBlockState(p, Blocks.AIR.getDefaultState());
			} else {
				world.setBlockState(p, ModBlocks.BRIMSTONE_BLOCK.getDefaultState());
			}
		}
		world.spawnParticles(ParticleTypes.FLAME, x + 0.5, y + 0.5, z + 0.5, 40, 1.0, 0.5, 1.0, 0.1);
		if (near.squaredDistanceTo(Vec3d.ofCenter(hit)) < 36) {
			Curses.curse(near, ModEffects.JUDGEMENT_HEAT);
		}
		return hit;
	}

	// ------------------------------------------------------------------ corrupción y visiones

	/** Un bloque de alrededor se vuelve piedra maldita. */
	public static boolean corruptNear(ServerWorld world, BlockPos pos, Random random) {
		for (int i = 0; i < 8; i++) {
			BlockPos p = pos.add(random.nextInt(11) - 5, random.nextInt(5) - 3, random.nextInt(11) - 5);
			BlockState b = world.getBlockState(p);
			if ((b.isOf(Blocks.NETHERRACK) || b.isOf(Blocks.BLACKSTONE) || b.isOf(Blocks.SOUL_SOIL) || b.isOf(ModBlocks.ASH_BLOCK))
					&& world.getBlockState(p.up()).isAir()) {
				world.setBlockState(p, ModBlocks.CURSED_NETHERRACK.getDefaultState());
				world.spawnParticles(ParticleTypes.SOUL, p.getX() + 0.5, p.getY() + 1.1, p.getZ() + 0.5, 6, 0.3, 0.1, 0.3, 0.01);
				return true;
			}
		}
		return false;
	}

	private static void visions(ServerWorld world, ServerPlayerEntity player, State s, long now, Random random, int corruption) {
		if (s.nextVision == 0) {
			s.nextVision = now + 1200 + random.nextInt(2400);
			return;
		}
		if (now < s.nextVision) {
			return;
		}
		s.nextVision = now + (long) ((2400 + random.nextInt(3600)) * (1.0 - corruption / 160.0));
		int kind = random.nextInt(10);
		if (kind < 4) {
			player.sendMessage(phrase(random), false);
			player.playSoundToPlayer(SoundEvents.AMBIENT_SOUL_SAND_VALLEY_ADDITIONS.value(), SoundCategory.AMBIENT, 1.0F, 0.5F);
		} else if (kind < 8) {
			// una silueta: a veces, hacia la Ciudadela
			Vec3d dir = null;
			if (now - s.lastHint > 3600 && random.nextBoolean()) {
				s.lastHint = now;
				BlockPos found = world.locateStructure(LANDMARKS, player.getBlockPos(), 40, false);
				if (found != null) {
					dir = Vec3d.of(found.subtract(player.getBlockPos())).multiply(1, 0, 1).normalize();
				}
			}
			if (dir == null || dir.lengthSquared() < 0.01) {
				double a = random.nextDouble() * Math.PI * 2;
				dir = new Vec3d(Math.cos(a), 0, Math.sin(a));
			}
			double d = 16 + random.nextInt(12);
			int x = (int) (player.getX() + dir.x * d);
			int z = (int) (player.getZ() + dir.z * d);
			int y = Math.max(player.getBlockY() - 6, Math.min(player.getBlockY() + 8, world.getTopY(Heightmap.Type.MOTION_BLOCKING, x, z)));
			ServerPlayNetworking.send(player, new HellPayloads.Vision(x + 0.5, y, z + 0.5));
		} else {
			// un susurro detrás
			Vec3d behind = player.getPos().subtract(player.getRotationVec(1.0F).multiply(3));
			world.playSound(null, behind.x, behind.y + 1, behind.z, SoundEvents.ENTITY_VEX_AMBIENT, SoundCategory.AMBIENT, 0.6F, 0.4F);
		}
	}

	/** Una frase bíblica deformada (con letras que se retuercen). */
	static Text phrase(Random random) {
		MutableText out = Text.literal("").formatted(Formatting.DARK_RED, Formatting.ITALIC);
		out.append(Text.literal("ẞ ").formatted(Formatting.OBFUSCATED));
		out.append(Text.translatable("message.evolvedwarfare.hell.vision." + random.nextInt(PHRASES)));
		out.append(Text.literal(" ᚱ").formatted(Formatting.OBFUSCATED));
		return out;
	}
}
