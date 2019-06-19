# Coroutines

<p align="center"><img src ="logo.png" alt="Coroutines logo" /></p>

Inspired by the [Apache Commons Javaflow](http://commons.apache.org/sandbox/commons-javaflow/) project, the Coroutines project is a Java toolkit that allows you to write coroutines in Java. Coroutines allows you to suspend the execution your Java method at will, save its state, and resume executing it from that saved state at a later point in time.

Why use Coroutines over Javaflow? The Couroutines project is a new Java coroutines implementation written from scratch that aims to solve some of the issues that Javaflow has. The Coroutines project provides several distinct advantages:

* Saves and loads method state faster than Javaflow [<sub>[1]</sub>](#footnotes)
* Provides Maven, Ant, and Gradle plugins [<sub>[2]</sub>](#footnotes)
* Provides a Java Agent [<sub>[3]</sub>](#footnotes)
* Proper support for Java 8+ bytecode [<sub>[4]</sub>](#footnotes)
* Proper support for synchronized blocks [<sub>[5]</sub>](#footnotes)
* Proper support for serialization and versioning [<sub>[6]</sub>](#footnotes)
* Modular project structure and the code is readable, tested, and well commented [<sub>[7]</sub>](#footnotes)

In addition, Javaflow appears to be largely unmaintained at present.

More information on the topic of coroutines and their advantages can be found on the following pages:

* [Wikipedia: Coroutine](https://en.wikipedia.org/wiki/Coroutine)
* [Wikipedia: Fiber (computer science)](https://en.wikipedia.org/wiki/Fiber_(computer_science))
* [Stackoverflow: Difference between a "coroutine" and a "thread"?](https://stackoverflow.com/a/23436125)

## Table of Contents

 * [Quick-start Guide](#quick-start-guide)
   * [Maven Instructions](#maven-instructions)
   * [Ant Instructions](#ant-instructions)
   * [Gradle Instructions](#gradle-instructions)
   * [Java Agent Instructions](#java-agent-instructions)
   * [Code Example](#code-example)
 * [Serialization and Versioning Guide](#serialization-and-versioning-guide)
   * [Serialization Instructions](#serialization-instructions)
   * [Versioning Instructions](#versioning-instructions)
     * [Example: Modifying state in current versions](#modifying-state-in-current-versions)
     * [Example: Upgrading from old versions (forward compatability)](#example-upgrading-from-old-versions-forward-compatability)
     * [Example: Supporting old versions (backward compatability)](#example-supporting-old-versions--backward-compatability)
   * [Common Pitfalls and Best Practices](#common-pitfalls-and-best-practices)
     * [Serialization pitfalls and best practices](#serialization-pitfalls-and-best-practices)
     * [Versioning pitfalls and best practices](#versioning-pitfalls-and-best-practices)
 * [Configuration Guide](#configuration-guide)
   * [Debug Mode](#debug-mode)
   * [Auto Serializable](#auto-serializable)
   * [Marker Type](#marker-type)
 * [FAQ](#faq)
   * [How much overhead am I adding?](#how-much-overhead-am-i-adding)
   * [What projects make use of Coroutines?](#what-projects-make-use-of-coroutines)
   * [What restrictions are there?](#what-restrictions-are-there)
     * [Reflection API](#reflection-api)
     * [Lambdas and INVOKEDYNAMIC](#lambdas-and-invokedynamic)
   * [Can I use this with an IDE?](#can-i-use-this-with-an-ide)
   * [What alternatives are available?](#what-alternatives-are-available)
 * [Change Log](#change-log)
 * [Footnotes](#footnotes)

## Quick-start Guide

The Coroutines project relies on bytecode instrumentation to make your coroutines work. Maven, Ant, and Gradle plugins are provided to instrument your code. In addition to these plugins, a Java Agent is provided to instrument your code at runtime. Although your code can target any version of Java from Java 1.6 to Java 10, the plugins and Java Agent that instrument your code will require Java 10 to run.

### Maven Instructions

In your POM...

First, add the "user" module as a dependency.
```xml
<dependency>
    <groupId>com.offbynull.coroutines</groupId>
    <artifactId>user</artifactId>
    <version>1.5.3</version>
</dependency>
```

Then, add the Maven plugin so that your classes get instrumented when you build.
```xml
<plugin>
    <groupId>com.offbynull.coroutines</groupId>
    <artifactId>maven-plugin</artifactId>
    <version>1.5.3</version>
    <executions>
        <!-- Instruments main classes at process-classes phase -->        
        <execution>
            <id>coroutines-instrument-id</id>
            <goals>
                <goal>instrument</goal>
            </goals>
        </execution>
        <!-- Instruments test classes at process-test-classes phase -->
        <execution>
            <id>test-coroutines-instrument-id</id>
            <goals>
                <goal>test-instrument</goal>
            </goals>
        </execution>
    </executions>
    <configuration>
        <!-- Uncomment if you'll be stepping through your coroutines in an IDE. -->
        <!-- <debugMode>true</debugMode> -->
    </configuration>
</plugin>
```

### Ant Instructions

In your build script...

First, define the Ant Task. It's available for download from [Maven Central](https://repo1.maven.org/maven2/com/offbynull/coroutines/ant-plugin/1.5.3/ant-plugin-1.5.3-shaded.jar).
```xml
<taskdef name="InstrumentTask" classname="com.offbynull.coroutines.antplugin.InstrumentTask">
    <classpath>
        <pathelement location="ant-task-1.5.3-shaded.jar"/>
    </classpath>
</taskdef>
```

Then, bind it to the target of your choice.
```xml
<target name="-post-compile">
    <!-- The classpath attribute is a semicolon delimited list of the classpath required by your code. -->
    <!-- Add the attribute debugMode="true" if you'll be stepping through your coroutines in an IDE. -->
    <InstrumentTask classpath="" sourceDirectory="build" targetDirectory="build"/>
</target>
```

You'll also need to include the "user" module's JAR in your classpath as a part of your build. It's also available for download from [Maven Central](https://repo1.maven.org/maven2/com/offbynull/coroutines/user/1.5.3/user-1.5.3.jar).

### Gradle Instructions

In your build script...

First, instruct Gradle to pull the coroutines plugin from Maven central...

```groovy
buildscript {
    repositories {
        mavenCentral()
    }

    dependencies {
        classpath group: 'com.offbynull.coroutines',  name: 'gradle-plugin',  version: '1.5.3'
    }
}
```

Then, apply the coroutines plugin and add the "user" module as a dependency...

```groovy
apply plugin: "java"
apply plugin: "coroutines"

coroutines {
    // Uncomment if you'll be stepping through your coroutines in an IDE.
    // debugMode = true 
}

repositories {
    mavenCentral()
}

dependencies {
    compile group: 'com.offbynull.coroutines', name: 'user', version: '1.5.3'
}
```

### Java Agent Instructions

The Coroutines Java Agent allows you to instrument your coroutines at runtime instead of build-time. That means that the bytecode instrumentation required to make your coroutines work happens when your application runs instead of when your application gets compiled.

To use the Java Agent, download it from [Maven Central](https://repo1.maven.org/maven2/com/offbynull/coroutines/java-agent/1.5.3/java-agent-1.5.3-shaded.jar) and apply it when you run your Java program...

```shell
java -javaagent:java-agent-1.5.3-shaded.jar myapp.jar

# Set the debug mode to true if you'll be stepping through your coroutines in
# an IDE. You can enable debug mode via Java Agent arguments
#
# -javaagent:java-agent-1.5.3-shaded.jar=debugMode=true
#
# By default, debug mode is false. 
```

The Coroutines Java Agent won't instrument classes that have already been instrumented, so it should be safe to use it with coroutine classes that may have already gone through instrumentation (as long as those classes have been instrumented by the same version of the instrumenter).

### Code Example

First, declare your coroutine...
```java
public static final class MyCoroutine implements Coroutine {
    @Override
    public void run(Continuation c) {
        System.out.println("started");
        for (int i = 0; i < 10; i++) {
            echo(c, i);
        }
    }

    private void echo(Continuation c, int x) {
        System.out.println(x);
        c.suspend();
    }
}
```

Then, execute your coroutine...
```java
CoroutineRunner r = new CoroutineRunner(new MyCoroutine());
r.execute();
r.execute();
r.execute();
r.execute();
```

This is what your output should look like...
```
started
0
1
2
3
```

Any method that takes in a ```Continuation``` type as a parameter will be instrumented by the plugin to work as part of a coroutine. The entry-point for your coroutine must implement the ```Coroutine``` interface. ```CoroutineRunner.execute()``` is used to start / resume execution of your coroutine, while ```Continuation.suspend()``` suspends the execution of your coroutine.

**:warning: WARNING -- Use ```Continuation``` objects as intended. :warning:**

1. The ```Continuation``` object is not meant to be retained. Never set it to a field or otherwise pass it to a method that isn't intended to run as part of a coroutine.
1. The only methods on ```Continuation``` that you should be calling are ```suspend()```, ```getContext()```, and ```setContext()```. All other methods are for internal use only.

## Serialization and Versioning Guide

**:warning: WARNING -- This is an advanced feature. Familiarity with JVM bytecode is highly recommended. :warning:**

The Coroutines project provides support for serialization and versioning. Serialization and versioning work hand-in-hand. Serialization allows you to convert your coroutine to a byte array and vice-versa, while versioning allows you to make small tweaks to your coroutine's logic while still being able to load up serialized data from previous versions.

Typical use-cases include...

 * checkpointing/caching your coroutine to disk and loading it back out again.
 * transmitting your coroutine over the wire.
 * forking your coroutine.

### Serialization Instructions

To serialize / deserialize a coroutine, use ```CoroutineWriter``` and ```CoroutineReader```. By default, these classes use Java's built-in object serialization mechanism, which means objects that makes up your coroutine's state (classes of methods called, objects on operand stack, objects on local variable table) must implement ```java.io.Serializable``` (see the [Auto Serializable](#auto-serializable) configuration option for more information).

Basic example of serialization/deserialization...
```java
public final class MyCoroutine implements Coroutine {
    @Override
    public void run(Continuation c) {
        System.out.println("started");
        for (int i = 0; i < 10; i++) {
            echo(c, i);
        }
    }

    private void echo(Continuation c, int x) {
        System.out.println(x);
        c.suspend();
    }
}

// Start it and execute it once
CoroutineRunner r1 = new CoroutineRunner(new MyCoroutine());
r1.execute();

// Serialize it and deserialize to a new object -- this is essentially a fork
byte[] dumped = new CoroutineWriter().write(r1);
CoroutineRunner r2 = new CoroutineReader().read(dumped);

// Continue executing both the original and the fork, alternating between the
// two
r1.execute();
r2.execute();
r1.execute();
r2.execute();
r1.execute();
r2.execute();
```

Execution output...
```
started
0
1
1
2
2
3
3
```

To further control how coroutines get serialized/deserialized, create custom implementations of ```CoroutineWriter.CoroutineSerializer``` and ```CoroutineReader.CoroutineDeserializer```. These custom implementations can be directly passed in to ```CoroutineWriter``` and ```CoroutineReader```. This is useful in cases where you may want to filter data, output to a different serialization format (e.g. XML, JSON, YAML, etc..), or use a different serializer (e.g. XStream, Kryo, Jackson, GSON, etc..).

### Versioning Instructions

When using one of the provided build system plugins on your code, classes which contain methods intended to run as part of a coroutine will have a corresponding file generated with the same name, but with a ```.coroutinesinfo``` extension. These files are human-readable and contain basic information required for supporting versioning. They will be included along-side your class files (both in your build path and JAR).

Basic example...

```java
import com.offbynull.coroutines.user.Continuation;
import com.offbynull.coroutines.user.Coroutine;
import java.io.Serializable;
import java.util.Random;

public final class MyCoroutine implements Coroutine {

    @Override
    public void run(Continuation c) {
        Random random = new Random(0);

        System.out.println("started");
        for (int i = 0; i < 10; i++) {
            echo(c, i, random.nextInt());
        }
    }

    private void echo(Continuation c, int i, int value) {
        c.suspend();
        System.out.println(value);
    }
}
```

The corresponding ```MyCoroutine.coroutinesinfo``` file that gets generated...

```
Class Name: MyCoroutine
Method Name: run
Method Params: (Lcom/offbynull/coroutines/user/Continuation;)V
Method Return: V
Method ID: -1538415977
------------------------------------
Continuation Point ID: 0    Line: 15   Type: NormalInvokeContinuationPoint
  varObjects[0]        // LVT index is 0 / name is this / type is LMyCoroutine;
  varObjects[1]        // LVT index is 1 / name is c / type is Lcom/offbynull/coroutines/user/Continuation;
  varObjects[2]        // LVT index is 2 / name is random / type is Ljava/util/Random;
  varInts[0]           // LVT index is 3 / name is i / type is int
  operandObjects[0]    // operand index is 0 / type is LMyCoroutine;
  operandObjects[1]    // operand index is 1 / type is Lcom/offbynull/coroutines/user/Continuation;
  operandInts[0]       // operand index is 2 / type is int
  operandInts[1]       // operand index is 3 / type is int


Class Name: MyCoroutine
Method Name: echo
Method Params: (Lcom/offbynull/coroutines/user/Continuation;II)V
Method Return: V
Method ID: 1191091979
------------------------------------
Continuation Point ID: 0    Line: 18   Type: SuspendContinuationPoint
  varObjects[0]        // LVT index is 0 / name is this / type is LMyCoroutine;
  varObjects[1]        // LVT index is 1 / name is c / type is Lcom/offbynull/coroutines/user/Continuation;
  varInts[0]           // LVT index is 2 / name is i / type is int
  varInts[1]           // LVT index is 3 / name is value / type is int
  operandObjects[0]    // operand index is 0 / type is Lcom/offbynull/coroutines/user/Continuation;
```

For each method identified to run as part of a coroutine, the corresponding ```.coroutinesinfo``` file details the...

 * basic method details (signature, return type, name, owning class, etc..).
 * unique ID used to identify the method (based on class name, method signature, and method bytecode).
 * continuation points in the method (where ```Continuation.suspend()``` is called / where methods that takes in a ```Continuation``` object are called).
 * types expected on the local variables table and operand stack at each continuation point

When a method intended to run as part of a coroutine is changed, the ID gets updated. Diffing the previous ```.coroutinesinfo``` against the new ```.coroutinesinfo``` will identify what needs to be changed for deserialization of previous versions to work, if anything. If changes are required, they can be applied by using the ```FrameUpdatePoint``` / ```FrameInterceptPoint``` interfaces. The following subsections provide a few basic versioning examples with the ```MyCoroutine``` example class provided above (please read them in order).

It's important to note that versioning has its limits. This feature is intended for use-cases such as hot-deploying small emergency fixes/patches to a server or enabling saves from older versions of a game to run on newer versions. It isn't intended for cases where there are large structural changes.

#### Example: Modifying state in current version

Notice how the first line of ```MyCoroutine.run()``` creates a ```Random``` seeded with 0. If we serialize this coroutine, we can replace the ```Random``` on deserialization with a more robust random number generator that isn't deterministically seeded.

Imagine we start the coroutine, run it a few times, and then serialize it...

```java
// Create the coroutine.
Coroutine myCoroutine = new MyCoroutine();
CoroutineRunner runner = new CoroutineRunner(myCoroutine);
        
// Run it a few times.
runner.execute();
runner.execute();
runner.execute();
runner.execute();

// Checkpoint it.
CoroutineWriter writer = new CoroutineWriter();
byte[] data = writer.write(runner);
```

```MyCoroutines.coroutinesinfo``` states that the ```Random``` object sits in index 2 of the objects variable array...

```
Class Name: MyCoroutine
Method Name: run
Method Params: (Lcom/offbynull/coroutines/user/Continuation;)V
Method Return: V
Method ID: -1538415977
------------------------------------
Continuation Point ID: 0    Line: 15   Type: NormalInvokeContinuationPoint
  varObjects[0]        // LVT index is 0 / name is this / type is LMyCoroutine;
  varObjects[1]        // LVT index is 1 / name is c / type is Lcom/offbynull/coroutines/user/Continuation;
  varObjects[2]        // LVT index is 2 / name is random / type is Ljava/util/Random;
  varInts[0]           // LVT index is 3 / name is i / type is int
  operandObjects[0]    // operand index is 0 / type is LMyCoroutine;
  operandObjects[1]    // operand index is 1 / type is Lcom/offbynull/coroutines/user/Continuation;
  operandInts[0]       // operand index is 2 / type is int
  operandInts[1]       // operand index is 3 / type is int
```

When we deserialize, we can explicitly tell the ```CoroutineReader``` to intercept the frame at this point and update the ```Random``` with a ```SecureRandom```...

```java
// Create frame update point for the point we want to intercept.
FrameInterceptPoint randomObjectUpdater = new FrameInterceptPoint(
        "MyCoroutine", // class to intercept
        -1538415977,   // method ID to intercept
        0,             // continuation point ID to intercept
        (frame, mode) -> {
            // Get new secure random
            SecureRandom secureRandom;
            try {
                secureRandom = SecureRandom.getInstanceStrong();
            } catch (NoSuchAlgorithmException nsae) {
                throw new RuntimeException(nsae);
            }

            // Replace existing random with new secure random
            Object[] objectVars = frame.getVariables().getObjects();
            objectVars[2] = secureRandom;
            return frame.withObjectVariables(objectVars);
        }
);

// Create reader with that frame intercept point.
CoroutineReader reader = new CoroutineReader(
        new FrameInterceptPoint[] { randomObjectUpdater }
);

// Load up the coroutine from the checkpoint. It should now contain a
// SecureRandom instead of a random.
runner = reader.read(data);

// Execute runner 6 times.
runner.execute();
runner.execute();
runner.execute();
runner.execute();
runner.execute();
runner.execute();
```

Now, if we execute this deserialized coroutine, we'll get numbers printed to stdout using our new ```SecureRandom```. Here's the output of 3 separate runs (both the serialization and deserialization portion). Note that on each run, the first 4 ```runner.execute()``` calls prior to serialization will always produce the same numbers on each run, while the 6 ```runner.execute()``` after deserializing will produce unique random numbers...

```
started
-1155484576
-723955400
1033096058
-1690734402
885992898
1619574133
-1366677246
1462064449
1424250291
-1216638635

started
-1155484576
-723955400
1033096058
-1690734402
-1935797204
-1633890489
-2128895307
-1600199017
-626132767
-346399779

started
-1155484576
-723955400
1033096058
-1690734402
1582406223
1521161502
512654592
937311901
809684394
431304956
```

#### Example: Upgrading from old versions (forward compatability)

If you update a method that's intended to run as part of a coroutine but still want to support deserializing from the old version of that method, you'll need to supply a ```FramePointUpdater``` to convert the older versions the new version.

Imagine we start ```MyCoroutine```, run it a few times, and then serialize it...

```java
// Create the coroutine.
Coroutine myCoroutine = new MyCoroutine();
CoroutineRunner runner = new CoroutineRunner(myCoroutine);
        
// Run it a few times.
runner.execute();
runner.execute();
runner.execute();
runner.execute();

// Checkpoint it.
CoroutineWriter writer = new CoroutineWriter();
byte[] data = writer.write(runner);
        
Files.write(Paths.get(".testfile.tmp"), data);
```

We then decide to tweak ```MyCoroutine.echo()``` to write some extra information to stdout:  a string that includes the iteration and if the value is divisible by 2...

```java
private void echo(Continuation c, int i, int value) {
    String extraInfo = "Iteration " + i + " and value is divisible by 2: " + (value % 2);

    c.suspend();

    System.out.println(value + " " + extraInfo);
}
```

The updated method should produce the following updated ```MyCoroutines.coroutinesinfo``` entry...

```
Class Name: MyCoroutine
Method Name: echo
Method Params: (Lcom/offbynull/coroutines/user/Continuation;II)V
Method Return: V
Method ID: -571717800
------------------------------------
Continuation Point ID: 0    Line: 20   Type: SuspendContinuationPoint
  varObjects[0]        // LVT index is 0 / name is this / type is LMyCoroutine;
  varObjects[1]        // LVT index is 1 / name is c / type is Lcom/offbynull/coroutines/user/Continuation;
  varInts[0]           // LVT index is 2 / name is i / type is int
  varInts[1]           // LVT index is 3 / name is value / type is int
  varObjects[2]        // LVT index is 4 / name is extraInfo / type is Ljava/lang/String;
  operandObjects[0]    // operand index is 0 / type is Lcom/offbynull/coroutines/user/Continuation;
```

If we diff the old ```MyCoroutines.coroutinesinfo``` with this new one, we'll get a clear picture of what needs to be changed...

```diff
 Class Name: MyCoroutine
 Method Name: echo
 Method Params: (Lcom/offbynull/coroutines/user/Continuation;II)V
 Method Return: V
-Method ID: 1191091979
+Method ID: -571717800
 ------------------------------------
-Continuation Point ID: 0    Line: 18   Type: SuspendContinuationPoint
+Continuation Point ID: 0    Line: 20   Type: SuspendContinuationPoint
   varObjects[0]        // LVT index is 0 / name is this / type is LMyCoroutine;
   varObjects[1]        // LVT index is 1 / name is c / type is Lcom/offbynull/coroutines/user/Continuation;
   varInts[0]           // LVT index is 2 / name is i / type is int
   varInts[1]           // LVT index is 3 / name is value / type is int
+  varObjects[2]        // LVT index is 4 / name is extraInfo / type is Ljava/lang/String;
   operandObjects[0]    // operand index is 0 / type is Lcom/offbynull/coroutines/user/Continuation;
```

We can see that the method ID got updated from ```1191091979``` to ```-571717800``` and a new item was added to index 2 of the objects variable array. When we deserialize, we can explicitly tell the CoroutineReader to intercept the old version and update it so this new variable slot is properly filled in...

```java
// Read it back in, making sure to add an updater to handle changes required
// for this modified version.
FrameUpdatePoint updater = new FrameUpdatePoint(
        "MyCoroutine",// class name to intercept
        1191091979,   // method ID to intercept
        0,            // continuation point to intercept
        (frame, mode) -> {
            // Read in old variables and compute the missing variable
            int[] ints = frame.getVariables().getInts();
            Object[] objects = frame.getVariables().getObjects();

            int i = ints[0];
            int value = ints[1];
            objects = Arrays.copyOf(objects, 3);
            objects[2] = "Iteration " + i + " and value is divisible by 2: " + (value % 2);

            // Return a new Frame with the the expected method ID and variables
            return frame
                    .withMethodId(-571717800)
                    .withObjectVariables(objects);
        }
);

CoroutineReader reader = new CoroutineReader(new FrameUpdatePoint[] { updater });

byte[] data = Files.readAllBytes(Paths.get(".testfile.tmp"));
CoroutineRunner runner = reader.read(data);

// Execute runner 6 times.
runner.execute();
runner.execute();
runner.execute();
runner.execute();
runner.execute();
runner.execute();
```

Output from deserialization onward...

```
-1690734402 Iteration 3 and value is divisible by 2: 0
-1557280266 Iteration 4 and value is divisible by 2: 0
1327362106 Iteration 5 and value is divisible by 2: 0
-1930858313 Iteration 6 and value is divisible by 2: -1
502539523 Iteration 7 and value is divisible by 2: 1
-1728529858 Iteration 8 and value is divisible by 2: 0
```

Keep in mind that you can chain updaters together. For example, if you have a third version of ```MyCoroutine.echo()``` , you can simply add another ```FrameUpdatePoint``` to ```CoroutineReader``` to convert the second version to the third version. If ```CoroutineReader``` sees the first version, it will automatically convert it to the second version, and then convert that second version to the third version.

#### Example: Supporting old versions  (backward compatability)

In many cases, it may not be enough to just support deserializing from older versions. You may also need to support downgrading to older versions when you serialize, such that both older and newer versions of your coroutine will be able to deserialize.

In the same way that you're able to chain ```FrameUpdatePoint```s in ```CoroutineReader``` to convert to newer versions, you can chain ```FrameUpdatePoint```s in ```CoroutineWriter``` to convert to older versions. The difference is that ```CoroutineWriter``` will write out each conversion in the update chain, allowing ```CoroutineReader``` to pick out the correct version and load it up.

Continuing from the previous example, imagine we start from the initial ```MyCoroutine``` and run it a few times (just like before), but this time we add a ```FrameUpdatePoint``` to ```CoroutineWriter``` so it converts to the modified version of ```MyCoroutine.echo()```...
```java
// Create the coroutine.
Coroutine myCoroutine = new MyCoroutine();
CoroutineRunner runner = new CoroutineRunner(myCoroutine);

// Run it a few times.
runner.execute();
runner.execute();
runner.execute();
runner.execute();

// Checkpoint it, adding a FrameUpdatePoint to also support the
// modified version of MyCoroutine.echo()
FrameUpdatePoint updater = new FrameUpdatePoint(
        "MyCoroutine",// class name to intercept
        1191091979,   // method ID to intercept
        0,            // continuation point to intercept
        (frame, mode) -> {
            int[] ints = frame.getVariables().getInts();
            Object[] objects = frame.getVariables().getObjects();

            int i = ints[0];
            int value = ints[1];
            objects = Arrays.copyOf(objects, 3);
            objects[2] = "Iteration " + i + " and value is divisible by 2: " + (value % 2);

            return frame
                    .withMethodId(-571717800)
                    .withObjectVariables(objects);
        }
);
CoroutineWriter writer = new CoroutineWriter(new FrameUpdatePoint[] { updater });
byte[] data = writer.write(runner);

Files.write(Paths.get(".testfile.tmp"), data);
```

Now, we can use ```CoroutineReader``` deserialize to either version of ```MyCoroutine``` without having to supply it a ```FrameUpdatePoint```. ```CoroutineReader``` will automatically pick out the correct version based on the method ID. The code to deserialize is straight forward...

```java
// Read it back in
CoroutineReader reader = new CoroutineReader();

byte[] data = Files.readAllBytes(Paths.get(".testfile.tmp"));
CoroutineRunner runner = reader.read(data);

// Execute runner 6 times.
runner.execute();
runner.execute();
runner.execute();
runner.execute();
runner.execute();
runner.execute();
```

If we deserialize to the initial ```MyCoroutine```, the output will be...

```
-1690734402
-1557280266
1327362106
-1930858313
502539523
-1728529858
```

If we deserialize to the modified ```MyCoroutine```, the output will be...

```
-1690734402 Iteration 3 and value is divisible by 2: 0
-1557280266 Iteration 4 and value is divisible by 2: 0
1327362106 Iteration 5 and value is divisible by 2: 0
-1930858313 Iteration 6 and value is divisible by 2: -1
502539523 Iteration 7 and value is divisible by 2: 1
-1728529858 Iteration 8 and value is divisible by 2: 0
```

Remember that the system is robust. If for whatever reason ```CoroutineReader``` cannot find a compatible version of ```MyCoroutine.echo()```, it will attempt to use the ```FrameUpdatePoint```s supplied to it to get it loadable before erroring out.

### Common Pitfalls and Best Practices

Special care needs to be taken to avoid common pitfalls with serializing and versioning your coroutines. Ultimately, you're the one responsible for testing your code and making sure it works as intended. Having said that, the sub-sections below detail common pitfalls and best practices.

#### Serialization pitfalls and best practices

Common pitfalls with serialization include...

 * Objects shared between coroutines will end up being duplicated on deserialization. This includes objects passed in via the coroutine context as well as Object constants that have been loaded as variables or operands. Depending on what your code does, this may or may not matter.
 * Objects being replaced during deserialization may be referenced in multiple places. For example, an object being replaced on the operand stack may be referenced in other coroutines as well, meaning those references likely need to be updated to the replaced object.
 * Objects being retained must all be serializable. Generally speaking, low-level resources (locks, files, sockets, etc..) will not be serializable. 

Your simplest and best option for avoiding these serialization pitfalls is to design your coroutine such that you isolate it from shared/global objects, locks, and IO resources. Other potential strategies include...

 * For IO resources, wrap them in such a way that the resource will be serializable. For example, instead of using ```ServerSocket``` directly, you can wrap it in a secondary class that dumps out the port and listening address when serializing and correctly maps it back out when deserializing.
 * For locks, create a custom ```CoroutineWriter.CoroutineSerializer``` implementation that scans and the object graph for known locks and writes out placeholders in their place. A corresponding ```CoroutineReader.CoroutineDeserializer``` implementation will then re-map those placeholders to the correct lock.
 * For shared/global objects, the same strategy for locks can be applied (previous bullet point).

#### Versioning pitfalls and best practices

Common pitfalls with versioning include...

 * Using different compilers (e.g. Oracle Java vs Eclipse Java Compiler) or using different versions of the same compiler (Oracle JDK 1.7 vs Oracle JDK 1.8) on the same code may produce different method IDs -- the ID in the ```.coroutinesinfo``` file may change even though you didn't modify your code. The bytecode generated between different compilers and compiler versions is mostly the same but also slightly different. These slight differences are what make the version change. To reduce headaches, follow the best practice of sticking to the same compiler vendor and version between your builds.
 * If the constants used by a method are changed, the method ID may not change. The problem is that if the constant is loaded as a getstatic instruction instead of a ldc instruction, the value of the constant won't be factored into the method ID. Future versions of the instrumenter may do multiple passes to properly mix in constants into the method ID.

## Configuration Guide

You can configure instrumentation by supplying key/value arguments to the instrumenter. Arguments are passed in differently depending on how you're performing instrumentation. If you're using...

 * Maven, provide configurations as XML tags inside the ```<configuration>``` tag ([Example](#maven-instructions))
 * Ant, provide configurations as XML attributes on the ```<InstrumentationTask>``` tag ([Example](#ant-instructions))
 * Gradle, provide configurations inside the ```coroutines``` block ([Example](#gradle-instructions))
 * Java Agent, provide configurations by appending ```=KEY1=VAL1,KEY2=VAL2,...``` to the ```-javaagent``` argument ([Example](#java-agent-instructions))

The following subsections provide information on the various configuration options.

### Debug Mode

Debug mode adds extra instrumentation logic such that you can always view the state of the methods that make up your coroutine when tracing via a debugger (e.g. the debugger in Netbeans/Eclipse/IntelliJ). By default, the instrumenter tries to keep things efficient by loading only as much as is needed for your coroutines to continue properly executing.

 * Name: ```debugMode```.
 * Value: { ```true``` | ```false``` }.
 * Default: ```false```.

### Auto Serializable

Auto-serializable will automatically force the owning classes of instrumented methods to implement ```java.io.Serializable``` (if it doesn't already do so) and default ```serialVersionUID``` to ```0L``` (if it isn't already set). This is required by the default serializer/deserializer because it uses Java's internal serialization mechanism to write out and read in your coroutines. Without ```java.io.Serializable```, you'll get a ```java.io.NotSerializableException``` when serializing your coroutine. Without a hardcoded ```serialVersionUID```, any change to the owning class will cause deserialization of previous versions to fail. 

This is provided as a convenience for the user. It does nothing to ensure that the variables, operands, or object state of the class being serialized is serializable. You can turn this feature off if you aren't using serialization, you're using a custom serializer, or you're supplying ```java.io.Serializable``` and ```serialVersionUID``` manually.

 * Name: ```autoSerializable```.
 * Value: { ```true``` | ```false``` }.
 * Default: ```true```.

### Marker Type

Marker type adds extra logic to track and output what the instrumenter added to your methods. This provides core information for debugging problems with the instrumenter -- it provides little to no value for you as a user.

 * Name: ```markerType```.
 * Value: { ```NONE``` | ```CONST``` | ```STDOUT``` }.
 * Default: ```NONE```.

## FAQ

#### How much overhead am I adding?

Instrumentation adds loading and saving code to each method that's intended to run as part of a coroutine, so your class files will become larger and that extra code will take time to execute.

As of version 1.2.0, the instrumenter generates much more efficient suspend/resume logic.

#### What projects make use of Coroutines?

| Project | Description |
|---------|-------------|
| [Actors](https://github.com/offbynull/actors) | The Coroutines project was made for Actors (formerly called Peernetic). Actors is a Java actor framework that greatly simplifies the design and development of horizontally scalable software. |
| [Towards Resilient Java Computational Programs](https://hal.archives-ouvertes.fr/hal-01316493/document) | Towards Resilient Java Computational Programs<br>The 46th Annual IEEE/IFIP International Conference on Dependable Systems and Networks<br>Jun 2016, Toulouse, France<br>Authors: Quyen L. Nguyen, Dr. Arun K. Sood |

If you know of any other projects please let me know and I'll update this section.

#### What restrictions are there?

##### Reflection API

Your coroutine won't get properly instrumented if any part of your invocation chain is done through Java's reflection API. The example below uses Java's reflection API to invoke echo. The instrumentation logic isn't able to recognize that reflections are being used to call echo and as such it will not instrument around the call to load and save the execution state of the method.

```java
public static final class MyCoroutine implements Coroutine {
    @Override
    public void run(Continuation c) {
        System.out.println("started");
        for (int i = 0; i < 10; i++) {
            // THIS WILL NOT BE INSTRUMENTED PROPERLY
            Method method = getClass().getDeclaredMethod(methodName);
            method.setAccessible(true);
            method.invoke(this, c, i);
        }
    }

    private void echo(Continuation c, int x) {
        System.out.println(x);
        c.suspend();
    }
}
```


##### Lambdas and INVOKEDYNAMIC

Instrumentation will fail if it detects that you're passing the Continuation object in to a lambda (or any INVOKEDYNAMIC instruction).

tl;dr: If you make use of a Continuation object in a lambda, it's equivalent to converting that lambda to a class and setting the Continuation object as a field in that class. Remember that you must always pass in a Continuation object as an argument to a method that's explicitly expecting it -- that's now the instrumentation logic figures out where to add extra code to save and load the execution state.

So ...

```java
    public void run(Continuation c) {
        for (int i = 0; i < 10; i++) {
            Consumer<Integer> consumer = (x) -> {
                c.suspend();
            }
            consumer.accept(i);
        }
    }
```

would be equivalent to

```java
    public void run(Continuation c) {
        for (int i = 0; i < 10; i++) {
            Consumer<Integer> consumer = new CustomConsumer(c);
            consumer.accept(i);
        }
    }
    
    private static final class CustomConsumer implements Consumer<Integer> {
        private final Continuation c;
        
        public CustomConsumer(Continuation c) {
            this.c = c;
        }
        
        public void accept(Integer o) {
            c.suspend();    // WILL NOT WORK FOR REASONS DESCRIBED ABOVE.
        }
    }
```

A more in depth explanation on why this happens is available in the code. Replicated here:
```
Why is invokedynamic not allowed? because apparently invokedynamic can map to anything... which means that we can't reliably
determine if what is being called by invokedynamic is going to be a method we expect to be instrumented to handle Continuations.

In Java8, this is the case for lambdas. Lambdas get translated to invokedynamic calls when they're created. Take the following
Java code as an example...

public void run(Continuation c) {
    String temp = "hi";
    builder.append("started\n");
    for (int i = 0; i < 10; i++) {
        Consumer<Integer> consumer = (x) -> {
            temp.length(); // pulls in temp as an arg, which causes c (the Continuation object) to go in as a the second argument
            builder.append(x).append('\n');
            System.out.println("XXXXXXX");
            c.suspend();
        }
        consumer.accept(i);
    }
}

This for loop in the above code maps out to...
   L5
    LINENUMBER 18 L5
    ALOAD 0: this
    ALOAD 2: temp
    ALOAD 1: c
    INVOKEDYNAMIC accept(LambdaInvokeTest, String, Continuation) : Consumer [
      // handle kind 0x6 : INVOKESTATIC
      LambdaMetafactory.metafactory(MethodHandles$Lookup, String, MethodType, MethodType, MethodHandle, MethodType) : CallSite
      // arguments:
      (Object) : void, 
      // handle kind 0x7 : INVOKESPECIAL
      LambdaInvokeTest.lambda$0(String, Continuation, Integer) : void, 
      (Integer) : void
    ]
    ASTORE 4
   L6
    LINENUMBER 24 L6
    ALOAD 4: consumer
    ILOAD 3: i
    INVOKESTATIC Integer.valueOf (int) : Integer
    INVOKEINTERFACE Consumer.accept (Object) : void
   L7
    LINENUMBER 17 L7
    IINC 3: i 1
   L4
    ILOAD 3: i
    BIPUSH 10
    IF_ICMPLT L5

Even though the invokedynamic instruction is calling a method called "accept", it doesn't actually call Consumer.accept().
Instead it just creates the Consumer object that accept() is eventually called on. This means that it makes no sense to add
instrumentation around invokedynamic because it isn't calling what we expected it to call. When accept() does eventually get
called, it doesn't take in a Continuation object as a parameter so instrumentation won't be added in around it.

There's no way to reliably instrument around the accept() method because we don't know if an accept() invocation will be to a
Consumer that we've instrumented.

The instrumenter identifies which methods to instrument and which method invocations to instrument by checking to see if they
explicitly take in a Continuation as a parameter. Using lambdas like this is essentially like creating an implementation of
Consumer as a class and setting the Continuation object as a field in that class. Cases like that cannot be reliably
identified for instrumentation.
```

#### Can I use this with an IDE?

If your IDE delegates to one of the supported build systems (Maven/Gradle/Ant), you can use this with your IDE. In some cases, your IDE may try to optimize by prematurely compiling classes internally, skipping any instrumentation that should be taking place as a part of your build. You'll have to turn this feature off.

For example, if you're using Maven through Netbeans, you must turn off the "Compile On Save" feature that's enabled by default. Otherwise, as soon as you make a change to your coroutine and save, Netbeans will compile your Java file without instrumentation. IntelliJ and Eclipse probably have similar options available. Unfortunately I don't have much experience with those IDEs (... if someone does please let me know and I'll update this section).

#### What alternatives are available?

Alternatives to the Coroutines project include:

* [Tascalate-Javaflow](https://github.com/vsilaev/tascalate-javaflow/)
* [Javaflow](http://commons.apache.org/sandbox/commons-javaflow/)
* [Coroutines](https://code.google.com/p/coroutines/)
* [Continuations Library](http://www.matthiasmann.de/content/view/24/26/)
* [Kilim](https://github.com/kilim/kilim)

If you know of any other projects please let me know and I'll update this section.

## Change Log

### [1.5.3] - 2019-06-19
- FIXED: MethodHasher improper handling of TableSwitch (see issue #88 -- thank you to @blackhzj).

### [1.5.2] - 2018-08-25
- FIXED: Improper handling of interface types (see issue #84 -- thank you to @little-pan).

### [1.5.1] - 2018-08-16
- FIXED: SerializationDetailer doesn't handle array types (see issue #85 -- thank you to @little-pan).

### [1.5.0] - 2018-06-09
- CHANGED: Changed required JDK version from 8 to 10.
- CHANGED: Changed minimum JRE version from 1.4 to 1.6.
- CHANGED: Upgraded dependencies and plugins.
- CHANGED: Unit tests upgraded to Junit 5.

### [1.4.0] - 2017-11-20
- CHANGED: Refactored to multi-pass instrumentation architecture.
- CHANGED: Serialization support now separate instrumentation passes.
- ADDED: Serialization backwards compatibility.
- ADDED: Instrumentation pass to force Coroutine classes to implement Serializable.
- ADDED: Increased serialization/versioning test coverage.
- CHANGED: Serialization classes made immutable.
- CHANGED: Decoupled serialized frame interception from frame updates.
- CHANGED: Decoupled construction from serialization.
- CHANGED: Combined serialization method ID and method version.
- CHANGED: Made deserialization to unrecognized method IDs illegal.
- CHANGED: Serialization class field identifiers now include continuation point IDs.
- CHANGED: Coroutine extends Serializable.
- CHANGED: Changed format of Java Agent config arguments.

### [1.3.0] - 2017-11-15
- ADDED: Serialization and versioning support.
- CHANGED: Upgraded dependencies and plugins.

### [1.2.3] - 2017-03-05
- FIXED: Avoid instrumenting core coroutines classes / Java bootstrap classes in Java Agent (see issue #77).
- FIXED: Avoid loading classes in Java Agent (see issue #77).

### [1.2.2] - 2017-01-08
- ADDED: Gradle plugin.
- REMOVED: Gradle instructions.
- CHANGED: Upgraded dependencies and plugins.

### [1.2.1] - 2016-11-19
- ADDED: Gradle instructions.
- ADDED: Java Agent for bytecode instrumenation at runtime.

### [1.2.0] - 2016-09-18
- CHANGED: Performance improvement: Deferred operand stack and local variable table saving until Coroutine suspended.
- CHANGED: Performance improvement: No longer autoboxing when storing/loading operand stack and local variable table.
- CHANGED: Performance improvement: No longer autoboxing when caching return value of continuation points.
- CHANGED: Performance improvement: Operand stack reloads after a save only if it's needed.
- CHANGED: Performance improvement: Casting of operand stack items only if it's needed.
- CHANGED: Performance improvement: Pushing/popping of method states only if it's needed.
- CHANGED: Refactored instrumentation logic.
- CHANGED: Upgraded dependencies and plugins.
- CHANGED: Class instrumentation marker changed from an interface to a public constant (constant value defines compatibility).
- ADDED: Debug markers/logging in instrumented code (must explicitly be enabled).
- ADDED: Increased test coverage.

### [1.1.1] - 2015-08-08
- CHANGED: Upgraded to dependencies and plugins
- FIXED: Incorrect Javadoc comment for CoroutineRunner.execute()
- FIXED: Override of SimpleVerifier.isAssignableFrom(Type t, Type u) unable to deal with arrays

### [1.1.0] - 2015-04-24
- CHANGED: Performance improvement: Deferred operand stack and local variable table loading. As a by product, code had to be refactored to be more modular / maintainable.
- FIXED: Missing serialization UID in CoroutineException.
- CHANGED: Upgraded all serialization UIDs due to removal of internal removePending() method.

### [1.0.4] - 2015-04-20
- FIXED: Proper handling of caught exceptions -- pending method states are now being properly rolled back on exception
- FIXED: Incorrect attempt to convert long to Double saving local variable table
- FIXED: Gracefully ignores when continuation point doesn't invoke other continuation points
- ADDED: Increased test coverage.

### [1.0.3] - 2015-04-16
- FIXED: Fixed typo in Maven plugin exception message
- FIXED: Proper handling of type Lnull; in local variable table and operand stack saving/loading code
- ADDED: Added CoroutineRunner.getCoroutine()
- ADDED: Increased test coverage.

### [1.0.2] - 2015-04-06
- FIXED: Maven plugin test-instrument goal would crash if test source folder did not exist.
- ADDED: Increased test coverage.
- ADDED: Made relevant classes in user module implement Serializable. Now if you really wanted to, you can serialize a CoroutineRunner.

### [1.0.1] - 2015-03-26
- FIXED: Incorrectly identified any 0 parameter method as a call to suspend().

### [1.0.0] - 2015-03-24
- Initial release.

## Footnotes
1. Javaflow has a reliance on thread local storage and other threading constructs. The Coroutines project avoids anything to do with threads. A quick benchmark performing 10,000,000 iterations of Javaflow's echo sample vs this project's echo example (System.out's removed in both) resulted in Javaflow executing in 46,518ms while Coroutines executed in 19,141ms. Setup used for this benchmark was an Intel i7 960 CPU with 12GB of RAM running Windows 7 and Java 8. _Update: These numbers were for version 1.0.x. As of 1.2.0, the performance has improved even further._
2. Javaflow only provides an Ant plugin.
3. Javaflow does not provide a Java agent.
4. Javaflow has [issues](https://issues.apache.org/jira/browse/SANDBOX-476?page=com.atlassian.jira.plugin.system.issuetabpanels:comment-tabpanel&focusedCommentId=14133339#comment-14133339) dealing with stackmap frames due to it's reliance on ASM's default behaviour for deriving common superclasses. The Coroutines project works around this behaviour by implementing custom logic.
5. Javaflow attempts to use static analysis to determine which monitors need to be exitted and reentered, which may not be valid in certain cases (e.g. if your class file was built with a JVM language other than Java). The Coroutines project keeps track of monitors at runtime.
6. Javaflow does not provide support for serialization, versioning, forking, or otherwise accessing local variables and operand items for a frame.
7. Javaflow's code is difficult to follow and everything is embedded in to a single project.
