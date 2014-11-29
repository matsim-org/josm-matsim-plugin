package org.matsim.contrib.josm;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.contrib.josm.OsmConvertDefaults.OsmWayDefaults;
import org.matsim.core.network.LinkImpl;
import org.matsim.core.network.NodeImpl;
import org.matsim.core.population.routes.LinkNetworkRouteImpl;
import org.matsim.core.population.routes.NetworkRoute;
import org.matsim.core.utils.geometry.CoordImpl;
import org.matsim.pt.transitSchedule.TransitScheduleFactoryImpl;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitRouteStop;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt.transitSchedule.api.TransitScheduleFactory;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;
import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.OsmPrimitiveType;
import org.openstreetmap.josm.data.osm.Relation;
import org.openstreetmap.josm.data.osm.RelationMember;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.data.osm.WaySegment;
import org.openstreetmap.josm.gui.dialogs.relation.sort.RelationSorter;
import org.openstreetmap.josm.gui.dialogs.relation.sort.WayConnectionType;
import org.openstreetmap.josm.gui.dialogs.relation.sort.WayConnectionTypeCalculator;
import org.openstreetmap.josm.gui.layer.OsmDataLayer;

class NewConverter {
	private final static Logger log = Logger.getLogger(NewConverter.class);

	private final static String TAG_LANES = "lanes";
	private final static String TAG_HIGHWAY = "highway";
	private final static String TAG_RAILWAY = "railway";
	private final static String TAG_MAXSPEED = "maxspeed";
	private final static String TAG_JUNCTION = "junction";
	private final static String TAG_ONEWAY = "oneway";

	static boolean keepPaths = Main.pref.getBoolean("matsim_keepPaths", false);

	private static final List<String> TRANSPORT_MODES = Arrays.asList(
			TransportMode.bike, TransportMode.car, TransportMode.other,
			TransportMode.pt, TransportMode.ride, TransportMode.transit_walk,
			TransportMode.walk);

	static Map<String, OsmWayDefaults> wayDefaults;

	// converts complete data layer and fills the given MATSim data structures
	// as well as data mappings
	public static void convertOsmLayer(OsmDataLayer layer, Scenario scenario,
			Map<Way, List<Link>> way2Links,
			Map<Link, List<WaySegment>> link2Segments,
			Map<Relation, TransitRoute> relation2Route) {
		log.info("=== Starting conversion of Osm data ===");
		log.setLevel(Level.OFF);

		// could be used for area filtering in future releases
		// List<JoinedPolygon> polygons = new ArrayList<JoinedPolygon>();
		// for (Way way : layer.data.getWays()) {
		// if (way.isClosed() && way.hasTag("matsim:convert_Area", "active")) {
		// polygons.add(new MultipolygonBuilder.JoinedPolygon(way));
		// }
		// }

		// convert single way
		if (!layer.data.getWays().isEmpty()) {
			for (Way way : layer.data.getWays()) {
				if (!way.isDeleted() /* && isInArea(polygons, way) */) {
					convertWay(way, scenario.getNetwork(), way2Links,
							link2Segments);
				}
			}

			List<Relation> publicTransportRoutesOsm = new ArrayList<Relation>();

			// check which relations should be converted to routes differed by
			// matsim and osm tag scheme
			for (Relation relation : layer.data.getRelations()) {
				if (!relation.isDeleted()
						&& relation.hasTag("type", "route")
						&& relation.hasTag("route",
								new String[] { "train", "track", "bus",
										"light_rail", "tram", "subway" })) {
					// if (relation.hasIncompleteMembers()) {
					// DownloadRelationMemberTask task = new
					// DownloadRelationMemberTask(
					// relation, relation.getIncompleteMembers(),
					// layer);
					// task.run();
					// }
					publicTransportRoutesOsm.add(relation);
				}
			}

			// sort elements by the way they are linked to each other
			RelationSorter sorter = new RelationSorter();

			// convert osm tagged relations
			for (Relation relation : publicTransportRoutesOsm) {
				sorter.sortMembers(relation.getMembers());
				convertTransitRouteOsm(relation, scenario, relation2Route,
						way2Links, link2Segments);
			}

		}
		log.info("=== End of Conversion. #Links: "
				+ scenario.getNetwork().getLinks().size() + " | #Nodes: "
				+ scenario.getNetwork().getNodes().size() + " ===");
	}

	// private static boolean isInArea(List<JoinedPolygon> polygons, Way way) {
	//
	// for (JoinedPolygon polygon: polygons) {
	// for (Node node: way.getNodes()) {
	// if ((Geometry.nodeInsidePolygon(node, polygon.getNodes()))) {
	// return true;
	// }
	// }
	// }
	// return false;
	// }

