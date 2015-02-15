package org.matsim.contrib.josm;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
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

    final JComboBox<ProjectionChoice> importSystem = new JComboBox<>(ProjectionPreference.getProjectionChoices().toArray(new ProjectionChoice[]{}));

	public ImportDialog() {
		GridBagConstraints c = new GridBagConstraints();
		setLayout(new GridBagLayout());
		c.gridwidth = 1;
		c.weightx = 1;
		c.fill = GridBagConstraints.HORIZONTAL;
		c.gridy = 0;
		add(networkPath, c);
		
		c.gridx=1;
		add(networkPathButton, c);

		c.gridy=1;
		c.gridx=0;
		add(schedulePath,c);

		c.gridx=1;
		add(schedulePathButton,c);

		
		c.gridx = 0;
		c.gridy = 2;

        JLabel importSystemLabel = new JLabel("origin system:");
        add(importSystemLabel, c);

		c.gridx = 1;
		add(importSystem, c);
		
		networkPathButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				JFileChooser chooser = new JFileChooser(
						System.getProperty("user.home"));
				chooser.setApproveButtonText("Import");
				chooser.setDialogTitle("MATSim-Import");
				FileFilter filter = new FileNameExtensionFilter("Network-XML",
						"xml");
				chooser.setFileFilter(filter);
				int result = chooser.showOpenDialog(Main.parent);
				if (result == JFileChooser.APPROVE_OPTION
						&& chooser.getSelectedFile().getAbsolutePath() != null) {
					networkPathButton.setText(chooser.getSelectedFile().getAbsolutePath());
				}
			}
		});
        schedulePath.setEnabled(Preferences.isSupportTransit());
        schedulePathButton.setEnabled(Preferences.isSupportTransit());
		schedulePathButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				JFileChooser chooser = new JFileChooser(
						System.getProperty("user.home"));
				chooser.setApproveButtonText("Import");
				chooser.setDialogTitle("MATSim-Import");
				FileFilter filter = new FileNameExtensionFilter("TransitSchedule-XML",
						"xml");
				chooser.setFileFilter(filter);
				int result = chooser.showOpenDialog(Main.parent);
				if (result == JFileChooser.APPROVE_OPTION
						&& chooser.getSelectedFile().getAbsolutePath() != null) {
					schedulePathButton.setText(chooser.getSelectedFile().getAbsolutePath());
				}
			}
		});

		
	}
}
