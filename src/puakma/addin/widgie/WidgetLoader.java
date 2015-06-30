/** ***************************************************************
WidgetLoader.java
Copyright (C) 2001  Brendon Upson 
http://www.wnc.net.au info@wnc.net.au

This program is free software; you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation; either version 2 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program; if not, write to the Free Software
Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA

*************************************************************** */

package puakma.addin.widgie;

import java.util.Hashtable;
/**
 * This class is used to load widgets. If a widget encounters a ClassNotFound exception
 * the current thread is killed. By loading the widget in this thread, only this
 * thread is killed, not the entire WIDGIE task.
 * @author  bupson
 */
public class WidgetLoader extends Thread
{
    private WidgetItem m_wItem;
    private Hashtable m_htWidgets;
    private String m_sServiceName;
    
    public WidgetLoader(WidgetItem wi, Hashtable ht, String sServiceName) 
    {
    	super("bw:"+sServiceName);
        this.setDaemon(false);        
        m_htWidgets = ht;
        m_wItem = wi;
        m_sServiceName = sServiceName;
    }
    
    /**
     * Run the thread
     */
    public void run()
    {
        m_htWidgets.put(m_sServiceName, m_wItem);
        if(!m_wItem.startWidget()) m_htWidgets.remove(m_sServiceName);
    }
}
