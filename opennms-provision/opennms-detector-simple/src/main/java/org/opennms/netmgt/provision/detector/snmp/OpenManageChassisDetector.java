/*
 * Licensed to The OpenNMS Group, Inc (TOG) under one or more
 * contributor license agreements.  See the LICENSE.md file
 * distributed with this work for additional information
 * regarding copyright ownership.
 *
 * TOG licenses this file to You under the GNU Affero General
 * Public License Version 3 (the "License") or (at your option)
 * any later version.  You may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at:
 *
 *      https://www.gnu.org/licenses/agpl-3.0.txt
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the
 * License.
 */
package org.opennms.netmgt.provision.detector.snmp;

import java.lang.reflect.UndeclaredThrowableException;
import java.net.InetAddress;

import org.opennms.netmgt.snmp.SnmpAgentConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Component
/**
 * <p>OpenManageChassisDetector class.</p>
 * 
 * @author agalue
 * @version $Id: $
 */
@Scope("prototype")
public class OpenManageChassisDetector extends SnmpDetector {

    private static final Logger LOG = LoggerFactory.getLogger(OpenManageChassisDetector.class);

    /**
     * Name of monitored service.
     */
    private static final String PROTOCOL_NAME = "Dell_OpenManageChassis";

    /**
     * This attribute defines the status of this chassis.
     */
    private static final String CHASSIS_STATUS_OID = ".1.3.6.1.4.1.674.10892.1.200.10.1.4.1";

    /**
     * Implement the chassis status
     */
    private enum DELL_STATUS {
        OTHER(1), UNKNOWN(2), OK(3), NON_CRITICAL(4), CRITICAL(5), NON_RECOVERABLE(6);

        private final int state; // state code

        DELL_STATUS(int s) {
            this.state = s;
        }

        private int value() {
            return this.state;
        }
    };

    /**
     * <p>Constructor for CiscoIpSlaDetector.</p>
     */
    public OpenManageChassisDetector(){
        setServiceName(PROTOCOL_NAME);
    }

    /**
     * {@inheritDoc}
     *
     * Returns true if the protocol defined by this plugin is supported. If
     * the protocol is not supported then a false value is returned to the
     * caller. The qualifier map passed to the method is used by the plugin to
     * return additional information by key-name. These key-value pairs can be
     * added to service events if needed.
     */
    @Override
    public boolean isServiceDetected(final InetAddress address, final SnmpAgentConfig agentConfig) {
        try {
            configureAgentPTR(agentConfig);
            configureAgentVersion(agentConfig);

            // Get the OpenManage chassis status
            String chassisStatus = getValue(agentConfig, CHASSIS_STATUS_OID, isHex());

            // If no chassis status received, do not detect the protocol and quit
            if (chassisStatus == null) {
                LOG.warn("isServiceDetected: Cannot receive chassis status");
                return false;
            } else {
                LOG.debug("isServiceDetected: OpenManageChassis: {}", chassisStatus);
            }

            // Validate chassis status, check status is somewhere between OTHER and NON_RECOVERABLE
            if  (Integer.parseInt(chassisStatus) >= DELL_STATUS.OTHER.value() && 
                    Integer.parseInt(chassisStatus) <= DELL_STATUS.NON_RECOVERABLE.value()) {
                // OpenManage chassis status detected
                LOG.debug("isServiceDetected: OpenManageChassis: is valid, protocol supported.");
                return true;
            }
        } catch (Throwable t) {
            throw new UndeclaredThrowableException(t);
        }
        return false;
    }

}
