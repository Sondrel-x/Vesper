import javax.swing.*;
import java.io.*;
import java.net.URI;
import java.net.http.HttpResponse;
import java.util.*;
import java.util.concurrent.*;

final class ProjectIcons {
    private static final Map<String, Icon> cache = Collections.synchronizedMap(new LinkedHashMap<>(160,.75f,true) {
        protected boolean removeEldestEntry(Map.Entry<String, Icon> entry) { return size() > 320; }
    });
    private static final ExecutorService workers = Executors.newFixedThreadPool(4, r -> {
        Thread t = new Thread(r, "Modrinth artwork"); t.setDaemon(true); return t;
    });
    static Icon get(Catalog.Hit hit) { return cache.get(hit.id()); }
    static void load(Catalog.Hit hit, JList<?> list) {
        if (get(hit) != null) return;
        workers.submit(() -> {
            try {
                Icon icon = fetch(hit.id(),hit.icon());
                if (icon != null) {
                    cache.put(hit.id(),icon);
                    SwingUtilities.invokeLater(list::repaint);
                }
            } catch (Exception ignored) { /* The row retains its initial if the service is offline. */ }
        });
    }
    static Icon fetch(String id, String url) throws Exception {
        if (!id.matches("[a-zA-Z0-9_-]+")) return null;
        try {
            if (url.isBlank()) url = Net.str(Net.get(Catalog.MR+"project/"+id).getAsJsonObject(),"icon_url");
            URI uri = URI.create(url);
            if ("https".equals(uri.getScheme()) && Set.of("cdn.modrinth.com","cdn-raw.modrinth.com").contains(uri.getHost())) {
                var res = Net.http.send(Net.req(uri.toASCIIString()).timeout(java.time.Duration.ofSeconds(10)).build(),HttpResponse.BodyHandlers.ofInputStream());
                try (var in = res.body()) {
                    if (res.statusCode()==200) {
                        Icon icon=ContentArt.decode(in);
                        if(icon!=null) return icon;
                    }
                }
            }
        } catch (Exception ignored) { /* Try the bundled artwork for preset projects. */ }
        try(var in=ProjectIcons.class.getResourceAsStream("/project-icons/"+id+".png")) {
            return in==null?null:ContentArt.decode(in);
        }
    }
}
