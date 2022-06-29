/** ***************************************************************
AGENDA.java
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

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.Vector;

import puakma.addin.AddInStatistic;
import puakma.addin.pmaAddIn;
import puakma.addin.pmaAddInStatusLine;
import puakma.addin.http.TornadoServer;
import puakma.addin.http.TornadoServerInstance;
import puakma.addin.http.document.DesignElement;
import puakma.util.Util;

/**
 * <p>Title: AGENDA</p>
 * <p>Description: Used to run scheduled actions. An action that is a candidate
 * to run is put in the m_vWaitList until room becomes free, it is then removed
 * from the waitlist and added to the m_vRunningActions list</p>
 * <p>Copyright: Copyright (c) 2002</p>
 * <p>Company: </p>
 * @author Brendon Upson
 * @version 1.0
 */

public class AGENDA extends pmaAddIn
{
	private pmaAddInStatusLine m_pStatus;
	private int m_iMaxConcurrentActions=5;
	//private int m_iCurrentActionCount=0;
	private int m_iErrCount = 0;
	private int m_iRefreshIntervalMinutes=15;
	private int m_iQueuePollIntervalSeconds=1;

	private Vector m_vActionList=new Vector();
	private Vector m_vWaitList = new Vector();
	private Vector m_vRunningActions = new Vector();
	private boolean m_bDebug=false;
	private boolean m_bShowStartFinish=false;
	private long m_lMaxRunSeconds=-1;

	public static final String STATISTIC_KEY_ACTIONSPERHOUR = "agenda.actionsperhour";
	public static final String STATISTIC_KEY_DEADACTIONSPERHOUR = "agenda.deadactionsperhour";

	/**
	 * This method is called by the pmaServer object
	 */
	public void pmaAddInMain()
	{
		TornadoServer.getInstance(m_pSystem);
		setAddInName("AGENDA");
		m_pStatus = createStatusLine();
		m_pStatus.setStatus("Starting...");
		m_pSystem.doInformation("AGENDA.Startup", this);

		createStatistic(STATISTIC_KEY_ACTIONSPERHOUR, AddInStatistic.STAT_CAPTURE_PER_HOUR, -1, true);
		createStatistic(STATISTIC_KEY_DEADACTIONSPERHOUR, AddInStatistic.STAT_CAPTURE_PER_HOUR, -1, true);

		String szTemp = m_pSystem.getSystemProperty("AGENDAMaxConcurrentActions");
		if(szTemp!=null)
		{
			try{
				m_iMaxConcurrentActions = Integer.parseInt(szTemp);
			}catch(Exception de){};
		}

		szTemp = m_pSystem.getSystemProperty("AGENDARefreshInterval");
		if(szTemp!=null)
		{
			try{
				m_iRefreshIntervalMinutes = Integer.parseInt(szTemp);
			}catch(Exception de){};
		}

		szTemp = m_pSystem.getSystemProperty("AGENDAMaxRunSeconds");
		if(szTemp!=null)
		{
			try{
				m_lMaxRunSeconds = Long.parseLong(szTemp);
			}catch(Exception de){};
		}

		szTemp =m_pSystem.getSystemProperty("AGENDADebug");
		if(szTemp!=null && szTemp.equals("1")) m_bDebug = true;

		szTemp =m_pSystem.getSystemProperty("AGENDAShowStartFinish");
		if(szTemp!=null && szTemp.equals("1")) m_bShowStartFinish = true;


		/*szTemp = m_pSystem.getSystemProperty("AGENDAQueuePollInterval");
      if(szTemp!=null)
      {
         try{
            m_iQueuePollIntervalSeconds = Integer.parseInt(szTemp);
         }catch(Exception de){};
      }*/



		m_pStatus.setStatus("Running. " + m_vRunningActions.size() + "/" + m_iMaxConcurrentActions + " concurrent actions");

		refreshActionList(false);
		// main loop
		while (!this.addInShouldQuit())
		{
			m_pStatus.setStatus("Running " + m_vRunningActions.size() + "/" + m_iMaxConcurrentActions + " actions");

			try
			{
				//check what needs to be run, add to waitlist
				doWaitList();
				//clean dead threads - if outOfMemory exceptions etc
				cleanDeadThreads();
				//move things from the waitlist until we have enough concurrent actions running
				runFromWaitList();
				//refresh action list?
				if(addInSecondsHaveElapsed(m_iRefreshIntervalMinutes*60)) refreshActionList(false);
			}
			catch(Exception e)
			{
				m_pSystem.doError(e.toString(), this);
				e.printStackTrace();
			}
			//double dblSleep = (m_iQueuePollIntervalSeconds*0.98)*1000; 
			try{Thread.sleep(998);}catch(Exception e){} //more than once a second!
		}//end while
		m_pStatus.setStatus("Shutting down");
		requestQuit();
		waitForRunners();
		m_pSystem.doInformation("AGENDA.Shutdown", this);
		removeStatusLine(m_pStatus);
		//m_pSystem.clearDBPoolManager();
	}


