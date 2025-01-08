/** ***************************************************************
SystemContext.java
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

package puakma.system;

import java.io.File;
import java.net.InetAddress;
import java.sql.Connection;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Vector;

import puakma.addin.http.TornadoApplication;
import puakma.addin.http.TornadoServer;
import puakma.addin.http.TornadoServerInstance;
import puakma.addin.http.action.SharedActionClassLoader;
import puakma.error.ErrorDetect;
import puakma.server.AddInMessage;

/**
 * The SystemContext is used to protect the system class from programmers who may
 * 'accidentally' set it to null or destroy some system properties.
 *
 * To create a new one the system class must be specified. A new DBPool Manager is created if
 * null is specified. The object may be cloned so that new instances may be passed down without
 * damaging the original. To clone:
 * SystemContext clonedSysCtx = SysCtx.clone(DBPoolManager); //use the specified pool manager
 * SystemContext clonedSysCtx = SysCtx.clone(null); //create a new pool manager inside
 */
public class SystemContext implements ErrorDetect,Cloneable
{
	private pmaSystem m_pSystem;
	private long m_UniqueNumber=0; //do NOT access this property!! see getNextUniqueNumber()

	private int m_iSystemConnGetCount=0;
	private int m_iSystemConnReleaseCount=0;
	private Hashtable<Connection, Connection> m_htConnections = new Hashtable<Connection, Connection>();


	public final static String DBALIAS_SYSTEM = "~DB_SYSTEM~";
	public final static int MAX_CONNECTIONS = 50;
	public final static int CONN_TIMEOUT = 5000; //ms
	public final static int CONN_EXPIRY = 60; //s was 1800s. 
	/*
	 * I figure if we're only getting a connection every 60 seconds, 
	 * it's a low use server so the overhead of getting a fresh 
	 * db connection is ok 
	 */
	/*
	private int m_iMaxConnectionCount = MAX_CONNECTIONS;
	private int m_iPoolConnectionTimeoutMS = CONN_TIMEOUT;
	private int m_iPoolConnectionExpireSeconds = CONN_EXPIRY;
	 */
	/*public SystemContext(pmaSystem paramSystem, dbConnectionPoolManager paramDBPoolMgr)
	{
		init(paramSystem);
		setDBPoolManager(paramDBPoolMgr);
	}*/

	public SystemContext(pmaSystem paramSystem)
	{		
		init(paramSystem);
	}

	/**
	 * Object initialisation routines
	 * @param paramSystem
	 */
	private void init(pmaSystem paramSystem)
	{
		m_pSystem = paramSystem;
	}

	/**
	 * This method overloads the default clone method os that a new context may be
	 * created.
	 */
	public synchronized Object clone()
	{
		//SystemContext ContextCopy = null;
		try
		{
			//ContextCopy = (SystemContext)super.clone();
			//return ContextCopy;
			return new SystemContext(m_pSystem);
		}
		catch(Exception e){}

		return null;
	}


	/**
	 * Called to tell the logging mechanism to try to reconnect to the database, 
	 * if it is configured to do so.
	 */
	public void retryCreateDBLoggingPool()
	{
		m_pSystem.pErr.retryCreateDBLoggingPool();
	}

	public void closeDBLoggingPool()
	{
		m_pSystem.pErr.closeDBLoggingPool();
	}


	/**
	 * Check for any open connections
	 *
	 */
	public void finalize()
	{		
		int iOpenConnections = m_htConnections.size();
		if(m_iSystemConnGetCount!=m_iSystemConnReleaseCount &&
				iOpenConnections>0)//m_htConnections.size()>0)
		{						
			//force them all to be cleared....
			Enumeration<Connection> en = m_htConnections.keys();
			while(en.hasMoreElements())
			{
				Connection cx = (Connection)en.nextElement();
				m_htConnections.remove(cx);
				releaseSystemConnection(cx);				
			}
			//doError("pmaSystem.ReleaseConnection", new String[]{""+iOpenConnections, "SYSTEM"}, this);
		}

	}


	


