package com.offbynull.coroutines.user;

public interface Coroutine {
    void run(Continuation continuation) throws Exception;
}
