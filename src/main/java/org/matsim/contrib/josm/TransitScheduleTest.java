package org.matsim.contrib.josm;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.contrib.josm.scenario.EditableScenario;
import org.matsim.contrib.josm.scenario.EditableScenarioUtils;
import org.matsim.contrib.josm.scenario.EditableTransitLine;
import org.matsim.contrib.josm.scenario.EditableTransitRoute;
import org.matsim.contrib.josm.scenario.EditableTransitStopFacility;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;
import org.matsim.pt.utils.TransitScheduleValidator;
import org.matsim.pt.utils.TransitScheduleValidator.ValidationResult;
import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.command.ChangePropertyCommand;
import org.openstreetmap.josm.command.Command;
import org.openstreetmap.josm.command.SequenceCommand;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.Relation;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.data.osm.WaySegment;
import org.openstreetmap.josm.data.validation.Severity;
import org.openstreetmap.josm.data.validation.Test;
import org.openstreetmap.josm.data.validation.TestError;
import org.openstreetmap.josm.gui.progress.ProgressMonitor;

public class TransitScheduleTest extends Test {

	private EditableScenario scenario;

	/**
	 * Maps routes to their id. Routes that share the same id are added up in a
	 * list.
	 */
	private Map<Id<TransitRoute>, ArrayList<Relation>> routeIds;

	/**
	 * Maps routes to their id. Routes that share the same id are added up in a
	 * list.
	 */
	private Map<Id<TransitLine>, ArrayList<Relation>> lineIds;

	/**
	 * Maps facilities to their id. Facilities that share the same id are added
	 * up in a list.
	 */
	private Map<Id<TransitStopFacility>, ArrayList<Relation>> facilityIds;

	/**
	 * Integer code for routes without ways
	 */
	private final static int DOUBTFUL_ROUTE = 3005;
	/**
	 * Integer code for duplicated id errors
	 */
	private final static int DUPLICATE_ROUTE_ID = 3008;
	/**
	 * Integer code for duplicated id errors
	 */
	private final static int DUPLICATE_FACILITY_ID = 3009;
	/**
	 * Integer code for duplicated id errors
	 */
	private final static int DUPLICATE_LINE_ID = 3010;

	/**
	 * Creates a new {@code TransitScheduleTest}.
	 */
	public TransitScheduleTest() {
		super(tr("MATSimValidation"), tr("Validates MATSim-related transit schedule data"));
	}

	/**
	 * Starts the test.
	 */
	@Override
	public void startTest(ProgressMonitor monitor) {
		if (Main.main.getActiveLayer() instanceof MATSimLayer) {
			this.scenario = ((MATSimLayer) Main.main.getActiveLayer()).getScenario();
		} else {
			Config config = ConfigUtils.createConfig();
			config.transit().setUseTransit(true);
			this.scenario = EditableScenarioUtils.createScenario(config);
			NetworkListener networkListener = new NetworkListener(Main.main.getCurrentDataSet(), scenario, new HashMap<Way, List<Link>>(), new HashMap<Link, List<WaySegment>>(),
					new HashMap<Relation, TransitStopFacility>());
			networkListener.visitAll();
		}
		this.routeIds = new HashMap<>();
		this.facilityIds = new HashMap<>();
		this.lineIds = new HashMap<>();
		super.startTest(monitor);
	}

	/**
	 * Visits a relation and checks for connected routes.
	 */
	public void visit(Relation r) {

		if (r.hasTag("type", "route") && r.hasKey("route")) {
			EditableTransitRoute route = null;
			Id<EditableTransitRoute> id = Id.create(r.getUniqueId(), EditableTransitRoute.class);
			for (EditableTransitLine line : scenario.getTransitSchedule().getEditableTransitLines().values()) {
				if (line.getEditableRoutes().containsKey(id)) {
					route = line.getEditableRoutes().get(id);
				}
			}
			if (route != null) {
				Id<TransitRoute> realId = route.getRealId();

				if (!routeIds.containsKey(realId)) {
					routeIds.put(realId, new ArrayList<Relation>());
				}
				routeIds.get(realId).add(r);
			}
			if (! Preferences.isTransitLite()) {
				if (r.getMemberPrimitives(Way.class).isEmpty()) {
					String msg = ("Route has no ways");
					errors.add(new TestError(this, Severity.WARNING, msg, DOUBTFUL_ROUTE, Collections.singleton(r), r.getMemberPrimitives(Way.class)));
				}
			}
		}

		if (r.hasTag("type", "route_master")) {
			String id = String.valueOf(r.getUniqueId());
			Id<TransitLine> lineId = Id.create(id, TransitLine.class);
			EditableTransitLine line = scenario.getTransitSchedule().getEditableTransitLines().get(lineId);
			if (line != null) {
				Id<TransitLine> realId = line.getRealId();
				if (!lineIds.containsKey(realId)) {
					lineIds.put(realId, new ArrayList<Relation>());
				}
				lineIds.get(realId).add(r);
			}
		}

		if (r.hasTag("type", "public_transport") && r.hasTag("public_transport", "stop_area")) {
			String id = String.valueOf(r.getUniqueId());
			Id<TransitStopFacility> transitStopFacilityId = Id.create(id, TransitStopFacility.class);
			EditableTransitStopFacility facility = ((EditableTransitStopFacility) scenario.getTransitSchedule().getFacilities().get(transitStopFacilityId));
			if (facility != null) {
				Id<TransitStopFacility> realId = facility.getOrigId();
				if (!facilityIds.containsKey(realId)) {
					facilityIds.put(realId, new ArrayList<Relation>());
				}
				facilityIds.get(realId).add(r);
			}
		}

	}
	
