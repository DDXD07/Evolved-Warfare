package com.evolvedwarfare.legendary;

import com.evolvedwarfare.hell.MiniBossEntity;
import java.util.EnumSet;
import net.minecraft.block.AbstractFireBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.SnowBlock;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.control.FlightMoveControl;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.goal.RevengeGoal;
import net.minecraft.entity.ai.pathing.BirdNavigation;
import net.minecraft.entity.ai.pathing.EntityNavigation;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * Un dragón (fase 101), de fuego o de hielo: vuela en círculos sobre su territorio; cuando ve a alguien, lo rodea por el
 * aire, cae en picado a morderle y, cada poco, se queda flotando y le echa el <b>aliento</b> (un chorro de 16 bloques
 * que quema o congela, y prende fuego o hiela el agua y nieva donde da). Con menos de un 60 % de vida también
 * <b>ruge</b> (empuja y aturde a los que tiene cerca) y con menos de un 30 % echa el aliento mucho más a menudo. Si
 * nadie se le acerca en mucho rato, se va (desaparece).
 */
public class DragonEntity extends MiniBossEntity implements Legendary {
	/** Lo que dura un chorro de aliento (ticks). */
	public static final int BREATH_TICKS = 50;
	public static final double BREATH_RANGE = 16.0;

	private final boolean ice;
	private int breath;
	private int dive;
	private int lonely;
	@Nullable
	private BlockPos home;

	public DragonEntity(EntityType<? extends HostileEntity> type, World world) {
		super(type, world, type == Legendaries.ICE_DRAGON ? BossBar.Color.BLUE : BossBar.Color.RED);
		this.ice = type == Legendaries.ICE_DRAGON;
		this.moveControl = new FlightMoveControl(this, 10, true);
		this.experiencePoints = 400;
		setNoGravity(true);
	}

	public static DefaultAttributeContainer.Builder createAttributes() {
		return attributes(500.0, 16.0, 14.0, 0.3).add(EntityAttributes.GENERIC_FLYING_SPEED, 0.6).add(EntityAttributes.GENERIC_FOLLOW_RANGE, 64.0);
	}

	public boolean ice() {
		return ice;
	}

	public void setHome(BlockPos home) {
		this.home = home.toImmutable();
	}

	@Override
	protected EntityNavigation createNavigation(World world) {
		BirdNavigation nav = new BirdNavigation(this, world);
		nav.setCanPathThroughDoors(false);
		nav.setCanSwim(false);
		return nav;
	}

	@Override
	protected void initGoals() {
		goalSelector.add(1, new DragonGoal(this));
		targetSelector.add(1, new RevengeGoal(this));
		targetSelector.add(2, new ActiveTargetGoal<>(this, PlayerEntity.class, false));
	}

	/** Lo que hace en el aire: rondar su territorio o cazar (rodear, picado y aliento). */
	static class DragonGoal extends Goal {
		private final DragonEntity dragon;

		DragonGoal(DragonEntity dragon) {
			this.dragon = dragon;
			setControls(EnumSet.of(Control.MOVE, Control.LOOK));
		}

		@Override
		public boolean canStart() {
			return true;
		}

		@Override
		public boolean shouldRunEveryTick() {
			return true;
		}

		@Override
		public void tick() {
			DragonEntity d = dragon;
			LivingEntity target = d.getTarget();
			if (target == null || !target.isAlive()) {
				// ronda su territorio, alto y despacio
				BlockPos center = d.home != null ? d.home : d.getBlockPos();
				if (d.age % 30 == 0 || !d.getMoveControl().isMoving()) {
					double a = d.age * 0.01;
					double x = center.getX() + Math.cos(a) * 28, z = center.getZ() + Math.sin(a) * 28;
					double y = d.getWorld().getTopY(Heightmap.Type.MOTION_BLOCKING, (int) x, (int) z) + 22;
					d.getMoveControl().moveTo(x, y, z, 0.7);
				}
				return;
			}
			d.getLookControl().lookAt(target, 40, 40);
			if (d.breath > 0) {
				// flota delante y algo por encima, y echa el aliento
				Vec3d away = d.getPos().subtract(target.getPos()).multiply(1, 0, 1);
				away = away.lengthSquared() < 1.0E-3 ? new Vec3d(1, 0, 0) : away.normalize();
				Vec3d spot = target.getPos().add(away.multiply(10)).add(0, 6, 0);
				d.getMoveControl().moveTo(spot.x, spot.y, spot.z, 0.6);
				return;
			}
			if (d.dive > 0) {
				d.dive--;
				d.getMoveControl().moveTo(target.getX(), target.getY() + 0.5, target.getZ(), 1.9);
				if (d.squaredDistanceTo(target) < 12.0) {
					d.tryAttack(target);
					d.dive = 0;
				}
				return;
			}
			double a = d.age * 0.03;
			double r = 14.0;
			double y = target.getY() + 9.0 + MathHelper.sin(d.age * 0.04F) * 2.0;
			d.getMoveControl().moveTo(target.getX() + Math.cos(a) * r, y, target.getZ() + Math.sin(a) * r, 1.1);
			if (d.random.nextInt(d.phase() == 1 ? 90 : 60) == 0) {
				d.dive = 50;
				d.playSound(SoundEvents.ENTITY_PHANTOM_SWOOP, 3.0F, 0.4F);
			}
		}
	}

