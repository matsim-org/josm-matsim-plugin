package org.matsim.contrib.osm;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.openstreetmap.josm.command.AddCommand;
import org.openstreetmap.josm.command.ChangeCommand;
import org.openstreetmap.josm.command.Command;
import org.openstreetmap.josm.command.SequenceCommand;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.Relation;
import org.openstreetmap.josm.data.osm.RelationMember;
import org.openstreetmap.josm.data.validation.Severity;
import org.openstreetmap.josm.data.validation.Test;
import org.openstreetmap.josm.data.validation.TestError;
import org.openstreetmap.josm.gui.progress.ProgressMonitor;

/**
 * The Test which is used for the validation of MATSim content.
 *
 * @author Nico
 *
 */
public class MasterRoutesTest extends Test {

	/**
	 * Maps routes without master routes to their ref id. Routes who share the
	 * same id are added up in a list.
	 */
	private Map<String, ArrayList<Relation>> routes;

	/**
	 * Maps existing master routes to their ref id.
	 */
	private Map<String, Relation> masterRoutes;

	/**
	 * Integer code for routes without master routes.
	 */
	private final static int ROUTE_NO_MASTER = 3009;

	/**
	 * /** Creates a new {@code MATSimTest}.
	 */
	public MasterRoutesTest() {
		super(tr("MasterRoutesTest"), tr("MasterRoutesTest"));
	}

	/**
	 * Starts the test. Initializes the mappings of {@link #nodeIds} and
	 * {@link #linkIds}.
	 */
	@Override
	public void startTest(ProgressMonitor monitor) {
		this.routes = new HashMap<>();
		this.masterRoutes = new HashMap<>();
		super.startTest(monitor);
	}


	@Override
	public void visit(Relation r) {

		if (r.isUsable()) {
			if (r.hasTag("type", "route")) {
				Relation master = null;
				for (OsmPrimitive referrer : r.getReferrers()) {
					if (referrer instanceof Relation
							&& referrer.hasTag("type", "route_master")) {
						master = (Relation) referrer;
						break;
					}
				}
				if (master == null) {
					if(r.get("ref")!=null) {
						if(routes.containsKey(r.get("ref"))) {
							routes.get(r.get("ref")).add(r);
						} else {
							routes.put(r.get("ref"), new ArrayList<Relation>());
							routes.get(r.get("ref")).add(r);
						}
					} else {
						routes.put(String.valueOf(r.getUniqueId()), new ArrayList<Relation>());
						routes.get(String.valueOf(r.getUniqueId())).add(r);
					}
				} else {
					masterRoutes.put(master.get("ref"), master);
				}
			}
		}
	}


	/**
	 * Ends the test. Errors and warnings are created in this method.
	 */
	@Override
	public void endTest() {

		for(Entry<String, ArrayList<Relation>> entry: routes.entrySet()) {
			String msg = ("Route(s) "+entry.getKey()+"  with no Route Master");
			errors.add(new TestError(this, Severity.WARNING, msg,
					ROUTE_NO_MASTER, entry.getValue()));
		}
		super.endTest();
	}

	@Override
	public boolean isFixable(TestError testError) {
		return testError.getCode() == ROUTE_NO_MASTER;
	}

	@Override
	public Command fixError(TestError testError) {
		if (!isFixable(testError)) {
			return null;
		}
		List<Command> commands = new ArrayList<Command>();
		if (testError.getCode() == 3009) {


			Relation master = new Relation();
			master.put("type", "route_master");


			for(OsmPrimitive route: testError.getPrimitives()) {
				master.put("ref", route.get("ref"));
				master.addMember(new RelationMember("", route));
				if(route.hasKey("route")) {
					if(master.hasKey("route_master")) {
						if(!master.get("route_master").contains((route.get("route")))) {
							master.put("route_master", master.get("route_master")+";"+route.get("route"));
						}
					} else {
						master.put("route_master", route.get("route"));
					}
				}
			}

			if(masterRoutes.containsKey(master.get("ref"))) {
				Relation oldMaster = masterRoutes.get(master.get("ref"));
				Relation newMaster = new Relation(oldMaster);
				for(OsmPrimitive route: testError.getPrimitives()) {
					newMaster.addMember(new RelationMember("", route));
					if(master.hasKey("route_master")) {
						newMaster.put("route_master", master.get("route_master"));
					}
				}
				newMaster.getMembers().addAll(master.getMembers());
				commands.add(new ChangeCommand(oldMaster, newMaster));
			} else {
				commands.add(new AddCommand(master));
			}
			return new SequenceCommand(name, commands);
		}
		return null;
	}
}
