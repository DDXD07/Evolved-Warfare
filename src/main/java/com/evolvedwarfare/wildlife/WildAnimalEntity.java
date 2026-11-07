package com.evolvedwarfare.wildlife;

import java.util.EnumSet;
import java.util.List;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.ai.goal.AnimalMateGoal;
import net.minecraft.entity.ai.goal.EscapeDangerGoal;
import net.minecraft.entity.ai.goal.FleeEntityGoal;
import net.minecraft.entity.ai.goal.FollowParentGoal;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.entity.ai.goal.RevengeGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.ai.goal.TemptGoal;
import net.minecraft.entity.ai.goal.WanderAroundFarGoal;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ItemStackParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * Un animal salvaje de cualquiera de las 20 especies ({@link WildSpecies}): el tipo de entidad dice cuál es.
 * <ul>
 * <li><b>Presas</b>: huyen de los jugadores (de lejos si vas de pie, de muy cerca si vas agachado) y de quienes las
 * cazan; si les pegas, corren.</li>
 * <li><b>Neutrales</b>: van a lo suyo; si les pegas se defienden (en manada, toda la manada).</li>
 * <li><b>Cazadores</b>: cuando tienen hambre cazan a sus presas (también conejos, gallinas, ovejas o peces de vanilla).
 * El puma, además, acecha a los jugadores de noche; el oso ataca si te acercas demasiado sin agacharte.</li>
 * <li>En manada siguen a los suyos; pastan (bajan la cabeza); los carroñeros (mapache, oso, coyote, tejón) se comen la
 * comida que haya tirada; las aves bajan planeando y no se hacen daño al caer; patos y castores nadan; el coyote aúlla
 * de noche.</li>
 * <li>Se crían con su comida (que también los atrae) y las crías siguen a sus padres.</li>
 * </ul>
 */
public class WildAnimalEntity extends AnimalEntity {
	/** Lo que tarda en volver a tener hambre un cazador que ha comido (tics). */
	private static final int FED_TICKS = 6000;
	/** El estado que se manda a los clientes cuando pasta (como la oveja). */
	public static final byte GRAZE_STATUS = 10;
	public static final int GRAZE_TICKS = 40;

	private int fed;
	/** En el cliente: los tics que le quedan pastando (para bajar la cabeza). */
	public int grazeTicks;
	private int howlCooldown = 200;

	public WildAnimalEntity(EntityType<? extends AnimalEntity> type, World world) {
		super(type, world);
		this.fed = this.random.nextInt(2400);
	}

	private WildSpecies speciesCache;

	public WildSpecies species() {
		WildSpecies s = speciesCache;
		if (s == null) {
			s = speciesCache = Wildlife.of(getType());
		}
		return s;
	}

	// ------------------------------------------------------------------ cerebro

	@Override
	protected void initGoals() {
		WildSpecies s = species();
		goalSelector.add(0, new SwimGoal(this));
		if (s.role == WildSpecies.Role.PREY) {
			goalSelector.add(1, new EscapeDangerGoal(this, 1.7));
		} else {
			goalSelector.add(1, new MeleeAttackGoal(this, s.role == WildSpecies.Role.PREDATOR ? 1.35 : 1.25, true));
		}
		goalSelector.add(2, new AnimalMateGoal(this, 1.0));
		goalSelector.add(3, new TemptGoal(this, 1.1, stack -> species().isFood(stack), false));
		if (s.fleeDistance > 0) {
			float d = s.fleeDistance;
			goalSelector.add(3, new FleeEntityGoal<>(this, PlayerEntity.class,
					e -> !e.isSneaking() || e.squaredDistanceTo(this) < (d * d) / 9.0, d, 1.3, 1.7,
					e -> e instanceof PlayerEntity p && !p.isCreative() && !p.isSpectator()));
		}
		if (s.role == WildSpecies.Role.PREY) {
			goalSelector.add(3, new FleeEntityGoal<>(this, WildAnimalEntity.class, 10.0F, 1.3, 1.7,
					e -> e instanceof WildAnimalEntity w && w.species().hunts(species())));
		}
		goalSelector.add(4, new FollowParentGoal(this, 1.1));
		if (s.herd) {
			goalSelector.add(5, new HerdGoal(this));
		}
		if (s.scavenger) {
			goalSelector.add(5, new ScavengeGoal(this));
		}
		if (s.grazer) {
			goalSelector.add(6, new GrazeGoal(this));
		}
		goalSelector.add(7, new WanderAroundFarGoal(this, 1.0));
		goalSelector.add(8, new LookAtEntityGoal(this, PlayerEntity.class, 6.0F));
		goalSelector.add(9, new LookAroundGoal(this));
		if (s.role != WildSpecies.Role.PREY) {
			RevengeGoal revenge = new RevengeGoal(this);
			targetSelector.add(1, s.herd ? revenge.setGroupRevenge() : revenge);
		}
		if (s.role == WildSpecies.Role.PREDATOR) {
			targetSelector.add(2, new ActiveTargetGoal<>(this, LivingEntity.class, 10, true, false,
					e -> fed <= 0 && !isBaby() && species().hunts(e.getType())));
			if (s.nightHunter) {
				targetSelector.add(3, new ActiveTargetGoal<>(this, PlayerEntity.class, 20, true, false,
						e -> !isBaby() && getWorld().isNight() && !e.isSneaking()));
			}
			if (s.territorial) {
				targetSelector.add(3, new ActiveTargetGoal<>(this, PlayerEntity.class, 40, true, false,
						e -> !isBaby() && !e.isSneaking() && e.squaredDistanceTo(this) < 25.0));
			}
		}
	}

