/** ***************************************************************
SessionContext.java
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

import puakma.error.*;
import java.net.*;
import java.util.*;

/**
 * The SessionContext is used to protect an individual user session from malicious
 * damage (as does the SystemContext).
 */
public class SessionContext implements ErrorDetect,Cloneable
{
	private pmaSession pSession;

	public SessionContext(pmaSession paramSession)
	{
		pSession = paramSession;
		pSession.setObjectLock(true); //increment a lock on the session object
	}

	protected void finalize()
	{
		//decrement the session lock count
		pSession.setObjectLock(false);
	}

	/**
	 * Determines if this session is a candidate for clustering.
	 * @return
	 */
	public boolean shouldSynchAcrossCluster()
	{
		return pSession.shouldSynchAcrossCluster();
	}

	/**
	 * Flag this session to be synchronized with the mates in this cluster
	 */
	public void setSynchAcrossCluster(boolean bSet)
	{
		//System.out.println("setSynchAcrossCluster="+bSet);
		pSession.setSynchAcrossCluster(bSet);
	}

	public void setObjectChanged(boolean bSet)
	{
		//System.out.println("setObjectChanged="+bSet);
		pSession.setObjectChanged(bSet);
	}

	/**
	 * Return this object in XML
	 * @return
	 */
	public StringBuilder getXMLRepresentation()
	{
		return pSession.getXMLRepresentation();
	}

	/**
	 *
	 * @return
	 */
	public Object clone()
	{
		SessionContext NewSessCtx;
		try
		{
			NewSessCtx = (SessionContext)super.clone();
			pSession.setObjectLock(true);
			return NewSessCtx;
		}
		catch(Exception e)
		{
			return null;
		}
	}


	public boolean addSessionObject(String szKey, Object obj)
	{
		setObjectChanged(true);
		return pSession.addSessionObject(szKey, obj);
	}


	public Object getSessionObject(String szKey)
	{
		return pSession.getSessionObject(szKey);
	}

	public Vector getSessionObjectKeys()  
	{
		Vector v = new Vector();
		Hashtable ht = pSession.getAllSessionObjects();
		Enumeration en = ht.keys();
		while(en.hasMoreElements())
		{
			String sKey = (String)en.nextElement();
			v.add(sKey);
		}

		return v;
	}


	/**
	 * Remove an object from the current session that matches the key, or remove all
	 * objects if null is passed
	 */
	public boolean removeSessionObject(String szKey)
	{
		setObjectChanged(true);
		return pSession.removeSessionObject(szKey);
	}

	/**
	 * Called when a user logs in (from pmaSystem.loginSession() )
	 */
	public void removeAllUserRolesObjects()
	{
		pSession.removeAllUserRolesObjects();
	}

	public void removeUserRole(String szAppPath, String szRoleName)
	{
		pSession.removeUserRole(szAppPath, szRoleName);
	}

	public UserRoles getUserRolesObject(String szAppPath)
	{
		return pSession.getUserRolesObject(szAppPath);
	}

	public boolean hasUserRole(String szAppPath, String szRoleName)
	{
		return pSession.hasUserRole(szAppPath, szRoleName);
	}

	public boolean hasUserRolesObject(String szAppPath)
	{
		return pSession.hasUserRolesObject(szAppPath);
	}

	public void addUserRolesObject(UserRoles ur)
	{
		setObjectChanged(true);
		pSession.addUserRolesObject(ur);
	}

	public void addUserRole(String szAppPath, String szRoleName)
	{
		setObjectChanged(true);
		pSession.addUserRole(szAppPath, szRoleName);
	}




	public String getFirstName()
	{
		return pSession.firstName;
	}

	public String getLastName()
	{
		return pSession.lastName;
	}

	public java.util.Date getLoginTime()
	{
		return new java.util.Date(pSession.loginTime.getTime());
	}

	public String getLoginName()
	{
		return pSession.loginName;
	}

	public java.util.Date getLastTransactionTime()
	{
		return new java.util.Date(pSession.lastTransaction.getTime());
	}

	public String getUserAgent()
	{
		return pSession.userAgent;
	}

	public String getAuthenticatorUsed()
	{
		return pSession.authenticatorUsed;
	}

	public String getUserName()
	{
		return pSession.userName;
	}

