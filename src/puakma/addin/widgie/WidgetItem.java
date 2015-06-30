/** ***************************************************************
WidgetItem.java
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

import java.util.Date;

import puakma.addin.http.action.HTTPSessionContext;
import puakma.addin.http.action.SharedActionClassLoader;
import puakma.addin.http.document.DesignElement;
import puakma.error.ErrorDetect;
import puakma.system.RequestPath;
import puakma.system.SystemContext;
import puakma.system.pmaSystem;

/**
 * This class describes a widget. It is what gets loaded in to the Hashtable, and
 * it contains a running BusinessWidget
 */
public class WidgetItem implements ErrorDetect
{
	private BusinessWidget m_Widget;
	private Date m_dtLastUpdated=new Date();
	private Date m_dtLoaded=null;
	private long m_lAccessCount=0;
	private String m_sWidgetName="";
	private SystemContext m_pSystem;
	private String m_sComment="";
	private String m_sDesignName="";
	private WIDGIE m_WidgetContainer;
	private static final int DEFAULT_SB_SIZE = 4096;

	public WidgetItem(SystemContext paramSystem, DesignElement de, Date dtLastUpdated, String sName, String sComment, WIDGIE wContainer)
	{
		if(dtLastUpdated!=null) m_dtLastUpdated = dtLastUpdated;
		m_WidgetContainer = wContainer;
		m_sWidgetName = sName;
		m_pSystem = paramSystem;
		m_sComment = sComment;
		m_sDesignName = de.getDesignName();
	}

	/**
	 * reset the counters are start the widget
	 */
	public boolean startWidget()
	{
		RequestPath rPath = new RequestPath(m_sWidgetName);
		//System.out.println("startWidget() " + rPath.getFullPath());

		try
		{
			SystemContext sysCtx = (SystemContext)m_pSystem.clone(); //so the programmer doesn't destroy our system object!
			//ClassLoader originalLoader = Thread.currentThread().getContextClassLoader();
			SharedActionClassLoader aLoader = m_pSystem.getActionClassLoader(rPath); //, DesignElement.DESIGN_TYPE_BUSINESSWIDGET);
			Class runclass = aLoader.getActionClass(rPath.DesignElementName, DesignElement.DESIGN_TYPE_BUSINESSWIDGET);
			if(runclass!=null)
			{
				Object object = runclass.newInstance();
				if( object instanceof BusinessWidget )
				{
					m_Widget = (BusinessWidget)object;
					m_Widget.setName("bw:"+rPath.DesignElementName);
					m_Widget.setDescription(m_sComment);
					m_sComment="";

					//Thread.currentThread().setContextClassLoader(aLoader);					
					m_Widget.setContextClassLoader(aLoader);
					m_Widget.init(sysCtx, m_WidgetContainer, rPath);					
					m_Widget.start();
					m_dtLoaded = new Date();
					m_lAccessCount=0;
					return true;
				}
				else
				{
					m_pSystem.doError("WIDGIE.ClassCastError", new String[]{m_sWidgetName, runclass.getName()}, this);
					//it's dead
					return false;
				}
			}
		}
		catch(Throwable e)
		{
			m_pSystem.doError("WIDGIE.ExecuteError", new String[]{m_sWidgetName, e.toString()}, this); 
			e.printStackTrace();
		}

		return false;
	}

	/**
	 * Tell the widget it is time to go...
	 */
	public void requestQuit()
	{
		if(m_Widget!=null) m_Widget.postQuitMessage();
	}


	/**
	 * This is where the service is called.
	 * @param sMethodName
	 * @param params
	 * @return
	 */
	public Object performService(HTTPSessionContext sess, String sMethodName, Object params[]) throws Exception
	{
		//m_lAccessCount++; //Hmmm not thread safe?
		//long lStart = System.currentTimeMillis();
		//m_pSystem.doDebug(0, "1. performService() ->["+sMethodName+"] " + (System.currentTimeMillis()-lStart) + "ms", sess);
		incrementAccesCount();
		if(m_Widget!=null) 
		{
			Object objReturn = m_Widget.performService(sess, sMethodName, params);
			//m_pSystem.doDebug(0, "2. performService() ->["+sMethodName+"] " + (System.currentTimeMillis()-lStart) + "ms", sess);
			return objReturn;
		}
		return null;
	}


