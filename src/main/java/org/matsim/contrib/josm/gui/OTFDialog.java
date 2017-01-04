/* *********************************************************************** *
 * project: org.matsim.*
 * OTFClient
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2009 by the members listed in the COPYING,        *
 *                   LICENSE and WARRANTY file.                            *
 * email           : info at matsim dot org                                *
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 *   This program is free software; you can redistribute it and/or modify  *
 *   it under the terms of the GNU General Public License as published by  *
 *   the Free Software Foundation; either version 2 of the License, or     *
 *   (at your option) any later version.                                   *
 *   See also COPYING, LICENSE and WARRANTY file                           *
 *                                                                         *
 * *********************************************************************** */
package org.matsim.contrib.josm.gui;

import org.matsim.api.core.v01.Scenario;
import org.matsim.contrib.otfvis.OTFVis;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.mobsim.qsim.QSim;
import org.matsim.vis.otfvis.OTFClientControl;
import org.matsim.vis.otfvis.OTFVisConfigGroup;
import org.matsim.vis.otfvis.OnTheFlyServer;
import org.matsim.vis.otfvis.caching.SimpleSceneLayer;
import org.matsim.vis.otfvis.data.OTFClientQuadTree;
import org.matsim.vis.otfvis.data.OTFConnectionManager;
import org.matsim.vis.otfvis.data.OTFServerQuadTree;
import org.matsim.vis.otfvis.data.fileio.SettingsSaver;
import org.matsim.vis.otfvis.gui.OTFControlBar;
import org.matsim.vis.otfvis.gui.OTFHostControl;
import org.matsim.vis.otfvis.gui.OTFQueryControl;
import org.matsim.vis.otfvis.gui.OTFQueryControlToolBar;
import org.matsim.vis.otfvis.handler.FacilityDrawer;
import org.matsim.vis.otfvis.handler.OTFAgentsListHandler;
import org.matsim.vis.otfvis.handler.OTFLinkAgentsHandler;
import org.matsim.vis.otfvis.opengl.drawer.OTFOGLDrawer;
import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.gui.ExtendedDialog;

import javax.swing.*;
import java.awt.*;


public final class OTFDialog extends ExtendedDialog {

	private static final long serialVersionUID = 1L;

	public OTFDialog(Scenario scenario, EventsManager eventsManager, QSim qSim) {
		super(Main.parent, "MATSim OTFVis", new String[]{});
		OnTheFlyServer server = OTFVis.startServerAndRegisterWithQSim(scenario.getConfig(), scenario, eventsManager, qSim);
		SettingsSaver saver = new SettingsSaver("otfsettings");
		OTFVisConfigGroup visconf = saver.tryToReadSettingsFile();
		if (visconf == null) {
			visconf = server.getOTFVisConfig();
		}

		OTFConnectionManager connectionManager = new OTFConnectionManager();
		connectionManager.connectLinkToWriter(OTFLinkAgentsHandler.Writer.class);
		connectionManager.connectWriterToReader(OTFLinkAgentsHandler.Writer.class, OTFLinkAgentsHandler.class);
		connectionManager.connectReaderToReceiver(OTFLinkAgentsHandler.class, OGLSimpleQuadDrawer.class);
		connectionManager.connectWriterToReader(OTFAgentsListHandler.Writer.class, OTFAgentsListHandler.class);
		if (scenario.getConfig().transit().isUseTransit()) {
			connectionManager.connectWriterToReader(FacilityDrawer.Writer.class, FacilityDrawer.Reader.class);
			connectionManager.connectReaderToReceiver(FacilityDrawer.Reader.class, FacilityDrawer.DataDrawer.class);
			connectionManager.connectReceiverToLayer(FacilityDrawer.DataDrawer.class, SimpleSceneLayer.class);
		}

		Component canvas = OTFOGLDrawer.createGLCanvas(visconf);
		OTFHostControl hostControl = new OTFHostControl(server, canvas);
		OTFClientControl.getInstance().setOTFVisConfig(visconf); // has to be set before OTFClientQuadTree.getConstData() is invoked!
		OTFServerQuadTree serverQuadTree = server.getQuad(connectionManager);
		OTFClientQuadTree clientQuadTree = serverQuadTree.convertToClient(server, connectionManager);

		OTFOGLDrawer mainDrawer = new OTFOGLDrawer(clientQuadTree, visconf, canvas, hostControl);
		OTFControlBar hostControlBar = new OTFControlBar(server, hostControl, mainDrawer);

		OTFQueryControl queryControl = new OTFQueryControl(server, visconf);
		OTFQueryControlToolBar queryControlBar = new OTFQueryControlToolBar(queryControl, visconf);
		queryControl.setQueryTextField(queryControlBar.getTextField());
		mainDrawer.setQueryHandler(queryControl);

		JPanel compositePanel = new JPanel();
		compositePanel.setBackground(Color.white);
		compositePanel.setOpaque(true);
		compositePanel.setLayout(new OverlayLayout(compositePanel));
		compositePanel.add(canvas);
		Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
		compositePanel.setPreferredSize(new Dimension(screenSize.width/2,screenSize.height/2));

		getContentPane().add(compositePanel, BorderLayout.CENTER);
		getContentPane().add(hostControlBar, BorderLayout.NORTH);
		getContentPane().add(queryControlBar, BorderLayout.SOUTH);
		pack();
	}

}
