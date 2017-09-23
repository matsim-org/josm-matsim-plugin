package org.matsim.contrib.josm;

import javafx.embed.swing.JFXPanel;
import org.matsim.contrib.josm.actions.*;
import org.matsim.contrib.josm.gui.LinksToggleDialog;
import org.matsim.contrib.josm.gui.PTToggleDialog;
import org.matsim.contrib.josm.gui.StopAreasToggleDialog;
import org.matsim.contrib.josm.gui.Preferences;
import org.matsim.contrib.josm.model.OsmConvertDefaults;
import org.matsim.contrib.osm.*;
import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.actions.ExtensionFileFilter;
import org.openstreetmap.josm.data.Preferences.PreferenceChangeEvent;
import org.openstreetmap.josm.data.Preferences.PreferenceChangedListener;
import org.openstreetmap.josm.data.osm.visitor.paint.MapRendererFactory;
import org.openstreetmap.josm.data.validation.OsmValidator;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.MainMenu;
import org.openstreetmap.josm.gui.MapFrame;
import org.openstreetmap.josm.gui.download.DownloadSelection;
import org.openstreetmap.josm.gui.preferences.PreferenceSetting;
import org.openstreetmap.josm.data.preferences.sources.ValidatorPrefHelper;
import org.openstreetmap.josm.gui.tagging.presets.TaggingPreset;
import org.openstreetmap.josm.gui.tagging.presets.TaggingPresetMenu;
import org.openstreetmap.josm.gui.tagging.presets.TaggingPresetReader;
import org.openstreetmap.josm.gui.tagging.presets.TaggingPresetSeparator;
import org.openstreetmap.josm.plugins.Plugin;
import org.openstreetmap.josm.plugins.PluginInformation;
import org.openstreetmap.josm.spi.preferences.Config;
import org.xml.sax.SAXException;

import javax.swing.*;
import java.awt.event.KeyEvent;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;

import static org.openstreetmap.josm.tools.I18n.marktr;

/**
 * This is the main class for the MATSim plugin.
 *
 * @see Plugin
 *
 * @author Nico
 *
 */
public class MATSimPlugin extends Plugin implements PreferenceChangedListener {

	public MATSimPlugin(PluginInformation info) {
		super(info);

		new JFXPanel(); // super-weird, but we get random deadlocks on OSX when we don't initialize JavaFX early

		// add xml exporter for matsim data
		ExtensionFileFilter.addExporterFirst(new NetworkExporter());

		MainMenu menu = MainApplication.getMenu();

		JMenu jMenu1 = menu.addMenu(marktr("OSM Repair"),marktr("OSM Repair") , KeyEvent.VK_CIRCUMFLEX, menu.getDefaultMenuPos(), "OSM Repair Tools");
		jMenu1.add(new JMenuItem(new RepairAction("Create Master Routes", new MasterRoutesTest())));
		jMenu1.add(new JMenuItem(new RepairAction("Check for Incomplete Routes", new IncompleteRoutesTest())));
		jMenu1.add(new JMenuItem(new RepairAction("Update Stop Tags", new UpdateStopTags())));
		jMenu1.add(new JMenuItem(new RepairAction("Create Stop Areas", new CreateStopAreas())));

		JMenu jMenu2 = menu.addMenu(marktr("MATSim"), marktr("MATSim"), KeyEvent.VK_DIVIDE, menu.getDefaultMenuPos(), "MATSim Tools");
		jMenu2.add(new ImportAction());
		jMenu2.add(new NewNetworkAction());
		jMenu2.add(new ConvertAction());
		jMenu2.add(new DownloadAction());
		jMenu2.add(new DownloadVBBAction());
		jMenu2.add(new JSeparator());
		jMenu2.add(new RepairAction("Validate TransitSchedule", new TransitScheduleTest()));
		TransitScheduleExportAction transitScheduleExportAction = new TransitScheduleExportAction();
		Main.pref.addPreferenceChangeListener(transitScheduleExportAction);
		jMenu2.add(transitScheduleExportAction);
		jMenu2.add(new OTFVisAction());

		// read tagging preset
		Reader reader = new InputStreamReader(getClass().getResourceAsStream("matsimPreset.xml"));
		Collection<TaggingPreset> tps;
		try {
			tps = TaggingPresetReader.readAll(reader, true);
		} catch (SAXException e) {
			throw new RuntimeException(e);
		}
		for (TaggingPreset tp : tps) {
			if (!(tp instanceof TaggingPresetSeparator)) {
				MainApplication.getToolbar().register(tp);
			}
		}
//		AutoCompletionManager.cachePresets(tps);
		HashMap<TaggingPresetMenu, JMenu> submenus = new HashMap<>();
		for (final TaggingPreset p : tps) {
			JMenu m = p.group != null ? submenus.get(p.group) : MainApplication.getMenu().presetsMenu;
			if (p instanceof TaggingPresetSeparator) {
				m.add(new JSeparator());
			} else if (p instanceof TaggingPresetMenu) {
				JMenu submenu = new JMenu(p);
				submenu.setText(p.getLocaleName());
				((TaggingPresetMenu) p).menu = submenu;
				submenus.put((TaggingPresetMenu) p, submenu);
				m.add(submenu);
			} else {
				JMenuItem mi = new JMenuItem(p);
				mi.setText(p.getLocaleName());
				m.add(mi);
			}
		}

		// register map renderer
		if (Main.pref.getBoolean("matsim_renderer", false)) {
			MapRendererFactory factory = MapRendererFactory.getInstance();
			factory.register(MapRenderer.class, "MATSim Renderer", "This is the MATSim map renderer");
			factory.activate(MapRenderer.class);
		}

		// register for preference changed events
		Main.pref.addPreferenceChangeListener(this);
		Main.pref.addPreferenceChangeListener(MapRenderer.PROPERTIES);
		OsmConvertDefaults.listen(Config.getPref());

		// load default converting parameters

		//register validators
		List<String> matsimTests = new ArrayList<>();
		OsmValidator.addTest(NetworkTest.class);
		matsimTests.add(NetworkTest.class.getName());

		//make sure MATSim Validators aren't executed before upload
		Main.pref.putCollection(ValidatorPrefHelper.PREF_SKIP_TESTS_BEFORE_UPLOAD, matsimTests);
	}

	public void addDownloadSelection(List<DownloadSelection> list) {

	}

	@Override
	public void mapFrameInitialized(MapFrame oldFrame, MapFrame newFrame) {
		if (newFrame != null) {
			MainApplication.getMap().addToggleDialog(new LinksToggleDialog());
			PTToggleDialog toggleDialog1 = new PTToggleDialog();
			MainApplication.getMap().addToggleDialog(toggleDialog1);
			toggleDialog1.init(); // after being added
			StopAreasToggleDialog toggleDialog2 = new StopAreasToggleDialog();
			MainApplication.getMap().addToggleDialog(toggleDialog2);
			toggleDialog2.init(); // after being added
		}
	}

	@Override
	public PreferenceSetting getPreferenceSetting() {
		return new Preferences.Factory().createPreferenceSetting();
	}

	@Override
	public void preferenceChanged(PreferenceChangeEvent e) {
		if (e.getKey().equalsIgnoreCase("matsim_renderer")) {
			MapRendererFactory factory = MapRendererFactory.getInstance();
			if (Main.pref.getBoolean("matsim_renderer")) {
				factory.register(MapRenderer.class, "MATSim Renderer", "This is the MATSim map renderer");
				factory.activate(MapRenderer.class);
			} else {
				factory.activateDefault();
				factory.unregister(MapRenderer.class);
			}
		}
	}
}
