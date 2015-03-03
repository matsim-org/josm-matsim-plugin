package org.matsim.contrib.josm;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.contrib.josm.scenario.EditableScenarioUtils;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.network.LinkImpl;
import org.matsim.core.network.NetworkImpl;
import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.data.Preferences.PreferenceChangeEvent;
import org.openstreetmap.josm.data.SelectionChangedListener;
import org.openstreetmap.josm.data.osm.*;
import org.openstreetmap.josm.data.osm.event.AbstractDatasetChangedEvent;
import org.openstreetmap.josm.data.osm.event.DataSetListenerAdapter;
import org.openstreetmap.josm.data.osm.event.DatasetEventManager;
import org.openstreetmap.josm.data.osm.event.SelectionEventManager;
import org.openstreetmap.josm.gui.MapView;
import org.openstreetmap.josm.gui.dialogs.ToggleDialog;
import org.openstreetmap.josm.gui.layer.Layer;
import org.openstreetmap.josm.gui.layer.OsmDataLayer;
import org.openstreetmap.josm.tools.ImageProvider;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
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
class LinksToggleDialog extends ToggleDialog implements MapView.EditLayerChangeListener, NetworkListener.ScenarioDataChangedListener {
	private final JTable table_links;
	private final MATSimTableModel_links tableModel_links = new MATSimTableModel_links();

    private final JButton networkAttributes = new JButton(new ImageProvider("dialogs", "edit").setWidth(16).get());
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

	LinksToggleDialog() {
		super("Links/Nodes", "matsim-scenario.png", "Links/Nodes", null, 150,
				true, Preferences.class);
		Main.pref.addPreferenceChangeListener(this);


		// table for link data
		table_links = new JTable();
		table_links.setDefaultRenderer(Object.class, new MATSimTableRenderer());
		table_links.setAutoCreateRowSorter(true);
		table_links.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table_links.setModel(tableModel_links);
        table_links.getSelectionModel().addListSelectionListener(tableModel_links);

		JScrollPane tableContainer_links = new JScrollPane(table_links);
		createLayout(tableContainer_links, false, null);

		networkAttributes.setToolTipText(tr("edit network attributes"));
		networkAttributes.setBorder(BorderFactory.createEmptyBorder());
		networkAttributes.addActionListener(new ActionListener() {
            @Override
            // open dialog that allows editing of global network attributes
            public void actionPerformed(ActionEvent e) {
                NetworkAttributes dialog = new NetworkAttributes();
                JOptionPane pane = new JOptionPane(dialog,
                        JOptionPane.PLAIN_MESSAGE, JOptionPane.OK_CANCEL_OPTION);
                JDialog dlg = pane.createDialog(Main.parent,
                        tr("Network Attributes"));
                dlg.setAlwaysOnTop(true);
                dlg.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
                dlg.setVisible(true);
                if (pane.getValue() != null) {
                    if (((Integer) pane.getValue()) == JOptionPane.OK_OPTION) {
                        dialog.apply();
                    }
                }
                dlg.dispose();
            }
        });
		this.titleBar.add(networkAttributes, this.titleBar.getComponentCount() - 3);
    }

	// called when MATSim data changes to update the data in this dialog
	private void notifyEverythingChanged() {
        OsmDataLayer layer = Main.main.getEditLayer();
        if (osmNetworkListener != null) {
            osmNetworkListener.removeListener(this);
        }
        if (isShowing() && layer != null) {
            if (layer instanceof MATSimLayer) {
                osmNetworkListener = ((MATSimLayer) layer).getNetworkListener(); // MATSim layers have their own network listener
            } else {
                osmNetworkListener = new NetworkListener(layer.data, EditableScenarioUtils.createScenario(ConfigUtils.createConfig()), new HashMap<Way, List<Link>>(), new HashMap<Link, List<WaySegment>>());
                osmNetworkListener.visitAll();
                layer.data.addDataSetListener(osmNetworkListener);
            }
            table_links.setModel(tableModel_links);
            this.networkAttributes.setEnabled(true);
            checkInternalIdColumn();
        } else { // empty data mappings if no data layer is active
            osmNetworkListener = null;
            table_links.setModel(new DefaultTableModel());
            setTitle(tr("Links/Nodes"));
            networkAttributes.setEnabled(false);
        }
        if (osmNetworkListener != null) {
            // set converted links that are to be drawn blue by map renderer
            MapRenderer.setWay2Links(osmNetworkListener.getWay2Links());
            osmNetworkListener.addListener(this);
        }
        notifyDataChanged();
    }

