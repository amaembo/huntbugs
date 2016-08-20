HuntBugs 0.0.10
===

[![Join the chat at https://gitter.im/amaembo/huntbugs](https://badges.gitter.im/amaembo/huntbugs.svg)](https://gitter.im/amaembo/huntbugs?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)
[![Maven Central](https://img.shields.io/maven-central/v/one.util/huntbugs.svg)](https://maven-badges.herokuapp.com/maven-central/one.util/huntbugs/)
[![Build Status](https://travis-ci.org/amaembo/huntbugs.png?branch=master)](https://travis-ci.org/amaembo/huntbugs)
[![Coverage Status](https://coveralls.io/repos/github/amaembo/huntbugs/badge.svg?branch=master)](https://coveralls.io/github/amaembo/huntbugs?branch=master)

New Java bytecode static analyzer tool based on [Procyon Compiler Tools](https://bitbucket.org/mstrobel/procyon/overview) aimed to supersede the [FindBugs](http://findbugs.sourceforge.net/).
Currently in early development stage, though already could be tried.

Currently 222 FindBugs warnings reimplemented and several new warnings added.

### Use with Maven

Compile project and run `mvn one.util:huntbugs-maven-plugin:huntbugs`

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

### Exec as command-line tool

Command-line tool is mostly aimed to aid developers. Nevertheless you may use it if you like.
To launch use `mvn exec:java -Dexec.args="...args..."` inside huntbugs subdirectory. Examples:

* `mvn exec:java -Dexec.args="-lw"` will list all the warnings.
* `mvn exec:java -Dexec.args="myfolder/*.jar"` will analyze all jars inside `myfolder` writing the report into `huntbugs.warnings.xml` and `huntbugs.warnings.html` in current directory.
* `mvn exec:java` will show all the supported command line options.
