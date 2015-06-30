/** ***************************************************************
MAILER.java
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
package puakma.addin.mail;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.Date;
import java.util.Hashtable;

import puakma.addin.AddInStatistic;
import puakma.addin.pmaAddIn;
import puakma.addin.pmaAddInStatusLine;
import puakma.error.ErrorDetect;
import puakma.system.pmaSystem;
import puakma.system.pmaThread;
import puakma.system.pmaThreadInterface;
import puakma.system.pmaThreadPoolManager;
import puakma.util.MailAddress;
import puakma.util.Util;

/**
 * This addin sends outbound smtp mail and also delivers local mail (to puakma users)
 */
public class MAILER extends pmaAddIn implements ErrorDetect
{
	private pmaThreadPoolManager m_tpm;
	private String szMailDomain="";
	private int max_pooled_threads = 0;
	private int min_pooled_threads = 0;
	private int thread_pool_timeout = 60000;
	private int mailer_cleanup_seconds = 120;
	private int mailer_poll_seconds = 120;
	private int mail_retry_minutes = 7;
	private int mail_retry_count = 10;
	private int mail_socket_timeout_seconds = 60;
	private int iSmartHostPort=25;
	private String szSmartHost;
	private String m_HostName;
	private Hashtable htMessageQueue = new Hashtable();
	private pmaAddInStatusLine pStatus;
	private boolean m_bRunning = true;

	public static final String STATISTIC_KEY_MAILSPERHOUR = "mailer.mailsperhour";

	/**
	 * This method is called by the pmaServer object
	 */
	public void pmaAddInMain()
	{
		pmaThread t;

		setAddInName("MAILER");    
		pStatus = createStatusLine();
		pStatus.setStatus("Starting...");
		m_pSystem.doInformation("MAILER.Startup", this);

		createStatistic(STATISTIC_KEY_MAILSPERHOUR, AddInStatistic.STAT_CAPTURE_PER_HOUR, -1, true);

		// startup the add an extra thread for the mail cleaner...
		szMailDomain = m_pSystem.getSystemProperty("MAILERMailDomain");
		if(szMailDomain == null || szMailDomain.length()==0)
		{
			m_pSystem.doError("MAILER.NoMailDomain", this);
			m_pSystem.doInformation("MAILER.Shutdown", this);
			return;
		}
//FIXME int parsing?
		max_pooled_threads = Integer.parseInt(m_pSystem.getSystemProperty("MAILERTransferThreads")) + 1;
		min_pooled_threads = max_pooled_threads;
		thread_pool_timeout = Integer.parseInt(m_pSystem.getSystemProperty("MAILERThreadCreateTimeout"));
		try{mailer_cleanup_seconds = Integer.parseInt(m_pSystem.getSystemProperty("MAILERCleanUpSeconds"));}catch(Exception e){}
		mailer_poll_seconds = Integer.parseInt(m_pSystem.getSystemProperty("MAILERPollSeconds"));
		if(mailer_poll_seconds < 10) mailer_poll_seconds = 10; //Less is SILLY!
		mail_retry_count = Integer.parseInt(m_pSystem.getSystemProperty("MAILERMaxRetries"));
		mail_retry_minutes = Integer.parseInt(m_pSystem.getSystemProperty("MAILERRetryMinutes"));
		try{mail_socket_timeout_seconds = Integer.parseInt(m_pSystem.getSystemProperty("MAILERSocketTimeoutSeconds"));}catch(Exception e){}
		m_pSystem.doInformation("MAILER.Started", new String[]{szMailDomain, m_pSystem.getSystemHostName(), String.valueOf(max_pooled_threads-1)}, this);


		szSmartHost = m_pSystem.getSystemProperty("MAILERSmartHost");
		m_HostName = m_pSystem.getSystemProperty("SystemHostName");
		if(m_HostName==null || m_HostName.length()==0) m_HostName = "not_set";


		if(szSmartHost==null || szSmartHost.length()==0)
			szSmartHost = null;
		else
		{
			iSmartHostPort = Integer.parseInt(m_pSystem.getSystemProperty("MAILERSmartHostPort"));
			m_pSystem.doInformation("MAILER.SmartHost", new String[]{szSmartHost, String.valueOf(iSmartHostPort)}, this);
		}

		MailCleaner mc = null;
		m_tpm = new pmaThreadPoolManager(m_pSystem, min_pooled_threads, max_pooled_threads, thread_pool_timeout, "mail");
		t = m_tpm.getNextThread();
		if(t!=null)
		{
			mc = new MailCleaner(m_pSystem, mailer_cleanup_seconds, this, t);
			t.runThread((pmaThreadInterface)mc);
		}

		// main loop
		while(m_bRunning)
		{
			if(this.addInShouldQuit()) m_bRunning = false;
			//System.out.println("running "+m_bRunning);
			if(m_tpm.hasAvailableThreads())
			{
				pStatus.setStatus("Checking for new messages...");
				long lMessageBodyID = -1;
				if((lMessageBodyID=getNextMessage()) > 0)
				{
					//create a handler
					t = m_tpm.getNextThread();
					if(t!=null)
					{
						//System.out.println("running transferthread: " + lMessageBodyID);
						MailTransferThread mailWorker = new MailTransferThread(m_pSystem, this, lMessageBodyID, t.getName(), mail_socket_timeout_seconds);
						t.runThread((pmaThreadInterface)mailWorker);
						this.incrementStatistic(STATISTIC_KEY_MAILSPERHOUR, 1);
					}
				}//getNextMessage
				else
				{
					pStatus.setStatus("Idle (" + (max_pooled_threads-1) + " transfer threads, "+getQueueStatus() + ")");
					try
					{						
						if(m_bRunning) sleep(1000*mailer_poll_seconds);						
					} catch(Exception e){ }
				}
			}
			else //there are no available threads, so wait 1 seconds
				try{sleep(1000);} catch(Exception w){ }

				//System.out.println("-END LOOP-");
		}//while
		m_bRunning = false;
		m_tpm.requestQuit();
		pStatus.setStatus("Shutting down");
		m_pSystem.doInformation("MAILER.Shutdown", this);
		removeStatusLine(pStatus);
	}//pmaAddInMain()


