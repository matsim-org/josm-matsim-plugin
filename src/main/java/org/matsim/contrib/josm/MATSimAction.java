package org.matsim.contrib.josm;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.HashMap;
import java.util.List;

import javax.swing.JDialog;
import javax.swing.JOptionPane;
import javax.swing.WindowConstants;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;
import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.actions.JosmAction;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.Relation;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.data.osm.WaySegment;
import org.openstreetmap.josm.gui.layer.Layer;
import org.openstreetmap.josm.gui.layer.OsmDataLayer;
import org.openstreetmap.josm.tools.Shortcut;

/**
 * Adds the MATSim buttons and their functionality to the tools bar.
 * 
 * @author Nico
 * 
 */
class MATSimAction {

	public JosmAction getImportAction() {
		return new ImportAction();
	}

	public JosmAction getNewNetworkAction() {
		return new NewNetworkAction();
	}

	public JosmAction getConvertAction() {
		return new ConvertAction();
	}

	/**
	 * The ImportAction that handles network imports.
	 * 
	 * @author Nico
	 * 
	 */
	@SuppressWarnings("serial")
	public class ImportAction extends JosmAction {

		public ImportAction() {
			super(tr("Import MATSim scenario"), "open.png",
					tr("Import MATSim scenario"), Shortcut.registerShortcut(
							"menu:matsimImport",
							tr("Menu: {0}", tr("MATSim Import")),
							KeyEvent.VK_G, Shortcut.ALT_CTRL), true);
		}

		@Override
		public void actionPerformed(ActionEvent e) {

			ImportDialog dialog = new ImportDialog();
			JOptionPane pane = new JOptionPane(dialog,
					JOptionPane.PLAIN_MESSAGE, JOptionPane.OK_CANCEL_OPTION);
			JDialog dlg = pane.createDialog(Main.parent, tr("Import"));
			dlg.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

			dlg.setVisible(true);
			if (pane.getValue() != null) {
				if (((Integer) pane.getValue()) == JOptionPane.OK_OPTION) {
					if(!dialog.networkPathButton.getText().equals("choose")) {
						if(dialog.schedulePathButton.getText().equals("choose")) {
							dialog.schedulePathButton.setText(null);
						}
						ImportTask task = new ImportTask(
								dialog.networkPathButton.getText(),
								dialog.schedulePathButton.getText());
						Main.worker.execute(task);
					}
				}
			}
			dlg.dispose();
		}
	}

	/**
	 * New Network Action which causes an empty {@link MATSimLayer} to be
	 * created
	 * 
	 * @author Nico
	 * 
	 */
	@SuppressWarnings("serial")
	public class NewNetworkAction extends JosmAction {

		public NewNetworkAction() {
			super(tr("New MATSim network"), "new.png",
					tr("Create new Network"), Shortcut.registerShortcut(
							"menu:matsimNetwork",
							tr("Menu: {0}", tr("New MATSim Network")),
							KeyEvent.VK_G, Shortcut.ALT_CTRL), true);
		}

		@Override
		public void actionPerformed(ActionEvent e) {
			DataSet dataSet = new DataSet();
			Config config = ConfigUtils.createConfig();
			Scenario scenario = ScenarioUtils.createScenario(config);
			MATSimLayer layer = new MATSimLayer(dataSet, "new Layer", null,
					scenario, new HashMap<Way, List<Link>>(),
					new HashMap<Link, List<WaySegment>>(),
					new HashMap<Relation, TransitRoute>());
			Main.main.addLayer(layer);
		}
	}

	/**
	 * The Convert Action which causes the {@link ConvertTask} to start. Results
	 * in a new {@link MATSimLayer} which holds the converted data.
	 * 
	 * @author Nico
	 * 
	 */
	@SuppressWarnings("serial")
	public static class ConvertAction extends JosmAction {

		public ConvertAction() {
			super(tr("Convert to MATSim Layer"), null,
					tr("Convert Osm layer to MATSim network layer"), Shortcut
							.registerShortcut(
									"menu:matsimConvert",
									tr("Menu: {0}",
											tr("Convert to MATSim Network")),
									KeyEvent.VK_G, Shortcut.ALT_CTRL), true);
		}

		@Override
		public void actionPerformed(ActionEvent e) {
			Layer activeLayer = Main.main.getActiveLayer();
			if (activeLayer instanceof OsmDataLayer
					&& !(activeLayer instanceof MATSimLayer)) {
				ConvertTask task = new ConvertTask();
				task.run();
			}
		}
	}
}
