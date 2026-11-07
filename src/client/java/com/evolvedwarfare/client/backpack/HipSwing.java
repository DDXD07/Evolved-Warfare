package com.evolvedwarfare.client.backpack;

import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;

/**
 * Las herramientas de la cadera cuelgan sueltas: un péndulo con muelle y rozamiento por jugador que se mueve con lo que
 * hace (al arrancar se quedan atrás, al frenar se van hacia delante, al girar se abren hacia fuera, al andar se balancean
 * con las piernas y al saltar o caer rebotan).
 */
public final class HipSwing {
	/** Hacia atrás (+) o delante, y hacia la izquierda del jugador (+) o la derecha, en radianes. */
	public static final class State {
		float pitch, pitchV, roll, rollV, lastPitch, lastRoll;
		double lastForward, lastSide, lastUp;
		boolean started;

		public float pitch(float tickDelta) {
			return MathHelper.lerp(tickDelta, lastPitch, pitch);
		}

		public float roll(float tickDelta) {
			return MathHelper.lerp(tickDelta, lastRoll, roll);
		}
	}

	private static final Map<Integer, State> STATES = new HashMap<>();
	private static final State REST = new State();

	private HipSwing() {
	}

	public static void register() {
		ClientTickEvents.END_WORLD_TICK.register(HipSwing::tick);
	}

	public static State of(PlayerEntity player) {
		return STATES.getOrDefault(player.getId(), REST);
	}

	private static void tick(ClientWorld world) {
		STATES.keySet().removeIf(id -> !(world.getEntityById(id) instanceof PlayerEntity));
		for (PlayerEntity p : world.getPlayers()) {
			State s = STATES.computeIfAbsent(p.getId(), k -> new State());
			double dx = p.getX() - p.prevX, dy = p.getY() - p.prevY, dz = p.getZ() - p.prevZ;
			if (dx * dx + dy * dy + dz * dz > 4.0) {
				dx = dy = dz = 0.0; // teletransporte
			}
			double r = p.bodyYaw * MathHelper.RADIANS_PER_DEGREE;
			double forward = dx * -Math.sin(r) + dz * Math.cos(r);
			double side = dx * Math.cos(r) + dz * Math.sin(r); // hacia la izquierda del jugador
			if (!s.started) {
				s.lastForward = forward;
				s.lastSide = side;
				s.lastUp = dy;
				s.started = true;
			}
			double af = forward - s.lastForward, as = side - s.lastSide, au = dy - s.lastUp;
			s.lastForward = forward;
			s.lastSide = side;
			s.lastUp = dy;
			// el giro del cuerpo también las abre (como una fuerza hacia fuera)
			float turn = MathHelper.wrapDegrees(p.bodyYaw - p.prevBodyYaw) * MathHelper.RADIANS_PER_DEGREE;
			// andar: las piernas las empujan a cada paso
			float step = MathHelper.sin(p.limbAnimator.getPos() * 0.6662F) * p.limbAnimator.getSpeed();
			s.lastPitch = s.pitch;
			s.lastRoll = s.roll;
			// donde quieren estar: con la velocidad se quedan un poco atrás (el aire, la inercia)
			float restPitch = (float) MathHelper.clamp(forward * 1.4, -0.5, 0.7);
			float restRoll = (float) MathHelper.clamp(side * 1.4, -0.5, 0.5);
			s.pitchV += (float) (af * 7.0) + step * 0.07F + (float) Math.abs(au) * 0.6F * Math.signum(s.pitchV + 1e-4F);
			s.rollV += (float) (as * 7.0) + turn * 0.9F + (float) au * 0.4F * Math.signum(s.roll + 1e-4F);
			s.pitchV += (restPitch - s.pitch) * 0.22F;
			s.rollV += (restRoll - s.roll) * 0.22F;
			s.pitchV *= 0.84F;
			s.rollV *= 0.84F;
			s.pitch = MathHelper.clamp(s.pitch + s.pitchV, -1.1F, 1.2F);
			s.roll = MathHelper.clamp(s.roll + s.rollV, -0.9F, 0.9F);
		}
	}
}
