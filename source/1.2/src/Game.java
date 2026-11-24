import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

final class Game {
    final Path root;
    final JsonObject vanilla,fabric;
    final String os=System.getProperty("os.name").startsWith("Windows")?"windows":"linux";
    Game(Path data,Engine.Profile p)throws Exception{
        root=data.resolve("game");vanilla=resource(p.resource("game"));
        if(p.loader().equals("Fabric"))fabric=Modpack.loader(p,false);
        else fabric=Net.obj("libraries",List.of(),"arguments",Net.obj("jvm",List.of(),"game",List.of()),"mainClass",Net.str(vanilla,"mainClass"));
    }
    static JsonObject resource(String name)throws IOException{try(var in=Game.class.getResourceAsStream(name)){return JsonParser.parseString(new String(in.readAllBytes(),StandardCharsets.UTF_8)).getAsJsonObject();}}
    record Asset(String url,Path path,String sha){}
    static Asset file(JsonObject a,Path path){return new Asset(Net.str(a,"url"),path,Net.str(a,"sha1"));}
    boolean allowed(JsonObject obj,boolean demo){
        if(!obj.has("rules"))return true;boolean allow=false;
        for(var item:obj.getAsJsonArray("rules")){var r=item.getAsJsonObject();boolean match=true;
            if(r.has("os")){var env=r.getAsJsonObject("os");if(env.has("name"))match=os.equals(Net.str(env,"name"));if(env.has("arch"))match&=System.getProperty("os.arch").equals(Net.str(env,"arch"));}
            if(r.has("features"))for(var f:r.getAsJsonObject("features").entrySet())match&=f.getValue().getAsBoolean()==(f.getKey().equals("is_demo_user")&&demo);
            if(match)allow="allow".equals(Net.str(r,"action"));
        }return allow;
    }
    boolean verified(Asset a,Properties stamps)throws Exception{
        java.nio.file.attribute.BasicFileAttributes attrs;
        try{attrs=Files.readAttributes(a.path,java.nio.file.attribute.BasicFileAttributes.class);}
        catch(NoSuchFileException missing){return false;}
        if(!attrs.isRegularFile())return false;
        String stamp=attrs.size()+"|"+attrs.lastModifiedTime();
        String key=root.relativize(a.path).toString()+":"+a.sha;
        if(stamp.equals(stamps.getProperty(key)))return true;
        var hash=java.security.MessageDigest.getInstance("SHA-1");try(var in=new java.security.DigestInputStream(Files.newInputStream(a.path),hash)){in.transferTo(OutputStream.nullOutputStream());}
        if(!HexFormat.of().formatHex(hash.digest()).equals(a.sha))return false;stamps.setProperty(key,stamp);return true;
    }
    static class DownloadFailure extends IOException {
        DownloadFailure(String file, String host, IOException cause) {
            super("Could not download " + file + " from " + host + " after 3 attempts.\n" + cause.getMessage(), cause);
        }
    }

