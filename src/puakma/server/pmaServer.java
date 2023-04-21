/** ***************************************************************
pmaServer.java
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
package puakma.server;

import java.io.FileOutputStream;
import java.util.Hashtable;
import java.util.StringTokenizer;
import java.util.Vector;

import puakma.addin.pmaAddIn;
import puakma.addin.http.TornadoServer;
import puakma.addin.http.TornadoServerInstance;
import puakma.error.ErrorDetect;
import puakma.error.ErrorPrintStream;
import puakma.error.pmaLog;
import puakma.system.SystemContext;
import puakma.system.pmaSessionCleaner;
import puakma.system.pmaSystem;
import puakma.system.pmaTempFileCleaner;

/**
 * This is the main Application server start point
 * All classes are instantiated from here
 */
public class pmaServer implements ErrorDetect,Runnable
{
	private pmaSystem m_pSystem;
	private pmaServerConsole m_pConsole;
	private boolean m_bStartConsole=true;
	private boolean m_bServerRunning=false;

	//private HTTPServer pHTTPServer;
	private Vector vAddIns = new Vector();
	private Hashtable m_htAddInAliases = new Hashtable();


	/** Ignore, just a stub for the ClassLoader
	 * pmaServer is started from here
	 */
	public void run()
	{
		m_bServerRunning=true;
		//add a shutdown hook for when the JVM kills us  
		Thread t = new pmaShutdownThread(this);
		try
		{
			Runtime.getRuntime().addShutdownHook(t);
		}
		catch(Exception e){}

		/*try
    {
        System.setSecurityManager(new PuakmaSecurityManager());
    }
    catch(Exception e){}
		 */

		m_htAddInAliases.put("http", "puakma.addin.http.HTTP");
		m_htAddInAliases.put("agenda", "puakma.addin.agenda.AGENDA");
		m_htAddInAliases.put("booster", "puakma.addin.booster.BOOSTER");
		m_htAddInAliases.put("mailer", "puakma.addin.mail.MAILER");
		m_htAddInAliases.put("cluster", "puakma.addin.cluster.CLUSTER");
		m_htAddInAliases.put("guiconsole", "puakma.addin.console.GUICONSOLE");
		m_htAddInAliases.put("hsqldb", "puakma.addin.db.HSQLDB");
		m_htAddInAliases.put("setup", "puakma.addin.db.SETUP");
		m_htAddInAliases.put("widgie", "puakma.addin.widgie.WIDGIE");
		m_htAddInAliases.put("stats", "puakma.addin.stats.STATS");

		//System.out.println(this.getClass().toString() + " ClassLoader=" + this.getClass().getClassLoader().toString());
		String szConfigFile = System.getProperty("PuakmaConfigFile");

		m_pSystem = new pmaSystem(szConfigFile, this); //can be only one system!
		String szTemp = m_pSystem.getConfigProperty("NoConsole");
		if(szTemp!=null && szTemp.equals("1")) m_bStartConsole=false;
		//m_pSystem.displayLicense();

		m_pSystem.pErr.doInformation("pmaServer.Startup", new String[]{m_pSystem.getVersionString()}, this);
		//if(!bStartConsole) m_pSystem.pErr.doInformation("pmaServer.AllowConsole", this);
		runServer();
		m_pSystem.pErr.doInformation("pmaServer.Shutdown", new String[]{m_pSystem.getSystemUptime()}, this);
		m_bServerRunning=false;
		//so we don't register multiple shutdown hooks
		pmaShutdownThread st = (pmaShutdownThread)t;
		try
		{
			if(!st.hasStarted()) Runtime.getRuntime().removeShutdownHook(t);
		}
		catch(Exception r){}
		TornadoServerInstance tsi = TornadoServer.getInstance();
		if(tsi!=null) tsi.shutdown();
	}

