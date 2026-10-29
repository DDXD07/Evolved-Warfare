package com.evolvedwarfare.weapon;

import com.evolvedwarfare.item.AmmoPouchItem;
import com.evolvedwarfare.registry.ModComponents;
import com.evolvedwarfare.registry.ModItems;
import com.evolvedwarfare.registry.ModSounds;
import java.util.List;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.stat.Stats;
import net.minecraft.text.Text;
import net.minecraft.util.Arm;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.UseAction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import net.minecraft.world.event.GameEvent;

/**
 * Mosquete de chispa. No se fabrica: lo vende el armero (raro).
 * <ul>
 *   <li>Descargado: mantén clic derecho 2 segundos para cargarlo (gasta una bala de mosquete, suelta o de una bolsa de
 *   munición). Se ve cómo alza el
 *   arma, mete la bala, la aprieta con la baqueta y sube el rastrillo.</li>
 *   <li>Cargado: clic derecho dispara. El rastrillo cae, sale humo de la boca y la bala hace 5 corazones de daño y
 *   empuja un bloque hacia atrás.</li>
 * </ul>
 */
public class MusketItem extends Item {
	/** Lo que tarda en cargar (ticks). */
	public static final int RELOAD_TICKS = 40;
	/** Daño de la bala: 5 corazones. */
	public static final float DAMAGE = 10.0F;
	/** Empuje del impacto (más o menos un bloque). */
	public static final double KNOCKBACK = 0.25;
	/** Velocidad de la bala (bloques por tick) y su desvío. */
	private static final float SPEED = 5.0F;
	private static final float SPREAD = 0.6F;
	/** Tras disparar, un momento sin poder volver a cargar (para el retroceso). */
	private static final int COOLDOWN = 12;

	public MusketItem(Settings settings) {
		super(settings);
	}

	public static boolean isLoaded(ItemStack stack) {
		return stack.getOrDefault(ModComponents.MUSKET_LOADED, false);
	}

	@Override
	public UseAction getUseAction(ItemStack stack) {
		// NONE para que en primera persona no se mueva como otra cosa: la animación la hace su renderizador
		return UseAction.NONE;
	}

	@Override
	public int getMaxUseTime(ItemStack stack, LivingEntity user) {
		return RELOAD_TICKS;
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
		ItemStack stack = user.getStackInHand(hand);
		if (isLoaded(stack)) {
			fire(world, user, hand, stack);
			return TypedActionResult.consume(stack);
		}
		if (user.getAbilities().creativeMode || hasAmmo(user.getInventory())) {
			user.setCurrentHand(hand);
			return TypedActionResult.consume(stack);
		}
		if (!world.isClient) {
			world.playSound(null, user.getX(), user.getY(), user.getZ(), ModSounds.MUSKET_EMPTY, SoundCategory.PLAYERS, 0.8F, 1.0F);
			user.sendMessage(Text.translatable("message.evolvedwarfare.musket.no_ammo").formatted(Formatting.RED), true);
		}
		return TypedActionResult.fail(stack);
	}

	/** Los sonidos de la carga, a la vez que la animación. */
	@Override
	public void usageTick(World world, LivingEntity user, ItemStack stack, int remainingUseTicks) {
		if (world.isClient) {
			return;
		}
		int tick = RELOAD_TICKS - remainingUseTicks;
		SoundEvent sound = null;
		float pitch = 1.0F;
		switch (tick) {
			case 15 -> sound = ModSounds.MUSKET_BALL_IN;
			case 19, 25 -> sound = ModSounds.MUSKET_RAMROD;
			case 32 -> {
				sound = ModSounds.MUSKET_COCK;
				pitch = 1.3F;
			}
			case 37 -> sound = ModSounds.MUSKET_COCK;
			default -> {
			}
		}
		if (sound != null) {
			world.playSound(null, user.getX(), user.getY(), user.getZ(), sound, SoundCategory.PLAYERS, 0.8F,
					pitch * (0.95F + world.getRandom().nextFloat() * 0.1F));
		}
	}

	/** Al terminar la carga: gasta una bala y queda cargado. */
	@Override
	public ItemStack finishUsing(ItemStack stack, World world, LivingEntity user) {
		if (!world.isClient && user instanceof PlayerEntity player) {
			if (player.getAbilities().creativeMode) {
				stack.set(ModComponents.MUSKET_LOADED, true);
			} else if (takeAmmo(player.getInventory())) {
				stack.set(ModComponents.MUSKET_LOADED, true);
				player.sendMessage(Text.translatable("message.evolvedwarfare.musket.loaded", ammoLeft(player.getInventory()))
						.formatted(Formatting.GRAY), true);
			} else {
				world.playSound(null, user.getX(), user.getY(), user.getZ(), ModSounds.MUSKET_EMPTY, SoundCategory.PLAYERS, 0.8F, 1.0F);
				player.sendMessage(Text.translatable("message.evolvedwarfare.musket.no_ammo").formatted(Formatting.RED), true);
			}
		}
		return stack;
	}

