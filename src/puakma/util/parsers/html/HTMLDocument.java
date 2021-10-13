/** ***************************************************************
HTMLDocument.java
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

package puakma.util.parsers.html;

import java.io.File;
import java.util.ArrayList;
import java.util.Hashtable;

/**
 *
 */
public class HTMLDocument {

	/**
	 * HTMLDocument constructor comment.
	 */

	private String rawHTML = null;
	private String textOnly = null;

	private String url = null;
	private String title = null;
	private int httpRespCode = 0;
	private String httpResp = null;
	private int m_iPageSize=0;
	private int m_iPort=80;        
	private String m_sServer="";
	private String m_sHost="";
	private String m_sContentType="";
	private long m_lProcessTimeMS=0;
	private String m_sErrorMessage="";
	private File m_fData=null;


	private Hashtable tags = null;

	public HTMLDocument() {
		super();

		tags = new Hashtable();
	}

	protected void finalize()
	{
		if(m_fData!=null) m_fData.delete();
	}


	public String toTextOnly() 
	{
		if(textOnly==null) return "";
		return textOnly;
	}

	public String toHTML() {
		return this.rawHTML;
	}

	public String getTitle() 
	{
		if(title==null) return "";
		return this.title;
	}

	public void setTitle(String t) 
	{
		if(t==null) 
			title="";
		else                
			title = cleanText(t);
	}        

	public void setRawHTML(String rawHTML) {
		this.rawHTML = rawHTML;
	}

	public void setPlainText(String plainText) {
		this.textOnly = cleanText(plainText);
	}

	public void setUrl(String url) {
		this.url = url;
	}

	public void setFile(File fInput)
	{
		m_fData = fInput;
	}

	public File getFile()
	{
		return m_fData;
	}

	public void setHttpResonseCode(int respCode) {
		this.httpRespCode = respCode;
	}

	public void setHttpResponse(String resp) {
		this.httpResp = resp;
	}

	public void setErrorMessage(String szMsg)
	{
		m_sErrorMessage = szMsg;
	}

	public String getErrorMessage()
	{
		return m_sErrorMessage;
	}

	public int getHttpResponseCode() {
		return this.httpRespCode;
	}

	public String getHttpResponse() {
		return this.httpResp;
	}

	public String getUrl() {
		return this.url;
	}

	public String getShortUrl() 
	{
		String szShortURL = this.url;
		int iPos = szShortURL.indexOf('?');
		if(iPos>=0)
		{
			szShortURL = szShortURL.substring(0, iPos);
		}
		return szShortURL;
	}

	public void addTag(HTMLTag tag) 
	{
		ArrayList al = null;
		String szName = tag.getName();

		al = (ArrayList) tags.get(szName);
		if(al == null) al = new ArrayList();

		al.add(tag);
		tags.put(szName, al);
	}
	
	public Hashtable getAllTags()
	{
		return tags;
	}

	public ArrayList getTagsByName(String tagName) 
	{
		ArrayList al = null;

		if (tagName == null) return new ArrayList();

		al = (ArrayList) tags.get(tagName);
		if(al==null) return new ArrayList();
		return al;
	}

	public String getKeywords() {

		ArrayList metaTags = this.getTagsByName("meta");

		if (metaTags == null)
			return "";

		for (int i = 0; i < metaTags.size(); i++) {

			HTMLTag tag = (HTMLTag) metaTags.get(i);
			String name = tag.getAttribute("name");

			if (name != null && name.equalsIgnoreCase("keywords"))
				return tag.getAttribute("content");

		}

		return "";
	}

	public String getDescription() {

		ArrayList metaTags = this.getTagsByName("meta");

		if (metaTags == null)
			return "";

		for (int i = 0; i < metaTags.size(); i++) {

			HTMLTag tag = (HTMLTag) metaTags.get(i);
			String name = tag.getAttribute("name");

			if (name != null && name.equalsIgnoreCase("description"))
				return tag.getAttribute("content");

		}

		return "";
	}

	public void setPort(int iPort)
	{
		m_iPort=iPort;
	}

	public int getPort()
	{
		return m_iPort;
	}


	public void setPageSize(int iSize)
	{
		m_iPageSize=iSize;        
	}

	public void setProcessTime(long lTimeMS)
	{
		m_lProcessTimeMS=lTimeMS;        
	}

	public void setServer(String szServer)
	{
		m_sServer=szServer;
	}

	public void setHost(String szHost)
	{
		int iPos=szHost.indexOf(':');
		if(iPos>=0)
			m_sHost=szHost.substring(0, iPos);
		else
			m_sHost=szHost;
	}

	public void setContentType(String szContentType)
	{
		m_sContentType=szContentType;
	}



	public int getPageSize()
	{
		return m_iPageSize;        
	}

	public long getProcessTime()
	{
		return m_lProcessTimeMS;        
	}

	public String getServer()
	{
		return m_sServer;
	}

	public String getHost()
	{
		return m_sHost;
	}

	public String getContentType()
	{
		return m_sContentType;
	}

	public String toString()
	{
		String CRLF="\r\n";
		return this.url + CRLF + this.getTitle() + CRLF + this.getContentType() + CRLF + this.getPageSize();
	}

	/**
	 * cleans up the html text so there are only spaces
	 */
	 public static String cleanText(String szText)
	 {
		 if(szText==null) return null;
		 szText = szText.replaceAll("&nbsp;", " ");
		 szText = szText.replaceAll("&nbsp", " ");
		 szText = szText.replaceAll("&quot;", "'");
		 szText = szText.replaceAll("&quot", "'");
		 szText = szText.replaceAll("&amp;", "&");
		 szText = szText.replaceAll("&amp", "&");
		 szText = szText.replaceAll("\r", "");
		 szText = szText.replaceAll("\n", " ");
		 while(szText.indexOf("  ")>=0) szText = szText.replaceAll("\\s\\s", " ");
		 int iTruncStart = 0;
		 while(iTruncStart<szText.length() && szText.charAt(iTruncStart)==' ') iTruncStart++;
		 szText = szText.substring(iTruncStart, szText.length());

		 return szText;
	 }

}