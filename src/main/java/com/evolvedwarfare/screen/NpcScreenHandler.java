package com.evolvedwarfare.screen;

import com.evolvedwarfare.entity.AbstractNpcEntity;
import com.evolvedwarfare.entity.NpcOrder;
import com.evolvedwarfare.registry.ModScreens;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ArrayPropertyDelegate;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.ArmorSlot;
import net.minecraft.screen.slot.Slot;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * Menú de un NPC: su equipo (armadura y manos), sus provisiones (la paga) y botones de órdenes.
 *
 * <p>Ranuras: 0-3 armadura, 4 mano principal, 5 segunda mano, 6-7 provisiones, 8-43 inventario del jugador.</p>
 */
public class NpcScreenHandler extends ScreenHandler {
	public static final int BUTTON_FOLLOW = 0;
	public static final int BUTTON_STAY = 1;
	public static final int BUTTON_HOME = 2;
	public static final int BUTTON_SET_HOME = 3;

	private static final int EQUIPMENT_SLOTS = 6;
	private static final int FIRST_PROVISION_SLOT = EQUIPMENT_SLOTS;
	private static final int FIRST_PLAYER_SLOT = EQUIPMENT_SLOTS + AbstractNpcEntity.PROVISION_SLOTS;

	// propiedades sincronizadas
	private static final int P_HEALTH = 0;
	private static final int P_MAX_HEALTH = 1;
	private static final int P_ORDER = 2;
	private static final int P_DAYS = 3;
	private static final int P_UNPAID = 4;
	private static final int P_HAS_HOME = 5;

	// posiciones (compartidas con la pantalla)
	public static final int ARMOR_X = 8;
	public static final int ARMOR_Y = 18;
	public static final int HANDS_X = 80;
	public static final int MAINHAND_Y = 54;
	public static final int OFFHAND_Y = 72;
	public static final int PROVISIONS_X = 101;
	public static final int PROVISIONS_Y = 63;
	public static final int INVENTORY_Y = 142;
	public static final int HOTBAR_Y = 200;

	private static final Identifier[] EMPTY_ARMOR_ICONS = {
			Identifier.ofVanilla("item/empty_armor_slot_helmet"),
			Identifier.ofVanilla("item/empty_armor_slot_chestplate"),
			Identifier.ofVanilla("item/empty_armor_slot_leggings"),
			Identifier.ofVanilla("item/empty_armor_slot_boots")};

	@Nullable
	private final AbstractNpcEntity npc;
	private final PropertyDelegate properties = new ArrayPropertyDelegate(6);

	/** Cliente: recibe el id de la entidad desde el servidor. */
	public NpcScreenHandler(int syncId, PlayerInventory playerInventory, Integer entityId) {
		this(syncId, playerInventory, findNpc(playerInventory.player, entityId), false);
	}

	/** Servidor. */
	public NpcScreenHandler(int syncId, PlayerInventory playerInventory, AbstractNpcEntity npc) {
		this(syncId, playerInventory, npc, true);
	}

