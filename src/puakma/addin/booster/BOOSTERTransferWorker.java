/***************************************************************************
The contents of this file are subject to the Puakma Public License Version 1.0 
 (the "License"); you may not use this file except in compliance with the 
 License. A copy of the License is available at http://www.puakma.net/

The Original Code is BOOSTERTransferWorker.
The Initial Developer of the Original Code is Brendon Upson. email: bupson@wnc.net.au 
Portions created by Brendon Upson are Copyright (C)2002. All Rights Reserved.

webWise Network Consultants Pty Ltd, Australia, http://www.wnc.net.au

Contributor(s) and Changelog:
-
-
 ***************************************************************************/

package puakma.addin.booster;


import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Locale;
import java.util.TimeZone;

import puakma.addin.http.HTTPServer;
import puakma.addin.http.log.HTTPLogEntry;
import puakma.error.pmaLog;
import puakma.system.RequestPath;
import puakma.system.SessionContext;
import puakma.util.ByteStreamReader;
import puakma.util.HTTPHeaderProcessor;
import puakma.util.Util;

/**
 *
 * @author  bupson
 */
public class BOOSTERTransferWorker 
{
	private static int UNKNOWN_CONTENT_LENGTH=-1;
	private static int CONTENT_ERROR=-99;
	private int MAXCHUNK = 65536; //64K - too big?
	private int MAX_GZIP_SIZE = 512000; //half meg
	private int MAX_CACHEABLEOBJECT_SIZE_BYTES = 512000; //half meg
	private long MAX_CACHE_MILLISECONDS = 86400000;//1440*60*1000;
	private long MIN_CACHE_MILLISECONDS = -1;
	//private BufferedReader m_Clientis=null;
	private ByteStreamReader m_Clientis=null;
	private BufferedOutputStream m_Clientos=null;
	private BufferedReader m_Serveris=null;
	private BufferedOutputStream m_Serveros=null;
	private BOOSTERRequestManager m_Parent;
	private boolean m_bRunning=true;
	private static String HTTP_NEWLINE = "\r\n";
	public static final String LAST_MOD_DATE = "EEE, dd MMM yyyy HH:mm:ss z";
	private TimeZone m_tzGMT = TimeZone.getTimeZone("GMT");    
	private boolean m_bNoServerConnect=false;
	private String[] m_sReplacementHosts=null;
	private boolean m_bDieAfterNext=false;
	private long m_lRequestID;
	private long m_lWebServerTime=0;
	private long m_lTotalReplyTime=0;
	private long m_lTotalTime=0;

	private String m_sRequestedHost;
	private String m_sRequestLine;
	private String m_sMethod;
	private String m_sURI;
	private String m_sHTTPVersionClient;
	//private String m_sHTTPVersionServer;    

	private int m_iInboundSize;
	private String m_sVia="";
	private boolean m_bRemoveCacheInfo=false;
	private boolean m_bAdjustContentTypes=false;

	//for session handling
	private boolean m_bSendSessionCookie = false;
	private boolean m_bCanKeepAlive = true;



	/** Creates a new instance of BOOSTERTransferThread */
	public BOOSTERTransferWorker(BOOSTERRequestManager rm, InputStream is, OutputStream os, long lRequestID) throws Exception
	{
		//if(is!=null) m_Clientis = new BufferedReader(new InputStreamReader(is, "ISO-8859-1"), 24576);
		if(is!=null) m_Clientis = new ByteStreamReader(is, 24576, "ISO-8859-1");
		if(os!=null) m_Clientos = new BufferedOutputStream(os, 2048);
		m_Parent = rm;        

		m_lRequestID = lRequestID;
		m_sVia = "Via: Puakma/WEB-BOOSTER-"+m_Parent.getBuildNumber();

		MAX_GZIP_SIZE = m_Parent.getMaxgzipBytes();
		MAX_CACHEABLEOBJECT_SIZE_BYTES = m_Parent.getMaxCacheableObjectBytes();
		MAX_CACHE_MILLISECONDS = m_Parent.getMaxCacheMinutes()*60*1000;
		MIN_CACHE_MILLISECONDS = m_Parent.getMinCacheSeconds()*1000;
		m_bRemoveCacheInfo = m_Parent.shouldRemoveCacheInfo();
		m_bAdjustContentTypes = m_Parent.shouldFixContentTypes();
		m_sReplacementHosts = m_Parent.getReplaceHosts();
	}


