package org.matsim.contrib.josm;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.Dimension;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.WindowConstants;

import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.actions.JosmAction;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.gui.layer.OsmDataLayer;
import org.openstreetmap.josm.gui.progress.PleaseWaitProgressMonitor;
import org.openstreetmap.josm.io.Compression;
import org.openstreetmap.josm.io.IllegalDataException;
import org.openstreetmap.josm.io.OsmReader;
import org.openstreetmap.josm.tools.GBC;
import org.openstreetmap.josm.tools.ImageProvider;
import org.openstreetmap.josm.tools.Shortcut;

public class FilteredOsmParserAction extends JosmAction {
    
    
    public FilteredOsmParserAction() {
        super(tr("Parse Osm Data for MATSim content"), null,
                tr("Parse Osm Data for MATSim content"), Shortcut
                        .registerShortcut(
                                "menu:matsimParse",
                                tr("Menu: {0}",
                                        tr("Parse Osm Data for MATSim content")),
                                KeyEvent.VK_G, Shortcut.ALT_CTRL), true);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
	
	ParseDialog dialog = new ParseDialog();
	
	JOptionPane pane = new JOptionPane(dialog,
	                JOptionPane.PLAIN_MESSAGE, JOptionPane.OK_CANCEL_OPTION);
	JDialog dlg = pane.createDialog(Main.parent, tr("Parse"));
	dlg.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
	dlg.setMinimumSize(new Dimension(1200, 600));
	dlg.setVisible(true);
	if (pane.getValue() != null) {
	    if (((Integer) pane.getValue()) == JOptionPane.OK_OPTION) {
		if(!dialog.osmPathButton.getText().equals("choose")) {
		    File file = new File(dialog.osmPathButton.getText());
		    InputStream stream = null;
		    try {
			stream = Compression.getUncompressedFileInputStream(file);
		    } catch (IOException e1) {
			JOptionPane.showMessageDialog(Main.parent,
				 "Import failed: "+e1.getMessage(),
				 "Failure", JOptionPane.ERROR_MESSAGE, new
				 ImageProvider("warning-small").setWidth(16).get());
		    }
		    
		    DataSet data = null;
		    if(stream!=null) {
			OsmReader.registerPostprocessor(new FilteredOsmParser());
        		try {
        		    PleaseWaitProgressMonitor progress = new PleaseWaitProgressMonitor();
        		    data = OsmReader.parseDataSet(stream, progress);
        		    progress.finishTask();
        		    progress.close();
        		} catch (IllegalDataException e1) {
        		    JOptionPane.showMessageDialog(Main.parent,
        			    "Import failed: "+e1.getMessage(),
        			    "Failure", JOptionPane.ERROR_MESSAGE, new
        			    ImageProvider("warning-small").setWidth(16).get());
        		}
        		
        		if(data!= null) {
        		    OsmDataLayer layer = new OsmDataLayer(data, file.getPath(), file);
        		    Main.main.addLayer(layer);
        		    Main.map.mapView.setActiveLayer(layer);
    			
        		}
		    }
	        }
	    }
	}
	dlg.dispose();
    }
    
    
    
    class ParseDialog extends JPanel {
	
	final JLabel osmPath = new JLabel("Osm file");
	final JButton osmPathButton = new JButton("choose");
	
	ParseDialog() {
	    
	    setLayout(new GridBagLayout());
	    add(osmPath, GBC.std());
	    
	    osmPathButton.addActionListener(new ActionListener() {
	        
	        @Override
	        public void actionPerformed(ActionEvent e) {
	            JFileChooser chooser = new JFileChooser(
	        	    System.getProperty("user.home"));
	            chooser.setApproveButtonText("Parse");
	            chooser.setDialogTitle("MATSim-Osm-Parser");
	            int result = chooser.showOpenDialog(Main.parent);
	            if (result == JFileChooser.APPROVE_OPTION
	        	    && chooser.getSelectedFile().getAbsolutePath() != null) {
	        		osmPathButton.setText(chooser.getSelectedFile().getAbsolutePath());
	            }
	        }
	    });
		
	    add(osmPathButton, GBC.eop());
	}
    }
}
