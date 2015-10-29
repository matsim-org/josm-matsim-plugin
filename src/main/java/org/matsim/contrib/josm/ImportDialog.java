package org.matsim.contrib.josm;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.filechooser.FileFilter;
import javax.swing.filechooser.FileNameExtensionFilter;

import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.gui.preferences.projection.ProjectionChoice;
import org.openstreetmap.josm.gui.preferences.projection.ProjectionPreference;
import org.openstreetmap.josm.tools.GBC;

/**
 * the import dialog
 *
 * @author Nico
 *
 */
@SuppressWarnings("serial")
class ImportDialog extends JPanel {
	// the JOptionPane that contains this dialog. required for the closeDialog()
	// method.

	/**
	 * Holds the path of the import file
	 */
	final JLabel networkPath = new JLabel("network");
	final JButton networkPathButton = new JButton("choose");
	final JLabel schedulePath = new JLabel("transit schedule");
	final JButton schedulePathButton = new JButton("choose");

	final JComboBox<ProjectionChoice> importSystem = new JComboBox<>(ProjectionPreference.getProjectionChoices().toArray(new ProjectionChoice[] {}));
	private JPanel projSubPrefPanel;
	private JPanel projSubPrefPanelWrapper = new JPanel(new GridBagLayout());

	public ImportDialog() {
		setLayout(new GridBagLayout());
		add(networkPath, GBC.std());

		add(networkPathButton, GBC.eop());

		add(schedulePath, GBC.std());

		add(schedulePathButton, GBC.eop());

		JLabel importSystemLabel = new JLabel("origin system:");
		add(importSystemLabel, GBC.std());

		importSystem.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				ProjectionChoice pc = (ProjectionChoice) importSystem.getSelectedItem();
				selectedProjectionChanged(pc);
			}
		});
		add(importSystem, GBC.eop());

		add(projSubPrefPanelWrapper, GBC.eop());

		selectedProjectionChanged((ProjectionChoice) importSystem.getSelectedItem());

		networkPathButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				JFileChooser chooser = new JFileChooser(System.getProperty("user.home"));
				chooser.setApproveButtonText("Import");
				chooser.setDialogTitle("MATSim-Import");
				FileFilter filter = new FileNameExtensionFilter("Network-XML", "xml");
				chooser.setFileFilter(filter);
				int result = chooser.showOpenDialog(Main.parent);
				if (result == JFileChooser.APPROVE_OPTION && chooser.getSelectedFile().getAbsolutePath() != null) {
					networkPathButton.setText(chooser.getSelectedFile().getAbsolutePath());
				}
			}
		});
		schedulePath.setEnabled(Preferences.isSupportTransit());
		schedulePathButton.setEnabled(Preferences.isSupportTransit());
		schedulePathButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				JFileChooser chooser = new JFileChooser(System.getProperty("user.home"));
				chooser.setApproveButtonText("Import");
				chooser.setDialogTitle("MATSim-Import");
				FileFilter filter = new FileNameExtensionFilter("TransitSchedule-XML", "xml");
				chooser.setFileFilter(filter);
				int result = chooser.showOpenDialog(Main.parent);
				if (result == JFileChooser.APPROVE_OPTION && chooser.getSelectedFile().getAbsolutePath() != null) {
					schedulePathButton.setText(chooser.getSelectedFile().getAbsolutePath());
				}
			}
		});

	}

	/**
	 * Handles all the work related to update the projection-specific
	 * preferences
	 *
	 * @param pc
	 *            the choice class representing user selection
	 */
	private void selectedProjectionChanged(final ProjectionChoice pc) {
		// Don't try to update if we're still starting up
		int size = getComponentCount();
		if (size < 1)
			return;

		final ActionListener listener = new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				// updateMeta(pc);
			}
		};

		// Replace old panel with new one
		projSubPrefPanelWrapper.removeAll();
		projSubPrefPanel = pc.getPreferencePanel(listener);
		projSubPrefPanelWrapper.add(projSubPrefPanel, GBC.std().fill(GBC.BOTH).weight(1.0, 1.0));
		revalidate();
		repaint();
		// updateMeta(pc);
	}

}
