package com.evolvedwarfare.smithing;

import com.evolvedwarfare.EvolvedWarfare;
import com.evolvedwarfare.config.EwConfig;
import com.evolvedwarfare.registry.ModComponents;
import com.evolvedwarfare.registry.ModItems;
import com.mojang.serialization.Codec;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.recipe.SpecialRecipeSerializer;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;

/**
 * La herrería realista: las herramientas y armas de hierro, oro y diamante ya no se hacen en la mesa. Se calienta el
 * lingote (alto horno u horno) y, con el lingote al rojo en la mano y el martillo de herrero encima, clic derecho a un
 * yunque: se elige la pieza y se forja con el minijuego ({@link ForgeMinigame}). La pieza se monta en la mesa con su mango
 * ({@link ForgedToolRecipe}). El nivel de herrería (0-10) sube forjando y agranda la zona buena.
 */
public final class Smithing {
	public static final AttachmentType<Integer> XP = AttachmentRegistry.<Integer>builder().persistent(Codec.INT).initializer(() -> 0)
			.buildAndRegister(EvolvedWarfare.id("smithing_xp"));
	private static final int[] LEVELS = {0, 25, 70, 140, 230, 340, 470, 630, 820, 1040, 1300};

	record Session(int id, ForgeMaterial material, BlockPos anvil, long started) {
	}

	private static final Map<UUID, Session> SESSIONS = new HashMap<>();
	private static int nextId = 1;

	private Smithing() {
	}

