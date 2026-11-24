import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.URI;
import java.net.http.HttpResponse;
import java.util.*;
import java.util.concurrent.*;

final class ProjectIcons {
    private static final Map<String,Icon> cache=Collections.synchronizedMap(new LinkedHashMap<>(128,.75f,true){
        protected boolean removeEldestEntry(Map.Entry<String,Icon> e){return size()>160;}
    });
    private static final ExecutorService workers=Executors.newFixedThreadPool(3,r->{var t=new Thread(r,"Project icon");t.setDaemon(true);return t;});
    static Icon get(String url){return cache.get(url);}
    static void load(String url,JList<?> list){
        if(url.isBlank()||cache.containsKey(url))return;
        workers.submit(()->{
            if(cache.containsKey(url)||!list.isShowing())return;
            try{
                var uri=URI.create(url);
                if(!"https".equals(uri.getScheme())||!"cdn.modrinth.com".equals(uri.getHost()))return;
                var response=Net.http.send(Net.req(url).timeout(java.time.Duration.ofSeconds(5)).GET().build(),HttpResponse.BodyHandlers.ofInputStream());
                try(var stream=response.body()){
                    if(response.statusCode()!=200)return;
                    byte[] bytes=stream.readNBytes(524289);if(bytes.length>524288)return;
                    try(var input=ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))){
                        var readers=ImageIO.getImageReaders(input);if(!readers.hasNext())return;
                        var reader=readers.next();try{reader.setInput(input);if(reader.getWidth(0)>2048||reader.getHeight(0)>2048)return;
                            var image=reader.read(0);var icon=new ImageIcon(image.getScaledInstance(34,34,Image.SCALE_SMOOTH));
                            cache.put(url,icon);SwingUtilities.invokeLater(list::repaint);
                        }finally{reader.dispose();}
                    }
                }
            }catch(Exception ignored){/* Search results remain usable when artwork is unavailable. */}
        });
    }
}
