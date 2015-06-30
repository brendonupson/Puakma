/** ***************************************************************
SOAPClient.java
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

package puakma.SOAP;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;

import puakma.addin.http.action.HTTPSessionContext;
import puakma.server.AddInMessage;
import puakma.system.RequestPath;
import puakma.util.Util;

/**
 * This class is used to invoke SOAP methods, or web services on remote hosts. It
 * is designed to work in the style of XML-RPC eg:
 * SOAPClient sc = new SOAPClient();
 * sc.setURL("http://someurl/");
 * sc.setMethod("MethodToInvoke");
 * sc.addParameter(p1);
 * Object o = sc.execute();
 *
 * By default messages will be compressed with gzip
 */
public class SOAPClient
{
	private final static String XML_ENCODING = "encoding=\"UTF-8\"";
	public final static String PUAKMA_SESSION_ID = "_pma_sess_id";
	private String m_sURL;
	private String m_sMethod;
	private String m_sService;
	private String m_sSessionID=null; //for authentication
	private String m_sUserName=null; //for authentication
	private String m_sPassword=null; //for authentication
	private String m_sProxyHostPort=null; //for proxy eg "host:3129"
	private String m_sProxyUserName=null; //for proxy authentication
	private String m_sProxyPassword=null; //for proxy authentication
	private ArrayList m_alParams = new ArrayList();
	private long m_lCallMS=-1;
	private String m_sSOAPReturn=null;
	private boolean m_bCompressMessages=true; //default is true
	SOAPCallParser m_scp = new SOAPCallParser();

	/**
	 * default constructor
	 */
	public SOAPClient()
	{
	}

	/**
	 * Constructor for setting all the important parts at once
	 * @param sURL
	 * @param sService
	 * @param sMethod
	 */
	public SOAPClient(String sURL, String sService, String sMethod)
	{
		m_sURL = sURL;
		m_sService = sService;
		m_sMethod = sMethod;
	}

	/**
	 * Constructor for setting all the important parts at once, for a Puakma
	 * service. This constructor implies the service name is embedded in the url
	 * @param sURL
	 * @param sMethod
	 */
	public SOAPClient(String sURL, String sMethod)
	{
		m_sURL = sURL;
		m_sMethod = sMethod;

		m_sService = getServiceNameFromURL(sURL);

		/*int iPos = sURL.lastIndexOf('?');
      if(iPos>=0)
      {
        String sFirstPart = sURL.substring(0, iPos);
        iPos = sURL.lastIndexOf('/');
        if(iPos>=0) m_sService = sFirstPart.substring(iPos+1);
      }
		 */


	}

	/**
	 *
	 */
	private String getServiceNameFromURL(String sURL)
	{
		int iPos = sURL.lastIndexOf('?');
		if(iPos>=0)
		{
			String sFirstPart = sURL.substring(0, iPos);
			iPos = sURL.lastIndexOf('/');
			if(iPos>=0) return sFirstPart.substring(iPos+1);
		}
		else //now we'll try to get the last part of the url
		{
			if(sURL.endsWith("/")) sURL = sURL.substring(0, sURL.length()-1);
			iPos = sURL.lastIndexOf('/');
			if(iPos>=0) return sURL.substring(iPos+1);
		}

		return null;
	}

