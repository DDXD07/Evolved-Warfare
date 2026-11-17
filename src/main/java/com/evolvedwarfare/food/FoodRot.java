package com.evolvedwarfare.food;

import com.evolvedwarfare.EvolvedWarfare;
import com.evolvedwarfare.config.EwConfig;
import com.mojang.serialization.Codec;
import java.util.Set;
import java.util.function.LongSupplier;
import net.minecraft.component.ComponentType;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.FoodComponent;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.math.MathHelper;
import org.jetbrains.annotations.Nullable;

/**
 * La comida que se pudre (fase 98): cada comida, al pasar por tu inventario, recibe la hora a la que se echa a perder
 * (según lo que sea: la carne y el pescado crudos, 2 días; cocinados, 4; los guisos, 3; lo demás, 6; lo seco y lo de oro,
 * nunca). Se va pasando también guardada en un cofre. <b>Fresca</b> (más de la mitad), <b>pasada</b> (alimenta algo
 * menos y puede dar hambre), <b>pocha</b> (alimenta la mitad, da hambre, náuseas y a veces veneno) y <b>podrida</b>: se
 * convierte en carne podrida o comida podrida. Se conserva <b>salándola</b> (×4), <b>ahumándola</b> en el ahumador (×3;
 * las dos cosas, ×8) y guardándola en una <b>nevera</b> (se pasa diez veces más despacio).
 */
public final class FoodRot {
	/** Cuándo se echa a perder (tiempo del mundo, en ticks). */
	public static final ComponentType<Long> EXPIRES = Registry.register(Registries.DATA_COMPONENT_TYPE, EvolvedWarfare.id("expires"),
			ComponentType.<Long>builder().codec(Codec.LONG).packetCodec(PacketCodecs.VAR_LONG).build());
	/** Cómo está conservada: 1 salada, 2 ahumada. */
	public static final ComponentType<Integer> PRESERVED = Registry.register(Registries.DATA_COMPONENT_TYPE, EvolvedWarfare.id("preserved"),
			ComponentType.<Integer>builder().codec(Codec.INT).packetCodec(PacketCodecs.VAR_INT).build());

	public static final int SALTED = 1, SMOKED = 2;
	public static final long DAY = 24000L;
	/** Las horas de caducidad se redondean a la hora del juego (para que se apilen). */
	public static final long BUCKET = 1000L;

	public enum Stage {
		FRESH, STALE, SPOILING, ROTTEN
	}

	private static final Set<Item> NEVER = Set.of(Items.DRIED_KELP, Items.GOLDEN_APPLE, Items.ENCHANTED_GOLDEN_APPLE, Items.GOLDEN_CARROT,
			Items.HONEY_BOTTLE, Items.ROTTEN_FLESH, Items.SPIDER_EYE, Items.CHORUS_FRUIT, Items.POISONOUS_POTATO, Items.SUSPICIOUS_STEW,
			Items.OMINOUS_BOTTLE);

	/** El reloj del mundo en el cliente (para las barras y las descripciones); lo pone el cliente. */
	public static LongSupplier clientTime = () -> -1L;

	private FoodRot() {
	}

	public static boolean enabled() {
		return EwConfig.get().foodRotEnabled;
	}

	public static void register() {
		// carga la clase (los componentes)
	}

	/** ¿Se pudre? (es comida y no de las que aguantan siempre) */
	public static boolean perishable(ItemStack stack) {
		if (stack.isEmpty() || stack.get(DataComponentTypes.FOOD) == null || NEVER.contains(stack.getItem())) {
			return false;
		}
		return !stack.isOf(FoodItems.ROTTEN_FOOD) && !stack.isOf(FoodItems.SALT);
	}

	public static boolean meat(ItemStack stack) {
		return stack.isIn(ItemTags.MEAT) || stack.isIn(ItemTags.FISHES);
	}

	/** Lo que aguanta (ticks) sin conservar. */
	public static long baseShelf(ItemStack stack) {
		String path = Registries.ITEM.getId(stack.getItem()).getPath();
		double days;
		if (meat(stack)) {
			days = path.startsWith("cooked_") ? 4 : 2;
		} else if (path.endsWith("_stew") || path.endsWith("_soup")) {
			days = 3;
		} else if (stack.isOf(Items.POTATO) || stack.isOf(Items.CARROT) || stack.isOf(Items.BEETROOT)) {
			days = 10;
		} else {
			days = 6;
		}
		return (long) (days * DAY / Math.max(0.05, EwConfig.get().foodRotSpeed));
	}

	public static int preserved(ItemStack stack) {
		Integer p = stack.get(PRESERVED);
		return p == null ? 0 : p;
	}

	public static double multiplier(int preserved) {
		boolean salted = (preserved & SALTED) != 0, smoked = (preserved & SMOKED) != 0;
		return salted && smoked ? 8.0 : salted ? 4.0 : smoked ? 3.0 : 1.0;
	}

	public static long shelf(ItemStack stack) {
		return (long) (baseShelf(stack) * multiplier(preserved(stack)));
	}

	@Nullable
	public static Long expires(ItemStack stack) {
		return stack.get(EXPIRES);
	}

