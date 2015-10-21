package org.matsim.contrib.josm;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.matsim.api.core.v01.Id;
import org.matsim.contrib.josm.scenario.EditableTransitLine;
import org.matsim.contrib.josm.scenario.EditableTransitRoute;
import org.matsim.contrib.josm.scenario.EditableTransitSchedule;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;
import org.matsim.pt.utils.TransitScheduleValidator;
import org.matsim.pt.utils.TransitScheduleValidator.ValidationResult;
import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.command.Command;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.Relation;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.data.validation.Severity;
import org.openstreetmap.josm.data.validation.Test;
import org.openstreetmap.josm.data.validation.TestError;
import org.openstreetmap.josm.gui.dialogs.relation.sort.WayConnectionType;
import org.openstreetmap.josm.gui.dialogs.relation.sort.WayConnectionTypeCalculator;
import org.openstreetmap.josm.gui.progress.ProgressMonitor;

public class TransitScheduleTest extends Test {

    private EditableTransitSchedule schedule;
    private ValidationResult result;

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
     * Integer code for unconnected route ways
     */
    private final static int UNCONNECTED_WAYS = 3004;
    /**
     * Integer code for routes without ways
     */
    private final static int DOUBTFUL_ROUTE = 3005;
    /**
     * Integer code for warnings emerging from {@link TransitScheduleValidator}
     */
    private final static int TRANSIT_SCHEDULE_VALIDATOR_WARNING = 3006;
    /**
     * Integer code for errors emerging from {@link TransitScheduleValidator}
     */
    private final static int TRANSIT_SCHEDULE_VALIDATOR_ERROR = 3007;
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

