package sonder.hud;
import net.minecraft.*;
import java.util.*;
import static sonder.hud.Layout.Widget;

public final class Menu extends class_437 {
    enum Page { WIDGETS, EFFECTS, MOVE }
    record Button(Hud.Box box,Runnable action){}
    Page page=Page.WIDGETS;int effectPage;
    final List<Button> buttons=new ArrayList<>();Map<Widget,Hud.Box> boxes=Map.of();
    Widget dragging,selected;int offsetX,offsetY;
    public Menu(){super(class_2561.method_43470("Vesper HUD"));}
    @Override public boolean method_25421(){return false;}
    @Override public void method_25432(){Hud.cfg.save();}
    void button(class_332 g,int x,int y,int w,int h,String name,boolean active,int mx,int my,Runnable action){
        var box=new Hud.Box(x,y,w,h);buttons.add(new Button(box,action));
        g.method_25294(x,y,x+w,y+h,box.hit(mx,my)?0xff775d88:active?0xff594266:0xff39313f);
        Hud.text(g,name,x+5,y+(h-8)/2,active?0xfff1d7ff:Hud.TEXT);
    }
    @Override public void method_25394(class_332 g,int mx,int my,float delta){
        buttons.clear();int width=g.method_51421(),height=g.method_51443();
        if(page==Page.MOVE){
            boxes=Hud.draw(g,true);
            String hint=selected==null?"Drag to move  •  Scroll a widget to resize":"Drag / scroll  •  "+Hud.label(selected)+"  "+Hud.cfg.percent(selected)+"%";int n=Hud.game().field_1772.method_1727(hint);
            Hud.text(g,hint,Math.max(2,(width-n)/2),height-11,Hud.SOFT);return;
        }
        int w=Math.min(310,width-6),h=202,x=(width-w)/2,y=Math.max(0,(height-h)/2),col=(w-28)/2;
        g.method_25294(0,0,width,height,0x740b0710);
        g.method_25294(x,y,x+w,y+h,0xfb261f2e);g.method_25294(x,y,x+w,y+2,Hud.SOFT);
        Hud.text(g,"VESPER   /   HUD",x+10,y+10,Hud.TEXT);
        button(g,x+w-45,y+6,36,17,"Done",true,mx,my,this::method_25419);
        button(g,x+10,y+28,col,18,"Widgets",page==Page.WIDGETS,mx,my,()->page=Page.WIDGETS);
        button(g,x+16+col,y+28,col,18,"Potion effects",page==Page.EFFECTS,mx,my,()->page=Page.EFFECTS);
        if(page==Page.WIDGETS){
            var widgets=Widget.values();for(int i=0;i<widgets.length;i++){var widget=widgets[i];boolean on=Hud.cfg.flag(widget.name());
                button(g,x+10+(i%2)*(col+6),y+52+(i/2)*22,col,19,(on?"✓ ":"– ")+Hud.label(widget),on,mx,my,()->Hud.cfg.toggle(widget.name()));}
            button(g,x+10,y+163,col,22,"Move / resize HUD",true,mx,my,()->page=Page.MOVE);
            button(g,x+16+col,y+163,col,22,"Panel background",Hud.cfg.flag("background"),mx,my,()->Hud.cfg.toggle("background"));
            Hud.text(g,"Right Shift opens or closes this menu.",x+10,y+188,Hud.MUTED);
        }else{
            for(int i=0;i<2;i++){String key=i==0?"level":"timer";boolean on=Hud.cfg.flag(key);
                button(g,x+10+i*(col+6),y+52,col,19,(on?"✓ ":"– ")+(i==0?"Effect levels":"Time remaining"),on,mx,my,()->Hud.cfg.toggle(key));}
            Hud.text(g,"Visible effects",x+10,y+80,Hud.SOFT);
            var entries=new LinkedHashMap<String,String>();for(var ef:Hud.effects())entries.put(ef.method_5579().method_55840(),Hud.effectName(ef));
            for(String id:new TreeSet<>(Hud.cfg.hidden))entries.putIfAbsent(id,id.substring(id.indexOf(':')+1));
            var ids=new ArrayList<>(entries.keySet());int count=(ids.size()+5)/6;effectPage=Math.clamp(effectPage,0,Math.max(0,count-1));
            if(ids.isEmpty())Hud.text(g,"Active effects appear here.",x+10,y+99,Hud.MUTED);
            for(int i=0;i<6&&effectPage*6+i<ids.size();i++){String id=ids.get(effectPage*6+i);boolean on=!Hud.cfg.hidden.contains(id);
                button(g,x+10+(i%2)*(col+6),y+94+(i/2)*21,col,18,(on?"✓ ":"– ")+entries.get(id),on,mx,my,()->{if(!Hud.cfg.hidden.remove(id))Hud.cfg.hidden.add(id);});}
            if(count>1)button(g,x+10,y+160,col,18,"Page "+(effectPage+1)+" / "+count,true,mx,my,()->effectPage=(effectPage+1)%count);
        }
    }
    @Override public boolean method_25402(class_11909 click,boolean twice){
        if(click.method_74245()!=0)return false;double x=click.comp_4798(),y=click.comp_4799();
        if(page==Page.MOVE){for(var w:Widget.values()){var b=boxes.get(w);if(b!=null&&b.hit(x,y)){selected=w;dragging=w;offsetX=(int)x-b.x();offsetY=(int)y-b.y();return true;}}}
        else for(var button:buttons)if(button.box.hit(x,y)){button.action.run();return true;}
        return false;
    }
    @Override public boolean method_25401(double x,double y,double horizontal,double vertical){
        if(page!=Page.MOVE||vertical==0)return false;
        var widgets=Widget.values();for(int i=widgets.length-1;i>=0;i--){var w=widgets[i];var box=boxes.get(w);
            if(box!=null&&box.hit(x,y)){selected=w;Hud.cfg.resize(w,vertical>0?1:-1);return true;}}
        return false;
    }
    @Override public boolean method_25403(class_11909 click,double dx,double dy){
        if(dragging==null)return false;var b=boxes.get(dragging);if(b==null)return false;
        Hud.cfg.place(dragging,"x",(int)click.comp_4798()-offsetX,field_22789-b.w());
        Hud.cfg.place(dragging,"y",(int)click.comp_4799()-offsetY,field_22790-b.h());return true;
    }
    @Override public boolean method_25406(class_11909 click){dragging=null;return true;}
    @Override public boolean method_25404(class_11908 key){if(key.comp_4795()==256&&page==Page.MOVE){dragging=null;page=Page.WIDGETS;Hud.cfg.save();return true;}return super.method_25404(key);}
}
