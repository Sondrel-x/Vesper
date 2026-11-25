import com.google.gson.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;

final class Content {
    record Item(String group,String name,boolean available,boolean on,boolean preset){
        Path file(Engine.Profile p){return p.game().resolve(group).resolve(name);}
        String key(){return group+"/"+name;}
    }
    static Path stateFile(Engine.Profile p){return p.dir().resolve("content.properties");}
    static Path parked(Engine.Profile p,String name){return p.dir().resolve("disabled-mods").resolve(name);}
    static boolean active(Properties props,String name){return !props.containsKey("mods/"+name);}
    static void imported(Engine.Profile p,String group,String name)throws IOException{
        var state=Engine.read(stateFile(p));if(state.remove(group+"/"+name)!=null)Engine.save(stateFile(p),state);
    }
    static List<String> enabledPacks(Engine.Profile p)throws IOException{
        var file=p.game().resolve("options.txt");if(Files.exists(file))for(String line:Files.readAllLines(file))if(line.startsWith("resourcePacks:")){
            try{return new ArrayList<>(JsonParser.parseString(line.substring(14)).getAsJsonArray().asList().stream().map(JsonElement::getAsString).toList());}
            catch(RuntimeException ex){throw new IOException("Minecraft's resource pack list could not be read.",ex);}}
        return new ArrayList<>(List.of("vanilla"));
    }
    static void setPack(Engine.Profile p,String name,boolean on)throws IOException{
        var choices=enabledPacks(p);String id="file/"+name;
        if(on==choices.contains(id))return;if(on)choices.add(id);else choices.removeIf(id::equals);
        writePacks(p,choices);
    }
    static void writePacks(Engine.Profile p,List<String> choices)throws IOException{
        var file=p.game().resolve("options.txt");var lines=Files.exists(file)?Files.readAllLines(file):new ArrayList<String>();
        String changed="resourcePacks:"+Net.JSON.toJson(choices);boolean found=false;
        for(int i=0;i<lines.size();i++)if(lines.get(i).startsWith("resourcePacks:")){lines.set(i,changed);found=true;break;}
        if(!found)lines.add(changed);Path tmp=Files.createTempFile(p.game(),"options",".part");
        try{Files.write(tmp,lines);Engine.commit(tmp,file);}finally{Files.deleteIfExists(tmp);}
    }
    static Path iris(Engine.Profile p){return p.game().resolve("config/iris.properties");}
    static void setShader(Engine.Profile p,String name,boolean on)throws IOException{
        var f=iris(p);var props=Engine.read(f);if(!on&&!name.equals(props.getProperty("shaderPack")))return;
        props.setProperty("enableShaders",Boolean.toString(on));if(on)props.setProperty("shaderPack",name);Files.createDirectories(f.getParent());Engine.save(f,props);
    }
    static List<Item> list(Engine e,Engine.Profile p,boolean packs)throws Exception{
        var state=Engine.read(stateFile(p));Map<String,Item> result=new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        if(!packs){
            var names=new HashSet<String>();if(p.hud())names.add("Sonder-HUD.jar");for(var m:e.chosen(p,e.extras(p)))names.add(m.file());
            var preset=new HashSet<>(names);for(var dir:List.of(p.game().resolve("mods"),p.dir().resolve("disabled-mods")))if(Files.isDirectory(dir))try(var s=Files.list(dir)){
                s.filter(Files::isRegularFile).map(f->f.getFileName().toString()).filter(n->n.toLowerCase(Locale.ROOT).endsWith(".jar")).forEach(names::add);}
            for(var n:names){String mode=state.getProperty("mods/"+n,"");if(mode.equals("removed"))continue;
                result.put(n,new Item("mods",n,Files.exists(p.game().resolve("mods").resolve(n))||Files.exists(parked(p,n)),mode.isEmpty(),preset.contains(n)));}
        }else{
            var enabled=enabledPacks(p);var shader=Engine.read(iris(p));for(String dir:List.of("resourcepacks","shaderpacks")){
                Path folder=p.game().resolve(dir);if(!Files.isDirectory(folder))continue;try(var s=Files.list(folder)){
                    for(var f:s.filter(Files::isRegularFile).toList()){String n=f.getFileName().toString();if(!n.toLowerCase(Locale.ROOT).endsWith(".zip"))continue;
                        boolean on=dir.equals("resourcepacks")?enabled.contains("file/"+n):n.equals(shader.getProperty("shaderPack"))&&Boolean.parseBoolean(shader.getProperty("enableShaders","true"));
                        result.put(dir+"/"+n,new Item(dir,n,true,on,false));}}}
        }return new ArrayList<>(result.values());
    }
    static void change(Engine.Profile p,Item item,boolean on,boolean remove)throws IOException{
        var state=Engine.read(stateFile(p));Path active=item.file(p),hold=parked(p,item.name());
        Path src=null,dst=null;
        boolean mod=item.group().equals("mods");
        if(remove){src=Files.exists(active)?active:mod&&Files.exists(hold)?hold:null;
            dst=p.dir().resolve("removed-content").resolve(UUID.randomUUID().toString()).resolve(item.name());
        }else if(mod){src=on?hold:active;dst=on?active:hold;if(!Files.exists(src))src=null;}
        if(src!=null){Files.createDirectories(dst.getParent());Files.move(src,dst);}
        try{
            if(mod){if(remove)state.setProperty(item.key(),"removed");else if(!on)state.setProperty(item.key(),"disabled");else state.remove(item.key());Engine.save(stateFile(p),state);}
            else if(item.group().equals("resourcepacks"))setPack(p,item.name(),!remove&&on);
            else setShader(p,item.name(),!remove&&on);
        }catch(IOException|RuntimeException ex){if(src!=null)Files.move(dst,src);throw ex;}
    }
}
