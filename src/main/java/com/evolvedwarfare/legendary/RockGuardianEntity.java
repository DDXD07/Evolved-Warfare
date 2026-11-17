package com.evolvedwarfare.legendary;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.entity.ai.goal.RevengeGoal;
import net.minecraft.entity.ai.goal.WanderAroundFarGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.PickaxeItem;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

/**
 * El guardián de roca (fase 107): a veces, al picar un mineral, sale de la veta de golpe. Un gólem de piedra con cristales
 * en la espalda: muy acorazado, lento y de puños pesados (los picos le hacen el doble). Al morir suelta, además, unos
 * cuantos de lo que guardaba la veta.
 */
public class RockGuardianEntity extends HostileEntity {
	/** Lo que guardaba (lo que suelta la veta: hierro en bruto, diamante...). */
	private Identifier hoard;

	public RockGuardianEntity(EntityType<? extends HostileEntity> type, World world) {
		super(type, world);
		this.experiencePoints = 30;
	}

	public static DefaultAttributeContainer.Builder createAttributes() {
		return HostileEntity.createHostileAttributes()
				.add(EntityAttributes.GENERIC_MAX_HEALTH, 80.0)
				.add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 11.0)
				.add(EntityAttributes.GENERIC_ARMOR, 14.0)
				.add(EntityAttributes.GENERIC_ARMOR_TOUGHNESS, 4.0)
				.add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.23)
				.add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 1.0)
				.add(EntityAttributes.GENERIC_ATTACK_KNOCKBACK, 1.5)
				.add(EntityAttributes.GENERIC_FOLLOW_RANGE, 32.0);
	}

	public void setHoard(Item item) {
		this.hoard = Registries.ITEM.getId(item);
	}

	@Override
	protected void initGoals() {
		goalSelector.add(2, new MeleeAttackGoal(this, 1.0, true));
		goalSelector.add(6, new WanderAroundFarGoal(this, 0.6));
		goalSelector.add(7, new LookAtEntityGoal(this, PlayerEntity.class, 12.0F));
		goalSelector.add(8, new LookAroundGoal(this));
		targetSelector.add(1, new RevengeGoal(this));
		targetSelector.add(2, new ActiveTargetGoal<>(this, PlayerEntity.class, true));
	}

	@Override
	public boolean damage(DamageSource source, float amount) {
		if (source.getAttacker() instanceof PlayerEntity p && p.getMainHandStack().getItem() instanceof PickaxeItem) {
			amount *= 2.0F;
		}
		return super.damage(source, amount);
	}

	@Override
	protected void dropLoot(DamageSource source, boolean causedByPlayer) {
		super.dropLoot(source, causedByPlayer);
		if (causedByPlayer && hoard != null && getWorld() instanceof ServerWorld) {
			Item item = Registries.ITEM.get(hoard);
			dropStack(new ItemStack(item, 2 + random.nextInt(4)));
		}
	}

	@Override
	public void writeCustomDataToNbt(NbtCompound nbt) {
		super.writeCustomDataToNbt(nbt);
		if (hoard != null) {
			nbt.putString("Hoard", hoard.toString());
		}
	}

	@Override
	public void readCustomDataFromNbt(NbtCompound nbt) {
		super.readCustomDataFromNbt(nbt);
		if (nbt.contains("Hoard")) {
			hoard = Identifier.tryParse(nbt.getString("Hoard"));
		}
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return SoundEvents.BLOCK_STONE_STEP;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.BLOCK_STONE_BREAK;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.ENTITY_IRON_GOLEM_DEATH;
	}

	@Override
	public float getSoundPitch() {
		return 0.5F;
	}
}
