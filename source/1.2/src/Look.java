import java.awt.*;
import java.awt.geom.*;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

final class Look {
    static final Color surface = new Color(27,45,49), edge = new Color(64,86,88);
    static final Image[] scenes = images();
    private static Image[] images() {
        String[] names = {"court","pier","rail"};
        Image[] result = new Image[names.length];
        for (int i=0;i<names.length;i++) result[i]=new ImageIcon(Look.class.getResource("/"+names[i]+".jpg")).getImage();
        return result;
    }
    static Graphics2D brush(Graphics raw) {
        var g=(Graphics2D)raw.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        return g;
    }
    static void crop(Graphics2D g,Image img,int w,int h){
        int sw=img.getWidth(null),sh=img.getHeight(null);
        double factor=Math.max(w/(double)sw,h/(double)sh);
        int width=(int)Math.ceil(sw*factor),height=(int)Math.ceil(sh*factor);
        g.drawImage(img,(w-width)/2,(h-height)/2,width,height,null);
    }
    static class Art extends JPanel {
        final int scene; final boolean shade;
        Art(int scene,int height,boolean shade){
            super(new BorderLayout(14,0));this.scene=scene;this.shade=shade;
            setOpaque(false);setPreferredSize(new Dimension(130,height));
        }
        protected void paintComponent(Graphics raw){
            var g=brush(raw);int w=getWidth(),h=getHeight();
            g.clip(new RoundRectangle2D.Double(0,0,w,h,20,20));crop(g,scenes[scene],w,h);
            if(shade){g.setPaint(new GradientPaint(0,0,new Color(16,12,26,245),w*.9f,0,new Color(22,14,37,12)));g.fillRect(0,0,w,h);}
            g.dispose();
        }
    }
    static JPanel card(LayoutManager layout){
        var p=new JPanel(layout){
            protected void paintComponent(Graphics raw){
                var g=brush(raw);g.setColor(getBackground());g.fillRoundRect(0,0,getWidth(),getHeight(),5,5);
                g.setColor(edge);g.drawRoundRect(0,0,getWidth()-1,getHeight()-1,5,5);g.dispose();
            }
        };p.setOpaque(false);p.setBackground(surface);return p;
    }
    static JButton button(String title,boolean primary) {
        var b=new JButton(title){
            protected void paintComponent(Graphics raw){
                var g=brush(raw);var base=getBackground();boolean nav=Boolean.TRUE.equals(getClientProperty("navigation"));
                if(getModel().isPressed()||getModel().isRollover())base=base.brighter();
                if(!nav||Boolean.TRUE.equals(getClientProperty("selected"))||getModel().isRollover()){
                    g.setColor(base);g.fillRoundRect(0,0,getWidth(),getHeight(),6,6);
                }
                if(nav&&Boolean.TRUE.equals(getClientProperty("selected"))){g.setColor(getForeground());g.fillRect(8,getHeight()-3,getWidth()-16,3);}
                if(!nav||hasFocus()){
                    g.setColor(hasFocus()?Sonder.INK:primary?base:edge);g.drawRoundRect(0,0,getWidth()-1,getHeight()-1,6,6);
                }
                g.dispose();super.paintComponent(raw);
            }
        };b.setOpaque(false);b.setContentAreaFilled(false);b.setBorderPainted(false);b.setFocusPainted(false);b.setRolloverEnabled(true);
        b.setFont(new Font("SansSerif",Font.BOLD,12));b.setForeground(primary?new Color(25,16,34):Sonder.INK);b.setBackground(primary?new Color(133,232,196):surface);
        b.setBorder(new EmptyBorder(10,15,10,15));b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));return b;
    }
    static JScrollPane scroll(Component item){
        var s=new JScrollPane(item);s.setBorder(null);s.setOpaque(false);s.getViewport().setOpaque(false);s.getVerticalScrollBar().setUnitIncrement(24);return s;
    }
    static Icon icon(String name){
        return new Icon(){
            public int getIconWidth(){return 20;}public int getIconHeight(){return 20;}
            public void paintIcon(Component c,Graphics raw,int x,int y){
                var g=brush(raw);g.translate(x,y);g.setColor(c.getForeground());g.setStroke(new BasicStroke(1.6f,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
                switch(name){
                    case "Play" -> g.drawPolygon(new int[]{5,16,5},new int[]{3,10,17},3);
                    case "Player" -> {g.drawOval(7,2,6,7);g.drawArc(3,11,14,12,0,180);}
                    case "Settings" -> {for(int i=0;i<3;i++){int yy=4+i*6,xx=i==1?6:13;g.drawLine(2,yy,xx-2,yy);g.drawOval(xx-2,yy-2,4,4);g.drawLine(xx+2,yy,18,yy);}}
                    case "Performance" -> {g.drawArc(2,4,16,16,0,180);g.drawLine(10,12,15,7);g.drawLine(3,15,17,15);}
                    case "Themes" -> {g.drawOval(2,2,13,13);g.drawOval(7,7,11,11);}
                    default -> {g.drawPolygon(new int[]{2,10,18,10},new int[]{6,2,6,10},4);g.drawPolyline(new int[]{2,10,18},new int[]{10,14,10},3);g.drawPolyline(new int[]{2,10,18},new int[]{14,18,14},3);}
                }g.dispose();
            }
        };
    }
}
