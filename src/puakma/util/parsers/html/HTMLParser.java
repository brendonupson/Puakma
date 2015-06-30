/** ***************************************************************
HTMLParser.java
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


import java.io.*;
import java.util.*;

/**
 * Parse a stream of html into its appropriate components
 * Assumes tags are NOT split across lines eg <tit\r\nle> does not exist
 * Creation date: (18/06/2002)
 * author: Brendon Upson
 */
public class HTMLParser
{
	private StringBuilder m_sPlainText = new StringBuilder(512);
	private StringBuilder m_sHTML = new StringBuilder(1024);
	private StringBuilder m_sTitle = new StringBuilder(50);
	private String m_sSkipUntil = "<";
	HTMLDocument m_doc;

	private final int MAX_READ = 204800; //200kB
	private static HashSet m_excludeTags = new HashSet();
	private boolean m_bWriteTitle=false;
	private boolean m_bIgnore = false;

	//private boolean m_bIgnoreComments=true; // <!-- bla -->

	/*static 
    {
	m_excludeTags = new HashSet();
	//m_excludeTags.add("style");
	//m_excludeTags.add("script");
    }*/

	/**
	 * HTMLParser constructor comment.
	 */
	public HTMLParser() {
		super();
	}

	public void setExcludeTag(String sTagName)
	{
		if(sTagName!=null && sTagName.length()>0) m_excludeTags.add(sTagName);
	}

	/**
	 * for testing....
	 */
	public static void main(String[] args) 
	{
		HTMLDocument doc = new HTMLDocument();
		HTMLParser parser = new HTMLParser();                
		try
		{
			File f = new File("d:/test.html");
			FileInputStream fin = new FileInputStream(f);
			doc.setPageSize((int)f.length());
			parser.parse(doc, (InputStream)fin);
		}
		catch(Exception e)
		{
			e.printStackTrace();
		}
		System.out.println("done.");
		System.out.println(doc.getTitle());
		System.out.println("-----------------------------");
		System.out.println(doc.toTextOnly());
		ArrayList ar = doc.getTagsByName("a");                                  
		if(ar==null) return;
		System.out.println("#items=" + ar.size());
		for(int i=0; i<ar.size(); i++)
		{
			HTMLTag tag = (HTMLTag)ar.get(i);
			String szNextURL = tag.getAttribute("href");
			System.out.println("[" + szNextURL + "]");
		}
	}

	/**
	 * Convenience method for creating a parsed document. We assume the string will be in
	 * ISO-8859-1 format
	 */
	public HTMLDocument parse(String sData) throws Exception
	{
		if(sData==null) return null;

		HTMLDocument doc = new HTMLDocument();
		byte buf[] = sData.getBytes("ISO-8859-1");
		ByteArrayInputStream bin = new ByteArrayInputStream(buf);

		doc.setPageSize(buf.length);
		parse(doc, (InputStream)bin);

		return doc;
	}

	/**
	 * Parse a stream putting the contents into the document
	 *
	 */
	public void parse(HTMLDocument doc, InputStream in) throws Exception 
	{		
		StringBuilder sbToProcess = new StringBuilder(1024);				
		int iTotalRead=0;
		int iRead=0;
		int iPageSize = doc.getPageSize();
		BufferedReader br = new BufferedReader(new InputStreamReader(in, "ISO-8859-1"));
		String szLine;
		m_doc = doc;
		char inputBuffer[] = new char[40960]; //40K

		while(iTotalRead<iPageSize && (iRead=br.read(inputBuffer))>0) 
		{
			szLine = new String(inputBuffer, 0, iRead);
			m_sHTML.append(szLine);
			szLine = szLine.replaceAll("\r", "");
			szLine = szLine.replaceAll("\n", " ");                        
			iTotalRead += iRead;                        
			sbToProcess.append(szLine);
			szLine = sbToProcess.toString();
			//System.out.println(szLine);
			//System.out.println("read=" + length + " bytes. docsize= " + doc.getPageSize());

			//Date dtStart = new Date();
			//long lStart = System.currentTimeMillis();
			int iPos = szLine.toLowerCase().indexOf(m_sSkipUntil);
			while(iPos>=0)
			{
				//System.out.println(szLine);
				sbToProcess = processBuffer(sbToProcess, iPos);
				szLine = sbToProcess.toString();
				iPos = szLine.toLowerCase().indexOf(m_sSkipUntil);
			}
			//System.out.println("processBuffer() " + (System.currentTimeMillis()-lStart) + "milliseconds");
			if(iTotalRead>MAX_READ)
			{                        
				System.out.println("Document size too large, truncating. Read=" + iTotalRead + "bytes url=" + doc.getUrl());
				System.gc();
				break; //so we don't run out of memory!
			}                    
		}//while

		//catch(Exception e){ System.out.println("err=" + e.toString()); }//ignore read timeouts
		m_doc.setRawHTML("");
		m_doc.setPlainText(m_sPlainText.toString());
		m_doc.setTitle(m_sTitle.toString());
	}


