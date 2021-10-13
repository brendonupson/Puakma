/** ***************************************************************
HTMLDocumentItem.java
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
package puakma.addin.http.document;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.TimeZone;
import java.util.Vector;

import puakma.addin.http.action.HTTPSessionContext;
import puakma.addin.http.util.HTMLTagTokenizer;
import puakma.system.Cookie;
import puakma.system.DocumentItem;
import puakma.system.DocumentMultiItem;
import puakma.system.SessionContext;
import puakma.system.X500Name;
import puakma.util.Util;

/**
 * This class defines a field on a html document. Puakma uses special
 * tags in the html to denote its fields <P@ ... @P> The purpose of these
 * is so that the document can be rendered with its full compliment of
 * fields, before the action is run on it. Assume special characters are
 * URL encoded (ie %20=' ').
 *
 * create a new one by item = new HTMLDocumentItem("<P@Text ... @P>")
 * 
 */
public class HTMLControl
{
	private String Name;
	private String DefaultValue;
	private String Columns="1";
	private String DisplayAs="";
	private String Size="";
	private String OtherOptions; //TODO probably not good for performance to store these like this in a single String
	private int Type;
	private HTMLDocument pDocument;

	private String DataSource=null;
	private String Connection=null;
	private String FirstChoice=null;
	private String MissingValueText=null;
	private String[] m_sMultiTagOptions=null; //eg "style=\"background-color: #000;\"" to be used in an <option> tag
	private String m_sRawTagHTML;

	public final static int ITEM_TYPE_UNKNOWN=-1;
	public final static int ITEM_TYPE_VOID=0; //no output at all
	public final static int ITEM_TYPE_TEXT=1;
	public final static int ITEM_TYPE_HIDDEN=2;
	public final static int ITEM_TYPE_RADIO=3;
	public final static int ITEM_TYPE_CHECK=4;
	public final static int ITEM_TYPE_LIST=5;
	public final static int ITEM_TYPE_COMBO=6;
	public final static int ITEM_TYPE_TEXTAREA=7;
	public final static int ITEM_TYPE_COMPUTED=8;
	public final static int ITEM_TYPE_PATH=9;
	public final static int ITEM_TYPE_FILE=10;
	public final static int ITEM_TYPE_PASSWORD=11;
	public final static int ITEM_TYPE_BUTTON=12;
	public final static int ITEM_TYPE_FORM=13;
	public final static int ITEM_TYPE_VERSION=14;
	public final static int ITEM_TYPE_PARAMETER=15;
	public final static int ITEM_TYPE_COOKIE=16;
	public final static int ITEM_TYPE_DATE=17;
	public final static int ITEM_TYPE_COMPUTEDDATE=18;
	public final static int ITEM_TYPE_VIEW=19;
	public final static int ITEM_TYPE_COMPUTEDPAGE=20;
	public final static int ITEM_TYPE_CHILDPAGE=21;
	public final static int ITEM_TYPE_HIDESTART=22;
	public final static int ITEM_TYPE_HIDEEND=23;
	public final static int ITEM_TYPE_USERNAME=24;
	public final static int ITEM_TYPE_STRINGTABLE=25;

	public final static String ITEM_NAME_TEXT="TEXT";
	public final static String ITEM_NAME_TEXTAREA="TEXTAREA";
	public final static String ITEM_NAME_HIDDEN="HIDDEN";
	public final static String ITEM_NAME_RADIO="RADIO";
	public final static String ITEM_NAME_CHECK="CHECKBOX";
	public final static String ITEM_NAME_LIST="LIST";
	public final static String ITEM_NAME_COMPUTED="COMPUTED";
	public final static String ITEM_NAME_PATH="PATH";
	public final static String ITEM_NAME_FILE="FILE";
	public final static String ITEM_NAME_PASSWORD="PASSWORD";
	public final static String ITEM_NAME_BUTTON="BUTTON";
	public final static String ITEM_NAME_FORM="FORM";
	public final static String ITEM_NAME_VERSION="VERSION";
	public final static String ITEM_NAME_PARAMETER="PARAMETER";
	public final static String ITEM_NAME_COOKIE="COOKIE";
	public final static String ITEM_NAME_DATE="DATE";
	public final static String ITEM_NAME_COMPUTEDDATE="COMPUTEDDATE";
	public final static String ITEM_NAME_VIEW="VIEW";
	public final static String ITEM_NAME_COMPUTEDPAGE="COMPUTEDPAGE";
	public final static String ITEM_NAME_CHILDPAGE="CHILDPAGE";
	public final static String ITEM_NAME_HIDESTART="HIDESTART";
	public final static String ITEM_NAME_HIDEEND="HIDEEND";
	public final static String ITEM_NAME_USERNAME="USERNAME";
	public final static String ITEM_NAME_STRINGTABLE="ST";

	public final static String FIELD_START="<P@";
	public final static String FIELD_END="@P>";
	public final static String MISSINGVALUEOPTION="MissingValueText";

	public HTMLControl(){}

	/*public static void main(String args[])
	{
		HTMLControl ctrl = new HTMLControl(null, "<@Text name = \"fred\" @P>");
	}*/
	
	
	/**
	 * Create a new object including the p tags at each end
	 * @param paramDocument
	 * @param sTagHTML
	 */
	public HTMLControl(HTMLDocument paramDocument, String sTagHTML)
	{
		pDocument = paramDocument;		
		m_sRawTagHTML = sTagHTML;
		init(m_sRawTagHTML);
	}//end constructor

	/**
	 * Basically resets the object to work with the new document
	 * @param paramDocument
	 */
	public void setParentDocument(HTMLDocument paramDocument)
	{
		pDocument = paramDocument;
		init(m_sRawTagHTML);
	}

	private void init(String sTagHTML) 
	{		
		String sType=null;
		//System.out.println("DEBUG: ---->> '" + szTagHTML + "'");
		if(sTagHTML.startsWith(FIELD_START))
			sTagHTML = sTagHTML.substring(FIELD_START.length(), sTagHTML.length());
		if(sTagHTML.endsWith(FIELD_END))
			sTagHTML = sTagHTML.substring(0, sTagHTML.length()-FIELD_END.length());
		sTagHTML = Util.trimSpaces(sTagHTML);  
		int iPos = sTagHTML.indexOf(' '); //find first space
		if(iPos>0)
		{
			sType = sTagHTML.substring(0, iPos);
			sTagHTML = sTagHTML.substring(iPos+1, sTagHTML.length());
		}
		else //for <P@Path @P>
		{
			sType = sTagHTML;
			sTagHTML = "";
		}

		HTMLTagTokenizer st = new HTMLTagTokenizer(sTagHTML);
		ArrayList arr = new ArrayList();
		while(st.hasMoreTokens())
		{
			String szItem = (String)st.nextElement();
			arr.add(szItem);
		}
		processItem(sType.toUpperCase(), arr);

	}

