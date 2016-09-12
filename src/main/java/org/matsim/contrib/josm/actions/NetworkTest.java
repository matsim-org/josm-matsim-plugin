package org.matsim.contrib.josm.actions;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Network;
import org.matsim.contrib.josm.model.Export;
import org.matsim.contrib.josm.model.MATSimLayer;
import org.matsim.contrib.josm.model.MLink;
import org.matsim.contrib.josm.model.MNode;
import org.matsim.contrib.josm.model.NetworkModel;
import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.command.Command;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.data.osm.WaySegment;
import org.openstreetmap.josm.data.validation.Severity;
import org.openstreetmap.josm.data.validation.Test;
import org.openstreetmap.josm.data.validation.TestError;
import org.openstreetmap.josm.gui.progress.ProgressMonitor;

/**
 * The Test which is used for the validation of MATSim content.
 *
 * @author Nico
 *
 */
public class NetworkTest extends Test {

	/**
	 * Maps ways (the links, respectively) to their id. Ways whose links share
	 * the same id are added up in a list.
	 */
	private Map<String, ArrayList<Way>> linkIds;
	/**
	 * Maps nodes to their id. Nodes that share the same id are added up in a
	 * list.
	 */
	private Map<String, ArrayList<Node>> nodeIds;
	private Network network;

	/**
	 * Identifies the link id to be fixed from a specific error.
	 */
	private Map<TestError, String> links2Fix;

	/**
	 * Integer code for duplicated link id error.
	 */
	private final static int DUPLICATE_LINK_ID = 3001;
	/**
	 * Integer code for duplicated node id error.
	 */
	private final static int DUPLICATE_NODE_ID = 3002;
	/**
	 * Integer code for doubtful link attribute(s).
	 */
	private final static int DOUBTFUL_LINK_ATTRIBUTE = 3003;
	private NetworkModel networkModel;

	/**
	 * Creates a new {@code MATSimTest}.
	 */
	public NetworkTest() {
		super(tr("MATSimValidation"), tr("Validates MATSim-related network data"));
	}

	/**
	 * Starts the test. Initializes the mappings of {@link #nodeIds} and
	 * {@link #linkIds}.
	 */
	@Override
	public void startTest(ProgressMonitor monitor) {
		this.nodeIds = new HashMap<>();
		this.linkIds = new HashMap<>();
		if (Main.getLayerManager().getActiveLayer() instanceof MATSimLayer) {
			networkModel = ((MATSimLayer) Main.getLayerManager().getActiveLayer()).getNetworkModel();
			Scenario scenario = Export.toScenario(networkModel);
			this.network = scenario.getNetwork();
			super.startTest(monitor);
		}
	}

	/**
	 * Visits a way and stores the Ids of the represented links. Also checks
	 * links for {@link #doubtfulAttributes(MLink)}.
	 */
	@Override
	public void visit(Way w) {
		if (this.network != null) {
			for (MLink link : networkModel.getWay2Links().get(w)) {
				String origId = link.getOrigId();
				if (!linkIds.containsKey(origId)) {
					linkIds.put(origId, new ArrayList<>());
				}

				linkIds.get(origId).add(w);

				if (doubtfulAttributes(link)) {
					String msg = ("Link contains doubtful attributes");
					Collection<Way> way = Collections.singleton(w);
					errors.add(new TestError(this, Severity.WARNING, msg, DOUBTFUL_LINK_ATTRIBUTE, way, way));
				}
			}
		}
	}

	/**
	 * Visits a node and stores it's Id.
	 */
	@Override
	public void visit(Node n) {
		if (this.network != null) {
			MNode node = networkModel.nodes().get(n);
			if (node != null) {
				String origId = node.getOrigId();
				if (!nodeIds.containsKey(origId)) {
					nodeIds.put(origId, new ArrayList<>());
				}
				nodeIds.get(origId).add(n);
			}
		}
	}

	/**
	 * Checks whether a {@code link} has doubtful link attributes (freespeed,
	 * capacity, length or number of lanes set to 0.)
	 *
	 * @param link
	 *            the {@code link} to be checked
	 * @return <code>true</code> if the {@code link} contains doubtful link
	 *         attributes. <code>false</code> otherwise
	 */
	private boolean doubtfulAttributes(MLink link) {
		return link.getFreespeed() == 0 || link.getCapacity() == 0 || link.getLength() == 0 || link.getNumberOfLanes() == 0;
	}

	/**
	 * Ends the test. Errors and warnings are created in this method.
	 */
	@Override
	public void endTest() {

		links2Fix = new HashMap<>();
		for (Entry<String, ArrayList<Way>> entry : linkIds.entrySet()) {
			if (entry.getValue().size() > 1) {
				List<WaySegment> segments = new ArrayList<>();
				for (Way way : entry.getValue()) {
					List<MLink> links = networkModel.getWay2Links().get(way);
					for (MLink link : links) {
						if (link.getOrigId().equalsIgnoreCase(entry.getKey())) {
							segments.addAll(link.getSegments());
						}
					}
				}

				// create error with message
				String msg = "Duplicated Id " + (entry.getKey() + " not allowed.");
				TestError error = new TestError(this, Severity.ERROR, msg, DUPLICATE_LINK_ID, entry.getValue(), segments);
				errors.add(error);
				links2Fix.put(error, entry.getKey());
			}

		}
		for (Entry<String, ArrayList<Node>> entry : nodeIds.entrySet()) {
			if (entry.getValue().size() > 1) {

				// create warning with message
				String msg = "Duplicated Id " + (entry.getKey() + " not allowed.");
				TestError error = new TestError(this, Severity.ERROR, msg, DUPLICATE_NODE_ID, entry.getValue(), entry.getValue());
				errors.add(error);

			}
		}
		super.endTest();
		network = null;
		linkIds = null;
		nodeIds = null;
	}

	@Override
	public boolean isFixable(TestError testError) {
		return testError.getCode() == DUPLICATE_LINK_ID || testError.getCode() == DUPLICATE_NODE_ID;
	}

	@Override
	public Command fixError(TestError testError) {
		if (!isFixable(testError)) {
			return null;
		}
		if (testError.getCode() == 3001 || testError.getCode() == 3002) {
			int i = 1;
			int j = 1;
			// go through all affected elements and adjust id with incremental
			// number
			for (OsmPrimitive primitive : testError.getPrimitives()) {
				if (primitive instanceof Way) {
					if (links2Fix.containsKey(testError)) {
						String id2Fix = links2Fix.get(testError);
						for (MLink link : networkModel.getWay2Links().get(primitive)) {
							if (link.getOrigId().equalsIgnoreCase(id2Fix)) {
								link.setOrigId(id2Fix + "(" + i + ")");
								i++;
							}
						}
					}
				} else if (primitive instanceof Node) {
					MNode node = this.networkModel.nodes().get(primitive);
					String origId = node.getOrigId();
					node.setOrigId(origId + "(" + j + ")");
					j++;
				}
			}

		}
		return null;// undoRedo handling done in mergeNodes
	}
}
