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
package org.opennms.netmgt.syslogd;

import static org.opennms.core.utils.InetAddressUtils.str;

import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import com.google.common.base.Strings;
import com.google.common.cache.Cache;
import org.apache.commons.lang.builder.ToStringBuilder;
import org.opennms.core.utils.InetAddressUtils;
import org.opennms.core.utils.LocationUtils;
import org.opennms.netmgt.config.SyslogdConfig;
import org.opennms.netmgt.config.syslogd.HideMatch;
import org.opennms.netmgt.config.syslogd.HostaddrMatch;
import org.opennms.netmgt.config.syslogd.HostnameMatch;
import org.opennms.netmgt.config.syslogd.ParameterAssignment;
import org.opennms.netmgt.config.syslogd.ProcessMatch;
import org.opennms.netmgt.config.syslogd.UeiMatch;
import org.opennms.netmgt.dao.api.AbstractInterfaceToNodeCache;
import org.opennms.netmgt.dao.api.InterfaceToNodeCache;
import org.opennms.netmgt.model.events.EventBuilder;
import org.opennms.netmgt.provision.LocationAwareDnsLookupClient;
import org.opennms.netmgt.xml.event.Event;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;

/**
 * This routine does the majority of Syslogd's work.
 * Improvements are most likely to be made.
 * 
 * TODO: This class is sloooow. It needs to be sped up significantly
 * to handle increased syslog volume.
 *
 * @author Seth
 * @author <a href="mailto:joed@opennms.org">Johan Edstrom</a>
 * @author <a href="mailto:brozow@opennms.org">Mathew Brozowski</a>
 * @author <a href="mailto:dj@opennms.org">DJ Gregor</a>
 * @author <a href="mailto:mhuot@opennms.org">Mike Huot</a>
 * @author <a href="mailto:jeffg@opennms.org">Jeff Gehlbach</a>
 * @author <a href="mailto:weave@oculan.com">Brian Weaver </a>
 */
public class ConvertToEvent {

    private static final Logger LOG = LoggerFactory.getLogger(ConvertToEvent.class);

    /** Constant <code>HIDDEN_MESSAGE="The message logged has been removed due"{trunked}</code> */
    protected static final String HIDDEN_MESSAGE = "The message logged has been removed due to configuration of Syslogd; it may contain sensitive data.";

    private final Event m_event;

    private final LocationAwareDnsLookupClient m_locationAwareDnsLookupClient;

    private final Cache<HostNameWithLocationKey, String> m_dnsCache;

    private static final LoadingCache<String,Pattern> CACHED_PATTERNS = CacheBuilder.newBuilder().build(
        new CacheLoader<String,Pattern>() {
            public Pattern load(String expression) {
                try {
                    return Pattern.compile(expression, Pattern.MULTILINE);
                } catch(final PatternSyntaxException e) {
                    LOG.warn("Failed to compile regex pattern '{}'", expression, e);
                    return null;
                }
            }
        }
    );

    /**
     * Reduce the limit of the buffer to trim trailing nulls from the value.
     * 
     * @param original ByteBuffer
     * @return A new {@link ByteBuffer} representing the trimmed value.
     */
    public static ByteBuffer trimTrailingNulls(ByteBuffer original) {
        ByteBuffer buffer = original.duplicate();
        // Trim trailing nulls from the string
        while (buffer.limit() > 0 && buffer.get(buffer.limit() - 1) == 0) {
            buffer.limit(buffer.limit() - 1);
        }
        buffer.rewind();
        return buffer;
    }

    public static final EventBuilder toEventBuilder(SyslogMessage message, String systemId, String location) {
        return toEventBuilder(message, systemId, location, null, null, null);
    }

