package org.matsim.contrib.josm.gui;

import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.Observable;
import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.MapChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.embed.swing.JFXPanel;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.AnchorPane;
import org.matsim.contrib.josm.actions.MyOverpassDownloader;
import org.matsim.contrib.josm.model.Line;
import org.matsim.contrib.josm.model.NetworkModel;
import org.matsim.contrib.josm.model.Route;
import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.actions.downloadtasks.DownloadOsmTask;
import org.openstreetmap.josm.actions.downloadtasks.PostDownloadHandler;
import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.data.SelectionChangedListener;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.Relation;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.data.osm.event.DatasetEventManager;
import org.openstreetmap.josm.data.osm.event.SelectionEventManager;
import org.openstreetmap.josm.gui.dialogs.ToggleDialog;
import org.openstreetmap.josm.gui.dialogs.relation.DownloadRelationTask;
import org.openstreetmap.josm.gui.layer.MainLayerManager.ActiveLayerChangeEvent;
import org.openstreetmap.josm.gui.layer.MainLayerManager.ActiveLayerChangeListener;
import org.openstreetmap.josm.gui.layer.OsmDataLayer;
import org.openstreetmap.josm.gui.util.HighlightHelper;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;

import static org.openstreetmap.josm.tools.I18n.tr;

/**
 * The ToggleDialog that shows link information of selected ways and route
 * information of selected route relation elements
 *
 * @author Nico
 */

@SuppressWarnings("serial")
public class PTToggleDialog extends ToggleDialog implements ActiveLayerChangeListener {
	private final JFXPanel fxPanel;
	private final TableView<Route> table_pt;
	private final StringProperty title;

	private FilteredList<Route> selectedRoutes;
	private final SelectionChangedListener selectionListener = osmPrimitives -> {
		if (Main.getLayerManager().getEditDataSet() != null) {
			HashSet<OsmPrimitive> selection = new HashSet<>(Main.main.getInProgressSelection());
			Platform.runLater(() -> selectedRoutes.setPredicate(route ->
					selection.contains(route.getRelation()) ||
							!Collections.disjoint(selection, route.getRelation().getMemberPrimitives())));
		} else {
			Platform.runLater(() -> selectedRoutes.setPredicate(route -> false));
		}
	};

	@Override
	public void showNotify() {
		Main.getLayerManager().addAndFireActiveLayerChangeListener(this);
		SelectionEventManager.getInstance().addSelectionListener(selectionListener, DatasetEventManager.FireMode.IN_EDT_CONSOLIDATED);
	}

	@Override
	public void hideNotify() {
		SelectionEventManager.getInstance().removeSelectionListener(selectionListener);
		Main.getLayerManager().removeActiveLayerChangeListener(this);
	}

