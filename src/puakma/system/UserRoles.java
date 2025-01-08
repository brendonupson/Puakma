/** ***************************************************************
UserRoles.java
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

import java.util.*;

/**
 * <p>Title: </p>
 * <p>Description: caches the roles a user has in a given application. These
 * UserRoles objects will be stored in a Hashtable on the session for retrieval.
 * Keys are case insensitive, stored in lowercase</p>
 * <p>Copyright: Copyright (c) 2002</p>
 * <p>Company: </p>
 * @author Brendon Upson
 * @version 1.0
 */

public class UserRoles
{
  private String m_sAppPath;
  private Hashtable<String, String> m_htRoles = new Hashtable<String, String>();

  public UserRoles(String szAppPath)
  {
    m_sAppPath = szAppPath.toLowerCase();
  }


  /**
   * @param szRoleName
   * @return true if the object contains the given role
   */
  public boolean hasRole(String szRoleName)
  {	  
    if(szRoleName!=null && m_htRoles.containsKey(szRoleName.toLowerCase()) ) return true;
    return false;
  }


  /**
  * Adds a role
  */
  public void addRole(String szRoleName)
  {
    String szRoleLower = szRoleName.toLowerCase();
    if( m_htRoles.containsKey(szRoleLower) ) return;

    m_htRoles.put(szRoleLower, szRoleName);
  }

  /**
  * Removes a role
  *
  */
  public void removeRole(String szRoleName)
  {
    m_htRoles.remove(szRoleName.toLowerCase());
  }

  /**
  * Gets all the roles in this object
  */
  public Vector<String> getRoles()
  {
    Vector<String> vRoles = new Vector<String>();
    Enumeration<String> en = m_htRoles.elements();
    while(en.hasMoreElements())
    {
      String s = (String)en.nextElement();
      vRoles.add(s);
    }
    return vRoles;
  }

  /**
   * Gets this object's XML representation
   * @return
   */
  public StringBuilder getXML()
  {
    StringBuilder sb = new StringBuilder(512);
    Enumeration<String> en = m_htRoles.elements();
    sb.append("<approle app=\"" + m_sAppPath +  "\">\r\n");
    while(en.hasMoreElements())
    {
      String sRole = (String)en.nextElement();
      sb.append("\t<role>" + puakma.util.Util.base64Encode(sRole.getBytes()) + "</role>\r\n");
    }
    sb.append("</approle>\r\n");
    return sb;
  }
  
  /**
   * A simple way to show the contents of this object. Any roles listed are the roles
   * that the session has. Roles that the session does not have will not be listed.
   * @return
   */
  public String toString()
  {
    StringBuilder sb = new StringBuilder(150);
    Enumeration<String> en = m_htRoles.elements();
    sb.append(m_sAppPath + "\r\n");
    while(en.hasMoreElements())
    {
      String sRole = (String)en.nextElement();
      sb.append("\trole: " + puakma.util.Util.base64Encode(sRole.getBytes()) + "\r\n");
    }
    
    return sb.toString();
  }

  /**
   *
   * @return a string representing this object
   */
  public String getKey()
  {
    return m_sAppPath;
  }
}