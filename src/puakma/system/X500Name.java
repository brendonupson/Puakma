/** ***************************************************************
X500Name.java
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

import java.util.ArrayList;

/**
* This class creates X500 style names, ie CN=Brendon Upson/OU=Dev/O=webWise
* Supports many OUs

    X500Name cn = new X500Name("John Smith");
    X500Name full = new X500Name("John Smith/Marketing/Sydney/ACME");
    X500Name canon = new X500Name("CN=John Smith/OU=Marketing/OU=Sydney/O=ACME");
    X500Name country = new X500Name("CN=John Smith/OU=Marketing/OU=Sydney/O=ACME/C=AU");

    String sz = full.getAbbreviatedName();
    sz = full.getCanonicalName();

    boolean b = full.equals(canon);
    b = full.equals(canon, true); //case sensitive name compare

String  X.500 AttributeType
------------------------------
CN      commonName
L       localityName
ST      stateOrProvinceName
O       organizationName
OU      organizationalUnitName
C       countryName
STREET  streetAddress
DC      domainComponent
UID     userid

*/
public class X500Name
{    
    
    private class X500NamePart{
        public String Key; //eg "CN"
        public ArrayList Values; //eg "Brendon Upson", "Users", ...
    };//inner class
    
    
  private static String VALID_PARTS[]={"CN","OU","O","DC","C", "L","ST","STREET","UID"};
  private String m_sSeperator="/";
  //private Hashtable m_htNameParts = new Hashtable();
  //private Vector m_vKeyOrder = new Vector(); //to store the order of the parts
  private ArrayList m_NameParts = new ArrayList();

  
  public X500Name(String szNameIn, String szSeperator)
  {
    setSeperator(szSeperator);
    setName(szNameIn);
  }

  public X500Name(String szNameIn)
  {
    setName(szNameIn);
  }

  /**
  * put an item in the hashtable
  * Only allow valid items to be added
  */
  public void put(String sItem, String sText)
  {
      if(sItem==null) return;
      sItem = sItem.toUpperCase();
      if(!isValidItem(sItem)) return;
      
      
        X500NamePart nmPart = getX500NamePart(sItem);//(ArrayList)m_htNameParts.get(sItem);
        if(nmPart==null) 
        {
            nmPart = new X500NamePart();
            nmPart.Values = new ArrayList();
            nmPart.Key = sItem;
        }
        nmPart.Values.add(sText);
        putX500NamePart(nmPart);
  }
  
  /**
   *
   */
  private synchronized void putX500NamePart(X500NamePart nmPart)
  {
      if(nmPart==null) return;
      X500NamePart nmPartFound = getX500NamePart(nmPart.Key);
      if(nmPartFound==null)
      {
          //add a new one
          m_NameParts.add(nmPart);
      }
      else
      {
          //update the existing one
          nmPartFound.Values = nmPart.Values;
      }
      
  }
  
  /**
   * Get all the values associated with eg "OU". Note that ordering is important.
   * @return null if the item is not found
   */
  private X500NamePart getX500NamePart(String sItem)
  {
      if(sItem==null) return null;
      for(int i=0; i<m_NameParts.size(); i++)
      {
          X500NamePart nmPart = (X500NamePart)m_NameParts.get(i);
          if(nmPart.Key.equals(sItem)) return nmPart;
      }
      
      return null;
  }

  /**
  * @return null if the object is not found
  * Only allow valid items to be retrieved
  * Pass bRawValue = true to not prefix the attribute (eg omit "CN=")
  */
  public String get(String sItem, boolean bRawValue)
  {
    if(sItem==null) return null;
    int iGetPos = 0;
    try{
        iGetPos = Integer.parseInt(String.valueOf(sItem.charAt(sItem.length()-1)));
        sItem = sItem.substring(0, sItem.length()-1);
    }catch(Exception e){}
    
    sItem = sItem.toUpperCase();
    if(!isValidItem(sItem)) return null;
    X500NamePart nmPart = getX500NamePart(sItem);
    if(nmPart==null) return null;
    
    ArrayList arr = nmPart.Values;
    if(arr==null || arr.size()==0) return null;
    StringBuilder sbReturn = new StringBuilder(50);
    for(int i=0; i<arr.size(); i++)
    {
        String sVal = (String)arr.get(i);
        if(iGetPos>0)
        {
            if(arr.size()<iGetPos-1) return null;
            sVal = (String)arr.get(iGetPos-1);
        }
        if(sbReturn.length()>0) sbReturn.append(m_sSeperator);
        if(!bRawValue) sbReturn.append(sItem + '=');
        sbReturn.append(sVal);
        if(iGetPos>0) break;
    }
    return sbReturn.toString();
  }

