import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipFile;
import javax.swing.*;
import java.awt.*;

final class Packs {
    static Path library(){
        String location=System.getenv("SONDER_PACKS");
        return location==null?Path.of(System.getProperty("user.dir"),"packs"):Path.of(location);
    }
    static String folder(Path zip)throws IOException{
        try(var z=new ZipFile(zip.toFile())){
            if(z.getEntry("pack.mcmeta")!=null)return "resourcepacks";
            if(z.stream().anyMatch(f->f.getName().startsWith("shaders/")&&!f.isDirectory()))return "shaderpacks";
        }
        throw new IOException(zip.getFileName()+" needs pack.mcmeta or a shaders folder at the root of the ZIP.");
    }
    static void add(Engine e,Engine.Profile p,Path source)throws Exception{
        var dir=folder(source);Path dest=p.game().resolve(dir);Files.createDirectories(dest);Path file=dest.resolve(source.getFileName());
        if(Files.exists(file)){
            if(Files.mismatch(file,source)!=-1)throw new IOException(file.getFileName()+" already exists with different contents.");
        }else{
            Path tmp=Files.createTempFile(dest,"sonder-",".part");try{Files.copy(source,tmp,StandardCopyOption.REPLACE_EXISTING);Files.move(tmp,file);}finally{Files.deleteIfExists(tmp);}
        }
        if(dir.equals("shaderpacks")&&!p.kind().equals("Custom")){var groups=e.extras(p);groups.add("iris");e.extras(p,groups);}
    }
    static void open(Component parent,Engine e,Engine.Profile p)throws Exception{
        var inputs=Imports.select(parent,Imports.zips(library()),null,"Pack collection · "+p.name());
        if(inputs.isEmpty())return;
        int count=0;boolean shader=false;var errors=new ArrayList<String>();
        for(Path src:inputs)try{add(e,p,src);count++;if(folder(src).equals("shaderpacks"))shader=true;}catch(Exception ex){errors.add(ex.getMessage());}
        String msg=count+" pack(s) available in "+p.name()+".";
        if(shader)msg+=p.kind().equals("Custom")?"\nFor shaders, add Iris and its dependencies to Custom.":"\nIris is enabled. Choose BSL under Video Settings → Shader Packs.";
        if(!errors.isEmpty())msg+="\n"+String.join("\n",errors);
        JOptionPane.showMessageDialog(parent,msg,"Texture & shader packs",errors.isEmpty()?JOptionPane.INFORMATION_MESSAGE:JOptionPane.WARNING_MESSAGE);
    }
}