	/**
	 * Goes through the RDBMS and refreshes the internal list of things to run.
	 */
	private void refreshActionList(boolean bShowCompleted)
	{
		TornadoServerInstance tsi = TornadoServer.getInstance();

		Connection cx = null;
		Statement stmt = null;
		ResultSet rs = null;

		//clear the list
		//System.out.println("REFRESHING ");
		m_pStatus.setStatus("Refreshing...");
		m_vActionList.removeAllElements();
		//Now repopulate the list
		try
		{
			cx = m_pSystem.getSystemConnection();
			stmt = cx.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
			rs = stmt.executeQuery("SELECT * FROM APPLICATION,DESIGNBUCKET WHERE APPLICATION.AppID=DESIGNBUCKET.AppID AND DESIGNBUCKET.DesignType=" + DesignElement.DESIGN_TYPE_SCHEDULEDACTION);
			while(rs.next())
			{
				int iDesignID = rs.getInt("DesignBucketID");
				String sAppGroup = rs.getString("AppGroup");
				if(sAppGroup==null) sAppGroup = "";
				String sAppName = rs.getString("AppName");
				String sDesignName = rs.getString("Name");
				String sOptions = rs.getString("Options");
				boolean bDisabled = tsi.isApplicationDisabled(sAppGroup, sAppName); //HTTPServer. isAppDisabled(m_pSystem, sAppName, sAppGroup);

				if(!bDisabled && sOptions!=null && sOptions.toLowerCase().indexOf("schedule=n")<0)
				{
					addToAgenda(sAppGroup, sAppName, sDesignName, sOptions, iDesignID);
					//System.out.println("ADD: " + szAppGroup + "/" + szAppName + ".pma/" + szDesignName + " [" + szOptions + "]");
				}
				else
				{
					//System.out.println("SKIPPING: " + szAppGroup + "/" + szAppName + ".pma/" + szDesignName + " [" + szOptions + "]");
				}

			}			
		}
		catch(Exception e)
		{
			m_pSystem.doError("AGENDA.BuildAgendaError", new String[]{e.toString()}, this);
		}
		finally
		{
			Util.closeJDBC(rs);
			Util.closeJDBC(stmt);
			m_pSystem.releaseSystemConnection(cx);
		}

		if(bShowCompleted) m_pSystem.doInformation("AGENDA.RefreshComplete", this);
	}


	/**
	 * Creates an Agenda Object and determines the next run time.
	 */
	private void addToAgenda(String szAppGroup, String szAppName, String szDesignName, String szOptions, int iDesignID)
	{
		AgendaItem aItem = new AgendaItem(szAppGroup, szAppName, szDesignName, szOptions, iDesignID);
		//aItem.setLastRunTime();
		aItem.setNextRunTime();
		m_vActionList.add(aItem);
	}


	/**
	 * Check that each listener is running. If the listener is not running, then remove it
	 */
	private void waitForRunners()
	{
		int iActiveCount = 1;
		while(iActiveCount>0)
		{
			iActiveCount=0;
			for(int i=0; i<m_vRunningActions.size(); i++)
			{
				//HTTPServer s = (HTTPServer)m_vRunningActions.elementAt(i);
				//if(s.isAlive()) iActiveCount++;
			}
			m_pStatus.setStatus("Shutting down. Waiting for " + iActiveCount + " actions to complete");
		}//while
	}

	/**
	 * Request this task to stop
	 */
	public void requestQuit()
	{
		m_vWaitList.removeAllElements();
		m_vActionList.removeAllElements();
		this.interrupt();
		super.requestQuit();
		for(int i=0; i<m_vRunningActions.size(); i++)
		{
			AgendaAction aAction = (AgendaAction)m_vRunningActions.elementAt(i);
			if(aAction!=null)
			{
				aAction.requestQuit();
			}
		}

	}



