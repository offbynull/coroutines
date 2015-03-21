# coroutines
Java coroutines

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

This is what the output looks like...
```
started
0
1
2
3
```


* Roughly twice the speed of Javaflow (no dependence on threadlocal mechanic)
* Supports Java8
* ANT plugin
* Maven plugin
* Well documented
