package org.matsim.contrib.josm;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.core.network.LinkImpl;
import org.matsim.core.network.NodeImpl;
import org.matsim.pt.utils.TransitScheduleValidator;
import org.matsim.pt.utils.TransitScheduleValidator.ValidationResult;
import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.command.Command;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.OsmPrimitiveType;
import org.openstreetmap.josm.data.osm.Relation;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.data.osm.WaySegment;
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
	result = TransitScheduleValidator.validateAll(layer.getScenario().getTransitSchedule(), network);

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
	analyzeTransitSchedulevalidatorResult(result);
	super.endTest();
    }

    @Override
    public boolean isFixable(TestError testError) {
	return false;
    }

    @Override
    public Command fixError(TestError testError) {
	return null;// undoRedo handling done in mergeNodes
    }

}
