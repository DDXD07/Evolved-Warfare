package com.evolvedwarfare.registry;

import com.evolvedwarfare.EvolvedWarfare;
import com.evolvedwarfare.block.TownHallBlock;
import com.evolvedwarfare.block.TownHallBlockEntity;
import com.evolvedwarfare.block.WorkSiteBlock;
import com.evolvedwarfare.block.WorkSiteBlockEntity;
import com.evolvedwarfare.work.Profession;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.MapColor;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.block.enums.NoteBlockInstrument;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.BlockSoundGroup;

public final class ModBlocks {
	/** Un puesto de trabajo por oficio: "farmer_post", "miner_post"... */
	public static final Map<Profession, WorkSiteBlock> WORK_SITES = new EnumMap<>(Profession.class);

	static {
		for (Profession profession : Profession.values()) {
			WORK_SITES.put(profession, register(profession.id() + "_post", new WorkSiteBlock(profession, settingsFor(profession))));
		}
	}

	/** Ayuntamiento: paga a tus NPCs cercanos con lo que guardes dentro. */
	public static final Block TOWN_HALL = register("town_hall", new TownHallBlock(AbstractBlock.Settings.create()
			.mapColor(MapColor.BROWN)
			.instrument(NoteBlockInstrument.BASS)
			.strength(2.5F)
			.sounds(BlockSoundGroup.WOOD)));

	public static final BlockEntityType<WorkSiteBlockEntity> WORK_SITE_ENTITY = Registry.register(Registries.BLOCK_ENTITY_TYPE,
			EvolvedWarfare.id("work_site"),
			BlockEntityType.Builder.create(WorkSiteBlockEntity::new, WORK_SITES.values().toArray(new Block[0])).build(null));

	public static final BlockEntityType<TownHallBlockEntity> TOWN_HALL_ENTITY = Registry.register(Registries.BLOCK_ENTITY_TYPE,
			EvolvedWarfare.id("town_hall"),
			BlockEntityType.Builder.create(TownHallBlockEntity::new, TOWN_HALL).build(null));

	private ModBlocks() {
	}

	/** Los talleres (sin zona) son de piedra; los puestos de campo, de madera. */
	public static boolean isStoneWorkshop(Profession profession) {
		return !profession.area().exists();
	}

	private static AbstractBlock.Settings settingsFor(Profession profession) {
		if (isStoneWorkshop(profession)) {
			return AbstractBlock.Settings.create()
					.mapColor(MapColor.STONE_GRAY)
					.instrument(NoteBlockInstrument.BASEDRUM)
					.strength(2.5F, 6.0F)
					.sounds(BlockSoundGroup.STONE);
		}
		return AbstractBlock.Settings.create()
				.mapColor(MapColor.OAK_TAN)
				.instrument(NoteBlockInstrument.BASS)
				.strength(2.5F)
				.sounds(BlockSoundGroup.WOOD);
	}

	private static <B extends Block> B register(String name, B block) {
		Registry.register(Registries.BLOCK, EvolvedWarfare.id(name), block);
		Registry.register(Registries.ITEM, EvolvedWarfare.id(name), new BlockItem(block, new Item.Settings()));
		return block;
	}

	public static void init() {
		// Fuerza la carga de la clase (y por tanto el registro).
	}
}
