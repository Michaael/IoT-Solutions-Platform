package com.ispf.driver.opcuaserver;

import com.ispf.driver.DriverException;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;

/**
 * Parsed OPC UA server point from mapping string {@code nodeId}.
 */
record OpcUaServerPoint(NodeId nodeId, String nodeIdText) {

    static OpcUaServerPoint parse(String mapping, int namespaceIndex) throws DriverException {
        if (mapping == null || mapping.isBlank()) {
            throw new DriverException("OPC UA server mapping requires nodeId: " + mapping);
        }
        String trimmed = mapping.trim();
        try {
            NodeId nodeId = NodeId.parse(trimmed);
            if (nodeId.getNamespaceIndex().intValue() == 0 && !trimmed.startsWith("ns=") && !trimmed.startsWith("i=")) {
                nodeId = new NodeId(namespaceIndex, trimmed);
            }
            return new OpcUaServerPoint(nodeId, trimmed);
        } catch (Exception e) {
            try {
                NodeId nodeId = new NodeId(namespaceIndex, trimmed);
                return new OpcUaServerPoint(nodeId, trimmed);
            } catch (Exception nested) {
                throw new DriverException("Invalid OPC UA server nodeId: " + mapping, e);
            }
        }
    }
}
