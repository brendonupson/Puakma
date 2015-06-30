package puakma.util.math.formulacalculator;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Hashtable;

import puakma.util.math.formulacalculator.functions.BaseFunction;

/**
 * @author Shahedur Rahman
 * @version 2.0
 */
public class FormulaCalculator
{
	//Start Num_Oper_Num operators.
	public static final String PLUS = "+";
	public static final String MINUS = "-";
	public static final String TIMES = "*";
	public static final String DIVIDE_BY = "/";
	public static final String POW = "^";
	public static final String TIMES_TEN_POW = "e";
	public static final String MOD = "%"; //added in version 1.1.0
	
	//boolean operators
	public static final String GREATER_THAN = ">"; 
	public static final String LESS_THAN = "<";
	public static final String GREATER_THAN_OR_EQUAL = ">=";
	public static final String LESS_THAN_OR_EQUAL = "<=";
	public static final String NOT_EQUAL = "!=";
	public static final String EQUAL = "=";

	private final String m_sNum_Oper_Num[] = {TIMES_TEN_POW, POW, MOD, DIVIDE_BY, TIMES, PLUS, MINUS,
			GREATER_THAN, GREATER_THAN_OR_EQUAL, LESS_THAN, LESS_THAN_OR_EQUAL, NOT_EQUAL, EQUAL};
	private final int TYPE_NUM_oper_NUM = 1;
	//Start Oper_Num operators. Can be implemented as external functions. 
	//That more effective as that will take care of recursions. Only add here when there cannot be any recursion in it.
	private final String m_sOper_Num[] = {};
	private final int TYPE_oper_NUM = 2;

	//Start Constants. These will be replaced with the system variables where ever they are found
	public static final String PI = "pi()";
	public static final String E = "e()";
	private final String[] m_SupportedConstants = {PI, E};

	private String m_sBlock = "";
	private String m_sLogTab = "";

	private Package m_defaultPackage = this.getClass().getPackage(); //note may be null if classloader does not support it
	private String m_sDefaultExternalFunctionPackage = m_defaultPackage==null ? "puakma.util.math.formulacalculator.functions" : m_defaultPackage.getName() + ".functions";
	private String m_sCustomExternalFunctionPackage = null;


	//Variable STUFF.
	public static final String VARIABLE_START = "{";
	public static final String VARIABLE_END = "}";
	private final String SYSTEM_VAR_PREFIX = "_fc_";

	public static final String BRACKET_START = "(";
	public static final String BRACKET_END = ")";


	private final String[] m_OperationOrder = {TIMES_TEN_POW, POW, MOD, DIVIDE_BY, TIMES, PLUS, MINUS,
			GREATER_THAN, GREATER_THAN_OR_EQUAL, LESS_THAN, LESS_THAN_OR_EQUAL, NOT_EQUAL, EQUAL};

	//Currently supported operators internally. This list must include only all the operators inside this object. 
	private final String[] m_SupportedOperators = {PLUS, MINUS, TIMES, DIVIDE_BY, BRACKET_START, BRACKET_END, POW, TIMES_TEN_POW, MOD,
			//the following order IS IMPORTANT
			GREATER_THAN_OR_EQUAL, GREATER_THAN, LESS_THAN_OR_EQUAL, LESS_THAN, NOT_EQUAL, EQUAL};

	private Double m_dResult = null;
	private boolean m_bValidityCheck = true;
	private ArrayList arrOperations = new ArrayList();
	private boolean m_bFormulaEvaluated = false;

	private double m_dReplaceDenominator0 = 0;
	private double m_dReplaceDivideBy0Result = Double.NaN;
	private Hashtable HT_FORMULA = new Hashtable();

	private final int POWER = 0;
	private final int SINE = 1;
	private final int COSINE = 2;
	private final int TANGENT = 3;
	private final int COSECANT = 4;
	private final int SECANT = 5;
	private final int COTANGENT = 6;
	private final int LOG_base_e = 7;
	private final int DEG_TO_RAD = 14;
	private final int RAD_TO_DEG = 15;
	private final int NUMBER_TO_POWER_e = 16;
	private final int SQUARE_ROOT = 17;

	private Hashtable m_htVariables = new Hashtable();
	private ArrayList m_arrValidVariables = new ArrayList();

	//logging stuff
	private StringBuilder m_sLogBook = new StringBuilder(512);
	
	/**
	 * Default constructor.
	 */
	public FormulaCalculator()
	{
		putConstantVariables();
	}

	/**
	 * This puts the default constants into the variable list whenever you create a new FormulaCalculator or remove all variables
	 */
	private void putConstantVariables() 
	{
		m_htVariables.put(SYSTEM_VAR_PREFIX+"pi", new Double(StrictMath.PI));
		m_htVariables.put(SYSTEM_VAR_PREFIX+"e", new Double(StrictMath.E));
	}

	/**
	 * Only used by the external functions. Not to be used by public.
	 * @param sFormula
	 * @param htVariables
	 * @throws InvalidVariableException 
	 */
	public FormulaCalculator(String sFormula, Hashtable htVariables) throws InvalidVariableException
	{
		checkAndSetAllVariables(htVariables);// must be done first so in setFormula, any unique variable creation will not override the old ones. 
		setFormula(sFormula);
	}
	/**
	 * This method checks the entire variable list for valid variable names. Each is checked and put in to the internal variable list.
	 * @param htVariables
	 * @throws InvalidVariableException
	 */
	private void checkAndSetAllVariables(Hashtable htVariables) throws InvalidVariableException 
	{
		if(htVariables!=null)
		{
			Enumeration en = htVariables.keys();
			while(en.hasMoreElements())
			{
				String sVariable = (String) en.nextElement();
				if(!isValidVariableName(sVariable)) throw new InvalidVariableException("INVALID NAME: ["+sVariable+"]");
			}//while
			//FIXME if we accidentally pass an uppercase variable name, it will not be found later!
			//We really should do a copy here rather than a plain set
			m_htVariables = htVariables;
		}//if
		putConstantVariables();
	}

	/**
	 * This is required to evaluate the formula into one number. If the formula is impossible to evaluate then 
	 * it returns a false. 
	 * @return
	 */
	public boolean evaluate()
	{
		if(m_bFormulaEvaluated) return true;
		Double dNaN = new Double(Double.NaN);
		String sBlock = m_sBlock.trim();
		if(!parseFormula(sBlock)) 
		{
			log("Parsing of ["+sBlock+"] failed.");
			m_dResult = dNaN;
			return false;
		}

		
		//for(int i=0; i<arrOperations.size(); i++)
		//	log("DEBUG: Array["+i+"] = "+arrOperations.get(i));
		

		for(int i=0; i<m_OperationOrder.length; i++) // evaluates according to the order of operations.
		{
			String sCurrentOperator = m_OperationOrder[i];
			int iOperatorIndex = arrOperations.indexOf(sCurrentOperator);
			int iOperatorType = getOperatorType(sCurrentOperator);
			while(iOperatorIndex>=0)
			{
				Double dValue1 = new Double(0);
				if(iOperatorType==TYPE_NUM_oper_NUM) dValue1 = (Double) arrOperations.get(iOperatorIndex-1);
				Double dValue2 = (Double) arrOperations.get(iOperatorIndex+1);
				if(!replaceCalculationWithResults(dValue1.doubleValue(), dValue2.doubleValue(), m_OperationOrder[i], iOperatorIndex))
				{
					m_dResult = dNaN;
					return false;
				}
				iOperatorIndex = arrOperations.indexOf(sCurrentOperator);
			}//while
		}//for

		if(arrOperations.size()==1) 
		{
			try
			{
				m_dResult = (Double) arrOperations.get(0);
				arrOperations.clear();
				m_bFormulaEvaluated = true;
			} 
			catch(ClassCastException c){log("Result is not a number.");}
		}

		return m_bFormulaEvaluated;
	}

