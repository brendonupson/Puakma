/** ***************************************************************
pmaServerConsole.java
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

import java.io.InputStream;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Properties;
import java.util.Vector;

import puakma.error.ErrorDetect;
import puakma.error.pmaLog;
import puakma.system.SessionContext;
import puakma.system.UserRoles;
import puakma.system.X500Name;
import puakma.system.pmaSession;
import puakma.system.pmaSystem;
import puakma.util.Util;
/**
 * Class to handle the console user i/o. The administrator can issue console commands
 * through this thread
 */
public class pmaServerConsole extends Thread implements ErrorDetect
{
	private final static String CRLF="\r\n";
	private final static String COMMAND_SUCCESS = "The command completed successfully";
	//private final static String COMMAND_DENIED = "You may not perform that command from this interface";
	private SimpleDateFormat m_sdf = new SimpleDateFormat( "d/M/yy HH:mm:ss" );
	private NumberFormat m_nfThousands;
	private NumberFormat m_nf0DP;

	//private BufferedReader m_is;
	//private InputStreamReader m_is;
	private pmaServer m_pParent;
	private pmaSystem m_pSystem;
	//private boolean m_bEchoCommand=false;
	private static String DASH_LINE = "------------------------------------------------\r\n"; 
	private static String GPL_INFO = DASH_LINE+"Puakma Core Server Technology, Copyright (C) 2001 Brendon Upson\r\n"+
	"Website: http://www.puakma.net - Email: info@puakma.net\r\n"+
	"Puakma Core Server Technology comes with ABSOLUTELY NO WARRANTY unless explicitly specified;\r\n"+
	"This is free software, and you are welcome to redistribute it "+
	"under certain conditions; type 'gpl full' for details.\r\n" + DASH_LINE;


	public static String VERB_HELP = "help";
	public static String VERB_HELPSHORT = "?";
	public static String VERB_QUIT = "quit";
	public static String VERB_LOAD = "load";
	public static String VERB_UNLOAD = "unload";
	public static String VERB_RELOAD = "reload";
	public static String VERB_STATUS = "status";
	public static String VERB_SESSIONS = "sessions";
	public static String VERB_SESSION = "session";
	public static String VERB_DROP = "drop";
	public static String VERB_TELL = "tell";
	public static String VERB_SHOW = "show";
	//public static String VERB_SET = "set";
	public static String VERB_CLEAR = "clear";
	public static String VERB_CONFIG = "config";
	public static String VERB_GARBAGECOLLECT = "gc";
	public static String VERB_RESTART = "restart";
	public static String VERB_GPL = "gpl";
	public static String VERB_STORE = "store";
	public static String VERB_STATS = "stats";

	public pmaServerConsole(pmaServer paramParent, pmaSystem paramSystem)
	{

		m_pParent = paramParent;
		m_pSystem = paramSystem;
		/*try
    {      
      m_is = new BufferedReader(new InputStreamReader(System.in));//, "ISO-8859-1"));
        //m_is = new BufferedReader(new InputStreamReader(System.in));
    }
    catch(Exception e){m_is=null; }
		 */
		m_nfThousands = NumberFormat.getInstance();
		m_nfThousands.setMaximumFractionDigits(1);
		m_nfThousands.setMinimumFractionDigits(1);

		m_nf0DP = NumberFormat.getInstance();
		m_nf0DP.setMaximumFractionDigits(0);
		m_nf0DP.setMinimumFractionDigits(0);
	}


	/**
	 *
	 *
	 */
	public void run()
	{

		//Thread.dumpStack()
		m_pSystem.pErr.doInformation("pmaServer.ConsoleStartOK", m_pParent);
		doGPL("", true);

		//m_bEchoCommand = shouldEchoCommands();
		//System.out.println("------->> echo="+m_bEchoCommand);
		//boolean bReadInput=true;
		while(m_pSystem.isSystemRunning())
		{
			try
			{
				String szLine = readLine();
				//  if(m_is.ready())
				//  {
				//    String szLine = m_is.readLine(); //i/o blocks here            
				executeCommand(szLine, true);            
				//  }
				//  else
				//      Thread.sleep(500);
			}
			catch(Exception e) {}
		}
		m_pSystem.pErr.doInformation("pmaServer.ConsoleStop", m_pParent);
	}

