/** ***************************************************************
WIDGIE.java
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
package puakma.addin.widgie;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.zip.GZIPInputStream;

import puakma.SOAP.SOAPCallParser;
import puakma.addin.AddInStatistic;
import puakma.addin.pmaAddIn;
import puakma.addin.pmaAddInStatusLine;
import puakma.addin.http.HTTPRequestManager;
import puakma.addin.http.HTTPServer;
import puakma.addin.http.TornadoApplication;
import puakma.addin.http.TornadoServer;
import puakma.addin.http.TornadoServerInstance;
import puakma.addin.http.action.HTTPSessionContext;
import puakma.addin.http.document.DesignElement;
import puakma.error.pmaLog;
import puakma.server.AddInMessage;
import puakma.system.RequestPath;
import puakma.system.SessionContext;
import puakma.system.pmaSystem;
import puakma.util.Util;

/**
 * WIDGIE is the  business logic container
 * It is loaded as an addin
 */
public class WIDGIE extends pmaAddIn
{
	private pmaAddInStatusLine m_pStatus;
	private Hashtable<String, WidgetItem> m_htWidgets = new Hashtable<String, WidgetItem>();
	private Hashtable m_htSessions = new Hashtable();
	private final static double WIDGIE_VERSION = 1.47;
	private boolean m_bAutoReload=false;
	private final static String TYPE_XML = "text/xml";
	private final static String XML_ENCODING = "encoding=\"UTF-8\"";
	//private final static String EVAL_MESSAGE = "*** EVALUATION VERSION http://www.puakma.net ***";
	private boolean m_bDebug=false;
	private static final int DEFAULT_SB_SIZE = 2048;

	public static final String STATISTIC_KEY_WIDGETSPERHOUR = "widgie.widgetsperhour";
	private static final String STATISTIC_KEY_WIDGETLISTSPERHOUR = "widgie.widgetlistsperhour";
	private static final String STATISTIC_KEY_WSDLSPERHOUR = "widgie.wsdlsperhour";
	private static final int SESSION_OBJECT_INDEX = 0;
	private static final int SESSION_COUNTER_INDEX = 1;

	/**
	 * This method is called by the pmaServer object
	 */
	public void pmaAddInMain()
	{		
		setAddInName("WIDGIE");
		m_pStatus = createStatusLine();
		m_pStatus.setStatus("Starting...");
		m_pSystem.doInformation("WIDGIE.Startup", new String[]{""+getVersion()}, this);

		createStatistic(STATISTIC_KEY_WIDGETSPERHOUR, AddInStatistic.STAT_CAPTURE_PER_HOUR, -1, true);
		createStatistic(STATISTIC_KEY_WIDGETLISTSPERHOUR, AddInStatistic.STAT_CAPTURE_PER_HOUR, -1, true);
		createStatistic(STATISTIC_KEY_WSDLSPERHOUR, AddInStatistic.STAT_CAPTURE_PER_HOUR, -1, true);

		String szTemp = m_pSystem.getSystemProperty("WIDGIEAutoReload");
		if(szTemp!=null && szTemp.equals("1")) m_bAutoReload = true;

		szTemp =m_pSystem.getSystemProperty("WIDGIEDebug");
		if(szTemp!=null && szTemp.equals("1")) m_bDebug = true;

		// main loop
		while (!addInShouldQuit())
		{
			loadWidgets();
			try{Thread.sleep(10000);}catch(Exception e){}
			int iSize = m_htWidgets.size();
			m_pStatus.setStatus("Running, widgets: " + iSize);
		}//end while
		unloadWidgets();
		m_pStatus.setStatus("Shutting down");
		m_pSystem.doInformation("WIDGIE.Shutdown", this);
		removeStatusLine(m_pStatus);
		//m_pSystem.clearDBPoolManager();
	}

