/** ***************************************************************
HTTPServer.java
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

import java.io.FileInputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.KeyStore;
import java.security.Principal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Properties;

import javax.net.ServerSocketFactory;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;

import puakma.addin.pmaAddIn;
import puakma.addin.pmaAddInStatusLine;
import puakma.addin.http.document.DesignElement;
import puakma.addin.http.log.HTTPLogEntry;
import puakma.error.ErrorDetect;
import puakma.error.pmaLog;
import puakma.system.RequestPath;
import puakma.system.SessionContext;
import puakma.system.SystemContext;
import puakma.system.pmaSystem;
import puakma.system.pmaThreadInterface;
import puakma.system.pmaThreadPoolManager;





/**
 * HTTPServer is Puakma's web server.
 * It is loaded as an addin
 */
public class HTTPServer extends Thread implements ErrorDetect
{
	private ServerSocket m_ss;
	private HTTP m_Parent;
	private boolean m_bRunning=false;
	private boolean m_bSSL=false;
	private boolean m_bTrustStoreEnabled=false;
	private String m_sInterface=null;

	private long request_id = 0;
	private int max_pooled_threads = 0;
	private int min_pooled_threads = 0;
	private int thread_pool_timeout = 5000;
	private pmaThreadPoolManager m_tpm;
	private String m_sSystemHostName;

	public String HTTP_PublicDir;
	public String m_sHTTPMaxSessionRedirect="";
	public int iHTTPPortTimeout=30000; //30 seconds
	private int m_iHTTPPort=-1;
	public Properties propMime= new Properties();
	//allow anonymous access to the public directory
	public boolean bAllowAnonPublicDir=false;
	private pmaAddInStatusLine pStatus;
	private SystemContext m_pSystem;

	private double m_dblBytesServed=0;
	private boolean m_bLogToRDB=false;
	private boolean m_bLogToFile=false;
	public boolean m_bLogInbound=false;

	private String m_sHTTPSSLKeyRing="";
	private String m_sHTTPSSLKeyRingPW="";

	private String m_sHTTPSSLTrustStore="";
	private String m_sHTTPSSLTrustStorePW="";

	private String m_sHTTPSOAPServer="";
	private int m_iHTTPMaxPerConnection=5;
	private int m_iHTTPMaxExpirySeconds=3600; //1hour
	private int m_iHTTPSlowActionMS = 0; //log actions that take longer than this
	private boolean m_bGZipOutput=false;
	private int m_iMaxURI=512; //default to 512 bytes
	private NumberFormat m_nfWhole = NumberFormat.getIntegerInstance();
	private NumberFormat m_nfMB = NumberFormat.getInstance();
	private String m_sHTTPSSLContext = "";
	private boolean m_bShouldDisableBasicAuth = false;

	public static final String SESSIONID_LABEL="_pma_sess_id";


	public HTTPServer(SystemContext paramCtx, int iPort, HTTP paramParent, boolean paramSSL)
	{
		m_bSSL = paramSSL;
		m_Parent = paramParent;
		m_pSystem = (SystemContext)paramCtx.clone();
		m_iHTTPPort = iPort;
		setDaemon(true);
	}

