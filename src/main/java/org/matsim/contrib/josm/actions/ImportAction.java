package org.matsim.contrib.josm.actions;

import org.matsim.contrib.josm.gui.ImportDialog;
import org.matsim.contrib.josm.model.Importer;
import org.matsim.contrib.josm.model.MATSimLayer;
import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.actions.JosmAction;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.PleaseWaitRunnable;
import org.openstreetmap.josm.gui.preferences.projection.ProjectionChoice;
import org.openstreetmap.josm.gui.preferences.projection.ProjectionPreference;
import org.openstreetmap.josm.tools.Shortcut;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;

import static org.openstreetmap.josm.tools.I18n.tr;

/**
 * The ImportAction that handles network imports.
 *
 * @author Nico
 */
@SuppressWarnings("serial")
public class ImportAction extends JosmAction {

    public ImportAction() {
        super(tr("Import MATSim scenario"), "open.png", tr("Import MATSim scenario"), Shortcut.registerShortcut("menu:matsimImport",
                tr("Menu: {0}", tr("MATSim Import")), KeyEvent.VK_G, Shortcut.ALT_CTRL), true);
    }

    @Override
    public void actionPerformed(ActionEvent e) {

        ImportDialog dialog = new ImportDialog();
        JOptionPane pane = new JOptionPane(dialog, JOptionPane.PLAIN_MESSAGE, JOptionPane.OK_CANCEL_OPTION);

        JDialog dlg = pane.createDialog(Main.parent, tr("Import"));
        dlg.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        dlg.setMinimumSize(new Dimension(400, 600));
        dlg.setVisible(true);
        if (pane.getValue() != null) {
            if (((Integer) pane.getValue()) == JOptionPane.OK_OPTION) {
                if (dialog.getNetworkFile() != null) {

                    ProjectionChoice pc = dialog.getProjectionChoice();

                    String id = pc.getId();
                    ProjectionPreference.setProjection(id, dialog.getPrefs(), false);

                    final java.io.File network = dialog.getNetworkFile();
                    final java.io.File schedule = dialog.getScheduleFile();
                    PleaseWaitRunnable task = new PleaseWaitRunnable("MATSim Import") {
                        private MATSimLayer layer;
                        private final Importer importer = new Importer(network, schedule);

                        @Override
                        protected void cancel() {
                        }

                        @Override
                        protected void finish() {
                            // layer = null happens if Exception happens during import,
                            // as Exceptions are handled only after this method is called.
                            if (layer != null) {
                                MainApplication.getLayerManager().addLayer(layer);
                                MainApplication.getLayerManager().setActiveLayer(layer);
                            }
                        }

                        @Override
                        protected void realRun() {
                            try {
                                layer = importer.createMatsimLayer();
                            } catch (Exception e) {
                                JOptionPane.showMessageDialog(Main.parent, "Error while parsing MATSim network file. Maybe it isn't one?", "Error", 1);
                            }
                        }
                    };
                    MainApplication.worker.execute(task);
                }
            }
        }
        dlg.dispose();
    }

}
