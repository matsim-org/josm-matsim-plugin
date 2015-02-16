package org.matsim.contrib.josm;

import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.gui.preferences.DefaultTabPreferenceSetting;
import org.openstreetmap.josm.gui.preferences.PreferenceSetting;
import org.openstreetmap.josm.gui.preferences.PreferenceSettingFactory;
import org.openstreetmap.josm.gui.preferences.PreferenceTabbedPane;
import org.openstreetmap.josm.gui.preferences.PreferenceTabbedPane.PreferencePanel;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import static org.openstreetmap.josm.tools.I18n.tr;

/**
 * Preferences for the MATSim Plugin
 * 
 * 
 */
final class Preferences extends DefaultTabPreferenceSetting {

    private final JCheckBox transitFeature = new JCheckBox("Transit support [alpha]");
	private final JCheckBox renderMatsim = new JCheckBox("Activate MATSim Renderer");
	private final JCheckBox showIds = new JCheckBox("Show link-Ids");
	private final JSlider wayOffset = new JSlider(0, 100);
	private final JLabel wayOffsetLabel = new JLabel("Link offset for overlapping links");
	private final JCheckBox showInternalIds = new JCheckBox("Show internal Ids in table");
	private final JCheckBox cleanNetwork = new JCheckBox("Clean Network");
	private final JCheckBox keepPaths = new JCheckBox("Keep Paths");
    private final JButton convertingDefaults = new JButton("Set converting defaults");
	private final JCheckBox filterActive = new JCheckBox("Activate hierarchy filter");
	private final JLabel hierarchyLabel = new JLabel("Only convert hierarchies up to: ");
	private final JTextField hierarchyLayer = new JTextField();

	public static class Factory implements PreferenceSettingFactory {
		@Override
		public PreferenceSetting createPreferenceSetting() {
			return new Preferences();
		}
	}

	private Preferences() {
		super("matsim-scenario.png", tr("MASim preferences"),
				tr("Configure the MATSim plugin."), false, new JTabbedPane());
	}

	private JPanel buildVisualizationPanel() {
		JPanel pnl = new JPanel(new GridBagLayout());
		GridBagConstraints cOptions = new GridBagConstraints();
        wayOffset.setValue((int) ((Main.pref.getDouble("matsim_wayOffset", 0)) / 0.03));
		showIds.setSelected(Main.pref.getBoolean("matsim_showIds"));
		renderMatsim.setSelected(Main.pref.getBoolean("matsim_renderer"));
		wayOffset.setEnabled(Main.pref.getBoolean("matsim_renderer"));
		showIds.setEnabled(Main.pref.getBoolean("matsim_renderer"));
		wayOffsetLabel.setEnabled(Main.pref.getBoolean("matsim_renderer"));
		showInternalIds.setSelected(Main.pref.getBoolean("matsim_showInternalIds", false));
		cOptions.anchor = GridBagConstraints.NORTHWEST;
		cOptions.insets = new Insets(4, 4, 4, 4);
		cOptions.weightx = 0;
		cOptions.weighty = 0;
		cOptions.gridx = 0;
		cOptions.gridy = 0;
		pnl.add(renderMatsim, cOptions);
		cOptions.gridy = 1;
		pnl.add(showIds, cOptions);
		cOptions.weightx = 0;
		cOptions.gridy = 2;
		pnl.add(wayOffsetLabel, cOptions);
		cOptions.weightx = 1;
		cOptions.gridx = 1;
		pnl.add(wayOffset, cOptions);
		cOptions.weightx = 0;
		cOptions.weighty = 1;
		cOptions.gridx = 0;
		cOptions.gridy = 3;
		pnl.add(showInternalIds, cOptions);
		return pnl;
	}