	public String tell(String sCommand)
	{
		String sReturn = super.tell(sCommand);	
		if(sReturn!=null && sReturn.length()>0) return sReturn;

		if(sCommand.equalsIgnoreCase("?") || sCommand.equalsIgnoreCase("help"))
		{
			return "->schedule\r\n" +
					"->status\r\n" +
					"->dbpool status\r\n" +
					"->refresh\r\n" +
					"->run /group/app.pma/action\r\n" +
					"->stats [statistickey]\r\n" +
					"->waitlist status\r\n" +
					"->waitlist clear\r\n";
		}

		if(sCommand.toLowerCase().equals("status"))
		{
			return "-> AGENDA status:" + "\r\n" +
					"Actions running: " + m_vRunningActions.size() + "\r\n" +
					"Actions waitlisted: " + m_vWaitList.size() + "\r\n" +
					"Actions scheduled: " + m_vActionList.size() + "\r\n" +
					"Dead actions: " + m_iErrCount + "\r\n" +
					"Max concurrent Actions: " + m_iMaxConcurrentActions + "\r\n" +
					"Refresh action list every " + m_iRefreshIntervalMinutes + " minutes\r\n" +
					"Poll queue every " + m_iQueuePollIntervalSeconds + " seconds\r\n";
		}

		if(sCommand.toLowerCase().equals("schedule"))
		{
			String sList[]=new String[m_vActionList.size()];
			for(int i=0; i<m_vActionList.size(); i++)
			{
				AgendaItem aItem = (AgendaItem)m_vActionList.elementAt(i);
				sList[i] = aItem.getSchedule();
			}
			Arrays.sort(sList);

			for(int i=0; i<sList.length; i++)
			{
				sReturn += sList[i] + "\r\n";
			}

			return "->schedule:\r\n" + sReturn;
		}

		if(sCommand.toLowerCase().equals("refresh"))
		{
			refreshActionList(true);      
			return "->Refresh complete.";
		}

		if(sCommand.toLowerCase().equals("waitlist clear"))
		{
			m_vWaitList.removeAllElements();
			return "->Waitlist cleared.";
		}

		if(sCommand.toLowerCase().equals("debug on"))
		{
			m_bDebug = true;
			return "-> Debug is now ON";
		}

		if(sCommand.toLowerCase().equals("debug off"))
		{
			m_bDebug = false;
			return "-> Debug is now OFF";
		}

		if(sCommand.toLowerCase().equals("debug status"))
		{
			if(m_bDebug) return "-> Debug is ON";

			return "-> Debug is OFF";
		}

		if(sCommand.toLowerCase().equals("dbpool status"))
		{
			return m_pSystem.getDBPoolStatus();
		}

		/*if(sCommand.toLowerCase().equals("dbpool restart"))
		{
			m_pSystem.restartDBPool();
			return "-> Pool restarted.";
		}*/

		if(sCommand.toLowerCase().equals("waitlist status"))
		{
			String szStatus="";
			Enumeration en = m_vWaitList.elements();
			while(en.hasMoreElements())
			{
				AgendaAction aAction = (AgendaAction)en.nextElement();
				if(aAction!=null)
				{
					szStatus += aAction.toString() + "\r\n";
				}
			}//while
			return "->Waitlist status:\r\n" + szStatus;
		}

		if(sCommand.toLowerCase().startsWith("run "))
		{
			int iPos = sCommand.indexOf(' ');
			if(iPos>0)
			{
				String szPath = sCommand.substring(iPos+1, sCommand.length());
				forceRun(szPath);
			}
		}

		return sReturn;
	}

	/**
	 * Puts items in the waitlist if they are not already in there.
	 */
	private void doWaitList()
	{
		Enumeration en = m_vActionList.elements();
		//for(int i=0; i<m_vActionList.size(); i++)
		while(en.hasMoreElements())
		{
			AgendaItem aItem = (AgendaItem)en.nextElement();
			if(aItem!=null && aItem.shouldRun())
			{
				//System.out.println(aItem);
				if(!waitlistContainsItem(aItem) && !isItemRunning(aItem))
				{
					//System.out.println("WAITLISTED: " + aItem + " now=" + new java.util.Date());
					AgendaAction aAction = new AgendaAction(this, m_pSystem, aItem.getPath(), aItem.getOptionString(), aItem.getDesignID(), aItem.getLastRunDate());
					m_vWaitList.add(aAction);
				}
				else
				{
					//System.out.println("SKIPPED: ---- " + aItem);
				}
				aItem.setLastRunTime();
				aItem.setNextRunTime();
			}
		}
	}


	/**
	 * Forces an action into the waitlist
	 * @param sPath
	 */
	private void forceRun(String sPath)
	{
		Enumeration en = m_vActionList.elements();
		//for(int i=0; i<m_vActionList.size(); i++)
		while(en.hasMoreElements())
		{
			AgendaItem aItem = (AgendaItem)en.nextElement();
			if(aItem!=null)
			{
				//if(aItem.getPath().equalsIgnoreCase(sPath))
				if(aItem.matchesPath(sPath))
				{
					//System.out.println("WAITLISTED: " + aItem + " " + sPath +" now=" + new java.util.Date());
					if(!waitlistContainsItem(aItem) && !isItemRunning(aItem))
					{
						AgendaAction aAction = new AgendaAction(this, m_pSystem, sPath, aItem.getOptionString(), aItem.getDesignID(), aItem.getLastRunDate());
						m_vWaitList.add(aAction);
					}
				}
				aItem.setLastRunTime();
				aItem.setNextRunTime();
			}
		}
	}