	/*public void closeIn()
  {
      try
      {
          Thread.currentThread().interrupt();
          //System.in.close();
          //if(m_is!=null) m_is.close();
      }
      catch(Exception e)
      {
          System.out.println(e.toString());
      }
  }
	 */

	/**
	 *
	 */
	private String readLine()
	{
		StringBuilder sbReturn=new StringBuilder(100);
		boolean bLooping=true;

		try
		{          
			while(bLooping)
			{
				if(System.in.available()>0) //.available()>0)
				{
					char cKey = (char)System.in.read();
					if(cKey=='\r' || cKey=='\n') 
						bLooping = false;
					else
					{                                                                
						sbReturn.append(cKey);
					}  

				}
				else
					Thread.sleep(1000);
				if(!m_pSystem.isSystemRunning()) bLooping = false;
			}
		}
		catch(Exception e){ return null; }
		if(sbReturn.length()==0) return null;
		return sbReturn.toString();
	}

	/**
	 * Executes the command entered by the user
	 */
	public String executeCommand(String szParamCommand, boolean bPrintToScreen)
	{
		String szVerb="";
		String sCommand = szParamCommand;
		if(sCommand==null) return "";
		sCommand = Util.trimSpaces(sCommand);
		if(sCommand.length()==0) return "";
		m_pSystem.pErr.doInformation("pmaServer.ConsoleCommand", new String[]{sCommand}, this);
		int iPos = sCommand.indexOf(' ');
		if(iPos>=0)
		{
			szVerb = sCommand.substring(0, iPos).toLowerCase();
			sCommand = sCommand.substring(iPos+1, sCommand.length());
		}
		else
		{
			szVerb = sCommand.toLowerCase();
			sCommand = "";
		}
		sCommand = Util.trimSpaces(sCommand);
		//System.out.println("verb=" + szVerb);
		//System.out.println("command=" + szCommand);

		if(szVerb.equals(VERB_HELP) || szVerb.equals(VERB_HELPSHORT)) return doHelp(sCommand, bPrintToScreen);
		if(szVerb.equals(VERB_QUIT)) return doQuit(sCommand, bPrintToScreen);
		if(szVerb.equals(VERB_UNLOAD)) return doUnload(sCommand, bPrintToScreen);
		if(szVerb.equals(VERB_LOAD)) return doLoad(sCommand, bPrintToScreen);
		if(szVerb.equals(VERB_RELOAD)) return doReload(sCommand, bPrintToScreen);
		if(szVerb.equals(VERB_STATUS)) return doStatus(sCommand, bPrintToScreen);
		if(szVerb.equals(VERB_SESSIONS)) return doSessions(sCommand, bPrintToScreen);
		if(szVerb.equals(VERB_SESSION)) return doSessionState(sCommand, bPrintToScreen);
		if(szVerb.equals(VERB_DROP)) return doDrop(sCommand, bPrintToScreen);
		if(szVerb.equals(VERB_TELL)) return doTell(sCommand, bPrintToScreen);
		if(szVerb.equals(VERB_SHOW)) return doShow(sCommand, bPrintToScreen);
		if(szVerb.equals(VERB_CLEAR)) return doClear(sCommand, bPrintToScreen);
		if(szVerb.equals(VERB_CONFIG)) return doConfig(sCommand, bPrintToScreen);
		if(szVerb.equals(VERB_GARBAGECOLLECT)) return doGC(sCommand, bPrintToScreen);
		if(szVerb.equals(VERB_RESTART)) return doRestart(sCommand, bPrintToScreen);
		if(szVerb.equals(VERB_GPL)) return doGPL(sCommand, bPrintToScreen);
		if(szVerb.equals(VERB_STORE)) return doStore(sCommand, bPrintToScreen);
		if(szVerb.equals(VERB_STATS)) return doStats(sCommand, bPrintToScreen);

		//if(szVerb.equals(VERB_SET)) return doSet(szCommand, bPrintToScreen);

		m_pSystem.pErr.doError("pmaServer.ConsoleUnknownCommand", new String[]{szParamCommand}, this);
		String szInfo = m_pSystem.getSystemMessageString("pmaServer.ConsoleUnknownCommand");
		return pmaLog.parseMessage(szInfo, new String[]{szParamCommand});
	}