	public PTToggleDialog() {
		super("Lines/Routes", "matsim-scenario.png", "Lines/Routes", null, 150, true, Preferences.class);
		Platform.setImplicitExit(false); // http://stackoverflow.com/questions/29302837/javafx-platform-runlater-never-running
		fxPanel = new JFXPanel();
		table_pt = new TableView<>();
		title = new SimpleStringProperty("Lines/Routes");
		createLayout(fxPanel, false, null);
		Platform.runLater(() -> {
			title.addListener((InvalidationListener) -> SwingUtilities.invokeLater(() -> setTitle(title.get())));
			TableColumn<Route, String> idColumn = new TableColumn<>("route id");
			idColumn.setCellValueFactory(r -> r.getValue().idProperty());
			TableColumn<Route, String> modeColumn = new TableColumn<>("mode");
			modeColumn.setCellValueFactory(r -> r.getValue().transportModeProperty());
			TableColumn<Route, Number> stopsSizeColumn = new TableColumn<>("#stops");
			stopsSizeColumn.setCellValueFactory(r -> Bindings.size(r.getValue().getStops()));
			TableColumn<Route, Number> routeSizeColumn = new TableColumn<>("#links");
			routeSizeColumn.setCellValueFactory(r -> Bindings.size(r.getValue().getRoute()));
			table_pt.getColumns().setAll(idColumn, modeColumn, stopsSizeColumn, routeSizeColumn);
			table_pt.setRowFactory(v -> {
				TableRow<Route> row = new TableRow<>();
				final ContextMenu rowMenu = new ContextMenu();
				MenuItem downloadItem = new MenuItem("Download members and stop areas");
				downloadItem.setOnAction(event -> {
					Collection<Relation> relations = new ArrayList<>();
					relations.add(row.getItem().getRelation());
					Main.worker.submit(new DownloadRelationTask(relations, Main.getLayerManager().getEditLayer()));
					DownloadOsmTask task = new DownloadOsmTask();
					StringBuilder query = new StringBuilder();
					query.append(String.format("[timeout:%d];", MyOverpassDownloader.TIMEOUT_S));
					query.append("(");
					for (OsmPrimitive osmPrimitive : row.getItem().getStopsAndPlatforms()) {
						if (osmPrimitive instanceof Node) {
							query.append(String.format("node(%d);", osmPrimitive.getId()));
						} else if (osmPrimitive instanceof Way) {
							query.append(String.format("way(%d);", osmPrimitive.getId()));
						}
					}
					query.append(") -> .primitives;");
					query.append("(");
					query.append("rel(bn.primitives)[public_transport=stop_area];");
					query.append("rel(bw.primitives)[public_transport=stop_area];");
					query.append(");");
					query.append("out meta;");
					Main.worker.submit(new PostDownloadHandler(task, task.download(
							new MyOverpassDownloader(query.toString()), false, new Bounds(0, 0, true), null)));
				});
				rowMenu.getItems().addAll(downloadItem);

				// only display context menu for non-null items:
				row.contextMenuProperty().bind(
						Bindings.when(Bindings.isNotNull(row.itemProperty()))
								.then(rowMenu)
								.otherwise((ContextMenu)null));
				return row;
			});


			HighlightHelper highlightHelper = new HighlightHelper();
			table_pt.getSelectionModel().getSelectedItems().addListener(new InvalidationListener() {
				@Override
				public void invalidated(Observable observable) {
					if (Main.getLayerManager().getEditDataSet() != null) {
						highlightHelper.clear();
						Main.getLayerManager().getEditDataSet().clearHighlightedWaySegments();
						for (Route route : table_pt.getSelectionModel().getSelectedItems()) {
							highlightHelper.highlight(route.getRelation().getMemberPrimitivesList());
						}
						Main.map.mapView.repaint();
					}
				}
			});
			AnchorPane root = new AnchorPane();
			AnchorPane.setTopAnchor(table_pt, 0.0);
			AnchorPane.setLeftAnchor(table_pt, 0.0);
			AnchorPane.setRightAnchor(table_pt, 0.0);
			AnchorPane.setBottomAnchor(table_pt, 0.0);
			root.getChildren().add(table_pt);
			Scene scene = new Scene(root);
			fxPanel.setScene(scene);
		});
	}

	public void init() {
		enabledness();
	}

	@Override
	public void activeOrEditLayerChanged(ActiveLayerChangeEvent e) {
		OsmDataLayer editLayer = Main.getLayerManager().getEditLayer();
		if (editLayer != null) {
			HashSet<OsmPrimitive> selection = new HashSet<>(Main.main.getInProgressSelection());
			Platform.runLater(() -> {
				NetworkModel networkModel = NetworkModel.createNetworkModel(editLayer.data);
				ObservableList<Line> lineList = FXCollections.observableArrayList();
				ObservableList<Route> routeList = FXCollections.observableArrayList();
				networkModel.lines().addListener((MapChangeListener<Relation, Line>) change -> {
					Platform.runLater(() -> lineList.remove(change.getValueRemoved()));
					if (change.wasAdded()) {
						Platform.runLater(() -> lineList.add(change.getValueAdded()));
					}
				});
				networkModel.routes().addListener((MapChangeListener<Relation, Route>) change -> {
					Platform.runLater(() -> routeList.remove(change.getValueRemoved()));
					if (change.wasAdded()) {
						Platform.runLater(() -> routeList.add(change.getValueAdded()));
					}
				});
				networkModel.visitAll();
				selectedRoutes = new FilteredList<>(routeList);
				table_pt.setItems(selectedRoutes);
				selectedRoutes.setPredicate(route ->
						selection.contains(route.getRelation()) ||
						!Collections.disjoint(selection, route.getRelation().getMemberPrimitives()));
				FilteredList<Line> selectedLines = new FilteredList<>(lineList, line -> !Collections.disjoint(line.getRoutes(), selectedRoutes));
				title.bind(Bindings.createStringBinding(() -> {
					if (selectedRoutes.isEmpty()) {
						return "Lines/Routes";
					} else {
						return tr("Lines: {0} / Routes: {1}", selectedLines.size(), selectedRoutes.size());
					}
				}, selectedLines, selectedRoutes));
			});
		} else {
			Platform.runLater(() -> {
				table_pt.setItems(FXCollections.emptyObservableList());
				title.bind(Bindings.createStringBinding(() -> "Lines/Routes"));
			});
		}
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
