package com.evolvedwarfare.health;

import com.evolvedwarfare.climate.Thirst;
import com.evolvedwarfare.config.EwConfig;
import com.evolvedwarfare.network.StaminaPayload;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BowItem;
import net.minecraft.item.CrossbowItem;
import net.minecraft.item.Items;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.MathHelper;

/**
 * El aguante (fase 97): una barra de 0 a 100 que gastan correr, saltar, pegar (más con armas lentas y pesadas), picar,
 * tensar el arco, nadar, trepar y parar golpes con el escudo. Se recupera sola un momento después de dejar de gastar
 * (más deprisa quieto que andando; nada corriendo), más despacio con hambre o sed, herido o con armadura pesada. Sin
 * aguante se está <b>agotado</b>: no se puede correr, se va lento, se pica despacio, los golpes hacen la mitad y el
 * escudo se cae; hasta recuperar 30.
 */
public final class Stamina {
	public static final float MAX = 100.0F;
	/** Hasta dónde hay que recuperarse para dejar de estar agotado. */
	public static final float RECOVER = 30.0F;
	/** Lo que se espera sin gastar antes de empezar a recuperar (ticks). */
	public static final int DELAY = 30;

	public static final class State {
		public float value = MAX;
		public boolean exhausted;
		int lastSpend = -1000;
		int weakHitTick = -1;
		float lastSent = -1.0F;
		boolean lastExhausted;
	}

	private static final Map<UUID, State> STATES = new HashMap<>();
	private static int now;

	private Stamina() {
	}

	public static boolean enabled() {
		return EwConfig.get().staminaEnabled;
	}

	public static State state(PlayerEntity player) {
		return STATES.computeIfAbsent(player.getUuid(), id -> new State());
	}

	public static float get(PlayerEntity player) {
		return state(player).value;
	}

	public static boolean exhausted(PlayerEntity player) {
		return enabled() && state(player).exhausted;
	}

	public static void set(PlayerEntity player, float value) {
		State s = state(player);
		s.value = MathHelper.clamp(value, 0.0F, MAX);
		s.exhausted = s.value <= 0.0F || s.exhausted && s.value < RECOVER;
	}

	private static boolean exempt(PlayerEntity player) {
		return !enabled() || player.getAbilities().creativeMode || player.isSpectator() || !player.isAlive();
	}

