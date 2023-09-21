/** ***************************************************************
HTTP.java
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
package puakma.addin.http;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.sql.Connection;
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
import puakma.server.AddInMessage;
import puakma.system.SystemContext;

/**
 * HTTPServer is Puakma's web server.
 * It is loaded as an addin
 */
public class HTTP extends pmaAddIn
{
	public static final String STATISTIC_KEY_HITSPERMINUTE = "http.hitsperminute";
	public static final String STATISTIC_KEY_HITSPERHOUR = "http.hitsperhour";
	public static final String STATISTIC_KEY_LOGINSPERHOUR = "http.loginsperhour";
	public static final String STATISTIC_KEY_BYTESINPERHOUR = "http.bytesinperhour";
	public static final String STATISTIC_KEY_BYTESOUTPERHOUR = "http.bytesoutperhour";

	public static final String STATISTIC_KEY_TOTALHITS = "http.hitstotal";
	public static final String STATISTIC_KEY_TOTALBYTESIN = "http.bytesintotal";
	public static final String STATISTIC_KEY_TOTALBYTESOUT = "http.bytesouttotal";

	public static String DEFAULT_CHAR_ENCODING = "UTF-8";//"ISO-8859-1";
	private pmaAddInStatusLine m_pStatus;
	private Vector m_Listeners = new Vector();
	//private int m_iListenerCount=0;
	//public Cache m_cacheDesign;
	//public Cache m_cacheObject;
	public Hashtable m_htAppAlwaysLoad=new Hashtable();
	private Properties m_propHostMap = new Properties();
	private boolean m_bDebug = false;
	private boolean m_bUseSingleDBPool=true;
	private String[] m_sCustomHeaderProcessors=null; 
	//public final static String PUAKMA_FILE_EXT=".pma";    
	private String m_sPuakmaFileExt = ".pma";
	private FileOutputStream m_fout;
	private Calendar m_calOutFile=Calendar.getInstance();
	private SimpleDateFormat m_simpledf = new SimpleDateFormat("yyyyMMdd");
	private ArrayList m_alMimeExcludes = new ArrayList();
	private boolean m_bAllowByteServing=false;
	private boolean m_bGenerateETags=false;  
	private HTTPLogger m_httpLog = new HTTPLogger(null);
	private boolean m_bGroupLevelCookies=false;
	private long m_lMaxUploadBytes = -1;
	private boolean m_bMinifyFileSystemJS=false;
	private boolean m_bNoReverseDNS = false;
	//private String[] m_sAllowMethods = new String[] {"PUT","DELETE"}; //defaults
	private String[] m_sAllowMethods = new String[] {"CONNECT", "DELETE", "GET", "HEAD", "OPTIONS", "POST", "PUT", "PATCH", "TRACE"}; //defaults


