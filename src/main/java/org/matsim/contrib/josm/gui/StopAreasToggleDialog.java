package org.matsim.contrib.josm.gui;

import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.embed.swing.JFXPanel;
import javafx.scene.Scene;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.layout.AnchorPane;
import org.matsim.contrib.josm.model.NetworkModel;
import org.matsim.contrib.josm.model.StopArea;
import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.gui.dialogs.ToggleDialog;
import org.openstreetmap.josm.gui.layer.OsmDataLayer;

import javax.swing.*;

public class StopAreasToggleDialog extends ToggleDialog {

	private final JFXPanel fxPanel = new JFXPanel();
	private OsmDataLayer editLayer;
	private final ListView<StopArea> list = new ListView<>();
	private final StringProperty title = new SimpleStringProperty("Stop areas");

	public StopAreasToggleDialog() {
		super("Stop areas", "matsim-scenario.png", "Stop areas", null, 150, true, Preferences.class);
		Platform.setImplicitExit(false); // http://stackoverflow.com/questions/29302837/javafx-platform-runlater-never-running
		Main.pref.addPreferenceChangeListener(this);
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
			Main.getLayerManager().addActiveLayerChangeListener(activeLayerChangeEvent -> {
				editLayer = Main.getLayerManager().getEditLayer();
				Platform.runLater(() -> {
					if (editLayer != null) {
						NetworkModel networkModel = NetworkModel.createNetworkModel(editLayer.data);
						networkModel.visitAll();
						list.setItems(networkModel.stopAreaList());
						title.bind(Bindings.createStringBinding(() -> {
							if (networkModel.stopAreaList().isEmpty()) {
								return "Stop areas";
							} else {
								return "Stop areas: " + networkModel.stopAreaList().size();
							}
						}, networkModel.stopAreaList()));
					} else {
						list.setItems(FXCollections.emptyObservableList());
					}
				});
			});
		});
	}

}