	/**
	 *
	 */
	private StringBuilder processBuffer(StringBuilder sbProcess, int iPos)
	{            
		String szWork = sbProcess.toString();
		String szTagName, szChunk, szFullTag;
		int iNameEnd; //, iTagEnd;                     
		//Date dtStart = new Date(); //for perf monitoring

		szChunk = "";
		if(iPos<0) return sbProcess;

		szChunk = szWork.substring(0, iPos);
		if(m_sSkipUntil.equals(">"))
		{
			//then process a tag
			m_bWriteTitle = false;
			iNameEnd = szChunk.indexOf(' ');
			if(iNameEnd<0) iNameEnd = szChunk.indexOf('>');
			if(iNameEnd<0) iNameEnd = szChunk.length();
			szTagName = szChunk.substring(0, iNameEnd);                

			if(szTagName.startsWith("!--") || m_excludeTags.contains(szTagName.toLowerCase())) 
			{
				m_bIgnore = true;
				if(m_excludeTags.contains(szTagName.toLowerCase()))
				{                        
					m_sSkipUntil = "</" + szTagName.toLowerCase() + ">";
				}
				else
				{

					if(szChunk.endsWith("--"))
					{
						szWork = szWork.substring(iPos+1, szWork.length());
						m_bIgnore = false;
						m_sSkipUntil = "<";
					}
					else
					{
						szWork = szWork.substring(3, szWork.length());
						m_sSkipUntil = "-->";
					}
				}
			}
			else
			{        
				szFullTag = szChunk.substring(0, iPos);
				if(shouldStoreTag(szFullTag, szTagName))//  szFullTag!=null && !szFullTag.equals("") && !szFullTag.startsWith("/"))
				{
					HTMLTag tag = new HTMLTag(szFullTag);
					if(tag.getName().toLowerCase().equals("title")) m_bWriteTitle=true;
					m_doc.addTag(tag);
				}                    
				m_sSkipUntil = "<";
				szWork = szWork.substring(iPos+1, szWork.length());
			}

			return new StringBuilder(szWork);
		}
		if(m_sSkipUntil.equals("<"))
		{
			//then process plain text                    
			if(!szChunk.equals("") && !m_bIgnore) m_sPlainText.append(szChunk + " ");
			if(m_bWriteTitle) m_sTitle.append(szChunk);
			szWork = szWork.substring(iPos+1, szWork.length());
			m_sSkipUntil = ">";
			return new StringBuilder(szWork);
		}   
		if(m_sSkipUntil.equals("-->"))
		{
			m_bIgnore = false;
			szWork = szWork.substring(iPos+m_sSkipUntil.length(), szWork.length());
			m_sSkipUntil = "<";
			return new StringBuilder(szWork);
		}
		if(m_sSkipUntil.startsWith("</"))
		{
			m_bIgnore = false;                
			szWork = szWork.substring(iPos+m_sSkipUntil.length(), szWork.length());
			m_sSkipUntil = "<";
			return new StringBuilder(szWork);
		}

		return sbProcess;
	}





	/**
	 * Determine if we should store this tag against the document object
	 */
	private boolean shouldStoreTag(String szFullTag, String szTagName)
	{
		if(szFullTag==null || szFullTag.length()==0) return false;            
		if(szFullTag.charAt(0)=='/') return false;
		if(szTagName.equalsIgnoreCase("b")) return false;
		if(szTagName.equalsIgnoreCase("br")) return false;
		if(szTagName.equalsIgnoreCase("table")) return false;
		if(szTagName.equalsIgnoreCase("tr")) return false;
		if(szTagName.equalsIgnoreCase("td")) return false;
		if(szTagName.equalsIgnoreCase("font")) return false;
		if(szTagName.equalsIgnoreCase("html")) return false;
		if(szTagName.equalsIgnoreCase("head")) return false;
		if(szTagName.equalsIgnoreCase("p")) return false;
		if(szTagName.equalsIgnoreCase("i")) return false;

		return true;
	}

}//end class