	/**
	 * This method is called by the pmaServer object
	 */
	public void run()
	{
		m_nfMB.setMinimumFractionDigits(2);
		m_nfMB.setMaximumFractionDigits(2);
		m_bRunning = true;
		
		m_sSystemHostName = m_pSystem.getSystemProperty("SystemHostName");	    
		//instance_name = m_pSystem.getSystemProperty("HTTPServerName");	    
		//m_pSystem.doInformation("HTTPServer.Startup", new String[]{instance_name}, this);
		// get the configs
		String szMimeFile = m_pSystem.getSystemProperty("HTTPMimeFile");
		HTTP_PublicDir = m_pSystem.getSystemProperty("HTTPPublicDir");
		String szTemp = m_pSystem.getSystemProperty("HTTPAllowAnonPublicDir");
		if(szTemp!=null && szTemp.equals("1")) bAllowAnonPublicDir = true;
		szTemp = m_pSystem.getSystemProperty("HTTPMaxSessionRedirect");
		if(szTemp!=null) m_sHTTPMaxSessionRedirect = new String(szTemp);

		m_sHTTPSOAPServer = m_pSystem.getSystemProperty("HTTPSOAPServer");
		if(m_sHTTPSOAPServer==null || m_sHTTPSOAPServer.length()==0) m_sHTTPSOAPServer = "puakma.addin.widgie.WIDGIE";
		m_sHTTPSSLKeyRing = m_pSystem.getSystemProperty("HTTPSSLKeyRing");
		m_sHTTPSSLKeyRingPW = m_pSystem.getSystemProperty("HTTPSSLKeyRingPW");
		if(m_sHTTPSSLKeyRingPW==null) m_sHTTPSSLKeyRingPW="";

		m_sHTTPSSLTrustStore = m_pSystem.getSystemProperty("HTTPSSLTrustStore");
		m_sHTTPSSLTrustStorePW = m_pSystem.getSystemProperty("HTTPSSLTrustStorePW");
		if(m_sHTTPSSLTrustStorePW==null) m_sHTTPSSLTrustStorePW="";
		
		m_sHTTPSSLContext = m_pSystem.getSystemProperty("HTTPSSLContext");
		if(m_sHTTPSSLContext==null || m_sHTTPSSLContext.length()==0) m_sHTTPSSLContext = "SSL";

		pStatus = m_Parent.createStatusLine(" " + getErrorSource());
		pStatus.setStatus("Starting...");

		szTemp =m_pSystem.getSystemProperty("HTTPTextLog");
		if(szTemp!=null && szTemp.length()>0) m_bLogToFile = true;
		szTemp =m_pSystem.getSystemProperty("HTTPLogToRDB");
		if(szTemp!=null && szTemp.equals("1")) m_bLogToRDB = true;
		szTemp =m_pSystem.getSystemProperty("HTTPLogInbound");
		if(szTemp!=null && szTemp.equals("1")) m_bLogInbound = true;

		szTemp =m_pSystem.getSystemProperty("HTTPgzip");
		if(szTemp!=null && szTemp.equals("1")) m_bGZipOutput = true;  
		
		szTemp =m_pSystem.getSystemProperty("HTTPDisableBasicAuth");
		if(szTemp!=null && szTemp.equals("1")) m_bShouldDisableBasicAuth = true;  
		

		InetAddress cInterface=null;
		m_sInterface = m_pSystem.getSystemProperty("HTTPInterface");
		if(m_sInterface!=null)
		{
			try
			{
				cInterface = InetAddress.getByName(m_sInterface);
			}
			catch(Exception e){}
		}

		try
		{
			propMime.load(new FileInputStream(szMimeFile));			
			//TODO trim spaces and only load useful stuff, not comments eg:
			//mpg=video/mpeg                           # MPEG
			//enumerate and adjust/trim the values? hould also be moved to HTTP so we only load one instance
		}
		catch(Exception e)
		{
			m_pSystem.doInformation("HTTPServer.NoMimeFile", new String[]{szMimeFile}, this);
		}
		// startup the
		try
		{
			max_pooled_threads = Integer.parseInt(m_pSystem.getSystemProperty("HTTPMaxThreads"));
			min_pooled_threads = Integer.parseInt(m_pSystem.getSystemProperty("HTTPMinThreads"));
			thread_pool_timeout = Integer.parseInt(m_pSystem.getSystemProperty("HTTPThreadCreateTimeout"));
		}catch(Exception e){}
		m_tpm = new pmaThreadPoolManager(m_pSystem, min_pooled_threads, max_pooled_threads, thread_pool_timeout, "http-"+m_iHTTPPort);
		m_tpm.start();


		// prepare server socket for accept
		int iHTTPPortBacklog = 10;
		try{ iHTTPPortTimeout = Integer.parseInt(m_pSystem.getSystemProperty("HTTPPortTimeout"));}
		catch(Exception r){}
		try{ iHTTPPortBacklog = Integer.parseInt(m_pSystem.getSystemProperty("HTTPPortBacklog"));}
		catch(Exception r){}
		try{ m_iHTTPMaxPerConnection = Integer.parseInt(m_pSystem.getSystemProperty("HTTPMaxPerConnection")); }
		catch(Exception r){}

		try{ m_iMaxURI = Integer.parseInt(m_pSystem.getSystemProperty("HTTPMaxURI")); }
		catch(Exception r){}

		try{ m_iHTTPMaxExpirySeconds = Integer.parseInt(m_pSystem.getSystemProperty("HTTPMaxExpirySeconds")); }
		catch(Exception r){}
		if(m_iHTTPMaxExpirySeconds<1) m_iHTTPMaxExpirySeconds = 3600;

		try{ m_iHTTPSlowActionMS = Integer.parseInt(m_pSystem.getSystemProperty("HTTPSlowActionMS")); }
		catch(Exception r){}
		

		try
		{
			if(m_bSSL)
			{
				ServerSocketFactory ssf = getServerSocketFactory(m_bSSL, m_iHTTPPort);
				if(ssf!=null) 
				{
					m_ss = ssf.createServerSocket(m_iHTTPPort, iHTTPPortBacklog, cInterface);					
				}

			}
			else
			{				
				m_ss = new ServerSocket (m_iHTTPPort, iHTTPPortBacklog, cInterface);
			}
		}
		catch(Exception ioe)
		{
			m_pSystem.doError("HTTPServer.NoPortOpen", new String[]{String.valueOf(m_iHTTPPort)}, this);
			if(m_sInterface!=null) m_pSystem.doError("HTTPServer.InterfaceError", new String[]{m_sInterface}, this);
			shutdownAll();
			m_Parent.removeStatusLine(pStatus);
			return;
		}

		m_pSystem.doInformation("HTTPServer.Listening", new String[]{String.valueOf(m_iHTTPPort)}, this);

		// main loop
		while(m_bRunning)
		{

			try
			{
				updateStatusLine();						
				Socket sock = m_ss.accept();
				//sock.setSoLinger(true, 1); //1 second
				request_id++;
				//System.out.println(request_id + " handoff");	

				dispatch(sock);
			}
			catch(Exception ioe)
			{
				m_pSystem.doError("HTTPServer.AcceptFail", new String[]{ioe.getMessage()}, this);
			}
		}//end while
		m_tpm.requestQuit();
		pStatus.setStatus("Shutting down");
		m_pSystem.doInformation("HTTPServer.ShuttingDown", this);
		try
		{
			m_ss.close();
		}
		catch (IOException ioe)
		{
			m_pSystem.doError("HTTPServer.SocketCloseFail", this);
		}
		m_pSystem.doInformation("HTTPServer.SocketClosed", this);

		shutdownAll();
		m_Parent.removeStatusLine(pStatus);
		//m_pSystem.clearDBPoolManager();
	}


	

