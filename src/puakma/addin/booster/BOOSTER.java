/***************************************************************************
The contents of this file are subject to the Puakma Public License Version 1.0 
 (the "License"); you may not use this file except in compliance with the 
 License. A copy of the License is available at http://www.puakma.net/

The Original Code is BOOSTER. 
The Initial Developer of the Original Code is Brendon Upson. email: bupson@wnc.net.au 
Portions created by Brendon Upson are Copyright (C)2002. All Rights Reserved.

webWise Network Consultants Pty Ltd, Australia, http://www.wnc.net.au

Contributor(s) and Changelog:
-
-
 ***************************************************************************/

package puakma.addin.booster;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Properties;
import java.util.StringTokenizer;
import java.util.Vector;

import puakma.addin.AddInStatistic;
import puakma.addin.pmaAddIn;
import puakma.addin.pmaAddInStatusLine;
import puakma.addin.http.log.HTTPLogEntry;
import puakma.addin.http.log.HTTPLogger;
import puakma.license.LicenseManager;
import puakma.pooler.Cache;
import puakma.system.SystemContext;

/**
 * @author Brendon Upson
 * @date 9 November 2003
 * HTTPServer is Puakma's web server.
 * It is loaded as an addin
 */
public class BOOSTER extends pmaAddIn
{
	public static final String STATISTIC_KEY_HITSPERMINUTE = "booster.hitsperminute";
	public static final String STATISTIC_KEY_HITSPERHOUR = "booster.hitsperhour";
	
	public static final String STATISTIC_KEY_SERVERBYTESPERHOUR = "booster.serverbytesperhour";
	public static final String STATISTIC_KEY_SERVERTOTALBYTES = "booster.serverbytestotal";
	
	public static final String STATISTIC_KEY_CLIENTBYTESPERHOUR = "booster.clientbytesperhour";
	public static final String STATISTIC_KEY_CLIENTTOTALBYTES = "booster.clientbytestotal";
	
	public static final String STATISTIC_KEY_TOTALHITS = "booster.hitstotal";
	
	public static final String STATISTIC_KEY_CACHEHITSPERHOUR = "booster.cachehitsperhour";
	public static final String STATISTIC_KEY_CACHEMISSESPERHOUR = "booster.cachemissesperhour";
	
	
	//TODO compression stats
	//available servers per hour
	

	
	public static final String SESSIONID_LABEL="_booster_sid";
	private pmaAddInStatusLine m_pStatus;
	private Vector m_Listeners = new Vector();    
	private boolean m_bUseRealHostName = false;
	private boolean m_bDebug = false;  
	private boolean m_bDebugHeaders = false;
	private BOOSTERAvailabilityThread m_rpAT=null;
	private int m_iPollInterval=30; //seconds
	private int m_iMaxURI=2048; //includes GET and HTTP/1.1
	private boolean m_bRunning=true;    
	private String m_sReplaceHosts[]=null; //for servers that generate their own references, eg Domino! Grrr!
	private String[] m_sCustomHeaderProcessors=null;    
	private boolean m_bAllowGZIP=false;    
	private final String BOOSTER_VERSION = "3.1.2";
	private File m_fUnavailable=null;
	private boolean m_bAllowReverseDNSLookups = true;

	private Hashtable m_htHostConfig = new Hashtable();
	private ArrayList m_arrNoCompress = new ArrayList(); //mimetypes that do not get compressed
	private ArrayList m_arrCompress = new ArrayList(); //mimetypes that get compressed
	private ArrayList m_arrStartWithCompress = new ArrayList(); //mimetypes that get compressed starting with xxxxx*
	private ArrayList m_arrEndWithCompress = new ArrayList(); //mimetypes that get compressed ending with xxxxx*
	private ArrayList m_arrWildCompress = new ArrayList(); //mimetypes that get compressed that contain this term
	//URIs that do not get compressed
	private ArrayList m_arrExactNoCompressURI = new ArrayList();
	private ArrayList m_arrStartWithNoCompressURI = new ArrayList();
	private ArrayList m_arrEndWithNoCompressURI = new ArrayList();
	private ArrayList m_arrWildNoCompressURI = new ArrayList();

	private ArrayList m_arrNoCache = new ArrayList();
	private ArrayList m_arrCache = new ArrayList();
	private ArrayList m_arrStartWithCache = new ArrayList(); //mimetypes that get cached starting with xxxxx*
	private ArrayList m_arrEndWithCache = new ArrayList(); //mimetypes that get cached ending with xxxxx*
	private ArrayList m_arrWildCache = new ArrayList(); //mimetypes that get cached that contain this term

	private ArrayList m_arrAlwaysCacheURIs = new ArrayList();
	private ArrayList m_arrNoCacheURIs = new ArrayList();

	private FileOutputStream m_fout;
	private Calendar m_calOutFile=Calendar.getInstance();
	private SimpleDateFormat m_simpledf = new SimpleDateFormat("yyyyMMdd");
	private SimpleDateFormat m_logdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
	private FileOutputStream m_fCompressionOut;
	private Calendar m_calCompressionFile=Calendar.getInstance();
	private NumberFormat m_nfDecimal = NumberFormat.getInstance();
	private boolean m_bNoCompressionLog=true;
	private ArrayList m_alMimeExcludes = new ArrayList();
	private boolean m_bHTTPLog=false;
	private Cache m_cache = null;
	private long m_lCacheSizeMB=0;
	private int m_iMaxgzipBytes=512000; //half meg default
	private int m_iMaxCacheableObjectBytes=512000; //half meg default
	private int m_iMaxCacheMinutes=24*60; //one day = 1440
	private int m_iMinCacheSeconds=-1; //no minumum
	private boolean m_bRemoveCacheInfo = false;
	private boolean m_bSharedCache = false;
	private boolean m_bFixContentTypes=true;
	private int m_iDefaultSocketTimeout = 30000; //30 seconds
	private String m_sPublicDir=null;
	//private String m_sMimeTypeFile=null;
	private Properties m_propMime= new Properties();
	private HTTPLogger m_httpLog = new HTTPLogger(null);
	private boolean m_bLicensed=false;
	private long m_lMaxUploadBytes = -1;
	private boolean m_bStickySessions = false;


