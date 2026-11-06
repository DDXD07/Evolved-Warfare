package com.evolvedwarfare.smithing;

import com.evolvedwarfare.network.ActivityPayload;
import com.evolvedwarfare.registry.ModComponents;
import java.util.List;
import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.UseAction;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

/**
 * El crisol en la mano: con metal fundido, mantén clic derecho apuntando a un molde tallado para verter (el chorro va más
 * deprisa cuanto más rato lo inclinas: suelta a tiempo). Fuera del fuego el metal se enfría en 30 s. Si no, se pone en el
 * suelo como un bloque (encima del fuego, para fundir).
 */
public class CrucibleItem extends BlockItem {
	public CrucibleItem(Block block, Settings settings) {
		super(block, settings);
	}

	public static CrucibleContents contents(ItemStack stack) {
		return stack.getOrDefault(ModComponents.CRUCIBLE, CrucibleContents.EMPTY);
	}

	@Override
	public ActionResult useOnBlock(ItemUsageContext context) {
		PlayerEntity player = context.getPlayer();
		World world = context.getWorld();
		if (player != null && world.getBlockEntity(context.getBlockPos()) instanceof MoldBlockEntity mold) {
			CrucibleContents c = contents(context.getStack());
			if (c.molten() > 0.01F && mold.accepts(c.material())) {
				player.setCurrentHand(context.getHand());
				if (!world.isClient) {
					world.playSound(null, context.getBlockPos(), SoundEvents.ITEM_BUCKET_EMPTY_LAVA, SoundCategory.PLAYERS, 0.6F, 1.2F);
					if (player instanceof ServerPlayerEntity sp) {
						ActivityPayload.set(sp, ActivityPayload.POURING);
					}
				}
				return ActionResult.CONSUME;
			}
			if (!world.isClient) {
				player.sendMessage(Text.translatable(c.molten() <= 0.01F ? "message.evolvedwarfare.crucible.no_molten"
						: "message.evolvedwarfare.crucible.wrong_mold").formatted(Formatting.GOLD), true);
			}
			return ActionResult.FAIL;
		}
		return super.useOnBlock(context);
	}

	@Override
	public int getMaxUseTime(ItemStack stack, LivingEntity user) {
		return 72000;
	}

	@Override
	public UseAction getUseAction(ItemStack stack) {
		return UseAction.NONE;
	}

	@Override
	public void usageTick(World world, LivingEntity user, ItemStack stack, int remainingUseTicks) {
		if (world.isClient || !(user instanceof PlayerEntity player)) {
			return;
		}
		CrucibleContents c = contents(stack);
		HitResult hit = player.raycast(5.0, 0.0F, false);
		if (c.molten() <= 0.01F || !(hit instanceof BlockHitResult bhr) || hit.getType() != HitResult.Type.BLOCK
				|| !(world.getBlockEntity(bhr.getBlockPos()) instanceof MoldBlockEntity mold)) {
			player.stopUsingItem();
			return;
		}
		int held = getMaxUseTime(stack, user) - remainingUseTicks;
		// el chorro: empieza fino y va a más
		float rate = Math.min(0.09F, 0.012F + held * 0.0016F);
		float amount = Math.min(rate, c.molten());
		float used = mold.pour(c.material(), amount);
		if (used <= 0.0F) {
			player.stopUsingItem();
			return;
		}
		float left = c.molten() - used;
		stack.set(ModComponents.CRUCIBLE, left <= 0.01F && c.solid() <= 0 ? CrucibleContents.EMPTY
				: new CrucibleContents(c.metal(), c.solid(), Math.max(0.0F, left), c.coolAt()));
		if (world instanceof ServerWorld sw) {
			Vec3d from = player.getEyePos().add(player.getRotationVec(1.0F).multiply(0.7)).add(0, -0.35, 0);
			Vec3d to = Vec3d.ofCenter(bhr.getBlockPos()).add(0, -0.25, 0);
			for (int i = 0; i < 4; i++) {
				Vec3d p = from.lerp(to, world.random.nextDouble());
				sw.spawnParticles(ParticleTypes.FALLING_LAVA, p.x, p.y, p.z, 1, 0.02, 0.0, 0.02, 0.0);
			}
			if (held % 6 == 0) {
				sw.spawnParticles(ParticleTypes.FLAME, to.x, to.y + 0.3, to.z, 2, 0.15, 0.02, 0.15, 0.01);
				world.playSound(null, bhr.getBlockPos(), SoundEvents.BLOCK_LAVA_POP, SoundCategory.BLOCKS, 0.5F, 0.7F + world.random.nextFloat() * 0.4F);
			}
		}
	}

	@Override
	public void onStoppedUsing(ItemStack stack, World world, LivingEntity user, int remainingUseTicks) {
		if (user instanceof ServerPlayerEntity sp) {
			ActivityPayload.set(sp, ActivityPayload.NONE);
		}
	}

	@Override
	public void inventoryTick(ItemStack stack, World world, Entity entity, int slot, boolean selected) {
		if (world.isClient || world.getTime() % 10 != 0) {
			return;
		}
		CrucibleContents c = contents(stack);
		if (c.molten() > 0.01F) {
			if (c.coolAt() == 0L) {
				stack.set(ModComponents.CRUCIBLE, new CrucibleContents(c.metal(), c.solid(), c.molten(), world.getTime() + CrucibleBlockEntity.COOL_TICKS));
			} else if (world.getTime() >= c.coolAt()) {
				CrucibleContents solid = CrucibleBlockEntity.solidified(c);
				if (solid.isEmpty()) {
					stack.remove(ModComponents.CRUCIBLE);
				} else {
					stack.set(ModComponents.CRUCIBLE, solid);
				}
				world.playSound(null, entity.getBlockPos(), SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.PLAYERS, 0.4F, 1.4F);
			}
		}
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
		CrucibleContents c = contents(stack);
		if (c.isEmpty()) {
			tooltip.add(Text.translatable("tooltip.evolvedwarfare.crucible.empty").formatted(Formatting.GRAY));
			return;
		}
		Text metal = Text.translatable("material.evolvedwarfare." + c.metal());
		if (c.molten() > 0.01F) {
			tooltip.add(Text.translatable("tooltip.evolvedwarfare.crucible.molten", metal, String.format(java.util.Locale.ROOT, "%.1f", c.molten()))
					.formatted(Formatting.GOLD));
		}
		if (c.solid() > 0) {
			tooltip.add(Text.translatable("tooltip.evolvedwarfare.crucible.solid", metal, c.solid()).formatted(Formatting.GRAY));
		}
	}
}
