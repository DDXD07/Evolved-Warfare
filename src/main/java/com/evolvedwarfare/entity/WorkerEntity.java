package com.evolvedwarfare.entity;

import com.evolvedwarfare.block.WorkSiteBlockEntity;
import com.evolvedwarfare.entity.ai.WorkGoal;
import com.evolvedwarfare.registry.ModEntities;
import com.evolvedwarfare.work.Job;
import com.evolvedwarfare.work.Profession;
import com.evolvedwarfare.work.WorkAction;
import com.evolvedwarfare.work.WorkContext;
import com.evolvedwarfare.work.WorkStatus;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.ai.goal.FleeEntityGoal;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * Trabajador: un recluta con oficio y puesto de trabajo. Trabaja de día en la zona de su puesto y guarda allí lo que
 * produce; de noche descansa junto al puesto. Huye de los monstruos y bandidos. Si no cobra, hace huelga.
 *
 * <p>Si le quitan el puesto (lo rompen o lo despiden) vuelve a ser recluta, con todo lo suyo.</p>
 */
public class WorkerEntity extends AbstractNpcEntity {
	private static final TrackedData<Integer> PROFESSION =
			DataTracker.registerData(WorkerEntity.class, TrackedDataHandlerRegistry.INTEGER);
	private static final long IGNORE_TICKS = 600;

	@Nullable
	private BlockPos sitePos;
	private int jobsDone;
	@Nullable
	private Job job;
	private WorkStatus status = WorkStatus.WAITING;
	private long lastActive = Long.MIN_VALUE / 2;
	private final Map<Long, Long> ignoredBlocks = new HashMap<>();
	private final Map<Integer, Long> ignoredEntities = new HashMap<>();

