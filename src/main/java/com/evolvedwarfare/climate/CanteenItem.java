package com.evolvedwarfare.climate;

import com.evolvedwarfare.registry.ModItems;
import java.util.List;
import net.minecraft.advancement.criterion.Criteria;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsage;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.stat.Stats;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.UseAction;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import net.minecraft.world.event.GameEvent;

/**
 * La cantimplora: {@link #SIPS} tragos. Vacía se llena en el agua (clic derecho), y entonces es agua sin hervir (puede
 * traer parásitos); hervida en el horno, en la fogata o llenada en un caldero al fuego, es agua hervida. Los tragos que
 * quedan se ven en la barra (azul).
 */
public class CanteenItem extends Item {
	public static final int SIPS = 4;

	public enum Kind {
		EMPTY, RAW, BOILED
	}

	private final Kind kind;

	public CanteenItem(Kind kind, Settings settings) {
		super(settings);
		this.kind = kind;
	}

	public Kind kind() {
		return kind;
	}

	/** La sed que quita cada trago. */
	public float sip() {
		return switch (kind) {
			case RAW -> 5.0F;
			case BOILED -> 6.0F;
			default -> 0.0F;
		};
	}

	public boolean risky() {
		return kind == Kind.RAW;
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
		ItemStack stack = user.getStackInHand(hand);
		if (kind == Kind.EMPTY || stack.getDamage() > 0) {
			BlockHitResult hit = raycast(world, user, RaycastContext.FluidHandling.SOURCE_ONLY);
			if (hit.getType() == HitResult.Type.BLOCK && world.getFluidState(hit.getBlockPos()).isIn(FluidTags.WATER)) {
				BlockPos pos = hit.getBlockPos();
				// agua realista: se lleva 4 capas (fuera del mar y los ríos)
				if (!world.isClient && !com.evolvedwarfare.fluid.FiniteFluids.drain(world, pos, net.minecraft.fluid.Fluids.WATER, com.evolvedwarfare.fluid.FiniteFluids.CANTEEN)) {
					return TypedActionResult.fail(stack);
				}
				world.playSound(user, user.getX(), user.getY(), user.getZ(), SoundEvents.ITEM_BOTTLE_FILL, SoundCategory.NEUTRAL, 1.0F, 0.9F);
				world.emitGameEvent(user, GameEvent.FLUID_PICKUP, pos);
				user.incrementStat(Stats.USED.getOrCreateStat(this));
				return TypedActionResult.success(ItemUsage.exchangeStack(stack, user, new ItemStack(ModItems.WATER_CANTEEN)), world.isClient());
			}
		}
		if (kind == Kind.EMPTY) {
			return TypedActionResult.pass(stack);
		}
		return ItemUsage.consumeHeldItem(world, user, hand);
	}

	@Override
	public UseAction getUseAction(ItemStack stack) {
		return kind == Kind.EMPTY ? UseAction.NONE : UseAction.DRINK;
	}

	@Override
	public int getMaxUseTime(ItemStack stack, LivingEntity user) {
		return kind == Kind.EMPTY ? 0 : 32;
	}

	@Override
	public SoundEvent getDrinkSound() {
		return SoundEvents.ENTITY_GENERIC_DRINK;
	}

	@Override
	public ItemStack finishUsing(ItemStack stack, World world, LivingEntity user) {
		if (kind == Kind.EMPTY) {
			return stack;
		}
		if (user instanceof ServerPlayerEntity player) {
			Criteria.CONSUME_ITEM.trigger(player, stack);
			player.incrementStat(Stats.USED.getOrCreateStat(this));
			Thirst.drinkWater(player, sip(), risky());
		}
		if (user instanceof PlayerEntity player && player.isInCreativeMode()) {
			return stack;
		}
		return afterSip(stack);
	}

	/** La cantimplora después de un trago (vacía, si era el último). */
	public static ItemStack afterSip(ItemStack stack) {
		int used = stack.getDamage() + 1;
		if (used >= SIPS) {
			return new ItemStack(ModItems.CANTEEN);
		}
		stack.setDamage(used);
		return stack;
	}

	@Override
	public int getItemBarColor(ItemStack stack) {
		return kind == Kind.BOILED ? 0xA8E4FF : 0x3F8FD8;
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
		super.appendTooltip(stack, context, tooltip, type);
		if (kind != Kind.EMPTY) {
			tooltip.add(Text.translatable("tooltip.evolvedwarfare.canteen.sips", SIPS - stack.getDamage(), SIPS).formatted(Formatting.AQUA));
		}
		tooltip.add(Text.translatable("item.evolvedwarfare.canteen.desc." + kind.name().toLowerCase(java.util.Locale.ROOT)).formatted(Formatting.GRAY));
	}
}
