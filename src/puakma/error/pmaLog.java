/** ***************************************************************
pmaLog.java
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
package puakma.error;

import puakma.jdbc.*;
import puakma.system.*;
import puakma.util.Util;
import puakma.server.AddInMessage;
import java.util.*;
import java.text.*;
import java.io.*;
import java.sql.*;

/**
 * Writes an entry to the errorlog. If the database is available, writes a row
 * in the LOG table, else to the system console
 */
public class pmaLog
{
	private pmaSystem m_pSystem;
	private String m_szDateFormat;
	private PrintWriter m_printLog;
	private SimpleDateFormat m_simpledf;
	private dbConnectionPooler m_dbPool;
	private String m_sLogFileName=null;
	private long m_ErrCount=0;
	//private String sLogReceivingAddIns[]=null;
	private Hashtable<String, String> m_htReceivingAddIns = new Hashtable<String, String>();
	private boolean m_bLogToDB=true;
	private Calendar m_calOutFile=Calendar.getInstance();
	private SimpleDateFormat m_simpledfLogfile = new SimpleDateFormat("yyyyMMdd");
	private long m_lTotalBytesWritten;
	private long m_lMaxLogSizeBytes;
	private String m_sCurrentLogFileName;

	public final static int DEBUGLEVEL_NONE=0;
	public final static int DEBUGLEVEL_MINIMAL=1;
	public final static int DEBUGLEVEL_STANDARD=2;
	public final static int DEBUGLEVEL_DETAILED=3;
	public final static int DEBUGLEVEL_VERBOSE=4;
	public final static int DEBUGLEVEL_FULL=5;

	public final static String ERROR_CHAR = "E";
	public final static String INFO_CHAR = "I";
	public final static String DEBUG_CHAR = "D";


	public pmaLog(pmaSystem paramSystem, String sDateFormat, String sLogFile, long lMaxLogFileSizeBytes)
	{
		m_pSystem = paramSystem;
		m_sLogFileName = sLogFile;
		m_sCurrentLogFileName = m_sLogFileName;

		m_lMaxLogSizeBytes = lMaxLogFileSizeBytes;

		//createLogFile(true);

		if(sDateFormat==null || sDateFormat.length()==0)
			m_szDateFormat="yyyy-MM-dd HH:mm:ss";
		else
			m_szDateFormat= sDateFormat;
		m_simpledf = new SimpleDateFormat(m_szDateFormat);

		m_dbPool = null;
		boolean bCheckDB=true;
		String sTemp = m_pSystem.getConfigProperty("NoDBCheck");
		if(sTemp==null || !sTemp.equals("1")) bCheckDB=false;

		sTemp = m_pSystem.getConfigProperty("LogNameDateFormat");
		if(sTemp!=null && sTemp.length()>0) m_simpledfLogfile = new SimpleDateFormat(sTemp);

		sTemp = m_pSystem.getConfigProperty("NoDBLog");
		if(sTemp==null || !sTemp.equals("1"))
		{
			m_bLogToDB = true;
			try
			{
				createDBPool();
				if(bCheckDB) amendTables();
			}
			catch(Exception e)
			{
				System.out.println(m_simpledf.format(new java.util.Date()) + " (I) Database Pool for logging was not created.");
				m_dbPool = null;
			}
		}
		else
			m_bLogToDB = false;        
	}

	/**
	 * Record the names of addins that want to receive log messages
	 */
	public void registerAddInToReceiveLogMessages(String sAddInName)
	{
		if(m_htReceivingAddIns.containsKey(sAddInName)) return;

		m_htReceivingAddIns.put(sAddInName, "");
	}

	/**
	 * Remove an addin from receiving log messages
	 */
	public void deregisterAddInToReceiveLogMessages(String sAddInName)
	{
		if(!m_htReceivingAddIns.containsKey(sAddInName)) return;

		m_htReceivingAddIns.remove(sAddInName);
	}

	/**
	 *
	 */
	private void createDBPool() throws Exception
	{
		m_dbPool = new dbConnectionPooler(10, 10000, 1,
				1800, m_pSystem.getSystemDBDriver(), m_pSystem.getSystemDBURL(),
				m_pSystem.getSystemDBUserName(), m_pSystem.getSystemDBPassword(), new SystemContext(m_pSystem) );

	}

