/* ***************************************************************
HTTPSessionContext.java
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
package puakma.addin.http.action;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.Date;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Locale;
import java.util.TimeZone;
import java.util.Vector;

import puakma.addin.http.HTTPRequestManager;
import puakma.addin.http.HTTPServer;
import puakma.addin.http.TornadoApplication;
import puakma.addin.http.TornadoServer;
import puakma.addin.http.TornadoServerInstance;
import puakma.addin.http.document.DesignElement;
import puakma.addin.http.document.HTMLDocument;
import puakma.addin.http.document.TableManager;
import puakma.error.ErrorDetect;
import puakma.error.pmaLog;
import puakma.pooler.CacheableItem;
import puakma.system.ActionRunnerInterface;
import puakma.system.RequestPath;
import puakma.system.SessionContext;
import puakma.system.SystemContext;
import puakma.system.UserRoles;
import puakma.system.X500Name;
import puakma.system.pmaSystem;
import puakma.util.Util;
/**
 * This object is passed to the Actions. It contains all the things that an action should need related to an instance of a user's session.
 */
public class HTTPSessionContext implements ErrorDetect
{
	private SystemContext m_SysCtx;
	private SessionContext m_SessCtx;
	private RequestPath m_rPath;
	private HTTPRequestManager m_HTTPRM;
	private int m_iDataConnGetCount;
	private int m_iDataConnReleaseCount;
	private Hashtable m_htConnections = new Hashtable();

	/* Create a new instance of HTTPSessionContext used by the HTTP stack
	 * @param httprm
	 * @param paramSysCtx
	 * @param paramSessCtx
	 * @param paramrPath
	 */
	public HTTPSessionContext(HTTPRequestManager httprm, SystemContext paramSysCtx, SessionContext paramSessCtx, RequestPath paramrPath)
	{
		m_SysCtx = paramSysCtx;
		m_SessCtx = paramSessCtx;
		m_rPath = paramrPath;
		m_HTTPRM = httprm;
	}


	/**
	 * Create a new instance of HTTPSessionContext. This version is used by, eg AGENDA
	 * @param paramSysCtx
	 * @param paramSessCtx
	 * @param paramrPath
	 */
	public HTTPSessionContext(SystemContext paramSysCtx, SessionContext paramSessCtx, RequestPath paramrPath)
	{
		m_SysCtx = paramSysCtx;
		m_SessCtx = paramSessCtx;
		m_rPath = paramrPath;
		m_HTTPRM = null;
	}



	/**
	 * Get a handle to the SystemContext
	 * @return a SystemContext
	 */
	public SystemContext getSystemContext()
	{
		return m_SysCtx;
	}


	/**
	 * Get a handle to the SessionContext
	 * @return a SessionContext
	 */
	public SessionContext getSessionContext()
	{
		return m_SessCtx;
	}

	/**
	 * Store an object by name on to the user's session. This object will only remain
	 * in memory and can only be accessed by this session. Useful for storing volatile
	 * session related info, for example shopping cart info.
	 * @param sKey The to refer to and retrieve this object in the future
	 * @param obj The object to store
	 * @return true if the object was stored successfully
	 */
	public boolean addSessionObject(String sKey, Object obj)
	{
		return m_SessCtx.addSessionObject(sKey, obj);
	}


	/**
	 * Get an object stored on a user's session
	 * @param sKey the name of the object to retrieve
	 * @return the object, nul if no object of that name had been previously stored
	 */
	public Object getSessionObject(String sKey)
	{
		return m_SessCtx.getSessionObject(sKey);
	}

	/**
	 * Get a Vector of object names that have been stored on this session
	 * @return a list of object names
	 */
	public Vector getSessionObjectKeys()  
	{
		return m_SessCtx.getSessionObjectKeys();
	}

	/**
	 * Remove an object from the session
	 * @param sKey the name of the object to remove
	 * @return true if an object was successfully removed
	 */
	public boolean removeSessionObject(String sKey)
	{
		return m_SessCtx.removeSessionObject(sKey);
	}


	/**
	 * Get the firstname of this session
	 * @return firstname eg "John"
	 */
	public String getFirstName()
	{
		return m_SessCtx.getFirstName();
	}

	/**
	 * Get the lastname of this session
	 * @return lastname eg "Smith"
	 */
	public String getLastName()
	{
		return m_SessCtx.getLastName();
	}

	/**
	 * Get the time this session logged in
	 * @return the date/time this session logged in to the server
	 */
	public java.util.Date getLoginTime()
	{
		return m_SessCtx.getLoginTime();
	}

	/**
	 * Get the login name used to successfully log in. This is not the X500Name, but the
	 * short name the user entered, eg "jsmith"
	 * @return the login name eg "jsmith"
	 */
	public String getLoginName()
	{
		return m_SessCtx.getLoginName();
	}

	/**
	 * Determines if this session is logged in. This is determined by the username not being "Anonymous"
	 * @return true if the user is not anonymous
	 */
	public boolean isLoggedIn()
	{
		return m_SessCtx.isLoggedIn();
	}

	/**
	 * Determines if this session is secure, network I/O encrypted using SSL 
	 * @return true if the connection is using SSL
	 */
	public boolean isSecureConnection()
	{
		if(m_HTTPRM==null) return false;
		return m_HTTPRM.isSecureConnection();
	}

