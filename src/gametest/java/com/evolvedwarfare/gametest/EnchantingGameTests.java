package com.evolvedwarfare.gametest;

import com.evolvedwarfare.config.EwConfig;
import com.evolvedwarfare.difficulty.Enchanting;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.screen.AnvilScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.GameTestException;
import net.minecraft.test.TestContext;
import net.minecraft.world.GameMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Encantar más difícil (fase 100): la mesa, la maldición, el yunque y la reparación. */
public class EnchantingGameTests implements FabricGameTest {
	private static final Logger LOG = LoggerFactory.getLogger("ew-gametest");

	private static void check(boolean ok, String message) {
		if (!ok) {
			throw new GameTestException(message);
		}
	}

	private static ServerPlayerEntity player(TestContext ctx) {
		ServerPlayerEntity player = (ServerPlayerEntity) ctx.createMockCreativeServerPlayerInWorld();
		player.changeGameMode(GameMode.SURVIVAL);
		player.getInventory().clear();
		return player;
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "enchanting")
	public void tableCostsMoreAndCurses(TestContext ctx) {
		ServerPlayerEntity player = player(ctx);
		player.experienceLevel = 30;
		ItemStack sword = new ItemStack(Items.IRON_SWORD);
		ItemStack lapis = new ItemStack(Items.LAPIS_LAZULI, 3);
		EwConfig c = EwConfig.get();
		double was = c.enchantCurseChance;
		c.enchantCurseChance = 0.0;
		try {
			Enchanting.afterTableEnchant(player, sword, lapis, 2);
		} finally {
			c.enchantCurseChance = was;
		}
		LOG.info("[EW-GAMETEST] mesa: niveles 30 -> {} (más la opción), lapislázuli 3 -> {}", player.experienceLevel, lapis.getCount());
		check(player.experienceLevel == 24, "La tercera opción no cuesta 6 niveles más: " + player.experienceLevel);
		check(lapis.isEmpty(), "No cuesta el doble de lapislázuli: " + lapis.getCount());
		boolean cursed = Enchanting.curse(player, sword);
		var vanishing = ctx.getWorld().getRegistryManager().get(RegistryKeys.ENCHANTMENT).getEntry(Enchantments.VANISHING_CURSE).orElseThrow();
		check(cursed && EnchantmentHelper.getLevel(vanishing, sword) == 1, "No se maldice");
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "enchanting")
	public void anvilCostsMore(TestContext ctx) {
		ServerPlayerEntity player = player(ctx);
		player.experienceLevel = 100;
		AnvilScreenHandler anvil = new AnvilScreenHandler(1, player.getInventory());
		ItemStack sword = new ItemStack(Items.IRON_SWORD);
		sword.setDamage(200);
		EwConfig c = EwConfig.get();
		boolean was = c.hardEnchanting;
		int normal, harder;
		try {
			c.hardEnchanting = false;
			anvil.getSlot(0).setStack(sword);
			anvil.getSlot(1).setStack(new ItemStack(Items.IRON_INGOT, 2));
			anvil.updateResult();
			normal = anvil.getLevelCost();
			c.hardEnchanting = true;
			anvil.updateResult();
			harder = anvil.getLevelCost();
		} finally {
			c.hardEnchanting = was;
		}
		LOG.info("[EW-GAMETEST] yunque: {} -> {} niveles", normal, harder);
		check(normal > 0 && harder > normal, "El yunque no cuesta más");
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "enchanting")
	public void mendingRepairsLess(TestContext ctx) {
		ItemStack sword = new ItemStack(Items.IRON_SWORD);
		var mending = ctx.getWorld().getRegistryManager().get(RegistryKeys.ENCHANTMENT).getEntry(Enchantments.MENDING).orElseThrow();
		sword.addEnchantment(mending, 1);
		int repair = EnchantmentHelper.getRepairWithXp(ctx.getWorld(), sword, 10);
		LOG.info("[EW-GAMETEST] reparación: 10 de experiencia reparan {}", repair);
		check(repair == 10, "La reparación no repara la mitad: " + repair);
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "enchanting")
	public void mendingBooksAreRare(TestContext ctx) {
		var registries = ctx.getWorld().getRegistryManager();
		var enchantments = registries.get(RegistryKeys.ENCHANTMENT);
		ItemStack mendingBook = com.evolvedwarfare.difficulty.MendingRarity.mendingBook(registries, 1);
		ItemStack sharpBook = EnchantmentHelper.getEnchantedBookWith(new net.minecraft.enchantment.EnchantmentLevelEntry(
				enchantments.getEntry(Enchantments.SHARPNESS).orElseThrow(), 3));
		EwConfig c = EwConfig.get();
		int was = c.mendingBookChance;
		try {
			c.mendingBookChance = 1_000_000;
			ItemStack rerolled = com.evolvedwarfare.difficulty.MendingRarity.filter(mendingBook.copy(), new ItemStack(Items.BOOK),
					net.minecraft.util.math.random.Random.create(7), registries, original -> sharpBook.copy());
			ItemStack stripped = com.evolvedwarfare.difficulty.MendingRarity.filter(mendingBook.copy(), new ItemStack(Items.BOOK),
					net.minecraft.util.math.random.Random.create(7), registries, original -> mendingBook.copy());
			c.mendingBookChance = 1;
			ItemStack lucky = com.evolvedwarfare.difficulty.MendingRarity.filter(sharpBook.copy(), new ItemStack(Items.BOOK),
					net.minecraft.util.math.random.Random.create(7), registries, original -> sharpBook.copy());
			LOG.info("[EW-GAMETEST] reparación rara: se vuelve a tirar {}, se le quita {}, con suerte {}",
					com.evolvedwarfare.difficulty.MendingRarity.hasMending(rerolled), com.evolvedwarfare.difficulty.MendingRarity.hasMending(stripped),
					com.evolvedwarfare.difficulty.MendingRarity.hasMending(lucky));
			check(!com.evolvedwarfare.difficulty.MendingRarity.hasMending(rerolled), "El libro al azar sigue trayendo reparación");
			check(!com.evolvedwarfare.difficulty.MendingRarity.hasMending(stripped), "No se le quita la reparación");
			check(com.evolvedwarfare.difficulty.MendingRarity.hasMending(lucky), "Nunca sale el libro de reparación");
		} finally {
			c.mendingBookChance = was;
		}
		ctx.complete();
	}
}
