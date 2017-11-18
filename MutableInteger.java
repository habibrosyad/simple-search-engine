/**
 * Wrapper class to enable the usage of a mutable 'Integer' when dealing with map.
 * 
 * @author M. Habib Rosyad
 */
public class MutableInteger {
    private int value;

    /**
     * Initialize with 0.
     */
    public MutableInteger() {
        value = 0;
    }
    
    /**
     * Initialize with a value.
     * 
     * @param value 
     */
    public MutableInteger(int value) {
        this.value = value;
    }
    
    /**
     * Get the value.
     * 
     * @return the value
     */
    public int getValue() {
        return value;
    }
    
    /**
     * Increase the current value by 1.
     */
    public void increment() {
        value++;
    }
    
    /**
     * Get String representation of the object.
     * Useful when saving data into the index file.
     * 
     * @return String representation of the object
     */
    @Override
    public String toString() {
        return "" + value;
    }
}