	public static void convertWay(Way way, Network network,
			Map<Way, List<Link>> way2Links,
			Map<Link, List<WaySegment>> link2Segments) {
		log.info("### Way " + way.getUniqueId() + " (" + way.getNodesCount()
				+ " nodes) ###");
		List<Link> links = new ArrayList<Link>();
		wayDefaults = OsmConvertDefaults.getWayDefaults();

		if (way.getNodesCount() > 1) {
			if (way.hasTag(TAG_HIGHWAY, wayDefaults.keySet())
					|| meetsMatsimReq(way.getKeys())
					|| (way.hasTag(TAG_RAILWAY, wayDefaults.keySet()) && elementOfConnectedRoute(way))) {
				List<Node> nodeOrder = new ArrayList<Node>();
				StringBuilder nodeOrderLog = new StringBuilder();
				log.setLevel(Level.OFF);
				for (int l = 0; l < way.getNodesCount(); l++) {
					Node current = way.getNode(l);
					if (current.getDataSet() == null) {
						continue;
					}
					if (l == 0 || l == way.getNodesCount() - 1 || keepPaths) {
						nodeOrder.add(current);
						log.debug("--- Way " + way.getUniqueId()
								+ ": dumped node " + l + " ("
								+ current.getUniqueId() + ") ");
						nodeOrderLog.append("(" + l + ") ");
					} else if (current
							.equals(way.getNode(way.getNodesCount() - 1))) {
						nodeOrder.add(current); // add node twice if it occurs
												// twice in a loop so length
												// to this node is not
												// calculated wrong
						log.debug("--- Way " + way.getUniqueId()
								+ ": dumped node " + l + " ("
								+ current.getUniqueId()
								+ ") beginning of loop / closed area ");
						nodeOrderLog.append("(" + l + ") ");
					} else if (current.isConnectionNode()) {
						for (OsmPrimitive prim : current.getReferrers()) {
							if (prim instanceof Way && !prim.equals(way)) {
								if (((Way) prim).hasKey(TAG_HIGHWAY)
										|| meetsMatsimReq(prim.getKeys())) {
									nodeOrder.add(current);
									log.debug("--- Way " + way.getUniqueId()
											+ ": dumped node " + l + " ("
											+ current.getUniqueId()
											+ ") way intersection");
									nodeOrderLog.append("(" + l + ") ");
									break;
								}
							}
						}
					} else {
						for (OsmPrimitive prim : current.getReferrers()) {
							if (prim instanceof Relation
									&& prim.hasTag("route", new String[] {
											"train", "track", "bus",
											"light_rail", "tram", "subway" })
									&& prim.hasTag("type", "route") && !nodeOrder.contains(current)) {
								nodeOrder.add(current);
								log.debug("--- Way " + way.getUniqueId()
										+ ": dumped node " + l + " ("
										+ current.getUniqueId()
										+ ") stop position "+current.getLocalName());
							}
						}

					}
				}

				log.debug("--- Way " + way.getUniqueId()
						+ ": order of kept nodes [ " + nodeOrderLog.toString()
						+ "]");

				for (Node node : nodeOrder) {
					checkNode(network, node);

					log.debug("--- Way " + way.getUniqueId()
							+ ": created / updated MATSim node "
							+ node.getUniqueId());
				}

				Double length = 0.;
				Double capacity = 0.;
				Double freespeed = 0.;
				Double nofLanes = 0.;
				boolean oneway = true;
				Set<String> modes = new HashSet<String>();
				boolean onewayReverse = false;

				Map<String, String> keys = way.getKeys();
				if (keys.containsKey(TAG_HIGHWAY)
						|| keys.containsKey(TAG_RAILWAY)) {
					
					String wayType ;
					if (keys.containsKey(TAG_HIGHWAY)) {
						wayType = keys.get(TAG_HIGHWAY);
					} else if (keys.containsKey(TAG_RAILWAY)) {
						wayType = keys.get(TAG_RAILWAY);
					} else {
						return;
					}

					// load defaults
					OsmWayDefaults defaults = wayDefaults.get(wayType);
					if (defaults != null) {

						if (defaults.hierarchy > Main.pref.getInteger(
								"matsim_filter_hierarchy", 6)) {
							return;
						}
						nofLanes = defaults.lanes;
						double laneCapacity = defaults.laneCapacity;
						freespeed = defaults.freespeed;
						oneway = defaults.oneway;

						// check if there are tags that overwrite defaults
						// - check tag "junction"
						if ("roundabout".equals(keys.get(TAG_JUNCTION))) {
							// if "junction" is not set in tags, get()
							// returns null and
							// equals()
							// evaluates to false
							oneway = true;
						}

						// check tag "oneway"
						String onewayTag = keys.get(TAG_ONEWAY);
						if (onewayTag != null) {
							if ("yes".equals(onewayTag)) {
								oneway = true;
							} else if ("true".equals(onewayTag)) {
								oneway = true;
							} else if ("1".equals(onewayTag)) {
								oneway = true;
							} else if ("-1".equals(onewayTag)) {
								onewayReverse = true;
								oneway = false;
							} else if ("no".equals(onewayTag)) {
								oneway = false; // may be used to overwrite
												// defaults
							} else {
								log.warn("--- Way " + way.getUniqueId()
										+ ": could not parse oneway tag");
							}
						}

						// In case trunks, primary and secondary roads are
						// marked as
						// oneway,
						// the default number of lanes should be two instead
						// of one.
						if (wayType.equalsIgnoreCase("trunk")
								|| wayType.equalsIgnoreCase("primary")
								|| wayType.equalsIgnoreCase("secondary")) {
							if (oneway && nofLanes == 1.0) {
								nofLanes = 2.0;
							}
						}

						String maxspeedTag = keys.get(TAG_MAXSPEED);
						if (maxspeedTag != null) {
							try {
								freespeed = Double.parseDouble(maxspeedTag) / 3.6; // convert
								// km/h to
								// m/s
							} catch (NumberFormatException e) {
								log.warn("--- Way " + way.getUniqueId()
										+ ": could not parse maxspeed tag");
							}
						}

						// check tag "lanes"
						String lanesTag = keys.get(TAG_LANES);
						if (lanesTag != null) {
							try {
								double tmp = Double.parseDouble(lanesTag);
								if (tmp > 0) {
									nofLanes = tmp;
								}
							} catch (Exception e) {
								log.warn("--- Way " + way.getUniqueId()
										+ ": could not parse lanes tag");
							}
						}
						// create the link(s)
						capacity = nofLanes * laneCapacity;
					}
				}
				if (keys.containsKey("capacity")) {
					Double capacityTag = parseDoubleIfPossible(keys
							.get("capacity"));
					if (capacityTag != null) {
						capacity = capacityTag;
					} else {
						log.warn("--- Way " + way.getUniqueId()
								+ ": could not parse MATSim capacity tag");
					}
				}
				if (keys.containsKey("freespeed")) {
					Double freespeedTag = parseDoubleIfPossible(keys
							.get("freespeed"));
					if (freespeedTag != null) {
						freespeed = freespeedTag;
					} else {
						log.warn("--- Way " + way.getUniqueId()
								+ ": could not parse MATSim freespeed tag");
					}
				}
				if (keys.containsKey("permlanes")) {
					Double permlanesTag = parseDoubleIfPossible(keys
							.get("permlanes"));
					if (permlanesTag != null) {
						nofLanes = permlanesTag;
					} else {
						log.warn("--- Way " + way.getUniqueId()
								+ ": could not parse MATSim permlanes tag");
					}
				}
				if (keys.containsKey("modes")) {
					Set<String> tempModes = new HashSet<String>();
					String tempArray[] = keys.get("modes").split(";");
					for (String mode : tempArray) {
						if (TRANSPORT_MODES.contains(mode)) {
							tempModes.add(mode);
						}
					}
					if (tempModes.size() != 0) {
						modes.clear();
						modes.addAll(tempModes);
					} else {
						log.warn("--- Way " + way.getUniqueId()
								+ ": could not parse MATSim modes tag");
					}
				}

				Double tempLength = null;
				if (keys.containsKey("length")) {
					Double temp = parseDoubleIfPossible(keys.get("length"));
					if (temp != null) {
						tempLength = temp;

					} else {
						log.warn("--- Way " + way.getUniqueId()
								+ ": could not parse MATSim length tag");
					}
				}

				if (modes.isEmpty()) {
					if (keys.containsKey(TAG_RAILWAY)) {
						modes.add(TransportMode.pt);
					}
					if (keys.containsKey(TAG_HIGHWAY)) {
						modes.add(TransportMode.car);
					}
				}

				long increment = 0;
				for (int k = 1; k < nodeOrder.size(); k++) {
					List<WaySegment> segs = new ArrayList<WaySegment>();
					Node nodeFrom = nodeOrder.get(k - 1);
					Node nodeTo = nodeOrder.get(k);

					if (nodeFrom.equals(nodeTo) && !keepPaths) {
						// skip uninteresting loop
						log.warn("--- Way " + way.getUniqueId()
								+ ": contains loose loop / closed area.");
						break;
					}

					int fromIdx = way.getNodes().indexOf(nodeFrom);
					int toIdx = way.getNodes().indexOf(nodeTo);
					if (fromIdx > toIdx) { // loop, take latter occurrence
						toIdx = way.getNodes().lastIndexOf(nodeTo);
					}

					length = 0.;
					for (int m = fromIdx; m < toIdx; m++) {
						segs.add(new WaySegment(way, m));
						length += way
								.getNode(m)
								.getCoor()
								.greatCircleDistance(
										way.getNode(m + 1).getCoor());
					}
					log.debug("--- Way " + way.getUniqueId()
							+ ": length between " + fromIdx + " and " + toIdx
							+ ": " + length);
					if (tempLength != null) {
						length = tempLength * length / way.getLength();
					}
					List<Link> tempLinks = createLink(network, way, nodeFrom,
							nodeTo, length, increment, oneway, onewayReverse,
							freespeed, capacity, nofLanes, modes);
					for (Link link : tempLinks) {
						link2Segments.put(link, segs);
					}
					links.addAll(tempLinks);
					increment++;
				}
			}

		}
		log.debug("### Finished Way " + way.getUniqueId() + ". " + links.size()
				+ " links resulted. ###");
		if (way == null || links.isEmpty() || links == null) {
			return;
		} else {
			way2Links.put(way, links);
		}
	}

