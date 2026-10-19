package com.evolvedwarfare.work;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.passive.ChickenEntity;
import net.minecraft.entity.passive.CowEntity;
import net.minecraft.entity.passive.GoatEntity;
import net.minecraft.entity.passive.PigEntity;
import net.minecraft.entity.passive.RabbitEntity;
import net.minecraft.entity.passive.SheepEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Box;
import org.jetbrains.annotations.Nullable;

/**
 * Pastor y criador (zona 17x17). Por orden de prioridad:
 * <ol>
 *   <li>recoge del suelo lo que sueltan sus animales (lana, huevos, plumas, carne...),</li>
 *   <li>pastor: esquila las ovejas (con tijeras) y ordeña vacas y cabras (con cubos de los suministros),</li>
 *   <li>si hay más de {@value #CAP} adultos de una especie, sacrifica uno (nunca a los que tienen nombre o van atados),</li>
 *   <li>si hay menos de {@value #CAP}, les da de comer (de los suministros) para que críen.</li>
 * </ol>
 */
public class LivestockJob implements Job {
	private static final int CAP = 8;
	private static final long MILK_COOLDOWN = 2400;
	private static final Predicate<ItemStack> BUCKET = s -> s.isOf(Items.BUCKET);

	private final List<Class<? extends AnimalEntity>> species;
	private final boolean shepherd;
	private final Predicate<ItemStack> loot;
	private final Map<UUID, Long> milked = new HashMap<>();

	private LivestockJob(List<Class<? extends AnimalEntity>> species, boolean shepherd, Predicate<ItemStack> loot) {
		this.species = species;
		this.shepherd = shepherd;
		this.loot = loot;
	}

	public static LivestockJob shepherd() {
		List<Class<? extends AnimalEntity>> species = List.of(SheepEntity.class, CowEntity.class, GoatEntity.class);
		return new LivestockJob(species, true, s -> s.isIn(ItemTags.WOOL) || s.isOf(Items.LEATHER)
				|| s.isOf(Items.BEEF) || s.isOf(Items.MUTTON));
	}

	public static LivestockJob breeder() {
		List<Class<? extends AnimalEntity>> species = List.of(ChickenEntity.class, PigEntity.class, RabbitEntity.class);
		return new LivestockJob(species, false, s -> s.isOf(Items.EGG) || s.isOf(Items.FEATHER) || s.isOf(Items.CHICKEN)
				|| s.isOf(Items.PORKCHOP) || s.isOf(Items.RABBIT) || s.isOf(Items.RABBIT_HIDE) || s.isOf(Items.RABBIT_FOOT));
	}

	private int kindOf(AnimalEntity animal) {
		for (int i = 0; i < species.size(); i++) {
			if (species.get(i).isInstance(animal)) {
				return i;
			}
		}
		return -1;
	}