	/**
	 *
	 */
	public void run()
	{
		ArrayList environment_lines = new ArrayList();
		boolean bFirstTime = true;        
		while(m_bRunning)
		{
			try
			{       
				m_Parent.getAddIn().incrementStatistic(BOOSTER.STATISTIC_KEY_HITSPERHOUR, 1);
				m_Parent.getAddIn().incrementStatistic(BOOSTER.STATISTIC_KEY_HITSPERMINUTE, 1);
				m_Parent.getAddIn().incrementStatistic(BOOSTER.STATISTIC_KEY_TOTALHITS, 1);

				environment_lines.clear();
				m_lTotalTime=System.currentTimeMillis();
				//readclient
				if(m_Parent.isDebug()) m_Parent.m_pSystem.doDebug(pmaLog.DEBUGLEVEL_NONE, this.m_lRequestID+" Reading client headers", m_Parent);                
				int iContentLength = readHeaders(environment_lines, true); //read client headers
				long lMaxUpload = m_Parent.getMaxUploadBytes();
				if(lMaxUpload>=0 && iContentLength>lMaxUpload)
				{
					String sMessage = pmaLog.parseMessage(m_Parent.m_pSystem.getSystemMessageString("BOOSTER.ContentLengthLimit"), new String[]{String.valueOf(iContentLength), String.valueOf(lMaxUpload), m_sURI });
					m_Parent.m_pSystem.doError(sMessage, m_Parent);
					m_bRunning = false; //drop connection
					sendHTTPResponse(414, sMessage, null, "text/html", Util.utf8FromString(sMessage), true);
					continue;        			
				}

				String sLowURI = "";
				if(m_sURI!=null) sLowURI = m_sURI.toLowerCase();
				//look for "://" ? this should pick up ftp:// etc 
				if(sLowURI.indexOf("://")>0)
				{                    
					/*m_Parent.m_pSystem.doDebug(pmaLog.DEBUGLEVEL_NONE, this.m_lRequestID+" PROXY: "+m_sRequestLine, m_Parent);
                    if(m_Parent.allowRegularProxy())
                    {
                        m_Parent.m_pSystem.doInformation("PROXY: " + m_sRequestLine, m_Parent);
                        //check for CONNECT (ssl passthru)
                    }
                    else
                    {
                        m_Parent.m_pSystem.doError("BOOSTER.NoRegularProxyAllowed", m_Parent);
                        m_bDieAfterNext = true;
                        sendHTTPResponse(400, "Bad Request", null, null, null, true);
                    }*/
					m_Parent.m_pSystem.doError("BOOSTER.NoRegularProxyAllowed", new String[]{m_sRequestLine}, m_Parent);
					m_bDieAfterNext = true;
					sendHTTPResponse(400, "Bad Request", null, null, null, true);
				}
				else //reverse proxy
				{
					if(m_sRequestedHost==null) m_sRequestedHost = Util.getMIMELine(environment_lines, "Host");
					checkForForceSSL(environment_lines);
					m_Parent.updateStatus(m_sRequestedHost +m_sURI);
					String sAcceptEncoding = Util.getMIMELine(environment_lines, "Accept-Encoding");
					//check each time in case the header changes between requests
					if(sAcceptEncoding!=null && sAcceptEncoding.indexOf("gzip")>=0) 
						m_Parent.setGZIPCapable(true);
					else
						m_Parent.setGZIPCapable(false);
					String sKey = m_sURI;
					if(m_sRequestedHost!=null && !m_Parent.isSharedCache()) 
						sKey = m_sRequestedHost+m_sURI;

					//System.out.println( "***** "+this.m_lRequestID+" LOOKIN FOR: " + sKey);
					BOOSTERCacheItem item = m_Parent.getCacheItem(sKey);
					if(item!=null && !m_bNoServerConnect && m_sMethod.equals("GET"))
					{
						//determine if the content should be compressed....
						boolean bShouldCompress = false;
						//long lStart = System.currentTimeMillis();

						//System.out.println( "***** "+this.m_lRequestID+" FOUND IN CACHE: " + m_sRequestedHost+m_sURI);
						if(item.hasChanged(environment_lines))
						{
							String sContentType = item.getContentType();                        
							bShouldCompress = m_Parent.shouldGZipOutput(m_sURI, sContentType);
							ArrayList out_lines = item.getResponseHeaders();
							Util.replaceHeaderValue(out_lines, "Host", m_sRequestedHost);
							m_Clientos.write(item.getHTTPReply(bShouldCompress, m_sVia, m_bCanKeepAlive));
							m_Clientos.flush();

							//out_lines.add("Host: "+m_sRequestedHost);
							if(m_Parent.isDebug()) m_Parent.m_pSystem.doDebug(pmaLog.DEBUGLEVEL_NONE, this.m_lRequestID+" Serving from cache: "+m_sURI, m_Parent);
							HTTPLogEntry stat = new HTTPLogEntry(m_Parent.getMimeExcludes(), environment_lines, out_lines, m_sRequestLine, sContentType, iContentLength, (long)m_iInboundSize, 200, m_Parent.getClientIPAddress(), m_Parent.getClientHostName(), m_Parent.m_pSystem.getSystemHostName(), m_sRequestedHost, m_lTotalTime, m_Parent.getBoosterIPAddress(), m_Parent.getServerPort(), null); 

							m_Parent.writeTextStatLog(stat);							
						}
						else
						{														
							sendNotChanged(environment_lines, m_bCanKeepAlive);								
						}
						//long lDiff = System.currentTimeMillis() - lStart;
						//System.out.println("Cache serve " + m_sURI + " took " + lDiff+"ms");
						m_Parent.getAddIn().incrementStatistic(BOOSTER.STATISTIC_KEY_CACHEHITSPERHOUR, 1);
					}
					else //not in cache
					{
						m_Parent.getAddIn().incrementStatistic(BOOSTER.STATISTIC_KEY_CACHEMISSESPERHOUR, 1);
						if(iContentLength==CONTENT_ERROR) break;
						if(!m_bNoServerConnect) //header processor is not doing the work
						{ 
							//check if we are asking for a local file
							String sPublicDir = m_Parent.getPublicDir();
							if(sPublicDir!=null && sPublicDir.length()>0)
							{
								RequestPath rPath = new RequestPath(m_sURI);
								String sFullRequestedPath = sPublicDir + puakma.util.Util.enforceDocumentRoot(rPath.getPathToApplication());
								File fLocal = new File(sFullRequestedPath);
								if(fLocal.exists() && fLocal.isFile() && !fLocal.isHidden())
								{                                
									serveLocalFile(fLocal, null);
									continue; //go back to the start
								}
							}


							//send to server
							if(bFirstTime)
							{
								bFirstTime = false;
								m_sRequestedHost = Util.getMIMELine(environment_lines, "Host");
								String sProxyRef = m_sRequestedHost;
								m_Parent.setProxyReference(sProxyRef);
								m_Parent.setupHost(sProxyRef);
								if(!m_Parent.isServerReady())
								{
									sendNoHostAvailableMessage();
									m_bRunning=false;
									break;
								}
								//String sAcceptEncoding = Util.getMIMELine(environment_lines, "Accept-Encoding");
								//if(sAcceptEncoding!=null && sAcceptEncoding.indexOf("gzip")>=0) m_Parent.setGZIPCapable(true);
							}
							m_iInboundSize = HTTPServer.getInboundRequestSize(m_sRequestLine, environment_lines);
							//m_sUserName = determineUserName(environment_lines); 
							//m_sUserAgent = Util.getMIMELine(environment_lines, "User-Agent");
							//m_sReferer = Util.getMIMELine(environment_lines, "Referer");

							if(m_Parent.shouldUseRealHostName())
							{                        
								String sHost = m_Parent.getServerName();
								if(m_Parent.isDebug()) m_Parent.m_pSystem.doDebug(pmaLog.DEBUGLEVEL_NONE, this.m_lRequestID + " Replacing host with: " + sHost, m_Parent);
								if(sHost!=null) 
								{
									Util.replaceHeaderValue(environment_lines, "Host", sHost);
									String sReferer = Util.getMIMELine(environment_lines, "Referer");
									if(sReferer!=null && sReferer.length()>0)
									{
										RequestPath r = new RequestPath(sReferer);
										//only change references to this host
										if(m_sRequestedHost.equalsIgnoreCase(r.Host)) //so if referer is say, google.com, we don't falsely change the header
										{
											r.Host = sHost;	
											r.setUseTornadoEscaping(false);
											Util.replaceHeaderValue(environment_lines, "Referer", r.getURL());
										}
										//System.out.println("Referer is now: [" + r.getURL() + "] was [" + sReferer + "]");
									}//if referer
								}

							}

							if(environment_lines.size()>0) environment_lines.add("X-Forwarded-For: "+ m_Parent.getClientIPAddress()); 
							String sContentType = Util.getMIMELine(environment_lines, "Content-Type"); 
							addSessionIDToHeaders(environment_lines);
							//send to server                  
							processDataPackage(iContentLength, environment_lines, sContentType, true);  
							m_lWebServerTime=System.currentTimeMillis();
							m_lTotalReplyTime=System.currentTimeMillis();



							//read server's reply
							//TODO 411 reply if http1.1 and no content length specified.
							if(m_Parent.isDebug()) m_Parent.m_pSystem.doDebug(pmaLog.DEBUGLEVEL_NONE, this.m_lRequestID+" Reading server headers", m_Parent);
							iContentLength = readHeaders(environment_lines, false);
							m_lWebServerTime=System.currentTimeMillis()-m_lWebServerTime;
							sContentType = Util.getMIMELine(environment_lines, "Content-Type"); 
							if(sContentType!=null && sContentType.toLowerCase().startsWith("text/")) m_Parent.updateTextPageServeCount();
							//send to client
							int iReplyCode = getReplyCode(environment_lines);                       
							if(iReplyCode>=300 && iReplyCode<400)
							{                                       
								replaceLocationHeader(environment_lines);
							}                    
							if(environment_lines.size()>0) 
								environment_lines.add(m_sVia);
							else
								break; //no headers....
							//if eval version and request %20 show an eval page
							/*if(m_Parent.m_pSystem.isEvaluationVersion() && m_Parent.getTextPageServeCount()%20==0) 
                            {
                                doEvalMessagePage();
                                m_bRunning=false;
                                break;
                            }*/
							processDataPackage(iContentLength, environment_lines, sContentType, false);                    
						} //if !NoServerConnect
					}//not found in cache
				}//else reverse proxy
				if(m_bDieAfterNext) m_bRunning = false;
			} 
			catch(SocketTimeoutException ste)
			{
				m_bRunning=false;
				if(m_Parent.isDebug())                
					m_Parent.m_pSystem.doError(this.m_lRequestID+" SocketTimeout: "+ste.toString(), m_Parent);
			}
			catch(SocketException se)
			{
				m_bRunning=false;
				if(m_Parent.isDebug())                
					m_Parent.m_pSystem.doError(this.m_lRequestID+" "+se.toString(), m_Parent);
			}
			catch(Exception e)
			{
				m_bRunning=false;
				//if(m_Parent.isDebug())
				//{
				m_Parent.m_pSystem.doError("BOOSTERTransferWorker run(): "+e.toString(), m_Parent);
				puakma.util.Util.logStackTrace(e, m_Parent.m_pSystem, 999);
				//System.out.println(this.getName() + " ****************** " + e.toString());
				//e.printStackTrace();
				//}
			}             
		}//while
		//if(m_Parent.isDebug()) m_Parent.m_pSystem.doDebug(pmaLog.DEBUGLEVEL_NONE, this.getName()+": Complete", m_Parent);
		//System.out.println(this.getName() + " finished"); 
		//m_Parent.endRequest();
		try
		{
			if(m_Clientis!=null) m_Clientis.close();
			if(m_Clientos!=null) m_Clientos.close();
			if(m_Serveris!=null) m_Serveris.close();
			if(m_Serveros!=null) m_Serveros.close();
		}
		catch(Exception r){}        
	}

