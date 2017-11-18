/**
 * Wrapper class to enable the usage of a mutable 'Double' when dealing with map.
 * 
 * @author M. Habib Rosyad
 */
public class MutableDouble {
    private double value;
    
    /**
     * Initialize with 0.
     */
    public MutableDouble() {
        value = 0;
    }
    
    /**
     * Initialize with a value.
     * 
     * @param value 
     */
    public MutableDouble(double value) {
        this.value = value;
    }
    
    /**
     * Add a value to the current value.
     * 
     * @param value add current value by this value
     */
    public void add(double value) {
        this.value += value;
    }
    
    /**
     * Get the value.
     * 
     * @return the value 
     */
    public double getValue() {
        return value;
    }
    
    /**
     * Set the value.
     * 
     * @param value set the current value into this
     * @return instance of this object to enable chaining.
     */
    public MutableDouble setValue(double value) {
        this.value = value;
        return this;
    }
}
