package sonder.hud;
import java.nio.file.*;
import java.io.*;
import java.util.*;
import net.fabricmc.loader.api.FabricLoader;

public final class Layout {
    enum Widget { FPS, PING, CPS, EFFECTS, ARMOR, COORDS, KEYS, FACING, SCOREBOARD }
    static final Layout INSTANCE=new Layout(FabricLoader.getInstance().getConfigDir().resolve("sonder-hud.properties"));
    final Path file;
    final Properties values=new Properties();
    final Set<String> hidden=new HashSet<>();
    Layout(Path file){
        this.file=file;
        if(Files.isRegularFile(file))try(var in=Files.newInputStream(file)){values.load(in);}catch(IOException|IllegalArgumentException ex){System.err.println("Sonder HUD settings: "+ex.getMessage());}
        for(String id:values.getProperty("hidden","").split(","))if(!id.isBlank())hidden.add(id);
    }
    public boolean flag(String key){return Boolean.parseBoolean(values.getProperty(key,Set.of("ARMOR","COORDS","KEYS","FACING").contains(key)?"false":"true"));}
    void toggle(String key){values.setProperty(key,Boolean.toString(!flag(key)));}
    static double limit(double n){return Double.isFinite(n)?Math.max(0,Math.min(n,1)):0;}
    int pos(Widget w,String axis,int range,int initial){
        try{return (int)Math.round(limit(Double.parseDouble(values.getProperty(w+"."+axis,"")))*Math.max(0,range));}
        catch(NumberFormatException ex){return Math.clamp(initial,0,Math.max(0,range));}
    }
    void place(Widget w,String axis,int value,int range){values.setProperty(w+"."+axis,Double.toString(limit((double)value/Math.max(1,range))));}
    int percent(Widget w){
        try{return Math.clamp(Integer.parseInt(values.getProperty(w+".scale","100")),50,200);}
        catch(NumberFormatException ignored){return 100;}
    }
    void resize(Widget w,int steps){values.setProperty(w+".scale",Integer.toString(Math.clamp(percent(w)+steps*10,50,200)));}
    boolean customBoard(){return percent(Widget.SCOREBOARD)!=100||values.containsKey("SCOREBOARD.x")||values.containsKey("SCOREBOARD.y");}
    void reset(){values.clear();hidden.clear();}
    void save(){
        try{Files.createDirectories(file.getParent());Path tmp=Files.createTempFile(file.getParent(),"sonder-hud",".tmp");
            try{values.setProperty("hidden",String.join(",",new TreeSet<>(hidden)));try(var out=Files.newOutputStream(tmp)){values.store(out,"Sonder HUD");}
                try{Files.move(tmp,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException ex){Files.move(tmp,file,StandardCopyOption.REPLACE_EXISTING);}
            }finally{Files.deleteIfExists(tmp);}
        }catch(IOException ex){System.err.println("Sonder HUD save: "+ex.getMessage());}
    }
}
