/** ***************************************************************
PuakmaSecurityManager.java
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

/*
 * NOT CURRENTLY USED....
 *
 */


package puakma.security;

import java.security.*;
/**
 *
 * This class may restrict permissions of anything that is trying to be executed by
 * ActionClassLoader
 *
 *  TODO!!!!
 */
public class PuakmaSecurityManager extends SecurityManager
{
    public final static String CLASSLOADER_ACTION = "puakma.addin.http.action.ActionClassLoader";
    /** Creates a new instance of PuakmaSecurityManager */
    public PuakmaSecurityManager() 
    {
        super();
    }
    
    
    
    public void checkPermission(Permission perm) throws SecurityException
    {
        /*ClassLoader cl = this.getClass().getClassLoader();
        String sCLClass = cl.getClass().getName();
        System.out.println(" loader="+sCLClass);
         */
        System.out.println("Checking... "+perm.toString());
        
    }
    
    /*public void checkPermission(Permission perm, Object context) throws SecurityException
    {
        ClassLoader cl = context.getClass().getClassLoader();
        String sCLClass = cl.getClass().getName();
        System.out.println(context.getClass().getName() + " loader="+sCLClass);
        
    }
     */
    
    /*
    public void checkRead(String sFile) throws SecurityException
    {
        ClassLoader cl = this.getClass().getClassLoader();
        String sCLClass = cl.getClass().getName();
        System.out.println(this.getClass().getName() + " loader="+sCLClass);
    }
     */
}
