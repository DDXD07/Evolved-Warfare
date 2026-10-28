package com.evolvedwarfare.weapon;

import com.evolvedwarfare.registry.ModEntities;
import com.evolvedwarfare.registry.ModItems;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.world.World;

/** La jabalina en vuelo: hace más daño que una flecha y se queda clavada para recogerla. */
public class JavelinEntity extends PersistentProjectileEntity {
	public JavelinEntity(EntityType<? extends JavelinEntity> type, World world) {
		super(type, world);
	}

	public JavelinEntity(World world, LivingEntity owner, ItemStack stack) {
		super(ModEntities.JAVELIN, owner, world, stack, null);
		setDamage(3.5);
	}

	@Override
	protected ItemStack getDefaultItemStack() {
		return new ItemStack(ModItems.JAVELIN);
	}

	@Override
	protected SoundEvent getHitSound() {
		return SoundEvents.ITEM_TRIDENT_HIT;
	}
}
