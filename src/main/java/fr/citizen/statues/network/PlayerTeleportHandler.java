package fr.citizen.statues.network;
import fr.citizen.statues.security.TeleportRequestService;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
public final class PlayerTeleportHandler {
 private PlayerTeleportHandler(){}
 public static void handle(PlayerTeleportPayload payload,net.minecraft.world.entity.player.Player player){
  if(!(player instanceof ServerPlayer actor)||actor.getServer()==null)return;
  var result=TeleportRequestService.request(actor,actor.getServer(),payload.statueId(),payload.destinationId(),System.currentTimeMillis());
  switch(result){
   case TELEPORTED->actor.sendSystemMessage(Component.literal("Téléportation réussie.").withStyle(net.minecraft.ChatFormatting.GREEN));
   case COOLDOWN->actor.sendSystemMessage(Component.literal("Patientez avant de recommencer.").withStyle(net.minecraft.ChatFormatting.YELLOW));
   default->actor.sendSystemMessage(Component.literal("Cette destination n'est plus disponible.").withStyle(net.minecraft.ChatFormatting.RED));
  }
 }
}