  /**
  * @return true if the item (ie "O") is allowed see VALID_PARTS array
  */
  private boolean isValidItem(String sItem)
  {
    int i=0;
    sItem = sItem.toUpperCase();
    for(i=0; i<VALID_PARTS.length; i++)
    {
      if(sItem.equalsIgnoreCase(VALID_PARTS[i])) return true;      
    }
    return false;
  }

  /** Main for testing purposes
  */
/*  public static void main(String args[])
  {
    X500Name cn = new X500Name("John Smith");
    X500Name full = new X500Name("John Smith/Marketing/SYDNEY/ACME");
    X500Name canon = new X500Name("CN=John Smith/OU=Marketing/OU=Sydney/O=ACME");
    X500Name country = new X500Name("CN=John Smith.OU=Marketing.OU=Sydney.O=ACME.C=AU", ".");

    String sz = full.getAbbreviatedName();
    sz = full.getCanonicalName();
    sz = canon.getAbbreviatedName();
    sz = canon.getCanonicalName();
    sz = country.getAbbreviatedName();
    sz = country.getCanonicalName();

    boolean b = full.equals(canon, true);

  }
*/

  public boolean equals(String sUserName)
  {
      if(sUserName==null) return false;
      if(sUserName.equals("*")) return true;
      
      X500Name nmCompare = new X500Name(sUserName);
      return equals(nmCompare);
  }

  public boolean equals(X500Name nmCompare)
  {
    return equals(nmCompare, false);
  }

  /**
  * Compares one name object against another, does a case sensitive compare if
  * required
  */
  public boolean equals(X500Name nmCompare, boolean bCompareCase)
  {
    String szName1 = nmCompare.getCanonicalName();
    String szName2 = getCanonicalName();

    if(!bCompareCase)
    {
      szName1 = szName1.toLowerCase();
      szName2 = szName2.toLowerCase();
    }

    if(szName1.equals(szName2)) return true;

    return false;
  }

  /**
  * Compares one name object against another, does a case sensitive compare if
  * required
  * John Smith/Marketing/Sydney/ACME matches with * /ACME and * /Sydney/ACME etc
  */
  public boolean matches(X500Name nmCompare, boolean bCompareCase)
  {
    String sAbbCompare = nmCompare.getAbbreviatedName();
    String sAbbName = getAbbreviatedName();
    boolean bMatchStart = true;

    if(sAbbCompare.startsWith("*"))
    {
      sAbbCompare = sAbbCompare.substring(1, sAbbCompare.length());
      bMatchStart = false;
    }

    if(sAbbCompare.endsWith("*"))
    {
      sAbbCompare = sAbbCompare.substring(0, sAbbCompare.length()-1);
      bMatchStart = true;
    }


    if(!bCompareCase) //case insensitive compare
    {
      sAbbCompare = sAbbCompare.toLowerCase();
      sAbbName = sAbbName.toLowerCase();
    }



    if(bMatchStart)
    {
      if(sAbbName.startsWith(sAbbCompare)) return true;
    }
    else
    {
      if(sAbbName.endsWith(sAbbCompare)) return true;
    }

    return false;
  }

  public boolean matches(X500Name nmCompare)
  {
    return matches(nmCompare, false);
  }



