/* ***************************************************************
RequestPath.java
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
package puakma.system;

import puakma.addin.http.document.*;
import puakma.util.Util;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.ArrayList;

/**
 * The RequestPath object is used to make and parse URIs to Tornado applications.
 * @author bupson
 *
 */
public class RequestPath
{
	public String Group="";
	public String Application="";  
	public String DesignElementName="";
	public String Action="";
	public String Parameters="";
	public int DesignType=0;
	public static String ENC_STYLE = "UTF-8";
	private String m_sPuakmaFileExt=".pma";
	public String FileExt=m_sPuakmaFileExt;
	
	public String Protocol = null;
	public String Host = null;
	public int Port = -1; //use default
	private boolean m_bUseTornadoEscaping = true;
	public static int DEFAULT_HTTP_PORT=80;
	public static int DEFAULT_HTTPS_PORT=443;

	/**
	 * Create a new RequestPath object
	 * @param sGroup
	 * @param sApp
	 * @param sDesignElementName
	 * @param sAction
	 */
	public RequestPath(String sGroup, String sApp, String sDesignElementName, String sAction)
	{
		String sTemp = System.getProperty(pmaSystem.PUAKMA_FILEEXT_SYSTEMKEY);
		if(sTemp!=null && sTemp.length()>0) m_sPuakmaFileExt = sTemp;

		StringBuilder sbFullPath=new StringBuilder(50);

		if(sGroup!=null && sGroup.length()!=0)
		{
			sbFullPath.append('/');
			sbFullPath.append(sGroup);
		}
		//szFullPath += "/" + szGroup;
		if(sApp!=null && sApp.length()!=0)
		{
			sbFullPath.append('/');
			sbFullPath.append(sApp);
			if(!sApp.toLowerCase().endsWith(m_sPuakmaFileExt.toLowerCase())) sbFullPath.append(m_sPuakmaFileExt);
		}
		//szFullPath += "/" + szApp;
		if(sDesignElementName!=null && sDesignElementName.length()!=0)
		{
			sbFullPath.append('/');
			sbFullPath.append(sDesignElementName);
		}
		//szFullPath += "/" + szDesign;
		if(sAction!=null && sAction.length()!=0)
		{
			sbFullPath.append('?');
			sbFullPath.append(sAction);
		}
		//szFullPath += "?" + szAction;
		setFullPath(sbFullPath.toString());      
	}
	/**
	 * Create a new object from the URL "/group/database.pma/designelement?Action&Parameters".
	 * May be:<br>
	 * <br>
	 * <code> 
	 * /database.pma
	 * /group/database.pma
	 * /group/database.pma/designelement
	 * </code>
	 * @param sFullPath the full URI, eg "/group/database.pma/designelement?Action&Parameters"
	 */
	public RequestPath(String sFullPath)
	{
		setFullPath(sFullPath);
	}
	
	public void setUseTornadoEscaping(boolean bUseTornadoEscaping)
	{
		m_bUseTornadoEscaping = bUseTornadoEscaping;
	}
	/**
	 * Return the full URL or URI if host parts not provided
	 * @return
	 */
	public String getURL()
	{
		if(Host==null) return getFullPath();
		
		String sProto = Protocol;
		if(sProto==null || sProto.length()==0) sProto = "http";
		String sPort = Port>0 ? (":" + Port): "";
		
		return sProto + "://" + Host + sPort + getFullPath();		
	}

