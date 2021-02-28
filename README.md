[![Java CI with Gradle](https://github.com/matsim-org/josm-matsim-plugin/actions/workflows/gradle.yml/badge.svg)](https://github.com/matsim-org/josm-matsim-plugin/actions/workflows/gradle.yml)

This is a plug-in for JOSM, the OpenStreetMap editor, which lets you preview, edit and save a MATSim network
directly from the map.

##### Use
You don't need to download the source to use it. It is in the JOSM plug-in repository. Just start JOSM and look
for the MATSim plug-in.

##### Develop
Unlike MATSim, the build is not based on Maven, but on Gradle. The reason is that there are some things to do
(like editing the Manifest, downloading JOSM for compilation, and building a flat jar) for which we
found a good example in Gradle.

Use your favorite IDE to import the Gradle project and/or see the comments in `build.gradle` for details. You can
run JOSM and the plug-in in the debugger.

##### Authors
* Nico Kühnel
* Michael Zilske