	/**
	 * Find out who we should be saying helo as - called by transfer thread
	 */
	public String getMailDomain()
	{
		return this.szMailDomain;
	}

	/**
	 * Returns the hostname of this server instance
	 */
	public String getHostName()
	{
		return m_HostName;
	}



	/**
	 * Get the number of minutes delay between retrying messages
	 */
	public int getMailRetryMinutes()
	{
		return mail_retry_minutes;
	}


	/**
	 * Get the number of times a message should be retried
	 */
	public int getMailMaxRetryCount()
	{
		return mail_retry_count;
	}



	/**
	 * Find if we are using a smart host
	 */
	public String getSmartHost()
	{
		return szSmartHost;
	}

	/**
	 * Removes a message id from the queue
	 */
	public synchronized void removeMessageID(long lMessageID)
	{      
		String sID = String.valueOf(lMessageID);
		if(htMessageQueue.containsKey(sID)) htMessageQueue.remove(sID);
	}

	/**
	 * Checks to see if a message is already being processed. If it is not in the
	 * Vector, we add it and return true. If it is being processed in another thread,
	 * just return false.
	 */
	public synchronized boolean addMessageID(long lMessageID)
	{      
		String sID = String.valueOf(lMessageID);
		if(!htMessageQueue.containsKey(sID)) 
		{
			htMessageQueue.put(sID, "");
			return true;
		}
		return false;
	}

	/**
	 * Find if we are using a smart host
	 */
	public int getSmartHostPort()
	{
		return iSmartHostPort;
	}


