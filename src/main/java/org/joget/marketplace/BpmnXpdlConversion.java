package org.joget.marketplace;

import org.joget.apps.app.model.AppDefinition;
import org.joget.apps.app.service.AppPluginUtil;
import org.joget.apps.app.service.AppService;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.form.model.Form;

import org.joget.apps.form.model.FormData;
import org.joget.apps.form.model.FormRow;
import org.joget.apps.form.model.FormRowSet;
import org.joget.apps.form.service.FileUtil;
import org.joget.apps.form.service.FormUtil;
import org.joget.plugin.base.DefaultApplicationPlugin;
import org.joget.workflow.model.WorkflowAssignment;
import org.springframework.context.ApplicationContext;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import org.apache.commons.io.FileUtils;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathFactory;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;


public class BpmnXpdlConversion extends DefaultApplicationPlugin {

    private final static String MESSAGE_PATH = "messages/BpmnXpdlConversion";
    Set<String> commonOutgoingRefs = new HashSet<>();
    Set<String> commonIngoingRefs = new HashSet<>();
    Set<String> finalRefs = new HashSet<>();

    @Override
    public String getName() {
        return AppPluginUtil.getMessage("org.joget.marketplace.BpmnXpdlConversion.pluginLabel", getClassName(), MESSAGE_PATH);
    }

    @Override
    public String getVersion() {
        return "8.0.0";
    }

    @Override
    public String getClassName() {
        return getClass().getName();
    }

    @Override
    public String getLabel() {
        //support i18n
        return AppPluginUtil.getMessage("org.joget.marketplace.BpmnXpdlConversion.pluginLabel", getClassName(), MESSAGE_PATH);
    }

    @Override
    public String getDescription() {
        //support i18n
        return AppPluginUtil.getMessage("org.joget.marketplace.BpmnXpdlConversion.pluginDesc", getClassName(), MESSAGE_PATH);
    }

    @Override
    public String getPropertyOptions() {
        return AppUtil.readPluginResource(getClassName(), "/properties/BpmnXpdlConversion.json", null, true, MESSAGE_PATH);
    }

