package com.evolvedwarfare.work;

import java.util.List;
import java.util.function.Predicate;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

/**
 * Talleres que transforman unas cosas en otras sin combustible: carbonero, cantero y flechero. Toma los materiales
 * de los suministros y deja el resultado en la producción.
 */
public class ConversionJob implements Job {
	record Input(Predicate<ItemStack> filter, int count) {
	}

	record Conversion(List<Input> inputs, Item output, int outputCount, int ticks) {
	}

	private final List<Conversion> conversions;
	private final SoundEvent sound;
	@Nullable
	private final ParticleEffect particle;

	private ConversionJob(List<Conversion> conversions, SoundEvent sound, @Nullable ParticleEffect particle) {
		this.conversions = conversions;
		this.sound = sound;
		this.particle = particle;
	}

	private static Conversion simple(Item from, int count, Item to, int outputCount, int ticks) {
		return new Conversion(List.of(new Input(s -> s.isOf(from), count)), to, outputCount, ticks);
	}

	/** 5 troncos → 4 de carbón vegetal (uno se quema para hacer el resto). */
	public static ConversionJob charcoalBurner() {
		return new ConversionJob(List.of(
				new Conversion(List.of(new Input(s -> s.isIn(ItemTags.LOGS_THAT_BURN), 5)), Items.CHARCOAL, 4, 200)),
				SoundEvents.BLOCK_FIRE_AMBIENT, ParticleTypes.LARGE_SMOKE);
	}

	/** Piedra labrada. */
	public static ConversionJob stonemason() {
		return new ConversionJob(List.of(
				simple(Items.COBBLESTONE, 1, Items.STONE_BRICKS, 1, 30),
				simple(Items.STONE, 1, Items.STONE_BRICKS, 1, 30),
				simple(Items.COBBLED_DEEPSLATE, 1, Items.DEEPSLATE_BRICKS, 1, 30),
				simple(Items.ANDESITE, 1, Items.POLISHED_ANDESITE, 1, 30),
				simple(Items.DIORITE, 1, Items.POLISHED_DIORITE, 1, 30),
				simple(Items.GRANITE, 1, Items.POLISHED_GRANITE, 1, 30),
				simple(Items.BLACKSTONE, 1, Items.POLISHED_BLACKSTONE_BRICKS, 1, 30),
				simple(Items.TUFF, 1, Items.TUFF_BRICKS, 1, 30),
				simple(Items.SAND, 4, Items.SANDSTONE, 1, 40),
				simple(Items.RED_SAND, 4, Items.RED_SANDSTONE, 1, 40)),
				SoundEvents.UI_STONECUTTER_TAKE_RESULT, null);
	}

	/** Flechas (pedernal + palo + pluma → 4) y pedernal a partir de grava. */
	public static ConversionJob fletcher() {
		return new ConversionJob(List.of(
				new Conversion(List.of(new Input(s -> s.isOf(Items.FLINT), 1), new Input(s -> s.isOf(Items.STICK), 1),
						new Input(s -> s.isOf(Items.FEATHER), 1)), Items.ARROW, 4, 40),
				simple(Items.GRAVEL, 1, Items.FLINT, 1, 30)),
				SoundEvents.BLOCK_WOOD_HIT, null);
	}

	private static boolean available(WorkContext ctx, Conversion conversion) {
		for (Input input : conversion.inputs()) {
			if (ctx.site.count(input.filter(), false) < input.count()) {
				return false;
			}
		}
		return true;
	}

	@Override
	@Nullable
	public WorkAction plan(WorkContext ctx) {
		if (ctx.outputFull()) {
			return ctx.idle(WorkStatus.STORAGE_FULL);
		}
		for (Conversion conversion : conversions) {
			if (available(ctx, conversion)) {
				WorkAction action = WorkAction.fromSite(ctx.sitePos, Vec3d.ofCenter(ctx.sitePos), ctx.ticks(conversion.ticks()),
						c -> convert(c, conversion));
				if (particle != null) {
					action.effect((c, t) -> {
						if (t % 15 == 0) {
							c.world.spawnParticles(particle, c.sitePos.getX() + 0.5, c.sitePos.getY() + 1.05, c.sitePos.getZ() + 0.5,
									1, 0.2, 0.0, 0.2, 0.01);
						}
					});
				}
				return action;
			}
		}
		return ctx.idle(WorkStatus.NO_SUPPLIES);
	}

	private boolean convert(WorkContext ctx, Conversion conversion) {
		if (!available(ctx, conversion)) {
			return false;
		}
		for (Input input : conversion.inputs()) {
			ctx.site.consume(input.filter(), input.count(), false);
		}
		ctx.store(new ItemStack(conversion.output(), conversion.outputCount()));
		ctx.sound(ctx.sitePos, sound, 0.7F, 1.0F);
		return true;
	}
}