	public static void main(String args[])
	{
		String sURL = "http://junk.com:8080/test/x.pma/Somepage?OpenPage&Id=3";
		//String sURL = "http://notestest01.odense.dk/Wiki/BMF/ITVidenTEST_2.nsf/dx/Tjek_telefon_fra_server";
		//String sURL = "/test/x.pma/files/test.png?Id=3";
		//String sURL = "/dev/saml.pma/acs?code=4/0AX4XfWgXokkJyVCatYdNIzylHQ7HwrfHi9kMfTCjEkNsLzcpqZVm47v1co6LDoo2EBLV9Q&scope=email%20https://www.googleapis.com/auth/userinfo.email%20openid";
		RequestPath r = new RequestPath(sURL);
		r.setUseTornadoEscaping(false);
		//r.Host = "";
		System.out.println("isPuakmaApplication() = "+r.isPuakmaApplication());
		System.out.println(r.toString());
	}
	/**
	 * This is the main method where the parsing occurs
	 * @param sFullPath the full URI, eg "/group/database.pma/designelement?Action&Parameters"
	 */
	public void setFullPath(String sFullPath)
	{
		String sTemp = System.getProperty(pmaSystem.PUAKMA_FILEEXT_SYSTEMKEY);
		if(sTemp!=null && sTemp.length()>0) m_sPuakmaFileExt = sTemp;

		if(sFullPath==null || sFullPath.length()==0) return;

		int iQuestionPos = sFullPath.indexOf('?');
		int iPos = sFullPath.indexOf("://");
		if(iPos>=0 && (iQuestionPos<0 || iPos<iQuestionPos))
		{
			Protocol = sFullPath.substring(0, iPos);
			sFullPath = sFullPath.substring(iPos+3);
			iPos = sFullPath.indexOf('/');
			if(iPos>=0)
			{
				Host = sFullPath.substring(0, iPos);
				sFullPath = sFullPath.substring(iPos); //leave the leading slash
				
				iPos = Host.indexOf(':');//check for port
				if(iPos>=0)
				{
					String sPort = Host.substring(iPos+1);
					Port = (int)Util.toInteger(sPort);
					Host = Host.substring(0, iPos);
				}
			}
		}
		String sBasePath = sFullPath;
		String sAction="";
		//break the string into 2     
		iPos = sFullPath.indexOf('?');
		if(iPos<0) iPos = sFullPath.indexOf('&');
		if(iPos>0)
		{
			sBasePath = sFullPath.substring(0, iPos);
			sAction = sFullPath.substring(iPos, sFullPath.length());
		}
		sBasePath = '/' + puakma.util.Util.trimChar(sBasePath, '/');

		//get the group and appname from the sBasePath
		String sExt = "";
		int iFileExtPos = sBasePath.lastIndexOf('.');
		if(iFileExtPos>=0) //we have a file extension
		{
			//now check to see if the design element has the . or it really is the file ext
			int iPmaExtPos = sBasePath.lastIndexOf(m_sPuakmaFileExt+'/'); //eg ".pma/"
			if(iPmaExtPos>=0 && iPmaExtPos<iFileExtPos) iFileExtPos = iPmaExtPos;

			sExt = sBasePath.substring(iFileExtPos);
			int iEndPos = sExt.indexOf('/');
			if(iEndPos>=0) sExt = sExt.substring(0, iEndPos);      
		}
		FileExt = sExt;
		int iDBOffset = sBasePath.length();
		if(sExt.length()>0) iDBOffset = sBasePath.indexOf(sExt);

		String sNoDesignPath = sBasePath;
		if(iDBOffset>0)
		{
			int iExtLength = sExt.length();
			int iDesignEnd = sBasePath.length();
			if(iDBOffset+iExtLength+1 < iDesignEnd)//we have a design element specified
			{
				DesignElementName = sBasePath.substring(iDBOffset+iExtLength+1, sBasePath.length());
				sNoDesignPath = sBasePath.substring(0, iDBOffset+iExtLength);
			}          
			int iAppOffset=sNoDesignPath.lastIndexOf('/');
			if(iAppOffset>=0) Application = sNoDesignPath.substring(iAppOffset+1, sNoDesignPath.length()-iExtLength);
			if(iAppOffset>1) Group = sNoDesignPath.substring(1, iAppOffset);
		}



		//get the parameters and action from sAction
		int iActionOffset=sAction.indexOf('?');
		if(iActionOffset>=0)
		{          
			Action = sAction.substring(iActionOffset+1, sAction.length());
			int iParamOffset = Action.indexOf('&');
			if(iParamOffset >= 0)
			{
				Parameters = Action.substring(iParamOffset, Action.length());
				Action = Action.substring(0, iParamOffset);
			}
		}
		/*
		 * //BU 10/2/12 you must specify a '?' in order to get parameters...
		else //check for parameters only ie /fred.pma&ID=8
		{
			int iParamOffset = sAction.indexOf('&');
			if(iParamOffset >= 0)
			{
				Parameters = sAction.substring(iParamOffset, sAction.length());  
			}
		}*/

		//we have specified a "/group/app.pma?Param=x"
		//Bad programmer, bad ;-)
		if(Action.indexOf('=')>=0)
		{
			if(Action.indexOf('&')>=0)
				Parameters += Action;
			else
				Parameters += '&' + Action;
			Action="";
		}

		//check IE hasn't sent us the #namedlink http://ray.camdenfamily.com/index.cfm/2005/10/8/IIS6-Bug-with-CFLOCATION
		int iHashOffset = Parameters.indexOf('#');
		if(iHashOffset>=0)
		{
			String sStart = Parameters.substring(0, iHashOffset);        
			String sEnd = Parameters.substring(iHashOffset);
			int iParamOffset = sEnd.indexOf('&');        
			if(iParamOffset >= 0)
			{
				sEnd = sEnd.substring(iParamOffset);
			}
			else
				sEnd=""; //#xyz is at the end of the URI
			Parameters = sStart + sEnd;        
		}

		try
		{
			Application = URLDecoder.decode(Application, ENC_STYLE);
			Group = URLDecoder.decode(Group, ENC_STYLE);
			DesignElementName = URLDecoder.decode(DesignElementName, ENC_STYLE);
		}
		catch(Exception r){}


		getDesignType();
	}

	/**
	 * Determine if this URL is for a Puakma application on this server. This is 
	 * based on the file type, usually ".pma"
	 *
	 */
	public boolean isPuakmaApplication()
	{
		if(FileExt.equalsIgnoreCase(this.m_sPuakmaFileExt)) return true;
		return false;
	}

	
	
