import javax.swing.*;
import java.awt.*;
import java.awt.geom.*;

/** Theme artwork drawn at the size of its surface. */
final class SignaturePanel extends JPanel {
    final int motif;
    final Color accent;
    Image cover; boolean plainCover;
    SignaturePanel(int motif,Color accent){
        super(new BorderLayout(12,12));this.motif=motif;this.accent=accent;setOpaque(false);
    }
    protected void paintComponent(Graphics raw){
        var g=Look.brush(raw);int w=getWidth(),h=getHeight();
        g.clip(new RoundRectangle2D.Double(0,0,w,h,9,9));
        g.setPaint(new GradientPaint(0,h,new Color(14,31,36),w,0,accent.darker().darker()));g.fillRect(0,0,w,h);
        if(cover!=null){Look.crop(g,cover,w,h);g.setColor(new Color(8,17,21,155));g.fillRect(0,0,w,h);}
        else{
            int baseline=h*(5+motif%3)/9;
            for(int layer=0;layer<4;layer++){
                var ridge=new Path2D.Double();ridge.moveTo(0,h);ridge.lineTo(0,baseline+layer*9);
                for(int k=1;k<=12;k++){
                    double rise=Math.sin(k*.86+motif+layer*.6)*h*.17+Math.cos(k*1.7-layer)*h*.10;
                    ridge.lineTo(k*w/12.0,baseline+rise+layer*h*.07);
                }
                ridge.lineTo(w,h);ridge.closePath();
                g.setColor(new Color(10+layer*3,33+layer*2,40+layer*2,195));g.fill(ridge);
                g.setColor(new Color(accent.getRed(),accent.getGreen(),accent.getBlue(),72-layer*10));g.draw(ridge);
            }
        }
        g.dispose();
    }
}
