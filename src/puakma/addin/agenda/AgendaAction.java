/** ***************************************************************
AgendaAction.java
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
package puakma.addin.agenda;


import java.text.SimpleDateFormat;
import java.util.ArrayList;

import puakma.addin.pmaAddInStatusLine;
import puakma.addin.http.action.HTTPSessionContext;
import puakma.addin.http.action.SharedActionClassLoader;
import puakma.addin.http.document.DesignElement;
import puakma.addin.http.document.HTMLDocument;
import puakma.error.ErrorDetect;
import puakma.system.ActionRunnerInterface;
import puakma.system.RequestPath;
import puakma.system.SessionContext;
import puakma.system.SystemContext;
import puakma.util.Util;


/**
 * <p>Title: AgendaAction</p>
 * <p>Description: This is the runnable component of the AGENDA</p>
 * <p>Copyright: Copyright (c) 2002</p>
 * <p>Company: </p>
 * @author Brendon Upson
 * @version 1.0
 */

public class AgendaAction extends Thread implements ErrorDetect
{
	private boolean bRunning=false;
	private int m_iDesignID=-1;
	private RequestPath m_rPath;
	private SystemContext m_pSystem;
	//private SessionContext m_pSession;
	private AGENDA m_Parent;
	private pmaAddInStatusLine m_pStatus;
	private String m_sOptions="";
	private java.util.Date m_dtLastRun;
	private SimpleDateFormat m_sdf = new SimpleDateFormat("EEE dd.MMM.yy HH:mm:ss");
	private ActionRunnerInterface m_act=null;
	private java.util.Date m_dtStart; 
	private int m_iInterruptCount=0;
	private static final int MAX_INTERRUPT_THRESHOLD=20;//interrupt max 20 times


	public AgendaAction(AGENDA pAgenda, SystemContext paramSystem, String sPath, String sOptions, int iDesignID, java.util.Date dtLastRun)
	{
		super("agenda:"+sPath);
		m_Parent = pAgenda;
		m_pSystem = paramSystem;
		m_sOptions = new String(sOptions);
		m_rPath = new RequestPath(sPath);
		m_iDesignID=iDesignID;
		m_dtLastRun = dtLastRun;
	}

	/**
	 * get the number of seconds this action has been running. 
	 * @return -1 if it is not running or has not started.
	 */
	public long getRunningTimeSeconds()
	{
		long lNow = System.currentTimeMillis();
		if(!bRunning || m_dtStart==null) return -1;

		long lStart = m_dtStart.getTime();
		long lDiff = (lNow - lStart)/1000;
		return lDiff;      
	}

	public void interrupt()
	{
		m_iInterruptCount++;
		super.interrupt();      
	}


	/**
	 * Returns true if this thread has run too long and is not responding to interrupt requests.
	 */
	public boolean hasExceededInterruptLimit()
	{
		if(m_iInterruptCount>MAX_INTERRUPT_THRESHOLD) return true;
		return false;
	}

	/**
	 * This method is called by the pmaServer object
	 */
	public void run()
	{
		SessionContext sessCtx=null;
		HTMLDocument doc;

		//ActionReturn act_return = null;
		bRunning=true;
		m_dtStart = new java.util.Date();

		m_pStatus = m_Parent.createStatusLine(" Action");
		m_pStatus.setStatus("Running: " + m_rPath.getFullPath());

		//System.out.println("Running: " + m_rPath.getPathToDesign() + " " + new java.util.Date());
		if(m_Parent.showStartFinishTimes())
			m_pSystem.doInformation("AGENDA.ActionStart", new String[]{m_rPath.getFullPath()}, this);

		try
		{      
			SystemContext sysCtx = (SystemContext)m_pSystem.clone(); //so the programmer doesn't destroy our system object!
			sessCtx = m_pSystem.createSystemSession("AgendaAction"); //ditto with the session
			//create the doc and set any vars required...

			doc = new HTMLDocument(new HTTPSessionContext(m_pSystem, sessCtx, m_rPath));
			doc.setParameters(m_rPath.Parameters);
			doc.rPath = m_rPath;
			doc.replaceItem("$ActionLastRun", m_dtLastRun);

			HTTPSessionContext pSession = new HTTPSessionContext(sysCtx, sessCtx, m_rPath);
			SharedActionClassLoader aLoader = m_pSystem.getActionClassLoader(m_rPath); //, DesignElement.DESIGN_TYPE_SCHEDULEDACTION);			
			Class runclass = aLoader.getActionClass(m_rPath.DesignElementName, DesignElement.DESIGN_TYPE_SCHEDULEDACTION);
			if(runclass!=null)
			{
				Object object = runclass.newInstance();
				m_act = (ActionRunnerInterface)object;
				
				m_act.init(pSession, doc, m_rPath.Group, m_rPath.Application);
				Thread.currentThread().setContextClassLoader(aLoader);
				m_act.execute();
				if(m_Parent.isDebug()) sysCtx.checkConnections(m_rPath.getFullPath(), sessCtx);
			}
		}
		catch(java.lang.OutOfMemoryError ome)
		{
			//request a gc then give the JVM some time to garbage collect
			//System.gc();
			try{Thread.sleep(5000);} catch(Exception e){}
			m_pSystem.doError("AGENDA.ActionExecuteError", new String[]{m_rPath.DesignElementName, ome.toString()}, this);
			ome.printStackTrace();
		}
		catch(Throwable t)
		{
			m_pSystem.doError("AGENDA.ActionExecuteError", new String[]{m_rPath.DesignElementName, t.toString()}, this);
			Util.logStackTrace(t, m_pSystem, -1);
		}

		//System.out.println("Finished: " + m_rPath.getPathToDesign());
		if(m_Parent.showStartFinishTimes())
			m_pSystem.doInformation("AGENDA.ActionFinish", new String[]{m_rPath.getPathToDesign()}, this);

		bRunning=false;
		//removed as sessions may run in widgets etc in the background. Let the session cleaner sort it
		//m_pSystem.dropSessionID(sessCtx.getSessionID());
		m_Parent.removeStatusLine(m_pStatus);
		m_Parent.removeRunner(this);

	}

