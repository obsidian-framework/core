package com.obsidian.core.livecomponents.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Request from client to execute LiveComponent action.
 * Contains component ID, action name, current state and parameters.
 */
public class ComponentRequest
{
    /** Component unique identifier */
    private String componentId;

    /** Action method name to execute */
    private String action;

    /** Current component state from client */
    private Map<String, Object> state = new HashMap<>();

    /** Action method parameters */
    private List<Object> params = new ArrayList<>();

    /**
     * Gets component ID.
     *
     * @return Component ID
     */
    public String getComponentId() { return componentId; }

    /**
     * Sets component ID.
     *
     * @param componentId Component ID
     */
    public void setComponentId(String componentId) { this.componentId = componentId; }

    /**
     * Gets action name.
     *
     * @return Action method name
     */
    public String getAction() { return action; }

    /**
     * Sets action name.
     *
     * @param action Action method name
     */
    public void setAction(String action) { this.action = action; }

    /**
     * Gets component state.
     *
     * @return State map
     */
    public Map<String, Object> getState() { return state; }

    /**
     * Sets component state.
     *
     * @param state State map
     */
    public void setState(Map<String, Object> state) { this.state = state; }

    /**
     * Gets action parameters.
     *
     * @return Parameter list
     */
    public List<Object> getParams() { return params; }

    /**
     * Sets action parameters.
     *
     * @param params Parameter list
     */
    public void setParams(List<Object> params) { this.params = params; }
}