	/**
	 * 
	 * @param environment_lines
	 */
	private void addSessionIDToHeaders(ArrayList environment_lines) 
	{
		//if we have a principal we want to send the session on.
		if(m_Parent.m_session==null || m_Parent.getPrincipal()==null) return;

		/*
		 * Cookie: __utma=153305656.818921236.1413517754.1413517754.1413517754.1; __utmz=153305656.1413517754.1.1.utmcsr=(direct)|utmccn=(direct)|utmcmd=(none); _booster_sid=1-1494450F96B-149445190DB; _pma_sess_id=DD-14944331763-14944519711; LtpaToken=AAECAzU0NEFENTY2NTQ0QjI5QzZBbm9ueW1vdXPclYJvaYjyjqlR0ujm08/4wXezxw==
		 * cookie *should* all be on one line
		 */
		//dumpEnv(environment_lines);

		String sSessionID = HTTPServer.getCurrentSessionID(null, environment_lines);
		//System.out.println("CURRENT SESSIONID: " + sSessionID);
		if(sSessionID==null || sSessionID.length()==0 || !sSessionID.equals(m_Parent.m_session.getSessionID()))
		{			
			//String sCookie = m_Parent.m_session.getCookieString(HTTPServer.SESSIONID_LABEL, "/", null);
			//String sCookieLine = Util.getMIMELine(environment_lines, "Cookie");
			replaceCookieValue(environment_lines, HTTPServer.SESSIONID_LABEL, m_Parent.m_session.getSessionID());
			//System.out.println("EXISTING COOKIELINE: " + sCookieLine);
			//System.out.println("ADD COOKIE: " + sCookie);
			//dumpEnv(environment_lines);
		}



	}

	public static void main(String args[])
	{
		ArrayList arr = new ArrayList();
		//String sLine = "Cookie: __utma=153305656.818921236.1413517754.1413517754.1413517754.1; __utmz=153305656.1413517754.1.1.utmcsr=(direct)|utmccn=(direct)|utmcmd=(none); _booster_sid=1-1494450F96B-149445190DB; _pma_sess_id=DD-14944331763-14944519711; LtpaToken=AAECAzU0NEFENTY2NTQ0QjI5QzZBbm9ueW1vdXPclYJvaYjyjqlR0ujm08/4wXezxw==";
		String sLine = "Cookie: __utma=153305656.818921236.1413517754.1413517754.1413517754.1; __utmz=153305656.1413517754.1.1.utmcsr=(direct)|utmccn=(direct)|utmcmd=(none); _booster_sid=1-1494450F96B-149445190DB; _xpma_sess_id=DD-14944331763-14944519711; LtpaToken=AAECAzU0NEFENTY2NTQ0QjI5QzZBbm9ueW1vdXPclYJvaYjyjqlR0ujm08/4wXezxw==";
		//String sLine = "Cookie: __utma=153305656.818921236.1413517754.1413517754.1413517754.1; __utmz=153305656.1413517754.1.1.utmcsr=(direct)|utmccn=(direct)|utmcmd=(none); _booster_sid=1-1494450F96B-149445190DB; _pma_sess_id=DD-14944331763-14944519711";
		arr.add("Date: x");
		arr.add("Content-Type: text/plain");
		arr.add(sLine);
		arr.add("Server: x");

		replaceCookieValue(arr, "_pma_sess_id", "fred");
		//replaceCookieValue(arr, "_pma_sess_id", null);
		
		dumpEnv(arr);
	}

	private static void replaceCookieValue(ArrayList lines, String sCookieName, String sValue) 
	{		
		boolean bFound = false;
		for(int i=0; i<lines.size(); i++)
		{
			String sMimeLine = (String)lines.get(i);
			if(sMimeLine.startsWith("Cookie: "))
			{
				bFound = true;
				if(sCookieName==null) //if name is null, remove all
				{
					lines.remove(i);
					return;
				}

				String sNewPair = sCookieName+'=' + sValue;

				int iPos = sMimeLine.indexOf(sCookieName+"=");
				if(iPos<1) 
				{
					lines.set(i, sMimeLine + "; " + sNewPair);
					return; //not found					
				}

				String sStart = sMimeLine.substring(0, iPos);
				String sEnd = sMimeLine.substring(iPos + sCookieName.length()+1);
				int iSemi = sEnd.indexOf("; ");
				int skip = 2;
				if(iSemi<0) 
				{
					iSemi = sEnd.length();
					skip = 0;
				}
				if(iSemi>=0)
					sEnd = sEnd.substring(iSemi+skip);

				if(sValue==null)
					lines.set(i, sStart + sEnd);
				else
					lines.set(i, sStart + sNewPair + (skip==0?"":";") + (skip==2?" ":"") + sEnd);
				return;
			}//if found
		}//for

		if(!bFound && sValue!=null)
			lines.add("Cookie: " + sCookieName + '=' + sValue + ';');
	}


	private static void dumpEnv(ArrayList lines) 
	{
		System.out.println("---- START ----");
		for(int i=0; i<lines.size(); i++)
		{
			System.out.println(lines.get(i));
		}
		System.out.println("---- END ----");
	}


	/**
	 * Force the browser to use SSL if that is what is set in booster.config
	 */
	private void checkForForceSSL(ArrayList environment_lines)
	{
		if(m_Parent.isSecureConnection()) return; //connection already secure, so ignore

		if(m_Parent.shouldForceClientSSL(m_sRequestedHost))
		{
			ArrayList out_lines = new ArrayList();
			out_lines.add("Host: "+m_sRequestedHost);
			out_lines.add("Location: https://"+m_sRequestedHost + m_sURI);

			sendHTTPResponse(302, "Forcing to SSL", out_lines, null, null, true);
			if(m_Parent.isDebug()) m_Parent.m_pSystem.doDebug(pmaLog.DEBUGLEVEL_NONE, this.m_lRequestID+" Sent 302 (Forcing SSL) "+m_sRequestedHost, m_Parent);            
			HTTPLogEntry stat = new HTTPLogEntry(m_Parent.getMimeExcludes(), environment_lines, out_lines, m_sRequestLine, "text/html", 0, (long)m_iInboundSize, 304, m_Parent.getClientIPAddress(), m_Parent.getClientHostName(), m_Parent.m_pSystem.getSystemHostName(), m_sRequestedHost, m_lTotalTime, m_Parent.getBoosterIPAddress(), m_Parent.getServerPort(), null); 

			m_Parent.writeTextStatLog(stat);
			m_bNoServerConnect = true;
		}
	}

	/**
	 * tell the client that the resource in the cache has not changed
	 */
	private void sendNotChanged(ArrayList environment_lines, boolean bKeepAlive)
	{
		//System.out.println("not changed");
		ArrayList arr = new ArrayList();
		arr.add("X-Cache-Hit: BOOSTER-Cache");
		if(bKeepAlive) 
			arr.add("Connection: keep-alive");
		else
			arr.add("Connection: close");

		sendHTTPResponse(304, "Not Modified", arr, null, null, true);
		if(m_Parent.isDebug()) m_Parent.m_pSystem.doDebug(pmaLog.DEBUGLEVEL_NONE, this.m_lRequestID+" Sent 304 (Not Modified) from cache: "+m_sURI, m_Parent);
		//StatisticEntry stat = new StatisticEntry(m_Parent.getMimeExcludes(), m_sRequestLine, "text/html", 0, m_iInboundSize, 304, m_Parent.getClientIPAddress(), m_sRequestedHost, m_sUserName, m_sUserAgent, m_sReferer, m_sRequestedHost, m_lTotalTime, m_Parent.getBoosterIPAddress(), m_Parent.getServerPort(), m_sMethod);
		ArrayList out_lines = new ArrayList();
		out_lines.add("Host: "+m_sRequestedHost);

		if(m_bSendSessionCookie && m_Parent.m_session!=null)
		{
			String sCookie = getSetCookieHeader();
			out_lines.add(sCookie);
			m_bSendSessionCookie = false;
			if(m_Parent.getPrincipal()!=null)
			{
				String sHTTPSessionCookie = m_Parent.m_session.getCookieString(HTTPServer.SESSIONID_LABEL, "/", null);
				out_lines.add(sHTTPSessionCookie);
			}
		}

		HTTPLogEntry stat = new HTTPLogEntry(m_Parent.getMimeExcludes(), environment_lines, out_lines, m_sRequestLine, "text/html", 0, (long)m_iInboundSize, 304, m_Parent.getClientIPAddress(), m_Parent.getClientHostName(), m_Parent.m_pSystem.getSystemHostName(), m_sRequestedHost, m_lTotalTime, m_Parent.getBoosterIPAddress(), m_Parent.getServerPort(), null); 

		m_Parent.writeTextStatLog(stat);
	}

