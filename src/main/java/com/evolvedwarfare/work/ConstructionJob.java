package com.evolvedwarfare.work;

import com.evolvedwarfare.blueprint.BlueprintLibrary;
import com.evolvedwarfare.blueprint.BlueprintRef;
import com.evolvedwarfare.blueprint.StoredBlueprint;
import com.evolvedwarfare.building.BuildPlan;
import com.evolvedwarfare.item.BlueprintItem;
import com.evolvedwarfare.registry.ModComponents;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

/**
 * Constructor: construye el plano que tenga en los suministros (un objeto Plano), detrás de su puesto y con el frente
 * mirando hacia donde mira el puesto. Igual que la obra de los demás puestos: limpia lo natural, rellena bajo el suelo
 * y pone los bloques de abajo arriba, pero cada bloque gasta su propio objeto. Si falta algo, lo dice en el menú (y el
 * mensajero lo trae del almacén).
 */
public class ConstructionJob implements Job {
	@Nullable
	private String planId;
	@Nullable
	private BuildPlan plan;
	private boolean announced;

	private final BuildJob build = new BuildJob(new BuildJob.Target() {
		@Override
		public BuildPlan plan(WorkContext ctx) {
			return plan;
		}

		@Override
		public void progress(WorkContext ctx, int placed, int total) {
			ctx.site.setBuildProgress(placed, total);
		}

		@Override
		public WorkStatus finished(WorkContext ctx) {
			if (!announced) {
				announced = true;
				ItemStack stack = blueprint(ctx);
				BlueprintRef ref = stack != null ? stack.get(ModComponents.BLUEPRINT) : null;
				if (ref != null) {
					ctx.worker.tellOwner(Text.translatable("message.evolvedwarfare.builder.done", ctx.worker.getName(),
							BlueprintItem.nameOf(ref)));
				}
			}
			return WorkStatus.DONE;
		}
	});

	@Nullable
	private static ItemStack blueprint(WorkContext ctx) {
		return ctx.site.find(stack -> stack.contains(ModComponents.BLUEPRINT), false);
	}

	@Override
	@Nullable
	public WorkAction plan(WorkContext ctx) {
		ItemStack stack = blueprint(ctx);
		if (stack == null) {
			planId = null;
			plan = null;
			ctx.site.setBuildProgress(0, 0);
			return ctx.idle(WorkStatus.NO_BLUEPRINT);
		}
		BlueprintRef ref = stack.get(ModComponents.BLUEPRINT);
		if (plan == null || !ref.id().equals(planId)) {
			StoredBlueprint stored = BlueprintLibrary.get(ctx.world.getServer(), ref.id());
			if (stored == null) {
				return ctx.idle(WorkStatus.BLUEPRINT_MISSING);
			}
			plan = BuildPlan.of(stored.toBlueprint(), ctx.sitePos, ctx.facing);
			planId = ref.id();
			announced = false;
		}
		return build.plan(ctx);
	}
}