	/**
	 * gets the MessageBodyID of the next message in the queue
	 * remember to query the threads to find out who is doing what!
	 */
	private long getNextMessage()
	{
		Connection cx = null;
		PreparedStatement prepStmt = null;
		ResultSet rs = null;
		long lMessageID=-1;
		long lMessageReturnID=-1;

		try
		{
			cx = m_pSystem.getSystemConnection();
			String szQuery = "SELECT MAILHEADER.MailBodyID FROM MAILHEADER,MAILBODY WHERE MAILHEADER.MailBodyID=MAILHEADER.MailBodyID AND MAILHEADER.MessageStatus='P' AND (MAILHEADER.RetryAfter IS NULL OR MAILHEADER.RetryAfter<?)";
			prepStmt = cx.prepareStatement(szQuery, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
			prepStmt.setTimestamp(1, new Timestamp(System.currentTimeMillis()) );
			rs = prepStmt.executeQuery();
			while(rs.next())
			{
				lMessageID = rs.getLong(1);
				if(addMessageID(lMessageID))
				{
					lMessageReturnID = lMessageID;
					break;
				}
			}			
		}
		catch (Exception sqle)
		{
			m_pSystem.doError("MAILER.MailError", new String[]{sqle.toString()}, this);
			lMessageReturnID = -1;
		}
		finally
		{
			Util.closeJDBC(rs);
			Util.closeJDBC(prepStmt);
			m_pSystem.releaseSystemConnection(cx);
		}
		return lMessageReturnID;
	}

	public boolean isRunning()
	{
		return m_bRunning;
	}

	/**
	 *
	 */
	public void requestQuit()
	{    
		//System.out.println("requestQuit() ");
		m_bRunning = false;
		this.interrupt();
		super.requestQuit();   
		//System.out.println("requestQuit() DONE.");
	}



	public String getErrorSource()
	{
		return "MAILER";
	}

	public String getErrorUser()
	{
		return pmaSystem.SYSTEM_ACCOUNT;
	}

	/**
	 * only allow this to be loaded once
	 */
	public boolean canLoadMultiple()
	{
		return false;
	}

	/**
	 * 
	 */
	public String tell(String sCommand)
	{
		String sReturn = super.tell(sCommand);	
		if(sReturn!=null && sReturn.length()>0) return sReturn;

		if(sCommand.equalsIgnoreCase("?") || sCommand.equalsIgnoreCase("help"))
		{
			return "->status\r\n" +        
			"->queue clear\r\n" + 			
			"->queue clear all\r\n" +
			"->resend dead\r\n" +
			"->stats [statistickey]\r\n";
		}

		if(sCommand.toLowerCase().equals("status"))
		{
			return showMailQueue();
		}
		if(sCommand.toLowerCase().equals("queue clear"))
		{
			return clearMailQueue(true);
		}

		if(sCommand.toLowerCase().equals("queue clear all"))
		{
			return clearMailQueue(false);
		}

		if(sCommand.toLowerCase().equals("resend dead"))
		{
			return resendDeadMails();
		}


		return "";
	}


	/**
	 * 
	 * @return
	 */
	private String resendDeadMails() 
	{
		Connection cx = null;
		Statement stmt = null;		
		try
		{
			cx = m_pSystem.getSystemConnection();
			String sQuery = "UPDATE MAILHEADER SET RetryCount=0,MessageStatus='P',FailReason='' WHERE MessageStatus='D'";
			stmt = cx.createStatement();
			stmt.execute(sQuery);

		}
		catch (Exception sqle)
		{
			m_pSystem.doError("resendDeadMails(): ", new String[]{sqle.toString()}, this);	       
		}
		finally
		{			
			Util.closeJDBC(stmt);
			m_pSystem.releaseSystemConnection(cx);
		}
		return "Dead mail resent.";
	}

	/**
	 * 
	 * @param bDeadOnly
	 * @return
	 */
	private String clearMailQueue(boolean bDeadOnly) 
	{
		Connection cx = null;
		Statement stmt = null;		
		try
		{
			cx = m_pSystem.getSystemConnection();
			String sQuery = "DELETE FROM MAILHEADER";
			if(bDeadOnly) sQuery = "DELETE FROM MAILHEADER WHERE MessageStatus='D'";
			stmt = cx.createStatement();
			stmt.execute(sQuery);

		}
		catch (Exception sqle)
		{
			m_pSystem.doError("clearMailQueue(): ", new String[]{sqle.toString()}, this);	       
		}
		finally
		{
			Util.closeJDBC(stmt);
			m_pSystem.releaseSystemConnection(cx);
		}
		return "Queue cleared.";
	}


	/**
	 * Display the contents of the system outbound mail queue
	 * @return
	 */
	private String showMailQueue() 
	{
		StringBuilder sbMessages = new StringBuilder(512);
		Connection cx = null;
		Statement stmt = null;
		ResultSet rs = null;
		long lQueueSize = 0;
		int SUBJECT_LEN=15;
		try
		{
			cx = m_pSystem.getSystemConnection();
			String sQuery = "SELECT * FROM MAILHEADER,MAILBODY WHERE MAILHEADER.MailBodyID=MAILBODY.MailBodyID ORDER BY SentDate";
			stmt = cx.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
			rs = stmt.executeQuery(sQuery);
			while(rs.next())
			{
				Date dtSent = Util.getResultSetDateValue(rs, "SentDate");
				long MailHeaderID = rs.getLong("MailHeaderID");
				int iRetries = rs.getInt("RetryCount");
				String sFailReason = rs.getString("FailReason");
				String sSender = rs.getString("Sender");
				String sSubject = rs.getString("Subject");
				String sSubjectDisplay = sSubject;
				if(sSubjectDisplay.length()>SUBJECT_LEN) sSubjectDisplay = sSubjectDisplay.substring(0, SUBJECT_LEN) + "...";
				String sMessageStatus = rs.getString("MessageStatus");
				MailAddress maRecipient = new MailAddress(rs.getString("Recipient"));
				MailAddress maFrom = new MailAddress(sSender);

				sbMessages.append(MailHeaderID + " " + sMessageStatus + " to:" + maRecipient.getBasicEmailAddress() + " from:" + maFrom.getBasicEmailAddress() + " \"" + sSubjectDisplay + "\" " + Util.formatDate(dtSent, "dd.MM.yy HH:mm") + " (" + iRetries + " retries, \"" + sFailReason + "\")\r\n");
				lQueueSize++;
			}	      
		}
		catch (Exception sqle)
		{
			m_pSystem.doError("showMailQueue(): ", new String[]{sqle.toString()}, this);	       
		}
		finally
		{
			Util.closeJDBC(rs);
			Util.closeJDBC(stmt);
			m_pSystem.releaseSystemConnection(cx);
		}
		sbMessages.append("----------\r\n");
		sbMessages.append(lQueueSize + " messages\r\n");

		return sbMessages.toString();
	}

	/**
	 * 
	 * @return
	 */
	private String getQueueStatus()
	{
		Connection cx = null;
		Statement stmt = null;
		ResultSet rs = null;
		StringBuilder sbStatus = new StringBuilder();

		try
		{
			cx = m_pSystem.getSystemConnection();
			String sQuery = "SELECT MessageStatus,COUNT(MessageStatus) as tot FROM MAILHEADER,MAILBODY WHERE MAILHEADER.MailBodyID=MAILBODY.MailBodyID GROUP BY MessageStatus ORDER BY MessageStatus";
			stmt = cx.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
			rs = stmt.executeQuery(sQuery);
			while(rs.next())
			{
				if(sbStatus.length()>0) sbStatus.append(',');
				sbStatus.append(rs.getString(1)+"="+rs.getInt(2));
			}	      
		}
		catch (Exception sqle)
		{
			m_pSystem.doError("getQueueStatus(): ", new String[]{sqle.toString()}, this);	       
		}
		finally
		{
			Util.closeJDBC(rs);
			Util.closeJDBC(stmt);
			m_pSystem.releaseSystemConnection(cx);
		}
		if(sbStatus.length()==0) sbStatus.append("Queue empty");
		return sbStatus.toString();
	}

}