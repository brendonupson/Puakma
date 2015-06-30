/** ***************************************************************
CoderUU.java
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
package puakma.coder;

//import java.lang.*;
//import skillm.coders.Coder68;

public class CoderUU extends Coder68
{
   public CoderUU()
   {  super( (byte)'~' );
   }

   protected byte enc( int i )
   {  return( (byte)((i & 0x3f ) + 32 ) );
   }

   protected byte dec( int i )
   {  return( (byte)((i - 32) & 0x3f ) );
   }

   public String getContentString()
   {  return( "Content-Transfer-Encoding: uuencode\r\n" );
   }

}