	/**
	 * Test harness
	 */
	public static void main(String[] args)
	{
		//SOAPClient sc = new SOAPClient();
		//sc.setURL("https://192.168.0.4/system/SOAPDesigner.pma/SOAPDesigner?WidgetExecute");        
		//sc.setProxyServer("localhost:3128");

		//public long saveKeyword(int appId, long kwId, String name, String[] datas)
		//sc.setURL("http://localhost/test.pma/wtest?WidgetExecute");
		//sc.setMethod("getTime"); 

		try
		{
			/*sc.addParameter(new Integer(1));
            sc.addParameter(new Long(2));
            sc.addParameter("Yowser!");
            sc.addParameter(new String[0]);
			 */
			//System.out.println(sc.getSOAPQuery().toString());
			for(int i=0; i<3; i++)
			{
				SOAPClient sc = new SOAPClient();
				sc.setURL("http://localhost/test.pma/wtest?WidgetExecute");
				sc.setMethod("getTime"); 
				String s = (String)sc.execute();
				System.out.println(i + ": " + s);
			}
			/*
            System.out.println("--------------------------");
            System.out.println(sc.getSOAPQueryReply().toString());
            //String s = new String((byte[])sc.execute());
            System.out.println("--------------------------");
            //System.out.println(s);
            //System.out.println(new String((byte[])o));
            System.out.println("--------------------------");
			 */
			//System.out.println("Call took " + sc.getCallTimeMS() + "ms");
			Thread.sleep(10000);
		}
		catch(Exception e)
		{
			System.out.println(e.toString());
			e.printStackTrace();
		}
		//System.out.println("sess="+sc.getSessionID());
	}

	/**
	 * Sends the widget request internally via memory to the widgie instance
	 * running in this JVM. Useful for calling widgets from HTTP actions or calling
	 * widgets from other widgets.
	 * @return null of the request failed
	 */
	public Object executeInternal(HTTPSessionContext pSession) throws Exception
	{        		
		m_scp = new SOAPCallParser(); //clears errors etc
		//TODO check roles!!
		/*
         EEEEEEKKKK. Should we check app roles or not?!?!
		 */

		long lStart = System.currentTimeMillis();
		String sQuery = getSOAPQuery().toString();
		AddInMessage msg = new AddInMessage();
		msg.setParameter("RequestMethod", "POST");
		String sURI=m_sURL;
		if(m_sURL.indexOf("://")>0) //full url specified
		{
			URL url = new URL(this.m_sURL);
			sURI = url.getFile();
		}
		RequestPath rp = new RequestPath(sURI);

		msg.setParameter("RequestPath", rp.getFullPath());        
		msg.setParameter("BasePath", rp.getPathToApplication());
		msg.ContentType = "text/xml";
		msg.Data = sQuery.getBytes("UTF-8");
		msg.SessionID = pSession.getSessionID();

		AddInMessage am = pSession.getSystemContext().sendMessage("widgie", msg);    
		if(am!=null)
		{    
			//System.out.println("am!=null");
			//TODO: deal with files being returned!!        
			if(am.Status==AddInMessage.STATUS_SUCCESS)
			{
				//System.out.println("am.Status==SUCCESS");
				m_sSOAPReturn = new String(am.Data, "UTF-8");
				ByteArrayInputStream bis = new ByteArrayInputStream(am.Data);        
				m_scp.parseStream(bis);

				Object oReturn = m_scp.getObjectToReturn();        
				m_lCallMS = System.currentTimeMillis() - lStart;
				if(oReturn instanceof SOAPFaultException) throw (SOAPFaultException)oReturn;
				return oReturn;
			}
			//not successful
			throw new Exception("Widget request failed. Status code: " + am.Status + " " + am.toString());
		}
		/*else
		{
			if(am!=null && am.Status!=AddInMessage.STATUS_SUCCESS) //failed :-(
				throw new Exception("Widget request failed");
		}*/		
		return null;
	}

	/**
	 * Convenience method for executing the web service, uses server default timeout
	 * @return
	 * @throws Exception
	 */
	public Object execute() throws SOAPFaultException, IOException
	{
		return execute(-1);
	}