	public String getDBPoolStatus()
	{
		StringBuilder sb = new StringBuilder(128);

		sb.append(m_pSystem.getDBPoolStatus());
		TornadoServerInstance tsi = TornadoServer.getInstance();
		Hashtable<String, TornadoApplication> htApp = tsi.getAllLoadedApplications();
		Iterator<TornadoApplication> it = htApp.values().iterator();
		while(it.hasNext())
		{
			TornadoApplication ta = (TornadoApplication) it.next();
			sb.append(ta.getDBPoolStatus());
		}

		return sb.toString();
	}

	

	/**
	 * Can the named address access this server?
	 */
	public boolean addressHasAccess(String sIPAddress)
	{
		return m_pSystem.addressHasAccess(sIPAddress);
	}

	/**
	 * Used to stop the system object
	 */
	public void stopSystem()
	{
		m_pSystem.stopSystem();
	}

	/**
	 * resets the system error counter
	 */
	public void clearErrorCount()
	{
		m_pSystem.clearErrorCount();
	}



	/**
	 * Gets an entry from the puakma.config file
	 */
	public String getSystemProperty(String szPropertyName)
	{
		return m_pSystem.getConfigProperty(szPropertyName);
	}


	/**
	 * Gets an entry from the msg_xx.lang file
	 */
	public String getSystemMessageString(String szPropertyName)
	{
		return m_pSystem.getSystemMessageString(szPropertyName);
	}

	/**
	 * The full build info of the current system, includes date, build#, version
	 * Usually used for display purposes.
	 */
	public String getVersionString()
	{
		return m_pSystem.getVersionString();
	}

	public String getVersion()
	{
		return m_pSystem.getVersion();
	}

	/**
	 * The build of the current system
	 */
	public int getBuild()
	{
		return m_pSystem.getBuild();
	}


	/**
	 * @return a vector of all SessionContext objects
	 */
	public Vector<SessionContext> getAllSessions()
	{
		Vector<SessionContext> v = new Vector<SessionContext>();
		try
		{
			Enumeration<pmaSession> eSess = m_pSystem.getSessionList();
			while(eSess.hasMoreElements())
			{
				pmaSession sess = (pmaSession)eSess.nextElement();
				SessionContext SessCtx = new SessionContext(sess);
				v.addElement(SessCtx);
			}
			return v;
		}
		catch(Exception e)
		{
			return v;
		}
	}

	/**
	 * The date & time the server was started
	 */
	public java.util.Date getSystemStartTime()
	{
		return new java.util.Date(m_pSystem.dtStart.getTime());
	}



	/**
	 * The name of the system host, ie: joe.acme.com
	 */
	public String getSystemHostName()
	{
		return m_pSystem.SystemHostName;
	}

	/**
	 * The file object showing where the temp directory is
	 */
	public File getTempDir()
	{
		return m_pSystem.getTempDir();
	}

	public File getConfigDir()
	{
		return m_pSystem.getConfigDir();
	}

	/**
	 * Get a system-wide unique number
	 */
	public long getSystemUniqueNumber()
	{
		return m_pSystem.getNextUniqueNumber();
	}

	/**
	 * Get a context unique number
	 */
	public long getUniqueNumber()
	{
		return m_UniqueNumber++;
	}


	/**
	 * Log errors...
	 */
	public void doError(String szErrCode, Object objSource)
	{
		m_pSystem.pErr.doError(szErrCode, objSource);
	}


	/**
	 * Log errors...
	 */
	public void doError(String szErrCode, String[] szParams, Object objSource)
	{
		m_pSystem.pErr.doError(szErrCode, szParams, objSource);
	}


	/**
	 * Log information...
	 */
	public void doInformation(String szErrCode, Object objSource)
	{
		m_pSystem.pErr.doInformation(szErrCode, objSource);
	}


	/**
	 * Log information...
	 */
	public void doInformation(String szErrCode, String[] szParams, Object objSource)
	{
		m_pSystem.pErr.doInformation(szErrCode, szParams, objSource);
	}


