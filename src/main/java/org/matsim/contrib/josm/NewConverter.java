package org.matsim.contrib.josm;

import java.util.ArrayList;
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
import org.openstreetmap.josm.data.coor.EastNorth;
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

	static Map<String, OsmWayDefaults> wayDefaults;

	// converts complete data layer and fills the given MATSim data structures
	// as well as data mappings
	public static void convertOsmLayer(OsmDataLayer layer, Scenario scenario,
			Map<Way, List<Link>> way2Links,
			Map<Link, List<WaySegment>> link2Segments,
			Map<Relation, TransitRoute> relation2Route,
			Map<Id<TransitStopFacility>, Stop> stops) {
		log.info("=== Starting conversion of Osm data ===");
		log.setLevel(Level.OFF);

		// could be used for area filtering in future releases
		// List<JoinedPolygon> polygons = new ArrayList<JoinedPolygon>();
		// for (Way way : layer.data.getWays()) {
		// if (way.isClosed() && way.hasTag("matsim:convert_Area", "active")) {
		// polygons.add(new MultipolygonBuilder.JoinedPolygon(way));
		// }
		// }

        for (Way way : layer.data.getWays()) {
            if (!way.isDeleted() /* && isInArea(polygons, way) */) {
                convertWay(way, scenario.getNetwork(), way2Links,
                        link2Segments);
            }
        }

        for (Node node : layer.data.getNodes()) {
            if (node.hasTag("public_transport", "platform")) {
                createStop(node, scenario, way2Links, link2Segments, stops);
            }
        }

        List<Relation> publicTransportRoutesOsm = new ArrayList<>();

        // check which relations should be converted to routes differed
        // by
        // matsim and osm tag scheme
        for (Relation relation : layer.data.getRelations()) {
            if (!relation.isDeleted()
                    && relation.hasTag("type", "route")
                    && relation.hasTag("route",
                    "train", "track", "bus",
                    "light_rail", "tram", "subway")) {
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
                    way2Links, link2Segments, stops);
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

	public static void createStop(Node node, Scenario scenario,
			Map<Way, List<Link>> way2Links,
			Map<Link, List<WaySegment>> link2Segments,
			Map<Id<TransitStopFacility>, Stop> stops) {

		Node stopPosition = null;
		Way way = null;
		String name = null;
		Link link = null;
		Id<TransitStopFacility> id = Id.create(node.getUniqueId(),
				TransitStopFacility.class);
		for (OsmPrimitive primitive : node.getReferrers()) {
			if (primitive instanceof Relation
					&& primitive.hasTag("matsim", "stop_relation")
					&& primitive.hasKey("id")) {
				id = Id.create(primitive.get("id"), TransitStopFacility.class);
				name = primitive.get("name");
				for (RelationMember member : ((Relation) primitive)
						.getMembers()) {
					if (member.isNode() && member.hasRole("stop")) {
						stopPosition = member.getNode();
					}
				}
				if (stopPosition != null) {
					for (RelationMember member : ((Relation) primitive)
							.getMembers()) {
						if (member.isWay() && member.hasRole("link")) {
							if (way2Links.containsKey(member.getWay())) {
								int index = member.getWay().getNodes()
										.indexOf(stopPosition);
								WaySegment segment;
								if (index == 0) {
									segment = new WaySegment(member.getWay(),
											index);
								} else {
									segment = new WaySegment(member.getWay(),
											index - 1);
								}
								for (Link refLink : way2Links.get(member
										.getWay())) {
									if (link2Segments.get(refLink).contains(
											segment)) {
										link = refLink;
										
									}
								}
							}
							way = member.getWay();
						}
					}
				}
			}
		}

		TransitStopFacility stop = scenario.getTransitSchedule().getFactory()
				.createTransitStopFacility(id, new CoordImpl(node.getEastNorth().getX(), node.getEastNorth().getY()), true);
		if (name != null) {
			stop.setName(name);
		} else {
			stop.setName(node.getName());
		}
		if (link != null) {
			stop.setLinkId(link.getId());
		}

		scenario.getTransitSchedule().addStopFacility(stop);
		stops.put(id, new Stop(stop, stopPosition, node, way));

	}

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
						nodeOrderLog.append("(").append(l).append(") ");
					} else if (current.isConnectionNode()) {
						for (OsmPrimitive prim : current.getReferrers()) {
							if (prim instanceof Way && !prim.equals(way)) {
								if (prim.hasKey(TAG_HIGHWAY)
										|| meetsMatsimReq(prim.getKeys())) {
									nodeOrder.add(current);
									log.debug("--- Way " + way.getUniqueId()
											+ ": dumped node " + l + " ("
											+ current.getUniqueId()
											+ ") way intersection");
									nodeOrderLog.append("(").append(l).append(") ");
									break;
								}
							}
						}
					} else {
						for (OsmPrimitive prim : current.getReferrers()) {
							if (prim instanceof Relation
									&& prim.hasTag("route", "train", "track", "bus",
                                    "light_rail", "tram", "subway")
									&& prim.hasTag("type", "route")
									&& !nodeOrder.contains(current)) {
								nodeOrder.add(current);
								log.debug("--- Way " + way.getUniqueId()
										+ ": dumped node " + l + " ("
										+ current.getUniqueId()
										+ ") stop position "
										+ current.getLocalName());
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

				Double capacity = 0.;
				Double freespeed = 0.;
				Double nofLanes = 0.;
				boolean oneway = true;
				boolean onewayReverse = false;

                if (way.getKeys().containsKey(TAG_HIGHWAY)
						|| way.getKeys().containsKey(TAG_RAILWAY)) {

					String wayType;
					if (way.getKeys().containsKey(TAG_HIGHWAY)) {
						wayType = way.getKeys().get(TAG_HIGHWAY);
					} else if (way.getKeys().containsKey(TAG_RAILWAY)) {
						wayType = way.getKeys().get(TAG_RAILWAY);
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
						if ("roundabout".equals(way.getKeys().get(TAG_JUNCTION))) {
							// if "junction" is not set in tags, get()
							// returns null and
							// equals()
							// evaluates to false
							oneway = true;
						}

						// check tag "oneway"
						String onewayTag = way.getKeys().get(TAG_ONEWAY);
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

						String maxspeedTag = way.getKeys().get(TAG_MAXSPEED);
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
						String lanesTag = way.getKeys().get(TAG_LANES);
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
				if (way.getKeys().containsKey("capacity")) {
					Double capacityTag = parseDoubleIfPossible(way.getKeys()
							.get("capacity"));
					if (capacityTag != null) {
						capacity = capacityTag;
					} else {
						log.warn("--- Way " + way.getUniqueId()
								+ ": could not parse MATSim capacity tag");
					}
				}
				if (way.getKeys().containsKey("freespeed")) {
					Double freespeedTag = parseDoubleIfPossible(way.getKeys()
							.get("freespeed"));
					if (freespeedTag != null) {
						freespeed = freespeedTag;
					} else {
						log.warn("--- Way " + way.getUniqueId()
								+ ": could not parse MATSim freespeed tag");
					}
				}
				if (way.getKeys().containsKey("permlanes")) {
					Double permlanesTag = parseDoubleIfPossible(way.getKeys()
							.get("permlanes"));
					if (permlanesTag != null) {
						nofLanes = permlanesTag;
					} else {
						log.warn("--- Way " + way.getUniqueId()
								+ ": could not parse MATSim permlanes tag");
					}
				}

                Double taggedLength = null;
				if (way.getKeys().containsKey("length")) {
					Double temp = parseDoubleIfPossible(way.getKeys().get("length"));
					if (temp != null) {
						taggedLength = temp;

					} else {
						log.warn("--- Way " + way.getUniqueId()
								+ ": could not parse MATSim length tag");
					}
				}


				long increment = 0;
				for (int k = 1; k < nodeOrder.size(); k++) {
					List<WaySegment> segs = new ArrayList<>();
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
                    Double length = 0.;
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
					if (taggedLength != null) {
                        if (length != 0.0) {
                            length = taggedLength * length / way.getLength();
                        } else {
                            length = taggedLength;
                        }
					}
					List<Link> tempLinks = createLink(network, way, nodeFrom,
							nodeTo, length, increment, oneway, onewayReverse,
							freespeed, capacity, nofLanes, determineModes(way));
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
        way2Links.put(way, links);
	}

    private static Set<String> determineModes(Way way) {
        Set<String> modes = new HashSet<>();
        if (way.getKeys().containsKey("modes")) {
            Set<String> tempModes = new HashSet<>();
            String tempArray[] = way.getKeys().get("modes").split(";");
            Collections.addAll(tempModes, tempArray);
            if (tempModes.size() != 0) {
                modes.clear();
                modes.addAll(tempModes);
            } else {
                log.warn("--- Way " + way.getUniqueId()
                        + ": could not parse MATSim modes tag");
            }
        }
        if (modes.isEmpty()) {
            if (way.getKeys().containsKey(TAG_RAILWAY)) {
                modes.add(TransportMode.pt);
            }
            if (way.getKeys().containsKey(TAG_HIGHWAY)) {
                modes.add(TransportMode.car);
            }
        }
        return modes;
    }

    private static boolean elementOfConnectedRoute(Way way) {
		for (OsmPrimitive primitive : way.getReferrers()) {
			if (primitive instanceof Relation
					&& primitive.hasTag("type", "route")) {
				List<Way> list = new ArrayList<Way>();
				for (OsmPrimitive member : ((Relation) primitive)
						.getMemberPrimitivesList()) {
					if (member instanceof Way) {
						list.add((Way) member);
					}
				}
				if (list.size() < 2) {
					return false;
				}
				for (int i = 1; i < list.size(); i++) {
					if (!waysConnected(list.get(i - 1), list.get(i),
							(Relation) primitive)) {
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
            EastNorth eastNorth = node.getEastNorth();
            if (!network.getNodes().containsKey(nodeId)) {
				org.matsim.api.core.v01.network.Node nn = network
						.getFactory()
						.createNode(
								Id.create(
										node.getUniqueId(),
										org.matsim.api.core.v01.network.Node.class),
								new CoordImpl(eastNorth.getX(), eastNorth.getY()));
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
				Coord coord = new CoordImpl(eastNorth.getX(), eastNorth.getY());
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
                                              Map<Link, List<WaySegment>> link2Segments,
                                              Map<Id<TransitStopFacility>, Stop> stops) {
		NetworkRoute route;
		Map<Stop, WaySegment> stops2Segment = new LinkedHashMap<>();
		log.debug("converting route relation" + relation.getUniqueId() + " "
				+ relation.getName());
		
		// filtere stops heraus
		for (RelationMember member : relation.getMembers()) {
			if (member.isNode()
					&& !member.getMember().isIncomplete()
					&& member.getMember()
							.hasTag("public_transport", "platform")) {

				Id<TransitStopFacility> id = Id.create(member.getNode()
						.getUniqueId(), TransitStopFacility.class);
				for (OsmPrimitive primitive : member.getNode().getReferrers()) {
					if (primitive instanceof Relation
							&& primitive.hasTag("matsim", "stop_relation")
							&& primitive.hasKey("id")) {
						id = Id.create(primitive.get("id"),
								TransitStopFacility.class);
					}
				}

				stops2Segment.put(stops.get(id), null);
			}
		}
		if (stops2Segment.isEmpty()) {
			log.debug("no platforms found. relation not converted.");
			return;
		}
		log.debug("stops found. proceeding.");

		// stop position zuordnen falls nicht vorhanden
		for (Stop stop: stops2Segment.keySet()) {
			if (stop.position == null) {
				findPosition(stop, relation);
			}
		}

		// überprüfe ob alle stopPositions auf way liegen
		if (nodesOnWay(relation, stops2Segment)) {
			log.debug("all nodes on ways.");
		} else {
			log.debug("not all nodes on ways.");
		}

		// überprüfe ob alle stops verbunden sind
		if (nodesConnected(relation, stops2Segment)) {
			log.debug("all nodes are connected with each other.");
		} else {
			log.debug("not all nodes are connected with each other.");
		}

		// erstelle / überprüfe MATSim Nodes,
		for (Stop stop: stops2Segment.keySet()) {
			checkNode(scenario.getNetwork(), stop.position);
		}

		// erstelle Route zwischen den Stops
		if (nodesConnected(relation, stops2Segment)) {
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
		for (Stop stop : stops2Segment.keySet()) {
			routeStops.add(builder.createTransitRouteStop(stop.facility, 0, 0));
		}

		TransitRoute tRoute = builder.createTransitRoute(
				Id.create(relation.getUniqueId(), TransitRoute.class), route,
				routeStops, "pt");

		tLine.addRoute(tRoute);

		schedule.removeTransitLine(tLine);
		schedule.addTransitLine(tLine);
		relation2Route.put(relation, tRoute);
	}

	private static void findPosition(Stop stop, Relation relation) {
		int platformIndex = relation.getMemberPrimitivesList().indexOf(stop.platform);
		OsmPrimitive previous = relation.getMemberPrimitivesList().get(platformIndex-1);
		if (previous instanceof Node && previous.hasTag("public_transport", "stop_position") && previous.getName().equals(stop.platform.getName())) {
			stop.position = (Node) previous;
		} else {
			stop.position = stop.platform;
		}
	}

	private static boolean nodesOnWay(Relation relation,
			Map<Stop, WaySegment> stops2Segment) {
		for (Stop stop : stops2Segment.keySet()) {
			boolean nodeOnWay = false;
			
			if (stop.way!= null) {
				stops2Segment.put(stop, new WaySegment(
						stop.way, stop.way.getNodes()
								.indexOf(stop.position)));
				log.debug(stop.position.getUniqueId()
						+ " is on way "
						+ stop.way.getUniqueId()
						+ stop.way.getNodes().indexOf(
								stop.position));
				nodeOnWay = true;
			}
			
			for (OsmPrimitive prim : stop.position.getReferrers()) {
				if (prim instanceof Way) {
					if (((Way) prim).containsNode(stop.position)
							&& relation.getMemberPrimitives().contains(prim)) {
						nodeOnWay = true;
						stop.way = ((Way)prim);
						
						if (stops2Segment.get(stop) != null) {
							if (((Way) prim).getNodes().indexOf(stop.position) > stops2Segment
									.get(stop).lowerIndex) {
								stops2Segment.put(stop, new WaySegment(
										((Way) prim), ((Way) prim).getNodes()
												.indexOf(stop.position)));
								log.debug(stop.position.getUniqueId()
										+ " is on way "
										+ prim.getUniqueId()
										+ ", segment "
										+ ((Way) prim).getNodes().indexOf(
												stop.position));
							}
						} else {
							stops2Segment.put(stop, new WaySegment(
									((Way) prim), ((Way) prim).getNodes()
											.indexOf(stop.position)));
							log.debug(stop.position.getUniqueId()
									+ " is on way "
									+ prim.getUniqueId()
									+ ", segment "
									+ ((Way) prim).getNodes().indexOf(
											stop.position));
						}
					}
				}
			}
			if (!nodeOnWay) {
				stops2Segment.put(stop, null);
				return false;
			}
		}
		return true;
	}

	private static boolean nodesConnected(Relation relation,
			Map<Stop, WaySegment> stops2Segment) {
        if (!nodesOnWay(relation, stops2Segment)) {
            return false;
        }
		Stop previous = null;
		for (Entry<Stop, WaySegment> entry : stops2Segment.entrySet()) {
			if (previous != null) {
				Way way1 = stops2Segment.get(previous).way;
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

    private static NetworkRoute createConnectedWayRoute(Relation relation,
			Scenario scenario,
			Map<Stop, WaySegment> stops2Segment,
			Map<Way, List<Link>> way2Links,
			Map<Link, List<WaySegment>> link2Segments) {
		// TODO Auto-generated method stub
		log.setLevel(Level.DEBUG);
		List<Id<Link>> links = new ArrayList<Id<Link>>();
		Stop previousStop = null;
		Link previousLink = null;
		Id<Link> firstLinkId = null;
		Id<Link> lastLinkId = null;
		for (Entry<Stop, WaySegment> entry : stops2Segment
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
				
				Id <org.matsim.api.core.v01.network.Node> id = Id.create(
					entry.getKey().position.getUniqueId(),
						org.matsim.api.core.v01.network.Node.class);
				
				org.matsim.api.core.v01.network.Node matsimNode = scenario
						.getNetwork()
						.getNodes()
						.get(id);

				for (Link link : wayLinks) {
					if (link.getFromNode().equals(matsimNode)) {
						previousLink = link;
						connectedWayLinks.add(previousLink);
						log.debug("stop " + entry.getKey().position.getName()
								+ " starts with link " + previousLink.getId());
						break;
					}
				}
				if (previousLink == null) {
					for (Link link : wayLinks) {
						if (link.getToNode().equals(matsimNode)) {
							previousLink = link;
							connectedWayLinks.add(previousLink);
							log.debug("stop " + entry.getKey().position.getName()
									+ " starts with link " + previousLink.getId());
							break;
						}
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

			// link für stopFacility suchen falls nicht gegeben
			if (entry.getKey().facility.getLinkId() == null) {
				if (connectedWayLinks.isEmpty()) {
					entry.getKey().facility.setLinkId(previousLink.getId());
				} else {
					for (Link link : connectedWayLinks) {
						if (entry.getKey().facility.getLinkId() != null) {
							break;
						}
						for (WaySegment segment : link2Segments.get(link)) {
							if (segment.getFirstNode().equals(
									entry.getValue().getFirstNode())) {
								entry.getKey().facility.setLinkId(link.getId());
							}
							if (segment.getSecondNode().equals(
									entry.getValue().getFirstNode())) {
								entry.getKey().facility.setLinkId(link.getId());
								break;
							}
						}
					}
				}
			}
			log.debug("stop " + entry.getKey().facility.getId() + " "
					+ entry.getKey().facility.getName() + " referenced by link "
					+ entry.getKey().facility.getLinkId());

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
			Map<Stop, WaySegment> stops2Segment) {
		List<Id<Link>> links = new ArrayList<Id<Link>>();
		int increment = 0;
		Stop previous = null;
		Id<Link> firstLinkId = null;
		Id<Link> lastLinkId = null;
		for (Stop stop : stops2Segment.keySet()) {

			if (previous == null) {
				previous = stop; // create dummy link with length=null from and
									// to first stop
			}

			Set<String> mode = Collections.singleton(TransportMode.pt);
			String fromNodeId = previous.facility.getId().toString();
			Node fromNode = (Node) relation.getDataSet().getPrimitiveById(
					Long.parseLong(fromNodeId.substring(fromNodeId
							.lastIndexOf("_") + 1)), OsmPrimitiveType.NODE);
			String toNodeId = stop.facility.getId().toString();
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
				stop.facility.setLinkId(link.getId());
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