	/**
	 * This method is called by the pmaServer object
	 */
	public void pmaAddInMain()
	{
		m_nfDecimal.setMaximumFractionDigits(2);
		m_nfDecimal.setMaximumFractionDigits(2);
		setAddInName("BOOSTER");
		m_pStatus = createStatusLine();
		m_pStatus.setStatus("Starting...");
		m_pSystem.doInformation("BOOSTER.Startup", new String[]{BOOSTER_VERSION+""}, this);
		
		createStatistic(STATISTIC_KEY_HITSPERMINUTE, AddInStatistic.STAT_CAPTURE_PER_MINUTE, -1, true);
		createStatistic(STATISTIC_KEY_HITSPERHOUR, AddInStatistic.STAT_CAPTURE_PER_HOUR, -1, true);		
		
		createStatistic(STATISTIC_KEY_SERVERBYTESPERHOUR, AddInStatistic.STAT_CAPTURE_PER_HOUR, -1, true);		
		createStatistic(STATISTIC_KEY_SERVERTOTALBYTES, AddInStatistic.STAT_CAPTURE_ONCE, -1, true);
		createStatistic(STATISTIC_KEY_CLIENTBYTESPERHOUR, AddInStatistic.STAT_CAPTURE_PER_HOUR, -1, true);		
		createStatistic(STATISTIC_KEY_CLIENTTOTALBYTES, AddInStatistic.STAT_CAPTURE_ONCE, -1, true);
		
		createStatistic(STATISTIC_KEY_TOTALHITS, AddInStatistic.STAT_CAPTURE_ONCE, -1, true);
		
		createStatistic(STATISTIC_KEY_CACHEHITSPERHOUR, AddInStatistic.STAT_CAPTURE_PER_HOUR, -1, true);
		createStatistic(STATISTIC_KEY_CACHEMISSESPERHOUR, AddInStatistic.STAT_CAPTURE_PER_HOUR, -1, true);

		LicenseManager lic = LicenseManager.getInstance(m_pSystem);
		m_bLicensed = lic.isLicensed(null);
		loadMimeExcludes();
		loadHostConfig();

		String sTemp = m_pSystem.getSystemProperty("BOOSTERReplaceHosts");
		ArrayList arr = puakma.util.Util.splitString(sTemp, ',');
		if(arr!=null && arr.size()>0) m_sReplaceHosts = puakma.util.Util.objectArrayToStringArray(arr.toArray());

		sTemp = m_pSystem.getSystemProperty("BOOSTERPollInterval");
		try{m_iPollInterval = Integer.parseInt(sTemp);}catch(Exception t){}

		sTemp = m_pSystem.getSystemProperty("BOOSTERPortTimeout");
		try{m_iDefaultSocketTimeout = Integer.parseInt(sTemp);}catch(Exception t){}


		sTemp = m_pSystem.getSystemProperty("BOOSTERMaxURI");
		try{m_iMaxURI = Integer.parseInt(sTemp);}catch(Exception t){}

		sTemp = m_pSystem.getSystemProperty("BOOSTERgzip");
		if(sTemp!=null && sTemp.equals("1")) m_bAllowGZIP=true; 
		if(m_bAllowGZIP) m_pSystem.doInformation("GZIP is enabled", this);

		sTemp = m_pSystem.getSystemProperty("BOOSTERNoReverseDNS");
		if(sTemp!=null && sTemp.equals("1")) m_bAllowReverseDNSLookups=false; 
		if(!m_bAllowReverseDNSLookups) m_pSystem.doInformation("Reverse DNS is disabled", this);

		//sTemp = m_pSystem.getSystemProperty("BOOSTERPollMethod");
		//if(sTemp!=null) m_sPollMethod = sTemp;

		sTemp = m_pSystem.getSystemProperty("BOOSTERLogNameDateFormat");
		if(sTemp!=null && sTemp.length()>0) m_simpledf = new SimpleDateFormat(sTemp);

		sTemp = m_pSystem.getSystemProperty("BOOSTERLogFormat");
		if(sTemp!=null && sTemp.length()>0) m_httpLog = new HTTPLogger(sTemp);

		String sMimeTypeFile = m_pSystem.getSystemProperty("HTTPMimeFile");
		if(sMimeTypeFile!=null)
		{        
			try
			{
				m_propMime.load(new FileInputStream(sMimeTypeFile));
			}
			catch(Exception e)
			{
				m_pSystem.doInformation("HTTPServer.NoMimeFile", new String[]{sMimeTypeFile}, this);
			}
		}

		sTemp = m_pSystem.getSystemProperty("BOOSTERUnavailableFile");
		if(sTemp!=null)
		{
			m_fUnavailable = new File(sTemp);
			if(!m_fUnavailable.isFile()) m_fUnavailable = null;
			/*else
        {            
            m_sUnavailableFileMimeType = determineMimeType(m_fUnavailable.getAbsolutePath());
        }*/
		}

		sTemp =m_pSystem.getSystemProperty("BOOSTERHeaderProcessors");
		if(sTemp!=null)
		{
			arr = puakma.util.Util.splitString(sTemp, ',');
			m_sCustomHeaderProcessors = puakma.util.Util.objectArrayToStringArray(arr.toArray());
		}

		sTemp = m_pSystem.getSystemProperty("BOOSTERCompressionLog");
		if(sTemp!=null && sTemp.trim().length()>0) m_bNoCompressionLog=false;

		sTemp = m_pSystem.getSystemProperty("BOOSTERTextLog");
		if(sTemp!=null && sTemp.length()>0) m_bHTTPLog=true;

		m_sPublicDir = m_pSystem.getSystemProperty("BOOSTERPublicDir");

		setupCompressRules();
		setupCacheRules();

		loadListeners();

		sTemp = m_pSystem.getSystemProperty("BOOSTERServerCacheMB");      
		if(sTemp!=null && sTemp.length()>0) try{ m_lCacheSizeMB = Long.parseLong(sTemp); }catch(Exception y){}
		if(m_lCacheSizeMB>0) m_cache = new Cache(m_lCacheSizeMB*1024*1024);    

		sTemp = m_pSystem.getSystemProperty("BOOSTERUseSharedCache");
		if(sTemp!=null && sTemp.equals("1")) m_bSharedCache = true;


		String szTemp =m_pSystem.getSystemProperty("BOOSTERDebug");
		if(szTemp!=null && szTemp.equals("1")) m_bDebug = true;

		/*
		 //BJU 16/8/07 removed. We will not support regular proxy services. Booster will not be all things to all people ;-)
		 szTemp =m_pSystem.getSystemProperty("BOOSTERAllowRegularProxy");
		if(szTemp!=null && szTemp.equals("1")) m_bAllowRegularProxy = true;
		*/

		szTemp =m_pSystem.getSystemProperty("BOOSTERUseRealHostName");
		if(szTemp!=null && szTemp.equals("1")) m_bUseRealHostName = true;

		szTemp =m_pSystem.getSystemProperty("BOOSTERMaxgzipKB");
		if(szTemp!=null && szTemp.length()>0) 
		{
			m_iMaxgzipBytes = 512;//KB
			try{ m_iMaxgzipBytes = Integer.parseInt(szTemp); } catch(Exception y){}
			//-1 means no limit
			if(m_iMaxgzipBytes>0) m_iMaxgzipBytes = m_iMaxgzipBytes * 1024;//convert to bytes
		}

		szTemp =m_pSystem.getSystemProperty("BOOSTERMaxCacheableObjectKB");
		if(szTemp!=null && szTemp.length()>0) 
		{
			m_iMaxCacheableObjectBytes = 512;//KB
			try{ m_iMaxCacheableObjectBytes = Integer.parseInt(szTemp); } catch(Exception y){}
			//-1 means no limit
			if(m_iMaxCacheableObjectBytes>0) m_iMaxCacheableObjectBytes = m_iMaxCacheableObjectBytes * 1024;//convert to bytes
		}

		szTemp =m_pSystem.getSystemProperty("BOOSTERMaxCacheMinutes");
		if(szTemp!=null && szTemp.length()>0) 
		{
			m_iMaxCacheMinutes = 1440;//KB
			try{ m_iMaxCacheMinutes = Integer.parseInt(szTemp); } catch(Exception y){}
			//there must be a limit!
			if(m_iMaxCacheMinutes<0) m_iMaxCacheMinutes=0;
		}

		szTemp =m_pSystem.getSystemProperty("BOOSTERMinCacheSeconds");
		if(szTemp!=null && szTemp.length()>0) 
		{          
			try{ m_iMinCacheSeconds = Integer.parseInt(szTemp); } catch(Exception y){}
		}

		szTemp =m_pSystem.getSystemProperty("BOOSTERRemoveCacheInfo");
		if(szTemp!=null && szTemp.equals("1")) m_bRemoveCacheInfo = true;

		szTemp =m_pSystem.getSystemProperty("BOOSTERFixContentTypes");
		if(szTemp!=null && szTemp.equals("1")) m_bFixContentTypes = true;
		
		szTemp =m_pSystem.getSystemProperty("BOOSTERMaxUploadBytes");
		if(szTemp!=null && szTemp.length()>0)
		{
			try{ m_lMaxUploadBytes = Long.parseLong(szTemp.trim()); }catch(Exception e){}
		}
		

		szTemp =m_pSystem.getSystemProperty("BOOSTERStickySessions");
		if(szTemp!=null && szTemp.equals("1")) m_bStickySessions = true;
		if(m_bStickySessions) m_pSystem.doInformation("Sticky sessions are enabled", this);


		m_rpAT = new BOOSTERAvailabilityThread(m_htHostConfig, m_iPollInterval, this);
		m_rpAT.start();
		// main loop
		long lCacheCounter=0;
		while (!this.addInShouldQuit() && m_Listeners.size()>0)
		{
			lCacheCounter++;
			try{Thread.sleep(2000);}catch(Exception e){}
			if(lCacheCounter%20==0)
			{
				cleanCache();
				lCacheCounter=0;
			}
			checkRunners();
			m_pStatus.setStatus("Running.");
		}//end while      
			m_bRunning = false;
			m_pStatus.setStatus("Shutting down");
			m_rpAT.interrupt();
			requestQuit();
			waitForRunners();      
			m_pSystem.doInformation("BOOSTER.Shutdown", this);
			removeStatusLine(m_pStatus);
	}