	private static boolean elementOfConnectedRoute(Way way) {
		for (OsmPrimitive primitive: way.getReferrers()) {
			if(primitive instanceof Relation && primitive.hasTag("type", "route")) {
				List<Way> list = new ArrayList<Way>();
				for(OsmPrimitive member: ((Relation)primitive).getMemberPrimitivesList()) {
					if(member instanceof Way) {
						list.add((Way)member);
					}
				}
				if(list.size()<2) {
					return false;
				}
				for(int i=1; i<list.size();i++) {
					if(!waysConnected(list.get(i-1), list.get(i), (Relation) primitive)) {
						return false;
					}
				}
				return true;
			} else {
				return false;
			}
		}
		return false;
	}

	// create or update matsim node
	private static void checkNode(Network network, Node node) {
		Id<org.matsim.api.core.v01.network.Node> nodeId = Id.create(
				node.getUniqueId(), org.matsim.api.core.v01.network.Node.class);
		if (!node.isIncomplete()) {
			if (!network.getNodes().containsKey(nodeId)) {
				double lat = node.getCoor().lat();
				double lon = node.getCoor().lon();
				org.matsim.api.core.v01.network.Node nn = network
						.getFactory()
						.createNode(
								Id.create(
										node.getUniqueId(),
										org.matsim.api.core.v01.network.Node.class),
								new CoordImpl(lon, lat));
				if (node.hasKey(ImportTask.NODE_TAG_ID)) {
					((NodeImpl) nn).setOrigId(node.get(ImportTask.NODE_TAG_ID));
				} else {
					((NodeImpl) nn).setOrigId(nn.getId().toString());
				}
				network.addNode(nn);
			} else {
				if (node.hasKey(ImportTask.NODE_TAG_ID)) {
					((NodeImpl) network.getNodes().get(nodeId)).setOrigId(node
							.get(ImportTask.NODE_TAG_ID));
				} else {
					((NodeImpl) network.getNodes().get(nodeId))
							.setOrigId(String.valueOf(node.getUniqueId()));
				}
				Coord coord = new CoordImpl(node.getCoor().getX(), node
						.getCoor().getY());
				((NodeImpl) network.getNodes().get(nodeId)).setCoord(coord);
			}
		}
	}