	/**
	 * Returns the string that you can use instead of the operation you are trying to perform.
	 * This way you can be sure of adding the right double to its highest possible precision.
	 * The system will automatically add the result to the variable list with a system generated variable prefixed with "_sys".
	 * So you can not replace this variable later.
	 * @param iType type of function to apply.
	 * @param dNumAppliedOn function applied on this number. This number is required.
	 * @param dNumToApply this number applied to the other eg. 5 to the power 2 (i.e. 5 square) this number will be 2.
	 * @param this is the string to be prefixed to make the operation more readable.
	 * @return null if result is NaN or infinity.
	 */
	private String addDoubleToFormula(int iType, double dNumAppliedOn, double dNumToApply, String sPrefix)
	{
		double d = Double.NaN;
		if((iType==SINE || iType==COSINE || iType==TANGENT || iType==COTANGENT || iType==SECANT || iType==COSECANT) 
				&& dNumToApply==0)
			dNumAppliedOn = StrictMath.toRadians(dNumAppliedOn);

		String sSecondValue = "";

		switch(iType)
		{
		case POWER: 
			sSecondValue = "_["+dNumToApply+"]";
			d = StrictMath.pow(dNumAppliedOn, dNumToApply); break;
		case SINE: d = StrictMath.sin(dNumAppliedOn);break;
		case COSINE: d = StrictMath.cos(dNumAppliedOn);break;
		case TANGENT: d = StrictMath.tan(dNumAppliedOn);break;
		case COSECANT: d = StrictMath.asin(dNumAppliedOn);break;
		case SECANT: d = StrictMath.acos(dNumAppliedOn);break;
		case COTANGENT: d = StrictMath.atan(dNumAppliedOn);break;
		case LOG_base_e: d = StrictMath.log(dNumAppliedOn);break;
		case DEG_TO_RAD: d = StrictMath.toRadians(dNumAppliedOn);break;
		case RAD_TO_DEG: d = StrictMath.toDegrees(dNumAppliedOn);break;
		case NUMBER_TO_POWER_e: d = StrictMath.exp(dNumAppliedOn);break;
		case SQUARE_ROOT: d = StrictMath.sqrt(dNumAppliedOn);break;
		}

		//Adding to the variable list.
		Double db = new Double(d);
		if(db!=null && !db.isInfinite() && !db.isNaN())
		{
			final String sAutoVariable = SYSTEM_VAR_PREFIX+sPrefix+"["+dNumAppliedOn+"]"+sSecondValue; 
			if(!m_htVariables.containsKey(sAutoVariable)) m_htVariables.put(sAutoVariable, db);
			return "{"+sAutoVariable+"}";
		}
		return null;
	}


	/**
	 * Returns the operation type which is used to determine how the operation should take place.
	 * For e.g. 2+3 or 2e3 or 3/4 is a "Number Operation Number" type, sin50 is a "Operation Number" type.
	 * @param sOperator
	 * @return
	 */
	private int getOperatorType(String sOperator) 
	{
		if(StringUtil.isStringMatchingIgnoreCase(sOperator, m_sNum_Oper_Num)>=0) return TYPE_NUM_oper_NUM;
		if(StringUtil.isStringMatchingIgnoreCase(sOperator, m_sOper_Num)>=0) return TYPE_oper_NUM;
		return -1;
	}

	/**
	 * Calculates and then replaces the arrOperations with the result.
	 * @param dValue1
	 * @param dValue2
	 * @param sOperator
	 * @param iOperatorIndex
	 */
	private boolean replaceCalculationWithResults(double dValue1, double dValue2, String sOperator, int iOperatorIndex) 
	{
		double dEquals = 0;
		int iElementsToRemove = 3; // this may change for example sin 50 will only remove 2 elements after calculations.
		
		if(sOperator.equals(GREATER_THAN)) dEquals = dValue1>dValue2? 1 : 0;
		if(sOperator.equals(GREATER_THAN_OR_EQUAL)) dEquals = dValue1>=dValue2? 1 : 0;
		if(sOperator.equals(LESS_THAN)) dEquals = dValue1<dValue2? 1 : 0;
		if(sOperator.equals(LESS_THAN_OR_EQUAL)) dEquals = dValue1<=dValue2? 1 : 0;
		if(sOperator.equals(EQUAL)) dEquals = dValue1==dValue2? 1 : 0;
		if(sOperator.equals(NOT_EQUAL)) dEquals = dValue1!=dValue2? 1 : 0;
		//TODO add boolean operators
		
		if(sOperator.equals(PLUS)) dEquals = dValue1+dValue2;
		if(sOperator.equals(MINUS)) dEquals = dValue1-dValue2;
		if(sOperator.equals(TIMES)) dEquals = dValue1*dValue2;
		if(sOperator.equals(DIVIDE_BY))
		{
			if(dValue2!=0) dEquals = dValue1/dValue2;
			else 
			{
				if(m_dReplaceDenominator0==0 && Double.isNaN(m_dReplaceDivideBy0Result)) 
				{
					log("Division by zero has occurred.");
					return false;
				}
				else
				{
					log("Division by zero has occurred in the original calculation.");
					if(m_dReplaceDenominator0!=0)
					{
						log("But the denominator is replaced by user defined number: "+m_dReplaceDenominator0);
						dEquals = dValue1/m_dReplaceDenominator0;
					}
					else
					{
						log("But the result is replaced by user defined number: "+m_dReplaceDivideBy0Result);
						dEquals = m_dReplaceDivideBy0Result;
					}
				}
			}
		}
		if(sOperator.equals(MOD)) dEquals = dValue1%dValue2;
		if(sOperator.equals(POW)) dEquals = StrictMath.pow(dValue1, dValue2);
		if(sOperator.equals(TIMES_TEN_POW)) dEquals = dValue1 * StrictMath.pow(10, dValue2);

		
		Double dResult = new Double(dEquals);
		if(dResult.isInfinite() || dResult.isNaN()) return false; 

		int iType = getOperatorType(sOperator);
		switch(iType)
		{
		case TYPE_NUM_oper_NUM: 
			arrOperations.add(iOperatorIndex-1, dResult); break; //adding in the place of the first number
		case TYPE_oper_NUM: 
			arrOperations.add(iOperatorIndex+2, dResult); // adding in the place after the second number 
			iElementsToRemove = 2; break;
		}
		for(int i=0; i<iElementsToRemove; i++) arrOperations.remove(iOperatorIndex);
		return true;
	}

