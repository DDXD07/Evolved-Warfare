package com.evolvedwarfare.health;

import com.evolvedwarfare.EvolvedWarfare;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.Rarity;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.UseAction;
import net.minecraft.world.World;

/** Lo de curarse: vendas, entablillados y cristales de corazón (fase 92); aguardiente, medicina, antídoto y botiquín (fase 93). */
public final class HealthItems {
	public static final List<Item> ITEMS = new ArrayList<>();

	/** Algo que se usa un rato (como comer) y al terminar cura. */
	public static class MedicalItem extends Item {
		private final int ticks;
		private final java.util.function.Predicate<PlayerEntity> effect;
		private final SoundEvent sound;
		/** Si se bebe (y deja el frasco). */
		private boolean drink;

		public MedicalItem(Settings settings, int ticks, SoundEvent sound, java.util.function.Predicate<PlayerEntity> effect) {
			super(settings);
			this.ticks = ticks;
			this.effect = effect;
			this.sound = sound;
		}

		public MedicalItem drink() {
			drink = true;
			return this;
		}

		@Override
		public int getMaxUseTime(ItemStack stack, LivingEntity user) {
			return ticks;
		}

		@Override
		public UseAction getUseAction(ItemStack stack) {
			return drink ? UseAction.DRINK : UseAction.NONE;
		}

		@Override
		public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
			user.setCurrentHand(hand);
			return TypedActionResult.consume(user.getStackInHand(hand));
		}

		@Override
		public void usageTick(World world, LivingEntity user, ItemStack stack, int remainingUseTicks) {
			if (!drink && !world.isClient && remainingUseTicks % 10 == 0) {
				world.playSound(null, user.getX(), user.getY(), user.getZ(), SoundEvents.ITEM_ARMOR_EQUIP_LEATHER.value(), SoundCategory.PLAYERS, 0.5F,
						1.3F);
			}
		}

		@Override
		public ItemStack finishUsing(ItemStack stack, World world, LivingEntity user) {
			if (!world.isClient && user instanceof PlayerEntity player && effect.test(player)) {
				world.playSound(null, user.getX(), user.getY(), user.getZ(), sound, SoundCategory.PLAYERS, 0.8F, 1.0F);
				if (!player.getAbilities().creativeMode) {
					stack.decrement(1);
					if (drink) {
						if (stack.isEmpty()) {
							return new ItemStack(Items.GLASS_BOTTLE);
						}
						if (!player.getInventory().insertStack(new ItemStack(Items.GLASS_BOTTLE))) {
							player.dropItem(new ItemStack(Items.GLASS_BOTTLE), false);
						}
					}
				}
			}
			return stack;
		}

		@Override
		public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
			tooltip.add(Text.translatable(getTranslationKey() + ".desc").formatted(Formatting.GRAY));
		}
	}

	public static final Item BANDAGE = item("bandage", new MedicalItem(new Item.Settings().maxCount(16), 30, SoundEvents.ITEM_ARMOR_EQUIP_LEATHER.value(),
			Health::bandage));
	public static final Item SPLINT = item("splint", new MedicalItem(new Item.Settings().maxCount(8), 50, SoundEvents.BLOCK_WOOD_PLACE, Health::splint));
	public static final Item HEART_CRYSTAL = item("heart_crystal", new Item(new Item.Settings().maxCount(16).rarity(Rarity.EPIC)) {
		@Override
		public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
			ItemStack stack = user.getStackInHand(hand);
			if (world.isClient) {
				return TypedActionResult.success(stack);
			}
			if (Health.addHeart(user)) {
				world.playSound(null, user.getX(), user.getY(), user.getZ(), SoundEvents.BLOCK_AMETHYST_BLOCK_RESONATE, SoundCategory.PLAYERS, 1.0F, 1.2F);
				world.playSound(null, user.getX(), user.getY(), user.getZ(), SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 0.6F, 1.4F);
				if (!user.getAbilities().creativeMode) {
					stack.decrement(1);
				}
				return TypedActionResult.consume(stack);
			}
			return TypedActionResult.fail(stack);
		}

		@Override
		public boolean hasGlint(ItemStack stack) {
			return true;
		}

		@Override
		public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
			tooltip.add(Text.translatable(getTranslationKey() + ".desc").formatted(Formatting.GRAY));
		}
	});

	// ------------------------------------------------------------------ medicina (fase 93)
	public static final Item FERMENTED_MASH = item("fermented_mash", new Item(new Item.Settings().maxCount(16)) {
		@Override
		public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
			tooltip.add(Text.translatable(getTranslationKey() + ".desc").formatted(Formatting.GRAY));
		}
	});
	public static final Item ALCOHOL = item("alcohol", new MedicalItem(new Item.Settings().maxCount(16), 32, SoundEvents.ENTITY_GENERIC_DRINK,
			Medicine::alcohol).drink());
	public static final Item MEDICINE = item("medicine", new MedicalItem(new Item.Settings().maxCount(16).rarity(Rarity.UNCOMMON), 32,
			SoundEvents.ENTITY_GENERIC_DRINK, Medicine::medicine).drink());
	public static final Item ANTIDOTE = item("antidote", new MedicalItem(new Item.Settings().maxCount(16).rarity(Rarity.UNCOMMON), 32,
			SoundEvents.ENTITY_GENERIC_DRINK, Medicine::antidote).drink());
	public static final Item MEDKIT = item("medkit", new MedicalItem(new Item.Settings().maxCount(4).rarity(Rarity.RARE), 60,
			SoundEvents.ITEM_ARMOR_EQUIP_LEATHER.value(), Medicine::medkit));

	public static final ItemGroup GROUP = Registry.register(Registries.ITEM_GROUP, EvolvedWarfare.id("survival"), FabricItemGroup.builder()
			.icon(() -> new ItemStack(BANDAGE))
			.displayName(Text.translatable("itemGroup.evolvedwarfare.survival"))
			.entries((ctx, entries) -> ITEMS.forEach(entries::add))
			.build());

	private HealthItems() {
	}

	private static Item item(String name, Item item) {
		ITEMS.add(Registry.register(Registries.ITEM, EvolvedWarfare.id(name), item));
		return item;
	}

	public static void register() {
		// carga la clase
	}
}
