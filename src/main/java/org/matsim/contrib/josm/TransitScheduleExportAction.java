// License: GPL. For details, see LICENSE file.
package org.matsim.contrib.josm;

import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.actions.DiskAccessAction;
import org.openstreetmap.josm.actions.ExtensionFileFilter;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.io.File;

import static org.openstreetmap.josm.actions.SaveActionBase.createAndOpenSaveFileChooser;
import static org.openstreetmap.josm.tools.I18n.tr;

class TransitScheduleExportAction extends DiskAccessAction {

    /**
     * Constructs a new {@code GpxExportAction}.
     */
    TransitScheduleExportAction() {
        super(tr("Export MATSim transit schedule..."), null, tr("Export the transit schedule."), null);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (isEnabled()) {
            File file = createAndOpenSaveFileChooser(tr("Export transit schedule"), new ExtensionFileFilter("xml", "xml",
                    "MATSim Transit Schedule Files (*.xml)"));
            if (file != null) {
                new TransitScheduleExporter(file).run((MATSimLayer) Main.map.mapView.getActiveLayer());
            }
        } else {
            JOptionPane.showMessageDialog(
                    Main.parent,
                    tr("Nothing to export. Get some data first."),
                    tr("Information"),
                    JOptionPane.INFORMATION_MESSAGE
            );
        }
    }

    /**
     * Refreshes the enabled state
     */
    @Override
    protected void updateEnabledState() {
        setEnabled(getEditLayer() instanceof MATSimLayer);
    }
}
