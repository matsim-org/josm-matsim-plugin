package org.matsim.contrib.osm;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.event.ActionEvent;

import org.openstreetmap.josm.actions.JosmAction;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.validation.OsmValidator;
import org.openstreetmap.josm.data.validation.Test;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.progress.swing.PleaseWaitProgressMonitor;

/**
 * @author Nico
 *
 */
@SuppressWarnings("serial")
public class RepairAction extends JosmAction {

	private Test test;

	public RepairAction(String actionName, Test test) {
		super(tr(actionName), null,
				tr(actionName), null, true);
		this.test = test;
	}


	@Override
	public void actionPerformed(ActionEvent e) {
		DataSet data = MainApplication.getLayerManager().getEditDataSet();
		PleaseWaitProgressMonitor progMonitor = new PleaseWaitProgressMonitor(
				"Validation");

		test.startTest(progMonitor);
		test.visit(data.allPrimitives());
		test.endTest();
		progMonitor.finishTask();
		progMonitor.close();


		// set up validator layer
		OsmValidator.initializeErrorLayer();
		MainApplication.getMap().validatorDialog.unfurlDialog();
		MainApplication.getLayerManager().getEditLayer().validationErrors.clear();
		MainApplication.getLayerManager().getEditLayer().validationErrors.addAll(test.getErrors());
		MainApplication.getMap().validatorDialog.tree.setErrors(test.getErrors());
	}



	@Override
	protected void updateEnabledState() {
		setEnabled(shouldBeEnabled());
	}



	private boolean shouldBeEnabled() {
		return MainApplication.getLayerManager().getEditDataSet() != null;
	}
}
