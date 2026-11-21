package fr.citizen.statues.network;

import fr.citizen.statues.CitizenStatues;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/** Recognised speech hypotheses, best first (free dictation alternatives + destination-constrained result). */
public record VoiceIntentPayload(String statueId, List<String> hypotheses) implements CustomPacketPayload {
    public static final int MAX_HYPOTHESES = 6;
    public static final Type<VoiceIntentPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CitizenStatues.MOD_ID, "voice_intent"));
    public static final StreamCodec<RegistryFriendlyByteBuf, VoiceIntentPayload> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.STRING_UTF8, VoiceIntentPayload::statueId,
                    ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(MAX_HYPOTHESES)), VoiceIntentPayload::hypotheses,
                    VoiceIntentPayload::new);

    public VoiceIntentPayload {
        if (statueId == null || statueId.isBlank() || statueId.length() > 128
                || hypotheses == null || hypotheses.isEmpty() || hypotheses.size() > MAX_HYPOTHESES
                || hypotheses.stream().anyMatch(h -> h == null || h.isBlank() || h.length() > 256)) {
            throw new IllegalArgumentException("Invalid voice intent");
        }
        hypotheses = List.copyOf(hypotheses);
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
