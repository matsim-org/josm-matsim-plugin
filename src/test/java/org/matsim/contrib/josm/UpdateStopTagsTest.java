package org.matsim.contrib.josm;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.matsim.contrib.josm.model.NetworkModel;
import org.matsim.contrib.osm.UpdateStopTags;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.openstreetmap.josm.command.Command;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.*;
import org.openstreetmap.josm.data.validation.TestError;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.layer.OsmDataLayer;
import org.openstreetmap.josm.testutils.JOSMTestRules;

import java.lang.reflect.InvocationTargetException;

public class UpdateStopTagsTest {

	@Rule
	public JOSMTestRules test = new JOSMTestRules().preferences();

	private OsmDataLayer layer;

	private Node highway_bus_stop_node;
	private Node highway_bus_stop_nodeOnWay;
	private Node amenity_bus_station_node;
	private Node amenity_bus_station_nodeOnWay;
	private Node highway_platform_node;
	private Way highway_platform_way;
	private Node railway_tram_stop_nodeOnway;
	private Node railway_halt_nodeOnWay;
	private Node railway_platform_node;
	private Way railway_platform_way;

	static final Tag platform = new Tag("public_transport", "platform");
	static final Tag stop = new Tag ("public_transport", "stop_position");

	@Before
	public void init() {
		org.openstreetmap.josm.spi.preferences.Config.getPref().putBoolean("matsim_supportTransit", true);
		DataSet data = new DataSet();
		initializeDataSet(data);
		layer = new OsmDataLayer(data, "test", null);
		Config config = ConfigUtils.createConfig();
		config.transit().setUseTransit(true);
		NetworkModel listener = NetworkModel.createNetworkModel(data);
		listener.visitAll();
		MainApplication.getLayerManager().addLayer(layer);
	}

	private void initializeDataSet(DataSet data) {
		LatLon latLon = new LatLon(0, 0);
		Way dummyWay = new Way();
		Node dummyNode1 = new Node(latLon);
		Node dummyNode2 = new Node(latLon);
		data.addPrimitive(dummyNode1);
		data.addPrimitive(dummyNode2);

		highway_bus_stop_node = new Node(latLon);
		highway_bus_stop_node.put("highway", "bus_stop");
		data.addPrimitive(highway_bus_stop_node);

		highway_bus_stop_nodeOnWay = new Node(latLon);
		highway_bus_stop_nodeOnWay.put("highway", "bus_stop");
		data.addPrimitive(highway_bus_stop_nodeOnWay);
		dummyWay.addNode(highway_bus_stop_nodeOnWay);

		amenity_bus_station_node = new Node(latLon);
		amenity_bus_station_node.put("amenity", "bus_station");
		data.addPrimitive(amenity_bus_station_node);

		amenity_bus_station_nodeOnWay = new Node(latLon);
		amenity_bus_station_nodeOnWay.put("amenity", "bus_station");
		data.addPrimitive(amenity_bus_station_nodeOnWay);
		dummyWay.addNode(amenity_bus_station_nodeOnWay);

		highway_platform_node = new Node(latLon);
		highway_platform_node.put("highway", "platform");
		data.addPrimitive(highway_platform_node);

		highway_platform_way = new Way();
		highway_platform_way.put("highway", "platform");
		highway_platform_way.addNode(dummyNode1);
		highway_platform_way.addNode(dummyNode2);
		data.addPrimitive(highway_platform_way);

		railway_tram_stop_nodeOnway = new Node(latLon);
		railway_tram_stop_nodeOnway.put("railway", "tram_stop");
		data.addPrimitive(railway_tram_stop_nodeOnway);
		dummyWay.addNode(railway_tram_stop_nodeOnway);

		railway_halt_nodeOnWay = new Node(latLon);
		railway_halt_nodeOnWay.put("railway", "halt");
		data.addPrimitive(railway_halt_nodeOnWay);
		dummyWay.addNode(railway_halt_nodeOnWay);

		railway_platform_node = new Node(latLon);
		railway_platform_node.put("railway", "platform");
		data.addPrimitive(railway_platform_node);

		railway_platform_way = new Way();
		railway_platform_way.put("railway", "platform");
		railway_platform_way.addNode(dummyNode1);
		railway_platform_way.addNode(dummyNode2);
		data.addPrimitive(railway_platform_way);

		data.addPrimitive(dummyWay);
	}

	@Test
	public void testUpdateStopTags() throws InvocationTargetException, InterruptedException {
		UpdateStopTags test = new UpdateStopTags();
		test.startTest(null);
		test.visit(layer.data.allPrimitives());
		test.endTest();
		Assert.assertEquals(10, test.getErrors().size());

		for(TestError error: test.getErrors()) {
			if (error.isFixable()) {
				final Command fixCommand = error.getFix();
				if (fixCommand != null) {
					fixCommand.executeCommand();
				}
			}
		}

		test.startTest(null);
		test.visit(layer.data.allPrimitives());
		test.endTest();
		Assert.assertEquals(0, test.getErrors().size());

		Assert.assertTrue(hasTag(platform, highway_bus_stop_node));
		Assert.assertTrue(hasTag(stop, highway_bus_stop_nodeOnWay));

		Assert.assertTrue(hasTag(platform, amenity_bus_station_node));
		Assert.assertTrue(hasTag(stop, amenity_bus_station_nodeOnWay));

		Assert.assertTrue(hasTag(platform, highway_platform_node));
		Assert.assertTrue(hasTag(platform, highway_platform_way));

		Assert.assertTrue(hasTag(stop, railway_tram_stop_nodeOnway));
		Assert.assertTrue(hasTag(stop, railway_halt_nodeOnWay));

		Assert.assertTrue(hasTag(platform, railway_platform_node));
		Assert.assertTrue(hasTag(platform, railway_platform_way));

	}


	private boolean hasTag(Tag tag, OsmPrimitive primitive) {
		if(primitive.hasTag(tag.getKey(), tag.getValue())) {
			return true;
		} else {
			return false;
		}
	}


}