	/**
	 *
	 */
	private void updateStatusLine()
	{
		if(m_sInterface==null)
			pStatus.setStatus("Listening. hits:" + m_nfWhole.format(request_id) + " avg:" + m_nfWhole.format(m_tpm.getAverageExecutionTime()) + "ms " + m_nfMB.format(m_dblBytesServed/1024/1024) + "mb");
		else
			pStatus.setStatus("Listening " + m_sInterface + ". hits:" + m_nfWhole.format(request_id) + " avg:" + m_nfWhole.format(m_tpm.getAverageExecutionTime()) + "ms " + m_nfMB.format(m_dblBytesServed/1024/1024) + "mb");
	}

	public boolean serverAllowsByteServing()
	{
		return m_Parent.serverAllowsByteServing();
	}

	public boolean shouldGenerateETags()
	{
		return m_Parent.shouldGenerateETags();
	}

	public boolean shouldMinifyFileSystemJS()
	{
		return m_Parent.shouldMinifyFileSystemJS();
	}


	/**
	 * record how many bytes the server has served
	 */
	public synchronized void updateBytesServed(int iAddBytes)
	{
		m_dblBytesServed += iAddBytes;
		updateStatusLine();
	}

	/**
	 * Writes lines to log web requests
	 */
	public void writeStatLog(HTTPLogEntry stat, String sInboundPath, String sInboundMethod, int iInboundSize)
	{
		if(m_bLogToFile) writeTextStatLog(stat);
		if(m_bLogToRDB) m_Parent.writeRDBStatLog(stat);
		if(m_bLogInbound) writeRDBInboundStatLog(sInboundPath, sInboundMethod, iInboundSize, stat.getCanonicalUserName());
	}


