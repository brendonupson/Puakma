/** ***************************************************************
MailAddress.java
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

/*
 * MailAddress.java
 *
 * Created on 16 September 2003, 21:12
 */

package puakma.util;

import java.util.ArrayList;

/**
 * For parsing internet email addresses ""Brendon Upson" bupson@wnc.net.au"
 * @author  bupson
 */
public class MailAddress 
{
    private String m_sOriginalAddress="";
    private String m_sHost="";
    private String m_sUserName="";
    private String m_sUserDescription="";
    
    /** Creates a new instance of MailAddress */
    public MailAddress(String sAddress) 
    {
        setAddress(sAddress);        
    }
    
    
    public static void main(String[] args) 
    {
        /*
        String m1 = "\"Brendon Upson\" bupson@wnc.net.au";
        String m2 = "bupson@wnc.net.au";
        String m3 = "<bupson@wnc.net.au>";
        String m4 = "\"Brendon Upson\" <bupson@wnc.net.au>";
        String m5 = "Jake Howlett <jake@codestore.net>";
        String m6 = "\"<Brendon Upson>\" <bupson@wnc.net.au>";
         */
        
        //String sAll = "\"bupson@somewhere\" bupson@wnc.net.au, bupson@wnc.net.au,<bupson@wnc.net.au>,\"Brendon Upson\" <bupson@wnc.net.au>, Jake Howl <jakeh@xyz.net>, \"<Brendon Upson>\" <bupson@wnc.net.au>";
        
        String sAll = "\"bupson@somewhere\" bupson@wnc.net.au, \"somethin@here\" test,,bu@somwhere.net.au,";
        MailAddress maReturn[] = parseMailAddresses(sAll, null);
        for(int i=0; i<maReturn.length; i++)
        {            
            System.out.println("Addr: ["+maReturn[i].getFullParsedEmailAddress()+"]");
            //System.out.println("SMTP: ["+maReturn[i].getSMTPEmailAddress()+"]");
        }
        /*
        MailAddress ma = new MailAddress(m6);
        System.out.println("User: ["+ma.getUserName()+"]");
        System.out.println("Host: ["+ma.getHost()+"]");
        System.out.println("Desc: ["+ma.getUserDescription()+"]");
        System.out.println("SMTP: ["+ma.getSMTPEmailAddress()+"]");
         */
        System.out.println("done.");
    }
     
    
    /**
     * Pass a single string containing multiple addresses and have this method 
     * parse it and return multiple valid MailAddress objects. Any bad addresses 
     * will be dropped.
     */
    public static MailAddress[] parseMailAddresses(String sAddresses, String sDelimiter)
    {        
        ArrayList arr = new ArrayList();
        if(sDelimiter==null) sDelimiter = ",";
        if(sAddresses==null || sAddresses.length()==0) return null;
        
        boolean bAddressesRemain=true;
        while(bAddressesRemain)
        {
            String sFirstPart = "";
            int iPos = 0;
            boolean bDroppingChunks = true;
            while(bDroppingChunks)
            {
                if(sAddresses.length()>0 && sAddresses.charAt(0)=='\"') //if we start with a ", then skip to the next one
                {
                    iPos = sAddresses.indexOf('\"');
                    if(iPos>=0) 
                    {
                        sFirstPart = sAddresses.substring(0, iPos);
                        sAddresses = sAddresses.substring(iPos, sAddresses.length());
                    }
                    bDroppingChunks = false;
                }
                else //find the next delimiter, check if the result contains a @
                {
                    iPos = sAddresses.indexOf(sDelimiter);
                    if(iPos>=0) 
                    {
                        String sNextChunk = sAddresses.substring(0, iPos);
                        int iAtPos = sNextChunk.indexOf('@');
                        if(iAtPos<1) //no @ so drop the item....
                        {
                            sAddresses = sAddresses.substring(iPos+sDelimiter.length(), sAddresses.length()).trim();
                            if(sAddresses.length()==0) bDroppingChunks = false;
                        }
                        else
                            bDroppingChunks = false;
                    }
                    else
                        bDroppingChunks = false;
                }
            }
            
            iPos = sAddresses.indexOf('@');
            if(iPos>0)
            {
                sFirstPart += sAddresses.substring(0, iPos);
                sAddresses = sAddresses.substring(iPos, sAddresses.length());
                iPos = sAddresses.indexOf(sDelimiter);
                String sLastPart = null;
                if(iPos>0)
                {
                    sLastPart = sAddresses.substring(0, iPos);
                    sAddresses = sAddresses.substring(iPos+sDelimiter.length(), sAddresses.length()).trim(); //skip over delimiter
                }
                else //no more in string
                {
                    sLastPart = sAddresses;
                    sAddresses = "";
                }
                String sAddress = sFirstPart + sLastPart;                
                MailAddress ma = new MailAddress(sAddress.trim());
                if(ma.isValidAddressSyntax()) arr.add(ma);                
            }
            else
                bAddressesRemain = false;
        }
            
        MailAddress maReturn[]=new MailAddress[arr.size()];
        for(int i=0; i<maReturn.length; i++)
        {
            maReturn[i] = (MailAddress)arr.get(i);
        }
        return maReturn;
    }
    
    
    /**
     * Checks if the actual address contains an @ after position 0 and that the 
     * hostname has some length to it. This DOES NOT verify that the account 
     * actually exists!
     */
    public boolean isValidAddressSyntax()
    {
        if(getSMTPEmailAddress().indexOf('@')>0 && m_sHost!=null && m_sHost.length()>1) return true;
        return false;
    }
    
    
    /**
     *
     */
    public void setAddress(String sAddress)
    {
        m_sOriginalAddress="";
        m_sHost="";
        m_sUserName="";
        m_sUserDescription="";
        
        sAddress = Util.trimChar(sAddress, new char[]{' ', '\r', '\n'});
        
        if(sAddress==null || sAddress.length()==0) return;
        m_sOriginalAddress = sAddress;
        m_sUserName = sAddress;
        int iPos = sAddress.lastIndexOf(' ');        
        if(iPos>0) //strip email bits before "user@y.com"
        {
            m_sUserDescription = sAddress.substring(0, iPos);
            m_sUserName = sAddress.substring(iPos+1, sAddress.length());
        }
        
        int iAtPos = m_sUserName.indexOf('@');
        if(iAtPos>0)
        {
            m_sHost = m_sUserName.substring(iAtPos+1, m_sUserName.length());
            m_sUserName = m_sUserName.substring(0, iAtPos);                
        }
         
        m_sHost = Util.trimChars(m_sHost, ">");
        m_sUserName = Util.trimChars(m_sUserName, "<");
        m_sUserDescription = Util.trimChars(m_sUserDescription, "\"");
    }
    
    
    /**
     * Returns the user's mail server
     */
    public String getHost()
    {
        return m_sHost;
    }
    
    /**
     * Returns the user's familiar name.
     */
    public String getUserDescription()
    {
        return m_sUserDescription;
    }
    
    
    /**
     * Returns the user's account name.
     */
    public String getUserName()
    {
        return m_sUserName;
    }
    
    
    /**
     * Returns the original details the object was constructed with
     */
    public String getFullOriginalAddress()
    {
        return this.m_sOriginalAddress;
    }
    
    /**
     * Returns the SMTP ready address "<user@domain.com>"
     */
    public String getSMTPEmailAddress()
    {
        return "<"+m_sUserName+"@"+m_sHost+">";
    }
    
    /**
     * Returns the basic address "user@domain.com"
     */
    public String getBasicEmailAddress()
    {
        return m_sUserName+"@"+m_sHost;
    }
    
    /**
     * Returns the standards compliant address ""smith, john" <user@domain.com>"
     */
    public String getFullParsedEmailAddress()
    {
        String sDesc="";
        if(m_sUserDescription.length()>0) sDesc = "\"" + m_sUserDescription + "\" ";
        return sDesc + "<"+m_sUserName+"@"+m_sHost+">";
    }
    
}
