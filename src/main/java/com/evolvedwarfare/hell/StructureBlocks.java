package com.evolvedwarfare.hell;

import com.mojang.serialization.MapCodec;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.Entity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.AxeItem;
import net.minecraft.item.BowItem;
import net.minecraft.item.CrossbowItem;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.SwordItem;
import net.minecraft.item.TridentItem;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.IntProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.ItemActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/** Los bloques de las estructuras del Nether bíblico (fase 35). */
public final class StructureBlocks {
	private StructureBlocks() {
	}

	/** Un bloque decorativo con orientación (alas rotas, espadas, trono, ídolo...). */
	public static class Facing extends Block {
		public static final DirectionProperty FACING = Properties.HORIZONTAL_FACING;
		public static final MapCodec<Facing> CODEC = createCodec(Facing::new);
		private final VoxelShape shape;
		private final boolean solid;

		public Facing(Settings settings) {
			this(settings, VoxelShapes.fullCube(), true);
		}

		public Facing(Settings settings, VoxelShape shape, boolean solid) {
			super(settings);
			this.shape = shape;
			this.solid = solid;
			setDefaultState(getStateManager().getDefaultState().with(FACING, Direction.NORTH));
		}

		@Override
		protected MapCodec<? extends Block> getCodec() {
			return CODEC;
		}

		@Override
		protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
			builder.add(FACING);
		}

		@Override
		public BlockState getPlacementState(ItemPlacementContext ctx) {
			return getDefaultState().with(FACING, ctx.getHorizontalPlayerFacing().getOpposite());
		}

