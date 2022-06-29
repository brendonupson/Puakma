/* ***************************************************************
pmaSystem.java
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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.Date;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Locale;
import java.util.Properties;
import java.util.StringTokenizer;
import java.util.TimeZone;
import java.util.Vector;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import puakma.addin.http.TornadoApplication;
import puakma.addin.http.TornadoServer;
import puakma.addin.http.TornadoServerInstance;
import puakma.addin.http.action.SharedActionClassLoader;
import puakma.addin.http.document.DesignElement;
import puakma.error.ErrorDetect;
import puakma.error.pmaLog;
import puakma.jdbc.dbConnectionPoolManager;
import puakma.security.LoginResult;
import puakma.security.PropertyEncoder;
import puakma.security.pmaAuthenticator;
import puakma.server.AddInMessage;
import puakma.server.pmaServer;
import puakma.util.Util;

/** This is the System class which should act like the glue to hold all
 * the pieces together. This class should never be garbage collected and should
 * live as long as the server is running.
 * Code started: June 13, 2001 Brendon Upson
 * 
 * Note: v5.1.0 9.March.2015 Changed all StringBuffer to StringBuilder so minimum target is now Java 1.5
 */
public class pmaSystem implements ErrorDetect
{
	//	these are the version strings for reporting to the addins etc.
	private final String PUAKMA_VERSION="6.0.24";
	private final int PUAKMA_BUILD=1072;
	private final String PUAKMA_BUILD_DATE="29 Jun 2022"; 
	private final String PUAKMA_VERSION_TYPE = "Enterprise Server Platform";
	private final String PUAKMA_VERSION_STRING="Puakma " + PUAKMA_VERSION_TYPE + " v" + PUAKMA_VERSION + " Build:" + PUAKMA_BUILD + " - " + PUAKMA_BUILD_DATE;

	public final static String SYSTEM_ACCOUNT = "SYSTEM";

	public pmaLog pErr;
	public Date dtStart= new Date(); //the time the server is started
	public String SystemHostName;
	public final static String PUAKMA_FILEEXT_SYSTEMKEY="PMA_FILEEXT";
	public final static String PUAKMA_DEFAULT_FILEEXT = ".pma";


	private Properties m_propMessages=new Properties(); //error/info messages language specific
	private Properties m_propConfig=new Properties(); //configuration parameters
	private File TempDir = null;
	private String m_sSystemDriverClass, m_sSystemDBURL, m_sSystemDBName, m_sSystemUser, m_sSystemPassword;
	private String m_sConfigFilePath="";
	private long m_lNextSessionID=0;
	private FileInputStream m_fs;
	private int m_iDebugLevel=0;
	private Hashtable m_hSessionList=new Hashtable(500);
	private Hashtable m_htExcludeAddresses=new Hashtable();
	private int m_iMaxSessions=-1; 
	private int m_iSessionTimeout=90; //90 minute default
	private int m_iAnonymousSessionTimeout=90;
	private boolean m_bRunning=true;
	private long m_lUniqueNumber=0; //do NOT access this property!! see getNextUniqueNumber()
	private Vector m_vAuthenticators = new Vector();
	private pmaServer m_pServer = null;
	private boolean m_bLogSessions=false;
	private boolean m_bUseExactAuthenticator=false; 
	private final static String SAVE_SESSION_FILE = "sessions.sav";
	private final static String PUAKMA_RESTART = "$$PUAKMA_RESTART";
	private boolean m_bSaveSessionsOnShutdown=false;
	private SystemContext m_sysCtx;  
	//private Hashtable m_htActionClassLoaders = new Hashtable();
	private PropertyEncoder m_propEnc;
	//private Cache m_cacheObject;
	private Hashtable m_htGlobalObjects = new Hashtable();

	private int m_iSystemConnGetCount=0;
	private int m_iSystemConnReleaseCount=0;


	private dbConnectionPoolManager m_DBPoolMgr;
	private InetAddress m_addressLocal;

	/**
	 * Refreshes all values from the config file that the server was started with.
	 * This is so we can reload the properties after the server has started.
	 */
	public void reloadConfig()
	{
		loadConfig(m_sConfigFilePath);
	}

	/**
	 * get a System message in the local server language
	 */
	public String getSystemMessageString(String sMessageName)
	{
		return m_propMessages.getProperty(sMessageName, sMessageName);
	}

	/**
	 * Used to retrieve config file properties
	 */
	public String getConfigProperty(String sPropertyName)
	{ 
		try
		{
			return m_propEnc.decode(m_propConfig.getProperty(sPropertyName));
		}
		catch(Exception e){}

		return null;
	}

	/**
	 * Used to decode ad hoc values. These are things like passwords stored in the DB.
	 */
	public String decodeValue(String sProperty)
	{
		try
		{
			return m_propEnc.decode(Util.trimSpaces(sProperty));
		}
		catch(Exception e){}

		return null;
	}

	/**
	 * Used to encode ad hoc values. These are things like passwords stored in the DB.
	 */
	public String encodeValue(String sProperty)
	{
		try
		{
			return m_propEnc.encode(sProperty);
		}
		catch(Exception e){}

		return null;
	}

	/**
	 * Get an enumeration of all property names from the config file.
	 */
	public Enumeration getConfigPropertyNames()
	{
		return m_propConfig.propertyNames();
	}

