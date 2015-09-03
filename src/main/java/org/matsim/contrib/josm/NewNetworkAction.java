package org.matsim.contrib.josm;

import org.matsim.api.core.v01.network.Link;
import org.matsim.contrib.josm.scenario.EditableScenario;
import org.matsim.contrib.josm.scenario.EditableScenarioUtils;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;
import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.actions.JosmAction;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.Relation;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.data.osm.WaySegment;
import org.openstreetmap.josm.tools.Shortcut;

import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.HashMap;
import java.util.List;

import static org.openstreetmap.josm.tools.I18n.tr;

/**
 * New Network Action which causes an empty {@link org.matsim.contrib.josm.MATSimLayer} to be
 * created
 *
 * @author Nico
 *
 */
@SuppressWarnings("serial")
class NewNetworkAction extends JosmAction {

    public NewNetworkAction() {
        super(tr("New MATSim network"), "new.png",
                tr("Create new Network"), Shortcut.registerShortcut(
                        "menu:matsimNetwork",
                        tr("Menu: {0}", tr("New MATSim Network")),
                        KeyEvent.VK_G, Shortcut.ALT_CTRL), true);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        MATSimLayer layer = createMatsimLayer();
        Main.main.addLayer(layer);
    }

    static MATSimLayer createMatsimLayer() {
        DataSet dataSet = new DataSet();
        Config config = ConfigUtils.createConfig();
        EditableScenario scenario = EditableScenarioUtils.createScenario(config);
        return new MATSimLayer(dataSet, MATSimLayer.createNewName(), null,
                scenario, new HashMap<Way, List<Link>>(),
                new HashMap<Link, List<WaySegment>>(), new HashMap<Relation, TransitStopFacility>()
        );
    }
}