	/**
	 * show help on the available commands
	 */
	private String doHelp(String szCommand, boolean bPrintToScreen)
	{
		String szReturn =
			"status                | Display the server status"+ CRLF +
			"sessions              | Display the user sessions"+ CRLF +
			"session [username]    | Display the session details"+ CRLF +
			"quit [save]           | Shut down the server, opt. save sessions"+ CRLF +
			"gc                    | Ask the JVM to collect garbage"+ CRLF +
			"restart server [save] | Restart the server, reloading puakma.jar, opt. save sessions"+ CRLF +
			"load [class]          | Load the named class ie: 'puakma.addin.http.HTTP' "+ CRLF +
			"unload [task]         | Unload the named task ie: HTTP"+ CRLF +
			"reload [task]         | Unload then load the named task ie: HTTP"+ CRLF +
			"drop [who]            | Drop user session. Use 'all' for all sessions"+ CRLF +
			"tell TASK ...         | Tells a task to perform some action" + CRLF +
			"clear ITEM            | eg: log, errors" + CRLF +
			"config WHAT           | Use RELOAD to refresh the server settings" + CRLF +
			"show VARNAME          | Shows a puakma.config variable, or java for System props" + CRLF +
			"stats                 | Display statistics from each server addin"+ CRLF +
			"store WHAT            | Access the global cache; flush, status";// + CRLF +
		//"set VARNAME=value  | Sets a puakma.config variable";

		if(bPrintToScreen) System.out.println(szReturn);
		return szReturn;
	}

	/**
	 * 
	 * @param sCommand
	 * @param bPrintToScreen
	 * @return
	 */
	private String doStore(String sCommand, boolean bPrintToScreen)
	{
		String szReturn="";
		Hashtable cacheObject =  m_pSystem.getAllGlobalObjects();
		if(cacheObject==null) szReturn = "-> GlobalStore is null" + CRLF;

		if(cacheObject!=null && sCommand.toLowerCase().equals("status"))
		{
			szReturn = "-> GlobalStore " + m_nf0DP.format(cacheObject.size()) + " objects:"+ CRLF;
			Enumeration en = cacheObject.keys();
			while(en.hasMoreElements())
			{
				String sKey = (String)en.nextElement();
				szReturn += sKey + CRLF;
			}

		}

		if(cacheObject!=null && sCommand.toLowerCase().equals("flush"))
		{
			m_pSystem.removeGlobalObject(null);
			szReturn = "-> GlobalStore: flushed." + CRLF;
		}

		if(bPrintToScreen) System.out.println(szReturn);
		return szReturn;
	}

