import com.google.gson.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;
import java.util.zip.*;
import javax.swing.*;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.metadata.version.VersionPredicate;

final class Compatibility {
    record Mod(Path jar, JsonObject data, boolean nested) {
        String id(){return Net.str(data,"id");}
        String version(){return Net.str(data,"version");}
    }
    record Issue(String text, String owner, String target) {}
    record Result(List<Issue> issues,Map<String,Path> files) {}
    record Stamp(Path path,long size,java.nio.file.attribute.FileTime modified){}
    private static final Map<Stamp,List<Mod>> cache=Collections.synchronizedMap(new LinkedHashMap<>(256,.75f,true){
        protected boolean removeEldestEntry(Map.Entry<Stamp,List<Mod>> entry){return size()>512;}
    });
    static List<Mod> read(Path jar)throws Exception {
        var key=new Stamp(jar,Files.size(jar),Files.getLastModifiedTime(jar));var cached=cache.get(key);
        if(cached!=null)return cached;
        var found=new ArrayList<Mod>();
        try(var zip=new ZipFile(jar.toFile())){
            var entry=zip.getEntry("fabric.mod.json");
            if(entry==null)return List.of();
            var meta=Modpack.json(zip.getInputStream(entry),1<<20);
            if(!"server".equals(Net.str(meta,"environment"))){
                found.add(new Mod(jar,meta,false));
                if(meta.has("jars"))for(var el:meta.getAsJsonArray("jars")){
                    var nested=zip.getEntry(Net.str(el.getAsJsonObject(),"file"));
                    if(nested==null||nested.getSize()>64L<<20)continue;
                    try(var in=new ZipInputStream(zip.getInputStream(nested))){
                        ZipEntry e;while((e=in.getNextEntry())!=null)if("fabric.mod.json".equals(e.getName())){
                            var m=Modpack.json(in,1<<20);
                            if(!"server".equals(Net.str(m,"environment")))found.add(new Mod(jar,m,true));break;
                        }
                    }
                }
            }
        }
        cache.put(key,found);return found;
    }
    static boolean matches(String version,JsonElement spec)throws Exception{
        if(spec.isJsonArray()){for(var alternative:spec.getAsJsonArray())if(matches(version,alternative))return true;return false;}
        return VersionPredicate.parse(spec.getAsString()).test(Version.parse(version));
    }
    static Result inspect(Engine.Profile p)throws Exception{
        var issues=new ArrayList<Issue>();var files=new TreeMap<String,Path>();
        if(!p.loader().equals("Fabric"))return new Result(issues,files);
        var mods=new ArrayList<Mod>();var versions=new HashMap<String,List<String>>();
        var manifest=Modpack.loader(p,false);
        String loader=Net.str(manifest,"id");
        versions.put("fabricloader",List.of(loader.substring("fabric-loader-".length(),loader.length()-p.version().length()-1)));
        versions.put("minecraft",List.of(p.version()));
        versions.put("java",List.of(Game.resource(p.resource("game")).getAsJsonObject("javaVersion").get("majorVersion").getAsString()));
        try(var paths=Files.list(p.game().resolve("mods"))){
            for(Path path:paths.filter(Files::isRegularFile).filter(f->f.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar")).sorted().toList()){
                for(var m:read(path)){
                    mods.add(m);var ids=new ArrayList<String>();ids.add(m.id());
                    if(m.data.has("provides"))for(var id:m.data.getAsJsonArray("provides"))ids.add(id.getAsString());
                    for(String id:ids)versions.computeIfAbsent(id,k->new ArrayList<>()).add(m.version());
                    if(!m.nested&&files.put(m.id(),path)!=null)issues.add(new Issue("Multiple installed JARs provide "+m.id(),m.id(),m.id()));
                }
            }
        }
        for(var m:mods){
            if(m.nested)continue;
            for(String rule:List.of("depends","breaks")){
                if(!m.data.has(rule))continue;
                for(var entry:m.data.getAsJsonObject(rule).entrySet()){
                    var found=versions.getOrDefault(entry.getKey(),List.of());
                    int matching=0;for(String v:found)if(matches(v,entry.getValue()))matching++;
                    boolean broken=rule.equals("depends")?matching==0:matching>0&&matching==found.size();
                    if(broken)issues.add(new Issue(m.id()+" "+m.version()+(rule.equals("depends")?" requires ":" conflicts with ")+entry.getKey()+" "+entry.getValue()+" (installed: "+(found.isEmpty()?"none":String.join(", ",found))+")",m.id(),entry.getKey()));
                }
            }
        }
        return new Result(issues,files);
    }
    static void check(Engine engine,Engine.Profile profile,Consumer<String> progress)throws Exception{
        Result r=inspect(profile);if(r.issues.isEmpty())return;
        String text=r.issues.stream().map(Issue::text).limit(10).reduce((a,b)->a+"\n"+b).orElse("");
        int[] answer={1};
        SwingUtilities.invokeAndWait(()->answer[0]=JOptionPane.showOptionDialog(null,text+"\n\nSearch Modrinth for compatible versions?", "Incompatible mods found",0,JOptionPane.WARNING_MESSAGE,null,new String[]{"Auto-fix","Cancel launch"},"Auto-fix"));
        if(answer[0]!=0)throw new IOException("Launch cancelled. Open the profile to manage its mods.");
        repair(engine,profile,progress);
    }
    // Work on private copies. Publish only a set that passes the complete profile check.
    static Engine.Profile snapshot(Engine.Profile original,Path dir)throws IOException{
        var copy=new Engine.Profile(dir,original.name(),original.kind(),original.version(),original.loader());
        Files.createDirectories(copy.game().resolve("mods"));
        for(String name:List.of("sonder.properties","content.properties","fabric-import.json","fabric-official-import.json")){
            Path src=original.dir().resolve(name);if(Files.isRegularFile(src))Files.copy(src,dir.resolve(name));
        }
        for(String rel:List.of(".minecraft/mods","disabled-mods")){
            Path src=original.dir().resolve(rel),dst=dir.resolve(rel);Files.createDirectories(dst);
            if(Files.isDirectory(src))try(var paths=Files.list(src)){
                for(Path f:paths.filter(Files::isRegularFile).filter(x->x.toString().toLowerCase(Locale.ROOT).endsWith(".jar")).toList()){
                    Path target=dst.resolve(f.getFileName());
                    try{Files.createLink(target,f);}catch(IOException|UnsupportedOperationException ex){Files.copy(f,target);}
                }
            }
        }
        return copy;
    }
    static void repair(Engine engine,Engine.Profile p,Consumer<String> progress)throws Exception{
        Path stage=Files.createTempDirectory(p.dir(),"repair-");
        try{
            var current=snapshot(p,stage.resolve("start"));Result before=inspect(current);
            var tried=new HashSet<String>();int attempts=0;
            while(!before.issues.isEmpty()){
                var ids=new LinkedHashSet<String>();for(var issue:before.issues){ids.add(issue.target);ids.add(issue.owner);}
                boolean improved=false;
                search: for(String id:ids){
                    if(Set.of("minecraft","java","fabricloader").contains(id))continue;
                    Path file=before.files.get(id);
                    String project=id;
                    if(file!=null){
                        String known=Engine.read(Content.stateFile(current)).getProperty("source/"+file.getFileName(),"");
                        if(known.startsWith("mr/"))project=known.substring(3);
                        else try{project=Net.str(Net.get(Catalog.MR+"version_file/"+Engine.hash(file)+"?algorithm=sha512").getAsJsonObject(),"project_id");}catch(IOException ex){continue;}
                    }
                    if(!project.matches("[A-Za-z0-9_-]+"))continue;
                    if(file==null)try{
                        project=Net.str(Net.get(Catalog.MR+"project/"+project).getAsJsonObject(),"id");
                    }catch(IOException ex){continue;}
                    var cat=new Catalog(engine,current,false);List<Catalog.Release> versions;
                    try{versions=cat.versions(project);}catch(IOException ex){continue;}
                    for(var v:versions.stream().limit(16).toList()){
                        if(!tried.add(project+"/"+v.id()))continue;
                        if(++attempts>64)break search;
                        progress.accept("Checking "+id+" "+v.label());
                        Path trial=Files.createTempDirectory(stage,"candidate-");boolean keep=false;
                        try{
                            var candidate=snapshot(current,trial);
                            new Catalog(engine,candidate,false).install(new Catalog.Hit(project,id,"",""),v.id(),progress);
                            var after=inspect(candidate);
                            if(after.issues.size()<before.issues.size()){
                                current=candidate;before=after;improved=true;keep=true;break search;
                            }
                        }catch(IOException ignored){/* Try another published version. */}
                        finally{if(!keep)Modpack.removeTree(trial);}
                    }
                }
                if(!improved)throw new IOException("No compatible set was found in the checked Modrinth versions. Your mods were kept.\n"+before.issues.getFirst().text);
            }
            apply(p,current,stage.resolve("backup"));
        }finally{Modpack.removeTree(stage);}
    }
    static void apply(Engine.Profile real,Engine.Profile fixed,Path backup)throws IOException{
        var originals=new LinkedHashMap<Path,Path>();var added=new ArrayList<Path>();Files.createDirectories(backup);
        var names=new LinkedHashSet<Path>();names.add(Path.of("content.properties"));
        for(String sub:List.of(".minecraft/mods","disabled-mods"))for(Path root:List.of(real.dir(),fixed.dir())){
            Path dir=root.resolve(sub);if(Files.isDirectory(dir))try(var files=Files.list(dir)){
                for(Path f:files.filter(Files::isRegularFile).filter(x->x.toString().toLowerCase(Locale.ROOT).endsWith(".jar")).toList())names.add(root.relativize(f));
            }
        }
        try{
            for(Path name:names){
                Path old=real.dir().resolve(name),fresh=fixed.dir().resolve(name);
                if(Files.exists(old)&&Files.exists(fresh)&&Files.mismatch(old,fresh)==-1)continue;
                if(Files.exists(old)){Path saved=backup.resolve(name);Files.createDirectories(saved.getParent());Files.move(old,saved);originals.put(old,saved);}
                if(Files.exists(fresh)){Files.createDirectories(old.getParent());Files.move(fresh,old);added.add(old);}
            }
        }catch(IOException ex){for(Path path:added)Files.deleteIfExists(path);for(var entry:originals.entrySet())Files.move(entry.getValue(),entry.getKey());throw ex;}
    }
}
