package com.evolvedwarfare.network;

import com.evolvedwarfare.blueprint.BlueprintLibrary;
import com.evolvedwarfare.blueprint.ScanArea;
import com.evolvedwarfare.blueprint.Scanner;
import com.evolvedwarfare.blueprint.StoredBlueprint;
import com.evolvedwarfare.item.BlueprintItem;
import com.evolvedwarfare.item.StructureScannerItem;
import com.evolvedwarfare.registry.ModBlocks;
import com.evolvedwarfare.registry.ModItems;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

/** Escanear, abrir la biblioteca, mandar planos para la vista previa, imprimir y borrar. */
public final class BlueprintNetworking {
	private static final int MAX_NAME = 32;

	private BlueprintNetworking() {
	}

	static void register() {
		PayloadTypeRegistry.playC2S().register(BlueprintPayloads.Scan.ID, BlueprintPayloads.Scan.CODEC);
		PayloadTypeRegistry.playS2C().register(BlueprintPayloads.Library.ID, BlueprintPayloads.Library.CODEC);
		PayloadTypeRegistry.playC2S().register(BlueprintPayloads.Request.ID, BlueprintPayloads.Request.CODEC);
		PayloadTypeRegistry.playS2C().register(BlueprintPayloads.Data.ID, BlueprintPayloads.Data.CODEC);
		PayloadTypeRegistry.playC2S().register(BlueprintPayloads.Action.ID, BlueprintPayloads.Action.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(BlueprintPayloads.Scan.ID, (payload, context) -> scan(context.player(), payload));
		ServerPlayNetworking.registerGlobalReceiver(BlueprintPayloads.Request.ID, (payload, context) -> sendData(context.player(), payload.id()));
		ServerPlayNetworking.registerGlobalReceiver(BlueprintPayloads.Action.ID, (payload, context) -> action(context.player(), payload));
	}

	private static boolean admin(ServerPlayerEntity player) {
		return player.hasPermissionLevel(2) || player.isCreative();
	}

	// ------------------------------------------------------------------ escáner

	private static void scan(ServerPlayerEntity player, BlueprintPayloads.Scan payload) {
		ItemStack scanner = player.getStackInHand(payload.mainHand() ? Hand.MAIN_HAND : Hand.OFF_HAND);
		if (!(scanner.getItem() instanceof StructureScannerItem) || !(player.getWorld() instanceof ServerWorld world)) {
			return;
		}
		ScanArea area = StructureScannerItem.area(scanner);
		BlockBox box = area.box();
		if (box == null) {
			return;
		}
		if (!Scanner.fits(box)) {
			player.sendMessage(Text.translatable("message.evolvedwarfare.scanner.too_big", 0, 0, 0, box.getBlockCountX(),
					box.getBlockCountY(), box.getBlockCountZ()), true);
			return;
		}
		if (player.squaredDistanceTo(box.getCenter().toCenterPos()) > 64.0 * 64.0) {
			player.sendMessage(Text.translatable("message.evolvedwarfare.scanner.too_far"), true);
			return;
		}
		if (!player.isCreative() && !player.getInventory().contains(new ItemStack(Items.PAPER))) {
			player.sendMessage(Text.translatable("message.evolvedwarfare.scanner.need_paper"), true);
			return;
		}
		String name = payload.name().trim();
		if (name.isEmpty()) {
			name = Text.translatable("gui.evolvedwarfare.scanner.default_name").getString();
		}
		if (name.length() > MAX_NAME) {
			name = name.substring(0, MAX_NAME);
		}
		Direction front = Direction.byId(payload.front());
		if (!front.getAxis().isHorizontal()) {
			front = Direction.NORTH;
		}
		String id = BlueprintLibrary.newId(name);
		StoredBlueprint blueprint = Scanner.scan(world, box, front, id, name, player.getName().getString());
		if (!BlueprintLibrary.save(blueprint)) {
			player.sendMessage(Text.translatable("message.evolvedwarfare.scanner.failed"), true);
			return;
		}
		if (!player.isCreative()) {
			takePaper(player);
		}
		player.getInventory().offerOrDrop(BlueprintItem.create(ModItems.BLUEPRINT, blueprint));
		world.playSound(null, player.getBlockPos(), SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT, SoundCategory.PLAYERS, 1.0F, 1.0F);
		player.sendMessage(Text.translatable("message.evolvedwarfare.scanner.saved", name, blueprint.blockCount()), false);
	}

	private static void takePaper(ServerPlayerEntity player) {
		for (int i = 0; i < player.getInventory().size(); i++) {
			ItemStack stack = player.getInventory().getStack(i);
			if (stack.isOf(Items.PAPER)) {
				stack.decrement(1);
				return;
			}
		}
	}

	// ------------------------------------------------------------------ mesa del arquitecto

	/** Abre la mesa del arquitecto (la lista de planos). */
	public static void openLibrary(ServerPlayerEntity player, BlockPos table) {
		sendLibrary(player, table, true);
	}

	private static void sendLibrary(ServerPlayerEntity player, BlockPos table, boolean open) {
		if (player.getServer() == null) {
			return;
		}
		List<BlueprintLibrary.Summary> list = new ArrayList<>(BlueprintLibrary.list(player.getServer()));
		ServerPlayNetworking.send(player, new BlueprintPayloads.Library(open, table, player.getName().getString(), admin(player), list));
	}

	private static void sendData(ServerPlayerEntity player, String id) {
		if (player.getServer() == null) {
			return;
		}
		StoredBlueprint b = BlueprintLibrary.get(player.getServer(), id);
		if (b == null) {
			return;
		}
		List<Integer> blocks = new ArrayList<>(b.blocks().length);
		for (int value : b.blocks()) {
			blocks.add(value);
		}
		ServerPlayNetworking.send(player, new BlueprintPayloads.Data(b.id, b.sizeX, b.sizeY, b.sizeZ, b.palette(), blocks));
	}

	private static boolean atTable(ServerPlayerEntity player, BlockPos table) {
		return player.getWorld().getBlockState(table).isOf(ModBlocks.ARCHITECT_TABLE)
				&& player.squaredDistanceTo(Vec3d.ofCenter(table)) <= 8.0 * 8.0;
	}

	private static void action(ServerPlayerEntity player, BlueprintPayloads.Action payload) {
		if (player.getServer() == null || !atTable(player, payload.table())) {
			return;
		}
		StoredBlueprint b = BlueprintLibrary.get(player.getServer(), payload.id());
		if (b == null) {
			return;
		}
		switch (payload.action()) {
			case BlueprintPayloads.Action.PRINT -> {
				if (!player.isCreative()) {
					if (!player.getInventory().contains(new ItemStack(Items.PAPER))) {
						player.sendMessage(Text.translatable("message.evolvedwarfare.scanner.need_paper"), true);
						return;
					}
					takePaper(player);
				}
				player.getInventory().offerOrDrop(BlueprintItem.create(ModItems.BLUEPRINT, b));
				player.getWorld().playSound(null, payload.table(), SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT, SoundCategory.BLOCKS,
						1.0F, 1.0F);
			}
			case BlueprintPayloads.Action.DELETE -> {
				if (b.builtin || !(admin(player) || b.author.equals(player.getName().getString()))) {
					player.sendMessage(Text.translatable("message.evolvedwarfare.blueprint.not_yours"), true);
					return;
				}
				BlueprintLibrary.delete(b.id);
				sendLibrary(player, payload.table(), false);
			}
			default -> {
			}
		}
	}
}