	/**
	 * Ends the test. Errors and warnings are created in this method.
	 */
	@Override
	public void endTest() {
		// analyzeTransitSchedulevalidatorResult(result);
		for (Entry<Id<TransitLine>, ArrayList<Relation>> entry : lineIds.entrySet()) {
			if (entry.getValue().size() > 1) {

				// create warning with message
				String msg = "Duplicated Id " + (entry.getKey() + " not allowed.");
				TestError error = new TestError(this, Severity.ERROR, msg, DUPLICATE_LINE_ID, entry.getValue(), entry.getValue());
				errors.add(error);
			}
		}

		for (Entry<Id<TransitRoute>, ArrayList<Relation>> entry : routeIds.entrySet()) {
			if (entry.getValue().size() > 1) {

				// create warning with message
				String msg = "Duplicated Id " + (entry.getKey() + " not allowed.");
				TestError error = new TestError(this, Severity.ERROR, msg, DUPLICATE_ROUTE_ID, entry.getValue(), entry.getValue());
				errors.add(error);

			}
		}

		for (Entry<Id<TransitStopFacility>, ArrayList<Relation>> entry : facilityIds.entrySet()) {
			if (entry.getValue().size() > 1) {

				// create warning with message
				String msg = "Duplicated Id " + (entry.getKey() + " not allowed.");
				TestError error = new TestError(this, Severity.ERROR, msg, DUPLICATE_FACILITY_ID, entry.getValue(), entry.getValue());
				errors.add(error);

			}
		}

		ValidationResult validationResult = TransitScheduleValidator.validateAll(scenario.getTransitSchedule(), scenario.getNetwork());
		TransitScheduleValidator.printResult(validationResult);
		super.endTest();
	}

	@Override
	public boolean isFixable(TestError testError) {
		return testError.getCode() == DUPLICATE_LINE_ID || testError.getCode() == DUPLICATE_ROUTE_ID || testError.getCode() == DUPLICATE_FACILITY_ID;
	}

	@Override
	public Command fixError(TestError testError) {
		List<Command> commands = new ArrayList<>();

		int j = 1;

		// go through all affected elements and adjust id with incremental
		// number
		if (testError.getCode() == DUPLICATE_LINE_ID) {
			for (OsmPrimitive primitive : testError.getPrimitives()) {
				Id<TransitLine> id = Id.create(primitive.getUniqueId(), TransitLine.class);
				EditableTransitLine line = scenario.getTransitSchedule().getEditableTransitLines().get(id);
				String realId = line.getRealId().toString();
				commands.add(new ChangePropertyCommand(primitive, "ref", (realId + "(" + j + ")")));
				j++;
			}
		}

		if (testError.getCode() == DUPLICATE_ROUTE_ID) {
			for (OsmPrimitive primitive : testError.getPrimitives()) {
				EditableTransitRoute route = null;
				Id<EditableTransitRoute> id = Id.create(primitive.getUniqueId(), EditableTransitRoute.class);
				for (EditableTransitLine line : scenario.getTransitSchedule().getEditableTransitLines().values()) {
					if (line.getEditableRoutes().containsKey(id)) {
						route = line.getEditableRoutes().get(id);
					}
				}
				if (route != null) {
					Id<TransitRoute> realId = route.getRealId();
					commands.add(new ChangePropertyCommand(primitive, "ref", (realId + "(" + j + ")")));
					j++;
				}
			}
		}

		if (testError.getCode() == DUPLICATE_FACILITY_ID) {
			for (OsmPrimitive primitive : testError.getPrimitives()) {
				Id<TransitStopFacility> id = Id.create(primitive.getUniqueId(), TransitStopFacility.class);
				EditableTransitStopFacility facility = scenario.getTransitSchedule().getEditableFacilities().get(id);
				String realId = facility.getOrigId().toString();
				commands.add(new ChangePropertyCommand(primitive, "ref", (realId + "(" + j + ")")));
				j++;
			}
		}

		return new SequenceCommand(tr("Fix transit schedule validation errors"), commands);
	}

}
