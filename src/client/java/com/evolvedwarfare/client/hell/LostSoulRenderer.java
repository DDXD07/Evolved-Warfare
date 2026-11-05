package com.evolvedwarfare.client.hell;

import com.evolvedwarfare.EvolvedWarfare;
import com.evolvedwarfare.hell.LostSoulEntity;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.client.render.entity.model.EntityModelLayer;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

/** El alma en pena: translúcida y con luz propia (se ve en la oscuridad). */
public class LostSoulRenderer extends MobEntityRenderer<LostSoulEntity, LostSoulModel> {
	public static final EntityModelLayer LAYER = new EntityModelLayer(EvolvedWarfare.id("lost_soul"), "main");
	private static final Identifier TEXTURE = EvolvedWarfare.id("textures/entity/hell/lost_soul.png");

	public LostSoulRenderer(EntityRendererFactory.Context ctx) {
		super(ctx, new LostSoulModel(ctx.getPart(LAYER)), 0.0F);
	}

	@Override
	public Identifier getTexture(LostSoulEntity entity) {
		return TEXTURE;
	}

	@Nullable
	@Override
	protected RenderLayer getRenderLayer(LostSoulEntity entity, boolean showBody, boolean translucent, boolean showOutline) {
		return RenderLayer.getEntityTranslucent(TEXTURE);
	}

	@Override
	protected int getBlockLight(LostSoulEntity entity, BlockPos pos) {
		return 12;
	}
}