	/**
	 *
	 */
	private void processItem(String szType, ArrayList al)
	{
		String szSize;
		Type = getTypeFromString(szType);
		if(Type == ITEM_TYPE_LIST) //it's only the size param that's different!
		{
			szSize = getArrayValue("Size", al, false);
			if(szSize.length()==0) Type = ITEM_TYPE_COMBO;
			Size = szSize;
		}
		Name = getArrayValue("Name", al, false);
		dropArrayValue("Name", al);
		DefaultValue = getArrayValue("Value", al, false);
		dropArrayValue("Value", al);
		Columns = Util.trimSpaces(getArrayValue("Cols", al, false));
		dropArrayValue("Cols", al);
		DisplayAs = Util.trimSpaces(getArrayValue("DisplayAs", al, false));
		dropArrayValue("DisplayAs", al);
		Size = Util.trimSpaces(getArrayValue("Size", al, false));
		dropArrayValue("Size", al);

		DataSource = Util.trimSpaces(getArrayValue("DataSource", al, false));
		dropArrayValue("DataSource", al);
		Connection = Util.trimSpaces(getArrayValue("Connection", al, false));
		dropArrayValue("Connection", al);
		FirstChoice = Util.trimSpaces(getArrayValue("FirstChoice", al, false));
		dropArrayValue("FirstChoice", al);
		MissingValueText = Util.trimSpaces(getArrayValue(MISSINGVALUEOPTION, al, true));
		//if(MissingValueText!=null && MissingValueText.length()==0) MissingValueText=null;
		dropArrayValue(MISSINGVALUEOPTION, al);

		String szChoices = getArrayValue("Choices", al, false);
		dropArrayValue("Choices", al);
		setChoiceArray(szChoices);
		String sUseKeyword = getArrayValue("UseKeyword", al, false);
		if(sUseKeyword.length()!=0)
		{
			pDocument.setItemChoices(Name, getKeywordsArray(sUseKeyword));
			if(Type==ITEM_TYPE_LIST || Type==ITEM_TYPE_COMBO || Type==ITEM_TYPE_CHECK || Type==ITEM_TYPE_RADIO)
			{
				//pDocument.setItemChoices(Name, getKeywordsArray(sUseKeyword));
			}
			else //text, computed etc
				DefaultValue = getKeywords(sUseKeyword); //? is this right???
			dropArrayValue("UseKeyword", al);
		}
		
		String sUseStringTable = getArrayValue("UseStringTable", al, false);
		if(sUseStringTable.length()!=0)
		{
			DefaultValue = getStringTableValue();
			dropArrayValue("UseStringTable", al);
		}

		OtherOptions = implodeArray(al);    
	}

	public String getOtherOptions()
	{
		return OtherOptions;
	}

	public void setOtherOptions(String sNewOptions)
	{
		OtherOptions = sNewOptions;
	}

	/**
	 *
	 */
	private void setChoiceArray(String sChoices)
	{
		if(sChoices==null || sChoices.length()==0) return;
		ArrayList ar = Util.splitString(sChoices, ',');

		pDocument.setItemChoices(Name, Util.objectArrayToStringArray(ar.toArray()));
	}

	/**
	 * Lookup the specified keywords entry and make a choices string
	 * @param sKeyword
	 * @return
	 */
	private String getKeywords(String sKeyword)
	{
		return pDocument.makeKeywordChoices(sKeyword, false); //sort by keywordorder
	}

	/**
	 *
	 */
	private String[] getKeywordsArray(String sKeyword)
	{
		return pDocument.makeKeywordChoicesArray(sKeyword, false); //sort by keywordorder
	}


	/**
	 *
	 */
	private String implodeArray(ArrayList al)
	{
		int i;    
		StringBuilder sbReturn = new StringBuilder(al.size()*50);

		for(i=0; i<al.size(); i++)
		{        
			sbReturn.append(' ');
			sbReturn.append((String)al.get(i));
		}
		return sbReturn.toString();
	}

	/**
	 * Removes an entry from the array. the idea is we remove the elements the
	 * class uses, which should leave the javascript & special tags
	 */
	private void dropArrayValue(String szKey, ArrayList al)
	{
		int i;
		String szValue;
		String szUValue;
		String szUKey = szKey.toUpperCase();

		for(i=0; i<al.size(); i++)
		{
			szValue = (String)al.get(i);
			szUValue = szValue.toUpperCase();
			if(szUValue.startsWith(szUKey)) //will drop partial matches!! ie "Namesake"
			{
				al.remove(i);
				szValue=null;
			}
		}
	}

	/**
	 * @deprecated @see getItemAttribute
	 * @param sOptionName
	 * @return
	 */
	public String getItemOption(String sOptionName)
	{
		return getItemAttribute(sOptionName);
	}

	/**
	 * gets the option ie "Size" from an item. Useful to get the onClick events etc.
	 * @param sOptionName
	 * @return "" if the option is not found
	 */
	public String getItemAttribute(String sOptionName)
	{
		if(sOptionName==null) return "";
		if(sOptionName.equalsIgnoreCase("size")) return Size;
		if(sOptionName.equalsIgnoreCase("name")) return Name;
		if(sOptionName.equalsIgnoreCase("value")) return DefaultValue;
		if(sOptionName.equalsIgnoreCase("datasource")) return DataSource;
		if(sOptionName.equalsIgnoreCase("connection")) return Connection;
		if(sOptionName.equalsIgnoreCase("firstchoice")) return FirstChoice;
		if(sOptionName.equalsIgnoreCase(MISSINGVALUEOPTION)) return MissingValueText;


		HTMLTagTokenizer st = new HTMLTagTokenizer(OtherOptions);
		ArrayList arr = new ArrayList();
		while(st.hasMoreTokens())
		{
			String szItem = (String)st.nextElement();      
			arr.add(szItem);
		}

		return getArrayValue(sOptionName, arr, false);
	}


	/**
	 * Sets an attribute in the html p-tag. eg fred="abcd". Any existing attributes
	 * are replaced with the value supplied. If the attribute does not already exist it 
	 * is added to the list
	 * @param sAttributeName
	 * @param sValue
	 */
	public void setItemAttribute(String sAttributeName, String sValue)
	{
		if(sAttributeName==null) return;
		if(sAttributeName.equalsIgnoreCase("size")) 
		{
			Size = sValue;
			return;
		}
		if(sAttributeName.equalsIgnoreCase("name")) 
		{
			Name = sValue;
			return;
		}
		if(sAttributeName.equalsIgnoreCase("value")) 
		{
			DefaultValue = sValue;
			return;
		}
		if(sAttributeName.equalsIgnoreCase("datasource")) 
		{
			DataSource = sValue;
			return;
		}
		if(sAttributeName.equalsIgnoreCase("connection")) 
		{
			Connection = sAttributeName;
			return;
		}
		if(sAttributeName.equalsIgnoreCase("firstchoice")) 
		{
			FirstChoice = sValue;
			return;
		}
		if(sAttributeName.equalsIgnoreCase(MISSINGVALUEOPTION)) 
		{
			MissingValueText = sValue;
			return;
		}
		if(sAttributeName.equalsIgnoreCase("cols")) 
		{
			Columns = sValue;
			return;
		}
		if(sAttributeName.equalsIgnoreCase("type")) return; // the server determines the type of field to create, so ignore


		/*
		 * id="idDate"
		 * format="dd/MM/yy HH:mm"
		 */
		boolean bFound = false;
		HTMLTagTokenizer st = new HTMLTagTokenizer(OtherOptions);
		ArrayList arr = new ArrayList();
		while(st.hasMoreTokens())
		{
			String sItem = (String)st.nextElement();   
			int iPos = sItem.indexOf('=');
			if(iPos>0)
			{
				String sName = sItem.substring(0, iPos);
				if(sName.equalsIgnoreCase(sAttributeName))
				{
					sItem = sAttributeName + "=\"" + sValue + "\"";
					bFound = true;
				}
				//System.out.println(sItem);
			}
			arr.add(sItem);
		}
		if(!bFound) arr.add(sAttributeName + "=\"" + sValue + "\"");

		OtherOptions = implodeArray(arr);
	}

