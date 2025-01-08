/** ***************************************************************
pmaAddIn.java
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
package puakma.addin;

import puakma.server.*;
import puakma.addin.AddInStatisticEntry;
import puakma.error.*;
import puakma.system.*;
import puakma.util.Util;

import java.util.*;

/**
 * This is the skeleton for a server addin task. Typically there will be many
 * of these running like servlets performing a whole raft of functions
 *
 * pmaAddIn pAddIn;
 * pAddIn = (pmaAddIn)Class.forName(szAddInName).newInstance();
 * pAddIn.init(pSystem);
 * vAddIns.addElement(pAddIn); //so we can track all addins loaded
 * pAddIn.start();
 */
public class pmaAddIn extends Thread implements ErrorDetect
{
	private boolean m_bShouldQuit=false;
	private boolean m_bIsRunning=false;
	private Date m_dtSecondReference=new Date();
	private Vector<pmaAddInStatusLine> m_vStatusLines = new Vector<pmaAddInStatusLine>();
	private String m_AddInName="$PuakmaAddIn";
	private pmaServer m_pServer;
	private Hashtable<String, AddInStatistic> m_htStatistics = new Hashtable<String, AddInStatistic>();

	protected SystemContext m_pSystem;


	public pmaAddIn()
	{
		setDaemon(true);
	}

	public final void init(pmaServer paramServer, SystemContext paramSystem)
	{
		if(m_pServer==null) m_pServer = paramServer;
		if(m_pSystem==null) m_pSystem = paramSystem;
	}

	/**
	 * Override this method with your own!!
	 */
	public void pmaAddInMain()
	{
	}

	/**
	 * Override this method with your own!!
	 */
	public synchronized void setAddInName(String szNewName)
	{
		m_AddInName = szNewName;
	}

	/**
	 * This method is called by the AddIn Loader (pmaServer object)
	 */
	public final void run()
	{
		if(m_bIsRunning) return;
		m_bIsRunning=true;
		pmaAddInMain(); //the main loop where the work gets done!
		m_bIsRunning=false;
	}

	/**
	 * creates a new status line
	 * @return the status object to use
	 */
	public final pmaAddInStatusLine createStatusLine(String sName)
	{
		pmaAddInStatusLine pStatus = new pmaAddInStatusLine(sName);
		m_vStatusLines.add(pStatus);
		return pStatus;
	}

	/**
	 * creates a new status line
	 * @return the status object to use
	 */
	public final pmaAddInStatusLine createStatusLine()
	{
		pmaAddInStatusLine pStatus = new pmaAddInStatusLine(m_AddInName);
		m_vStatusLines.add(pStatus);
		return pStatus;
	}


	/**
	 * Remove a given status line
	 */
	public final void removeStatusLine(pmaAddInStatusLine pStatus)
	{
		m_vStatusLines.remove(pStatus);
	}


	/**
	 * gets the values of all the status lines
	 */
	public final String getStatus()
	{
		StringBuilder sbReturn=new StringBuilder(256);
		for(int i=0; i<m_vStatusLines.size(); i++)
		{
			pmaAddInStatusLine pStatus = (pmaAddInStatusLine)m_vStatusLines.elementAt(i);
			if(pStatus!=null) 
			{
				sbReturn.append(pStatus.toString());
				sbReturn.append("\r\n");
			}
		}
		return sbReturn.toString();
	}


	/**
	 * @return true if iSeconds have elapsed since the method was last called
	 */
	public final boolean addInSecondsHaveElapsed(int iSeconds)
	{
		Date dtNow = new Date();
		if( (int)((dtNow.getTime()-m_dtSecondReference.getTime())/1000) > iSeconds)
		{
			m_dtSecondReference = new Date();
			return true;
		}
		return false;
	}

	/**
	 * This method should be called regularly by an addin. Stats pruning added here so the specific addin doesn't have to worry about it 
	 * @return true if the addin should quit
	 */
	public boolean addInShouldQuit()
	{    
		pruneStatistics(null);
		return m_bShouldQuit;
	}

	/**
	 * @return true if the addin is still running
	 * may be used later to determine if the thread should be killed when
	 * shutting down the addin
	 */
	public boolean addInIsRunning()
	{
		return m_bIsRunning;
	}

	/**
	 * Asks the class nicely to quit
	 */
	public void requestQuit()
	{
		m_bShouldQuit=true;
		interrupt();
	}

	public String getAddInName()
	{
		return m_AddInName;
	}