	/**
	 *
	 */
	private void loadConfig(String sConfigFilePath)
	{          
		System.out.println(PUAKMA_VERSION_STRING);
		System.out.println("Loading configuration from: " + sConfigFilePath);

		System.setProperty("networkaddress.cache.ttl", "0"); //disable DNS caching

		try
		{
			m_fs = new FileInputStream(sConfigFilePath);
			m_propConfig.clear();
			m_propConfig.load(m_fs);
			//get the existing system key if one exists
			String sSystemKey = m_propConfig.getProperty("SystemKey");      
			m_propEnc = new PropertyEncoder(sSystemKey);
			//check and encode the file, hashing the cleartext passwords
			m_propEnc.encodeConfigFile(sConfigFilePath);
			//now reload the properties file
			m_fs = new FileInputStream(sConfigFilePath);
			m_propConfig.load(m_fs);
		}
		catch(Exception e)
		{
			System.out.println("Error loading system config from: " + sConfigFilePath + " " + e.toString());
			return;
		}

		SystemHostName = getConfigProperty("SystemHostName");
		m_sSystemDBName = getConfigProperty("SystemDBName");
		if(m_sSystemDBName==null) m_sSystemDBName = "PUAKMA";

		m_sSystemDBURL = getConfigProperty("SystemDBURL");
		m_sSystemUser = getConfigProperty("SystemUser");
		m_sSystemPassword = getConfigProperty("SystemPW");
		m_sSystemDriverClass = getConfigProperty("SystemDriverClass");
		String szTemp = getConfigProperty("SystemTempDir");
		if(szTemp!=null)
			TempDir = new File(szTemp);
		else
		{
			TempDir = new File("../temp"); //try the temp dir relative to us
			if(!TempDir.isDirectory() || !TempDir.exists())
				TempDir = new File("/"); //???Just dump it in the root?        
		}

		szTemp = getConfigProperty("SystemDebugLevel");
		if(szTemp!=null)
		{
			try{m_iDebugLevel = Integer.parseInt(szTemp);}catch(Exception kk){}
			m_iDebugLevel = Math.abs(m_iDebugLevel); //just in case someone uses a neg number
			if(m_iDebugLevel!=0) System.out.println("Debug Level: " + m_iDebugLevel);
		}

		String szLanguageFilePath = getConfigProperty("LanguageFilePath");
		System.out.println("Loading language file: " + szLanguageFilePath);
		if(szLanguageFilePath.length()>0)
		{
			try
			{
				FileInputStream fsLang = new FileInputStream(szLanguageFilePath);
				try{m_propMessages.load(fsLang);} catch(IOException ioe){System.out.println("Error loading language properties from: " + szLanguageFilePath + " " + ioe.toString());}
			}
			catch(FileNotFoundException e)
			{
				System.out.println("Error loading language file from: " + szLanguageFilePath + " " + e.toString());
			}
		}
		String szSessionTimeout = getConfigProperty("SessionTimeout");
		if(szSessionTimeout!=null)
		{
			try{m_iSessionTimeout=Integer.parseInt(szSessionTimeout);}
			catch(NumberFormatException nfe){}
		}
		System.out.println("Session Timeout: " + m_iSessionTimeout + " min.");

		szSessionTimeout = getConfigProperty("AnonymousSessionTimeout");
		if(szSessionTimeout!=null)
		{
			try{m_iAnonymousSessionTimeout=Integer.parseInt(szSessionTimeout);}
			catch(NumberFormatException nfe){}
		}
		System.out.println("Anonymous Session Timeout: " + m_iAnonymousSessionTimeout + " min.");


		//if(m_bEvaluationVersion) System.out.println("EVALUATION VERSION --- http://www.puakma.net");

		m_bLogSessions = false;
		szTemp = getConfigProperty("LogSessions");
		if(szTemp!=null && szTemp.equals("1")) m_bLogSessions=true;

		m_bSaveSessionsOnShutdown = false;
		szTemp = getConfigProperty("SaveSessionsOnShutdown");
		if(szTemp!=null && szTemp.equals("1")) m_bSaveSessionsOnShutdown=true;

		szTemp = getConfigProperty("MaxSessions");
		if(szTemp!=null)
		{
			try{m_iMaxSessions=Integer.parseInt(szTemp);}
			catch(NumberFormatException nfe){}
		}

		m_bUseExactAuthenticator = false;
		szTemp = getConfigProperty("UseExactAuthenticator");
		if(szTemp!=null && szTemp.equals("1")) m_bUseExactAuthenticator=true;

		szTemp =getConfigProperty("HTTPPuakmaFileExt");
		if(szTemp!=null && szTemp.length()>0 && szTemp.indexOf('.')==0)
		{      
			System.setProperty(PUAKMA_FILEEXT_SYSTEMKEY, szTemp);
		}
		else
			System.setProperty(PUAKMA_FILEEXT_SYSTEMKEY, PUAKMA_DEFAULT_FILEEXT);

		reloadIPExcludeList();
		System.out.println("");
		System.out.println("");
	}

	/**
	 * returns the string URL to the system database
	 */
	public String getSystemDBURL()
	{
		return m_sSystemDBURL + m_sSystemDBName;
	}

	/**
	 * returns the system database driver ie: "org.gjt.mm.mysql"
	 */
	public String getSystemDBDriver()
	{
		return new String(m_sSystemDriverClass);
	}

	/**
	 * returns the system database username
	 */
	public String getSystemDBUserName()
	{
		return m_sSystemUser;
	}

	/**
	 * returns the system database username
	 */
	public String getSystemDBPassword()
	{
		return m_sSystemPassword;
	}

	/**
	 * 
	 * @param sConfigFilePath
	 */
	public pmaSystem(String sConfigFilePath)
	{
		init(sConfigFilePath, null);
	}

	/**
	 * 
	 * @param sConfigFilePath
	 * @param paramServer
	 */
	public pmaSystem(String sConfigFilePath, pmaServer paramServer)
	{
		init(sConfigFilePath, paramServer);
	}