	@Override
	@Nullable
	public WorkAction plan(WorkContext ctx) {
		if (ctx.outputFull()) {
			return ctx.idle(WorkStatus.STORAGE_FULL);
		}
		Box box = ctx.areaBox(0.0);
		ItemEntity item = Jobs.nearestItem(ctx, box.expand(2.0), loot);
		if (item != null) {
			return Jobs.pickup(item);
		}
		List<AnimalEntity> animals = ctx.world.getEntitiesByClass(AnimalEntity.class, box,
				a -> a.isAlive() && kindOf(a) >= 0 && !ctx.ignored(a));
		if (animals.isEmpty()) {
			return ctx.idle(WorkStatus.NOTHING_HERE);
		}
		long now = ctx.world.getTime();
		milked.values().removeIf(until -> until < now);

		if (shepherd) {
			if (ctx.hasTool()) {
				AnimalEntity sheep = nearest(ctx, animals, a -> a instanceof SheepEntity s && !s.isBaby() && s.isShearable());
				if (sheep instanceof SheepEntity target) {
					return WorkAction.atEntity(target, ctx.ticks(30), c -> shear(c, target));
				}
			}
			if (ctx.site.count(BUCKET, true) > 0) {
				AnimalEntity cow = nearest(ctx, animals, a -> (a instanceof CowEntity || a instanceof GoatEntity)
						&& !a.isBaby() && !milked.containsKey(a.getUuid()));
				if (cow != null) {
					return WorkAction.atEntity(cow, ctx.ticks(25), c -> milk(c, cow));
				}
			}
		}

		List<List<AnimalEntity>> adults = new ArrayList<>();
		int[] total = new int[species.size()];
		for (int i = 0; i < species.size(); i++) {
			adults.add(new ArrayList<>());
		}
		for (AnimalEntity animal : animals) {
			int kind = kindOf(animal);
			total[kind]++;
			if (!animal.isBaby()) {
				adults.get(kind).add(animal);
			}
		}

		// demasiados: sacrifica uno
		for (List<AnimalEntity> group : adults) {
			if (group.size() > CAP) {
				for (AnimalEntity animal : group) {
					if (!animal.hasCustomName() && !animal.isLeashed()) {
						return WorkAction.atEntity(animal, ctx.ticks(20), c -> cull(c, animal));
					}
				}
			}
		}

		// pocos: les da de comer para que críen
		boolean hungry = false;
		for (int kind = 0; kind < species.size(); kind++) {
			if (total[kind] >= CAP) {
				continue;
			}
			List<AnimalEntity> ready = new ArrayList<>();
			int inLove = 0;
			for (AnimalEntity animal : adults.get(kind)) {
				if (animal.isInLove()) {
					inLove++;
				} else if (animal.getBreedingAge() == 0 && animal.canEat()) {
					ready.add(animal);
				}
			}
			if (ready.isEmpty() || ready.size() + inLove < 2) {
				continue;
			}
			AnimalEntity first = ready.get(0);
			if (ctx.site.find(first::isBreedingItem, true) == null) {
				hungry = true;
				continue;
			}
			AnimalEntity target = nearest(ctx, ready, a -> true);
			if (target != null) {
				return WorkAction.atEntity(target, ctx.ticks(15), c -> feed(c, target));
			}
		}
		return ctx.idle(hungry ? WorkStatus.NO_SUPPLIES : WorkStatus.WAITING);
	}

	@Nullable
	private static AnimalEntity nearest(WorkContext ctx, List<AnimalEntity> animals, Predicate<AnimalEntity> filter) {
		AnimalEntity best = null;
		double bestDistance = Double.MAX_VALUE;
		for (AnimalEntity animal : animals) {
			if (!filter.test(animal)) {
				continue;
			}
			double d = ctx.worker.squaredDistanceTo(animal);
			if (d < bestDistance) {
				bestDistance = d;
				best = animal;
			}
		}
		return best;
	}

	private static boolean shear(WorkContext ctx, SheepEntity sheep) {
		if (!sheep.isAlive() || !sheep.isShearable() || !ctx.hasTool()) {
			return false;
		}
		sheep.sheared(SoundCategory.NEUTRAL);
		ctx.damageTool(1);
		return true;
	}

	private boolean milk(WorkContext ctx, AnimalEntity animal) {
		if (!animal.isAlive() || animal.isBaby() || !ctx.site.consume(BUCKET, 1, true)) {
			return false;
		}
		ctx.store(new ItemStack(Items.MILK_BUCKET));
		milked.put(animal.getUuid(), ctx.world.getTime() + MILK_COOLDOWN);
		ctx.sound(animal.getBlockPos(), animal instanceof GoatEntity ? SoundEvents.ENTITY_GOAT_MILK : SoundEvents.ENTITY_COW_MILK, 1.0F, 1.0F);
		return true;
	}

	private static boolean cull(WorkContext ctx, AnimalEntity animal) {
		if (!animal.isAlive()) {
			return false;
		}
		ctx.worker.swingHand(Hand.MAIN_HAND);
		animal.damage(ctx.world.getDamageSources().mobAttack(ctx.worker), 1000.0F);
		return true;
	}

	private static boolean feed(WorkContext ctx, AnimalEntity animal) {
		if (!animal.isAlive() || animal.isBaby() || animal.getBreedingAge() != 0 || !animal.canEat() || animal.isInLove()) {
			return false;
		}
		if (!ctx.site.consume(animal::isBreedingItem, 1, true)) {
			return false;
		}
		animal.lovePlayer(null);
		return true;
	}
}
