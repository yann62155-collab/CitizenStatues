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
                                                        StringArgumentType.getString(c, "destinationId"))))))
                        .then(Commands.literal("enable")
                                .then(Commands.argument("statueId", StringArgumentType.word())
                                        .then(Commands.argument("destinationId", StringArgumentType.word())
                                                .executes(c -> setDestinationEnabled(c.getSource(),
                                                        StringArgumentType.getString(c, "statueId"),
                                                        StringArgumentType.getString(c, "destinationId"), true)))))
                        .then(Commands.literal("disable")
                                .then(Commands.argument("statueId", StringArgumentType.word())
                                        .then(Commands.argument("destinationId", StringArgumentType.word())
                                                .executes(c -> setDestinationEnabled(c.getSource(),
                                                        StringArgumentType.getString(c, "statueId"),
                                                        StringArgumentType.getString(c, "destinationId"), false)))))
                        .then(Commands.literal("set-here")
                                .then(Commands.argument("statueId", StringArgumentType.word())
                                        .then(Commands.argument("destinationId", StringArgumentType.word())
                                                .executes(c -> moveDestinationHere(c.getSource(),
                                                        StringArgumentType.getString(c, "statueId"),
                                                        StringArgumentType.getString(c, "destinationId"))))))
                        .then(Commands.literal("catalog-add").requires(source -> source.hasPermission(2))
                                .then(Commands.argument("destinationId", StringArgumentType.word())
                                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                                .executes(c -> addCatalogDestination(c.getSource(),
                                                        StringArgumentType.getString(c, "destinationId"),
                                                        StringArgumentType.getString(c, "name"))))))
                        .then(Commands.literal("catalog-list").requires(source -> source.hasPermission(2))
                                .executes(c -> listCatalog(c.getSource())))
                        .then(Commands.literal("catalog-enable").requires(source -> source.hasPermission(2))
                                .then(Commands.argument("destinationId", StringArgumentType.word())
                                        .executes(c -> setCatalogDestinationEnabled(c.getSource(),
                                                StringArgumentType.getString(c, "destinationId"), true))))
                        .then(Commands.literal("catalog-disable").requires(source -> source.hasPermission(2))
                                .then(Commands.argument("destinationId", StringArgumentType.word())
                                        .executes(c -> setCatalogDestinationEnabled(c.getSource(),
                                                StringArgumentType.getString(c, "destinationId"), false))))
                        .then(Commands.literal("catalog-set-here").requires(source -> source.hasPermission(2))
                                .then(Commands.argument("destinationId", StringArgumentType.word())
                                        .executes(c -> moveCatalogDestinationHere(c.getSource(),
                                                StringArgumentType.getString(c, "destinationId")))))
                        .then(Commands.literal("catalog-rename").requires(source -> source.hasPermission(2))
                                .then(Commands.argument("destinationId", StringArgumentType.word())
                                        .then(Commands.argument("newName", StringArgumentType.greedyString())
                                                .executes(c -> updateCatalogDestination(c.getSource(),
                                                        StringArgumentType.getString(c, "destinationId"),
                                                        StringArgumentType.getString(c, "newName"), null)))))
                        .then(Commands.literal("catalog-description").requires(source -> source.hasPermission(2))
                                .then(Commands.argument("destinationId", StringArgumentType.word())
                                        .then(Commands.argument("description", StringArgumentType.greedyString())
                                                .executes(c -> updateCatalogDestination(c.getSource(),
                                                        StringArgumentType.getString(c, "destinationId"),
                                                        null, StringArgumentType.getString(c, "description"))))))
                        .then(Commands.literal("catalog-remove").requires(source -> source.hasPermission(2))
                                .then(Commands.argument("destinationId", StringArgumentType.word())
                                        .executes(c -> removeCatalogDestination(c.getSource(),
                                                StringArgumentType.getString(c, "destinationId")))))
                        .then(Commands.literal("assign").requires(source -> source.hasPermission(2))
                                .then(Commands.argument("statueId", StringArgumentType.word())
                                        .then(Commands.argument("destinationId", StringArgumentType.word())
                                                .executes(c -> assignCatalogDestination(c.getSource(),
                                                        StringArgumentType.getString(c, "statueId"),
                                                        StringArgumentType.getString(c, "destinationId"))))))
                        .then(Commands.literal("unassign").requires(source -> source.hasPermission(2))
                                .then(Commands.argument("statueId", StringArgumentType.word())
                                        .then(Commands.argument("destinationId", StringArgumentType.word())
                                                .executes(c -> unassignCatalogDestination(c.getSource(),
                                                        StringArgumentType.getString(c, "statueId"),
                                                        StringArgumentType.getString(c, "destinationId"))))))));
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

    private static int setCatalogDestinationEnabled(CommandSourceStack source, String id, boolean enabled) {
        var current = StatueDataService.get().registry().destination(id);
        if (current.isEmpty()) { source.sendFailure(Component.literal("Destination du catalogue inconnue.")); return 0; }
        Destination old = current.get();
        try {
            Destination updated = new Destination(old.id(), old.name(), old.description(), old.dimension(),
                    old.x(), old.y(), old.z(), old.yaw(), old.pitch(), old.icon(), enabled);
            StatueDataService.get().registry().putCatalogDestination(updated);
            StatueDataService.get().save();
            source.sendSuccess(() -> Component.literal(enabled
                    ? "Destination activée dans le catalogue et sur les statues associées."
                    : "Destination désactivée dans le catalogue et sur les statues associées."), true);
            return 1;
        } catch (IllegalArgumentException | IOException ex) {
            source.sendFailure(Component.literal("Modification impossible : " + ex.getMessage())); return 0;
        }
    }

    private static int updateCatalogDestination(CommandSourceStack source, String id, String name, String description) {
        var current = StatueDataService.get().registry().destination(id);
        if (current.isEmpty()) { source.sendFailure(Component.literal("Destination du catalogue inconnue.")); return 0; }
        Destination old = current.get();
        try {
            Destination updated = new Destination(old.id(), name == null ? old.name() : name,
                    description == null ? old.description() : description, old.dimension(),
                    old.x(), old.y(), old.z(), old.yaw(), old.pitch(), old.icon(), old.enabled());
            StatueDataService.get().registry().putCatalogDestination(updated);
            StatueDataService.get().save();
            source.sendSuccess(() -> Component.literal("Destination du catalogue modifiée et synchronisée avec les statues associées."), true);
            return 1;
        } catch (IllegalArgumentException | IOException ex) {
            source.sendFailure(Component.literal("Modification impossible : " + ex.getMessage())); return 0;
        }
    }

    private static int moveCatalogDestinationHere(CommandSourceStack source, String destinationId)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var current = StatueDataService.get().registry().destination(destinationId);
        if (current.isEmpty()) { source.sendFailure(Component.literal("Destination du catalogue inconnue.")); return 0; }
        var player = source.getPlayerOrException();
        Destination old = current.get();
        try {
            Destination updated = new Destination(old.id(), old.name(), old.description(),
                    player.serverLevel().dimension().location().toString(),
                    player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot(),
                    old.icon(), old.enabled());
            StatueDataService.get().registry().putCatalogDestination(updated);
            StatueDataService.get().save();
            source.sendSuccess(() -> Component.literal("Destination du catalogue déplacée ; les statues associées ont été actualisées."), true);
            return 1;
        } catch (IllegalArgumentException | IOException ex) {
            source.sendFailure(Component.literal("Déplacement impossible : " + ex.getMessage())); return 0;
        }
    }

    private static int addCatalogDestination(CommandSourceStack source, String id, String name) {
        var pos = source.getPosition(); var rot = source.getRotation();
        try {
            Destination d = new Destination(id, name, "Destination du catalogue",
                    source.getLevel().dimension().location().toString(), pos.x, pos.y, pos.z,
                    rot.y, rot.x, "compass", true);
            StatueDataService.get().registry().putCatalogDestination(d);
            StatueDataService.get().save();
            source.sendSuccess(() -> Component.literal("Destination ajoutée au catalogue : " + name), true);
            return 1;
        } catch (IllegalArgumentException | IOException ex) {
            source.sendFailure(Component.literal("Création impossible : " + ex.getMessage())); return 0;
        }
    }

    private static int listCatalog(CommandSourceStack source) {
        var entries = StatueDataService.get().registry().catalog();
        source.sendSuccess(() -> Component.literal("Catalogue (" + entries.size() + ") : "
                + entries.stream().map(d -> d.name() + " [" + d.id() + "]").toList()), false);
        return entries.size();
    }

    private static int removeCatalogDestination(CommandSourceStack source, String id) {
        if (!StatueDataService.get().registry().removeCatalogDestination(id)) {
            source.sendFailure(Component.literal("Destination du catalogue inconnue.")); return 0;
        }
        try {
            StatueDataService.get().save();
            source.sendSuccess(() -> Component.literal("Destination retirée du catalogue. Les statues déjà configurées la conservent."), true);
            return 1;
        } catch (IOException ex) { source.sendFailure(Component.literal("Sauvegarde impossible.")); return 0; }
    }

    private static int assignCatalogDestination(CommandSourceStack source, String statueId, String destinationId) {
        if (!StatueDataService.get().registry().assignDestination(statueId, destinationId)) {
            source.sendFailure(Component.literal("Statue ou destination du catalogue inconnue.")); return 0;
        }
        try {
            StatueDataService.get().save();
            source.sendSuccess(() -> Component.literal("Destination attribuée à la statue."), true);
            return 1;
        } catch (IOException ex) { source.sendFailure(Component.literal("Sauvegarde impossible.")); return 0; }
    }

    private static int unassignCatalogDestination(CommandSourceStack source, String statueId, String destinationId) {
        if (!StatueDataService.get().registry().unassignDestination(statueId, destinationId)) {
            source.sendFailure(Component.literal("Statue ou destination non attribuée.")); return 0;
        }
        try {
            StatueDataService.get().save();
            source.sendSuccess(() -> Component.literal("Destination retirée de cette statue uniquement."), true);
            return 1;
        } catch (IOException ex) { source.sendFailure(Component.literal("Sauvegarde impossible.")); return 0; }
    }

    private static int moveDestinationHere(CommandSourceStack source, String statueId, String destinationId)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var profile = StatueDataService.get().registry().get(statueId);
        if (profile.isEmpty()) { source.sendFailure(Component.literal("Statue inconnue.")); return 0; }
        Destination current = profile.get().destinations().get(destinationId);
        if (current == null) { source.sendFailure(Component.literal("Destination inconnue.")); return 0; }
        var player = source.getPlayerOrException();
        try {
            profile.get().putDestination(new Destination(current.id(), current.name(), current.description(),
                    player.serverLevel().dimension().location().toString(),
                    player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot(),
                    current.icon(), current.enabled()));
            StatueDataService.get().save();
            source.sendSuccess(() -> Component.literal("Position de la destination mise à jour."), true);
            return 1;
        } catch (IllegalArgumentException | IOException ex) {
            source.sendFailure(Component.literal("Modification impossible : " + ex.getMessage())); return 0;
        }
    }
    private static int setDestinationEnabled(CommandSourceStack source, String statueId,
                                             String destinationId, boolean enabled) {
        var profile = StatueDataService.get().registry().get(statueId);
        if (profile.isEmpty()) { source.sendFailure(Component.literal("Statue inconnue.")); return 0; }
        Destination current = profile.get().destinations().get(destinationId);
        if (current == null) { source.sendFailure(Component.literal("Destination inconnue.")); return 0; }
        try {
            profile.get().putDestination(new Destination(current.id(), current.name(), current.description(),
                    current.dimension(), current.x(), current.y(), current.z(), current.yaw(), current.pitch(),
                    current.icon(), enabled));
            StatueDataService.get().save();
            source.sendSuccess(() -> Component.literal(enabled ? "Destination activée." : "Destination désactivée."), true);
            return 1;
        } catch (IllegalArgumentException | IOException ex) {
            source.sendFailure(Component.literal("Modification impossible : " + ex.getMessage())); return 0;
        }
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

    private static int move(CommandSourceStack source, String id)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var player = source.getPlayerOrException();
        var profile = StatueDataService.get().registry().get(id);
        if (profile.isEmpty()) { source.sendFailure(Component.literal("Statue inconnue.")); return 0; }
        GuideStatueEntity target = null;
        for (var entity : source.getLevel().getAllEntities()) {
            if (entity instanceof GuideStatueEntity statue && id.equals(statue.getProfileId())) {
                target = statue; break;
            }
        }
        if (target == null) { source.sendFailure(Component.literal("Entité de statue introuvable dans cette dimension.")); return 0; }
        target.teleportTo(player.getX(), player.getY(), player.getZ());
        target.setYRot(player.getYRot());
        source.sendSuccess(() -> Component.literal("Statue déplacée à votre position."), true);
        return 1;
    }

    private static int setSpecialization(CommandSourceStack source, String id, String type) {
        var profile = StatueDataService.get().registry().get(id);
        if (profile.isEmpty()) { source.sendFailure(Component.literal("Statue inconnue.")); return 0; }
        String normalized = type.toLowerCase(java.util.Locale.ROOT);
        if (!java.util.Set.of("general", "spawn", "guild", "market").contains(normalized)) {
            source.sendFailure(Component.literal("Spécialisation invalide. Valeurs : general, spawn, guild, market."));
            return 0;
        }
        try {
            profile.get().setSpecialization(normalized);
            StatueDataService.get().save();
            source.sendSuccess(() -> Component.literal("Spécialisation définie : " + normalized), true);
            return 1;
        } catch (IOException ex) {
            source.sendFailure(Component.literal("Sauvegarde impossible.")); return 0;
        }
    }

    private static int addSynonym(CommandSourceStack source, String id, String destinationId, String phrase) {
        var profile = StatueDataService.get().registry().get(id);
        if (profile.isEmpty()) { source.sendFailure(Component.literal("Statue inconnue.")); return 0; }
        try {
            profile.get().putSynonym(phrase, destinationId);
            StatueDataService.get().save();
            source.sendSuccess(() -> Component.literal("Synonyme ajouté : " + phrase), true);
            return 1;
        } catch (IllegalArgumentException | IOException ex) {
            source.sendFailure(Component.literal("Impossible d'ajouter le synonyme : " + ex.getMessage()));
            return 0;
        }
    }

    private static int removeSynonym(CommandSourceStack source, String id, String phrase) {
        var profile = StatueDataService.get().registry().get(id);
        if (profile.isEmpty()) { source.sendFailure(Component.literal("Statue inconnue.")); return 0; }
        profile.get().removeSynonym(phrase);
        try {
            StatueDataService.get().save();
            source.sendSuccess(() -> Component.literal("Synonyme supprimé : " + phrase), true);
            return 1;
        } catch (IOException ex) {
            source.sendFailure(Component.literal("Sauvegarde impossible.")); return 0;
        }
    }

}
