package org.matsim.contrib.josm;

//License: GPL. For details, see LICENSE file.

import java.util.concurrent.Future;

import org.openstreetmap.josm.actions.downloadtasks.DownloadOsmTask;
import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.gui.progress.ProgressMonitor;

/**
 * Open the download dialog and download the data. Run in the worker thread.
 */
public class DownloadMATSimOsmTask extends DownloadOsmTask {

    @Override
    public Future<?> download(boolean newLayer, Bounds downloadArea, ProgressMonitor progressMonitor) {
        return download(new FilteredDownloader(downloadArea), newLayer, downloadArea, progressMonitor);
    }

}
