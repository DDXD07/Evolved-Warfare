package com.evolvedwarfare.entity;

import net.minecraft.entity.EntityType;
import net.minecraft.text.Text;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;

/**
 * El recluta: un NPC sin oficio todavía. Vive en las ciudades, campamentos y torres que genera el mod
 * hasta que alguien lo contrata. En fases siguientes podrá convertirse en soldado o trabajador.
 */
public class RecruitEntity extends AbstractNpcEntity {
	/** Aspectos disponibles: 0-3 hombres, 4-5 mujeres. */
	public static final int VARIANTS = 6;
	private static final int FIRST_FEMALE_VARIANT = 4;

	private static final String[] MALE_NAMES = {
			"Aldric", "Bermudo", "Rodrigo", "Sancho", "Gonzalo", "Tristán", "Alfonso", "Martín", "Fruela",
			"Nuño", "Garcí", "Pelayo", "Ordoño", "Rolando", "Godofredo", "Bernardo", "Íñigo", "Mendo"};
	private static final String[] FEMALE_NAMES = {
			"Elvira", "Ximena", "Urraca", "Leonor", "Inés", "Teresa", "Sancha", "Berenguela", "Mencía",
			"Toda", "Aldonza", "Constanza", "Matilde", "Brianda"};

	public RecruitEntity(EntityType<? extends RecruitEntity> type, World world) {
		super(type, world);
	}

	/** Aspecto y nombre al azar (al generarse en una ciudad, campamento o torre). */
	@Override
	protected void initAppearance(Random random) {
		int variant = random.nextInt(VARIANTS);
		setVariant(variant);
		String[] names = variant >= FIRST_FEMALE_VARIANT ? FEMALE_NAMES : MALE_NAMES;
		setCustomName(Text.literal(names[random.nextInt(names.length)]));
	}
}
