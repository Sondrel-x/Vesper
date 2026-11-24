import com.google.gson.*;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.time.Duration;
import java.util.*;

final class Net {
    static final Gson JSON=new Gson();
    static final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(18)).followRedirects(HttpClient.Redirect.NEVER).build();
    static String enc(String s){return URLEncoder.encode(s,StandardCharsets.UTF_8);}
    static String query(Map<String,String> data){return String.join("&",data.entrySet().stream().map(e->enc(e.getKey())+"="+enc(e.getValue())).toList());}
    static JsonObject obj(Object... params){JsonObject o=new JsonObject();for(int i=0;i<params.length;i+=2)o.add((String)params[i],JSON.toJsonTree(params[i+1]));return o;}
    static String str(JsonObject o,String k){return o.has(k)&&!o.get(k).isJsonNull()?o.get(k).getAsString():"";}
    static HttpRequest.Builder req(String url){return HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofMinutes(2)).header("User-Agent","SonderLauncher/0.4 (Minecraft Java)");}
    static final class MinecraftAccessDenied extends IOException{
        MinecraftAccessDenied(String details){super(details);}
    }
    static JsonElement send(HttpRequest r)throws Exception{
        var res=http.send(r,HttpResponse.BodyHandlers.ofString());
        if(res.statusCode()==403&&r.uri().getHost().equals("api.minecraftservices.com")){
            String path=r.uri().getPath();String code="";try{String value=str(JsonParser.parseString(res.body()).getAsJsonObject(),"error");if(value.matches("[A-Za-z_]{1,60}"))code=" ("+value+")";}catch(RuntimeException ignored){}
            if(path.equals("/authentication/login_with_xbox"))
                throw new MinecraftAccessDenied("Minecraft Services declined direct launcher access (HTTP 403)"+code);
            throw new IOException("Minecraft Services declined access to the Java profile. Check that this account opens Java Edition in the official launcher.\n"+path+" · HTTP 403"+code);
        }
        if(res.statusCode()/100!=2)throw new IOException(r.uri().getHost()+" returned HTTP "+res.statusCode()+".");
        return JsonParser.parseString(res.body());
    }
    static JsonElement get(String url)throws Exception{return send(req(url).GET().build());}
    static JsonObject post(String url,JsonObject body)throws Exception{return send(req(url).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(body.toString())).build()).getAsJsonObject();}
    static void download(String url,Path dst,String algorithm,String hash)throws Exception{
        Files.createDirectories(dst.getParent());Path tmp=Files.createTempFile(dst.getParent(),"incoming",".part");
        try{
            URI uri=URI.create(url);
            HttpResponse<InputStream> response;
            int redirects=0;
            while(true){
                if(!"https".equalsIgnoreCase(uri.getScheme())||uri.getHost()==null)throw new IOException("Invalid download URL.");
                var r=req(uri.toASCIIString());
                response=http.send(r.build(),HttpResponse.BodyHandlers.ofInputStream());
                if(response.statusCode()/100!=3)break;
                response.body().close();if(++redirects>4)throw new IOException("Too many download redirects.");
                uri=uri.resolve(response.headers().firstValue("location").orElseThrow());
            }
            try(var input=response.body()){
                if(response.statusCode()/100!=2)throw new IOException("Download failed ("+response.statusCode()+") for "+dst.getFileName());
                var digest=MessageDigest.getInstance(algorithm);try(var in=new DigestInputStream(input,digest)){Files.copy(in,tmp,StandardCopyOption.REPLACE_EXISTING);}
                if(!HexFormat.of().formatHex(digest.digest()).equalsIgnoreCase(hash))throw new IOException("Checksum mismatch for "+dst.getFileName());
            }Engine.commit(tmp,dst);
        }finally{Files.deleteIfExists(tmp);}
    }
}
