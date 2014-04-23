/*******************************************************************************
 * This file is part of OpenNMS(R).
 *
 * Copyright (C) 2006-2012 The OpenNMS Group, Inc.
 * OpenNMS(R) is Copyright (C) 1999-2012 The OpenNMS Group, Inc.
 *
 * OpenNMS(R) is a registered trademark of The OpenNMS Group, Inc.
 *
 * OpenNMS(R) is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published
 * by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * OpenNMS(R) is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with OpenNMS(R).  If not, see:
 *      http://www.gnu.org/licenses/
 *
 * For more information contact:
 *     OpenNMS(R) Licensing <license@opennms.org>
 *     http://www.opennms.org/
 *     http://www.opennms.com/
 *******************************************************************************/

package org.opennms.netmgt.rrd;

public class RrdDataSource {
    private final String m_name;
    private final String m_type;
    private final int m_heartBeat;
    private final String m_min;
    private final String m_max;
    
    /**
     * <p>Constructor for RrdDataSource.</p>
     *
     * @param name a {@link java.lang.String} object.
     * @param type a {@link java.lang.String} object.
     * @param heartBeat a int.
     * @param min a {@link java.lang.String} object.
     * @param max a {@link java.lang.String} object.
     */
    public RrdDataSource(String name, String type, int heartBeat, String min, String max) {
        m_name = name;
        m_type = type;
        m_heartBeat = heartBeat;
        m_min = min;
        m_max = max;
    }

    /**
     * <p>getHeartBeat</p>
     *
     * @return a int.
     */
    public int getHeartBeat() {
        return m_heartBeat;
    }

    /**
     * <p>getMax</p>
     *
     * @return a {@link java.lang.String} object.
     */
    public String getMax() {
        return m_max;
    }

    /**
     * <p>getMin</p>
     *
     * @return a {@link java.lang.String} object.
     */
    public String getMin() {
        return m_min;
    }

    /**
     * <p>getName</p>
     *
     * @return a {@link java.lang.String} object.
     */
    public String getName() {
        return m_name;
    }

    /**
     * <p>getType</p>
     *
     * @return a {@link java.lang.String} object.
     */
    public String getType() {
        return m_type;
    }

}
