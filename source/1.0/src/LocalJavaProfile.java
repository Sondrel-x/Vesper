import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Match display metadata from the official launcher to the signed-in account. */
final class LocalJavaProfile {
    record Player(String uuid, String name) {}
    static Player match(Path folder,String email,String xid,String tag) {
        Set<Player> matches=new HashSet<>();
        for(String file:List.of("launcher_accounts.json","launcher_accounts_microsoft_store.json")){
            try(var input=new JsonReader(Files.newBufferedReader(folder.resolve(file)))){
                input.beginObject();
                while(input.hasNext()){
                    if(!"accounts".equals(input.nextName())){input.skipValue();continue;}
                    input.beginObject();
                    while(input.hasNext()){input.nextName();Player player=readAccount(input,email,xid,tag);if(player!=null)matches.add(player);}
                    input.endObject();
                }
                input.endObject();
            }catch(IOException|IllegalStateException ignored){}
        }
        return matches.size()==1?matches.iterator().next():null;
    }
    static Player readAccount(JsonReader in,String email,String xid,String tag)throws IOException{
        String id="",name="",login="",xbox="",remote="",gamertag="";
        in.beginObject();
        while(in.hasNext()){
            String key=in.nextName();
            if(key.equals("minecraftProfile")&&in.peek()==JsonToken.BEGIN_OBJECT){
                in.beginObject();while(in.hasNext()){String field=in.nextName();
                    if(field.equals("id"))id=string(in);else if(field.equals("name"))name=string(in);else in.skipValue();}
                in.endObject();
            }else switch(key){
                case "username" -> login=string(in);
                case "xuid","xboxUserId" -> xbox=string(in);
                case "remoteId" -> remote=string(in);
                case "gamertag","gamerTag" -> gamertag=string(in);
                default -> in.skipValue();
            }
        }
        in.endObject();id=id.replace("-","");
        boolean matched=(!email.isBlank()&&email.equalsIgnoreCase(login))||(!xid.isBlank()&&(xid.equals(xbox)||xid.equals(remote)))||(!tag.isBlank()&&tag.equalsIgnoreCase(gamertag));
        return matched&&id.matches("[a-fA-F0-9]{32}")&&name.matches("[A-Za-z0-9_]{1,16}")?new Player(id.toLowerCase(Locale.ROOT),name):null;
    }
    static String string(JsonReader in)throws IOException{
        if(in.peek()==JsonToken.STRING||in.peek()==JsonToken.NUMBER)return in.nextString();
        in.skipValue();return "";
    }
}