	public String getVersionNumber()
	{
		return BOOSTER_VERSION;
	}

	/**
	 * Determine if this software has the appropriate keys
	 */
	public boolean isLicensed()
	{
		return m_bLicensed;
	}

	/**
	 * get the size of the largest object we should try to gzip. Returns -1 for no size
	 * limit
	 */
	public int getMaxgzipBytes()
	{
		return m_iMaxgzipBytes;
	}

	/**
	 * is the cache shared by all virtual hosts on this instance
	 */
	public boolean isSharedCache()
	{
		return m_bSharedCache;
	}

	/**
	 * Get the maximum amount of time (in minutes) an object can live in the cache
	 */
	public int getMaxCacheMinutes()
	{
		return m_iMaxCacheMinutes;
	}

	public int getMinCacheSeconds()
	{
		return m_iMinCacheSeconds;
	}

	/**
	 * Strip cache info from http reply
	 */
	public boolean shouldRemoveCacheInfo()
	{
		return m_bRemoveCacheInfo;
	}

	/**
	 * get the size of the largest object we should try to add to the cache. Returns -1 for no size
	 * limit
	 */
	public int getMaxCacheableObjectBytes()
	{
		return this.m_iMaxCacheableObjectBytes;
	}

	/**
	 *
	 */
	private void loadHostConfig()
	{
		String sConfigFile = m_pSystem.getSystemProperty("BOOSTERHostConfig");
		if(sConfigFile==null) return;

		try
		{
			Properties prop = new Properties();
			prop.load(new FileInputStream(sConfigFile));
			Enumeration en = prop.keys();            
			while(en.hasMoreElements())
			{
				String s = (String)en.nextElement();
				if(s.toUpperCase().startsWith("HOSTS~"))
				{
					ArrayList arrHosts = puakma.util.Util.splitString((String)prop.get(s), ',');
					BOOSTERHostConfig hc = new BOOSTERHostConfig();
					String sKey = s;
					sKey = sKey.substring(6, sKey.length());
					hc.m_sPollPath = (String)prop.get("POLLPATH~"+sKey);
					if(hc.m_sPollPath==null || hc.m_sPollPath.length()==0) hc.m_sPollPath = (String)prop.get("POLLPATH~*");
					if(hc.m_sPollPath==null || hc.m_sPollPath.length()==0) hc.m_sPollPath = "/";

					hc.m_sPollMethod = (String)prop.get("POLLMETHOD~"+sKey);
					if(hc.m_sPollMethod==null || hc.m_sPollMethod.length()==0) hc.m_sPollMethod = (String)prop.get("POLLMETHOD~*");
					if(hc.m_sPollMethod==null || hc.m_sPollMethod.length()==0) hc.m_sPollMethod = "HEAD";

					String sVal = (String)prop.get("FORCESSL~"+sKey);
					if(sVal==null || sVal.length()==0) sVal = (String)prop.get("FORCESSL~*");
					if(sVal!=null && sVal.length()>0 && sVal.equals("1")) hc.m_bForceSSL = true;

					sVal = (String)prop.get("FORCECLIENTSSL~"+sKey);
					if(sVal==null || sVal.length()==0) sVal = (String)prop.get("FORCECLIENTSSL~*");
					if(sVal!=null && sVal.length()>0 && sVal.equals("1")) hc.m_bForceClientToSSL = true;

					sVal = (String)prop.get("BACKUP~"+sKey);
					if(sVal==null || sVal.length()==0) sVal = (String)prop.get("BACKUP~*");
					if(sVal!=null && sVal.length()>0) hc.m_sBackupDomain = sVal;

					hc.m_sDomain = sKey;
					hc.m_sServers = puakma.util.Util.objectArrayToStringArray(arrHosts.toArray());
					hc.m_vAvailableServers = new Vector();
					this.m_htHostConfig.put(sKey, hc);
					//System.out.println(s + "=>>" +  sKey);
				}

			}
		}
		catch(Exception r)
		{
			m_pSystem.doError(r.toString(), this);
		}

	}