	private void init(String sConfigFilePath, pmaServer paramServer)
	{
		m_pServer = paramServer;
		m_sConfigFilePath = sConfigFilePath;
		getLocalAddress(true);
		loadConfig(sConfigFilePath);
		long lLogMaxKB = Util.toInteger(getConfigProperty("LogFileMaxSizeKB"));
		pErr = new pmaLog(this, getConfigProperty("LogDateFormat"), getConfigProperty("LogFile"), (lLogMaxKB*1024));
		pErr.doInformation("pmaSystem.Instantiate", new String[]{SystemHostName}, this);
		m_sysCtx = new SystemContext(this); //create system-wide session context

		loadAuthenticators();

		loadSavedSessions();  

		int iMaxConnectionCount = 50;
		String sPoolSize = getConfigProperty("SystemDefaultPoolSize");
		long lPoolSize = Util.toInteger(sPoolSize);
		if(lPoolSize>0) iMaxConnectionCount = (int)lPoolSize;

		int iPoolConnectionTimeoutMS = 15000;
		String sPoolTimeout = getConfigProperty("SystemDefaultPoolTimeoutSeconds");
		long lPoolTimeout = Util.toInteger(sPoolTimeout);
		if(lPoolTimeout>0) iPoolConnectionTimeoutMS = (int)lPoolTimeout*1000;//convert to seconds

		int iPoolConnectionExpireSeconds = 120;
		String sPoolExpire = getConfigProperty("SystemDefaultPoolExpireSeconds");
		long lPoolExpire = Util.toInteger(sPoolExpire);
		if(lPoolExpire>0) iPoolConnectionExpireSeconds = (int)lPoolExpire;

		m_DBPoolMgr = new dbConnectionPoolManager(new SystemContext(this), "SYSTEM");
		try
		{
			m_DBPoolMgr.createPooler(SystemContext.DBALIAS_SYSTEM, iMaxConnectionCount, iPoolConnectionTimeoutMS, 0,
					iPoolConnectionExpireSeconds, getSystemDBDriver(), getSystemDBURL(),
					getSystemDBUserName(), getSystemDBPassword() );
		}
		catch(Exception e){}

	}


	/*
	 * For session management
	 * @return The next available Session ID
	 */
	private long getNextSessionID()
	{
		pErr.doDebug(pmaLog.DEBUGLEVEL_FULL, "getNextSessionID()", this);
		m_lNextSessionID++;
		return m_lNextSessionID;
	}

	/**
	 * Check if the sessions have expired and removes them
	 */
	public synchronized int dropSessions(boolean bDropAllSessions)
	{
		int iDropped=0;
		pErr.doDebug(pmaLog.DEBUGLEVEL_FULL, "dropSessions()", this);

		Enumeration eSessions = m_hSessionList.elements();

		while(eSessions.hasMoreElements())
		{
			pmaSession session = (pmaSession)eSessions.nextElement();
			if(bDropAllSessions)
			{
				if(m_bLogSessions) pErr.doInformation("pmaSystem.DropSession", new String[]{session.getFullSessionID(),session.userName}, this);
				m_hSessionList.remove(session.getFullSessionID());
				//if(!expiredSession.isLocked()) expiredSession = null;
				iDropped++;
			}
			else //only drop those that have expired
			{
				if(session.hasExpired(m_iSessionTimeout, m_iAnonymousSessionTimeout))
				{
					if(m_bLogSessions) pErr.doInformation("pmaSystem.DropSession", new String[]{session.getFullSessionID(),session.userName}, this);
					m_hSessionList.remove(session.getFullSessionID());
					//if(!expiredSession.isLocked()) expiredSession = null; //let gc sort it out??
					iDropped++;
				}
			}//else
		}//while

		return iDropped;
	}


	/**
	 * drops a session specified in the szSessionID
	 * @param sFullSessionID
	 */
	public synchronized void dropSessionID(String sFullSessionID)
	{
		m_hSessionList.remove(sFullSessionID);
	}

	/**
	 * Used by the cluster manager to register sessions
	 * @param sess
	 */
	public void registerSession(pmaSession sess)
	{
		//System.out.println("registering: [" + sess.getFullSessionID() +"]");
		sess.setSystem(this);
		if(m_hSessionList.containsKey(sess.getFullSessionID()))
		{
			boolean bUpdate = true;
			pmaSession sessOld = (pmaSession)m_hSessionList.get(sess.getFullSessionID());
			if(sessOld!=null)
			{
				//don't update if the transaction is older than the session we already have
				if(sessOld.lastTransaction.after(sess.lastTransaction)) bUpdate=false;
				else m_hSessionList.remove(sess.getFullSessionID());
			}
			if(bUpdate) m_hSessionList.put(sess.getFullSessionID(), sess);
		}
		else
			m_hSessionList.put(sess.getFullSessionID(), sess);
		//		System.out.println("listsize="+hSessionList.size());
	}

	/**
	 * Drops a session belonging to the named user szWho
	 * @return the number of sessions dropped
	 */
	public synchronized int dropSession(String sWho)
	{
		int iDropped=0;
		pErr.doDebug(pmaLog.DEBUGLEVEL_FULL, "dropSession()", this);
		if(sWho==null) return 0;

		X500Name nmWho = new X500Name(sWho);
		String sWhoLow = nmWho.getCanonicalName().toLowerCase();

		Enumeration eSessions = m_hSessionList.elements();

		while(eSessions.hasMoreElements())
		{
			pmaSession session = (pmaSession)eSessions.nextElement();
			X500Name nmSession = new X500Name(session.userName);
			if(nmSession.getCanonicalName().toLowerCase().equals(sWhoLow))
			{
				if(m_bLogSessions) pErr.doInformation("pmaSystem.DropSession", new String[]{session.getFullSessionID(),session.userName}, this);
				m_hSessionList.remove(session.getFullSessionID());
				if(!session.isLocked()) session = null;
				iDropped++;
			}

		}//while
		return iDropped;
	}

	/**
	 * Return an ArrayList of session objects that match the username passed (common or canonical)
	 */
	public ArrayList getSessionsByUserName(String szWho)
	{
		ArrayList arr = new ArrayList();
		if(szWho==null) return arr;

		X500Name nmWho = new X500Name(szWho);
		//String sWhoLow = nmWho.getCanonicalName().toLowerCase();

		Enumeration eSessions = m_hSessionList.elements();

		while(eSessions.hasMoreElements())
		{
			pmaSession sess = (pmaSession)eSessions.nextElement();
			X500Name nmSession = new X500Name(sess.userName);
			if(nmSession.equals(nmWho) || nmSession.getCommonName().toLowerCase().equals(nmWho.getCommonName().toLowerCase()))
			{
				arr.add(new SessionContext(sess));
			}

		}//while
		return arr;
	}

