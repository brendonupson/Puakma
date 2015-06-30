/** ***************************************************************
AntiHackWorker.java
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


package puakma.util;

import puakma.system.*;
import puakma.error.*;
import java.io.*;
import java.net.*;

/**
 *
 * @author  bupson
 */
public class AntiHackWorker implements pmaThreadInterface, ErrorDetect
{
    private int m_iPort=-1;
    private boolean m_bAggressive=false;
    private Socket m_Sock;
    private SystemContext m_pSystem;
    
    /** Creates a new instance of AntiHackWorker */
    public AntiHackWorker(SystemContext paramSystem, Socket s) 
    {
        m_Sock = s;
        m_pSystem = paramSystem;
        String sTemp = m_pSystem.getSystemProperty("AggressiveAntiHacking");
        if(sTemp!=null && sTemp.equals("1")) m_bAggressive = true;
    }
    
    public void destroy() 
    {
        
        try{m_Sock.close();}catch(Exception r){}
    }
    
    public String getThreadDetail() 
    {
        return "";
    }
    
    
    /**
     *
     *
     */
    public void run() 
    {
        //System.out.println("running antihack");
        try
        {
            if(m_bAggressive)
                floodRequestor();
            else
                destroy();
        }
        catch(Exception e)
        {
            //no errors....
            //e.printStackTrace();
        }
        //System.out.println("finished antihack");
    }
    
    /**
     * Flood the requestors socket with 1MB of junk
     */
    private void floodRequestor() throws Exception
    {
        //System.out.println("floodRequestor()");
        OutputStream os = m_Sock.getOutputStream();
        //os.write("HTTP/1.1 200 GO AWAY\r\n".getBytes());
        //os.write(makeAntiHackBuffer());
        //os.write("\r\n".getBytes());
        
        for(int i=0; i<1024; i++)
        {
            //os.write("Content-Encoding: ".getBytes());
            os.write("\r\n\r\nYOU MAY NOT ACCESS THIS SERVER!!\r\n\r\n".getBytes());
            os.write(makeAntiHackBuffer());
            //os.write("\r\n".getBytes());           
        }
        /*os.write("Content-Length: 48000\r\n".getBytes());  //wrong content length ;-)          
        os.write("\r\n".getBytes());
        os.write(makeAntiHackBuffer()); 
        os.write("YOU MAY NOT ACCESS THIS SERVER!!\r\n".getBytes());
        os.write(makeAntiHackBuffer());
         */
        
        os.flush();
        os.close();
    }
    
    /**
     * Make a 1024 byte buffer of random chars from 1 to 32
     *
     */
    private byte[] makeAntiHackBuffer()
    {
        byte buf[] = new byte[1024];
        int iMaxChar=254;
        
        for(int i=0; i<buf.length; i++)
        {
            buf[i] = (byte)((Math.random()*(double)iMaxChar)+1);
            //don't use crlf - we want to try to do a buffer overflow attack
            if(buf[i]==10 || buf[i]==12) buf[i]=1;
        }
        return buf;
    }
    
    
    public String getErrorSource()
    {
        return "AntiHackWorker (" + m_iPort + ")";
    }

    public String getErrorUser()
    {
        return "AntiHacker";
    }
    
}
