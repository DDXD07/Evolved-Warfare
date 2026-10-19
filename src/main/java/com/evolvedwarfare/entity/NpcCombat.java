package com.evolvedwarfare.entity;

import com.evolvedwarfare.registry.ModItems;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.DyedColorComponent;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.item.BowItem;
import net.minecraft.item.CrossbowItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;

/** Utilidades de combate compartidas por soldados y bandidos. */
public final class NpcCombat {
	/** Colores de la ropa de los bandidos (cuero teñido): granate, negro, pardo y verde oliva. */
	private static final int[] BANDIT_COLORS = {0x5E1B1B, 0x2B2B2B, 0x4A3423, 0x3C3F2A};
	/** Azul de los soldados que no vienen de un recluta (p. ej. invocados con /summon). */
	public static final int SOLDIER_COLOR = 0x2D4A86;

	private NpcCombat() {
	}

	public static boolean hasBow(LivingEntity entity) {
		return entity.getMainHandStack().getItem() instanceof BowItem;
	}

	public static boolean hasCrossbow(LivingEntity entity) {
		return entity.getMainHandStack().getItem() instanceof CrossbowItem;
	}

	/**
	 * Dispara una flecha (arco) o un virote (ballesta) hacia el objetivo. Las flechas de los NPCs no se
	 * pueden recoger, para que no sirvan para conseguir flechas gratis.
	 */
	public static void shootArrow(LivingEntity shooter, LivingEntity target, boolean crossbow, float pull, float damageMultiplier) {
		World world = shooter.getWorld();
		ItemStack weapon = shooter.getMainHandStack().copy();
		ArrowEntity arrow = new ArrowEntity(world, shooter, new ItemStack(Items.ARROW), weapon);
		double dx = target.getX() - shooter.getX();
		double dz = target.getZ() - shooter.getZ();
		double dy = target.getBodyY(0.3333333333333333) - arrow.getY();
		double horizontal = Math.sqrt(dx * dx + dz * dz);
		float speed = crossbow ? 2.2F : 1.6F * Math.max(0.35F, pull);
		float spread = (float) (14 - world.getDifficulty().getId() * 4);
		arrow.setVelocity(dx, dy + horizontal * (crossbow ? 0.12 : 0.2), dz, speed, spread);
		arrow.setDamage(arrow.getDamage() * damageMultiplier * (crossbow ? 1.5 : 1.0));
		if (crossbow || pull >= 1.0F) {
			arrow.setCritical(true);
		}
		arrow.pickupType = PersistentProjectileEntity.PickupPermission.DISALLOWED;
		world.spawnEntity(arrow);
		shooter.playSound(crossbow ? SoundEvents.ITEM_CROSSBOW_SHOOT : SoundEvents.ENTITY_SKELETON_SHOOT, 1.0F,
				1.0F / (shooter.getRandom().nextFloat() * 0.4F + 0.8F));
	}

	/** Con escudo en la mano izquierda, para parte de los golpes que le llegan de frente. */
	public static boolean tryShieldBlock(LivingEntity self, DamageSource source) {
		if (!self.getOffHandStack().isOf(Items.SHIELD) || source.isIn(DamageTypeTags.BYPASSES_SHIELD)) {
			return false;
		}
		Vec3d from = source.getPosition();
		if (from == null) {
			return false;
		}
		Vec3d look = self.getRotationVec(1.0F);
		Vec3d to = from.subtract(self.getPos());
		double length = Math.sqrt(to.x * to.x + to.z * to.z);
		if (length < 1.0E-4 || (to.x * look.x + to.z * look.z) / length < 0.25 || self.getRandom().nextFloat() >= 0.4F) {
			return false;
		}
		self.playSound(SoundEvents.ITEM_SHIELD_BLOCK, 1.0F, 0.8F + self.getRandom().nextFloat() * 0.4F);
		return true;
	}

	public static int randomBanditColor(Random random) {
		return BANDIT_COLORS[random.nextInt(BANDIT_COLORS.length)];
	}