	/**
	 * Loads the widgets from the database into the Hashtable. The url is the key
	 * into the hashtable
	 */
	private void loadWidgets()
	{
		m_pSystem.doDebug(pmaLog.DEBUGLEVEL_FULL, "loadWidgets()", this);
		DesignElement design=null;
		Connection cx=null;
		ResultSet rs = null;
		Statement stmt = null;
		Hashtable htFoundList = new Hashtable();
		TornadoServerInstance tsi = TornadoServer.getInstance(m_pSystem);

		try
		{
			cx = m_pSystem.getSystemConnection();
			String szQuery = "SELECT * FROM APPLICATION,DESIGNBUCKET WHERE APPLICATION.AppID=DESIGNBUCKET.AppID AND DESIGNBUCKET.DesignType=" + DesignElement.DESIGN_TYPE_BUSINESSWIDGET;
			stmt = cx.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
			rs = stmt.executeQuery(szQuery);
			ResultSet rsParams;
			while(rs.next())
			{
				int iDesignBucketID=-1;
				design=new DesignElement();
				iDesignBucketID = rs.getInt("DesignBucketID");
				String sAppName = rs.getString("AppName");
				String sAppGroup = rs.getString("AppGroup");
				design.setApplicationName(sAppName);
				design.setApplicationGroup(sAppGroup);
				design.setDesignName(rs.getString("Name"));
				design.setDesignType(rs.getInt("DesignType"));
				design.setContentType(rs.getString("ContentType"));
				String szComment = rs.getString("Comment");
				java.util.Date dtLastUpdated = puakma.util.Util.getResultSetDateValue(rs, "Updated");
				boolean bDisabled = tsi.isApplicationDisabled(sAppGroup, sAppName); //HTTPServer.isAppDisabled(m_pSystem, sAppName, sAppGroup);

				if(!bDisabled)
				{
					szQuery = "SELECT * FROM DESIGNBUCKETPARAM WHERE DesignBucketID=" + iDesignBucketID;
					Statement stmtParams = cx.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
					rsParams = stmtParams.executeQuery(szQuery);
					while(rsParams.next())
					{
						design.addParameter(rsParams.getString("ParamName"), rsParams.getString("ParamValue"));
					}
					rsParams.close();
					stmtParams.close();
					//if options field says disabled, then unload

					RequestPath rPath = new RequestPath(design.getApplicationGroup(), design.getApplicationName()+System.getProperty(pmaSystem.PUAKMA_FILEEXT_SYSTEMKEY), design.getDesignName(), "");
					String sServiceName = rPath.getPathToDesign().toLowerCase();
					//track all services we find so we can kill active services later if required
					htFoundList.put(sServiceName, "");
					if(!m_htWidgets.containsKey(sServiceName))
					{
						try
						{  
							m_pSystem.doInformation("Loading widget: " + rPath.getPathToDesign(), this);
							WidgetItem wItem = new WidgetItem(m_pSystem, design, dtLastUpdated, rPath.getPathToDesign(), szComment, this);
							WidgetLoader wLoader = new WidgetLoader(wItem, m_htWidgets, sServiceName);
							wLoader.start();
							//if(wItem.startWidget()) m_htWidgets.put(sServiceName, wItem);
						}
						catch(Exception e)
						{
							m_pSystem.doError("WIDGIE.LoadWidgetsError", new String[]{e.toString()}, this);
						}
					}
					else //it exists, so check autoload and the lastupdate date
					{
						WidgetItem wItem = (WidgetItem)m_htWidgets.get(sServiceName);
						if(wItem!=null && wItem.hasChanged(dtLastUpdated) && m_bAutoReload)
						{
							//reload it.
							m_pSystem.doInformation("Reloading widget: " + rPath.getPathToDesign(), this);
							wItem.requestQuit();
							m_htWidgets.remove(sServiceName);
							wItem = new WidgetItem(m_pSystem, design, dtLastUpdated, rPath.getPathToDesign(), szComment, this);
							if(wItem.startWidget()) m_htWidgets.put(sServiceName, wItem);
						}
					}
				}//if !bDisabled
			}

		}
		catch (Exception sqle)
		{
			m_pSystem.doError("WIDGIE.LoadWidgetsError", new String[]{sqle.toString()}, this);
		}
		finally
		{
			Util.closeJDBC(rs);
			Util.closeJDBC(stmt);
			m_pSystem.releaseSystemConnection(cx);
		}

		doAutoUnload(htFoundList);
	}

	/**
	 * When passed a list of all the services found in the database, this function
	 * will try to autostop those that were not found. eg: They must have been removed
	 * @param htFoundList
	 */
	private void doAutoUnload(Hashtable htFoundList)
	{
		if(!m_bAutoReload) return;

		Enumeration en = m_htWidgets.elements();
		while(en.hasMoreElements())
		{
			WidgetItem wItem = (WidgetItem)en.nextElement();
			if(wItem!=null)
			{
				String sName = wItem.getName();
				if(!htFoundList.containsKey(sName.toLowerCase()))
				{
					m_pSystem.doInformation("Unloading widget: " + sName, this);
					m_htWidgets.remove(sName.toLowerCase());
					wItem.requestQuit();
				}
			}
		}//while
	}