	public static void register() {
		ForgedToolRecipe.SERIALIZER = Registry.register(Registries.RECIPE_SERIALIZER, EvolvedWarfare.id("forged_tool"),
				new SpecialRecipeSerializer<>(ForgedToolRecipe::new));
		PayloadTypeRegistry.playS2C().register(SmithingPayloads.Open.ID, SmithingPayloads.Open.CODEC);
		PayloadTypeRegistry.playC2S().register(SmithingPayloads.Result.ID, SmithingPayloads.Result.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(SmithingPayloads.Result.ID, (payload, context) -> finish(context.player(), payload));
		UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
			if (hand != Hand.MAIN_HAND || !world.getBlockState(hit.getBlockPos()).isIn(BlockTags.ANVIL)
					|| !(player.getMainHandStack().getItem() instanceof HotIngotItem hot)) {
				return ActionResult.PASS;
			}
			if (world.isClient) {
				return ActionResult.SUCCESS;
			}
			if (!hasHammer(player)) {
				player.sendMessage(Text.translatable("message.evolvedwarfare.smithing.need_hammer").formatted(Formatting.GOLD), true);
				return ActionResult.FAIL;
			}
			open((ServerPlayerEntity) player, hot.material, hit.getBlockPos());
			return ActionResult.SUCCESS;
		});
	}

	// ------------------------------------------------------------------ nivel

	public static int level(PlayerEntity player) {
		int xp = player.getAttachedOrElse(XP, 0);
		int level = 0;
		while (level < LEVELS.length - 1 && xp >= LEVELS[level + 1]) {
			level++;
		}
		return level;
	}

	private static void addXp(ServerPlayerEntity player, int amount) {
		int before = level(player);
		player.setAttached(XP, player.getAttachedOrElse(XP, 0) + amount);
		if (level(player) > before) {
			player.sendMessage(Text.translatable("message.evolvedwarfare.smithing.level", level(player)).formatted(Formatting.GOLD), false);
			player.playSoundToPlayer(SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 0.8F, 1.2F);
		}
	}

	// ------------------------------------------------------------------ forjar

	public static boolean hasHammer(PlayerEntity player) {
		return player.isCreative() || player.getInventory().contains(new ItemStack(ModItems.SMITHING_HAMMER));
	}

	private static int count(PlayerInventory inv, net.minecraft.item.Item item) {
		int n = 0;
		for (int i = 0; i < inv.size(); i++) {
			if (inv.getStack(i).isOf(item)) {
				n += inv.getStack(i).getCount();
			}
		}
		return n;
	}

	private static void take(PlayerInventory inv, net.minecraft.item.Item item, int amount) {
		for (int i = 0; i < inv.size() && amount > 0; i++) {
			ItemStack s = inv.getStack(i);
			if (s.isOf(item)) {
				int n = Math.min(amount, s.getCount());
				s.decrement(n);
				amount -= n;
			}
		}
	}

	public static void open(ServerPlayerEntity player, ForgeMaterial material, BlockPos anvil) {
		Session s = new Session(nextId++, material, anvil.toImmutable(), player.getWorld().getTime());
		SESSIONS.put(player.getUuid(), s);
		net.minecraft.item.Item hot = material == ForgeMaterial.GOLD ? ModItems.HOT_GOLD_INGOT : ModItems.HOT_IRON_INGOT;
		ServerPlayNetworking.send(player, new SmithingPayloads.Open(s.id(), material.ordinal(), count(player.getInventory(), hot),
				count(player.getInventory(), Items.DIAMOND), level(player), player.getRandom().nextLong()));
	}

	/** Hace la pieza (para el yunque y las pruebas). */
	public static ItemStack part(ForgedPart part, ForgeMaterial material, int quality) {
		ItemStack stack = new ItemStack(ModItems.FORGED_PART);
		stack.set(ModComponents.FORGED_PART, new PartData(part.id, material.id, quality));
		return stack;
	}

	private static void finish(ServerPlayerEntity player, SmithingPayloads.Result result) {
		Session s = SESSIONS.remove(player.getUuid());
		if (s == null || s.id() != result.session() || result.part() < 0 || result.part() >= ForgedPart.values().length
				|| !(player.getWorld() instanceof ServerWorld world)) {
			return;
		}
		ForgedPart part = ForgedPart.values()[result.part()];
		ForgeMaterial material = result.diamond() && s.material() == ForgeMaterial.IRON ? ForgeMaterial.DIAMOND : s.material();
		if (part.result(material) == null || world.getTime() - s.started() < part.strikes * 4L) {
			return;
		}
		net.minecraft.item.Item hot = s.material() == ForgeMaterial.GOLD ? ModItems.HOT_GOLD_INGOT : ModItems.HOT_IRON_INGOT;
		PlayerInventory inv = player.getInventory();
		if (!player.isCreative()) {
			if (count(inv, hot) < part.cost) {
				player.sendMessage(Text.translatable("message.evolvedwarfare.smithing.need_ingots", part.cost).formatted(Formatting.RED), true);
				return;
			}
			if (material == ForgeMaterial.DIAMOND && count(inv, Items.DIAMOND) < part.cost) {
				player.sendMessage(Text.translatable("message.evolvedwarfare.smithing.need_diamonds", part.cost).formatted(Formatting.RED), true);
				return;
			}
			take(inv, hot, part.cost);
			if (material == ForgeMaterial.DIAMOND) {
				take(inv, Items.DIAMOND, part.cost);
			}
			for (int i = 0; i < inv.size(); i++) {
				if (inv.getStack(i).isOf(ModItems.SMITHING_HAMMER)) {
					inv.getStack(i).damage(1, player, i == inv.selectedSlot ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
					break;
				}
			}
		}
		int quality = ForgeMinigame.quality(Math.max(0.0F, Math.min(1.0F, result.score())));
		ItemStack piece = part(part, material, quality);
		if (!inv.insertStack(piece)) {
			player.dropItem(piece, false);
		}
		player.sendMessage(Text.translatable("message.evolvedwarfare.smithing.done", piece.getName(),
				Text.translatable("forge_quality.evolvedwarfare." + ForgeQuality.key(quality)).formatted(ForgeQuality.color(quality))), true);
		world.playSound(null, s.anvil(), SoundEvents.BLOCK_ANVIL_USE, SoundCategory.BLOCKS, 0.8F, 1.0F);
		world.spawnParticles(ParticleTypes.LAVA, s.anvil().getX() + 0.5, s.anvil().getY() + 1.0, s.anvil().getZ() + 0.5, 6, 0.2, 0.1, 0.2, 0.0);
		addXp(player, 2 + part.cost * 2 + quality * 2);
	}
}
