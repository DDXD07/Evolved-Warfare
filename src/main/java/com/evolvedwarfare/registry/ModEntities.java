package com.evolvedwarfare.registry;

import com.evolvedwarfare.EvolvedWarfare;
import com.evolvedwarfare.entity.AbstractNpcEntity;
import com.evolvedwarfare.entity.RecruitEntity;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

public final class ModEntities {
	/** El recluta: NPC básico del que salen todas las demás clases (soldados, trabajadores...). */
	public static final EntityType<RecruitEntity> RECRUIT = Registry.register(Registries.ENTITY_TYPE,
			EvolvedWarfare.id("recruit"),
			EntityType.Builder.create(RecruitEntity::new, SpawnGroup.MISC)
					.dimensions(0.6F, 1.8F)
					.eyeHeight(1.62F)
					.maxTrackingRange(10)
					.build("recruit"));

	private ModEntities() {
	}

	public static void init() {
		FabricDefaultAttributeRegistry.register(RECRUIT, AbstractNpcEntity.createNpcAttributes());
	}
}
