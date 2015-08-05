# Coroutines

Inspired by the [Apache Commons Javaflow](http://commons.apache.org/sandbox/commons-javaflow/) project, the Coroutines project is a Java toolkit that allows you to write coroutines in Java. Coroutines allows you to suspend the execution your Java method at will, save its state, and resume executing it from that saved state at a later point in time.

Why use Coroutines over Javaflow? The Couroutines project is a new Java coroutines implementation written from scratch that aims to solve some of the issues that Javaflow has. The Coroutines project provides several distinct advantages:

* Saves and loads method state faster than Javaflow <sub>[1]</sub>
* Provides both a Maven plugin and an Ant plugin <sub>[2]</sub>
* Proper support for Java 8 bytecode <sub>[3]</sub>
* Proper support for synchronized blocks <sub>[4]</sub>
* Modular project structure and the code is readable, tested, and well commented <sub>[5]</sub>

In addition, Javaflow appears to be largely unmaintained at present.

More information on the topic of coroutines and their advantages can be found on the following pages:

* [Wikipedia: Coroutine](http://en.wikipedia.org/wiki/Coroutine)
* [Stackoverflow: Difference between a "coroutine" and a "thread"?](http://stackoverflow.com/a/23436125)

## Example

### Setup

The Coroutines project relies on bytecode instrumentation to make your coroutines work. Both Maven and Ant plugins are provided to instrument your code. Although your code can target any version of Java from Java 1.4 to Java 8, the Ant and Maven plugins that instrument your code will require Java 8 to run.

**Maven Instructions**

In your POM...

First, add the "user" module as a dependency.
```xml
<dependency>
    <groupId>com.offbynull.coroutines</groupId>
    <artifactId>user</artifactId>
    <version>1.1.0</version>
</dependency>
```

Then, add the Maven plugin so that your classes get instrumented when you build.
```xml
<plugin>
    <groupId>com.offbynull.coroutines</groupId>
    <artifactId>maven-plugin</artifactId>
    <version>1.1.0</version>
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

First, define the Ant Task. It's available for download from [Maven Central](https://repo1.maven.org/maven2/com/offbynull/coroutines/ant-plugin/1.1.0/ant-plugin-1.1.0-shaded.jar).
```xml
<taskdef name="InstrumentTask" classname="com.offbynull.coroutines.anttask.InstrumentTask">
    <classpath>
        <pathelement location="ant-task-1.1.0-shaded.jar"/>
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

You'll also need to include the "user" module's JAR in your classpath as a part of your build. It's also available for download from [Maven Central](https://repo1.maven.org/maven2/com/offbynull/coroutines/user/1.1.0/user-1.1.0.jar).

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

    // IMPORTANT: Methods that are intended to be run as part of a coroutine must take in a Continuation type as a parameter. Otherwise, the
    // plugin will fail to instrument the method.
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


## FAQ

#### How much overhead am I adding?

It depends. Instrumentation adds loading and saving code to each method that's intended to run as part of a coroutine, so your class files will become larger and that extra code will take time to execute. I personally haven't noticed any drastic slowdowns in my own projects, but be aware that highly recursive coroutines / heavy call depths may end up consuming a lot of resources thereby causing noticeable performance loss.

#### What projects make use of Coroutines?

The Coroutines project was originally made for use in (and is heavily used by) the [Peernetic](https://github.com/offbynull/peernetic) project. Peernetic is a Java actor-based P2P computing framework specifically designed to facilitate development and testing of distributed and P2P algorithms. The use of Coroutines makes actor logic easily understandable/readable. See the Javadoc header in [this file](https://github.com/offbynull/peernetic/blob/2143d4b208d1107a933e06868e53811a2c7608c4/core/src/main/java/com/offbynull/peernetic/core/actor/CoroutineActor.java) for an overview of why this is.

#### Why not use annotations?

This question was originally asked by @MrElusive...

> I wanted to hide the Continuation parameter so that clients would not need to worry about passing it around.
> 
> <snip>
>
> Out of curiosity, would it not be possible to annotate stack-saveable methods using something like @Continuable?"

There are a couple of issues with using annotations ...

1. First is that versions of Java that don't support annotations won't work anymore. This includes anything below 1.5.
1. Second (most important) is that you introduce ambiguity by not passing in the Continuation object as a parameter. Passing in the Continuation object as a parameter acts as both a marker (equivalent to an annotation) and a guarantee that the Continuation object you're using is the one you want (it gets passed down the stack with the invocation chain). If instead of passing as a parameter, you were to use a Continuation object taken from some external source (e.g. a field or the return value of a method), there's more of a chance that you'll end up calling suspend on the wrong Continuation object / calling suspend on the correct Continuation object but at the wrong time or in the wrong state.

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

#### Is there a Gradle plugin?

A Gradle plugin is on the backburner. In the mean time, Gradle users can make use of the Ant plugin through [Gradle's Ant integration](http://gradle.org/docs/current/userguide/ant.html). The major issue here is that the Gradle plugin APIs aren't made available on Maven Central. From Maven's [Guide to uploading artifacts to the Central Repository](http://maven.apache.org/guides/mini/guide-central-repository-upload.html):

>I have other repositories or pluginRepositories listed in my POM, is that a problem?
>
>At present, this won't preclude your project from being included, but we do strongly encourage making sure all your dependencies are included in Central. If you rely on sketchy repositories that have junk in them or disappear, it just creates havok for downstream users. Try to keep your dependencies among reliable repos like Central, Jboss, etc.

#### What alternatives are available?

Alternatives to the Coroutines project include:

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
- FIXED: Upgraded to ASM 5.0.4
- FIXED: Incorrect Javadoc comment for CoroutineRunner.execute()
- FIXED: Override of SimpleVerifier.isAssignableFrom(Type t, Type u) unable to deal with arrays
- CHANGED: Abstracted ClassInformationRepository in preparation for Java9

### [1.1.0] - 2015-04-24
- ADDED: Major performance improvement: Deferred operand stack and local variable table loading. As a by product, code had to be refactored to be more modular / maintainable.
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
3. Javaflow has [issues](https://issues.apache.org/jira/browse/SANDBOX-476?page=com.atlassian.jira.plugin.system.issuetabpanels:comment-tabpanel&focusedCommentId=14133339#comment-14133339) dealing with stackmap frames due to it's reliance on ASM's default behaviour for deriving common superclasses. The Coroutines project works around this behaviour by implementing custom logic.
4. Javaflow attempts to use static analysis to determine which monitors need to be exitted and reentered, which may not be valid in certain cases (e.g. if your class file was built with a JVM language other than Java). The Coroutines project keeps track of monitors at runtime.
5. Javaflow's code is difficult to follow and everything is embedded in to a single project.