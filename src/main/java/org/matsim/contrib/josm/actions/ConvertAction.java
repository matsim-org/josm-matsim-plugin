package org.matsim.contrib.josm.actions;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.swing.JOptionPane;

import org.matsim.contrib.josm.gui.Preferences;
import org.matsim.contrib.josm.model.LayerConverter;
import org.matsim.contrib.josm.model.MATSimLayer;
import org.openstreetmap.josm.actions.JosmAction;
import org.openstreetmap.josm.data.validation.OsmValidator;
import org.openstreetmap.josm.data.validation.Severity;
import org.openstreetmap.josm.data.validation.TestError;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.PleaseWaitRunnable;
import org.openstreetmap.josm.gui.progress.swing.PleaseWaitProgressMonitor;
import org.openstreetmap.josm.io.OsmTransferException;
import org.openstreetmap.josm.tools.ImageProvider;
import org.openstreetmap.josm.tools.Shortcut;
import org.xml.sax.SAXException;

/**
 * Results in a new {@link MATSimLayer} which holds the converted data.
 *
 * @author Nico
 *
 */
@SuppressWarnings("serial")
public class ConvertAction extends JosmAction {

    public ConvertAction() {
        super(tr("Convert to MATSim Layer"), null, tr("Convert Osm layer to MATSim network layer"), Shortcut.registerShortcut("menu:matsimConvert",
                tr("Menu: {0}", tr("Convert to MATSim Network")), KeyEvent.VK_G, Shortcut.ALT_CTRL), true);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
		List<TestError> breakingErrors = breakingErrors();
		if (breakingErrors.isEmpty()) {
			PleaseWaitRunnable task = new PleaseWaitRunnable("Converting to MATSim Network") {
				private MATSimLayer layer;

				@Override
				protected void cancel() {
				}

				@Override
				protected void realRun() throws SAXException, IOException, OsmTransferException {
					if (Preferences.isTransitLite()) {
						this.layer = LayerConverter.convertToPseudoNetwork(MainApplication.getLayerManager().getEditLayer());
					} else {
						this.layer = LayerConverter.convertWithFullTransit(MainApplication.getLayerManager().getEditLayer());
					}
				}

				@Override
				protected void finish() {
					if (layer != null) {
						// Do not zoom to full layer extent, but leave the view port where
						// it is.
						// (Perhaps I want to look at the particular are I am viewing right
						// now.)
						MainApplication.getLayerManager().addLayer(layer);
					}
				}
			};
			task.run();
		} else {
			OsmValidator.initializeErrorLayer();
			MainApplication.getMap().validatorDialog.unfurlDialog();
			MainApplication.getLayerManager().getEditLayer().validationErrors.clear();
			MainApplication.getLayerManager().getEditLayer().validationErrors.addAll(breakingErrors);
			MainApplication.getMap().validatorDialog.tree.setErrors(breakingErrors);
		}
    }

	private List<TestError> breakingErrors() {
		NetworkTest test1 = new NetworkTest();
		PleaseWaitProgressMonitor progMonitor1 = new PleaseWaitProgressMonitor("Validation");
		test1.startTest(progMonitor1);
		test1.visit(MainApplication.getLayerManager().getEditDataSet().allPrimitives());
		test1.endTest();
		progMonitor1.finishTask();
		progMonitor1.close();

		if (test1.getErrors().stream().anyMatch(error -> error.getSeverity().equals(Severity.ERROR))) {
			JOptionPane.showMessageDialog(MainApplication.getMainFrame(), "Export failed due to validation errors. See validation layer for details.",
					"Failure", JOptionPane.ERROR_MESSAGE, new ImageProvider("warning-small").setWidth(16).get());
			return test1.getErrors();
		}

		TransitScheduleTest test2 = new TransitScheduleTest();
		PleaseWaitProgressMonitor progMonitor2 = new PleaseWaitProgressMonitor("Validation");
		test2.startTest(progMonitor2);
		test2.visit(MainApplication.getLayerManager().getEditDataSet().allPrimitives());
		test2.endTest();
		progMonitor2.finishTask();
		progMonitor2.close();

		List<TestError> allErrors = Stream.concat(test1.getErrors().stream(), test2.getErrors().stream()).collect(Collectors.toList());
		if (test2.getErrors().stream().anyMatch(error -> error.getSeverity().equals(Severity.ERROR))) {
			JOptionPane.showMessageDialog(MainApplication.getMainFrame(), "Export failed due to validation errors. See validation layer for details.",
					"Failure", JOptionPane.ERROR_MESSAGE, new ImageProvider("warning-small").setWidth(16).get());
			return allErrors;
		} else {
			return Collections.emptyList();
		}
	}

	@Override
    protected void updateEnabledState() {
        setEnabled(MainApplication.getLayerManager().getEditLayer() != null && !(MainApplication.getLayerManager().getEditLayer() instanceof MATSimLayer));
    }

}
