package org.matsim.contrib.josm.actions;

import com.jogamp.opengl.GLAutoDrawable;
import org.matsim.api.core.v01.Id;
import org.matsim.contrib.josm.model.Export;
import org.matsim.contrib.josm.model.NetworkModel;
import org.matsim.contrib.josm.scenario.EditableScenario;
import org.matsim.contrib.otfvis.OTFVis;
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
import org.matsim.vis.otfvis.OTFClient;
import org.matsim.vis.otfvis.OTFClientControl;
import org.matsim.vis.otfvis.OTFVisConfigGroup;
import org.matsim.vis.otfvis.OnTheFlyServer;
import org.matsim.vis.otfvis.caching.SimpleSceneLayer;
import org.matsim.vis.otfvis.data.OTFClientQuadTree;
import org.matsim.vis.otfvis.data.OTFConnectionManager;
import org.matsim.vis.otfvis.data.OTFServerQuadTree;
import org.matsim.vis.otfvis.data.fileio.SettingsSaver;
import org.matsim.vis.otfvis.gui.OTFHostControlBar;
import org.matsim.vis.otfvis.gui.OTFQueryControl;
import org.matsim.vis.otfvis.gui.OTFQueryControlToolBar;
import org.matsim.vis.otfvis.handler.FacilityDrawer;
import org.matsim.vis.otfvis.handler.OTFAgentsListHandler;
import org.matsim.vis.otfvis.handler.OTFLinkAgentsHandler;
import org.matsim.vis.otfvis.opengl.drawer.OTFOGLDrawer;
import org.matsim.vis.otfvis.opengl.layer.OGLSimpleQuadDrawer;
import org.matsim.vis.otfvis.opengl.layer.OGLSimpleStaticNetLayer;
import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.actions.JosmAction;
import org.openstreetmap.josm.tools.Shortcut;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;

import static org.openstreetmap.josm.tools.I18n.tr;

public class OTFVisAction extends JosmAction {

	public OTFVisAction() {
		super(tr("Simulate"), null, tr("Simulate"), Shortcut.registerShortcut("menu:matsimConvert",
				tr("Menu: {0}", tr("Simulate")), KeyEvent.VK_G, Shortcut.ALT_CTRL), true);
	}

	@Override
	public void actionPerformed(ActionEvent e) {
		NetworkModel networkModel = NetworkModel.createNetworkModel(Main.getLayerManager().getEditDataSet());
		networkModel.visitAll();
		EditableScenario scenario = Export.toScenario(networkModel);

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
		QSim instance = (QSim) injector.getInstance(Mobsim.class);
		OnTheFlyServer server = OTFVis.startServerAndRegisterWithQSim(scenario.getConfig(), scenario, injector.getInstance(EventsManager.class), instance);
		SettingsSaver saver = new SettingsSaver("otfsettings");
		OTFVisConfigGroup visconf = saver.tryToReadSettingsFile();
		if (visconf == null) {
			visconf = server.getOTFVisConfig();
		}

		OTFConnectionManager connectionManager = new OTFConnectionManager();
		connectionManager.connectLinkToWriter(OTFLinkAgentsHandler.Writer.class);
		connectionManager.connectWriterToReader(OTFLinkAgentsHandler.Writer.class, OTFLinkAgentsHandler.class);
		connectionManager.connectReaderToReceiver(OTFLinkAgentsHandler.class, OGLSimpleQuadDrawer.class);
		connectionManager.connectReceiverToLayer(OGLSimpleQuadDrawer.class, OGLSimpleStaticNetLayer.class);
		connectionManager.connectWriterToReader(OTFAgentsListHandler.Writer.class, OTFAgentsListHandler.class);
		if (scenario.getConfig().transit().isUseTransit()) {
			connectionManager.connectWriterToReader(FacilityDrawer.Writer.class, FacilityDrawer.Reader.class);
			connectionManager.connectReaderToReceiver(FacilityDrawer.Reader.class, FacilityDrawer.DataDrawer.class);
			connectionManager.connectReceiverToLayer(FacilityDrawer.DataDrawer.class, SimpleSceneLayer.class);
		}


		GLAutoDrawable canvas = OTFOGLDrawer.createGLCanvas(visconf);
		OTFClient otfClient = new OTFClient(canvas);
		otfClient.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE); // Default is "EXIT_ON_CLOSE" in MATSim 0.8.0
		otfClient.setServer(server);
		OTFClientControl.getInstance().setOTFVisConfig(visconf); // has to be set before OTFClientQuadTree.getConstData() is invoked!
		OTFServerQuadTree serverQuadTree = server.getQuad(connectionManager);
		OTFClientQuadTree clientQuadTree = serverQuadTree.convertToClient(server, connectionManager);
		clientQuadTree.getConstData();
		OTFHostControlBar hostControlBar = otfClient.getHostControlBar();

		OTFOGLDrawer mainDrawer = new OTFOGLDrawer(clientQuadTree, hostControlBar, visconf, canvas);
		OTFQueryControl queryControl = new OTFQueryControl(server, hostControlBar, visconf);
		OTFQueryControlToolBar queryControlBar = new OTFQueryControlToolBar(queryControl, visconf);
		queryControl.setQueryTextField(queryControlBar.getTextField());
		otfClient.getContentPane().add(queryControlBar, BorderLayout.SOUTH);
		mainDrawer.setQueryHandler(queryControl);
		otfClient.addDrawerAndInitialize(mainDrawer, saver);
		otfClient.pack();
		otfClient.setVisible(true);
		new Thread(() -> {
			instance.run();
			SwingUtilities.invokeLater(otfClient::dispose);
		}).start();
	}
}
