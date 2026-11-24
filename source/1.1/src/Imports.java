import javax.swing.*;
import java.awt.*;
import java.nio.file.*;
import java.util.*;
import java.util.List;
final class Imports {
    static List<Path> select(Component owner,List<Path> paths,Path initial,String title){
        var choices=new LinkedHashMap<Path,JCheckBox>();
        var items=new JPanel();items.setLayout(new BoxLayout(items,BoxLayout.Y_AXIS));
        for(Path p:paths){var check=new JCheckBox(p.getFileName().toString(),p.equals(initial));choices.put(p,check);items.add(check);}
        if(paths.isEmpty()){JOptionPane.showMessageDialog(owner,"No matching files in this folder.");return List.of();}
        var panel=new JPanel(new BorderLayout());var pane=new JScrollPane(items);pane.setPreferredSize(new Dimension(465,290));panel.add(pane);
        var bar=Sonder.row();var all=new JButton("Select all");var clear=new JButton("Clear");all.addActionListener(a->choices.values().forEach(c->c.setSelected(true)));clear.addActionListener(a->choices.values().forEach(c->c.setSelected(false)));bar.add(all);bar.add(clear);panel.add(bar,BorderLayout.NORTH);
        int result=JOptionPane.showOptionDialog(owner,panel,title,0,JOptionPane.PLAIN_MESSAGE,null,new String[]{"Import selected","Import all","Cancel"},"Import selected");
        if(result==1)return paths;if(result!=0)return List.of();return paths.stream().filter(f->choices.get(f).isSelected()).toList();
    }
    static List<Path> zips(Path dir)throws Exception{try(var s=Files.list(dir)){return s.filter(Files::isRegularFile).filter(f->f.toString().toLowerCase(Locale.ROOT).endsWith(".zip")).sorted().toList();}}
}
