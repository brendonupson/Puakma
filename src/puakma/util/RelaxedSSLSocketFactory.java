/** ***************************************************************
RelaxedSSLSocketFactory.java
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

//import javax.net.ssl.*;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;

import javax.net.SocketFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;


/**
 * RelaxedSSLSocketFactory
 *
 */
public class RelaxedSSLSocketFactory extends SSLSocketFactory {
  private SSLSocketFactory factory;

  public RelaxedSSLSocketFactory() {
    //System.out.println( "DummySocketFactory instantiated");
    try {
      SSLContext sslcontext = SSLContext.getInstance( "TLS");
      sslcontext.init( null,
                       // new KeyManager[] { new DummyKeyManager()},
                       new TrustManager[] { new RelaxedTrustManager()},
                       new java.security.SecureRandom());
      factory = ( SSLSocketFactory) sslcontext.getSocketFactory();

    } catch( Exception ex) {
      ex.printStackTrace();
    }
  }

  public static SocketFactory getDefault() {
    //System.out.println( "DummySocketFactory.getDefault()");
    return new RelaxedSSLSocketFactory();
  }

  public Socket createSocket( Socket socket, String s, int i, boolean flag)
      throws IOException {
    //System.out.println( "DummySocketFactory.createSocket()");
    return factory.createSocket( socket, s, i, flag);
  }

  public Socket createSocket( InetAddress inaddr, int i,
                              InetAddress inaddr1, int j) throws IOException {
    //System.out.println( "DummySocketFactory.createSocket()");
    return factory.createSocket( inaddr, i, inaddr1, j);
  }

  public Socket createSocket( InetAddress inaddr, int i)
      throws IOException {
    //System.out.println( "DummySocketFactory.createSocket()");
    return factory.createSocket( inaddr, i);
  }

  public Socket createSocket( String s, int i, InetAddress inaddr, int j)
      throws IOException {
    //System.out.println( "DummySocketFactory.createSocket()");
    return factory.createSocket( s, i, inaddr, j);
  }

  public Socket createSocket( String s, int i) throws IOException {
    //System.out.println( "DummySocketFactory.createSocket()");
    return factory.createSocket( s, i);
  }

  public String[] getDefaultCipherSuites() {
    //System.out.println( "DummySocketFactory.getDefaultCipherSuites()");
    return factory.getSupportedCipherSuites();
  }

  public String[] getSupportedCipherSuites() {
    //System.out.println( "DummySocketFactory.getSupportedCipherSuites()");
    return factory.getSupportedCipherSuites();
  }
}