	/**
	 * Returns a the block of formula after it has been checked for validity. Also performs some necessary operations
	 * such as replacing all the constants with variables with double values, replacing all the external functions 
	 * with variables with values etc.  
	 * NOTE: sequence of these checks are important. Don't change it unless you know what you are doing.
	 * And trust me you don't... 
	 * @param sBlock
	 * @return Null if not valid.
	 */
	private String getValidityCheck(String sBlock)
	{
		//TODO write code to ignore external functions such as AVERAGE({A}, 2, 3, 5, {B}+{C}) or SIN(SIN(50))
		if(sBlock==null || sBlock.length()==0) return null;
		sBlock = StringUtil.replaceAll(sBlock.toLowerCase(), " ", ""); //strip spaces

		//Constants replacement
		sBlock = replaceConstants(sBlock);

		if(!checkForBracketSets(BRACKET_START, BRACKET_END, sBlock)) return null; // checks for valid bracket sets.
		if(!checkForBracketSets(VARIABLE_START, VARIABLE_END, sBlock)) return null; // checks for valid bracket sets.

		sBlock = containsIllegalChar(sBlock);
		if(sBlock==null) return null; //if any illegal character found, bail out
		//All external functions are by now replaced with a unique variable which gets calculated during parsing. 		
		//so regular validity checks for the rest of the block is carried out after this.


		if(foundNoOperatorsAroundBrackets(sBlock)) return null; //brackets must be surrounded by operators.

		sBlock = StringUtil.replaceAll(sBlock, BRACKET_START+BRACKET_END, "0"); //Converts "1+()" to "1+0" or "1*()" to "1*0"
		sBlock = StringUtil.replaceAll(sBlock, VARIABLE_START+VARIABLE_END, "0"); //Converts "1+{}" to "1+0" or "1*{}" to "1*0"

		sBlock = parseMultipleOperators(sBlock);
		if(sBlock==null) return null; // converts "-+" into "-" and if illegal combinations found, bail out.

		if(foundIllegalDots(sBlock)) return null; // check for "34.467.43" numbers.
		if(foundIllegalPrefixSuffix(sBlock)) return null; // check for "56+4+"

		return sBlock;
	}

	/**
	 * Parses and if successful adds the numbers and operations into an array. 
	 * @return
	 * @throws InvalidFormulaException 
	 */
	private boolean parseFormula(String sBlock) 
	{
		if(m_bValidityCheck)// saves parsing if we know that the string is a valid math block.
		{
			String sNewFormula = sBlock;
			if(!checkVariableValidity(sBlock, null)) return false; //if a variable is not set.
			sBlock = getValidityCheck(sBlock);
			try{if(sBlock==null) throw new InvalidFormulaException(sNewFormula+" Last Error: "+getLastError());}// Validity Check Failed
			catch(InvalidFormulaException e){e.printStackTrace(); log(e.toString()); return false;}

			//TODO other parsing for validity.
		}

		int iNextOperatorPos = getOperatorPos(sBlock, 0);
		int iParsePass = 0;
		while(sBlock.length()>0)
		{
			log("Pass "+iParsePass+"["+sBlock+"]");
			if(iNextOperatorPos==0)
			{
				String sOperator = getOperator(iNextOperatorPos, sBlock);

				if(!isValidFirstOperator(sOperator)) return false;
				if(sOperator.equals(BRACKET_START))//get the end of this bracket and add the inside into a new FormulaCalculator
				{
					int iBracketEnd = getBracketEnd(iNextOperatorPos, sBlock);
					if(iBracketEnd<0) return false;
					String sNewBlock = sBlock.substring(iNextOperatorPos+1, iBracketEnd);
					log("New formula: ["+sNewBlock+"]");
					FormulaCalculator fc = new FormulaCalculator(sNewBlock, true, m_htVariables, m_dReplaceDenominator0, m_dReplaceDivideBy0Result, m_sLogTab+" ");
					if(fc.evaluate())
					{
						double dResult = fc.getResult();
						log(sNewBlock+" = "+dResult+"\r\n"+m_sLogTab+"-------");

						arrOperations.add(new Double(dResult));

						sBlock = sBlock.substring(iBracketEnd+1);
						iNextOperatorPos = getOperatorPos(sBlock, 0);
						if(iNextOperatorPos>0)
						{
							log("In formula ["+fc.getCurrentFormula()+"]: no operator is found between bracket end and the next number.");
							return false; 
						}
						if(iNextOperatorPos==0) 
						{
							sBlock = getBlockAfterAddingOperator(sBlock, iNextOperatorPos);
							iNextOperatorPos = getOperatorPos(sBlock, 0);
						}
					}//if evaluate
					else
					{
						log("\r\n**************************\r\n"+fc.getLog()+"\r\n**************************\r\n");
						log("["+fc.getCurrentFormula()+"] Resulted in a NaN.");
						return false;
					}
				}// if BracketSTART
				else
				{
					if(StringUtil.isStringMatchingIgnoreCase(sOperator, new String[]{PLUS, MINUS})>=0) //other operators such as "sin" may later be supported.
					{
						if(sBlock.startsWith(MINUS))
						{
							arrOperations.add(new Double(-1)); // -1*(whatever) or -1*{whatever}
							arrOperations.add(TIMES);
						}
						sBlock = sBlock.substring(iNextOperatorPos+1);
						iNextOperatorPos = getOperatorPos(sBlock, 0);
					}
					/*else
					{
						//Place holder for future support
						String sOperator = getOperator(iPositionOfFirstOperator, sBlock);

					}*/
				}
			}
			else if(iNextOperatorPos>0)
			{
				String sNumber = sBlock.substring(0, iNextOperatorPos);
				if(!addValueToArray(sNumber)) return false; //Something serious has happened
				sBlock = getBlockAfterAddingOperator(sBlock, iNextOperatorPos);
				iNextOperatorPos = getOperatorPos(sBlock, 0);
			}
			else if(iNextOperatorPos<0) //no more operators left, String must be empty or a number
			{
				if(!addValueToArray(sBlock)) return false; //Something serious has happened
				sBlock = "";
			}
			iParsePass++;
		}//while
		return true;
	}

