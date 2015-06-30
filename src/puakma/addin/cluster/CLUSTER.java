/** ***************************************************************
CLUSTER.java
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

import java.net.Socket;
import java.util.ArrayList;
import java.util.StringTokenizer;
import java.util.Vector;

import javax.net.ssl.SSLSocketFactory;

import puakma.addin.pmaAddIn;
import puakma.addin.pmaAddInStatusLine;
import puakma.error.ErrorDetect;
import puakma.server.AddInMessage;
import puakma.system.SessionContext;
import puakma.system.pmaSystem;
import puakma.system.pmaThread;
import puakma.system.pmaThreadInterface;
import puakma.system.pmaThreadPoolManager;
import puakma.util.Util;


/**
 * <p>Title: </p>
 * <p>Description: </p>
 * <p>Copyright: Copyright (c) 2002</p>
 * <p>Company: </p>
 * @author Brendon Upson
 * @version 1.0
 */

public class CLUSTER extends pmaAddIn implements ErrorDetect
{	
	public static final String SESSION_WILDCARD = "*";
	public pmaThreadPoolManager m_tpm;


	private Vector m_Listeners = new Vector();
	private boolean m_bDebug = false;
	private pmaAddInStatusLine m_pStatus;
	private String m_sClusterMemberships="";
	private ArrayList m_alClusterMemberships = new ArrayList();
	private String m_sClusterServers="";
	private ArrayList m_alClusterServers = new ArrayList();
	private ArrayList m_alAllowList;

	private CLUSTERReconnectThread m_crt;
	private boolean m_bConnectInProgress=false;
	private boolean m_bSynchOnConnect=true;

	/**
	 * This method is called by the pmaServer object
	 */
	public void pmaAddInMain()
	{
		setAddInName("CLUSTER");
		m_pStatus = createStatusLine();
		m_pStatus.setStatus("Starting...");
		m_pSystem.doInformation("CLUSTER.Startup", this);

		String szTemp =m_pSystem.getSystemProperty("CLUSTERDebug");
		if(szTemp!=null && szTemp.equals("1")) m_bDebug = true;

		szTemp = m_pSystem.getSystemProperty("CLUSTERSynchOnConnect");
		if(szTemp!=null && !szTemp.equals("1")) m_bSynchOnConnect = false;


		m_sClusterMemberships = m_pSystem.getSystemProperty("CLUSTERMember");
		m_alClusterMemberships = Util.splitString(m_sClusterMemberships, ',');
		if(m_alClusterMemberships==null) m_alClusterMemberships=new ArrayList();


		m_sClusterServers = m_pSystem.getSystemProperty("CLUSTERServers");
		m_alClusterServers = Util.splitString(m_sClusterServers, ',');
		if(m_alClusterServers==null) m_alClusterServers=new ArrayList();

		String sAllow = m_pSystem.getSystemProperty("CLUSTERAllow");
		m_alAllowList = Util.splitString(sAllow, ',');

		// startup the thread pool manager
		int max_pooled_threads = 100;
		int min_pooled_threads = 5;
		int thread_pool_timeout = 3000;
		m_tpm = new pmaThreadPoolManager(m_pSystem, min_pooled_threads, max_pooled_threads, thread_pool_timeout, "cluster");
		m_tpm.start();

		loadListeners();
		m_crt = new CLUSTERReconnectThread(m_pSystem, this);
		m_crt.start();
		//setupConnections();

		while(!this.addInShouldQuit())
		{
			m_pStatus.setStatus("Running.");
			if(addInSecondsHaveElapsed(5))
			{
				Vector v = m_pSystem.getAllSessions();
				for(int i=0; i<v.size(); i++)
				{
					SessionContext sess = (SessionContext)v.elementAt(i);
					if(sess.shouldSynchAcrossCluster())
					{
						//m_pSystem.doInformation("Clustering " + sess.getSessionID(), sess);
						if(isDebug()) m_pSystem.doDebug(0, "Clustering " + sess.getSessionID(), this);
						sess.setObjectChanged(false);
						sess.setSynchAcrossCluster(false);						
						addSessionToThreads(sess);
					}
				}//for
				pushQueuedSessions();

			}//if
			//pushHeartbeatToAll();
			checkThreads();
			try{Thread.sleep(1000);}catch(Exception e){}
		}//end while
		m_tpm.requestQuit();
		m_crt.requestQuit();
		m_pStatus.setStatus("Shutting down");
		requestQuit();
		waitForRunners();
		m_pSystem.doInformation("CLUSTER.Shutdown", this);
		removeStatusLine(m_pStatus);
	}