	/**
	 * 
	 */
	public void clearAllSessionObjectsWithClassLoader()
	{      
		Enumeration eSessions = m_hSessionList.elements();

		while(eSessions.hasMoreElements())
		{
			pmaSession sess = (pmaSession)eSessions.nextElement();
			Hashtable ht = sess.getAllSessionObjects();
			Enumeration keys = ht.keys();
			while(keys.hasMoreElements())
			{
				String sKey = (String)keys.nextElement();
				Object obj = ht.get(sKey);
				Object objCL = obj.getClass().getClassLoader();
				if(objCL instanceof puakma.addin.http.action.SharedActionClassLoader)
				{
					//System.out.println("removing: "+sKey + " from " + sess.UserName);
					ht.remove(sKey);
				}
			}
		}//while
	}

	private InetAddress getLocalAddress(boolean bSet)
	{
		try
		{
			//this is a blocking call and may take 20 seconds to return if host is not configured correctly!
			// http://portal2portal.blogspot.com.au/2011/12/slow-performance-or-hang-in-hostname.html
			if(bSet) m_addressLocal = InetAddress.getLocalHost(); 				
		}
		catch(Exception e)
		{
			e.printStackTrace();
		}

		return m_addressLocal;
	}

	/**
	 * Creates a new session object and adds it to the hashtable
	 * @return the session object
	 */
	public pmaSession createNewSession(InetAddress addr, String szUserAgent)
	{
		pErr.doDebug(pmaLog.DEBUGLEVEL_FULL, "createNewSession()", this);
		if(szUserAgent==null) szUserAgent = "?";
		//check to see if we have too many people on this server
		if(m_iMaxSessions>=0 && getSessionCount()+1 > m_iMaxSessions) return null;
		try
		{
			if(addr==null) 
			{				 
				addr = getLocalAddress(false);
			}
		}
		catch(Exception e){}

		pmaSession pSession = new pmaSession(this, getNextSessionID(), szUserAgent, addr);

		setDefaultTimeZoneAndLocale(new SessionContext(pSession));


		m_hSessionList.put(pSession.getFullSessionID(), pSession);
		if(m_bLogSessions) pErr.doInformation("pmaSystem.NewConnection", new String[]{pSession.getFullSessionID(), addr.getHostAddress()}, this);
		return pSession;
	}

	/**
	 * Sets the session context properties if they don't already exist
	 * @param pSession
	 */
	private void setDefaultTimeZoneAndLocale(SessionContext pSession)
	{
		if(pSession==null) return;

		Locale loc = pSession.getLocale();
		if(loc==null)
		{
			String sDefaultLocale = getConfigProperty("SessionDefaultLocale"); //eg "en_AU"
			if(sDefaultLocale!=null && sDefaultLocale.length()>0)
			{
				String sLanguage = sDefaultLocale;
				String sCountry = "";
				ArrayList arr = Util.splitString(sDefaultLocale, '_');
				if(arr.size()>0) sLanguage = (String) arr.get(0);
				if(arr.size()>1) sCountry = (String) arr.get(1);
				pSession.setLocale(new Locale(sLanguage, sCountry));
			}
		}//null locale

		TimeZone tz = pSession.getTimeZone();
		if(tz==null)
		{
			String sDefaultTimeZone = getConfigProperty("SessionDefaultTimeZone"); //eg "Australia/Sydney"
			if(sDefaultTimeZone!=null && sDefaultTimeZone.length()>0)
			{
				pSession.setTimeZone(TimeZone.getTimeZone(sDefaultTimeZone));
			}
		}//null timezone

	}

	/**
	 * Creates a new system session object and adds it to the hashtable
	 * @return the session object
	 */
	public pmaSession createSystemSession(String sUserAgent)
	{
		pErr.doDebug(pmaLog.DEBUGLEVEL_FULL, "createSystemSession()", this);

		InetAddress inetAddr=null;
		try
		{
			inetAddr = InetAddress.getLocalHost();
		}
		catch(Exception e){}

		pmaSession pSession = new pmaSession(this, getNextSessionID(), sUserAgent, inetAddr);
		pSession.firstName="";
		pSession.lastName=SYSTEM_ACCOUNT;
		pSession.userName=SYSTEM_ACCOUNT;
		m_hSessionList.put(pSession.getFullSessionID(), pSession);
		if(m_bLogSessions) pErr.doInformation("pmaSystem.NewConnection", new String[]{pSession.getFullSessionID(), pSession.userName}, this);
		return pSession;
	}

	/**
	 *
	 */
	public void saveActiveSessionsToFile(String sFileName)
	{      
		try
		{
			File fSess = new File(sFileName);
			FileOutputStream fout = new FileOutputStream(fSess);
			Enumeration en = m_hSessionList.elements();           
			fout.write("<?xml version=\"1.0\" encoding=\"utf-8\"?>\r\n".getBytes());
			fout.write("<sessiondata>\r\n".getBytes());
			while(en.hasMoreElements())
			{
				pmaSession sess = (pmaSession)en.nextElement();
				StringBuilder sbSession = sess.getXMLRepresentation();
				fout.write(sbSession.toString().getBytes());
			}//for
			fout.write("</sessiondata>\r\n".getBytes());
			fout.flush();
			fout.close();
		}
		catch(Exception e)
		{
			pErr.doError("pmaSystem.SaveSessions", new String[]{sFileName, e.toString()}, this);
		}
	}

	/**
	 * Attempts to find an existing session object
	 * @return the session object
	 */
	public pmaSession getSession(String sFullSessionID)
	{
		pErr.doDebug(pmaLog.DEBUGLEVEL_FULL, "getSession()", this);
		pmaSession pSess = (pmaSession)m_hSessionList.get(sFullSessionID);
		//long lStart = System.currentTimeMillis();
		if(pSess==null)
		{
			if(m_pServer!=null && m_pServer.isAddInLoaded("CLUSTER"))
			{
				pErr.doDebug(pmaLog.DEBUGLEVEL_FULL, "Requesting cluster session pull for: "+sFullSessionID, this);
				//tell cluster to pull it
				AddInMessage msg = new AddInMessage();
				msg.setParameter("action", "getSession");
				msg.setParameter("SessionID", sFullSessionID);
				sendMessage("CLUSTER", msg);
				//try to get it again
				pSess = (pmaSession)m_hSessionList.get(sFullSessionID);
				//takes about 190ms over a 100Mbit LAN
				//System.out.println("getSession took " + (System.currentTimeMillis()-lStart) + "ms");
			}
		}
		return pSess;
	}


