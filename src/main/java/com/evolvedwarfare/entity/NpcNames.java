package com.evolvedwarfare.entity;

import net.minecraft.text.Text;
import net.minecraft.util.math.random.Random;

/** Nombres medievales para los NPCs del jugador. Aspectos 0-3: hombres; 4-5: mujeres. */
public final class NpcNames {
	public static final int VARIANTS = 6;
	public static final int FIRST_FEMALE_VARIANT = 4;

	private static final String[] MALE = {
			"Aldric", "Bermudo", "Rodrigo", "Sancho", "Gonzalo", "Tristán", "Alfonso", "Martín", "Fruela",
			"Nuño", "Garcí", "Pelayo", "Ordoño", "Rolando", "Godofredo", "Bernardo", "Íñigo", "Mendo"};
	private static final String[] FEMALE = {
			"Elvira", "Ximena", "Urraca", "Leonor", "Inés", "Teresa", "Sancha", "Berenguela", "Mencía",
			"Toda", "Aldonza", "Constanza", "Matilde", "Brianda"};

	private NpcNames() {
	}

	/** Aspecto y nombre al azar. */
	public static void randomize(AbstractNpcEntity npc, Random random) {
		int variant = random.nextInt(VARIANTS);
		npc.setVariant(variant);
		String[] names = variant >= FIRST_FEMALE_VARIANT ? FEMALE : MALE;
		npc.setCustomName(Text.literal(names[random.nextInt(names.length)]));
	}
}
