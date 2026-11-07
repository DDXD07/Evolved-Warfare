package com.evolvedwarfare.mixin;

import com.evolvedwarfare.hunting.Hunting;
import java.util.List;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.loot.LootTable;
import net.minecraft.loot.context.LootContextParameterSet;
import net.minecraft.loot.context.LootContextParameters;
import net.minecraft.loot.context.LootContextTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Al morir un animal de caza, en vez de soltar su carne se queda tumbado en el suelo (para despellejarlo con un cuchillo). */
@Mixin(LivingEntity.class)
public abstract class LivingEntityCarcassMixin extends Entity {
	@Shadow
	protected PlayerEntity attackingPlayer;

	protected LivingEntityCarcassMixin(EntityType<?> type, World world) {
		super(type, world);
	}

	@Shadow
	public abstract net.minecraft.registry.RegistryKey<LootTable> getLootTable();

	@Shadow
	public abstract long getLootTableSeed();

	@Inject(method = "dropLoot", at = @At("HEAD"), cancellable = true)
	private void evolvedwarfare$carcass(DamageSource source, boolean causedByPlayer, CallbackInfo ci) {
		LivingEntity self = (LivingEntity) (Object) this;
		if (!(getWorld() instanceof ServerWorld world) || !Hunting.skinnable(self)) {
			return;
		}
		LootTable table = world.getServer().getReloadableRegistries().getLootTable(getLootTable());
		LootContextParameterSet.Builder b = new LootContextParameterSet.Builder(world).add(LootContextParameters.THIS_ENTITY, self)
				.add(LootContextParameters.ORIGIN, getPos()).add(LootContextParameters.DAMAGE_SOURCE, source)
				.addOptional(LootContextParameters.ATTACKING_ENTITY, source.getAttacker())
				.addOptional(LootContextParameters.DIRECT_ATTACKING_ENTITY, source.getSource());
		if (causedByPlayer && attackingPlayer != null) {
			b = b.add(LootContextParameters.LAST_DAMAGE_PLAYER, attackingPlayer).luck(attackingPlayer.getLuck());
		}
		List<ItemStack> loot = table.generateLoot(b.build(LootContextTypes.ENTITY), getLootTableSeed());
		if (Hunting.spawnCarcass(world, self, loot)) {
			ci.cancel();
		}
	}
}