	/**
	 * If its a number then adds it to the array. OR adds the value of the variable in to the array.
	 * Checks formula variables for validity. 
	 * @param sBlock
	 */
	private boolean addValueToArray(String sBlock) 
	{//log("DEBUG: "+sBlock);
		if(isVariable(sBlock))
		{
			String sVariable = sBlock.substring(1, sBlock.length()-1);
			Object obj = getVariableValue(sVariable);
			if(obj==null)
			{
				log(sVariable+" is not set. Evaluation is not possible.");
				return false; 
			}
			try 
			{
				if(obj instanceof Double)
				{
					Double dValue = (Double)obj;
					if(dValue.isNaN()) throw new InvalidVariableException("INVALID VALUE: Variable:["+sVariable+"] Value:[NaN]");
					arrOperations.add(dValue);
				}
				else if(obj instanceof String)
				{
					String sFormula = String.valueOf(obj);
					if(!m_arrValidVariables.contains(sVariable)) 
					{
						sFormula = getValidityCheck(sFormula);
						if(sFormula==null) throw new InvalidVariableException("INVALID VALUE: Variable:["+sVariable+"] Value:["+sFormula+"]");
						m_htVariables.remove(sVariable); 
						m_htVariables.put(sVariable, sFormula); //Putting the validated formula
						m_arrValidVariables.add(sVariable); //Adding to the list of Valid Variables.
					}	
					return parseFormula(BRACKET_START+sFormula+BRACKET_END);
				}
			}
			catch (Exception e) {e.printStackTrace(); log(e.toString()); return false;}
		}
		else
		{
			try
			{
				Double d = Double.valueOf(sBlock);
				arrOperations.add(d);
			} 
			catch(Exception e)
			{
				log("Can not parse ["+sBlock+"] into a number.");
				return false;
			}	
		}
		return true;
	}

	/**
	 * Adds operator to the arrOperations and returns remaining block
	 * @param sBlock
	 * @param iPositionOfOperator
	 * @return
	 */
	private String getBlockAfterAddingOperator(String sBlock, int iPositionOfOperator) 
	{
		String sOperator = getOperator(iPositionOfOperator, sBlock);
		arrOperations.add(sOperator);
		return sBlock.substring(iPositionOfOperator+sOperator.length());
	}

	/**
	 * Checks for characters that are not legal in a math block, 
	 * variables are checked for operators or dots or brackets.
	 * All external functions are replaced with a unique variable which gets calculated during parsing. 
	 * @return
	 */
	private String containsIllegalChar(String sBlock) 
	{	
		String cValidStringSet[] = {"0","1","2","3","4","5","6","7","8","9","."};
		ArrayList arrValidStrings = new ArrayList(m_SupportedOperators.length+20);
		for(int i=0; i<m_SupportedOperators.length; i++)
			arrValidStrings.add(m_SupportedOperators[i]);
		for(int i=0; i<cValidStringSet.length; i++)
			arrValidStrings.add(cValidStringSet[i]);

		String sInValidCharInVariables[] = {PLUS, MINUS, TIMES, DIVIDE_BY, BRACKET_START, BRACKET_END, VARIABLE_START};
		StringBuilder sbBlock = new StringBuilder(sBlock.length());

		boolean bSwitchVariableOn = false;
		while(sBlock.length()>0)
		{
			if(bSwitchVariableOn) //if within variable
			{
				int iPrefixIndex = StringUtil.isStringPrefixedIgnoreCase(sBlock, sInValidCharInVariables);
				if(iPrefixIndex>=0)
				{
					log("Variable character ["+sInValidCharInVariables[iPrefixIndex]+"] is not allowed.");
					return null;
				}
			}
			else // if outside variable check if the current String begins with a VAR_START.
				if(sBlock.startsWith(VARIABLE_START)) bSwitchVariableOn = true;

			if(!bSwitchVariableOn) //if not within a variable or a VAR_START
			{
				int iPrefixIndex = StringUtil.isStringPrefixedIgnoreCase(sBlock, arrValidStrings.toArray());
				if(iPrefixIndex<0) 
				{
					//Checking so that any external function must be followed by a bracket.
					int iBracketPos = getOperatorPos(sBlock, 0, new String[]{BRACKET_START});
					int iOtherOperatorPos = getOperatorPos(sBlock, 0, new String[]{PLUS, MINUS, TIMES, DIVIDE_BY, BRACKET_END, POW});
					if(iBracketPos<0 || (iOtherOperatorPos>=0 && iBracketPos>iOtherOperatorPos))
					{
						log("Illegal character ["+sBlock.charAt(0)+"] is found.");
						return null;
					}
					String sFunction = sBlock.substring(0, iBracketPos);
					int iBracketEnd = getBracketEnd(sFunction.length(), sBlock);
					//adding the function's value in the variable list replacing this with a variable.
					String sFunctionParam = sBlock.substring(sFunction.length()+1, iBracketEnd);
					try 
					{
						double dFormulaResult = parseExternalFunction(sFunction, sFunctionParam);
						sbBlock.append(VARIABLE_START+makeUniqueVariable(dFormulaResult)+VARIABLE_END);
					} 
					catch (InvalidFormulaException e) 
					{
						log(e.toString());
						e.printStackTrace();
						return null;
					}
					sBlock = sBlock.substring(iBracketEnd+1);
				}
				else
				{ //moves 1 operator or a digit at a time.
					String sPrefix = String.valueOf(arrValidStrings.get(iPrefixIndex));
					sBlock = sBlock.substring(sPrefix.length()); //removes the Prefix from the sBlock
					sbBlock.append(sPrefix); // and appends it to the Stringbuffer.
				}
			}
			else 
			{
				if(sBlock.startsWith(VARIABLE_END)) bSwitchVariableOn = false;
				if(!sBlock.startsWith(" ")) sbBlock.append(sBlock.charAt(0));
				sBlock = sBlock.substring(1); //moves 1 character at a time.
			}

		}//while
		if(sbBlock.length()==0) 
		{
			log("No Mathematical expression or number found.");
			return null;
		}
		return sbBlock.toString(); 
	}


	/**
	 * Converts "++___+__" into "-"
	 * @return
	 */
	private String parseMultipleOperators(String sBlock) 
	{
		String sTemp = sBlock;
		//getting rid of all the variables from the string so illegal combinations are not checked in the variable.
		ArrayList arr = getAllVariables(sBlock);
		for(int i=0; i<arr.size(); i++)
			sTemp = StringUtil.replaceAll(sTemp, VARIABLE_START+(String)arr.get(i)+VARIABLE_END, VARIABLE_START+VARIABLE_END);

		ArrayList arrIllegalCombo = new ArrayList(100); //array list of illegal operator combination.
		String sLegalSecondOperator[] = {PLUS, MINUS}; //this are allowed as a legal second operator in a combination as they may represent sign.
		for(int i=0;i<m_sOper_Num.length; i++)
		{
			for(int j=0; j<m_sNum_Oper_Num.length; j++)
			{
				if(StringUtil.isStringMatchingIgnoreCase(m_sNum_Oper_Num[j], sLegalSecondOperator)<0)
					arrIllegalCombo.add(m_sOper_Num[i]+m_sNum_Oper_Num[j]);
			}
			arrIllegalCombo.add(m_sOper_Num[i]+BRACKET_END);
		}
		for(int i=0; i<m_sNum_Oper_Num.length; i++)
		{
			String sNumOperNum = m_sNum_Oper_Num[i];
			for(int j=0; j<m_sNum_Oper_Num.length; j++)
			{
				if(StringUtil.isStringMatchingIgnoreCase(m_sNum_Oper_Num[j], sLegalSecondOperator)<0)
					arrIllegalCombo.add(sNumOperNum+m_sNum_Oper_Num[j]);
			}
			arrIllegalCombo.add(sNumOperNum+BRACKET_END);
			if(!isValidFirstOperator(sNumOperNum)) arrIllegalCombo.add(BRACKET_START+sNumOperNum);
		}

		//log("DEBUG: Number of illegal combinations"+arrIllegalCombo.size()+"\r\n"+arrIllegalCombo.toString());

		if(StringUtil.isSubstringPresentIgnoreCase(sTemp, m_SupportedOperators)<0 && StringUtil.isSubstringPresentIgnoreCase(sTemp, arrIllegalCombo.toArray())>=0) 
		{
			log("Illegal Combinations of Characters found in ["+sBlock+"]");
			return null;
		}

		String sSignCombinations[] = {PLUS+PLUS, MINUS+MINUS, PLUS+MINUS, MINUS+PLUS};

		while(StringUtil.isSubstringPresentIgnoreCase(sBlock, sSignCombinations)>=0)
		{
			sBlock = StringUtil.replaceAll(sBlock, PLUS+PLUS, PLUS);
			sBlock = StringUtil.replaceAll(sBlock, PLUS+MINUS, MINUS);
			sBlock = StringUtil.replaceAll(sBlock, MINUS+PLUS, MINUS);
			sBlock = StringUtil.replaceAll(sBlock, MINUS+MINUS, PLUS);
		}

		return sBlock;
	}


