package fr.citizen.statues.registry;

import com.mojang.brigadier.arguments.StringArgumentType;
import fr.citizen.statues.CitizenStatues;
import fr.citizen.statues.data.StatueDataService;
import fr.citizen.statues.entity.GuideStatueEntity;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.io.IOException;

@EventBusSubscriber(modid = CitizenStatues.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public final class ServerCommands {
    private ServerCommands() {}

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("citizenstatues")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("summon")
                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                .executes(context -> summon(context.getSource(),
                                        StringArgumentType.getString(context, "name")))))
                .then(Commands.literal("list")
                        .executes(context -> {
                            var registry = StatueDataService.get().registry();
                            context.getSource().sendSuccess(() -> Component.literal(
                                    "Statues enregistrées : " + registry.size()), false);
                            return registry.size();
                        })));
    }

    private static int summon(net.minecraft.commands.CommandSourceStack source, String name)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerLevel level = source.getLevel();
        GuideStatueEntity statue = ModEntities.GUIDE_STATUE.get().create(level);
        if (statue == null) {
            source.sendFailure(Component.literal("Impossible de créer la statue."));
            return 0;
        }
        var profile = StatueDataService.get().registry().create(name, "general");
        statue.setProfileId(profile.id());
        var pos = source.getPosition();
        var rotation = source.getRotation();
        statue.moveTo(pos.x, pos.y, pos.z, rotation.y, 0.0F);
        if (!level.addFreshEntity(statue)) {
            StatueDataService.get().registry().remove(profile.id());
            source.sendFailure(Component.literal("Le serveur a refusé la création de la statue."));
            return 0;
        }
        try {
            StatueDataService.get().save();
        } catch (IOException exception) {
            CitizenStatues.LOGGER.error("Could not persist newly created statue profile", exception);
            source.sendFailure(Component.literal("Statue créée, mais la sauvegarde a échoué. Consulte la console."));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Statue '" + name + "' créée. ID : " + profile.id()), true);
        return 1;
    }
}