    public static final EventBuilder toEventBuilder(SyslogMessage message, String systemId, String location,
                                                    Date receivedTimestamp,
                                                    LocationAwareDnsLookupClient locationAwareDnsLookupClient,
                                                    Cache<HostNameWithLocationKey, String> dnsCache) {
        if (message == null) {
            return null;
        }

        // Build a basic event out of the syslog message
        final String priorityTxt = message.getSeverity().toString();
        final String facilityTxt = message.getFacility().toString();

        EventBuilder bldr = new EventBuilder();
        bldr.setUei("uei.opennms.org/syslogd/" + facilityTxt + "/" + priorityTxt);
        bldr.setSource("syslogd");

        // Set constant values in EventBuilder

        // Set monitoring system
        bldr.setDistPoller(systemId);
        // Set event host
        bldr.setHost(InetAddressUtils.getLocalHostName());
        // Set default event destination to logndisplay
        bldr.setLogDest("logndisplay");


        // Set values from SyslogMessage in the EventBuilder

        bldr.addParam("hostname", message.getHostName());

        // Add any syslog message parameters as event parameters.
        message.getParameters().forEach((k, v) -> bldr.addParam(k.toString(), v));

        InetAddress hostInetAddress = resolveHostName(locationAwareDnsLookupClient, dnsCache, location, systemId, message);
        if (hostInetAddress != null) {
            // Set nodeId
            InterfaceToNodeCache cache = AbstractInterfaceToNodeCache.getInstance();
            if (cache != null) {
                cache.getFirstNodeId(location, hostInetAddress)
                        .ifPresent(bldr::setNodeid);
            }

            bldr.setInterface(hostInetAddress);
        }

        if (message.getDate() != null) {
            // The message has a date, transfer it to the event
            bldr.setTime(message.getDate());
        } else {
            boolean didSetPartialDate = false;

            if (message.getYear() != null) {
                bldr.setYear(message.getYear());
                didSetPartialDate = true;
            }
            if (message.getMonth() != null) {
                bldr.setMonth(message.getMonth());
                didSetPartialDate = true;
            }
            if (message.getDayOfMonth() != null) {
                bldr.setDayOfMonth(message.getDayOfMonth());
                didSetPartialDate = true;
            }
            if (message.getHourOfDay() != null) {
                bldr.setHourOfDay(message.getHourOfDay());
                didSetPartialDate = true;
            }
            if (message.getMinute() != null) {
                bldr.setMinute(message.getMinute());
                didSetPartialDate = true;
            }
            if (message.getSecond() != null) {
                bldr.setSecond(message.getSecond());
                didSetPartialDate = true;
            }
            if (message.getMillisecond() != null) {
                bldr.setMillisecond(message.getMillisecond());
                didSetPartialDate = true;
            }
            if (message.getZoneId() != null) {
                bldr.setZoneId(message.getZoneId());
                didSetPartialDate = true;
            }

            if (!didSetPartialDate && receivedTimestamp != null) {
                // We did not set *any* date information on the event - use the received timestamp
                bldr.setTime(receivedTimestamp);
            }
        }

        bldr.setLogMessage(message.getMessage());
        // Using parms provides configurability.
        bldr.addParam("syslogmessage", message.getMessage());

        bldr.addParam("severity", priorityTxt);

        bldr.addParam("timestamp", SyslogMessage.getRfc3164FormattedDate(bldr.currentEventTime()));

        if (message.getMessageID() != null) {
            bldr.addParam("messageid", message.getMessageID());
        }

        if (message.getProcessName() != null) {
            bldr.addParam("process", message.getProcessName());
        }

        bldr.addParam("service", facilityTxt);

        if (message.getProcessId() != null) {
            bldr.addParam("processid", message.getProcessId().toString());
        }

        return bldr;
    }

    private static InetAddress resolveHostName(LocationAwareDnsLookupClient locationAwareDnsLookupClient,
                                               Cache<HostNameWithLocationKey, String> dnsCache,
                                               String location, String systemId, SyslogMessage message) {

        if (LocationUtils.isDefaultLocationName(location)) {
            return message.getHostAddress();
        }
        String hostName = message.getHostName();
        if (Strings.isNullOrEmpty(hostName) ||
                locationAwareDnsLookupClient == null || dnsCache == null) {
            return null;
        }
        HostNameWithLocationKey cacheKey = new HostNameWithLocationKey(hostName, location);
        // Try to find the element in the cache first.
        String hostIpAddress = dnsCache.getIfPresent(cacheKey);
        // If it's not present in the cache, try lookup and add result to cache.
        if (hostIpAddress == null) {
            CompletableFuture<String> future = locationAwareDnsLookupClient.lookup(hostName, location, systemId);
            try {
                hostIpAddress = future.get();
                if (hostIpAddress != null) {
                    dnsCache.put(cacheKey, hostIpAddress);
                }
            } catch (InterruptedException | ExecutionException e) {
                LOG.warn("Exception while resolving hostname {} at location {}", hostName, location);
            }
        }
        InetAddress hostInetAddress = null;
        if (hostIpAddress != null) {
            hostInetAddress = InetAddressUtils.addr(hostIpAddress);
        }
        return hostInetAddress;
    }

