/** ***************************************************************
LoginResult.java
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
package puakma.security;

import java.util.*;

/**
* This object is returned from an Authenticator object when it attempts
* to authenticate with the given system.
* Returns the full username of the user (x500/x400)
*/
public final class LoginResult
{
  //Map the possible return codes
  public final static int LOGIN_RESULT_SUCCESS=0;
  public final static int LOGIN_RESULT_FAIL=1;
  public final static int LOGIN_RESULT_ACCOUNT_DISABLED=2;
  public final static int LOGIN_RESULT_ACCOUNT_EXPIRED=3;
  public final static int LOGIN_RESULT_TOO_MANY_MATCHES=5;
  public final static int LOGIN_RESULT_INVALID_USER=5;

  //these variable are filled by the authenticator
  public String FirstName="";
  public String LastName="";
  public String UserName="";
  public String LoginMessage="";
  public Vector vAliases = new Vector();
  public int ReturnCode=LOGIN_RESULT_INVALID_USER;

  private Hashtable m_hData=new Hashtable();

  //just a dud so we can create a new one
  public LoginResult(){}

  /**
  * Determine if the current return code is final. If final, we don't need to
  * continue to look through more authenticators trying to log in
  */
  public boolean isFinalReturnCode()
  {
    if(ReturnCode==LOGIN_RESULT_INVALID_USER)
      return false;
    else
      return true;
  }

  /**
   * Put a value in the table
   * @param szName
   */
  public void put(String szName, String szValue)
  {
    m_hData.put(szName, szValue);
  }

  /**
   * get an attribute's value
   * @param szName
   */
  public String get(String szName)
  {
    return (String)m_hData.get(szName);
  }
  
  public String toString()
  {
      StringBuilder sb = new StringBuilder(128);      
      sb.append("ReturnCode: ["+ this.ReturnCode + "]\r\n");
      sb.append("FirstName: ["+ this.FirstName + "]\r\n");
      sb.append("LastName: ["+ this.LastName + "]\r\n");
      sb.append("UserName: ["+ this.UserName + "]\r\n");
      return sb.toString();
  }

}
