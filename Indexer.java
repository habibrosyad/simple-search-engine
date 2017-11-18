import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Implementation for indexing and searching.
 * Index a collection of documents for searching.
 * 
 * @author M. Habib Rosyad
 */
public class Indexer {
    private final static String INDEX_FILE = "index.txt";
    private final static String STOPWORDS_FILE = "stopwords.txt";
    private final static int PSEUDO_RF_ITER = 2;
    private final static int PSEUDO_RF_K = 5;
    private final static double PSEUDO_RF_ALPHA = 1.;
    private final static double PSEUDO_RF_BETA = .7;
    private final static double PSEUDO_RF_GAMA = 0.25;
    
    private final Map<String,MutableDouble> documentVectorLength;
    private Map<String,Map<String,Double>> documentVectors = new HashMap<>();
    private final Map<String,Postings> index; // Format <term>,<postings>,<idf>
    private final Path indexPath;
    private Set<String> stopwords;
    private Path stopwordsPath;
    private final PorterStemmer stemmer;
    
    /**
     * Initialize indexer for indexing and searching through the collection.
     * 
     * @param dir index directory
     * @throws java.io.IOException 
     */
    public Indexer(String dir) throws IOException {
        indexPath = Paths.get(dir, INDEX_FILE); // Get path/index.txt.
        stemmer = new PorterStemmer();
        index = new HashMap<>();
        documentVectorLength = new HashMap<>();
        documentVectors = new HashMap<>();
        
        // Create index path dirs incase it doesn't exists.
        if (!Files.isDirectory(indexPath.getParent()))
            Files.createDirectory(indexPath.getParent());
        // Try to load default stopwords (in the index directory),
        // do not throw exception in this part because stopwords can be loaded later.
        // Default stopwords list obtained from http://www.jmlr.org/papers/volume5/lewis04a/lewis04a.pdf
        // * paper which used words found on the stop word list from the SMART system.
        try {
            loadStopwords(Paths.get(dir, STOPWORDS_FILE).toString());
        } catch (IOException e) {
            System.out.println("No default stopwords source available");
        }
    }
    
    /**
     * Get normalized tokens.
     * Set default filters used to tokenize text.
     * 
     * @param source source text or file
     * @return a tokenizer
     */
    private Tokenizer getNormalizedTokens(Object source)  {
        return new Tokenizer(source)
            .addFilter(t -> t.length() > 1)
            .addFilter(t -> !stopwords.contains(t.toLowerCase())) // Remove stopwords
            .addFilter(t -> !isNumeric(t)); // Remove numerics
    }
    
    /**
     * Check whether a String is numeric or not.
     * Also check combination of numeric tokens e.g. 10-10 (using '-')
     * 
     * @param value String to check
     * @return true if String consists only of numerics combination, false otherwise
     */
    private boolean isNumeric(String value) {
        return value.matches("(?:-?\\d+(?:\\.\\d+)?)+");  //match a number with optional '-' and decimal.
    }
    
    /**
     * Load stopwords for tokenization.
     * 
     * @param path stopwords file path
     * @throws IOException 
     */
    public final void loadStopwords(String path) throws IOException {
        try (Scanner parser = new Scanner(new FileReader(path))) {
            this.stopwords = new HashSet<>((Arrays.asList(
               parser.useDelimiter("\\Z").next().split("[\\r\\n]+")
            )));
        }
        // Save the path for copying stopwords later in the index directory.
        stopwordsPath = Paths.get(path);
    }
    
    /**
     * Parse the index file.
     * Load index into memory, used for searching.
     * 
     * @throws Exception 
     */
    public void read() throws Exception {
        Scanner parser = new Scanner(new FileReader(indexPath.toFile()));
        // Also calculate each document vector length.
        while (parser.hasNextLine()) {
            String record = parser.nextLine();
            Postings postings = new Postings(record);
            String token = record.substring(0, record.indexOf(','));
            double idf = postings.getIdf();
            index.put(token, postings);
            // Update document set.
            postings.getTfs().entrySet().stream().forEach(positions -> {
                String documentId = positions.getKey();
                double wf = (1 + Math.log(postings.getTf(documentId)));
                MutableDouble length = documentVectorLength.get(documentId);
                
                if (length == null) {
                    length = new MutableDouble();
                    documentVectorLength.put(documentId, length);
                }
                // Calculate power of 2 of wf-idf.
                length.add(Math.pow(wf*idf, 2));
                // Put into document vectors.
                Map<String,Double> documentVector = documentVectors.get(documentId);
                if (documentVector == null) {
                    documentVector = new HashMap<>();
                    documentVectors.put(documentId, documentVector);
                }
                documentVector.put(token, wf*idf);
            });
        }
        // Document vector length is the square root of the sum of the values.
        documentVectorLength.replaceAll((k,v) -> v.setValue(Math.sqrt(v.getValue())));
    }
    
