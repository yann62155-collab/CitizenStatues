package fr.citizen.statues.registry;

import com.mojang.brigadier.arguments.StringArgumentType;
import fr.citizen.statues.CitizenStatues;
import fr.citizen.statues.data.Destination;
import fr.citizen.statues.data.StatueDataService;
import fr.citizen.statues.entity.GuideStatueEntity;
import fr.citizen.statues.security.TeleportRequestService;
import net.minecraft.commands.CommandSourceStack;
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
                .then(Commands.literal("travel")
                        .then(Commands.argument("statueId", StringArgumentType.word())
                                .then(Commands.argument("destinationId", StringArgumentType.word())
                                        .executes(c -> travel(c.getSource(),
                                                StringArgumentType.getString(c, "statueId"),
                                                StringArgumentType.getString(c, "destinationId"))))))
                .then(Commands.literal("summon").requires(source -> source.hasPermission(2))
                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                .executes(c -> summon(c.getSource(), StringArgumentType.getString(c, "name")))))
                .then(Commands.literal("list").requires(source -> source.hasPermission(2)).executes(c -> {
                    var profiles = StatueDataService.get().registry().all();
                    c.getSource().sendSuccess(() -> Component.literal("Statues enregistrées : " + profiles.size()
                            + " — " + profiles.stream().map(p -> p.name() + " [" + p.id() + "]").toList()), false);
                    return profiles.size();
                }))
                .then(Commands.literal("move").requires(source -> source.hasPermission(2))
                        .then(Commands.argument("statueId", StringArgumentType.word())
                                .executes(c -> move(c.getSource(),
                                        StringArgumentType.getString(c, "statueId")))))
                .then(Commands.literal("rename").requires(source -> source.hasPermission(2))
                        .then(Commands.argument("statueId", StringArgumentType.word())
                                .then(Commands.argument("newName", StringArgumentType.greedyString())
                                        .executes(c -> rename(c.getSource(),
                                                StringArgumentType.getString(c, "statueId"),
                                                StringArgumentType.getString(c, "newName"))))))
                .then(Commands.literal("enable").requires(source -> source.hasPermission(2))
                        .then(Commands.argument("statueId", StringArgumentType.word())
                                .executes(c -> setEnabled(c.getSource(),
                                        StringArgumentType.getString(c, "statueId"), true))))
                .then(Commands.literal("disable").requires(source -> source.hasPermission(2))
                        .then(Commands.argument("statueId", StringArgumentType.word())
                                .executes(c -> setEnabled(c.getSource(),
                                        StringArgumentType.getString(c, "statueId"), false))))
                .then(Commands.literal("remove").requires(source -> source.hasPermission(2))
                        .then(Commands.argument("statueId", StringArgumentType.word())
                                .executes(c -> remove(c.getSource(),
                                        StringArgumentType.getString(c, "statueId")))))
                .then(Commands.literal("specialization").requires(source -> source.hasPermission(2))
                        .then(Commands.argument("statueId", StringArgumentType.word())
                                .then(Commands.argument("type", StringArgumentType.word())
                                        .executes(c -> setSpecialization(c.getSource(),
                                                StringArgumentType.getString(c, "statueId"),
                                                StringArgumentType.getString(c, "type"))))))
                .then(Commands.literal("synonym").requires(source -> source.hasPermission(2))
                        .then(Commands.literal("add")
                                .then(Commands.argument("statueId", StringArgumentType.word())
                                        .then(Commands.argument("destinationId", StringArgumentType.word())
                                                .then(Commands.argument("phrase", StringArgumentType.greedyString())
                                                        .executes(c -> addSynonym(c.getSource(),
                                                                StringArgumentType.getString(c, "statueId"),
                                                                StringArgumentType.getString(c, "destinationId"),
                                                                StringArgumentType.getString(c, "phrase")))))))
                        .then(Commands.literal("remove")
                                .then(Commands.argument("statueId", StringArgumentType.word())
                                        .then(Commands.argument("phrase", StringArgumentType.greedyString())
                                                .executes(c -> removeSynonym(c.getSource(),
                                                        StringArgumentType.getString(c, "statueId"),
                                                        StringArgumentType.getString(c, "phrase")))))))
                .then(Commands.literal("destination").requires(source -> source.hasPermission(2))
                        .then(Commands.literal("add")
                                .then(Commands.argument("statueId", StringArgumentType.word())
                                        .then(Commands.argument("destinationId", StringArgumentType.word())
                                                .then(Commands.argument("name", StringArgumentType.greedyString())
                                                        .executes(c -> addDestination(c.getSource(),
                                                                StringArgumentType.getString(c, "statueId"),
                                                                StringArgumentType.getString(c, "destinationId"),
                                                                StringArgumentType.getString(c, "name")))))))
                        .then(Commands.literal("remove")
                                .then(Commands.argument("statueId", StringArgumentType.word())
                                        .then(Commands.argument("destinationId", StringArgumentType.word())
                                                .executes(c -> removeDestination(c.getSource(),
                                                        StringArgumentType.getString(c, "statueId"),
                                                        StringArgumentType.getString(c, "destinationId")))))));
    }

    private static int travel(CommandSourceStack source, String statueId, String destinationId)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var player = source.getPlayerOrException();
        var result = TeleportRequestService.request(player, source.getServer(), statueId,
                destinationId, System.currentTimeMillis());
        switch (result) {
            case TELEPORTED -> player.sendSystemMessage(Component.literal("Vous avez été téléporté.")
                    .withStyle(net.minecraft.ChatFormatting.GREEN));
            case COOLDOWN -> player.sendSystemMessage(Component.literal("Patientez un instant avant de recommencer.")
                    .withStyle(net.minecraft.ChatFormatting.YELLOW));
            case UNKNOWN_STATUE, UNKNOWN_DESTINATION, DISABLED_STATUE, DENIED ->
                    player.sendSystemMessage(Component.literal("Cette destination n'est plus disponible.")
                            .withStyle(net.minecraft.ChatFormatting.RED));
        }
        return result == TeleportRequestService.Result.TELEPORTED ? 1 : 0;
    }

    private static int summon(CommandSourceStack source, String name) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerLevel level = source.getLevel();
        GuideStatueEntity statue = ModEntities.GUIDE_STATUE.get().create(level);
        if (statue == null) { source.sendFailure(Component.literal("Impossible de créer la statue.")); return 0; }
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
        try { StatueDataService.get().save(); }
        catch (IOException exception) {
            CitizenStatues.LOGGER.error("Could not persist newly created statue profile", exception);
            source.sendFailure(Component.literal("Statue créée, mais la sauvegarde a échoué. Consulte la console."));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Statue '" + name + "' créée. ID : " + profile.id()), true);
        return 1;
    }

    private static int addDestination(CommandSourceStack source, String statueId, String destinationId, String name) {
        var profile = StatueDataService.get().registry().get(statueId);
        if (profile.isEmpty()) { source.sendFailure(Component.literal("Statue inconnue : " + statueId)); return 0; }
        var pos = source.getPosition();
        var rotation = source.getRotation();
        try {
            Destination destination = new Destination(destinationId, name,
                    "Destination enregistrée depuis la position de l'administrateur",
                    source.getLevel().dimension().location().toString(),
                    pos.x, pos.y, pos.z, rotation.y, rotation.x, "compass", true);
            profile.get().putDestination(destination);
            StatueDataService.get().save();
            source.sendSuccess(() -> Component.literal("Destination '" + name + "' enregistrée pour " + profile.get().name()), true);
            return 1;
        } catch (IllegalArgumentException | IOException exception) {
            source.sendFailure(Component.literal("Impossible d'enregistrer la destination : " + exception.getMessage()));
            return 0;
        }
    }

    private static int rename(CommandSourceStack source, String id, String name) {
        var profile = StatueDataService.get().registry().get(id);
        if (profile.isEmpty()) { source.sendFailure(Component.literal("Statue inconnue.")); return 0; }
        try {
            profile.get().rename(name); StatueDataService.get().save();
            source.sendSuccess(() -> Component.literal("Statue renommée : " + name), true); return 1;
        } catch (IllegalArgumentException | IOException ex) {
            source.sendFailure(Component.literal("Échec : " + ex.getMessage())); return 0;
        }
    }

    private static int setEnabled(CommandSourceStack source, String id, boolean enabled) {
        var profile = StatueDataService.get().registry().get(id);
        if (profile.isEmpty()) { source.sendFailure(Component.literal("Statue inconnue.")); return 0; }
        try {
            profile.get().setEnabled(enabled); StatueDataService.get().save();
            source.sendSuccess(() -> Component.literal(enabled ? "Statue activée." : "Statue désactivée."), true); return 1;
        } catch (IOException ex) { source.sendFailure(Component.literal("Sauvegarde impossible.")); return 0; }
    }

    private static int remove(CommandSourceStack source, String id) {
        if (!StatueDataService.get().registry().remove(id)) {
            source.sendFailure(Component.literal("Statue inconnue.")); return 0;
        }
        for (ServerLevel level : source.getServer().getAllLevels()) {
            for (var entity : level.getAllEntities()) {
                if (entity instanceof GuideStatueEntity statue && id.equals(statue.getProfileId())) statue.discard();
            }
        }
        try { StatueDataService.get().save(); }
        catch (IOException ex) { source.sendFailure(Component.literal("Statue supprimée en mémoire, sauvegarde échouée.")); return 0; }
        source.sendSuccess(() -> Component.literal("Statue et entités associées supprimées."), true);
        return 1;
    }

    private static int removeDestination(CommandSourceStack source, String statueId, String destinationId) {
        var profile = StatueDataService.get().registry().get(statueId);
        if (profile.isEmpty() || !profile.get().removeDestination(destinationId)) {
            source.sendFailure(Component.literal("Statue ou destination inconnue.")); return 0;
        }
        try { StatueDataService.get().save(); }
        catch (IOException ex) { source.sendFailure(Component.literal("Destination retirée en mémoire, sauvegarde échouée.")); return 0; }
        source.sendSuccess(() -> Component.literal("Destination supprimée."), true);
        return 1;
    }
}