	/**
	 * Determines which operator can be at the very start of a block.
	 * @param sOperator
	 * @return
	 */
	private boolean isValidFirstOperator(String sOperator)
	{
		if(StringUtil.isStringMatchingIgnoreCase(sOperator, new String[]{PLUS, MINUS, BRACKET_START})>=0) return true;
		return false;
	}
	/**  
	 * @return The necessary variable name to put in a formula to represent a constant. Eg.PI. 
	 * If constant passed in is not in the list returns a "0" to keep the formula valid.
	 */
	private final String getConstantVariable(String sConstant)
	{
		if(sConstant.equals(PI)) return "{"+SYSTEM_VAR_PREFIX+"pi}";
		if(sConstant.equals(E)) return "{"+SYSTEM_VAR_PREFIX+"e}";
		return "0";
	}

	/**
	 * Does the calculation for individual external functions. All external functions must be upper case.
	 * @param sFunction will be turned in to uppercase before looking for a class.
	 * @param sBlock
	 * @return
	 * @throws InvalidFormulaException 
	 */
	private double parseExternalFunction(String sFunction, String sBlock) throws InvalidFormulaException
	{		
		Class c = null;
		BaseFunction b = null;
		if(m_sCustomExternalFunctionPackage!=null && m_sCustomExternalFunctionPackage.length()>0)
		{
			try 
			{
				c = Class.forName(m_sCustomExternalFunctionPackage + "." + sFunction.toUpperCase());
				b = (BaseFunction)c.newInstance();
			} 
			catch (Exception e) {}
		}
		else //no custom package provided, so use the system default		
		{
			String sClassToCreate = m_sDefaultExternalFunctionPackage + "." + sFunction.toUpperCase();
			try 
			{
				c = Class.forName(sClassToCreate);
				b = (BaseFunction)c.newInstance();
			} 
			catch (Exception e) {throw new InvalidFormulaException("Function["+sFunction+"] " + sClassToCreate + " "+ e.toString());}
		}
		b.setFunctionVariables(m_htVariables);
		b.setFunctionParameters(sBlock);
		b.setDenominatorAndResultZero(m_dReplaceDenominator0, m_dReplaceDivideBy0Result);
		double dResult = b.getResult();
		if(b.updateGlobalVariables())
		{
			try {checkAndSetAllVariables(b.getFunctionVariables());} 
			catch (InvalidVariableException e) {throw new InvalidFormulaException("Function["+sFunction+"] is trying to set global variables with an invalid syntax. " + e.toString());}
		}
		return dResult;
	}

	/***********************************************************************
	 *START of stuff which u usually don't need to change... 
	 ***********************************************************************/	

	/**
	 * Removes a particular variable or all variables if null or empty string is passed. 
	 * @param sVariable
	 */
	public void removeVariable(String sVariable)
	{
		if(sVariable!=null && sVariable.length()>0) 
			m_htVariables.remove(sVariable.toLowerCase());
		else 
			m_htVariables.clear();

		putConstantVariables();
	}

	/**
	 * Sets up a double to be used to divide the numerator in case a division by zero occurs somewhere.
	 * If you call this last then this overrides replaceDivideByZeroResult()
	 * @param dReplacement
	 * @return false if dReplacement is NaN 
	 */
	public boolean replaceDenominatorZero(double dReplacement)
	{
		if(!Double.isNaN(dReplacement)) 
		{
			m_dReplaceDenominator0 = dReplacement;
			m_dReplaceDivideBy0Result = Double.NaN;
			return true;
		}
		return false;
	}

	/**
	 * Sets up a double to be used to divide the numerator in case a division by zero occurs somewhere. Default is NaN.
	 * If you call this last then this overrides replaceDenominatorZero()
	 * @param dReplacement 
	 */
	public void replaceDivideByZeroResult(double dReplacement)
	{
		m_dReplaceDivideBy0Result = dReplacement;
		m_dReplaceDenominator0 = 0;
	}
	/**
	 * Sets the formula. Returns false if its a invalid formula.
	 * @param sBlock
	 * @return
	 * @throws InvalidFormulaException 
	 */
	public void setFormula(String sNewFormula)
	{
		m_sBlock = sNewFormula;
		arrOperations.clear();
		m_bFormulaEvaluated = false;
	}