		@Override
		protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
			return shape;
		}

		@Override
		protected VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
			return solid ? shape : VoxelShapes.empty();
		}
	}

	// ------------------------------------------------------------------ Babel

	/** Runa de Babel: cambia de forma (y de lengua) cuando te acercas. */
	public static class Rune extends Block {
		public static final IntProperty GLYPH = IntProperty.of("glyph", 0, 7);
		public static final MapCodec<Rune> CODEC = createCodec(Rune::new);

		public Rune(Settings settings) {
			super(settings);
			setDefaultState(getStateManager().getDefaultState().with(GLYPH, 0));
		}

		@Override
		protected MapCodec<? extends Block> getCodec() {
			return CODEC;
		}

		@Override
		protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
			builder.add(GLYPH);
		}

		@Override
		public void randomDisplayTick(BlockState state, World world, BlockPos pos, Random random) {
			if (random.nextInt(4) == 0) {
				world.addParticle(ParticleTypes.ENCHANT, pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5, random.nextGaussian() * 0.4, -0.6,
						random.nextGaussian() * 0.4);
			}
		}
	}

	/** Suelo agrietado: al pisarlo cruje y, poco después, se hunde (y arrastra a los de al lado). Agachado aguanta. */
	public static class Crumbling extends Block {
		public static final MapCodec<Crumbling> CODEC = createCodec(Crumbling::new);
		public static final int DELAY = 12;

		public Crumbling(Settings settings) {
			super(settings);
		}

		@Override
		protected MapCodec<? extends Block> getCodec() {
			return CODEC;
		}

		@Override
		public void onSteppedOn(World world, BlockPos pos, BlockState state, Entity entity) {
			if (!world.isClient && !entity.bypassesSteppingEffects() && !world.getBlockTickScheduler().isQueued(pos, this)) {
				crack(world, pos);
			}
			super.onSteppedOn(world, pos, state, entity);
		}

		public static void crack(World world, BlockPos pos) {
			Block block = world.getBlockState(pos).getBlock();
			if (block instanceof Crumbling && !world.getBlockTickScheduler().isQueued(pos, block)) {
				world.scheduleBlockTick(pos, block, DELAY);
				world.playSound(null, pos, SoundEvents.BLOCK_POINTED_DRIPSTONE_LAND, SoundCategory.BLOCKS, 0.8F, 0.6F);
			}
		}

		@Override
		protected void scheduledTick(BlockState state, ServerWorld world, BlockPos pos, Random random) {
			world.breakBlock(pos, false);
			for (Direction d : Direction.Type.HORIZONTAL) {
				BlockPos n = pos.offset(d);
				if (world.getBlockState(n).getBlock() instanceof Crumbling && !world.getBlockTickScheduler().isQueued(n, this)) {
					world.scheduleBlockTick(n, this, 4 + random.nextInt(6));
				}
			}
		}
	}

	/** Bloque cambiante: aparece y desaparece (cuando no es sólido se ve como un fantasma y se atraviesa). */
	public static class Shifting extends Block {
		public static final BooleanProperty SOLID = BooleanProperty.of("solid");
		public static final MapCodec<Shifting> CODEC = createCodec(Shifting::new);

		public Shifting(Settings settings) {
			super(settings);
			setDefaultState(getStateManager().getDefaultState().with(SOLID, true));
		}

		@Override
		protected MapCodec<? extends Block> getCodec() {
			return CODEC;
		}

		@Override
		protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
			builder.add(SOLID);
		}

		@Override
		protected VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
			return state.get(SOLID) ? VoxelShapes.fullCube() : VoxelShapes.empty();
		}

		@Override
		protected boolean isTransparent(BlockState state, BlockView world, BlockPos pos) {
			return !state.get(SOLID);
		}
	}

	/** La puerta de piedra de lo alto de Babel: no lleva a ninguna parte... salvo a una visión. */
	public static class VisionGate extends Block {
		public static final MapCodec<VisionGate> CODEC = createCodec(VisionGate::new);
		private static final Map<UUID, Long> NEXT = new HashMap<>();

		public VisionGate(Settings settings) {
			super(settings);
		}

		@Override
		protected MapCodec<? extends Block> getCodec() {
			return CODEC;
		}

		@Override
		protected VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
			return VoxelShapes.empty();
		}

		@Override
		protected void onEntityCollision(BlockState state, World world, BlockPos pos, Entity entity) {
			if (world instanceof ServerWorld sw && entity instanceof ServerPlayerEntity player) {
				long now = sw.getTime();
				Long next = NEXT.get(player.getUuid());
				if (next != null && now < next) {
					return;
				}
				NEXT.put(player.getUuid(), now + 200);
				HellStructures.vision(sw, player, pos);
			}
		}

		@Override
		public void randomDisplayTick(BlockState state, World world, BlockPos pos, Random random) {
			world.addParticle(ParticleTypes.REVERSE_PORTAL, pos.getX() + random.nextDouble(), pos.getY() + random.nextDouble(), pos.getZ() + random.nextDouble(),
					0, 0, 0);
		}
	}

	// ------------------------------------------------------------------ los templos

	/** El altar de un falso dios: acepta ofrendas (comida, oro, libros, armas) y da fuerza... o una maldición. */
	public static class Altar extends Block {
		public static final MapCodec<Altar> CODEC = createCodec(Altar::new);
		private static final VoxelShape SHAPE = VoxelShapes.cuboid(0, 0, 0, 1, 14 / 16.0, 1);
		private static final Map<UUID, Long> NEXT = new HashMap<>();
		public static final int COOLDOWN = 600;

		public Altar(Settings settings) {
			super(settings);
		}

		@Override
		protected MapCodec<? extends Block> getCodec() {
			return CODEC;
		}

		@Override
		protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
			return SHAPE;
		}

		/** Lo que vale la ofrenda (0 = no la quiere). */
		public static int value(ItemStack stack) {
			if (stack.isEmpty()) {
				return 0;
			}
			String path = Registries.ITEM.getId(stack.getItem()).getPath();
			if (stack.isOf(Items.GOLD_BLOCK) || stack.isOf(Items.ENCHANTED_GOLDEN_APPLE)) {
				return 5;
			}
			if (stack.isOf(Items.ENCHANTED_BOOK) || stack.isOf(Items.WRITTEN_BOOK) || stack.isOf(Items.GOLDEN_APPLE)) {
				return 4;
			}
			if (stack.getItem() instanceof SwordItem || stack.getItem() instanceof AxeItem || stack.getItem() instanceof TridentItem
					|| stack.getItem() instanceof BowItem || stack.getItem() instanceof CrossbowItem) {
				return stack.hasEnchantments() ? 4 : 3;
			}
			if (path.contains("gold")) {
				return path.contains("nugget") ? 1 : 3;
			}
			if (stack.isOf(Items.BOOK) || stack.isOf(Items.WRITABLE_BOOK) || stack.getItem() instanceof BookOfCondemnationItem) {
				return 2;
			}
			if (stack.contains(DataComponentTypes.FOOD)) {
				return 1;
			}
			return 0;
		}

		@Override
		protected ItemActionResult onUseWithItem(ItemStack stack, BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand,
				BlockHitResult hit) {
			if (stack.getItem() instanceof BlessedSaltItem) {
				if (world instanceof ServerWorld sw && player instanceof ServerPlayerEntity sp) {
					purify(sw, sp, pos);
					stack.decrementUnlessCreative(1, sp);
				}
				return ItemActionResult.success(world.isClient);
			}
			int value = value(stack);
			if (value == 0) {
				if (!world.isClient) {
					player.sendMessage(Text.translatable("message.evolvedwarfare.altar.refused").formatted(Formatting.GRAY), true);
				}
				return ItemActionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
			}
			if (world instanceof ServerWorld sw && player instanceof ServerPlayerEntity sp) {
				Long next = NEXT.get(sp.getUuid());
				if (next != null && sw.getTime() < next) {
					sp.sendMessage(Text.translatable("message.evolvedwarfare.altar.wait").formatted(Formatting.GRAY), true);
					return ItemActionResult.CONSUME;
				}
				NEXT.put(sp.getUuid(), sw.getTime() + COOLDOWN);
				stack.decrementUnlessCreative(1, sp);
				offer(sw, sp, pos, value, sw.random);
			}
			return ItemActionResult.success(world.isClient);
		}

		/** Hace la ofrenda: devuelve si fue un don (si no, maldición). */
		public static boolean offer(ServerWorld world, ServerPlayerEntity player, BlockPos pos, int value, Random random) {
			double chance = 0.45 + value * 0.08 - NetherClimate.corruption(player) / 250.0;
			boolean boon = random.nextDouble() < chance;
			double x = pos.getX() + 0.5, y = pos.getY() + 1.0, z = pos.getZ() + 0.5;
			if (boon) {
				int ticks = 20 * (90 + value * 45);
				int amp = value >= 4 ? 1 : 0;
				StatusEffectInstance[] boons = {
						new StatusEffectInstance(StatusEffects.STRENGTH, ticks, amp),
						new StatusEffectInstance(StatusEffects.RESISTANCE, ticks, amp),
						new StatusEffectInstance(StatusEffects.REGENERATION, ticks / 3, amp),
						new StatusEffectInstance(StatusEffects.SPEED, ticks, amp + 1),
						new StatusEffectInstance(StatusEffects.FIRE_RESISTANCE, ticks * 2, 0),
						new StatusEffectInstance(StatusEffects.HASTE, ticks, amp + 1)};
				player.addStatusEffect(boons[random.nextInt(boons.length)]);
				world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, x, y, z, 30, 0.3, 0.5, 0.3, 0.03);
				world.playSound(null, pos, SoundEvents.BLOCK_RESPAWN_ANCHOR_CHARGE, SoundCategory.BLOCKS, 1.0F, 0.7F);
				player.sendMessage(Text.translatable("message.evolvedwarfare.altar.boon").formatted(Formatting.GOLD), true);
				NetherClimate.addCorruption(player, 2);
			} else {
				Curses.curse(player, Curses.random(random));
				player.addStatusEffect(new StatusEffectInstance(StatusEffects.DARKNESS, 100, 0));
				world.spawnParticles(ParticleTypes.SQUID_INK, x, y, z, 30, 0.3, 0.5, 0.3, 0.05);
				world.playSound(null, pos, SoundEvents.ENTITY_WARDEN_HEARTBEAT, SoundCategory.BLOCKS, 1.4F, 0.6F);
				player.sendMessage(Text.translatable("message.evolvedwarfare.altar.curse").formatted(Formatting.DARK_RED), true);
				NetherClimate.addCorruption(player, 6);
			}
			return boon;
		}

		/** El ritual de purificación: sal bendita en el altar quita todas las maldiciones y baja mucho la corrupción. */
		public static void purify(ServerWorld world, ServerPlayerEntity player, BlockPos pos) {
			Curses.lift(player);
			NetherClimate.addCorruption(player, -25);
			NEXT.remove(player.getUuid());
			world.spawnParticles(ParticleTypes.END_ROD, pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5, 50, 0.4, 0.8, 0.4, 0.05);
			world.playSound(null, pos, SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.BLOCKS, 1.0F, 1.4F);
			player.sendMessage(Text.translatable("message.evolvedwarfare.altar.purified").formatted(Formatting.AQUA), true);
		}
	}

	/** Un ídolo de piedra: su cabeza (la dibuja el cliente) gira para mirarte. */
	public static class Idol extends BlockWithEntity {
		public static final DirectionProperty FACING = Properties.HORIZONTAL_FACING;
		public static final MapCodec<Idol> CODEC = createCodec(Idol::new);
		private static final VoxelShape SHAPE = VoxelShapes.cuboid(2 / 16.0, 0, 2 / 16.0, 14 / 16.0, 15 / 16.0, 14 / 16.0);

		public Idol(Settings settings) {
			super(settings);
			setDefaultState(getStateManager().getDefaultState().with(FACING, Direction.NORTH));
		}

		@Override
		protected MapCodec<? extends BlockWithEntity> getCodec() {
			return CODEC;
		}

		@Override
		protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
			builder.add(FACING);
		}

		@Override
		public BlockState getPlacementState(ItemPlacementContext ctx) {
			return getDefaultState().with(FACING, ctx.getHorizontalPlayerFacing().getOpposite());
		}

		@Override
		protected BlockRenderType getRenderType(BlockState state) {
			return BlockRenderType.MODEL;
		}

		@Override
		protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
			return SHAPE;
		}

		@Nullable
		@Override
		public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
			return new IdolBlockEntity(pos, state);
		}
	}

	// ------------------------------------------------------------------ los cementerios

	/** Una espada de caído clavada en el suelo: se arranca con la mano vacía (y algo se despierta). */
	public static class EmbeddedSword extends Facing {
		public static final MapCodec<EmbeddedSword> CODEC = createCodec(EmbeddedSword::new);

		public EmbeddedSword(Settings settings) {
			super(settings, VoxelShapes.cuboid(5 / 16.0, 0, 5 / 16.0, 11 / 16.0, 1, 11 / 16.0), false);
		}

		@Override
		protected MapCodec<? extends Block> getCodec() {
			return CODEC;
		}

		@Override
		protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
			if (world instanceof ServerWorld sw && player instanceof ServerPlayerEntity sp) {
				pull(sw, sp, pos);
			}
			return ActionResult.success(world.isClient);
		}

		public static ItemStack pull(ServerWorld world, ServerPlayerEntity player, BlockPos pos) {
			int kind = world.random.nextInt(FallenBladeItem.KINDS.length);
			ItemStack blade = FallenBladeItem.of(kind);
			world.removeBlock(pos, false);
			world.playSound(null, pos, SoundEvents.BLOCK_CHAIN_BREAK, SoundCategory.BLOCKS, 1.0F, 0.6F);
			world.spawnParticles(ParticleTypes.END_ROD, pos.getX() + 0.5, pos.getY() + 0.8, pos.getZ() + 0.5, 20, 0.2, 0.4, 0.2, 0.04);
			player.sendMessage(Text.translatable("message.evolvedwarfare.sword.pulled", blade.getName()).formatted(Formatting.GOLD), true);
			if (!player.giveItemStack(blade.copy())) {
				player.dropItem(blade, false);
			}
			if (world.random.nextBoolean()) {
				Curses.curse(player, Curses.random(world.random));
			}
			HellStructures.wakeNear(world, pos.toCenterPos(), 16, player);
			return blade;
		}

		@Override
		public void randomDisplayTick(BlockState state, World world, BlockPos pos, Random random) {
			if (random.nextInt(3) == 0) {
				world.addParticle(ParticleTypes.END_ROD, pos.getX() + 0.5, pos.getY() + 0.6 + random.nextDouble() * 0.4, pos.getZ() + 0.5, 0, 0.01, 0);
			}
		}
	}

	// ------------------------------------------------------------------ la Fortaleza del Abaddon

	/** El trono del Abismo: solo reconoce a quien ha caído bastantes veces en este reino; entonces abre lo sellado. */
	public static class Throne extends Facing {
		public static final MapCodec<Throne> CODEC = createCodec(Throne::new);

		public Throne(Settings settings) {
			super(settings, VoxelShapes.fullCube(), true);
		}

		@Override
		protected MapCodec<? extends Block> getCodec() {
			return CODEC;
		}

		@Override
		protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
			if (world instanceof ServerWorld sw && player instanceof ServerPlayerEntity sp) {
				HellStructures.useThrone(sw, sp, pos);
			}
			return ActionResult.success(world.isClient);
		}
	}
}