	/**
	 * Writes entries to log web requests to relational DB Table HTTPStat
	 */
	private void writeRDBInboundStatLog(String sInboundPath, String sInboundMethod, int iInboundSize, String szUserName)
	{

		Connection cx = null;

		try
		{
			cx = m_pSystem.getSystemConnection();
			PreparedStatement prepStmt = cx.prepareStatement("INSERT INTO HTTPSTATIN(RequestDate,Method,Request,ContentLength,UserName,ServerName) VALUES(?,?,?,?,?,?)");
			prepStmt.setTimestamp(1, new java.sql.Timestamp(System.currentTimeMillis()));
			prepStmt.setString(2, sInboundMethod);
			prepStmt.setString(3, sInboundPath);
			prepStmt.setInt(4, iInboundSize);
			prepStmt.setString(5, szUserName);
			prepStmt.setString(6, m_sSystemHostName);
			prepStmt.execute();      
			prepStmt.close();      
		}
		catch(Exception e)
		{
			m_pSystem.doError("HTTPServer.WriteStatLogError", new String[]{"RDB_TABLE=HTTPSTATIN", e.getMessage()}, this);
		}
		finally
		{
			m_pSystem.releaseSystemConnection(cx);
		}
	}




	/**
	 * Writes lines to log web requests
	 */
	private void writeTextStatLog(HTTPLogEntry stat)
	{
		m_Parent.writeTextStatLog(stat);
	}

	/**
	 * Called to request the server to stop
	 */
	public void requestQuit()
	{
		if(m_ss!=null)
		{
			m_bRunning = false;
			try
			{
				m_ss.close();          
			}catch(Exception e){}
		}
		m_tpm.requestQuit();
	}

	/**
	 * Determine if the task is running
	 */
	public boolean isRunning()
	{
		return m_bRunning;
	}

	/**
	 * used by main accept loop to serve HTTP requests (either serve
	 * a file or launch new RequestManagers).
	 * This will block and wait if too many RMs are active.
	 */
	private void dispatch(Socket sock)
	{
		boolean bOK=false;
		m_pSystem.doDebug(pmaLog.DEBUGLEVEL_FULL, "dispatch()", this);

		if(this.m_pSystem.addressHasAccess(sock.getInetAddress().getHostAddress()))
		{
			//System.out.println("====== Creating rm");
			HTTPRequestManager rm = new HTTPRequestManager(m_pSystem, this, sock, request_id, m_bSSL, m_sSystemHostName);
			//System.out.println("====== rm created");
			if(m_bSSL && m_bTrustStoreEnabled) 
			{				
				SSLSocket sslSock = (SSLSocket)sock;				
				try {					
					//System.out.println("====== Starting handshake");
					//System.out.println("getEnabledCipherSuites: " + Util.implode(sslSock.getEnabledCipherSuites(), "; "));
					//System.out.println("getEnabledProtocols: " + Util.implode(sslSock.getEnabledProtocols(), "; "));
					//sslSock.setEnabledCipherSuites(new String[]{"TLS_DHE_RSA_WITH_AES_256_CBC_SHA"});
					sslSock.setUseClientMode(false);					
					sslSock.setWantClientAuth(true);
					sslSock.setEnableSessionCreation(true);		
					//long lStart = System.currentTimeMillis();
					sslSock.setTcpNoDelay(true);
					SSLSession sess = sslSock.getSession();
					if(sess!=null && sess.isValid())
					{

						Principal principal = sess.getPeerPrincipal();
						rm.setSessionPrincipal(principal);						
						/*
						System.out.println("CipherSuite=["+sess.getCipherSuite()+"]");
						long lDiff = System.currentTimeMillis() - lStart;
						System.out.println("Handshake took: " + lDiff + "ms");
						 */ 
					}
					//System.out.println("====== Finished handshake: " + sess.getPeerPrincipal().getName());
					//
					//InputStream inputstream = sslSock.getInputStream();
				} catch (Exception e) {			
					e.printStackTrace();
					return;
				}
			}
			//System.out.println("****** REQUEST " + request_id + " " + " ******");
			bOK=m_tpm.runThread((pmaThreadInterface)rm);
		}
		else
		{
			puakma.util.AntiHackWorker ahw = new puakma.util.AntiHackWorker(m_pSystem, sock);
			bOK=m_tpm.runThread((pmaThreadInterface)ahw);
		}

		if(!bOK)
		{
			m_pSystem.doError("HTTPServer.RequestThreadError", this);
			// sock.close(); //drop the connection??        
		}		
	}

