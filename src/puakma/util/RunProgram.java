/** ***************************************************************
RunProgram.java
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

import java.io.*;

/**
 *
 * @author  bupson
 */
public class RunProgram 
{    
    private int m_iBufferSize=-1;
    private byte m_buf[];
    private byte m_bufErr[];
    
    public RunProgram()
    {
        //default constructor
    }
    
    /** Creates a new instance of RunProgram */
    public RunProgram(int iBufferSize) 
    {
        m_iBufferSize = iBufferSize;
        
    }
    
    /*public static void main(String sArgs[])
    {
        RunProgram rp = new RunProgram();
        int iReturnVal = rp.execute(new String[]{"ls"}, null, "/data/puakma/temp/");
        System.out.println("Returned: "+iReturnVal);
        System.out.println(new String(rp.getCommandOutput()));
    }*/
    
    /**
     * Execute a command and capture its output in a buffer
     */
    public int execute(String cmdarray[], String env[], String sWorkingDir)
    {
        File fWorkingDir = new File(sWorkingDir);
        //if dir is incorrect, set to null - current dir whatever that is
        if(!(fWorkingDir.exists() && fWorkingDir.isDirectory())) fWorkingDir = null;
        return execute(cmdarray, env, fWorkingDir, null);    
    }
    
    
    
    /**
     * Execute a command and capture its output in a buffer
     */
    public int execute(String cmdarray[], String env[], File fWorkingDir, InputStream is)
    {   
        int iReturnVal = 999;
        Process proc = null;
        StreamConsumer sc = null;
        StreamConsumer scErr = null;
        try
        {
            proc = Runtime.getRuntime().exec(cmdarray, env, fWorkingDir);
            
            sc = new StreamConsumer(proc.getInputStream(), m_iBufferSize);
            scErr = new StreamConsumer(proc.getErrorStream(), m_iBufferSize);
            sc.start();
            scErr.start();
            OutputStream os = proc.getOutputStream();           
            byte buf[] = new byte[1024];
            while(is!=null && is.available()>0)
            {                
                int iRead = is.read(buf);
                //System.out.println("WRITING: [" + new String(buf, 0, iRead) + "]");
                os.write(buf, 0, iRead);
                os.flush();
            }
            iReturnVal = proc.waitFor();
            m_buf = sc.getBuffer();
            m_bufErr = scErr.getBuffer();
        }
        catch(Exception e)
        {
            System.out.println(e.toString());
        }
          
        if(sc!=null && sc.isAlive()) sc.requestQuit();
        if(scErr!=null && scErr.isAlive()) scErr.requestQuit();
        return iReturnVal;
    }
    
    /**
     *
     */
    public byte[] getCommandOutput()
    {
        return m_buf;
    }
    
    /**
     *
     */
    public byte[] getCommandErrorOutput()
    {
        return m_bufErr;
    }
    
    
    
}//RunProgram


/**
 * Consumes an input Stream
 *
 */
class StreamConsumer extends Thread
{
    private InputStream m_is; 
    private ByteArrayOutputStream m_baos;
    private static int DEFAULT_BUFFER_SIZE = 2048;
    private int m_iBufferSize;
    private boolean m_bRunning = true;
    
    StreamConsumer(InputStream is, int iBufferSize)
    {
        m_is = is;    
        m_iBufferSize = iBufferSize;
    }
    
    /**
     * Ask the thread to quit
     */
    public void requestQuit()
    {
        m_bRunning = false;
        try{ m_is.close(); }catch(IOException ioe){}
    }
    
    /**
     *
     *
     */
    public void run()
    {
        if(m_is==null) return;
        
        if(m_iBufferSize>0) 
            m_baos = new ByteArrayOutputStream(m_iBufferSize);
        else
            m_baos = new ByteArrayOutputStream(DEFAULT_BUFFER_SIZE);
        try
        {
            int iTotalRead = 0;
            byte buf[] = new byte[1024];
            while(m_bRunning)
            {           
                if(m_is==null) break;
                int iRead = m_is.read(buf);
                if(iRead>0)
                {
                    if(m_iBufferSize>0 && iTotalRead<m_iBufferSize)
                    {
                        int iToGo = m_iBufferSize-iTotalRead;                        
                        if(iToGo>=iRead)                        
                            m_baos.write(buf, 0, iRead);                        
                        else
                            m_baos.write(buf, 0, iToGo);                            
                    }
                    else
                    {
                        //unlimited buffer, read it all
                        if(m_iBufferSize<=0) m_baos.write(buf, 0, iRead);
                    }
                    iTotalRead+= iRead;
                }
            }//while
            
        }
        catch (Exception e)
        {
            //e.printStackTrace();  
        }
    }
    
    /**
     *
     */
    public byte[] getBuffer()
    {
        if(m_baos==null) return null;
        
        return m_baos.toByteArray();
    }
}//class
