/** ***************************************************************
SOAPCallParser.java
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


package puakma.SOAP;

/**
 * <p>Title: </p>
 * <p>Description: For parsing inbound SOAP messages and formatting it into pieces
 * ready to be passed to the actual service. We assume that the message will fit in
 * memory!</p>
 * <p>Copyright: Copyright (c) 2002</p>
 * <p>Company: </p>
 * @author Brendon Upson
 * @version 1.0
 */

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Date;
import java.util.Hashtable;
import java.util.StringTokenizer;
import java.util.Vector;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import puakma.system.SystemContext;

public class SOAPCallParser
{
	private Vector m_vParams=new Vector();
	private String m_Method;
	private String m_Service;
	private boolean m_bIsOK=false;
	private SystemContext m_sysCtx;
	private Object m_oReturn;
	private String m_sFault=null;
	private String m_sFaultCode=null;
	private String m_sElementPrefix=""; //eg "Soap"
	private String m_sSoapEnc="soapenc:";
	private String m_sXsd="xsd:";
	private String m_sXsi="xsi:";
	private Hashtable m_htNamespaceMap = new Hashtable();
	private static int DEFAULT_SB_SIZE=12000;

	public static final String SEPERATOR = ":";
	//TODO MAKE THESE METHODS NOT CONSTANTS IN CASE m_sXsd changes
	public final static String OBJECT_TYPE_STRING = "string";
	public final static int OBJECT_TYPE_STRING_ID = 1;
	public final static String OBJECT_TYPE_FLOAT = "float";
	public final static int OBJECT_TYPE_FLOAT_ID = 2;
	public final static String OBJECT_TYPE_INTEGER = "int";
	public final static int OBJECT_TYPE_INTEGER_ID = 3;
	public final static String OBJECT_TYPE_DOUBLE = "double";
	public final static int OBJECT_TYPE_DOUBLE_ID = 4;
	public final static String OBJECT_TYPE_LONG = "integer";
	public final static int OBJECT_TYPE_LONG_ID = 5;
	public final static String OBJECT_TYPE_DATETIME = "dateTime"; //2003-01-01T21:31:52.038Z
	public final static int OBJECT_TYPE_DATETIME_ID = 6;
	public final static String OBJECT_TYPE_BOOLEAN = "boolean";
	public final static int OBJECT_TYPE_BOOLEAN_ID = 7;

	public final static String OBJECT_TYPE_BASE64 = "base64Binary";
	public final static int OBJECT_TYPE_BASE64_ID = 9;


	public String ELEMENT_TYPE_ARRAY = "Array";
	public String SOAP_FAULT = "Fault";
	public String SOAP_FAULT_CODE = "faultcode";
	public String SOAP_FAULT_STRING = "faultstring";
	//public String SOAP_NULL = " xsi:null=\"1\" ";
	public String SOAP_DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'";
	public final static String XMLNS = "xmlns";

	private org.w3c.dom.Document m_document;


	public String getSOAPNull()
	{
		return " "+ m_sXsi + "null=\"1\" ";
	}


	public SOAPCallParser(SystemContext sysCtx)
	{
		m_sysCtx = sysCtx;
	}

	public SOAPCallParser()
	{
		m_sysCtx = null;
	}

	/*
  public SOAPCallParser(InputStream is)
  {
    m_sysCtx = null;
    try
    {
      m_bIsOK = parseStream(is);
    }
    catch(Exception e)
    {
      if(m_sysCtx!=null)
        m_sysCtx.doError(e.toString(), this);
      else      
        System.err.println("SOAPCallParser(InputStream) ERROR: " + e.toString());
      //e.printStackTrace();
    }

  }
	 */


	public SOAPCallParser(SystemContext sysCtx, InputStream is, String sServiceName)
	{
		m_sysCtx = sysCtx;
		m_Service = sServiceName;
		try
		{
			m_bIsOK = parseStream(is);
		}
		catch(Exception e)
		{
			if(m_sysCtx!=null)
				m_sysCtx.doError(e.toString(), sysCtx);
			else
				System.err.println("SOAPCallParser(SystemContext, InputStream) ERROR: " + e.toString());
		}

	}

	public static void main(String args[])
	{
		try
		{
			FileInputStream fis = new FileInputStream(new File("c:/temp/soaparray.xml"));
			SOAPCallParser scp = new SOAPCallParser(null, (InputStream)fis, "someservice");
			Object o = scp.getObjectToReturn();
			System.out.println("Return: "+ o);
			System.out.println("Finished....");
		}
		catch(Exception f){System.out.print("Oh Crap. " + f.toString());}
	}

	public synchronized void setServiceName(String sServiceName)
	{
		m_Service = sServiceName;
	}

	/**
	 *
	 * @param is
	 * @return
	 * @throws Exception
	 */
	public boolean parseStream(InputStream is) throws Exception
	{

		DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
		DocumentBuilder parser = dbf.newDocumentBuilder();

		//I guess we should parse by position in the rather than by looking for an 
		//explicit tag name.
		// WHAT SAYS THIS IS A SOAP MESSAGE?? SOME ELEMENT IN THE HEADER?

		//  Parse the Document
		m_document = parser.parse(is);

		Node rootnode = m_document.getFirstChild();// .getChildNodes();
		//if(rootnode.getLength()==0) return false;
		if(rootnode==null) return false;

		//assume the first node is the envelope
		//NodeList nlHeader = rootnode;
		//if(nlHeader.getLength()>0) processHeader( nlHeader.item(0) );
		processHeader( rootnode );

		//assume the first child inside the envelope is the body
		//NodeList nlHeader = rootnode.getChildNodes();
		//System.out.println("childnodes="+nlHeader.getLength());
		NodeList nlBody = rootnode.getChildNodes();
		if(nlBody.getLength()>0) 
		{
			for(int i=0; i<nlBody.getLength(); i++)
			{
				//first node defines the service and method
				Node ServiceNode = nlBody.item(i);
				if(ServiceNode.getNodeType()==Node.ELEMENT_NODE)
				{
					processBody( ServiceNode );
					break;
				}
			}
		}

		return true;
	}