    @Override
    public void notifyDataChanged() {
        if (osmNetworkListener != null) {
            setTitle(tr(
                    "Links: {0} / Nodes: {1}",
                    osmNetworkListener.getScenario().getNetwork().getLinks().size(), osmNetworkListener.getScenario().getNetwork().getNodes().size()));
        } else {
            setTitle(tr("No MATSim layer active"));
        }
        tableModel_links.selectionChanged(null);
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

	@Override
	public void preferenceChanged(PreferenceChangeEvent e) {
		super.preferenceChanged(e);
        if (e.getKey().equalsIgnoreCase("matsim_showInternalIds")) {
			checkInternalIdColumn();
		}
	}

	// hecks if internal-id should be shown
	private void checkInternalIdColumn() {
		if (!Main.pref.getBoolean("matsim_showInternalIds", false)) {
			table_links.getColumn("internal-id").setMinWidth(0);
			table_links.getColumn("internal-id").setMaxWidth(0);
		} else {
			table_links.getColumn("internal-id").setMaxWidth(
					table_links.getColumn("id").getMaxWidth());
			table_links.getColumn("internal-id").setWidth(
					table_links.getColumn("id").getWidth());
		}
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

	// handles the underlying data of the links table
	private class MATSimTableModel_links extends AbstractTableModel implements
			SelectionChangedListener, ListSelectionListener {

		private final String[] columnNames = { "id", "internal-id", "length",
				"freespeed", "capacity", "permlanes", "modes" };


		private Map<Integer, Id<Link>> links;

		MATSimTableModel_links() {
			this.links = new HashMap<>();
			DataSet.addSelectionListener(this);
		}

		@Override
		public Class<?> getColumnClass(int columnIndex) {
			if (columnIndex == 0) {
				return String.class;
			} else if (columnIndex == 1) {
				return String.class;
			} else if (columnIndex == 2) {
				return Double.class;
			} else if (columnIndex == 3) {
				return Double.class;
			} else if (columnIndex == 4) {
				return Double.class;
			} else if (columnIndex == 5) {
				return Double.class;
			} else if (columnIndex == 6) {
				return String.class;
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
			return links.size();
		}

		@Override
		public Object getValueAt(int rowIndex, int columnIndex) {
			Id<Link> id = links.get(rowIndex);
			Link link = osmNetworkListener.getScenario().getNetwork().getLinks().get(id);
			if (columnIndex == 0) {
				return ((LinkImpl) link).getOrigId();
			} else if (columnIndex == 1) {
				return link.getId().toString();
			} else if (columnIndex == 2) {
				return link.getLength();
			} else if (columnIndex == 3) {
				return link.getFreespeed();
			} else if (columnIndex == 4) {
				return link.getCapacity();
			} else if (columnIndex == 5) {
				return link.getNumberOfLanes();
			} else if (columnIndex == 6) {
				return link.getAllowedModes().toString();
			}
			throw new RuntimeException();
		}

		@Override
		// change shown link information of selected elements when selection
		// changes
		public void selectionChanged(Collection<? extends OsmPrimitive> newSelection) {
            links.clear();
            if (osmNetworkListener != null) {
                DataSet currentDataSet = Main.main.getCurrentDataSet();
                if (currentDataSet != null) {
                    currentDataSet.clearHighlightedWaySegments();
                    int i = 0;
                    for (OsmPrimitive primitive : Main.main.getInProgressSelection()) {
                        if (primitive instanceof Way) {
                            if (osmNetworkListener.getWay2Links().containsKey(primitive)) {
                                for (Link link : osmNetworkListener.getWay2Links().get(primitive)) {
                                    links.put(i, link.getId());
                                    i++;
                                }
                            }
                        }
                    }
                }
            }
			fireTableDataChanged();
		}

		@Override
		// highlight and zoom to way segments that refer to the selected link in
		// the table
		public void valueChanged(ListSelectionEvent e) {
            DataSet currentDataSet = Main.main.getCurrentDataSet();
            if (currentDataSet != null) {
                if (table_links.getSelectedRow() != -1) {
                    int row = table_links.convertRowIndexToModel(table_links.getSelectedRow());
                    Link link = osmNetworkListener.getScenario().getNetwork().getLinks().get(Id.create((String) this.getValueAt(row, 1), Link.class));
                    if (osmNetworkListener.getLink2Segments().containsKey(link)) {
                        List<WaySegment> segments = osmNetworkListener.getLink2Segments().get(link);
                        currentDataSet.setHighlightedWaySegments(segments);
                    }
                }
                Main.map.mapView.repaint();
            }
		}
	}

    // dialog that handles editing of global network attributes
	private class NetworkAttributes extends JPanel {

		private final JLabel laneWidth = new JLabel("effective lane width [m]:");
		private final JLabel capacityPeriod = new JLabel("capacity period [s]:");
		private final JTextField laneWidthValue = new JTextField();
		private final JTextField capacityPeriodValue = new JTextField();

		public NetworkAttributes() {
			Layer layer = Main.main.getActiveLayer();
			if (layer instanceof MATSimLayer) {
				laneWidthValue.setText(String.valueOf(((MATSimLayer) layer)
						.getScenario().getNetwork()
						.getEffectiveLaneWidth()));
				capacityPeriodValue.setText(String
						.valueOf(((MATSimLayer) layer).getScenario()
								.getNetwork().getCapacityPeriod()));
			}
			add(laneWidth);
			add(laneWidthValue);
			add(capacityPeriod);
			add(capacityPeriodValue);
		}

		void apply() {
			Layer layer = Main.main.getActiveLayer();
			if (layer instanceof MATSimLayer) {
				String lW = laneWidthValue.getText();
				String cP = capacityPeriodValue.getText();
				if (!lW.isEmpty()) {
					((NetworkImpl) ((MATSimLayer) layer).getScenario()
							.getNetwork()).setEffectiveLaneWidth(Double
							.parseDouble(lW));
				}
				if (!cP.isEmpty()) {
					((NetworkImpl) ((MATSimLayer) layer).getScenario()
							.getNetwork()).setCapacityPeriod(Double
							.parseDouble(cP));
				}
			}
		}
	}

}
