package org.matsim.contrib.josm;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.openstreetmap.josm.Main;

import java.net.URL;

public class ImportTaskTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Before
    public void init() {
        new JOSMFixture(folder.getRoot().getPath()).init();
    }

    @Test
    public void readNetworkWithoutTransit() {
        URL network = getClass().getResource("/test-input/pt-tutorial/multimodalnetwork.xml");
        new Importer(network.getFile(), null, Main.getProjection()).run();
    }

    @Test
    public void readNetworkWithTransit() {
        URL network = getClass().getResource("/test-input/pt-tutorial/multimodalnetwork.xml");
        URL transitSchedule = getClass().getResource("/test-input/pt-tutorial/transitschedule.xml");
        new Importer(network.getFile(), transitSchedule.getFile(), Main.getProjection()).run();
    }

}