package com.evolvedwarfare.weapon;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ProjectileItem;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.UseAction;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * El golpe fuerte de las armas cuerpo a cuerpo del mod: mantener clic derecho lo carga (cada arma tarda lo suyo,
 * {@link MeleeSwing#chargeTicks}); al soltar con la carga completa, golpea a lo que tengas delante (a tu alcance) con el
 * doble de daño. Si sueltas antes, no pasa nada (el arma vuelve a su sitio).
 *
 * <p>Si en la otra mano llevas algo que se usa con clic derecho (escudo, comida, bloques, antorchas, lo que se lanza...),
 * el clic derecho sigue siendo para eso.</p>
 */
public final class HeavyAttack {
	public static final float MULTIPLIER = 2.0F;
	/** Quién está dando ahora un golpe fuerte (solo en el hilo del servidor, durante su ataque). */
	@Nullable
	private static PlayerEntity striking;

	private HeavyAttack() {
	}

	public static int maxUseTime() {
		return 72000;
	}

	/** Lo de la otra mano tiene su propio clic derecho. */
	private static boolean offhandBusy(PlayerEntity player) {
		ItemStack off = player.getOffHandStack();
		return !off.isEmpty() && (off.getUseAction() != UseAction.NONE || off.getItem() instanceof BlockItem
				|| off.getItem() instanceof ProjectileItem || off.contains(DataComponentTypes.FOOD));
	}

	/** Clic derecho con el arma: empieza a cargar. */
	public static TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
		ItemStack stack = user.getStackInHand(hand);
		if (hand != Hand.MAIN_HAND || offhandBusy(user)) {
			return TypedActionResult.pass(stack);
		}
		user.setCurrentHand(hand);
		return TypedActionResult.consume(stack);
	}

	/** Al soltar: si estaba cargado del todo, el golpe. */
	public static void release(ItemStack stack, World world, LivingEntity user, int remainingUseTicks, WeaponType type) {
		int used = maxUseTime() - remainingUseTicks;
		if (used < MeleeSwing.chargeTicks(type) || world.isClient || !(user instanceof ServerPlayerEntity player)) {
			return;
		}
		strike(player);
	}

	/** El golpe fuerte: a lo primero que haya delante, a su alcance (sin atravesar bloques). */
	public static boolean strike(ServerPlayerEntity player) {
		World world = player.getWorld();
		world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.PLAYERS,
				1.0F, 0.6F);
		Entity target = target(player);
		if (target == null) {
			return false;
		}
		striking = player;
		try {
			player.attack(target);
		} finally {
			striking = null;
		}
		return true;
	}

	@Nullable
	private static Entity target(PlayerEntity player) {
		double reach = player.getEntityInteractionRange();
		Vec3d eye = player.getEyePos();
		Vec3d look = player.getRotationVec(1.0F);
		Vec3d end = eye.add(look.multiply(reach));
		double limit = reach * reach;
		HitResult block = player.getWorld().raycast(new RaycastContext(eye, end, RaycastContext.ShapeType.COLLIDER,
				RaycastContext.FluidHandling.NONE, player));
		if (block.getType() != HitResult.Type.MISS) {
			limit = Math.min(limit, block.getPos().squaredDistanceTo(eye));
		}
		Box box = player.getBoundingBox().stretch(look.multiply(reach)).expand(1.0);
		EntityHitResult hit = ProjectileUtil.raycast(player, eye, end, box, e -> !e.isSpectator() && e.canHit() && e != player.getVehicle(),
				limit);
		return hit == null ? null : hit.getEntity();
	}

	/** El daño de un golpe fuerte (lo llama el mixin del daño). */
	public static float modify(DamageSource source, float amount) {
		if (striking != null && source.getAttacker() == striking && source.isOf(DamageTypes.PLAYER_ATTACK)) {
			return amount * MULTIPLIER;
		}
		return amount;
	}

	public static boolean isStriking(PlayerEntity player) {
		return striking == player;
	}
}