	/**
	 * Calls the web service. The reply is cached in memory, so we assume that
	 * there is enough room for the content-length
	 * @param iTimeOutMins in seconds of the http connection
	 * @return the result of the web service
	 */
	public Object execute(int iTimeOutMins) throws SOAPFaultException, IOException
	{
		m_scp = new SOAPCallParser(); //clears errors etc
		//Date dtStart = new Date();
		long lStart = System.currentTimeMillis();
		//int MAX_CHUNK = 20428;//20K
		URLConnection connection=null;

		//do proxy setup
		if(m_sProxyHostPort!=null)
		{                                    
			System.setProperty("http.proxyHost", m_sProxyHostPort) ;
			System.setProperty("http.proxyPort", "80");
			int iPos = m_sProxyHostPort.indexOf(':');
			if(iPos>0)
			{
				String sHost = m_sProxyHostPort.substring(0, iPos);
				String sPort = m_sProxyHostPort.substring(iPos+1);
				if(sPort.length()==0) sPort = "80";
				System.setProperty("http.proxyHost", sHost) ;
				System.setProperty("http.proxyPort", sPort);                
			}            
		}

		URL url = new URL(m_sURL);
		if(url.getProtocol().equalsIgnoreCase("https"))
		{                                
			try
			{
				SSLContext ctx = SSLContext.getInstance("SSL");            
				TrustManager tmArray[] = new TrustManager[1];
				tmArray[0] = new puakma.util.RelaxedTrustManager();          
				ctx.init(null, tmArray, null);

				SSLSocketFactory sf = ctx.getSocketFactory();
				HttpsURLConnection.setDefaultSSLSocketFactory(sf); 
				HttpsURLConnection.setDefaultHostnameVerifier(new puakma.util.RelaxedHostnameVerifier());
				HttpsURLConnection shurl = (HttpsURLConnection)url.openConnection();
				connection = (URLConnection)shurl; 
			}
			catch(Exception e)
			{
				throw new SOAPAuthenticationException("SSL Setup Failed", e.getMessage());
			}
		}
		else
		{
			connection = url.openConnection();            
		}                 

		connection.setDoInput( true );
		connection.setDoOutput( true );
		if(m_sProxyUserName!=null) connection.setRequestProperty( "Proxy-Authorization", "Basic "+hashUserNamePassword(m_sProxyUserName, m_sProxyPassword) );
		if(m_sUserName!=null) connection.setRequestProperty( "Authorization", "Basic "+hashUserNamePassword(m_sUserName, m_sPassword));
		if(m_sSessionID!=null) connection.setRequestProperty( "Cookie", PUAKMA_SESSION_ID+'='+ m_sSessionID);
		connection.setRequestProperty( "Content-Type", "text/xml" );        
		if(iTimeOutMins>=0) connection.setRequestProperty( "Session-Timeout", ""+iTimeOutMins );
		connection.setRequestProperty( "User-Agent", "Puakma/SOAPClient" );
		connection.setRequestProperty( "SOAPAction", '\"' + m_sURL + '\"' );
		if(m_bCompressMessages)
		{
			connection.setRequestProperty( "Content-Encoding", "gzip" );
			connection.setRequestProperty( "Accept-Encoding", "gzip" );
		}

		DataOutputStream output = new DataOutputStream(connection.getOutputStream() );                        

		String s = getSOAPQuery().toString();
		byte bufReply[]=null;

		try{ bufReply = s.getBytes("UTF-8"); }catch(Exception t){}
		//System.out.println(s); //for debug
		if(m_bCompressMessages)
		{            
			output.write(puakma.util.Util.gzipBuffer(bufReply));
			//System.out.println("----------------> sent compressed"); //for debug
		}
		else
		{
			output.write(bufReply);
			//System.out.println("----------------> sent uncompressed"); //debug
		}
		output.close();

		HttpURLConnection hc = (HttpURLConnection)connection;
		int iReplyCode = hc.getResponseCode();
		//System.out.println(iReplyCode + " "+hc.getResponseMessage() + "  Using proxy:"+hc.usingProxy());
		if(iReplyCode!=200)//(iReplyCode<200 || iReplyCode>=300)
		{
			if(iReplyCode>=400 && iReplyCode<404) 
				throw new SOAPAuthenticationException(""+iReplyCode, hc.getResponseMessage());

			throw new SOAPFaultException(""+iReplyCode, hc.getResponseMessage());
		}

		//System.out.println(connection.getHeaderField(0));
		//CHECK FOR "HTTP/1.1 200 OK"
		DataInputStream input = new DataInputStream(connection.getInputStream() );
		//this piece of shenanigans is so we can interrogate the raw xml return if we need to
		int iLen = connection.getContentLength();
		String sEncoding = connection.getHeaderField("Content-Encoding"); 
		processSessionCookie(connection);

		ByteArrayOutputStream baos = new ByteArrayOutputStream(iLen);
		int iTotalRead=0;
		byte buf[] = new byte[8192]; //8K?
		while(iTotalRead<iLen)
		{
			int iRead = input.read(buf);
			iTotalRead += iRead;
			if(iRead>0)
			{
				baos.write(buf, 0, iRead);
			}   
			else
				try{Thread.sleep(10);}catch(Exception f){}
		}//while 
		input.close();
		bufReply = baos.toByteArray();         
		if(sEncoding!=null && sEncoding.equalsIgnoreCase("gzip"))
		{
			//System.out.println(iLen+" ----------------> gzip reply from server"); //debug
			bufReply = puakma.util.Util.ungzipBuffer(bufReply);
		}        
		m_sSOAPReturn = new String(bufReply, "UTF-8");
		/*System.out.println(m_sSOAPReturn); 
        //System.out.println("buffer="+bufReply.length + " package=" + m_sSOAPReturn.length());
        for(int i=0; i<bufReply.length; i++)
        {
            byte b = bufReply[i];
            if(b==0x00) System.out.print("_"+i);
            else System.out.print(String.valueOf((char)b));
        }*/


		ByteArrayInputStream bis = new ByteArrayInputStream(bufReply);

		try
		{
			m_scp.parseStream(bis);
		}
		catch(Exception r)
		{
			throw new SOAPFaultException("SOAPClient parseStream() Failed", r.getMessage());
		}


		Object oReturn = m_scp.getObjectToReturn();        
		m_lCallMS = System.currentTimeMillis() - lStart;
		if(oReturn instanceof puakma.SOAP.SOAPFaultException) throw (puakma.SOAP.SOAPFaultException)oReturn;
		return oReturn;
	}