	/*
	 * This runs the app server
	 * Once here we can assume a system database connection
	 */
	private void runServer()
	{
		//m_pSystem.pErr.doDebug(pmaLog.DEBUGLEVEL_FULL, "runServer()", this);
		//long lticks=0;
		try
		{
			String sErrLog = m_pSystem.getConfigProperty("SystemErrLog");
			if(sErrLog!=null && sErrLog.length()>0)
			{
				System.setErr(new ErrorPrintStream(new FileOutputStream(sErrLog, true)));
				System.err.println( "---------------- SERVER START ---------------- ");// + new java.util.Date() );
			}
		}catch(Exception e){}

		m_pConsole = new pmaServerConsole(this, m_pSystem);
		if(m_bStartConsole) m_pConsole.start();
		//new stream reader now allows console to always start
		//m_pConsole.start();
		//still some issues if started from a terminal then terminal is quit

		loadAddIns();
		pmaSessionCleaner sesssionCleaner = new pmaSessionCleaner(this, m_pSystem);
		pmaTempFileCleaner tempFileCleaner = new pmaTempFileCleaner(this, m_pSystem);
		sesssionCleaner.start();
		tempFileCleaner.start();

		while(m_pSystem.isSystemRunning())
		{
			try{Thread.sleep(10000);}catch(Exception exInt){ System.err.println(exInt.toString()); m_pSystem.stopSystem(); }
			cleanAddInList();
		}
		if(sesssionCleaner.isAlive()) sesssionCleaner.interrupt();
		if(tempFileCleaner.isAlive()) tempFileCleaner.interrupt();
		
		unloadAddIn("", true);
		waitForAddInsToQuit();
		System.err.println( "---------------- SERVER SHUTDOWN ---------------- ");
		//if(m_pConsole.isAlive()) m_pConsole.closeIn();
		if(m_pConsole.isAlive()) m_pConsole.interrupt();
	}

	/**
	 * Method to stop the server running
	 */
	public void shutdown()
	{
		if(m_pSystem!=null)
		{
			if(m_pSystem.pErr!=null) m_pSystem.pErr.doInformation("JVM initiated shutdown....", this);
			m_pSystem.stopSystem();
		}
	}

	/**
	 * returns true when the server is running
	 */
	public boolean isRunning()
	{
		return m_bServerRunning;
	}


	/*
	 * Waits for all Addins to quit
	 */
	public void waitForAddInsToQuit()
	{
		pmaAddIn pAddIn;
		int i;
		boolean bContinue=true;
		while(bContinue)
		{
			cleanAddInList();
			bContinue = false;
			for(i=0; i<vAddIns.size(); i++)
			{
				pAddIn = (pmaAddIn)vAddIns.elementAt(i);
				if(/*pAddIn.addInIsRunning()*/pAddIn.isAlive())
				{
					bContinue = true;
					try{Thread.sleep(100);}catch(Exception r){}
					break;
				}
			}
		}//while
	}


	/*
	 * Removes dead/finished addins from the list of runners
	 */
	public void cleanAddInList()
	{
		pmaAddIn pAddIn;
		int i;
		boolean bContinue=true;
		while(bContinue)
		{
			bContinue = false;
			for(i=0; i<vAddIns.size(); i++)
			{
				pAddIn = (pmaAddIn)vAddIns.elementAt(i);
				if(/*!pAddIn.addInIsRunning()*/ !pAddIn.isAlive())
				{
					//System.out.println("removing: " + pAddIn.getClass().getName());
					vAddIns.remove(i);
					bContinue=true;
					break;
				}
			}
		}//while
	}



	/**
	 * Load the addins specified in the config file
	 */
	private void loadAddIns()
	{
		//if(m_pSystem.pErr!=null) m_pSystem.pErr.doDebug(pmaLog.DEBUGLEVEL_FULL, "loadAddIns()", this);
		String szAddInList=m_pSystem.getConfigProperty("AddIns");
		String szAddInName;
		if(szAddInList != null)
		{
			StringTokenizer stk= new StringTokenizer(szAddInList, ",", false);
			while (stk.hasMoreTokens())
			{
				if(!m_pSystem.isSystemRunning()) return;
				szAddInName = stk.nextToken();
				loadAddIn(szAddInName);
			}//end while
		}//end if
	}


	/**
	 * Determines if the given addin is loaded on this system
	 * @param sName
	 * @return
	 */
	public boolean isAddInLoaded(String sName)
	{
		pmaAddIn pAddIn;
		if(sName==null) return false;    
		for(int i=0; i<vAddIns.size(); i++)
		{
			pAddIn = (pmaAddIn)vAddIns.elementAt(i);
			if((pAddIn.getAddInName().equalsIgnoreCase(sName) || pAddIn.getClass().getName().equalsIgnoreCase(sName)) && pAddIn.addInIsRunning())
			{
				return true;
			}
		}
		return false;
	}

	/**
	 * if a part addin name is passed eg http it should return the fully qualified name puakma.addin.http.HTTP
	 */
	private String unAliasName(String sName)
	{
		if(sName==null || sName.length()==0) return sName;
		if(m_htAddInAliases.containsKey(sName.toLowerCase())) return (String)m_htAddInAliases.get(sName.toLowerCase());

		return sName;
	}

	/**
	 * Get String array of class names of the currently loaded addins
	 * @return
	 */
	public String[] getLoadedAddInNames()
	{
		cleanAddInList();
		String sNames[] = new String[vAddIns.size()];
		pmaAddIn pAddIn;
		for(int i=0; i<vAddIns.size(); i++)
		{
			pAddIn = (pmaAddIn)vAddIns.elementAt(i);
			sNames[i] = pAddIn.getClass().getName();
		}
		return sNames;
	}

