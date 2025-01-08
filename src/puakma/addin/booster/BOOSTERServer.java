/***************************************************************************
The contents of this file are subject to the Puakma Public License Version 1.0 
 (the "License"); you may not use this file except in compliance with the 
 License. A copy of the License is available at http://www.puakma.net/

The Original Code is BOOSTERServer. 
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
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.KeyStore;
import java.security.Principal;
import java.text.NumberFormat;
import java.util.ArrayList;

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
import puakma.addin.http.log.HTTPLogEntry;
import puakma.error.ErrorDetect;
import puakma.error.pmaLog;
import puakma.system.SystemContext;
import puakma.system.pmaSystem;
import puakma.system.pmaThreadInterface;
import puakma.system.pmaThreadPoolManager;



public class BOOSTERServer extends Thread implements ErrorDetect
{
	private ServerSocket m_ss;    
	private BOOSTER m_Parent;
	private boolean m_bRunning=false;
	private boolean m_bSSL=false;
	private String m_sInterface=null;
	private boolean m_bTrustStoreEnabled=false;
	private long m_lTextPagesServed=0;

	//private boolean stop_accepting = false;
	private long request_id = 0;
	private int max_pooled_threads = 200;
	private int min_pooled_threads = 20;
	private int thread_pool_timeout = 5000;
	private pmaThreadPoolManager m_tpm;

	//public String instance_name;          // set below
	//private Thread current;                // set below
	public int iHTTPPortTimeout=30000; //30 seconds
	private int iHTTPPort=80;    
	private pmaAddInStatusLine pStatus;
	public SystemContext m_pSystem;
	private double m_dblBytesServed=0;

	private String m_sHTTPSSLKeyRing="";
	private String m_sHTTPSSLKeyRingPW="";

	private String m_sHTTPSSLTrustStore="";
	private String m_sHTTPSSLTrustStorePW="";

	private double m_dBytesRaw=0;
	private double m_dBytesCompressed=0;
	private double m_dBytesSentTotal=0;
	private NumberFormat m_nfDecimal;
	//private Cache m_cache = null;
	private String m_sHTTPSSLContext = "";

	public BOOSTERServer(SystemContext paramCtx, int iPort, BOOSTER paramParent, boolean paramSSL)
	{
		m_nfDecimal = NumberFormat.getInstance();
		m_nfDecimal.setMaximumFractionDigits(2);
		m_nfDecimal.setMinimumFractionDigits(2);
		m_bSSL = paramSSL;
		m_Parent = paramParent;
		m_pSystem = (SystemContext)paramCtx.clone();
		iHTTPPort = iPort;
		setDaemon(true);
	}

	/**
	 * This method is called by the pmaServer object
	 */
	public void run()
	{
		m_bRunning = true;
		

		m_sHTTPSSLKeyRing = m_pSystem.getSystemProperty("BOOSTERSSLKeyRing");
		m_sHTTPSSLKeyRingPW = m_pSystem.getSystemProperty("BOOSTERSSLKeyRingPW");
		if(m_sHTTPSSLKeyRingPW==null) m_sHTTPSSLKeyRingPW="";

		m_sHTTPSSLTrustStore = m_pSystem.getSystemProperty("BOOSTERSSLTrustStore");
		m_sHTTPSSLTrustStorePW = m_pSystem.getSystemProperty("BOOSTERSSLTrustStorePW");
		if(m_sHTTPSSLTrustStorePW==null) m_sHTTPSSLTrustStorePW="";

		m_sHTTPSSLContext = m_pSystem.getSystemProperty("BOOSTERSSLContext");
		if(m_sHTTPSSLContext==null || m_sHTTPSSLContext.length()==0) m_sHTTPSSLContext = "SSL";

		pStatus = m_Parent.createStatusLine(" " + getErrorSource());
		pStatus.setStatus("Starting...");
		
		InetAddress cInterface=null;
		m_sInterface = m_pSystem.getSystemProperty("BOOSTERInterface");
		if(m_sInterface!=null)
		{
			try
			{
				cInterface = InetAddress.getByName(m_sInterface);
			}
			catch(Exception e){}
		}

		/*String sTemp = m_pSystem.getSystemProperty("BOOSTERUseSharedCache");
      if(sTemp!=null && !sTemp.equals("1"))
      {
          sTemp = m_pSystem.getSystemProperty("BOOSTERServerCacheMB");
          long lCacheSize = 0;
          if(sTemp!=null && sTemp.length()>0) try{ lCacheSize = Long.parseLong(sTemp); }catch(Exception y){}
          if(lCacheSize>0) m_cache = new Cache(lCacheSize*1024*1024);
      }*/

		// startup the
		try{max_pooled_threads = Integer.parseInt(m_pSystem.getSystemProperty("BOOSTERMaxThreads"));}catch(Exception e1){}
		try{min_pooled_threads = Integer.parseInt(m_pSystem.getSystemProperty("BOOSTERMinThreads"));}catch(Exception e2){}
		try{thread_pool_timeout = Integer.parseInt(m_pSystem.getSystemProperty("BOOSTERThreadCreateTimeout"));}catch(Exception e3){}
		m_tpm = new pmaThreadPoolManager(m_pSystem, min_pooled_threads, max_pooled_threads, thread_pool_timeout, "boost");
		m_tpm.start();

		// prepare server socket for accept
		try{ iHTTPPortTimeout = Integer.parseInt(m_pSystem.getSystemProperty("BOOSTERPortTimeout")); }catch(Exception e1){}
		int iHTTPPortBacklog = 20;
		try{ iHTTPPortBacklog = Integer.parseInt(m_pSystem.getSystemProperty("BOOSTERPortBacklog")); }catch(Exception e1){}

		try
		{
			if(m_bSSL)
			{
				ServerSocketFactory ssf = getServerSocketFactory(m_bSSL);
				if(cInterface==null)
				{
					if(ssf!=null) m_ss = ssf.createServerSocket(iHTTPPort, iHTTPPortBacklog);
				}
				else
				{
					if(ssf!=null) m_ss = ssf.createServerSocket(iHTTPPort, iHTTPPortBacklog, cInterface);
				}
			}
			else
			{
				if(cInterface==null)
					m_ss = new ServerSocket (iHTTPPort, iHTTPPortBacklog);
				else
					m_ss = new ServerSocket (iHTTPPort, iHTTPPortBacklog, cInterface);
			}        
		}
		catch(Exception ioe)
		{
			m_pSystem.doError("BOOSTERServer.NoPortOpen", new String[]{String.valueOf(iHTTPPort)}, this);
			if(m_sInterface!=null) m_pSystem.doError("BOOSTERServer.InterfaceError", new String[]{m_sInterface}, this);
			shutdownAll();
			m_Parent.removeStatusLine(pStatus);
			return;
		}

		m_pSystem.doInformation("BOOSTERServer.Listening", new String[]{String.valueOf(iHTTPPort)}, this);

		// main loop
		while(m_bRunning)
		{

			try
			{
				updateStatusLine();
				Socket sock = m_ss.accept();
				request_id++;
				//System.out.println(request_id + " handoff");
				//String sNextHost = getNextAvailableHost();
				dispatch(sock);        
			}
			catch(Exception ioe)
			{
				m_pSystem.doError("BOOSTERServer.AcceptFail", new String[]{ioe.getMessage()}, this);
			}
		}//end while
		m_tpm.requestQuit();
		pStatus.setStatus("Shutting down");
		m_pSystem.doInformation("BOOSTERServer.ShuttingDown", this);
		try
		{      
			m_ss.close();
		}
		catch (IOException ioe)
		{
			m_pSystem.doError("BOOSTERServer.SocketCloseFail", this);
		}
		m_pSystem.doInformation("BOOSTERServer.SocketClosed", this);

		shutdownAll();
		m_Parent.removeStatusLine(pStatus);
		//m_pSystem.clearDBPoolManager();
	}

	/**
	 *
	 */
	public void checkAvailability()
	{
		m_Parent.checkAvailability();      
	}

	/**
	 *
	 */
	public String[] getReplaceHosts()
	{
		return m_Parent.getReplaceHosts();
	}

	public int getMaxURI()
	{
		return m_Parent.getMaxURI();
	}

	/**
	 * get the size of the largest object we should try to gzip
	 */
	public int getMaxgzipBytes()
	{
		return m_Parent.getMaxgzipBytes();
	}

	/**
	 * Get the maximum amount of time (in minutes) an object can live in the cache
	 */
	public int getMaxCacheMinutes()
	{
		return m_Parent.getMaxCacheMinutes();
	}

	public int getMinCacheSeconds()
	{
		return m_Parent.getMinCacheSeconds();
	}

	public boolean shouldRemoveCacheInfo()
	{
		return m_Parent.shouldRemoveCacheInfo();
	}

	public boolean shouldUseStickySessions()
	{
		return m_Parent.shouldUseStickySessions();
	}

	/**
	 * get the size of the largest object we should try to add to the cache. Returns -1 for no size
	 * limit
	 */
	public int getMaxCacheableObjectBytes()
	{
		return m_Parent.getMaxCacheableObjectBytes();
	}

	/**
	 *
	 */
	public boolean getForceSSL(String sDomain)
	{
		return m_Parent.getForceSSL(sDomain);
	}

	public boolean shouldForceClientSSL(String sRequestedHost)
	{
		return m_Parent.shouldForceClientSSL(sRequestedHost);
	}

	private void updateStatusLine()
	{
		if(m_sInterface==null)
			pStatus.setStatus("Listening. hits:" + request_id + " avg:" + (long)m_tpm.getAverageExecutionTime() + "ms ");
		else
			pStatus.setStatus("Listening " + m_sInterface + ". hits:" + request_id + " avg:" + (long)m_tpm.getAverageExecutionTime() + "ms " + (long)(m_dblBytesServed/1024) + "Kb");
	}

	/**
	 *
	 */
	public String getStatus()
	{
		String CRLF = "\r\n";
		StringBuilder sb = new StringBuilder(128);

		sb.append(" --- BOOSTER on port: " + m_ss.getLocalPort() + " ---" + CRLF);
		sb.append(" Hits: " + request_id + CRLF);
		sb.append(" Avg connection time: " + (long)m_tpm.getAverageExecutionTime() + "ms" + CRLF);
		sb.append(" Total Bytes sent: " + (long)m_dBytesSentTotal + CRLF);      
		if(m_dBytesCompressed>0)
		{
			sb.append(" Raw Bytes: " + (long)m_dBytesRaw + CRLF);
			sb.append(" Compressed bytes: " + (long)m_dBytesCompressed + CRLF);
			sb.append(" Compression ratio: " + (int)((m_dBytesCompressed/m_dBytesRaw)*100) + "% of original size" + CRLF);
		}   

		return sb.toString();
	}



	public boolean shouldGZipOutput(String sURI, String sContentType)
	{        
		return m_Parent.shouldGZipOutput(sURI, sContentType);

	}

	public boolean shouldCacheOutput(String sURI, String sContentType)
	{        
		return m_Parent.shouldCacheOutput(sURI, sContentType);
	}

	public boolean shouldFixContentTypes()
	{
		return m_Parent.shouldFixContentTypes();
	}

	public String getVersionNumber()
	{
		return m_Parent.getVersionNumber();
	}


	/**
	 * Gets the next host form the available list
	 */
	public synchronized String getNextAvailableHost(String sRequestedHost)
	{
		return m_Parent.getNextAvailableHost(sRequestedHost);
	}

	/**
	 * Return the number of available hosts
	 */
	public int getAvailableHostCount(String sRequestedHost)
	{
		return m_Parent.getAvailableHostCount(sRequestedHost);
	}

	/**
	 * Returns a list of all the configured cluster members
	 */
	public String[] getAllServerNodes(String sRequestedHost)
	{
		return m_Parent.getAllServerNodes(sRequestedHost);
	}

	/**
	 * Called to request the server to stop
	 */
	public void requestQuit()
	{
		if(m_ss!=null)
		{
			m_bRunning = false;
			try{m_ss.close();}catch(Exception e){}
		}
		//System.out.println("requestQuit() "+ this.getErrorSource());
		//tpm.destroy();
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
			BOOSTERRequestManager rm = new BOOSTERRequestManager(m_pSystem, this, sock, request_id, m_bSSL);
			//System.out.println("****** REQUEST " + request_id + " " + " ******");
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
					// TODO Auto-generated catch block
					e.printStackTrace();
					return;
				}
			}

			bOK=m_tpm.runThread((pmaThreadInterface)rm);
		}
		else
		{
			puakma.util.AntiHackWorker ahw = new puakma.util.AntiHackWorker(m_pSystem, sock);
			bOK=m_tpm.runThread((pmaThreadInterface)ahw);
		}

		if(!bOK)
		{
			m_pSystem.doError("BOOSTERServer.RequestThreadError", this);
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
	 * Close all active request manager threads
	 */
	private void shutdownAll()
	{
		m_pSystem.doDebug(pmaLog.DEBUGLEVEL_FULL, "shutdownAll()", this);
		// wait for all active rms to end (thus returning their
		// db connections to the internal dbcp
		int loopcount = 0;
		int active_count = 0;
		int iShutDownSeconds = 3; 

		try{iShutDownSeconds = Integer.parseInt(m_pSystem.getSystemProperty("BOOSTERShutdownWaitSeconds"));}catch(Exception r){}

		//tpm.destroy();
		while((active_count = m_tpm.getActiveThreadCount()) > 0 && loopcount < iShutDownSeconds)
		{
			m_pSystem.doInformation("BOOSTERServer.ShutdownPending", new String[]{String.valueOf(active_count)}, this);
			loopcount++;
			try
			{
				Thread.sleep (1000);
			}
			catch (InterruptedException ie) {}
		}//end while

		// not all threads have quit cleanly!
		if (active_count > 0)
			m_pSystem.doInformation("BOOSTERServer.ThreadsActive", new String[]{String.valueOf(active_count)}, this);

		m_pSystem.doInformation("BOOSTERServer.Shutdown", this);
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
		if(m_bSSL) sSSL=m_sHTTPSSLContext; //" SSL";
		return "BOOSTERServer(" + iHTTPPort + sSSL + ")" ;
	}

	public String getErrorUser()
	{
		return pmaSystem.SYSTEM_ACCOUNT;
	}


	/**
	 * Determine the type of socket to open
	 */
	private ServerSocketFactory getServerSocketFactory(boolean bGetSSL) throws Exception
	{
		if(bGetSSL)
		{
			SSLServerSocketFactory ssf = null;
			try
			{
				// set up key manager to do server authentication				
				char[] cKeyStorePassphrase = m_sHTTPSSLKeyRingPW.toCharArray(); //password to access keystore

				SSLContext ctx = SSLContext.getInstance(m_sHTTPSSLContext );//"TLSv1.2"); //TLS | SSL
				
				KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
				KeyStore ks = KeyStore.getInstance("JKS");
				ks.load(new FileInputStream(m_sHTTPSSLKeyRing), cKeyStorePassphrase);
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
				return ssf;
				/*else //plain SSL
				{					
					//System.out.println("m_sHTTPSSLKeyRingPW=["+m_sHTTPSSLKeyRingPW+"]");

					SSLContext ctx = SSLContext.getInstance("SSL"); //TLS //SSLv3
					KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
					KeyStore ks = KeyStore.getInstance("JKS");

					ks.load(new FileInputStream(m_sHTTPSSLKeyRing), cKeyStorePassphrase);
					kmf.init(ks, cKeyStorePassphrase);
					ctx.init(kmf.getKeyManagers(), null, null);

					ssf = ctx.getServerSocketFactory();          
					return ssf;
				}*/


			}
			catch (Exception e)
			{			
				e.printStackTrace();
				m_pSystem.doError("BOOSTERServer.SocketFactoryError", new String[]{e.getMessage()}, this);
				throw new Exception(e.toString());
			}
		}
		else
		{
			return ServerSocketFactory.getDefault();
		}
	}

	public boolean allowReverseDNS()
	{
		return m_Parent.allowReverseDNS();
	}

	public boolean isDebug()
	{
		return m_Parent.isDebug();
	}

	public boolean isDebugHeaders()
	{
		return m_Parent.isDebugHeaders();
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

	public File getUnavailableFile()
	{
		return m_Parent.getUnavailableFile();
	}

	public String getFileMimeType(String sRequest)
	{
		return m_Parent.getFileMimeType(sRequest);
	}

	/**
	 *
	 */
	public synchronized void updateCompressionStats(double dblRaw, double dblCompressed)
	{
		m_dBytesRaw += dblRaw;
		m_dBytesCompressed += dblCompressed;
	}

	public void updateBytesSent(double dblSent)
	{
		m_dBytesSentTotal += dblSent;
	}

	public boolean shouldUseRealHostName()
	{
		return m_Parent.shouldUseRealHostName();
	}

	/**
	 *
	 */
	public String[] getCustomHeaderProcessors()
	{
		return m_Parent.getCustomHeaderProcessors();
	}

	/**
	 * Writes lines to log web request comperssion stats
	 */
	public void writeCompressionStatLog(String sURI, int iOrigSize, int iNewSize, int iWebServerTime, int iTotalTime, String sContentType)
	{
		m_Parent.writeCompressionStatLog(sURI, iOrigSize, iNewSize, iWebServerTime, iTotalTime, sContentType);
	}

	public synchronized void updateTextPageServeCount()
	{
		m_lTextPagesServed++;
	}

	public long getTextPageServeCount()
	{
		return m_lTextPagesServed;
	}

	public String formatNumber(double dblNumber)
	{
		return m_nfDecimal.format(dblNumber);
	}

	/**
	 * Writes lines to log web requests
	 */
	public void writeTextStatLog(HTTPLogEntry stat)
	{
		m_Parent.writeTextStatLog(stat);
	}

	/**
	 * list of mimetypes to exclude form logging
	 */
	public ArrayList<String> getMimeExcludes()
	{
		return m_Parent.getMimeExcludes();
	}

	/**
	 * Gets and item from the cache. If the cache for this listener is null,
	 * then it will be fetched from the parent shared cache.
	 */
	public BOOSTERCacheItem getCacheItem(String sKey)
	{
		return m_Parent.getCacheItem(sKey);      
	}

	/**
	 *
	 */
	public boolean addToCache(BOOSTERCacheItem item)
	{
		return m_Parent.addToCache(item);      
	}

	public boolean isSharedCache()
	{
		return m_Parent.isSharedCache();
	}

	public int getDefaultSocketTimeout()
	{
		return m_Parent.getDefaultSocketTimeout();
	}

	public String getPublicDir()
	{
		return m_Parent.getPublicDir();
	}

	/**
	 * Determine if this software has the appropriate keys
	 */
	public boolean isLicensed()
	{
		return m_Parent.isLicensed();
	}

	/**
	 * Return the largest content-length this server will accept. -1 is unlimited
	 * @return
	 */
	public long getMaxUploadBytes()
	{
		return m_Parent.getMaxUploadBytes();
	}

	public pmaAddIn getAddIn() 
	{
		return m_Parent;
	}


}//class
