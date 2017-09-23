package org.matsim.contrib.josm.gui;

import org.openstreetmap.josm.spi.preferences.PreferenceChangeEvent;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.MapChangeListener;
import javafx.collections.ObservableList;
import javafx.embed.swing.JFXPanel;
import javafx.scene.Scene;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.layout.AnchorPane;
import org.matsim.contrib.josm.model.NetworkModel;
import org.matsim.contrib.josm.model.StopArea;
import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.data.osm.Relation;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.dialogs.ToggleDialog;
import org.openstreetmap.josm.gui.layer.MainLayerManager;
import org.openstreetmap.josm.gui.layer.OsmDataLayer;

import javax.swing.*;

public class StopAreasToggleDialog extends ToggleDialog implements MainLayerManager.ActiveLayerChangeListener {

	private final JFXPanel fxPanel = new JFXPanel();
	private OsmDataLayer editLayer;
	private final ListView<StopArea> list = new ListView<>();
	private final StringProperty title = new SimpleStringProperty("Stop areas");

	public StopAreasToggleDialog() {
		super("Stop areas", "matsim-scenario.png", "Stop areas", null, 150, true, Preferences.class);
		Platform.setImplicitExit(false); // http://stackoverflow.com/questions/29302837/javafx-platform-runlater-never-running
		createLayout(fxPanel, false, null);
		Platform.runLater(() -> {
			title.addListener((InvalidationListener) -> SwingUtilities.invokeLater(() -> setTitle(title.get())));
			AnchorPane root = new AnchorPane();
			list.setCellFactory(l -> new ListCell<StopArea>() {
				@Override
				protected void updateItem(StopArea stopArea, boolean empty) {
					super.updateItem(stopArea, empty);
					if (stopArea != null) {
						setText(stopArea.getName());
					}
				}
			});
			AnchorPane.setTopAnchor(list, 0.0);
			AnchorPane.setLeftAnchor(list, 0.0);
			AnchorPane.setRightAnchor(list, 0.0);
			AnchorPane.setBottomAnchor(list, 0.0);
			root.getChildren().add(list);
			Scene scene = new Scene(root);
			fxPanel.setScene(scene);
		});
	}

	public void init() {
		enabledness();
	}

	@Override
	public void showNotify() {
		MainApplication.getLayerManager().addActiveLayerChangeListener(this);
	}

	@Override
	public void hideNotify() {
		MainApplication.getLayerManager().removeActiveLayerChangeListener(this);
	}

	@Override
	public void activeOrEditLayerChanged(MainLayerManager.ActiveLayerChangeEvent activeLayerChangeEvent) {
		editLayer = MainApplication.getLayerManager().getEditLayer();
		Platform.runLater(() -> {
			if (editLayer != null) {
				NetworkModel networkModel = NetworkModel.createNetworkModel(editLayer.data);
				ObservableList<StopArea> stopAreaList = FXCollections.observableArrayList();
				networkModel.stopAreas().addListener((MapChangeListener<Relation, StopArea>) change -> {
					Platform.runLater(() -> stopAreaList.remove(change.getValueRemoved()));
					if (change.wasAdded()) {
						Platform.runLater(() -> stopAreaList.add(change.getValueAdded()));
					}
				});
				networkModel.visitAll();
				list.setItems(stopAreaList);
				title.bind(Bindings.createStringBinding(() -> {
					if (stopAreaList.isEmpty()) {
						return "Stop areas";
					} else {
						return "Stop areas: " + stopAreaList.size();
					}
				}, stopAreaList));
			} else {
				list.setItems(FXCollections.emptyObservableList());
			}
		});
	}

	@Override
	public void preferenceChanged(PreferenceChangeEvent preferenceChangeEvent) {
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
