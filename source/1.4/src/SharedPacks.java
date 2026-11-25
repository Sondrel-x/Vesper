import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

final class SharedPacks {
    static final String MANIFEST="selection.properties";

    static String filename(String name)throws IOException{
        if(name.isBlank()||name.equals(".")||name.equals("..")||name.contains("/")||name.contains("\\")||name.contains(":"))
            throw new IOException("Invalid resource pack name.");
        return name;
    }
    static void zipFolder(Path dir,Path tmp)throws IOException{
        try(var zip=new ZipOutputStream(Files.newOutputStream(tmp));var files=Files.walk(dir)){
            for(var file:files.sorted().toList()){
                if(Files.isSymbolicLink(file))throw new IOException("Cannot share linked files in "+dir.getFileName());
                if(!Files.isRegularFile(file))continue;
                var entry=new ZipEntry(dir.relativize(file).toString().replace(File.separatorChar,'/'));
                entry.setTime(0);zip.putNextEntry(entry);Files.copy(file,zip);zip.closeEntry();
            }
        }
    }
    static void publish(Engine e,Engine.Profile source)throws Exception{
        Path store=Files.createDirectories(e.base.resolve("shared-resourcepacks"));
        var selection=new Properties();int index=0;
        for(String id:Content.enabledPacks(source)){
            if(!id.startsWith("file/"))continue;
            String name=filename(id.substring(5));
            Path file=source.game().resolve("resourcepacks").resolve(name);
            if(Files.isSymbolicLink(file))throw new IOException("Cannot share a linked pack: "+name);
            Path tmp=Files.createTempFile(store,"pack-",".part");
            try{
                if(Files.isDirectory(file)){zipFolder(file,tmp);name+=".zip";}
                else Files.copy(file,tmp,StandardCopyOption.REPLACE_EXISTING);
                String hash=Engine.hash(tmp);Path blob=store.resolve(hash);
                if(!Files.isRegularFile(blob))Files.move(tmp,blob);
                selection.setProperty("name."+index,name);
                selection.setProperty("hash."+index,hash);index++;
            }finally{Files.deleteIfExists(tmp);}
        }
        selection.setProperty("size",Integer.toString(index));
        selection.setProperty("revision",UUID.randomUUID().toString());
        Engine.save(store.resolve(MANIFEST),selection);
        Engine.save(source.dir().resolve("shared-packs.properties"),selection);
    }
    static void apply(Engine e,Engine.Profile profile)throws Exception{
        Path store=e.base.resolve("shared-resourcepacks");
        var selection=Engine.read(store.resolve(MANIFEST));String rev=selection.getProperty("revision");
        Path marker=profile.dir().resolve("shared-packs.properties");
        var previous=Engine.read(marker);
        if(rev==null||rev.equals(previous.getProperty("revision")))return;
        var next=new ArrayList<>(Content.enabledPacks(profile));
        for(String k:previous.stringPropertyNames())if(k.startsWith("applied."))next.remove(previous.getProperty(k));
        Path dest=Files.createDirectories(profile.game().resolve("resourcepacks"));
        var record=new Properties();record.setProperty("revision",rev);
        int count=Integer.parseInt(selection.getProperty("size"));
        for(int i=0;i<count;i++){
            String name=filename(selection.getProperty("name."+i)),hash=selection.getProperty("hash."+i);
            if(!hash.matches("[a-f0-9]{128}"))throw new IOException("Invalid shared pack checksum.");
            Path file=dest.resolve(name),blob=store.resolve(hash);
            if(Files.exists(file)&&(!Files.isRegularFile(file)||!Engine.hash(file).equals(hash)))
                file=dest.resolve("Vesper-"+hash.substring(0,16)+"-"+name);
            if(!Files.exists(file)){
                Path tmp=Files.createTempFile(dest,"pack-",".part");
                try{Files.copy(blob,tmp,StandardCopyOption.REPLACE_EXISTING);
                    if(!Engine.hash(tmp).equals(hash))throw new IOException("Damaged shared pack: "+name);
                    Files.move(tmp,file);
                }finally{Files.deleteIfExists(tmp);}
            }else if(!Engine.hash(file).equals(hash))throw new IOException("Conflicting pack: "+file.getFileName());
            String id="file/"+file.getFileName();next.remove(id);next.add(id);record.setProperty("applied."+i,id);
        }
        Content.writePacks(profile,next);Engine.save(marker,record);
    }
}