	/**
	 * bIsClientConnection=true means we are reading from the client
	 */
	private void processDataPackage(int iContentLength, ArrayList environment_lines, String sContentType, boolean bIsClientConnection) throws Exception
	{
		if(m_Parent.isDebug())
			m_Parent.m_pSystem.doDebug(pmaLog.DEBUGLEVEL_NONE, this.m_lRequestID+" processDataPackage()", m_Parent);
		float fBefore = iContentLength;
		float fAfter=0;
		int iReplyCode = getReplyCode(environment_lines);
		boolean bCacheIsCompressed = false;

		String sContentEncoding = Util.getMIMELine(environment_lines, "Content-Encoding");
		if(sContentEncoding!=null && sContentEncoding.indexOf("gzip")>=0) bCacheIsCompressed = true;
		boolean bChunkedTransferEncoding = false;
		String sTransferEncoding = Util.getMIMELine(environment_lines, "Transfer-Encoding");                
		if(sTransferEncoding!=null && sTransferEncoding.equalsIgnoreCase("chunked")) bChunkedTransferEncoding=true;

		byte bufCacheable[]=null;
		if(!bIsClientConnection)
		{
			if(m_bRemoveCacheInfo)
			{
				puakma.util.Util.replaceHeaderValue(environment_lines, "Cache-control", null);
				puakma.util.Util.replaceHeaderValue(environment_lines, "Pragma", null);
			}

			//BU 28/11/2012 removed. If the sending web server sends a past expiry, ignore and pass it on. 
			String sExpires = Util.getMIMELine(environment_lines, "Expires");
			//System.out.println("expires:[" + sExpires + "]");
			if(sExpires!=null)
			{
				//if we have an expiry date, then use this as the time to live
				long lNow = System.currentTimeMillis();
				long lExpiryDate = Util.getDateMSFromGMTString(sExpires);
				//System.out.println("expires:[" + sExpires + "]=" + lExpiryDate + " now="+lNow);
				//if expires>now
				if(lExpiryDate<lNow) 
				{
					lExpiryDate = lNow; //+1000; 
					java.util.Date dtExpires = new java.util.Date();
					dtExpires.setTime(lExpiryDate);
					//System.out.println("Past expiry date. Now set to " + dtExpires);
					sExpires = puakma.util.Util.formatDate(dtExpires, LAST_MOD_DATE, Locale.UK, m_tzGMT);
					puakma.util.Util.replaceHeaderValue(environment_lines, "Expires", sExpires);
				}

			}

			//Lotus Domino sends .js and .css files as text/html :-(
			if(m_bAdjustContentTypes)
			{
				if(m_sURI.endsWith(".js")) 
				{
					sContentType = "text/javascript";
					puakma.util.Util.replaceHeaderValue(environment_lines, "Content-Type", sContentType);
				}
				if(m_sURI.endsWith(".css")) 
				{
					sContentType = "text/css";
					puakma.util.Util.replaceHeaderValue(environment_lines, "Content-Type", sContentType);                
				}
			}

		}
		//adjust headers here                 
		//a MAX_GZIP_SIZE 0f -1 means no limit on object size for zipping
		if(!bIsClientConnection && (iContentLength<MAX_GZIP_SIZE || MAX_GZIP_SIZE<0) && sContentEncoding==null && m_Parent.shouldGZipOutput(m_sURI, sContentType))
		{    
			fBefore = 0;
			float fRatio=0;
			//System.out.println(m_lRequestID + "pre len="+iContentLength + " replycode="+iReplyCode);
			//long lStart = System.currentTimeMillis();
			int iReadLen = iContentLength;
			if(bChunkedTransferEncoding) iReadLen=-1;
			byte[] http_response_body = processGZIPContent(iReadLen, iReplyCode);
			//System.out.println(m_lRequestID + "post processGZIPContent() " + (System.currentTimeMillis()-lStart) );
			//bufCacheable = http_response_body;
			if(http_response_body!=null) fBefore = http_response_body.length;
			if(fBefore>0)
			{                
				byte bufCompressed[] = puakma.util.Util.gzipBuffer(http_response_body);                
				fAfter = bufCompressed.length;
				if(fAfter<fBefore)
				{
					http_response_body = bufCompressed;
					bCacheIsCompressed = true;

					fRatio = (float)((fBefore-fAfter)/fBefore)*100;
					if(m_Parent.isDebug())
						m_Parent.m_pSystem.doDebug(pmaLog.DEBUGLEVEL_NONE, this.m_lRequestID+" GZIP Output: " + sContentType + " " + (int)fBefore + "/" + (int)fAfter + " " + m_Parent.formatNumber(fRatio) + "%", m_Parent);
					environment_lines.add("Content-Encoding: gzip");
				}
				else //don't compress it....
					fAfter = fBefore;
			}
			bufCacheable = http_response_body;

			if(bChunkedTransferEncoding) puakma.util.Util.replaceHeaderValue(environment_lines, "Transfer-Encoding", null);
			//setContentLength(environment_lines, http_response_body.length);                    
			puakma.util.Util.replaceHeaderValue(environment_lines, "Content-Length", String.valueOf(http_response_body.length));
			sendHeaders(environment_lines, bIsClientConnection);
			m_Clientos.write(http_response_body);
			m_Clientos.flush();
			m_Parent.updateCompressionStats(fBefore, fAfter);
			m_Parent.updateBytesSent(fAfter);			
		}
		else
		{
			fAfter = fBefore;
			//System.out.println(this.getName() + " regular content...");
			sendHeaders(environment_lines, bIsClientConnection);
			if(bChunkedTransferEncoding)
				processChunkedContent(bIsClientConnection); 
			else
				bufCacheable = processContent(iContentLength, iReplyCode, bIsClientConnection); 
			//update stats
			if(!bIsClientConnection) m_Parent.updateBytesSent(fBefore);
		} 


		if(bIsClientConnection)
		{
			m_Parent.getAddIn().incrementStatistic(BOOSTER.STATISTIC_KEY_CLIENTBYTESPERHOUR, fAfter);
			m_Parent.getAddIn().incrementStatistic(BOOSTER.STATISTIC_KEY_CLIENTTOTALBYTES, fAfter);
			m_Parent.getAddIn().incrementStatistic(BOOSTER.STATISTIC_KEY_CACHEMISSESPERHOUR, 1);
		}
		else
		{
			m_Parent.getAddIn().incrementStatistic(BOOSTER.STATISTIC_KEY_SERVERBYTESPERHOUR, fAfter);
			m_Parent.getAddIn().incrementStatistic(BOOSTER.STATISTIC_KEY_SERVERTOTALBYTES, fAfter);
		}


		if(!bIsClientConnection)
		{
			m_lTotalReplyTime=System.currentTimeMillis()-m_lTotalReplyTime;
			m_lTotalTime=System.currentTimeMillis()-m_lTotalTime;
			m_Parent.writeCompressionStatLog(m_sURI, (int)fBefore, (int)fAfter, (int)m_lWebServerTime, (int)m_lTotalReplyTime, sContentType);

			//StatisticEntry stat = new StatisticEntry(m_Parent.getMimeExcludes(), m_sRequestLine, sContentType, iContentLength, m_iInboundSize, iReplyCode, m_Parent.getClientIPAddress(), m_sRequestedHost, m_sUserName, m_sUserAgent, m_sReferer, m_sRequestedHost, m_lTotalTime, m_Parent.getBoosterIPAddress(), m_Parent.getServerPort(), m_sMethod);            
			HTTPLogEntry stat = new HTTPLogEntry(m_Parent.getMimeExcludes(), null, environment_lines, m_sRequestLine, sContentType, iContentLength, (long)m_iInboundSize, iReplyCode, m_Parent.getClientIPAddress(), m_Parent.getClientHostName(), m_Parent.m_pSystem.getSystemHostName(), m_sRequestedHost, m_lTotalTime, m_Parent.getBoosterIPAddress(), m_Parent.getServerPort(), null); 

			m_Parent.writeTextStatLog(stat);

			//now add the item to the cache
			if(bufCacheable!=null && bufCacheable.length>0 && iReplyCode==200 && m_sMethod.equals("GET"))
			{
				addToCache(bufCacheable, environment_lines, bCacheIsCompressed, sContentType);                
			}
		}
	}

