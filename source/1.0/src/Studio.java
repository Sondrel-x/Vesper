import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.geom.*;
import java.nio.file.*;
import java.util.*;
import java.util.List;

final class Studio {
    final Sonder app;
    final JPanel canvas=new JPanel(new BorderLayout(0,14));
    final Map<String,JButton> nav=new LinkedHashMap<>();
    final String[] titles={"Tide","Solstice","Ion"};
    final Color[] accents={new Color(115,229,194),new Color(255,180,86),new Color(145,176,255)};
    final String[] lines={"Sea glass","Burnished amber","Electric blue"};
    int theme,settingsIndex;
    Color colour=accents[0];
    final PlayHistory history=new PlayHistory();
    String page="Play";
    JPanel browser;
    JScrollPane settings;
    Studio(Sonder app){this.app=app;canvas.setOpaque(false);}
    void load(Properties prefs){theme=Math.max(0,List.of(titles).indexOf(prefs.getProperty("studioTheme",titles[0])));colour=accents[theme];}
    JPanel navigation(){
        var holder=sheet();var rail=new JPanel(new GridLayout(1,0,3,0));rail.setOpaque(false);
        var names=Map.of("Play","Home","Profiles","Profiles","Add-ons","Content","Player","Account","Performance","Performance","Themes","Themes","Settings","Settings");
        for(String tab:List.of("Play","Profiles","Add-ons","Player","Performance","Themes","Settings")){
            var button=Sonder.action(names.get(tab),()->{page=tab;if(tab.equals("Add-ons"))app.activeTab=0;app.refresh();},app);
            button.putClientProperty("navigation",true);button.setIconTextGap(9);
            button.setBorder(new EmptyBorder(13,2,13,2));button.setHorizontalAlignment(SwingConstants.CENTER);nav.put(tab,button);rail.add(button);
        }
        holder.add(rail);return holder;
    }
    void openProfile(Engine.Profile profile)throws Exception{
        page="Add-ons";app.activeTab=0;
        if(profile.equals(app.profile()))app.refresh();else app.list.setSelectedValue(profile,true);
    }
    JPanel sheet(){var p=new JPanel(new BorderLayout(12,12));p.setOpaque(false);return p;}
    JPanel card(){var p=Look.card(new BorderLayout(8,8));p.setBorder(new EmptyBorder(15,18,15,18));p.setBackground(app.side.tint(14));return p;}
    void show(Engine.Profile p)throws Exception{
        boolean browsing=page.equals("Browse");if(!browsing)browser=null;canvas.removeAll();nav.forEach((n,b)->{b.putClientProperty("selected",(n.equals(page)||(browsing&&n.equals("Add-ons"))));b.setBackground(app.side.tint((n.equals(page)||(browsing&&n.equals("Add-ons")))?23:9));b.setForeground((n.equals(page)||(browsing&&n.equals("Add-ons")))?Color.WHITE:Sonder.MUTED);});
        if(!browsing){
            var header=sheet();header.add(Sonder.label(switch(page){case "Play"->"Play";case "Profiles"->"Your library";case "Add-ons"->p.name();case "Player"->"Java account";case "Themes"->"Appearance";default->page;},31,true));
            var switcher=Sonder.action(p.name()+"  ▾",()->{
                var menu=new JPopupMenu();
                for(int i=0;i<app.model.size();i++){var pr=app.model.get(i);var entry=menu.add(pr.name());entry.addActionListener(ev->app.safe(()->openProfile(pr)));}
                var origin=(Component)((BorderLayout)header.getLayout()).getLayoutComponent(BorderLayout.EAST);menu.show(origin,0,origin.getHeight());
            },app);
            if(page.equals("Add-ons")){var crumb=Sonder.action("‹  Profiles",()->{page="Profiles";app.refresh();},app);header.add(crumb,BorderLayout.WEST);}
            if(page.equals("Play")||page.equals("Profiles"))header.add(Sonder.action("+  New profile",app::create,app),BorderLayout.EAST);else header.add(switcher,BorderLayout.EAST);canvas.add(header,BorderLayout.NORTH);
        }
        switch(page){
            case "Profiles" -> canvas.add(library());
            case "Browse" -> canvas.add(browser);
            case "Play" -> canvas.add(play(p));
            case "Themes" -> canvas.add(themes());
            case "Player" -> canvas.add(player(p));
            case "Add-ons" -> canvas.add(content(p));
            case "Performance" -> canvas.add(performance(p));
            case "Settings" -> canvas.add(settings);
        }
        app.getRootPane().setDefaultButton(page.equals("Play")?app.play:null);
        canvas.revalidate();canvas.repaint();
    }
    JPanel play(Engine.Profile p)throws Exception{
        var body=sheet();
        var tools=Sonder.row();var version=new JComboBox<>(Engine.VERSIONS);var loader=new JComboBox<>(new String[]{"Fabric","Vanilla"});
        version.setSelectedItem(p.version());loader.setSelectedItem(p.loader());tools.add(Sonder.label("Version",13,true));tools.add(version);tools.add(Sonder.label("Loader",13,true));tools.add(loader);
        Runnable select=()->app.safe(()->{
            String v=(String)version.getSelectedItem(),l=(String)loader.getSelectedItem();
            if(v.equals(p.version())&&l.equals(p.loader()))return;
            Engine.Profile other=app.e.profiles().stream().filter(x->x.version().equals(v)&&x.loader().equals(l)&&x.kind().equals("Custom")).findFirst().orElse(null);
            if(other==null){String name="Vesper "+l+" "+v;var names=new HashSet<String>();for(var x:app.e.profiles())names.add(x.name().toLowerCase(Locale.ROOT));String base=name;for(int i=2;names.contains(name.toLowerCase(Locale.ROOT));i++)name=base+" ("+i+")";other=app.e.create(name,"Custom",v,l);app.model.addElement(other);}
            app.list.setSelectedValue(other,true);
        });
        version.addActionListener(a->{if(!app.busy)select.run();});loader.addActionListener(a->{if(!app.busy)select.run();});
        var hero=new SignaturePanel(theme,colour);hero.setBorder(new EmptyBorder(22,24,22,24));
        if(app.cover.image!=app.cover.art)hero.cover=app.cover.image;
        var intro=sheet();intro.add(Sonder.label("ACTIVE PROFILE",12,true),BorderLayout.NORTH);
        intro.add(Sonder.label(app.cover.title,29,true));var meta=Sonder.label(Content.list(app.e,p,false).stream().filter(Content.Item::on).count()+" enabled mods    /    "+Content.list(app.e,p,true).stream().filter(Content.Item::on).count()+" active packs    /    "+app.personal(p).getProperty("ram","4")+" GB RAM",13,false);
        var summary=sheet();String story=app.personal(p).getProperty("story","").strip();if(!story.isEmpty())summary.add(Sonder.label(story,13,false),BorderLayout.NORTH);summary.add(meta);intro.add(summary,BorderLayout.SOUTH);hero.add(intro,BorderLayout.NORTH);
        var launch=sheet();launch.add(tools,BorderLayout.NORTH);var actions=Sonder.row();app.play.setText("Launch Minecraft");app.play.setIcon(Look.icon("Play"));actions.add(app.play);var playActions=sheet();playActions.add(actions,BorderLayout.WEST);app.official.setText("Official launcher");playActions.add(app.official,BorderLayout.EAST);launch.add(playActions,BorderLayout.SOUTH);hero.add(launch,BorderLayout.SOUTH);
        var center=sheet();var recent=history.latest(Collections.list(app.model.elements()));var area=sheet();area.add(Sonder.label("Last played",21,true),BorderLayout.NORTH);
        var last=card();
        if(recent==null){
            last.add(Sonder.label("Your first played profile will appear here.",14,false));
            last.add(Sonder.action("Browse profiles",()->{page="Profiles";app.refresh();},app),BorderLayout.EAST);
        }else{
            var pr=recent.profile();var mark=profileMark(pr,theme);mark.setPreferredSize(new Dimension(56,54));last.add(mark,BorderLayout.WEST);
            var info=sheet();info.add(Sonder.label(pr.name(),18,true));
            var date=java.time.Instant.ofEpochMilli(recent.time()).atZone(java.time.ZoneId.systemDefault());
            info.add(Sonder.label(pr.loader()+"  ·  "+pr.version()+"  ·  "+date.format(java.time.format.DateTimeFormatter.ofPattern("MMM d, HH:mm")),12,false),BorderLayout.SOUTH);last.add(info);
            var buttons=Sonder.row();buttons.add(Sonder.action("Open",()->openProfile(pr),app));buttons.add(Sonder.action("Play again",()->{app.list.setSelectedValue(pr,true);app.runGame(pr,false);},app));last.add(buttons,BorderLayout.EAST);
        }
        var wrap=sheet();wrap.add(last,BorderLayout.NORTH);area.add(wrap);
        var spotlight=identity();
        var top=new JPanel(new BorderLayout(16,0));top.setOpaque(false);
        spotlight.setPreferredSize(new Dimension(260,310));top.add(hero);top.add(spotlight,BorderLayout.EAST);top.setPreferredSize(new Dimension(600,310));
        body.add(top,BorderLayout.NORTH);
        center.add(area,BorderLayout.NORTH);
        var shortcuts=Sonder.row();
        shortcuts.add(Sonder.action("Manage profiles",()->{page="Profiles";app.refresh();},app));
        shortcuts.add(Sonder.action("Browse Modrinth",()->{if(app.editable(p))new Catalog(app.e,p,false).open(app);},app));
        shortcuts.add(Sonder.action("Frame settings",()->{page="Performance";app.refresh();},app));
        center.add(shortcuts,BorderLayout.SOUTH);body.add(center);return body;
    }
    JPanel library(){
        var library=sheet();var filter=new JTextField();filter.putClientProperty("JTextField.placeholderText","Find a profile by name, version, or loader");filter.getAccessibleContext().setAccessibleName("Find profile");
        library.add(filter,BorderLayout.NORTH);
        var rows=new JPanel(new GridLayout(0,3,12,12));rows.setOpaque(false);
        for(int i=0;i<app.model.size();i++){
            var profile=app.model.get(i);
            var tile=profileMark(profile,i%3);tile.setPreferredSize(new Dimension(210,156));
            var open=Sonder.action(profile.name(),()->openProfile(profile),app);
            open.setFont(new Font("Dialog",Font.BOLD,21));open.setToolTipText("Open mods and resource packs");
            open.setHorizontalAlignment(SwingConstants.LEFT);
            tile.setBorder(new EmptyBorder(14,14,14,14));
            tile.add(Sonder.label(profile.loader()+"  /  "+profile.version(),12,true),BorderLayout.NORTH);
            tile.add(open,BorderLayout.CENTER);
            tile.add(Sonder.action("Launch game",()->{app.list.setSelectedValue(profile,true);app.runGame(profile,false);},app),BorderLayout.SOUTH);
            tile.putClientProperty("filter",(profile.name()+" "+profile.loader()+" "+profile.version()).toLowerCase(Locale.ROOT));rows.add(tile);
        }
        var results=sheet();results.add(rows,BorderLayout.NORTH);
        var empty=Sonder.label("No matching profiles.",14,false);empty.setVisible(false);results.add(empty);
        filter.getDocument().addDocumentListener(new javax.swing.event.DocumentListener(){
            void update(){String query=filter.getText().strip().toLowerCase(Locale.ROOT);int hits=0;for(var c:rows.getComponents())if(c instanceof JPanel row){
                boolean match=row.getClientProperty("filter").toString().contains(query);row.setVisible(match);if(match)hits++;
            }empty.setVisible(hits==0);rows.revalidate();rows.repaint();}
            public void insertUpdate(javax.swing.event.DocumentEvent e){update();}public void removeUpdate(javax.swing.event.DocumentEvent e){update();}public void changedUpdate(javax.swing.event.DocumentEvent e){update();}
        });library.add(Look.scroll(results));return library;
    }
    JPanel identity()throws Exception{
        boolean connected=Files.isRegularFile(app.accounts.vault());
        var panel=new SignaturePanel(theme,colour);panel.setBorder(new EmptyBorder(17,14,15,14));panel.add(Sonder.label("JAVA EDITION",12,true),BorderLayout.NORTH);
        var skin=app.accounts.portrait();if(connected&&Files.isRegularFile(skin))panel.add(new SkinPortrait(skin,colour));
        var info=sheet();info.add(Sonder.label(connected?app.accounts.display():"Java account",18,true),BorderLayout.NORTH);
        var button=Sonder.action(connected?"Switch account  ▾":"Connect account",connected||!app.shelf.saved().isEmpty()?app::account:app::connectAccount,app);
        info.add(button,BorderLayout.SOUTH);panel.add(info,BorderLayout.SOUTH);return panel;
    }
    String accountCaption()throws Exception{
        if(!Files.isRegularFile(app.accounts.vault()))return "Minecraft account";
        if(app.accountsProfileReady())return "Java Edition";
        if(app.accounts.linked().containsKey("name"))return "Local Java profile  ·  direct launch pending";
        return "Microsoft connected  ·  Java profile pending";
    }
    SignaturePanel profileMark(Engine.Profile p,int scene){
        var mark=new SignaturePanel(scene,colour);Path icon=p.dir().resolve("profile-icon.jpg");if(Files.isRegularFile(icon))mark.cover=new ImageIcon(icon.toString()).getImage();mark.plainCover=true;return mark;
    }
    JPanel content(Engine.Profile p){
        var body=sheet();var toolbar=new SignaturePanel(theme,colour);if(app.cover.image!=app.cover.art)toolbar.cover=app.cover.image;toolbar.setBorder(new EmptyBorder(14,15,14,15));var profileBadge=profileMark(p,theme);profileBadge.setPreferredSize(new Dimension(46,46));toolbar.add(profileBadge,BorderLayout.WEST);
        toolbar.add(Sonder.label(p.loader()+"   /   Minecraft "+p.version(),14,true));var commands=Sonder.row();
        commands.add(Sonder.action("Customize",app::customize,app));commands.add(Sonder.action("Open folder",()->Desktop.getDesktop().open(p.game().toFile()),app));
        Path log=p.game().resolve("logs/latest.log");if(Files.isRegularFile(log))commands.add(Sonder.action("Game log",()->Desktop.getDesktop().open(log.toFile()),app));
        commands.add(Sonder.action("Play profile",()->app.runGame(p,false),app));toolbar.add(commands,BorderLayout.EAST);
        body.add(toolbar,BorderLayout.NORTH);body.add(app.tabs);return body;
    }
    JPanel themes(){
        var panel=sheet();var cards=new JPanel(new GridLayout(3,1,0,10));cards.setOpaque(false);
        for(int i=0;i<titles.length;i++){
            final int n=i;var preview=new SignaturePanel(i,accents[i]);preview.setBorder(new EmptyBorder(16,12,16,12));
            preview.add(Sonder.label(lines[i],12,true),BorderLayout.NORTH);var block=sheet();block.add(Sonder.label(titles[i],21,true));
            var apply=Sonder.action(theme==i?"Selected":"Apply theme",()->{
                var file=app.e.base.resolve("launcher.properties");var prefs=Engine.read(file);prefs.setProperty("studioTheme",titles[n]);Engine.save(file,prefs);app.refresh();
            },app);apply.setEnabled(theme!=i);block.add(apply,BorderLayout.SOUTH);preview.add(block,BorderLayout.EAST);cards.add(preview);
        }cards.setPreferredSize(new Dimension(560,420));panel.add(cards,BorderLayout.NORTH);return panel;
    }
    JPanel player(Engine.Profile p)throws Exception{
        var panel=sheet();var portrait=identity();portrait.setPreferredSize(new Dimension(320,320));panel.add(portrait,BorderLayout.EAST);
        var editor=card();editor.add(Sonder.label(accountCaption(),17,true),BorderLayout.NORTH);
        var story=new JTextArea(app.accountPrefs().getProperty("bio",""));story.setLineWrap(true);story.setWrapStyleWord(true);story.setMargin(new Insets(12,12,12,12));story.getAccessibleContext().setAccessibleName("Your story");story.putClientProperty("JTextField.placeholderText","Your story");
        var content=sheet();content.add(Sonder.label("Your story",14,true),BorderLayout.NORTH);content.add(Look.scroll(story));editor.add(content);
        editor.add(Sonder.action("Save story",()->{app.accountSetting("bio",story.getText().strip());app.status.setText("Story saved.");},app),BorderLayout.SOUTH);panel.add(editor);return panel;
    }
    JComponent performance(Engine.Profile p)throws Exception{
        var grid=new JPanel(new GridLayout(0,3,8,8));grid.setOpaque(false);var items=Content.list(app.e,p,false);
        var names=Map.of("sodium","Terrain rendering","lithium","Game logic","ferrite-core","Memory use","entityculling","Entity visibility","immediatelyfast","Immediate rendering","dynamic-fps","Background frame rate","krypton","Network processing","c2me-fabric","Chunk loading","moreculling","Block-face rendering");
        for(var m:app.e.catalog){if(!names.containsKey(m.slug()))continue;var item=items.stream().filter(x->x.name().equals(m.file())).findFirst().orElse(null);if(item==null){
                if(!p.kind().equals("Custom")&&m.group().equals("krypton")){
                    var c=card();c.add(Sonder.label("NETWORK PROCESSING",11,true),BorderLayout.NORTH);c.add(Sonder.label("Krypton",19,true));
                    var enable=Sonder.action("Enable Krypton",()->{if(!app.editable(p))return;var extras=app.e.extras(p);extras.add("krypton");app.e.extras(p,extras);Content.imported(p,"mods",m.file());app.refresh();},app);c.add(enable,BorderLayout.SOUTH);grid.add(c);
                }continue;
            }
            var c=card();var category=Sonder.label(names.get(m.slug()).toUpperCase(Locale.ROOT),11,true);category.setForeground(colour);c.add(category,BorderLayout.NORTH);c.add(ContentArt.tile(p,item,"mr/"+m.id(),colour),BorderLayout.WEST);c.add(Sonder.label(Sonder.displayMod(m.file()),19,true));var check=new JCheckBox("Enabled",item.on());check.setOpaque(false);
            check.addActionListener(a->{check.setSelected(item.on());app.safe(()->{if(app.editable(p)){Content.change(p,item,!item.on(),false);app.refresh();}});});c.add(check,BorderLayout.SOUTH);grid.add(c);}
        var display=card();display.add(Sonder.label("Display",17,true),BorderLayout.WEST);
        var sync=new JCheckBox("VSync · reduce screen tearing",FrameSettings.vsync(p));
        sync.setOpaque(false);
        sync.addActionListener(ev->app.safe(()->{
            if(app.editable(p))FrameSettings.vsync(p,sync.isSelected());
            sync.setSelected(FrameSettings.vsync(p));
        }));
        display.add(sync);
        var panel=sheet();panel.add(display,BorderLayout.NORTH);if(grid.getComponentCount()>0)panel.add(grid,BorderLayout.CENTER);else panel.add(Sonder.label("No performance mods are selected in this profile.",15,false),BorderLayout.CENTER);
        var foot=Sonder.row();if(!p.kind().equals("Custom"))foot.add(Sonder.action("Preset options",app::options,app));panel.add(foot,BorderLayout.SOUTH);return Look.scroll(panel);
    }
    static class Scene extends Look.Art {
        Scene(int scene,Color accent){super(scene,145,false);}
    }
}
