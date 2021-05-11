package com.bbn.map.dcop;

import java.io.Serializable;
import java.util.Objects;

import com.bbn.protelis.networkresourcemanagement.RegionIdentifier;

/**
 * @author khoihd
 *
 */
public class AugmentedRoot implements Serializable {
    /**
     * 
     */
    private static final long serialVersionUID = -9061455779486708255L;

    private final RegionIdentifier root;
    
    private final int treeNumber;
    
    private static final AugmentedRoot EMPTY_TREE = new AugmentedRoot();
    
    /**
     * @return an empty tree
     */
    public static AugmentedRoot getEmptyTree() {
        return EMPTY_TREE;
    }
    
    /**
     * Default constructor.
     */
    private AugmentedRoot() {
        root = null;
        treeNumber = 0;
    }
    
    /**
     * @param root .
     */
    public AugmentedRoot(RegionIdentifier root) {
        this.root = root;
        treeNumber = 0;
    }

    /**
     * @param root
     *          the region who is root of the tree
     * @param treeNumber
     *          numbers the ordering of the tree created by this root region
     */         
    public AugmentedRoot(RegionIdentifier root, int treeNumber) {
        this.root = root;
        this.treeNumber = treeNumber;
    }
    
    /**
     * Copy constructor.
     * @param augmentedRoot .
     */
    public AugmentedRoot(AugmentedRoot augmentedRoot) {
        this.root = augmentedRoot.getRoot();
        this.treeNumber = augmentedRoot.getTreeNumber();

    }


    /**
     * @return true if the root is empty. The region doesn't belong to any tree at this time
     */
    public boolean isEmptyTree() {
        return this.equals(EMPTY_TREE);
    }
    
    /**
     * @param regionID .
     * @return true if this augmentedRoot has this regionID (no matter what the tree number is)
     */
    public boolean hasRegionID(RegionIdentifier regionID) {
        return root.equals(regionID);
    }

    /**
     * @return the root
     */
    public RegionIdentifier getRoot() {
        return root;
    }
    
    /**
     * @return the treeNumber
     */
    public int getTreeNumber() {
        return treeNumber;
    }

    @Override
    public int hashCode() {
        return Objects.hash(root, treeNumber);
    }


    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        } else if (null == obj || getClass() != obj.getClass()) {
            return false;
        } else {
            final AugmentedRoot other = (AugmentedRoot) obj;
            return Objects.equals(getRoot(), other.getRoot()) //
                && getTreeNumber() == other.getTreeNumber() //
            ;
        }
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("AugmentedRoot [root=");
        builder.append(root);
        builder.append(", treeNumber=");
        builder.append(treeNumber);
        builder.append("]");
        return builder.toString();
    }
}