	private void pushQueuedSessions() 
	{
		Vector vClusterMates = m_tpm.getActiveObjects();
		for(int i=0; i<vClusterMates.size(); i++)
		{
			CLUSTERThread cThread = (CLUSTERThread)vClusterMates.elementAt(i);
			if(cThread!=null)
			{
				if(cThread.isOK()) 
				{
					cThread.processPushQueue();
					cThread.sendHeartbeat();
				}
			}
		}
	}

	private void checkThreads() 
	{
		Vector vClusterMates = m_tpm.getActiveObjects();
		for(int i=0; i<vClusterMates.size(); i++)
		{
			CLUSTERThread cThread = (CLUSTERThread)vClusterMates.elementAt(i);
			if(cThread!=null)
			{
				if(!cThread.isOK()) vClusterMates.remove(i);
			}
		}
	}

	/**
	 * Connect to a remote host and send a chunk of hello data
	 * @param sHost
	 */
	public CLUSTERThread connect(String sHost, int iPort, boolean bSSL) throws Exception
	{		

		if(isConnectedToHost(sHost)) return null;

		boolean bOK=false;
		Socket sock=null;

		if(bSSL)
		{
			SSLSocketFactory sslFact = (SSLSocketFactory)SSLSocketFactory.getDefault();
			sock = sslFact.createSocket(sHost, iPort);
		}
		else
			sock = new Socket(sHost, iPort);
		//System.out.println("connecting " + new Date());
		CLUSTERThread cThread = new CLUSTERThread(m_pSystem, this, sock, sock.getInetAddress().getHostAddress(), 0, bSSL);
		//System.out.println("connected " + new Date());
		pmaThread pt = m_tpm.getNextThread();
		if(pt!=null)
		{
			if(pt.runThread((pmaThreadInterface)cThread)) bOK=true;
		}


		if(bOK)
		{
			if(m_bSynchOnConnect) 
			{
				ArrayList arrHeaders = getStandardHTTPHeader(true);
				arrHeaders.add("Request-Session: "+SESSION_WILDCARD);				
				cThread.sendData(arrHeaders, null);
			}
		}
		else
		{
			m_pSystem.doError("CLUSTER.ConnectError", new String[]{sHost, ""+iPort}, this);
			cThread = null;
		}
		return cThread;
	}


	/**
	 * Determines if this server is a member of the named cluster
	 * @param sCluster
	 * @return
	 */
	public boolean isClusterMember(String sParamCluster)
	{
		for(int i=0; i<m_alClusterMemberships.size(); i++)
		{
			String sCluster = (String)m_alClusterMemberships.get(i);
			if(sCluster.equalsIgnoreCase(sParamCluster)) return true;
		}
		return false;
	}


	/**
	 * Add a sesison to the internal hastable. If it exists, replace it.
	 * @param sess
	 */
	private void addSessionToThreads(SessionContext sess)
	{   
		//if(m_htSessions.containsKey(sess.getSessionID())) return;

		StringBuilder sb = new StringBuilder(512);
		sb.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\r\n");
		sb.append(sess.getXMLRepresentation());
		String sSessionID = sess.getSessionID();
		//m_htSessions.put(sess.getSessionID(), sb);
		Vector vConnections = m_tpm.getActiveObjects();
		for(int i=0; i<vConnections.size(); i++)
		{
			CLUSTERThread ct = (CLUSTERThread)vConnections.elementAt(i);
			if(ct!=null) ct.addToPushQueue(sSessionID, sb);//sendData(arHeaders, body);
		}


	}