	/**
	 * Determines if this element should be null
	 * <some_element xsi:null="1"></some_element>
	 * @param nAttribute
	 * @return
	 */
	public boolean isNull(Node nAttribute)
	{
		NamedNodeMap n = nAttribute.getAttributes();
		Node nNull = n.getNamedItem(m_sXsi+"null");
		if(nNull!=null)
		{
			if(nNull.getNodeValue().equals("1")) return true;
		}
		return false;
	}

	/**
	 * decodes the body of the SOAP message
	 * @param nBody
	 */
	private void processBody(Node nBody)
	{
		boolean bFirstNode = true;
		//System.out.println(nBody.getNodeName());
		NodeList nlAllBodyParts = nBody.getChildNodes();
		for(int i=0; i<nlAllBodyParts.getLength(); i++)
		{
			//first node defines the service and method
			Node ServiceNode = nlAllBodyParts.item(i);
			if(ServiceNode.getNodeType()==Node.ELEMENT_NODE)
			{
				String sName = ServiceNode.getNodeName();
				//NamedNodeMap att = ServiceNode.getAttributes();
				if(bFirstNode)
				{
					//String sPrefix = determinePrefix(sName);
					m_Method = determineMethod(sName);          
				}

				//Now parse the body node parameters
				processBodyMethodParams(ServiceNode.getChildNodes());

				bFirstNode = false;
				break;
			}//if entity node
		}
	}

	/**
	 * parse out the parameters passed to the function.
	 * @param nlParams
	 */
	private void processBodyMethodParams(NodeList nlParams)
	{
		//NodeList nlParams = ServiceNode.getChildNodes();
		//boolean bIsNull = false;
		for(int i=0; i<nlParams.getLength(); i++) //traverse each parameter
		{
			//bIsNull = false;
			Node param = nlParams.item(i);
			if(param.getNodeType()==Node.ELEMENT_NODE)
			{
				//if(isNull(param)) bIsNull = true;     
				//System.out.println("["+param.getNodeName() + "]");
				if(param.getNodeName().equals(m_sElementPrefix+SOAP_FAULT))
				{
					processFault(param.getChildNodes());
					return;
				}

				NamedNodeMap attParam = param.getAttributes();
				Node nHREF = attParam.getNamedItem("href");
				if(nHREF!=null)
				{
					String sRef = nHREF.getNodeValue();    
					if(sRef.charAt(0)=='#') sRef = sRef.substring(1);              
					//System.out.println("ref="+sRef);
					Node nRefNode = getRefNode(m_document.getChildNodes(), sRef);               
					if(nRefNode!=null) 
					{                  
						processMethodParameter(nRefNode);
					}
					else
						System.out.println("Element not found: " + sRef);
				}
				else
					processMethodParameter(param);

			}//if element node
		}//for k

	}

	/**
	 * gets the element node with the ID attribute set to sRef
	 * <s ID="sRef" ...>
	 */
	private Node getRefNode(NodeList nlRoot, String sRef)
	{
		if(nlRoot==null || sRef==null) return null;
		//Node nRefNode 
		for(int i=0; i< nlRoot.getLength(); i++)
		{
			Node n = nlRoot.item(i);
			if(n.getNodeType()==Node.ELEMENT_NODE)
			{
				//System.out.println("node="+n.getNodeName());
				NamedNodeMap nAttrs = n.getAttributes();
				Node nID = nAttrs.getNamedItem("id");
				if(nID!=null) 
				{
					String sIDName = nID.getNodeValue();
					if(sIDName!=null && sIDName.equals(sRef)) return n;
				}
				Node n2 = getRefNode(n.getChildNodes(), sRef);
				if(n2!=null) return n2;
			}
		}

		return null;
	}

	/**
	 * Processes a parameter of a method
	 */
	private void processMethodParameter(Node nElement)
	{
		boolean bIsNull = false;
		String sType = m_sXsd+OBJECT_TYPE_STRING;
		String sArrayLength = null;
		if(isNull(nElement)) bIsNull = true;
		NamedNodeMap attParam = nElement.getAttributes();
		Node nObjectType = attParam.getNamedItem(m_sXsi+"type");
		if(nObjectType!=null)
		{
			sType = nObjectType.getNodeValue();
			if(sType.length()==0) sType = null;
		}
		else //assume string if null or ""
		{
			//String s = nElement.getNodeName();
			if(nElement.getNodeName().equalsIgnoreCase(m_sSoapEnc+ELEMENT_TYPE_ARRAY))
			{
				nObjectType = attParam.getNamedItem(m_sSoapEnc+"arrayType");
				if(nObjectType!=null)
				{
					sType = m_sXsd+OBJECT_TYPE_STRING;
					String sArrayType = nObjectType.getNodeValue();
					int iPos = sArrayType.indexOf('[');
					if(iPos>0) sType = sArrayType.substring(0, iPos);
					sArrayLength = sArrayType.substring(iPos+1, sArrayType.length()-1);
				}
				else
					sType = null;
			}
		}//else
		//System.out.println(nElement.getNodeName() + " type="+sType + " arraylength="+sArrayLength);

		dissectData(nElement.getChildNodes(), sType, sArrayLength, bIsNull);
	}

