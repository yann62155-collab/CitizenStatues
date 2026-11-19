package fr.citizen.statues.registry;

import fr.citizen.statues.CitizenStatues;
import fr.citizen.statues.entity.GuideStatueEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, CitizenStatues.MOD_ID);

    public static final DeferredHolder<EntityType<?>, EntityType<GuideStatueEntity>> GUIDE_STATUE =
            ENTITY_TYPES.register("guide_statue", () -> EntityType.Builder
                    .of(GuideStatueEntity::new, MobCategory.MISC)
                    .sized(0.8F, 2.4F)
                    .clientTrackingRange(10)
                    .updateInterval(20)
                    .build("citizenstatues:guide_statue"));

    private ModEntities() {}
}
