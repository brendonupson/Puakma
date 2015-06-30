package puakma.util.math.formulacalculator;
import java.util.ArrayList;

/**
 * @author Shahedur Rahman gone crazy
 * @version 1.0.0
 */
public class StringUtil 
{

	private StringUtil(){}

	/**
	 * Just a utility method to see if a string matches any strings in the array 
	 * Returns the index of the first match found or -1 if not found.
	 * @param s
	 * @param sMatch
	 * @return
	 */
	public static final int isStringMatchingIgnoreCase(String s, Object[] sMatch)
	{
		return isStringMatching(s, sMatch, true);
	}
	
	/**
	 * Just a utility method to see if a string matches any strings in the array 
	 * Returns the index of the first match found or -1 if not found.
	 * @param s
	 * @param sMatch
	 * @return
	 */
	public static final int isStringMatchingExactly(String s, Object[] sMatch)
	{
		return isStringMatching(s, sMatch, false);
	}
	
	/**
	 * Just a utility method to see if a string matches any strings in the array 
	 * Returns the index of the first match found or -1 if not found.
	 * @param s
	 * @param sMatch
	 * @return
	 */
	private static final int isStringMatching(String s, Object[] sMatch, boolean bIgnoreCase)
	{
		if(s!=null && sMatch!=null)
			for(int i=0; i<sMatch.length; i++)
			{
				if(bIgnoreCase)
				{
					if(s.equalsIgnoreCase(String.valueOf(sMatch[i]))) return i;
				}
				else
					if(s.equals(String.valueOf(sMatch[i]))) return i;
			}
		return -1;
	}

	/**
	 * Just a utility method to see if a string starts with any strings in the array 
	 * Returns the index of the first match found or -1 if not found.
	 * @param s
	 * @param sMatch
	 * @return
	 */
	public static final int isStringPrefixedIgnoreCase(String s, Object[] sMatch)
	{ 
		if(s!=null && sMatch!=null)
		{
			s = s.toLowerCase();
			for(int i=0; i<sMatch.length; i++)
				if(s.startsWith(String.valueOf(sMatch[i]).toLowerCase())) return i;
		}
		return -1;
	}

	/**
	 * Just a utility method to see if a string ends with any strings in the array 
	 * Returns the index of the first match found or -1 if not found.
	 * @param s
	 * @param sMatch
	 * @return
	 */
	public static final int isStringSuffixedIgnoreCase(String s, Object[] sMatch)
	{
		if(s!=null && sMatch!=null)
		{
			s = s.toLowerCase();
			for(int i=0; i<sMatch.length; i++)
				if(s.endsWith(String.valueOf(sMatch[i]).toLowerCase())) return i;
		}
		return -1;
	}

	/**
	 * Used instead of String.replaceAll(). This does not take regular expression.
	 * @param sBlock
	 * @param sMatch
	 * @param sReplace
	 * @return
	 */
	public static String replaceAll(String sBlock, String sMatch, String sReplace) 
	{
		if(sBlock==null || sMatch==null || sReplace==null) return null;
		char cMatch[] = sMatch.toCharArray();
		char c[] = sBlock.toCharArray();
		int iMatchLength = cMatch.length;
		int iBlockLength = c.length;
		StringBuilder sb = new StringBuilder(iBlockLength);
		int i=0;
		while(i<iBlockLength)
		{
			boolean bMatch = false;
			if((iBlockLength-i)>=iMatchLength)
			{
				bMatch = true;
				for(int j=0; j<iMatchLength; j++)
				{
					if(c[i+j]!=cMatch[j])
					{
						bMatch = false;
						break;
					}
				}
			}
			if(bMatch) 
			{
				sb.append(sReplace);
				i+=iMatchLength;
			}
			else
			{
				sb.append(c[i]);
				i++;
			}
		}
		return sb.toString();
	}

	/**
	 * Checks the String for Substring from the array
	 * Returns the index of the first match found or -1 if not found.
	 * @param s
	 * @param sMatch
	 * @return
	 */
	public static int isSubstringPresentIgnoreCase(String s, Object[] sMatch) 
	{
		if(s!=null && sMatch!=null)
		{
			s = s.toLowerCase();
			for(int i=0; i<sMatch.length; i++)
			{
				int iPos = s.indexOf(String.valueOf(sMatch[i]).toLowerCase());
				if(iPos>=0) return i;
			}
		}
		return -1;
	}

	/**
	 * Splits a String into a an array
	 * @return null if either of the inputs are null
	 */
	public static String[] splitString(String sInput, String sSeperator)
	{
		return splitStringIgnoreNesting(sInput, sSeperator, null, null);
	}
	
	
	/**
	 * Splits a String into a an array and ignores separator inside the nest start and end.
	 * @return null if either of the inputs are null
	 */
	public static String[] splitStringIgnoreNesting(String sInput, String sSeperator, String sNestStart, String sNestEnd)
	{
		//TODO needs to take into account nested functions...
		if(sInput==null || sSeperator==null) return null;
		ArrayList arr = new ArrayList();

		int iPos = sInput.indexOf(sSeperator);
		while(iPos>=0)
		{
			if(sNestStart!=null && sNestStart.length()>0 && sNestEnd!=null && sNestEnd.length()>0)
				while(isInsideNest(iPos, sInput, sNestStart, sNestEnd)) iPos = sInput.indexOf(sSeperator, iPos+1);
			String sElement = sInput.substring(0, iPos);
			sInput = sInput.substring(iPos+sSeperator.length(), sInput.length());
			arr.add(sElement);
			iPos = sInput.indexOf(sSeperator);
		}
		if(sInput.length()!=0) arr.add(sInput);

		String s[] = new String[arr.size()];
		for(int i=0; i<s.length; i++)
			s[i] = String.valueOf(arr.get(i));

		return s;
	}
	/**
	 * Checks whether the position is inside a nest or not. Useful for ignoring operators inside a variable. 
	 * eg iPos = 5, sBlock = "hvh(khg  d]", sNestStart = "(" sNestEnd = "]" will return true.
	 * @param iPos
	 * @param sBlock
	 * @param sNestStart
	 * @param sNestEnd
	 * @return
	 */
	public static boolean isInsideNest(int iPos, String sBlock, String sNestStart, String sNestEnd)
	{
		if(sBlock==null || sBlock.length()==0 || iPos<=0 || iPos>sBlock.length()) return false;
		int iNEST_START = sBlock.lastIndexOf(sNestStart, iPos);
		int iNEST_END = sBlock.indexOf(sNestEnd, iNEST_START);
		if(iNEST_START>=0 && iNEST_END>=0 && iPos<iNEST_END) return true;
		return false;
	}
	
	/**
	 * Returns an array of all possible combinations of mixed cases from a string. Can be huge.
	 * Example usage: Class.forName() which needs an exact match of a string. 
	 * @param sBlock
	 * @return
	 */
	public static String[] getPossibleCombinationOfMixedCases(String sBlock)
	{
		if(sBlock==null || sBlock.length()==0) return new String[]{}; 
		//ArrayList arr = new ArrayList(sBlock.length()*sBlock.length());
		char[] c = sBlock.toCharArray();
		while(c.length>0)
		{
			//TODO
		}
		return null;
	}
}//class
