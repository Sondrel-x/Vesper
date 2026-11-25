import com.google.gson.*;
import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.zip.*;

final class ContentArt {
    private static final ExecutorService io=Executors.newFixedThreadPool(3,r->{Thread t=new Thread(r,"Content artwork");t.setDaemon(true);return t;});
    private static final Map<String,CompletableFuture<Icon>> icons=new LinkedHashMap<>(160,.75f,true){
        protected boolean removeEldestEntry(Map.Entry<String,CompletableFuture<Icon>> e){return size()>320;}
    };
    static JLabel tile(Engine.Profile profile,Content.Item item,String project,Color accent){
        String name=Sonder.displayMod(item.name());
        var label=Sonder.label(name.isBlank()?"?":name.substring(0,1).toUpperCase(Locale.ROOT),18,true);
        label.setHorizontalAlignment(SwingConstants.CENTER);label.setOpaque(true);label.setBackground(accent.darker().darker().darker());label.setPreferredSize(new Dimension(44,44));
        Path active=item.file(profile);Path file=item.group().equals("mods")&&!Files.exists(active)?Content.parked(profile,item.name()):active;
        String key=file+"|"+file.toFile().lastModified()+"|"+project;
        CompletableFuture<Icon> image;
        synchronized(icons){image=icons.computeIfAbsent(key,k->CompletableFuture.supplyAsync(()->load(file,project),io));}
        image.thenAccept(icon->{if(icon!=null)SwingUtilities.invokeLater(()->{label.setText("");label.setOpaque(false);label.setIcon(icon);});});
        return label;
    }
    static Icon load(Path file,String project){
        if(Files.isRegularFile(file))try(var zip=new ZipFile(file.toFile())){
            String logo="pack.png";var metadata=zip.getEntry("fabric.mod.json");
            if(metadata!=null)try(var in=zip.getInputStream(metadata)){
                var data=JsonParser.parseString(new String(in.readNBytes(262144),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject().get("icon");
                if(data!=null&&data.isJsonPrimitive())logo=data.getAsString();
                else if(data!=null&&data.isJsonObject())logo=data.getAsJsonObject().entrySet().stream().min(Comparator.comparingInt(e->Math.abs(Integer.parseInt(e.getKey())-64))).map(e->e.getValue().getAsString()).orElse(logo);
            }
            var entry=zip.getEntry(logo);
            if(entry==null)entry=zip.stream().filter(e->e.getName().matches("(?i)(?:assets/[^/]+/)?(?:icon|logo|pack)[.](?:png|webp|jpg|jpeg)")).findFirst().orElse(null);
            if(entry==null&&metadata==null)entry=zip.stream().filter(e->e.getName().matches("assets/[^/]+/textures/(item|block)/[^/]+[.]png")).findFirst().orElse(null);
            if(entry!=null)try(var in=zip.getInputStream(entry)){var icon=decode(in);if(icon!=null)return icon;}
        }catch(Exception ignored){}
        try {
            String id = project!=null && project.startsWith("mr/") ? project.substring(3) : "";
            if (id.isEmpty() && Files.isRegularFile(file) && !file.getFileName().toString().equals("Sonder-HUD.jar")) {
                String hash=Engine.hash(file);
                id=Net.str(Net.get(Catalog.MR+"version_file/"+hash+"?algorithm=sha512").getAsJsonObject(),"project_id");
            }
            if(!id.isEmpty()) return ProjectIcons.fetch(id,"");
        } catch(Exception ignored) { /* A private mod may have no matching Modrinth project. */ }
        if(file.getFileName().toString().equals("Sonder-HUD.jar"))try(var in=ContentArt.class.getResourceAsStream("/sonder.png")){return decode(in);}catch(IOException ignored){}
        return null;
    }
    static Icon decode(InputStream stream)throws IOException{
        byte[] b=stream.readNBytes(4*1024*1024+1);if(b.length>4*1024*1024)return null;
        try(var input=ImageIO.createImageInputStream(new ByteArrayInputStream(b))){
            var readers=ImageIO.getImageReaders(input);if(!readers.hasNext())return null;var reader=readers.next();
            try{reader.setInput(input);int w=reader.getWidth(0),h=reader.getHeight(0);if(w>4096||h>8192)return null;
                var params=reader.getDefaultReadParam();if(h>w&&h%w==0){h=w;params.setSourceRegion(new Rectangle(0,0,w,w));}int step=Math.max(1,Math.min(w,h)/80);params.setSourceSubsampling(step,step,0,0);
                var im=reader.read(0,params);var out=new BufferedImage(44,44,BufferedImage.TYPE_INT_ARGB);var g=out.createGraphics();g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                double scale=44.0/Math.max(w,h);int width=Math.max(1,(int)(w*scale)),height=Math.max(1,(int)(h*scale));g.drawImage(im,(44-width)/2,(44-height)/2,width,height,null);g.dispose();return new ImageIcon(out);
            }finally{reader.dispose();}
        }
    }
}