	/**
	 * Return the number of available hosts
	 */
	public int getAvailableHostCount(String sDomain)
	{
		//System.out.println("getAvailableHostCount("+sDomain+");");
		String sKey = null;
		if(sDomain!=null) sKey = sDomain.toLowerCase();
		BOOSTERHostConfig hc = (BOOSTERHostConfig)m_htHostConfig.get(sKey);
		if(hc==null) hc = (BOOSTERHostConfig)m_htHostConfig.get("*");
		if(hc==null) return 0;

		int iStandardCount = hc.m_vAvailableServers.size();
		int iBackupCount = 0;
		if(hc.m_sBackupDomain!=null) 
		{
			hc = (BOOSTERHostConfig)m_htHostConfig.get(hc.m_sBackupDomain.toLowerCase());
			iBackupCount = hc.m_vAvailableServers.size();
		}
		return iStandardCount + iBackupCount;
	}

	/**
	 *
	 */
	public String getPollPath(String sDomain)
	{
		BOOSTERHostConfig hc = (BOOSTERHostConfig)m_htHostConfig.get(sDomain.toLowerCase());
		if(hc==null) hc = (BOOSTERHostConfig)m_htHostConfig.get("*");
		if(hc==null) return "/";
		return hc.m_sPollPath;
	}

	/**
	 *
	 */
	public boolean getForceSSL(String sDomain)
	{
		BOOSTERHostConfig hc = (BOOSTERHostConfig)m_htHostConfig.get(sDomain.toLowerCase());
		if(hc==null) hc = (BOOSTERHostConfig)m_htHostConfig.get("*");
		if(hc==null) return false;
		return hc.m_bForceSSL;
	}

	public boolean shouldForceClientSSL(String sRequestedHost)
	{
		if(sRequestedHost==null) return false;
		BOOSTERHostConfig hc = (BOOSTERHostConfig)m_htHostConfig.get(sRequestedHost.toLowerCase());
		if(hc==null) hc = (BOOSTERHostConfig)m_htHostConfig.get("*");
		if(hc==null) return false;
		return hc.m_bForceClientToSSL;
	}

	/**
	 * Should we use the real name for the host (node) or the one the user used in the request?
	 */
	public boolean shouldUseRealHostName()
	{
		return m_bUseRealHostName;
	}

	/**
	 *
	 */
	public String getPollMethod(String sDomain)
	{
		BOOSTERHostConfig hc = (BOOSTERHostConfig)m_htHostConfig.get(sDomain.toLowerCase());
		if(hc==null) hc = (BOOSTERHostConfig)m_htHostConfig.get("*");
		if(hc==null) return "HEAD";
		return hc.m_sPollMethod;
	}

	public String doConsoleNodeStatus()
	{
		StringBuilder sb = new StringBuilder(128);
		Enumeration en = m_htHostConfig.keys();
		while(en.hasMoreElements())
		{
			String sKey = (String)en.nextElement();
			BOOSTERHostConfig hc = (BOOSTERHostConfig)m_htHostConfig.get(sKey);
			if(hc!=null) sb.append(hc.showStatus());
		}
		return sb.toString();
	}


	/**
	 * Check that each listener is running. If the listener is not running, then remove it
	 */
	private void checkRunners()
	{

		for(int i=0; i<m_Listeners.size(); i++)
		{
			BOOSTERServer s = (BOOSTERServer)m_Listeners.elementAt(i);
			if(!s.isRunning()) m_Listeners.removeElementAt(i);
		}   
	}

	/**
	 * Determine if the task is running
	 */
	public boolean isRunning()
	{
		return m_bRunning;
	}

	public boolean shouldGZipOutput()
	{
		return m_bAllowGZIP;
	}    


	public void setAvailableHostVector(String sDomain, Vector vNewAvailableHosts)
	{
		BOOSTERHostConfig hc = (BOOSTERHostConfig)m_htHostConfig.get(sDomain.toLowerCase());
		if(hc==null) hc = (BOOSTERHostConfig)m_htHostConfig.get("*");
		if(hc!=null && vNewAvailableHosts!=null) 
		{
			if(hc.m_vAvailableServers!=null) hc.m_vAvailableServers.removeAllElements();
			hc.m_vAvailableServers = vNewAvailableHosts;
		}
	}

	/**
	 *
	 */
	public File getUnavailableFile()
	{
		return m_fUnavailable;
	}


	/**
	 *
	 */
	public String getFileMimeType(String sRequestPath)
	{
		//return m_sUnavailableFileMimeType;
		if(sRequestPath==null) sRequestPath="";
		int iPos = sRequestPath.indexOf('?');
		if(iPos>=0) sRequestPath = sRequestPath.substring(0, iPos);
		iPos = sRequestPath.indexOf('&');
		if(iPos>=0) sRequestPath = sRequestPath.substring(0, iPos);
		return determineMimeType(sRequestPath);
	}  

	/**
	 * Returns a list of all the configured cluster members
	 */
	public String[] getAllServerNodes(String sDomain)
	{
		BOOSTERHostConfig hc = (BOOSTERHostConfig)m_htHostConfig.get(sDomain.toLowerCase());
		if(hc==null) hc = (BOOSTERHostConfig)m_htHostConfig.get("*");
		if(hc==null) return null;
		return hc.m_sServers;
	}

	/**
	 *
	 */
	public SystemContext getSystemContext()
	{
		return m_pSystem;
	}

