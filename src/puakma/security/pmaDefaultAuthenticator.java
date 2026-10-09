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
import java.sql.Timestamp;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.concurrent.ConcurrentHashMap;

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
	private static final int MAX_GROUPS_VISITED = 500;
	private static final int MAX_GROUP_CACHE_ENTRIES = 10000;
	//g2.GroupID is non-null when the member is itself a group, so only real groups are walked
	private static final String GROUP_MEMBER_QUERY = "SELECT m.Member, g2.GroupID FROM PMAGROUP g JOIN PMAGROUPMEMBER m ON g.GroupID=m.GroupID LEFT JOIN PMAGROUP g2 ON UPPER(g2.GroupName)=UPPER(TRIM(m.Member)) WHERE UPPER(g.GroupName)=?";

	/**
	 * Group definitions keyed on upper case group name. Groups are the same for every user
	 * so one entry serves all sessions. Groups are edited via SQL by the admin apps so
	 * entries simply expire after GroupCacheSeconds (puakma.config, default 60, 0=off)
	 */
	private final ConcurrentHashMap<String, GroupEntry> m_htGroupCache = new ConcurrentHashMap<String, GroupEntry>();
	private long m_lGroupCacheMS = 60000;

	private static final class GroupEntry
	{
		final String[] members;
		final boolean[] isGroup;
		final long lExpires;

		GroupEntry(String[] members, boolean[] isGroup, long lExpires)
		{
			this.members = members;
			this.isGroup = isGroup;
			this.lExpires = lExpires;
		}
	}

	/**
	 * The connection for one group walk, only opened if the cache cannot answer
	 */
	private static final class GroupLookup
	{
		Connection cx;
		PreparedStatement stmt;
	}

	public void init()
	{
		String sTemp = SysCtx.getSystemProperty("GroupCacheSeconds");
		if(sTemp!=null && sTemp.trim().length()>0) m_lGroupCacheMS = Math.max(0, Util.toInteger(sTemp.trim()))*1000;
	}

	public LoginResult loginUser(String sUserName, String sPassword, String sIPAddress, String sUserAgent, String sAppURI)
	{
		LoginResult loginResult = new LoginResult();

		//avoid buffer overflow login attempts
		if(sUserName==null || sUserName.length()==0 || sUserName.length()>120) return loginResult;
		if(sPassword==null) return loginResult;

		String sEmailWhere = "";
		String sLoginName = sUserName.toLowerCase();
		MailAddress ma = new MailAddress(sLoginName);
		if(ma.isValidAddressSyntax())
		{
			sEmailWhere = " OR LOWER(EmailAddress)=?";
		}

		//message to log once the connection has been released
		String sLogKey = null;
		String sLogParams[] = null;
		boolean bLogAsError = false;

		Connection cx = null;
		PreparedStatement stmt = null;
		ResultSet rs = null;
		try
		{
			cx = SysCtx.getSystemConnection();
			String sQuery = "SELECT PersonID,FirstName,LastName,UserName,Password,LoginFlag FROM PERSON WHERE (LOWER(ShortName)=? OR LOWER(Alias)=?"+sEmailWhere+")";
			stmt = cx.prepareStatement(sQuery, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
			stmt.setString(1, sLoginName);
			stmt.setString(2, sLoginName);
			if(sEmailWhere.length()>0) stmt.setString(3, sLoginName);
			rs = stmt.executeQuery();

			boolean bFound = rs.next();
			String sFirstName = null, sLastName = null, sCanonicalName = null, sStoredPassword = null, sLoginFlag = null;
			int iPersonID = 0;
			if(bFound)
			{
				iPersonID = rs.getInt("PersonID");
				sFirstName = rs.getString("FirstName");
				sLastName = rs.getString("LastName");
				sCanonicalName = rs.getString("UserName");
				sStoredPassword = Util.trimSpaces(rs.getString("Password"));
				sLoginFlag = rs.getString("LoginFlag");
			}
			boolean bTooMany = bFound && rs.next();
			Util.closeJDBC(rs);
			rs = null;
			Util.closeJDBC(stmt);
			stmt = null;

			if(bTooMany)
			{
				loginResult.ReturnCode=LoginResult.LOGIN_RESULT_TOO_MANY_MATCHES;
				if(m_bShowLoginErrors) { sLogKey = "pmaDefaultAuthenticator.LoginTooMany"; sLogParams = new String[]{sLoginName}; bLogAsError = true; }
			}
			else if(!bFound)
			{
				loginResult.ReturnCode=LoginResult.LOGIN_RESULT_INVALID_USER;
				if(m_bShowLoginErrors) { sLogKey = "pmaDefaultAuthenticator.LoginNotFound"; sLogParams = new String[]{sLoginName}; bLogAsError = true; }
			}
			else
			{
				String sEncryptedPW = Util.encryptString(sPassword);
				//we use startswith because the pw may be truncated in the DB.
				//An empty stored password must never match, startsWith("") is always true
				boolean bPasswordOK = sEncryptedPW!=null && sStoredPassword!=null && sStoredPassword.length()>0 
						&& sEncryptedPW.startsWith(sStoredPassword);
				if(!bPasswordOK)
				{
					//loginResult.ReturnCode=LoginResult.LOGIN_RESULT_FAIL; //Don't do this because another authenticator in the chain may succeed
					sLogKey = "pmaDefaultAuthenticator.LoginFailure";
					sLogParams = new String[]{sLoginName, sIPAddress};
				}
				else if(sLoginFlag!=null && sLoginFlag.toUpperCase().indexOf('D')>=0)
				{
					loginResult.ReturnCode=LoginResult.LOGIN_RESULT_ACCOUNT_DISABLED;
					sLogKey = "pmaDefaultAuthenticator.LoginAccountDisabled"; 
					sLogParams = new String[]{sLoginName}; 
					bLogAsError = true;
				}
				else
				{
					loginResult.FirstName = sFirstName;
					loginResult.LastName = sLastName;
					loginResult.UserName = sCanonicalName;
					loginResult.ReturnCode=LoginResult.LOGIN_RESULT_SUCCESS;
					sLogKey = "pmaDefaultAuthenticator.LoginSuccess";
					sLogParams = new String[]{sCanonicalName, sIPAddress};
					updateLastLogin(cx, iPersonID, sIPAddress, sUserAgent);
				}
			}
		}
		catch (Exception sqle)
		{
			SysCtx.doError("pmaDefaultAuthenticator.LoginSQLError", new String[]{sqle.getMessage()}, this);
		}
		finally
		{
			Util.closeJDBC(rs);
			Util.closeJDBC(stmt);
			SysCtx.releaseSystemConnection(cx);
		}

		if(sLogKey!=null)
		{
			if(bLogAsError) 
				SysCtx.doError(sLogKey, sLogParams, this);
			else
				SysCtx.doInformation(sLogKey, sLogParams, this);
		}
		return loginResult;
	}

	/**
	 * Note the login time. A failure here is logged but does not fail the login
	 */
	private void updateLastLogin(Connection cx, int iPersonID, String sIPAddress, String sUserAgent)
	{
		PreparedStatement stmt = null;
		try
		{
			stmt = cx.prepareStatement("UPDATE PERSON SET LastLogin=?, LastLoginAddress=?, LastLoginUserAgent=? WHERE PersonID=?");
			stmt.setTimestamp(1, new Timestamp(System.currentTimeMillis()));
			stmt.setString(2, sIPAddress);
			stmt.setString(3, sUserAgent);
			stmt.setInt(4, iPersonID);
			stmt.execute();
		}
		catch(Exception e)
		{
			SysCtx.doError("pmaDefaultAuthenticator.loginSQLError", new String[]{e.getMessage()}, this);
		}
		finally
		{
			Util.closeJDBC(stmt);
		}
	}

	/**
	 * Determines if the user is in the group passed, including via nested groups.
	 */
	public boolean isUserInGroup(SessionContext sessCtx, String sGroup, String sAppURI)
	{
		return isUserInGroupPrivate(sessCtx, sGroup);
	}

	/**
	 * Populate the loginresult object from the matching canonical name
	 *
	 */
	public LoginResult populateSession(String szCanonicalName, String sAppURI)
	{
		Connection cx = null;
		PreparedStatement prepStmt = null;
		ResultSet rs = null;
		
		LoginResult loginResult = new LoginResult();

		try
		{
			cx = SysCtx.getSystemConnection();
			String sQuery = "SELECT FirstName,LastName,UserName,LastLogin,LastLoginUserAgent,LastLoginAddress,LoginFlag FROM PERSON WHERE UserName=?";
			//prepStmt = cx.prepareStatement(sQuery);
			prepStmt = cx.prepareStatement(sQuery, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
			prepStmt.setString(1, szCanonicalName);        
			rs = prepStmt.executeQuery();
			if(rs.next())
			{
				loginResult.FirstName = rs.getString("FirstName");
				loginResult.LastName = rs.getString("LastName");
				loginResult.UserName = rs.getString("UserName");            
				loginResult.ReturnCode=LoginResult.LOGIN_RESULT_SUCCESS;            
			}			
		}
		catch(Exception e)
		{
			SysCtx.doError(e.toString(), this);
		}
		finally
		{
			Util.closeJDBC(rs);
			Util.closeJDBC(prepStmt);
			SysCtx.releaseSystemConnection(cx);
		}
		return loginResult;
	}



	/**
	 * Walks the group and its nested groups breadth first. Group definitions come from the
	 * cache where possible, a single connection is opened only on a cache miss. Each group
	 * is visited at most once (case insensitive) so cyclic nesting cannot loop, and the walk
	 * is capped at MAX_GROUPS_VISITED.
	 */
	private boolean isUserInGroupPrivate(SessionContext sessCtx, String sGroup)
	{
		if(sGroup==null || sGroup.length()==0) return false;
		SysCtx.doDebug(pmaLog.DEBUGLEVEL_FULL, "isUserInGroupPrivate(%s->%s)", new String[]{sessCtx.getUserName(), sGroup}, this);

		X500Name nmUser = sessCtx.getX500Name();
		HashSet<String> visited = new HashSet<>();
		ArrayDeque<String> queue = new ArrayDeque<>();
		String sGroupUpper = sGroup.toUpperCase();
		visited.add(sGroupUpper);
		queue.add(sGroupUpper);

		GroupLookup lookup = new GroupLookup();
		try
		{
			while(!queue.isEmpty())
			{
				GroupEntry entry = getGroupEntry(queue.poll(), lookup);
				for(int i=0; i<entry.members.length; i++)
				{
					String sMember = entry.members[i];
					//check *=All, exact match, partial match (username must be longer than result!)
					X500Name nmMember = new X500Name(sMember);
					if(sMember.equals("*") || nmUser.equals(nmMember) || nmUser.matches(nmMember))
					{
						SysCtx.doDebug(pmaLog.DEBUGLEVEL_VERBOSE, "User '%s' is in group '%s'", new String[]{sessCtx.getUserName(), sGroup}, sessCtx);
						return true;
					}
					String sMemberUpper = sMember.toUpperCase();
					if(entry.isGroup[i] && visited.add(sMemberUpper))
					{
						if(visited.size()>MAX_GROUPS_VISITED)
						{
							SysCtx.doError("isUserInGroup() group nesting of '%s' exceeds %s groups, stopping", new String[]{sGroup, String.valueOf(MAX_GROUPS_VISITED)}, this);
							return false;
						}
						queue.add(sMemberUpper);
					}
				}
			}
		}
		catch (Exception sqle)
		{
			SysCtx.doError("HTTPRequest.GroupNestRecurse", new String[]{sqle.getMessage()}, this);
		}
		finally
		{
			Util.closeJDBC(lookup.stmt);
			SysCtx.releaseSystemConnection(lookup.cx);
		}
		return false;
	}

	/**
	 * Get the members of the group from the cache, or from the database on a miss.
	 * Groups that do not exist are cached as empty so they are not queried every time.
	 * Concurrent misses on the same group each query and the last one wins, which is harmless.
	 * @param sGroupUpper the group name in upper case
	 */
	private GroupEntry getGroupEntry(String sGroupUpper, GroupLookup lookup) throws Exception
	{
		long lNow = System.currentTimeMillis();
		if(m_lGroupCacheMS>0)
		{
			GroupEntry entry = m_htGroupCache.get(sGroupUpper);
			if(entry!=null && entry.lExpires>lNow) return entry;
		}

		SysCtx.doDebug(pmaLog.DEBUGLEVEL_FULL, "getGroupEntry(%s) loading from database", new String[]{sGroupUpper}, this);
		if(lookup.cx==null) lookup.cx = SysCtx.getSystemConnection();
		if(lookup.stmt==null) lookup.stmt = lookup.cx.prepareStatement(GROUP_MEMBER_QUERY, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);

		ArrayList<String> members = new ArrayList<>();
		ArrayList<Boolean> memberIsGroup = new ArrayList<>();
		ResultSet rs = null;
		try
		{
			lookup.stmt.setString(1, sGroupUpper);
			rs = lookup.stmt.executeQuery();
			while(rs.next())
			{
				String sMember = Util.trimSpaces(rs.getString(1));
				if(sMember==null || sMember.length()==0) continue;
				members.add(sMember);
				memberIsGroup.add(rs.getObject(2)!=null);
			}
		}
		finally
		{
			Util.closeJDBC(rs);
		}

		boolean isGroup[] = new boolean[memberIsGroup.size()];
		for(int i=0; i<isGroup.length; i++) isGroup[i] = memberIsGroup.get(i).booleanValue();
		GroupEntry entry = new GroupEntry(members.toArray(new String[members.size()]), isGroup, lNow + m_lGroupCacheMS);

		if(m_lGroupCacheMS>0)
		{
			//bound junk names, eg permissions naming groups that do not exist
			if(m_htGroupCache.size()>=MAX_GROUP_CACHE_ENTRIES) m_htGroupCache.clear();
			m_htGroupCache.put(sGroupUpper, entry);
		}
		return entry;
	}
}