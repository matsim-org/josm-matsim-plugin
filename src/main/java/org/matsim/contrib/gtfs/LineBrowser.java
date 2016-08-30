package org.matsim.contrib.gtfs;

import com.conveyal.gtfs.GTFSFeed;
import com.conveyal.gtfs.model.*;
import javafx.application.Application;
import javafx.beans.property.ListProperty;
import javafx.beans.property.SimpleListProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.TreeItemPropertyValueFactory;
import javafx.stage.Stage;

import java.util.*;

public class LineBrowser extends Application {

	public static void main(String[] args) {
		launch(args);
	}

	@Override
	public void start(Stage stage) throws Exception {
		GTFSFeed feed = GTFSFeed.fromFile("/Users/michaelzilske/wurst/vbb/380248.zip");
		feed.findPatterns();

		TableView<Stop> stopListView = new TableView<>(FXCollections.observableArrayList(feed.stops.values()));
		TableColumn<Stop, String> id = new TableColumn<>("Id");
		id.setCellValueFactory(f -> new SimpleStringProperty(f.getValue().stop_id));
		TableColumn<Stop, String> name = new TableColumn<>("Name");
		name.setCellValueFactory(f -> new SimpleStringProperty(f.getValue().stop_name));
		stopListView.getColumns().addAll(id, name);
		stage.setScene(new Scene(stopListView));
		stage.show();
		lineStage(feed);
		patternStage(feed);
	}

	private void lineStage(GTFSFeed feed) {
		Stage stage = new Stage();
		TableView<Route> routeListView = new TableView<>(FXCollections.observableArrayList(feed.routes.values()));
		TableColumn<Route, String> id = new TableColumn<>("Id");
		id.setCellValueFactory(f -> new SimpleStringProperty(f.getValue().route_id));
		TableColumn<Route, String> shortName = new TableColumn<>("Short Name");
		shortName.setCellValueFactory(f -> new SimpleStringProperty(f.getValue().route_short_name));
		TableColumn<Route, String> longName = new TableColumn<>("Long Name");
		longName.setCellValueFactory(f -> new SimpleStringProperty(f.getValue().route_long_name));
		routeListView.getColumns().addAll(id, shortName, longName);
		stage.setScene(new Scene(routeListView));
		stage.show();
	}


	private void patternStage(GTFSFeed feed) {
		Stage stage = new Stage();
		TableView<Pattern> patternListView = new TableView<>(FXCollections.observableArrayList(feed.patterns.values()));
		TableColumn<Pattern, String> route = new TableColumn<>("Route");
		route.setCellValueFactory(f -> new SimpleStringProperty(f.getValue().associatedRoutes.toString()));
		TableColumn<Pattern, String> name = new TableColumn<>("Name");
		name.setCellValueFactory(f -> new SimpleStringProperty(f.getValue().name));
		patternListView.getColumns().addAll(route, name);
		stage.setScene(new Scene(patternListView));
		stage.show();

		Stage stage1 = new Stage();
		TreeTableView tableView = new TreeTableView(getPatternData(feed));
		TreeTableColumn name1 = new TreeTableColumn("Name");
		name1.setCellValueFactory(new TreeItemPropertyValueFactory("name"));
		tableView.getColumns().addAll(name1);
		stage1.setScene(new Scene(tableView));
		stage1.show();
	}

	private TreeItem getPatternData(GTFSFeed feed) {
		TreeItem patternTreeItemTreeItem = new RootPatternTreeItem(feed);
		return patternTreeItemTreeItem;
	}

	private class RootPatternTreeItem extends TreeItem {

		public RootPatternTreeItem(GTFSFeed feed) {
			super(new RootPattern());
			Map<String, TreeItem> agencyTreeItems = new HashMap<>();
			for (Agency agency : feed.agency.values()) {
				TreeItem agencyTreeItem = new TreeItem(new MyAgency(agency));
				agencyTreeItems.put(agency.agency_id, agencyTreeItem);
				getChildren().add(agencyTreeItem);
			}
			Map<String, TreeItem> routeTreeItems = new HashMap<>();
			for (Route route : feed.routes.values()) {
				TreeItem routeTreeItem = new TreeItem(new MyRoute(route));
				routeTreeItems.put(route.route_id, routeTreeItem);
				agencyTreeItems.get(route.agency_id).getChildren().add(routeTreeItem);
			}
			for (Pattern pattern : feed.patterns.values()) {
				for (String routeId : pattern.associatedRoutes) {
					((MyRoute) routeTreeItems.get(routeId).getValue()).getPatterns().add(pattern);
				}
			}
			for (TreeItem treeItem : routeTreeItems.values()) {
				MyRoute route = (MyRoute) treeItem.getValue();
				List<Pattern> allPatterns = new ArrayList<>(route.getPatterns());
				route.getPatterns().clear();
				for (Pattern pattern : allPatterns) {
					maybeInsert(route, pattern);
				}
				for (Pattern pattern : route.getPatterns()) {
					TreeItem e = new TreeItem(new MyPattern(pattern));
					for (String stopId : pattern.orderedStops) {
						e.getChildren().add(new TreeItem(new MyStop(feed.stops.get(stopId))));
					}
					treeItem.getChildren().add(e);
				}
			}
			setExpanded(true);
		}
	}

	private void maybeInsert(MyRoute route, Pattern pattern) {
		ListIterator<Pattern> listIterator = route.getPatterns().listIterator();
		while (listIterator.hasNext()) {
			Pattern otherPattern = listIterator.next();
			if (Collections.indexOfSubList(pattern.orderedStops, otherPattern.orderedStops) != -1) {
				listIterator.remove();
			} else if (Collections.indexOfSubList(otherPattern.orderedStops, pattern.orderedStops) != -1) {
				return;
			}
		}
		route.getPatterns().add(pattern);
	}

	public static class RootPattern {

		public String getName() {
			return "Verkehrsverbund Berlin-Brandenburg";
		}

	}

	public static class MyRoute extends RootPattern {

		private final Route route;

		private final ListProperty<Pattern> patterns = new SimpleListProperty<>(FXCollections.observableArrayList());

		public MyRoute(Route route) {
			this.route = route;
		}

		public String getName() {
			return (route.route_short_name == null ? "" : route.route_short_name)
					+ (route.route_long_name == null ? "" : route.route_long_name);
		}

		public ObservableList<Pattern> getPatterns() {
			return patterns;
		}

	}

	public static class MyPattern extends RootPattern {

		private final Pattern pattern;

		public MyPattern(Pattern pattern) {
			this.pattern = pattern;
		}

		public String getName() {
			return pattern.name;
		}

	}

	public static class MyStop extends RootPattern {

		private final Stop stop;

		public MyStop(Stop stop) {
			this.stop = stop;
		}

		public String getName() {
			return stop.stop_name;
		}

	}


	public static class MyAgency extends RootPattern{
		private final Agency agency;

		public MyAgency(Agency agency) {
			this.agency = agency;
		}

		public String getName() {
			return agency.agency_name;
		}
	}
}