	/**
	 * Determines if an addin is loaded
	 */
	public boolean isAddInLoaded(String sAddInName)
	{
		if(m_pServer==null) return false;
		return m_pServer.isAddInLoaded(sAddInName);
	}

	/**
	 * Get a list of addin class names currently loaded.
	 */
	public String[] getLoadedAddInNames()
	{
		if(m_pServer==null) return new String[]{""};
		return m_pServer.getLoadedAddInNames();
	}

	/**
	 * Determines if a given sesison exists on this server
	 * @return true if it exists
	 */
	public boolean sessionExists(String sFullSessionID)
	{
		pmaSession pSess = (pmaSession)m_hSessionList.get(sFullSessionID);
		if(pSess==null) return false;

		return true;
	}

	public int getDebugLevel()
	{
		return m_iDebugLevel;
	}


	public String getErrorSource()
	{
		return "pmaSystem";
	}

	public String getErrorUser()
	{
		return SYSTEM_ACCOUNT;
	}

	/**
	 *
	 */
	private void loadSavedSessions()
	{    
		String sSessionSaveFile = getSavedSessionFilePath();
		try
		{          
			File fSess = new File(sSessionSaveFile);
			if(!fSess.exists()) return;
			pErr.doInformation("pmaSystem.LoadSessions", new String[]{sSessionSaveFile}, this);
			FileInputStream fin = new FileInputStream(fSess); 
			DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();      
			DocumentBuilder parser = dbf.newDocumentBuilder();
			org.w3c.dom.Document document = parser.parse(fin);
			NodeList rootNL = document.getElementsByTagName("sessiondata"); //get root node
			Node nRoot = rootNL.item(0);
			NodeList nlSessions = nRoot.getChildNodes();
			int iCount=0;
			for(int i=0; i<nlSessions.getLength(); i++)
			{
				Node nSession = nlSessions.item(i);
				pmaSession sess = new pmaSession();      
				if(sess.populateSessionFromXML(nSession))
				{
					sess.setSynchAcrossCluster(false);
					sess.setObjectChanged(false);
					registerSession(sess);
					//System.out.println("Loaded: "+sess.toString());
					iCount++;
				}
			}          
			fin.close();
			pErr.doInformation("pmaSystem.LoadedSessions", new String[]{iCount+""}, this);
			//delete the session file
			fSess.delete(); //so we don't load them twice
		}
		catch(Exception e)
		{
			pErr.doError("pmaSystem.LoadSessionsError", new String[]{sSessionSaveFile, e.toString()}, this);
			puakma.util.Util.logStackTrace(e, new SystemContext(this), 999);
		}
	}

	/**
	 * makes the path for the file containing saved sessions
	 */
	public String getSavedSessionFilePath()
	{
		if(TempDir==null) return "/";
		return this.TempDir.getAbsolutePath() + File.separator + SAVE_SESSION_FILE;
	}

	/**
	 * Saves all active sessions to the default file
	 */
	private void saveActiveSessions()
	{       
		clearAllSessionObjectsWithClassLoader();
		String sSessionSaveFile = getSavedSessionFilePath();
		pErr.doInformation("pmaSystem.SaveSessions", new String[]{sSessionSaveFile}, this);
		saveActiveSessionsToFile(sSessionSaveFile);      
	}

	/**
	 * Used to set a flag which tells other modules to quit
	 */
	public void stopSystem()
	{
		pErr.doDebug(pmaLog.DEBUGLEVEL_FULL, "stopSystem()", this);
		if(m_bSaveSessionsOnShutdown) saveActiveSessions(); 
		dropSessions(true);
		m_bRunning=false;
	}

	/**
	 * Used to set a flag which tells other modules to quit
	 * Sets a flag to tell the controlling process it can restart
	 */
	public void restartSystem()
	{
		pErr.doDebug(pmaLog.DEBUGLEVEL_FULL, "restartSystem()", this);
		System.setProperty(PUAKMA_RESTART, "1");
		stopSystem();
	}

	public synchronized void setSaveSessionsOnShutdown(boolean bSave)
	{
		m_bSaveSessionsOnShutdown = bSave;
	}  



	/**
	 * Determines if the system is running or not
	 */
	public boolean isSystemRunning()
	{
		return m_bRunning;
	}

	/**
	 * Method to access the authentication chain to check if the user is in a group
	 * @param sessCtx
	 * @param sGroup
	 * @return true or false
	 */
	public boolean isUserInGroup(SessionContext sessCtx, String sGroup, String sURI)
	{
		//don't try to determine group memberships of the system account
		if(sessCtx.getUserName().equals(pmaSystem.SYSTEM_ACCOUNT)) return false;

		//System.out.println("isUserInGroup:"+sessCtx.getUserName() + " grp:" + szGroup + " exactauth:"+m_bUseExactAuthenticator);
		if(m_bUseExactAuthenticator)
		{
			for(int i=0; i<m_vAuthenticators.size(); i++)
			{
				pmaAuthenticator auth = (pmaAuthenticator)m_vAuthenticators.get(i);
				if(auth!=null && (auth.getClass().getName().equals(sessCtx.getAuthenticatorUsed()) || sessCtx.getAuthenticatorUsed().length()==0))
				{            
					return auth.isUserInGroup(sessCtx, sGroup, sURI);
				}
			}
		}
		else
		{
			//pmaAuthenticator auth = (pmaAuthenticator)vAuthenticators.get(0);
			//if(auth!=null) return auth.isUserInGroup(sessCtx, szGroup, sURI);
			//Check each authenticator, if any say yes, then let the user in
			for(int i=0; i<m_vAuthenticators.size(); i++)
			{
				pmaAuthenticator auth = (pmaAuthenticator)m_vAuthenticators.get(i);
				if(auth!=null)
				{            
					if(auth.isUserInGroup(sessCtx, sGroup, sURI)) return true;
				}
			}

		}

		return false;
	}


