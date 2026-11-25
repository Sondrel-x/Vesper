import java.nio.file.*;
import java.util.*;
import javax.swing.*;

final class PerformanceChoices {
    static final Map<String,String> names=Map.ofEntries(
        Map.entry("better-block-entities","Better Block Entities"),Map.entry("badoptimizations","BadOptimizations"),
        Map.entry("threadtweak","ThreadTweak"),Map.entry("sodium-relief","Sodium Relief"),
        Map.entry("c2me-fabric","C2ME"),Map.entry("krypton","Krypton"),Map.entry("modernfix","ModernFix"),
        Map.entry("particle_core","Particle Core"),Map.entry("zfastnoise","ZFastNoise"),
        Map.entry("sodium","Sodium"),Map.entry("lithium","Lithium"),Map.entry("ferrite-core","FerriteCore"),
        Map.entry("entityculling","Entity Culling"),Map.entry("moreculling","More Culling"),
        Map.entry("dynamic-fps","Dynamic FPS"),Map.entry("immediatelyfast","ImmediatelyFast"));
    static void open(Sonder app,Engine.Profile profile)throws Exception{
        if(!app.editable(profile))return;
        var checks=new LinkedHashMap<Engine.Mod,JCheckBox>();
        var body=new JPanel(new java.awt.GridLayout(0,2,4,4));
        for(var m:app.e.catalog)if(names.containsKey(m.slug())){
            var box=new JCheckBox(names.get(m.slug()));checks.put(m,box);body.add(box);
        }
        var all=new JButton("Enable all");all.addActionListener(e->checks.values().forEach(c->c.setSelected(true)));
        if(JOptionPane.showConfirmDialog(app,new Object[]{"Choose mods to enable in "+profile.name()+".",all,body},
                "Performance mods",JOptionPane.OK_CANCEL_OPTION)!=JOptionPane.OK_OPTION)return;
        var picks=checks.entrySet().stream().filter(x->x.getValue().isSelected()).map(Map.Entry::getKey).toList();
        if(picks.isEmpty())return;
        app.work("Checking compatible performance mods…",update->{
            var catalog=new Catalog(app.e,profile,false);var issues=new ArrayList<String>();int enabled=0;
            for(var m:picks){
                String name=names.get(m.slug());update.accept("Checking "+name+"…");
                try{
                    var receipt=Engine.read(Content.stateFile(profile));
                    var managed=app.e.chosen(profile,app.e.extras(profile)).stream().filter(x->x.id().equals(m.id())).findFirst();
                    String file=managed.map(Engine.Mod::file).orElse(receipt.getProperty("file/mr/"+m.id()));
                    if(file==null||managed.isEmpty()&&!Files.isRegularFile(profile.game().resolve("mods").resolve(file))&&!Files.isRegularFile(Content.parked(profile,file))){
                        var versions=catalog.versions(m.id());
                        if(versions.isEmpty()){issues.add(name+" has no compatible release.");continue;}
                        var release=versions.stream().filter(x->x.channel().equals("release")).findFirst().orElse(versions.get(0));
                        catalog.install(new Catalog.Hit(m.id(),name,"",""),release.id(),update);
                        file=Engine.read(Content.stateFile(profile)).getProperty("file/mr/"+m.id());
                    }
                    if(file!=null&&!Content.active(Engine.read(Content.stateFile(profile)),file)){
                        var item=new Content.Item("mods",file,true,false,managed.isPresent());
                        Content.change(profile,item,true,false);
                    }
                    enabled++;
                }catch(Exception ex){issues.add(name+" — "+Sonder.message(ex));}
            }
            if(!issues.isEmpty())SwingUtilities.invokeLater(()->JOptionPane.showMessageDialog(app,String.join("\n",issues),"Performance mod results",JOptionPane.INFORMATION_MESSAGE));
            return "Enabled "+enabled+" of "+picks.size()+" performance mods.";
        });
    }
}