	/**
	 *
	 */
	private void processFault(NodeList nFaultParts)
	{
		//System.out.println("Processing fault...");
		for(int i=0; i<nFaultParts.getLength(); i++)
		{        
			Node nPart = nFaultParts.item(i);
			//System.out.println("fault node: " + nPart.getNodeName());
			if(nPart.getNodeType()==Node.ELEMENT_NODE)
			{
				NodeList nlMessage = nPart.getChildNodes();
				if(nlMessage.getLength()>0)
				{
					Node nMessage = nlMessage.item(0);
					String sText = nMessage.getNodeValue();
					if(nPart.getNodeName().equals(m_sElementPrefix+SOAP_FAULT_CODE)) m_sFaultCode = sText;        
					if(nPart.getNodeName().equals(m_sElementPrefix+SOAP_FAULT_STRING)) m_sFault = sText;
				}
			}
		}
	}

	/**
	 *
	 * @param nlData
	 * @param sType
	 * @param sArrayLength
	 */
	private void dissectData(NodeList nlData, String sType, String sArrayLength, boolean bIsNull)
	{
		String sData="";

		//System.out.println("type="+sType+ " typeid="+getTypeID(sType));
		if(bIsNull)
		{
			addParameter(getTypeID(sType), null);
			return;
		}

		if(sType.equals(m_sXsd+OBJECT_TYPE_BASE64))
		{
			byte btData[] = puakma.util.Util.base64Decode(getData(nlData));
			addParameterObject((Object)btData);
			return;
		}

		if(sType.equals(m_sXsd+OBJECT_TYPE_DATETIME) && sArrayLength==null)
		{
			sData = getData(nlData);
			Date dtParam = puakma.util.Util.makeDate(sData, SOAP_DATE_FORMAT);
			addParameterObject((Object)dtParam);
			return;
		}

		if(sArrayLength==null)
		{
			sData = getData(nlData);
			addParameter(getTypeID(sType), sData);
			return;
		}

		//this is an array :-(
				//process array....
		int iArrayLengths[] = getArrayLengths(sArrayLength);
		int iMaxElements = getMaxElements(iArrayLengths);

		Object oArray = makeObjectArray(getTypeID(sType), iArrayLengths);
		int iStartPos = 0; 
		getArrayData(nlData, getTypeID(sType), oArray, iMaxElements, iArrayLengths, iStartPos);

		addParameterObject(oArray);
	}

	/**
	 *
	 * @param iElements
	 * @return
	 */
	public static int getMaxElements(int iElements[])
	{
		int iTotal=iElements[0];
		for(int i=1; i<iElements.length; i++)   iTotal *= iElements[i];
		return iTotal;
	}

	/**
	 *
	 * @param sArrayLength
	 * @return
	 */
	private int[] getArrayLengths(String sArrayLength)
	{
		StringTokenizer st = new StringTokenizer(sArrayLength, ",");
		int iCommaCount=0;
		int i=0;
		//count how many commas
		while(i<sArrayLength.length())
		{
			if(sArrayLength.charAt(i)==',') iCommaCount++;
			i++;
		}

		int iReturn[] = new int[iCommaCount+1];

		i=0;
		while(st.hasMoreElements())
		{
			String sVal = (String)st.nextElement();
			try{ iReturn[i] = Integer.parseInt(sVal); }catch(Exception e){iReturn[i]=1;}
			i++;
		}

		return iReturn;
	}


	/**
	 * Adds the appropriate object to the internal parameter list
	 * @param iType
	 * @param sData
	 */
	private void addParameter(int iType, String sData)
	{
		try
		{
			switch(iType)
			{
			case OBJECT_TYPE_INTEGER_ID:
				Integer i = new Integer(sData);
				addParameterObject((Object)i);
				break;
			case OBJECT_TYPE_LONG_ID:
				Long l = new Long(sData);
				addParameterObject((Object)l);
				break;
			case OBJECT_TYPE_FLOAT_ID:
				Float f = new Float(sData);
				addParameterObject((Object)f);
				break;
			case OBJECT_TYPE_DOUBLE_ID:
				Double d = new Double(sData);
				addParameterObject((Object)d);
				break;
			case OBJECT_TYPE_BOOLEAN_ID:
				Boolean b = new Boolean(sData);
				addParameterObject((Object)b);
				break;
			case OBJECT_TYPE_DATETIME_ID:
				Date dt=puakma.util.Util.makeDate(sData, SOAP_DATE_FORMAT);
				addParameterObject((Object)dt);
				break;
			default: //strings and anything else we haven't defined
			addParameterObject((Object)sData);
			};
		}
		catch(Exception e)
		{
			addParameterObject((Object)sData);
		}
	}


	private Object makeObjectArray(int iType, int[] iArrayLengths)
	{
		try
		{
			switch(iType)
			{
			case OBJECT_TYPE_INTEGER_ID:
				return Array.newInstance(Integer.TYPE, iArrayLengths);
			case OBJECT_TYPE_LONG_ID:
				return Array.newInstance(Long.TYPE, iArrayLengths);
			case OBJECT_TYPE_FLOAT_ID:
				return Array.newInstance(Float.TYPE, iArrayLengths);
			case OBJECT_TYPE_DOUBLE_ID:
				return Array.newInstance(Double.TYPE, iArrayLengths);
			case OBJECT_TYPE_BOOLEAN_ID:
				return Array.newInstance(Boolean.TYPE, iArrayLengths);
			case OBJECT_TYPE_DATETIME_ID:
				return Array.newInstance(Date.class, iArrayLengths);

			default: //strings and anything else we haven't defined

			};
			return Array.newInstance(String.class, iArrayLengths);
		}
		catch(Exception e)
		{
			if(m_sysCtx!=null)
				m_sysCtx.doError(e.toString(), this);
			else
				System.err.println("makeObjectArray(int, int[]) ERROR: " + e.toString());
		}

		return null;
	}




