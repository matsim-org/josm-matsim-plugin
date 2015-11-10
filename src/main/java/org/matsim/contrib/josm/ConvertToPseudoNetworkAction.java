package org.matsim.contrib.josm;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Node;
import org.matsim.contrib.josm.scenario.EditableScenario;
import org.matsim.contrib.josm.scenario.EditableScenarioUtils;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitRouteStop;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;
import org.matsim.pt.utils.CreatePseudoNetwork;
import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.actions.JosmAction;
import org.openstreetmap.josm.data.ProjectionBounds;
import org.openstreetmap.josm.data.osm.Relation;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.data.osm.WaySegment;
import org.openstreetmap.josm.tools.Shortcut;

import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static org.openstreetmap.josm.tools.I18n.tr;

class ConvertToPseudoNetworkAction extends JosmAction {

	public ConvertToPseudoNetworkAction() {
		super(tr("Convert to transit pseudo-network"), null, tr("Convert to transit pseudo-network"), Shortcut.registerShortcut("menu:matsimPseudoNetwork",
				tr("Menu: {0}", tr("Convert to transit pseudo-network")), KeyEvent.VK_G, Shortcut.ALT_CTRL), true);
	}

	@Override
	public void actionPerformed(ActionEvent e) {
		if (isEnabled()) {
			Main.worker.submit(new Runnable() {
				@Override
				public void run() {
					try {
						Config config = ConfigUtils.createConfig();
						config.transit().setUseTransit(Preferences.isSupportTransit());
						EditableScenario sourceScenario = EditableScenarioUtils.createScenario(config);

						NetworkListener networkListener = new NetworkListener(getEditLayer().data, sourceScenario, new HashMap<Way, List<Link>>(),
								new HashMap<Link, List<WaySegment>>(), new HashMap<Relation, TransitStopFacility>());
						networkListener.visitAll();

						emptyNetwork(sourceScenario);
						fixTransitSchedule(sourceScenario);
						new CreatePseudoNetwork(sourceScenario.getTransitSchedule(), sourceScenario.getNetwork(), "pt_")
								.createNetwork();

						Importer importer = new Importer(sourceScenario, Main.getProjection());
						importer.run();
						ProjectionBounds projectionBounds = null;

						Main.main.addLayer(importer.getLayer(), projectionBounds);
					} catch (Exception e) {
						Main.error(e);
					}
				}
			});
		}
	}

	private void emptyNetwork(EditableScenario sourceScenario) {
		for (Id<Link> linkId : new ArrayList<>(sourceScenario.getNetwork().getLinks().keySet())) {
			sourceScenario.getNetwork().removeLink(linkId);
		}
		for (Id<Node> nodeId : new ArrayList<>(sourceScenario.getNetwork().getNodes().keySet())) {
			sourceScenario.getNetwork().removeNode(nodeId);
		}
		for (TransitLine transitLine : sourceScenario.getTransitSchedule().getTransitLines().values()) {
			for (TransitRoute transitRoute : transitLine.getRoutes().values()) {
				transitRoute.setRoute(null);
			}
		}
	}

	private void fixTransitSchedule(EditableScenario sourceScenario) {
		for (TransitLine transitLine : sourceScenario.getTransitSchedule().getTransitLines().values()) {
			for (TransitRoute transitRoute : transitLine.getRoutes().values()) {
				transitRoute.setRoute(null);
				// FIXME: I have to normalize the facilities here - there are referenced facility objects here which are not in the scenario.
				// FIXME: This fact will VERY likely also lead to problems elsewhere.
				for (TransitRouteStop transitRouteStop : transitRoute.getStops()) {
					transitRouteStop.setStopFacility(sourceScenario.getTransitSchedule().getFacilities().get(transitRouteStop.getStopFacility().getId()));
				}
			}
		}
	}

	@Override
	protected void updateEnabledState() {
		setEnabled(getEditLayer() != null && !(getEditLayer() instanceof MATSimLayer));
	}

}
