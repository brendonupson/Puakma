/** ***************************************************************
Util.java
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

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.io.Serializable;
import java.io.StringWriter;
import java.security.MessageDigest;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.text.DateFormat;
import java.text.NumberFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.Hashtable;
import java.util.Locale;
import java.util.TimeZone;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import javax.naming.NamingEnumeration;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import puakma.coder.CoderB64;
import puakma.system.SystemContext;

public class Util
{
	public static final String SHORT_DATE = "shortdate";
	public static final String LONG_DATE = "longdate";
	public static final String SHORT_DATE_TIME = "shortdatetime";
	public static final String LONG_DATE_TIME = "longdatetime";

	/**
	 * Strips the leading and trailing quotes from a string
	 * "xxxx" will return xxxx
	 * @param sString the string to remove the quotes from
	 */
	public static String stripQuotes(String sString)
	{		
		return trimChar(sString, '\"');
	}

	/**
	 * Strips the leading and trailing spaces from a string
	 * " xxxx  " will return "xxxx"
	 */
	public static String trimSpaces(String sString)
	{
		return trimChar(sString, ' ');
	}

	/**
	 * A more efficient trimming algorithm for stripping a single character (eg space)
	 * from the beginning and end of a string
	 * @param sString
	 * @param c
	 * @return
	 */
	public static String trimChar(String sString, char c)
	{
		/*if(sString==null) return null;
		int iLen = sString.length();
		if(iLen==0) return sString;

		StringBuilder sbData = new StringBuilder(512);
		sbData.append(sString);
		int iStart=0;

		while(iStart<iLen && sbData.charAt(iStart)==c) iStart++;
		int iEnd=iLen-1;
		while(iEnd>0 && sbData.charAt(iEnd)==c) iEnd--;

		if(iStart>iEnd) return "";
		return sbData.substring(iStart, iEnd+1);
		 */
		return trimChar(sString, new char[]{c});
	}


	/**
	 * Trims any of the listed characters from the beginning and end of strings. Useful to get rid of eg '\n', '\r', and ' '
	 * all at the same time
	 * @param sString
	 * @param c
	 * @return
	 */
	public static String trimChar(String sString, char c[])
	{
		if(sString==null) return null;
		if(c==null) return sString;
		int iLen = sString.length();
		if(iLen==0) return sString;

		StringBuilder sbData = new StringBuilder(iLen);
		sbData.append(sString);
		int iStart=0;

		while(iStart<iLen && isCharInList(sbData.charAt(iStart), c) ) iStart++;
		int iEnd=iLen-1;
		while(iEnd>0 && isCharInList(sbData.charAt(iEnd), c) ) iEnd--;

		if(iStart>iEnd) return "";
		return sbData.substring(iStart, iEnd+1);
	}


	/**
	 * Returns true if cTest appears in cList
	 * @param cTest
	 * @param cList
	 * @return
	 */
	public static boolean isCharInList(char cTest, char cList[])
	{
		if(cList==null || cList.length==0) return false;
		for(int i=0; i<cList.length; i++)
		{
			if(cTest==cList[i]) return true;
		}

		return false;
	}


	/**
	 * Determines if the string passed is a valid representation of a whole number.
	 * Internally tried to convert to a long, if successful returns true. USeful for checking URL
	 * parameters to see if they are valid whole numbers.
	 * @return true if the input is a valid long eg "32998"
	 *
	 */
	public static boolean isInteger(String sInput)
	{
		if(sInput==null) return false;
		sInput = Util.trimSpaces(sInput);

		try{
			Long.parseLong(sInput);
			return true;          
		}
		catch(Exception e)
		{}

		// now try "23,089"
		try
		{     
			NumberFormat nf = NumberFormat.getInstance();
			Number n = nf.parse(sInput); 
			double d = n.doubleValue();
			long l = n.longValue();        
			if(l==d) return true; //no remainder, thus a whole integer
		}
		catch(ParseException pe){}

		return false;
	}

	public static boolean isNumeric(String sInput)
	{
		if(sInput==null) return false;
		sInput = Util.trimSpaces(sInput);

		try{
			Long.parseLong(sInput);
			return true;          
		}
		catch(Exception e)
		{}

		// now try "23,089"
		try
		{     
			NumberFormat nf = NumberFormat.getInstance();
			nf.parse(sInput); 
			//double d = n.doubleValue();

			return true; 
		}
		catch(ParseException pe){}

		return false;
	}

	/**
	 * Converts a String value to a long. Supports pure int values eg "34"
	 * and floating points eg "34.0". Floating point numbers must have no remainder.
	 * @param sInput
	 * @return
	 */
	public static long toInteger(String sInput)
	{
		if(sInput==null) return 0;
		sInput = Util.trimSpaces(sInput);

		try{
			return Long.parseLong(sInput);			         
		}
		catch(Exception e)
		{}

		// now try "23,089"
		try
		{     
			NumberFormat nf = NumberFormat.getInstance();
			Number n = nf.parse(sInput); 
			double d = n.doubleValue();
			long l = n.longValue();        
			if(l==d) return l; //no remainder, thus a whole integer
		}
		catch(ParseException pe){}

		return 0;
	}

	/**
	 * Minifies a block of JavaScript code. Assume charset is utf-8.
	 * @param bufCode
	 * @return
	 */
	public static byte[] minifyJSCode(byte bufCode[])
	{
		try 
		{
			ByteArrayInputStream bais = new ByteArrayInputStream(bufCode);
			ByteArrayOutputStream baos = new ByteArrayOutputStream((int)(bufCode.length*0.9));
			JSMin jsmin = new JSMin(bais, baos);
			jsmin.jsmin();
			return baos.toByteArray();
		} 
		catch (Exception e) 
		{
			//System.err.println(e.toString());
			e.printStackTrace();			
		}
		return bufCode;
	}


	/**
	 * Strips the leading and trailing spaces from a string
	 * " xxxx  " will return "xxxx"
	 */
	public static String trimChars(String sString, String sToTrim)
	{
		String sReturn=sString;
		int iToTrimLength=sToTrim.length();

		while(sReturn.startsWith(sToTrim)) sReturn = sReturn.substring(iToTrimLength, sReturn.length());
		while(sReturn.endsWith(sToTrim)) sReturn = sReturn.substring(0, sReturn.length()-iToTrimLength);

		return sReturn;
	}

	/**
	 * Convert a date into a format suitable for http headers etc "EEE, dd MMM yyyy HH:mm:ss z"
	 * @param dtIn
	 * @return
	 */
	public static String toGMTString(Date dtIn)
	{
		if(dtIn==null) dtIn = new Date();
		String sGMTFormat = "EEE, dd MMM yyyy HH:mm:ss z";
		return formatDate(dtIn, sGMTFormat, Locale.UK, TimeZone.getTimeZone("GMT"));
	}

	/**
	 * Gets a line of text with the specified key
	 * some browsers (like opera) split stuff across 2 lines
	 * The next related line starts with a space
	 * TODO: possibly make this account for multiple split lines? Does any browser do this??
	 */
	public static String getMIMELine(ArrayList v, String sKey)
	{
		String sLine2="";
		int iOffset;
		if(sKey==null) return null;

		for(int i=0; i<v.size(); i++)
		{
			String sLine = (String)v.get(i);
			iOffset = sLine.indexOf(':');
			if( iOffset > 0 && sKey.equalsIgnoreCase(sLine.substring(0, iOffset)) )
			{
				//+1 to skip the space after the : ie, "Content-Length: 25"
				if(iOffset+2>sLine.length()) return ""; //check for java.lang.StringIndexOutOfBoundsException
				sLine = sLine.substring(iOffset+2, sLine.length());
				if(i<v.size()-1) //check for another line!
				{
					sLine2 = (String)v.get(i+1);
					if(sLine2.length()>0 && sLine2.charAt(0)!=' ') sLine2 = "";
				}
				return sLine + sLine2;
			}
		}//for

		return null; //not found
	}


	/**
	 *
	 */
	public static String[] getAllMIMELines(ArrayList v, String sKey)
	{
		String szLine, szLine2="";
		int iOffset;
		if(sKey==null) return null;
		ArrayList arrReturn = new ArrayList();

		for(int i=0; i<v.size(); i++)
		{
			szLine = (String)v.get(i);
			iOffset = szLine.indexOf(':');
			if( iOffset > 0 && sKey.equalsIgnoreCase(szLine.substring(0, iOffset)) )
			{
				//+1 to skip the space after the : ie, "Content-Length: 25"
				szLine = szLine.substring(iOffset+2, szLine.length());
				if(i<v.size()-1) //check for another line!
				{
					szLine2 = (String)v.get(i+1);
					if(szLine2.length()>0 && szLine2.charAt(0)!=' ') szLine2 = "";
				}
				arrReturn.add(szLine + szLine2);
			}
		}//for

		if(arrReturn.size()==0) return null; //not found

		return objectArrayToStringArray(arrReturn.toArray());
	}

	/**
	 * Gets a value from a line of text from a mime header, ie name="xxx"
	 * will return "xxx". Just pass the name (szName) of the item you want, not the
	 * entire token, ie "name"
	 * @return null if the item does not exist
	 */
	public static String getMIMELineValue(String sLine, String sName)
	{
		int iOffset;
		if(sName==null || sLine==null) return null;
		sName = sName + "=";

		iOffset = sLine.indexOf(sName);
		if(iOffset>=0)
		{
			sLine = sLine.substring(iOffset+sName.length(), sLine.length());
			if(sLine.length()>0 && sLine.charAt(0)=='\"')
			{
				sLine = sLine.substring(1, sLine.length());
				iOffset = sLine.indexOf('\"');
				if(iOffset>=0)
					sLine = sLine.substring(0, iOffset);
			}
			else //assume a ; is the delimiter
			{
				iOffset = sLine.indexOf(';');
				if(iOffset>=0)
					sLine = sLine.substring(0, iOffset);
			}

			return sLine;
		}
		return null;
	}

	/**
	 * Puts a value in the http header. Pass a null value to remove the header completely
	 */
	public static void replaceHeaderValue(ArrayList environment_lines, String sHeader, String sValue)
	{
		String sFind = sHeader.toLowerCase()+':';
		for(int i=0; i<environment_lines.size(); i++)
		{
			String s = (String)environment_lines.get(i);
			if(s.toLowerCase().startsWith(sFind))
			{
				environment_lines.remove(i);
				if(sValue!=null) environment_lines.add(sHeader+": "+sValue);
				return;
			}
		}
		//couldn't find the header so add it...
		if(sValue!=null) environment_lines.add(sHeader+": "+sValue);
	}

	/**
	 * Gets the time in milliseconds from a datetime in GMT format as per HTTP header format
	 * @param A date in the format "EEE, dd MMM yyyy HH:mm:ss z" eg "Wed, 09 Jan 2008 23:59:59 GMT"
	 */
	public static long getDateMSFromGMTString(String sGMT)
	{    
		final String LAST_MOD_DATE = "EEE, dd MMM yyyy HH:mm:ss z";
		Date dt = makeDate(sGMT, LAST_MOD_DATE, Locale.UK, TimeZone.getTimeZone("GMT"));
		if(dt==null) return 0;
		return dt.getTime();
	}


	public static void main(String s[])
	{
		/*String sNum = "2287.0";
		//System.out.println( sNum + " isInteger=" + isInteger(sNum) + " isNumeric=" + isNumeric(sNum) + " int="+toInteger(sNum));
		//String sValue = new String(tci.Value);
		if(Util.isNumeric(sNum)) System.out.println("OK:" + Util.toInteger(sNum));
		else System.out.println("ERR!");
		*/
		/*try {
			System.out.println(Util.getMXAddress("wnc.com.au"));
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}*/
		
		//String sTestDate = "Fri, 25 Oct 2013 09:19:01 GMT";
		/*String sTestDate = " Tue, 26 Nov 2013 23:59:59 GMT";
		long lExpiryDate = puakma.util.Util.getDateMSFromGMTString(sTestDate);
		Date dtTest = new Date();
		dtTest.setTime(lExpiryDate);
		System.out.println("lExpiryDate="+lExpiryDate + " = " + sTestDate + " = " + dtTest);
		*/
		
		/*
		 * long lNow = System.currentTimeMillis();
				long lExpiryDate = puakma.util.Util.getDateMSFromGMTString(sExpires);
				//if expires>now
				if(lExpiryDate<lNow) 
				{
		 */
		
		String sDesc = " - Terry Smith";
		char cTrim[] = new char[]{' ', '-', ',', '.', ':', '#', ';', '/', '~',
				'0', '1', '2', '3', '4', '5', '6', '7', '8', '9'};
		System.out.println(sDesc + "=" +  Util.trimChar(sDesc, cTrim));
		
	}


	/**
	 * Splits a delimited String into a ArrayList of individual components
	 * example: ArrayList v = puakma.util.Util.splitString("NSW,VIC,QLD", ",");
	 * @return null if either of the inputs are null
	 */
	public static ArrayList<String> splitString(String sInput, String sSeparator)
	{
		if(sInput==null || sSeparator==null) return null;
		ArrayList<String> vReturn = new ArrayList<String>();

		int iPos = sInput.indexOf(sSeparator);
		while(iPos>=0)
		{
			String szToken = sInput.substring(0, iPos);
			sInput = sInput.substring(iPos+sSeparator.length(), sInput.length());
			vReturn.add(szToken);
			iPos = sInput.indexOf(sSeparator);
		}
		if(sInput.length()!=0) vReturn.add(sInput);

		return vReturn;
	}

	/**
	 * Converts an array or collection into a single string. eg "1","2","3" with a sSeparator of ","
	 * will return "1,2,3"
	 * @param strings a Collection or String[] to be imploded
	 * @param sSeparator if null, no separator is used
	 * @return null if there is invalid input data, otherwise a single String of the imploded data.
	 */
	public static String implode(Object strings, String sSeparator) 
	{
		String sSourceData[] = null;
		StringBuilder sbReturn = new StringBuilder(64);

		if(strings==null) return null;
		if(sSeparator==null) sSeparator = "";

		if(strings instanceof Collection)
		{
			Collection coll = (Collection)strings;
			sSourceData = Util.objectArrayToStringArray(coll.toArray());
		}
		if(strings instanceof String[])
		{
			sSourceData = (String[])strings;
		}

		//couldn't convert the input object
		if(sSourceData==null) return null;

		for(int i=0; i<sSourceData.length; i++)
		{
			if(sbReturn.length()>0) sbReturn.append(sSeparator);
			sbReturn.append(sSourceData[i]);
		}

		return sbReturn.toString();
	}

	/**
	 * Splits a delimited String into a ArrayList of individual components.
	 * This verison uses an arraylist and a single char, so is a little faster.
	 * example: ArrayList a = puakma.util.Util.splitString("NSW,VIC,QLD", ',');
	 * @return null if either of the inputs are null
	 */
	public static ArrayList splitString(String sInput, char cChar)
	{
		if(sInput==null) return null;
		ArrayList vReturn = new ArrayList();

		int iPos = sInput.indexOf(cChar);
		while(iPos>=0)
		{
			String szToken = sInput.substring(0, iPos);
			sInput = sInput.substring(iPos+1, sInput.length());
			vReturn.add(szToken);
			iPos = sInput.indexOf(cChar);
		}
		if(sInput.length()!=0) vReturn.add(sInput);

		return vReturn;
	}


	/**
	 * Hashes a string into a SHA fingerprint of bytes, then converts those bytes to a
	 * Base64 string. Useful for performing a one way hash of a password 
	 * @param sToEncode
	 * @return
	 */
	public static String encryptString(String sToEncode)
	{		
		try
		{
			CoderB64 encoder = new CoderB64() ;
			return encoder.encode(hashBytes(sToEncode.getBytes("UTF8")));
		}
		catch(Exception e){}

		return null;
	}


	/**
	 * 
	 * @param bufIn
	 * @return
	 */
	public static byte[] hashBytes(byte bufIn[])
	{      
		try
		{
			MessageDigest md = MessageDigest.getInstance("SHA") ;
			md.update(bufIn);
			return md.digest();
		}
		catch(Exception e)
		{

		}
		return null;
	}


	/**
	 * Easy way to base64 encode
	 * @param bIn
	 * @return a base64 encoded string
	 */
	public static String base64Encode(byte bIn[])
	{
		if(bIn==null) return null;
		CoderB64 encoder = new CoderB64();
		String sOut = encoder.encode(bIn);
		return sOut;
	}

	/**
	 * Easy way to base64 encode
	 * @param sIn
	 * @return a base64 encoded string
	 */
	public static byte[] base64Decode(String sIn)
	{
		if(sIn==null) return null;
		CoderB64 encoder = new CoderB64();
		return encoder.decode(sIn.getBytes());
	}



	/**
	 * Converts a resultset item into a java.util.Date. This includes the date and time components
	 * @param rs
	 * @param sColumnName
	 * @return
	 * @throws Exception
	 */
	public static Date getResultSetDateValue(ResultSet rs, String sColumnName) throws Exception
	{
		Timestamp ts = rs.getTimestamp(sColumnName);
		if(ts!=null) return new Date(ts.getTime());
		return null;
	}


	/**
	 * Determines if a column exists within the given resultset
	 * @param rs
	 * @param sColumnName
	 * @return true if the column exists. Case insensitive
	 */
	public static boolean resultSetHasColumn(ResultSet rs, String sColumnName) throws Exception
	{
		if(rs==null) return false;
		ResultSetMetaData rsmd = rs.getMetaData();
		int iCols = rsmd.getColumnCount();
		for(int i=1; i<=iCols; i++) //cols count 1 through n
		{
			if(rsmd.getColumnName(i).equalsIgnoreCase(sColumnName)) return true;
		}
		return false;
	}




	/**
	 * Convert a string date into a proper Date object
	 * @param sDate
	 * @return a date object representing the string
	 */
	public static Date makeDate(String sDate, String sFormatString)
	{
		return makeDate(sDate, sFormatString, null, null);
	}

	/**
	 * Convert a text date into a Date object based on the timezone and local passed. Null tz and locale
	 * will result in the server's timezone and locale being used
	 * @param sDate
	 * @param sFormatString
	 * @param locale may be null
	 * @param tz may be null
	 * @return
	 */
	public static Date makeDate(String sDate, String sFormatString, Locale locale, TimeZone tz)
	{
		if(locale==null) locale = Locale.getDefault();
		if(tz==null) tz = TimeZone.getDefault();

		if(sFormatString.indexOf("date")>=0)
		{
			DateFormat df = null; 
			// shortdate, longdate etc
			if(sFormatString.equalsIgnoreCase(SHORT_DATE))
			{

				if(locale!=null) 
					df = DateFormat.getDateInstance(DateFormat.SHORT, locale);
				else
					df = DateFormat.getDateInstance(DateFormat.SHORT);				
			}

			if(df==null && sFormatString.equalsIgnoreCase(SHORT_DATE_TIME))
			{				
				if(locale!=null) 
					df = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, locale);
				else
					df = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT);				
			}

			if(df==null && sFormatString.equalsIgnoreCase(LONG_DATE))
			{				
				if(locale!=null) 
					df = DateFormat.getDateInstance(DateFormat.LONG, locale);
				else
					df = DateFormat.getDateInstance(DateFormat.LONG);				
			}

			if(df==null && sFormatString.equalsIgnoreCase(LONG_DATE_TIME))
			{				
				if(locale!=null) 
					df = DateFormat.getDateTimeInstance(DateFormat.LONG, DateFormat.SHORT, locale);
				else
					df = DateFormat.getDateTimeInstance(DateFormat.LONG, DateFormat.SHORT);
								
			}

			if(df!=null)
			{
				if(tz!=null) df.setTimeZone(tz);
				try{
					return df.parse(sDate);
				}catch(Exception de){}
			}
		}



		SimpleDateFormat sdf;
		if(sFormatString==null || sFormatString.length()==0)
			sdf = new SimpleDateFormat();
		else
			sdf = new SimpleDateFormat(sFormatString, locale);

		sdf.setTimeZone(tz);

		try
		{
			return sdf.parse(sDate);
		}
		catch(Exception p){}

		return null;
	}

	/**
	 * Format dates, convenience method
	 * @param dtFormat
	 * @param sFormatString
	 * @return
	 */
	public static String formatDate(java.util.Date dtFormat, String sFormatString)
	{
		return formatDate(dtFormat, sFormatString, Locale.getDefault(), TimeZone.getDefault());
	}

	/**
	 * Convert a date into a formatted String. This will automatically do the timezone
	 * conversion too.
	 * To convert to a GMT string:
	 * puakma.util.Util.formatDate(new java.util.Date(), "EEE, dd MMM yyyy HH:mm:ss z", Locale.UK, TimeZone.getTimeZone("GMT"));
	 *
	 * @param dtFormat Date to format; dtFormat; Locale (for languages); TimeZone
	 * @param sFormatString
	 * @param locale may be null
	 * @param tz may be null
	 * @return the string representation of the date of "" if the date could not be converted
	 */
	public static String formatDate(Date dtFormat, String sFormatString, Locale locale, TimeZone tz)
	{
		if(sFormatString==null) sFormatString = "";

		if(dtFormat==null) return "";

		if(sFormatString.indexOf("date")>=0)
		{
			// shortdate, longdate etc
			if(sFormatString.equalsIgnoreCase(SHORT_DATE))
			{
				DateFormat df = null; 
				if(locale!=null) 
					df = DateFormat.getDateInstance(DateFormat.SHORT, locale);
				else
					df = DateFormat.getDateInstance(DateFormat.SHORT);
				if(tz!=null) df.setTimeZone(tz);
				return df.format(dtFormat);
			}

			if(sFormatString.equalsIgnoreCase(SHORT_DATE_TIME))
			{
				DateFormat df = null; 
				if(locale!=null) 
					df = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, locale);
				else
					df = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT);
				if(tz!=null) df.setTimeZone(tz);
				return df.format(dtFormat);
			}

			if(sFormatString.equalsIgnoreCase(LONG_DATE))
			{
				DateFormat df = null; 
				if(locale!=null) 
					df = DateFormat.getDateInstance(DateFormat.LONG, locale);
				else
					df = DateFormat.getDateInstance(DateFormat.LONG);
				if(tz!=null) df.setTimeZone(tz);
				return df.format(dtFormat);
			}

			if(sFormatString.equalsIgnoreCase(LONG_DATE_TIME))
			{
				DateFormat df = null; 
				if(locale!=null) 
					df = DateFormat.getDateTimeInstance(DateFormat.LONG, DateFormat.SHORT, locale);
				else
					df = DateFormat.getDateTimeInstance(DateFormat.LONG, DateFormat.SHORT);
				if(tz!=null) df.setTimeZone(tz);
				return df.format(dtFormat);
			}
			//we should never get here...
		}

		//regular formatting, eg "dd/MM/yy"
		SimpleDateFormat sdf;
		if(sFormatString.length()==0)
			sdf = new SimpleDateFormat();
		else
		{
			if(locale==null)
				sdf = new SimpleDateFormat(sFormatString);
			else
				sdf = new SimpleDateFormat(sFormatString, locale);
		}
		if(tz!=null) sdf.setTimeZone(tz);

		try
		{
			return sdf.format(dtFormat);
		}
		catch(Exception p){}

		return "";
	}

	/**
	 * Removed as of build 455 22/10/2004, replaced by {@link #adjustDate(java.util.Date dtDate, int iYears, int iMonths, int iDays, int iHours, int iMinutes, int iSeconds)}
	 * Foolishly I created this method a long time ago with an uppercase name.
	 * Adjusts the Date passed in by the appropriate amounts.
	 * @param dtDate
	 * @param iYears
	 * @param iMonths
	 * @param iDays
	 * @param iHours
	 * @param iMinutes
	 * @param iSeconds
	 * @return
	 */
	/*
  public static java.util.Date AdjustDate(java.util.Date dtDate, int iYears, int iMonths, int iDays, int iHours, int iMinutes, int iSeconds)
  {
    // removed from version 2.93 onward.
    return adjustDate(dtDate, iYears, iMonths, iDays, iHours, iMinutes, iSeconds);
  }
	 */

	/**
	 * Adjusts the Date passed in by the appropriate amounts.
	 * @param dtDate
	 * @param iYears
	 * @param iMonths
	 * @param iDays
	 * @param iHours
	 * @param iMinutes
	 * @param iSeconds
	 * @return
	 */
	public static java.util.Date adjustDate(Date dtDate, int iYears, int iMonths, int iDays, int iHours, int iMinutes, int iSeconds)
	{
		if(dtDate == null) return null;
		Calendar cal = Calendar.getInstance();
		Calendar calNew = Calendar.getInstance();
		cal.setTime(dtDate);
		calNew.setTime(dtDate);//to ensure milliseconds are copied
		calNew.set(cal.get(Calendar.YEAR)+iYears, cal.get(Calendar.MONTH)+iMonths, cal.get(Calendar.DAY_OF_MONTH)+iDays, cal.get(Calendar.HOUR_OF_DAY)+iHours, cal.get(Calendar.MINUTE)+iMinutes, cal.get(Calendar.SECOND)+iSeconds);    
		//dtAdjusted = new java.util.Date(calNew.getTime().getTime());
		//return dtAdjusted;
		return calNew.getTime();
	}

	/**
	 * Sets the time component of a Date to all 0's
	 */
	public static Date clearTime(Date dtIn)
	{
		Calendar cal = Calendar.getInstance();
		cal.setTime(dtIn);
		cal.set(Calendar.HOUR_OF_DAY, 0);
		cal.set(Calendar.MINUTE, 0);
		cal.set(Calendar.SECOND, 0);
		cal.set(Calendar.MILLISECOND, 0);

		return cal.getTime();
	}

	/**
	 * Joins two byte arrays together
	 * @param src
	 * @param append
	 * @return
	 */
	public static byte[] appendBytes(byte src[], byte append[])
	{
		if(src==null && append==null) return null;
		if(src==null) return append;
		if(append==null) return src;

		byte bufReturn[] = new byte[src.length+append.length];
		System.arraycopy(src, 0, bufReturn, 0, src.length);
		System.arraycopy(append, 0, bufReturn, src.length, append.length);

		return bufReturn;
	}

	/**
	 * Joins two char arrays together
	 * @param src
	 * @param append
	 * @return
	 */
	public static char[] appendChars(char src[], char append[])
	{
		if(src==null && append==null) return null;
		if(src==null) return append;
		if(append==null) return src;

		char bufReturn[] = new char[src.length+append.length];
		System.arraycopy(src, 0, bufReturn, 0, src.length);
		System.arraycopy(append, 0, bufReturn, src.length, append.length);

		return bufReturn;
	}


	/**
	 * Returns the position of one byte array within another.
	 * @param src
	 * @param find
	 * @return -1 if not found
	 */
	public static int indexOf(byte src[], byte find[])
	{
		if(src==null || find==null || find.length==0) return -1;
		int i=-1;
		for(i=0; i<src.length; i++)
		{
			if(src[i]==find[0])
			{
				boolean bOK=true;
				for(int k=0; k<find.length; k++)
				{
					int iSrcPos = i+k;
					if(iSrcPos>=src.length) return -1;
					if(src[iSrcPos]!=find[k]) bOK=false;
				}//for k
				if(bOK) return i;
			}
		}//for i

		return -1;
	}

	/**
	 * Returns the position of one byte array within another.
	 * @param src
	 * @param find
	 * @return -1 if not found
	 */
	public static int indexOf(char src[], char find[])
	{
		if(src==null || find==null || find.length==0) return -1;
		int i=-1;
		for(i=0; i<src.length; i++)
		{
			if(src[i]==find[0])
			{
				boolean bOK=true;
				for(int k=0; k<find.length; k++)
				{
					int iSrcPos = i+k;
					if(iSrcPos>=src.length) return -1;
					if(src[iSrcPos]!=find[k]) bOK=false;
				}//for k
				if(bOK) return i;
			}
		}//for i

		return -1;
	}


	/**
	 * Can be called when no systemContext is available
	 * @param xmlData
	 * @param xslData
	 * @return
	 */
	public static StringBuilder xmlTransform(StringBuilder xmlData, StringBuilder xslData)
	{
		return xmlTransform(null, xmlData, xslData);
	}

	/**
	 * Transforms an xml document with an xsl stylesheet
	 * @param pSystem
	 * @param xmlData
	 * @param xslData
	 * @return
	 */
	public static StringBuilder xmlTransform(SystemContext pSystem, StringBuilder xmlData, StringBuilder xslData)
	{
		StringBuilder sbReturn = new StringBuilder(2048);

		if(xmlData==null || xslData==null) return null;
		try
		{
			TransformerFactory tFactory = TransformerFactory.newInstance();
			Transformer transformer = tFactory.newTransformer( new StreamSource(new ByteArrayInputStream(xslData.toString().getBytes("UTF-8"))) );
			StreamSource source = new StreamSource(new ByteArrayInputStream(xmlData.toString().getBytes("UTF-8")));
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			StreamResult res = new StreamResult(baos);
			transformer.transform(source, res);
			sbReturn.append(baos.toString());
		}
		catch(Exception e)
		{
			if(pSystem!=null) pSystem.doError(e.toString(), pSystem);
			return null;
		}
		return sbReturn;
	}

	/**
	 * Serialize an object into a byte array
	 * @param ser
	 * @return
	 * @throws Exception
	 */
	public static byte[] serializeObject( Serializable ser ) throws Exception
	{
		if(ser==null) return null;
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		ObjectOutputStream oos = new ObjectOutputStream( baos );
		oos.writeObject( ser );
		byte[] retval = baos.toByteArray();
		return retval;
	}

	/**
	 * Converts an array of Objects into an array of Strings. This method is useful 
	 * for converting the output of a Vector or ArrayList into a String array suitable for combo
	 * and list fields.
	 */
	public static String[] objectArrayToStringArray(Object[] array)
	{
		if(array==null) return null;
		String[] output = new String[array.length];

		for(int i=0; i<array.length; i++)
		{                       
			output[i] = String.valueOf(array[i]);
		}       

		return output;
	}


	/**
	 * Gets the MX address for a given host. For round-robin DNS, this retrieves
	 * only the first entry. We rely on the DNS server to supply its prefered
	 * choice.
	 * Will return the correct address or throw an exception
	 */
	public static String getMXAddress(String sMXHost) throws Exception
	{
		final String MX_RECORD="MX";
		String sResult = null;
		Hashtable htJNDIEnv = new Hashtable();      
		htJNDIEnv.put("java.naming.factory.initial", "com.sun.jndi.dns.DnsContextFactory");

		DirContext ictx = new InitialDirContext(htJNDIEnv);
		Attributes a = ictx.getAttributes(sMXHost, new String[] { "MX" });
		NamingEnumeration all = a.getAll();
		if( all.hasMore() ) 
		{
			Attribute attr = (Attribute)all.next();
			String sAttr = attr.getID();
			if(sAttr==null || !sAttr.equalsIgnoreCase(MX_RECORD)) throw new Exception("Util.getMXAddress() DNS lookup failed.");         
			if(attr.size()<1) throw new Exception("Util.getMXAddress() No addresses returned.");         

			sResult = (String)attr.get(0);
			int iPos = sResult.indexOf(' ');
			int iTrimFactor=0;
			if(sResult.charAt(sResult.length()-1)=='.') iTrimFactor=1;
			// MX string has priority (lower better) followed by associated mailserver
			//eg "5 mx3.hotmail.com." so we need to remove the trailing .
			if(iPos>0) sResult = sResult.substring(iPos+1, sResult.length()-iTrimFactor);

		}//if
		if(sResult==null) throw new Exception("Util.getMXAddress() Domain not found: "+sMXHost);

		return sResult;
	}          

	/**
	 * Compress the byte array using the gzip algorithm
	 *
	 */
	public static byte[] gzipBuffer(byte[] bufInput)
	{
		byte bufReturn[] = null;
		if(bufInput==null || bufInput.length==0) return bufInput;

		try 
		{
			ByteArrayOutputStream baos = new ByteArrayOutputStream(bufInput.length);            
			GZIPOutputStream gzos =  new GZIPOutputStream(baos, 2048);
			gzos.write(bufInput);
			gzos.flush();
			gzos.finish();
			gzos.close();
			bufReturn = baos.toByteArray();
		}
		catch(Exception ie) 
		{
			return null;
		}

		return bufReturn;
	}

	/**
	 * DeCompress the byte array using the gzip algorithm
	 *
	 */
	public static byte[] ungzipBuffer(byte[] bufInput)
	{
		byte bufReturn[] = null;
		if(bufInput==null || bufInput.length==0) return bufInput;

		try 
		{
			ByteArrayOutputStream baos = new ByteArrayOutputStream(bufInput.length*2); //double the input because we are decompressing
			ByteArrayInputStream bais = new ByteArrayInputStream(bufInput);
			GZIPInputStream gzis =  new GZIPInputStream(bais);
			byte bufTemp[] = new byte[65535];
			while(gzis.available()>0)
			{
				int iRead = gzis.read(bufTemp);
				if(iRead>0) baos.write(bufTemp, 0, iRead); //append to the buffer
			}
			gzis.close();           
			bufReturn = baos.toByteArray();
		}
		catch(Exception ie) 
		{
			return null;
		}

		return bufReturn;
	}

	/**
	 * Returns the float of the JVM version. 1.403_B256 will return 1.403
	 */
	public static float getJVMVersion()
	{
		String sVer = System.getProperty("java.version"); //eg "1.4.2"
		if(sVer==null || sVer.length()==0) return 0;
		String sFinalVer = "";
		boolean bDotFound=false;
		for(int i=0; i<sVer.length(); i++)
		{
			char c = sVer.charAt(i);
			if((bDotFound && c=='.') || ((c<'0'||c>'9') && c!='.')) //skip at first non-printable char
			{
				//System.out.println(c);
				break;
			}
			else
				sFinalVer += c;
			if(c=='.') bDotFound=true;
		}
		//System.out.println("["+sFinalVer+"]");

		try
		{
			float f = Float.parseFloat(sFinalVer);          
			return f;
		}
		catch(Exception e)
		{
			System.out.println(e.toString());
		}
		return 0;
	}

	/**
	 * Writes stack trace info to the log. Use negative depths to trace from the back of the stack
	 */
	public static void logStackTrace(Throwable e, SystemContext pSystem, int iDepth)
	{
		if(e==null || pSystem==null) return;
		StringBuilder sb = new StringBuilder(512);
		StackTraceElement ste[] = e.getStackTrace();
		if(iDepth>0)
		{
			for(int i=0; i<ste.length; i++)
			{
				if(iDepth>0 && i>iDepth) break;
				sb.append(i + " class="+ste[i].getClassName()+ " method="+ste[i].getMethodName() + " line="+ste[i].getLineNumber() + "\r\n");
			}
		}
		if(iDepth<0)
		{
			/*for(int i=(ste.length-1); i>=0; i--)
			{
				if(i<ste.length+iDepth) break;
				sb.append(i + " class="+ste[i].getClassName()+ " method="+ste[i].getMethodName() + " line="+ste[i].getLineNumber() + "\r\n");
			}*/
			StringWriter sw = new StringWriter();
			PrintWriter pw = new PrintWriter(sw);
			e.printStackTrace(pw);
			sb.append(sw.toString()); 
		}
		pSystem.doDebug(0, sb.toString(), pSystem);
	}

	/**
	 * 
	 * @param e
	 * @param pSystem
	 * @param iDepth
	 */
	public static void logStackTrace(Exception e, SystemContext pSystem, int iDepth)
	{
		logStackTrace((Throwable)e, pSystem, iDepth);
	}

	/**
	 * Writes stack trace info to the log. starting from the object passed
	 */
	public static void logStackTrace(Throwable e, SystemContext pSystem, Object obj, int iDepth)
	{
		if(e==null || pSystem==null) return;
		String sStartClass="";
		StringBuilder sb = new StringBuilder(512);
		if(obj!=null) sStartClass = obj.getClass().getName();
		StackTraceElement ste[] = e.getStackTrace();
		if(iDepth<=0) iDepth = 9999;
		boolean bStartLogging = false;
		int iLogged=0;

		for(int i=0; i<ste.length; i++)
		{            
			if(iLogged>iDepth) break;
			String sExcClass = ste[i].getClassName();
			if(!bStartLogging && sStartClass.equals(sExcClass)) bStartLogging = true;
			if(bStartLogging) 
			{
				//pSystem.doDebug(0, i + " class="+sExcClass+ " method="+ste[i].getMethodName() + " line="+ste[i].getLineNumber(), pSystem);
				sb.append(i + " class="+ste[i].getClassName()+ " method="+ste[i].getMethodName() + " line="+ste[i].getLineNumber() + "\r\n");
				iLogged++;
			}
		}
		if(iLogged>0) pSystem.doDebug(0, sb.toString(), pSystem);      
	}

	public static void logStackTrace(Exception e, SystemContext pSystem, Object obj, int iDepth)
	{
		logStackTrace((Throwable)e, pSystem, obj, iDepth);
	}


	/**
	 * Converts a file into a byte array. ChunkSize specifies the size of each read, if set to <1 defaults to 4096
	 * @param f
	 * @param iChunkSize
	 * @return
	 */
	public static byte[] readFile(File f, int iChunkSize)
	{
		if(iChunkSize<1) iChunkSize = 4096;
		try 
		{
			FileInputStream fin = new FileInputStream(f);
			ByteArrayOutputStream baos = new ByteArrayOutputStream(iChunkSize);
			byte buf[] = new byte[iChunkSize]; //4k chunks
			while(fin.available()>0)
			{				
				int iRead = fin.read(buf);
				if(iRead>0) baos.write(buf, 0, iRead);
			}
			fin.close();
			return baos.toByteArray();
		} 
		catch (Exception e) {}
		return null;
	}

	/**
	 * Reads a http response, http 1.0 and "Transfer-Encoding: chunked" reply 
	 * into a byte buffer up to iMaxSize
	 * set iContentLength to -1 if unknown or chunked/http 1.0
	 *@return null if the iMaxSize was exceeded, set iMaxSize to -1 for no limit
	 */
	public static byte[] getHTTPContent(BufferedReader br, long lContentLength, int iMaxSize) throws Exception
	{
		//BufferedReader br=new BufferedReader(new InputStreamReader(is, "ISO-8859-1"));         
		ByteArrayOutputStream baos=new ByteArrayOutputStream();

		if(lContentLength>=0)
		{
			int iTotalRead=0;
			char cBuf[] = new char[1024];
			while(iTotalRead<lContentLength)
			{
				int iRead = br.read(cBuf);
				iTotalRead += iRead;                
				if(iRead>0)
				{
					byte buf[] = makeByteArray(cBuf, iRead);
					baos.write(buf, 0, iRead);
				}   
				else
					Thread.sleep(10);
			}//while 
			return baos.toByteArray();
		}

		//if we get to here, must be a http 1.0 or "chunked" reply
		//System.out.println("----- starting chunked read -----");
		final int UNKNOWN_CHUNKLENGTH=-1;
		//String CRLF = "\r\n";        
		int iChunkLength=0;
		boolean bMoreChunks=true; 
		br.mark(1024);
		String sChunkSize = br.readLine();                
		int iChunkCount=0;
		while(bMoreChunks)
		{      
			//System.out.println("More chunks: "+sChunkSize);
			try
			{   //chunks are in base 16 FC01 etc and may have a trailing comment
				int iPos = sChunkSize.indexOf(';');
				if(iPos>0) sChunkSize = sChunkSize.substring(0, iPos);
				iChunkLength = Integer.parseInt(sChunkSize, 16);
			}
			catch(Exception t)
			{
				//System.out.println("Error parsing chunklen:["+sChunkSize+"]");
				iChunkLength = UNKNOWN_CHUNKLENGTH;
			}

			int iTotalRead=0;
			//if first time through and we can't understand the chunk length, just 
			//read to end of stream and assume http/1.0
			if(iChunkLength==UNKNOWN_CHUNKLENGTH && iChunkCount==0)
			{
				br.reset();                
				int iRead=0;
				char cBuf[] = new char[1024];
				while((iRead = br.read(cBuf)) >= 0)
				{

					iTotalRead += iRead;
					if(iRead>0)
					{
						byte buf[] = makeByteArray(cBuf, iRead);                    
						baos.write(buf);                                            
					} 
					if(iMaxSize>=0 && iTotalRead>iMaxSize) return null;
				}
				break;
			}
			if(iChunkLength==0) break;
			//System.out.println("Chunk is " + iChunkLength + " bytes [" + sChunkSize + "]");

			while(iTotalRead<iChunkLength)
			{
				char cBuf[] = new char[iChunkLength-iTotalRead];
				int iRead = br.read(cBuf);
				iTotalRead += iRead;
				if(iRead>0)
				{
					byte buf[] = makeByteArray(cBuf, iRead);                    
					baos.write(buf);                                         
				} 
				else
					break;
				if(iMaxSize>=0 && iTotalRead>iMaxSize) return null;
			}//while      
			//System.out.println("total read ="+iTotalRead);
			sChunkSize = br.readLine(); //swallow crlf
			sChunkSize = br.readLine(); //read the next chunksize
			//don't break on "0" as there are 2 more CRLFs that need to be swallowed
			if(sChunkSize==null || sChunkSize.length()==0 /*|| sChunkSize.equals("0")*/) bMoreChunks = false;
			iChunkCount++;
		}//while morechunks        
		//baos.flush(); 
		//System.out.println(iChunkCount + " chunks ");
		return baos.toByteArray();
	}



	/**
	 * Convert a char array into a byte array
	 *
	 */
	public static byte[] makeByteArray(char cBuf[], int iRead)
	{
		byte buf[] = new byte[iRead];

		for(int i=0; i<iRead; i++) buf[i] = (byte)cBuf[i];
		return buf;
	}


	/**
	 * Converts a character array (double byte chars) to a byte array. In terms
	 * of internationalisation, this chops characters so should be used with care.
	 *
	 * @return the converted byte array, or null if the input is null
	 */
	public static byte[] charArrayToByteArray(char charBuf[])
	{
		if(charBuf==null) return null;
		int iLen = charBuf.length;
		byte buf[]=new byte[iLen];
		for(int p=0; p<iLen; p++) buf[p] = (byte)(charBuf[p]);

		return buf;
	}

	/**
	 * This method makes sure that the client is only allowed files that
	 * reside within the document root directory
	 */
	public static String enforceDocumentRoot(String requested_path)
	{
		String DOTDOT = "..";
		String SLASHSLASH = "//";
		String SLASHDOTDOT = "/..";
		//String HTTP_NEWLINE = "\r\n";

		// For added performance in the future, use a StringBuilder in
		// this method instead of lots of Strings..

		String enforced_path = requested_path;
		int index;

		// Make sure all backslashes are replaced with slashes, so that
		// on Windows nobody can back out of the document root using a
		// path like "\windows\win.ini"
		enforced_path = enforced_path.replace('\\', '/');

		// Now, make sure the path doesn't say "//" anywhere
		while ((index = enforced_path.indexOf(SLASHSLASH)) != -1)
		{
			enforced_path = enforced_path.substring(0, index) +
			enforced_path.substring(index + 1,
					enforced_path.length());
		}

		// Next, starting from the beginning of the path and going
		// towards the end of the path, interpret any ".." to mean
		// that we should rip out the last directory from the path,
		// unless we go back beyond the document root dir (in which
		// case we'll send a Bad Request error page.
		int last_index = 0;
		while ((index = enforced_path.indexOf('/', last_index + 1)) != -1)
		{
			// If the next token m_is 2 chars long (will short-circuit most
			// cases!), and if the token m_is "..", then we should try to
			// drop the last token from the path
			if ((index - last_index) == 3 &&
					enforced_path.substring(last_index + 1, index).equals(DOTDOT))
			{
				// Make sure we don't back up too far..
				if (last_index == 0)
				{		    
					return enforced_path;
				}

				// Otherwise, clip out the last token and the ".." token
				int before_last_index = enforced_path.lastIndexOf('/',
						last_index - 1);
				if (before_last_index == -1)
				{
					// We can't find a previous token to cut out, so this
					// is a bad request.		    
					return enforced_path;
				}

				String s = enforced_path.substring(0, before_last_index + 1);

				enforced_path = s + enforced_path.substring(index + 1,
						enforced_path.length());

				index = before_last_index;
			}
			last_index = index;
		}

		// The above while loop does not catch the case where you have
		// just ".." on the end (without a slash on the very end), so
		// we'll check for that here..
		if (enforced_path.endsWith(DOTDOT))
		{
			// If the whole enforced_path now equals "/..", then this
			// m_is a bad request.
			if (enforced_path.equals(SLASHDOTDOT))
			{		
				return enforced_path;
			}

			// Otherwise, we'll remove the last token and the "/.."
			index = enforced_path.lastIndexOf('/');
			last_index = enforced_path.lastIndexOf('/', index - 1);

			// Make sure we know where to start the chop
			if (last_index == -1)
			{
				// We couldn't find the spot to cut.. it may not have
				// even been there, so regardless we'll send a Bad Request
				// Error page.		
				return enforced_path;
			}

			enforced_path = enforced_path.substring(0, last_index);
		}

		return enforced_path;
	}

	/**
	 * gets the mime boundary from the content string
	 * example: "multipart/form-data; boundary=-------------------6473622145"
	 */
	public static String getMessageBoundary(String sContentString)
	{        
		String szBoundary = Util.getMIMELineValue(sContentString, "boundary");
		//real boundary has extra --
		return "--" + szBoundary;
	}

	/**
	 * Utility method to safety close various jdbc objects
	 * ResultSet, Statement, PreparedStatement
	 */
	public static void closeJDBC(Object obj)
	{
		if(obj==null) return;

		try
		{
			if(obj instanceof ResultSet) ((ResultSet)obj).close();
			if(obj instanceof Statement) ((Statement)obj).close();
			if(obj instanceof PreparedStatement) ((PreparedStatement)obj).close();
		}catch(Exception e){}
	}

	/**
	 * Converts a block of utf8 bytes to a String
	 */
	public static String stringFromUTF8(byte buf[])
	{
		//see http://java.sun.com/j2se/1.4.2/docs/guide/intl/encoding.doc.html
		final String DEFAULT_CHARSET="UTF-8"; 
		String sReturn = null;      
		try
		{
			sReturn = new String(buf, DEFAULT_CHARSET);
		}
		catch(Exception e){}
		return sReturn;
	}

	/**
	 * Converts a String to a block of utf8 bytes
	 */
	public static byte[] utf8FromString(String sIn)
	{
		//see http://java.sun.com/j2se/1.4.2/docs/guide/intl/encoding.doc.html
		final String DEFAULT_CHARSET="UTF-8"; 
		byte bufReturn[] = null;      
		try
		{
			bufReturn = sIn.getBytes(DEFAULT_CHARSET);
		}
		catch(Exception e){}
		return bufReturn;
	}

	/**
	 * For reading blob streams. This is primarily for Oracle because it can't deal with 
	 * ResultSet.getBytes() properly - always returns 86 bytes which is apparently the blob locator
	 * @param blob
	 * @return
	 * @throws SQLException
	 * @throws IOException
	 */
	/*private static byte[] getBlobBytes(Blob blob) throws SQLException, IOException 
	{
		if(blob==null)	return null;

		return readStreamToByteArray(blob.getBinaryStream());
	}*/

	/**
	 * 
	 * @param is
	 * @return
	 * @throws IOException
	 */
	public static byte[] readStreamToByteArray(InputStream is) throws IOException
	{
		if(is==null) return null;
		ByteArrayOutputStream baos = new ByteArrayOutputStream(2048); // ~ 2k
		byte[] buf = new byte[1024];		
		try 
		{
			for (;;) 
			{
				int dataSize = is.read(buf);
				if (dataSize == -1)
					break;
				baos.write(buf, 0, dataSize);
			}
		} 
		finally 
		{
			if (is != null) 
			{
				try 
				{
					is.close();
				}
				catch (IOException ex) {}
			}
		}
		return baos.toByteArray();
	}

	/**
	 * Gets the binary content from a column and returns it as a byte array
	 * @param rs
	 * @param iColumn
	 * @return
	 * @throws SQLException
	 * @throws IOException
	 */
	public static byte[] getBlobBytes(ResultSet rs, int iColumn) throws SQLException, IOException 
	{
		return readStreamToByteArray(rs.getBinaryStream(iColumn));
	}

	/**
	 * Gets the binary content from a column and returns it as a byte array.
	 * This is primarily for Oracle because it can't deal with 
	 * ResultSet.getBytes() properly - always returns 86 bytes which is apparently the blob locator
	 * @param rs
	 * @param sColumnName
	 * @return
	 * @throws SQLException
	 * @throws IOException
	 */
	public static byte[] getBlobBytes(ResultSet rs, String sColumnName) throws SQLException, IOException 
	{
		return readStreamToByteArray(rs.getBinaryStream(sColumnName));
	}

}//class