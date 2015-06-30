/** ***************************************************************
MailCleaner.java
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

import puakma.addin.pmaAddInStatusLine;
import puakma.error.ErrorDetect;
import puakma.error.pmaLog;
import puakma.system.SystemContext;
import puakma.system.pmaSystem;
import puakma.system.pmaThread;
import puakma.system.pmaThreadInterface;

public class MailCleaner implements pmaThreadInterface, ErrorDetect
{
	private int CleanUpIntervalSeconds;
	//private pmaThread ParentThread;
	private MAILER pParent;
	private SystemContext pSystem;
	private pmaAddInStatusLine pStatus;

	public MailCleaner(SystemContext paramSystem, int paramCleanEvery, MAILER paramParent, pmaThread paramParentThread)
	{
		pSystem = paramSystem;
		pParent = paramParent;
		CleanUpIntervalSeconds = paramCleanEvery;
		if(CleanUpIntervalSeconds<10) CleanUpIntervalSeconds = 10; //anything less is SILLY!
		//ParentThread = paramParentThread;
	}


	/**
	 *
	 */
	public void run()
	{
		pSystem.doDebug(pmaLog.DEBUGLEVEL_FULL, "run()", this);
		pStatus = pParent.createStatusLine(" " + getErrorSource());

		//int iSleepInterval = (CleanUpIntervalSeconds*1000);
		//continue until someone tells the thread to die
		while(pParent.isRunning())
		{
			pStatus.setStatus("Idle (Cleanup every " + CleanUpIntervalSeconds + " seconds)");
			try
			{ 				
				if(pParent.isRunning()) Thread.sleep(1000*CleanUpIntervalSeconds); else break;            				
			} catch(Exception e){ }
			if(pParent.isRunning())
			{
				pStatus.setStatus("Cleaning...");
				long lCount = cleanUp();
				if(lCount>0)
					pSystem.doInformation("MailCleaner.FinishProcessing", new String[]{String.valueOf(lCount)}, this);
			}
		}
		pStatus.setStatus("Shutting down");
		pParent.removeStatusLine(pStatus);    
	}



	/**
	 *
	 */
	private long cleanUp()
	{
		long lDeleted=0;
		Connection cx=null;

		try
		{
			cx = pSystem.getSystemConnection();      
			String szQuery = "SELECT MailHeaderID,mb.MailBodyID from MAILBODY mb LEFT JOIN MAILHEADER mh on mb.MailBodyId = mh.MailBodyId WHERE MailHeaderID IS NULL AND (DeliveredDate<? OR DeliveredDate IS NULL)";
			PreparedStatement Stmt = cx.prepareStatement(szQuery, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
			//set to a minute and a half ago so we don't blow away a half saved email message
			java.util.Date dtPast = puakma.util.Util.adjustDate(new java.util.Date(), 0, 0, 0, 0, -5, 0);
			Stmt.setTimestamp(1, new java.sql.Timestamp(dtPast.getTime()));
			ResultSet RS = Stmt.executeQuery();
			while(RS.next())
			{
				long lMailBodyID = RS.getLong("MailBodyID");
				//System.out.println("cleaning "+lMailBodyID);
				//if(lMailBodyID>0) //hsqldb numbers from zero....
				//{
				pSystem.doDebug(pmaLog.DEBUGLEVEL_DETAILED, "Deleting message #%s", new String[]{String.valueOf(lMailBodyID)}, this);
				deleteMessage(lMailBodyID);
				lDeleted++;
				//}
			}
			RS.close();
			Stmt.close();
		}
		catch (Exception sqle)
		{
			pSystem.doError("MailCleaner.CleanError", new String[]{sqle.toString()}, this);
		}
		finally
		{
			pSystem.releaseSystemConnection(cx);
		}
		return lDeleted;
	}


	/**
	 * Deletes the message from the table & attachment table
	 */
	private void deleteMessage(long lMailBodyID)
	{
		Connection cx=null;
		try
		{
			cx = pSystem.getSystemConnection();
			//can I not do both at once??
			String szQuery1 = "DELETE FROM MAILATTACHMENT WHERE MailBodyID=" + lMailBodyID;
			String szQuery2 = "DELETE FROM MAILBODY WHERE MailBodyID=" + lMailBodyID;
			Statement Stmt = cx.createStatement();
			Stmt.execute(szQuery1);
			Stmt.execute(szQuery2);
			Stmt.close();
		}
		catch (Exception sqle)
		{
			pSystem.doError("MailCleaner.DeleteError", new String[]{String.valueOf(lMailBodyID), sqle.toString()}, this);
		}
		finally
		{
			pSystem.releaseSystemConnection(cx);
		}
	}


	public String getErrorSource()
	{
		return "MailCleaner";
	}

	public String getErrorUser()
	{
		return pmaSystem.SYSTEM_ACCOUNT;
	}

	public void destroy()
	{
	}

	public String getThreadDetail() 
	{
		return getErrorSource();
	}

}