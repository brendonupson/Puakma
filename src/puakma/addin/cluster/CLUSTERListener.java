/** ***************************************************************
CLUSTERListener.java
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

import java.io.FileInputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.KeyStore;

import javax.net.ServerSocketFactory;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocketFactory;

import puakma.addin.pmaAddInStatusLine;
import puakma.error.ErrorDetect;
import puakma.error.pmaLog;
import puakma.system.SystemContext;
import puakma.system.pmaSystem;
import puakma.system.pmaThreadInterface;




/**
 * CLUSTERListener listens for incoming cluster connections.
 */
public class CLUSTERListener extends Thread implements ErrorDetect
{
	private ServerSocket m_ss;
	//private Socket m_cs;
	private CLUSTER m_Parent;
	private boolean m_bRunning=false;
	private boolean m_bSSL=false;
	private long request_id = 0;
	private int m_iCLUSTERPort=-1;
	private pmaAddInStatusLine pStatus;
	private SystemContext m_pSystem;
	//private FileOutputStream m_fout;
	private String m_sHTTPSSLKeyRing="";
	private String m_sHTTPSSLKeyRingPW="";
	//private boolean m_bLogToFile=false;
	private String m_sInterface=null;


	public CLUSTERListener(SystemContext paramCtx, int iPort, CLUSTER paramParent, boolean paramSSL)
	{
		m_bSSL = paramSSL;
		m_Parent = paramParent;
		m_pSystem = (SystemContext)paramCtx.clone();
		m_iCLUSTERPort = iPort;
		setDaemon(true);
	}

	/**
	 * This method is called by the CLUSTER object
	 */
	public void run()
	{
		m_bRunning = true;
		pStatus = m_Parent.createStatusLine(" " + getErrorSource());
		pStatus.setStatus("Starting...");
		//instance_name = m_pSystem.getSystemProperty("CLUSTERServerName");

		InetAddress cInterface=null;
		m_sInterface = m_pSystem.getSystemProperty("CLUSTERInterface");
		if(m_sInterface!=null)
		{
			try
			{
				cInterface = InetAddress.getByName(m_sInterface);
			}
			catch(Exception e){}
		}

		m_sHTTPSSLKeyRing = m_pSystem.getSystemProperty("CLUSTERSSLKeyRing");
		m_sHTTPSSLKeyRingPW = m_pSystem.getSystemProperty("CLUSTERSSLKeyRingPW");
		if(m_sHTTPSSLKeyRingPW==null) m_sHTTPSSLKeyRingPW="";

		//String szTemp =m_pSystem.getSystemProperty("CLUSTERLogToFile");
		//if(szTemp!=null && szTemp.equals("1")) m_bLogToFile = true;

		// prepare server socket for accept
		int iCLUSTERPortBacklog = Integer.parseInt(m_pSystem.getSystemProperty("CLUSTERPortBacklog"));
		if(iCLUSTERPortBacklog<1) iCLUSTERPortBacklog = 5;

		try
		{
			if(m_bSSL)
			{
				ServerSocketFactory ssf = getServerSocketFactory(m_bSSL);
				if(cInterface==null)
				{
					if(ssf!=null) m_ss = ssf.createServerSocket(m_iCLUSTERPort, iCLUSTERPortBacklog);
				}
				else
				{
					if(ssf!=null) m_ss = ssf.createServerSocket(m_iCLUSTERPort, iCLUSTERPortBacklog, cInterface);
				}
			}
			else
			{
				if(cInterface==null)
					m_ss = new ServerSocket (m_iCLUSTERPort, iCLUSTERPortBacklog);
				else
					m_ss = new ServerSocket (m_iCLUSTERPort, iCLUSTERPortBacklog, cInterface);

			}
		}
		catch(Exception ioe)
		{
			m_pSystem.doError("CLUSTERListener.NoPortOpen", new String[]{String.valueOf(m_iCLUSTERPort)}, this);
			//shutdownAll();
			if(m_sInterface!=null) m_pSystem.doError("CLUSTERListener.InterfaceError", new String[]{m_sInterface}, this);
			m_Parent.removeStatusLine(pStatus);
			return;
		}

		m_pSystem.doInformation("CLUSTERListener.Listening", new String[]{String.valueOf(m_iCLUSTERPort)}, this);

		// main loop
		while(m_bRunning)
		{

			try
			{
				updateStatusLine();
				Socket sock = m_ss.accept();
				request_id++;
				dispatch(sock);
			}
			catch(Exception ioe)
			{
				m_pSystem.doError("CLUSTERListener.AcceptFail", new String[]{ioe.getMessage()}, this);
			}
		}//end while
		pStatus.setStatus("Shutting down");
		m_pSystem.doInformation("CLUSTERListener.ShuttingDown", this);
		try
		{
			m_ss.close();
		}
		catch (IOException ioe)
		{
			m_pSystem.doError("CLUSTERListener.SocketCloseFail", this);
		}
		m_pSystem.doInformation("CLUSTERListener.SocketClosed", this);

		//shutdownAll();
		m_Parent.removeStatusLine(pStatus);
		//m_pSystem.clearDBPoolManager();
		m_pSystem.doInformation("CLUSTERListener.Shutdown", this);
	}


