package com.evolvedwarfare.difficulty;

import com.evolvedwarfare.EvolvedWarfare;
import com.evolvedwarfare.health.HealthItems;
import com.evolvedwarfare.health.Medicine;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;

/**
 * Los monstruos élite (fase 95): más grandes, con mucha más vida y más daño, con su nombre a la vista («★ Zombi de Fuego»)
 * y uno o más <b>rasgos</b>, cada uno con sus partículas: de Fuego (quema), de Escarcha (ralentiza), de Sangre (se cura con
 * lo que hace), de Veneno (veneno fuerte), del Viento (rápido), de Hierro (acorazado), de la Hidra (se regenera), de
 * Pólvora (explota al morir), de Furia (pega más cuanto más herido) y de Ceniza (marchita). Al matarlos sueltan mucha más
 * experiencia y botín (esmeraldas, a veces remedios y, rara vez, un cristal de corazón).
 */
public final class Elites {
	public enum Affix {
		BURNING(ParticleTypes.FLAME, Formatting.GOLD),
		FROST(ParticleTypes.SNOWFLAKE, Formatting.AQUA),
		VAMPIRIC(ParticleTypes.CRIMSON_SPORE, Formatting.DARK_RED),
		VENOMOUS(ParticleTypes.ITEM_SLIME, Formatting.GREEN),
		SWIFT(ParticleTypes.CLOUD, Formatting.WHITE),
		ARMORED(ParticleTypes.ENCHANTED_HIT, Formatting.GRAY),
		REGENERATING(ParticleTypes.HAPPY_VILLAGER, Formatting.DARK_GREEN),
		EXPLOSIVE(ParticleTypes.SMOKE, Formatting.RED),
		BERSERK(ParticleTypes.ANGRY_VILLAGER, Formatting.DARK_PURPLE),
		WITHERING(ParticleTypes.SOUL, Formatting.DARK_GRAY);

		final ParticleEffect particle;
		final Formatting color;

		Affix(ParticleEffect particle, Formatting color) {
			this.particle = particle;
			this.color = color;
		}

		public String id() {
			return name().toLowerCase(Locale.ROOT);
		}

		public Text title() {
			return Text.translatable("elite.evolvedwarfare.affix." + id());
		}

		static Affix byId(String id) {
			for (Affix a : values()) {
				if (a.id().equals(id)) {
					return a;
				}
			}
			return null;
		}
	}

	private static final Identifier HEALTH_ID = EvolvedWarfare.id("elite_health");
	private static final Identifier DAMAGE_ID = EvolvedWarfare.id("elite_damage");
	private static final Identifier SCALE_ID = EvolvedWarfare.id("elite_scale");
	private static final Identifier KNOCKBACK_ID = EvolvedWarfare.id("elite_knockback");
	private static final Identifier SPEED_ID = EvolvedWarfare.id("elite_swift");
	private static final Identifier ARMOR_ID = EvolvedWarfare.id("elite_armor");
	private static final Identifier TOUGHNESS_ID = EvolvedWarfare.id("elite_toughness");
	private static final Identifier BERSERK_ID = EvolvedWarfare.id("elite_berserk");

	/** Los élite cargados (para sus partículas y lo que hacen cada segundo). */
	private static final Set<MobEntity> LOADED = Collections.newSetFromMap(new java.util.WeakHashMap<>());

