/** ***************************************************************
CLUSTERThread.java
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
package puakma.addin.cluster;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.StringTokenizer;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.NodeList;

import puakma.addin.pmaAddInStatusLine;
import puakma.error.ErrorDetect;
import puakma.error.pmaLog;
import puakma.system.SystemContext;
import puakma.system.pmaSession;
import puakma.system.pmaSystem;
import puakma.system.pmaThreadInterface;
import puakma.util.Util;

/**
 * For sending and receiving cluster info. There should be one of these threads between
 * each pair in the cluster
 */
public class CLUSTERThread implements pmaThreadInterface, ErrorDetect
{
	private CLUSTER m_cluster;
	//public SessionContext m_pSession;
	public SystemContext m_pSystem;
	private long request_id;
	private Socket m_sock;
	private int m_iCLUSTERPort;
	private boolean m_bSecure;
	private boolean m_bLooping=true;
	private BufferedReader m_is;
	private BufferedOutputStream m_os;
	private String m_http_request_line;  // Stores the GET/POST request line
	private String m_sInboundMethod;
	private String m_sInboundPath;

	//private double m_fHTTPVersion=1.0;

	private pmaAddInStatusLine m_pStatus;
	private ArrayList m_environment_lines; // Stores all other data lines sent to us

	private boolean m_bSetupOK=false;
	private DocumentBuilder m_parser=null;
	private DocumentBuilderFactory m_dbf=null;
	private long m_lLastTrans=0;
	private Hashtable m_htPushQueue = new Hashtable();	
	private String m_sHostAddress = null;



	/**
	 * constructor used by CLUSTERListener
	 */
	CLUSTERThread(SystemContext paramSystem, CLUSTER server, Socket s, String paramHost, long rq_id, boolean bSecure)
	{
		m_bSecure = bSecure;
		m_cluster = server;
		m_sock = s;
		request_id = rq_id;
		m_pSystem = paramSystem; //this is shared
		try
		{ 
			m_iCLUSTERPort = m_sock.getLocalPort(); 
			m_sock.setTcpNoDelay(true);
		} catch(Exception e){}

		//m_sHost = paramHost;
		m_bSetupOK = doSetup();

		if(!m_cluster.allowConnect(s.getInetAddress().getHostAddress()))
		{
			m_pSystem.doError("CLUSTERThread.HostNotAllowed", new String[]{s.getInetAddress().getHostAddress()}, this);
			m_bSetupOK = false;
			return;
		}

		m_dbf = DocumentBuilderFactory.newInstance();
		try
		{
			m_parser = m_dbf.newDocumentBuilder();
		}
		catch(Exception e)
		{
			m_pSystem.doError("CLUSTERThread.XMLParserError", new String[]{e.toString()}, this);
			m_bSetupOK = false;
		}
	}

	/**
	 *
	 * @return
	 */
	public boolean isOK()
	{
		return m_bSetupOK && m_sock.isConnected() && 
				!m_sock.isInputShutdown() && !m_sock.isOutputShutdown() &&
				!m_sock.isClosed();
	}


	/**
	 * determines if the connection is SSL or not.
	 * @return
	 */
	public boolean isSecureConnection()
	{
		return m_bSecure;
	}


	/*public void shutdown()
	{		
		m_bLooping = false;
	}*/


