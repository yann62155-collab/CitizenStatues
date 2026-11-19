package fr.citizen.statues.network;

import fr.citizen.statues.CitizenStatues;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record VoiceIntentResultPayload(boolean success, String message) implements CustomPacketPayload {
    public static final Type<VoiceIntentResultPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CitizenStatues.MOD_ID, "voice_intent_result"));
    public static final StreamCodec<RegistryFriendlyByteBuf, VoiceIntentResultPayload> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.BOOL, VoiceIntentResultPayload::success,
                    ByteBufCodecs.STRING_UTF8, VoiceIntentResultPayload::message, VoiceIntentResultPayload::new);
    public VoiceIntentResultPayload {
        if (message == null || message.length() > 256) throw new IllegalArgumentException("Invalid voice result");
    }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