	public WorkerEntity(EntityType<? extends WorkerEntity> type, World world) {
		super(type, world);
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {
		super.initDataTracker(builder);
		builder.add(PROFESSION, 0);
	}

	@Override
	protected void initGoals() {
		super.initGoals();
		goalSelector.add(1, new FleeEntityGoal<>(this, HostileEntity.class, 10.0F, 0.9, 1.25));
		goalSelector.add(2, new WorkGoal(this));
	}

	@Override
	protected void initAppearance(Random random) {
		NpcNames.randomize(this, random);
	}

	// ------------------------------------------------------------------ oficio y puesto

	public Profession getProfession() {
		return Profession.byOrdinal(getDataTracker().get(PROFESSION));
	}

	private void setProfession(Profession profession) {
		getDataTracker().set(PROFESSION, profession.ordinal());
		job = null;
	}

	public Job getJob() {
		if (job == null) {
			job = getProfession().createJob();
		}
		return job;
	}

	/** Lo asigna a un puesto: ese es su sitio (su "casa"). */
	public void assignTo(BlockPos site, Profession profession) {
		setProfession(profession);
		sitePos = site.toImmutable();
		setHomeAt(sitePos);
	}

	@Nullable
	public BlockPos getSitePos() {
		return sitePos;
	}

	public int getJobsDone() {
		return jobsDone;
	}

	public WorkStatus getWorkStatus() {
		return status;
	}

	public void setWorkStatus(WorkStatus status) {
		this.status = status;
	}

	/** Contexto para trabajar ahora, o null si su puesto ya no es suyo. */
	@Nullable
	public WorkContext createContext() {
		if (sitePos == null || !(getWorld() instanceof ServerWorld world)) {
			return null;
		}
		if (!(world.getBlockEntity(sitePos) instanceof WorkSiteBlockEntity site) || !getUuid().equals(site.getWorkerUuid())) {
			return null;
		}
		return new WorkContext(world, this, site, getProfession());
	}

	/** ¿Puede trabajar ahora? Si no, deja el motivo en su estado. */
	public boolean mayWork() {
		if (sitePos == null) {
			return false;
		}
		if (getOrder() != NpcOrder.HOME) {
			status = WorkStatus.AWAY;
			return false;
		}
		if (getUnpaidDays() > 0) {
			status = WorkStatus.UNPAID;
			return false;
		}
		if (isRestTime()) {
			status = WorkStatus.RESTING;
			return false;
		}
		return true;
	}

	/** De noche descansa (en el Nether y el End no hay noche). */
	private boolean isRestTime() {
		World world = getWorld();
		if (world.getDimension().hasFixedTime()) {
			return false;
		}
		long time = world.getTimeOfDay() % 24000L;
		return time >= 13000L && time < 23000L;
	}

	/** Mientras trabaja no se vuelve a casa ni se pone a pasear. */
	@Override
	public boolean isBusy() {
		return getWorld().getTime() - lastActive < 40;
	}

	public void markBusy() {
		lastActive = getWorld().getTime();
	}

	public void onJobDone() {
		jobsDone++;
	}

	/** Gasta la herramienta (si se rompe, sigue trabajando sin ella, más despacio). */
	public void damageTool(int amount) {
		ItemStack tool = getMainHandStack();
		if (amount > 0 && !tool.isEmpty() && tool.isDamageable()) {
			tool.damage(amount, this, EquipmentSlot.MAINHAND);
		}
	}

	// ------------------------------------------------------------------ sitios a los que no llega

	public boolean isIgnored(BlockPos pos) {
		return ignoredBlocks.containsKey(pos.asLong());
	}

	public boolean isIgnored(Entity entity) {
		return ignoredEntities.containsKey(entity.getId());
	}

	public void ignore(WorkAction action) {
		long until = getWorld().getTime() + IGNORE_TICKS;
		Entity entity = action.entity();
		if (entity != null) {
			ignoredEntities.put(entity.getId(), until);
		} else {
			ignoredBlocks.put(action.walkTo().asLong(), until);
		}
	}

	// ------------------------------------------------------------------ cada segundo

	@Override
	public void tick() {
		super.tick();
		if (isAlive() && age % 20 == 0 && getWorld() instanceof ServerWorld world) {
			tickSite(world);
		}
	}

	private void tickSite(ServerWorld world) {
		if (sitePos == null) {
			// sin puesto (p. ej. invocado con /summon): vuelve a ser recluta
			if (age > 60) {
				quitJob(false);
			}
			return;
		}
		if (!world.isChunkLoaded(sitePos)) {
			return;
		}
		BlockEntity blockEntity = world.getBlockEntity(sitePos);
		if (!(blockEntity instanceof WorkSiteBlockEntity site) || !getUuid().equals(site.getWorkerUuid())
				|| site.getProfession() != getProfession()) {
			quitJob(true);
			return;
		}
		if (mayWork()) {
			if (status == WorkStatus.AWAY || status == WorkStatus.UNPAID || status == WorkStatus.RESTING) {
				status = WorkStatus.WAITING;
			}
		}
		equipSpareTool(site);
		site.heartbeat(world.getTime(), getId(), status, jobsDone);
		long now = world.getTime();
		ignoredBlocks.values().removeIf(until -> until < now);
		ignoredEntities.values().removeIf(until -> until < now);
	}

	/** Si tiene la mano vacía y en los suministros hay una herramienta de su oficio, la coge. */
	private void equipSpareTool(WorkSiteBlockEntity site) {
		Profession profession = getProfession();
		if (!profession.usesTool() || !getMainHandStack().isEmpty()) {
			return;
		}
		for (int i = 0; i < site.getSupplySlots(); i++) {
			ItemStack stack = site.getStack(i);
			if (profession.isTool(stack)) {
				equipStack(EquipmentSlot.MAINHAND, stack.split(1));
				site.markDirty();
				return;
			}
		}
	}

	/** Deja el oficio y vuelve a ser recluta (se queda donde está). */
	@Nullable
	public RecruitEntity quitJob(boolean notify) {
		RecruitEntity recruit = convertTo(ModEntities.RECRUIT, true);
		if (recruit == null) {
			return null;
		}
		copyNpcStateTo(recruit);
		recruit.setHomeAt(recruit.getBlockPos());
		if (notify) {
			recruit.notifyOwner(Text.translatable("message.evolvedwarfare.worker.lost_site", recruit.getName()));
		}
		return recruit;
	}

	@Override
	protected Text getDefaultName() {
		return Text.translatable(getProfession().translationKey());
	}

	// ------------------------------------------------------------------ guardado

	@Override
	public void writeCustomDataToNbt(NbtCompound nbt) {
		super.writeCustomDataToNbt(nbt);
		nbt.putString("Profession", getProfession().id());
		if (sitePos != null) {
			nbt.putInt("SiteX", sitePos.getX());
			nbt.putInt("SiteY", sitePos.getY());
			nbt.putInt("SiteZ", sitePos.getZ());
		}
		nbt.putInt("JobsDone", jobsDone);
	}

	@Override
	public void readCustomDataFromNbt(NbtCompound nbt) {
		super.readCustomDataFromNbt(nbt);
		setProfession(Profession.byId(nbt.getString("Profession"), Profession.FARMER));
		sitePos = nbt.contains("SiteX") ? new BlockPos(nbt.getInt("SiteX"), nbt.getInt("SiteY"), nbt.getInt("SiteZ")) : null;
		jobsDone = nbt.getInt("JobsDone");
	}
}
