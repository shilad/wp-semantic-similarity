wp-semantic-similarity
======================
This project creates a large-scale semantic similarity network between Wikipedia articles.
The semantic network is computed in batch mode using three types of article features: article text, article links, and article categories.

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

  `./bin/index.sh conf/example.configuration.json jvm_MBs esa`
  `./bin/index.sh conf/example.configuration.json jvm_MBs text cats links main`

* Create the concept mapper index, which is used to map phrases to Wikipedia articles. Download dictionary.bz2 from http://www-nlp.stanford.edu/pubs/crosswikis-data.tar.bz2/dictionary.bz2, then run:

  `./bin/make-concept-index.sh path/to/dictionary.bz2 path/to/index/output-directory jvm_MBs`
  
* TODO: compute the list of ids that will appear as column ids in the similar matrix

* Generate the precomputed similarity matrices:

  `./bin/make-sims.sh jvm_MBs -c path/to/conf.json -n esa -o dat/esa-sims.matrix -r 500 -v dat/valid_ids.txt`
  `./bin/make-sims.sh jvm_MBs -c path/to/conf.json -n text -o dat/text-sims.matrix -r 500 -v dat/valid_ids.txt`
  `./bin/make-sims.sh jvm_MBs -c path/to/conf.json -n links -o dat/link-sims.matrix -r 500 -v dat/valid_ids.txt`
  `./bin/make-sims.sh jvm_MBs -c path/to/conf.json -n inlinks -o dat/inlink-sims.matrix -r 500 -v dat/valid_ids.txt`
  
  The text and esa jobs will take quite a while - many hours.

* Generate the tranposes of the similarity files:

  `./bin/transpose.sh ./dat/cat-sims.matrix ./dat/cat-sims.transpose.matrix 24000 5000`
  `./bin/transpose.sh ./dat/text-sims.matrix ./dat/text-sims.transpose.matrix 24000 5000`
  `./bin/transpose.sh ./dat/link-sims.matrix ./dat/link-sims.transpose.matrix 24000 5000`
  `./bin/transpose.sh ./dat/esa-sims.matrix ./dat/esa-sims.transpose.matrix 24000 5000`
  `./bin/transpose.sh ./dat/inlink-sims.matrix ./dat/inlink-sims.transpose.matrix 24000 5000`
  
  These take about 30 min each.

* Generate the gold standard dataset:

  `./bin/make_gold.sh`

  This should take about a minute, and it writes a combined gold standard dataset from a variety of sources to dat/gold/combined.tab.txt.

* Fit the combined model:
  `TODO`

* Generate the final pairwise similarity matrix:
  `TODO`
