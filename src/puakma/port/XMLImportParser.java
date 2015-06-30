/** ***************************************************************
XMLImportParser.java
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

package puakma.port;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Timestamp;
import java.util.Date;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.ContentHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

import puakma.util.Util;

/**
 *
 * @author  bupson
 */
public class XMLImportParser implements ContentHandler //, TableRowImportCallback
{    
	private InputStream m_in;
	//private byte[] m_buffer;  
	//private boolean m_bOK=true;
	private puakma.coder.CoderB64 m_b64 = new puakma.coder.CoderB64();  
	private XMLReader m_xmlReader;
	private boolean m_bNullValue =false;
	private TableColumnItem m_tci = null;
	private TableRow m_tr = null;
	private boolean m_bProcessValue=false;
	private TableRowImportCallback m_callback=null;
	private long m_lCount=0;
	private StringBuilder m_sbValue = new StringBuilder(1024);
	private boolean m_bBase64Encoded = false;
	private long m_lRowCount=0;


	/**
	 * @deprecated
	 * @param in
	 */
	public XMLImportParser(InputStream in)
	{
		if(in.markSupported()) in.mark(2048);
		//first up try and use it as a Zip stream
		try
		{
			m_in = new ZipInputStream(in);    	
			ZipEntry ze = ((ZipInputStream)m_in).getNextEntry();
			if(ze==null) 
			{
				System.err.println("Does not look like a zip stream");
				if(in.markSupported()) in.reset();
				m_in = in;				
			}
		}
		catch(Exception e)
		{
			//Not a Zip file, so treat as a standard stream
			System.err.println(e.toString());
			e.printStackTrace();
			try{ if(in.markSupported()) in.reset(); }catch(Exception reset){}
			m_in = in;
		}

		initParser();
	}

	/**
	 * 
	 * @param in
	 * @param bIsZippedStream
	 */
	public XMLImportParser(InputStream in, boolean bIsZippedStream)
	{
		m_in = in;

		if(bIsZippedStream)
		{
			if(in.markSupported()) in.mark(4096);

			try
			{
				m_in = new ZipInputStream(in);    	
				ZipEntry ze = ((ZipInputStream)m_in).getNextEntry();
				if(ze==null) 
				{
					System.err.println("Does not look like a zip stream");
					if(in.markSupported()) in.reset();
					m_in = in;				
				}
			}
			catch(Exception e)
			{
				//Not a Zip file, so treat as a standard stream
				System.err.println(e.toString());
				e.printStackTrace();
				try{ if(in.markSupported()) in.reset(); }catch(Exception reset){}
				m_in = in;
			}
		}//if zipped

		initParser();
	}

	/**
	 * 
	 */
	private void initParser()
	{
		try
		{
			SAXParserFactory factory = SAXParserFactory.newInstance();  
			// Parse the input 
			SAXParser saxParser = factory.newSAXParser();
			m_xmlReader = saxParser.getXMLReader();

		}
		catch(Exception e)
		{
			System.err.println("XMLImportParser(in):" + e.toString());
			//m_bOK = false;
		}
	}

	/**
	 * This expects the input stream to be a Zip input stream
	 */
	/*public oldXMLImportParser(InputStream in)
  {
    m_in = new ZipInputStream(in);

    try
    {
        m_in.getNextEntry();
        SAXParserFactory factory = SAXParserFactory.newInstance();  
    // Parse the input 
        SAXParser saxParser = factory.newSAXParser();
        m_xmlReader = saxParser.getXMLReader();

    }
    catch(Exception e)
    {
      System.err.println("XMLImportParser(in):" + e.toString());
      //m_bOK = false;
    }
  }*/

	public static void main(String args[])
	{
		/*String sB64 = "MTIyODQyODA1MTk2NQ=="; //"MTIyNTQ2MTYwMDAwMA=="; 
		String sDate = new String(Util.base64Decode(sB64));

		Date dt = new Date(Util.toInteger(sDate));
		System.out.println(sB64 + " " + sDate + " = " + dt);

		System.out.println(Util.base64Encode("HO2731908".getBytes()));
		 */

		TableRow tr = new TableRow("TABLE");
		Date dtMyDate = Util.makeDate("1/11/08 01:00", "dd/MM/yy HH:mm");
		tr.addColumn("MyDate", dtMyDate);

		long lDate = tr.getColumnValueLong("MyDate");
		//TableColumnItem tci = tr.getColumn("MyDate");
		//String s = new String(tci.Value);
		Timestamp ts = new Timestamp(lDate);

		System.out.println("myDate = " + lDate + " " + dtMyDate + " ts=" + ts);

		if(true) return;
		try
		{
			File f = new File("/Users/bupson/Desktop/TRACKABLEITEM.pdx");

			FileInputStream fin = new FileInputStream(f);
			XMLImportParser xml = new XMLImportParser(fin);
			xml.parse(null);           

		}
		catch(Exception e)
		{
			e.printStackTrace();
		}
	}


