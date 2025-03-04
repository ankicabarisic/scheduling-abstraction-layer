/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package org.ow2.proactive.sal.service.util;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

import javax.annotation.PostConstruct;

import org.apache.commons.lang3.RandomStringUtils;
import org.ow2.proactive.sal.model.*;
import org.ow2.proactive.sal.service.service.RepositoryService;
import org.ow2.proactive.sal.service.service.infrastructure.PAResourceManagerGateway;
import org.ow2.proactive.scheduler.common.exception.NotConnectedException;
import org.ow2.proactive_grid_cloud_portal.scheduler.exception.PermissionRestException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import lombok.extern.log4j.Log4j2;


@Log4j2
@Component
public class ByonUtils {

    private static PAResourceManagerGateway resourceManagerGateway;

    private static RepositoryService repositoryService;

    @Autowired
    private PAResourceManagerGateway tempResourceManagerGateway;

    @Autowired
    private RepositoryService tempRepositoryService;

    private ByonUtils() {
    }

    @PostConstruct
    private void initStaticAttributes() {
        resourceManagerGateway = this.tempResourceManagerGateway;
        repositoryService = this.tempRepositoryService;
    }

    static final int MAX_CONNECTION_RETRIES = 10;

    static final int INTERVAL = 20000;

    /**
     * @param np an Object of class NodeProperties that contains all the nodes properties needed for the candidate declaration
     * @param jobId a String identifier of the node candidate job
     * @param nodeType a String of the node type (byon or edge)
     * @return an object of class NodeCandidate
     */
    public static NodeCandidate createNodeCandidate(NodeProperties np, String jobId, String nodeType, String nodeId,
            String nodeName) {
        LOGGER.debug("Creating the {} node candidate ...", nodeType.toUpperCase());
        //Start by setting the universal nodes properties
        NodeCandidate nc = new NodeCandidate();
        nc.setPrice(np.getPrice());
        nc.setMemoryPrice(0.0);
        nc.setPricePerInvocation(0.0);
        nc.setNodeId(nodeId);

        //create a dummy cloud definition for BYON nodes
        Cloud dummyCloud = ByonUtils.getOrCreateDummyCloud(nodeType);
        //Create a dummy image
        Image image = new Image();
        image.setOperatingSystem(np.getOperatingSystem());
        //Define the hardware
        Hardware hardware = new Hardware();
        hardware.setCores(np.getCores());
        hardware.setCpuFrequency(np.getCpuFrequency());
        hardware.setDisk(np.getDisk());
        hardware.setRam(np.getRam());
        hardware.setFpga(np.getFpga());
        hardware.setGpu(np.getGpu());
        hardware.setProviderId(np.getProviderId());
        //Define the location
        Location location = new Location();
        location.setGeoLocation(np.getGeoLocation());

        //Define the properties that depend on the node type

        if (nodeType.equals("byon")) {
            String bid = RandomStringUtils.randomAlphanumeric(16);
            //set the image name
            image.setId("byon-image-" + bid);
            //set the image Name
            image.setName("byon-image-name-" + np.getOperatingSystem().getOperatingSystemFamily() + "-" +
                          np.getOperatingSystem().getOperatingSystemArchitecture());
            //set the hardware
            hardware.setId("byon-hardware-" + bid);
            hardware.setName("byon-" + nodeName);
            //set the location
            location.setId("byon-location-" + bid);
            //set the nc parameters
            nc.setNodeCandidateType(NodeCandidate.NodeCandidateTypeEnum.BYON);
            // set the nc jobIdForBYON
            nc.setJobIdForBYON(jobId);
            // set the nc jobIdForEDGE
            nc.setJobIdForEDGE(null);
        } else { //the node type is EDGE
            String eid = RandomStringUtils.randomAlphanumeric(16);
            //set the image id
            image.setId("edge-image-" + eid);
            //set the image Name
            image.setName("edge-image-name-" + np.getOperatingSystem().getOperatingSystemFamily() + "-" +
                          np.getOperatingSystem().getOperatingSystemArchitecture());
            //set the hardware
            hardware.setId("edge-hardware-" + eid);
            hardware.setName("edge-" + nodeName);
            //set the location
            location.setId("edge-location-" + eid);
            //set the nc parameters
            nc.setNodeCandidateType(NodeCandidate.NodeCandidateTypeEnum.EDGE);
            // set the nc jobIdForBYON
            nc.setJobIdForBYON(null);
            // set the nc jobIdForEDGE
            nc.setJobIdForEDGE(jobId);
        }

        nc.setCloud(dummyCloud);
        repositoryService.saveImage(image);
        nc.setImage(image);
        repositoryService.saveHardware(hardware);
        nc.setHardware(hardware);
        repositoryService.saveLocation(location);
        nc.setLocation(location);
        repositoryService.saveNodeCandidate(nc);
        LOGGER.info("{} node candidate created.", nodeType.toUpperCase());
        return nc;
    }

