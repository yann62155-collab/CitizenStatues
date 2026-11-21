package fr.citizen.statues.network;

import fr.citizen.statues.CitizenStatues;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record VoiceIntentResultPayload(int action, String message) implements CustomPacketPayload {
    /** What the client must do next. */
    public enum Action { TELEPORTED, RETRY, OPEN_MENU, CANCELLED, UNAVAILABLE }

    public static final Type<VoiceIntentResultPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CitizenStatues.MOD_ID, "voice_intent_result"));
    public static final StreamCodec<RegistryFriendlyByteBuf, VoiceIntentResultPayload> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.VAR_INT, VoiceIntentResultPayload::action,
                    ByteBufCodecs.STRING_UTF8, VoiceIntentResultPayload::message, VoiceIntentResultPayload::new);

    public VoiceIntentResultPayload(Action action, String message) {
        this(action.ordinal(), message);
    }

    public VoiceIntentResultPayload {
        if (action < 0 || action >= Action.values().length) throw new IllegalArgumentException("Invalid voice action");
        if (message == null) message = "";
        if (message.length() > 256) message = message.substring(0, 253) + "…";
    }

    public Action actionType() { return Action.values()[action]; }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
