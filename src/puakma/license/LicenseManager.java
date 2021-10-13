/***************************************************************************
The contents of this file are subject to the Puakma Public License Version 1.0 
 (the "License"); you may not use this file except in compliance with the 
 License. A copy of the License is available at http://www.puakma.net/

The Original Code is LicenseManager. 
The Initial Developer of the Original Code is Brendon Upson. email: bupson@wnc.net.au 
Portions created by Brendon Upson are Copyright (C)2002. All Rights Reserved.

webWise Network Consultants Pty Ltd, Australia, http://www.wnc.net.au

Contributor(s) and Changelog:
- Created on June 16, 2006, 9:03 PM
-
***************************************************************************/

package puakma.license;

import puakma.system.SystemContext;
import java.io.*;
import java.util.Properties;
import java.util.Hashtable;

/**
 * This object is used for managing WNC created license files. This code should not be opensource.
 * @author  bupson
 */
public class LicenseManager 
{    
    private static LicenseManager m_ref;
    private SystemContext m_pSystem;
    private boolean m_bLicensedVersion=false;
    private long m_lLicenseUsers=0; //the number of users we are licensed for
    private String m_sLicenseNumber="";
    
  private LicenseManager(SystemContext pSystem)
  {
    m_pSystem = pSystem;
    displayLicense();
  }
  
  /**
   *
   */
  public void displayLicense()
  {
      //if(true) return;
      m_bLicensedVersion = false;
      
      boolean bIsLicensed=false;
      boolean bFound = false;
      File fLicense = null;
      File fConfigDir = m_pSystem.getConfigDir();
      if(fConfigDir.exists())
      {
          File fFiles[] = fConfigDir.listFiles();
          for(int i=0; i<fFiles.length; i++)
          {
              fLicense = fFiles[i];
              String sFileName = fLicense.getName().toLowerCase();
              if(sFileName.endsWith(".lic")) 
              {
                  bFound = true;
                  break;
              }
          }
      }
      if(!bFound) fLicense = null;
      String sLicenseTo="";
      String sLicenseFor="";
      String sLicenseDate="";
      //String sLicenseNumber="";
      String sLicenseUsers="";
      //byte bufLicenseKey[]=null;
      
      if(fLicense!=null) //we have a Xxxxx.lic file
      {
        m_pSystem.doInformation("Found license file: "+fLicense.getName(), m_pSystem);
        try
        {          
          Properties propLic = new Properties();
          FileInputStream fs = new FileInputStream(fLicense.getAbsolutePath());
          try
          {
            byte bufRaw[] = new byte[(int)fLicense.length()];
            fs.read(bufRaw);
            ByteArrayInputStream bais = new ByteArrayInputStream(puakma.util.Util.ungzipBuffer(bufRaw));
            propLic.load(bais);
            /*
                LicensedTo=webWise Network Consultants Pty Ltd
                LicensedFor=Puakma Web Booster 3.x PT0023                
                LicenseNumber=ADF356547ABE
                LicenseKey=asgf23/asgfafgkasfgkasdfgak
             */
            sLicenseTo = propLic.getProperty("LicenseTo");
            sLicenseFor = propLic.getProperty("LicenseFor");            
            m_sLicenseNumber = propLic.getProperty("LicenseNumber");
            sLicenseUsers = propLic.getProperty("LicenseUsers");
            try{ m_lLicenseUsers = Long.parseLong(sLicenseUsers); }catch(Exception e){}
            if(sLicenseUsers==null) sLicenseUsers="";
            long lLicenseTime = Long.parseLong(m_sLicenseNumber, 16);
            java.util.Date dtLicense = new java.util.Date();
            dtLicense.setTime(lLicenseTime);
            sLicenseDate = puakma.util.Util.formatDate(dtLicense, "d.MMM.yyyy");
            String sKey = propLic.getProperty("LicenseKey");
            
            String sCheck = "";
            final String sSecretKey = "PuAkmA10M!l_42";
            try
            {
                String sConCat = sLicenseTo+sLicenseFor+m_sLicenseNumber+sLicenseUsers+sSecretKey;
                //strip the high order byte
                byte bufOriginal[] = puakma.util.Util.makeByteArray(sConCat.toCharArray(), sConCat.length());                
                byte bufKey[] = puakma.util.Util.hashBytes(bufOriginal);
                sCheck = puakma.util.Util.base64Encode(bufKey);
            }
            catch(Exception e){}
            
            
            //check key against data....
            if(sCheck.equals(sKey)) 
                bIsLicensed = true;
            else
                m_pSystem.doError("INVALID LICENSE FILE: " + fLicense.getAbsolutePath(), m_pSystem);
            
          } catch(IOException ioe)
          {
              m_pSystem.doError("Error loading license from: " + fLicense.getAbsolutePath(), m_pSystem);
          }
        }
        catch(Exception e){}
        
      }
      if(bIsLicensed)
      {
        m_pSystem.doInformation("Licensed to: " + sLicenseTo, m_pSystem);
        m_pSystem.doInformation("Licensed for: " + sLicenseFor + " ("+m_sLicenseNumber+") "+sLicenseDate, m_pSystem);
        if(m_lLicenseUsers==-1)
            m_pSystem.doInformation("Licensed for UNLIMITED Users", m_pSystem);
        if(m_lLicenseUsers>0)
            m_pSystem.doInformation("Licensed for " + m_lLicenseUsers + " Users", m_pSystem);
        m_bLicensedVersion = true;
      }
      else
      {             
          char c = (char)7;
          System.out.println(c);//bell char          
          
          String sNotLic1 =  "###### THIS SOFTWARE IS NOT LICENSED ######";
          String sNotLic2 =  "######### FOR NON-COMMERCIAL USE ONLY #########";
          String sNotLic3 =  "#########    http://www.puakma.net    #########";
          m_pSystem.doError(sNotLic1, m_pSystem);
          m_pSystem.doError(sNotLic2, m_pSystem);
          m_pSystem.doError(sNotLic3, m_pSystem);
          try{Thread.sleep(5000);}catch(Exception e){}
      }
      
  }
  
  public long getUserLicenseCount()
  {
      return m_lLicenseUsers;
  }
  
  public String getLicenseNumber()
  {
      return m_sLicenseNumber;
  }
  
  /**
   * Determines if the software holds a valid license key or not. In future we may want to 
   * pass product specific data and determine if a component is licensed.
   */
  public boolean isLicensed(Hashtable htProductData)
  {
    return m_bLicensedVersion;
  }

  /**
   * instantiate and return a reference to this object. Can only be
   * called internally.
   */
  public static synchronized LicenseManager getInstance(SystemContext pSystem)
  {
    if (m_ref == null)
        // it's ok, we can call this constructor
        m_ref = new LicenseManager(pSystem);		
    return m_ref;
  }

  /**
   * This object may not be cloned.
   */
  public Object clone() throws CloneNotSupportedException
  {
    throw new CloneNotSupportedException();     
  }
  
  
  
  
}//class
