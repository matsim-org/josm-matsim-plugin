package org.matsim.contrib.josm.actions;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.contrib.josm.gui.OTFDialog;
import org.matsim.contrib.josm.model.Export;
import org.matsim.contrib.josm.model.NetworkModel;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.controler.AbstractModule;
import org.matsim.core.controler.Injector;
import org.matsim.core.events.EventsUtils;
import org.matsim.core.mobsim.framework.Mobsim;
import org.matsim.core.mobsim.qsim.QSim;
import org.matsim.core.mobsim.qsim.QSimModule;
import org.matsim.core.scenario.ScenarioByInstanceModule;
import org.matsim.pt.transitSchedule.api.Departure;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.utils.CreateVehiclesForSchedule;
import org.openstreetmap.josm.actions.JosmAction;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.tools.Shortcut;

public class OTFVisAction extends JosmAction {

	public OTFVisAction() {
		super(tr("Simulate"), null, tr("Simulate"), Shortcut.registerShortcut("menu:matsimConvert",
				tr("Menu: {0}", tr("Simulate")), KeyEvent.VK_G, Shortcut.ALT_CTRL), true);
	}

	@Override
	public void actionPerformed(ActionEvent e) {
		NetworkModel networkModel = NetworkModel.createNetworkModel(MainApplication.getLayerManager().getEditDataSet());
		networkModel.visitAll();
		Scenario scenario = Export.toScenario(networkModel);

		long departureId = 0;
		for (TransitLine transitLine : scenario.getTransitSchedule().getTransitLines().values()) {
			for (TransitRoute transitRoute : transitLine.getRoutes().values()) {
				for (double time = 0.0; time < 0.5 * 60 * 60; time += 10 * 60.0) {
					transitRoute.addDeparture(scenario.getTransitSchedule().getFactory().createDeparture(Id.create(departureId++, Departure.class), time));
				}
			}
		}
		new CreateVehiclesForSchedule(scenario.getTransitSchedule(), scenario.getTransitVehicles()).run();

		EventsManager eventsManager = EventsUtils.createEventsManager();
		com.google.inject.Injector injector = Injector.createInjector(scenario.getConfig(), new AbstractModule() {
			@Override
			public void install() {
				install(new ScenarioByInstanceModule(scenario));
				bind(EventsManager.class).toInstance(eventsManager);
				install(new QSimModule());
			}
		});
		QSim qSim = (QSim) injector.getInstance(Mobsim.class);
		OTFDialog otfDialog = new OTFDialog(scenario, eventsManager, qSim);
		otfDialog.setModal(false);
		otfDialog.setVisible(true);
		new Thread(qSim::run).start();
	}
}