	/**
	 * Gets the data for a standard parameter type
	 * @param nlData
	 */
	private String getData(NodeList nlData)
	{
		StringBuilder sbData= new StringBuilder(DEFAULT_SB_SIZE);
		for(int d=0; d<nlData.getLength(); d++) //traverse all data
		{
			Node nData = nlData.item(d);
			if(nData.getNodeType()==Node.TEXT_NODE || nData.getNodeType()==Node.CDATA_SECTION_NODE) sbData.append(nData.getNodeValue());
		}//for d

		return cleanXMLRawText(sbData);
	}



	/**
	 * Get the data for the array items
	 * @param nlData
	 * @param iType
	 */
	private void getArrayData(NodeList nlData, int iType, Object oData, int iMaxLen, int[] iArrayLen, int iStartPos)
	{
		int iElementCount=iStartPos; //offset to start at
		for(int i=0; i<nlData.getLength(); i++) //traverse all data
		{
			Node nData = nlData.item(i);
			if(nData!=null && nData.getNodeType()==Node.ELEMENT_NODE && i>=iElementCount)
			{
				int iSetItem[] = getElementOffset(iElementCount, iArrayLen);
				NodeList nlText = nData.getChildNodes();
				for(int d=0; d<nlText.getLength(); d++)
				{
					Node nText = nlText.item(d);
					if(nText!=null && (nText.getNodeType()==Node.TEXT_NODE || nText.getNodeType()==Node.CDATA_SECTION_NODE) && iElementCount<iMaxLen)
					{
						String sData = cleanXMLRawText(nText.getNodeValue());
						setArrayValue(iType, sData, oData, iSetItem);
					}
				}
				iElementCount++;
			}
		}//for d
	}


	/**
	 * When passed an offset determines the array position that should be set
	 * @param iOffset
	 * @param iArrayShape
	 * @return
	 */
	public static int[] getElementOffset(int iOffset, int iArrayShape[])
	{
		//f#ckin matrix.....
		//i wish i was better at math!! Grrrrr.
		int iLen = iArrayShape.length;
		int iReturn[] = new int[iLen];
		int iBase[] = new int[iLen];
		int iIndex=0;
		//int iCount=0;

		if(iOffset==0) return iReturn;

		//this should be done OUTSIDE this function for better performance //TODO
		//ie, wrap this method with another
		for(int k=iLen-1; k>=0; k--)
		{
			if(iIndex==0)
			{
				iBase[k] = 1;
				iIndex=1;
			}
			else
			{
				iIndex = iArrayShape[k+1]*iIndex;
				iBase[k] = iIndex;
			}
		}

		//now pop in the pieces
		int iRemainder = iOffset;
		for(int i=0; i<iLen; i++)
		{
			double dVal = (double)iRemainder/(double)iBase[i];
			int iVal = (int)Math.floor(dVal);

			if(iVal>0) //then there is a remainder
			{
				if(dVal==1)
				{
					iReturn[i] = iVal;
					break;
				}
				iRemainder = iRemainder - (iVal*iBase[i]);
				iReturn[i] = iVal;
			}
		}

		return iReturn;
	}




	/**
	 * sets the value in the array described by iSetItem
	 * @param iType
	 * @param sData
	 */
	private void setArrayValue(int iType, String sData, Object oData, int iSetItem[])
	{
		Object oToSet = oData;
		//int iPos=0;
		for(int i=0; i<iSetItem.length-1; i++)
		{
			oToSet = Array.get(oToSet, iSetItem[i]);
		}

		switch(iType)
		{
		case OBJECT_TYPE_INTEGER_ID:
			int iValue=0;
			try{iValue = new Integer(sData).intValue();}catch(Exception r){}
			Array.setInt(oToSet, iSetItem[iSetItem.length-1], iValue);
			break;
		case OBJECT_TYPE_LONG_ID:
			long lValue=0;
			try{lValue = new Long(sData).longValue();}catch(Exception r){}
			Array.setLong(oToSet, iSetItem[iSetItem.length-1], lValue);
			break;
		case OBJECT_TYPE_FLOAT_ID:
			float fValue=0;
			try{fValue = new Float(sData).floatValue();}catch(Exception r){}
			Array.setFloat(oToSet, iSetItem[iSetItem.length-1], fValue);
			break;
		case OBJECT_TYPE_DOUBLE_ID:
			double dValue=0;
			try{dValue = new Double(sData).doubleValue();}catch(Exception r){}
			Array.setDouble(oToSet, iSetItem[iSetItem.length-1], dValue);
			break;
		case OBJECT_TYPE_BOOLEAN_ID:
			boolean bValue=false;
			try{bValue = new Boolean(sData).booleanValue();}catch(Exception r){}
			Array.setBoolean(oToSet, iSetItem[iSetItem.length-1], bValue);
			break;
		case OBJECT_TYPE_DATETIME_ID:
			Date dtValue=null;
			dtValue = puakma.util.Util.makeDate(sData, SOAP_DATE_FORMAT);
			Array.set(oToSet, iSetItem[iSetItem.length-1], dtValue);
			break;
		default: //strings and anything else we haven't defined
		Array.set(oToSet, iSetItem[iSetItem.length-1], sData);
		};
	}