	// creates links between given nodes along the respective WaySegments.
	// adapted from original OsmNetworkReader
	private static List<Link> createLink(final Network network,
			final OsmPrimitive primitive, final Node fromNode,
			final Node toNode, double length, long increment, boolean oneway,
			boolean onewayReverse, Double freespeed, Double capacity,
			Double nofLanes, Set<String> modes) {

		// only create link, if both nodes were found, node could be null, since
		// nodes outside a layer were dropped
		List<Link> links = new ArrayList<Link>();
		Id<org.matsim.api.core.v01.network.Node> fromId = Id.create(
				fromNode.getUniqueId(),
				org.matsim.api.core.v01.network.Node.class);
		Id<org.matsim.api.core.v01.network.Node> toId = Id.create(
				toNode.getUniqueId(),
				org.matsim.api.core.v01.network.Node.class);
		if (network.getNodes().get(fromId) != null
				&& network.getNodes().get(toId) != null) {

			String id = String.valueOf(primitive.getUniqueId()) + "_"
					+ increment;
			String origId;

			if (primitive instanceof Way
					&& primitive.hasKey(ImportTask.WAY_TAG_ID)) {
				origId = primitive.get(ImportTask.WAY_TAG_ID);
			} else if (primitive instanceof Relation) {
				if (primitive.hasKey("ref")) {
					id = id + "_" + primitive.get("ref");
					origId = id;
				} else {
					id = id + "_TRANSIT";
					origId = id;
				}
			} else {
				origId = id;
			}

			if (!onewayReverse) {
				Link l = network.getFactory().createLink(
						Id.create(id, Link.class),
						network.getNodes().get(fromId),
						network.getNodes().get(toId));
				l.setLength(length);
				l.setFreespeed(freespeed);
				l.setCapacity(capacity);
				l.setNumberOfLanes(nofLanes);
				l.setAllowedModes(modes);
				if (l instanceof LinkImpl) {
					((LinkImpl) l).setOrigId(origId);
				}
				network.addLink(l);
				links.add(l);
				log.info("--- Way " + primitive.getUniqueId() + ": link "
						+ ((LinkImpl) l).getOrigId() + " created");
			}
			if (!oneway) {
				Link l = network.getFactory().createLink(
						Id.create(id + "_r", Link.class),
						network.getNodes().get(toId),
						network.getNodes().get(fromId));
				l.setLength(length);
				l.setFreespeed(freespeed);
				l.setCapacity(capacity);
				l.setNumberOfLanes(nofLanes);
				l.setAllowedModes(modes);
				if (l instanceof LinkImpl) {
					((LinkImpl) l).setOrigId(origId + "_r");
				}
				network.addLink(l);
				links.add(l);
				log.info("--- Way " + primitive.getUniqueId() + ": link "
						+ ((LinkImpl) l).getOrigId() + " created");
			}
		}
		return links;
	}

