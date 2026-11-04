package com.evolvedwarfare.climate;

import com.mojang.serialization.MapCodec;
import java.util.function.Supplier;
import net.minecraft.block.CropBlock;
import net.minecraft.item.ItemConvertible;

/** Un cultivo del mod (tomate, maíz, col, cebolla, ajo): como el trigo, ocho edades, y se planta con su semilla. */
public class SeasonalCropBlock extends CropBlock {
	private final Supplier<ItemConvertible> seeds;
	private final MapCodec<SeasonalCropBlock> codec;

	public SeasonalCropBlock(Settings settings, Supplier<ItemConvertible> seeds) {
		super(settings);
		this.seeds = seeds;
		this.codec = createCodec(s -> new SeasonalCropBlock(s, seeds));
	}

	@Override
	public MapCodec<? extends CropBlock> getCodec() {
		return codec;
	}

	@Override
	protected ItemConvertible getSeedsItem() {
		return seeds.get();
	}
}