    /**
     * Create a dummy object of class Cloud to be used for the node candidates
     * @return the created byonCloud object
     */
    public static Cloud getOrCreateDummyCloud(String nodeType) {
        LOGGER.debug("Searching for the dummy cloud ...");
        //Check if the Byon cloud already exists
        Optional<Cloud> optCloud = Optional.ofNullable(repositoryService.getCloud(nodeType));
        if (optCloud.isPresent()) {
            LOGGER.info("Dummy cloud for {} was found!", nodeType);
            return optCloud.get();
        }

        LOGGER.debug("Creating the dummy cloud for {} Nodes ...", nodeType);
        //else, Byon cloud will be created
        Cloud newCloud = new Cloud();
        newCloud.setCloudType((nodeType.equals("byon")) ? CloudType.BYON : CloudType.EDGE);
        newCloud.setOwner((nodeType.equals("byon")) ? "BYON" : "EDGE");
        newCloud.setId(nodeType);

        //Add the Byon cloud to the database
        repositoryService.saveCloud(newCloud);
        LOGGER.info("Dummy {} cloud created.", nodeType.toUpperCase());
        return newCloud;

        /*
         * TODO :
         * Check if we have to add other variables to the new cloud
         */
    }

    /**
     * @param nsName A valid Node Source name
     * @return The BYON Host Name
     */
    public static String getBYONHostname(String nsName) {
        LOGGER.info("Getting the byon node host name for: " + nsName);
        List<String> nodeSourcesNames = new LinkedList<>();
        List<String> nodeHostNames = new LinkedList<>();
        List<String> nodeStates = new LinkedList<>();
        int retries = 0;
        while (retries < MAX_CONNECTION_RETRIES) {
            retries++;
            //Check if the node source exist
            nodeSourcesNames = resourceManagerGateway.getDeployedNodeSourcesNames();
            if (!nodeSourcesNames.contains(nsName)) {
                LOGGER.warn("The node source " + nsName + " is not deployed");
            } else {
                LOGGER.info("Found the node source");
                //check if the node source have nodes
                nodeHostNames = resourceManagerGateway.getNodeHostNames(nsName);
                if (nodeHostNames == null) {
                    LOGGER.warn("The node Source " + nsName + " Does not have any nodes");
                } else {
                    LOGGER.info("List of node names is returned successfully");
                    //check the number of nodes
                    if (nodeHostNames.size() != 1) {
                        if (nodeHostNames.size() == 0) {
                            LOGGER.warn("The node Source " + nsName + " Does not have any nodes");
                        } else {
                            LOGGER.error("The node Source " + nsName + " has more than one node");
                            throw new IllegalStateException("Node source has multiple nodes");
                        }
                    } else {
                        LOGGER.info("One node name was returned");
                        //check if the host name is not empty
                        if (nodeHostNames.get(0).equals("")) {
                            nodeStates = resourceManagerGateway.getNodeStates(nsName);
                            LOGGER.warn("The node is in " + nodeStates.get(0) +
                                        " state => host name is empty, retrying to get node information");
                        } else {
                            return nodeHostNames.get(0);
                        }
                    }
                }
            }
            try {
                Thread.sleep(INTERVAL);
            } catch (InterruptedException e) {
                LOGGER.error("The sleep thread was interrupted");
            }
        }
        LOGGER.error("The node host name is not retrieved after " + retries + " retries");
        throw new IllegalStateException("Node hostname is empty");

        /*
         * TODO
         * change the get getNodeStates and getNodeHostNames to getNodeEvents
         * to limit the number of connections to the RM
         * nodeStates.get(0) may lead to IndexOutOfRangeException => to be changed
         */
    }

    /**
     * Undeploy or remove the node source of BYON or Edge node
     * @param nodeSourceName of ByonNode or EdgeNode to be undeployed or removed.
     * @param preempt If true undeploy or remove node source immediately without waiting for nodes to be freed
     * @param remove If true completely remove the node source, if false only undeply the node source
     * @return  true if the resourceManagerGateway return no errors, false otherwise
     */
    public static Boolean undeployNs(String nodeSourceName, Boolean preempt, Boolean remove) {
        if (remove) {
            try {
                LOGGER.info("Removing node source " + nodeSourceName + " from the ProActive server");
                if (resourceManagerGateway.getNodeSourceNames("all").contains(nodeSourceName)) {
                    resourceManagerGateway.removeNodeSource(nodeSourceName, preempt);
                } else {
                    LOGGER.warn("The node source \"" + nodeSourceName + "\" does not exist in the RM");
                }
            } catch (NotConnectedException | PermissionRestException e) {
                LOGGER.error(Arrays.toString(e.getStackTrace()));
                return false;
            }
        } else {
            try {
                LOGGER.info("Undeploying node source " + nodeSourceName + " from the ProActive server");
                if (resourceManagerGateway.getNodeSourceNames("deployed").contains(nodeSourceName)) {
                    resourceManagerGateway.undeployNodeSource(nodeSourceName, preempt);
                } else {
                    LOGGER.warn("The node source \"" + nodeSourceName + "\" is not deployed in the RM");
                }
            } catch (NotConnectedException | PermissionRestException e) {
                LOGGER.error(Arrays.toString(e.getStackTrace()));
                return false;
            }
        }
        LOGGER.info("node source was removed with no errors");
        return true;
    }

    public static ByonNode getByonNodeFromNC(NodeCandidate nodeCandidate) {
        List<ByonNode> allByonNodes = repositoryService.listByonNodes();
        for (ByonNode byonNode : allByonNodes) {
            if (byonNode.getNodeCandidate().getId().equals(nodeCandidate.getId())) {
                return byonNode;
            }
        }
        return null;
    }

    public static EdgeNode getEdgeNodeFromNC(NodeCandidate nodeCandidate) {
        List<EdgeNode> allEdgeNodes = repositoryService.listEdgeNodes();
        for (EdgeNode edgeNode : allEdgeNodes) {
            if (edgeNode.getNodeCandidate().getId().equals(nodeCandidate.getId())) {
                return edgeNode;
            }
        }
        return null;
    }
}