	public static void convertTransitRouteOsm(Relation relation,
			Scenario scenario, Map<Relation, TransitRoute> relation2Route,
			Map<Way, List<Link>> way2Links,
			Map<Link, List<WaySegment>> link2Segments) {
		NetworkRoute route;
		Map<TransitStopFacility, WaySegment> stops2Segment = new LinkedHashMap<TransitStopFacility, WaySegment>();
		log.debug("converting route relation" + relation.getUniqueId() + " "
				+ relation.getName());
		// filtere nodes heraus
		Map<Node, WaySegment> nodes2Segment = new LinkedHashMap<Node, WaySegment>();
		for (RelationMember member : relation.getMembers()) {
			if (member.isNode() && !member.getMember().isIncomplete()) {
				nodes2Segment.put(member.getNode(), null);
			}
		}
		if (nodes2Segment.isEmpty()) {
			log.debug("no nodes found. relation not converted.");
			return;
		}
		log.debug("nodes found. proceeding.");

		// überprüfe ob alle nodes auf way liegen
		boolean nodesOnWay = nodesOnWay(relation, nodes2Segment.keySet(),
				nodes2Segment);
		if (nodesOnWay) {
			log.debug("all nodes on ways.");
		} else {
			log.debug("not all nodes on ways.");
		}

		// überprüfe ob alle nodes verbunden sind
		boolean nodesConnected;
		if (nodesOnWay == true) {
			nodesConnected = nodesConnected(relation, nodes2Segment);
		} else {
			nodesConnected = false;
		}

		if (nodesConnected) {
			log.debug("all nodes are connected with each other.");
		} else {
			log.debug("not all nodes are connected with each other.");
		}

		// erstelle / überprüfe MATSim Nodes,
		for (Node node : nodes2Segment.keySet()) {
			checkNode(scenario.getNetwork(), node);
		}

		// erstelle StopFacilities für alle nodes
		for (Entry<Node, WaySegment> entry : nodes2Segment.entrySet()) {
			TransitStopFacility stop = createStopFacility(entry.getKey(),
					relation, scenario.getTransitSchedule());
			scenario.getTransitSchedule().addStopFacility(stop);
			stops2Segment.put(stop, entry.getValue());
		}
		nodes2Segment.clear();
		if (stops2Segment.isEmpty()) {
			log.debug("no stop facilities created. relation not converted.");
			return;
		} else {
			log.debug("stop facilities created. proceeding.");
		}

		// erstelle Route zwischen den Stops
		if (nodesOnWay && nodesConnected) {
			log.debug("nodes on ways and fully connected. creating topological route.");
			// route entlang ways
			route = createConnectedWayRoute(relation, scenario, stops2Segment,
					way2Links, link2Segments);
		} else {
			log.debug("not all nodes on ways or fully conected. creating beeline route.");
			// beeline route
			route = createBeeLineRoute(relation, scenario, stops2Segment);
		}
		if (route == null) {
			log.debug("no route created. relation not converted.");
			for (TransitStopFacility facility : stops2Segment.keySet()) {
				scenario.getTransitSchedule().removeStopFacility(facility);
			}
			return;
		} else {
			log.debug("route created. proceeding.");
		}

		TransitSchedule schedule = scenario.getTransitSchedule();
		TransitScheduleFactory builder = schedule.getFactory();

		Id<TransitLine> lineId;
		if (relation.hasKey("ref")) {
			lineId = Id.create(relation.get("ref"), TransitLine.class);
		} else {
			lineId = Id.create(relation.getUniqueId(), TransitLine.class);
		}

		TransitLine tLine;
		if (!scenario.getTransitSchedule().getTransitLines()
				.containsKey(lineId)) {
			tLine = builder.createTransitLine(lineId);
		} else {
			tLine = scenario.getTransitSchedule().getTransitLines().get(lineId);
		}

		ArrayList<TransitRouteStop> routeStops = new ArrayList<TransitRouteStop>();
		for (TransitStopFacility stop : stops2Segment.keySet()) {
			routeStops.add(builder.createTransitRouteStop(stop, 0, 0));
		}

		TransitRoute tRoute = builder.createTransitRoute(
				Id.create(relation.getUniqueId(), TransitRoute.class), route,
				routeStops, "pt");

		tLine.addRoute(tRoute);

		schedule.removeTransitLine(tLine);
		schedule.addTransitLine(tLine);
		relation2Route.put(relation, tRoute);
	}

