package org.matsim.contrib.josm.gui;

import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.data.preferences.BooleanProperty;
import org.openstreetmap.josm.data.preferences.IntegerProperty;
import org.openstreetmap.josm.data.preferences.StringProperty;
import org.openstreetmap.josm.gui.preferences.DefaultTabPreferenceSetting;
import org.openstreetmap.josm.gui.preferences.PreferenceSetting;
import org.openstreetmap.josm.gui.preferences.PreferenceSettingFactory;
import org.openstreetmap.josm.gui.preferences.PreferenceTabbedPane;
import org.openstreetmap.josm.gui.preferences.PreferenceTabbedPane.PreferencePanel;
import org.openstreetmap.josm.spi.preferences.Config;

import javax.swing.*;
import java.awt.*;

import static org.openstreetmap.josm.tools.I18n.tr;

/**
 * Preferences for the MATSim Plugin
 *
 *
 */
public final class Preferences extends DefaultTabPreferenceSetting {

	//General
	private final JLabel exportNetworkVersionLabel = new JLabel(tr("Exported network xml version"));
	private final String[] networkVersionOptions = {"v2", "v1"};
	private final JComboBox<String> exportNetworkVersionCB = new JComboBox<>(networkVersionOptions);

	//Visualization
	private final JCheckBox renderMatsim = new JCheckBox(tr("Activate MATSim Renderer"));
	private final JCheckBox showIds = new JCheckBox(tr("Show link-Ids"));
	private final JSlider wayOffset = new JSlider(0, 100);
	private final JLabel wayOffsetLabel = new JLabel(tr("Link offset for overlapping links"));
	private final JCheckBox showInternalIds = new JCheckBox(tr("Show internal Ids in table"));

	//Converter
	private final JCheckBox transitFeature = new JCheckBox(tr("Transit support [alpha]"));
	private final JCheckBox transitLite = new JCheckBox(tr("Transit routes lite"));
	private final JCheckBox cleanNetwork = new JCheckBox(tr("Clean Network"));
	private final JCheckBox keepPaths = new JCheckBox(tr("Keep Paths"));
	private final JButton convertingDefaults = new JButton(tr("Set conversion defaults"));
	private final JCheckBox includeRoadType = new JCheckBox(tr("Include road type in output"));
	private final JCheckBox filterActive = new JCheckBox(tr("Activate hierarchy filter"));
	private final JLabel hierarchyLabel = new JLabel(tr("Only convert hierarchies up to: "));
	private final JTextField hierarchyLayer = new JTextField();


	public static final BooleanProperty PROP_CLEAN_NETWORK = new BooleanProperty("matsim_cleanNetwork", true);
	private static final IntegerProperty PROP_FILTER_HIERARCHY = new IntegerProperty("matsim_filter_hierarchy", 6);
	private static final BooleanProperty PROP_INCLUDE_ROAD_TYPE = new BooleanProperty("matsim_includeRoadType", false);
	private static final BooleanProperty PROP_KEEP_PATHS = new BooleanProperty("matsim_keepPaths", false);
	private static final StringProperty PROP_NETWORK_VERSION = new StringProperty("matsim_networkVersion", "v2");
	private static final BooleanProperty PROP_SUPPORT_TRANSIT = new BooleanProperty("matsim_supportTransit", false);
	private static final BooleanProperty PROP_TRANSIT_LITE = new BooleanProperty("matsim_transit_lite", false);


  public static class Factory implements PreferenceSettingFactory {
		@Override
		public PreferenceSetting createPreferenceSetting() {
			return new Preferences();
		}
	}

	private Preferences() {
		super("matsim-scenario.png", tr("MASim preferences"), tr("Configure the MATSim plugin."), false, new JTabbedPane());
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
		PROP_NETWORK_VERSION.put((String) exportNetworkVersionCB.getSelectedItem());
		setSupportTransit(transitFeature.isSelected());
		setTransitLite(transitLite.isSelected());
		Config.getPref().putBoolean("matsim_showIds", showIds.isSelected());
		Config.getPref().putBoolean("matsim_renderer", renderMatsim.isSelected());
		PROP_CLEAN_NETWORK.put(cleanNetwork.isSelected());
		PROP_KEEP_PATHS.put(keepPaths.isSelected());
		Config.getPref().putBoolean("matsim_showInternalIds", showInternalIds.isSelected());
		Config.getPref().putBoolean("matsim_filterActive", filterActive.isSelected());
		PROP_INCLUDE_ROAD_TYPE.put(includeRoadType.isSelected());
		PROP_FILTER_HIERARCHY.put(Integer.parseInt(hierarchyLayer.getText()));
		Config.getPref().putDouble("matsim_wayOffset", ((double) wayOffset.getValue()) * 0.03);
		return false;
	}

	private JTabbedPane buildContentPane() {
		JTabbedPane pane = getTabPane();
		pane.addTab(tr("General"), buildGeneralPanel());
		pane.addTab(tr("Visualization"), buildVisualizationPanel());
		pane.addTab(tr("Converter"), buildConvertPanel());
		return pane;
	}

