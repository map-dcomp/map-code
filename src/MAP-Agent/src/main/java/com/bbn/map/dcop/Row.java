package com.bbn.map.dcop;

import java.io.Serializable;
import java.util.ArrayList;

/**
 * @author khoihd
 *
 */
public class Row implements Serializable {
    private static final long serialVersionUID = -7773374424812673056L;
    private int variableCount;
    private ArrayList<Double> valueList;
    private int randomCount;
    private ArrayList<Double> randomList;
    private double utility;
    
    /**
     * Constructors.
     */
    public Row() {
        valueList = new ArrayList<Double>();
        randomList = new ArrayList<Double>();
    }

    /**
     * @param newRow used for constructor
     */
    public Row(Row newRow) {
        this.variableCount = newRow.variableCount;
        this.utility = newRow.utility;
        //copy value list from newRow
        this.valueList = new ArrayList<Double>();
        for (Double value:newRow.getValueList()) {
            this.valueList.add(value);
        }
    }
    
    //input: X1, X2, X3,...,Xn
    //input utility
    /**
     * @param input input
     * @param utility utility
     */
    public Row(ArrayList<Double> input, double utility) {
        this.valueList = input;
        this.variableCount = input.size();
        this.utility = utility;
    }
    
    /**
     * @param decisionVariableList list
     * @param randVariableList list
     * @param utility utility
     */
    public Row(ArrayList<Double> decisionVariableList, ArrayList<Double> randVariableList, double utility) {
        this.valueList = decisionVariableList;
        this.randomList = randVariableList;
        this.variableCount = decisionVariableList.size();
        this.randomCount = randVariableList.size();
        this.utility = utility;
    }
    
    /**
     * @param decisionAndRandomList list
     * @param noDecision number of decision
     * @param utility utility
     */
    public Row(ArrayList<Double> decisionAndRandomList, int noDecision, double utility) {
        for (int i = 0; i < noDecision; i++) {
            this.valueList.add(decisionAndRandomList.get(i));
        }
        
        for (int i = noDecision; i<decisionAndRandomList.size(); i++) {
            this.randomList.add(decisionAndRandomList.get(i));
        }
        
        this.variableCount = valueList.size();
        this.randomCount = randomList.size();
        this.utility = utility;
    }

    /**
     * @param index index
     * @return value at a index of that row.
     */
    public Double getValueAtPosition(int index) {
        if (index >= variableCount) {
            System.err.println("Index out of bounds: " + index);
            System.err.println("Size:" +  variableCount);
        }
        return valueList.get(index);
    }

    /**
     *  print dec values and utility.
     */
    public void printDecVar() {
        for (Double value:valueList)
            System.out.print(value + " ");
        System.out.println("utility " + utility);
    }
    
    /**
     * @return row.
     */
    public String getDecVar() {
        StringBuffer rowStringBuffer = new StringBuffer();
        for (Double value:valueList)
            rowStringBuffer.append(Double.toString(value) + " ");
        rowStringBuffer.append("utility " + utility);
        
        return rowStringBuffer.toString();
    }
    
    /**
     *  print rand var.
     */
    public void printRandVar() {
        for (Double value:randomList)
            System.out.print(value + " ");
        System.out.println("utility " + utility);
    }
    
    /**
     * print dec and rand value list.
     */
    public void printBoth() {
        for (Double value:valueList)
            System.out.print(value + " ");
        System.out.print("y ");
        for (Double value:randomList)
            System.out.print(value + " ");
        System.out.println("utility " + utility);
    }
    
    /**
     * @param rowToCompare rowToCompare
     * @return true or false
     */
    public boolean equalDecisionVar(Object rowToCompare) {
        // If the object is compared with itself then return true  
        if (rowToCompare == this) {
            return true;
        }
 
        if (!(rowToCompare instanceof Row)) {
            return false;
        }
        
        // typecast o to Complex so that we can compare data members 
        Row castedTypeRow = (Row) rowToCompare;
        
        if (castedTypeRow.getVariableCount() != this.variableCount)
            System.err.println("Different number of variable count: " + this.variableCount + " " + 
                                    castedTypeRow.getRandomCount());
         
        // Compare the data members and return accordingly 
        return     castedTypeRow.randomCount == this.randomCount
            &&    castedTypeRow.randomList.equals(this.randomList);
    }
    
    @Override
    public int hashCode() {
        return valueList.hashCode();
    }
    
    @Override
    public boolean equals(Object rowToCompare) {
        // If the object is compared with itself then return true  
        if (rowToCompare == this) {
            return true;
        }
 
        if (!(rowToCompare instanceof Row)) {
            return false;
        }
         
        // typecast o to Complex so that we can compare data members 
        Row castedTypeRow = (Row) rowToCompare;
         
        // Compare the data members and return accordingly 
        return     castedTypeRow.variableCount == this.variableCount
            &&    castedTypeRow.valueList.equals(this.valueList)
            &&    castedTypeRow.utility == this.utility;
    }
    
    /**
     * @return variableCount
     */
    public int getVariableCount() {
        return variableCount;
    }

    /**
     * @return valueList
     */
    public ArrayList<Double> getValueList() {
        return valueList;
    }

    /**
     * @return randomCount
     */
    public int getRandomCount() {
        return randomCount;
    }

    /**
     * @return list
     */
    public ArrayList<Double> getRandomList() {
        return randomList;
    }

    /**
     * @return utility
     */
    public double getUtility() {
        return utility;
    }
    
    /**
     * @param utility set
     */
    public void setUtility(double utility) {
        this.utility = utility;
    }
    
}
