package fr.citizen.statues.registry;

import fr.citizen.statues.CitizenStatues;
import fr.citizen.statues.entity.GuideStatueEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;

@EventBusSubscriber(modid = CitizenStatues.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class ModEvents {
    private ModEvents() {}

    @SubscribeEvent
    public static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(ModEntities.GUIDE_STATUE.get(), GuideStatueEntity.createAttributes().build());
    }
}
