package com.evolvedwarfare.registry;

import com.evolvedwarfare.EvolvedWarfare;
import com.evolvedwarfare.entity.AbstractNpcEntity;
import com.evolvedwarfare.entity.BanditEntity;
import com.evolvedwarfare.entity.FactionNpcEntity;
import com.evolvedwarfare.entity.PirateEntity;
import com.evolvedwarfare.entity.RecruitEntity;
import com.evolvedwarfare.entity.SoldierEntity;
import com.evolvedwarfare.entity.TraderEntity;
import com.evolvedwarfare.entity.VikingEntity;
import com.evolvedwarfare.entity.WorkerEntity;
import com.evolvedwarfare.siege.SiegeEngineEntity;
import com.evolvedwarfare.siege.SiegeKind;
import com.evolvedwarfare.siege.SiegeShotEntity;
import com.evolvedwarfare.weapon.JavelinEntity;
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

	/** Comerciante de ciudad (fase 9): comercia con Glory Coins. */
	public static final EntityType<TraderEntity> TRADER = Registry.register(Registries.ENTITY_TYPE,
			EvolvedWarfare.id("trader"),
			EntityType.Builder.create(TraderEntity::new, SpawnGroup.MISC)
					.dimensions(0.6F, 1.8F)
					.eyeHeight(1.62F)
					.maxTrackingRange(10)
					.build("trader"));

	/** Guardia de una ciudad amurallada: no se contrata; va a por quien haga daño a su gente. */
	public static final EntityType<com.evolvedwarfare.entity.CityGuardEntity> CITY_GUARD = Registry.register(Registries.ENTITY_TYPE,
			EvolvedWarfare.id("city_guard"),
			EntityType.Builder.create(com.evolvedwarfare.entity.CityGuardEntity::new, SpawnGroup.MISC)
					.dimensions(0.6F, 1.8F)
					.eyeHeight(1.62F)
					.maxTrackingRange(10)
					.build("city_guard"));

	/** Bandido: enemigo de los jugadores. */
	public static final EntityType<BanditEntity> BANDIT = Registry.register(Registries.ENTITY_TYPE,
			EvolvedWarfare.id("bandit"),
			EntityType.Builder.create(BanditEntity::new, SpawnGroup.MONSTER)
					.dimensions(0.6F, 1.8F)
					.eyeHeight(1.62F)
					.maxTrackingRange(10)
					.build("bandit"));

	/** Vikingo: guerrero del norte (fase 5). */
	public static final EntityType<VikingEntity> VIKING = Registry.register(Registries.ENTITY_TYPE,
			EvolvedWarfare.id("viking"),
			EntityType.Builder.create(VikingEntity::new, SpawnGroup.MONSTER)
					.dimensions(0.6F, 1.8F)
					.eyeHeight(1.62F)
					.maxTrackingRange(10)
					.build("viking"));

	/** Pirata: saqueador de las costas (fase 5). */
	public static final EntityType<PirateEntity> PIRATE = Registry.register(Registries.ENTITY_TYPE,
			EvolvedWarfare.id("pirate"),
			EntityType.Builder.create(PirateEntity::new, SpawnGroup.MONSTER)
					.dimensions(0.6F, 1.8F)
					.eyeHeight(1.62F)
					.maxTrackingRange(10)
					.build("pirate"));

	/** Carreta de 5x3 sobre la que se construye bloque a bloque (la llevas tú o un caballo). */
	public static final EntityType<com.evolvedwarfare.cart.CartEntity> CART = Registry.register(Registries.ENTITY_TYPE,
			EvolvedWarfare.id("cart"),
			EntityType.Builder.<com.evolvedwarfare.cart.CartEntity>create(com.evolvedwarfare.cart.CartEntity::new, SpawnGroup.MISC)
					.dimensions(3.4F, 1.75F)
					.maxTrackingRange(10)
					.trackingTickInterval(2)
					.build("cart"));

	/** Un rastrillo o un puente levadizo mientras se mueve (lo crea el torno; se dibuja con los bloques de la puerta). */
	public static final EntityType<com.evolvedwarfare.block.GateMoverEntity> GATE_MOVER = Registry.register(Registries.ENTITY_TYPE,
			EvolvedWarfare.id("gate_mover"),
			EntityType.Builder.<com.evolvedwarfare.block.GateMoverEntity>create(com.evolvedwarfare.block.GateMoverEntity::new, SpawnGroup.MISC)
					.dimensions(1.0F, 1.0F)
					.maxTrackingRange(10)
					.trackingTickInterval(20)
					.disableSummon()
					.makeFireImmune()
					.build("gate_mover"));

	/** Máquinas de asedio (fase 7). */
	public static final EntityType<SiegeEngineEntity> CATAPULT = engine(SiegeKind.CATAPULT, 3.0F, 2.0F);
	public static final EntityType<SiegeEngineEntity> BALLISTA = engine(SiegeKind.BALLISTA, 1.6F, 1.7F);
	public static final EntityType<SiegeEngineEntity> TREBUCHET = engine(SiegeKind.TREBUCHET, 5.0F, 6.3F);
	public static final EntityType<SiegeEngineEntity> RAM = engine(SiegeKind.RAM, 3.0F, 2.9F);

	/** Proyectiles de las máquinas (piedra, pedrusco, vasija incendiaria, virote). */
	public static final EntityType<SiegeShotEntity> SIEGE_SHOT = Registry.register(Registries.ENTITY_TYPE,
			EvolvedWarfare.id("siege_shot"),
			EntityType.Builder.<SiegeShotEntity>create(SiegeShotEntity::new, SpawnGroup.MISC)
					.dimensions(0.5F, 0.5F)
					.maxTrackingRange(10)
					.trackingTickInterval(10)
					.build("siege_shot"));

	/** Jabalina lanzada (fase 8). */
	public static final EntityType<JavelinEntity> JAVELIN = Registry.register(Registries.ENTITY_TYPE,
			EvolvedWarfare.id("javelin"),
			EntityType.Builder.<JavelinEntity>create(JavelinEntity::new, SpawnGroup.MISC)
					.dimensions(0.5F, 0.5F)
					.eyeHeight(0.13F)
					.maxTrackingRange(4)
					.trackingTickInterval(20)
					.build("javelin"));

	/** Bala de mosquete en vuelo (fase 10). */
	public static final EntityType<com.evolvedwarfare.weapon.MusketBallEntity> MUSKET_BALL = Registry.register(Registries.ENTITY_TYPE,
			EvolvedWarfare.id("musket_ball"),
			EntityType.Builder.<com.evolvedwarfare.weapon.MusketBallEntity>create(com.evolvedwarfare.weapon.MusketBallEntity::new,
							SpawnGroup.MISC)
					.dimensions(0.15F, 0.15F)
					.maxTrackingRange(4)
					.trackingTickInterval(10)
					.build("musket_ball"));

	private static EntityType<SiegeEngineEntity> engine(SiegeKind kind, float width, float height) {
		return Registry.register(Registries.ENTITY_TYPE, EvolvedWarfare.id(kind.id()),
				EntityType.Builder.<SiegeEngineEntity>create((type, world) -> new SiegeEngineEntity(type, world, kind), SpawnGroup.MISC)
						.dimensions(width, height)
						.maxTrackingRange(10)
						.trackingTickInterval(2)
						.build(kind.id()));
	}

	public static EntityType<SiegeEngineEntity> engineType(SiegeKind kind) {
		return switch (kind) {
			case CATAPULT -> CATAPULT;
			case BALLISTA -> BALLISTA;
			case TREBUCHET -> TREBUCHET;
			case RAM -> RAM;
		};
	}

	/** Lo que se corta con el desmembramiento (brazos, piernas, cabezas). */
	// el Nether bíblico
	public static final EntityType<com.evolvedwarfare.hell.DemonEntity> DEMON = Registry.register(Registries.ENTITY_TYPE, EvolvedWarfare.id("demon"),
			EntityType.Builder.<com.evolvedwarfare.hell.DemonEntity>create(com.evolvedwarfare.hell.DemonEntity::new, SpawnGroup.MONSTER)
					.dimensions(0.9F, 2.6F).eyeHeight(2.3F).makeFireImmune().maxTrackingRange(10).build("demon"));
	public static final EntityType<com.evolvedwarfare.hell.ImpEntity> IMP = Registry.register(Registries.ENTITY_TYPE, EvolvedWarfare.id("imp"),
			EntityType.Builder.<com.evolvedwarfare.hell.ImpEntity>create(com.evolvedwarfare.hell.ImpEntity::new, SpawnGroup.MONSTER)
					.dimensions(0.5F, 1.2F).eyeHeight(1.05F).makeFireImmune().maxTrackingRange(8).build("imp"));
	public static final EntityType<com.evolvedwarfare.hell.LostSoulEntity> LOST_SOUL = Registry.register(Registries.ENTITY_TYPE,
			EvolvedWarfare.id("lost_soul"),
			EntityType.Builder.<com.evolvedwarfare.hell.LostSoulEntity>create(com.evolvedwarfare.hell.LostSoulEntity::new, SpawnGroup.MONSTER)
					.dimensions(0.7F, 1.9F).eyeHeight(1.6F).makeFireImmune().maxTrackingRange(8).build("lost_soul"));

	public static final EntityType<com.evolvedwarfare.hell.LegionEntity> LEGION = Registry.register(Registries.ENTITY_TYPE, EvolvedWarfare.id("legion"),
			EntityType.Builder.<com.evolvedwarfare.hell.LegionEntity>create(com.evolvedwarfare.hell.LegionEntity::new, SpawnGroup.MONSTER)
					.dimensions(1.3F, 2.4F).eyeHeight(2.0F).makeFireImmune().maxTrackingRange(10).build("legion"));
	public static final EntityType<com.evolvedwarfare.hell.BeelzebubEntity> BEELZEBUB = Registry.register(Registries.ENTITY_TYPE, EvolvedWarfare.id("beelzebub"),
			EntityType.Builder.<com.evolvedwarfare.hell.BeelzebubEntity>create(com.evolvedwarfare.hell.BeelzebubEntity::new, SpawnGroup.MONSTER)
					.dimensions(1.4F, 2.7F).eyeHeight(2.3F).makeFireImmune().maxTrackingRange(10).build("beelzebub"));
	public static final EntityType<com.evolvedwarfare.hell.MolochEntity> MOLOCH = Registry.register(Registries.ENTITY_TYPE, EvolvedWarfare.id("moloch"),
			EntityType.Builder.<com.evolvedwarfare.hell.MolochEntity>create(com.evolvedwarfare.hell.MolochEntity::new, SpawnGroup.MONSTER)
					.dimensions(1.8F, 3.9F).eyeHeight(3.4F).makeFireImmune().maxTrackingRange(12).build("moloch"));
	public static final EntityType<com.evolvedwarfare.hell.AbaddonEntity> ABADDON = Registry.register(Registries.ENTITY_TYPE, EvolvedWarfare.id("abaddon"),
			EntityType.Builder.<com.evolvedwarfare.hell.AbaddonEntity>create(com.evolvedwarfare.hell.AbaddonEntity::new, SpawnGroup.MONSTER)
					.dimensions(2.0F, 4.6F).eyeHeight(4.1F).makeFireImmune().maxTrackingRange(12).build("abaddon"));

	public static final EntityType<com.evolvedwarfare.wood.FallingTreeEntity> FALLING_TREE = Registry.register(Registries.ENTITY_TYPE,
			EvolvedWarfare.id("falling_tree"),
			EntityType.Builder.<com.evolvedwarfare.wood.FallingTreeEntity>create(com.evolvedwarfare.wood.FallingTreeEntity::new, SpawnGroup.MISC)
					.dimensions(0.5F, 0.5F)
					.maxTrackingRange(10)
					.trackingTickInterval(20)
					.build("falling_tree"));

	public static final EntityType<com.evolvedwarfare.combat.SeveredLimbEntity> SEVERED_LIMB = Registry.register(Registries.ENTITY_TYPE,
			EvolvedWarfare.id("severed_limb"),
			EntityType.Builder.<com.evolvedwarfare.combat.SeveredLimbEntity>create(com.evolvedwarfare.combat.SeveredLimbEntity::new, SpawnGroup.MISC)
					.dimensions(0.35F, 0.35F)
					.maxTrackingRange(8)
					.build("severed_limb"));

	private ModEntities() {
	}

	public static void init() {
		FabricDefaultAttributeRegistry.register(LEGION, com.evolvedwarfare.hell.LegionEntity.createAttributes());
		FabricDefaultAttributeRegistry.register(BEELZEBUB, com.evolvedwarfare.hell.BeelzebubEntity.createAttributes());
		FabricDefaultAttributeRegistry.register(MOLOCH, com.evolvedwarfare.hell.MolochEntity.createAttributes());
		FabricDefaultAttributeRegistry.register(ABADDON, com.evolvedwarfare.hell.AbaddonEntity.createAttributes());
		FabricDefaultAttributeRegistry.register(DEMON, com.evolvedwarfare.hell.DemonEntity.createDemonAttributes());
		FabricDefaultAttributeRegistry.register(IMP, com.evolvedwarfare.hell.ImpEntity.createImpAttributes());
		FabricDefaultAttributeRegistry.register(LOST_SOUL, com.evolvedwarfare.hell.LostSoulEntity.createSoulAttributes());
		FabricDefaultAttributeRegistry.register(RECRUIT, AbstractNpcEntity.createNpcAttributes());
		FabricDefaultAttributeRegistry.register(SOLDIER, AbstractNpcEntity.createNpcAttributes());
		FabricDefaultAttributeRegistry.register(CITY_GUARD, AbstractNpcEntity.createNpcAttributes());
		FabricDefaultAttributeRegistry.register(WORKER, WorkerEntity.createWorkerAttributes());
		FabricDefaultAttributeRegistry.register(BANDIT, BanditEntity.createBanditAttributes());
		FabricDefaultAttributeRegistry.register(VIKING, FactionNpcEntity.createFactionAttributes());
		FabricDefaultAttributeRegistry.register(PIRATE, FactionNpcEntity.createFactionAttributes());
		FabricDefaultAttributeRegistry.register(TRADER, TraderEntity.createTraderAttributes());
		// los animales salvajes
		com.evolvedwarfare.wildlife.Wildlife.registerTypes();
	}
}
