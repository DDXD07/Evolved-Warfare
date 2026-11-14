package com.evolvedwarfare.world.dungeon;

import com.evolvedwarfare.EvolvedWarfare;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.loot.LootTable;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * El Corazón de la mazmorra. Duerme hasta que se acerca alguien (a 10 bloques); entonces despierta al jefe encima, cierra
 * todas las rejas de alrededor y lleva la pelea: la barra de vida, los poderes cada pocos segundos, la furia a media vida
 * y que el jefe no se aleje de su arena. Si todos se van (o mueren) un buen rato, el jefe desaparece y la mazmorra vuelve
 * a dormirse (con las rejas abiertas). Cuando el jefe cae, abre las rejas y se convierte en el cofre del botín.
 */
public class DungeonHeartBlockEntity extends BlockEntity {
	public static final int WAKE_RANGE = 10;
	public static final int ARENA = 24;
	public static final int LEAVE_TICKS = 600;
	private String dungeon = "";
	/** 0 dormido, 1 peleando, 2 superada. */
	private int stage;
	@Nullable
	private UUID boss;
	private final List<BlockPos> gates = new ArrayList<>();
	private int powerTimer = 80, missing, absent, leapTicks = -1;
	private boolean enraged;
	@Nullable
	private ServerBossBar bar;

	public DungeonHeartBlockEntity(BlockPos pos, BlockState state) {
		super(Dungeons.HEART_ENTITY, pos, state);
	}

	public String dungeon() {
		return dungeon;
	}

	public void setDungeon(String dungeon) {
		this.dungeon = dungeon;
		markDirty();
	}

	public int stage() {
		return stage;
	}

	@Nullable
	public UUID bossId() {
		return boss;
	}

	public List<BlockPos> gates() {
		return gates;
	}

	public static void tick(World world, BlockPos pos, BlockState state, DungeonHeartBlockEntity heart) {
		if (!(world instanceof ServerWorld sw) || heart.dungeon.isEmpty() || Bosses.of(heart.dungeon) == null) {
			return;
		}
		if (heart.stage == 0) {
			if ((world.getTime() + pos.asLong()) % 10 == 0 && heart.someoneInArena(sw)) {
				heart.start(sw);
			}
		} else if (heart.stage == 1) {
			heart.fight(sw);
		}
	}

	/** Los jugadores que pelean (vivos, ni creativos ni espectadores) a esa distancia. */
	public List<ServerPlayerEntity> near(ServerWorld world, double range, double height) {
		Vec3d c = pos.toCenterPos();
		List<ServerPlayerEntity> out = new ArrayList<>();
		for (ServerPlayerEntity p : world.getPlayers()) {
			if (p.isAlive() && !p.isSpectator() && !p.isCreative() && Math.abs(p.getY() - c.y) <= height
					&& p.squaredDistanceTo(c.x, p.getY(), c.z) <= range * range) {
				out.add(p);
			}
		}
		return out;
	}

	/** ¿Hay alguien ya dentro de la arena? (a 10 bloques y a la altura del suelo del Corazón: de 2,5 por debajo a 4 por encima). */
	public boolean someoneInArena(ServerWorld world) {
		for (ServerPlayerEntity p : near(world, WAKE_RANGE, 6)) {
			double dy = p.getY() - pos.getY();
			if (dy >= -2.5 && dy <= 4) {
				return true;
			}
		}
		return false;
	}

