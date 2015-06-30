/** ***************************************************************
DesignElement.java
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
package puakma.addin.http.document;

import java.util.ArrayList;
import java.util.Date;

import puakma.pooler.CacheableItem;
import puakma.system.Parameter;
import puakma.system.pmaSystem;

/**
 * This class defines the design element pulled from the design bucket.
 *
 */
public class DesignElement implements Cloneable, CacheableItem
{
	private String m_sApplicationGroup;
	private String m_sApplicationName;
	private String m_sDesignName;
	private int m_iDesignType;
	private byte[] m_DesignData;
	private String m_sContentType;
	private java.util.Date m_dtLastModified;
	public String m_sFullClassName=null; //store the full class name so we don't have to keep parsing the binary class data (see ActionClassLoader)
	private ArrayList m_ParsedDocumentParts=null;  
	//private Hashtable htParameters=new Hashtable();
	private ArrayList m_arrParameters=new ArrayList();

	private Date m_dtLastAccess = new Date();

	public static final int DESIGN_TYPE_UNKNOWN = 0;
	public static final int DESIGN_TYPE_PAGE = 1;
	public static final int DESIGN_TYPE_RESOURCE = 2;
	public static final int DESIGN_TYPE_ACTION = 3;
	public static final int DESIGN_TYPE_LIBRARY = 4;
	public static final int DESIGN_TYPE_DOCUMENTATION = 5;
	public static final int DESIGN_TYPE_SCHEDULEDACTION = 6;
	public static final int DESIGN_TYPE_BUSINESSWIDGET = 7; //f#@k thinking of a name is difficult!

	public static final int DESIGN_TYPE_VORTEX_CONFIG = 100;


	//composite matching for multiple design types
	public static final int DESIGN_TYPE_HTTPACCESSIBLE = 10000;

	//make params lowercase so we don't have to convert them later
	//when they get compared to paths
	public static final String PARAMETER_OPENACTION = "openaction";
	public static final String PARAMETER_SAVEACTION = "saveaction";

	public static final String PARAMETER_OPENPAGE = "openpage";
	public static final String PARAMETER_READPAGE = "readpage";
	public static final String PARAMETER_SAVEPAGE = "savepage";

	public static final String PARAMETER_WIDGETLIST = "widgetlist";
	public static final String PARAMETER_WIDGETWSDL = "widgetwsdl";
	public static final String PARAMETER_WIDGETEXECUTE = "widgetexecute";

	public static final String PARAMETER_ANONYMOUSACCESS = "anonymousaccess";
	public static final String PARAMETER_MINIFYLEVEL = "minifylevel";

	public final static String DESIGN_PAGE_LOGIN = "$Login";


	public DesignElement()
	{
		m_sApplicationGroup = "";
		m_sApplicationName="";
		m_sDesignName="";
		m_iDesignType=0;
		m_DesignData=null;
		m_sContentType="text/html";
	}

	/**
	 * Make a copy of this object
	 */
	public Object clone() throws CloneNotSupportedException
	{
		return super.clone();
	}

	public byte[] getContent()
	{
		return m_DesignData;
	}

	public String getContentType()
	{
		return m_sContentType;
	}

	public int getDesignType()
	{
		return m_iDesignType;
	}

	public synchronized void setContentType(String sType)
	{
		m_sContentType = sType;
	}

	public synchronized void setDesignName(String sNewName)
	{
		m_sDesignName = sNewName;
	}

	public synchronized void setApplicationName(String sNewName)
	{
		if(sNewName==null)
			m_sApplicationName = "";
		else
			m_sApplicationName = sNewName;
	}

	public synchronized void setApplicationGroup(String sNewGroup)
	{
		if(sNewGroup==null)
			m_sApplicationGroup = "";
		else
			m_sApplicationGroup = sNewGroup;
	}

	public synchronized void setDesignType(int iType)
	{
		m_iDesignType = iType;
	}

	public synchronized void setDesignData(byte[] data)
	{
		m_DesignData = data;
	}

	public Date getLastModified()
	{
		return m_dtLastModified;
	}

	public synchronized void setLastModified(Date dtNewModified)
	{
		m_dtLastModified = dtNewModified;
	}

	public int getDesignDataLength()
	{
		if(m_DesignData==null) return 0;
		return m_DesignData.length;
	}


	public byte[] getDesignData()
	{
		return m_DesignData;
	}

	public String getDesignName()
	{
		return m_sDesignName;
	}

	public String getApplicationName()
	{
		return m_sApplicationName;
	}

	public String getApplicationGroup()
	{
		return m_sApplicationGroup;
	}

	/**
	 * Get the unique identifier for this design object. basically returns
	 * a URL "/group/app.pma/designName/1"
	 * CacheableItem interface
	 */
	public String getItemKey()
	{
		String sReturn = "";
		if(m_sApplicationGroup.length()!=0) sReturn += '/' + m_sApplicationGroup;		
		if(m_sApplicationName.length()!=0) sReturn += '/' + m_sApplicationName + System.getProperty(pmaSystem.PUAKMA_FILEEXT_SYSTEMKEY);
		if(m_sDesignName.length()!=0) sReturn += '/' + m_sDesignName + '/' + m_iDesignType;

		return sReturn.toLowerCase();
	}

	/**
	 * return a rough approximation of the amout of memory (in bytes) this object occupies
	 * CacheableItem interface
	 */
	public double getSize()
	{
		return getDesignDataLength() + 50;
	}

	/**
	 * Record the last time this item was used.
	 * Used to determine if the item has expired
	 */
	public synchronized void logCacheAccess()
	{
		m_dtLastAccess= new Date();
	}

