import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import javax.imageio.*;
import javax.swing.*;
final class SkinPortrait extends JPanel{
    final BufferedImage skin;
    final Color tint;
    SkinPortrait(Path file,Color tint)throws IOException{
        this.tint=tint;skin=Files.exists(file)?ImageIO.read(file.toFile()):null;
        setPreferredSize(new Dimension(150,200));setOpaque(false);
        setToolTipText("Drag to rotate");var drag=new java.awt.event.MouseAdapter(){
            public void mousePressed(java.awt.event.MouseEvent e){lastX=e.getX();}
            public void mouseDragged(java.awt.event.MouseEvent e){yaw+=(e.getX()-lastX)*.022;lastX=e.getX();repaint();}
        };addMouseListener(drag);addMouseMotionListener(drag);
    }
    static void choose(Sonder app)throws Exception{
        var fc=new JFileChooser();fc.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Minecraft skin PNG","png"));
        if(fc.showOpenDialog(app)!=JFileChooser.APPROVE_OPTION)return;
        Path src=fc.getSelectedFile().toPath();if(Files.size(src)>1024*1024)throw new IOException("Choose a 64 × 64 or 64 × 32 skin PNG.");
        try(var stream=ImageIO.createImageInputStream(src.toFile())){var readers=ImageIO.getImageReaders(stream);if(!readers.hasNext())throw new IOException("Choose a PNG skin.");var reader=readers.next();try{reader.setInput(stream);
            int h=reader.getHeight(0);if(!reader.getFormatName().equalsIgnoreCase("png")||reader.getWidth(0)!=64||(h!=32&&h!=64))throw new IOException("Choose a 64 × 64 or 64 × 32 skin PNG.");
            var tmp=Files.createTempFile(app.e.base,"portrait",".png");try{ImageIO.write(reader.read(0),"png",tmp.toFile());Engine.commit(tmp,app.e.base.resolve("portrait.png"));}finally{Files.deleteIfExists(tmp);}
        }finally{reader.dispose();}}app.refresh();
    }
    double yaw=-0.38; int lastX; java.awt.image.BufferedImage frame;
    double cachedYaw;int cachedW,cachedH;
    record Face(double depth,BufferedImage texture,java.awt.geom.AffineTransform transform){}
    record P(double x,double y,double depth){}
    P project(double x,double y,double z){
        double c=Math.cos(yaw),s=Math.sin(yaw),d=z*c-x*s;
        double scale=Math.min(getWidth()/26.0,getHeight()/37.0);
        return new P(getWidth()/2.0+(x*c+z*s)*scale,getHeight()/2.0+((y-16)*.985+d*.17)*scale,d*.985-(y-16)*.17);
    }
    void face(java.util.List<Face> faces,double[][] vertices,int u,int v,int w,int h,boolean mirror){
        var a=project(vertices[0][0],vertices[0][1],vertices[0][2]);
        var b=project(vertices[1][0],vertices[1][1],vertices[1][2]);
        var d=project(vertices[2][0],vertices[2][1],vertices[2][2]);
        double bx=b.x-a.x,by=b.y-a.y,dx=d.x-a.x,dy=d.y-a.y;
        if(bx*dy-by*dx<=0.0001)return;
        var tx=new java.awt.geom.AffineTransform((mirror?-bx:bx)/w,(mirror?-by:by)/w,dx/h,dy/h,mirror?b.x:a.x,mirror?b.y:a.y);
        faces.add(new Face((b.depth+d.depth)/2,skin.getSubimage(u,v,w,h),tx));
    }
    void box(java.util.List<Face> f,double x,double y,double z,int w,int h,int depth,int u,int v,double inflate,boolean mirror){
        double l=x-inflate,r=x+w+inflate,t=y-inflate,b=y+h+inflate,n=z+depth+inflate,k=z-inflate;
        double[] ltn={l,t,n},rtn={r,t,n},lbn={l,b,n},rbn={r,b,n},ltk={l,t,k},rtk={r,t,k},lbk={l,b,k},rbk={r,b,k};
        face(f,new double[][]{ltn,rtn,lbn},u+depth,v+depth,w,h,mirror);
        face(f,new double[][]{rtk,ltk,rbk},u+depth*2+w,v+depth,w,h,mirror);
        face(f,new double[][]{ltk,ltn,lbk},mirror?u+depth+w:u,v+depth,depth,h,mirror);
        face(f,new double[][]{rtn,rtk,rbn},mirror?u:u+depth+w,v+depth,depth,h,mirror);
        face(f,new double[][]{ltk,rtk,ltn},u+depth,v,w,depth,mirror);
        face(f,new double[][]{lbn,rbn,lbk},u+depth+w,v,w,depth,mirror);
    }
    protected void paintComponent(Graphics raw){
        if(skin==null){
            raw.setColor(Sonder.INK);String text="Skin unavailable";var fm=raw.getFontMetrics();
            raw.drawString(text,(getWidth()-fm.stringWidth(text))/2,getHeight()/2);return;
        }
        if(frame==null||cachedW!=getWidth()||cachedH!=getHeight()||yaw!=cachedYaw){
            cachedW=getWidth();cachedH=getHeight();cachedYaw=yaw;
            frame=new BufferedImage(cachedW,cachedH,BufferedImage.TYPE_INT_ARGB);var g=frame.createGraphics();
            var f=new java.util.ArrayList<Face>();boolean modern=skin.getHeight()==64;
            boolean slim=modern&&((skin.getRGB(54,20)>>>24)==0&&(skin.getRGB(55,31)>>>24)==0);int arm=slim?3:4;
            box(f,-4,0,-4,8,8,8,0,0,0,false);box(f,-4,0,-4,8,8,8,32,0,.46,false);
            box(f,-4,8,-2,8,12,4,16,16,0,false);box(f,-4-arm,8,-2,arm,12,4,40,16,0,false);
            box(f,4,8,-2,arm,12,4,modern?32:40,modern?48:16,0,!modern);
            box(f,-4,20,-2,4,12,4,0,16,0,false);box(f,0,20,-2,4,12,4,modern?16:0,modern?48:16,0,!modern);
            if(modern){box(f,-4,8,-2,8,12,4,16,32,.22,false);box(f,-4-arm,8,-2,arm,12,4,40,32,.22,false);
                box(f,4,8,-2,arm,12,4,48,48,.22,false);box(f,-4,20,-2,4,12,4,0,32,.22,false);box(f,0,20,-2,4,12,4,0,48,.22,false);}
            f.sort(java.util.Comparator.comparingDouble(Face::depth));
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            for(var face:f)g.drawImage(face.texture,face.transform,null);g.dispose();
        }
        raw.drawImage(frame,0,0,null);
    }
}
