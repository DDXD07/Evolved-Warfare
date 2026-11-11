package com.evolvedwarfare.trade;

import com.evolvedwarfare.screen.TraderScreenHandler;
import com.evolvedwarfare.world.CityLevels;
import java.util.List;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.registry.Registries;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.village.VillagerType;
import org.jetbrains.annotations.Nullable;

/**
 * Los aldeanos de vanilla ya no comercian con esmeraldas (fase 54): algunos (unos 2 de cada 5 de los que tienen oficio)
 * tienen tienda con la compraventa del mod en Glory Coins, el catálogo básico de su oficio (solo lo común y lo poco común)
 * un 15 % más barato que en las ciudades; los demás solo trabajan.
 * <ul>
 * <li>granjero, pescador, carnicero: tendero (comida);</li>
 * <li>armero, herrero de armas, herrero de herramientas: armero;</li>
 * <li>albañil: cantero;</li>
 * <li>cartógrafo, bibliotecario, clérigo: joyero;</li>
 * <li>pastor, peletero, flechero: sastre (en el desierto, mercader de especias; en la nieve, peletero).</li>
 * </ul>
 */
public final class VillageShops {
	/** De cada 5 aldeanos con oficio, cuántos tienen tienda. */
	public static final int SHOPS_IN_FIVE = 2;

	private VillageShops() {
	}

	/** El oficio de comerciante que le tocaría por su oficio de aldeano (null si no tiene oficio). */
	@Nullable
	public static TraderType tradeOf(VillagerEntity v) {
		String job = Registries.VILLAGER_PROFESSION.getId(v.getVillagerData().getProfession()).getPath();
		VillagerType type = v.getVillagerData().getType();
		return switch (job) {
			case "farmer", "fisherman", "butcher" -> TraderType.GROCER;
			case "armorer", "weaponsmith", "toolsmith" -> TraderType.ARMORER;
			case "mason" -> TraderType.MASON;
			case "cartographer", "librarian", "cleric" -> TraderType.JEWELER;
			case "shepherd", "leatherworker", "fletcher" -> type == VillagerType.DESERT ? TraderType.SPICE_MERCHANT
					: type == VillagerType.SNOW ? TraderType.FURRIER : TraderType.TAILOR;
			default -> null;
		};
	}

	/** ¿Tiene tienda? (siempre lo mismo para el mismo aldeano). */
	public static boolean hasShop(VillagerEntity v) {
		if (tradeOf(v) == null) {
			return false;
		}
		long h = it.unimi.dsi.fastutil.HashCommon.mix(v.getUuid().getMostSignificantBits() ^ v.getUuid().getLeastSignificantBits());
		return Math.floorMod(h, 5L) < SHOPS_IN_FIVE;
	}

	/** Lo que hace el aldeano tendero (lo pone la mezcla en VillagerEntity). */
	public interface Keeper extends Shop {
		/** El catálogo de hoy (con su nivel y sus precios). */
		List<Deal> evolvedwarfare$catalog(ServerWorld world, TraderType type);

		int evolvedwarfare$level(ServerWorld world);
	}

	/** Clic derecho en un aldeano: abre su tienda o dice que está trabajando. */
	public static void interact(VillagerEntity villager, ServerPlayerEntity player) {
		TraderType type = tradeOf(villager);
		if (type == null || !hasShop(villager) || !(villager instanceof Keeper keeper)) {
			Text job = Text.translatable("entity.minecraft.villager." + Registries.VILLAGER_PROFESSION.getId(villager.getVillagerData().getProfession()).getPath());
			player.sendMessage(Text.translatable(type == null ? "message.evolvedwarfare.villager.idle" : "message.evolvedwarfare.villager.working",
					villager.getDisplayName(), job).formatted(Formatting.GRAY), true);
			villager.playSound(SoundEvents.ENTITY_VILLAGER_NO, 1.0F, villager.getSoundPitch());
			return;
		}
		ServerWorld world = player.getServerWorld();
		List<Deal> deals = keeper.evolvedwarfare$catalog(world, type);
		int level = keeper.evolvedwarfare$level(world);
		int percent = (int) Math.round((CityLevels.priceMultiplier(level) * TraderType.VILLAGE_DISCOUNT - 1.0) * 100.0);
		// el nivel va en negativo: así la pantalla sabe que es una aldea
		TraderScreenHandler.Data data = new TraderScreenHandler.Data(villager.getId(), type.ordinal(), -level, percent, deals);
		if (villager.getCustomer() == null) {
			villager.setCustomer(player);
		}
		player.openHandledScreen(new ExtendedScreenHandlerFactory<TraderScreenHandler.Data>() {
			@Override
			public TraderScreenHandler.Data getScreenOpeningData(ServerPlayerEntity serverPlayer) {
				return data;
			}

			@Override
			public Text getDisplayName() {
				return Text.translatable("gui.evolvedwarfare.villager.shop", villager.getDisplayName(), Text.translatable("entity.evolvedwarfare.trader." + type.id()));
			}

			@Override
			public ScreenHandler createMenu(int syncId, PlayerInventory inv, PlayerEntity p) {
				return new TraderScreenHandler(syncId, inv, keeper, data);
			}
		});
		villager.playSound(SoundEvents.ENTITY_VILLAGER_TRADE, 1.0F, villager.getSoundPitch());
	}
}
