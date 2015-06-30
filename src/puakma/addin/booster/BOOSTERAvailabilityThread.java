/***************************************************************************
The contents of this file are subject to the Puakma Public License Version 1.0 
 (the "License"); you may not use this file except in compliance with the 
 License. A copy of the License is available at http://www.puakma.net/

The Original Code is BOOSTERAvailabilityThread. 
The Initial Developer of the Original Code is Brendon Upson. email: bupson@wnc.net.au 
Portions created by Brendon Upson are Copyright (C)2002. All Rights Reserved.

webWise Network Consultants Pty Ltd, Australia, http://www.wnc.net.au

Contributor(s) and Changelog:
-
-
***************************************************************************/

package puakma.addin.booster;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Vector;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.TrustManager;

import puakma.error.ErrorDetect;
import puakma.error.pmaLog;
import puakma.security.SSLSocketFactoryEx;
import puakma.system.SystemContext;
import puakma.system.pmaSystem;

/**
 * Checks the host lists to see what is available.
 * @author Brendon Upson
 * @date 9 November 2003
 */
public class BOOSTERAvailabilityThread extends Thread implements ErrorDetect
{
    private int m_iPollIntervalSec=30;
    BOOSTER m_pParent;
    //private String m_sGetPath = "/";
    //private boolean m_bPartialAvailability=true;
    SystemContext m_pSystem;
    //private String m_sPollMethod;
    private Hashtable m_htHostConfig=null;
    
    /** Creates a new instance of BOOSTERAvailabilityThread */
    public BOOSTERAvailabilityThread(Hashtable ht, int iPollInterval, BOOSTER pParent)
    {
        m_iPollIntervalSec = iPollInterval;
        m_htHostConfig=ht;
        m_pParent = pParent;
        m_pSystem = m_pParent.getSystemContext();        
    }
    
    /**
     *
     */
    public void run()
    {
        //wait 2 secs to give the server time to start
        try{Thread.sleep(2000);}catch(Exception e){}
        
        //set all servers to available on startup
        Enumeration en = m_htHostConfig.keys();
        while(en.hasMoreElements())
        {
            String sDomain = (String)en.nextElement();
            m_pParent.setServiceAvailability(sDomain, true);            
        }
        
        while(m_pParent.isRunning())
        {              
            m_pSystem.doDebug(pmaLog.DEBUGLEVEL_STANDARD, "Checking server availability", this);
            if(!m_pParent.isLicensed())
                m_pSystem.doInformation("-- UNLICENSED - FOR NON-COMMERCIAL USE ONLY --", this);
            en = m_htHostConfig.keys();
            while(en.hasMoreElements())
            {
                String sDomain = (String)en.nextElement();
                setAvailability(sDomain);            
            }
            try{Thread.sleep(m_iPollIntervalSec*1000);}catch(Exception r){}
        }
    }
    
    
    /**
     *
     */
    private void setAvailability(String sDomain)
    {
        String sServers[] = m_pParent.getAllServerNodes(sDomain);
        String sPollPath = m_pParent.getPollPath(sDomain);
        String sPollMethod = m_pParent.getPollMethod(sDomain);
        Vector vAvailable = new Vector();
        for(int i=0; i<sServers.length; i++)
        {
            String sServer = sServers[i];
            if(isServerUp(sServer, sPollPath, sPollMethod)) vAvailable.add(sServer);
        }
        if(sServers.length!=vAvailable.size()) 
        {
            m_pSystem.doError("BOOSTER.UnavailableMessage", new String[]{vAvailable.size()+"", ""+sServers.length, sDomain}, this);            
            m_pParent.setServiceAvailability(sDomain, true);
        }
        else
        {
            if(m_pParent.getServiceAvailability(sDomain))
            {
                m_pSystem.doInformation("BOOSTER.AvailableMessage", new String[]{sServers.length+"", sDomain}, this);
                m_pParent.setServiceAvailability(sDomain, false);
                
            }
        }
        m_pParent.setAvailableHostVector(sDomain, vAvailable);
    }
    
