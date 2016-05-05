HuntBugs 0.0.2
===

[![Maven Central](https://img.shields.io/maven-central/v/one.util/huntbugs.svg)](https://maven-badges.herokuapp.com/maven-central/one.util/huntbugs/)
[![Build Status](https://travis-ci.org/amaembo/huntbugs.png?branch=master)](https://travis-ci.org/amaembo/huntbugs)
[![Coverage Status](https://coveralls.io/repos/github/amaembo/huntbugs/badge.svg?branch=master)](https://coveralls.io/github/amaembo/huntbugs?branch=master)

New Java bytecode static analyzer tool based on [Procyon Compiler Tools](https://bitbucket.org/mstrobel/procyon/overview) aimed to supersede the [FindBugs](http://findbugs.sourceforge.net/).
Currently in early development stage, though already could be tried.

Currently 135 FindBugs warnings reimplemented and several new warnings added.

### Use with Maven

Run `mvn one.util:huntbugs-maven-plugin:huntbugs`

The output report is located in `target/huntbugs/report.html`
