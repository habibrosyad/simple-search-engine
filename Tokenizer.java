import java.io.FileReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Scanner;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tokenization implementation.
 * 
 * - Any words hyphenated across a line break must be joined into a single token 
 *   (with the final token not containing the hyphen).
 * - Email addresses, web URLs and IP addresses must be preserved as a single token.
 * - Text within single quotation marks or inverted commas (e.g. ‘Word Press’)
 *   should be placed in single token.
 * - Two or more words separated by whitespace, all of which begin with a capital
 *   letter, must be preserved as a single token (i.e. include the whitespace in 
 *   the token).
 * - Acronym should be preserved as a single token with or without full stop or 
 *   period (e.g. C.A.T can result in CAT or C.A.T)
 * - For all other text, split the text into tokens using as delimiters either
 *   whitespace of elements of the following subset of punctuation: {.,:;”’()?!}
 * 
 * @author M. Habib Rosyad
 */
public class Tokenizer implements Iterator<String> {
    // Collection of special rules for retrieving tokens.
    private static final Pattern[] RULES = {
        // Email
        Pattern.compile("[-A-Za-z0-9_.]+@[A-Za-z][A-Za-z0-9_]+\\.[a-z]+"), 
        // Acronym
        Pattern.compile("[A-Z](?:\\.?[A-Z])+"),
        // Web address
        Pattern.compile("(?:https?|ftp|file)://[-a-zA-Z0-9+&@#/%?=~_|!:,.;]*[-a-zA-Z0-9+&@#/%=~_|]"),
        // IP address
        Pattern.compile("(?:\\d{1,3})(?:\\.\\d{1,3}){3}")
    };
    
    private final Scanner source;
    private final List<String> buffer;
    private final List<Predicate<String>> filters;
    private Predicate<String> combinedFilter;
    
    /**
     * Initialize tokenizer with a source.
     * 
     * @param source either a String of a FileReader object. 
     */
    public Tokenizer(Object source) {
        // Check type of the source.
        if (source instanceof String)
            this.source = new Scanner((String) source);
        else if (source instanceof FileReader)
            this.source = new Scanner((FileReader) source);
        else
            throw new UnsupportedOperationException("Unknown source");
        // Token buffer.
        this.buffer = new ArrayList<>();
        //  Initialize with a default filter.
        this.filters = new ArrayList<>(Arrays.asList(
            t -> !t.isEmpty() // A token must not be an empty String.
        ));
        combineFilter();
    }
    
    /**
     * Add filter for tokenizing.
     * Instead of doing filter/normalization later, it can be done here
     * during the tokenization process, this could save computation processes.
     * 
     * @param filter
     * @return Tokenizer object for chaining addFilter
     */
    public Tokenizer addFilter(Predicate<String> filter) {
        if (filters.add(filter))
            combineFilter();
        return this;
    }
    
    /**
     * Combine all filter in the filter list.
     */
    private void combineFilter() {
        combinedFilter = filters.stream().reduce(f -> true, Predicate::and);
    }
    
    /**
     * Get tokens from the source text.
     * Lazy load the tokens as needed.
     */
    private void getTokens() {
        StringBuilder line = new StringBuilder();
        while (source.hasNextLine()) {
            line.append(source.nextLine().trim().replaceAll("[^\\x20-\\x7e]", ""));
            
            if (line.length() < 1) {
                continue;
            }
            // If it is ended with '-' get the next line.
            if (line.charAt(line.length() - 1) == '-') {
                line.setLength(line.length() - 1);
                continue;
            }
            
            try ( 
                // Scan through the line
                Scanner parser = new Scanner(line.toString())) {
                StringBuilder tokenBuilder = new StringBuilder();
                
                while (parser.hasNext()) {
                    String baseToken = parser.next();
                    String trimmedToken = baseToken.replaceAll("^[^A-Za-z0-9]+|[^-A-Za-z0-9]+$", "").trim();
                    // Loop for other rules first.
                    boolean hasRuleMatch = false;
                    for (Pattern rule : RULES) {
                        Matcher m = rule.matcher(trimmedToken);
                        if (m.matches()) {
                            buffer.add(trimmedToken);
                            hasRuleMatch = true;
                            break;
                        }
                    }
                    if (hasRuleMatch)
                        continue;
                    // Check whether the token begin with quotation mark
                    // and check whether the token begin with capital.
                    tokenBuilder.append(baseToken);
                    // if ((tokenBuilder.charAt(0) == '\'' && tokenBuilder.charAt(tokenBuilder.length() - 1) != '\'') ||
                    //    (Character.isUpperCase(tokenBuilder.charAt(0)) && parser.hasNext("[A-Z][A-Za-z]+[^A-Za-z]?"))) {
                    //    tokenBuilder.append(" ");
                    //    continue;
                    //}
                    // Disable for now to support phrasal query
                    
                    // Split by ~ ` ! @ # $ % ^ & * ( ) _ - + = { [ } ] | \ : ; " ' < , > . ? /
                    Arrays.asList(tokenBuilder.toString().split("[^\\sA-Za-z0-9]+")).stream()
                        .map(String::trim).filter(combinedFilter).forEach((t) -> {
                        buffer.add(t);
                    });
                    tokenBuilder.setLength(0);
                }
            } catch (Exception e) {
                System.out.println("Unable to tokenize text " + e.getMessage());
            }
            
            if (!buffer.isEmpty()) 
                break;
        }
    }
    
    /**
     * Check whether there is a next token to extract.
     * 
     * @return true if there is a next token, false otherwise 
     */
    @Override
    public boolean hasNext() {
        if (buffer.isEmpty())
            getTokens();
        return !buffer.isEmpty();
    }
    
    /**
     * Get the next token.
     * @return the next token
     */
    @Override
    public String next() {
        if (buffer.isEmpty())
            getTokens();
        String token = buffer.get(0);
        buffer.remove(token);
        return token;
    }
    
    @Override
    public void remove() {
        throw new UnsupportedOperationException();
    }
}