	private void fire(World world, PlayerEntity user, Hand hand, ItemStack stack) {
		stack.remove(ModComponents.MUSKET_LOADED);
		stack.set(ModComponents.MUSKET_FIRED, world.getTime());
		user.getItemCooldownManager().set(this, COOLDOWN);
		if (!(world instanceof ServerWorld server)) {
			return;
		}
		MusketBallEntity ball = new MusketBallEntity(world, user);
		ball.setVelocity(user, user.getPitch(), user.getYaw(), 0.0F, SPEED, SPREAD);
		world.spawnEntity(ball);

		// humo y fogonazo en la boca del cañón (delante y un poco hacia el lado de la mano)
		Vec3d look = user.getRotationVec(1.0F);
		Vec3d right = look.crossProduct(new Vec3d(0.0, 1.0, 0.0));
		right = right.lengthSquared() < 1.0E-4 ? new Vec3d(1.0, 0.0, 0.0) : right.normalize();
		boolean rightHand = (user.getMainArm() == Arm.RIGHT) == (hand == Hand.MAIN_HAND);
		double side = rightHand ? 1.0 : -1.0;
		Vec3d muzzle = user.getEyePos().add(look.multiply(1.5)).add(right.multiply(0.25 * side)).add(0.0, -0.18, 0.0);
		smoke(server, muzzle, look);
		Vec3d pan = user.getEyePos().add(look.multiply(0.45)).add(right.multiply(0.3 * side)).add(0.0, -0.22, 0.0);
		server.spawnParticles(ParticleTypes.SMOKE, pan.x, pan.y, pan.z, 3, 0.03, 0.03, 0.03, 0.01);

		world.playSound(null, user.getX(), user.getEyeY(), user.getZ(), ModSounds.MUSKET_FIRE, SoundCategory.PLAYERS, 3.0F,
				0.9F + world.getRandom().nextFloat() * 0.15F);
		world.emitGameEvent(user, GameEvent.PROJECTILE_SHOOT, user.getPos());
		user.incrementStat(Stats.USED.getOrCreateStat(this));
		stack.damage(1, user, LivingEntity.getSlotForHand(hand));
	}

	private static void smoke(ServerWorld world, Vec3d muzzle, Vec3d look) {
		Random random = world.getRandom();
		for (int i = 0; i < 14; i++) {
			double speed = 0.03 + random.nextDouble() * 0.2;
			Vec3d v = look.multiply(speed).add((random.nextDouble() - 0.5) * 0.05, (random.nextDouble() - 0.3) * 0.04,
					(random.nextDouble() - 0.5) * 0.05);
			world.spawnParticles(i % 3 == 0 ? ParticleTypes.LARGE_SMOKE : ParticleTypes.CLOUD, muzzle.x, muzzle.y, muzzle.z, 0,
					v.x, v.y, v.z, 1.0);
		}
		for (int i = 0; i < 5; i++) {
			Vec3d v = look.multiply(0.05 + random.nextDouble() * 0.1);
			world.spawnParticles(ParticleTypes.FLAME, muzzle.x, muzzle.y, muzzle.z, 0, v.x, v.y, v.z, 1.0);
		}
		world.spawnParticles(ParticleTypes.POOF, muzzle.x, muzzle.y, muzzle.z, 4, 0.06, 0.06, 0.06, 0.02);
	}

	/** Si lleva alguna bala: suelta o en una bolsa de munición. */
	private static boolean hasAmmo(PlayerInventory inventory) {
		return ammoLeft(inventory) > 0;
	}

	/** Las balas que le quedan (sueltas y en bolsas de munición). */
	private static int ammoLeft(PlayerInventory inventory) {
		int total = AmmoPouchItem.countIn(inventory, ModItems.MUSKET_BALL);
		for (int i = 0; i < inventory.size(); i++) {
			ItemStack stack = inventory.getStack(i);
			if (stack.isOf(ModItems.MUSKET_BALL)) {
				total += stack.getCount();
			}
		}
		return total;
	}

	/**
	 * Gasta una bala: primero una suelta en la otra mano (si la has puesto ahí es que quieres usar esa), luego de las
	 * bolsas de munición y por último las sueltas del inventario.
	 */
	private static boolean takeAmmo(PlayerInventory inventory) {
		ItemStack offhand = inventory.offHand.get(0);
		if (offhand.isOf(ModItems.MUSKET_BALL)) {
			offhand.decrement(1);
			inventory.markDirty();
			return true;
		}
		if (AmmoPouchItem.takeOne(inventory, ModItems.MUSKET_BALL)) {
			return true;
		}
		for (int i = 0; i < inventory.size(); i++) {
			ItemStack stack = inventory.getStack(i);
			if (stack.isOf(ModItems.MUSKET_BALL)) {
				stack.decrement(1);
				inventory.markDirty();
				return true;
			}
		}
		return false;
	}

	/** Cargar o disparar no hace que el arma "se guarde y se vuelva a sacar" en la mano. */
	public boolean allowComponentsUpdateAnimation(PlayerEntity player, Hand hand, ItemStack oldStack, ItemStack newStack) {
		return false;
	}

	@Override
	public boolean canRepair(ItemStack stack, ItemStack ingredient) {
		return ingredient.isOf(Items.IRON_INGOT);
	}

	@Override
	public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip, TooltipType type) {
		super.appendTooltip(stack, context, tooltip, type);
		tooltip.add(Text.translatable(isLoaded(stack) ? "item.evolvedwarfare.musket.loaded" : "item.evolvedwarfare.musket.unloaded")
				.formatted(isLoaded(stack) ? Formatting.GREEN : Formatting.GRAY));
		tooltip.add(Text.translatable("item.evolvedwarfare.musket.tip_damage").formatted(Formatting.DARK_GREEN));
		tooltip.add(Text.translatable("item.evolvedwarfare.musket.tip_reload").formatted(Formatting.GRAY));
		tooltip.add(Text.translatable("item.evolvedwarfare.musket.tip_fire").formatted(Formatting.GRAY));
	}
}