	/**
	 * gets the option ie "Size" from an item. Useful to get the onClick events etc.
	 * @param szOptionName
	 * @return "" if the option is not found
	 * @deprecated
	 */
	public void dropItemOption(String sOptionName)
	{
		HTMLTagTokenizer st = new HTMLTagTokenizer(OtherOptions);
		ArrayList arr = new ArrayList();
		while(st.hasMoreTokens())
		{
			String sItem = (String)st.nextElement();
			if(!sOptionName.equalsIgnoreCase(sItem)) arr.add(sItem);
		}


	}


	/**
	 * Get a value from a pair ie Name="xxxx" where szKey="Name" will
	 * return xxxx, stripping off the quotes (if any)
	 */
	private String getArrayValue(String sKey, ArrayList al, boolean bAllowReturnNull)
	{
		int i;
		String szValue;
		String szUValue;
		String szUKey = sKey.toUpperCase();

		for(i=0; i<al.size(); i++)
		{
			szValue = (String)al.get(i);
			szUValue = szValue.toUpperCase();
			if(szUValue.startsWith(szUKey))
			{
				int iPos = szValue.indexOf('=');
				if(iPos>=0) szValue = szValue.substring(iPos+1, szValue.length());
				if(szValue.charAt(0)=='\"') 
					return Util.stripQuotes(szValue);
				else
					return Util.trimSpaces(szValue);
			}
		}

		if(bAllowReturnNull) return null;

		return "";
	}

	/**
	 * Determine the type of item we are processing
	 */
	public int getTypeFromString(String szType)
	{
		if(szType.equals(ITEM_NAME_STRINGTABLE)) return ITEM_TYPE_STRINGTABLE;
		if(szType.equals(ITEM_NAME_TEXT)) return ITEM_TYPE_TEXT;
		if(szType.equals(ITEM_NAME_COMPUTED)) return ITEM_TYPE_COMPUTED;
		if(szType.equals(ITEM_NAME_HIDDEN)) return ITEM_TYPE_HIDDEN;
		if(szType.equals(ITEM_NAME_RADIO)) return ITEM_TYPE_RADIO;
		if(szType.equals(ITEM_NAME_CHECK)) return ITEM_TYPE_CHECK;
		if(szType.equals(ITEM_NAME_LIST)) return ITEM_TYPE_LIST;
		if(szType.equals(ITEM_NAME_TEXTAREA)) return ITEM_TYPE_TEXTAREA;
		if(szType.equals(ITEM_NAME_PATH)) return ITEM_TYPE_PATH;
		if(szType.equals(ITEM_NAME_FILE)) return ITEM_TYPE_FILE;
		if(szType.equals(ITEM_NAME_PASSWORD)) return ITEM_TYPE_PASSWORD;
		if(szType.equals(ITEM_NAME_BUTTON)) return ITEM_TYPE_BUTTON;
		if(szType.equals(ITEM_NAME_FORM)) return ITEM_TYPE_FORM;
		if(szType.equals(ITEM_NAME_VERSION)) return ITEM_TYPE_VERSION;
		if(szType.equals(ITEM_NAME_PARAMETER)) return ITEM_TYPE_PARAMETER;
		if(szType.equals(ITEM_NAME_COOKIE)) return ITEM_TYPE_COOKIE;
		if(szType.equals(ITEM_NAME_DATE)) return ITEM_TYPE_DATE;
		if(szType.equals(ITEM_NAME_COMPUTEDDATE)) return ITEM_TYPE_COMPUTEDDATE;
		if(szType.equals(ITEM_NAME_VIEW)) return ITEM_TYPE_VIEW;
		if(szType.equals(ITEM_NAME_COMPUTEDPAGE)) return ITEM_TYPE_COMPUTEDPAGE;
		if(szType.equals(ITEM_NAME_CHILDPAGE)) return ITEM_TYPE_CHILDPAGE;
		if(szType.equals(ITEM_NAME_HIDESTART)) return ITEM_TYPE_HIDESTART;
		if(szType.equals(ITEM_NAME_HIDEEND)) return ITEM_TYPE_HIDEEND;
		if(szType.equals(ITEM_NAME_USERNAME)) return ITEM_TYPE_USERNAME;

		return ITEM_TYPE_UNKNOWN;
	}

	/**
	 * Turns the field into its HTML representation
	 */
	public StringBuilder getHTML(boolean bReadMode)
	{
		if(bReadMode)
			return getReadHTML();
		else
			return getEditHTML();
	}

	/**
	 * for <P@Path @P>, returns the appropriate path component specified in the value attribute of the tag.
	 * eg: Group, Application, Action, DesignElementName, Parameters, FileExt
	 */
	private String getPathData()
	{
		String sValue = DefaultValue;
		String sPathToApp = pDocument.rPath.getPathToApplication();
		if(sValue==null || sValue.length()==0) return sPathToApp;

		//Group, Application, Action, DesignElementName, Parameters, FileExt
		if(sValue.equalsIgnoreCase("group")) return pDocument.rPath.Group;
		if(sValue.equalsIgnoreCase("application")) return pDocument.rPath.Application;
		if(sValue.equalsIgnoreCase("action")) return pDocument.rPath.Action;
		if(sValue.equalsIgnoreCase("designelementname")) return pDocument.rPath.DesignElementName;
		if(sValue.equalsIgnoreCase("parameters")) return pDocument.rPath.Parameters;
		if(sValue.equalsIgnoreCase("fileext")) return pDocument.rPath.FileExt;

		return sPathToApp;
	}

