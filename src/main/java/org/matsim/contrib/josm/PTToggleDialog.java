package org.matsim.contrib.josm;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.Component;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;

import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.contrib.josm.scenario.EditableScenario;
import org.matsim.contrib.josm.scenario.EditableScenarioUtils;
import org.matsim.contrib.josm.scenario.EditableTransitRoute;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;
import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.data.SelectionChangedListener;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.OsmPrimitiveType;
import org.openstreetmap.josm.data.osm.Relation;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.data.osm.WaySegment;
import org.openstreetmap.josm.data.osm.event.AbstractDatasetChangedEvent;
import org.openstreetmap.josm.data.osm.event.DataSetListenerAdapter;
import org.openstreetmap.josm.data.osm.event.DatasetEventManager;
import org.openstreetmap.josm.data.osm.event.SelectionEventManager;
import org.openstreetmap.josm.gui.MapView;
import org.openstreetmap.josm.gui.dialogs.ToggleDialog;
import org.openstreetmap.josm.gui.layer.OsmDataLayer;
import org.openstreetmap.josm.gui.util.HighlightHelper;

/**
 * The ToggleDialog that shows link information of selected ways and route
 * information of selected route relation elements
 *
 * @author Nico
 */

@SuppressWarnings("serial")
class PTToggleDialog extends ToggleDialog implements MapView.EditLayerChangeListener, NetworkListener.ScenarioDataChangedListener {
	private final JTable table_pt;
	private NetworkListener osmNetworkListener;

	private final DataSetListenerAdapter dataSetListenerAdapter = new DataSetListenerAdapter(new DataSetListenerAdapter.Listener() {
		@Override
		public void processDatasetEvent(AbstractDatasetChangedEvent abstractDatasetChangedEvent) {
			notifyDataChanged();
		}
	});
	private final SelectionChangedListener selectionListener = new SelectionChangedListener() {
		@Override
		public void selectionChanged(Collection<? extends OsmPrimitive> osmPrimitives) {
			notifyDataChanged();
		}
	};
	private final MATSimTableModel_pt tableModel_pt;

	@Override
	public void showNotify() {
		DatasetEventManager.getInstance().addDatasetListener(dataSetListenerAdapter, DatasetEventManager.FireMode.IN_EDT_CONSOLIDATED);
		SelectionEventManager.getInstance().addSelectionListener(selectionListener, DatasetEventManager.FireMode.IN_EDT_CONSOLIDATED);
		MapView.addEditLayerChangeListener(this);
		notifyEverythingChanged();
	}

	@Override
	public void hideNotify() {
		DatasetEventManager.getInstance().removeDatasetListener(dataSetListenerAdapter);
		SelectionEventManager.getInstance().removeSelectionListener(selectionListener);
		MapView.removeEditLayerChangeListener(this);
		notifyEverythingChanged();
	}

	PTToggleDialog() {
		super("Lines/Routes/Stops", "matsim-scenario.png", "Lines/Routes/Stops", null, 150, true, Preferences.class);
		Main.pref.addPreferenceChangeListener(this);

		// table for route data
		table_pt = new JTable();
		table_pt.setDefaultRenderer(Object.class, new MATSimTableRenderer());
		table_pt.setAutoCreateRowSorter(true);
		table_pt.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		tableModel_pt = new MATSimTableModel_pt();
		table_pt.setModel(tableModel_pt);
		table_pt.getSelectionModel().addListSelectionListener(tableModel_pt);

		JScrollPane tableContainer_pt = new JScrollPane(table_pt);
		createLayout(tableContainer_pt, false, null);
	}

	// called when MATSim data changes to update the data in this dialog
	private void notifyEverythingChanged() {
		if (osmNetworkListener != null) {
			osmNetworkListener.removeListener(this);
		}
		OsmDataLayer layer = Main.main.getEditLayer();
		if (isShowing() && layer instanceof MATSimLayer) {
			if (((MATSimLayer) layer).getScenario().getConfig().transit().isUseTransit()) {
				osmNetworkListener = ((MATSimLayer) layer).getNetworkListener(); // MATSim
			}
		} else if (isShowing() && layer != null && Preferences.isSupportTransit()) {
			Config config = ConfigUtils.createConfig();
			config.transit().setUseTransit(true);
			EditableScenario scenario = EditableScenarioUtils.createScenario(config);
			osmNetworkListener = new NetworkListener(layer.data, scenario, new HashMap<Way, List<Link>>(), new HashMap<Link, List<WaySegment>>(),
					new HashMap<Relation, TransitStopFacility>());
			osmNetworkListener.visitAll();
			layer.data.addDataSetListener(osmNetworkListener);
		} else { // empty data mappings if no data layer is active
			setTitle(tr("Lines/Stops/Routes"));
		}
		if (osmNetworkListener != null) {
			osmNetworkListener.addListener(this);
		}
		notifyDataChanged();
	}

