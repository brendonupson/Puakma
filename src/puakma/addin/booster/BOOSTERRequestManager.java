/***************************************************************************
The contents of this file are subject to the Puakma Public License Version 1.0 
 (the "License"); you may not use this file except in compliance with the 
 License. A copy of the License is available at http://www.puakma.net/

The Original Code is BOOSTERRequestManager. 
The Initial Developer of the Original Code is Brendon Upson. email: bupson@wnc.net.au 
Portions created by Brendon Upson are Copyright (C)2002. All Rights Reserved.

webWise Network Consultants Pty Ltd, Australia, http://www.wnc.net.au

Contributor(s) and Changelog:
- 
-
 ***************************************************************************/
package puakma.addin.booster;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import javax.net.SocketFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;

import puakma.addin.pmaAddIn;
import puakma.addin.pmaAddInStatusLine;
import puakma.addin.http.log.HTTPLogEntry;
import puakma.error.ErrorDetect;
import puakma.error.pmaLog;
import puakma.security.SSLSocketFactoryEx;
import puakma.server.AddInMessage;
import puakma.system.SessionContext;
import puakma.system.SystemContext;
import puakma.system.pmaThreadInterface;


/**
 * BOOSTERRequestManager
 * @author Brendon Upson
 * @date 9 November 2003
 */
public class BOOSTERRequestManager implements pmaThreadInterface, ErrorDetect
{
	//private final static int MAX_MS_RUNTIME = 300000;//5mins = 1000x60x5=
	protected SystemContext m_pSystem;
	protected SessionContext m_session = null;
	private Principal m_principal = null;

	private BOOSTERServer m_booster_server;    

	private String m_sProxyReference="";

	// these are all that come in on a new BOOSTERRequestManager (instantiated by
	// the BOOSTERServer at hit receive time)
	private long request_id;
	private Socket m_sockClient;
	private Socket m_sockServer;
	private int m_iHTTPPort;
	private boolean m_bSecure;      
	private BOOSTERTransferWorker m_BoostWorker=null;

	//private String m_http_request_line;  // Stores the GET/POST request line    
	private pmaAddInStatusLine m_pStatus;
	//private java.util.Date m_dtStart = new java.util.Date(); //timestamp object creation so we can work out transaction time

	private String m_sServer="";
	private boolean m_bIsGZIPCapable=false;
	//private static int DEFAULT_TIMEOUT=300000; //5 minutes = 300000ms

	/**
	 * constructor used by BOOSTERServer
	 */
	BOOSTERRequestManager(SystemContext paramSystem, BOOSTERServer server, Socket s, long rq_id, boolean bSecure)
	{
		m_bSecure = bSecure;
		m_booster_server = server;
		m_sockClient = s;
		try{ m_sockClient.setTcpNoDelay(true); }catch(Exception t){}
		request_id = rq_id;            
		m_pSystem = paramSystem; //this is shared      
		try{ 
			m_iHTTPPort = m_sockClient.getLocalPort(); 
			m_sockClient.setSoTimeout(m_booster_server.getDefaultSocketTimeout());
		} catch(Exception e){}      
	}


	/**
	 * determines if the connection is SSL or not.
	 * @return
	 */
	public boolean isSecureConnection()
	{
		return m_bSecure;
	}

	/**
	 *
	 */
	public void updateStatus(String sURI)
	{
		if(sURI==null)
			m_pStatus.setStatus("Request #" + request_id + " from " + m_sockClient.getInetAddress().getHostAddress());
		else
			m_pStatus.setStatus("Request #" + request_id + " " + m_sockClient.getInetAddress().getHostAddress() + " " + sURI);
	}


	/**
	 *
	 */
	public boolean shouldForceClientSSL(String sRequestedHost)
	{
		return this.m_booster_server.shouldForceClientSSL(sRequestedHost);
	}



	/**
	 * Invoked by the BOOSTERServer for each request.  Do not use publicly.
	 */
	public void run()
	{      
		//int iCount=0;
		//m_pSystem.doInformation("Handling HTTP request " + request_id + " from " + m_sockClient, this);
		m_pSystem.doDebug(pmaLog.DEBUGLEVEL_FULL, "run()", this);
		m_pStatus = m_booster_server.createStatusLine(" " + "BOOSTERreq");
		updateStatus(null);
		// if setup m_is successful, hand it off

		InputStream isClient;
		OutputStream osClient;
		//String sAddr = "unknown";

		try
		{          
			isClient = m_sockClient.getInputStream();
			osClient = m_sockClient.getOutputStream();
			m_BoostWorker = new BOOSTERTransferWorker(this, isClient, osClient, request_id);
			m_BoostWorker.run(); //not a thread!
		}
		catch(Exception r)
		{              
			m_pSystem.doError("run() "+r.toString(), this);          
			puakma.util.Util.logStackTrace(r, m_pSystem, 999);
		}         
		doCleanup();
		m_booster_server.removeStatusLine(m_pStatus);
	}


