package com.evolvedwarfare.hunting;

import com.evolvedwarfare.network.ActivityPayload;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * Un animal abatido, tumbado en el suelo: se dibuja como el animal (con su pelaje, su color...) de lado. Guarda lo que habría
 * soltado. Con un cuchillo, clic derecho y se despelleja (el que lo hace se agacha y trabaja cinco segundos): 4-10 de cuero,
 * 2-6 de su carne y lo demás que llevara (lana, plumas, huesos...). Si nadie lo despelleja, a los 5 minutos se pudre y
 * suelta lo de siempre.
 */
public class CarcassEntity extends Entity {
	private static final TrackedData<String> TYPE = DataTracker.registerData(CarcassEntity.class, TrackedDataHandlerRegistry.STRING);
	private static final TrackedData<NbtCompound> LOOK = DataTracker.registerData(CarcassEntity.class, TrackedDataHandlerRegistry.NBT_COMPOUND);
	private static final TrackedData<Float> WIDTH = DataTracker.registerData(CarcassEntity.class, TrackedDataHandlerRegistry.FLOAT);
	private static final TrackedData<Float> HEIGHT = DataTracker.registerData(CarcassEntity.class, TrackedDataHandlerRegistry.FLOAT);
	private static final TrackedData<Integer> PROGRESS = DataTracker.registerData(CarcassEntity.class, TrackedDataHandlerRegistry.INTEGER);
	/** Lo que tarda en pudrirse si nadie lo despelleja (tics). */
	public static final int ROT = 6000;
	/** Lo de las llaves que no hacen falta para dibujarlo (y que pesan). */
	private static final String[] STRIP = {"UUID", "Pos", "Motion", "Rotation", "Brain", "Attributes", "active_effects", "Passengers", "Leash",
			"CustomName", "DeathTime", "HurtTime", "HurtByTimestamp", "FallDistance", "Fire", "Air", "OnGround", "PortalCooldown", "LoveCause", "InLove",
			"Tags", "HandItems", "HandDropChances", "ArmorDropChances", "body_armor_item", "Inventory", "Items", "SaddleItem", "ArmorItem"};

	private final List<ItemStack> loot = new ArrayList<>();
	@Nullable
	private UUID skinner;