	/**
	 *
	 */
	private void unloadWidgets()
	{
		Enumeration en = m_htWidgets.elements();
		while(en.hasMoreElements())
		{
			WidgetItem wItem = (WidgetItem)en.nextElement();
			if(wItem!=null)
			{
				String sName = wItem.getName();
				m_pSystem.doInformation("Unloading widget: " + sName, this);        
				m_htWidgets.remove(sName.toLowerCase());
				wItem.requestQuit();        
			}
		}//while
	}



	/**
	 *
	 * @param sCommand
	 * @return
	 */
	public String tell(String sCommand)
	{
		String sReturn = super.tell(sCommand);	
		if(sReturn!=null && sReturn.length()>0) return sReturn;

		if(sCommand.equalsIgnoreCase("?") || sCommand.equalsIgnoreCase("help"))
		{
			return "->dbpool status\r\n" +
			"->debug on|off|status\r\n" +        
			"->reload\r\n" +
			"->stats [statistickey]\r\n" +
			"->status\r\n";
		}

		if(sCommand.toLowerCase().equals("quit"))
		{
			requestQuit();
			return "-> shutting down\r\n";
		}

		if(sCommand.toLowerCase().equals("reload"))
		{
			unloadWidgets();
			try{ Thread.sleep(5000); }catch(Exception e){}
			loadWidgets();
			return "-> Reloaded.\r\n";
		}
		/*
    //this seems a bit pointless, so I removed it.
    if(szCommand.toLowerCase().equals("load"))
    {
      loadWidgets();      
      return "-> All widgets loaded.\r\n";
    }

    if(szCommand.toLowerCase().equals("unload"))
    {
      unloadWidgets();      
      return "-> All widgets unloaded.\r\n";
    }
		 */

		if(sCommand.toLowerCase().equals("status"))
		{
			sReturn = "-> status:\r\n";
			sReturn += "AutoReload is ";
			if(m_bAutoReload)
				sReturn += "ON";
			else
				sReturn += "OFF";

			sReturn += "\r\n\r\n";
			Enumeration<WidgetItem> en = m_htWidgets.elements();
			ArrayList<String> arr = new ArrayList<String>();
			while(en.hasMoreElements())
			{
				WidgetItem wItem = (WidgetItem)en.nextElement();
				//szReturn += wItem.toString() + "\r\n";
				arr.add(wItem.toString());
			}
			Collections.sort(arr);
			for(int i=0; i<arr.size(); i++)
			{
				sReturn += (String)arr.get(i) + "\r\n";
			}
			
			//sReturn += "Sessions: " + m_htSessions.size() + "\r\n";

			return sReturn;
		}

		if(sCommand.toLowerCase().equals("debug on"))
		{
			m_bDebug = true;
			return "-> Debug is now ON";
		}

		if(sCommand.toLowerCase().equals("debug off"))
		{
			m_bDebug = false;
			return "-> Debug is now OFF";
		}

		if(sCommand.toLowerCase().equals("debug status"))
		{
			if(m_bDebug) return "-> Debug is ON";

			return "-> Debug is OFF";
		}

		if(sCommand.toLowerCase().equals("dbpool status"))
		{
			return m_pSystem.getDBPoolStatus();
		}

		return sReturn;
	}

