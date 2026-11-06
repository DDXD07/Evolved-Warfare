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
 * La herrería realista: las herramientas y armas de hierro, oro y diamante ya no se hacen en la mesa.
 * <ol>
 * <li>Se calienta el lingote (alto horno u horno) y se funde en un crisol sobre el fuego ({@link CrucibleBlock}).</li>
 * <li>Se pone un molde de arcilla ({@link MoldBlock}), se elige la pieza y se talla (minijuego).</li>
 * <li>Se vierte el metal del crisol en el molde (lo justo), cuaja, se enfría y se rompe: sale la pieza en bruto.</li>
 * <li>Con la pieza en bruto en la mano y el martillo encima, clic derecho a un yunque: se afila con el minijuego del martillo
 * ({@link ForgeMinigame}) y, con hierro y diamantes, se le puede dar filo de diamante.</li>
 * <li>La pieza se monta en la mesa con su mango ({@link ForgedToolRecipe}).</li>
 * </ol>
 * La calidad sale del molde, del vertido y del afilado. El nivel de herrería (0-10) sube forjando y agranda la zona buena.
 */
public final class Smithing {
	public static final AttachmentType<Integer> XP = AttachmentRegistry.<Integer>builder().persistent(Codec.INT).initializer(() -> 0)
			.buildAndRegister(EvolvedWarfare.id("smithing_xp"));
	private static final int[] LEVELS = {0, 25, 70, 140, 230, 340, 470, 630, 820, 1040, 1300};

