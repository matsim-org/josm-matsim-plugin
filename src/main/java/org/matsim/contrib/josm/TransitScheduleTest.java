package org.matsim.contrib.josm;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.matsim.api.core.v01.network.Network;
import org.matsim.pt.utils.TransitScheduleValidator;
import org.matsim.pt.utils.TransitScheduleValidator.ValidationResult;
import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.command.Command;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.OsmPrimitiveType;
import org.openstreetmap.josm.data.osm.Relation;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.data.validation.Severity;
import org.openstreetmap.josm.data.validation.Test;
import org.openstreetmap.josm.data.validation.TestError;
import org.openstreetmap.josm.gui.dialogs.relation.sort.WayConnectionType;
import org.openstreetmap.josm.gui.dialogs.relation.sort.WayConnectionTypeCalculator;
import org.openstreetmap.josm.gui.progress.ProgressMonitor;

public class TransitScheduleTest extends Test {

    private MATSimLayer layer;
    private Network network;
    private ValidationResult result;

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
	    layer = (MATSimLayer) Main.main.getActiveLayer();
	    this.network = layer.getScenario().getNetwork();
	}
	super.startTest(monitor);
//	result = TransitScheduleValidator.validateAll(layer.getScenario().getTransitSchedule(), network);

    }

    /**
     * Visits a relation and checks for connected routes.
     */
    public void visit(Relation r) {

	if (r.hasTag("type", "route") && r.hasKey("route")) {
	  
	    if (r.getMemberPrimitives(Way.class).isEmpty()) {
		String msg = ("Route has no ways");
		errors.add(new TestError(this, Severity.WARNING, msg, DOUBTFUL_ROUTE, Collections.singleton(r), r.getMemberPrimitives(Way.class)));
	    }
	    if (!waysConnected(r)) {
		String msg = ("Route is not fully connected");
		errors.add(new TestError(this, Severity.WARNING, msg, UNCONNECTED_WAYS, Collections.singleton(r), r.getMemberPrimitives(Way.class)));
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
	WayConnectionTypeCalculator calc = new WayConnectionTypeCalculator();
	List<WayConnectionType> connections = calc.updateLinks(relation.getMembers());
	List<OsmPrimitive> primitiveList = relation.getMemberPrimitivesList();
	boolean firstWayFound = false;
	boolean lastWayFound = false;
	for (Way way: relation.getMemberPrimitives(Way.class)) {
	    int i = primitiveList.indexOf(way);
	    if (connections.get(i).linkPrev && connections.get(i).linkNext) {
		continue;
	    } else if (connections.get(i).linkPrev && lastWayFound == false){
		lastWayFound = true;
		continue;
	    } else if (connections.get(i).linkNext && firstWayFound == false) {
		firstWayFound = true;
		continue;
	    } else {
		return false;
	    }
	}
	return true;
    }

    private void analyzeTransitSchedulevalidatorResult(ValidationResult result) {
	for (String warning : result.getWarnings()) {
	    List<Relation> r = new ArrayList<Relation>();
	    Matcher matcher = Pattern.compile("(\\+|-)?\\d+").matcher(warning);
	    while (matcher.find()) {

		Relation relation = (Relation) layer.data.getPrimitiveById(Long.parseLong(matcher.group()), OsmPrimitiveType.RELATION);
		if (relation != null) {
		    r.add((relation));
		}
		warning = warning.replace(matcher.group(), relation.hasKey("id") ? relation.get("id") : relation.get("ref"));
	    }
	    errors.add(new TestError(this, Severity.WARNING, warning, TRANSIT_SCHEDULE_VALIDATOR_WARNING, r, r));
	}

	for (String error : result.getErrors()) {
	    List<OsmPrimitive> primitives = new ArrayList<OsmPrimitive>();
	    Matcher matcher = Pattern.compile("[^_](\\+|-)?\\d+").matcher(error);
	    while (matcher.find()) {
		System.out.println(matcher.group().trim());
		Relation relation = (Relation) layer.data.getPrimitiveById(Long.parseLong(matcher.group().trim()), OsmPrimitiveType.RELATION);
		if (relation != null) {
		    primitives.add((relation));
		    error = error.replace(matcher.group(), relation.hasKey("id") ? relation.get("id") : relation.get("ref"));
		} else {
		    Way way = (Way) layer.data.getPrimitiveById(Long.parseLong(matcher.group().trim()), OsmPrimitiveType.WAY);
		    if (way != null) {
			primitives.add(way);
			error = error.replace(matcher.group(), way.hasKey("id") ? way.get("id") : matcher.group());
		    }
		}
	    }
	    errors.add(new TestError(this, Severity.ERROR, error, TRANSIT_SCHEDULE_VALIDATOR_ERROR, primitives, primitives));
	}
    }

    /**
     * Ends the test. Errors and warnings are created in this method.
     */
    @Override
    public void endTest() {
//	analyzeTransitSchedulevalidatorResult(result);
	super.endTest();
    }

    @Override
    public boolean isFixable(TestError testError) {
	return false;
    }

    @Override
    public Command fixError(TestError testError) {
	if (!isFixable(testError)) {
	    return null;
	}
	return null;// undoRedo handling done in mergeNodes

    }

}
