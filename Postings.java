import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Postings is part of the record in the index which hold the tf value for each document.
 * In addition postings also store the idf data.
 * 
 * @author M. Habib Rosyad
 */
public class Postings {
    private static final Pattern FORMAT = Pattern.compile("^[^,]+,(?:[^,]+,\\d+:[;0-9]*,)+(?:\\-?\\d+\\.)?\\d+$");
    private final Map<String, Positions> tfs;
    private double idf;
    
    /**
     * Initialize a blank postings.
     */
    public Postings() {
        tfs = new HashMap<>();
        idf = 0;
    }
    
    /**
     * Create a new postings based on a given line of text.
     * Format of the line should be term,{docId,tf:{pos1;pos2;...}},idf
     * 
     * @param line the text which hold the postings information
     * @throws java.io.IOException 
     */
    public Postings(String line) throws IOException {
        Matcher m = FORMAT.matcher(line);
        if (m.matches()) {
            tfs = new HashMap<>();
            // Get tfs
            String[] split = line.split(",");
            for (int i = 1; i < split.length - 1; i += 2) {
                tfs.put(split[i], new Positions(split[i+1]));
            }
            // Get idf
            idf = Double.parseDouble(split[split.length - 1]);
        } else {
            throw new IOException("Illegal term properties " + line);
        }
    }
    
    /**
     * Increase tf value inside postings based on the document id.
     * 
     * @param documentId the document id
     * @param numberOfDocuments number of documents in the collection
     * @param position
     */
    public void addTf(String documentId, long numberOfDocuments, int position) {
        Positions positions = tfs.get(documentId);
        if (positions == null) {
            positions = new Positions();
            tfs.put(documentId, positions);
        }
        positions.getValue().increment();
        positions.getPositions().add(position);
        // Recalculate idf.
        // Increment denominator by 1 to allow for query terms that do not appear in the index.
        idf = round(Math.log((double)numberOfDocuments/(tfs.size()+1)),3);
    }
    
    /**
     * Get idf value of the postings.
     * 
     * @return the idf value
     */
    public double getIdf() {
        return idf;
    }
    
    /**
     * Get tf value of a document inside postings.
     * 
     * @param documentId the document id
     * @return the tf value
     */
    public int getTf(String documentId) {
        Positions positions = tfs.get(documentId);
        if (positions == null) 
            return 0;
        return positions.getValue().getValue();
    }
    
    /**
     * Get positional information of the term inside a document.
     * 
     * @param documentId
     * @return 
     */
    public List<Integer> getPositions(String documentId) {
        Positions positions = tfs.get(documentId);
        if (positions == null) 
            return null;
        return positions.getPositions();
    }
    
    /**
     * Get all tfs.
     * The key is the document id, and the content is the tf value.
     * 
     * @return a map represents tf collections
     */
    public Map<String, Positions> getTfs() {
        return tfs;
    }
    
    /**
     * Round up a value to n precision.
     * 
     * @param value
     * @param precision
     * @return 
     */
    private static double round(double value, int precision) {
        int scale = (int) Math.pow(10, precision);
        return (double) Math.round(value * scale) / scale;
    }
    
    /**
     * String representation of the postings.
     * Useful for saving postings to the index file.
     * 
     * @return string representation of the postings
     */
    @Override
    public String toString() {
        return tfs.entrySet().stream()
                .map((entry) -> entry.getKey() + "," + entry.getValue())
                .collect(Collectors.joining(",")) + "," +
                idf;
    }
    
    
    
    /**
     * Combine several postings together recursively.
     * 
     * @param numberOfDocuments
     * @param args
     * @return 
     */
    public static Postings merge(long numberOfDocuments, Postings... args) {
        if (args.length == 1)
            return args[0];
        else {
            // Get the 2 last args to combine.
            Postings[] toMerge = Arrays.copyOfRange(args, args.length-2, args.length);
            Map<String, Positions> tfs1 = toMerge[0].getTfs();
            Map<String, Positions> tfs2 = toMerge[1].getTfs();
            Postings newPostings = new Postings();
            // Find similar doc id between 2 postings.
            Set<String> similarDocId = tfs1.keySet();
            similarDocId.retainAll(tfs2.keySet());
            // Loop through similar doc id.
            for (String docId : similarDocId) {
                // Positions.
                List<Integer> positions1 = tfs1.get(docId).getPositions();
                List<Integer> positions2 = tfs2.get(docId).getPositions();
                
                int i = 0;
                int j = 0;
                int lastPosition = 0;
                for (int position1 : positions1) {
                    for (int position2: positions2) {
                        if (position2 > position1 && position2 > lastPosition) {
                            // Include position1 in the new postings.
                            newPostings.addTf(docId, numberOfDocuments, position1);
                            lastPosition = position2;
                            break;
                        }
                    }
                    i++;
                }
            }
            if (args.length > 2) {
                Postings[] restToMerge = Arrays.copyOfRange(args, 0, args.length-1);
                // Add newPostings to restToMerge in the last position.
                restToMerge[restToMerge.length-1] = newPostings;
                return merge(numberOfDocuments, restToMerge);
            } else {
                return merge(numberOfDocuments, newPostings);
            }
        }
    }
}