	/**
	 * clear something ie the LOG
	 */
	private String doClear(String szCommand, boolean bPrintToScreen)
	{
		String szReturn="";

		if(szCommand.toLowerCase().equals("log"))
		{
			m_pSystem.pErr.clearServerLog();
			szReturn = "->Log cleared" + CRLF;
		}

		if(szCommand.toLowerCase().equals("errors"))
		{
			m_pSystem.clearErrorCount();
			szReturn = "->Error count cleared" + CRLF;
		}

		if(szCommand.toLowerCase().startsWith("roles"))
		{
			int iPos = szCommand.indexOf(' ');
			int iCleared = 0;
			String sName = szCommand.substring(iPos+1);
			if(sName.equals("*"))
			{
				Enumeration en = m_pSystem.getSessionList();
				while(en.hasMoreElements())
				{
					SessionContext sess = new SessionContext((pmaSession)en.nextElement());
					szReturn += "  Clearing roles for " + sess.getUserNameAbbreviated() + " " + sess.getSessionID() +  CRLF;
					sess.removeAllUserRolesObjects();
					iCleared++;
				}
			}
			else //specific user
			{
				ArrayList arr = m_pSystem.getSessionsByUserName(sName);
				for(int i=0; i<arr.size(); i++)
				{
					SessionContext sess = (SessionContext)arr.get(i);
					szReturn += "  Clearing roles for " + sess.getUserNameAbbreviated() + " " + sess.getSessionID() +  CRLF;
					sess.removeAllUserRolesObjects();
					iCleared++;
				}
			}
			if(iCleared>0)
				szReturn += "->Roles cleared from " + iCleared + " sessions" + CRLF;
			else
				szReturn = "->No matching sessions" + CRLF;
		}

		if(szCommand.toLowerCase().startsWith("objects"))
		{
			int iPos = szCommand.indexOf(' ');
			String sName = szCommand.substring(iPos+1);
			ArrayList arr = m_pSystem.getSessionsByUserName(sName);
			for(int i=0; i<arr.size(); i++)
			{
				SessionContext sess = (SessionContext)arr.get(i);
				szReturn += "  Clearing session objects for " + sess.getUserNameAbbreviated() + " " + sess.getSessionID() +  CRLF;
				sess.removeSessionObject(null);
			}
			if(arr.size()>0)
				szReturn += "->Session objects cleared" + CRLF;
			else
				szReturn = "->No matching sessions" + CRLF;
		}


		if(bPrintToScreen) System.out.println(szReturn);
		return szReturn;
	}

	/**
	 * clear something ie the LOG
	 */
	private String doGC(String szCommand, boolean bPrintToScreen)
	{
		String szReturn="->Garbage Collection Requested" + CRLF;
		System.runFinalization();
		System.gc();

		if(bPrintToScreen) System.out.println(szReturn);
		return szReturn;
	}


	/**
	 * show a system property
	 */
	private String doConfig(String szCommand, boolean bPrintToScreen)
	{
		String szReturn="";

		if(szCommand.toLowerCase().equals("reload"))
		{
			m_pSystem.reloadConfig();
			szReturn += m_pParent.tellAllAddIns("config reload");
			szReturn += "->Config reloaded" + CRLF;
		}

		if(bPrintToScreen) System.out.println(szReturn);
		return szReturn;
	}

	private static ArrayList<String> visitThreadGroup(ThreadGroup group, int level) 
	{
		ArrayList<String> arrThreadData = new ArrayList<String>();

		arrThreadData.add("---- THREADGROUP: " + group.getName() + " (" + group.activeCount() +") ----");
		// Get threads in `group'
		int numThreads = group.activeCount();
		Thread[] threads = new Thread[numThreads*2];
		numThreads = group.enumerate(threads, false);

		// Enumerate each thread in `group'
		for (int i=0; i<numThreads; i++) 
		{
			// Get thread
			Thread thread = threads[i];
			StackTraceElement ste[] = thread.getStackTrace();
			String sST = "";
			if(ste!=null && ste.length>0) 
			{
				sST = " "+ste[0].getMethodName() +"(";
				if(ste[0].getLineNumber()>=0) sST += "#"+ste[0].getLineNumber();
				sST += ")";				
			}
			arrThreadData.add(thread.getName() + " id:"+thread.getId() + " pri:"+thread.getPriority() + " state:"+thread.getState().name()+" " + thread.getClass().getCanonicalName() + sST);

		}

		// Get thread subgroups of `group'
		int numGroups = group.activeGroupCount();
		ThreadGroup[] groups = new ThreadGroup[numGroups*2];
		numGroups = group.enumerate(groups, false);

		// Recursively visit each subgroup
		for (int i=0; i<numGroups; i++) 
		{
			arrThreadData.addAll(visitThreadGroup(groups[i], level+1));
		}

		return arrThreadData;
	}

