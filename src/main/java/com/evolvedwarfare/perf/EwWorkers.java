package com.evolvedwarfare.perf;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Los hilos del mod (fase 55): trabajo pesado que no toca el mundo (dibujar los chunks del Atlas, precargar...) va aquí en
 * vez de al hilo del servidor o al del dibujo. Son de baja prioridad y, como mucho, la mitad de los núcleos (dejando al menos
 * uno para el juego).
 */
public final class EwWorkers {
	public static final int THREADS = Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors() / 2 - 1));
	public static final ExecutorService POOL = Executors.newFixedThreadPool(THREADS, new ThreadFactory() {
		private final AtomicInteger n = new AtomicInteger();

		@Override
		public Thread newThread(Runnable r) {
			Thread t = new Thread(r, "ew-worker-" + n.incrementAndGet());
			t.setDaemon(true);
			t.setPriority(Thread.MIN_PRIORITY + 1);
			return t;
		}
	});

	private EwWorkers() {
	}
}
