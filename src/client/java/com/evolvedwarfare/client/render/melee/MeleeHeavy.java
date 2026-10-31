package com.evolvedwarfare.client.render.melee;

import com.evolvedwarfare.weapon.MedievalWeapons;
import com.evolvedwarfare.weapon.MeleeSwing;
import com.evolvedwarfare.weapon.WeaponType;
import java.util.Map;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.entity.LivingEntity;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import org.jetbrains.annotations.Nullable;

/**
 * El golpe fuerte en el cliente: en qué punto está cada uno (cargando, cargado, soltando el golpe o volviendo a su
 * sitio si soltó antes) para las animaciones de primera y tercera persona, la barrita bajo la mira mientras cargas y
 * un aviso al estar cargado del todo.
 */
public final class MeleeHeavy {
	/** Lo que tarda en volver a su sitio si se suelta antes de tiempo (tics). */
	private static final float CANCEL_TICKS = 5.0F;
	public static final Pose NONE = new Pose(0, 0.0F, 0.0F);

	/** kind: 0 nada, 1 cargando (o volviendo: progress baja), 2 golpe fuerte (0..1). */
	public record Pose(int kind, float progress, float now) {
		public boolean active() {
			return kind != 0;
		}

		public float[] fp(WeaponType type) {
			if (kind == 2) {
				return MeleeAnimations.heavyFp(type, progress);
			}
			float[] v = MeleeAnimations.chargeFp(type, progress);
			if (progress >= 1.0F) {
				v[MeleeAnimations.FP_DY] += MathHelper.sin(now * 2.3F) * 0.004F;
				v[MeleeAnimations.FP_ROLL] += MathHelper.sin(now * 3.1F) * 0.8F;
			}
			float twirl = MeleeAnimations.twirl(type) * MathHelper.RADIANS_PER_DEGREE;
			if (twirl > 0.0F) {
				// la bola da vueltas: el arma describe un círculo pequeño y se balancea
				v[MeleeAnimations.FP_DX] += MathHelper.cos(now * twirl) * 0.05F * progress;
				v[MeleeAnimations.FP_DY] += MathHelper.sin(now * twirl) * 0.05F * progress;
				v[MeleeAnimations.FP_ROLL] += MathHelper.sin(now * twirl) * 20.0F * progress;
			}
			return v;
		}

		public float[] tp(WeaponType type) {
			if (kind == 2) {
				return MeleeAnimations.heavyTp(type, progress);
			}
			float[] v = MeleeAnimations.chargeTp(type, progress);
			if (progress >= 1.0F) {
				v[MeleeAnimations.TP_ARM_PITCH] += MathHelper.sin(now * 2.7F) * 1.5F;
			}
			float twirl = MeleeAnimations.twirl(type) * MathHelper.RADIANS_PER_DEGREE;
			if (twirl > 0.0F) {
				v[MeleeAnimations.TP_ARM_ROLL] += MathHelper.sin(now * twirl) * 18.0F * progress;
				v[MeleeAnimations.TP_ARM_YAW] += MathHelper.cos(now * twirl) * 18.0F * progress;
			}
			return v;
		}
	}

	private static final class State {
		float charge;
		/** Cargado del todo (en tics enteros, como lo cuenta el servidor). */
		boolean full;
		float last = -1.0F;
		float releasedAt = -1.0F;
		float cancelAt = -1.0F;
		float cancelFrom;
	}

	private static final Map<LivingEntity, State> STATES = new WeakHashMap<>();
	private static boolean readySound;

	private MeleeHeavy() {
	}

	/** Si está cargando un golpe fuerte con esa arma (en la mano principal). */
	@Nullable
	public static WeaponType charging(LivingEntity entity) {
		if (!entity.isUsingItem() || entity.getActiveHand() != Hand.MAIN_HAND) {
			return null;
		}
		return MedievalWeapons.typeOf(entity.getActiveItem());
	}