	public ArrayList getStandardHTTPHeader(boolean bKeepAlive)
	{
		ArrayList arHeaders = new ArrayList();
		arHeaders.add("CLUSTER " + m_sClusterMemberships);
		if(bKeepAlive)
			arHeaders.add("Connection: Keep-Alive");
		else
			arHeaders.add("Connection: Close");
		arHeaders.add("Client: " + m_pSystem.getSystemProperty("SystemHostName"));
		arHeaders.add("Content-Type: text/clusterinfo");

		return arHeaders;
	}



	/**
	 * Synchronized everything on this machine with all clusterbuddies
	 */
	public void synchAll()
	{
		if(isDebug()) m_pSystem.doDebug(0, "Synch all sessions and servers", this);
		Vector vClusterMates = m_tpm.getActiveObjects();
		ArrayList arHeaders = getStandardHTTPHeader(true);
		arHeaders.add("Request-Session: *");
		for(int i=0; i<vClusterMates.size(); i++)
		{
			CLUSTERThread cThread = (CLUSTERThread)vClusterMates.elementAt(i);
			if(cThread!=null)
			{
				pushSessions(cThread, null, true);
				//issue a pull request
				cThread.addToPushQueue(SESSION_WILDCARD, null);
				//cThread.sendData(arHeaders, null);
			}
		}
	}

	public void sendSessionImmediatelyToAllClusterMates(String sSessionID)
	{
		if(sSessionID==null) return;

		Vector vClusterMates = m_tpm.getActiveObjects();	
		for(int i=0; i<vClusterMates.size(); i++)
		{
			CLUSTERThread cThread = (CLUSTERThread)vClusterMates.elementAt(i);
			if(cThread!=null)
			{
				pushSessions(cThread, sSessionID, true);				
			}
		}
	}

	/**
	 * Called when a thread requests a session or sessions be sent.
	 * @param cThread
	 * @param sRequest
	 */
	public void pushSessions(CLUSTERThread cThread, String sRequest, boolean bImmediate)
	{
		if(sRequest==null) //send all
		{
			if(isDebug()) m_pSystem.doDebug(0, "processSessionRequest() * " + cThread.getConnectedHostAddress(), this);

			Vector v = m_pSystem.getAllSessions();
			for(int i=0; i<v.size(); i++)
			{
				SessionContext sess = (SessionContext)v.elementAt(i);
				if(isDebug()) m_pSystem.doDebug(0, "Cluster synch: " + sess.getSessionID() + " " + cThread.getConnectedHostAddress(), this);
				sess.setObjectChanged(false);
				sess.setSynchAcrossCluster(false);				
				sendSession(cThread, sess, bImmediate);
			}//for
		}
		else //pull selected session
		{
			if(m_pSystem.sessionExists(sRequest))
			{
				SessionContext sessCtx = m_pSystem.getSession(sRequest);
				if(sessCtx!=null) sendSession(cThread, sessCtx, bImmediate);
			}
		}
	}

	private void sendSession(CLUSTERThread cThread, SessionContext sessCtx, boolean bImmediate)
	{
		//ArrayList arHeaders = getStandardHTTPHeader(true);
		if(isDebug()) m_pSystem.doDebug(0, "Sending session: " + sessCtx.getUserName() + " " + sessCtx.getSessionID() + " to " + cThread.getConnectedHostAddress(), this);
		//byte[] body = Util.utf8FromString(sessCtx.getXMLRepresentation().toString());
		//
		if(bImmediate && cThread.isOK())					
			cThread.sendData(getStandardHTTPHeader(true), Util.utf8FromString(sessCtx.getXMLRepresentation().toString()));		
		else
			cThread.addToPushQueue(sessCtx.getSessionID(), sessCtx.getXMLRepresentation());
	}



