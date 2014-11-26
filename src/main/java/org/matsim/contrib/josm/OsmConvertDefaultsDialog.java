package org.matsim.contrib.josm;

import org.matsim.contrib.josm.OsmConvertDefaults.OsmWayDefaults;
import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.tools.ImageProvider;

import javax.swing.*;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.HashMap;
import java.util.Map;

import static org.openstreetmap.josm.tools.I18n.tr;

/**
 * This dialog is used to show and edit the values which are used when
 * converting osm data
 * 
 * @author nkuehnel
 * 
 */
@SuppressWarnings("serial")
class OsmConvertDefaultsDialog extends JPanel {
    private final Map<String, JComponent> input = new HashMap<>();

    public OsmConvertDefaultsDialog() {
		setLayout(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 4, 4, 4);
		c.gridwidth = 1;
		c.weightx = 0.8;
		c.fill = GridBagConstraints.HORIZONTAL;
		for (int i = 0; i < OsmConvertDefaults.wayTypes.length; i++) {
			for (int j = 0; j < OsmConvertDefaults.wayAttributes.length; j++) {
				if (i == 0) {
					c.gridy = 0;
					c.gridx = (j + 1);
					add(new JLabel(tr(OsmConvertDefaults.wayAttributes[j])), c);
				}
				if (j == 0) {
					c.gridx = 0;
					c.gridy = (i + 1);
					String type = OsmConvertDefaults.wayTypes[i];

					ImageIcon icon;
					try {
						icon = ImageProvider.get("presets", type);
					} catch (RuntimeException exception) {
						if (type.contains("_")) {
							type = type.substring(0, type.indexOf("_"));
						}
						try {
							icon = ImageProvider.get("presets", type);
						} catch (RuntimeException exception2) {
							type = "way_" + type;
							try {
								icon = ImageProvider.get("presets", type);
							} catch (RuntimeException exception3) {
								icon = null;
							}
						}
					}
					add(new JLabel(tr(OsmConvertDefaults.wayTypes[i]), icon,
							JLabel.LEFT), c);
				}
				c.gridy = (i + 1);
				c.gridx = (j + 1);

				if (j < OsmConvertDefaults.wayAttributes.length - 1) {
					JTextField tF_hierarchy = new JTextField();
					add(tF_hierarchy, c);
					input.put(i + "_" + j, tF_hierarchy);
				} else {
					JCheckBox oneway = new JCheckBox("oneway");
					add(oneway, c);
					input.put(i + "_" + j, oneway);
				}
			}
		}

		JButton reset = new JButton("reset");
		c.gridx = 0;
		c.gridy = (OsmConvertDefaults.wayTypes.length + 1);
		c.gridwidth = 4;
		add(reset, c);

		fillValues();

		reset.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				OsmConvertDefaults.reset();
				OsmConvertDefaults.load();
				fillValues();
			}
		});
	}

	private void fillValues() {

		for (int i = 0; i < OsmConvertDefaults.wayTypes.length; i++) {
			OsmWayDefaults highwayDefault = OsmConvertDefaults
					.getWayDefaults().get(OsmConvertDefaults.wayTypes[i]);
			for (int j = 0; j < OsmConvertDefaults.wayAttributes.length; j++) {
				String value;
				switch (j) {
				case 0:
					value = String.valueOf(highwayDefault.hierarchy);
					((JTextField) input.get(i + "_" + j)).setText(value);
					break;
				case 1:
					value = String.valueOf(highwayDefault.lanes);
					((JTextField) input.get(i + "_" + j)).setText(value);
					break;
				case 2:
					value = String.valueOf(highwayDefault.freespeed);
					((JTextField) input.get(i + "_" + j)).setText(value);
					break;
				case 3:
					value = String.valueOf(highwayDefault.freespeedFactor);
					((JTextField) input.get(i + "_" + j)).setText(value);
					break;
				case 4:
					value = String.valueOf(highwayDefault.laneCapacity);
					((JTextField) input.get(i + "_" + j)).setText(value);
					break;
				case 5:
					((JCheckBox) input.get(i + "_" + j))
							.setSelected(highwayDefault.oneway);
					break;
				}
			}
		}
	}

	// processes the input given by user and stores values in preferences
    void handleInput() {
		for (int i = 0; i < OsmConvertDefaults.wayTypes.length; i++) {

			int hierarchy = Integer.parseInt(((JTextField) input.get(i + "_0"))
					.getText());
			double lanes = Double
					.parseDouble(((JTextField) input.get(i + "_1")).getText());
			double freespeed = Double.parseDouble(((JTextField) input.get(i
					+ "_2")).getText());
			double freespeedFactor = Double.parseDouble(((JTextField) input
					.get(i + "_3")).getText());
			double laneCapacity = Double.parseDouble(((JTextField) input.get(i
					+ "_4")).getText());
			boolean oneway = (((JCheckBox) input.get(i + "_5")).isSelected());

			OsmConvertDefaults.getWayDefaults().put(
					OsmConvertDefaults.wayTypes[i],
					new OsmWayDefaults(hierarchy, lanes, freespeed,
							freespeedFactor, laneCapacity, oneway));
			Main.pref.put("matsim_convertDefaults_"
					+ OsmConvertDefaults.wayTypes[i], hierarchy + ";" + lanes
					+ ";" + freespeed + ";" + freespeedFactor + ";"
					+ laneCapacity + ";" + oneway);
		}
	}
}
