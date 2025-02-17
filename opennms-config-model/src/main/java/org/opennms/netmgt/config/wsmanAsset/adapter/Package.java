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
package org.opennms.netmgt.config.wsmanAsset.adapter;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import org.opennms.core.xml.ValidateUsing;
import org.opennms.netmgt.config.utils.ConfigUtils;

@XmlRootElement(name = "package")
@XmlAccessorType(XmlAccessType.FIELD)
@ValidateUsing("snmp-asset-adapter-configuration.xsd")
public class Package implements Serializable {
    private static final long serialVersionUID = 2L;

    @XmlAttribute(name = "name", required = true)
    private String m_name;

    /**
     * Stored asset (vendor) from WsManDetector
     */
    @XmlElement(name = "vendor", required = true)
    private String m_vendor;

    @XmlElement(name = "assetField", required = true)
    private List<AssetField> m_assetFields = new ArrayList<>();

    public String getName() {
        return m_name;
    }

    public void setName(final String name) {
        m_name = ConfigUtils.assertNotEmpty(name, "name");
    }

    public String getVendor() {
        return m_vendor;
    }

    public void setVendor(final String vendor) {
        m_vendor = ConfigUtils.assertNotEmpty(vendor, "vendor");
    }

    public List<AssetField> getAssetFields() {
        return m_assetFields;
    }

    public void setAssetFields(final List<AssetField> assetFields) {
        ConfigUtils.assertMinimumSize(assetFields, 1, "assetField");
        if (assetFields == m_assetFields) return;
        m_assetFields.clear();
        if (assetFields != null) m_assetFields.addAll(assetFields);
    }

    public void addAssetField(final AssetField assetField) {
        m_assetFields.add(assetField);
    }

    public boolean removeAssetField(final AssetField assetField) {
        return m_assetFields.remove(assetField);
    }

    @Override
    public int hashCode() {
        return Objects.hash(m_name, 
                            m_vendor,
                            m_assetFields);
    }

    @Override
    public boolean equals(final Object obj) {
        if ( this == obj ) {
            return true;
        }

        if (obj instanceof Package) {
            final Package that = (Package)obj;
            return Objects.equals(this.m_name, that.m_name)
                    && Objects.equals(this.m_vendor, that.m_vendor)
                    && Objects.equals(this.m_assetFields, that.m_assetFields);
        }
        return false;
    }
}
