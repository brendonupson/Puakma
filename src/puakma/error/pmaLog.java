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
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Writes an entry to the errorlog. If the database is available, writes a row
 * in the LOG table, else to the system console.
 * <p>
 * Callers only format the message and put it on a bounded queue; a single background thread does the 
 * console, log file, add-in and database work (the database in batches), so logging never blocks the 
 * calling (request) thread. If the queue fills up, DEBUG messages are dropped first, then everything, and the 
 * number dropped is reported. On JVM shutdown the queue is drained; after that messages are written directly.
 */
public class pmaLog
{
	private pmaSystem m_pSystem;
	private String m_szDateFormat;
	private Writer m_printLog; //only touched by writeBatch(), which is synchronized
	private SimpleDateFormat m_simpledf; //only used by writeBatch() after construction
	private volatile DbConnectionPooler m_dbPool;
	private String m_sLogFileName=null;
	private final AtomicLong m_ErrCount = new AtomicLong(0);
	private ConcurrentHashMap<String, String> m_htReceivingAddIns = new ConcurrentHashMap<String, String>();
	private volatile boolean m_bLogToDB=true;
	private java.time.LocalDate m_dayOutFile = null;
	private long m_lNextLogFileAttempt = 0;
	private long m_lNextDBAttempt = 0;
	private SimpleDateFormat m_simpledfLogfile = new SimpleDateFormat("yyyyMMdd");
	private long m_lTotalBytesWritten;
	private long m_lMaxLogSizeBytes;
	private String m_sCurrentLogFileName;

	private static final int QUEUE_CAPACITY = 20000;
	private static final int MAX_BATCH = 200;
	private static final long RETRY_INTERVAL_MS = 10000;
	private static final String LINE_SEP = System.lineSeparator();

	private static class LogRecord
	{
		final long time = System.currentTimeMillis();
		final String msg, type, user, source;
		LogRecord(String msg, String type, String user, String source)
		{
			this.msg = (msg==null) ? "" : msg;
			this.type = type;
			this.user = user;
			this.source = source;
		}
	}

	private final ArrayBlockingQueue<LogRecord> m_queue = new ArrayBlockingQueue<LogRecord>(QUEUE_CAPACITY);
	private final AtomicLong m_lDropped = new AtomicLong(0);
	private long m_lReportedDropped = 0; //writeBatch() only
	private long m_lLastDropReport = 0; //writeBatch() only
	private volatile boolean m_bAsync = true;
	private Thread m_writerThread;

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

