package org.matsim.contrib.osm;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;

import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.actions.JosmAction;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.validation.OsmValidator;
import org.openstreetmap.josm.gui.progress.PleaseWaitProgressMonitor;
import org.openstreetmap.josm.tools.Shortcut;

/**
 * The ImportAction that handles network imports.
 *
 * @author Nico
 *
 */
@SuppressWarnings("serial")
public class CreateMasterRoutesAction extends JosmAction {



    public CreateMasterRoutesAction() {
        super(tr("Create Master Routes"), null,
                tr("Create Master Routes"), Shortcut.registerShortcut(
                        "Create Master Routes",
                        tr("Menu: {0}", tr("Create Master Routes")),
                        KeyEvent.VK_G, Shortcut.ALT_CTRL), true);
    }

    
    @Override
    public void actionPerformed(ActionEvent e) {
    	DataSet data = Main.main.getCurrentDataSet();
    	
    
    	
    	MasterRoutesTest test = new MasterRoutesTest();
		PleaseWaitProgressMonitor progMonitor = new PleaseWaitProgressMonitor(
				"Validation");

		test.startTest(progMonitor);
		test.visit(data.allPrimitives());
		test.endTest();
		progMonitor.finishTask();
		progMonitor.close();


		// set up validator layer
		OsmValidator.initializeErrorLayer();
		Main.map.validatorDialog.unfurlDialog();
		Main.main.getEditLayer().validationErrors.clear();
		Main.main.getEditLayer().validationErrors.addAll(test.getErrors());
		Main.map.validatorDialog.tree.setErrors(test.getErrors());
    }
    
    
    
    @Override
    protected void updateEnabledState() {
    	setEnabled(shouldBeEnabled());
    }

    

    private boolean shouldBeEnabled() {
    	return Main.main.getCurrentDataSet() != null;
    }
}