	private static long round(long time) {
		return Math.floorDiv(time, BUCKET) * BUCKET;
	}

	/** Le pone la caducidad si aún no la tiene. */
	public static void stamp(ItemStack stack, long now) {
		if (perishable(stack) && !stack.contains(EXPIRES)) {
			stack.set(EXPIRES, round(now + shelf(stack)));
		}
	}

	/** Lo que le queda (de 1, recién hecha, a 0, podrida). */
	public static float fraction(ItemStack stack, long now) {
		Long exp = expires(stack);
		if (exp == null) {
			return 1.0F;
		}
		return MathHelper.clamp((float) (exp - now) / Math.max(1L, shelf(stack)), 0.0F, 1.0F);
	}

	public static Stage stage(ItemStack stack, long now) {
		Long exp = expires(stack);
		if (exp == null) {
			return Stage.FRESH;
		}
		if (exp <= now) {
			return Stage.ROTTEN;
		}
		float f = fraction(stack, now);
		return f > 0.5F ? Stage.FRESH : f > 0.2F ? Stage.STALE : Stage.SPOILING;
	}

	/** Lo que hay en el inventario de un jugador, cada segundo: se le pone la caducidad, se redondea y, si toca, se pudre. */
	@Nullable
	public static ItemStack check(ItemStack stack, long now) {
		if (!enabled() || !perishable(stack)) {
			return null;
		}
		Long exp = expires(stack);
		if (exp == null) {
			stamp(stack, now);
			return null;
		}
		if (exp % BUCKET != 0) {
			stack.set(EXPIRES, round(exp));
		}
		if (exp <= now) {
			return rotten(stack);
		}
		return null;
	}

	/** En lo que se convierte al pudrirse. */
	public static ItemStack rotten(ItemStack stack) {
		return new ItemStack(meat(stack) ? Items.ROTTEN_FLESH : FoodItems.ROTTEN_FOOD, stack.getCount());
	}

	/** Conservar: salar o ahumar (lo que le quede se multiplica, sin refrescarla). */
	public static void preserve(ItemStack stack, int how, long now) {
		if (!perishable(stack)) {
			return;
		}
		Long exp = expires(stack);
		long before = shelf(stack);
		long left = exp == null ? before : Math.max(0L, exp - now);
		stack.set(PRESERVED, preserved(stack) | how);
		long after = shelf(stack);
		stack.set(EXPIRES, round(now + (long) ((double) left * after / Math.max(1L, before))));
	}

	/** Recién ahumada: como nueva y con su caducidad de ahumada. */
	public static void smoked(ItemStack stack, long now) {
		if (!perishable(stack)) {
			return;
		}
		stack.set(PRESERVED, preserved(stack) | SMOKED);
		stack.set(EXPIRES, round(now + shelf(stack)));
	}

	// ------------------------------------------------------------------ comerla

	/** Lo que alimenta según cómo esté. */
	public static FoodComponent nourishment(FoodComponent food, ItemStack stack, long now) {
		if (!enabled()) {
			return food;
		}
		float m = switch (stage(stack, now)) {
			case STALE -> 0.8F;
			case SPOILING, ROTTEN -> 0.5F;
			default -> 1.0F;
		};
		if (m >= 1.0F) {
			return food;
		}
		return new FoodComponent(Math.max(1, Math.round(food.nutrition() * m)), food.saturation() * m, food.canAlwaysEat(), food.eatSeconds(),
				food.usingConvertsTo(), food.effects());
	}

	/** Lo que sienta mal comerla pasada o pocha. */
	public static void afterEating(PlayerEntity player, ItemStack stack, long now) {
		if (!enabled() || player.getWorld().isClient) {
			return;
		}
		var r = player.getRandom();
		switch (stage(stack, now)) {
			case STALE -> {
				if (r.nextFloat() < 0.25F) {
					player.addStatusEffect(new StatusEffectInstance(StatusEffects.HUNGER, 20 * 15, 0));
				}
			}
			case SPOILING, ROTTEN -> {
				if (r.nextFloat() < 0.7F) {
					player.addStatusEffect(new StatusEffectInstance(StatusEffects.HUNGER, 20 * 30, 0));
				}
				if (r.nextFloat() < 0.4F) {
					player.addStatusEffect(new StatusEffectInstance(StatusEffects.NAUSEA, 20 * 8, 0));
				}
				if (r.nextFloat() < 0.2F) {
					player.addStatusEffect(new StatusEffectInstance(StatusEffects.POISON, 20 * 4, 0));
				}
			}
			default -> {
			}
		}
	}

	// ------------------------------------------------------------------ la barra

	public static boolean showBar(ItemStack stack) {
		return enabled() && stack.contains(EXPIRES) && clientTime.getAsLong() >= 0;
	}

	public static int barStep(ItemStack stack) {
		return Math.round(13.0F * fraction(stack, clientTime.getAsLong()));
	}

	public static int barColor(ItemStack stack) {
		float f = fraction(stack, clientTime.getAsLong());
		return MathHelper.hsvToRgb(f / 3.0F * 0.9F, 0.85F, 1.0F);
	}
}