	/**
	 * show a system property
	 */
	private String doShow(String sCommand, boolean bPrintToScreen)
	{
		ArrayList<String> arr = new ArrayList<String>();
		boolean bHandled = false;
		boolean bSort = true;

		if(sCommand!=null && sCommand.equalsIgnoreCase("java"))
		{
			Properties props = System.getProperties();
			Enumeration en = props.propertyNames();
			while(en.hasMoreElements())
			{
				String szName = (String)en.nextElement();
				arr.add(szName + '=' + props.getProperty(szName));
			}
			bHandled = true;
		}

		if(!bHandled && sCommand!=null && sCommand.equalsIgnoreCase("threads"))
		{
			ThreadGroup root = Thread.currentThread().getThreadGroup().getParent();
			while (root.getParent() != null) {
				root = root.getParent();
			}

			// Visit each thread group
			arr = visitThreadGroup(root, 0);

			bHandled = true;
			bSort = false;
		}

		if(!bHandled)
		{
			if(sCommand==null || sCommand.length()==0) //puakma property
			{
				Enumeration en = m_pSystem.getConfigPropertyNames();
				while(en.hasMoreElements())
				{
					String szName = (String)en.nextElement();
					arr.add(szName + '=' + m_pSystem.getConfigProperty(szName));
				}

			}
			else
			{
				String szValue = m_pSystem.getConfigProperty(sCommand);
				if(szValue==null) szValue="[not_set]";
				arr.add(sCommand + '=' + szValue);
			}
			bHandled = true;
		}


		if(bSort) Collections.sort(arr);
		StringBuilder sbReturn=new StringBuilder(512);
		for(int i=0; i<arr.size(); i++)
		{
			sbReturn.append((String)arr.get(i) + CRLF);
		}

		if(bPrintToScreen) System.out.println(sbReturn.toString());
		return sbReturn.toString();
	}




	/**
	 * show help on the available commands
	 */
	private String doQuit(String szCommand, boolean bPrintToScreen)
	{
		//System.out.println("["+szCommand+"]");
		if(szCommand.toLowerCase().equals("save"))
		{
			m_pSystem.setSaveSessionsOnShutdown(true);              
		}


		m_pSystem.stopSystem();

		return COMMAND_SUCCESS;
	}


	/**
	 * show help on the available commands
	 */
	private String doRestart(String szCommand, boolean bPrintToScreen)
	{
		String szRestartMessage="Unknown restart option: [" + szCommand + "]";
		if(szCommand.toLowerCase().equals("server"))
		{
			szRestartMessage="The system is restarting, please wait...";
			m_pSystem.restartSystem();
		}
		if(szCommand.toLowerCase().equals("server save"))
		{
			szRestartMessage="The system is saving sessions and restarting, please wait...";
			m_pSystem.setSaveSessionsOnShutdown(true);
			m_pSystem.restartSystem();
		}
		if(bPrintToScreen) System.out.println(szRestartMessage);
		return szRestartMessage;
	}

	/**
	 * Display interactive GPL info
	 */
	private String doGPL(String szCommand, boolean bPrintToScreen)
	{
		StringBuilder sbGPLText=new StringBuilder(512);
		if(szCommand.equalsIgnoreCase("full")) //show full gpl
		{
			try
			{
				InputStream is = this.getClass().getClassLoader().getResourceAsStream("gpl.txt");
				byte buf[] = new byte[4096];
				while(is.available()>0)
				{                
					int iRead = is.read(buf);
					String s = new String(buf, 0, iRead, "utf-8");
					sbGPLText.append(s);
				}
			}
			catch(Exception e){e.printStackTrace();}
		}
		else
			sbGPLText.append(GPL_INFO);
		if(bPrintToScreen) System.out.println(sbGPLText.toString());
		return sbGPLText.toString();
	}

