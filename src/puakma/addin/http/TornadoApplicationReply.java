package puakma.addin.http;

import java.util.ArrayList;

import puakma.util.Util;

public class TornadoApplicationReply 
{	
	private int m_iHttpReplyCode=500;
	private String m_sReplyMessage="";
	private ArrayList<String> m_arrExtraHeaders = new ArrayList<String>();
	private byte m_bufContent[] = null;
	//private boolean m_bHasStreamed = false;
	private String m_sContentType="text/html";
	
	public TornadoApplicationReply(int iReply, String sMessage, byte bufContent[]) 
	{
		m_iHttpReplyCode = iReply;
		m_sReplyMessage = sMessage;
		m_bufContent = bufContent;
	}

	/**
	 * Sets an extra header that should be included in the http response
	 * @param sHeaderLine
	 */
	public void addHttpHeader(String sHeaderLine) 
	{
		m_arrExtraHeaders.add(sHeaderLine);
	}
	
	/**
	 * Return the extra http headers in a two dimensional array of name/value pairs 
	 * @return
	 */
	public String[][] getExtraHeaders()
	{
		String sReply[][] = new String[m_arrExtraHeaders.size()][2];
		
		for(int i=0; i<m_arrExtraHeaders.size(); i++)
		{
			String sLine = (String)m_arrExtraHeaders.get(i);
			int iPos = sLine.indexOf(':');
			if(iPos>0) // can't be ": xxx" or have no : at all
			{
				sReply[i][0] = sLine.substring(0, iPos);
				sReply[i][1] = Util.trimSpaces(sLine.substring(iPos+1));
			}
		}
		
		return sReply;
	}

	/**
	 * 
	 * @return
	 */
	public String getContentType() 
	{
		return m_sContentType;
	}
	
	public void setContentType(String sNewType)
	{
		//TODO check \r\nContent-Disposition: .... etc and add to m_arrExtraHeaders
		m_sContentType = sNewType;
	}
	
	/**
	 * 
	 * @return
	 */
	public int getReplyCode()
	{
		return m_iHttpReplyCode;
	}
	
	/**
	 * 
	 * @return
	 */
	public String getReplyMessage()
	{
		return m_sReplyMessage;
	}
	
	/**
	 * get the size of the reply payload
	 * @return
	 */
	public int getContentLength()
	{
		if(m_bufContent==null) return 0;
		
		return m_bufContent.length;
	}
	
	/**
	 * 
	 * @return
	 */
	public byte[] getContent()
	{
		if(m_bufContent==null) return new byte[0];
		
		return m_bufContent;
	}
}