	/**
	 * The name of this widget
	 * @return
	 */
	public String getName()
	{
		return m_sWidgetName;
	}

	/**
	 * The name of this widget
	 * @return
	 */
	public String getServiceName()
	{
		int iPos = m_sWidgetName.lastIndexOf('/');
		if(iPos>=0) return m_sWidgetName.substring(iPos+1, m_sWidgetName.length());

		return m_sWidgetName;
	}


	public String toString()
	{
		String sStarted = "(Not started)";
		if(m_dtLoaded!=null) sStarted = "Started:" + puakma.util.Util.formatDate(m_dtLoaded, "dd.MMM.yy HH:mm:ss");
		return m_sWidgetName + "  Hits:" + m_lAccessCount + " " + sStarted;
	}


	/**
	 * Returns the name of the design element
	 * @return
	 */
	public String getDesignName()
	{
		return m_sDesignName;
	}

	/**
	 * gets the description of this service
	 * @return
	 */
	public String getDescription()
	{
		if(m_Widget!=null) return m_Widget.getDescription();
		return "";
	}

	/**
	 * sets the description of this service
	 */
	public void setDescription(String sNewDesc)
	{
		if(m_Widget!=null) m_Widget.setDescription(sNewDesc);
	}

	/**
	 * Decide if the design of this service has changed based on the last updated date
	 * @param dtModified
	 * @return
	 */
	public boolean hasChanged(Date dtModified)
	{
		if(dtModified.getTime()==m_dtLastUpdated.getTime()) return false;
		return true;
	}



	/**
	 * increase the number of times this object has been used
	 */
	public synchronized void incrementAccesCount()
	{
		m_lAccessCount++;
	}


	/**
	 * For WSDL XML output
	 * @return
	 */
	public StringBuilder getXMLServiceDescription()
	{
		StringBuilder sb = new StringBuilder(DEFAULT_SB_SIZE);
		sb.append("<service name=\"" + getServiceName() + "\">\r\n");
		sb.append("\t<wsdl>" + m_sWidgetName + "?WidgetWSDL</wsdl>\r\n");
		sb.append("\t<description>" + m_sComment + "</description>\r\n");
		sb.append("\t<last-modified>" + m_dtLastUpdated + "</last-modified>\r\n");
		sb.append("\t<loaded-date>" + m_dtLoaded + "</loaded-date>\r\n");
		sb.append("\t<accesses>" + m_lAccessCount + "</accesses>\r\n");
		sb.append("</service>\r\n");

		return sb;
	}


	/**
	 * For WSDL XML output
	 * @return
	 */
	public StringBuilder getWSDLMessages()
	{
		if(m_Widget!=null) return m_Widget.getWSDLMessages();
		return null;
	}

	public StringBuilder getWSDLBindings()
	{
		if(m_Widget!=null) return m_Widget.getWSDLBindings(m_sDesignName);
		return null;
	}

	public StringBuilder getWSDLPortTypes()
	{
		if(m_Widget!=null) return m_Widget.getWSDLPortTypes();
		return null;
	}

	public StringBuilder getWSDLComplexTypes()
	{
		if(m_Widget!=null) return m_Widget.getWSDLComplexTypes();
		return null;
	}

	/**
	 * Determines if this widget comes from the sPathToApp passed eg "/group/app.pma"
	 * @param sPathToApp
	 * @return
	 */
	public boolean matchesPath(String sPathToApp)
	{      
		if(m_sWidgetName.toLowerCase().startsWith(sPathToApp.toLowerCase())) return true;
		return false;
	}

	public String getErrorSource()
	{
		return this.getClass().getName();
	}

	public String getErrorUser()
	{
		return pmaSystem.SYSTEM_ACCOUNT;
	}
}
