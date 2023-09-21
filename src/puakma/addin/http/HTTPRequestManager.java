/** ***************************************************************
HTTPRequestManager.java
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

import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URLDecoder;
import java.security.Principal;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.Vector;

import puakma.addin.pmaAddInStatusLine;
import puakma.addin.http.action.ActionReturn;
import puakma.addin.http.action.HTTPSessionContext;
import puakma.addin.http.action.SharedActionClassLoader;
import puakma.addin.http.document.DesignElement;
import puakma.addin.http.document.HTMLDocument;
import puakma.addin.http.log.HTTPLogEntry;
import puakma.error.ErrorDetect;
import puakma.error.pmaLog;
import puakma.security.LTPAToken;
import puakma.server.AddInMessage;
import puakma.system.ActionRunnerInterface;
import puakma.system.Cookie;
import puakma.system.Document;
import puakma.system.DocumentFileItem;
import puakma.system.DocumentItem;
import puakma.system.RequestPath;
import puakma.system.SessionContext;
import puakma.system.SystemContext;
import puakma.system.X500Name;
import puakma.system.pmaSession;
import puakma.system.pmaSystem;
import puakma.system.pmaThreadInterface;
import puakma.util.ByteStreamReader;
import puakma.util.HTTPHeaderProcessor;
import puakma.util.RunProgram;
import puakma.util.Util;

/**
 * HTTPRequestManagers do several things:<BR>
 * 1) wait for a GET or POST request from the client m_socket<BR>
 * 2) processRequest()<BR>
 * <P>
 * HTTPRequestManagers are invoked as separate threads (one thread to a
 * request) after being initialized by the HTTPServer at run-time
 *
 * Connection: close 
 * Accept-Ranges: bytes
 */
public class HTTPRequestManager implements pmaThreadInterface, ErrorDetect
{
	private HTTPServer m_http_server;
	public HTTPSessionContext m_pSession;
	public SystemContext m_pSystem;
	private Principal m_principal;

	private static final String DOTDOT = "..";
	private static final String SLASHSLASH = "//";
	private static final String SLASHDOTDOT = "/..";
	private static final String HTTP_NEWLINE = "\r\n";

	private static final int STACKTRACE_DEPTH = -1;//20;
	private static final String HTTP_VERSION="HTTP/1.1"; //all the other servers report 1.0 ?!
	public static final String ACCESS_APP_ROLE = "AllowAccess";
	public static final String ACCESS_WS_ROLE = "WebServiceAccess";
	public static final String ACCESS_RESOURCES_ROLE = "ResourceAccess";
	public static final String DENY_ACCESS_ROLE = "DenyAccess";
	public static final String LAST_MOD_DATE = "EEE, dd MMM yyyy HH:mm:ss z";
	//private int m_iPipelineTimeoutSeconds=5;

	// these are all that come in on a new HTTPRequestManager (instantiated by
	// the HTTPServer at hit receive time)
	private String m_sSystemHostName;
	private long request_id;
	private Socket m_sock;
	private int m_iHTTPPort;
	private boolean m_bSecure;
	private boolean m_bDataPosted=false;
	private float m_fHTTPVersion=1;
	private int m_iConnectionsLeft = 1;
	private int m_iServerTimeout=1;
	private String m_sClientIPAddress = null;
	private String m_sClientHostName = null;

	private ByteStreamReader m_is;
	private BufferedOutputStream m_os;
	private boolean m_bIsWidgetRequest=false;
	private boolean m_bCloseConnection = false;

	private String m_http_request_line;  // Stores the GET/POST request line
	private String m_NewLocation; //used when processing the request
	private pmaAddInStatusLine m_pStatus;
	private String m_sRequestedHost="";
	private String m_sBaseRef="";
	private long m_lStart = System.currentTimeMillis();
	//private java.util.Date m_dtStart = new java.util.Date(); //timestamp object creation so we can work out transaction time
	private ArrayList m_environment_lines = new ArrayList(); // Stores all other data lines sent to us
	//instance of any running action
	private ActionRunnerInterface m_action = null;

	private int m_iInboundSize=0;
	private String m_sInboundMethod="";
	private String m_sInboundPath="";
	private TimeZone m_tzGMT = TimeZone.getTimeZone("GMT");
	private String m_sHTTPVersion;
	private String m_sDefaultCharSetLine=null; //"charset=us-ascii";
	private String m_sCharacterEncoding=null; //eg "ISO-8859-1"
	private boolean m_bSendSessionCookie=false;
	private String m_sIfNoneMatch;
	private boolean m_bAllowByteRangeServing = true;
	private long m_lUnique=0;
	private static final String MIME_START = "---------------9696"; //+ ""+pmaSystem.getBuild();

	private boolean m_bShouldDisableBasicAuth = false;
	//return codes - use w3c http return codes
	public final static int RET_OK = 200;
	public final static int RET_PARTIAL_CONTENT = 206;


	/*
>   Status 301 and 302 redirects
> 	are supposed to be issued with the same method as
> 	the original request.  Broken behaviour in some
> 	browsers resulted in a lot or content authors who
> 	didn't read the specs using 302 to redirect to
> 	a confirmation page.  As such redirects to GET were
> 	clearly needed, status 303 was added to cover them,
> 	in an early draft of HTTP 1.1 (if you just return
> 	Location:, but no Status: in a CGI script, you get
> 	a 302 redirect, or an internal redirect).
	 */
	public final static int RET_DO_NOTHING=-1;
	public final static int RET_SEEOTHER = 302; //reply to use to a post
	public final static int RET_BADREQUEST = 400;
	public final static int RET_UNAUTHORIZED = 401;
	public final static int RET_FORBIDDEN = 403;
	public final static int RET_FILENOTFOUND = 404;
	public final static int RET_LENGTHREQUIRED = 411;
	public final static int RET_INTERNALSERVERERROR = 500;
	public final static int RET_UNAVAILABLE = 503;
	public final static int RET_NOT_MODIFIED = 304;
	private String m_sHTTPURLPrefix="http";
	//used as a failsafe. If some bad interal error occurs, ensures we don't get stuck in an infinite loop.
	private boolean m_bIsInErrorState = false;

	/**
	 * constructor used by HTTPServer
	 */
	HTTPRequestManager(SystemContext paramSystem, HTTPServer server, Socket s, long rq_id, boolean bSecure, String szHostName)
	{
		m_bSecure = bSecure;
		m_http_server = server;
		m_sock = s;

		request_id = rq_id;
		m_pSystem = paramSystem; //this is shared
		if(m_bSecure) m_sHTTPURLPrefix="https";
		try{ m_iHTTPPort = m_sock.getLocalPort(); } catch(Exception e){}
		m_iConnectionsLeft = m_http_server.getMaxRequestsPerConnection();
		m_iServerTimeout = (m_http_server.iHTTPPortTimeout/1000);
		if(m_iServerTimeout<=0) m_iServerTimeout=1;      
		//System.out.println(request_id + " constructor()" );
		m_sSystemHostName = szHostName;
		m_bAllowByteRangeServing = m_http_server.serverAllowsByteServing();

		m_bShouldDisableBasicAuth = m_http_server.shouldDisableBasicAuth();
	}


	/**
	 * determines if the connection is SSL or not.
	 * @return
	 */
	public boolean isSecureConnection()
	{
		return m_bSecure;
	}

	public void setSessionPrincipal(Principal principal) 
	{
		m_principal = principal;		
	}


	/**
	 * Allows others to determine if the user posted data to the server or not.
	 * Used by HTMLDocument.createDocumentItems() to determine if we should set 
	 * the default values for fields or not.
	 */
	public boolean isDataPosted()
	{
		return m_bDataPosted;
	}


	/**
	 * Invoked by the HTTPServer for each request.  Do not use publicly.
	 */
	public void run()
	{
		try{ m_sock.setTcpNoDelay(true); }catch(Exception t){}
		//System.out.println(request_id + " run()" );      
		int iCount=0;
		//pSystem.pErr.doInformation("Handling HTTP request " + request_id + " from " + m_sock, this);
		m_pSystem.doDebug(pmaLog.DEBUGLEVEL_FULL, "run()", this);
		m_pStatus = m_http_server.createStatusLine(' ' + "HTTPreq");
		m_pStatus.setStatus("Processing request #" + request_id + " from " + m_sock.getInetAddress().getHostAddress());
		// if setup m_is successful, hand it off
		if (doSetup())
		{                
			//reset the socket timeout as we try to read more headers
			try{ m_sock.setSoTimeout(m_http_server.iHTTPPortTimeout); }catch(Exception e){}

			boolean bOK=true;
			while(bOK && m_pSystem.isSystemRunning()) //break thread if we are shutting down.
			{				
				m_http_server.incrementStatistic(HTTP.STATISTIC_KEY_HITSPERMINUTE, 1);
				m_http_server.incrementStatistic(HTTP.STATISTIC_KEY_HITSPERHOUR, 1);
				m_http_server.incrementStatistic(HTTP.STATISTIC_KEY_TOTALHITS, 1);
				m_bSendSessionCookie=false;
				iCount++;
				if(iCount==m_http_server.getMaxRequestsPerConnection()) m_bCloseConnection = true;
				//try to read the first line
				try
				{
					m_http_request_line = m_is.readLine();
					if(m_http_request_line==null || m_http_request_line.length()==0 || !m_pSystem.isSystemRunning()) break;
				}
				catch(Exception g) { break; }
				m_iConnectionsLeft--;

				m_bDataPosted = false;
				//System.out.println(request_id + " here" );
				//System.out.println(request_id + "_request: " + iCount + " [" + m_http_request_line + "] close="+m_bCloseConnection);
				long lStartTrans = System.currentTimeMillis();
				bOK = processRequest();
				long lTransTimeMS = System.currentTimeMillis() - lStartTrans;
				if(m_http_server.isDebug())
					m_pSystem.doDebug(pmaLog.DEBUGLEVEL_NONE, m_http_request_line + " " + lTransTimeMS + "ms", this);            

				if(m_bCloseConnection) bOK=false;
				if(m_pSession!=null) m_pSession.setSynchAcrossCluster(true);
				//System.out.println(request_id + "_request: " + iCount + " done [" + m_http_request_line + "]");
				try{ m_os.flush(); } catch(Exception j){}

				if(bOK) //for performance, don't bother checking if we're gonna drop the connection anyway
				{
					if(iCount>=m_http_server.getMaxRequestsPerConnection()) bOK=false;
				}
			}
		}

		//System.out.println(request_id + " finish" );
		doCleanup();
		m_http_server.removeStatusLine(m_pStatus);

	}

	/**
	 * tidy up the loose ends
	 */
	protected void finalize()
	{
		doCleanup();
		if(m_pStatus!=null) m_http_server.removeStatusLine(m_pStatus);
	}


	/**
	 * Sets up the request
	 */
	private boolean doSetup()
	{
		m_pSystem.doDebug(pmaLog.DEBUGLEVEL_FULL, "doSetup()", this);
		if(!openStreams())
		{
			return (false);
		}

		/*OutputStream m_sock_output_stream;
      try
      {
          m_sock_output_stream = m_sock.getOutputStream();
      }
      catch (IOException ioe)
      {
        m_pSystem.doError("HTTPRequest.GetOutputStream", new String[]{String.valueOf(request_id),ioe.getMessage()}, this);
        return (false);
      }*/

		return (true);
	}


	// create a BufferedReader and a BufferedWriter from the client m_socket
	private boolean openStreams()
	{
		m_pSystem.doDebug(pmaLog.DEBUGLEVEL_FULL, "openStreams()", this);
		try
		{
			InputStream input = m_sock.getInputStream();
			//VERY important to specify the correct charset!!
			//this took me 2 days to work out why I couldn't upload gif/binary files!
			//ISO-8859-1 as specified in the http1.1 w3c doco
			m_is = new ByteStreamReader(input, 8192, HTTP.DEFAULT_CHAR_ENCODING); 
			//m_is = new BufferedReader(new InputStreamReader(input, "ISO-8859-1"), 24576);

			OutputStream output_stream = m_sock.getOutputStream();
			m_os = new BufferedOutputStream(output_stream, 8192); //BJU 4/5/05 changed to 8k buffer
		}
		catch (IOException ioe)
		{
			m_pSystem.doError("HTTPRequest.BufferedStream", new String[]{String.valueOf(request_id),ioe.getMessage()}, this);
			return (false);
		}

		return (true);
	}


