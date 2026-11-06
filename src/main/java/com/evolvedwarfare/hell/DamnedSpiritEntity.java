package com.evolvedwarfare.hell;

import com.evolvedwarfare.registry.ModItems;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.block.BlockState;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.ai.control.FlightMoveControl;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.ai.pathing.BirdNavigation;
import net.minecraft.entity.ai.pathing.EntityNavigation;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * Espíritu de los condenados: no hace daño, pero al tocarte te roba algo del inventario y huye a la lava más cercana para
 * tirarlo. Si lo matas antes, suelta lo robado; si no encuentra lava, a los 30 s lo deja caer. Con sal bendita (clic
 * derecho) se le purifica: descansa, devuelve lo robado y te quita algo de corrupción.
 */
public class DamnedSpiritEntity extends DamnedBase {
	private ItemStack stolen = ItemStack.EMPTY;
	private int carried;
	@Nullable
	private BlockPos lava;

	public DamnedSpiritEntity(EntityType<? extends HostileEntity> type, World world) {
		super(type, world);
		this.moveControl = new FlightMoveControl(this, 10, true);
		this.experiencePoints = 4;
		setNoGravity(true);
	}

	public static DefaultAttributeContainer.Builder createAttributes() {
		return HostileEntity.createHostileAttributes()
				.add(EntityAttributes.GENERIC_MAX_HEALTH, 12.0)
				.add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 0.0)
				.add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.3)
				.add(EntityAttributes.GENERIC_FLYING_SPEED, 0.55)
				.add(EntityAttributes.GENERIC_FOLLOW_RANGE, 20.0);
	}

	@Override
	protected EntityNavigation createNavigation(World world) {
		BirdNavigation nav = new BirdNavigation(this, world);
		nav.setCanPathThroughDoors(false);
		return nav;
	}

	@Override
	protected void initGoals() {
		targetSelector.add(2, new ActiveTargetGoal<>(this, PlayerEntity.class, true));
	}

	public ItemStack stolen() {
		return stolen;
	}

	/** Le roba algo (al azar del inventario, sin la armadura) a ese jugador; devuelve lo robado. */
	public ItemStack steal(PlayerEntity player) {
		List<Integer> slots = new ArrayList<>();
		for (int i = 0; i < player.getInventory().main.size(); i++) {
			if (!player.getInventory().main.get(i).isEmpty()) {
				slots.add(i);
			}
		}
		if (slots.isEmpty()) {
			return ItemStack.EMPTY;
		}
		int slot = slots.get(random.nextInt(slots.size()));
		stolen = player.getInventory().main.get(slot).copy();
		player.getInventory().main.set(slot, ItemStack.EMPTY);
		equipStack(net.minecraft.entity.EquipmentSlot.MAINHAND, stolen.copyWithCount(1));
		setEquipmentDropChance(net.minecraft.entity.EquipmentSlot.MAINHAND, 0.0F);
		carried = 0;
		lava = findLava();
		if (player instanceof ServerPlayerEntity sp) {
			sp.sendMessage(Text.translatable("message.evolvedwarfare.spirit.stole", stolen.getName()).formatted(Formatting.DARK_AQUA), true);
		}
		getWorld().playSound(null, getBlockPos(), SoundEvents.ENTITY_ALLAY_ITEM_TAKEN, SoundCategory.HOSTILE, 1.0F, 0.5F);
		return stolen;
	}

	@Nullable
	private BlockPos findLava() {
		BlockPos at = getBlockPos();
		BlockPos best = null;
		double bestD = Double.MAX_VALUE;
		for (BlockPos p : BlockPos.iterate(at.add(-16, -8, -16), at.add(16, 4, 16))) {
			if (getWorld().getFluidState(p).isIn(FluidTags.LAVA) && getWorld().getBlockState(p.up()).isAir()) {
				double d = p.getSquaredDistance(at);
				if (d < bestD) {
					bestD = d;
					best = p.toImmutable();
				}
			}
		}
		return best;
	}

	@Override
	protected void mobTick() {
		super.mobTick();
		if (stolen.isEmpty()) {
			if (getTarget() instanceof PlayerEntity p && p.isAlive()) {
				getMoveControl().moveTo(p.getX(), p.getEyeY() - 0.5, p.getZ(), 1.0);
				if (squaredDistanceTo(p) < 2.0 && !p.isCreative()) {
					steal(p);
				}
			}
			return;
		}
		carried++;
		if (lava != null) {
			getMoveControl().moveTo(lava.getX() + 0.5, lava.getY() + 2.5, lava.getZ() + 0.5, 1.4);
			if (squaredDistanceTo(lava.getX() + 0.5, lava.getY() + 2.5, lava.getZ() + 0.5) < 2.5) {
				// lo tira a la lava
				ItemEntity drop = new ItemEntity(getWorld(), getX(), getY(), getZ(), stolen);
				drop.setVelocity(0, -0.3, 0);
				getWorld().spawnEntity(drop);
				if (getTarget() instanceof ServerPlayerEntity sp) {
					sp.sendMessage(Text.translatable("message.evolvedwarfare.spirit.burned", stolen.getName()).formatted(Formatting.RED), true);
				}
				vanish();
			}
		} else if (getTarget() != null) {
			// huye
			var away = getPos().subtract(getTarget().getPos()).normalize().multiply(8).add(getPos());
			getMoveControl().moveTo(away.x, away.y + 1, away.z, 1.2);
		}
		if (carried > 600) {
			dropStolen();
			vanish();
		}
	}

	private void dropStolen() {
		if (!stolen.isEmpty()) {
			ItemEntity drop = new ItemEntity(getWorld(), getX(), getY() + 0.5, getZ(), stolen);
			drop.setToDefaultPickupDelay();
			getWorld().spawnEntity(drop);
			stolen = ItemStack.EMPTY;
		}
	}

	private void vanish() {
		stolen = ItemStack.EMPTY;
		if (getWorld() instanceof ServerWorld sw) {
			sw.spawnParticles(ParticleTypes.SOUL, getX(), getBodyY(0.5), getZ(), 20, 0.3, 0.6, 0.3, 0.03);
		}
		discard();
	}

	/** Purificado con sal bendita: descansa, devuelve lo robado y quita corrupción. */
	public void purify(PlayerEntity by) {
		dropStolen();
		if (getWorld() instanceof ServerWorld sw) {
			sw.spawnParticles(ParticleTypes.END_ROD, getX(), getBodyY(0.5), getZ(), 30, 0.3, 0.7, 0.3, 0.05);
			sw.playSound(null, getBlockPos(), SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.NEUTRAL, 0.8F, 1.6F);
		}
		if (by instanceof ServerPlayerEntity sp) {
			NetherClimate.addCorruption(sp, -5);
			sp.sendMessage(Text.translatable("message.evolvedwarfare.spirit.freed").formatted(Formatting.AQUA), true);
		}
		discard();
	}

	@Override
	protected ActionResult interactMob(PlayerEntity player, Hand hand) {
		ItemStack held = player.getStackInHand(hand);
		if (held.isOf(ModItems.BLESSED_SALT)) {
			if (!getWorld().isClient) {
				held.decrementUnlessCreative(1, player);
				purify(player);
			}
			return ActionResult.success(getWorld().isClient);
		}
		return super.interactMob(player, hand);
	}

	@Override
	protected void dropEquipment(ServerWorld world, DamageSource source, boolean causedByPlayer) {
		super.dropEquipment(world, source, causedByPlayer);
		dropStolen();
	}

	@Override
	public void tickMovement() {
		super.tickMovement();
		if (getWorld().isClient && random.nextInt(3) == 0) {
			getWorld().addParticle(ParticleTypes.SOUL_FIRE_FLAME, getParticleX(0.3), getRandomBodyY(), getParticleZ(0.3), 0, 0.01, 0);
		}
	}

	@Override
	public void writeCustomDataToNbt(NbtCompound nbt) {
		super.writeCustomDataToNbt(nbt);
		if (!stolen.isEmpty()) {
			nbt.put("Stolen", stolen.encode(getRegistryManager()));
		}
	}

	@Override
	public void readCustomDataFromNbt(NbtCompound nbt) {
		super.readCustomDataFromNbt(nbt);
		stolen = nbt.contains("Stolen") ? ItemStack.fromNbt(getRegistryManager(), nbt.get("Stolen")).orElse(ItemStack.EMPTY) : ItemStack.EMPTY;
	}

	@Override
	public boolean handleFallDamage(float fallDistance, float damageMultiplier, DamageSource damageSource) {
		return false;
	}

	@Override
	protected void fall(double heightDifference, boolean onGround, BlockState state, BlockPos landedPosition) {
	}

	@Override
	public boolean cannotDespawn() {
		return !stolen.isEmpty() || super.cannotDespawn();
	}
}
