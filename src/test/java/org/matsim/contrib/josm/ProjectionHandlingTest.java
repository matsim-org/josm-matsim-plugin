package org.matsim.contrib.josm;


import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Node;
import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.gui.preferences.projection.ProjectionPreference;
import org.openstreetmap.josm.testutils.JOSMTestRules;


public class ProjectionHandlingTest {
	
	@Rule
	public JOSMTestRules test = new JOSMTestRules().preferences().projection();

	static final double DELTA = 0.0001;
	Map<Id<Node>, Coord> nodeCoords = new HashMap<>();
	MATSimLayer matsimLayer;

	@Before
	public void init() {
		Main.setProjection(ProjectionPreference.mercator.getProjection());
		matsimLayer = PtTutorialScenario.layer();
	       
		for(Node node: matsimLayer.getNetworkListener().getScenario().getNetwork().getNodes().values()) {
			nodeCoords.put(node.getId(), node.getCoord());
		}
	}
	
	@Test
	public void test() {
		
		Main.getLayerManager().addLayer(matsimLayer);
		
		Assert.assertEquals(ProjectionPreference.mercator.getProjection().toCode(), Main.getProjection().toCode());
		Main.setProjection(ProjectionPreference.wgs84.getProjection());
		Assert.assertEquals(ProjectionPreference.wgs84.getProjection().toCode(), Main.getProjection().toCode());
		
		for(Entry<Id<Node>, Coord> entry: nodeCoords.entrySet()) {
			Node node = matsimLayer.getNetworkListener().getScenario().getNetwork().getNodes().get(entry.getKey());
			Assert.assertNotEquals(entry.getValue().getX(), node.getCoord().getX(), DELTA);
			Assert.assertNotEquals(entry.getValue().getY(), node.getCoord().getY(), DELTA);
		}
		
		Main.setProjection(ProjectionPreference.mercator.getProjection());
		Assert.assertEquals(ProjectionPreference.mercator.getProjection().toCode(), Main.getProjection().toCode());
		
		for(Entry<Id<Node>, Coord> entry: nodeCoords.entrySet()) {
			Node node = matsimLayer.getNetworkListener().getScenario().getNetwork().getNodes().get(entry.getKey());
			Assert.assertEquals(entry.getValue().getX(), node.getCoord().getX(), DELTA);
			Assert.assertEquals(entry.getValue().getY(), node.getCoord().getY(), DELTA);
		}
		
	}

}
