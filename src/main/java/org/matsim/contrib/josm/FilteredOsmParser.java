package org.matsim.contrib.josm;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.PrimitiveId;
import org.openstreetmap.josm.data.osm.Relation;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.data.osm.visitor.AbstractVisitor;
import org.openstreetmap.josm.gui.progress.ProgressMonitor;
import org.openstreetmap.josm.io.OsmServerReadPostprocessor;

public class FilteredOsmParser extends AbstractVisitor implements OsmServerReadPostprocessor {
    
    List<PrimitiveId> delete;
    
    FilteredOsmParser() {
	delete = new ArrayList<PrimitiveId>();
    }
    
    @Override
    public void postprocessDataSet(DataSet ds, ProgressMonitor progress) {
	visitAll(ds.allPrimitives(), progress);
	
	for(PrimitiveId primitive: delete) {
	    ds.removePrimitive(primitive);
	}
	
	ds.cleanupDeletedPrimitives();
    }

    @Override
    public void visit(Node n) {
	// TODO Auto-generated method stub
	
    }

    @Override
    public void visit(Way w) {
//	if(w.hasKey("highway") && Main.pref.getBoolean("matsim_download_" + w.get("highway"), true)) {
//	    return;
//	} else {
//	    delete.add(w);
//	}
    }

    @Override
    public void visit(Relation r) {
	if(r.hasTag("type", "public_transport") && r.hasTag("public_transport", "stop_area")) {
	    
	} 
	
//	if(r.hasTag("type", "route"))
	
    }
    
    public void visitAll(Collection<OsmPrimitive> selection, ProgressMonitor progress) {
        progress.setTicksCount(selection.size());
        for (OsmPrimitive p : selection) {
            if (p.isUsable()) {
                p.accept(this);
            }
            progress.worked(1);
        }
    }
    
    
    
    
    private String getRoutePredicates() {
	int counter = 0;
	StringBuilder routes = new StringBuilder("[\"route\"~\"");
	for (String route : OsmConvertDefaults.routeTypes) {
		if (Main.pref.getBoolean("matsim_download_" + route, true)) {
			routes.append(route);
			routes.append("|");
			counter++;
		}
	}

	if (routes.lastIndexOf("|")!=-1) {
		routes.replace(routes.lastIndexOf("|"), routes.lastIndexOf("|")+1, "");
	}
	routes.append("\"]");
	if(counter == 0) {
		return null;
	}
	return routes.toString();
}

private String getStopAreaPredicate() {
	return "[\"type\"~\"public_transport\"][\"public_transport\"~\"stop_area\"]";
}

private String getHighwayPredicates() {
	int counter = 0;
	StringBuilder highways = new StringBuilder("[\"highway\"~\"");
	for (String highway : OsmConvertDefaults.highwayTypes) {
		if (Main.pref.getBoolean("matsim_download_" + highway, true)) {
			highways.append(highway);
			highways.append("|");
			counter++;
		}
	}
	if (highways.lastIndexOf("|")!=-1) {
		highways.replace(highways.lastIndexOf("|"), highways.lastIndexOf("|")+1, "");
	}
	highways.append("\"]");
	if(counter == 0) {
		return null;
	}
	return highways.toString();
}

}
