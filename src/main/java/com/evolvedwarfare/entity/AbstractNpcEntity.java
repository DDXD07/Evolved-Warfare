package com.evolvedwarfare.entity;

import com.evolvedwarfare.EvolvedWarfare;
import com.evolvedwarfare.config.EwConfig;
import com.evolvedwarfare.entity.ai.FollowOwnerNpcGoal;
import com.evolvedwarfare.entity.ai.ReturnHomeGoal;
import com.evolvedwarfare.entity.ai.StayGoal;
import com.evolvedwarfare.entity.ai.WanderNearHomeGoal;
import com.evolvedwarfare.item.RecruitmentContractItem;
import com.evolvedwarfare.registry.ModSounds;
import com.evolvedwarfare.screen.NpcScreenHandler;
import java.util.UUID;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.entity.EntityData;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.ai.goal.LongDoorInteractGoal;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.ai.pathing.MobNavigation;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.LocalDifficulty;
import net.minecraft.world.ServerWorldAccess;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * Base de todos los NPCs de Evolved Warfare.
 *
 * <ul>
 *   <li><b>Origen</b>: solo aparecen en las ciudades y estructuras que genera el mod. Ahí están
 *       <i>libres</i>: viven alrededor de su sitio y esperan a que alguien los contrate.</li>
 *   <li><b>Contratar</b>: clic derecho con un contrato de reclutamiento sobre un NPC libre.</li>
 *   <li><b>Dueño</b>: el jugador que lo contrató. Solo él puede darle órdenes y abrir su menú.</li>
 *   <li><b>Órdenes</b>: seguir, quedarse quieto o volver a casa.</li>
 *   <li><b>Equipo</b>: armadura y armas en sus manos (se ven puestas y se sueltan al morir).</li>
 *   <li><b>Paga</b>: cada día de juego consume 1 provisión (comida o Glory Coin). Si pasa
 *       los días sin cobrar que diga la configuración (3), deserta.</li>
 * </ul>
 */
public abstract class AbstractNpcEntity extends PathAwareEntity {
	public static final int UPKEEP_INTERVAL = 24000;
	public static int maxUnpaidDays() {
		return Math.max(1, EwConfig.get().maxUnpaidDays);
	}
	public static final int PROVISION_SLOTS = 2;
	/** Lo que acepta como paga (comida cocinada y la Glory Coin). */
	public static final TagKey<Item> UPKEEP = TagKey.of(RegistryKeys.ITEM, EvolvedWarfare.id("upkeep"));

	private static final TrackedData<Integer> VARIANT =
			DataTracker.registerData(AbstractNpcEntity.class, TrackedDataHandlerRegistry.INTEGER);

	@Nullable
	private UUID ownerUuid;
	private NpcOrder order = NpcOrder.FOLLOW;
	@Nullable
	private BlockPos home;
	/** Sitio donde le mandaron quedarse quieto (vuelve a él después de luchar). */
	@Nullable
	private BlockPos stayPos;
	private int upkeepTimer = UPKEEP_INTERVAL;
	private int unpaidDays;
	private final SimpleInventory provisions = new SimpleInventory(PROVISION_SLOTS);

	protected AbstractNpcEntity(EntityType<? extends AbstractNpcEntity> type, World world) {
		super(type, world);
		setPersistent();
		if (getNavigation() instanceof MobNavigation navigation) {
			navigation.setCanPathThroughDoors(true);
		}
	}

