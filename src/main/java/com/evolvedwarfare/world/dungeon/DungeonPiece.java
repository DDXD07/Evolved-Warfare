package com.evolvedwarfare.world.dungeon;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.structure.SimpleStructurePiece;
import net.minecraft.structure.StructureContext;
import net.minecraft.structure.StructurePlacementData;
import net.minecraft.structure.StructureTemplateManager;
import net.minecraft.structure.processor.BlockIgnoreStructureProcessor;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.ServerWorldAccess;

/** Una mazmorra: su plantilla, girada alrededor de la entrada (el pivote) y colocada chunk a chunk. */
public class DungeonPiece extends SimpleStructurePiece {
	public DungeonPiece(StructureTemplateManager manager, Identifier id, BlockPos pos, BlockRotation rotation, BlockPos pivot) {
		super(Dungeons.PIECE, 0, manager, id, id.toString(), placement(rotation, pivot), pos);
	}

	public DungeonPiece(StructureTemplateManager manager, NbtCompound nbt) {
		super(Dungeons.PIECE, nbt, manager, id -> placement(rotation(nbt.getString("Rot")), new BlockPos(nbt.getInt("PX"), nbt.getInt("PY"),
				nbt.getInt("PZ"))));
	}

	private static BlockRotation rotation(String name) {
		try {
			return BlockRotation.valueOf(name);
		} catch (IllegalArgumentException e) {
			return BlockRotation.NONE;
		}
	}

	public static StructurePlacementData placement(BlockRotation rotation, BlockPos pivot) {
		return new StructurePlacementData().setRotation(rotation).setMirror(BlockMirror.NONE).setPosition(pivot)
				.addProcessor(BlockIgnoreStructureProcessor.IGNORE_STRUCTURE_BLOCKS);
	}

	@Override
	protected void writeNbt(StructureContext context, NbtCompound nbt) {
		super.writeNbt(context, nbt);
		nbt.putString("Rot", placementData.getRotation().name());
		BlockPos p = placementData.getPosition();
		nbt.putInt("PX", p.getX());
		nbt.putInt("PY", p.getY());
		nbt.putInt("PZ", p.getZ());
	}

	@Override
	protected void handleMetadata(String metadata, BlockPos pos, ServerWorldAccess world, Random random, BlockBox boundingBox) {
	}
}
