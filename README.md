TeaVM
=====

What is TeaVM?
--------------

TeaVM is an ahead-of-time translator from Java bytecode to JVM.
It can be compared with GWT, however TeaVM does not require source code of your application and
all required libraries.
You can use TeaVM for building applications for the browser, due to the following features:

  * per-method dependency analyzer, that determines a set of methods that are really needed
    to run your application, so TeaVM won't translate whole JAR files;
  * fast JavaScript; for now it is almost as fast as the JavaScript, generated by GWT;
  * Java class library emulation;
  * integration with Maven and Eclipse;
  * generation of source maps;
  * debugger;
  * interoperation with JavaScript libraries together with the set of predefined browser interfaces.


Quick start
-----------

There are several options of using TeaVM. One is the Maven build.
The easiest way to create a new TeaVM project is to type in the command line:

    mvn -DarchetypeCatalog=local \
      -DarchetypeGroupId=org.teavm \
      -DarchetypeArtifactId=teavm-maven-webapp \
      -DarchetypeVersion=0.2.1 archetype:generate

Now you can execute `mvn clean package` and get the generated `war` file.
Deploy this `war` in Tomcat or another container, or simply unzip it and open the `index.html` page.

It is much easier to develop TeaVM applications using Eclipse.
If you prefer Eclipse, please read [this tutorial](https://github.com/konsoletyper/teavm/wiki/Eclipse-tutorial).

To learn TeaVM deeper, you take a look at the [teavm-samples](teavm-samples) module,
containing examples of TeaVM-based projects.
Also you can read [project's wiki](https://github.com/konsoletyper/teavm/wiki/).


DukeScript
----------

[DukeScript](http://wiki.apidesign.org/wiki/DukeScript) is a set of APIs that allows Java applications
easily talk to JavaScript environment to (usually) animate an HTML page. While DukeScript has its own
implementation of JVM, called [Bck2Brwsr](http://wiki.apidesign.org/wiki/Bck2Brwsr), TeaVM also provides
support for running DukeScript applications, using [teavm-html4j](teavm-html4j) plugin.


Live examples
-------------

Compare the speed of JavaScript produced by TeaVM and GWT here: http://teavm.org/live-examples/jbox2d-benchmark/

Play [Geobot](http://teavm.org/live-examples/geobot/), a little physics-based puzzle game.
Thanks to [joan789](http://joan789.deviantart.com/) for her great artwork!

Thanks to [Jaroslav Tulach](http://wiki.apidesign.org/wiki/User:JaroslavTulach), author of DukeScript, we have several
DukeScript example applications. One is the minesweeper game.
You can try its TeaVM-compiled version [here](http://xelfi.cz/minesweeper/teavm/), and then take a look at
[source code](http://source.apidesign.org/hg/html~demo/file/4dce5ea7e13a/minesweeper/src/main/java/org/apidesign/demo/minesweeper/MinesModel.java)
and [HTML page](http://source.apidesign.org/hg/html~demo/file/4dce5ea7e13a/minesweeper/src/main/webapp/pages/index.html).

Another example is avaialble [here](http://graphhopper.com/teavm/).
It uses [GraphHopper](https://github.com/graphhopper/graphhopper/) to build route in browser.
Unlike original GraphHopper example it works completely in browser instead of querying server.
Thanks to [Peter Karich](https://github.com/karussell).
