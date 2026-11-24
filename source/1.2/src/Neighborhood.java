import javax.swing.*;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.util.List;

final class Neighborhood extends JPanel {
    static final String[] styles={"Lantern street","Moonlit harbor","Observatory"};
    Color accent=Sonder.PURPLE;
    String style=styles[0];
    Neighborhood(){super(new BorderLayout(0,16));setOpaque(false);setBorder(BorderFactory.createEmptyBorder(19,12,12,12));}
    Color tint(int level){return new Color(18+accent.getRed()*level/100,31+accent.getGreen()*level/100,36+accent.getBlue()*level/100);}
    protected void paintComponent(Graphics raw){
        var g=Look.brush(raw);int w=getWidth(),h=getHeight();Color rail=tint(7);
        g.clip(new RoundRectangle2D.Double(0,0,w,h,20,20));g.setColor(rail);g.fillRect(0,0,w,h);
        int height=Math.min(170,h/3);g.translate(0,h-height);
        int scene=Math.max(0,List.of(styles).indexOf(style));Look.crop(g,Look.scenes[scene],w,height);
        g.setPaint(new GradientPaint(0,0,rail,0,height,new Color(17,13,24,210)));g.fillRect(0,0,w,height);g.dispose();
    }
}