	public String tell(String sCommand)
	{		
		//System.out.println("a[" + sCommand + "]" + this.getClass().getName());
		if(sCommand==null) return "";

		String sCommandLow = Util.trimSpaces(sCommand).toLowerCase();
		if(sCommandLow.startsWith("stats"))
		{			
			String sStatisticKeys[] = null;

			if(sCommandLow.indexOf(' ')>0)
			{
				sStatisticKeys = new String[]{sCommandLow.substring("stats".length()+1, sCommandLow.length())};
			}

			StringBuilder sb = new StringBuilder(128);
			if(sStatisticKeys==null) sStatisticKeys = getStatisticKeys();

			//System.out.println("b[" + sCommandLow + "]" + this.getClass().getName() + " len:" + sStatisticKeys.length);

			if(sStatisticKeys!=null)
			{
				for(int k=0; k<sStatisticKeys.length; k++) //loop though all the keys
				{
					if(hasStatisticsKey(sStatisticKeys[k]))
					{
						sb.append("---- " + sStatisticKeys[k] + " ----\r\n");
						AddInStatistic as[] = getStatistics(sStatisticKeys[k]);
						if(as!=null)
						{						
							for(int i=0; i<as.length; i++) //loop through each stat
							{
								AddInStatisticEntry se[] = as[i].getStatistics();
								if(se!=null)
								{
									for(int j=0; j<se.length; j++) //loop through each entry
									{
										sb.append(se[j].toString() + "\r\n");
									}
								}
							}
						}//if as!=null
					}
				}
			}
			return sb.toString(); //"->" + sCommand;
		}

		return "";
	}

	/**
	 * Determines if this addin is recording the key specified
	 * @param sStatisticsKey
	 * @return
	 */
	public boolean hasStatisticsKey(String sStatisticsKey) 
	{
		if(sStatisticsKey==null) return false;
		return m_htStatistics.containsKey(sStatisticsKey.toLowerCase());
	}

	/**
	 * Allow other tasks to execute console commands
	 * @param szCommand
	 * @return a string containing the results of the command. CRLF breaks each line
	 */
	public String doConsoleCommand(String szCommand)
	{
		return m_pServer.doConsoleCommand(szCommand);
	}

	/**
	 * Finds the active session or creates a new one for the message specified
	 * This handles the message containing a sessionid or a username/password
	 * @param oMessage
	 * @return null if the session id is invalid or the username/password combo are invalid
	 */
	protected SessionContext getMessageSender(AddInMessage oMessage)
	{		
		SessionContext sessCtx=null;

		if(oMessage.SessionID!=null && oMessage.SessionID.length()>0)
		{
			sessCtx = m_pSystem.getSession(oMessage.SessionID);			
			if(sessCtx==null) return null; //invalid session supplied		
		}
		else
		{			
			sessCtx = m_pSystem.createNewSession(null, "AddInMessenger");			
			sessCtx.setSessionTimeOut(2);//this will be a shortlived session!
			if(oMessage.UserName!=null && oMessage.UserName.length()!=0)
			{
				if(!m_pSystem.loginSession(sessCtx, oMessage.UserName, oMessage.Password, null)) return null;
			}
		}//else
		
		
		return sessCtx;
	}

	/**
	 * Default message response. This should be overloaded in your class if
	 * required
	 * @param oMessage
	 * @return
	 */
	public AddInMessage sendMessage(AddInMessage oMessage)
	{
		//long lStart = System.currentTimeMillis();
		//m_pSystem.doDebug(0, "1. performService() ->["+sMethodName+"] " + (System.currentTimeMillis()-lStart) + "ms", sess);
		
		
		
		if(oMessage==null) return null;

		SessionContext sessCtx = getMessageSender(oMessage);
		if(sessCtx==null) return null;

		
		String sAction = oMessage.getParameter("Action");
		if(sAction!=null && sAction.equalsIgnoreCase("GetStatistics"))
		{
			String sStatisticKey = oMessage.getParameter("key");
			if(sStatisticKey.length()==0) sStatisticKey = null;
			AddInStatistic as[] = getStatistics(sStatisticKey);
			if(as!=null)
			{
				AddInMessage am = new AddInMessage();
				am.setObject("statistics", as);
				am.Status = AddInMessage.STATUS_SUCCESS;
				return am;
			}
		}
		/*
		long lSize=0;
		if(oMessage.Data!=null) lSize=oMessage.Data.length;
		if(oMessage.Attachment!=null) lSize=oMessage.Attachment.length();
		m_pSystem.doInformation("AddInMessage Received: " + oMessage.ContentType + " " + lSize + "bytes from [" + sessCtx.getUserName() + "]", this);
		AddInMessage am = new AddInMessage();
		am.Status = AddInMessage.STATUS_UNKNOWNMESSAGE;
		return am;
		 */
		return null;
	}

	public String getErrorSource()
	{
		return m_AddInName;
	}

	public String getErrorUser()
	{
		return pmaSystem.SYSTEM_ACCOUNT;
	}

	/**
	 * Determines if the addin may be loaded multiple times. Default is true. 
	 * Override this and return false if the addin may be loaded only once (eg Mailer)
	 */
	public boolean canLoadMultiple()
	{
		return true;
	}

	/**
	 * Determines if the addin has completed all its startup processes correctly. Default is true. 
	 * Override this and return false if the addin is not yet ready for work. Note: pmaServer will 
	 * call this method and wait until it returns true. This will allow addins to load in order in 
	 * case one addin relies on another.
	 * See: AddIns= line in puakma.config
	 */
	public boolean addInReady()
	{
		return true;
	}

