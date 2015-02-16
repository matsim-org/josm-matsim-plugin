package org.matsim.contrib.josm;

import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.actions.AutoScaleAction;
import org.openstreetmap.josm.data.SelectionChangedListener;
import org.openstreetmap.josm.data.osm.*;
import org.openstreetmap.josm.data.osm.event.AbstractDatasetChangedEvent;
import org.openstreetmap.josm.data.osm.event.DataSetListenerAdapter;
import org.openstreetmap.josm.data.osm.event.DatasetEventManager;
import org.openstreetmap.josm.data.osm.event.SelectionEventManager;
import org.openstreetmap.josm.gui.MapView;
import org.openstreetmap.josm.gui.dialogs.ToggleDialog;
import org.openstreetmap.josm.gui.layer.OsmDataLayer;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.util.*;
import java.util.List;

import static org.openstreetmap.josm.tools.I18n.tr;

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
		super("Lines/Routes/Stops", "matsim-scenario.png", "Lines/Routes/Stops", null, 150,
				true, Preferences.class);
		Main.pref.addPreferenceChangeListener(this);

        // table for route data
		table_pt = new JTable();
		table_pt.setDefaultRenderer(Object.class, new MATSimTableRenderer());
		table_pt.setAutoCreateRowSorter(true);
		table_pt.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        MATSimTableModel_pt tableModel_pt = new MATSimTableModel_pt();
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
            if (((MATSimLayer) layer).getScenario().getConfig().scenario().isUseTransit()) {
                osmNetworkListener = ((MATSimLayer) layer).getNetworkListener(); // MATSim layers have their own network listener
            }
        } else if (isShowing() && layer != null && Preferences.isSupportTransit()) {
            Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
            scenario.getConfig().scenario().setUseTransit(true);
            scenario.getConfig().scenario().setUseVehicles(true);
            osmNetworkListener = new NetworkListener(layer.data, scenario, new HashMap<Way, List<Link>>(), new HashMap<Link, List<WaySegment>>(), new HashMap<Relation, TransitRoute>());
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
        if (osmNetworkListener != null && osmNetworkListener.getScenario().getConfig().scenario().isUseTransit()) {
            setTitle(tr(
                    "Lines: {0} / Routes: {1} / Stops: {2}",
                    countTransitLines(osmNetworkListener.getScenario()), countTransitRoutes(osmNetworkListener.getScenario()), countStopFacilities(osmNetworkListener.getScenario())));
        } else {
            setTitle(tr("No MATSim transit schedule active"));
        }
    }

    private int countStopFacilities(Scenario scenario) {
        if (scenario.getConfig().scenario().isUseTransit()) {
            return scenario.getTransitSchedule().getFacilities().size();
        } else {
            return 0;
        }
    }

    private int countTransitRoutes(Scenario scenario) {
        int nRoutes = 0;
        if (scenario.getConfig().scenario().isUseTransit()) {
            for (TransitLine line : scenario.getTransitSchedule().getTransitLines().values()) {
                nRoutes += line.getRoutes().size();
            }
        }
        return nRoutes;
    }

    private int countTransitLines(Scenario scenario) {
        if (scenario.getConfig().scenario().isUseTransit()) {
            return scenario.getTransitSchedule().getTransitLines().size();
        } else {
            return 0;
        }
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
		public Component getTableCellRendererComponent(JTable table,
				Object value, boolean isSelected, boolean hasFocus, int row,
				int column) {
			setBackground(null);
			return super.getTableCellRendererComponent(table, value,
					isSelected, hasFocus, row, column);
		}
	}

    // handles the underlying data of the routes table
	private class MATSimTableModel_pt extends AbstractTableModel implements
			SelectionChangedListener, ListSelectionListener {

		private final String[] columnNames = { "route id", "mode", "#stops",
				"#links" };

		private Map<Integer, TransitRoute> routes;

		MATSimTableModel_pt() {
			this.routes = new HashMap<>();
			DataSet.addSelectionListener(this);
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
			TransitRoute route = routes.get(rowIndex);
			if (columnIndex == 0) {
				DataSet currentDataSet = Main.main.getCurrentDataSet();
				if (currentDataSet != null) {
					Relation routeRelation = (Relation) currentDataSet.getPrimitiveById(Long.parseLong(route.getId().toString()), OsmPrimitiveType.RELATION);
					if (routeRelation.hasKey("ref")) {
						return routeRelation.get("ref");
					} else {
						return route.getId().toString();
					}
				}
			} else if (columnIndex == 1) {
				return route.getTransportMode();
			} else if (columnIndex == 2) {
				return route.getStops().size();
			} else if (columnIndex == 3) {
				return route.getRoute() != null ? route.getRoute().getLinkIds().size() + 2 : 0;
			}
			throw new RuntimeException();
		}

		@Override
		// change shown route information of selected elements when selection
		// changes
		public void selectionChanged(Collection<? extends OsmPrimitive> newSelection) {
            this.routes.clear();
            if (osmNetworkListener != null) {
                DataSet currentDataSet = Main.main.getCurrentDataSet();
                if (currentDataSet != null) {
                    currentDataSet.clearHighlightedWaySegments();
                    currentDataSet.clearHighlightedVirtualNodes();
                    Set<TransitRoute> uniqueRoutes = new LinkedHashSet<>();
                    for (OsmPrimitive primitive : Main.main.getInProgressSelection()) {
                        for (OsmPrimitive maybeRelation : primitive.getReferrers()) {
                            if (osmNetworkListener.getRelation2Route().containsKey(maybeRelation)) {
                                uniqueRoutes.add(osmNetworkListener.getRelation2Route().get(maybeRelation));
                            }
                        }
                    }
                    int i = 0;
                    for (TransitRoute uniqueRoute: uniqueRoutes) {
                        routes.put(i, uniqueRoute);
                        i++;
                    }
                }
            }
			fireTableDataChanged();
		}

		@Override
		public void valueChanged(ListSelectionEvent e) {
            DataSet currentDataSet = Main.main.getCurrentDataSet();
            if (currentDataSet != null && !e.getValueIsAdjusting()) {
                currentDataSet.clearHighlightedWaySegments();
                int selectedRow = table_pt.getSelectedRow();
                if (selectedRow != -1) {
                    Long id = Long.parseLong(routes.get(selectedRow).getId().toString());
                    Relation route = (Relation) currentDataSet.getPrimitiveById(id, OsmPrimitiveType.RELATION);
                    for (OsmPrimitive primitive: route.getMemberPrimitivesList()) {
                        primitive.setHighlighted(true);
                    }
                    AutoScaleAction.zoomTo(Collections.singleton((OsmPrimitive)route));
                }
                Main.map.mapView.repaint();
            }
		}
	}

}
