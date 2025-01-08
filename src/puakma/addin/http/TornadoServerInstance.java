package puakma.addin.http;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Enumeration;
import java.util.Hashtable;

import puakma.addin.http.action.HTTPSessionContext;
import puakma.addin.http.document.DesignElement;
import puakma.pooler.Cache;
import puakma.system.RequestPath;
import puakma.system.SystemContext;
import puakma.util.Util;

public class TornadoServerInstance 
{
	private SystemContext m_pSystem;
	private Hashtable<String, TornadoApplication> m_htApplications = new Hashtable<String, TornadoApplication>();
	private Cache m_cacheDesign; //global, shared by all applications, 1 big bucket

	/**
	 * Create a new instance from the system context. If the singleton is already running, the passed 
	 * parameter is ignored
	 * @param pSystem
	 */
	protected TornadoServerInstance(SystemContext pSystem)
	{
		m_pSystem = pSystem;
		init();
	}

	/**
	 * 
	 */
	private void init()
	{
		double dCacheSize = 10485760; //10MB default
		String sTemp = m_pSystem.getSystemProperty("HTTPServerCacheMB");
		if(sTemp!=null)
		{
			try
			{
				dCacheSize = Double.parseDouble(sTemp) *1024 *1024;
			}
			catch(Exception de){};
		}
		m_cacheDesign = new Cache(dCacheSize);
	}

	/**
	 * Get a handle to the Tornado application
	 * @param sAppPath
	 * @return
	 */
	public synchronized TornadoApplication getTornadoApplication(String sAppPath)
	{
		RequestPath rp = new RequestPath(sAppPath);
		return getTornadoApplication(rp.Group, rp.Application);
	}

	public synchronized TornadoApplication getTornadoApplication(long lAppID)
	{
		RequestPath rp = getApplicationPath(lAppID);
		return getTornadoApplication(rp.Group, rp.Application);
	}

	public RequestPath getApplicationPath(long lAppID)
	{
		Connection cxSys=null;
		PreparedStatement stmt = null;
		ResultSet rs = null;

		RequestPath rp = null;


		try
		{
			String sQuery = "SELECT AppName,AppGroup FROM APPLICATION WHERE AppID=" + lAppID;
			cxSys = m_pSystem.getSystemConnection();
			stmt = cxSys.prepareStatement(sQuery, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);				
			rs = stmt.executeQuery();
			if(rs.next())
			{
				rp = new RequestPath(rs.getString("AppGroup"), rs.getString("AppName"), "", "");
			}        
		}
		catch(Exception de)
		{				
			m_pSystem.doError("getApplicationPath(long lAppID)", new String[]{de.toString()}, m_pSystem);				
		}
		finally
		{
			Util.closeJDBC(rs);
			Util.closeJDBC(stmt);
			m_pSystem.releaseSystemConnection(cxSys);
		}
		return rp;
	}

	/**
	 * Flush the entire application cache, remove all items from the 
	 * design cache and apps
	 *
	 */
	public synchronized void flushApplicationCache()
	{
		m_cacheDesign.expireAll(0);
		m_cacheDesign.resetCounters();
		m_htApplications.clear();
		m_pSystem.clearClassLoader(null);
	}

	/**
	 * remove a particular item from the design cache
	 * @param sKey
	 */
	public void removeDesignCacheItem(String sKey)
	{		
		//if no key or a wildcard app, drop the whole cache
		if(sKey==null || sKey.indexOf("/*/")>=0)
		{
			flushApplicationCache();      
			return;
		}
		m_pSystem.clearClassLoader(sKey);
		m_cacheDesign.removeItem(sKey);
		//clear the app always load params...
		//TODO always load?
		//RequestPath rPath = new RequestPath(szKey);
		//String sAppPath = rPath.getPathToApplication().toLowerCase();
		//m_htAppAlwaysLoad.remove(sAppPath);
	}

	/**
	 * 
	 * @param sAppGroup
	 * @param sAppName
	 * @return
	 */
	public synchronized TornadoApplication getTornadoApplication(String sAppGroup, String sAppName)
	{
		if(sAppGroup==null) sAppGroup="";
		if(sAppName==null) sAppName="";

		String sKey = ("/" + sAppGroup + "/" + sAppName).toLowerCase();
		//System.out.println("Locating key: " + sKey);
		if(m_htApplications.containsKey(sKey)) return (TornadoApplication)m_htApplications.get(sKey);
		//check if wildcard app
		String sKeyWildcard = ("/*/" + sAppName).toLowerCase();
		if(m_htApplications.containsKey(sKeyWildcard)) return (TornadoApplication)m_htApplications.get(sKeyWildcard);

		TornadoApplication ta = new TornadoApplication(m_pSystem, m_cacheDesign, sAppGroup, sAppName);
		//if(ta.appExists()) m_htApplications.put(sKey, ta);
		if(ta.appExists()) m_htApplications.put(ta.getApplicationKey(), ta);
		return ta;
	}

	/**
	 * 
	 * @return
	 */
	public Hashtable<String, TornadoApplication> getAllLoadedApplications()
	{
		return m_htApplications;
	}

