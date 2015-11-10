package org.matsim.contrib.josm;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.WindowConstants;

import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.actions.JosmAction;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.gui.layer.OsmDataLayer;
import org.openstreetmap.josm.gui.progress.PleaseWaitProgressMonitor;
import org.openstreetmap.josm.io.Compression;
import org.openstreetmap.josm.io.IllegalDataException;
import org.openstreetmap.josm.io.OsmReader;
import org.openstreetmap.josm.io.OsmServerReadPostprocessor;
import org.openstreetmap.josm.tools.GBC;
import org.openstreetmap.josm.tools.ImageProvider;
import org.openstreetmap.josm.tools.Shortcut;

public class FilteredOsmParserAction extends JosmAction {

	public FilteredOsmParserAction() {
		super(tr("Read and filter OSM file ..."), null, tr("Read an OSM file, discarding everything which is not relevant for MATSim. (alpha)"), Shortcut.registerShortcut("menu:matsimParse",
				tr("Menu: {0}", tr("Parse Osm Data for MATSim content")), KeyEvent.VK_G, Shortcut.ALT_CTRL), true);
	}

	@Override
	public void actionPerformed(ActionEvent e) {

		ParseDialog dialog = new ParseDialog();

		JOptionPane pane = new JOptionPane(dialog, JOptionPane.PLAIN_MESSAGE, JOptionPane.OK_CANCEL_OPTION);
		JDialog dlg = pane.createDialog(Main.parent, tr("Parse"));
		dlg.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
		dlg.setMinimumSize(new Dimension(400, 300));
		dlg.setVisible(true);
		if (pane.getValue() != null) {
			if (((Integer) pane.getValue()) == JOptionPane.OK_OPTION) {
				if (!dialog.osmPathButton.getText().equals("choose")) {
					File file = new File(dialog.osmPathButton.getText());
					InputStream stream = null;
					try {
						stream = Compression.getUncompressedFileInputStream(file);
					} catch (IOException e1) {
						JOptionPane.showMessageDialog(Main.parent, "Import failed: " + e1.getMessage(), "Failure", JOptionPane.ERROR_MESSAGE,
								new ImageProvider("warning-small").setWidth(16).get());
					}

					DataSet data = null;
					if (stream != null) {
						OsmServerReadPostprocessor postProcessor = new FilteredOsmParser();
						OsmReader.registerPostprocessor(postProcessor);
						try {
							PleaseWaitProgressMonitor progress = new PleaseWaitProgressMonitor();
							data = OsmReader.parseDataSet(stream, progress);
							progress.finishTask();
							progress.close();
						} catch (IllegalDataException e1) {
							JOptionPane.showMessageDialog(Main.parent, "Import failed: " + e1.getMessage(), "Failure", JOptionPane.ERROR_MESSAGE,
									new ImageProvider("warning-small").setWidth(16).get());
							e1.printStackTrace();
						}
						OsmReader.deregisterPostprocessor(postProcessor);

						if (data != null) {
							OsmDataLayer layer = new OsmDataLayer(data, file.getPath(), file);
							Main.main.addLayer(layer);
							Main.map.mapView.setActiveLayer(layer);

						}
					}
				}
			}
		}
		dlg.dispose();
	}

	class ParseDialog extends JPanel {

		final JLabel osmPath = new JLabel("Osm file");
		final JButton osmPathButton = new JButton("choose");

		ParseDialog() {

			setLayout(new GridBagLayout());
			add(osmPath, GBC.std());

			osmPathButton.addActionListener(new ActionListener() {

				@Override
				public void actionPerformed(ActionEvent e) {
					JFileChooser chooser = new JFileChooser(System.getProperty("user.home"));
					chooser.setApproveButtonText("Parse");
					chooser.setDialogTitle("MATSim-Osm-Parser");
					int result = chooser.showOpenDialog(Main.parent);
					if (result == JFileChooser.APPROVE_OPTION && chooser.getSelectedFile().getAbsolutePath() != null) {
						osmPathButton.setText(chooser.getSelectedFile().getAbsolutePath());
					}
				}
			});

			add(osmPathButton, GBC.eop());

			JPanel contentPnl = new JPanel(new GridLayout(0, 1));
			contentPnl.setAlignmentX(LEFT_ALIGNMENT);
			JPanel highwaysPnl = new JPanel(new FlowLayout());
			highwaysPnl.setAlignmentX(LEFT_ALIGNMENT);
			JPanel routesPnl = new JPanel(new FlowLayout());
			routesPnl.setAlignmentX(LEFT_ALIGNMENT);

			JLabel lblHighways = new JLabel("Highways:");
			contentPnl.add(lblHighways);

			ActionListener cbListener = new ActionListener() {

				@Override
				public void actionPerformed(ActionEvent e) {
					JCheckBox cb = (JCheckBox) e.getSource();
					Main.pref.put("matsim_parse_" + cb.getText(), cb.isSelected());
				}
			};

			for (String highwayType : OsmConvertDefaults.highwayTypes) {
				JCheckBox cb = new JCheckBox(highwayType);
				cb.setToolTipText(tr("Select to download " + cb.getText() + " highways in the selected download area."));
				cb.setSelected(Main.pref.getBoolean("matsim_parse_" + cb.getText(), true));
				cb.addActionListener(cbListener);
				highwaysPnl.add(cb, GBC.std());
			}
			contentPnl.add(highwaysPnl);

			JLabel lblRoutes = new JLabel("Routes:");
			contentPnl.add(lblRoutes);

			for (String routeType : OsmConvertDefaults.routeTypes) {
				JCheckBox cb = new JCheckBox(routeType);
				cb.setToolTipText(tr("Select to download " + cb.getText() + " routes in the selected download area."));
				cb.setSelected(Main.pref.getBoolean("matsim_parse_" + cb.getText(), true));
				cb.addActionListener(cbListener);
				routesPnl.add(cb, GBC.std());
			}
			contentPnl.add(routesPnl);
			add(contentPnl);
		}
	}
}
