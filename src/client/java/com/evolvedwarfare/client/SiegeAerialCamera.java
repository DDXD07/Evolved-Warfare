package com.evolvedwarfare.client;

import com.evolvedwarfare.network.SiegeAimPayload;
import com.evolvedwarfare.siege.SiegeBallistics;
import com.evolvedwarfare.siege.SiegeEngineEntity;
import com.evolvedwarfare.siege.SiegeKind;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * La cámara aérea de las máquinas de asedio: montado en una catapulta, una balista o un trebuchet, <b>mantén el clic
 * izquierdo</b> y la vista sube por encima del blanco. El ratón mueve el punto al que apuntas (por el suelo, también
 * detrás de los muros); se ve el recorrido del tiro, dónde cae y la zona que alcanza ({@link SiegeAimPreview}).
 * Espacio dispara como siempre; al soltar el clic vuelves al asiento.
 *
 * <p>Mientras vas montado en una máquina el clic izquierdo no pega ni rompe bloques (así tampoco se rompe la máquina
 * sin querer en creativo). La cámara se pone con {@code CameraMixin}, el ratón lo recoge {@code EntityLookMixin} y el
 * punto se manda al servidor ({@link SiegeAimPayload}) para que el disparo vaya ahí.</p>
 */
public final class SiegeAerialCamera {
	/** Inclinación de la cámara (grados hacia abajo). */
	private static final float PITCH = 58.0F;

	private static boolean active;
	private static Perspective savedPerspective = Perspective.FIRST_PERSON;
	/** El punto del suelo al que se apunta. */
	private static Vec3d target = Vec3d.ZERO;
	/** Lo que sigue la cámara (el blanco, suavizado). */
	private static Vec3d focus = Vec3d.ZERO;
	private static Vec3d cam = Vec3d.ZERO;
	private static Vec3d prevCam = Vec3d.ZERO;
	private static float yaw;
	private static float prevYaw;
	private static float pitch;
	private static float prevPitch;
	/** De la vista del asiento (0) a la aérea (1). */
	private static float blend;
	private static Vec3d startEye = Vec3d.ZERO;
	private static float startYaw;
	private static float startPitch;
	private static double height = 20.0;

	private SiegeAerialCamera() {
	}

