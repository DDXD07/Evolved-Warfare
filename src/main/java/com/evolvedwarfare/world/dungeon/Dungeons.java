package com.evolvedwarfare.world.dungeon;

import com.evolvedwarfare.EvolvedWarfare;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.MapColor;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.block.piston.PistonBehavior;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.structure.StructurePieceType;
import net.minecraft.world.gen.structure.StructureType;

/**
 * Las mazmorras (fase 66): estructuras grandes hechas de plantillas (las diseña tools/ew_dungeons.py), con generadores,
 * trampas, salas secretas y, al fondo, el Corazón de la mazmorra: cuando llega alguien despierta al mini jefe (un monstruo
 * vanilla potenciado, ver {@link Bosses}), cierra las rejas de la arena y, cuando el jefe cae, se convierte en el cofre
 * del botín.
 */
public final class Dungeons {
	public static final String BOSS_TAG = "ew_dungeon_boss";
	public static final String MINION_TAG = "ew_dungeon_minion";
	public static final String HEART_TAG = "ew_heart:";
	public static final List<String> IDS = List.of("forgotten_mausoleum", "mad_mage_spire", "pharaoh_tomb", "dwarven_forge", "spider_nest",
			"frozen_keep", "serpent_temple", "forgotten_prison", "forgotten_sewers", "soul_shrine");
	public static final List<Item> CREATIVE = new ArrayList<>();

	private static AbstractBlock.Settings sealed() {
		return AbstractBlock.Settings.create().strength(-1.0F, 3600000.0F).dropsNothing().pistonBehavior(PistonBehavior.BLOCK)
				.allowsSpawning((st, w, p, t) -> false);
	}

	public static final Block HEART = block("dungeon_heart", new DungeonHeartBlock(sealed().mapColor(MapColor.BLACK).luminance(s -> 9)
			.sounds(BlockSoundGroup.POLISHED_DEEPSLATE)));
	public static final Block GATE = block("dungeon_gate", new DungeonGateBlock(sealed().mapColor(MapColor.IRON_GRAY).nonOpaque()
			.sounds(BlockSoundGroup.CHAIN).suffocates((st, w, p) -> false).blockVision((st, w, p) -> false).solidBlock((st, w, p) -> false)));

	public static final BlockEntityType<DungeonHeartBlockEntity> HEART_ENTITY = Registry.register(Registries.BLOCK_ENTITY_TYPE,
			EvolvedWarfare.id("dungeon_heart"), BlockEntityType.Builder.create(DungeonHeartBlockEntity::new, HEART).build(null));

	public static final StructurePieceType PIECE = Registry.register(Registries.STRUCTURE_PIECE, EvolvedWarfare.id("dungeon"),
			(StructurePieceType.ManagerAware) DungeonPiece::new);
	public static final StructureType<DungeonStructure> STRUCTURE = Registry.register(Registries.STRUCTURE_TYPE, EvolvedWarfare.id("dungeon"),
			() -> DungeonStructure.CODEC);

	private Dungeons() {
	}

	private static Block block(String name, Block block) {
		Registry.register(Registries.BLOCK, EvolvedWarfare.id(name), block);
		CREATIVE.add(Registry.register(Registries.ITEM, EvolvedWarfare.id(name), new BlockItem(block, new Item.Settings())));
		return block;
	}

	public static void register() {
		// cuando cae un jefe, su Corazón se abre (lo lleva apuntado en una etiqueta)
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity.getWorld() instanceof ServerWorld world && entity.getCommandTags().contains(BOSS_TAG)) {
				DungeonHeartBlockEntity.bossDied(world, entity);
			}
		});
	}
}
