package fr.citizen.statues.registry;

import fr.citizen.statues.CitizenStatues;
import fr.citizen.statues.item.GuideStatueItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredItem;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(CitizenStatues.MOD_ID);
    public static final DeferredItem<Item> GUIDE_STATUE = ITEMS.register("guide_statue",
            () -> new GuideStatueItem(new Item.Properties().stacksTo(1)));

    private ModItems() {}
}