	@Override
	public void notifyDataChanged() {
		if (osmNetworkListener != null && osmNetworkListener.getScenario().getConfig().transit().isUseTransit()) {
			setTitle(tr("Lines: {0} / Routes: {1} / Stops: {2}", countTransitLines(osmNetworkListener.getScenario()),
					countTransitRoutes(osmNetworkListener.getScenario()), countStopFacilities(osmNetworkListener.getScenario())));
		} else {
			setTitle(tr("No MATSim transit schedule active"));
		}
		tableModel_pt.selectionChanged();
	}

	private int countStopFacilities(Scenario scenario) {
		if (scenario.getConfig().transit().isUseTransit()) {
			return scenario.getTransitSchedule().getFacilities().size();
		} else {
			return 0;
		}
	}

	private int countTransitRoutes(Scenario scenario) {
		int nRoutes = 0;
		if (scenario.getConfig().transit().isUseTransit()) {
			for (TransitLine line : scenario.getTransitSchedule().getTransitLines().values()) {
				nRoutes += countRoutes(line);
			}
		}
		return nRoutes;
	}

	private int countTransitLines(Scenario scenario) {
		int nLines = 0;
		if (scenario.getConfig().transit().isUseTransit()) {
			for (TransitLine line : scenario.getTransitSchedule().getTransitLines().values()) {
				if (countRoutes(line) > 0) {
					nLines++;
				}
			}
		}
		return nLines;
	}

	private int countRoutes(TransitLine line) {
		int nRoutes = 0;
		for (TransitRoute transitRoute : line.getRoutes().values()) {
			if (!((EditableTransitRoute) transitRoute).isDeleted()) {
				nRoutes++;
			}
		}
		return nRoutes;
	}

	@Override
	// react to active layer (active data set) changes by setting the current
	// data mappings
	// MATSim layers contain data mappings while OsmDataLayers must first be
	// converted
	// also adjusts standard file export formats
	public void editLayerChanged(OsmDataLayer oldLayer, OsmDataLayer newLayer) {
		// clear old data set listeners
		if (osmNetworkListener != null && oldLayer != null) {
			oldLayer.data.removeDataSetListener(osmNetworkListener);
		}
		notifyEverythingChanged();
	}

	private class MATSimTableRenderer extends DefaultTableCellRenderer {
		@Override
		public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
			setBackground(null);
			return super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
		}
	}

	// handles the underlying data of the routes table
	private class MATSimTableModel_pt extends AbstractTableModel implements ListSelectionListener {

		private final String[] columnNames = { "route id", "mode", "#stops", "#links" };

		private List<TransitRoute> routes;

		private HighlightHelper highlightHelper = new HighlightHelper();

		MATSimTableModel_pt() {
			this.routes = new ArrayList<>();
		}

		@Override
		public Class<?> getColumnClass(int columnIndex) {
			if (columnIndex == 0) {
				return String.class;
			} else if (columnIndex == 1) {
				return String.class;
			} else if (columnIndex == 2) {
				return Integer.class;
			} else if (columnIndex == 3) {
				return Integer.class;
			}
			throw new RuntimeException();
		}

		@Override
		public String getColumnName(int column) {
			return columnNames[column];
		}

		@Override
		public int getColumnCount() {
			return columnNames.length;
		}

		@Override
		public int getRowCount() {
			return routes.size();
		}

		@Override
		public Object getValueAt(int rowIndex, int columnIndex) {
			EditableTransitRoute route = (EditableTransitRoute) routes.get(rowIndex);
			if (columnIndex == 0) {
				return route.getRealId().toString();
			} else if (columnIndex == 1) {
				return route.getTransportMode();
			} else if (columnIndex == 2) {
				return route.getStops().size();
			} else if (columnIndex == 3) {
				return route.getRoute() != null ? route.getRoute().getLinkIds().size() + 2 : 0;
			}
			throw new RuntimeException();
		}

		// change shown route information of selected elements when selection
		// changes
		void selectionChanged() {
			this.routes.clear();
			if (osmNetworkListener != null) {
				DataSet currentDataSet = Main.main.getCurrentDataSet();
				if (currentDataSet != null) {
					Set<TransitRoute> uniqueRoutes = new LinkedHashSet<>();
					for (OsmPrimitive primitive : Main.main.getInProgressSelection()) {
						for (OsmPrimitive maybeRelation : primitive.getReferrers()) {
							EditableTransitRoute route = osmNetworkListener.findRoute(maybeRelation);
							if (route != null && !route.isDeleted()) {
								uniqueRoutes.add(route);
							}
						}
					}
					routes.addAll(uniqueRoutes);
				}
			}
			fireTableDataChanged();
		}

		@Override
		public void valueChanged(ListSelectionEvent e) {
			DataSet currentDataSet = Main.main.getCurrentDataSet();
			if (currentDataSet != null && !e.getValueIsAdjusting()) {
				highlightHelper.clear();
				currentDataSet.clearHighlightedWaySegments();
				int selectedRow = table_pt.getSelectedRow();
				if (selectedRow != -1) {
					Long id = Long.parseLong(routes.get(table_pt.convertRowIndexToModel(selectedRow)).getId().toString());
					Relation route = (Relation) currentDataSet.getPrimitiveById(id, OsmPrimitiveType.RELATION);
					highlightHelper.highlight(route.getMemberPrimitivesList());
				}
				Main.map.mapView.repaint();
			}
		}
	}

}
