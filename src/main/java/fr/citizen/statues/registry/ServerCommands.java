package fr.citizen.statues.registry;

import com.mojang.brigadier.arguments.StringArgumentType;
import fr.citizen.statues.CitizenStatues;
import fr.citizen.statues.data.Destination;
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
                        }))
                .then(Commands.literal("destination")
                        .then(Commands.literal("add")
                                .then(Commands.argument("statueId", StringArgumentType.word())
                                        .then(Commands.argument("destinationId", StringArgumentType.word())
                                                .then(Commands.argument("name", StringArgumentType.greedyString())
                                                        .executes(context -> addDestination(
                                                                context.getSource(),
                                                                StringArgumentType.getString(context, "statueId"),
                                                                StringArgumentType.getString(context, "destinationId"),
                                                                StringArgumentType.getString(context, "name")))))))));
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

    private static int addDestination(net.minecraft.commands.CommandSourceStack source,
                                      String statueId, String destinationId, String name) {
        var profile = StatueDataService.get().registry().get(statueId);
        if (profile.isEmpty()) {
            source.sendFailure(Component.literal("Statue inconnue : " + statueId));
            return 0;
        }
        var pos = source.getPosition();
        var rotation = source.getRotation();
        var dimension = source.getLevel().dimension().location().toString();
        try {
            Destination destination = new Destination(destinationId, name,
                    "Destination enregistrée depuis la position de l'administrateur",
                    dimension, pos.x, pos.y, pos.z, rotation.y, rotation.x, "compass", true);
            profile.get().putDestination(destination);
            StatueDataService.get().save();
            source.sendSuccess(() -> Component.literal("Destination '" + name
                    + "' enregistrée pour la statue " + profile.get().name()), true);
            return 1;
        } catch (IllegalArgumentException | IOException exception) {
            source.sendFailure(Component.literal("Impossible d'enregistrer la destination : "
                    + exception.getMessage()));
            return 0;
        }
    }
}