	/**
	 * Returns the number of active RequestManager Threads.  Allows you to
	 * get and idea of current load/concurrency.
	 */
	public int getActiveThreadCount()
	{
		return m_tpm.getActiveThreadCount();
	}

	/**
	 * Returns the number of active RequestManager Threads.  Allows you to
	 * get and idea of current load/concurrency.
	 */
	public int getThreadPoolSize()
	{
		return m_tpm.getThreadCount();
	}

	/**
	 * Returns the number of active RequestManager Threads.  Allows you to
	 * get and idea of current load/concurrency.
	 */
	public int getMaxThreadPoolSize()
	{
		return m_tpm.getThreadMax();
	}


	/**
	 * gets the current value of the request id.  The value is
	 * incremented just before pulling a new client off the queue.
	 */
	public long getCurrentRequestID ()
	{
		return (request_id);
	}


	/**
	 *
	 * @return
	 */
	public int getMaxRequestsPerConnection()
	{
		return m_iHTTPMaxPerConnection;
	}


	/**
	 * Close all active request manager threads
	 */
	private void shutdownAll()
	{
		m_pSystem.doDebug(pmaLog.DEBUGLEVEL_FULL, "shutdownAll()", this);
		// wait for all active rms to end (thus returning their
		// db connections to the internal dbcp
		int loopcount = 0;
		int active_count = 0;
		int iShutDownSeconds = 5; 

		try{ iShutDownSeconds = Integer.parseInt(m_pSystem.getSystemProperty("HTTPShutdownWaitSeconds")); }
		catch(Exception r){}

		//tpm.destroy();
		while((active_count = m_tpm.getActiveThreadCount()) > 0 && loopcount < iShutDownSeconds)
		{
			m_pSystem.doInformation("HTTPServer.ShutdownPending", new String[]{String.valueOf(active_count)}, this);
			loopcount++;
			try
			{
				Thread.sleep (1000);
			}
			catch (InterruptedException ie) {}
		}//end while

		// not all threads have quit cleanly!
		if (active_count > 0)
			m_pSystem.doInformation("HTTPServer.ThreadsActive", new String[]{String.valueOf(active_count)}, this);

		m_pSystem.doInformation("HTTPServer.Shutdown", this);
	}


	public pmaAddInStatusLine createStatusLine(String szName)
	{
		return m_Parent.createStatusLine(szName);
	}

	public void removeStatusLine(pmaAddInStatusLine pStatus)
	{
		m_Parent.removeStatusLine(pStatus);
	}

	public String getErrorSource()
	{
		String sSSL="";
		if(m_bSSL) sSSL=m_sHTTPSSLContext;//"SSL";
		return "HTTPServer(" + m_iHTTPPort + sSSL + ")" ;
	}

	public String getErrorUser()
	{
		return pmaSystem.SYSTEM_ACCOUNT;
	}


	private String getKeyRingPassword(int iPort)
	{		
		String sPW = m_pSystem.getSystemProperty("HTTPSSLKeyRingPW"+iPort);
		if(sPW==null) sPW = m_sHTTPSSLKeyRingPW;
		if(sPW==null) return "";
		return sPW;
	}
	
