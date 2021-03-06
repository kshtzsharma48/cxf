WS-Security Demo  (UsernameToken and Timestamp)
=================

This demo shows how WS-Security support in Apache CXF may be enabled.

WS-Security can be configured to the Client and Server endpoints by adding WSS4JInterceptors.
Both Server and Client can be configured for outgoing and incoming interceptors. Various Actions like,
Timestamp, UsernameToken, Signature, Encryption, etc., can be applied to the interceptors by passing
appropriate configuration properties.

The logging feature is used to log the inbound and outbound
SOAP messages and display these to the console.

In all other respects this demo is based on the basic hello_world sample.

Please review the README in the samples directory before continuing.


Prerequisite
------------

If your environment already includes cxf-manifest.jar on the CLASSPATH,
and the JDK and ant bin directories on the PATH, it is not necessary to
run the environment script described in the samples directory README.
If your environment is not properly configured, or if you are planning
on using wsdl2java, javac, and java to build and run the demos, you must
set the environment by running the script.


*** Requirements ***

The samples in this directory use STRONG encryption.  The default encryption algorithms
included in a JRE is not adequate for these samples.   The Java Cryptography Extension
(JCE) Unlimited Strength Jurisdiction Policy Files available on Oracle's JDK download
page[3] *must* be installed for the examples to work.   If you get errors about invalid
key lengths, the Unlimited Strength files are not installed.

[3] http://www.oracle.com/technetwork/java/javase/downloads/index.html


Building and running the demo using Maven
---------------------------------------

From the base directory of this sample (i.e., where this README file is
located), the maven pom.xml file can be used to build and run the demo.


Using either UNIX or Windows:

  mvn install (builds the demo)
  mvn -Pserver  (from one command line window)
  Mvn -Pclient  (from a second command line window)

On startup, the client makes a sequence of 4 two-way invocations.

To remove the code generated from the WSDL file and the .class
files, run "mvn clean".


