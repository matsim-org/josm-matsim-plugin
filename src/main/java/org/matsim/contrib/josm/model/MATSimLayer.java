package org.matsim.contrib.josm.model;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.Action;

import org.matsim.api.core.v01.network.Link;
import org.matsim.contrib.josm.scenario.EditableScenario;
import org.matsim.contrib.josm.scenario.EditableTransitRoute;
import org.openstreetmap.josm.actions.RenameLayerAction;
import org.openstreetmap.josm.actions.SaveActionBase;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.Relation;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.data.osm.WaySegment;
import org.openstreetmap.josm.gui.dialogs.LayerListDialog;
import org.openstreetmap.josm.gui.dialogs.LayerListPopup;
import org.openstreetmap.josm.gui.layer.Layer;
import org.openstreetmap.josm.gui.layer.OsmDataLayer;

/**
 * a layer which contains MATSim-network data to differ from normal OSM layers
 *
 * @author nkuehnel
 *
 */
public class MATSimLayer extends OsmDataLayer {

    private final NetworkModel networkModel;

    public MATSimLayer(DataSet data, String name, File associatedFile, NetworkModel networkModel) {
        super(data, name, associatedFile);
        this.networkModel = networkModel;
    }

    @Override
    public Action[] getMenuEntries() {
        List<Action> actions = new ArrayList<>();
        actions.addAll(Arrays.asList(LayerListDialog.getInstance().createActivateLayerAction(this), LayerListDialog.getInstance()
                        .createShowHideLayerAction(), LayerListDialog.getInstance().createDeleteLayerAction(), SeparatorLayerAction.INSTANCE,
                new LayerSaveAsAction(this)));
        actions.addAll(Arrays.asList(SeparatorLayerAction.INSTANCE, new RenameLayerAction(getAssociatedFile(), this)));
        actions.addAll(Arrays.asList(SeparatorLayerAction.INSTANCE, new LayerListPopup.InfoAction(this)));
        return actions.toArray(new Action[actions.size()]);
    }

    @Override
    public boolean isMergable(final Layer other) {
        return false;
    }

    @Override
    public boolean requiresUploadToServer() {
        return false;
    }

    @Override
    public File createAndOpenSaveFileChooser() {
        return SaveActionBase.createAndOpenSaveFileChooser(tr("Save MATSim network file"), "xml");
    }

    public NetworkModel getNetworkModel() {
        return networkModel;
    }
}