    /**
     * Constructs a new event encapsulation instance based upon the
     * information passed to the method. The passed byte array is decoded into
     * a string using the {@link StandardCharsets#US_ASCII} character encoding.
     *
     * @param systemId
     * @param location
     * @param addr     The remote agent's address.
     * @param port     The remote agent's port
     * @param incoming The syslog datagram in {@link StandardCharsets#US_ASCII} encoding.
     * @param config   The Syslogd configuration
     * @throws MessageDiscardedException
     */
    public ConvertToEvent(
            final String systemId,
            final String location,
            final InetAddress addr,
            final int port,
            final ByteBuffer incoming,
            final SyslogdConfig config,
            final LocationAwareDnsLookupClient locationAwareDnsLookupClient
    ) throws MessageDiscardedException {
        this(systemId, location, addr, port, incoming, null, config, locationAwareDnsLookupClient, null);
    }

    /**
     * Constructs a new event encapsulation instance based upon the
     * information passed to the method. The passed byte array is decoded into
     * a string using the {@link StandardCharsets#US_ASCII} character encoding.
     *
     * @param systemId
     * @param location
     * @param addr The remote agent's address.
     * @param port The remote agent's port
     * @param incoming The syslog datagram in {@link StandardCharsets#US_ASCII} encoding.
     * @param receivedTimestamp the time the message was received
     * @param config The Syslogd configuration
     * @param locationAwareDnsLookupClient Location Aware DNS Lookup Client
     * @param dnsCache
     * @throws MessageDiscardedException
     */
    public ConvertToEvent(
            final String systemId,
            final String location,
            final InetAddress addr,
            final int port,
            final ByteBuffer incoming,
            final Date receivedTimestamp,
            final SyslogdConfig config,
            LocationAwareDnsLookupClient locationAwareDnsLookupClient, Cache<HostNameWithLocationKey, String> dnsCache) throws MessageDiscardedException {

        this.m_locationAwareDnsLookupClient = locationAwareDnsLookupClient;
        this.m_dnsCache = dnsCache;

        if (config == null) {
            throw new IllegalArgumentException("Config cannot be null");
        }

        ByteBuffer buffer = trimTrailingNulls(incoming);

        if (LOG.isTraceEnabled()) {
            LOG.trace("Converting to event: {}", this);
        }

        SyslogParser parser = SyslogParser.getParserInstance(config, buffer);
        if (!parser.find()) {
            throw new MessageDiscardedException(String.format("Message does not match regex: '%s'", SyslogParser.fromByteBuffer(buffer).toString()));
        }
        SyslogMessage message;
        try {
            message = parser.parse();
        } catch (final SyslogParserException ex) {
            LOG.debug("Unable to parse '{}'", SyslogParser.fromByteBuffer(buffer), ex);
            throw new MessageDiscardedException(ex);
        }

        if (message == null) {
            throw new MessageDiscardedException(String.format("Unable to parse message: '%s'", SyslogParser.fromByteBuffer(buffer).toString()));
        }

        if (LOG.isTraceEnabled()) {
            LOG.trace("got syslog message {}", SyslogParser.fromByteBuffer(buffer));
        }

        if (config.shouldIncludeRawSyslogmessage()) {
            // Set the raw syslog message as a parm
            message.addParameter("rawSyslogmessage", SyslogParser.fromByteBuffer(buffer));
        }

        // If no host name was provided we will use the source IP address
        if(message.getHostName() == null)
        {
            message.setHostName(addr.getHostAddress());
        }

        final String priorityTxt = message.getSeverity().toString();
        final String facilityTxt = message.getFacility().toString();

        // Post-process the message based on the SyslogdConfig

        // We will also here find out if, the host needs to
        // be replaced, the message matched to a UEI, and
        // last if we need to actually hide the message.
        // this being potentially helpful in avoiding showing
        // operator a password or other data that should be
        // confidential.

        /*
        * We matched on a regexp for host/message pair.
        * This can be a forwarded message as in BSD Style
        * or syslog-ng.
        * We assume that the host is given to us
        * as an IP/Hostname and that the resolver
        * on the ONMS host actually can resolve the
        * node to match against nodeId.
         */

        // Time to verify UEI matching.

        EventBuilder bldr = toEventBuilder(message, systemId, location, receivedTimestamp, m_locationAwareDnsLookupClient, dnsCache);

        final List<UeiMatch> ueiMatch = (config.getUeiList() == null ? Collections.emptyList() : config.getUeiList());
        for (final UeiMatch uei : ueiMatch) {
            final boolean messageMatchesUeiListEntry = containsIgnoreCase(uei.getFacilities(), facilityTxt) &&
                                              containsIgnoreCase(uei.getSeverities(), priorityTxt) &&
                                              matchProcess(uei.getProcessMatch().orElse(null), message.getProcessName()) &&
                                              matchHostname(uei.getHostnameMatch().orElse(null), message.getHostName()) &&
                                              matchHostAddr(uei.getHostaddrMatch().orElse(null), str(message.getHostAddress()));

            if (messageMatchesUeiListEntry) {
                if (uei.getMatch().getType().equals("substr")) {
                    if (matchSubstring(message.getMessage(), uei, bldr, config.getDiscardUei())) {
                        break;
                    }
                } else if ((uei.getMatch().getType().startsWith("regex"))) {
                    if (matchRegex(message.getMessage(), uei, bldr, config.getDiscardUei())) {
                        break;
                    }
                }
            }
        }

        // Time to verify if we need to hide the message
        final List<HideMatch> hideMatch = (config.getHideMessages() == null ? Collections.emptyList() : config.getHideMessages());
        boolean doHide = false;
        if (hideMatch.size() > 0) {
            // Match this regex against the full string of the message
            final String fullText = message.asRfc3164Message();

            for (final HideMatch hide : hideMatch) {
                if (hide.getMatch().getType().equals("substr")) {
                    if (fullText.contains(hide.getMatch().getExpression())) {
                        // We should hide the message based on this match
                        doHide = true;
                        break;
                    }
                } else if (hide.getMatch().getType().equals("regex")) {
                    try {
                        Pattern msgPat = getPattern(hide.getMatch().getExpression());
                        Matcher msgMat = msgPat.matcher(fullText);
                        if (msgMat.find()) {
                            // We should hide the message based on this match
                            doHide = true;
                            break;
                        }
                    } catch (PatternSyntaxException pse) {
                        LOG.warn("Failed to compile hide-match regex pattern '{}'", hide.getMatch().getExpression(), pse);
                    }
                }
            }
        }

        if (doHide) {
            LOG.debug("Hiding syslog message from Event - May contain sensitive data");
            bldr.setLogMessage(HIDDEN_MESSAGE);
            bldr.setParam("syslogmessage", HIDDEN_MESSAGE);
        }

        m_event = bldr.getEvent();
    }