	/**
	 * Invoked by the HTTPServer for each request.  Do not use publicly.
	 */
	public void run()
	{
		m_pSystem.doDebug(pmaLog.DEBUGLEVEL_FULL, "run()", this);
		// if setup m_is successful, hand it off
		if(m_bSetupOK)
		{

			m_sHostAddress   = m_sock.getInetAddress().getHostAddress();
			//int iCount=0;
			m_pStatus = m_cluster.createStatusLine(" " + "CLUSTERThread");
			m_pStatus.setStatus("Connected to " + m_sHostAddress);
			m_pSystem.doInformation("CLUSTERThread.Join", new String[]{m_sHostAddress}, this);

			while(m_bLooping)
			{			
				try
				{							
					if(m_sock.getInputStream().available()<1)
					{
						Thread.sleep(50);
						continue;
					}
					
					m_http_request_line = m_is.readLine();
					if(m_http_request_line==null || m_http_request_line.length()==0) continue;
					//System.out.println(m_http_request_line);
				}	
				catch(InterruptedException inte)
				{
					continue;
				}
				catch(Exception ioe)
				{
					m_pSystem.doError("CLUSTERThread.ConnectionLost", new String[]{ioe.toString()}, this);
					m_bLooping=false;
					break;
				}
				if(m_bLooping) processIncomingRequest();
				try{ m_os.flush(); } catch(Exception j){}

				//if the client says close the connection, then close it.
				String sCloseConnection = Util.getMIMELine(m_environment_lines, "Connection");
				if(sCloseConnection!=null && sCloseConnection.equalsIgnoreCase("close"))
				{
					m_pSystem.doInformation("CLUSTERThread.Leave", new String[]{m_sHostAddress}, this);
					m_bLooping=false;
				}
			}
		}
		doCleanup();
		m_cluster.removeStatusLine(m_pStatus);
	}

	/**
	 * tidy up the loose ends
	 */
	protected void finalize()
	{
		doCleanup();
		if(m_pStatus!=null) m_cluster.removeStatusLine(m_pStatus);
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
			m_is = new BufferedReader(new InputStreamReader(input, "ISO-8859-1"));

			OutputStream output_stream = m_sock.getOutputStream();
			m_os = new BufferedOutputStream(output_stream);
		}
		catch (IOException ioe)
		{
			m_pSystem.doError("HTTPRequest.BufferedStream", new String[]{String.valueOf(request_id),ioe.getMessage()}, this);
			return (false);
		}

