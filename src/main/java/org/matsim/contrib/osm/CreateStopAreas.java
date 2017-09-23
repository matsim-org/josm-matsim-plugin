package org.matsim.contrib.osm;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.openstreetmap.josm.command.AddCommand;
import org.openstreetmap.josm.command.ChangeCommand;
import org.openstreetmap.josm.command.Command;
import org.openstreetmap.josm.command.SequenceCommand;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.Relation;
import org.openstreetmap.josm.data.osm.RelationMember;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.data.validation.Severity;
import org.openstreetmap.josm.data.validation.Test;
import org.openstreetmap.josm.data.validation.TestError;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.progress.ProgressMonitor;

public class CreateStopAreas extends Test {

	/**
	 * Maps platforms without stop areas to their name. Platforms who share the
	 * same name are added up in a list.
	 */
	private Map<String, ArrayList<OsmPrimitive>> stops;

	/**
	 * Maps existing master routes to their ref id.
	 */
	private Map<String, Relation> stopAreas;

	/**
	 * Integer code for routes without master routes.
	 */
	private final static int MISSING_STOP_AREA = 3009;

	/**
	 * /** Creates a new {@code MATSimTest}.
	 */
	public CreateStopAreas() {
		super(tr("CreateStopAreas"), tr("CreateStopAreas"));
	}

	@Override
	public void startTest(ProgressMonitor monitor) {
		this.stops = new HashMap<>();
		this.stopAreas = new HashMap<>();
		super.startTest(monitor);
	}


	@Override
	public void visit(Way w) {

		if (w.isUsable()) {
			if (w.hasTag("public_transport", "platform")) {
				Relation stopArea = null;
				for (OsmPrimitive referrer : w.getReferrers()) {
					if (referrer instanceof Relation
							&& referrer.hasTag("type", "public_transport") && referrer.hasTag("public_transport", "stop_area")) {
						stopArea = (Relation) referrer;
						break;
					}
				}
				if (stopArea == null) {

					if(w.hasKey("name")) {
						if(stops.containsKey(w.getName())) {
							stops.get(w.getName()).add(w);
						} else {
							stops.put(w.getName(), new ArrayList<OsmPrimitive>());
							stops.get(w.getName()).add(w);
						}
					} else {
						stops.put(String.valueOf(w.getUniqueId()), new ArrayList<OsmPrimitive>());
						stops.get(String.valueOf(w.getUniqueId())).add(w);
					}
				} else {
					stopAreas.put(stopArea.getName(), stopArea);
				}
			}
		}
	}

	public void visit(Node n) {

		if (n.isUsable()) {
			if (n.hasTag("public_transport", "platform")) {
				Relation stopArea = null;
				for (OsmPrimitive referrer : n.getReferrers()) {
					if (referrer instanceof Relation
							&& referrer.hasTag("type", "public_transport") && referrer.hasTag("public_transport", "stop_area")) {
						stopArea = (Relation) referrer;
						break;
					}
				}
				if (stopArea == null) {
					if(n.hasKey("name")) {
						if(stops.containsKey(n.getName())) {
							stops.get(n.getName()).add(n);
						} else {
							stops.put(n.getName(), new ArrayList<OsmPrimitive>());
							stops.get(n.getName()).add(n);
						}
					} else {
						stops.put(String.valueOf(n.getUniqueId()), new ArrayList<OsmPrimitive>());
						stops.get(String.valueOf(n.getUniqueId())).add(n);
					}
				} else {
					stopAreas.put(stopArea.getName(), stopArea);
				}
			} else if (n.hasTag("public_transport", "stop_position")) {
				Relation stopArea = null;
				for (OsmPrimitive referrer : n.getReferrers()) {
					if (referrer instanceof Relation
							&& referrer.hasTag("type", "public_transport") && referrer.hasTag("public_transport", "stop_area")) {
						stopArea = (Relation) referrer;
						break;
					}
				}
				if (stopArea == null) {
					if(n.hasKey("name")) {
						if(stops.containsKey(n.getName())) {
							stops.get(n.getName()).add(n);
						} else {
							stops.put(n.getName(), new ArrayList<OsmPrimitive>());
							stops.get(n.getName()).add(n);
						}
					} else {
						stops.put(String.valueOf(n.getUniqueId()), new ArrayList<OsmPrimitive>());
						stops.get(String.valueOf(n.getUniqueId())).add(n);
					}
				} else {
					stopAreas.put(stopArea.getName(), stopArea);
				}

			}
		}
	}


	/**
	 * Ends the test. Errors and warnings are created in this method.
	 */
	@Override
	public void endTest() {

		for(Entry<String, ArrayList<OsmPrimitive>> entry: stops.entrySet()) {
			String msg = ("Platform / stop position "+entry.getKey()+"  could be a member of a stop area");
			TestError error = TestError.builder(this, Severity.WARNING,  MISSING_STOP_AREA).message(msg).primitives(entry.getValue()).build();
			errors.add(error);
		}
		super.endTest();
	}

	@Override
	public boolean isFixable(TestError testError) {
		return testError.getCode() == MISSING_STOP_AREA;
	}

	@Override
	public Command fixError(TestError testError) {
		if (!isFixable(testError)) {
			return null;
		}
		List<Command> commands = new ArrayList<>();
		if (testError.getCode() == 3009) {


			Relation stopArea = new Relation();
			stopArea.put("type", "public_transport");
			stopArea.put("public_transport", "stop_area");


			for(OsmPrimitive stop: testError.getPrimitives()) {
				stopArea.put("name", stop.getName());
				stopArea.put("ref", stop.get("ref"));
				if(stop.get("public_transport").equals("platform")) {
					stopArea.addMember(new RelationMember("platform", stop));
				} else if(stop.get("public_transport").equals("stop_position")) {
					stopArea.addMember(new RelationMember("stop", stop));
				}
			}

			if(stopAreas.containsKey(stopArea.getName())) {
				Relation oldStopArea = stopAreas.get(stopArea.getName());
				Relation newStopArea = new Relation(oldStopArea);
				for(OsmPrimitive stop: testError.getPrimitives()) {
					newStopArea.addMember(new RelationMember("platform", stop));
				}
				newStopArea.getMembers().addAll(stopArea.getMembers());
				commands.add(new ChangeCommand(MainApplication.getLayerManager().getEditLayer().data, oldStopArea, newStopArea));
			} else {
				commands.add(new AddCommand(MainApplication.getLayerManager().getEditLayer().data, stopArea));
			}
			return new SequenceCommand(name, commands);
		}
		return null;
	}

}
