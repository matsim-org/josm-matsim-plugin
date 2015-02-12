package org.matsim.contrib.josm;

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
import org.matsim.core.population.routes.NetworkRoute;
import org.matsim.core.population.routes.RouteUtils;
import org.matsim.core.utils.geometry.CoordImpl;
import org.matsim.pt.transitSchedule.api.*;
import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.data.coor.EastNorth;
import org.openstreetmap.josm.data.osm.*;
import org.openstreetmap.josm.gui.dialogs.relation.sort.RelationSorter;
import org.openstreetmap.josm.gui.layer.OsmDataLayer;

import java.util.*;

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

    public static void createStopIfItIsOne(Node node, Scenario scenario, Map<Way, List<Link>> way2Links) {
        if (node.hasTag("public_transport", "platform")) {
            Node stopPosition = null;
            Way way = null;
            Link link = null;
            Id<TransitStopFacility> id = Id.create(node.getUniqueId(), TransitStopFacility.class);
            for (OsmPrimitive primitive : node.getReferrers()) {
                if (primitive instanceof Relation && primitive.hasTag("matsim", "stop_relation")) {
                    for (RelationMember member : ((Relation) primitive).getMembers()) {
                        if (member.isNode() && member.hasRole("stop")) {
                            stopPosition = member.getNode();
                        }
                        if (member.isWay() && member.hasRole("link")) {
                            way = member.getWay();
                            List<Link> links = way2Links.get(way);
                            link = links.get(links.size() - 1);
                        }
                    }
                }
            }
            TransitStopFacility stop = scenario.getTransitSchedule().getFactory().createTransitStopFacility(id, new CoordImpl(node.getEastNorth().getX(), node.getEastNorth().getY()), true);
            stop.setName(node.getName());
            if (link != null) {
                stop.setLinkId(link.getId());
            }
            scenario.getTransitSchedule().addStopFacility(stop);
        }
	}

	public static void convertWay(Way way, Network network, Map<Way, List<Link>> way2Links, Map<Link, List<WaySegment>> link2Segments) {
		log.info("### Way " + way.getUniqueId() + " (" + way.getNodesCount()
				+ " nodes) ###");
		List<Link> links = new ArrayList<>();
		wayDefaults = OsmConvertDefaults.getWayDefaults();

		if (way.getNodesCount() > 1) {
			if (way.hasTag(TAG_HIGHWAY, wayDefaults.keySet())
					|| meetsMatsimReq(way.getKeys())
					|| (way.hasTag(TAG_RAILWAY, wayDefaults.keySet()))) {
				List<Node> nodeOrder = new ArrayList<>();
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
						nodeOrderLog.append("(").append(l).append(") ");
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
			final Way way, final Node fromNode,
			final Node toNode, double length, long increment, boolean oneway,
			boolean onewayReverse, Double freespeed, Double capacity,
			Double nofLanes, Set<String> modes) {

		// only create link, if both nodes were found, node could be null, since
		// nodes outside a layer were dropped
		List<Link> links = new ArrayList<>();
		Id<org.matsim.api.core.v01.network.Node> fromId = Id.create(
				fromNode.getUniqueId(),
				org.matsim.api.core.v01.network.Node.class);
		Id<org.matsim.api.core.v01.network.Node> toId = Id.create(
				toNode.getUniqueId(),
				org.matsim.api.core.v01.network.Node.class);
		if (network.getNodes().get(fromId) != null && network.getNodes().get(toId) != null) {
			String id = String.valueOf(way.getUniqueId()) + "_" + increment;
			String origId;

			if (way.hasKey(ImportTask.WAY_TAG_ID)) {
				origId = way.get(ImportTask.WAY_TAG_ID);
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
				((LinkImpl) l).setOrigId(origId);
				network.addLink(l);
				links.add(l);
				log.info("--- Way " + way.getUniqueId() + ": link "
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
				((LinkImpl) l).setOrigId(origId + "_r");
				network.addLink(l);
				links.add(l);
				log.info("--- Way " + way.getUniqueId() + ": link "
						+ ((LinkImpl) l).getOrigId() + " created");
			}
		}
		return links;
	}

	public static void convertTransitRouteIfItIsOne(Relation relation,
                                                    Scenario scenario, Map<Relation, TransitRoute> relation2Route,
                                                    Map<Way, List<Link>> way2Links) {
        if (!relation.hasTag("type", "route")) {
            return;
        }
        RelationSorter sorter = new RelationSorter();
        sorter.sortMembers(relation.getMembers());
		log.debug("converting route relation" + relation.getUniqueId() + " " + relation.getName());
        ArrayList<TransitRouteStop> routeStops = new ArrayList<>();
        TransitSchedule schedule = scenario.getTransitSchedule();
        TransitScheduleFactory builder = schedule.getFactory();
        for (RelationMember member : relation.getMembers()) {
			if (member.isNode()
					&& !member.getMember().isIncomplete()
					&& member.getMember()
							.hasTag("public_transport", "platform")) {
				Id<TransitStopFacility> id = Id.create(member.getNode().getUniqueId(), TransitStopFacility.class);
                routeStops.add(builder.createTransitRouteStop(schedule.getFacilities().get(id), 0, 0));
			}
		}

        NetworkRoute route = createConnectedWayRoute(relation, scenario, way2Links);

		Id<TransitRoute> routeId = Id.create(relation.getUniqueId(), TransitRoute.class);
		

        Id<TransitLine> transitLineId = getTransitLineId(relation);
		TransitLine tLine;
		if (!scenario.getTransitSchedule().getTransitLines().containsKey(transitLineId)) {
			tLine = builder.createTransitLine(transitLineId);
            schedule.addTransitLine(tLine);
        } else {
			tLine = scenario.getTransitSchedule().getTransitLines().get(transitLineId);
		}
		TransitRoute tRoute = builder.createTransitRoute(routeId, route, routeStops, relation.get("route"));
		tLine.addRoute(tRoute);
		relation2Route.put(relation, tRoute);
	}

    private static Id<TransitLine> getTransitLineId(Relation relation) {
        for (OsmPrimitive primitive : relation.getReferrers()) {
            if (primitive instanceof Relation && primitive.hasTag("type", "route_master")) {
                return Id.create(primitive.get("ref"), TransitLine.class);
            }
        }
        // no enclosing transit line; use route id as line id;
        return Id.create(relation.get("ref"), TransitLine.class);
    }

    private static NetworkRoute createConnectedWayRoute(Relation relation, Scenario scenario, Map<Way, List<Link>> way2Links) {
        List<Id<Link>> links = new ArrayList<>();
        for (Way way : relation.getMemberPrimitives(Way.class)) {
            for (Link link : way2Links.get(way)) {
                links.add(link.getId());
            }
        }
        if (links.isEmpty()) {
            return null;
        } else {
            return RouteUtils.createNetworkRoute(links, scenario.getNetwork());
        }
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

}
