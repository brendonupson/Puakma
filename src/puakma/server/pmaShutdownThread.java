/** ***************************************************************
pmaShutdownThread.java
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
/*
 * pmaShutdownThread.java
 *
 * Created on 2 October 2003, 22:17
 * This is the shutdown thread called by the JVM when quitting
 */

package puakma.server;

/**
 *
 * @author  bupson
 */
public class pmaShutdownThread extends Thread 
{
    pmaServer m_Server=null;
    private boolean m_bHasStarted=false;
    
    /** Creates a new instance of pmaShutdownThread */
    public pmaShutdownThread(pmaServer pServer)     
    {
        m_Server=pServer;
    }
    
    public boolean hasStarted()
    {
        return m_bHasStarted;
    }
    
    public void run()
    {
        m_bHasStarted=true;
        int iCount=0;
        //System.out.println("Initiating shutdown....");
        m_Server.shutdown();
        //allow 30 seconds for the server to shutdown - maybe a hung thread?
        while(m_Server.isRunning() && iCount<60) 
        {
            iCount++;
            System.out.print(".");
            try{Thread.sleep(500);}catch(Exception r){}
        }
    }
    
}
