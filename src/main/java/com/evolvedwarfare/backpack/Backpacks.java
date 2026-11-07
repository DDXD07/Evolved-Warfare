package com.evolvedwarfare.backpack;

import com.evolvedwarfare.EvolvedWarfare;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.MapColor;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.block.piston.PistonBehavior;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.DyedColorComponent;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.Rarity;
import net.minecraft.world.GameRules;

/**
 * El inventario nuevo (fase 38): el jugador (en supervivencia) solo tiene 5 huecos, que son la barra rápida; todo lo demás
 * va en la mochila que lleve a la espalda (cinco tiers: 10, 18, 27, 36 y 54 huecos). En creativo, todo como siempre.
 *
 * Aquí: las mochilas (objetos, bloque, pantalla), lo que lleva puesto cada jugador (y lo que pesa), la tecla para abrirla,
 * y lo que se hace con lo que quede en los huecos cerrados (va a la mochila o se cae).
 */
public final class Backpacks {
	/** Los huecos de la barra rápida (y de todo el inventario sin mochila). */
	public static final int HOTBAR = 6;
	/** Dónde van en la pantalla del inventario: la barra, y la primera fila de la mochila. */
	public static final int HOTBAR_Y = 88, PACK_Y = 112;
	public static final int[] SIZES = {10, 18, 27, 36, 54};
	public static final String[] TIERS = {"basic", "traveler", "explorer", "adventurer", "abyssal"};
	public static final int[] COLORS = {0x8B5A2B, 0x7A5634, 0x6E4A2A, 0x5A3A26, 0x3A3436};
	private static final Identifier WEIGHT = EvolvedWarfare.id("backpack_weight");

	/** La mochila que lleva a la espalda (con todo lo de dentro en su componente). Se ve en todos (se sincroniza). */
	public static final AttachmentType<ItemStack> WORN = AttachmentRegistry.<ItemStack>builder().persistent(ItemStack.OPTIONAL_CODEC)
			.syncWith(ItemStack.OPTIONAL_PACKET_CODEC, AttachmentSyncPredicate.all()).copyOnDeath().initializer(() -> ItemStack.EMPTY)
			.buildAndRegister(EvolvedWarfare.id("backpack"));

	public static final List<Item> ITEMS = new ArrayList<>();
	public static final Item[] BACKPACKS = new Item[SIZES.length];

	static {
		for (int t = 0; t < SIZES.length; t++) {
			Item.Settings settings = new Item.Settings().maxCount(1).component(DataComponentTypes.DYED_COLOR, new DyedColorComponent(COLORS[t], true));
			if (t == 4) {
				settings = settings.fireproof().rarity(Rarity.EPIC);
			} else if (t == 3) {
				settings = settings.rarity(Rarity.RARE);
			} else if (t == 2) {
				settings = settings.rarity(Rarity.UNCOMMON);
			}
			BACKPACKS[t] = Registry.register(Registries.ITEM, EvolvedWarfare.id("backpack_" + TIERS[t]), new BackpackItem(t, settings));
			ITEMS.add(BACKPACKS[t]);
		}
	}

	public static final Block BACKPACK_BLOCK = Registry.register(Registries.BLOCK, EvolvedWarfare.id("backpack"), new BackpackBlock(
			AbstractBlock.Settings.create().mapColor(MapColor.BROWN).strength(0.6F).nonOpaque().sounds(BlockSoundGroup.WOOL)
					.pistonBehavior(PistonBehavior.DESTROY)));
	public static final BlockEntityType<BackpackBlockEntity> BACKPACK_ENTITY = Registry.register(Registries.BLOCK_ENTITY_TYPE,
			EvolvedWarfare.id("backpack"), BlockEntityType.Builder.create(BackpackBlockEntity::new, BACKPACK_BLOCK).build(null));
	public static final ScreenHandlerType<BackpackScreenHandler> SCREEN = Registry.register(Registries.SCREEN_HANDLER, EvolvedWarfare.id("backpack"),
			new ExtendedScreenHandlerType<>((syncId, inv, slots) -> new BackpackScreenHandler(syncId, inv, new SimpleInventory(BackpackInventory.MAX), slots),
					PacketCodecs.VAR_INT));

