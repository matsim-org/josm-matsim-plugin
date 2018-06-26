package org.matsim.contrib.josm.actions;

import com.vividsolutions.jts.geom.Coordinate;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.contrib.josm.model.Export;
import org.matsim.contrib.josm.model.MATSimLayer;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.utils.geometry.geotools.MGC;
import org.matsim.core.utils.gis.PointFeatureFactory;
import org.matsim.core.utils.gis.PolylineFeatureFactory;
import org.matsim.core.utils.gis.ShapeFileWriter;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.actions.DiskAccessAction;
import org.openstreetmap.josm.actions.ExtensionFileFilter;
import org.openstreetmap.josm.gui.MainApplication;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;

import static org.openstreetmap.josm.actions.SaveActionBase.createAndOpenSaveFileChooser;
import static org.openstreetmap.josm.tools.I18n.tr;

public final class ShapeExporter extends DiskAccessAction {

    public ShapeExporter() {
        super(tr("Export MATSim network to shape file..."), null, null, null);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (isEnabled()) {
            File file = createAndOpenSaveFileChooser(tr("Export MATSim network to shape file…"), new ExtensionFileFilter("shp", "shp",
                    tr("Shape Files") +" (*.shp)"));
            if (file != null) {
                Scenario targetScenario = Export.toScenario(((MATSimLayer) MainApplication.getLayerManager().getActiveLayer()).getNetworkModel());

                Network network = targetScenario.getNetwork();
                CoordinateReferenceSystem crs = MGC.getCRS(Main.getProjection().toCode());

                Collection<SimpleFeature> features = new ArrayList<>();
                PolylineFeatureFactory linkFactory = new PolylineFeatureFactory.Builder().
                        setCrs(crs).
                        setName("link").
                        addAttribute("ID", String.class).
                        addAttribute("fromID", String.class).
                        addAttribute("toID", String.class).
                        addAttribute("length", Double.class).
                        addAttribute("type", String.class).
                        addAttribute("capacity", Double.class).
                        addAttribute("freespeed", Double.class).
                        create();

                for (Link link : network.getLinks().values()) {
                    Coordinate fromNodeCoordinate = new Coordinate(link.getFromNode().getCoord().getX(), link.getFromNode().getCoord().getY());
                    Coordinate toNodeCoordinate = new Coordinate(link.getToNode().getCoord().getX(), link.getToNode().getCoord().getY());
                    Coordinate linkCoordinate = new Coordinate(link.getCoord().getX(), link.getCoord().getY());
                    SimpleFeature ft = linkFactory.createPolyline(new Coordinate [] {fromNodeCoordinate, linkCoordinate, toNodeCoordinate},
                            new Object [] {link.getId().toString(), link.getFromNode().getId().toString(),link.getToNode().getId().toString(), link.getLength(), NetworkUtils.getType(link), link.getCapacity(), link.getFreespeed()}, null);
                    features.add(ft);
                }
                ShapeFileWriter.writeGeometries(features, file.getAbsolutePath()+"_links.shp");

                features = new ArrayList<>();
                PointFeatureFactory nodeFactory = new PointFeatureFactory.Builder().
                        setCrs(crs).
                        setName("nodes").
                        addAttribute("ID", String.class).
                        create();

                for (Node node : network.getNodes().values()) {
                    SimpleFeature ft = nodeFactory.createPoint(node.getCoord(), new Object[] {node.getId().toString()}, null);
                    features.add(ft);
                }
                ShapeFileWriter.writeGeometries(features, file.getAbsolutePath()+"_nodes.shp");
            }
        } else {
            JOptionPane.showMessageDialog(Main.parent, tr("Nothing to export. Get some data first."), tr("Information"),
                    JOptionPane.INFORMATION_MESSAGE);
        }
    }

    /**
     * Notifies me when the layer changes, but not when preferences change.
     */
    @Override
    protected void updateEnabledState() {
        setEnabled(shouldBeEnabled());
    }

    private boolean shouldBeEnabled() {
        return getLayerManager().getEditLayer() instanceof MATSimLayer;
    }
}
