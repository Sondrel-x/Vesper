import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

final class PlayHistory {
    static void started(Engine.Profile p)throws IOException{
        var props=new Properties();props.setProperty("started",Long.toString(System.currentTimeMillis()));
        Engine.save(p.dir().resolve("play-history.properties"),props);
    }
    record Entry(Engine.Profile profile,long time){}
    final Map<Path,LogTime> logs=new HashMap<>();
    record LogTime(long modified,long size,long played){}
    long fromLog(Engine.Profile p){
        Path path=p.game().resolve("logs/latest.log");try{
            long modified=Files.getLastModifiedTime(path).toMillis(),size=Files.size(path);
            var old=logs.get(path);if(old!=null&&old.modified==modified&&old.size==size)return old.played;
            long played=0;try(var file=new RandomAccessFile(path.toFile(),"r")){
                // Read bounded samples even when an older instance has a large log.
                for(long offset:new long[]{0,Math.max(0,size-65536)}){
                    file.seek(offset);byte[] bytes=new byte[65536];int n=file.read(bytes);if(n<=0)break;
                    String data=new String(bytes,0,n,StandardCharsets.UTF_8);
                    if(data.contains("Sound engine started")||data.contains("Starting integrated minecraft server")||data.contains("[Render thread/INFO]: Connecting to ")){played=modified;break;}
                    if(size<=65536)break;
                }
            }logs.put(path,new LogTime(modified,size,played));return played;
        }catch(IOException ignored){return 0;}
    }
    Entry latest(Iterable<Engine.Profile> profiles)throws IOException{
        Entry latest=null;
        for(var p:profiles){
            long saved=0;try{saved=Long.parseLong(Engine.read(p.dir().resolve("play-history.properties")).getProperty("started","0"));}catch(NumberFormatException ignored){}
            long time=Math.max(saved,fromLog(p));if(time>0&&(latest==null||time>latest.time))latest=new Entry(p,time);
        }return latest;
    }
}
