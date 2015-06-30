/***************************************************************************
The contents of this file are subject to the Puakma Public License Version 1.0 
 (the "License"); you may not use this file except in compliance with the 
 License. A copy of the License is available at http://www.puakma.net/

The Original Code is BOOSTERHostConfig. 
The Initial Developer of the Original Code is Brendon Upson. email: bupson@wnc.net.au 
Portions created by Brendon Upson are Copyright (C)2002. All Rights Reserved.

webWise Network Consultants Pty Ltd, Australia, http://www.wnc.net.au

Contributor(s) and Changelog:
-
-
***************************************************************************/

package puakma.addin.booster;

import java.util.*;

public class BOOSTERHostConfig 
{
    public String m_sDomain; //www.somehost.com
    public Vector m_vAvailableServers = new Vector();
    public String m_sServers[]=null;
    public String m_sPollPath; 
    public String m_sBackupDomain;
    public String m_sPollMethod;
    public boolean m_bForceSSL=false;
    public boolean m_bForceClientToSSL=false;
    public int m_iLoadSplit[]=null; //eg 60,30,10
    private int m_iCurrentHost=0;
    public boolean m_bPartialAvailability=true;
    
    /** Creates a new instance of BOOSTERHostConfig */
    public BOOSTERHostConfig() 
    {
    }
    
    /**
     *
     */
    public synchronized String getNextAvailableHost()
    {
      String sNextHost=null;
      try
      {
        sNextHost = (String)m_vAvailableServers.get(m_iCurrentHost);
        m_iCurrentHost++;
      }
      catch(Exception e)
      {
          m_iCurrentHost = 0;
      }      
      if(m_iCurrentHost>m_vAvailableServers.size()-1) m_iCurrentHost=0;
      
      return sNextHost;
    }
    
    public String getBackupDomain()
    {
        //System.out.println("Trying backup domain: " + m_sBackupDomain);
        return m_sBackupDomain;
    }
    
    public String showStatus()
    {
        String CRLF="\r\n";
        StringBuilder sb = new StringBuilder(128);
        
        sb.append("  "+m_sDomain+CRLF);
        sb.append("   Poll: "+m_sPollMethod + " " + m_sPollPath + CRLF);
        for(int i=0; i<m_sServers.length; i++)
        {
            sb.append("   Node: "+m_sServers[i]+CRLF);
        }
//        sb.append("   "+CRLF);
        
        return sb.toString();
    }
    
}
