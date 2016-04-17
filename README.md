HuntBugs
===

New Java bytecode static analyzer tool based on [Procyon Compiler Tools](https://bitbucket.org/mstrobel/procyon/overview) aimed to supersede the [FindBugs](http://findbugs.sourceforge.net/).
Currently in early development stage, though already could be used:

~~~~
$ cd huntbugs
$ mvn clean package
$ cd target
$ java -jar huntbugs-0.0.1-SNAPSHOT.jar <jars or class folders>
~~~~

The output files are:

* huntbugs.stats.txt -- analysis stats
* huntbugs.errors.txt -- internal analyzer errors
* huntbugs.warnings.txt -- detected warnings in the analyzed code (plain text)
* huntbugs.warnings.xml -- detected warnings in the analyzed code (xml with descriptions)

Use `src/main/resources/huntbugs/report.xsl` to transform xml output to html.