	public String getUserNameAbbreviated()
	{
		X500Name nmUser = new X500Name(pSession.userName);
		return nmUser.getAbbreviatedName();
	}

	public X500Name getX500Name()
	{
		return new X500Name(pSession.userName);
	}

	public void clearSession()
	{
		setObjectChanged(true);
		pSession.clearSession();
	}

	public InetAddress getInternetAddress()
	{
		try
		{
			return InetAddress.getByName(pSession.internetAddress.getHostAddress());
		}
		catch(Exception e)
		{
			return null;
		}
	}

	/**
	 * @return true if the user is not anonymous
	 */
	public boolean isLoggedIn()
	{
		return pSession.isLoggedIn();
	}

	public boolean isSystemSession()
	{
		return pSession.isSystemSession();
	}

	/**
	@deprecated
	*/
	public String getInternetAddressString()
	{
		return getHostAddress();
	}

	public String getHostAddress()
	{		
		return pSession.internetAddress==null ? "" : pSession.internetAddress.getHostAddress();
	}

	/**
	 * Sets the timeout in minutes for this session. -1 will use the system session
	 * timeout.
	 * @param iNewTimeOutMins
	 */
	public void setSessionTimeOut(int iNewTimeOutMins)
	{
		pSession.setSessionTimeOut(iNewTimeOutMins);
	}

	public String getSessionID()
	{
		return pSession.getFullSessionID();
	}

	public String getCookieString(String sPath, String sDomain)
	{
		return pSession.getCookieString(sPath, sDomain);
	}

	public String getCookieString(String sCookieName, String sPath, String sDomain, boolean bIsSecure, boolean bIsHttpOnly)
	{
		return pSession.getCookieString(sCookieName, sPath, sDomain, bIsSecure, bIsHttpOnly);
	}

	public void setFirstName(String szFirstName)
	{
		synchronized(pSession.firstName)
		{
			pSession.setObjectChanged();
			pSession.firstName = szFirstName;
		}
	}

	public void setLastName(String szLastName)
	{
		synchronized(pSession.lastName)
		{
			pSession.setObjectChanged();
			pSession.lastName = szLastName;
		}
	}

	public void setUserName(String szUserName)
	{
		synchronized(pSession.userName)
		{
			pSession.setObjectChanged();
			pSession.userName = szUserName;
		}
	}

	public void setLoginName(String szLoginName)
	{
		synchronized(pSession.loginName)
		{
			pSession.setObjectChanged();
			pSession.loginName = szLoginName;
		}
	}


	public void setAuthenticatorUsed(String szAuthenticatorUsed)
	{
		if(szAuthenticatorUsed==null) szAuthenticatorUsed="";
		synchronized(pSession.authenticatorUsed)
		{
			pSession.setObjectChanged();
			pSession.authenticatorUsed = szAuthenticatorUsed;
		}
	}


	public void setUserAgent(String szUserAgent)
	{
		synchronized(pSession.userAgent)
		{
			pSession.setObjectChanged();
			pSession.userAgent = szUserAgent;
		}
	}

	public void setLastTransactionTime()
	{
		synchronized(pSession.lastTransaction)
		{
			pSession.setObjectChanged();
			pSession.lastTransaction = new java.util.Date();
		}
	}
	
	public void setTimeZone(TimeZone tz)
	{
		pSession.userTimeZone = tz; //not synchronized, zone may be null
	}
	
	
	public TimeZone getTimeZone()
	{
		return pSession.userTimeZone;
	}
	
	public void setLocale(Locale locale)
	{
		pSession.userLocale = locale; //not synchronized, loc may be null
	}
	
	public Locale getLocale()
	{
		return pSession.userLocale;
	}


	public String getErrorSource()
	{
		return pSession.getErrorSource();
	}

	public String getErrorUser()
	{      
		return pSession.getErrorUser();
	}

	public int getSessionTimeout()
	{
		return pSession.sessionTimeoutMinutes;
	}

	public Date getSSOExpiryDate() 
	{
		return pSession.getSSOExpiryDate();
	}
	
	public void setSSOExpiryDate(Date dtNewExpiryDate) 
	{
		pSession.setSSOExpiryDate(dtNewExpiryDate);
	}
}