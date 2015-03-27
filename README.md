# Coroutines

Inspired by the [Apache Commons Javaflow](http://commons.apache.org/sandbox/commons-javaflow/) project, the Coroutines project is a Java toolkit that allows you to write coroutines in Java. Coroutines allows you to suspend the execution your Java method at will, save its state, and resume executing it from that saved state at a later point in time.

Why use Coroutines over Javaflow? The Couroutines project is a new Java coroutines implementation written from scratch that aims to solve some of the issues that Javaflow has. The Coroutines project provides several distinct advantages:

* Roughly 25% to 50% faster than Javaflow <sub>1</sub>
* Provides both a Maven plugin and an Ant plugin <sub>2</sub>
* Proper support for Java 8 bytecode <sub>3</sub>
* Proper support for synchronized blocks <sub>4</sub>
* Modular project structure and the code is readable, tested, and well commented <sub>5</sub>

In addition, Javaflow appears to be largely unmaintained at present.

More information on the topic of coroutines and their advantages can be found on the following pages:

* [Wikipedia: Coroutine](http://en.wikipedia.org/wiki/Coroutine)
* [Stackoverflow: Difference between a "coroutine" and a "thread"?](http://stackoverflow.com/a/23436125)

## Example

### Setup

The project relies on bytecode instrumentation to make your coroutines work. Both Maven and Ant plugins are provided to instrument your code. Although your code can target any version of Java from Java 1.4 to Java 8, the Ant and Maven plugins that instrument your code will require Java 8 to run.

**Maven Instructions**

In your POM...

First, add the "user" module as a dependency.
```xml
<dependency>
    <groupId>com.offbynull.coroutines</groupId>
    <artifactId>user</artifactId>
    <version>1.0.1</version>
</dependency>
```

Then, add the Maven plugin so that your classes get instrumented when you build.
```xml
<plugin>
    <groupId>com.offbynull.coroutines</groupId>
    <artifactId>maven-plugin</artifactId>
    <version>1.0.1</version>
    <executions>
        <execution>
            <goals>
                <goal>instrument</goal>
                <goal>test-instrument</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

**Ant Instructions**

In your build script...

First, define the Ant Task. It's available for download from [Maven Central](https://repo1.maven.org/maven2/com/offbynull/coroutines/ant-plugin/1.0.1/ant-plugin-1.0.1-shaded.jar).
```xml
<taskdef name="InstrumentTask" classname="com.offbynull.coroutines.anttask.InstrumentTask">
    <classpath>
        <pathelement location="ant-task-1.0.1-shaded.jar"/>
    </classpath>
</taskdef>
```

Then, bind it to the target of your choice.
```xml
<target name="-post-compile">
    <!-- The classpath attribute is a semicolon delimited list of the classpath required by your code. -->
    <InstrumentTask classpath="" sourceDirectory="build" targetDirectory="build"/>
</target>
```

You'll also need to include the "user" module's JAR in your classpath as a part of your build. It's also available for download from [Maven Central](https://repo1.maven.org/maven2/com/offbynull/coroutines/user/1.0.1/user-1.0.1.jar).

### Code

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


### Explanation

The entry-point for your coroutine must implement the *Coroutine* interface.

* *CoroutineRunner.execute()* starts or resumes execution of your coroutine.
* *Continuation.suspend()* suspends the execution of your coroutine.
* Any method that takes in a *Continuation* type as a parameter will be instrumented by the plugin to work as part of a coroutine. 

Aside from that, some important things to be aware of:

* The *Continuation* object is not meant to be retained. Never set it to a field or otherwise pass it to methods that aren't intended to run as part of a coroutine.
* The only methods on *Continuation* that you should be calling are *suspend()*, *getContext()*, and *setContext()*. All other methods are for internal use only.


##Footnotes
1. Javaflow has a reliance on thread local storage and other threading constructs. The Coroutines project avoids anything to do with threads.
2. Javaflow only provides an Ant plugin.
3. Javaflow has [issues](https://issues.apache.org/jira/browse/SANDBOX-476?page=com.atlassian.jira.plugin.system.issuetabpanels:comment-tabpanel&focusedCommentId=14133339#comment-14133339) dealing with stackmap frames due to it's reliance on ASM's default behaviour for deriving common superclasses. The Coroutines project works around this behaviour by implementing custom logic.
4. Javaflow attempts to use static analysis to determine which monitors need to be exitted and reentered, which may not be valid in certain cases (e.g. if your class file was built with a JVM language other than Java). The Coroutines project keeps track of monitors at runtime.
5. Javaflow's code is difficult to follow and everything is embedded in to a single project.