	/**
	 * Log debug...
	 */
	public void doDebug(int iDebugLevel, String szErrCode, Object objSource)
	{
		m_pSystem.pErr.doDebug(iDebugLevel, szErrCode, objSource);
	}


	/**
	 * Log debug...
	 */
	public void doDebug(int iDebugLevel, String szErrCode, String[] szParams, Object objSource)
	{
		m_pSystem.pErr.doDebug(iDebugLevel, szErrCode, szParams, objSource);
	}

	public int getDebugLevel()
	{
		return m_pSystem.getDebugLevel();
	}


	/**
	 * Releases a system database connection from the database pool
	 * @param cx
	 */
	public void releaseSystemConnection(Connection cx)
	{    
		if(cx==null) return;
		
		m_htConnections.remove(cx);
		m_iSystemConnReleaseCount++;
		m_pSystem.releaseSystemConnection(cx);
	}

	/**
	 * 
	 * @param sPath
	 * @param sessCtx
	 */
	public void checkConnections(String sPath, SessionContext sessCtx)
	{
		if(m_iSystemConnGetCount!=m_iSystemConnReleaseCount)
			m_pSystem.pErr.doError("System connections may not be released correctly. Check your source code. "+sPath, sessCtx);

		//if(m_iDataConnGetCount!=m_iDataConnReleaseCount)
		//	m_pSystem.pErr.doError("Data connections may not be released correctly. Check your source code. "+sPath, sessCtx);
	}

	/**
	 * Useful for killing and resetablishing a database pool
	 * @param sAppPath
	 */
	public void resetDatabasePool(String sAppPath)
	{
		TornadoServerInstance tsi = TornadoServer.getInstance();
		TornadoApplication ta = tsi.getTornadoApplication(sAppPath);		
		ta.resetDatabasePool();
	}
	/**
	 * Releases an application specific data connection
	 * @param cx
	 * @deprecated This method should be removed
	 */
	public void releaseDataConnection(Connection cx)
	{   
		//cycle through the loaded apps and see if any of them have this connection
		TornadoServerInstance tsi = TornadoServer.getInstance();
		Hashtable<String, TornadoApplication> htApp = tsi.getAllLoadedApplications();
		Iterator<TornadoApplication> it = htApp.values().iterator();
		while(it.hasNext())
		{
			TornadoApplication ta = (TornadoApplication) it.next();
			if(ta.releaseDataConnection(cx)) return;
		}
		
		/*if(cx==null) return;
		try
		{
			m_DBPoolMgr.releaseConnection(cx);
			//m_iDataConnReleaseCount++;
			m_htConnections.remove(cx);			      
		}
		catch(Exception e)
		{
			m_pSystem.pErr.doError("releaseDataConnection() " + e.toString(), this);
		}*/
	}

	/**
	 * Gets a handle to the system connection object.
	 */
	public Connection getSystemConnection() throws Exception
	{				
		m_iSystemConnGetCount++;
		Connection cx = m_pSystem.getSystemConnection();
		m_htConnections.put(cx, cx);
		return cx;
	}

	/**
	 * Removes a pool form the pool manager.
	 * @param sPoolAlias typically the database URL + database name, eg "jdbc:mysql://localhost:3306/somedb"
	 * @return returns true if a pool was removed. If the pool does not exist or removal fails will return false
	 * @deprecated
	 */
	/*public boolean removeDatabasePool(String sPoolAlias)
	{
		return m_DBPoolMgr.removePooler(sPoolAlias); 
	}*/