	/**
	 * This method is called by the pmaServer object
	 */
	public void pmaAddInMain()
	{
		setAddInName("HTTP");
		m_pStatus = createStatusLine();
		m_pStatus.setStatus("Starting...");
		m_pSystem.doInformation("HTTP.Startup", this);
		TornadoServer.getInstance(m_pSystem);
		createStatistic(STATISTIC_KEY_HITSPERMINUTE, AddInStatistic.STAT_CAPTURE_PER_MINUTE, -1, true);
		createStatistic(STATISTIC_KEY_HITSPERHOUR, AddInStatistic.STAT_CAPTURE_PER_HOUR, -1, true);
		createStatistic(STATISTIC_KEY_LOGINSPERHOUR, AddInStatistic.STAT_CAPTURE_PER_HOUR, -1, true);
		createStatistic(STATISTIC_KEY_BYTESINPERHOUR, AddInStatistic.STAT_CAPTURE_PER_HOUR, -1, true);
		createStatistic(STATISTIC_KEY_BYTESOUTPERHOUR, AddInStatistic.STAT_CAPTURE_PER_HOUR, -1, true);

		createStatistic(STATISTIC_KEY_TOTALBYTESIN, AddInStatistic.STAT_CAPTURE_ONCE, -1, true);
		createStatistic(STATISTIC_KEY_TOTALBYTESOUT, AddInStatistic.STAT_CAPTURE_ONCE, -1, true);
		createStatistic(STATISTIC_KEY_TOTALHITS, AddInStatistic.STAT_CAPTURE_ONCE, -1, true);

		loadMimeExcludes();
		loadListeners();

		/*double dCacheSize = 10485760; //10MB default
		String sTemp = m_pSystem.getSystemProperty("HTTPObjectCacheMB");
		if(sTemp!=null)
		{
			try
			{
				dCacheSize = Double.parseDouble(sTemp) *1024 *1024;
			}
			catch(Exception de){};
		}
		m_cacheObject = new Cache(dCacheSize);
		 */


		String sTemp =m_pSystem.getSystemProperty("HTTPMaxUploadBytes");
		if(sTemp!=null && sTemp.length()>0)
		{
			try{ m_lMaxUploadBytes = Long.parseLong(sTemp.trim()); }catch(Exception e){}
		}

		sTemp =m_pSystem.getSystemProperty("HTTPPuakmaFileExt");
		if(sTemp!=null && sTemp.length()>0 && sTemp.indexOf('.')==0)
		{
			m_sPuakmaFileExt = sTemp;          
		}

		sTemp = m_pSystem.getSystemProperty("HTTPDebug");
		if(sTemp!=null && sTemp.equals("1")) m_bDebug = true;

		sTemp = m_pSystem.getSystemProperty("HTTPMinifyJS");
		if(sTemp!=null && sTemp.equals("1")) m_bMinifyFileSystemJS = true;


		sTemp =m_pSystem.getSystemProperty("HTTPAllowByteServing");
		if(sTemp!=null && sTemp.equals("1")) m_bAllowByteServing = true;

		sTemp =m_pSystem.getSystemProperty("HTTPGenerateETags");
		if(sTemp!=null && sTemp.equals("1")) m_bGenerateETags = true;

		sTemp =m_pSystem.getSystemProperty("HTTPGroupLevelLogin");
		if(sTemp!=null && sTemp.equals("1")) m_bGroupLevelCookies = true;

		sTemp =m_pSystem.getSystemProperty("HTTPNoReverseDNS");
		if(sTemp!=null && sTemp.equals("1")) m_bNoReverseDNS = true;

		sTemp = m_pSystem.getSystemProperty("HTTPLogNameDateFormat");
		if(sTemp!=null && sTemp.length()>0) m_simpledf = new SimpleDateFormat(sTemp);

		sTemp = m_pSystem.getSystemProperty("HTTPLogFormat");
		if(sTemp!=null && sTemp.length()>0) m_httpLog = new HTTPLogger(sTemp);


		sTemp =m_pSystem.getSystemProperty("HTTPHeaderProcessors");
		if(sTemp!=null)
		{
			ArrayList arr = puakma.util.Util.splitString(sTemp, ',');
			m_sCustomHeaderProcessors = puakma.util.Util.objectArrayToStringArray(arr.toArray());
		}
		
		sTemp =m_pSystem.getSystemProperty("HTTPAllowMethods");
		if(sTemp!=null)
		{
			ArrayList arr = puakma.util.Util.splitString(sTemp, ',');
			m_sAllowMethods  = puakma.util.Util.objectArrayToStringArray(arr.toArray());
		}

		loadHostMap();

		// main loop
		while (!this.addInShouldQuit() && m_Listeners.size()>0)
		{
			try{Thread.sleep(2000);}catch(Exception e){}
			checkRunners();
			m_pStatus.setStatus("Running.");
		}//end while
		m_pStatus.setStatus("Shutting down");
		requestQuit();
		waitForRunners();
		m_pSystem.doInformation("HTTP.Shutdown", this);
		removeStatusLine(m_pStatus);
	}

	/**
	 * loads the hosts file into a properties object
	 */
	private void loadHostMap()
	{
		String szTemp = m_pSystem.getSystemProperty("HTTPHostMapFile");
		if(szTemp!=null && szTemp.length()>0)
		{
			m_propHostMap.clear();
			try
			{
				FileInputStream fs = new FileInputStream(szTemp);
				m_propHostMap.load(fs);
			}
			catch(Exception e)
			{
				m_pSystem.doError("HTTPServer.HostMap", new String[]{szTemp, e.toString()}, this);
			}
		}//if
	}

	/**
	 * Check that each listener is running. If the listener is not running, then remove it
	 */
	private void checkRunners()
	{

		for(int i=0; i<m_Listeners.size(); i++)
		{
			HTTPServer s = (HTTPServer)m_Listeners.elementAt(i);
			if(!s.isRunning()) m_Listeners.removeElementAt(i);
		}
		//m_iListenerCount = m_Listeners.size();
	}

	public boolean serverAllowsByteServing()
	{
		return m_bAllowByteServing;
	}

	public boolean shouldGenerateETags()
	{
		return m_bGenerateETags;
	}