	/**
	 * Do the work. Method will return when parsing and callbacks are complete
	 */
	public long parse(TableRowImportCallback callback) throws IOException,SAXException
	{
		m_lRowCount = 0;
		m_callback = callback;
		m_xmlReader.setContentHandler(this);
		m_xmlReader.parse(new InputSource(m_in));

		return m_lRowCount;
	}

	public void characters(char[] values, int iStart, int iLength) throws org.xml.sax.SAXException 
	{      
		if(m_bProcessValue && iLength>0)
		{          
			m_sbValue.append(values, iStart, iLength);          
		}
	}  

	public void endDocument() throws org.xml.sax.SAXException {
	}  

	public void endElement(String sNameSpaceURI, String sLocalName, String sqName) throws org.xml.sax.SAXException 
	{
		if(sqName.equals("row"))
		{          
			m_lRowCount++;
			if(m_tr!=null && m_callback!=null)
			{
				//System.out.println("----- End of ROW -----");
				//System.out.println(m_tr.toString());
				m_callback.importCallback(m_tr);              
				m_tr = null;
				m_tci=null;
			}
		}

		if(sqName.equals("value"))
		{

			if(this.m_bNullValue)
				m_tci.setValue(null);
			else
			{
				byte buf[] = Util.utf8FromString(m_sbValue.toString());
				if(m_bBase64Encoded) buf = m_b64.decode(buf);
				m_tci.setValue(buf);          
			}

			m_bProcessValue=false;
		}
	}

	public void endPrefixMapping(String str) throws org.xml.sax.SAXException {
	}

	public void ignorableWhitespace(char[] values, int param, int param2) throws org.xml.sax.SAXException {
	}

	public void processingInstruction(String str, String str1) throws org.xml.sax.SAXException {
	}

	public void setDocumentLocator(org.xml.sax.Locator locator) {
	}

	public void skippedEntity(String str) throws org.xml.sax.SAXException {
	}

	public void startDocument() throws org.xml.sax.SAXException 
	{
	}

	public void startElement(String sNameSpaceURI, String sLocalName, String sqName, org.xml.sax.Attributes attributes) throws org.xml.sax.SAXException 
	{
		//System.out.println("startElement:"+sqName);
		if(sqName.equals("row"))
		{
			m_lCount++;
			//System.out.println("=== Start row ===");
			String sTable = attributes.getValue("table");
			m_tr = new TableRow(sTable);
			return;
		}

		if(sqName.equals("item"))
		{
			String sName = attributes.getValue("name");
			m_tr.addColumn(sName, "");
			m_tci = m_tr.getColumn(sName);          
			int iType = (int)Util.toInteger(attributes.getValue("type"));
			String sForeignKeyTo = attributes.getValue("foreignkeyto");
			boolean bIsPrimaryKey = Boolean.valueOf(attributes.getValue("isprimarykey")).booleanValue();
			m_tci.Type = iType;          
			m_tci.ForeignKeyTo = sForeignKeyTo;
			m_tci.isPrimaryKey = bIsPrimaryKey; 
			m_bNullValue = false;
			return;
		}

		if(sqName.equals("value"))
		{
			m_sbValue.delete(0, m_sbValue.length());
			String sNull =  attributes.getValue("isNull");
			//String sEncoding =  attributes.getValue("encoding");
			m_bNullValue = Boolean.valueOf(sNull).booleanValue(); 
			// <value encoding=\"base64\">
			String sEncoding = attributes.getValue("encoding");
			m_bBase64Encoded  = false;
			if(sEncoding!=null && sEncoding.equalsIgnoreCase("base64")) m_bBase64Encoded = true;
			m_bProcessValue = true;
			return;
		}
	}

	public void startPrefixMapping(String str, String str1) throws org.xml.sax.SAXException {
	}

	/*public void importCallback(TableRow tr) 
  {
      TableColumnItem tci = tr.getColumn("TrackableItemCreator");
      if(tci==null || tci.isNull())
      {
          System.out.println("#### BAD RECORD: "+m_lCount);
          System.out.println(tr.toString());
      }
  }*/

}//class
