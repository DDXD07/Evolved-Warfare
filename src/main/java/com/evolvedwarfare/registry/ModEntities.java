package com.evolvedwarfare.registry;

import com.evolvedwarfare.EvolvedWarfare;
import com.evolvedwarfare.entity.AbstractNpcEntity;
import com.evolvedwarfare.entity.BanditEntity;
import com.evolvedwarfare.entity.RecruitEntity;
import com.evolvedwarfare.entity.SoldierEntity;
import com.evolvedwarfare.entity.WorkerEntity;
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

	/** Soldado del jugador (guerrero, bárbaro, arquero, ballestero o médico). */
	public static final EntityType<SoldierEntity> SOLDIER = Registry.register(Registries.ENTITY_TYPE,
			EvolvedWarfare.id("soldier"),
			EntityType.Builder.create(SoldierEntity::new, SpawnGroup.MISC)
					.dimensions(0.6F, 1.8F)
					.eyeHeight(1.62F)
					.maxTrackingRange(10)
					.build("soldier"));

	/** Trabajador del jugador (granjero, leñador, minero...): un recluta con oficio y puesto. */
	public static final EntityType<WorkerEntity> WORKER = Registry.register(Registries.ENTITY_TYPE,
			EvolvedWarfare.id("worker"),
			EntityType.Builder.create(WorkerEntity::new, SpawnGroup.MISC)
					.dimensions(0.6F, 1.8F)
					.eyeHeight(1.62F)
					.maxTrackingRange(10)
					.build("worker"));

	/** Bandido: enemigo de los jugadores. */
	public static final EntityType<BanditEntity> BANDIT = Registry.register(Registries.ENTITY_TYPE,
			EvolvedWarfare.id("bandit"),
			EntityType.Builder.create(BanditEntity::new, SpawnGroup.MONSTER)
					.dimensions(0.6F, 1.8F)
					.eyeHeight(1.62F)
					.maxTrackingRange(10)
					.build("bandit"));

	private ModEntities() {
	}

	public static void init() {
		FabricDefaultAttributeRegistry.register(RECRUIT, AbstractNpcEntity.createNpcAttributes());
		FabricDefaultAttributeRegistry.register(SOLDIER, AbstractNpcEntity.createNpcAttributes());
		FabricDefaultAttributeRegistry.register(WORKER, AbstractNpcEntity.createNpcAttributes());
		FabricDefaultAttributeRegistry.register(BANDIT, BanditEntity.createBanditAttributes());
	}
}
