package com.evolvedwarfare.backpack;

import com.evolvedwarfare.network.ActivityPayload;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

/**
 * Dejar la mochila en el suelo y recogerla (fase 40), con su animación (los demás también la ven):
 * <ul>
 * <li>Agachado + clic derecho con la mano vacía en el suelo: te la quitas de la espalda y la dejas (o, con ella en la mano,
 * la bajas). Tarda {@link #PLACE_TICKS} tics: la coges del hombro, te agachas y la dejas.</li>
 * <li>Agachado + clic derecho con la mano vacía en una mochila del suelo (sin llevar otra): te agachas, la coges y te la
 * echas a la espalda ({@link #DON_TICKS} tics; a los {@link #GRAB_TICK} ya es tuya).</li>
 * </ul>
 */
public final class PackActions {
	public static final int PLACE_TICKS = 12, DON_TICKS = 12, GRAB_TICK = 4;

	private record Pending(boolean place, boolean fromBack, BlockPos pos, Direction facing, int due, int grab, ServerWorld world) {
	}

	private static final Map<UUID, Pending> PENDING = new HashMap<>();
	private static int ticks;

	private PackActions() {
	}

	public static void register() {
		UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
			if (hand != Hand.MAIN_HAND || !player.isSneaking() || !player.getMainHandStack().isEmpty() || player.isSpectator()) {
				return ActionResult.PASS;
			}
			BlockPos pos = hit.getBlockPos();
			if (world.getBlockState(pos).isOf(Backpacks.BACKPACK_BLOCK)) {
				if (!Backpacks.worn(player).isEmpty()) {
					return ActionResult.PASS;
				}
				if (player instanceof ServerPlayerEntity sp) {
					startPickup(sp, pos);
				}
				return ActionResult.SUCCESS;
			}
			if (hit.getSide() == Direction.UP && !Backpacks.worn(player).isEmpty()) {
				if (player instanceof ServerPlayerEntity sp) {
					startPlace(sp, pos.up(), player.getHorizontalFacing().getOpposite(), true);
				}
				return ActionResult.SUCCESS;
			}
			return ActionResult.PASS;
		});
	}

	public static boolean busy(PlayerEntity player) {
		return PENDING.containsKey(player.getUuid());
	}

	/** Empieza a dejarla en pos (de la espalda, o de la mano). */
	public static boolean startPlace(ServerPlayerEntity player, BlockPos pos, Direction facing, boolean fromBack) {
		if (busy(player) || !player.getServerWorld().getBlockState(pos).isReplaceable()) {
			return false;
		}
		PENDING.put(player.getUuid(), new Pending(true, fromBack, pos.toImmutable(), facing, ticks + PLACE_TICKS, 0, player.getServerWorld()));
		ActivityPayload.set(player, ActivityPayload.PLACING_PACK);
		player.getServerWorld().playSound(null, player.getBlockPos(), SoundEvents.ITEM_ARMOR_EQUIP_LEATHER.value(), SoundCategory.PLAYERS, 0.8F, 0.8F);
		return true;
	}

	/** Empieza a recoger la del suelo en pos. */
	public static boolean startPickup(ServerPlayerEntity player, BlockPos pos) {
		if (busy(player)) {
			return false;
		}
		PENDING.put(player.getUuid(), new Pending(false, true, pos.toImmutable(), Direction.NORTH, ticks + DON_TICKS, ticks + GRAB_TICK,
				player.getServerWorld()));
		ActivityPayload.set(player, ActivityPayload.DONNING_PACK);
		return true;
	}

	public static void tick(MinecraftServer server) {
		ticks++;
		Iterator<Map.Entry<UUID, Pending>> it = PENDING.entrySet().iterator();
		while (it.hasNext()) {
			var e = it.next();
			Pending p = e.getValue();
			ServerPlayerEntity player = server.getPlayerManager().getPlayer(e.getKey());
			if (player == null || !player.isAlive() || player.getServerWorld() != p.world() || player.squaredDistanceTo(p.pos().toCenterPos()) > 36) {
				if (player != null) {
					ActivityPayload.set(player, ActivityPayload.NONE);
				}
				it.remove();
				continue;
			}
			if (!p.place() && ticks == p.grab()) {
				grab(player, p);
			}
			if (ticks >= p.due()) {
				if (p.place()) {
					finishPlace(player, p);
				}
				ActivityPayload.set(player, ActivityPayload.NONE);
				it.remove();
			}
		}
	}

	private static void finishPlace(ServerPlayerEntity player, Pending p) {
		ItemStack stack;
		if (p.fromBack()) {
			Backpacks.inventory(player).flush();
			stack = Backpacks.worn(player).copy();
		} else {
			stack = player.getMainHandStack().getItem() instanceof BackpackItem ? player.getMainHandStack().copy() : ItemStack.EMPTY;
		}
		if (stack.isEmpty() || !BackpackBlock.place(p.world(), p.pos(), stack, p.facing())) {
			return;
		}
		if (p.fromBack()) {
			Backpacks.setWornRaw(player, ItemStack.EMPTY);
		} else {
			player.getMainHandStack().decrementUnlessCreative(1, player);
		}
	}

	private static void grab(ServerPlayerEntity player, Pending p) {
		if (!(p.world().getBlockEntity(p.pos()) instanceof BackpackBlockEntity be) || !Backpacks.worn(player).isEmpty()) {
			return;
		}
		ItemStack stack = be.toItem();
		be.clear();
		p.world().removeBlock(p.pos(), false);
		Backpacks.setWorn(player, stack);
		p.world().playSound(null, player.getBlockPos(), SoundEvents.ITEM_ARMOR_EQUIP_LEATHER.value(), SoundCategory.PLAYERS, 1.0F, 1.0F);
	}
}
