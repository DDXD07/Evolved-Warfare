package com.evolvedwarfare.engineering;

import com.evolvedwarfare.EvolvedWarfare;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.component.ComponentType;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.AttributeModifierSlot;
import net.minecraft.component.type.AttributeModifiersComponent;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;

/** El filo de la afiladora (fase 79): +1,5 de daño durante unos golpes; luego se pierde. */
public final class Sharpening {
	public static final int HITS = 40;
	public static final double DAMAGE = 1.5;
	private static final Identifier ID = EvolvedWarfare.id("sharpened");
	public static final ComponentType<Integer> SHARPENED = Registry.register(Registries.DATA_COMPONENT_TYPE, ID,
			ComponentType.<Integer>builder().codec(com.mojang.serialization.Codec.INT).packetCodec(net.minecraft.network.codec.PacketCodecs.VAR_INT).build());

	private Sharpening() {
	}

	public static int hitsLeft(ItemStack stack) {
		return stack.getOrDefault(SHARPENED, 0);
	}

	/** La deja afilada (o vuelve a llenar el filo). */
	public static ItemStack apply(ItemStack stack) {
		AttributeModifiersComponent mods = without(stack);
		mods = mods.with(EntityAttributes.GENERIC_ATTACK_DAMAGE, new EntityAttributeModifier(ID, DAMAGE, EntityAttributeModifier.Operation.ADD_VALUE),
				AttributeModifierSlot.MAINHAND);
		stack.set(DataComponentTypes.ATTRIBUTE_MODIFIERS, mods);
		stack.set(SHARPENED, HITS);
		return stack;
	}

	/** Los atributos sin el del filo. */
	private static AttributeModifiersComponent without(ItemStack stack) {
		AttributeModifiersComponent mods = stack.getOrDefault(DataComponentTypes.ATTRIBUTE_MODIFIERS,
				stack.getItem().getComponents().getOrDefault(DataComponentTypes.ATTRIBUTE_MODIFIERS, AttributeModifiersComponent.DEFAULT));
		List<AttributeModifiersComponent.Entry> keep = new ArrayList<>();
		for (AttributeModifiersComponent.Entry e : mods.modifiers()) {
			if (!e.modifier().id().equals(ID)) {
				keep.add(e);
			}
		}
		return new AttributeModifiersComponent(keep, mods.showInTooltip());
	}

	/** Un golpe con ella: gasta el filo. */
	public static void hit(ItemStack stack) {
		int left = hitsLeft(stack);
		if (left <= 0) {
			return;
		}
		if (left > 1) {
			stack.set(SHARPENED, left - 1);
			return;
		}
		stack.remove(SHARPENED);
		stack.set(DataComponentTypes.ATTRIBUTE_MODIFIERS, without(stack));
	}

	public static void register() {
		AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
			if (!world.isClient) {
				hit(player.getMainHandStack());
			}
			return ActionResult.PASS;
		});
	}
}