		return (true);
	}


	/**
	 * Handles the http request
	 */
	private void processIncomingRequest()
	{
		m_pSystem.doDebug(pmaLog.DEBUGLEVEL_FULL, "processRequest()", this);

		try
		{
			m_environment_lines = new ArrayList();
			// Wait here for an HTTP GET/POST request line
			//m_http_request_line = m_is.readLine();
			//if(m_http_request_line==null) return false;
			m_pSystem.doDebug(pmaLog.DEBUGLEVEL_STANDARD, "REQUEST: '%s'", new String[]{m_http_request_line}, this);
			//System.out.println( request_id + " "+m_http_request_line);

			// Now parse the request line and decide what to do
			StringTokenizer st = new StringTokenizer(m_http_request_line);
			m_sInboundMethod = st.nextToken();
			m_sInboundPath = st.nextToken();

			boolean bContinue=false;
			ArrayList alMembers = Util.splitString(m_sInboundPath, ',');
			for(int i=0; i<alMembers.size(); i++)
			{
				if(m_cluster.isClusterMember((String)alMembers.get(i)))
				{
					bContinue=true;
					break;
				}
			}
			if(!bContinue)//not a member of this cluster!
			{
				m_pSystem.doError("CLUSTERThread.NotMember", new String[]{m_sock.getInetAddress().getHostName(), m_sock.getInetAddress().getHostAddress()}, this);
				m_bLooping = false;
				return;
			}

			String version = "HTTP/1.0";

			// Get the HTTP version string that the client sent in, if any
			if (st.hasMoreTokens()) version = st.nextToken();

			try
			{
				int iPos = version.lastIndexOf('/');
				if(iPos>=0) version = version.substring(iPos+1, version.length());
				//m_fHTTPVersion = Float.parseFloat(version);
			}
			catch(Exception k){ /*m_fHTTPVersion=1;*/ }
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


			if(m_sInboundMethod.equals("CLUSTER"))
			{
				setLastTrans();
				/*for(int i=0; i<m_environment_lines.size(); i++)
              {
                String s = (String)m_environment_lines.get(i); //+"\r\n";
              }*/

				String sHeartbeat = Util.getMIMELine(m_environment_lines, "Heartbeat");
				if(sHeartbeat!=null && m_cluster.isDebug()) m_pSystem.doDebug(0, "Heartbeat message received", this);
				String sLen = Util.getMIMELine(m_environment_lines, "Content-Length");
				if(sLen!=null && sHeartbeat==null) //ignore heartbeats
				{
					int iContentLength = (int)Util.toInteger(sLen);
					if(iContentLength>0)
					{
						char[] cBuf = new char[iContentLength];
						int iRead = m_is.read(cBuf);
						byte[] buf = new byte[iRead];
						for(int i=0; i<iRead; i++) buf[i] = (byte)cBuf[i];
						//if(m_cluster.isDebug()) m_pSystem.doDebug(0, iContentLength + " bytes cluster data received", this);
						ByteArrayInputStream bais = new ByteArrayInputStream(buf);
						org.w3c.dom.Document document = m_parser.parse(bais);
						NodeList rootnode = document.getElementsByTagName("session"); //get root node
						if(rootnode.getLength()>=0) processSession(rootnode);
					}
				}//sLen!=null
				else
				{
					String sRequest = Util.getMIMELine(m_environment_lines, "Request-Session");
					if(sRequest!=null && sRequest.length()>0) m_cluster.pushSessions(this, sRequest, true);

				}
			}
			else
				m_bLooping = false; //bail out, some crappy request

		}//try
		catch (Exception ioe)
		{
			if(m_http_request_line==null) m_http_request_line="";
			m_pSystem.doError("HTTPRequest.BasicError", new String[]{ioe.toString(), m_http_request_line}, this);
			Util.logStackTrace(ioe, m_pSystem, -1);
		}
	}


	private void processSession(NodeList nlRoot)
	{
		boolean bProcessedAll = false;
		pmaSession sess = new pmaSession();
		try
		{
			bProcessedAll = sess.populateSessionFromXML(nlRoot.item(0));
		}
		catch(Exception e)
		{
			m_pSystem.doError("CLUSTERThread.SessionParseError", new String[]{e.toString()}, this);
			return;
		}
		if(!bProcessedAll) return;		
		m_pSystem.registerSession(sess);
		sess.setSynchAcrossCluster(false);
		sess.setObjectChanged(false);
		if(m_cluster.isDebug()) m_pSystem.doDebug(0, "Session received: " + sess.userName + " " + sess.getFullSessionID(), this);
	}       

	/**
	 * Send data out to the connected cluster mate
	 * @param arHeaders
	 * @param body
	 */
	public synchronized boolean sendData(ArrayList arHeaders, byte[] body)
	{
		if(!isOK())
		{
			//System.out.println("--- SEND DATA: SOCKET CLOSED ---");
			return false;
		}
		String CRLF = "\r\n";

		//System.out.println("--- SEND DATA ---");
		//if(m_cluster.isDebug()) m_pSystem.doDebug(0, "Sending cluster data...", this);
		StringBuilder sbHeaders = new StringBuilder(260);

		//arHeaders.add("Host: " + m_sHost);
		if(body!=null) arHeaders.add("Content-Length: " + body.length);
		for(int i=0; i<arHeaders.size(); i++)
		{
			sbHeaders.append((String)arHeaders.get(i)+CRLF);
		}
		sbHeaders.append(CRLF);

		try
		{
			m_os.write(Util.utf8FromString(sbHeaders.toString()));
			if(body!=null) m_os.write(body);
			m_os.flush();
		}
		catch(Exception e)
		{
			m_pSystem.doError("CLUSTERThread.SendError", new String[]{getConnectedHostAddress(), e.toString()}, this);
			this.destroy();
		}
		setLastTrans();

		return true;
	}


	/**
	 * Sends a heartbeat message to the remote system if this connection has been 
	 * idle for more than 30s
	 *
	 */
	public void sendHeartbeat()
	{
		long lDiff = System.currentTimeMillis()-m_lLastTrans;
		if(lDiff<30000) return;
		if(m_cluster.isDebug()) m_pSystem.doDebug(0, "Sending Heartbeat...", this);
		ArrayList arHeaders = m_cluster.getStandardHTTPHeader(true);
		arHeaders.add("Heartbeat: (|)");
		sendData(arHeaders, null);
	}


	/**
	 * Close the input and output streams
	 */
	private void doCleanup()
	{
		//System.out.println("start doCleanup()");
		m_pSystem.doDebug(pmaLog.DEBUGLEVEL_FULL, "doCleanup()", this);
		m_bLooping=false;

		try
		{
			m_is.close();					
		}
		catch(IOException io1){}

		try
		{
			m_os.close();
		}
		catch(IOException io2){}
		
		try
		{			
			m_sock.close();
		}
		catch(IOException io3){}
	}

	public void destroy()
	{				
		doCleanup();
	}


	/**
	 * Find out what we're connected to
	 * @return
	 */
	public String getConnectedHostAddress()
	{
		if(m_sHostAddress!=null) return m_sHostAddress;

		m_sHostAddress = m_sock.getInetAddress().getHostAddress();
		return m_sHostAddress;
	}

	/**
	 * Tags the last transaction time
	 */
	private synchronized void setLastTrans()
	{
		m_lLastTrans = System.currentTimeMillis();
	}

	public String getErrorSource()
	{
		return "CLUSTERThread(" + m_iCLUSTERPort + ')';
	}

	public String getErrorUser()
	{
		//if(m_pSession==null)
		return pmaSystem.SYSTEM_ACCOUNT + " [" + m_sHostAddress + ']';
		//else
		//  return m_pSession.getUserName() + " [" + m_pSession.getInternetAddressString() + "]";
	}



	public String getThreadDetail() 
	{
		return "ClusterThread";
	}

	public void addToPushQueue(String sSessionID, StringBuilder sb) 
	{
		//if it's already in the queue remove it - we want the latest representation of the session state
		if(m_htPushQueue.containsKey(sSessionID)) m_htPushQueue.remove(sSessionID);

		if(sb==null) sb = new StringBuilder();
		m_htPushQueue.put(sSessionID, sb);		
	}

	public void processPushQueue()
	{
		//System.out.println("processPushQueue() " + new Date());
		ArrayList arHeaders = m_cluster.getStandardHTTPHeader(true);

		Iterator itKeys = m_htPushQueue.keySet().iterator();
		while(itKeys.hasNext())
		{
			boolean bRemove = false;
			String sKey = (String)itKeys.next();
			StringBuilder sb = (StringBuilder)m_htPushQueue.get(sKey);
			if(sb.length()==0)
			{
				ArrayList arSend = (ArrayList)arHeaders.clone();
				//System.out.println("...requesting " + sKey);
				arSend.add("Request-Session: "+sKey);
				bRemove = this.sendData(arSend, null);
			}
			else
			{
				//System.out.println("...pushing " + sKey);
				ArrayList arSend = (ArrayList)arHeaders.clone();
				byte[] body = Util.utf8FromString(sb.toString());
				//if(body!=null) arSend.add("Content-Length: " + body.length);
				//sendClusterInfoToAll(arSend, body);
				//pushToAll(arSend, body);
				bRemove = this.sendData(arSend, body);
			}
			if(bRemove) itKeys.remove(); //remove last element processed if it was sent ok
		}
	}

	public int getPushQueueItemCount() 
	{
		return m_htPushQueue.size();
	}

	public String getStatus() 
	{
		String sLast = "never";
		if(m_lLastTrans>0) 
		{
			Calendar cal = Calendar.getInstance();
			cal.setTimeInMillis(m_lLastTrans);
			sLast = Util.formatDate(cal.getTime(), "shortdatetime");
		}
		
		return getConnectedHostAddress()+"  q:"+getPushQueueItemCount() + " ok:"+isOK() + " last:" + sLast;
	}

}



