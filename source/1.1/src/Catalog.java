import com.google.gson.*;
import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.jar.JarFile;

final class Catalog {
    static final String MR="https://api.modrinth.com/v2/";
    record Hit(String id,String name,String summary,String web,String icon){
        Hit(String id,String name,String summary,String web){this(id,name,summary,web,"");}
        public String toString(){return name;}}
    record Artifact(String filename,String url,String alg,String hash){}
    final Engine engine;final Engine.Profile profile;final boolean packs;
    record Release(String id,String label,String channel,String date){public String toString(){return label+"   ·   "+channel+"   "+date;}}
    List<Release> versions(String id)throws Exception{
        var data=Net.get(MR+"project/"+id+"/version?game_versions="+Net.enc(Net.JSON.toJson(List.of(profile.version())))+(packs?"":"&loaders="+Net.enc("[\"fabric\"]")));
        var result=new ArrayList<Release>();for(var entry:data.getAsJsonArray()){
            var v=entry.getAsJsonObject();String date=Net.str(v,"date_published");
            result.add(new Release(Net.str(v,"id"),Net.str(v,"version_number"),Net.str(v,"version_type"),date.substring(0,Math.min(10,date.length()))));
        }return result;
    }
    Catalog(Engine e,Engine.Profile p,boolean packs){engine=e;profile=p;this.packs=packs;}
    String type(){return packs?"resourcepack":"mod";}
    List<Hit> search(String query)throws Exception{
        List<Hit> hits=new ArrayList<>();
        var facets=new ArrayList<List<String>>();facets.add(List.of("project_type:"+type()));facets.add(List.of("versions:"+profile.version()));if(!packs)facets.add(List.of("categories:fabric"));
        var response=Net.get(MR+"search?limit=40&query="+Net.enc(query)+"&facets="+Net.enc(Net.JSON.toJson(facets))).getAsJsonObject();
        for(var el:response.getAsJsonArray("hits")){var o=el.getAsJsonObject();hits.add(new Hit(Net.str(o,"project_id"),Net.str(o,"title"),Net.str(o,"description"),"https://modrinth.com/"+type()+"/"+Net.str(o,"slug"),Net.str(o,"icon_url")));}
        return hits;
    }
    void resolve(String id,String version,Map<String,Artifact> files,Set<String> seen)throws Exception{
        if(!seen.add(id+(version==null?"":"/"+version)))return;
        if(!packs&&version==null){
            var controls=Engine.read(Content.stateFile(profile));
            for(var m:engine.chosen(profile,engine.extras(profile)))if(m.id().equals(id)){
                if(!Content.active(controls,m.file()))throw new IOException(m.file()+" is disabled or removed. Restore the preset entry in Settings or enable its switch.");
                return;
            }
        }
        JsonObject v;
        if(version==null){var a=Net.get(MR+"project/"+id+"/version?game_versions="+Net.enc("[\""+profile.version()+"\"]")+(packs?"":"&loaders="+Net.enc("[\"fabric\"]"))).getAsJsonArray();if(a.isEmpty())throw new IOException("No compatible version for "+id);
            v=a.asList().stream().map(JsonElement::getAsJsonObject).filter(o->"release".equals(Net.str(o,"version_type"))).findFirst().orElse(a.get(0).getAsJsonObject());
        }else v=Net.get(MR+"version/"+version).getAsJsonObject();
        var file=v.getAsJsonArray("files").asList().stream().map(JsonElement::getAsJsonObject).filter(o->o.get("primary").getAsBoolean()).findFirst().orElse(v.getAsJsonArray("files").get(0).getAsJsonObject());
        var artifact=new Artifact(Net.str(file,"filename"),Net.str(file,"url"),"SHA-512",Net.str(file.getAsJsonObject("hashes"),"sha512"));
        if(files.containsKey(id)&&!files.get(id).equals(artifact))throw new IOException("Dependencies request conflicting versions of "+id);files.put(id,artifact);
        for(var el:v.getAsJsonArray("dependencies")){var d=el.getAsJsonObject();if("required".equals(Net.str(d,"dependency_type"))){String dep=Net.str(d,"project_id"),ver=Net.str(d,"version_id");if(dep.isBlank()&&!ver.isBlank())dep=Net.str(Net.get(MR+"version/"+ver).getAsJsonObject(),"project_id");if(!dep.isBlank())resolve(dep,ver.isBlank()?null:ver,files,seen);}}
    }
    static String modId(Path f)throws IOException{try(var z=new JarFile(f.toFile())){var entry=z.getEntry("fabric.mod.json");if(entry==null)throw new IOException("Expected a Fabric mod: "+f.getFileName());try(var in=new InputStreamReader(z.getInputStream(entry),java.nio.charset.StandardCharsets.UTF_8)){return Net.str(JsonParser.parseReader(in).getAsJsonObject(),"id");}}}
    String install(Hit hit,String version,Consumer<String> progress)throws Exception{
        var files=new LinkedHashMap<String,Artifact>();resolve(hit.id(),version,files,new HashSet<>());
        Path dest=profile.game().resolve(packs?"resourcepacks":"mods");Files.createDirectories(dest);
        Path stage=Files.createTempDirectory(dest,".sonder-install-");var backups=new LinkedHashMap<Path,Path>();var created=new ArrayList<Path>();
        var state=Engine.read(Content.stateFile(profile));var items=Content.list(engine,profile,packs);var presets=engine.chosen(profile,engine.extras(profile));
        var activePacks=Content.enabledPacks(profile);String oldPack=null,newPack=null;
        try{
            // Verify all downloads before moving installed files.
            for(var entry:files.entrySet()){
                var f=entry.getValue();if(!f.filename().matches("[^/\\\\:]+")||!f.filename().toLowerCase(Locale.ROOT).endsWith(packs?".zip":".jar"))throw new IOException("Unsupported filename.");
                Path tmp=stage.resolve(f.filename());if(Files.exists(tmp))throw new IOException("Conflicting download filenames.");
                progress.accept("Downloading "+f.filename());Net.download(f.url(),tmp,f.alg(),f.hash());
                if(packs){if(!Packs.folder(tmp).equals("resourcepacks"))throw new IOException("Expected a resource pack.");}else modId(tmp);
            }
            for(var entry:files.entrySet()){
                var f=entry.getValue();String id=entry.getKey();Path tmp=stage.resolve(f.filename());String fabric=packs?"":modId(tmp);
                String project="mr/"+id;String old=state.getProperty("file/"+project,"");Engine.Mod preset=null;
                if(!packs)for(var m:presets)if(m.id().equals(id)||m.file().equals(old)){preset=m;old=m.file();break;}
                if(old.isEmpty()&&!packs)for(var item:items){Path path=item.on()?item.file(profile):Content.parked(profile,item.name());
                    if(Files.isRegularFile(path)&&fabric.equals(modId(path))){old=item.name();break;}}
                if(!packs&&preset==null)for(var m:presets)if(m.file().equals(old)){preset=m;break;}
                if(old.isEmpty()&&packs&&files.size()==1){
                    // Recognize older installs by their provider filenames when there is no receipt yet.
                    var known=Net.get(MR+"project/"+id+"/version?game_versions="+Net.enc(Net.JSON.toJson(List.of(profile.version())))).getAsJsonArray();
                    var names=new HashSet<String>();for(var v:known)for(var candidate:v.getAsJsonObject().getAsJsonArray("files"))names.add(Net.str(candidate.getAsJsonObject(),"filename"));
                    var matches=items.stream().filter(i->names.contains(i.name())).toList();if(matches.size()==1)old=matches.get(0).name();
                }
                if(!old.isEmpty()&&!Path.of(old).getFileName().toString().equals(old))throw new IOException("Invalid installed filename.");
                boolean disabled=!packs&&"disabled".equals(state.getProperty("mods/"+old));
                Path target=disabled?Content.parked(profile,f.filename()):dest.resolve(f.filename());Files.createDirectories(target.getParent());
                Path previous=old.isEmpty()?null:disabled?Content.parked(profile,old):dest.resolve(old);
                if(Files.exists(target)&&!target.equals(previous)&&Files.mismatch(tmp,target)!=-1)throw new IOException("A different file already uses "+f.filename());
                if(previous!=null&&Files.isRegularFile(previous)&&(!previous.equals(target)||Files.mismatch(tmp,previous)!=-1)){
                    Path backup=Files.createTempFile(stage,"old",".bak");Files.move(previous,backup,StandardCopyOption.REPLACE_EXISTING);backups.put(previous,backup);}
                if(!Files.exists(target)){Files.move(tmp,target);created.add(target);}
                if(preset!=null){
                    var override=new Engine.Mod(preset.group(),preset.slug(),preset.id(),f.filename(),Engine.hash(target),f.url(),preset.deps());
                    state.setProperty("override/"+preset.id(),Net.JSON.toJson(override));
                }
                if(!old.isEmpty()&&!old.equals(f.filename())){state.remove("source/"+old);state.remove("mods/"+old);}
                if(!packs){if(disabled)state.setProperty("mods/"+f.filename(),"disabled");else state.remove("mods/"+f.filename());}
                if(packs&&!old.equals(f.filename())&&activePacks.contains("file/"+old)){oldPack=old;newPack=f.filename();}
                state.setProperty("source/"+f.filename(),project);state.setProperty("file/"+project,f.filename());
            }
            if(oldPack!=null){Content.setPack(profile,newPack,true);Content.setPack(profile,oldPack,false);}
            Engine.save(Content.stateFile(profile),state);return "Installed in "+profile.name()+".";
        }catch(Exception ex){
            for(var path:created)Files.deleteIfExists(path);for(var backup:backups.entrySet())Files.move(backup.getValue(),backup.getKey());
            if(oldPack!=null){Content.setPack(profile,oldPack,true);if(!activePacks.contains("file/"+newPack))Content.setPack(profile,newPack,false);}throw ex;
        }finally{try(var paths=Files.list(stage)){for(var path:paths.toList())Files.deleteIfExists(path);}Files.deleteIfExists(stage);}
    }
    void open(Sonder app)throws Exception{open(app,null);}
    void open(Sonder app,Hit initial)throws Exception{
        if(!app.editable(profile))return;
        var installed=new HashSet<String>();
        var prefs=Engine.read(Content.stateFile(profile));
        var presetIds=new HashMap<String,String>();if(!packs)for(var mod:engine.chosen(profile,engine.extras(profile)))presetIds.put(mod.file(),mod.id());
        for(var item:Content.list(engine,profile,packs)){
            String source=prefs.getProperty("source/"+item.name(),"");
            if(source.startsWith("mr/"))installed.add(source.substring(3));else if(presetIds.containsKey(item.name()))installed.add(presetIds.get(item.name()));
        }
        var panel=new JPanel(new BorderLayout(8,8));panel.setBorder(new javax.swing.border.EmptyBorder(14,14,14,14));
        var bar=new JPanel(new BorderLayout(6,0));var input=new JTextField();var search=Sonder.button("Search",true);bar.add(input);bar.add(search,BorderLayout.EAST);var heading=new JPanel(new BorderLayout(0,8));heading.setOpaque(false);
        var title=new JPanel(new BorderLayout());title.setOpaque(false);title.add(Sonder.label("Browse Modrinth",26,true));
        var back=Sonder.action("Back to profile",()->{app.studio.page="Add-ons";app.activeTab=packs?1:0;app.refresh();},app);title.add(back,BorderLayout.EAST);heading.add(title,BorderLayout.NORTH);heading.add(Sonder.label(profile.name()+"    /    "+profile.loader()+" "+profile.version()+"    /    "+(packs?"Resource packs":"Mods"),13,false));heading.add(bar,BorderLayout.SOUTH);panel.add(heading,BorderLayout.NORTH);
        var model=new DefaultListModel<Hit>();var list=new JList<>(model);var results=Look.scroll(list);panel.add(results);
        var checked=new LinkedHashSet<Hit>();
        list.setFixedCellHeight(70);list.setCellRenderer((l,h,i,on,focus)->{
            var cell=new JPanel(new BorderLayout(10,0));cell.setBorder(BorderFactory.createEmptyBorder(5,10,5,10));cell.setBackground(on?app.side.tint(28):Look.surface);
            var tick=new JCheckBox();tick.setSelected(checked.contains(h));tick.setOpaque(false);var leading=new JPanel(new BorderLayout(5,0));leading.setOpaque(false);leading.add(tick,BorderLayout.WEST);var badge=new JLabel(ProjectIcons.get(h));if(badge.getIcon()==null)badge.setText(h.name().isEmpty()?"?":h.name().substring(0,1));badge.setHorizontalAlignment(SwingConstants.CENTER);badge.setPreferredSize(new Dimension(44,44));leading.add(badge);cell.add(leading,BorderLayout.WEST);
            var words=new JPanel(new GridLayout(2,1));words.setOpaque(false);words.add(Sonder.label(h.name(),16,true));words.add(Sonder.label(h.summary(),12,false));cell.add(words);var quick=Sonder.button(installed.contains(h.id())?"Added":"Add",!installed.contains(h.id()));quick.setPreferredSize(new Dimension(70,32));quick.setEnabled(!installed.contains(h.id()));cell.add(quick,BorderLayout.EAST);return cell;
        });
        Consumer<Hit> quickAdd=hit->{
            if(app.busy||installed.contains(hit.id())||!app.editable(profile))return;
            app.work("Adding "+hit.name()+"…",updates->{var available=versions(hit.id());if(available.isEmpty())throw new IOException("No compatible version.");var chosen=available.stream().filter(v->v.channel().equals("release")).findFirst().orElse(available.get(0));String result=install(hit,chosen.id(),updates);SwingUtilities.invokeLater(()->{installed.add(hit.id());list.repaint();});return result;});
        };
        list.getInputMap().put(KeyStroke.getKeyStroke("ENTER"),"installSelected");list.getActionMap().put("installSelected",new AbstractAction(){
            public void actionPerformed(java.awt.event.ActionEvent event){var hit=list.getSelectedValue();if(hit!=null)quickAdd.accept(hit);}
        });
        Runnable mark=()->{Hit h=list.getSelectedValue();if(h!=null){if(!checked.remove(h))checked.add(h);list.repaint();}};
        list.addMouseListener(new java.awt.event.MouseAdapter(){public void mouseClicked(java.awt.event.MouseEvent ev){int i=list.locationToIndex(ev.getPoint());if(i<0||!list.getCellBounds(i,i).contains(ev.getPoint()))return;if(ev.getX()<40)mark.run();else if(ev.getX()>=list.getWidth()-80)quickAdd.accept(model.get(i));}});
        list.getInputMap().put(KeyStroke.getKeyStroke("SPACE"),"mark");list.getActionMap().put("mark",new AbstractAction(){public void actionPerformed(java.awt.event.ActionEvent ev){mark.run();}});
        var detail=app.studio.card();detail.setPreferredSize(new Dimension(255,230));detail.setBorder(BorderFactory.createEmptyBorder(16,12,16,12));
        var project=Sonder.label("Project",18,true);detail.add(project,BorderLayout.NORTH);
        var msg=new JTextArea();msg.setLineWrap(true);msg.setWrapStyleWord(true);msg.setEditable(false);msg.setOpaque(false);detail.add(Look.scroll(msg));
        var buttons=Sonder.row();var install=Sonder.button("Install checked",false);var site=Sonder.button("Project page",false);var all=Sonder.button("Select all",false);
        all.addActionListener(a->{checked.addAll(Collections.list(model.elements()));list.repaint();});var clear=Sonder.button("Clear",false);clear.addActionListener(a->{checked.clear();list.repaint();});
        buttons.add(all);buttons.add(clear);buttons.add(install);panel.add(buttons,BorderLayout.SOUTH);
        var releases=new JComboBox<Release>();var add=Sonder.button("Install version",true);var picker=new JPanel(new GridLayout(0,1,0,6));picker.setOpaque(false);add.setEnabled(false);
        picker.add(Sonder.label("Compatible version",13,true));picker.add(releases);picker.add(add);picker.add(site);detail.add(picker,BorderLayout.SOUTH);panel.add(detail,BorderLayout.WEST);
        list.addListSelectionListener(a->{if(a.getValueIsAdjusting())return;var hit=list.getSelectedValue();releases.removeAllItems();add.setEnabled(false);project.setText(hit==null?"Project":hit.name());if(hit==null)return;
            msg.setText(hit.summary());background(()->versions(hit.id()),rows->{if(!hit.equals(list.getSelectedValue()))return;
                for(var v:rows)releases.addItem(v);add.setEnabled(list.isEnabled()&&!rows.isEmpty());if(rows.isEmpty())msg.setText("No compatible files.");},msg,()->{});
        });
        add.addActionListener(a->{var hit=list.getSelectedValue();var release=(Release)releases.getSelectedItem();if(hit==null||release==null||!app.editable(profile))return;
            for(var control:List.of(list,releases,add,install,search))control.setEnabled(false);app.busy=true;
            background(()->install(hit,release.id(),s->SwingUtilities.invokeLater(()->msg.setText(s))),result->{installed.add(hit.id());list.repaint();msg.setText(result);},msg,()->{
                for(var control:List.of(list,releases,add,install,search))control.setEnabled(true);app.busy=false;app.safe(app::refresh);});
        });
        search.addActionListener(a->{search.setEnabled(false);install.setEnabled(false);checked.clear();model.clear();msg.setText("Searching…");String term=input.getText();
            background(()->search(term),hits->{for(var h:hits){model.addElement(h);ProjectIcons.load(h,list);}if(hits.isEmpty())msg.setText("No matching "+profile.version()+" files.");else list.setSelectedIndex(0);},msg,()->{search.setEnabled(true);install.setEnabled(true);});});
        input.addActionListener(a->search.doClick());site.addActionListener(a->app.safe(()->{if(list.getSelectedValue()!=null)Desktop.getDesktop().browse(URI.create(list.getSelectedValue().web()));}));
        install.addActionListener(a->{if(!app.editable(profile))return;var selected=List.copyOf(checked);if(selected.isEmpty()){msg.setText("Check the projects you want to install.");return;}for(var control:List.of(list,releases,add,install,search))control.setEnabled(false);app.busy=true;
            background(()->{int count=0;var errors=new ArrayList<String>();for(var hit:selected)try{install(hit,null,s->SwingUtilities.invokeLater(()->msg.setText(s)));count++;SwingUtilities.invokeLater(()->{installed.add(hit.id());list.repaint();});}catch(Exception ex){errors.add(hit.name()+": "+Sonder.message(ex));}return count+" projects ready. "+String.join("\n",errors);},msg::setText,msg,()->{for(var control:List.of(list,releases,add,install,search))control.setEnabled(true);app.busy=false;app.safe(app::refresh);});});
        app.studio.page="Browse";app.studio.browser=panel;app.studio.show(profile);input.requestFocusInWindow();
        if(initial==null)search.doClick();else{model.addElement(initial);ProjectIcons.load(initial,list);list.setSelectedIndex(0);}
    }
    static <T> void background(Callable<T> task,Consumer<T> done,JTextArea msg,Runnable cleanup){new SwingWorker<T,Void>(){
        protected T doInBackground()throws Exception{return task.call();}
        protected void done(){try{done.accept(get());}catch(Exception ex){msg.setText(Sonder.message(ex));}finally{cleanup.run();}}
    }.execute();}
}
