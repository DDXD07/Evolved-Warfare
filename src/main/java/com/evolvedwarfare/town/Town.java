package com.evolvedwarfare.town;

import java.util.List;
import net.minecraft.block.BedBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.enums.BedPart;
import net.minecraft.entity.ai.brain.MemoryModuleType;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.random.Random;
import org.jetbrains.annotations.Nullable;

/**
 * La vida en el pueblo, lo común a todos los NPCs: elegir oficio y carácter, buscar una cama libre si no la tienen
 * (campamentos, torres, los que vienen de una aldea) y conocer a sus vecinos (la familia de su casa, los demás según su
 * carácter).
 */
public final class Town {
	/** Hasta dónde buscan cama libre (desde donde viven) y a sus vecinos. */
	public static final int BED_RADIUS = 12;
	public static final double NEIGHBORS = 48.0;

	private Town() {
	}

	/** Oficio (si no lo trae) y carácter al azar, una sola vez. */
	public static void roll(Townsfolk t, Random random, @Nullable NpcJob job) {
		NpcProfile p = t.ewProfile();
		if (p.rolled) {
			return;
		}
		p.job = job != null ? job : NpcJob.randomResident(random);
		p.personality = Personality.random(random);
		p.rolled = true;
		t.ewProfileChanged();
	}

	/** Cada pocos segundos, hasta que tenga cama (si le toca) y conozca a sus vecinos. */
	public static void tick(Townsfolk t, ServerWorld world) {
		MobEntity self = t.ewSelf();
		NpcProfile p = t.ewProfile();
		if (!p.rolled) {
			roll(t, world.random, t.ewBirthJob());
		}
		if ((self.age + self.getId()) % 100 != 0) {
			return;
		}
		if (p.bed != null && !(world.getBlockState(p.bed).getBlock() instanceof BedBlock) && world.isChunkLoaded(p.bed)) {
			// le han quitado la cama
			p.bed = null;
			p.bedSearched = false;
		}
		if (p.bed == null && !p.bedSearched && !t.ewHired()) {
			p.bedSearched = true;
			p.bed = freeBed(world, self.getBlockPos(), BED_RADIUS, self);
		}
		if (!p.met && self.age > 40) {
			p.met = true;
			meet(t, world);
		}
	}

	/** Se conocen: la familia (misma casa) se quiere mucho; los demás del pueblo, según su carácter y un poco de suerte. */
	public static void meet(Townsfolk t, ServerWorld world) {
		MobEntity self = t.ewSelf();
		NpcProfile p = t.ewProfile();
		List<MobEntity> near = world.getEntitiesByClass(MobEntity.class, self.getBoundingBox().expand(NEIGHBORS),
				e -> e != self && e instanceof Townsfolk && e.isAlive());
		for (MobEntity e : near) {
			NpcProfile o = ((Townsfolk) e).ewProfile();
			boolean sameTown = p.community != 0 && p.community == o.community;
			boolean close = e.squaredDistanceTo(self) < 24 * 24;
			if (!sameTown && !close) {
				continue;
			}
			if (p.household != 0 && p.household == o.household) {
				p.addFamily(e.getUuid());
				o.addFamily(self.getUuid());
				continue;
			}
			if (p.knows(e.getUuid())) {
				continue;
			}
			int base = p.personality.chemistry(o.personality) + world.random.nextInt(31) - 10;
			if (p.job == o.job) {
				base += 8;
			}
			p.setAffinity(e.getUuid(), base);
			if (!o.knows(self.getUuid())) {
				o.setAffinity(self.getUuid(), base + world.random.nextInt(11) - 5);
			}
		}
	}

	/** La cabecera de la cama libre más cercana (que no sea de otro NPC ni de un aldeano), o null. */
	@Nullable
	public static BlockPos freeBed(ServerWorld world, BlockPos around, int radius, @Nullable MobEntity self) {
		BlockPos best = null;
		double bestD = Double.MAX_VALUE;
		for (BlockPos pos : BlockPos.iterate(around.add(-radius, -4, -radius), around.add(radius, 4, radius))) {
			BlockState s = world.getBlockState(pos);
			if (!(s.getBlock() instanceof BedBlock) || s.get(BedBlock.PART) != BedPart.HEAD || s.get(BedBlock.OCCUPIED)) {
				continue;
			}
			double d = pos.getSquaredDistance(around);
			if (d < bestD && !taken(world, pos, self)) {
				bestD = d;
				best = pos.toImmutable();
			}
		}
		return best;
	}

	/** ¿Es la cama de alguien (otro NPC o un aldeano)? */
	public static boolean taken(ServerWorld world, BlockPos head, @Nullable MobEntity self) {
		Box box = new Box(head).expand(64.0);
		for (MobEntity e : world.getEntitiesByClass(MobEntity.class, box, e -> e != self && e.isAlive())) {
			if (e instanceof Townsfolk t && head.equals(t.ewProfile().bed)) {
				return true;
			}
			if (e instanceof VillagerEntity v && v.getBrain().getOptionalRegisteredMemory(MemoryModuleType.HOME)
					.filter(g -> g.pos().equals(head)).isPresent()) {
				return true;
			}
		}
		return false;
	}
}