	/**
	 * Saves this formula For Future use. Also this becomes the current formula.
	 * @param sFormulaName
	 * @param sBlock
	 * @return false if the formula is not valid
	 * @throws InvalidFormulaException 
	 */
	public void setFormula(String sFormulaName, String sBlock)
	{
		setFormula(sBlock);
		if(HT_FORMULA.containsKey(sFormulaName)) HT_FORMULA.remove(sFormulaName);
		HT_FORMULA.put(sFormulaName, m_sBlock);
	}
	/**
	 * Uses a previously saved formula. If not found returns false and keeps the last used formula intact.
	 * @param sFormulaName
	 * @return
	 */
	public boolean evaluate(String sFormulaName)
	{ 
		if(sFormulaName!=null && HT_FORMULA.containsKey(sFormulaName))
		{
			m_sBlock = (String)HT_FORMULA.get(sFormulaName);
			log("Using formula: ["+m_sBlock+"]");
			arrOperations.clear();
			m_bFormulaEvaluated = false;
			return evaluate();
		}
		return false;
	}
	/**
	 * Called from inside basically to calculate the inside of a bracket as a whole new formula. 
	 * This keeps the inside of a bracket first in the order of operations.
	 * @param sBlock
	 * @param bValidBlock
	 * @param htVariableList
	 */
	private FormulaCalculator(String sBlock, boolean bValidBlock, Hashtable htVariableList, double dReplaceDenominator0, double dReplaceDivisionBy0Result, String tab)
	{
		m_sBlock = sBlock;
		m_sLogTab += tab;
		m_bValidityCheck = !bValidBlock;
		if(htVariableList!=null) m_htVariables = htVariableList;
		m_dReplaceDenominator0 = dReplaceDenominator0;
		m_dReplaceDivideBy0Result = dReplaceDivisionBy0Result;
	}
	/**
	 * Sets or replaces a variable. System variables are prefixed with a "_fc_" so users variables cannot start with "_fc_"
	 * such as "_fc_pi" is a system variable for PI (3.1415....). Also variable names do not include "{" or "}". 
	 * @param sVariable : Case insensitive
	 * @param sValue
	 * @return
	 * @throws InvalidVariableException 
	 */
	public void setVariable(String sVariable, String sValue) throws InvalidVariableException
	{
		if(!isValidVariableName(sVariable) || sVariable.startsWith(SYSTEM_VAR_PREFIX)) 
			throw new InvalidVariableException("INVALID NAME:["+sVariable+"]");
		if(sValue==null)
			throw new InvalidVariableException("NULL VALUE: Name:["+sVariable+"] Value:["+sValue+"]");
		sVariable = sVariable.toLowerCase();
		if(m_htVariables.containsKey(sVariable)) m_htVariables.remove(sVariable); 
		m_htVariables.put(sVariable, sValue);
		m_bFormulaEvaluated = false;
	}
	/**
	 * Checks if the Variable name is Valid. Variable name must not include {} or empty spaces.
	 * @param sVariable
	 * @return
	 */
	private boolean isValidVariableName(String sVariable)
	{
		if(sVariable==null || sVariable.length()==0) return false;
		String sInvalidStuffList[] = {PLUS, MINUS, TIMES, DIVIDE_BY, BRACKET_START, BRACKET_END, VARIABLE_START, VARIABLE_END, " "};
		if(StringUtil.isSubstringPresentIgnoreCase(sVariable, sInvalidStuffList)>=0) return false;
		return true;
	}
	/**
	 * Sets a variable. If the variable exists replaces the variable.
	 * @param sVariable
	 * @param sValue
	 * @return false if sVariable is illegal or dValue is NaN.
	 * @throws InvalidVariableException 
	 */
	public void setVariable(String sVariable, double dValue) throws InvalidVariableException
	{
		if(!isValidVariableName(sVariable) || sVariable.startsWith(SYSTEM_VAR_PREFIX)) 
			throw new InvalidVariableException("INVALID NAME:["+sVariable+"]");
		sVariable = sVariable.toLowerCase();
		if(m_htVariables.containsKey(sVariable)) m_htVariables.remove(sVariable); 
		m_htVariables.put(sVariable, new Double(dValue));
		m_bFormulaEvaluated = false;
	}
	/**
	 * Gets the value of the variable. Can be a Double or a String
	 * @param sVariable
	 * @return Object
	 */
	public Object getVariableValue(String sVariable)
	{
		if(sVariable!=null && sVariable.length()>0)
		{
			sVariable = sVariable.toLowerCase();
			if(m_htVariables.containsKey(sVariable))
				return m_htVariables.get(sVariable);
		}
		return null;
	}
	/**
	 * Returns the result in a nice format. If parsing failed returns log.
	 * @return
	 */
	public String toString()
	{
		if(evaluate())
		{
			StringBuilder sb = new StringBuilder(64);
			Enumeration en = m_htVariables.keys();
			if(m_dReplaceDenominator0!=0) 
				sb.append("Any division by zero is replaced by division by ["+m_dReplaceDenominator0+"]\r\n");
			else
				sb.append("Any division by zero result is replaced by ["+m_dReplaceDivideBy0Result+"]\r\n");
			sb.append("\n\rVariables currently present:\r\n");
			while(en.hasMoreElements())
			{
				String sVariable = (String) en.nextElement();
				Object obj = m_htVariables.get(sVariable);
				if(obj instanceof Double) sb.append(sVariable+"\t= "+((Double)obj).toString()+"\r\n");
				if(obj instanceof String) sb.append(sVariable+"\t= "+(String)obj+"\r\n");
			}
			sb.append("RESULT: ["+m_sBlock+"] = ["+getResult()+"]\r\n");
			return sb.toString();
		}
		return "EVALUATION FAILED!! \n Formula Passed ["+m_sBlock+"] \r\n Last Error: ["+getLastError()+"]\r\n Full Error Log: \r\n"+getLog();
	}
	/**
	 * Log may be necessary to see where the parsing or calculation was up to before (if) there was an error.
	 * @return
	 */
	private String getLog(){return m_sLogBook.toString();}
	/**
	 * Returns the original string passed in.
	 * @return
	 */
	public String getCurrentFormula(){return m_sBlock;}	
	/**
	 * Checks if all the variables are set. And if there is any recursion.
	 * @return false if a variable is not defined
	 */
	private boolean checkVariableValidity(String sBlock, ArrayList arr) 
	{
		ArrayList arrVariable = getAllVariables(sBlock);
		if(arr==null) arr = new ArrayList(arrVariable.size());
		for(int i=0; i<arrVariable.size(); i++)
		{
			String sVariable = (String)arrVariable.get(i);
			Object obj = getVariableValue(sVariable);
			if(obj==null)
			{
				log("Variable ["+sVariable+"] is not set. Valid calculation is impossible.");
				return false;
			}
			else
			{
				if(obj instanceof String)
				{
					if(arr.contains(sVariable)) 
					{
						log("Recursion found in variable ["+sVariable+"]. Impossible to calculate.");
						return false;
					}
					arr.add(sVariable); //adding to the recursion checker
					if(!checkVariableValidity((String) obj, arr)) return false;  //checking for recursion
					arr.remove(sVariable);  //removing from the recursion checker
				} 
			}
		}
		return true;
	}
	/**
	 * Gets the unique list of variables from a string.
	 * @param sBlock
	 * @return
	 */
	private ArrayList getAllVariables(String sBlock) 
	{
		ArrayList arr = new ArrayList();
		String sVariable = getNextVariable(sBlock);
		while(sVariable!=null)
		{
			if(!arr.contains(sVariable)) arr.add(sVariable);
			sBlock = StringUtil.replaceAll(sBlock, "{"+sVariable+"}", "");
			sVariable = getNextVariable(sBlock);
		}
		return arr;
	}
	/**
	 * Returns a left most variable name or a null when no variable present.
	 * @return
	 */
	private String getNextVariable(String sBlock)
	{
		int iStartIndex = sBlock.indexOf(VARIABLE_START);
		if(iStartIndex>=0)
		{
			int iEndIndex = sBlock.indexOf(VARIABLE_END, iStartIndex);
			if(iEndIndex>iStartIndex) return sBlock.substring(iStartIndex+1, iEndIndex);
		}
		return null;
	}
	/**
	 * returns the result of the evaluation. NOTE: if evaluate() fails returns a NaN.
	 * @return
	 */
	public double getResult()
	{
		if(evaluate() && m_dResult!=null) return m_dResult.doubleValue();
		return Double.NaN;
	}
	/**
	 * Checks if the passed String is a variable. Must include the {}.
	 * @param sBlock
	 * @return
	 */
	private boolean isVariable(String sBlock) 
	{
		if(sBlock!=null)
		{
			sBlock = sBlock.trim();
			if(sBlock.startsWith(VARIABLE_START) && sBlock.endsWith(VARIABLE_END)) return true;
		}
		return false;
	}

