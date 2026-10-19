package com.evolvedwarfare.screen;

import com.evolvedwarfare.block.WorkSiteBlock;
import com.evolvedwarfare.block.WorkSiteBlockEntity;
import com.evolvedwarfare.building.BuildState;
import com.evolvedwarfare.registry.ModScreens;
import com.evolvedwarfare.work.Profession;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ArrayPropertyDelegate;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

/**
 * Menú del edificio de un puesto: vista previa en 3D del diseño de la semilla actual, botones para otra semilla, volver a
 * la anterior, construir o trabajar sin edificio, y el avance de la obra. No tiene ranuras.
 */
public class BlueprintScreenHandler extends ScreenHandler {
	public static final int BUTTON_NEW_SEED = 0;
	public static final int BUTTON_PREVIOUS = 1;
	public static final int BUTTON_BUILD = 2;
	public static final int BUTTON_SKIP = 3;
	public static final int BUTTON_CANCEL = 4;
	public static final int BUTTON_SITE = 5;

	public static final int P_SEED_LO = 0;
	public static final int P_SEED_HI = 1;
	public static final int P_STATE = 2;
	public static final int P_HISTORY = 3;
	public static final int P_PLACED = 4;
	public static final int P_TOTAL = 5;
	public static final int P_NOTE = 6;
	public static final int PROPERTY_COUNT = 7;

	@Nullable
	private final WorkSiteBlockEntity site;
	private final PropertyDelegate properties;
	private final Profession profession;
	private final BlockPos pos;

	/** Cliente: recibe la posición del puesto. */
	public BlueprintScreenHandler(int syncId, PlayerInventory playerInventory, BlockPos pos) {
		this(syncId, null, new ArrayPropertyDelegate(PROPERTY_COUNT), professionAt(playerInventory.player, pos), pos);
	}

	/** Servidor. */
	public BlueprintScreenHandler(int syncId, PlayerInventory playerInventory, WorkSiteBlockEntity site) {
		this(syncId, site, site.blueprintProperties, site.getProfession(), site.getPos());
	}

	private BlueprintScreenHandler(int syncId, @Nullable WorkSiteBlockEntity site, PropertyDelegate properties,
			Profession profession, BlockPos pos) {
		super(ModScreens.BLUEPRINT, syncId);
		checkDataCount(properties, PROPERTY_COUNT);
		this.site = site;
		this.properties = properties;
		this.profession = profession;
		this.pos = pos.toImmutable();
		addProperties(properties);
	}

	private static Profession professionAt(PlayerEntity player, BlockPos pos) {
		BlockState state = player.getWorld().getBlockState(pos);
		return state.getBlock() instanceof WorkSiteBlock block ? block.getProfession() : Profession.FARMER;
	}

	@Override
	public boolean onButtonClick(PlayerEntity player, int id) {
		if (site == null || !(player instanceof ServerPlayerEntity serverPlayer) || !site.canAccess(player)) {
			return false;
		}
		switch (id) {
			case BUTTON_NEW_SEED -> site.newSeed();
			case BUTTON_PREVIOUS -> site.previousSeed();
			case BUTTON_BUILD -> site.confirmBuilding();
			case BUTTON_SKIP -> site.skipBuilding();
			case BUTTON_CANCEL -> site.cancelBuilding();
			case BUTTON_SITE -> serverPlayer.openHandledScreen(site);
			default -> {
				return false;
			}
		}
		return true;
	}

	@Override
	public boolean canUse(PlayerEntity player) {
		if (site == null) {
			return true;
		}
		return !site.isRemoved() && site.canAccess(player) && player.squaredDistanceTo(Vec3d.ofCenter(pos)) <= 64.0;
	}

	@Override
	public ItemStack quickMove(PlayerEntity player, int slot) {
		return ItemStack.EMPTY;
	}

	// ------------------------------------------------------------------ datos para la pantalla

	public Profession getProfession() {
		return profession;
	}

	public BlockPos getPos() {
		return pos;
	}

	/** Hacia dónde mira el puesto (para orientar la vista previa igual que en el mundo). */
	public Direction facingIn(net.minecraft.world.World world) {
		BlockState state = world.getBlockState(pos);
		return state.contains(WorkSiteBlock.FACING) ? state.get(WorkSiteBlock.FACING) : Direction.NORTH;
	}

	public int seed() {
		return ((properties.get(P_SEED_HI) & 0xFFFF) << 16) | (properties.get(P_SEED_LO) & 0xFFFF);
	}

	public BuildState state() {
		return BuildState.byOrdinal(properties.get(P_STATE));
	}

	public int historySize() {
		return properties.get(P_HISTORY);
	}

	public int placed() {
		return properties.get(P_PLACED);
	}

	public int total() {
		return properties.get(P_TOTAL);
	}

	public int note() {
		return properties.get(P_NOTE);
	}
}
