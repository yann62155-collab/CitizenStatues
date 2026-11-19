package fr.citizen.statues.network;

import fr.citizen.statues.CitizenStatues;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/** Server-authoritative, read-only snapshot used to populate the admin screen. */
public record AdminSnapshotPayload(List<String> lines) implements CustomPacketPayload {
    public static final Type<AdminSnapshotPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CitizenStatues.MOD_ID, "admin_snapshot"));

    public static final StreamCodec<RegistryFriendlyByteBuf, AdminSnapshotPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()),
                    AdminSnapshotPayload::lines,
                    AdminSnapshotPayload::new);

    public AdminSnapshotPayload {
        lines = List.copyOf(lines == null ? List.of() : lines);
        if (lines.size() > 256 || lines.stream().anyMatch(line -> line.length() > 256)) {
            throw new IllegalArgumentException("Admin snapshot is too large");
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