    /**
     * Search terms inside the index.
     * Weight is calculated by sublinear-tf.
     * Support phrasal query
     * 
     * @param query String to search in the index 
     * @param topN n top number of documents to display in the search results
     * @return 
     */
    public List<String> search(String query, long topN, boolean useRF) {
        Map<String,MutableInteger> tfs = new HashMap<>();
        Map<String,Double> queryVector = new HashMap<>();
        Map<String,Double> ranks = new HashMap<>();
        Tokenizer tokens = getNormalizedTokens(query);
        List<String> phrase = new ArrayList<>();
        // If number of docs is set to 0, then get the value from documentVectorLength.
        double n = (double) documentVectorLength.size();
        // Count token frequency.
        while (tokens.hasNext()) {
            // Start convert to lowercase and stem.
            String token = stemmer.stem(tokens.next().toLowerCase());
            if (!isNumeric(token)) {
                MutableInteger tf = tfs.get(token);
                if (tf == null) {
                    tf = new MutableInteger();
                    tfs.put(token, tf);
                    phrase.add(token);
                }
                tf.increment();
            }
        }
        // Construct query vector.
        tfs.entrySet().stream().forEach(tf -> {
            String token = tf.getKey();
            double wf = 1 + Math.log(tf.getValue().getValue());
            Postings postings = index.get(token);
            if (postings != null) {
                queryVector.put(token, wf * postings.getIdf());
            } else {
                queryVector.put(token, wf * Math.log(n));
            }
        });
        // Add queryVector for phrase.
        if (phrase.size() > 1) {
            String joined = String.join(" ", phrase);
            Postings postings = getPhrasalPostings(joined, (int) n);
            if (postings != null) {
                queryVector.put(joined, postings.getIdf());
            }
        }
        // Execute pseudo RF as defined by PSEUDO_RF_ITER
        int i = 0;
        while (i < (useRF ? PSEUDO_RF_ITER : 1)) {
            // Calculate query vector length.
            double queryVectorLength = Math.sqrt(queryVector.values().stream()
                                            .reduce(.0, (a,b) -> a + b*b));
            // Construct document vector.
            documentVectorLength.entrySet().stream().forEach(l -> {
                String documentId = l.getKey();
                Map<String,Double> documentVector = new HashMap<>();
                MutableDouble dotProduct = new MutableDouble();
                queryVector.entrySet().stream().forEach(v -> {
                    String token = v.getKey();
                    double wq = v.getValue();
                    Postings postings;

                    if (isPhrasal(token)) {
                        postings = getPhrasalPostings(token, (int) n);
                    } else {
                        postings = index.get(token);
                    }

                    if (postings != null) {
                        int tf = postings.getTf(documentId);
                        if (tf != 0) {
                            double wf = (1 + Math.log(tf));
                            dotProduct.add(wf * postings.getIdf() * wq);
                            
                            
                        }
                    }
                });
                if (dotProduct.getValue() > 0.)
                    ranks.put(documentId, dotProduct.getValue()/(queryVectorLength*documentVectorLength.get(documentId).getValue()));
            });
            
            if (ranks.size() > 0)
                applyPseudoRF(queryVector, documentVectors, sort(ranks));
            else
                return null;
            i++;
        }
        
        if (ranks.size() > 0) {
            DecimalFormat format = new DecimalFormat("#.###");
            format.setRoundingMode(RoundingMode.CEILING);
            return ranks.entrySet().stream()
                    .filter(r -> r.getValue() > .0)
                    .sorted((a,b) -> b.getValue().compareTo(a.getValue()))
                    .limit(topN)
                    .map(r -> r.getKey() + "," + format.format(r.getValue()))
                    .collect(Collectors.toList());
        }
        
        return null;
    }
    
    /**
     * Check if a token is in a phrasal format.
     * 
     * @param value
     * @return 
     */
    private boolean isPhrasal(String token) {
        return token.matches("[^\\s]+(?:\\s[^\\s]+)+");
    }
    
    /**
     * Get intersection of postings.
     * 
     * @param token
     * @param n
     * @return 
     */
    private Postings getPhrasalPostings(String token, int n) {
        String[] split = token.split(" ");
        List<Postings> postings = Arrays.asList(split).stream()
                .map(t -> index.get(t))
                .filter(t -> t != null)
                .collect(Collectors.toList());
        // Merge
        if (postings.size() > 0)
            return Postings.merge(n, postings.toArray(new Postings[0]));
        return null;
    }
    
