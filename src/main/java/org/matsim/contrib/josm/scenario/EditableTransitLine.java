package org.matsim.contrib.josm.scenario;

import org.matsim.api.core.v01.Id;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class EditableTransitLine implements TransitLine {

    final Id<TransitLine> id;
    Id<TransitLine> realId;
    String name;
    final Map<Id<TransitRoute>, EditableTransitRoute> editableRoutes = new HashMap<>();

    public EditableTransitLine(Id<TransitLine> id) {
        this.id = id;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void setName(String name) {
        this.name = name;
    }

    public Map<Id<TransitRoute>, EditableTransitRoute> getEditableRoutes() {
        return editableRoutes;
    }

    @Override
    public void addRoute(TransitRoute transitRoute) {
        editableRoutes.put(transitRoute.getId(), (EditableTransitRoute) transitRoute);
    }

    @Override
    public Map<Id<TransitRoute>, TransitRoute> getRoutes() {
        return Collections.<Id<TransitRoute>, TransitRoute>unmodifiableMap(editableRoutes);
    }

    @Override
    public boolean removeRoute(TransitRoute transitRoute) {
        return editableRoutes.remove(transitRoute.getId()) != null;
    }

    @Override
    public Id<TransitLine> getId() {
        return id;
    }

    public Id<TransitLine> getRealId() {
        return realId;
    }

    public void setRealId(Id<TransitLine> realId) {
        this.realId = realId;
    }

}