	/**
	 * Strips the spaces and crlf's etc from the start and end of strings
	 * @param sData
	 * @return
	 */
	public static String cleanXMLRawText(String sData)
	{
		if(sData==null) return null;
		int iStart=0;
		int iLen = sData.length();
		while(iStart<iLen && sData.charAt(iStart)<=32) iStart++;
		int iEnd=iLen-1;
		while(iEnd>0 && sData.charAt(iEnd)<=32) iEnd--;

		if(iStart>iEnd) return "";
		return sData.substring(iStart, iEnd+1);
	}

	/**
	 *
	 */
	public static String cleanXMLRawText(StringBuilder sbData)
	{
		if(sbData==null) return null;
		int iStart=0;
		int iLen = sbData.length();
		while(iStart<iLen && sbData.charAt(iStart)<=32) iStart++;
		int iEnd=iLen-1;
		while(iEnd>0 && sbData.charAt(iEnd)<=32) iEnd--;

		if(iStart>iEnd) return "";
		return sbData.substring(iStart, iEnd+1);
	}


	private String determineMethod(String sIn)
	{
		int iPos = sIn.indexOf( SEPERATOR );
		if(iPos>0) return sIn.substring(iPos+1, sIn.length());

		return "";
	}



	/**
	 * decodes the body of the SOAP message
	 * @param nHeader
	 */
	private void processHeader(Node nHeader)
	{      
		//determine the prefix <Soap:Envelope> should redice to "Soap"
		m_sElementPrefix = nHeader.getNodeName();
		int iPos = m_sElementPrefix.indexOf(':');
		if(iPos>=0) 
		{
			m_sElementPrefix = m_sElementPrefix.substring(0, iPos) + ':';

		}
		else
			m_sElementPrefix="";
		//System.out.println("m_sElementPrefix="+m_sElementPrefix);

		NamedNodeMap nmAttr = nHeader.getAttributes();
		for(int i=0; i<nmAttr.getLength(); i++)
		{
			Node nAtt = nmAttr.item(i);
			String sName = nAtt.getNodeName();
			String sValue = "";
			try{sValue = nAtt.getNodeValue();}catch(Exception e){}
			if(sName.startsWith(XMLNS)) //use this instead of "xmlns:"
			{
				iPos = sName.indexOf(':');
				if(iPos>0)
					sName = sName.substring(iPos+1); //, sName.length());
				else //no colon, so treat as default namespace
					sName = "";//sName.substring(XMLNS.length()+1);
				//System.out.println(sName + "=" + sValue);
				m_htNamespaceMap.put(sName, sValue);
				if(sValue.equalsIgnoreCase("http://schemas.xmlsoap.org/soap/encoding/")) 
				{
					m_sSoapEnc = sName;
					if(m_sSoapEnc.length()>0) m_sSoapEnc += ':';
				}
				if(sValue.equalsIgnoreCase("http://www.w3.org/2001/XMLSchema")) 
				{
					m_sXsd = sName;
					if(m_sXsd.length()>0) m_sXsd += ':';
				}
				if(sValue.equalsIgnoreCase("http://www.w3.org/2001/XMLSchema-instance")) 
				{
					m_sXsi = sName;
					if(m_sXsi.length()>0) m_sXsi += ':';
				}


			}
		}
	}


	/**
	 *
	 * @return
	 */
	public Object[] getParams()
	{
		if(m_vParams.size()==0) return null;

		Object o[]=new Object[m_vParams.size()];
		for(int i=0; i<m_vParams.size(); i++)
		{
			o[i] = m_vParams.elementAt(i);
		}

		return o;
	}

	/**
	 *
	 * @return
	 */
	public String getMethod()
	{
		return m_Method;
	}

	/**
	 *
	 * @return
	 */
	public String getService()
	{
		return m_Service;
	}

	/**
	 * Determines if the message was parsed correctly
	 * @return
	 */
	public boolean isOK()
	{
		return m_bIsOK;
	}

	/**
	 * Map the string types to a number so we can use a switch on the type if we need to
	 * @param sType
	 * @return
	 */
	public int getTypeID(String sType)
	{
		if(sType.equals(m_sXsd+OBJECT_TYPE_STRING)) return OBJECT_TYPE_STRING_ID;
		if(sType.equals(m_sXsd+OBJECT_TYPE_FLOAT)) return OBJECT_TYPE_FLOAT_ID;
		if(sType.equals(m_sXsd+OBJECT_TYPE_INTEGER)) return OBJECT_TYPE_INTEGER_ID;
		if(sType.equals(m_sXsd+OBJECT_TYPE_LONG)) return OBJECT_TYPE_LONG_ID;
		if(sType.equals(m_sXsd+OBJECT_TYPE_BASE64)) return OBJECT_TYPE_BASE64_ID;
		if(sType.equals(m_sXsd+OBJECT_TYPE_DOUBLE)) return OBJECT_TYPE_DOUBLE_ID;
		if(sType.equals(m_sXsd+OBJECT_TYPE_BOOLEAN)) return OBJECT_TYPE_BOOLEAN_ID;
		if(sType.equals(m_sXsd+OBJECT_TYPE_DATETIME)) return OBJECT_TYPE_DATETIME_ID;

		//DUNNO SO IT MUST BE A STRING
		return OBJECT_TYPE_STRING_ID;
	}

