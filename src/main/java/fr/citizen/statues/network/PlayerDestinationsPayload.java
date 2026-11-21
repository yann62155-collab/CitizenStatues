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

 /**
  * Encodes enabled destinations as "id|name|description|icon|dimension|synonym;synonym".
  * Synonyms let the client constrain speech recognition to the words this statue really expects.
  */
 public static List<String> encode(fr.citizen.statues.data.StatueProfile profile){
  java.util.Map<String,java.util.List<String>> synonyms=new java.util.HashMap<>();
  profile.synonyms().forEach((phrase,target)->synonyms.computeIfAbsent(target,k->new java.util.ArrayList<>()).add(phrase));
  return profile.destinations().values().stream()
   .filter(fr.citizen.statues.data.Destination::enabled)
   .sorted(java.util.Comparator.comparing(fr.citizen.statues.data.Destination::name,String.CASE_INSENSITIVE_ORDER))
   .limit(128)
   .map(d->{
    String base=d.id()+"|"+clean(d.name())+"|"+clean(d.description())+"|"+clean(d.icon())+"|"+d.dimension();
    if(base.length()>500) base=base.substring(0,500);
    StringBuilder syn=new StringBuilder();
    for(String phrase:synonyms.getOrDefault(d.id(),java.util.List.of())){
     String c=clean(phrase).replace(";",",");
     if(base.length()+syn.length()+c.length()+2>512) break;
     if(syn.length()>0) syn.append(';');
     syn.append(c);
    }
    return base+"|"+syn;
   }).toList();
 }
 private static String clean(String v){return v==null?"":v.replace("|","/").replace("\n"," ");}
}
