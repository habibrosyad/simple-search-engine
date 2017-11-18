import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 *
 * @author M. Habib Rosyad
 */
public class Positions {
    private static final Pattern FORMAT = Pattern.compile("^\\d+:\\d+(?:;\\d+)*$");
    private final MutableInteger value;
    private final List<Integer> positions;
    
    public Positions() {
        value = new MutableInteger();
        positions = new ArrayList<>();
    }
    
    /**
     * Parse a line to Positions object.
     * 
     * @param line 
     */
    public Positions(String line) throws IOException {
        // Check format.
        Matcher m = FORMAT.matcher(line);
        if (m.matches()) {
            String[] split = line.split(":");
            value = new MutableInteger(Integer.parseInt(split[0]));
            positions = Arrays.asList(split[1].split(";")).stream()
                    .map(s -> Integer.parseInt(s))
                    .collect(Collectors.toList());
        } else {
            throw new IOException("Illegal position properties " + line);
        }
    }
    
    public Positions(int value, List<Integer> positions) {
        this.value = new MutableInteger(value);
        this.positions = positions;
    }
    
    public MutableInteger getValue() {
        return value;
    }
    
    public List<Integer> getPositions() {
        return positions;
    }
    
    /**
     * Produce value:pos1;pos2;...
     * @return 
     */
    @Override
    public String toString() {
        return value + ":" + positions.toString()
                .replaceAll("[\\[\\]]*", "")
                .replaceAll(", ",";");
    }
}