	public boolean shouldMinifyFileSystemJS()
	{
		return m_bMinifyFileSystemJS;
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
				HTTPServer s = (HTTPServer)m_Listeners.elementAt(i);
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
			HTTPServer s = (HTTPServer)m_Listeners.elementAt(i);
			s.requestQuit();
		}
	}


	/**
	 * Loads each of the http listener objects
	 */
	private void loadListeners()
	{
		SystemContext newCtx;
		HTTPServer p;
		String szID;
		int iPort;
		String szPorts=m_pSystem.getSystemProperty("HTTPPorts");

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
					p = new HTTPServer(newCtx, iPort, this, true);
				}
				else
				{
					iPort = Integer.parseInt(szID);
					p = new HTTPServer(newCtx, iPort, this, false);
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
	 */
	public String tell(String sCommand)
	{
		String sReturn = super.tell(sCommand);	
		if(sReturn!=null && sReturn.length()>0) return sReturn;

		if(sCommand.equalsIgnoreCase("?") || sCommand.equalsIgnoreCase("help"))
		{
			return "->cache status\r\n" +        
			"->cache flush\r\n" +	
			"->debug on|off|status\r\n" +
			"->dbpool status\r\n" +
			"->dbpool restart\r\n" +
			"->hostmap reload\r\n" +
			"->kill [threadid]\r\n" +			
			"->stats [statistickey]\r\n" +
			"->thread status\r\n";
		}

		if(sCommand.toLowerCase().equals("cache status"))
		{
			TornadoServerInstance tsi = TornadoServer.getInstance();
			return "-> DesignCache: " + tsi.getDesignCacheStats() + "\r\n";			
		}

		if(sCommand.toLowerCase().equals("cache flush"))
		{
			TornadoServerInstance tsi = TornadoServer.getInstance();
			tsi.flushApplicationCache();
			tsi.clearClassLoader(null);

			return "-> DesignCache: flushed.\r\n";
		}



		if(sCommand.toLowerCase().equals("hostmap reload"))
		{
			loadHostMap();
			return "-> Hostmap: reloaded.\r\n";
		}

		if(sCommand.toLowerCase().equals("debug on"))
		{
			m_bDebug = true;
			return "-> Debug is now ON";
		}

		if(sCommand.toLowerCase().equals("debug off"))
		{
			m_bDebug = false;
			return "-> Debug is now OFF";
		}

		if(sCommand.toLowerCase().equals("debug status"))
		{
			if(m_bDebug) return "-> Debug is ON";

			return "-> Debug is OFF";
		}

		if(sCommand.toLowerCase().equals("dbpool status"))
		{
			return m_pSystem.getDBPoolStatus();
		}

		/*if(sCommand.toLowerCase().equals("dbpool restart"))
		{
			m_pSystem.restartDBPool();
			return "-> Pool restarted.";
		}*/

		if(sCommand.toLowerCase().equals("thread status"))
		{
			StringBuilder sbOut = new StringBuilder(256);
			sbOut.append("--------------- THREAD STATUS ---------------\r\n");
			for(int i=0; i<m_Listeners.size(); i++)
			{
				HTTPServer hs = (HTTPServer)m_Listeners.get(i);
				if(hs!=null)
				{
					sbOut.append(hs.getThreadDetail());
				}
			}
			sbOut.append("---------------------------------------------\r\n");
			return sbOut.toString();
		}



		String sKill = "kill";
		if(sCommand.toLowerCase().startsWith(sKill))
		{
			if(sCommand.length()<=sKill.length()+1) return "";
			String sThreadID = sCommand.substring(sKill.length()+1, sCommand.length());

			for(int i=0; i<m_Listeners.size(); i++)
			{
				HTTPServer hs = (HTTPServer)m_Listeners.get(i);
				if(hs!=null)
				{
					hs.killThread(sThreadID);
				}
			}

			return "->" + sCommand;
		}



		Enumeration en = m_Listeners.elements();
		while(en.hasMoreElements())
		{
			HTTPServer hs = (HTTPServer)en.nextElement();
			sReturn += hs.tell(sCommand);
		}

		return sReturn;
	}


	public boolean usesSingleDBPool()
	{
		return m_bUseSingleDBPool;
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
	 * returns the default url for a given host or URI from the properties file. Note
	 * if the url was http://server.com/booster we need to check for "/booster" and "/booster/"
	 * @param szRequestedHost
	 * @return
	 */
	public String getDefaultHostURL(String szRequestedHost)
	{
		if(szRequestedHost==null || szRequestedHost.length()==0) return "/";

		szRequestedHost = szRequestedHost.toLowerCase();
		String sTemp = m_propHostMap.getProperty(szRequestedHost);
		if(sTemp!=null) return sTemp;

		if(szRequestedHost.length()>0 && szRequestedHost.endsWith("/"))
		{
			sTemp = szRequestedHost.substring(0, szRequestedHost.length()-1);
			sTemp = m_propHostMap.getProperty(sTemp.toLowerCase());
			if(sTemp!=null) return sTemp;
		}
		if(szRequestedHost.length()>0 && !szRequestedHost.endsWith("/"))
		{
			sTemp = szRequestedHost+'/';
			sTemp = m_propHostMap.getProperty(sTemp.toLowerCase());
			if(sTemp!=null) return sTemp;
		}   

		return null;
	}

	public String[] getCustomHeaderProcessors()
	{
		return m_sCustomHeaderProcessors;
	}
	
	/**
	 * GET, POST, HEAD are always allowed
	 * This list specifies additional methods eg SEARCH, PUT, PROPFIND, OPTIONS, ... 
	 * @return
	 */
	public String[] getAllowedHTTPMethods()
	{
		return m_sAllowMethods;
	}
	
	

	/**
	 *
	 */
	public String getPuakmaFileExt()
	{
		return m_sPuakmaFileExt;
	}

	/**
	 * Writes lines to log web requests
	 */
	public synchronized void writeTextStatLog(HTTPLogEntry stat)
	{
		if(stat==null) return;

		Calendar calNow = Calendar.getInstance();

		try
		{
			//create a new log each day
			if(m_fout==null || calNow.get(Calendar.DAY_OF_MONTH)!=m_calOutFile.get(Calendar.DAY_OF_MONTH))
			{
				String szDate = m_simpledf.format(m_calOutFile.getTime());
				m_calOutFile = Calendar.getInstance();
				szDate = m_simpledf.format(m_calOutFile.getTime());
				String sBareLog = m_pSystem.getSystemProperty("HTTPTextLog");
				if(sBareLog==null || sBareLog.length()==0) sBareLog = "weblog_*.log";
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
			m_pSystem.doError("HTTPServer.WriteStatLogError", new String[]{m_pSystem.getSystemProperty("HTTPTextLog"), e.getMessage()}, this);
		}
	}

	/**
	 *
	 */
	public void writeRDBStatLog(HTTPLogEntry stat)
	{

		Connection cx = null;
		if(stat==null) return;

		try
		{
			cx = m_pSystem.getSystemConnection();
			m_httpLog.logRequestToRDB(cx, stat);
		}
		catch(Exception e)
		{
			m_pSystem.doError("HTTPServer.WriteStatLogError", new String[]{"RDB_TABLE=HTTPSTAT", e.getMessage()}, this);
		}
		finally
		{
			m_pSystem.releaseSystemConnection(cx);
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
		String szMimes=m_pSystem.getSystemProperty("HTTPLogMimeExclude");
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
	 * Receives messages from other addins
	 */
	public AddInMessage sendMessage(AddInMessage oMessage)
	{
		AddInMessage am = super.sendMessage(oMessage);
		if(am!=null) return am;

		if(oMessage==null) return null;
		//SessionContext sessCtx = getMessageSender(oMessage);
		//if(sessCtx==null) return null;
		String sAction = oMessage.getParameter("Action");
		if(sAction!=null && sAction.equalsIgnoreCase("FlushDesignElement"))
		{
			String sKey = oMessage.getParameter("DesignElementKey");
			if(sKey!=null) sKey = sKey.toLowerCase();
			removeDesignCacheItem(sKey);
		}


		/*am = new AddInMessage();
		am.Status = AddInMessage.STATUS_SUCCESS;
		return am;*/
		return new AddInMessage(AddInMessage.STATUS_SUCCESS);
	}

	/**
	 * Expire design cache item if older than given age.
	 * Pass 0 to force expire
	 */
	public void removeDesignCacheItem(String sKey)
	{
		TornadoServerInstance tsi = TornadoServer.getInstance();
		tsi.removeDesignCacheItem(sKey);

		/*m_pSystem.clearClassLoader(szKey);
    if(szKey==null)
    {
      m_cacheDesign.expireAll(0);
      m_cacheDesign.resetCounters();      
      return;
    }
    m_cacheDesign.removeItem(szKey);
    //clear the app always load params...
    RequestPath rPath = new RequestPath(szKey);
    String sAppPath = rPath.getPathToApplication().toLowerCase();
    m_htAppAlwaysLoad.remove(sAppPath);
		 */
	}

	public boolean isGroupLevelCookie()
	{
		return m_bGroupLevelCookies;
	}

	/**
	 * Return the largest content-length this server will accept. -1 is unlimited
	 * @return
	 */
	public long getMaxUploadBytes()
	{
		return m_lMaxUploadBytes;
	}

	/**
	 * Determines if the HTTP server allows reverse dns lookups. Note on some servers with a badly configured 
	 * DNS this may make the server appear to run slowly
	 * @return
	 */
	public boolean canLookupHostName() 
	{
		return !m_bNoReverseDNS;
	}

}//class
