package com.evolvedwarfare.gametest;

import com.evolvedwarfare.armor.ArmorClass;
import com.evolvedwarfare.armor.ArmorTier;
import com.evolvedwarfare.armor.ClassArmorItem;
import com.evolvedwarfare.armor.FactionArmor;
import com.evolvedwarfare.registry.ModItems;
import com.evolvedwarfare.weapon.MusketItem;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.AttributeModifiersComponent;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.GameTestException;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.Vec3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Armaduras de clase: 80 piezas con receta (la de netherita en la mesa de herrería), cada material protege más, el
 * ladrón y el mosquetero pierden 1 de armadura por pieza, los números van ocultos en la descripción, y las ventajas
 * suben con el material y se notan (recarga del mosquete, velocidad, daño por la espalda).
 */
public class ClassArmorGameTests implements FabricGameTest {
	private static final Logger LOG = LoggerFactory.getLogger("ew-gametest");

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void classArmorItems(TestContext ctx) {
		int count = 0;
		for (Map.Entry<ArmorClass, Map<ArmorTier, List<Item>>> entry : ModItems.CLASS_ARMOR.entrySet()) {
			int previous = -1;
			for (ArmorTier tier : ArmorTier.values()) {
				int total = 0;
				for (Item item : entry.getValue().get(tier)) {
					count++;
					ClassArmorItem armor = (ClassArmorItem) item;
					total += ClassArmorItem.protection(armor.armorClass(), tier, armor.getType());
					ItemStack stack = new ItemStack(item);
					AttributeModifiersComponent mods = stack.get(DataComponentTypes.ATTRIBUTE_MODIFIERS);
					check(mods != null && !mods.showInTooltip() && !mods.modifiers().isEmpty(), "Los números de " + item + " se ven sin la tecla");
					check(ctx.getWorld().getRecipeManager().get(Registries.ITEM.getId(item)).isPresent(), "Sin receta: " + item);
				}
				check(total >= previous, entry.getKey() + " " + tier + " protege menos que el material anterior");
				previous = total;
			}
		}
		int thief = 0;
		int warrior = 0;
		for (ArmorItem.Type type : FactionArmor.TYPES) {
			thief += ClassArmorItem.protection(ArmorClass.THIEF, ArmorTier.IRON, type);
			warrior += ClassArmorItem.protection(ArmorClass.WARRIOR, ArmorTier.IRON, type);
		}
		LOG.info("[EW-GAMETEST] armaduras de clase: {} piezas; hierro: guerrero {} de armadura, ladrón {}", count, warrior, thief);
		check(count == 80, "No hay 80 piezas: " + count);
		check(warrior - thief == 4, "El ladrón no pierde 4 de armadura");
		check(ArmorClass.ARCHER.setPercent(ArmorClass.Effect.DRAW_SPEED, ArmorTier.LEATHER) == 5
				&& ArmorClass.ARCHER.setPercent(ArmorClass.Effect.DRAW_SPEED, ArmorTier.NETHERITE) == 25, "La ventaja no sube un 5 % por material");
		ctx.complete();
	}

	/** Con el conjunto de mosquetero de diamante se recarga un 20 % antes; con medio conjunto, un 10 %. */
	@GameTest(templateName = EMPTY_STRUCTURE)
	public void musketeerReloadsFaster(TestContext ctx) {
		ServerPlayerEntity player = ctx.createMockCreativeServerPlayerInWorld();
		int before = MusketItem.reloadTicks(player);
		dress(player, ArmorClass.MUSKETEER, ArmorTier.DIAMOND, 2);
		int half = MusketItem.reloadTicks(player);
		dress(player, ArmorClass.MUSKETEER, ArmorTier.DIAMOND, 4);
		int full = MusketItem.reloadTicks(player);
		float draw = ClassArmorItem.bonus(player, ArmorClass.Effect.DRAW_SPEED);
		LOG.info("[EW-GAMETEST] mosquetero de diamante: recarga {} -> {} (medio) -> {} (entero) tics", before, half, full);
		check(full == Math.round(before * 0.8F) && half == Math.round(before * 0.9F), "La recarga no baja un 10/20 %");
		check(draw == 0.0F, "El mosquetero tensa más rápido los arcos");
		ctx.complete();
	}

	/** El guerrero va más lento y el ladrón más rápido; el ladrón pega más por la espalda. */
	@GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 40)
	public void classArmorEffects(TestContext ctx) {
		for (int x = 0; x < 6; x++) {
			for (int z = 0; z < 6; z++) {
				ctx.setBlockState(x, 0, z, Blocks.STONE);
			}
		}
		ZombieEntity warrior = ctx.spawnMob(EntityType.ZOMBIE, 1, 1, 1);
		ZombieEntity thief = ctx.spawnMob(EntityType.ZOMBIE, 3, 1, 1);
		ZombieEntity plain = ctx.spawnMob(EntityType.ZOMBIE, 5, 1, 1);
		for (ZombieEntity z : List.of(warrior, thief, plain)) {
			z.setAiDisabled(true);
		}
		dress(warrior, ArmorClass.WARRIOR, ArmorTier.IRON, 4);
		dress(thief, ArmorClass.THIEF, ArmorTier.DIAMOND, 4);
		ctx.waitAndRun(3, () -> {
			double base = plain.getAttributeValue(EntityAttributes.GENERIC_MOVEMENT_SPEED);
			double slow = warrior.getAttributeValue(EntityAttributes.GENERIC_MOVEMENT_SPEED);
			double fast = thief.getAttributeValue(EntityAttributes.GENERIC_MOVEMENT_SPEED);
			double knock = warrior.getAttributeValue(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE);
			// el ladrón detrás del zombi normal (que mira al sur) y luego delante
			plain.setBodyYaw(0.0F);
			Vec3d at = plain.getPos();
			thief.refreshPositionAndAngles(at.x, at.y, at.z - 2.0, 0.0F, 0.0F);
			float back = ClassArmorItem.modifyDamage(plain, thief.getDamageSources().mobAttack(thief), 10.0F);
			thief.refreshPositionAndAngles(at.x, at.y, at.z + 2.0, 180.0F, 0.0F);
			float front = ClassArmorItem.modifyDamage(plain, thief.getDamageSources().mobAttack(thief), 10.0F);
			LOG.info("[EW-GAMETEST] armaduras de clase: velocidad {} (guerrero {}, ladrón {}), empuje guerrero {}, golpe del ladrón por detrás {} / de frente {}",
					base, slow, fast, knock, back, front);
			for (ZombieEntity z : List.of(warrior, thief, plain)) {
				z.discard();
			}
			check(slow < base * 0.95 && fast > base * 1.1, "La velocidad no cambia con el guerrero y el ladrón");
			check(knock >= 0.14, "El guerrero de hierro no resiste el empuje");
			check(Math.abs(back - 12.4F) < 0.01F && front == 10.0F, "El ladrón no pega un 24 % más por la espalda");
			ctx.complete();
		});
	}

	private static void dress(net.minecraft.entity.LivingEntity entity, ArmorClass armorClass, ArmorTier tier, int pieces) {
		for (int i = 0; i < pieces; i++) {
			ArmorItem.Type type = FactionArmor.TYPES[i];
			entity.equipStack(type.getEquipmentSlot(), new ItemStack(ModItems.classArmor(armorClass, tier, type)));
		}
	}

	private static void check(boolean ok, String message) {
		if (!ok) {
			throw new GameTestException(message);
		}
	}
}
