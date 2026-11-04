package com.evolvedwarfare.client.catalog;

import com.evolvedwarfare.block.CatalogBlock;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.Perspective;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

/**
 * Lo que pasa al abrir un catálogo: se pasa a primera persona, la cámara se acerca al libro del mostrador y lo mira
 * desde arriba, la tapa se abre y entonces sube el libro de la pantalla (las páginas). Al cerrarlo, al revés. Todo en
 * milisegundos desde que empieza (o desde que se empieza a cerrar).
 */
public final class CatalogSession {
	/** Se acerca al libro. */
	static final float APPROACH = 650.0F;
	/** Se abre la tapa (de, a). */
	static final float COVER_FROM = 450.0F;
	static final float COVER_TO = 1050.0F;
	/** Sube el libro de la pantalla (de, a). */
	static final float GUI_FROM = 950.0F;
	static final float GUI_TO = 1300.0F;
	/** Al cerrar: baja el libro de la pantalla, se cierra la tapa, vuelve la cámara y se acaba. */
	static final float CLOSE_GUI = 280.0F;
	static final float CLOSE_COVER_FROM = 150.0F;
	static final float CLOSE_COVER_TO = 600.0F;
	static final float CLOSE_CAMERA_FROM = 350.0F;
	static final float CLOSE_CAMERA_TO = 950.0F;
	static final float CLOSE_END = 1000.0F;

	/** La vista de la cámara. */
	public record View(double x, double y, double z, float yaw, float pitch) {
	}

	@Nullable
	private static BlockPos book;
	private static Direction facing = Direction.SOUTH;
	private static long start = -1L;
	private static long closing = -1L;
	@Nullable
	private static Perspective previous;
	private static Vec3d from = Vec3d.ZERO;
	private static float fromYaw;
	private static float fromPitch;
	private static boolean openSound;

	private CatalogSession() {
	}

	private static long now() {
		return System.nanoTime() / 1_000_000L;
	}

	public static boolean active() {
		return start >= 0L;
	}

	@Nullable
	public static BlockPos book() {
		return book;
	}

	/** Empieza (al abrirse la pantalla del catálogo). */
	public static void begin(MinecraftClient client, @Nullable BlockPos bookPos) {
		start = now();
		closing = -1L;
		openSound = false;
		previous = client.options.getPerspective();
		if (!previous.isFirstPerson()) {
			client.options.setPerspective(Perspective.FIRST_PERSON);
		}
		PlayerEntity player = client.player;
		book = null;
		if (bookPos != null && player != null && client.world != null) {
			BlockState state = client.world.getBlockState(bookPos);
			if (state.getBlock() instanceof CatalogBlock && player.getEyePos().squaredDistanceTo(Vec3d.ofCenter(bookPos)) < 64.0) {
				book = bookPos.toImmutable();
				facing = state.get(CatalogBlock.FACING);
			}
		}
		if (player != null) {
			from = player.getCameraPosVec(1.0F);
			fromYaw = player.getYaw();
			fromPitch = player.getPitch();
		}
	}

	public static void beginClose() {
		if (active() && closing < 0L) {
			closing = now();
		}
	}

	public static boolean closingStarted() {
		return closing >= 0L;
	}

	/** ¿Ha terminado de cerrarse (ya se puede quitar la pantalla)? */
	public static boolean finished() {
		return closing >= 0L && now() - closing >= (book != null ? CLOSE_END : CLOSE_GUI);
	}

	/** Se acaba: vuelve la vista que tenía. */
	public static void end(MinecraftClient client) {
		if (previous != null && client.options.getPerspective() != previous) {
			client.options.setPerspective(previous);
		}
		previous = null;
		start = -1L;
		closing = -1L;
		book = null;
	}

	private static float elapsed() {
		return active() ? now() - start : 0.0F;
	}

	private static float closeElapsed() {
		return closing >= 0L ? now() - closing : 0.0F;
	}

	static float smooth(float t) {
		t = MathHelper.clamp(t, 0.0F, 1.0F);
		return t * t * (3.0F - 2.0F * t);
	}

	private static float between(float t, float a, float b) {
		return smooth((t - a) / (b - a));
	}

	/** ¿Hay que hacer sonar el libro al abrirse? (una vez) */
	static boolean takeOpenSound() {
		if (!openSound && book != null && elapsed() >= COVER_FROM) {
			openSound = true;
			return true;
		}
		return false;
	}

	/** Cuánto está abierta la tapa del libro de ese sitio (0 cerrado, 1 abierto). */
	public static float cover(BlockPos pos) {
		if (!active() || book == null || !book.equals(pos)) {
			return 0.0F;
		}
		float open = between(elapsed(), COVER_FROM, COVER_TO);
		if (closing >= 0L) {
			open *= 1.0F - between(closeElapsed(), CLOSE_COVER_FROM, CLOSE_COVER_TO);
		}
		return open;
	}

	/** Cuánto ha subido el libro de la pantalla (0 abajo, fuera; 1 en su sitio). */
	public static float gui() {
		if (!active()) {
			return 0.0F;
		}
		float in = book != null ? between(elapsed(), GUI_FROM, GUI_TO) : between(elapsed(), 0.0F, GUI_TO - GUI_FROM);
		if (closing >= 0L) {
			in *= 1.0F - between(closeElapsed(), 0.0F, CLOSE_GUI);
		}
		return in;
	}

	/** ¿Se puede leer y tocar ya? */
	public static boolean reading() {
		return active() && closing < 0L && gui() >= 1.0F;
	}

	/** La vista de la cámara mientras se acerca, lee y vuelve (null: la de siempre). */
	@Nullable
	public static View view(float tickDelta) {
		if (!active() || book == null) {
			return null;
		}
		float t = between(elapsed(), 0.0F, APPROACH);
		if (closing >= 0L) {
			t *= 1.0F - between(closeElapsed(), CLOSE_CAMERA_FROM, CLOSE_CAMERA_TO);
		}
		Vec3d center = new Vec3d(book.getX() + 0.5, book.getY() + 0.12, book.getZ() + 0.5);
		Vec3d eye = center.add(facing.getOffsetX() * 0.52, 0.74, facing.getOffsetZ() * 0.52);
		Vec3d look = center.subtract(eye);
		float yaw = (float) Math.toDegrees(Math.atan2(-look.x, look.z));
		float pitch = (float) Math.toDegrees(Math.atan2(-look.y, Math.sqrt(look.x * look.x + look.z * look.z)));
		Vec3d pos = from.lerp(eye, t);
		return new View(pos.x, pos.y, pos.z, MathHelper.lerpAngleDegrees(t, fromYaw, yaw), MathHelper.lerp(t, fromPitch, pitch));
	}
}