	/*
	 * Unloads an Addin
	 */
	public void unloadAddIn(String szAddInName, boolean bUnloadAll)
	{
		//if(m_pSystem.pErr!=null) m_pSystem.pErr.doDebug(pmaLog.DEBUGLEVEL_FULL, "unloadAddIn()", this);
		pmaAddIn pAddIn;
		boolean bFound = false;
		int i;
		if(szAddInName==null) return;
		//System.out.println("addins="+vAddIns.size());
		for(i=0; i<vAddIns.size(); i++)
		{
			pAddIn = (pmaAddIn)vAddIns.elementAt(i);
			//System.out.println(i + "found: "+pAddIn.getClass().getName());
			if(pAddIn.getClass().getName().equals(szAddInName) || pAddIn.getAddInName().equalsIgnoreCase(szAddInName) || bUnloadAll)
			{
				//System.out.println("unloading: "+pAddIn.getClass().getName());
				pAddIn.requestQuit();
				bFound = true;
				//check i so that we dont get an outofbounds exception
				if(!bUnloadAll && i<vAddIns.size()) vAddIns.removeElementAt(i);
			}
		}
		if(!bUnloadAll && !bFound)
			m_pSystem.pErr.doError("pmaServer.AddInNotFound", new String[]{szAddInName}, this);
	}


	/*
	 * gets a handle to a loaded Addin. returns null if not loaded
	 */
	public pmaAddIn getAddIn(String szAddInName)
	{    
		pmaAddIn pAddIn=null;    
		int i;
		if(szAddInName==null) return null;
		for(i=0; i<vAddIns.size(); i++)
		{
			pAddIn = (pmaAddIn)vAddIns.elementAt(i);
			if(pAddIn.getClass().getName().equals(szAddInName) || pAddIn.getAddInName().equalsIgnoreCase(szAddInName))
			{                
				return pAddIn;
			}
		}

		return null;
	}


	/**
	 * Load a single addin. Will be useful later when we want to dynamically
	 * load/unload addins
	 */
	public void loadAddIn(String sAddInName)
	{
		//if(m_pSystem.pErr!=null) m_pSystem.pErr.doDebug(pmaLog.DEBUGLEVEL_FULL, "loadAddIn()", this);
		pmaAddIn pAddIn;
		SystemContext sysCtx = new SystemContext(m_pSystem);    

		if(sAddInName==null) return;
		sAddInName = unAliasName(sAddInName);
		try
		{      
			pAddIn = getAddIn(sAddInName);
			if(pAddIn==null || (pAddIn!=null && pAddIn.canLoadMultiple()))
			{      
				pAddIn = (pmaAddIn)Class.forName(sAddInName).newInstance();
				pAddIn.init(this, sysCtx);
				vAddIns.addElement(pAddIn);
				pAddIn.start();
				waitForAddInReady(pAddIn);
				//System.out.println("loaded: "+pAddIn.getClass().getName());
			}
			else
			{
				if(pAddIn!=null && !pAddIn.canLoadMultiple()) m_pSystem.pErr.doError("pmaServer.AddInLoadMultiple", new String[]{sAddInName}, this);
			}
		}
		catch(Exception e)
		{
			m_pSystem.pErr.doError("pmaServer.AddInError", new String[]{sAddInName, e.toString()}, this);
		}
	}

	/**
	 * Called when loading an addin, wait until it says it is loaded before returning.
	 * This will ensure that when addins are loaded at startup, prior addins have loaded successfully
	 * before loading more. eg. An addin that loads the system database must be loaded and ready before
	 * other addins load.
	 *
	 */
	private void waitForAddInReady(pmaAddIn pAddIn)
	{
		if(pAddIn==null) return;
		int iCount=0;

		while(m_pSystem.isSystemRunning() && !pAddIn.addInReady())
		{
			//NOTE: This may result in an infintite loop!! 
			//We will rely on addin developer to ensure the addin takes care of itself
			if(iCount>20) m_pSystem.pErr.doInformation("pmaServer.AddInNotReady", new String[]{pAddIn.getAddInName()}, this);
			try{ Thread.sleep(1000); }catch(Exception t){}
			iCount++;
		}
	}


	public String showAddInStatus()
	{
		StringBuilder sbReturn = new StringBuilder(512);
		pmaAddIn pAddIn;
		
		for(int i=0; i<vAddIns.size(); i++)
		{
			pAddIn = (pmaAddIn)vAddIns.elementAt(i);
			sbReturn.append(pAddIn.getStatus());
		}

		return sbReturn.toString();
	}