	/**
	 * Determines the design type (int) of the requested design element based on the Action
	 * If there is no Action, it will remain 0
	 */
	private void getDesignType()
	{
		DesignType=0;
		String szTmpAction=Action.toLowerCase();
		if(Action.length()==0) return;

		if(szTmpAction.endsWith("page"))      
		{
			DesignType=DesignElement.DESIGN_TYPE_PAGE;
			return;
		}
		if(szTmpAction.endsWith("resource"))
		{
			DesignType=DesignElement.DESIGN_TYPE_RESOURCE;
			return;
		}
		if(szTmpAction.endsWith("action"))
		{
			DesignType=DesignElement.DESIGN_TYPE_ACTION;
			return;
		}
	}

	/**
	 * For debugging purposes display the internal parts of this object as a string
	 */
	public String toString()
	{
		return "Protocol=["+Protocol + "] Port=["+Port+"] Host=["+Host+"] Group=[" + Group + "] Application=[" + Application + "] FileExt=["+FileExt+"] DesignElementName=[" + DesignElementName + "] Action=[" + Action + "] Parameters=[" + Parameters + "] DesignType=["+DesignType+"]";
	}

	/**
	 * Gets the path up to and including the application
	 * used to construct an URL mostly for determining image paths relative
	 * to the app.
	 */
	public String getPathToApplication()
	{    
		StringBuilder sb = new StringBuilder(50);
		if(Group.length()>0)
		{
			sb.append('/');
			//try{ sb.append(URLEncoder.encode(Group, ENC_STYLE)); }catch(Exception y){}
			sb.append(getEncoded(Group));
		}
		sb.append('/');
		sb.append(getEncoded(Application));    
		sb.append(FileExt);
		return sb.toString();
	}
	
	private String getEncoded(String sURLPart)
	{
		//FIXME switch
		if(!m_bUseTornadoEscaping ) return sURLPart;
		try{ return URLEncoder.encode(sURLPart, ENC_STYLE); }catch(Exception y){}
		return "";
	}

	/**
	 * Gets the path up to and including the design element
	 * used to construct an URL mostly for SavePage
	 */
	public String getPathToDesign()
	{       
		StringBuilder sb = new StringBuilder(50);
		sb.append(getPathToApplication());
		sb.append('/');
		//sb.append(getEncoded(DesignElementName));
		sb.append(DesignElementName);
		//try{ sb.append(URLEncoder.encode(DesignElementName, ENC_STYLE)); }catch(Exception y){}    

		return sb.toString();
	}

	/**
	 * Removes a parameter from the document.
	 * @param sParamName the name of the parameter to remove.
	 */
	public void removeParameter(String sParamName)
	{
		if(Parameters==null || Parameters.length()==0 || sParamName==null || sParamName.length()==0) return;
		int iParamStart=0;
		int iParamEnd=0;
		String szRemainder="";
		if(!sParamName.startsWith("&")) sParamName = '&' + sParamName;

		iParamStart = Parameters.toLowerCase().indexOf(sParamName.toLowerCase());
		if(iParamStart>=0) //has the parameter
		{
			szRemainder = Parameters.substring(iParamStart+1, Parameters.length());
			Parameters = Parameters.substring(0, iParamStart);
			iParamEnd = szRemainder.indexOf('&');
			if(iParamEnd>0) //must be at least 1 char!
			{
				szRemainder = szRemainder.substring(iParamEnd, szRemainder.length());
			}
			else
				szRemainder="";

			Parameters += szRemainder;
		}
	}

	/**
	 * Returns a parameter value from the URI. Return null if the parameter name does not exist
	 * @param sParamName the name of the parameter value to return
	 * @return the value of the named parameter as a String. Returns null if the parameter
	 * does not exist.
	 */
	public String getParameter(String sParamName)
	{
		if(Parameters==null || Parameters.length()==0 || sParamName==null || sParamName.length()==0) return null;
		ArrayList<String> arrParams = Util.splitString(Parameters, '&');
		for(int i=0; i<arrParams.size(); i++)
		{
			String sCurrentParameter = (String)arrParams.get(i);
			int iPos = sCurrentParameter.indexOf('=');
			if(iPos>=0)
			{
				String sCurrentParamName = sCurrentParameter.substring(0, iPos);
				if(sCurrentParamName.equalsIgnoreCase(sParamName))
				{
					return sCurrentParameter.substring(iPos+1);
				}
			}

		}
		return null;
	}


	/**
	 * Gets the full path including the parameters
	 * used to construct an URL mostly for SavePage and redirect
	 * @return the full URI including design element, action and any parameters
	 */
	public String getFullPath()
	{
		StringBuilder sb = new StringBuilder(60);
		sb.append(getPathToDesign());
		
		if(Action.length()>0 || Parameters.length()>0) sb.append('?');
		
		if(Action.length()>0) sb.append(Action);		    
		if(Parameters.length()>0) sb.append(Parameters);
		  
		return sb.toString();
	}

}//class