    private static boolean matchFind(final String expression, final String input, final String context) {
        if (input == null) {
            return false;
        }
        final Pattern pat = getPattern(expression);
        if (pat == null) {
            LOG.debug("Unable to get pattern for expression '{}' in {} context", expression, context);
            return false;
        }
        final Matcher mat = pat.matcher(input);
        if (mat != null && mat.find()) {
            LOG.trace("Successful regex {} for input '{}' against expression '{}'", context, input, expression);
            return true;
        } else {
           return false;
        }
    }

    private static boolean matchHostAddr(final HostaddrMatch hostaddrMatch, final String hostAddress) {
        if (hostaddrMatch == null) return true;
        return matchFind(hostaddrMatch.getExpression(), hostAddress, "hostaddr-match");
    }

    private static boolean matchHostname(final HostnameMatch hostnameMatch, final String hostName) {
        if (hostnameMatch == null) return true;
        return matchFind(hostnameMatch.getExpression(), hostName, "hostname-match");
    }

    private static boolean matchProcess(final ProcessMatch processMatch, final String processName) {
        if (processMatch == null) return true;
        return matchFind(processMatch.getExpression(), processName, "process-match");
    }

    private static boolean containsIgnoreCase(List<String> collection, String match) {
         if (collection.size() == 0) return true;
         for (String string : collection) {
             if (string.equalsIgnoreCase(match)) return true;
         }
         return false;
    }

    private static Pattern getPattern(final String expression) {
        return CACHED_PATTERNS.getUnchecked(expression);
    }

