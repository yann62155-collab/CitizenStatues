package fr.citizen.statues.item;

import fr.citizen.statues.data.StatueDataService;
import fr.citizen.statues.entity.GuideStatueEntity;
import fr.citizen.statues.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.entity.player.Player;

/** Places a configured guide statue on the clicked block. */
public final class GuideStatueItem extends Item {
    public GuideStatueItem(Properties properties) { super(properties); }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!(context.getLevel() instanceof ServerLevel level)) return InteractionResult.SUCCESS;
        Player player = context.getPlayer();
        if (player == null) return InteractionResult.PASS;
        BlockPos place = context.getClickedPos().relative(context.getClickedFace());
        if (!level.getBlockState(place).getCollisionShape(level, place).isEmpty()
                || !level.getBlockState(place.above()).getCollisionShape(level, place.above()).isEmpty()) {
            player.displayClientMessage(Component.literal("Il faut deux blocs d'espace libre pour placer le guide."), true);
            return InteractionResult.FAIL;
        }

        GuideStatueEntity statue = ModEntities.GUIDE_STATUE.get().create(level);
        if (statue == null) return InteractionResult.FAIL;
        var profile = StatueDataService.get().registry().create("Guide Citizen", "general");
        statue.setProfileId(profile.id());
        statue.moveTo(place.getX() + 0.5D, place.getY(), place.getZ() + 0.5D,
                player.getYRot(), 0.0F);
        if (!level.addFreshEntity(statue)) {
            StatueDataService.get().registry().remove(profile.id());
            return InteractionResult.FAIL;
        }
        try {
            StatueDataService.get().save();
        } catch (java.io.IOException exception) {
            statue.discard();
            StatueDataService.get().registry().remove(profile.id());
            fr.citizen.statues.CitizenStatues.LOGGER.error("Unable to save placed guide statue", exception);
            player.displayClientMessage(Component.literal("La statue n'a pas pu être sauvegardée."), true);
            return InteractionResult.FAIL;
        }
        if (!player.getAbilities().instabuild) context.getItemInHand().shrink(1);
        player.displayClientMessage(Component.literal("Guide Citizen placé. Configure-le avec /citizenstatues admin."), true);
        return InteractionResult.CONSUME;
    }
}