    /**
     * Get new query vector based on pseudo relevance feedback.
     * This method implement Rochio Algorithm.
     * Assume sorted ranks map.
     * 
     * @return 
     */
    private void applyPseudoRF(Map<String, Double> query, Map<String, Map<String,Double>> documents, Map<String,Double> ranks) {
        // Assume top PSEUDO_RF_K as relevant.
        final int k = ranks.size() >= PSEUDO_RF_K ? PSEUDO_RF_K : ranks.size();
        // n doc - k
        int ik = documents.size() - k;
        MutableInteger i = new MutableInteger();
        Map<String,MutableDouble> relevant = new HashMap<>();
        Map<String,MutableDouble> irrelevant = new HashMap<>();
        // Calc sigma relevant and irrelevant.
        ranks.entrySet().stream().forEach(r -> {
            Map<String,Double> document = documents.get(r.getKey());
            document.entrySet().stream().forEach(d -> {
                String token = d.getKey();
                if (i.getValue() < k) {
                    // Calc as relevant.
                    MutableDouble value = relevant.get(token);
                    if (value == null) {
                        value = new MutableDouble();
                        relevant.put(token, value);
                    }
                    value.add(d.getValue());
                } else {
                    // Calc as irrelevant.
                    MutableDouble value = irrelevant.get(token);
                    if (value == null) {
                        value = new MutableDouble();
                        irrelevant.put(token, value);
                    }
                    value.add(d.getValue());
                }
            });
            i.increment();
        });
        // Calc new query vector.
        relevant.replaceAll((key,value) -> value.setValue(value.getValue()*PSEUDO_RF_BETA/k));
        irrelevant.replaceAll((key,value) -> value.setValue(value.getValue()*PSEUDO_RF_GAMA/ik));
        // Loop for relevant.
        relevant.entrySet().stream().forEach(w -> {
            String token = w.getKey();
            Double docValue = w.getValue().getValue();
            Double queryValue = query.get(token);
            if (queryValue == null) {
                query.put(token, docValue);
            } else {
                query.put(token, PSEUDO_RF_ALPHA*queryValue + docValue);
            }
        });
        // Loop for irrelevant.
        irrelevant.entrySet().stream().forEach(w -> {
            String token = w.getKey();
            Double docValue = w.getValue().getValue();
            Double queryValue = query.get(token);
            if (queryValue == null) {
                query.put(token, -docValue);
            } else {
                query.put(token, PSEUDO_RF_ALPHA*queryValue - docValue);
            }
        });
    }
    
    /**
     * Update the index.
     * 
     * @param dir document collection directory
     * @throws IOException 
     */
    public void update(String dir) throws IOException {
        Path path = Paths.get(dir);
        // Check collection path existence and permission of source.
        if (!Files.isDirectory(path) ||
            !Files.isReadable(path)) 
            throw new IOException("Invalid collection path");
        // Count documents in the collection.
        long n;
        try (Stream<Path> collections = Files.walk(path, 1)) {
            n = collections.filter(p -> Files.isRegularFile(p)).count();
        }
        // Update index.
        try (Stream<Path> collections = Files.walk(path, 1)) {
            collections.filter(p -> Files.isRegularFile(p)).forEach(p -> {
                // Clean filename from ',' since it is used as delimiter in the index.
                String fileName = p.getFileName().toString().replaceAll(",", "");
                try {    
                    Tokenizer tokens = getNormalizedTokens(new FileReader(p.toFile()));
                    // Init token position.
                    int position = 0;
                    while (tokens.hasNext()) {
                        // Start normalisation, convert to lowercase.
                        String token = stemmer.stem(tokens.next().toLowerCase());
                        // Ensure the stemmed token is not a number.
                        if (!isNumeric(token)) {
                            Postings postings = index.get(token);

                            if (postings == null) {
                                postings = new Postings();
                                index.put(token, postings);
                            }

                            postings.addTf(fileName, n, position++);
                        }
                    }
                } catch (FileNotFoundException e) {
                    System.out.println("Unable to read document " + fileName);
                }
            });
        }
        // Save index, this can only be done after the idf values are fixed.
        try (BufferedWriter writer = Files.newBufferedWriter(indexPath)) {
            index.entrySet().stream().forEach(record -> {
                try {
                    writer.write(record.getKey() + "," + record.getValue().toString());
                    writer.newLine();
                } catch (IOException e) {
                    System.out.println("Unable to save the index " + e.getMessage());
                }
            });
        }
        // Copy stopwords file into index directory for searching later.
        Files.copy(stopwordsPath, Paths.get(indexPath.getParent().toString(), STOPWORDS_FILE), StandardCopyOption.REPLACE_EXISTING);
    }
    
    /**
     * Sort map descending.
     * https://www.mkyong.com/java/how-to-sort-a-map-in-java/
     * 
     * @param <K>
     * @param <V>
     * @param unsortMap
     * @return 
     */
    private static <K, V extends Comparable<? super V>> Map<K, V> sort(Map<K, V> unsortMap) {
        List<Map.Entry<K, V>> list =
                new LinkedList<>(unsortMap.entrySet());

        Collections.sort(list, (Map.Entry<K, V> o1, Map.Entry<K, V> o2) -> (o2.getValue()).compareTo(o1.getValue()));

        Map<K, V> result = new LinkedHashMap<>();
        list.forEach((entry) -> {
            result.put(entry.getKey(), entry.getValue());
        });

        return result;
    }
}