	/**
	 *
	 */
	public void setupHost(String sRequestedHost) throws Exception
	{
		if(sRequestedHost==null) return;

		int iPos = sRequestedHost.indexOf(':');
		if(iPos>0) sRequestedHost = sRequestedHost.substring(0, iPos);
		//System.out.println("BOOSTER_"+request_id + " >>" + sRequestedHost);

		//set m_sockServer
		doHostSocketSetup(sRequestedHost.toLowerCase());

		InputStream isServer;
		OutputStream osServer;
		osServer = null;
		isServer = null;
		if(m_sockServer!=null)
		{
			isServer = m_sockServer.getInputStream();
			osServer = m_sockServer.getOutputStream();
			m_BoostWorker.setServerStreams(isServer, osServer);
			//m_rClient.setOutputStream(osServer);
			//m_rServer.setInputStream(isServer);
		}        
	}

	/**
	 * get the size of the largest object we should try to gzip
	 */
	public int getMaxgzipBytes()
	{
		return m_booster_server.getMaxgzipBytes();
	}

	public int getMaxCacheableObjectBytes()
	{
		return m_booster_server.getMaxCacheableObjectBytes();
	}

	public int getMaxCacheMinutes()
	{
		return m_booster_server.getMaxCacheMinutes();
	}

	public int getMinCacheSeconds()
	{
		return m_booster_server.getMinCacheSeconds();
	}

	public boolean shouldRemoveCacheInfo()
	{
		return m_booster_server.shouldRemoveCacheInfo();
	}

	public String getBuildNumber()
	{
		return this.m_booster_server.getVersionNumber();
	}

	public boolean isServerReady()
	{
		if(m_sockServer==null) return false;
		return true;
	}


	/**
	 * tidy up the loose ends
	 */
	protected void finalize()
	{
		if(m_pStatus!=null) m_booster_server.removeStatusLine(m_pStatus);
		doCleanup();      
	}

	/**
	 * 0 is infinity, a negative value will set the default timeout
	 */
	/*public void zz_setClientTimeout(int iMS)
    {
        try{ 
            if(iMS<0)
                m_sockClient.setSoTimeout(DEFAULT_TIMEOUT);
            else
                m_sockClient.setSoTimeout(iMS);
        }
        catch(Exception e){}
    }*/

