package org.matsim.contrib.josm;


import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Node;
import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.gui.preferences.projection.ProjectionPreference;


public class ProjectionHandlingTest {
	
	Map<Id<Node>, Coord> nodeCoords = new HashMap<>();
	MATSimLayer matsimLayer;

	@Rule
	public TemporaryFolder folder = new TemporaryFolder();

	@Before
	public void init() {
		new JOSMFixture(folder.getRoot().getPath()).init(true);
		Main.setProjection(ProjectionPreference.mercator.getProjection());
		matsimLayer = PtTutorialScenario.layer();
	       
		for(Node node: matsimLayer.getNetworkListener().getScenario().getNetwork().getNodes().values()) {
			nodeCoords.put(node.getId(), node.getCoord());
		}
	}
	
	@Test
	public void test() {
		
		Main.main.addLayer(matsimLayer);
		
		Assert.assertEquals(ProjectionPreference.mercator.getProjection().toCode(), Main.getProjection().toCode());
		Main.setProjection(ProjectionPreference.wgs84.getProjection());
		Assert.assertEquals(ProjectionPreference.wgs84.getProjection().toCode(), Main.getProjection().toCode());
		
		for(Entry<Id<Node>, Coord> entry: nodeCoords.entrySet()) {
			Node node = matsimLayer.getNetworkListener().getScenario().getNetwork().getNodes().get(entry.getKey());
			Assert.assertNotEquals(entry.getValue().getX(), node.getCoord().getX(), 0);
			Assert.assertNotEquals(entry.getValue().getY(), node.getCoord().getY(), 0);
		}
		
		Main.setProjection(ProjectionPreference.mercator.getProjection());
		Assert.assertEquals(ProjectionPreference.mercator.getProjection().toCode(), Main.getProjection().toCode());
		
		for(Entry<Id<Node>, Coord> entry: nodeCoords.entrySet()) {
			Node node = matsimLayer.getNetworkListener().getScenario().getNetwork().getNodes().get(entry.getKey());
			Assert.assertEquals(entry.getValue().getX(), node.getCoord().getX(),1);
			Assert.assertEquals(entry.getValue().getY(), node.getCoord().getY(),1);
		}
		
	}

}
