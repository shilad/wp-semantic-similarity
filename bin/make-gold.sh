#!/bin/bash

DL=./dat/gold/dl
SRC=./dat/gold/src  # src datasets

rm -rf $DL
mkdir -p $DL
rm -rf $SRC
mkdir -p $SRC

# Downloads five datasets and combines them into a single gold standard

# Gabrilovich et al, 2002
# see http://www.cs.technion.ac.il/~gabr/resources/data/wordsim353/
#wget -P $DL http://www.cs.technion.ac.il/~gabr/resources/data/wordsim353/wordsim353.zip &&
#mkdir $DL/wordsim353 &&
#unzip -d $DL/wordsim353 $DL/wordsim353.zip &&
#tail -n '+2' $DL/wordsim353/combined.csv > $SRC/wordsim353.csv || 
#{ echo "ERROR: preparing wordsim353 failed" >&2; exit 1;}

# MTurk, Radinsky et al, 2011
# see http://www.technion.ac.il/~kirar/Datasets.html
#wget -P $DL http://www.technion.ac.il/~kirar/files/Mtruk.csv &&
#cp -p $DL/Mtruk.csv $SRC/radinsky.csv || 
#{ echo "ERROR: preparing radinsky dataset failed" >&2; exit 1;}

# Concept sim, Miller et al, 1991
# http://www.seas.upenn.edu/~hansens/conceptSim/
#wget -P $DL http://www.seas.upenn.edu/~hansens/conceptSim/ConceptSim.tar.gz &&
#tar -C $DL -xzvf $DL/ConceptSim.tar.gz &&
#sed -e 's/	[	]*/,/g' < $DL/ConceptSim/MC_word.txt  > $SRC/MC.csv &&
#sed -e 's/	[	]*/,/g' < $DL/ConceptSim/RG_word.txt  > $SRC/RG.csv ||
#{ echo "ERROR: preparing conceptsim dataset failed" >&2; exit 1;}

# Atlasify: Hecht et al, 2012
#
#wget -P $DL http://www.cs.northwestern.edu/~ddowney/data/atlasify240.csv &&
#cp -p $DL/atlasify240.csv $SRC/atlasify240.csv ||
#{ echo "ERROR: preparing atlasify dataset failed" >&2; exit 1;}

# WikiSimi
#
wget -P $DL http://sigwp.org/wikisimi/WikiSimi3000_1.csv &&
cp -p $DL/WikiSimi3000_1.csv  $SRC/WikiSimi3000.tab ||
{ echo "ERROR: preparing wikisimi dataset failed" >&2; exit 1;}

python2 src/main/python/combine_gold.py $SRC/*.* >dat/gold/combined.txt &&
python2 src/main/python/filter_gold.py <dat/gold/combined.txt >dat/gold/combined.filtered.txt ||
{ echo "ERROR: combining datasets failed" >&2; exit 1;}

python2 src/main/python/combine_gold.py $SRC/WikiSimi3000.tab |
python2 src/main/python/filter_gold.py /combined.txt >dat/gold/combined.articles.txt ||
{ echo "ERROR: combining datasets failed" >&2; exit 1;}


echo "SUCCESS!"
