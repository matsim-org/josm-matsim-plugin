package org.matsim.contrib.josm.scenario;

import org.matsim.api.core.v01.Scenario;

public interface EditableScenario extends Scenario {

    @Override
    EditableTransitSchedule getTransitSchedule();

}