	/**
	 * Default message response. This should be overloaded in your class if
	 * required
	 * @param inMessage
	 * @return
	 */
	public AddInMessage sendMessage(AddInMessage inMessage)
	{
		AddInMessage am = super.sendMessage(inMessage);
		if(am!=null) return am;

		if(inMessage==null)
		{
			m_pSystem.doInformation("WIDGIE.InvalidMessage", this);
			return null;
		}


		SessionContext sessCtx = getMessageSender(inMessage);
		if(sessCtx==null) 
		{
			m_pSystem.doInformation("WIDGIE.InvalidSession", new String[]{inMessage.SessionID}, this);
			return null;
		}
		String sPath = inMessage.getParameter("RequestPath");
		String sBasePath = inMessage.getParameter("BasePath");
		String sEncoding = inMessage.getParameter("Content-Encoding");
		String sAcceptEncoding = inMessage.getParameter("Accept-Encoding");
		String sMethod = inMessage.getParameter("RequestMethod");
		if(sMethod==null) sMethod="GET";
		
		//m_pSystem.doDebug(0, "sendMessage() "+sPath, this);
		

		long lSize=0;
		InputStream isMessage=null;
		AddInMessage outMessage = new AddInMessage();
		if(inMessage.Data!=null)
		{
			//byte buf[] = oMessage.Data;        
			if(sEncoding!=null && sEncoding.equalsIgnoreCase("gzip")) inMessage.Data = puakma.util.Util.ungzipBuffer(inMessage.Data);
			isMessage = (InputStream)(new ByteArrayInputStream(inMessage.Data));      
			lSize=inMessage.Data.length;
			if(lSize==0)
			{
				String sErr = pmaLog.parseMessage( m_pSystem.getSystemMessageString("WIDGIE.NoDataPassed"), new String[]{sPath});
				m_pSystem.doError(sErr, sessCtx);
				outMessage.Data = sErr.getBytes();
				outMessage.Status = AddInMessage.STATUS_ERROR;
				return outMessage;
			}
		}
		if(inMessage.Attachment!=null)
		{
			try
			{
				isMessage = (InputStream)(new FileInputStream(inMessage.Attachment));
				if(sEncoding!=null && sEncoding.equalsIgnoreCase("gzip")) 
					isMessage = (InputStream)(new GZIPInputStream(new FileInputStream(inMessage.Attachment)));
			}
			catch(Exception e){}
			lSize=inMessage.Attachment.length();
		}
		//String szFromAccount="";

		//FOR DEBUG....
		//System.out.println(new String(oMessage.Data));
		if(m_bDebug && inMessage.Data!=null) m_pSystem.doDebug(0, new String(inMessage.Data), sessCtx);

		RequestPath rPathOriginal = new RequestPath(sPath);
		TornadoServerInstance tsi = TornadoServer.getInstance();
		TornadoApplication ta = tsi.getTornadoApplication(rPathOriginal.getPathToApplication());
		ta.setUpUserRoles(sessCtx);
		RequestPath rPath = ta.getRequestPath(); //remap to include the aster if there is one
		rPath.DesignElementName = rPathOriginal.DesignElementName;
		rPath.Action = rPathOriginal.Action;


		//System.out.println(rPath.getPathToApplication());

		HTTPSessionContext pSession = new HTTPSessionContext(m_pSystem, sessCtx, rPath);
		String sAction = rPath.Action.toLowerCase();
		if(sAction==null) sAction="";
		if(sAction.length()==0) sMethod = sMethod.toUpperCase();

		String sThreadID = Thread.currentThread().getName();
		//System.out.println("threadid="+sThreadID + " sessid="+sessCtx.getSessionID());		
		addSession(sThreadID, pSession);
		if(sAction.equals(DesignElement.PARAMETER_WIDGETLIST)) outMessage = getWidgetList(pSession, rPath, sBasePath);
		if(sAction.equals(DesignElement.PARAMETER_WIDGETWSDL) || (sAction.length()==0 && sMethod.equals("GET"))) outMessage = getWidgetWSDL(pSession, rPath, sBasePath);
		if(sAction.equals(DesignElement.PARAMETER_WIDGETEXECUTE) || (sAction.length()==0 && sMethod.equals("POST"))) outMessage = executeWidget(pSession, rPath, isMessage);
		//FIXME it is possible that this can be called multiple times by the same thread....
		//so removing by threadid may not be a good plan
		//m_htSessions.remove(sThreadID);
		removeSession(sThreadID);

		if(outMessage!=null && sAcceptEncoding!=null && sAcceptEncoding.indexOf("gzip")>=0)
		{
			//System.out.println("----------------------> gzipping reply.."+ am.Data.length);
			//System.out.println(new String(am.Data));
			outMessage.setParameter("Content-Encoding", "gzip");
			outMessage.Data = puakma.util.Util.gzipBuffer(outMessage.Data);
			//System.out.println("----------------------> gzipped.. " + am.Data.length);
			//System.out.println(new String(am.Data));
		}
		if(lSize>0 && inMessage.DeleteAttachmentWhenDone) inMessage.Attachment.delete();
		if(m_bDebug) m_pSystem.checkConnections(rPath.getFullPath(), sessCtx);
		return outMessage;
	}