	/**
	 * Adds the buffer to the cache. Note the buffer may be compressed if the webserver
	 * has already compressed it. We should store items in the cache compressed to save memory.
	 *
	 */
	private void addToCache(byte[] bufCacheable, ArrayList environment_lines, boolean bCacheIsCompressed, String sContentType)
	{
		//if(!(bufCacheable!=null && bufCacheable.length>0 && iReplyCode==200)) return;        

		BOOSTERCacheItem item = new BOOSTERCacheItem(m_sRequestedHost, m_sURI, bufCacheable, environment_lines, bCacheIsCompressed, MAX_CACHEABLEOBJECT_SIZE_BYTES, MAX_CACHE_MILLISECONDS, MIN_CACHE_MILLISECONDS, m_Parent.isSharedCache());
		if(item.isCacheable() && m_Parent.shouldCacheOutput(m_sURI, sContentType))
		{
			//System.out.println("ADDING: "+m_sRequestedHost+m_sURI + " " + bufCacheable.length + " to cache... gzip="+bCacheIsCompressed);
			if(m_Parent.addToCache(item))
			{
				if(m_Parent.isDebug()) m_Parent.m_pSystem.doDebug(pmaLog.DEBUGLEVEL_NONE, this.m_lRequestID+" Added "+m_sURI + " " + bufCacheable.length + " to cache...", m_Parent);
			}
		}
		//else
		//    System.out.println("NOT CACHEABLE: "+m_sURI + " " + bufCacheable.length + " " + sContentType);

	}

	/**
	 *
	 */
	private void sendHeaders(ArrayList environment_lines, boolean bIsClientConnection) throws Exception
	{
		if(m_Parent.isDebug()) m_Parent.m_pSystem.doDebug(pmaLog.DEBUGLEVEL_NONE, this.m_lRequestID+" Sending headers. ToClient="+bIsClientConnection, m_Parent);
		StringBuilder sbOut = new StringBuilder(512); 
		BufferedOutputStream os=null;
		if(bIsClientConnection)         
			os = m_Serveros;                            
		else //server output       
			os = m_Clientos;        
		if(bIsClientConnection)//write the main request line
		{
			sbOut.append(m_sMethod);
			sbOut.append(' ');
			sbOut.append(m_sURI);
			sbOut.append(' ');
			sbOut.append(m_sHTTPVersionClient);
			sbOut.append(HTTP_NEWLINE);                        
		}        

		for(int i=0; i<environment_lines.size(); i++)
		{
			sbOut.append((String)environment_lines.get(i));
			sbOut.append(HTTP_NEWLINE);           
		}

		if(!bIsClientConnection && m_bSendSessionCookie && m_Parent.m_session!=null)
		{
			String sCookie = getSetCookieHeader();
			sbOut.append(sCookie);
			sbOut.append(HTTP_NEWLINE);
			m_bSendSessionCookie = false;
			if(m_Parent.getPrincipal()!=null)
			{
				String sHTTPSessionCookie = m_Parent.m_session.getCookieString(HTTPServer.SESSIONID_LABEL, "/", null);
				sbOut.append(sHTTPSessionCookie);
				sbOut.append(HTTP_NEWLINE);
			}
			//System.out.println("SESSION cookie sent sendHeaders() " + sCookie);
		}

		sbOut.append(HTTP_NEWLINE);
		//System.out.println("-----------------------------");
		//System.out.println(sbOut.toString());
		os.write(sbOut.toString().getBytes());
		os.flush();
	}


	/**
	 * 
	 * @return
	 */
	private String getSetCookieHeader()
	{
		return m_Parent.m_session.getCookieString(BOOSTER.SESSIONID_LABEL, "/", getHostOnly(m_sRequestedHost));
	}

	/**
	 * Determines if Booster should track sessions
	 * @return
	 */
	private boolean shouldTrackSessions()
	{
		//return m_Parent.shouldUseStickySessions();
		return m_Parent.shouldUseStickySessions() || m_Parent.getPrincipal()!=null;
	}


	/**
	 * Get the host only portion from the server:port combo eg "www.xxx.com:8080" 
	 * will return "www.xxx.com"
	 * @param sRequestedHost
	 * @return
	 */
	private String getHostOnly(String sRequestedHost) 
	{
		if(sRequestedHost!=null)
		{
			if(sRequestedHost.indexOf('.')<0) return null; // localhost?
			int iPos = sRequestedHost.indexOf(':'); //may be www.xxx.com:8080
			if(iPos>0) return sRequestedHost.substring(0, iPos);
			return sRequestedHost;
		}
		return null;
	}


	/**
	 * 
	 * @param bIsClientConnection
	 * @return
	 * @throws IOException
	 */
	private String readLine(boolean bIsClientConnection) throws IOException
	{
		if(bIsClientConnection) 
		{
			return m_Clientis.readLine();        
		}

		//must be server        
		return m_Serveris.readLine();

	}