	/**
	 * Gets a handle to a connection object.
	 * This version opens the connection by name
	 * @deprecated
	 */
	/*public Connection getDataConnection(String sDriverClass, String sDBURL, String sDBURLOptions, String sDBName, String sDBUserName, String sDBPassword) throws Exception
	{
		Connection cx=null;
		String sFullURL = sDBURL + sDBName;
		if(sDBURLOptions!=null && sDBURLOptions.length()>0) sFullURL += sDBURLOptions;

		//this.doDebug(0, "Getting connection to server [" + sFullURL + "] [" + sDriverClass + "] [" + sDBURL + "] ["+ sDBUserName + "]/[" + sDBPassword + "]", this);

		try
		{
			//if no db name is specified we want a server connection. Chances are, we've called this before with bad credentials
			//especially if the webdesign dbconnection page has been visited. So for server only connections, remove any old pools and create a new one with new credentials
			if(sDBName==null || sDBName.length()==0)
			{
				//this.doDebug(0, "Getting connection to server only [" + sFullURL + "] " + sDriverClass + " " + sDBURL + " "+ sDBUserName + "/" + sDBPassword, this);
				m_DBPoolMgr.removePooler(sFullURL); //this may return false, if the pool does not exist					
				m_DBPoolMgr.createPooler(sFullURL, m_iMaxConnectionCount, m_iPoolConnectionTimeoutMS, 0,
						m_iPoolConnectionExpireSeconds, sDriverClass, sFullURL,
						sDBUserName, sDBPassword );
			}
			else
			{
				//if the pool does not exist, try to create one.
				if(!m_DBPoolMgr.hasPool(sFullURL))
				{
					m_DBPoolMgr.createPooler(sFullURL, m_iMaxConnectionCount, m_iPoolConnectionTimeoutMS, 0,
							m_iPoolConnectionExpireSeconds, sDriverClass, sFullURL,
							sDBUserName, sDBPassword );
				}
			}

			cx = m_DBPoolMgr.getConnection(sFullURL);
			m_htConnections.put(cx, sDBName);
			m_iDataConnGetCount++;
		}
		catch(Exception e)
		{
			String szMessage = pmaLog.parseMessage(getSystemMessageString("pmaSystem.NoDataConnection"), new String[]{e.getMessage()});
			throw new Exception(szMessage);
		}

		return cx;
	}*/

	/**
	 * Used to decode ad hoc values. These are things like passwords stored in the DB.
	 */
	public String decodeValue(String sProperty)
	{
		return m_pSystem.decodeValue(sProperty);
	}

	/**
	 * Used to encode ad hoc values. These are things like passwords stored in the DB.
	 */
	public String encodeValue(String sProperty)
	{
		return m_pSystem.encodeValue(sProperty);
	}

	/**
	 * Determines if the system is running or not
	 */
	public boolean isSystemRunning()
	{
		return m_pSystem.isSystemRunning();
	}


	/**
	 * Determines if this is an evaluation version of Puakma
	 */
	/*public boolean isEvaluationVersion()
  {
    return m_pSystem.isEvaluationVersion();
  }*/

	/*public boolean isLicensedVersion()
  {
    return m_pSystem.isLicensedVersion();
  }*/


	/**
	 * Attempts to find the user based on the credentials in the document
	 * If successful, updates the session object with the new credentials
	 * @return true if the session was successfully logged in
	 */
	public boolean loginSession(SessionContext pSession, Document docLogin)
	{
		return m_pSystem.loginSession(pSession, docLogin);
	}

	public boolean loginSession(SessionContext pSession, String szLoginName, String szPassword, String sURI)
	{
		return m_pSystem.loginSession(pSession, szLoginName, szPassword, sURI);
	}

	/**
	 * Calls the authenticator to refresh the session properties. This is when a
	 * user tries to autologin using an ltpa token
	 */
	public void populateSession(SessionContext pSession, String sURI)
	{
		m_pSystem.populateSession(pSession, sURI);
	}

	/**
	 * Creates a new session object
	 */
	public SessionContext createNewSession(InetAddress Addr, String szUserAgent)
	{
		pmaSession sess = m_pSystem.createNewSession(Addr, szUserAgent);
		if(sess==null) return null;
		return new SessionContext(sess);
	}

	/**
	 *
	 * @param szUserAgent
	 * @return
	 */
	public SessionContext createSystemSession(String szUserAgent)
	{
		pmaSession sess = m_pSystem.createSystemSession(szUserAgent);
		if(sess==null) return null;
		return new SessionContext(sess);
	}

