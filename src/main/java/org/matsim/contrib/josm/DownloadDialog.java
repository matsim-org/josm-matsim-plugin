package org.matsim.contrib.josm;

//License: GPL. For details, see LICENSE file.

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

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

		JLabel lblHighways = new JLabel("Highways:");
		contentPnl.add(lblHighways);

		ActionListener cbListener = new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				JCheckBox cb = (JCheckBox) e.getSource();
				Main.pref.put("matsim_download_" + cb.getText(), cb.isSelected());
			}
		};

		for (String highwayType : OsmConvertDefaults.highwayTypes) {
			JCheckBox cb = new JCheckBox(highwayType);
			cb.setToolTipText(tr("Select to download " + cb.getText() + " highways in the selected download area."));
			cb.setSelected(Main.pref.getBoolean("matsim_download_" + cb.getText(), true));
			cb.addActionListener(cbListener);
			highwaysPnl.add(cb, GBC.std());
		}
		contentPnl.add(highwaysPnl);

		JLabel lblRoutes = new JLabel("Routes:");
		contentPnl.add(lblRoutes);

		for (String routeType : OsmConvertDefaults.routeTypes) {
			JCheckBox cb = new JCheckBox(routeType);
			cb.setToolTipText(tr("Select to download " + cb.getText() + " routes in the selected download area."));
			cb.setSelected(Main.pref.getBoolean("matsim_download_" + cb.getText(), true));
			cb.addActionListener(cbListener);
			routesPnl.add(cb, GBC.std());
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