	/**
	 * gets the HTML when the document is being READ
	 */
	private StringBuilder getReadHTML()
	{
		//String szReturn="";
		StringBuilder sbReturn = new StringBuilder(8192);
		String sValue = pDocument.getItemValue(Name);
		if(sValue==null) sValue = "";

		switch(Type){
		case ITEM_TYPE_STRINGTABLE:
			sbReturn.append(getStringTableValue());
			break;
		case ITEM_TYPE_PATH:
			sbReturn.append(getPathData());
			break;
		case ITEM_TYPE_PASSWORD:
			sbReturn.append("*****"); //? should we show this at all?
			break;
		case ITEM_TYPE_FILE:
		case ITEM_TYPE_BUTTON:
		case ITEM_TYPE_HIDDEN:
			//szReturn = "";
			break;
		case ITEM_TYPE_TEXT:
			sbReturn.append(sValue);
			break;
		case ITEM_TYPE_DATE:
		case ITEM_TYPE_COMPUTEDDATE:
			sbReturn.append(getFormattedDate(false));
			break;
		case ITEM_TYPE_COOKIE:
			sbReturn.append(getDocumentCookie(Name));
			break;
		case ITEM_TYPE_PARAMETER:
			sbReturn.append(getDocumentParameter(Name));
			break;
		case ITEM_TYPE_COMPUTED:
			sbReturn.append(sValue);
			break;
		case ITEM_TYPE_RADIO:
			//sbReturn.append(sValue);
			sbReturn.append(makeCheckRadioTag(true, true));
			break;
		case ITEM_TYPE_CHECK:
			//sbReturn.append(sValue);
			sbReturn.append(makeCheckRadioTag(false, true));
			break;
		case ITEM_TYPE_TEXTAREA:
			sbReturn.append(sValue);
			break;
		case ITEM_TYPE_LIST:
			//sbReturn.append(sValue);
			sbReturn.append(makeListTag(false, true));
			break;
		case ITEM_TYPE_COMBO:
			//sbReturn.append(sValue);
			sbReturn.append(makeListTag(true, true));
			break;
		case ITEM_TYPE_FORM:
			sbReturn = getFormTagHTML();
			break;
		case ITEM_TYPE_VERSION:
			sbReturn.append(pDocument.getServerInfoHTML());
			break;
		case ITEM_TYPE_VIEW:
			sbReturn.append(renderView());
			break;
		case ITEM_TYPE_COMPUTEDPAGE:
			sbReturn.append(getComputedPageHTML(true));
			break;
		case ITEM_TYPE_CHILDPAGE:
			break;
		case ITEM_TYPE_VOID:
		case ITEM_TYPE_HIDESTART:
		case ITEM_TYPE_HIDEEND:
			break;
		case ITEM_TYPE_USERNAME:
			sbReturn.append(getUserName());
			break;
		default:
			sbReturn.append("<!- PUAKMA_ERROR_CONVERTING_FIELD_TO_HTML Name=" + Name + " -->");
		break;
		};


		//sbReturn.append(szReturn);
		return sbReturn;
	}

	/**
	 * Account for multivalued items
	 */
	private String[] getItemValuesForCompare()
	{
		String sItemValue = pDocument.getItemValue(Name);
		if(sItemValue==null) return null;
		String sValues[] = new String[]{sItemValue};
		DocumentItem di = pDocument.getItem(Name);
		if(di instanceof DocumentMultiItem)
		{
			DocumentMultiItem dmi = (DocumentMultiItem)di;
			Vector v = dmi.getValues();
			if(v!=null)
			{
				sValues = new String[v.size()];
				for(int i=0; i<v.size(); i++)
				{
					sValues[i] = (String) v.get(i);
				}
			}
			return sValues;
		}

		if(di!=null && (di.getType()==DocumentItem.ITEM_TYPE_INTEGER || di.getType()==DocumentItem.ITEM_TYPE_NUMERIC))
		{
			if(pDocument.isItemNull(Name)) return new String[]{""}; //so it won't return a zero
			/*
			if(di.getType()==DocumentItem.ITEM_TYPE_INTEGER)
				sValues = new String[]{String.valueOf(pDocument.getItemIntegerValue(Name))};
			else//numeric 5.330
			{
				NumberFormat nf = null;
				Locale loc = pDocument.pSession.getLocale(); 
				if(loc!=null) nf = NumberFormat.getInstance(loc);
				else nf = NumberFormat.getInstance();
				sValues = new String[]{nf.format(pDocument.getItemNumericValue(Name))};
			}*/
			sValues = new String[]{pDocument.getItemValue(Name)};
		}
		return sValues;
	}

	/*
	private String getItemValueForCompare()
	{
		String sValue = pDocument.getItemValue(Name);
		DocumentItem di = pDocument.getItem(Name);
		if(di!=null && (di.getType()==DocumentItem.ITEM_TYPE_INTEGER || di.getType()==DocumentItem.ITEM_TYPE_NUMERIC))
		{
			if(pDocument.isItemNull(Name)) return ""; //so it won't return a zero
			sValue = String.valueOf(pDocument.getItemIntegerValue(Name));
		}
		return sValue;
	}*/

	/**
	 *
	 */
	private String getUserName()
	{
		HTTPSessionContext pSession = pDocument.getHTTPSessionContext();
		if(pSession==null) return "";
		X500Name nmUser = new X500Name(pSession.getUserName());

		String sVal = DefaultValue;//getItemValueForCompare();
		if(sVal!=null)
		{
			if(sVal.equalsIgnoreCase("canonical")) return nmUser.getCanonicalName();
			if(sVal.equalsIgnoreCase("cn") || sVal.equalsIgnoreCase("common")) return nmUser.getCommonName();
			if(sVal.equalsIgnoreCase("fname")) return pSession.getFirstName();
			if(sVal.equalsIgnoreCase("lname")) return pSession.getLastName();
		}

		return nmUser.getAbbreviatedName();
	}



