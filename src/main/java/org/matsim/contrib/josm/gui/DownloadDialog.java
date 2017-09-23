package org.matsim.contrib.josm.gui;

import org.matsim.contrib.josm.model.OsmConvertDefaults;
import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.spi.preferences.Config;
import org.openstreetmap.josm.tools.GBC;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;

import static org.openstreetmap.josm.tools.I18n.tr;

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

		JPanel contentPnl = new JPanel(new GridLayout(2, 1));

		ActionListener cbListener = e -> {
			JCheckBox cb = (JCheckBox) e.getSource();
			Config.getPref().putBoolean("matsim_download_" + cb.getText(), cb.isSelected());
		};

		JPanel highwaysPnl = new JPanel(new FlowLayout());
		highwaysPnl.add(new JLabel("Highways:"));
		for (String highwayType : OsmConvertDefaults.highwayTypes) {
			JCheckBox cb = new JCheckBox(highwayType);
			cb.setToolTipText(tr("Select to download " + cb.getText() + " highways in the selected download area."));
			cb.setSelected(Config.getPref().getBoolean("matsim_download_" + cb.getText(), true));
			cb.addActionListener(cbListener);
			highwaysPnl.add(cb, GBC.std());
		}
		contentPnl.add(highwaysPnl);

		JPanel routesPnl = new JPanel(new FlowLayout());
		routesPnl.add(new JLabel("Routes:"));
		for (String routeType : OsmConvertDefaults.routeTypes) {
			JCheckBox cb = new JCheckBox(routeType);
			cb.setToolTipText(tr("Select to download " + cb.getText() + " routes in the selected download area."));
			cb.setSelected(Config.getPref().getBoolean("matsim_download_" + cb.getText(), true));
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
			Config.getPref().put("osm-download.bounds", currentBounds.encodeAsString(";"));
		}
	}
}
