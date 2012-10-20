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
* Create the lucene index. This should take an hour or two:

  `./bin/index.sh`

* Generate the similarity files:

  `./bin/cat-sim.sh ./dat/lucene/cats/ ./dat/cat.sims.matrix 500 cache-size-in-MB`
  `./bin/text-sim.sh ./dat/lucene/text/ ./dat/text.sims.matrix 500 cache-size-in-MB`
  `./bin/link-sim.sh ./dat/lucene/links/ ./dat/links.sims.matrix 500 cache-size-in-MB`

  The cat and link jobs are fast (about an hour). The text job takes about 5 to 10 hours.

* Generate the tranposes of the similarity files:

  `./bin/transpose.sh ./dat/cat-sims.matrix ./dat/cat-sims.transpose.matrix 24000 5000`
  `./bin/transpose.sh ./dat/text-sims.matrix ./dat/text-sims.transpose.matrix 24000 5000`
  `./bin/transpose.sh ./dat/link-sims.matrix ./dat/link-sims.transpose.matrix 24000 5000`
  
  These take about 30 min each.

* Generate the pairwise similarity files:

  `./bin/pairwise-sim.sh ./dat/cat-sims.matrix ./dat/cat-sims.transpose.matrix ./dat/cat-sims-stage2.matrix 500 jvm_MBs`
  `./bin/pairwise-sim.sh ./dat/text-sims.matrix ./dat/text-sims.transpose.matrix ./dat/text-sims-stage2.matrix 500 jvm_MBs`
  `./bin/pairwise-sim.sh ./dat/links-sims.matrix ./dat/links-sims.transpose.matrix ./dat/links-sims-stage2.matrix 500 jvm_MBs`

  These are SLOW. About a day and a half each.