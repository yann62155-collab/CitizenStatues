package fr.citizen.statues.entity;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Stationary, silent guide statue. It has no goals, does not wander, and
 * deliberately emits no ambient, hurt, or death sounds.
 */
public class GuideStatueEntity extends Mob {
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

    @Override
    protected void registerGoals() {
        // Intentionally empty: statues never move autonomously.
    }

    @Override
    public void tick() {
        super.tick();
        setDeltaMovement(Vec3.ZERO);
        fallDistance = 0.0F;
    }

    @Override
    public void travel(Vec3 travelVector) {
        setDeltaMovement(Vec3.ZERO);
    }

    @Override
    public void move(MoverType type, Vec3 movement) {
        // Prevent external pushes and physics from displacing the statue.
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean canBeLeashed() {
        return false;
    }

    @Override
    public InteractionResult mobInteract(net.minecraft.world.entity.player.Player player,
                                          InteractionHand hand) {
        if (level().isClientSide) return InteractionResult.SUCCESS;
        // The server-side interaction menu/network handler is registered in the next integration layer.
        return InteractionResult.CONSUME;
    }

    @Override
    protected net.minecraft.sounds.SoundEvent getAmbientSound() { return null; }
    @Override
    protected net.minecraft.sounds.SoundEvent getHurtSound(
            net.minecraft.world.damagesource.DamageSource source) { return null; }
    @Override
    protected net.minecraft.sounds.SoundEvent getDeathSound() { return null; }
}
