package com.bbn.map.dcop;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;

/**
 * @author khoihd
 *
 */
public class Table implements Serializable {
    
    /**
     * 
     */
    private static final long serialVersionUID = -2675509097502238364L;
    static final int CONTAIN_RAND = 0;
    static final int NOT_CONTAIN_RAND = 1;
    private int rowCount;
    private int variableCount;
//    private int randomCount;
    private ArrayList<Row> table;
    private ArrayList<String> decVarLabel;
    private ArrayList<String> randVarLabel;
//    HashMap<Double, ArrayList<Row>> loadMap;
    private HashMap<Double, ArrayList<Row>> keyMap;

    /**
     * @param anotherTable table
     */
    public Table(Table anotherTable) {
        this.rowCount = anotherTable.rowCount;
        this.variableCount = anotherTable.variableCount;
        this.table = new ArrayList<Row>();
//        this.decVarLabel = (ArrayList<String>) anotherTable.decVarLabel.clone();
        this.decVarLabel = new ArrayList<String>();
        ArrayList<String> anotherTableDecVarLabel = anotherTable.getDecVarLabel();
        for (String label:anotherTableDecVarLabel) {
            this.decVarLabel.add(label);
        }

        //add row to new table
        for (Row row:anotherTable.getTable()) {
            this.table.add(new Row(row));
        }
    }
    
    boolean equalArray(ArrayList<?> list1, ArrayList<?> list2) {
        if (list1 == null || list2 == null)
            return false;
        if (list1.size() != list2.size())
            return false;
        for (int index=0; index<list1.size(); index++) {
            if (!list1.get(index).equals(list2.get(index)))
                return false;                
        }
        return true;
    }
    
    /**
     * @param decValueList decValueList
     * @return utility given row
     */
    public double getUtilityGivenDecValueList(ArrayList<Double> decValueList) {
        for (int index=0; index<table.size(); index++) {
            if (equalArray(table.get(index).getValueList(), decValueList))
                return table.get(index).getUtility();
        }
        return Integer.MIN_VALUE;
    }
    
    /**
     * @param decValueList decValueList
     * @param randValueList randValueList
     * @return utility given dec and rand value list
     */
    public double getUtilityGivenDecAndRandValueList(ArrayList<Double> decValueList, ArrayList<Double> randValueList) {
        for (int index=0; index<table.size(); index++) {
            if (equalArray(table.get(index).getValueList(), decValueList)
            && equalArray(table.get(index).getRandomList(), randValueList))
                return table.get(index).getUtility();
        }
        return Integer.MIN_VALUE;
    }
    
//    @Override
//    public boolean equals(Object tableToCompare) {
//        if (!(tableToCompare instanceof Table)) {
//            return false;
//        }
//        
//        // If the object is compared with itself then return true ?
//        if (tableToCompare == this) {
//            return true;
//        }
//         
//        // typecast o to Complex so that we can compare data members 
//        Table castedTypeTable = (Table) tableToCompare;
//         
//        // Compare the data members and return accordingly 
//        return  castedTypeTable.rowCount == this.rowCount    
//            &&    castedTypeTable.variableCount == this.variableCount
//            &&    castedTypeTable.table.equals(this.table)
//            &&    castedTypeTable.decVarLabel == this.decVarLabel;
//    }
    
    /**
     * @param newLabel constructor
     */
    public Table(ArrayList<String> newLabel) {
        table = new ArrayList<Row>();
        decVarLabel = new ArrayList<String>();
        for (String variable:newLabel)
            decVarLabel.add(variable);
        variableCount = decVarLabel.size();
        rowCount = 0;
        keyMap = new HashMap<Double, ArrayList<Row>>();
    }
    
//    public Table(ArrayList<String> newLabel, int randType) {
//        this(newLabel);
//        this.randType = randType;
//    }
    
    /**
     * @param decVarList decVarList
     * @param randVarList randValist
     */
    public Table(ArrayList<String> decVarList, ArrayList<String> randVarList) {
        table = new ArrayList<Row>();
        decVarLabel = decVarList;
        randVarLabel = randVarList;
        variableCount = decVarList.size();
//        randomCount = randVarList.size();
    }
    
//    public Table(ArrayList<String> decVarList, ArrayList<String> randVarList, int randType) {
//        this(decVarList, randVarList);
//    }
    
