/** ***************************************************************
CLUSTERReconnectThread.java
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

import java.net.InetAddress;
import java.util.ArrayList;

import puakma.error.ErrorDetect;
import puakma.error.pmaLog;
import puakma.system.SystemContext;
import puakma.system.pmaSystem;

/**
 * For sending and receiving cluster info. There should be one of these threads between
 * each pair in the cluster
 */
public class CLUSTERReconnectThread extends Thread implements ErrorDetect
{
	public SystemContext m_pSystem;
	private boolean m_bLooping=true;
	//private pmaAddInStatusLine m_pStatus;
	//private java.util.Date m_dtStart = new java.util.Date(); //timestamp object creation so we can work out transaction time
	private CLUSTER m_Parent;


	/**
	 *
	 * @param paramCtx
	 * @param paramParent
	 */
	public CLUSTERReconnectThread(SystemContext paramCtx, CLUSTER paramParent)
	{
		m_Parent = paramParent;
		m_pSystem = (SystemContext)paramCtx.clone();
		setDaemon(true);
	}


	/**
	 * Invoked by the CLUSTER
	 */
	public void run()
	{
		m_pSystem.doDebug(pmaLog.DEBUGLEVEL_FULL, "run()", this);

		while(m_bLooping)
		{
			//check list of hosts against actual connections
			ArrayList<String> al = m_Parent.getClusterServerList();
			//TODO connect port AND SSL!!!
			int iCLUSTERPort=6969;
			int iMaxReconnect=18000; //18 seconds
			for(int i=0; i<al.size(); i++)
			{
				String sHost = (String)al.get(i);
				int iPort = iCLUSTERPort;
				int iPos = sHost.indexOf(':');
				if(iPos>0)
				{
					String sPort = sHost.substring(iPos+1, sHost.length());
					try{iPort = Integer.parseInt(sPort);}catch(Exception ie){}
					sHost = sHost.substring(0, iPos);
				}

				if(sHost!=null)
				{
					String sAddr = "";
					try
					{
						InetAddress ia = InetAddress.getByName(sHost);
						sAddr = ia.getHostAddress();
					}catch(Exception g){ /* go back to for */ }
					if(!(m_Parent.isConnectedToHost(sHost) || m_Parent.isConnectedToHost(sAddr)))
					{
						if(m_Parent.isDebug()) m_pSystem.doDebug(0, "Connecting to ["+sHost+"]", this);
						try
						{
							//m_Parent.waitForConnection();
							m_Parent.setConnectInProgress(true);

							CLUSTERThread cThread = m_Parent.connect(sHost, iPort, false);
							//try{Thread.sleep(1000);}catch(Exception r){}
							if(cThread!=null && cThread.isOK())
								m_pSystem.doInformation("Connected to %s on port %s", new String[]{(String)al.get(i), String.valueOf(iCLUSTERPort)}, this);

						}
						catch(Exception e)
						{
							m_pSystem.doDebug(pmaLog.DEBUGLEVEL_STANDARD, "ERROR connecting to %s on port %s: " + e.toString(), new String[]{(String)al.get(i), String.valueOf(iCLUSTERPort)}, this);
						}
						m_Parent.setConnectInProgress(false);
					}
					else
					{
						m_Parent.cleanClusterMates();
					}
				}
			}

			try{
				int iReconnectIn=(int)((double)iMaxReconnect*Math.random());
				//System.out.println("reconnect in="+iReconnectIn);
				Thread.sleep(iReconnectIn);
			} catch(InterruptedException ie) {}
		}

	}


	public void requestQuit()
	{
		m_bLooping=false;
		this.interrupt();
	}


	public String getErrorSource()
	{
		return "CLUSTERReconnectThread";
	}

	public String getErrorUser()
	{
		return pmaSystem.SYSTEM_ACCOUNT;
	}
}



