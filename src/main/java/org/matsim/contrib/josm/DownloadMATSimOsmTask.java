package org.matsim.contrib.josm;

//License: GPL. For details, see LICENSE file.

import java.util.concurrent.Future;

import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.actions.downloadtasks.DownloadOsmTask;
import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.gui.preferences.server.OverpassServerPreference;
import org.openstreetmap.josm.gui.progress.ProgressMonitor;
import org.openstreetmap.josm.io.OverpassDownloadReader;

/**
 * Open the download dialog and download the data. Run in the worker thread.
 */
public class DownloadMATSimOsmTask extends DownloadOsmTask {

    static String getRoutePredicates() {
		int counter = 0;
		StringBuilder routes = new StringBuilder("[\"route\"~\"");
		for (String route : OsmConvertDefaults.routeTypes) {
			if (Main.pref.getBoolean("matsim_download_" + route, true)) {
				routes.append(route);
				routes.append("|");
				counter++;
			}
		}

		if (routes.lastIndexOf("|") != -1) {
			routes.replace(routes.lastIndexOf("|"), routes.lastIndexOf("|") + 1, "");
		}
		routes.append("\"]");
		if (counter == 0) {
			return null;
		}
		return routes.toString();
	}

    static String getHighwayPredicates() {
		int counter = 0;
		StringBuilder highways = new StringBuilder("[\"highway\"~\"");
		for (String highway : OsmConvertDefaults.highwayTypes) {
			if (Main.pref.getBoolean("matsim_download_" + highway, true)) {
				highways.append(highway);
				highways.append("|");
				counter++;
			}
		}
		if (highways.lastIndexOf("|") != -1) {
			highways.replace(highways.lastIndexOf("|"), highways.lastIndexOf("|") + 1, "");
		}
		highways.append("\"]");
		if (counter == 0) {
			return null;
		}
		return highways.toString();
	}

    static String getQuery(String highwayPredicates, String routePredicates, double lon1, double lat1, double lon2, double lat2) {
		StringBuilder sb = new StringBuilder();
        // Five minutes. Apparently, this is also parsed in OverpassDownloadReader and used as client timeout.
        sb.append("[timeout:600];");
        String bbox = "(" + lat1 + "," + lon1 + "," + lat2 + "," + lon2 + ")";
		if (highwayPredicates != null) {
			sb.append(String.format("way %s %s -> .highways;", bbox, highwayPredicates));
		}
		if (routePredicates != null) {
			sb.append(String.format("node %s; rel(bn) %s -> .routes;", bbox, routePredicates));
			sb.append("rel (br.routes) [type=route_master] -> .route_masters;");
			sb.append("(.routes; rel (r.route_masters);) -> .routes;");
			sb.append("(node(r.routes:stop)->.x; node(r.routes:platform)->.x;) -> .stops_and_platforms;");
			sb.append("rel(bn.stops_and_platforms)[public_transport=stop_area] -> .stop_areas;");
			sb.append("(.route_masters>>; .routes>>; .stop_areas>>;) -> .all_transit;");
		}
		sb.append("(");
		if (highwayPredicates != null) {
			sb.append(".highways;");
			sb.append(".highways>;");
		}
		if (routePredicates != null) {
			sb.append(".all_transit;");
		}
		sb.append(");");
		sb.append("out meta;");
		return sb.toString();
	}

    @Override
    public Future<?> download(boolean newLayer, Bounds downloadArea, ProgressMonitor progressMonitor) {
        double lat1 = downloadArea.getMinLat();
        double lon1 = downloadArea.getMinLon();
        double lat2 = downloadArea.getMaxLat();
        double lon2 = downloadArea.getMaxLon();
        return download(new OverpassDownloadReader(downloadArea, OverpassServerPreference.getOverpassServer(), getQuery(getHighwayPredicates(), getRoutePredicates(), lon1, lat1, lon2, lat2)), newLayer, downloadArea, progressMonitor);
    }

}