	/**
	 * Allow other tasks to execute console commands
	 * @param szCommand
	 * @return a string containing the results of the command. CRLF breaks each line
	 */
	public String doConsoleCommand(String szCommand)
	{
		return m_pConsole.executeCommand(szCommand, false);
	}

	/**
	 * stops and starts an addin
	 */
	public String reloadAddIn(String szAddInName)
	{
		int iCount =0;
		String sFullClassName = getFullAddInName(szAddInName);
		unloadAddIn(sFullClassName, false);
		while(isAddInLoaded(szAddInName) && iCount<35) //wait 35 seconds to do the unload
		{
			try{ Thread.sleep(1000); }catch(Exception g){}
			iCount++;
		}
		if(isAddInLoaded(szAddInName))
		{          
			String szMessage = m_pSystem.getSystemMessageString("pmaServer.AddInReloadFail");
			String sErr = pmaLog.parseMessage(szMessage, new String[]{sFullClassName});
			m_pSystem.pErr.doError(sErr, this);
			return sErr;
		}

		loadAddIn(sFullClassName);

		return "-> OK.";
	}

	/**
	 * get the full name of the addin when passed an alias. Updated to allow a full name 
	 * to also be passed.
	 */
	public String getFullAddInName(String szAddInName)
	{                   
		for(int i=0; i<vAddIns.size(); i++)
		{
			pmaAddIn pAddIn = (pmaAddIn)vAddIns.elementAt(i);            
			if(pAddIn.getAddInName().equalsIgnoreCase(szAddInName) || pAddIn.getClass().getName().equalsIgnoreCase(szAddInName))
			{
				return pAddIn.getClass().getName();
			}
		}
		return null;
	}

	/**
	 * tell a particular addin something
	 * @param sAddInName
	 * @param sCommand
	 * @return
	 */
	public String tellAddIn(String sAddInName, String sCommand)
	{
		StringBuilder sbReturn = new StringBuilder(512);
		pmaAddIn pAddIn;
		//boolean bFound = false;
		int i;
		if(sAddInName==null) return doConsoleCommand(sCommand);
		for(i=0; i<vAddIns.size(); i++)
		{
			pAddIn = (pmaAddIn)vAddIns.elementAt(i);      
			//System.out.println(pAddIn.getClass().getName() + " = ["+pAddIn.getAddInName()+"]");
			//if(pAddIn.getAddInName().equalsIgnoreCase(szAddInName))
			if(pAddIn.getClass().getName().equals(sAddInName) || pAddIn.getAddInName().equalsIgnoreCase(sAddInName))
			{
				sbReturn.append(pAddIn.tell(sCommand));
			}
		}
		return sbReturn.toString();
	}

	/**
	 * tell all addins something
	 * @param sCommand
	 * @return
	 */
	public String tellAllAddIns(String sCommand)
	{
		StringBuilder sbReturn = new StringBuilder(512);
		pmaAddIn pAddIn;
		int i;
		for(i=0; i<vAddIns.size(); i++)
		{
			pAddIn = (pmaAddIn)vAddIns.elementAt(i);
			sbReturn.append(pAddIn.tell(sCommand));
		}
		return sbReturn.toString();
	}


	/**
	 * Send the message to the specified addin task. Returns null if the addin
	 * cannot be found
	 * @param sAddInName
	 * @param oMessage
	 * @return
	 */
	public AddInMessage sendMessage(String sAddInName, AddInMessage oMessage)
	{
		pmaAddIn pAddIn;
		//boolean bFound = false;
		int i;
		AddInMessage returnMessage=null;

		if(sAddInName==null) return null;

		//TODO: send to another machine
		if(oMessage.DestinationHost!=null)
		{
			m_pSystem.pErr.doInformation("pmaServer.AddInRemoteMessage", new String[]{sAddInName, oMessage.DestinationHost}, this);
			return returnMessage;
		}

		for(i=0; i<vAddIns.size(); i++)
		{
			pAddIn = (pmaAddIn)vAddIns.elementAt(i);
			if(pAddIn.getClass().getName().equals(sAddInName) || pAddIn.getAddInName().equalsIgnoreCase(sAddInName))
			{
				return pAddIn.sendMessage(oMessage);
			}
		}
		m_pSystem.pErr.doError("pmaServer.AddInNotFound", new String[]{sAddInName}, this);
		return returnMessage;
	}



	public String getErrorSource()
	{
		return "pmaServer";
	}

	public String getErrorUser()
	{
		return pmaSystem.SYSTEM_ACCOUNT;
	}
}