	/**
	 * Map the object types to a number so we can use a switch on the type if we need to
	 * @param oType
	 * @return
	 */
	public int getTypeID(Object oType)
	{
		if(oType==null) return OBJECT_TYPE_STRING_ID;
		if(oType instanceof NullObject) return ((NullObject)oType).getType();
		return getTypeID(oType.getClass());
	}

	/**
	 *
	 * @param c
	 * @return
	 */
	public int getTypeID(Class c)
	{
		if(c.isArray()) c = c.getComponentType();
		String sClassName = c.getName();
		if(sClassName.equals(String.class.getClass().getName())) return OBJECT_TYPE_STRING_ID;
		if(sClassName.equals(Float.class.getName())) return OBJECT_TYPE_FLOAT_ID;
		if(sClassName.equals(float.class.getName())) return OBJECT_TYPE_FLOAT_ID;
		if(sClassName.equals(Integer.class.getName())) return OBJECT_TYPE_INTEGER_ID;
		if(sClassName.equals(int.class.getName())) return OBJECT_TYPE_INTEGER_ID;
		if(sClassName.equals(Long.class.getName())) return OBJECT_TYPE_LONG_ID;
		if(sClassName.equals(long.class.getName())) return OBJECT_TYPE_LONG_ID;
		if(sClassName.equals(Double.class.getName())) return OBJECT_TYPE_DOUBLE_ID;
		if(sClassName.equals(double.class.getName())) return OBJECT_TYPE_DOUBLE_ID;
		if(sClassName.equals(Date.class.getName())) return OBJECT_TYPE_DATETIME_ID;
		if(sClassName.equals(Boolean.class.getName())) return OBJECT_TYPE_BOOLEAN_ID;
		if(sClassName.equals(boolean.class.getName())) return OBJECT_TYPE_BOOLEAN_ID;

		if(sClassName.equals(m_sXsd+OBJECT_TYPE_BASE64)) return OBJECT_TYPE_BASE64_ID;
		//DUNNO SO IT MUST BE  ASTRING
		return OBJECT_TYPE_STRING_ID;
	}



	/**
	 * set the return value for the reply soap message
	 * @param o
	 */
	public synchronized void setObjectReturn(Object o)
	{
		m_oReturn = o;
	}


	/**
	 * set the return value for the reply soap message
	 * @param o
	 */
	public void addParameterObject(Object o)
	{
		m_vParams.add(o);
	}


	/**
	 * Gets all the class properties and packages the message
	 * @return
	 */
	public StringBuilder getSOAPMessage(boolean bAsReply)
	{
		StringBuilder sbReturn = new StringBuilder(DEFAULT_SB_SIZE);

		sbReturn.append("<soapenv:Envelope\r\n");
		sbReturn.append("\txmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\"\r\n");    
		sbReturn.append("\txmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\r\n");
		sbReturn.append("\txmlns:xsd=\"http://www.w3.org/2001/XMLSchema\"\r\n");
		sbReturn.append("\txmlns:soapenc=\"http://schemas.xmlsoap.org/soap/encoding/\">\r\n");

		//header start

		//body start
		sbReturn.append("<soapenv:Body>\r\n");
		sbReturn.append("\t<f:" + m_Method + " xmlns:f=\"urn:" + m_Service + "\" soapenv:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\">\r\n");

		//parameter/reply block here
		if(bAsReply) //return
		{
			if(m_sFaultCode!=null)
			{
				sbReturn.append("\t<soapenv:Fault>\r\n");
				sbReturn.append("\t\t<soapenv:faultcode>" + m_sFaultCode + "</soapenv:faultcode>\r\n");
				sbReturn.append("\t\t<soapenv:faultstring><![CDATA[" + m_sFault + "]]></soapenv:faultstring>\r\n");
				sbReturn.append("\t\t<soapenv:detail />\r\n");
				sbReturn.append("\t</soapenv:Fault>\r\n");
			}
			else
			{
				sbReturn.append("\t\t" + getReturnXML() + "\r\n");
			}
		}
		else //parameters
		{
		}

		sbReturn.append("\t</f:" + m_Method + ">\r\n");
		sbReturn.append("</soapenv:Body>\r\n");
		sbReturn.append("</soapenv:Envelope>\r\n");
		return sbReturn;
	}


	/**
	 * Formats the return object into an XML chunk
	 * @return of XML
	 */
	private String getReturnXML()
	{
		return getObjectRepresentation(m_Method+"Return", m_oReturn, true);
	}