	/**
	 * Called externally to try to recreate the database pool.
	 */
	public void retryCreateDBLoggingPool()
	{
		if(m_dbPool==null)
		{
			try
			{
				createDBPool();
				m_bLogToDB = true;
			}
			catch(Exception e){}
		}
	}

	/**
	 * Called externally to try to recreate the database pool.
	 */
	public void closeDBLoggingPool()
	{
		if(m_dbPool!=null) 
		{
			m_bLogToDB = false;
			m_dbPool.shutdown();
			m_dbPool = null;          
		}      
	}

	/**
	 * This function adds a new column to the pmaLog table "UserName" as "User" is a
	 * postgresql reserved word
	 *
	 */
	private void amendTables()
	{
		if(true) return; //BJU commented to improve server start time...
		Connection cx=null;
		if(m_dbPool!=null)
		{
			try
			{
				cx = m_dbPool.getConnection();
				Statement Stmt = cx.createStatement();

				//Added 19/6/2003
				ResultSet rs = Stmt.executeQuery("SELECT * FROM PMALOG");
				rs.next();
				if(!puakma.util.Util.resultSetHasColumn(rs, "UserName"))
				{
					Stmt.execute("ALTER TABLE PMALOG ADD COLUMN UserName VARCHAR(120)");
					Stmt.execute("UPDATE PMALOG SET UserName=User");
					Stmt.execute("ALTER TABLE PMALOG DROP COLUMN User");
				}
				//Added 24/9/04 to allow for multiple servers sharing the same db instance
				if(!puakma.util.Util.resultSetHasColumn(rs, "ServerName"))
				{
					Stmt.execute("ALTER TABLE PMALOG ADD COLUMN ServerName VARCHAR(255)");
				}
				rs = Stmt.executeQuery("SELECT * FROM HTTPSTAT");
				rs.next();
				if(!puakma.util.Util.resultSetHasColumn(rs, "ServerName"))
				{
					Stmt.execute("ALTER TABLE HTTPSTAT ADD COLUMN ServerName VARCHAR(255)");
				}
				rs = Stmt.executeQuery("SELECT * FROM HTTPSTATIN");
				rs.next();
				if(!puakma.util.Util.resultSetHasColumn(rs, "ServerName"))
				{
					Stmt.execute("ALTER TABLE HTTPSTATIN ADD COLUMN ServerName VARCHAR(255)");
				}

				//Added 19/6/2003
				rs = Stmt.executeQuery("SELECT * FROM PMATABLE");
				rs.next();
				if(!puakma.util.Util.resultSetHasColumn(rs, "BuildOrder"))
				{
					Stmt.execute("ALTER TABLE PMATABLE ADD COLUMN BuildOrder INTEGER");
				}

				//Added 30/9/2003
				rs = Stmt.executeQuery("SELECT * FROM KEYWORDDATA");
				rs.next();
				if(!puakma.util.Util.resultSetHasColumn(rs, "KeywordOrder"))
				{
					Stmt.execute("ALTER TABLE KEYWORDDATA ADD COLUMN KeywordOrder INTEGER");
					Stmt.execute("UPDATE KEYWORDDATA SET KeywordOrder=0");
				}

				//Added 26/7/2004 
				rs = Stmt.executeQuery("SELECT * FROM DBCONNECTION");
				rs.next();
				if(!puakma.util.Util.resultSetHasColumn(rs, "DBURLOptions"))
				{
					Stmt.execute("ALTER TABLE DBCONNECTION ADD COLUMN DBURLOptions VARCHAR(255)");
					Stmt.execute("UPDATE DBCONNECTION SET DBURLOptions=''");
				}

				try
				{
					//this column was removed due to the performance hit incurred by
					//postgresql. Every use of the connection caused a table update
					//which makes postgres tables inefficient.
					Stmt.execute("ALTER TABLE DBCONNECTION DROP COLUMN LastUsed");
				}catch(Exception e){}

				rs.close();
				Stmt.close();
			}
			catch(Exception e)
			{
				System.out.println("Could not amend table to add new settings: " + e.toString());
			}
			m_dbPool.releaseConnection(cx);
		}
	}