	/**
	 * Handles the http request. returns true to keep the connection open, false to close the
	 * connection after the current request.
	 */
	private boolean processRequest()
	{
		m_lStart = System.currentTimeMillis(); //reset the timer
		SessionContext sessCtx=null;
		try
		{
			m_environment_lines.clear();
			int iMaxURI = m_http_server.getMaxURI();
			if(iMaxURI>0 && m_http_request_line.length()>iMaxURI) 
			{            
				sendError(414, "Invalid Request, URI too long", "text/plain", ("Invalid Request, URI too long. " + m_http_request_line.length() + " > " + iMaxURI).getBytes());
				return false; //bodgy URI, bail out
			}
			m_pSystem.doDebug(pmaLog.DEBUGLEVEL_STANDARD, "REQUEST: '%s'", new String[]{m_http_request_line}, this);
			//System.out.println( request_id + " "+m_http_request_line);
			// Now parse the request line and decide what to do
			m_sHTTPVersion = "HTTP/1.0";
			m_sInboundPath="";
			int iPos = m_http_request_line.indexOf(' ');
			if(iPos>=0)
			{
				m_sInboundMethod = m_http_request_line.substring(0, iPos);
				m_sInboundPath = m_http_request_line.substring(iPos+1, m_http_request_line.length());
			}
			iPos = m_sInboundPath.indexOf(' ');
			if(iPos>=0)
			{
				m_sHTTPVersion = m_sInboundPath.substring(iPos+1, m_sInboundPath.length());
				m_sInboundPath = m_sInboundPath.substring(0, iPos);
			}   
			m_bIsWidgetRequest = isWidgetRequest(m_sInboundPath.toLowerCase());

			//long lEnd = System.currentTimeMillis() - lStart;
			//System.out.println(lEnd+"ms");

			// Read lines until we get a blank one (a single blank
			// line marks the end of request lines)
			String environment_line;
			environment_line = m_is.readLine();
			while(environment_line!=null && environment_line.length()!=0)
			{
				// Add the new environment data line to our Vector of them
				m_environment_lines.add(environment_line);
				environment_line = m_is.readLine();
				//System.out.println("["+environment_line+"]");
			}
			//for debug!!
			//dumpHeaders(m_environment_lines);
			m_sIfNoneMatch = Util.getMIMELine(m_environment_lines, "If-None-Match");

			//if the client says close the connection, then close it.
			String szCloseConnection = Util.getMIMELine(m_environment_lines, "Connection");
			if(szCloseConnection!=null && szCloseConnection.equalsIgnoreCase("close")) 
				m_bCloseConnection=true;
			else
			{
				if(szCloseConnection==null && m_sHTTPVersion.equals("HTTP/1.0")) m_bCloseConnection=true;
			}

			//if(m_http_server.m_bLogInbound) m_iInboundSize = HTTPServer.getInboundRequestSize(m_http_request_line, m_environment_lines);
			m_iInboundSize = HTTPServer.getInboundRequestSize(m_http_request_line, m_environment_lines);
			m_sRequestedHost = getHost(m_environment_lines);
			if(m_sRequestedHost==null) m_sRequestedHost="";
			m_sBaseRef = makeBaseRef(m_sRequestedHost, m_sInboundPath);
			String sSessionID="";

			//HTTP Header processing here....
			if(customHTTPHeaderProcessing())
			{
				return true;
			}

			// Get the HTTP version string that the client sent in, if any
			try
			{
				String version = m_sHTTPVersion;
				iPos = m_sHTTPVersion.lastIndexOf('/');
				if(iPos>=0) version = m_sHTTPVersion.substring(iPos+1, version.length());
				m_fHTTPVersion = Float.parseFloat(version);
			}
			catch(Exception k){ m_fHTTPVersion=1; }

			//SessionContext m_pSession;
			//Determine if a session cookie exists        
			sSessionID = HTTPServer.getCurrentSessionID(null, m_environment_lines);
			if(sSessionID.length()==0)//if no session cookie create one
			{
				sessCtx = getSession(m_environment_lines);
				m_bSendSessionCookie=true;
			}
			else //get existing
			{
				sessCtx = m_pSystem.getSession(sSessionID);
				//check if session has expired
				if(sessCtx == null) 
				{
					sessCtx = getSession(m_environment_lines);
					m_bSendSessionCookie=true;
				}
			}

			//session limit reached... so abort
			if(sessCtx == null)
			{
				m_pSystem.doError("HTTPRequest.SessionLimitReached", new String[]{m_pSystem.getSessionCount()+""}, this);
				ArrayList extra_headers = new ArrayList();
				String szRedirect = m_http_server.m_sHTTPMaxSessionRedirect;
				if(szRedirect.indexOf("://")>0 && szRedirect.toLowerCase().startsWith("http"))
				{
					//302 redirect to another host
					extra_headers.add("Location: " + szRedirect);
					sendHTTPResponse(RET_SEEOTHER, /*"Redirected to: <a href=\"" + szRedirect + "\">" + szRedirect + "</a>"*/"Moved", extra_headers, HTTP_VERSION, "text/html", null);
				}
				else
				{
					if(szRedirect.length()==0)
					{
						//show an error 503 service unavailable
						sendHTTPResponse(RET_UNAVAILABLE, "TOO MANY SESSIONS - try again later", extra_headers, HTTP_VERSION,
								"text/html", null);
					}
					else
					{
						//serve off the filesystem
						serveLocalFile(szRedirect);
					}
				}
				return true;
			}//if session==null
			m_pSession = new HTTPSessionContext(this, m_pSystem, sessCtx, new RequestPath(m_sInboundPath));

			m_pSession.setLastTransactionTime();			
			if(!m_pSession.isLoggedIn()) loginSessionByHTTPHeader();
			//if(m_bIsWidgetRequest) m_pSystem.doDebug(0, "processRequest() H " + m_http_request_line + " " + (System.currentTimeMillis()-m_lStart) + "ms", this);

			//System.out.println("m_sInboundPath:"+m_sInboundPath);
			if(m_sInboundPath.toLowerCase().indexOf("&logout") > 0)
			{
				m_pSystem.doInformation("HTTPRequest.UserLogout", this);
				m_pSession.clearSession();
				m_bSendSessionCookie = true;
				m_bCloseConnection = true;
				m_pSession.setSynchAcrossCluster(true);
			}

			m_pStatus.setStatus(m_pSession.getUserNameAbbreviated() + ' ' + m_sInboundPath);


			boolean bRequestProcessed=false;
			if(!bRequestProcessed && (m_sInboundMethod.equalsIgnoreCase("GET") || m_sInboundMethod.equalsIgnoreCase("HEAD")))
			{
				doGet(null, m_sInboundPath, false, true);
				bRequestProcessed=true;
			}
			if(!bRequestProcessed && m_sInboundMethod.equalsIgnoreCase("POST")) 
			{
				m_bDataPosted = true;
				doPost(m_sInboundPath);
				bRequestProcessed=true;
			}
			//treat as post, CardDav etc extensively use OPTIONS
			//allow other types eg PROPFIND, SEARCH, PUT
			String sAllowMethods[] = m_http_server.getAllowedHTTPMethods();
			if(!bRequestProcessed && isInList(m_sInboundMethod, sAllowMethods)) //m_sInboundMethod.equalsIgnoreCase("OPTIONS")) 
			{	
				//sendOptions(m_sInboundPath);
				//bRequestProcessed=true;
				m_bDataPosted = true;
				doPost(m_sInboundPath);
				bRequestProcessed=true;
			}
			
			//update in case the action did some login
			m_pStatus.setStatus(m_pSession.getUserNameAbbreviated() + ' ' + m_sInboundPath);

			if(!bRequestProcessed) //request not supported eg SEARCH
			{     
				//we are getting a pile of things like PROPFIND in the log :-(
				//maybe we add a config variable to decide whether we write these 'bad' requests
				//to the log??
				//m_pSystem.doError("HTTPRequest.BasicError", new String[]{"Unsupported: "+m_http_request_line}, this);
				sendHTTPResponse(501, m_sInboundMethod + " is not supported by this server", null, HTTP_VERSION,	"text/html", null);
				return false; //close connection, something wierd here.....
			}

		}//try
		catch (Exception ioe)
		{
			String szErr = ioe.getMessage();
			if(szErr==null) szErr = ioe.toString();

			if(m_http_request_line==null) m_http_request_line="";
			if(m_http_request_line.length()>0)
			{
				char c = m_http_request_line.charAt(0);
				if(!(c>=32 && c<=126)) m_http_request_line = "";
			}

			m_pSystem.doError("HTTPRequest.BasicError", new String[]{szErr, m_http_request_line}, this);
			StackTraceElement ste[] = ioe.getStackTrace();
			if(ste.length>0 && m_http_request_line.length()>0) //ignore requests that are bodgy else we get double errors
				//m_pSystem.doDebug(0, "Class=" + ste[0].getClassName() + " Method=" + ste[0].getMethodName() + " Line=" + ste[0].getLineNumber(), this);
				Util.logStackTrace(ioe, m_pSystem, STACKTRACE_DEPTH);
			sendInternalServerError();
			ioe.printStackTrace();
			return false;
		}
		return true;
	}

	/**
	 * 
	 * @param sMethod
	 * @param sAllowMethods
	 * @return
	 */
	private boolean isInList(String sMethod, String[] sAllowMethods) 
	{
		if(sMethod==null || sAllowMethods==null || sAllowMethods.length==0) return false;
		for(int i=0; i<sAllowMethods.length; i++)
		{
			if(sMethod.equalsIgnoreCase(sAllowMethods[i])) return true;
		}

		return false;
	}


	/**
	 * Tries to log in the session based on the info in the "Authorization:" header
	 */
	private void loginSessionByHTTPHeader()
	{

		if(m_pSession.isLoggedIn()) return;

		if(!m_bShouldDisableBasicAuth)
		{
			String sAuth = Util.getMIMELine(m_environment_lines, "Authorization");
			if(sAuth!=null) //try to log in the session....
			{
				//System.out.println(sAuth);
				//sAuth may be "Basic YnVwc31uOnJ1c3NpYQ==" or just "YnVwc31uOnJ1c3NpYQ=="
				int iSpace = sAuth.indexOf(' ');
				if(iSpace>=0) sAuth = sAuth.substring(iSpace+1);        
				String sUserNamePass = new String(Util.base64Decode(sAuth));
				int iPos = sUserNamePass.indexOf(':');
				if(iPos>=0)
				{
					String sUser = sUserNamePass.substring(0, iPos);
					String sPass = sUserNamePass.substring(iPos+1, sUserNamePass.length());
					//m_pSystem.doDebug(0, "loginSessionByHTTPHeader() A " + (System.currentTimeMillis()-m_lStart) + "ms", this);
					if(m_pSystem.loginSession(m_pSession.getSessionContext(), sUser, sPass, m_sInboundPath))
					{
						m_http_server.incrementStatistic(HTTP.STATISTIC_KEY_LOGINSPERHOUR, 1);
					}
					//m_pSystem.doDebug(0, "loginSessionByHTTPHeader() B " + (System.currentTimeMillis()-m_lStart) + "ms", this);
					m_bSendSessionCookie=true;
					return;
				}
			}
		}

		//Assume all cookies are on the same line "name=val; name=val;"
		String sCookies = Util.getMIMELine(m_environment_lines, "Cookie");
		if(sCookies!=null)
		{
			//System.out.println("COOKIES:"+sCookies);
			//Look for LtpaToken=
			//There may be multiple cookies, so send the last one....
			int iPos = sCookies.lastIndexOf(LTPAToken.LTPACOOKIENAME);
			if(iPos>=0)
			{
				String sLTPA = sCookies.substring(iPos+LTPAToken.LTPACOOKIENAME.length()+1);
				iPos = sLTPA.indexOf(';');
				if(iPos>0) sLTPA = sLTPA.substring(0, iPos);
				try
				{
					byte bufSecret[] = makeWebSSOSecret();
					LTPAToken ltpatok = new LTPAToken(sLTPA, bufSecret, null); //default to cp850
					if(!ltpatok.hasExpired() && !ltpatok.getUserName().equals(pmaSession.ANONYMOUS_USER))
					{                      
						//System.out.println("["+sLTPA+"]");
						X500Name nmUser = new X500Name(ltpatok.getUserName());
						//System.out.println("ltpa token has not expired, logging in "+nmUser.getAbbreviatedName());
						m_pSession.setUserName(nmUser.getCanonicalName());
						m_pSession.setFirstName(nmUser.getFirstName());
						m_pSession.setLastName(nmUser.getLastName());
						m_pSystem.populateSession(m_pSession.getSessionContext(), m_sInboundPath);
						m_bSendSessionCookie = true;
						m_http_server.incrementStatistic(HTTP.STATISTIC_KEY_LOGINSPERHOUR, 1);
						//System.out.println(m_pSession.getFirstName() + " " + m_pSession.getLastName());
						return;
					}
					//else
					//    System.out.println("ltpa token expired on " + ltpatok.getExpiryDate());

				}
				catch(Exception y)
				{
					//System.out.println("invalid token");
				}
			}
		}
	}

	/**
	 * gets the timeout for a new Ltpa session 
	 */
	private Date getLtpaSessionExpiryDate()
	{
		Date dtSessionExpires = m_pSession.getSSOExpiryDate();
		if(dtSessionExpires!=null) return dtSessionExpires;
		int iTimeout = 24*60; //1 day
		java.util.Date dtReturn = puakma.util.Util.adjustDate(new java.util.Date(), 0, 0, 0, 0, iTimeout, 0);
		String sTimeout = m_pSystem.getSystemProperty("SessionTimeout");
		if(sTimeout!=null && sTimeout.length()>0)
		{
			try
			{ 
				iTimeout = Integer.parseInt(sTimeout);
				dtReturn = puakma.util.Util.adjustDate(new java.util.Date(), 0, 0, 0, 0, iTimeout, 0);
			}
			catch(Exception e){}
		}
		return dtReturn; 
	}