	private Elites() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Elites::tick);
		ServerLivingEntityEvents.AFTER_DEATH.register(Elites::onDeath);
	}

	static void track(MobEntity mob) {
		Threat t = mob.getAttached(Difficulty.THREAT);
		if (t != null && t.elite()) {
			LOADED.add(mob);
		}
	}

	/** Los rasgos al azar: uno; a partir del nivel 6, a veces dos; a partir del 14, a veces tres. */
	public static List<Affix> roll(MobEntity mob, int level) {
		Random r = mob.getRandom();
		int count = 1;
		if (level >= 6 && r.nextFloat() < 0.5F) {
			count++;
		}
		if (level >= 14 && r.nextFloat() < 0.3F) {
			count++;
		}
		List<Affix> pool = new ArrayList<>(List.of(Affix.values()));
		List<Affix> out = new ArrayList<>();
		for (int k = 0; k < count && !pool.isEmpty(); k++) {
			out.add(pool.remove(r.nextInt(pool.size())));
		}
		return out;
	}

	/** Lo hace élite con esos rasgos. */
	public static void make(MobEntity mob, List<Affix> affixes) {
		Threat before = mob.getAttached(Difficulty.THREAT);
		List<String> ids = new ArrayList<>();
		affixes.forEach(a -> ids.add(a.id()));
		mob.setAttached(Difficulty.THREAT, new Threat(before == null ? 0 : before.level(), ids));
		Difficulty.modifier(mob, EntityAttributes.GENERIC_MAX_HEALTH, HEALTH_ID, 1.5, EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		Difficulty.modifier(mob, EntityAttributes.GENERIC_ATTACK_DAMAGE, DAMAGE_ID, 0.5, EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		Difficulty.modifier(mob, EntityAttributes.GENERIC_SCALE, SCALE_ID, 0.15, EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		Difficulty.modifier(mob, EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, KNOCKBACK_ID, 0.3, EntityAttributeModifier.Operation.ADD_VALUE);
		if (affixes.contains(Affix.SWIFT)) {
			Difficulty.modifier(mob, EntityAttributes.GENERIC_MOVEMENT_SPEED, SPEED_ID, 0.35, EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		}
		if (affixes.contains(Affix.ARMORED)) {
			Difficulty.modifier(mob, EntityAttributes.GENERIC_ARMOR, ARMOR_ID, 8.0, EntityAttributeModifier.Operation.ADD_VALUE);
			Difficulty.modifier(mob, EntityAttributes.GENERIC_ARMOR_TOUGHNESS, TOUGHNESS_ID, 4.0, EntityAttributeModifier.Operation.ADD_VALUE);
		}
		if (affixes.contains(Affix.BURNING)) {
			mob.addStatusEffect(new StatusEffectInstance(StatusEffects.FIRE_RESISTANCE, StatusEffectInstance.INFINITE, 0, false, false));
		}
		mob.setHealth(mob.getMaxHealth());
		mob.setCustomName(name(mob, affixes));
		mob.setCustomNameVisible(true);
		LOADED.add(mob);
	}

	/** «★ Zombi de Fuego y de Escarcha» (en inglés, «★ Fiery Frost Zombie»). */
	static Text name(MobEntity mob, List<Affix> affixes) {
		MutableText list = affixes.get(0).title().copy();
		for (int k = 1; k < affixes.size(); k++) {
			list = Text.translatable(k == affixes.size() - 1 ? "elite.evolvedwarfare.and" : "elite.evolvedwarfare.comma", list, affixes.get(k).title());
		}
		return Text.translatable("elite.evolvedwarfare.name", mob.getType().getName(), list).formatted(affixes.get(0).color);
	}

	public static List<Affix> affixes(LivingEntity entity) {
		Threat t = Difficulty.threat(entity);
		List<Affix> out = new ArrayList<>();
		if (t != null) {
			for (String id : t.affixes()) {
				Affix a = Affix.byId(id);
				if (a != null) {
					out.add(a);
				}
			}
		}
		return out;
	}

	// ------------------------------------------------------------------ cada medio segundo

	private static void tick(MinecraftServer server) {
		if (server.getTicks() % 10 != 0) {
			return;
		}
		boolean second = server.getTicks() % 20 == 0;
		for (Iterator<MobEntity> it = LOADED.iterator(); it.hasNext();) {
			MobEntity mob = it.next();
			if (mob.isRemoved() || !mob.isAlive()) {
				it.remove();
				continue;
			}
			if (!(mob.getWorld() instanceof ServerWorld world)) {
				continue;
			}
			List<Affix> affixes = affixes(mob);
			for (Affix a : affixes) {
				if (a == Affix.BERSERK && mob.getHealth() > mob.getMaxHealth() * 0.5F) {
					continue;
				}
				world.spawnParticles(a.particle, mob.getX(), mob.getBodyY(0.6), mob.getZ(), 2, mob.getWidth() * 0.4, mob.getHeight() * 0.3,
						mob.getWidth() * 0.4, 0.01);
			}
			if (second) {
				if (affixes.contains(Affix.REGENERATING) && mob.getHealth() < mob.getMaxHealth()) {
					mob.heal(1.0F);
				}
				if (affixes.contains(Affix.BERSERK)) {
					Difficulty.modifier(mob, EntityAttributes.GENERIC_ATTACK_DAMAGE, BERSERK_ID,
							mob.getHealth() <= mob.getMaxHealth() * 0.5F ? 0.6 : 0.0, EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
				}
			}
		}
	}

	// ------------------------------------------------------------------ al pegar y al morir

	/** Lo que hace un élite al pegar (lo llama LivingEntityDamageMixin con la vida que ha quitado). */
	public static void onHit(LivingEntity victim, DamageSource source, float lost) {
		if (!(source.getAttacker() instanceof MobEntity attacker) || lost <= 0.0F) {
			return;
		}
		List<Affix> affixes = affixes(attacker);
		if (affixes.isEmpty()) {
			return;
		}
		for (Affix a : affixes) {
			switch (a) {
				case BURNING -> victim.setOnFireFor(4.0F);
				case FROST -> {
					victim.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 60, 1));
					victim.setFrozenTicks(Math.min(victim.getMinFreezeDamageTicks() + 40, victim.getFrozenTicks() + 80));
				}
				case VAMPIRIC -> attacker.heal(lost * 0.5F);
				case VENOMOUS -> {
					if (victim instanceof PlayerEntity player && Medicine.enabled()) {
						Medicine.envenom(player, 0, 20 * 10);
					} else {
						victim.addStatusEffect(new StatusEffectInstance(StatusEffects.POISON, 100, 0));
					}
				}
				case WITHERING -> victim.addStatusEffect(new StatusEffectInstance(StatusEffects.WITHER, 100, 0));
				default -> {
				}
			}
		}
	}

	private static void onDeath(LivingEntity entity, DamageSource source) {
		Threat t = Difficulty.threat(entity);
		if (t == null || !t.elite() || !(entity.getWorld() instanceof ServerWorld world)) {
			return;
		}
		List<Affix> affixes = affixes(entity);
		if (affixes.contains(Affix.EXPLOSIVE)) {
			world.createExplosion(entity, entity.getX(), entity.getBodyY(0.5), entity.getZ(), 2.5F, World.ExplosionSourceType.NONE);
		}
		if (!(source.getAttacker() instanceof PlayerEntity)) {
			return;
		}
		ExperienceOrbEntity.spawn(world, entity.getPos(), 15 + 3 * t.level() + 10 * affixes.size());
		Random r = entity.getRandom();
		drop(entity, new ItemStack(Items.EMERALD, 1 + r.nextInt(2 + t.level() / 5)));
		if (r.nextFloat() < 0.3F) {
			ItemStack[] remedies = {new ItemStack(HealthItems.BANDAGE, 2), new ItemStack(HealthItems.ALCOHOL), new ItemStack(HealthItems.ANTIDOTE),
					new ItemStack(HealthItems.SPLINT), new ItemStack(HealthItems.MEDICINE)};
			drop(entity, remedies[r.nextInt(remedies.length)]);
		}
		if (r.nextFloat() < 0.02F + 0.002F * t.level()) {
			drop(entity, new ItemStack(HealthItems.HEART_CRYSTAL));
		}
	}

	private static void drop(LivingEntity entity, ItemStack stack) {
		ItemEntity item = new ItemEntity(entity.getWorld(), entity.getX(), entity.getBodyY(0.5), entity.getZ(), stack);
		item.setToDefaultPickupDelay();
		entity.getWorld().spawnEntity(item);
	}
}