	/**
	 * Returns any of the all supported operators if the Block has that operator in the Position.
	 * @param iStartPositionOfOperator
	 * @param sBlock
	 * @return
	 */
	private String getOperator(int iStartPositionOfOperator, String sBlock) 
	{
		if(sBlock!=null && sBlock.length()>=0)
			for(int i=0; i<m_SupportedOperators.length; i++)
				if(sBlock.startsWith(m_SupportedOperators[i], iStartPositionOfOperator)) return m_SupportedOperators[i];

		return "";
	}
	/**
	 * If a string has two or ends with decimal in a single number then returns true.
	 * @return
	 */
	private boolean foundIllegalDots(String sBlock) 
	{
		int iPos = sBlock.indexOf(".");
		while(iPos>=0)
		{
			if(isInsideVariable(iPos, sBlock))
			{
				iPos = sBlock.indexOf(".", iPos+1);
				continue;
			}
			int iOperatorPos = getOperatorPos(sBlock, iPos+1);
			int iVariableStartPos = sBlock.indexOf(VARIABLE_START, iPos+1);
			if((iOperatorPos>0 && iOperatorPos==(iPos+1)) || (iVariableStartPos>0 && iVariableStartPos==(iPos+1)))
			{
				log("Found illegal decimal point at index["+iPos+"] of ["+sBlock+"]");
				return true;
			}
			int iNextDotPos = sBlock.indexOf(".", iPos+1);
			if(iNextDotPos>=0 && (iOperatorPos<0 || iOperatorPos>iNextDotPos))
			{
				log("Found second decimal point in he same number at index["+iNextDotPos+"] of ["+sBlock+"]");
				return true;
			}
			iPos = iNextDotPos;
		}
		return false;
	}

	/**
	 * Finds illegal leading or trailing characters
	 * @return
	 */
	private boolean foundIllegalPrefixSuffix(String sBlock) 
	{
		if(sBlock.length()==0) return false;
		String sOriginal = sBlock;
		while(sBlock.length()>0)
		{
			if(sBlock.endsWith(BRACKET_END)) 
				sBlock = sBlock.substring(0, sBlock.length()-1);
			else
			{
				if(StringUtil.isStringSuffixedIgnoreCase(sBlock, m_SupportedOperators)>=0 || sBlock.endsWith(".")) 
				{
					log("INVALID FORMULA: Found an operator or a decimal at the end of the formula.");
					return true;
				}
				break;
			}	
		}
		sBlock = sOriginal;
		while(sBlock.length()>0)
		{
			if(StringUtil.isStringPrefixedIgnoreCase(sBlock, m_SupportedOperators)>=0) 
			{
				String sOperator = getOperator(0, sBlock);
				if(isValidFirstOperator(sOperator)) 
				{
					sBlock = sBlock.substring(sOperator.length());
					continue;
				}
				else
				{
					log("INVALID FORMULA: Found an illegal operator at the start of the formula.");
					return true;
				}
			}
			break;
		}
		return false;
	}

	/**
	 * gets the left most operator.
	 * @param sBlock
	 * @param iStartPos
	 * @return
	 */
	private int getOperatorPos(String sBlock, int iStartPos)
	{
		return getOperatorPos(sBlock, iStartPos, m_SupportedOperators);
	}

	/**
	 * gets the left most operator in the list.
	 * @param sBlock
	 * @param iStartPos
	 * @return
	 */
	private int getOperatorPos(String sBlock, int iStartPos, String[] sOpertorList)
	{
		if(sBlock.length()==0 || sOpertorList==null) return -1;
		int iMinPos = sBlock.length();
		for(int i=0; i<sOpertorList.length; i++)
		{
			int iPos = sBlock.toLowerCase().indexOf(sOpertorList[i].toLowerCase(), iStartPos);
			if(iPos>=0 && iPos<iMinPos && !isInsideVariable(iPos, sBlock)) iMinPos = iPos;
		}
		if(iMinPos==sBlock.length()) return -1;
		return iMinPos;
	}	
	/**
	 * Checks whether the position is inside a variable or not. Useful for ignoring operators inside a variable. 
	 * @param iPos
	 * @param sBlock
	 * @return
	 */
	private boolean isInsideVariable(int iPos, String sBlock){return StringUtil.isInsideNest(iPos, sBlock, VARIABLE_START, VARIABLE_END);}

	/**
	 * Checks for an equal number of bracket starts and ends and also checks for ")(".
	 * @return
	 */
	private boolean checkForBracketSets(String sStart, String sEnd, String sBlock) 
	{
		if(sBlock==null) return false;

		String sSource = sBlock.trim();

		int iBracketSet = 0;
		while(sSource.length()>0)
		{
			if(sSource.startsWith(sStart)) iBracketSet++;
			if(sSource.startsWith(sEnd)) iBracketSet--;
			if(iBracketSet<0) 
			{
				log("INVALID FORMULA: Bracket starts and ends do not match.");
				return false;
			}
			sSource = sSource.substring(1);
		}
		if(iBracketSet==0) return true;
		if(iBracketSet>0) log("INVALID FORMULA: Bracket starts and ends do not match.");
		return false;
	}
	/**
	 * A bracket ( or { must follow and } or ) must be followed by an operator unless they are the first or last character.
	 * @param sBlock
	 * @return
	 */
	private boolean foundNoOperatorsAroundBrackets(String sBlock) 
	{
		if(sBlock==null) return true;
		ArrayList arrInvalidCombo = new ArrayList(64);
		for(int i=0; i<10; i++)
		{
			arrInvalidCombo.add(i+BRACKET_START);
			arrInvalidCombo.add(i+VARIABLE_START);
			arrInvalidCombo.add(BRACKET_END+i);
			arrInvalidCombo.add(VARIABLE_END+i);
		}
		arrInvalidCombo.add("."+BRACKET_START);
		arrInvalidCombo.add("."+VARIABLE_START);
		arrInvalidCombo.add(BRACKET_END+".");
		arrInvalidCombo.add(VARIABLE_END+".");
		for(int i=0; i<m_sOper_Num.length; i++)
		{
			arrInvalidCombo.add(BRACKET_END+m_sOper_Num[i]);
			arrInvalidCombo.add(VARIABLE_END+m_sOper_Num[i]);
		}

		if(StringUtil.isSubstringPresentIgnoreCase(sBlock, arrInvalidCombo.toArray())>=0)
		{
			log("INVALID FORMULA: All bracket sets in the formula is must be surrounded by an operator.");
			return true;
		}

		return false;
	}
	/**
	 * Gets the position of the end bracket for the starting bracket.
	 * @param iStartBracketPos : Index of the start bracket (inclusive)
	 * @param sBlock
	 */
	private int getBracketEnd(int iStartBracketPos, String sBlock) 
	{
		sBlock = sBlock.substring(iStartBracketPos);
		int iBracketNumber = 0;
		int i = 0;
		while(sBlock.length()>0)
		{
			if(sBlock.startsWith(BRACKET_END))
			{
				iBracketNumber--;
				if(iBracketNumber==0) return i+iStartBracketPos;
			}
			if(sBlock.startsWith(BRACKET_START))
				iBracketNumber++;

			i++;
			sBlock = sBlock.substring(1);
		}
		return -1;
	}