	/** La tecla de la mochila (B): abre la que llevas puesta. */
	public record OpenPayload() implements CustomPayload {
		public static final Id<OpenPayload> ID = new Id<>(EvolvedWarfare.id("open_backpack"));
		public static final PacketCodec<RegistryByteBuf, OpenPayload> CODEC = PacketCodec.unit(new OpenPayload());

		@Override
		public Id<? extends CustomPayload> getId() {
			return ID;
		}
	}

	private static final Map<PlayerEntity, BackpackInventory> INVENTORIES = Collections.synchronizedMap(new WeakHashMap<>());

	private Backpacks() {
	}

	public static void register() {
		PayloadTypeRegistry.playC2S().register(OpenPayload.ID, OpenPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(OpenPayload.ID, (payload, context) -> open(context.player()));
		ServerTickEvents.END_SERVER_TICK.register(Backpacks::tick);
		Sorting.register();
		com.evolvedwarfare.pickup.Pickup.register();
		PackActions.register();
		ServerTickEvents.END_SERVER_TICK.register(PackActions::tick);
	}

	// ------------------------------------------------------------------ quién va limitado y qué lleva

	/** ¿Va con el inventario de 5 (supervivencia o aventura)? En creativo y espectador, como siempre. */
	public static boolean limited(PlayerEntity player) {
		if (player.isSpectator()) {
			return false;
		}
		if (player instanceof ServerPlayerEntity sp) {
			return !sp.interactionManager.getGameMode().isCreative();
		}
		return !player.getAbilities().creativeMode;
	}

	public static ItemStack worn(PlayerEntity player) {
		ItemStack s = player.getAttached(WORN);
		return s == null ? ItemStack.EMPTY : s;
	}

	/** Pone (o quita, con EMPTY) la mochila de la espalda, guardando antes lo de la que llevaba. */
	public static void setWorn(PlayerEntity player, ItemStack stack) {
		inventory(player).flush();
		setWornRaw(player, stack);
	}

	static void setWornRaw(PlayerEntity player, ItemStack stack) {
		if (stack.isEmpty()) {
			player.removeAttached(WORN);
		} else {
			player.setAttached(WORN, stack);
		}
	}

	public static BackpackInventory inventory(PlayerEntity player) {
		return INVENTORIES.computeIfAbsent(player, BackpackInventory::new);
	}

	/** Cuántos huecos tiene la mochila que lleva (0 sin mochila). */
	public static int size(PlayerEntity player) {
		return BackpackItem.slots(worn(player));
	}

	/** Mete en la mochila lo que no ha cabido en la barra; devuelve si ha entrado algo. */
	public static boolean insert(PlayerEntity player, ItemStack stack) {
		if (stack.getItem() instanceof BackpackItem || size(player) == 0) {
			return false;
		}
		return inventory(player).insert(stack);
	}

	// ------------------------------------------------------------------ cada tick

	private static void tick(MinecraftServer server) {
		boolean slow = server.getTicks() % 10 == 0;
		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			if (server.getTicks() % 5 == 0) {
				Loadout.show(player);
			}
			if (!limited(player)) {
				removeWeight(player);
				continue;
			}
			// la barra rápida es de 5
			PlayerInventory inv = player.getInventory();
			if (inv.selectedSlot >= HOTBAR) {
				inv.selectedSlot = HOTBAR - 1;
			}
			int heavy = heavyUsed(player);
			if (player.isSprinting() && heavy > 0) {
				player.addExhaustion(0.006F * heavy);
			}
			if (slow) {
				tidy(player);
				Loadout.fix(player);
				weigh(player, heavy);
			}
		}
	}

