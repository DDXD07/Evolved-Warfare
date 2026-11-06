package com.evolvedwarfare.client.hell;

import com.evolvedwarfare.EvolvedWarfare;
import com.evolvedwarfare.hell.DamnedEntities;
import com.evolvedwarfare.hell.DesertGoatEntity;
import com.evolvedwarfare.hell.NephilimEntity;
import net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.render.entity.model.EntityModelLayer;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.HostileEntity;

/** Cómo se ven las criaturas del Nether bíblico (con los modelos de tools/ew_damned.py). */
public final class DamnedRenderers {
	private DamnedRenderers() {
	}

	private static EntityModelLayer layer(String id) {
		EntityModelLayer layer = new EntityModelLayer(EvolvedWarfare.id(id), "main");
		EntityModelLayerRegistry.registerModelLayer(layer, () -> BossModel.texturedModelData(id));
		return layer;
	}

	private static <T extends HostileEntity> void simple(EntityType<T> type, String id, EntityModelLayer layer, float shadow, float scale,
			boolean translucent) {
		EntityRendererRegistry.register(type, ctx -> new BossRenderer<T>(ctx, id, layer, shadow, translucent).scaled(scale));
	}

	public static void register() {
		EntityModelLayer locust = layer("abyss_locust");
		simple(DamnedEntities.ABYSS_LOCUST, "abyss_locust", locust, 0.2F, 1.0F, false);
		simple(DamnedEntities.GREATER_LOCUST, "abyss_locust", locust, 0.4F, 1.8F, false);
		simple(DamnedEntities.FIRE_SERPENT, "fire_serpent", layer("fire_serpent"), 0.3F, 1.0F, false);
		EntityModelLayer goat = layer("desert_goat");
		EntityModelLayer goatSulfur = layer("desert_goat_sulfur");
		EntityRendererRegistry.register(DamnedEntities.DESERT_GOAT, ctx -> new BossRenderer<DesertGoatEntity>(ctx, "desert_goat", goat, 0.5F)
				.withVariant(DesertGoatEntity::sulfur, new BossModel<>(ctx.getPart(goatSulfur), "desert_goat_sulfur"), "desert_goat_sulfur"));
		simple(DamnedEntities.FALLEN, "fallen", layer("fallen"), 0.5F, 1.0F, false);
		EntityModelLayer neph = layer("nephilim");
		EntityModelLayer nephArmored = layer("nephilim_armored");
		EntityRendererRegistry.register(DamnedEntities.NEPHILIM, ctx -> new BossRenderer<NephilimEntity>(ctx, "nephilim", neph, 1.2F)
				.withVariant(NephilimEntity::armored, new BossModel<>(ctx.getPart(nephArmored), "nephilim_armored"), "nephilim_armored"));
		simple(DamnedEntities.DAMNED_SPIRIT, "damned_spirit", layer("damned_spirit"), 0.0F, 1.0F, true);
		simple(DamnedEntities.SALT_STATUE, "salt_statue", layer("salt_statue"), 0.5F, 1.0F, false);
		// los mini jefes del Apocalipsis (tools/ew_apocalypse.py)
		simple(com.evolvedwarfare.hell.Apocalypse.ABYSS_ANGEL, "abyss_angel", layer("abyss_angel"), 0.8F, 1.0F, false);
		simple(com.evolvedwarfare.hell.Apocalypse.FALSE_PROPHET, "false_prophet", layer("false_prophet"), 0.6F, 1.0F, false);
		simple(com.evolvedwarfare.hell.Apocalypse.GREATER_BEAST, "greater_beast", layer("greater_beast"), 2.0F, 1.9F, false);
	}
}
