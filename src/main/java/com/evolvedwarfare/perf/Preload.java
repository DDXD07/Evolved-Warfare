package com.evolvedwarfare.perf;

import com.evolvedwarfare.EvolvedWarfare;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import net.fabricmc.loader.api.FabricLoader;

/**
 * La precarga (fase 55): mientras el juego abre (y carga sus recursos), un hilo aparte carga todas las clases del mod (sin
 * ejecutarlas), para que la primera vez que sale algo (un comerciante, un tren, el Atlas...) no haya un tirón por cargarlas.
 * Lo que quiera calentarse además (modelos, cachés...) se añade con {@link #also(Runnable)}.
 */
public final class Preload {
	private static final List<Runnable> EXTRA = new ArrayList<>();
	private static volatile boolean started;

	private Preload() {
	}

	/** Algo más que hacer en la precarga (en el hilo de la precarga: no puede tocar el mundo ni OpenGL). */
	public static synchronized void also(Runnable task) {
		EXTRA.add(task);
	}

	public static synchronized void start() {
		if (started) {
			return;
		}
		started = true;
		Thread t = new Thread(Preload::run, "ew-preload");
		t.setDaemon(true);
		t.setPriority(Thread.MIN_PRIORITY + 1);
		t.start();
	}

	private static void run() {
		long t0 = System.nanoTime();
		int loaded = 0, failed = 0;
		ClassLoader loader = Preload.class.getClassLoader();
		List<String> names = new ArrayList<>();
		for (Path root : FabricLoader.getInstance().getModContainer(EvolvedWarfare.MOD_ID).map(c -> c.getRootPaths()).orElse(List.of())) {
			Path base = root.resolve("com/evolvedwarfare");
			if (!Files.isDirectory(base)) {
				continue;
			}
			try (Stream<Path> files = Files.walk(base)) {
				files.filter(p -> p.toString().endsWith(".class")).forEach(p -> {
					String rel = root.relativize(p).toString().replace('\\', '/');
					names.add(rel.substring(0, rel.length() - 6).replace('/', '.'));
				});
			} catch (Exception e) {
				EvolvedWarfare.LOGGER.debug("Precarga: no se pudo recorrer {}", base, e);
			}
		}
		for (String name : names) {
			// las mezclas no se pueden cargar a mano; las pruebas no van en el jar
			if (name.contains(".mixin.") || name.contains(".gametest.")) {
				continue;
			}
			try {
				Class.forName(name, false, loader);
				loaded++;
			} catch (Throwable e) {
				failed++;
			}
		}
		long t1 = System.nanoTime();
		List<Runnable> extra;
		synchronized (Preload.class) {
			extra = new ArrayList<>(EXTRA);
		}
		for (Runnable r : extra) {
			try {
				r.run();
			} catch (Throwable e) {
				EvolvedWarfare.LOGGER.debug("Precarga: falló una tarea", e);
			}
		}
		EvolvedWarfare.LOGGER.info("Precarga de Evolved Warfare: {} clases en {} ms ({} sin cargar), {} tareas más en {} ms, {} hilos de trabajo", loaded,
				(t1 - t0) / 1_000_000, failed, extra.size(), (System.nanoTime() - t1) / 1_000_000, EwWorkers.THREADS);
	}
}
