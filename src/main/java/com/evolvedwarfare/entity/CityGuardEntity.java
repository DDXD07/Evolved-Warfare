package com.evolvedwarfare.entity;

import com.evolvedwarfare.world.city.CityDesigner;
import com.evolvedwarfare.world.city.CityLaw;
import net.minecraft.entity.EntityData;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.LocalDifficulty;
import net.minecraft.world.ServerWorldAccess;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * Guardia de una ciudad amurallada: no se puede contratar, se queda en su puesto (las puertas, la plaza, las torres, el
 * cuartel), lucha contra los monstruos y los asaltantes y va a por el jugador que haya hecho daño a alguien de su
 * ciudad mientras la ciudad le guarde rencor ({@link CityLaw}: un día). Persigue por toda la ciudad y un poco más.
 */
public class CityGuardEntity extends SoldierEntity {
	/** Colores del tabardo (cuero teñido) según el estilo de la ciudad. */
	private static final int[] COLORS = {0x8E2B2B, 0xC9952B, 0x2D4A86};

	private long cityKey;
	@Nullable
	private BlockPos cityCenter;
	private int cityRadius = 48;
	private int style;

	public CityGuardEntity(EntityType<? extends CityGuardEntity> type, World world) {
		super(type, world);
	}

	@Override
	protected void initGoals() {
		super.initGoals();
		targetSelector.add(2, new ActiveTargetGoal<>(this, PlayerEntity.class, 10, true, false,
				target -> target instanceof PlayerEntity player && CityLaw.wanted(getWorld(), cityKey, player)));
	}

	/** De qué ciudad es (su clave, el centro de la plaza y el radio de la muralla) y su estilo. */
	public void setCity(long key, BlockPos center, int radius, CityDesigner.Style cityStyle) {
		this.cityKey = key;
		this.cityCenter = center;
		this.cityRadius = radius;
		this.style = cityStyle.ordinal();
	}

	public long getCityKey() {
		return cityKey;
	}

	@Override
	public EntityData initialize(ServerWorldAccess world, LocalDifficulty difficulty, SpawnReason spawnReason,
			@Nullable EntityData entityData) {
		NpcRole role = getRole();
		presetRole(role);
		NpcCombat.equip(this, role, world.getRandom(), COLORS[Math.floorMod(style, COLORS.length)], false);
		equipStack(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			setEquipmentDropChance(slot, 0.0F);
		}
		EntityData data = super.initialize(world, difficulty, spawnReason, entityData);
		setOrder(NpcOrder.STAY);
		return data;
	}

	/** Nadie contrata a un guardia de la ciudad. */
	@Override
	public boolean isForHire() {
		return false;
	}

	@Override
	protected ActionResult interactMob(PlayerEntity player, Hand hand) {
		if (!getWorld().isClient) {
			player.sendMessage(Text.translatable(CityLaw.wanted(getWorld(), cityKey, player) ? "message.evolvedwarfare.city_guard.angry"
					: "message.evolvedwarfare.city_guard.hello"), true);
		}
		return ActionResult.success(getWorld().isClient);
	}

	@Override
	public void tick() {
		super.tick();
		if (!getWorld().isClient && age % 20 == 0 && getTarget() instanceof PlayerEntity player && !CityLaw.wanted(getWorld(), cityKey, player)) {
			setTarget(null);
		}
	}

	@Override
	@Nullable
	public Vec3d getLeashCenter() {
		return cityCenter != null ? Vec3d.ofBottomCenter(cityCenter) : super.getLeashCenter();
	}

	@Override
	public double getLeashRadius() {
		return cityCenter != null ? cityRadius + 16.0 : super.getLeashRadius();
	}

	@Override
	protected Text getDefaultName() {
		return Text.translatable("entity.evolvedwarfare.city_guard");
	}

	@Override
	public boolean canImmediatelyDespawn(double distanceSquared) {
		return false;
	}

	@Override
	public void writeCustomDataToNbt(NbtCompound nbt) {
		super.writeCustomDataToNbt(nbt);
		nbt.putLong("CityKey", cityKey);
		if (cityCenter != null) {
			nbt.putInt("CityX", cityCenter.getX());
			nbt.putInt("CityY", cityCenter.getY());
			nbt.putInt("CityZ", cityCenter.getZ());
		}
		nbt.putInt("CityRadius", cityRadius);
		nbt.putInt("CityStyle", style);
	}

	@Override
	public void readCustomDataFromNbt(NbtCompound nbt) {
		super.readCustomDataFromNbt(nbt);
		cityKey = nbt.getLong("CityKey");
		if (nbt.contains("CityX")) {
			cityCenter = new BlockPos(nbt.getInt("CityX"), nbt.getInt("CityY"), nbt.getInt("CityZ"));
		}
		if (nbt.contains("CityRadius")) {
			cityRadius = nbt.getInt("CityRadius");
		}
		style = nbt.getInt("CityStyle");
	}
}
