/** ***************************************************************
Coder68.java
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

import java.lang.System;

// 3 8 bit byte to 4 6 bit byte encoding
// used for Base64 UUEncode encoding and decoding

public class Coder68
{
   private byte PadChar = (byte)' ';

   public Coder68( byte pad )
   {  PadChar = pad;
   }

   public String getContentString()
   {  return( "Content-Transfer-Encoding: unknown\r\n" );
   }

   protected byte enc( int i )
   {  return( (byte) i );
   }

   public String encode(byte[] bIn)
   {    
      /*if(bIn==null) return null;
      if(bIn.length==0) return "";
      //byte bOut[] = new byte[(bIn.length*2)+5];
       //( iSrcLen / 3 ) * 4 )
      int iSize = (int)((bIn.length/3)*4)+10;
      if(iSize<0) return null;
      
      byte bOut[] = new byte[iSize];
      int iLen = encode(bIn, bOut, bIn.length, bOut.length );
      
      if(iLen<0) return null;
       */
      byte bufReturn[] = encodeBytes(bIn);
      if(bufReturn==null) return null;

      return new String(bufReturn);
      //String szReturn = new String(bufReturn);
      //return szReturn.substring(0, iLen);
   }
   
   /**
    *
    */
   public byte[] encodeBytes(byte[] bIn)
   {    
      if(bIn==null) return null;
      if(bIn.length==0) return bIn;
      //byte bOut[] = new byte[(bIn.length*2)+5];
       //( iSrcLen / 3 ) * 4 )
      int iSize = (int)((bIn.length/3)*4)+10;
      if(iSize<0) return null;
      
      byte bOut[] = new byte[iSize];
      int iLen = encode(bIn, bOut, bIn.length, bOut.length );
      
      if(iLen<0) return null;

      byte bufReturn[] = new byte[iLen];
      System.arraycopy(bOut, 0, bufReturn, 0, iLen);
      return bufReturn;
   }

   public int encode( byte[] bIn, byte[] bOut, int iSrcLen, int iDestLen )
   {  int j, i, o, enclen, remlen;

      // make sure the destination is a quarter again as large as the source.
      if ( iDestLen < ( iSrcLen / 3 ) * 4 ) return( -1 );

      i = 0;
      o = 0;
      enclen = iSrcLen / 3;   // number of 4 byte encodings (source DIV 3)
      remlen = iSrcLen % 3;   // remainder if srclen not divisible by 3 (source MOD 3)

      for (j = 0; j < enclen; j++)
      {  bOut[o]   = enc(  ( bIn[i] >> 2)   & 0x3f );
         bOut[o+1] = enc( (( bIn[i] << 4)   & 0x30 ) | (( bIn[i+1] >> 4 ) & 0x0f) );
         bOut[o+2] = enc( (( bIn[i+1] << 2) & 0x3c ) | (( bIn[i+2] >> 6 ) & 0x03) );
         bOut[o+3] = enc( bIn[i+2] & 0x3f );
         i += 3;
         o += 4;
      }

      if (remlen > 0)
      {  bOut[o]   = enc( ( bIn[i] >> 2) & 0x3f );
         if (remlen == 1)
         {  bOut[o+1] = enc( (bIn[i]<<4) & 0x30 );
            bOut[o+2] = PadChar;
         }
         else // remlen == 2
         {  bOut[o+1] = enc( (( bIn[i] << 4) & 0x30 ) | (( bIn[i+1] >> 4 ) & 0x0f) );
            bOut[o+2] = enc( (bIn[i+1] << 2) & 0x3c );
         }
         bOut[o+3] = PadChar;
         // increment the last byte
         o += 4;
      }
      bOut[ o ] = 0;

      return( o );
   }

   protected byte dec( int i )
   {  return( (byte) i );
   }


   public byte[] decode(byte[] bIn)
   {
      if(bIn==null || bIn.length==0) return bIn;
      byte bOut[] = new byte[bIn.length]; //output will be same or shorter than input
      int iLen = decode(bIn, bOut, bIn.length, bOut.length );

      if(iLen<0) return null;

      byte bReturn[] = new byte[iLen];
      System.arraycopy(bOut, 0, bReturn, 0, iLen);

      return bReturn;
   }

   public int decode( byte bIn[], byte bOut[], int iSrcLen, int iDestLen )
   {  
      int i, o, j, declen, inc=0;

      // ensure input length is OK
      if ( iDestLen < ( (iSrcLen + 3) / 4 ) * 3 ) return( -1 );

      declen = ( iSrcLen + 3 ) / 4;
      i = 0;
      o = 0;

      for (j = 0; j < declen; j++)
      {

         if ( i < iSrcLen )
         {  if ( bIn[i] == PadChar ) bOut[o] = 0;
            else
            {  bOut[ o ] = (byte)(( dec(bIn[i+0]) << 2 ) & 0xfc );
               inc++;
            }
         }
         else bOut[ o ] = 0;

         if ( i + 1 < iSrcLen )
         {  if ( bIn[i+1] == PadChar ) bOut[o+1] = 0;
            else
            {  bOut[ o+0 ] |= (byte)(( dec(bIn[i+1]) >> 4 ) & 0x0f );
               bOut[ o+1 ] =  (byte)(( dec(bIn[i+1]) << 4 ) & 0xf0 );
               if( i + 2 < iSrcLen  && bIn[ i + 2 ] != PadChar )inc++;
            }
         }
         else bOut[ o + 1 ] = 0;

         if ( i + 2 < iSrcLen )
         {  if ( bIn[i+2] == PadChar ) bOut[o+2] = 0;
            else
            {  bOut[ o+1 ] |= (byte)(( dec(bIn[i+2]) >> 2 ) & 0x3f );
               bOut[ o+2 ] =  (byte)(( dec(bIn[i+2]) << 6 ) & 0xc0 );
               if( i + 3 < iSrcLen && bIn[ i + 3 ] != PadChar )inc++;
            }
         }
         else bOut[ o + 2 ] = 0;


         if ( i + 3 < iSrcLen )
         {  if ( bIn[i+3] == PadChar ) bOut[o+3] = 0;
            else bOut[ o+2 ] |= (byte)(dec(bIn[i+3]));
         }
         else bOut[ o + 3 ] = 0;

         i += 4;
         o += 3;
      }

      bOut[ inc ] = 0;

      return( inc );
   }
}

