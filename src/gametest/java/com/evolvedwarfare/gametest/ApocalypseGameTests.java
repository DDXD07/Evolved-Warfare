package com.evolvedwarfare.gametest;

import com.evolvedwarfare.hell.AbyssAngelEntity;
import com.evolvedwarfare.hell.Apocalypse;
import com.evolvedwarfare.hell.ApocalypseItems;
import com.evolvedwarfare.hell.Curses;
import com.evolvedwarfare.hell.FalseProphetEntity;
import com.evolvedwarfare.hell.GreaterBeastEntity;
import com.evolvedwarfare.hell.HellStructures;
import com.evolvedwarfare.hell.RitualCircleBlockEntity;
import com.evolvedwarfare.hell.Rituals;
import com.evolvedwarfare.hell.SealFragmentItem;
import com.evolvedwarfare.registry.ModEffects;
import com.evolvedwarfare.registry.ModItems;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.loot.LootTable;
import net.minecraft.loot.context.LootContextParameterSet;
import net.minecraft.loot.context.LootContextParameters;
import net.minecraft.loot.context.LootContextTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.GameTestException;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** El Apocalipsis (fase 36): los mini jefes, los ritos, los sellos y sus objetos. */
public class ApocalypseGameTests implements FabricGameTest {
	private static final Logger LOG = LoggerFactory.getLogger("ew-gametest");

	private static void check(boolean ok, String message) {
		if (!ok) {
			throw new GameTestException(message);
		}
	}

	private static ServerPlayerEntity survivor(TestContext ctx) {
		ServerPlayerEntity player = (ServerPlayerEntity) ctx.createMockCreativeServerPlayerInWorld();
		player.changeGameMode(GameMode.SURVIVAL);
		player.getInventory().clear();
		Vec3d at = ctx.getAbsolute(new Vec3d(1.5, 2, 1.5));
		player.refreshPositionAndAngles(at.x, at.y, at.z, 0, 0);
		return player;
	}

	private static RitualCircleBlockEntity circle(TestContext ctx, BlockPos rel) {
		BlockPos pos = ctx.getAbsolutePos(rel);
		ctx.getWorld().setBlockState(pos, Apocalypse.RITUAL_CIRCLE.getDefaultState());
		return (RitualCircleBlockEntity) ctx.getWorld().getBlockEntity(pos);
	}