	/**
	 * ask this action to quit
	 */
	public void requestQuit()
	{
		if(m_act!=null) m_act.requestQuit();
	}

	/**
	 * 
	 * @param bSetRunTime
	 */
	public void updateOptionString(boolean bSetRunTime)
	{
		if(m_sOptions==null) m_sOptions="";
		String sOptionsLower = m_sOptions.toLowerCase();
		//System.out.println("opt=["+szOpt+"]");
		int iPos = sOptionsLower.indexOf("lastrun=");
		if(iPos>=0)
		{
			String sLeft = m_sOptions.substring(0, iPos);
			String sRight = m_sOptions.substring(iPos, m_sOptions.length());
			iPos = sRight.indexOf(',');
			if(iPos>=0)
				sRight = sRight.substring(iPos, sRight.length());
			else
				sRight = "";

			String sLastRun = "";
			if(bSetRunTime) sLastRun = "LastRun=" + System.currentTimeMillis();
			m_sOptions = sLeft + sLastRun + sRight;
		}
		else //just append
		{
			if(bSetRunTime) m_sOptions += ",LastRun=" + System.currentTimeMillis();
		}

		//System.out.println("Before:[" + m_sOptions + "]");
		//4/3/2010 BU
		//remove spurious commas eg "Schedule=D,Interval=1,Days=SMTWHFA,StartTime=23:30,,,,,,,,,,,,,,LastRun=1267623003231"
		StringBuilder sbOptions = new StringBuilder(m_sOptions.length());
		ArrayList arr = Util.splitString(m_sOptions, ',');
		for(int i=0; i<arr.size(); i++)
		{
			String sValue = Util.trimSpaces((String)arr.get(i));
			if(sValue.length()>0)
			{
				if(sbOptions.length()>0) sbOptions.append(',');
				sbOptions.append(sValue);
			}
		}
		m_sOptions = sbOptions.toString();	
		//System.out.println("After: [" + m_sOptions + "]");
	}


	/**
	 * Checks whether this thread is OK.
	 * @return
	 */
	public boolean isOK()
	{
		if(bRunning && !this.isAlive()) //most likely caused by an outofmemory exception
		{
			m_pSystem.doError("AGENDA.NotOK", new String[]{m_rPath.getPathToDesign()}, this);
			m_Parent.removeStatusLine(m_pStatus);
			return false;
		}

		return true;
	}

	/**
	 *
	 * @return
	 */
	public String getOptionString()
	{
		return m_sOptions;
	}

	/**
	 * Gets the path to the design element /group/app.pma/design
	 * @return
	 */
	public int getDesignID()
	{
		return m_iDesignID;
	}

	/**
	 * Describe this object
	 * @return
	 */
	public String toString()
	{
		return m_rPath.getPathToDesign() + " Running=" + bRunning + " LastRun=" + m_sdf.format(m_dtLastRun);
	}

	/**
	 * Determines if the current Action is running or not.
	 * @return
	 */
	public boolean isRunning()
	{
		return bRunning;
	}

	/**
	 * Gets the path to the design element /group/app.pma/design
	 * @return
	 */
	public String getPath()
	{
		return m_rPath.getPathToDesign();
	}


	public String getErrorSource()
	{

		return "AgendaAction(" + m_rPath.getPathToDesign() + ")" ;
	}

	public String getErrorUser()
	{
		return "System";
	}
}