	/**
	 * Gets the next host form the available list
	 */
	public synchronized String getNextAvailableHost(String sDomain)
	{
		String sKey = null;
		if(sDomain!=null) 
			sKey = sDomain.toLowerCase();
		else
			return null;
		BOOSTERHostConfig hc = (BOOSTERHostConfig)m_htHostConfig.get(sKey);
		if(hc==null && sDomain!=null) hc = (BOOSTERHostConfig)m_htHostConfig.get("*");
		if(hc==null) return null;
		String sNextHost = hc.getNextAvailableHost();
		if(sNextHost==null) //try the backup cluster
		{
			return getNextAvailableHost(hc.getBackupDomain()); //recurse
		}
		return sNextHost;
	}


	/**
	 * Gets the host availability history
	 */
	public boolean getServiceAvailability(String sDomain)
	{
		BOOSTERHostConfig hc = (BOOSTERHostConfig)m_htHostConfig.get(sDomain.toLowerCase());
		if(hc==null) hc = (BOOSTERHostConfig)m_htHostConfig.get("*");
		if(hc==null) return false;
		return hc.m_bPartialAvailability;
	}

	/**
	 * Gets the host availability history
	 */
	public synchronized void setServiceAvailability(String sDomain, boolean bNewVal)
	{
		BOOSTERHostConfig hc = (BOOSTERHostConfig)m_htHostConfig.get(sDomain.toLowerCase());
		if(hc!=null) hc.m_bPartialAvailability = bNewVal;

	}

	/**
	 * Check that each listener is running. If the listener is not running, then remove it
	 */
	private void waitForRunners()
	{
		int iActiveCount = 1;
		while(iActiveCount>0)
		{
			iActiveCount=0;
			for(int i=0; i<m_Listeners.size(); i++)
			{
				BOOSTERServer s = (BOOSTERServer)m_Listeners.elementAt(i);
				if(s.isAlive()) iActiveCount++;
			}
			m_pStatus.setStatus("Shutting down. Waiting for " + iActiveCount + " tasks to complete");
		}//while
	}

	/**
	 * Request this task to stop
	 */
	public void requestQuit()
	{
		this.interrupt();
		super.requestQuit();
		for(int i=0; i<m_Listeners.size(); i++)
		{
			BOOSTERServer s = (BOOSTERServer)m_Listeners.elementAt(i);
			s.requestQuit();
		}
		//System.out.println("requestQuit() "+ this.getErrorSource());
	}


	/**
	 * Loads each of the http listener objects
	 */
	private void loadListeners()
	{
		SystemContext newCtx;
		BOOSTERServer p;
		String szID;
		int iPort;
		String szPorts=m_pSystem.getSystemProperty("BOOSTERPorts");

		if(szPorts != null && szPorts.length()!=0)
		{
			StringTokenizer stk= new StringTokenizer(szPorts, ",", false);
			while (stk.hasMoreTokens())
			{
				szID = stk.nextToken();
				newCtx = (SystemContext)m_pSystem.clone();
				if(szID.toLowerCase().endsWith("ssl"))
				{
					iPort = Integer.parseInt(szID.substring(0, szID.length()-3));
					p = new BOOSTERServer(newCtx, iPort, this, true);
				}
				else
				{
					iPort = Integer.parseInt(szID);
					p = new BOOSTERServer(newCtx, iPort, this, false);
				}
				if(p!=null)
				{
					p.start();
					m_Listeners.add(p);
					//m_iListenerCount++;
				}
			}//end while
		}//end if
	}

	/**
	 *
	 *
	 */
	public String tell(String sCommand)
	{
		String sReturn = super.tell(sCommand);	
		if(sReturn!=null && sReturn.length()>0) return sReturn;

		if(sCommand.equalsIgnoreCase("?") || sCommand.equalsIgnoreCase("help"))
		{
			return "->cache flush\r\n"+
			"->cache status\r\n"+
			"->debugheaders on|off|status\r\n"+
			"->debug on|off|status\r\n"+
			"->poll\r\n"+
			"->stats [statistickey]\r\n" +
			"->status\r\n";             
		}

		if(sCommand.toLowerCase().equals("poll"))
		{        
			m_rpAT.interrupt();
			return "-> Polling.";
		}

		if(sCommand.toLowerCase().equals("cache flush"))
		{        
			if(m_cache!=null)
			{
				m_cache.expireAll(0);
			}
			return "-> Cache flushed.";
		}

		if(sCommand.toLowerCase().equals("cache status"))
		{
			StringBuilder sb = new StringBuilder(256);
			if(m_cache!=null)
			{
				long lHits = (long)m_cache.getCacheHits();
				long lMisses = (long)m_cache.getCacheMisses();
				double dTot = (double)(lHits+lMisses);
				double dblHitPercent = 0;
				if(dTot>0) dblHitPercent = ((double)lHits/dTot)*100;
				NumberFormat nf = NumberFormat.getInstance();
				nf.setMaximumFractionDigits(2);
				nf.setMinimumFractionDigits(2);
				sb.append("-> Cache elements=" + m_cache.getItemCount());
				sb.append(" size="+nf.format(m_cache.getCacheSize()/1024) + "Kb/");
				sb.append(nf.format(m_cache.getCacheMaxSize()/1024));
				sb.append("Kb hits=" + lHits + " misses=" + lMisses);
				sb.append(" " + nf.format(dblHitPercent) +"%\r\n");
				sb.append("Shared cache: " + m_bSharedCache + "\r\n");
				sb.append(m_cache.toString());
			}
			else
				sb.append("-> Caching is OFF");

			return sb.toString();
		}

		if(sCommand.toLowerCase().equals("debug off"))
		{
			m_bDebug = false;
			return "-> Debug is now OFF";
		}


		if(sCommand.toLowerCase().equals("debug on"))
		{
			m_bDebug = true;
			return "-> Debug is now ON";
		}

		if(sCommand.toLowerCase().equals("debugheaders on"))
		{
			m_bDebugHeaders = true;
			return "-> DebugHeaders is now ON";
		}

		if(sCommand.toLowerCase().equals("debugheaders off"))
		{
			m_bDebugHeaders = false;
			return "-> DebugHeaders is now OFF";
		}

		if(sCommand.toLowerCase().equals("debug status"))
		{
			if(m_bDebug) return "-> Debug is ON";

			return "-> Debug is OFF";
		}

		if(sCommand.toLowerCase().equals("status"))
		{
			StringBuilder sb = new StringBuilder(256);
			sb.append("gzip enabled             : " + this.m_bAllowGZIP +"\r\n");
			sb.append("Larget object for gzip   : " + this.m_iMaxgzipBytes +" bytes\r\n");
			sb.append("Largest cacheable object : " + this.m_iMaxCacheableObjectBytes +" bytes\r\n");
			sb.append("Maximum cache time       : " + this.m_iMaxCacheMinutes +" minutes\r\n");
			sb.append("Sticky Sessions          : " + this.m_bStickySessions +"\r\n");
			//String sCacheType="Per Port, " + m_lCacheSizeMB + " MB";
			//if(m_cache!=null) sCacheType = "Shared, " + (m_lCacheSizeMB*this.m_Listeners.size()) + " MB";
			sb.append("Shared Cache             : " + m_bSharedCache +"\r\n");
			sb.append("\r\n");

			Enumeration en = m_Listeners.elements();
			while(en.hasMoreElements())
			{
				BOOSTERServer hs = (BOOSTERServer)en.nextElement();
				sb.append(hs.getStatus());
			}
			sb.append(doConsoleNodeStatus());
			return sb.toString();
		}

		return sReturn;
	}