	/**
	 * Attempts to find the user based on the credentials in the document
	 * If successful, updates the session object with the new credentials
	 */
	public boolean loginSession(SessionContext pSession, Document docLogin)
	{
		pErr.doDebug(pmaLog.DEBUGLEVEL_FULL, "loginSession()", this);
		if(pSession==null || docLogin==null) return false;
		if(!pSession.getUserName().equals(pmaSession.ANONYMOUS_USER)) return true; //already logged in!

		String szLoginName = docLogin.getItemValue(Document.PAGE_USERNAME_ITEM);
		if(szLoginName==null || szLoginName.length()==0) return false;
		String sPassword = docLogin.getItemValue(Document.PAGE_PASSWORD_ITEM);
		String sURI = docLogin.getItemValue(Document.PAGE_REDIRECT_ITEM);

		return loginSession(pSession, szLoginName, sPassword, sURI);
	}

	/**
	 * Calls the authenticator to refresh the session properties. This is when a
	 * user tries to autologin using an ltpa token
	 */
	public boolean populateSession(SessionContext pSession, String sURI)
	{
		String szAuthenticatorUsed=null;
		LoginResult loginResult=null;
		for(int i=0; i<m_vAuthenticators.size(); i++)
		{
			pmaAuthenticator auth = (pmaAuthenticator)m_vAuthenticators.get(i);
			if(auth!=null)
			{
				loginResult = auth.populateSession(pSession.getUserName(), sURI);
				if(loginResult!=null && (loginResult.isFinalReturnCode()))
				{
					//record what authenticator was used to log in this session
					szAuthenticatorUsed = auth.getClass().getName();
					//System.out.println("break!");
					break;
				}
			}
		}//for

		if(loginResult!=null && loginResult.ReturnCode==LoginResult.LOGIN_RESULT_SUCCESS)
		{
			pSession.setAuthenticatorUsed(szAuthenticatorUsed);
			pSession.setFirstName(loginResult.FirstName);
			pSession.setLastName(loginResult.LastName);        
			pErr.doInformation("pmaSystem.NewLogin", new String[]{pSession.getUserName(), pSession.getHostAddress()}, this);
			pSession.removeAllUserRolesObjects();
			setDefaultTimeZoneAndLocale(pSession);
			//System.out.println("fname="+loginResult.FirstName + " " + pSession.getFirstName());
			//System.out.println("lname="+loginResult.LastName + " " + pSession.getLastName());

			return true;
		}

		return false;
	}


	/**
	 *
	 * @param pSession
	 * @param szLoginName
	 * @param szPassword
	 * @return true if the session was logged in
	 */
	public boolean loginSession(SessionContext pSession, String szLoginName, String szPassword, String sURI)
	{
		//long lStart = System.currentTimeMillis();
		//pErr.doDebug(0, "loginSession() A " + (System.currentTimeMillis()-lStart) + "ms", this);
		String szAuthenticatorUsed=null;
		LoginResult loginResult=null;
		for(int i=0; i<m_vAuthenticators.size(); i++)
		{
			pmaAuthenticator auth = (pmaAuthenticator)m_vAuthenticators.get(i);
			if(auth!=null)
			{
				loginResult = auth.loginUser(szLoginName, szPassword, pSession.getHostAddress(), pSession.getUserAgent(), sURI);
				if(loginResult!=null && (loginResult.isFinalReturnCode()))
				{
					//record what authenticator was used to log in this session
					szAuthenticatorUsed = auth.getClass().getName();
					pSession.setLoginName(szLoginName);
					break;
				}
			}
		}

		if(loginResult!=null && loginResult.ReturnCode!=LoginResult.LOGIN_RESULT_SUCCESS)
		{
			if(loginResult.ReturnCode==LoginResult.LOGIN_RESULT_INVALID_USER) pErr.doError("pmaSystem.AuthNone", new String[]{szLoginName}, this);
			return false;
		}
		else
		{
			if(loginResult!=null && loginResult.ReturnCode== LoginResult.LOGIN_RESULT_SUCCESS)
			{
				pSession.setAuthenticatorUsed(szAuthenticatorUsed);
				pSession.setFirstName(loginResult.FirstName);
				pSession.setLastName(loginResult.LastName);
				pSession.setUserName(loginResult.UserName);

				pErr.doInformation("pmaSystem.NewLogin", new String[]{pSession.getUserName(), pSession.getHostAddress()}, this);
				pSession.removeAllUserRolesObjects();
				if(isAddInLoaded("STATS"))
				{
					AddInMessage oMessage = new AddInMessage();
					oMessage.setParameter("Action", "Login");									
					sendMessage("STATS", oMessage);
				}
				//pErr.doDebug(0, "loginSession() A " + (System.currentTimeMillis()-lStart) + "ms", this);				
				//pErr.doDebug(0, "loginSession() B " + (System.currentTimeMillis()-lStart) + "ms", this);

				return true;
			}
		}
		//pErr.doDebug(0, "loginSession() Z " + (System.currentTimeMillis()-lStart) + "ms", this);

		return false;
	}


	/**
	 * Return all the session objects
	 */
	public Enumeration getSessionList()
	{
		pErr.doDebug(pmaLog.DEBUGLEVEL_FULL, "getSessionList()", this);
		return m_hSessionList.elements();
	}

	/**
	 * This is a unique number service. We can guarantee that numbers provided by this
	 * function will be unique for the life of the server.
	 */
	public synchronized long getNextUniqueNumber()
	{
		return m_lUniqueNumber++;
	}


	/**
	 * Load the authenticators specified in the config file
	 */
	private void loadAuthenticators()
	{
		pErr.doDebug(pmaLog.DEBUGLEVEL_FULL, "loadAuthenticators()", this);
		String szAuthList=getConfigProperty("Authenticators");
		String szAuthName;
		if(szAuthList != null)
		{
			StringTokenizer stk= new StringTokenizer(szAuthList, ",", false);
			while (stk.hasMoreTokens())
			{
				szAuthName = stk.nextToken();
				loadAuthenticator(szAuthName);
			}//end while
		}//end if
	}


