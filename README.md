# coroutines

Inspired by the Apache Commons Javaflow project, The Coroutines project is a Java toolkit that allows users to suspend the execution of their method, save that method's state, and resume running that method at a later point in time.

An introductory example...

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



Some more information on coroutines vs threads can be found at ...
[Wikipedia: Coroutine](http://en.wikipedia.org/wiki/Coroutine)
[Stackoverflow: Difference between a "coroutine" and a "thread"?](http://stackoverflow.com/a/23436125)

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
    <version>1.0.0-SNAPSHOT</version>
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

Unfortunately there isn't a Gradle plugin currently available. However, Gradle does allow using calling Ant tasks. As such, you could make use of the provided Ant task in your Grade 

### Why not just use Javaflow?

Apache Commons Javaflow seems to be largely unsupported at this point. But, aside from that, using the Coroutines project has the following advantages:

1. Simple benchmarking has shown Coroutines to be roughly 25% to 50% faster than Javaflow. This is likely due to the use of threading constructs in Javaflow.
1. Coroutines provides both a Maven plugin and an Ant plugin. Javaflow only provides an Ant plugin.
1. Coroutines has support for Java 8 bytecode. Javaflow has had issues dealing with stackmap frames due to it's reliance on ASM's default behaviour of using the current classloader to derive the common superclass.
1. Coroutines project is modular and the code is readable, well commented, and well tested code.