		startWriter();
	}

	private void startWriter()
	{
		m_writerThread = new Thread(new Runnable(){ public void run(){ writerLoop(); } }, "pmaLogWriter");
		m_writerThread.setDaemon(true);
		m_writerThread.start();
		try
		{
			Runtime.getRuntime().addShutdownHook(new Thread(new Runnable(){ public void run(){ shutdown(3000); } }, "pmaLogShutdown"));
		}
		catch(Exception e){} //JVM is already shutting down
	}

	/**
	 * Write out everything that is queued and stop the background thread. Anything logged afterwards 
	 * is written directly by the calling thread. Waits up to lWaitMS.
	 */
	public void shutdown(long lWaitMS)
	{
		m_bAsync = false;
		Thread t = m_writerThread;
		if(t!=null)
		{
			t.interrupt();
			try{ t.join(lWaitMS); }catch(InterruptedException e){ Thread.currentThread().interrupt(); }
		}
		drainQueue(false); //in case the writer thread ran out of time
	}

	private void writerLoop()
	{
		ArrayList<LogRecord> batch = new ArrayList<LogRecord>(MAX_BATCH);
		while(m_bAsync)
		{
			try
			{
				LogRecord first = m_queue.take();
				batch.add(first);
				m_queue.drainTo(batch, MAX_BATCH-1);
				writeBatch(batch, true);
			}
			catch(InterruptedException e)
			{
				break; //shutdown()
			}
			catch(Throwable t)
			{
				System.err.println("pmaLog writer error: " + t);
			}
			finally
			{
				batch.clear();
			}
		}
		Thread.interrupted();
		drainQueue(true);
	}

	private void drainQueue(boolean bToDB)
	{
		ArrayList<LogRecord> batch = new ArrayList<LogRecord>(MAX_BATCH);
		while(m_queue.drainTo(batch, MAX_BATCH)>0)
		{
			try{ writeBatch(batch, bToDB); }catch(Throwable t){}
			batch.clear();
		}
	}

	/**
	 * Hand the record to the writer thread without ever blocking. 
	 */
	private void enqueue(LogRecord rec)
	{
		if(!m_bAsync) //shutting down, write it directly
		{
			ArrayList<LogRecord> one = new ArrayList<LogRecord>(1);
			one.add(rec);
			writeBatch(one, false);
			return;
		}
		//keep the last quarter of the queue for errors and information
		if(DEBUG_CHAR.equals(rec.type) && m_queue.remainingCapacity()<QUEUE_CAPACITY/4)
		{
			m_lDropped.incrementAndGet();
			return;
		}
		if(!m_queue.offer(rec)) m_lDropped.incrementAndGet();
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
		m_dbPool = new DbConnectionPooler(10, 10000, 1,
				1800, m_pSystem.getSystemDBDriver(), m_pSystem.getSystemDBURL(),
				m_pSystem.getSystemDBUserName(), m_pSystem.getSystemDBPassword(), new SystemContext(m_pSystem) );

	}

	/**
	 * Called externally to try to recreate the database pool.
	 */
	public synchronized void retryCreateDBLoggingPool()
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
	public synchronized void closeDBLoggingPool()
	{
		DbConnectionPooler pool = m_dbPool;
		if(pool!=null) 
		{
			m_bLogToDB = false;
			m_dbPool = null;          
			pool.shutdown();
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
			finally
			{
				m_dbPool.releaseConnection(cx);
			}
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

		DbConnectionPooler pool = m_dbPool;
		if(pool!=null)
		{
			try
			{
				cx = pool.getConnection();				
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
				pool.releaseConnection(cx);
			}
		}
	}

	/**
	 * @return the number of errors that have been recorded
	 */
	public long getErrorCount()
	{
		return m_ErrCount.get();
	}

	public void clearErrorCount()
	{
		m_ErrCount.set(0);
	}

	private void writeLog(String szMsg, String szType, String szUser, String szSource)
	{
		enqueue(new LogRecord(szMsg, szType, szUser, szSource));
	}

	/**
	 * Does the real work on the writer thread (or the calling thread once shut down): console, add-ins and 
	 * log file for each record, one flush, then one database batch.
	 */
	private synchronized void writeBatch(ArrayList<LogRecord> batch, boolean bToDB)
	{
		java.util.Date dt = new java.util.Date();
		long lDropped = m_lDropped.get();
		if(lDropped!=m_lReportedDropped && (System.currentTimeMillis()-m_lLastDropReport>10000 || !m_bAsync))
		{
			batch.add(new LogRecord((lDropped-m_lReportedDropped) + " log messages were dropped because the log queue was full", ERROR_CHAR, "", "pmaLog"));
			m_lReportedDropped = lDropped;
			m_lLastDropReport = System.currentTimeMillis();
		}

		StringBuilder sb = new StringBuilder(256);
		for(int i=0; i<batch.size(); i++)
		{
			LogRecord rec = batch.get(i);
			dt.setTime(rec.time);
			sb.setLength(0);
			sb.append(m_simpledf.format(dt));
			sb.append(": (").append(rec.type).append(") ").append(rec.msg);
			sb.append("  (").append(rec.user).append(" - ").append(rec.source).append(')');
			String sLine = sb.toString();
			System.out.println(sLine);
			try{ sendMessageToAddIn(new java.util.Date(rec.time), rec.msg, rec.type, rec.user, rec.source); }catch(Throwable t){}
			writeToTextLog(sLine);
		}
		flushTextLog();
		if(bToDB) writeBatchToDB(batch);
	}

	private void writeBatchToDB(ArrayList<LogRecord> batch)
	{
		if(!m_bLogToDB || batch.size()==0) return;
		long lNow = System.currentTimeMillis();
		if(lNow<m_lNextDBAttempt) return; //the DB was failing, the message is already in the console and log file

		DbConnectionPooler pool = m_dbPool;
		if(pool==null) 
		{
			//the database has been restarted in the background or started after the db server
			m_lNextDBAttempt = lNow + RETRY_INTERVAL_MS;
			retryCreateDBLoggingPool();
			pool = m_dbPool;
			if(pool==null) return;
			m_lNextDBAttempt = 0;
		}

		Connection cx = null;
		PreparedStatement prepStmt = null;
		try
		{
			cx = pool.getConnection();
			prepStmt = cx.prepareStatement("INSERT INTO PMALOG(LogString,LogDate,Source,UserName,Type,ServerName) VALUES(?,?,?,?,?,?)");
			for(int i=0; i<batch.size(); i++)
			{
				LogRecord rec = batch.get(i);
				prepStmt.setString(1, rec.msg);
				prepStmt.setTimestamp(2, new Timestamp(rec.time));
				prepStmt.setString(3, rec.source);
				prepStmt.setString(4, rec.user);
				prepStmt.setString(5, rec.type);
				prepStmt.setString(6, m_pSystem.SystemHostName);
				prepStmt.addBatch();
			}
			prepStmt.executeBatch();
		}
		catch(Exception e)
		{
			m_lNextDBAttempt = System.currentTimeMillis() + RETRY_INTERVAL_MS;
			try
			{
				if(cx==null || cx.isClosed()) 
				{
					//connection is dead. Drop the pool, it is recreated on the next attempt. 
					synchronized(this){ if(m_dbPool==pool) m_dbPool = null; }
					pool.shutdown();
					cx = null;
				}
			}
			catch(Exception w){}
		}
		finally
		{
			Util.closeJDBC(prepStmt);
			if(cx!=null) pool.releaseConnection(cx);
		}
	}

	/**
	 * Writes a line of text to the log file. Rotate the log if required. Not flushed until flushTextLog()
	 */
	private void writeToTextLog(String sLine)
	{
		try
		{
			//create a new log each day
			java.time.LocalDate today = java.time.LocalDate.now();
			if(m_printLog==null || !today.equals(m_dayOutFile))
			{
				long lNow = System.currentTimeMillis();
				if(lNow<m_lNextLogFileAttempt) return; //couldn't open the file recently
				String sBareLog = m_sLogFileName;
				if(sBareLog==null || sBareLog.length()==0) return;
				closeTextLog();
				m_lNextLogFileAttempt = lNow + RETRY_INTERVAL_MS; //cleared when the open works
				String szDate = m_simpledfLogfile.format(new java.util.Date(lNow));
				m_sCurrentLogFileName = sBareLog.replace("*", szDate);

				System.out.println("Using log file: " + m_sCurrentLogFileName);
				File fLog = new File(m_sCurrentLogFileName);	
				m_lTotalBytesWritten = fLog.length();
				m_printLog = new BufferedWriter(new FileWriter(fLog.getAbsolutePath(), true), 16384);
				m_dayOutFile = today;
				m_lNextLogFileAttempt = 0;
			}
			int iBytesToWrite = sLine.length()+2;
			m_lTotalBytesWritten += iBytesToWrite;
			if(m_lMaxLogSizeBytes>iBytesToWrite && m_lTotalBytesWritten>m_lMaxLogSizeBytes) 
			{
				rotateLogs();
				m_lTotalBytesWritten = iBytesToWrite; //reset
			}
			if(m_printLog!=null)
			{
				m_printLog.write(sLine);
				m_printLog.write(LINE_SEP);
			}
		}
		catch(Exception e)
		{
			//can't log a logging failure. Close so it is reopened (after a pause) rather than failing on every message
			closeTextLog();
			m_lNextLogFileAttempt = System.currentTimeMillis() + RETRY_INTERVAL_MS;
		}
	}

	private void flushTextLog()
	{
		if(m_printLog==null) return;
		try{ m_printLog.flush(); }
		catch(Exception e)
		{ 
			closeTextLog(); 
			m_lNextLogFileAttempt = System.currentTimeMillis() + RETRY_INTERVAL_MS;
		}
	}

	private void closeTextLog()
	{
		Writer w = m_printLog;
		m_printLog = null;
		if(w!=null) try{ w.close(); }catch(Exception e){}
	}

	/**
	 * Take the current log file and rename it to xxxx.1, then start a new log file
	 */
	private void rotateLogs() throws IOException
	{
		System.out.println("\r\n\r\n**** ROTATING LOG [" +m_sCurrentLogFileName +"] ****\r\n\r\n");
		closeTextLog(); //release the file before renaming (required on Windows)
		File fActiveLog = new File(m_sCurrentLogFileName);
		File fArchivedLog = new File(m_sCurrentLogFileName + ".1");
		if(fActiveLog.exists()) 
		{
			fArchivedLog.delete();
		}

		fActiveLog.renameTo(fArchivedLog);

		fActiveLog = new File(m_sCurrentLogFileName);
		m_printLog = new BufferedWriter(new FileWriter(fActiveLog.getAbsolutePath(), false), 16384);
	}

	/**
	 *
	 */
	private void incrementErrCount()
	{
		m_ErrCount.incrementAndGet();
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
		ErrorDetect errDetect = (objSource instanceof ErrorDetect) ? (ErrorDetect)objSource : m_pSystem;
		sSourceUser[0] = errDetect.getErrorSource();
		sSourceUser[1] = errDetect.getErrorUser();

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
		writeLog(szError, ERROR_CHAR, sSourceUser[1], sSourceUser[0]);
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
		if(sParams==null) return sMessage;
		if(sMessage==null) return "";

		int i = sMessage.indexOf("%s");
		if(i<0) return sMessage;

		StringBuilder sbNew = new StringBuilder(sMessage.length() + 64);
		int iFrom = 0;
		int k = 0;
		while(i>=0)
		{
			sbNew.append(sMessage, iFrom, i);
			if(sParams.length > k)
				sbNew.append(sParams[k]);
			else
				sbNew.append("#?#");
			k++;
			iFrom = i+2;
			i = sMessage.indexOf("%s", iFrom);
		}
		sbNew.append(sMessage, iFrom, sMessage.length());
		return sbNew.toString();
	}

	/**
	 * Check if there is a registered add ot receive log messages, if so, send it the message
	 */
	private void sendMessageToAddIn(java.util.Date dtNow, String szMsg, String szType, String szUser, String szSource)
	{
		if(m_htReceivingAddIns.size()==0) return;

		for(String sAddInClass : m_htReceivingAddIns.keySet())
		{
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