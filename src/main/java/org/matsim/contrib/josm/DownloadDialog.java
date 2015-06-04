package org.matsim.contrib.josm;

//License: GPL. For details, see LICENSE file.

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Arrays;
import java.util.List;

import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;

import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.tools.GBC;

/**
 * Dialog displayed to download OSM data from OSM server.
 */
public class DownloadDialog extends org.openstreetmap.josm.gui.download.DownloadDialog {

    private static DownloadDialog instance;

    private JLabel lblHighways;
    private JLabel lblRoutes;

    private JCheckBox cbDownloadMotorway;
    private JCheckBox cbDownloadMotorwayLink;
    private JCheckBox cbDownloadTrunk;
    private JCheckBox cbDownloadTrunkLink;
    private JCheckBox cbDownloadPrimary;
    private JCheckBox cbDownloadPrimaryLink;
    private JCheckBox cbDownloadSecondary;
    private JCheckBox cbDownloadTertiary;
    private JCheckBox cbDownloadMinor;
    private JCheckBox cbDownloadUnclassified;
    private JCheckBox cbDownloadResidential;
    private JCheckBox cbDownloadLivingStreet;

    private JCheckBox cbDownloadRail;
    private JCheckBox cbDownloadLightRail;
    private JCheckBox cbDownloadTram;
    private JCheckBox cbDownloadSubway;
    private JCheckBox cbDownloadBus;

    private List<JCheckBox> highways;
    private List<JCheckBox> routes;

    public DownloadDialog(Component comp) {
	super(comp);
    }

    protected void buildMainPanelAboveDownloadSelections(JPanel pnl) {
	pnl.removeAll();
	
	JPanel contentPnl = new JPanel(new GridLayout(0, 1));
	contentPnl.setAlignmentX(LEFT_ALIGNMENT);
	JPanel highwaysPnl = new JPanel(new FlowLayout());
	highwaysPnl.setPreferredSize(pnl.getPreferredSize());
	highwaysPnl.setAlignmentX(LEFT_ALIGNMENT);
	JPanel routesPnl = new JPanel(new FlowLayout());
	routesPnl.setPreferredSize(pnl.getPreferredSize());
	routesPnl.setAlignmentX(LEFT_ALIGNMENT);
	
	
	lblHighways = new JLabel("Highways:");
	contentPnl.add(lblHighways);
	highways = Arrays.asList(cbDownloadMotorway, cbDownloadMotorwayLink, cbDownloadTrunk, cbDownloadTrunkLink, cbDownloadPrimary, cbDownloadPrimaryLink, cbDownloadSecondary, cbDownloadTertiary, cbDownloadMinor, cbDownloadUnclassified, cbDownloadResidential, cbDownloadLivingStreet);

	ActionListener cbListener = new ActionListener() {

	    @Override
	    public void actionPerformed(ActionEvent e) {
		JCheckBox cb = (JCheckBox) e.getSource();
		Main.pref.put("matsim_download_" + cb.getText(), cb.isSelected());
	    }
	};

	int i = 0;
	for (JCheckBox cb : highways) {
	    cb = new JCheckBox(OsmConvertDefaults.wayTypes[i]);
	    cb.setToolTipText(tr("Select to download " + cb.getText() + " highways in the selected download area."));
	    cb.setSelected(Main.pref.getBoolean("matsim_download_" + cb.getText(), true));
	    cb.addActionListener(cbListener);
	    if (i == highways.size() - 1) {
		highwaysPnl.add(cb, GBC.eol());
	    } else {
		highwaysPnl.add(cb, GBC.std());
	    }
	    i++;
	}
	contentPnl.add(highwaysPnl);

	lblRoutes = new JLabel("Routes:");
	contentPnl.add(lblRoutes);
	routes = Arrays.asList(cbDownloadRail, cbDownloadLightRail, cbDownloadTram, cbDownloadSubway, cbDownloadBus);

	int j = 12;
	for (JCheckBox cb : routes) {
	    if (j < 16) {
		cb = new JCheckBox(OsmConvertDefaults.wayTypes[j]);
		cb.setToolTipText(tr("Select to download " + cb.getText() + " routes in the selected download area."));
		cb.setSelected(Main.pref.getBoolean("matsim_download_" + cb.getText(), true));
		cb.addActionListener(cbListener);
		routesPnl.add(cb, GBC.std());
		j++;
	    } else {
		cb = new JCheckBox("bus");
		cb.setToolTipText(tr("Select to download " + cb.getText() + " routes in the selected download area."));
		cb.setSelected(Main.pref.getBoolean("matsim_download_" + cb.getText(), true));
		cb.addActionListener(cbListener);
		routesPnl.add(cb, GBC.eol());
	    }
	}
	contentPnl.add(routesPnl);

	pnl.add(contentPnl, GBC.eol());
    }

    /**
     * Replies true if the user selected to download OSM data
     * 
     * @return true if the user selected to download OSM data
     */
    public boolean isDownloadOsmData() {
	return true;
    }

    /**
     * Replies true if the user selected to download GPX data
     * 
     * @return true if the user selected to download GPX data
     */
    public boolean isDownloadGpxData() {
	return false;
    }

    /**
     * Replies the unique instance of the download dialog
     * 
     * @return the unique instance of the download dialog
     */
    public static synchronized DownloadDialog getInstance() {
	if (instance == null) {
	    instance = new DownloadDialog(Main.parent);
	}
	return instance;
    }

    /**
     * Remembers the current settings in the download dialog.
     */
    public void rememberSettings() {
	if (currentBounds != null) {
	    Main.pref.put("osm-download.bounds", currentBounds.encodeAsString(";"));
	}
    }
}