	/**
	 * Read the http headers and return the content length
	 */
	private int readHeaders(ArrayList environment_lines, boolean bIsClientConnection) throws Exception
	{
		int iContentLength=0;                 
		//BufferedReader is=null;
		String environment_line;
		environment_lines.clear();  


		environment_line = readLine(bIsClientConnection);//is.readLine();
		if(environment_line==null || environment_line.length()==0 || environment_line.length()>m_Parent.getMaxURI()) 
		{
			//System.out.println("ERR: " + environment_line.length());
			m_bRunning=false;
			return CONTENT_ERROR;
		}
		StringBuilder sbDebugHeaders = new StringBuilder(256);
		//if(m_Parent.isDebugHeaders()) m_Parent.m_pSystem.doDebug(pmaLog.DEBUGLEVEL_NONE, this.m_lRequestID + " REQ:["+environment_line+"]", m_Parent);
		sbDebugHeaders.append(environment_line + "\r\n");
		while(environment_line!=null && environment_line.length()!=0)
		{
			// Add the new environment data line to our Vector of them
			environment_lines.add(environment_line);
			environment_line = readLine(bIsClientConnection);//is.readLine();          
			//if(m_Parent.isDebugHeaders()) m_Parent.m_pSystem.doDebug(pmaLog.DEBUGLEVEL_NONE, this.m_lRequestID + " ["+environment_line+"]", m_Parent);
			sbDebugHeaders.append(environment_line + "\r\n");
		}
		if(m_Parent.isDebugHeaders()) m_Parent.m_pSystem.doDebug(pmaLog.DEBUGLEVEL_NONE, "("+m_lRequestID + ") "+ sbDebugHeaders.toString(), m_Parent);
		if(environment_line==null) return CONTENT_ERROR; //something bad happened

		//--------------- headers have been read ------------------------
		int iReplyCode = getReplyCode(environment_lines);

		//if a client connection, chop up the request so we can call the 
		//header processors
		if(bIsClientConnection)
		{
			//m_Parent.setClientTimeout(-1); //reset timeout to default
			environment_line = (String)environment_lines.get(0); 
			m_sRequestLine = environment_line;
			environment_lines.remove(0);

			int iPos = environment_line.indexOf(' ');
			if(iPos>=0)
			{
				m_sMethod = environment_line.substring(0, iPos);
				m_sURI = environment_line.substring(iPos+1, environment_line.length());
			}
			iPos = m_sURI.indexOf(' ');
			if(iPos>=0)
			{
				m_sHTTPVersionClient = m_sURI.substring(iPos+1, m_sURI.length());
				m_sURI = m_sURI.substring(0, iPos);
			}

			if(m_Parent.m_session==null && shouldTrackSessions())
			{
				m_Parent.m_session = getSession(environment_lines);				
				if(m_Parent.m_session==null) 
				{
					m_Parent.m_session = m_Parent.m_pSystem.createNewSession(InetAddress.getByName(m_Parent.getClientIPAddress()), "BOOSTER_Session_Tracker");
					Principal principal = m_Parent.getPrincipal();
					if(principal!=null)
					{
						m_Parent.m_session.setLoginName(principal.getName());
						m_Parent.m_session.setUserName(principal.getName());
						m_Parent.m_session.setAuthenticatorUsed("TLS"); //?? or just blank?						
					}					
					m_bSendSessionCookie = true;
					m_Parent.m_session.setObjectChanged(true);
					m_Parent.m_session.setSynchAcrossCluster(true);
					m_Parent.pushClusterSession(m_Parent.m_session);
					//System.out.println("SESSION tracking: createNewSession()");
				}
			}

			m_bNoServerConnect = customHTTPHeaderProcessing(environment_lines);

			if(m_Parent.isDebug())
				m_Parent.m_pSystem.doDebug(pmaLog.DEBUGLEVEL_NONE, this.m_lRequestID+" " + m_sMethod+" "+m_sURI+" "+m_sHTTPVersionClient, m_Parent);
		}
		else
		{
			environment_line = (String)environment_lines.get(0);
			//int iPos = environment_line.indexOf(' ');
			//if(iPos>=0) m_sHTTPVersionServer = environment_line.substring(0, iPos);
			if(m_Parent.isDebug())
				m_Parent.m_pSystem.doDebug(pmaLog.DEBUGLEVEL_NONE, this.m_lRequestID+" " + environment_line, m_Parent);
		}

		//check for a 100 Continue, issued by IIS 6.0. I have a feeling this code is not quite right if the following is correct:
		//B>>POST (without body)
		//S<<100 Continue
		//B>>POST_body
		//S<<200 OK
		boolean b100Continue = !bIsClientConnection && (iReplyCode>=100 && iReplyCode<200);
		if(b100Continue)
		{
			environment_lines.clear();
			return readHeaders(environment_lines, bIsClientConnection);
		}


		//if the client says close the connection, then close it.
		String sCloseConnection = puakma.util.Util.getMIMELine(environment_lines, "Connection");
		//if no connection specified and this is a http 1.0 server, close the connection
		//if((szCloseConnection!=null && szCloseConnection.equalsIgnoreCase("close")) || (szCloseConnection==null && !nonHTTP10Server()) )
		//close the connection unless we specifically get a keep-alive reply from server
		m_bCanKeepAlive = true;
		if(sCloseConnection==null || sCloseConnection.toLowerCase().indexOf("keep-alive")<0)
		{   
			m_bCanKeepAlive = false;
			m_bRunning=false;
			if(bIsClientConnection) 
				m_bDieAfterNext=true;
			else
				m_bRunning = false;        	
		}

		//content length of zero on a POST ??!!!
		String sContentLength = puakma.util.Util.getMIMELine(environment_lines, "Content-Length");
		if(sContentLength!=null && sContentLength.length()>0)
		{
			try
			{ 
				iContentLength = Integer.parseInt(puakma.util.Util.trimSpaces(sContentLength));                
			}
			catch(Exception ie)
			{
				m_Parent.m_pSystem.doError("Invalid Content-Length: ["+sContentLength + "]", m_Parent);
				iContentLength = CONTENT_ERROR; //force connection to drop.
			}
			if(!bIsClientConnection && this.m_sMethod.equalsIgnoreCase("HEAD")) iContentLength = 0;
		}   
		else
		{
			if(bIsClientConnection) 
				iContentLength = 0;
			else
			{
				/*
				//only 1.0 servers may specify no content length
				if(isNonHTTP10Server())
				{
					iContentLength = 0;
					//should reply with 411 content length required
					//iContentLength = CONTENT_ERROR; 
				}
				else
					iContentLength = UNKNOWN_CONTENT_LENGTH;
				 */
				//BU 7.Aug.2012 Stupid IBM Domino/xpages stuff says http1.1 but supplies no content-length
				if(iReplyCode==200) iContentLength = UNKNOWN_CONTENT_LENGTH;
			}

		}

		if(m_Parent.isDebug())
			m_Parent.m_pSystem.doDebug(pmaLog.DEBUGLEVEL_NONE, this.m_lRequestID + " Header read. content="+iContentLength+" bytes connection="+sCloseConnection, m_Parent);        
		return iContentLength;
	}

	/**
	 * determines if the server reply is not HTTP1.0. This means the server must have a 
	 * content length
	 */
	/*private boolean isNonHTTP10Server()
	{
		if(m_sHTTPVersionServer!=null && !m_sHTTPVersionServer.equalsIgnoreCase("HTTP/1.0")) return true;

		return false;
	}*/


	/**
	 * try to locate the session object based on the session id
	 */
	private SessionContext getSession(ArrayList environment_lines)
	{        
		String sSessionID = HTTPServer.getCurrentSessionID(BOOSTER.SESSIONID_LABEL, environment_lines); 		
		if(sSessionID!=null && sSessionID.length()>0)
		{
			SessionContext sess = m_Parent.m_pSystem.getSession(sSessionID);
			if(sess!=null) 
			{                
				sess.setLastTransactionTime();
				sess.setObjectChanged(true);
				sess.setSynchAcrossCluster(true);
			}
			return sess;
		}
		return null;
	}



	/**
	 *
	 */
	private boolean customHTTPHeaderProcessing(ArrayList environment_lines)
	{
		String sProcessors[] = m_Parent.getCustomHeaderProcessors();
		if(sProcessors==null) return false;
		m_Parent.m_pSystem.doDebug(pmaLog.DEBUGLEVEL_FULL, "customHTTPHeaderProcessing()", m_Parent);

		SessionContext pSession = m_Parent.m_session;
		if(pSession==null) pSession = getSession(environment_lines);

		for(int i=0; i<sProcessors.length; i++)
		{
			try
			{
				Class c = Class.forName(sProcessors[i]);
				if(m_Parent.isDebug())
					m_Parent.m_pSystem.doDebug(pmaLog.DEBUGLEVEL_NONE, " HTTPHeaderProcessor: " + c.getName(), m_Parent);
				Object o = c.newInstance();
				HTTPHeaderProcessor hp = (HTTPHeaderProcessor)o;
				hp.init(m_Parent.getAddIn(), m_Parent.m_pSystem, pSession, m_sMethod, m_sURI, m_sHTTPVersionClient, environment_lines, m_Parent.isSecureConnection(), m_Parent.getClientIPAddress(), m_Clientis);
				boolean bBreak = hp.execute();
				m_sMethod = hp.getHTTPMethod();
				m_sURI = hp.getHTTPURI();
				m_sHTTPVersionClient = hp.getHTTPVersion();
				if(hp.shouldReplyToClient())
				{                                        
					sendHTTPResponse(hp.getReturnCode(), hp.getReturnMessage(), hp.getReturnHeaders(), hp.getReturnMimeType(), hp.getReturnBuffer(), true);
					return true;
				}
				if(bBreak) break;
			}
			catch(Exception e)
			{                
				m_Parent.m_pSystem.doError("customHTTPHeaderProcessing() "+e.toString(), m_Parent);
				puakma.util.Util.logStackTrace(e, m_Parent.m_pSystem, 999);
			}
		}
		return false;
	}

	/**
	 *
	 */
	public void sendHTTPResponse(int http_code, String http_code_string,
			ArrayList extra_headers, 
			String content_type, byte[] http_response_body, boolean bIsClientConnection)
	{
		if(null == http_response_body)
		{
			// Send the default response body, it's a simple "error" page
			//String response_content = "Response has no content";
			//http_response_body = response_content.getBytes();
			http_response_body = new byte[0];
		}               

		sendHTTPResponse(http_code, http_code_string, extra_headers, 
				content_type, http_response_body.length, new ByteArrayInputStream(http_response_body), bIsClientConnection);
	}

