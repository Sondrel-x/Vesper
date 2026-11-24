import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.function.Consumer;
import java.util.jar.JarFile;
import java.util.concurrent.*;
import java.nio.file.attribute.BasicFileAttributes;

final class Engine {
    static final String MC="1.21.11", LOADER="0.20.1";
    static final String[] VERSIONS={"26.3","26.2","26.1.2","26.1.1","26.1",MC,"1.21.10","1.21.8","1.21.4","1.21.1"};
    final Path base, root, cache;
    final List<Mod> catalog;
    record Mod(String group,String slug,String id,String file,String hash,String url,List<String> deps) {}
    record Profile(Path dir,String name,String kind,String version,String loader) {
        boolean hud(){return !kind.equals("Competitive PvP")&&version.equals(MC)&&loader.equals("Fabric");}
        String resource(String prefix){return "/"+prefix+(version.equals(MC)?"":"-"+version)+".json";}
        Path game(){return dir.resolve(".minecraft");}
        public String toString(){return name;}
    }
    Engine(Path base) throws Exception {
        this.base=base; root=base; cache=base.resolve("downloads");
        Files.createDirectories(root.resolve("instances")); Files.createDirectories(cache);
        try(var r=new BufferedReader(new InputStreamReader(Objects.requireNonNull(getClass().getResourceAsStream("/catalog.tsv")),StandardCharsets.UTF_8))){
            catalog=r.lines().map(line->{String[] a=line.split("\t",-1);
                return new Mod(a[0],a[1],a[2],a[3],a[4],a[5],a[6].isBlank()?List.of():List.of(a[6].split(",")));}).toList();
        }
    }
    static Properties read(Path p)throws IOException{
        Properties props=new Properties(); if(Files.exists(p))try(var in=Files.newInputStream(p)){props.load(in);}return props;
    }
    static void commit(Path temp,Path target)throws IOException{
        try{Files.move(temp,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
        catch(AtomicMoveNotSupportedException e){Files.move(temp,target,StandardCopyOption.REPLACE_EXISTING);}
    }
    static void save(Path p,Properties props)throws IOException{
        Path temp=Files.createTempFile(p.getParent(),"settings",".part");
        try{try(var out=Files.newOutputStream(temp)){props.store(out,null);}commit(temp,p);}finally{Files.deleteIfExists(temp);}
    }
    List<Profile> profiles()throws Exception{
        List<Profile> list=new ArrayList<>();
        for(Path parent:List.of(root.resolve("instances"),base.resolve("Prism/instances"))){
            if(!Files.isDirectory(parent))continue;
            try(var dirs=Files.list(parent)){for(var dir:dirs.filter(Files::isDirectory).toList()){
                var file=dir.resolve("sonder.properties");var props=read(file);
                if(!props.containsKey("kind"))continue;
                list.add(new Profile(dir,props.getProperty("name"),props.getProperty("kind"),props.getProperty("version",MC),props.getProperty("loader","Fabric")));
            }}
        }
        Set<String> used=new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for(var p:list)used.add(p.name());
        list.sort(Comparator.comparing(p->p.dir.toString()));
        for(int i=0;i<list.size();i++){
            var p=list.get(i);
            String next=legacyPreset(p.kind(),p.name());
            if(next==null)continue;
            String title=next;
            for(int n=2;!used.add(title);n++)title=next+" ("+n+")";
            Path file=p.dir.resolve("sonder.properties");
            var props=read(file);props.setProperty("name",title);save(file,props);
            list.set(i,new Profile(p.dir,title,p.kind,p.version,p.loader));
        }
        list.sort(Comparator.comparing(Profile::name,String.CASE_INSENSITIVE_ORDER));return list;
    }
    static String legacyPreset(String kind,String name){
        String normalized=name.strip().toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
        return switch(kind){
            case "Custom" -> normalized.equals("mymods")?"Workshop":null;
            case "FPS Boost" -> Set.of("sonderfps","vesperfps").contains(normalized)?"Velocity":null;
            case "FPS + QoL" -> Set.of("sondersmp","sonderfps+qol","vesperfps+qol").contains(normalized)?"Horizon":null;
            default -> null;
        };
    }
    static Properties personalSettings(Profile p)throws IOException{
        Path file=p.dir.resolve("personal.properties");
        var settings=read(file);
        if(legacyPreset(p.kind,settings.getProperty("nickname",""))!=null){
            settings.remove("nickname");
            save(file,settings);
        }
        return settings;
    }
    Profile create(String name,String kind)throws Exception{
        return create(name,kind,MC,"Fabric");
    }
    Profile create(String name,String kind,String version,String loader)throws Exception{
        if(!List.of(VERSIONS).contains(version)||!List.of("Vanilla","Fabric").contains(loader))throw new IOException("Unsupported game setup.");
        if(!kind.equals("Custom")&&(!version.equals(MC)||!loader.equals("Fabric")))throw new IOException("The preset requires Fabric "+MC);
        name=name.strip();
        if(name.isEmpty()||name.length()>48||name.chars().anyMatch(c->c<32||c=='\\'||c=='='))throw new IOException("Use a profile name of 1 to 48 characters without control characters, backslashes or equals signs.");
        if(!List.of("FPS Boost","FPS + QoL","Competitive PvP","Custom").contains(kind))throw new IOException("Unknown preset.");
        for(var p:profiles())if(p.name.equalsIgnoreCase(name))throw new IOException("That profile name already exists.");
        Path dir=root.resolve("instances/sonder-"+UUID.randomUUID());Profile p=new Profile(dir,name,kind,version,loader);
        Files.createDirectories(p.game().resolve("mods"));
        var props=new Properties();props.setProperty("name",name);props.setProperty("kind",kind);props.setProperty("version",version);props.setProperty("loader",loader);save(dir.resolve("sonder.properties"),props);return p;
    }
    Set<String> extras(Profile p)throws IOException{
        var props=read(p.dir.resolve("sonder.properties"));
        String value=props.getProperty("extras","");
        Set<String> groups=value.isBlank()?new HashSet<>():new HashSet<>(List.of(value.split(",")));
        if(!p.kind.equals("Custom")&&!p.kind.equals("Competitive PvP")&&!props.containsKey("visualDefaults"))groups.add("reload");
        return groups;
    }
    void extras(Profile p,Set<String> groups)throws IOException{
        Path f=p.dir.resolve("sonder.properties");var props=read(f);props.setProperty("visualDefaults","2");props.setProperty("extras",String.join(",",groups));save(f,props);
    }
    List<Mod> chosen(Profile p,Set<String> groups)throws IOException{
        if(p.kind.equals("Custom"))return List.of();
        var gs=new HashSet<>(groups);gs.add("core");if(p.kind.equals("FPS + QoL"))gs.add("qol");
        Set<String> ids=new HashSet<>();for(var m:catalog)if(gs.contains(m.group)||(p.kind.equals("Competitive PvP")&&m.slug.equals("modernfix")))ids.add(m.id);
        boolean added;do{added=false;for(var m:catalog)if(ids.contains(m.id))added|=ids.addAll(m.deps);}while(added);
        var state=read(Content.stateFile(p));var result=new ArrayList<Mod>();
        for(var m:catalog)if(ids.contains(m.id)){
            String data=state.getProperty("override/"+m.id);result.add(data==null?m:Net.JSON.fromJson(data,Mod.class));
        }
        return result;
    }
    static String hash(Path path)throws Exception{
        var d=MessageDigest.getInstance("SHA-512");try(var in=new DigestInputStream(Files.newInputStream(path),d)){in.transferTo(OutputStream.nullOutputStream());}
        return HexFormat.of().formatHex(d.digest());
    }
    Path download(Mod mod)throws Exception{
        return ModDownload.get(this,new ModDownload.Request(mod.file,mod.hash,-1,List.of(mod.url)));
    }
    void installHud(Profile p)throws Exception{
        Path mods=p.game().resolve("mods");Files.createDirectories(mods);
        Path dst=mods.resolve("Sonder-HUD.jar");
        try(var in=Objects.requireNonNull(getClass().getResourceAsStream("/sonder-hud.jar"))){
            byte[] bytes=in.readAllBytes();String wanted=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-512").digest(bytes));
            String installed=Files.exists(dst)?hash(dst):null;
            if(wanted.equals(installed))return;
            if(installed!=null&&!"128418eda6d80ea66b62ed801aaac37fe895fccfc7b1aa57c2d1d998037dd65b9b7704d2ae62cac77ab58810695c370c25d619688c110d699f6966c560b0ada2".equals(installed)&&!"f4cd3919240f47357535ed3a059c8ad7b28da797870692b2c36e80506a28f6e48c7a5351dab7e93c1f15d7dbf16dd2ef11c3b4c21fc9d0e07b8709b7e81c0be9".equals(installed))throw new IOException("Move the conflicting Sonder-HUD.jar out of this profile's mods folder to install the bundled HUD.");
            Path tmp=Files.createTempFile(mods,"hud",".part");
            try{Files.write(tmp,bytes);commit(tmp,dst);}finally{Files.deleteIfExists(tmp);}
        }
    }
    static String stamp(Mod m,Path path)throws IOException{
        var attrs=Files.readAttributes(path,BasicFileAttributes.class);
        return m.hash+"|"+attrs.size()+"|"+attrs.lastModifiedTime()+"|"+attrs.creationTime();
    }
    void prepare(Profile p,Consumer<String> status)throws Exception{
        var controls=read(Content.stateFile(p));if(p.hud()&&Content.active(controls,"Sonder-HUD.jar"))installHud(p);
        if(p.kind.equals("Custom"))return;
        var selected=chosen(p,extras(p)).stream().filter(m->Content.active(controls,m.file)).toList();
        var activeIds=new HashSet<String>();for(var m:selected)activeIds.add(m.id);
        for(var m:selected)for(String id:m.deps)if(!activeIds.contains(id)){
            String name=catalog.stream().filter(v->v.id.equals(id)).map(v->v.file).findFirst().orElse(id);
            throw new IOException(m.file+" needs "+name+". Enable the dependency or disable the dependent mod.");
        }
        Path mods=p.game().resolve("mods");Files.createDirectories(mods);
        var wanted=new HashSet<String>();Path manifest=p.dir.resolve("verified-mods.properties");
        var previous=read(manifest);var verified=new Properties();
        var tasks=new LinkedHashMap<Path,Future<Path>>();
        try(var pool=Executors.newFixedThreadPool(4)){
            for(Mod m:selected){
                wanted.add(m.file);Path dst=mods.resolve(m.file);
                if(Files.isRegularFile(dst)){
                    String stamp=stamp(m,dst);
                    if(stamp.equals(previous.getProperty(m.file))||hash(dst).equals(m.hash)){verified.setProperty(m.file,stamp);continue;}
                }
                tasks.put(dst,pool.submit(()->{status.accept("Preparing "+m.file);return download(m);}));
            }
            var ready=new LinkedHashMap<Path,Path>();
            for(var entry:tasks.entrySet())ready.put(entry.getKey(),entry.getValue().get());
            for(var entry:ready.entrySet()){Path tmp=Files.createTempFile(mods,"install",".part");
                try{Files.copy(entry.getValue(),tmp,StandardCopyOption.REPLACE_EXISTING);commit(tmp,entry.getKey());}finally{Files.deleteIfExists(tmp);}}
        }
        for(Mod m:selected)if(!verified.containsKey(m.file))verified.setProperty(m.file,stamp(m,mods.resolve(m.file)));
        for(Mod m:catalog)if(!wanted.contains(m.file)&&previous.containsKey(m.file)){Path dst=mods.resolve(m.file);if(Files.exists(dst)&&hash(dst).equals(m.hash))Files.delete(dst);}
        if(!verified.equals(previous))save(manifest,verified);
    }
    List<Path> importSources(Path source)throws IOException{
        if(!Files.isDirectory(source))return List.of(source);
        Path dir=source;
        for(String candidate:List.of("mods",".minecraft/mods","minecraft/mods")){
            if(Files.isDirectory(source.resolve(candidate))){dir=source.resolve(candidate);break;}
        }
        try(var paths=Files.list(dir)){
            var jars=paths.filter(Files::isRegularFile).filter(f->f.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar")).sorted().toList();
            if(jars.isEmpty())throw new IOException("No mod JARs found in "+dir);return jars;
        }
    }
    void importMod(Profile p,Path src)throws Exception{

        if(!src.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar"))throw new IOException("Choose a .jar file.");
        try(var jar=new JarFile(src.toFile())){if(jar.getEntry("fabric.mod.json")==null)throw new IOException(src.getFileName()+" is missing Fabric mod metadata.");}
        Path mods=p.game().resolve("mods"), dst=mods.resolve(src.getFileName());String name=dst.getFileName().toString();
        if(Files.exists(Content.parked(p,name))){if(Files.mismatch(src,Content.parked(p,name))!= -1)throw new IOException(name+" already exists as a disabled mod.");Content.change(p,new Content.Item("mods",name,true,false,false),true,false);return;}
        if(Files.exists(dst)){if(Files.mismatch(src,dst)==-1){Content.imported(p,"mods",name);return;}throw new IOException(name+" already exists.");}
        Path tmp=Files.createTempFile(mods,"import",".part");try{Files.copy(src,tmp,StandardCopyOption.REPLACE_EXISTING);Files.move(tmp,dst);Content.imported(p,"mods",name);}finally{Files.deleteIfExists(tmp);}
    }
}
