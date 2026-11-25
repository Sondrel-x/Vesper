import com.google.gson.JsonElement;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

final class ModDownload {
    record Request(String filename, String hash, long size, List<String> urls) {
        Request {
            if (!hash.matches("(?i)[0-9a-f]{128}")) throw new IllegalArgumentException("Missing SHA-512 for " + filename);
            hash = hash.toLowerCase(Locale.ROOT);
            urls = List.copyOf(urls);
        }
        boolean matches(Path file) throws Exception {
            return Files.isRegularFile(file) && (size < 0 || Files.size(file) == size) && Engine.hash(file).equals(hash);
        }
    }
    static final class Failure extends IOException {
        final Request request;
        Failure(Request request, IOException cause) {
            super("Could not download " + request.filename() + ".\n" + cause.getMessage(), cause);
            this.request = request;
        }
    }
    static Path get(Engine e, Request r) throws Exception {
        Path cached=e.cache.resolve(r.hash()+".jar");
        if (r.matches(cached)) return cached;
        IOException last=new IOException("No available download URL.");
        for (String url : r.urls()) {
            try {
                Net.download(url,cached,"SHA-512",r.hash(),r.size());
                return cached;
            } catch (IOException ex) { last=ex; }
        }
        throw new Failure(r,last);
    }
    static boolean ask(Failure error) throws Exception {
        int[] result={1};
        SwingUtilities.invokeAndWait(()->result[0]=JOptionPane.showOptionDialog(null,
            error.getMessage()+"\nSearch for an exact copy locally or on Modrinth?",
            "Mod download",JOptionPane.DEFAULT_OPTION,JOptionPane.WARNING_MESSAGE,null,
            new String[]{"Auto-fix","Cancel"},"Auto-fix"));
        return result[0]==0;
    }
    static Path repair(Engine engine,Request r) throws Exception {
        Path cached=engine.cache.resolve(r.hash()+".jar");
        if (r.matches(cached)) return cached;
        Set<Path> folders=new LinkedHashSet<>();
        for (var profile : engine.profiles()) {
            folders.add(profile.game().resolve("mods"));
            folders.add(profile.dir().resolve("disabled-mods"));
        }
        String roaming=System.getenv("APPDATA");
        if (roaming!=null) folders.add(Path.of(roaming,".minecraft","mods"));
        for (Path folder : folders) {
            if (!Files.isDirectory(folder)) continue;
            try (var entries=Files.newDirectoryStream(folder)) {
                for (Path path : entries) {
                    if (!path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar") || !r.matches(path)) continue;
                    Path tmp=Files.createTempFile(engine.cache,"repair",".part");
                    try {
                        Files.copy(path,tmp,StandardCopyOption.REPLACE_EXISTING);
                        if (r.matches(tmp)) { Engine.commit(tmp,cached); return cached; }
                    } finally { Files.deleteIfExists(tmp); }
                }
            } catch (IOException ignored) { /* Other profiles or the provider may still have the file. */ }
        }
        var urls=new LinkedHashSet<String>();
        try {
            var version=Net.get(Catalog.MR+"version_file/"+r.hash()+"?algorithm=sha512").getAsJsonObject();
            for (JsonElement entry : version.getAsJsonArray("files")) {
                var file=entry.getAsJsonObject();
                if (r.hash().equalsIgnoreCase(Net.str(file.getAsJsonObject("hashes"),"sha512")))
                    urls.add(Net.str(file,"url"));
            }
        } catch (IOException ignored) { /* Retry the original provider if lookup is unavailable. */ }
        urls.addAll(r.urls());
        try { return get(engine,new Request(r.filename(),r.hash(),r.size(),List.copyOf(urls))); }
        catch (Failure failed) { throw new IOException("Auto-fix could not retrieve a verified copy of "+r.filename()+".\n"+failed.getCause().getMessage(),failed); }
    }
    static void install(Engine e,Request r,Path dest,boolean alreadyApproved) throws Exception {
        Path cached;
        try { cached=get(e,r); }
        catch (Failure failed) {
            if (!alreadyApproved && !ask(failed)) throw new IOException("Download cancelled for "+r.filename());
            cached=repair(e,r);
        }
        Files.createDirectories(dest.getParent());
        Files.copy(cached,dest,StandardCopyOption.REPLACE_EXISTING);
    }
}