	public String toString()
	{
		StringBuilder sb = new StringBuilder();
		Enumeration<String> en = m_htApplications.keys();
		while(en.hasMoreElements())
		{
			String sKey = (String)en.nextElement();
			TornadoApplication ta = (TornadoApplication)m_htApplications.get(sKey);
			sb.append(ta.toString());
		}

		return sb.toString();
	}

	public void shutdown()
	{
		m_htApplications.clear();
		if(m_cacheDesign!=null) m_cacheDesign.expireAll(-1);		
	}


	/**
	 * Show the statistics of the design cache
	 * @return
	 */
	public String getDesignCacheStats()
	{
		if(m_cacheDesign==null) return "";
		/*long lHits = (long)m_cacheDesign.getCacheHits();
		long lMisses = (long)m_cacheDesign.getCacheMisses();
		double dblHitPercent = ((double)lHits/(double)(lHits+lMisses))*100;
		NumberFormat nf2DP = NumberFormat.getInstance();
		nf2DP.setMaximumFractionDigits(2);
		nf2DP.setMinimumFractionDigits(2);
		NumberFormat nf0DP = NumberFormat.getInstance();
		nf0DP.setMaximumFractionDigits(0);
		nf0DP.setMinimumFractionDigits(0);
		return "elements=" + nf0DP.format(m_cacheDesign.getItemCount()) + " size="+nf2DP.format(m_cacheDesign.getCacheSize()/1024) + "Kb/" + nf2DP.format(m_cacheDesign.getCacheMaxSize()/1024) + "Kb hits=" + lHits + " misses=" + lMisses + " hitrate=" + nf2DP.format(dblHitPercent) +"%";
		 */
		return m_cacheDesign.toString();
	}

	/**
	 * Remove all design cache elements older than a certain age
	 * @param lAgeInMilliSeconds
	 */
	public void expireDesignCache(long lAgeInMilliSeconds) 
	{
		if(m_cacheDesign!=null) m_cacheDesign.expireAll(lAgeInMilliSeconds);		
	}

	/**
	 * Gets a design element from the application cache or database
	 * @param sAppGroup
	 * @param sAppName
	 * @param sDesignName
	 * @param iDesignType
	 * @return
	 */
	public DesignElement getDesignElement(String sAppGroup, String sAppName, String sDesignName, int iDesignType) 
	{
		TornadoApplication ta = getTornadoApplication(sAppGroup, sAppName);
		if(ta==null) return null;
		return ta.getDesignElement(sDesignName, iDesignType);
	}

	/**
	 * Get an application level parameter, eg LoginPage
	 * @param sAppGroup
	 * @param sAppName
	 * @param sParamName
	 * @return
	 */
	public String getApplicationParameter(String sAppGroup, String sAppName, String sParamName) 
	{
		TornadoApplication ta = getTornadoApplication(sAppGroup, sAppName);
		if(ta==null) return "";
		return ta.getApplicationParameter(sParamName);
	}

	/**
	 * Get a list of all design element names of a certain type in the named application
	 * @param sAppGroup
	 * @param sAppName
	 * @param iDesignType
	 * @return
	 */
	public String[] getAllDesignElementNames(String sAppGroup, String sAppName, int iDesignType) 
	{
		TornadoApplication ta = getTornadoApplication(sAppGroup, sAppName);
		if(ta==null) return new String[0];
		return ta.getAllDesignElementNames(iDesignType);

	}

	/**
	 * Get all the roles in the named application
	 * @param sAppGroup
	 * @param sAppName
	 * @return
	 */
	public String[] getApplicationRoles(String sAppGroup, String sAppName) 
	{
		TornadoApplication ta = getTornadoApplication(sAppGroup, sAppName);
		if(ta==null) return new String[0];
		return ta.getRoles();
	}

	/**
	 * Returns true if the current application has been marked as disabled
	 * @param sAppGroup
	 * @param sAppName
	 * @return
	 */
	public boolean isApplicationDisabled(String sAppGroup, String sAppName) 
	{
		TornadoApplication ta = getTornadoApplication(sAppGroup, sAppName);
		if(ta==null) return true;
		return ta.isApplicationDisabled();
	}

	public long getApplicationID(String sAppGroup, String sAppName) 
	{
		TornadoApplication ta = getTornadoApplication(sAppGroup, sAppName);
		if(ta==null) return -1;
		return ta.getApplicationID(); 
	}

	/**
	 * 
	 * @param pSession
	 * @param sURI
	 */
	public void processRequest(HTTPSessionContext pSession, String sURI)
	{ 
		//TornadoApplication ta = getTornadoApplication(sURI); 
		//TornadoApplicationReply tar = ta.processRequest(pSession, "todo", sURI, false, null);
	}

	public boolean clearClassLoader(String sAppPath) 
	{
		

		//clear all
		if(sAppPath==null)
		{
			boolean bCleared = false;
			Enumeration<String> en = m_htApplications.keys();
			while(en.hasMoreElements())
			{
				String sKey = (String)en.nextElement();
				TornadoApplication ta = (TornadoApplication)m_htApplications.get(sKey);			
				if(ta!=null && ta.clearClassLoader()) bCleared = true;
			}
			return bCleared;
		}
		
		//specific app clear
		TornadoApplication ta = getTornadoApplication(sAppPath);
		if(ta!=null && ta.appExists())
		{
			if(ta.clearClassLoader()) return true;
		}
		return false;
	}

}//class
