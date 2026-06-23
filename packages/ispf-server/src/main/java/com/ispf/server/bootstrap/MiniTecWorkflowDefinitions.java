package com.ispf.server.bootstrap;

/**
 * BPMN workflows for mini-TEC automation.
 */
public final class MiniTecWorkflowDefinitions {

    public static final String GAS_EMERGENCY_TRIP = """
            <?xml version="1.0" encoding="UTF-8"?>
            <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                         xmlns:ispf="http://ispf.io/bpmn"
                         targetNamespace="http://ispf.io/bpmn">
              <process id="mini-tec-gas-emergency-trip" name="Gas emergency trip" isExecutable="true">
                <startEvent id="start"/>
                <serviceTask id="tripGas" name="Trip gas supply"
                             ispf:action="setVariable"
                             ispf:targetObject="%s"
                             ispf:variable="cmdGasTrip"
                             ispf:value="true"/>
                <serviceTask id="stopGpus" name="Stop GPUs"
                             ispf:action="setVariable"
                             ispf:targetObject="%s"
                             ispf:variable="cmdStop"
                             ispf:value="true"/>
                <endEvent id="end"/>
                <sequenceFlow sourceRef="start" targetRef="tripGas"/>
                <sequenceFlow sourceRef="tripGas" targetRef="stopGpus"/>
                <sequenceFlow sourceRef="stopGpus" targetRef="end"/>
              </process>
            </definitions>
            """.formatted(MiniTecPaths.GRPB, MiniTecPaths.GPU_01);

    public static final String LOAD_AUTO_UNLOAD = """
            <?xml version="1.0" encoding="UTF-8"?>
            <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                         xmlns:ispf="http://ispf.io/bpmn"
                         targetNamespace="http://ispf.io/bpmn">
              <process id="mini-tec-load-module-auto-unload" name="Load module auto unload" isExecutable="true">
                <startEvent id="start"/>
                <serviceTask id="reduceLoad" name="Reduce load setpoint"
                             ispf:action="setVariable"
                             ispf:targetObject="%s"
                             ispf:variable="loadSetpointPct"
                             ispf:value="15"/>
                <endEvent id="end"/>
                <sequenceFlow sourceRef="start" targetRef="reduceLoad"/>
                <sequenceFlow sourceRef="reduceLoad" targetRef="end"/>
              </process>
            </definitions>
            """.formatted(MiniTecPaths.LOAD_MODULE);

    public static final String GPU_START_SEQUENCE = """
            <?xml version="1.0" encoding="UTF-8"?>
            <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                         xmlns:ispf="http://ispf.io/bpmn"
                         targetNamespace="http://ispf.io/bpmn">
              <process id="mini-tec-gpu-start-sequence" name="GPU start sequence" isExecutable="true">
                <startEvent id="start"/>
                <serviceTask id="checkGrpb" name="Check GRPB valve"
                             ispf:action="log"
                             ispf:message="mini-tec: GRPB check before GPU start"/>
                <serviceTask id="startGpu" name="Start GPU"
                             ispf:action="setVariable"
                             ispf:targetObject="%s"
                             ispf:variable="cmdStart"
                             ispf:value="true"/>
                <endEvent id="end"/>
                <sequenceFlow sourceRef="start" targetRef="checkGrpb"/>
                <sequenceFlow sourceRef="checkGrpb" targetRef="startGpu"/>
                <sequenceFlow sourceRef="startGpu" targetRef="end"/>
              </process>
            </definitions>
            """.formatted(MiniTecPaths.GPU_01);

    public static final String ACK_PROTECTION = """
            <?xml version="1.0" encoding="UTF-8"?>
            <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                         xmlns:ispf="http://ispf.io/bpmn"
                         targetNamespace="http://ispf.io/bpmn">
              <process id="mini-tec-ack-protection" name="Acknowledge protection" isExecutable="true">
                <startEvent id="start"/>
                <serviceTask id="unlatch" name="Unlatch alarm"
                             ispf:action="setVariable"
                             ispf:targetObject="%s"
                             ispf:variable="alarmLatched"
                             ispf:value="false"/>
                <endEvent id="end"/>
                <sequenceFlow sourceRef="start" targetRef="unlatch"/>
                <sequenceFlow sourceRef="unlatch" targetRef="end"/>
              </process>
            </definitions>
            """.formatted(MiniTecPaths.STATION_HUB);

    private MiniTecWorkflowDefinitions() {
    }
}
