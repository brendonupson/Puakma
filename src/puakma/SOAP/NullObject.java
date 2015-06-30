/** ***************************************************************
NullObject.java
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
 * <p>Description: This class is used to denote a null Object. This is because we
 * cannot reflect on a regular null object (eg String). Uses the SOAPCallParser
 * type definitions.</p>
 * <p>Copyright: Copyright (c) 2002</p>
 * <p>Company: </p>
 * @author Brendon Upson
 * @version 1.0
 */

public class NullObject
{
  private int m_iType=-1;


  public NullObject(int iType)
  {
    m_iType = iType;
  }

  /**
   *
   * @return
   */
  public int getType()
  {
    return m_iType;
  }

  /**
   *
   * @return
   */
  public synchronized void setType(int iType)
  {
     m_iType = iType;
  }


  /**
   *
   * @return
   */
  public Object getNullObject()
  {
    return null;
  }

  /**
   *
   * @return
   */
  public String toString()
  {
    return "";
  }

  /**
   *
   * @return
   */
  public static NullObject setString()
  {
    return new NullObject(SOAPCallParser.OBJECT_TYPE_STRING_ID);
  }

  /**
   *
   * @return
   */
  public static NullObject setInteger()
  {
    return new NullObject(SOAPCallParser.OBJECT_TYPE_INTEGER_ID);
  }

  /**
   *
   * @return
   */
  public static NullObject setLong()
  {
    return new NullObject(SOAPCallParser.OBJECT_TYPE_LONG_ID);
  }

  /**
   *
   * @return
   */
  public static NullObject setFloat()
  {
    return new NullObject(SOAPCallParser.OBJECT_TYPE_FLOAT_ID);
  }

  /**
   *
   * @return
   */
  public static NullObject setDouble()
  {
    return new NullObject(SOAPCallParser.OBJECT_TYPE_DOUBLE_ID);
  }

  /**
   *
   * @return
   */
  public static NullObject setDate()
  {
    return new NullObject(SOAPCallParser.OBJECT_TYPE_DATETIME_ID);
  }

  /**
   *
   * @return
   */
  public static NullObject setBoolean()
  {
    return new NullObject(SOAPCallParser.OBJECT_TYPE_BOOLEAN_ID);
  }

  /**
   *
   * @return
   */
  public static NullObject setBase64()
  {
    return new NullObject(SOAPCallParser.OBJECT_TYPE_BASE64_ID);
  }


}//end class