	/**
	 * Format one object into XML
	 * @param sNodeName
	 * @param oData
	 * @return
	 */
	public String getObjectRepresentation(String sNodeName, Object oData, boolean bIsReturn)
	{
		if(oData instanceof VoidReturn) return "<!-- void return-type -->";

		StringBuilder sbReturn = new StringBuilder(DEFAULT_SB_SIZE);
		//String sReturnAppend="Return";
		//String sEncodingStyle = " ";    
		String sXSIType=m_sSoapEnc+"string";
		String sValue="";

		String sNullString = "";
		if(oData==null || oData instanceof NullObject) sNullString = getSOAPNull();

		if(oData!=null && oData.getClass().isArray())
		{
			String sDims="";
			int iArrayType = getArrayTypeID(oData);
			int iDimensions[] = getArrayDimensions(oData);
			sXSIType = getSOAPType(iArrayType);

			if(iArrayType==OBJECT_TYPE_BASE64_ID && iDimensions.length==1)
			{
				sValue = puakma.util.Util.base64Encode((byte[])oData);
				//sbReturn.append("<"+sNodeName+sNullString+" xsi:type=\"" + sXSIType + "\">" + sValue + "</"+sNodeName+">");
				sbReturn.append("<"+sNodeName+sNullString+" "+m_sXsi+"type=\"" + sXSIType + "\">" + sValue + "</"+sNodeName+">");        
			}
			else //plain old array
			{
				sNodeName = m_sSoapEnc+ELEMENT_TYPE_ARRAY;
				for(int i=0; i<iDimensions.length; i++)
				{
					if(sDims.length()==0)
						sDims = String.valueOf(iDimensions[i]);
					else
						sDims += ","+iDimensions[i];
				}
				sValue = getObjectXMLArrayData("item", iArrayType, oData, iDimensions);// eg: "<arrayval>34.5</arrayval>\r\n";
				sbReturn.append("<"+sNodeName+sNullString+" "+m_sSoapEnc+"arrayType=\"" + sXSIType + "[" + sDims + "]\">" + sValue + "\r\n\t\t</"+sNodeName+">");
			}
		}
		else
		{
			int iType = getTypeID(oData);
			switch(iType)
			{
			case OBJECT_TYPE_INTEGER_ID:
				sXSIType = m_sXsd+OBJECT_TYPE_INTEGER;
				if(oData!=null) sValue = oData.toString();
				break;
			case OBJECT_TYPE_LONG_ID:
				sXSIType = m_sXsd+OBJECT_TYPE_LONG;
				if(oData!=null) sValue = oData.toString();
				break;
			case OBJECT_TYPE_FLOAT_ID:
				sXSIType = m_sXsd+OBJECT_TYPE_FLOAT;
				if(oData!=null) sValue = oData.toString();
				break;
			case OBJECT_TYPE_DOUBLE_ID:
				sXSIType = m_sXsd+OBJECT_TYPE_DOUBLE;
				if(oData!=null) sValue = oData.toString();
				break;
			case OBJECT_TYPE_BOOLEAN_ID:
				sXSIType = m_sXsd+OBJECT_TYPE_BOOLEAN;
				if(oData!=null) sValue = oData.toString();
				break;
			case OBJECT_TYPE_DATETIME_ID:
				sXSIType = m_sXsd+OBJECT_TYPE_DATETIME;
				if(oData!=null) sValue = puakma.util.Util.formatDate((Date)oData, SOAP_DATE_FORMAT);
				break;
			default: //strings and anything else we haven't defined
			sXSIType = m_sXsd+OBJECT_TYPE_STRING;
				if(oData!=null) sValue = "<![CDATA[" + oData.toString() + "]]>";
			};
			//sbReturn.append("<"+sNodeName+sNullString+" xsi:type=\"" + sXSIType + "\">" + sValue + "</"+sNodeName+">");
			sbReturn.append("<"+sNodeName+sNullString+" "+m_sXsi+"type=\"" + sXSIType + "\">" + sValue + "</"+sNodeName+">");

		}//else    

		return sbReturn.toString();
	}

	/**
	 *
	 * @param sMeth
	 */
	public synchronized void setMethod(String sMeth)
	{
		m_Method = sMeth;
	}

	/**
	 *
	 * @param sSvc
	 */
	public synchronized void setService(String sSvc)
	{
		m_Service = sSvc;
	}


	/**
	 * set the fault message....
	 * @param sFault
	 */
	public synchronized void setFault(String sFaultCode, String sFault)
	{
		m_sFault = sFault;
		m_sFaultCode = sFaultCode;
	}

	/**
	 * Gets the dimensions of this array Object
	 * @param oData
	 * @return
	 */
	public static int[] getArrayDimensions(Object oData)
	{
		if(oData==null) return null;
		if(!oData.getClass().isArray()) return null;

		ArrayList arr = new ArrayList();
		boolean bMore = true;
		Object oArray=oData;

		int iLen = Array.getLength(oArray);
		arr.add(new Integer(iLen));
		if(iLen==0) bMore = false;
		while(bMore)
		{
			oArray = Array.get(oArray, 0);
			if(oArray==null)
				bMore=false;
			else
			{
				if(oArray.getClass().isArray())
					arr.add(new Integer(Array.getLength(oArray)));
				else
					bMore=false;
			}
		}//while

			int iReturn[] = new int[arr.size()];
		for(int i=0; i<arr.size(); i++) iReturn[i] = ((Integer)arr.get(i)).intValue();
		return iReturn;
	}


	/**
	 * Determine the SOAP type of array object passed.
	 * @param oData
	 * @return
	 */
	public int getArrayTypeID(Object oData)
	{
		String sClass = oData.getClass().getName();
		if(sClass.endsWith(getArrayType((Object)new int[0]))) return OBJECT_TYPE_INTEGER_ID;
		if(sClass.endsWith(getArrayType((Object)new Integer[]{}))) return OBJECT_TYPE_INTEGER_ID;

		if(sClass.endsWith(getArrayType((Object)new long[0]))) return OBJECT_TYPE_LONG_ID;
		if(sClass.endsWith(getArrayType((Object)new Long[]{}))) return OBJECT_TYPE_LONG_ID;

		if(sClass.endsWith(getArrayType((Object)new float[0]))) return OBJECT_TYPE_FLOAT_ID;
		if(sClass.endsWith(getArrayType((Object)new Float[]{}))) return OBJECT_TYPE_FLOAT_ID;

		if(sClass.endsWith(getArrayType((Object)new byte[0]))) return OBJECT_TYPE_BASE64_ID;
		if(sClass.endsWith(getArrayType((Object)new Byte[]{}))) return OBJECT_TYPE_BASE64_ID;

		if(sClass.endsWith(getArrayType((Object)new double[0]))) return OBJECT_TYPE_DOUBLE_ID;
		if(sClass.endsWith(getArrayType((Object)new Double[]{}))) return OBJECT_TYPE_DOUBLE_ID;

		if(sClass.endsWith(getArrayType((Object)new boolean[0]))) return OBJECT_TYPE_BOOLEAN_ID;
		if(sClass.endsWith(getArrayType((Object)new Boolean[]{}))) return OBJECT_TYPE_BOOLEAN_ID;

		if(sClass.equals(getArrayType((Object)new Date[]{}))) return OBJECT_TYPE_DATETIME_ID;

		//DUNNO SO IT MUST BE A STRING
		return OBJECT_TYPE_STRING_ID;
	}

