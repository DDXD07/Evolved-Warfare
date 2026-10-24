package com.evolvedwarfare.item;

import com.evolvedwarfare.ClientBridge;
import com.evolvedwarfare.blueprint.ScanArea;
import com.evolvedwarfare.blueprint.Scanner;
import com.evolvedwarfare.registry.ModComponents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import java.util.List;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.joml.Vector3f;

/**
 * Escáner de planos: clic derecho en un bloque marca una esquina (la primera, luego la segunda). Con las dos marcadas,
 * clic derecho al aire abre el escáner para ponerle nombre, elegir el frente y guardarlo (gasta un papel). Mayús + clic
 * al aire borra la selección. Mientras lo llevas en la mano se ve el borde de la caja.
 */
public class StructureScannerItem extends Item {
	private static final DustParticleEffect EDGE = new DustParticleEffect(new Vector3f(0.2F, 0.8F, 1.0F), 1.0F);
	private static final DustParticleEffect TOO_BIG = new DustParticleEffect(new Vector3f(1.0F, 0.25F, 0.2F), 1.0F);

	public StructureScannerItem(Settings settings) {
		super(settings);
	}

	public static ScanArea area(ItemStack stack) {
		return stack.getOrDefault(ModComponents.SCAN_AREA, ScanArea.EMPTY);
	}

	/** Con el escáner en la mano, el clic marca la esquina aunque el bloque tenga menú (cofres, puertas...). */
	public static void registerEvents() {
		UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
			ItemStack stack = player.getStackInHand(hand);
			if (player.isSpectator() || !(stack.getItem() instanceof StructureScannerItem)) {
				return ActionResult.PASS;
			}
			return mark(world, hit.getBlockPos(), player, stack);
		});
	}

	@Override
	public ActionResult useOnBlock(ItemUsageContext context) {
		PlayerEntity player = context.getPlayer();
		return player == null ? ActionResult.PASS : mark(context.getWorld(), context.getBlockPos(), player, context.getStack());
	}

	private static ActionResult mark(World world, BlockPos clicked, PlayerEntity player, ItemStack stack) {
		if (!world.isClient) {
			ScanArea area = area(stack).mark(clicked);
			stack.set(ModComponents.SCAN_AREA, area);
			BlockPos p = clicked;
			if (area.complete()) {
				BlockBox box = area.box();
				player.sendMessage(Text.translatable(Scanner.fits(box) ? "message.evolvedwarfare.scanner.second"
								: "message.evolvedwarfare.scanner.too_big", p.getX(), p.getY(), p.getZ(),
						box.getBlockCountX(), box.getBlockCountY(), box.getBlockCountZ()), true);
			} else {
				player.sendMessage(Text.translatable("message.evolvedwarfare.scanner.first", p.getX(), p.getY(), p.getZ()), true);
			}
		}
		return ActionResult.success(world.isClient);
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
		ItemStack stack = user.getStackInHand(hand);
		if (user.isSneaking()) {
			if (!world.isClient) {
				stack.set(ModComponents.SCAN_AREA, ScanArea.EMPTY);
				user.sendMessage(Text.translatable("message.evolvedwarfare.scanner.cleared"), true);
			}
			return TypedActionResult.success(stack, world.isClient);
		}
		ScanArea area = area(stack);
		if (!area.complete()) {
			if (!world.isClient) {
				user.sendMessage(Text.translatable("message.evolvedwarfare.scanner.need_corners"), true);
			}
			return TypedActionResult.fail(stack);
		}
		if (world.isClient) {
			ClientBridge.openScanner.accept(hand);
		}
		return TypedActionResult.success(stack, world.isClient);
	}

	/** El borde de la caja, visible solo para quien lleva el escáner. */
	@Override
	public void inventoryTick(ItemStack stack, World world, Entity entity, int slot, boolean selected) {
		if (!(world instanceof ServerWorld serverWorld) || !(entity instanceof ServerPlayerEntity player) || world.getTime() % 10 != 0) {
			return;
		}
		if (!selected && player.getOffHandStack() != stack) {
			return;
		}
		ScanArea area = area(stack);
		if (area.first().isPresent() && !area.complete()) {
			BlockPos p = area.first().get();
			serverWorld.spawnParticles(player, EDGE, true, p.getX() + 0.5, p.getY() + 1.1, p.getZ() + 0.5, 6, 0.3, 0.3, 0.3, 0.0);
			return;
		}
		BlockBox box = area.box();
		if (box == null || player.squaredDistanceTo(box.getCenter().toCenterPos()) > 96 * 96) {
			return;
		}
		DustParticleEffect dust = Scanner.fits(box) ? EDGE : TOO_BIG;
		double x0 = box.getMinX();
		double y0 = box.getMinY();
		double z0 = box.getMinZ();
		double x1 = box.getMaxX() + 1;
		double y1 = box.getMaxY() + 1;
		double z1 = box.getMaxZ() + 1;
		for (double[] edge : new double[][] {
				{x0, y0, z0, x1, y0, z0}, {x0, y1, z0, x1, y1, z0}, {x0, y0, z1, x1, y0, z1}, {x0, y1, z1, x1, y1, z1},
				{x0, y0, z0, x0, y0, z1}, {x1, y0, z0, x1, y0, z1}, {x0, y1, z0, x0, y1, z1}, {x1, y1, z0, x1, y1, z1},
				{x0, y0, z0, x0, y1, z0}, {x1, y0, z0, x1, y1, z0}, {x0, y0, z1, x0, y1, z1}, {x1, y0, z1, x1, y1, z1}}) {
			double length = Math.abs(edge[3] - edge[0]) + Math.abs(edge[4] - edge[1]) + Math.abs(edge[5] - edge[2]);
			int steps = Math.max(1, (int) Math.ceil(length));
			for (int i = 0; i <= steps; i++) {
				double t = i / (double) steps;
				serverWorld.spawnParticles(player, dust, true, edge[0] + (edge[3] - edge[0]) * t, edge[1] + (edge[4] - edge[1]) * t,
						edge[2] + (edge[5] - edge[2]) * t, 1, 0.0, 0.0, 0.0, 0.0);
			}
		}
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
		ScanArea area = area(stack);
		area.first().ifPresent(p -> tooltip.add(Text.translatable("item.evolvedwarfare.structure_scanner.corner", 1, p.getX(), p.getY(), p.getZ())
				.withColor(0xAAAAAA)));
		area.second().ifPresent(p -> tooltip.add(Text.translatable("item.evolvedwarfare.structure_scanner.corner", 2, p.getX(), p.getY(), p.getZ())
				.withColor(0xAAAAAA)));
		BlockBox box = area.box();
		if (box != null) {
			tooltip.add(Text.translatable("item.evolvedwarfare.structure_scanner.size", box.getBlockCountX(), box.getBlockCountY(),
					box.getBlockCountZ()).withColor(Scanner.fits(box) ? 0x55FFFF : 0xFF5555));
		}
		tooltip.add(Text.translatable("item.evolvedwarfare.structure_scanner.help").withColor(0x777777));
		super.appendTooltip(stack, context, tooltip, type);
	}
}