	public CarcassEntity(EntityType<?> type, World world) {
		super(type, world);
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {
		builder.add(TYPE, "minecraft:cow");
		builder.add(LOOK, new NbtCompound());
		builder.add(WIDTH, 0.9F);
		builder.add(HEIGHT, 1.4F);
		builder.add(PROGRESS, 0);
	}

	/** Del animal que acaba de morir: su tipo, su aspecto, su tamaño, dónde y hacia dónde mira, y lo que soltaría. */
	public void copyFrom(LivingEntity dead, List<ItemStack> drops) {
		dataTracker.set(TYPE, Registries.ENTITY_TYPE.getId(dead.getType()).toString());
		NbtCompound look = new NbtCompound();
		dead.writeNbt(look);
		for (String k : STRIP) {
			look.remove(k);
		}
		look.putFloat("Health", 1.0F);
		dataTracker.set(LOOK, look);
		dataTracker.set(WIDTH, dead.getWidth());
		dataTracker.set(HEIGHT, dead.getHeight());
		refreshPositionAndAngles(dead.getX(), dead.getY(), dead.getZ(), dead.getBodyYaw(), 0.0F);
		setVelocity(dead.getVelocity().multiply(0.5, 0.0, 0.5));
		loot.clear();
		for (ItemStack s : drops) {
			if (!s.isEmpty()) {
				loot.add(s.copy());
			}
		}
		calculateDimensions();
	}

	public Identifier animalType() {
		Identifier id = Identifier.tryParse(dataTracker.get(TYPE));
		return id == null ? Identifier.ofVanilla("cow") : id;
	}

	public NbtCompound look() {
		return dataTracker.get(LOOK);
	}

	/** El ancho y el alto del animal de pie. */
	public float animalWidth() {
		return dataTracker.get(WIDTH);
	}

	public float animalHeight() {
		return dataTracker.get(HEIGHT);
	}

	/** Lo que lleva despellejado (0-1). */
	public float progress() {
		return dataTracker.get(PROGRESS) / (float) Hunting.SKIN_TIME;
	}

	public List<ItemStack> loot() {
		return loot;
	}

	@Override
	public void onTrackedDataSet(TrackedData<?> data) {
		super.onTrackedDataSet(data);
		if (WIDTH.equals(data) || HEIGHT.equals(data)) {
			calculateDimensions();
		}
	}

	@Override
	public EntityDimensions getDimensions(EntityPose pose) {
		// tumbado: tan largo como alto era, y de alto lo que era de ancho
		float w = dataTracker.get(WIDTH), h = dataTracker.get(HEIGHT);
		return EntityDimensions.changing(Math.max(w, h) * 0.9F, Math.max(0.3F, Math.min(w, h) * 0.8F));
	}

	@Override
	public void tick() {
		super.tick();
		// cae y se para en el suelo
		if (!isOnGround()) {
			setVelocity(getVelocity().add(0.0, -0.04, 0.0));
		}
		move(MovementType.SELF, getVelocity());
		setVelocity(getVelocity().multiply(isOnGround() ? 0.5 : 0.98, 0.98, isOnGround() ? 0.5 : 0.98));
		if (!(getWorld() instanceof ServerWorld world)) {
			return;
		}
		if (skinner != null) {
			skinTick(world);
		} else if (age > ROT) {
			// se pudre: suelta lo de siempre
			for (ItemStack s : loot) {
				dropStack(s.copy());
			}
			discard();
		}
	}

	private ServerPlayerEntity skinningPlayer(ServerWorld world) {
		return skinner != null && world.getPlayerByUuid(skinner) instanceof ServerPlayerEntity p ? p : null;
	}

	private boolean canSkin(ServerPlayerEntity p) {
		return p != null && p.isAlive() && !p.isRemoved() && p.getWorld() == getWorld() && Hunting.isKnife(p.getMainHandStack())
				&& Math.sqrt(p.squaredDistanceTo(getBoundingBox().getCenter())) < 4.0 + Math.max(animalWidth(), animalHeight()) * 0.5;
	}

	private void skinTick(ServerWorld world) {
		ServerPlayerEntity p = skinningPlayer(world);
		if (!canSkin(p)) {
			com.evolvedwarfare.EvolvedWarfare.LOGGER.debug("despellejar parado: jugador {}, cuchillo {}, distancia {}", p, p != null ? p.getMainHandStack() : null,
					p != null ? Math.sqrt(p.squaredDistanceTo(getBoundingBox().getCenter())) : -1);
			stopSkinning(world);
			return;
		}
		int done = dataTracker.get(PROGRESS) + 1;
		dataTracker.set(PROGRESS, done);
		Vec3d c = getBoundingBox().getCenter();
		if (done % 4 == 0) {
			world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.REDSTONE_BLOCK.getDefaultState()), c.x, c.y + 0.1, c.z, 3,
					0.25, 0.1, 0.25, 0.05);
		}
		if (done % 12 == 0) {
			world.playSound(null, getBlockPos(), done % 24 == 0 ? SoundEvents.ENTITY_SHEEP_SHEAR : SoundEvents.BLOCK_HONEY_BLOCK_SLIDE,
					SoundCategory.PLAYERS, 0.7F, 0.8F + random.nextFloat() * 0.3F);
		}
		int bars = 20;
		int filled = done * bars / Hunting.SKIN_TIME;
		p.sendMessage(Text.translatable("message.evolvedwarfare.skinning", Text.literal("■".repeat(filled)).formatted(Formatting.RED)
				.append(Text.literal("■".repeat(bars - filled)).formatted(Formatting.DARK_GRAY))).formatted(Formatting.GRAY), true);
		if (done >= Hunting.SKIN_TIME) {
			finish(world, p);
		}
	}

	private void stopSkinning(ServerWorld world) {
		ServerPlayerEntity p = skinningPlayer(world);
		if (p != null) {
			ActivityPayload.set(p, ActivityPayload.NONE);
			p.sendMessage(Text.empty(), true);
		}
		skinner = null;
		dataTracker.set(PROGRESS, 0);
	}

	/** Despellejado: cuero, carne y lo demás; el cuchillo se gasta; queda la sangre. */
	private void finish(ServerWorld world, ServerPlayerEntity p) {
		Item meat = Items.BEEF;
		for (ItemStack s : loot) {
			if (Hunting.isMeat(s)) {
				meat = s.getItem();
				break;
			}
		}
		drop(new ItemStack(Items.LEATHER, 4 + random.nextInt(7)));
		drop(new ItemStack(meat, 2 + random.nextInt(5)));
		for (ItemStack s : loot) {
			if (!Hunting.isMeat(s) && !s.isOf(Items.LEATHER)) {
				drop(s.copy());
			}
		}
		p.getMainHandStack().damage(1, p, net.minecraft.entity.EquipmentSlot.MAINHAND);
		ActivityPayload.set(p, ActivityPayload.NONE);
		p.sendMessage(Text.empty(), true);
		world.playSound(null, getBlockPos(), SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.PLAYERS, 0.8F, 0.7F);
		BloodStainEntity stain = Hunting.BLOOD_STAIN.create(world);
		if (stain != null) {
			stain.refreshPositionAndAngles(getX(), getY(), getZ(), random.nextFloat() * 360.0F, 0.0F);
			world.spawnEntity(stain);
		}
		skinner = null;
		discard();
	}

	private void drop(ItemStack stack) {
		while (!stack.isEmpty()) {
			ItemStack part = stack.split(stack.getMaxCount());
			ItemEntity item = new ItemEntity(getWorld(), getX(), getY() + 0.3, getZ(), part);
			item.setVelocity(random.nextGaussian() * 0.05, 0.2, random.nextGaussian() * 0.05);
			item.setToDefaultPickupDelay();
			getWorld().spawnEntity(item);
		}
	}

	@Override
	public ActionResult interact(PlayerEntity player, Hand hand) {
		if (hand != Hand.MAIN_HAND) {
			return ActionResult.PASS;
		}
		if (!Hunting.isKnife(player.getMainHandStack())) {
			if (!getWorld().isClient) {
				player.sendMessage(Text.translatable("message.evolvedwarfare.skinning.need_knife").formatted(Formatting.GOLD), true);
			}
			return ActionResult.CONSUME;
		}
		if (getWorld() instanceof ServerWorld world && player instanceof ServerPlayerEntity sp) {
			return startSkinning(world, sp) ? ActionResult.CONSUME : ActionResult.FAIL;
		}
		return ActionResult.SUCCESS;
	}

	/** Empieza a despellejarlo (si no lo está haciendo otro). */
	public boolean startSkinning(ServerWorld world, ServerPlayerEntity player) {
		ServerPlayerEntity other = skinningPlayer(world);
		if (other != null && other != player && canSkin(other)) {
			player.sendMessage(Text.translatable("message.evolvedwarfare.skinning.busy").formatted(Formatting.RED), true);
			return false;
		}
		if (player.getUuid().equals(skinner)) {
			return true;
		}
		skinner = player.getUuid();
		dataTracker.set(PROGRESS, 0);
		ActivityPayload.set(player, ActivityPayload.SKINNING);
		return true;
	}

	@Override
	public void remove(RemovalReason reason) {
		if (getWorld() instanceof ServerWorld world && skinner != null) {
			ServerPlayerEntity p = skinningPlayer(world);
			if (p != null) {
				ActivityPayload.set(p, ActivityPayload.NONE);
			}
		}
		super.remove(reason);
	}

	@Override
	public boolean canHit() {
		return !isRemoved();
	}

	@Override
	public boolean damage(DamageSource source, float amount) {
		return false;
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	protected void readCustomDataFromNbt(NbtCompound nbt) {
		dataTracker.set(TYPE, nbt.getString("Animal"));
		dataTracker.set(LOOK, nbt.getCompound("Look"));
		dataTracker.set(WIDTH, nbt.contains("Width") ? nbt.getFloat("Width") : 0.9F);
		dataTracker.set(HEIGHT, nbt.contains("Height") ? nbt.getFloat("Height") : 1.4F);
		loot.clear();
		for (NbtElement e : nbt.getList("Loot", NbtElement.COMPOUND_TYPE)) {
			ItemStack.fromNbt(getRegistryManager(), e).ifPresent(loot::add);
		}
		calculateDimensions();
	}

	@Override
	protected void writeCustomDataToNbt(NbtCompound nbt) {
		nbt.putString("Animal", dataTracker.get(TYPE));
		nbt.put("Look", dataTracker.get(LOOK));
		nbt.putFloat("Width", dataTracker.get(WIDTH));
		nbt.putFloat("Height", dataTracker.get(HEIGHT));
		NbtList list = new NbtList();
		for (ItemStack s : loot) {
			if (!s.isEmpty()) {
				list.add(s.encode(getRegistryManager()));
			}
		}
		nbt.put("Loot", list);
	}
}
