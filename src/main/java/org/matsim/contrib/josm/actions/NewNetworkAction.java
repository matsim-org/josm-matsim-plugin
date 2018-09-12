package org.matsim.contrib.josm.actions;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;

import org.matsim.contrib.josm.model.MATSimLayer;
import org.matsim.contrib.josm.model.NetworkModel;
import org.openstreetmap.josm.actions.JosmAction;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.tools.Shortcut;

/**
 * New Network Action which causes an empty
 * {@link MATSimLayer} to be created
 *
 * @author Nico
 *
 */
@SuppressWarnings("serial")
public class NewNetworkAction extends JosmAction {

	public NewNetworkAction() {
		super(tr("New MATSim network"), "new.png", tr("Create new Network"), Shortcut.registerShortcut("menu:matsimNetwork",
				tr("Menu: {0}", tr("New MATSim network")), KeyEvent.VK_G, Shortcut.ALT_CTRL), true);
	}

	@Override
	public void actionPerformed(ActionEvent e) {
		MainApplication.getLayerManager().addLayer(createMatsimLayer());
	}

	public static MATSimLayer createMatsimLayer() {
		DataSet dataSet = new DataSet();
		return new MATSimLayer(dataSet, MATSimLayer.createNewName(), null, NetworkModel.createNetworkModel(dataSet));
	}
}
