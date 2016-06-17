package org.matsim.contrib.osm;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.command.ChangePropertyCommand;
import org.openstreetmap.josm.command.Command;
import org.openstreetmap.josm.command.SequenceCommand;
import org.openstreetmap.josm.data.osm.*;
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

	@Override
	public void startTest(ProgressMonitor monitor) {
		proposals = new HashMap<>();
		super.startTest(monitor);
	}

	@Override
	public void visit(Node n) {
		if (n.hasTag("highway", "bus_stop")) {
			if (n.isReferredByWays(1)) {
				shouldBeTaggedStopPosition(n);
			} else {
				shouldBeTaggedPlatform(n);
			}
		}
		if (n.hasTag("amenity", "bus_station")) {
			if (n.isReferredByWays(1)) {
				shouldBeTaggedStopPosition(n);
			} else {
				shouldBeTaggedPlatform(n);
			}
		}
		if (n.hasTag("highway", "platform")) {
			shouldBeTaggedPlatform(n);
		}
		if (n.hasTag("railway", "tram_stop") && n.isReferredByWays(1)) {
			shouldBeTaggedStopPosition(n);
		}
		if (n.hasTag("railway", "halt") && n.isReferredByWays(1)) {
			shouldBeTaggedStopPosition(n);
		}
		if (n.hasTag("railway", "platform")) {
			shouldBeTaggedPlatform(n);
		}
	}

	@Override
	public void visit(Way w) {
		if (w.hasTag("highway", "bus_stop")) {
			shouldBeTaggedPlatform(w);
		}
		if (w.hasTag("amenity", "bus_station")) {
			shouldBeTaggedPlatform(w);
		}
		if (w.hasTag("highway", "platform")) {
			shouldBeTaggedPlatform(w);
		}
		if (w.hasTag("railway", "platform")) {
			shouldBeTaggedPlatform(w);
		}
	}

	@Override
	public void visit(Relation relation) {
		for (RelationMember relationMember : relation.getMembers()) {
			if (relationMember.hasRole("platform")) {
				shouldBeTaggedPlatform(relationMember.getMember());
			} else if (relationMember.hasRole("stop")) {
				shouldBeTaggedStopPosition(relationMember.getMember());
			}
		}
	}

	private void shouldBeTaggedPlatform(OsmPrimitive primitive) {
		if (!primitive.hasTag("public_transport", "platform")) {
			proposals.put(primitive, new TagCorrection(new Tag("public_transport", "platform")));
		}
	}

	private void shouldBeTaggedStopPosition(OsmPrimitive primitive) {
		if (!primitive.hasTag("public_transport", "stop_position")) {
			proposals.put(primitive, new TagCorrection(new Tag("public_transport", "stop_position")));
		}
	}


	/**
	 * Ends the test. Errors and warnings are created in this method.
	 */
	@Override
	public void endTest() {

		for(Entry<OsmPrimitive, TagCorrection> entry: proposals.entrySet()) {
			String msg = (entry.getKey().getType().toString()+" should be tagged "+entry.getValue().newTag);
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
			List<Command> commands = new ArrayList<>();
			for (OsmPrimitive prim: testError.getPrimitives()) {
				commands.add(new ChangePropertyCommand(prim, proposals.get(prim).newTag.getKey(), proposals.get(prim).newTag.getValue()));
			}
			return new SequenceCommand("Commands", commands);
		}
		return null;
	}

	class TagCorrection {

		Tag newTag;

		private TagCorrection(Tag newT) {
			newTag = newT;
		}
	}
}