	private Component buildGeneralPanel() {
		JPanel pnl = new JPanel(new GridBagLayout());
		GridBagConstraints cOptions = new GridBagConstraints();

		exportNetworkVersionCB.setSelectedItem(getNetworkExportVersion());

		cOptions.insets = new Insets(4, 4, 4, 4);
		cOptions.anchor = GridBagConstraints.NORTHWEST;
		cOptions.gridx = 0;
		cOptions.gridy = 0;
		pnl.add(exportNetworkVersionLabel, cOptions);
		cOptions.gridx = 1;
		cOptions.weightx = 1;
		cOptions.weighty=1;
		pnl.add(exportNetworkVersionCB, cOptions);
		return pnl;
	}

	private JPanel buildVisualizationPanel() {
		JPanel pnl = new JPanel(new GridBagLayout());
		GridBagConstraints cOptions = new GridBagConstraints();
		wayOffset.setValue((int) ((Config.getPref().getDouble("matsim_wayOffset", 0)) / 0.03));
		showIds.setSelected(Config.getPref().getBoolean("matsim_showIds"));
		renderMatsim.setSelected(Config.getPref().getBoolean("matsim_renderer"));
		renderMatsim.addActionListener(e -> {
			if (!renderMatsim.isSelected()) {
				showIds.setSelected(false);
			} else {
				showIds.setSelected(isCleanNetwork());
			}
			showIds.setEnabled(renderMatsim.isSelected());
			wayOffset.setEnabled(renderMatsim.isSelected());
			showIds.setEnabled(renderMatsim.isSelected());
			wayOffsetLabel.setEnabled(renderMatsim.isSelected());
		});
		wayOffset.setEnabled(renderMatsim.isSelected());
		showIds.setEnabled(renderMatsim.isSelected());
		wayOffsetLabel.setEnabled(renderMatsim.isSelected());
		showInternalIds.setSelected(new BooleanProperty("matsim_showInternalIds", false).get());
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
		transitFeature.addActionListener(e -> {
			if (transitFeature.isSelected()) {
				cleanNetwork.setSelected(false);
				transitLite.setSelected(isTransitLite());
			} else {
				cleanNetwork.setSelected(isCleanNetwork());
				transitLite.setSelected(false);
			}
			cleanNetwork.setEnabled(!transitFeature.isSelected());
		});

		transitLite.setSelected(isSupportTransit() && isTransitLite());

		cleanNetwork.setSelected(isCleanNetwork());
		cleanNetwork.setEnabled(!transitFeature.isSelected());
		keepPaths.setSelected(isKeepPaths());
		convertingDefaults.addActionListener(e -> {
			OsmConvertDefaultsDialog dialog = new OsmConvertDefaultsDialog();
			JOptionPane pane = new JOptionPane(dialog, JOptionPane.PLAIN_MESSAGE, JOptionPane.OK_CANCEL_OPTION);
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
		});

		includeRoadType.setSelected(PROP_INCLUDE_ROAD_TYPE.get());
		filterActive.setSelected(new BooleanProperty("matsim_filterActive", false).get());
		hierarchyLayer.setText(String.valueOf(PROP_FILTER_HIERARCHY.get()));

		cOptions.anchor = GridBagConstraints.NORTHWEST;

		cOptions.insets = new Insets(4, 4, 4, 4);

		cOptions.weighty = 0;
		cOptions.weightx = 0;
		cOptions.gridx = 0;
		cOptions.gridy = 0;
		pnl.add(transitFeature, cOptions);

		cOptions.gridx = 1;
		pnl.add(transitLite, cOptions);

		cOptions.gridx = 0;
		cOptions.gridy++;
		pnl.add(cleanNetwork, cOptions);

		cOptions.gridy++;
		pnl.add(keepPaths, cOptions);

		cOptions.gridy++;
		pnl.add(convertingDefaults, cOptions);

		cOptions.gridy++;
		pnl.add(includeRoadType, cOptions);

		cOptions.gridy++;
		pnl.add(filterActive, cOptions);

		cOptions.gridy++;
		pnl.add(hierarchyLabel, cOptions);
		cOptions.gridx = 1;
		pnl.add(hierarchyLayer, cOptions);

		cOptions.weighty = 1;
		cOptions.weightx = 1;
		cOptions.fill = GridBagConstraints.HORIZONTAL;
		cOptions.gridwidth = 2;
		cOptions.gridx = 0;
		cOptions.gridy++;
		JSeparator jSep = new JSeparator(SwingConstants.HORIZONTAL);
		pnl.add(jSep, cOptions);

		return pnl;
	}

	public static String getNetworkExportVersion() {
		return PROP_NETWORK_VERSION.get();
	}

	public static boolean isKeepPaths() {
		return PROP_KEEP_PATHS.get();
	}

	public static boolean isCleanNetwork() {
		return PROP_CLEAN_NETWORK.get();
	}

	public static boolean isSupportTransit() {
		return PROP_SUPPORT_TRANSIT.get();
	}

	public static void setSupportTransit(boolean supportTransit) {
		PROP_SUPPORT_TRANSIT.put(supportTransit);
	}

	public static boolean includeRoadType() {
		return PROP_INCLUDE_ROAD_TYPE.get();
	}

	public static boolean isTransitLite() {
		return PROP_TRANSIT_LITE.get();
	}

	public static void setTransitLite(boolean transitLite) {
		PROP_TRANSIT_LITE.put(transitLite);
	}

	public static int getMatsimFilterHierarchy() {
		return PROP_FILTER_HIERARCHY.get();
	}

}