	/**
	 * unload an addin
	 */
	private String doUnload(String szCommand, boolean bPrintToScreen)
	{
		String szAddInName="";
		int iPos = szCommand.indexOf(' ');
		if(iPos>0)
			szAddInName = szCommand.substring(0, iPos);
		else
			szAddInName = szCommand;
		m_pParent.unloadAddIn(szAddInName, false);
		return COMMAND_SUCCESS;
	}

	/**
	 * reload an addin, interface stop then start
	 */
	private String doReload(String szCommand, boolean bPrintToScreen)
	{
		String szAddInName="";
		int iPos = szCommand.indexOf(' ');
		if(iPos>0)
			szAddInName = szCommand.substring(0, iPos);
		else
			szAddInName = szCommand;
		return m_pParent.reloadAddIn(szAddInName);    
	}


	/**
	 * unload an addin
	 */
	private String doLoad(String szCommand, boolean bPrintToScreen)
	{
		String szAddInClass="";
		int iPos = szCommand.indexOf(' ');
		if(iPos>0)
			szAddInClass = szCommand.substring(0, iPos);
		else
			szAddInClass = szCommand;
		m_pParent.loadAddIn(szAddInClass);
		return COMMAND_SUCCESS;
	}


	/**
	 * unload an addin
	 * @param szCommand
	 * @param bPrintToScreen
	 * @return
	 */
	private String doDrop(String szCommand, boolean bPrintToScreen)
	{
		int iDropped=0;
		String szWhat=szCommand.trim().toLowerCase();
		if(szWhat.equals("all"))
		{
			iDropped = m_pSystem.dropSessions(true);
		}
		else //drop an individual user session
		{
			if(szWhat.length()!=0) iDropped = m_pSystem.dropSession(szWhat);
		}
		System.gc(); //ask for a garbage collect to reclaim memory
		m_pSystem.pErr.doInformation("Dropped %s session(s)", new String[]{iDropped+""}, this);
		return COMMAND_SUCCESS;
	}


	/**
	 * display the server status
	 */
	private String doStatus(String sCommand, boolean bPrintToScreen)
	{
		StringBuilder sbReturn=new StringBuilder(512);

		sbReturn.append(CRLF);
		Runtime rt = Runtime.getRuntime();
		long lJVMMaxMem = rt.maxMemory();
		long lTotalMem = rt.totalMemory();
		long lFreeMem = rt.freeMemory();
		double dbl1K = (double)1024;
		String szMem = m_nfThousands.format((double)lFreeMem/dbl1K/dbl1K) + "Mb/" + m_nfThousands.format((double)lTotalMem/dbl1K/dbl1K) + "Mb " + m_nfThousands.format(((double)lFreeMem/(double)lTotalMem)*100) + '%'; 

		String szMaxSessionCount="Unlimited";
		if(m_pSystem.getMaxSessionCount()>=0) szMaxSessionCount = ""+m_pSystem.getMaxSessionCount();

		int iCPU = Runtime.getRuntime().availableProcessors();
		sbReturn.append("Server:   " + m_pSystem.getVersionString() + CRLF);
		sbReturn.append("OS:       " + System.getProperty("os.name") + ' ' + System.getProperty("os.version") + ' ' + System.getProperty("os.arch") + " (" + iCPU + " cpu) " + m_nfThousands.format((double)lJVMMaxMem/dbl1K/dbl1K) + "Mb JVM, " + m_nfThousands.format((double)(lTotalMem-lFreeMem)/dbl1K/dbl1K) + "Mb in use" + CRLF);
		sbReturn.append("JVM:      " + System.getProperty("java.version") + " - " + System.getProperty("java.vendor") + "   RTMemFree: " + szMem + CRLF);
		sbReturn.append("Time now: " + new java.util.Date() + CRLF);
		sbReturn.append("Started:  " + m_pSystem.dtStart+ CRLF);
		sbReturn.append("Uptime:   " + m_pSystem.getSystemUptime() + CRLF);
		sbReturn.append("Errors:   " + m_pSystem.getErrorCount() + CRLF);
		sbReturn.append("Sessions: " + m_pSystem.getSessionCount() + "/" +  szMaxSessionCount + CRLF);
		sbReturn.append("------------------ TASKS -----------------------" + CRLF);
		sbReturn.append(m_pParent.showAddInStatus());
		sbReturn.append("------------------------------------------------" + CRLF);

		if(bPrintToScreen) System.out.println(sbReturn.toString());
		return sbReturn.toString();
	}