	/**
	 *
	 * @param m_environment_lines
	 * @return
	 */
	private SessionContext getSession(ArrayList m_environment_lines)
	{
		SessionContext sessCtx=null;



		InetAddress addr = null;
		String szIP = Util.getMIMELine(m_environment_lines, "X-Forwarded-For"); //did we come through a proxy?
		m_sClientIPAddress = szIP;      
		if(szIP==null)
		{
			addr = m_sock.getInetAddress();
			m_sClientIPAddress = addr.getHostAddress();
			m_sClientHostName = m_sClientIPAddress;
			if(m_http_server.canLookupHostName()) m_sClientHostName = addr.getHostName();
			//sessCtx = m_pSystem.createNewSession(addr, Util.getMIMELine(m_environment_lines, "User-Agent"));
		}
		else //is through a proxy. get the first entry in the list. If multiple proxies will be "1.2.3.4,5.6.7.8,11.33.22.44"
		{
			int iPos = szIP.indexOf(',');
			if(iPos>0) szIP = szIP.substring(0, iPos);

			try{ addr = InetAddress.getByName(szIP); } catch(Exception e){}
			m_sClientIPAddress = szIP;
			m_sClientHostName = m_sClientIPAddress;
			if(addr!=null && m_http_server.canLookupHostName()) m_sClientHostName = addr.getHostName();

		}

		sessCtx = m_pSystem.createNewSession(addr, Util.getMIMELine(m_environment_lines, "User-Agent"));
		if(m_principal!=null)
		{
			sessCtx.setLoginName(m_principal.getName());
			sessCtx.setUserName(m_principal.getName());
			sessCtx.setAuthenticatorUsed("TLS"); //?? or just blank?
		}
		sessCtx.setObjectChanged(true);
		sessCtx.setSynchAcrossCluster(true);
		pushClusterSession(sessCtx);
		return sessCtx;
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


	/**
	 * do custom URL preprocesing if any. return true if a custom processor wrote to the client
	 */
	private boolean customHTTPHeaderProcessing()
	{
		String sProcessors[] = m_http_server.getCustomHeaderProcessors();
		if(sProcessors==null) return false;
		m_pSystem.doDebug(pmaLog.DEBUGLEVEL_FULL, "customHTTPHeaderProcessing()", this);

		SessionContext sess = null;
		if(m_pSession!=null) sess = m_pSession.getSessionContext();
		for(int i=0; i<sProcessors.length; i++)
		{
			try
			{
				Class c = Class.forName(sProcessors[i]);
				if(m_http_server.isDebug())
					m_pSystem.doDebug(pmaLog.DEBUGLEVEL_NONE, " HTTPHeaderProcessor: " + c.getName(), this);
				Object o = c.newInstance();
				HTTPHeaderProcessor hp = (HTTPHeaderProcessor)o;
				hp.init(m_http_server.getAddIn(), m_pSystem, sess, m_sInboundMethod, m_sInboundPath, m_sHTTPVersion, m_environment_lines, m_bSecure, m_sock.getInetAddress().getHostAddress(), m_is);
				boolean bBreak = hp.execute();
				m_sInboundMethod = hp.getHTTPMethod();
				m_sInboundPath = hp.getHTTPURI();
				m_sHTTPVersion = hp.getHTTPVersion();
				if(hp.shouldReplyToClient())
				{                                        
					sendHTTPResponse(hp.getReturnCode(), hp.getReturnMessage(), hp.getReturnHeaders(), HTTP_VERSION, hp.getReturnMimeType(), hp.getReturnBuffer());
					return true;
				}
				if(bBreak) break;
			}
			catch(Exception e)
			{
				//e.printStackTrace();
				m_pSystem.doError("customHTTPHeaderProcessing() "+e.toString(), this);
				puakma.util.Util.logStackTrace(e, m_pSystem, STACKTRACE_DEPTH);
				/*
                StackTraceElement ste[] = e.getStackTrace();
                if(ste.length>0)
                    m_pSystem.doError("Class=" + ste[0].getClassName() + " Method=" + ste[0].getMethodName() + " Line=" + ste[0].getLineNumber(), this);
				 */
			}
		}
		return false;
	}






	/**
	 * Determine the requested host from the headers.
	 * @param v
	 * @return
	 */
	private String getHost(ArrayList vHeaders)
	{
		String sReturn = Util.getMIMELine(vHeaders, "X-Forwarded-Host"); //did we come through a proxy?
		if(sReturn==null) return Util.getMIMELine(vHeaders, "Host");

		int iPos = sReturn.indexOf(',');
		if(iPos>=0) return sReturn.substring(0, iPos);
		return sReturn;
	}


	/**
	 *
	 * @param doc
	 */
	private void setUpCookies(HTMLDocument doc)
	{
		String sPartial;
		String sCookie = Util.getMIMELine(m_environment_lines, "Cookie");
		//System.out.println(szCookie);
		//szCookie += "; name2=value2; name3=value3";

		if(sCookie==null || doc==null) return; //cookie will be null on first connect
		int iPos, iEnd;
		iPos = sCookie.indexOf(';');
		if(iPos<0 && sCookie.length()>0) iPos = sCookie.length();
		while(iPos>=0)
		{
			if(iPos==0)
			{
				iEnd = sCookie.length();
				sPartial = sCookie.substring(iPos, iEnd);
			}
			else
			{
				iEnd = iPos+1;
				if(iEnd>sCookie.length()) iEnd = sCookie.length();
				sPartial = sCookie.substring(0, iPos);
			}

			sCookie = sCookie.substring(iEnd, sCookie.length());
			Cookie c = new Cookie(sPartial);
			//if not the session cookie, add it...
			if(!c.getName().equalsIgnoreCase(HTTPServer.SESSIONID_LABEL)) doc.addCookie(c);
			iPos = sCookie.indexOf(';');
			if(iPos<0 && sCookie.length()>0) iPos = 0;
		}
	}



	/**
	 * Called for a POST
	 *
	 */
	private void doPost(String document_path)
	{
		//m_pSystem.doDebug(0, "doPost() " + document_path +" " + (System.currentTimeMillis()-m_lStart) + "ms", this);

		m_pSystem.doDebug(pmaLog.DEBUGLEVEL_FULL, "doPost()", this);
		boolean bForceClientPull=false;
		HTMLDocument docIn=null;
		String szContentType =  Util.getMIMELine(m_environment_lines, "Content-Type");
		if(szContentType==null) szContentType = "multipart/form-data"; //field1=5&field2=7 etc
		String szBoundary = Util.getMessageBoundary(szContentType);
		szContentType = getContentType(szContentType);
		String szContentLength = Util.getMIMELine(m_environment_lines, "Content-Length");
		long lLength = 0;

		try{
			lLength = Integer.parseInt(puakma.util.Util.trimSpaces(szContentLength));
		}catch(Exception nfe){lLength=-1;}
		if(lLength<0) 
		{
			//dumpHeaders(m_environment_lines);
			sendHTTPResponse(RET_LENGTHREQUIRED, "Content length required", null, HTTP_VERSION, null, null);
			return;
		}

		//check if there is an max POST size and if so enforce it
		long lMaxUpload = m_http_server.getMaxUploadBytes();
		if(lMaxUpload>=0 && lLength>lMaxUpload)
		{
			String sMessage = pmaLog.parseMessage(m_pSystem.getSystemMessageString("HTTPRequest.ContentLengthLimit"), new String[]{String.valueOf(lLength), String.valueOf(lMaxUpload), m_sInboundPath });
			m_pSystem.doError(sMessage, this);
			sendHTTPResponse(RET_BADREQUEST, "Bad request", null, HTTP_VERSION, null, sMessage.getBytes());
			return;
		}

		RequestPath rPath = new RequestPath(document_path);

		//check we might be posting to a cgi program
		if(!rPath.isPuakmaApplication() && document_path.toLowerCase().startsWith("/cgi-bin/"))
		{
			serveLocalFile(document_path);
			return;
		}

		String sDisabled = getAppParam(m_pSession, "DisableApp", rPath.Group, rPath.Application);
		if(sDisabled!=null && sDisabled.equals("1"))
		{
			HTMLDocument docErr = docIn;
			if(docErr==null)
			{
				docErr = new HTMLDocument(m_pSession);
				docErr.rPath = rPath;
			}          
			sendPuakmaError(RET_FORBIDDEN, docErr);
			return;
		}
		//m_pSystem.doDebug(0, "doPost() B " + document_path +" " + (System.currentTimeMillis()-m_lStart) + "ms", this);

		String sLowAction = rPath.Action.toLowerCase();
		if(sLowAction.equals(DesignElement.PARAMETER_SAVEPAGE) || sLowAction.equals(DesignElement.PARAMETER_OPENACTION)
				|| m_bIsWidgetRequest || sLowAction.length()==0)
		{
			String sCharSet = getInboundCharset(rPath);
			if(szContentType.equals(Document.CONTENT_MULTI))
				docIn = new HTMLDocument(m_pSystem, m_pSession, rPath.DesignElementName, m_is, szContentType, szBoundary, lLength, sCharSet);
			else
				docIn = new HTMLDocument(m_pSystem, m_pSession, rPath.DesignElementName, m_is, szContentType, lLength, sCharSet);

			//m_pSystem.doDebug(0, "doPost() C " + document_path +" " + (System.currentTimeMillis()-m_lStart) + "ms", this);
			if(!docIn.isDocumentCreatedOK())
			{
				m_bCloseConnection = true;
				//send a reply to the client
				sendPuakmaError(400, docIn);              
				return;
			}
			docIn.setParameters(rPath.Parameters);
			docIn.setCharacterEncoding(m_sCharacterEncoding);
			bForceClientPull=false;
			if(docIn.hasItem(Document.PAGE_LOGIN_ITEM))
			{
				//attempt to login in the session with new credentials
				if(!docIn.hasItem(Document.PAGE_LOGIN_BYPASS))
				{
					if(m_pSystem.loginSession(m_pSession.getSessionContext(), docIn)) 
					{
						bForceClientPull=true;
						m_http_server.incrementStatistic(HTTP.STATISTIC_KEY_LOGINSPERHOUR, 1);
					}
				}
				m_bSendSessionCookie=true;
			}
		}//! starts with 'open'
		doGet(docIn, document_path, bForceClientPull, false);
	}

	/**
	 *
	 */
	private String getContentType(String sContentString)
	{
		//m_pSystem.doDebug(pmaLog.DEBUGLEVEL_FULL, "getContentType()", this);
		int iPos = sContentString.indexOf(';');
		if(iPos>=0) return sContentString.substring(0, iPos);
		return sContentString;
	}



	/**
	 * This method serves a file right out into the m_socket.  If it cannot
	 * open and read the file, it will send a "Not Found" error page to the
	 * client.
	 *                                                              <BR><BR>
	 * @param document_path     the request URI to what the browser wants,
	 *                          it probably looks something like
	 *                          "/docs/index.pma/something?action".
	 * @param http_version      the version of HTTP that we got from the
	 *                          browser, if any.  We need this in case we're
	 *                          sending an error page so that we know if we
	 *                          need to send HTTP headers along with the
	 *                          error or not.
	 * @param environment_lines a Vector of Strings, each one containing an
	 *                          HTTP request header line received from the
	 *                          client.
	 */
	private void doGet(HTMLDocument doc, String document_path, boolean bForceClientPull, boolean bGet)
	{

		if(document_path==null) document_path="/";
		m_pSystem.doDebug(pmaLog.DEBUGLEVEL_FULL, "doGet() " + document_path, this);
		//if (m_http_response_sent) return;

		if(document_path.length()==1 && document_path.charAt(0)=='/')
		{
			String szDefaultURL = m_http_server.getDefaultHostURL(m_sRequestedHost);
			if(szDefaultURL == null) szDefaultURL = m_pSystem.getSystemProperty("HTTPDefaultURL");
			if(szDefaultURL != null)
			{
				if(szDefaultURL.charAt(0)=='/')
					document_path = szDefaultURL;
				else
				{
					String sURL = szDefaultURL.toLowerCase();
					if(sURL.startsWith("http:") || sURL.startsWith("https:") || sURL.startsWith("ftp:")) //full 302 redirect
					{
						ArrayList extra_headers = new ArrayList();
						extra_headers.add("Location: " + szDefaultURL);
						sendHTTPResponse(RET_SEEOTHER, "Moved", extra_headers, HTTP_VERSION, null, null);
						return;
					}
					else
						document_path += szDefaultURL;
				}
			}//if
			else
			{
				document_path += "index.html";
			}
		}//if

		//check for redirection based on URI eg http://some.host.com/booster/
		String szRedirectURL = m_http_server.getDefaultHostURL(document_path);
		if(szRedirectURL!=null)
		{
			ArrayList extra_headers = new ArrayList();
			String sURL = szRedirectURL.toLowerCase();
			if(sURL.startsWith("http:") || sURL.startsWith("https:") || sURL.startsWith("ftp:")) //full 302 redirect
			{          
				extra_headers.add("Location: " + szRedirectURL);
			}
			else //path only specified
			{
				String sProto = null;
				if(m_bSecure) sProto = "https://"; else sProto = "http://"; //assume must be http of some sort
				extra_headers.add("Location: " + sProto + m_sRequestedHost + szRedirectURL);
			}          
			sendHTTPResponse(RET_SEEOTHER, "Moved", extra_headers, HTTP_VERSION, null, null);
			return;        
		}

		RequestPath rPath = new RequestPath(document_path);
		m_pSession.setRequestPath(rPath); //reset in case we asked for a "/" and it was aliased

		//check there m_is a .pma in the path || it m_is a local file we are requesting
		if( (document_path.length()==1 && document_path.charAt(0)=='/') || !rPath.isPuakmaApplication())
		{
			if(!document_path.equals("/"))
			{
				//serve local file....
				serveLocalFile(document_path);
			}
			else
			{
				//if we get here the admin has not set a default URL, so show not found error
				m_pSystem.doError("HTTPRequest.BadRequest", new String[]{document_path}, m_pSession);
				sendNotFoundError();
			}
			return;
		}

		String sDisabled = getAppParam(m_pSession, "DisableApp", rPath.Group, rPath.Application);
		if(sDisabled!=null && sDisabled.equals("1"))
		{        
			HTMLDocument docErr = doc;
			if(docErr==null)
			{
				docErr = new HTMLDocument(m_pSession);
				docErr.rPath = rPath;
			}          
			sendPuakmaError(RET_FORBIDDEN, docErr);                    
			return;
		}

		//we remove &logout so we don't get caught in an infinite loop
		//cause the login page will redirect you to /../.?openpage&logout
		rPath.removeParameter("&logout");
		document_path = rPath.getFullPath();
		//now check we are asking for something sensible, ie /puakma.pma/page?OpenPage
		if(rPath.DesignElementName.length()==0) //eg ""
		{
			String szDefaultOpen = getAppParam(m_pSession, Document.APPPARAM_DEFAULTOPEN, rPath.Group, rPath.Application);
			if(szDefaultOpen != null && szDefaultOpen.length()!=0)
			{
				if(szDefaultOpen.charAt(0)=='/')
					document_path = rPath.getPathToApplication() + szDefaultOpen.substring(1, szDefaultOpen.length());
				else
					document_path = rPath.getPathToApplication() + '/' + szDefaultOpen.substring(0, szDefaultOpen.length());
				document_path += rPath.Parameters;
				rPath = new RequestPath(document_path);
				ArrayList extra_headers = new ArrayList();
				String sProto = null;
				if(m_bSecure) sProto = "https://"; else sProto = "http://"; //assume must be http of some sort
				extra_headers.add("Location: " + sProto + m_sRequestedHost + document_path);				
				sendHTTPResponse(RET_SEEOTHER, "Moved", extra_headers, HTTP_VERSION, "text/html", null);
				return;
			}
		}

		if(!m_bSecure)
		{
			String sForceSecureConnectionParam = getAppParam(m_pSession, Document.APPPARAM_FORCESECURECONNECTION, rPath.Group, rPath.Application);
			if(sForceSecureConnectionParam != null && sForceSecureConnectionParam.equals("1"))
			{
				m_pSystem.doDebug(pmaLog.DEBUGLEVEL_STANDARD, "Secure connection is required: '%s'", new String[]{document_path}, m_pSession);
				ArrayList extra_headers = new ArrayList();
				String sProto = "https://";
				extra_headers.add("Location: " + sProto + m_sRequestedHost + document_path);				
				sendHTTPResponse(RET_SEEOTHER, "SSL Required", extra_headers, HTTP_VERSION, "text/html", null);
				return;
			}
		}//!secure

		m_sCharacterEncoding = getAppParam(m_pSession, Document.APPPARAM_DEFAULTCHARSET, rPath.Group, rPath.Application);
		if(m_sCharacterEncoding!=null && m_sCharacterEncoding.length()>0) 
			m_sDefaultCharSetLine = "charset="+m_sCharacterEncoding;      
		else
			m_sDefaultCharSetLine = "charset=utf-8";

		m_pSystem.doDebug(pmaLog.DEBUGLEVEL_STANDARD, "Serving puakma file: '%s'", new String[]{document_path}, m_pSession);
		//***** PROCESS PUAKMA REQUEST ****

		HTMLDocument docHTML;
		if(doc==null)
		{
			docHTML = new HTMLDocument(m_pSession);
			docHTML.setParameters(rPath.Parameters);
		}
		else
			docHTML = doc;
		setUpCookies(docHTML);
		docHTML.setCharacterEncoding(m_sCharacterEncoding);
		docHTML.setHTTPFields(m_environment_lines);
		if(m_sClientIPAddress==null) m_sClientIPAddress = m_sock.getInetAddress().getHostAddress();
		docHTML.replaceItem("@Requestor-IP", m_sClientIPAddress);
		docHTML.replaceItem("@Peer-IP", m_sock.getInetAddress().getHostAddress());
		docHTML.replaceItem("@BaseRef", m_sBaseRef);
		docHTML.replaceItem("@Method", m_sInboundMethod);


		//Now cache the userroles for this Application if it is the first access of this app
		//HTTPServer.setUpUserRoles(m_pSystem, m_pSession.getSessionContext(), rPath);
		TornadoServerInstance tsi = TornadoServer.getInstance();
		TornadoApplication ta = tsi.getTornadoApplication(rPath.getPathToApplication());
		ta.setUpUserRoles(m_pSession.getSessionContext());
		//************

		//if we're only after business widgets, just do that and get out....
		if(m_bIsWidgetRequest)
		{
			doWidgetRequest(docHTML);
			return;
		}

		int iHTTPReplyCode = performRequest(document_path, docHTML, bGet, bForceClientPull, false);
		if(iHTTPReplyCode==HTTPRequestManager.RET_DO_NOTHING)
		{
			try{ m_os.flush(); } catch(Exception ioe){}
			return; //the action took care of everything
		}

		ArrayList extra_headers = new ArrayList();
		Vector vExtraHeaders = docHTML.getExtraHeaders();
		if(vExtraHeaders!=null)
		{
			for(int i=0; i<vExtraHeaders.size(); i++)
			{
				String sValue = (String)vExtraHeaders.get(i);
				extra_headers.add(sValue);
			}
		}

		//Check the programmer has wedged some extra stuff in the content-type field :-(
		//This was the old way of doing things...
		String sContentType = docHTML.getContentType();
		if(sContentType!=null && sContentType.indexOf('\r')>=0)
		{
			ArrayList vSplit = Util.splitString(sContentType, "\r\n");
			docHTML.setContentType((String) vSplit.get(0));
			for(int i=1; i<vSplit.size(); i++)
			{
				String sHeader = (String) vSplit.get(i);
				extra_headers.add(sHeader);
				//m_pSystem.doDebug(0, sHeader, this);
			}
		}

		/**
		 * I was going to change completely to ltpa tokens and use the cookie token as the session id BUT
		 * the token cookie changes when the user logs in. It will be better to try to log in the user automagically
		 * based on the ltpa token, when no puakma session id is specified.
		 */
		/* BJU if(m_bSendSessionCookie) 
		{         
			String sPath = getCookiePath();

			extra_headers.add(m_pSession.getCookieString(sPath, getCookieDomain()));

			LTPAToken ltpa = new LTPAToken();
			ltpa.setUserName(m_pSession.getUserName());          
			byte bufSecret[] = makeWebSSOSecret();          
			ltpa.setExpiryDate(getLtpaSessionExpiryDate());
			//this domain thing may not be right - what about multi-homed servers?? !!
			String sLtpaCookie = ltpa.getAsCookie(bufSecret, sPath, m_pSystem.getSystemProperty("WEBSSODomain"), false);
			//m_pSystem.doDebug(0, sLtpaCookie, this);
			extra_headers.add("Set-Cookie: "+sLtpaCookie);
			//System.out.println(sLtpaCookie);
		}*/
		boolean bNotModified = iHTTPReplyCode==HTTPRequestManager.RET_NOT_MODIFIED;
		docHTML.setCookiesInHTTPHeader(extra_headers); //copy from doc to arraylist
		if(docHTML.designObject!=null && !bNotModified) 
		{
			//don't bother sending a last modified or expires
			int iDesignType = docHTML.designObject.getDesignType(); 
			if(iDesignType == DesignElement.DESIGN_TYPE_RESOURCE)
			{           
				if(Util.getMIMELine(extra_headers, "Last-Modified")==null)
				{
					String sLastModified = "Last-Modified: " + Util.formatDate(docHTML.designObject.getLastModified(), LAST_MOD_DATE, Locale.UK, m_tzGMT);
					extra_headers.add(sLastModified);
				}
				if(Util.getMIMELine(extra_headers, "Expires")==null)
				{
					//set an expiry time one hour from now					
					String sExpires = "Expires: " + Util.formatDate(Util.adjustDate(new Date(), 0, 0, 0, 1, 0, 0), LAST_MOD_DATE, Locale.UK, m_tzGMT);					
					extra_headers.add(sExpires);
				}

			}
			if(iDesignType == DesignElement.DESIGN_TYPE_PAGE || 
					iDesignType == DesignElement.DESIGN_TYPE_ACTION)
			{   
				Date dtNow = new Date();
				String sDate = Util.formatDate(dtNow, LAST_MOD_DATE, Locale.UK, m_tzGMT);

				if(Util.getMIMELine(extra_headers, "Last-Modified")==null)
				{
					String sLastModified = "Last-Modified: " + sDate;
					extra_headers.add(sLastModified);
				}
				if(Util.getMIMELine(extra_headers, "Expires")==null)
				{
					String sExpires = "Expires: " + sDate;
					extra_headers.add(sExpires);
				}
			}
		}//last modified and expires block



		switch(iHTTPReplyCode)
		{
		case RET_OK:
			sendHTTPResponse(iHTTPReplyCode, "OK", extra_headers, HTTP_VERSION,
					docHTML.getContentType(), docHTML.getContent());
			break;
		case RET_NOT_MODIFIED:
			sendHTTPResponse(iHTTPReplyCode, "Not Modified", extra_headers, HTTP_VERSION,
					docHTML.getContentType(), null);
			break;
		case RET_SEEOTHER: //allow for relative paths, full incl. http://, and on server /path.pma
			String szLocation = m_NewLocation;
			if(szLocation.indexOf("://")<0)
			{
				if(szLocation.charAt(0)=='/') //docHTML.getItemValue("@Host")
					szLocation = m_sHTTPURLPrefix + "://" + m_sRequestedHost + szLocation;
				else
					szLocation = m_sHTTPURLPrefix + "://" + m_sRequestedHost + '/' + szLocation;
			}
			extra_headers.add("Location: " + szLocation);
			sendHTTPResponse(iHTTPReplyCode, "Moved", extra_headers, HTTP_VERSION, docHTML.getContentType(), docHTML.getContent());
			break;
		case RET_FILENOTFOUND:
			//sendNotFoundError(null);
			//System.out.println("sending not found. " + docHTML.getContentType() + " " + docHTML.getContentLength());
			sendPuakmaError(RET_FILENOTFOUND, docHTML);
			break;
		case RET_FORBIDDEN:
			/*sendHTTPResponse(RET_FORBIDDEN, "ACCESS IS NOT ALLOWED: " + m_pSession.getUserName(), extra_headers, HTTP_VERSION,
			              "text/html", null);*/
			sendPuakmaError(RET_FORBIDDEN, docHTML);
			break;
		default: //hopefully will never get here!
			/*sendHTTPResponse(500, "Our Oompa Loompas don't quite know what to make of that request", extra_headers, HTTP_VERSION,
			              "text/html", null);*/
			sendPuakmaError(500, docHTML);
			break;
		};
	}

	/**
	 * Return the path this cookie should apply to. Default is "/", otherwise if group level
	 * login is configured, use the group, eg "/dir1/"
	 */
	private String getCookiePath()
	{
		if(m_http_server.isGroupLevelCookie())
		{            
			if(m_sInboundPath==null || m_sInboundPath.length()<2 || m_sInboundPath.charAt(0)!='/') return "/";
			RequestPath rPath = new RequestPath(m_sInboundPath);
			if(rPath.Group==null || rPath.Group.length()==0) return "/";
			return '/'+rPath.Group+'/';
		}
		return "/";
	}

	/**
	 * get the currently configured SSO secret
	 */
	private byte[] makeWebSSOSecret()
	{
		String sBase64Secret = m_pSystem.getSystemProperty("WEBSSOSecretB64");
		if(sBase64Secret==null || sBase64Secret.length()==0) sBase64Secret = puakma.util.Util.base64Encode("secret".getBytes());
		byte bufSecret[] = puakma.util.Util.base64Decode(sBase64Secret);          
		return bufSecret;
	}

	/**
	 * For SSO, returns the domain name that this cookie applies to, eg .wnc.net.au
	 * @return
	 */
	private String getCookieDomain()
	{
		String sDomain = m_pSystem.getSystemProperty("HTTPDomain~"+m_sRequestedHost);
		//if(sDomain==null) return "";

		//return "; domain="+sDomain;
		return sDomain;
	}

	/**
	 *
	 * @param document_path
	 * @param docHTML
	 * @param bForceClientPull
	 * @return
	 */
	public int performRequest(String document_path, HTMLDocument docHTML, boolean bGet, boolean bForceClientPull, boolean bByPassSecurity)
	{
		//m_pSystem.doDebug(0, "performRequest() " + (System.currentTimeMillis()-m_lStart) + "ms", this);

		long lStart = System.currentTimeMillis();
		//guess that means we're after a page, action or something
		int RequestReturnCode = processDesignElementRequest(document_path, docHTML, bForceClientPull, bByPassSecurity);
		if(RequestReturnCode==RET_FILENOTFOUND || RequestReturnCode==RET_FORBIDDEN) return RequestReturnCode;

		if(docHTML.designObject!=null && docHTML.designObject.getDesignType()==DesignElement.DESIGN_TYPE_BUSINESSWIDGET)
		{
			//m_pSystem.doDebug(0, "performRequest() 2 " + (System.currentTimeMillis()-m_lStart) + "ms", this);

			//we have asked for a business widget without specifying ?WidgetXxxxxxxx
			doWidgetRequest(docHTML);
			return RET_DO_NOTHING;
		}


		if((RequestReturnCode!=RET_SEEOTHER || !bGet) && RequestReturnCode!=RET_NOT_MODIFIED)
		{
			docHTML.prepare();
			//System.out.println(docHTML.toString());


			ActionReturn act_return = doPreActionProcessing(docHTML, bGet);
			if(act_return!=null && act_return.HasStreamed) return RET_DO_NOTHING;
			if(act_return==null || ((act_return.RedirectTo==null || act_return.RedirectTo.length()==0) && act_return.bBuffer==null))
			{
				act_return = runActionOnDocument(docHTML, null, bGet);
				if(act_return!=null && act_return.HasStreamed) return RET_DO_NOTHING;
			}  

			long lActionTimeMS = System.currentTimeMillis() - lStart;
			docHTML.replaceItem("$ActionTimeMS", lActionTimeMS);

			if(act_return!=null && act_return.RedirectTo!=null && act_return.RedirectTo.length()!=0)
			{
				m_NewLocation = act_return.RedirectTo;
				RequestReturnCode=RET_SEEOTHER;
			}
			else
			{
				//if(act_return==null || act_return.sBuffer==null)
				if(act_return==null || act_return.bBuffer==null)
				{              
					if(docHTML.rPath.Action.equalsIgnoreCase(DesignElement.PARAMETER_READPAGE))
						docHTML.renderDocument(true, m_http_server.isDebug());
					else
					{
						//BU 12/2/05 removed to stop server spitting out raw class file when ?execute or similar
						//is passed on the command line.
						if((docHTML.designObject!=null && docHTML.designObject.getDesignType()==DesignElement.DESIGN_TYPE_ACTION) 
								|| docHTML.rPath.Action.equalsIgnoreCase(DesignElement.PARAMETER_OPENACTION))
						{
							//stop actions that do nothing from spitting out the raw class file
							docHTML.setContent(("Action Done: " + new java.util.Date()).getBytes());
							if(act_return==null || act_return.ContentType==null || act_return.ContentType.length()==0)
								docHTML.setContentType("text/plain");
							else
								docHTML.setContentType(act_return.ContentType);
						}
						else
							docHTML.renderDocument(false, m_http_server.isDebug());                
					}
				}
				else
				{					
					docHTML.setContent(act_return.bBuffer);
					docHTML.setContentType(act_return.ContentType);
				}
			}//no action redirect
		}// !see other

		return RequestReturnCode;
	}

	/**
	 * Runs the standard actions on a page
	 * @param docHTML
	 * @param bGet
	 */
	private ActionReturn doPreActionProcessing(HTMLDocument docHTML, boolean bGet)
	{
		//only run this on pages and actions
		//NOTE: This may need ot be changed in the future so it will run against resources as well!! configurable through another appparam?
		if(docHTML.designObject!=null && !(docHTML.designObject.getDesignType()==DesignElement.DESIGN_TYPE_ACTION || docHTML.designObject.getDesignType()==DesignElement.DESIGN_TYPE_PAGE)) return null;
		ActionReturn act_return=null;
		int iLoop=1;
		String sActionType = DesignElement.PARAMETER_OPENACTION;
		if(!bGet) sActionType = DesignElement.PARAMETER_SAVEACTION;

		String szPreAction = getAppParam(m_pSession, sActionType, docHTML.rPath.Group, docHTML.rPath.Application);
		while(szPreAction!=null)
		{
			act_return = runActionOnDocument(docHTML, szPreAction, bGet);
			//if this is a redirect, do it, other wise keep processing
			//this action type cannot write out
			if(act_return!=null && act_return.RedirectTo!=null && act_return.RedirectTo.length()>0) return act_return;
			if(act_return!=null && (act_return.bBuffer!=null || act_return.HasStreamed)) return act_return;
			szPreAction = getAppParam(m_pSession, sActionType+iLoop, docHTML.rPath.Group, docHTML.rPath.Application);
			if(szPreAction==null || szPreAction.length()<1) return null;
			iLoop++;
		}

		return act_return;
	}





	/**
	 * Runs the appropriate action against the document
	 */
	private ActionReturn runActionOnDocument(HTMLDocument doc, String sActionClass, boolean bOpenPage)
	{
		m_pSystem.doDebug(pmaLog.DEBUGLEVEL_FULL, "runActionOnDocument()", this);
		ActionReturn actionReturn = null;
		//String szRedirect;
		//StringBuilder sbOut=null;
		SystemContext SysCtx=null;
		ClassLoader ctx_cl = Thread.currentThread().getContextClassLoader();
		if(doc != null && doc.designObject != null)
		{
			try
			{
				if(sActionClass==null)
				{
					if(doc.designObject.getDesignType() == DesignElement.DESIGN_TYPE_ACTION)
						sActionClass = doc.designObject.getDesignName();
					else
					{
						if(bOpenPage)
							sActionClass = doc.designObject.getParameterValue(DesignElement.PARAMETER_OPENACTION);
						else
							sActionClass = doc.designObject.getParameterValue(DesignElement.PARAMETER_SAVEACTION);
					}
				}//szActionClass==null
				if(sActionClass!=null && sActionClass.length()!=0)
				{
					if(m_http_server.isDebug())
					{
						if(bOpenPage)
							m_pSystem.doDebug(0, "Running OpenAction: " + sActionClass, this);
						else
							m_pSystem.doDebug(0, "Running SaveAction: " + sActionClass, this);
					}  

					long lStart = System.currentTimeMillis();
					//BJU. Made shared so that classloader references are maintained.
					//ActionClassLoader aLoader= new ActionClassLoader(this, m_pSystem, m_pSession.getSessionContext(), doc.rPath.Group, doc.rPath.Application, szActionClass);            

					actionReturn = new ActionReturn();
					SysCtx = (SystemContext)m_pSystem.clone();
					//SessionContext SessCtx = (SessionContext)m_pSession.getSessionContext().clone(); //ditto with the session
					SessionContext SessCtx = (SessionContext)m_pSession.getSessionContext(); //BJU 9/4/08 I don't believe this needs to be cloned as per line above
					HTTPSessionContext httpSessCtx = new HTTPSessionContext(this, SysCtx, SessCtx, doc.rPath);
					SharedActionClassLoader aLoader = m_pSystem.getActionClassLoader(doc.rPath); //, DesignElement.DESIGN_TYPE_ACTION);
					Class runclass = aLoader.getActionClass(sActionClass, DesignElement.DESIGN_TYPE_ACTION);
					if(runclass==null) return actionReturn;            
					Object object = runclass.newInstance();					
					m_action = (ActionRunnerInterface)object;					
					m_action.init(httpSessCtx, doc, doc.rPath.Group, doc.rPath.Application);

					//setup the context classloader see http://www.javaworld.com/javaworld/javaqa/2003-06/01-qa-0606-load.html

					Thread.currentThread().setContextClassLoader(aLoader);
					actionReturn.RedirectTo = m_action.execute();
					Thread.currentThread().setContextClassLoader(ctx_cl);

					if(m_http_server.isDebug()) SysCtx.checkConnections(doc.rPath.getFullPath(), m_pSession.getSessionContext());
					//act_return.sBuffer = act.getStringBuilder();
					actionReturn.HasStreamed = m_action.hasStreamed();
					actionReturn.bBuffer = m_action.getByteBuffer();
					actionReturn.ContentType = m_action.getContentType(); 
					long lActionTimeMS = System.currentTimeMillis() - lStart;
					
					if(m_http_server.getSlowActionTimeLimitMS()>0 && lActionTimeMS>m_http_server.getSlowActionTimeLimitMS())
					{
						m_pSystem.doError("HTTPRequest.SlowAction", new String[]{sActionClass, doc.rPath.getFullPath(), String.valueOf(lActionTimeMS), String.valueOf(m_http_server.getSlowActionTimeLimitMS())}, this);
					}
					
					//System.out.println(szActionClass + " took " + lEnd + "ms");
				}
			}
			catch(java.lang.OutOfMemoryError ome)
			{
				Thread.currentThread().setContextClassLoader(ctx_cl);
				actionReturn = null; //in case there is any buffer allocated, remove all allocated memory
				//request a gc then give the JVM some time to garbage collect
				//System.gc();
				try{Thread.sleep(5000);} catch(Exception e){}
				ome.printStackTrace();
				m_pSystem.doError(ome.toString() + " path=[" + doc.rPath.getFullPath()+"] class=["+sActionClass+"]", this);				
				actionReturn = new ActionReturn();
				return actionReturn;
			}
			catch(Throwable e)
			{
				Thread.currentThread().setContextClassLoader(ctx_cl);

				String sPath = sActionClass;
				StringBuilder sb = new StringBuilder(256);
				StringBuilder sbRaw = new StringBuilder(256);
				sbRaw.append(e.toString()+"\r\n");
				if(doc!=null) sPath = doc.rPath.getFullPath() + " - " + sActionClass;
				String sRawMsg = m_pSystem.getSystemMessageString("HTTPRequest.ActionExecuteError");
				String sMsg = puakma.error.pmaLog.parseMessage(sRawMsg, new String[]{sPath, e.toString()});
				m_pSystem.doError(sMsg, this);
				StackTraceElement ste[] = e.getStackTrace();
				//if(ste.length>0)
				sb.append("<html>\r\n");
				sb.append("<head><title>500: Internal Server Error</title></head>\r\n");
				sb.append("<body><h1>" + sMsg + "</h1>");
				sb.append("<br/>");
				sb.append("<table cellspacing=\"0\" cellpadding=\"4\">");
				sb.append("<tr bgcolor=\"f0f0f0\"><td><b>Class</b></td><td><b>Method</b></td><td><b>Line</b></td></tr>\r\n");
				for(int i=0; i<ste.length; i++)  
				{
					String sLine = "Class=" + ste[i].getClassName() + " Method=" + ste[i].getMethodName() + " Line=" + ste[i].getLineNumber();
					sbRaw.append(sLine+"\r\n");
					//m_pSystem.doDebug(0, sLine, this);
					sb.append("<tr>");
					sb.append("<td>"+ste[i].getClassName()+"</td>");
					sb.append("<td><i>"+ste[i].getMethodName()+"</i></td>");
					sb.append("<td>"+ste[i].getLineNumber()+"</td>");
					sb.append("</tr>\r\n");
				}
				sb.append("</table>\r\n");
				sb.append("</body></html>\r\n");
				Util.logStackTrace(e, m_pSystem, STACKTRACE_DEPTH);
				//e.printStackTrace();
				/*
				if(doc==null)
					sendError(500, "Internal Server Error", "text/html", sb.toString().getBytes());
				else
				{
					doc.replaceItem(Document.ERROR_FIELD_NAME, sbRaw.toString());
					sendPuakmaError(500, doc);
				}*/
				doc.replaceItem(Document.ERROR_FIELD_NAME, sbRaw.toString());
				sendPuakmaError(500, doc);
			}			
		}//if


		return actionReturn;
	}

	/**
	 * Serves a file from the file system
	 */
	private void serveLocalFile(String document_path)
	{
		String sRequestURI = document_path;
		RequestPath rPath = new RequestPath(document_path);
		document_path = rPath.getPathToApplication();
		if(document_path.charAt(document_path.length()-1)=='/') document_path = document_path.substring(0, document_path.length()-1);
		try
		{
			document_path = URLDecoder.decode(document_path, "UTF-8"); //decode the string, remove %20 etc
		}
		catch(Exception uee){}
		m_pSystem.doDebug(pmaLog.DEBUGLEVEL_FULL, "serveLocalFile()", this);
		ArrayList extra_headers = new ArrayList();
		if(m_pSession!=null) extra_headers.add(m_pSession.getCookieString());
		//long lStart = System.currentTimeMillis();
		String sEnforcedPath = enforceDocumentRoot(document_path);
		String szRequestPath = m_http_server.HTTP_PublicDir + sEnforcedPath;
		//long lEnd = System.currentTimeMillis();
		//System.out.println((lEnd-lStart) + "ms 1");
		//szRequestPath = enforceDocumentRoot(m_http_server.HTTP_PublicDir + szRequestPath);
		//lEnd = System.currentTimeMillis();
		//System.out.println((lEnd-lStart) + "ms 2");
		//if(m_http_response_sent) return;
		if(m_http_server.HTTP_PublicDir==null || m_http_server.HTTP_PublicDir.length()==0)
		{
			m_pSystem.doError("HTTPRequest.NoPublicDir", this);
			sendBadRequestError();
			return;
		}
		//Determine if we are allowed to serve files to Anonymous users...
		if(!m_http_server.bAllowAnonPublicDir)
		{
			if(m_pSession==null || m_pSession.getUserName().equals(pmaSession.ANONYMOUS_USER))
			{
				File fErr = new File(m_http_server.HTTP_PublicDir+'/' + RET_FORBIDDEN + ".html");
				if(!fErr.canRead() || !fErr.exists() || !fErr.isFile())
					sendHTTPResponse(RET_FORBIDDEN, "NO ANONYMOUS ACCESS ALLOWED", extra_headers, HTTP_VERSION, "text/html", null);
				else
					serveFile(RET_FORBIDDEN, fErr, null);
				return;
			}
		}

		//CGI-BIN
		if(sEnforcedPath.startsWith("/cgi-bin"))
		{
			//StrisEnforcedPath);
			//System.out.println("RUNNING: "+req.Application);
			RunProgram rp = new RunProgram(); 
			String sCommandLine[] = new String[]{szRequestPath}; 
			String sEnvironmentArray[]=makeCGIEnvironment(sRequestURI);
			byte buf[]=null;
			int iReturnVal = rp.execute(sCommandLine, sEnvironmentArray, m_pSystem.getTempDir(), m_is.getInputStream());
			if(iReturnVal!=0) 
			{ 
				//m_pSystem.doError(sEnforcedPath + " returned " + iReturnVal, this);
				buf = rp.getCommandErrorOutput();       
			} 
			else 
			{ 
				buf = rp.getCommandOutput(); 
			} 
			try
			{
				streamToClient(buf);
				m_os.flush();

			}catch(Exception e){}
			//System.out.println("CGI-BIN REQUEST: " + sEnforcedPath);
			//System.out.println("PATH TO FILE   : " + szRequestPath);
			m_bCloseConnection = true;
			return;
		}

		File fToServe = new File(szRequestPath);
		if(!fToServe.canRead() || !fToServe.exists() || !fToServe.isFile())
		{
			File fErr = new File(m_http_server.HTTP_PublicDir+'/' + RET_FILENOTFOUND + ".html");
			if(!fErr.canRead() || !fErr.exists() || !fErr.isFile())
				sendNotFoundError();
			else
				serveFile(RET_FILENOTFOUND, fErr, null);
			return;
		}

		serveFile(RET_OK, fToServe, null);
	}

	/**
	 * Make the CGI environment variables.
	 */
	private String[] makeCGIEnvironment(String sRequestURI)
	{
		if(sRequestURI==null) sRequestURI="";
		String sPathInfo = "";
		String sQueryString="";
		int iPos = sRequestURI.indexOf('?');
		if(iPos>=0) 
		{
			sPathInfo = sRequestURI.substring(iPos);
			sQueryString= sRequestURI.substring(iPos+1);
		}        

		ArrayList arr = new ArrayList();
		arr.add("SERVER_SOFTWARE="+m_pSystem.getVersionString());
		arr.add("SERVER_NAME="+m_pSystem.getSystemHostName());
		arr.add("GATEWAY_INTERFACE=CGI/1.1");

		arr.add("REQUEST_URI="+sRequestURI);
		arr.add("PATH_INFO="+sPathInfo);
		arr.add("QUERY_STRING="+sQueryString);
		arr.add("DOCUMENT_ROOT="+m_http_server.HTTP_PublicDir);
		arr.add("HTTP_ACCEPT="+puakma.util.Util.getMIMELine(m_environment_lines, "Accept"));
		arr.add("HTTP_ACCEPT_CHARSET="+puakma.util.Util.getMIMELine(m_environment_lines, "Accept-Charset"));
		arr.add("HTTP_ACCEPT_ENCODING="+puakma.util.Util.getMIMELine(m_environment_lines, "Accept-Encoding"));
		arr.add("HTTP_ACCEPT_LANGUAGE="+puakma.util.Util.getMIMELine(m_environment_lines, "Accept-Language"));
		arr.add("HTTP_CONNECTION="+puakma.util.Util.getMIMELine(m_environment_lines, "Connection"));
		arr.add("HTTP_HOST="+puakma.util.Util.getMIMELine(m_environment_lines, "Host"));
		arr.add("HTTP_KEEP_ALIVE="+puakma.util.Util.getMIMELine(m_environment_lines, "Keep-Alive"));
		String sReferer = puakma.util.Util.getMIMELine(m_environment_lines, "Referer");
		if(sReferer!=null) arr.add("HTTP_Referer="+sReferer);
		arr.add("HTTP_USER_AGENT="+puakma.util.Util.getMIMELine(m_environment_lines, "User-Agent"));
		arr.add("PATH="+System.getProperty("java.library.path"));
		arr.add("REMOTE_ADDR="+m_pSession.getHostAddress());
		InetSocketAddress addr = (InetSocketAddress)m_sock.getRemoteSocketAddress();
		arr.add("REMOTE_PORT="+addr.getPort());
		arr.add("REQUEST_METHOD="+m_sInboundMethod);
		arr.add("SERVER_ADDR="+m_sock.getLocalAddress().getHostAddress());
		arr.add("SERVER_PORT="+m_sock.getLocalPort());
		arr.add("SERVER_PROTOCOL="+m_sHTTPVersion);
		String sContentLength = puakma.util.Util.getMIMELine(m_environment_lines, "Content-Length");
		if(sContentLength!=null)
		{
			arr.add("CONTENT_LENGTH="+sContentLength); 
		}

		//check for nulls!!

		return puakma.util.Util.objectArrayToStringArray(arr.toArray());
	}

	/**
	 *
	 * @param fToServe
	 */
	private void serveFile(int iErrCode, File fToServe, ArrayList extra_headers)
	{
		//final int SECONDS_IN_DAY = 86400;
		if(extra_headers==null) extra_headers = new ArrayList();
		String sMimeType = determineMimeType(fToServe.getAbsolutePath());
		java.util.Date dtLastModified = new java.util.Date( fToServe.lastModified() );
		if(!hasResourceChanged(dtLastModified))
		{
			//no change, send a 304
			sendHTTPResponse(RET_NOT_MODIFIED, "Not Modified", null, HTTP_VERSION,
					sMimeType, null);
			return;
		}

		try
		{				
			String sLastModified = "Last-Modified: " + puakma.util.Util.formatDate(dtLastModified, LAST_MOD_DATE, Locale.UK, m_tzGMT);
			extra_headers.add(sLastModified);
			int iSeconds = (int)Math.abs((System.currentTimeMillis() - dtLastModified.getTime())/1000);
			iSeconds = iSeconds/2; //set expiry to half the time since it was last modified
			int iMaxEpirySeconds = m_http_server.getMaxExpirySeconds();
			if(iMaxEpirySeconds>=0 && iSeconds>iMaxEpirySeconds) iSeconds = iMaxEpirySeconds;
			Date dtExpires = Util.adjustDate(new Date(), 0, 0, 0, 0, 0, iSeconds);
			String sExpires = "Expires: " + puakma.util.Util.formatDate(dtExpires, LAST_MOD_DATE, Locale.UK, m_tzGMT);
			extra_headers.add(sExpires);

			FileInputStream fin = new FileInputStream(fToServe);
			String sReply = "";
			if(iErrCode==RET_OK) sReply="OK";
			//assume the smaller files may be css/js/jpg etc so can be compressed
			if(fToServe.length()<204800) //less than 200Kb
			{            
				byte buf[] = new byte[(int)fToServe.length()];
				byte smallbuf[] = new byte[102400];
				int iRead = fin.read(smallbuf);
				int iCopyPos = 0;
				while(iRead>0)
				{                  
					System.arraycopy(smallbuf, 0, buf, iCopyPos, iRead);
					iCopyPos += iRead;
					iRead = fin.read(smallbuf);
				}

				//m_pSystem.doDebug(0, "pre-minify " + "[" + sMimeType + "] " + fToServe.getName(), this);
				if(m_http_server.shouldMinifyFileSystemJS() && 
						sMimeType!=null && 
						sMimeType.equalsIgnoreCase("text/javascript")) 
				{
					buf = Util.minifyJSCode(buf);					
				}
				sendHTTPResponse(iErrCode, sReply, extra_headers, HTTP_VERSION,
						sMimeType, buf);
			}
			else
				sendHTTPResponse(iErrCode, sReply, extra_headers, HTTP_VERSION,
						sMimeType, fToServe.length(),  fin);
			fin.close();
		}
		catch(Exception e)
		{
			sendHTTPResponse(500, "Something really bad happened when trying to serve your file. Sorry.... :-) " + e.toString(), extra_headers, HTTP_VERSION,
					"text/html", null);
		}
	}


	/**
	 * Attempts to load the mime type for the given file
	 */
	private String determineMimeType(String sRequestPath)
	{
		String szMimeType="";
		int iPos;
		iPos = sRequestPath.lastIndexOf('.');
		if(iPos>=0)
		{
			String szExt = sRequestPath.substring(iPos+1, sRequestPath.length());
			szMimeType = m_http_server.propMime.getProperty(szExt);
			//if not found, try the default type
			if(szMimeType==null) szMimeType = m_http_server.propMime.getProperty("*");
			if(szMimeType!=null)
			{
				iPos = szMimeType.indexOf(' ');
				if(iPos>=0) szMimeType = szMimeType.substring(0, iPos);
				return szMimeType;
			}
		}
		return "www/unknown";
	}


	/**
	 * This method makes sure that the client is only allowed files that
	 * reside within the document root directory
	 */
	private String enforceDocumentRoot(String requested_path)
	{
		m_pSystem.doDebug(pmaLog.DEBUGLEVEL_FULL, "enforceDocumentRoot()", this);
		//System.out.println("HTTPRM: Requested path: " + requested_path);

		// For added performance in the future, use a StringBuilder in
		// this method instead of lots of Strings..

		String enforced_path = requested_path;
		int index;

		// Make sure all backslashes are replaced with slashes, so that
		// on Windows nobody can back out of the document root using a
		// path like "\windows\win.ini"
		enforced_path = enforced_path.replace('\\', '/');

		// Now, make sure the path doesn't say "//" anywhere
		while ((index = enforced_path.indexOf(SLASHSLASH)) != -1)
		{
			enforced_path = enforced_path.substring(0, index) +
					enforced_path.substring(index + 1,
							enforced_path.length());
		}

		// Next, starting from the beginning of the path and going
		// towards the end of the path, interpret any ".." to mean
		// that we should rip out the last directory from the path,
		// unless we go back beyond the document root dir (in which
		// case we'll send a Bad Request error page.
		int last_index = 0;
		while ((index = enforced_path.indexOf('/', last_index + 1)) != -1)
		{
			// If the next token m_is 2 chars long (will short-circuit most
			// cases!), and if the token m_is "..", then we should try to
			// drop the last token from the path
			if ((index - last_index) == 3 &&
					enforced_path.substring(last_index + 1, index).equals(DOTDOT))
			{
				// Make sure we don't back up too far..
				if (last_index == 0)
				{
					sendBadRequestError();
					return enforced_path;
				}

				// Otherwise, clip out the last token and the ".." token
				int before_last_index = enforced_path.lastIndexOf('/',
						last_index - 1);
				if (before_last_index == -1)
				{
					// We can't find a previous token to cut out, so this
					// m_is a bad request.
					sendBadRequestError();
					return enforced_path;
				}

				String s = enforced_path.substring(0, before_last_index + 1);

				enforced_path = s + enforced_path.substring(index + 1,
						enforced_path.length());

				index = before_last_index;
			}
			last_index = index;
		}

		// The above while loop does not catch the case where you have
		// just ".." on the end (without a slash on the very end), so
		// we'll check for that here..
		if (enforced_path.endsWith(DOTDOT))
		{
			// If the whole enforced_path now equals "/..", then this
			// m_is a bad request.
			if (enforced_path.equals(SLASHDOTDOT))
			{
				sendBadRequestError();
				return enforced_path;
			}

			// Otherwise, we'll remove the last token and the "/.."
			index = enforced_path.lastIndexOf('/');
			last_index = enforced_path.lastIndexOf('/', index - 1);

			// Make sure we know where to start the chop
			if (last_index == -1)
			{
				// We couldn't find the spot to cut.. it may not have
				// even been there, so regardless we'll send a Bad Request
				// Error page.
				sendBadRequestError();
				return enforced_path;
			}

			enforced_path = enforced_path.substring(0, last_index);
		}

		return enforced_path;
	}

	/**
	 * This is a generic method for sending HTTP coded responses back
	 * to the browser in response to a request.  This method does not
	 * automatically use a STEAM template, but you could always pass
	 * in the already-filled-in content from any kind of template you
	 * like.
	 *                                                               <BR><BR>
	 * Here are some useful error codes and error strings to use:
	 *                                                           <BLOCKQUOTE>
	 *     200 "OK"                                                      <BR>
	 *     201 "Created"                                                 <BR>
	 *     202 "Accepted"                                                <BR>
	 *     204 "No Content"                                              <BR>
	 *     300 "Multiple Choices"                                        <BR>
	 *     301 "Moved Permanently"                                       <BR>
	 *     302 "Moved Temporarily"                                       <BR>
	 *     304 "Not Modified"                                            <BR>
	 *     400 "Bad Request"                                             <BR>
	 *     401 "Unauthorized"                                            <BR>
	 *     403 "Forbidden"                                               <BR>
	 *     404 "Not Found"                                               <BR>
	 *     500 "Internal Server Error"                                   <BR>
	 *     501 "Not Implemented"                                         <BR>
	 *     502 "Bad Gateway"                                             <BR>
	 *     503 "Service Unavailable"
	 *                                                      </BLOCKQUOTE><BR>
	 *
	 * @param http_code          the HTTP result code for the response you
	 *                           wish to send to the browser.
	 * @param http_code_string   the String explanation of the HTTP result
	 *                           code, like "Not Found".
	 * @param extra_headers      a Vector of Strings, each one an HTTP header
	 *                           line to be sent with the response (if any
	 *                           headers get sent).  This method appends the
	 *                           HTTP line termination String to each header
	 *                           line, so the Strings in this Vector must not
	 *                           already have them on the end!  Also, this
	 *                           set of headers should not include Date,
	 *                           Server, or Content-Type header lines.
	 * @param http_version       the HTTP version String that the browser
	 *                           sent to us, if any.
	 * @param content_type       the content type String (like "text/html")
	 *                           to use for this response.
	 * @param http_response_body a byte array containing the body of the
	 *                           response to send, instead of what this
	 *                           method would send by default.
	 */
	private void sendHTTPResponse(int http_code, String http_code_string,
			ArrayList extra_headers, String http_version,
			String content_type, byte[] http_response_body)
	{

		if(extra_headers==null) extra_headers = new ArrayList();
		if(null==http_response_body)
		{
			if((http_code>=300 || http_code<400))
			{                  
				http_response_body = new byte[0];
			}
			else
			{
				// Send the default response body, it's a simple "error" page
				String response_content = "<html>\r\n<head>\r\n<title>" +
						m_pSystem.getVersionString() + "</title>\r\n</head>\r\n<body>\r\n" +
						"<h1>HTTP " + http_code + ": " + http_code_string +
						"</h1>\r\n<br/>\r\n<br>\n<hr>\r\n<i><a href=\"" + m_sHTTPURLPrefix + "://" +
						m_sRequestedHost + "\">" + m_pSystem.getVersionString() + "</a></i>\r\n<br>\r\n" +
						"</body>\r\n</html>\n";
				http_response_body = response_content.getBytes();
			}
		}
		String sEncoding = puakma.util.Util.getMIMELine(extra_headers, "Content-Encoding"); 
		if(sEncoding==null && http_response_body!=null && http_response_body.length>0  && shouldGZipOutput(content_type) )
		{
			//m_pSystem.doDebug(0, "GZipping output for: " + m_http_request_line, this);
			extra_headers.add("Content-Encoding: gzip");
			float fBefore = http_response_body.length;          
			http_response_body = puakma.util.Util.gzipBuffer(http_response_body);
			float fAfter = http_response_body.length;          
			if(this.m_http_server.isDebug())
				m_pSystem.doDebug(pmaLog.DEBUGLEVEL_NONE, "GZIP Output: " + content_type + " " + (int)fBefore + '/' + (int)fAfter + " " + (float)(fAfter/fBefore)*100 + "%", m_pSession);
		}

		//generate an ETag header by using md5 of page
		//this will ensure identical content pages are the same
		if(http_code>=200 && http_code<300 && m_http_server.shouldGenerateETags())
		{
			String sETag = puakma.util.Util.base64Encode(puakma.util.Util.hashBytes(http_response_body));
			//System.out.println(sETag.length() + " bytes :"+sETag);
			extra_headers.add("ETag: \""+sETag+'\"');
			extra_headers.add("Vary: ETag");

			if(m_sIfNoneMatch!=null)
			{
				m_sIfNoneMatch = puakma.util.Util.trimChar(m_sIfNoneMatch, '\"');
				//System.out.println("If-None-Match: "+m_sIfNoneMatch);
				if(m_sIfNoneMatch.equals(sETag))
				{
					//System.out.println("ETag matches, send a 304 not modified....");         
					sendHTTPResponse(RET_NOT_MODIFIED, "Not Modified", null, HTTP_VERSION,
							content_type, null);
					return;
				}           
			}

		}

		sendHTTPResponse(http_code, http_code_string, extra_headers, http_version,
				content_type, http_response_body.length, new ByteArrayInputStream(http_response_body));
	}

	/**
	 * This is a stream version for sending BIG files
	 */
	private void sendHTTPResponse(int http_code, String http_code_string,
			ArrayList extra_headers, String http_version,
			String content_type, long lStreamLengthBytes, InputStream is)
	{
		final int MAX_CHUNK = 8192; //512k data chunks
		File fOriginal=null;
		File fByteServe=null;
		ArrayList out_lines = new ArrayList();

		//m_pSystem.doDebug(0, http_code_string + " " + http_code_string + " stream:"+lStreamLengthBytes + " " + m_sInboundPath, this);
		/*
		 * BJU 8/5/07 Moved code here so all requests get the session cookie sent
		 * back to the browser for a GET and POST. 
		 */
		if(m_bSendSessionCookie) 
		{         
			if(extra_headers==null) extra_headers = new ArrayList();
			String sPath = getCookiePath();

			extra_headers.add(m_pSession.getCookieString(sPath, getCookieDomain()));

			LTPAToken ltpa = new LTPAToken();
			ltpa.setUserName(m_pSession.getUserName());          
			byte bufSecret[] = makeWebSSOSecret();          
			ltpa.setExpiryDate(getLtpaSessionExpiryDate());
			//this domain thing may not be right - what about multi-homed servers?? !!
			String sLtpaCookie = ltpa.getAsCookie(bufSecret, sPath, m_pSystem.getSystemProperty("WEBSSODomain"), false, m_pSession.getSSOExpiryDate());
			//m_pSystem.doDebug(0, sLtpaCookie, this);
			extra_headers.add("Set-Cookie: "+sLtpaCookie);
			//System.out.println(sLtpaCookie);
		}


		//if an internal server error, close the connection
		if(http_code>=500) m_bCloseConnection = true;

		m_pSystem.doDebug(pmaLog.DEBUGLEVEL_FULL, "sendHTTPResponse(stream)", this);
		// Don't send more than one HTTP response to the client..
		//if(m_http_response_sent) return;

		try
		{
			//this.dumpHeaders(m_environment_lines);
			String sRange = getByteRangeServe(m_environment_lines);
			if(sRange!=null)
			{
				http_code = RET_PARTIAL_CONTENT;
				http_code_string = "Partial Content";
			}
			m_os.write((http_version + " " + http_code + " " + http_code_string + HTTP_NEWLINE).getBytes());
			String sServer = "Server: Tornado/" + m_pSystem.getVersion();
			m_os.write((sServer + HTTP_NEWLINE).getBytes());
			String sDate = "Date: " + puakma.util.Util.formatDate(new java.util.Date(), LAST_MOD_DATE, Locale.UK, m_tzGMT);
			m_os.write((sDate + HTTP_NEWLINE).getBytes());
			out_lines.add(sServer);
			out_lines.add(sDate);

			//tell the client byteserving is supported...
			if(m_bAllowByteRangeServing) m_os.write(("Accept-Ranges: bytes" + HTTP_NEWLINE).getBytes());

			if(m_fHTTPVersion<1.1 || m_bCloseConnection || !m_pSystem.isSystemRunning())
			{
				m_os.write(("Connection: close" + HTTP_NEWLINE).getBytes());
				out_lines.add("Connection: close");
			}
			else
			{
				m_os.write(("Connection: Keep-Alive" + HTTP_NEWLINE).getBytes());
				String sTimeout = "Keep-Alive: timeout=" + m_iServerTimeout + ", max=" + m_iConnectionsLeft;
				m_os.write((sTimeout + HTTP_NEWLINE).getBytes());
				out_lines.add("Connection: Keep-Alive");
				out_lines.add(sTimeout);
			}
			/*if(!m_pSystem.isLicensedVersion()) 
          {
              m_os.write(("X-HTTPLicense: NON-COMMERCIAL USE ONLY" + HTTP_NEWLINE).getBytes());
              out_lines.add("X-HTTPLicense: NON-COMMERCIAL USE ONLY");
          }*/


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
					out_lines.add(header_line);
					//System.out.println(request_id + " " + header_line);
					header_line = header_line + HTTP_NEWLINE;
					m_os.write(header_line.getBytes());

				}
			}//extra headers

			if(null == content_type) content_type="text/html";
			// Send the Content-Type
			//range is first byte - last byte
			long lFirstByteInRange=0;
			long lLastByteInRange=(lStreamLengthBytes-1);

			if(http_code!=RET_NOT_MODIFIED && http_code!=416)
			{                  
				//if(m_bAllowByteRangeServing && sRange!=null)
				if(sRange!=null)
				{         
					//Range: bytes=100510392-
					//note: does not support multiple ranges eg. Range: bytes=25-100,104-210,255-900                  
					//if there is an end range specified use it
					//if(sRange.length()>0 && sRange.charAt(sRange.length()-1)!='-')
					//{
					//System.out.println("ByteServe range:["+sRange+"]");

					int iEqualsPos = sRange.lastIndexOf('=');
					//determine the start range...
					if(iEqualsPos>0) sRange = sRange.substring(iEqualsPos+1, sRange.length());                  

					ArrayList arrRange = puakma.util.Util.splitString(sRange, ',');

					if(arrRange.size()>1)
					{
						/* ***************************************
						 * Programs prefer the file in the order it asked for :-(
						 * so stream to file, then jump around in the temp file.
						 * eg: Range: bytes=23375437-23375617, 2356584-23344433
						 * this is a multiple range, probably from Adobe PDF reader
						 *
						 * For small files, it may be faster to do this in a byte buffer,
						 * but this will typically be for a BIG file 20MB pdf or so.
						 */

						fOriginal = File.createTempFile(String.valueOf(m_pSystem.getSystemUniqueNumber())+"_byteserve_o_", null, m_pSystem.getTempDir());
						fOriginal.deleteOnExit();
						sendStreamToFile(is, fOriginal);
						fByteServe = File.createTempFile(String.valueOf(m_pSystem.getSystemUniqueNumber())+"_byteserve_", null, m_pSystem.getTempDir());
						fByteServe.deleteOnExit();
						FileOutputStream foutBS = new FileOutputStream(fByteServe);

						String sBoundary = getNextMimeBoundary();
						long lTotalOut=0;
						//for each chunk                      
						for(int i=0; i<arrRange.size(); i++)
						{
							FileInputStream fin = new FileInputStream(fOriginal);
							String sSubRange = puakma.util.Util.trimSpaces((String)arrRange.get(i)); 
							int iRange[] = getRangeAsInt(sSubRange);
							if(iRange[1]<=0) iRange[1] = (int)(lStreamLengthBytes-1);
							int iRequestLength=(iRange[1]-iRange[0])+1;
							//System.out.println(i+":"+sSubRange + " "+iRequestLength + " bytes");

							foutBS.write(("\r\n--"+sBoundary+"\r\n").getBytes());
							String sContentType = "Content-Type: "+content_type;
							foutBS.write((sContentType+"\r\n").getBytes());
							out_lines.add(sContentType);
							foutBS.write(("Content-Range: bytes "+sSubRange+"/"+lStreamLengthBytes+"\r\n\r\n").getBytes());
							//add data.....
							//System.out.println("Skipping " + (iRange[0]-1));
							//System.out.println("Available stream:"+fin.available());
							//soakStartStream(fin, iRange[0]-lTotalOut);
							if(iRange[0]>0) fin.skip(iRange[0]-1);
							lTotalOut += iRange[0]-1;

							int len=0;
							int iWrote=0;

							//System.out.println("request length " + iRequestLength);
							while((len=fin.available()) > 0 && iWrote<iRequestLength)
							{
								if(len>MAX_CHUNK) len = MAX_CHUNK;                              
								if((iWrote+len)>iRequestLength) len = iRequestLength-iWrote;
								byte[] output = new byte[len];                              
								int iRead = fin.read(output);              
								if(iRead>0) foutBS.write(output, 0, iRead);

								lTotalOut += iRead;
								iWrote += iRead;
							}
							//System.out.println("     : "+iRequestLength);
							//System.out.println("wrote: "+iWrote);                          
							fin.close();
						}
						foutBS.write(("\r\n--"+sBoundary+"--\r\n").getBytes());
						foutBS.flush();
						foutBS.close();
						//http://httpd.apache.org/docs/misc/known_client_problems.html
						//adobe's own server uses multipart/byteranges not multipart/x-byteranges
						content_type = "multipart/byteranges; boundary="+sBoundary;

						lFirstByteInRange = 0;                      
						lLastByteInRange=fByteServe.length();
						is = (InputStream)(new FileInputStream(fByteServe));
						//System.out.println("Finished multi-range serve: "+lEndRange);
					}//if range >1
					else //single range
					{
						int iDashPos = sRange.indexOf('-');
						String sStartRange = sRange.substring(0, iDashPos);
						if(sStartRange.length()>0)
						{                          
							try{lFirstByteInRange=Long.parseLong(sStartRange);}catch(Exception e){} 
						}

						//determine the end range...                      
						if(iDashPos>0)
						{
							String sEndRange = sRange.substring(iDashPos+1, sRange.length());
							try{lLastByteInRange=Long.parseLong(sEndRange);}catch(Exception e){} 
							if(lLastByteInRange==0) lLastByteInRange = (lStreamLengthBytes-1);
						}
						//}
						//m_pSystem.doDebug(0, "byte serving! ["+sRange+"] firstbyte="+lFirstByteInRange+" lastbyte="+lLastByteInRange + " stream len="+lStreamLengthBytes, this);
						//if first > eof then return 416 Requested range not satisfiable
						if(	(lLastByteInRange>lStreamLengthBytes || lFirstByteInRange>lLastByteInRange || lFirstByteInRange<0))
						{
							//disable byte serving
							//m_bAllowByteRangeServing = false;
							//m_pSystem.doDebug(0, m_sInboundMethod + " 416 ["+sRange+"] firstbyte="+lFirstByteInRange+" lastbyte="+lLastByteInRange + " stream len="+lStreamLengthBytes, this);
							Util.replaceHeaderValue(m_environment_lines, "Content-Range", null);
							Util.replaceHeaderValue(m_environment_lines, "Range", null);
							String sErr = "Requested range not satisfiable";                             
							sendError(416, sErr, "text/plain", sErr.getBytes());
							return;
						}

						m_os.write(("Content-Range: bytes " + lFirstByteInRange + "-" + lLastByteInRange + "/" + lStreamLengthBytes + HTTP_NEWLINE).getBytes());
					}
				}
				String sContentType = "Content-Type: " + getAppropriateCharset(content_type);
				m_os.write((sContentType + HTTP_NEWLINE).getBytes());
				String sContentLength = "Content-Length: " + ((lLastByteInRange-lFirstByteInRange)+1);
				m_os.write((sContentLength + HTTP_NEWLINE).getBytes());
				out_lines.add(sContentType);
				out_lines.add(sContentLength);
			} 
			//System.out.println("here");
			//send a blank line to denote the start of the data
			m_os.write((HTTP_NEWLINE).getBytes());


			if(!m_sInboundMethod.equalsIgnoreCase("HEAD") && http_code!=RET_NOT_MODIFIED)
			{
				//System.out.println("here2");
				int len=MAX_CHUNK;
				int iContentLength = (int) ((lLastByteInRange-lFirstByteInRange)+1);
				long lTotalOut=0;
				if(iContentLength<MAX_CHUNK) len = iContentLength;
				byte output[] = new byte[len];

				//	if(m_sInboundPath!=null && m_sInboundPath.indexOf(".mp4")>0) m_pSystem.doDebug(0, http_code + " " + http_code_string + " ["+sRange+"] firstbyte="+lFirstByteInRange+" lastbyte="+lLastByteInRange + " contentlen=" + iContentLength +" stream len="+lStreamLengthBytes + " " + m_sInboundPath, this);

				is.skip(lFirstByteInRange);

				while((len=is.available()) > 0)
				{


					int iRead = is.read(output);
					if(iRead>0)
					{
						//System.out.println("lTotalOut="+lTotalOut+" iRead="+iRead+" lEndRange="+lEndRange);
						if((lTotalOut+iRead)>iContentLength) iRead = (int)(iContentLength-lTotalOut);
						if(iRead<=0) break;
						m_os.write(output, 0, iRead);
						m_http_server.updateBytesServed(iRead);   
						lTotalOut += iRead;
					}					
					//System.out.println("here4");
				}
				//System.out.println("Wrote: "+lTotalOut);
				m_http_server.incrementStatistic(HTTP.STATISTIC_KEY_BYTESOUTPERHOUR, lTotalOut);
				m_http_server.incrementStatistic(HTTP.STATISTIC_KEY_TOTALBYTESOUT, lTotalOut);
			}//if !HEAD request
			m_os.flush();        
		} //try
		catch (IOException ioe)
		{
			//System.out.println("ERR: " + ioe.toString());
		}