    /**
     * Checks the message for substring matches to a {@link UeiMatch}. If the message
     * matches, then the UEI is updated (or the event is discarded if the discard
     * UEI is used). Parameter assignments are NOT performed for substring matches.
     * 
     * @param message
     * @param uei
     * @param bldr
     * @param discardUei
     * @return
     * @throws MessageDiscardedException
     */
    private static boolean matchSubstring(String message, final UeiMatch uei, final EventBuilder bldr, final String discardUei) throws MessageDiscardedException {
        final boolean traceEnabled = LOG.isTraceEnabled();
        if (message.contains(uei.getMatch().getExpression())) {
            if (discardUei.equals(uei.getUei())) {
                if (traceEnabled) LOG.trace("Specified UEI '{}' is same as discard-uei, discarding this message.", uei.getUei());
                throw new MessageDiscardedException();
            } else {
                // Update the UEI to the new value
                if (traceEnabled) LOG.trace("Changed the UEI of a Syslogd event, based on substring match, to : {}", uei.getUei());
                bldr.setUei(uei.getUei());
                return true;
            }
        } else {
            if (traceEnabled) LOG.trace("No substring match for text of a Syslogd event to : {}", uei.getMatch().getExpression());
            return false;
        }
    }

    /**
     * Checks the message for matches to a {@link UeiMatch}. If the message
     * matches, then the UEI is updated (or the event is discarded if the discard
     * UEI is used) and parameters are added to the event.
     * 
     * @param message
     * @param uei
     * @param bldr
     * @param discardUei
     * @return
     * @throws MessageDiscardedException
     */
    private static boolean matchRegex(final String message, final UeiMatch uei, final EventBuilder bldr, final String discardUei) throws MessageDiscardedException {
        final boolean traceEnabled = LOG.isTraceEnabled();
        final String expression = uei.getMatch().getExpression();
        final Pattern msgPat = getPattern(expression);
        if (msgPat == null) {
            LOG.debug("Unable to create pattern for expression '{}'", expression);
            return false;
        } 

        final Matcher msgMat = msgPat.matcher(message);

        // If the message matches the regex
        if ((msgMat != null) && (msgMat.find())) {
            // Discard the message if the UEI is set to the discard UEI
            if (discardUei.equals(uei.getUei())) {
                if (traceEnabled) LOG.trace("Specified UEI '{}' is same as discard-uei, discarding this message.", uei.getUei());
                throw new MessageDiscardedException();
            } else {
                // Update the UEI to the new value
                if (traceEnabled) LOG.trace("Changed the UEI of a Syslogd event, based on regex match, to : {}", uei.getUei());
                bldr.setUei(uei.getUei());
            }

            if (msgMat.groupCount() > 0) {
                // Perform default parameter mapping
                if (uei.getMatch().getDefaultParameterMapping()) {
                    if (traceEnabled) LOG.trace("Doing default parameter mappings for this regex match.");
                    for (int groupNum = 1; groupNum <= msgMat.groupCount(); groupNum++) {
                        if (traceEnabled) LOG.trace("Added parm 'group{}' with value '{}' to Syslogd event based on regex match group", groupNum, msgMat.group(groupNum));
                        bldr.addParam("group"+groupNum, msgMat.group(groupNum));
                    }
                }

                // If there are specific parameter mappings as well, perform those mappings
                if (uei.getParameterAssignments().size() > 0) {
                    if (traceEnabled) LOG.trace("Doing user-specified parameter assignments for this regex match.");
                    for (ParameterAssignment assignment : uei.getParameterAssignments()) {
                        String parmName = assignment.getParameterName();
                        String parmValue = msgMat.group(assignment.getMatchingGroup());
                        parmValue = parmValue == null ? "" : parmValue;
                        bldr.addParam(parmName, parmValue);
                        if (traceEnabled) {
                            LOG.trace("Added parm '{}' with value '{}' to Syslogd event based on user-specified parameter assignment", parmName, parmValue);
                        }
                    }
                }
            }

            return true;
        }

        if (traceEnabled) LOG.trace("Message portion '{}' did not regex-match pattern '{}'", message, expression);
        return false;
    }

    /**
     * <p>getEvent</p>
     *
     * @return a {@link org.opennms.netmgt.xml.event.Event} object.
     */
    public Event getEvent() {
        return m_event;
    }

    /**
     * <p>toString</p>
     *
     * @return a {@link java.lang.String} object.
     */
    @Override
    public String toString() {
        return new ToStringBuilder(this)
            .append("Event", m_event)
            .toString();
    }
}
