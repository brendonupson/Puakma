/** ***************************************************************
AddInMessage.java
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

import java.io.File;
import java.util.*;
/**
 * <p>Title: AddInMessage</p>
 * <p>Description: For passing messages between server AddIn tasks. This is not threadsafe!</p>
 * <p>Copyright: Copyright (c) 2002</p>
 * <p>Company: </p>
 * @author Brendon Upson
 * @version 1.0
 */

public class AddInMessage
{
  public final static int STATUS_PENDING=-1;
  public final static int STATUS_SUCCESS=0;
  public final static int STATUS_ERROR=1;
  public final static int STATUS_UNKNOWNMESSAGE=2;
  public final static int STATUS_NOT_AUTHORIZED=3;

  public String ContentType="text/plain";
  public int Status=STATUS_PENDING;
  public byte Data[]=null;
  public File Attachment=null;
  public boolean DeleteAttachmentWhenDone=false;
  public String DestinationHost=null; //null means this machine, format = "www.abc.com:3425"

  //for authentication
  public String SessionID="";
  public String UserName="";
  public String Password="";
  public boolean UseSecureConnection=false; //use SSL to connect to the remote host

  private Hashtable m_htParameters=new Hashtable();
  private Hashtable m_htObjects=new Hashtable();

  /**
   * Dummy constructor so the object may be created
   */
  public AddInMessage()
  {
  }
  
  public AddInMessage(int iStatus)
  {
	  Status = iStatus;
  }
  
  /**
   * 
   */
  public String toString()
  {
	  int iLen = 0;
	  if(Data!=null) iLen = Data.length;
	  StringBuilder sbParams = new StringBuilder();
	  Iterator itKeys = m_htParameters.keySet().iterator();
	  while(itKeys.hasNext())
	  {
		  String sKey = (String)itKeys.next();
		  String sValue = (String) m_htParameters.get(sKey);
		  
		  if(sbParams.length()>0) sbParams.append(", ");
		  sbParams.append(sKey + ": [" + sValue + "]");
	  }
	  return "sid:"+SessionID + " status:" + Status + " datalength:"+iLen + " params: " + sbParams;
  }

  /**
   * Get a parameter value
   * @param sKey
   * @return null if parameter not found
   */
  public String getParameter(String sKey)
  {
    return (String)m_htParameters.get(sKey.toLowerCase());
  }

  public void setParameter(String sKey, String sParam)
  {
    m_htParameters.put(sKey.toLowerCase(), sParam);
  }
  
  /**
   * Get a parameter value
   * @param sKey
   * @return null if parameter not found
   */
  public Object getObject(String sKey)
  {
    return m_htObjects.get(sKey.toLowerCase());
  }

  public void setObject(String sKey, Object obj)
  {
    m_htObjects.put(sKey.toLowerCase(), obj);
  }


}