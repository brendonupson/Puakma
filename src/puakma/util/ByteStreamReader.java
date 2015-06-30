/** ***************************************************************
ByteStreamReader.java
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


import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;

/**
 * This class is designed to read a stream of bytes. It is a reader because we
 * want to be able to use readLine(). This will read a line up to the CRLF (\r\n)
 * and consume the CRLF
 * @author  bupson
 */
public class ByteStreamReader extends Reader 
{    
    private BufferedInputStream m_is=null;
    //private byte[] m_buf = null;
    private String m_sCharSet = null;
        
    public ByteStreamReader(InputStream is, int iBufferSize, String sCharSet) 
    {        
        initialise(is, iBufferSize, sCharSet);
    }
    
    public ByteStreamReader(InputStream is) 
    {
        initialise(is, -1, null);
    }
    
    /**
     * Do the heavy lifting to create the reader
     */
    private void initialise(InputStream is, int iBufferSize, String sCharSet)
    {        
        if(iBufferSize>0) 
            m_is = new BufferedInputStream(is, iBufferSize);        
        else
            m_is = new BufferedInputStream(is);
        m_sCharSet = sCharSet;
    }
    
    public void close() throws java.io.IOException 
    {
        m_is.close();
    }
    
    public void mark(int readlimit)
    {
        m_is.mark(readlimit);
    }
    
    public void reset() throws IOException
    {
        m_is.reset();
    }
    
    /**     
     * This method should not be used and is used only to maintain compatibility with 
     * the Readers
     */
    public int read(char[] values, int param, int param2) throws java.io.IOException 
    {
        return 0;        
    }
    
    /**
     *Read a block of bytes from the stream into the byte buffer
     *
     */
    public int read(char[] cbuf) throws java.io.IOException
    {
        if(cbuf==null || cbuf.length==0) return 0;
                
        byte buf[] = new byte[cbuf.length];
        int iRead = m_is.read(buf);
        for(int i=0; i<iRead; i++)
        {
            cbuf[i] = (char)buf[i];
        }
        return iRead;
    }
    
    /**
     *Read a block of bytes from the stream into the byte buffer
     *
     */
    public int read(byte[] buf) throws java.io.IOException
    {
        if(buf==null || buf.length==0) return 0;
                
        return m_is.read(buf);
    }
    
    
    
    /**
     * Read all the way up to a CRLF and consume it. This method looks for \r\n in that
     * order and is usually used for reading http streams
     * @return The string up to the CRLF. Returns null if no data was read from the stream
     */
    public String readLine() throws java.io.IOException
    {
        byte CRLF[] = new byte[]{'\r','\n'};
        byte buf[] = new byte[1];
        byte bufReturn[] = null;
        byte bufTestCRLF[] = new byte[2];
        bufTestCRLF[0] = 0;
        bufTestCRLF[1] = 0;
        boolean bReading = true;
        ByteArrayOutputStream baos = new ByteArrayOutputStream(2048);
                
        while(bReading)
        {
            int iRead = m_is.read(buf);
            if(iRead>0)
            {
                baos.write(buf);
                //System.out.print(Integer.toHexString(buf[0]) + " ");
                bufTestCRLF[0] = bufTestCRLF[1];
                bufTestCRLF[1] = buf[0];
                if(CRLF[0]==bufTestCRLF[0] && CRLF[1]==bufTestCRLF[1])
                {
                    bReading = false;
                    byte buf2[] = baos.toByteArray();
                    bufReturn = new byte[buf2.length-2];
                    System.arraycopy(buf2, 0, bufReturn, 0, buf2.length-2);
                }
            }
            if(iRead<0) bReading = false; //end of stream
        }
        if(bufReturn==null && baos.size()>0) bufReturn = baos.toByteArray();
        if(bufReturn==null) return null;
        
        String s = new String(bufReturn, m_sCharSet);
        //System.out.println("{"+s+"} " + bufReturn.length);
        return s;
    }
    
    /**
     * Get a handle to the underlying stream
     */
    public InputStream getInputStream()
    {
        return m_is;
    }
    
}