	/**
	 * Determines if the waitlist contains the item
	 * @param aItem
	 * @return true or false
	 */
	private boolean waitlistContainsItem(AgendaItem aItem)
	{
		for(int i=0; i<m_vWaitList.size(); i++)
		{
			AgendaAction aAction = (AgendaAction)m_vWaitList.elementAt(i);
			if(aAction!=null && aAction.getPath().equalsIgnoreCase(aItem.getPath())) return true;
		}

		return false;
	}

	/**
	 * Determines if an item is currently running
	 */
	private boolean isItemRunning(AgendaItem aItem)
	{      
		for(int i=0; i<m_vRunningActions.size(); i++)
		{
			AgendaAction aAction = (AgendaAction)m_vRunningActions.elementAt(i);
			if(aAction!=null && aAction.getPath().equalsIgnoreCase(aItem.getPath())) return true;
		}

		return false;
	}

	/**
	 * Removes a given item from the run list
	 * @param aAction
	 */
	public synchronized void removeRunner(AgendaAction aAction)
	{

		for(int i=0; i<m_vRunningActions.size(); i++)
		{
			AgendaAction aActionList = (AgendaAction)m_vRunningActions.elementAt(i);
			if(aAction!=null && aAction.equals(aActionList))
			{
				if(m_vRunningActions.size()>i) m_vRunningActions.remove(i);
				return;
			}
		}

	}

	/**
	 * Log a message when actions start and end
	 * @return
	 */
	public boolean showStartFinishTimes()
	{
		return m_bShowStartFinish;
	}


	/**
	 * Are we in debug mode??
	 * @return
	 */
	public boolean isDebug()
	{
		return m_bDebug;
	}


	/**
	 *
	 */
	private void runFromWaitList()
	{
		//System.out.println("RUN WAITLIST");
		while(m_vRunningActions.size()<=m_iMaxConcurrentActions && m_vWaitList.size()>0)
		{
			AgendaAction aAction = (AgendaAction)m_vWaitList.firstElement();      
			if(aAction!=null)
			{
				m_vRunningActions.add(aAction);
				m_vWaitList.remove(aAction);
				aAction.start();
				this.incrementStatistic(STATISTIC_KEY_ACTIONSPERHOUR, 1);
				updateDesignBucket(aAction, true);
			}
		}
	}

	/**
	 * Removes any dead threads from the running queue
	 */
	private void cleanDeadThreads()
	{
		for(int i=0; i<m_vRunningActions.size(); i++)
		{
			AgendaAction aAction = (AgendaAction)m_vRunningActions.elementAt(i);
			if(aAction!=null)
			{
				if(!aAction.isRunning())
				{
					if(m_vRunningActions.size()>i) m_vRunningActions.remove(i);
					continue;
				}
				
				long lRunSeconds = aAction.getRunningTimeSeconds();
				if(m_lMaxRunSeconds>0 && lRunSeconds>0 && lRunSeconds>m_lMaxRunSeconds)
				{
					m_pSystem.doError("AGENDA.RunTooLong", new String[]{""+m_lMaxRunSeconds}, aAction);
					this.incrementStatistic(STATISTIC_KEY_DEADACTIONSPERHOUR, 1);
					aAction.requestQuit();
					aAction.interrupt();
				}
				if(!aAction.isOK() || aAction.hasExceededInterruptLimit())
				{
					updateDesignBucket(aAction, false); //set no lastrun so it will run again
					m_iErrCount++;
					if(m_vRunningActions.size()>i) m_vRunningActions.remove(i);
				}
			}			
		}
	}


	/**
	 * Resets the options string with the correct LastRun= value
	 * @param aAction
	 */
	private void updateDesignBucket(AgendaAction aAction, boolean bSetLastRunTime)
	{
		Connection cx = null;
		PreparedStatement prepStmt = null;
		aAction.updateOptionString(bSetLastRunTime);
		String szOptions = aAction.getOptionString();
		int iID = aAction.getDesignID();

		try
		{
			cx = m_pSystem.getSystemConnection();
			prepStmt = cx.prepareStatement("UPDATE DESIGNBUCKET SET Options=? WHERE DesignBucketID=" + iID);
			prepStmt.setString(1, szOptions);
			prepStmt.execute();			
		}
		catch(Exception e)
		{
			m_pSystem.doError("AGENDA.UpdateDesignError", new String[]{e.toString()}, this);
		}
		finally
		{
			Util.closeJDBC(prepStmt);
			m_pSystem.releaseSystemConnection(cx);
		}
	}

	/**
	 * only allow this to be loaded once
	 */
	public boolean canLoadMultiple()
	{
		return false;
	}

}//class