	/** {@code now}: edad + tickDelta (lo mismo que animationProgress). */
	public static Pose pose(LivingEntity entity, WeaponType type, float now) {
		State s = STATES.computeIfAbsent(entity, e -> new State());
		if (s.last >= 0.0F && (now - s.last > 3.0F || now < s.last)) {
			// hacía rato que no tenía el arma en la mano (cambió de objeto): nada pendiente
			s.charge = 0.0F;
			s.full = false;
			s.releasedAt = -1.0F;
			s.cancelAt = -1.0F;
		}
		s.last = now;
		if (charging(entity) == type) {
			s.charge = progress(entity, type, now - entity.age);
			s.full = s.charge >= 1.0F;
			s.releasedAt = -1.0F;
			s.cancelAt = -1.0F;
			return new Pose(1, s.charge, now);
		}
		if (s.charge > 0.0F) {
			// acaba de soltar: golpe si estaba lleno, si no, vuelta atrás
			if (s.full) {
				s.releasedAt = now;
			} else {
				s.cancelFrom = s.charge;
				s.cancelAt = now;
			}
			s.charge = 0.0F;
			s.full = false;
		}
		if (s.releasedAt >= 0.0F) {
			float p = (now - s.releasedAt) / MeleeSwing.heavyTicks(type);
			if (p >= 0.0F && p < 1.0F) {
				return new Pose(2, p, now);
			}
			s.releasedAt = -1.0F;
		}
		if (s.cancelAt >= 0.0F) {
			float t = (now - s.cancelAt) / CANCEL_TICKS;
			if (t >= 0.0F && t < 1.0F) {
				t = t * t * (3.0F - 2.0F * t);
				return new Pose(1, s.cancelFrom * (1.0F - t), now);
			}
			s.cancelAt = -1.0F;
		}
		return NONE;
	}

	/**
	 * Cuánto lleva cargado (0..1). Llega a 1 solo con los tics enteros de carga (lo mismo que mira el servidor para dar
	 * el golpe), así que la barra llena y la postura de cargado del todo significan que al soltar sale el golpe.
	 */
	private static float progress(LivingEntity entity, WeaponType type, float tickDelta) {
		int used = entity.getItemUseTime();
		int need = MeleeSwing.chargeTicks(type);
		if (used >= need) {
			return 1.0F;
		}
		return MathHelper.clamp((used + tickDelta) / need, 0.0F, 0.97F);
	}

	public static void register() {
		HudRenderCallback.EVENT.register((context, tickCounter) -> bar(context, tickCounter.getTickDelta(true)));
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			ClientPlayerEntity player = client.player;
			WeaponType type = player == null ? null : charging(player);
			if (type == null) {
				readySound = false;
				return;
			}
			if (!readySound && player.getItemUseTime() >= MeleeSwing.chargeTicks(type)) {
				readySound = true;
				client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 1.6F, 0.3F));
			}
		});
	}

	/** La barrita bajo la mira (debajo del indicador de ataque de vanilla); dorada y latiendo cuando está llena. */
	private static void bar(DrawContext context, float tickDelta) {
		MinecraftClient client = MinecraftClient.getInstance();
		ClientPlayerEntity player = client.player;
		if (player == null || client.options.hudHidden || !client.options.getPerspective().isFirstPerson() || client.currentScreen != null) {
			return;
		}
		WeaponType type = charging(player);
		if (type == null) {
			return;
		}
		float progress = progress(player, type, tickDelta);
		int width = 16;
		int x = context.getScaledWindowWidth() / 2 - width / 2;
		int y = context.getScaledWindowHeight() / 2 + 15;
		context.fill(x - 1, y - 1, x + width + 1, y + 3, 0x90000000);
		int color;
		if (progress >= 1.0F) {
			float pulse = 0.75F + 0.25F * MathHelper.sin((player.age + tickDelta) * 0.6F);
			color = ((int) (pulse * 255.0F) << 24) | 0xFFC23D;
		} else {
			color = 0xFFE6E6E6;
		}
		context.fill(x, y, x + Math.round(width * progress), y + 2, color);
	}
}
