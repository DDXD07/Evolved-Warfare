package com.evolvedwarfare.town;

import net.minecraft.entity.mob.MobEntity;

/** Un NPC con vida en un pueblo (reclutas, trabajadores, soldados, guardias y mercaderes). */
public interface Townsfolk {
	NpcProfile ewProfile();

	/** El perfil ha cambiado (para que lo vea el cliente). */
	void ewProfileChanged();

	/** Lo que el cliente sabe de su perfil (oficio y carácter, ver {@link NpcProfile#packed}). */
	int ewProfileBits();

	default MobEntity ewSelf() {
		return (MobEntity) this;
	}

	/** El oficio con el que nace (null: uno de vecino al azar). */
	@org.jetbrains.annotations.Nullable
	default NpcJob ewBirthJob() {
		return null;
	}

	/** ¿Tiene dueño (lo ha contratado alguien)? */
	boolean ewHired();
}