	/** Despierta al jefe y cierra las rejas. */
	public void start(ServerWorld world) {
		Bosses.Boss spec = Bosses.of(dungeon);
		if (spec == null) {
			return;
		}
		MobEntity mob = spec.spawn(world, pos.up(), dungeon);
		if (mob == null) {
			return;
		}
		mob.addCommandTag(Dungeons.BOSS_TAG);
		mob.addCommandTag(Dungeons.HEART_TAG + pos.asLong());
		boss = mob.getUuid();
		if (stage == 0) {
			setGates(world, true);
			Text name = mob.getDisplayName();
			for (ServerPlayerEntity p : near(world, 48, 24)) {
				p.sendMessage(Text.translatable("message.evolvedwarfare.dungeon.awaken", name).formatted(Formatting.DARK_RED), true);
			}
			world.playSound(null, pos, SoundEvents.ENTITY_WITHER_SPAWN, SoundCategory.HOSTILE, 0.6F, 0.8F);
			world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, pos.getX() + 0.5, pos.getY() + 1.5, pos.getZ() + 0.5, 60, 0.6, 1.0, 0.6, 0.05);
		}
		stage = 1;
		missing = 0;
		absent = 0;
		enraged = false;
		powerTimer = 80;
		markDirty();
	}

	private void setGates(ServerWorld world, boolean closed) {
		if (closed) {
			gates.clear();
			for (BlockPos p : BlockPos.iterate(pos.add(-ARENA - 4, -14, -ARENA - 4), pos.add(ARENA + 4, 14, ARENA + 4))) {
				if (world.getBlockState(p).isOf(Dungeons.GATE)) {
					gates.add(p.toImmutable());
				}
			}
		}
		for (BlockPos p : gates) {
			BlockState s = world.getBlockState(p);
			if (s.isOf(Dungeons.GATE) && s.get(DungeonGateBlock.CLOSED) != closed) {
				world.setBlockState(p, s.with(DungeonGateBlock.CLOSED, closed));
			}
		}
		if (!gates.isEmpty()) {
			world.playSound(null, gates.get(0), closed ? SoundEvents.BLOCK_IRON_DOOR_CLOSE : SoundEvents.BLOCK_IRON_DOOR_OPEN,
					SoundCategory.BLOCKS, 1.0F, 0.6F);
		}
	}

	@Nullable
	public MobEntity bossEntity(ServerWorld world) {
		if (boss == null) {
			return null;
		}
		Entity e = world.getEntity(boss);
		return e instanceof MobEntity m ? m : null;
	}

	private void fight(ServerWorld world) {
		Bosses.Boss spec = Bosses.of(dungeon);
		MobEntity mob = bossEntity(world);
		List<ServerPlayerEntity> fighters = near(world, 48, 24);
		// si no queda nadie un rato, la mazmorra vuelve a dormirse
		absent = fighters.isEmpty() ? absent + 1 : 0;
		if (absent > LEAVE_TICKS) {
			reset(world);
			return;
		}
		if (mob == null || mob.isRemoved()) {
			if (++missing > 200 && !fighters.isEmpty()) {
				start(world);
			}
			return;
		}
		missing = 0;
		if (!mob.isAlive()) {
			return;
		}
		// la barra de vida
		if (bar == null) {
			bar = new ServerBossBar(mob.getDisplayName(), BossBar.Color.RED, BossBar.Style.NOTCHED_10);
		}
		bar.setPercent(mob.getHealth() / mob.getMaxHealth());
		for (ServerPlayerEntity p : List.copyOf(bar.getPlayers())) {
			if (!fighters.contains(p)) {
				bar.removePlayer(p);
			}
		}
		for (ServerPlayerEntity p : fighters) {
			bar.addPlayer(p);
		}
		// que no se vaya de su arena (ni se quede atascado fuera)
		Vec3d home = Vec3d.ofBottomCenter(pos.up());
		if (mob.squaredDistanceTo(home) > ARENA * ARENA || mob.getY() < pos.getY() - 8) {
			mob.requestTeleport(home.x, home.y, home.z);
			mob.setVelocity(Vec3d.ZERO);
		}
		// a por el jugador más cercano
		if (world.getTime() % 20 == 0 && (!(mob.getTarget() instanceof ServerPlayerEntity t) || !t.isAlive() || !fighters.contains(t))) {
			fighters.stream().min(Comparator.comparingDouble(mob::squaredDistanceTo)).ifPresent(mob::setTarget);
		}
		// el aterrizaje del salto
		if (leapTicks >= 0 && ++leapTicks > 6 && mob.isOnGround()) {
			leapTicks = -1;
			Bosses.Power.SHOCKWAVE.use(world, mob, mob.getTarget(), spec, this);
		}
		// la furia, a media vida
		if (!enraged && mob.getHealth() < mob.getMaxHealth() * 0.5F) {
			enraged = true;
			mob.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, -1, 0));
			mob.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, -1, 0));
			bar.setColor(BossBar.Color.PURPLE);
			for (ServerPlayerEntity p : fighters) {
				p.sendMessage(Text.translatable("message.evolvedwarfare.dungeon.rage", mob.getDisplayName()).formatted(Formatting.RED), true);
			}
			world.playSound(null, mob.getBlockPos(), SoundEvents.ENTITY_RAVAGER_ROAR, SoundCategory.HOSTILE, 1.2F, 0.7F);
			if (mob.getTarget() != null) {
				Bosses.Power.SUMMON.use(world, mob, mob.getTarget(), spec, this);
			}
		}
		// los poderes
		LivingEntity target = mob.getTarget();
		if (target != null && target.isAlive() && --powerTimer <= 0) {
			Bosses.Power power = spec.powers().get(world.random.nextInt(spec.powers().size()));
			power.use(world, mob, target, spec, this);
			powerTimer = (enraged ? 70 : 110) + world.random.nextInt(50);
		}
	}

	void leaped() {
		leapTicks = 0;
	}

	public boolean enraged() {
		return enraged;
	}

	/** Todos se fueron: el jefe se va, las rejas se abren y vuelve a dormir. */
	public void reset(ServerWorld world) {
		MobEntity mob = bossEntity(world);
		if (mob != null) {
			mob.discard();
		}
		setGates(world, false);
		clearBar();
		stage = 0;
		boss = null;
		absent = 0;
		markDirty();
	}

	private void clearBar() {
		if (bar != null) {
			bar.clearPlayers();
			bar = null;
		}
	}

	/** El jefe cayó: rejas abiertas, experiencia y el Corazón se vuelve el cofre del botín. */
	public void finish(ServerWorld world) {
		if (stage != 1) {
			return;
		}
		stage = 2;
		setGates(world, false);
		clearBar();
		Text name = Text.translatable("boss.evolvedwarfare." + dungeon);
		Text place = Text.translatable("dungeon.evolvedwarfare." + dungeon);
		for (ServerPlayerEntity p : near(world, 64, 32)) {
			p.sendMessage(Text.translatable("message.evolvedwarfare.dungeon.cleared", name, place).formatted(Formatting.GOLD), false);
		}
		Vec3d c = pos.toCenterPos();
		world.playSound(null, pos, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.PLAYERS, 1.0F, 1.0F);
		world.spawnParticles(ParticleTypes.TOTEM_OF_UNDYING, c.x, c.y + 1, c.z, 120, 0.8, 1.2, 0.8, 0.3);
		ExperienceOrbEntity.spawn(world, c.add(0, 1, 0), 500);
		String id = dungeon;
		world.setBlockState(pos, Blocks.CHEST.getDefaultState().with(ChestBlock.FACING, Direction.NORTH));
		if (world.getBlockEntity(pos) instanceof ChestBlockEntity chest) {
			chest.setLootTable(RegistryKey.of(RegistryKeys.LOOT_TABLE, EvolvedWarfare.id("chests/dungeon/" + id + "/boss")), world.random.nextLong());
		}
	}

	/** Lo llama el evento de muerte: el jefe lleva apuntado dónde está su Corazón. */
	public static void bossDied(ServerWorld world, LivingEntity entity) {
		for (String tag : entity.getCommandTags()) {
			if (tag.startsWith(Dungeons.HEART_TAG)) {
				try {
					BlockPos p = BlockPos.fromLong(Long.parseLong(tag.substring(Dungeons.HEART_TAG.length())));
					if (world.getBlockEntity(p) instanceof DungeonHeartBlockEntity heart && entity.getUuid().equals(heart.boss)) {
						heart.finish(world);
					}
				} catch (NumberFormatException ignored) {
					// etiqueta rota
				}
			}
		}
	}

	@Override
	public void markRemoved() {
		super.markRemoved();
		clearBar();
	}

	@Override
	protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		super.readNbt(nbt, registries);
		dungeon = nbt.getString("Dungeon");
		stage = nbt.getInt("Stage");
		boss = nbt.containsUuid("Boss") ? nbt.getUuid("Boss") : null;
		enraged = nbt.getBoolean("Enraged");
		gates.clear();
		for (long l : nbt.getLongArray("Gates")) {
			gates.add(BlockPos.fromLong(l));
		}
	}

	@Override
	protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		super.writeNbt(nbt, registries);
		nbt.putString("Dungeon", dungeon);
		nbt.putInt("Stage", stage);
		if (boss != null) {
			nbt.putUuid("Boss", boss);
		}
		nbt.putBoolean("Enraged", enraged);
		nbt.putLongArray("Gates", gates.stream().mapToLong(BlockPos::asLong).toArray());
	}
}