	/**
	 * Tell the logging mechanism to send log events to this addin
	 */
	public void registerThisAddInToReceiveLogMessages()
	{
		m_pSystem.registerAddInToReceiveLogMessages(getClass().getName());
	}

	/**
	 * Tell the logging mechanism to no longer send log events to this addin
	 */
	public void deregisterThisAddInToReceiveLogMessages()
	{
		m_pSystem.deregisterAddInToReceiveLogMessages(getClass().getName());
	}

	/**
	 * Create a repository for statistics
	 * @param sStatisticKey eg "http.hitsperhour"
	 * @param iCaptureType see StatisticEntry.STAT_TYPE_xxx
	 * @param iMaxPeriodsHistory use -1 for defaults for the capture type
	 */
	public void createStatistic(String sStatisticKey, int iCaptureType, int iMaxPeriodsHistory, boolean bForceCreation)
	{
		if(sStatisticKey==null) return;
		
		if(bForceCreation) 
			m_htStatistics.remove(sStatisticKey.toLowerCase());
		else
		{
			if(m_htStatistics.containsKey(sStatisticKey.toLowerCase())) return; //already exists, don't re-add
		}
		
		AddInStatistic as = new AddInStatistic(this, sStatisticKey, iCaptureType, iMaxPeriodsHistory);
		m_htStatistics.put(as.getStatisticKey(), as);
	}

	/**
	 * 
	 * @param sStatisticKey
	 * @param dIncrementBy
	 */
	public void incrementStatistic(String sStatisticKey, double dIncrementBy)
	{
		if(sStatisticKey==null) return;
		sStatisticKey = sStatisticKey.toLowerCase();
		if(m_htStatistics.containsKey(sStatisticKey))
		{
			AddInStatistic as = (AddInStatistic) m_htStatistics.get(sStatisticKey);
			as.increment(dIncrementBy);
		}
	}
	
	/**
	 * Replaces the value in the current interval with the new value
	 * @param sStatisticKey
	 * @param dSetToValue
	 */
	public void setStatistic(String sStatisticKey, Object objSetToValue)
	{
		if(sStatisticKey==null) return;
		sStatisticKey = sStatisticKey.toLowerCase();
		if(m_htStatistics.containsKey(sStatisticKey))
		{
			AddInStatistic as = (AddInStatistic) m_htStatistics.get(sStatisticKey);
			as.set(objSetToValue);
		}
	}

	/**
	 * 
	 * @param sStatisticKey
	 * @return
	 */
	/*public StatisticEntry[] getStatistics(String sStatisticKey)
	{
		if(sStatisticKey==null) return null;
		sStatisticKey = sStatisticKey.toLowerCase();
		if(m_htStatistics.containsKey(sStatisticKey))
		{
			AddInStatistic as = (AddInStatistic) m_htStatistics.get(sStatisticKey);
			return as.getStatistics();
		}

		return null;
	}*/

	public AddInStatistic[] getStatistics(String sStatisticKey)
	{
		
		if(sStatisticKey!=null)
		{
			sStatisticKey = sStatisticKey.toLowerCase();
			if(m_htStatistics.containsKey(sStatisticKey))
			{
				AddInStatistic as = (AddInStatistic) m_htStatistics.get(sStatisticKey);
				return new AddInStatistic[]{as};
			}
			return null; //no stat by that name
		}

		
		//all
		ArrayList<AddInStatistic> arr = new ArrayList<AddInStatistic>();
		Enumeration en = m_htStatistics.keys();
		while(en.hasMoreElements())
		{
			String sKey = (String) en.nextElement();
			AddInStatistic as = (AddInStatistic) m_htStatistics.get(sKey);
			if(as!=null) arr.add(as);
		}

		AddInStatistic as[] = new AddInStatistic[arr.size()]; 
		for(int i=0; i<as.length; i++)
		{
			as[i] = (AddInStatistic)arr.get(i);
		}
		return as;
	}

	/**
	 * Find all the stats this server is tracking
	 * @return
	 */
	public String[] getStatisticKeys()
	{
		ArrayList<String> arr = new ArrayList<String>();
		Enumeration en = m_htStatistics.keys();
		while(en.hasMoreElements())
		{
			String sKey = (String) en.nextElement();
			arr.add(sKey);
		}

		return Util.objectArrayToStringArray(arr.toArray());
	}


	/**
	 * Remove old stats from the lists
	 * @param sStatisticKey pass null to prune all
	 */
	public void pruneStatistics(String sStatisticKey)
	{
		if(sStatisticKey!=null)
		{
			sStatisticKey = sStatisticKey.toLowerCase();
			if(m_htStatistics.containsKey(sStatisticKey))
			{
				AddInStatistic as = (AddInStatistic) m_htStatistics.get(sStatisticKey);
				as.prune();
			}
			return;
		}

		Enumeration en = m_htStatistics.keys();
		while(en.hasMoreElements())
		{
			String sKey = (String) en.nextElement();
			AddInStatistic as = (AddInStatistic) m_htStatistics.get(sKey);
			as.prune();
		}
	}

}