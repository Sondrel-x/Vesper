import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.geom.*;
import java.io.*;
import java.nio.file.*;
import java.nio.channels.*;
import java.util.*;
import java.util.List;

public final class Sonder extends JFrame {
    static final Color BG=new Color(15,25,29), INK=new Color(241,247,239), MUTED=new Color(177,196,198), PURPLE=new Color(64,176,157);
    final Engine e;
    final Studio studio=new Studio(this);
    final DefaultListModel<Engine.Profile> model=new DefaultListModel<>();
    final JList<Engine.Profile> list=new JList<>(model);
    final JLabel title=label("",29,true), subtitle=label("",14,false),status=label("Ready",12,false);
    final JPanel contents=new JPanel(); final JButton play=button("Launch Minecraft",true),settings=button("Profile options",false);
    boolean busy;
    final JTabbedPane tabs=new JTabbedPane();
    final Neighborhood side=new Neighborhood();
    final JLabel accountState=label("",12,false);
    final JButton official=button("Play via Minecraft Launcher",false);
    final Landscape cover=new Landscape();
    volatile Accounts accounts;
    final AccountShelf shelf;
    final Map<Path,Process> running=new HashMap<>();
    int activeTab;
    static String message(Throwable ex){while(ex.getCause()!=null&&(ex instanceof java.util.concurrent.ExecutionException||ex instanceof java.util.concurrent.CompletionException))ex=ex.getCause();return ex.getMessage()==null?ex.toString():ex.getMessage();}
    static JButton action(String name,Action a,Sonder owner){var b=button(name,false);b.addActionListener(v->owner.safe(()->{if(!owner.busy)a.run();}));return b;}
    Properties personal(Engine.Profile p)throws IOException{return Engine.personalSettings(p);}
    void personal(Engine.Profile p,Properties v)throws IOException{Engine.save(p.dir().resolve("personal.properties"),v);}

