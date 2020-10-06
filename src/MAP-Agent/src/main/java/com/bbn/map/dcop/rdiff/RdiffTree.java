package com.bbn.map.dcop.rdiff;

import java.io.Serializable;
import java.util.Objects;

import com.bbn.protelis.networkresourcemanagement.RegionIdentifier;

/**
 * @author khoihd
 *
 */
class RdiffTree implements Serializable {
    /**
     * 
     */
    private static final long serialVersionUID = -8204044542906924829L;

    private RegionIdentifier parent;
    
    private RegionIdentifier root;
        
    /**
     * Default constructor.
     */
    RdiffTree() {
    }
    
    
    /**
     * @param parent .
     */
    RdiffTree(RegionIdentifier parent) {
        this.parent = parent;
    }
    
    
    /** Copy constructor.
     * @param object .
     */
    RdiffTree(RdiffTree object) {
        parent = object.getParent();
        root = object.getRoot(); 
    }


    /**
     * @return the parent
     */
    RegionIdentifier getParent() {
        return parent;
    }

    
    /**
     * @return true if the parent is not null
     */
    boolean hasParent() {
        return null != parent;
    }
    
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        } else if (null == obj || getClass() != obj.getClass()) {
            return false;
        } else {
            final RdiffTree other = (RdiffTree) obj;
            return Objects.equals(getParent(), other.getParent()) //
                    && Objects.equals(getRoot(), other.getRoot())
            ;
        }
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(parent, root);
    }

    /**
     * @param parent the parent to set
     */
    void setParent(RegionIdentifier parent) {
        this.parent = parent;
    }


    /**
     * @return the root
     */
    public RegionIdentifier getRoot() {
        return root;
    }


    /**
     * @param root the root to set
     */
    public void setRoot(RegionIdentifier root) {
        this.root = root;
    }


    /* (non-Javadoc)
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("RdiffTree [parent=");
        builder.append(parent);
        builder.append(", root=");
        builder.append(root);
        builder.append("]");
        return builder.toString();
    }


    public boolean hasRoot() {
        return root != null;
    }
}