	private static boolean nodesOnWay(Relation relation, Set<Node> set,
			Map<Node, WaySegment> nodes2Segment) {
		for (Node node : set) {
			boolean nodeOnWay = false;
			for (OsmPrimitive prim : node.getReferrers()) {
				if (prim instanceof Way) {
					if (((Way) prim).containsNode(node)
							&& relation.getMemberPrimitives().contains(prim)) {
						nodeOnWay = true;

						if (nodes2Segment.containsKey(node)) {
							if (nodes2Segment.get(node) != null) {
								if (((Way) prim).getNodes().indexOf(node) > nodes2Segment
										.get(node).lowerIndex) {
									nodes2Segment.put(node, new WaySegment(
											((Way) prim), ((Way) prim)
													.getNodes().indexOf(node)));
									log.debug(node.getUniqueId()
											+ " is on way "
											+ prim.getUniqueId()
											+ ", segment "
											+ ((Way) prim).getNodes().indexOf(
													node));
								}
							} else {
								nodes2Segment.put(node, new WaySegment(
										((Way) prim), ((Way) prim).getNodes()
												.indexOf(node)));
								log.debug(node.getUniqueId() + " is on way "
										+ prim.getUniqueId() + ", segment "
										+ ((Way) prim).getNodes().indexOf(node));
							}
						}
					}
				}
			}
			if (nodeOnWay == false) {
				nodes2Segment.put(node, null);
				return false;
			}
		}
		return true;
	}

	private static boolean nodesConnected(Relation relation,
			Map<Node, WaySegment> nodes2Segment) {
		Node previous = null;
		for (Entry<Node, WaySegment> entry : nodes2Segment.entrySet()) {
			if (previous != null) {
				Way way1 = nodes2Segment.get(previous).way;
				Way way2 = entry.getValue().way;
				if (waysConnected(way1, way2, relation)) {
					log.debug(way1.getName() + " " + way2.getName()
							+ " connected!");
				} else {
					log.debug((way1.getName() + " " + way2.getName() + " not connected!"));
					return false;
				}
			}
			previous = entry.getKey();
		}
		return true;
	}

	private static boolean waysConnected(Way way1, Way way2, Relation relation) {
		// TODO Auto-generated method stub
		WayConnectionTypeCalculator calc = new WayConnectionTypeCalculator();
		List<WayConnectionType> connections = calc.updateLinks(relation
				.getMembers());

		List<OsmPrimitive> primitiveList = relation.getMemberPrimitivesList();
		int i = primitiveList.indexOf(way1);
		int j = primitiveList.indexOf(way2);

		if (i == j) {
			return true;
		}

		if (i > j) {
			return false;
		}

		for (int k = i; k <= j; k++) {
			if (k != i) {
				if (!connections.get(k).linkPrev) {
					return false;
				}
			}
			if (k != j) {
				if (!connections.get(k).linkNext) {
					return false;
				}
			}
		}
		return true;
	}