	/**
	 * Load a single authenticator. Will be useful later when we want to dynamically
	 * load/unload these
	 */
	public void loadAuthenticator(String szAuthName)
	{
		pErr.doDebug(pmaLog.DEBUGLEVEL_FULL, "loadAuthenticator()", this);
		pmaAuthenticator pAuth;

		if(szAuthName==null) return;
		try
		{
			pAuth = (pmaAuthenticator)Class.forName(szAuthName).newInstance();
			pAuth.init(m_sysCtx);
			m_vAuthenticators.addElement(pAuth);
			pErr.doInformation("pmaSystem.AuthLoad", new String[]{szAuthName}, this);
		}
		catch(Exception e)
		{
			pErr.doError("pmaSystem.AuthError", new String[]{szAuthName, e.toString()}, this);
		}
	}

	/*
	 * Unloads an Authenticator. the name is the full package & class name
	 */
	public void unloadAuthenticator(String szAuthName, boolean bUnloadAll)
	{
		pErr.doDebug(pmaLog.DEBUGLEVEL_FULL, "unloadAuthenticator()", this);
		pmaAuthenticator pAuth;

		if(szAuthName==null) return;
		for(int i=0; i<m_vAuthenticators.size(); i++)
		{
			pAuth = (pmaAuthenticator)m_vAuthenticators.elementAt(i);
			String szName = pAuth.getClass().getName();
			if(szName.equals(szAuthName) || bUnloadAll)
			{
				m_vAuthenticators.removeElementAt(i);
				pErr.doInformation("pmaSystem.AuthUnLoad", new String[]{szName}, this);
			}
		}
	}

	/**
	 * Returns a strings in hours, minutes, seconds representing how long the
	 * server has been running
	 */
	public String getSystemUptime()
	{
		long msUpTime = System.currentTimeMillis() - dtStart.getTime();
		long days = msUpTime/1000/60/60/24;
		msUpTime -= days*24*60*60*1000;
		long hours = msUpTime/1000/60/60;
		msUpTime -= hours*60*60*1000;
		int minutes = (int)(msUpTime/1000/60);
		msUpTime -= minutes*60*1000;
		int seconds = (int)(msUpTime/1000);
		return days + "d " + hours + "h " + minutes + "m " + seconds + "s";
	}


	/**
	 * @return the number of errors logged since the system started
	 */
	public long getErrorCount()
	{
		return pErr.getErrorCount();
	}

	/**
	 * Clears the error counter
	 */
	public void clearErrorCount()
	{
		pErr.clearErrorCount();
	}

	/**
	 * @return the number of sessions currently on this server
	 */
	public int getSessionCount()
	{
		return m_hSessionList.size();
	}

	/**
	 * @return the maximum number of sessions allowed on this server
	 * -1 == unlimited
	 */
	public int getMaxSessionCount()
	{
		return m_iMaxSessions;
	}

	public File getTempDir()
	{
		return TempDir;
	}

	public File getConfigDir()
	{
		File fConfig = new File(m_sConfigFilePath);
		return fConfig.getParentFile();
	}

	/*public static boolean isEvaluationVersion()
  {
    return m_bEvaluationVersion;
  }*/





	public AddInMessage sendMessage(String sAddInClass, AddInMessage oMessage)
	{
		//TODO: support sending messages to other servers!  
		if(m_pServer==null) return oMessage;
		return m_pServer.sendMessage(sAddInClass, oMessage);
	}



	/**
	 * Issue a command to the server console
	 * @param szCommand
	 * @return the respnose from the server
	 */
	public String doConsoleCommand(String szCommand)
	{
		if(m_pServer==null) return "";
		return m_pServer.doConsoleCommand(szCommand);
	}


	public String getVersionString()
	{
		/*if(m_bEvaluationVersion)
      return "*** EVALUATION VERSION *** " + PUAKMA_VERSION_STRING;*/

		return PUAKMA_VERSION_STRING;
	}

	/**
	 * Determines if the current ip address is allowed to access this server. Checks
	 * the address exclude file. May implement an include file also later.
	 * //TODO wildcard address matching
	 */
	public boolean addressHasAccess(String sIPAddress)
	{
		if(sIPAddress==null) return true; //it could happen...
		if(isAddressExcluded(sIPAddress)) return false;
		return true;
	}

	/**
	 * Determines if the named address is in the exclude list
	 */
	private boolean isAddressExcluded(String sIPAddress)
	{
		if(sIPAddress==null) return false;
		if(this.m_htExcludeAddresses.containsKey(sIPAddress)) return true;

		return false;
	}


	/**
	 *
	 */
	public void reloadIPExcludeList()
	{
		m_htExcludeAddresses.clear();
		//m_htExcludeAddresses.put("192.168.0.3", "");
		String sFile = getConfigProperty("IPExcludeFile");
		if(sFile==null || sFile.length()==0) return;
		File fExclude = new File(sFile);
		if(!fExclude.isFile()) return;
		BufferedReader br = null;
		try
		{
			br = new BufferedReader(new InputStreamReader(new FileInputStream(fExclude)));
			String sLine = br.readLine();
			while(sLine!=null)
			{
				if(sLine.length()>0 && sLine.charAt(0)>'0' && sLine.charAt(0)<='9')
				{
					//System.out.println("added:[" + sLine + "]");
					m_htExcludeAddresses.put(sLine.trim(), "");
				}
				sLine = br.readLine();
			}
		}
		catch(Exception e)
		{
			//no error...
		}
		finally
		{
			try{ if(br!=null) br.close(); } catch(Exception e){}
		}
	}

	public void registerAddInToReceiveLogMessages(String sAddInName)
	{
		pErr.registerAddInToReceiveLogMessages(sAddInName);
	}

	public void deregisterAddInToReceiveLogMessages(String sAddInName)
	{
		pErr.deregisterAddInToReceiveLogMessages(sAddInName);
	}


	public String getVersion()
	{
		return PUAKMA_VERSION;
	}

	public int getBuild()
	{
		return PUAKMA_BUILD;
	}