	/**
	 * display the user sessions
	 */
	private String doSessions(String szCommand, boolean bPrintToScreen)
	{
		StringBuilder sbReturn = new StringBuilder(512);
		StringBuilder sbSession = new StringBuilder(512);
		ArrayList<String> arr = new ArrayList<String>();

		Enumeration en = m_pSystem.getSessionList();
		boolean bFound=false;
		int iPos;
		String szAgent="";
		sbReturn.append("----------------- SESSIONS ---------------------");
		sbReturn.append(CRLF);
		try
		{
			while(en!=null && en.hasMoreElements())
			{
				sbSession.delete(0, sbSession.length());
				pmaSession sess = (pmaSession)en.nextElement();
				if(sess!=null)
				{
					bFound=true;
					iPos = sess.userAgent.indexOf(' ');
					if(iPos>0)
						szAgent = sess.userAgent.substring(0, iPos);
					else
						szAgent = sess.userAgent;
					X500Name nmUser = new X500Name(sess.userName);
					sbSession.append(nmUser.getAbbreviatedName());
					sbSession.append(", ");
					if(sess.internetAddress!=null)
					{
						sbSession.append(sess.internetAddress.getHostAddress());
						sbSession.append(", ");
					}
					sbSession.append(szAgent);
					sbSession.append(", Last=");
					sbSession.append(m_sdf.format(sess.lastTransaction));
					sbSession.append(", Idle=");
					long lDiff = (System.currentTimeMillis()-sess.lastTransaction.getTime())/1000;
					sbSession.append(lDiff);
					sbSession.append('s');
					arr.add(sbSession.toString());
				}
			}//while
		}
		catch(Exception e)
		{
			sbReturn.append("Error building session list: " + e.toString() + CRLF);
			e.printStackTrace();
		}

		String sSessions[] = puakma.util.Util.objectArrayToStringArray(arr.toArray());
		Arrays.sort(sSessions);
		if(!bFound) sbReturn.append("None" + CRLF);
		for(int i=0; i<sSessions.length; i++) 
		{
			sbReturn.append(sSessions[i]);
			sbReturn.append(CRLF);
		}
		sbReturn.append("------------------------------------------------");
		sbReturn.append(CRLF);

		if(bPrintToScreen) System.out.println(sbReturn.toString());
		return sbReturn.toString();
	}

