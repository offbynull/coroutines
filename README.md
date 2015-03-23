# Coroutines

Inspired by the Apache Commons Javaflow project, the Coroutines project is a Java toolkit that allows you to suspend the execution of your method, save its state, and resume executing it from that state at a later point in time.

Why not just use Javaflow? Unfortunately, Javaflow seems to be largely unsupported at this point. But, aside from that, the Coroutines project ...

1. **is roughly 25% to 50% faster than Javaflow** _(Javaflow has a reliance on thread local storage and other threading constructs)_.
1. **has both a Maven plugin and an Ant plugin** _(Javaflow only provides an Ant plugin)_.
1. **has support for Java 8 bytecode** _(Javaflow has issues dealing with stackmap frames due to it's reliance on ASM's default behaviour for deriving common superclasses)_.
1. **has proper support for synchronized blocks** _(Javaflow attempts to use static analysis to determine which monitors need to be exitted and reentered, which may not be valid in certain cases)_.
1. **is modular and the code is readable, well commented, and well tested code** _(Javaflow is difficult to follow and everything is embedded in to a single project)_.

More information on the topic of coroutines can be found at ...

* [Wikipedia: Coroutine](http://en.wikipedia.org/wiki/Coroutine)
* [Stackoverflow: Difference between a "coroutine" and a "thread"?](http://stackoverflow.com/a/23436125)

## How do you use it?

### A simple example

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



This is what your output looks like...
```
started
0
1
2
3
```



### Running Maven as your build system?

First, make use of the following dependency...
```xml
<dependency>
    <groupId>com.offbynull.coroutines</groupId>
    <artifactId>user</artifactId>
    <version>1.0.0</version>
</dependency>
```

Then, make use of the following plugin...
```xml
<plugin>
    <groupId>com.offbynull.coroutines</groupId>
    <artifactId>maven-plugin</artifactId>
    <version>1.0.0</version>
    <executions>
        <execution>
            <goals>
                <goal>instrument</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

### Running Ant as your build system?

Just define the task and bind to the target of your choice...
```xml
<taskdef name="InstrumentTask" classname="com.offbynull.coroutines.anttask.InstrumentTask">
    <classpath>
        <pathelement location="ant-task-1.0.0-shaded.jar"/>
    </classpath>
</taskdef>

<target name="-post-compile">
    <InstrumentTask classpath="" sourceDirectory="build" targetDirectory="build"/>
</target>
```

### Running Gradle as your build system?

Unfortunately there isn't a Gradle plugin currently available. However, Gradle does allow calling Ant tasks. As such, you could make use of the provided Ant task in Gradle.

