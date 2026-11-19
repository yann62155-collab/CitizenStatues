package fr.citizen.statues.network;
import fr.citizen.statues.CitizenStatues;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
public record PlayerTeleportPayload(String statueId,String destinationId) implements CustomPacketPayload {
 public static final Type<PlayerTeleportPayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(CitizenStatues.MOD_ID,"player_teleport"));
 public static final StreamCodec<RegistryFriendlyByteBuf,PlayerTeleportPayload> STREAM_CODEC=StreamCodec.composite(ByteBufCodecs.STRING_UTF8,PlayerTeleportPayload::statueId,ByteBufCodecs.STRING_UTF8,PlayerTeleportPayload::destinationId,PlayerTeleportPayload::new);
 public PlayerTeleportPayload { if(statueId==null||statueId.isBlank()||statueId.length()>128||destinationId==null||destinationId.isBlank()||destinationId.length()>128) throw new IllegalArgumentException("Invalid teleport request"); }
 @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