	public String getErrorSource()
	{
		return "CLUSTER";
	}

	public String getErrorUser()
	{
		return pmaSystem.SYSTEM_ACCOUNT;
	}

	/**
	 *
	 * @param szCommand
	 * @return
	 */
	public String tell(String szCommand)
	{
		String szReturn="";

		if(szCommand.equalsIgnoreCase("?") || szCommand.equalsIgnoreCase("help"))
		{
			return "->\r\n" +
					"->status\r\n" +
					"->synch\r\n" +
					"->disconnect\r\n" +
					"->debug on|off|status";
		}

		if(szCommand.toLowerCase().equals("status"))
		{
			return getClusterStatus();
		}

		if(szCommand.toLowerCase().equals("disconnect"))
		{
			disconnect();
			return "-> Dropping cluster connections";
		}

		if(szCommand.toLowerCase().equals("debug on"))
		{
			m_bDebug = true;
			return "-> Debug is now ON";
		}

		if(szCommand.toLowerCase().equals("debug off"))
		{
			m_bDebug = false;
			return "-> Debug is now OFF";
		}

		if(szCommand.toLowerCase().equals("debug status"))
		{
			if(m_bDebug) return "-> Debug is ON";

			return "-> Debug is OFF";
		}

		if(szCommand.toLowerCase().equals("synch"))
		{
			synchAll();
			return "-> Request sent";
		}



		return szReturn;
	}

