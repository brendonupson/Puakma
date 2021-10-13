/** ***************************************************************
pmaDefaultAuthenticator.java
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
package puakma.security;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.Hashtable;

import puakma.error.pmaLog;
import puakma.system.SessionContext;
import puakma.system.X500Name;
import puakma.util.MailAddress;
import puakma.util.Util;

/**
 * This is the default authenticator. It will authenticate a user against the Puakma
 * PERSON Table
 */
public class pmaDefaultAuthenticator extends pmaAuthenticator
{

	public LoginResult loginUser(String sUserName, String sPassword, String sIPAddress, String sUserAgent, String sAppURI)
	{
		LoginResult loginResult = new LoginResult();

		String sEmailWhere = "";
		String sLoginName = sUserName.toLowerCase();
		MailAddress ma = new MailAddress(sLoginName);
		if(ma.isValidAddressSyntax())
		{
			sEmailWhere = " OR LOWER(EmailAddress)=?";
		}
		//avoid buffer overflow login attempts
		if(sLoginName==null || sLoginName.length()>120) return loginResult;
		//String szResult;
		Connection cx=null;

		int iFound;
		try
		{
			cx = SysCtx.getSystemConnection();
			String szQuery = "SELECT Count(ShortName) FROM PERSON WHERE (LOWER(ShortName)=? OR LOWER(Alias)=?"+sEmailWhere+")";
			PreparedStatement prepStmt = cx.prepareStatement(szQuery, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
			prepStmt.setString(1, sLoginName);
			prepStmt.setString(2, sLoginName);
			if(sEmailWhere.length()>0) prepStmt.setString(3, sLoginName);
			ResultSet rs = prepStmt.executeQuery();
			iFound=0;
			if(rs.next()) iFound = rs.getInt(1);
			if(iFound>1)
			{
				SysCtx.doError("pmaDefaultAuthenticator.LoginTooMany", new String[]{sLoginName}, this);
				loginResult.ReturnCode=LoginResult.LOGIN_RESULT_TOO_MANY_MATCHES;
				rs.close();
				prepStmt.close();
				SysCtx.releaseSystemConnection(cx);
				return loginResult;
			}
			if(iFound<1)
			{
				SysCtx.doError("pmaDefaultAuthenticator.LoginNotFound", new String[]{sLoginName}, this);
				loginResult.ReturnCode=LoginResult.LOGIN_RESULT_INVALID_USER;
				rs.close();
				prepStmt.close();
				SysCtx.releaseSystemConnection(cx);
				return loginResult;
			}


			rs.close();
			prepStmt.close();
			szQuery = "SELECT FirstName,LastName,UserName,Password,LastLogin,LastLoginUserAgent,LastLoginAddress,LoginFlag FROM PERSON WHERE (LOWER(ShortName)=? OR LOWER(Alias)=?"+sEmailWhere+")";
			prepStmt = cx.prepareStatement(szQuery, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
			prepStmt.setString(1, sLoginName);
			prepStmt.setString(2, sLoginName);
			if(sEmailWhere.length()>0) prepStmt.setString(3, sLoginName);
			rs = prepStmt.executeQuery();
			if(rs.next())
			{
				//hash password
				String sEncryptedPW = Util.encryptString(sPassword);
				String sStoredPassword = Util.trimSpaces(rs.getString("Password"));
				//System.out.println("pw=["+szPassword+"]");
				//System.out.println("encryp=["+szEncryptedPW+"]");
				//System.out.println("stored=["+szStoredPassword+"]");
				//we use startswith because the pw may be truncated in the DB
				if(sEncryptedPW.startsWith(sStoredPassword)) //if password matches
				{
					String sLoginFlag = rs.getString("LoginFlag");
					if(sLoginFlag!=null && sLoginFlag.toUpperCase().indexOf('D')>=0)
					{
						SysCtx.doError("pmaDefaultAuthenticator.LoginAccountDisabled", new String[]{sLoginName}, this);
						loginResult.ReturnCode=LoginResult.LOGIN_RESULT_ACCOUNT_DISABLED;
						rs.close();
						prepStmt.close();
						SysCtx.releaseSystemConnection(cx);
						return loginResult;
					}

					loginResult.FirstName = rs.getString("FirstName");
					loginResult.LastName = rs.getString("LastName");
					loginResult.UserName = rs.getString("UserName");
					SysCtx.doInformation("pmaDefaultAuthenticator.loginSuccess", new String[]{loginResult.UserName, sIPAddress}, this);
					loginResult.ReturnCode=LoginResult.LOGIN_RESULT_SUCCESS;
					//Note the login time...
					PreparedStatement prepStmt2;
					prepStmt2 = cx.prepareStatement("UPDATE PERSON SET LastLogin=?, LastLoginAddress=?, LastLoginUserAgent=? WHERE UserName=?");
					prepStmt2.setTimestamp(1, new Timestamp(System.currentTimeMillis()));
					prepStmt2.setString(2, sIPAddress);
					prepStmt2.setString(3, sUserAgent);
					prepStmt2.setString(4, loginResult.UserName);
					prepStmt2.execute();
					prepStmt2.close();
				}
				else
				{
					//stop echoing buffer overflows to the screen.
					if(sLoginName!=null) SysCtx.doInformation("pmaDefaultAuthenticator.loginFailure", new String[]{sLoginName, sIPAddress}, this);
				}
			}
			rs.close();
			prepStmt.close();
		}
		catch (Exception sqle)
		{
			SysCtx.doError("pmaDefaultAuthenticator.loginSQLError", new String[]{sqle.getMessage()}, this);
		}
		finally
		{
			SysCtx.releaseSystemConnection(cx);
		}
		return loginResult;
	}

	/**
	 * This is a recursive function that will determine if the user (m_pSession)
	 * is in the group passed. The recursion checks nested groups.
	 */
	public boolean isUserInGroup(SessionContext sessCtx, String sGroup, String sAppURI)
	{
		return isUserInGroupPrivate(null, sessCtx, sGroup);
	}

	/**
	 * Populate the loginresult object from the matching canonical name
	 *
	 */
	public LoginResult populateSession(String szCanonicalName, String sAppURI)
	{
		Connection cx=null;
		LoginResult loginResult = new LoginResult();

		try
		{
			cx = SysCtx.getSystemConnection();
			String sQuery = "SELECT FirstName,LastName,UserName,LastLogin,LastLoginUserAgent,LastLoginAddress,LoginFlag FROM PERSON WHERE UserName=?";
			PreparedStatement prepStmt = cx.prepareStatement(sQuery);
			prepStmt = cx.prepareStatement(sQuery, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
			prepStmt.setString(1, szCanonicalName);        
			ResultSet rs = prepStmt.executeQuery();
			if(rs.next())
			{
				loginResult.FirstName = rs.getString("FirstName");
				loginResult.LastName = rs.getString("LastName");
				loginResult.UserName = rs.getString("UserName");            
				loginResult.ReturnCode=LoginResult.LOGIN_RESULT_SUCCESS;            
			}
			rs.close();
			prepStmt.close();
		}
		catch(Exception e)
		{
			SysCtx.doError(e.toString(), this);
		}
		finally
		{
			SysCtx.releaseSystemConnection(cx);
		}
		return loginResult;
	}



	/**
	 * This is to stop the possibility of recursively nested groups. The hastable
	 * contains the names of the items already checked. Items will only be checked once
	 */
	private boolean isUserInGroupPrivate(Hashtable ht, SessionContext sessCtx, String szGroup)
	{
		if(ht==null) ht = new Hashtable();
		SysCtx.doDebug(pmaLog.DEBUGLEVEL_FULL, "isUserInGroupPrivate(%s->%s)", new String[]{sessCtx.getUserName(), szGroup}, this);
		boolean bIsInGroup=false;
		String szResult;
		Connection cx=null;
		if(ht.containsKey(szGroup)) return false;
		ht.put(szGroup, szGroup);
		try
		{
			cx = SysCtx.getSystemConnection();
			String szQuery = "SELECT PMAGROUPMEMBER.Member FROM PMAGROUP,PMAGROUPMEMBER WHERE UPPER(PMAGROUP.GroupName)='" + szGroup.toUpperCase() + "' AND PMAGROUP.GroupID=PMAGROUPMEMBER.GroupID";
			Statement Stmt = cx.createStatement();
			ResultSet RS = Stmt.executeQuery(szQuery);
			while (RS.next())
			{
				szResult = Util.trimSpaces(RS.getString(1));
				X500Name nmResult = new X500Name(szResult);
				X500Name nmUser = sessCtx.getX500Name();
				//check exact match, *=All, partial match (username must be longer than result!)
				if(nmUser.equals(nmResult)
						|| szResult.equals("*")
						|| nmUser.matches(nmResult))
				{
					SysCtx.doDebug(pmaLog.DEBUGLEVEL_VERBOSE, "User '%s' is in group '%s'", new String[]{sessCtx.getUserName(), szGroup}, sessCtx);
					bIsInGroup=true;
					break;
				}
				else
				{
					bIsInGroup = isUserInGroupPrivate(ht, sessCtx, szResult);
					if(bIsInGroup) break;
				}
			}//while
			RS.close();
			Stmt.close();
		}
		catch (Exception sqle)
		{
			SysCtx.doError("HTTPRequest.GroupNestRecurse", new String[]{sqle.getMessage()}, this);
		}
		finally
		{
		SysCtx.releaseSystemConnection(cx);
		}
		return bIsInGroup;
	}
}