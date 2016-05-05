HuntBugs 0.0.2
===

New Java bytecode static analyzer tool based on [Procyon Compiler Tools](https://bitbucket.org/mstrobel/procyon/overview) aimed to supersede the [FindBugs](http://findbugs.sourceforge.net/).
Currently in early development stage, though already could be tried.

Currently 135 FindBugs warnings reimplemented and several new warnings added.

### Use with Maven

Run `mvn one.util:huntbugs-maven-plugin:huntbugs`

The output report is located in `target/huntbugs/report.html`
