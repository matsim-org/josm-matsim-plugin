package org.matsim.contrib.gtfs;

import com.conveyal.gtfs.GTFSFeed;
import com.conveyal.gtfs.model.*;
import javafx.application.Application;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.TreeItemPropertyValueFactory;
import javafx.stage.Stage;
import javafx.util.Pair;

import org.matsim.core.utils.misc.Time;

import java.util.*;
import java.util.stream.Collectors;

public class LineBrowser extends Application {

	public static void main(String[] args) {
		launch(args);
	}

	@Override
	public void start(Stage stage) throws Exception {
		GTFSFeed feed = GTFSFeed.fromFile("/Users/michaelzilske/wurst/vbb/380248.zip");
		feed.findPatterns();
		stopStage(feed, stage);
		patternStage(feed, new Stage());
	}

	private void stopStage(GTFSFeed feed, Stage stage) {
		TableView<Stop> stopListView = new TableView<>(FXCollections.observableArrayList(feed.stops.values()));
		TableColumn<Stop, String> id = new TableColumn<>("Id");
		id.setCellValueFactory(f -> new SimpleStringProperty(f.getValue().stop_id));
		TableColumn<Stop, String> name = new TableColumn<>("Name");
		name.setCellValueFactory(f -> new SimpleStringProperty(f.getValue().stop_name));
		stopListView.getColumns().addAll(id, name);
		stage.setScene(new Scene(stopListView));
		stage.show();
	}

	private void patternStage(GTFSFeed feed, Stage stage) {
		TreeTableView<RootPattern> tableView = new TreeTableView<>(getPatternData(feed));
		TreeTableColumn<RootPattern, String> name = new TreeTableColumn<>("Name");
		name.setCellValueFactory(new TreeItemPropertyValueFactory("name"));
		TreeTableColumn<RootPattern, List<String>> trips = new TreeTableColumn<>("Trips");
		trips.setCellValueFactory(new TreeItemPropertyValueFactory<>("trips"));
		trips.setCellFactory(column -> {
			TreeTableCell<RootPattern, List<String>> objectObjectTreeTableCell = new TreeTableCell<RootPattern, List<String>>() {
				@Override protected void updateItem(List<String> item, boolean empty) {
					if (item == getItem()) return;
					super.updateItem(item, empty);
					if (item == null) {
						super.setText(null);
						super.setGraphic(null);
					} else if (item instanceof Node) {
						super.setText(null);
						super.setGraphic((Node)item);
					} else {
						super.setText(item.toString());
						super.setGraphic(null);
					}
					MenuItem button = new MenuItem("Show trips");
					button.setOnAction(event -> {
						tripStage(feed, ((MyPattern) getTreeTableRow().getItem()), new Stage());
					});
					ContextMenu contextMenu = new ContextMenu();
					contextMenu.getItems().add(button);
					setContextMenu(contextMenu);
				}
			};
			return objectObjectTreeTableCell;
		});
		tableView.getColumns().addAll(name, trips);
		stage.setScene(new Scene(tableView));
		stage.show();
	}

	public static class MyStopWithTimes {
		Stop stop;
		ListProperty<StopTime> stopTimes = new SimpleListProperty<>(FXCollections.observableArrayList());
	}

	private void tripStage(GTFSFeed feed, MyPattern pattern, Stage stage) {
		List<MyStopWithTimes> stops = new ArrayList<>();
		for (String orderedStop : pattern.pattern.orderedStops) {
			MyStopWithTimes e = new MyStopWithTimes();
			e.stop = feed.stops.get(orderedStop);
			stops.add(e);
		}
		TableView<MyStopWithTimes> stopTableView = new TableView<>(FXCollections.observableArrayList(stops));
		TableColumn<MyStopWithTimes, String> stopColumn = new TableColumn<>("Stop");
		stopColumn.setCellValueFactory(f -> new SimpleStringProperty(f.getValue().stop.stop_name));
		stopTableView.getColumns().add(stopColumn);
		for (int i=0; i<pattern.getTrips().size(); i++) {
			String tripId = pattern.getTrips().get(i);
			TableColumn<MyStopWithTimes, String> column = new TableColumn<>();
			int finalI = i;
			column.setCellValueFactory(f -> new SimpleObjectProperty<String>(Time.writeTime((int) f.getValue().stopTimes.get(finalI).departure_time)));
			try {
				int j=0;
				for (StopTime stopTime : feed.getInterpolatedStopTimesForTrip(tripId)) {
					stops.get(j).stopTimes.add(stopTime);
					++j;
				}
			} catch (GTFSFeed.FirstAndLastStopsDoNotHaveTimes firstAndLastStopsDoNotHaveTimes) {
				firstAndLastStopsDoNotHaveTimes.printStackTrace();
			}
			stopTableView.getColumns().add(column);
		}
		stage.setScene(new Scene(stopTableView));
		stage.show();
	}


	private TreeItem<RootPattern> getPatternData(GTFSFeed feed) {
		TreeItem patternTreeItemTreeItem = new RootPatternTreeItem(feed);
		return patternTreeItemTreeItem;
	}

	private class RootPatternTreeItem extends TreeItem<RootPattern> {

		public RootPatternTreeItem(GTFSFeed feed) {
			super(new RootPattern());
			final Map<String, TreeItem<RootPattern>> agencyTreeItems = new HashMap<>();
			for (Agency agency : feed.agency.values()) {
				final TreeItem<RootPattern> agencyTreeItem = new TreeItem<>(new MyAgency(agency));
				agencyTreeItems.put(agency.agency_id, agencyTreeItem);
				getChildren().add(agencyTreeItem);
			}
			final Map<String, Pair<TreeItem<RootPattern>, MyRoute>> routeTreeItems = new HashMap<>();
			for (Route route : feed.routes.values()) {
				final MyRoute myRoute = new MyRoute(route);
				final TreeItem<RootPattern> routeTreeItem = new TreeItem<>(myRoute);
				routeTreeItems.put(route.route_id, new Pair<>(routeTreeItem, myRoute));
				agencyTreeItems.get(route.agency_id).getChildren().add(routeTreeItem);
			}
			for (Pattern pattern : feed.patterns.values()) {
				for (String routeId : pattern.associatedTrips.stream().map(it -> feed.trips.get(it).route_id).collect(Collectors.toSet())) {
					routeTreeItems.get(routeId).getValue().getPatterns().add(pattern);
				}
			}
			for (Pair<TreeItem<RootPattern>, MyRoute> treeItem : routeTreeItems.values()) {
				final MyRoute route = treeItem.getValue();
				List<Pattern> allPatterns = new ArrayList<>(route.getPatterns());
				route.getPatterns().clear();
				for (Pattern pattern : allPatterns) {
					maybeInsert(route, pattern);
				}
				for (Pattern pattern : route.getPatterns()) {
					TreeItem<RootPattern> e = new TreeItem<>(new MyPattern(pattern));
					for (String stopId : pattern.orderedStops) {
						e.getChildren().add(new TreeItem<>(new MyStop(feed.stops.get(stopId))));
					}
					treeItem.getKey().getChildren().add(e);
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

		public List<String> getTrips() {
			return pattern.associatedTrips;
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
