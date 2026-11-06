package com.evolvedwarfare.hell;

import com.evolvedwarfare.registry.ModItems;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.item.EnchantedBookItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * Los ritos del círculo de ritual: cada uno es un puñado de ofrendas (sin orden). Purificación, pacto oscuro, los siete
 * sellos (la Llave del Abismo), consagración (Bendición Divina), llamar al Falso Profeta y despertar a la Bestia mayor.
 */
public final class Rituals {
	public static final String[] NAMES = {"purification", "dark_pact", "seals", "consecration", "prophet", "beast"};

	public enum Rite {
		PURIFICATION, DARK_PACT, SEALS, CONSECRATION, PROPHET, BEAST
	}

	private Rituals() {
	}

	private static Predicate<ItemStack> is(net.minecraft.item.Item item) {
		return s -> s.isOf(item);
	}

	private static Predicate<ItemStack> seal(int k) {
		return s -> s.isOf(Apocalypse.SEAL_FRAGMENT) && SealFragmentItem.kind(s) == k;
	}

	private static final Predicate<ItemStack> ANY_SEAL = s -> s.isOf(Apocalypse.SEAL_FRAGMENT);

	static List<Predicate<ItemStack>> recipe(Rite rite) {
		return switch (rite) {
			case PURIFICATION -> List.of(is(ModItems.BLESSED_SALT), is(Items.GOLD_INGOT), is(Items.GLOWSTONE_DUST));
			case DARK_PACT -> List.of(is(ModItems.ABYSS_METAL), is(ModItems.DARK_FEATHER), is(Items.SOUL_SAND));
			case SEALS -> List.of(seal(0), seal(1), seal(2), seal(3), seal(4), seal(5), seal(6));
			case CONSECRATION -> List.of(is(Items.BOOK), is(ModItems.BLESSED_SALT), is(ModItems.BLESSED_SALT), is(Items.GOLD_BLOCK), ANY_SEAL);
			case PROPHET -> List.of(is(Items.GOLD_BLOCK), is(Items.WRITTEN_BOOK), s -> s.isIn(ItemTags.CANDLES));
			case BEAST -> List.of(is(Items.MAGMA_CREAM), is(Items.BLAZE_ROD), is(ModItems.FALLEN_ARMOR_SHARD), is(HellStructures.BOOK_OF_CONDEMNATION));
		};
	}

	/** ¿Qué rito forman estas ofrendas? (todas tienen que encajar, sin sobrar ninguna). */
	@Nullable
	public static Rite match(List<ItemStack> offerings) {
		for (Rite rite : Rite.values()) {
			List<Predicate<ItemStack>> r = recipe(rite);
			if (r.size() == offerings.size() && fits(new ArrayList<>(r), offerings, 0)) {
				return rite;
			}
		}
		return null;
	}

	private static boolean fits(List<Predicate<ItemStack>> left, List<ItemStack> items, int i) {
		if (i == items.size()) {
			return left.isEmpty();
		}
		for (int k = 0; k < left.size(); k++) {
			if (left.get(k).test(items.get(i))) {
				Predicate<ItemStack> p = left.remove(k);
				if (fits(left, items, i + 1)) {
					return true;
				}
				left.add(k, p);
			}
		}
		return false;
	}

