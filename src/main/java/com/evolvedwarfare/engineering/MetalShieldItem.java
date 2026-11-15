package com.evolvedwarfare.engineering;

import com.evolvedwarfare.EvolvedWarfare;
import java.util.List;
import net.minecraft.component.type.AttributeModifierSlot;
import net.minecraft.component.type.AttributeModifiersComponent;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ShieldItem;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;

/**
 * Los escudos reforzados (fase 78): el escudo reforzado (cantos y bandas de hierro: aguanta el doble) y el pavés (de
 * cuerpo entero: aguanta el triple, en alto no te empujan, pero pesa). A ninguno de los dos lo bajan las hachas.
 */
public class MetalShieldItem extends ShieldItem {
	public final boolean pavise;

	public MetalShieldItem(boolean pavise) {
		super(settings(pavise));
		this.pavise = pavise;
	}

	private static Item.Settings settings(boolean pavise) {
		Item.Settings s = new Item.Settings().maxDamage(pavise ? 1008 : 672);
		if (pavise) {
			s.attributeModifiers(AttributeModifiersComponent.builder()
					.add(EntityAttributes.GENERIC_MOVEMENT_SPEED, new EntityAttributeModifier(EvolvedWarfare.id("pavise_weight"), -0.1,
							EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL), AttributeModifierSlot.HAND)
					.build());
		}
		return s;
	}

	@Override
	public boolean canRepair(ItemStack stack, ItemStack ingredient) {
		return ingredient.isOf(pavise ? Metalwork.STEEL_PLATE : Metalwork.IRON_PLATE);
	}

	@Override
	public String getTranslationKey(ItemStack stack) {
		return getTranslationKey();
	}

	@Override
	public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip, TooltipType type) {
		tooltip.add(Text.translatable(getTranslationKey() + ".desc").formatted(Formatting.GRAY));
	}

	/** ¿Lleva en alto uno de estos escudos? */
	public static boolean blockingWith(LivingEntity entity, boolean paviseOnly) {
		return entity.isBlocking() && entity.getActiveItem().getItem() instanceof MetalShieldItem s && (!paviseOnly || s.pavise);
	}

	/** El desgaste al parar un golpe (como el del escudo de vanilla, que solo lo hace con el suyo). */
	public static void wear(PlayerEntity player, float amount) {
		ItemStack active = player.getActiveItem();
		if (!(active.getItem() instanceof MetalShieldItem) || amount < 3.0F || player.getWorld().isClient) {
			return;
		}
		int dmg = 1 + MathHelper.floor(amount);
		Hand hand = player.getActiveHand();
		active.damage(dmg, player, LivingEntity.getSlotForHand(hand));
		if (active.isEmpty()) {
			player.setStackInHand(hand, ItemStack.EMPTY);
			player.clearActiveItem();
			player.playSound(SoundEvents.ITEM_SHIELD_BREAK, 0.8F, 0.8F + player.getWorld().random.nextFloat() * 0.4F);
		}
	}
}