	/**
	 * Determine if this cacheableitem has expired
	 */
	public boolean itemHasExpired(long lMsDifference)
	{
		long lLastAccess = m_dtLastAccess.getTime();
		long lNow = System.currentTimeMillis();
		if(lNow-lLastAccess > lMsDifference) return true;
		return false;
	}



	/**
	 * Adds a parameter object to the list. Multiple keys of the same value are allowed
	 */
	public synchronized void addParameter(String paramName, String paramValue)
	{
		if(paramName==null) return;
		if(paramValue==null) paramValue = "";
		String sKey = paramName.toLowerCase();
		Parameter p = new Parameter(sKey, paramValue);		
		m_arrParameters.add(p);
	}

	/**
	 * Gets a parameter value. If the parameter does not exist, returns "".
	 * If there are multiple parameters, only the first match is returned.
	 */
	public String getParameterValue(String paramName)
	{		
		for(int i=0; i<m_arrParameters.size(); i++)
		{
			Parameter p = (Parameter)m_arrParameters.get(i);
			if(p!=null && p.Name.equalsIgnoreCase(paramName)) return p.Value;
		}

		return "";
	}


	/**
	 * Returns an ArrayList describing all parameters
	 */
	public ArrayList getParameters()
	{
		return m_arrParameters;
	}

	public String toString()
	{
		int iSize=0;
		if(m_DesignData!=null) iSize = m_DesignData.length;
		return getItemKey() + "   LastMod: " + m_dtLastModified + "   DataSize:" + iSize + "bytes";
	}

	/**
	 * Determines if this design element allows anonymous access, based on the DesignBucketParam
	 * "ANONYMOUSACCESS" = "1"
	 * @return
	 */
	public boolean allowAnonymousAccess() 
	{
		String sAllowAnon = getParameterValue(PARAMETER_ANONYMOUSACCESS);
		if(sAllowAnon!=null && sAllowAnon.equals("1")) return true;
		return false;
	}

	/**
	 * Determines if this design element can be minified. eg Extra spaces, tabs CR LF
	 * removed.
	 * @return
	 */
	public int getMinifyLevel() 
	{
		String sMinifyLevel = getParameterValue(PARAMETER_MINIFYLEVEL);
		try{
			//must be Javascript and have minify set.
			if(m_sContentType.indexOf("/javascript")<0) return 0;			
			return Integer.parseInt(sMinifyLevel);
		}
		catch(Exception e){}
		return 0;
	}

	/**
	 * 
	 * @param docDestination
	 * @param bOverwriteExisting
	 */
	public void copyControls(HTMLDocument docDestination, boolean bOverwriteExisting) 
	{
		if(m_ParsedDocumentParts==null) return;

		for(int i=0; i<m_ParsedDocumentParts.size(); i++)
		{
			Object obj = m_ParsedDocumentParts.get(i);
			if(obj instanceof HTMLControl)
			{
				HTMLControl dElement = (HTMLControl) obj;
				boolean bHasControl = docDestination.getHTMLControl(dElement.getName())!=null;
				if((bHasControl && bOverwriteExisting) || !bHasControl) 
					docDestination.setHTMLControl(dElement.getName(), dElement);
			}			
		}//for
	}
	/**
	 * 
	 * @param sItemName
	 * @param ctrl
	 */
	public void setHTMLControl(String sItemName, HTMLControl ctrl)
	{
		if(m_ParsedDocumentParts==null || sItemName==null || sItemName.length()==0) return;

		boolean bFound = false;
		for(int i=0; i<m_ParsedDocumentParts.size(); i++)
		{
			Object obj = m_ParsedDocumentParts.get(i);
			if(obj instanceof HTMLControl)
			{
				HTMLControl dElement = (HTMLControl) obj;
				if(dElement.getName().equalsIgnoreCase(sItemName))	
				{
					m_ParsedDocumentParts.set(i, ctrl);
					//System.out.println("setHTMLControl("+sItemName+") - exists in m_ParsedDocumentParts" );
					bFound = true;
				}
			}			
		}//for

		if(!bFound) 
		{
			m_ParsedDocumentParts.add(ctrl);
			//System.out.println("setHTMLControl("+sItemName+") - ADDING TO m_ParsedDocumentParts" );
		}
	}

	/**
	 * 
	 * @param doc
	 * @param bAlwaysReturnAValidList
	 * @return
	 */
	public ArrayList getParsedDocumentParts(HTMLDocument doc, boolean bAlwaysReturnAValidList) 
	{		
		if(bAlwaysReturnAValidList && m_ParsedDocumentParts==null) return new ArrayList();

		if(m_ParsedDocumentParts==null) return null;

		ArrayList arr = new ArrayList(m_ParsedDocumentParts.size());
		for(int i=0; i<m_ParsedDocumentParts.size(); i++)
		{
			Object obj = m_ParsedDocumentParts.get(i);
			if(obj instanceof HTMLControl)
			{
				HTMLControl dElement = (HTMLControl) obj;
				HTMLControl ctrlNew = new HTMLControl(doc, dElement.getRawTag());
				arr.add(ctrlNew);				
			}
			else
				arr.add(obj);
		}
		return arr;
	}

	public void addParsedDocumentPart(Object obj) 
	{
		if(m_ParsedDocumentParts==null) m_ParsedDocumentParts = new ArrayList(50);
		m_ParsedDocumentParts.add(obj);
	}

	/**
	 * required prior to the call to a computed page so we don't get the old page data
	 */
	public void removeParsedDocumentParts() 
	{
		m_ParsedDocumentParts = null;
	}

	public boolean hasParsedDocumentParts() 
	{
		return m_ParsedDocumentParts!=null;
	}
}