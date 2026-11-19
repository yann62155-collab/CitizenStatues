package fr.citizen.statues.registry;

import fr.citizen.statues.CitizenStatues;
import fr.citizen.statues.entity.GuideStatueEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;

public final class ModEntities {
    public static final DeferredRegister.Entities ENTITY_TYPES =
            DeferredRegister.createEntities(CitizenStatues.MOD_ID);

    public static final DeferredHolder<EntityType<?>, EntityType<GuideStatueEntity>> GUIDE_STATUE =
            ENTITY_TYPES.registerEntityType("guide_statue", GuideStatueEntity::new,
                    MobCategory.MISC, builder -> builder.sized(0.8F, 2.4F)
                            .clientTrackingRange(10).updateInterval(20));

    private ModEntities() {}
}
