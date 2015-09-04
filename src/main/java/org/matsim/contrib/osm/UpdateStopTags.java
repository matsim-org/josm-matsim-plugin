package org.matsim.contrib.osm;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.openstreetmap.josm.command.ChangePropertyCommand;
import org.openstreetmap.josm.command.Command;
import org.openstreetmap.josm.command.SequenceCommand;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.Tag;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.data.validation.Severity;
import org.openstreetmap.josm.data.validation.Test;
import org.openstreetmap.josm.data.validation.TestError;
import org.openstreetmap.josm.gui.progress.ProgressMonitor;

/**
 * The Test which is used for the validation of OSM content.
 * 
 * @author Nico
 * 
 */
public class UpdateStopTags extends Test {

    // tries to reach accordance with
    // http://wiki.openstreetmap.org/wiki/Proposed_features/Public_Transport#Compatibility_with_well_known_tags

    /**
     * Maps primitives to a proposal of changing to new tags.
     */
    private Map<OsmPrimitive, TagCorrection> proposals;
    

    /**
     * Integer code for routes without master routes.
     */
    private final static int UPDATETAG = 3033;

    /**
     * /** Creates a new {@code MATSimTest}.
     */
    public UpdateStopTags() {
	super(tr("UpdateStopTags"), tr("UpdateStopTags"));
    }

    /**
     * Starts the test. Initializes the mappings of {@link #nodeIds} and
     * {@link #linkIds}.
     */
    @Override
    public void startTest(ProgressMonitor monitor) {
	proposals = new HashMap<OsmPrimitive, TagCorrection>();
	super.startTest(monitor);
    }

    @Override
    public void visit(Node n) {
	if (n.hasTag("highway", "bus_stop")) {
	    if (n.isReferredByWays(1)) {
		proposals.put(n, new TagCorrection(new Tag("highway", "bus_stop"), new Tag("public_transport", "stop_position")));
	    } else {
		proposals.put(n,new TagCorrection(new Tag("highway", "bus_stop"), new Tag("public_transport", "platform")));
	    }
	}
	if (n.hasTag("amenity", "bus_station")) {
	    if (n.isReferredByWays(1)) {
		proposals.put(n, new TagCorrection(new Tag("amenity", "bus_station"), new Tag("public_transport", "stop_position")));
	    } else {
		proposals.put(n, new TagCorrection(new Tag("amenity", "bus_station"), new Tag("public_transport", "platform")));
	    }
	}
	if (n.hasTag("highway", "platform")) {
	    proposals.put(n, new TagCorrection(new Tag("highway", "platform"), new Tag("public_transport", "platform")));
	}
	if (n.hasTag("railway", "tram_stop") && n.isReferredByWays(1)) {
	    proposals.put(n, new TagCorrection(new Tag("railway", "tram_stop"), new Tag("public_transport", "stop_position")));
	}
	if (n.hasTag("railway", "halt") && n.isReferredByWays(1)) {
	    proposals.put(n, new TagCorrection(new Tag("railway", "halt"), new Tag("public_transport", "stop_position")));
	}
	if (n.hasTag("railway", "platform")) {
	    proposals.put(n, new TagCorrection(new Tag("railway", "platform"), new Tag("public_transport", "platform")));
	}
    }

    @Override
    public void visit(Way w) {
	if (w.hasTag("highway", "bus_stop")) {
	    proposals.put(w, new TagCorrection(new Tag("highway", "bus_stop"), new Tag("public_transport", "platform")));
	}
	if (w.hasTag("amenity", "bus_station")) {
	    proposals.put(w, new TagCorrection(new Tag("amenity", "bus_station"), new Tag("public_transport", "platform")));
	}
	if (w.hasTag("highway", "platform")) {
	    proposals.put(w, new TagCorrection(new Tag("highway", "platform"), new Tag("public_transport", "platform")));
	}
	if (w.hasTag("railway", "platform")) {
	    proposals.put(w, new TagCorrection(new Tag("railway", "platform"),
		    new Tag("public_transport", "platform")));
	}
    }

    /**
     * Ends the test. Errors and warnings are created in this method.
     */
    @Override
    public void endTest() {

	 for(Entry<OsmPrimitive, TagCorrection> entry: proposals.entrySet()) {
	     String msg = (entry.getValue().oldTag+" on "+entry.getKey().getType().toString()+" should be replaced by "+entry.getValue().newTag);
	     errors.add(new TestError(this, Severity.WARNING, msg,
		     UPDATETAG, entry.getKey()));
	 }

	super.endTest();
    }

    @Override
    public boolean isFixable(TestError testError) {
	return testError.getCode() == UPDATETAG;
    }

    @Override
    public Command fixError(TestError testError) {
	if (!isFixable(testError)) {
	    return null;
	}
	if (testError.getCode() == 3033) {
	    List<Command> commands = new ArrayList<Command>();
	    for (OsmPrimitive prim: testError.getPrimitives()) {
		commands.add(new ChangePropertyCommand(prim, proposals.get(prim).newTag.getKey(), proposals.get(prim).newTag.getValue()));
		commands.add(new ChangePropertyCommand(prim, proposals.get(prim).oldTag.getKey(), null));
            }
	    return new SequenceCommand("Commands", commands);
        }
        return null;
    }

    class TagCorrection {

	Tag oldTag;
	Tag newTag;

	private TagCorrection(Tag oldT, Tag newT) {
	    oldTag = oldT;
	    newTag = newT;
	}
    }
}
