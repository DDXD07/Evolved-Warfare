package com.evolvedwarfare.work;

import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.recipe.AbstractCookingRecipe;
import net.minecraft.recipe.RecipeEntry;
import net.minecraft.recipe.RecipeType;
import net.minecraft.recipe.input.SingleStackRecipeInput;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

/**
 * Herrero y cocinero: trabajan en su puesto como un horno atendido. Cogen de los suministros lo que se puede cocinar
 * con su tipo de receta (alto horno: menas y metal en bruto; ahumador: comida) y el combustible (carbón, madera,
 * cubos de lava...). Cada pieza gasta lo mismo que en un horno: un carbón da para 8.
 * El cocinero también hornea pan con 3 de trigo.
 */
public class CookingJob<R extends AbstractCookingRecipe> implements Job {
	public static final int FUEL_PER_ITEM = 200;
	private static final Predicate<ItemStack> WHEAT = s -> s.isOf(Items.WHEAT);

	private final RecipeType<R> type;
	private final boolean bakesBread;
	private final ParticleEffect particle;

	public CookingJob(RecipeType<R> type, boolean bakesBread, ParticleEffect particle) {
		this.type = type;
		this.bakesBread = bakesBread;
		this.particle = particle;
	}

	@Override
	@Nullable
	public WorkAction plan(WorkContext ctx) {
		if (ctx.outputFull()) {
			return ctx.idle(WorkStatus.STORAGE_FULL);
		}
		boolean bread = bakesBread && ctx.site.count(WHEAT, false) >= 3;
		if (findInput(ctx) < 0 && !bread) {
			return ctx.idle(WorkStatus.NO_SUPPLIES);
		}
		if (ctx.site.getFuel() < FUEL_PER_ITEM && findFuel(ctx) < 0) {
			return ctx.idle(WorkStatus.NO_FUEL);
		}
		return WorkAction.fromSite(ctx.sitePos, Vec3d.ofCenter(ctx.sitePos), ctx.ticks(100), this::cook)
				.effect((c, t) -> {
					if (t % 12 == 0) {
						c.world.spawnParticles(particle, c.sitePos.getX() + 0.5, c.sitePos.getY() + 1.05, c.sitePos.getZ() + 0.5,
								2, 0.2, 0.0, 0.2, 0.01);
					}
				});
	}

	private Optional<RecipeEntry<R>> match(WorkContext ctx, ItemStack stack) {
		return ctx.world.getRecipeManager().getFirstMatch(type, new SingleStackRecipeInput(stack), ctx.world);
	}

	private int findInput(WorkContext ctx) {
		for (int i = 0; i < ctx.site.getSupplySlots(); i++) {
			ItemStack stack = ctx.site.getStack(i);
			if (!stack.isEmpty() && match(ctx, stack).isPresent()) {
				return i;
			}
		}
		return -1;
	}

	private int findFuel(WorkContext ctx) {
		for (int i = 0; i < ctx.site.getSupplySlots(); i++) {
			ItemStack stack = ctx.site.getStack(i);
			if (!stack.isEmpty() && AbstractFurnaceBlockEntity.canUseAsFuel(stack) && match(ctx, stack).isEmpty()
					&& !(bakesBread && stack.isOf(Items.WHEAT))) {
				return i;
			}
		}
		return -1;
	}

	/** Echa combustible hasta tener para una pieza más. */
	private boolean ensureFuel(WorkContext ctx) {
		for (int tries = 0; tries < 8 && ctx.site.getFuel() < FUEL_PER_ITEM; tries++) {
			int slot = findFuel(ctx);
			if (slot < 0) {
				return false;
			}
			ItemStack stack = ctx.site.getStack(slot);
			int time = AbstractFurnaceBlockEntity.createFuelTimeMap().getOrDefault(stack.getItem(), 0);
			if (time <= 0) {
				return false;
			}
			Item remainder = stack.getItem().getRecipeRemainder();
			stack.decrement(1);
			ctx.site.addFuel(time);
			if (remainder != null) {
				ctx.store(new ItemStack(remainder));
			}
		}
		return ctx.site.getFuel() >= FUEL_PER_ITEM;
	}

	private boolean cook(WorkContext ctx) {
		if (!ensureFuel(ctx)) {
			return false;
		}
		ItemStack result;
		int slot = findInput(ctx);
		if (slot >= 0) {
			ItemStack input = ctx.site.getStack(slot);
			Optional<RecipeEntry<R>> recipe = match(ctx, input);
			if (recipe.isEmpty()) {
				return false;
			}
			result = recipe.get().value().craft(new SingleStackRecipeInput(input), ctx.world.getRegistryManager());
			input.decrement(1);
		} else if (bakesBread && ctx.site.consume(WHEAT, 3, false)) {
			result = new ItemStack(Items.BREAD);
		} else {
			return false;
		}
		ctx.site.addFuel(-FUEL_PER_ITEM);
		ctx.store(result);
		ctx.sound(ctx.sitePos, SoundEvents.BLOCK_FURNACE_FIRE_CRACKLE, 0.8F, 1.0F);
		return true;
	}
}
