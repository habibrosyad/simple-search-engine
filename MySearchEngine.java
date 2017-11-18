
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Simple search engine implementation using vector space model.
 * Commands supported:
 *      index collection_dir index_dir stopwords.txt
 *      search index_dir num_docs keyword_list
 * @author M. Habib Rosyad
 */
public class MySearchEngine {
    private static final String INDEX_COMMAND = "index";
    private static final String SEARCH_COMMAND = "search";
    
    /**
     * Animated spinner while waiting for process to finish.
     * 
     * http://www.4pmp.com/2010/01/spinning-command-line-cursor-in-java/
     */
    private static class Spinner implements Runnable {
        @Override
        public void run() {
            String[] phases = {"|", "/", "-", "\\"};
            
            try {
                while (true){
                    for (String phase : phases){
                        System.out.printf("\b"+phase);
                        Thread.sleep(100);
                    }
                }
            }
            catch (InterruptedException e) {
                // No need to do anything if interrupted
            }
        }
    }
    
    private static String resolvePath(String path) {
        return path.replaceAll("^~", System.getProperty("user.home"));
    }
    
    
    public static void main(String[] args) throws Exception {
        // Create a new thread for spinning the cursor
        Thread spinner = new Thread(new Spinner());
        // Create a new thread for spinning the cursor
        // Read commands
        if (args.length >= 4) {
            // Add some space for readability.
            System.out.println();
            
            switch (args[0]) {
                case INDEX_COMMAND: 
                    if (args.length == 4) {
                        // args[1] is the collection dir.
                        // args[2] is the index dir.
                        // args[3] is the stopwords file path.
                        try {
                            Indexer indexer = new Indexer(resolvePath(args[2]));
                            indexer.loadStopwords(resolvePath(args[3]));
                            System.out.print("Indexing in progress...");
                            spinner.start();
                            indexer.update(resolvePath(args[1]));
                            spinner.interrupt();
                            spinner.join();
                            System.out.println("\nIndexing finished");
                        } catch(IOException e) {
                            spinner.interrupt();
                            spinner.join();
                            System.out.println("\nSomething wrong happened: " + e.getMessage());
                        }
                    } else {
                        System.out.println("Invalid number of arguments");
                    }
                    break;
                case SEARCH_COMMAND:
                    // args[1] is the index dir.
                    // args[2] might be flag for enabling pseudo rf or
                    // args[2] is the top n number of documents that should be returned.
                    // args[3...] is the list of keywords 
                    try {
                        System.out.print("Searching...");
                        spinner.start();
                        Indexer indexer = new Indexer(resolvePath(args[1]));
                        indexer.read();
                        List<String> results;
                        // Check if rf flag is enabled.
                        if (args[2].equals("-rf"))  
                            results = indexer.search(Arrays.stream(args).skip(4).collect(Collectors.joining(" ")), Integer.parseInt(args[3]), true);
                        else
                            results = indexer.search(Arrays.stream(args).skip(3).collect(Collectors.joining(" ")), Integer.parseInt(args[2]), false);
                        spinner.interrupt();
                        spinner.join();
                        System.out.println("\n");
                        if (results != null && results.size() > 0) {
                            IntStream.range(0, results.size()).forEach(i -> {
                                System.out.println(++i + ". " + results.get(--i));
                            });
                        } else {
                            System.out.println("Nothing found :(");
                        }
                    } catch(Exception e) {
                        spinner.interrupt();
                        spinner.join();
                        // e.printStackTrace();
                        System.out.println("\nSomething wrong happened: " + e.getMessage());
                    }
                    break;
                default: System.out.println("Unknown command");
            }
        } else {
            System.out.println("Insufficient number of arguments");
        }
    }
}
