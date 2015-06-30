/** ***************************************************************
HSQLDB.java
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

package puakma.addin.db;

import java.sql.Connection;
import java.sql.PreparedStatement;

import puakma.addin.pmaAddIn;
import puakma.addin.pmaAddInStatusLine;

/**
 *
 * @author  bupson
 */
public class HSQLDB extends pmaAddIn
{
	private pmaAddInStatusLine m_pStatus;	
	private boolean m_bAddInRunning=false;
	private static String FULL_USER_NAME = "CN=System Administrator/O=System";

	public void pmaAddInMain()
	{
		setAddInName("HSQLDB");
		m_pStatus = createStatusLine();
		m_pStatus.setStatus("Starting...");
		m_pSystem.doInformation("HSQLDB Embedded Database Startup", this);

		try
		{
			m_pSystem.doInformation("Waiting for hsqldb to start...", this);
			//importPMX();
			SETUP.doSetup(m_pSystem, this);
			m_pSystem.retryCreateDBLoggingPool();
			while(!addInShouldQuit())
			{
				m_bAddInRunning=true;
				try{Thread.sleep(1000);}catch(Exception e){}

				m_pStatus.setStatus("Running.");
			}//end while           
		}
		catch(Exception e)
		{
			m_pSystem.doError(e.toString(), this);
			puakma.util.Util.logStackTrace(e, m_pSystem, 10);
		}

		m_bAddInRunning = false;
		m_pStatus.setStatus("Shutting down");
		try{Thread.sleep(2000);}catch(Exception e){}
		waitForOtherAddIns(30);

		m_pSystem.doInformation("HSQLDB Embedded Database Shutdown", this);
		m_pSystem.closeDBLoggingPool();

		org.hsqldb.DatabaseManager.closeDatabases(0);
		//stmt.executeUpdate(”SHUTDOWN”);
		//where stmt is the java.sql.Statement you have created earlier from HSQLDB java.sql.Connection. 

		try{Thread.sleep(1000);}catch(Exception e){}
		System.gc();
		//http://blog.taragana.com/index.php/archive/how-to-close-all-connections-in-hsqldb-to-prevent-a-locking-defect/3/
		/*... But back to the point: if the .lck file does not get deleted, then there’s a 10 second 		  
		 buffer zone because the lock file is touched at 10 second intervals.
		 */
		try{Thread.sleep(10000);}catch(Exception e){}
		removeStatusLine(m_pStatus);
	}

	/**
	 *
	 */
	private void waitForOtherAddIns(int iWaitSeconds)
	{
		int iWaitCount=0;
		boolean bWaiting=true;
		while(bWaiting)
		{
			String sAddIns[] = m_pSystem.getLoadedAddInNames();
			//System.out.println("Waiting for "+ sAddIns.length+ " AddIns...");
			if(sAddIns==null || sAddIns.length<2) bWaiting = false;
			try{Thread.sleep(1000);}catch(Exception e){}
			if(iWaitCount>iWaitSeconds) bWaiting = false;
			iWaitCount++;
		}


	}

	/**
	 * Reset the default sysadmin account password
	 */
	private String resetPassword()
	{
		char cPW[] = new char[5];
		for(int i=0; i<cPW.length; i++) cPW[i] =  (char)((Math.random()*26)+'A');
		String sRawPW = new String(cPW);
		sRawPW = sRawPW.toLowerCase() + String.valueOf((int)(Math.random()*100));
		String sEncryptedPassword = puakma.util.Util.encryptString(sRawPW);

		boolean bOK = false;
		Connection cx = null;
		try
		{
			cx = m_pSystem.getSystemConnection();
			PreparedStatement prep = cx.prepareStatement("UPDATE PERSON SET Password=? WHERE UserName=?");
			prep.setString(1, sEncryptedPassword);
			prep.setString(2, FULL_USER_NAME);
			prep.execute();
			bOK = true;
		}
		catch(Exception e)
		{
			m_pSystem.doError(e.toString(), this);            
		}
		finally
		{
			m_pSystem.releaseSystemConnection(cx);
		}

		if(!bOK)
		{
			return "Password reset for '" + FULL_USER_NAME + "' failed";
		}

		m_pSystem.doInformation("Password for '" + FULL_USER_NAME + "' has been reset via console", this);
		return "Password for '" + FULL_USER_NAME + "' reset to '" + sRawPW + "'";
	}

	



	/**
	 *
	 */
	public void requestQuit()
	{      
		super.requestQuit();
		this.interrupt();    
	}


	/**
	 *
	 */
	public boolean addInReady()
	{
		return m_bAddInRunning;
	}

	/**
	 *
	 *
	 */
	public String tell(String szCommand)
	{

		if(szCommand.equalsIgnoreCase("?") || szCommand.equalsIgnoreCase("help"))
		{
			return "->pwreset\r\n"+
			"\r\n";        
		}

		if(szCommand.equalsIgnoreCase("pwreset"))
		{
			return resetPassword();         
		}


		return "";

	}

}