  /*
  *
  */
  private synchronized void setName(String szName)
  {
    if(szName==null) return;
    int iPos = szName.indexOf(m_sSeperator);
    if(iPos<0)// check in case the name is "CN=John Smith" or "John Smith"
    {      
      if(szName.toLowerCase().startsWith("cn=")) szName = szName.substring(3, szName.length());
      put("CN", szName);
      return;
    }
    else //is a canonical name with multiple parts
    {
      boolean bCanonical=false;
      //CN
      String szCN = szName.substring(0, iPos);
      //szName = szName.substring(iPos+m_sSeperator.length(), szName.length());
      iPos=szCN.indexOf('=');
      if(iPos>0)
      {
        bCanonical=true;
        //szCN = szCN.substring(iPos+1, szCN.length());
      }
      //put("CN", szCN);
      if(bCanonical)
        setCanonicalName(szName);
      else
        setAbbreviatedName(szName);
    }
  }

  

  /**
  * John Smith/Dev/ACME
  * szName = John Smith/Dev/ACME
  * Assumes there is no Country component, last item is treated as ORG (O)
  */
  private void setAbbreviatedName(String sName)
  {
    if(sName==null || sName.trim().length()==0) return;  
    
    String sParts[] = sName.split(m_sSeperator);
    put("CN", sParts[0]);
    if(sParts.length>1)
    {        
        for(int i=1; i<sParts.length-1; i++)
        {
            put("OU", sParts[i]);
        }
        put("O", sParts[sParts.length-1]);
    }
    //note: order of puts is important!
    
  }


  /**
  * CN=John Smith/OU=Dev/O=ACME
  * szName = CN=John Smith/OU=Dev/O=ACME
  * Assumes all components are prefixed with their type
  */
  private void setCanonicalName(String sName)
  {    
    if(sName==null || sName.trim().length()==0) return;
    
    String sParts[] = sName.split(m_sSeperator);
    for(int i=0; i<sParts.length; i++)
    {
        String sPart = sParts[i];
        int iPos = sPart.indexOf('=');
        String sItem="CN";
        if(iPos>=0) 
        {
            sItem = sPart.substring(0, iPos);
            if(isValidItem(sItem))
            {
                this.put(sItem, sPart.substring(iPos+1, sPart.length()) );
            }
        }
            
    }
    
  }




  /**
  * Sets the seperator to use between the names
  */
  public synchronized void setSeperator(String szNewSeperator)
  {
    m_sSeperator = szNewSeperator;
  }


  public String getCanonicalName()
  {
    /*String szName = getFullName(true);
    if(szName.indexOf(m_sSeperator)<0) return getCommonName();
    return szName;
     */
    return getFullName(true);  
  }

  public String toString()
  {
    return getFullName(true);
  }

  public String getAbbreviatedName()
  {
    return getFullName(false);
  }


  /**
  * Called internally to put the name back together from the parts
  */
  private String getFullName(boolean bCanonical)
  {
    StringBuilder sbName=new StringBuilder(300);

    for(int i=0; i<m_NameParts.size(); i++)    
    {
        X500NamePart nmPart = (X500NamePart)m_NameParts.get(i);        
        
        for(int k=0; k<nmPart.Values.size(); k++)
        {
            if(sbName.length()>0 || i>0) sbName.append(m_sSeperator); //i>0 to cater for CN=""
            if(bCanonical) sbName.append(nmPart.Key+'=');                
            sbName.append((String)nmPart.Values.get(k));
        }        
    }
   
    return sbName.toString();
  }


  /**
  * @return the CN component of the name, "" if not found
  */
  public String getCommonName()
  {
    String sCommon = get("CN1", true);
    if(sCommon==null) return "";

    return sCommon;
  }

  /**
  * This is not 'exact' chops off the first word
  */
  public String getFirstName()
  {
    String szFirstName = getCommonName();
    int iPos = szFirstName.indexOf(' ');
    if(iPos>=0) szFirstName = szFirstName.substring(0, iPos);
    return szFirstName;
  }

  /**
  * This is not 'exact' chops off the first word
  */
  public String getLastName()
  {
    String szLastName = getCommonName();
    int iPos = szLastName.indexOf(' ');
    if(iPos>=0) szLastName = szLastName.substring(iPos+1, szLastName.length());
    return szLastName;
  }


}
