package org.matsim.contrib.josm.actions;

import org.matsim.contrib.josm.model.LayerConverter;
import org.matsim.contrib.josm.model.MATSimLayer;
import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.actions.JosmAction;
import org.openstreetmap.josm.data.ProjectionBounds;
import org.openstreetmap.josm.gui.PleaseWaitRunnable;
import org.openstreetmap.josm.gui.layer.OsmDataLayer;
import org.openstreetmap.josm.io.OsmTransferException;
import org.openstreetmap.josm.tools.Shortcut;
import org.xml.sax.SAXException;

import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.io.IOException;

import static org.openstreetmap.josm.tools.I18n.tr;

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
        if (isEnabled()) {
            PleaseWaitRunnable task = new PleaseWaitRunnable("Converting to MATSim Network") {
				private MATSimLayer converter;

				@Override
				protected void cancel() {
				}

				@Override
				protected void realRun() throws SAXException, IOException, OsmTransferException {
					this.converter = LayerConverter.convertWithFullTransit((OsmDataLayer) Main.main.getActiveLayer());
				}

				@Override
				protected void finish() {
					if (converter != null) {
						// Do not zoom to full layer extent, but leave the view port where
						// it is.
						// (Perhaps I want to look at the particular are I am viewing right
						// now.)
						ProjectionBounds projectionBounds = null;
						Main.main.addLayer(converter, projectionBounds);
					}
				}
			};
            task.run();
        }
    }

    @Override
    protected void updateEnabledState() {
        setEnabled(getEditLayer() != null && !(getEditLayer() instanceof MATSimLayer));
    }

}
