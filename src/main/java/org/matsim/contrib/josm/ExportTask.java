package org.matsim.contrib.josm;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JOptionPane;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.network.LinkImpl;
import org.matsim.core.network.NetworkWriter;
import org.matsim.core.network.NodeImpl;
import org.matsim.core.network.algorithms.NetworkCleaner;
import org.matsim.core.population.routes.LinkNetworkRouteImpl;
import org.matsim.core.population.routes.NetworkRoute;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.io.UncheckedIOException;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitRouteStop;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt.transitSchedule.api.TransitScheduleWriter;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;
import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.gui.PleaseWaitRunnable;
import org.openstreetmap.josm.gui.layer.Layer;
import org.openstreetmap.josm.gui.layer.OsmDataLayer;
import org.openstreetmap.josm.io.OsmTransferException;
import org.xml.sax.SAXException;

/**
 * The task which which writes out the network xml file
 * 
 * @author Nico
 * 
 */

class ExportTask extends PleaseWaitRunnable {

	private final File networkFile;
	private final File scheduleFile;

	/**
	 * Creates a new Export task with the given export <code>file</code>
	 * location
	 * 
	 * @param file
	 *            The file to be exported to
	 */
	public ExportTask(File file, boolean newFile) {
		super("MATSim Export");
		// set file paths in given directory path
		if (newFile) {
			this.networkFile = new File(file.getAbsolutePath() + "/network.xml");
			this.scheduleFile = new File(file.getAbsolutePath()
					+ "/transit_schedule.xml");
		} else {
			this.networkFile = new File(file.getParentFile().getAbsolutePath()
					+ "/network.xml");
			this.scheduleFile = new File(file.getParentFile().getAbsolutePath()
					+ "/transit_schedule.xml");

		}
	}

	/**
	 * @see org.openstreetmap.josm.gui.PleaseWaitRunnable#cancel()
	 */
	@Override
	protected void cancel() {
	}

	/**
	 * @see org.openstreetmap.josm.gui.PleaseWaitRunnable#finish()
	 */
	@Override
	protected void finish() {
		JOptionPane.showMessageDialog(Main.parent,
				"Export finished. File written to: " + networkFile.getPath());
	}

