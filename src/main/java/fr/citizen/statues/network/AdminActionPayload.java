package fr.citizen.statues.network;

import fr.citizen.statues.CitizenStatues;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** A small, validated request from an operator's admin screen. */
public record AdminActionPayload(String action, String statueId, String destinationId) implements CustomPacketPayload {
    public static final Type<AdminActionPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CitizenStatues.MOD_ID, "admin_action"));

    public static final StreamCodec<RegistryFriendlyByteBuf, AdminActionPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, AdminActionPayload::action,
                    ByteBufCodecs.STRING_UTF8, AdminActionPayload::statueId,
                    ByteBufCodecs.STRING_UTF8, AdminActionPayload::destinationId,
                    AdminActionPayload::new);

    public AdminActionPayload {
        action = bounded(action, 32);
        statueId = bounded(statueId, 128);
        destinationId = bounded(destinationId, 128);
    }

    private static String bounded(String value, int max) {
        if (value == null || value.length() > max) throw new IllegalArgumentException("Invalid admin action field");
        return value;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