	public static void register() {
		ClientTickEvents.START_CLIENT_TICK.register(SiegeAerialCamera::input);
		ClientTickEvents.END_CLIENT_TICK.register(SiegeAerialCamera::tick);
		AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> manning(player) ? ActionResult.FAIL : ActionResult.PASS);
		AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> manning(player) ? ActionResult.FAIL : ActionResult.PASS);
		HudRenderCallback.EVENT.register((context, tickCounter) -> hud(context));
	}

	private static boolean manning(PlayerEntity player) {
		return player.getVehicle() instanceof SiegeEngineEntity;
	}

	public static boolean isActive() {
		return active;
	}

	/** El punto al que se apunta con la cámara aérea (null si no se está usando). */
	@Nullable
	public static Vec3d target() {
		return active ? target : null;
	}

	// ------------------------------------------------------------------ el clic izquierdo

	/** Al principio del tic (antes que vanilla): se queda con el clic izquierdo mientras vas montado. */
	private static void input(MinecraftClient client) {
		ClientPlayerEntity player = client.player;
		if (player == null || !(player.getVehicle() instanceof SiegeEngineEntity engine)) {
			if (active) {
				stop(client);
			}
			return;
		}
		KeyBinding attack = client.options.attackKey;
		boolean held = client.currentScreen == null && client.mouse.isCursorLocked() && physicallyDown(client, attack);
		while (attack.wasPressed()) {
			// nada: montado, el clic izquierdo es la cámara aérea
		}
		attack.setPressed(false);
		boolean wanted = held && engine.getKind().fires() && !player.isSpectator();
		if (wanted && !active) {
			start(client, player, engine);
		} else if (!wanted && active) {
			stop(client);
		}
	}

	/** Si la tecla está apretada de verdad (vanilla la damos por suelta para que no pegue). */
	private static boolean physicallyDown(MinecraftClient client, KeyBinding key) {
		InputUtil.Key bound = KeyBindingHelper.getBoundKeyOf(key);
		long window = client.getWindow().getHandle();
		if (bound.getCategory() == InputUtil.Type.MOUSE) {
			return GLFW.glfwGetMouseButton(window, bound.getCode()) == GLFW.GLFW_PRESS;
		}
		if (bound.getCategory() == InputUtil.Type.KEYSYM && bound.getCode() != InputUtil.UNKNOWN_KEY.getCode()) {
			return InputUtil.isKeyPressed(window, bound.getCode());
		}
		return false;
	}

	private static void start(MinecraftClient client, ClientPlayerEntity player, SiegeEngineEntity engine) {
		active = true;
		savedPerspective = client.options.getPerspective();
		client.options.setPerspective(Perspective.THIRD_PERSON_BACK);
		// se empieza apuntando adonde caería el tiro ahora mismo
		Vec3d impact = SiegeAimPreview.impact();
		Vec3d fallback = engine.getPos().add(engine.forward().multiply(defaultDistance(engine.getKind())));
		target = impact != null && impact.squaredDistanceTo(engine.getPos()) > 16.0 ? impact : fallback;
		target = clamp(engine, target);
		focus = target;
		startEye = player.getCameraPosVec(1.0F);
		startYaw = player.getYaw();
		startPitch = player.getPitch();
		cam = prevCam = startEye;
		yaw = prevYaw = startYaw;
		pitch = prevPitch = startPitch;
		blend = 0.0F;
	}

	private static void stop(MinecraftClient client) {
		active = false;
		client.options.setPerspective(savedPerspective);
		if (client.player != null && client.getNetworkHandler() != null) {
			ClientPlayNetworking.send(new SiegeAimPayload(false, 0.0, 0.0, 0.0));
		}
	}

	private static double defaultDistance(SiegeKind kind) {
		return switch (kind) {
			case CATAPULT -> 30.0;
			case TREBUCHET -> 60.0;
			default -> 40.0;
		};
	}

	// ------------------------------------------------------------------ el ratón mueve el blanco

	/** Lo llama EntityLookMixin con lo que se ha movido el ratón: true si lo usamos (y el jugador no gira la cabeza). */
	public static boolean captureLook(Entity entity, double dx, double dy) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (!active || entity != client.player || !(client.player.getVehicle() instanceof SiegeEngineEntity engine)) {
			return false;
		}
		double scale = 0.0035 * height;
		float y = engine.getYaw() * MathHelper.RADIANS_PER_DEGREE;
		Vec3d forward = new Vec3d(-MathHelper.sin(y), 0.0, MathHelper.cos(y));
		Vec3d right = new Vec3d(-MathHelper.cos(y), 0.0, -MathHelper.sin(y));
		target = clamp(engine, target.add(right.multiply(dx * scale)).add(forward.multiply(-dy * scale)));
		return true;
	}

	/** Dentro del arco de la máquina y entre poco más cerca que lo mínimo y algo más lejos que su alcance. */
	private static Vec3d clamp(SiegeEngineEntity engine, Vec3d point) {
		SiegeKind kind = engine.getKind();
		Vec3d muzzle = engine.muzzle();
		double dx = point.x - muzzle.x;
		double dz = point.z - muzzle.z;
		double distance = Math.sqrt(dx * dx + dz * dz);
		double max = Math.min(SiegeBallistics.LOOK_RANGE, SiegeBallistics.range(kind, SiegeBallistics.farthestElevation(kind)) * 1.15 + 6.0);
		double min = 4.0;
		float bearing = distance < 1.0E-3 ? engine.getYaw() : SiegeBallistics.yawTo(muzzle, point);
		bearing = SiegeBallistics.clampYaw(kind, engine.getYaw(), bearing);
		distance = MathHelper.clamp(distance, min, max);
		float b = bearing * MathHelper.RADIANS_PER_DEGREE;
		double x = muzzle.x - MathHelper.sin(b) * distance;
		double z = muzzle.z + MathHelper.cos(b) * distance;
		return new Vec3d(x, ground(engine, x, z, point.y), z);
	}

	/** Altura del suelo en ese punto (lo de más arriba que pararía una piedra), o la que había si no está cargado. */
	private static double ground(SiegeEngineEntity engine, double x, double z, double fallback) {
		int bx = MathHelper.floor(x);
		int bz = MathHelper.floor(z);
		if (!engine.getWorld().isChunkLoaded(bx >> 4, bz >> 4)) {
			return fallback;
		}
		int top = engine.getWorld().getTopY(Heightmap.Type.MOTION_BLOCKING, bx, bz);
		return top <= engine.getWorld().getBottomY() ? fallback : top;
	}

	// ------------------------------------------------------------------ cada tic

	private static void tick(MinecraftClient client) {
		ClientPlayerEntity player = client.player;
		if (active && player != null && player.getVehicle() instanceof SiegeEngineEntity engine && !client.isPaused()) {
			target = clamp(engine, target);
			focus = focus.lerp(target, 0.45);
			double distance = Math.sqrt(MathHelper.squaredHypot(target.x - engine.getX(), target.z - engine.getZ()));
			height = MathHelper.clamp(14.0 + distance * 0.25, 16.0, 48.0);
			float engineYaw = engine.getYaw();
			float y = engineYaw * MathHelper.RADIANS_PER_DEGREE;
			double back = height / Math.tan(PITCH * MathHelper.RADIANS_PER_DEGREE);
			Vec3d aerial = focus.add(MathHelper.sin(y) * back, height, -MathHelper.cos(y) * back);
			blend = Math.min(1.0F, blend + 0.16F);
			float t = blend * blend * (3.0F - 2.0F * blend);
			prevCam = cam;
			prevYaw = yaw;
			prevPitch = pitch;
			cam = player.getCameraPosVec(1.0F).lerp(aerial, t);
			yaw = startYaw + MathHelper.wrapDegrees(engineYaw - startYaw) * t;
			pitch = MathHelper.lerp(t, startPitch, PITCH);
			// el jugador mira hacia el blanco (para la torreta de la balista y al volver al asiento)
			Vec3d eye = player.getEyePos();
			double horizontal = Math.sqrt(MathHelper.squaredHypot(target.x - eye.x, target.z - eye.z));
			player.setYaw(SiegeBallistics.yawTo(eye, target));
			player.setPitch((float) -(MathHelper.atan2(target.y - eye.y, horizontal) * MathHelper.DEGREES_PER_RADIAN));
			player.setHeadYaw(player.getYaw());
			ClientPlayNetworking.send(new SiegeAimPayload(true, target.x, target.y, target.z));
		}
		SiegeAimPreview.tick(client);
	}

	// ------------------------------------------------------------------ lo que usa la cámara

	/** Posición y giro de la cámara para este fotograma; null si no está activa. */
	@Nullable
	public static View view(float tickDelta) {
		if (!active) {
			return null;
		}
		Vec3d pos = prevCam.lerp(cam, tickDelta);
		float viewYaw = prevYaw + MathHelper.wrapDegrees(yaw - prevYaw) * tickDelta;
		return new View(pos.x, pos.y, pos.z, viewYaw, MathHelper.lerp(tickDelta, prevPitch, pitch));
	}

	public record View(double x, double y, double z, float yaw, float pitch) {
	}

	// ------------------------------------------------------------------ texto arriba

	private static void hud(DrawContext context) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (!active || client.options.hudHidden || client.player == null) {
			return;
		}
		TextRenderer font = client.textRenderer;
		SiegeBallistics.Aim aim = SiegeAimPreview.aim();
		Vec3d muzzle = aim != null ? aim.muzzle() : client.player.getPos();
		int distance = (int) Math.round(Math.sqrt(MathHelper.squaredHypot(target.x - muzzle.x, target.z - muzzle.z)));
		boolean ok = aim != null && aim.reachable();
		Text title = Text.translatable("hud.evolvedwarfare.siege.aerial", distance).formatted(Formatting.BOLD);
		Text status = ok ? Text.translatable("hud.evolvedwarfare.siege.aerial.in_range").formatted(Formatting.GREEN)
				: Text.translatable("hud.evolvedwarfare.siege.aerial.out_of_range").formatted(Formatting.RED);
		Text hint = Text.translatable("hud.evolvedwarfare.siege.aerial.hint").formatted(Formatting.GRAY);
		int x = context.getScaledWindowWidth() / 2;
		context.drawCenteredTextWithShadow(font, title, x, 10, 0xFFFFFF);
		context.drawCenteredTextWithShadow(font, status, x, 22, 0xFFFFFF);
		context.drawCenteredTextWithShadow(font, hint, x, 34, 0xFFFFFF);
	}
}
