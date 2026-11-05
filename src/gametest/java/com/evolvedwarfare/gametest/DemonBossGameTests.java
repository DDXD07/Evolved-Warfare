package com.evolvedwarfare.gametest;

import com.evolvedwarfare.hell.AbaddonEntity;
import com.evolvedwarfare.hell.BeelzebubEntity;
import com.evolvedwarfare.hell.DemonEntity;
import com.evolvedwarfare.hell.DivineBlessing;
import com.evolvedwarfare.hell.LegionEntity;
import com.evolvedwarfare.hell.MolochEntity;
import com.evolvedwarfare.registry.ModEntities;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.SmallFireballEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.GameTestException;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.Box;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Los mini jefes demoníacos y la Bendición Divina (la única manera de herir a un demonio). */
public class DemonBossGameTests implements FabricGameTest {
	private static final Logger LOG = LoggerFactory.getLogger("ew-gametest");

	private static void check(boolean ok, String message) {
		if (!ok) {
			throw new GameTestException(message);
		}
	}

	private static ItemStack blessed(ServerWorld world, ItemStack stack) {
		RegistryEntry<Enchantment> entry = world.getRegistryManager().get(RegistryKeys.ENCHANTMENT).getEntry(DivineBlessing.KEY).orElseThrow();
		stack.addEnchantment(entry, 1);
		return stack;
	}

	@GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 100)
	public void onlyBlessedWeaponsHurtDemons(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		DemonEntity demon = ctx.spawnEntity(ModEntities.DEMON, 1, 2, 1);
		ZombieEntity zombie = ctx.spawnEntity(EntityType.ZOMBIE, 4, 2, 1);
		PlayerEntity player = ctx.createMockCreativeServerPlayerInWorld();
		player.equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.DIAMOND_SWORD));
		float before = demon.getHealth();
		boolean plain = demon.damage(world.getDamageSources().playerAttack(player), 8.0F);
		boolean fire = demon.damage(world.getDamageSources().explosion(null, null), 8.0F);
		boolean zombieHurt = zombie.damage(world.getDamageSources().playerAttack(player), 4.0F);
		float afterPlain = demon.getHealth();
		player.equipStack(EquipmentSlot.MAINHAND, blessed(world, new ItemStack(Items.DIAMOND_SWORD)));
		boolean holy = demon.damage(world.getDamageSources().playerAttack(player), 8.0F);
		float afterHoly = demon.getHealth();
		LOG.info("[EW-GAMETEST] Bendición Divina: espada normal {} ({} -> {}), explosión {}, zombi herido {}, espada bendita {} (-> {})", plain,
				before, afterPlain, fire, zombieHurt, holy, afterHoly);
		check(!plain && !fire && afterPlain == before, "Un arma sin bendecir hiere al demonio");
		check(zombieHurt, "Los que no son demonios dejan de recibir daño");
		check(holy && afterHoly < before, "La espada bendita no hiere al demonio");
		demon.discard();
		zombie.discard();
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 100)
	public void miniBosses(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		LegionEntity legion = ctx.spawnEntity(ModEntities.LEGION, 1, 2, 1);
		BeelzebubEntity beelzebub = ctx.spawnEntity(ModEntities.BEELZEBUB, 4, 3, 1);
		MolochEntity moloch = ctx.spawnEntity(ModEntities.MOLOCH, 1, 2, 6);
		AbaddonEntity abaddon = ctx.spawnEntity(ModEntities.ABADDON, 6, 2, 6);
		ArmorStandEntity dummy = ctx.spawnEntity(EntityType.ARMOR_STAND, 9, 2, 9);
		int imps = legion.split(world);
		int fire = moloch.breathe(world, dummy);
		int servants = abaddon.summon(world);
		boolean tiers = legion.tier() == 1 && beelzebub.tier() == 2 && moloch.tier() == 3 && abaddon.tier() == 4;
		boolean bigger = legion.getHeight() < beelzebub.getHeight() && beelzebub.getHeight() < moloch.getHeight() && moloch.getHeight() < abaddon.getHeight();
		boolean tougher = legion.getMaxHealth() < beelzebub.getMaxHealth() && beelzebub.getMaxHealth() < moloch.getMaxHealth()
				&& moloch.getMaxHealth() < abaddon.getMaxHealth();
		LOG.info("[EW-GAMETEST] mini jefes: grados {}, cada uno más grande {} y más duro {} (vida {} / {} / {} / {}); Legión suelta {} diablillos, Moloc "
				+ "escupe {} bolas, Abadón llama a {} siervos; Belcebú vuela {}", tiers, bigger, tougher, legion.getMaxHealth(), beelzebub.getMaxHealth(),
				moloch.getMaxHealth(), abaddon.getMaxHealth(), imps, fire, servants, beelzebub.hasNoGravity());
		check(tiers && bigger && tougher, "Los mini jefes no van de menos a más");
		check(imps == 2 && fire == 5 && servants == 3 && beelzebub.hasNoGravity(), "Los poderes de los mini jefes no van bien");
		Box area = new Box(ctx.getAbsolutePos(net.minecraft.util.math.BlockPos.ORIGIN)).expand(24);
		world.getEntitiesByClass(net.minecraft.entity.LivingEntity.class, area, e -> DivineBlessing.isDemon(e)).forEach(e -> e.discard());
		world.getEntitiesByClass(SmallFireballEntity.class, area, e -> true).forEach(e -> e.discard());
		dummy.discard();
		ctx.complete();
	}
}
