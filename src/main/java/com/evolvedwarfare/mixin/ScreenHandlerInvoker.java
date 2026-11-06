package com.evolvedwarfare.mixin;

import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ScreenHandler.class)
public interface ScreenHandlerInvoker {
	@Invoker("addSlot")
	Slot evolvedwarfare$addSlot(Slot slot);

	@Invoker("insertItem")
	boolean evolvedwarfare$insertItem(ItemStack stack, int startIndex, int endIndex, boolean fromLast);
}