	/**
	 * Gets the last date/time of a server request. Can be used to determine the idle state of a session.
	 * @return the Date of the last request
	 */
	public java.util.Date getLastTransactionTime()
	{
		return m_SessCtx.getLastTransactionTime();
	}

	/**
	 * For web clients, this denotes the "User-Agent" http header
	 * @return the String value of the type of client being used to access the server
	 */
	public String getUserAgent()
	{
		return m_SessCtx.getUserAgent();
	}

	/**
	 * Get the canonical X500Name of this session as a String
	 * @return the X500Name, eg "CN=Brendon Upson/O=webWise"
	 */
	public String getUserName()
	{
		return m_SessCtx.getUserName();
	}

	/**
	 * Get the abbreviated X500Name of this session as a String
	 * @return the X500Name, eg "Brendon Upson/webWise"
	 */
	public String getUserNameAbbreviated()
	{
		return m_SessCtx.getUserNameAbbreviated();
	}

	/**
	 * Get the X500Name of this session
	 * @return the X500Name object
	 */
	public X500Name getX500Name()
	{
		return m_SessCtx.getX500Name();
	}

	/**
	 * Reset all the session properties back to their initial (and non-logged in) state.
	 *
	 */
	public void clearSession()
	{
		m_SessCtx.clearSession();
	}

	/**
	 * For network based sessions, return the address of the client
	 * @return the address of the connected client
	 */
	public InetAddress getInternetAddress()
	{
		return m_SessCtx.getInternetAddress();
	}

	/**
	 * Returns the client's address as a String
	 * @return the String value of the connected client's address eg "203.12.33.4"
	 */
	public String getHostAddress()
	{
		return m_SessCtx.getHostAddress();
	}

	/**
	 * Get the internal session id that uniquely designates this session 
	 * @return the session id
	 */
	public String getSessionID()
	{
		return m_SessCtx.getSessionID();
	}

	/**
	 * Get the "Set-Cookie" String for use in a HTTP reply
	 * @return the Set-Cookie directive
	 */
	public String getCookieString()
	{
		return m_SessCtx.getCookieString("/", null);
	}

	/**
	 * Get the "Set-Cookie" String for use in a HTTP reply
	 * @param sPath eg "/"
	 * @param sDomain eg ".wnc.net.au"
	 * @return the Set-Cookie directive
	 */
	public String getCookieString(String sPath, String sDomain)
	{
		return m_SessCtx.getCookieString(sPath, sDomain);
	}

	/**
	 * Set the firstname property of this session
	 * @param sFirstName
	 */
	public void setFirstName(String sFirstName)
	{
		m_SessCtx.setFirstName(sFirstName);
	}

	/**
	 * Set the lastname property of this session
	 * @param sLastName
	 */
	public void setLastName(String sLastName)
	{
		m_SessCtx.setLastName(sLastName);
	}

	/**
	 * Set the username property of this session
	 * @param sUserName in X500Format
	 */
	public void setUserName(String sUserName)
	{
		m_SessCtx.setUserName(sUserName);
	}

	/**
	 * Sets the design element that will be returned to the client
	 * @param doc
	 * @param de
	 */
	public void setDesignObject(HTMLDocument doc, DesignElement de)
	{
		doc.designObject = de;
		doc.removeParsedParts();
		doc.prepare();
	}

	/**
	 * Sets the TimeZone of the current session. If this method is never called
	 * or called with a null, the server's default zone will be used
	 * @param tz
	 */
	public void setTimeZone(TimeZone tz)
	{
		m_SessCtx.setTimeZone(tz);		
	}

	/**
	 * Gets the current sessions timezone setting. A null value denotes the server's default time
	 * zone will be used
	 * @return
	 */
	public TimeZone getTimeZone()
	{
		return m_SessCtx.getTimeZone();
	}

	/**
	 * Sets the Locale of the current session. If this method is never called
	 * or called with a null, the server's default locale will be used
	 * @param tz
	 */
	public void setLocale(Locale locale)
	{
		m_SessCtx.setLocale(locale);		
	}

	/**
	 * Gets the current sessions Locale setting. A null value denotes the server's default
	 * locale will be used
	 * @return
	 */
	public Locale getLocale()
	{
		return m_SessCtx.getLocale();
	}

	/**
	 * Get the named design element from the current application.
	 * @param sDesignName The name of the design element as it appears in the design list eg "HomePage"
	 * @param iDesignType Use DesignElement.DESIGN_TYPE_XXXXX eg DesignElement.DESIGN_TYPE_PAGE. 
	 * the puakma.addin.http.document.DesignElement object for a list of types
	 * @return null if not found
	 */
	public DesignElement getDesignObject(String sDesignName, int iDesignType)
	{		
		TornadoServerInstance tsi = TornadoServer.getInstance(m_SysCtx);
		return tsi.getDesignElement(m_rPath.Group, m_rPath.Application, sDesignName, iDesignType);
	}


