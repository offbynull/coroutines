# Coroutines

<p align="center"><img src ="logo.png" alt="Coroutines logo" /></p>

Inspired by the [Apache Commons Javaflow](http://commons.apache.org/sandbox/commons-javaflow/) project, the Coroutines project is a Java toolkit that allows you to write coroutines in Java. Coroutines allows you to suspend the execution your Java method at will, save its state, and resume executing it from that saved state at a later point in time.

Why use Coroutines over Javaflow? The Couroutines project is a new Java coroutines implementation written from scratch that aims to solve some of the issues that Javaflow has. The Coroutines project provides several distinct advantages:

* Saves and loads method state faster than Javaflow [<sub>[Footnote 1]</sub>](#footnotes)
* Provides Maven, Ant, and Gradle plugins [<sub>[Footnote 2]</sub>](#footnotes)
* Provides a Java Agent [<sub>[Footnote 3]</sub>](#footnotes)
* Proper support for Java 8 bytecode [<sub>[Footnote 4]</sub>](#footnotes)
* Proper support for synchronized blocks [<sub>[Footnote 5]</sub>](#footnotes)
* Modular project structure and the code is readable, tested, and well commented [<sub>[Footnote 6]</sub>](#footnotes)

In addition, Javaflow appears to be largely unmaintained at present.

More information on the topic of coroutines and their advantages can be found on the following pages:

* [Wikipedia: Coroutine](http://en.wikipedia.org/wiki/Coroutine)
* [Stackoverflow: Difference between a "coroutine" and a "thread"?](http://stackoverflow.com/a/23436125)

## Table of Contents

 * [Quick-start Guide](#quick-start-guide)
   * [Maven Instructions](#maven-instructions)
   * [Ant Instructions](#ant-instructions)
   * [Gradle Instructions](#gradle-instructions)
   * [Java Agent Instructions](#java-agent-instructions)
   * [Code Example](#code-example)
 * [FAQ](#faq)
   * [How much overhead am I adding?](#how-much-overhead-am-i-adding)
   * [What projects make use of Coroutines?](#what-projects-make-use-of-coroutines)
   * [What restrictions are there?](#what-restrictions-are-there)
   * [Can I use this with an IDE?](#can-i-use-this-with-an-ide)
   * [Can I serialize/deserialize my Coroutine?](#can-i-serializedeserialize-my-coroutine)
   * [How do I use the Java Agent?](#how-do-i-use-the-java-agent)
   * [What alternatives are available?](#what-alternatives-are-available)
 * [Change Log](#change-log)
 * [Footnotes](#footnotes)

## Quick-start Guide

The Coroutines project relies on bytecode instrumentation to make your coroutines work. Maven, Ant, and Gradle plugins are provided to instrument your code. In addition to these plugins, a Java Agent is provided to instrument your code at runtime. Although your code can target any version of Java from Java 1.4 to Java 8, the plugins and Java Agent that instrument your code will require Java 8 to run.

### Maven Instructions

In your POM...

First, add the "user" module as a dependency.
```xml
<dependency>
    <groupId>com.offbynull.coroutines</groupId>
    <artifactId>user</artifactId>
    <version>1.2.3</version>
</dependency>
```

Then, add the Maven plugin so that your classes get instrumented when you build.
```xml
<plugin>
    <groupId>com.offbynull.coroutines</groupId>
    <artifactId>maven-plugin</artifactId>
    <version>1.2.3</version>
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

First, define the Ant Task. It's available for download from [Maven Central](https://repo1.maven.org/maven2/com/offbynull/coroutines/ant-plugin/1.2.3/ant-plugin-1.2.3-shaded.jar).
```xml
<taskdef name="InstrumentTask" classname="com.offbynull.coroutines.anttask.InstrumentTask">
    <classpath>
        <pathelement location="ant-task-1.2.3-shaded.jar"/>
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

You'll also need to include the "user" module's JAR in your classpath as a part of your build. It's also available for download from [Maven Central](https://repo1.maven.org/maven2/com/offbynull/coroutines/user/1.2.3/user-1.2.3.jar).

### Gradle Instructions

In your build script...

First, instruct Gradle to pull the coroutines plugin from Maven central...

```groovy
buildscript {
    repositories {
        mavenCentral()
    }

    dependencies {
        classpath group: 'com.offbynull.coroutines',  name: 'gradle-plugin',  version: '1.2.3'
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
    compile group: 'com.offbynull.coroutines', name: 'user', version: '1.2.3'
}
```

### Java Agent Instructions

The Coroutines Java Agent allows you to instrument your coroutines at runtime instead of build-time. That means that the bytecode instrumentation required to make your coroutines work happens when your application runs instead of when your application gets compiled.

To use the Java Agent, download it from [Maven Central](https://repo1.maven.org/maven2/com/offbynull/coroutines/java-agent/1.2.3/java-agent-1.2.3-shaded.jar) and apply it when you run your Java program...

```shell
java -javaagent:java-agent-1.2.3-shaded.jar myapp.jar

# Set the debug mode to true if you'll be stepping through your coroutines in
# an IDE. You can enable debug mode via Java Agent arguments
#
# -javaagent:java-agent-1.2.3-shaded.jar=NONE,true
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

Any method that takes in a **Continuation** type as a parameter will be instrumented by the plugin to work as part of a coroutine. The entry-point for your coroutine must implement the **Coroutine** interface. **CoroutineRunner.execute()** is used to start / resume execution of your coroutine, while **Continuation.suspend()** suspends the execution of your coroutine.

**:warning: THINGS TO AVOID :warning:**

1. The **Continuation** object is not meant to be retained. Never set it to a field or otherwise pass it to a method that isn't intended to run as part of a coroutine.
1. The only methods on **Continuation** that you should be calling are **suspend()**, **getContext()**, and **setContext()**. All other methods are for internal use only.

## FAQ

#### How much overhead am I adding?

It depends. Instrumentation adds loading and saving code to each method that's intended to run as part of a coroutine, so your class files will become larger and that extra code will take time to execute. I personally haven't noticed any drastic slowdowns in my own projects.

Version 1.2.0 of the instrumenter generates much more efficient suspend/resume logic.

#### What projects make use of Coroutines?

| Project | Description |
|---------|-------------|
| [Peernetic](https://github.com/offbynull/peernetic) | The Coroutines project was originally made for use in (and is heavily used by) the Peernetic project. Peernetic is a Java actor-based P2P computing framework specifically designed to facilitate development and testing of distributed and P2P algorithms. The use of Coroutines makes actor logic easily understandable/readable. See the Javadoc header in [this file](https://github.com/offbynull/peernetic/blob/2143d4b208d1107a933e06868e53811a2c7608c4/core/src/main/java/com/offbynull/peernetic/core/actor/CoroutineActor.java) for an overview of why this is. |
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

A more indepth explanation on why this happens is available in the code. Replicated here:
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

If your IDE delegates to Maven or Ant, you can use this with your IDE. In some cases, your IDE may try to optimize by prematurely compiling classes internally, skipping any instrumentation that should be taking place as a part of your build. You'll have to turn this feature off.

For example, if you're using Maven through Netbeans, you must turn off the "Compile On Save" feature that's enabled by default. Otherwise, as soon as you make a change to your coroutine and save, Netbeans will compile your Java file without instrumentation. IntelliJ and Eclipse probably have similar options available. Unfortunately I don't have much experience with those IDEs (... if someone does please let me know and I'll update this section).

#### Can I serialize/deserialize my Coroutine?

Technically possible, but highly not recommended. Why? The issue is that you don't really know what's on the operand stack/local variables table.

1. If you're doing any kind of IO at any point at all in your Coroutine (writing to System.out, a file, a socket, etc..), it'll likely either fail to serialize properly or deserialize to an inconsistent state.
1. If you recompile your class using a different version of the JDK than the one you originally used (even without any code changes), the instructions that make up the method may change, and you'll likely fail to continue execution after you deserialize.
1. Objects on the operand stack / local variable table will be recreated when you deserialize. That means that in certain cases you may be performing an operation on/with the wrong object. For example, in certain cases reference equality tests (== operator) on an object may fail after serialization. Imagine the following scenario...

```java
public static final class MyCoroutine implements Coroutine, Serializable {
    private static final Object OBJECT = new Object();
    @Override
    public void run(Continuation c) {
        Object testObj = OBJECT;
        c.suspend(); // SERIALIZE AT THIS SUSPEND, THEN DESERIALIZE AND CONTINUE
        System.out.println(testObj == OBJECT);
    }
}
```

If you run the coroutine, serialize it after suspend(), then deserialize it and continue running it from the deserialized version, the code above will print out false. Why? testObj is recreated on deserialization, meaning that it's no longer referring to the same object that's in the static final field. The objects may be equal based on value (the equals() method may return true), but they're referring to different objects after deserialization.

There are likely other reasons as well. Deserialization issues may cause subtle problems that aren't always obvious. It's best to avoid serializing coroutines unless you're absolutely sure you know what you're doing.

#### What alternatives are available?

Alternatives to the Coroutines project include:

* [Tascalate-Javaflow](https://github.com/vsilaev/tascalate-javaflow/)
* [Javaflow](http://commons.apache.org/sandbox/commons-javaflow/)
* [Coroutines](https://code.google.com/p/coroutines/)
* [Continuations Library](http://www.matthiasmann.de/content/view/24/26/)
* [Kilim](https://github.com/kilim/kilim)

If you know of any other projects please let me know and I'll update this section.

## Change Log
<sub>Template adapted from http://keepachangelog.com/</sub>

All notable changes to this project will be documented in this file.
This project adheres to [Semantic Versioning](http://semver.org/).

### [Unreleased][unreleased]

### [1.2.3] - 2017-03-05
- FIXED: Avoid instrumenting core coroutines classes / Java bootstrap classes in Java Agent (see issue #77).
- FIXED: Avoid loading classes in Java Agent (see issue #77).

### [1.2.2] - 2017-01-08
- ADDED: Gradle plugin.
- REMOVED: Gradle instructions.
- CHANGED: Upgraded dependencies and plugins

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
1. Javaflow has a reliance on thread local storage and other threading constructs. The Coroutines project avoids anything to do with threads. A quick benchmark performing 10,000,000 iterations of Javaflow's echo sample vs this project's echo example (System.out's removed in both) resulted in Javaflow executing in 46,518ms while Coroutines executed in 19,141ms. Setup used for this benchmark was an Intel i7 960 CPU with 12GB of RAM running Windows 7 and Java 8.
2. Javaflow only provides an Ant plugin.
3. Javaflow does not provide a Java agent.
4. Javaflow has [issues](https://issues.apache.org/jira/browse/SANDBOX-476?page=com.atlassian.jira.plugin.system.issuetabpanels:comment-tabpanel&focusedCommentId=14133339#comment-14133339) dealing with stackmap frames due to it's reliance on ASM's default behaviour for deriving common superclasses. The Coroutines project works around this behaviour by implementing custom logic.
5. Javaflow attempts to use static analysis to determine which monitors need to be exitted and reentered, which may not be valid in certain cases (e.g. if your class file was built with a JVM language other than Java). The Coroutines project keeps track of monitors at runtime.
6. Javaflow's code is difficult to follow and everything is embedded in to a single project.