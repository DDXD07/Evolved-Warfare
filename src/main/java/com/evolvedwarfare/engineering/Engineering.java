package com.evolvedwarfare.engineering;

import com.evolvedwarfare.EvolvedWarfare;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.MapColor;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.text.Text;

/**
 * La ingeniería medieval (fase 72): la red de fuerza mecánica con velocidad y par. Fase 1: el eje, los engranajes, la caja
 * de engranajes, la manivela, las ruedas hidráulicas y los molinos de viento (tres tamaños), y los medidores. Fase 2 (73):
 * las máquinas que gastan esa fuerza: la muela, el aserradero, el martinete y el fuelle.
 */
public final class Engineering {
	public static final List<Block> BLOCKS = new ArrayList<>();
	public static final List<Item> ITEMS = new ArrayList<>();

	private static AbstractBlock.Settings wood() {
		return AbstractBlock.Settings.create().mapColor(MapColor.OAK_TAN).strength(1.5F).sounds(BlockSoundGroup.WOOD).nonOpaque();
	}

	public static final Block SHAFT = block("shaft", new ShaftBlock(wood(), 0));
	public static final Block SMALL_GEAR = block("small_gear", new ShaftBlock(wood(), 1));
	public static final Block LARGE_GEAR = block("large_gear", new ShaftBlock(wood(), 2));
	public static final Block GEARBOX = block("gearbox", new GearboxBlock(AbstractBlock.Settings.create().mapColor(MapColor.OAK_TAN).strength(2.0F)
			.sounds(BlockSoundGroup.WOOD)));
	public static final Block HAND_CRANK = block("hand_crank", new CrankBlock(wood()));
	public static final Block WATER_WHEEL_SMALL = block("water_wheel_small", new WaterWheelBlock(wood().strength(2.0F), 3));
	public static final Block WATER_WHEEL_MEDIUM = block("water_wheel_medium", new WaterWheelBlock(wood().strength(2.5F), 5));
	public static final Block WATER_WHEEL_LARGE = block("water_wheel_large", new WaterWheelBlock(wood().strength(3.0F), 7));
	public static final Block WINDMILL_SMALL = block("windmill_small", new WindmillBlock(wood().strength(2.0F), 5));
	public static final Block WINDMILL_MEDIUM = block("windmill_medium", new WindmillBlock(wood().strength(2.5F), 9));
	public static final Block WINDMILL_LARGE = block("windmill_large", new WindmillBlock(wood().strength(3.0F), 13));
	public static final Block SPEED_GAUGE = block("speed_gauge", new GaugeBlock(AbstractBlock.Settings.create().mapColor(MapColor.OAK_TAN)
			.strength(1.5F).sounds(BlockSoundGroup.WOOD), false));
	public static final Block STRESS_GAUGE = block("stress_gauge", new GaugeBlock(AbstractBlock.Settings.create().mapColor(MapColor.OAK_TAN)
			.strength(1.5F).sounds(BlockSoundGroup.WOOD), true));

	public static final BlockEntityType<KineticBlockEntity> KINETIC_ENTITY = Registry.register(Registries.BLOCK_ENTITY_TYPE,
			EvolvedWarfare.id("kinetic"), BlockEntityType.Builder.create(KineticBlockEntity::new, BLOCKS.toArray(new Block[0])).build(null));

	// fase 73: las máquinas
	private static final List<Block> MACHINES = new ArrayList<>();
	public static final Block MILLSTONE = machine("millstone", new MillstoneBlock(AbstractBlock.Settings.create().mapColor(MapColor.STONE_GRAY)
			.strength(2.5F).sounds(BlockSoundGroup.STONE).nonOpaque()));
	public static final Block SAWMILL = machine("sawmill", new SawmillBlock(wood().strength(2.5F)));
	public static final Block TRIP_HAMMER = machine("trip_hammer", new TripHammerBlock(wood().strength(3.0F)));
	public static final Block BELLOWS = machine("bellows", new BellowsBlock(wood()));
	public static final Item FLOUR = item("flour", new DescribedItem(new Item.Settings()));
	public static final BlockEntityType<MachineBlockEntity> MACHINE_ENTITY = Registry.register(Registries.BLOCK_ENTITY_TYPE,
			EvolvedWarfare.id("machine"), BlockEntityType.Builder.create(MachineBlockEntity::new, MACHINES.toArray(new Block[0])).build(null));

	public static final ItemGroup GROUP = Registry.register(Registries.ITEM_GROUP, EvolvedWarfare.id("engineering"), FabricItemGroup.builder()
			.icon(() -> new ItemStack(LARGE_GEAR)).displayName(Text.translatable("itemGroup.evolvedwarfare.engineering"))
			.entries((ctx, entries) -> ITEMS.forEach(entries::add)).build());

	private Engineering() {
	}

	private static Block block(String name, Block block) {
		Registry.register(Registries.BLOCK, EvolvedWarfare.id(name), block);
		BLOCKS.add(block);
		ITEMS.add(Registry.register(Registries.ITEM, EvolvedWarfare.id(name), new BlockItem(block, new Item.Settings())));
		return block;
	}

	private static Block machine(String name, Block block) {
		Registry.register(Registries.BLOCK, EvolvedWarfare.id(name), block);
		MACHINES.add(block);
		ITEMS.add(Registry.register(Registries.ITEM, EvolvedWarfare.id(name), new BlockItem(block, new Item.Settings())));
		return block;
	}

	private static Item item(String name, Item item) {
		ITEMS.add(Registry.register(Registries.ITEM, EvolvedWarfare.id(name), item));
		return item;
	}

	/** Un objeto con su descripción debajo del nombre. */
	static class DescribedItem extends Item {
		DescribedItem(Settings settings) {
			super(settings);
		}

		@Override
		public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, net.minecraft.item.tooltip.TooltipType type) {
			tooltip.add(Text.translatable(getTranslationKey() + ".desc").formatted(net.minecraft.util.Formatting.GRAY));
		}
	}

	public static void register() {
		ServerTickEvents.END_WORLD_TICK.register(KineticNetwork::flush);
	}
}
