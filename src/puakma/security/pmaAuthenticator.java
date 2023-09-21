/** ***************************************************************
pmaAuthenticator.java
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

import puakma.error.ErrorDetect;
import puakma.system.SessionContext;
import puakma.system.SystemContext;
import puakma.system.pmaSystem;

/**
* This class is used to provide an alternate authentication mechanism. this will
* allow users to authenticate against a text file, LDAP server, Netware, NT, etc...
* Possibly a number of custom written authenticators will be loaded into a vector
* in the system class. When an application makes a call to pmaSystem.loginSession()
* the system will cycle through the authenticators trying each one in turn.
*/
public class pmaAuthenticator implements ErrorDetect
{

  protected SystemContext SysCtx; //Golding use a custom authenticator, so if you change this it will break
  public boolean m_bShowLoginErrors = true;

  public pmaAuthenticator() {}

  public final void init(SystemContext paramSysCtx)
  {
    SysCtx = paramSysCtx;
    init();
  }

  /**
   * Any module specific initialisation goes here
   */
  public void init()
  {
  }

  /**
  * This function does all the work. Over-ride this to provide the custom authentication.
  * This function should return a LoginResult.LOGIN_RESULT_INVALID_USER if the user cannot be found.
  * This will cause the system object to look to the next authenticator in the list.
  * @deprecated
  */
  public LoginResult loginUser(String sUserName, String sPassword, String sAddress, String sUserAgent)
  {
    LoginResult loginResult = new LoginResult();

    return loginResult;
  }
  
  /**
   * gets the login data from the authenticator for the canonical name passed. This
   * is used when a token (eg ltpa) is used to auto login
   * @deprecated
   */
  public LoginResult populateSession(String sCanonicalName)
  {
      return null;
  }

  /**
   * Determines if the user is in the given group
   * @param sessCtx
   * @param sGroup
   * @return
   * @deprecated
   */
  public boolean isUserInGroup(SessionContext sessCtx, String sGroup)
  {
    return false;
  }


  /**
  * This function does all the work. Over-ride this to provide the custom authentication.
  * This function should return a LoginResult.LOGIN_RESULT_INVALID_USER if the user cannot be found.
  * This will cause the system object to look to the next authenticator in the list.  
  */
  public LoginResult loginUser(String sUserName, String sPassword, String sAddress, String sUserAgent, String sAppURI)
  {
    LoginResult loginResult = new LoginResult();

    return loginResult;
  }
  
  /**
   * gets the login data from the authenticator for the canonical name passed. This
   * is used when a token (eg ltpa) is used to auto login
   */
  public LoginResult populateSession(String sCanonicalName, String sAppURI)
  {
      return null;
  }

  /**
   * Determines if the user is in the given group
   * @param sessCtx
   * @param sGroup
   * @return  true if the user is in the group
   */
  public boolean isUserInGroup(SessionContext sessCtx, String sGroup, String sAppURI)
  {
    return false;
  }



  public String getErrorSource()
  {
    return getClass().getName();
  }

  public String getErrorUser()
  {
    return pmaSystem.SYSTEM_ACCOUNT;
  }
}