	/**
	 * Creates/opens the log file
	 */
	/*private synchronized void createLogFile(boolean bAppend)
  {
    if(m_sLogFileName != null)
    {
      if(m_sLogFileName.length()!=0)
      {
        File fOut = new File(m_sLogFileName);
        if(!fOut.isDirectory())
        {
          try
          {
            if(m_printLog!=null) m_printLog.close();
            if(!fOut.exists()) fOut.createNewFile();
            m_printLog = new PrintWriter(new FileWriter(fOut.getAbsolutePath(), bAppend), true);
          }
          catch(Exception e)
          {
            System.out.println("Error creating output file: " + m_sLogFileName + " - " + e.toString());
            m_printLog = null;
          }
        }
        else
        {
          System.out.println("Log file specified is a directory. Logging to file is disabled. " + m_sLogFileName);
        }//isDirectory
      }
    }
  }*/

	/**
	 * Clears out the server log files from the RDBMS and file system
	 */
	public void clearServerLog()
	{
		Connection cx=null;
		Statement stmt = null;

		if(m_dbPool!=null)
		{
			try
			{
				cx = m_dbPool.getConnection();				
				stmt = cx.createStatement();
				stmt.execute("DELETE FROM PMALOG");				       
			}
			catch(Exception e)
			{
				System.out.println("Could not clear RDBMS log: " + e.toString());
			}
			finally
			{
				Util.closeJDBC(stmt);
				m_dbPool.releaseConnection(cx);
			}
		}
		if(m_printLog!=null)
		{
			//TODO do we bother with this now that the log can rotate daily?
			//createLogFile(false);
		}
	}

	/**
	 * @return the number of errors that have been recorded
	 */
	public long getErrorCount()
	{
		return m_ErrCount;
	}

	public synchronized void clearErrorCount()
	{
		m_ErrCount=0;
	}

	/**
	 *
	 */
	private String formatDate(java.util.Date dtIn)
	{
		return m_simpledf.format(dtIn);
	}

	private void writeLog(String szMsg, String szType, String szUser, String szSource)
	{
		Connection cx=null;

		//the database has been restarted in the background
		// or started after the db server
		if(m_dbPool==null && m_bLogToDB) retryCreateDBLoggingPool();


		if(m_dbPool==null)
		{
			try{ writeLog(szMsg, szType, szUser, szSource, null); }catch(Exception w){}
			return;
		}

		try
		{
			cx = m_dbPool.getConnection();
			writeLog(szMsg, szType, szUser, szSource, cx);      
		}
		catch(Exception e)
		{
			try
			{ 
				if(cx==null || cx.isClosed() ) 
				{
					m_dbPool.releaseConnection(cx);
					createDBPool(); 
					return;
				}
			}
			catch(Exception w){}
		}
		finally{
			m_dbPool.releaseConnection(cx);
		}
	}

	/**
	 * This version of the function allows us to write critical messages. Otherwise we get into an infinite loop
	 * if the system connection does not exist or fails
	 */
	private void writeLog(String szMsg, String szType, String szUser, String szSource, Connection cx) throws Exception
	{
		if(szMsg==null) szMsg="";

		java.util.Date dtNow=new java.util.Date();
		String szDate;
		StringBuilder sbLogMsg=new StringBuilder(100);

		szDate = formatDate(dtNow);
		sbLogMsg.append(szDate);
		sbLogMsg.append(": (");
		sbLogMsg.append(szType);
		sbLogMsg.append(") ");
		sbLogMsg.append(szMsg);
		sbLogMsg.append("  (");
		sbLogMsg.append(szUser);
		sbLogMsg.append(" - ");
		sbLogMsg.append(szSource);
		sbLogMsg.append(')');;
		System.out.println(sbLogMsg.toString());
		sendMessageToAddIn(dtNow, szMsg, szType, szUser, szSource);
		writeToTextLog(sbLogMsg.toString());

		if(cx!=null)
		{      
			String szQuery = "INSERT INTO PMALOG(LogString,LogDate,Source,UserName,Type,ServerName) VALUES(?,?,?,?,?,?)";
			PreparedStatement prepStmt = cx.prepareStatement(szQuery);
			prepStmt.setString(1, szMsg);
			prepStmt.setTimestamp(2, new Timestamp(System.currentTimeMillis()));
			prepStmt.setString(3, szSource);
			prepStmt.setString(4, szUser);
			prepStmt.setString(5, szType);
			prepStmt.setString(6, m_pSystem.SystemHostName);
			prepStmt.execute();
			prepStmt.close();      
		}
	}