	/**
	 *
	 */
	public void checkAvailability()
	{      
		m_rpAT.interrupt();
	}


	/**
	 * If we should be debugging the code
	 * @return
	 */
	public boolean isDebug()
	{
		return m_bDebug;
	}
	
	
	/**
	 * Determines if sessions should always use the same back end server
	 * @return
	 */
	public boolean shouldUseStickySessions()
	{
		return m_bStickySessions;
	}


	public boolean allowReverseDNS()
	{
		return m_bAllowReverseDNSLookups;
	}

	/**
	 * If we should be debugging the http headers
	 * @return
	 */
	public boolean isDebugHeaders()
	{
		return m_bDebugHeaders;
	}


	/**
	 * Attempts to load the mime type for the given file
	 */
	private String determineMimeType(String szRequestPath)
	{
		String DEFAULT_TYPE = "www/unknown";

		if(m_propMime.size()==0) return DEFAULT_TYPE;

		String szMimeType="";
		int iPos;
		iPos = szRequestPath.lastIndexOf('.');
		if(iPos>=0)
		{
			String szExt = szRequestPath.substring(iPos+1, szRequestPath.length());
			szMimeType = m_propMime.getProperty(szExt);
			//if not found, try the default type
			if(szMimeType==null) szMimeType = m_propMime.getProperty("*");
			if(szMimeType!=null)
			{
				iPos = szMimeType.indexOf(' ');
				if(iPos>=0) szMimeType = szMimeType.substring(0, iPos);
				return szMimeType;
			}
		}
		return DEFAULT_TYPE;
	}

	/**
	 *
	 */
	public String[] getReplaceHosts()
	{
		return m_sReplaceHosts;
	}

	public String[] getCustomHeaderProcessors()
	{
		return m_sCustomHeaderProcessors;
	}

	public int getMaxURI()
	{
		return m_iMaxURI;
	}


	public boolean shouldGZipOutput(String sURI, String sContentType)
	{        
		if(!shouldGZipOutput() || sContentType==null || sContentType.length()==0) return false;
		
		// first up check the URI
		if(sURI!=null && sURI.length()>0)
		{
			String sLowURI = sURI.toLowerCase();
			for(int i=0; i<m_arrExactNoCompressURI.size(); i++)
			{
				String sCompareURI = (String)m_arrExactNoCompressURI.get(i);
				if(sLowURI.equals(sCompareURI)) return false;
			}        
			for(int i=0; i<m_arrWildNoCompressURI.size(); i++)
			{
				String sCompareURI = (String)m_arrWildNoCompressURI.get(i);
				if(sLowURI.indexOf(sCompareURI)>=0) return false;
			}
			for(int i=0; i<m_arrStartWithNoCompressURI.size(); i++)
			{
				String sCompareURI = (String)m_arrStartWithNoCompressURI.get(i);
				if(sLowURI.startsWith(sCompareURI)) return false;
			}
			for(int i=0; i<m_arrEndWithNoCompressURI.size(); i++)
			{
				String sCompareURI = (String)m_arrEndWithNoCompressURI.get(i);
				if(sLowURI.endsWith(sCompareURI)) return false;
			}        
		}

		//if the content-type is split over multiple lines or has crap appended
		//to the end, trim it off
		int iPos = sContentType.indexOf('\r');
		if(iPos>0) sContentType = sContentType.substring(0, iPos);
		
		iPos = sContentType.indexOf(';');
		if(iPos>0) sContentType = sContentType.substring(0, iPos);
		
		int i=0;
		//System.out.println("**** GZIP "+sContentType);
		for(i=0; i<m_arrNoCompress.size(); i++)
		{
			String sType = (String)m_arrNoCompress.get(i);
			//System.out.println("! "+sType);
			if(sType.equals(sContentType)) return false; //don't compress!
		}

		for(i=0; i<m_arrStartWithCompress.size(); i++)
		{
			String sType = (String)m_arrStartWithCompress.get(i);
			//System.out.println("s "+sType);
			if(sContentType.startsWith(sType)) return true; //compress!
		}

		for(i=0; i<m_arrWildCompress.size(); i++)
		{
			String sType = (String)m_arrWildCompress.get(i);
			//System.out.println("* "+sType);
			if(sContentType.indexOf(sType)>=0) return true; //compress! eg *javascript*
		}

		for(i=0; i<m_arrCompress.size(); i++)
		{
			String sType = (String)m_arrCompress.get(i);
			//System.out.println("= "+sType);
			if(sType.equals(sContentType)) return true; //compress!
		}                

		for(i=0; i<m_arrEndWithCompress.size(); i++)
		{
			String sType = (String)m_arrEndWithCompress.get(i);
			//System.out.println("e "+sType);
			if(sContentType.endsWith(sType)) return true; //compress!
		}

		return false;
	}

	/**
	 * Change .js and .css files to the correct mimetype for domino web servers
	 */
	public boolean shouldFixContentTypes()
	{
		return m_bFixContentTypes;
	}