	private String getKeyRingFilePath(int iPort)
	{		
		String sPath = m_pSystem.getSystemProperty("HTTPSSLKeyRing"+iPort);
		if(sPath==null) sPath = m_sHTTPSSLKeyRing;
		if(sPath==null) return "";
		return sPath;		
	}

	/**
	 * Determine the type of socket to open. Can use a different keystore for each port
	 */
	private ServerSocketFactory getServerSocketFactory(boolean bGetSSL, int iPort) throws Exception
	{
		if(bGetSSL)
		{
			SSLServerSocketFactory ssf = null;
			try
			{
				String sKeyRingPW = getKeyRingPassword(iPort);
				String sKeyRingFilePath = getKeyRingFilePath(iPort);
				
				// set up key manager to do server authentication				
				char[] cKeyStorePassphrase = sKeyRingPW.toCharArray(); //m_sHTTPSSLKeyRingPW.toCharArray(); //password to access keystore


				SSLContext ctx = SSLContext.getInstance(m_sHTTPSSLContext); //TLS | SSL
				KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
				KeyStore ks = KeyStore.getInstance("JKS");
				//ks.load(new FileInputStream(m_sHTTPSSLKeyRing), cKeyStorePassphrase);
				ks.load(new FileInputStream(sKeyRingFilePath), cKeyStorePassphrase);
				kmf.init(ks, cKeyStorePassphrase);

				TrustManager trustManagers[] = null;
				if(m_sHTTPSSLTrustStore!=null && m_sHTTPSSLTrustStore.length()>0)
				{
					char[] cTrustStorePassphrase = m_sHTTPSSLTrustStorePW.toCharArray(); //password to access trust store
					TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509"); //SunX509
					KeyStore trustStore = KeyStore.getInstance("JKS");
					trustStore.load(new FileInputStream(m_sHTTPSSLTrustStore), cTrustStorePassphrase);
					tmf.init(trustStore);
					trustManagers = tmf.getTrustManagers();
					m_bTrustStoreEnabled = true;
					m_pSystem.doInformation("HTTPServer.TrustStoreStatus", new String[]{m_sHTTPSSLTrustStore, String.valueOf(trustStore.size())}, this);
				}

				ctx.init(kmf.getKeyManagers(), trustManagers, null);
				ssf = ctx.getServerSocketFactory();	
				
				/*SSLServerSocket socket = (SSLServerSocket)ssf.createServerSocket();
				String enabledProtocols[] = socket.getEnabledProtocols();
				String availableProtocols[] = socket.getSupportedProtocols();
				String availableCiphers[] = socket.getEnabledCipherSuites();
				System.out.println("AvailableProtocols:" + Util.implode(availableProtocols, ","));
				System.out.println("EnabledProtocols  :" + Util.implode(enabledProtocols, ","));
				System.out.println("EnabledCiphers    :" + Util.implode(availableCiphers, ","));
*/
				return ssf;				
			}
			catch (Exception e)
			{
				e.printStackTrace();
				m_pSystem.doError("HTTPServer.SocketFactoryError", new String[]{e.getMessage()}, this);
				throw new Exception(e.toString());
			}
		}
		else
		{
			return ServerSocketFactory.getDefault();
		}
	}


	/**
	 * 
	 * @param sAppGroup
	 * @param sAppName
	 * @param sDesignName
	 * @param iType
	 * @return
	 */
	public DesignElement getDesignElement(String sAppGroup, String sAppName, String sDesignName, int iType)
	{
		TornadoServerInstance tsi = TornadoServer.getInstance();
		return tsi.getDesignElement(sAppGroup, sAppName, sDesignName, iType);
		
	}


	public String getAppParam(SessionContext pSession, String sParamName, String sAppGroup, String sAppName)
	{		
		//return getAppParamFromRDBMS(pSession, sParamName, sGroup, sApplication);
		TornadoServerInstance tsi = TornadoServer.getInstance();
		return tsi.getApplicationParameter(sAppGroup, sAppName, sParamName);
	}
	public String[] getAllDesignElementNames(SystemContext m_pSystem, SessionContext m_pSession, String sAppName, String sAppGroup, int iDesignType)
	{
		TornadoServerInstance tsi = TornadoServer.getInstance();
		return tsi.getAllDesignElementNames(sAppGroup, sAppName, iDesignType);
	}

