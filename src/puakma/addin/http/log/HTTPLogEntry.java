/** ***************************************************************
HTTPLogEntry.java
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

package puakma.addin.http.log;

import java.util.ArrayList;

import puakma.system.RequestPath;
import puakma.system.SessionContext;


/**
 * This will be created and populated by the HTTP server, then passed into HTTPLogger
 */
public class HTTPLogEntry
{
	private String m_sHTTPRequest;
	private String m_sContentType;  
	//private String m_sUserName;
	private String m_sSystemHostName;
	private String m_sRequestedHost;
	private long m_lSize;
	//private long m_lInboundSize;
	private long m_lTransactionMS;
	private int m_iReturnCode;
	private String m_sClientIPAddress;
	private String m_sServerIPAddress;
	private int m_iPort=80;
	private String m_sClientHostName;
	private java.util.Date m_dtNow = new java.util.Date();
	//private SimpleDateFormat m_sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
	private boolean m_bIgnoreStat=false;
	private String m_sMethod;
	private ArrayList<String> m_arrInboundHeaders = new ArrayList<String>();
	private ArrayList<String> m_arrOutboundHeaders = new ArrayList<String>();
	private String m_sProtocol="";
	private String m_sURI="";
	private RequestPath m_rp = new RequestPath("/");
	private SessionContext m_pSession;

	public HTTPLogEntry(){}//default, should never be used

	/**
	 * Set up the object
	 */
	public HTTPLogEntry(ArrayList<String> alMimeExcludes, ArrayList<String> alInboundHeaders, ArrayList<String> alOutboundHeaders, String szHTTPRequest, String szContentType, long lSize, long lInboundSize, int iReturnCode, String szClientIPAddress, String szClientHostName, String szSystemHostName, String szRequestedHost, long lTrans, String sServerIPAddress, int iPort, SessionContext sess)
	{
		if(alInboundHeaders!=null) m_arrInboundHeaders = alInboundHeaders;
		if(alOutboundHeaders!=null) m_arrOutboundHeaders = alOutboundHeaders;
		m_pSession = sess;
		m_sHTTPRequest = szHTTPRequest;
		if(m_sHTTPRequest==null) m_sHTTPRequest="";
		if(szContentType==null) szContentType="www/unknown";
		int iPos = szContentType.indexOf('\r');
		if(iPos>=0) m_sContentType = szContentType.substring(0, iPos);
		else m_sContentType = szContentType;
		m_lSize = lSize;
		//m_lInboundSize = lInboundSize;
		m_lTransactionMS = lTrans;
		m_iReturnCode = iReturnCode;
		m_sClientIPAddress = szClientIPAddress;
		m_sClientHostName = szClientHostName;
		if(m_sClientHostName==null || m_sClientHostName.length()==0) m_sClientHostName="-";
		if(m_sClientIPAddress==null || m_sClientIPAddress.length()==0) m_sClientIPAddress="-";

		m_sSystemHostName = szSystemHostName;
		if(m_sSystemHostName==null) m_sSystemHostName="";
		m_sServerIPAddress = sServerIPAddress;
		if(m_sServerIPAddress==null) m_sServerIPAddress="";

		m_sRequestedHost = szRequestedHost;
		if(m_sRequestedHost==null) m_sRequestedHost = "";
		m_iPort=iPort;
		ArrayList<String> arr = puakma.util.Util.splitString(m_sHTTPRequest, ' ');
		if(arr!=null)
		{
			if(arr.size()>0) m_sMethod = (String)arr.get(0);
			if(arr.size()>1) m_sURI = (String)arr.get(1);
			if(arr.size()>2) m_sProtocol = (String)arr.get(2);
		}
		m_rp = new RequestPath(m_sURI);
		if(m_sMethod==null) m_sMethod="-";
		m_bIgnoreStat = shouldExclude(alMimeExcludes);
	}