	/**
	 * Gets the Puakma session ID from the HTTP reply
	 */
	private void processSessionCookie(URLConnection connection)
	{
		//Map mHeaders = connection.getHeaderFields();
		int i=0;
		String sHeader = connection.getHeaderField(i);
		while(sHeader!=null)
		{

			String sKey = connection.getHeaderFieldKey(i);
			if(sKey!=null && sKey.equalsIgnoreCase("Set-Cookie"))
			{
				//this might be a session cookie....
				int iPos = sHeader.indexOf(PUAKMA_SESSION_ID);
				if(iPos>=0)
				{
					//System.out.println(i + " " + sHeader + " --> " + sKey);                
					sHeader = sHeader.substring(iPos + PUAKMA_SESSION_ID.length()+1);
					iPos = sHeader.indexOf(';');
					if(iPos>0) m_sSessionID = sHeader.substring(0, iPos);
					break;
				}
			}
			i++;
			sHeader = connection.getHeaderField(i);
		}        
		//System.out.println(" --> " + m_sSessionID);
		//m_sSessionID
	}

	/**
	 * Sets the username and password in the http header
	 * @param sUserName
	 * @param sPassword
	 */
	public String hashUserNamePassword(String sUserName, String sPassword)
	{
		if(sUserName==null || sPassword==null) return "";
		String sUserNamePassword = sUserName + ':' + sPassword;
		return Util.base64Encode(sUserNamePassword.getBytes());
	}


	/**
	 * Used to determine what the server replied with. Useful for debug.
	 * @return
	 */
	public String getSOAPQueryReply()
	{
		return m_sSOAPReturn;
	}