	/**
	 * gets the HTML when the document is being EDITed
	 */
	private StringBuilder getEditHTML()
	{
		StringBuilder sbReturn=new StringBuilder(8192);
		String szNewSize="";

		String sValue = pDocument.getItemValue(Name);
		/*DocumentItem di = pDocument.getItem(Name);
    if(di!=null && (di.getType()==DocumentItem.ITEM_TYPE_INTEGER || di.getType()==DocumentItem.ITEM_TYPE_NUMERIC))
    {
        sValue = String.valueOf(pDocument.getItemIntegerValue(Name));
    }
    //integer values get comma separated :-(
		 **/
		//String sValue = getItemValueForCompare();
		//System.out.println(Name + ": ["+sValue+"] type:"+Type);
		if(sValue==null) sValue = "";
		if(Size.length()!=0) szNewSize=" size=\"" + Size + '\"';

		switch(Type){
		case ITEM_TYPE_STRINGTABLE:
			sbReturn.append(getStringTableValue());
			break;
		case ITEM_TYPE_PATH:
			sbReturn.append(getPathData());
			break;
		case ITEM_TYPE_BUTTON:
			sbReturn.append("<input name=\"");
			sbReturn.append(Name);
			sbReturn.append("\" type=\"");
			sbReturn.append(getCustomType("button"));
			sbReturn.append("\" value=\"");
			sbReturn.append(parseSpecialChars(sValue));
			sbReturn.append('\"');
			sbReturn.append(OtherOptions);
			sbReturn.append("/>");
			break;
		case ITEM_TYPE_HIDDEN:
			sbReturn.append("<input name=\"");
			sbReturn.append(Name);
			//sbReturn.append("\" type=\"hidden\" value=\"");
			sbReturn.append("\" type=\"");
			sbReturn.append(getCustomType("hidden"));
			sbReturn.append("\" value=\"");
			sbReturn.append(parseSpecialChars(sValue));
			sbReturn.append('\"');
			sbReturn.append(OtherOptions);
			sbReturn.append("/>");
			break;
		case ITEM_TYPE_DATE:
			String szFormat = getItemAttribute("format");
			if(Size.length()==0) 
			{
				int iSize = szFormat.length();
				if(iSize==0) iSize=8; //dd/MM/yy
				szNewSize=" size=\"" + (iSize+2) + '\"';
			}
			sbReturn.append("<input name=\"");
			sbReturn.append(Name);
			//sbReturn.append("\" type=\"text\" value=\"");
			sbReturn.append("\" type=\"");
			sbReturn.append(getCustomType("text"));
			sbReturn.append("\" value=\"");
			sbReturn.append(getFormattedDate(false));
			sbReturn.append('\"');
			sbReturn.append(szNewSize);
			sbReturn.append(OtherOptions);
			sbReturn.append("/>");
			break;
		case ITEM_TYPE_TEXT:
			sbReturn.append("<input name=\"");
			sbReturn.append(Name);
			//sbReturn.append("\" type=\"text\" value=\"");
			sbReturn.append("\" type=\"");
			sbReturn.append(getCustomType("text"));
			sbReturn.append("\" value=\"");
			sbReturn.append(parseSpecialChars(sValue));
			sbReturn.append('\"');
			sbReturn.append(szNewSize);
			sbReturn.append(OtherOptions);
			sbReturn.append("/>");
			break;
		case ITEM_TYPE_PASSWORD:
			sbReturn.append("<input name=\"");
			sbReturn.append(Name);
			//sbReturn.append("\" type=\"password\" value=\"");
			sbReturn.append("\" type=\"");
			sbReturn.append(getCustomType("password"));
			sbReturn.append("\" value=\"");
			sbReturn.append(parseSpecialChars(sValue));
			sbReturn.append('\"');
			sbReturn.append(szNewSize);
			sbReturn.append(OtherOptions);
			sbReturn.append("/>");
			break;
		case ITEM_TYPE_FILE:
			//szReturn = "<input name=\"" + Name + "\" type=\"file\" value=\"" + Value + "\"" + szNewSize + OtherOptions + "/>";
			//value is not used by any current browser BJU 5.Jun.2003
			sbReturn.append("<input name=\"");
			sbReturn.append(Name);
			//sbReturn.append("\" type=\"file\" value=\"\"");
			sbReturn.append("\" type=\"");
			sbReturn.append(getCustomType("file"));
			sbReturn.append("\" value=\"\"");
			sbReturn.append(szNewSize);
			sbReturn.append(OtherOptions);
			sbReturn.append("/>");
			break;
		case ITEM_TYPE_COOKIE:
			sbReturn.append(getDocumentCookie(Name));
			break;
		case ITEM_TYPE_PARAMETER:
			sbReturn.append(getDocumentParameter(Name));
			break;
		case ITEM_TYPE_COMPUTEDDATE:
			sbReturn.append(getFormattedDate(false));
			break;
		case ITEM_TYPE_COMPUTED:
			sbReturn.append(sValue);
			break;
		case ITEM_TYPE_RADIO:
			sbReturn.append(makeCheckRadioTag(true, false));
			break;
		case ITEM_TYPE_CHECK:
			sbReturn.append(makeCheckRadioTag(false, false));
			break;
		case ITEM_TYPE_TEXTAREA:
			sbReturn.append("<textarea name=\"");
			sbReturn.append(Name);
			sbReturn.append("\" cols=\"");
			sbReturn.append(Columns);
			sbReturn.append('\"');
			sbReturn.append(OtherOptions);
			sbReturn.append('>');
			sbReturn.append(getEscapedText());
			sbReturn.append("</textarea>");
			break;
		case ITEM_TYPE_LIST:
			sbReturn.append(makeListTag(false, false));
			break;
		case ITEM_TYPE_COMBO:
			sbReturn.append(makeListTag(true, false));
			break;
		case ITEM_TYPE_FORM:
			sbReturn = getFormTagHTML();
			break;
		case ITEM_TYPE_VERSION:
			sbReturn.append(pDocument.getServerInfoHTML());
			break;
		case ITEM_TYPE_VIEW:
			sbReturn.append(renderView());
			break;
		case ITEM_TYPE_COMPUTEDPAGE:
			sbReturn.append(getComputedPageHTML(false));
			break;
		case ITEM_TYPE_CHILDPAGE:
			break;
		case ITEM_TYPE_VOID:
		case ITEM_TYPE_HIDESTART:
		case ITEM_TYPE_HIDEEND:
			break;
		case ITEM_TYPE_USERNAME:
			sbReturn.append(getUserName());
			break;
		default:
			sbReturn.append("<!-- ERROR_CONVERTING_FIELD_TO_HTML Name=\"");
		sbReturn.append(Name);
		sbReturn.append("\" -->");
		break;
		};

		return sbReturn;
	}

	/**
	 * 
	 * @param sDefaultType
	 * @return
	 */
	private String getCustomType(String sDefaultType) 
	{
		String sType = getItemAttribute("type");
		if(sType!=null && sType.length()>0) return sType;

		return sDefaultType;
	}

	/**
	 * Get a value from the string table, localized
	 * @return
	 */
	private String getStringTableValue() 
	{
		HTTPSessionContext pSession =  pDocument.getHTTPSessionContext();
		if(pSession==null) return "ST:["+Name+"]";
		
		return pSession.getStringTableEntry(Name, null);
	}
	
	

	/**
	 * For textareas, replace all "<" with "&lt;", and ">" with "&gt;". Typically this is used
	 * when displaying html pages inside a textarea tag
	 *
	 */
	private String getEscapedText()
	{
		String sValue = pDocument.getItemValue(Name);
		if(sValue==null || sValue.length()==0) return sValue;
		//if(DefaultValue==null || DefaultValue.length()==0) return DefaultValue;
		//now replace &nbsp; etc with &amp;nbsp;
		//added 8/4/06
		String sWorkValue = sValue.replaceAll("(&)([a-zA-Z]{2,}+;)", "&amp;$2");
		sWorkValue = sWorkValue.replaceAll("(&)(#[0-9]{2,}+;)", "&amp;$2");

		int iPos=sWorkValue.indexOf('<');
		while(iPos>=0)
		{
			String sStart = sWorkValue.substring(0, iPos);
			String sEnd = sWorkValue.substring(iPos+1);
			sWorkValue = sStart + "&lt;" + sEnd;
			iPos=sWorkValue.indexOf('<');
		}
		
		iPos=sWorkValue.indexOf('>');
		while(iPos>=0)
		{
			String sStart = sWorkValue.substring(0, iPos);
			String sEnd = sWorkValue.substring(iPos+1);
			sWorkValue = sStart + "&gt;" + sEnd;
			iPos=sWorkValue.indexOf('>');
		}

		
		return sWorkValue;
	}

	/**
	 *
	 */
	private StringBuilder getComputedPageHTML(boolean bReadMode)
	{
		StringBuilder sbPage=new StringBuilder(8192);
		HTTPSessionContext pSession =  pDocument.getHTTPSessionContext();
		String sValue = pDocument.getItemValue(Name);
		if(sValue==null) sValue = "";
		if(sValue!=null && sValue.length()>0)
		{
			DesignElement de = pSession.getDesignObject(sValue, DesignElement.DESIGN_TYPE_PAGE);
			if(de==null) return sbPage;
			try
			{
				//System.out.println("--- getComputedPageHTML ----");
				/*HTMLDocument docTemp = (HTMLDocument)pDocument.clone();
              docTemp.setContent((byte[])null);                          
              docTemp.designObject = de;
              docTemp.prepare(null);
              docTemp.renderDocument(bReadMode, false);
				 */

				de.removeParsedDocumentParts();

				HTMLDocument docTemp = new HTMLDocument(pSession);
				docTemp.rPath = pDocument.rPath;
				pDocument.copyAllItems(docTemp);
				docTemp.setContent((byte[])null);                          
				docTemp.designObject = de;
										
				docTemp.prepare();
				pDocument.copyControls(docTemp, true);				
				docTemp.renderDocument(bReadMode, false);
				//docTemp.copyAllNewItems(pDocument);		
				/*
				docTemp.prepare();
				docTemp.renderDocument(bReadMode, false);
				docTemp.copyAllNewItems(pDocument);
				*/
			              
				byte buf[] = docTemp.getContent();
				if(buf!=null)
				{
					String sComputedPage = "";  
					try{sComputedPage = new String(buf, docTemp.getCharacterEncoding()); }
					catch(Exception r){ sComputedPage = new String(buf); }
					sbPage.append("<input type=\"hidden\" name=\""+Name+"\" value=\""+ parseSpecialChars(sValue)+"\"/>");
					sbPage.append(sComputedPage);
				}
			}
			catch(Exception e){ e.printStackTrace(); }

		}
		return sbPage;
	}

