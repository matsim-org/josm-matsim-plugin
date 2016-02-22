package org.matsim.contrib.josm.scenario;

import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.population.Population;
import org.matsim.core.config.Config;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.facilities.ActivityFacilities;
import org.matsim.households.Households;
import org.matsim.lanes.data.v20.Lanes;
import org.matsim.vehicles.Vehicles;

public class EditableScenarioUtils {

    public static EditableScenario createScenario(Config config) {
        ScenarioUtils.ScenarioBuilder scenarioBuilder = new ScenarioUtils.ScenarioBuilder(config);
        scenarioBuilder.setTransitSchedule(new EditableTransitSchedule());
        final Scenario scenario = scenarioBuilder.build();
        return new EditableScenario() {
            @Override
            public EditableTransitSchedule getTransitSchedule() {
                return (EditableTransitSchedule) scenario.getTransitSchedule();
            }

            @Override
            public Network getNetwork() {
                return scenario.getNetwork();
            }

            @Override
            public Population getPopulation() {
                return scenario.getPopulation();
            }

            @Override
            public Config getConfig() {
                return scenario.getConfig();
            }

            @Override
            public void addScenarioElement(String s, Object o) {
                scenario.addScenarioElement(s, o);
            }

            @Override
            public Object getScenarioElement(String s) {
                return scenario.getScenarioElement(s);
            }

            @Override
            public ActivityFacilities getActivityFacilities() {
                return scenario.getActivityFacilities();
            }

            @Override
            public Vehicles getTransitVehicles() {
                return scenario.getTransitVehicles();
            }

            @Override
            public Vehicles getVehicles() {
                return scenario.getVehicles();
            }

            @Override
            public Households getHouseholds() {
                return scenario.getHouseholds();
            }

            @Override
            public Lanes getLanes() {
                return scenario.getLanes();
            }
        };
    }

}
