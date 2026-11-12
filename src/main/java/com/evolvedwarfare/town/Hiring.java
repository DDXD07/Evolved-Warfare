package com.evolvedwarfare.town;

import com.evolvedwarfare.entity.AbstractNpcEntity;
import com.evolvedwarfare.entity.CityGuardEntity;
import com.evolvedwarfare.entity.NpcNames;
import com.evolvedwarfare.entity.RecruitEntity;
import com.evolvedwarfare.entity.SoldierEntity;
import com.evolvedwarfare.entity.TraderEntity;
import com.evolvedwarfare.logistics.Market;
import com.evolvedwarfare.registry.ModEntities;
import com.evolvedwarfare.world.city.CityLaw;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.jetbrains.annotations.Nullable;

/**
 * Contratar a cualquiera del pueblo: los vecinos y los reclutas, con un contrato; los guardias de la ciudad, con un
 * contrato y {@link #GUARD_COINS} Glory Coins (ya saben luchar; no si la ciudad te guarda rencor); los mercaderes, con un
 * contrato y {@link #MERCHANT_COINS} (cierran la tienda y se van contigo); y los aldeanos de las aldeas, con un contrato.
 * Los que no eran reclutas se convierten en uno (o en soldado, los guardias), con su nombre, su oficio y sus amistades.
 */
public final class Hiring {
	public static final int GUARD_COINS = 15;
	public static final int MERCHANT_COINS = 25;

	private Hiring() {
	}

	/** Cobra el contrato (y las monedas, si hacen falta); si no le llega, avisa y no cobra nada. */
	private static boolean pay(PlayerEntity player, ItemStack contract, int coins) {
		if (coins > 0 && !player.getAbilities().creativeMode) {
			if (Market.countCoins(player.getInventory()) < coins) {
				player.sendMessage(Text.translatable("message.evolvedwarfare.hire.coins", coins).formatted(Formatting.RED), true);
				return false;
			}
			Market.takeCoins(player.getInventory(), coins);
		}
		contract.decrementUnlessCreative(1, player);
		return true;
	}

	/** Un guardia de la ciudad deja su puesto y pasa a ser tu soldado (con su clase, su equipo y su nombre). */
	@Nullable
	public static SoldierEntity hireGuard(CityGuardEntity guard, PlayerEntity player, ItemStack contract) {
		if (CityLaw.wanted(guard.getWorld(), guard.getCityKey(), player)) {
			player.sendMessage(Text.translatable("message.evolvedwarfare.city_guard.angry"), true);
			return null;
		}
		if (!pay(player, contract, GUARD_COINS)) {
			return null;
		}
		var role = guard.getRole();
		NpcProfile profile = guard.ewProfile();
		SoldierEntity soldier = guard.convertTo(ModEntities.SOLDIER, true);
		if (soldier == null) {
			return null;
		}
		soldier.setRole(role);
		soldier.setVariant(guard.getVariant());
		carry(profile, soldier, NpcJob.GUARD);
		soldier.signContract(player);
		return soldier;
	}

	/** Un mercader cierra su tienda y se va contigo (como recluta: luego puedes darle un puesto o un arma). */
	@Nullable
	public static RecruitEntity hireTrader(TraderEntity trader, PlayerEntity player, ItemStack contract) {
		if (!pay(player, contract, MERCHANT_COINS)) {
			return null;
		}
		int variant = trader.getVariant();
		NpcProfile profile = trader.ewProfile();
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			trader.equipStack(slot, ItemStack.EMPTY);
		}
		RecruitEntity recruit = trader.convertTo(ModEntities.RECRUIT, false);
		if (recruit == null) {
			return null;
		}
		recruit.setVariant(variant);
		if (!trader.hasCustomName()) {
			recruit.setCustomName(NpcNames.randomName(recruit.getRandom(), variant));
		}
		carry(profile, recruit, NpcJob.MERCHANT);
		recruit.signContract(player);
		return recruit;
	}

	/** Un aldeano deja su aldea y se va contigo, con el oficio que tenía. */
	@Nullable
	public static RecruitEntity hireVillager(VillagerEntity villager, PlayerEntity player, ItemStack contract) {
		if (!pay(player, contract, 0)) {
			return null;
		}
		NpcJob job = NpcJob.of(villager);
		villager.releaseAllTickets();
		RecruitEntity recruit = villager.convertTo(ModEntities.RECRUIT, false);
		if (recruit == null) {
			return null;
		}
		int variant = recruit.getRandom().nextInt(NpcNames.VARIANTS);
		recruit.setVariant(variant);
		if (!villager.hasCustomName()) {
			recruit.setCustomName(NpcNames.randomName(recruit.getRandom(), variant));
		}
		NpcProfile p = recruit.ewProfile();
		p.job = job;
		p.personality = Personality.random(recruit.getRandom());
		p.rolled = true;
		p.bedSearched = true;
		recruit.ewProfileChanged();
		recruit.signContract(player);
		return recruit;
	}

	/** Se lleva su perfil (oficio, carácter, amistades); deja su cama y su puesto. */
	private static void carry(NpcProfile from, AbstractNpcEntity to, NpcJob job) {
		NpcProfile p = to.ewProfile();
		p.copyFrom(from);
		if (!p.rolled) {
			p.job = job;
			p.personality = Personality.random(to.getRandom());
			p.rolled = true;
		}
		p.bed = null;
		p.workplace = null;
		p.bedSearched = true;
		to.ewProfileChanged();
	}
}
