/** ***************************************************************
ObjectStore.java
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

import java.util.Hashtable;
import java.util.Enumeration;

/**
 * The object store is used to store objects in the JVM memory on an adhoc basis.
 * This class follows the singleton pattern, thus cannot be instantiated directly.
 * @author  bupson
 */
public class ObjectStore 
{
    private static ObjectStore m_ref;
    private Hashtable m_ht = new Hashtable();
    
  private ObjectStore()
  {
    // no code req'd
  }

  /**
   * instantiate and return a reference to this object. Can only be
   * called internally.
   */
  private static synchronized ObjectStore getInstance()
  {
    if (m_ref == null)
        // it's ok, we can call this constructor
        m_ref = new ObjectStore();		
    return m_ref;
  }

  /**
   * This object may not be cloned.
   */
  public Object clone() throws CloneNotSupportedException
  {
    throw new CloneNotSupportedException();     
  }
  
  /**
   * Get a stored object
   */
  public static Object getObject(String sKey)
  {
      ObjectStore obj = ObjectStore.getInstance();
      return obj.getObjectInternal(sKey);
  }
  
  /**
   * Add a stored object
   */
  public static void putObject(String sKey, Object o)
  {
      ObjectStore obj = ObjectStore.getInstance();
      obj.putObjectInternal(sKey, o);
  }
  
  public static Enumeration getAllKeys()
  {
      ObjectStore obj = ObjectStore.getInstance();
      return obj.getAllKeysInternal();
  }
  
  /**
   * Remove a stored object
   */
  public static void removeObject(String sKey)
  {
      ObjectStore obj = ObjectStore.getInstance();
      obj.removeObjectInternal(sKey);
  }
  
  public Object getObjectInternal(String sKey)
  {
      return m_ht.get(sKey);
  }
  
  public void putObjectInternal(String sKey, Object o)
  {
      if(m_ht.containsKey(sKey)) removeObjectInternal(sKey);
      m_ht.put(sKey, o);
  }

  public void removeObjectInternal(String sKey)
  {
      m_ht.remove(sKey);
  }
  
  public Enumeration getAllKeysInternal()
  {
      return m_ht.keys();
  }
  
}
