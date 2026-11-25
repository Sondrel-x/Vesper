import java.io.IOException;
import java.nio.file.*;
import java.util.*;

final class FrameSettings {
    static boolean vsync(Engine.Profile profile)throws IOException{
        Path file=profile.game().resolve("options.txt");
        if(Files.exists(file))for(String line:Files.readAllLines(file))
            if(line.startsWith("enableVsync:"))return Boolean.parseBoolean(line.substring(12));
        return true;
    }
    static void vsync(Engine.Profile profile,boolean enabled)throws IOException{
        setOption(profile,"enableVsync",Boolean.toString(enabled));
    }
    static String option(Engine.Profile profile,String key,String fallback)throws IOException{
        Path file=profile.game().resolve("options.txt");
        if(Files.exists(file))for(String line:Files.readAllLines(file))if(line.startsWith(key+":"))return line.substring(key.length()+1);
        return fallback;
    }
    static void setOption(Engine.Profile profile,String key,String value)throws IOException{
        writeOptions(profile,Map.of(key,value));
    }
    static Path menuBackup(Engine.Profile profile){return profile.dir().resolve("menu-stutter-backup.properties");}
    static void menuWorkaround(Engine.Profile profile,boolean enable)throws IOException{
        Path backup=menuBackup(profile);
        var replacements=new LinkedHashMap<String,String>();
        replacements.put("rawMouseInput","false");
        replacements.put("menuBackgroundBlurriness","0");
        if(enable){
            if(!Files.exists(backup)){
                var saved=new Properties();
                for(String key:replacements.keySet()){
                    String old=option(profile,key,null);
                    if(old!=null)saved.setProperty(key,old);
                }
                Engine.save(backup,saved);
            }
            writeOptions(profile,replacements);
        }else if(Files.exists(backup)){
            var saved=Engine.read(backup);
            replacements.replaceAll((key,value)->saved.getProperty(key));
            writeOptions(profile,replacements);
            Files.delete(backup);
        }
    }
    private static void writeOptions(Engine.Profile profile,Map<String,String> values)throws IOException{
        Path file=profile.game().resolve("options.txt");
        var lines=Files.exists(file)?Files.readAllLines(file):new ArrayList<String>();
        lines.removeIf(line->{int i=line.indexOf(':');return i>=0&&values.containsKey(line.substring(0,i));});
        values.forEach((key,value)->{if(value!=null)lines.add(key+":"+value);});
        Path tmp=Files.createTempFile(profile.game(),"display",".part");
        try{Files.write(tmp,lines);Engine.commit(tmp,file);}finally{Files.deleteIfExists(tmp);}
    }
}