	/**
	 * Sets up the request. 
	 */
	private void doHostSocketSetup(String sRequestedHost)
	{
		if(sRequestedHost==null) return;
		m_pSystem.doDebug(pmaLog.DEBUGLEVEL_FULL, "doSetup()", this);
		String sSessionHostKey = ("bss_" + sRequestedHost).toLowerCase();

		//socket timeout disabled due to premature closing when a large file or
		//long transaction occurs
		/*try{ m_sockClient.setSoTimeout(DEFAULT_TIMEOUT); } 
        catch(Exception e){}*/		 
		if(shouldUseStickySessions())
		{

			if(m_session!=null)
			{
				//System.out.println("doHostSocketSetup() m_session is "+m_session.getSessionID());
				String sStickyHost = (String)m_session.getSessionObject(sSessionHostKey);
				if(sStickyHost!=null)
				{
					m_sServer = sStickyHost;
					//System.out.println("Using sticky session: " + sRequestedHost + " --> " + m_sServer);
					int iPort = getPort(m_sServer);
					String sHost = getHost(m_sServer);
					boolean bIsSSL = isSecureType(m_sServer, "SSL");					
					try
					{                          
						m_sockServer = makeSocket(sHost, iPort, bIsSSL); 
						try{ m_sockServer.setSoTimeout(m_booster_server.getDefaultSocketTimeout()); } 
						catch(Exception e){}						
					}
					catch(Exception ex)
					{                                
						//System.out.println(ex.toString());
						m_pSystem.doDebug(pmaLog.DEBUGLEVEL_DETAILED, "Host:["+sHost+"] port:" + iPort + " is unavailable", this);											                
					}					
					return;					
				}
				//else
				//	System.out.println("Sticky session not setup yet for: " + sRequestedHost);
			}			
		}

		//String sPort="";
		String sHost=null;
		int iPort=80;
		boolean bCheckAvailability=false;

		sHost = m_booster_server.getNextAvailableHost(sRequestedHost); 
		//System.out.println("Trying host: "+sHost);
		boolean bIsSSL = false;

		int iAvailableHosts = m_booster_server.getAvailableHostCount(sRequestedHost);
		for(int i=0; i<iAvailableHosts; i++)
		{
			iPort = getPort(sHost);			
			bIsSSL = isSecureType(sHost, "SSL");			
			sHost = getHost(sHost); //careful, sHost variable is changed here!

			/*bIsSSL = false;
			int iPos = sHost.indexOf(':');
			if(iPos>0)
			{
				sPort = sHost.substring(iPos+1, sHost.length());
				int iSSLPos = sPort.toUpperCase().indexOf("SSL");
				if(iSSLPos>0)
				{
					sPort = sPort.substring(0, iSSLPos);
					bIsSSL = true;
					iPort = 443; //default ssl port
				}                
				sHost = sHost.substring(0, iPos);
				try{iPort = Integer.parseInt(sPort);}catch(Exception y){ }
			}*/

			m_pSystem.doDebug(pmaLog.DEBUGLEVEL_DETAILED, "Attempting to use host:["+sHost+"] port:" + iPort, this);            

			try
			{                          
				m_sockServer = makeSocket(sHost, iPort, bIsSSL); 
				try{ m_sockServer.setSoTimeout(m_booster_server.getDefaultSocketTimeout()); } 
				catch(Exception e){}
				break; //request is OK, break from loop
			}
			catch(Exception ex)
			{                                
				//System.out.println(ex.toString());
				m_pSystem.doDebug(pmaLog.DEBUGLEVEL_DETAILED, "Host:["+sHost+"] port:" + iPort + " is unavailable, trying next node...", this);
				sHost = m_booster_server.getNextAvailableHost(sRequestedHost);
				bCheckAvailability=true;
				if(sHost==null) break;                 
			}
		}

		if(bCheckAvailability) m_booster_server.checkAvailability();
		if(iPort==80)
			m_sServer = sHost;
		else
			m_sServer = sHost+':'+iPort;

		//store the server used on the session
		if(shouldUseStickySessions() && m_session!=null)
		{			
			String sLine = m_sServer;
			if(bIsSSL) sLine += "SSL";			
			m_session.addSessionObject(sSessionHostKey, sLine);
			//System.out.println("Saving sticky session for: " + sLine); 
		}
	}


	/**
	 * Determines if the host to use from the line in booster.config is using SSL
	 * eg "www.xyz.com:45SSL" will return true
	 * @param sServerLineFromConfigFile
	 * @param sSecureType "SSL" or "TLS"
	 * @return
	 */
	/*private static boolean isSSL(String sServerLineFromConfigFile) 
	{
		int iPos = sServerLineFromConfigFile.indexOf(':');
		if(iPos>=0)
		{
			String sPort = sServerLineFromConfigFile.substring(iPos+1, sServerLineFromConfigFile.length());
			int iSSLPos = sPort.toUpperCase().indexOf("SSL");
			if(iSSLPos>=0) return true;			
		}
		return false;
	}*/

	private static boolean isSecureType(String sServerLineFromConfigFile, String sSecureType) 
	{
		int iPos = sServerLineFromConfigFile.indexOf(':');
		if(iPos>=0)
		{
			String sPort = sServerLineFromConfigFile.substring(iPos+1, sServerLineFromConfigFile.length());
			int iSSLPos = sPort.toUpperCase().indexOf("SSL");
			if(iSSLPos>=0) return true;			
		}
		return false;
	}


	/**
	 * Gets the host to use from the line in booster.config
	 * eg "www.xyz.com:45SSL" will return "www.xyz.com"
	 * @param sServerLineFromConfigFile
	 * @return
	 */
	private static String getHost(String sServerLineFromConfigFile) 
	{
		int iPos = sServerLineFromConfigFile.indexOf(':');
		if(iPos>=0)
		{
			return sServerLineFromConfigFile.substring( 0, iPos);					
		}

		return sServerLineFromConfigFile; //no port specified
	}


