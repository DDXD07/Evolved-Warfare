package com.evolvedwarfare.weapon;

import com.evolvedwarfare.item.AmmoPouchItem;
import com.evolvedwarfare.registry.ModComponents;
import com.evolvedwarfare.registry.ModItems;
import com.evolvedwarfare.registry.ModSounds;
import java.util.List;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
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
 * Mosquete de chispa. No se fabrica: lo vende el armero (raro). Se usa como un arma de fuego:
 * <ul>
 *   <li><b>Clic izquierdo</b>: dispara (si está cargado). El rastrillo cae, sale humo de la boca y la bala hace
 *   5 corazones de daño al torso y empuja un bloque hacia atrás. Apuntando es mucho más preciso que desde la cadera.</li>
 *   <li><b>Clic derecho</b> (mantener): apunta con la mira metálica (el punto de mira al centro y un poco de zoom).</li>
 *   <li><b>R</b>: recarga (2 segundos; gasta una bala de mosquete, suelta o de una bolsa de munición). Se ve cómo alza el
 *   arma, mete la bala, la aprieta con la baqueta y sube el rastrillo. Si cambias de objeto, se interrumpe.</li>
 * </ul>
 * El clic izquierdo y la R llegan del cliente por red ({@code FirearmPayload}); el servidor lo comprueba todo.
 */
public class MusketItem extends Item {
	/** Lo que tarda en cargar (ticks). */
	public static final int RELOAD_TICKS = 40;

	/** Lo que tarda en recargar este jugador ("Recarga rápida" de Armas de fuego la acorta). */
	public static int reloadTicks(@org.jetbrains.annotations.Nullable PlayerEntity player) {
		if (player == null) {
			return RELOAD_TICKS;
		}
		float faster = com.evolvedwarfare.skill.Skills.value(player, com.evolvedwarfare.skill.Perk.QUICK_RELOAD) / 100.0F
				+ com.evolvedwarfare.armor.ClassArmorItem.bonus(player, com.evolvedwarfare.armor.ArmorClass.Effect.RELOAD_SPEED);
		return Math.max(10, Math.round(RELOAD_TICKS * (1.0F - faster)));
	}
	/** Daño de la bala: 5 corazones (al torso). */
	public static final float DAMAGE = 10.0F;
	/** Empuje del impacto (más o menos un bloque). */
	public static final double KNOCKBACK = 0.25;
	/** Velocidad de la bala (bloques por tick). */
	private static final float SPEED = 5.0F;
	/** Desvío de la bala: apuntando con la mira, casi nada; desde la cadera, bastante. */
	private static final float SPREAD_AIMED = 0.25F;
	private static final float SPREAD_HIP = 1.6F;
	/** Tras disparar, un momento sin poder hacer nada (el retroceso). */
	private static final int COOLDOWN = 12;
	/** Cuánto sube la vista al disparar (grados). */
	public static final float KICK = 2.5F;

	public MusketItem(Settings settings) {
		super(settings);
	}

	public static boolean isLoaded(ItemStack stack) {
		return stack.getOrDefault(ModComponents.MUSKET_LOADED, false);
	}

	public static boolean isReloading(ItemStack stack) {
		return stack.contains(ModComponents.MUSKET_RELOAD);
	}

	/** Si está apuntando con un mosquete (mantiene clic derecho). */
	public static boolean isAiming(LivingEntity user) {
		return user.isUsingItem() && user.getActiveItem().getItem() instanceof MusketItem;
	}

	@Override
	public UseAction getUseAction(ItemStack stack) {
		// NONE para que vanilla no lo mueva en primera persona: apuntar lo hace su renderizador
		return UseAction.NONE;
	}

	@Override
	public int getMaxUseTime(ItemStack stack, LivingEntity user) {
		return 72000;
	}

