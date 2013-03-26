wp-semantic-similarity
======================
A Java library for semantic similarity measures derived from Wikipedia. In particular:
* Given a Wikipedia article, it lists the most similar Wikipedia articles along with their similarity scores.
* Given a phrase, it lists the most similar Wikipedia articles along with their similarity scores.
* It calculates the semantic similarity between two Wikipedia articles.
* It calculates the semantic similarity between two phrases.

The semantic network between Wikipedia pages uses article text, article links, and article categories. A machine learner trained on four different human-labeled semantic similarity datasets combines metrics based on these features. For a good overview of state-of-the-art in semantic similarity, see [Explanatory Semantic Relatedness and Explicit Spatialization for Exploratory Search, Hecht et al.](http://brenthecht.com/papers/bhecht_sigir2012_ExpSpatialization_SRplusE.pdf).

For the phrase-based functions, the concept resolution framework maps a phrase to a Wikipedia article in one of two ways: 1) by searching 
Wikipedia articles for the phrase, or 2) by searching for the phrase in links to Wikipedia articles [crawled by Google](http://www-nlp.stanford.edu/pubs/crosswikis-data.tar.bz2/).

One of the goals of this project is to generate a precomputed semantic similarity network over all Wikipedia pages using state of the semantic similarity techniques.

This is still a work in progress.

Software and hardware recommendations
-----------
* Java 6 or higher (required)
* Maven (required)
* 16GB memory or more.
* About 200GB of free disk space.
* A reasonably fast machine (dual or quad core)

Instructions for building the semantic similarity network:
-----------
* Clone this project
* Download the wikipedia dumps from http://dumps.wikimedia.org/enwiki/ . You want the *-pages-articles*.bz2 files, but the version broken down into many (25 or so) different bz2 files.
* Create a configuration file modeled after the example in conf/example.configuration.js
* Create the lucene index. Both steps should take an hour or two:

```bash
  ./bin/index.sh conf/example-configuration.json jvm_MBs esa
  ./bin/index.sh conf/example-configuration.json jvm_MBs text cats links main
```

* Create the concept mapper index, which is used to map phrases to Wikipedia articles. Download dictionary.bz2 from http://www-nlp.stanford.edu/pubs/crosswikis-data.tar.bz2/dictionary.bz2, then run:

  `./bin/make-concept-index.sh path/to/dictionary.bz2 path/to/index/output-directory jvm_MBs`
  
* Train the underlying similarity metrics on the article similarity dataset:

```bash
  ./bin/train.sh 10000 -c conf/example-configuration.json -e 35 -g dat/gold/combined.articles.txt  -n inlinks -t dat/dictionary.pruned/ 
  ./bin/train.sh 10000 -c conf/example-configuration.json -e 35 -g dat/gold/combined.articles.txt  -n outlinks -t dat/dictionary.pruned/ 
  ./bin/train.sh 10000 -c conf/example-configuration.json -e 35 -g dat/gold/combined.articles.txt  -n article-text -t dat/dictionary.pruned/ 
  ./bin/train.sh 10000 -c conf/example-configuration.json -e 35 -g dat/gold/combined.articles.txt  -n esa -t dat/dictionary.pruned/ 
  ./bin/train.sh 10000 -c conf/example-configuration.json -e 35 -g dat/gold/combined.articles.txt  -n article-cats -t dat/dictionary.pruned/   
```
  
* Compute the list of ids that may appear as column ids in the similarity matrix (these are the 250K most linked-to ids):

  `./bin/make-valid-ids.sh conf/example-configuration.json 50`

* Generate the precomputed similarity matrices:

```bash
  ./bin/make-sims.sh jvm_MBs -c path/to/conf.json -n esa -o dat/esa-sims.matrix -r 500 -v dat/valid_ids.txt
  ./bin/make-sims.sh jvm_MBs -c path/to/conf.json -n article-text -o dat/text-sims.matrix -r 500 -v dat/valid_ids.txt
  ./bin/make-sims.sh jvm_MBs -c path/to/conf.json -n article-links -o dat/link-sims.matrix -r 500 -v dat/valid_ids.txt
  ./bin/make-sims.sh jvm_MBs -c path/to/conf.json -n article-inlinks -o dat/inlink-sims.matrix -r 500 -v dat/valid_ids.txt
```
  
  The text and esa jobs will take quite a while - many hours.

* Generate the tranposes of the similarity files:

```bash
  ./bin/transpose.sh ./dat/cat-sims.matrix ./dat/cat-sims.transpose.matrix 24000 5000
  ./bin/transpose.sh ./dat/text-sims.matrix ./dat/text-sims.transpose.matrix 24000 5000
  ./bin/transpose.sh ./dat/link-sims.matrix ./dat/link-sims.transpose.matrix 24000 5000
  ./bin/transpose.sh ./dat/esa-sims.matrix ./dat/esa-sims.transpose.matrix 24000 5000
  ./bin/transpose.sh ./dat/inlink-sims.matrix ./dat/inlink-sims.transpose.matrix 24000 5000
```
  
  These take about 30 min each.

* Generate the gold standard dataset:

  `./bin/make_gold.sh`

  This should take about a minute, and it writes a combined gold standard dataset from a variety of sources to dat/gold/combined.tab.txt.

* Fit the combined model

```bash
   ./bin/make-ensemble.sh 10100 
                        -c conf/example-configuration.json 
                        -e weka
                        -g dat/gold/combined.filtered.txt 
                        -o dat/weka.out 
                        -r 10000 
                        -t 35 
                        -v ./dat/valid_ids.txt
```

* Generate the final pairwise similarity matrix:
  `TODO`
