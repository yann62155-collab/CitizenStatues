package fr.citizen.statues.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import fr.citizen.statues.CitizenStatues;
import java.io.IOException;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.ChatFormatting;
import fr.citizen.statues.data.StatueDataService;
import fr.citizen.statues.data.StatueProfile;
import fr.citizen.statues.network.PlayerDestinationsPayload;
import net.neoforged.neoforge.network.PacketDistributor;

/** Stationary, silent guide statue. No autonomous goals, sounds, or physics displacement. */
public class GuideStatueEntity extends Mob {
    private static final String PROFILE_TAG = "CitizenStatueProfile";
    private String profileId = "";

    public GuideStatueEntity(EntityType<? extends Mob> type, Level level) {
        super(type, level);
        setNoAi(true);
        setSilent(true);
        setInvulnerable(false);
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 100.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D);
    }

    public String getProfileId() { return profileId; }
    public void setProfileId(String id) { profileId = id == null ? "" : id; }

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        // Only server operators (permission level 2) may damage/remove a guide statue.
        if (source.getEntity() instanceof ServerPlayer player) {
            return !player.hasPermissions(2);
        }
        return true;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide || !(source.getEntity() instanceof ServerPlayer player)
                || !player.hasPermissions(2)) {
            return false;
        }
        String removedProfile = profileId;
        discard();
        if (!removedProfile.isBlank() && StatueDataService.get().registry().remove(removedProfile)) {
            try {
                StatueDataService.get().save();
            } catch (IOException exception) {
                CitizenStatues.LOGGER.error("Could not save removed guide statue profile {}", removedProfile, exception);
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                        "Statue retirée, mais son profil n'a pas pu être sauvegardé. Consulte la console.")
                        .withStyle(ChatFormatting.RED));
                return true;
            }
        }
        player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                "Statue supprimée.").withStyle(ChatFormatting.GREEN));
        return true;
    }

    @Override protected void registerGoals() {}

    @Override
    public void tick() {
        super.tick();
        setDeltaMovement(Vec3.ZERO);
        fallDistance = 0.0F;
    }

    @Override public void travel(Vec3 travelVector) { setDeltaMovement(Vec3.ZERO); }
    @Override public void move(MoverType type, Vec3 movement) {}
    @Override public boolean isPushable() { return false; }
    @Override public boolean canBeLeashed() { return false; }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (level().isClientSide) return InteractionResult.SUCCESS;
        if (!(player instanceof net.minecraft.server.level.ServerPlayer serverPlayer)) {
            return InteractionResult.CONSUME;
        }
        StatueProfile profile = StatueDataService.get().registry().get(profileId).orElse(null);
        if (profile == null || !profile.enabled()) {
            serverPlayer.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "Ce guide n'est pas disponible pour le moment.").withStyle(ChatFormatting.RED));
            return InteractionResult.CONSUME;
        }
        var destinations = PlayerDestinationsPayload.encode(profile);
        PacketDistributor.sendToPlayer(serverPlayer,
                new PlayerDestinationsPayload(profileId, profile.name(), destinations));
        return InteractionResult.CONSUME;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString(PROFILE_TAG, profileId);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        profileId = tag.getString(PROFILE_TAG);
    }

    @Override protected SoundEvent getAmbientSound() { return null; }
    @Override protected SoundEvent getHurtSound(DamageSource source) { return null; }
    @Override protected SoundEvent getDeathSound() { return null; }
}
