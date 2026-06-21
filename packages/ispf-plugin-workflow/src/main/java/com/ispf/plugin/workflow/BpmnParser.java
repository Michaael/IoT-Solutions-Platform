package com.ispf.plugin.workflow;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * BPMN 2.0 parser for ISPF workflows — startEvent, serviceTask, userTask, messageTask,
 * exclusiveGateway, parallelGateway, endEvent, sequenceFlow with conditions.
 */
public class BpmnParser {

    private static final String ISPF_NS = "http://ispf.io/bpmn";

    public BpmnProcess parse(String bpmnXml) throws WorkflowException {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            Document document = factory.newDocumentBuilder()
                    .parse(new ByteArrayInputStream(bpmnXml.getBytes(StandardCharsets.UTF_8)));

            Element process = firstElement(document, "process");
            if (process == null) {
                throw new WorkflowException("BPMN process element not found");
            }

            String processId = process.getAttribute("id");
            String processName = process.getAttribute("name");

            Map<String, String> nodeTypes = new HashMap<>();
            Map<String, ServiceTaskDefinition> serviceTasks = new HashMap<>();
            Map<String, UserTaskDefinition> userTasks = new HashMap<>();
            Map<String, MessageTaskDefinition> messageTasks = new HashMap<>();
            Map<String, SignalCatchDefinition> signalCatchEvents = new HashMap<>();
            List<SequenceFlowDefinition> sequenceFlows = new ArrayList<>();
            String startNodeId = null;

            for (Element start : elements(process, "startEvent")) {
                if (startNodeId == null) {
                    startNodeId = start.getAttribute("id");
                    nodeTypes.put(startNodeId, "startEvent");
                }
            }

            for (Element end : elements(process, "endEvent")) {
                String id = end.getAttribute("id");
                nodeTypes.put(id, "endEvent");
            }

            for (Element gateway : elements(process, "exclusiveGateway")) {
                String id = gateway.getAttribute("id");
                nodeTypes.put(id, "exclusiveGateway");
            }

            for (Element gateway : elements(process, "parallelGateway")) {
                String id = gateway.getAttribute("id");
                nodeTypes.put(id, "parallelGateway");
            }

            for (Element task : elements(process, "serviceTask")) {
                String id = task.getAttribute("id");
                nodeTypes.put(id, "serviceTask");
                serviceTasks.put(id, parseServiceTask(task));
            }

            for (Element task : elements(process, "userTask")) {
                String id = task.getAttribute("id");
                nodeTypes.put(id, "userTask");
                userTasks.put(id, parseUserTask(task));
            }

            for (Element task : elements(process, "messageTask")) {
                String id = task.getAttribute("id");
                nodeTypes.put(id, "messageTask");
                messageTasks.put(id, parseMessageTask(task));
            }

            for (Element catchEvent : elements(process, "intermediateCatchEvent")) {
                String id = catchEvent.getAttribute("id");
                nodeTypes.put(id, "intermediateCatchEvent");
                signalCatchEvents.put(id, parseSignalCatch(catchEvent));
            }

            for (Element flow : elements(process, "sequenceFlow")) {
                sequenceFlows.add(parseSequenceFlow(flow));
            }

            if (startNodeId == null || startNodeId.isBlank()) {
                throw new WorkflowException("BPMN startEvent is required");
            }

            return new BpmnProcess(
                    processId,
                    processName,
                    startNodeId,
                    nodeTypes,
                    serviceTasks,
                    userTasks,
                    messageTasks,
                    signalCatchEvents,
                    List.copyOf(sequenceFlows)
            );
        } catch (WorkflowException e) {
            throw e;
        } catch (Exception e) {
            throw new WorkflowException("Failed to parse BPMN XML", e);
        }
    }

    private SequenceFlowDefinition parseSequenceFlow(Element flow) {
        String id = flow.getAttribute("id");
        String source = flow.getAttribute("sourceRef");
        String target = flow.getAttribute("targetRef");
        String condition = readIspfAttribute(flow, "condition");
        if (condition == null || condition.isBlank()) {
            condition = readIspfAttribute(flow, "conditionExpression");
        }
        boolean defaultFlow = "true".equalsIgnoreCase(readIspfAttribute(flow, "default"));
        return new SequenceFlowDefinition(id, source, target, condition, defaultFlow);
    }

    private SignalCatchDefinition parseSignalCatch(Element catchEvent) throws WorkflowException {
        Map<String, String> parameters = new HashMap<>();
        readIspfAttribute(catchEvent, parameters, "signal");
        String signalName = parameters.get("signal");
        if (signalName == null || signalName.isBlank()) {
            Element signalDef = firstChildElement(catchEvent, "signalEventDefinition");
            if (signalDef != null) {
                signalName = signalDef.getAttribute("signalRef");
                if (signalName == null || signalName.isBlank()) {
                    signalName = readIspfAttribute(signalDef, "signal");
                }
            }
        }
        if (signalName == null || signalName.isBlank()) {
            throw new WorkflowException("Signal catch event requires ispf:signal: " + catchEvent.getAttribute("id"));
        }
        return new SignalCatchDefinition(
                catchEvent.getAttribute("id"),
                catchEvent.getAttribute("name"),
                signalName,
                Map.copyOf(parameters)
        );
    }

    private MessageTaskDefinition parseMessageTask(Element task) {
        Map<String, String> parameters = new HashMap<>();
        readIspfAttribute(task, parameters, "subject");
        readIspfAttribute(task, parameters, "message");
        readIspfAttribute(task, parameters, "channel");
        String subject = parameters.getOrDefault("subject", "ispf.workflow.message");
        String message = parameters.getOrDefault("message", task.getAttribute("name"));
        String channel = parameters.getOrDefault("channel", "nats");
        return new MessageTaskDefinition(
                task.getAttribute("id"),
                task.getAttribute("name"),
                subject,
                message,
                channel,
                Map.copyOf(parameters)
        );
    }

    private UserTaskDefinition parseUserTask(Element task) {
        Map<String, String> parameters = new HashMap<>();
        readIspfAttribute(task, parameters, "assigneeRole");
        readIspfAttribute(task, parameters, "title");
        readIspfAttribute(task, parameters, "instructions");
        readIspfAttribute(task, parameters, "action");

        String title = parameters.getOrDefault("title", task.getAttribute("name"));
        String instructions = parameters.getOrDefault("instructions", "");
        String assigneeRole = parameters.getOrDefault("assigneeRole", "operator");

        return new UserTaskDefinition(
                task.getAttribute("id"),
                task.getAttribute("name"),
                title,
                instructions,
                assigneeRole,
                Map.copyOf(parameters)
        );
    }

    private ServiceTaskDefinition parseServiceTask(Element task) throws WorkflowException {
        String id = task.getAttribute("id");
        String name = task.getAttribute("name");
        Map<String, String> parameters = new HashMap<>();

        readIspfAttribute(task, parameters, "action");
        readIspfAttribute(task, parameters, "targetObject");
        readIspfAttribute(task, parameters, "variable");
        readIspfAttribute(task, parameters, "value");
        readIspfAttribute(task, parameters, "subject");
        readIspfAttribute(task, parameters, "message");
        readIspfAttribute(task, parameters, "level");
        readIspfAttribute(task, parameters, "objectPath");
        readIspfAttribute(task, parameters, "functionName");
        readIspfAttribute(task, parameters, "inputMap");
        readIspfAttribute(task, parameters, "outputMap");
        readIspfAttribute(task, parameters, "eventName");
        readIspfAttribute(task, parameters, "payloadVariable");
        readIspfAttribute(task, parameters, "contextKey");
        readIspfAttribute(task, parameters, "sourceVariable");
        readIspfAttribute(task, parameters, "valueField");
        readIspfAttribute(task, parameters, "workflowPath");

        Element extensions = firstChildElement(task, "extensionElements");
        if (extensions != null) {
            for (Element child : childElements(extensions)) {
                String localName = child.getLocalName() != null ? child.getLocalName() : child.getNodeName();
                if (localName.contains(":")) {
                    localName = localName.substring(localName.indexOf(':') + 1);
                }
                parameters.putIfAbsent(localName, child.getTextContent().trim());
            }
        }

        String actionRaw = parameters.getOrDefault("action", "log");
        WorkflowActionType action = parseAction(actionRaw);

        return new ServiceTaskDefinition(id, name, action, Map.copyOf(parameters));
    }

    private static void readIspfAttribute(Element element, Map<String, String> parameters, String name) {
        String value = readIspfAttribute(element, name);
        if (value != null && !value.isBlank()) {
            parameters.put(name, value);
        }
    }

    private static String readIspfAttribute(Element element, String name) {
        String value = element.getAttributeNS(ISPF_NS, name);
        if (value == null || value.isBlank()) {
            value = element.getAttribute("ispf:" + name);
        }
        return value;
    }

    private static Element firstElement(Document document, String localName) {
        NodeList nodes = document.getElementsByTagNameNS("*", localName);
        if (nodes.getLength() == 0) {
            return null;
        }
        return (Element) nodes.item(0);
    }

    private static List<Element> elements(Element parent, String localName) {
        NodeList nodes = parent.getElementsByTagNameNS("*", localName);
        List<Element> result = new ArrayList<>();
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            if (node.getParentNode() == parent || isDirectChild(parent, node)) {
                result.add((Element) node);
            }
        }
        if (result.isEmpty()) {
            for (int i = 0; i < nodes.getLength(); i++) {
                result.add((Element) nodes.item(i));
            }
        }
        return result;
    }

    private static boolean isDirectChild(Element parent, Node node) {
        return node.getParentNode() != null && node.getParentNode().isSameNode(parent);
    }

    private static Element firstChildElement(Element parent, String localName) {
        for (Element child : childElements(parent)) {
            String name = child.getLocalName() != null ? child.getLocalName() : child.getNodeName();
            if (name.endsWith(localName)) {
                return child;
            }
        }
        return null;
    }

    private static List<Element> childElements(Element parent) {
        List<Element> children = new ArrayList<>();
        NodeList nodes = parent.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            if (node instanceof Element element) {
                children.add(element);
            }
        }
        return children;
    }

    private static WorkflowActionType parseAction(String actionRaw) throws WorkflowException {
        return switch (actionRaw.trim().toLowerCase(Locale.ROOT)) {
            case "log" -> WorkflowActionType.LOG;
            case "setvariable", "set_variable" -> WorkflowActionType.SET_VARIABLE;
            case "publishnats", "publish_nats" -> WorkflowActionType.PUBLISH_NATS;
            case "invokefunction", "invoke_function" -> WorkflowActionType.INVOKE_FUNCTION;
            case "fireevent", "fire_event" -> WorkflowActionType.FIRE_EVENT;
            case "readvariable", "read_variable" -> WorkflowActionType.READ_VARIABLE;
            case "startworkflow", "start_workflow" -> WorkflowActionType.START_WORKFLOW;
            default -> throw new WorkflowException("Unsupported service task action: " + actionRaw);
        };
    }
}