    boolean reuseInstalled(Asset asset, Path minecraft) throws Exception {
        Path relative = root.relativize(asset.path);
        Path source = minecraft.resolve(relative);
        if (relative.startsWith("versions") && relative.getNameCount() == 3 && relative.endsWith("client.jar")) {
            String version = relative.getName(1).toString();
            source = source.resolveSibling(version + ".jar");
        }
        if (!Files.isRegularFile(source)) return false;
        Files.createDirectories(asset.path.getParent());
        Path temp = Files.createTempFile(asset.path.getParent(), "local", ".part");
        try {
            Files.copy(source, temp, StandardCopyOption.REPLACE_EXISTING);
            var digest = java.security.MessageDigest.getInstance("SHA-1");
            try (var in = new java.security.DigestInputStream(Files.newInputStream(temp), digest)) {
                in.transferTo(OutputStream.nullOutputStream());
            }
            if (!HexFormat.of().formatHex(digest.digest()).equalsIgnoreCase(asset.sha)) return false;
            Engine.commit(temp, asset.path);
            return true;
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    void fetch(Asset a,Properties stamps)throws Exception{
        String appData = System.getenv("APPDATA");
        boolean copied = false;
        if (appData != null) {
            try { copied = reuseInstalled(a, Path.of(appData, ".minecraft")); }
            catch (IOException ignored) { /* Use the network when the local copy is inaccessible. */ }
        }
        if (!copied) {
            for (int attempt = 1; ; attempt++) {
                try { Net.download(a.url, a.path, "SHA-1", a.sha); break; }
                catch (IOException ex) {
                    if (attempt == 3) throw new DownloadFailure(root.relativize(a.path).toString(), java.net.URI.create(a.url).getHost(), ex);
                    Thread.sleep(350L * attempt);
                }
            }
        }
        stamps.setProperty(root.relativize(a.path).toString()+":"+a.sha,Files.size(a.path)+"|"+Files.getLastModifiedTime(a.path));
    }
    List<Asset> checkFiles(Collection<Asset> downloads,Properties stamps)throws Exception{
        var entries=new ArrayList<>(downloads);var missing=new ArrayList<Asset>();
        int workers=Math.min(4,Math.max(1,Runtime.getRuntime().availableProcessors()));
        int chunk=Math.max(1,(entries.size()+workers-1)/workers);
        try(var pool=Executors.newFixedThreadPool(workers)){
            var tasks=new ArrayList<Future<List<Asset>>>();
            for(int start=0;start<entries.size();start+=chunk){
                var batch=entries.subList(start,Math.min(start+chunk,entries.size()));
                tasks.add(pool.submit(()->{
                    var needed=new ArrayList<Asset>();
                    for(var entry:batch)if(!verified(entry,stamps))needed.add(entry);
                    return needed;
                }));
            }
            for(var task:tasks)missing.addAll(task.get());
        }
        return missing;
    }
    static void args(List<String> cmd,JsonArray entries,Map<String,String> vars,Game game,boolean demo){
        for(var e:entries){if(e.isJsonObject()&&!game.allowed(e.getAsJsonObject(),demo))continue;
            var v=e.isJsonObject()?e.getAsJsonObject().get("value"):e;
            for(var token:v.isJsonArray()?v.getAsJsonArray().asList():List.of(v)){
                String s=token.getAsString();for(var kv:vars.entrySet())s=s.replace("${"+kv.getKey()+"}",kv.getValue());cmd.add(s);}
        }
    }
    Path runtime(Consumer<String> status)throws Exception{
        int required=vanilla.getAsJsonObject("javaVersion").get("majorVersion").getAsInt();
        if(required==Runtime.version().feature())return Path.of(System.getProperty("java.home"),"bin",os.equals("windows")?"javaw.exe":"java");
        if(required!=25||System.getenv("SONDER_EXECUTABLE")==null)throw new IOException("This game version needs Java "+required+". Use Play via Minecraft Launcher on this device.");
        status.accept("Preparing Java 25 for Minecraft…");
        var helper=new ProcessBuilder(System.getenv("SONDER_EXECUTABLE"),"--game-java25").redirectErrorStream(true).start();
        List<String> output;try(var reader=helper.inputReader(java.nio.charset.StandardCharsets.UTF_8)){output=reader.lines().toList();}
        if(helper.waitFor()!=0||output.isEmpty())throw new IOException("Could not prepare Java 25. Check your connection and try again.");
        Path executable=Path.of(output.getLast());if(!Files.isRegularFile(executable))throw new IOException("Java 25 installation is incomplete.");
        return executable;
    }
    Process launch(Engine.Profile profile,Accounts.Session user,int memory,Consumer<String> status)throws Exception{
        Path executable=runtime(status);Files.createDirectories(root);Path libs=root.resolve("libraries"),assets=root.resolve("assets");
        var index=vanilla.getAsJsonObject("assetIndex");String id=Net.str(index,"id");
        var indexFile=file(index,assets.resolve("indexes").resolve(id+".json"));status.accept("Checking game files…");Path saved=root.resolve("verified-game.properties");var stamps=Engine.read(saved);var previous=(Properties)stamps.clone();
        if(!verified(indexFile,stamps))fetch(indexFile,stamps);
        var downloads=new LinkedHashMap<Path,Asset>();var cp=new LinkedHashSet<String>();
        for(var manifest:List.of(fabric,vanilla))for(var el:manifest.getAsJsonArray("libraries")){
            var l=el.getAsJsonObject();if(!allowed(l,false))continue;
            var meta=l.getAsJsonObject("downloads").getAsJsonObject("artifact");var a=file(meta,libs.resolve(Net.str(meta,"path")));downloads.put(a.path,a);cp.add(a.path.toString());
        }
        var client=file(vanilla.getAsJsonObject("downloads").getAsJsonObject("client"),root.resolve("versions/"+profile.version()+"/client.jar"));downloads.put(client.path,client);cp.add(client.path.toString());
        var log=vanilla.getAsJsonObject("logging").getAsJsonObject("client");var logFile=log.getAsJsonObject("file");var logAsset=file(logFile,assets.resolve("log_configs/"+Net.str(logFile,"id")));downloads.put(logAsset.path,logAsset);
        try(var r=Files.newBufferedReader(indexFile.path)){
            for(var el:JsonParser.parseReader(r).getAsJsonObject().getAsJsonObject("objects").entrySet()){
                String hash=Net.str(el.getValue().getAsJsonObject(),"hash");String part=hash.substring(0,2)+"/"+hash;
                var a=new Asset("https://resources.download.minecraft.net/"+part,assets.resolve("objects").resolve(part),hash);downloads.put(a.path,a);
            }
        }
        var missing=checkFiles(downloads.values(),stamps);
        if(!missing.isEmpty()){var done=new AtomicInteger();status.accept(missing.size()==1 ? "Preparing " + root.relativize(missing.getFirst().path) + "…" : "Preparing "+missing.size()+" game files…");
            try(var pool=Executors.newFixedThreadPool(12)){var tasks=new ArrayList<Future<?>>();
                for(var a:missing)tasks.add(pool.submit(()->{fetch(a,stamps);int n=done.incrementAndGet();if(n%50==0||n==missing.size())status.accept("Game files  "+n+" / "+missing.size());return null;}));
                try{for(var f:tasks)f.get();}catch(Exception ex){pool.shutdownNow();throw ex;}
            }}
        if(!previous.equals(stamps))Engine.save(saved,stamps);
        Path natives=root.resolve("natives");Files.createDirectories(natives);
        Map<String,String> vars=new HashMap<>(Map.of("auth_player_name",user.name(),"auth_uuid",user.uuid(),"auth_access_token",user.token(),"clientid",user.app(),"auth_xuid",user.xuid(),"version_name",profile.version(),"version_type","release","game_directory",profile.game().toString(),"assets_root",assets.toString(),"assets_index_name",id));
        vars.put("user_type","msa");vars.put("classpath",String.join(File.pathSeparator,cp));vars.put("natives_directory",natives.toString());vars.put("launcher_name","Vesper");vars.put("launcher_version",ReleaseUpdates.VERSION);
        var cmd=new ArrayList<String>();cmd.add(executable.toString());cmd.add("-Xmx"+memory+"G");
        args(cmd,vanilla.getAsJsonObject("arguments").getAsJsonArray("jvm"),vars,this,user.demo());
        args(cmd,fabric.getAsJsonObject("arguments").getAsJsonArray("jvm"),vars,this,user.demo());
        cmd.add(Net.str(log,"argument").replace("${path}",logAsset.path.toString()));
        cmd.add(Net.str(fabric,"mainClass"));args(cmd,vanilla.getAsJsonObject("arguments").getAsJsonArray("game"),vars,this,user.demo());args(cmd,fabric.getAsJsonObject("arguments").getAsJsonArray("game"),vars,this,user.demo());
        status.accept("Starting Minecraft…");Files.createDirectories(profile.game().resolve("logs"));
        // Java argfiles avoid Windows' command-line length limit for long profile paths. Only JVM arguments go in this file.
        int main=cmd.indexOf(Net.str(fabric,"mainClass"));var argfile=Files.createTempFile(root,"jvm-",".args");
        var quoted=cmd.subList(1,main).stream().map(s->"\""+s.replace("\\","\\\\").replace("\"","\\\"")+"\"").toList();
        Files.write(argfile,quoted,StandardCharsets.UTF_8);var direct=new ArrayList<String>(List.of(cmd.getFirst(),"@"+argfile));
        direct.addAll(cmd.subList(main,cmd.size()));
        try{var proc=new ProcessBuilder(direct).directory(profile.game().toFile()).redirectErrorStream(true).redirectOutput(profile.game().resolve("logs/sonder-launch.log").toFile()).start();
            proc.onExit().thenRun(()->{try{Files.deleteIfExists(argfile);}catch(IOException ignored){}});return proc;
        }catch(Exception ex){Files.deleteIfExists(argfile);throw ex;}
    }
}