	/**
	 *
	 */
	public StringBuilder getFormTagHTML()
	{
		//String szEncType="";
		StringBuilder sbForm=new StringBuilder(150);
		//String szTo=rPath.getPathToDesign() + "?SavePage";
		sbForm.append("<form");
		if(pDocument.hasRichItems()) sbForm.append(" enctype=\"multipart/form-data\"");

/*
		if(false) //bj
		{
			sbForm.append(" name=\"");
			//if the developer specified a name, use it
			if(Name!=null && Name.length()>0)
				sbForm.append(Name);
			else //otherwise use the name of the page
			{
				if(pDocument.designObject!=null) sbForm.append(pDocument.designObject.getDesignName());
			}
			sbForm.append("\"");
		}*/
		sbForm.append(" method=\"post\" action=\"");
		sbForm.append(pDocument.rPath.getPathToDesign());
		sbForm.append("?SavePage\""); 

		if(OtherOptions!=null && OtherOptions.length()>0)
		{
			if(OtherOptions.charAt(0)!=' ') sbForm.append(' ');
			sbForm.append(OtherOptions);
		}
		sbForm.append('>');
		return sbForm;
	}


	/*private boolean matchesValue(String sOption)
	{
		String sValue = getItemValueForCompare();
		if(sValue==null) sValue="";
		if(sOption==null) sOption="";

		if(sOption.equals(sValue)) return true;

		int iPos = sValue.indexOf(',');
		if(iPos>=0) //is a single choice ie: "green"
		{			
			//it must be a multivalued default ie: "1,2,3"
			StringTokenizer st = new StringTokenizer(sValue, ",");
			while(st.hasMoreTokens())
			{
				String sDef = (String)st.nextElement();
				if(sOption.equals(sDef)) return true;
			}
		}

		return false;
	}*/

	private boolean matchesValue(String sOption)
	{
		String sValues[] = getItemValuesForCompare();
		if(sValues==null || sValues.length==0) sValues=new String[]{""};
		if(sOption==null) sOption="";

		for(int i=0; i<sValues.length; i++)
		{
			//System.out.println(i + ". "+ Name + " [" + sValues[i] + "]=opt:["+sOption+"]");
			if(sOption.equals(sValues[i])) return true;
		}

		/*
		int iPos = sValue.indexOf(',');
		if(iPos>=0) //is a single choice ie: "green"
		{			
			//it must be a multivalued default ie: "1,2,3"
			StringTokenizer st = new StringTokenizer(sValue, ",");
			while(st.hasMoreTokens())
			{
				String sDef = (String)st.nextElement();
				if(sOption.equals(sDef)) return true;
			}
		}*/

		return false;
	}


	/**
	 * 
	 * @param bRadio
	 * @param bReadMode
	 * @return
	 */
	private StringBuilder makeCheckRadioTag(boolean bRadio, boolean bReadMode)
	{
		StringBuilder sb = new StringBuilder(8192);    
		String sWhat="checkbox";
		boolean bAllowsMultipleSelections=true;
		boolean bSelectionMade=false;
		int iCols=1;
		if(bRadio) 
		{
			sWhat = "radio";
			bAllowsMultipleSelections = false;
		}
		boolean bValueIsInList = false;

		String sChoices[] = pDocument.getItemChoices(Name);
		if(sChoices==null || sChoices.length==0) 
		{
			sChoices = getChoicesFromConnection(DataSource, Connection); 
		}

		if(sChoices!=null && sChoices.length>0)
		{
			HTMLControlChoice choices[] = HTMLControlChoice.makeChoiceArray(sChoices, null);
			ArrayList arrItems = new ArrayList(choices.length);
			try{ iCols=Integer.parseInt(Columns); }catch(Exception e){}
			if(iCols<1) iCols = 1;

			for(int i=0; i<choices.length; i++)
			{
				boolean bSelected = false;
				if(matchesValue(choices[i].getAliasText())) bSelected = true;
				if(!bAllowsMultipleSelections && bSelectionMade) bSelected = false;

				if(bSelected)
				{
					bValueIsInList = true;
					bSelectionMade = true;
				}

				if(bReadMode)
				{
					if(bSelected)
					{
						arrItems.add(choices[i].getDisplayText());
					}
				}
				else//edit mode
				{		
					sb.delete(0, sb.length());
					sb.append("<input name=\"");
					sb.append(Name);
					sb.append("\" type=\"");
					sb.append(sWhat);
					sb.append("\" value=\"");
					sb.append(choices[i].getAliasText());
					sb.append('\"');

					if(bSelected) 
					{
						sb.append(" checked=\"checked\"");
						bValueIsInList = true;						
					}
					sb.append(OtherOptions);
					sb.append("/>");
					sb.append(choices[i].getDisplayText());
					arrItems.add(sb.toString());
				}
			}//for

			//if value is not in the list, check if the tag supports dynamically adding it
			if(!bValueIsInList)
			{        
				if(MissingValueText!=null)
				{
					String sValue = pDocument.getItemValue(Name);
					if(bReadMode)
					{
						if(MissingValueText.length()==0)
							arrItems.add(sValue);
						else
							arrItems.add(MissingValueText);
					}
					else //edit mode
					{
						sb.delete(0, sb.length());
						if(sValue==null) sValue="";
						sb.append("<input name=\"");
						sb.append(Name);
						sb.append("\" type=\"");
						sb.append(sWhat);
						sb.append("\" value=\"");
						sb.append(sValue);
						sb.append("\" checked=\"checked\"");
						sb.append(OtherOptions);
						sb.append("/>");
						if(MissingValueText.length()==0)
							sb.append(sValue);							
						else
							sb.append(MissingValueText);
						arrItems.add(sb.toString());
					}
				}
			}

			sChoices = null; //release memory			

			sb.delete(0, sb.length());
			int iCount=1;
			if(DisplayAs!=null && DisplayAs.equalsIgnoreCase("table"))
			{
				sb.append("<table class=\"inputContainer\">\r\n");
				sb.append("<tr>");
				for(int i=0; i<arrItems.size(); i++)
				{
					sb.append("<td>");
					sb.append((String)arrItems.get(i));
					sb.append("</td>");

					if(iCount>=iCols)
					{
						sb.append("</tr>\r\n");
						if(i!=(choices.length-1)) sb.append("<tr>");
						iCount = 1;
					}
					else
						iCount++;					
				}
				if(iCount>1) //add empty cells if required to complete a row
				{
					for(int k=iCount; k<=iCols; k++) sb.append("<td></td>");
				}

				sb.append("</tr>\r\n");
				sb.append("</table>\r\n");
			}
			else //default rendering with break tags
			{

				for(int i=0; i<arrItems.size(); i++)
				{
					sb.append((String)arrItems.get(i));
					if(iCount>=iCols)
					{
						//add a break tag if not the last one....
						if(i!=(choices.length-1))
						{
							sb.append("<br/>\r\n");
							iCount = 1;
						}
					}
					else
						iCount++;
				}
			}

		}//if has choices

		return sb;
	}





