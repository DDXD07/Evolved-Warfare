package com.evolvedwarfare.engineering;

import com.evolvedwarfare.EvolvedWarfare;
import com.evolvedwarfare.smithing.CrucibleBlockEntity;
import com.evolvedwarfare.smithing.CrucibleContents;
import com.evolvedwarfare.smithing.ForgeMaterial;
import com.evolvedwarfare.smithing.HotIngotItem;
import net.minecraft.block.BlockState;
import net.minecraft.component.ComponentType;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.AttributeModifierSlot;
import net.minecraft.component.type.AttributeModifiersComponent;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * La metalurgia (fase 77): las placas de metal (el martinete aplana los lingotes en el yunque), las anillas de hierro (para
 * la cota de malla) y el acero (hierro fundido en el crisol con carbón mientras sopla un fuelle). Las piezas forjadas de
 * acero dan las herramientas y armas de hierro mejoradas: +50 % de durabilidad, +1 de daño y +10 % al picar.
 */
public final class Metalwork {
	/** Golpes del martinete por placa. */
	public static final int PLATE_STRIKES = 4;
	public static final ComponentType<Boolean> STEEL = Registry.register(Registries.DATA_COMPONENT_TYPE, EvolvedWarfare.id("steel"),
			ComponentType.<Boolean>builder().codec(com.mojang.serialization.Codec.BOOL).packetCodec(net.minecraft.network.codec.PacketCodecs.BOOL).build());

	public static final Item STEEL_INGOT = item("steel_ingot", new Engineering.DescribedItem(new Item.Settings()));
	public static final Item HOT_STEEL_INGOT = item("hot_steel_ingot", new HotIngotItem(ForgeMaterial.STEEL, () -> Metalwork.STEEL_INGOT,
			new Item.Settings()));
	public static final Item IRON_PLATE = item("iron_plate", new Engineering.DescribedItem(new Item.Settings()));
	public static final Item COPPER_PLATE = item("copper_plate", new Engineering.DescribedItem(new Item.Settings()));
	public static final Item GOLD_PLATE = item("gold_plate", new Engineering.DescribedItem(new Item.Settings()));
	public static final Item STEEL_PLATE = item("steel_plate", new Engineering.DescribedItem(new Item.Settings()));
	public static final Item IRON_RINGS = item("iron_rings", new Engineering.DescribedItem(new Item.Settings()));

	// fase 78: armaduras de placas y escudos reforzados
	public static final java.util.List<Item> PLATE_ARMOR = new java.util.ArrayList<>();

	static {
		for (PlateArmorItem.Kind kind : PlateArmorItem.Kind.values()) {
			for (net.minecraft.item.ArmorItem.Type type : new net.minecraft.item.ArmorItem.Type[] {net.minecraft.item.ArmorItem.Type.HELMET,
					net.minecraft.item.ArmorItem.Type.CHESTPLATE, net.minecraft.item.ArmorItem.Type.LEGGINGS, net.minecraft.item.ArmorItem.Type.BOOTS}) {
				PLATE_ARMOR.add(item(kind.id + "_" + type.getName(), new PlateArmorItem(kind, type)));
			}
		}
	}

	public static final Item REINFORCED_SHIELD = item("reinforced_shield", new MetalShieldItem(false));
	public static final Item PAVISE = item("pavise", new MetalShieldItem(true));

	// fase 80: las herramientas de acero (se forjan; no se mejoran a netherita)
	public static final Item STEEL_SWORD = item("steel_sword", new net.minecraft.item.SwordItem(SteelTier.INSTANCE, new Item.Settings()
			.attributeModifiers(net.minecraft.item.SwordItem.createAttributeModifiers(SteelTier.INSTANCE, 3, -2.4F))));
	public static final Item STEEL_PICKAXE = item("steel_pickaxe", new net.minecraft.item.PickaxeItem(SteelTier.INSTANCE, new Item.Settings()
			.attributeModifiers(net.minecraft.item.MiningToolItem.createAttributeModifiers(SteelTier.INSTANCE, 1.0F, -2.8F))));
	public static final Item STEEL_AXE = item("steel_axe", new net.minecraft.item.AxeItem(SteelTier.INSTANCE, new Item.Settings()
			.attributeModifiers(net.minecraft.item.MiningToolItem.createAttributeModifiers(SteelTier.INSTANCE, 6.0F, -3.1F))));
	public static final Item STEEL_SHOVEL = item("steel_shovel", new net.minecraft.item.ShovelItem(SteelTier.INSTANCE, new Item.Settings()
			.attributeModifiers(net.minecraft.item.MiningToolItem.createAttributeModifiers(SteelTier.INSTANCE, 1.5F, -3.0F))));
	public static final Item STEEL_HOE = item("steel_hoe", new net.minecraft.item.HoeItem(SteelTier.INSTANCE, new Item.Settings()
			.attributeModifiers(net.minecraft.item.MiningToolItem.createAttributeModifiers(SteelTier.INSTANCE, -2.0F, -1.0F))));

	/** La herramienta de acero de ese tipo ("sword", "pickaxe"...), o null. */
	@Nullable
	public static Item steelTool(String kind) {
		return switch (kind) {
			case "sword" -> STEEL_SWORD;
			case "pickaxe" -> STEEL_PICKAXE;
			case "axe" -> STEEL_AXE;
			case "shovel" -> STEEL_SHOVEL;
			case "hoe" -> STEEL_HOE;
			default -> null;
		};
	}

