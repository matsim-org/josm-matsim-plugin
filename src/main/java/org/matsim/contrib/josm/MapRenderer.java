package org.matsim.contrib.josm;

import org.matsim.api.core.v01.TransportMode;
import org.matsim.contrib.josm.model.MLink;
import org.matsim.contrib.josm.model.OsmConvertDefaults;
import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.data.Preferences.PreferenceChangeEvent;
import org.openstreetmap.josm.data.Preferences.PreferenceChangedListener;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.data.osm.visitor.paint.MapRendererFactory;
import org.openstreetmap.josm.data.osm.visitor.paint.StyledMapRenderer;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.NavigatableComponent;
import org.openstreetmap.josm.gui.mappaint.styleelement.LabelCompositionStrategy;
import org.openstreetmap.josm.gui.mappaint.styleelement.TextLabel;
import org.openstreetmap.josm.gui.mappaint.styleelement.placement.OnLineStrategy;

import java.awt.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The MATSim MapRenderer. Draws ways that correspond to existing MATSim link(s)
 * in a MATSim-blue color. Also offers offset for overlapping links as well as
 * the option to show MATSim ids on ways
 *
 * @author Nico
 */
public class MapRenderer extends StyledMapRenderer {

    public final static Properties PROPERTIES = new Properties();

    /**
     * Creates a new MapRenderer. Initialized by
     * <strong>MapRendererFactory</strong>
     *
     * @see StyledMapRenderer
     * @see MapRendererFactory
     */
    public MapRenderer(Graphics2D arg0, NavigatableComponent arg1, boolean arg2) {
        super(arg0, arg1, arg2);
    }

    /**
     * Maps links to their corresponding way.
     */
    private static Map<Way, List<MLink>> way2Links = new HashMap<>();

    public static void setWay2Links(Map<Way, List<MLink>> way2LinksTmp) {
        way2Links = way2LinksTmp;
        MainApplication.getMap().repaint();
    }

    /**
     * Draws a <code>way</code>. Ways that are mapped in <code>way2Links</code>
     * and represent MATSim links are drawn in a blue color. If "show Ids" is
     * turned on, Ids of the links are drawn on top or below the
     * <code>way</code>.
     *
     * @param showOrientation show arrows that indicate the technical orientation of the way
     *                        (defined by order of nodes)
     * @param showOneway      show symbols that indicate the direction of the feature, e.g.
     *                        oneway street or waterway
     * @param onewayReversed  for oneway=-1 and similar
     * @see #textOffset(Way)
     */
    @Override
    public void drawWay(Way way, Color color, BasicStroke line, BasicStroke dashes, Color dashedColor, float offset, boolean showOrientation,
                        boolean showHeadArrowOnly, boolean showOneway, boolean onewayReversed) {

        if (way2Links != null && way2Links.containsKey(way) && !way2Links.get(way).isEmpty()) {
            if (!way.isSelected()) {
                if (Properties.showIds) { // draw id on path
                    TextLabel label = new MATSimTextLabel(PROPERTIES, PROPERTIES.FONT, Properties.MATSIMCOLOR, 0.f, new Color(0, 145, 190));
                    drawText(way, label, new OnLineStrategy(textOffset(way)));
                }
                if (way.hasTag("modes", TransportMode.pt)) { // draw
                    // dashed
                    // lines
                    // for
                    // pt
                    // links
                    float[] dashPhase = {9.f};
                    BasicStroke trainDashes = new BasicStroke(2, 0, 1, 10.f, dashPhase, 9.f);
                    super.drawWay(way, Properties.MATSIMCOLOR, line, trainDashes, Color.white, Properties.wayOffset * -1, showOrientation,
                            showHeadArrowOnly, !way.hasTag("highway", OsmConvertDefaults.getWayDefaults().keySet()), onewayReversed);
                } else { // draw simple blue lines for other links, if
                    // way is not converted by highway tag, draw
                    // direction arrow for directed edge
                    super.drawWay(way, Properties.MATSIMCOLOR, line, dashes, dashedColor, Properties.wayOffset * -1, showOrientation,
                            showHeadArrowOnly, !way.hasTag("highway", OsmConvertDefaults.getWayDefaults().keySet()), onewayReversed);
                }
                return;
            } else {
                if (Properties.showIds) { // draw ids on selected ways
                    // also
                    TextLabel label = new MATSimTextLabel(PROPERTIES, PROPERTIES.FONT, selectedColor, 0.f, selectedColor);
                    drawText(way, label, new OnLineStrategy(textOffset(way)));
                }
            }
        }
        super.drawWay(way, color, line, dashes, dashedColor, Properties.wayOffset * -1, showOrientation, showHeadArrowOnly, showOneway,
                onewayReversed);
    }

    /**
     * Returns the text <code>offset</code> for the given <code>way</code>.
     * <strong>Positive</strong>, if the <code>id</code> of the <code>way</code>
     * 's first node is less than it's last node's <code>id</code>.
     * <strong>Negative</strong> otherwise.
     *
     * @param way The way which offset is to be calculated
     * @return The text offset for the given <code>way</code>
     */
    private int textOffset(Way way) {
        int offset = -15;

        if (way.firstNode().getUniqueId() < way.lastNode().getUniqueId()) {
            offset *= -1;
        }
        return offset;
    }

    /**
     * The properties for the text elements used to visualize the Ids of the
     * MATSim links.
     *
     * @author Nico
     */
    static class Properties implements PreferenceChangedListener, LabelCompositionStrategy {

        final static Font FONT = new Font("Droid Sans", Font.PLAIN, 14);
        final static Color MATSIMCOLOR = new Color(80, 145, 190);
        static boolean showIds = Main.pref.getBoolean("matsim_showIds", false);
        static float wayOffset = ((float) Main.pref.getDouble("matsim_wayOffset", 0));

        @Override
        // listen for changes in preferences that concern renderer adjustments
        public void preferenceChanged(PreferenceChangeEvent e) {
            if (e.getKey().equalsIgnoreCase("matsim_showIds")) {
                showIds = Main.pref.getBoolean("matsim_showIds");
            }
            if (e.getKey().equalsIgnoreCase("matsim_wayOffset")) {
                wayOffset = ((float) (Main.pref.getDouble("matsim_wayOffset", 0)));
            }
        }

        /**
         * Composes the MATSim Id text for the OsmPrimitive <code>prim</code>.
         * Multiple MATSim Ids are consecutively appended. <br>
         * <br>
         * Example: <br>
         * [{@code Id1}] [{@code Id2}] [{@code Id3}]
         *
         * @param prim The given Primitive. Only Ways can represent MATSim
         *             link-Ids
         * @return The [id]s of the links represented by the given Primitive
         * <code>prim</code> or an empty string if no link is
         * represented.
         */
        @Override
        public String compose(OsmPrimitive prim) {
            StringBuilder sB = new StringBuilder();
            if (way2Links.containsKey(prim)) {
                for (MLink link : way2Links.get(prim)) {
                    sB.append(" [").append(link.getOrigId()).append("] ");
                }
            }
            return sB.toString();
        }
    }

    static class MATSimTextLabel extends TextLabel {

        protected MATSimTextLabel(LabelCompositionStrategy labelCompositionStrategy, Font font, Color color, Float aFloat, Color color1) {
            super(labelCompositionStrategy, font, color, aFloat, color1);
        }
    }

}