	@Override
	protected int ability(ServerWorld world, LivingEntity target) {
		int phase = phase();
		if (phase >= 2 && random.nextInt(3) == 0 && squaredDistanceTo(target) < 15 * 15) {
			roar(world);
			return 100;
		}
		breath = BREATH_TICKS;
		world.playSound(null, getBlockPos(), SoundEvents.ENTITY_ENDER_DRAGON_GROWL, SoundCategory.HOSTILE, 4.0F, ice ? 1.3F : 0.9F);
		return phase == 3 ? 110 : 190;
	}

	/** El rugido: empuja y aturde a los que tiene cerca. */
	public void roar(ServerWorld world) {
		world.playSound(null, getBlockPos(), SoundEvents.ENTITY_ENDER_DRAGON_GROWL, SoundCategory.HOSTILE, 6.0F, 0.6F);
		for (PlayerEntity p : players(12)) {
			Vec3d push = p.getPos().subtract(getPos()).multiply(1, 0, 1);
			push = push.lengthSquared() < 1.0E-3 ? new Vec3d(1, 0, 0) : push.normalize();
			p.addVelocity(push.x * 1.6, 0.6, push.z * 1.6);
			p.velocityModified = true;
			p.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 60, 2), this);
			p.addStatusEffect(new StatusEffectInstance(StatusEffects.NAUSEA, 100, 0), this);
		}
		world.spawnParticles(ParticleTypes.SONIC_BOOM, getX(), getBodyY(0.6), getZ(), 1, 0, 0, 0, 0);
	}

	/** De dónde sale el aliento: la boca (delante y algo arriba). */
	public Vec3d mouth() {
		Vec3d look = Vec3d.fromPolar(0, getHeadYaw()).multiply(getWidth() * 0.75);
		return getPos().add(look.x, getHeight() * 0.75, look.z);
	}

	/** Un tick de aliento hacia el objetivo (público para las pruebas). */
	public void breathTick(ServerWorld world, LivingEntity target) {
		Vec3d from = mouth();
		Vec3d to = target.getPos().add(0, target.getHeight() * 0.5, 0);
		Vec3d dir = to.subtract(from);
		if (dir.lengthSquared() < 1.0E-4) {
			return;
		}
		dir = dir.normalize();
		ParticleEffect particle = ice ? ParticleTypes.SNOWFLAKE : ParticleTypes.FLAME;
		for (double s = 1.0; s <= BREATH_RANGE; s += 1.0) {
			Vec3d p = from.add(dir.multiply(s));
			double spread = 0.08 + s * 0.04;
			world.spawnParticles(particle, p.x, p.y, p.z, 3, spread, spread, spread, 0.01);
			if (!ice && (int) s % 4 == 0) {
				world.spawnParticles(ParticleTypes.LARGE_SMOKE, p.x, p.y, p.z, 1, spread, spread, spread, 0.01);
			}
		}
		if (age % 5 == 0) {
			DamageSource source = new DamageSource(world.getRegistryManager().get(RegistryKeys.DAMAGE_TYPE)
					.entryOf(ice ? Legendaries.DRAGON_ICE : Legendaries.DRAGON_FIRE), this, this);
			Box box = new Box(from, from.add(dir.multiply(BREATH_RANGE))).expand(2.5);
			for (LivingEntity e : world.getEntitiesByClass(LivingEntity.class, box, e -> e != this && e.isAlive())) {
				Vec3d v = e.getPos().add(0, e.getHeight() * 0.5, 0).subtract(from);
				double along = v.dotProduct(dir);
				if (along < 0 || along > BREATH_RANGE || v.subtract(dir.multiply(along)).length() > 1.2 + along * 0.12) {
					continue;
				}
				if (e.damage(source, 4.0F)) {
					if (ice) {
						e.setFrozenTicks(Math.min(e.getMinFreezeDamageTicks() + 120, e.getFrozenTicks() + 60));
						e.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 80, 2), this);
					} else {
						e.setOnFireFor(6.0F);
					}
				}
			}
		}
		// donde da: prende fuego, o hiela el agua y nieva
		if (age % 4 == 0 && Legendaries.griefing(world)) {
			BlockHitResult hit = world.raycast(new RaycastContext(from, from.add(dir.multiply(BREATH_RANGE + 4)), RaycastContext.ShapeType.COLLIDER,
					RaycastContext.FluidHandling.SOURCE_ONLY, this));
			if (hit.getType() == HitResult.Type.BLOCK) {
				scorch(world, hit.getBlockPos(), hit.getSide());
			}
		}
	}

	private void scorch(ServerWorld world, BlockPos pos, Direction side) {
		for (int k = 0; k < 3; k++) {
			BlockPos at = pos.add(random.nextInt(3) - 1, 0, random.nextInt(3) - 1);
			BlockState state = world.getBlockState(at);
			BlockPos above = at.up();
			if (ice) {
				if (state.isOf(Blocks.WATER) && state.getFluidState().isStill()) {
					world.setBlockState(at, Blocks.ICE.getDefaultState());
				} else if (state.isOf(Blocks.LAVA) && state.getFluidState().isStill()) {
					world.setBlockState(at, Blocks.OBSIDIAN.getDefaultState());
				} else if (world.getBlockState(above).isAir() && state.isSideSolidFullSquare(world, at, Direction.UP)) {
					world.setBlockState(above, Blocks.SNOW.getDefaultState().with(SnowBlock.LAYERS, 1 + random.nextInt(2)));
				}
			} else if (world.getBlockState(above).isAir()) {
				BlockState fire = AbstractFireBlock.getState(world, above);
				if (fire.canPlaceAt(world, above)) {
					world.setBlockState(above, fire);
				}
			}
		}
	}

	@Override
	protected void mobTick() {
		super.mobTick();
		if (!(getWorld() instanceof ServerWorld world)) {
			return;
		}
		LivingEntity target = getTarget();
		if (breath > 0) {
			breath--;
			if (target != null && target.isAlive()) {
				breathTick(world, target);
			}
			if (breath % 12 == 0) {
				world.playSound(null, getBlockPos(), ice ? SoundEvents.BLOCK_POWDER_SNOW_PLACE : SoundEvents.ITEM_FIRECHARGE_USE,
						SoundCategory.HOSTILE, 3.0F, ice ? 0.6F : 0.7F);
			}
		}
		if (age % 22 == 0 && !isOnGround()) {
			world.playSound(null, getBlockPos(), SoundEvents.ENTITY_ENDER_DRAGON_FLAP, SoundCategory.HOSTILE, 2.0F, ice ? 1.1F : 0.85F);
		}
		// se va si nadie se le acerca en 5 minutos
		if (age % 20 == 0) {
			boolean near = world.getClosestPlayer(this, 160.0) != null;
			lonely = near ? 0 : lonely + 20;
			if (lonely > 20 * 60 * 5) {
				discard();
			}
		}
	}

	@Override
	public boolean tryAttack(Entity target) {
		boolean hit = super.tryAttack(target);
		if (hit) {
			playSound(SoundEvents.ENTITY_EVOKER_FANGS_ATTACK, 2.0F, 0.6F);
			if (target instanceof LivingEntity l) {
				if (ice) {
					l.setFrozenTicks(Math.min(l.getMinFreezeDamageTicks() + 80, l.getFrozenTicks() + 80));
				} else {
					l.setOnFireFor(4.0F);
				}
			}
		}
		return hit;
	}

	@Override
	public void onDeath(DamageSource source) {
		super.onDeath(source);
		if (getWorld() instanceof ServerWorld world) {
			for (PlayerEntity p : world.getPlayers(p -> p.squaredDistanceTo(this) < 200 * 200)) {
				p.sendMessage(Text.translatable("message.evolvedwarfare.legendary.slain", getDisplayName()).formatted(Formatting.GOLD), false);
			}
		}
	}

	@Override
	public boolean canFreeze() {
		return !ice;
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	protected void fall(double heightDifference, boolean onGround, BlockState state, BlockPos landedPosition) {
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return SoundEvents.ENTITY_ENDER_DRAGON_AMBIENT;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.ENTITY_ENDER_DRAGON_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.ENTITY_ENDER_DRAGON_DEATH;
	}

	@Override
	protected float getSoundVolume() {
		return 4.0F;
	}

	@Override
	public float getSoundPitch() {
		return ice ? 1.25F : 0.95F;
	}

	@Override
	public void writeCustomDataToNbt(NbtCompound nbt) {
		super.writeCustomDataToNbt(nbt);
		if (home != null) {
			nbt.putLong("Home", home.asLong());
		}
	}

	@Override
	public void readCustomDataFromNbt(NbtCompound nbt) {
		super.readCustomDataFromNbt(nbt);
		if (nbt.contains("Home")) {
			home = BlockPos.fromLong(nbt.getLong("Home"));
		}
	}
}
