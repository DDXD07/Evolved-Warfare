package com.evolvedwarfare.engineering;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ChargedProjectilesComponent;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.item.CrossbowItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * Las ballestas mecánicas (fase 79). La pesada (arbalesta): arco de acero tensado con torno, tarda mucho más en cargar
 * pero el virote sale más rápido y hace más del doble de daño. La de repetición: al tensarla carga hasta 5 flechas de su
 * cargador, y cada clic dispara una.
 */
public class MechanicalCrossbowItem extends CrossbowItem {
	public final boolean repeating;
	/** Cuánto más tarda en cargar que una ballesta normal, y cuánto daño y velocidad dan sus flechas. */
	public final float chargeFactor, damageFactor, speedFactor;
	public static final int MAGAZINE = 5, REPEAT_COOLDOWN = 6;

	public MechanicalCrossbowItem(boolean repeating, Settings settings) {
		super(settings);
		this.repeating = repeating;
		this.chargeFactor = repeating ? 2.0F : 2.4F;
		this.damageFactor = repeating ? 0.8F : 2.2F;
		this.speedFactor = repeating ? 1.0F : 1.45F;
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, List<net.minecraft.text.Text> tooltip, net.minecraft.item.tooltip.TooltipType type) {
		super.appendTooltip(stack, context, tooltip, type);
		tooltip.add(net.minecraft.text.Text.translatable(getTranslationKey() + ".desc").formatted(net.minecraft.util.Formatting.GRAY));
	}

	@Override
	public boolean canRepair(ItemStack stack, ItemStack ingredient) {
		return ingredient.isOf(repeating ? Metalwork.IRON_PLATE : Metalwork.STEEL_PLATE);
	}

	@Override
	protected ProjectileEntity createArrowEntity(World world, LivingEntity shooter, ItemStack weaponStack, ItemStack projectileStack, boolean critical) {
		ProjectileEntity p = super.createArrowEntity(world, shooter, weaponStack, projectileStack, critical);
		if (p instanceof PersistentProjectileEntity arrow) {
			arrow.setDamage(arrow.getDamage() * damageFactor);
		}
		return p;
	}

	@Override
	protected void shoot(LivingEntity shooter, ProjectileEntity projectile, int index, float speed, float divergence, float yaw,
			@Nullable LivingEntity target) {
		super.shoot(shooter, projectile, index, speed * speedFactor, divergence * (repeating ? 1.5F : 0.6F), yaw, target);
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
		ItemStack stack = user.getStackInHand(hand);
		ChargedProjectilesComponent loaded = stack.get(DataComponentTypes.CHARGED_PROJECTILES);
		if (!repeating || loaded == null || loaded.isEmpty()) {
			return super.use(world, user, hand);
		}
		// la de repetición: una flecha cada vez
		if (world instanceof ServerWorld sw) {
			List<ItemStack> left = new ArrayList<>(loaded.getProjectiles());
			ItemStack first = left.remove(0);
			stack.set(DataComponentTypes.CHARGED_PROJECTILES, left.isEmpty() ? ChargedProjectilesComponent.DEFAULT : ChargedProjectilesComponent.of(left));
			shootAll(sw, user, hand, stack, List.of(first), first.isOf(Items.FIREWORK_ROCKET) ? 1.6F : 3.15F, 1.0F, true, null);
			world.playSound(null, user.getX(), user.getY(), user.getZ(), SoundEvents.ITEM_CROSSBOW_SHOOT, SoundCategory.PLAYERS, 1.0F, 1.25F);
			user.getItemCooldownManager().set(this, REPEAT_COOLDOWN);
		}
		return TypedActionResult.consume(stack);
	}

	@Override
	public void onStoppedUsing(ItemStack stack, World world, LivingEntity user, int remainingUseTicks) {
		if (!repeating) {
			super.onStoppedUsing(stack, world, user, remainingUseTicks);
			return;
		}
		int used = getMaxUseTime(stack, user) - remainingUseTicks;
		if (used < getPullTime(stack, user) || isCharged(stack)) {
			return;
		}
		List<ItemStack> magazine = load(user, stack);
		if (!magazine.isEmpty()) {
			stack.set(DataComponentTypes.CHARGED_PROJECTILES, ChargedProjectilesComponent.of(magazine));
			world.playSound(null, user.getX(), user.getY(), user.getZ(), SoundEvents.ITEM_CROSSBOW_LOADING_END, SoundCategory.PLAYERS, 1.0F,
					0.9F + world.random.nextFloat() * 0.2F);
		}
	}

	/** Llena el cargador con lo que haya (hasta 5). */
	public static List<ItemStack> load(LivingEntity user, ItemStack crossbow) {
		List<ItemStack> out = new ArrayList<>();
		boolean creative = user instanceof PlayerEntity p && p.isCreative();
		for (int i = 0; i < MAGAZINE; i++) {
			ItemStack ammo = user.getProjectileType(crossbow);
			if (ammo.isEmpty()) {
				break;
			}
			out.add(creative ? ammo.copyWithCount(1) : ammo.split(1));
		}
		return out;
	}
}