	/**
	 * @see org.openstreetmap.josm.gui.PleaseWaitRunnable#realRun()
	 */
	@Override
	protected void realRun() throws SAXException, IOException,
			OsmTransferException, UncheckedIOException {

		this.progressMonitor.setTicksCount(3);
		this.progressMonitor.setTicks(0);

		// create empty data structures
		Config config = ConfigUtils.createConfig();
		Scenario sc = ScenarioUtils.createScenario(config);
		Network network = sc.getNetwork();
		config.scenario().setUseTransit(true);
		config.scenario().setUseVehicles(true);
		TransitSchedule schedule = sc.getTransitSchedule();
		Layer layer = Main.main.getActiveLayer();

		if (layer instanceof OsmDataLayer) {
            this.progressMonitor.setTicks(1);
            this.progressMonitor.setCustomText("rearranging data..");

            // copy nodes with switched id fields
            for (Node node : ((MATSimLayer) layer).getMatsimScenario()
                    .getNetwork().getNodes().values()) {
                Node newNode = network.getFactory()
                        .createNode(
                                Id.create(((NodeImpl) node).getOrigId(),
                                        Node.class), node.getCoord());
                network.addNode(newNode);
            }
            // copy links with switched id fields
            for (Link link : ((MATSimLayer) layer).getMatsimScenario()
                    .getNetwork().getLinks().values()) {
                Link newLink = network.getFactory()
                        .createLink(
                                Id.create(((LinkImpl) link).getOrigId(),
                                        Link.class),
                                network.getNodes().get(
                                        Id.create(
                                                ((NodeImpl) link
                                                        .getFromNode())
                                                        .getOrigId(),
                                                Link.class)),
                                network.getNodes().get(
                                        Id.create(((NodeImpl) link
                                                        .getToNode()).getOrigId(),
                                                Node.class)));
                newLink.setFreespeed(link.getFreespeed());
                newLink.setCapacity(link.getCapacity());
                newLink.setLength(link.getLength());
                newLink.setNumberOfLanes(link.getNumberOfLanes());
                newLink.setAllowedModes(link.getAllowedModes());
                network.addLink(newLink);
            }

			// check for network cleaner
			if (Main.pref.getBoolean("matsim_cleanNetwork")) {
				this.progressMonitor.setTicks(2);
				this.progressMonitor.setCustomText("cleaning network..");
				new NetworkCleaner().run(network);
			}

			// write out paths
			this.progressMonitor.setTicks(3);
			this.progressMonitor.setCustomText("writing out xml file(s)..");
			new NetworkWriter(network).write(networkFile.getAbsolutePath());
			
			if (((MATSimLayer) layer).getMatsimScenario().getTransitSchedule() != null) {
				TransitSchedule oldSchedule = ((MATSimLayer) layer)
						.getMatsimScenario().getTransitSchedule();
				
				for (TransitStopFacility stop : oldSchedule.getFacilities()
						.values()) {
					
					TransitStopFacility newStop = schedule.getFactory()
							.createTransitStopFacility(
									stop.getId(), stop.getCoord(),
									stop.getIsBlockingLane());
					
					Id<Link> oldId = stop.getLinkId();
					Link oldLink = ((MATSimLayer) layer).getMatsimScenario()
							.getNetwork().getLinks().get(oldId);
					Id<Link> newLinkId = Id.createLinkId(((LinkImpl) oldLink)
							.getOrigId());
					newStop.setLinkId(newLinkId);
					schedule.addStopFacility(newStop);
				}
				
				for (TransitLine line : ((MATSimLayer) layer)
						.getMatsimScenario().getTransitSchedule()
						.getTransitLines().values()) {

					TransitLine newTLine;
					if (schedule.getTransitLines().containsKey(line.getId())) {
						newTLine = schedule.getTransitLines().get(line.getId());
					} else {
						newTLine = schedule.getFactory().createTransitLine(
								line.getId());
						schedule.addTransitLine(newTLine);
					}

					for (TransitRoute route : line.getRoutes().values()) {
						List<Id<Link>> links = new ArrayList<Id<Link>>();
						Id<Link> startLinkId = Id
								.createLinkId(((LinkImpl) ((MATSimLayer) layer)
										.getMatsimScenario().getNetwork()
										.getLinks()
										.get(route.getRoute().getStartLinkId()))
										.getOrigId());
						for (Id<Link> id : route.getRoute().getLinkIds()) {
							links.add(Id
									.createLinkId(((LinkImpl) ((MATSimLayer) layer)
											.getMatsimScenario().getNetwork()
											.getLinks().get(id)).getOrigId()));
						}
						Id<Link> endLinkId = Id
								.createLinkId(((LinkImpl) ((MATSimLayer) layer)
										.getMatsimScenario().getNetwork()
										.getLinks()
										.get(route.getRoute().getEndLinkId()))
										.getOrigId());
						NetworkRoute networkRoute = new LinkNetworkRouteImpl(
								startLinkId, endLinkId);
						networkRoute.setLinkIds(startLinkId, links, endLinkId);

						List<TransitRouteStop> newTRStops = new ArrayList<TransitRouteStop>();
						for (TransitRouteStop tRStop : route.getStops()) {
							newTRStops.add(schedule.getFactory()
									.createTransitRouteStop(
											schedule.getFacilities().get(tRStop.getStopFacility().getId()), tRStop.getArrivalOffset(), tRStop.getDepartureOffset()));
						}

						TransitRoute newTRoute = schedule.getFactory()
								.createTransitRoute(route.getId(),
										networkRoute, newTRStops,
										route.getTransportMode());
						newTLine.addRoute(newTRoute);
					}
				}
				new TransitScheduleWriter(schedule).writeFile(scheduleFile
						.getAbsolutePath());
			}
		}
	}
}