	/**
	 * Gets the port number to use from the line in booster.config
	 * eg "www.xyz.com:45SSL" will return 45
	 * @param sServerLineFromConfigFile
	 * @return
	 */
	private static int getPort(String sServerLineFromConfigFile) 
	{
		int iPort = 80;
		int iPos = sServerLineFromConfigFile.indexOf(':');
		if(iPos>=0)
		{
			String sPort = sServerLineFromConfigFile.substring(iPos+1, sServerLineFromConfigFile.length());
			int iSSLPos = sPort.toUpperCase().indexOf("SSL");
			if(iSSLPos>0)
			{
				sPort = sPort.substring(0, iSSLPos);
				iPort = 443; //default ssl port
			}                			
			try{iPort = Integer.parseInt(sPort);}catch(Exception y){ }		
		}
		return iPort;
	}


	/**
	 * Get the name of the server we connected to
	 */
	public String getServerName()
	{
		return m_sServer;
	}

	public boolean shouldUseRealHostName()
	{
		return m_booster_server.shouldUseRealHostName();
	}

	public boolean shouldUseStickySessions()
	{
		return m_booster_server.shouldUseStickySessions();
	}

	/**
	 *
	 */
	private Socket zz_makeSocket(String sHost, int iPort, boolean bIsSSL) throws Exception
	{
		if(!bIsSSL) return new Socket(sHost, iPort);

		SSLContext ctx = SSLContext.getInstance("SSL");
		TrustManager tmArray[] = new TrustManager[1];
		tmArray[0] = new puakma.util.RelaxedTrustManager();          
		ctx.init(null, tmArray, null);

		SocketFactory sf = ctx.getSocketFactory();
		Socket s = sf.createSocket(sHost, iPort);


		try{ s.setTcpNoDelay(true); }catch(Exception t){}
		return s;
	}

	private Socket makeSocket(String sHost, int iPort, boolean bIsSSL) throws Exception
	{
		if(!bIsSSL) return new Socket(sHost, iPort);

		//SocketFactory sf = ctx.getSocketFactory();
		SSLSocketFactoryEx sf = new SSLSocketFactoryEx();
		Socket s = sf.createSocket(sHost, iPort);

		try{ s.setTcpNoDelay(true); }catch(Exception t){}
		return s;
	}


	/**
	 * Close the input and output streams
	 */
	private void doCleanup()
	{
		m_pSystem.doDebug(pmaLog.DEBUGLEVEL_FULL, "doCleanup()", this);
		try
		{          

			if(m_sockClient!=null) m_sockClient.close();
			if(m_sockServer!=null) m_sockServer.close();
		}
		catch(Exception ioe)
		{
			m_pSystem.doError("HTTPRequest.CloseInput", new String[]{ioe.toString()}, this);
		}      
	}

	/**
	 *
	 */
	public void destroy()
	{
		doCleanup();
	}

	/**
	 *
	 */
	public String getProxyReference()
	{
		return m_sProxyReference;        
	}

	/**
	 *
	 */
	public synchronized void setProxyReference(String sNewRef)
	{
		m_sProxyReference = sNewRef;        
	}


	public boolean shouldGZipOutput(String sURI, String sContentType)
	{
		if(!m_bIsGZIPCapable) return false;        
		boolean bZip = m_booster_server.shouldGZipOutput(sURI, sContentType);;
		//System.out.println("should gzip "+sContentType + ' ' + bZip);
		return bZip;        
	}

	public boolean shouldCacheOutput(String sURI, String sContentType)
	{        
		return m_booster_server.shouldCacheOutput(sURI, sContentType);
	}

	public boolean shouldFixContentTypes()
	{
		return m_booster_server.shouldFixContentTypes();
	}

	/**
	 *
	 */
	public void setGZIPCapable(boolean bIsCapable)
	{
		//System.out.println("gzip="+bIsCapable);
		m_bIsGZIPCapable = bIsCapable;
	}

	/**
	 *
	 */
	public boolean isDebug()
	{
		return m_booster_server.isDebug();
	}

	public boolean isDebugHeaders()
	{
		return m_booster_server.isDebugHeaders();
	}


	/**
	 * Allow other tasks to execute console commands
	 * @param szCommand
	 * @return a string containing the results of the command. CRLF breaks each line
	 */
	public String doConsoleCommand(String szCommand)
	{
		return m_booster_server.doConsoleCommand(szCommand);
	}

	public String getErrorSource()
	{
		return "BOOSTERreq (" + m_iHTTPPort + ')';
	}