	private static ItemStack dyed(Item item, int color) {
		ItemStack stack = new ItemStack(item);
		stack.set(DataComponentTypes.DYED_COLOR, new DyedColorComponent(color, false));
		return stack;
	}

	/**
	 * Equipo inicial según la clase. 'color' tiñe la ropa de cuero; 'elite' mejora las piezas
	 * (dificultad difícil o capitanes).
	 */
	public static void equip(MobEntity mob, NpcRole role, Random r, int color, boolean elite) {
		switch (role) {
			case WARRIOR -> {
				mob.equipStack(EquipmentSlot.MAINHAND, new ItemStack(elite || r.nextFloat() < 0.6F ? Items.IRON_SWORD : Items.STONE_SWORD));
				if (r.nextFloat() < 0.5F || elite) {
					mob.equipStack(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
				}
				mob.equipStack(EquipmentSlot.CHEST, elite || r.nextFloat() < 0.4F ? new ItemStack(Items.CHAINMAIL_CHESTPLATE) : dyed(Items.LEATHER_CHESTPLATE, color));
				if (r.nextFloat() < 0.75F) {
					mob.equipStack(EquipmentSlot.HEAD, r.nextFloat() < 0.33F ? new ItemStack(Items.IRON_HELMET) : dyed(Items.LEATHER_HELMET, color));
				}
				mob.equipStack(EquipmentSlot.LEGS, dyed(Items.LEATHER_LEGGINGS, color));
			}
			case BARBARIAN -> {
				mob.equipStack(EquipmentSlot.MAINHAND, new ItemStack(elite || r.nextFloat() < 0.5F ? Items.IRON_AXE : Items.STONE_AXE));
				if (r.nextFloat() < 0.6F) {
					mob.equipStack(EquipmentSlot.HEAD, dyed(Items.LEATHER_HELMET, color));
				}
				mob.equipStack(EquipmentSlot.LEGS, dyed(Items.LEATHER_LEGGINGS, color));
				mob.equipStack(EquipmentSlot.FEET, dyed(Items.LEATHER_BOOTS, color));
			}
			case ARCHER -> {
				mob.equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
				mob.equipStack(EquipmentSlot.HEAD, dyed(Items.LEATHER_HELMET, color));
				mob.equipStack(EquipmentSlot.CHEST, dyed(Items.LEATHER_CHESTPLATE, color));
				if (elite) {
					mob.equipStack(EquipmentSlot.FEET, dyed(Items.LEATHER_BOOTS, color));
				}
			}
			case CROSSBOWMAN -> {
				mob.equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.CROSSBOW));
				mob.equipStack(EquipmentSlot.HEAD, elite || r.nextFloat() < 0.5F ? new ItemStack(Items.CHAINMAIL_HELMET) : dyed(Items.LEATHER_HELMET, color));
				mob.equipStack(EquipmentSlot.CHEST, dyed(Items.LEATHER_CHESTPLATE, color));
				mob.equipStack(EquipmentSlot.LEGS, dyed(Items.LEATHER_LEGGINGS, color));
			}
			case MEDIC -> {
				mob.equipStack(EquipmentSlot.MAINHAND, new ItemStack(ModItems.MEDIC_KIT));
				mob.equipStack(EquipmentSlot.CHEST, dyed(Items.LEATHER_CHESTPLATE, 0xE8E2D0));
				mob.equipStack(EquipmentSlot.HEAD, dyed(Items.LEATHER_HELMET, color));
			}
			case CAPTAIN -> {
				mob.equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
				mob.equipStack(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
				mob.equipStack(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
				mob.equipStack(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
				mob.equipStack(EquipmentSlot.LEGS, new ItemStack(Items.CHAINMAIL_LEGGINGS));
				mob.equipStack(EquipmentSlot.FEET, new ItemStack(Items.IRON_BOOTS));
			}
		}
	}
}
