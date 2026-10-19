package com.evolvedwarfare.screen;

import com.mojang.datafixers.util.Pair;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.util.Identifier;

/**
 * Ranura de armadura del NPC: una sola pieza y solo del tipo que toca (casco, pechera, grebas o botas).
 * (La ArmorSlot de Minecraft no es pública en 1.21.1, por eso tenemos la nuestra.)
 */
public class NpcArmorSlot extends Slot {
	private final LivingEntity wearer;
	private final EquipmentSlot equipmentSlot;
	private final Identifier emptyIcon;

	public NpcArmorSlot(Inventory inventory, LivingEntity wearer, EquipmentSlot equipmentSlot, int index, int x, int y,
			Identifier emptyIcon) {
		super(inventory, index, x, y);
		this.wearer = wearer;
		this.equipmentSlot = equipmentSlot;
		this.emptyIcon = emptyIcon;
	}

	@Override
	public int getMaxItemCount() {
		return 1;
	}

	@Override
	public boolean canInsert(ItemStack stack) {
		return wearer.getPreferredEquipmentSlot(stack) == equipmentSlot;
	}

	/** Silueta gris de la pieza cuando la ranura está vacía. */
	@Override
	public Pair<Identifier, Identifier> getBackgroundSprite() {
		return Pair.of(PlayerScreenHandler.BLOCK_ATLAS_TEXTURE, emptyIcon);
	}
}
