package waffleoRai_Video.psx;

import java.awt.Dimension;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;

import waffleoRai_Files.psx.XAStreamFile;
import waffleoRai_Files.tree.FileNode;
import waffleoRai_Sound.psx.PSXXAStream;
import waffleoRai_Sound.psx.XAAudioStream;
import waffleoRai_Video.AVPlayerPanel;
import waffleoRai_Video.BufferedVideoPanel;

public class Test_VideoPlayer {

	public static void main(String[] args) {
		// TODO Auto-generated method stub
		String infile = "C:\\Users\\Blythe\\Documents\\Game Stuff\\PSX\\GameData\\MewMew\\MOVIE.BIN";
		
		
		/*SwingUtilities.invokeLater(new Runnable() {
            public void run() {
            	
            	try{
        			PSXXAStream fullstr = PSXXAStream.readStream(FileNode.createFileNodeOf(infile));
        			XAStreamFile strfile = fullstr.getFile(0); //Should only be one.
        			
        			XAVideoSource vidsrc = new XAVideoSource(strfile, 1);
        			
        			JFrame mygui = new JFrame();
                	BufferedVideoPanel vpnl = new BufferedVideoPanel(vidsrc, true, false, true);
                	vpnl.setAsyncBuffering(true);
                	mygui.add(vpnl);
                	
                	mygui.setMinimumSize(new Dimension(vidsrc.getWidth(), vidsrc.getHeight()));
                	mygui.setPreferredSize(new Dimension(vidsrc.getWidth(), vidsrc.getHeight()));
                	
                	mygui.addWindowListener(new WindowAdapter(){

    					public void windowClosing(WindowEvent e){
    						System.exit(0);
    					}
                	});
                	
                	mygui.pack();
                	
                	vpnl.startBufferWorker();
                	mygui.setVisible(true);
        		}
        		catch(Exception e){
        			e.printStackTrace();
        			System.exit(1);
        		}
            }
        });*/
		
		//With audio
		/*SwingUtilities.invokeLater(new Runnable() {
            public void run() {
            	
            	try{
        			PSXXAStream fullstr = PSXXAStream.readStream(FileNode.createFileNodeOf(infile));
        			XAStreamFile strfile = fullstr.getFile(0); //Should only be one.
        			
        			XAVideoSource vidsrc = new XAVideoSource(strfile, 1);
        			XAAudioStream sndsrc = new XAAudioStream(strfile, 1);
        			
        			JFrame mygui = new JFrame();
                	AVPlayerPanel vpnl = new AVPlayerPanel(vidsrc, sndsrc, true, false, true);
                	vpnl.openBuffer();
                	mygui.add(vpnl);
                	
                	mygui.setMinimumSize(new Dimension(vidsrc.getWidth(), vidsrc.getHeight()));
                	mygui.setPreferredSize(new Dimension(vidsrc.getWidth(), vidsrc.getHeight()));
                	
                	mygui.addWindowListener(new WindowAdapter(){

    					public void windowClosing(WindowEvent e){
    						System.exit(0);
    					}
                	});
                	
                	mygui.pack();
                	
                	mygui.setVisible(true);
        		}
        		catch(Exception e){
        			e.printStackTrace();
        			System.exit(1);
        		}
            }
        });*/

	}

}