	private void updateStatusLine()
	{
		if(m_sInterface==null)
			pStatus.setStatus("Listening port:"+m_iCLUSTERPort);
		else
			pStatus.setStatus("Listening port:"+m_iCLUSTERPort + " interface:" + m_sInterface);
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
		//String sHostName = sock.getInetAddress().getHostName();
		String sHostAddress = sock.getInetAddress().getHostAddress();
		boolean bOK=false;
		m_pSystem.doDebug(pmaLog.DEBUGLEVEL_FULL, "dispatch()", this);

		if(m_Parent.addInShouldQuit()) return;

		m_Parent.waitForConnection();
		m_Parent.setConnectInProgress(true);
		if(m_Parent.isConnectedToHost(sHostAddress))
		{
			//drop the connection			
			try{ sock.close();  } catch(Exception cse){}
			m_Parent.setConnectInProgress(false);
			return;
		}

		if(this.m_pSystem.addressHasAccess(sHostAddress))
		{
			CLUSTERThread rm = new CLUSTERThread(m_pSystem, m_Parent, sock, sHostAddress, request_id, m_bSSL);
			m_Parent.setConnectInProgress(false);
			bOK=m_Parent.m_tpm.runThread((pmaThreadInterface)rm);
		}
		else
		{
			puakma.util.AntiHackWorker ahw = new puakma.util.AntiHackWorker(m_pSystem, sock);
			bOK=m_Parent.m_tpm.runThread((pmaThreadInterface)ahw);
		}
		if(!bOK)
		{
			m_pSystem.doError("CLUSTERListener.RequestThreadError", this);
			try
			{
				sock.close(); //drop the connection
			}
			catch(Exception cse){}
		}
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
		String szSSL="";
		if(m_bSSL) szSSL=" SSL";
		return "CLUSTERListener " + szSSL ;
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
				SSLContext ctx;
				KeyManagerFactory kmf;
				KeyStore ks;
				char[] passphrase = m_sHTTPSSLKeyRingPW.toCharArray(); //password to access keystore

				ctx = SSLContext.getInstance("SSL"); //TLS
				kmf = KeyManagerFactory.getInstance("SunX509");
				ks = KeyStore.getInstance("JKS");

				//ks.load(new FileInputStream("testkeys"), passphrase);
				ks.load(new FileInputStream(m_sHTTPSSLKeyRing), passphrase);
				//kmf.init(ks, passphrase);
				kmf.init(ks, passphrase);
				ctx.init(kmf.getKeyManagers(), null, null);

				ssf = ctx.getServerSocketFactory();
				return ssf;
			}
			catch (Exception e)
			{
				//e.printStackTrace();
				m_pSystem.doError("CLUSTERListener.SocketFactoryError", new String[]{e.getMessage()}, this);
				throw new Exception(e.toString());
			}
		}
		else
		{
			return ServerSocketFactory.getDefault();
		}
	}


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

}