	/**
	 * This is a stream version for sending BIG files
	 */
	public void sendHTTPResponse(int http_code, String http_code_string,
			ArrayList extra_headers, String content_type, 
			long lStream, InputStream is, boolean bIsClientConnection)
	{     
		BufferedOutputStream os=null;
		if(bIsClientConnection) 
			os = m_Clientos;
		else
			os = m_Serveros;


		if(os==null) return;
		String HTTP_VERSION="HTTP/1.1";

		try
		{
			os.write((HTTP_VERSION + " " + http_code + " " + http_code_string + HTTP_NEWLINE).getBytes());
			os.write(("Server: Puakma/" + m_Parent.m_pSystem.getVersion() + " (BOOSTER)"+HTTP_NEWLINE).getBytes());
			os.write(("Date: " + Util.formatDate(new java.util.Date(), LAST_MOD_DATE, Locale.UK, m_tzGMT) + HTTP_NEWLINE).getBytes());
			String sConnection = Util.getMIMELine(extra_headers, "Connection");			
			if(sConnection==null) os.write(("Connection: close" + HTTP_NEWLINE).getBytes());
			if(null == content_type) content_type="text/html";
			if(lStream>0) os.write(("Content-Type: " + content_type + HTTP_NEWLINE).getBytes());
			os.write(("Content-Length: " + lStream + HTTP_NEWLINE).getBytes());

			if(!bIsClientConnection && m_bSendSessionCookie && m_Parent.m_session!=null)
			{
				String sCookie = getSetCookieHeader();
				os.write((sCookie + HTTP_NEWLINE).getBytes());
				m_bSendSessionCookie = false;
				//System.out.println("SESSION cookie sent sendHTTPResponse()");
			}

			if(!m_Parent.isLicensed()) os.write(("X-BOOSTERLicense: NON-COMMERCIAL USE ONLY" + HTTP_NEWLINE).getBytes());

			// Send out any extra headers, if we were given any
			if(null != extra_headers)
			{
				String header_line;

				// Loop through the header line Strings
				Object oHeaders[] = extra_headers.toArray();
				for(int i=0; i<oHeaders.length; i++ )
				{
					// Send the header line out to the client
					header_line = (String) oHeaders[i];
					header_line = header_line + HTTP_NEWLINE;
					os.write(header_line.getBytes());
				}
			}//extra headers

			//send a blank line to denote the start of the data
			os.write((HTTP_NEWLINE).getBytes());            
			int len=0;
			byte[] output;
			final int MAX_CHUNK = 524288; //512k data chunks
			while((len=is.available()) > 0)
			{
				if(len>MAX_CHUNK) len = MAX_CHUNK;
				output = new byte[len];

				int iRead = is.read(output);                            
				if(iRead>0) 
					os.write(output, 0, iRead);            
			}          
			os.flush();
		} //try
		catch (Exception e)
		{
			//System.out.println("sendHTTPResponse(): " + e.toString());
			//e.printStackTrace();
		}
		//System.out.println("stuff sent");
	}



	/**
	 *     
	 */
	/*private void setContentLength(ArrayList environment_lines, int iContentLength)
    {
        for(int i=0; i<environment_lines.size(); i++)
        {
            String s = (String)environment_lines.get(i);
            if(s.toLowerCase().startsWith("content-length:"))
            {
                environment_lines.remove(i);
                environment_lines.add("Content-Length: "+iContentLength);
                return;
            }
        }
    }*/



	/**
	 * 
	 */
	public void sendNoHostAvailableMessage()
	{
		ArrayList arrToClient = new ArrayList();

		File f = m_Parent.getUnavailableFile();         
		if(f==null || !f.exists() || !f.isFile())
		{            
			//BJU 12/7/06 if no file specified, just drop the connection
			//String sContentType="text/html";
			//byte buf[] = "<h1>No servers are currently available</h1>\r\n".getBytes();
			//sendHTTPResponse(502, "Server is unavailable", arrToClient, sContentType, buf, true);
			return;
		}

		serveLocalFile(f, arrToClient);
	}



	/**
	 * Serve a file from the local filesystem
	 */
	private void serveLocalFile(File fToServe, ArrayList arrToClient)
	{

		String sContentType=m_Parent.getFileMimeType(fToServe.getName());
		try
		{
			//System.out.println("Serving local file: " + fToServe.getAbsolutePath() + " type="+sContentType);
			FileInputStream fin = new FileInputStream(fToServe);
			sendHTTPResponse(200, "OK", arrToClient, sContentType,
					fToServe.length(), fin, true);
		}
		catch(Exception e)
		{
			m_Parent.m_pSystem.doError("BOOSTER.NoLocalFile", new String[]{fToServe.getAbsolutePath(), e.toString()}, m_Parent);
		} 
	}


	/**
	 * "HTTP/1.1 302 Found"
	 */
	private int getReplyCode(ArrayList environment_lines)
	{
		int iReturn=200;
		if(environment_lines==null || environment_lines.size()==0) return -1;
		String s1 = (String)environment_lines.get(0);        
		if(s1!=null)
		{
			int iPos = s1.indexOf(' ');
			if(iPos>0) s1 = s1.substring(iPos+1, s1.length());
			iPos = s1.indexOf(' ');
			if(iPos>0) s1 = s1.substring(0, iPos);
			try{iReturn=Integer.parseInt(s1);}catch(Exception e){}
		}

		return iReturn;
	}

	/**
	 *
	 */
	private void replaceLocationHeader(ArrayList environment_lines)
	{
		String sLocation = Util.getMIMELine(environment_lines, "Location");
		if(sLocation==null) return;
		boolean bUpdate=false;

		String sNewLocation=sLocation;
		//System.out.println(this.getName() + " REPLACE: " + sLocation);
		if(m_sReplacementHosts!=null)
		{
			String sServer = m_Parent.getProxyReference();
			int iPos = sLocation.indexOf("://");                    
			if(iPos>0) 
			{
				String sOldServer=sLocation.substring(iPos+3, sLocation.length());
				iPos = sOldServer.indexOf('/');
				if(iPos>0)
				{
					sOldServer=sOldServer.substring(0, iPos);
				}
				for(int i=0; i<m_sReplacementHosts.length; i++)
				{
					if(sOldServer.equalsIgnoreCase(m_sReplacementHosts[i]))
					{
						sNewLocation = sLocation.replaceFirst(sOldServer, sServer);
						bUpdate = true;
						break;
					}
					else
					{
						/*
						 * If we specify a * as a replacehost, then we want to always rip out the WHOLE servername
						 * and port number. This was to fix an issue with Domino R5 that does odd things with server
						 * references.
						 */
						//if(m_sReplacementHosts[i].length()>1 && 
						//    m_sReplacementHosts[i].charAt(0)=='*' && m_sReplacementHosts[i].charAt(1)==':')
						if(m_sReplacementHosts[i].length()==1 && 
								m_sReplacementHosts[i].charAt(0)=='*')
						{
							//String sPort = m_sReplacementHosts[i].substring(2, m_sReplacementHosts[i].length());
							//if(sOldServer.endsWith(sPort))
							//{
							sNewLocation = sLocation.replaceFirst(sOldServer, sServer);
							bUpdate = true;
							break;
							//}
						}
						else
						{
							if(sOldServer.equals("*"))
							{
								sNewLocation = sLocation.replaceFirst(sOldServer, sServer);
								if(m_Parent.isDebug()) m_Parent.m_pSystem.doDebug(pmaLog.DEBUGLEVEL_NONE, this.m_lRequestID+" Replaced location header with: "+sNewLocation, m_Parent);
								bUpdate = true;
								break;
							}
						}
					}//else  
				}//for
			}//if has ://
		}//replace host

		//System.out.println("loc ["+sNewLocation+"]");
		//if a secure connection 
		//and we are forcing ssl for this host
		//and we are redirecting to standard http
		String sLowURL = sNewLocation.toLowerCase();            
		if(m_Parent.isSecureConnection() && m_Parent.getForceSSL(getServerName(sLowURL)) && sLowURL.startsWith("http:"))
		{                                
			sNewLocation = "https:" + sNewLocation.substring(5);                 
			bUpdate = true;
		}
		//System.out.println("loc ["+sNewLocation+"]");                        

		if(bUpdate) puakma.util.Util.replaceHeaderValue(environment_lines, "Location", sNewLocation);                
	}

	/**
	 * Extract the hostname from a url
	 */
	private String getServerName(String sURL)
	{
		try
		{
			URL u = new URL(sURL);            
			return u.getHost().toLowerCase();
		}
		catch(Exception r)
		{}
		return "";
	}


