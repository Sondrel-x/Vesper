import com.google.gson.*;
import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;
import java.util.zip.*;

final class Modpack {
    static final long MAX_FILE = 512L << 20, MAX_PACK = 4L << 30;
    static JsonObject json(InputStream stream, int limit) throws IOException {
        try(stream) {
            byte[] bytes=stream.readNBytes(limit+1);
            if(bytes.length>limit) throw new IOException("Metadata is too large.");
            return JsonParser.parseString(new String(bytes,StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
    static Path target(Path root,String name) throws IOException {
        for(String part:name.split("/",-1)) {
            if(part.isBlank() || part.endsWith(".") || part.endsWith(" ") || part.matches(".*[\\\\:<>\"|?*\\p{Cntrl}].*") ||
                part.matches("(?i)(con|prn|aux|nul|com[1-9]|lpt[1-9])([.].*)?"))
                throw new IOException("Invalid modpack path: "+name);
        }
        return root.resolve(name);
    }
    static void removeTree(Path root)throws IOException {
        if(Files.exists(root))try(var paths=Files.walk(root)){
            for(Path p:paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(p);
        }
    }
    static Engine.Profile install(Engine engine,Path archive,Consumer<String> progress)throws Exception {
        Path stage=Files.createTempDirectory(engine.root,"import-");
        try(var zip=new ZipFile(archive.toFile())) {
            var index=zip.getEntry("modrinth.index.json");
            if(index==null) throw new IOException("Choose a Modrinth modpack (.mrpack) with modrinth.index.json.");
            var manifest=json(zip.getInputStream(index),4<<20);
            if(!"minecraft".equals(Net.str(manifest,"game"))||manifest.get("formatVersion").getAsInt()!=1)
                throw new IOException("Unsupported modpack format.");
            var deps=manifest.getAsJsonObject("dependencies");
            String mc=Net.str(deps,"minecraft"),fabric=Net.str(deps,"fabric-loader");
            if(!List.of(Engine.VERSIONS).contains(mc)||!Set.of("minecraft","fabric-loader").containsAll(deps.keySet()))
                throw new IOException("This pack needs an unsupported Minecraft version or mod loader: "+deps);
            String loader=fabric.isEmpty()?"Vanilla":"Fabric";
            if(!fabric.isEmpty()&&!fabric.equals(Engine.LOADER))saveLoader(stage,mc,fabric);
            String base=Net.str(manifest,"name").strip();
            if(base.isEmpty())base="Imported pack";
            Set<String> used=new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
            for(var p:engine.profiles())used.add(p.name());
            String name=base;for(int n=2;used.contains(name);n++)name=base+" ("+n+")";
            var profile=new Engine.Profile(stage,name,"Custom",mc,loader);
            Path game=profile.game();Files.createDirectories(game.resolve("mods"));
            long total=0; int count=0;
            for(var item:manifest.getAsJsonArray("files")) {
                var f=item.getAsJsonObject();
                if(f.has("env")&&"unsupported".equals(Net.str(f.getAsJsonObject("env"),"client")))continue;
                Path dest=target(game,Net.str(f,"path"));
                long size=f.get("fileSize").getAsLong();total+=size;
                if(size<0||size>MAX_FILE||total>MAX_PACK||++count>20000)throw new IOException("Pack exceeds import limits.");
                String hash=Net.str(f.getAsJsonObject("hashes"),"sha512");
                if(!hash.matches("(?i)[a-f0-9]{128}"))throw new IOException("Missing SHA-512 for "+dest.getFileName());
                progress.accept("Importing "+dest.getFileName());
                if (dest.startsWith(game.resolve("mods")) && dest.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar")) {
                    var urls=new ArrayList<String>();
                    for(var mirror:f.getAsJsonArray("downloads")) if(mirror.getAsString().startsWith("https://"))urls.add(mirror.getAsString());
                    ModDownload.install(engine,new ModDownload.Request(dest.getFileName().toString(),hash,size,urls),dest,false);
                    continue;
                }
                IOException error=new IOException("No supported download for "+dest.getFileName());
                boolean ok=false;
                for(var mirror:f.getAsJsonArray("downloads")) {
                    String url=mirror.getAsString();
                    if(!"https".equalsIgnoreCase(URI.create(url).getScheme()))continue;
                    try{Net.download(url,dest,"SHA-512",hash,size);ok=true;break;}catch(IOException ex){error=ex;}
                }
                if(!ok)throw error;
            }
            for(String folder:List.of("overrides/","client-overrides/")) {
                var entries=zip.entries();
                while(entries.hasMoreElements()) {
                    var entry=entries.nextElement();if(entry.isDirectory()||!entry.getName().startsWith(folder))continue;
                    Path dest=target(game,entry.getName().substring(folder.length()));
                    long size=entry.getSize();total+=size;
                    if(size<0||size>MAX_FILE||total>MAX_PACK||++count>20000)throw new IOException("Pack exceeds import limits.");
                    Files.createDirectories(dest.getParent());
                    try(var in=zip.getInputStream(entry);var out=Files.newOutputStream(dest)){Net.copyLimited(in,out,size);}
                }
            }
            var props=new Properties();props.setProperty("name",name);props.setProperty("kind","Custom");props.setProperty("version",mc);props.setProperty("loader",loader);
            Engine.save(stage.resolve("sonder.properties"),props);
            if(profile.hud()){var state=new Properties();state.setProperty("mods/Sonder-HUD.jar","removed");Engine.save(Content.stateFile(profile),state);}
            Path dir=engine.root.resolve("instances/sonder-"+UUID.randomUUID());
            Files.move(stage,dir,StandardCopyOption.ATOMIC_MOVE);
            return new Engine.Profile(dir,name,"Custom",mc,loader);
        } finally {removeTree(stage);}
    }
    static void saveLoader(Path stage,String mc,String version)throws Exception {
        if(!version.matches("[0-9A-Za-z.+-]{1,64}"))throw new IOException("Invalid Fabric loader version.");
        var official=Net.get("https://meta.fabricmc.net/v2/versions/loader/"+mc+"/"+version+"/profile/json").getAsJsonObject();
        var direct=official.deepCopy();
        for(var entry:direct.getAsJsonArray("libraries")){
            var lib=entry.getAsJsonObject();var parts=Net.str(lib,"name").split(":");
            if(parts.length!=3)throw new IOException("Unsupported Fabric library "+lib);
            String path=parts[0].replace('.','/')+"/"+parts[1]+"/"+parts[2]+"/"+parts[1]+"-"+parts[2]+".jar";
            String url=Net.str(lib,"url")+path,sha=Net.str(lib,"sha1");
            if(!sha.matches("(?i)[0-9a-f]{40}")){
                var res=Net.http.send(Net.req(url+".sha1").build(),java.net.http.HttpResponse.BodyHandlers.ofString());
                sha=res.body().strip();
                if(res.statusCode()!=200||!sha.matches("(?i)[0-9a-f]{40}"))throw new IOException("Missing checksum for "+path);
            }
            lib.add("downloads",Net.obj("artifact",Net.obj("path",path,"url",url,"sha1",sha)));
        }
        OfficialLauncher.writeJson(stage.resolve("fabric-import.json"),direct);
        OfficialLauncher.writeJson(stage.resolve("fabric-official-import.json"),official);
    }
    static JsonObject loader(Engine.Profile p,boolean official)throws IOException {
        Path f=p.dir().resolve(official?"fabric-official-import.json":"fabric-import.json");
        if(Files.isRegularFile(f))return json(Files.newInputStream(f),4<<20);
        return Game.resource(official?(p.version().equals(Engine.MC)?"/fabric-official.json":p.resource("official")):p.resource("fabric"));
    }
}
