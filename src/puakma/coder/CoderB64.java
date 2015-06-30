/** ***************************************************************
CoderB64.java
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

// base 64 coding
// To decode a Base 64 encoded string you simply convert a chunk
// of 4 bytes of blocks of 6 bits into 3 bytes of 8 bits.
// Base 64 encoded strings may be padded with the '=' char
// to ensure they extend to a 4 byte boundary.
// Keep in mind that the output may be binary.
// NOTE: You must pass in an input buffer of a size that
// is divisible by 4 otherwise the output will be incorrect
// at the boundary.
// The destination buffer can be 1/4 smaller than the input
// buffer.
// If you need to pad your b64 string, use the '=' char.


// The idea behind the encoding is to change three bytes ( 8 bits )
// into 4 bytes of 6 relevant bits. Therefore the destination buffer
// must be 1/4 again as big as the source buffer.
// Remember, the input may be binary so you must supply the
// input buffer length
// If you don't send a source buffer length that is divisible by
// 3 then the function will consider the buffer to just be a chunk
// and not the last block. This allows multi block buffers.
// Otherwise the function will append a pad character at
// the end of the chunk.


public class CoderB64 extends Coder68
{
   private static String B64 = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";

   public CoderB64()
   {  super( (byte)'=' );
   }

   protected byte enc( int i )
   {  return( (byte)B64.charAt( i ) );
   }

   protected byte dec( int i )
   {  return( (byte)B64.indexOf( i ) );
   }

   public String getContentString()
   {  return( "Content-Transfer-Encoding: base64\r\n" );
   }
}


