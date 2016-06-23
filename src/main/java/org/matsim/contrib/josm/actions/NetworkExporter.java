package org.matsim.contrib.josm.actions;

import java.io.File;
import java.io.IOException;

import javax.swing.JOptionPane;

import org.matsim.api.core.v01.Scenario;
import org.matsim.contrib.josm.model.Export;
import org.matsim.contrib.josm.model.MATSimLayer;
import org.matsim.contrib.josm.scenario.EditableScenario;
import org.matsim.core.network.NetworkWriter;
import org.matsim.core.network.algorithms.NetworkCleaner;
import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.actions.ExtensionFileFilter;
import org.openstreetmap.josm.data.validation.OsmValidator;
import org.openstreetmap.josm.data.validation.Severity;
import org.openstreetmap.josm.data.validation.TestError;
import org.openstreetmap.josm.gui.layer.Layer;
import org.openstreetmap.josm.gui.layer.OsmDataLayer;
import org.openstreetmap.josm.gui.progress.PleaseWaitProgressMonitor;
import org.openstreetmap.josm.io.FileExporter;
import org.openstreetmap.josm.tools.ImageProvider;

/**
 * The FileExporter that handles file output when saving. Runs
 * {@link NetworkTest} prior to the {@link Export}.
 *
 * @author Nico
 *
 */
public final class NetworkExporter extends FileExporter {

	/**
	 * Creates a new {@code MATSimNetworkFileExporter}. <br>
	 * Extension used is {@code .xml}.
	 */
	public NetworkExporter() {
		super(new ExtensionFileFilter("xml", "xml", "MATSim Network Files (*.xml)"));
	}

	/**
	 * Checks whether the {@code layer} with the given {@code pathname} can be
	 * saved as MATSim network.
	 *
	 * @param pathname
	 *            The path to which the network should be written to
	 * @param layer
	 *            The layer which holds the data
	 * @return <code>true</code> if the given {@code layer} is a
	 *         {@link MATSimLayer}. <code>false</code> otherwise
	 */
	@Override
	public boolean acceptFile(File pathname, Layer layer) {
		return layer instanceof MATSimLayer;
	}

	/**
	 * Exports the MATSim network of the given {@code layer} into the given
	 * {@code file}. <br>
	 * <br>
	 * Before exporting a {@link NetworkTest} is convertWithFullTransit, resulting in a validation
	 * layer. The export fails if severe errors are found.
	 *
	 * @see Export
	 * @param file
	 *            The {@code .xml} network-file to which the network data is
	 *            stored to
	 * @param layer
	 *            The layer which holds the network data (must be a
	 *            {@link MATSimLayer})
	 */
	@Override
	public void exportData(File file, Layer layer) throws IOException {

		NetworkTest test = new NetworkTest();
		PleaseWaitProgressMonitor progMonitor = new PleaseWaitProgressMonitor("Validation");

		// convertWithFullTransit validator tests
		test.startTest(progMonitor);
		test.visit(((OsmDataLayer) layer).data.allPrimitives());
		test.endTest();
		progMonitor.finishTask();
		progMonitor.close();

		boolean okToExport = true;

		for (TestError error : test.getErrors()) {
			if (error.getSeverity().equals(Severity.ERROR)) {
				JOptionPane.showMessageDialog(Main.parent, "Export failed due to validation errors. See validation layer for details.", "Failure",
						JOptionPane.ERROR_MESSAGE, new ImageProvider("warning-small").setWidth(16).get());
				okToExport = false; // abort export when errors occur
				break;
			}
		}

		if (okToExport) { // check if export should be continued when
			// warnings occur
			for (TestError error : test.getErrors()) {
				if (error.getSeverity().equals(Severity.WARNING)) {
					int proceed = JOptionPane.showConfirmDialog(Main.parent, "Validaton resulted in warnings.\n Proceed?", "Warning",
							JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
					if (proceed == JOptionPane.NO_OPTION) {
						okToExport = false;
						break;
					} else if (proceed == JOptionPane.YES_OPTION) {
						break;
					}
				}
			}
		}

		// start export task if not aborted
		if (okToExport) {
			EditableScenario layerScenario = ((MATSimLayer) layer).getNetworkModel().getScenario();
			Scenario targetScenario = Export.convertIdsAndFilterDeleted(layerScenario);

			if (Main.pref.getBoolean("matsim_cleanNetwork")) {
				new NetworkCleaner().run(targetScenario.getNetwork());
			}
			new NetworkWriter(targetScenario.getNetwork()).write(file.getPath());
		}

		// set up error layer
		OsmValidator.initializeErrorLayer();
		Main.map.validatorDialog.unfurlDialog();
		Main.main.getEditLayer().validationErrors.clear();
		Main.main.getEditLayer().validationErrors.addAll(test.getErrors());
		Main.map.validatorDialog.tree.setErrors(test.getErrors());

	}
}