	/**
	 * display the user sessions
	 */
	private String doSessionState(String szCommand, boolean bPrintToScreen)
	{
		StringBuilder sbReturn = new StringBuilder(512);
		String sUserName=szCommand;

		X500Name nmSearch = new X500Name(sUserName.toLowerCase());

		Enumeration<pmaSession> en = m_pSystem.getSessionList();
		//boolean bFound=false;    

		sbReturn.append("----------------- SESSION DETAILS ---------------------");

		sbReturn.append(CRLF);
		try
		{
			while(en!=null && en.hasMoreElements())
			{
				pmaSession sess = (pmaSession)en.nextElement();
				if(sess!=null)
				{
					//bFound=true;            
					X500Name nmUserCompare = new X500Name(sess.userName.toLowerCase());
					X500Name nmUser = new X500Name(sess.userName);
					if(sUserName.equals("*") || nmUserCompare.equals(nmSearch) || nmUserCompare.getCommonName().equalsIgnoreCase(nmSearch.getCommonName()))
					{
						sbReturn.append("User: "+nmUser.getAbbreviatedName() + CRLF); 
						sbReturn.append("SessionID: "+ sess.getFullSessionID() + CRLF);
						if(sess.userTimeZone!=null) sbReturn.append("TimeZone: "+ sess.userTimeZone.getDisplayName() + CRLF);
						if(sess.userLocale!=null) sbReturn.append("Locale: "+ sess.userLocale.getDisplayName() + CRLF);
						sbReturn.append("Address: "+sess.internetAddress.getHostAddress() + CRLF);               
						sbReturn.append("User Agent: "+sess.userAgent + CRLF);        
						sbReturn.append("Login Time: "+m_sdf.format(sess.loginTime) + CRLF);
						sbReturn.append("Last Activity: "+m_sdf.format(sess.lastTransaction) + CRLF);               
						long lDiff = (System.currentTimeMillis()-sess.lastTransaction.getTime())/1000;
						sbReturn.append("Idle: "+lDiff+" seconds" + CRLF);
						sbReturn.append("Session Objects: "+ sess.getAllSessionObjects().size() + " ");
						String sObjectNames="";
						Enumeration enSO = sess.getAllSessionObjects().keys();
						while(enSO.hasMoreElements())
						{
							String sKey = (String)enSO.nextElement();
							if(sObjectNames.length()>0) sObjectNames += ", ";
							sObjectNames += sKey;                   
						}
						sbReturn.append(sObjectNames+CRLF);
						sbReturn.append("Roles:"+ CRLF);
						UserRoles ur[] = sess.getAllUserRolesObjects();
						for(int i=0; i<ur.length; i++)
						{
							String sKey = ur[i].getKey();
							sbReturn.append("   App: "+sKey + CRLF);
							Vector vRoles = ur[i].getRoles();
							String sRoles="";
							for(int k=0; k<vRoles.size(); k++)
							{
								if(sRoles.length()>0) sRoles += ", ";
								sRoles += (String)vRoles.get(k);
							}
							sbReturn.append("   --> "+sRoles + CRLF);
						}//for
						sbReturn.append("-------------------------------------------------------");
						sbReturn.append(CRLF);
					}

				}
			}//while
		}catch(Exception e){sbReturn.append("Error building session list: " + e.toString() + CRLF);}




		if(bPrintToScreen) System.out.println(sbReturn.toString());
		return sbReturn.toString();
	}

	/**
	 * 
	 * @param sCommand
	 * @param bPrintToScreen
	 * @return
	 */
	private String doStats(String sCommand, boolean bPrintToScreen)
	{
		StringBuilder sb = new StringBuilder(64);
		String sAddInNames[] = m_pParent.getLoadedAddInNames();
		if(sAddInNames!=null && sAddInNames.length>0)
		{
			for(int i=0; i<sAddInNames.length; i++)
			{
				//sb.append(sAddInNames[i] + CRLF);
				sb.append( m_pParent.tellAddIn(sAddInNames[i], "stats " + sCommand) );			
			}
		}
		else
			sb.append("No AddIns loaded");
		if(bPrintToScreen) System.out.println(sb.toString());
		return sb.toString();
	}


	/**
	 * tell an addin
	 */
	private String doTell(String szCommand, boolean bPrintToScreen)
	{
		String szAddInName="";
		String szDisplay="";
		int iPos = szCommand.indexOf(' ');
		if(iPos>0)
		{
			szAddInName = szCommand.substring(0, iPos);
			szCommand = szCommand.substring(iPos+1, szCommand.length());
		}
		else
		{
			szAddInName = szCommand;
			szCommand = "";
		}
		szDisplay += m_pParent.tellAddIn(szAddInName, szCommand);
		if(bPrintToScreen) System.out.println(szDisplay);
		return szDisplay;
	}


	public String getErrorSource()
	{
		return "pmaServerConsole";
	}

	public String getErrorUser()
	{
		return pmaSystem.SYSTEM_ACCOUNT;
	}
}