	private JPanel buildConvertPanel() {
		JPanel pnl = new JPanel(new GridBagLayout());
		GridBagConstraints cOptions = new GridBagConstraints();
        transitFeature.setSelected(isSupportTransit());
        transitFeature.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (transitFeature.isSelected()) {
                    cleanNetwork.setSelected(false);
                } else {
                    cleanNetwork.setSelected(isCleanNetwork());
                }
                cleanNetwork.setEnabled(!transitFeature.isSelected());
            }
        });
        cleanNetwork.setSelected(isCleanNetwork());
        cleanNetwork.setEnabled(!Main.pref.getBoolean("matsim_supportTransit"));
		keepPaths.setSelected(isKeepPaths());
		convertingDefaults.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				OsmConvertDefaultsDialog dialog = new OsmConvertDefaultsDialog();
				JOptionPane pane = new JOptionPane(dialog,
						JOptionPane.PLAIN_MESSAGE, JOptionPane.OK_CANCEL_OPTION);
				JDialog dlg = pane.createDialog(Main.parent, tr("Defaults"));
				dlg.setAlwaysOnTop(true);
				dlg.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
				dlg.setVisible(true);
				if (pane.getValue() != null) {
					if (((Integer) pane.getValue()) == JOptionPane.OK_OPTION) {
						dialog.handleInput();
					}
				}
				dlg.dispose();
			}
		});

		filterActive.setSelected(Main.pref.getBoolean("matsim_filterActive",
				false));
		hierarchyLayer.setText(String.valueOf(Main.pref.getInteger(
				"matsim_filter_hierarchy", 6)));

		cOptions.anchor = GridBagConstraints.NORTHWEST;

		cOptions.insets = new Insets(4, 4, 4, 4);

		cOptions.weighty = 0;
		cOptions.weightx = 0;
		cOptions.gridx = 0;
		cOptions.gridy = 0;
        pnl.add(transitFeature, cOptions);
        cOptions.gridy = 1;
        pnl.add(cleanNetwork, cOptions);
		
		cOptions.gridy = 2;
		pnl.add(keepPaths, cOptions);

		cOptions.gridy = 3;
		pnl.add(convertingDefaults, cOptions);

		cOptions.gridy = 4;
		pnl.add(filterActive, cOptions);

		cOptions.gridy = 5;
		pnl.add(hierarchyLabel, cOptions);
		cOptions.gridx = 1;
		pnl.add(hierarchyLayer, cOptions);
		
		cOptions.weighty = 1;
		cOptions.weightx = 1;
		cOptions.fill = GridBagConstraints.HORIZONTAL;
		cOptions.gridwidth = 2;
		cOptions.gridx = 0;
		cOptions.gridy = 6;
		JSeparator jSep = new JSeparator(SwingConstants.HORIZONTAL);
		pnl.add(jSep, cOptions);

		return pnl;
	}

    static boolean isKeepPaths() {
        return Main.pref.getBoolean("matsim_keepPaths", false);
    }

    static boolean isCleanNetwork() {
        return Main.pref.getBoolean("matsim_cleanNetwork", true);
    }

    static boolean isSupportTransit() {
        return Main.pref.getBoolean("matsim_supportTransit", false);
    }

    JTabbedPane buildContentPane() {
		JTabbedPane pane = getTabPane();
		pane.addTab(tr("Visualization"), buildVisualizationPanel());
		pane.addTab(tr("Converter Options"), buildConvertPanel());
		return pane;
	}

	@Override
	public void addGui(final PreferenceTabbedPane gui) {
		GridBagConstraints gc = new GridBagConstraints();
		gc.weightx = 1.0;
		gc.weighty = 1.0;
		gc.anchor = GridBagConstraints.NORTHWEST;
		gc.fill = GridBagConstraints.BOTH;
		PreferencePanel panel = gui.createPreferenceTab(this);
		panel.add(buildContentPane(), gc);
	}

	@Override
	public boolean ok() {
        Main.pref.put("matsim_supportTransit", transitFeature.isSelected());
        Main.pref.put("matsim_showIds", showIds.isSelected());
        Main.pref.put("matsim_renderer", renderMatsim.isSelected());
        Main.pref.put("matsim_cleanNetwork", cleanNetwork.isSelected());
        Main.pref.put("matsim_keepPaths", keepPaths.isSelected());
        Main.pref.put("matsim_showInternalIds", showInternalIds.isSelected());
        Main.pref.put("matsim_filterActive", filterActive.isSelected());
		Main.pref.putInteger("matsim_filter_hierarchy", Integer.parseInt(hierarchyLayer.getText()));
        Main.pref.putDouble("matsim_wayOffset", ((double) wayOffset.getValue()) * 0.03);
		return false;
	}
}