	record Session(int id, ForgeMaterial material, BlockPos anvil, long started, int slot, ForgedPart part, float cast) {
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
		ClayMoldRecipe.SERIALIZER = Registry.register(Registries.RECIPE_SERIALIZER, EvolvedWarfare.id("clay_mold"),
				new SpecialRecipeSerializer<>(ClayMoldRecipe::new));
		PayloadTypeRegistry.playS2C().register(MoldPayloads.Open.ID, MoldPayloads.Open.CODEC);
		PayloadTypeRegistry.playC2S().register(MoldPayloads.Carve.ID, MoldPayloads.Carve.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(MoldPayloads.Carve.ID, (payload, context) -> carve(context.player(), payload));
		ServerPlayNetworking.registerGlobalReceiver(SmithingPayloads.Result.ID, (payload, context) -> finish(context.player(), payload));
		UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
			if (hand != Hand.MAIN_HAND || !world.getBlockState(hit.getBlockPos()).isIn(BlockTags.ANVIL)) {
				return ActionResult.PASS;
			}
			ItemStack held = player.getMainHandStack();
			if (held.getItem() instanceof HotIngotItem) {
				// ya no se forja el lingote: se funde
				if (!world.isClient) {
					player.sendMessage(Text.translatable("message.evolvedwarfare.smithing.use_crucible").formatted(Formatting.GOLD), true);
				}
				return ActionResult.FAIL;
			}
			PartData data = held.get(ModComponents.FORGED_PART);
			if (!held.isOf(ModItems.FORGED_PART) || data == null || data.quality() > 0) {
				return ActionResult.PASS;
			}
			if (world.isClient) {
				return ActionResult.SUCCESS;
			}
			if (!hasHammer(player)) {
				player.sendMessage(Text.translatable("message.evolvedwarfare.smithing.need_hammer").formatted(Formatting.GOLD), true);
				return ActionResult.FAIL;
			}
			ForgedPart part = ForgedPart.byId(data.part());
			ForgeMaterial material = ForgeMaterial.byId(data.material());
			if (part == null || material == null) {
				return ActionResult.FAIL;
			}
			open((ServerPlayerEntity) player, material, part, held.getOrDefault(ModComponents.CAST_SCORE, 0.5F), hit.getBlockPos());
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

	/** Abre el afilado de una pieza en bruto (la de la mano) en el yunque. */
	public static void open(ServerPlayerEntity player, ForgeMaterial material, ForgedPart part, float cast, BlockPos anvil) {
		Session s = new Session(nextId++, material, anvil.toImmutable(), player.getWorld().getTime(), player.getInventory().selectedSlot, part, cast);
		SESSIONS.put(player.getUuid(), s);
		com.evolvedwarfare.network.ActivityPayload.set(player, com.evolvedwarfare.network.ActivityPayload.FORGING);
		ServerPlayNetworking.send(player, new SmithingPayloads.Open(s.id(), material.ordinal(), part.ordinal(), cast,
				player.isCreative() ? 99 : count(player.getInventory(), Items.DIAMOND), level(player), player.getRandom().nextLong()));
	}

	/** La pieza en bruto, recién sacada del molde (calidad 0), con la nota del molde y el vertido. */
	public static ItemStack rough(ForgedPart part, ForgeMaterial material, float cast) {
		ItemStack stack = part(part, material, 0);
		stack.set(ModComponents.CAST_SCORE, Math.max(0.0F, Math.min(1.0F, cast)));
		return stack;
	}

	/** La calidad final: un 45 % el molde y el vertido, un 55 % el afilado. */
	public static int finalQuality(float cast, float hammer) {
		return ForgeMinigame.quality(Math.max(0.0F, Math.min(1.0F, cast * 0.45F + hammer * 0.55F)));
	}

	private static void carve(ServerPlayerEntity player, MoldPayloads.Carve c) {
		if (c.part() < 0 || c.part() >= ForgedPart.values().length || player.squaredDistanceTo(net.minecraft.util.math.Vec3d.ofCenter(c.pos())) > 64
				|| !(player.getWorld().getBlockEntity(c.pos()) instanceof MoldBlockEntity mold)) {
			return;
		}
		if (mold.carve(ForgedPart.values()[c.part()], c.bits(), c.score())) {
			player.getWorld().playSound(null, c.pos(), SoundEvents.BLOCK_MUD_PLACE, SoundCategory.BLOCKS, 0.8F, 1.0F);
			addXp(player, 1);
		}
	}

	/** Hace la pieza (para el yunque y las pruebas). */
	public static ItemStack part(ForgedPart part, ForgeMaterial material, int quality) {
		ItemStack stack = new ItemStack(ModItems.FORGED_PART);
		stack.set(ModComponents.FORGED_PART, new PartData(part.id, material.id, quality));
		return stack;
	}

	private static void finish(ServerPlayerEntity player, SmithingPayloads.Result result) {
		Session s = SESSIONS.remove(player.getUuid());
		com.evolvedwarfare.network.ActivityPayload.set(player, com.evolvedwarfare.network.ActivityPayload.NONE);
		if (s == null || s.id() != result.session() || result.part() != s.part().ordinal() || !(player.getWorld() instanceof ServerWorld world)) {
			return;
		}
		ForgedPart part = s.part();
		ForgeMaterial material = result.diamond() && s.material() == ForgeMaterial.IRON ? ForgeMaterial.DIAMOND : s.material();
		if (part.result(material) == null || world.getTime() - s.started() < part.strikes * 4L) {
			return;
		}
		PlayerInventory inv = player.getInventory();
		// la pieza en bruto sigue donde estaba
		ItemStack rough = inv.getStack(s.slot());
		PartData data = rough.get(ModComponents.FORGED_PART);
		if (!rough.isOf(ModItems.FORGED_PART) || data == null || data.quality() > 0 || !data.part().equals(part.id)
				|| !data.material().equals(s.material().id)) {
			return;
		}
		if (material == ForgeMaterial.DIAMOND && !player.isCreative() && count(inv, Items.DIAMOND) < part.cost) {
			player.sendMessage(Text.translatable("message.evolvedwarfare.smithing.need_diamonds", part.cost).formatted(Formatting.RED), true);
			return;
		}
		rough.decrement(1);
		if (!player.isCreative()) {
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
		int quality = finalQuality(s.cast(), result.score());
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