	/**
	 * 
	 * @param sThreadID
	 * @param pSession
	 */
	private void addSession(String sThreadID, HTTPSessionContext pSession) 
	{
		//if the session does not exist in m_htSessions add it
		//if it does, increment the counter
		ArrayList arr = (ArrayList) m_htSessions.get(sThreadID);
		if(arr==null)
		{
			arr = new ArrayList();
			arr.add(pSession);
			arr.add(new Integer(1));
			m_htSessions.put(sThreadID, arr);
			return;
		}

		Integer i = (Integer) arr.get(SESSION_COUNTER_INDEX);
		i = new Integer(i.intValue()+1);
		arr.set(SESSION_COUNTER_INDEX, i);

	}

	/**
	 * Retrieves the session associated with the named thread
	 * @param sThreadID
	 * @return
	 */
	protected HTTPSessionContext getSessionFromThreadID(String sThreadID)
	{      
		//return (HTTPSessionContext)m_htSessions.get(sThreadName);
		ArrayList arr = (ArrayList) m_htSessions.get(sThreadID);
		if(arr==null) return null;

		return (HTTPSessionContext)arr.get(SESSION_OBJECT_INDEX);
	}

	/**
	 * 
	 * @param sThreadID
	 */
	private void removeSession(String sThreadID) 
	{
		//Find the record
		// decrement the counter, if zero remove the object
		ArrayList arr = (ArrayList) m_htSessions.get(sThreadID);
		if(arr==null) return;

		Integer i = (Integer) arr.get(SESSION_COUNTER_INDEX);
		i = new Integer(i.intValue()-1);
		if(i.intValue()<1)
		{
			m_htSessions.remove(sThreadID);
			return;
		}
		arr.set(SESSION_COUNTER_INDEX, i);		
	}

	/**
	 *
	 * @param pSession
	 * @param rPath
	 * @return
	 */
	private AddInMessage getWidgetList(HTTPSessionContext pSession, RequestPath rPath, String sBasePath)
	{
		AddInMessage am = new AddInMessage();
		am.ContentType = TYPE_XML;

		//**** KINDA UDDI OUTPUT.....
		String sPathToApp = rPath.getPathToApplication();
		StringBuilder sb = new StringBuilder(DEFAULT_SB_SIZE);
		sb.append("<?xml version=\"1.0\" " + XML_ENCODING + "?>\r\n");
		sb.append("<!-- PUAKMA WEB SERVICE LIST: AUTO-GENERATED -->\r\n");
		//if(m_pSystem.isLVersion()) sb.append("<!-- " + EVAL_MESSAGE + " -->\r\n");
		sb.append("<!-- Generated: " + puakma.util.Util.formatDate(new java.util.Date(), "yyyy-MM-dd HH:mm:ss") + " -->\r\n\r\n");

		sb.append("<serviceList>\r\n");
		Enumeration en = m_htWidgets.elements();
		while(en.hasMoreElements())
		{
			WidgetItem wItem = (WidgetItem)en.nextElement();
			if(wItem!=null)
			{
				if(wItem.matchesPath(sPathToApp) && pSession.hasUserRole(HTTPRequestManager.ACCESS_APP_ROLE))
				{
					StringBuilder sbOut = new StringBuilder(DEFAULT_SB_SIZE);
					sbOut.append("<service location=\"" + sBasePath + wItem.getDesignName() + "?WidgetWSDL\"><![CDATA[");
					sbOut.append(wItem.getDescription());
					sbOut.append("]]></service>\r\n");
					if(sbOut!=null) sb.append(sbOut);
				}
			}
		}
		sb.append("</serviceList>\r\n");

		try{ am.Data = sb.toString().getBytes("UTF-8"); }catch(Exception e){}
		am.Status = AddInMessage.STATUS_SUCCESS;
		this.incrementStatistic(STATISTIC_KEY_WIDGETLISTSPERHOUR, 1);
		return am;
	}