	public String getErrorUser()
	{    
		return m_sockClient.getInetAddress().getHostAddress();   
	}

	public String getClientIPAddress()
	{
		return m_sockClient.getInetAddress().getHostAddress();
	}

	/**
	 * Gets the hostname of the client as specified in the reverse dns. Reverse DNS may be
	 * turned off in the booster config, if so just return the ip address.
	 * @return
	 */
	public String getClientHostName()
	{
		if(m_booster_server.allowReverseDNS())
			return m_sockClient.getInetAddress().getHostName();

		return getClientIPAddress();
	}

	/**
	 * get the ip address of web booster
	 */
	public String getBoosterIPAddress()
	{
		return m_sockClient.getLocalAddress().getHostAddress();
	}

	/**
	 * list of mimetypes to exclude form logging
	 */
	public ArrayList getMimeExcludes()
	{
		return m_booster_server.getMimeExcludes();
	}

	/**
	 * get the http port that the client connected to
	 */
	public int getServerPort()
	{
		return m_iHTTPPort;
	}

	public File getUnavailableFile()
	{
		return m_booster_server.getUnavailableFile();
	}

	public String getFileMimeType(String sRequest)
	{
		return m_booster_server.getFileMimeType(sRequest);
	}

	/**
	 *
	 */
	public String[] getReplaceHosts()
	{
		return m_booster_server.getReplaceHosts();
	}

	/**
	 *
	 */
	public boolean getForceSSL(String sDomain)
	{
		return m_booster_server.getForceSSL(sDomain);
	}

	public void updateCompressionStats(double dblSent, double dblCompressed)
	{
		m_booster_server.updateCompressionStats(dblSent, dblCompressed);
	}

	public void updateBytesSent(double dblSent)
	{
		m_booster_server.updateBytesSent(dblSent);
	}

	public int getMaxURI()
	{
		return m_booster_server.getMaxURI();
	}

	/**
	 *
	 */
	public String[] getCustomHeaderProcessors()
	{
		return m_booster_server.getCustomHeaderProcessors();
	}



	public String getThreadDetail() 
	{
		return "Booster";
	}

	public void updateTextPageServeCount()
	{
		m_booster_server.updateTextPageServeCount();
	}

	public long getTextPageServeCount()
	{
		return m_booster_server.getTextPageServeCount();
	}

	public String formatNumber(double dblNumber)
	{
		return m_booster_server.formatNumber(dblNumber);
	}

	/**
	 * Writes lines to log web request comperssion stats
	 */
	public void writeCompressionStatLog(String sURI, int iOrigSize, int iNewSize, int iWebServerTime, int iTotalTime, String sContentType)
	{
		m_booster_server.writeCompressionStatLog(sURI, iOrigSize, iNewSize, iWebServerTime, iTotalTime, sContentType);
	}

	/**
	 * Writes lines to log web requests
	 */
	public void writeTextStatLog(HTTPLogEntry stat)
	{
		m_booster_server.writeTextStatLog(stat);  
	}

	/**
	 * get an item from the cache
	 */
	public BOOSTERCacheItem getCacheItem(String sKey)
	{
		return m_booster_server.getCacheItem(sKey);
	}

	/**
	 * Add an item to the cache
	 */
	public boolean addToCache(BOOSTERCacheItem item)
	{
		return m_booster_server.addToCache(item);
	}

	public boolean isSharedCache()
	{
		return m_booster_server.isSharedCache();
	}

	public String getPublicDir()
	{
		return m_booster_server.getPublicDir();
	}

	/**
	 * Determine if this software has the appropriate keys
	 */
	public boolean isLicensed()
	{
		return m_booster_server.isLicensed();
	}

	public long getMaxUploadBytes()
	{
		return m_booster_server.getMaxUploadBytes(); 
	}


	public pmaAddIn getAddIn() 
	{
		return m_booster_server.getAddIn();
	}


	public void setSessionPrincipal(Principal principal) 
	{
		m_principal = principal;
	}


	public Principal getPrincipal() 
	{
		return m_principal;
	}


	public void pushClusterSession(SessionContext sess)
	{
		if(m_pSystem.isAddInLoaded("CLUSTER"))
		{
			//m_pSystem.doConsoleCommand("tell CLUSTER synch");
			AddInMessage msg = new AddInMessage();
			msg.setParameter("action", "push");
			msg.setParameter("sessionid", sess.getSessionID());
			m_pSystem.sendMessage("CLUSTER", msg);
		}
	}


}//class