	/**
	 * Read the content chunk. Return the buffer if it is less than the specified size (for storage in cache), 
	 * otherwise return null  
	 */
	private byte[] processContent(int iContentLength, int iReplyCode, boolean bIsClientConnection) throws Exception
	{
		//BufferedReader is=null;         
		BufferedOutputStream os=null;
		ByteArrayOutputStream baos = null;
		if(bIsClientConnection) 
		{                         
			//is = m_Clientis;
			os = m_Serveros;
		}
		else //server        
		{              
			//is = m_Serveris;
			os = m_Clientos;
		}
		int iTotalRead=0;
		int iTotalSent=0;
		int iChunkSize=0; 
		//if this is not a http 1.0 server replying with a 200 ok, bail out.
		if(!bIsClientConnection && iContentLength<0 && (iReplyCode<200 || iReplyCode>=300)) return null;
		if(bIsClientConnection && iContentLength<0 && m_sMethod!=null && m_sMethod.equalsIgnoreCase("GET")) return null; //BJU added

		if(m_Parent.isDebug())
			m_Parent.m_pSystem.doDebug(pmaLog.DEBUGLEVEL_NONE, this.m_lRequestID+" Sending standard output. client="+bIsClientConnection + " len="+iContentLength, m_Parent);
		if(iContentLength>0 && !bIsClientConnection && iContentLength<=MAX_CACHEABLEOBJECT_SIZE_BYTES) baos = new ByteArrayOutputStream(iContentLength);
		//System.out.println("totread="+iTotalRead + " len="+iContentLength + " client="+bIsClientConnection);
		while(iTotalRead<iContentLength || (iContentLength<0 && !bIsClientConnection))
		{
			if(iContentLength<=0)
				iChunkSize = 1024;
			else
				iChunkSize = iContentLength-iTotalRead;
			if(iChunkSize>MAXCHUNK) iChunkSize = MAXCHUNK;
			char cBuf[] = new char[iChunkSize];
			//System.out.print("Reading.... ("+iChunkSize+") ");
			//int iRead = is.read(cBuf);
			int iRead = readInput(cBuf, bIsClientConnection);
			//System.out.println("  read: "+iRead);
			if(iRead<0) break; //BJU - should this stay in?? This is required for HTTP1.0 posts.
			iTotalRead += iRead;
			if(iRead>0)
			{
				byte buf[] = puakma.util.Util.makeByteArray(cBuf, iRead);  
				if(baos!=null) baos.write(buf);
				os.write(buf);
				//System.out.print(new String(buf));
				iTotalSent += iRead;                
			}
			else            
				Thread.sleep(10);

		}//while        
		os.flush(); 
		if(m_Parent.isDebug() && iTotalSent>0)
			m_Parent.m_pSystem.doDebug(pmaLog.DEBUGLEVEL_NONE, this.m_lRequestID+" Content sent: " + iTotalSent + " bytes", m_Parent);

		if(baos!=null) return baos.toByteArray();
		return null;
	}

	/**
	 *
	 */
	private int readInput(char cBuf[], boolean bIsClientConnection) throws IOException
	{
		int iRead = 0;
		if(bIsClientConnection)
			iRead = m_Clientis.read(cBuf);
		else
			iRead = m_Serveris.read(cBuf);

		return iRead;
	}


	/**
	 * Read the content chunked     
	 */
	private void processChunkedContent(boolean bIsClientConnection) throws Exception
	{
		//BufferedReader is=null;         
		BufferedOutputStream os=null;
		if(bIsClientConnection) 
		{
			//is = m_Clientis; 
			os = m_Serveros;
		}
		else //server        
		{
			//is = m_Serveris;            
			os = m_Clientos;
		}
		String CRLF = "\r\n";
		//System.out.println(this.getName() + "Sending chunked output...");
		if(m_Parent.isDebug())
			m_Parent.m_pSystem.doDebug(pmaLog.DEBUGLEVEL_NONE, this.m_lRequestID+" Sending chunked output", m_Parent);
		int iChunkLength=0;
		int iChunkCount=0;
		boolean bMoreChunks=true; 
		if(bIsClientConnection) m_Clientis.mark(1024); else m_Serveris.mark(1024); //is.mark(1024);
		String sChunkSize = readLine(bIsClientConnection);//is.readLine();

		while(bMoreChunks)
		{            
			try
			{//chunks are in base 16 FC01 etc and may have a trailing comment
				int iPos = sChunkSize.indexOf(';');
				if(iPos>0) sChunkSize = sChunkSize.substring(0, iPos);
				iChunkLength = Integer.parseInt(sChunkSize, 16);
			}
			catch(Exception t){iChunkLength = UNKNOWN_CONTENT_LENGTH;}

			//System.out.println(" chunk is " + iChunkLength + " bytes [0x" + sChunkSize + "]");
			int iTotalRead=0;
			//do this block if the content isn't really chunked
			if(iChunkLength==UNKNOWN_CONTENT_LENGTH && iChunkCount==0)
			{
				if(bIsClientConnection) m_Clientis.reset(); else m_Serveris.reset();//is.reset();                
				int iRead=0;
				char cBuf[] = new char[1024];
				while((iRead = this.readInput(cBuf, bIsClientConnection)) >= 0)
				{

					iTotalRead += iRead;
					if(iRead>0)
					{
						byte buf[] = puakma.util.Util.makeByteArray(cBuf, iRead);                         
						os.write(buf);                                            
					} 
				}
				break;
			}

			os.write((sChunkSize+CRLF).getBytes());
			if(iChunkLength==0) 
			{
				//System.out.print("processing footer...");
				sChunkSize = this.readLine(bIsClientConnection);//is.readLine(); //swallow footers
				//System.out.println("["+sChunkSize+"]");
				while(sChunkSize!=null && sChunkSize.length()>0)
				{
					//System.out.println("-->"+sChunkSize);
					os.write((sChunkSize+CRLF).getBytes());
					sChunkSize = this.readLine(bIsClientConnection);//is.readLine(); //swallow footers
				}
				os.write(CRLF.getBytes());
				break;
			}

			//ByteArrayOutputStream baos = new ByteArrayOutputStream();
			while(iTotalRead<iChunkLength)
			{
				char cBuf[] = new char[iChunkLength-iTotalRead];
				int iRead = this.readInput(cBuf, bIsClientConnection);//is.read(cBuf);
				if(iRead<0) break;
				iTotalRead += iRead;
				if(iRead>0)
				{
					byte buf[] = puakma.util.Util.makeByteArray(cBuf, iRead);
					//m_os.write(buf, 0, iRead);                     
					os.write(buf); 
					//baos.write(buf, 0, iRead);
					//System.out.print(new String(buf));
					//System.out.println(" wrote " + iRead + " bytes"); 
				}  
				else
					Thread.sleep(10);
			}//while
			os.write(CRLF.getBytes());            

			//byte b2[] = puakma.util.Util.ungzipBuffer(baos.toByteArray());
			//System.out.println(new String(b2));

			this.readLine(bIsClientConnection);//is.readLine(); //swallow crlf
			sChunkSize = this.readLine(bIsClientConnection);//is.readLine(); //read the next chunksize
			if(sChunkSize==null || sChunkSize.length()==0) bMoreChunks = false;

			iChunkCount++;

		}//while morechunks

		//System.out.println(" flushing...");
		os.flush(); 
	}


	/**
	 * Get the content and gzip it. return the gzipped buffer so we can adjust 
	 * the header
	 */
	private byte[] processGZIPContent(int iContentLength, int iReplyCode) throws Exception
	{
		BufferedReader is=null;         
		is = m_Serveris;

		if(m_Parent.isDebug()) m_Parent.m_pSystem.doDebug(pmaLog.DEBUGLEVEL_NONE, this.m_lRequestID+" Reading server content ready for gzip "+iContentLength, m_Parent);

		return puakma.util.Util.getHTTPContent(is, iContentLength, -1);
	}

	/**
	 *
	 */
	public synchronized void setServerStreams(InputStream is, OutputStream os)
	{
		try
		{
			if(is!=null) m_Serveris = new BufferedReader(new InputStreamReader(is, "ISO-8859-1"));
			if(os!=null) m_Serveros = new BufferedOutputStream(os);         
		}
		catch(Exception e)
		{            
			m_Parent.m_pSystem.doError("setServerStreams() "+e.toString(), m_Parent);
		}
	}



}//class
