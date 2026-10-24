package com.evolvedwarfare.world;

import com.evolvedwarfare.EvolvedWarfare;
import java.util.Optional;
import net.fabricmc.fabric.api.object.builder.v1.trade.TradeOfferHelper;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.Entity;
import net.minecraft.item.FilledMapItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.map.MapDecorationType;
import net.minecraft.item.map.MapDecorationTypes;
import net.minecraft.item.map.MapState;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.TradeOffers;
import net.minecraft.village.TradedItem;
import net.minecraft.village.VillagerProfession;
import net.minecraft.world.gen.structure.Structure;
import org.jetbrains.annotations.Nullable;

/**
 * Mapas para encontrar los asentamientos de las facciones: los venden los cartógrafos (como los mapas de exploración
 * vanilla) y salen en algunos cofres (ver {@code tools/ew_loot.py}).
 */
public final class ExplorationMaps {
	public static final TagKey<Structure> ON_VIKING_MAPS = TagKey.of(RegistryKeys.STRUCTURE, EvolvedWarfare.id("on_viking_maps"));
	public static final TagKey<Structure> ON_PIRATE_MAPS = TagKey.of(RegistryKeys.STRUCTURE, EvolvedWarfare.id("on_pirate_maps"));

	private ExplorationMaps() {
	}

	public static void register() {
		// aprendiz: mapa del tesoro pirata; oficial: mapa del asentamiento vikingo
		TradeOfferHelper.registerVillagerOffers(VillagerProfession.CARTOGRAPHER, 2, factories -> factories.add(
				new SellMap(ON_PIRATE_MAPS, "filled_map.evolvedwarfare.pirate_cove", MapDecorationTypes.RED_X, 11)));
		TradeOfferHelper.registerVillagerOffers(VillagerProfession.CARTOGRAPHER, 3, factories -> factories.add(
				new SellMap(ON_VIKING_MAPS, "filled_map.evolvedwarfare.viking_settlement", MapDecorationTypes.VILLAGE_SNOWY, 13)));
	}

	/** Esmeraldas + brújula → mapa hasta la estructura más cercana de ese tipo (si hay una a menos de 100 regiones). */
	private record SellMap(TagKey<Structure> structure, String nameKey, RegistryEntry<MapDecorationType> decoration, int price)
			implements TradeOffers.Factory {
		@Override
		@Nullable
		public TradeOffer create(Entity entity, Random random) {
			if (!(entity.getWorld() instanceof ServerWorld world)) {
				return null;
			}
			BlockPos pos = world.locateStructure(structure, entity.getBlockPos(), 100, true);
			if (pos == null) {
				return null;
			}
			ItemStack map = FilledMapItem.createMap(world, pos.getX(), pos.getZ(), (byte) 2, true, true);
			FilledMapItem.fillExplorationMap(world, map);
			MapState.addDecorationsNbt(map, pos, "+", decoration);
			map.set(DataComponentTypes.ITEM_NAME, Text.translatable(nameKey));
			return new TradeOffer(new TradedItem(Items.EMERALD, price), Optional.of(new TradedItem(Items.COMPASS)), map, 12, 10, 0.2F);
		}
	}
}