	/**
	 *
	 * @param o
	 * @return
	 */
	public static String getArrayType(Object o)
	{
		return o.getClass().getName();
	}

	/**
	 * Map the string types to a number so we can use a switch on the type if we need to
	 * @param iType
	 * @return
	 */
	public String getSOAPType(int iType)
	{
		if(iType==OBJECT_TYPE_STRING_ID) return m_sXsd+OBJECT_TYPE_STRING;
		if(iType==OBJECT_TYPE_FLOAT_ID) return m_sXsd+OBJECT_TYPE_FLOAT;
		if(iType==OBJECT_TYPE_INTEGER_ID) return m_sXsd+OBJECT_TYPE_INTEGER;
		if(iType==OBJECT_TYPE_BASE64_ID) return m_sXsd+OBJECT_TYPE_BASE64;
		if(iType==OBJECT_TYPE_DOUBLE_ID) return m_sXsd+OBJECT_TYPE_DOUBLE;
		if(iType==OBJECT_TYPE_LONG_ID) return m_sXsd+OBJECT_TYPE_LONG;
		if(iType==OBJECT_TYPE_BOOLEAN_ID) return m_sXsd+OBJECT_TYPE_BOOLEAN;
		if(iType==OBJECT_TYPE_DATETIME_ID) return m_sXsd+OBJECT_TYPE_DATETIME;

		//DUNNO SO IT MUST BE A STRING
		return m_sXsd+OBJECT_TYPE_STRING;
	}


	/**
	 * gets the XML representation of this data
	 * @return
	 */
	public String getObjectXMLArrayData(String sElementName, int iType, Object oData, int iDimensions[])
	{
		if(oData==null) return "";
		StringBuilder sb = new StringBuilder(DEFAULT_SB_SIZE);
		int iMax = getMaxElements(iDimensions);
		String sNull = "";
		for(int i=0; i<iMax; i++)
		{
			sNull = "";
			Object sData = getArrayPointObject(iType, oData, iDimensions, i);

			if(sData==null)
			{
				sData = "";
				sNull = getSOAPNull();
			}
			sb.append("\r\n\t\t\t<"+sElementName + sNull + ">");

			switch(iType)
			{
			case OBJECT_TYPE_STRING_ID:
				sb.append("<![CDATA[" + sData + "]]>");
				break;
			case OBJECT_TYPE_DATETIME_ID:
				sb.append(puakma.util.Util.formatDate((Date)sData, SOAP_DATE_FORMAT));
				break;
			default:
				sb.append(sData);
			break;
			}
			/*      if(iType==OBJECT_TYPE_STRING_ID)
        sb.append("<![CDATA[" + sData + "]]>");
      else
        sb.append(sData);
			 */
			sb.append("</"+sElementName+">");
		}

		return sb.toString();
	}


	/**
	 *
	 * @param iType
	 * @param oData
	 * @param iArrayShape
	 * @param iOffset
	 * @return
	 */
	public Object getArrayPointObject(int iType, Object oData, int iArrayShape[], int iOffset)
	{
		Object oToSet = oData;
		int iToGet[] = getElementOffset(iOffset, iArrayShape);
		for(int i=0; i<iToGet.length-1; i++)
		{
			oToSet = Array.get(oToSet, iToGet[i]);
			if(oToSet==null) return null;
		}
		//in case we do no loops above
		if(oToSet==null) return null;

		switch(iType)
		{
		case OBJECT_TYPE_INTEGER_ID:
			return String.valueOf(Array.getInt(oToSet, iToGet[iToGet.length-1]));
		case OBJECT_TYPE_LONG_ID:
			return String.valueOf(Array.getLong(oToSet, iToGet[iToGet.length-1]));
		case OBJECT_TYPE_FLOAT_ID:
			return String.valueOf(Array.getFloat(oToSet, iToGet[iToGet.length-1]));
		case OBJECT_TYPE_DOUBLE_ID:
			return String.valueOf(Array.getDouble(oToSet, iToGet[iToGet.length-1]));
		case OBJECT_TYPE_BOOLEAN_ID:
			return String.valueOf(Array.getBoolean(oToSet, iToGet[iToGet.length-1]));
		case OBJECT_TYPE_DATETIME_ID:
			return Array.get(oToSet, iToGet[iToGet.length-1]);
		default: //strings and anything else we haven't defined
		};
		Object obj = Array.get(oToSet, iToGet[iToGet.length-1]);
		if(obj==null) return null;
		return String.valueOf(obj);
	}


	/**
	 *
	 */
	public Object getObjectToReturn()
	{
		if(m_sFaultCode!=null) //must be a fault
		{
			String sFault = m_sFault;
			if(sFault==null) sFault = "";
			puakma.SOAP.SOAPFaultException sfe = new puakma.SOAP.SOAPFaultException(m_sFaultCode, sFault);        
			return sfe;
		}
		else //return the first element
		{
			if(m_vParams.size()>0) return m_vParams.elementAt(0);
		}
		return null;
	}
}