	    this.schedule = ((MATSimLayer) Main.main.getActiveLayer()).getScenario().getTransitSchedule();
	    this.routeIds = new HashMap<>();
	    this.facilityIds = new HashMap<>();
	    this.lineIds = new HashMap<>();
	}
	super.startTest(monitor);
	// result =
	// TransitScheduleValidator.validateAll(layer.getScenario().getTransitSchedule(),
	// network);

    }

    /**
     * Visits a relation and checks for connected routes.
     */
    public void visit(Relation r) {

	if (r.hasTag("type", "route") && r.hasKey("route")) {

	    if (schedule != null) {

		EditableTransitRoute route = null;
		Id<EditableTransitRoute> id = Id.create(r.getUniqueId(), EditableTransitRoute.class);
		for (EditableTransitLine line : schedule.getEditableTransitLines().values()) {
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
	    }

	    if (r.getMemberPrimitives(Way.class).isEmpty()) {
		String msg = ("Route has no ways");
		errors.add(new TestError(this, Severity.WARNING, msg, DOUBTFUL_ROUTE, Collections.singleton(r), r.getMemberPrimitives(Way.class)));
	    }
	    if (!waysConnected(r)) {
		String msg = ("Route is not fully connected");
		errors.add(new TestError(this, Severity.WARNING, msg, UNCONNECTED_WAYS, Collections.singleton(r), r.getMemberPrimitives(Way.class)));
	    }
	}

	if (r.hasTag("type", "route_master")) {
	    if (schedule != null) {
		String id = String.valueOf(r.getUniqueId());
		Id<TransitLine> lineId = Id.create(id, TransitLine.class);
		EditableTransitLine line = schedule.getEditableTransitLines().get(lineId);
		if (line != null) {
		    Id<TransitLine> realId = line.getRealId();
		    if (!lineIds.containsKey(realId)) {
			lineIds.put(realId, new ArrayList<Relation>());
		    }
		    lineIds.get(realId).add(r);
		}
	    }
	}

	if (r.hasTag("type", "public_transport") && r.hasTag("public_transport", "stop_area")) {

	    if (schedule != null) {
		String id = String.valueOf(r.getUniqueId());
		Id<TransitStopFacility> transitStopFacilityId = Id.create(id, TransitStopFacility.class);
		TransitStopFacility facility = schedule.getFacilities().get(transitStopFacilityId);
		if (facility != null) {
		    Id<TransitStopFacility> realId = Id.create(facility.getName(), TransitStopFacility.class);

		    if (!facilityIds.containsKey(realId)) {
			facilityIds.put(realId, new ArrayList<Relation>());
		    }
		    facilityIds.get(realId).add(r);
		}
	    }

	}

    }

    /**
     * Checks whether a {@code way} is connected to other ways of a
     * {@code relation} (forwards and backwards)
     * 
     * @param way
     *            the {@code way} to be checked
     * @param relation
     *            the {@code relation} which describes the route
     * @return <code>true</code> if the {@code way} is connected to other ways,
     *         <code>false</code> otherwise
     */
    private static boolean waysConnected(Relation relation) {
	// TODO Auto-generated method stub
	if (!relation.getMembers().isEmpty()) {
	    WayConnectionTypeCalculator calc = new WayConnectionTypeCalculator();
	    List<WayConnectionType> connections = calc.updateLinks(relation.getMembers());
	    List<OsmPrimitive> primitiveList = relation.getMemberPrimitivesList();
	    boolean firstWayFound = false;
	    boolean lastWayFound = false;
	    for (Way way : relation.getMemberPrimitives(Way.class)) {
		int i = primitiveList.indexOf(way);
		if (connections.get(i).linkPrev && connections.get(i).linkNext) {
		    continue;
		} else if (connections.get(i).linkPrev && lastWayFound == false) {
		    lastWayFound = true;
		    continue;
		} else if (connections.get(i).linkNext && firstWayFound == false) {
		    firstWayFound = true;
		    continue;
		} else {
		    return false;
		}
	    }
	}
	return true;
    }

    // private void analyzeTransitSchedulevalidatorResult(ValidationResult
    // result) {
    // for (String warning : result.getWarnings()) {
    // List<Relation> r = new ArrayList<Relation>();
    // Matcher matcher = Pattern.compile("(\\+|-)?\\d+").matcher(warning);
    // while (matcher.find()) {
    //
    // Relation relation = (Relation)
    // layer.data.getPrimitiveById(Long.parseLong(matcher.group()),
    // OsmPrimitiveType.RELATION);
    // if (relation != null) {
    // r.add((relation));
    // }
    // warning = warning.replace(matcher.group(), relation.hasKey("id") ?
    // relation.get("id") : relation.get("ref"));
    // }
    // errors.add(new TestError(this, Severity.WARNING, warning,
    // TRANSIT_SCHEDULE_VALIDATOR_WARNING, r, r));
    // }
    //
    // for (String error : result.getErrors()) {
    // List<OsmPrimitive> primitives = new ArrayList<OsmPrimitive>();
    // Matcher matcher = Pattern.compile("[^_](\\+|-)?\\d+").matcher(error);
    // while (matcher.find()) {
    // System.out.println(matcher.group().trim());
    // Relation relation = (Relation)
    // layer.data.getPrimitiveById(Long.parseLong(matcher.group().trim()),
    // OsmPrimitiveType.RELATION);
    // if (relation != null) {
    // primitives.add((relation));
    // error = error.replace(matcher.group(), relation.hasKey("id") ?
    // relation.get("id") : relation.get("ref"));
    // } else {
    // Way way = (Way)
    // layer.data.getPrimitiveById(Long.parseLong(matcher.group().trim()),
    // OsmPrimitiveType.WAY);
    // if (way != null) {
    // primitives.add(way);
    // error = error.replace(matcher.group(), way.hasKey("id") ? way.get("id") :
    // matcher.group());
    // }
    // }
    // }
    // errors.add(new TestError(this, Severity.ERROR, error,
    // TRANSIT_SCHEDULE_VALIDATOR_ERROR, primitives, primitives));
    // }
    // }

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
	super.endTest();
    }

    @Override
    public boolean isFixable(TestError testError) {
	return testError.getCode() == DUPLICATE_LINE_ID || testError.getCode() == DUPLICATE_ROUTE_ID || testError.getCode() == DUPLICATE_FACILITY_ID;
    }

    @Override
    public Command fixError(TestError testError) {
	if (!isFixable(testError)) {
	    return null;
	}

	int j = 1;

	// go through all affected elements and adjust id with incremental
	// number
	if (testError.getCode() == DUPLICATE_LINE_ID) {
	    for (OsmPrimitive primitive : testError.getPrimitives()) {
		Id<TransitLine> id = Id.create(primitive.getUniqueId(), TransitLine.class);
		EditableTransitLine line = schedule.getEditableTransitLines().get(id);
		String realId = line.getRealId().toString();
		line.setRealId(Id.create((realId + "(" + j + ")"), TransitLine.class));
		j++;
	    }
	}

	if (testError.getCode() == DUPLICATE_ROUTE_ID) {
	    for (OsmPrimitive primitive : testError.getPrimitives()) {
		EditableTransitRoute route = null;
		Id<EditableTransitRoute> id = Id.create(primitive.getUniqueId(), EditableTransitRoute.class);
		for (EditableTransitLine line : schedule.getEditableTransitLines().values()) {
		    if (line.getEditableRoutes().containsKey(id)) {
			route = line.getEditableRoutes().get(id);
		    }
		}
		if (route != null) {
		    Id<TransitRoute> realId = route.getRealId();
		    route.setRealId(Id.create(realId + "(" + j + ")", TransitRoute.class));
		    j++;
		}
	    }
	}

	if (testError.getCode() == DUPLICATE_FACILITY_ID) {
	    for (OsmPrimitive primitive : testError.getPrimitives()) {
		Id<TransitStopFacility> id = Id.create(primitive.getUniqueId(), TransitStopFacility.class);
		TransitStopFacility facility = schedule.getFacilities().get(id);
		String realId = facility.getName();
		facility.setName(realId + "(" + j + ")");
		j++;
	    }
	}

	return null;// undoRedo handling done in mergeNodes

    }

}