	public static DefaultAttributeContainer.Builder createNpcAttributes() {
		return MobEntity.createMobAttributes()
				.add(EntityAttributes.GENERIC_MAX_HEALTH, 20.0)
				.add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.3)
				.add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 1.0);
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {
		super.initDataTracker(builder);
		builder.add(VARIANT, 0);
	}

	/** Con lanza o alabarda llega más lejos (fase 8). */
	@Override
	protected net.minecraft.util.math.Box getAttackBox() {
		net.minecraft.util.math.Box box = super.getAttackBox();
		double extra = com.evolvedwarfare.weapon.MedievalWeapons.npcReach(getMainHandStack());
		return extra > 0.0 ? box.expand(extra, 0.0, extra) : box;
	}

	@Override
	protected void initGoals() {
		goalSelector.add(0, new SwimGoal(this));
		goalSelector.add(1, new LongDoorInteractGoal(this, true));
		goalSelector.add(2, new FollowOwnerNpcGoal(this, 1.0));
		goalSelector.add(2, new StayGoal(this));
		goalSelector.add(3, new ReturnHomeGoal(this, 1.0));
		goalSelector.add(4, new WanderNearHomeGoal(this, 0.6));
		goalSelector.add(6, new LookAtEntityGoal(this, PlayerEntity.class, 8.0F));
		goalSelector.add(7, new LookAroundGoal(this));
	}

	// ------------------------------------------------------------------ apariencia

	/** Aspecto y nombre al azar; cada tipo de NPC decide los suyos. */
	protected void initAppearance(Random random) {
	}

	/**
	 * Se llama al aparecer por primera vez: al generarse dentro de una estructura (o con /summon).
	 * Un NPC sin dueño se queda viviendo alrededor del sitio donde apareció.
	 */
	@Override
	public EntityData initialize(ServerWorldAccess world, LocalDifficulty difficulty, SpawnReason spawnReason,
			@Nullable EntityData entityData) {
		EntityData data = super.initialize(world, difficulty, spawnReason, entityData);
		setLeftHanded(false);
		initAppearance(world.getRandom());
		if (ownerUuid == null) {
			home = getBlockPos();
			order = NpcOrder.HOME;
		}
		return data;
	}

	public int getVariant() {
		return getDataTracker().get(VARIANT);
	}

	public void setVariant(int variant) {
		getDataTracker().set(VARIANT, variant);
	}

	// ------------------------------------------------------------------ dueño y órdenes

	public void setOwner(PlayerEntity player) {
		ownerUuid = player.getUuid();
	}

	@Nullable
	public UUID getOwnerUuid() {
		return ownerUuid;
	}

	/** ¿Está libre, esperando a que alguien lo contrate? */
	public boolean isForHire() {
		return ownerUuid == null;
	}

	public boolean isOwner(PlayerEntity player) {
		return ownerUuid != null && ownerUuid.equals(player.getUuid());
	}

	@Nullable
	public PlayerEntity getOwner() {
		return ownerUuid == null ? null : getWorld().getPlayerByUuid(ownerUuid);
	}

	public NpcOrder getOrder() {
		return order;
	}

	/** Orden efectiva: "volver a casa" sin casa equivale a quedarse quieto. */
	public NpcOrder getEffectiveOrder() {
		return order == NpcOrder.HOME && home == null ? NpcOrder.STAY : order;
	}

	public void setOrder(NpcOrder order) {
		this.order = order;
		if (order == NpcOrder.STAY) {
			stayPos = getBlockPos();
		}
		getNavigation().stop();
	}

	@Nullable
	public BlockPos getStayPos() {
		return stayPos;
	}

	@Nullable
	public BlockPos getHome() {
		return home;
	}

	/** Ocupado con algo (un trabajador en plena tarea): no vuelve a casa ni se pone a pasear. */
	public boolean isBusy() {
		return false;
	}

	/** ¿Puede pasear por los alrededores de su casa? */
	public boolean canWander() {
		return true;
	}

	/** Fija la casa donde está ahora y le ordena quedarse allí. */
	public void setHomeHere() {
		setHomeAt(getBlockPos());
	}

	/** Le manda a vivir (o a vigilar) en un sitio concreto. */
	public void setHomeAt(BlockPos pos) {
		home = pos.toImmutable();
		setOrder(NpcOrder.HOME);
	}

	/** Copia dueño, órdenes, paga y aspecto a otro NPC (al entrenar un recluta como soldado). */
	public void copyNpcStateTo(AbstractNpcEntity other) {
		other.ownerUuid = ownerUuid;
		other.order = order;
		other.home = home;
		other.stayPos = stayPos;
		other.upkeepTimer = upkeepTimer;
		other.unpaidDays = unpaidDays;
		for (int i = 0; i < provisions.size(); i++) {
			other.provisions.setStack(i, provisions.getStack(i).copy());
			provisions.setStack(i, ItemStack.EMPTY);
		}
		other.setVariant(getVariant());
	}

	// ------------------------------------------------------------------ paga

	public SimpleInventory getProvisions() {
		return provisions;
	}

	public int getUnpaidDays() {
		return unpaidDays;
	}

	/** Días de paga que tiene guardados (1 provisión = 1 día). */
	public int getProvisionDays() {
		int days = 0;
		for (int i = 0; i < provisions.size(); i++) {
			ItemStack stack = provisions.getStack(i);
			if (stack.isIn(UPKEEP)) {
				days += stack.getCount();
			}
		}
		return days;
	}

	public static boolean isUpkeepItem(ItemStack stack) {
		return stack.isIn(UPKEEP);
	}

	private boolean consumeProvision() {
		for (int i = 0; i < provisions.size(); i++) {
			ItemStack stack = provisions.getStack(i);
			if (stack.isIn(UPKEEP)) {
				stack.decrement(1);
				provisions.markDirty();
				return true;
			}
		}
		return false;
	}

	@Override
	public void tick() {
		super.tick();
		if (!getWorld().isClient && isAlive()) {
			// Solo cobra cuando sirve a alguien.
			if (ownerUuid != null) {
				tickUpkeep();
			}
			// Bien pagado, se cura poco a poco.
			if (unpaidDays == 0 && age % 100 == 0 && getHealth() < getMaxHealth()) {
				heal(1.0F);
			}
		}
	}

	private void tickUpkeep() {
		if (--upkeepTimer > 0) {
			return;
		}
		upkeepTimer = UPKEEP_INTERVAL;
		// intendencia (Liderazgo de su dueño): algunos días no gasta su paga
		com.evolvedwarfare.skill.Perk quartermaster = com.evolvedwarfare.skill.Perk.QUARTERMASTER;
		float free = quartermaster.value(com.evolvedwarfare.skill.Skills.rank(getWorld(), ownerUuid, quartermaster)) / 100.0F;
		if (free > 0.0F && getRandom().nextFloat() < free) {
			unpaidDays = 0;
			return;
		}
		if (consumeProvision()) {
			if (getServer() != null && getServer().getPlayerManager().getPlayer(ownerUuid) instanceof net.minecraft.server.network.ServerPlayerEntity owner) {
				com.evolvedwarfare.skill.Skills.gain(owner, com.evolvedwarfare.skill.Skill.LEADERSHIP, 4.0F);
			}
			unpaidDays = 0;
			if (getProvisionDays() == 0) {
				notifyOwner(Text.translatable("message.evolvedwarfare.last_provision", getName()));
			}
			return;
		}
		unpaidDays++;
		if (unpaidDays >= maxUnpaidDays()) {
			notifyOwner(Text.translatable("message.evolvedwarfare.deserted", getName()));
			desert();
		} else {
			notifyOwner(Text.translatable("message.evolvedwarfare.unpaid", getName(), maxUnpaidDays() - unpaidDays));
		}
	}

	/** Se marcha: deja en el suelo todo lo que llevaba. */
	private void desert() {
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			ItemStack stack = getEquippedStack(slot);
			if (!stack.isEmpty()) {
				dropStack(stack.copy());
				equipStack(slot, ItemStack.EMPTY);
			}
		}
		dropProvisions();
		discard();
	}

	private void dropProvisions() {
		for (int i = 0; i < provisions.size(); i++) {
			ItemStack stack = provisions.removeStack(i);
			if (!stack.isEmpty()) {
				dropStack(stack);
			}
		}
	}

	protected void notifyOwner(Text message) {
		PlayerEntity owner = getOwner();
		if (owner != null) {
			owner.sendMessage(message, false);
		}
	}

	// ------------------------------------------------------------------ interacción

	@Override
	protected ActionResult interactMob(PlayerEntity player, Hand hand) {
		World world = getWorld();
		ItemStack held = player.getStackInHand(hand);
		if (isForHire()) {
			if (!world.isClient) {
				if (held.getItem() instanceof RecruitmentContractItem) {
					hire(player, held);
				} else {
					player.sendMessage(Text.translatable("message.evolvedwarfare.for_hire", getName()), true);
				}
			}
			return ActionResult.success(world.isClient);
		}
		if (!isOwner(player)) {
			if (!world.isClient) {
				player.sendMessage(Text.translatable("message.evolvedwarfare.not_yours", getName()), true);
			}
			return ActionResult.success(world.isClient);
		}
		if (!player.isSneaking() && isUpkeepItem(held)) {
			// Clic con comida/monedas: se guarda directamente como provisiones.
			if (!world.isClient) {
				ItemStack rest = provisions.addStack(held.copy());
				int added = held.getCount() - rest.getCount();
				if (added > 0) {
					if (!player.isCreative()) {
						held.decrement(added);
					}
					player.sendMessage(Text.translatable("message.evolvedwarfare.fed", getName(), getProvisionDays()), true);
				} else {
					player.sendMessage(Text.translatable("message.evolvedwarfare.provisions_full", getName()), true);
				}
			}
			return ActionResult.success(world.isClient);
		}
		if (!world.isClient && player instanceof ServerPlayerEntity serverPlayer) {
			openMenu(serverPlayer);
		}
		return ActionResult.success(world.isClient);
	}

	/** Firma el contrato: pasa a servir al jugador y lo sigue. */
	private void hire(PlayerEntity player, ItemStack contract) {
		ownerUuid = player.getUuid();
		home = null;
		unpaidDays = 0;
		upkeepTimer = UPKEEP_INTERVAL;
		setOrder(NpcOrder.FOLLOW);
		contract.decrementUnlessCreative(1, player);
		playSound(ModSounds.RECRUIT_YES, 1.0F, getSoundPitch());
		if (getWorld() instanceof ServerWorld serverWorld) {
			serverWorld.spawnParticles(ParticleTypes.HAPPY_VILLAGER, getX(), getBodyY(0.8), getZ(), 10, 0.4, 0.5, 0.4, 0.0);
		}
		player.sendMessage(Text.translatable("message.evolvedwarfare.recruited", getName()), true);
	}

	private void openMenu(ServerPlayerEntity player) {
		AbstractNpcEntity npc = this;
		player.openHandledScreen(new ExtendedScreenHandlerFactory<Integer>() {
			@Override
			public Integer getScreenOpeningData(ServerPlayerEntity serverPlayer) {
				return npc.getId();
			}

			@Override
			public Text getDisplayName() {
				return npc.getName();
			}

			@Override
			public ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity playerEntity) {
				return new NpcScreenHandler(syncId, playerInventory, npc);
			}
		});
	}

	// ------------------------------------------------------------------ daño, muerte, despawn

	@Override
	public boolean damage(DamageSource source, float amount) {
		// Su dueño no puede herirlo por accidente.
		if (source.getAttacker() instanceof PlayerEntity player && isOwner(player)) {
			return false;
		}
		return super.damage(source, amount);
	}

	@Override
	public void onDeath(DamageSource source) {
		if (!getWorld().isClient) {
			notifyOwner(Text.translatable("message.evolvedwarfare.died", getName()));
		}
		super.onDeath(source);
	}

	@Override
	protected void dropInventory() {
		super.dropInventory();
		dropProvisions();
	}

	/** El equipo que le das siempre se recupera entero al morir. */
	@Override
	protected float getDropChance(EquipmentSlot slot) {
		return 2.0F;
	}

	@Override
	public boolean canImmediatelyDespawn(double distanceSquared) {
		return false;
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return ModSounds.RECRUIT.ambient();
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return ModSounds.RECRUIT.hurt();
	}

	@Override
	protected SoundEvent getDeathSound() {
		return ModSounds.RECRUIT.death();
	}

	@Override
	public float getSoundPitch() {
		return ModSounds.pitch(this);
	}

	/** Tu gente habla poco: cada 15 s o más. */
	@Override
	public int getMinAmbientSoundDelay() {
		return 300;
	}

	// ------------------------------------------------------------------ guardado

	@Override
	public void writeCustomDataToNbt(NbtCompound nbt) {
		super.writeCustomDataToNbt(nbt);
		if (ownerUuid != null) {
			nbt.putUuid("Owner", ownerUuid);
		}
		nbt.putString("Order", order.name());
		if (home != null) {
			nbt.putInt("HomeX", home.getX());
			nbt.putInt("HomeY", home.getY());
			nbt.putInt("HomeZ", home.getZ());
		}
		if (stayPos != null) {
			nbt.putInt("StayX", stayPos.getX());
			nbt.putInt("StayY", stayPos.getY());
			nbt.putInt("StayZ", stayPos.getZ());
		}
		nbt.putInt("Variant", getVariant());
		nbt.putInt("UpkeepTimer", upkeepTimer);
		nbt.putInt("UnpaidDays", unpaidDays);
		nbt.put("Provisions", provisions.toNbtList(getRegistryManager()));
	}

	@Override
	public void readCustomDataFromNbt(NbtCompound nbt) {
		super.readCustomDataFromNbt(nbt);
		ownerUuid = nbt.containsUuid("Owner") ? nbt.getUuid("Owner") : null;
		order = NpcOrder.byName(nbt.getString("Order"));
		home = nbt.contains("HomeX") ? new BlockPos(nbt.getInt("HomeX"), nbt.getInt("HomeY"), nbt.getInt("HomeZ")) : null;
		stayPos = nbt.contains("StayX") ? new BlockPos(nbt.getInt("StayX"), nbt.getInt("StayY"), nbt.getInt("StayZ")) : null;
		setVariant(nbt.getInt("Variant"));
		upkeepTimer = nbt.contains("UpkeepTimer") ? nbt.getInt("UpkeepTimer") : UPKEEP_INTERVAL;
		unpaidDays = nbt.getInt("UnpaidDays");
		provisions.readNbtList(nbt.getList("Provisions", 10), getRegistryManager());
	}
}
