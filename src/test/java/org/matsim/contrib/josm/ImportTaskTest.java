package org.matsim.contrib.josm;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

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
        new Importer(network.getFile(), null).run();
    }

    @Test
    public void readNetworkWithTransit() {
        URL network = getClass().getResource("/test-input/pt-tutorial/multimodalnetwork.xml");
        URL transitSchedule = getClass().getResource("/test-input/pt-tutorial/transitschedule.xml");
        new Importer(network.getFile(), transitSchedule.getFile()).run();
    }

}