	/**
	 *
	 * @param pSession
	 * @param rPath
	 * @return
	 */
	private AddInMessage getWidgetWSDL(HTTPSessionContext pSession, RequestPath rPath, String sBasePath)
	{
		AddInMessage am = new AddInMessage();
		am.ContentType = TYPE_XML;

		String sPathToApp = rPath.getPathToDesign();
		//do this first so we can get the ACTUAL design element name
		StringBuilder sbMethods = new StringBuilder(DEFAULT_SB_SIZE);
		StringBuilder sbBindings = new StringBuilder(DEFAULT_SB_SIZE);
		StringBuilder sbPortTypes = new StringBuilder(DEFAULT_SB_SIZE);
		StringBuilder sbComplexTypes = new StringBuilder(DEFAULT_SB_SIZE);
		String sDesignName = null;
		String sDescription = "";
		Enumeration en = m_htWidgets.elements();
		while(en.hasMoreElements())
		{
			WidgetItem wItem = (WidgetItem)en.nextElement();
			if(wItem!=null)
			{
				if(wItem.matchesPath(sPathToApp) && pSession.hasUserRole(HTTPRequestManager.ACCESS_APP_ROLE))
				{
					if(sDesignName==null) sDesignName = wItem.getDesignName();
					StringBuilder sbOut = wItem.getWSDLMessages();
					StringBuilder sbBind = wItem.getWSDLBindings();
					StringBuilder sbPort = wItem.getWSDLPortTypes();
					StringBuilder sbComplex = wItem.getWSDLComplexTypes();
					sDescription = wItem.getDescription();
					if(sbOut!=null) sbMethods.append(sbOut);
					if(sbBind!=null) sbBindings.append(sbBind);
					if(sbPort!=null) sbPortTypes.append(sbPort);
					if(sbComplex!=null) sbComplexTypes.append(sbComplex);
				}
			}
		}

		//**** WSDL OUTPUT.....
		String sDefaultNamespace = "urn:DefaultNamespace"; //sDesignName
		StringBuilder sb = new StringBuilder(DEFAULT_SB_SIZE);
		sb.append("<?xml version=\"1.0\" " + XML_ENCODING + " ?>\r\n");
		sb.append("<!-- PUAKMA WEB SERVICE: AUTO-GENERATED SERVICE DEFINITION -->\r\n");
		sb.append("<!-- Generated: " + puakma.util.Util.formatDate(new java.util.Date(), "yyyy-MM-dd HH:mm:ss") + " -->\r\n\r\n");

		/*sb.append("<definitions name=\"" + sDesignName + "\"\r\n");
    sb.append("\ttargetNamespace=\"urn:" + sDesignName + "\"\r\n");
    sb.append("\txmlns:typens=\"urn:" + sDesignName + "\"\r\n");*/
		sb.append("<definitions targetNamespace=\"" + sDefaultNamespace + "\" ");
		//sb.append("\txmlns:typens=\"urn:" + sDesignName + "\" ");
		sb.append("xmlns=\"http://schemas.xmlsoap.org/wsdl/\" "); //default namespace
		sb.append("xmlns:wsdl=\"http://schemas.xmlsoap.org/wsdl/\" "); //and wsdl for fun
		sb.append("xmlns:apachesoap=\"http://xml.apache.org/xml-soap\" ");
		sb.append("xmlns:impl=\"" + sDefaultNamespace + "\" xmlns:intf=\"" + sDefaultNamespace + "\" ");
		sb.append("xmlns:soapenc=\"http://schemas.xmlsoap.org/soap/encoding/\" ");    
		sb.append("xmlns:soap=\"http://schemas.xmlsoap.org/wsdl/soap/\" ");
		sb.append("xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\">\r\n");

		/*
     <wsdl:definitions targetNamespace="urn:DefaultNamespace" xmlns:apachesoap="http://xml.apache.org/xml-soap" xmlns:impl="urn:DefaultNamespace" xmlns:intf="urn:DefaultNamespace" xmlns:soapenc="http://schemas.xmlsoap.org/soap/encoding/" xmlns:wsdl="http://schemas.xmlsoap.org/wsdl/" xmlns:wsdlsoap="http://schemas.xmlsoap.org/wsdl/soap/" xmlns:xsd="http://www.w3.org/2001/XMLSchema">
		 */

		/*sb.append("\txmlns:xsd=\"http://www.w3.org/2001/XMLSchema\"\r\n");
    sb.append("\txmlns:soap=\"http://schemas.xmlsoap.org/wsdl/soap/\"\r\n");
    sb.append("\txmlns:SOAP-ENC=\"http://schemas.xmlsoap.org/soap/encoding/\"\r\n");
    sb.append("\txmlns:wsdl=\"http://schemas.xmlsoap.org/wsdl/\"\r\n");
    sb.append("\txmlns=\"http://schemas.xmlsoap.org/wsdl/\">\r\n\r\n"); //default namespace
		 */

		sb.append("<!-- TYPES -->\r\n\r\n");
		if(sbComplexTypes.length()>0)
		{
			sb.append("<types>\r\n");

			sb.append("\t<schema targetNamespace=\"" + sDefaultNamespace + "\" xmlns=\"http://www.w3.org/2001/XMLSchema\">\r\n");
			sb.append("\t<import namespace=\"http://schemas.xmlsoap.org/soap/encoding/\" />\r\n");

			/*sb.append("\t<schema xmlns=\"http://www.w3.org/2001/XMLSchema\"\r\n");
      sb.append("\t\txmlns:SOAP-ENC=\"http://schemas.xmlsoap.org/soap/encoding/\"\r\n");
      sb.append("\t\txmlns:wsdl=\"http://schemas.xmlsoap.org/wsdl/\">\r\n");
			 */
			sb.append(sbComplexTypes);
			sb.append("\t</schema>\r\n");

			sb.append("</types>\r\n\r\n");
		}
		else
			sb.append("<types />\r\n\r\n");

		sb.append("<!-- MESSAGES -->\r\n\r\n");

		sb.append(sbMethods);

		sb.append("<!-- PORT TYPES -->\r\n\r\n");
		sb.append("<portType name=\"" + sDesignName + "Port\">\r\n");
		sb.append(sbPortTypes);
		sb.append("</portType>\r\n\r\n");

		sb.append("<!-- BINDINGS -->\r\n\r\n");
		sb.append("<binding name=\"" + sDesignName + "Binding\" type=\"impl:" + sDesignName + "Port\">\r\n");
		sb.append("\t<soap:binding style=\"rpc\" transport=\"http://schemas.xmlsoap.org/soap/http\" />\r\n");
		sb.append(sbBindings);
		sb.append("</binding>\r\n\r\n");

		sb.append("<!-- Endpoint for Web APIs -->\r\n");
		sb.append("<service name=\"" + sDesignName + "\">\r\n");
		sb.append("<documentation><![CDATA[" + sDescription + "]]></documentation>\r\n");
		sb.append("\t<port name=\"" + sDesignName + "Port\" binding=\"impl:" + sDesignName + "Binding\">\r\n");
		sb.append("\t<soap:address location=\"" + sBasePath + sDesignName + "?WidgetExecute\"/>\r\n");
		sb.append("\t</port>\r\n");
		sb.append("</service>\r\n\r\n");

		sb.append("</definitions>\r\n");

		//am.Data = sb.toString().getBytes();
		try{ am.Data = sb.toString().getBytes("UTF-8"); }catch(Exception e){}
		am.Status = AddInMessage.STATUS_SUCCESS;
		this.incrementStatistic(STATISTIC_KEY_WSDLSPERHOUR, 1);
		return am;
	}


