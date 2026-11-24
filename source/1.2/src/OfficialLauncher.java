import com.google.gson.*;
import java.awt.Desktop;
import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

final class OfficialLauncher {
    record App(String storeId,Path executable){
        boolean store(){return storeId!=null;}
        void open()throws IOException{
            if(store())new ProcessBuilder("explorer.exe","shell:AppsFolder\\"+storeId).start();
            else new ProcessBuilder(executable.toString()).start();
        }
    }
    static App locate()throws Exception{
        String script="$ErrorActionPreference='Stop'; (Get-StartApps | Where-Object { $_.AppID -like 'Microsoft.4297127D64EC6_8wekyb3d8bbwe!*' } | Select-Object -First 1).AppID";
        Path windows=Path.of(System.getenv("SystemRoot"));
        var process=new ProcessBuilder(windows.resolve("System32/WindowsPowerShell/v1.0/powershell.exe").toString(),"-NoProfile","-NonInteractive","-Command",script).redirectError(ProcessBuilder.Redirect.DISCARD).start();
        if(process.waitFor(15,TimeUnit.SECONDS)){String id=new String(process.getInputStream().readAllBytes(),StandardCharsets.UTF_8).trim();
            if(id.matches("(?i)Microsoft\\.4297127D64EC6_8wekyb3d8bbwe![A-Za-z0-9._-]+"))return new App(id,null);
        }else process.destroyForcibly();
        for(String env:List.of("ProgramFiles(x86)","ProgramFiles","LOCALAPPDATA")){String dir=System.getenv(env);if(dir==null)continue;
            for(String sub:List.of("Minecraft Launcher","Programs/Minecraft Launcher")){Path path=Path.of(dir,sub,"MinecraftLauncher.exe");if(Files.isRegularFile(path))return new App(null,path);}}
        return null;
    }
    static void writeJson(Path path,JsonObject data)throws IOException{
        Files.createDirectories(path.getParent());Path tmp=Files.createTempFile(path.getParent(),"sonder-",".tmp");
        try{Files.writeString(tmp,data.toString(),StandardCharsets.UTF_8);Engine.commit(tmp,path);}finally{Files.deleteIfExists(tmp);}
    }
    static boolean running(){return ProcessHandle.allProcesses().anyMatch(p->p.info().command().map(c->Path.of(c).getFileName().toString().equalsIgnoreCase("MinecraftLauncher.exe")).orElse(false));}
    static void configure(Path minecraft,Engine.Profile p,boolean store,boolean launcherRunning)throws Exception{
        boolean modded=p.loader().equals("Fabric");
        JsonObject fabric=modded?Modpack.loader(p,true):null;
        String version=modded?Net.str(fabric,"id"):p.version();
        Path manifest=minecraft.resolve("versions").resolve(version).resolve(version+".json");
        Path file=minecraft.resolve(store?"launcher_profiles_microsoft_store.json":"launcher_profiles.json");
        String original=Files.exists(file)?Files.readString(file):null;
        JsonObject root=original==null?new JsonObject():JsonParser.parseString(original).getAsJsonObject();
        if(!root.has("profiles"))root.add("profiles",new JsonObject());
        var profiles=root.getAsJsonObject("profiles");
        String key="sonder-"+UUID.nameUUIDFromBytes(p.dir().toAbsolutePath().normalize().toString().getBytes(StandardCharsets.UTF_8));
        JsonObject previous=profiles.has(key)?profiles.getAsJsonObject(key):new JsonObject();
        var entry=previous.deepCopy();entry.addProperty("type","custom");entry.addProperty("name",p.name());entry.addProperty("gameDir",p.game().toAbsolutePath().toString());entry.addProperty("lastVersionId",version);
        boolean changed=!entry.equals(previous);
        boolean installManifest=modded&&!Files.exists(manifest);
        if(modded&&!installManifest){
            // Upgrade only the version descriptor written by Sonder 0.5.
            JsonElement installed=JsonParser.parseString(Files.readString(manifest));
            installManifest=installed.equals(Game.resource("/fabric.json"));
        }
        if((changed||installManifest)&&launcherRunning)throw new IOException("Close the official Minecraft Launcher, then click Play via Minecraft Launcher again so it can load the new installation.");
        if(installManifest)writeJson(manifest,fabric);
        if(changed){
            if(original==null?Files.exists(file):!original.equals(Files.readString(file)))throw new IOException("Minecraft updated its installations. Click Play via Minecraft Launcher again.");
            profiles.add(key,entry);writeJson(file,root);
        }
    }
    static String handoff(Engine e,Engine.Profile p,Consumer<String> updates)throws Exception{
        App app=locate();if(app==null){Desktop.getDesktop().browse(URI.create("https://www.minecraft.net/download"));throw new IOException("Install the official Minecraft Launcher from the download page, then click Play via Minecraft Launcher again.");}
        updates.accept("Preparing "+p.name()+"…");e.prepare(p,updates);Compatibility.check(e,p,updates);
        updates.accept("Setting up the official installation…");
        configure(Path.of(System.getenv("APPDATA"),".minecraft"),p,app.store(),running());
        app.open();return "In Minecraft Launcher, choose Java Edition → "+p.name()+" → Play.";
    }
}
