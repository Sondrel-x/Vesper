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
        Path file=profile.game().resolve("options.txt");
        var lines=Files.exists(file)?Files.readAllLines(file):new ArrayList<String>();
        lines.removeIf(line->line.startsWith("enableVsync:"));
        lines.add("enableVsync:"+enabled);
        Path tmp=Files.createTempFile(profile.game(),"display",".part");
        try{Files.write(tmp,lines);Engine.commit(tmp,file);}finally{Files.deleteIfExists(tmp);}
    }
}
