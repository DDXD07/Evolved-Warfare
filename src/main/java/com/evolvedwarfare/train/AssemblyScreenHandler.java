package com.evolvedwarfare.train;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerContext;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;

/** La pantalla de la mesa de ensamblaje: qué se puede montar y con qué; el botón (id = cuál) lo monta con lo del inventario. */
public class AssemblyScreenHandler extends ScreenHandler {
	private final ScreenHandlerContext context;

	public AssemblyScreenHandler(int syncId, PlayerInventory inv) {
		this(syncId, inv, ScreenHandlerContext.EMPTY);
	}

	public AssemblyScreenHandler(int syncId, PlayerInventory inv, ScreenHandlerContext context) {
		super(Trains.ASSEMBLY_SCREEN, syncId);
		this.context = context;
	}

	@Override
	public boolean onButtonClick(PlayerEntity player, int id) {
		if (id < 0 || id >= Trains.assemblies().size()) {
			return false;
		}
		Trains.Assembly a = Trains.assemblies().get(id);
		if (!player.getAbilities().creativeMode && !Building.pay(player.getInventory(), a.needs())) {
			return false;
		}
		ItemStack out = new ItemStack(a.result());
		if (!player.giveItemStack(out)) {
			player.dropItem(out, false);
		}
		context.run((world, pos) -> {
			world.playSound(null, pos, SoundEvents.BLOCK_ANVIL_USE, SoundCategory.BLOCKS, 0.8F, 0.9F);
			world.playSound(null, pos, SoundEvents.BLOCK_SMITHING_TABLE_USE, SoundCategory.BLOCKS, 1.0F, 0.8F);
		});
		return true;
	}

	@Override
	public ItemStack quickMove(PlayerEntity player, int slot) {
		return ItemStack.EMPTY;
	}

	@Override
	public boolean canUse(PlayerEntity player) {
		return canUse(context, player, Trains.ASSEMBLY_TABLE);
	}
}