    /**
     * sServer = "someserver.com:80"     
     */
    private boolean isServerUp(String sServer, String sPollPath, String sMethod)
    {
        String sPort="";
        String sHost="";
        int iPort=80;
      
        if(sServer==null || sServer.length()==0) return false;
        
        boolean bIsSSL=false;
        sHost = sServer;
        int iPos = sServer.indexOf(':');
        if(iPos>0) 
        {
            sPort = sServer.substring(iPos+1, sServer.length());
            sHost = sServer.substring(0, iPos);
            int iSSLPos = sPort.toUpperCase().indexOf("SSL");
            if(iSSLPos>0)
            {
                sPort = sPort.substring(0, iSSLPos);
                bIsSSL = true;
                iPort = 443; //default ssl port
            }
            
            
            try{iPort = Integer.parseInt(sPort);}catch(Exception y){}
        }
        m_pSystem.doDebug(pmaLog.DEBUGLEVEL_DETAILED, "Polling host:["+sHost+"] port:" + iPort, this);
        
        int iReturnCode = sendRequest(sHost, iPort, bIsSSL, sPollPath, sMethod);
        
        if(iReturnCode>0 && iReturnCode<500) 
            return true;
        else
        {
            if(m_pParent.isDebug())
                m_pSystem.doDebug(pmaLog.DEBUGLEVEL_NONE, sHost+" on port " + iPort + " is not responding. Return code:"+iReturnCode, this);
        }
        return false;
    }
    
    /**
     *
     */
    private int sendRequest(String sHost, int iPort, boolean bIsSSL, String sPollPath, String sMethod)
    {
        int iReturnCode=-1;
        String sPort="";
        if(iPort!=80 || (iPort!=443&&bIsSSL) ) sPort=":"+iPort;
        String sPrefix="http://";
        if(bIsSSL) sPrefix="https://";
        String sURL = sPrefix+sHost+sPort+sPollPath;
        String sReturnMessage = "";
        
        try
        {
            if(m_pParent.isDebug())
                        m_pSystem.doDebug(pmaLog.DEBUGLEVEL_NONE, "Checking: "+sURL, this);
            URL url = new URL(sURL); 
            if(bIsSSL)
            {                	
                
            	TrustManager tmArray[] = new TrustManager[]{new puakma.util.RelaxedTrustManager()};
                SSLSocketFactoryEx factory = new SSLSocketFactoryEx(null, tmArray, null);
                
        		HostnameVerifier hnv=new HostnameVerifier() 
                {
                    public boolean verify(String hostname, javax.net.ssl.SSLSession sess)
                    {
                        //System.out.println("verify called");
                        return true;
                    }                                                
                };
                
        		
                HttpsURLConnection shurl = (HttpsURLConnection)url.openConnection(); 
                shurl.setHostnameVerifier(hnv);
                
                shurl.setSSLSocketFactory(factory);
                shurl.setRequestMethod(sMethod);
                shurl.setRequestProperty("User-Agent", "Puakma_BOOSTER_Poller/"+m_pParent.getVersionNumber());
                shurl.setRequestProperty( "Session-Timeout", "1" ); //1minute
                shurl.connect();
                iReturnCode = shurl.getResponseCode();  
                sReturnMessage = shurl.getResponseMessage();
            }
            else
            {
                HttpURLConnection hurl = (HttpURLConnection)url.openConnection();
                hurl.setRequestMethod(sMethod);
                hurl.setRequestProperty("User-Agent", "Puakma_BOOSTER_Poller/"+m_pParent.getVersionNumber());
                hurl.setRequestProperty( "Session-Timeout", "1" ); //1minute
                hurl.connect();
                iReturnCode = hurl.getResponseCode();
                sReturnMessage = hurl.getResponseMessage();
            }                                    
        }
        catch(Exception e)
        {
            //?? log message?            
            e.printStackTrace();
            //if(bIsSSL) return 200; //fake it!!
        	m_pSystem.doDebug(pmaLog.DEBUGLEVEL_DETAILED, "Poll failed: "+e.toString(), this);
        }
        //m_pSystem.doDebug(pmaLog.DEBUGLEVEL_DETAILED, sURL+" reply="+iReturnCode, this);
        if(m_pParent.isDebug())
            m_pSystem.doDebug(pmaLog.DEBUGLEVEL_NONE, sURL+" replied: \""+iReturnCode + " " + sReturnMessage + "\"", this);
        
        return iReturnCode;
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
