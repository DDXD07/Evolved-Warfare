package com.evolvedwarfare.mixin;

import net.minecraft.entity.ai.goal.GoalSelector;
import net.minecraft.entity.mob.MobEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Para añadir objetivos a criaturas de vanilla (la noche más dura). */
@Mixin(MobEntity.class)
public interface MobEntityGoalsAccessor {
	@Accessor("goalSelector")
	GoalSelector evolvedwarfare$goals();

	@Accessor("targetSelector")
	GoalSelector evolvedwarfare$targets();
}