	/**
	 * returns true if this tag (eg list) supports multiple selections ' multiple="1"', 'multiple' or similar
	 * attributes added
	 */
	public boolean tagSupportsMultipleSelections()
	{
		if(OtherOptions==null || OtherOptions.length()==0) return false;

		//check for correctly specified tag parameter eg multiple="1"
		String sMultiple = getItemAttribute("multiple");
		if(sMultiple!=null && sMultiple.length()>0) return true; 

		//now check for other (non-xhtml) bad ways multiple may have been specified....
		String sLowOptions = OtherOptions.toLowerCase();
		if(sLowOptions.indexOf(" multiple ")>=0) return true;
		if(sLowOptions.startsWith("multiple ")) return true;
		if(sLowOptions.endsWith(" multiple")) return true;

		return false;
	}

	/**
	 * New and improved method for making lists and combo boxes
	 * @param bCombo
	 * @param bReadMode
	 * @return
	 */
	private StringBuilder makeListTag(boolean bCombo, boolean bReadMode)
	{
		//long lStart = System.currentTimeMillis();
		boolean bAllowsMultipleSelections = false;
		if(!bCombo && tagSupportsMultipleSelections()) bAllowsMultipleSelections = true;

		String sFirstChoiceToUse = FirstChoice;
		String sChoices[] = pDocument.getItemChoices(Name);
		if(sChoices==null || sChoices.length==0) 
		{
			sChoices = getChoicesFromConnection(DataSource, Connection);			
			sFirstChoiceToUse = null;
			if(sChoices==null || sChoices.length==0) return new StringBuilder(); //nothing to do			
		}

		HTMLControlChoice choices[] = HTMLControlChoice.makeChoiceArray(sChoices, sFirstChoiceToUse);



		StringBuilder sbReturn = new StringBuilder(choices.length*30); //rough memory allocation
		if(!bReadMode)
		{
			sbReturn.append("<select name=\"");
			sbReturn.append(Name);
			sbReturn.append('\"');
			if(!bCombo)	sbReturn.append(" size=\"" + Size + '\"');			
			sbReturn.append(OtherOptions);
			sbReturn.append('>');
		}

		boolean bSelectionMade = false;
		boolean bValueIsInList = false;
		for(int i=0; i<choices.length; i++)
		{
			boolean bSelected = false;
			if(matchesValue(choices[i].getAliasText())) bSelected = true;
			if(!bAllowsMultipleSelections && bSelectionMade) bSelected = false;

			if(bSelected)
			{
				bValueIsInList = true;
				bSelectionMade = true;
			}
			if(bReadMode)
			{
				if(bSelected)
				{
					if(sbReturn.length()>0) sbReturn.append("<br/>\r\n");
					sbReturn.append(choices[i].getDisplayText());
				}
			}
			else//edit mode
			{				
				sbReturn.append(choices[i].getAsOption(bSelected, getAdditionalTagAttributes(i)));
			}
		}

//		if value is not in the list, check if the tag supports dynamically adding it
		if(!bValueIsInList)
		{        
			if(MissingValueText!=null)
			{
				String sValue = pDocument.getItemValue(Name);
				if(sValue==null) sValue="";  
				if(bReadMode)
				{
					if(MissingValueText.length()==0)
						sbReturn.append(sValue);
					else
						sbReturn.append(MissingValueText);
				}
				else
				{
					sbReturn.append("<option value=\"");
					sbReturn.append(HTMLControlChoice.getEscapedTextForValueAttribute(sValue));            
					sbReturn.append("\" selected=\"selected\">");
					if(MissingValueText.length()==0)
						sbReturn.append(sValue);
					else
						sbReturn.append(MissingValueText);

					sbReturn.append("</option>");
				}
			}
		}  

		if(!bReadMode)
		{
			sbReturn.append("</select>");
		}
		//long lDiff = System.currentTimeMillis() - lStart;
		//System.out.println(Name + " took " + lDiff + "ms to make " + choices.length + " choices");
		return sbReturn;
	}

	/**
	 * Set the internal additional attributes.  "style=\"background-color: #000;\"" to be used in an <option> tag
	 * Note that this will only have an effect on tags that generate multiple html elements
	 * @param sNewAttributes
	 */
	public synchronized void setAdditionalTagAttributes(String sNewAttributes[])
	{
		m_sMultiTagOptions = sNewAttributes;
	}

	/**
	 * 
	 * @param iChoicePosition
	 * @return
	 */
	private String getAdditionalTagAttributes(int iChoicePosition) 
	{
		if(m_sMultiTagOptions==null || m_sMultiTagOptions.length<=iChoicePosition) return null;
		return m_sMultiTagOptions[iChoicePosition];
	}



	/**
	 * Get the name of this item
	 */
	public String getName()
	{
		return Name;
	}

	/**
	 * Get the type of this item
	 */
	public int getType()
	{
		return Type;
	}

	/**
	 * Determines if the control can receive multiple values. Checks and lists
	 * @return
	 */
	public boolean isMultiValued()
	{
		if(Type==ITEM_TYPE_CHECK) return true;
		if(Type==ITEM_TYPE_LIST) return tagSupportsMultipleSelections();

		return false;
	}

