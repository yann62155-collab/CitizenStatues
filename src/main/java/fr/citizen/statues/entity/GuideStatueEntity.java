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
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;

/** Stationary, silent guide statue. No autonomous goals, sounds, or physics displacement. */
public class GuideStatueEntity extends Mob {
    private static final String PROFILE_TAG = "CitizenStatueProfile";
    private String profileId = "";

    public GuideStatueEntity(EntityType<? extends Mob> type, Level level) {
        super(type, level);
        setNoAi(true);
        setSilent(true);
        setInvulnerable(true);
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
        return level().isClientSide ? InteractionResult.SUCCESS : InteractionResult.CONSUME;
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString(PROFILE_TAG, profileId);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        profileId = tag.getString(PROFILE_TAG);
    }

    @Override protected SoundEvent getAmbientSound() { return null; }
    @Override protected SoundEvent getHurtSound(DamageSource source) { return null; }
    @Override protected SoundEvent getDeathSound() { return null; }
}
