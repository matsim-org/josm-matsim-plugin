package org.matsim.contrib.josm;


import java.util.HashMap;
import java.util.Map;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.matsim.api.core.v01.Coord;
import org.matsim.contrib.josm.model.MATSimLayer;
import org.matsim.contrib.josm.model.MNode;
import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.preferences.projection.ProjectionPreference;
import org.openstreetmap.josm.testutils.JOSMTestRules;


public class ProjectionHandlingTest {
	
	@Rule
	public JOSMTestRules test = new JOSMTestRules().preferences().projection();

	static final double DELTA = 0.0001;
	Map<String, Coord> nodeCoords = new HashMap<>();
	MATSimLayer matsimLayer;

	@Before
	public void init() {
		Main.setProjection(ProjectionPreference.mercator.getProjection());
		matsimLayer = PtTutorialScenario.layer();
	       
		for(MNode node: matsimLayer.getNetworkModel().nodes().values()) {
			nodeCoords.put(node.getOrigId(), node.getCoord());
		}
	}
	
	@Test
	public void test() {
		
		MainApplication.getLayerManager().addLayer(matsimLayer);
		
		Assert.assertEquals(ProjectionPreference.mercator.getProjection().toCode(), Main.getProjection().toCode());
		Main.setProjection(ProjectionPreference.wgs84.getProjection());
		Assert.assertEquals(ProjectionPreference.wgs84.getProjection().toCode(), Main.getProjection().toCode());

		for (MNode node : matsimLayer.getNetworkModel().nodes().values()) {
			Assert.assertNotEquals(nodeCoords.get(node.getOrigId()).getX(), node.getCoord().getX(), DELTA);
			Assert.assertNotEquals(nodeCoords.get(node.getOrigId()).getY(), node.getCoord().getY(), DELTA);

		}

		Main.setProjection(ProjectionPreference.mercator.getProjection());
		Assert.assertEquals(ProjectionPreference.mercator.getProjection().toCode(), Main.getProjection().toCode());

		for (MNode node : matsimLayer.getNetworkModel().nodes().values()) {
			Assert.assertEquals(nodeCoords.get(node.getOrigId()).getX(), node.getCoord().getX(), DELTA);
			Assert.assertEquals(nodeCoords.get(node.getOrigId()).getY(), node.getCoord().getY(), DELTA);

		}
		
	}

}