	// fase 79: las ballestas mecánicas
	public static final Item ARBALEST = item("arbalest", new MechanicalCrossbowItem(false, new Item.Settings().maxCount(1).maxDamage(700)));
	public static final Item REPEATING_CROSSBOW = item("repeating_crossbow", new MechanicalCrossbowItem(true,
			new Item.Settings().maxCount(1).maxDamage(600)));

	private Metalwork() {
	}

	private static Item item(String name, Item item) {
		Engineering.ITEMS.add(Registry.register(Registries.ITEM, EvolvedWarfare.id(name), item));
		return item;
	}

	public static void register() {
		// carga la clase (los objetos se registran arriba)
		Sharpening.register();
	}

	/** La placa que sale de aplanar ese lingote, o null. */
	@Nullable
	public static Item plateOf(ItemStack stack) {
		if (stack.isOf(Items.IRON_INGOT)) {
			return IRON_PLATE;
		}
		if (stack.isOf(Items.COPPER_INGOT)) {
			return COPPER_PLATE;
		}
		if (stack.isOf(Items.GOLD_INGOT)) {
			return GOLD_PLATE;
		}
		if (stack.isOf(STEEL_INGOT)) {
			return STEEL_PLATE;
		}
		return null;
	}

	/** El lingote de un metal del crisol. */
	@Nullable
	public static Item ingotOf(String metal) {
		return switch (metal) {
			case "iron" -> Items.IRON_INGOT;
			case "gold" -> Items.GOLD_INGOT;
			case "steel" -> STEEL_INGOT;
			default -> null;
		};
	}

	/** ¿Sopla un fuelle en marcha al crisol (o a su fuego)? */
	public static boolean bellowsBlowing(World world, BlockPos crucible) {
		for (BlockPos target : new BlockPos[] {crucible, crucible.down()}) {
			for (Direction d : Direction.Type.HORIZONTAL) {
				BlockPos b = target.offset(d);
				BlockState s = world.getBlockState(b);
				if (s.getBlock() instanceof BellowsBlock && s.get(Properties.HORIZONTAL_FACING) == d.getOpposite()
						&& world.getBlockEntity(b) instanceof KineticBlockEntity be && Math.abs(be.speed()) > 0.01F) {
					return true;
				}
			}
		}
		return false;
	}

	/** Echa carbón al hierro fundido con el fuelle soplando: se hace acero (un carbón por lingote). */
	public static boolean carburize(ServerWorld world, BlockPos pos, ItemStack coal, @Nullable PlayerEntity player) {
		if (!(world.getBlockEntity(pos) instanceof CrucibleBlockEntity crucible)) {
			return false;
		}
		CrucibleContents c = crucible.contents();
		if (!"iron".equals(c.metal()) || c.solid() > 0 || c.molten() < 0.5F || !CrucibleBlockEntity.heatBelow(world, pos)) {
			tell(player, Text.translatable("message.evolvedwarfare.steel.need_molten"));
			return false;
		}
		if (!bellowsBlowing(world, pos)) {
			tell(player, Text.translatable("message.evolvedwarfare.steel.need_bellows"));
			return false;
		}
		int need = (int) Math.ceil(c.molten() - 0.01F);
		boolean creative = player != null && player.isCreative();
		if (!creative && coal.getCount() < need) {
			tell(player, Text.translatable("message.evolvedwarfare.steel.need_coal", need));
			return false;
		}
		if (!creative) {
			coal.decrement(need);
		}
		crucible.setContents(new CrucibleContents("steel", 0, c.molten(), 0L));
		world.playSound(null, pos, SoundEvents.BLOCK_BLASTFURNACE_FIRE_CRACKLE, SoundCategory.BLOCKS, 1.0F, 0.8F);
		world.playSound(null, pos, SoundEvents.ITEM_FIRECHARGE_USE, SoundCategory.BLOCKS, 0.6F, 1.2F);
		world.spawnParticles(ParticleTypes.FLAME, pos.getX() + 0.5, pos.getY() + 0.8, pos.getZ() + 0.5, 12, 0.2, 0.1, 0.2, 0.03);
		if (player != null) {
			player.sendMessage(Text.translatable("message.evolvedwarfare.steel.made", need).formatted(Formatting.AQUA), true);
		}
		return true;
	}

	/** Saca los lingotes del metal frío del crisol (agachado, con la mano vacía). */
	public static boolean takeIngots(World world, BlockPos pos, PlayerEntity player) {
		if (!(world.getBlockEntity(pos) instanceof CrucibleBlockEntity crucible)) {
			return false;
		}
		CrucibleContents c = crucible.contents();
		Item ingot = ingotOf(c.metal());
		if (ingot == null || c.solid() <= 0 || c.molten() > 0.01F) {
			return false;
		}
		player.getInventory().offerOrDrop(new ItemStack(ingot, c.solid()));
		crucible.setContents(CrucibleContents.EMPTY);
		world.playSound(null, pos, SoundEvents.BLOCK_DECORATED_POT_HIT, SoundCategory.BLOCKS, 0.8F, 1.2F);
		player.sendMessage(Text.translatable("message.evolvedwarfare.crucible.ingots", c.solid()).formatted(Formatting.GOLD), true);
		return true;
	}

	private static void tell(@Nullable PlayerEntity player, Text text) {
		if (player != null) {
			player.sendMessage(text.copy().formatted(Formatting.YELLOW), true);
		}
	}
}
