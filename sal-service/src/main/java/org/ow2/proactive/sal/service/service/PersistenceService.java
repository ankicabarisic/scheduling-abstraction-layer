/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package org.ow2.proactive.sal.service.service;

import java.util.*;
import java.util.stream.Collectors;

import org.ow2.proactive.sal.model.*;
import org.ow2.proactive.scheduler.common.exception.NotConnectedException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import lombok.extern.log4j.Log4j2;


@Log4j2
@Service("PersistenceService")
public class PersistenceService {
    @Autowired
    private PAGatewayService paGatewayService;

    @Autowired
    private CloudService cloudService;

    @Autowired
    private RepositoryService repositoryService;

    @Autowired
    private ClusterService clusterService;

    @Autowired
    private EdgeService edgeService;

    /**
     * Clean all clusters, clouds, edge devices, and database entries.
     * @param sessionId A valid session id
     */

    public boolean cleanAll(String sessionId) throws NotConnectedException {
        LOGGER.info("Received cleanAll endpoint call with sessionId: {}", sessionId);
        // Check if the connection is active
        if (!paGatewayService.isConnectionActive(sessionId)) {
            LOGGER.warn("Session {} is not active. Aborting cleanAll operation.", sessionId);
            throw new NotConnectedException();
        }

        // Cleaning clusters
        LOGGER.info("CLEAN-ALL: Initiating cluster cleanup...");
        boolean clustersCleaned = cleanAllClustersFunction(sessionId);
        if (clustersCleaned) {
            LOGGER.info("CLEAN-ALL: Successfully cleaned all clusters.");
        } else {
            LOGGER.warn("CLEAN-ALL: Cluster cleanup encountered issues.");
        }

        // Cleaning clouds
        LOGGER.info("CLEAN-ALL: Initiating cloud cleanup...");
        boolean cloudsCleaned = cleanAllCloudsFunction(sessionId);
        if (cloudsCleaned) {
            LOGGER.info("CLEAN-ALL: Successfully cleaned all clouds.");
        } else {
            LOGGER.warn("CLEAN-ALL: Cloud cleanup encountered issues.");
        }

        // Cleaning edge devices
        LOGGER.info("CLEAN-ALL: Initiating edge devices cleanup...");
        boolean edgesCleaned = cleanAllEdgeFunction(sessionId);
        if (edgesCleaned) {
            LOGGER.info("CLEAN-ALL: Successfully cleaned all edge devices.");
        } else {
            LOGGER.warn("CLEAN-ALL: Edge device cleanup encountered issues.");
        }

        boolean databaseCleaned = false;

        if (clustersCleaned && edgesCleaned && cloudsCleaned) {
            //Clean all database entries
            LOGGER.info("CLEAN-ALL: Initiating database cleanup...");
            databaseCleaned = repositoryService.cleanAll(sessionId);
            if (databaseCleaned) {
                LOGGER.info("CLEAN-ALL: Successfully cleaned database.");
            } else {
                LOGGER.warn("CLEAN-ALL: Database cleanup encountered issues.");
            }
        }

        LOGGER.info("CLEAN-ALL: Completed all cleanup processes for sessionId: {}", sessionId);
        return clustersCleaned && edgesCleaned && cloudsCleaned && databaseCleaned;

    }

    /**
     * Cleans all clouds by undeploying cloud nodes and removing cloud entries.
     * @param sessionId A valid session id
     * @return true if all clouds were cleaned successfully, false otherwise
     */
    public boolean cleanAllClouds(String sessionId) throws NotConnectedException {
        LOGGER.info("Received cleanAllClouds endpoint call with sessionId: {}", sessionId);

        // Check if the connection is active
        if (!paGatewayService.isConnectionActive(sessionId)) {
            LOGGER.warn("Session {} is not active. Aborting cloud cleanup.", sessionId);
            throw new NotConnectedException();
        }

        // Perform actual cleanup
        return cleanAllCloudsFunction(sessionId);
    }

    /**
     * Cleans all clouds by undeploying cloud nodes and removing cloud entries.
     * @param sessionId A valid session id
     * @return true if all clouds were cleaned successfully, false otherwise
     */
    public boolean cleanAllClusters(String sessionId) throws NotConnectedException {
        LOGGER.info("Received cleanAllClusters endpoint call with sessionId: {}", sessionId);

        // Check if the connection is active
        if (!paGatewayService.isConnectionActive(sessionId)) {
            LOGGER.warn("Session {} is not active. Aborting cloud cleanup.", sessionId);
            throw new NotConnectedException();
        }

        // Perform actual cleanup
        return cleanAllClustersFunction(sessionId);
    }

    /**
     * Deregister all edge devices by removing their entries.
     * @param sessionId A valid session id
     * @return true if all edge devices were deregistered successfully, false otherwise
     */
    public boolean cleanAllEdges(String sessionId) throws NotConnectedException {
        LOGGER.info("Received cleanAllEdges endpoint call with sessionId: {}", sessionId);

        // Check if the connection is active
        if (!paGatewayService.isConnectionActive(sessionId)) {
            LOGGER.warn("Session {} is not active. Aborting edge device cleanup.", sessionId);
            throw new NotConnectedException();
        }

        // Perform the actual edge device cleanup
        return cleanAllEdgeFunction(sessionId);
    }