		//cleanup temp files used for byteserving
		if(fOriginal!=null) fOriginal.delete(); 
		if(fByteServe!=null) fByteServe.delete(); 

		//System.out.println("REQUEST DONE");

		//DO WE NEED THIS??? MAY BE OVERKILL
		if(m_http_server.isDebug())
		{
			m_pSystem.doDebug(0, request_id + ": " + m_pSession.getSessionID() + " [" + m_http_request_line + "] ", m_pSession);
		}

		long lTransMS = System.currentTimeMillis() - m_lStart;
		//String szReferer = Util.getMIMELine(m_environment_lines, "Referer");
		//if(szReferer==null) szReferer = "";
		HTTPLogEntry stat;
		if(m_pSession==null) 
			stat = new HTTPLogEntry(m_http_server.getMimeExcludes(), m_environment_lines, out_lines, m_http_request_line, content_type, lStreamLengthBytes, (long)m_iInboundSize, http_code, m_sClientIPAddress, m_sClientHostName, m_sSystemHostName, m_sRequestedHost, lTransMS, m_sock.getLocalAddress().getHostAddress(), m_iHTTPPort, null); 
		else
			stat = new HTTPLogEntry(m_http_server.getMimeExcludes(), m_environment_lines, out_lines, m_http_request_line, content_type, lStreamLengthBytes, (long)m_iInboundSize, http_code, m_sClientIPAddress, m_sClientHostName, m_sSystemHostName, m_sRequestedHost, lTransMS, m_sock.getLocalAddress().getHostAddress(), m_iHTTPPort, m_pSession.getSessionContext());

