package fr.citizen.statues.network;
import fr.citizen.statues.CitizenStatues;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import java.util.List;
public record PlayerDestinationsPayload(String statueId, String title, List<String> destinations) implements CustomPacketPayload {
 public static final Type<PlayerDestinationsPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(CitizenStatues.MOD_ID,"player_destinations"));
 public static final StreamCodec<RegistryFriendlyByteBuf,PlayerDestinationsPayload> STREAM_CODEC=StreamCodec.composite(
 ByteBufCodecs.STRING_UTF8,PlayerDestinationsPayload::statueId,ByteBufCodecs.STRING_UTF8,PlayerDestinationsPayload::title,
 ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()),PlayerDestinationsPayload::destinations,PlayerDestinationsPayload::new);
 public PlayerDestinationsPayload { if(statueId==null||statueId.length()>128||title==null||title.length()>128||destinations==null||destinations.size()>128||destinations.stream().anyMatch(s->s==null||s.length()>512)) throw new IllegalArgumentException("Invalid destinations payload"); destinations=List.copyOf(destinations); }
 @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