	/**
	 * Return a list of all design element names for the specified type. Use -1 to get all design element names
	 * regardless of type
	 * @param iDesignType Use DesignElement.DESIGN_TYPE_XXXXX eg DesignElement.DESIGN_TYPE_PAGE
	 * @return a String array of all the design element names in this application
	 */
	public String[] getAllDesignElementNames(int iDesignType)
	{
		TornadoServerInstance tsi = TornadoServer.getInstance(m_SysCtx);
		return tsi.getAllDesignElementNames(m_rPath.Group, m_rPath.Application, iDesignType);		
	}

	/**
	 * Get the named design element from the current application.
	 * @param sDesignName
	 * @param iDesignType
	 * @param sAppGroup
	 * @param sAppName
	 * @return null if not found or a Design element object
	 */
	public DesignElement getDesignObject(String sDesignName, int iDesignType, String sAppGroup, String sAppName)
	{		
		TornadoServerInstance tsi = TornadoServer.getInstance(m_SysCtx);
		return tsi.getDesignElement(sAppGroup, sAppName, sDesignName, iDesignType);
	}



	/**
	 * Gets a handle to a connection object in the given application.
	 * This version opens the connection by name
	 * If param is null or "" get the first connection available
	 * @param lAppID the appid of the application to get the connection for
	 * @param sConnectionName the name of the connection
	 * @return the JDBC connection object
	 * @throws an Exception if there was a problem getting the connection
	 */
	public Connection getDataConnection(long lAppID, String sConnectionName) throws Exception
	{
		TornadoServerInstance tsi = TornadoServer.getInstance();
		TornadoApplication ta = tsi.getTornadoApplication(lAppID);
		Connection cx = ta.getDataConnection(sConnectionName);
		m_htConnections.put(cx, cx);
		return cx;

	}


	/**
	 * Returns the path to the current application
	 * @return RequestPath object
	 */
	public RequestPath getRequestPath()
	{
		return m_rPath;
	}


	/**
	 * Gets a handle to a connection object. This version opens the connection by name
	 * If param is null or "" get the first connection available
	 * @param sConnectionName the name of the connection as it appears in the application's design
	 * @return a JDBC connection object, null if one could not be opened
	 */
	public Connection getDataConnection(String sConnectionName) throws Exception
	{
		m_iDataConnGetCount++;
		TornadoServerInstance tsi = TornadoServer.getInstance();
		TornadoApplication ta = tsi.getTornadoApplication(m_rPath.getPathToApplication());
		return ta.getDataConnection(sConnectionName);
	}



	public String getDataConnectionProperty(String sConnectionName, String sPropertyName)
	{
		TornadoServerInstance tsi = TornadoServer.getInstance();
		TornadoApplication ta = tsi.getTornadoApplication(m_rPath.getPathToApplication());
		return ta.getDataConnectionProperty(sConnectionName, sPropertyName);
	}





	/**
	 * Used in webdesign.pma
	 */
	public void releaseDataConnection(long lAppID, Connection cx)
	{
		TornadoServerInstance tsi = TornadoServer.getInstance();
		TornadoApplication ta = tsi.getTornadoApplication(lAppID);
		if(ta.releaseDataConnection(cx)) 
		{
			m_htConnections.remove(cx);
			m_iDataConnReleaseCount++;
			return;
		}		
	}
	/**
	 * Unlocks a database connection. 
	 * @param cx the connection object to put back into the database pool
	 */
	public void releaseDataConnection(Connection cx)
	{		
		TornadoServerInstance tsi = TornadoServer.getInstance();
		TornadoApplication ta = tsi.getTornadoApplication(m_rPath.getPathToApplication());		
		if(ta.releaseDataConnection(cx)) 
		{
			m_htConnections.remove(cx);
			m_iDataConnReleaseCount++;
			return;
		}


		//Release by appid		
		Hashtable htApp = tsi.getAllLoadedApplications();
		Iterator it = htApp.values().iterator();
		while(it.hasNext())
		{
			ta = (TornadoApplication) it.next();
			if(ta.releaseDataConnection(cx)) return;
		}

		return;
	}

	/**
	 * Gets all the data connection names in the application specified by its appid
	 * @param lAppID 
	 * @return a Vector of database connection names stored in the Vector as Strings
	 */
	public Vector getAllDataConnectionNames(long lAppID)
	{
		Vector vReturn = new Vector();    
		Connection cxSys=null;  
		Statement stmt = null;
		ResultSet rs = null;

		m_SysCtx.doDebug(pmaLog.DEBUGLEVEL_FULL, "getAllDataConnectionNames(appid)", this);


		try
		{        
			String szQuery = "SELECT DBConnectionName FROM APPLICATION,DBCONNECTION WHERE APPLICATION.AppID=" + lAppID + " AND APPLICATION.AppID=DBCONNECTION.AppID";
			cxSys = m_SysCtx.getSystemConnection();
			stmt = cxSys.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
			rs = stmt.executeQuery(szQuery);
			while(rs.next())
			{
				String sConnName = rs.getString(1);
				if(sConnName!=null && sConnName.length()>0) vReturn.add(sConnName);
			}        
		}
		catch(Exception de)
		{      
			m_SysCtx.doError("pmaSystem.getDataConnectionError", new String[]{de.toString()}, m_SessCtx);      
		}
		finally{
			Util.closeJDBC(rs);
			Util.closeJDBC(stmt);
			m_SysCtx.releaseSystemConnection(cxSys);
		}

		return vReturn;
	}



