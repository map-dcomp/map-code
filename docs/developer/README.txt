Initial setup
-------------

Get the submodules setup

    cd root of repository
     ./setup

If you are on Windows and cannot execute the setup script, open it in a text editor and execute the git commands in the file to setup your environment.

Install eclipse, Neon has been tested.
Install the checkstyle plugin for eclipse.

Install gradle support http://www.vogella.com/tutorials/EclipseGradle/article.html only stel 2

Install scala support using update site http://download.scala-ide.org/sdk/lithium/e46/scala212/stable/site
At a minimum install  "Scala", "Scala IDE for Eclipse".

If you get errors about incompatible versions of the scala compiler.
  * Go into the eclipse project properties
  * Got down to "Scala Compiler"
  * Select "Use Project Settings"
  * Select a "Scala Installation" of "Latest 2.11 bundle (dynamic)"


Get the initial build setup

   cd src && ./build.sh
   
For each project in src

  1. rm .classpath
  2. ./gradlew eclipseClasspath
  3. Tell eclipse to import a project and select the directory 


After this, when you switch branches you'll need to execute `git submodule update` to ensure that the submodules are at the correct commit.
You can see if you need to do this by checking `git status` for a message about "new commits".

Building dependencies
---------------------

You will need to build P2Protelis and install it into your local maven repository before you can build the MAP agent.

    cd src/P2Protelis
    ./gradlew publish

Refresh the project in eclipse.

You will need to repeat this each time that P2Protelis changes versions.

The install step will run all of the tests for P2Protelis, this may take some time. You can skip the tests by running

    ./gradlew publish -x test

Checking for errors
-------------------

Before merging to master or checking in directly to master, please make sure there are no Checkstyle or FindBugs errors. These are also run in Jenkins, but at present it is only accessible from the BBN internal network.

Run static FindBugs and Checkstyle on a project:

    ./gradlew findbugsMain findbugsTest checkstyleMain checkstyleTest
    
Then look in `build/reports/checkstyle` and `build/reports/findbugs` for the results. The checkstyle reports are HTML, so can be opened in a browser. The FindBugs results are in XML, so you should download FindBugs from http://findbugs.sourceforge.net/ and then use the GUI to view the xml files that are created.

To suppress FindBugs warnings you can use @SuppressFBWarning(value="findbugs_check_name", justification="reason").
For example
    @SuppressFBWarnings(value = "DLS_DEAD_LOCAL_STORE", justification = "Bug in FindBugs 3.0.4")

To suppress checkstyle you can use the following:
    // CHECKSTYLE:OFF
    // reason for checkstyle being off
    ...
    // CHECKSTYLE:ON


Browsing documentation
----------------------

You can generate documentation by running

    ./gradlew javadoc

Then look in build/docs/javadoc/index.html.


Note about dependencies
-----------------------

P2Protelis cannot depend on anything in MAP. It is intended as a base that
MAP builds on top of and will eventually be pushed back to GitHub.


Running the visualization
--------------------

This assumes that you have run src/build.sh recently.

    cd src/MAP-Visualization
    ./gradlew build -x test
    java -jar build/libs/MAP-Visualization-0.0.1-executable.jar
    

Switch branches
---------------

When switching branches that reference different versions of one of the
dependencies you will need to update the eclipse classpath with:

    rm .classpath
    ./gradelew eclipseClasspath
    
Logging
------

The file map.logging.xml in the current working directory is used to
configure logging. If it's not present, then an internal logging
configuration is used.
