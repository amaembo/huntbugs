HuntBugs 0.0.7
===

[![Join the chat at https://gitter.im/amaembo/huntbugs](https://badges.gitter.im/amaembo/huntbugs.svg)](https://gitter.im/amaembo/huntbugs?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)
[![Maven Central](https://img.shields.io/maven-central/v/one.util/huntbugs.svg)](https://maven-badges.herokuapp.com/maven-central/one.util/huntbugs/)
[![Build Status](https://travis-ci.org/amaembo/huntbugs.png?branch=master)](https://travis-ci.org/amaembo/huntbugs)
[![Coverage Status](https://coveralls.io/repos/github/amaembo/huntbugs/badge.svg?branch=master)](https://coveralls.io/github/amaembo/huntbugs?branch=master)

New Java bytecode static analyzer tool based on [Procyon Compiler Tools](https://bitbucket.org/mstrobel/procyon/overview) aimed to supersede the [FindBugs](http://findbugs.sourceforge.net/).
Currently in early development stage, though already could be tried.

Currently 199 FindBugs warnings reimplemented and several new warnings added.

### Use with Maven

Run `mvn one.util:huntbugs-maven-plugin:huntbugs`

The output report is located in `target/huntbugs/report.html`

### Use with Ant

* Build `huntbugs-ant-plugin` via `mvn package` (or alternatively download from [here](https://oss.sonatype.org/content/repositories/releases/one/util/huntbugs-ant-plugin/))
* Take the resulting `huntbugs-ant-plugin-<version>-nodeps.jar`
* Define the task:

~~~~xml
<taskdef resource="one/util/huntbugs/ant/antlib.xml">
    <classpath path="path/to/huntbugs-ant-plugin-<version>-nodeps.jar"/>
</taskdef>
~~~~

* Run it:

~~~~xml
<huntbugs classPath="${MY_APP_CLASSPATH}" 
          auxClassPath="${DEPS_CLASSPATH}" 
          html="path/to/html/report.html" 
          xml="path/to/xml/report.xml"/>
~~~~

### Use with Gradle

Check the [Gradle plugin page](https://github.com/lavcraft/huntbugs-gradle-plugin)

### Use with Eclipse

Check the [Eclipse plugin page](https://github.com/aaasko/huntbugs-eclipse) (in early development stage)