    /**
     * @param newRow newRow
     */
    public void addRow(Row newRow) {
        this.table.add(newRow);
        this.rowCount++;
    }
    
    /**
     * @param newRow newRow
     * @param key key
     */
    public void addRow(Row newRow, double key) {
        this.table.add(newRow);
        this.rowCount++;
        
        Set<Double> currentLoadSet = keyMap.keySet();
        if (currentLoadSet.contains(key)) {
            ArrayList<Row> rowList = keyMap.get(key);
            rowList.add(newRow);
            keyMap.put(key, rowList);
        }
        else {
            ArrayList<Row> rowList = new ArrayList<Row>();
            rowList.add(newRow);
            keyMap.put(key, rowList);
        }
    }
    
    /**
     * @param key key
     * @return rowlist from a key
     */
    public ArrayList<Row> getRowListFromKey(double key) {
        return keyMap.get(key);
    }
    
    //tao 1 array chua values cua 1 bien
    //chay tung dong cua vector
    //kiem tra gia tri cua bien, co trong listValues chua
    //neu chua thi them vao
    //tra ve danh sach tat ca gia tri cua 1 bien, khong bi duplicate
    ArrayList<Double> listValuesOfVariable(int index) {
        ArrayList<Double> listValues = new ArrayList<Double>();
        for (Row row: table) {            
            if (!listValues.contains(row.getValueAtPosition(index)))
                listValues.add(row.getValueAtPosition(index));
        }
        return listValues;
    }
    
//    public boolean containVariable(ArrayList<String> list, String input) {
//        if (list.size() == 0)
//            return false;
//        for (String temp: list) {
//            if (temp.equals(input))
//                return true;
//        }
//        return false;
//    }
    
    /**
     *  print dec variables.
     */
    public void printDecVar() {
        System.out.println("Dec lable list: ");
        for (String variable:decVarLabel)
            System.out.print(variable + " ");
        System.out.println();
        
        for (Row row:table) {
            row.printDecVar();
        }
    }
    
    /**
     *  print rand variables.
     */
    public void printRandVar() {
        System.out.println("Rand lable list: ");
        for (String variable:randVarLabel)
            System.out.print(variable + " ");
        System.out.println();
        
        for (Row row:table) {
            row.printRandVar();
        }
    }
    
    /**
     * @param filename write table to a file
     */
    public void writeDecVarToFile(String filename) {
        try {
            PrintWriter writer = new PrintWriter(filename, "UTF-8");
            for (Row row:table)
                writer.println(row.getDecVar());

            writer.close();
        } catch (IOException e) {
           // do something
        }
    }
    
    /**
     *  print dec and rand variables.
     */
    public void printDecAndRandVar() {
        System.out.println("Dec and Rand lable list: ");
        for (String variable:decVarLabel)
            System.out.print(variable + " ");
        System.out.print("y ");
        for (String variable:randVarLabel)
            System.out.print(variable + " ");
        
        System.out.println();
        for (Row row:table) {
            row.printBoth();
        }
    }

    /**
     * @return rowCount
     */
    public int getRowCount() {
        return rowCount;
    }
    
    /**
     * @return get variable count
     */
    public int getVariableCount() {
        return variableCount;
    }

    /**
     * @return table
     */
    public ArrayList<Row> getTable() {
        return table;
    }

    /**
     * @return decVarLabel
     */
    public ArrayList<String> getDecVarLabel() {
        return decVarLabel;
    }

    /**
     * @return randVarLabel
     */
    public ArrayList<String> getRandVarLabel() {
        return randVarLabel;
    }
    
    /**
     * @return keyMap
     */
    public HashMap<Double, ArrayList<Row>> getKeyMap() {
        return keyMap;
    }

    /**
     * @param keyMap keyMap
     */
    public void setLoadMap(HashMap<Double, ArrayList<Row>> keyMap) {
        this.keyMap = keyMap;
    }
}