	// create stop facility at position of given node, if already used by
	// another route, duplicate stop with incremental id
	protected static TransitStopFacility createStopFacility(Node node,
			Relation relation, TransitSchedule schedule) {

		Id<TransitStopFacility>	stopId = Id.create(relation.getUniqueId() + "_"
					+ node.getUniqueId(), TransitStopFacility.class);
		
		int i = 0;
		while (schedule.getFacilities().containsKey(stopId)) {
			stopId = Id.create(i+"_"+stopId.toString(), TransitStopFacility.class);
			i++;
		}
		
		double lat = node.getCoor().lat();
		double lon = node.getCoor().lon();
		TransitScheduleFactory factory = new TransitScheduleFactoryImpl();
		TransitStopFacility stop = factory.createTransitStopFacility(stopId,
				new CoordImpl(lon, lat), true);
		if (node.getLocalName() != null) {
			stop.setName(node.getLocalName());
		} else {
			stop.setName("unknown stop");
		}
		return stop;
	}

	private static NetworkRoute createConnectedWayRoute(Relation relation,
			Scenario scenario,
			Map<TransitStopFacility, WaySegment> stops2Segment,
			Map<Way, List<Link>> way2Links,
			Map<Link, List<WaySegment>> link2Segments) {
		// TODO Auto-generated method stub
		List<Id<Link>> links = new ArrayList<Id<Link>>();
		TransitStopFacility previousStop = null;
		Link previousLink = null;
		Id<Link> firstLinkId = null;
		Id<Link> lastLinkId = null;
		log.setLevel(Level.OFF);
		for (Entry<TransitStopFacility, WaySegment> entry : stops2Segment
				.entrySet()) {
			List<Link> wayLinks = null;
			List<Link> connectedWayLinks = new ArrayList<Link>();
			Link nextLink = previousLink;

			if (way2Links.containsKey(entry.getValue().way)) {
				wayLinks = way2Links.get(entry.getValue().way);
			} else {
				return null;
			}

			if (previousStop != null) {
				int i = relation.getMemberPrimitivesList().indexOf(
						stops2Segment.get(previousStop).way);
				int j = relation.getMemberPrimitivesList().indexOf(
						entry.getValue().way);

				for (int k = i + 1; k < j; k++) {
					OsmPrimitive primitive = relation.getMemberPrimitivesList()
							.get(k);
					if (way2Links.containsKey(primitive)) {
						nextLink = previousLink;
						while (nextLink != null) {
							nextLink = getNextConnectedLink(
									way2Links.get(primitive), previousLink);
							if (nextLink != null) {
								connectedWayLinks.add(nextLink);
								log.debug("link " + previousLink.getId()
										+ " connects with link "
										+ nextLink.getId());
								previousLink = nextLink;
							}
						}
					} else {
						throw new RuntimeException("Primitive "
								+ primitive.getUniqueId()
								+ "of route relation " + relation.getUniqueId()
								+ " defines no links");
					}
				}
			}

			if (previousStop == null) {
				long stopId = Long.parseLong(entry
						.getKey()
						.getId()
						.toString()
						.substring(
								entry.getKey().getId().toString()
										.lastIndexOf("_") + 1));
				org.matsim.api.core.v01.network.Node matsimNode = scenario
						.getNetwork()
						.getNodes()
						.get(Id.create(stopId,
								org.matsim.api.core.v01.network.Node.class));

				for (Link link : wayLinks) {
					if (link.getFromNode().equals(matsimNode)) {
						previousLink = link;
						connectedWayLinks.add(previousLink);
						log.debug("stop " + entry.getKey().getName()
								+ " starts with link " + previousLink.getId());
						break;
					}
				}
			}

			nextLink = previousLink;
			while (nextLink != null) {
				nextLink = getNextConnectedLink(wayLinks, previousLink);
				if (nextLink != null) {
					connectedWayLinks.add(nextLink);
					log.debug("link " + previousLink.getId()
							+ " connects with link " + nextLink.getId());
					previousLink = nextLink;
				}
			}

			// link für stopFacility suchen
			if (connectedWayLinks.isEmpty()) {
				entry.getKey().setLinkId(previousLink.getId());
			} else {
				for (Link link : connectedWayLinks) {
					if (entry.getKey().getLinkId() != null) {
						break;
					}
					for (WaySegment segment : link2Segments.get(link)) {
						if (segment.getFirstNode().equals(
								entry.getValue().getFirstNode())) {
							entry.getKey().setLinkId(link.getId());
						}
						if (segment.getSecondNode().equals(
								entry.getValue().getFirstNode())) {
							entry.getKey().setLinkId(link.getId());
							break;
						}
					}
				}
			}
			log.debug("stop " + entry.getKey().getId() + " "+ entry.getKey().getName() +" referenced by link "
					+ entry.getKey().getLinkId());

			// links der route hinzufügen
			for (Link link : connectedWayLinks) {
				if (!link.getAllowedModes().contains("pt")) {
					Set<String> modes = new HashSet<String>();
					for (String string : link.getAllowedModes()) {
						modes.add(string);
					}
					modes.add("pt");
					link.setAllowedModes(modes);
				}
				Id<Link> id = Id.createLinkId(((LinkImpl) link).getOrigId());
				links.add(id);
			}
			previousStop = entry.getKey();
		}

		firstLinkId = links.remove(0);
		lastLinkId = links.remove(links.size() - 1);

		if (firstLinkId == null || lastLinkId == null || links.isEmpty()) {
			return null;
		}

		NetworkRoute networkRoute = new LinkNetworkRouteImpl(firstLinkId,
				lastLinkId);
		networkRoute.setLinkIds(firstLinkId, links, lastLinkId);
		return networkRoute;
	}