	/** Gasta aguante (multiplicado por la configuración). Devuelve si había bastante. */
	public static boolean spend(PlayerEntity player, float amount) {
		if (exempt(player) || player.getWorld().isClient) {
			return true;
		}
		State s = state(player);
		float cost = amount * (float) EwConfig.get().staminaCost * com.evolvedwarfare.skill.SurvivorAttributes.staminaCost(player);
		boolean enough = s.value >= cost;
		s.value = Math.max(0.0F, s.value - cost);
		s.lastSpend = now;
		if (s.value <= 0.0F && !s.exhausted) {
			s.exhausted = true;
			player.sendMessage(Text.translatable("message.evolvedwarfare.stamina.exhausted").formatted(Formatting.GOLD), true);
			player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_HORSE_BREATHE, SoundCategory.PLAYERS,
					1.0F, 1.5F);
		}
		return enough;
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Stamina::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> STATES.remove(handler.player.getUuid()));
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> STATES.remove(newPlayer.getUuid()));
		AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
			if (!world.isClient) {
				onAttack(player);
			}
			return ActionResult.PASS;
		});
		PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
			if (!world.isClient) {
				spend(player, 1.0F + Math.min(3.0F, state.getHardness(world, pos) * 0.3F));
			}
		});
	}

	/** Lo que cuesta un golpe: más con las armas lentas (hachas, martillos...). */
	public static float attackCost(PlayerEntity player) {
		double speed = player.getAttributeValue(EntityAttributes.GENERIC_ATTACK_SPEED);
		return (float) (4.0 + 6.0 / Math.max(0.5, speed));
	}

	public static void onAttack(PlayerEntity player) {
		if (exempt(player)) {
			return;
		}
		boolean enough = spend(player, attackCost(player));
		if (!enough || state(player).exhausted) {
			state(player).weakHitTick = now;
		}
	}

	public static void onJump(PlayerEntity player) {
		spend(player, 5.0F);
	}

	/** Un golpe sin aguante hace la mitad. */
	public static float modifyMelee(DamageSource source, float amount) {
		if (source.isOf(DamageTypes.PLAYER_ATTACK) && source.getAttacker() instanceof PlayerEntity player && enabled()
				&& state(player).weakHitTick == now) {
			return amount * 0.5F;
		}
		return amount;
	}

	/** Parar un golpe con el escudo cansa; agotado, el escudo se cae. */
	public static void onBlocked(PlayerEntity player, float amount) {
		if (exempt(player) || amount <= 0.0F) {
			return;
		}
		spend(player, Math.min(25.0F, amount * 2.5F));
		if (state(player).exhausted && player.isBlocking()) {
			player.getItemCooldownManager().set(Items.SHIELD, 60);
			player.clearActiveItem();
			player.getWorld().sendEntityStatus(player, (byte) 30);
		}
	}

	// ------------------------------------------------------------------ cada tick

	private static void tick(MinecraftServer server) {
		now = server.getTicks();
		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			tick(player);
		}
	}

	/** Un tick de aguante (público para las pruebas). */
	public static void tick(ServerPlayerEntity player) {
		State s = state(player);
		if (exempt(player)) {
			s.value = MAX;
			s.exhausted = false;
			sync(player, s);
			return;
		}
		boolean moving = player.getVelocity().horizontalLengthSquared() > 1.0E-4 || player.getX() != player.prevX || player.getZ() != player.prevZ;
		if (player.isSprinting()) {
			if (s.exhausted) {
				player.setSprinting(false);
			} else {
				spend(player, 0.33F);
			}
		}
		if (player.isSwimming() || player.isTouchingWater() && !player.isOnGround() && moving) {
			spend(player, 0.15F);
		}
		if (player.isClimbing() && !player.isOnGround() && Math.abs(player.getY() - player.prevY) > 0.01) {
			spend(player, 0.2F);
		}
		if (player.isUsingItem() && (player.getActiveItem().getItem() instanceof BowItem || player.getActiveItem().getItem() instanceof CrossbowItem)) {
			spend(player, 0.12F);
		}
		// recuperarse
		if (now - s.lastSpend >= DELAY && s.value < MAX) {
			float rate = player.isSprinting() ? 0.0F : moving ? 0.55F : 0.9F;
			if (player.isSleeping()) {
				rate = 3.0F;
			}
			int food = player.getHungerManager().getFoodLevel();
			rate *= food <= 0 ? 0.2F : food < 6 ? 0.5F : 1.0F;
			if (Thirst.enabled() && Thirst.get(player) < 6.0F) {
				rate *= 0.5F;
			}
			if (Health.enabled()) {
				WoundData d = Health.get(player);
				if (d.fraction(BodyPart.TORSO) < 0.5F) {
					rate *= 0.6F;
				}
				if (d.isBroken(BodyPart.RIGHT_LEG) || d.isBroken(BodyPart.LEFT_LEG)) {
					rate *= 0.7F;
				}
			}
			rate *= 1.0F - 0.35F * Math.min(20, player.getArmor()) / 20.0F;
			rate *= (float) EwConfig.get().staminaRegen;
			// el atributo de aguante (fase 114)
			rate *= com.evolvedwarfare.skill.SurvivorAttributes.staminaRegen(player);
			s.value = Math.min(MAX, s.value + rate);
		}
		if (s.exhausted && s.value >= RECOVER) {
			s.exhausted = false;
		}
		if (s.exhausted && now % 20 == 0) {
			player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 30, 0, false, false, true));
			player.addStatusEffect(new StatusEffectInstance(StatusEffects.MINING_FATIGUE, 30, 0, false, false, true));
		}
		sync(player, s);
	}

	private static void sync(ServerPlayerEntity player, State s) {
		boolean changed = Math.abs(s.value - s.lastSent) >= 0.5F || s.exhausted != s.lastExhausted || (s.value >= MAX && s.lastSent < MAX);
		if (!changed && now % 40 != 0) {
			return;
		}
		if (ServerPlayNetworking.canSend(player, StaminaPayload.ID)) {
			ServerPlayNetworking.send(player, new StaminaPayload(s.value, s.exhausted, enabled() && !player.getAbilities().creativeMode));
		}
		s.lastSent = s.value;
		s.lastExhausted = s.exhausted;
	}
}