	/**
	 *
	 */
	public boolean shouldCacheOutput(String sURI, String sContentType)
	{    
		int i=0;
		for(i=0; i<m_arrNoCacheURIs.size(); i++)
		{
			String sURILookup = (String)m_arrNoCacheURIs.get(i);
			if(sURILookup.endsWith("*"))
			{
				//match first
				sURILookup = sURILookup.substring(0, sURILookup.length()-1);
				if(sURI.length()>=sURILookup.length())
				{
					sURI = sURI.substring(0, sURILookup.length());                    
				}
			}
			if(sURILookup.startsWith("*"))
			{
				//match last
				sURILookup = sURILookup.substring(1);
				if(sURI.length()>=sURILookup.length())
				{
					sURI = sURI.substring(1);                    
				}
			}
			if(sURI.equalsIgnoreCase(sURILookup)) 
			{
				//System.out.println("NOCACHE:"+sURI);
				return false;
			}
		}

		for(i=0; i<m_arrAlwaysCacheURIs.size(); i++)
		{
			String sURILookup = (String)m_arrAlwaysCacheURIs.get(i);
			//System.out.println("s "+sType);
			if(sURI.equals(sURILookup)) return true; //Cache!
		}

		//if the content-type is split over multiple lines or has crap appended
		//to the end, trim it off
		int iPos = sContentType.indexOf('\r');
		if(iPos>0)
		{
			sContentType = sContentType.substring(0, iPos);
		}
		iPos = sContentType.indexOf(';');
		if(iPos>0)
		{
			sContentType = sContentType.substring(0, iPos);
		}


		for(i=0; i<m_arrNoCache.size(); i++)
		{
			String sType = (String)m_arrNoCache.get(i);
			//System.out.println("! "+sType);
			if(sType.equals(sContentType)) return false; //don't Cache!
		}

		for(i=0; i<m_arrStartWithCache.size(); i++)
		{
			String sType = (String)m_arrStartWithCache.get(i);
			//System.out.println("s "+sType);
			if(sContentType.startsWith(sType)) return true; //Cache!
		}

		for(i=0; i<m_arrWildCache.size(); i++)
		{
			String sType = (String)m_arrWildCache.get(i);
			//System.out.println("* "+sType);
			if(sContentType.indexOf(sType)>=0 || sType.equals("*")) return true; //Cache! eg *javascript*
		}

		for(i=0; i<m_arrCache.size(); i++)
		{
			String sType = (String)m_arrCache.get(i);
			//System.out.println("= "+sType);
			if(sType.equals(sContentType)) return true; //Cache!
		}                

		for(i=0; i<m_arrEndWithCache.size(); i++)
		{
			String sType = (String)m_arrEndWithCache.get(i);
			//System.out.println("e "+sType);
			if(sContentType.endsWith(sType)) return true; //Cache!
		}

		return false;
	}


	/**
	 *
	 *
	 */
	private void setupCacheRules()
	{        
		String sTemp = m_pSystem.getSystemProperty("BOOSTERCacheContentTypes");
		if(sTemp!=null && sTemp.length()>0)
		{
			ArrayList arr = puakma.util.Util.splitString(sTemp, ',');
			for(int i=0; i<arr.size(); i++)
			{
				String s = (String)arr.get(i);
				if(s.startsWith("*"))
				{
					if(s.endsWith("*"))
						if(s.length()==1)
							m_arrWildCache.add(s);
						else
							m_arrWildCache.add(s.substring(1, s.length()-1));                    
					else //exact match                    
						m_arrEndWithCache.add(s.substring(1, s.length()));                    
				}
				else
				{
					if(s.endsWith("*"))                    
						m_arrStartWithCache.add(s.substring(0, s.length()-1));                    
					else
						this.m_arrCache.add(s);
				}

			}
		}
		else //cache everything possible
			m_arrWildCache.add("*");

		//
		sTemp = m_pSystem.getSystemProperty("BOOSTERAlwaysCacheURI");
		if(sTemp!=null && sTemp.length()>0)
		{
			ArrayList arr = puakma.util.Util.splitString(sTemp, ',');
			for(int i=0; i<arr.size(); i++)
			{
				//exact matches only...
				String s = (String)arr.get(i);                
				m_arrAlwaysCacheURIs.add(s);                
			}
		}
		sTemp = m_pSystem.getSystemProperty("BOOSTERNoCacheURI");
		if(sTemp!=null && sTemp.length()>0)
		{
			ArrayList arr = puakma.util.Util.splitString(sTemp, ',');
			for(int i=0; i<arr.size(); i++)
			{
				//exact matches only...
				String s = (String)arr.get(i);                
				m_arrNoCacheURIs.add(s);                
			}
		}

		sTemp = m_pSystem.getSystemProperty("BOOSTERNoCacheContentTypes");
		if(sTemp!=null && sTemp.length()>0) m_arrNoCache = puakma.util.Util.splitString(sTemp, ',');

	}

	/**
	 *
	 */
	private void setupCompressRules()
	{
		String sTemp = m_pSystem.getSystemProperty("BOOSTERCompressTypes");
		if(sTemp!=null && sTemp.length()>0)
		{
			ArrayList arr = puakma.util.Util.splitString(sTemp, ',');
			for(int i=0; i<arr.size(); i++)
			{
				String s = (String)arr.get(i);
				if(s.startsWith("*"))
				{
					if(s.endsWith("*"))                    
						m_arrWildCompress.add(s.substring(1, s.length()-1));                    
					else //exact match                    
						m_arrEndWithCompress.add(s.substring(1, s.length()));                    
				}
				else
				{
					if(s.endsWith("*"))                    
						m_arrStartWithCompress.add(s.substring(0, s.length()-1));                    
					else
						this.m_arrCompress.add(s);
				}

			}
		}

		sTemp = m_pSystem.getSystemProperty("BOOSTERNoCompressTypes");
		if(sTemp!=null && sTemp.length()>0) m_arrNoCompress = puakma.util.Util.splitString(sTemp, ',');


		//Now do URI based checking
		sTemp = m_pSystem.getSystemProperty("BOOSTERNoCompressURI");
		if(sTemp!=null && sTemp.length()>0) 
		{
			ArrayList arr = puakma.util.Util.splitString(sTemp, ',');
			for(int i=0; i<arr.size(); i++)
			{
				String s = (String)arr.get(i);
				s = s.toLowerCase();
				if(s.startsWith("*"))
				{
					if(s.endsWith("*"))
						if(s.length()==1)
							m_arrWildNoCompressURI.add(s);
						else
							m_arrWildNoCompressURI.add(s.substring(1, s.length()-1));                    
					else //exact match                         
						m_arrEndWithNoCompressURI.add(s.substring(1, s.length()));                    
				}
				else
				{
					if(s.endsWith("*"))                    
						m_arrStartWithNoCompressURI.add(s.substring(0, s.length()-1));                    
					else
						m_arrExactNoCompressURI.add(s);
				}

			}//for
		}


	}

	/**
	 * only allow this to be loaded once
	 */
	public boolean canLoadMultiple()
	{
		return false;
	}


