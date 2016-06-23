package org.matsim.contrib.josm.actions;

import org.matsim.contrib.josm.model.*;
import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.actions.JosmAction;
import org.openstreetmap.josm.data.ProjectionBounds;
import org.openstreetmap.josm.gui.PleaseWaitRunnable;
import org.openstreetmap.josm.io.OsmTransferException;
import org.openstreetmap.josm.tools.Shortcut;
import org.xml.sax.SAXException;

import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.io.IOException;

import static org.openstreetmap.josm.tools.I18n.tr;

public class ConvertToPseudoNetworkAction extends JosmAction {

	public ConvertToPseudoNetworkAction() {
		super(tr("Convert to transit pseudo-network"), null, tr("Convert to transit pseudo-network"), Shortcut.registerShortcut("menu:matsimPseudoNetwork",
				tr("Menu: {0}", tr("Convert to transit pseudo-network")), KeyEvent.VK_G, Shortcut.ALT_CTRL), true);
	}

	@Override
	public void actionPerformed(ActionEvent e) {
		if (isEnabled()) {
			Main.worker.submit(new PleaseWaitRunnable(tr("Convert to transit pseudo-network")) {
				@Override
				protected void cancel() {

				}

				@Override
				protected void realRun() throws SAXException, IOException, OsmTransferException {
					ProjectionBounds projectionBounds = null;
					Main.main.addLayer(LayerConverter.convertToPseudoNetwork(getEditLayer()), projectionBounds);
				}

				@Override
				protected void finish() {

				}
			});
		}
	}

	@Override
	protected void updateEnabledState() {
		setEnabled(getEditLayer() != null && !(getEditLayer() instanceof MATSimLayer));
	}

}