    /**
     * Deregister all edge devices by removing their entries.
     * @param sessionId A valid session id
     * @return true if all edge devices were deregistered successfully, false otherwise
     */
    public boolean cleanAllSALDatabase(String sessionId) throws NotConnectedException {
        LOGGER.info("Received cleanAllSALDatabase request for sessionId: {}", sessionId);

        // Check if the session is active before proceeding
        if (!paGatewayService.isConnectionActive(sessionId)) {
            LOGGER.warn("Session {} is not active. Aborting SAL database cleanup.", sessionId);
            throw new NotConnectedException();
        }

        // Delegate to repositoryService to clean the database entries
        return repositoryService.cleanAll(sessionId);
    }

    /**
     * Helper function to perform cloud cleanup and return the result.
     * @param sessionId A valid session id
     * @return true if all clouds were cleaned successfully, false otherwise
     */
    public Boolean cleanAllCloudsFunction(String sessionId) {
        LOGGER.info("Starting cloud cleanup function for sessionId: {}", sessionId);

        try {
            if (cloudService.isAnyAsyncNodeCandidatesProcessesInProgress(sessionId)) {
                LOGGER.warn("Asynchronous node candidate retrieval is in progress. Cloud cleanup is deferred.");
                return false;
            }

            // Retrieve all clouds
            List<PACloud> allClouds = repositoryService.listPACloud();
            if (allClouds.isEmpty()) {
                LOGGER.warn("No clouds found to clean for sessionId: {}", sessionId);
                return true;
            }

            // Collect all cloud IDs
            final List<String> cloudIds = allClouds.stream().map(PACloud::getCloudId).collect(Collectors.toList());
            LOGGER.info("Found {} clouds to clean for sessionId: {}", cloudIds.size(), sessionId);

            // Perform cloud removal
            boolean removeCloudsResult = cloudService.removeClouds(sessionId, cloudIds, true);
            if (removeCloudsResult) {
                LOGGER.info("Successfully removed all clouds for sessionId: {}", sessionId);
            } else {
                LOGGER.error("Failed to remove one or more clouds for sessionId: {}", sessionId);
                throw new RuntimeException("Cloud removal failed");
            }

            return removeCloudsResult;

        } catch (Exception e) {
            // Log any errors with a message and stack trace
            LOGGER.error("Unexpected error during cloud cleanup for sessionId: {}. Details: ", sessionId, e);
            throw new RuntimeException("Unexpected error during cloud cleanup", e);
        }
    }

    /**
     * Helper function to perform cluster cleanup and return the result.
     * @param sessionId A valid session id
     * @return true if all clusters were undeployed successfully, false otherwise
     */
    public Boolean cleanAllClustersFunction(String sessionId) {
        LOGGER.info("Starting cluster cleanup function for sessionId: {}", sessionId);

        try {
            // Retrieve all clusters
            List<Cluster> allClusters = repositoryService.listCluster();

            if (allClusters.isEmpty()) {
                LOGGER.warn("No clusters found to clean for sessionId: {}", sessionId);
                return true;
            }

            boolean overallSuccess = true; // Track overall success status

            for (Cluster cluster : allClusters) {
                String clusterName = cluster.getName();

                // Try deleting the cluster and update overall status
                boolean deleteResult = clusterService.deleteCluster(sessionId, clusterName);
                if (!deleteResult) {
                    LOGGER.error("Failed to delete cluster: {} for sessionId: {}", clusterName, sessionId);
                    overallSuccess = false; // Mark as false if any deletion fails
                } else {
                    LOGGER.info("Successfully deleted cluster: {} for sessionId: {}", clusterName, sessionId);
                }
            }

            if (overallSuccess) {
                LOGGER.info("Successfully removed all clusters for sessionId: {}", sessionId);
            } else {
                LOGGER.warn("Completed cluster cleanup with some failures for sessionId: {}", sessionId);
            }

            return overallSuccess;

        } catch (Exception e) {
            // Log any errors with a message and stack trace
            LOGGER.error("Unexpected error during cluster cleanup for sessionId: {}. Error details:", sessionId, e);
            return false;
        }
    }

    /**
     * Helper function to perform edge devices deregistration and return the result.
     * @param sessionId A valid session id
     * @return true if all edge devices were unregistered successfully, false otherwise
     */
    public Boolean cleanAllEdgeFunction(String sessionId) {
        LOGGER.info("Initiating edge device cleanup for sessionId: {}", sessionId);

        try {
            // Retrieve all edge devices
            List<EdgeNode> allEdgeDevices = repositoryService.listEdgeNodes();

            if (allEdgeDevices.isEmpty()) {
                LOGGER.warn("No edge devices found for sessionId: {}. Nothing to deregister.", sessionId);
                return true;
            }

            boolean overallSuccess = true; // Track overall success status

            LOGGER.info("Attempting to deregister {} edge devices for sessionId: {}", allEdgeDevices.size(), sessionId);

            for (EdgeNode edge : allEdgeDevices) {
                String edgeID = edge.getId();

                // Try deregistrating edge device and update overall status
                boolean deleteResult = edgeService.deleteEdgeNode(sessionId, edgeID);
                if (!deleteResult) {
                    LOGGER.error("Failed to delete edge device with ID: {} for sessionId: {}", edgeID, sessionId);
                    overallSuccess = false; // Mark as false if any deletion fails
                } else {
                    LOGGER.info("Successfully deleted  edge device with ID: {} for sessionId: {}", edgeID, sessionId);
                }
            }

            if (overallSuccess) {
                LOGGER.info("All edge devices successfully deregistered for sessionId: {}", sessionId);
            } else {
                LOGGER.warn("Completed edge cleanup with some failures for sessionId: {}", sessionId);
            }

            return overallSuccess;

        } catch (Exception e) {
            // Log any errors with a message and stack trace
            LOGGER.error("Unexpected error during edge cleanup for sessionId: {}. Error details:", sessionId, e);
            return false;
        }
    }

}
