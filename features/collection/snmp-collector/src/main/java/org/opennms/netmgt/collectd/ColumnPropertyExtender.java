/*******************************************************************************
 * This file is part of OpenNMS(R).
 *
 * Copyright (C) 2019-2020 The OpenNMS Group, Inc.
 * OpenNMS(R) is Copyright (C) 1999-2020 The OpenNMS Group, Inc.
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

package org.opennms.netmgt.collectd;

import org.apache.commons.lang.StringUtils;
import org.opennms.netmgt.collection.api.AttributeGroup;
import org.opennms.netmgt.collection.api.AttributeGroupType;
import org.opennms.netmgt.collection.api.CollectionAttribute;
import org.opennms.netmgt.collection.api.CollectionResource;
import org.opennms.netmgt.config.datacollection.MibObjProperty;
import org.opennms.netmgt.snmp.SnmpUtils;
import org.opennms.netmgt.snmp.SnmpValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

/**
 * The Class RegExPropertyExtender.
 * 
 * @author <a href="mailto:jmguzman@whitestack.com">José Miguel Guzmán</a> based on the work of
 * <a href="mailto:agalue@opennms.org">Alejandro Galue</a>
 */
public class ColumnPropertyExtender implements SnmpPropertyExtender {

    /** The Constant LOG. */
    private static final Logger LOG = LoggerFactory.getLogger(ColumnPropertyExtender.class);

    /** The Constant SOURCE_TYPE. */
    private static final String SOURCE_TYPE = "source-type";

    /** The Constant SOURCE_ALIAS. */
    private static final String SOURCE_ALIAS = "source-alias";

    /** The Constant INDEX_PATTERN. */
    private static final String INDEX_COLUMN = "index-column";

    @Override
    public SnmpAttribute getTargetAttribute(List<CollectionAttribute> sourceAttributes, SnmpCollectionResource targetResource, MibObjProperty property) {
        final String sourceType = property.getParameterValue(SOURCE_TYPE);
        if (StringUtils.isBlank(sourceType)) {
            LOG.warn("Cannot execute the Column property extender because: missing parameter {}", SOURCE_TYPE);
            return null;
        }

        final String sourceAlias = property.getParameterValue(SOURCE_ALIAS);
        if (StringUtils.isBlank(sourceAlias)) {
            LOG.warn("Cannot execute the Column property extender because: missing parameter {}", SOURCE_ALIAS);
            return null;
        }

        final String indexColumn = property.getParameterValue(INDEX_COLUMN);
        if (StringUtils.isBlank(indexColumn)) {
            LOG.warn("Cannot execute the Column property extender because: missing parameter {}", INDEX_COLUMN);
            return null;
        }

        String targetIndex = null;
        Optional<CollectionAttribute> target = null;

        for(AttributeGroup group:targetResource.getGroups()) {
            for (CollectionAttribute attribute : group.getAttributes()) {
                try {
                    if (indexColumn.equals(attribute.getName())) {
                        targetIndex = attribute.getStringValue();
                    }
                } catch (Exception e) {
                    LOG.error("Error: " + e, e);
                }
            }
        }

        if (targetIndex != null) {
            final String index = targetIndex;
            target = sourceAttributes.stream().filter(a -> matches(sourceType, sourceAlias, index, a)).findFirst();
        }
        if (target != null && target.isPresent()) {
            AttributeGroupType groupType = targetResource.getGroupType(property.getGroupName());
            if (groupType != null) {
                MibPropertyAttributeType type = new MibPropertyAttributeType(targetResource.getResourceType(), property, groupType);
                SnmpValue value = SnmpUtils.getValueFactory().getOctetString(target.get().getStringValue().getBytes());
                return new SnmpAttribute(targetResource, type, value);
            }
        }

        return null;
    }

    /**
     * Matches.
     *
     * @param sourceType the source type
     * @param sourceAlias the source alias
     * @param index the resource index to check
     * @param a the collection attribute to check
     * @return true, if successful
     */
    private boolean matches(final String sourceType, final String sourceAlias, final String index, final CollectionAttribute a) {
        final CollectionResource r = a.getResource();
        return a.getName().equals(sourceAlias) && r.getResourceTypeName().equals(sourceType) && r.getInstance().equals(index);
    }
}