	/** Lo que quede en los huecos cerrados (de antes, o al salir de creativo) va a la mochila o se cae al suelo. */
	public static int tidy(PlayerEntity player) {
		PlayerInventory inv = player.getInventory();
		int moved = 0;
		for (int i = HOTBAR; i < inv.main.size(); i++) {
			ItemStack s = inv.main.get(i);
			if (s.isEmpty()) {
				continue;
			}
			inv.main.set(i, ItemStack.EMPTY);
			insert(player, s);
			if (!s.isEmpty()) {
				player.dropItem(s, false, true);
			}
			moved++;
		}
		return moved;
	}

	/** Huecos de carga pesada de una mochila de n huecos (los últimos): 2, 3, 5, 7 y 10. */
	public static int heavySlots(int slots) {
		return slots == 0 ? 0 : Math.max(2, slots / 5);
	}

	/** ¿Es de carga pesada el hueco i de una mochila de n huecos? */
	public static boolean isHeavy(int i, int slots) {
		return i < slots && i >= slots - heavySlots(slots);
	}

	/** Cuántos huecos de carga pesada lleva ocupados (al estilo de 7 Days to Die: cada uno frena un 4 %). */
	public static int heavyUsed(PlayerEntity player) {
		int n = size(player);
		BackpackInventory inv = inventory(player);
		int used = 0;
		for (int i = n - heavySlots(n); i < n; i++) {
			if (i >= 0 && !inv.getStack(i).isEmpty()) {
				used++;
			}
		}
		return used;
	}

	public static final double SLOW_PER_HEAVY = 0.04;

	/** El peso: cada hueco de carga pesada ocupado frena un 4 %. */
	public static void weigh(PlayerEntity player, int heavy) {
		EntityAttributeInstance speed = player.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
		if (speed == null) {
			return;
		}
		double slow = -SLOW_PER_HEAVY * heavy;
		EntityAttributeModifier old = speed.getModifier(WEIGHT);
		if (heavy == 0) {
			if (old != null) {
				speed.removeModifier(WEIGHT);
			}
		} else if (old == null || Math.abs(old.value() - slow) > 0.001) {
			speed.updateModifier(new EntityAttributeModifier(WEIGHT, slow, EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		}
	}

	private static void removeWeight(PlayerEntity player) {
		EntityAttributeInstance speed = player.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
		if (speed != null && speed.getModifier(WEIGHT) != null) {
			speed.removeModifier(WEIGHT);
		}
	}

	// ------------------------------------------------------------------ abrirla, y al morir

	/** Abre la mochila que lleva puesta (la tecla B). */
	public static void open(ServerPlayerEntity player) {
		int slots = size(player);
		if (slots == 0) {
			player.sendMessage(Text.translatable("message.evolvedwarfare.backpack.none").formatted(Formatting.GRAY), true);
			return;
		}
		ItemStack worn = worn(player);
		player.openHandledScreen(new ExtendedScreenHandlerFactory<Integer>() {
			@Override
			public Integer getScreenOpeningData(ServerPlayerEntity p) {
				return slots;
			}

			@Override
			public Text getDisplayName() {
				return worn.getName();
			}

			@Override
			public ScreenHandler createMenu(int syncId, PlayerInventory inv, PlayerEntity p) {
				return new BackpackScreenHandler(syncId, inv, inventory(p), slots);
			}
		});
	}

	/** Al morir (sin conservar el inventario), la mochila cae entera, con todo dentro. Devuelve la que ha caído. */
	public static ItemStack dropOnDeath(PlayerEntity player) {
		if (player.getWorld().getGameRules().getBoolean(GameRules.KEEP_INVENTORY)) {
			return ItemStack.EMPTY;
		}
		ItemStack worn = worn(player);
		if (worn.isEmpty()) {
			return ItemStack.EMPTY;
		}
		inventory(player).flush();
		worn = worn(player).copy();
		setWornRaw(player, ItemStack.EMPTY);
		player.dropItem(worn, true, false);
		return worn;
	}
}