	/** Clic derecho: apuntar con la mira (mientras se mantiene). No mientras recarga ni en la otra mano. */
	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
		ItemStack stack = user.getStackInHand(hand);
		if (hand != Hand.MAIN_HAND || isReloading(stack)) {
			return TypedActionResult.fail(stack);
		}
		user.setCurrentHand(hand);
		return TypedActionResult.consume(stack);
	}

	/** Clic izquierdo (llega por red): dispara si está cargado; si no, chasquido y aviso de que se recarga con R. */
	public static void tryFire(ServerPlayerEntity player) {
		ItemStack stack = player.getMainHandStack();
		if (!(stack.getItem() instanceof MusketItem musket) || player.getItemCooldownManager().isCoolingDown(musket)
				|| isReloading(stack)) {
			return;
		}
		if (!isLoaded(stack)) {
			player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.MUSKET_EMPTY, SoundCategory.PLAYERS,
					0.7F, 1.0F);
			player.getItemCooldownManager().set(musket, 6);
			player.sendMessage(Text.translatable("message.evolvedwarfare.musket.press_reload", Text.keybind("key.evolvedwarfare.reload"))
					.formatted(Formatting.GRAY), true);
			return;
		}
		musket.fire(player.getServerWorld(), player, stack, isAiming(player));
	}

	/** Tecla R (llega por red): empieza a recargar. */
	public static void tryReload(ServerPlayerEntity player) {
		ItemStack stack = player.getMainHandStack();
		if (!(stack.getItem() instanceof MusketItem musket) || isReloading(stack) || player.getItemCooldownManager().isCoolingDown(musket)) {
			return;
		}
		if (isLoaded(stack)) {
			player.sendMessage(Text.translatable("message.evolvedwarfare.musket.already_loaded").formatted(Formatting.GRAY), true);
			return;
		}
		if (!player.getAbilities().creativeMode && !hasAmmo(player.getInventory())) {
			player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.MUSKET_EMPTY, SoundCategory.PLAYERS,
					0.8F, 1.0F);
			player.sendMessage(Text.translatable("message.evolvedwarfare.musket.no_ammo").formatted(Formatting.RED), true);
			return;
		}
		if (isAiming(player)) {
			player.stopUsingItem();
		}
		stack.set(ModComponents.MUSKET_RELOAD, player.getWorld().getTime());
	}

	/** Lleva la recarga: sus sonidos y, a los 2 segundos, la bala dentro. Se corta si deja de tenerlo en la mano. */
	@Override
	public void inventoryTick(ItemStack stack, World world, Entity entity, int slot, boolean selected) {
		Long start = stack.get(ModComponents.MUSKET_RELOAD);
		if (start == null || world.isClient) {
			return;
		}
		if (!selected || !(entity instanceof PlayerEntity player)) {
			stack.remove(ModComponents.MUSKET_RELOAD);
			return;
		}
		long tick = world.getTime() - start;
		int reload = reloadTicks(player);
		// los sonidos van al mismo paso que la recarga (más deprisa con "Recarga rápida")
		long now = tick * RELOAD_TICKS / reload;
		long before = (tick - 1) * RELOAD_TICKS / reload;
		SoundEvent sound = null;
		float pitch = 1.0F;
		if (now != before || tick == 0) {
			if (now == 15) {
				sound = ModSounds.MUSKET_BALL_IN;
			} else if (now == 19 || now == 25) {
				sound = ModSounds.MUSKET_RAMROD;
			} else if (now == 32) {
				sound = ModSounds.MUSKET_COCK;
				pitch = 1.3F;
			} else if (now == 37) {
				sound = ModSounds.MUSKET_COCK;
			}
		}
		if (sound != null) {
			world.playSound(null, player.getX(), player.getY(), player.getZ(), sound, SoundCategory.PLAYERS, 0.8F,
					pitch * (0.95F + world.getRandom().nextFloat() * 0.1F));
		}
		if (tick < reload) {
			return;
		}
		stack.remove(ModComponents.MUSKET_RELOAD);
		float spare = com.evolvedwarfare.skill.Skills.value(player, com.evolvedwarfare.skill.Perk.SPARE_BALLS) / 100.0F;
		if (player.getAbilities().creativeMode || (spare > 0.0F && hasAmmo(player.getInventory()) && world.getRandom().nextFloat() < spare)) {
			// creativo, o "Bolsillo de balas": carga sin gastar la bala
			stack.set(ModComponents.MUSKET_LOADED, true);
		} else if (takeAmmo(player.getInventory())) {
			stack.set(ModComponents.MUSKET_LOADED, true);
			player.sendMessage(Text.translatable("message.evolvedwarfare.musket.loaded", ammoLeft(player.getInventory()))
					.formatted(Formatting.GRAY), true);
		} else {
			world.playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.MUSKET_EMPTY, SoundCategory.PLAYERS, 0.8F, 1.0F);
			player.sendMessage(Text.translatable("message.evolvedwarfare.musket.no_ammo").formatted(Formatting.RED), true);
		}
	}

	/** Lo que el cliente adelanta al disparar (para que el rastrillo caiga sin esperar al servidor). */
	public static void predictFire(ItemStack stack, PlayerEntity player) {
		stack.remove(ModComponents.MUSKET_LOADED);
		stack.set(ModComponents.MUSKET_FIRED, player.getWorld().getTime());
		player.getItemCooldownManager().set(stack.getItem(), COOLDOWN);
	}

	private void fire(ServerWorld world, PlayerEntity user, ItemStack stack, boolean aimed) {
		stack.remove(ModComponents.MUSKET_LOADED);
		stack.set(ModComponents.MUSKET_FIRED, world.getTime());
		user.getItemCooldownManager().set(this, COOLDOWN);
		MusketBallEntity ball = new MusketBallEntity(world, user);
		ball.setVelocity(user, user.getPitch(), user.getYaw(), 0.0F, SPEED, aimed ? SPREAD_AIMED : SPREAD_HIP);
		world.spawnEntity(ball);

		// humo y fogonazo en la boca del cañón (delante y, desde la cadera, un poco hacia el lado de la mano)
		Vec3d look = user.getRotationVec(1.0F);
		Vec3d right = look.crossProduct(new Vec3d(0.0, 1.0, 0.0));
		right = right.lengthSquared() < 1.0E-4 ? new Vec3d(1.0, 0.0, 0.0) : right.normalize();
		double side = (user.getMainArm() == Arm.RIGHT ? 1.0 : -1.0) * (aimed ? 0.3 : 1.0);
		Vec3d muzzle = user.getEyePos().add(look.multiply(aimed ? 2.0 : 1.5)).add(right.multiply(0.25 * side))
				.add(0.0, aimed ? -0.05 : -0.18, 0.0);
		smoke(world, muzzle, look);
		Vec3d pan = user.getEyePos().add(look.multiply(0.45)).add(right.multiply(0.3 * side)).add(0.0, -0.22, 0.0);
		world.spawnParticles(ParticleTypes.SMOKE, pan.x, pan.y, pan.z, 3, 0.03, 0.03, 0.03, 0.01);

		world.playSound(null, user.getX(), user.getEyeY(), user.getZ(), ModSounds.MUSKET_FIRE, SoundCategory.PLAYERS, 3.0F,
				0.9F + world.getRandom().nextFloat() * 0.15F);
		world.emitGameEvent(user, GameEvent.PROJECTILE_SHOOT, user.getPos());
		user.incrementStat(Stats.USED.getOrCreateStat(this));
		stack.damage(1, user, net.minecraft.entity.EquipmentSlot.MAINHAND);
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
		tooltip.add(Text.translatable("item.evolvedwarfare.musket.tip_controls", Text.keybind("key.evolvedwarfare.reload"))
				.formatted(Formatting.GRAY));
	}
}
