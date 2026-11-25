import com.google.gson.*;
import javax.swing.*;
import java.io.*;
import java.nio.file.*;
import java.time.Duration;

final class ReleaseUpdates {
    static final String VERSION="1.4", REPO="Sondrel-x/Vesper";
    record Release(String version,String name,String url,String hash){}
    static long numeric(String version){
        String[] parts=version.split("[.]");
        return Long.parseLong(parts[0])*10000000000L+Long.parseLong(parts[1])*100000L+(parts.length==3?Long.parseLong(parts[2]):0);
    }
    static Release newer(JsonObject json){
        if(json.get("draft").getAsBoolean() || json.get("prerelease").getAsBoolean())return null;
        String tag=Net.str(json,"tag_name");
        if(!tag.matches("v?[0-9]{1,5}(\\.[0-9]{1,5}){1,2}"))return null;
        String ver=tag.replaceFirst("^v","");
        if(numeric(ver)<=numeric(VERSION))return null;
        String installer="Vesper-Launcher-Setup-"+ver+"-Windows-x64.exe";
        for(var el:json.getAsJsonArray("assets")){
            var asset=el.getAsJsonObject();String hash=Net.str(asset,"digest"),url=Net.str(asset,"browser_download_url");
            if(installer.equals(Net.str(asset,"name")) && hash.matches("sha256:[a-fA-F0-9]{64}")
              && url.equals("https://github.com/"+REPO+"/releases/download/"+tag+"/"+installer))
                return new Release(ver,installer,url,hash.substring(7));
        }
        return null;
    }
    static void check(Sonder app){
        Thread.startVirtualThread(()->{
            try{
                String api="https://api.github.com/repos/"+REPO;
                var repo=Net.send(Net.req(api).timeout(Duration.ofSeconds(6)).build()).getAsJsonObject();
                if(!"334284351".equals(Net.str(repo.getAsJsonObject("owner"),"id")))return;
                var json=Net.send(Net.req(api+"/releases/latest").timeout(Duration.ofSeconds(6)).build());
                var release=newer(json.getAsJsonObject());
                if(release!=null)SwingUtilities.invokeLater(()->prompt(app,release));
            }catch(Exception offline){ /* The release feed is optional at startup. */ }
        });
    }
    static void prompt(Sonder app,Release release){
        if(!app.isDisplayable())return;
        if(app.busy){var retry=new Timer(1200,event->prompt(app,release));retry.setRepeats(false);retry.start();return;}
        var choices=new String[]{"Update","Continue"};
        int action=JOptionPane.showOptionDialog(app,"Vesper "+release.version()+" is available. You have "+VERSION+".","Launcher update",0,JOptionPane.INFORMATION_MESSAGE,null,choices,choices[0]);
        if(action!=0)return;
        app.work("Downloading Vesper "+release.version()+"…",progress->{
            Path target=app.e.base.resolve("updates").resolve(release.name());
            Net.download(release.url(),target,"SHA-256",release.hash());
            new ProcessBuilder(target.toString()).start();
            SwingUtilities.invokeLater(()->app.dispatchEvent(new java.awt.event.WindowEvent(app,java.awt.event.WindowEvent.WINDOW_CLOSING)));
            return "Opening installer…";
        });
    }
}
