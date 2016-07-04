package org.matsim.contrib.josm.gui;

import org.matsim.api.core.v01.Scenario;
import org.matsim.contrib.josm.model.MATSimLayer;
import org.matsim.contrib.josm.model.NetworkModel;
import org.matsim.contrib.josm.scenario.EditableTransitRoute;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.data.SelectionChangedListener;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.OsmPrimitiveType;
import org.openstreetmap.josm.data.osm.Relation;
import org.openstreetmap.josm.data.osm.event.DataSetListenerAdapter;
import org.openstreetmap.josm.data.osm.event.DatasetEventManager;
import org.openstreetmap.josm.data.osm.event.SelectionEventManager;
import org.openstreetmap.josm.gui.dialogs.ToggleDialog;
import org.openstreetmap.josm.gui.layer.MainLayerManager.ActiveLayerChangeEvent;
import org.openstreetmap.josm.gui.layer.MainLayerManager.ActiveLayerChangeListener;
import org.openstreetmap.josm.gui.layer.OsmDataLayer;
import org.openstreetmap.josm.gui.util.HighlightHelper;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.openstreetmap.josm.tools.I18n.tr;

/**
 * The ToggleDialog that shows link information of selected ways and route
 * information of selected route relation elements
 *
 * @author Nico
 */

@SuppressWarnings("serial")
public class PTToggleDialog extends ToggleDialog implements ActiveLayerChangeListener, NetworkModel.ScenarioDataChangedListener {
	private final JTable table_pt;
	private NetworkModel networkModel;

	private final DataSetListenerAdapter dataSetListenerAdapter = new DataSetListenerAdapter(abstractDatasetChangedEvent -> notifyDataChanged());
	private final SelectionChangedListener selectionListener = osmPrimitives -> notifyDataChanged();
	private final MATSimTableModel_pt tableModel_pt;

	@Override
	public void showNotify() {
		DatasetEventManager.getInstance().addDatasetListener(dataSetListenerAdapter, DatasetEventManager.FireMode.IN_EDT_CONSOLIDATED);
		SelectionEventManager.getInstance().addSelectionListener(selectionListener, DatasetEventManager.FireMode.IN_EDT_CONSOLIDATED);
		Main.getLayerManager().addActiveLayerChangeListener(this);
		notifyEverythingChanged();
	}

	@Override
	public void hideNotify() {
		DatasetEventManager.getInstance().removeDatasetListener(dataSetListenerAdapter);
		SelectionEventManager.getInstance().removeSelectionListener(selectionListener);
		Main.getLayerManager().removeActiveLayerChangeListener(this);
		notifyEverythingChanged();
	}

	public PTToggleDialog() {
		super("Lines/Routes", "matsim-scenario.png", "Lines/Routes", null, 150, true, Preferences.class);
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

	public void init() {
		enabledness();
	}

	// called when MATSim data changes to update the data in this dialog
	private void notifyEverythingChanged() {
		if (networkModel != null) {
			networkModel.removeListener(this);
		}
		OsmDataLayer layer = Main.getLayerManager().getEditLayer();
		if (isShowing() && layer instanceof MATSimLayer) {
			if (((MATSimLayer) layer).getNetworkModel().getScenario().getConfig().transit().isUseTransit()) {
				networkModel = ((MATSimLayer) layer).getNetworkModel(); // MATSim
			}
		} else if (isShowing() && layer != null && Preferences.isSupportTransit()) {
			networkModel = NetworkModel.createNetworkModel(layer.data);
			networkModel.visitAll();
		} else { // empty data mappings if no data layer is active
			setTitle(tr("Lines/Routes"));
		}
		if (networkModel != null) {
			networkModel.addListener(this);
		}
		notifyDataChanged();
	}

	@Override
	public void notifyDataChanged() {
		if (networkModel != null && networkModel.getScenario().getConfig().transit().isUseTransit()) {
			setTitle(tr("Lines: {0} / Routes: {1}", countTransitLines(networkModel.getScenario()),
					countTransitRoutes(networkModel.getScenario())));
		} else {
			setTitle(tr("No MATSim transit schedule active"));
		}
		tableModel_pt.selectionChanged();
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
			if (networkModel != null) {
				if (Main.getLayerManager().getEditDataSet() != null) {
					Set<TransitRoute> uniqueRoutes = new LinkedHashSet<>();
					for (OsmPrimitive primitive : Main.main.getInProgressSelection()) {
						for (OsmPrimitive maybeRelation : primitive.getReferrers()) {
							EditableTransitRoute route = networkModel.findRoute(maybeRelation);
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
			if (Main.getLayerManager().getEditDataSet() != null && !e.getValueIsAdjusting()) {
				highlightHelper.clear();
				Main.getLayerManager().getEditDataSet().clearHighlightedWaySegments();
				int selectedRow = table_pt.getSelectedRow();
				if (selectedRow != -1) {
					Long id = Long.parseLong(routes.get(table_pt.convertRowIndexToModel(selectedRow)).getId().toString());
					Relation route = (Relation) Main.getLayerManager().getEditDataSet().getPrimitiveById(id, OsmPrimitiveType.RELATION);
					highlightHelper.highlight(route.getMemberPrimitivesList());
				}
				Main.map.mapView.repaint();
			}
		}
	}

	@Override
	public void activeOrEditLayerChanged(ActiveLayerChangeEvent e) {
		notifyEverythingChanged();
	}

	@Override
	public void preferenceChanged(org.openstreetmap.josm.data.Preferences.PreferenceChangeEvent preferenceChangeEvent) {
		super.preferenceChanged(preferenceChangeEvent);
		if (preferenceChangeEvent.getKey().equalsIgnoreCase("matsim_supportTransit")) {
			enabledness();
		}
	}

	private void enabledness() {
		boolean enabled = Main.pref.getBoolean("matsim_supportTransit");
		getButton().setEnabled(enabled);
		if (isShowing() && !enabled) {
			hideDialog();
			hideNotify();
		}
	}

}
