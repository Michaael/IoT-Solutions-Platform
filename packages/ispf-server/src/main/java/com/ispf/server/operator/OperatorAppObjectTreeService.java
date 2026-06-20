package com.ispf.server.operator;

import com.ispf.core.object.ObjectType;
import com.ispf.server.object.ObjectManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Set;

@Service
public class OperatorAppObjectTreeService {

    public static final String OPERATOR_APPS_ROOT = "root.platform.operator-apps";

    private final ObjectManager objectManager;
    private final OperatorAppUiStore store;

    public OperatorAppObjectTreeService(ObjectManager objectManager, OperatorAppUiStore store) {
        this.objectManager = objectManager;
        this.store = store;
    }

    @Transactional
    public void syncAll() {
        ensureRoot();
        Set<String> expected = new HashSet<>();
        for (OperatorAppUiStore.OperatorAppUiRecord record : store.listAll()) {
            String nodeName = sanitizeNodeName(record.appId());
            expected.add(nodeName);
            String path = OPERATOR_APPS_ROOT + "." + nodeName;
            ensureNode(
                    path,
                    ObjectType.APPLICATION,
                    record.title(),
                    "Operator UI, appId=" + record.appId(),
                    "operator-app-v1"
            );
        }
        pruneChildren(OPERATOR_APPS_ROOT, expected);
    }

    private void ensureRoot() {
        if (objectManager.tree().findByPath(OPERATOR_APPS_ROOT).isEmpty()) {
            objectManager.create(
                    "root.platform",
                    "operator-apps",
                    ObjectType.CUSTOM,
                    "Operator Apps",
                    "Operator HMI — набор дашбордов для ?mode=operator&app=<id>",
                    "app-folder-v1"
            );
        }
    }

    private void ensureNode(
            String path,
            ObjectType type,
            String displayName,
            String description,
            String templateId
    ) {
        if (objectManager.tree().findByPath(path).isPresent()) {
            objectManager.updateInfo(path, displayName, description);
            return;
        }
        int lastDot = path.lastIndexOf('.');
        String parentPath = path.substring(0, lastDot);
        String name = path.substring(lastDot + 1);
        objectManager.create(parentPath, name, type, displayName, description, templateId);
    }

    private void pruneChildren(String folderPath, Set<String> expectedChildNames) {
        if (objectManager.tree().findByPath(folderPath).isEmpty()) {
            return;
        }
        for (var child : objectManager.tree().childrenOf(folderPath)) {
            String childName = child.path().substring(child.path().lastIndexOf('.') + 1);
            if (!expectedChildNames.contains(childName)) {
                objectManager.delete(child.path());
            }
        }
    }

    private static String sanitizeNodeName(String name) {
        if (name == null || name.isBlank()) {
            return "node";
        }
        String sanitized = name.replaceAll("[^a-zA-Z0-9_-]", "_");
        if (sanitized.isEmpty()) {
            return "node";
        }
        if (Character.isDigit(sanitized.charAt(0))) {
            return "n_" + sanitized;
        }
        return sanitized;
    }
}
