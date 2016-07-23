package org.matsim.contrib.josm.actions;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.contrib.josm.model.*;
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
import org.openstreetmap.josm.data.validation.Severity;
import org.openstreetmap.josm.data.validation.Test;
import org.openstreetmap.josm.data.validation.TestError;
import org.openstreetmap.josm.gui.progress.ProgressMonitor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import static org.openstreetmap.josm.tools.I18n.tr;

public class TransitScheduleTest extends Test {

	private NetworkModel networkModel;

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

	private final static int MATSIM_ERROR_MESSAGE = 3333;

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
		if (Main.getLayerManager().getActiveLayer() instanceof MATSimLayer) {
			this.networkModel = ((MATSimLayer) Main.getLayerManager().getActiveLayer()).getNetworkModel();
		} else {
			Config config = ConfigUtils.createConfig();
			config.transit().setUseTransit(true);
			NetworkModel networkModel = NetworkModel.createNetworkModel(Main.main.getCurrentDataSet());
			networkModel.visitAll();
			this.networkModel = networkModel;
		}
		super.startTest(monitor);
	}

	@Override
	public void endTest() {

		Map<Id<TransitLine>, List<Line>> lineIds = networkModel.lines().values().stream().collect(Collectors.groupingBy(Line::getMatsimId));
		for (Entry<Id<TransitLine>, List<Line>> entry : lineIds.entrySet()) {
			if (entry.getValue().size() > 1) {
				// create warning with message
				String msg = "Duplicated Id " + (entry.getKey() + " not allowed.");
				List<OsmPrimitive> relations = entry.getValue().stream().map(Line::getRelation).collect(Collectors.toList());
				TestError error = new TestError(this, Severity.ERROR, msg, DUPLICATE_LINE_ID, relations, relations);
				errors.add(error);
			}
		}

		Map<Id<TransitRoute>, List<Route>> routeIds = networkModel.routes().values().stream().collect(Collectors.groupingBy(route -> Id.create(route.getId(), TransitRoute.class)));
		for (Entry<Id<TransitRoute>, List<Route>> entry : routeIds.entrySet()) {
			if (entry.getValue().size() > 1) {

				// create warning with message
				String msg = "Duplicated Id " + (entry.getKey() + " not allowed.");
				List<OsmPrimitive> relations = entry.getValue().stream().map(Route::getRelation).collect(Collectors.toList());
				TestError error = new TestError(this, Severity.ERROR, msg, DUPLICATE_ROUTE_ID, relations, relations);
				errors.add(error);

			}
		}

		Map<Id<TransitStopFacility>, List<StopArea>> facilityIds = networkModel.stopAreas().values().stream().collect(Collectors.groupingBy(StopArea::getMatsimId));
		for (Entry<Id<TransitStopFacility>, List<StopArea>> entry : facilityIds.entrySet()) {
			if (entry.getValue().size() > 1) {

				// create warning with message
				String msg = "Duplicated Id " + (entry.getKey() + " not allowed.");
				List<OsmPrimitive> relations = entry.getValue().stream().map(StopArea::getRelation).collect(Collectors.toList());

				TestError error = new TestError(this, Severity.ERROR, msg, DUPLICATE_FACILITY_ID, relations, relations);
				errors.add(error);

			}
		}
		if (errors.isEmpty()) { // Otherwise, it is possible that we have a condition where Export would throw an Exception
			// We continue validation with a preview of the real, exported TransitSchedule, so that the ids
			// in the error messages are the ones in the XML.
			Scenario targetScenario = Export.toScenario(networkModel);
			ValidationResult validationResult = TransitScheduleValidator.validateAll(targetScenario.getTransitSchedule(), targetScenario.getNetwork());
			for (String errorString : validationResult.getWarnings()) {
				errors.add(new TestError(this, Severity.WARNING, errorString, MATSIM_ERROR_MESSAGE, Collections.<OsmPrimitive>emptyList()));
			}
			for (String errorString : validationResult.getErrors()) {
				// We call them only Warnings in JOSM because it still can be exported to XML.
				errors.add(new TestError(this, Severity.WARNING, errorString, MATSIM_ERROR_MESSAGE, Collections.<OsmPrimitive>emptyList()));
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
		List<Command> commands = new ArrayList<>();

		int j = 1;

		// go through all affected elements and adjust id with incremental
		// number
		if (testError.getCode() == DUPLICATE_LINE_ID) {
			for (OsmPrimitive primitive : testError.getPrimitives()) {
				Line line = networkModel.lines().get(primitive);
				commands.add(new ChangePropertyCommand(primitive, "ref", (line.getMatsimId().toString() + "(" + j + ")")));
				j++;
			}
		}

		if (testError.getCode() == DUPLICATE_ROUTE_ID) {
			for (OsmPrimitive primitive : testError.getPrimitives()) {
				Route route = networkModel.routes().get(primitive);
				commands.add(new ChangePropertyCommand(primitive, "ref", (route.getId() + "(" + j + ")")));
				j++;
			}
		}

		if (testError.getCode() == DUPLICATE_FACILITY_ID) {
			for (OsmPrimitive primitive : testError.getPrimitives()) {
				StopArea stopArea = networkModel.stopAreas().get(primitive);
				commands.add(new ChangePropertyCommand(primitive, "ref", (stopArea.getMatsimId().toString() + "(" + j + ")")));
				j++;
			}
		}

		return new SequenceCommand(tr("Fix transit schedule validation errors"), commands);
	}

}