	/**
	 * Returns TRUE if this stat should be ignored
	 */
	private boolean shouldExclude(ArrayList<String> alMimeExcludes)
	{
		int i;
		String sMimeExclude;

		if(alMimeExcludes==null) return false;
		for(i=0; i<alMimeExcludes.size(); i++)
		{
			sMimeExclude = (String)alMimeExcludes.get(i);
			if(sMimeExclude.equalsIgnoreCase(m_sContentType)) return true;
		}
		return false;
	}

	public boolean shouldLog()
	{
		return !m_bIgnoreStat;
	}


	public String getClientIP()
	{
		return m_sClientIPAddress;
	}

	public String getClientHostName()
	{
		return m_sClientHostName;
	}

	public String getLocalIP()
	{
		return m_sServerIPAddress;
	}

	public String getFileName()
	{
		return m_rp.DesignElementName;
	}

	public long getResponseBytes()
	{
		return m_lSize;
	}

	public long getServeMS()
	{
		return m_lTransactionMS;
	}

	/**
	 * The actual host name
	 */
	public String getServerName()
	{
		return m_sSystemHostName;
	}

	/**
	 * The entry in the Host: header
	 */
	public String getRequestedServerName()
	{
		return m_sRequestedHost;
	}

	public int getServerPort()
	{
		return m_iPort;
	}

	public String getRequestLine()
	{
		return m_sHTTPRequest; //"GET /foo.pma/Design?OpenPage&id=3 HTTP/1.1";
	}

	public String getRequestProtocol()
	{
		return m_sProtocol;
	}

	public String getRequestMethod()
	{
		return m_sMethod;
	}

	public String getPathToDesign()
	{
		return m_rp.getPathToDesign();
	}

	public String getQueryString()
	{
		// include the ?
		int iPos = m_sURI.indexOf('?');
		if(iPos>=0) return m_sURI.substring(iPos, m_sURI.length());
		return "";
	}

	public int getReturnStatus()
	{
		return m_iReturnCode;
	}

	public String getUserNameNoSpaces()
	{
		String sLoginName = null;
		if(m_pSession!=null) sLoginName = m_pSession.getUserNameAbbreviated();
		if(sLoginName==null || sLoginName.length()==0) sLoginName = "Anonymous";
		return sLoginName.replaceAll(" ", "_");
	}

	public String getCanonicalUserName()
	{
		if(m_pSession!=null) return m_pSession.getUserName();
		return "Anonymous";
	}

	public java.util.Date getRequestDate()
	{
		return m_dtNow;
	}

	public String getRequestHeader(String sHeaderName)
	{
		return removeCRLF(puakma.util.Util.getMIMELine(m_arrInboundHeaders, sHeaderName));        
	}

	public String getReplyHeader(String sHeaderName)
	{
		return removeCRLF(puakma.util.Util.getMIMELine(m_arrOutboundHeaders, sHeaderName));
	}

	/**
	 * Someitmes a line may include a CRLF as the programmer tries to crap additional headers into
	 * for example, the content-type. This will return only the parts prior to the cr or lf.
	 */
	private String removeCRLF(String sIn)
	{
		if(sIn==null) return null;

		int iPos = sIn.indexOf('\r'); //check CRLF or only CR
		if(iPos>=0) sIn = sIn.substring(0, iPos);
		iPos = sIn.indexOf('\n'); //someone may have entered LFCR or only LF
		if(iPos>=0) sIn = sIn.substring(0, iPos);
		return sIn;
	}

	/**
	 * returns null if not found. note: is case sensitive
	 */
	public String getCookieValue(String sCookieName)
	{
		String sCookie = puakma.util.Util.getMIMELine(m_arrInboundHeaders, "Cookie");
		if(sCookie==null || sCookie.length()==0) return null;
		int iPos = sCookie.indexOf(sCookieName+'=');
		if(iPos>=0)
		{
			String sValue = sCookie.substring(iPos + (sCookieName.length()+1));
			iPos = sValue.indexOf(';');
			if(iPos>=0) sValue = sValue.substring(0, iPos);
			return sValue;
		}
		return null;
	}

	/**
	 * Keep-Alive or close or null
	 */
	public String getConnectionState()
	{
		return puakma.util.Util.getMIMELine(m_arrOutboundHeaders, "Connection");
	}

}//class