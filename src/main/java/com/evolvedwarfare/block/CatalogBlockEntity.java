package com.evolvedwarfare.block;

import com.evolvedwarfare.registry.ModBlocks;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.util.math.BlockPos;

/** Solo para dibujar el libro en 3D (y abrirlo con su animación); no guarda nada. */
public class CatalogBlockEntity extends BlockEntity {
	public CatalogBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlocks.TRADER_CATALOG_ENTITY, pos, state);
	}
}