	/*public int getSessionTimeOut()
  {
      return iSessionTimeout;
  }*/



	/**
	 * Close a classloader so that new design can be picked up correctly.
	 * Only drop the loader if we are saving java code.
	 */
	public void clearClassLoader(String szKey)
	{
		boolean bDropObjects = false;
		//System.out.println("Clearing classloader: "+szKey);
		TornadoServerInstance tsi = TornadoServer.getInstance();

		if(szKey==null || szKey.length()==0)
		{
			//m_htActionClassLoaders.clear();
			bDropObjects = tsi.clearClassLoader(null);			
		}
		else
		{
			if(szKey.endsWith("/"+DesignElement.DESIGN_TYPE_ACTION)
					|| szKey.endsWith("/"+DesignElement.DESIGN_TYPE_SCHEDULEDACTION)
					|| szKey.endsWith("/"+DesignElement.DESIGN_TYPE_LIBRARY)
					|| szKey.endsWith("/"+DesignElement.DESIGN_TYPE_BUSINESSWIDGET))
			{
				RequestPath rPath = new RequestPath(szKey); 
				String sBaseKey = rPath.getPathToApplication().toLowerCase(); // "/group/app.pma/"
				bDropObjects = tsi.clearClassLoader(sBaseKey);
				/*Enumeration en = m_htActionClassLoaders.keys();
				while(en.hasMoreElements())
				{
					String sKey = (String)en.nextElement(); // "/group/app.pma/3"
					if(sKey.startsWith(sBaseKey)) 
					{
						m_htActionClassLoaders.remove(sKey);
						bDropObjects = true;
					}
				}//while
				 */
			}
		}

		if(bDropObjects) 
		{
			clearAllSessionObjectsWithClassLoader();
			//if(isAddInLoaded("WIDGIE")) doConsoleCommand("reload WIDGIE");
			//if(isAddInLoaded("AGENDA")) doConsoleCommand("reload AGENDA");			
		}

	}

	public SharedActionClassLoader getActionClassLoader(RequestPath rPath)
	{
		TornadoServerInstance tsi = TornadoServer.getInstance();
		TornadoApplication ta = tsi.getTornadoApplication(rPath.getPathToApplication());

		if(ta.appExists())
		{
			return ta.getActionClassLoader();
		}

		return null;
	}


	/**
	 * Find the shared classloader object for a given app
	 */
	/*public synchronized SharedActionClassLoader getActionClassLoader(RequestPath rPath)
	{
		if(rPath==null) return null;
		//FIXME this should be in TornadoApplication ?
		SharedActionClassLoader sacl=null;
		String sKey = rPath.getPathToApplication().toLowerCase(); // + iDesignType;		
		boolean bNew = false;
		if(m_htActionClassLoaders.containsKey(sKey))
		{          
			sacl = (SharedActionClassLoader)m_htActionClassLoaders.get(sKey);
		}

		RequestPath rWild = new RequestPath(rPath.getPathToApplication());
		rWild.Group = "*";
		String sKeyWild = rWild.getPathToApplication().toLowerCase();
		if(m_htActionClassLoaders.containsKey(sKey))
		{          
			sacl = (SharedActionClassLoader)m_htActionClassLoaders.get(sKey);
		}

		if(sacl==null)
		{
			sacl = new SharedActionClassLoader(new SystemContext(this), rPath.Group, rPath.Application);
			m_htActionClassLoaders.put(sKey, sacl);
			System.out.println("Created new classloader: " + sKey);
			bNew = true;
		}

		//this.pErr.doDebug(0, "bNew="+bNew + " Classloader: " + sKey + " " + sacl.hashCode() + " this=" + this.hashCode() + " from:"+Thread.currentThread().getName(), this);

		return sacl;
	}*/

	/**
	 * 
	 * @param oItem
	 * @return
	 */
	public boolean addGlobalObject(String sKey, Object obj)
	{
		if(m_htGlobalObjects.containsKey(sKey)) removeGlobalObject(sKey);
		m_htGlobalObjects.put(sKey, obj);
		return true;
	}

	/**
	 * 
	 * @param sKey
	 * @return
	 */
	public Object getGlobalObject(String sKey)
	{
		if(sKey==null) return null;
		return m_htGlobalObjects.get(sKey);
	}

	/**
	 * Removes a named object from the temp storage area. Pass null to remove all objects
	 * @param sKey
	 * @return
	 */
	public boolean removeGlobalObject(String sKey)
	{
		if(sKey==null) 
		{
			if(m_htGlobalObjects.size()==0) return false;//no objects
			m_htGlobalObjects.clear();
			return true;
		}

		if(m_htGlobalObjects.remove(sKey)==null) return false;
		return true;
	}

	public Hashtable getAllGlobalObjects()
	{
		return m_htGlobalObjects;
	}

	public Connection getSystemConnection() throws Exception
	{		
		Connection cx=null;
		try
		{      
			cx = m_DBPoolMgr.getConnection(SystemContext.DBALIAS_SYSTEM);			
		}
		catch(Exception e)
		{
			//try to recreate the pool, cause this one is busted.
			//setDBPoolManager(m_DBPoolMgr);
			String szMessage = pmaLog.parseMessage(getSystemMessageString("pmaSystem.NoSystemConnection"), new String[]{e.toString()});
			throw new Exception(szMessage);
		}
		m_iSystemConnGetCount++;
		return cx;
	}

	public void releaseSystemConnection(Connection cx)
	{    
		if(cx==null) return;
		try
		{
			m_DBPoolMgr.releaseConnection(SystemContext.DBALIAS_SYSTEM, cx);
			m_iSystemConnReleaseCount++;		      
		}
		catch(Exception e)
		{
			pErr.doError("releaseSystemConnection() " + e.toString(), this);
		}
	}

	protected void resetConnectionChecker()
	{     		
		//gotta make a new one so it loses the clone reference.		     
		m_iSystemConnGetCount=0;
		m_iSystemConnReleaseCount=0;
	}

	public String getDBPoolStatus()
	{
		return m_DBPoolMgr.getStatus();
	}

}//end class pmaSystem