	@GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 60)
	public void miniBossesStand(TestContext ctx) {
		AbyssAngelEntity angel = ctx.spawnEntity(Apocalypse.ABYSS_ANGEL, 2, 3, 2);
		FalseProphetEntity prophet = ctx.spawnEntity(Apocalypse.FALSE_PROPHET, 5, 2, 2);
		GreaterBeastEntity beast = ctx.spawnEntity(Apocalypse.GREATER_BEAST, 4, 2, 5);
		ctx.runAtTick(20, () -> {
			LOG.info("[EW-GAMETEST] mini jefes: ángel {} ({}), profeta {}, bestia {} con {} cabezas, ignífuga {}", angel.getHealth(), angel.hasNoGravity(),
					prophet.getHealth(), beast.getHealth(), beast.heads(), beast.isFireImmune());
			check(angel.isAlive() && prophet.isAlive() && beast.isAlive() && angel.getMaxHealth() >= 300 && beast.heads() == GreaterBeastEntity.HEADS
					&& beast.isFireImmune() && angel.hasNoGravity() && beast.isPersistent(), "Los mini jefes no aparecen bien");
			angel.discard();
			prophet.discard();
			beast.discard();
			ctx.complete();
		});
	}

	@GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 40)
	public void beastLosesHeadsAndHeals(TestContext ctx) {
		GreaterBeastEntity beast = ctx.spawnEntity(Apocalypse.GREATER_BEAST, 4, 2, 4);
		beast.setAiDisabled(true);
		beast.setHealth(beast.getMaxHealth() * 0.5F);
		beast.updateHeads();
		int half = beast.heads();
		beast.setHealth(beast.getMaxHealth() * 0.1F);
		beast.updateHeads();
		int after = beast.heads();
		float healthAfter = beast.getHealth() / beast.getMaxHealth();
		LOG.info("[EW-GAMETEST] la Bestia: a media vida {} cabezas, al final {} (sanada {}, vida {})", half, after, beast.healed(), healthAfter);
		check(half == 4 && beast.healed() && after == 3 && healthAfter > 0.35F, "La Bestia no pierde cabezas o no sana");
		beast.discard();
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 40)
	public void prophetConvertsAndDeceives(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		ServerPlayerEntity player = survivor(ctx);
		FalseProphetEntity prophet = ctx.spawnEntity(Apocalypse.FALSE_PROPHET, 4, 2, 4);
		prophet.setAiDisabled(true);
		ZombieEntity zombie = ctx.spawnEntity(EntityType.ZOMBIE, 6, 2, 4);
		zombie.setAiDisabled(true);
		int converted = prophet.convert(world, 10, player);
		int illusions = prophet.illusions(world, player, 2);
		long copies = world.getEntitiesByClass(FalseProphetEntity.class, new Box(prophet.getBlockPos()).expand(10), FalseProphetEntity::illusion).size();
		LOG.info("[EW-GAMETEST] el profeta: {} seguidores (fuerza {}), {} ilusiones ({} en el mundo)", converted, zombie.hasStatusEffect(StatusEffects.STRENGTH),
				illusions, copies);
		check(converted == 1 && zombie.getCommandTags().contains(FalseProphetEntity.FOLLOWER) && zombie.hasStatusEffect(StatusEffects.STRENGTH)
				&& illusions == 2 && copies == 2, "El Falso Profeta no convierte o no hace ilusiones");
		world.getEntitiesByClass(FalseProphetEntity.class, new Box(prophet.getBlockPos()).expand(10), e -> true).forEach(e -> e.discard());
		zombie.discard();
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void purificationWards(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		ServerPlayerEntity player = survivor(ctx);
		Curses.curse(player, ModEffects.FEVER);
		boolean cursed = player.hasStatusEffect(ModEffects.FEVER);
		RitualCircleBlockEntity c = circle(ctx, new BlockPos(2, 2, 2));
		c.add(new ItemStack(Items.GLOWSTONE_DUST));
		c.add(new ItemStack(ModItems.BLESSED_SALT));
		c.add(new ItemStack(Items.GOLD_INGOT));
		Rituals.Rite rite = Rituals.perform(world, c.getPos(), player, c);
		boolean warded = player.hasStatusEffect(Apocalypse.BLESSED_WARD);
		boolean blocked = !Curses.curse(player, ModEffects.SALT_THIRST);
		LOG.info("[EW-GAMETEST] purificación: maldito {}, rito {}, protegido {}, maldición bloqueada {}, círculo vacío {}", cursed, rite, warded, blocked,
				c.count() == 0);
		check(cursed && rite == Rituals.Rite.PURIFICATION && !player.hasStatusEffect(ModEffects.FEVER) && warded && blocked && c.count() == 0,
				"El ritual de purificación no funciona");
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void sealsMakeTheKey(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		ServerPlayerEntity player = survivor(ctx);
		RitualCircleBlockEntity c = circle(ctx, new BlockPos(2, 2, 2));
		for (int k = 0; k < 6; k++) {
			c.add(SealFragmentItem.of(k));
		}
		c.add(SealFragmentItem.of(0));
		Rituals.Rite wrong = Rituals.perform(world, c.getPos(), player, c);
		c.pop();
		c.add(SealFragmentItem.of(6));
		Rituals.Rite right = Rituals.perform(world, c.getPos(), player, c);
		boolean key = player.getInventory().contains(new ItemStack(Apocalypse.ABYSS_KEY));
		boolean names = !SealFragmentItem.of(2).getName().getString().equals(SealFragmentItem.of(5).getName().getString());
		LOG.info("[EW-GAMETEST] sellos: repetido {}, los siete {}, llave {}, nombres distintos {}", wrong, right, key, names);
		check(wrong == null && right == Rituals.Rite.SEALS && key && names, "Los siete sellos no hacen la llave");
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "abyss_key", tickLimit = 40)
	public void keyWakesTheAngel(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		ServerPlayerEntity player = survivor(ctx);
		BlockPos throne = ctx.getAbsolutePos(new BlockPos(1, 2, 1));
		world.setBlockState(throne, HellStructures.ABYSS_THRONE.getDefaultState());
		for (int i = 0; i < HellStructures.THRONE_DEATHS; i++) {
			HellStructures.addNetherDeath(player);
		}
		player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Apocalypse.ABYSS_KEY));
		HellStructures.useThrone(world, player, throne);
		var angels = world.getEntitiesByClass(AbyssAngelEntity.class, new Box(throne).expand(20), e -> true);
		LOG.info("[EW-GAMETEST] la llave: {} ángeles, llave gastada {}", angels.size(), player.getMainHandStack().isEmpty());
		check(angels.size() == 1 && player.getMainHandStack().isEmpty() && angels.get(0).getTarget() == player, "La llave no despierta al Ángel");
		angels.forEach(a -> a.discard());
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 40)
	public void relicsWork(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		ServerPlayerEntity player = survivor(ctx);
		var pig = ctx.spawnEntity(EntityType.PIG, 4, 2, 4);
		float before = pig.getHealth();
		int burned = Apocalypse.fireFromHeaven(world, pig.getBlockPos(), player, 6.0F);
		ZombieEntity zombie = ctx.spawnEntity(EntityType.ZOMBIE, 2, 2, 5);
		zombie.setTarget(player);
		int feared = ApocalypseItems.BeastHorn.fear(world, player, 20);
		LOG.info("[EW-GAMETEST] reliquias: fuego del cielo {} (vida {} -> {}), cuerno {} (blanco {})", burned, before, pig.getHealth(), feared,
				zombie.getTarget());
		check(burned >= 1 && pig.getHealth() < before && feared >= 1 && zombie.getTarget() == null && zombie.hasStatusEffect(StatusEffects.WEAKNESS),
				"El fuego del cielo o el cuerno no funcionan");
		pig.discard();
		zombie.discard();
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void darkPactAndFragmentsInChests(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		ServerPlayerEntity player = survivor(ctx);
		player.addStatusEffect(new StatusEffectInstance(Apocalypse.DARK_PACT, 200, 0));
		double damage = player.getAttributeValue(net.minecraft.entity.attribute.EntityAttributes.GENERIC_ATTACK_DAMAGE);
		LootTable table = world.getServer().getReloadableRegistries().getLootTable(Apocalypse.loot("chests/babel_top"));
		LootContextParameterSet params = new LootContextParameterSet.Builder(world).add(LootContextParameters.ORIGIN, ctx.getAbsolute(new Vec3d(1, 1, 1)))
				.build(LootContextTypes.CHEST);
		int seals = 0;
		for (ItemStack s : table.generateLoot(params)) {
			seals += s.isOf(Apocalypse.SEAL_FRAGMENT) ? 1 : 0;
		}
		LOG.info("[EW-GAMETEST] pacto oscuro: daño {}; fragmentos en la cima de Babel {}", damage, seals);
		check(damage >= 4.0 && seals >= 1, "El pacto oscuro o los fragmentos de sello no van");
		ctx.complete();
	}
}
