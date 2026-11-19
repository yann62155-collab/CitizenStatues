package fr.citizen.statues.network;

import fr.citizen.statues.CitizenStatues;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record VoiceIntentPayload(String statueId, String utterance) implements CustomPacketPayload {
    public static final Type<VoiceIntentPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CitizenStatues.MOD_ID, "voice_intent"));
    public static final StreamCodec<RegistryFriendlyByteBuf, VoiceIntentPayload> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.STRING_UTF8, VoiceIntentPayload::statueId,
                    ByteBufCodecs.STRING_UTF8, VoiceIntentPayload::utterance, VoiceIntentPayload::new);

    public VoiceIntentPayload {
        if (statueId == null || statueId.isBlank() || statueId.length() > 128
                || utterance == null || utterance.isBlank() || utterance.length() > 256) {
            throw new IllegalArgumentException("Invalid voice intent");
        }
    }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