    @Override
    public Object execute(Map map) {
        ApplicationContext ac = AppUtil.getApplicationContext();
        AppService appService = (AppService) ac.getBean("appService");
        AppDefinition appDef = AppUtil.getCurrentAppDefinition();

        // source file
        String formDefIdSourceFile = (String) map.get("formDefIdSourceFile");
        String sourceFileFieldId = (String) map.get("sourceFileFieldId");
        String sourceFileRecordId = (String) map.get("sourceFileRecordId");

        // output file
        String formDefIdOutputFile = (String) map.get("formDefIdOutputFile");
        String outputFileFieldId = (String) map.get("outputFileFieldId");
        String outputFileRecordId = (String) map.get("outputFileRecordId");

        String participantId = "participant1";
        String participantName = "Participant 1";

        WorkflowAssignment wfAssignment = (WorkflowAssignment) map.get("workflowAssignment");
        if (wfAssignment != null) {
            if (sourceFileRecordId != null && sourceFileRecordId.equals("")) {
                sourceFileRecordId = appService.getOriginProcessId(wfAssignment.getProcessId());
            }
            if (outputFileRecordId != null && outputFileRecordId.equals("")) {
                outputFileRecordId = appService.getOriginProcessId(wfAssignment.getProcessId());
            }
        }

        if (formDefIdSourceFile != null && formDefIdOutputFile != null) {
            try {
                // get pdf
                FormData formData = new FormData();
                formData.setPrimaryKeyValue(sourceFileRecordId);
                Form loadForm = appService.viewDataForm(appDef.getId(), appDef.getVersion().toString(), formDefIdSourceFile, null, null, null, formData, null, null);
                org.joget.apps.form.model.Element el = FormUtil.findElement(sourceFileFieldId, loadForm, formData);
                File bpmnFile = FileUtil.getFile(FormUtil.getElementPropertyValue(el, formData), loadForm, sourceFileRecordId);

                FormRowSet frs = new FormRowSet();
                FormRow row = new FormRow();
                StringBuilder resultBuilder = new StringBuilder();
                String fileName;
                String filePaths = bpmnFile.getPath();
                List<String> fileList = getFilesList(filePaths);

                for (String filePath : fileList) {
                    File uploadedFile = new File(filePath.trim());
                    byte[] srcPDFFileContent;
                      try (FileInputStream fileIS = new FileInputStream(uploadedFile)) {
                        srcPDFFileContent = fileIS.readAllBytes();
                    }
        
                    // Parse BPMN XML file
                    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                    factory.setNamespaceAware(true); // Ensure namespace awareness
                    factory.setExpandEntityReferences(false);
                    DocumentBuilder builder = factory.newDocumentBuilder();
                    Document bpmnDoc = builder.parse(bpmnFile);
                    bpmnDoc.getDocumentElement().normalize();

                    // Create XPDL Document
                    Document xpdlDoc = builder.newDocument();
                    org.w3c.dom.Element xpdlRoot = xpdlDoc.createElementNS("http://www.wfmc.org/2002/XPDL1.0", "Package");
                    xpdlRoot.setAttribute("xmlns:xsi", "http://www.w3.org/2001/XMLSchema-instance");
                    xpdlRoot.setAttribute("xsi:schemaLocation", "http://www.wfmc.org/2002/XPDL1.0 http://wfmc.org/standards/docs/TC-1025_schema_10_xpdl.xsd");
                    xpdlRoot.setAttribute("xmlns", "http://www.wfmc.org/2002/XPDL1.0");
                    xpdlRoot.setAttribute("Name", "BPMN to XPDL Conversion");
                    xpdlRoot.setAttribute("Id", appDef.getAppId());
                    xpdlRoot.setAttribute("xmlns:xpdl", "http://www.wfmc.org/2002/XPDL1.0");
                    xpdlDoc.appendChild(xpdlRoot);

                    // Extract BPMN process information
                    NodeList processList = bpmnDoc.getElementsByTagNameNS("*", "process");
                    for (int i = 0; i < processList.getLength(); i++) {
                        org.w3c.dom.Element bpmnProcess = (org.w3c.dom.Element) processList.item(i);
                        String processId = bpmnProcess.getAttribute("id");
                        String processName = bpmnProcess.getAttribute("name");

                        // Create Script element
                        org.w3c.dom.Element xpdlScript = xpdlDoc.createElement("Script");
                        xpdlScript.setAttribute("Type", "text/javascript");
                        xpdlRoot.appendChild(xpdlScript);

                        // Create Applications element
                        org.w3c.dom.Element xpdlApplications = xpdlDoc.createElement("Applications");
                        xpdlRoot.appendChild(xpdlApplications);
                        org.w3c.dom.Element xpdlApplication = xpdlDoc.createElement("Application");
                        xpdlApplication.setAttribute("Id", "default_application");
                        xpdlApplications.appendChild(xpdlApplication);

                        // Create PackageHeader element
                        org.w3c.dom.Element xpdlPackageHeader = xpdlDoc.createElement("PackageHeader");
                        xpdlRoot.appendChild(xpdlPackageHeader);
                        org.w3c.dom.Element xpdlVendor = xpdlDoc.createElement("Vendor");
                        xpdlPackageHeader.appendChild(xpdlVendor);
                        org.w3c.dom.Element xpdlVersion = xpdlDoc.createElement("XPDLVersion");
                        xpdlVersion.setTextContent("1.0");
                        xpdlPackageHeader.appendChild(xpdlVersion);
                        org.w3c.dom.Element xpdlCreated = xpdlDoc.createElement("Created");
                        xpdlPackageHeader.appendChild(xpdlCreated);

                        // Create XPDL WorkflowProcesses element
                        org.w3c.dom.Element xpdlProcesses = xpdlDoc.createElement("WorkflowProcesses");
                        xpdlRoot.appendChild(xpdlProcesses);
                        // Create XPDL Participants element
                        org.w3c.dom.Element xpdlParticipants = xpdlDoc.createElement("Participants");
                        xpdlRoot.appendChild(xpdlParticipants);
                        addWorkflowProcess(bpmnDoc, xpdlDoc, participantId, participantName, xpdlRoot, processId, processName, bpmnProcess, xpdlProcesses, xpdlParticipants, false, "process");
                    }

                    // Save XPDL file with indentation and formatting
                    TransformerFactory transformerFactory = TransformerFactory.newInstance();
                    Transformer transformer = transformerFactory.newTransformer();
                    transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
                    transformer.setOutputProperty(OutputKeys.INDENT, "yes");
                    transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");

                    DOMSource source = new DOMSource(xpdlDoc);
                    ByteArrayOutputStream os = new ByteArrayOutputStream();
                    StreamResult result = new StreamResult(os);
                    transformer.transform(source, result);
    
                    // write to file
                    if (os.size() > 0) {
                        fileName = writeFile(uploadedFile, appService, appDef, formDefIdOutputFile, outputFileRecordId, os.toByteArray());
                    } else {
                        // if no image and text, return original file
                        fileName = writeFile(uploadedFile, appService, appDef, formDefIdOutputFile, outputFileRecordId, srcPDFFileContent);
                    }
                    if (resultBuilder.length() > 0) {
                        resultBuilder.append(";");
                    }
                    resultBuilder.append(fileName);

                    // add to joget record
                    row.put(outputFileFieldId, resultBuilder.toString());
                    frs.add(row);
                    appService.storeFormData(appDef.getAppId(), appDef.getVersion().toString(), formDefIdOutputFile, frs, outputFileRecordId);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    private String writeFile(File uploadedFile, AppService appService, AppDefinition appDef, String formDefIdOutputFile, String outputFileRecordId, byte[] outputPDFFileContent) throws IOException {
        // output in new pdf
        String fileName = "xpdlxml.xml";

        String tableName = appService.getFormTableName(appDef, formDefIdOutputFile);
        String path = FileUtil.getUploadPath(tableName, outputFileRecordId);
        final File file = new File(path + fileName);
        FileUtils.writeByteArrayToFile(file, outputPDFFileContent);
        return fileName;
    }

    public List<String> getFilesList(String filePaths) {
        String[] fileArray = filePaths.split(";");
        List<String> fileList = new ArrayList<>();

        String directoryPath = "";
        for (String filePath : fileArray) {
            String fullPath = "";
            String trimmedPath = filePath.trim();
            int lastSeparatorIndex = trimmedPath.lastIndexOf(File.separator);
            if (lastSeparatorIndex != -1) {
                directoryPath = trimmedPath.substring(0, lastSeparatorIndex);
                String fileName = trimmedPath.substring(lastSeparatorIndex + 1);
                fullPath = directoryPath + File.separator + fileName;
            } else {
                fullPath = directoryPath + File.separator + trimmedPath;
            }
            fileList.add(fullPath);
        }
        return fileList;
    }

    private void addActivityChild(Document bpmnDoc, Document xpdlDoc, org.w3c.dom.Element xpdlActivities, String activityId, String activityName, String participantId, String actRoute) {
        // Create XPDL Activity element
        org.w3c.dom.Element xpdlActivity = xpdlDoc.createElement("Activity");
        xpdlActivity.setAttribute("Id", activityId);
        xpdlActivity.setAttribute("Name", activityName);
        xpdlActivities.appendChild(xpdlActivity);

        // Create XPDL Performer element
        org.w3c.dom.Element xpdlPerformer = xpdlDoc.createElement("Performer");
        xpdlPerformer.setTextContent(participantId);
        xpdlActivity.appendChild(xpdlPerformer);

        // Create XPDL ExtendedAttribute element
        org.w3c.dom.Element xpdlActExtAttrs = xpdlDoc.createElement("ExtendedAttributes");
        xpdlActivity.appendChild(xpdlActExtAttrs);
        org.w3c.dom.Element xpdlActExtAttr = xpdlDoc.createElement("ExtendedAttribute");
        xpdlActExtAttr.setAttribute("Value", participantId);
        xpdlActExtAttr.setAttribute("Name", "JaWE_GRAPH_PARTICIPANT_ID");
        xpdlActExtAttrs.appendChild(xpdlActExtAttr);
        org.w3c.dom.Element xpdlActExtAttr2 = xpdlDoc.createElement("ExtendedAttribute");
              
        // get x y
        String xyString = findActivityCoordinate(bpmnDoc, activityId);
        if(xyString.equals("")){
            xyString = activityId + "/0/0";
        }
        
        String[] parts = xyString.split("/");

        if (parts[0].equals(activityId)) {
            xpdlActExtAttr2.setAttribute("Value", parts[1] + "," + parts[2]);
            xpdlActExtAttr2.setAttribute("Name", "JaWE_GRAPH_OFFSET");
            xpdlActExtAttrs.appendChild(xpdlActExtAttr2);
        }

        switch (actRoute){
            case "activity":
            {
                // Create XPDL Implementation element
                org.w3c.dom.Element xpdlImplementation = xpdlDoc.createElement("Implementation");
                xpdlActivity.appendChild(xpdlImplementation);
                org.w3c.dom.Element xpdlImplementationNo = xpdlDoc.createElement("No");
                xpdlImplementation.appendChild(xpdlImplementationNo);
                break;
            }
            case "route":
            {
                // Create XPDL Route element
                org.w3c.dom.Element xpdlRoute = xpdlDoc.createElement("Route");
                xpdlActivity.appendChild(xpdlRoute);

                addTransitionRestriction(xpdlDoc, xpdlActivity, activityId);

                break;
            }
            case "subflow":
            {
                // Create XPDL Implementation element
                org.w3c.dom.Element xpdlImplementation = xpdlDoc.createElement("Implementation");
                xpdlActivity.appendChild(xpdlImplementation);
                org.w3c.dom.Element xpdlSubFlow = xpdlDoc.createElement("SubFlow");
                xpdlSubFlow.setAttribute("Id", activityId);
                xpdlSubFlow.setAttribute("Execution", "SYNCHR");
                xpdlImplementation.appendChild(xpdlSubFlow);
                addTransitionRestriction(xpdlDoc, xpdlActivity, activityId);
                break;
            }
            default:
                break;
        }
    }

    private String findActivityCoordinate(Document bpmnDoc, String activityId) {
        String xyString = new String();

        NodeList bpmnDiagramList = bpmnDoc.getElementsByTagNameNS("*","BPMNDiagram");
        NodeList bpmnPlaneList = ((org.w3c.dom.Element) bpmnDiagramList.item(0)).getElementsByTagNameNS("*","BPMNPlane");
        NodeList bpmnShapeList = ((org.w3c.dom.Element) bpmnPlaneList.item(0)).getElementsByTagNameNS("*","BPMNShape");

        for (int i = 0; i < bpmnShapeList.getLength(); i++) {
            org.w3c.dom.Element bpmnShape = (org.w3c.dom.Element) bpmnShapeList.item(i);
            String bpmnElement = bpmnShape.getAttribute("bpmnElement");
            if(bpmnElement.equals(activityId)){
                NodeList bpmnBoundsList = ((org.w3c.dom.Element) bpmnShapeList.item(i)).getElementsByTagNameNS("*","Bounds");
                org.w3c.dom.Element bpmnBounds = (org.w3c.dom.Element) bpmnBoundsList.item(0);
                String x = bpmnBounds.getAttribute("x");
                String y = bpmnBounds.getAttribute("y");
                xyString = bpmnElement + "/" + x + "/" + y;
                break;
            }
        }

       return xyString;
    }

    private void addWorkflowProcess(Document bpmnDoc, Document xpdlDoc, String participantId, String participantName, org.w3c.dom.Element xpdlRoot, String processId, String processName, org.w3c.dom.Element bpmnProcess, org.w3c.dom.Element xpdlProcesses, org.w3c.dom.Element xpdlParticipants, Boolean subprocessvalid, String processOrSub){
        try{
            XPath xpath = XPathFactory.newInstance().newXPath();
            List<String> sourceRefs = new ArrayList<>();
            List<String> targetRefs = new ArrayList<>();
           
            org.w3c.dom.Element xpdlProcess = xpdlDoc.createElement("WorkflowProcess");
            xpdlProcess.setAttribute("Id", processId);
            xpdlProcess.setAttribute("Name", processName);
            xpdlProcesses.appendChild(xpdlProcess);

            // Process activities
            org.w3c.dom.Element xpdlActivities = xpdlDoc.createElement("Activities");
            xpdlProcess.appendChild(xpdlActivities);


            // get sequence flow from BPMN for start workflow extended attribute
            int transitionNo = 1;
            Boolean skipTransition = false;

            // Transitions
            org.w3c.dom.Element xpdlTransitions = xpdlDoc.createElement("Transitions");
            xpdlProcess.appendChild(xpdlTransitions);

            // Extended Attributes
            org.w3c.dom.Element xpdlExtAttrs = xpdlDoc.createElement("ExtendedAttributes");
            xpdlProcess.appendChild(xpdlExtAttrs);
            org.w3c.dom.Element xpdlExtAttr = xpdlDoc.createElement("ExtendedAttribute");
            xpdlExtAttr.setAttribute("Name", "JaWE_GRAPH_WORKFLOW_PARTICIPANT_ORDER");
            xpdlExtAttr.setAttribute("Value", participantId);
            xpdlExtAttrs.appendChild(xpdlExtAttr);

            String xpathExpressionSeqFlow = "//*[local-name()='" + processOrSub + "']/*[contains(local-name(), 'sequenceFlow')]";
            NodeList seqFlow = (NodeList) xpath.evaluate(xpathExpressionSeqFlow, bpmnProcess, XPathConstants.NODESET);
            for (int j = 0; j < seqFlow.getLength(); j++) {
                skipTransition = false;
                org.w3c.dom.Element bpmnSeq = (org.w3c.dom.Element) seqFlow.item(j);
                String sourceRefSeq = bpmnSeq.getAttribute("sourceRef");
                String targetRefSeq = bpmnSeq.getAttribute("targetRef");
                sourceRefs.add(sourceRefSeq);
                targetRefs.add(targetRefSeq);
 
                // start event
                if(j == 0){
                    String xyString = findActivityCoordinate(bpmnDoc, sourceRefSeq);
                    if(xyString.equals("")){
                        xyString = sourceRefSeq + "/0/0";
                    }
                    String[] parts = xyString.split("/");
                    org.w3c.dom.Element bpmnStartEvent = (org.w3c.dom.Element) seqFlow.item(0);
                    String targetRef = bpmnStartEvent.getAttribute("targetRef");
                    org.w3c.dom.Element xpdlExtAttr2 = xpdlDoc.createElement("ExtendedAttribute");
                    xpdlExtAttr2.setAttribute("Name", "JaWE_GRAPH_START_OF_WORKFLOW");
                    xpdlExtAttr2.setAttribute("Value", "JaWE_GRAPH_PARTICIPANT_ID=" + participantId + ",CONNECTING_ACTIVITY_ID=" + targetRef + ",X_OFFSET=" + parts[1] + ",Y_OFFSET=" + parts[2] + ",JaWE_GRAPH_TRANSITION_STYLE=NO_ROUTING_ORTHOGONAL,TYPE=START_DEFAULT");
                    xpdlExtAttrs.appendChild(xpdlExtAttr2);
                    continue;
                }
             
 
                // get end events from BPMN for end workflow extended attribute
                String xpathExpressionEndEvent = "//*[local-name()='" + processOrSub + "']/*[contains(local-name(), 'endEvent')]";
                NodeList endEvent = (NodeList) xpath.evaluate(xpathExpressionEndEvent, bpmnProcess, XPathConstants.NODESET);
                for (int k = 0; k < endEvent.getLength(); k++) {
                    String xyString = findActivityCoordinate(bpmnDoc, targetRefSeq);
                    if(xyString.equals("")){
                        xyString = targetRefSeq + "/0/0";
                    }
                    String[] parts = xyString.split("/");
                    org.w3c.dom.Element bpmnEndEvent = (org.w3c.dom.Element) endEvent.item(k);
                    String endEventId = bpmnEndEvent.getAttribute("id");
 
                    if(endEventId.equals(targetRefSeq)){
 
                        // check if is error event instead of end event
                        XPathExpression checkErrorEventDefinition = xpath.compile("./*[local-name()='errorEventDefinition']");
                        NodeList errorEventDefinitionList = (NodeList) checkErrorEventDefinition.evaluate(endEvent.item(k), XPathConstants.NODESET);
 
                        if (errorEventDefinitionList.getLength()==1){
                            addActivityChild(bpmnDoc, xpdlDoc, xpdlActivities, endEventId, endEventId, participantId, "activity");         
                            addTransition(xpdlDoc, xpdlTransitions, endEventId, sourceRefSeq, ++transitionNo);
                            finalRefs.add(transitionNo + "," + endEventId + "," + sourceRefSeq);
                            org.w3c.dom.Element xpdlExtAttr3 = xpdlDoc.createElement("ExtendedAttribute");
                            xpdlExtAttr3.setAttribute("Name", "JaWE_GRAPH_END_OF_WORKFLOW");
                            xpdlExtAttr3.setAttribute("Value", "JaWE_GRAPH_PARTICIPANT_ID=" + participantId + ",CONNECTING_ACTIVITY_ID=" + endEventId + ",X_OFFSET=" + parts[1] + ",Y_OFFSET=" + parts[2] + ",JaWE_GRAPH_TRANSITION_STYLE=NO_ROUTING_ORTHOGONAL,TYPE=END_DEFAULT");
                            xpdlExtAttrs.appendChild(xpdlExtAttr3);
                        } else {
                            org.w3c.dom.Element xpdlExtAttr3 = xpdlDoc.createElement("ExtendedAttribute");
                            xpdlExtAttr3.setAttribute("Name", "JaWE_GRAPH_END_OF_WORKFLOW");
                            xpdlExtAttr3.setAttribute("Value", "JaWE_GRAPH_PARTICIPANT_ID=" + participantId + ",CONNECTING_ACTIVITY_ID=" + sourceRefSeq + ",X_OFFSET=" + parts[1] + ",Y_OFFSET=" + parts[2] + ",JaWE_GRAPH_TRANSITION_STYLE=NO_ROUTING_ORTHOGONAL,TYPE=END_DEFAULT");
                            xpdlExtAttrs.appendChild(xpdlExtAttr3);
                        }      
                        skipTransition = true;                 
                    }
                }
 
                if(!skipTransition){
                    addTransition(xpdlDoc, xpdlTransitions, targetRefSeq, sourceRefSeq, ++transitionNo);
                    finalRefs.add(transitionNo + "," + targetRefSeq + "," + sourceRefSeq);
                }
            }

            // get exclusive gateway from BPMN
            String xpathExpressionGateway = "//*[local-name()='" + processOrSub + "']/*[contains(translate(local-name(), 'ABCDEFGHIJKLMNOPQRSTUVWXYZ', 'abcdefghijklmnopqrstuvwxyz'), 'gateway')]";
            NodeList gateway = (NodeList) xpath.evaluate(xpathExpressionGateway, bpmnProcess, XPathConstants.NODESET);

            for (int j = 0; j < gateway.getLength(); j++) {
                org.w3c.dom.Element bpmnGateway = (org.w3c.dom.Element) gateway.item(j);
                String gatewayId = bpmnGateway.getAttribute("id");
                String gatewayName = bpmnGateway.getAttribute("name");

                addActivityChild(bpmnDoc, xpdlDoc, xpdlActivities, gatewayId, gatewayName, participantId,  "route");
            }

            // get tasks and boundary events from BPMN
            String xpathExpressionTasksAndBoundaryEvents = "//*[local-name()='" + processOrSub + "']/*[contains(translate(local-name(), 'ABCDEFGHIJKLMNOPQRSTUVWXYZ', 'abcdefghijklmnopqrstuvwxyz'), 'task') or contains(translate(local-name(), 'ABCDEFGHIJKLMNOPQRSTUVWXYZ', 'abcdefghijklmnopqrstuvwxyz'), 'boundaryevent')]";
            NodeList tasks = (NodeList) xpath.evaluate(xpathExpressionTasksAndBoundaryEvents, bpmnProcess, XPathConstants.NODESET);

            for (int j = 0; j < tasks.getLength(); j++) {
                org.w3c.dom.Element bpmnTask = (org.w3c.dom.Element) tasks.item(j);
                String taskId = bpmnTask.getAttribute("id");
                String taskName = bpmnTask.getAttribute("name");
                String attachedToRef = bpmnTask.getAttribute("attachedToRef");
                if(taskName.equals("")){
                    taskName = taskId;
                }
                
                addActivityChild(bpmnDoc, xpdlDoc, xpdlActivities, taskId, taskName, participantId, "activity");
                if(!attachedToRef.equals("")){
                    addTransition(xpdlDoc, xpdlTransitions, taskId, attachedToRef, ++transitionNo);
                    finalRefs.add(transitionNo + "," + taskId + "," + attachedToRef);
                }
            }

            // get subprocess from BPMN
            String xpathExpressionSubProcess = "//*[local-name()='" + processOrSub + "']/*[contains(local-name(), 'subProcess')]";
            NodeList subProcess = (NodeList) xpath.evaluate(xpathExpressionSubProcess, bpmnProcess, XPathConstants.NODESET);

            org.w3c.dom.Element bpmnSubProcess = null;
            String subProcessId = "";
            String subProcessName = "";
            for (int j = 0; j < subProcess.getLength(); j++) {
                bpmnSubProcess = (org.w3c.dom.Element) subProcess.item(j);
                subProcessId = bpmnSubProcess.getAttribute("id");
                subProcessName = bpmnSubProcess.getAttribute("name");

                addActivityChild(bpmnDoc, xpdlDoc, xpdlActivities, subProcessId, subProcessName, participantId, "subflow");
                subprocessvalid = true;
            }
            if (subprocessvalid){
                addWorkflowProcess(bpmnDoc, xpdlDoc, subProcessId + "_" + participantId, "Participant", xpdlRoot, subProcessId, subProcessName, bpmnSubProcess, xpdlProcesses, xpdlParticipants, false, "subProcess");
            }

            // Process Header
            org.w3c.dom.Element xpdlProcessHeader = xpdlDoc.createElement("ProcessHeader");
            xpdlProcessHeader.setAttribute("DurationUnit", "h");
            xpdlProcess.appendChild(xpdlProcessHeader);

            // Data Fields
            org.w3c.dom.Element xpdlDataFields = xpdlDoc.createElement("DataFields");
            xpdlProcess.appendChild(xpdlDataFields);
            org.w3c.dom.Element xpdlDataField = xpdlDoc.createElement("DataField");
            xpdlDataField.setAttribute("IsArray", "FALSE");
            xpdlDataField.setAttribute("Id", "status");
            xpdlDataFields.appendChild(xpdlDataField);
            org.w3c.dom.Element xpdlDataType = xpdlDoc.createElement("DataType");
            xpdlDataField.appendChild(xpdlDataType);
            org.w3c.dom.Element xpdlBasicType = xpdlDoc.createElement("BasicType");
            xpdlBasicType.setAttribute("Type", "STRING");
            xpdlDataType.appendChild(xpdlBasicType);

            // Create XPDL Participant element
            
            org.w3c.dom.Element xpdlParticipant = xpdlDoc.createElement("Participant");
            xpdlParticipant.setAttribute("Id", participantId);
            xpdlParticipant.setAttribute("Name", participantName);
            xpdlParticipants.appendChild(xpdlParticipant);
            org.w3c.dom.Element xpdlParticipantType = xpdlDoc.createElement("ParticipantType");
            xpdlParticipantType.setAttribute("Type", "ROLE");
            xpdlParticipant.appendChild(xpdlParticipantType);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void addTransition(Document xpdlDoc,  org.w3c.dom.Element xpdlTransitions, String to, String from, int transitionNo){
        org.w3c.dom.Element xpdlTransition = xpdlDoc.createElement("Transition");
        xpdlTransition.setAttribute("Id", "transition" + transitionNo);
        xpdlTransition.setAttribute("To", to);
        xpdlTransition.setAttribute("From", from);
        xpdlTransitions.appendChild(xpdlTransition);
    }

    private void addTransitionRestriction(Document xpdlDoc, org.w3c.dom.Element xpdlActivity, String activityId){
         // Create XPDL TransitionRestrictions element
         org.w3c.dom.Element xpdlTransitionRestrictions = xpdlDoc.createElement("TransitionRestrictions");
         xpdlActivity.appendChild(xpdlTransitionRestrictions);
         org.w3c.dom.Element xpdlTransitionRestriction = xpdlDoc.createElement("TransitionRestriction");
         xpdlTransitionRestrictions.appendChild(xpdlTransitionRestriction);

         org.w3c.dom.Element xpdlJoin = xpdlDoc.createElement("Join");
         xpdlJoin.setAttribute("Type", "XOR");
         xpdlTransitionRestriction.appendChild(xpdlJoin);
         org.w3c.dom.Element xpdlTransitionRefsJoin = xpdlDoc.createElement("TransitionRefs");
         xpdlJoin.appendChild(xpdlTransitionRefsJoin);

         org.w3c.dom.Element xpdlSplit = xpdlDoc.createElement("Split");
         xpdlSplit.setAttribute("Type", "XOR");
         xpdlTransitionRestriction.appendChild(xpdlSplit);
         org.w3c.dom.Element xpdlTransitionRefsSplit = xpdlDoc.createElement("TransitionRefs");
         xpdlSplit.appendChild(xpdlTransitionRefsSplit);
      
         Set<String> seen = new HashSet<>();
         Set<String> seenTo = new HashSet<>();

         // Iterate through finalRef to find duplicates in second column
         for (String entry : finalRefs) {
             String[] parts = entry.split(",");
             if (parts.length >= 3) {
                 String id = parts[0];
                 String to = parts[1];
                 String from = parts[2];
 
                 // Check if sourceRef has been seen before and add to commonRefs if duplicate
                 if (!seen.add(from)) {
                    commonOutgoingRefs.add(from);
                 }
                 if (!seenTo.add(to)) {
                    commonIngoingRefs.add(to);
                }
             }
         }

         // matching outgoing flows
         for (String commonRef : commonOutgoingRefs) {
             if(commonRef.equals(activityId)){
                 for (String finalRef : finalRefs) {
                     if(finalRef.contains(activityId)){
                         String[] partsFinalRef = finalRef.split(",");
                         if (partsFinalRef.length >= 3) {
                             String transId = partsFinalRef[0]; 
                             String to = partsFinalRef[1];   
                             String from = partsFinalRef[2]; 
                             
                             if(from.equals(activityId)){
                                org.w3c.dom.Element xpdlTransitionRef = xpdlDoc.createElement("TransitionRef");
                                xpdlTransitionRef.setAttribute("Id", "transition" + transId);
                                xpdlTransitionRefsSplit.appendChild(xpdlTransitionRef);
                             }
                         }
                     }
                 }
             }
         }
         // matching ingoing flows
         for (String commonRef : commonIngoingRefs) {
             if (commonRef.equals(activityId)) {
                 for (String finalRef : finalRefs) {
                     if (finalRef.contains(activityId)) {
                         String[] partsFinalRef = finalRef.split(",");
                         if (partsFinalRef.length >= 3) {
                             String transId = partsFinalRef[0];
                             String to = partsFinalRef[1];
                             String from = partsFinalRef[2];

                             if (to.equals(activityId)) {
                                 org.w3c.dom.Element xpdlTransitionRef = xpdlDoc.createElement("TransitionRef");
                                 xpdlTransitionRef.setAttribute("Id", "transition" + transId);
                                 xpdlTransitionRefsJoin.appendChild(xpdlTransitionRef);
                             }
                         }
                     }
                 }
             }
         }
     }
}