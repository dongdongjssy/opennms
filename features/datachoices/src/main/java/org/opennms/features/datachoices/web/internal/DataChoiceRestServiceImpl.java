/*******************************************************************************
 * This file is part of OpenNMS(R).
 *
 * Copyright (C) 2024 The OpenNMS Group, Inc.
 * OpenNMS(R) is Copyright (C) 1999-2024 The OpenNMS Group, Inc.
 *
 * OpenNMS(R) is a registered trademark of The OpenNMS Group, Inc.
 *
 * OpenNMS(R) is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * OpenNMS(R) is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with OpenNMS(R).  If not, see:
 *      http://www.gnu.org/licenses/
 *
 * For more information contact:
 *     OpenNMS(R) Licensing <license@opennms.org>
 *     http://www.opennms.org/
 *     http://www.opennms.com/
 *******************************************************************************/

package org.opennms.features.datachoices.web.internal;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.Response;

import org.opennms.features.datachoices.internal.StateManager;
import org.opennms.features.datachoices.internal.usagestatistics.UsageStatisticsMetadataDTO;
import org.opennms.features.datachoices.internal.usagestatistics.UsageStatisticsReportDTO;
import org.opennms.features.datachoices.internal.usagestatistics.UsageStatisticsReporter;
import org.opennms.features.datachoices.internal.usagestatistics.UsageStatisticsStatusDTO;
import org.opennms.features.datachoices.internal.userdatacollection.UserDataCollectionFormData;
import org.opennms.features.datachoices.internal.userdatacollection.UserDataCollectionService;
import org.opennms.features.datachoices.internal.userdatacollection.UserDataCollectionStatusDTO;
import org.opennms.features.datachoices.web.DataChoiceRestService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;

/** 
 * Rest-Endpoint mounted at /datachoices. Supported paths are:
 *
 * GET /opennms/rest/datachoices
 * GET /opennms/rest/datachoices/status
 * POST /opennms/rest/datachoices/status
 * GET /opennms/rest/datachoices/meta
 * GET /opennms/rest/datachoices/userdatacollection/status
 * POST /opennms/rest/datachoices/userdatacollection/status
 * POST /opennms/rest/datachoices/userdatacollection/submit
 *
 * These are no longer supported:
 * POST /opennms/rest/datachoices?action=enable
 * POST /opennms/rest/datachoices?action=disable
 *
 * @author jwhite
 * @author mvrueden
 */
public class DataChoiceRestServiceImpl implements DataChoiceRestService {
    private static final Logger LOG = LoggerFactory.getLogger(DataChoiceRestServiceImpl.class);
    private StateManager m_stateManager;
    private UsageStatisticsReporter m_usageStatisticsReporter;

    private UserDataCollectionService userDataCollectionService;

    private static final String METADATA_RESOURCE_PATH = "web/datachoicesMetadata.json";

    @Override
    public UsageStatisticsReportDTO getUsageStatistics() throws ServletException, IOException {
        return m_usageStatisticsReporter.generateReport();
    }

    @Override
    public Response getStatus() throws ServletException, IOException {
        UsageStatisticsStatusDTO dto = new UsageStatisticsStatusDTO();

        try {
            dto.setEnabled(m_stateManager.isEnabled());
            dto.setInitialNoticeAcknowledged(m_stateManager.isInitialNoticeAcknowledged());
        } catch (Exception e) {
            return getExceptionResponse("Error getting Usage Statistics status.", e);
        }

        return Response.ok(dto).build();
    }

    @Override
    public Response setStatus(HttpServletRequest request, UsageStatisticsStatusDTO dto) throws ServletException, IOException {
        try {
            final String remoteUser = request.getRemoteUser();

            if (dto.getEnabled() != null) {
                m_stateManager.setEnabled(dto.getEnabled().booleanValue(), remoteUser);
            }

            if (dto.getInitialNoticeAcknowledged() != null) {
                m_stateManager.setInitialNoticeAcknowledged(dto.getInitialNoticeAcknowledged().booleanValue(), remoteUser);
            }
        } catch (Exception e) {
            return getExceptionResponse("Error setting Usage Statistics status.", e);
        }

        return Response.accepted().build();
    }

    @Override
    public Response getUserDataCollectionStatus() throws ServletException, IOException {
        UserDataCollectionStatusDTO dto = new UserDataCollectionStatusDTO();

        try {
            dto.setNoticeAcknowledged(m_stateManager.isUserDataCollectionNoticeAcknowledged());
            dto.setOptedIn(m_stateManager.isUserDataCollectionOptedIn());
        } catch (Exception e) {
            return getExceptionResponse("Error getting User Data Collection status.", e);
        }

        return Response.ok(dto).build();
    }

    @Override
    public Response setUserDataCollectionStatus(HttpServletRequest request, UserDataCollectionStatusDTO dto) throws ServletException, IOException {
        try {
            final String remoteUser = request.getRemoteUser();

            if (dto.getOptedIn() != null) {
                m_stateManager.setUserDataCollectionOptedIn(dto.getOptedIn());
            }

            if (dto.getNoticeAcknowledged() != null) {
                m_stateManager.setUserDataCollectionNoticeAcknowledged(dto.getNoticeAcknowledged(), remoteUser);
            }
        } catch (Exception e) {
            return getExceptionResponse("Error setting User Data Collection status.", e);
        }

        return Response.accepted().build();
    }

    @Override
    public Response getMetadata() {
        UsageStatisticsMetadataDTO dto = null;

        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream(METADATA_RESOURCE_PATH)) {
            ObjectMapper mapper = new ObjectMapper();
            dto = mapper.readValue(inputStream, UsageStatisticsMetadataDTO.class);
        } catch (Exception e) {
            return getExceptionResponse("Error getting Usage Statistics metadata.", e);
        }

        return Response.ok(dto).build();
    }

    @Override
    public Response submitUserDataCollectionData(HttpServletRequest request, UserDataCollectionFormData data) throws ServletException, IOException {
        String show = System.getProperty("opennms.userDataCollection.show", "true");

        // don't process User Data Collection if disabled by configuration
        if (show != null && show.equalsIgnoreCase("false")) {
            String msg = "User Data Collection has been disabled by the 'opennms.userDataCollection.show' configuration point.";
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(msg).build();
        }

        try {
            userDataCollectionService.submit(data);
        } catch (Exception e) {
            return getExceptionResponse("Error submitting User Data Collection form data.", e);
        }

        return Response.accepted().build();
    }

    public void setStateManager(StateManager stateManager) {
        m_stateManager = stateManager;
    }

    public void setUsageStatisticsReporter(UsageStatisticsReporter usageStatisticsReporter) {
        m_usageStatisticsReporter = Objects.requireNonNull(usageStatisticsReporter);
    }

    public void setUserDataCollectionService(UserDataCollectionService service) {
        this.userDataCollectionService = service;
    }

    private Response getExceptionResponse(String msg, Throwable e) {
        LOG.error(msg, e);
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(msg).build();
    }
}