	/**
	 *
	 * @return
	 */
	public StringBuilder getSOAPQuery()
	{
		StringBuilder sbOut = new StringBuilder(2048);
		sbOut.append("<?xml version=\"1.0\" " + XML_ENCODING + " ?>\r\n");
		sbOut.append("<soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\" \r\n");
		sbOut.append("\txmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" \r\n");
		sbOut.append("\txmlns:xsd=\"http://www.w3.org/2001/XMLSchema\"\r\n");
		sbOut.append("\txmlns:soapenc=\"http://schemas.xmlsoap.org/soap/encoding/\">\r\n");
		sbOut.append("<soap:Body>\r\n\r\n");

		sbOut.append("\t<f:" + m_sMethod + " xmlns:f=\"urn:" + m_sService + "\" soap:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\">\r\n");

		for(int i=0; i<m_alParams.size(); i++)
		{
			Object obj = m_alParams.get(i);
			String sParam = m_scp.getObjectRepresentation("param"+(i+1), obj, false);
			sbOut.append("\t\t" + sParam + "\r\n");
		}

		sbOut.append("\t</f:" + m_sMethod + ">\r\n\r\n");

		sbOut.append("</soap:Body>\r\n");
		sbOut.append("</soap:Envelope>\r\n");
		return sbOut;
	}

	/**
	 * Empties the list of parameters
	 */
	public void clearParameters()
	{
		synchronized(m_alParams)
		{
			m_alParams = new ArrayList();
		}
	}

	/**
	 * Set the session ID you want to use to access the service
	 * @param paramSessID
	 */
	public void setSessionID(String paramSessID)
	{
		m_sSessionID = paramSessID;
	}

	/**
	 * Get the session ID that this object is using. May return null
	 * @return The session id
	 */
	public String getSessionID()
	{
		return m_sSessionID;
	}

	/**
	 * Set the session ID from the session object. Added for convenience.
	 * @param sessCtx
	 */
	/*public void setSessionID(SessionContext sessCtx)
    {
      if(sessCtx==null) return;
      m_sSessionID = sessCtx.getSessionID();
    }*/

	/**
	 *
	 * @param sUserName
	 * @param sPassword
	 */
	public void setUserNamePassword(String sUserName, String sPassword)
	{
		if(sUserName==null || sPassword==null) return;
		m_sPassword = sPassword;
		m_sUserName = sUserName;
	}

	public void setProxyServer(String sHost)
	{
		m_sProxyHostPort = sHost;
	}

	/**
	 * The account to use for proxy connections
	 * @param sUserName
	 * @param sPassword
	 */
	public void setProxyUserNamePassword(String sUserName, String sPassword)
	{
		if(sUserName==null || sPassword==null) return;
		m_sProxyPassword = sPassword;
		m_sProxyUserName = sUserName;
	}

	/**
	 * Adds a parameter to the end of the list
	 */
	public void addParameter(Object oAdd)
	{
		synchronized(m_alParams)
		{
			m_alParams.add(oAdd);
		}
	}

	public void addParameter(int iParam)
	{
		addParameter(new Integer(iParam));
	}

	public void addParameter(long lParam)
	{
		addParameter(new Long(lParam));
	}

	public void addParameter(float fParam)
	{
		addParameter(new Float(fParam));
	}

	public void addParameter(double dParam)
	{
		addParameter(new Double(dParam));
	}

	public void addParameter(boolean bParam)
	{
		addParameter(new Boolean(bParam));
	}

	/**
	 * Set the method we will be invoking on the host
	 * @param Method Name
	 */
	public synchronized void setMethod(String sNewMethod)
	{
		m_sMethod = sNewMethod;
	}

	/**
	 * Set the service we will be invoking on the host
	 * @param sNewService
	 */
	public synchronized void setService(String sNewService)
	{
		m_sService = sNewService;
	}

	/**
	 * Set the method we will be invoking on the host
	 * @param sNewURL
	 */
	public synchronized void setURL(String sNewURL)
	{
		m_sURL = sNewURL;
		if(m_sService==null) m_sService = getServiceNameFromURL(m_sURL);        
	}



	/**
	 * Gets the number of milliseconds the execute() function took to complete
	 * @return -1 if it did not yet complete, otherwise a value greater than 0
	 */
	public long getCallTimeMS()
	{
		return m_lCallMS;
	}

	public synchronized void setCompressMessages(boolean bNewVal)
	{
		m_bCompressMessages = bNewVal;
	}

}//end class