	@Override
	public boolean isBreedingItem(ItemStack stack) {
		return species().isFood(stack);
	}

	@Nullable
	@Override
	public PassiveEntity createChild(ServerWorld world, PassiveEntity entity) {
		return Wildlife.type(species()).create(world);
	}

	@Override
	public boolean onKilledOther(ServerWorld world, LivingEntity other) {
		// un cazador que caza come (y se cura)
		fed = FED_TICKS;
		heal(4.0F);
		return super.onKilledOther(world, other);
	}

	@Override
	public boolean tryAttack(net.minecraft.entity.Entity target) {
		boolean hit = super.tryAttack(target);
		if (hit && species() == WildSpecies.BROWN_BEAR) {
			playSound(SoundEvents.ENTITY_POLAR_BEAR_WARNING, 1.0F, 0.85F);
		}
		return hit;
	}

	// ------------------------------------------------------------------ cada tic

	@Override
	protected void mobTick() {
		super.mobTick();
		if (fed > 0) {
			fed--;
		}
		WildSpecies s = species();
		if (s == WildSpecies.COYOTE && getWorld().isNight() && --howlCooldown <= 0) {
			howlCooldown = 1200 + random.nextInt(2400);
			playSound(SoundEvents.ENTITY_WOLF_HOWL, 1.6F, 1.35F);
		}
	}

	@Override
	public void tickMovement() {
		super.tickMovement();
		WildSpecies s = species();
		if (getWorld().isClient && grazeTicks > 0) {
			grazeTicks--;
		}
		if (s.bird && !isOnGround() && !isTouchingWater()) {
			// planea al caer, como la gallina
			Vec3d v = getVelocity();
			if (v.y < 0.0) {
				setVelocity(v.multiply(1.0, 0.6, 1.0));
			}
		}
		if (s.swimmer && isTouchingWater()) {
			// flota y nada deprisa
			Vec3d v = getVelocity();
			if (getFluidHeight(net.minecraft.registry.tag.FluidTags.WATER) > 0.25) {
				setVelocity(v.x * 1.06, Math.max(v.y, 0.03), v.z * 1.06);
			}
		}
	}

	@Override
	public boolean handleFallDamage(float fallDistance, float damageMultiplier, DamageSource damageSource) {
		return !species().bird && super.handleFallDamage(fallDistance, damageMultiplier, damageSource);
	}

	@Override
	public void handleStatus(byte status) {
		if (status == GRAZE_STATUS) {
			grazeTicks = GRAZE_TICKS;
		} else {
			super.handleStatus(status);
		}
	}

	/** Cuánto baja la cabeza pastando (0 a 1), en el cliente. */
	public float grazeAmount(float tickDelta) {
		if (grazeTicks <= 0) {
			return 0.0F;
		}
		float t = grazeTicks - tickDelta;
		if (t > GRAZE_TICKS - 4) {
			return (GRAZE_TICKS - t) / 4.0F;
		}
		return t < 4 ? t / 4.0F : 1.0F;
	}

	@Override
	public void writeCustomDataToNbt(NbtCompound nbt) {
		super.writeCustomDataToNbt(nbt);
		nbt.putInt("Fed", fed);
	}

	@Override
	public void readCustomDataFromNbt(NbtCompound nbt) {
		super.readCustomDataFromNbt(nbt);
		fed = nbt.getInt("Fed");
	}

	// ------------------------------------------------------------------ sonidos

	@Override
	protected SoundEvent getAmbientSound() {
		return species().ambient;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return species().hurt;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return species().death;
	}

	@Override
	public float getSoundPitch() {
		return species().pitch * (isBaby() ? 1.4F : 1.0F) * (0.92F + random.nextFloat() * 0.16F);
	}

	@Override
	public int getMinAmbientSoundDelay() {
		return species().bird ? 160 : 260;
	}

	// ------------------------------------------------------------------ comportamientos propios

	/** En manada: si se aleja de los suyos, vuelve con el más cercano. */
	static class HerdGoal extends Goal {
		private final WildAnimalEntity mob;
		@Nullable
		private WildAnimalEntity mate;
		private int delay;