	/**
	 * Writes a line of text to the log file. Rotate the log if required
	 * @param sLine
	 */
	private synchronized void writeToTextLog(String sLine)
	{

		Calendar calNow = Calendar.getInstance();

		try
		{
			//create a new log each day
			if(m_printLog==null || calNow.get(Calendar.DAY_OF_MONTH)!=m_calOutFile.get(Calendar.DAY_OF_MONTH))
			{
				String szDate = m_simpledf.format(m_calOutFile.getTime());
				m_calOutFile = Calendar.getInstance();
				szDate = m_simpledfLogfile.format(m_calOutFile.getTime());
				String sBareLog = m_sLogFileName; //m_pSystem.getSystemProperty("HTTPTextLog");
				if(sBareLog==null || sBareLog.length()==0) return;//sBareLog = "puakma.log";
				m_sCurrentLogFileName = sBareLog.replaceAll("\\*", szDate);

				//m_pSystem.doInformation("HTTP.NewWebLogFile", new String[]{szLogFile}, this);
				System.out.println("Using log file: " + m_sCurrentLogFileName);
				File fLog = new File(m_sCurrentLogFileName);	
				m_lTotalBytesWritten = fLog.length();
				boolean bAppendToFile = true;
				m_printLog = new PrintWriter(new FileWriter(fLog.getAbsolutePath(), bAppendToFile), true);	        
			}
			if(m_printLog!=null) 
			{
				int iBytesToWrite = sLine.length()+2;
				m_lTotalBytesWritten += iBytesToWrite;
				if(m_lMaxLogSizeBytes>iBytesToWrite && m_lTotalBytesWritten>m_lMaxLogSizeBytes) 
				{
					rotateLogs();
					m_lTotalBytesWritten = iBytesToWrite; //reset
				}
				m_printLog.println(sLine);
			}
		}
		catch(Exception e)
		{
			//m_pSystem.doError("HTTPServer.WriteStatLogError", new String[]{m_pSystem.getSystemProperty("HTTPTextLog"), e.getMessage()}, this);
		}
	}

	/**
	 * Take the current log file and rename it to xxxx.1, then start a new log file
	 */
	private void rotateLogs() 
	{
		System.out.println("\r\n\r\n**** ROTATING LOG [" +m_sCurrentLogFileName +"] ****\r\n\r\n");
		File fActiveLog = new File(m_sCurrentLogFileName);
		File fArchivedLog = new File(m_sCurrentLogFileName + ".1");
		if(fActiveLog.exists()) 
		{
			fArchivedLog.delete();
		}

		fActiveLog.renameTo(fArchivedLog);

		fActiveLog = new File(m_sCurrentLogFileName);
		try 
		{
			m_printLog = new PrintWriter(new FileWriter(fActiveLog.getAbsolutePath(), false), true);
		} 
		catch (IOException e) 
		{
			e.printStackTrace();
		}
	}

	/**
	 *
	 */
	private synchronized void incrementErrCount()
	{
		m_ErrCount++;
	}

	/**
	 * This handles the casting in case someone passes in an object that does not implement 
	 * an ErrorDetect interface
	 * @param objSource
	 * @return a String array which includes the Source of the error and the User
	 */
	private String[] getErrorSourceUser(Object objSource)
	{
		String sSourceUser[] = new String[2];


		if(objSource==null) objSource = m_pSystem;
		try
		{
			ErrorDetect errDetect = (ErrorDetect)objSource; //possible classcastexception
			sSourceUser[0] = errDetect.getErrorSource();
			sSourceUser[1] = errDetect.getErrorUser();
		}
		catch(Exception e)
		{ 
			sSourceUser[0] = m_pSystem.getErrorSource();
			sSourceUser[1] = m_pSystem.getErrorUser();
		}

		return sSourceUser;
	}


	/**
	 * Messages are not written to the RDBMS.
	 */
	public void doConnectionlessError(String szErrCode, String szParams[], Object objSource)
	{
		incrementErrCount();
		String sSourceUser[] = getErrorSourceUser(objSource);
		/*if(objSource==null) objSource = m_pSystem;
		ErrorDetect errDetect = (ErrorDetect)objSource;*/
		String szError = m_pSystem.getSystemMessageString(szErrCode);
		szError = parseMessage(szError, szParams);
		try{ writeLog(szError, ERROR_CHAR, sSourceUser[1], sSourceUser[0], null); }catch(Exception w){}
	}


