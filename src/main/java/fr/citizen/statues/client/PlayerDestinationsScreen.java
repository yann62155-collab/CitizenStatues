package fr.citizen.statues.client;
import fr.citizen.statues.network.PlayerTeleportPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.ArrayList;
import java.util.List;
public final class PlayerDestinationsScreen extends Screen {
 private final String statueId; private final List<String[]> entries=new ArrayList<>();
 public PlayerDestinationsScreen(String statueId,String title,List<String> destinations){
  super(Component.literal(title));this.statueId=statueId;
  for(String line:destinations){String[] p=line.split("\\|",3);if(p.length>=2)entries.add(new String[]{p[0],p[1],p.length==3?p[2]:""});}
 }
 @Override protected void init(){
  int panel=Math.min(300,width-24),left=(width-panel)/2,visible=Math.min(entries.size(),8);
  int top=Math.max(35,(height-visible*27-70)/2);
  addRenderableWidget(Button.builder(Component.literal("Fermer"),b->onClose()).bounds(width/2-40,height-30,80,20).build());
  for(int i=0;i<visible;i++){String[] e=entries.get(i);addRenderableWidget(Button.builder(Component.literal("➜ "+e[1]),b->PacketDistributor.sendToServer(new PlayerTeleportPayload(statueId,e[0]))).bounds(left,top+i*27,panel,22).build());}
 }
 @Override public void render(GuiGraphics g,int mx,int my,float pt){
  renderBackground(g,mx,my,pt);g.drawCenteredString(font,title,width/2,18,0xFFFFFFFF);
  if(entries.isEmpty())g.drawCenteredString(font,Component.literal("Aucune destination disponible."),width/2,height/2,0xFFAAAAAA);
  super.render(g,mx,my,pt);
 }
 @Override public boolean isPauseScreen(){return false;}
}