	/**
	 * Get a list of all the connection names in the current application
	 * @return a Vector of Strings
	 * 
	 */
	public Vector getAllDataConnectionNames()
	{
		TornadoServerInstance tsi = TornadoServer.getInstance();
		TornadoApplication ta = tsi.getTornadoApplication(m_rPath.getPathToApplication());
		return ta.getAllDataConnectionNames();
	}




	/**
	 * Clear all the cached roles from this session. This has almost the same effect as logging a user out.
	 * The next time an application is accessed the roles for that application will be reapplied to this 
	 * session
	 *
	 */
	public void removeAllUserRolesObjects()
	{
		m_SessCtx.removeAllUserRolesObjects();
	}

	/**
	 * Remove a specific role for a specific application
	 * @param sAppPath eg "/group/app.pma"
	 * @param sRoleName eg "Manager"
	 */
	public void removeUserRole(String sAppPath, String sRoleName)
	{
		m_SessCtx.removeUserRole(sAppPath, sRoleName);
	}

	/**
	 * Get the UserRoles object for this session for the name application
	 * @param sAppPath eg "/group/app.pma"
	 * @return the UserRoles object
	 */
	public UserRoles getUserRolesObject(String sAppPath)
	{
		return m_SessCtx.getUserRolesObject(sAppPath);
	}

	/**
	 * Determines if this session has the named role in the named application
	 * @param sAppPath eg "/group/app.pma"
	 * @param sRoleName eg "Manager"
	 * @return true if the session has the role
	 */
	public boolean hasUserRole(String sAppPath, String sRoleName)
	{
		return m_SessCtx.hasUserRole(sAppPath, sRoleName);
	}

	/**
	 * Determines if this session has the named role in this application!
	 * @param sRoleName
	 * @return true if the session has the role
	 */
	public boolean hasUserRole(String sRoleName)
	{
		return m_SessCtx.hasUserRole(m_rPath.getPathToApplication(), sRoleName);
	}

	/**
	 * 
	 * @param sAppPath
	 * @return true if the session has the object
	 */
	public boolean hasUserRolesObject(String sAppPath)
	{
		return m_SessCtx.hasUserRolesObject(sAppPath);
	}

	/**
	 * 
	 * @param ur
	 */
	public void addUserRolesObject(UserRoles ur)
	{
		m_SessCtx.addUserRolesObject(ur);
	}

	/**
	 * 
	 * @param sAppPath
	 * @param sRoleName
	 */
	public void addUserRole(String sAppPath, String sRoleName)
	{
		m_SessCtx.addUserRole(sAppPath, sRoleName);
	}


	/**
	 * Expire entire http design cache if older than given age.
	 * Pass 0 to expire everything.
	 * @param lAgeInMilliSeconds
	 */
	public void expireDesignCache(long lAgeInMilliSeconds)
	{
		//if(m_HTTPRM!=null) m_HTTPRM.expireDesignCache(lAgeInMilliSeconds);
		HTTPServer.expireDesignCache(lAgeInMilliSeconds);
	}

	/**
	 * Expire design cache item if older than given age.
	 * @param sKey eg "/group/app.pma/PageName/1"
	 */
	public void removeDesignCacheItem(String sKey)
	{      
		//if(m_HTTPRM!=null) m_HTTPRM.removeDesignCacheItem(sKey);
		HTTPServer.removeDesignCacheItem(sKey);
	}

	/**
	 * Removes an object from the http server's object cache
	 * @param sKey the name of the global object
	 */
	public boolean removeGlobalObject(String sKey)
	{
		//if(m_HTTPRM!=null) m_HTTPRM.removeGlobalObject(sKey);
		return m_SysCtx.removeGlobalObject(sKey);
	}

	/**
	 * Removes an object from the http server's object cache
	 * @param sKey
	 */
	public Object getGlobalObject(String sKey)
	{
		/*if(m_HTTPRM!=null) return m_HTTPRM.getGlobalObject(sKey);
		return null;*/
		return m_SysCtx.getGlobalObject(sKey);
	}

	/**
	 * Gets a keyword value from the current application as a long.
	 * @param sKey the name of the keyword
	 * @return a long value, 0 if it could not be converted
	 */
	public long getKeywordIntegerValue(String sKey)
	{
		long lReturn=0;
		String sVal = getKeywordValue(sKey);
		try
		{
			lReturn = Long.parseLong(sVal);
		}
		catch(Exception e){}
		return lReturn;
	}


