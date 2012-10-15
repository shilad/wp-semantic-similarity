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
* About 50GB of free disk space.
* A reasonably fast machine (dual or quad core)

Instructions for building the semantic similarity network:
-----------
* Clone this project
* Download the wikipedia dumps from http://dumps.wikimedia.org/enwiki/ . You want the *-pages-articles*.bz2 files, but the version broken down into many (25 or so) different bz2 files.
* Create the lucene index: `./bin/index.sh`
* Generate the similarity files:

  `./bin/cat-sim.sh ./dat/lucene/cats/ ./dat/cat.sims.matrix 500 cache-size-in-MB`
  `./bin/text-sim.sh ./dat/lucene/text/ ./dat/text.sims.matrix 500 cache-size-in-MB`
  `./bin/link-sim.sh ./dat/lucene/links/ ./dat/links.sims.matrix 500 cache-size-in-MB`
