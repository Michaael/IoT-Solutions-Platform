package com.ispf.driver.opcuaserver;

import com.ispf.driver.DriverException;
import org.eclipse.milo.opcua.sdk.server.OpcUaServer;
import org.eclipse.milo.opcua.sdk.server.UaNodeManager;
import org.eclipse.milo.opcua.sdk.server.api.DataItem;
import org.eclipse.milo.opcua.sdk.server.api.ManagedNamespaceWithLifecycle;
import org.eclipse.milo.opcua.sdk.server.api.MonitoredItem;
import org.eclipse.milo.opcua.sdk.server.nodes.UaFolderNode;
import org.eclipse.milo.opcua.sdk.server.nodes.UaNodeContext;
import org.eclipse.milo.opcua.sdk.server.nodes.UaVariableNode;
import org.eclipse.milo.opcua.stack.core.Identifiers;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.QualifiedName;
import org.eclipse.milo.opcua.stack.core.types.builtin.Variant;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Custom OPC UA namespace backing ISPF server variable points.
 */
final class IspfOpcUaNamespace extends ManagedNamespaceWithLifecycle {

    private static final String NAMESPACE_URI = "urn:ispf:opcua:server";

    private final UaNodeManager nodeManager = new UaNodeManager();
    private final Map<NodeId, UaVariableNode> variableNodes = new ConcurrentHashMap<>();
    private final Map<NodeId, String> variableValues = new ConcurrentHashMap<>();
    private UaFolderNode variablesFolder;

    IspfOpcUaNamespace(OpcUaServer server) {
        super(server, NAMESPACE_URI);
        getLifecycleManager().addStartupTask(this::initializeNodes);
    }

    void ensureVariable(OpcUaServerPoint point) throws DriverException {
        if (variableNodes.containsKey(point.nodeId())) {
            return;
        }
        try {
            UaNodeContext context = new IspfNodeContext(getServer(), nodeManager);
            UaVariableNode variableNode = new UaVariableNode(
                    context,
                    point.nodeId(),
                    new QualifiedName(getNamespaceIndex(), point.nodeIdText()),
                    LocalizedText.english(point.nodeIdText())
            );
            variableNode.setDataType(Identifiers.String);
            variableNode.setAccessLevel(Unsigned.ubyte(3));
            variableNode.setUserAccessLevel(Unsigned.ubyte(3));
            String initialValue = variableValues.computeIfAbsent(point.nodeId(), ignored -> "");
            variableNode.setValue(new DataValue(new Variant(initialValue)));
            nodeManager.addNode(variableNode);
            if (variablesFolder != null) {
                variablesFolder.addOrganizes(variableNode);
            }
            variableNodes.put(point.nodeId(), variableNode);
        } catch (Exception e) {
            throw new DriverException("Failed to create OPC UA variable node " + point.nodeId(), e);
        }
    }

    void writeValue(NodeId nodeId, String value) {
        variableValues.put(nodeId, value);
        UaVariableNode node = variableNodes.get(nodeId);
        if (node != null) {
            node.setValue(new DataValue(new Variant(value)));
        }
    }

    String readValue(NodeId nodeId) {
        return variableValues.getOrDefault(nodeId, "");
    }

    @Override
    public void onDataItemsCreated(List<DataItem> dataItems) {
        // no-op for v0.1
    }

    @Override
    public void onDataItemsModified(List<DataItem> dataItems) {
        // no-op for v0.1
    }

    @Override
    public void onDataItemsDeleted(List<DataItem> dataItems) {
        // no-op for v0.1
    }

    @Override
    public void onMonitoringModeChanged(List<MonitoredItem> monitoredItems) {
        // no-op for v0.1
    }

    private void initializeNodes() {
        registerNodeManager(nodeManager);
        UaNodeContext context = new IspfNodeContext(getServer(), nodeManager);
        variablesFolder = new UaFolderNode(
                context,
                newNodeId("IspfVariables"),
                new QualifiedName(getNamespaceIndex(), "IspfVariables"),
                LocalizedText.english("ISPF Variables")
        );
        nodeManager.addNode(variablesFolder);
        getServer().getAddressSpaceManager()
                .getManagedNode(Identifiers.ObjectsFolder)
                .ifPresent(objectsFolder -> ((UaFolderNode) objectsFolder).addOrganizes(variablesFolder));
    }

    private static final class IspfNodeContext implements UaNodeContext {
        private final OpcUaServer server;
        private final UaNodeManager nodeManager;

        private IspfNodeContext(OpcUaServer server, UaNodeManager nodeManager) {
            this.server = server;
            this.nodeManager = nodeManager;
        }

        @Override
        public OpcUaServer getServer() {
            return server;
        }

        @Override
        public org.eclipse.milo.opcua.sdk.server.api.NodeManager<org.eclipse.milo.opcua.sdk.server.nodes.UaNode> getNodeManager() {
            return nodeManager;
        }
    }
}