	/**
	 *
	 */
	private String getFormattedDate(boolean bUseDefault)
	{
		if(getType()==ITEM_TYPE_DATE || getType()==ITEM_TYPE_COMPUTEDDATE)
		{
			SessionContext sess = pDocument.getSessionContext();
			TimeZone tz = TimeZone.getDefault();
			if(sess!=null && sess.getTimeZone()!=null) tz = sess.getTimeZone();
			String sValue = DefaultValue;
			java.util.Date dtToProcess=null;
			if(!bUseDefault) 
			{
				dtToProcess=pDocument.getItemDateValue(Name);
				sValue = pDocument.getItemValue(Name);
			}
			if(sValue==null) sValue="";
			boolean bProcessed=false;

			if(dtToProcess==null)
			{
				Calendar cal = Calendar.getInstance(tz);				
				java.util.Date dtNow = cal.getTime();//new java.util.Date();

				if(sValue.equalsIgnoreCase("today") || sValue.equalsIgnoreCase("now")) { bProcessed=true; dtToProcess = dtNow;}
				if(!bProcessed && sValue.equalsIgnoreCase("tomorrow")) { bProcessed=true; dtToProcess = Util.adjustDate(dtNow, 0, 0, 1, 0, 0, 0); }
				if(!bProcessed && sValue.equalsIgnoreCase("yesterday")) { bProcessed=true; dtToProcess = Util.adjustDate(dtNow, 0, 0, -1, 0, 0, 0); }
				if(!bProcessed && sValue.equalsIgnoreCase("nextweek")) { bProcessed=true; dtToProcess = Util.adjustDate(dtNow, 0, 0, 7, 0, 0, 0); }
				if(!bProcessed && sValue.equalsIgnoreCase("lastweek")) { bProcessed=true; dtToProcess = Util.adjustDate(dtNow, 0, 0, -7, 0, 0, 0); }
				if(!bProcessed && sValue.equalsIgnoreCase("nextmonth")) { bProcessed=true; dtToProcess = Util.adjustDate(dtNow, 0, 1, 0, 0, 0, 0); }
				if(!bProcessed && sValue.equalsIgnoreCase("lastmonth")) { bProcessed=true; dtToProcess = Util.adjustDate(dtNow, 0, -1, 0, 0, 0, 0); }
				if(!bProcessed && sValue.equalsIgnoreCase("nextyear")) { bProcessed=true; dtToProcess = Util.adjustDate(dtNow, 1, 0, 0, 0, 0, 0); }
				if(!bProcessed && sValue.equalsIgnoreCase("lastyear")) { bProcessed=true; dtToProcess = Util.adjustDate(dtNow, -1, 0, 0, 0, 0, 0); }
			}
			if(dtToProcess!=null)
			{
				String sFormat = getItemAttribute("format");
				if(sess!=null)
				{
					//if there is a session object of the same name as the format, use it
					String sObj = (String)sess.getSessionObject(sFormat);
					if(sObj!=null && sObj.length()>0) sFormat = sObj;
				}
				if(sFormat==null) sFormat = Util.SHORT_DATE;

				return Util.formatDate(dtToProcess, sFormat, sess.getLocale(), sess.getTimeZone());
				/*String sLocale = getItemAttribute("locale");
				SimpleDateFormat sdf;
				//locales as per http://ftp.ics.uci.edu/pub/ietf/http/related/iso639.txt
				Locale locale = Locale.getDefault();            
				if(sLocale!=null && sLocale.length()>0) locale = new Locale(sLocale);				
				if(sess!=null && sess.getLocale()!=null) locale = sess.getLocale();

				if(sFormat==null || sFormat.length()==0)
					sdf = new SimpleDateFormat();
				else
					sdf = new SimpleDateFormat(sFormat, locale);

				sdf.setTimeZone(tz);

				try
				{
					//DateFormat default:= sdf.getDateInstance(DateFormat.
					return sdf.format(dtToProcess);
				}
				catch(Exception e) {}*/
			}
		}//if a DATE type
		return "";
	}

	/**
	 *
	 */
	public String getDefaultValue()
	{
		if(getType()==ITEM_TYPE_DATE 
				|| getType()==ITEM_TYPE_COMPUTEDDATE) return getFormattedDate(true);
		return DefaultValue;
	}

	public synchronized void setName(String sNewName)
	{
		Name = sNewName;
	}

	public synchronized void setValue(String newValue)
	{
		if(newValue==null) newValue="";
		DefaultValue = newValue;
	}

	/**
	 * @deprecated
	 * BJU 26/9/05
	 */
	public synchronized void setChoices(String sChoices)
	{   
		pDocument.setItemChoices(Name, sChoices);
		//setChoiceArray(szChoices);
	}

	/**
	 * @deprecated
	 * BJU 26/9/05
	 */
	public synchronized void setChoices(String[] sChoices)
	{
		pDocument.setItemChoices(Name, sChoices);
		//Choices = szChoices;
	}


	/**
	 * Get the choices of this item (combos, lists, etc
	 */
	public String[] getChoices()
	{
		//System.out.println("getting choices for "+this.Name);
		//if(Choices!=null) System.out.println("#"+Choices.length);
		//return Choices;
		return pDocument.getItemChoices(Name);
	}

	public synchronized void setType(int iNewType)
	{
		Type = iNewType;
	}

	public synchronized void setSize(String szNewSize)
	{
		Size = szNewSize;
	}



	/**
	 * A nice wrapper in case parameter returns null
	 */
	public String getDocumentParameter(String szName)
	{
		String szReturn = pDocument.getParameter(szName);
		if(szReturn==null) return DefaultValue; //use the tag value
		return szReturn;
	}

	/**
	 * A nice wrapper in case parameter returns null
	 */
	public String getDocumentCookie(String szName)
	{
		Cookie cook = pDocument.getCookie(szName);
		if(cook==null) return "";
		if(cook.getValue()==null) return DefaultValue; //use the tag value
		return cook.getValue();
	}

	/**
	 * return true if a file attachment or text area
	 */
	public boolean isRichItem()
	{
		if(Type==ITEM_TYPE_FILE || Type==ITEM_TYPE_TEXTAREA) return true;
		return false;
	}


	/**
	 * Remove any special chars from the string so that the value="xyz" tags don't get broken
	 * in the browser.
	 */
	public static String parseSpecialChars(String sIn)
	{
		if(sIn.indexOf('\"')<0) return sIn;      
		String sReturn = sIn;

		sReturn = sReturn.replaceAll("\"", "&quot;");
		return sReturn;
	}

	/**
	 * Open the named data connection and execute the sql command to get the list
	 * of choices.
	 */
	private String[] getChoicesFromConnection(String sSQL, String sConn)
	{            
		if(sSQL==null || sSQL.length()==0) return null;
		TableManager t = new TableManager(pDocument.getHTTPSessionContext(), sConn, "");
		if(FirstChoice==null || FirstChoice.length()==0) 
			return t.makeChoicesArray(sSQL);
		else
			return t.makeChoicesArray(sSQL, FirstChoice);
	}

	/**
	 * Render the view.
	 */
	private String renderView()
	{
		int iMax=-1;
		int iNavStyle=0;

		String sXSL = getItemAttribute("xslstylesheet");
		String sNextText = getItemAttribute("nexttext");
		if(sNextText==null || sNextText.length()==0) sNextText = pDocument.getSystemContext().getSystemMessageString("HTMLView.NextRecordset");
		if(sNextText==null) sNextText = "&gt;&gt;";

		String sPrevText = getItemAttribute("previoustext");
		if(sPrevText==null || sPrevText.length()==0) sPrevText = pDocument.getSystemContext().getSystemMessageString("HTMLView.PreviousRecordset");
		if(sPrevText==null) sPrevText = "&lt;&lt;";

		String sMax = getItemAttribute("maxperpage");
		if(sMax!=null && sMax.length()>0)
		{
			try{ iMax = Integer.parseInt(sMax);} catch(Exception r){}
		}
		String sNavStyle = getItemAttribute("navigationstyle");
		if(sNavStyle!=null && sNavStyle.length()>0)
		{
			try{ iNavStyle = Integer.parseInt(sNavStyle);} catch(Exception r){}
		}


		/*System.out.println("sql="+sSQL);
      System.out.println("xsl="+sXSL);      
      System.out.println("conn="+sConn);
      System.out.println("next="+sNextText);
      System.out.println("prev="+sPrevText);
      System.out.println("Max="+iMax);
		 */
		HTMLView vwData = new HTMLView(pDocument, sXSL, DataSource, Connection, sNextText, sPrevText);
		if(iMax>0) vwData.setRowsPerView(iMax);
		vwData.setNavigationStyle(iNavStyle);
		return vwData.getViewHTML(this.Name);            
	}

	public String getRawTag() 
	{
		return m_sRawTagHTML;
	}

}//end class