		HerdGoal(WildAnimalEntity mob) {
			this.mob = mob;
			setControls(EnumSet.of(Control.MOVE));
		}

		@Override
		public boolean canStart() {
			if (mob.isBaby() || --delay > 0) {
				return false;
			}
			delay = 40 + mob.getRandom().nextInt(40);
			List<WildAnimalEntity> near = mob.getWorld().getEntitiesByClass(WildAnimalEntity.class, mob.getBoundingBox().expand(24.0),
					e -> e != mob && e.getType() == mob.getType() && !e.isBaby());
			WildAnimalEntity best = null;
			double bestD = Double.MAX_VALUE;
			for (WildAnimalEntity e : near) {
				double d = e.squaredDistanceTo(mob);
				if (d < bestD) {
					bestD = d;
					best = e;
				}
			}
			if (best == null || bestD < 64.0) {
				return false;
			}
			mate = best;
			return true;
		}

		@Override
		public boolean shouldContinue() {
			return mate != null && mate.isAlive() && mob.squaredDistanceTo(mate) > 25.0 && !mob.getNavigation().isIdle();
		}

		@Override
		public void start() {
			if (mate != null) {
				mob.getNavigation().startMovingTo(mate, 1.05);
			}
		}

		@Override
		public void stop() {
			mate = null;
		}
	}

	/** Pasta: se para, baja la cabeza un par de segundos (y se cura un poco). */
	static class GrazeGoal extends Goal {
		private final WildAnimalEntity mob;
		private int timer;

		GrazeGoal(WildAnimalEntity mob) {
			this.mob = mob;
			setControls(EnumSet.of(Control.MOVE, Control.LOOK, Control.JUMP));
		}

		@Override
		public boolean canStart() {
			return mob.isOnGround() && mob.getRandom().nextInt(mob.isBaby() ? 600 : 300) == 0;
		}

		@Override
		public void start() {
			timer = GRAZE_TICKS;
			mob.getNavigation().stop();
			mob.getWorld().sendEntityStatus(mob, GRAZE_STATUS);
		}

		@Override
		public boolean shouldContinue() {
			return timer > 0;
		}

		@Override
		public void tick() {
			if (--timer == GRAZE_TICKS / 2) {
				mob.heal(1.0F);
				mob.playSound(SoundEvents.BLOCK_GRASS_STEP, 0.4F, 1.2F);
			}
		}
	}

	/** Carroñero: va a por la comida tirada en el suelo y se la come. */
	static class ScavengeGoal extends Goal {
		private final WildAnimalEntity mob;
		@Nullable
		private ItemEntity food;
		private int delay;

		ScavengeGoal(WildAnimalEntity mob) {
			this.mob = mob;
			setControls(EnumSet.of(Control.MOVE));
		}

		@Override
		public boolean canStart() {
			if (--delay > 0) {
				return false;
			}
			delay = 60;
			List<ItemEntity> items = mob.getWorld().getEntitiesByClass(ItemEntity.class, mob.getBoundingBox().expand(10.0),
					e -> e.getStack().get(net.minecraft.component.DataComponentTypes.FOOD) != null && !e.cannotPickup());
			food = items.isEmpty() ? null : items.get(0);
			return food != null;
		}

		@Override
		public boolean shouldContinue() {
			return food != null && food.isAlive() && mob.squaredDistanceTo(food) < 196.0;
		}

		@Override
		public void start() {
			if (food != null) {
				mob.getNavigation().startMovingTo(food, 1.15);
			}
		}

		@Override
		public void tick() {
			if (food == null) {
				return;
			}
			if (mob.squaredDistanceTo(food) < 2.5) {
				ItemStack stack = food.getStack();
				if (mob.getWorld() instanceof ServerWorld world) {
					world.spawnParticles(new ItemStackParticleEffect(ParticleTypes.ITEM, stack.copyWithCount(1)), food.getX(), food.getY() + 0.2,
							food.getZ(), 6, 0.1, 0.1, 0.1, 0.05);
				}
				stack.decrement(1);
				if (stack.isEmpty()) {
					food.discard();
				}
				mob.playSound(SoundEvents.ENTITY_GENERIC_EAT, 0.8F, 1.0F);
				mob.heal(3.0F);
				mob.fed = FED_TICKS;
				food = null;
			} else if (mob.getNavigation().isIdle()) {
				mob.getNavigation().startMovingTo(food, 1.15);
			}
		}

		@Override
		public void stop() {
			food = null;
		}
	}

	/** Lo que mide para el que la mira (las crías, la mitad). */
	public float renderScale() {
		return isBaby() ? 0.55F : 1.0F;
	}

	/** ¿Está corriendo? (para que el modelo agite más la cola o las alas) */
	public boolean running() {
		return getVelocity().horizontalLengthSquared() > 0.012;
	}
}