	/**
	 * Loads each of the http listener objects
	 */
	private void loadListeners()
	{
		CLUSTERListener p;
		String szID;
		int iPort;
		String szPorts=m_pSystem.getSystemProperty("CLUSTERPorts");

		if(szPorts != null && szPorts.length()!=0)
		{
			StringTokenizer stk= new StringTokenizer(szPorts, ",", false);
			while (stk.hasMoreTokens())
			{
				szID = stk.nextToken();
				if(szID.toLowerCase().endsWith("ssl"))
				{
					iPort = Integer.parseInt(szID.substring(0, szID.length()-3));
					p = new CLUSTERListener(m_pSystem, iPort, this, true);
				}
				else
				{
					iPort = Integer.parseInt(szID);
					p = new CLUSTERListener(m_pSystem, iPort, this, false);
				}
				if(p!=null)
				{
					p.start();
					m_Listeners.add(p);
				}
			}//end while
		}//end if
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
				CLUSTERListener s = (CLUSTERListener)m_Listeners.elementAt(i);
				if(s.isAlive()) iActiveCount++;
			}
			m_pStatus.setStatus("Shutting down. Waiting for " + iActiveCount + " tasks to complete");
		}//while

		int loopcount = 0;
		int active_count = 0;
		int iShutDownSeconds = 2;

		try{ Integer.parseInt(m_pSystem.getSystemProperty("CLUSTERShutdownWaitSeconds")); }
		catch(Exception e){}

		while((active_count = m_tpm.getActiveThreadCount()) > 0 && loopcount < iShutDownSeconds)
		{
			m_pSystem.doInformation("CLUSTER.ShutdownPending", new String[]{String.valueOf(active_count)}, this);
			loopcount++;
			try{ Thread.sleep (1000); } catch(InterruptedException ie) {}
		}//end while

		// not all threads have quit cleanly!
		if (active_count > 0)
			m_pSystem.doInformation("CLUSTER.ThreadsActive", new String[]{String.valueOf(active_count)}, this);

	}

	/**
	 * Returns a string describing the current state of the cluster
	 * @return
	 */
	public String getClusterStatus()
	{
		StringBuilder sbStatus = new StringBuilder(256);
		//    sbStatus.append("Listening on port: " + m_iCLUSTERPort + "\r\n");
		sbStatus.append("Peers connected: " + m_tpm.getActiveThreadCount() + "\r\n");
		sbStatus.append("Peer status: ");

		String sQueue = "";
		Vector vClusterMates = m_tpm.getActiveObjects();
		for(int i=0; i<vClusterMates.size(); i++)
		{
			CLUSTERThread t = (CLUSTERThread)vClusterMates.elementAt(i);
			if(t!=null) 
			{
				if(sQueue.length()>0) sQueue += "\r\n";
				sQueue += t.getStatus(); //t.getConnectedHostAddress()+":"+String.valueOf(t.getPushQueueItemCount());
			}
		}
		sbStatus.append(sQueue);

		sbStatus.append("\r\n"); //FIXME
		sbStatus.append("Member of the following clusters:\r\n");
		for(int i=0; i<m_alClusterMemberships.size(); i++)
		{
			sbStatus.append("\t" + (String)m_alClusterMemberships.get(i) + "\r\n");
		}
		return sbStatus.toString();
	}


	/**
	 * Returns true if the connecting HostIP is allowed to connect to this machine
	 * @param sHostIP
	 * @return
	 */
	public boolean allowConnect(String sHostIP)
	{
		if(m_alAllowList==null) return true;
		for(int i=0; i<m_alAllowList.size(); i++)
		{
			String sAllowIP = (String)m_alAllowList.get(i);
			if(sAllowIP!=null && sAllowIP.equals(sHostIP)) return true;
		}
		return false;
	}

	/**
	 * Determines is a given host is already connected
	 * @param sHost
	 * @return
	 */
	public boolean isConnectedToHost(String sHost)
	{
		Vector vClusterMates = m_tpm.getActiveObjects();
		for(int i=0; i<vClusterMates.size(); i++)
		{
			CLUSTERThread t = (CLUSTERThread)vClusterMates.elementAt(i);
			if(t!=null)
			{
				//System.out.println("isConnectedToHost() " + t.getConnectedHostName() + " " + t.getConnectedHostAddress());
				//if(t.getConnectedHostName().equalsIgnoreCase(sHost) || t.getConnectedHostAddress().equalsIgnoreCase(sHost)) return true;
				if(t.isOK())
				{
					if(t.getConnectedHostAddress().equalsIgnoreCase(sHost)) return true;
				}
				else
				{
					t.destroy();
				}
			}
		}

		return false;
	}

	/**
	 *
	 * @return
	 */
	public boolean isDebug()
	{
		return m_bDebug;
	}

	/**
	 * Request this task to stop
	 */
	public void requestQuit()
	{
		super.requestQuit();
		for(int i=0; i<m_Listeners.size(); i++)
		{
			CLUSTERListener s = (CLUSTERListener)m_Listeners.elementAt(i);
			s.requestQuit();			
		}
		//send a message to all clustermate to tell them to drop the connection
		//sendToAll(getStandardHTTPHeader(false), null);
		disconnect();

		this.interrupt();

	}

	public void cleanClusterMates()
	{
		//send a message to all clustermate to tell them to drop the connection
		Vector vClusterMates = m_tpm.getActiveObjects();
		for(int i=0; i<vClusterMates.size(); i++)
		{
			CLUSTERThread t = (CLUSTERThread)vClusterMates.elementAt(i);
			if(t!=null && !t.isOK())
			{
				System.out.println("Removing dead thread " + t.getConnectedHostAddress());				
				t.destroy();
				m_tpm.cleanPool();
			}
		}
	}


	/**
	 *
	 */
	public void disconnect()
	{
		//send a message to all clustermate to tell them to drop the connection
		Vector vClusterMates = m_tpm.getActiveObjects();
		for(int i=0; i<vClusterMates.size(); i++)
		{
			CLUSTERThread t = (CLUSTERThread)vClusterMates.elementAt(i);
			if(t!=null) 
			{
				t.sendData(getStandardHTTPHeader(false), null);//request close
				t.destroy();
			}
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
	 * Send data to all connections
	 * @param arHeaders
	 * @param body
	 */
	/*private void pushToAll(ArrayList arHeaders, byte[] body)
	{
		Vector vConnections = m_tpm.getActiveObjects();
		for(int i=0; i<vConnections.size(); i++)
		{
			CLUSTERThread ct = (CLUSTERThread)vConnections.elementAt(i);
			if(ct!=null) ct.sendData(arHeaders, body);
		}
	}*/


	/**
	 * Send a heartbeat message to all connections
	 * @param arHeaders
	 * @param body
	 */
	/*private void pushHeartbeatToAll()
	{
		Vector vConnections = m_tpm.getActiveObjects();
		for(int i=0; i<vConnections.size(); i++)
		{
			CLUSTERThread ct = (CLUSTERThread)vConnections.elementAt(i);
			if(ct!=null) ct.sendHeartbeat();
		}
	}*/

	/**
	 * Sets a flag to say there is a connection in progress. So we don't
	 * get duplicate connections to the same host.
	 * @param bInProgress
	 * @return
	 */
	public synchronized void setConnectInProgress(boolean bInProgress)
	{
		m_bConnectInProgress = bInProgress;
	}

	public boolean isConnectionInProgress()
	{
		return m_bConnectInProgress;
	}

	public void waitForConnection()
	{
		while(m_bConnectInProgress && !this.addInShouldQuit())
		{
			int iWait = (int)(Math.random()*100);
			//System.out.println("waiting " + iWait + "ms");
			try{ Thread.sleep(iWait); } catch(InterruptedException ie) {}
		}
	}


	public AddInMessage sendMessage(AddInMessage oMessage)
	{
		if(oMessage==null) return null;
		String sAction = oMessage.getParameter("action");
		if(sAction==null) return null;

		//System.out.println("CLUSTER sendMessage: " + oMessage.toString());
		if(sAction.equalsIgnoreCase("push"))
		{
			String sSessionID = oMessage.getParameter("SessionID");
			if(sSessionID==null || sSessionID.equals(SESSION_WILDCARD))
				this.synchAll();
			else
				this.sendSessionImmediatelyToAllClusterMates(sSessionID);
			return new AddInMessage(AddInMessage.STATUS_SUCCESS); 
		}

		if(sAction.equalsIgnoreCase("getSession"))
		{
			//System.out.println("getSession: " + oMessage.toString());
			String sSessionID = oMessage.getParameter("SessionID");

			if(m_tpm.getActiveThreadCount()<=0) 
			{
				m_crt.interrupt();
				//wait for 1 second
				try{Thread.sleep(1000);}catch(Exception e){}
			}
			if(sSessionID!=null && m_tpm.getActiveThreadCount()>0) //if we are connected to another node
			{
				if(isDebug()) m_pSystem.doDebug(0, "Requesting session: "+sSessionID, this);
				//ask all clustermates if they have the session
				Vector vClusterMates = m_tpm.getActiveObjects();
				for(int i=0; i<vClusterMates.size(); i++)
				{
					CLUSTERThread cThread = (CLUSTERThread)vClusterMates.elementAt(i);
					if(cThread!=null)
					{
						//issue a pull request
						cThread.addToPushQueue(sSessionID, null);
					}
				}//for
				int iLoopCount=0;
				while(iLoopCount<100)
				{
					//wait for 2 seconds
					try{Thread.sleep(18);}catch(Exception e){}
					if(m_pSystem.sessionExists(sSessionID) || sSessionID.equals(SESSION_WILDCARD)) return new AddInMessage(AddInMessage.STATUS_SUCCESS);;
					iLoopCount++;
				}
			}
			return null;
		}//getsession

		return null;
	}
	/**
	 * get a list of the hosts supposed to be in this cluster
	 * @return
	 */
	public ArrayList getClusterServerList()
	{
		return (ArrayList)m_alClusterServers.clone();
	}

	/**
	 *
	 */
	public boolean shouldSynchOnConnect()
	{
		return m_bSynchOnConnect;
	}

	/**
	 * only allow this to be loaded once
	 */
	public boolean canLoadMultiple()
	{
		return false;
	}
}