	/**
	 * Gets the first keyword matching szKey (case insensitive). Note that if there
	 * are multiple keywords, there is no guarantee which will be returned.
	 * @param sKey
	 * @return null if no match
	 */
	public String getKeywordValue(String sKey)
	{
		Connection cxSys=null;
		PreparedStatement stmt = null;
		ResultSet rs = null;
		String sReturn=null;
		String sAppGroup=m_rPath.Group, sAppName=m_rPath.Application;

		m_SysCtx.doDebug(pmaLog.DEBUGLEVEL_FULL, "getKeywordValue()", m_SessCtx);
		if(sAppName==null || sAppName.length()==0) return null;

		boolean bHasGroup = true;
		if(sAppGroup==null || sAppGroup.length()==0) bHasGroup = false;

		String sQuery = "SELECT Data FROM APPLICATION,KEYWORD,KEYWORDDATA WHERE UPPER(APPLICATION.AppName)=? AND (APPLICATION.AppGroup='' OR APPLICATION.AppGroup IS NULL) AND APPLICATION.AppID=KEYWORD.AppID AND KEYWORD.KeywordID=KEYWORDDATA.KeywordID AND UPPER(KEYWORD.Name)=?";
		if(bHasGroup)
		{
			sQuery = "SELECT Data FROM APPLICATION,KEYWORD,KEYWORDDATA WHERE UPPER(APPLICATION.AppName)=? AND (UPPER(APPLICATION.AppGroup)=? OR APPLICATION.AppGroup='*') AND APPLICATION.AppID=KEYWORD.AppID AND KEYWORD.KeywordID=KEYWORDDATA.KeywordID AND UPPER(KEYWORD.Name)=?";
		}

		try
		{        
			cxSys = m_SysCtx.getSystemConnection();
			stmt = cxSys.prepareStatement(sQuery, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
			stmt.setString(1, sAppName.toUpperCase());
			if(bHasGroup)
			{
				stmt.setString(2, sAppGroup.toUpperCase());
				stmt.setString(3, sKey.toUpperCase());
			}
			else
				stmt.setString(2, sKey.toUpperCase());
			rs = stmt.executeQuery();
			if(rs.next())
			{
				sReturn = rs.getString(1);
			}
		}
		catch(Exception de)
		{
			m_SysCtx.doError("HTTPRequest.getKeywordError", new String[]{sKey, de.toString()}, m_SessCtx);
		}
		finally{
			Util.closeJDBC(rs);
			Util.closeJDBC(stmt);
			m_SysCtx.releaseSystemConnection(cxSys);
		}

		return sReturn;
	}

	/**
	 * Gets all keywords matching sKey (case insensitive). 
	 * @param sKey the keyword name
	 * @param bSortByValue Pass true to have the results put in the vector in sorted Data order.
	 * @return null if no match
	 */
	public Vector getAllKeywordValues(String sKey, boolean bSortByValue)
	{
		Vector vReturn=null;
		Connection cxSys=null; 
		PreparedStatement stmt = null;
		ResultSet rs = null;
		String sAppGroup=m_rPath.Group, sAppName=m_rPath.Application;
		String sOrderClause=" ORDER BY KeywordOrder,Data";

		//m_SysCtx.doDebug(pmaLog.DEBUGLEVEL_FULL, "getAllKeywordValues()", m_SessCtx);
		if(sKey==null || sKey.length()==0) return null;
		if(sAppName==null || sAppName.length()==0) return null;
		boolean bHasGroup = true;
		if(sAppGroup==null || sAppGroup.length()==0) bHasGroup = false;

		if(bSortByValue) sOrderClause = " ORDER BY Data";
		String sQuery = "SELECT Data FROM APPLICATION,KEYWORD,KEYWORDDATA WHERE UPPER(APPLICATION.AppName)=? AND (APPLICATION.AppGroup='' OR APPLICATION.AppGroup IS NULL) AND APPLICATION.AppID=KEYWORD.AppID AND KEYWORD.KeywordID=KEYWORDDATA.KeywordID AND UPPER(KEYWORD.Name)=?" + sOrderClause;
		if(bHasGroup)
		{
			sQuery = "SELECT Data FROM APPLICATION,KEYWORD,KEYWORDDATA WHERE UPPER(APPLICATION.AppName)=? AND (UPPER(APPLICATION.AppGroup)=? OR APPLICATION.AppGroup='*') AND APPLICATION.AppID=KEYWORD.AppID AND KEYWORD.KeywordID=KEYWORDDATA.KeywordID AND UPPER(KEYWORD.Name)=?" + sOrderClause;
		}


		try
		{
			//System.out.println("[" + szQuery + "]");
			cxSys = m_SysCtx.getSystemConnection();
			stmt = cxSys.prepareStatement(sQuery, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
			stmt.setString(1, sAppName.toUpperCase());
			if(bHasGroup)   
			{
				stmt.setString(2, sAppGroup.toUpperCase());
				stmt.setString(3, sKey.toUpperCase());
			}
			else
				stmt.setString(2, sKey.toUpperCase());

			rs = stmt.executeQuery();
			while(rs.next())
			{
				if(vReturn==null) vReturn = new Vector();
				vReturn.add(rs.getString("Data"));
			}
			rs.close();
			stmt.close();
		}
		catch(Exception de)
		{
			m_SysCtx.doError("HTTPRequest.getKeywordError", new String[]{sKey, de.toString()}, m_SessCtx);
		}
		finally{
			Util.closeJDBC(rs);
			Util.closeJDBC(stmt);
			m_SysCtx.releaseSystemConnection(cxSys);
		}
		return vReturn;
	}

	/**
	 * Stores an object in memory for access by all http users/threads
	 * @param cacheItem the item to store in the global object cache
	 * @return true if the object was stored successfully
	 * @deprecated
	 */
	public boolean storeGlobalObject(CacheableItem oItem)
	{
		/*if(m_HTTPRM!=null) return m_HTTPRM.storeGlobalObject(cacheItem);

		m_SysCtx.doError("HTTPRequest.NoGlobalAdd", m_SessCtx);
		 */
		return addGlobalObject(oItem.getItemKey(), oItem);
	}

	public boolean addGlobalObject(String sKey, Object obj)
	{
		return m_SysCtx.addGlobalObject(sKey, obj);
	}

	/**
	 * Transforms an xml document based on the xsl deign element specified.
	 * @param xmlData
	 * @param sDesignElement
	 * @return a transformed lock of XML
	 */
	public StringBuilder xmlTransform(StringBuilder xmlData, String sDesignElement)
	{

		DesignElement de= getDesignObject(sDesignElement, DesignElement.DESIGN_TYPE_RESOURCE);

		if(de==null)
		{
			//get the xml columns
			String sColumns[] = TableManager.getXMLColumnNames(xmlData);
			//for(int i=0; i<sColumns.length; i++) System.out.println(sColumns[i]);
			//make a stylesheet and save it to the design collection
			//if it saved ok, then recall this method, cause we'll find the sheet next time
			if(saveXSLDesignElement(sDesignElement, sColumns)) return xmlTransform(xmlData, sDesignElement);

			return null;
		}
		//maybe check the contenttype is xsl ??
		String sXML=null;
		try
		{
			sXML = new String(de.getContent(), "UTF-8");
			if(sXML!=null && (sXML.indexOf("<P@")>=0 || sXML.indexOf("<p@")>=0))
			{
				//FIXME parse out the <P@ST tags
				HTMLDocument docHTML = new HTMLDocument(this);
				docHTML.designObject = de;
				docHTML.rPath = new RequestPath(getRequestPath().getFullPath());
				docHTML.setParameters(getRequestPath().Parameters);
				docHTML.prepare();
				docHTML.renderDocument(false);
				sXML = new String(docHTML.getContent(), "UTF-8");
			}			
		}
		catch(Exception e){}
		StringBuilder xslData = new StringBuilder(sXML);
		return Util.xmlTransform(m_SysCtx, xmlData, xslData);
	}

	/**
	 * Stores a new xsl resource based on the column names and design name
	 * @param sDesignName
	 * @param sColumnNames
	 * @return
	 */
	private boolean saveXSLDesignElement(String sDesignName, String sColumnNames[])
	{    

		if(sDesignName==null || sDesignName.length()==0 || sColumnNames==null || sColumnNames.length==0) 
		{
			m_SysCtx.doError("HTTPSessionContext.NoXML", new String[]{sDesignName}, this);
			return false;
		}
		boolean bOK=true;
		Connection cx=null;
		PreparedStatement prepStmt = null;
		String sQuery;    
		StringBuilder sbXSL = new StringBuilder(4096);

		sbXSL.append("<?xml version=\"1.0\"?>\r\n");
		sbXSL.append("<xsl:stylesheet xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\" version=\"1.0\">\r\n\r\n");

		sbXSL.append("<xsl:output method=\"html\" indent=\"yes\"/>\r\n");

		sbXSL.append("<xsl:template match=\"/\">\r\n");
		sbXSL.append("\t<xsl:apply-templates/>\r\n");  
		sbXSL.append("</xsl:template>\r\n\r\n");

		sbXSL.append("<xsl:template match=\"data\">\r\n");
		sbXSL.append("<table class=\"listTable\">\r\n");  
		sbXSL.append("<thead>\r\n");
		sbXSL.append("<tr>\r\n");
		sbXSL.append("\t<th>&#160;</th>\r\n");
		//for each column, show the header
		for(int i=0; i<sColumnNames.length; i++)
		{
			sbXSL.append("\t<th>");
			sbXSL.append(sColumnNames[i]);
			sbXSL.append("</th>\r\n");
		}		
		sbXSL.append("</tr>\r\n");
		sbXSL.append("</thead>\r\n\r\n");

		sbXSL.append("<tbody>\r\n");
		sbXSL.append("<xsl:for-each select=\"row\">\r\n");
		sbXSL.append("<tr>\r\n");

		sbXSL.append("\r\n\t<!-- Decimals: <xsl:value-of select=\"format-number(item[@name='numbercolumn']/value, '###,###.00')\" /> -->\r\n");
		sbXSL.append("\t<!-- Totals: <xsl:value-of select=\"format-number(sum(/data/row/item[@name='numbercolumn']/value), '###,###.00')\" /> -->\r\n");
		sbXSL.append("\t<!-- Dates: <xsl:value-of select=\"item[@name='datecolumn']/value/year\" /> (year,month,day,hour,minute,second) -->\r\n\r\n");

		//show an example of a delete column
		sbXSL.append("\t<td>\r\n");
		sbXSL.append("\t\t<a><xsl:attribute name=\"href\">JavaScript:deleteRecord('<xsl:value-of select=\"item[@name='columnid']/value\"/>');</xsl:attribute>\r\n");
		sbXSL.append("\t\t<img border=\"0\" alt=\"Delete this record\"><xsl:attribute name=\"src\"><xsl:value-of select=\"/data/@path\"/>/recyclebin.gif?OpenResource</xsl:attribute></img>\r\n");
		sbXSL.append("\t\t</a>\r\n");          	
		sbXSL.append("\t</td>\r\n");

		//for each column
		for(int i=0; i<sColumnNames.length; i++)
		{
			sbXSL.append("\t<td>\r\n");
			if(i==0) sbXSL.append("\t\t<a><xsl:attribute name=\"href\"><xsl:value-of select=\"/data/@path\"/><xsl:text>/EditPageName?OpenPage&amp;id=</xsl:text><xsl:value-of select=\"item[@name='idcolumn']/value\"/></xsl:attribute>\r\n");        
			sbXSL.append("\t\t<xsl:value-of select=\"item[@name='"+sColumnNames[i].toLowerCase()+"']/value\"/>\r\n");          
			if(i==0) sbXSL.append("\t\t</a>\r\n");
			sbXSL.append("\t</td>\r\n");
		}

		sbXSL.append("</tr>\r\n");
		sbXSL.append("</xsl:for-each>\r\n");
		sbXSL.append("</tbody>\r\n\r\n");
		sbXSL.append("<!-- sum a column or count all the rows\r\n");
		sbXSL.append("<tr>\r\n");
		sbXSL.append("\t<td>$<xsl:value-of select=\"format-number(sum(/data/row/item[@name='numbercolumn']/value), '###,###.00')\"/></td>\r\n");
		sbXSL.append("\t<td><xsl:value-of select=\"count(/data/row)\"/></td>\r\n");
		sbXSL.append("</tr>\r\n");
		sbXSL.append("-->\r\n\r\n");

		sbXSL.append("</table>\r\n");
		sbXSL.append("</xsl:template>\r\n");
		sbXSL.append("</xsl:stylesheet>\r\n");


		TornadoServerInstance tsi = TornadoServer.getInstance(m_SysCtx);
		try
		{
			cx = m_SysCtx.getSystemConnection();        
			sQuery = "INSERT INTO DESIGNBUCKET(AppID,Name,DesignType,ContentType,Updated,UpdatedBy,Comment,Options,InheritFrom,DesignData) VALUES(?,?,?,?,?,?,?,?,?,?)";
			prepStmt = cx.prepareStatement(sQuery);
			long lAppID = tsi.getApplicationID(m_rPath.Group, m_rPath.Application); //HTTPServer.getAppID(m_SysCtx, m_rPath.Application, m_rPath.Group);
			if(lAppID>0)
			{
				prepStmt.setLong(1, lAppID);
				prepStmt.setString(2, sDesignName);
				prepStmt.setInt(3, DesignElement.DESIGN_TYPE_RESOURCE); //designtype

				//if(docDesign.getItemValue("Type").equals(String.valueOf(DesignElement.DESIGN_TYPE_PAGE))) szMimeType = "text/html";
				prepStmt.setString(4, "text/xml");//contenttype
				prepStmt.setTimestamp(5, new Timestamp((new java.util.Date()).getTime())); //updated
				prepStmt.setString(6, pmaSystem.SYSTEM_ACCOUNT );//updatedby
				prepStmt.setString(7, "");//comment
				prepStmt.setString(8, "");
				prepStmt.setString(9, ""); //inherit

				prepStmt.setBinaryStream(10, new ByteArrayInputStream(sbXSL.toString().getBytes()), (int)sbXSL.length() );//designdata

				prepStmt.execute();
			}
			else
				bOK=false;

		}
		catch (Exception sqle)
		{
			m_SysCtx.doError(sqle.getMessage(), m_SessCtx);
		}
		finally{
			Util.closeJDBC(prepStmt);
			m_SysCtx.releaseSystemConnection(cx);
		}

		return bOK;
	}

	/**
	 * Set the RequestPath of this request
	 * @param rp
	 */
	public void setRequestPath(RequestPath rp)
	{
		m_rPath = rp;
	}

	/**
	 * Set the RequestPath of this request
	 * @param sRequestPath as a String
	 */
	public void setRequestPath(String sRequestPath)
	{
		m_rPath = new RequestPath(sRequestPath);
	}


	/**
	 * This method calls the puakma.util.Util version, provided here for convenience
	 * @param xmlData
	 * @param xslData
	 * @return a transformed xml document as a StringBuilder
	 */
	public StringBuilder xmlTransform(StringBuilder xmlData, StringBuilder xslData)
	{
		return Util.xmlTransform(m_SysCtx, xmlData, xslData);
	}



	/**
	 * Allow users to execute console commands
	 * @param sCommand
	 * @return a string containing the results of the command. CRLF breaks each line, may be null or ""
	 */
	public String doConsoleCommand(String sCommand)
	{
		return m_SysCtx.doConsoleCommand(sCommand);
	}

	/**
	 * Convenience method to allow the running of the named action
	 * @param doc
	 * @param sActionClass
	 * @return
	 */
	public ActionReturn runActionOnDocument(HTMLDocument doc, String sActionClass)
	{
		return runActionOnDocument(doc, sActionClass, DesignElement.DESIGN_TYPE_ACTION);
	}

	/**
	 * Runs the named action against the passed document. 
	 * @param doc
	 * @param sActionClass
	 * @param iDesignElementType can be either DesignElement.DESIGN_TYPE_ACTION or DesignElement.DESIGN_TYPE_SCHEDULEDACTION
	 * @return an ActionReturn object that the programmer may check to see what the action has done
	 */
	public ActionReturn runActionOnDocument(HTMLDocument doc, String sActionClass, int iDesignElementType)
	{      
		ActionReturn act_return = null;      
		SystemContext SysCtx=null;

		if(sActionClass==null || sActionClass.length()==0 || doc==null) return null;

		try
		{                    
			//ActionClassLoader aLoader= new ActionClassLoader(m_HTTPRM, m_SysCtx, m_SessCtx, doc.rPath.Group, doc.rPath.Application, szActionClass);
			act_return = new ActionReturn();           
			SysCtx = (SystemContext)m_SysCtx.clone();                        
			SessionContext SessCtx = (SessionContext)m_SessCtx.clone(); //ditto with the session    
			RequestPath rp = new RequestPath(m_rPath.getFullPath());
			if(doc.rPath!=null) rp = doc.rPath;
			HTTPSessionContext HTTPSessCtx = new HTTPSessionContext(m_HTTPRM, SysCtx, SessCtx, rp);
			SharedActionClassLoader aLoader = m_SysCtx.getActionClassLoader(rp); //, iDesignElementType);
			//Class runclass = aLoader.getActionClass();
			Class runclass = aLoader.getActionClass(sActionClass, DesignElement.DESIGN_TYPE_ACTION);
			if(runclass==null) return act_return;
			Object object = runclass.newInstance();
			ActionRunnerInterface act = (ActionRunnerInterface)object;
			act.init(HTTPSessCtx, doc, rp.Group, rp.Application);
			act_return.RedirectTo = act.execute();            
			act_return.HasStreamed = act.hasStreamed();
			act_return.bBuffer = act.getByteBuffer();
			act_return.ContentType = act.getContentType();          
		}
		catch(Throwable e)
		{
			String sPath = sActionClass;
			if(doc!=null) sPath = doc.rPath.getFullPath() + " - " + sActionClass;
			m_SysCtx.doError("HTTPRequest.ActionExecuteError", new String[]{sPath, e.toString()}, m_SessCtx);          
			Util.logStackTrace(e, m_SysCtx, 99);
		}

		return act_return;
	}

	/**
	 * Send a block of bytes directly to the browser. Note the buffer is flushed after each call, so call this
	 * method fewer times for better IO performance
	 * @param buf
	 * @throws IOException
	 */
	public void streamToClient(byte[] buf) throws IOException
	{
		if(m_HTTPRM==null) return;
		m_HTTPRM.streamToClient(buf);
	}

	/**
	 * Get a handle to the http output stream to write directly to the client
	 * @return null if called from a scheduled action (eg by AGENDA)
	 */
	public OutputStream getOutputStream()
	{
		if(m_HTTPRM==null) return null;
		return m_HTTPRM.getOutputStream();
	}


	/**
	 * For logging, gets the source of the error, the URI
	 */
	public String getErrorSource() 
	{
		if(m_rPath!=null) return m_rPath.getFullPath();
		return m_SessCtx.getErrorSource();
	}

	/**
	 * For logging, gets the user that caused the error
	 */
	public String getErrorUser() 
	{
		return m_SessCtx.getErrorUser();
	}

	/**
	 * Tells the cluster manager whether this object should be pushed across the cluster
	 * @param bSet
	 */
	public void setSynchAcrossCluster(boolean bSet)
	{    
		m_SessCtx.setSynchAcrossCluster(bSet);
	}

	/**
	 * Sets the last transaction time of this session.
	 *
	 */
	public void setLastTransactionTime()
	{
		m_SessCtx.setLastTransactionTime();
	}


	/** 
	 * Get the date that the SSO token should expire. Null means when the browser is closed
	 * 
	 * @return
	 */
	public Date getSSOExpiryDate() 
	{
		return m_SessCtx.getSSOExpiryDate();
	}

	public void setSSOExpiryDate(Date dtNewExpiryDate) 
	{
		m_SessCtx.setSSOExpiryDate(dtNewExpiryDate);
	}

	/**
	 * Check for any open connections
	 *
	 */
	public void finalize()
	{
		StringBuilder sbDBNames = new StringBuilder(m_htConnections.size()*20);
		if(m_htConnections.size()>0)
		{
			int iOpenConnections = m_htConnections.size();
			//force them all to be cleared....
			Enumeration en = m_htConnections.keys();
			while(en.hasMoreElements())
			{
				Connection cx = (Connection)en.nextElement();
				try
				{
					String sDBName = cx.getMetaData().getDatabaseProductName();
					if(sDBName.length()>0) 
					{
						if(sbDBNames.length()>0) sbDBNames.append(',');
						sbDBNames.append(sDBName);
					}
				}catch(Exception e){}
				releaseDataConnection(cx);
				m_htConnections.remove(cx);
			}
			m_SysCtx.doError("pmaSystem.ReleaseConnection", new String[]{""+iOpenConnections, sbDBNames.toString()}, this);
		}
	}

	/**
	 * Finds an entry in the String table for i18n
	 */ 
	public String getStringTableEntry(String sStringTableKey, String sVariables[]) 
	{
		Locale loc = getLocale();

		TornadoServerInstance tsi = TornadoServer.getInstance(m_SysCtx);
		TornadoApplication ta = tsi.getTornadoApplication(this.getRequestPath().getPathToApplication());
		String sMessage = ta.getStringTableEntry(sStringTableKey, loc);

		return pmaLog.parseMessage(sMessage, sVariables);


	}
}//class