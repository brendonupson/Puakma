/** ***************************************************************
CacheableItem.java
Copyright (C) 2001  Mike Skillicorn 
http://www.seatechnology.com.au mike@mikeskillicorn.com

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

package puakma.pooler;

/**
* Defines items that may be in the Cache. An object must implement
* this interface to be allowed into the cache
*/
public interface CacheableItem
{
  public double getSize();
  public String getItemKey();
  public void logCacheAccess();
  public boolean itemHasExpired(long lAgeInMilliseconds);
} 