	/**
	 * Expire entire design cache if older than given age.
	 * Pass 0 to expire everything
	 */
	public static void expireDesignCache(long lAgeInMilliSeconds)
	{
		TornadoServerInstance tsi = TornadoServer.getInstance();
		tsi.expireDesignCache(lAgeInMilliSeconds);
		//m_Parent.m_cacheDesign.expireAll(lAgeInMilliSeconds);
	}

	/**
	 * Expire design cache item if older than given age.
	 * Pass 0 to force expire
	 */
	public static void removeDesignCacheItem(String sKey)
	{
		//m_Parent.removeDesignCacheItem(szKey);
		TornadoServerInstance tsi = TornadoServer.getInstance();
		tsi.removeDesignCacheItem(sKey);
		/*m_pSystem.clearClassLoader(szKey);
    if(szKey==null)
    {
      m_Parent.m_cacheDesign.expireAll(0);
      m_Parent.m_cacheDesign.resetCounters();      
      return;
    }
    m_Parent.m_cacheDesign.removeItem(szKey);
    //clear the app always load params...
    RequestPath rPath = new RequestPath(szKey);
    String sAppPath = rPath.getPathToApplication().toLowerCase();
    m_Parent.m_htAppAlwaysLoad.remove(sAppPath);
		 */
	}

	/*
	public void removeGlobalObject(String szKey)
	{
		//m_Parent.m_cacheObject.removeItem(szKey.toLowerCase());
		m_pSystem.set
	}


	public Object getGlobalObject(String szKey)
	{
		return m_Parent.m_cacheObject.getItem(szKey.toLowerCase());
	}
	 */

	/**
	 *
	 */
	public String[] getCustomHeaderProcessors()
	{
		return m_Parent.getCustomHeaderProcessors();
	}

	public String[] getAllowedHTTPMethods()
	{
		return m_Parent.getAllowedHTTPMethods();
	}
	/**
	 * Stores an object in memory for access by all http users/threads
	 * @return true if the object was stored successfully
	 */
	/*public boolean storeGlobalObject(Object oItem)
	{
		return m_Parent.m_cacheObject.addItem(oItem);
	}*/

	public boolean isDebug()
	{
		return m_Parent.isDebug();
	}

	public String tell(String szCommand)
	{
		return "";
	}



	/**
	 * Allow other tasks to execute console commands
	 * @param szCommand
	 * @return a string containing the results of the command. CRLF breaks each line
	 */
	public String doConsoleCommand(String szCommand)
	{
		return m_Parent.doConsoleCommand(szCommand);
	}

	/**
	 * Determine the name of the class that the SOAP requests should be sent to.
	 * This is the name of a server addin
	 */
	public String getWidgitServer()
	{
		return m_sHTTPSOAPServer;
	}


	/**
	 * Apply the hostmap table
	 * @param szRequestedHost
	 * @return
	 */
	public String getDefaultHostURL(String szRequestedHost)
	{
		return m_Parent.getDefaultHostURL(szRequestedHost);
	}




	/**
	 * Checks whether an application has the named role in its list of roles.
	 * @return true if the role exists, false if it does not.
	 */
	public static boolean appHasRole(SystemContext sysCtx, RequestPath rPath, String sRoleName)
	{
		TornadoServerInstance tsi = TornadoServer.getInstance();
		String sRoles[] = tsi.getApplicationRoles(rPath.Group, rPath.Application);

		for(int i=0; i<sRoles.length; i++)
		{
			String sAppRoleName = sRoles[i]; //(String)vAppRoles.get(i);
			if(sAppRoleName.equals(sRoleName)) return true;
		}
		return false;
	}

