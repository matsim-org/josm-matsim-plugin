// License: GPL. For details, see LICENSE file.
package org.matsim.contrib.josm;

import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.actions.DiskAccessAction;
import org.openstreetmap.josm.actions.ExtensionFileFilter;
import org.openstreetmap.josm.data.validation.OsmValidator;
import org.openstreetmap.josm.data.validation.Severity;
import org.openstreetmap.josm.data.validation.TestError;
import org.openstreetmap.josm.gui.layer.OsmDataLayer;
import org.openstreetmap.josm.gui.progress.PleaseWaitProgressMonitor;
import org.openstreetmap.josm.tools.ImageProvider;

import javax.swing.*;

import java.awt.event.ActionEvent;
import java.io.File;

import static org.openstreetmap.josm.actions.SaveActionBase.createAndOpenSaveFileChooser;
import static org.openstreetmap.josm.tools.I18n.tr;

class TransitScheduleExportAction extends DiskAccessAction implements
	org.openstreetmap.josm.data.Preferences.PreferenceChangedListener {

    /**
     * Constructs a new {@code GpxExportAction}.
     */
    TransitScheduleExportAction() {
	super(tr("Export MATSim transit schedule..."), null,
		tr("Export the transit schedule."), null);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
	if (isEnabled()) {
	    File file = createAndOpenSaveFileChooser(
		    tr("Export transit schedule"), new ExtensionFileFilter(
			    "xml", "xml",
			    "MATSim Transit Schedule Files (*.xml)"));
	    if (file != null) {

		// TransitScheduleTest test = new TransitScheduleTest();
		// PleaseWaitProgressMonitor progMonitor = new
		// PleaseWaitProgressMonitor("Validation");
		//
		// // run validator tests
		// test.startTest(progMonitor);
		// test.endTest();
		// progMonitor.finishTask();
		// progMonitor.close();
		//
		// boolean okToExport = true;
		//
		// for (TestError error : test.getErrors()) {
		// if (error.getSeverity().equals(Severity.ERROR)) {
		// JOptionPane.showMessageDialog(Main.parent,
		// "Export failed due to validation errors. See validation layer for details.",
		// "Failure", JOptionPane.ERROR_MESSAGE, new
		// ImageProvider("warning-small").setWidth(16).get());
		// okToExport = false; // abort export when errors occur
		// break;
		// }
		// }
		//
		// if (okToExport) { // check if export should be continued when
		// // warnings occur
		// for (TestError error : test.getErrors()) {
		// if (error.getSeverity().equals(Severity.WARNING)) {
		// int proceed = JOptionPane.showConfirmDialog(Main.parent,
		// "Validaton resulted in warnings.\n Proceed?", "Warning",
		// JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
		// if (proceed == JOptionPane.NO_OPTION) {
		// okToExport = false;
		// break;
		// } else if (proceed == JOptionPane.YES_OPTION) {
		// break;
		// }
		// }
		// }
		// }

		// start export task if not aborted
		// if (okToExport) {
		new TransitScheduleExporter(file)
			.run((MATSimLayer) Main.map.mapView.getActiveLayer());
		// }

		// set up error layer
		// OsmValidator.initializeErrorLayer();
		// Main.map.validatorDialog.unfurlDialog();
		// Main.main.getEditLayer().validationErrors.clear();
		// Main.main.getEditLayer().validationErrors.addAll(test.getErrors());
		// Main.map.validatorDialog.tree.setErrors(test.getErrors());

	    }
	} else {
	    JOptionPane.showMessageDialog(Main.parent,
		    tr("Nothing to export. Get some data first."),
		    tr("Information"), JOptionPane.INFORMATION_MESSAGE);
	}
    }

    /**
     * Notifies me when the layer changes, but not when preferences change.
     */
    @Override
    protected void updateEnabledState() {
	setEnabled(shouldBeEnabled());
    }

    @Override
    public void preferenceChanged(
	    org.openstreetmap.josm.data.Preferences.PreferenceChangeEvent preferenceChangeEvent) {
	setEnabled(shouldBeEnabled());
    }

    private boolean shouldBeEnabled() {
	return getEditLayer() instanceof MATSimLayer
		&& ((MATSimLayer) getEditLayer()).getScenario().getConfig()
			.transit().isUseTransit();
    }

}