		m_http_server.writeStatLog(stat, m_sInboundPath, m_sInboundMethod, m_iInboundSize);		
		m_http_server.incrementStatistic(HTTP.STATISTIC_KEY_BYTESINPERHOUR, m_iInboundSize);
		m_http_server.incrementStatistic(HTTP.STATISTIC_KEY_TOTALBYTESIN, m_iInboundSize);
	}

	/**
	 * When a range is passed eg "2311-3099" will return 2311 & 3099
	 * This indicates how many bytes should be skipped from the start of the stream
	 * and how many bytes should be read from the stream when processed by the calling function.
	 *
	 */
	private int[] getRangeAsInt(String sSubRange)
	{
		int iReturn[] = new int[2];

		int iPos = sSubRange.indexOf('-');
		if(iPos>0)
		{
			String sStart = sSubRange.substring(0, iPos);
			String sEnd = sSubRange.substring(iPos+1, sSubRange.length());
			//int iStart=0;
			//int iEnd=0;
			try{iReturn[0] = Integer.parseInt(sStart);}catch(Exception a){}
			try{iReturn[1] = Integer.parseInt(sEnd);}catch(Exception a){}
			//iReturn[0] = iStart;
			//iReturn[1] = iEnd;
		}
		return iReturn;
	}

	/**
	 * Send an entire stream to a file
	 */
	private void sendStreamToFile(InputStream is, File fOriginal) throws IOException
	{
		final int MAX_CHUNK=20000;
		int len=0;
		int iTotalWrote=0;
		FileOutputStream fout = new FileOutputStream(fOriginal);
		//System.out.println("skipping "+lSkip+" bytes");
		//long lStart = System.currentTimeMillis();
		while((len=is.available()) > 0)
		{
			if(len > MAX_CHUNK) len = MAX_CHUNK;          
			byte output[] = new byte[len];

			int iRead = is.read(output);
			fout.write(output, 0, iRead);
			iTotalWrote+=iRead;
		}
		fout.flush();
		fout.close();
	}



	/**
	 * Code to make the next mime boundary
	 */
	private synchronized String getNextMimeBoundary()
	{
		SimpleDateFormat partcodedf = new SimpleDateFormat( "yyyyMMddHHmmssSS" );

		m_lUnique++;
		return MIME_START + "_"+ partcodedf.format(new java.util.Date()) + m_lUnique;
	}


	/**
	 * Should this request be byte served or not.
	 * @return null if this is not a byterange serve
	 */
	private String getByteRangeServe(ArrayList arrHeaders)
	{
		String sRange = puakma.util.Util.getMIMELine(arrHeaders, "Content-Range");
		if(sRange==null) sRange = puakma.util.Util.getMIMELine(arrHeaders, "Range");
		if(sRange!=null && sRange.length()==0) return null;
		return sRange;
	}

	/**
	 * So actions have direct control
	 * @return
	 */
	public OutputStream getOutputStream()
	{
		return m_os;
	}

	/**
	 * send some bytes straight to the client
	 */
	public void streamToClient(byte[] buf, boolean bFlush) throws IOException
	{
		if(buf!=null)
		{
			m_os.write(buf);
			m_http_server.updateBytesServed(buf.length);
		}
		if(bFlush) m_os.flush(); //expensive, but required for small http replies :-(
	}

	/**
	 * Use streamToClient(buf, bool);
	 * @param buf
	 * @throws IOException
	 */
	@Deprecated 
	public void streamToClient(byte[] buf) throws IOException
	{
		streamToClient(buf, true);	
	}

	/**
	 * Determines the inbound character set from the Page/Action or application.
	 */
	private String getInboundCharset(RequestPath rPath)
	{
		String sCharSet=null;
		String sAppCharacterEncoding = getAppParam(m_pSession, Document.APPPARAM_DEFAULTCHARSET, rPath.Group, rPath.Application);
		if(sAppCharacterEncoding!=null && sAppCharacterEncoding.length()>0) sCharSet = sAppCharacterEncoding;

		//now lookup the page and see if that has a charset specified 
		String sDesignName = rPath.DesignElementName;
		if(sDesignName!=null && sDesignName.length()>0)
		{
			DesignElement de = this.getDesignElement(m_pSession, rPath.Application, rPath.Group, sDesignName, rPath.DesignType);
			if(de!=null)
			{
				String sContentType = de.getContentType(); //"text/html; charset=utf-8"
				if(sContentType!=null && sContentType.length()>0)
				{
					String sCS = "charset=";
					int iPos = sContentType.toLowerCase().indexOf(sCS);
					if(iPos>=0)
					{
						sCharSet = puakma.util.Util.trimSpaces(sContentType.substring(iPos+sCS.length()));
					}
				}
			}
		}

		if(sCharSet==null || sCharSet.length()==0) sCharSet=HTTP.DEFAULT_CHAR_ENCODING;
		return sCharSet;
	}

	/**
	 * Determine if we should use a particular charset
	 */
	private String getAppropriateCharset(String sContentType)
	{
		if(sContentType==null) return null;
		if(m_sDefaultCharSetLine==null || m_sDefaultCharSetLine.length()==0) return sContentType; //no charset specified, bail out

		String sLow = sContentType.toLowerCase();
		//if a text type and no charset specified, use the default charset for this app request
		if(sLow.startsWith("text/") && sLow.indexOf("charset=")<0)
		{
			return sContentType + "; " + m_sDefaultCharSetLine;
		}
		return sContentType;
	}

	/**
	 * Tries to find a 404 or 500 etc page in the database and serve that.
	 * @param iErrCode
	 * @param docHTML
	 */
	private void sendPuakmaError(int iErrCode, HTMLDocument docHTML)
	{    
		if(docHTML==null || docHTML.rPath==null) return;   
		docHTML.removeItem(Document.PAGE_REDIRECT_ITEM);
		String sDocPath = docHTML.rPath.getPathToApplication() + '/' + iErrCode + "?OpenPage";
		docHTML.removeParsedParts();
		//int iReturnCode = performRequest(sDocPath, docHTML, true, false, true);
		int iReturnCode = RET_INTERNALSERVERERROR;
		if(m_bIsInErrorState) m_bCloseConnection = true;
		if(!m_bIsInErrorState) iReturnCode = performRequest(sDocPath, docHTML, true, false, true);
		m_bIsInErrorState  = true;
		switch(iReturnCode)
		{
		case RET_FILENOTFOUND:
			sDocPath = "/puakma" + m_http_server.getPuakmaFileExt() + '/' + iErrCode + "?OpenPage";        
			iReturnCode = performRequest(sDocPath, docHTML, true, false, true);
			if(iReturnCode==RET_FILENOTFOUND)
				sendError(iErrCode, null, null, null);
			else
			{         
				sendError(iErrCode, null, docHTML.getContentType(), docHTML.getContent());
			}
			break;
		default:          
			sendError(iErrCode, null, docHTML.getContentType(), docHTML.getContent());
			break;
		}
	}

	private void sendError(int iErrorCode, String sHTTPError, String sContentType, byte data[])
	{
		m_pSystem.doDebug(pmaLog.DEBUGLEVEL_FULL, "sendError()", this);
		if(sHTTPError==null) sHTTPError = "Error";
		sendHTTPResponse(iErrorCode, sHTTPError, null, HTTP_VERSION, sContentType, data);
	}

	private void sendNotFoundError()
	{
		m_pSystem.doDebug(pmaLog.DEBUGLEVEL_FULL, "sendNotFoundError()", this);
		sendHTTPResponse(404, "Not Found", null, HTTP_VERSION, null, null);
	}

	/**
	 *
	 */
	private void sendBadRequestError()
	{
		m_pSystem.doDebug(pmaLog.DEBUGLEVEL_FULL, "sendBadRequestError()", this);
		sendHTTPResponse(400, "Bad Request", null, HTTP_VERSION, null, null);
	}

	/**
	 *
	 */
	private void sendOptions(String sInboundPath) //eg "/" or "*" maybe we'll do something with this later??
	{
		m_pSystem.doDebug(pmaLog.DEBUGLEVEL_FULL, "sendOptions()", this);
		ArrayList extra_headers = new ArrayList();
		extra_headers.add("Allow: GET, POST, HEAD, OPTIONS");
		sendHTTPResponse(200, "OK", extra_headers, HTTP_VERSION, null, null);
	}

	/**
	 *
	 */
	private void sendInternalServerError()
	{
		m_pSystem.doDebug(pmaLog.DEBUGLEVEL_FULL, "sendInternalServerError()", this);
		sendHTTPResponse(500, "Internal server error", null, HTTP_VERSION, null, null);
	}


	/**
	 * Close the input and output streams
	 */
	private void doCleanup()
	{
		m_pSystem.doDebug(pmaLog.DEBUGLEVEL_FULL, "doCleanup()", this);
		if(m_action!=null) m_action.requestQuit();
		try
		{
			m_sock.close();
		}
		catch(IOException io1)
		{
			//m_pSystem.doError("HTTPRequest.CloseInput", new String[]{io1.getMessage()}, this);
		}
		
		try
		{
			m_is.close();
		}
		catch(IOException io1)
		{
			//m_pSystem.doError("HTTPRequest.CloseInput", new String[]{io1.getMessage()}, this);
		}

		try
		{
			m_os.close();
		}
		catch(IOException io2)
		{
			//m_pSystem.doError("HTTPRequest.CloseOutput", new String[]{io2.getMessage()}, this);
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
	 * @param sRequestURI
	 * @param document
	 * @param bForceRedirect
	 * @param bByPassSecurity
	 * @return
	 */
	private int processDesignElementRequest(String sRequestURI, HTMLDocument document, boolean bForceRedirect, boolean bByPassSecurity)
	{
		m_pSystem.doDebug(pmaLog.DEBUGLEVEL_VERBOSE, "processDesignElementRequest()", this);
		if(sRequestURI == null || m_pSession == null || document == null) return RET_INTERNALSERVERERROR;
		DesignElement design=null;
		int RequestReturnCode=RET_OK;

		if(document.hasItem(Document.PAGE_REDIRECT_ITEM))
		{
			String sRedirect = document.getItemValue(Document.PAGE_REDIRECT_ITEM);
			if(sRedirect==null || sRedirect.trim().length()==0) sRedirect = sRequestURI;      
			document.rPath = new RequestPath(sRedirect);//new RequestPath(document.getItemValue(Document.PAGE_REDIRECT_ITEM));
			if(document.rPath.Action.equalsIgnoreCase(DesignElement.PARAMETER_SAVEPAGE)) document.rPath = new RequestPath(document.rPath.getPathToApplication());
			sRequestURI = document.rPath.getFullPath();
		}
		else
			document.rPath = new RequestPath(sRequestURI);

		boolean bRequestLogin = sRequestURI.toLowerCase().indexOf("&login")>0;
		boolean bCanAccessApp = m_pSession.hasUserRole(document.rPath.getPathToApplication(), ACCESS_APP_ROLE);
		boolean bCanAccessResources = bCanAccessApp;
		boolean bIsAccessingResource = false;
		boolean bDesignElementAllowsAnonymousAccess = false;
		design = getDesignElement(m_pSession, document.rPath.Application, document.rPath.Group, document.rPath.DesignElementName, document.rPath.DesignType);
		if(design!=null) bDesignElementAllowsAnonymousAccess = design.allowAnonymousAccess();
		if(document.rPath.DesignType==0) //unknown design type
		{
			//since there is no ?Openresource action in the url, get the design element and check its type			
			if(design!=null) bIsAccessingResource = design.getDesignType()==DesignElement.DESIGN_TYPE_RESOURCE;
		}
		else
			bIsAccessingResource = document.rPath.DesignType==DesignElement.DESIGN_TYPE_RESOURCE;
		if(!bCanAccessResources) bCanAccessResources = m_pSession.hasUserRole(document.rPath.getPathToApplication(), ACCESS_RESOURCES_ROLE);
		if(bCanAccessResources && bIsAccessingResource && !bCanAccessApp) 
		{
			//if the user is allowed to access resources, check that this is a resource 
			//request. If it is, let them in.
			bCanAccessApp = true;
		}
		//check if specific design element allow anonymous access
		if(!bCanAccessApp && bDesignElementAllowsAnonymousAccess) bCanAccessApp = true;

		if(!(bCanAccessApp && bIsAccessingResource))
		{
			//some sort of authentication is required
			if(!bByPassSecurity && (bRequestLogin || !bCanAccessApp))
			{
				design = null;
				boolean bIsLoggedIn = m_pSession.isLoggedIn();

				//check that the page we are accessing is the default login page for this app      
				if(!isAccessingLoginPage(document) && (!bCanAccessApp || !bIsLoggedIn))
				{            
					if(!bIsLoggedIn)
					{                                            
						m_pSystem.doInformation("HTTPRequest.NoAnonymousAccess", new String[]{document.rPath.getFullPath()}, this);
						//RequestReturnCode = RET_UNAUTHORIZED;
						if(document.hasItem(Document.PAGE_REDIRECT_ITEM))
							document.setItemValue(Document.PAGE_REDIRECT_ITEM, document.rPath.getFullPath());
						else
							document.replaceItem(Document.PAGE_REDIRECT_ITEM, document.rPath.getFullPath());

						String szUserAttempt=document.getItemValue(Document.PAGE_USERNAME_ITEM);
						if(szUserAttempt!=null && szUserAttempt.length()!=0 && !document.hasItem(Document.PAGE_LOGIN_BYPASS))
						{
							document.replaceItem(Document.PAGE_PASSWORD_ITEM, "");          
							document.replaceItem(Document.PAGE_MESSAGESTRING_ITEM, pmaLog.parseMessage(m_pSystem.getSystemMessageString("HTTPRequest.BadLoginMessage"), new String[]{szUserAttempt}));
						}                
						document.rPath.Action = DesignElement.PARAMETER_OPENPAGE;
						document.rPath.DesignType = DesignElement.DESIGN_TYPE_PAGE;
						document.rPath.DesignElementName = getAppParam(m_pSession, Document.APPPARAM_LOGINPAGE, document.rPath.Group, document.rPath.Application);
						design = null;
						if(document.rPath.DesignElementName.length()!=0) design = getDesignElement(m_pSession, document.rPath.Application, document.rPath.Group, document.rPath.DesignElementName, document.rPath.DesignType);
						if(design==null)
						{
							design = getDesignElement(m_pSession, "puakma", "", DesignElement.DESIGN_PAGE_LOGIN, DesignElement.DESIGN_TYPE_PAGE);
							document.rPath.DesignElementName = DesignElement.DESIGN_PAGE_LOGIN;
						}
						if(design==null)
						{
							//add a server generated login screen, maybe get it from puakma.jar
							document.designObject = null;
							RequestReturnCode=RET_FILENOTFOUND;
						}
						else
						{
							//
							document.PageName = design.getDesignName();
							document.designObject = design;
							RequestReturnCode=RET_OK;
						}                   
					}//anonymous access
					else
					{
						m_pSystem.doError("HTTPRequest.NoAccess", new String[]{m_pSession.getUserName(), document.rPath.getFullPath()}, this);
						RequestReturnCode = RET_FORBIDDEN;
					}        
					return RequestReturnCode;
				} //if canaccessapp
			}//if &login
		}//if !accessing a resource

		//check if in the user has the DenyAccess role
		if(m_pSession.hasUserRole(document.rPath.getPathToApplication(), DENY_ACCESS_ROLE))
		{
			m_pSystem.doError("HTTPRequest.NoAccess", new String[]{m_pSession.getUserName(), document.rPath.getFullPath()}, this);
			return RET_FORBIDDEN;
		}

		if(bForceRedirect)
		{
			m_NewLocation = document.rPath.getFullPath();
			document.designObject = null;
			RequestReturnCode = RET_SEEOTHER;
			return RequestReturnCode;
		}

		if(document.hasItem(Document.PAGE_REDIRECT_ITEM))
		{
			document.rPath = new RequestPath(document.getItemValue(Document.PAGE_REDIRECT_ITEM));
		}

		//may have been set above when we checked for a resource, if so don't reget it
		if(design==null) design = getDesignElement(m_pSession, document.rPath.Application, document.rPath.Group, document.rPath.DesignElementName, document.rPath.DesignType);

		if(document.rPath.Action.compareToIgnoreCase(DesignElement.PARAMETER_SAVEPAGE) == 0)
		{
			document.designObject = design;
			return RET_OK;
		}

		if(design==null)
		{
			//perhaps try getting the requested resource from another place??
			document.designObject = null;
			RequestReturnCode = RET_FILENOTFOUND;
			return RequestReturnCode;
		}

		document.PageName = design.getDesignName();
		document.designObject = design;
		boolean bIsResource = design.getDesignType()==DesignElement.DESIGN_TYPE_RESOURCE;
		if(bIsResource && !hasResourceChanged(design))    
			RequestReturnCode = RET_NOT_MODIFIED;            
		else
		{
			if(bIsResource)
			{
				Date dtLastModified = design.getLastModified();

				Date dtExpires = new Date();
				long lDiff = (System.currentTimeMillis() - dtLastModified.getTime()) / 2;
				if(lDiff<0) lDiff = 0;
				if(lDiff>0) dtExpires = Util.adjustDate(dtExpires, 0, 0, 0, 0, 0, (int) (lDiff/1000) );
				String sLastGMTMod = Util.formatDate(dtLastModified, LAST_MOD_DATE, Locale.UK, m_tzGMT);
				document.setExtraHeaderValue("Last-Modified", sLastGMTMod, true);
				String sExpiresGMT = Util.formatDate(dtExpires, LAST_MOD_DATE, Locale.UK, m_tzGMT);
				document.setExtraHeaderValue("Expires", sExpiresGMT, true);
			}
			RequestReturnCode = RET_OK;
		}
		return RequestReturnCode;
	}

	/**
	 * Determines if the current request is for the custom login page in the current application
	 */
	private boolean isAccessingLoginPage(Document document)
	{
		String sCustomLoginPage = getAppParam(m_pSession, DesignElement.DESIGN_PAGE_LOGIN, document.rPath.Group, document.rPath.Application); 
		if(sCustomLoginPage==null || sCustomLoginPage.length()==0) return false;

		if(sCustomLoginPage.equalsIgnoreCase(document.rPath.DesignElementName)) return true;

		return false;
	}

	/**
	 * Convenience method, can probably be removed later
	 * @param design
	 * @return
	 */
	private boolean hasResourceChanged(DesignElement design)
	{
		return hasResourceChanged(design.getLastModified());
	}

	/**
	 * Determines if the design element has changed since the date If-Modified-Since
	 * sent by the client
	 */
	private boolean hasResourceChanged(Date dtLastModified)
	{
		String sIfModSince = Util.getMIMELine(m_environment_lines, "If-Modified-Since");		
		if(sIfModSince==null) return true;
		//resend byteserves
		String sRange = Util.getMIMELine(m_environment_lines, "Range");
		if(sRange!=null) return true;

		int iPos = sIfModSince.indexOf(';');
		if(iPos>0) sIfModSince = sIfModSince.substring(0, iPos);
		String sLastGMTMod = Util.formatDate(dtLastModified, LAST_MOD_DATE, Locale.UK, m_tzGMT);
		//System.out.println("If-Modified-Since: "+sIfModSince);
		//System.out.println("Last Mod:          "+sLastGMTMod);
		boolean bTheSame = sLastGMTMod.equals(sIfModSince);
		//System.out.println("hasResourceChanged()="+!bTheSame);
		return !bTheSame;
	}


	/**
	 * Determines if the session has this role in the application
	 * if iType==0 we don't know what type of design element to look for, so
	 * just find the first match based on the name in actions, resources and pages.
	 *
	 */
	public DesignElement getDesignElement(HTTPSessionContext pSession, String szApplication, String szAppGroup, String szDesignName, int iType)
	{
		if(iType==DesignElement.DESIGN_TYPE_UNKNOWN) iType = DesignElement.DESIGN_TYPE_HTTPACCESSIBLE;
		return m_http_server.getDesignElement(szAppGroup, szApplication, szDesignName, iType);
	}

	public DesignElement getDesignElement(SessionContext pSession, String szApplication, String szAppGroup, String szDesignName, int iType)
	{
		return m_http_server.getDesignElement(szAppGroup, szApplication, szDesignName, iType);
	}



	/**
	 * Gets an application parameter. if there are multiple parameters with the same key
	 * they are returned comma seperated ie "x,y,z"
	 *
	 */
	public String getAppParam(HTTPSessionContext pSession, String sParamName, String sGroup, String sApplication)
	{
		return m_http_server.getAppParam(pSession.getSessionContext(), sParamName, sGroup, sApplication);  
	}



	/**
	 * returns "http://your.host.com/somepath.pma/bla?OpenForm"
	 */
	public String makeBaseRef(String sHost, String sURI)
	{
		StringBuilder sbAddress = new StringBuilder(256);
		int iPos;
		//System.out.println("["+szPath+"]");
		sbAddress.append(m_sHTTPURLPrefix);
		sbAddress.append("://");
		sbAddress.append(sHost);
		iPos = sURI.indexOf(m_http_server.getPuakmaFileExt());    
		if(iPos>0)
			sURI = sURI.substring(0, iPos+4) + '/';
		else
		{
			iPos = sURI.lastIndexOf('/');
			if(iPos>0) sURI = sURI.substring(0, iPos+1);
		}
		sbAddress.append(sURI);
		//System.out.println(szAddress.toString());
		return sbAddress.toString();
	}



	/**
	 * Expire entire design cache if older than given age.
	 * Pass 0 to expire everything
	 */
	public void expireDesignCache(long lAgeInMilliSeconds)
	{
		HTTPServer.expireDesignCache(lAgeInMilliSeconds);
	}

	/**
	 * Expire design cache item if older than given age.
	 */
	public void removeDesignCacheItem(String sKey)
	{
		HTTPServer.removeDesignCacheItem(sKey);
	}

	/**
	 * Removes an object from the global system cache
	 */
	public void removeGlobalObject(String sKey)
	{ 
		m_pSystem.removeGlobalObject(sKey);

	}

	/**
	 * Gets an object from global system cache
	 */
	/*public Object getGlobalObject(String sKey)
	{
		return m_pSystem.getGlobalObject(sKey);
	}*/

	/**
	 * @deprecated use setGlobalObject() 28/4/2009 BJU
	 * @param oItem
	 * @return
	 */
	/*public boolean storeGlobalObject(Object oItem)
	{
		return setGlobalObject(oItem);
	}*/

	/**
	 * Stores an object in global system cache
	 * @param oItem must implement puakma.pooler.CacheableItem
	 * @return true if the object was stored successfully
	 */
	/*public boolean setGlobalObject(Object oItem)
	{
		return m_pSystem.setGlobalObject(oItem);
	}*/


	/**
	 * Allow other tasks to execute console commands
	 * @param szCommand
	 * @return a string containing the results of the command. CRLF breaks each line
	 */
	public String doConsoleCommand(String szCommand)
	{
		return m_http_server.doConsoleCommand(szCommand);
	}

	/**
	 * Determines if the current request if for the business widget server.
	 * Assume the parameter is LOWERCASE!!
	 * @param sPath
	 * @return
	 */
	private boolean isWidgetRequest(String sLowerPath)
	{
		if(sLowerPath.indexOf(DesignElement.PARAMETER_WIDGETEXECUTE)>0) return true;
		if(sLowerPath.indexOf(DesignElement.PARAMETER_WIDGETWSDL)>0) return true;
		if(sLowerPath.indexOf(DesignElement.PARAMETER_WIDGETLIST)>0) return true;

		return false;
	}

	/**
	 *
	 * @param doc
	 */
	private void doWidgetRequest(HTMLDocument doc)
	{ 

		//long lStart = System.currentTimeMillis();
		//m_pSystem.doDebug(0, "A. doWidgetRequest() " + (System.currentTimeMillis()-m_lStart) + "ms", this);
		ArrayList extra_headers = new ArrayList();
		String sAuth="WWW-Authenticate: Basic realm=\"BusinessWidget\"";
		m_pSystem.doDebug(pmaLog.DEBUGLEVEL_FULL, "doWidgetRequest(%s)", new String[]{m_sInboundPath}, this);

		//if you don't have the WebServiceAccess role AND the app has one, then lock them out.
		boolean bCanAccessWS = m_pSession.hasUserRole(m_pSession.getRequestPath().getPathToApplication(), ACCESS_WS_ROLE);
		if(!bCanAccessWS)
		{
			if(HTTPServer.appHasRole(m_pSystem, m_pSession.getRequestPath(), ACCESS_WS_ROLE))
			{
				if(!m_pSession.isLoggedIn()) extra_headers.add(sAuth);
				sendHTTPResponse(RET_UNAUTHORIZED, "NOT AUTHORIZED", extra_headers, HTTP_VERSION, "text/plain", ("You do not have the "+ACCESS_WS_ROLE+" role in this application required to access BusinessWidgets").getBytes());
				return;
			}
		}

		//there is no WebServiceAccess role in the db.
		//if the user is allowed to access web services, then don't check if they are allowed access to the app
		//this will allow users to only access web services and none of the other parts of the app - so, better security
		if(!bCanAccessWS)
		{
			boolean bCanAccessApp = m_pSession.hasUserRole(m_pSession.getRequestPath().getPathToApplication(), ACCESS_APP_ROLE);
			if(!bCanAccessApp)
			{
				if(!m_pSession.isLoggedIn()) extra_headers.add(sAuth);
				sendHTTPResponse(RET_UNAUTHORIZED, "NOT AUTHORIZED", extra_headers, HTTP_VERSION, "text/plain", "You are not authorized to access this application".getBytes());
				return;
			}
		}


		AddInMessage msg = new AddInMessage();
		msg.setParameter("RequestMethod", m_sInboundMethod);
		msg.setParameter("RequestPath", m_sInboundPath);
		msg.setParameter("BasePath", m_sBaseRef);
		String sEncoding = doc.getItemValue("@Content-Encoding");
		if(sEncoding!=null) msg.setParameter("Content-Encoding", sEncoding); //for gzip content
		String sAcceptEncoding = doc.getItemValue("@Accept-Encoding");
		if(sAcceptEncoding!=null) msg.setParameter("Accept-Encoding", sAcceptEncoding); //for gzip content
		msg.SessionID = m_pSession.getSessionID();
		String sTimeOut = puakma.util.Util.getMIMELine(m_environment_lines, "Session-Timeout");
		if(sTimeOut!=null && sTimeOut.length()>0)
		{
			try
			{
				int iNewTimeOut = Integer.parseInt(sTimeOut);
				m_pSession.getSessionContext().setSessionTimeOut(iNewTimeOut);
			}catch(Exception y){}
		}
		if(doc.hasItem(Document.DATA_ITEM_NAME))
		{

			DocumentItem di = doc.getItem(Document.DATA_ITEM_NAME);
			if(di.getType()==DocumentItem.ITEM_TYPE_FILE)
			{
				DocumentFileItem dfi = (DocumentFileItem)di;
				dfi.setDeleteOnExit(false); //let the other process handle the cleanup
				msg.ContentType = dfi.getMimeType();
				msg.Attachment = (File)dfi.getObject();
				msg.DeleteAttachmentWhenDone=true;
			}
			else
			{
				msg.ContentType = doc.getContentType();
				msg.Data = di.getValue();
			}
		}//if has data
		else //must be a GET
		{
			// anything??
		}

		//m_pSystem.doInformation("Sending message to addin: [" + szAddInClass + "]", this);
		//m_pSystem.doDebug(0, "B doWidgetRequest() " + (System.currentTimeMillis()-lStart) + "ms", this);
		if(m_pSession!=null) extra_headers.add(m_pSession.getCookieString());    
		AddInMessage am = m_pSystem.sendMessage(m_http_server.getWidgitServer(), msg); 
		//m_pSystem.doDebug(0, "C doWidgetRequest() " + (System.currentTimeMillis()-lStart) + "ms", this);

		if(am!=null) sEncoding = am.getParameter("Content-Encoding");    
		if(sEncoding!=null && sEncoding.length()>0) extra_headers.add("Content-Encoding: " + sEncoding);
		if(am!=null && am.Status==AddInMessage.STATUS_SUCCESS)
		{      
			//TODO: deal with files being returned!!        
			sendHTTPResponse(RET_OK, "OK", extra_headers, HTTP_VERSION, am.ContentType, am.Data);
		}
		else
		{
			if(am==null)
				sendHTTPResponse(RET_BADREQUEST, "BAD REQUEST", extra_headers, HTTP_VERSION, "text/plain", "Web service request failed".getBytes());
			else
			{
				if(am.Status==AddInMessage.STATUS_NOT_AUTHORIZED)
				{
					if(!m_pSession.isLoggedIn()) extra_headers.add(sAuth);
					sendHTTPResponse(RET_UNAUTHORIZED, "NOT AUTHORIZED", extra_headers, HTTP_VERSION, "text/plain", ("You are not authorized to perform that function").getBytes());
				}
				else
					sendHTTPResponse(RET_BADREQUEST, "BAD REQUEST", extra_headers, HTTP_VERSION, am.ContentType, am.Data);
			}
		}

	}



	/**
	 * Should we zip the output where possible
	 */
	public boolean shouldGZipOutput(String sContentType)
	{
		//m_pSystem.doDebug(0, "shouldGZipOutput() ["+sContentType + "] " + m_http_request_line, this);
		if(sContentType==null || sContentType.length()==0 || !m_http_server.shouldGZipOutput())  return false;

		//if the content-type is split over multiple lines or has crap appended
		//to the end, trim it off
		int iPos = sContentType.indexOf('\r');
		if(iPos>0)
		{
			sContentType = sContentType.substring(0, iPos);
		}
		String sAcceptEncoding = Util.getMIMELine(m_environment_lines, "Accept-Encoding");
		if(sAcceptEncoding!=null && sAcceptEncoding.indexOf("gzip")>=0) 
		{
			//only compress text data, eg text/html, text/xml, application/x-javascript etc
			//don't compress gif files and zip files
			if(sContentType!=null  && 
					(sContentType.startsWith("text") || 
							sContentType.endsWith("jpg") || 
							sContentType.endsWith("jpeg") || 
							sContentType.equals("application/x-javascript")  || 
							sContentType.equals("application/javascript")  || 
							sContentType.equals("application/json"))) return true;

			//!sContentType.equals("image/gif") && !sContentType.endsWith("jpg"))  return true;
		}

		return false;
	}

	public String getErrorSource()
	{
		return "HTTPRequestManager (" + m_iHTTPPort + ")";
	}

	public String getErrorUser()
	{
		if(m_pSession==null)
			return pmaSystem.SYSTEM_ACCOUNT + " [" + m_sock.getInetAddress().getHostAddress() + "]";
		else
			return m_pSession.getUserName() + " [" + m_pSession.getSessionContext().getHostAddress() + "]";
	}

	/**
	 * return details about this thread
	 *
	 */
	public String getThreadDetail() 
	{                  
		String sUser = "";
		String sPath = "";
		if(m_pSession!=null) 
		{
			X500Name nm = new X500Name(m_pSession.getUserName());
			sUser = nm.getAbbreviatedName();
		}
		long lMS = System.currentTimeMillis() - m_lStart;
		if(m_sInboundPath!=null) sPath = m_sInboundPath;
		return sUser + " " + sPath + " " + (int)(lMS/1000) + "sec";
	}








}//class