	/**
	 * 
	 * @param pSession
	 * @param rPath
	 * @param isMessage
	 * @return
	 */
	private AddInMessage executeWidget(HTTPSessionContext pSession, RequestPath rPath, InputStream isMessage)
	{      		
		if(isMessage==null)
		{
			m_pSystem.doError("WIDGIE.ServiceNotFound", this);
			return null;
		}

		AddInMessage am = new AddInMessage();
		am.ContentType = TYPE_XML;    
		String sService="";
		String sMethName="";
		SOAPCallParser scpReply = new SOAPCallParser(m_pSystem);
		try
		{
			SOAPCallParser scp = new SOAPCallParser(m_pSystem, isMessage, rPath.DesignElementName);
			Object objReturn=null;
			sService = rPath.getPathToDesign();
			sMethName = scp.getMethod();
			//check if the user is allowed access to this widget
			boolean bCanAccessWidget = pSession.hasUserRole(rPath.getPathToApplication(), HTTPRequestManager.ACCESS_WS_ROLE+'.'+rPath.DesignElementName);
			if(!bCanAccessWidget)
			{
				if(HTTPServer.appHasRole(m_pSystem, rPath, HTTPRequestManager.ACCESS_WS_ROLE+'.'+rPath.DesignElementName))
				{
					am.Data = new byte[0];//"".getBytes();
					am.Status = AddInMessage.STATUS_NOT_AUTHORIZED;
					return am;
				}
			}
			//check if the user is allowed access to this widget's method
			boolean bCanAccessMethod = pSession.hasUserRole(rPath.getPathToApplication(), HTTPRequestManager.ACCESS_WS_ROLE+'.'+rPath.DesignElementName+'.'+sMethName);
			if(!bCanAccessMethod)
			{
				if(HTTPServer.appHasRole(m_pSystem, rPath, HTTPRequestManager.ACCESS_WS_ROLE+'.'+rPath.DesignElementName+'.'+sMethName))
				{
					am.Data = new byte[0];//"".getBytes();
					am.Status = AddInMessage.STATUS_NOT_AUTHORIZED;
					return am;
				}
			}
			//System.out.println("service="+sService);
			//System.out.println("method="+sMethName);
			//scpReply = new SOAPCallParser(m_pSystem);
			scpReply.setService(scp.getService());
			scpReply.setMethod(sMethName);
			WidgetItem wItem = getWidgetItem(sService);
			//System.out.println("witem="+wItem);
			if(m_bDebug) m_pSystem.doDebug(0, "CALLING:"+wItem.toString() + "->["+sMethName+"]", pSession);
			if(wItem!=null)
			{
				try
				{
					this.incrementStatistic(STATISTIC_KEY_WIDGETSPERHOUR, 1);
					objReturn = wItem.performService(pSession, sMethName, scp.getParams());
					scpReply.setObjectReturn(objReturn);
				}
				catch(Throwable pse)
				{
					Throwable cause = pse.getCause();
					if(cause!=null) pse = cause; 
					//Throwable t = pse.getCause();

					StackTraceElement ste[] = pse.getStackTrace();
					StringBuilder sbSTE = new StringBuilder();
					if(ste!=null)
					{
						for(int i=0; i<ste.length; i++)
						{
							sbSTE.append(ste[i] + "\r\n");
						}
					}

					String sMsg = pse.getMessage();
					if(sMsg==null) 
						sMsg = "";
					else
						sMsg += " ";
					scpReply.setFault(pse.getClass().getName(), sMsg + sbSTE.toString());          
				}
			}
			else
			{
				scpReply.setFault("", pmaLog.parseMessage(m_pSystem.getSystemMessageString("WIDGIE.WidgetNotExist"), new String[]{sService}));
			}

		}
		catch(java.lang.OutOfMemoryError ome)
		{
			m_pSystem.doError(ome.toString(), this);
			am.Data = new byte[0];//"".getBytes();
			am.Status = AddInMessage.STATUS_ERROR;
			return am;
		}
		catch(Throwable e)
		{
			String sErr = pmaLog.parseMessage(m_pSystem.getSystemMessageString("WIDGIE.SetupError"), new String[]{e.toString(), sService, sMethName});      
			m_pSystem.doError(sErr, this);
			e.printStackTrace();
			scpReply.setFault(this.getClass().getName(), sErr);
		}
		
		StringBuilder sb = new StringBuilder(DEFAULT_SB_SIZE);
		sb.append("<?xml version=\"1.0\" " + XML_ENCODING + " ?>\r\n");
		sb.append(scpReply.getSOAPMessage(true));    
		
		//System.out.println(sb.toString()); //for debug
		if(m_bDebug)
		{
			m_pSystem.doDebug(0, "---------- WIDGIE RESPONSE FOLLOWS ----------", pSession);
			m_pSystem.doDebug(0, sb.toString(), pSession);
		}

		try{ am.Data = sb.toString().getBytes("UTF-8"); }catch(Exception e){}

		am.Status = AddInMessage.STATUS_SUCCESS;
		return am;
	}


	/**
	 * get an item from the hashtable
	 * @param sService
	 * @return null if the item is not found
	 */
	private WidgetItem getWidgetItem(String sService)
	{
		WidgetItem wi = (WidgetItem)m_htWidgets.get(sService.toLowerCase());
		return wi;
	}

	/**
	 * Gets the version of this widgie server
	 * @return
	 */
	public double getVersion()
	{
		return WIDGIE_VERSION;
	}

	/**
	 * only allow this to be loaded once
	 */
	public boolean canLoadMultiple()
	{
		return false;
	}
}
