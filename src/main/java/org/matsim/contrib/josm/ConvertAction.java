package org.matsim.contrib.josm;

import org.openstreetmap.josm.actions.JosmAction;
import org.openstreetmap.josm.gui.layer.OsmDataLayer;
import org.openstreetmap.josm.tools.Shortcut;

import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;

import static org.openstreetmap.josm.tools.I18n.tr;

/**
 * The Convert Action which causes the {@link org.matsim.contrib.josm.ConvertTask} to start. Results
 * in a new {@link org.matsim.contrib.josm.MATSimLayer} which holds the converted data.
 *
 * @author Nico
 *
 */
@SuppressWarnings("serial")
class ConvertAction extends JosmAction {

    public ConvertAction() {
        super(tr("Convert to MATSim Layer"), null,
                tr("Convert Osm layer to MATSim network layer"), Shortcut
                        .registerShortcut(
                                "menu:matsimConvert",
                                tr("Menu: {0}",
                                        tr("Convert to MATSim Network")),
                                KeyEvent.VK_G, Shortcut.ALT_CTRL), true);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (isEnabled()) {
            ConvertTask task = new ConvertTask();
            task.run();
        }
    }

    @Override
    protected void updateEnabledState() {
        setEnabled(getEditLayer() instanceof OsmDataLayer
                && !(getEditLayer() instanceof MATSimLayer));
    }
}
