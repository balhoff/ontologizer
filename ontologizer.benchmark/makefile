#
# A simple makefile to conduct the benchmark
#

.PHONY: all
all: clperf
	cd ../ontologizer.parent && mvn package -DskipTests
	ONTOLOGIZER_ENABLE_EXPERIMENTAL=yes java -jar target/ontologizer.benchmark-0.0.1-SNAPSHOT-jar-with-dependencies.jar \
		--obo ../ontologizer.tests/data/gene_ontology.1_2.obo.gz \
		--association ../ontologizer.tests/data/gene_association.sgd.gz \
		--max-terms-per-combination 3 \
		--term-combinations-per-run 20 \
		--output-dir /tmp/ontobench

.PHONY: help
help:
	ONTOLOGIZER_ENABLE_EXPERIMENTAL=yes java -jar target/ontologizer.benchmark-0.0.1-SNAPSHOT-jar-with-dependencies.jar --help

clperf:
	if [ ! -d "clperf.git" ]; then git clone https://github.com/sba1/clperf.git clperf.git; fi
	make -C clperf.git
	cp clperf.git/clperf .
