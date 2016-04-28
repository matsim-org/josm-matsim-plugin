package org.matsim.contrib.josm;

import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.actions.JosmAction;
import org.openstreetmap.josm.gui.preferences.projection.ProjectionChoice;
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
 *
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
		dlg.setMinimumSize(new Dimension(800, 600));
		dlg.setVisible(true);
		if (pane.getValue() != null) {
			if (((Integer) pane.getValue()) == JOptionPane.OK_OPTION) {
				if (!dialog.networkPathButton.getText().equals("choose")) {
					if (dialog.schedulePathButton.getText().equals("choose")) {
						dialog.schedulePathButton.setText(null);
					}
					ImportTask task = new ImportTask(dialog.networkPathButton.getText(), dialog.schedulePathButton.getText(),
							 dialog.getSelectedProjection());
					Main.worker.execute(task);
				}
			}
		}
		dlg.dispose();
	}
}