	/**
	 *
	 */
	public void doError(String szErrCode, String szParams[], Object objSource)
	{
		incrementErrCount();
		String sSourceUser[] = getErrorSourceUser(objSource);
		/*if(objSource==null) objSource = m_pSystem;
		ErrorDetect errDetect = (ErrorDetect)objSource;*/
		String szError="";
		if(szErrCode==null)
			szError = String.valueOf(szErrCode);
		else
			szError = m_pSystem.getSystemMessageString(szErrCode);

		szError = parseMessage(szError, szParams);
		writeLog(szError, ERROR_CHAR, sSourceUser[1], sSourceUser[0]);
	}

	/**
	 *
	 */
	public void doError(String szErrCode, Object objSource)
	{
		doError(szErrCode, null, objSource);
	}

	/**
	 *
	 */
	public void doInformation(String szErrCode, String szParams[], Object objSource)
	{
		//if(objSource==null) objSource = m_pSystem;
		//ErrorDetect errDetect = (ErrorDetect)objSource;
		String sSourceUser[] = getErrorSourceUser(objSource);
		String szInfo="";
		if(szErrCode==null)
			szInfo = String.valueOf(szErrCode);
		else
			szInfo = m_pSystem.getSystemMessageString(szErrCode);
		szInfo = parseMessage(szInfo, szParams);
		writeLog(szInfo, INFO_CHAR, sSourceUser[1], sSourceUser[0]);
	}


	/**
	 *
	 */
	public void doInformation(String szErrCode, Object objSource)
	{
		doInformation(szErrCode, null, objSource);
	}


	/**
	 * Writes debug information to the log if the debug level is <= the system
	 * threshold. The higher the debug level the more detailed the messages should be
	 */
	public void doDebug(int iDebugLevel, String szErrCode, String szParams[], Object objSource)
	{
		if(iDebugLevel <= m_pSystem.getDebugLevel())
		{
			//if(objSource==null) objSource = m_pSystem;
			//ErrorDetect errDetect = (ErrorDetect)objSource;
			String sSourceUser[] = getErrorSourceUser(objSource);
			String szInfo="";
			if(szErrCode==null)
				szInfo = String.valueOf(szErrCode);
			else
				szInfo = m_pSystem.getSystemMessageString(szErrCode);
			//String szInfo = m_pSystem.propMessages.getProperty(szErrCode, szErrCode);
			szInfo = parseMessage(szInfo, szParams);
			writeLog(szInfo, DEBUG_CHAR, sSourceUser[1], sSourceUser[0]);
		}
	}

	public void doDebug(int iDebugLevel, String szErrCode, Object objSource)
	{
		doDebug(iDebugLevel, szErrCode, null, objSource);
	}



	/**
	 * This function replaces the %s with the array of sParams. This function is static so you can call it from anywhere to create your
	 * own parameter driven message strings.
	 * @param sMessage The message string containing the %s placeholders
	 * @param sParams the list of string replacements
	 * @return a String with all the %s replaced
	 * 
	 */
	public static String parseMessage(String sMessage, String sParams[])
	{     
		int i, k;
		StringBuilder sbNew = new StringBuilder(256);

		if(sParams==null) return sMessage;
		if(sMessage==null) return "";

		i=sMessage.indexOf("%s");
		if(i >= 0)
		{      
			k=0;
			//szNew="";
			while(i>=0)
			{
				sbNew.append(sMessage.substring(0, i));
				sMessage = sMessage.substring(i+2);
				if(sParams.length > k)
					sbNew.append(sParams[k]);
				else
					sbNew.append("#?#");
				k=k+1;
				i=sMessage.indexOf("%s");
			}
			sbNew.append(sMessage);
			return sbNew.toString();
		}

		return sMessage;
	}

	/**
	 * Check if there is a registered add ot receive log messages, if so, send it the message
	 */
	private void sendMessageToAddIn(java.util.Date dtNow, String szMsg, String szType, String szUser, String szSource)
	{
		if(m_htReceivingAddIns.size()==0) return;

		Enumeration<String> en = m_htReceivingAddIns.keys();
		while(en.hasMoreElements())
		{
			String sAddInClass = (String)en.nextElement();
			if(m_pSystem.isAddInLoaded(sAddInClass))
			{
				AddInMessage oMessage = new AddInMessage();
				oMessage.setObject("Date", dtNow);
				oMessage.setParameter("Message", szMsg);
				oMessage.setParameter("Type", szType);
				oMessage.setParameter("User", szUser);
				oMessage.setParameter("Source", szSource);
				m_pSystem.sendMessage(sAddInClass, oMessage);
			}
		}      
	}

}//class