	/**
	 * drop the session of the sessionid specified
	 * @param szSessionID
	 */
	public void dropSessionID(String szSessionID)
	{
		m_pSystem.dropSessionID(szSessionID);
		//System.out.println("Session dropped: " + szSessionID);
	}

	/**
	 * Attempts to find an existing session object
	 * @return the session object
	 */
	public SessionContext getSession(String sFullSessionID)
	{
		pmaSession sess = m_pSystem.getSession(sFullSessionID);
		if(sess==null) return null;
		return new SessionContext(sess);
	}

	/**
	 * determines if a given sesison exists on this server
	 * @return true if it exists
	 */
	public boolean sessionExists(String szFullSessionID)
	{
		return m_pSystem.sessionExists(szFullSessionID);
	}

	/**
	 * @return the number of session objects on this server
	 */
	public int getSessionCount()
	{
		return m_pSystem.getSessionCount();
	}

	/**
	 * @return the number of errors logged since the system started
	 */
	public long getErrorCount()
	{
		return m_pSystem.getErrorCount();
	}

	/**
	 * Issue a console command
	 * @param szCommand
	 * @return
	 */
	public String doConsoleCommand(String szCommand)
	{
		return m_pSystem.doConsoleCommand(szCommand);
	}

	/**
	 * Sends a message to the specified server AddIn task
	 * @param szAddInClass
	 * @param oMessage
	 * @return
	 */
	public AddInMessage sendMessage(String szAddInClass, AddInMessage oMessage)
	{
		return m_pSystem.sendMessage(szAddInClass, oMessage);
	}

	public void registerAddInToReceiveLogMessages(String sAddInName)
	{
		m_pSystem.registerAddInToReceiveLogMessages(sAddInName);
	}

	public void deregisterAddInToReceiveLogMessages(String sAddInName)
	{
		m_pSystem.deregisterAddInToReceiveLogMessages(sAddInName);
	}

	public boolean isUserInGroup(SessionContext sessCtx, String sGroup, String sURI)
	{
		return m_pSystem.isUserInGroup(sessCtx, sGroup, sURI);
	}

	public void registerSession(pmaSession sess)
	{
		m_pSystem.registerSession(sess);
	}

	public boolean isAddInLoaded(String sAddInName)
	{
		return m_pSystem.isAddInLoaded(sAddInName);
	}

	public String[] getLoadedAddInNames()
	{
		return m_pSystem.getLoadedAddInNames();
	}

	public String getErrorSource()
	{
		return m_pSystem.getErrorSource();
	}

	public String getErrorUser()
	{
		return m_pSystem.getErrorUser();
	}

	public void clearAllSessionObjectsWithClassLoader()
	{
		m_pSystem.clearAllSessionObjectsWithClassLoader();
	}

	/*
	public SharedActionClassLoader getActionClassLoader(RequestPath rPath, int iDesignType)
	{
		return m_pSystem.getActionClassLoader(rPath, iDesignType);
	}*/
	
	public SharedActionClassLoader getActionClassLoader(RequestPath rPath)
	{
		return m_pSystem.getActionClassLoader(rPath);
	}

	public void clearClassLoader(String szKey)
	{
		m_pSystem.clearClassLoader(szKey);
	}

	public boolean addGlobalObject(String sKey, Object obj)
	{
		return m_pSystem.addGlobalObject(sKey, obj);
	}

	public Object getGlobalObject(String sKey)
	{
		return m_pSystem.getGlobalObject(sKey);
	}

	public boolean removeGlobalObject(String sKey)
	{
		return m_pSystem.removeGlobalObject(sKey);
	}
	/*
	public boolean setGlobalObject(Object oItem) 
	{
		return m_pSystem.setGlobalObject(oItem);
	}

	public Object getGlobalObject(String sKey) 
	{
		return m_pSystem.getGlobalObject(sKey);
	}

	public void removeGlobalObject(String sKey) 
	{
		m_pSystem.removeGlobalObject(sKey);
	}
	 */
}