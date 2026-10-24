package com.evolvedwarfare.blueprint;

import com.evolvedwarfare.building.Blueprint;
import net.minecraft.block.AbstractBannerBlock;
import net.minecraft.block.AbstractCandleBlock;
import net.minecraft.block.AbstractPressurePlateBlock;
import net.minecraft.block.AbstractRailBlock;
import net.minecraft.block.AbstractSignBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ButtonBlock;
import net.minecraft.block.CarpetBlock;
import net.minecraft.block.CocoaBlock;
import net.minecraft.block.FlowerPotBlock;
import net.minecraft.block.LadderBlock;
import net.minecraft.block.LanternBlock;
import net.minecraft.block.LeverBlock;
import net.minecraft.block.PlantBlock;
import net.minecraft.block.RedstoneWireBlock;
import net.minecraft.block.TorchBlock;
import net.minecraft.block.TripwireHookBlock;
import net.minecraft.block.VineBlock;
import net.minecraft.block.enums.BedPart;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.state.property.Properties;

/** Cómo se pone cada bloque de un plano escaneado. */
public final class BlueprintFlags {
	private BlueprintFlags() {
	}

	/** Bloques que necesitan algo donde apoyarse: van al final de la obra. */
	public static boolean needsSupport(BlockState state) {
		Block block = state.getBlock();
		return block instanceof TorchBlock || block instanceof LanternBlock || block instanceof LadderBlock
				|| block instanceof AbstractRailBlock || block instanceof ButtonBlock || block instanceof AbstractPressurePlateBlock
				|| block instanceof CarpetBlock || block instanceof AbstractSignBlock || block instanceof AbstractBannerBlock
				|| block instanceof PlantBlock || block instanceof RedstoneWireBlock || block instanceof LeverBlock
				|| block instanceof TripwireHookBlock || block instanceof VineBlock || block instanceof AbstractCandleBlock
				|| block instanceof FlowerPotBlock || block instanceof CocoaBlock;
	}

	/** La otra mitad de un bloque doble (la de arriba de una puerta o planta alta, la cabecera de una cama). */
	public static boolean isOtherHalf(BlockState state) {
		if (state.contains(Properties.DOUBLE_BLOCK_HALF) && state.get(Properties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER) {
			return true;
		}
		return state.contains(Properties.BED_PART) && state.get(Properties.BED_PART) == BedPart.HEAD;
	}

	public static int flagsFor(BlockState state) {
		int flags = 0;
		if (needsSupport(state)) {
			flags |= Blueprint.LATE;
		}
		if (isOtherHalf(state)) {
			flags |= Blueprint.UPPER_HALF;
		}
		return flags;
	}
}
