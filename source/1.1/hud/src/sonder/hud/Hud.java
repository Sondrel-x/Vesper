package sonder.hud;
import net.minecraft.*;
import java.util.*;
import static sonder.hud.Layout.Widget;

public final class Hud {
    static final int TEXT=0xfff6effb, SOFT=0xffd2a9f1, MUTED=0xffc0b0ca, PANEL=0xd9251e31;
    static final Deque<Long> left=new ArrayDeque<>(), right=new ArrayDeque<>();
    public static class_310 game(){return class_310.method_1551();}
    public static final Layout cfg=Layout.INSTANCE;
    static void trim(Deque<Long> q,long now){while(!q.isEmpty()&&now-q.peekFirst()>=1_000_000_000L)q.removeFirst();}
    public static void click(int button){if(button!=0&&button!=1)return;var q=button==0?left:right;long now=System.nanoTime();trim(q,now);q.addLast(now);}
    record Box(int x,int y,int w,int h){boolean hit(double a,double b){return a>=x&&a<x+w&&b>=y&&b<y+h;}}
    static String label(Widget w){return switch(w){case SCOREBOARD->"Server scoreboard";case PING->"Server ping";case EFFECTS->"Potion effects";case KEYS->"Movement keys";case COORDS->"Coordinates";case FACING->"Direction";case ARMOR->"Armor durability";default->w.name();};}
    static List<class_1293> effects(){
        if(game().field_1724==null)return List.of();
        return game().field_1724.method_6026().stream().sorted(Comparator.comparing(Hud::effectName)).toList();
    }
    static String effectName(class_1293 ef){return ef.method_5579().comp_349().method_5560().getString();}
    static String duration(int ticks){int seconds=Math.max(0,ticks)/20;return seconds/60+":"+(seconds%60<10?"0":"")+seconds%60;}
    static List<String> lines(Widget w,boolean preview){
        var mc=game();var player=mc.field_1724;if(player==null)return List.of();
        switch(w){
            case FPS:return List.of(mc.method_47599()+" FPS");
            case PING:{var n=mc.method_1562();var entry=n==null?null:n.method_2871(player.method_5667());
                return List.of(mc.method_1542()?"Local world":entry==null?"Ping —":entry.method_2959()+" ms");}
            case CPS:{long now=System.nanoTime();trim(left,now);trim(right,now);return List.of("L "+left.size()+"  R "+right.size()+" CPS");}
            case COORDS:return List.of("XYZ  "+player.method_31477()+"  "+player.method_31478()+"  "+player.method_31479());
            case FACING:return List.of("Facing  "+player.method_5735().toString());
            case KEYS:{var o=mc.field_1690;return List.of((o.field_1894.method_1434()?"[W] ":" W  ")+(o.field_1913.method_1434()?"[A] ":" A  ")+(o.field_1881.method_1434()?"[S] ":" S  ")+(o.field_1849.method_1434()?"[D]":" D"));}

        }
        return List.of();
    }
    record Row(class_2561 title,class_2561 value,class_1799 item,class_1293 effect,int color){
        static Row text(String s){return new Row(class_2561.method_43470(s),null,null,null,TEXT);}
        int width(){var font=game().field_1772;return font.method_27525(title)+(value==null?0:5+font.method_27525(value))+(item!=null||effect!=null?20:0);}
        int height(){return item!=null||effect!=null?20:11;}
    }
    static List<Row> rows(Widget w,boolean preview){
        var player=game().field_1724;var result=new ArrayList<Row>();
        if(w==Widget.ARMOR){
            for(var slot:List.of(class_1304.field_6169,class_1304.field_6174,class_1304.field_6172,class_1304.field_6166)){
                var stack=player.method_6118(slot);if(stack.method_7960())continue;
                boolean damageable=stack.method_7963();
                String amount=damageable?(stack.method_7936()-stack.method_7919())+"/"+stack.method_7936():"—";
                result.add(new Row(class_2561.method_43470(amount),null,stack,null,damageable?0xff000000|stack.method_31580():MUTED));
            }
        }else if(w==Widget.EFFECTS){
            for(var effect:effects()){
                if(cfg.hidden.contains(effect.method_5579().method_55840()))continue;
                String title=effectName(effect);if(cfg.flag("level"))title+=" "+(effect.method_5578()+1);
                if(cfg.flag("timer"))title+="  "+(effect.method_48559()?"∞":duration(effect.method_5584()));
                result.add(new Row(class_2561.method_43470(title),null,null,effect,TEXT));
            }
        }else if(w==Widget.SCOREBOARD){
            if(!preview&&!cfg.customBoard())return List.of();
            var board=game().field_1687.method_8428();
            var team=board.method_1164(player.method_5820());
            var slot=team==null?null:class_8646.method_52622(team.method_1202());
            var objective=slot==null?null:board.method_1189(slot);
            if(objective==null)objective=board.method_1189(class_8646.field_45157);
            if(objective!=null){
                result.add(new Row(objective.method_1114(),null,null,null,TEXT));
                var format=objective.method_55380(class_9025.field_47567);
                board.method_1184(objective).stream().filter(entry->!entry.method_55385())
                    .sorted(Comparator.comparing(class_9011::comp_2128).reversed().thenComparing(class_9011::comp_2127,String.CASE_INSENSITIVE_ORDER))
                    .limit(15).forEach(entry->result.add(new Row(class_268.method_1142(board.method_1164(entry.comp_2127()),entry.method_55387()),entry.method_55386(format),null,null,TEXT)));
            }else if(preview){result.add(Row.text("Server scoreboard"));result.add(Row.text("Score preview"));}
        }else for(var line:lines(w,preview))result.add(Row.text(line));
        if(preview&&result.isEmpty())result.add(Row.text(label(w)));
        return result;
    }
    static void text(class_332 g,String s,int x,int y,int color){g.method_25303(game().field_1772,s,x,y,color);}
    public static boolean replaceBoard(){return !cfg.flag("SCOREBOARD")||cfg.customBoard()||game().field_1755 instanceof Menu menu&&menu.page==Menu.Page.MOVE;}
    public static EnumMap<Widget,Box> draw(class_332 g,boolean preview){
        var boxes=new EnumMap<Widget,Box>(Widget.class);int width=g.method_51421(),height=g.method_51443();
        for(Widget w:Widget.values()){
            if(!cfg.flag(w.name()))continue;var rows=rows(w,preview);if(rows.isEmpty())continue;
            int bw=12+rows.stream().mapToInt(Row::width).max().orElse(48),bh=6+rows.stream().mapToInt(Row::height).sum();
            float scale=cfg.percent(w)/100f;int sw=(int)Math.ceil(bw*scale),sh=(int)Math.ceil(bh*scale);
            int dx=w==Widget.EFFECTS||w==Widget.SCOREBOARD?width-sw-4:6;
            int dy=switch(w){case FPS,EFFECTS->6;case PING->26;case CPS->46;case ARMOR->68;case COORDS->160;case KEYS->182;case FACING->204;case SCOREBOARD->(height-sh)/2;};
            var b=new Box(cfg.pos(w,"x",width-sw,dx),cfg.pos(w,"y",height-sh,dy),Math.min(width,sw),Math.min(height,sh));boxes.put(w,b);
            var matrices=g.method_51448();matrices.pushMatrix();
            try{matrices.translate((float)b.x,(float)b.y);matrices.scale(scale,scale);
                if(preview||cfg.flag("background"))g.method_25294(0,0,bw,bh,PANEL);g.method_25294(0,0,2,bh,SOFT);
                int y=3;var font=game().field_1772;
                for(var row:rows){
                    int x=6;if(row.item!=null){g.method_51427(row.item,x,y+2);x+=20;}
                    if(row.effect!=null){g.method_52706(class_10799.field_56883,class_329.method_71644(row.effect.method_5579()),x,y+2,16,16);x+=20;}
                    int ty=y+(row.height()-9)/2;g.method_51439(font,row.title,x,ty,row.color,true);
                    if(row.value!=null)g.method_51439(font,row.value,bw-6-font.method_27525(row.value),ty,TEXT,true);
                    y+=row.height();
                }
            }finally{matrices.popMatrix();}
        }return boxes;
    }
}