	/**
	 * 
	 * @param SysCtx
	 * @param sAppName
	 * @param sAppGroup
	 * @return
	 */
	public boolean isAppDisabled(SystemContext SysCtx, String sAppName, String sAppGroup)
	{
		TornadoServerInstance tsi = TornadoServer.getInstance();
		return tsi.isApplicationDisabled(sAppGroup, sAppName);
	}

	/**
	 *
	 * @param v
	 * @return
	 */
	public static String getCurrentSessionID(String sSessionCookieName, ArrayList v)
	{      
		if(sSessionCookieName==null || sSessionCookieName.length()==0) sSessionCookieName = SESSIONID_LABEL; 
		String szCookie = puakma.util.Util.getMIMELine(v, "Cookie");
		if(szCookie==null) return "";

		szCookie = puakma.util.Util.getMIMELineValue(szCookie, sSessionCookieName);
		if(szCookie==null) return "";

		return szCookie;
	}

	/**
	 * count the number of bytes sent up to the server from a client request
	 */
	public static int getInboundRequestSize(String sRequestLine, ArrayList environment_lines)
	{
		if(sRequestLine==null || environment_lines==null) return 0;

		int iReturn = sRequestLine.length()+2;
		int iPayloadSize=0;

		//count the header size
		for(int i=0; i<environment_lines.size(); i++)
		{
			String szLine = (String)environment_lines.get(i); // .elementAt(i);            
			iReturn += (szLine.length() + 2); //for \r\n
		}
		iReturn += 2; //blank line that seperates the header from the payload
		//now add the data payload
		String szContentLength = puakma.util.Util.getMIMELine(environment_lines, "Content-Length");

		if(szContentLength!=null)
		{
			try{iPayloadSize = Integer.parseInt(szContentLength);} catch(Exception e){}
		}
		iReturn += iPayloadSize;

		return iReturn;
	}

	public boolean isGroupLevelCookie()
	{
		return m_Parent.isGroupLevelCookie();
	}


	/**
	 * list of mimetypes to exclude form logging
	 */
	public ArrayList getMimeExcludes()
	{
		return m_Parent.getMimeExcludes();
	}

	/**
	 * Determines if we have one pool or multiple
	 * @return
	 */
	public boolean usesSingleDBPool()
	{
		return m_Parent.usesSingleDBPool();
	}

	/**
	 * Determines if the output stream should be compressed (where possible)
	 * with gzip
	 */
	public boolean shouldGZipOutput()
	{
		return m_bGZipOutput;
	}

	/**
	 * get the detail about what is being run currently
	 *
	 */
	public String getThreadDetail()
	{
		return m_tpm.getThreadDetail();
	}

	public void killThread(String sThreadID)
	{
		m_tpm.killThread(sThreadID);      
	}

	public int getMaxURI()
	{
		return m_iMaxURI;
	}
	
	public int getSlowActionTimeLimitMS()
	{
		return m_iHTTPSlowActionMS;
	}

	public String getPuakmaFileExt()
	{
		return m_Parent.getPuakmaFileExt();
	}

	public long getMaxUploadBytes()
	{
		return m_Parent.getMaxUploadBytes();
	}

	/**
	 * Return the maximum number seconds (future) that a file system's expiry date can be.
	 * @return
	 */
	public int getMaxExpirySeconds()
	{
		return m_iHTTPMaxExpirySeconds;
	}

	/**
	 * 
	 * @param sStatisticKey
	 * @param dIncrementBy
	 */
	public void incrementStatistic(String sStatisticKey, double dIncrementBy) 
	{
		m_Parent.incrementStatistic(sStatisticKey, dIncrementBy);

	}

	/**
	 * Provides a reference to the underlying addin. Used in the http header processor
	 * @return
	 */
	public pmaAddIn getAddIn() 
	{
		return m_Parent;
	}

	/**
	 * 
	 * @return
	 */
	public boolean canLookupHostName() 
	{
		return m_Parent.canLookupHostName();
	}

	public boolean shouldDisableBasicAuth() 
	{
		// TODO Auto-generated method stub
		return m_bShouldDisableBasicAuth;
	}

	public ArrayList<String> getHttpOptions() 
	{
		return m_Parent.getHttpOptions();
	}

}//class