    interface Action{void run()throws Exception;}
    static JLabel label(String t,int size,boolean bold){var l=new JLabel(t);l.setFont(new Font(size>=22?"Dialog":"SansSerif",bold?Font.BOLD:Font.PLAIN,size));l.setForeground(bold?INK:MUTED);return l;}
    static JButton button(String text,boolean solid){return Look.button(text,solid);}
    static JPanel row(){var p=new JPanel(new FlowLayout(FlowLayout.LEFT,8,5));p.setOpaque(false);return p;}
    void safe(Action a){try{a.run();}catch(Exception ex){JOptionPane.showMessageDialog(this,ex.getMessage(),"Vesper Client",JOptionPane.ERROR_MESSAGE);}}
    Sonder(Engine engine)throws Exception{
        super("Vesper " + ReleaseUpdates.VERSION);e=engine;shelf=new AccountShelf(e.base);accounts=shelf.current();
        setIconImage(new ImageIcon(getClass().getResource("/sonder.png")).getImage());setDefaultCloseOperation(EXIT_ON_CLOSE);setMinimumSize(new Dimension(870,680));
        var body=new JPanel(new BorderLayout());setContentPane(body);
        var mast=new JPanel(new BorderLayout(12,0));mast.setBackground(new Color(22,22,29));
        mast.setBorder(new EmptyBorder(9,20,9,20));
        var brand=label("VESPER",20,true);brand.setIcon(new ImageIcon(getIconImage().getScaledInstance(34,34,Image.SCALE_SMOOTH)));brand.setIconTextGap(10);
        brand.setForeground(INK);mast.setBackground(new Color(15,27,31));
        mast.setBorder(new EmptyBorder(8,17,8,12));mast.add(brand,BorderLayout.WEST);
        mast.add(studio.navigation(),BorderLayout.CENTER);body.add(mast,BorderLayout.NORTH);
        var main=new JPanel(new BorderLayout(0,10));main.setOpaque(false);main.setBorder(new EmptyBorder(20,24,10,24));body.add(main);
        main.add(studio.canvas);tabs.addChangeListener(ev->{if(tabs.getSelectedIndex()>=0)activeTab=tabs.getSelectedIndex();});
        var foot=new JPanel(new BorderLayout(10,0));foot.setOpaque(false);foot.add(status);foot.add(accountState,BorderLayout.EAST);main.add(foot,BorderLayout.SOUTH);
        list.addListSelectionListener(a->{if(!a.getValueIsAdjusting())safe(this::refresh);});settings.addActionListener(a->safe(this::options));

        var profiles=e.profiles();if(profiles.isEmpty()){e.create("Velocity","FPS Boost");e.create("Horizon","FPS + QoL");e.create("Workshop","Custom");profiles=e.profiles();}
        for(var p:profiles)model.addElement(p);var prefs=Engine.read(e.base.resolve("launcher.properties"));
        String last=prefs.getProperty("lastProfile","");int idx=0;
        for(int i=0;i<model.size();i++){var item=model.get(i);if(item.dir().getFileName().toString().equals(last)){idx=i;break;}if(item.kind().equals("FPS Boost"))idx=i;}
        list.setSelectedIndex(idx);getRootPane().setDefaultButton(play);
        setSize(1160,760);setLocationRelativeTo(null);
        SwingUtilities.invokeLater(()->ReleaseUpdates.check(this));

    }
    Engine.Profile profile(){return list.getSelectedValue();}
    void refresh()throws Exception{
        var p=profile();if(p==null)return;
        Path settingsFile=e.base.resolve("launcher.properties");var prefs=Engine.read(settingsFile);
        if(!p.dir().getFileName().toString().equals(prefs.getProperty("lastProfile"))){prefs.setProperty("lastProfile",p.dir().getFileName().toString());Engine.save(settingsFile,prefs);}
        refreshAccount();var prefsProfile=personal(p);
        studio.load(prefs);Color accent=studio.colour;
        side.accent=accent;side.style=prefsProfile.getProperty("district",Neighborhood.styles[studio.theme]);cover.scene=studio.theme;side.repaint();cover.showProfile(p,prefsProfile,accent);play.setBackground(Color.decode(prefsProfile.getProperty("accent",String.format("#%06x",accent.getRGB()&0xffffff))));play.setForeground(play.getBackground().getRed()*.299+play.getBackground().getGreen()*.587+play.getBackground().getBlue()*.114>145?BG:Color.WHITE);list.setSelectionBackground(accent.darker().darker());
        Color shade=new Color(17+accent.getRed()/20,17+accent.getGreen()/20,19+accent.getBlue()/20);
        UIManager.put("Panel.background",side.tint(7));UIManager.put("TabbedPane.background",side.tint(10));UIManager.put("Component.focusColor",accent);UIManager.put("ScrollPane.background",side.tint(7));getContentPane().setBackground(new Color(13,23,26));list.setBackground(shade);tabs.putClientProperty("JTabbedPane.showTabSeparators",false);tabs.putClientProperty("JTabbedPane.tabType","card");
        if(studio.page.equals("Add-ons")||studio.page.equals("Settings")){
        int index=activeTab;tabs.removeAll();
        tabs.setTabPlacement(JTabbedPane.LEFT);tabs.addTab("Mods",contentTab(p,false));tabs.addTab("Resource Packs",contentTab(p,true));
        contents.removeAll();contents.setLayout(new BoxLayout(contents,BoxLayout.Y_AXIS));contents.setAlignmentX(Component.LEFT_ALIGNMENT);
        if(!p.kind().equals("Custom")){var groups=e.extras(p);quickSwitch(p,"fullbright","Fullbright","Press G in game to switch brightness.",groups);quickSwitch(p,"reload","Hide Mojang loading screen","Show resource-loading progress.",groups);}
        var appearance=row();appearance.setAlignmentX(Component.LEFT_ALIGNMENT);
        appearance.add(action("Customize this profile",this::customize,this));if(!p.kind().equals("Custom"))appearance.add(action("Preset options",this::options,this));
        appearance.add(action("Open folder",()->Desktop.getDesktop().open(p.game().toFile()),this));
        var memory=row();memory.setAlignmentX(Component.LEFT_ALIGNMENT);var ram=new JSpinner(new SpinnerNumberModel(Integer.parseInt(prefsProfile.getProperty("ram","4")),2,16,1));memory.add(label("Direct launch memory (GB)",14,true));memory.add(ram);ram.addChangeListener(ev->safe(()->{var values=personal(p);values.setProperty("ram",ram.getValue().toString());personal(p,values);}));
        var extras=row();extras.setAlignmentX(Component.LEFT_ALIGNMENT);extras.add(action("Play Minecraft demo",()->runGame(p,true),this));
        var restore=row();restore.setAlignmentX(Component.LEFT_ALIGNMENT);restore.add(action("Restore removed preset entries",()->{if(!editable(p))return;var state=Engine.read(Content.stateFile(p));Set<String> keys=new HashSet<>();keys.add("mods/Sonder-HUD.jar");for(var m:e.chosen(p,e.extras(p)))keys.add("mods/"+m.file());for(String k:keys)if("removed".equals(state.getProperty(k)))state.remove(k);Engine.save(Content.stateFile(p),state);refresh();},this));
        var groupsView=new JPanel();groupsView.setOpaque(false);groupsView.setLayout(new BoxLayout(groupsView,BoxLayout.Y_AXIS));
        for(var group:List.of("Game","Appearance","Maintenance")){
            var block=studio.card();var fields=new JPanel();fields.setOpaque(false);fields.setLayout(new BoxLayout(fields,BoxLayout.Y_AXIS));
            block.add(label(group,18,true),BorderLayout.WEST);
            if(group.equals("Game")){fields.add(memory);fields.add(contents);fields.add(extras);}
            if(group.equals("Appearance"))fields.add(appearance);
            if(group.equals("Maintenance"))fields.add(restore);
            block.add(fields);block.setMaximumSize(new Dimension(Integer.MAX_VALUE,block.getPreferredSize().height));groupsView.add(block);groupsView.add(Box.createVerticalStrut(10));
        }
        studio.settings=Look.scroll(groupsView);tabs.addTab("Settings",studio.settings);tabs.setSelectedIndex(Math.clamp(index,0,2));
        }
        for(var listener:official.getActionListeners())official.removeActionListener(listener);
        official.addActionListener(a->safe(()->openOfficial(p)));
        official.setToolTipText("Open "+p.name()+" in the official Minecraft Launcher");
        for(var listener:play.getActionListeners())play.removeActionListener(listener);
        play.addActionListener(a->safe(()->runGame(p,false)));
        play.setText("Launch "+p.name());
        play.setToolTipText("Launch "+p.name()+" directly with Vesper");
        play.getAccessibleContext().setAccessibleDescription(play.getToolTipText());
        studio.show(p);tabs.revalidate();tabs.repaint();
    }
    JPanel contentTab(Engine.Profile p,boolean packs)throws Exception{
        var panel=new JPanel(new BorderLayout(0,8));if(!packs&&p.loader().equals("Vanilla")){panel.add(label("Vanilla profiles run without Fabric mods.",15,false));return panel;}panel.setBorder(new EmptyBorder(10,9,7,9));
        var bar=row();bar.add(action("Browse Modrinth",()->{if(editable(p))new Catalog(e,p,packs).open(this);},this));
        if(packs)bar.add(action("Pack collection",()->{if(!editable(p))return;Packs.open(this,e,p);refresh();},this));
        bar.add(action("Import…",()->importFiles(packs),this));var search=new JTextField(16);search.putClientProperty("JTextField.placeholderText",packs?"Find a pack…":"Find a mod…");search.getAccessibleContext().setAccessibleName("Filter installed content");
        var toolbar=studio.sheet();toolbar.add(bar);toolbar.add(search,BorderLayout.EAST);panel.add(toolbar,BorderLayout.NORTH);
        var rows=new JPanel();rows.setLayout(new BoxLayout(rows,BoxLayout.Y_AXIS));rows.setBackground(side.tint(6));
        var items=Content.list(e,p,packs);
        var sources=Engine.read(Content.stateFile(p));var byFile=new HashMap<String,String>();
        if(!packs)for(var m:e.chosen(p,e.extras(p)))byFile.put(m.file(),"mr/"+m.id());
        for(var item:items){
            var line=Look.card(new BorderLayout(10,0));line.setBackground(side.tint(14));line.setBorder(new EmptyBorder(4,12,4,8));line.setMaximumSize(new Dimension(Integer.MAX_VALUE,62));
            var text=new JPanel(new GridLayout(2,1));text.setOpaque(false);
            String name=item.name().equals("Sonder-HUD.jar")?"Vesper HUD":packs?item.name().replaceFirst("(?i)\\.zip$","").replace('_',' '):displayMod(item.name());
            text.add(label(name,14,true));text.add(label((item.on()?(item.available()?"Enabled":"On next launch"):"Disabled")+(item.group().equals("shaderpacks")?"  ·  Shader":""),11,false));var tile=ContentArt.tile(p,item,sources.getProperty("source/"+item.name(),byFile.get(item.name())),side.accent);line.add(tile,BorderLayout.WEST);line.add(text);line.setToolTipText(item.name());
            var controls=row();var toggle=new JToggleButton(){protected void paintComponent(Graphics raw){var g=(Graphics2D)raw.create();g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);g.setColor(isSelected()?side.accent:new Color(92,86,99));g.fillRoundRect(3,6,33,17,17,17);g.setColor(isSelected()?side.tint(6):Color.WHITE);g.fillOval(isSelected()?21:6,9,11,11);if(hasFocus()){g.setColor(Color.WHITE);g.drawRoundRect(1,4,37,21,19,19);}g.dispose();}};
            toggle.setSelected(item.on());toggle.setPreferredSize(new Dimension(40,29));toggle.setToolTipText((item.on()?"Disable ":"Enable ")+name);toggle.getAccessibleContext().setAccessibleName(toggle.getToolTipText());
            toggle.addActionListener(a->{toggle.setSelected(item.on());safe(()->{if(editable(p)){Content.change(p,item,!item.on(),false);refresh();}});});
            var remove=new JButton("×");remove.setToolTipText("Remove "+name);remove.getAccessibleContext().setAccessibleName(remove.getToolTipText());remove.addActionListener(a->safe(()->{if(editable(p)&&JOptionPane.showConfirmDialog(this,"Remove "+name+" from "+p.name()+"?","Remove from profile",JOptionPane.OK_CANCEL_OPTION)==0){Content.change(p,item,false,true);refresh();}}));
            String source=sources.getProperty("source/"+item.name(),byFile.get(item.name()));
            if(source!=null&&source.startsWith("mr/")){
                String id=source.substring(3);
                var switchVersion=action("Swap version",()->{if(editable(p))new Catalog(e,p,packs).open(this,new Catalog.Hit(id,name,"Installed file: "+item.name(),"https://modrinth.com/"+(packs?"resourcepack/":"mod/")+id));},this);
                controls.add(switchVersion);
            }
            controls.add(toggle);controls.add(remove);line.add(controls,BorderLayout.EAST);rows.add(line);rows.add(Box.createVerticalStrut(4));}
        if(items.isEmpty())rows.add(label(packs?"Choose or import a resource pack.":"Choose or import a Fabric mod.",14,false));
        search.getDocument().addDocumentListener(new javax.swing.event.DocumentListener(){
            void filter(){String query=search.getText().strip().toLowerCase(Locale.ROOT);
                for(var child:rows.getComponents())if(child instanceof JPanel row)row.setVisible(row.getToolTipText().toLowerCase(Locale.ROOT).contains(query));
                rows.revalidate();rows.repaint();}
            public void insertUpdate(javax.swing.event.DocumentEvent event){filter();}
            public void removeUpdate(javax.swing.event.DocumentEvent event){filter();}
            public void changedUpdate(javax.swing.event.DocumentEvent event){filter();}
        });
        var sc=Look.scroll(rows);sc.getVerticalScrollBar().setUnitIncrement(28);panel.add(sc);
        var drop=button(packs?"Drop pack ZIPs here or choose files":"Drop mod JARs here or choose files",false);
        drop.addActionListener(event->safe(()->importFiles(packs)));
        var handler=new TransferHandler(){
            public boolean canImport(TransferSupport transfer){return !busy&&transfer.isDataFlavorSupported(java.awt.datatransfer.DataFlavor.javaFileListFlavor);}
            public boolean importData(TransferSupport transfer){
                if(!canImport(transfer)||!editable(p))return false;
                try{var raw=(List<?>)transfer.getTransferable().getTransferData(java.awt.datatransfer.DataFlavor.javaFileListFlavor);
                    var paths=raw.stream().filter(File.class::isInstance).map(File.class::cast).map(File::toPath).toList();
                    importInto(p,packs,paths);return true;
                }catch(Exception ex){status.setText(message(ex));return false;}
            }
        };
        drop.setTransferHandler(handler);rows.setTransferHandler(handler);sc.setTransferHandler(handler);panel.setTransferHandler(handler);
        panel.add(drop,BorderLayout.SOUTH);return panel;
    }
    boolean editable(Engine.Profile p){if(busy||running.containsKey(p.dir())&&running.get(p.dir()).isAlive()){status.setText("Close the game or wait for the current action before editing this profile.");return false;}return true;}
    static String displayMod(String filename){
        String name=filename.split("(?i)(?:[-_]fabric|[-_+](?=v?[0-9]))",2)[0].replace('_',' ').replace('-',' ');
        return name.isBlank()?filename:Character.toUpperCase(name.charAt(0))+name.substring(1);
    }
    void importFiles(boolean packs)throws Exception{
        var p=profile();if(!editable(p))return;var choose=new JFileChooser();choose.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
        choose.setDialogTitle("Choose a folder or file to see the import checklist");choose.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(packs?"Pack ZIPs":"Fabric JARs",packs?"zip":"jar"));
        if(choose.showOpenDialog(this)!=JFileChooser.APPROVE_OPTION)return;Path first=choose.getSelectedFile().toPath(),dir=Files.isDirectory(first)?first:first.getParent();
        var paths=packs?Imports.zips(dir):e.importSources(dir);
        var files=Imports.select(this,paths,first,"Import into "+p.name());importInto(p,packs,files);
    }
    void importInto(Engine.Profile p,boolean packs,List<Path> files){
        if(files.isEmpty()||!editable(p))return;
        work("Importing into "+p.name()+"…",updates->{
            var errors=new ArrayList<String>();int added=0;
            for(Path f:files)try{
                if(!Files.isRegularFile(f)||!f.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(packs?".zip":".jar"))throw new IOException("Expected "+(packs?"a pack ZIP":"a Fabric JAR"));
                updates.accept("Importing "+f.getFileName());if(packs)Packs.add(e,p,f);else e.importMod(p,f);added++;
            }catch(Exception ex){errors.add(f.getFileName()+": "+message(ex));}
            String result="Imported "+added+" of "+files.size()+" into "+p.name()+".";
            if(!errors.isEmpty())throw new IOException(result+"\n"+String.join("\n",errors));return result;
        });
    }

    void customize()throws Exception{
        var p=profile();var pref=personal(p);var story=new JTextField(pref.getProperty("story",""),28);
        var alias=new JTextField(pref.getProperty("nickname",p.name()),22);
        var accent=button("Choose accent",false);accent.setBackground(Color.decode(pref.getProperty("accent","#ba91d5")));
        accent.addActionListener(v->{var c=JColorChooser.showDialog(this,"Profile accent",accent.getBackground());if(c!=null)accent.setBackground(c);});
        Path[] iconFile={null};var iconButton=button("Choose profile icon…",false);
        iconButton.addActionListener(event->{var fc=new JFileChooser();fc.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("PNG or JPEG","png","jpg","jpeg"));if(fc.showOpenDialog(this)==JFileChooser.APPROVE_OPTION){iconFile[0]=fc.getSelectedFile().toPath();iconButton.setText(iconFile[0].getFileName().toString());}});
        var banner=button("Choose a screenshot…",false);Path[] chosen={null};var original=new JCheckBox("Use theme artwork",Boolean.parseBoolean(pref.getProperty("useArt",Boolean.toString(!Files.exists(p.dir().resolve("cover.jpg"))))));
        banner.addActionListener(v->{var fc=new JFileChooser();fc.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("PNG or JPEG","png","jpg","jpeg"));if(fc.showOpenDialog(this)==JFileChooser.APPROVE_OPTION){chosen[0]=fc.getSelectedFile().toPath();original.setSelected(false);banner.setText(chosen[0].getFileName().toString());}});
        var ram=new JSpinner(new SpinnerNumberModel(Integer.parseInt(pref.getProperty("ram","4")),2,16,1));
        if(JOptionPane.showConfirmDialog(this,new Object[]{"Cover title",alias,"A memory, a goal, or the name of your world",story,accent,iconButton,banner,original,"Game memory (GB)",ram},"Customize "+p.name(),JOptionPane.OK_CANCEL_OPTION)!=0)return;
        if(iconFile[0]!=null)Landscape.copyCover(iconFile[0],p.dir().resolve("profile-icon.jpg"));
        if(chosen[0]!=null&&!original.isSelected())Landscape.copyCover(chosen[0],p.dir().resolve("cover.jpg"));
        pref.setProperty("useArt",Boolean.toString(original.isSelected()));pref.setProperty("nickname",alias.getText().strip());pref.setProperty("story",story.getText().strip());
        pref.setProperty("accent",String.format("#%06x",accent.getBackground().getRGB()&0xffffff));pref.setProperty("ram",ram.getValue().toString());personal(p,pref);refresh();
    }
    void quickSwitch(Engine.Profile p,String key,String name,String hint,Set<String> groups){
        var panel=new JPanel(new BorderLayout(12,0));panel.setBackground(Look.surface);panel.setBorder(new EmptyBorder(8,15,8,14));
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE,65));panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        var words=new JPanel(new GridLayout(2,1));words.setOpaque(false);words.add(label(name,15,true));words.add(label(hint,12,false));panel.add(words);
        var check=new JCheckBox("Enabled",groups.contains(key));check.setOpaque(false);check.getAccessibleContext().setAccessibleName(name);
        check.addActionListener(a->safe(()->{if(busy){check.setSelected(!check.isSelected());return;}var selected=e.extras(p);
            if(check.isSelected())selected.add(key);else selected.remove(key);
            e.extras(p,selected);refresh();status.setText("Saved for next launch.");}));panel.add(check,BorderLayout.EAST);
        contents.add(panel);contents.add(Box.createVerticalStrut(6));
    }
    void importPack(){
        if(busy)return;
        var picker=new JFileChooser();picker.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Modrinth modpacks (*.mrpack)","mrpack"));
        if(picker.showOpenDialog(this)!=JFileChooser.APPROVE_OPTION)return;
        Path archive=picker.getSelectedFile().toPath();
        work("Importing modpack…",updates->{
            var imported=Modpack.install(e,archive,updates);
            SwingUtilities.invokeLater(()->safe(()->{model.addElement(imported);studio.openProfile(imported);}));
            return "Imported "+imported.name()+".";
        });
    }
    void create()throws Exception{
        var name=new JTextField(22);var type=new JComboBox<>(new String[]{"Competitive PvP","FPS Boost","FPS + QoL","Custom"});
        var next=new JComboBox<>(new String[]{"Open profile","Browse Modrinth mods","Browse Modrinth resource packs"});
        if(JOptionPane.showConfirmDialog(this,new Object[]{"Profile name",name,"Preset",type,"After creating",next},"New profile",JOptionPane.OK_CANCEL_OPTION)!=0)return;
        var p=e.create(name.getText(),(String)type.getSelectedItem());model.addElement(p);studio.page="Add-ons";list.setSelectedValue(p,true);int choice=next.getSelectedIndex();tabs.setSelectedIndex(choice==2?1:0);if(choice>0)new Catalog(e,p,choice==2).open(this);
    }
    void options()throws Exception{
        var p=profile();if(p==null)return;
        if(p.kind().equals("Custom")){var chooser=new JFileChooser();chooser.setMultiSelectionEnabled(true);
            chooser.setDialogTitle("Select Fabric JARs, a mods folder, or a launcher profile folder");
            chooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
            chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Fabric mods (*.jar) or a profile folder","jar"));
            if(chooser.showOpenDialog(this)==JFileChooser.APPROVE_OPTION){List<String> errors=new ArrayList<>();int imported=0;
                for(File f:chooser.getSelectedFiles())try{for(Path src:e.importSources(f.toPath()))try{e.importMod(p,src);imported++;}catch(Exception ex){errors.add(ex.getMessage());}}catch(Exception ex){errors.add(ex.getMessage());}
                status.setText("Imported "+imported+" mod JARs.");refresh();if(!errors.isEmpty())throw new IOException(String.join("\n",errors));}return;}
        var groups=e.extras(p);var pane=new JPanel(new GridLayout(0,1));pane.add(label("Optional features for this profile",17,true));
        var toggles=new LinkedHashMap<String,JCheckBox>();
        String[][] options={{"zoom","Zoomify • adjustable in-game zoom"},{"oneclient","OneClient performance mods • ModernFix, Particle Core, ZFastNoise"},{"terrain","Distant terrain • Voxy + LOD Server Support"},{"maps","Xaero’s world map and minimap (SMP)"},{"voxy-extra","Voxy Extra settings (also enables distant terrain)"},{"iris","Iris shader support"},{"essential","Essential social features"},{"krypton","Krypton networking"},{"fast-ip-ping","Fast IP Ping"},{"fadeless","Fadeless transitions"},{"c2me-fabric","C2ME parallel chunk processing"}};
        for(var item:options){if(e.catalog.stream().noneMatch(m->m.group().equals(item[0])))continue;var cb=new JCheckBox(item[1],groups.contains(item[0]));toggles.put(item[0],cb);pane.add(cb);}
        pane.add(label("Maps and far players depend on server rules and support.",12,false));
        if(JOptionPane.showConfirmDialog(this,pane,"Profile options",JOptionPane.OK_CANCEL_OPTION)!=0)return;
        var selected=new HashSet<String>(groups);selected.removeAll(toggles.keySet());toggles.forEach((k,v)->{if(v.isSelected())selected.add(k);});
        if(selected.contains("voxy-extra"))selected.add("terrain");e.extras(p,selected);refresh();status.setText("Options saved for the next launch.");
    }
    void accountSetup()throws Exception{
        var file=e.base.resolve("launcher.properties");var pref=Engine.read(file);var id=new JTextField(accounts.client(),36);
        var signout=new JCheckBox("Disconnect the saved Minecraft account");
        if(JOptionPane.showConfirmDialog(this,new Object[]{"Vesper’s public Microsoft Application (client) ID",id,"Desktop redirect URI: http://localhost",signout},"Account setup",JOptionPane.OK_CANCEL_OPTION)!=0)return;
        String value=id.getText().strip();if(!value.isEmpty())UUID.fromString(value);pref.setProperty("clientId",value);Engine.save(file,pref);
        if(signout.isSelected()){Files.deleteIfExists(accounts.vault());accounts.clearProfile();status.setText("Account disconnected.");}
    }
    Properties accountPrefs()throws IOException{return Engine.read(e.base.resolve("account-ui.properties"));}
    void accountSetting(String key,String value)throws IOException{var props=accountPrefs();props.setProperty(key,value);Engine.save(e.base.resolve("account-ui.properties"),props);}
    boolean accountsProfileReady(){try{return accounts.profile().containsKey("name");}catch(IOException ex){return false;}}
    void refreshAccount()throws IOException{
        String name=accounts.display();
        accountState.setText(!Files.isRegularFile(accounts.vault())?"All profiles · Connect account":
            accounts.profile().containsKey("name")?"Minecraft connected · "+name:"Microsoft connected · Java profile pending");
        accountState.setToolTipText(accountState.getText());
    }
    void openOfficial(Engine.Profile p)throws Exception{
        if(!editable(p))return;work("Finding Minecraft Launcher…",updates->OfficialLauncher.handoff(e,p,updates));
    }
    void account()throws Exception{
        var menu=new JPopupMenu();
        for(var saved:shelf.saved()){
            var entry=menu.add(saved.display()+(accounts.base.equals(saved.base)?"   ✓":""));
            entry.addActionListener(ev->safe(()->{if(busy)return; shelf.activate(saved);accounts=saved;refresh();}));
        }
        menu.addSeparator();
        menu.add("Add Microsoft account…").addActionListener(ev->safe(()->{
            var id=accounts.client();if(id.isBlank()){accountSetup();id=accounts.client();}
            if(id.isBlank())return;
            final String client=id;
            work("Opening Microsoft account picker…",updates->{
                var fresh=shelf.create();
                try{var session=fresh.connect(client);shelf.activate(fresh);accounts=fresh;return "Playing as "+session.name();}
                finally{if(accounts!=fresh)shelf.discard(fresh);}
            });
        }));
        menu.add("Account settings…").addActionListener(ev->safe(()->{accountSetup();refresh();}));
        Component anchor=KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
        if(anchor==null||!anchor.isShowing())anchor=getRootPane();
        menu.show(anchor,0,anchor.getHeight());
    }
    void previewPlayer()throws Exception{
        String name=JOptionPane.showInputDialog(this,"Java username for your public skin preview",Engine.read(e.base.resolve("preview-player.properties")).getProperty("name",""));
        if(name!=null)work("Loading Minecraft skin…",updates->{accounts.preview(name.strip());return "Public profile preview · "+name.strip();});
    }
    void connectAccount()throws Exception{
        if(Files.isRegularFile(accounts.vault())){
            work("Checking Minecraft access…",updates->"Minecraft connected as "+accounts.refresh().name()+".");return;
        }
        if(accounts.client().isBlank()){accountSetup();if(accounts.client().isBlank())return;}
        String appId=accounts.client();work("Opening Microsoft sign-in…",updates->{var session=accounts.connect(appId);accountSetting("player",session.name());return "Connected as "+session.name()+" for all profiles.";});
    }
    interface Job{String run(java.util.function.Consumer<String> updates)throws Exception;}
    void work(String start,Job job){work(start,job,null);}
    void work(String start,Job job,Engine.Profile launchTarget){
        if(busy)return;status.setToolTipText(null);busy=true;official.setEnabled(false);play.setEnabled(false);list.setEnabled(false);status.setText(start);
        new SwingWorker<String,String>(){
            protected String doInBackground()throws Exception{return job.run(s->publish(s));}
            protected void process(List<String> messages){status.setText(messages.getLast());}
            protected void done(){
                busy=false;official.setEnabled(true);play.setEnabled(true);list.setEnabled(true);
                try{status.setText(get());refresh();}
                catch(Exception ex){
                    safe(Sonder.this::refresh);
                    if(ex.getCause() instanceof Net.MinecraftAccessDenied denied && Files.isRegularFile(accounts.vault())){
                        status.setText("Sign-in saved. To play now, use Play via Minecraft Launcher.");
                        status.setToolTipText(denied.getMessage());
                        if(launchTarget!=null){
                            int next=JOptionPane.showOptionDialog(Sonder.this,"Minecraft declined direct access for Vesper. Open "+launchTarget.name()+" in the official launcher?","Minecraft sign-in",JOptionPane.DEFAULT_OPTION,JOptionPane.PLAIN_MESSAGE,null,new String[]{"Open Minecraft Launcher","Stay in Vesper"},"Open Minecraft Launcher");
                            if(next==0)safe(()->openOfficial(launchTarget));
                        }
                        return;
                    }
                    status.setText("Action stopped.");JOptionPane.showMessageDialog(Sonder.this,message(ex),"Vesper Launcher",JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }
    void runGame(Engine.Profile p,boolean demo)throws Exception{
        if(p==null||busy)return;
        if(running.containsKey(p.dir())&&running.get(p.dir()).isAlive()){status.setText("Minecraft is already running for this profile.");return;}
        if(!demo&&!Files.exists(accounts.vault())&&accounts.client().isBlank()){accountSetup();if(accounts.client().isBlank())return;}
        int ram=Integer.parseInt(personal(p).getProperty("ram","4"));
        work(demo?"Preparing demo…":"Connecting your Minecraft account…",updates->{
            var user=demo?Accounts.Session.trial():Files.exists(accounts.vault())?accounts.forPlay():accounts.connect(accounts.client());
            updates.accept("Preparing "+p.name()+"…");if(!demo)accountSetting("player",user.name());e.prepare(p,updates);Compatibility.check(e,p,updates);var proc=new Game(e.base,p).launch(p,user,ram,updates);
            if(proc.waitFor(2,java.util.concurrent.TimeUnit.SECONDS)&&proc.exitValue()!=0)throw new IOException("Minecraft stopped. Open the profile folder → logs → sonder-launch.log.");
            if(!demo)PlayHistory.started(p);
            SwingUtilities.invokeLater(()->running.put(p.dir(),proc));
            return "Minecraft launched · "+p.name();
        },demo?null:p);
    }
    static class Landscape extends JPanel{
        final Image art=Look.scenes[0];
        int scene;Image image=art;String title="Vesper",note="";Color accent=PURPLE;Path last;long modified;
        Landscape(){setPreferredSize(new Dimension(550,204));setOpaque(false);}
        void showProfile(Engine.Profile p,Properties pref,Color colour){
            title=pref.getProperty("nickname",p.name());if(title.isBlank())title=p.name();
            note=pref.getProperty("story","");if(note.isBlank())note="";
            accent=colour;Path cover=p.dir().resolve("cover.jpg");long m=cover.toFile().lastModified();
            if(!Boolean.parseBoolean(pref.getProperty("useArt","false"))&&m>0){if(!cover.equals(last)||modified!=m){try{image=javax.imageio.ImageIO.read(cover.toFile());}catch(Exception ex){image=art;}last=cover;modified=m;}}
            else{image=art;last=null;}repaint();
        }
        static void copyCover(Path from,Path to)throws IOException{
            if(Files.size(from)>32*1024*1024)throw new IOException("Choose an image smaller than 32 MB.");
            try(var stream=javax.imageio.ImageIO.createImageInputStream(from.toFile())){var readers=javax.imageio.ImageIO.getImageReaders(stream);if(!readers.hasNext())throw new IOException("Choose a PNG or JPEG.");
                var reader=readers.next();try{reader.setInput(stream);int w=reader.getWidth(0),h=reader.getHeight(0);var param=reader.getDefaultReadParam();int sample=Math.max(1,Math.max(w,h)/2000);param.setSourceSubsampling(sample,sample,0,0);
                    var img=reader.read(0,param);var rgb=new java.awt.image.BufferedImage(img.getWidth(),img.getHeight(),java.awt.image.BufferedImage.TYPE_INT_RGB);var g=rgb.createGraphics();g.drawImage(img,0,0,null);g.dispose();
                    Path tmp=Files.createTempFile(to.getParent(),"cover",".jpg");try{javax.imageio.ImageIO.write(rgb,"jpg",tmp.toFile());Engine.commit(tmp,to);}finally{Files.deleteIfExists(tmp);}
                }finally{reader.dispose();}}
        }
        protected void paintComponent(Graphics raw){var g=(Graphics2D)raw.create();int w=getWidth(),h=getHeight();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.clip(new RoundRectangle2D.Double(0,0,w,h,18,18));if(image==art&&scene>0){var city=new Studio.Scene(scene,accent);city.setSize(w,h);city.paintComponent(g);}else if(image!=null){double s=Math.max(w/(double)image.getWidth(null),h/(double)image.getHeight(null));int iw=(int)(image.getWidth(null)*s),ih=(int)(image.getHeight(null)*s);g.drawImage(image,(w-iw)/2,(h-ih)/2,iw,ih,this);}
            g.setPaint(new GradientPaint(0,0,new Color(15,13,25,64),0,h,new Color(15,12,25,238)));g.fillRect(0,0,w,h);g.dispose();}
        static String fit(Graphics2D g,String s,int max){if(g.getFontMetrics().stringWidth(s)<=max)return s;while(!s.isEmpty()&&g.getFontMetrics().stringWidth(s+"…")>max)s=s.substring(0,s.length()-1);return s+"…";}
    }
    public static void main(String[] args)throws Exception{
        com.formdev.flatlaf.FlatDarkLaf.setup();
        for(String key:List.of("ComboBox.background","Spinner.background","TextArea.background","TextField.background","Viewport.background","TabbedPane.contentAreaColor"))UIManager.put(key,Look.surface);
        UIManager.put("TextArea.foreground",INK);UIManager.put("TabbedPane.selectedBackground",Look.surface);
        UIManager.put("TabbedPane.underlineColor",new Color(115,229,194));UIManager.put("TabbedPane.tabHeight",40);
        UIManager.put("ScrollBar.width",9);UIManager.put("ScrollBar.thumbArc",12);
        UIManager.put("Component.arc",5);UIManager.put("Button.arc",5);
        UIManager.put("Component.focusColor",new Color(115,229,194));UIManager.put("Panel.background",BG);
        UIManager.put("OptionPane.background",BG);UIManager.put("Label.foreground",INK);
        Path home=Path.of(System.getProperty("user.home"),".sonder-client");Files.createDirectories(home);
        var ch=FileChannel.open(home.resolve("launcher.lock"),StandardOpenOption.CREATE,StandardOpenOption.WRITE);var lock=ch.tryLock();
        if(lock==null){JOptionPane.showMessageDialog(null,"Vesper Client is already open.");ch.close();return;}
        Runtime.getRuntime().addShutdownHook(new Thread(()->{try{lock.release();ch.close();}catch(IOException ignored){}}));
        SwingUtilities.invokeLater(()->{try{new Sonder(new Engine(home)).setVisible(true);}catch(Exception ex){JOptionPane.showMessageDialog(null,ex.toString(),"Vesper Client",JOptionPane.ERROR_MESSAGE);System.exit(1);}});
    }
}