	/** Hace el rito del círculo (si lo hay); devuelve cuál fue (null si ninguno o no se pudo). */
	@Nullable
	public static Rite perform(ServerWorld world, BlockPos pos, ServerPlayerEntity player, RitualCircleBlockEntity circle) {
		Rite rite = match(circle.offerings());
		if (rite == null) {
			player.sendMessage(Text.translatable("message.evolvedwarfare.ritual.nothing").formatted(Formatting.GRAY), true);
			world.playSound(null, pos, SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.BLOCKS, 0.6F, 0.6F);
			return null;
		}
		boolean nether = world.getRegistryKey() == World.NETHER;
		double x = pos.getX() + 0.5, y = pos.getY() + 0.3, z = pos.getZ() + 0.5;
		switch (rite) {
			case PURIFICATION -> {
				Curses.lift(player);
				NetherClimate.addCorruption(player, -40);
				player.addStatusEffect(new StatusEffectInstance(Apocalypse.BLESSED_WARD, 20 * 60 * 10, 0));
				world.spawnParticles(ParticleTypes.END_ROD, x, y + 1, z, 80, 0.6, 1.2, 0.6, 0.05);
				world.playSound(null, pos, SoundEvents.BLOCK_BEACON_POWER_SELECT, SoundCategory.BLOCKS, 1.0F, 1.2F);
			}
			case DARK_PACT -> {
				if (!nether) {
					player.sendMessage(Text.translatable("message.evolvedwarfare.ritual.need_nether").formatted(Formatting.GRAY), true);
					return null;
				}
				NetherClimate.addCorruption(player, 25);
				player.addStatusEffect(new StatusEffectInstance(Apocalypse.DARK_PACT, 20 * 60 * 5, 0));
				world.spawnParticles(ParticleTypes.SQUID_INK, x, y + 1, z, 60, 0.5, 1.0, 0.5, 0.05);
				world.playSound(null, pos, SoundEvents.ENTITY_WARDEN_SONIC_CHARGE, SoundCategory.BLOCKS, 1.0F, 0.6F);
			}
			case SEALS -> give(player, new ItemStack(Apocalypse.ABYSS_KEY));
			case CONSECRATION -> {
				var entry = world.getRegistryManager().get(RegistryKeys.ENCHANTMENT).getEntry(DivineBlessing.KEY).orElse(null);
				if (entry == null) {
					return null;
				}
				give(player, EnchantedBookItem.forEnchantment(new net.minecraft.enchantment.EnchantmentLevelEntry(entry, 1)));
				world.spawnParticles(ParticleTypes.END_ROD, x, y + 1, z, 60, 0.4, 1.0, 0.4, 0.08);
			}
			case PROPHET -> {
				if (!world.getEntitiesByClass(FalseProphetEntity.class, new Box(pos).expand(64), e -> !e.illusion()).isEmpty()) {
					player.sendMessage(Text.translatable("message.evolvedwarfare.ritual.busy").formatted(Formatting.GRAY), true);
					return null;
				}
				if (Apocalypse.summon(world, Apocalypse.FALSE_PROPHET, pos.up().offset(player.getHorizontalFacing().getOpposite(), 4), player) == null) {
					return null;
				}
			}
			case BEAST -> {
				if (!nether) {
					player.sendMessage(Text.translatable("message.evolvedwarfare.ritual.need_nether").formatted(Formatting.GRAY), true);
					return null;
				}
				if (!world.getEntitiesByClass(GreaterBeastEntity.class, new Box(pos).expand(64), e -> true).isEmpty()) {
					player.sendMessage(Text.translatable("message.evolvedwarfare.ritual.busy").formatted(Formatting.GRAY), true);
					return null;
				}
				BlockPos lava = lavaNear(world, pos, 14);
				if (lava == null) {
					player.sendMessage(Text.translatable("message.evolvedwarfare.ritual.need_lava").formatted(Formatting.GRAY), true);
					return null;
				}
				world.spawnParticles(ParticleTypes.LAVA, lava.getX() + 0.5, lava.getY() + 1, lava.getZ() + 0.5, 80, 2, 0.5, 2, 0.2);
				if (Apocalypse.summon(world, Apocalypse.GREATER_BEAST, lava.up(), player) == null) {
					return null;
				}
			}
		}
		circle.clear();
		player.sendMessage(Text.translatable("message.evolvedwarfare.ritual." + NAMES[rite.ordinal()]).formatted(Formatting.LIGHT_PURPLE), false);
		world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, x, y, z, 40, 0.5, 0.1, 0.5, 0.02);
		world.playSound(null, pos, SoundEvents.BLOCK_RESPAWN_ANCHOR_CHARGE, SoundCategory.BLOCKS, 1.0F, 0.5F);
		return rite;
	}

	private static void give(ServerPlayerEntity player, ItemStack stack) {
		if (!player.giveItemStack(stack)) {
			player.dropItem(stack, false);
		}
	}

	@Nullable
	static BlockPos lavaNear(ServerWorld world, BlockPos pos, int r) {
		BlockPos best = null;
		double bd = Double.MAX_VALUE;
		for (BlockPos p : BlockPos.iterate(pos.add(-r, -4, -r), pos.add(r, 3, r))) {
			if (world.getFluidState(p).isIn(FluidTags.LAVA) && world.getBlockState(p.up()).isAir()) {
				double d = p.getSquaredDistance(pos);
				if (d < bd) {
					bd = d;
					best = p.toImmutable();
				}
			}
		}
		return best;
	}

	static SpawnReason reason() {
		return SpawnReason.EVENT;
	}
}