	private NpcScreenHandler(int syncId, PlayerInventory playerInventory, @Nullable AbstractNpcEntity npc, boolean server) {
		super(ModScreens.NPC, syncId);
		this.npc = npc;
		// En el servidor las ranuras tocan el NPC de verdad; en el cliente solo muestran lo que llega.
		Inventory equipment = server && npc != null ? new NpcEquipmentInventory(npc) : new SimpleInventory(EQUIPMENT_SLOTS);
		Inventory provisions = server && npc != null ? npc.getProvisions() : new SimpleInventory(AbstractNpcEntity.PROVISION_SLOTS);
		LivingEntity wearer = npc != null ? npc : playerInventory.player;

		for (int i = 0; i < 4; i++) {
			addSlot(new ArmorSlot(equipment, wearer, NpcEquipmentInventory.SLOTS[i], i, ARMOR_X, ARMOR_Y + i * 18, EMPTY_ARMOR_ICONS[i]));
		}
		addSlot(new Slot(equipment, 4, HANDS_X, MAINHAND_Y));
		addSlot(new Slot(equipment, 5, HANDS_X, OFFHAND_Y));
		for (int i = 0; i < AbstractNpcEntity.PROVISION_SLOTS; i++) {
			addSlot(new Slot(provisions, i, PROVISIONS_X + i * 18, PROVISIONS_Y) {
				@Override
				public boolean canInsert(ItemStack stack) {
					return AbstractNpcEntity.isUpkeepItem(stack);
				}
			});
		}
		for (int row = 0; row < 3; row++) {
			for (int col = 0; col < 9; col++) {
				addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, INVENTORY_Y + row * 18));
			}
		}
		for (int col = 0; col < 9; col++) {
			addSlot(new Slot(playerInventory, col, 8 + col * 18, HOTBAR_Y));
		}
		addProperties(properties);
		if (server) {
			updateProperties();
		}
	}

	@Nullable
	private static AbstractNpcEntity findNpc(PlayerEntity player, Integer entityId) {
		Entity entity = entityId == null ? null : player.getWorld().getEntityById(entityId);
		return entity instanceof AbstractNpcEntity npc ? npc : null;
	}

	@Nullable
	public AbstractNpcEntity getNpc() {
		return npc;
	}

	// ------------------------------------------------------------------ servidor

	private void updateProperties() {
		if (npc == null) {
			return;
		}
		properties.set(P_HEALTH, Math.round(npc.getHealth() * 10.0F));
		properties.set(P_MAX_HEALTH, Math.round(npc.getMaxHealth() * 10.0F));
		properties.set(P_ORDER, npc.getOrder().ordinal());
		properties.set(P_DAYS, Math.min(npc.getProvisionDays(), 9999));
		properties.set(P_UNPAID, npc.getUnpaidDays());
		properties.set(P_HAS_HOME, npc.getHome() != null ? 1 : 0);
	}

	@Override
	public void sendContentUpdates() {
		updateProperties();
		super.sendContentUpdates();
	}

	@Override
	public boolean onButtonClick(PlayerEntity player, int id) {
		if (npc == null || !npc.isOwner(player)) {
			return false;
		}
		switch (id) {
			case BUTTON_FOLLOW -> npc.setOrder(NpcOrder.FOLLOW);
			case BUTTON_STAY -> npc.setOrder(NpcOrder.STAY);
			case BUTTON_HOME -> npc.setOrder(NpcOrder.HOME);
			case BUTTON_SET_HOME -> npc.setHomeHere();
			default -> {
				return false;
			}
		}
		updateProperties();
		return true;
	}

	@Override
	public boolean canUse(PlayerEntity player) {
		return npc != null && npc.isAlive() && npc.isOwner(player) && player.squaredDistanceTo(npc) < 64.0;
	}

	@Override
	public ItemStack quickMove(PlayerEntity player, int index) {
		Slot slot = slots.get(index);
		if (!slot.hasStack()) {
			return ItemStack.EMPTY;
		}
		ItemStack stack = slot.getStack();
		ItemStack original = stack.copy();
		int playerEnd = FIRST_PLAYER_SLOT + 36;
		if (index < FIRST_PLAYER_SLOT) {
			// Del NPC al jugador
			if (!insertItem(stack, FIRST_PLAYER_SLOT, playerEnd, true)) {
				return ItemStack.EMPTY;
			}
		} else {
			// Del jugador al NPC: provisiones, armadura en su hueco, o a las manos
			boolean moved = false;
			if (AbstractNpcEntity.isUpkeepItem(stack)) {
				moved = insertItem(stack, FIRST_PROVISION_SLOT, FIRST_PLAYER_SLOT, false);
			}
			if (!moved && npc != null) {
				EquipmentSlot preferred = npc.getPreferredEquipmentSlot(stack);
				for (int i = 0; i < 4 && !moved; i++) {
					if (NpcEquipmentInventory.SLOTS[i] == preferred && !slots.get(i).hasStack()) {
						moved = insertItem(stack, i, i + 1, false);
					}
				}
			}
			if (!moved && !slots.get(4).hasStack()) {
				moved = insertItem(stack, 4, 5, false);
			}
			if (!moved) {
				return ItemStack.EMPTY;
			}
		}
		if (stack.isEmpty()) {
			slot.setStack(ItemStack.EMPTY);
		} else {
			slot.markDirty();
		}
		return stack.getCount() == original.getCount() ? ItemStack.EMPTY : original;
	}

	// ------------------------------------------------------------------ datos para la pantalla

	public float health() {
		return properties.get(P_HEALTH) / 10.0F;
	}

	public float maxHealth() {
		return properties.get(P_MAX_HEALTH) / 10.0F;
	}

	public NpcOrder order() {
		int i = properties.get(P_ORDER);
		NpcOrder[] values = NpcOrder.values();
		return values[Math.max(0, Math.min(i, values.length - 1))];
	}

	public int provisionDays() {
		return properties.get(P_DAYS);
	}

	public int unpaidDays() {
		return properties.get(P_UNPAID);
	}

	public boolean hasHome() {
		return properties.get(P_HAS_HOME) != 0;
	}
}