	/**
	 * Replaces constants with variables.
	 * @param sBlock
	 * @return
	 */
	private String replaceConstants(String sBlock) 
	{
		int iConstantIndex = StringUtil.isSubstringPresentIgnoreCase(sBlock, m_SupportedConstants);
		while(iConstantIndex>=0)
		{
			String sConstant = m_SupportedConstants[iConstantIndex];
			sBlock = StringUtil.replaceAll(sBlock, sConstant, getConstantVariable(sConstant));
			iConstantIndex = StringUtil.isSubstringPresentIgnoreCase(sBlock, m_SupportedConstants);
		}
		return sBlock;
	}

	/**
	 * Returns a variable string and add the result to the system variables.
	 * @param dNumer
	 * @param bRadians: true if your number is radian
	 * @return
	 */
	public String SIN(double dNumer, boolean bRadians)
	{
		if(bRadians) return addDoubleToFormula(SINE, dNumer, 1, "sin_rad");
		return addDoubleToFormula(SINE, dNumer, 0, "sin_deg");
	}
	/**
	 * Returns a variable string and add the result to the system variables.
	 * @param dNumer
	 * @param bRadians: true if your number is radian
	 * @return
	 */
	public String COSINE(double dNumer, boolean bRadians)
	{
		if(bRadians) return addDoubleToFormula(COSINE, dNumer, 1, "cos_rad");
		return addDoubleToFormula(COSINE, dNumer, 0, "cos_deg");
	}
	/**
	 * Returns a variable string and add the result to the system variables.
	 * @param dNumer
	 * @param bRadians: true if your number is radian
	 * @return
	 */
	public String TANGENT(double dNumer, boolean bRadians)
	{
		if(bRadians) return addDoubleToFormula(TANGENT, dNumer, 1, "tan_rad");
		return addDoubleToFormula(TANGENT, dNumer, 0, "tan_deg");
	}
	/**
	 * Returns a variable string and add the result to the system variables.
	 * @param dNumer
	 * @param bRadians: true if your number is radian
	 * @return
	 */
	public String COSECANT(double dNumer, boolean bRadians)
	{
		if(bRadians) return addDoubleToFormula(COSECANT, dNumer, 1, "cosec_rad");
		return addDoubleToFormula(COSECANT, dNumer, 0, "cosec_deg");
	}
	/**
	 * Returns a variable string and add the result to the system variables.
	 * @param dNumer
	 * @param bRadians: true if your number is radian
	 * @return
	 */
	public String SECANT(double dNumer, boolean bRadians)
	{
		if(bRadians) return addDoubleToFormula(SECANT, dNumer, 1, "sec_rad");
		return addDoubleToFormula(SECANT, dNumer, 0, "sec_deg");
	}
	/**
	 * Returns a variable string and add the result to the system variables.
	 * @param dNumer
	 * @param bRadians: true if your number is radian
	 * @return
	 */
	public String COTANGENT(double dNumer, boolean bRadians)
	{
		if(bRadians) return addDoubleToFormula(COTANGENT, dNumer, 1, "cot_rad");
		return addDoubleToFormula(COTANGENT, dNumer, 0, "cot_deg");
	}
	/**
	 * Returns a variable string and add the result to the system variables.
	 * @param dNumer
	 * @return
	 */
	public String LOG_base_e(double dNumer){return addDoubleToFormula(LOG_base_e, dNumer, 0, "ln");}	
	/**
	 * Returns a variable string and add the result to the system variables.
	 * @param dNumer
	 * @return
	 */
	public String DEG_TO_RAD(double dNumer){return addDoubleToFormula(DEG_TO_RAD, dNumer, 0, "to_deg");}
	/**
	 * Returns a variable string and add the result to the system variables.
	 * @param dNumer
	 * @return
	 */
	public String RAD_TO_DEG(double dNumer){return addDoubleToFormula(RAD_TO_DEG, dNumer, 0, "to_deg");}
	/**
	 * Returns a variable string and add the result to the system variables.
	 * @param dNumer
	 * @return
	 */
	public String NUMBER_TO_POWER_e(double dNumer){return addDoubleToFormula(NUMBER_TO_POWER_e, dNumer, 0, "pow_e");}
	/**
	 * Returns a variable string and add the result to the system variables.
	 * @param dNumer
	 * @return
	 */
	public String SQUARE_ROOT(double dNumer){return addDoubleToFormula(SQUARE_ROOT, dNumer, 0, "sqrt");}
	/**
	 * Returns a variable string and add the result to the system variables.
	 * @param dNumer
	 * @param dPower
	 * @return
	 */
	public String POWER(double dNumer, double dPower){return addDoubleToFormula(POWER, dNumer, dPower, "pow");}

	/**
	 * Adds the value in the variable and gives it a unique name and returns the name. 
	 * @param dValue
	 * @return
	 */
	private String makeUniqueVariable(double dValue)
	{
		String sVar = getUniqueVariableName();
		m_htVariables.put(sVar, new Double(dValue));		
		return sVar; 
	}


	/**
	 * Gets a unique Variable name.
	 * @return
	 */
	private String getUniqueVariableName()
	{
		long lVar = (long) (1000000 * StrictMath.random());
		String sVar = SYSTEM_VAR_PREFIX+lVar;
		while(m_htVariables.containsKey(sVar)) sVar = SYSTEM_VAR_PREFIX + (lVar++);
		return sVar;
	}
	/**
	 * Writes the log
	 * @param s
	 */
	private void log(String s)
	{
		m_sLogBook.append(m_sLogTab+s+"\r\n");
		//comment out following after debug.
		//System.out.println(m_sLogTab+s);
	}

	/**
	 * Clears the log.
	 */
	public void clearLog(){m_sLogBook.delete(0, m_sLogBook.length());}

	/**
	 * Users can create their own functions that overrides the functions in the default package.
	 * @param sPackagePath
	 */
	public void setCustomExternalFunctionsPackage(String sPackagePath){m_sCustomExternalFunctionPackage = sPackagePath;}
	/**
	 * Gets the last error from the log. Useful to throw in the exception.
	 * @return
	 */
	public String getLastError() 
	{
		String sSeperator = "\r\n";
		int iPos = m_sLogBook.substring(0, (m_sLogBook.length()-sSeperator.length())).lastIndexOf("\r\n");
		if(iPos<0) return m_sLogBook.toString();
		return m_sLogBook.substring(iPos+sSeperator.length());
	}

	/***********************************************************************
	 *END of stuff which u usually don't need to change... 
	 ************************************************************************/

}//class
