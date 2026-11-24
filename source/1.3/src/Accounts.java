import com.google.gson.*;
import com.sun.net.httpserver.HttpServer;
import java.awt.Desktop;
import java.io.*;
import java.net.*;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.concurrent.*;

final class Accounts {
    static final String MS="https://login.microsoftonline.com/consumers/oauth2/v2.0/", SCOPE="XboxLive.signin offline_access";
    record Session(String name,String uuid,String token,String app,String xuid,boolean demo){
        static Session trial(){return new Session("DemoPlayer","00000000000000000000000000000000","0","","",true);}
    }
    final Path base;
    final Path launcher;
    Accounts(Path base){this(base,base);}
    Accounts(Path launcher, Path base){this.launcher=launcher;this.base=base;}
    private Session activeSession;
    private long reusableUntil;
    Session forPlay()throws Exception{
        if(activeSession!=null&&System.nanoTime()-reusableUntil<0&&Files.isRegularFile(vault())&&activeSession.app().equals(client()))return activeSession;
        if(Files.isRegularFile(vault()) && Files.isRegularFile(base.resolve("game-session.bin"))){
            try {
                byte[] raw=vaultCrypt("--unprotect", Files.readAllBytes(base.resolve("game-session.bin")));
                var saved=Net.JSON.fromJson(new String(raw,StandardCharsets.UTF_8),Cached.class);
                long left=saved.until()-System.currentTimeMillis();
                var session=saved.session();
                if(left>0 && left<=3600000 && client().equals(session.app()) && session.uuid().equals(profile().getProperty("uuid"))){
                    activeSession=session;
                    reusableUntil=System.nanoTime()+TimeUnit.MILLISECONDS.toNanos(left);
                    return session;
                }
            } catch(Exception unavailable){ /* Refresh with Microsoft if the local cache is unavailable. */ }
        }
        return refresh();
    }
    record Cached(Session session,long until){}
    Path vault(){return base.resolve("microsoft-session.bin");}
    String client()throws Exception{
        var id=System.getenv("SONDER_MICROSOFT_CLIENT_ID");
        if(id!=null&&!id.isBlank())return id;
        String local=Engine.read(launcher.resolve("launcher.properties")).getProperty("clientId","");
        if(!local.isBlank())return local;
        try(var in=getClass().getResourceAsStream("/sonder-public.properties")){
            var settings=new Properties();if(in!=null)settings.load(in);return settings.getProperty("clientId","");
        }
    }
    static String random(){byte[] b=new byte[48];new SecureRandom().nextBytes(b);return Base64.getUrlEncoder().withoutPadding().encodeToString(b);}
    static JsonObject token(Map<String,String> form)throws Exception{
        var request=Net.req(MS+"token").header("Content-Type","application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString(Net.query(form))).build();
        var response=Net.http.send(request,java.net.http.HttpResponse.BodyHandlers.ofString());
        var json=JsonParser.parseString(response.body()).getAsJsonObject();
        if(response.statusCode()/100!=2){
            if("invalid_grant".equals(Net.str(json,"error")))throw new SignInExpired();
            throw new IOException("Microsoft sign-in returned HTTP "+response.statusCode()+".");
        }
        return json;
    }
    static final class SignInExpired extends IOException{SignInExpired(){super("Microsoft requires a new sign-in. Select Connect account.");}}
    static byte[] vaultCrypt(String mode,byte[] data)throws Exception{
        String exe=Objects.requireNonNull(System.getenv("SONDER_EXECUTABLE"),"Windows credential protection requires the installed Vesper Launcher.");
        var proc=new ProcessBuilder(exe,mode).start();try(var out=proc.getOutputStream()){out.write(data);}
        byte[] value=proc.getInputStream().readAllBytes();if(proc.waitFor()!=0)throw new IOException("Windows could not access this account. Connect the account again.");return value;
    }
    void save(String app,String refresh)throws Exception{
        if(refresh.isBlank())throw new IOException("Microsoft did not return a refresh token.");Path tmp=Files.createTempFile(base,"account",".part");
        try{Files.write(tmp,vaultCrypt("--protect",Net.obj("app",app,"refresh",refresh).toString().getBytes(StandardCharsets.UTF_8)));Engine.commit(tmp,vault());}finally{Files.deleteIfExists(tmp);}
    }
    Properties profile()throws IOException{return Engine.read(base.resolve("verified-player.properties"));}
    void clearProfile()throws IOException{Files.deleteIfExists(base.resolve("game-session.bin"));activeSession=null;reusableUntil=0;Files.deleteIfExists(base.resolve("linked-display.properties"));Files.deleteIfExists(base.resolve("verified-player.properties"));}
    String display()throws IOException{
        if(!Files.isRegularFile(vault()))return "Connect account";
        var linked=linked();return profile().getProperty("name",linked.getProperty("name","Java profile pending"));
    }
    Properties linked()throws IOException{return Engine.read(base.resolve("linked-display.properties"));}
    Path portrait()throws IOException{
        String cached=Files.isRegularFile(vault())?profile().getProperty("skin",profile().containsKey("name")?"":linked().getProperty("skin","")):"";
        return cached.matches("skin-[a-fA-F0-9]{32,64}[.]png")?base.resolve(cached):(Files.isRegularFile(vault())?base.resolve("no-account-skin"):base.resolve("portrait.png"));
    }
    void preview(String name)throws Exception{
        if(!name.matches("[a-zA-Z0-9_]{1,16}"))throw new IOException("Enter a Java Edition username.");
        var player=Net.get("https://api.mojang.com/users/profiles/minecraft/"+name).getAsJsonObject();String id=Net.str(player,"id");
        if(!id.matches("[a-fA-F0-9]{32}"))throw new IOException("Minecraft could not find that username.");
        var publicProfile=Net.get("https://sessionserver.mojang.com/session/minecraft/profile/"+id).getAsJsonObject();
        for(var entry:publicProfile.getAsJsonArray("properties")){
            var prop=entry.getAsJsonObject();if(!"textures".equals(Net.str(prop,"name")))continue;
            var payload=JsonParser.parseString(new String(Base64.getDecoder().decode(Net.str(prop,"value")),StandardCharsets.UTF_8)).getAsJsonObject();
            var textures=payload.getAsJsonObject("textures");if(textures==null||!textures.has("SKIN"))break;
            String url=Net.str(textures.getAsJsonObject("SKIN"),"url").replaceFirst("^http://","https://");
            if(!url.matches("https://textures[.]minecraft[.]net/texture/[a-fA-F0-9]{32,64}"))break;
            fetchSkin(url,base.resolve("portrait.png"));
            var info=new Properties();info.setProperty("name",Net.str(player,"name"));Engine.save(base.resolve("preview-player.properties"),info);return;
        }
        throw new IOException("No custom skin was published for this username.");
    }
    static void fetchSkin(String url,Path path)throws Exception{
        var req=Net.req(url).timeout(java.time.Duration.ofSeconds(8)).build();
        var response=Net.http.send(req,java.net.http.HttpResponse.BodyHandlers.ofInputStream());
        try(var input=response.body()){
            if(response.statusCode()!=200)throw new IOException("Skin unavailable.");
            byte[] b=input.readNBytes(1048577);
            if(b.length<32||b.length>1048576)throw new IOException("Invalid skin size.");
            try(var stream=javax.imageio.ImageIO.createImageInputStream(new ByteArrayInputStream(b))){
                var readers=javax.imageio.ImageIO.getImageReaders(stream);if(!readers.hasNext())throw new IOException("Invalid skin.");
                var reader=readers.next();try{reader.setInput(stream);int h=reader.getHeight(0);
                    if(!reader.getFormatName().equalsIgnoreCase("png")||reader.getWidth(0)!=64||(h!=32&&h!=64))throw new IOException("Invalid skin dimensions.");
                    var image=reader.read(0);Path tmp=Files.createTempFile(path.getParent(),"skin",".part");
                    try{javax.imageio.ImageIO.write(image,"png",tmp.toFile());Engine.commit(tmp,path);}finally{Files.deleteIfExists(tmp);}
                }finally{reader.dispose();}
            }
        }
    }
    Session connect(String app)throws Exception{
        if(!app.matches("[0-9a-fA-F]{8}(?:-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}"))throw new IOException("Enter Vesper’s public Application (client) ID in Account setup.");
        String state=random(),verifier=random(),challenge=Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII)));
        var result=new CompletableFuture<String>();var server=HttpServer.create(new InetSocketAddress(InetAddress.getByName("127.0.0.1"),0),0);
        String redirect="http://localhost:"+server.getAddress().getPort()+"/";
        server.createContext("/",exchange->{String msg="This sign-in request could not be verified.";int code=400;
            try{var fields=new HashMap<String,String>();String raw=exchange.getRequestURI().getRawQuery();
                if(raw!=null)for(var pair:raw.split("&")){String[] kv=pair.split("=",2);if(kv.length==2)fields.put(kv[0],URLDecoder.decode(kv[1],StandardCharsets.UTF_8));}
                if(exchange.getRequestURI().getPath().equals("/")&&state.equals(fields.get("state"))){
                    if(fields.containsKey("code")){msg="Vesper received your Microsoft sign-in. Close this tab and return to the launcher to see Minecraft access status.";result.complete(fields.get("code"));code=200;}
                    else{msg="Sign-in cancelled. You can return to Vesper.";result.completeExceptionally(new IOException("Microsoft sign-in was cancelled."));}
                }byte[] bytes=msg.getBytes(StandardCharsets.UTF_8);exchange.getResponseHeaders().set("Cache-Control","no-store");exchange.sendResponseHeaders(code,bytes.length);exchange.getResponseBody().write(bytes);
            }finally{exchange.close();}
        });server.start();
        try{Desktop.getDesktop().browse(URI.create(MS+"authorize?"+Net.query(Map.of("client_id",app,"response_type","code","redirect_uri",redirect,"scope",SCOPE,"state",state,"code_challenge",challenge,"code_challenge_method","S256","prompt","select_account"))));
            String code=result.get(4,TimeUnit.MINUTES);var login=token(Map.of("client_id",app,"grant_type","authorization_code","code",code,"redirect_uri",redirect,"code_verifier",verifier));
            clearProfile();
            save(app,Net.str(login,"refresh_token"));
            return minecraft(app,Net.str(login,"access_token"),accountEmail(login));
        }finally{server.stop(1);}
    }
    Session refresh()throws Exception{
        activeSession=null;
        if(!Files.isRegularFile(vault()))throw new IOException("Connect your Minecraft account first.");
        var saved=JsonParser.parseString(new String(vaultCrypt("--unprotect",Files.readAllBytes(vault())),StandardCharsets.UTF_8)).getAsJsonObject();
        String app=Net.str(saved,"app");JsonObject login;try{login=token(Map.of("client_id",app,"grant_type","refresh_token","refresh_token",Net.str(saved,"refresh"),"scope",SCOPE));}catch(SignInExpired ex){Files.deleteIfExists(vault());clearProfile();throw ex;}
        if(!Net.str(login,"refresh_token").isBlank())save(app,Net.str(login,"refresh_token"));return minecraft(app,Net.str(login,"access_token"),accountEmail(login));
    }
    static String accountEmail(JsonObject login){
        try{String[] parts=Net.str(login,"id_token").split("[.]");var data=JsonParser.parseString(new String(Base64.getUrlDecoder().decode(parts[1]),StandardCharsets.UTF_8)).getAsJsonObject();
            String email=Net.str(data,"email");return email.contains("@")?email:"";
        }catch(RuntimeException ignored){return "";}
    }
    void fallback(JsonObject xbox,JsonObject claims,String email)throws Exception{
        var info=linked();if(!email.isBlank())info.setProperty("login",email);
        String tag=Net.str(claims,"gtg"),xid=Net.str(claims,"xid");
        if(tag.isBlank()){
            try{var token=Net.post("https://xsts.auth.xboxlive.com/xsts/authorize",Net.obj("Properties",Net.obj("SandboxId","RETAIL","UserTokens",List.of(Net.str(xbox,"Token"))),"RelyingParty","http://xboxlive.com","TokenType","JWT"));
                var u=token.getAsJsonObject("DisplayClaims").getAsJsonArray("xui").get(0).getAsJsonObject();
                tag=Net.str(u,"gtg");xid=Net.str(u,"xid");
            }catch(IOException ignored){}
        }
        if(!tag.isBlank())info.setProperty("gamertag",tag);
        String root=System.getenv("APPDATA");var matched=root==null?null:LocalJavaProfile.match(Path.of(root,".minecraft"),info.getProperty("login",""),xid,tag);
        if(matched!=null){
            if(!matched.uuid().equals(info.getProperty("uuid")))info.remove("skin");
            info.setProperty("uuid",matched.uuid());info.setProperty("name",matched.name());
        }
        Path record=base.resolve("linked-display.properties");Engine.save(record,info);
        if(matched==null)return;
        var player=Net.get("https://sessionserver.mojang.com/session/minecraft/profile/"+matched.uuid()).getAsJsonObject();
        if(!matched.uuid().equalsIgnoreCase(Net.str(player,"id")))return;
        for(var item:player.getAsJsonArray("properties")){
            var prop=item.getAsJsonObject();if(!"textures".equals(Net.str(prop,"name")))continue;
            var data=JsonParser.parseString(new String(Base64.getDecoder().decode(Net.str(prop,"value")),StandardCharsets.UTF_8)).getAsJsonObject();
            var textures=data.getAsJsonObject("textures");if(textures==null||!textures.has("SKIN"))return;
            String url=Net.str(textures.getAsJsonObject("SKIN"),"url").replaceFirst("^http://","https://");
            if(!url.matches("https://textures[.]minecraft[.]net/texture/[a-fA-F0-9]{32,64}"))return;
            String asset="skin-"+url.substring(url.lastIndexOf('/')+1)+".png";
            if(!Files.exists(base.resolve(asset)))fetchSkin(url,base.resolve(asset));info.setProperty("skin",asset);Engine.save(record,info);return;
        }
    }
    Session minecraft(String app,String ms,String email)throws Exception{
        var xbox=Net.post("https://user.auth.xboxlive.com/user/authenticate",Net.obj("Properties",Net.obj("AuthMethod","RPS","SiteName","user.auth.xboxlive.com","RpsTicket","d="+ms),"RelyingParty","http://auth.xboxlive.com","TokenType","JWT"));
        var xsts=Net.post("https://xsts.auth.xboxlive.com/xsts/authorize",Net.obj("Properties",Net.obj("SandboxId","RETAIL","UserTokens",List.of(Net.str(xbox,"Token"))),"RelyingParty","rp://api.minecraftservices.com/","TokenType","JWT"));
        var xui=xsts.getAsJsonObject("DisplayClaims").getAsJsonArray("xui").get(0).getAsJsonObject();
        JsonObject mc;try{mc=Net.post("https://api.minecraftservices.com/authentication/login_with_xbox",Net.obj("identityToken","XBL3.0 x="+Net.str(xui,"uhs")+";"+Net.str(xsts,"Token")));}catch(Net.MinecraftAccessDenied denied){
            try{fallback(xbox,xui,email);}catch(Exception ignored){}throw denied;
        }
        long issued=System.nanoTime();String access=Net.str(mc,"access_token");var profile=Net.send(Net.req("https://api.minecraftservices.com/minecraft/profile").header("Authorization","Bearer "+access).build()).getAsJsonObject();
        if(Net.str(profile,"id").isBlank()||Net.str(profile,"name").isBlank())throw new IOException("No Minecraft Java profile was returned for this account.");
        var cache=new Properties();cache.setProperty("name",Net.str(profile,"name"));cache.setProperty("uuid",Net.str(profile,"id"));
        String uuid=Net.str(profile,"id");
        if(!uuid.matches("[a-fA-F0-9]{32}"))throw new IOException("Invalid Minecraft profile ID.");
        var skins=profile.getAsJsonArray("skins");
        if(skins!=null)for(var entry:skins){
            var skin=entry.getAsJsonObject();if(!"ACTIVE".equals(Net.str(skin,"state")))continue;
            String url=Net.str(skin,"url").replaceFirst("^http://","https://");
            if(url.matches("https://textures[.]minecraft[.]net/texture/[a-fA-F0-9]{32,64}")){
                String asset="skin-"+url.substring(url.lastIndexOf('/')+1)+".png";Path file=base.resolve(asset);
                try{if(!Files.isRegularFile(file))fetchSkin(url,file);cache.setProperty("skin",asset);}
                catch(IOException ex){/* A texture outage must not block game launch. */}
            }
            break;
        }
        Engine.save(base.resolve("verified-player.properties"),cache);
        var session=new Session(Net.str(profile,"name"),uuid,access,app,Net.str(xui,"xid"),false);
        long seconds;try{seconds=Long.parseLong(Net.str(mc,"expires_in"));}catch(NumberFormatException absent){seconds=0;}
        if(seconds>120){reusableUntil=issued+TimeUnit.SECONDS.toNanos(Math.min(3600,seconds-120));activeSession=session;
            long remaining=TimeUnit.NANOSECONDS.toMillis(reusableUntil-System.nanoTime());
            Path temp=null;
            try{
                temp=Files.createTempFile(base,"game-session",".part");
                var cached=new Cached(session,System.currentTimeMillis()+remaining);
                Files.write(temp,vaultCrypt("--protect",Net.JSON.toJson(cached).getBytes(StandardCharsets.UTF_8)));
                Engine.commit(temp,base.resolve("game-session.bin"));
            }catch(Exception ignored){ /* The live session is still usable. */ }
            finally{if(temp!=null)Files.deleteIfExists(temp);}
        }
        return session;
    }
}
