//
// This file is part of the OpenNMS(R) Application.
//
// OpenNMS(R) is Copyright (C) 2004 The OpenNMS Group, Inc.  All rights reserved.
// OpenNMS(R) is a derivative work, containing both original code, included code and modified
// code that was published under the GNU General Public License. Copyrights for modified 
// and included code are below.
//
// OpenNMS(R) is a registered trademark of The OpenNMS Group, Inc.
//
// Original code base Copyright (C) 1999-2001 Oculan Corp.  All rights reserved.
//
// This program is free software; you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation; either version 2 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
//
// For more information contact:
// OpenNMS Licensing       <license@opennms.org>
//     http://www.opennms.org/
//     http://www.opennms.com/
//
package org.opennms.netmgt.poller;

import java.util.Date;

import org.opennms.netmgt.xml.event.Event;

/**
 * Represents a PollableElement 
 *
 * @author brozow
 */
abstract public class PollableElement {
    
    private PollableContainer m_parent;
    private PollStatus m_status = PollStatus.STATUS_UNKNOWN;
    private boolean m_statusChanged = false;
    private long m_statusChangeTime = 0L;
    private PollEvent m_cause;


    protected PollableElement(PollableContainer parent) {
        m_parent = parent;
    }

    protected PollableContainer getParent() {
        return m_parent;
    }
    
    public void visit(PollableVisitor v) {
        visitThis(v);
    }
    
    protected void visitThis(PollableVisitor v) {
        v.visitElement(this);
    }
    
    public PollStatus getStatus() {
        return m_status;
    }
    private void setStatus(PollStatus status) {
        m_status = status;
    }
    public boolean isStatusChanged() {
        return m_statusChanged;
    }
    private void setStatusChanged(boolean statusChanged) {
        m_statusChanged = statusChanged;
    }
    public long getStatusChangeTime() {
        return m_statusChangeTime;
    }
    private void setStatusChangeTime(long statusChangeTime) {
        m_statusChangeTime = statusChangeTime;
    }
    public void updateStatus(PollStatus newStatus) {
        if (getStatus() != newStatus) {
            setStatus(newStatus);
            setStatusChanged(true);
            setStatusChangeTime(System.currentTimeMillis());
        }
    }
    public void resetStatusChanged() {
        setStatusChanged(false);
    }
    public void recalculateStatus() {
        // do nothing for just an element
    }
    
    public abstract PollContext getContext();

    /**
     * @param service
     * @return
     */
    public PollStatus doPoll(PollableElement elem) {
        if (getParent() == null)
            return poll(elem);
        else
            return getParent().doPoll(elem);
    }

    /**
     * 
     */
    abstract public PollStatus poll();

    protected PollStatus poll(PollableElement elem) {
        if (elem != this)
            throw new IllegalArgumentException("Invalid parameter to poll on "+this+": "+elem);
        
        return poll();
    }

    /**
     * @return
     */
    public PollableElement selectPollElement() {
        return this;
    }

    /**
     * @param date
     * @return
     */
    public abstract Event createDownEvent(Date date);

    /**
     * @param date
     * @return
     */
    public abstract Event createUpEvent(Date date);

    /**
     * @param cause TODO
     */
    protected void createOutage(PollEvent cause) {
        if (!hasOpenOutage())
            m_cause = cause;
    
        resetStatusChanged();
    }

    /**
     * @param resolution TODO
     * @param e
     */
    protected void resolveOutage(PollEvent resolution) {
        if (hasOpenOutage())
            m_cause = null;
        resetStatusChanged();
    }
    
    protected boolean hasOpenOutage() {
        return m_cause != null;
    }
    
    /**
     * @param date
     * 
     */
    public void processStatusChange(Date date) {
        if (getStatus().isDown() && isStatusChanged()) {
            processGoingDown(date);
        } else if (getStatus().isUp() && isStatusChanged()) {
            processComingUp(date);
        }
    }

    protected void processComingUp(Date date) {
        Event upEvent = getContext().sendEvent(createUpEvent(date));
        PollEvent resolution = new PollEvent(upEvent, date);
        resolveOutage(resolution);
    }

    protected void processGoingDown(Date date) {
        Event downEvent = getContext().sendEvent(createDownEvent(date));
        PollEvent cause = new PollEvent(downEvent, date);
        createOutage(cause);
    }

    /**
     * @param date
     */
    public void processLingeringStatusChanges(PollEvent cause, Date date) {
        if (getStatus().isDown()) {
            Event downEvent = getContext().sendEvent(createDownEvent(date));
            PollEvent newCause = new PollEvent(downEvent, date);
            createOutage(newCause);
        }
    }
    
    public PollEvent getCause() {
        return m_cause;
    }

}
