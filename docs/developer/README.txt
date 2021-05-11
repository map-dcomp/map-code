Initial setup
-------------

Get the submodules setup

    cd root of repository
     ./setup

If you are on Windows and cannot execute the setup script, open it in a text editor and execute the git commands in the file to setup your environment.

Install eclipse
Install the checkstyle plugin for eclipse.

Get the initial build setup

  1. cd src && ./gradlew assemble
  2. cd src && ./gradlew cleanEclipseClasspath eclipseClasspath
  3. Tell eclipse to import a project and select the directory 


After this, when you switch branches you'll need to execute `git submodule update` to ensure that the submodules are at the correct commit.
You can see if you need to do this by checking `git status` for a message about "new commits".

Checking for errors
-------------------

Before merging to master or checking in directly to master, please make sure there are no Checkstyle or SpotBugs errors. These are also run in Jenkins, but at present it is only accessible from the BBN internal network.

Run static FindBugs and Checkstyle on a project:

    ./gradlew spotbugsMain spotbugsTest checkstyleMain checkstyleTest
    
Then look in `build/reports/checkstyle` and `build/reports/spotbugs` for the results. The checkstyle reports are HTML, so can be opened in a browser. The SpotBugs results are in XML, so you should download SpotBugs from https://spotbugs.readthedocs.io/en/stable/installing.html and then use the GUI to view the xml files that are created.

To suppress SpotBugs warnings you can use @SuppressFBWarning(value="findbugs_check_name", justification="reason").
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

    cd src
    ./gradlew :MAP-Visualization:assemble
    java -jar MAP-Visualization/build/libs/MAP-Visualization-0.0.1-executable.jar
    

Switch branches
---------------

When switching branches that reference different versions of one of the
dependencies you will need to update the eclipse classpath with:

    ./gradlew cleanEclipseClasspath
    ./gradelew eclipseClasspath
    
Logging
------

The file map.logging.xml in the current working directory is used to
configure logging. If it's not present, then an internal logging
configuration is used.