	/**
	 * Writes lines to log web requests
	 */
	public synchronized void writeTextStatLog(HTTPLogEntry stat)
	{
		if(stat==null || !m_bHTTPLog) return;

		Calendar calNow = Calendar.getInstance();

		try
		{
			//create a new log each day
			if(m_fout==null || calNow.get(Calendar.DAY_OF_MONTH)!=m_calOutFile.get(Calendar.DAY_OF_MONTH))
			{
				String szDate = m_simpledf.format(m_calOutFile.getTime());
				m_calOutFile = Calendar.getInstance();
				szDate = m_simpledf.format(m_calOutFile.getTime());
				String sBareLog = m_pSystem.getSystemProperty("BOOSTERTextLog");
				if(sBareLog==null || sBareLog.length()==0) sBareLog = "boosterweblog_*.log";
				String szLogFile = sBareLog.replaceAll("\\*", szDate);

				m_pSystem.doInformation("HTTP.NewWebLogFile", new String[]{szLogFile}, this);
				File fLog = new File(szLogFile);
				boolean bFileExists = fLog.exists();
				m_fout = new FileOutputStream(szLogFile, true);

				if(!bFileExists)
				{
					//Tag this file as an IIS 5.0 log file
					//this will enable log analysis tools to process it better....
					SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
					m_fout.write(("#LogGenerator: " + m_pSystem.getVersionString() + "\r\n").getBytes());            
					m_fout.write(("#Date: "+sdf.format(m_calOutFile.getTime()) + "\r\n").getBytes());
					m_fout.write(("#Fields: "+ m_httpLog.getLogFormat() + "\r\n").getBytes());
				}
			}
			m_httpLog.logRequest(m_fout, stat);
		}
		catch(Exception e)
		{
			m_pSystem.doError("HTTPServer.WriteStatLogError", new String[]{m_pSystem.getSystemProperty("BOOSTERTextLog"), e.getMessage()}, this);
		}
	}


	/**
	 * Writes lines to log web request comperssion stats
	 */
	public synchronized void writeCompressionStatLog(String sURI, int iOrigSize, int iNewSize, int iWebServerTime, int iTotalTime, String sContentType)
	{
		if(m_bNoCompressionLog || iOrigSize<=0 || iNewSize<=0) return;

		Calendar calNow = Calendar.getInstance();

		if(sContentType==null) sContentType="unknown";
		if(sURI==null) sURI="???";
		//String szLogFile = m_pSystem.getSystemProperty("BOOSTERCompressionLog") + szDate + ".log";

		try
		{
			//create a new log each day
			if(m_fCompressionOut==null || calNow.get(Calendar.DAY_OF_MONTH)!=m_calCompressionFile.get(Calendar.DAY_OF_MONTH))
			{          
				m_calCompressionFile = Calendar.getInstance();
				String szDate = m_simpledf.format(m_calCompressionFile.getTime());        
				String sBareLog = m_pSystem.getSystemProperty("BOOSTERCompressionLog");
				if(sBareLog==null || sBareLog.length()==0) sBareLog = "compression_*.log";
				String szLogFile = sBareLog.replaceFirst("\\*", szDate);

				m_pSystem.doInformation("BOOSTER.NewCompressionLogFile", new String[]{szLogFile}, this);
				File fLog = new File(szLogFile);
				boolean bFileExists = fLog.exists();
				m_fCompressionOut = new FileOutputStream(szLogFile, true);

				if(!bFileExists)
				{            
					m_fCompressionOut.write(("Date Time\tCompress%\tURI\tOrigSize\tNewSize\tWebServerTime (ms)\tTotalTime (ms)\tContentType\r\n").getBytes());
				}
			}      
			m_fCompressionOut.write(m_logdf.format(calNow.getTime()).getBytes());
			m_fCompressionOut.write('\t');
			double dblCompress = ((double)(iOrigSize-iNewSize)/(double)iOrigSize)*100;
			if(dblCompress>=100) dblCompress=0;
			m_fCompressionOut.write((m_nfDecimal.format(dblCompress)+"%").getBytes());
			m_fCompressionOut.write('\t');
			m_fCompressionOut.write(sURI.getBytes());

			m_fCompressionOut.write('\t');
			m_fCompressionOut.write(String.valueOf(iOrigSize).getBytes());
			m_fCompressionOut.write('\t');
			m_fCompressionOut.write(String.valueOf(iNewSize).getBytes());
			m_fCompressionOut.write('\t');
			m_fCompressionOut.write(String.valueOf(iWebServerTime).getBytes());
			m_fCompressionOut.write('\t');
			m_fCompressionOut.write(String.valueOf(iTotalTime).getBytes());
			m_fCompressionOut.write('\t');
			m_fCompressionOut.write(sContentType.getBytes());
			m_fCompressionOut.write('\r');
			m_fCompressionOut.write('\n');      
		}
		catch(Exception e)
		{
			m_pSystem.doError("BOOSTER.WriteCompressionLogError", new String[]{m_pSystem.getSystemProperty("BOOSTERCompressionLog"), e.getMessage()}, this);
		}
	}


	/**
	 * return the list of mimetypes to exclude form logging
	 */
	public ArrayList getMimeExcludes()
	{
		return m_alMimeExcludes;
	}

	/**
	 * Loads each of the http listener objects
	 */
	private void loadMimeExcludes()
	{
		String szMimes=m_pSystem.getSystemProperty("BOOSTERLogMimeExclude");
		String szMimeType="";

		if(szMimes != null && szMimes.length()>0)
		{
			StringTokenizer stk= new StringTokenizer(szMimes, ",", false);
			while (stk.hasMoreTokens())
			{
				szMimeType = stk.nextToken();
				if(szMimeType.length()>0) m_alMimeExcludes.add(szMimeType);
			}//end while
		}//end if
	}

	/**
	 * get an item from the shared cache.
	 */
	public BOOSTERCacheItem getCacheItem(String sKey)
	{
		if(m_cache==null) return null;

		BOOSTERCacheItem item = (BOOSTERCacheItem)m_cache.getItem(sKey);
		return item;
	}

	/**
	 * Add an item to the cache
	 */
	public boolean addToCache(BOOSTERCacheItem item)
	{
		if(m_cache==null) return false;
		return m_cache.addItem(item);
	}

	/**
	 * Called periodically to clean the cache of expired objects
	 */
	public void cleanCache()
	{
		if(m_cache!=null) 
		{
			m_cache.expireAll(System.currentTimeMillis());
		}


	}

	public int getDefaultSocketTimeout()
	{
		return m_iDefaultSocketTimeout;
	}

	public String getPublicDir()
	{
		return m_sPublicDir;
	}
	
	/**
	 * Return the largest content-length this server will accept. -1 is unlimited
	 * @return
	 */
	public long getMaxUploadBytes()
	{
		return m_lMaxUploadBytes;
	}

}//class
