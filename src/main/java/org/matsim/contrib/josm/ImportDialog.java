package org.matsim.contrib.josm;

import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.Collection;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSeparator;
import javax.swing.SwingConstants;
import javax.swing.filechooser.FileFilter;
import javax.swing.filechooser.FileNameExtensionFilter;

import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.gui.preferences.projection.ProjectionChoice;
import org.openstreetmap.josm.gui.preferences.projection.ProjectionPreference;
import org.openstreetmap.josm.tools.GBC;
import org.openstreetmap.josm.tools.ImageProvider;

/**
 * the import dialog
 *
 * @author Nico
 *
 */
@SuppressWarnings("serial")
class ImportDialog extends JPanel {

	/**
	 * Holds the path of the import file
	 */
	final JLabel networkHeading = new JLabel("Network:");
	final JLabel networkPath = new JLabel("...");
	final JButton networkPathButton = new JButton(new ImageProvider("open.png").getResource().getImageIcon(new Dimension(10, 10)));
	final JLabel schedulePathHeading = new JLabel("Transit Schedule:");
	final JLabel schedulePath = new JLabel("...");
	final JButton schedulePathButton = new JButton(new ImageProvider("open.png").getResource().getImageIcon(new Dimension(10, 10)));
	final JLabel importSystemLabel = new JLabel("Origin System:");

	final JComboBox<ProjectionChoice> importSystemCB = new JComboBox<>(ProjectionPreference.getProjectionChoices().toArray(new ProjectionChoice[] {}));
	private JPanel projSubPrefPanelWrapper = new JPanel(new GridBagLayout());
	private JPanel projSubPrefPanel;
	
	private ProjectionChoice projectionChoice = null;
	private Collection<String> prefs = null;
	
	private File networkFile = null;
	private File scheduleFile = null;

	public ImportDialog() {
		
		networkHeading.setFont(networkHeading.getFont().deriveFont(Font.BOLD));
		schedulePathHeading.setFont(networkHeading.getFont().deriveFont(Font.BOLD));
		importSystemLabel.setFont(networkHeading.getFont().deriveFont(Font.BOLD));
		networkPath.setBorder(BorderFactory.createEtchedBorder());
		schedulePath.setBorder(BorderFactory.createEtchedBorder());
		
		setLayout(new GridBagLayout());
		
		add(networkHeading, GBC.eop());
		add(networkPath, GBC.eop().fill(GridBagConstraints.HORIZONTAL) );
		add(networkPathButton, GBC.eop());
		
		JSeparator sep = new JSeparator(SwingConstants.HORIZONTAL);
		sep.setPreferredSize(new Dimension(400,3));
		add(sep, GBC.eop());

		add(schedulePathHeading, GBC.eop());
		add(schedulePath, GBC.eop().fill(GridBagConstraints.HORIZONTAL) );
		add(schedulePathButton, GBC.eop());
		
		JSeparator sep2 = new JSeparator(SwingConstants.HORIZONTAL);
		sep2.setPreferredSize(new Dimension(400,3));
		add(sep2, GBC.eop());

		add(importSystemLabel, GBC.eop());

		importSystemCB.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				ProjectionChoice pc = (ProjectionChoice) importSystemCB.getSelectedItem();
				selectedProjectionChanged(pc);
			}
		});
		add(importSystemCB, GBC.eop());

		add(projSubPrefPanelWrapper, GBC.eop());

		selectedProjectionChanged((ProjectionChoice) importSystemCB.getSelectedItem());

		networkPathButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				JFileChooser chooser;
				if(networkFile == null) {
					chooser = new JFileChooser(System.getProperty("user.home"));
				} else {
					chooser = new JFileChooser(networkFile.getAbsolutePath());
				}
				chooser.setApproveButtonText("Import");
				chooser.setDialogTitle("MATSim-Import");
				FileFilter filter = new FileNameExtensionFilter("Network-XML", "xml");
				chooser.setFileFilter(filter);
				int result = chooser.showOpenDialog(Main.parent);
				if (result == JFileChooser.APPROVE_OPTION && chooser.getSelectedFile().getAbsolutePath() != null) {
					networkFile = new File(chooser.getSelectedFile().getAbsolutePath());
					networkPath.setText(networkFile.getName());
				}
			}
		});
		schedulePath.setEnabled(Preferences.isSupportTransit());
		schedulePathButton.setEnabled(Preferences.isSupportTransit());
		schedulePathButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				JFileChooser chooser;
				if(scheduleFile == null) {
					chooser = new JFileChooser(System.getProperty("user.home"));
				} else {
					chooser = new JFileChooser(networkFile.getAbsolutePath());
				}
				chooser.setApproveButtonText("Import");
				chooser.setDialogTitle("MATSim-Import");
				FileFilter filter = new FileNameExtensionFilter("TransitSchedule-XML", "xml");
				chooser.setFileFilter(filter);
				int result = chooser.showOpenDialog(Main.parent);
				if (result == JFileChooser.APPROVE_OPTION && chooser.getSelectedFile().getAbsolutePath() != null) {
					scheduleFile = new File(chooser.getSelectedFile().getAbsolutePath());
					schedulePath.setText(scheduleFile.getName());
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
				prefs = pc.getPreferences(projSubPrefPanel);
			}
		};

		// Replace old panel with new one
		projSubPrefPanelWrapper.removeAll();
		projSubPrefPanel = pc.getPreferencePanel(listener);
		projSubPrefPanelWrapper.add(projSubPrefPanel, GBC.std().fill(GBC.BOTH).weight(1.0, 1.0));
		revalidate();
		repaint();
		projectionChoice = pc;
	}
	
	
	public ProjectionChoice getProjectionChoice() {
		return projectionChoice;
	}
	
	public Collection<String> getPrefs() {
		return prefs;
	}
	
	public File getNetworkFile() {
		return networkFile;
	}
	
	public File getScheduleFile() {
		return scheduleFile;
	}
	

}
