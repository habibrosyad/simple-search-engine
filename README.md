# simple-search-engine
This is a command-line-based simple text search engine using vector space model with positional indexes which includes a naive pseudo relevance feedback feature build on top of Rocchio algorithm. Cosine similarity is used as a relevance measurement.

## Requirements
 - At least Java 8 (the codes hugely abuse the Java 8 Streams feature)
 - A terminal (this is a command-line-based application!)

## How to use
 - Compile the codes by executing `javac *.java`
 - Stopwords and sample collections are included in this repository, to begin indexing document collections run this command:
 ```
 java MySearchEngine index /path/to/collections /path/to/index-dir /path/to/stopwords.txt
 ```
 - The previous command will create an index file (`index.txt`) located in the `/path/to/index-dir/index.txt`
 - To perform searches on the newly created index execute the following command:
 ```
 java MySearchEngine search /path/to/index-dir [num_docs_to_retrive] [keyword_list_separated_by_space]
 ```
 - After executing the command, the system will return list of documents ordered based on the similarity score, the maximum documents returned would be the number specified on `[num_docs_to_retrive]` parameter
 - To try the naive pseudo relevance feedback feature, add addtional flag `-rf` to the previous command:
 ```
 java MySearchEngine search /path/to/index-dir -rf [num_docs_to_retrive] [keyword_list_separated_by_space]
 ```