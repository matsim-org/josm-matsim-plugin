package org.matsim.contrib.josm;

import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.Relation;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.data.osm.visitor.AbstractVisitor;
import org.openstreetmap.josm.gui.progress.ProgressMonitor;
import org.openstreetmap.josm.io.OsmServerReadPostprocessor;

public class FilteredOsmParser extends AbstractVisitor implements OsmServerReadPostprocessor {

	DataSet ds;

	FilteredOsmParser() {

	}

	@Override
	public void postprocessDataSet(DataSet ds, ProgressMonitor progress) {
		this.ds = ds;
		visitAll(ds, progress);
	}

	@Override
	public void visit(Node n) {
		if (n.getReferrers().size() > 0) {

		} else {
			n.setDeleted(true);
		}
	}

	@Override
	public void visit(Way w) {
		if (w.hasKey("highway") && OsmConvertDefaults.highwayTypes.contains(w.get("highway"))
				&& Main.pref.getBoolean("matsim_parse_" + w.get("highway"), true)) {

		} else if (w.getReferrers().size() > 0) {

		} else {
			w.setDeleted(true);
		}
	}

	@Override
	public void visit(Relation r) {
		if (r.hasTag("type", "public_transport") && r.hasTag("public_transport", "stop_area")) {

		} else if (r.hasTag("type", "route") && r.hasKey("route") && OsmConvertDefaults.routeTypes.contains(r.get("route"))
				&& Main.pref.getBoolean("matsim_parse_" + r.get("route"), true)) {

		} else if (r.hasTag("type", "route_master") && Main.pref.getBoolean("matsim_parse_" + r.get("route_master"), true)) {

		} else {
			r.setDeleted(true);
		}
	}

	public void visitAll(DataSet ds, ProgressMonitor progress) {
		progress.setTicksCount(ds.allPrimitives().size());
		for (OsmPrimitive p : ds.getRelations()) {
			p.accept(this);
			progress.worked(1);
		}
		for (OsmPrimitive p : ds.getWays()) {
			p.accept(this);
			progress.worked(1);
		}
		for (OsmPrimitive p : ds.getNodes()) {
			p.accept(this);
			progress.worked(1);
		}
	}
}
