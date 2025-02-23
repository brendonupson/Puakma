/** ***************************************************************
pmaSession.java
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

import java.io.ByteArrayInputStream;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.net.InetAddress;
import java.util.Date;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Locale;
import java.util.TimeZone;

import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import puakma.addin.http.HTTPServer;
import puakma.addin.http.TornadoApplication;
import puakma.addin.http.TornadoServer;
import puakma.addin.http.TornadoServerInstance;
import puakma.error.ErrorDetect;
import puakma.util.Util;


public class pmaSession implements ErrorDetect
{
	public final static String ANONYMOUS_USER="Anonymous";
	public String firstName="";
	public String lastName=ANONYMOUS_USER;
	public String userName=ANONYMOUS_USER;
	public String userAgent="Unknown"; //type of client being used
	public String authenticatorUsed="";
	public long sessionID;
	public String loginName="";
	public TimeZone userTimeZone; //null = system timezone
	public Locale userLocale; //null = system timezone
	public java.util.Date loginTime= new java.util.Date();
	public java.util.Date lastTransaction= new java.util.Date();
	public InetAddress internetAddress;
	private Hashtable m_htUserRoles = new Hashtable();
	public int sessionTimeoutMinutes=-1;
	private pmaSystem m_pSystem;
	//set the following to true so that new sessions will get replicated across
	//the cluster
	private boolean m_bSynchAcrossCluster=true;
	private boolean m_bObjectHasChanged=true;

	private int LockCount=0;
	private Hashtable m_htSessionObjects = new Hashtable();
	public String m_sServerUnique=""; //so this object is unique across clustered servers
	private Date ssoExpiryDate = null; //when browser is closed

	/**
	 * So we can create dummy empty sessions
	 */
	public pmaSession(){}

	public pmaSession(pmaSystem paramSystem, long lNewSessionID, String paramUserAgent, InetAddress Addr)
	{		
		internetAddress = Addr;
		sessionID = lNewSessionID;
		userAgent = paramUserAgent;
		m_pSystem = paramSystem;
		m_sServerUnique=Long.toHexString(m_pSystem.dtStart.getTime()).toUpperCase();
	}

	public void setSystem(pmaSystem paramSystem)
	{
		m_pSystem = paramSystem;
	}

	/**
	 * This will reset the session as if it were an anonymous login
	 * should be called when there is a &logout in the url
	 * The session object is NOT destroyed on logout. It will be cleaned
	 * up later when the session has timed-out
	 */
	public void clearSession()
	{
		firstName="";
		lastName=ANONYMOUS_USER;
		userName=ANONYMOUS_USER;
		loginName="";
		m_htUserRoles = new Hashtable();
		m_htSessionObjects = new Hashtable(); //this should be cleared as it is effectively a new session
	}

	public String toString()
	{
		String sIP = "UnknownIP";
		if(internetAddress!=null) sIP = internetAddress.getHostAddress();
		return userName + ' ' + getFullSessionID() + ' ' + sIP;
	}

	/**
	 * This is used to determine how many other objects have an interest in this session
	 * The session cleaner should check this flag before destroying sessions
	 */
	public synchronized void setObjectLock(boolean bLock)
	{
		if(bLock) LockCount++; else LockCount--;
		if(LockCount<0) LockCount = 0; //just in case...
	}

	/**
	 * Adds an object to this session object. This is used as a temporary storage area
	 * for dynamic data. The data will vanish when the session is cleaned.
	 * @return true if the object was successfully added.
	 * The hashtable is already synchronized which should take care of thread contention.
	 * Object names (szKey) must be unique
	 */
	public boolean addSessionObject(String sKey, Object obj)
	{
		if(m_htSessionObjects.containsKey(sKey)) removeSessionObject(sKey);
		m_htSessionObjects.put(sKey, obj);
		return true;
	}


	/**
	 * Gets a named object from the temp storage area
	 * @return null if the object is not found
	 * The hashtable is already synchronized which should take care of thread contention.
	 */
	public Object getSessionObject(String sKey)
	{
		return m_htSessionObjects.get(sKey);
	}

	public Hashtable getAllSessionObjects()
	{
		return m_htSessionObjects;
	}

	/**
	 * Removes a named object from the temp storage area. Pass null to remove all objects from the session
	 * @return true if the object was successfully removed
	 * The hashtable is already synchronized which should take care of thread contention.
	 */
	public boolean removeSessionObject(String szKey)
	{
		if(szKey==null) 
		{
			if(m_htSessionObjects.size()==0) return false;//no objects
			m_htSessionObjects.clear();
			return true;
		}

		if(m_htSessionObjects.remove(szKey)==null) return false;
		return true;
	}

	/**
	 * Called when a user logs in (from pmaSystem.loginSession() )
	 */
	public void removeAllUserRolesObjects()
	{
		m_htUserRoles.clear();// = new Hashtable();
	}


	public void removeUserRolesObject(String szAppPath)
	{
		szAppPath = getAppKey(szAppPath);
		if(m_htUserRoles.containsKey(szAppPath))
		{
			m_htUserRoles.remove(szAppPath);
		}
	}


	public void addUserRolesObject(UserRoles ur)
	{
		if(!m_htUserRoles.containsKey(ur.getKey()))
		{
			m_htUserRoles.put(ur.getKey(), ur);
		}
	}


	public void addUserRole(String szAppPath, String szRoleName)
	{
		szAppPath = getAppKey(szAppPath);
		if(m_htUserRoles.containsKey(szAppPath))
		{
			UserRoles ur = (UserRoles)m_htUserRoles.get(szAppPath);
			ur.addRole(szRoleName);
		}
		else //create an object and add it
		{
			UserRoles ur = new UserRoles(szAppPath);
			ur.addRole(szRoleName);
			m_htUserRoles.put(ur.getKey(), ur);
		}
	}


	/**
	 * @return true if something has locked this session
	 */
	public boolean isLocked()
	{
		if(LockCount>0) return true;
		return false;
	}


	/**
	 * @return true if the user is not anonymous
	 */
	public boolean isLoggedIn()
	{
		if(userName.equals(ANONYMOUS_USER)) return false;
		return true;
	}

	/**
	 * @return true if the current session is a system session.
	 * system sessions should not be clustered.
	 */
	public boolean isSystemSession()
	{
		if(userName.equals(pmaSystem.SYSTEM_ACCOUNT)) return true;
		return false;
	}


	/**
	 * Should make it harder to crack the session ID without sniffing
	 * the packets. Use the login time for an element of randomness.
	 * Added Server starttime so that it will be unique across clusters :-)
	 * @return String representing the cookie value
	 */
	public String getFullSessionID()
	{
		String sSessionID = Long.toHexString(sessionID).toUpperCase();
		String sUnique = Long.toHexString(loginTime.getTime()).toUpperCase();

		return sSessionID + '-' + m_sServerUnique + '-' + sUnique;
	}

	/**
	 * Gets the string to send the http client. Allows the cookie name to be set so it
	 * can be used with other things, eg BOOSTER 
	 * This session is used to access the entire server, hence Path=/
	 */
	public String getCookieString(String sCookieName, String sPath, String sDomain, boolean bIsSecure, boolean bIsHttpOnly)
	{
		if(sPath==null || sPath.trim().length()==0) sPath = "/";
		if(sCookieName==null || sCookieName.length()==0) sCookieName = puakma.addin.http.HTTPServer.SESSIONID_LABEL;
		String sCookie = "Set-Cookie: " + sCookieName + '=' + getFullSessionID() + "; version=1; path="+sPath;
		String sCookieDomain = "";
		if(sDomain!=null && sDomain.trim().length()>0) sCookieDomain = "; domain="+sDomain;    
		//expiry is controlled serverside if we the expiry is updated with each transaction
		//and we don't want the overhead of resending the cookie every time
		if(bIsSecure) sCookie += "; secure";
		if(bIsHttpOnly) sCookie += "; httpOnly";
		
		return sCookie+sCookieDomain;
	}

	/**
	 * default method for HTTP sessions
	 * @param sPath
	 * @param sDomain
	 * @return
	 */
	public String getCookieString(String sPath, String sDomain)
	{
		return getCookieString(HTTPServer.SESSIONID_LABEL, sPath, sDomain, false, true);
	}

	/**
	 * Determines if this session has expired based on the last transaction time
	 * @return true if the session has expired, else false
	 */
	public boolean hasExpired(int iExpiryMinutes, int iAnonymousExpiryMinutes)
	{
		/* a custom session timeout should only be >=0 and < server configured max
    this stops rogue browsers sending a Session-Timeout: 999999999 header and clogging
    up the servers memory with sessions with a ridiculous timeout value
		 */
		if(sessionTimeoutMinutes>=0 && sessionTimeoutMinutes<iExpiryMinutes) iExpiryMinutes = sessionTimeoutMinutes;    

		long lDiffMinutes = (System.currentTimeMillis() - lastTransaction.getTime())/1000/60;
		//System.out.println("isLoggedIn()=" + isLoggedIn() + " lDiffMinutes="+lDiffMinutes);
		if(isLoggedIn() && lDiffMinutes>iExpiryMinutes) return true;
		if(!isLoggedIn() && lDiffMinutes>iAnonymousExpiryMinutes) return true;
		return false;
	}


	/**
	 * For backward compatibility. Treats anonymous sessions the same as logged in sessions.
	 *
	 */
	public boolean hasExpired(int iExpiryMinutes)
	{
		return hasExpired(iExpiryMinutes, iExpiryMinutes);
	}


	/**
	 * Provide an unaliasing mechanism to turn "/dev/app.pma" into "/aster/app.pma"
	 * @param sAppPath
	 * @return
	 */
	private String getAppKey(String sAppPath)
	{
		TornadoServerInstance tsi = TornadoServer.getInstance();
		TornadoApplication ta = tsi.getTornadoApplication(sAppPath);

		return ta.getRequestPath().getPathToApplication().toLowerCase();
	}


	/**
	 * determines if the user has the given role in an application
	 */
	public boolean hasUserRolesObject(String sAppPath)
	{
		String sApp = getAppKey(sAppPath);
		if( m_htUserRoles.containsKey(sApp) ) return true;
		return false;
	}

	/**
	 * determines if the user has the given role in an application
	 */
	public boolean hasUserRole(String szAppPath, String szRoleName)
	{
		String szApp = getAppKey(szAppPath);
		if( m_htUserRoles.containsKey(szApp) )
		{
			UserRoles ur = (UserRoles)m_htUserRoles.get(szApp);
			return ur.hasRole(szRoleName);
		}
		return false;
	}


	public UserRoles getUserRolesObject(String szAppPath)
	{
		String szApp = getAppKey(szAppPath);
		if( m_htUserRoles.containsKey(szApp) )
		{
			UserRoles ur = (UserRoles)m_htUserRoles.get(szApp);
			return ur;
		}
		return null;
	}

	/**
	 * Return an array of all userroles objects
	 */
	public UserRoles[] getAllUserRolesObjects()
	{
		int i=0;
		UserRoles ur[] = new UserRoles[m_htUserRoles.size()];
		Enumeration en = m_htUserRoles.elements();
		while(en.hasMoreElements())
		{
			ur[i] = (UserRoles)en.nextElement();
			i++;
		}
		return ur;
	}



	/**
	 * Removes a role from an application
	 *
	 */
	public void removeUserRole(String szAppPath, String szRoleName)
	{
		String szApp = getAppKey(szAppPath);
		if( m_htUserRoles.containsKey(szApp) )
		{
			UserRoles ur = (UserRoles)m_htUserRoles.get(szApp);
			ur.removeRole(szRoleName);
		}
	}

	/**
	 * trip the flag to synch this session across the cluster
	 * @param bSet
	 */
	public synchronized void setSynchAcrossCluster(boolean bSet)
	{
		if(bSet && m_bObjectHasChanged) m_bSynchAcrossCluster = true;
		if(!bSet) m_bSynchAcrossCluster = false;
		//m_pSystem.pErr.doInformation("Finished with session context - synching... " + SessionID, this);
	}


	/**
	 * Return this object in XML
	 * @return
	 */
	public StringBuilder getXMLRepresentation()
	{
		//TODO: Take out CRLF later to improve performance?
		final String DATE_FORMAT="yyyy-MM-dd HH:mm:ss:SSS";

		StringBuilder sb = new StringBuilder(512);
		//sb.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\r\n");
		sb.append("<session id=\"" + sessionID + "\" su=\"" + m_sServerUnique + "\" timeout=\"" + sessionTimeoutMinutes + "\" login=\"" + Util.formatDate(loginTime, DATE_FORMAT) + "\" trans=\"" + Util.formatDate(lastTransaction, DATE_FORMAT) + "\">\r\n");
		sb.append("<uname>" + Util.base64Encode(Util.utf8FromString(userName)) + "</uname>\r\n");
		sb.append("<uagent>" + Util.base64Encode(Util.utf8FromString(userAgent)) + "</uagent>\r\n");
		sb.append("<fname>" + Util.base64Encode(Util.utf8FromString(firstName)) + "</fname>\r\n");
		sb.append("<lname>" + Util.base64Encode(Util.utf8FromString(lastName)) + "</lname>\r\n");
		sb.append("<auth>" + authenticatorUsed + "</auth>\r\n");
		sb.append("<ip>" + (internetAddress!=null?internetAddress.getHostAddress():"") + "</ip>\r\n");

		sb.append(getXMLRoleData());

		StringBuilder sbObjectData = getXMLObjectData();
		if(sbObjectData.length()>0)
		{
			sb.append("<objects>\r\n");
			sb.append(sbObjectData);
			sb.append("</objects>\r\n");
		}
		sb.append("</session>\r\n");
		return sb;
	}

	/**
	 * Get the role info for this session as XML
	 * @return
	 */
	private StringBuilder getXMLRoleData()
	{
		StringBuilder sb = new StringBuilder(2048);
		sb.append("<roles>\r\n");
		Enumeration en = m_htUserRoles.elements();
		while(en.hasMoreElements())
		{
			UserRoles ur = (UserRoles)en.nextElement();
			if(ur!=null)
			{
				sb.append(ur.getXML());
			}
		}
		sb.append("</roles>\r\n");
		return sb;
	}


	/**
	 * Get the objects stored on this session as XML
	 * @return
	 */
	private StringBuilder getXMLObjectData()
	{
		StringBuilder sb = new StringBuilder(2048);

		Hashtable ht = getAllSessionObjects();
		Enumeration en = ht.elements();
		Enumeration enKeys = ht.keys();
		while(en.hasMoreElements())
		{
			String sKey = (String)enKeys.nextElement();
			Object obj = en.nextElement();
			if(obj!=null)
			{
				try
				{
					byte buf[]=puakma.util.Util.serializeObject((Serializable)obj);
					String s = puakma.util.Util.base64Encode(buf);
					//System.out.println(s);
					sb.append("<obj name=\"" + sKey + "\">");
					sb.append(s);
					sb.append("</obj>\r\n");

					//System.out.println(sb.toString());
				}
				catch(Exception r){}
			}
		}

		return sb;
	}

	/**
	 * Determines if this session is a candidate for clustering.
	 * @return
	 */
	public boolean shouldSynchAcrossCluster()
	{
		//return (m_bObjectHasChanged || m_bSynchAcrossCluster) && !isSystemSession();
		return m_bSynchAcrossCluster && !isSystemSession();
	}


	/**
	 * Build the session properties based on the passed xml document.
	 * @param currentNode
	 */
	public boolean populateSessionFromXML(Node currentNode) throws Exception
	{
		boolean bProcessedAll = false;      

		//Node currentNode = nlRoot.item(0);
		String sName = currentNode.getNodeName();
		if(sName.equals("session"))
			getSessionParts(currentNode, this);
		else
			return false; //dunno what this request was
		NodeList nlAllItems = currentNode.getChildNodes();
		for(int i=0; i<nlAllItems.getLength(); i++)
		{
			//first node defines the service and method
			Node childNode = nlAllItems.item(i);
			if(childNode.getNodeType()==Node.ELEMENT_NODE)
			{
				bProcessedAll = true;
				sName = childNode.getNodeName();
				if(sName.equals("uname")) this.userName = getUserNameFromXML(childNode);
				if(sName.equals("fname")) this.firstName = getFirstNameFromXML(childNode);
				if(sName.equals("lname")) this.lastName = getLastNameFromXML(childNode);
				if(sName.equals("uagent")) this.userAgent = getUserAgentFromXML(childNode);
				if(sName.equals("ip")) this.internetAddress = getAddressFromXML(childNode);
				if(sName.equals("auth")) this.authenticatorUsed = getAuthenticatorFromXML(childNode);
				if(sName.equals("roles")) getSessionRolesFromXML(childNode, this);
				if(sName.equals("objects")) getSessionObjectsFromXML(childNode, this);
			}
		}      

		return bProcessedAll;
	}

	/**
	 *
	 * @param currentNode
	 * @param sess
	 */
	private void getSessionParts(Node currentNode, pmaSession sess) throws Exception
	{
		final String DATE_FORMAT="yyyy-MM-dd HH:mm:ss:SSS";

		NamedNodeMap nmSession = currentNode.getAttributes();
		Node att = nmSession.getNamedItem("id");
		sess.sessionID = Long.parseLong( att.getNodeValue() );

		att = nmSession.getNamedItem("timeout");
		sess.setSessionTimeOut( Integer.parseInt(att.getNodeValue()) );


		att = nmSession.getNamedItem("su");
		sess.m_sServerUnique = att.getNodeValue();

		att = nmSession.getNamedItem("login");
		sess.loginTime = puakma.util.Util.makeDate(att.getNodeValue(), DATE_FORMAT);

		att = nmSession.getNamedItem("trans");
		sess.lastTransaction = puakma.util.Util.makeDate(att.getNodeValue(), DATE_FORMAT);

	}

	/**
	 *
	 * @param currentNode
	 * @return
	 */
	private String getUserNameFromXML(Node currentNode)
	{
		Node nText = currentNode.getChildNodes().item(0);
		if(nText==null || nText.getNodeType()!=Node.TEXT_NODE) return "";
		String s = nText.getNodeValue();
		if(s.length()==0) return s;
		return Util.stringFromUTF8(Util.base64Decode(s));
	}

	/**
	 *
	 * @param currentNode
	 * @return
	 */
	private String getAuthenticatorFromXML(Node currentNode)
	{
		Node nText = currentNode.getChildNodes().item(0);
		if(nText==null || nText.getNodeType()!=Node.TEXT_NODE) return "";
		String s = nText.getNodeValue();
		return s;
	}

	/**
	 *
	 * @param currentNode
	 * @return
	 */
	private InetAddress getAddressFromXML(Node currentNode) throws Exception
	{
		Node nText = currentNode.getChildNodes().item(0);
		if(nText==null || nText.getNodeType()!=Node.TEXT_NODE) return null; //throw new Exception("No IP Node");
		String s = nText.getNodeValue();
		if(s.length()==0) return null; //throw new Exception("IP Address not specified");
		return InetAddress.getByName(s);
	}


	/**
	 *
	 * @param currentNode
	 * @return
	 */
	private String getFirstNameFromXML(Node currentNode)
	{
		Node nText = currentNode.getChildNodes().item(0);
		if(nText==null || nText.getNodeType()!=Node.TEXT_NODE) return "";
		String s = nText.getNodeValue();
		if(s.length()==0) return s;
		return Util.stringFromUTF8(Util.base64Decode(s));
	}

	/**
	 *
	 * @param currentNode
	 * @return
	 */
	private String getLastNameFromXML(Node currentNode)
	{
		Node nText = currentNode.getChildNodes().item(0);
		if(nText==null || nText.getNodeType()!=Node.TEXT_NODE) return "";
		String s = nText.getNodeValue();
		if(s.length()==0) return s;
		return Util.stringFromUTF8(Util.base64Decode(s));
	}

	/**
	 *
	 * @param currentNode
	 * @return
	 */
	private String getUserAgentFromXML(Node currentNode)
	{
		Node nText = currentNode.getChildNodes().item(0);
		if(nText==null || nText.getNodeType()!=Node.TEXT_NODE) return "";
		String s = nText.getNodeValue();
		if(s.length()==0) return s;
		return Util.stringFromUTF8(Util.base64Decode(s));
	}

	/**
	 *
	 * @param currentNode
	 * @param sess
	 * @throws Exception
	 */
	private void getSessionRolesFromXML(Node currentNode, pmaSession sess) throws Exception
	{
		UserRoles ur=null;
		NodeList nlAppRole = currentNode.getChildNodes();
		for(int i=0; i<nlAppRole.getLength(); i++)
		{
			Node nAppRole = nlAppRole.item(i);
			if(nAppRole.getNodeType()==Node.ELEMENT_NODE)
			{
				if(nAppRole.getNodeName().equals("approle"))
				{
					NamedNodeMap nmSession = nAppRole.getAttributes();
					Node app = nmSession.getNamedItem("app");
					String sAppName = app.getNodeValue();
					ur = new UserRoles(sAppName);
					//System.out.println("role for app=" + sAppName);
					NodeList nlRoles = nAppRole.getChildNodes();
					for(int k=0; k<nlRoles.getLength(); k++)
					{
						Node nRole = nlRoles.item(k);
						if(nRole.getNodeType()==Node.ELEMENT_NODE)
						{
							if(nRole.hasChildNodes())
							{
								String sRole = nRole.getFirstChild().getNodeValue();
								ur.addRole( Util.stringFromUTF8(puakma.util.Util.base64Decode(sRole)) );
								//System.out.println("role=[" + sRole + "]");
							}
						}
					}//for
					sess.addUserRolesObject(ur);
				}
			}
		}//for
	}

	/**
	 *
	 * @param currentNode
	 * @param sess
	 * @throws Exception
	 */
	private void getSessionObjectsFromXML(Node currentNode, pmaSession sess) throws Exception
	{
		Object obj=null;
		NodeList nlObject = currentNode.getChildNodes();
		for(int i=0; i<nlObject.getLength(); i++)
		{
			Node nObject = nlObject.item(i);
			if(nObject.getNodeType()==Node.ELEMENT_NODE)
			{
				NamedNodeMap nmObj=nObject.getAttributes();
				Node nName = nmObj.getNamedItem("name");
				Node nData = nObject.getFirstChild();
				String sKey=nName.getNodeValue();
				String s = nData.getNodeValue();
				byte buf[] = puakma.util.Util.base64Decode(s);
				if(buf!=null)
				{
					ByteArrayInputStream bais = new ByteArrayInputStream(buf);
					ObjectInputStream ois = new ObjectInputStream( bais );
					obj = ois.readObject();
					//System.out.println("key="+sKey+ " data="+s);
					sess.addSessionObject(sKey, obj);
				}
			}
		}//for
	}

	/**
	 * 
	 * @param bSet
	 */
	public void setObjectChanged(boolean bSet)
	{
		m_bObjectHasChanged=bSet;
	}

	public void setObjectChanged()
	{
		setObjectChanged(true);
	}

	/**
	 * Sets the timeout in minutes for this session. -1 will use the system session
	 * timeout.
	 * @param iNewTimeOutMins - a value in minutes
	 */
	public synchronized void setSessionTimeOut(int iNewTimeOutMins)
	{
		if(iNewTimeOutMins<0) iNewTimeOutMins=-1;
		sessionTimeoutMinutes = iNewTimeOutMins;
	}

	public String getErrorSource()
	{
		return "pmaSession";
	}

	public String getErrorUser()
	{
		return userName;
	}

	public Date getSSOExpiryDate() 
	{		
		return ssoExpiryDate;
	}

	public synchronized void setSSOExpiryDate(Date dtNewExpiryDate) 
	{		
		ssoExpiryDate = dtNewExpiryDate;
		setObjectChanged();
	}
}