	private static Link getNextConnectedLink(List<Link> wayLinks,
			Link previousLink) {
		for (Link link : wayLinks) {
			if (link.getFromNode().equals(previousLink.getToNode())
					&& !link.getToNode().equals(previousLink.getFromNode())) {
				return link;
			}
		}
		return null;
	}

	private static NetworkRoute createBeeLineRoute(Relation relation,
			Scenario scenario,
			Map<TransitStopFacility, WaySegment> stops2Segment) {
		List<Id<Link>> links = new ArrayList<Id<Link>>();
		int increment = 0;
		TransitStopFacility previous = null;
		Id<Link> firstLinkId = null;
		Id<Link> lastLinkId = null;
		for (TransitStopFacility stop : stops2Segment.keySet()) {

			if (previous == null) {
				previous = stop; // create dummy link with length=null from and
									// to first stop
			}

			Set<String> mode = Collections.singleton(TransportMode.pt);
			String fromNodeId = previous.getId().toString();
			Node fromNode = (Node) relation.getDataSet().getPrimitiveById(
					Long.parseLong(fromNodeId.substring(fromNodeId
							.lastIndexOf("_") + 1)), OsmPrimitiveType.NODE);
			String toNodeId = stop.getId().toString();
			Node toNode = (Node) relation.getDataSet()
					.getPrimitiveById(
							Long.parseLong(toNodeId.substring(toNodeId
									.lastIndexOf("_") + 1)),
							OsmPrimitiveType.NODE);
			double length = fromNode.getCoor().greatCircleDistance( // beeline
																	// distance
					toNode.getCoor());
			for (Link link : createLink(scenario.getNetwork(), relation,
					fromNode, toNode, length, increment, true, false,
					120 / 3.6, 2000., 1., mode)) {
				stop.setLinkId(link.getId());
				if (increment != 0
						&& increment != stops2Segment.entrySet().size() - 1) {
					links.add(Id.createLinkId(((LinkImpl) link).getOrigId()));
				} else if (increment == 0) {
					firstLinkId = Id
							.createLinkId(((LinkImpl) link).getOrigId());
				} else if (increment == stops2Segment.entrySet().size() - 1) {
					lastLinkId = Id.createLinkId(((LinkImpl) link).getOrigId());
				}
			}
			previous = stop;
			increment++;
		}

		if (firstLinkId == null || lastLinkId == null || links.isEmpty()) {
			return null;
		}

		NetworkRoute networkRoute = new LinkNetworkRouteImpl(firstLinkId,
				lastLinkId);
		networkRoute.setLinkIds(firstLinkId, links, lastLinkId);
		return networkRoute;
	}

	// checks for used MATSim tag scheme
	private static boolean meetsMatsimReq(Map<String, String> keys) {
		if (!keys.containsKey("capacity"))
			return false;
		if (!keys.containsKey("freespeed"))
			return false;
		if (!keys.containsKey("permlanes"))
			return false;
		if (!keys.containsKey("modes"))
			return false;
		return true;
	}

	private static Double parseDoubleIfPossible(String string) {
		try {
			return Double.parseDouble(string);
		} catch (NumberFormatException e) {
			return null;
		}
	}

	// if (!node2WaySegment.containsKey(node)) {
	// double distance = Double.MAX_VALUE;
	// WaySegment segment = null;
	// for (OsmPrimitive prim : relation.getMemberPrimitivesList()) {
	// if (prim instanceof Way) {
	// Way way = (Way) prim;
	// for (int i = 0; i < way.getNodesCount() - 1; i++) {
	//
	// Node node1 = way.getNode(i);
	// Node node2 = way.getNode(i + 1);
	//
	// EastNorth pointOnSegment = Geometry
	// .closestPointToSegment(
	// node1.getEastNorth(),
	// node2.getEastNorth(),
	// node.getEastNorth());
	// double distanceTmp = node.getEastNorth()
	// .distance(pointOnSegment);
	//
	// if (distanceTmp < distance) {
	// distance = distanceTmp;
	// segment = new WaySegment(way, i);
	// }
	// }
	// }
	// }
	// node2WaySegment.put(node, segment);
	// }

}
